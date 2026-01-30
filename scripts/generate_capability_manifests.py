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
    python3 generate_capability_manifests.py [--dry-run] [--token TOKEN]

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
    def __init__(self, gh: Github, token: str, dry_run: bool = False):
        self.gh = gh
        self.token = token
        self.dry_run = dry_run
        self.repos: Dict[str, object] = {}  # Cache for repositories
        self.workspace_root = Path(__file__).parent.parent.absolute()

    def initialize(self) -> None:
        try:
            for repo_name in TARGET_REPOS:
                self.repos[repo_name] = self.gh.get_repo(f"{OWNER}/{repo_name}")
            self.durion_repo = self.repos[SOURCE_REPO]
        except GithubException as exc:
            raise RuntimeError(f"Could not access repositories: {exc}")

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
        """
        children = []
        
        for repo_name in TARGET_REPOS:
            try:
                repo = self.repos[repo_name]
                # Search for issues with both type:story and the capability ID label
                for issue in repo.get_issues(state="open", labels=["type:story", capability_id]):
                    if issue.pull_request is not None:
                        continue
                    children.append((repo_name, issue.number, issue.title))
                    if self.dry_run:
                        print(f"    Found child story in {repo_name}: #{issue.number} - {issue.title}")
            except Exception as e:
                if self.dry_run:
                    print(f"    Error searching {repo_name}: {e}")
        
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
        """Generate CAPABILITY_MANIFEST data structure for a capability issue."""
        capability_id = self.extract_capability_id(capability_issue)
        if not capability_id:
            raise ValueError(f"Issue #{capability_issue.number} missing cap: label")
        
        domain = self.extract_domain_label(capability_issue)
        
        # Find child stories by searching for type:story label + capability ID label
        children = self.find_child_stories_by_labels(capability_id)
        
        # Categorize children by repository
        backend_child = None
        frontend_child = None
        for repo_name, issue_num, title in children:
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
                
                # Find wireframes
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
        
        # Build stories section
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
        
        # Contract guide section
        if domain:
            contract_guide = {
                "repo": f"{OWNER}/{SOURCE_REPO}",
                "path": f"domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md",
                "anchor": "",  # To be filled manually
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
            "coordination": {
                "github_project_url": "https://github.com/users/louisburroughs/projects/1",  # To be filled manually
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
            "stories": [story],
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

    def run(self) -> None:
        """Main execution: fetch capabilities and generate manifests."""
        print(f"Fetching capability issues from {OWNER}/{SOURCE_REPO}...")
        capability_issues = self.fetch_capability_issues()
        print(f"Found {len(capability_issues)} capability issues")
        
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
    parser = argparse.ArgumentParser(description="Generate CAPABILITY_MANIFEST.yaml files")
    parser.add_argument("--dry-run", action="store_true", help="Show what would be done without making changes")
    parser.add_argument("--token", help="GitHub token (or use GITHUB_TOKEN env var)")
    args = parser.parse_args()
    
    token = args.token or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("Error: GitHub token required. Provide via --token or GITHUB_TOKEN env var.", file=sys.stderr)
        sys.exit(1)
    
    gh = Github(token)
    generator = CapabilityManifestGenerator(gh, token, dry_run=args.dry_run)
    
    try:
        generator.initialize()
        generator.run()
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
