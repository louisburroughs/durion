#!/usr/bin/env python3
"""
bulk_create_issues_from_manifest.py

Creates GitHub issues from a manifest JSON that lists markdown file paths.

Each markdown file must begin with YAML frontmatter:

---
repo: owner/repo
title: "Issue title"
labels:
  - type:story
  - domain:positivity
  - status:draft
---
<issue body markdown...>

The script:
- Loads manifest JSON (ordered list)
- Loads each markdown
- Validates manifest repo/title match frontmatter (optional)
- Strips frontmatter and posts issue to GitHub

Usage:
  pip install requests pyyaml
  export GITHUB_TOKEN=...
  python bulk_create_issues_from_manifest.py nlt-i/manifest.json
  python bulk_create_issues_from_manifest.py nlt-i/manifest.json --dry-run
  python bulk_create_issues_from_manifest.py nlt-i/manifest.json --start 5 --limit 3
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Tuple

import requests
import yaml

FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.DOTALL)


@dataclass(frozen=True)
class IssueDoc:
    repo: str
    title: str
    labels: List[str]
    body: str
    source_path: Path


def parse_issue_md(path: Path) -> IssueDoc:
    text = path.read_text(encoding="utf-8")
    m = FRONTMATTER_RE.match(text)
    if not m:
        raise ValueError(f"{path}: missing YAML frontmatter block")

    fm_raw = m.group(1)
    fm: Dict[str, Any] = yaml.safe_load(fm_raw) or {}

    repo = fm.get("repo")
    title = fm.get("title")
    labels = fm.get("labels") or []

    if not isinstance(repo, str) or "/" not in repo:
        raise ValueError(f"{path}: frontmatter 'repo' must be like 'owner/repo'")
    if not isinstance(title, str) or not title.strip():
        raise ValueError(f"{path}: frontmatter 'title' is required")
    if not isinstance(labels, list) or any(not isinstance(x, str) for x in labels):
        raise ValueError(f"{path}: frontmatter 'labels' must be a list of strings")

    body = text[m.end():].lstrip("\n")
    return IssueDoc(
        repo=repo.strip(),
        title=title.strip(),
        labels=[x.strip() for x in labels],
        body=body,
        source_path=path,
    )


def create_issue(doc: IssueDoc, token: str, dry_run: bool = False) -> Tuple[str, str]:
    owner, repo = doc.repo.split("/", 1)
    url = f"https://api.github.com/repos/{owner}/{repo}/issues"
    payload = {"title": doc.title, "body": doc.body, "labels": doc.labels}

    if dry_run:
        return ("DRY_RUN", url)

    r = requests.post(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
        json=payload,
        timeout=30,
    )
    if r.status_code >= 300:
        raise RuntimeError(f"GitHub error {r.status_code} for {doc.source_path}:\n{r.text}")

    return (r.json().get("html_url", ""), url)


def load_manifest(path: Path) -> Dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or "items" not in data:
        raise ValueError("manifest must be a JSON object containing 'items'")
    if not isinstance(data["items"], list):
        raise ValueError("manifest 'items' must be a list")
    return data


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest", help="Path to manifest.json")
    ap.add_argument("--dry-run", action="store_true", help="Validate and print actions without creating issues")
    ap.add_argument("--start", type=int, default=0, help="Start index into manifest items")
    ap.add_argument("--limit", type=int, default=None, help="Max number of items to process")
    ap.add_argument(
        "--no-validate-manifest",
        action="store_true",
        help="Do not validate manifest repo/title against markdown frontmatter",
    )
    args = ap.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token and not args.dry_run:
        raise SystemExit("GITHUB_TOKEN is required (or use --dry-run)")

    manifest_path = Path(args.manifest)
    manifest = load_manifest(manifest_path)
    items: List[Dict[str, Any]] = manifest["items"]

    start = max(args.start, 0)
    end = len(items) if args.limit is None else min(len(items), start + max(args.limit, 0))
    selected = items[start:end]

    base_dir = manifest_path.parent

    for i, it in enumerate(selected, start=start):
        if not isinstance(it, dict) or "path" not in it:
            raise ValueError(f"manifest item {i} must be an object with a 'path' field")

        md_path = (base_dir / it["path"]).resolve()
        if not md_path.exists():
            raise FileNotFoundError(f"manifest item {i} path not found: {md_path}")

        doc = parse_issue_md(md_path)

        if not args.no_validate_manifest:
            m_repo = it.get("repo")
            m_title = it.get("title")
            if isinstance(m_repo, str) and m_repo.strip() and m_repo.strip() != doc.repo:
                raise ValueError(f"{md_path}: manifest repo '{m_repo}' != frontmatter repo '{doc.repo}'")
            if isinstance(m_title, str) and m_title.strip() and m_title.strip() != doc.title:
                raise ValueError(f"{md_path}: manifest title mismatch")

        issue_url, api_url = create_issue(doc, token, dry_run=args.dry_run)
        print(f"[{i}] {md_path} -> {doc.repo} -> {issue_url} (POST {api_url})")


if __name__ == "__main__":
    main()