# Flyway vs Entity Schema Gap Report

Date: 2026-04-06
Scope: `durion-positivity-backend` modules in Phase 1-5 cleanup plan.

Update (later 2026-04-06):
- `pos-invoice` added `V2__create_billing_and_payment_tables.sql` to cover missing entity tables.
- `pos-catalog` added `V3__create_catalog_core_schema.sql` to provide real empty-db table creation.
- `pos-location` added `V10__create_location_type_table.sql` to cover missing `location_type`.
- `pos-people` added `V10__create_timekeeping_policy_and_person_location_assignment.sql` to cover missing entity tables.
- `pos-accounting` patched foundational migrations (`V2`, `V3`, `V5`) to create core prerequisites (`gl_account`, `posting_category`, `mapping_key`, `gl_mapping`, `invoice_status_views`) for fresh Flyway bootstrap.
- `pos-customer` added `V2__create_customer_entity_parity_tables.sql` for previously missing party/contact/projection tables.
- `pos-inventory` added `V9__create_inventory_entity_parity_tables.sql` for ASN/PO/reservation/pick/return and related parity tables.
- `pos-workorder` added `V17__create_workorder_entity_parity_tables.sql` for approval/audit/snapshot/substitute/state transition parity tables.
- `pos-shop-manager` added `V17__create_shop_audit_entry_table.sql`.
- `pos-security-service` added `V10__create_security_entity_parity_tables.sql` for residual mapped entity tables.
- Bootstrap blockers were patched in historical migrations (`pos-inventory` V7/V8, `pos-workorder` V1/V3-V5/V7-V9/V13-V16) plus PostgreSQL type compatibility (`VARCHAR2` -> `VARCHAR`) in `pos-invoice` V1 and `pos-location` V2.
- Static scan now shows full `@Table` table-name coverage across Flyway-managed modules; remaining gaps are primarily column/type/constraint parity.
- Phase 5.2 kickoff: `pos-customer` added `V3__add_customer_column_parity_baseline.sql` to introduce nullable/idempotent explicit entity-mapped columns for customer parity tables.
- `pos-inventory` added `V10__add_inventory_column_parity_baseline.sql` to introduce nullable/idempotent explicit entity-mapped columns for inventory parity + operational tables.
- `pos-workorder` added `V18__add_workorder_column_parity_baseline.sql` to introduce nullable/idempotent explicit entity-mapped columns for workorder parity + core operational tables.
- `pos-accounting` added `V9__create_accounting_additional_parity_tables.sql` to close remaining table-name parity and baseline key entity columns.
- `pos-catalog` added `V4__add_catalog_column_parity_baseline.sql` for explicit entity-mapped column baseline across catalog tables.
- Full 5.2 validation run succeeded across all Flyway-managed modules (`ALL_MODULE_SMOKE_PASS_PHASE_5_2`) and Flyway hygiene checks passed.
- Phase 5 seed integration completed for module-owned repeatables across `pos-security-service`, `pos-location`, `pos-catalog`, `pos-inventory`, `pos-accounting`, `pos-invoice`, and `pos-people`.
- Post-seed smoke rerun succeeded (`ALL_MODULE_SMOKE_PASS_PHASE_5_SEED_INTEGRATION`) and catalog seed volume check validated `100` demo products + `20` demo services.
- `pos-price` was brought under Flyway with `V1__baseline_price_schema.sql`, plus `R__seed_reference_price.sql` for `product_base_price`; all-module smoke then passed including `pos-price` (`ALL_MODULE_SMOKE_PASS_PHASE_5_SEED_INTEGRATION_WITH_PRICE`) with `121` base-price rows seeded.
- Phase 5.3 execution pass added precision-tightening migrations for high-drift modules:
  - `pos-accounting/V10__tighten_accounting_precision_parity.sql`
  - `pos-catalog/V5__tighten_catalog_precision_parity.sql`
  - `pos-customer/V4__tighten_customer_precision_parity.sql`
  - `pos-inventory/V12__tighten_inventory_precision_parity.sql`
  - `pos-workorder/V19__tighten_workorder_precision_parity.sql`
- Full smoke rerun with 5.3 migrations passed (`ALL_MODULE_SMOKE_PASS_PHASE_5_3`) and Flyway hygiene checks remained green.
- Migration-contract verification against smoke databases confirms all 5.3-enforced constraints were applied:
  - `pos-accounting`: `NOT NULL 52/52`, `DEFAULT 24/24`, `INDEX 7/7`
  - `pos-catalog`: `NOT NULL 23/23`, `DEFAULT 14/14`, `INDEX 3/3`
  - `pos-customer`: `NOT NULL 27/27`, `DEFAULT 16/16`, `INDEX 3/3`
  - `pos-inventory`: `NOT NULL 30/30`, `DEFAULT 16/16`, `INDEX 4/4`
  - `pos-workorder`: `NOT NULL 24/24`, `DEFAULT 2/2`, `INDEX 5/5`
- 5.3 follow-up pass landed additional gap-closure migrations (`V11` accounting, `V6` catalog, `V5` customer, `V13` inventory, `V20` workorder) and fresh smoke passed (`ALL_MODULE_SMOKE_PASS_PHASE_5_3_ITER2`).
- Final 5.3 pass landed remaining-column closure migrations:
  - `pos-accounting/V12__add_accounting_remaining_entity_columns.sql` (`1` column)
  - `pos-catalog/V7__add_catalog_remaining_entity_columns.sql` (`57` columns)
  - `pos-inventory/V14__add_inventory_remaining_entity_columns.sql` (`114` columns)
  - `pos-workorder/V21__add_workorder_remaining_entity_columns.sql` (`91` columns)
- Fresh all-module smoke rerun passed with final 5.3 migrations (`ALL_MODULE_SMOKE_PASS_PHASE_5_3_ITER3`), and Flyway hygiene checks remained green.
- Final lightweight entity-vs-schema scan snapshot (explicit mapped columns tracked in 5.3 high-drift scope):
  - `pos-accounting`: `ENTITY_COL_EXISTS 351/351`
  - `pos-catalog`: `ENTITY_COL_EXISTS 113/113`
  - `pos-customer`: `ENTITY_COL_EXISTS 97/97`
  - `pos-inventory`: `ENTITY_COL_EXISTS 290/290`
  - `pos-workorder`: `ENTITY_COL_EXISTS 215/215`

## Method

This report compares, per module:

- JPA entity table names from `@Table(name = "...")`
- Flyway tables created by `CREATE TABLE ...` in `src/main/resources/db/migration`
- Flyway referenced tables from `ALTER TABLE ...` for dependency checks

This is a static source scan and does not include runtime DB introspection.

## Summary

| Module | Status | Key Gap |
|---|---|---|
| `pos-security-service` | Table coverage closed | Residual work is column/type/constraint parity |
| `pos-shop-manager` | Table coverage closed | External/shared tables (`shop`, `bay`, `mobile_unit`) are still referenced in-chain |
| `pos-location` | Table coverage closed | External/shared `location` dependency remains by design |
| `pos-people` | Table coverage closed | External/shared `person` dependency remains by design |
| `pos-invoice` | Table coverage closed | Residual work is column/type/constraint parity |
| `pos-inventory` | 5.3 closed for high-drift scope | Remaining residuals are only documented external/shared ownership boundaries |
| `pos-workorder` | 5.3 closed for high-drift scope | Remaining residuals are only documented external/shared ownership boundaries |
| `pos-accounting` | 5.3 closed for high-drift scope | Remaining residuals are only documented external/shared ownership boundaries |
| `pos-catalog` | 5.3 closed for high-drift scope | UUID migration still emits expected clean-bootstrap notices; no unresolved high-drift column-existence gaps |
| `pos-customer` | 5.3 closed for high-drift scope | Remaining residuals are only documented external/shared ownership boundaries |

## Detailed Gaps

### Current Residual Gaps

Table-coverage parity for explicitly mapped `@Table(name = ...)` entities is now closed for all Flyway-managed modules.

Remaining residuals after 5.3 closure:
- Shared ownership dependencies that are intentionally not created in-module:
  - `pos-location` depends on external/shared `location`.
  - `pos-people` depends on external/shared `person`.
  - `pos-shop-manager` references `shop`, `bay`, and `mobile_unit` as external/shared ownership in current chain.

## Impact to Seed Integration

Table-name contract blockers have been removed for covered modules and module-owned Phase 5 seed repeatables are integrated.
Remaining seed follow-on scope is column/type/constraint tightening in Phase 5.3.

## Recommended Closure Order

1. Tighten column/type/constraint parity for high-drift modules (`pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inventory`, `pos-workorder`).
2. Formalize/document external/shared ownership boundaries for `location`, `person`, `shop`/`bay`/`mobile_unit`.
3. Keep generator-to-target mapping alignment under CI/contract validation as schema precision work proceeds in 5.3.
