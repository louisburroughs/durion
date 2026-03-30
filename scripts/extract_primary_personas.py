#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path


INLINE_PRIMARY_PERSONA_RE = re.compile(
    r"^\s*(?:[-*]\s*)?(?:\*\*)?Primary Persona(?:\*\*)?\s*:\s*(.+?)\s*$",
    re.IGNORECASE,
)
HEADER_PRIMARY_PERSONA_RE = re.compile(
    r"^\s{0,3}(?:#{1,6})\s+Primary Persona\s*$",
    re.IGNORECASE,
)
MARKDOWN_CLEANUP_RE = re.compile(r"[*_`]+")
PARENTHETICAL_RE = re.compile(r"\s*\([^)]*\)")
SECONDARY_CLAUSE_RE = re.compile(r"(?i)(?:^|[.;])\s*secondary\s*:\s*.*$")
NARRATIVE_SUFFIX_RE = re.compile(
    r"""(?ix)
    (?:
        ,\s*with\b.*|
        \s+with\b.*|
        \s+who\b.*|
        \s+viewing\b.*|
        \s+operating\b.*|
        \s+interacting\b.*|
        \s+benefiting\b.*|
        \s+using\b.*|
        \s+monitoring\b.*|
        \s+managing\b.*|
        \s+as\s+day-to-day\s+actor\b.*|
        \s+day-to-day\s+actor\b.*
    )$
    """
)
PERSONA_SPLIT_RE = re.compile(r"\s*(?:/|,|;|\band\b)\s*", re.IGNORECASE)


def clean_persona(value: str) -> str:
    cleaned = value.strip()
    cleaned = re.sub(r"^\s*[-*]\s+", "", cleaned)
    cleaned = MARKDOWN_CLEANUP_RE.sub("", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def root_persona(value: str) -> str:
    cleaned = clean_persona(value)
    cleaned = PARENTHETICAL_RE.sub("", cleaned)
    cleaned = SECONDARY_CLAUSE_RE.sub("", cleaned)
    cleaned = NARRATIVE_SUFFIX_RE.sub("", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" ,;/.")
    return cleaned


def split_personas(value: str) -> list[str]:
    root = root_persona(value)
    if not root:
        return []

    parts = [part.strip() for part in PERSONA_SPLIT_RE.split(root)]
    return [part for part in parts if part]


def extract_primary_persona(text: str) -> str | None:
    lines = text.splitlines()

    for index, line in enumerate(lines):
        inline_match = INLINE_PRIMARY_PERSONA_RE.match(line)
        if inline_match:
            persona = clean_persona(inline_match.group(1))
            if persona:
                return persona

        if HEADER_PRIMARY_PERSONA_RE.match(line):
            for next_line in lines[index + 1 :]:
                candidate = next_line.strip()
                if not candidate:
                    continue
                if candidate.startswith("#"):
                    break
                persona = clean_persona(candidate)
                if persona:
                    return persona

    return None


def iter_story_files(capabilities_root: Path):
    yield from sorted(capabilities_root.glob("CAP-*/stories/*/*.md"))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extract unique Primary Persona values from capability story markdown files."
    )
    parser.add_argument(
        "capabilities_root",
        nargs="?",
        default=Path(__file__).resolve().parents[1] / "docs" / "capabilities",
        type=Path,
        help="Path to docs/capabilities (defaults to the sibling path in this repo).",
    )
    args = parser.parse_args()

    capabilities_root = args.capabilities_root.resolve()
    if not capabilities_root.exists():
        raise SystemExit(f"Capabilities path does not exist: {capabilities_root}")

    counts: Counter[str] = Counter()
    files_scanned = 0
    files_with_persona = 0

    for story_file in iter_story_files(capabilities_root):
        files_scanned += 1
        persona = extract_primary_persona(story_file.read_text(encoding="utf-8"))
        if persona:
            for split_persona in split_personas(persona):
                counts[split_persona] += 1
            files_with_persona += 1

    print(f"Capabilities root: {capabilities_root}")
    print(f"Story files scanned: {files_scanned}")
    print(f"Story files with primary persona: {files_with_persona}")
    print(f"Unique primary personas: {len(counts)}")
    print()

    for persona, count in sorted(counts.items(), key=lambda item: (-item[1], item[0].lower())):
        print(f"{count:>4}  {persona}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
