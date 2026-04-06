# Seed Input Contract (`seed-input.yaml`)

## Purpose

This contract defines the user-supplied input required to generate startup preload SQL for Durion backend modules.

The generator should combine:

- Derived data from source-of-truth code/config (permissions manifests, event type registries, framework defaults)
- Required business-context input from this file
- Optional faker-generated demo data (for non-production profiles)

## Profiles

- `profile: prod-minimal`
- `profile: staging-demo`

Rules:

- `prod-minimal` must avoid synthetic identities and fake business transactions unless explicitly allowed.
- `staging-demo` may generate optional demo users/customers/orders/invoices using faker.

## Derivation Rules (No Manual Input Required)

The SQL generator should derive these datasets automatically:

- `permissions` from all `pos-*/src/main/resources/permissions.yaml`
- `permissions.bit_index` from `PermissionCode` enum mappings when present
- `event_type` from `*EventTypes` registries / `EventTypeRegistration` declarations
- baseline roles from startup defaults and Flyway role seeds
- accounting defaults for `override_policy_threshold` and `refund_policy_config` when omitted

User input should not duplicate these unless overriding is explicitly supported.

## Required Input (Needs Your Values)

### 1) Tenant + Environment

- `tenant.id` (UUID)
- `tenant.code` (string)
- `tenant.name` (string)
- `environment.name` (e.g., `alpha`, `staging`, `prod`)
- `environment.default_timezone` (IANA zone)
- `environment.currency` (ISO 4217)

### 2) Security Bootstrap

- At least one admin user in `security.users`
- Role assignments in `security.role_assignments`
- Optional explicit `security.role_permission_overrides` only when deviating from defaults

### 3) Location Topology

- `locations.location_types`
- `locations.sites`
- `locations.service_areas`
- `locations.capabilities`
- `locations.travel_buffer_policies`

### 4) People Mapping

- `people.persons`
- `people.user_person_links`
- `people.person_location_assignments`
- `people.timekeeping_policies` (at least one GLOBAL policy)

### 5) Accounting Reference

- `accounting.gl_accounts`
- `accounting.posting_categories`
- `accounting.mapping_keys`
- `accounting.gl_mappings`
- `accounting.default_gl_mappings`
- `accounting.statement_line_mappings`

### 6) Commercial Baseline

- `catalog.products` and/or `catalog.services`
- `pricing.base_prices`
- `inventory.replenishment_policies`
- `inventory.putaway_rules`
- `inventory.approval_thresholds`
- `invoice.billing_rules`

## Optional Input (Can Be Faker Generated)

- `demo.users`
- `demo.customers`
- `demo.vehicles`
- `demo.orders`
- `demo.invoices`
- `demo.inventory_activity`
- `demo.workorders`

Guardrails:

- Faker generation is allowed only when `profile=staging-demo` unless `demo.allow_in_prod=true`.
- Generated records must use clearly marked namespaces/codes (for cleanup and traceability).

## YAML Contract (Field-Level)

```yaml
version: 1
profile: prod-minimal # prod-minimal | staging-demo

tenant:
  id: "00000000-0000-0000-0000-000000000001"
  code: "alpha"
  name: "Durion Alpha"

environment:
  name: "alpha"
  default_timezone: "America/New_York"
  currency: "USD"

security:
  users:
    - username: "admin.alpha"
      email: "admin@durionpos.org"
      person_ref: "person_admin_1"
      password:
        mode: "bcrypt_hash" # bcrypt_hash | plaintext_for_hashing
        value: "$2a$12$replace_with_real_hash"
      enabled: true
  role_assignments:
    - username: "admin.alpha"
      role: "ADMIN"
      scope_type: "GLOBAL" # GLOBAL | LOCATION
      location_codes: []
  role_permission_overrides: []

locations:
  location_types:
    - code: "SHOP"
      name: "Shop"
      description: "Primary service shop"
    - code: "WAREHOUSE"
      name: "Warehouse"
      description: "Inventory warehouse"
    - code: "HQ"
    - name: "Headquarters"
    - description: "Company main site"
    - code: "DISPATCH"
    - name: "Dispatch"
    - description: "Mobile unit only site"
    - code: "OFFICE"
    - name: "Office"
    - description: "Business only site"
  sites:
    - code: "CAR-001"
      name: "Carolina"
      timezone: "America/New_York"
      location_type_code: "HQ"
      address:
        line1: "100 Main St"
        city: "Greenville"
        state: "SC"
        postal_code: "29615"
        country: "US"
    - code: "CAR-002"
      name: "Carolina Distibution"
      timezone: "America/New_York"
      location_type_code: "WAREHOUSE"
      address:
        line1: "100 Airport Rd"
        city: "Greer"
        state: "SC"
        postal_code: "29617"
        country: "US"
    - code: "CAR-003"
      name: "Charlotte Core Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "123 Trade St"
        city: "Charlotte"
        state: "NC"
        postal_code: "28202"
        country: "US"

    - code: "CAR-004"
      name: "South Charlotte Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "456 Ballantyne Commons Pkwy"
        city: "Charlotte"
        state: "NC"
        postal_code: "28277"
        country: "US"

    - code: "CAR-005"
      name: "North Charlotte Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "789 Huntersville-Concord Rd"
        city: "Huntersville"
        state: "NC"
        postal_code: "28078"
        country: "US"

    - code: "CAR-006"
      name: "Iredell South Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "101 Main St"
        city: "Mooresville"
        state: "NC"
        postal_code: "28115"
        country: "US"

    - code: "CAR-007"
      name: "Iredell North Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "202 Broad St"
        city: "Statesville"
        state: "NC"
        postal_code: "28677"
        country: "US"

    - code: "CAR-008"
      name: "Lincoln County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "303 E Main St"
        city: "Lincolnton"
        state: "NC"
        postal_code: "28092"
        country: "US"

    - code: "CAR-009"
      name: "Catawba County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "404 1st Ave NW"
        city: "Hickory"
        state: "NC"
        postal_code: "28601"
        country: "US"

    - code: "CAR-010"
      name: "Cabarrus County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "505 Concord Pkwy N"
        city: "Concord"
        state: "NC"
        postal_code: "28027"
        country: "US"

    - code: "CAR-011"
      name: "Rowan County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "606 E Innes St"
        city: "Salisbury"
        state: "NC"
        postal_code: "28144"
        country: "US"

    - code: "CAR-012"
      name: "Stanly County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "707 US Hwy 52 N"
        city: "Albemarle"
        state: "NC"
        postal_code: "28001"
        country: "US"

    - code: "CAR-013"
      name: "Gaston East Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "808 Park St"
        city: "Belmont"
        state: "NC"
        postal_code: "28012"
        country: "US"

    - code: "CAR-014"
      name: "Gaston West Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "909 W Franklin Blvd"
        city: "Gastonia"
        state: "NC"
        postal_code: "28052"
        country: "US"

    - code: "CAR-015"
      name: "Cleveland County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "111 S Lafayette St"
        city: "Shelby"
        state: "NC"
        postal_code: "28150"
        country: "US"

    - code: "CAR-016"
      name: "Union West Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "222 E John St"
        city: "Matthews"
        state: "NC"
        postal_code: "28105"
        country: "US"

    - code: "CAR-017"
      name: "Union East Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "333 N Main St"
        city: "Monroe"
        state: "NC"
        postal_code: "28112"
        country: "US"

    - code: "CAR-018"
      name: "Anson County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "444 W Wade St"
        city: "Wadesboro"
        state: "NC"
        postal_code: "28170"
        country: "US"

    - code: "CAR-019"
      name: "York North Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "555 SC-160"
        city: "Fort Mill"
        state: "SC"
        postal_code: "29708"
        country: "US"

    - code: "CAR-020"
      name: "York Central Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "666 E Main St"
        city: "Rock Hill"
        state: "SC"
        postal_code: "29730"
        country: "US"

    - code: "CAR-021"
      name: "York South Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "777 N Congress St"
        city: "York"
        state: "SC"
        postal_code: "29745"
        country: "US"

    - code: "CAR-022"
      name: "Lancaster County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "888 S Main St"
        city: "Lancaster"
        state: "SC"
        postal_code: "29720"
        country: "US"

    - code: "CAR-023"
      name: "Chester County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "999 Columbia St"
        city: "Chester"
        state: "SC"
        postal_code: "29706"
        country: "US"

    - code: "CAR-024"
      name: "Chesterfield County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "1010 W Main St"
        city: "Chesterfield"
        state: "SC"
        postal_code: "29709"
        country: "US"

    - code: "CAR-025"
      name: "Alexander County Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "1111 NC-16"
        city: "Taylorsville"
        state: "NC"
        postal_code: "28681"
        country: "US"

    - code: "CAR-026"
      name: "Burke-Caldwell Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "1212 Harper Ave"
        city: "Lenoir"
        state: "NC"
        postal_code: "28645"
        country: "US"

    - code: "CAR-027"
      name: "Upper Piedmont SC Service Area"
      timezone: "America/New_York"
      location_type_code: "SHOP"
      address:
        line1: "1313 N Main St"
        city: "Union"
        state: "SC"
        postal_code: "29379"
        country: "US"
  service_areas:
    - code: "charlotte-core"
      name: "Mecklenburg County"
    - code: "south-charlotte"
      name: "South Mecklenburg / Ballantyne / Pineville"
    - code: "north-charlotte"
      name: "Huntersville / Cornelius / Davidson corridor"
    - code: "iredell-south"
      name: "Mooresville / south Iredell"
    - code: "iredell-north"
      name: "Statesville / north Iredell"
    - code: "lincoln"
      name: "Lincoln County"
    - code: "catawba"
      name: "Catawba County – extended market"
    - code: "cabarrus"
      name: "Concord / Kannapolis"
    - code: "rowan"
      name: "Salisbury / Rowan County"
    - code: "stanly"
      name: "Albemarle / Stanly County"
    - code: "gaston-east"
      name: "Belmont / Mount Holly"
    - code: "gaston-west"
      name: "Gastonia / Bessemer City"
    - code: "cleveland"
      name: "Shelby / Cleveland County"
    - code: "union-west"
      name: "Matthews / Indian Trail"
    - code: "union-east"
      name: "Monroe / Wingate"
    - code: "anson"
      name: "Anson County – rural extension"
    - code: "york-north"
      name: "Fort Mill / Tega Cay"
    - code: "york-central"
      name: "Rock Hill"
    - code: "york-south"
      name: "York / Clover"
    - code: "lancaster"
      name: "Lancaster / Indian Land"
    - code: "chester-sc"
      name: "Chester County"
    - code: "chesterfield"
      name: "Chesterfield County – extended"
    - code: "alexander"
      name: "Alexander County"
    - code: "burke-caldwell"
      name: "Unifour spillover"
    - code: "upper-piedmont-sc"
      name: "far southern fringe beyond York/Lancaster"
  capabilities:
    - code: "ALIGNMENT"
      name: "Wheel Alignment"
    - code: "OIL_CHANGE"
      name: "Oil Change"
    - code: "BRAKE_SERVICE"
      name: "Brake Inspection & Repair"
    - code: "TIRE_SERVICE"
      name: "Tire Mounting, Balancing & Repair"
    - code: "SUSPENSION"
      name: "Suspension & Steering Repair"
    - code: "ENGINE_DIAGNOSTICS"
      name: "Engine Diagnostics"
    - code: "TRANSMISSION"
      name: "Transmission Service & Repair"
    - code: "ELECTRICAL"
      name: "Electrical System Diagnostics & Repair"
    - code: "COOLING_SYSTEM"
      name: "Cooling System Service"
    - code: "FUEL_SYSTEM"
      name: "Fuel System Service & Repair"
    - code: "EXHAUST"
      name: "Exhaust & Emissions Repair"
    - code: "AC_SERVICE"
      name: "HVAC / A/C Service"
    - code: "DOT_INSPECTION"
      name: "DOT Inspection"
    - code: "PM_SERVICE"
      name: "Preventive Maintenance Service"
    - code: "HYDRAULICS"
      name: "Hydraulic System Repair"
    - code: "DRIVELINE"
      name: "Driveline & Differential Service"
    - code: "CLUTCH"
      name: "Clutch Repair & Replacement"
    - code: "BATTERY"
      name: "Battery Testing & Replacement"
    - code: "TRAILER_REPAIR"
      name: "Trailer Repair & Maintenance"
    - code: "ROADSIDE_SERVICE"
      name: "Emergency Roadside Service"
  travel_buffer_policies:
    - code: "DEFAULT_15M"
      name: "Default 15m"
      buffer_type: "MINUTES"
      buffer_value: 15

people:
  persons:
    - ref: "person_admin_1"
      first_name: "System"
      last_name: "Admin"
      email: "admin@durionpos.org"
  user_person_links:
    - username: "admin.alpha"
      person_ref: "person_admin_1"
      link_type: "PRIMARY"
  person_location_assignments:
    - person_ref: "person_admin_1"
      location_code: "CAR-001"
      role: "MANAGER"
      is_primary: true
  timekeeping_policies:
    - code: "GLOBAL_DEFAULT"
      scope_type: "GLOBAL"
      scope_ref: null
      threshold_minutes: 10

catalog:
  products:
    - sku: "OIL-5W30-5QT"
      name: "Synthetic Oil 5W30"
      uom: "EACH"
      active: true
  services:
    - code: "SVC-OIL"
      name: "Oil Change Service"
      active: true

pricing:
  base_prices:
    - item_code: "SVC-OIL"
      item_type: "SERVICE" # SERVICE | PRODUCT
      amount: 79.99
      currency: "USD"

inventory:
  replenishment_policies:
    - policy_code: "ATL-REPL-DEFAULT"
      location_code: "CAR-002"
      sku: "OIL-5W30-5QT"
      reorder_point: 20
      reorder_quantity: 40
  putaway_rules:
    - rule_code: "PUTAWAY-OIL"
      location_code: "CAR-002"
      sku_prefix: "OIL-"
      destination_storage_code: "ATL-001-BULK"
  approval_thresholds:
    - tier: "SUPERVISOR"
      max_delta_value: 100.00
      active: true
accounting:
  gl_accounts:
    - code: "4000"
      name: "Service Revenue"
      type: "REVENUE"
    - code: "1200"
      name: "Accounts Receivable"
      type: "ASSET"
  posting_categories:
    - code: "ORDER_REVENUE"
      name: "Order Revenue"
  mapping_keys:
    - code: "DEFAULT"
      posting_category_code: "ORDER_REVENUE"
  gl_mappings:
    - source_system: "ORDER"
      external_code: "ORDER_COMPLETED"
      posting_category_code: "ORDER_REVENUE"
      mapping_key_code: "DEFAULT"
      gl_account_code: "4000"
  default_gl_mappings:
    - event_type: "ORDER_CART_CREATE"
      debit_gl_account_code: "1200"
      credit_gl_account_code: "4000"
      active: true
  statement_line_mappings:
    - statement_type: "INCOME_STATEMENT"
      line_code: "REVENUE"
      gl_account_code: "4000"
      operation: "ADD"

invoice:
  billing_rules:
    - party_code: "COMMERCIAL_DEFAULT"
      purchase_order_required: false
      payment_terms_code: "NET_30"
      delivery_method: "EMAIL"

faker:
  enabled: false
  seed: 42
  locale: "en"
  counts:
    demo_users: 0
    demo_customers: 0
    demo_vehicles: 0
    demo_orders: 0
    demo_invoices: 0
```

## Validation Rules

- `version` must be `1`.
- `profile` must be one of `prod-minimal`, `staging-demo`.
- At least one `security.users` record and one ADMIN-capable assignment must exist.
- All references (`person_ref`, `location_code`, `posting_category_code`, `gl_account_code`, etc.) must resolve.
- `timekeeping_policies` must include at least one GLOBAL policy.
- `faker.enabled=true` in `prod-minimal` requires explicit override flag in generator CLI.

## Generator CLI Contract

Recommended interface:

```bash
npm run seed:generate -- \
  --input ./seed-input.yaml \
  --out ./generated-seed-sql \
  --profile prod-minimal
```

Expected output files (ordered):

1. `001_security.sql`
2. `002_event_types.sql`
3. `003_locations_people.sql`
4. `004_catalog_pricing_inventory.sql`
5. `005_accounting.sql`
6. `006_invoice.sql`
7. `900_demo_optional.sql`
8. `999_assertions.sql`

All SQL files should be idempotent (`ON CONFLICT ...`).
