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


def _detect_missing_steps(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
) -> list[Gap]:
    """Detect stages that have story references but no matching issues."""
    gaps: list[Gap] = []

    for journey in journeys:
        for stage in journey.stages:
            if not stage.story_ids:
                # Stage with no story refs at all — that's a gap
                gaps.append(Gap(
                    gap_type="missing_step",
                    severity="medium",
                    journey=journey.name,
                    stage=stage.stage_name,
                    description=f"Stage '{stage.stage_name}' in journey '{journey.name}' has no associated story references",
                    evidence=[f"Baseline file: {journey.source_file}"],
                ))
                continue

            # Check each story ref exists in the DB
            for ref in stage.story_ids:
                number_str = ref.lstrip("#")
                try:
                    number = int(number_str)
                except ValueError:
                    continue

                row = con.execute(
                    "SELECT number FROM issues WHERE number = ?",
                    (number,),
                ).fetchone()

                if not row:
                    gaps.append(Gap(
                        gap_type="missing_step",
                        severity="high",
                        journey=journey.name,
                        stage=stage.stage_name,
                        description=f"Story {ref} referenced in stage '{stage.stage_name}' was not found in any repo",
                        evidence=[f"Journey: {journey.name}", f"Stage: {stage.stage_name}", f"Source: {journey.source_file}"],
                    ))

    return gaps


def _detect_role_gaps(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
) -> list[Gap]:
    """Detect personas that appear in events but not in baseline journeys, and vice versa."""
    gaps: list[Gap] = []

    baseline_personas = bp.get_personas_in_baseline(journeys)

    # Get all actors from extracted events
    cursor = con.execute("SELECT DISTINCT actor FROM journey_events")
    event_actors = {row["actor"] for row in cursor.fetchall()}

    # Actors in events but not in baseline
    for actor in sorted(event_actors - baseline_personas):
        if actor in ("Unknown", "Unclassified"):
            continue
        # Count how many events this actor has
        count = con.execute(
            "SELECT COUNT(*) as cnt FROM journey_events WHERE actor = ?",
            (actor,),
        ).fetchone()["cnt"]

        gaps.append(Gap(
            gap_type="role_gap",
            severity="medium" if count > 3 else "low",
            journey="(none)",
            stage="(none)",
            description=f"Persona '{actor}' appears in {count} extracted events but is not in any baseline journey",
            evidence=[f"Event count: {count}"],
        ))

    # Personas in baseline but with no events
    for persona in sorted(baseline_personas - event_actors):
        gaps.append(Gap(
            gap_type="role_gap",
            severity="low",
            journey="(none)",
            stage="(none)",
            description=f"Persona '{persona}' exists in baseline journeys but has no matching extracted events",
            evidence=[],
        ))

    return gaps


def _detect_orphan_issues(con: sqlite3.Connection) -> list[Gap]:
    """Detect issues that are not tracked by any parent and not referenced in any baseline."""
    gaps: list[Gap] = []

    # Issues that are not tracked by any parent
    cursor = con.execute(
        """SELECT i.repo, i.number, i.title
           FROM issues i
           WHERE NOT EXISTS (
               SELECT 1 FROM sub_issues si
               WHERE si.child_repo = i.repo AND si.child_number = i.number
           )
           AND i.state != 'closed'
           ORDER BY i.repo, i.number"""
    )
    orphans = cursor.fetchall()

    for orphan in orphans:
        gaps.append(Gap(
            gap_type="orphan",
            severity="low",
            journey="(none)",
            stage="(none)",
            description=f"Issue {orphan['repo']}#{orphan['number']} ({orphan['title'][:80]}) has no parent tracker",
            evidence=[f"Repo: {orphan['repo']}", f"State: open"],
        ))

    return gaps


def _detect_integration_gaps(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
) -> list[Gap]:
    """Detect journey stages that suggest cross-domain integration but lack counterpart events."""
    gaps: list[Gap] = []

    # Look for stages that mention other domains in their notes
    integration_keywords = {
        "order": "Order",
        "inventory": "Inventory",
        "invoice": "Invoice",
        "accounting": "Accounting",
        "catalog": "Catalog",
        "customer": "Customer",
        "workorder": "Workorder",
        "price": "Price",
        "payment": "Payment",
        "document": "Document",
    }

    for journey in journeys:
        journey_domain = None
        for keyword, domain in integration_keywords.items():
            if keyword in journey.name.lower():
                journey_domain = domain
                break

        if not journey_domain:
            continue

        for stage in journey.stages:
            notes_lower = (stage.notes or "").lower()
            for keyword, target_domain in integration_keywords.items():
                if target_domain == journey_domain:
                    continue
                if keyword in notes_lower:
                    # Check if we have events that bridge these domains
                    bridging = con.execute(
                        """SELECT COUNT(*) as cnt FROM journey_events
                           WHERE object LIKE ? AND actor IN (
                               SELECT DISTINCT actor FROM journey_events WHERE object LIKE ?
                           )""",
                        (f"%{target_domain}%", f"%{journey_domain}%"),
                    ).fetchone()["cnt"]

                    if bridging == 0:
                        gaps.append(Gap(
                            gap_type="integration_gap",
                            severity="medium",
                            journey=journey.name,
                            stage=stage.stage_name,
                            description=f"Stage '{stage.stage_name}' references {target_domain} domain but no bridging events found",
                            evidence=[
                                f"Journey domain: {journey_domain}",
                                f"Target domain: {target_domain}",
                                f"Notes: {stage.notes[:100]}",
                            ],
                        ))

    return gaps


def _detect_missing_journeys(
    journeys: list[bp.Journey],
    con: sqlite3.Connection,
) -> list[Gap]:
    """Detect domain objects with extracted events but no baseline journey."""
    gaps: list[Gap] = []

    # Get all unique action objects from events
    cursor = con.execute(
        """SELECT object, COUNT(*) as cnt
           FROM journey_events
           GROUP BY object
           HAVING cnt >= 3
           ORDER BY cnt DESC"""
    )
    event_objects = cursor.fetchall()

    # Get all journey names and stage names from baseline
    baseline_names = set()
    for j in journeys:
        baseline_names.add(j.name.lower())
        for s in j.stages:
            baseline_names.add(s.stage_name.lower())

    for obj in event_objects:
        obj_name = obj["object"]
        obj_lower = obj_name.lower()

        # Check if this object is covered by any baseline journey
        covered = any(obj_lower in name for name in baseline_names)
        if not covered and obj_lower not in ("unknown", "unclassified"):
            gaps.append(Gap(
                gap_type="missing_journey",
                severity="high" if obj["cnt"] >= 5 else "medium",
                journey="(none)",
                stage="(none)",
                description=f"Domain object '{obj_name}' has {obj['cnt']} events but no corresponding baseline journey",
                evidence=[f"Event count: {obj['cnt']}"],
            ))

    return gaps


def detect_gaps(
    con: sqlite3.Connection,
    journeys: list[bp.Journey],
) -> list[Gap]:
    """Run all gap detectors and return combined results.

    Returns a list of Gap objects sorted by severity (high first).
    """
    all_gaps: list[Gap] = []

    log.info("Running gap detection...")

    log.info("  Detecting missing steps...")
    all_gaps.extend(_detect_missing_steps(journeys, con))

    log.info("  Detecting missing journeys...")
    all_gaps.extend(_detect_missing_journeys(journeys, con))

    log.info("  Detecting role gaps...")
    all_gaps.extend(_detect_role_gaps(journeys, con))

    log.info("  Detecting integration gaps...")
    all_gaps.extend(_detect_integration_gaps(journeys, con))

    log.info("  Detecting orphan issues...")
    all_gaps.extend(_detect_orphan_issues(con))

    # Sort by severity
    severity_order = {"high": 0, "medium": 1, "low": 2}
    all_gaps.sort(key=lambda g: (severity_order.get(g.severity, 3), g.gap_type, g.journey))

    log.info("Detected %d total gaps", len(all_gaps))
    return all_gaps
