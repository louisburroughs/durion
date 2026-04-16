"""Parse baseline journey documents from docs/journeys/*.md."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from pathlib import Path

log = logging.getLogger(__name__)


@dataclass
class JourneyStage:
    """A single stage within a journey."""
    persona: str
    stage_name: str
    story_ids: list[str] = field(default_factory=list)
    notes: str = ""


@dataclass
class Journey:
    """A journey definition parsed from a baseline markdown file."""
    family: str
    name: str
    stages: list[JourneyStage] = field(default_factory=list)
    source_file: str = ""


# Matches H2/H3 headings
HEADING_RE = re.compile(r"^(#{2,3})\s+(.+)$", re.MULTILINE)

# Matches a swimlane table row: | persona | stage | story IDs | notes |
TABLE_ROW_RE = re.compile(
    r"^\|\s*`?([^|`]+?)`?\s*\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*([^|]*?)\s*\|$",
    re.MULTILINE,
)

# Matches the header/separator rows of a markdown table
TABLE_HEADER_RE = re.compile(r"^\|\s*[-:]+\s*\|", re.MULTILINE)

# Matches story references like #123 or #45
STORY_REF_RE = re.compile(r"#(\d+)")

# Matches numbered journey headings: "1. Some Journey" or "### 1. Some Journey"
NUMBERED_HEADING_RE = re.compile(r"^(?:#{2,3}\s+)?(\d+(?:\.\d+)?)[.\s]+(.+)$")


def _extract_story_ids(text: str) -> list[str]:
    """Extract #NNN references from a string."""
    return [f"#{m.group(1)}" for m in STORY_REF_RE.finditer(text)]


def _parse_journey_section(
    heading: str, body: str, family: str, source_file: str
) -> Journey | None:
    """Parse a single journey section into a Journey object."""
    journey = Journey(family=family, name=heading.strip(), source_file=source_file)

    # Find all table rows
    rows = TABLE_ROW_RE.findall(body)
    if not rows:
        return None

    for row in rows:
        persona_raw, stage_name, story_ids_str, notes = row

        # Skip header rows
        if "persona" in persona_raw.lower() or "---" in persona_raw:
            continue
        if TABLE_HEADER_RE.match(f"|{persona_raw}|"):
            continue

        persona = persona_raw.strip().strip("`")
        stage = stage_name.strip()
        story_ids = _extract_story_ids(story_ids_str)
        note = notes.strip()

        if persona and stage:
            journey.stages.append(JourneyStage(
                persona=persona,
                stage_name=stage,
                story_ids=story_ids,
                notes=note,
            ))

    if journey.stages:
        return journey
    return None


def parse_baseline(journeys_dir: Path) -> list[Journey]:
    """Parse all journey baseline documents from a directory.

    Scans for *.md files and extracts journey definitions
    from markdown tables.

    Returns a list of Journey objects.
    """
    journeys: list[Journey] = []
    md_files = sorted(journeys_dir.glob("*.md"))

    if not md_files:
        log.warning("No markdown files found in %s", journeys_dir)
        return journeys

    for md_file in md_files:
        # Skip non-journey files
        if md_file.name in ("personas.md", "journey-gap-discovery.md"):
            continue

        log.info("Parsing baseline: %s", md_file.name)
        try:
            content = md_file.read_text(encoding="utf-8")
        except (PermissionError, UnicodeDecodeError) as e:
            log.warning("Skipping %s: %s", md_file.name, e)
            continue

        # Split into sections by H2/H3 headings
        sections: list[tuple[str, str, str]] = []
        current_family = md_file.stem.replace("-", " ").replace("_", " ").title()
        headings = list(HEADING_RE.finditer(content))

        if not headings:
            # Treat entire file as one section
            journey = _parse_journey_section(
                current_family, content, current_family, md_file.name
            )
            if journey:
                journeys.append(journey)
            continue

        for i, match in enumerate(headings):
            level = len(match.group(1))
            heading_text = match.group(2).strip()
            start = match.end()
            end = headings[i + 1].start() if i + 1 < len(headings) else len(content)
            body = content[start:end]

            # H2 headings are journey families, H3 are sub-journeys
            if level == 2:
                current_family = heading_text
                # Check if this section itself has table rows
                journey = _parse_journey_section(
                    heading_text, body, current_family, md_file.name
                )
                if journey:
                    journeys.append(journey)
            elif level == 3:
                journey = _parse_journey_section(
                    heading_text, body, current_family, md_file.name
                )
                if journey:
                    journeys.append(journey)

    log.info("Parsed %d journeys from %d files", len(journeys), len(md_files))
    return journeys


def get_all_baseline_story_refs(journeys: list[Journey]) -> set[str]:
    """Get all story references from baseline journeys."""
    refs: set[str] = set()
    for journey in journeys:
        for stage in journey.stages:
            refs.update(stage.story_ids)
    return refs


def get_personas_in_baseline(journeys: list[Journey]) -> set[str]:
    """Get all persona names used in baseline journeys."""
    return {stage.persona for j in journeys for stage in j.stages}
