#!/usr/bin/env python3
"""
Generate CAPABILITY_MANIFEST.yaml files for GitHub issues with type:capability label.

For each capability issue in the durion repository, this script:
- Extracts parent capability and child stories (backend + frontend)
- Determines domain labels from child stories
- Maps domains to backend modules and frontend repositories
- Generates paths to business rule files (AGENT_GUIDE.md, BACKEND_CONTRACT_GUIDE.md, DOMAIN_NOTES.md)
- Generates wireframe paths: /domains/{domain}/.ui/{story-title}-{issue #}.md
- Maps domains to openapi.json paths in backend modules
- Creates CAPABILITY_MANIFEST.yaml in docs/capabilities/<cap-id>/

Usage:
    # Generate all capabilities
    python3 generate_capability_manifests.py --token TOKEN
    
    # Generate a single capability (issue #275)
    python3 generate_capability_manifests.py --capability 275 --token TOKEN
    
    # Generate a range of capabilities (issues #270 to #280)
    python3 generate_capability_manifests.py --range 270 280 --token TOKEN
    
    # Generate specific capabilities (issues #270, #275, #280)
    python3 generate_capability_manifests.py --list 270 275 280 --token TOKEN

Auth:
    Provide a GitHub token with `repo` scope via --token or GITHUB_TOKEN.
"""

import argparse
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional

from github import Github, GithubException
import requests
import yaml


OWNER = "louisburroughs"
SOURCE_REPO = "durion"
CAPABILITY_LABEL = "type:capability"
TARGET_REPOS = ["durion", "durion-moqui-frontend", "durion-positivity-backend"]

# Domain to backend module mapping
DOMAIN_TO_BACKEND_MODULE = {
    'workexec': 'pos-workorder',
    'shopmgmt': 'pos-shop-manager',
    'order': 'pos-order',
    'location': 'pos-location',
    'people': 'pos-people',
    'pricing': 'pos-price',
    'security': 'pos-security-service',
    'accounting': 'pos-accounting',
    'crm': 'pos-customer',
    'inventory': 'pos-inventory'
}

# Domain to frontend repository mapping (component repos)
DOMAIN_TO_FRONTEND_COMPONENT = {
    'workexec': ["durion-workexec"],  # Coordination only
    'shopmgmt': ["durion-shopmgr"],  # Coordination only
    'order': ["durion-product"],
    'location': ["durion-hr"],
    'people': ["durion-hr"],
    'pricing': ["durion-product"],
    'security': ["durion-hr"],
    'accounting': ["durion-accounting"],
    'crm': ["durion-crm"],
    'inventory': ["durion-inventory"]
}


class CapabilityManifestGenerator:
    def __init__(self, gh: Github, token: str, dry_run: bool = False, verbose: bool = False):
        self.gh = gh
        self.token = token
        self.dry_run = dry_run
        self.verbose = verbose
        self.repos: Dict[str, object] = {}  # Cache for repositories
        self.workspace_root = Path(__file__).parent.parent.absolute()
        # API base URL for REST endpoints not covered by PyGithub
        self.api_base_url = "https://api.github.com"
        self.api_headers = {
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28"
        }

    def _vprint(self, message: str, indent: int = 0) -> None:
        """Print message only if verbose mode is enabled."""
        if self.verbose:
            prefix = "  " * indent
            print(f"{prefix}[VERBOSE] {message}")

    def initialize(self) -> None:
        try:
            for repo_name in TARGET_REPOS:
                self.repos[repo_name] = self.gh.get_repo(f"{OWNER}/{repo_name}")
                self._vprint(f"Initialized repo: {OWNER}/{repo_name}")
            self.durion_repo = self.repos[SOURCE_REPO]
        except GithubException as exc:
            raise RuntimeError(f"Could not access repositories: {exc}")

    def fetch_sub_issues(self, repo_name: str, issue_number: int) -> List[Dict]:
        """
        Fetch sub-issues for a given issue using GitHub's sub-issues REST API.
        GET /repos/{owner}/{repo}/issues/{issue_number}/sub_issues
        
        Returns: List of sub-issue dictionaries with full issue data
        """
        self._vprint(f"Fetching sub-issues for {repo_name}#{issue_number}")
        url = f"{self.api_base_url}/repos/{OWNER}/{repo_name}/issues/{issue_number}/sub_issues"
        
        all_sub_issues = []
        page = 1
        per_page = 100
        
        while True:
            try:
                response = requests.get(
                    url,
                    headers=self.api_headers,
                    params={"per_page": per_page, "page": page}
                )
                
                if response.status_code == 404:
                    self._vprint(f"  No sub-issues endpoint or issue not found for {repo_name}#{issue_number}", 1)
                    return []
                
                response.raise_for_status()
                sub_issues = response.json()
                
                if not sub_issues:
                    break
                    
                self._vprint(f"  Page {page}: Found {len(sub_issues)} sub-issues", 1)
                all_sub_issues.extend(sub_issues)
                
                if len(sub_issues) < per_page:
                    break
                page += 1
                
            except requests.RequestException as e:
                self._vprint(f"  Error fetching sub-issues: {e}", 1)
                return []
        
        self._vprint(f"  Total sub-issues found: {len(all_sub_issues)}", 1)
        return all_sub_issues

    def fetch_parent_issue(self, repo_name: str, issue_number: int) -> Optional[Dict]:
        """
        Fetch the parent issue for a given issue using GitHub's sub-issues REST API.
        GET /repos/{owner}/{repo}/issues/{issue_number}/parent
        
        Returns: Parent issue dictionary or None if no parent
        """
        self._vprint(f"Fetching parent issue for {repo_name}#{issue_number}")
        url = f"{self.api_base_url}/repos/{OWNER}/{repo_name}/issues/{issue_number}/parent"
        
        try:
            response = requests.get(url, headers=self.api_headers)
            
            if response.status_code == 404:
                self._vprint(f"  No parent issue found for {repo_name}#{issue_number}", 1)
                return None
            
            response.raise_for_status()
            parent = response.json()
            self._vprint(f"  Parent issue: #{parent.get('number')} - {parent.get('title')}", 1)
            return parent
            
        except requests.RequestException as e:
            self._vprint(f"  Error fetching parent issue: {e}", 1)
            return None

    def fetch_capability_issues(self) -> List:
        """Fetch all issues with type:capability label from durion repository."""
        issues = []
        for issue in self.durion_repo.get_issues(state="open", labels=[CAPABILITY_LABEL]):
            if issue.pull_request is not None:
                continue
            issues.append(issue)
        return issues

    def extract_capability_id(self, issue) -> Optional[str]:
        """Extract capability ID from issue labels (CAP:xxx format)."""
        for label in issue.labels:
            if label.name.startswith("CAP:"):
                return label.name
        return None

    def extract_domain_label(self, issue) -> Optional[str]:
        """Extract domain label from issue (domain:xxx format)."""
        for label in issue.labels:
            if label.name.startswith("domain:"):
                return label.name.split(":")[1]
        return None

    def find_child_stories_by_labels(self, capability_id: str) -> List[Tuple[str, int, str]]:
        """
        Find child stories across all repositories by searching for issues with:
        - Label: "type:story"
        - Label: capability_id (e.g., "CAP:253")
        Returns: List of (repo_name, issue_num, title)
        
        NOTE: This is the LEGACY label-based approach. Use fetch_sub_issues() for the 
        new GitHub sub-issues hierarchy.
        """
        self._vprint(f"[LEGACY] Searching for stories with labels: type:story, {capability_id}")
        children = []
        
        for repo_name in TARGET_REPOS:
            try:
                repo = self.repos[repo_name]
                self._vprint(f"  Searching in {repo_name}...", 1)
                # Search for issues with both type:story and the capability ID label
                for issue in repo.get_issues(state="open", labels=["type:story", capability_id]):
                    if issue.pull_request is not None:
                        continue
                    children.append((repo_name, issue.number, issue.title))
                    self._vprint(f"    Found: #{issue.number} - {issue.title}", 2)
                    if self.dry_run:
                        print(f"    Found child story in {repo_name}: #{issue.number} - {issue.title}")
            except Exception as e:
                self._vprint(f"    Error searching {repo_name}: {e}", 2)
                if self.dry_run:
                    print(f"    Error searching {repo_name}: {e}")
        
        self._vprint(f"  Total label-based children found: {len(children)}", 1)
        return children

    def find_parent_stories_via_subissues(self, capability_issue) -> List[Dict]:
        """
        Find parent stories (sub-issues of the capability issue) using GitHub's sub-issues API.
        These are the direct children of a capability issue (type:capability).
        
        Returns: List of sub-issue dictionaries representing parent stories
        """
        self._vprint(f"Finding parent stories for capability #{capability_issue.number}")
        
        # Get sub-issues of the capability issue
        sub_issues = self.fetch_sub_issues(SOURCE_REPO, capability_issue.number)
        
        if not sub_issues:
            self._vprint("  No sub-issues found via API", 1)
            return []
        
        parent_stories = []
        for si in sub_issues:
            issue_num = si.get("number")
            title = si.get("title", "")
            state = si.get("state", "unknown")
            labels = [l.get("name", "") for l in si.get("labels", [])]
            repo_url = si.get("repository_url", "")
            
            # Extract repo name from URL: https://api.github.com/repos/{owner}/{repo}
            repo_name = repo_url.split("/")[-1] if repo_url else SOURCE_REPO
            
            self._vprint(f"  Sub-issue #{issue_num}: {title}", 1)
            self._vprint(f"    State: {state}, Labels: {labels}", 2)
            self._vprint(f"    Repository: {repo_name}", 2)
            
            parent_stories.append({
                "issue_number": issue_num,
                "title": title,
                "state": state,
                "labels": labels,
                "repo_name": repo_name,
                "raw": si
            })
        
        self._vprint(f"  Total parent stories (sub-issues): {len(parent_stories)}", 1)
        return parent_stories

    def find_story_children_via_subissues(self, parent_story: Dict) -> Dict[str, List[Dict]]:
        """
        Find the children of a parent story (backend and frontend issues).
        These are sub-issues of the parent story.
        
        Returns: Dict with 'backend' and 'frontend' lists of child issue info
        """
        repo_name = parent_story.get("repo_name", SOURCE_REPO)
        issue_number = parent_story.get("issue_number")
        
        self._vprint(f"Finding children for parent story #{issue_number} in {repo_name}")
        
        # Get sub-issues of the parent story
        sub_issues = self.fetch_sub_issues(repo_name, issue_number)
        
        children = {"backend": [], "frontend": []}
        
        for si in sub_issues:
            issue_num = si.get("number")
            title = si.get("title", "")
            labels = [l.get("name", "") for l in si.get("labels", [])]
            repo_url = si.get("repository_url", "")
            state = si.get("state", "unknown")
            
            # Extract repo name from URL
            child_repo = repo_url.split("/")[-1] if repo_url else ""
            
            self._vprint(f"    Child #{issue_num}: {title}", 2)
            self._vprint(f"      Repository: {child_repo}, State: {state}", 3)
            self._vprint(f"      Labels: {labels}", 3)
            
            child_info = {
                "issue_number": issue_num,
                "title": title,
                "state": state,
                "labels": labels,
                "repo_name": child_repo,
                "raw": si
            }
            
            # Categorize by repository
            if child_repo == "durion-positivity-backend":
                children["backend"].append(child_info)
                self._vprint(f"      -> Categorized as BACKEND", 3)
            elif child_repo == "durion-moqui-frontend":
                children["frontend"].append(child_info)
                self._vprint(f"      -> Categorized as FRONTEND", 3)
            else:
                self._vprint(f"      -> Unknown repository, skipping", 3)
        
        self._vprint(f"    Backend children: {len(children['backend'])}, Frontend children: {len(children['frontend'])}", 2)
        return children

    def get_issue(self, repo_name: str, issue_num: int):
        """Fetch an issue from a specific repository."""
        try:
            repo = self.gh.get_repo(f"{OWNER}/{repo_name}")
            return repo.get_issue(issue_num)
        except GithubException:
            return None

    def find_wireframe_files(self, domain: str, issue_num: int, story_title: str) -> List[Dict[str, str]]:
        """Find wireframe markdown files and meta files for a story in /domains/{domain}/.ui/.
        Returns: List of dicts with 'path' and 'type' keys
        """
        if not domain:
            if self.dry_run:
                print(f"      [wireframe debug] No domain provided for story #{issue_num}")
            return []
        
        wireframe_dir = self.workspace_root / "domains" / domain / ".ui"
        if self.dry_run:
            print(f"      [wireframe debug] Looking for wireframes in: {wireframe_dir}")
            print(f"      [wireframe debug] Directory exists: {wireframe_dir.exists()}")
        
        if not wireframe_dir.exists():
            if self.dry_run:
                print(f"      [wireframe debug] Wireframe directory does not exist: {wireframe_dir}")
            return []
        
        # Normalize story title for filename matching (lowercase, replace spaces with hyphens)
        normalized_title = story_title.lower().replace(" ", "-")
        
        # Look for wireframe markdown files: *{issue_num}.wf.md
        md_pattern = f"*{issue_num}.wf.md"
        # Look for wireframe meta files: *{issue_num}.wf.meta.json
        meta_pattern = f"*{issue_num}.wf.meta.json"
        
        if self.dry_run:
            print(f"      [wireframe debug] Markdown pattern: {md_pattern}")
            print(f"      [wireframe debug] Meta pattern: {meta_pattern}")
            print(f"      [wireframe debug] Story title (normalized): {normalized_title}")
        
        wireframes = []
        
        # Find markdown wireframe files
        for file in wireframe_dir.glob(md_pattern):
            rel_path = file.relative_to(self.workspace_root)
            wireframes.append({
                "path": str(rel_path),
                "type": "wireframe"
            })
            if self.dry_run:
                print(f"      [wireframe debug] Found wireframe markdown: {rel_path}")
        
        # Find wireframe meta files
        for file in wireframe_dir.glob(meta_pattern):
            rel_path = file.relative_to(self.workspace_root)
            wireframes.append({
                "path": str(rel_path),
                "type": "meta"
            })
            if self.dry_run:
                print(f"      [wireframe debug] Found wireframe meta: {rel_path}")
        
        if self.dry_run:
            print(f"      [wireframe debug] Total wireframe files found: {len(wireframes)}")
        
        return wireframes

    def generate_business_rules_paths(self, domain: str) -> List[str]:
        """Generate paths to domain business rule files."""
        if not domain:
            return []
        
        base_path = f"domains/{domain}/.business-rules"
        files = [
            "AGENT_GUIDE.md",
            "BACKEND_CONTRACT_GUIDE.md",
            "DOMAIN_NOTES.md"
        ]
        
        paths = []
        for file in files:
            full_path = self.workspace_root / base_path / file
            if full_path.exists():
                paths.append({
                    "repo": f"{OWNER}/{SOURCE_REPO}",
                    "path": f"{base_path}/{file}"
                })
        
        return paths

    def generate_openapi_path(self, domain: str) -> Optional[Dict[str, str]]:
        """Map domain to backend module and generate openapi.json path."""
        if not domain or domain not in DOMAIN_TO_BACKEND_MODULE:
            return None
        
        module = DOMAIN_TO_BACKEND_MODULE[domain]
        return {
            "producer_repo": f"{OWNER}/durion-positivity-backend",
            "spec_path": f"{module}/target/openapi.json",
            "generated": True
        }

    def generate_manifest(self, capability_issue) -> Dict:
        """Generate CAPABILITY_MANIFEST data structure for a capability issue.
        
        Hierarchy:
        - Capability (parent_capability) - the type:capability issue
          - Parent Stories (sub-issues of capability) - intermediate level
            - Backend Child (sub-issue in durion-positivity-backend)
            - Frontend Child (sub-issue in durion-moqui-frontend)
        """
        capability_id = self.extract_capability_id(capability_issue)
        if not capability_id:
            raise ValueError(f"Issue #{capability_issue.number} missing cap: label")
        
        domain = self.extract_domain_label(capability_issue)
        
        self._vprint(f"\n{'='*60}")
        self._vprint(f"CAPABILITY: #{capability_issue.number} - {capability_issue.title}")
        self._vprint(f"  Capability ID: {capability_id}")
        self._vprint(f"  Domain: {domain}")
        self._vprint(f"  Labels: {[l.name for l in capability_issue.labels]}")
        self._vprint(f"{'='*60}")
        
        # ========== NEW: Use sub-issues API to find parent stories ==========
        self._vprint("\n--- Finding Parent Stories via Sub-Issues API ---")
        parent_stories = self.find_parent_stories_via_subissues(capability_issue)
        
        # ========== FALLBACK: Also try label-based search for comparison ==========
        self._vprint("\n--- Finding Stories via Label-Based Search (Legacy) ---")
        label_based_children = self.find_child_stories_by_labels(capability_id)
        
        # Compare and log discrepancies
        if self.verbose:
            self._vprint("\n--- Comparing Sub-Issues vs Label-Based Results ---")
            subissue_nums = {ps.get("issue_number") for ps in parent_stories}
            label_nums = {num for _, num, _ in label_based_children}
            
            only_in_subissues = subissue_nums - label_nums
            only_in_labels = label_nums - subissue_nums
            in_both = subissue_nums & label_nums
            
            self._vprint(f"  Found in sub-issues API only: {only_in_subissues or 'none'}")
            self._vprint(f"  Found in label search only: {only_in_labels or 'none'}")
            self._vprint(f"  Found in both: {in_both or 'none'}")
        
        # ========== Process each parent story to find its backend/frontend children ==========
        all_stories = []
        
        if parent_stories:
            self._vprint("\n--- Processing Parent Stories and Their Children ---")
            for ps in parent_stories:
                ps_num = ps.get("issue_number")
                ps_title = ps.get("title")
                ps_repo = ps.get("repo_name")
                
                self._vprint(f"\nParent Story #{ps_num}: {ps_title}")
                self._vprint(f"  Repository: {ps_repo}")
                
                # Find backend/frontend children of this parent story
                story_children = self.find_story_children_via_subissues(ps)
                
                # Build story structure
                backend_child = None
                frontend_child = None
                
                # Process backend children
                for bc in story_children.get("backend", []):
                    if backend_child is None:  # Take first one
                        backend_child = {
                            "repo": f"{OWNER}/durion-positivity-backend",
                            "issue": bc.get("issue_number")
                        }
                        self._vprint(f"  Backend Child: #{bc.get('issue_number')} - {bc.get('title')}")
                
                # Process frontend children
                for fc in story_children.get("frontend", []):
                    if frontend_child is None:  # Take first one
                        fc_issue_num = fc.get("issue_number")
                        fc_title = fc.get("title")
                        child_domain = domain  # Use capability domain
                        
                        # Try to get more details from the issue
                        child_issue = self.get_issue("durion-moqui-frontend", fc_issue_num)
                        if child_issue:
                            child_domain = self.extract_domain_label(child_issue) or domain
                        
                        frontend_child = {
                            "repo": f"{OWNER}/durion-moqui-frontend",
                            "issue": fc_issue_num,
                            "impacted_component_repos": [
                                f"{OWNER}/{comp}" for comp in DOMAIN_TO_FRONTEND_COMPONENT.get(child_domain, [])
                            ]
                        }
                        
                        # Find wireframes
                        wireframes = self.find_wireframe_files(child_domain, fc_issue_num, fc_title)
                        if wireframes:
                            frontend_child["wireframes"] = [
                                {
                                    "repo": f"{OWNER}/{SOURCE_REPO}",
                                    "path": wf["path"],
                                    "type": wf["type"]
                                }
                                for wf in wireframes
                            ]
                        
                        self._vprint(f"  Frontend Child: #{fc_issue_num} - {fc_title}")
                
                story = {
                    "parent_story": {
                        "repo": f"{OWNER}/{ps_repo}",
                        "issue": ps_num,
                        "title": ps_title,
                        "labels": ps.get("labels", [])
                    },
                    "children": {}
                }
                
                if backend_child:
                    story["children"]["backend"] = backend_child
                
                if frontend_child:
                    business_rules = self.generate_business_rules_paths(domain)
                    if business_rules:
                        frontend_child["business_rules"] = business_rules
                    story["children"]["frontend"] = frontend_child
                
                # Contract guide section
                if domain:
                    contract_guide = {
                        "repo": f"{OWNER}/{SOURCE_REPO}",
                        "path": f"domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md",
                        "anchor": "",
                        "status": "draft"
                    }
                    
                    openapi = self.generate_openapi_path(domain)
                    if openapi:
                        contract_guide["openapi"] = openapi
                    
                    story["contract_guide"] = contract_guide
                
                # PR links section
                story["pr_links"] = {
                    "durion_contract_pr": "",
                    "backend_pr": "",
                    "frontend_prs": []
                }
                
                story["merge_order"] = [
                    "durion_contract_pr",
                    "backend_pr",
                    "frontend_prs"
                ]
                
                all_stories.append(story)
        
        # If no sub-issues found, fall back to legacy label-based approach
        if not all_stories and label_based_children:
            self._vprint("\n--- Falling back to label-based stories (no sub-issues found) ---")
            
            backend_child = None
            frontend_child = None
            
            for repo_name, issue_num, title in label_based_children:
                child_issue = self.get_issue(repo_name, issue_num)
                if not child_issue:
                    continue
                
                if repo_name == "durion-positivity-backend":
                    backend_child = {
                        "repo": f"{OWNER}/{repo_name}",
                        "issue": issue_num
                    }
                elif repo_name == "durion-moqui-frontend":
                    child_domain = self.extract_domain_label(child_issue) or domain
                    frontend_child = {
                        "repo": f"{OWNER}/{repo_name}",
                        "issue": issue_num,
                        "impacted_component_repos": [
                            f"{OWNER}/{comp}" for comp in DOMAIN_TO_FRONTEND_COMPONENT.get(child_domain, [])
                        ]
                    }
                    
                    wireframes = self.find_wireframe_files(child_domain, issue_num, title)
                    if wireframes:
                        frontend_child["wireframes"] = [
                            {
                                "repo": f"{OWNER}/{SOURCE_REPO}",
                                "path": wf["path"],
                                "type": wf["type"]
                            }
                            for wf in wireframes
                        ]
            
            # Build legacy single-story structure
            story = {
                "parent": {
                    "repo": f"{OWNER}/{SOURCE_REPO}",
                    "issue": capability_issue.number,
                    "title": capability_issue.title,
                    "domain": domain,
                    "labels": [label.name for label in capability_issue.labels]
                },
                "children": {}
            }
            
            if backend_child:
                story["children"]["backend"] = backend_child
            
            if frontend_child:
                business_rules = self.generate_business_rules_paths(domain)
                if business_rules:
                    frontend_child["business_rules"] = business_rules
                story["children"]["frontend"] = frontend_child
            
            if domain:
                contract_guide = {
                    "repo": f"{OWNER}/{SOURCE_REPO}",
                    "path": f"domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md",
                    "anchor": "",
                    "status": "draft"
                }
                
                openapi = self.generate_openapi_path(domain)
                if openapi:
                    contract_guide["openapi"] = openapi
                
                story["contract_guide"] = contract_guide
            
            story["pr_links"] = {
                "durion_contract_pr": "",
                "backend_pr": "",
                "frontend_prs": []
            }
            
            story["merge_order"] = [
                "durion_contract_pr",
                "backend_pr",
                "frontend_prs"
            ]
            
            all_stories.append(story)
        
        # Build full manifest
        now_utc = datetime.now(timezone.utc).isoformat()
        
        manifest = {
            "meta": {
                "capability_id": capability_id,
                "capability_name": capability_issue.title,
                "owner_repo": f"{OWNER}/{SOURCE_REPO}",
                "created_utc": now_utc,
                "last_updated_utc": now_utc
            },
            "parent_capability": {
                "repo": f"{OWNER}/{SOURCE_REPO}",
                "issue": capability_issue.number,
                "title": capability_issue.title,
                "domain": domain,
                "labels": [label.name for label in capability_issue.labels]
            },
            "coordination": {
                "github_project_url": "https://github.com/users/louisburroughs/projects/1",
                "status_field_name": "status:{Backlog,Ready,In Progress,In Review,Done}",
                "preferred_branch_prefix": "cap/"
            },
            "contract_registry": {
                "root_path": "domains",
                "guide_path_suffix": ".business-rules/BACKEND_CONTRACT_GUIDE.md",
                "contract_status_markers": ["draft", "stable-for-ui"]
            },
            "repositories": [
                {
                    "name": "durion-positivity-backend",
                    "slug": f"{OWNER}/durion-positivity-backend",
                    "type": "backend",
                    "notes": ""
                },
                {
                    "name": "durion-moqui-frontend",
                    "slug": f"{OWNER}/durion-moqui-frontend",
                    "type": "frontend-coordination",
                    "notes": "Frontend child issues live here; code changes may land in component repos."
                }
            ],
            "stories": all_stories,
            "execution": {
                "agent_rules": [
                    "Backend changes that affect API/event behavior REQUIRE a durion contract PR.",
                    "Frontend work starts only when contract status is stable-for-ui.",
                    f"One branch per repo per capability: {capability_id.replace(':', '/')}."
                ],
                "definition_of_done": [
                    "All PRs merged and required checks green",
                    "Contract guide entry updated with examples + behavioral assertions",
                    "Provider behavioral contract tests added/updated",
                    "Frontend wired to stable contract"
                ]
            }
        }
        
        # Add component repos if present
        if domain and DOMAIN_TO_FRONTEND_COMPONENT.get(domain):
            for comp_repo in DOMAIN_TO_FRONTEND_COMPONENT[domain]:
                manifest["repositories"].append({
                    "name": comp_repo,
                    "slug": f"{OWNER}/{comp_repo}",
                    "type": "frontend-component",
                    "notes": ""
                })
        
        self._vprint(f"\n--- Manifest Summary ---")
        self._vprint(f"  Total stories: {len(all_stories)}")
        for i, s in enumerate(all_stories):
            ps = s.get("parent_story") or s.get("parent")
            self._vprint(f"  Story {i+1}: #{ps.get('issue')} - {ps.get('title', 'N/A')}")
            children = s.get("children", {})
            if children.get("backend"):
                self._vprint(f"    Backend: #{children['backend'].get('issue')}")
            if children.get("frontend"):
                self._vprint(f"    Frontend: #{children['frontend'].get('issue')}")
        
        return manifest

    def write_manifest_file(self, capability_id: str, manifest: Dict) -> str:
        """Write CAPABILITY_MANIFEST.yaml to docs/capabilities/<cap-id>/."""
        # Convert cap:xxx to cap-xxx for directory name
        cap_dir_name = capability_id.replace(":", "-")
        manifest_dir = self.workspace_root / "docs" / "capabilities" / cap_dir_name
        manifest_file = manifest_dir / "CAPABILITY_MANIFEST.yaml"
        
        if self.dry_run:
            print(f"[DRY-RUN] Would create: {manifest_file}")
            return str(manifest_file)
        
        # Create directory if it doesn't exist
        manifest_dir.mkdir(parents=True, exist_ok=True)
        
        # Write YAML file
        with open(manifest_file, 'w') as f:
            yaml.dump(manifest, f, default_flow_style=False, sort_keys=False, allow_unicode=True)
        
        return str(manifest_file)

    def filter_issues_by_numbers(self, all_issues: List, issue_numbers: List[int]) -> List:
        """Filter issues to only those with numbers in the given list."""
        issue_map = {issue.number: issue for issue in all_issues}
        filtered = [issue_map[num] for num in issue_numbers if num in issue_map]
        return filtered

    def filter_issues_by_range(self, all_issues: List, start: int, end: int) -> List:
        """Filter issues to only those within the given range (inclusive)."""
        return [issue for issue in all_issues if start <= issue.number <= end]

    def run(self, issue_numbers: Optional[List[int]] = None, issue_range: Optional[Tuple[int, int]] = None) -> None:
        """Main execution: fetch capabilities and generate manifests.
        
        Args:
            issue_numbers: Optional list of specific issue numbers to process
            issue_range: Optional tuple (start, end) for range of issue numbers (inclusive)
        """
        print(f"Fetching capability issues from {OWNER}/{SOURCE_REPO}...")
        capability_issues = self.fetch_capability_issues()
        print(f"Found {len(capability_issues)} capability issues")
        
        # Apply filters if specified
        if issue_numbers:
            capability_issues = self.filter_issues_by_numbers(capability_issues, issue_numbers)
            print(f"Filtered to {len(capability_issues)} issue(s) by number: {issue_numbers}")
        elif issue_range:
            start, end = issue_range
            capability_issues = self.filter_issues_by_range(capability_issues, start, end)
            print(f"Filtered to {len(capability_issues)} issue(s) in range {start}..{end}")
        
        if not capability_issues:
            print("No capability issues to process.")
            return
        
        for issue in capability_issues:
            try:
                print(f"\nProcessing capability #{issue.number}: {issue.title}")
                manifest = self.generate_manifest(issue)
                capability_id = manifest["meta"]["capability_id"]
                manifest_path = self.write_manifest_file(capability_id, manifest)
                
                if self.dry_run:
                    print(f"  [DRY-RUN] Generated manifest for {capability_id}")
                else:
                    print(f"  ✅ Created: {manifest_path}")
                    
            except Exception as e:
                print(f"  ❌ Error processing issue #{issue.number}: {e}")
                if self.dry_run:
                    import traceback
                    traceback.print_exc()


def main():
    parser = argparse.ArgumentParser(
        description="Generate CAPABILITY_MANIFEST.yaml files",
        epilog="""
Examples:
  # Generate all capabilities
  python3 generate_capability_manifests.py --token TOKEN
  
  # Generate a single capability (issue #275)
  python3 generate_capability_manifests.py --capability 275 --token TOKEN
  
  # Generate a single capability with verbose debug output
  python3 generate_capability_manifests.py --capability 275 --verbose --token TOKEN
  
  # Generate a range of capabilities (issues #270 to #280)
  python3 generate_capability_manifests.py --range 270 280 --token TOKEN
  
  # Generate specific capabilities (issues #270, #275, #280)
  python3 generate_capability_manifests.py --list 270 275 280 --token TOKEN
        """,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--capability", type=int, help="Generate manifest for a single capability issue number")
    parser.add_argument("--range", type=int, nargs=2, metavar=("START", "END"), 
                        help="Generate manifests for issue numbers in range START..END (inclusive)")
    parser.add_argument("--list", type=int, nargs="+", metavar="ISSUE_NUMBER",
                        help="Generate manifests for specific issue numbers (space-separated)")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be done without making changes")
    parser.add_argument("--verbose", "-v", action="store_true", 
                        help="Enable verbose debug output showing sub-issue relationships and API calls")
    parser.add_argument("--token", help="GitHub token (or use GITHUB_TOKEN env var)")
    args = parser.parse_args()
    
    # Validate argument combinations
    arg_count = sum([args.capability is not None, args.range is not None, args.list is not None])
    if arg_count > 1:
        print("Error: Only one of --capability, --range, or --list can be specified.", file=sys.stderr)
        sys.exit(1)
    
    token = args.token or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("Error: GitHub token required. Provide via --token or GITHUB_TOKEN env var.", file=sys.stderr)
        sys.exit(1)
    
    gh = Github(token)
    generator = CapabilityManifestGenerator(gh, token, dry_run=args.dry_run, verbose=args.verbose)
    
    try:
        generator.initialize()
        
        if args.capability is not None:
            generator.run(issue_numbers=[args.capability])
        elif args.range is not None:
            start, end = args.range
            generator.run(issue_range=(start, end))
        elif args.list is not None:
            generator.run(issue_numbers=args.list)
        else:
            generator.run()
            
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
