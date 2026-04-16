#!/usr/bin/env python3
"""Journey Gap Discovery CLI — orchestrates the full pipeline."""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from . import db
from . import extractor
from . import clarification_linker
from . import consolidator
from . import normalizer
from . import baseline_parser
from . import gap_detector
from . import reporter

log = logging.getLogger("journey-gap")

DEFAULT_DB = Path(__file__).resolve().parent / "github-issues.db"
DEFAULT_OUTPUT = Path(__file__).resolve().parent.parent.parent / "docs" / "journeys" / "journey-gap-report.md"
DEFAULT_JOURNEYS_DIR = Path(__file__).resolve().parent.parent.parent / "docs" / "journeys"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="journey-gap",
        description="Discover gaps between GitHub issues and baseline journey maps.",
    )
    parser.add_argument(
        "--db-path",
        type=Path,
        default=DEFAULT_DB,
        help=f"SQLite database path (default: {DEFAULT_DB})",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Output report path (default: {DEFAULT_OUTPUT})",
    )
    parser.add_argument(
        "--journeys-dir",
        type=Path,
        default=DEFAULT_JOURNEYS_DIR,
        help=f"Baseline journey docs directory (default: {DEFAULT_JOURNEYS_DIR})",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable debug logging",
    )

    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    log.info("=== Journey Gap Discovery ===")
    log.info("DB: %s", args.db_path)
    log.info("Output: %s", args.output)
    log.info("Journeys: %s", args.journeys_dir)

    # Step 1: Connect and reset DB (full re-extract each run)
    con = db.connect(args.db_path)
    db.reset(con)
    log.info("Database initialized at %s", args.db_path)

    # Step 2: Extract all issues
    log.info("")
    log.info("--- Step 1: Extract Issues ---")
    extractor.extract(con)

    # Step 3: Link clarifications
    log.info("")
    log.info("--- Step 2: Link Clarifications ---")
    linked = clarification_linker.link_clarifications(con)
    log.info("Linked %d clarification issues", linked)

    # Step 4: Consolidate issue bodies
    log.info("")
    log.info("--- Step 3: Consolidate Issues ---")
    consolidated = consolidator.consolidate(con)
    log.info("Consolidated %d issues", consolidated)

    # Step 5: Normalize events and personas
    log.info("")
    log.info("--- Step 4: Normalize Events ---")
    events = normalizer.normalize(con)
    log.info("Extracted %d journey events", events)

    # Step 6: Parse baseline journeys
    log.info("")
    log.info("--- Step 5: Parse Baseline ---")
    journeys = baseline_parser.parse_baseline(args.journeys_dir)
    log.info("Parsed %d baseline journeys", len(journeys))

    # Step 7: Detect gaps
    log.info("")
    log.info("--- Step 6: Detect Gaps ---")
    gaps = gap_detector.detect_gaps(con, journeys)
    log.info("Found %d gaps", len(gaps))

    # Step 8: Generate report
    log.info("")
    log.info("--- Step 7: Generate Report ---")
    reporter.generate_report(con, journeys, gaps, args.output)

    con.close()
    log.info("")
    log.info("=== Done ===")
    log.info("Report: %s", args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
