# Accounting Documentation Reconciliation Implementation Plan

> **For agentic workers:** Use the executing-plans workflow task by task, with an independent Anvil review adapted to available tools.

**Goal:** Make `durion/domains/accounting/` the canonical home for the seven documents currently in backend `pos-accounting/docs/`, with durable knowledge-catalog navigation.

**Architecture:** Preserve existing accounting business rules as their own authority, classify old implementation reports and schema snapshots as historical, and retain reference material without presenting it as a current contract. Backend documentation becomes navigation pointers. Extend the existing catalog generator so regeneration preserves document links and lifecycle status.

**Tech Stack:** Markdown, existing Python catalog generator, standard-library unittest.

## Baseline and scope

- Both repositories were clean: durion `0e5f0a75`, backend `e5d7ec82e`.
- Work branches: `docs/accounting-documentation-reconciliation` in both checkouts.
- Existing catalog: 123 concepts, zero OKF conformance problems; full regeneration already proposes 72 unrelated file changes.
- All seven backend documents must survive, including historical examples; backend copies become short redirects.
- No application code, schema, packaged RAG content, API contract, or runtime behavior changes.
- Anvil's local agent definition is available at `agent-config/agents/anvil.md`; dedicated runtime/SQL ledger tools are unavailable. Use tool-backed checks and an independent review without claiming those integrations.

## Task 1: Reconcile ownership and content

- [x] Read source documents, existing domain guides, and ADR-0044 / ADR-0062; identify conflicting or obsolete assertions.
- [x] Move `BILL_MATCHING_IMPLEMENTATION_SUMMARY.md`, `BILL_MATCHING_IMPROVEMENTS.md`, and `GL_POSTING_EVENT_IMPLEMENTATION.md` into `domains/accounting/archive/implementation/`.
- [x] Move `flyway-baseline-reset-plan.md` and `pos-accounting-erd.md` into `domains/accounting/archive/`; wrap the ERD in a Mermaid fence.
- [x] Move `OUTBOX_PATTERN.md` into `domains/accounting/archive/implementation/` as a historical report; prominently distinguish the current two outboxes and correct overclaimed guarantees with source links.
- [x] Move `de-bookkeeping-rag.md` into `domains/accounting/reference/` as conceptual, non-normative material.
- [x] Add `domains/accounting/implementation-reference.md` to reconcile overlapping reports into one inspected implementation reference, with links to current source.
- [x] Add type, title, description, status, and original source provenance to every migrated document; annotate stale claims without silently rewriting historical results.

## Task 2: Canonical navigation and backend pointers

- [x] Expand `domains/accounting/index.md` with authority precedence, the seven destinations, existing business rules, comparisons, plans, research and UI entry points.
- [x] Add a pointer to that index from `.business-rules/AGENT_GUIDE.md`.
- [x] Replace each backend source file with a short canonical link, retaining old paths for bookmarks.
- [x] Add backend `pos-accounting/docs/README.md`; update module `README.md` and `index.md` and the inbound Flyway/runbook/planning references found by repository search.

## Task 3: Durable catalog generation

- [x] Add `scripts/tests/test_accounting_catalog.py` with isolated fixtures proving that published domain documents are individually linked with their lifecycle status, hidden business rules remain separate, untyped domains retain existing output, and module navigation reaches the canonical domain index.
- [x] Run `python3 -m unittest discover -s scripts/tests -p test_accounting_catalog.py` and confirm the new behavior fails before implementation.
- [x] Extend `scripts/generate-knowledge-catalog.py` to opt domains into document indexing through a typed domain `index.md`; discover visible Markdown recursively and render titles/status without copying canonical prose.
- [x] Run the tests again; regenerate the accounting domain and its associated module catalog entries using the generator, preserving unrelated baseline drift outside those entries.
- [x] Update root `README.md` with the catalog source/indexing convention.

## Task 4: Verification and independent review

- [x] Verify all seven original bodies are retained, allowing only documented link repairs, Markdown rendering fixes, and status/provenance additions.
- [x] Resolve all new relative and canonical repository links locally; check migrated document coverage in the accounting index and generated catalog.
- [x] Run catalog unit tests and `python3 scripts/generate-knowledge-catalog.py --check` (expect zero problems).
- [x] Regenerate accounting entries a second time and verify byte-for-byte stability.
- [x] Run `git diff --check` in both repositories; inspect the complete changed-file list for unintended edits.
- [x] Obtain independent Anvil review, resolve material findings, and record actual verification results here.

## Execution results

Completed on 2026-09-16 in both working checkouts. The user subsequently authorized commits and coordinated pull requests.

- Preserved all seven original bodies (compared against backend HEAD) and replaced the seven backend files with short compatibility pointers.
- Added a consolidated implementation reference to reconcile the overlapping reports; marked six historical/superseded sources and one conceptual reference.
- Verified 175 repository/local links across 30 changed Markdown files, with zero missing paths or checked local anchors.
- Verified each migrated document has exactly one canonical destination and is linked from the domain index, generated catalog and old backend path.
- Verified the packaged MCP bookkeeping resource is byte-for-byte unchanged; it already differs from the conceptual documentation source.
- Catalog regression tests: `python3 -m unittest discover -s scripts/tests -p test_accounting_catalog.py` — four passed (including the publication-time long-link regression test). Before implementation, two failed on missing navigation, as expected.
- OKF validation: `python3 scripts/generate-knowledge-catalog.py --check` — 123 concepts, zero problems.
- Executed the actual generator twice and compared the five affected accounting/domain-associated module entries byte-for-byte. They are stable. Restored unrelated generator output from pre-run bytes to avoid importing existing drift.
- Updated the accounting domain entry and module entries for `pos-accounting`, `pos-tax`, `pos-tax-common`, and `pos-domain-events` (the existing catalog infers their accounting association).
- `git diff --check` passed in both repositories. Application tests were not run: no application code, schema, or runtime resource changed.
- Independent review used the local Anvil instructions through a regular subagent. It separately verified preservation, links, catalog tests and conformance. It found a Jaccard correction attached to the wrong historical report; the correction was moved to the summary containing the example.

The full catalog had pre-existing regeneration drift before this work; this task verifies repeatability of the affected entries and OKF conformance, not whole-catalog freshness.

Publication verification found that `durion` uses `master` and has no `main` branch. Canonical links introduced by this reconciliation and domain-link generation now use `master`.

Publication checks: the generated catalog passes Markdown lint after long document links use reference definitions. Refreshed the existing ADR README table with its generator to resolve pre-existing formatting drift required by the PR gate; ADR and domain frontmatter checks now pass.
