#!/usr/bin/env python3
"""
WorkExec Issue Label Update Script

Updates labels on all 28 WorkExec issues:
- Removes: blocked:clarification, status:draft
- Adds: status:needs-review
- Moves 9 issues to their correct domains (people, shopmgmt, order)

Usage:
    python3 update-workexec-issue-labels.py [--dry-run] [--token TOKEN]

Environment Variables:
    GITHUB_TOKEN: GitHub API token (required if --token not provided)
"""

import os
import sys
import argparse
from typing import List, Tuple

try:
    from github import Github, GithubException
except ImportError:
    print("Error: PyGithub not installed. Install with: pip install PyGithub")
    sys.exit(1)

# Configuration
REPO_OWNER = "louisburroughs"
REPO_NAME = "durion-moqui-frontend"

# Issues grouped by domain assignment
ISSUE_ASSIGNMENTS = {
    "domain:workexec": [222, 216, 162, 219, 157, 79, 134, 129],
    "domain:people": [149, 146, 145, 132, 131],
    "domain:shopmgmt": [138, 137, 133, 127],
    "domain:order": [85],
}

# Labels to remove from all issues
LABELS_TO_REMOVE = ["blocked:clarification", "status:draft"]

# Labels to add to all issues
LABELS_TO_ADD = ["status:needs-review"]

# Color codes
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    CYAN = '\033[0;36m'
    NC = '\033[0m'  # No Color


def print_colored(text: str, color: str) -> None:
    """Print colored text"""
    print(f"{color}{text}{Colors.NC}")


def update_issue_labels(
    issue,
    new_domain: str = None,
    dry_run: bool = False
) -> Tuple[bool, str]:
    """
    Update labels for a single issue
    
    Args:
        issue: GitHub issue object
        new_domain: New domain label to assign (e.g., "domain:people")
        dry_run: If True, print changes without making them
    
    Returns:
        Tuple of (success, message)
    """
    try:
        # Get current labels
        current_labels = {label.name for label in issue.labels}
        
        # Calculate labels to keep
        labels_to_keep = current_labels - set(LABELS_TO_REMOVE)
        
        # Remove old domain label if changing domains
        if new_domain and "domain:workexec" in current_labels:
            labels_to_keep.discard("domain:workexec")
        
        # Add new labels
        new_labels = labels_to_keep | set(LABELS_TO_ADD)
        if new_domain:
            new_labels.add(new_domain)
        
        # Convert to sorted list for consistent display
        new_labels_list = sorted(list(new_labels))
        
        if dry_run:
            removed = current_labels & set(LABELS_TO_REMOVE)
            added_new = set(LABELS_TO_ADD)
            if new_domain:
                added_new.add(new_domain)
            
            message = f"Issue #{issue.number}:\n"
            message += f"  Current labels: {', '.join(sorted(current_labels)) if current_labels else '(none)'}\n"
            if removed:
                message += f"  Remove: {', '.join(removed)}\n"
            message += f"  Add: {', '.join(added_new)}\n"
            message += f"  Final labels: {', '.join(new_labels_list)}"
            return (True, message)
        
        # Apply changes
        issue.set_labels(*new_labels_list)
        
        message = f"Issue #{issue.number}: Updated labels\n"
        message += f"  Labels: {', '.join(new_labels_list) if new_labels_list else '(none)'}"
        return (True, message)
    
    except GithubException as e:
        return (False, f"Issue #{issue.number}: GitHub API error - {e.data.get('message', str(e))}")
    except Exception as e:
        return (False, f"Issue #{issue.number}: Error - {str(e)}")


def main():
    """Main script execution"""
    parser = argparse.ArgumentParser(
        description="Update labels on WorkExec issues",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Dry run (show what would change)
  python3 update-workexec-issue-labels.py --dry-run
  
  # Apply changes with explicit token
  python3 update-workexec-issue-labels.py --token ghp_xxxxx
  
  # Apply changes using GITHUB_TOKEN env var
  GITHUB_TOKEN=ghp_xxxxx python3 update-workexec-issue-labels.py
        """
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would change without making changes"
    )
    parser.add_argument(
        "--token",
        help="GitHub API token (defaults to GITHUB_TOKEN env var)"
    )
    
    args = parser.parse_args()
    
    # Get GitHub token
    github_token = args.token or os.getenv("GITHUB_TOKEN")
    if not github_token:
        print_colored("Error: GitHub token not provided", Colors.RED)
        print("  Set GITHUB_TOKEN environment variable or use --token argument")
        sys.exit(1)
    
    # Initialize GitHub API
    try:
        g = Github(github_token)
        repo = g.get_repo(f"{REPO_OWNER}/{REPO_NAME}")
    except GithubException as e:
        print_colored(f"Error: Could not access repository: {e}", Colors.RED)
        sys.exit(1)
    
    # Print header
    print_colored("=" * 60, Colors.BLUE)
    print_colored("WorkExec Issue Label Update", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Repository: {REPO_OWNER}/{REPO_NAME}")
    print(f"Dry run: {args.dry_run}")
    print()
    
    # Collect all issues to update
    total_issues = sum(len(issues) for issues in ISSUE_ASSIGNMENTS.values())
    print_colored(f"Processing {total_issues} issues...", Colors.CYAN)
    print()
    
    success_count = 0
    failure_count = 0
    
    # Process each domain group
    for domain, issue_numbers in sorted(ISSUE_ASSIGNMENTS.items()):
        print_colored(f"--- {domain} ({len(issue_numbers)} issues) ---", Colors.GREEN)
        
        for issue_num in sorted(issue_numbers):
            try:
                issue = repo.get_issue(issue_num)
                success, message = update_issue_labels(issue, domain, args.dry_run)
                
                if success:
                    print_colored(f"✓ {message}", Colors.GREEN)
                    success_count += 1
                else:
                    print_colored(f"✗ {message}", Colors.RED)
                    failure_count += 1
            except GithubException as e:
                error_msg = e.data.get('message', str(e)) if hasattr(e, 'data') else str(e)
                print_colored(f"✗ Issue #{issue_num}: {error_msg}", Colors.RED)
                failure_count += 1
        
        print()
    
    # Print summary
    print_colored("=" * 60, Colors.BLUE)
    print_colored("Summary", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Total issues processed: {total_issues}")
    print_colored(f"Successful: {success_count}", Colors.GREEN)
    if failure_count > 0:
        print_colored(f"Failed: {failure_count}", Colors.RED)
    print()
    
    if args.dry_run:
        print_colored("✓ Dry run complete. No changes were made.", Colors.YELLOW)
    else:
        print_colored("✓ Label update complete!", Colors.GREEN)
    
    print()
    print_colored("Changes made:", Colors.CYAN)
    print(f"  {Colors.RED}Removed:{Colors.NC} blocked:clarification, status:draft (all issues)")
    print(f"  {Colors.GREEN}Added:{Colors.NC} status:needs-review (all issues)")
    print()
    print_colored("Domain assignments:", Colors.CYAN)
    for domain, issues in sorted(ISSUE_ASSIGNMENTS.items()):
        print(f"  {domain}: {', '.join(f'#{n}' for n in sorted(issues))}")
    print()
    print_colored("Note: Issue #133 is marked as duplicate of #137", Colors.YELLOW)
    
    return 0 if failure_count == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
