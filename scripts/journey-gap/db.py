"""SQLite schema creation and connection helpers."""

from __future__ import annotations

import logging
import sqlite3
from pathlib import Path

log = logging.getLogger(__name__)

SCHEMA_SQL = """\
CREATE TABLE IF NOT EXISTS issues (
  number INTEGER,
  repo TEXT,
  title TEXT,
  state TEXT,
  body TEXT,
  url TEXT,
  labels TEXT,
  milestone TEXT,
  created_at TEXT,
  updated_at TEXT,
  PRIMARY KEY (repo, number)
);

CREATE TABLE IF NOT EXISTS comments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo TEXT,
  issue_number INTEGER,
  author TEXT,
  body TEXT,
  created_at TEXT,
  FOREIGN KEY (repo, issue_number) REFERENCES issues(repo, number)
);

CREATE TABLE IF NOT EXISTS sub_issues (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_repo TEXT,
  parent_number INTEGER,
  child_repo TEXT,
  child_number INTEGER,
  title TEXT,
  state TEXT,
  FOREIGN KEY (parent_repo, parent_number) REFERENCES issues(repo, number)
);

CREATE TABLE IF NOT EXISTS clarifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  clarification_repo TEXT,
  clarification_number INTEGER,
  origin_repo TEXT,
  origin_number INTEGER,
  FOREIGN KEY (clarification_repo, clarification_number) REFERENCES issues(repo, number),
  FOREIGN KEY (origin_repo, origin_number) REFERENCES issues(repo, number)
);

CREATE TABLE IF NOT EXISTS consolidated_issues (
  repo TEXT,
  number INTEGER,
  consolidated_body TEXT,
  canonical_persona TEXT,
  PRIMARY KEY (repo, number),
  FOREIGN KEY (repo, number) REFERENCES issues(repo, number)
);

CREATE TABLE IF NOT EXISTS journey_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo TEXT,
  issue_number INTEGER,
  actor TEXT,
  action TEXT,
  object TEXT,
  source_ref TEXT,
  FOREIGN KEY (repo, issue_number) REFERENCES issues(repo, number)
);
"""


def connect(db_path: Path) -> sqlite3.Connection:
    """Open (or create) the SQLite database and ensure schema exists."""
    db_path.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(str(db_path))
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL;")
    con.execute("PRAGMA foreign_keys=ON;")
    con.executescript(SCHEMA_SQL)
    con.commit()
    log.info("Database ready at %s", db_path)
    return con


def reset(con: sqlite3.Connection) -> None:
    """Drop and recreate all tables (full re-extract)."""
    tables = [
        "journey_events",
        "consolidated_issues",
        "clarifications",
        "sub_issues",
        "comments",
        "issues",
    ]
    for t in tables:
        con.execute(f"DELETE FROM {t};")  # noqa: S608 — table names are hard-coded
    con.commit()
    log.info("All tables cleared for full re-extract")
