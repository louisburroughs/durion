# Agent Consolidation, Knowledge Catalog, and Doc Slimming — Design

**Date:** 2026-08-19
**Status:** Approved (pending implementation)

## 1. Overview & Sequencing

**Goal:** Consolidate scattered agent/skill/tooling configuration under `durion`, stand up an
OKF-based (Open Knowledge Format v0.2) knowledge catalog spanning `durion` and
`durion-positivity-backend`, slim `AGENTS.md`/`README.md` files down to quick-start-plus-links, and
add per-module `index.md` files — all as one coordinated initiative, executed as four sequential
phases.

**Repos involved:** `durion` (becomes the source of truth for shared agent tooling and the
knowledge catalog), `durion-positivity-backend` (first consumer + catalog target). Other repos
(`durion-positivity-frontend`, `-sdk`, `-sdk-angular`, `-sdk-java`, `durion-moqui-frontend`) are
follow-up work for phase 1's submodule mechanism, not blocking this spec.

**Phases, in strict order (each depends on the last):**

1. **Agent consolidation** — move durion-authored `.claude/agents`, `.claude/commands`,
   `.claude/instructions`, `CLAUDE.md` core content, and `.mcp.json` into a single location in
   `durion`, exposed to other repos as a git submodule. Marketplace-sourced skills (via each repo's
   `skills-lock.json`) are untouched — that mechanism already works and is out of scope.
2. **Knowledge catalog** — new OKF v0.2 bundle at `durion/knowledge-catalog/`. v1 scope: `durion`
   (ADRs, domains) + `durion-positivity-backend` (`pos-*` modules), built as thin pointer-concepts
   (`resource:` → GitHub URL) rather than forked content.
3. **AGENTS.md / README.md slimming** — across `durion` and `durion-positivity-backend`, cut both
   down to quick-start + links, moving extracted detail into skills/catalog concepts created in
   phases 1–2.
4. **Per-module `index.md`** — lowercase, OKF-compliant, one per `pos-*` module in
   `durion-positivity-backend` and one per `durion/domains/*`, doubling as both human nav and OKF
   `index.md`.

**Why this order:** phase 2 reuses the git-submodule pattern proven in phase 1; phase 3 needs
phases 1–2 to exist as link targets before content can be safely deleted from `AGENTS.md`; phase
4's `index.md` files are consumed by phase 2's catalog (module index doubles as OKF index), so it
lands last, wiring domain → module → catalog together.

## 2. Phase 1 — Agent Consolidation

**Target structure in `durion`:**

```
durion/
  agent-config/
    agents/          ← from durion/.claude/agents (canonical; backend's copy is a stale
                        duplicate missing test-coverage.md and validate_api_naming.py)
    commands/        ← from durion/.claude/commands
    instructions/    ← from durion/.claude/instructions
    mcp.json         ← from durion/.mcp.json
    CLAUDE.md        ← consolidated core guidance
```

**Mechanism:**

- `durion` hosts this directory natively.
- Each consuming repo adds `durion` as a git submodule at a fixed path, e.g. `.durion-shared/`
  (read-only reference, pinned commit).
- Each repo's `.claude/agents`, `.claude/commands`, `.claude/instructions` become symlinks into
  `.durion-shared/agent-config/...` so tools that expect `.claude/agents/*.md` keep working
  unmodified.
- Repo-specific custom agents (e.g. backend's `mermaid-erd-all.agent.md` under `.github/agents/`)
  stay local — only durion-authored *generic* agents/commands/instructions move.
- `.mcp.json`: repos with identical server configs symlink to the shared file; repos needing extras
  keep a local file, untouched by this phase (known gap, not solved here).
- Update mechanism: bumping the submodule pointer in each consumer repo is a manual
  `git submodule update --remote` + commit — no auto-sync daemon in v1.

**Risk/rollback:** reconcile `durion`'s and backend's `.claude/agents` copies to one canonical
version *before* any repo starts symlinking, so nothing referenced only by the backend copy is
silently dropped.

## 3. Phase 2 — Knowledge Catalog

**Bundle root:** `durion/knowledge-catalog/` (OKF v0.2). Root `index.md` carries
`okf_version: 0.2`.

**Structure:**

```
knowledge-catalog/
  index.md
  log.md
  adr/
    index.md
    adr-0044-platform-event-only-domain-walls.md   # pointer concept
    ...
  domains/
    index.md
    accounting.md  billing.md  crm.md  ...          # one per durion/domains/*
  backend/
    index.md
    pos-order.md  pos-accounting.md  ...            # one per pos-* module
```

**Pointer concept shape** (example — `domains/accounting.md`):

```yaml
---
type: Domain
title: Accounting
description: Journal entries, ledger, event ingestion for financial records.
resource: https://github.com/louisburroughs/durion/blob/main/domains/accounting
tags: [domain, accounting, backend]
generated: { by: human:louisburroughs, at: 2026-08-19T00:00:00Z }
---
See the [Accounting domain docs](https://github.com/louisburroughs/durion/blob/main/domains/accounting)
for capability specs.
Backend implementation: [pos-accounting](/backend/pos-accounting.md).
```

- `type` values used: `ADR`, `Domain`, `Module` (for `pos-*` backend services), consistent
  bundle-wide.
- Cross-links between `domains/*.md` and `backend/*.md` use bundle-relative paths (OKF §6.1) to
  connect the domain spec to its implementing module.
- No `verified`/trust fields asserted in v1 (all concepts start unverified — accurate, since
  nothing has been through review yet).

**Generation approach:** a one-time script scans `durion/docs/adr/*.md`, `durion/domains/*/`, and
`durion-positivity-backend/pos-*/` to scaffold pointer files (title/description pulled from each
doc's first heading or existing summary field), committed once, then hand-maintained. No re-run
automation in v1 — flagged as a future improvement.

**Non-goals for v1:** no Attested Computation concepts, no `sources`/credibility signals, no
cross-org exchange — navigable pointers only.

## 4. Phase 3 — AGENTS.md / README.md Slimming

**AGENTS.md target shape (per repo):**

```markdown
# AGENTS.md — <repo>

## Quick Start
<build/test/run commands only>

## Non-negotiable Rules
<bulleted, 1 line each>

## Where to Look
- Shared agent config: `.durion-shared/agent-config/` (phase 1)
- Domain/module knowledge: `durion/knowledge-catalog/` (phase 2)
- Module docs: `<module>/index.md` (phase 4)
- Full patterns/templates: <link to specific skill or catalog concept>
```

**What moves out, and to where:**

- Full code templates (event registry, event initializer, ArchUnit rule examples) currently in
  backend's `AGENTS.md` → new skill (e.g. `.agents/skills/pos-events-pattern/SKILL.md`). Task-
  execution patterns become skills; "how this system works" narratives become catalog `Playbook`
  concepts.
- ADR-0044 domain-wall narrative → AGENTS.md keeps a one-line pointer to the canonical ADR, deletes
  the prose duplicate.
- Permission catalog sync steps → condense to command + link to `docs/OPERATIONS_RUNBOOK.md`.

**README.md target shape:** what it is (2–3 sentences) → quick start (setup/run) → links
(AGENTS.md, knowledge-catalog, docs/). Drop embedded architecture diagrams/long prose duplicating
`docs/ARCHITECTURE_GUIDE.md`.

**Order of operations per repo:** only slim `AGENTS.md`/`README.md` after the replacement
skill/catalog concept exists and is committed — never delete-then-create.

**Scope for v1:** `durion` and `durion-positivity-backend` only; frontend/sdk repos get the same
treatment as a documented follow-up.

## 5. Phase 4 — Per-Module index.md

**Per `pos-*` module** (e.g. `pos-order/index.md`, no frontmatter per OKF §8):

```markdown
# pos-order

* [service/](service/) - Public API interfaces exposed to other modules
* [internal/controller](internal/controller/) - REST endpoints
* [internal/service](internal/service/) - Business logic
* [internal/repository](internal/repository/) - Spring Data JPA
* [internal/entity](internal/entity/) - JPA entities

# Related
* [Domain spec](https://github.com/louisburroughs/durion/blob/main/domains/order) - capability/business rules
* [Catalog concept](https://github.com/louisburroughs/durion/blob/main/knowledge-catalog/backend/pos-order.md)
```

Generated once by a script walking each module's package tree, then hand-maintained.

**`durion/domains/*/index.md`:** same pattern — lists sub-docs in that domain folder plus a link
to the implementing `pos-*` module(s) and its catalog concept, closing the loop
domain → module → catalog.

## 6. Validation

- **Phase 1:** submodule resolves in each consumer repo; symlinked `.claude/agents` still loads in
  Claude Code/Copilot CLI (spot-check one agent invocation).
- **Phase 2:** every pointer concept's `resource` URL resolves (link-check script); root `index.md`
  lists every subdirectory.
- **Phase 3:** `AGENTS.md`/`README.md` word count drop measured; no broken internal links after
  trimming (markdown link checker).
- **Phase 4:** `./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test` still passes (index.md
  files are docs-only, shouldn't affect ArchUnit, but confirms no accidental package disturbance).

## 7. Non-Goals (all phases)

No CI enforcement/automation added in v1 — no lint rule requiring `index.md` on new modules, no
catalog freshness bot, no auto-sync daemon for the submodule. Pure structural/content work now;
tooling enforcement is a future follow-up.
