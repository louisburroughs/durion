#!/usr/bin/env python3
"""Give every domain business-rules guide an OKF-conformant frontmatter block.

The knowledge catalog's domain notes point at `domains/<domain>/.business-rules/`,
so those guides are the documents an agent opens next. OKF v0.2 asks one thing of
a concept file: parseable YAML frontmatter with a non-empty `type`.

Keys are inserted, never re-rendered. Several guides carry hand-written frontmatter
with nested values (the contract guides' `traceability:` block), and re-emitting a
parsed mapping would silently flatten them. This script adds `type` at the top and
appends only the recommended keys that are missing, leaving every existing line
byte-identical.

`BACKEND_API_REFERENCE.generated.md` is written by generate_backend_contract_guides.py,
which emits the same block itself; this script keeps those files conformant in between
regenerations.

Usage:
    python3 scripts/domain_okf_frontmatter.py            # write frontmatter
    python3 scripts/domain_okf_frontmatter.py --check    # CI: fail if a guide lacks type
    python3 scripts/domain_okf_frontmatter.py --dry-run  # report without writing
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adr_okf_frontmatter import (  # noqa: E402
    delink,
    existing_keys,
    frontmatter_problem,
    render_frontmatter,
    split_frontmatter,
)


def scalar(value: str) -> str:
    """One `key: value` line, quoted the way YAML needs it.

    Descriptions carry text like `Normative source: AGENT_GUIDE.md` and
    `domain:resource:action`; writing those raw produced six blocks no parser reads.
    """
    return yaml.safe_dump(value, allow_unicode=True, default_flow_style=None, width=10_000).strip()

REPO = Path(__file__).resolve().parents[1]
DOMAINS = REPO / "domains"

# Filename carries the kind of document. Values are descriptive rather than registered:
# OKF has no type registry, and consumers must tolerate types they do not know.
TYPES = {
    "AGENT_GUIDE.md": "Agent Guide",
    "BACKEND_CONTRACT_GUIDE.md": "Backend Contract",
    "BACKEND_API_REFERENCE.generated.md": "API Reference",
    "DOMAIN_NOTES.md": "Domain Notes",
    "STORY_VALIDATION_CHECKLIST.md": "Checklist",
    "PERMISSION_TAXONOMY.md": "Permission Taxonomy",
    "CROSS_DOMAIN_INTEGRATION_CONTRACTS.md": "Integration Contract",
    "CROSS_DOMAIN_INTEGRATION_CONTRACT.md": "Integration Contract",
    "WORKORDER_STATE_MACHINE.md": "State Machine",
    "CUSTOMER_APPROVAL_WORKFLOW.md": "Workflow",
    "CHANGE_REQUEST_WORKFLOW.md": "Workflow",
    "ERROR_CODES.md": "Error Catalog",
    "POSTING_RULES_SCHEMA.md": "Schema",
    "DIMENSION_SCHEMA.md": "Schema",
    "DOMAIN_MODEL.md": "Domain Model",
    "UI_UX_PATTERNS_PLAN.md": "Plan",
    "accounting.md": "Reference Notes",
}

# The keys this script writes. A block containing only these was authored here and
# may be rebuilt; anything else is curated and is only ever added to.
SCRIPT_OWNED_KEYS = {"type", "title", "description", "domain", "tags"}

DOMAIN_TITLES = {
    "crm": "CRM",
    "shopmgmt": "Shop Management",
    "workexec": "Work Execution",
    "nlti": "NLTI",
}

# Bold header metadata ("**Audience:** ...") reads like prose to a line scanner.
HEADER_META = re.compile(r"^\*{0,2}(Document Type|Audience|Last Updated|OpenAPI Source|Do Not Edit|Status|Domain|Owner)\b", re.I)


def domain_title(domain: str) -> str:
    return DOMAIN_TITLES.get(domain, domain.replace("-", " ").title())


def first_prose(body: str) -> str:
    """First real sentence, skipping headings, tables and bold header metadata."""
    for raw in body.splitlines():
        if raw.startswith(("  ", "\t")):
            continue
        line = re.sub(r"^[-*]\s*", "", raw.strip())
        label = re.match(r"^\*\*(.{2,40}?)[.:]?\*\*:?\s*(.+)$", line)
        if label:
            line = label.group(2)
        line = re.sub(r"\s+", " ", delink(line)).strip()
        if HEADER_META.match(line) or line.startswith(("#", "|", ">", "```", "---", "!", "*")):
            continue
        if len(line) >= 45:
            return line[:197] + "..." if len(line) > 200 else line
    return ""


def describe(path: Path, domain: str, kind: str, body: str) -> str:
    if path.name == "BACKEND_API_REFERENCE.generated.md":
        # Generated from the OpenAPI spec; its own prose is a metadata header.
        return f"Generated API reference for the {domain_title(domain)} domain, rendered from the module's OpenAPI spec."
    return first_prose(body) or f"{kind} for the {domain_title(domain)} domain."


def build(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    frontmatter, body = split_frontmatter(text)
    keys = existing_keys(frontmatter)
    domain = path.parent.parent.name
    kind = TYPES.get(path.name, "Domain Document")

    tags = ["domain", domain, kind.lower().replace(" ", "-")]

    # A block this script wrote under the old unquoted renderer can be unparseable
    # (`description: Normative source: AGENT_GUIDE.md`). Inserting keys into it would
    # leave it broken, so it is rebuilt through the renderer instead. Only blocks whose
    # keys are all ones this script owns qualify: a curated block is never rebuilt.
    if frontmatter and frontmatter_problem(frontmatter) and set(keys) <= SCRIPT_OWNED_KEYS:
        frontmatter, keys = "", {}

    if not frontmatter:
        fields = {
            "type": kind,
            "title": keys.get("title") or f"{domain_title(domain)} {kind}",
            "description": describe(path, domain, kind, body),
            "domain": domain,
            "tags": tags,
        }
        return render_frontmatter(fields) + "\n" + body.lstrip("\n")

    # Insert into the existing block rather than re-rendering it. A decode-and-dump
    # round trip rewrites hand-written values — `last_verified_utc: 2026-02-24T14:23:11Z`
    # comes back as `2026-02-24 14:23:11+00:00` — and those 14 contract guides are
    # curated. Only the values added here are rendered, and they are quoted properly.
    existing_lines = frontmatter.splitlines()
    additions = []
    if "type" not in keys:
        existing_lines.insert(0, f"type: {scalar(kind)}")
    if "title" not in keys:
        additions.append(f"title: {scalar(f'{domain_title(domain)} {kind}')}")
    if "description" not in keys:
        additions.append(f"description: {scalar(describe(path, domain, kind, body))}")
    if "domain" not in keys:
        additions.append(f"domain: {scalar(domain)}")
    if "tags" not in keys:
        additions.append(f"tags: [{', '.join(tags)}]")
    block = "\n".join(["---", *existing_lines, *additions, "---"])
    return block + "\n" + body


def guides() -> list[Path]:
    return sorted(DOMAINS.rglob(".business-rules/*.md"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="exit non-zero if a guide has no type")
    parser.add_argument("--dry-run", action="store_true", help="report changes without writing")
    args = parser.parse_args()

    changed, offenders = [], []
    for path in guides():
        current = path.read_text(encoding="utf-8")
        if args.check:
            frontmatter, _ = split_frontmatter(current)
            problem = frontmatter_problem(frontmatter)
            if problem:
                offenders.append(f"{path.relative_to(REPO)}: {problem}")
            continue
        wanted = build(path)
        if wanted != current:
            changed.append(str(path.relative_to(REPO)))
            if not args.dry_run:
                path.write_text(wanted, encoding="utf-8")

    if args.check:
        print(f"checked {len(guides())} guides; non-conformant: {len(offenders)}")
        for name in offenders:
            print(f"  {name}")
        return 1 if offenders else 0

    verb = "would update" if args.dry_run else "updated"
    print(f"{verb} {len(changed)} of {len(guides())} guides")
    for name in changed[:10]:
        print(f"  {name}")
    if len(changed) > 10:
        print(f"  ... and {len(changed) - 10} more")
    return 0


if __name__ == "__main__":
    sys.exit(main())
