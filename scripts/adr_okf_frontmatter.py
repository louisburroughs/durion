#!/usr/bin/env python3
"""Give every ADR an OKF-conformant YAML frontmatter block.

OKF v0.2 (https://github.com/GoogleCloudPlatform/open-knowledge-format) asks one
thing of a concept file: parseable YAML frontmatter carrying a non-empty `type`.
This script derives the rest of the recommended keys from what each ADR already
states in its body, so the records stay the single source and nothing is invented:

    type          always "ADR"
    title         existing frontmatter title, else the ADR's own heading
    description   first real sentence of Context (else Decision, else the title)
    status        OKF lifecycle: accepted -> stable, proposed/pending -> draft,
                  superseded/deprecated -> deprecated. Omitted when the ADR
                  records no status, rather than inventing one.
    adr_status    the ADR's own vocabulary, kept verbatim (OKF keeps unknown keys)
    created       **Date:** / ## Status date / existing frontmatter, else the
                  date git first saw the file
    supersedes / superseded_by   ADR ids, when the body states the relation
    related       other ADR ids the body links to
    tags          [adr] plus domain and topic tags derived from name and title

Hand-written `title`, `description`, `created` and `supersedes` are kept as they
stand. `status`, `adr_status`, `related` and `tags` are derived on every run: they
restate what the record says, and a value left behind by an earlier run must not
outlive the rule that produced it.

The script is idempotent — running it twice produces the same bytes.

Usage:
    python3 scripts/adr_okf_frontmatter.py            # write frontmatter
    python3 scripts/adr_okf_frontmatter.py --check    # CI: fail if any ADR is non-conformant
    python3 scripts/adr_okf_frontmatter.py --dry-run  # print what would change
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[1]
ADR_DIR = REPO / "docs" / "adr"
TEMPLATE = "TEMPLATE.adr.md"

# ADR status vocabulary -> OKF lifecycle status (SPEC.md §Lifecycle).
OKF_STATUS = {
    "accepted": "stable",
    "proposed": "draft",
    "pending": "draft",
    "superseded": "deprecated",
    "deprecated": "deprecated",
    "rejected": "deprecated",
}

# Topic tags keyed on words in the file name or title. Deterministic and small on
# purpose: a tag nobody can predict is worse than no tag.
TOPIC_TAGS = {
    "accessibility": "accessibility",
    "accounting": "accounting",
    "angular": "frontend",
    "api": "api-contract",
    "audit": "audit",
    "billing": "billing",
    "catalog": "product",
    "crm": "crm",
    "event": "events",
    "flyway": "database",
    "frontend": "frontend",
    "gateway": "gateway",
    "inventory": "inventory",
    "jpa": "database",
    "location": "location",
    "multitenancy": "multitenancy",
    "openapi": "api-contract",
    "order": "order",
    "people": "people",
    "permission": "rbac",
    "permissions": "rbac",
    "platform": "platform",
    "pricing": "pricing",
    "roles": "rbac",
    "security": "security",
    "shopmgmt": "shopmgmt",
    "tax": "accounting",
    "tenant": "multitenancy",
    "tenantid": "multitenancy",
    "uuid": "identifiers",
    "vehicle": "crm",
    "warranty": "warranty",
    "workexec": "workexec",
    "workorder": "workexec",
}

SECTION = re.compile(r"^#{1,4}\s*(Context|Decision|Summary|Problem)\s*$", re.M)

# The vocabulary ADRs in this repo actually use. A word outside it means the ADR
# records no status, which is reported as such rather than guessed at.
ADR_VOCAB = {"accepted", "proposed", "pending", "superseded", "deprecated", "rejected", "draft", "active"}

# Header metadata that reads like prose but describes the record, not the decision.
META_LABEL = re.compile(
    r"^(Context|Status|Date|Deciders|Stakeholders|Affected Issues|Decision Makers|Plan|Supersedes|Superseded)\b",
    re.I,
)

# Box-drawing diagrams: several ADRs draw their architecture, and a line scanner
# cannot tell that art from prose.
ART = re.compile(r"[│┌└├─┐┘┤┬┴┼╔╚╗╝║═]|\+--|--\+")

# A description has to begin a sentence. A line that starts lower-case or mid-token
# is the tail of a wrapped line, not the start of one.
SENTENCE_START = re.compile(r"^[\"'`A-Z]")


# A frontmatter block is the opening fence, keys, and a closing fence — tolerating
# trailing spaces and CRLF on either fence. Anchored at the start of the file and
# non-greedy, so a `---` horizontal rule further down is never mistaken for the
# closing fence: doing that swallows the document's own heading as frontmatter.
FRONTMATTER = re.compile(r"\A---[ \t]*\r?\n(.*?\n)?---[ \t]*\r?\n", re.S)


def split_frontmatter(text: str) -> tuple[str, str]:
    """Return (frontmatter, body). Frontmatter is '' when the file has none."""
    match = FRONTMATTER.match(text)
    if not match:
        return "", text
    return (match.group(1) or "").rstrip("\n"), text[match.end() :]


def existing_keys(frontmatter: str) -> dict[str, str]:
    keys: dict[str, str] = {}
    for line in frontmatter.splitlines():
        match = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$", line)
        if match:
            keys[match.group(1)] = match.group(2).strip()
    return keys


def delink(text: str) -> str:
    """Markdown link -> its label, and drop bold/inline-code markers."""
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    return text.replace("`", "")


def clean_status_word(raw: str) -> str:
    """'✅ Accepted' / 'ACCEPTED - 2026-01-12' -> 'accepted'."""
    words = re.findall(r"[A-Za-z]+", raw)
    return words[0].lower() if words else ""


def derive_title(number: str, frontmatter_title: str, body: str) -> str:
    if frontmatter_title:
        return frontmatter_title.strip("'\"")
    heading = next((l.strip() for l in body.splitlines() if l.strip().startswith("#")), "")
    heading = re.sub(r"^#+\s*", "", heading)
    # The corpus spells its headings five ways; strip whichever prefix is present.
    for prefix in (
        r"^ADR-[A-Z]+-\d+\s*:\s*",      # ADR-CRM-001: Title
        r"^ADR[-\s]*\d+\s*[:\-]\s*",    # ADR-0062: Title / ADR 0001: Title
        r"^ADR\s*:\s*\d+\s*-\s*",       # ADR: 0006 - Title
        r"^ADR\s*:\s*",                 # ADR: Title
        r"^\d+\s*-\s*",                 # 0014 - Title
    ):
        heading = re.sub(prefix, "", heading)
    return f"ADR-{number}: {heading.strip()}" if heading.strip() else f"ADR-{number}"


def derive_status(frontmatter: dict[str, str], body: str) -> str:
    # adr_status holds the record's own word; `status` is the OKF lifecycle value this
    # script derives from it, so reading `status` back would turn 'stable' into the ADR
    # status on the next run and churn every file.
    recorded = clean_status_word(frontmatter.get("adr_status", "") or frontmatter.get("status", ""))
    if recorded in ADR_VOCAB:
        return recorded
    # **Status:** ACCEPTED   |   **Status**: Accepted   |   ## Status\n\n**ACCEPTED** - date
    for pattern in (
        r"\*\*Status:?\*\*:?\s*([^\n]+)",
        r"^#{1,4}\s*Status\s*$\s*\n+\s*([^\n]+)",
        r"^\s*Status\s*:\s*([^\n]+)",
    ):
        match = re.search(pattern, body, re.M)
        if match:
            word = clean_status_word(delink(match.group(1)))
            if word in ADR_VOCAB:
                return word
    return ""


def derive_created(path: Path, frontmatter: dict[str, str], body: str) -> str:
    if frontmatter.get("created"):
        return frontmatter["created"].strip("'\"")
    for pattern in (r"\*\*Date:?\*\*:?\s*(\d{4}-\d{2}-\d{2})", r"\*\*[A-Z]+\*\*\s*-\s*(\d{4}-\d{2}-\d{2})"):
        match = re.search(pattern, body)
        if match:
            return match.group(1)
    # Last resort: the day the repository first saw the file. Stated, not guessed.
    result = subprocess.run(
        ["git", "log", "--diff-filter=A", "--format=%ad", "--date=short", "-1", "--", str(path)],
        cwd=REPO,
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout.strip()


def sentence_trim(text: str, limit: int = 220) -> str:
    """Cut on a sentence boundary when there is one, so a description ends cleanly."""
    if len(text) <= limit:
        return text
    cut = text[:limit]
    stop = cut.rfind(". ")
    return cut[: stop + 1] if stop > 80 else cut.rstrip() + "..."


def derive_description(body: str, title: str) -> str:
    """First real sentence of Context, else Decision, else the title.

    Context sections are written as bold-labelled bullets ('- **Current state.** ...')
    that wrap onto indented continuation lines, so a paragraph is reassembled before
    it is judged. Header metadata ('**Context:** #207 ...') describes the record
    rather than the decision and is skipped.
    """
    sections = [(m.group(1), m.end()) for m in SECTION.finditer(body)]
    ordered = sorted(sections, key=lambda s: {"Context": 0, "Problem": 1, "Decision": 2, "Summary": 3}.get(s[0], 4))
    for chunk in [body[start:] for _, start in ordered] + [body]:
        lines = chunk.splitlines()[:120]
        index = 0
        while index < len(lines):
            raw = lines[index]
            stripped = raw.strip()
            if not stripped or stripped.startswith(("#", "|", ">", "```", "---", "!")) or raw.startswith(("  ", "\t")):
                index += 1
                continue
            # Reassemble the wrapped remainder of this bullet or paragraph.
            parts, cursor = [stripped], index + 1
            while cursor < len(lines):
                nxt = lines[cursor]
                if not nxt.strip() or nxt.strip().startswith(("#", "|", ">", "```", "-", "*")):
                    break
                parts.append(nxt.strip())
                cursor += 1
            text = re.sub(r"^[-*]\s*", "", " ".join(parts))
            label = re.match(r"^\*\*(.{2,40}?)[.:]?\*\*:?\s*(.+)$", text)
            if label:
                text = label.group(2)
            text = re.sub(r"\s+", " ", delink(text)).strip()
            if (
                len(text) >= 45
                and not META_LABEL.match(text)
                and not text.startswith(("#", "*"))
                and not ART.search(text)  # diagrams read as prose to a line scanner
                and SENTENCE_START.match(text)  # a fragment of a wrapped line is not a description
            ):
                return sentence_trim(text)
            index = max(cursor, index + 1)
    # Older ADRs state their question on a **Context:** header line instead of in a
    # section. That line is the record's own summary, so it beats echoing the title.
    header = re.search(r"\*\*Context:?\*\*:?\s*(.+)", body)
    if header:
        text = re.sub(r"\s+", " ", delink(header.group(1))).strip()
        if len(text) >= 40 and not ART.search(text):
            return sentence_trim(text)
    return title


def derive_relations(number: str, body: str, adr_status: str) -> tuple[str, str, list[str]]:
    """Supersede edges, read conservatively.

    Prose saying 'superseded by ADR-XXXX' often narrows a single claim inside a
    still-live record — ADR-0011 says it about its claim contract while remaining
    ACCEPTED. Only an explicit metadata line, or a record whose own status is
    terminal, establishes that the ADR itself was superseded; anything looser stays
    an ordinary `related` link.
    """
    supersedes = superseded_by = ""
    match = re.search(r"\*\*Supersedes:?\*\*:?\s*\[?ADR-(\d{4})", body)
    if match:
        supersedes = f"ADR-{match.group(1)}"
    explicit = re.search(r"\*\*Superseded[- ]by:?\*\*:?\s*\[?ADR-(\d{4})", body)
    prose = re.search(r"[Ss]uperseded by \[?ADR-(\d{4})", body)
    if explicit:
        superseded_by = f"ADR-{explicit.group(1)}"
    elif prose and adr_status in {"superseded", "deprecated"}:
        superseded_by = f"ADR-{prose.group(1)}"
    related = sorted({f"ADR-{n}" for n in re.findall(r"ADR-(\d{4})", body) if n != number})
    for relation in (supersedes, superseded_by):
        if relation in related:
            related.remove(relation)
    return supersedes, superseded_by, related


def derive_tags(path: Path, title: str) -> list[str]:
    words = set(re.findall(r"[a-z]+", f"{path.name} {title}".lower()))
    tags = sorted({TOPIC_TAGS[w] for w in words if w in TOPIC_TAGS})
    return ["adr", *tags]


def parse_frontmatter(block: str) -> dict[str, object]:
    """Decode a frontmatter block into real values.

    Reading raw text and re-emitting it escapes an already-escaped value a second
    time: ADR-0059 says `workorder''s`, and copying that spelling into another
    single-quoted scalar produced `workorder''''s` for consumers. Decoding first
    and re-quoting on the way out keeps one level of escaping.
    """
    try:
        data = yaml.safe_load(block) if block.strip() else {}
    except yaml.YAMLError:
        return {}
    return data if isinstance(data, dict) else {}


def frontmatter_problem(block: str) -> str:
    """Why this frontmatter is not conformant, or '' when it is.

    OKF asks for a parseable block carrying a non-empty `type`. Checking for the
    key with a regex accepts blocks YAML rejects — six guides shipped with an
    unquoted `Normative source: AGENT_GUIDE.md` description that no parser reads.
    """
    if not block.strip():
        return "missing frontmatter"
    try:
        data = yaml.safe_load(block)
    except yaml.YAMLError as error:
        return f"frontmatter is not parseable YAML ({str(error).splitlines()[0]})"
    if not isinstance(data, dict):
        return "frontmatter is not a mapping"
    if not str(data.get("type", "")).strip():
        return "frontmatter has no non-empty type"
    return ""


def render_frontmatter(fields: dict[str, object]) -> str:
    """Emit a frontmatter block, letting the YAML writer handle quoting.

    Hand-rolled quoting is what produced unparseable blocks: a description
    containing `domain:resource:action` or `Normative source: AGENT_GUIDE.md` needs
    quoting that a "contains a colon" test gets wrong in both directions.
    """
    populated = {k: v for k, v in fields.items() if v not in ("", None, [])}
    if not populated:
        return "---\n---\n"
    # `None` keeps short sequences inline (`tags: [adr, multitenancy]`), which is how
    # frontmatter reads everywhere else here — but it also collapses a one-key mapping
    # to `{okf_version: '0.2'}`, a whole block on one line. Block style for that case.
    flow_style = False if len(populated) == 1 else None
    body = yaml.safe_dump(
        populated,
        sort_keys=False,
        allow_unicode=True,
        default_flow_style=flow_style,
        width=10_000,
    )
    return f"---\n{body}---\n"


def build(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    raw_frontmatter, body = split_frontmatter(text)
    existing = existing_keys(raw_frontmatter)
    decoded = parse_frontmatter(raw_frontmatter)
    number = re.match(r"(\d+)", path.name).group(1) if re.match(r"(\d+)", path.name) else "NNNN"

    title = derive_title(number, existing.get("title", ""), body)
    adr_status = derive_status(existing, body)
    supersedes, superseded_by, related = derive_relations(number, body, adr_status)
    fields: dict[str, object] = {
        "type": "ADR",
        "title": title,
        # A hand-written description is kept; only a missing one is derived.
        "description": str(decoded.get("description") or "").strip() or derive_description(body, title),
        "status": OKF_STATUS.get(adr_status, ""),
        "adr_status": adr_status,
        "created": derive_created(path, existing, body),
        "supersedes": existing.get("supersedes", "") or supersedes,
        # A retained superseded_by faces the same test as a derived one. An earlier run
        # of this script wrote values under a looser rule, and those must not outlive it
        # (ADR-0011 is ACCEPTED and only its claim contract moved to ADR-0040), while a
        # hand-written relation on a terminal record is kept (ADR-0023).
        "superseded_by": superseded_by
        or (
            existing.get("superseded_by", "")
            if adr_status in {"superseded", "deprecated"} or re.search(r"\*\*Superseded[- ]by:?\*\*", body)
            else ""
        ),
        "related": related,
        "tags": derive_tags(path, title),
    }
    return render_frontmatter(fields) + body.lstrip("\n")


def build_template(path: Path) -> str:
    """The template carries the shape so new ADRs are born conformant."""
    _, body = split_frontmatter(path.read_text(encoding="utf-8"))
    fields = {
        "type": "ADR",
        "title": "ADR-NNNN: [Brief Decision Title]",
        "description": "[One sentence stating the decision and why it was needed.]",
        "status": "draft",
        "adr_status": "pending",
        "created": "YYYY-MM-DD",
        "tags": ["adr"],
    }
    return render_frontmatter(fields) + body.lstrip("\n")


def adr_files() -> list[Path]:
    return sorted(ADR_DIR.glob("*.adr.md"))


README = ADR_DIR / "README.md"
TABLE_START = "<!-- adr-table:start -->"
TABLE_END = "<!-- adr-table:end -->"


def table_row(path: Path) -> tuple[str, str, str, str]:
    """One Current-ADRs row, read from the record's own frontmatter."""
    frontmatter, _ = split_frontmatter(path.read_text(encoding="utf-8"))
    keys = existing_keys(frontmatter)
    number = re.match(r"(\d+)", path.name).group(1)
    title = keys.get("title", "").strip("'\"")
    title = re.sub(rf"^ADR-{number}:\s*", "", title)  # the number is already a column
    status = keys.get("adr_status", "").upper()
    # The supersede relation is the reason a status reads the way it does, so it
    # travels with it rather than needing a separate column.
    if keys.get("superseded_by"):
        successor = keys["superseded_by"].replace("ADR-", "")
        status = f"SUPERSEDED BY {successor}" if status in ("", "SUPERSEDED") else f"{status} (superseded by {successor})"
    elif keys.get("supersedes"):
        status = f"{status} (supersedes {keys['supersedes'].replace('ADR-', '')})".strip()
    return number, title, status or "—", keys.get("created", "—")


def render_table() -> str:
    rows = [table_row(p) for p in adr_files() if p.name != TEMPLATE]
    lines = [
        TABLE_START,
        "<!-- Generated by scripts/adr_okf_frontmatter.py from each ADR's frontmatter. Do not edit by hand. -->",
        "",
        "| Number | Title | Status | Date |",
        "| ------ | ----- | ------ | ---- |",
    ]
    lines += [f"| {number} | {title} | {status} | {created} |" for number, title, status, created in rows]
    lines += ["", TABLE_END]
    return "\n".join(lines)


def readme_with_table(text: str) -> str:
    """Replace the marked block, leaving every hand-written section alone."""
    start, end = text.find(TABLE_START), text.find(TABLE_END)
    if start == -1 or end == -1:
        raise SystemExit(f"{README}: missing {TABLE_START} / {TABLE_END} markers around the Current ADRs table")
    return text[:start] + render_table() + text[end + len(TABLE_END) :]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="exit non-zero if any ADR is not conformant")
    parser.add_argument("--dry-run", action="store_true", help="report changes without writing")
    args = parser.parse_args()

    changed, offenders = [], []
    for path in adr_files():
        wanted = build_template(path) if path.name == TEMPLATE else build(path)
        current = path.read_text(encoding="utf-8")
        if args.check:
            frontmatter, _ = split_frontmatter(current)
            problem = frontmatter_problem(frontmatter)
            if problem:
                offenders.append(f"{path.name}: {problem}")
            continue
        if wanted != current:
            changed.append(path.name)
            if not args.dry_run:
                path.write_text(wanted, encoding="utf-8")

    readme_text = README.read_text(encoding="utf-8")
    readme_wanted = readme_with_table(readme_text)
    readme_stale = readme_wanted != readme_text

    if args.check:
        if readme_stale:
            offenders.append("README.md (Current ADRs table is stale; re-run this script)")
        print(f"checked {len(adr_files())} ADRs; non-conformant: {len(offenders)}")
        for name in offenders:
            print(f"  {name}")
        return 1 if offenders else 0

    if readme_stale:
        changed.append("README.md (Current ADRs table)")
        if not args.dry_run:
            README.write_text(readme_wanted, encoding="utf-8")

    verb = "would update" if args.dry_run else "updated"
    print(f"{verb} {len(changed)} of {len(adr_files())} ADRs")
    for name in changed[:10]:
        print(f"  {name}")
    if len(changed) > 10:
        print(f"  ... and {len(changed) - 10} more")
    return 0


if __name__ == "__main__":
    sys.exit(main())
