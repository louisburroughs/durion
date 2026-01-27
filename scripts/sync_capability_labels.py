#!/usr/bin/env python3
"""
Sync capability labels across repositories.

- Source: issues in louisburroughs/durion labeled `type:capability`
- For each issue: create/update label `CAP:{###}` with color #0052cc and description = issue title
- Targets: durion, durion-positivity-backend, durion-moqui-frontend

Usage:
    python3 sync_capability_labels.py [--dry-run] [--token TOKEN]

Auth:
    Provide a GitHub token with `repo` scope via --token or GITHUB_TOKEN.
"""

import argparse
import os
import sys
from typing import Dict, Iterable, List, Tuple

from github import Github, GithubException

OWNER = "louisburroughs"
SOURCE_REPO = "durion"
TARGET_REPOS = ["durion", "durion-positivity-backend", "durion-moqui-frontend"]
CAPABILITY_LABEL = "type:capability"
LABEL_COLOR = "0052cc"  # GitHub expects color without leading '#'
DESCRIPTION_LIMIT = 100


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync capability labels across repositories")
    parser.add_argument("--dry-run", action="store_true", help="Preview changes without applying them")
    parser.add_argument("--token", help="GitHub token with repo scope (defaults to GITHUB_TOKEN)")
    return parser.parse_args()


def get_github_client(token: str) -> Github:
    if not token:
        raise ValueError("GitHub token not provided; set GITHUB_TOKEN or use --token")
    return Github(token)


def fetch_capability_issues(repo) -> List[Tuple[int, str]]:
    issues = []
    for issue in repo.get_issues(state="all", labels=[CAPABILITY_LABEL]):
        # Skip pull requests; only process issues
        if issue.pull_request is not None:
            continue
        issues.append((issue.number, issue.title.strip()))
    return issues


def truncate_description(text: str, limit: int = DESCRIPTION_LIMIT) -> str:
    if len(text) <= limit:
        return text
    return text[: max(limit - 3, 0)].rstrip() + "..."


def ensure_label(repo, name: str, description: str, color: str, dry_run: bool) -> str:
    try:
        label = repo.get_label(name)
        needs_update = (label.color.lower() != color.lower()) or (label.description or "") != description
        if needs_update:
            if dry_run:
                return "would-update"
            label.edit(name=name, color=color, description=description)
            return "updated"
        return "unchanged"
    except GithubException as exc:  # 404 -> label missing
        if getattr(exc, "status", None) == 404:
            if dry_run:
                return "would-create"
            repo.create_label(name=name, color=color, description=description)
            return "created"
        raise


def main() -> None:
    args = parse_args()
    token = args.token or os.getenv("GITHUB_TOKEN")

    try:
        gh = get_github_client(token)
    except ValueError as err:
        print(f"Error: {err}")
        sys.exit(1)

    try:
        source_repo = gh.get_repo(f"{OWNER}/{SOURCE_REPO}")
    except GithubException as exc:
        print(f"Error: could not access source repo {OWNER}/{SOURCE_REPO}: {exc}")
        sys.exit(1)

    capability_issues = fetch_capability_issues(source_repo)
    if not capability_issues:
        print("No capability issues found with label 'type:capability'. Nothing to do.")
        return

    print(f"Found {len(capability_issues)} capability issues in {OWNER}/{SOURCE_REPO}.")
    targets: Dict[str, object] = {}
    for repo_name in TARGET_REPOS:
        try:
            targets[repo_name] = gh.get_repo(f"{OWNER}/{repo_name}")
        except GithubException as exc:
            print(f"Error: could not access target repo {OWNER}/{repo_name}: {exc}")
            sys.exit(1)

    actions: List[str] = []
    for issue_number, issue_title in sorted(capability_issues, key=lambda item: item[0]):
        label_name = f"CAP:{issue_number:03d}"
        description = truncate_description(issue_title)
        for repo_name, repo in targets.items():
            try:
                result = ensure_label(repo, label_name, description, LABEL_COLOR, args.dry_run)
                actions.append(f"{repo_name} -> {label_name}: {result}")
            except GithubException as exc:
                actions.append(f"{repo_name} -> {label_name}: error {exc}")

    print("\nPlanned actions:" if args.dry_run else "\nApplied actions:")
    for action in actions:
        print(f"- {action}")

    if args.dry_run:
        print("\nDry run complete. Run without --dry-run to apply changes.")


if __name__ == "__main__":
    main()
