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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adr_okf_frontmatter import delink, existing_keys, split_frontmatter  # noqa: E402

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

    if not frontmatter:
        lines = [
            "---",
            f"type: {kind}",
            f"title: {keys.get('title') or f'{domain_title(domain)} {kind}'}",
            f"description: {describe(path, domain, kind, body)}",
            f"domain: {domain}",
            f"tags: [domain, {domain}, {kind.lower().replace(' ', '-')}]",
            "---",
        ]
        return "\n".join(lines) + "\n\n" + body.lstrip("\n")

    # Insert into the existing block so nested values (traceability:) stay intact.
    existing_lines = frontmatter.splitlines()
    additions = []
    if "type" not in keys:
        existing_lines.insert(0, f"type: {kind}")
    if "title" not in keys:
        additions.append(f"title: {domain_title(domain)} {kind}")
    if "description" not in keys:
        additions.append(f"description: {describe(path, domain, kind, body)}")
    if "domain" not in keys:
        additions.append(f"domain: {domain}")
    if "tags" not in keys:
        additions.append(f"tags: [domain, {domain}, {kind.lower().replace(' ', '-')}]")
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
            if not frontmatter or not existing_keys(frontmatter).get("type"):
                offenders.append(str(path.relative_to(REPO)))
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
