#!/usr/bin/env python3
"""
Standardize CAPABILITY_MANIFEST.yaml files by adding local frontend story
references under ./stories/frontend.

Usage:
    python3 scripts/standardize_manifest_story_paths.py
    python3 scripts/standardize_manifest_story_paths.py --dry-run
"""

import argparse
from pathlib import Path
from typing import Any, Dict, Tuple

import yaml

ARCHIVED_FRONTEND_REPO = "louisburroughs/durion-moqui-frontend"
ACTIVE_FRONTEND_REPO = "louisburroughs/durion-positivity-frontend"


def capability_prefix(capability_id: str) -> str:
    if capability_id.startswith("CAP:"):
        return capability_id.replace("CAP:", "CAP_")
    return "NOCAP"


def local_story_filename(capability_id: str, issue_number: Any) -> str:
    issue = str(issue_number) if issue_number is not None else "unknown"
    return f"{capability_prefix(capability_id)}.{issue}.frontend.md"


def ensure_frontend_block(story: Dict[str, Any]) -> Dict[str, Any]:
    children = story.get("children")
    if not isinstance(children, dict):
        children = {}
        story["children"] = children

    frontend = children.get("frontend")
    if not isinstance(frontend, dict):
        frontend = {}
        children["frontend"] = frontend

    return frontend


def normalize_frontend_repo(frontend: Dict[str, Any]) -> bool:
    current_repo = frontend.get("repo")
    if not current_repo or current_repo == ARCHIVED_FRONTEND_REPO:
        frontend["repo"] = ACTIVE_FRONTEND_REPO
        return True
    return False


def resolve_frontend_issue(story: Dict[str, Any], frontend: Dict[str, Any]) -> Any:
    issue = frontend.get("issue")
    if issue is not None:
        return issue
    parent_story = story.get("parent_story") or {}
    return parent_story.get("issue")


def update_story_frontend_reference(story: Dict[str, Any], capability_id: str) -> bool:
    frontend = ensure_frontend_block(story)
    repo_changed = normalize_frontend_repo(frontend)

    frontend_issue = resolve_frontend_issue(story, frontend)
    issue_changed = False
    if frontend_issue is not None and "issue" not in frontend:
        frontend["issue"] = frontend_issue
        issue_changed = True

    target_path = f"./stories/frontend/{local_story_filename(capability_id, frontend_issue)}"
    current_path = frontend.get("story_markdown_path")
    if current_path != target_path:
        frontend["story_markdown_path"] = target_path
        return True

    return repo_changed or issue_changed


def update_manifest(manifest_path: Path, dry_run: bool) -> Tuple[bool, int]:
    """
    Update a single manifest file.
    Returns True if the file was modified, False otherwise.
    """
    with manifest_path.open("r") as f:
        data = yaml.safe_load(f)

    modified = False
    updated_stories = 0
    if "stories" not in data or not isinstance(data["stories"], list):
        return False, 0

    capability_id = ((data.get("meta") or {}).get("capability_id") or "NOCAP")

    for story in data["stories"]:
        story_changed = update_story_frontend_reference(story, capability_id)
        if story_changed:
            modified = True
            updated_stories += 1

    if modified and not dry_run:
        with manifest_path.open("w", encoding="utf-8") as f:
            yaml.dump(data, f, default_flow_style=False, sort_keys=False)

    return modified, updated_stories


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    capabilities_root = Path("docs/capabilities")
    manifest_paths = sorted(capabilities_root.glob("**/CAPABILITY_MANIFEST.yaml"))

    updated_files = 0
    updated_story_refs = 0
    for path in manifest_paths:
        changed, updated_in_file = update_manifest(path, args.dry_run)
        if changed:
            print(f"Updated: {path}")
            updated_files += 1
            updated_story_refs += updated_in_file

    print(f"\nProcessed {len(manifest_paths)} manifests.")
    print(f"Updated {updated_files} files.")
    print(f"Updated {updated_story_refs} story frontend references.")


if __name__ == "__main__":
    main()
