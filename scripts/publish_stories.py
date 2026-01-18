#!/usr/bin/env python3
"""
publish_stories.py

Publishes rewritten stories from story-work/frontend/{issue}/after.md to GitHub issues.

Usage:
  # Publish all pending stories in batch
  python publish_stories.py --story-root ./scripts/story-work/frontend --owner louisburroughs --repo durion-moqui-frontend

  # Publish a single story (for testing)
  python publish_stories.py --story-root ./scripts/story-work/frontend --owner louisburroughs --repo durion-moqui-frontend --issue 65

  # Dry run (preview without making changes)
  python publish_stories.py --story-root ./scripts/story-work/frontend --owner louisburroughs --repo durion-moqui-frontend --dry-run

  # Include recommended labels
  python publish_stories.py --story-root ./scripts/story-work/frontend --owner louisburroughs --repo durion-moqui-frontend --include-recommended

Environment:
  GITHUB_TOKEN: Required for GitHub API access

Exit codes:
  0 - Success
  1 - General error
  2 - Invalid arguments or missing files
  3 - Missing GITHUB_TOKEN
  4 - Parse error
  5 - Blocking/risk labels present (with --fail-on-blocking)
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    import requests
except ImportError:
    print("ERROR: requests library required. Install with: pip install requests", file=sys.stderr)
    sys.exit(1)

VERSION = "1.0"


def iso_now() -> str:
    """Return current UTC timestamp in ISO format."""
    return datetime.now(timezone.utc).isoformat()


def atomic_write_json(path: Path, data: Dict[str, Any]) -> None:
    """Write JSON data atomically using a temp file."""
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    os.replace(tmp, path)


def load_json(path: Path) -> Dict[str, Any]:
    """Load JSON from file."""
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


class GitHubClient:
    """Simple GitHub API client with retry logic."""

    def __init__(self, token: str, verbose: bool = False):
        self.token = token
        self.verbose = verbose
        self.session = requests.Session()
        self.session.headers.update({
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        })

    def _log(self, msg: str) -> None:
        if self.verbose:
            logging.debug(msg)

    def _backoff_sleep(self, attempt: int) -> None:
        """Exponential backoff for retries."""
        sleep_time = min(2 ** attempt, 60)
        self._log(f"Sleeping {sleep_time}s before retry...")
        time.sleep(sleep_time)

    def _request(self, method: str, url: str, **kwargs) -> requests.Response:
        """Execute HTTP request with retry logic."""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                resp = self.session.request(method, url, **kwargs)
                if resp.status_code == 403 and "rate limit" in resp.text.lower():
                    if attempt < max_retries - 1:
                        self._log(f"Rate limited, attempt {attempt + 1}/{max_retries}")
                        self._backoff_sleep(attempt)
                        continue
                resp.raise_for_status()
                return resp
            except requests.exceptions.RequestException as e:
                if attempt < max_retries - 1:
                    self._log(f"Request failed: {e}, attempt {attempt + 1}/{max_retries}")
                    self._backoff_sleep(attempt)
                    continue
                raise
        raise Exception(f"Failed after {max_retries} attempts")

    def get_issue(self, owner: str, repo: str, number: int) -> Dict[str, Any]:
        """Fetch issue details."""
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}"
        resp = self._request("GET", url)
        return resp.json()

    def update_issue_body(self, owner: str, repo: str, number: int, body: str) -> Dict[str, Any]:
        """Update issue body."""
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}"
        resp = self._request("PATCH", url, json={"body": body})
        return resp.json()

    def get_issue_labels(self, owner: str, repo: str, number: int) -> List[str]:
        """Get current labels on an issue."""
        issue = self.get_issue(owner, repo, number)
        return [label["name"] for label in issue.get("labels", [])]

    def add_label(self, owner: str, repo: str, number: int, label: str) -> None:
        """Add a single label to an issue."""
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}/labels"
        self._request("POST", url, json={"labels": [label]})

    def remove_label(self, owner: str, repo: str, number: int, label: str) -> None:
        """Remove a label from an issue."""
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}/labels/{label}"
        try:
            self._request("DELETE", url)
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 404:
                # Label doesn't exist, ignore
                pass
            else:
                raise


def parse_labels_from_after_md(text: str) -> Tuple[List[str], List[str], List[str]]:
    """
    Parse labels from the '## 🏷️ Labels (Proposed)' section.
    
    Returns:
        Tuple of (required_labels, recommended_labels, blocking_labels)
    """
    # Find the labels section
    m = re.search(r"^##\s*🏷️\s*Labels\s*\(Proposed\)\s*$", text, re.MULTILINE)
    if not m:
        return [], [], []

    # Slice from labels header to next H2 or end
    start = m.start()
    rest = text[start:]
    m2 = re.search(r"^##\s+(?!🏷️\s*Labels\s*\(Proposed\)).+$", rest, re.MULTILINE)
    labels_block = rest if not m2 else rest[:m2.start()]

    def extract_list(section_title: str) -> List[str]:
        """Extract bullet list items from a subsection."""
        s = re.search(rf"^###\s*{re.escape(section_title)}\s*$", labels_block, re.MULTILINE)
        if not s:
            return []
        tail = labels_block[s.end():]
        nxt = re.search(r"^###\s+.+$", tail, re.MULTILINE)
        chunk = tail if not nxt else tail[:nxt.start()]
        labels = []
        for line in chunk.splitlines():
            line = line.strip()
            if line.startswith("- "):
                lab = line[2:].strip()
                if lab and lab.lower() != "none":
                    labels.append(lab)
        return labels

    required = extract_list("Required")
    recommended = extract_list("Recommended")
    blocking = extract_list("Blocking / Risk")

    return required, recommended, blocking


def extract_body_without_labels(text: str) -> str:
    """
    Extract the body content excluding the labels section.
    The body includes everything after the labels section.
    """
    # Find the labels section
    m = re.search(r"^##\s*🏷️\s*Labels\s*\(Proposed\)\s*$", text, re.MULTILINE)
    if not m:
        # No labels section, return whole text
        return text

    # Find the end of the labels section (next H2)
    start = m.start()
    rest = text[start:]
    m2 = re.search(r"^##\s+(?!🏷️\s*Labels\s*\(Proposed\)).+$", rest, re.MULTILINE)
    
    if m2:
        # Return everything from the next H2 onwards
        body_start = start + m2.start()
        return text[body_start:].strip()
    else:
        # No content after labels section
        return ""


def validate_after_md(text: str) -> Tuple[bool, List[str]]:
    """
    Validate that after.md has required sections.
    Sections may be numbered (e.g., "## 1. Story Header") or unnumbered.
    
    Returns:
        Tuple of (is_valid, error_messages)
    """
    errors = []

    # Check for labels section
    if not re.search(r"^##\s*🏷️\s*Labels\s*\(Proposed\)\s*$", text, re.MULTILINE):
        errors.append("Missing '## 🏷️ Labels (Proposed)' section")

    # Check for required label subsections
    required_subsections = [
        r"^###\s*Required\s*$",
        r"^###\s*Recommended\s*$",
        r"^###\s*Blocking\s*/\s*Risk\s*$",
    ]
    for pattern in required_subsections:
        if not re.search(pattern, text, re.MULTILINE):
            errors.append(f"Missing subsection matching pattern: {pattern}")

    # Check for Rewrite Variant
    if not re.search(r"^\*\*Rewrite Variant:\*\*\s+\S+", text, re.MULTILINE):
        errors.append("Missing '**Rewrite Variant:** <variant>' specification")

    # Check for required content sections (may be numbered)
    # Pattern: # [number]. Section Name or ## [number]. Section Name or # Section Name or ## Section Name
    required_sections = [
        "Story Header",
        "Story Intent",
        "Actors & Stakeholders",
        "Functional Behavior",
        "Business Rules",  # May be "Business Rules (Translated to UI Behavior)"
        "Data Requirements",
        "Acceptance Criteria",
        "Audit & Observability",
    ]
    
    optional_sections = [
        "Preconditions",  # May be "Preconditions & Dependencies"
        "Open Questions",  # Optional but encouraged
    ]
    
    for section in required_sections:
        # Match: # 1. Section Name or ## 1. Section Name (with optional extra text in parens)
        # Support both H1 (#) and H2 (##) headers
        pattern = rf"^#{{1,2}}\s+(?:\d+\.\s+)?{re.escape(section)}(?:\s*\([^)]*\))?\s*$"
        if not re.search(pattern, text, re.MULTILINE):
            # Try alternative patterns for sections with common variations
            if section == "Business Rules":
                alt_pattern = r"^#{{1,2}}\s+(?:\d+\.\s+)?Business Rules\s*\([^)]*\)\s*$"
                if re.search(alt_pattern, text, re.MULTILINE):
                    continue
            errors.append(f"Missing required section: '{section}' (may be numbered like '# 2. {section}' or '## 2. {section}')")

    return len(errors) == 0, errors


def scan_story_folders(story_root: Path) -> List[int]:
    """
    Scan story_root for folders containing after.md files.
    
    Returns:
        List of issue numbers (extracted from folder names)
    """
    if not story_root.exists():
        return []

    issues = []
    for folder in story_root.iterdir():
        if not folder.is_dir():
            continue
        # Folder name should be numeric (issue number)
        if not folder.name.isdigit():
            continue
        after_md = folder / "after.md"
        if after_md.exists():
            issues.append(int(folder.name))

    return sorted(issues)


def load_or_create_plan(story_root: Path, owner: str, repo: str) -> Dict[str, Any]:
    """
    Load existing publish plan or create a new one.
    
    Plan format:
    {
      "version": "1.0",
      "created_at": "...",
      "updated_at": "...",
      "owner": "...",
      "repo": "...",
      "state": {
        "pending": [65, 66, ...],
        "processing": [],
        "completed": {"65": {"processed_at": "...", "file": "..."}},
        "failed": {"66": {"error": "...", "last_attempt": "..."}}
      }
    }
    """
    plan_path = story_root / ".publish_plan.json"

    if plan_path.exists():
        plan = load_json(plan_path)
        # Update metadata
        plan["updated_at"] = iso_now()
        return plan

    # Create new plan
    issues = scan_story_folders(story_root)
    plan = {
        "version": VERSION,
        "created_at": iso_now(),
        "updated_at": iso_now(),
        "owner": owner,
        "repo": repo,
        "state": {
            "pending": issues,
            "processing": [],
            "completed": {},
            "failed": {},
        }
    }
    return plan


def save_plan(story_root: Path, plan: Dict[str, Any]) -> None:
    """Save publish plan atomically."""
    plan_path = story_root / ".publish_plan.json"
    plan["updated_at"] = iso_now()
    atomic_write_json(plan_path, plan)


def apply_labels(
    client: GitHubClient,
    owner: str,
    repo: str,
    issue_number: int,
    required: List[str],
    recommended: List[str],
    blocking: List[str],
    include_recommended: bool = False,
    dry_run: bool = False
) -> None:
    """
    Apply labels to an issue.
    
    - Always applies required and blocking labels
    - Optionally applies recommended labels
    - Removes conflicting status:* and domain:* labels as needed
    """
    labels_to_apply = required + blocking
    if include_recommended:
        labels_to_apply.extend(recommended)

    # Deduplicate
    labels_to_apply = list(dict.fromkeys(labels_to_apply))

    if dry_run:
        logging.info(f"[DRY RUN] Would apply labels to #{issue_number}: {labels_to_apply}")
        return

    logging.info(f"Applying {len(labels_to_apply)} labels to #{issue_number}")

    # Get current labels
    current_labels = client.get_issue_labels(owner, repo, issue_number)

    # Remove conflicting status:* labels if we're applying a new one
    new_status_labels = [l for l in labels_to_apply if l.startswith("status:")]
    if new_status_labels:
        for label in current_labels:
            if label.startswith("status:") and label not in new_status_labels:
                logging.info(f"  Removing conflicting status label: {label}")
                client.remove_label(owner, repo, issue_number, label)

    # Remove conflicting domain:* labels if we're applying a new one
    # (unless blocked:domain-conflict is present)
    new_domain_labels = [l for l in labels_to_apply if l.startswith("domain:")]
    has_domain_conflict = "blocked:domain-conflict" in labels_to_apply
    if new_domain_labels and not has_domain_conflict:
        for label in current_labels:
            if label.startswith("domain:") and label not in new_domain_labels:
                logging.info(f"  Removing conflicting domain label: {label}")
                client.remove_label(owner, repo, issue_number, label)

    # Apply new labels
    for label in labels_to_apply:
        logging.info(f"  + {label}")
        client.add_label(owner, repo, issue_number, label)


def publish_single_issue(
    client: GitHubClient,
    owner: str,
    repo: str,
    issue_number: int,
    story_root: Path,
    include_recommended: bool = False,
    fail_on_blocking: bool = False,
    dry_run: bool = False
) -> Tuple[bool, Optional[str]]:
    """
    Publish a single issue's rewritten story.
    
    Returns:
        Tuple of (success, error_message)
    """
    after_md_path = story_root / str(issue_number) / "after.md"

    if not after_md_path.exists():
        return False, f"after.md not found: {after_md_path}"

    # Read after.md
    try:
        text = after_md_path.read_text(encoding="utf-8")
    except Exception as e:
        return False, f"Failed to read after.md: {e}"

    # Validate
    is_valid, errors = validate_after_md(text)
    if not is_valid:
        error_msg = "Validation failed:\n  " + "\n  ".join(errors)
        return False, error_msg

    # Parse labels
    required, recommended, blocking = parse_labels_from_after_md(text)

    # Check for blocking labels
    if fail_on_blocking and blocking:
        error_msg = f"Blocking/Risk labels present: {blocking}"
        return False, error_msg

    # Extract body (without labels section)
    body = extract_body_without_labels(text)

    if not body:
        return False, "No body content found after labels section"

    # Update issue body
    if dry_run:
        logging.info(f"[DRY RUN] Would update #{issue_number} body ({len(body)} chars)")
    else:
        logging.info(f"Updating #{issue_number} body ({len(body)} chars)")
        try:
            client.update_issue_body(owner, repo, issue_number, body)
        except Exception as e:
            return False, f"Failed to update issue body: {e}"

    # Apply labels
    try:
        apply_labels(client, owner, repo, issue_number, required, recommended, blocking, include_recommended, dry_run)
    except Exception as e:
        return False, f"Failed to apply labels: {e}"

    return True, None


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Publish rewritten stories to GitHub issues",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    parser.add_argument("--story-root", required=True, type=Path,
                        help="Root directory containing story folders (e.g., ./scripts/story-work/frontend)")
    parser.add_argument("--owner", required=True,
                        help="GitHub repository owner")
    parser.add_argument("--repo", required=True,
                        help="GitHub repository name")
    parser.add_argument("--issue", type=int,
                        help="Publish a single issue (for testing)")
    parser.add_argument("--include-recommended", action="store_true",
                        help="Include recommended labels (default: only required + blocking)")
    parser.add_argument("--fail-on-blocking", action="store_true",
                        help="Fail if blocking/risk labels are present")
    parser.add_argument("--dry-run", action="store_true",
                        help="Preview changes without applying them")
    parser.add_argument("--verbose", action="store_true",
                        help="Enable verbose logging")
    parser.add_argument("--force-refresh", action="store_true",
                        help="Refresh the plan by scanning for new issues")

    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(levelname)s: %(message)s"
    )

    # Check for GITHUB_TOKEN
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        logging.error("GITHUB_TOKEN environment variable required")
        return 3

    # Validate story root
    if not args.story_root.exists():
        logging.error(f"Story root not found: {args.story_root}")
        return 2

    # Initialize GitHub client
    client = GitHubClient(token, verbose=args.verbose)

    # Load or create plan (needed for both single and batch modes)
    logging.info("Loading publish plan...")
    plan = load_or_create_plan(args.story_root, args.owner, args.repo)

    # Single issue mode
    if args.issue:
        logging.info(f"Publishing single issue: #{args.issue}")
        issue_str = str(args.issue)
        
        success, error = publish_single_issue(
            client, args.owner, args.repo, args.issue, args.story_root,
            include_recommended=args.include_recommended,
            fail_on_blocking=args.fail_on_blocking,
            dry_run=args.dry_run
        )
        
        # Update plan with result
        if not args.dry_run:
            if success:
                plan["state"]["completed"][issue_str] = {
                    "processed_at": iso_now(),
                    "file": str(args.story_root / str(args.issue) / "after.md")
                }
                # Remove from pending/failed if present
                if args.issue in plan["state"]["pending"]:
                    plan["state"]["pending"].remove(args.issue)
                if issue_str in plan["state"]["failed"]:
                    del plan["state"]["failed"][issue_str]
            else:
                plan["state"]["failed"][issue_str] = {
                    "error": error,
                    "last_attempt": iso_now()
                }
                # Remove from pending if present
                if args.issue in plan["state"]["pending"]:
                    plan["state"]["pending"].remove(args.issue)
            
            save_plan(args.story_root, plan)
            logging.info(f"Updated plan: {args.story_root / '.publish_plan.json'}")
        
        if success:
            logging.info(f"✅ Successfully published #{args.issue}")
            return 0
        else:
            logging.error(f"❌ Failed to publish #{args.issue}: {error}")
            return 1

    # Batch mode
    if args.force_refresh:
        logging.info("Force refresh: scanning for new issues...")
        issues = scan_story_folders(args.story_root)
        # Add new issues to pending (if not already completed/failed)
        for issue in issues:
            issue_str = str(issue)
            if issue_str not in plan["state"]["completed"] and issue_str not in plan["state"]["failed"]:
                if issue not in plan["state"]["pending"]:
                    plan["state"]["pending"].append(issue)
        plan["state"]["pending"].sort()

    pending = plan["state"]["pending"]
    completed = plan["state"]["completed"]
    failed = plan["state"]["failed"]

    if not pending:
        logging.info("No pending issues to publish")
        return 0

    logging.info(f"Found {len(pending)} pending issues to publish")

    # Process each pending issue
    for issue_number in pending[:]:  # Copy to allow modification during iteration
        issue_str = str(issue_number)
        logging.info(f"\n{'='*60}")
        logging.info(f"Processing Issue #{issue_number}")
        logging.info(f"{'='*60}")

        # Move to processing
        plan["state"]["processing"].append(issue_number)
        plan["state"]["pending"].remove(issue_number)
        save_plan(args.story_root, plan)

        # Publish
        success, error = publish_single_issue(
            client, args.owner, args.repo, issue_number, args.story_root,
            include_recommended=args.include_recommended,
            fail_on_blocking=args.fail_on_blocking,
            dry_run=args.dry_run
        )

        # Update plan
        plan["state"]["processing"].remove(issue_number)
        if success:
            logging.info(f"✅ Successfully published #{issue_number}")
            completed[issue_str] = {
                "processed_at": iso_now(),
                "file": str(args.story_root / str(issue_number) / "after.md")
            }
        else:
            logging.error(f"❌ Failed to publish #{issue_number}: {error}")
            failed[issue_str] = {
                "error": error,
                "last_attempt": iso_now()
            }

        save_plan(args.story_root, plan)

    # Summary
    logging.info(f"\n{'='*60}")
    logging.info("SUMMARY")
    logging.info(f"{'='*60}")
    logging.info(f"Completed: {len(completed)}")
    logging.info(f"Failed: {len(failed)}")
    logging.info(f"Remaining: {len(plan['state']['pending'])}")

    if failed:
        logging.info("\nFailed issues:")
        for issue_str, info in failed.items():
            logging.info(f"  #{issue_str}: {info['error']}")

    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
