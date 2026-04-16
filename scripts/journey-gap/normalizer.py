"""Journey event extraction and canonical persona assignment."""

from __future__ import annotations

import logging
import re
import sqlite3
from pathlib import Path

log = logging.getLogger(__name__)

# Canonical persona taxonomy — parsed from docs/journeys/personas.md
# Maps raw persona labels to canonical persona names
_PERSONA_MERGES: dict[str, str] = {
    # Service Advisor stays as-is
    "service advisor": "Service Advisor",
    # Customer Support family
    "csr": "Customer Support Associate",
    "customer service representative": "Customer Support Associate",
    "customer support associate": "Customer Support Associate",
    "counter associate": "Customer Support Associate",
    "front desk": "Customer Support Associate",
    "cashier": "Customer Support Associate",
    "pos cashier": "Customer Support Associate",
    "pos clerk": "Customer Support Associate",
    # Account Management
    "account manager": "Account Manager",
    "fleet account manager": "Account Manager",
    # Location Management
    "location manager": "Location Manager",
    "shop manager": "Location Manager",
    "store manager": "Location Manager",
    "back office": "Location Manager",
    "back office manager": "Location Manager",
    "manager": "Location Manager",
    "approver": "Location Manager",
    "shop administrator": "Location Manager",
    "hr administrator": "Location Manager",
    "ops admin": "Location Manager",
    # Dispatch
    "dispatcher": "Dispatcher",
    "scheduler": "Dispatcher",
    "mobile lead": "Dispatcher",
    # Technician
    "technician": "Technician",
    "mechanic": "Technician",
    # Parts
    "parts manager": "Parts Manager",
    "parts associate": "Parts Associate",
    "parts counter staff": "Parts Associate",
    # Inventory
    "inventory control manager": "Inventory Control Manager",
    "inventory manager": "Inventory Control Manager",
    "inventory controller": "Inventory Control Manager",
    "inventory admin": "Inventory Control Manager",
    "inventory staff": "Inventory Staff",
    # Warehouse
    "warehouse manager": "Warehouse Manager",
    "warehouse associate": "Warehouse Associate",
    "receiver": "Warehouse Associate",
    "stock clerk": "Warehouse Associate",
    # Accounting
    "accounting associate": "Accounting Associate",
    "accountant": "Accounting Associate",
    "gl accountant": "Accounting Associate",
    "gl specialist": "Accounting Associate",
    "accounting clerk": "Accounting Associate",
    "accounts receivable clerk": "Accounting Associate",
    "ap clerk": "Accounting Associate",
    "billing specialist": "Accounting Associate",
    "back office accountant": "Accounting Associate",
    "payroll clerk": "Accounting Associate",
    "accounting operations user": "Accounting Associate",
    "accounting ops": "Accounting Associate",
    "accounting ops user": "Accounting Associate",
    "accounting operations analyst": "Accounting Associate",
    "accounting ops specialist": "Accounting Associate",
    "accounting integration operator": "Accounting Associate",
    "integration operator": "Accounting Associate",
    "finance ops user": "Accounting Associate",
    # Finance management
    "controller": "Controller",
    "financial controller": "Controller",
    "finance": "Controller",
    "finance manager": "Finance Manager",
    "financemanager": "Finance Manager",
    "accounting manager": "Accounting Manager",
    # Audit
    "auditor": "Auditor",
    "compliance auditor": "Auditor",
    "system auditor": "Auditor",
    # Product/Pricing
    "product admin": "Product Administrator",
    "product administrator": "Product Administrator",
    "pricing administrator": "Pricing Administrator",
    "pricing analyst": "Pricing Analyst",
    "pricing manager": "Pricing Manager",
    # System admin
    "system administrator": "System Administrator",
    "admin": "System Administrator",
    "admin user": "System Administrator",
    "accounting admin": "System Administrator",
    "finance admin": "System Administrator",
    # Director
    "director": "Director",
    # Marketing
    "marketing manager": "Marketing Manager",
    # Technical
    "backend engineer": "Backend Engineer",
    "domain architect": "Domain Architect",
    "platform engineer": "Platform Engineer",
    "platform integrator": "Platform Integrator",
    "integration support engineer": "Integration Support Engineer",
    "moqui engineer": "Moqui Engineer",
    # Non-human
    "system": "System",
    "system user": "System",
}

# All canonical persona names (unique values)
CANONICAL_PERSONAS = sorted(set(_PERSONA_MERGES.values()))

# Regex to find "As a <persona>" patterns in issue bodies
AS_A_RE = re.compile(
    r"[Aa]s\s+(?:a|an)\s+([A-Z][A-Za-z /&]+?)(?:,|\s+I\s)",
)

# Regex to find "Primary Persona:" or "Canonical Persona:" headers
PERSONA_HEADER_RE = re.compile(
    r"(?:Primary|Canonical)\s+Persona\s*:\s*`?([^`\n]+?)`?\s*$",
    re.MULTILINE,
)

# Regex to extract action/object from user story format
# "I want to <action> <object>"
ACTION_RE = re.compile(
    r"I\s+want\s+to\s+(\w+(?:\s+\w+)?)\s+(?:a|an|the|my|all)?\s*(\w+(?:\s+\w+)?)",
    re.IGNORECASE,
)


def _resolve_persona(raw: str) -> str | None:
    """Map a raw persona string to a canonical persona name."""
    cleaned = raw.strip().lower()
    # Direct lookup
    if cleaned in _PERSONA_MERGES:
        return _PERSONA_MERGES[cleaned]
    # Partial match — check if any key is a substring
    for key, canonical in _PERSONA_MERGES.items():
        if key in cleaned or cleaned in key:
            return canonical
    return None


def _extract_persona_from_body(body: str) -> str | None:
    """Try to extract the canonical persona from an issue body."""
    # Try explicit persona header first
    header_match = PERSONA_HEADER_RE.search(body)
    if header_match:
        resolved = _resolve_persona(header_match.group(1))
        if resolved:
            return resolved

    # Try "As a <persona>" pattern
    as_a_match = AS_A_RE.search(body)
    if as_a_match:
        resolved = _resolve_persona(as_a_match.group(1))
        if resolved:
            return resolved

    return None


def _extract_events_from_body(body: str, repo: str, number: int) -> list[dict]:
    """Extract simplified journey events from an issue body."""
    events: list[dict] = []
    source_ref = f"{repo}#{number}"

    # Extract all "As a X, I want to Y Z" patterns
    for as_a_match in AS_A_RE.finditer(body):
        actor_raw = as_a_match.group(1)
        actor = _resolve_persona(actor_raw) or actor_raw.strip()

        # Find the action/object that follows
        remaining = body[as_a_match.end():]
        action_match = ACTION_RE.search(remaining[:200])
        if action_match:
            action = action_match.group(1).strip().title()
            obj = action_match.group(2).strip().title()
            events.append({
                "actor": actor,
                "action": action,
                "object": obj,
                "source_ref": source_ref,
            })

    # If no user-story pattern found, try to derive from title
    if not events:
        # We'll create a single event from the title in the caller
        pass

    return events


def _extract_event_from_title(title: str, repo: str, number: int, persona: str | None) -> dict | None:
    """Extract a journey event from an issue title."""
    source_ref = f"{repo}#{number}"

    # Common title patterns: "Create X", "List X", "Update X", etc.
    title_action_re = re.compile(
        r"^(?:feat|fix|chore)?[:\s]*(?:As\s+\w+\s+)?(\w+)\s+(.+?)(?:\s*[-–—]\s*.+)?$",
        re.IGNORECASE,
    )
    match = title_action_re.match(title.strip())
    if match:
        action = match.group(1).strip().title()
        obj = match.group(2).strip().title()
        # Filter out noise words as actions
        # Skip noise words that aren't real actions
        if action.lower() in ("the", "a", "an", "this", "that"):
            return None
        return {
            "actor": persona or "Unknown",
            "action": action,
            "object": obj[:100],
            "source_ref": source_ref,
        }

    return None


def normalize(con: sqlite3.Connection) -> int:
    """Extract journey events and assign canonical personas.

    Returns the number of events extracted.
    """
    cursor = con.execute(
        "SELECT ci.repo, ci.number, ci.consolidated_body, i.title FROM consolidated_issues ci JOIN issues i ON i.repo = ci.repo AND i.number = ci.number"
    )
    issues = cursor.fetchall()
    event_count = 0

    for issue in issues:
        repo = issue["repo"]
        number = issue["number"]
        body = issue["consolidated_body"] or ""
        title = issue["title"] or ""

        # Determine canonical persona
        persona = _extract_persona_from_body(body)
        if not persona:
            persona = _extract_persona_from_body(title)

        # Update the canonical_persona field
        if persona:
            con.execute(
                "UPDATE consolidated_issues SET canonical_persona = ? WHERE repo = ? AND number = ?",
                (persona, repo, number),
            )

        # Extract events from body
        events = _extract_events_from_body(body, repo, number)

        # If no events from body, try from title
        if not events:
            title_event = _extract_event_from_title(title, repo, number, persona)
            if title_event:
                events = [title_event]

        # If still no events, create a minimal one
        if not events and persona:
            events = [{
                "actor": persona,
                "action": "Unclassified",
                "object": title[:100] if title else "Unknown",
                "source_ref": f"{repo}#{number}",
            }]

        for event in events:
            con.execute(
                "INSERT INTO journey_events (repo, issue_number, actor, action, object, source_ref) VALUES (?,?,?,?,?,?)",
                (repo, number, event["actor"], event["action"], event["object"], event["source_ref"]),
            )
            event_count += 1

    con.commit()
    log.info("Extracted %d journey events from %d issues", event_count, len(issues))
    return event_count


def load_persona_taxonomy(personas_path: Path) -> dict[str, str]:
    """Load and return the persona merge map.

    This can be extended to parse personas.md dynamically,
    but the current version uses the hard-coded taxonomy
    which was derived from personas.md.
    """
    return dict(_PERSONA_MERGES)
