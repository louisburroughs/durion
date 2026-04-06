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
- [ ] Phase 5: Seed Flyway migration integration after cleanup guardrails and environment baselines.

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

## Concrete Next Patch Set (suggested PR sequence)

1. PR A: migration hygiene tooling + CI checks (read-only checks first). Completed.
2. PR B: add Flyway deps + set `ddl-auto=validate` for target modules. Completed.
3. PR C: repair `pos-accounting` version numbering. Completed.
4. PR D: introduce `pos-catalog` and `pos-customer` baselines. Completed.
5. PR E: add seed Flyway migrations generated from `scripts/seed-generator` outputs. Next.

## Risks and Mitigations

- Risk: Existing prod schema drift from intended baseline.
- Mitigation: schema diff + backup + staged baseline + dry-run migrate in clone.

- Risk: Renaming migrations after any environment has tracked them.
- Mitigation: only rename before Flyway adoption; otherwise add reconciliation migrations instead of renames.

- Risk: Data-changing migrations lock large tables.
- Mitigation: run during maintenance windows and split heavy operations.
