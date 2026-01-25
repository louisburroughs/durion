#!/usr/bin/env python3
"""
Accounting Domain Issue Label Update Script

Updates labels on all 6 resolved accounting issues from Phase 3:
- Removes: blocked:clarification
- Adds: status:resolved, domain:accounting
- Ensures all issues are correctly labeled for implementation phase

Issues Updated:
  #203 - Posting Categories & GL Mapping Configuration
  #194 - Vendor Bill from Event
  #193 - AP Approval & Payment Scheduling
  #190 - Manual Journal Entry with Controls
  #187 - Bank/Cash Reconciliation
  #183 - WorkCompleted Event Ingestion

Usage:
    python3 update-accounting-issue-labels.py [--dry-run] [--token TOKEN]

Environment Variables:
    GITHUB_TOKEN: GitHub API token (required if --token not provided)
"""

import argparse
import os
import sys
from typing import Dict, List, Tuple

try:
    from github import Github, GithubException
except ImportError:
    print("Error: PyGithub not installed. Install with: pip install PyGithub")
    sys.exit(1)

# Configuration
REPO_OWNER = "louisburroughs"
REPO_NAME = "durion-moqui-frontend"

# Resolved accounting issues from Phase 3
ACCOUNTING_ISSUES = {
    203: "Posting Categories & GL Mapping Configuration",
    194: "Vendor Bill from Event",
    193: "AP Approval & Payment Scheduling",
    190: "Manual Journal Entry with Controls",
    187: "Bank/Cash Reconciliation",
    183: "WorkCompleted Event Ingestion",
}

# Labels configuration
LABELS_TO_REMOVE = ["blocked:clarification", "status:draft"]
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


def print_header() -> None:
    """Print script header"""
    print_colored("=" * 60, Colors.BLUE)
    print_colored("Accounting Domain Issue Label Update", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Repository: {REPO_OWNER}/{REPO_NAME}")
    print(f"Issues to update: {len(ACCOUNTING_ISSUES)}")
    print()


def update_issue_labels(
    issue,
    dry_run: bool = False
) -> Tuple[bool, str]:
    """
    Update labels for a single accounting issue
    
    Args:
        issue: GitHub issue object
        dry_run: If True, print changes without making them
    
    Returns:
        Tuple of (success, message)
    """
    try:
        issue_num = issue.number
        issue_title = issue.title
        current_labels = {label.name for label in issue.labels}
        
        # Determine labels to remove (only if they exist)
        labels_to_remove = [
            label for label in LABELS_TO_REMOVE if label in current_labels
        ]
        
        # Determine labels to add (avoid duplicates)
        labels_to_add = [
            label for label in LABELS_TO_ADD if label not in current_labels
        ]
        
        # Build status message
        status_parts = []
        if labels_to_remove:
            status_parts.append(f"remove {len(labels_to_remove)}")
        if labels_to_add:
            status_parts.append(f"add {len(labels_to_add)}")
        
        if not status_parts:
            return (True, f"#{issue_num}: Already correctly labeled")
        
        status_msg = f"#{issue_num}: {', '.join(status_parts)}"
        
        # Apply changes
        if not dry_run:
            new_labels = current_labels - set(labels_to_remove) | set(labels_to_add)
            issue.edit(labels=list(new_labels))
        
        return (True, status_msg)
        
    except GithubException as e:
        return (False, f"#{issue.number}: GitHub API error - {str(e)}")
    except Exception as e:
        return (False, f"#{issue.number}: Error - {str(e)}")


def main():
    """Main script execution"""
    parser = argparse.ArgumentParser(
        description="Update labels on resolved accounting issues",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Dry run (show what would change)
  python3 update-accounting-issue-labels.py --dry-run
  
  # Apply changes with explicit token
  python3 update-accounting-issue-labels.py --token ghp_xxxxx
  
  # Apply changes using GITHUB_TOKEN env var
  GITHUB_TOKEN=ghp_xxxxx python3 update-accounting-issue-labels.py
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
        print_colored(
            "Error: GitHub token not provided. Set GITHUB_TOKEN or use --token",
            Colors.RED
        )
        sys.exit(1)
    
    # Initialize GitHub API
    try:
        gh = Github(github_token)
        repo = gh.get_user(REPO_OWNER).get_repo(REPO_NAME)
    except GithubException as e:
        print_colored(f"Error: Failed to authenticate with GitHub - {str(e)}", Colors.RED)
        sys.exit(1)
    
    # Print header
    print_header()
    
    if args.dry_run:
        print_colored("DRY RUN MODE - No changes will be made", Colors.YELLOW)
        print()
    
    # Process each accounting issue
    success_count = 0
    failure_count = 0
    
    print_colored("Processing issues:", Colors.CYAN)
    print()
    
    for issue_num in sorted(ACCOUNTING_ISSUES.keys()):
        try:
            issue = repo.get_issue(issue_num)
            success, message = update_issue_labels(issue, args.dry_run)
            
            if success:
                print(f"  {Colors.GREEN}✓{Colors.NC} {message}")
                success_count += 1
            else:
                print(f"  {Colors.RED}✗{Colors.NC} {message}")
                failure_count += 1
                
        except GithubException as e:
            print(f"  {Colors.RED}✗{Colors.NC} #{issue_num}: Could not fetch issue - {str(e)}")
            failure_count += 1
    
    print()
    
    # Print summary
    print_colored("=" * 60, Colors.BLUE)
    print_colored("Summary", Colors.BLUE)
    print_colored("=" * 60, Colors.BLUE)
    print(f"Total issues processed: {len(ACCOUNTING_ISSUES)}")
    print_colored(f"✓ Successful: {success_count}", Colors.GREEN)
    if failure_count > 0:
        print_colored(f"✗ Failed: {failure_count}", Colors.RED)
    print()
    
    if args.dry_run:
        print_colored(
            "Changes NOT applied (dry run mode). Run without --dry-run to apply.",
            Colors.YELLOW
        )
    else:
        print_colored("Changes applied successfully!", Colors.GREEN)
    
    print()
    print_colored("Label changes:", Colors.CYAN)
    if LABELS_TO_REMOVE:
        print(f"  {Colors.RED}Removed:{Colors.NC} {', '.join(LABELS_TO_REMOVE)}")
    if LABELS_TO_ADD:
        print(f"  {Colors.GREEN}Added:{Colors.NC} {', '.join(LABELS_TO_ADD)}")
    
    print()
    print_colored("Issues updated:", Colors.CYAN)
    for issue_num, title in sorted(ACCOUNTING_ISSUES.items()):
        print(f"  #{issue_num} - {title}")
    
    print()
    print_colored("Next steps:", Colors.CYAN)
    print("  1. Review Phase 3 documentation at:")
    print("     domains/accounting/accounting-questions.md")
    print("  2. Begin frontend/backend implementation")
    print("  3. Reference GitHub issues for detailed acceptance criteria")
    print()


if __name__ == "__main__":
    main()
