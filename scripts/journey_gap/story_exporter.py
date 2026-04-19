"""Export rewritten story issues to flat markdown files."""

from __future__ import annotations

import json
import logging
import sqlite3
from pathlib import Path
import re
import shutil

log = logging.getLogger(__name__)

STORY_LABEL = "type:story"
CAPABILITY_LABEL_RE = re.compile(r"^CAP:\s*(\d+)$", re.IGNORECASE)


def _parse_labels(raw_labels: str | None) -> list[str]:
    if not raw_labels:
        return []

    try:
        parsed = json.loads(raw_labels)
    except json.JSONDecodeError:
        return [label.strip().strip('"') for label in raw_labels.split(",") if label.strip()]

    if not isinstance(parsed, list):
        return []

    return [str(label).strip() for label in parsed if str(label).strip()]


def _extract_capability(labels: list[str]) -> str | None:
    for label in labels:
        match = CAPABILITY_LABEL_RE.match(label)
        if match:
            return f"CAP-{match.group(1)}"
    return None


def _slugify(value: str, *, fallback: str) -> str:
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", value).strip("-").lower()
    return slug or fallback


def _repo_slug(repo: str) -> str:
    return _slugify(repo.split("/", 1)[-1], fallback="repo")


def _clear_generated_output(output_dir: Path) -> None:
    for child in output_dir.iterdir():
        if child.is_dir():
            shutil.rmtree(child)
        elif child.suffix.lower() == ".md":
            child.unlink()


def export_stories(con: sqlite3.Connection, output_dir: Path) -> int:
    """Export one cleaned markdown file per open story issue."""
    output_dir.mkdir(parents=True, exist_ok=True)

    _clear_generated_output(output_dir)

    cur = con.cursor()
    cur.execute('''
        SELECT
            i.repo,
            i.number,
            i.title,
            coalesce(c.consolidated_body, i.body),
            i.labels,
            i.url
        FROM issues i
        LEFT JOIN consolidated_issues c ON i.repo = c.repo AND i.number = c.number
        WHERE UPPER(i.state) = 'OPEN'
          AND LOWER(i.labels) LIKE '%type:story%'
        ORDER BY i.repo, i.number
    ''')

    exported = 0
    for repo, number, title, body, raw_labels, url in cur.fetchall():
        if not body:
            continue

        labels = _parse_labels(raw_labels)
        if STORY_LABEL not in {label.lower() for label in labels}:
            continue

        capability = _extract_capability(labels)
        repo_slug = _repo_slug(repo)
        title_slug = _slugify(title, fallback=f"story-{number}")
        prefix = capability or "NO-CAP"
        file_name = f"{prefix}-{repo_slug}-{number}-{title_slug}.md"
        file_path = output_dir / file_name

        metadata_lines = [
            f"# {title}",
            "",
            f"- Issue: {repo}#{number}",
            f"- Capability: {capability or 'Unassigned'}",
        ]
        if url:
            metadata_lines.append(f"- Source: {url}")

        content = "\n".join(metadata_lines) + f"\n\n---\n\n{body.strip()}\n"
        file_path.write_text(content, encoding="utf-8")
        exported += 1

    return exported
