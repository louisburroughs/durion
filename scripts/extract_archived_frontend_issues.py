#!/usr/bin/env python3
"""
extract_archived_frontend_issues.py

Usage examples:
  python extract_archived_frontend_issues.py --out-dir archived-frontend-issues
  python extract_archived_frontend_issues.py --state open --dry-run
  python extract_archived_frontend_issues.py --issue 111 --force

Dependencies: Python 3.10+, requests

This script exports issues from an archived frontend repository into one
markdown file per issue. Each markdown file includes issue metadata, labels,
body, and all comments.

Output file naming:
  CAP_000.111.frontend.md
  NOCAP.111.frontend.md

Capability labels are discovered from issue labels using the existing
repository convention: CAP:###.

The script honors GITHUB_TOKEN environment variable for API access only.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import random
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests

VERSION = "1.0"
CAPABILITY_PATTERN = re.compile(r"^CAP:(\d+)$")


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_write_json(path: Path, data: Dict[str, Any]) -> None:
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as file_handle:
        json.dump(data, file_handle, indent=2, ensure_ascii=False)
    os.replace(tmp, path)


def atomic_write_text(path: Path, content: str) -> None:
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as file_handle:
        file_handle.write(content)
    os.replace(tmp, path)


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as file_handle:
        return json.load(file_handle)


def ensure_lock(out_dir: Path, force: bool = False) -> None:
    lock = out_dir / ".lock"
    if lock.exists():
        try:
            mtime = lock.stat().st_mtime
        except OSError:
            mtime = 0
        age = time.time() - mtime
        if age < 3600 and not force:
            raise RuntimeError(
                f"Lock file {lock} exists and is recent (age={int(age)}s). Use --force to override."
            )
    out_dir.mkdir(parents=True, exist_ok=True)
    lock.write_text(f"pid:{os.getpid()}\n{iso_now()}\n", encoding="utf-8")


def release_lock(out_dir: Path) -> None:
    lock = out_dir / ".lock"
    try:
        lock.unlink()
    except OSError:
        pass


class GitHubFetcher:
    def __init__(self, token: str, verbose: bool = False):
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "extract-archived-frontend-issues-script",
        })
        self.verbose = verbose

    def _backoff_sleep(self, attempt: int) -> None:
        base = min(60, 2 ** attempt)
        jitter = random.uniform(0, 1)
        time.sleep(base + jitter)

    def _get(self, url: str, params: Optional[Dict[str, Any]] = None) -> requests.Response:
        tries = 0
        while True:
            tries += 1
            response = self.session.get(url, params=params, timeout=30)
            if response.status_code in (200, 201):
                return response

            if response.status_code in (429, 403):
                remaining = response.headers.get("X-RateLimit-Remaining")
                if response.status_code == 429 or (remaining is not None and remaining == "0"):
                    wait = int(response.headers.get("Retry-After") or 60)
                    logging.warning("Rate limited. Sleeping %s seconds", wait)
                    time.sleep(wait)
                    continue

            if tries >= 5:
                response.raise_for_status()

            logging.info("Request failed (status=%s). Backing off and retrying...", response.status_code)
            self._backoff_sleep(tries)

    def fetch_all_issues(self, owner: str, repo: str, state: str, per_page: int = 100) -> List[Dict[str, Any]]:
        issues: List[Dict[str, Any]] = []
        page = 1
        while True:
            url = f"https://api.github.com/repos/{owner}/{repo}/issues"
            params = {"state": state, "per_page": per_page, "page": page, "sort": "created", "direction": "asc"}
            response = self._get(url, params=params)
            raw_items = response.json()
            if not isinstance(raw_items, list):
                raise RuntimeError(f"Unexpected response fetching issues: {raw_items}")

            page_items = [item for item in raw_items if "pull_request" not in item]
            issues.extend(page_items)

            if len(raw_items) < per_page:
                break
            page += 1
        return issues

    def fetch_issue(self, owner: str, repo: str, number: int) -> Dict[str, Any]:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}"
        response = self._get(url)
        return response.json()

    def fetch_issue_comments(self, owner: str, repo: str, number: int, per_page: int = 100) -> List[Dict[str, Any]]:
        comments: List[Dict[str, Any]] = []
        page = 1
        while True:
            url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}/comments"
            params = {"per_page": per_page, "page": page}
            response = self._get(url, params=params)
            page_items = response.json()
            if not isinstance(page_items, list):
                raise RuntimeError(f"Unexpected response fetching comments for issue {number}: {page_items}")

            comments.extend(page_items)
            if len(page_items) < per_page:
                break
            page += 1
        return comments


def extract_capability_prefix(labels: List[str]) -> str:
    for label in labels:
        match = CAPABILITY_PATTERN.match(label)
        if match:
            return f"CAP_{int(match.group(1)):03d}"
    return "NOCAP"


def build_output_name(issue_number: int, labels: List[str]) -> str:
    capability_prefix = extract_capability_prefix(labels)
    return f"{capability_prefix}.{issue_number}.frontend.md"


def render_issue_markdown(owner: str, repo: str, issue: Dict[str, Any], comments: List[Dict[str, Any]]) -> str:
    issue_number = issue.get("number")
    title = issue.get("title", "")
    url = issue.get("html_url", "")
    state = issue.get("state", "")
    created_at = issue.get("created_at", "")
    updated_at = issue.get("updated_at", "")
    closed_at = issue.get("closed_at") or ""
    labels = [label.get("name", "") for label in issue.get("labels", [])]
    capability = extract_capability_prefix(labels)
    body = issue.get("body") or ""
    assignees = ", ".join(assignee.get("login", "") for assignee in issue.get("assignees", [])) or "None"
    author = (issue.get("user") or {}).get("login", "unknown")

    lines = [
        f"# {title}",
        "",
        f"- Repository: {owner}/{repo}",
        f"- Issue: #{issue_number}",
        f"- URL: {url}",
        f"- State: {state}",
        f"- Author: {author}",
        f"- Assignees: {assignees}",
        f"- Capability: {capability}",
        f"- Labels: {', '.join(labels) if labels else 'None'}",
        f"- Created: {created_at}",
        f"- Updated: {updated_at}",
        f"- Closed: {closed_at or 'N/A'}",
        "",
        "## Issue Body",
        "",
        body if body else "_No issue body provided._",
        "",
        f"## Comments ({len(comments)})",
        "",
    ]

    if not comments:
        lines.append("_No comments._")
    else:
        for index, comment in enumerate(comments, start=1):
            comment_author = (comment.get("user") or {}).get("login", "unknown")
            comment_created_at = comment.get("created_at", "")
            comment_updated_at = comment.get("updated_at", "")
            comment_body = comment.get("body") or "_Empty comment body._"
            lines.extend([
                f"### Comment {index}",
                "",
                f"- Author: {comment_author}",
                f"- Created: {comment_created_at}",
                f"- Updated: {comment_updated_at}",
                "",
                comment_body,
                "",
            ])

    return "\n".join(lines).rstrip() + "\n"


def build_plan(owner: str, repo: str, state: str, fetcher: GitHubFetcher, per_page: int, issue_number: Optional[int]) -> Dict[str, Any]:
    now = iso_now()
    if issue_number is not None:
        pending = [issue_number]
        logging.info("Created single-issue plan for %s/%s#%s", owner, repo, issue_number)
    else:
        logging.info("Fetching %s issues for %s/%s", state, owner, repo)
        issues = fetcher.fetch_all_issues(owner, repo, state=state, per_page=per_page)
        pending = [issue["number"] for issue in issues]
        logging.info("Found %d issues", len(pending))

    return {
        "version": VERSION,
        "created_at": now,
        "updated_at": now,
        "owner": owner,
        "repo": repo,
        "state_filter": state,
        "pending": pending,
        "processing": [],
        "completed": {},
        "failed": {},
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--owner", default="louisburroughs")
    parser.add_argument("--repo", default="durion-moqui-frontend")
    parser.add_argument("--out-dir", default="archived-frontend-issues")
    parser.add_argument("--state", choices=["open", "closed", "all"], default="all")
    parser.add_argument("--issue", type=int, default=None)
    parser.add_argument("--per-page", type=int, default=100)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--force-refresh-plan", action="store_true")
    parser.add_argument("--force", action="store_true")
    return parser


def get_github_token() -> Optional[str]:
    return os.environ.get("GITHUB_TOKEN")


def load_or_build_plan(args: argparse.Namespace, fetcher: GitHubFetcher, plan_path: Path) -> Dict[str, Any]:
    if plan_path.exists() and not args.force_refresh_plan:
        return load_json(plan_path)

    plan = build_plan(args.owner, args.repo, args.state, fetcher, args.per_page, args.issue)
    if not args.dry_run:
        atomic_write_json(plan_path, plan)
    return plan


def save_plan(plan: Dict[str, Any], plan_path: Path, dry_run: bool) -> None:
    if dry_run:
        return
    plan["updated_at"] = iso_now()
    atomic_write_json(plan_path, plan)


def begin_processing_issue(
    issue_number: int,
    processing: List[int],
    plan: Dict[str, Any],
    plan_path: Path,
    dry_run: bool,
) -> None:
    if dry_run:
        return
    processing.append(issue_number)
    save_plan(plan, plan_path, dry_run)


def finish_processing_issue(
    issue_number: int,
    processing: List[int],
    plan: Dict[str, Any],
    plan_path: Path,
    dry_run: bool,
) -> None:
    if dry_run:
        return
    try:
        processing.remove(issue_number)
    except ValueError:
        pass
    save_plan(plan, plan_path, dry_run)


def record_failed_issue(
    issue_number: int,
    error: Exception,
    failed: Dict[str, Any],
    dry_run: bool,
) -> None:
    if dry_run:
        return
    failed[str(issue_number)] = {"error": str(error), "last_attempt": iso_now()}


def should_stop_dry_run(pending: List[int], dry_run: bool, processed_count: int, dry_run_limit: int) -> bool:
    return bool(pending) and dry_run and processed_count >= dry_run_limit


def process_issue(
    args: argparse.Namespace,
    fetcher: GitHubFetcher,
    out_dir: Path,
    issue_number: int,
    completed: Dict[str, Any],
) -> int:
    issue = fetcher.fetch_issue(args.owner, args.repo, issue_number)
    labels = [label.get("name", "") for label in issue.get("labels", [])]
    output_name = build_output_name(issue_number, labels)
    destination = out_dir / output_name
    comments = fetcher.fetch_issue_comments(args.owner, args.repo, issue_number)
    content = render_issue_markdown(args.owner, args.repo, issue, comments)

    if args.dry_run:
        logging.info("[dry-run] would write %s", destination)
    else:
        atomic_write_text(destination, content)
        completed[str(issue_number)] = {
            "file": output_name,
            "processed_at": iso_now(),
            "comment_count": len(comments),
        }

    return len(comments)


def process_pending_issues(
    args: argparse.Namespace,
    fetcher: GitHubFetcher,
    out_dir: Path,
    plan: Dict[str, Any],
    plan_path: Path,
) -> Dict[str, Any]:
    pending: List[int] = plan.setdefault("pending", [])
    processing: List[int] = plan.setdefault("processing", [])
    completed: Dict[str, Any] = plan.setdefault("completed", {})
    failed: Dict[str, Any] = plan.setdefault("failed", {})
    processed_count = 0
    failed_count = 0
    dry_run_limit = 5

    while pending:
        if should_stop_dry_run(pending, args.dry_run, processed_count, dry_run_limit):
            break

        issue_number = pending.pop(0)
        issue_key = str(issue_number)
        if issue_key in completed:
            continue

        begin_processing_issue(issue_number, processing, plan, plan_path, args.dry_run)

        try:
            process_issue(args, fetcher, out_dir, issue_number, completed)
            processed_count += 1
        except Exception as exc:  # noqa: BLE001
            logging.exception("Failed processing issue %s", issue_number)
            failed_count += 1
            record_failed_issue(issue_number, exc, failed, args.dry_run)
        finally:
            finish_processing_issue(issue_number, processing, plan, plan_path, args.dry_run)

    return {
        "processed": processed_count,
        "failed": failed_count,
        "remaining": len(pending),
        "output_dir": str(out_dir),
    }


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s: %(message)s")

    token = get_github_token()
    if not token:
        logging.error("GITHUB_TOKEN not set in environment")
        return 2

    out_dir = Path(args.out_dir)
    plan_path = out_dir / "archive_extraction_plan.json"

    try:
        ensure_lock(out_dir, force=args.force)
    except RuntimeError as exc:
        logging.error(exc)
        return 2

    fetcher = GitHubFetcher(token, verbose=args.verbose)

    try:
        plan = load_or_build_plan(args, fetcher, plan_path)
        summary = process_pending_issues(args, fetcher, out_dir, plan, plan_path)
        print(json.dumps(summary))
        return 0
    finally:
        release_lock(out_dir)


if __name__ == "__main__":
    raise SystemExit(main())