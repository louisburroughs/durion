# Flyway Migration Cleanup Plan (Long-Term Seed Strategy Prerequisite)

## Execution Progress (2026-04-06)

Status key: `[x]` completed, `[-]` in progress, `[ ]` pending

- [x] Phase 1.1: Added Flyway dependencies to target modules (`pos-accounting`, `pos-catalog`, `pos-inventory`, `pos-invoice`, `pos-location`, `pos-people`, `pos-shop-manager`, `pos-workorder`) and added PostgreSQL Flyway support to `pos-customer`.
- [x] Phase 1.2: Updated target module runtime `application.yml` files from `spring.jpa.hibernate.ddl-auto=update` to `validate`.
- [x] Phase 2.1: Repaired `pos-accounting` duplicate version graph by renumbering migrations to ordered `V1..V8`.
- [x] Phase 2.2: Replaced `pos-catalog` `V999`-only state with `V1__baseline_catalog_schema.sql` and `V2__migrate_to_uuid_v7.sql`; removed verification `SELECT` blocks from migration execution.
- [x] Phase 2.3: Added `pos-customer` baseline migration `V1__baseline_customer_schema.sql`.
- [x] Phase 4: Added CI guardrails script (`scripts/check-flyway-hygiene.sh`) and wired it into CI workflow (`.github/workflows/ci.yml`).
- [x] Phase 3 decision recorded: current databases are empty, so baseline/reconciliation is skipped and clean `flyway migrate` from `V1` is required.
- [-] Phase 5: Seed Flyway migration integration after cleanup guardrails and environment baselines.
  - [x] Added module-owned repeatable seed migration for `pos-security-service` (`R__seed_reference_security.sql`) sourced from generated seed SQL and adapted to current module schema.
  - [x] Added module-owned repeatable seed migration for `pos-location` (`R__seed_reference_location.sql`) sourced from generated seed SQL (service areas, capabilities, travel buffers).
  - [ ] Remaining modules pending schema-aligned seed integration (`pos-catalog`, `pos-inventory`, `pos-accounting`, `pos-invoice`, `pos-people`) where generated SQL currently references tables/columns not yet represented in Flyway baseline chains.
- [x] Phase 5.1: Entity/Flyway table-coverage parity closure for all Flyway-managed modules (see `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md`).
  - [x] Added `pos-invoice` migration `V2__create_billing_and_payment_tables.sql` to cover entity tables `billing_rules`, `payment_intents`, `receipts`, and `refund_records`.
  - [x] Added `pos-catalog` migration `V3__create_catalog_core_schema.sql` to replace marker-only bootstrap behavior with core table creation for empty databases.
  - [x] Added `pos-location` migration `V10__create_location_type_table.sql` to cover missing `location_type` entity table.
  - [x] Added `pos-people` migration `V10__create_timekeeping_policy_and_person_location_assignment.sql` to cover missing `timekeeping_policy` and `person_location_assignment` entity tables.
  - [x] Patched `pos-accounting` foundational migrations (`V2`, `V3`, `V5`) to add core table creation/idempotency for fresh bootstrap (`gl_account`, `posting_category`, `mapping_key`, `gl_mapping`, `invoice_status_views` prerequisites).
  - [x] Added remaining parity migrations for unresolved modules:
    - `pos-customer/V2__create_customer_entity_parity_tables.sql`
    - `pos-inventory/V9__create_inventory_entity_parity_tables.sql`
    - `pos-workorder/V17__create_workorder_entity_parity_tables.sql`
    - `pos-shop-manager/V17__create_shop_audit_entry_table.sql`
    - `pos-security-service/V10__create_security_entity_parity_tables.sql`
  - [x] Patched bootstrap blockers in historical migration chains:
    - `pos-inventory` `V7`/`V8` guarded against absent pre-parity tables.
    - `pos-workorder` V-series bootstrap hardening (`V1`, `V3`-`V5`, `V7`-`V9`, `V13`-`V16`) plus PostgreSQL type fix (`VARCHAR2` -> `VARCHAR` in `V1`).
    - PostgreSQL compatibility fixes: `pos-location/V2` and `pos-invoice/V1` (`VARCHAR2` -> `VARCHAR`).
  - [x] Executed fresh Postgres smoke bootstrap for all Flyway-managed modules (`pos-security-service`, `pos-location`, `pos-people`, `pos-invoice`, `pos-catalog`, `pos-accounting`, `pos-customer`, `pos-inventory`, `pos-shop-manager`, `pos-workorder`) with migration chains applying in Flyway order.
  - [x] `./scripts/check-flyway-hygiene.sh` passed after parity closure.
  - [ ] Remaining parity work moved to follow-on scope: column-level/type/constraint alignment for all entity mappings in high-gap modules.

## Why This First

Before adding long-term seed SQL as Flyway-managed migrations, backend services must have a clean, consistent migration strategy.

Historical pre-cleanup snapshot (from `durion-positivity-backend`, before phases 1-4):
- Many modules had `src/main/resources/db/migration` files but did not include Flyway dependencies.
- Most modules ran `spring.jpa.hibernate.ddl-auto=update` in runtime config.
- `pos-accounting` had duplicate Flyway version numbers (`V1__` used three times).
- `pos-catalog` had only `V999__...` and no baseline migration chain.
- `pos-customer` included Flyway dependency but had no migration scripts.

## Inventory Summary (Pre-Cleanup Baseline)

| Module | Flyway dep in `pom.xml` | `db/migration` files | Runtime `ddl-auto` |
|---|---|---:|---|
| `pos-accounting` | no | 8 | `update` |
| `pos-catalog` | no | 1 | `update` |
| `pos-customer` | yes | 0 | `update` |
| `pos-inventory` | no | 8 | `update` |
| `pos-invoice` | no | 1 | `update` |
| `pos-location` | no | 9 | `update` |
| `pos-people` | no | 9 | `update` |
| `pos-security-service` | yes | 9 | `validate` |
| `pos-shop-manager` | no | 16 | `update` |
| `pos-workorder` | no | 16 | `update` |

## Target End State

1. Every DB-owning service uses Flyway migrations as the schema source of truth.
2. Runtime `ddl-auto` is `validate` (or `none`) for persistent environments.
3. No duplicate migration versions in any module.
4. Each module has an explicit baseline + forward chain.
5. Seed SQL can be added as versioned/repeatable migrations safely.

## Phase 1: Standardize Migration Engine and Runtime Schema Policy

### 1.1 Add Flyway dependencies to DB-owning modules

Add in each module `pom.xml` (except where already present):
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-postgresql`

Modules to add now:
- `pos-accounting`, `pos-catalog`, `pos-inventory`, `pos-invoice`, `pos-location`, `pos-people`, `pos-shop-manager`, `pos-workorder`

### 1.2 Standardize runtime JPA schema mode

For persistent/non-test profiles, set:
- `spring.jpa.hibernate.ddl-auto=validate`

Keep `update`/`create-drop` only for explicit local OpenAPI/test profile overrides.

## Phase 2: Repair Broken Migration Graphs

### 2.1 `pos-accounting` duplicate `V1__` versions (must fix before enabling Flyway)

Current files:
- `V1__create_bill_number_sequence.sql`
- `V1__create_default_gl_mapping_table.sql`
- `V1__create_statement_line_mappings.sql`
- `V2__create_event_outbox_table.sql`
- `V3__add_payment_outcome_tables.sql`
- `V4__add_accounting_status_columns.sql`
- `V5__add_discrepancy_columns.sql`
- `V6__create_accounting_status_sync_audit.sql`

Proposed renumber map (pre-Flyway enablement):
- `V1__create_bill_number_sequence.sql` -> `V1__create_bill_number_sequence.sql` (keep)
- `V1__create_default_gl_mapping_table.sql` -> `V2__create_default_gl_mapping_table.sql`
- `V1__create_statement_line_mappings.sql` -> `V3__create_statement_line_mappings.sql`
- `V2__create_event_outbox_table.sql` -> `V4__create_event_outbox_table.sql`
- `V3__add_payment_outcome_tables.sql` -> `V5__add_payment_outcome_tables.sql`
- `V4__add_accounting_status_columns.sql` -> `V6__add_accounting_status_columns.sql`
- `V5__add_discrepancy_columns.sql` -> `V7__add_discrepancy_columns.sql`
- `V6__create_accounting_status_sync_audit.sql` -> `V8__create_accounting_status_sync_audit.sql`

Important:
- Do this only before Flyway is used in shared/persistent environments for this module.
- If any env already has partial migration history, use baseline/reconciliation instead of simple rename.

### 2.2 `pos-catalog` baseline gap (`V999__...` only)

Current state:
- Only `V999__migrate_to_uuid_v7.sql` exists.

Cleanup strategy:
1. Create `V1__baseline_catalog_schema.sql` from current intended schema.
2. Rework UUID migration to the next sequential migration (`V2__...`) if still required.
3. Remove verification `SELECT` blocks from migration files (or isolate in runbooks/tests, not migration execution).
4. Ensure migration is deterministic and transactional (avoid partially commented manual steps).

### 2.3 `pos-customer` Flyway dependency but no migrations

Create initial baseline:
- `V1__baseline_customer_schema.sql`

Then lock runtime to `ddl-auto=validate`.

## Phase 3: Baseline Existing Persistent EC2 Databases Safely

For each service database already created via Hibernate `update`, baseline before `migrate`.

### 3.0 Current environment state (2026-04-06)

Current deployment databases are empty.

Implication:
- Skip baseline reconciliation flow (`baselineOnMigrate=true`) for this environment.
- Run standard `flyway migrate` from `V1` upward for each service database.
- Keep runtime `spring.jpa.hibernate.ddl-auto=validate`.

Follow-up requirement for empty DB bootstrap:
- Ensure each Flyway-managed module has a true schema-creating baseline/forward chain for fresh database creation.
- Marker-only baseline files (for example, `SELECT 1`) are acceptable for reconciliation scenarios but not sufficient by themselves to build an empty database schema.

### 3.1 Baseline approach

Per database/service:
1. Backup database.
2. Verify baseline script matches actual table/index constraints in that database.
3. Run Flyway with:
- `baselineOnMigrate=true`
- `baselineVersion=1` (or module-specific baseline version)
4. Run `flyway migrate`.

### 3.2 Service-by-service rollout order

Recommended:
1. `pos-security-service` (already closest to target)
2. `pos-location`, `pos-people`
3. `pos-inventory`, `pos-invoice`
4. `pos-shop-manager`, `pos-workorder`
5. `pos-accounting`
6. `pos-catalog`
7. `pos-customer`

Rationale:
- Start from stable and foundational modules, then move to modules with migration graph anomalies.

## Phase 4: CI Guardrails (Required Before Seed Migration Work)

Add checks that fail PRs when:
1. Duplicate migration versions exist in a module.
2. A module has `db/migration` files but no Flyway dependency.
3. Non-test runtime config uses `ddl-auto=update` for Flyway-managed modules.
4. Migration filename pattern deviates from `V<integer>__<description>.sql` (or approved `R__...`).

## Phase 5: Seed Integration After Cleanup

After phases 1-4:
1. Put generated preload SQL under Flyway (preferred by module ownership, not a single DB script).
2. Use versioned migrations for one-time bootstrap data and repeatables for reference sync only where safe.
3. Keep generated SQL idempotent with explicit natural-key `ON CONFLICT` rules.

Execution update (2026-04-06):
- Implemented repeatable (`R__...`) Flyway seed migrations for modules with schema-compatible generated output:
  - `pos-security-service/src/main/resources/db/migration/R__seed_reference_security.sql`
  - `pos-location/src/main/resources/db/migration/R__seed_reference_location.sql`
- Deferred remaining module integrations until generator output is aligned with module Flyway table/column contracts.

Schema parity dependency:
- Full Phase 5 completion now depends on closing entity-to-Flyway table coverage gaps documented in `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md`.

### Phase 5.1: Close Entity/Flyway Gaps

Goal:
- Ensure each Flyway-managed module has an explicit, deterministic migration chain that can create all tables required by its current JPA entity model (or intentionally documented external/shared dependencies).

Required actions by module:
- [x] `pos-catalog`: marker-only baseline replaced with schema-creating forward chain (`V3`).
- [x] `pos-customer`: introduced schema forward chain coverage (`V2`) for customer/person/contact/projection entity tables.
- [x] `pos-accounting`: foundational missing DDL coverage added (`gl_account`, `posting_category`, `mapping_key`, `gl_mapping`, `invoice_status_views` prerequisites).
- [x] `pos-invoice`: missing DDL for `billing_rules`, `payment_intents`, `receipts`, `refund_records` added (`V2`).
- [x] `pos-people`: missing DDL for `timekeeping_policy` and `person_location_assignment` added (`V10`).
- [x] `pos-people`: external/shared ownership boundary validated during smoke (`person` table stub required for standalone chain execution).
- [x] `pos-location`: missing `location_type` DDL added (`V10`).
- [x] `pos-location`: external/shared ownership boundary validated during smoke (`location` table stub required for standalone chain execution).
- [x] `pos-inventory`: missing DDL for ASN/PO/allocation/reservation/pick/return/approval-threshold tables added (`V9`).
- [x] `pos-workorder`: missing DDL for estimate/approval/snapshot/substitute/state tables added (`V17`), with bootstrap hardening in earlier migrations.
- [x] `pos-shop-manager`: missing `shop_audit_entry` DDL added (`V17`).
- [x] `pos-security-service`: residual entity-only tables closed (`V10`).

Validation gates for Phase 5.1 completion:
1. Per-module table coverage check: every `@Table(name=...)` is either:
   - created in module Flyway chain, or
   - explicitly mapped as external/shared ownership in module docs.
2. `./scripts/check-flyway-hygiene.sh` passes.
3. Fresh empty database bootstrap succeeds with `flyway migrate` for all Flyway-managed modules (with explicit external/shared-owner table stubs for modules that alter external tables: `location`, `person`, `shop`/`bay`/`mobile_unit`).
4. Seed generator output-to-target-table mapping is validated against Flyway schemas, then Phase 5 seed migrations are completed module-by-module.

Execution note (2026-04-06):
- Phase 5.1 table-coverage closure is complete.
- Additional parity migrations now landed for `pos-location` and `pos-people`.
- Additional foundational parity patch landed for `pos-accounting` migration chain to remove fresh-bootstrap dependency failures.
- Remaining module parity landed: `pos-customer`, `pos-inventory`, `pos-workorder`, `pos-shop-manager`, and `pos-security-service`.
- Fresh empty-db smoke bootstrap succeeded across all Flyway-managed modules using Flyway-ordered execution; standalone smoke included explicit external/shared-owner stubs where required (`location`, `person`, `shop`/`bay`/`mobile_unit`).
- Flyway hygiene script passed after parity closure.
- Follow-on work is now column/type/constraint tightening to full entity-level parity.

Migration history caution:
- Because some parity fixes are in existing versioned files (`pos-accounting` V2/V3/V5), environments that already executed those versions may require explicit Flyway checksum reconciliation (`repair`) or compensating forward migrations depending on rollout policy.

## Concrete Next Patch Set (suggested PR sequence)

1. PR A: migration hygiene tooling + CI checks (read-only checks first). Completed.
2. PR B: add Flyway deps + set `ddl-auto=validate` for target modules. Completed.
3. PR C: repair `pos-accounting` version numbering. Completed.
4. PR D: introduce `pos-catalog` and `pos-customer` baselines. Completed.
5. PR E: add seed Flyway migrations generated from `scripts/seed-generator` outputs. In progress (security + location landed; blocked by schema parity gaps for remaining modules).
6. PR F: close entity/Flyway schema gaps per `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md`.
7. PR G: finish module-owned seed Flyway integration for all remaining modules after PR F.
8. PR H: production rollout reconciliation for modified historical migrations (Flyway checksum repair or forward-only compensating migration strategy).

## Plan Closeout Checklist

This cleanup plan is complete when all items below are true:

1. All modules in `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md` are either:
   - covered by explicit Flyway table creation for current entity mappings, or
   - have explicit documented external/shared ownership for referenced tables.
2. `./scripts/check-flyway-hygiene.sh` passes in CI.
3. Fresh empty-db smoke bootstrap passes for every Flyway-managed module.
4. Seed Flyway migrations are integrated module-by-module (Phase 5 complete).
5. Rollout note for environments with existing Flyway history is documented and approved (`repair` vs compensating forward migrations).

## Risks and Mitigations

- Risk: Existing prod schema drift from intended baseline.
- Mitigation: schema diff + backup + staged baseline + dry-run migrate in clone.

- Risk: Renaming migrations after any environment has tracked them.
- Mitigation: only rename before Flyway adoption; otherwise add reconciliation migrations instead of renames.

- Risk: Data-changing migrations lock large tables.
- Mitigation: run during maintenance windows and split heavy operations.
