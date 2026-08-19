#!/usr/bin/env python3
"""Regenerate the shared Durion knowledge catalog and module navigation files.

This is the canonical sync entry point for the doc-structure work. It refreshes:
- durion/knowledge-catalog/
- durion/domains/*/index.md
- durion-positivity-backend/pos-*/index.md

Usage:
  python3 scripts/sync-shared-docs.py
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def backend_root() -> Path:
    return repo_root().parent / 'durion-positivity-backend'


def run_script(script_name: str) -> None:
    root = repo_root()
    script = root / 'scripts' / script_name
    if not script.exists():
        raise FileNotFoundError(f'Missing script: {script}')
    subprocess.run([sys.executable, str(script)], check=True, cwd=root)


def main() -> int:
    if not backend_root().exists():
        raise SystemExit(f'Backend repo not found: {backend_root()}')

    run_script('generate-knowledge-catalog.py')
    run_script('generate-module-indexes.py')
    print('Shared doc sync complete.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
