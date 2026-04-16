"""Clarification issue attachment — links [CLARIFICATION] issues to origins."""

from __future__ import annotations

import logging
import re
import sqlite3

log = logging.getLogger(__name__)

# Matches: [CLARIFICATION] Origin #123: Some title text
CLARIFICATION_RE = re.compile(
    r"\[CLARIFICATION\]\s*Origin\s*#(\d+)\s*:", re.IGNORECASE
)


def link_clarifications(con: sqlite3.Connection) -> int:
    """Scan all issue titles for the clarification pattern and create links.

    Returns the number of clarifications linked.
    """
    cursor = con.execute("SELECT repo, number, title FROM issues")
    rows = cursor.fetchall()
    linked = 0

    for row in rows:
        title = row["title"] or ""
        match = CLARIFICATION_RE.search(title)
        if not match:
            continue

        origin_number = int(match.group(1))
        clarification_repo = row["repo"]
        clarification_number = row["number"]

        # The origin issue is assumed to be in the same repo
        # Check if the origin issue exists
        origin = con.execute(
            "SELECT number FROM issues WHERE repo = ? AND number = ?",
            (clarification_repo, origin_number),
        ).fetchone()

        if not origin:
            # Try finding the origin in any repo
            origin = con.execute(
                "SELECT repo, number FROM issues WHERE number = ?",
                (origin_number,),
            ).fetchone()
            if origin:
                origin_repo = origin["repo"]
            else:
                log.warning(
                    "Clarification %s#%d references origin #%d which was not found",
                    clarification_repo,
                    clarification_number,
                    origin_number,
                )
                continue
        else:
            origin_repo = clarification_repo

        con.execute(
            "INSERT OR IGNORE INTO clarifications (clarification_repo, clarification_number, origin_repo, origin_number) VALUES (?,?,?,?)",
            (clarification_repo, clarification_number, origin_repo, origin_number),
        )
        linked += 1
        log.debug(
            "Linked clarification %s#%d → origin %s#%d",
            clarification_repo,
            clarification_number,
            origin_repo,
            origin_number,
        )

    con.commit()
    log.info("Linked %d clarification issues", linked)
    return linked
