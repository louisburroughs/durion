#!/usr/bin/env python3
"""
extract_stories.py

Usage examples:
  python extract_stories.py --owner louisburroughs --out-dir stories
  python extract_stories.py --owner louisburroughs --readiness-labels "status:ready-for-dev,status:needs-review" --skip-label status:draft
  python extract_stories.py --owner louisburroughs --dry-run

Dependencies: Python 3.10+, requests

This script extracts open GitHub issues from two repos and writes them
to a filesystem layout suitable for downstream processing. It is resumable
and stores progress in an atomic `extraction_plan.json` file under `--out-dir`.

Plan file format (keys):
  version, created_at, updated_at, owner, frontend_repo, backend_repo,
  readiness_labels (list), skip_label (string), state {
    pending_frontend: [ints], pending_backend: [ints], processing: [],
    completed: {"<repo>/<num>": {repo, file, processed_at}},
    failed: {"<repo>/<num>": {repo, error, last_attempt}}
  }

The script honors `GITHUB_TOKEN` environment variable for API access only.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import random
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests

VERSION = "1.0"


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def atomic_write_json(path: Path, data: Dict[str, Any]) -> None:
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    os.replace(tmp, path)


def load_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


class GitHubFetcher:
    def __init__(self, token: str, verbose: bool = False):
        self.token = token
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "extract-stories-script",
        })
        self.verbose = verbose

    def _backoff_sleep(self, attempt: int) -> None:
        base = min(60, 2 ** attempt)
        jitter = random.uniform(0, 1)
        time.sleep(base + jitter)

    def _get(self, url: str, params: Optional[Dict[str, Any]] = None) -> requests.Response:
        tries = 0
        while True:
            tries += 1
            resp = self.session.get(url, params=params, timeout=30)
            if resp.status_code in (200, 201):
                return resp
            # rate limit handling
            if resp.status_code in (429, 403):
                remaining = resp.headers.get("X-RateLimit-Remaining")
                if resp.status_code == 429 or (remaining is not None and remaining == "0"):
                    wait = int(resp.headers.get("Retry-After") or 60)
                    logging.warning("Rate limited. Sleeping %s seconds", wait)
                    time.sleep(wait)
                    continue
            if tries >= 5:
                resp.raise_for_status()
            logging.info("Request failed (status=%s). Backing off and retrying...", resp.status_code)
            self._backoff_sleep(tries)

    def fetch_all_open_issues(self, owner: str, repo: str, per_page: int = 100) -> List[Dict[str, Any]]:
        issues: List[Dict[str, Any]] = []
        page = 1
        while True:
            url = f"https://api.github.com/repos/{owner}/{repo}/issues"
            params = {"state": "open", "per_page": per_page, "page": page}
            resp = self._get(url, params=params)
            raw_items = resp.json()
            if not isinstance(raw_items, list):
                raise RuntimeError(f"Unexpected response fetching issues: {raw_items}")
            # filter out PRs (GitHub returns PRs in this endpoint; they have a pull_request field)
            page_items = [i for i in raw_items if "pull_request" not in i]
            issues.extend(page_items)

            # IMPORTANT: pagination must be based on the raw page size, not the filtered size.
            # Otherwise a page containing many PRs could look "short" after filtering and we
            # would incorrectly stop early.
            if len(raw_items) < per_page:
                break
            page += 1
        return issues

    def fetch_issue(self, owner: str, repo: str, number: int) -> Dict[str, Any]:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{number}"
        resp = self._get(url)
        return resp.json()


def ensure_lock(out_dir: Path, force: bool = False) -> None:
    lock = out_dir / ".lock"
    if lock.exists():
        try:
            mtime = lock.stat().st_mtime
        except Exception:
            mtime = 0
        age = time.time() - mtime
        if age < 3600 and not force:
            raise SystemExit(f"Lock file {lock} exists and is recent (age={int(age)}s). Use --force to override.")
    out_dir.mkdir(parents=True, exist_ok=True)
    lock.write_text(f"pid:{os.getpid()}\n{iso_now()}\n")


def release_lock(out_dir: Path) -> None:
    lock = out_dir / ".lock"
    try:
        lock.unlink()
    except Exception:
        pass


def build_plan(owner: str, frontend_repo: str, backend_repo: str, skip_label: str, fetcher: GitHubFetcher, per_page: int) -> Dict[str, Any]:
    now = iso_now()
    logging.info("Fetching frontend issues for %s/%s", owner, frontend_repo)
    frontend_issues = fetcher.fetch_all_open_issues(owner, frontend_repo, per_page=per_page)
    logging.info("Found %d frontend open issues", len(frontend_issues))
    logging.info("Fetching backend issues for %s/%s", owner, backend_repo)
    backend_issues = fetcher.fetch_all_open_issues(owner, backend_repo, per_page=per_page)
    logging.info("Found %d backend open issues", len(backend_issues))

    pending_frontend = [i["number"] for i in frontend_issues]
    pending_backend = []
    for i in backend_issues:
        labels = [l["name"] for l in i.get("labels", [])]
        if skip_label and skip_label in labels:
            continue
        pending_backend.append(i["number"])

    plan = {
        "version": VERSION,
        "created_at": now,
        "updated_at": now,
        "owner": owner,
        "frontend_repo": frontend_repo,
        "backend_repo": backend_repo,
        "readiness_labels": [],
        "skip_label": skip_label,
        "state": {
            "pending_frontend": pending_frontend,
            "pending_backend": pending_backend,
            "processing": [],
            "completed": {},
            "failed": {},
        },
    }
    return plan


def write_issue_file(path: Path, issue: Dict[str, Any]) -> None:
    title = issue.get("title", "")
    url = issue.get("html_url", "")
    labels = ", ".join([l.get("name", "") for l in issue.get("labels", [])])
    body = issue.get("body") or ""
    content = f"Title: {title}\nURL: {url}\nLabels: {labels}\n\n{body}"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        f.write(content)


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--owner", default="louisburroughs")
    p.add_argument("--frontend-repo", default="durion-moqui-frontend")
    p.add_argument("--backend-repo", default="durion-positivity-backend")
    p.add_argument("--out-dir", default="stories")
    p.add_argument("--readiness-labels", default="status:ready-for-dev,status:needs-review")
    p.add_argument("--skip-label", default="status:draft")
    p.add_argument("--per-page", type=int, default=100)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--verbose", action="store_true")
    p.add_argument("--force-refresh-plan", action="store_true")
    p.add_argument("--force", action="store_true")
    args = p.parse_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format="%(levelname)s: %(message)s")

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        logging.error("GITHUB_TOKEN not set in environment")
        return 2

    dry_run_limit = 5

    out_dir = Path(args.out_dir)
    plan_path = out_dir / "extraction_plan.json"

    try:
        ensure_lock(out_dir, force=args.force)
    except SystemExit as e:
        logging.error(e)
        return 2

    fetcher = GitHubFetcher(token, verbose=args.verbose)

    plan: Dict[str, Any]
    if plan_path.exists() and not args.force_refresh_plan:
        try:
            plan = load_json(plan_path)
        except Exception as e:
            release_lock(out_dir)
            logging.error("Failed to read plan file: %s", e)
            logging.error("Use --force-refresh-plan to recreate the plan.")
            return 3
    else:
        plan = build_plan(args.owner, args.frontend_repo, args.backend_repo, args.skip_label, fetcher, args.per_page)
        plan["readiness_labels"] = [s.strip() for s in args.readiness_labels.split(",") if s.strip()]
        if not args.dry_run:
            atomic_write_json(plan_path, plan)
        logging.info(
            "Created new plan with %d frontend and %d backend pending issues",
            len(plan["state"]["pending_frontend"]),
            len(plan["state"]["pending_backend"]),
        )

    # Ensure plan has expected structure
    state = plan.setdefault("state", {})
    pending_frontend: List[int] = state.setdefault("pending_frontend", [])
    pending_backend: List[int] = state.setdefault("pending_backend", [])
    processing: List[str] = state.setdefault("processing", [])
    completed: Dict[str, Any] = state.setdefault("completed", {})
    failed: Dict[str, Any] = state.setdefault("failed", {})

    frontend_processed = 0
    backend_processed = 0
    backend_skipped_not_updated = 0
    failed_count = 0

    def save_plan():
        if args.dry_run:
            return
        plan["updated_at"] = iso_now()
        atomic_write_json(plan_path, plan)

    # Process frontend pending list
    while pending_frontend:
        if args.dry_run and frontend_processed >= dry_run_limit:
            break

        num = pending_frontend.pop(0)
        key = str(num)
        if key in completed and completed.get(key, {}).get("repo") == "frontend":
            logging.debug("Skipping frontend %s already completed", num)
            continue

        if not args.dry_run:
            processing.append(num)
            save_plan()

        try:
            issue = fetcher.fetch_issue(args.owner, args.frontend_repo, num)
            dest = out_dir / "frontend" / str(num) / "before.md"
            if args.dry_run:
                logging.info("[dry-run] would write %s", dest)
            else:
                write_issue_file(dest, issue)
                completed[key] = {"repo": "frontend", "file": str(dest.relative_to(out_dir)), "processed_at": iso_now()}
            frontend_processed += 1
        except Exception as e:
            logging.exception("Failed processing frontend issue %s", num)
            if not args.dry_run:
                failed[key] = {"repo": "frontend", "error": str(e), "last_attempt": iso_now()}
            failed_count += 1
        finally:
            if not args.dry_run:
                try:
                    processing.remove(num)
                except ValueError:
                    pass
                save_plan()

    # Process backend pending list (single pass; keep "not ready" issues pending for future runs)
    new_pending_backend: List[int] = []
    while pending_backend:
        if args.dry_run and backend_processed >= dry_run_limit:
            # keep remaining as pending
            new_pending_backend.extend(pending_backend)
            break

        num = pending_backend.pop(0)
        key = str(num)
        if key in completed and completed.get(key, {}).get("repo") == "backend":
            logging.debug("Skipping backend %s already completed", num)
            continue

        if not args.dry_run:
            processing.append(num)
            save_plan()

        try:
            issue = fetcher.fetch_issue(args.owner, args.backend_repo, num)
            labels = [l.get("name", "") for l in issue.get("labels", [])]
            ready = any(lbl in plan.get("readiness_labels", []) for lbl in labels)
            if not ready:
                backend_skipped_not_updated += 1
                new_pending_backend.append(num)
                logging.info("Backend issue %s not in readiness labels, leaving pending", num)
            else:
                dest = out_dir / "backend" / str(num) / "backend.md"
                if args.dry_run:
                    logging.info("[dry-run] would write %s", dest)
                else:
                    write_issue_file(dest, issue)
                    completed[key] = {"repo": "backend", "file": str(dest.relative_to(out_dir)), "processed_at": iso_now()}
                backend_processed += 1
        except Exception as e:
            logging.exception("Failed processing backend issue %s", num)
            if not args.dry_run:
                failed[key] = {"repo": "backend", "error": str(e), "last_attempt": iso_now()}
            failed_count += 1
            new_pending_backend.append(num)
        finally:
            if not args.dry_run:
                try:
                    processing.remove(num)
                except ValueError:
                    pass
                save_plan()

    if not args.dry_run:
        state["pending_backend"] = new_pending_backend

    release_lock(out_dir)

    summary = {
        "frontend_processed": frontend_processed,
        "backend_processed": backend_processed,
        "backend_skipped_not_updated": backend_skipped_not_updated,
        "failed": failed_count,
    }
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
