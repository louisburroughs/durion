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
| `pos-inventory` | Table coverage closed | Residual work is column/type/constraint parity |
| `pos-workorder` | Table coverage closed | Residual work is column/type/constraint parity |
| `pos-accounting` | Improved | Core bootstrap prerequisites now created; additional column-level parity remains broad |
| `pos-catalog` | Table coverage closed | UUID migration still emits expected notices for legacy table names in clean bootstrap |
| `pos-customer` | Table coverage closed | Residual work is column/type/constraint parity |

## Detailed Gaps

### Current Residual Gaps

Table-coverage parity for explicitly mapped `@Table(name = ...)` entities is now closed for all Flyway-managed modules.

Remaining gaps to track in follow-on work:
- Column/type/constraint parity against entity mappings (especially `pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inventory`, `pos-workorder`).
- Shared ownership dependencies that are intentionally not created in-module:
  - `pos-location` depends on external/shared `location`.
  - `pos-people` depends on external/shared `person`.
  - `pos-shop-manager` references `shop`, `bay`, and `mobile_unit` as external/shared ownership in current chain.

## Impact to Seed Integration

Table-name contract blockers have been removed for covered modules. Remaining seed blocker scope is now mostly column-level contract alignment and data-shape constraints.

## Recommended Closure Order

1. Tighten column/type/constraint parity for high-drift modules (`pos-accounting`, `pos-catalog`, `pos-customer`, `pos-inventory`, `pos-workorder`).
2. Formalize/document external/shared ownership boundaries for `location`, `person`, `shop`/`bay`/`mobile_unit`.
3. Re-run seed generator contract alignment and complete Phase 5 seed migrations for all remaining modules.
