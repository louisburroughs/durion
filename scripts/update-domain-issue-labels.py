#!/usr/bin/env python3
"""
All Domains Issue Label Update Script

Updates labels on all resolved domain issues from Phase 3 analysis:
- Removes: blocked:clarification
- Adds: analysis:complete, ready:backend-implementation, ready:frontend-implementation
- Handles domain-specific label requirements

Issues Updated:
  Shop Management:
    #76 - Appointment: Create Appointment from Estimate or Order
  
  Location:
    #141 - Locations: Create Bays with Constraints and Capacity
    #139 - Scheduling: Create Appointment with CRM Customer and Vehicle
  
  People:
    #130 - Timekeeping: Approve Submitted Time for a Day/Workorder
  
  Security:
    #66 - Define POS Roles and Permission Matrix
    #65 - Financial Exception Audit Trail (Query/Export)
  
  Pricing:
    #236 - Calculate Taxes and Totals on Estimate
    #161 - Create Promotion Offer (Basic)
    #84 - Apply Line Price Override with Permission and Reason

Usage:
    python3 update-domain-issue-labels.py [--dry-run] [--token TOKEN] [--domain DOMAIN]

Arguments:
    --domain: Only update issues from specific domain (shopmgmt, location, people, security, pricing, all)
              Default: all

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

# All resolved domain issues from Phase 1-3 analysis
# Format: {issue_num: (domain, title, labels_to_add)}
DOMAIN_ISSUES = {
    # Shop Management (Phase 1-3 complete)
    76: {
        "domain": "shopmgmt",
        "title": "Appointment: Create Appointment from Estimate or Order",
        "labels_add": ["analysis:complete", "ready:backend-implementation", "ready:frontend-implementation"],
    },
    # Location (Phase 1 complete)
    141: {
        "domain": "location",
        "title": "Locations: Create Bays with Constraints and Capacity",
        "labels_add": ["analysis:in-progress", "phase:contract-discovery"],
    },
    139: {
        "domain": "location",
        "title": "Scheduling: Create Appointment with CRM Customer and Vehicle",
        "labels_add": ["analysis:in-progress", "phase:contract-discovery"],
    },
    # People (Phase 1-2 complete)
    130: {
        "domain": "people",
        "title": "Timekeeping: Approve Submitted Time for a Day/Workorder",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
    # Security (Phase 1-2 complete)
    66: {
        "domain": "security",
        "title": "Define POS Roles and Permission Matrix",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
    65: {
        "domain": "security",
        "title": "Financial Exception Audit Trail (Query/Export)",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
    # Pricing (Phase 1-2 complete)
    236: {
        "domain": "pricing",
        "title": "Calculate Taxes and Totals on Estimate",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
    161: {
        "domain": "pricing",
        "title": "Create Promotion Offer (Basic)",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
    84: {
        "domain": "pricing",
        "title": "Apply Line Price Override with Permission and Reason",
        "labels_add": ["analysis:in-progress", "phase:data-contracts"],
    },
}

# Labels configuration
LABELS_TO_REMOVE = ["blocked:clarification"]

# Color codes
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    CYAN = '\033[0;36m'
    MAGENTA = '\033[0;35m'
    NC = '\033[0m'  # No Color


def print_colored(text: str, color: str) -> None:
    """Print colored text"""
    print(f"{color}{text}{Colors.NC}")


def print_header(domain_filter: str = "all") -> None:
    """Print script header"""
    print_colored("=" * 70, Colors.BLUE)
    print_colored("All Domains Issue Label Update Script", Colors.BLUE)
    print_colored("=" * 70, Colors.BLUE)
    print(f"Repository: {REPO_OWNER}/{REPO_NAME}")
    
    # Count issues by filter
    if domain_filter == "all":
        issue_count = len(DOMAIN_ISSUES)
        print(f"Issues to update: {issue_count} (all domains)")
    else:
        filtered_issues = [
            n for n, data in DOMAIN_ISSUES.items() 
            if data["domain"] == domain_filter
        ]
        issue_count = len(filtered_issues)
        print(f"Issues to update: {issue_count} (domain: {domain_filter})")
    
    print()


def update_issue_labels(
    issue,
    labels_to_add: List[str],
    labels_to_remove: List[str] = None,
    dry_run: bool = False
) -> Tuple[bool, str]:
    """
    Update labels for a single issue
    
    Args:
        issue: GitHub issue object
        labels_to_add: Labels to add
        labels_to_remove: Labels to remove
        dry_run: If True, print changes without making them
    
    Returns:
        Tuple of (success, message)
    """
    if labels_to_remove is None:
        labels_to_remove = []
    
    try:
        issue_num = issue.number
        current_labels = {label.name for label in issue.labels}
        
        # Determine labels to remove (only if they exist)
        labels_removing = [
            label for label in labels_to_remove if label in current_labels
        ]
        
        # Determine labels to add (avoid duplicates)
        labels_adding = [
            label for label in labels_to_add if label not in current_labels
        ]
        
        # Build status message
        status_parts = []
        if labels_removing:
            status_parts.append(f"remove {len(labels_removing)}")
        if labels_adding:
            status_parts.append(f"add {len(labels_adding)}")
        
        if not status_parts:
            return (True, f"#{issue_num}: Already correctly labeled")
        
        status_msg = f"#{issue_num}: {', '.join(status_parts)}"
        
        # Apply changes
        if not dry_run:
            new_labels = current_labels - set(labels_removing) | set(labels_adding)
            issue.edit(labels=list(new_labels))
        
        return (True, status_msg)
        
    except GithubException as e:
        return (False, f"#{issue.number}: GitHub API error - {str(e)}")
    except Exception as e:
        return (False, f"#{issue.number}: Error - {str(e)}")


def main():
    """Main script execution"""
    parser = argparse.ArgumentParser(
        description="Update labels on resolved domain issues from Phase 1-3 analysis",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Dry run for all domains
  python3 update-domain-issue-labels.py --dry-run
  
  # Update only shop management issues
  python3 update-domain-issue-labels.py --domain shopmgmt --dry-run
  
  # Apply changes for location domain
  python3 update-domain-issue-labels.py --domain location --token ghp_xxxxx
  
  # Apply changes using GITHUB_TOKEN env var
  GITHUB_TOKEN=ghp_xxxxx python3 update-domain-issue-labels.py

Supported domains: all, shopmgmt, location, people, security, pricing
        """
    )
    parser.add_argument(
        "--domain",
        default="all",
        choices=["all", "shopmgmt", "location", "people", "security", "pricing"],
        help="Domain filter (default: all)"
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
    
    # Filter issues by domain
    if args.domain == "all":
        issues_to_process = DOMAIN_ISSUES.items()
    else:
        issues_to_process = [
            (num, data) for num, data in DOMAIN_ISSUES.items()
            if data["domain"] == args.domain
        ]
    
    # Print header
    print_header(args.domain)
    
    if args.dry_run:
        print_colored("DRY RUN MODE - No changes will be made", Colors.YELLOW)
        print()
    
    # Group issues by domain for display
    issues_by_domain = {}
    for num, data in issues_to_process:
        domain = data["domain"]
        if domain not in issues_by_domain:
            issues_by_domain[domain] = []
        issues_by_domain[domain].append((num, data))
    
    # Process each issue
    success_count = 0
    failure_count = 0
    
    print_colored("Processing issues:", Colors.CYAN)
    print()
    
    for domain in sorted(issues_by_domain.keys()):
        domain_issues = issues_by_domain[domain]
        print_colored(f"  {domain.upper()} Domain:", Colors.MAGENTA)
        
        for issue_num, data in sorted(domain_issues, key=lambda x: x[0]):
            try:
                issue = repo.get_issue(issue_num)
                labels_add = data.get("labels_add", [])
                success, message = update_issue_labels(
                    issue, 
                    labels_add, 
                    LABELS_TO_REMOVE, 
                    args.dry_run
                )
                
                if success:
                    print(f"    {Colors.GREEN}✓{Colors.NC} {message}")
                    success_count += 1
                else:
                    print(f"    {Colors.RED}✗{Colors.NC} {message}")
                    failure_count += 1
                    
            except GithubException as e:
                print(f"    {Colors.RED}✗{Colors.NC} #{issue_num}: Could not fetch issue - {str(e)}")
                failure_count += 1
        
        print()
    
    # Print summary
    print_colored("=" * 70, Colors.BLUE)
    print_colored("Summary", Colors.BLUE)
    print_colored("=" * 70, Colors.BLUE)
    print(f"Total issues processed: {len(issues_to_process)}")
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
    print(f"  {Colors.GREEN}Added per domain:{Colors.NC}")
    print(f"    - Shop Management: analysis:complete, ready:backend-implementation, ready:frontend-implementation")
    print(f"    - Location: analysis:in-progress, phase:contract-discovery")
    print(f"    - People: analysis:in-progress, phase:data-contracts")
    print(f"    - Security: analysis:in-progress, phase:data-contracts")
    print(f"    - Pricing: analysis:in-progress, phase:data-contracts")
    
    print()
    print_colored("Issues updated by domain:", Colors.CYAN)
    for domain in sorted(issues_by_domain.keys()):
        domain_issues = issues_by_domain[domain]
        print(f"  {Colors.MAGENTA}{domain.upper()}{Colors.NC}:")
        for issue_num, data in sorted(domain_issues, key=lambda x: x[0]):
            print(f"    #{issue_num} - {data['title']}")
    
    print()
    print_colored("Next steps:", Colors.CYAN)
    print("  1. Review Phase documentation at:")
    print("     /durion/Durion-Processing-ShopMgmt-Phase*.md (for shop management)")
    print("     /durion/domains/location/location-questions.md")
    print("     /durion/domains/people/people-questions.md")
    print("     /durion/domains/security/security-questions.md")
    print("     /durion/domains/pricing/pricing-questions.md")
    print("  2. Begin backend/frontend implementation")
    print("  3. Reference GitHub issues for detailed acceptance criteria")
    print()


if __name__ == "__main__":
    main()
