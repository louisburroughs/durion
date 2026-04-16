"""Gap detection — compares normalized events against baseline journeys."""

from __future__ import annotations

import logging
import sqlite3
from dataclasses import dataclass, field

from . import baseline_parser as bp

log = logging.getLogger(__name__)


@dataclass
class Gap:
    """A detected gap."""
    gap_type: str  # missing_step, missing_journey, role_gap, integration_gap, orphan
    severity: str  # high, medium, low
    journey: str
    stage: str
    description: str
    evidence: list[str] = field(default_factory=list)


@dataclass
class CoveredStage:
    """A stage that has been covered by one or more issues."""
    journey: str
    stage: str
    persona: str
    expected_event: tuple[str, str]
    covering_issues: list[int] = field(default_factory=list)


def detect_gaps(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
) -> tuple[list[Gap], list[CoveredStage]]:
    """Detect gaps and return covered stages by auto-slotting."""
    gaps: list[Gap] = []
    covered: list[CoveredStage] = []

    # Get all active issues
    cur = con.cursor()
    cur.execute("SELECT number, title FROM issues WHERE UPPER(state) = 'OPEN'")
    active_issues = {row[0]: row[1] for row in cur.fetchall()}
    slotted_issues = set()

    for journey in journeys:
        for stage in journey.stages:
            if not stage.expected_events:
                # Stage with no expected events
                gaps.append(Gap(
                    gap_type="missing_step",
                    severity="high",
                    journey=journey.name,
                    stage=stage.stage_name,
                    description=f"Stage '{stage.stage_name}' has no expected semantic events defined",
                    evidence=[],
                ))
                continue
                
            for action, obj in stage.expected_events:
                # Query DB for matches
                cur.execute(
                    '''
                    SELECT issue_number FROM journey_events
                    WHERE actor LIKE ? AND action LIKE ? AND object LIKE ?
                    ''',
                    (f"%{stage.persona}%", f"%{action}%", f"%{obj}%")
                )
                matches = cur.fetchall()
                
                if matches:
                    covered_issues = [m[0] for m in matches if m[0] in active_issues]
                    if covered_issues:
                        slotted_issues.update(covered_issues)
                        covered.append(CoveredStage(
                            journey=journey.name,
                            stage=stage.stage_name,
                            persona=stage.persona,
                            expected_event=(action, obj),
                            covering_issues=covered_issues
                        ))
                    else:
                         gaps.append(Gap(
                            gap_type="missing_step",
                            severity="high",
                            journey=journey.name,
                            stage=stage.stage_name,
                            description=f"Expected event [{action}, {obj}] for '{stage.persona}' is matched by closed issues but no OPEN issues",
                            evidence=[f"[Action: {action}, Object: {obj}]"],
                        ))
                else:
                    # Missing
                    gaps.append(Gap(
                        gap_type="missing_step",
                        severity="high",
                        journey=journey.name,
                        stage=stage.stage_name,
                        description=f"Expected event [{action}, {obj}] for '{stage.persona}' has no covering issues in the backlog",
                        evidence=[f"[Action: {action}, Object: {obj}]"],
                    ))

    # Identify Orphans
    for num, title in active_issues.items():
        if num not in slotted_issues:
            gaps.append(Gap(
                gap_type="orphan",
                severity="medium",
                journey="Unmapped",
                stage=f"Issue #{num}",
                description=f"Active capability not mapped to any known journey event",
                evidence=[f"#{num}: {title}"],
            ))
            
    return gaps, covered
