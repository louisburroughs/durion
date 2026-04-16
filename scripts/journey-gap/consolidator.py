"""LLM-powered issue body consolidation via `gh copilot` CLI."""

from __future__ import annotations

import logging
import shutil
import sqlite3
import subprocess

log = logging.getLogger(__name__)

CONSOLIDATION_PROMPT = """\
You are merging an issue body with its comments and clarification content.

RULES:
1. Preserve the original section headings exactly as they appear.
2. Merge comment and clarification content into the relevant sections.
3. If a comment updates or contradicts a section, use the latest comment content (latest comment wins).
4. Do NOT elaborate, add new questions, or invent new section headings.
5. Do NOT add any preamble or explanation — output only the consolidated issue body.

--- ORIGINAL ISSUE BODY ---
{body}

--- COMMENTS (chronological order) ---
{comments}

--- CLARIFICATION ISSUES ---
{clarifications}

--- OUTPUT ---
Produce the consolidated issue body below:
"""


def _gh_copilot_available() -> bool:
    """Check if the gh copilot CLI extension is available."""
    if not shutil.which("gh"):
        return False
    result = subprocess.run(
        ["gh", "copilot", "--help"],
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result.returncode == 0


def _consolidate_via_copilot(prompt: str) -> str | None:
    """Send a prompt to gh copilot and return the response."""
    try:
        result = subprocess.run(
            ["gh", "copilot", "suggest", "-t", "shell"],
            input=prompt,
            capture_output=True,
            text=True,
            timeout=120,
        )
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
    except (subprocess.TimeoutExpired, OSError) as e:
        log.warning("gh copilot call failed: %s", e)
    return None


def _build_prompt(body: str, comments: list[dict], clarifications: list[dict]) -> str:
    """Build the consolidation prompt from issue parts."""
    comments_text = "\n\n".join(
        f"[{c['created_at']}] @{c['author']}:\n{c['body']}"
        for c in comments
    ) or "(none)"

    clarifications_text = "\n\n".join(
        f"Clarification #{c['number']} ({c['repo']}):\n{c['body']}"
        for c in clarifications
    ) or "(none)"

    return CONSOLIDATION_PROMPT.format(
        body=body or "(empty)",
        comments=comments_text,
        clarifications=clarifications_text,
    )


def _fallback_consolidate(body: str, comments: list[dict], clarifications: list[dict]) -> str:
    """Simple concatenation fallback when gh copilot is unavailable."""
    parts = [body or ""]

    if comments:
        parts.append("\n\n---\n\n## Comments\n")
        for c in comments:
            parts.append(f"\n**@{c['author']}** ({c['created_at']}):\n{c['body']}\n")

    if clarifications:
        parts.append("\n\n---\n\n## Clarifications\n")
        for c in clarifications:
            parts.append(f"\n**Clarification #{c['number']}** ({c['repo']}):\n{c['body']}\n")

    return "".join(parts)


def consolidate(con: sqlite3.Connection) -> int:
    """Consolidate all issues by merging body + comments + clarifications.

    Returns the number of issues consolidated.
    """
    copilot_ok = _gh_copilot_available()
    if copilot_ok:
        log.info("gh copilot available — using LLM consolidation")
    else:
        log.info("gh copilot unavailable — using fallback concatenation")

    cursor = con.execute("SELECT repo, number, body FROM issues")
    issues = cursor.fetchall()
    count = 0

    for issue in issues:
        repo = issue["repo"]
        number = issue["number"]
        body = issue["body"] or ""

        # Fetch comments
        comments_cursor = con.execute(
            "SELECT author, body, created_at FROM comments WHERE repo = ? AND issue_number = ? ORDER BY created_at",
            (repo, number),
        )
        comments = [dict(c) for c in comments_cursor.fetchall()]

        # Fetch clarification issue bodies
        clar_cursor = con.execute(
            """SELECT i.repo, i.number, i.body
               FROM clarifications c
               JOIN issues i ON i.repo = c.clarification_repo AND i.number = c.clarification_number
               WHERE c.origin_repo = ? AND c.origin_number = ?""",
            (repo, number),
        )
        clarifications = [dict(c) for c in clar_cursor.fetchall()]

        # Only use LLM if there are comments or clarifications to merge
        if copilot_ok and (comments or clarifications):
            prompt = _build_prompt(body, comments, clarifications)
            consolidated = _consolidate_via_copilot(prompt)
            if not consolidated:
                consolidated = _fallback_consolidate(body, comments, clarifications)
        elif comments or clarifications:
            consolidated = _fallback_consolidate(body, comments, clarifications)
        else:
            consolidated = body

        con.execute(
            "INSERT OR REPLACE INTO consolidated_issues (repo, number, consolidated_body, canonical_persona) VALUES (?,?,?,?)",
            (repo, number, consolidated, None),
        )
        count += 1

        if count % 50 == 0:
            con.commit()
            log.info("  Consolidated %d/%d issues", count, len(issues))

    con.commit()
    log.info("Consolidated %d issues total", count)
    return count
