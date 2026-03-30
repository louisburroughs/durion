#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


BUSINESS_VALUE_RE = re.compile(
    r"(?m)^(?P<line>(?:#{2,6}\s+Business Value\s*$|(?:[-*]\s+)?(?:\*\*)?Business Value(?:\*\*)?\s*:?.*$))"
)


def build_metadata_block(entry: dict) -> str:
    metadata = entry["persona_metadata"][0]
    return "\n".join(
        [
            "**Persona Metadata**",
            f"- `primary_persona`: `{entry['primary_persona']}`",
            f"- `canonical_persona`: `{metadata['canonical_persona']}`",
            f"- `persona_family`: `{metadata['persona_family']}`",
            f"- `actor_type`: `{metadata['actor_type']}`",
            "",
            "",
        ]
    )


def apply_metadata(text: str, entry: dict) -> tuple[str, str]:
    if "`canonical_persona`" in text or "**Persona Metadata**" in text:
        return text, "skipped_existing"

    match = BUSINESS_VALUE_RE.search(text)
    if not match:
        return text, "skipped_no_business_value"

    block = build_metadata_block(entry)
    start = match.start("line")
    updated = text[:start] + block + text[start:]
    return updated, "updated"


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Write normalized persona metadata blocks into story markdown files for unambiguous mappings."
    )
    parser.add_argument(
        "--mapping-json",
        default=repo_root / "persona_headers_by_story.json",
        type=Path,
        help="Path to generated persona header JSON.",
    )
    args = parser.parse_args()

    mapping_path = args.mapping_json.resolve()
    entries = json.loads(mapping_path.read_text(encoding="utf-8"))

    updated = 0
    skipped_existing = 0
    skipped_no_business_value = 0
    skipped_review = 0

    for entry in entries:
        if entry["review_required"] or len(entry["persona_metadata"]) != 1 or entry["unmapped_personas"]:
            skipped_review += 1
            continue

        story_path = Path(entry["story_path"])
        original = story_path.read_text(encoding="utf-8")
        rewritten, status = apply_metadata(original, entry)

        if status == "updated":
            story_path.write_text(rewritten, encoding="utf-8")
            updated += 1
        elif status == "skipped_existing":
            skipped_existing += 1
        elif status == "skipped_no_business_value":
            skipped_no_business_value += 1

    print(f"Updated stories: {updated}")
    print(f"Skipped review-required stories: {skipped_review}")
    print(f"Skipped already-annotated stories: {skipped_existing}")
    print(f"Skipped stories without business-value anchor: {skipped_no_business_value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
