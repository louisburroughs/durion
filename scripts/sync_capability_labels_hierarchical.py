#!/usr/bin/env python3
"""
Hierarchical Capability Label Synchronization

Extracts issues with label `type:capability` from durion and applies `CAP:{###}` labels
to the capability and all of its descendants (child, grandchild, etc. issues).

Issue hierarchy is discovered via GitHub's native sub-issue relationships and
explicit issue references in PR/issue bodies.

Capability issues → Child issues (durion) → Grandchild issues (durion, durion-moqui-frontend, durion-positivity-backend)

Usage:
    python3 sync_capability_labels_hierarchical.py [--dry-run] [--token TOKEN]

Auth:
    Provide a GitHub token with `repo` scope via --token or GITHUB_TOKEN.
"""

import argparse
import os
import re
import sys
from typing import Dict, List, Set, Tuple

from github import Github, GithubException
import requests


OWNER = "louisburroughs"
SOURCE_REPO = "durion"
TARGET_REPOS = ["durion", "durion-positivity-backend", "durion-moqui-frontend"]
CAPABILITY_LABEL = "type:capability"
LABEL_COLOR = "0052cc"
DESCRIPTION_LIMIT = 100


class CapabilityLabelSyncer:
    def __init__(self, gh: Github, token: str, dry_run: bool = False):
        self.gh = gh
        self.token = token
        self.dry_run = dry_run
        self.repos: Dict[str, object] = {}
        self.processed_issues: Set[Tuple[str, int]] = set()  # (repo_name, issue_num)
        self.actions: List[str] = []

    def initialize_repos(self) -> None:
        for repo_name in TARGET_REPOS:
            try:
                self.repos[repo_name] = self.gh.get_repo(f"{OWNER}/{repo_name}")
            except GithubException as exc:
                raise RuntimeError(f"Could not access repo {OWNER}/{repo_name}: {exc}")

    def fetch_capability_issues(self) -> List[Tuple[int, str]]:
        issues = []
        repo = self.repos[SOURCE_REPO]
        for issue in repo.get_issues(state="all", labels=[CAPABILITY_LABEL]):
            if issue.pull_request is not None:
                continue
            issues.append((issue.number, issue.title.strip()))
        return issues

    def get_issue(self, repo_name: str, issue_num: int):
        if repo_name not in self.repos:
            return None
        try:
            return self.repos[repo_name].get_issue(issue_num)
        except GithubException:
            return None

    def extract_issue_references(self, text: str, repo_name: str = None) -> List[Tuple[str, int]]:
        """
        Extract issue references from text.
        Patterns:
        - #123 (relative to current repo)
        - owner/repo#123
        - https://github.com/owner/repo/issues/123
        - Markdown links: [text](#123) or [text](owner/repo#123)
        """
        if not text:
            return []
        
        references: List[Tuple[str, int]] = []

        # Pattern 1: #123 (relative reference)
        for match in re.finditer(r'(?:^|[^/\w])#(\d+)(?:[^\w]|$)', text):
            if repo_name:
                references.append((repo_name, int(match.group(1))))

        # Pattern 2: owner/repo#123
        for match in re.finditer(rf'{OWNER}/([\w\-]+)#(\d+)', text):
            repo = match.group(1)
            issue_num = int(match.group(2))
            references.append((repo, issue_num))

        # Pattern 3: https://github.com/owner/repo/issues/123
        for match in re.finditer(rf'github\.com/{OWNER}/([\w\-]+)/issues/(\d+)', text):
            repo = match.group(1)
            issue_num = int(match.group(2))
            references.append((repo, issue_num))
        
        # Pattern 4: Tasklist format: - [ ] #123 or - [x] #123
        for match in re.finditer(r'-\s*\[[ xX]\]\s*#(\d+)', text):
            if repo_name:
                references.append((repo_name, int(match.group(1))))

        return references

    def find_child_issues_graphql(self, repo_name: str, issue_num: int) -> List[Tuple[str, int]]:
        """Fetch sub-issues using GitHub GraphQL API."""
        query = """
        query($owner: String!, $repo: String!, $issueNumber: Int!, $pageSize: Int!) {
          repository(owner: $owner, name: $repo) {
            issue(number: $issueNumber) {
              subIssues(first: $pageSize) {
                totalCount
                nodes {
                  number
                  title
                  repository {
                    nameWithOwner
                  }
                }
              }
            }
          }
        }
        """
        
        variables = {
            "owner": OWNER,
            "repo": repo_name,
            "issueNumber": issue_num,
            "pageSize": 100
        }
        
        try:
            # Use token directly for GraphQL request
            headers = {
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json"
            }
            response = requests.post(
                "https://api.github.com/graphql",
                json={"query": query, "variables": variables},
                headers=headers
            )
            
            if response.status_code == 200:
                data = response.json()
                
                if "errors" in data:
                    return []
                
                if "data" in data and data["data"] and data["data"].get("repository"):
                    repo_data = data["data"]["repository"]
                    if repo_data.get("issue") and repo_data["issue"].get("subIssues"):
                        sub_issues = repo_data["issue"]["subIssues"]
                        total_count = sub_issues.get("totalCount", 0)
                        nodes = sub_issues.get("nodes", [])
                        
                        children = []
                        for item in nodes:
                            # Parse repo name from nameWithOwner (owner/repo format)
                            name_with_owner = item["repository"]["nameWithOwner"]
                            child_repo = name_with_owner.split("/")[-1]  # Get repo name
                            child_num = item["number"]
                            children.append((child_repo, child_num))
                        
                        return children
            else:
                if self.dry_run:
                    print(f"    DEBUG: GraphQL request failed with status {response.status_code}")
                    print(f"    DEBUG: Response: {response.text[:200]}")
        except Exception as e:
            if self.dry_run:
                print(f"    DEBUG: GraphQL query failed: {e}")
            else:
                if self.dry_run:
                    print(f"    DEBUG: GraphQL request failed with status {response.status_code}")
                    print(f"    DEBUG: Response: {response.text[:200]}")
        except Exception as e:
            if self.dry_run:
                print(f"    DEBUG: GraphQL query failed: {e}")
        
        return []

    def find_child_issues(self, repo_name: str, issue_num: int) -> List[Tuple[str, int]]:
        """
        Find child issues using:
        1. GitHub native sub-issues (via GraphQL)
        2. Explicit references in issue body/comments
        """
        children: List[Tuple[str, int]] = []
        issue = self.get_issue(repo_name, issue_num)
        if not issue:
            return children

        # Method 1: Fetch native sub-issues via GraphQL
        graphql_children = self.find_child_issues_graphql(repo_name, issue_num)
        children.extend(graphql_children)

        # Method 2: Check issue body for references
        if hasattr(issue, "body") and issue.body:
            refs = self.extract_issue_references(issue.body, repo_name)
            children.extend(refs)

        # Method 3: Check comments for child references
        try:
            for comment in issue.get_comments():
                if hasattr(comment, "body") and comment.body:
                    refs = self.extract_issue_references(comment.body, repo_name)
                    children.extend(refs)
        except Exception:
            pass

        # Deduplicate and remove self-references
        children = [(r, n) for r, n in set(children) if not (r == repo_name and n == issue_num)]
        
        return children

    def truncate_description(self, text: str, limit: int = DESCRIPTION_LIMIT) -> str:
        if len(text) <= limit:
            return text
        return text[: max(limit - 3, 0)].rstrip() + "..."

    def ensure_label(self, repo_name: str, label_name: str, description: str) -> str:
        """Create or update label in a repo. Returns action status."""
        repo = self.repos.get(repo_name)
        if not repo:
            return "repo-not-found"

        try:
            label = repo.get_label(label_name)
            needs_update = (label.color.lower() != LABEL_COLOR.lower()) or (label.description or "") != description
            if needs_update:
                if self.dry_run:
                    return "would-update"
                label.edit(name=label_name, color=LABEL_COLOR, description=description)
                return "updated"
            return "unchanged"
        except GithubException as exc:
            if getattr(exc, "status", None) == 404:
                if self.dry_run:
                    return "would-create"
                try:
                    repo.create_label(name=label_name, color=LABEL_COLOR, description=description)
                    return "created"
                except GithubException as create_exc:
                    return f"create-error:{create_exc}"
            return f"get-error:{exc}"

    def add_label_to_issue(self, repo_name: str, issue_num: int, label_name: str) -> str:
        """Add label to an issue. Returns action status."""
        issue = self.get_issue(repo_name, issue_num)
        if not issue:
            return "issue-not-found"

        try:
            current_labels = {label.name for label in issue.labels}
            if label_name in current_labels:
                return "already-labeled"
            if self.dry_run:
                return "would-add"
            new_labels = list(current_labels | {label_name})
            issue.edit(labels=new_labels)
            return "added"
        except GithubException as exc:
            return f"error:{exc}"

    def process_issue_tree(self, repo_name: str, issue_num: int, label_name: str, depth: int = 0) -> None:
        """Recursively process an issue and its descendants."""
        key = (repo_name, issue_num)
        if key in self.processed_issues:
            return
        self.processed_issues.add(key)

        indent = "  " * depth
        issue = self.get_issue(repo_name, issue_num)
        if not issue:
            self.actions.append(f"{indent}{repo_name}#{issue_num}: issue-not-found")
            return

        # Add label to this issue
        label_result = self.add_label_to_issue(repo_name, issue_num, label_name)
        issue_link = f"https://github.com/{OWNER}/{repo_name}/issues/{issue_num}"
        self.actions.append(f"{indent}{repo_name}#{issue_num} | {issue.title} | {issue_link}: {label_result}")

        # Find and process children
        children = self.find_child_issues(repo_name, issue_num)
        if children and depth == 0:
            self.actions.append(f"{indent}  → Found {len(children)} direct child references")
        
        for child_repo, child_num in children:
            self.process_issue_tree(child_repo, child_num, label_name, depth + 1)

    def sync(self) -> None:
        """Main sync logic."""
        self.initialize_repos()
        capability_issues = self.fetch_capability_issues()

        if not capability_issues:
            print("No capability issues found with label 'type:capability'.")
            return

        print("=" * 80)
        print("CAPABILITY LABEL SYNCHRONIZATION")
        print("=" * 80)
        print(f"Found {len(capability_issues)} capability issues")
        if self.dry_run:
            print("Mode: DRY RUN (no changes will be made)")
        else:
            print("Mode: APPLY (changes will be made)")
        print("=" * 80)
        print()

        total_labels_to_create = 0
        total_issues_to_label = 0

        for cap_num, cap_title in sorted(capability_issues, key=lambda x: x[0]):
            label_name = f"CAP:{cap_num:03d}"
            description = self.truncate_description(cap_title)

            # Ensure label exists in all target repos
            label_results = {}
            for repo_name in TARGET_REPOS:
                result = self.ensure_label(repo_name, label_name, description)
                label_results[repo_name] = result

            # Count label creations
            creates = sum(1 for r in label_results.values() if "create" in r)
            if creates > 0:
                total_labels_to_create += creates

            print(f"CAPABILITY: {label_name} — {cap_title}")
            print(f"{'─' * 80}")

            # Show label creation status
            print("Label creation across repositories:")
            for repo_name in TARGET_REPOS:
                result = label_results[repo_name]
                status_icon = "→" if "would" in result else "✓" if result == "unchanged" else "!"
                action_text = result.replace("would-", "").upper() if "would" in result else result.upper()
                print(f"  {status_icon} {repo_name:30s} {action_text}")
            print()

            # Process issue tree
            self.processed_issues.clear()
            self.actions.clear()

            self.process_issue_tree(SOURCE_REPO, cap_num, label_name)

            # Count issues that will be labeled
            issues_to_label = sum(1 for a in self.actions if "would-add" in a or "added" in a)
            total_issues_to_label += issues_to_label

            if self.actions:
                print(f"Issues that will be labeled with {label_name}:")
                for action in self.actions:
                    # Format: "{indent}repo#num | title | link: action"
                    # Extract indentation from the beginning of action string
                    indent_count = len(action) - len(action.lstrip())
                    indent_spaces = " " * indent_count
                    
                    # Check if this is a "Found X children" info line
                    if "→ Found" in action:
                        print(f"{indent_spaces}{action.strip()}")
                        continue
                    
                    # Split on last colon to separate issue info from action
                    colon_idx = action.rfind(":")
                    if colon_idx > 0:
                        issue_part = action[:colon_idx].strip()
                        action_part = action[colon_idx + 1:].strip()
                        
                        # Parse issue_part: "repo#num | title | link"
                        parts = issue_part.split("|")
                        if len(parts) >= 2:
                            repo_issue = parts[0].strip()
                            title = parts[1].strip() if len(parts) > 1 else ""
                            link = parts[2].strip() if len(parts) > 2 else ""
                            
                            # Map action results to display text
                            action_map = {
                                "would-add": "→ WILL LABEL",
                                "added": "✓ LABELED",
                                "already-labeled": "○ ALREADY HAS LABEL",
                                "issue-not-found": "✗ NOT FOUND",
                            }
                            action_display = action_part
                            for action_key, display_text in action_map.items():
                                if action_key in action_display:
                                    action_display = display_text
                                    break
                            
                            # Handle error messages
                            if "error:" in action_display.lower():
                                action_display = "✗ ERROR"
                            
                            print(f"{indent_spaces}  {repo_issue:20s} {title[:50]:50s} {action_display}")
                            if link:
                                print(f"{indent_spaces}     {link}")
            else:
                print(f"No child issues found for {label_name}")

            print()
            print()

        # Print summary
        print("=" * 80)
        print("SUMMARY")
        print("=" * 80)
        print(f"Capabilities to process: {len(capability_issues)}")
        print(f"Labels to create: {total_labels_to_create}")
        print(f"Issues to label: {total_issues_to_label}")
        print()
        if self.dry_run:
            print("✓ Dry run complete. Run without --dry-run to apply these changes.")
        else:
            print("✓ Synchronization complete!")
        print("=" * 80)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Sync capability labels hierarchically across repositories"
    )
    parser.add_argument("--dry-run", action="store_true", help="Preview changes without applying")
    parser.add_argument("--token", help="GitHub token (defaults to GITHUB_TOKEN)")
    args = parser.parse_args()

    token = args.token or os.getenv("GITHUB_TOKEN")
    if not token:
        print("Error: GitHub token not provided; set GITHUB_TOKEN or use --token")
        sys.exit(1)

    try:
        gh = Github(token)
    except GithubException as exc:
        print(f"Error: Failed to authenticate with GitHub: {exc}")
        sys.exit(1)

    syncer = CapabilityLabelSyncer(gh, token, dry_run=args.dry_run)
    try:
        syncer.sync()
    except RuntimeError as exc:
        print(f"Error: {exc}")
        sys.exit(1)

if __name__ == "__main__":
    main()