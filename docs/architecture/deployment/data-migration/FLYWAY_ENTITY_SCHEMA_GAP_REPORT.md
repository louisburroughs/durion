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
| `pos-inventory` | 5.2 baseline closed | Follow-on (5.3) type/constraint tightening remains |
| `pos-workorder` | 5.2 baseline closed | Follow-on (5.3) type/constraint tightening remains |
| `pos-accounting` | 5.2 baseline closed | Broad type/constraint tightening remains in follow-on (5.3) |
| `pos-catalog` | 5.2 baseline closed | UUID migration still emits expected clean-bootstrap notices; follow-on (5.3) tightening remains |
| `pos-customer` | 5.2 baseline closed | Follow-on (5.3) type/constraint tightening remains |

## Detailed Gaps

### Current Residual Gaps

Table-coverage parity for explicitly mapped `@Table(name = ...)` entities is now closed for all Flyway-managed modules.

Remaining gaps to track in follow-on (Phase 5.3) work:
- Column/type/constraint parity against entity mappings (especially `pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inventory`, `pos-workorder`).
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
