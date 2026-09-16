#!/usr/bin/env python3
"""Generate the OKF knowledge catalog for the Durion workspace.

The catalog is a build artifact, not a source: every note here is derived from a
canonical document in `durion` or a module in `durion-positivity-backend`. Re-run
it whenever those change; never hand-edit `knowledge-catalog/`.

Format is OKF v0.2 (https://github.com/GoogleCloudPlatform/open-knowledge-format):
a directory of markdown files whose frontmatter always carries a non-empty `type`,
with `index.md` and `log.md` reserved for directory listings and history, and
`okf_version` declared only in the bundle-root `index.md`.

Each note carries what an agent needs to decide whether to open the canonical
document — a real description, lifecycle status, and links to the neighbouring
concepts — rather than a bare pointer.

Idempotent: the `generated.at` stamp follows the source document's last commit,
so re-running without upstream changes rewrites nothing.

Usage:
    python3 scripts/generate-knowledge-catalog.py            # regenerate the bundle
    python3 scripts/generate-knowledge-catalog.py --check    # CI: verify OKF conformance
    python3 scripts/generate-knowledge-catalog.py --dry-run  # report without writing

Override the backend checkout with DURION_BACKEND=/path/to/durion-positivity-backend.

A domain index with frontmatter `type: Domain Guide` opts into recursive indexing
of visible Markdown documents. Hidden business-rule guides keep their own section;
other hidden artifacts are reached through the canonical domain index.
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adr_okf_frontmatter import (  # noqa: E402  (path set above)
    TEMPLATE,
    delink,
    derive_description,
    derive_title,
    existing_keys,
    frontmatter_problem,
    parse_frontmatter,
    render_frontmatter,
    split_frontmatter,
)

REPO = Path(__file__).resolve().parents[1]
BACKEND = Path(os.environ.get("DURION_BACKEND", REPO.parent / "durion-positivity-backend"))
CATALOG = REPO / "knowledge-catalog"
GITHUB = "https://github.com/louisburroughs"
DURION_BRANCH = "master"
OKF_VERSION = "0.2"
RESERVED = {"index.md", "log.md"}

# Domains whose folder carries no prose to quote. Stated here rather than left blank,
# and short enough that a reader can tell a human wrote them.
DOMAIN_FALLBACK = {
    "general": "Workspace bucket for cross-domain UI artifacts that no business domain owns.",
    "warranty": "Warranty claim lifecycle: eligibility, settlement, reimbursement, and part returns.",
}
MODULE_FALLBACK = {
    "pos-agent-framework": "Placeholder module directory; no build file or README yet.",
}


def run_git(*args: str, cwd: Path) -> str:
    result = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, check=False)
    return result.stdout.strip()


def last_touched(path: Path, cwd: Path) -> str:
    """ISO timestamp of the source's last commit — stable across reruns."""
    stamp = run_git("log", "--format=%cI", "-1", "--", str(path), cwd=cwd)
    return stamp or dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def first_prose(path: Path, minimum: int = 45) -> str:
    """First real sentence in a document, skipping its frontmatter and markup."""
    if not path.exists():
        return ""
    _, body = split_frontmatter(path.read_text(encoding="utf-8", errors="replace"))
    for raw in body.splitlines():
        if raw.startswith(("  ", "\t")):
            continue
        line = re.sub(r"^[-*]\s*", "", raw.strip())
        line = delink(line)
        line = re.sub(r"\s+", " ", line).strip()
        if len(line) >= minimum and not line.startswith(("#", "|", ">", "```", "---", "!")):
            return line[:197] + "..." if len(line) > 200 else line
    return ""


def render(fields: dict[str, object], body: str) -> str:
    """Frontmatter plus body, with quoting left to the shared YAML renderer."""
    return render_frontmatter(fields) + "\n" + body.rstrip("\n") + "\n"


def write(path: Path, content: str, state: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == content:
        state["unchanged"] += 1
        return
    state["written"].append(str(path.relative_to(REPO)))
    if not state["dry_run"]:
        path.write_text(content, encoding="utf-8")


# ── source inventories ────────────────────────────────────────────────────────


def module_mentions() -> tuple[dict[str, list[str]], dict[str, str]]:
    """Which modules each domain talks about, and each module's owning domain.

    Derived from how often a domain's own docs name a module. It is a reading of
    the prose, not a contract: modules named fewer than five times are ignored,
    and a module with no clear majority domain gets none.
    """
    per_domain: dict[str, collections.Counter] = {}
    for domain in sorted(p for p in (REPO / "domains").iterdir() if p.is_dir()):
        counts: collections.Counter = collections.Counter()
        for doc in domain.rglob("*.md"):
            relative = doc.relative_to(domain)
            if "archive" in relative.parts:
                continue
            for name in re.findall(r"\bpos-[a-z-]+\b", doc.read_text(encoding="utf-8", errors="replace")):
                counts[name] += 1
        per_domain[domain.name] = counts
    domain_modules = {d: [m for m, c in c_.most_common(4) if c >= 5] for d, c_ in per_domain.items()}
    owner: dict[str, str] = {}
    for module in {m for counts in per_domain.values() for m in counts}:
        ranked = sorted(((counts[module], d) for d, counts in per_domain.items()), reverse=True)
        if ranked and ranked[0][0] >= 5:
            owner[module] = ranked[0][1]
    return domain_modules, owner


def adr_records() -> list[dict]:
    records = []
    for path in sorted((REPO / "docs" / "adr").glob("*.adr.md")):
        if path.name == TEMPLATE:  # the template is a form, not a decision
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        frontmatter, body = split_frontmatter(text)
        # Decoded, not raw: copying a raw spelling re-escapes it, so ADR-0059's
        # `workorder''s` reached consumers as `workorder''''s`.
        keys = {k: ("" if v is None else v) for k, v in parse_frontmatter(frontmatter).items()}
        number = re.match(r"(\d+)", path.name).group(1)
        title = str(keys.get("title", "")) or derive_title(number, "", body)
        records.append(
            {
                "number": number,
                "slug": path.stem.replace(".adr", ""),
                "path": path,
                "title": title,
                "description": str(keys.get("description", "")) or derive_description(body, title),
                "status": str(keys.get("status", "")),
                "adr_status": str(keys.get("adr_status", "")),
                "created": str(keys.get("created", "")),
                "supersedes": str(keys.get("supersedes", "")),
                "superseded_by": str(keys.get("superseded_by", "")),
                "related": list(keys.get("related") or []),
                "tags": list(keys.get("tags") or []) or ["adr"],
            }
        )
    return records


def module_records() -> list[dict]:
    records = []
    for module in sorted(p for p in BACKEND.glob("pos-*") if p.is_dir()):
        pom = module / "pom.xml"
        description = ""
        if pom.exists():
            match = re.search(r"<description>(.*?)</description>", pom.read_text(encoding="utf-8", errors="replace"), re.S)
            if match:
                description = " ".join(match.group(1).split())
        description = description or first_prose(module / "README.md", minimum=25) or MODULE_FALLBACK.get(module.name, "")
        deployable = (module / "Dockerfile").exists() or (module / "src/main/resources/application.yml").exists()
        records.append(
            {
                "name": module.name,
                "path": module,
                "description": description or f"{module.name} module.",
                "kind": "Service" if deployable else "Library",
                "openapi": (module / "openapi.yaml").exists(),
            }
        )
    return records


def has_documentation_index(domain: Path) -> bool:
    index = domain / "index.md"
    if not index.exists():
        return False
    keys = parse_frontmatter(split_frontmatter(index.read_text(encoding="utf-8"))[0])
    return keys.get("type") == "Domain Guide"


def domain_records(domain_modules: dict[str, list[str]]) -> list[dict]:
    records = []
    for domain in sorted(p for p in (REPO / "domains").iterdir() if p.is_dir()):
        rules = domain / ".business-rules"
        description = (
            first_prose(rules / "AGENT_GUIDE.md")
            or first_prose(rules / "BACKEND_CONTRACT_GUIDE.md")
            or DOMAIN_FALLBACK.get(domain.name, "")
        )
        # (filename, type) so the note can say what each guide is, read from the
        # guide's own frontmatter rather than guessed at here.
        guides = []
        for guide in sorted(rules.glob("*.md")) if rules.exists() else []:
            guide_keys = parse_frontmatter(split_frontmatter(guide.read_text(encoding="utf-8", errors="replace"))[0])
            guides.append((guide.name, str(guide_keys.get("type") or "Domain Document")))
        documents = []
        documentation_index = has_documentation_index(domain)
        if documentation_index:
            for doc in sorted(domain.rglob("*.md")):
                relative = doc.relative_to(domain)
                if doc == domain / "index.md" or any(part.startswith(".") for part in relative.parts):
                    continue
                frontmatter, body = split_frontmatter(doc.read_text(encoding="utf-8", errors="replace"))
                keys = parse_frontmatter(frontmatter)
                heading = re.search(r"^#\s+(.+)$", body, re.M)
                title = str(keys.get("title") or (heading.group(1) if heading else doc.stem))
                details = " · ".join(str(keys[key]) for key in ("type", "status") if keys.get(key))
                documents.append((relative.as_posix(), title, details))
        records.append(
            {
                "name": domain.name,
                "path": domain,
                "description": description or f"The {domain.name} domain.",
                "guides": guides,
                "documentation_index": documentation_index,
                "documents": documents,
                "modules": domain_modules.get(domain.name, []),
                "docs": len(list(domain.rglob("*.md"))),
            }
        )
    return records


# ── note rendering ────────────────────────────────────────────────────────────


def adr_note(record: dict, owner_by_module: dict[str, str]) -> str:
    resource = f"{GITHUB}/durion/blob/{DURION_BRANCH}/docs/adr/{record['path'].name}"
    sections = [f"[Canonical ADR]({resource}) — `docs/adr/{record['path'].name}`"]

    facts = []
    if record["adr_status"]:
        state = record["adr_status"].capitalize()
        dated = f" since {record['created']}" if record["created"] else ""
        facts.append(f"**Status:** {state}{dated}")
    if record["supersedes"]:
        facts.append(f"**Supersedes:** [{record['supersedes']}](../adr/{slug_for(record['supersedes'])}.md)")
    if record["superseded_by"]:
        facts.append(f"**Superseded by:** [{record['superseded_by']}](../adr/{slug_for(record['superseded_by'])}.md)")
    if facts:
        sections.append("\n".join(facts))

    if record["related"]:
        # One link per line: a single joined line reached 380 characters on ADR-0011.
        links = "\n".join(f"* [{r}](../adr/{slug_for(r)}.md)" for r in record["related"][:8])
        sections.append(f"**Related:**\n\n{links}")
    return "\n\n".join(sections)


SLUGS: dict[str, str] = {}


def slug_for(adr_id: str) -> str:
    return SLUGS.get(adr_id.replace("ADR-", ""), adr_id.lower())


def domain_note(record: dict) -> str:
    resource = f"{GITHUB}/durion/blob/{DURION_BRANCH}/domains/{record['name']}"
    # Sections are joined, not hand-spaced: a domain with no modules used to emit two
    # blank lines in a row.
    sections = [f"[Domain folder]({resource}) — `domains/{record['name']}/` ({record['docs']} documents)"]
    if record["documentation_index"]:
        sections.append(f"[Canonical documentation index]({resource}/index.md) — authority, current guidance, and historical records")
    if record["modules"]:
        links = [f"[{m}](../backend/{m}.md)" for m in record["modules"]]
        sections.append(soft_wrap("**Implemented by:**", links))
    if record["guides"]:
        # Every guide, linked and typed: these are the documents an agent opens next,
        # so a bare filename list made the note a dead end.
        base = f"{resource}/.business-rules"
        listed = "\n".join(wrapped_item(f"* [{name}]({base}/{name})", kind) for name, kind in record["guides"])
        sections.append(f"**Business rules:**\n\n{listed}")
    if record["documents"]:
        items, references = [], []
        for number, (path, title, details) in enumerate(record["documents"], start=1):
            url = f"{resource}/{path}"
            prefix = f"* [{title}]({url})"
            if len(prefix) + (3 if details else 0) > MAX_LINE:
                label = f"document-{number}"
                prefix = f"* [{title}][{label}]"
                references.append(f"[{label}]: {url}")
            items.append(wrapped_item(prefix, details))
        sections.append("**Documentation:**\n\n" + "\n".join(items))
        if references:
            sections.append("\n".join(references))
    return "\n\n".join(sections)


def module_note(record: dict, owner: str) -> str:
    resource = f"{GITHUB}/durion-positivity-backend/blob/main/{record['name']}"
    lines = [f"[Module directory]({resource}) — `{record['name']}/`", "", f"**Kind:** {record['kind']}"]
    if owner:
        lines.append(f"**Domain:** [{owner}](../domains/{owner}.md)")
        if has_documentation_index(REPO / "domains" / owner):
            lines.append(f"**Documentation:** [Canonical {owner} index]({GITHUB}/durion/blob/{DURION_BRANCH}/domains/{owner}/index.md)")
    if record["openapi"]:
        lines.append(f"**API contract:** [`openapi.yaml`]({resource}/openapi.yaml)")
    return "\n".join(lines)


MAX_LINE = 175  # .markdownlint.jsonc MD013


def wrapped_item(prefix: str, trailer: str = "") -> str:
    """A list item whose text soft-wraps instead of running past the line limit.

    Markdown treats an indented continuation as part of the same item, so the text
    stays whole and renders identically; only the source line breaks.
    """
    if not trailer:
        return prefix
    if len(prefix) + 3 + len(trailer) <= MAX_LINE:
        return f"{prefix} — {trailer}"
    lines, current = [f"{prefix} —"], "  "
    for word in trailer.split():
        candidate = f"{current} {word}".replace("   ", "  ", 1) if current.strip() else f"  {word}"
        if len(candidate) > MAX_LINE:
            lines.append(current.rstrip())
            current = f"  {word}"
        else:
            current = candidate
    lines.append(current.rstrip())
    return "\n".join(lines)


def soft_wrap(prefix: str, items: list[str], limit: int = MAX_LINE) -> str:
    """A comma-separated run broken across source lines at the line limit.

    Markdown renders a soft break as a space, so the paragraph reads identically and
    only the source obeys MD013 — which a domain implemented by four modules did not,
    once the links became relative.
    """
    lines, current = [], prefix
    for index, item in enumerate(items):
        piece = item + ("," if index < len(items) - 1 else "")
        candidate = f"{current} {piece}" if current else piece
        if current and len(candidate) > limit:
            lines.append(current)
            current = piece
        else:
            current = candidate
    lines.append(current)
    return "\n".join(lines)


def index_note(heading: str, rows: list[tuple[str, str, str]]) -> str:
    lines = [f"# {heading}", ""]
    for name, link, description in rows:
        lines.append(wrapped_item(f"* [{name}]({link})", description))
    return "\n".join(lines) + "\n"


def trim(text: str, limit: int = 120) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


# ── conformance ───────────────────────────────────────────────────────────────


def check_bundle() -> list[str]:
    problems = []
    for path in sorted(CATALOG.rglob("*.md")):
        relative = path.relative_to(CATALOG)
        frontmatter, _ = split_frontmatter(path.read_text(encoding="utf-8", errors="replace"))
        keys = parse_frontmatter(frontmatter)
        if path.name in RESERVED:
            if "okf_version" in keys and relative != Path("index.md"):
                problems.append(f"{relative}: okf_version may only appear in the bundle-root index.md")
            if frontmatter and relative != Path("index.md"):
                problems.append(f"{relative}: reserved file must not carry frontmatter")
            continue
        problem = frontmatter_problem(frontmatter)
        if problem:
            problems.append(f"{relative}: {problem}")
    root = CATALOG / "index.md"
    if not root.exists():
        problems.append("index.md: bundle root index is missing")
    elif "okf_version" not in parse_frontmatter(split_frontmatter(root.read_text(encoding="utf-8"))[0]):
        problems.append("index.md: bundle root must declare okf_version")
    return problems


# ── main ──────────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify OKF conformance of the existing bundle")
    parser.add_argument("--dry-run", action="store_true", help="report what would change without writing")
    args = parser.parse_args()

    if args.check:
        problems = check_bundle()
        total = len([p for p in CATALOG.rglob("*.md") if p.name not in RESERVED])
        print(f"OKF v{OKF_VERSION} conformance: {total} concepts, {len(problems)} problem(s)")
        for problem in problems:
            print(f"  {problem}")
        return 1 if problems else 0

    if not BACKEND.exists():
        print(f"backend checkout not found at {BACKEND}; set DURION_BACKEND", file=sys.stderr)
        return 2

    state = {"written": [], "unchanged": 0, "dry_run": args.dry_run}
    domain_modules, owner_by_module = module_mentions()
    adrs = adr_records()
    SLUGS.update({a["number"]: a["slug"] for a in adrs})
    domains = domain_records(domain_modules)
    modules = module_records()

    for adr in adrs:
        fields = {
            "type": "ADR",
            "title": adr["title"],
            "description": adr["description"],
            "resource": f"{GITHUB}/durion/blob/{DURION_BRANCH}/docs/adr/{adr['path'].name}",
            "path": f"durion/docs/adr/{adr['path'].name}",
            "tags": adr["tags"],
            "status": adr["status"],
            "sources": [f"docs/adr/{adr['path'].name}"],
            "generated": {"by": "script:generate-knowledge-catalog.py", "at": last_touched(adr["path"], REPO)},
        }
        write(CATALOG / "adr" / f"{adr['slug']}.md", render(fields, adr_note(adr, owner_by_module)), state)

    for domain in domains:
        fields = {
            "type": "Domain",
            "title": domain["name"],
            "description": domain["description"],
            "resource": f"{GITHUB}/durion/blob/{DURION_BRANCH}/domains/{domain['name']}",
            "path": f"durion/domains/{domain['name']}/",
            "tags": ["domain", domain["name"]],
            "sources": [f"domains/{domain['name']}/"],
            "generated": {"by": "script:generate-knowledge-catalog.py", "at": last_touched(domain["path"], REPO)},
        }
        write(CATALOG / "domains" / f"{domain['name']}.md", render(fields, domain_note(domain)), state)

    for module in modules:
        owner = owner_by_module.get(module["name"], "")
        fields = {
            "type": "Module",
            "title": module["name"],
            "description": module["description"],
            "resource": f"{GITHUB}/durion-positivity-backend/blob/main/{module['name']}",
            "path": f"durion-positivity-backend/{module['name']}/",
            "tags": ["backend", module["kind"].lower(), *([owner] if owner else [])],
            "sources": [f"durion-positivity-backend/{module['name']}/"],
            "generated": {"by": "script:generate-knowledge-catalog.py", "at": last_touched(module["path"], BACKEND)},
        }
        write(CATALOG / "backend" / f"{module['name']}.md", render(fields, module_note(module, owner)), state)

    write(
        CATALOG / "adr" / "index.md",
        index_note("ADR Index", [(a["title"], f"{a['slug']}.md", trim(a["description"])) for a in adrs]),
        state,
    )
    write(
        CATALOG / "domains" / "index.md",
        index_note("Domain Index", [(d["name"], f"{d['name']}.md", trim(d["description"])) for d in domains]),
        state,
    )
    write(
        CATALOG / "backend" / "index.md",
        index_note("Backend Module Index", [(m["name"], f"{m['name']}.md", trim(m["description"])) for m in modules]),
        state,
    )

    root_body = (
        "# Durion Knowledge Catalog\n\n"
        "Generated from the canonical documents in this workspace and the backend module suite. "
        "Do not hand-edit: run `python3 scripts/generate-knowledge-catalog.py`.\n\n"
        f"* [ADRs](adr/index.md) — {len(adrs)} architecture decision records\n"
        f"* [Domains](domains/index.md) — {len(domains)} business domains\n"
        f"* [Backend Modules](backend/index.md) — {len(modules)} modules in durion-positivity-backend\n"
    )
    write(CATALOG / "index.md", render({"okf_version": OKF_VERSION}, root_body), state)

    # The log is re-rendered rather than prepended to, so spacing stays consistent as
    # entries accumulate: one entry per day, newest first, blank line under each heading.
    today = dt.date.today().isoformat()
    log_path = CATALOG / "log.md"
    previous = log_path.read_text(encoding="utf-8") if log_path.exists() else ""
    entries = {date: body.rstrip() for date, body in re.findall(r"^## (\S+)\s*\n+((?:\*.*\n)+)", previous, re.M)}
    entries[today] = (
        f"* **Regenerated**: {len(adrs)} ADR, {len(domains)} domain, and {len(modules)} module concepts "
        f"from `docs/adr/`, `domains/`, and the backend module suite."
    )
    rendered = "# Directory Update Log\n\n" + "\n\n".join(
        f"## {date}\n\n{body}" for date, body in sorted(entries.items(), reverse=True)
    )
    write(log_path, rendered + "\n", state)

    verb = "would write" if args.dry_run else "wrote"
    print(f"{verb} {len(state['written'])} file(s); {state['unchanged']} unchanged")
    for name in state["written"][:12]:
        print(f"  {name}")
    if len(state["written"]) > 12:
        print(f"  ... and {len(state['written']) - 12} more")
    if not args.dry_run:
        problems = check_bundle()
        print(f"conformance: {len(problems)} problem(s)")
        for problem in problems[:10]:
            print(f"  {problem}")
        return 1 if problems else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
