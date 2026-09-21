---
name: 'Complete Standalone-Id to JPA Relationship Autonomous Plan'
description: 'Execute the remaining standalone-id to JPA relationship migration autonomously in durion-positivity-backend until done criteria are met.'
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

1. Work only rows in the ledger's **Open DEFER rows** table. There is no `CONVERT_NOW` queue to
   refresh: every approved candidate is `DONE`, and `KEEP_SCALAR` rows are settled.
2. Do **not** modify Audit/Event entities. The five `pos-workorder` `DEFER` rows are event entities
   or point at one; the ledger treats them as `KEEP_SCALAR`, so they are never candidates.
3. Do **not** introduce cross-service/module JPA relationships.
4. Preserve API request/response scalar ID shapes unless explicitly approved.
5. Keep DB FK column names stable with `@JoinColumn(name = "...")`.
6. Prefer `@ManyToOne`; use `@OneToOne` only when uniqueness is truly enforced.
7. Keep implementation packages under `com.positivity.{domain}.internal...` except public service interfaces.
8. Repository access is allowed from service/DAO patterns (`..service..`, `..dao..`, `..internal.dao..`) per current ArchUnit rule.
9. `pos-event-receiver` DAO interface/implementation pattern is intentional; do not “normalize away” that pattern.

## Autonomous Execution Loop

Repeat until no open `DEFER` row has a resolved blocker:

1. Re-verify every open `DEFER` row against current source:
   - confirm the field is still scalar at the location the ledger cites,
   - test whether its stated blocker still holds (`TABLE_PER_CLASS` on `AbstractParty`, a composite
     `@IdClass` without `@MapsId`, the `CycleCountTask`/`CountEntry` cycle).
   A row whose blocker still holds stays `DEFER`; record the re-verification date in the ledger.
2. Pick the next batch from rows whose blocker has cleared:
   - one blocker per batch — rows deferred for the same reason convert together, because clearing
     the blocker is the work,
   - within a blocker, lowest dependency graph complexity and strongest available tests first.
3. Batch cap:
   - max 5 entities or 12 FK conversions.
4. Apply the ledger's "Converting a deferred row" recipe per row:
   - relationship field + `@JoinColumn` on existing FK column,
   - two-step dual mapping only when something outside the module still reads the scalar,
   - update repository derivations/JPQL to relation navigation,
   - update service/controller mappings while preserving scalar API contracts,
   - update relevant tests in same batch, including FK-safe teardown ordering.
5. Validation gate for each batch:
   - `./mvnw -pl <module> -DskipTests compile test-compile`
   - `./mvnw -pl <module> -Dtest=<focused_tests> test`
   - `./mvnw -pl <module> -DskipTests install`
   - `./mvnw -pl pos-archunit -Dtest=ArchitectureTests test`
6. If failures occur:
   - fix in same batch,
   - retry validation,
   - after 2 failed repair attempts leave the row `DEFER`, rewrite its "Why deferred" with what the
     attempt showed, and continue.
7. Batch closeout:
   - move each converted row from **Open DEFER rows** to `DONE` in the ledger's queue with evidence,
   - correct the remaining-deferred count and the "verified at" citations the ledger states.

## Completion Criteria

Stop only when all are true:

1. Every open `DEFER` row is either `DONE`, or still `DEFER` with a blocker re-verified as real in
   this run and stated in the ledger.
2. No cross-service JPA links were introduced.
3. All changed modules pass module validation and focused tests.
4. `pos-archunit` architecture tests pass.
5. Audit/Event entities remain untouched.

## Required Response Format

After each batch, report:

1. Batch scope (module, entities, fields, the blocker that cleared).
2. Exact files changed.
3. Exact test commands run + pass/fail.
4. Ledger updates (rows moved to `DONE`, rows re-verified as `DEFER`).
5. Remaining open `DEFER` rows, each with its blocker.
6. If blocked: concrete blocker + proposed mitigation.

## Working Directory

Primary repo for execution:
- `~/IdeaProjects/durion-positivity-backend`

Original prompt (archived when the wave finished):
- `~/IdeaProjects/durion/.github/agents/archive/prompts/complete-standalone-id-jpa-relationship-autonomous-plan.prompt.md`
