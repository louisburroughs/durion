"""GitHub issue extraction via `gh` CLI and GraphQL API."""

from __future__ import annotations

import json
import logging
import sqlite3
import subprocess
import time
from typing import Any

log = logging.getLogger(__name__)

REPOS = [
    ("louisburroughs", "durion"),
    ("louisburroughs", "durion-positivity-backend"),
    ("louisburroughs", "durion-moqui-frontend"),
]

# GraphQL query template — fetches one issue with hierarchy, comments, labels
ISSUE_GRAPHQL = """\
query($owner: String!, $repo: String!, $number: Int!) {
  repository(owner: $owner, name: $repo) {
    issue(number: $number) {
      number
      title
      body
      state
      url
      createdAt
      updatedAt
      milestone { title }
      labels(first: 20) { nodes { name } }
      comments(first: 100) {
        nodes { author { login } body createdAt }
      }
      trackedInIssues(first: 10) {
        nodes { number title state repository { nameWithOwner } }
      }
      trackedIssues(first: 50) {
        nodes { number title state repository { nameWithOwner } }
      }
    }
  }
}
"""


def _run_gh(args: list[str], retries: int = 3) -> str:
    """Run a gh CLI command with retry-on-failure."""
    for attempt in range(retries):
        result = subprocess.run(
            ["gh", *args],
            capture_output=True,
            text=True,
            timeout=60,
        )
        if result.returncode == 0:
            return result.stdout
        # rate-limit or transient error — backoff
        if attempt < retries - 1:
            wait = 2 ** (attempt + 1)
            log.warning(
                "gh command failed (rc=%d), retrying in %ds: %s",
                result.returncode,
                wait,
                result.stderr[:200],
            )
            time.sleep(wait)
        else:
            log.error("gh command failed after %d attempts: %s", retries, result.stderr[:500])
            raise RuntimeError(f"gh CLI failed: {result.stderr[:500]}")
    return ""  # unreachable


def _list_issue_numbers(owner: str, repo: str) -> list[int]:
    """List all issue numbers (any state) from a repo via gh CLI."""
    numbers: list[int] = []
    try:
        raw = _run_gh([
            "api",
            "--paginate",
            f"/repos/{owner}/{repo}/issues?state=all&per_page=100",
            # Exclude pull requests — the issues endpoint returns both
            "-q", ".[] | select(.pull_request == null) | .number",
        ])
    except RuntimeError:
        log.warning("Failed to list issues for %s/%s, stopping", owner, repo)
        return numbers

    numbers = [int(n) for n in raw.strip().splitlines() if n.strip()]
    return numbers


def _fetch_issue_graphql(owner: str, repo: str, number: int) -> dict[str, Any] | None:
    """Fetch a single issue with hierarchy via GraphQL."""
    try:
        raw = _run_gh([
            "api", "graphql",
            "-f", f"query={ISSUE_GRAPHQL}",
            "-F", f"owner={owner}",
            "-F", f"repo={repo}",
            "-F", f"number={number}",
        ])
    except RuntimeError:
        log.warning("Failed to fetch %s/%s#%d via GraphQL", owner, repo, number)
        return None

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.warning("Invalid JSON from GraphQL for %s/%s#%d", owner, repo, number)
        return None

    if "errors" in data:
        log.warning("GraphQL errors for %s/%s#%d: %s", owner, repo, number, data["errors"])
        return None

    issue = data.get("data", {}).get("repository", {}).get("issue")
    return issue


def _fetch_issue_rest(owner: str, repo: str, number: int) -> dict[str, Any] | None:
    """Fetch a single issue via REST (fallback when GraphQL fails)."""
    try:
        raw = _run_gh([
            "api", f"/repos/{owner}/{repo}/issues/{number}",
        ])
        return json.loads(raw)
    except (RuntimeError, json.JSONDecodeError):
        log.warning("Failed to fetch %s/%s#%d via REST", owner, repo, number)
        return None


def _fetch_comments_rest(owner: str, repo: str, number: int) -> list[dict[str, Any]]:
    """Fetch comments for an issue via REST."""
    try:
        raw = _run_gh([
            "api", f"/repos/{owner}/{repo}/issues/{number}/comments",
            "--paginate",
        ])
        comments = json.loads(raw)
        return comments if isinstance(comments, list) else []
    except (RuntimeError, json.JSONDecodeError):
        return []


def _store_issue(
    con: sqlite3.Connection,
    owner: str,
    repo: str,
    issue: dict[str, Any],
    source: str = "graphql",
) -> None:
    """Insert a single issue into the database."""
    repo_name = f"{owner}/{repo}"

    if source == "graphql":
        number = issue["number"]
        title = issue.get("title", "")
        body = issue.get("body", "")
        state = issue.get("state", "").lower()
        url = issue.get("url", "")
        labels_list = [n["name"] for n in issue.get("labels", {}).get("nodes", [])]
        milestone = (issue.get("milestone") or {}).get("title")
        created = issue.get("createdAt", "")
        updated = issue.get("updatedAt", "")
    else:
        # REST fallback
        number = issue["number"]
        title = issue.get("title", "")
        body = issue.get("body") or ""
        state = issue.get("state", "").lower()
        url = issue.get("html_url", "")
        labels_list = [l["name"] for l in issue.get("labels", [])]
        milestone = (issue.get("milestone") or {}).get("title")
        created = issue.get("created_at", "")
        updated = issue.get("updated_at", "")

    # Insert issue row first (child tables have FK on this)
    con.execute(
        "INSERT OR REPLACE INTO issues (number, repo, title, state, body, url, labels, milestone, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
        (number, repo_name, title, state, body, url, json.dumps(labels_list), milestone, created, updated),
    )

    if source == "graphql":
        # Store comments
        for c in issue.get("comments", {}).get("nodes", []):
            con.execute(
                "INSERT INTO comments (repo, issue_number, author, body, created_at) VALUES (?,?,?,?,?)",
                (repo_name, number, (c.get("author") or {}).get("login", ""), c.get("body", ""), c.get("createdAt", "")),
            )

        # Store tracked children (sub_issues)
        for child in issue.get("trackedIssues", {}).get("nodes", []):
            child_repo = child.get("repository", {}).get("nameWithOwner", repo_name)
            con.execute(
                "INSERT INTO sub_issues (parent_repo, parent_number, child_repo, child_number, title, state) VALUES (?,?,?,?,?,?)",
                (repo_name, number, child_repo, child["number"], child.get("title", ""), (child.get("state") or "").lower()),
            )

        # Store tracked parents (as sub_issues with this issue as child)
        for parent in issue.get("trackedInIssues", {}).get("nodes", []):
            parent_repo_name = parent.get("repository", {}).get("nameWithOwner", repo_name)
            con.execute(
                "INSERT OR IGNORE INTO sub_issues (parent_repo, parent_number, child_repo, child_number, title, state) VALUES (?,?,?,?,?,?)",
                (parent_repo_name, parent["number"], repo_name, number, title, state),
            )


def extract(con: sqlite3.Connection) -> None:
    """Extract all issues from all configured repos into the database."""
    for owner, repo in REPOS:
        repo_name = f"{owner}/{repo}"
        log.info("Extracting issues from %s ...", repo_name)

        # List all issue numbers
        numbers = _list_issue_numbers(owner, repo)
        log.info("Found %d issues in %s", len(numbers), repo_name)

        for i, num in enumerate(numbers, 1):
            if i % 20 == 0:
                log.info("  Progress: %d/%d issues from %s", i, len(numbers), repo_name)

            # Try GraphQL first (gives hierarchy data)
            issue = _fetch_issue_graphql(owner, repo, num)
            if issue:
                _store_issue(con, owner, repo, issue, source="graphql")
            else:
                # Fallback to REST
                rest_issue = _fetch_issue_rest(owner, repo, num)
                if rest_issue:
                    _store_issue(con, owner, repo, rest_issue, source="rest")
                    # Fetch comments separately for REST
                    comments = _fetch_comments_rest(owner, repo, num)
                    for c in comments:
                        con.execute(
                            "INSERT INTO comments (repo, issue_number, author, body, created_at) VALUES (?,?,?,?,?)",
                            (repo_name, num, (c.get("user") or {}).get("login", ""), c.get("body", ""), c.get("created_at", "")),
                        )
                else:
                    log.warning("Skipping %s#%d — could not fetch", repo_name, num)

            # Rate-limit courtesy pause
            if i % 10 == 0:
                time.sleep(0.5)

        con.commit()
        log.info("Finished extracting %s (%d issues)", repo_name, len(numbers))
