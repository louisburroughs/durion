#!/usr/bin/env python3
"""
Create stories/frontend directories under each capability folder.

Usage:
  python scripts/create_capability_story_dirs.py
  python scripts/create_capability_story_dirs.py --dry-run
  python scripts/create_capability_story_dirs.py --capabilities-root docs/capabilities
"""

from __future__ import annotations

import argparse
from pathlib import Path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--capabilities-root", default="docs/capabilities")
    parser.add_argument("--dry-run", action="store_true")
    return parser


def iter_capability_dirs(capabilities_root: Path) -> list[Path]:
    return sorted(
        path for path in capabilities_root.iterdir()
        if path.is_dir() and path.name.startswith("CAP-")
    )


def create_story_dirs(capability_dirs: list[Path], dry_run: bool) -> tuple[int, int]:
    created_count = 0
    existing_count = 0

    for capability_dir in capability_dirs:
        target_dir = capability_dir / "stories" / "frontend"
        if target_dir.exists():
            existing_count += 1
            continue

        if not dry_run:
            target_dir.mkdir(parents=True, exist_ok=True)
        created_count += 1

    return created_count, existing_count


def main() -> int:
    args = build_parser().parse_args()
    capabilities_root = Path(args.capabilities_root)

    if not capabilities_root.exists() or not capabilities_root.is_dir():
        raise SystemExit(f"Capabilities root not found: {capabilities_root}")

    capability_dirs = iter_capability_dirs(capabilities_root)
    created_count, existing_count = create_story_dirs(capability_dirs, args.dry_run)

    print(f"capability_dirs={len(capability_dirs)} created={created_count} existing={existing_count} dry_run={args.dry_run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())