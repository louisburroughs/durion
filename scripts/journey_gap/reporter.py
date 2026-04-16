"""Generate the journey gap report as markdown with Mermaid diagrams."""

from __future__ import annotations

import logging
import sqlite3
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from . import baseline_parser as bp
from .gap_detector import Gap, CoveredStage

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
    covered: list[CoveredStage],
) -> str:
    """Generate per-journey traceability matrices."""
    sections: list[str] = []

    for journey in journeys:
        lines = [
            f"### {journey.name}",
            "",
            f"*Family: {journey.family} | Source: {journey.source_file}*",
            "",
            "| Stage | Persona | Expected Events | Covering Issues | Coverage Status | Notes |",
            "|-------|---------|-----------------|-----------------|-----------------|-------|",
        ]

        for stage in journey.stages:
            expected_events_str = ", ".join([f"[{a}, {o}]" for a, o in stage.expected_events]) if stage.expected_events else "—"

            if not stage.expected_events:
                coverage_status = "🔴 Missing"
                covering_refs_str = "—"
            else:
                stage_covered_events = [c for c in covered if c.journey == journey.name and c.stage == stage.stage_name and c.persona == stage.persona]
                
                # Check for active issues
                covering_issues = set()
                for c in stage_covered_events:
                    for i in c.covering_issues:
                        row = con.execute("SELECT number FROM issues WHERE number = ? AND state = 'OPEN'", (i,)).fetchone()
                        if row:
                            covering_issues.add(f"#{row[0]}")
                            
                covering_refs_str = ", ".join(sorted(list(covering_issues))) if covering_issues else "—"
                
                # Check if all expected events are covered
                all_covered = True
                for action, obj in stage.expected_events:
                    found = False
                    for c in stage_covered_events:
                        if c.expected_event == (action, obj):
                            # check if it has open issues
                            for i in c.covering_issues:
                                if con.execute("SELECT number FROM issues WHERE number = ? AND state = 'OPEN'", (i,)).fetchone():
                                    found = True
                                    break
                    if not found:
                        all_covered = False
                        break
                        
                if all_covered and covering_issues:
                    coverage_status = "🟢 Full"
                elif covering_issues:
                    coverage_status = "🟡 Partial"
                else:
                    coverage_status = "🔴 Missing"

            notes_text = stage.notes[:60] if stage.notes else ""
            lines.append(f"| {stage.stage_name} | `{stage.persona}` | `{expected_events_str}` | {covering_refs_str} | {coverage_status} | {notes_text} |")

        sections.append("\n".join(lines))

    return "\n\n".join(sections)


def _gaps_list(gaps: list[Gap]) -> str:
    """Format the raw list of gaps."""
    if not gaps:
        return "No gaps detected! 🎉"

    # Sort by severity (high -> medium -> low) then gap_type
    def sev_sort(g: Gap) -> int:
        return {"high": 1, "medium": 2, "low": 3}.get(g.severity, 99)

    sorted_gaps = sorted(gaps, key=lambda g: (sev_sort(g), g.gap_type, g.journey))

    lines = []
    for g in sorted_gaps:
        # e.g., 🔴 **High** - **Missing Step** in *Customer Intake to Estimate* (Intake)
        icon = {"high": "🔴", "medium": "🟡", "low": "🟢"}.get(g.severity, "")
        gap_type_lbl = g.gap_type.replace("_", " ").title()
        
        lines.append(f"### {icon} {g.severity.title()} - {gap_type_lbl}")
        
        if g.journey != "Unmapped":
             lines.append(f"**Location:** {g.journey} > {g.stage}")
             
        lines.append(f"**Description:** {g.description}")
        if g.evidence:
            lines.append("")
            lines.append("**Evidence:**")
            for ev in g.evidence:
                lines.append(f"- {ev}")
        lines.append("")

    return "\n".join(lines)


def generate_report(
    con: sqlite3.Connection,
    journeys: list[bp.Journey],
    gaps: list[Gap],
    covered: list[CoveredStage],
    output_path: Path,
) -> None:
    """Generate and write the final markdown report."""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    md = [
        "# Journey Gap Discovery Report",
        "",
        f"> Generated on: {now}",
        "",
        "## Executive Summary",
        "",
        _summary_table(gaps),
        "",
        "## Detailed Gaps",
        "",
        _gaps_list(gaps),
        "",
        "## Traceability Matrix",
        "",
        _traceability_section(journeys, con, gaps, covered),
    ]

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("\n".join(md), encoding="utf-8")
    log.info("Wrote report to %s", output_path)
