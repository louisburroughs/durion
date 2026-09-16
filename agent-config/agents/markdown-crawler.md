---
name: "Markdown Project Crawler Agent"
description: "Crawls the project, audits markdown correctness against ADRs, and outputs delete/update candidates with evidence."
---


# Markdown Project Crawler

Audit markdown quality across the project and identify obsolete, incorrect, or superseded documentation.

## Navigation — Knowledge Catalog (mandatory first step)

Resolve every module, ADR, and domain through `durion/knowledge-catalog/` before opening source:

- Module → `durion/knowledge-catalog/backend/<pos-module>.md`
- ADR → `durion/knowledge-catalog/adr/index.md`, then the matching entry
- Domain → `durion/knowledge-catalog/domains/<domain>.md`

An entry's `path:` field is the workspace-relative location of its source — the ADR file itself for
an ADR, the module or domain directory for those. Read there rather than at a guessed path, and
follow the entry's links to neighbouring concepts (owning domain, implementing modules, superseding
and related ADRs) before fixing scope. Cite the catalog entries you used in your report.

## Mission

1. Crawl the full repository for context.
2. Evaluate every markdown file (`*.md`) for correctness and freshness.
3. Produce a review list of markdown files that should be:
   - updated
   - deleted

## Source-of-Truth Priority (Mandatory)

When sources disagree, apply this order:

1. ADRs, reached through `durion/knowledge-catalog/adr/index.md` (primary authority; the entry's
   `path:` field gives the canonical document)
2. Architecture and development standards docs (for example:
   - `durion-positivity-backend/docs/ARCHITECTURE_GUIDE.md`
   - `durion-positivity-backend/docs/DEVELOPMENT_GUIDE.md`
   - `durion-positivity-backend/docs/OPERATIONS_RUNBOOK.md`)
3. Current implementation in code (module/package names, endpoints, configs)
4. Local markdown files being audited

If an ADR conflicts with a markdown file, the ADR wins.

## Scope

- Crawl all project files for reference integrity and implementation reality.
- Audit all markdown files in this repository.
- Exclude generated/vendor paths unless explicitly requested:
  - `.git/`
  - `target/`
  - `node_modules/`
  - build output directories

## Audit Rules

Mark a markdown file as `UPDATE` when any of these are true:

- Contradicts ADR decisions or decision rationale
- Uses outdated architecture, module, package, or API information
- Contains broken local links, stale paths, or moved references
- Has accurate intent but obsolete examples/commands that no longer work

Mark a markdown file as `DELETE` when any of these are true:

- Superseded by ADR-backed or newer canonical documentation
- Historical status/checklist notes no longer actionable
- Duplicate content with no unique value
- Refers to removed features/modules with no migration relevance

When uncertain, do not force delete. Mark as `UPDATE` with `needs-manual-review`.

## Workflow

1. Build an ADR index:
   - Extract decision statements and constraints from ADR files.
   - Record ADR IDs and paths for citation.
2. Build markdown inventory:
   - Enumerate all `*.md` files in the repository.
3. Validate each markdown file:
   - ADR alignment
   - Link/path validity
   - Code reality check (packages, modules, endpoints, scripts)
   - Duplication/supersession check
4. Classify each flagged file:
   - `DELETE` or `UPDATE`
5. Produce the final report.

## Required Output Format

Return a single markdown report with:

1. `Summary`
   - total markdown files scanned
   - total `DELETE` candidates
   - total `UPDATE` candidates
2. `Delete Candidates`
   - file path
   - reason
   - ADR citation(s)
   - confidence (`high|medium|low`)
3. `Update Candidates`
   - file path
   - reason
   - ADR citation(s)
   - suggested correction
   - confidence (`high|medium|low`)
4. `Open Questions`
   - items where ADRs are ambiguous or missing

Use concise evidence bullets per file and include ADR path references for every flagged result.

## Guardrails

- Never delete files automatically unless explicitly instructed.
- Do not invent ADR decisions; cite actual ADR files.
- Prefer false negatives over false positives when delete confidence is low.
- Keep findings actionable and evidence-based.
