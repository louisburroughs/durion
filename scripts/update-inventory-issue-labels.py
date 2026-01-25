#!/usr/bin/env python3
"""
Inventory Issue Label Update Script

Updates labels on the 20 Inventory issues resolved in Phase 2/3:
- Removes: blocked:clarification, status:draft
- Adds: status:needs-review
- Applies/updates domain labels (reassignments included)

Usage:
    python3 update-inventory-issue-labels.py [--dry-run] [--token TOKEN]

Environment Variables:
    GITHUB_TOKEN: GitHub API token (required if --token not provided)
"""

import argparse
import os
import sys
from typing import List, Tuple, Optional

try:
    from github import Github, GithubException
except ImportError:
    print("Error: PyGithub not installed. Install with: pip install PyGithub")
    sys.exit(1)

REPO_OWNER = "louisburroughs"
REPO_NAME = "durion-moqui-frontend"

# Issue groups (based on Phase 2 resolution mapping)
INVENTORY_ISSUES = [243, 242, 241, 99, 97, 96, 94, 93, 91, 90, 88, 87]
WORKEXEC_ISSUES = [244, 92]
PRICING_ISSUES = [260]
PRODUCT_ISSUES = [121, 120, 119, 81, 108]

LABELS_TO_REMOVE = ["blocked:clarification", "status:draft"]
BASE_ADD_LABELS = ["status:needs-review"]

class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    CYAN = '\033[0;36m'
    NC = '\033[0m'

def print_colored(text: str, color: str) -> None:
    print(f"{color}{text}{Colors.NC}")

def ensure_labels(issue, labels: List[str], dry_run: bool) -> None:
    existing = {l.name for l in issue.get_labels()}
    to_add = [l for l in labels if l not in existing]
    if not to_add:
        return
    if dry_run:
        print(f"  Would add labels: {', '.join(to_add)}")
    else:
        issue.add_to_labels(*to_add)
        print_colored(f"  Added labels: {', '.join(to_add)}", Colors.GREEN)

def remove_labels(issue, labels: List[str], dry_run: bool) -> None:
    existing = {l.name for l in issue.get_labels()}
    to_remove = [l for l in labels if l in existing]
    if not to_remove:
        return
    if dry_run:
        print(f"  Would remove labels: {', '.join(to_remove)}")
    else:
        for l in to_remove:
            issue.remove_from_labels(l)
        print_colored(f"  Removed labels: {', '.join(to_remove)}", Colors.RED)

def process_issue(issue_num: int, domain_label: Optional[str], dry_run: bool, gh_repo) -> Tuple[bool, str]:
    try:
        issue = gh_repo.get_issue(number=issue_num)
        print_colored(f"Processing Issue #{issue_num}...", Colors.BLUE)

        # Add base + domain labels
        add_labels = BASE_ADD_LABELS.copy()
        if domain_label:
            add_labels.append(domain_label)
        ensure_labels(issue, add_labels, dry_run)

        # Remove old labels
        remove_labels(issue, LABELS_TO_REMOVE, dry_run)

        # If reassigned, remove domain:inventory
        if domain_label and domain_label != "domain:inventory":
            remove_labels(issue, ["domain:inventory"], dry_run)

        print()
        return True, "ok"
    except GithubException as e:
        return False, f"GithubException: {e}"
    except Exception as e:
        return False, f"Exception: {e}"


def main():
    parser = argparse.ArgumentParser(
        description="Update labels on Inventory issues",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Dry run (show what would change)
  python3 update-inventory-issue-labels.py --dry-run

  # Apply changes with explicit token
  python3 update-inventory-issue-labels.py --token ghp_xxxxx

  # Apply changes using GITHUB_TOKEN env var
  GITHUB_TOKEN=ghp_xxxxx python3 update-inventory-issue-labels.py
        """
    )
    parser.add_argument("--dry-run", action="store_true", help="Show changes without applying")
    parser.add_argument("--token", help="GitHub API token (defaults to GITHUB_TOKEN env var)")
    args = parser.parse_args()

    github_token = args.token or os.getenv("GITHUB_TOKEN")
    if not github_token:
        print_colored("Error: Missing GitHub token. Provide --token or set GITHUB_TOKEN.", Colors.RED)
        sys.exit(1)

    try:
        gh = Github(github_token)
        repo = gh.get_repo(f"{REPO_OWNER}/{REPO_NAME}")
    except GithubException as e:
        print_colored(f"Error initializing GitHub API: {e}", Colors.RED)
        sys.exit(1)

    print_colored("=" * 60, Colors.BLUE)
    print_colored("Inventory Issue Label Update", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Repository: {REPO_OWNER}/{REPO_NAME}")
    print(f"Dry run: {args.dry_run}")
    print()

    groups = [
        ("domain:inventory", INVENTORY_ISSUES),
        ("domain:workexec", WORKEXEC_ISSUES),
        ("domain:pricing", PRICING_ISSUES),
        ("domain:product", PRODUCT_ISSUES),
    ]

    total = sum(len(g[1]) for g in groups)
    print_colored(f"Processing {total} issues...", Colors.CYAN)
    print()

    success, failure = 0, 0
    for domain_label, issue_numbers in groups:
        print_colored(f"== {domain_label} ==", Colors.GREEN)
        for num in issue_numbers:
            ok, msg = process_issue(num, domain_label, args.dry_run, repo)
            if ok:
                success += 1
            else:
                failure += 1
                print_colored(f"  Failed: #{num} — {msg}", Colors.RED)
        print()

    print_colored("=" * 60, Colors.BLUE)
    print_colored("Summary", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Total issues processed: {total}")
    print_colored(f"Successful: {success}", Colors.GREEN)
    if failure:
        print_colored(f"Failed: {failure}", Colors.RED)
    print()


if __name__ == "__main__":
    main()
