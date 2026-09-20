---
name: jpa-plan
description: Work the deferred remainder of the standalone-id to JPA relationship migration in durion-positivity-backend. The CONVERT_NOW wave is complete; this covers DEFER rows whose blocker has cleared, and the KEEP_SCALAR rule for columns that cross a service boundary.
---

# Complete Standalone-Id to JPA Relationship Autonomous Plan

## Objective

The CONVERT_NOW wave of this migration is **complete** (execution finished 2026-03-10). What remains
is the deferred set, and the standing rule for new entities.

Work from the per-field disposition ledger:
- `durion/docs/architecture/deployment/data-migration/ENTITY_RELATIONSHIP_MIGRATION_LEDGER.md`

It records, per column, what converted, what must stay scalar because it crosses a service boundary
(`KEEP_SCALAR` — never convert these), and what is deferred with the blocker that defers it
(`DEFER` — TABLE_PER_CLASS inheritance, composite `@IdClass` needing `@MapsId`, cyclic teardown).
It also carries the two-step dual-mapping recipe and the per-entity conversion checklist.

Take a `DEFER` row only when its stated blocker is actually resolved. The pre-migration candidate
inventory and the two execution plans were retired once the wave finished; git history holds them.

## Critical Rules

1. Migrate only **Core Entries** candidates.
2. Do **not** modify Audit/Event entities in this phase.
3. Do **not** introduce cross-service/module JPA relationships.
4. Preserve API request/response scalar ID shapes unless explicitly approved.
5. Keep DB FK column names stable with `@JoinColumn(name = "...")`.
6. Prefer `@ManyToOne`; use `@OneToOne` only when uniqueness is truly enforced.
7. Keep implementation packages under `com.positivity.{domain}.internal...` except public service interfaces.
8. Repository access is allowed from service/DAO patterns (`..service..`, `..dao..`, `..internal.dao..`) per current ArchUnit rule.
9. `pos-event-receiver` DAO interface/implementation pattern is intentional; do not “normalize away” that pattern.

## Autonomous Execution Loop

Repeat until no `CONVERT_NOW` candidates remain:

1. Refresh queue classification (`CONVERT_NOW`, `KEEP_SCALAR`, `DEFER`) from current source state.
2. Pick next batch by:
   - highest `CONVERT_NOW` count,
   - lowest dependency graph complexity,
   - strongest available tests.
3. Batch cap:
   - max 5 entities or 12 FK conversions.
4. Apply conversion recipe per candidate:
   - relationship field + `@JoinColumn` on existing FK column,
   - compatibility scalar accessor if needed,
   - update repository derivations/JPQL to relation navigation,
   - update service/controller mappings while preserving scalar API contracts,
   - update relevant tests in same batch.
5. Validation gate for each batch:
   - `./mvnw -pl <module> -DskipTests compile test-compile`
   - `./mvnw -pl <module> -Dtest=<focused_tests> test`
   - `./mvnw -pl <module> -DskipTests install`
   - `./mvnw -pl pos-archunit -Dtest=ArchitectureTests test`
6. If failures occur:
   - fix in same batch,
   - retry validation,
   - after 2 failed repair attempts mark candidate `DEFER` with explicit reason and continue.
7. Batch closeout:
   - update the row's disposition and evidence in `ENTITY_RELATIONSHIP_MIGRATION_LEDGER.md`.

## Completion Criteria

Stop only when all are true:

1. Every approved same-module Core candidate is `DONE` or explicitly `DEFER` with rationale.
2. No cross-service JPA links were introduced.
3. All changed modules pass module validation and focused tests.
4. `pos-archunit` architecture tests pass.
5. Audit/Event entities remain untouched.

## Required Response Format

After each batch, report:

1. Batch scope (module, entities, fields).
2. Exact files changed.
3. Exact test commands run + pass/fail.
4. Queue/status updates.
5. Remaining `CONVERT_NOW` items.
6. If blocked: concrete blocker + proposed mitigation.

## Working Directory

Primary repo for execution:
- `~/IdeaProjects/durion-positivity-backend`

Prompt file location:
- `~/IdeaProjects/durion/.github/prompts/complete-standalone-id-jpa-relationship-autonomous-plan.prompt.md`
