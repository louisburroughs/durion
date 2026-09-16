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
    split_frontmatter,
)

REPO = Path(__file__).resolve().parents[1]
BACKEND = Path(os.environ.get("DURION_BACKEND", REPO.parent / "durion-positivity-backend"))
CATALOG = REPO / "knowledge-catalog"
GITHUB = "https://github.com/louisburroughs"
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
    lines = ["---"]
    for key, value in fields.items():
        if value in ("", None, []):
            continue
        if isinstance(value, list):
            lines.append(f"{key}: [{', '.join(str(v) for v in value)}]")
        elif isinstance(value, dict):
            inner = ", ".join(f"{k}: {v}" for k, v in value.items())
            lines.append(f"{key}: {{ {inner} }}")
        elif isinstance(value, str) and (":" in value or value.startswith(("'", '"', "["))):
            lines.append(f"{key}: '{value.replace(chr(39), chr(39) * 2)}'")
        else:
            lines.append(f"{key}: {value}")
    lines.append("---")
    return "\n".join(lines) + "\n\n" + body.rstrip("\n") + "\n"


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
        keys = existing_keys(frontmatter)
        number = re.match(r"(\d+)", path.name).group(1)
        title = keys.get("title", "").strip("'\"") or derive_title(number, "", body)
        records.append(
            {
                "number": number,
                "slug": path.stem.replace(".adr", ""),
                "path": path,
                "title": title,
                "description": keys.get("description", "").strip("'\"") or derive_description(body, title),
                "status": keys.get("status", ""),
                "adr_status": keys.get("adr_status", ""),
                "created": keys.get("created", ""),
                "supersedes": keys.get("supersedes", ""),
                "superseded_by": keys.get("superseded_by", ""),
                "related": [r.strip() for r in keys.get("related", "").strip("[]").split(",") if r.strip()],
                "tags": [t.strip() for t in keys.get("tags", "").strip("[]").split(",") if t.strip()] or ["adr"],
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
            guide_keys = existing_keys(split_frontmatter(guide.read_text(encoding="utf-8", errors="replace"))[0])
            guides.append((guide.name, guide_keys.get("type", "Domain Document")))
        records.append(
            {
                "name": domain.name,
                "path": domain,
                "description": description or f"The {domain.name} domain.",
                "guides": guides,
                "modules": domain_modules.get(domain.name, []),
                "docs": len(list(domain.rglob("*.md"))),
            }
        )
    return records


# ── note rendering ────────────────────────────────────────────────────────────


def adr_note(record: dict, owner_by_module: dict[str, str]) -> str:
    resource = f"{GITHUB}/durion/blob/main/docs/adr/{record['path'].name}"
    lines = [f"[Canonical ADR]({resource}) — `docs/adr/{record['path'].name}`", ""]
    if record["adr_status"]:
        state = record["adr_status"].capitalize()
        dated = f" since {record['created']}" if record["created"] else ""
        lines.append(f"**Status:** {state}{dated}")
    if record["supersedes"]:
        lines.append(f"**Supersedes:** [{record['supersedes']}](/adr/{slug_for(record['supersedes'])}.md)")
    if record["superseded_by"]:
        lines.append(f"**Superseded by:** [{record['superseded_by']}](/adr/{slug_for(record['superseded_by'])}.md)")
    if record["related"]:
        links = ", ".join(f"[{r}](/adr/{slug_for(r)}.md)" for r in record["related"][:8])
        lines.append(f"**Related:** {links}")
    return "\n".join(lines)


SLUGS: dict[str, str] = {}


def slug_for(adr_id: str) -> str:
    return SLUGS.get(adr_id.replace("ADR-", ""), adr_id.lower())


def domain_note(record: dict) -> str:
    resource = f"{GITHUB}/durion/blob/main/domains/{record['name']}"
    lines = [f"[Domain folder]({resource}) — `domains/{record['name']}/` ({record['docs']} documents)", ""]
    if record["modules"]:
        links = ", ".join(f"[{m}](/backend/{m}.md)" for m in record["modules"])
        lines.append(f"**Implemented by:** {links}")
    if record["guides"]:
        # Every guide, linked and typed: these are the documents an agent opens next,
        # so a bare filename list made the note a dead end.
        lines += ["", "**Business rules:**", ""]
        base = f"{resource}/.business-rules"
        for name, kind in record["guides"]:
            lines.append(f"* [{name}]({base}/{name}) — {kind}")
    return "\n".join(lines)


def module_note(record: dict, owner: str) -> str:
    resource = f"{GITHUB}/durion-positivity-backend/blob/main/{record['name']}"
    lines = [f"[Module directory]({resource}) — `{record['name']}/`", "", f"**Kind:** {record['kind']}"]
    if owner:
        lines.append(f"**Domain:** [{owner}](/domains/{owner}.md)")
    if record["openapi"]:
        lines.append(f"**API contract:** [`openapi.yaml`]({resource}/openapi.yaml)")
    return "\n".join(lines)


def index_note(heading: str, rows: list[tuple[str, str, str]]) -> str:
    lines = [f"# {heading}", ""]
    for name, link, description in rows:
        summary = f" — {description}" if description else ""
        lines.append(f"* [{name}]({link}){summary}")
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
        keys = existing_keys(frontmatter)
        if path.name in RESERVED:
            if "okf_version" in keys and relative != Path("index.md"):
                problems.append(f"{relative}: okf_version may only appear in the bundle-root index.md")
            if frontmatter and relative != Path("index.md"):
                problems.append(f"{relative}: reserved file must not carry frontmatter")
            continue
        if not frontmatter:
            problems.append(f"{relative}: missing frontmatter")
        elif not keys.get("type"):
            problems.append(f"{relative}: frontmatter has no non-empty type")
    root = CATALOG / "index.md"
    if not root.exists():
        problems.append("index.md: bundle root index is missing")
    elif "okf_version" not in existing_keys(split_frontmatter(root.read_text(encoding="utf-8"))[0]):
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
            "resource": f"{GITHUB}/durion/blob/main/docs/adr/{adr['path'].name}",
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
            "resource": f"{GITHUB}/durion/blob/main/domains/{domain['name']}",
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
        f"* [ADRs](/adr/index.md) — {len(adrs)} architecture decision records\n"
        f"* [Domains](/domains/index.md) — {len(domains)} business domains\n"
        f"* [Backend Modules](/backend/index.md) — {len(modules)} modules in durion-positivity-backend\n"
    )
    write(CATALOG / "index.md", render({"okf_version": OKF_VERSION}, root_body), state)

    today = dt.date.today().isoformat()
    entry = (
        f"## {today}\n"
        f"* **Regenerated**: {len(adrs)} ADR, {len(domains)} domain, and {len(modules)} module concepts "
        f"from `docs/adr/`, `domains/`, and the backend module suite.\n"
    )
    log_path = CATALOG / "log.md"
    previous = log_path.read_text(encoding="utf-8") if log_path.exists() else "# Directory Update Log\n"
    previous = re.sub(rf"## {today}\n(?:\*.*\n)+", "", previous)  # one entry per day
    header, _, rest = previous.partition("\n")
    write(log_path, f"{header}\n\n{entry}{rest.lstrip()}", state)

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
