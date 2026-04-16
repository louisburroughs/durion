"""Generate the journey gap report as markdown with Mermaid diagrams."""

from __future__ import annotations

import logging
import sqlite3
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from . import baseline_parser as bp
from .gap_detector import Gap

log = logging.getLogger(__name__)


def _summary_table(gaps: list[Gap]) -> str:
    """Generate a summary table of gaps by type and severity."""
    counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for gap in gaps:
        counts[gap.gap_type][gap.severity] += 1

    lines = [
        "| Gap Type | High | Medium | Low | Total |",
        "|----------|------|--------|-----|-------|",
    ]
    for gap_type in ("missing_step", "missing_journey", "role_gap", "integration_gap", "orphan"):
        s = counts.get(gap_type, {})
        h, m, lo = s.get("high", 0), s.get("medium", 0), s.get("low", 0)
        total = h + m + lo
        if total > 0:
            label = gap_type.replace("_", " ").title()
            lines.append(f"| {label} | {h} | {m} | {lo} | {total} |")

    total_all = len(gaps)
    lines.append(f"| **Total** | | | | **{total_all}** |")
    return "\n".join(lines)


def _traceability_section(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
    gaps: list[Gap],
) -> str:
    """Generate per-journey traceability matrices."""
    sections: list[str] = []

    for journey in journeys:
        lines = [
            f"### {journey.name}",
            "",
            f"*Family: {journey.family} | Source: {journey.source_file}*",
            "",
            "| Stage | Persona | Story Refs | Coverage | Notes |",
            "|-------|---------|-----------|----------|-------|",
        ]

        for stage in journey.stages:
            # Determine coverage status
            if not stage.story_ids:
                coverage = "🔴 Missing"
            else:
                # Check if all refs exist
                all_found = True
                for ref in stage.story_ids:
                    num_str = ref.lstrip("#")
                    try:
                        num = int(num_str)
                    except ValueError:
                        continue
                    row = con.execute("SELECT number FROM issues WHERE number = ?", (num,)).fetchone()
                    if not row:
                        all_found = False
                        break

                coverage = "🟢 Full" if all_found else "🟡 Partial"

            refs_str = ", ".join(stage.story_ids) if stage.story_ids else "—"
            notes = stage.notes[:60] if stage.notes else ""
            lines.append(
                f"| {stage.stage_name} | {stage.persona} | {refs_str} | {coverage} | {notes} |"
            )

        # List gaps for this journey
        journey_gaps = [g for g in gaps if g.journey == journey.name]
        if journey_gaps:
            lines.append("")
            lines.append("**Gaps detected:**")
            for g in journey_gaps:
                lines.append(f"- **[{g.severity.upper()}]** {g.description}")

        lines.append("")
        sections.append("\n".join(lines))

    return "\n".join(sections)


def _gaps_by_type(gaps: list[Gap]) -> str:
    """Generate detailed gap listings grouped by type."""
    sections: list[str] = []
    by_type: dict[str, list[Gap]] = defaultdict(list)
    for gap in gaps:
        by_type[gap.gap_type].append(gap)

    type_labels = {
        "missing_step": "Missing Steps",
        "missing_journey": "Missing Journeys",
        "role_gap": "Role Coverage Gaps",
        "integration_gap": "Integration Gaps",
        "orphan": "Orphan Issues",
    }

    for gap_type, label in type_labels.items():
        typed_gaps = by_type.get(gap_type, [])
        if not typed_gaps:
            continue

        lines = [f"### {label}", ""]
        for i, gap in enumerate(typed_gaps, 1):
            severity_icon = {"high": "🔴", "medium": "🟡", "low": "🟢"}.get(gap.severity, "⚪")
            lines.append(f"{i}. {severity_icon} **{gap.severity.upper()}**: {gap.description}")
            if gap.evidence:
                for ev in gap.evidence:
                    lines.append(f"   - {ev}")

        lines.append("")
        sections.append("\n".join(lines))

    return "\n".join(sections)


def _orphan_table(gaps: list[Gap]) -> str:
    """Generate a table of orphan issues."""
    orphans = [g for g in gaps if g.gap_type == "orphan"]
    if not orphans:
        return "*No orphan issues detected.*"

    lines = [
        "| Issue | Description |",
        "|-------|-------------|",
    ]
    for gap in orphans[:50]:  # Limit to 50
        lines.append(f"| {gap.description[:40]} | {gap.description} |")

    if len(orphans) > 50:
        lines.append(f"| ... | *{len(orphans) - 50} more orphans not shown* |")

    return "\n".join(lines)


def _mermaid_journey_diagram(journeys: list[bp.Journey]) -> str:
    """Generate a Mermaid flowchart showing journey families."""
    lines = ["```mermaid", "flowchart TB"]

    for i, journey in enumerate(journeys):
        j_id = f"j{i}"
        lines.append(f"    {j_id}[\"{journey.name}\"]")

        for si, stage in enumerate(journey.stages):
            s_id = f"j{i}s{si}"
            lines.append(f"    {s_id}({stage.stage_name})")
            if si == 0:
                lines.append(f"    {j_id} --> {s_id}")
            else:
                prev_id = f"j{i}s{si - 1}"
                lines.append(f"    {prev_id} --> {s_id}")

    lines.append("```")
    return "\n".join(lines)


def _mermaid_gap_heatmap(gaps: list[Gap]) -> str:
    """Generate a Mermaid diagram highlighting gaps by journey."""
    by_journey: dict[str, list[Gap]] = defaultdict(list)
    for gap in gaps:
        if gap.journey != "(none)":
            by_journey[gap.journey].append(gap)

    if not by_journey:
        return ""

    lines = ["```mermaid", "flowchart LR"]
    for i, (journey, jgaps) in enumerate(sorted(by_journey.items())):
        severity_counts = defaultdict(int)
        for g in jgaps:
            severity_counts[g.severity] += 1

        high = severity_counts.get("high", 0)
        medium = severity_counts.get("medium", 0)

        if high > 0:
            style = ":::critical"
        elif medium > 0:
            style = ":::warning"
        else:
            style = ""

        j_id = f"gap{i}"
        label = f"{journey}\\n({len(jgaps)} gaps)"
        lines.append(f"    {j_id}[\"{label}\"]")

    lines.extend([
        "",
        "    classDef critical fill:#ff6b6b,stroke:#c0392b,color:#fff",
        "    classDef warning fill:#feca57,stroke:#f39c12,color:#333",
    ])

    # Apply styles
    for i, (journey, jgaps) in enumerate(sorted(by_journey.items())):
        high = sum(1 for g in jgaps if g.severity == "high")
        j_id = f"gap{i}"
        if high > 0:
            lines.append(f"    class {j_id} critical")
        elif any(g.severity == "medium" for g in jgaps):
            lines.append(f"    class {j_id} warning")

    lines.append("```")
    return "\n".join(lines)


def generate_report(
    con: sqlite3.Connection,
    journeys: list[bp.Journey],
    gaps: list[Gap],
    output_path: Path,
) -> None:
    """Generate the full journey gap report as markdown."""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    # Gather stats
    issue_count = con.execute("SELECT COUNT(*) as cnt FROM issues").fetchone()["cnt"]
    event_count = con.execute("SELECT COUNT(*) as cnt FROM journey_events").fetchone()["cnt"]

    report_parts = [
        "---",
        "title: Journey Gap Analysis Report",
        f"generated: {now}",
        "---",
        "",
        f"*Generated on {now} | {issue_count} issues | {event_count} events | {len(journeys)} journeys | {len(gaps)} gaps*",
        "",
        "## Summary",
        "",
        _summary_table(gaps),
        "",
        "## Gap Heatmap",
        "",
        _mermaid_gap_heatmap(gaps),
        "",
        "## Journey Traceability",
        "",
        _traceability_section(journeys, con, gaps),
        "",
        "## Gap Details",
        "",
        _gaps_by_type(gaps),
        "",
        "## Orphan Issues",
        "",
        _orphan_table(gaps),
        "",
        "## Journey Overview",
        "",
        _mermaid_journey_diagram(journeys),
        "",
    ]

    report_content = "\n".join(report_parts)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(report_content, encoding="utf-8")
    log.info("Report written to %s (%d bytes)", output_path, len(report_content))
