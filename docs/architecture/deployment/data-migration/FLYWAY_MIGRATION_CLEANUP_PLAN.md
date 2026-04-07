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
- [x] Phase 5: Seed Flyway migration integration after cleanup guardrails and environment baselines.
  - [x] Added module-owned repeatable seed migration for `pos-security-service` (`R__seed_reference_security.sql`) sourced from generated seed SQL and adapted to current module schema.
  - [x] Added module-owned repeatable seed migration for `pos-location` (`R__seed_reference_location.sql`) sourced from generated seed SQL (service areas, capabilities, travel buffers).
  - [x] Added module-owned repeatable seed migration for `pos-catalog` (`R__seed_reference_catalog.sql`) with catalog-owned reference seeds (`product`, `service`) only.
  - [x] Added module-owned repeatable seed migration for `pos-inventory` (`R__seed_reference_inventory.sql`) plus schema support migration `V11__add_approval_threshold_config_columns.sql` for inventory-owned seed shapes.
  - [x] Added module-owned repeatable seed migration for `pos-accounting` (`R__seed_reference_accounting.sql`) with schema-compatible default/audit and statement mapping values.
  - [x] Added module-owned repeatable seed migration for `pos-invoice` (`R__seed_reference_invoice.sql`) for billing rules.
  - [x] Added module-owned repeatable seed migration for `pos-people` (`R__seed_reference_people.sql`) with guarded cross-module inserts for external/shared ownership (`person`, `location`).
  - [x] Added `pos-price` Flyway baseline and module-owned repeatable seed migration:
    - `pos-price/src/main/resources/db/migration/V1__baseline_price_schema.sql`
    - `pos-price/src/main/resources/db/migration/R__seed_reference_price.sql` (`product_base_price`)
  - [x] Fresh empty-db smoke with Phase 5 seeds passed across all Flyway-managed modules including `pos-price` (`ALL_MODULE_SMOKE_PASS_PHASE_5_SEED_INTEGRATION_WITH_PRICE`) and `./scripts/check-flyway-hygiene.sh` passed.
  - [x] Catalog seed volume verification passed: `100` demo products and `20` demo services (`sku LIKE 'DEMO-PROD-%'`, `code LIKE 'DEMO-SVC-%'`).
  - [x] Price seed volume verification passed: `121` `product_base_price` rows in `pos-price` smoke database.
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
  - [x] Remaining parity work moved to follow-on scope: column-level/type/constraint alignment for all entity mappings in high-gap modules (tracked in Phase 5.3).
- [x] Phase 5.2: Column/type/constraint baseline parity pass (forward-only, module-by-module).
  - [x] `pos-customer`: added `V3__add_customer_column_parity_baseline.sql` with nullable/idempotent explicit entity-mapped columns across parity tables.
  - [x] `pos-inventory`: added `V10__add_inventory_column_parity_baseline.sql` with nullable/idempotent explicit entity-mapped columns across parity and operational tables.
  - [x] `pos-workorder`: added `V18__add_workorder_column_parity_baseline.sql` with nullable/idempotent explicit entity-mapped columns across parity and earlier chain tables.
  - [x] `pos-accounting`: added `V9__create_accounting_additional_parity_tables.sql` to create remaining entity tables with baseline columns.
  - [x] `pos-catalog`: added `V4__add_catalog_column_parity_baseline.sql` to baseline explicit entity-mapped columns for catalog tables.
  - [x] Full fresh bootstrap smoke passed across all Flyway-managed modules after 5.2 migrations (`ALL_MODULE_SMOKE_PASS_PHASE_5_2` with documented external/shared-owner stubs).
  - [x] `./scripts/check-flyway-hygiene.sh` passed after 5.2 migrations.
  - [-] Follow-on phase (5.3): non-null/default/index/constraint fidelity and precise type parity per entity mapping.
    - [x] Added 5.3 tightening migrations for high-drift modules:
      - `pos-accounting/V10__tighten_accounting_precision_parity.sql`
      - `pos-catalog/V5__tighten_catalog_precision_parity.sql`
      - `pos-customer/V4__tighten_customer_precision_parity.sql`
      - `pos-inventory/V12__tighten_inventory_precision_parity.sql`
      - `pos-workorder/V19__tighten_workorder_precision_parity.sql`
    - [x] Fresh empty-db smoke passed across all Flyway-managed modules with 5.3 migrations (`ALL_MODULE_SMOKE_PASS_PHASE_5_3`).
    - [x] `./scripts/check-flyway-hygiene.sh` passed with 5.3 migrations (`11` modules with migrations).
    - [x] Added follow-up 5.3 gap-closure migrations and revalidated smoke (`ALL_MODULE_SMOKE_PASS_PHASE_5_3_ITER2`):
      - `pos-accounting/V11__close_accounting_precision_gap_examples.sql`
      - `pos-catalog/V6__close_catalog_precision_gap_examples.sql`
      - `pos-customer/V5__close_customer_precision_gap_examples.sql`
      - `pos-inventory/V13__close_inventory_precision_gap_examples.sql`
      - `pos-workorder/V20__close_workorder_precision_gap_examples.sql`
    - [x] 5.3 migration-contract verification against smoke DBs passed for all targeted modules:
      - `pos-accounting`: `NOT NULL 52/52`, `DEFAULT 24/24`, `INDEX 7/7`
      - `pos-catalog`: `NOT NULL 23/23`, `DEFAULT 14/14`, `INDEX 3/3`
      - `pos-customer`: `NOT NULL 27/27`, `DEFAULT 16/16`, `INDEX 3/3`
      - `pos-inventory`: `NOT NULL 30/30`, `DEFAULT 16/16`, `INDEX 4/4`
      - `pos-workorder`: `NOT NULL 24/24`, `DEFAULT 2/2`, `INDEX 5/5`
    - [ ] Remaining: module-by-module formal entity-vs-schema diff closure evidence and any additional precision/constraint deltas.

### Phase 5.3: Precision Parity Tightening (Planned)

Goal:
- Move from baseline parity to exact entity-to-Flyway fidelity for nullability, defaults, type precision/scale/length, indexes, and constraints.

Execution order:
1. `pos-accounting`
2. `pos-catalog`
3. `pos-customer`
4. `pos-inventory`
5. `pos-workorder`
6. Remaining Flyway modules as needed from drift findings

Per-module required steps:
- [ ] Generate entity-vs-Flyway diff for columns and constraints (source scan + database introspection from fresh bootstrap DB).
- [x] Add forward-only migration(s) to align:
  - nullability (`NOT NULL`/nullable)
  - defaults
  - data types and numeric precision/scale
  - varchar lengths
  - check constraints and enum/value-domain constraints
  - PK/UK/FK constraints
  - required indexes (including uniqueness and composite order)
- [x] Ensure external/shared-owner boundaries remain documented and unchanged (`location`, `person`, `shop`/`bay`/`mobile_unit`).
- [x] Re-run module seed repeatables after constraint tightening and resolve any seed/data-shape conflicts.
- [x] Re-run full empty-db smoke across all Flyway-managed modules.
- [x] Re-run `./scripts/check-flyway-hygiene.sh`.
- [x] Update `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md` with module-by-module closure notes.

Phase 5.3 completion gates:
1. High-drift modules (`pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inventory`, `pos-workorder`) have no unresolved precision parity gaps.
2. All-module smoke bootstrap passes with repeatables applied.
3. Flyway hygiene passes in CI.
4. Cleanup plan and gap report both show 5.3 closed.

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
- `pos-catalog/src/main/resources/db/migration/R__seed_reference_catalog.sql`
- `pos-inventory/src/main/resources/db/migration/R__seed_reference_inventory.sql`
- `pos-accounting/src/main/resources/db/migration/R__seed_reference_accounting.sql`
- `pos-invoice/src/main/resources/db/migration/R__seed_reference_invoice.sql`
- `pos-people/src/main/resources/db/migration/R__seed_reference_people.sql`
- `pos-price/src/main/resources/db/migration/V1__baseline_price_schema.sql`
- `pos-price/src/main/resources/db/migration/R__seed_reference_price.sql`
- Executed fresh empty-db smoke with external/shared-owner stubs and all phase-5 repeatables applied in-module order (`ALL_MODULE_SMOKE_PASS_PHASE_5_SEED_INTEGRATION_WITH_PRICE`).
- `./scripts/check-flyway-hygiene.sh` passed after seed integration (`11` modules with migrations).

Schema parity dependency:
- Entity/Flyway table-coverage and 5.2 column-baseline parity dependencies are satisfied; remaining follow-on is precision parity tightening in Phase 5.3.

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
5. PR E: add seed Flyway migrations generated from `scripts/seed-generator` outputs. Completed.
6. PR F: close entity/Flyway schema gaps per `FLYWAY_ENTITY_SCHEMA_GAP_REPORT.md`. Completed.
7. PR G: finish module-owned seed Flyway integration for all remaining modules after PR F. Completed (including `pos-price` ownership for `product_base_price`).
8. PR H: production rollout reconciliation for modified historical migrations (Flyway checksum repair or forward-only compensating migration strategy). Pending follow-on.
9. PR I: column/type/constraint parity pass (phase 5.2), starting with `pos-customer` and proceeding module-by-module. Completed.

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
