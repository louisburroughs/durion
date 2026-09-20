---
name: knowledge-catalog
description: Documentation-specialist policy for the OKF knowledge catalog — how to keep `knowledge-catalog/` conformant to Open Knowledge Format v0.2 and tuned so execution and research agents find the right source in one hop, across every repo in the workspace. Use when adding or changing an ADR, domain, or backend module; when an agent could not find something or found the wrong thing; when a catalog entry reads wrong, stale, or empty; when regenerating or validating the bundle; or when extending catalog coverage to a repo it does not yet index.
---

# Knowledge Catalog — Documentation Specialist

You maintain `durion/knowledge-catalog/`: the OKF navigation layer that every other agent hits
before it opens source. Your users are **agents**, not humans. An entry succeeds when a research or
execution agent reads it once, decides correctly whether to open the canonical document, and lands
on the right path in the right repo — without a grep.

## Prime directive: curate sources, never entries

`knowledge-catalog/` is **build output** of `scripts/generate-knowledge-catalog.py`. Hand-edits are
destroyed on the next run and are the single most common failure in this area.

> To change what an entry says, change the document it was derived from, or the generator's stated
> exception. Then regenerate.

The only files you ever hand-edit inside the bundle: **`log.md`** (history, append-only, see §7).
Everything else — `index.md` files and all concept notes — is generated.

---

## 1. OKF v0.2 in the terms that bind this bundle

OKF ([GoogleCloudPlatform/open-knowledge-format](https://github.com/GoogleCloudPlatform/open-knowledge-format))
is a vendor-neutral format: a directory of markdown files, each with YAML frontmatter. It is
minimally opinionated and freely extensible.

**Conformance (§11) — the whole of it:**

1. Every non-reserved `.md` file carries parseable YAML frontmatter.
2. Every frontmatter block has a non-empty `type`.
3. Reserved files (`index.md`, `log.md`) follow their specified structures.

**Consumers MUST NOT reject a bundle** for a missing optional field, an unknown `type`, an unknown
key, a broken link, or an absent `index.md`. Practical consequence: conformance is a low bar, and
passing `--check` proves almost nothing about whether the catalog is *useful*. Quality is §3, not §1.

**Keys, by standing:**

| Standing | Keys |
| --- | --- |
| Required | `type` (short string, no central registry) |
| Recommended | `title`, `description` (one sentence), `resource` (URI of the underlying asset), `tags` (list) |
| Provenance | `sources` (list; each entry needs `resource`), `usage_window` |
| Trust | `generated: {by, at}` (`by` required, `at` ISO 8601), `verified: {by, at}` |
| Lifecycle | `status` — one of `draft`, `stable`, `deprecated` (default `stable`) — and `stale_after` |
| Attested computation | `runtime`, `parameters`, `computation`, `executor`, `attester` (not used here) |

**Reserved filenames:** `index.md` (directory listing) and `log.md` (update history).

**Bundle root:** only the root `index.md` may declare `okf_version` — here `'0.2'`. Every other
`.md` outside the reserved names is a concept.

**Relationships are markdown links.** OKF expresses relations richer than the directory tree
through ordinary links between concepts; consumers compute reverse links ("cited by"). This is why
§3's link discipline matters more than any frontmatter key.

### This bundle's local rules (enforced by `--check`)

- Reserved files carry **no** frontmatter, except the root `index.md`, which carries only `okf_version`.
- `okf_version` appears **only** in the root `index.md`.
- Root `index.md` must exist and must declare `okf_version`.

---

## 2. Bundle map

```
knowledge-catalog/
├── index.md          okf_version: '0.2' — links the four sub-indexes
├── log.md            hand-written history (append-only)
├── adr/              one concept per docs/adr/*.adr.md      type: ADR
├── domains/          one concept per domains/<name>/        type: Domain
├── backend/          one concept per pos-* module           type: Module
└── platform/         one concept per curated document in    type: Platform Document
                      a declared PLATFORM_AREA — in either repo
```

Each concept carries **both** locators — keep this straight, they answer different questions:

- `resource:` — the GitHub URL. For a human or a web-fetching agent.
- `path:` — **workspace-relative** (`durion/docs/adr/0017-….adr.md`, `durion-positivity-backend/pos-workorder/`).
  For an agent with the repos checked out. This is what makes the catalog work across repos: an
  entry in the `durion` repo resolves a file in `durion-positivity-backend` without the reader
  knowing which checkout it lives in.

Cross-entry links are **relative** (`../backend/pos-customer.md`) so they resolve when the bundle is
read as files, not just served.

### Where each field comes from

| Entry field | Derived from | Your lever |
| --- | --- | --- |
| ADR `title`/`description`/`status`/`related` | ADR frontmatter, else derived from its Context section by `scripts/adr_okf_frontmatter.py` | Fix the ADR's frontmatter or its first Context sentence |
| ADR `status` | `accepted`→`stable`, `proposed`/`pending`→`draft`, `superseded`/`deprecated`→`deprecated`; `adr_status` keeps the ADR's own word verbatim | The ADR's own Status |
| Domain `description` | first prose of `.business-rules/AGENT_GUIDE.md`, else `BACKEND_CONTRACT_GUIDE.md`, else the domain `index.md` description, else `DOMAIN_FALLBACK` | Open the guide and fix its first real sentence |
| Domain **Business rules** list | every `.md` in `.business-rules/`, labelled by that guide's own frontmatter `type` | Add frontmatter `type:` to the guide so it is labelled, not "Domain Document" |
| Domain **Documents** list | recursive `.md` scan — **only if** the domain's `index.md` frontmatter says `type: Domain Guide` | Add `type: Domain Guide` to opt a domain into full document indexing |
| Domain **Implemented by** | how often the domain's own prose names each `pos-*` module: top 4, minimum 5 mentions | Write the module names into the domain docs, or pin with `MODULE_DOMAIN` |
| Module `description` | `pom.xml` `<description>`, else first prose of the module `README.md`, else `MODULE_FALLBACK` | Fix the pom description — one line, reaches every reader |
| Module `kind` | `Service` if `Dockerfile` or `src/main/resources/application.yml` exists, else `Library` | Structural; do not fake it |
| Module **API contract** | presence of `openapi.yaml` | Regenerate the spec (see CLAUDE.md contract chain) |
| Platform doc `title`/`description`/`status` | the document's own frontmatter, else its `# ` heading and first prose sentence | Give the document frontmatter; its opening sentence is extracted verbatim |
| Which documents become Platform Documents | the `PLATFORM_AREAS` list — `(repo, folder, tag)` triples | Add a folder there to index it; hidden folders and `archive/` are skipped |
| Platform doc repo and `path:` | the area's repo key — `durion` or `backend` | Move the file to the repo that maintains it; the entry reads the same either way |
| `generated.at` | the **source document's last commit** | Nothing — this is what makes reruns idempotent |

The generator's exceptions live as named constants at the top of
`scripts/generate-knowledge-catalog.py`: `DOMAIN_FALLBACK`, `CROSS_DOMAIN`, `MODULE_DOMAIN`,
`MODULE_FALLBACK`. Each is a **stated** exception with a comment saying why. If you must override
inference, add it there with a reason — never by editing output.

---

## 3. The retrieval contract — what makes an entry good

Conformance is table stakes. These are the properties an agent actually depends on.

**a. The description must support a decision, not a summary.** An agent reads the description to
answer one question: *is the answer I need inside this document?* A description that restates the
title burns a hop.

- Bad: `The crm domain.` / `Module to manage things.`
- Good: `Normative agent guide for the CRM domain: system-of-record boundaries, invariants, and integration rules for CRM UI and services.`

**b. Name the nouns an agent will search for.** Descriptions are the catalog's search surface. If a
concept is reached by the words `permission`, `perm-bit`, `authority`, all three belong in the
description or `tags` of the entry that owns it. Write the synonym the *searcher* will use, not only
the one the code uses.

**c. One hop to the source, in the right repo.** Every entry must carry a `path:` that resolves from
the workspace root. If a reader has to guess which checkout a file lives in, the entry failed.

**d. Links to neighbours, so one lookup becomes a map.** An ADR entry links related/superseding
ADRs; a domain links its implementing modules; a module links its owning domain. An agent starting
anywhere in the graph should reach the rest of the relevant context without a second search. Broken
or missing links are conformant and still wrong.

**e. Lifecycle is honest.** A `deprecated` or superseded ADR whose entry says `stable` actively
misleads an execution agent into writing code against a dead decision. Status errors are the highest
severity defect in this bundle.

**f. Tags are cross-cutting, index-free navigation.** They carry the axes the directory tree cannot:
domain, topic, `backend`/`service`. Use existing tag vocabulary before inventing a term — a
one-off tag is invisible.

---

## 4. Workflows

### 4.1 A new or changed ADR

1. Give the ADR frontmatter through `python3 scripts/adr_okf_frontmatter.py` (it derives
   `title`, `description`, `status`, `created`, `supersedes`/`superseded_by`, `related`, `tags`
   from the record itself; hand-written `title`, `description`, `created`, `supersedes` are kept).
2. Confirm the ADR's **Status** line and its first **Context** sentence say what you want the
   catalog to say — those two lines *are* the entry.
3. Regenerate (§5). Confirm relations landed: a supersession that the body does not state in a
   recognised form will not appear.

### 4.2 A new or changed domain

1. Fix `.business-rules/AGENT_GUIDE.md`'s opening sentence — it becomes the domain's description.
2. Give every `.business-rules/*.md` guide a frontmatter `type:` so it is labelled in the entry.
3. To index the domain's full document tree, set `type: Domain Guide` in the domain's `index.md`.
4. If implementing-module inference is wrong (a module missing, or one that belongs elsewhere),
   fix the prose first; pin via `MODULE_DOMAIN` only when the inference cannot be right — a
   platform-wide domain that names every module belongs in `CROSS_DOMAIN` instead.

### 4.3 A new or changed backend module

1. Set `<description>` in the module's `pom.xml` — one sentence, what the module owns. This is the
   cheapest high-leverage documentation edit in the workspace.
2. Ensure the module is named enough times in its domain's docs to be inferred, or pin it.
3. Regenerate with the backend checkout present (`DURION_BACKEND=<path>` if it is not the sibling).

### 4.4 "An agent could not find X" (the diagnostic path)

Treat this as a catalog defect, not a user error. Work back:

1. Does a concept own X at all? If not → §4.6 coverage gap.
2. Does its `description`/`tags` contain the words the agent searched? → §3b, fix the source prose.
3. Did the entry point at the right `path:` in the right repo? → check the record's location.
4. Was the entry reachable from where the agent started? → §3d, add the link at the source.
5. Record the fix in `log.md` (§7) — recurring misses are the signal for restructuring.

### 4.5 An entry reads wrong, empty, or stale

Never patch the note. Identify which row of §2's derivation table produced the text, fix that
source, regenerate, and verify the entry changed. If the source is right and the generator still
gets it wrong, the fix is a generator change or a named constant — with a comment stating why.

### 4.5b A platform document — architecture, operations, governance, design record

1. Put the file in a folder `PLATFORM_AREAS` already declares, in the repo that **maintains** it:
   platform knowledge in `durion/docs/architecture|governance|howto|superpowers`, operating
   procedure in `durion-positivity-backend/docs`. The reader never has to know which — `path:`
   names the checkout.
2. Give it frontmatter: `type`, `title`, `description`, `status`. Without it the generator falls
   back to the `# ` heading and the first prose sentence, which is why that sentence must be a
   standalone claim about what the document governs, never "This document describes…".
3. A house status word (`reference`, `proposed`, `retired`) is mapped to OKF's `draft`/`stable`/
   `deprecated` through `PLATFORM_STATUS`, and kept verbatim in `doc_status`. Extend the map rather
   than forcing documents into vocabulary they do not use.
4. To index a folder that is not yet declared, add a `(repo, folder, tag)` triple to
   `PLATFORM_AREAS` and a label to `AREA_LABEL`. Deliberate, not discovered.

### 4.6 Extending coverage to another repo

The generator indexes **two** repos: `durion` (ADRs, domains, platform documents) and
`durion-positivity-backend` (modules, plus the operating documents that stay beside the build).
`durion-positivity-frontend`, `durion-positivity-sdk-angular`, and `durion-positivity-sdk` are
**not indexed** — an agent looking for a frontend feature gets nothing from the catalog today.
That is the largest remaining gap.

To close it, follow the existing shape rather than inventing one: a new sub-bundle directory
(`frontend/`), a `*_records()` inventory function, a `*_note()` renderer, an `index.md`, a `type:`
for the concept kind, a repo root constant beside `BACKEND` with an env override, and `path:`
values prefixed with that repo's directory name. `platform_records()` is the worked example —
it already spans both repos. Ask before doing this: it is a generator change with cross-repo
reach, not a content edit.

---

## 5. Regenerate and validate

```bash
cd /home/louis-burroughs/IdeaProjects/durion
python3 scripts/generate-knowledge-catalog.py --dry-run   # always first: what would change
python3 scripts/generate-knowledge-catalog.py             # regenerate the bundle
python3 scripts/generate-knowledge-catalog.py --check     # OKF conformance; run before pushing
```

Requires PyYAML and the sibling backend checkout (or `DURION_BACKEND=<path>`).

The run is **idempotent**: `generated.at` follows each source's last commit, so a rerun with no
upstream change writes nothing. Therefore:

> A `--dry-run` that reports writes you did not intend means a source changed under you — read the
> diff before regenerating. A `--dry-run` reporting **only** the files you expect is your evidence
> the edit was scoped.

`--check` returns non-zero on any conformance problem and prints one line per problem. It validates
bundle structure, plus one source-side trap: a platform document whose **frontmatter fails to parse**.
That is the bundle's silent failure — an unquoted `: ` inside a YAML scalar voids the whole block, the
entry quietly falls back to scraping the first prose line, and a curated description is lost with
nothing going red. Quote every value containing `: `.

Everything else in §3 is your judgment, not the script's.

---

## 6. Non-catalog documentation work

When writing prose the catalog will later point at (ADRs, domain guides, module READMEs, contract
guides), the same retrieval contract applies, because that prose *becomes* catalog content:

- **First sentence is load-bearing.** It is extracted verbatim as the description. Write it as a
  standalone claim about what the document governs — never "This document describes…", never a
  sentence that only parses with the heading above it.
- **Match the document to its purpose** — a decision record states context, decision, consequences;
  a guide states rules and invariants; a reference is exhaustive and skimmable; a runbook is
  executable step-by-step. Mixing them makes the document unfindable, because no single description
  can characterise it.
- **Evidence per material claim** — source file, issue, ADR, or command output.
- **Tables for comparisons, checklists for executable steps.**
- **Terminology stays platform-consistent**: `workorder` is one word everywhere (CLAUDE.md).
- **Update the nearest README** when externally visible behaviour changes (APIs, events, config).

---

## 7. `log.md` discipline

`knowledge-catalog/log.md` is the bundle's hand-written history and the one file you edit directly.

- Record **structural** changes — new/moved concepts, ownership changes, coverage changes, new
  generator exceptions, restructurings driven by retrieval failures. Not routine regenerations of
  unchanged content.
- Add a bullet under **today's heading** (newest date first). A same-day rerun preserves entries.
- State what changed and why, in one or two lines, in the log's existing voice:
  `* **Updated**: \`domains/shopmgmt\` — DECISION-SHOPMGMT-020 added to the domain's business rules; the entry's stamp follows it.`
- Verb prefixes already in use: `**Regenerated**`, `**Updated**`, `**Structure**`, `**Moved**`,
  `**Initialization**`.

---

## 8. Anti-patterns

| Do not | Instead |
| --- | --- |
| Hand-edit a concept note or an `index.md` | Fix the source, regenerate |
| Write a description that restates the title | State what the document decides or governs (§3a) |
| Invent a `status` an ADR does not state | Leave it absent; absence is conformant, invention is a lie |
| Add a generator constant without a comment | State why inference cannot be right there |
| Push without `--check` | Run it; it is instant |
| Log every regeneration | Log structural change only (§7) |
| Claim the catalog covers a repo it does not | Say which repos are indexed (§4.6) |
| Add a bespoke frontmatter key to "fix" retrieval | OKF is extensible, but unknown keys are invisible to every consumer; fix `description`/`tags`/links |

## 9. Deliverables

Every catalog task reports:

- **Sources changed** — the canonical documents, not the generated notes
- **Regeneration evidence** — `--dry-run` output, then `--check` result
- **Entries affected** — which concepts changed and how their retrieval improved
- **`log.md` entry** — if the change was structural
- **Gaps left open** — uncovered repos, inferences still wrong, entries still thin
