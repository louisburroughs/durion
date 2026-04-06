# Backend Preload Tables (Module Crawl)

## Purpose

This document lists backend tables/entities that should be preloaded so the POS platform is usable after deployment.

Sources used:

- `durion-positivity-backend/pos-*/docs/*-erd.md`
- Module startup initializers and migration seeds under `durion-positivity-backend/pos-*/src/main/**`

## 1) Bootstrap-Critical (Required for Core Platform Function)

| Module | Table(s) | Why preload is required |
|---|---|---|
| `pos-security-service` | `users` | At least one active user is needed for authenticated access. |
| `pos-security-service` | `roles` | Authorization model depends on roles; defaults are auto-initialized, but roles must exist. |
| `pos-security-service` | `permissions` | Token authorization (`perm_bits`) depends on registered permissions. |
| `pos-security-service` | `role_assignments` and `principal_roles` (or `user_roles`, depending on schema version) | Users need role linkage to receive effective authorities/permission bits. |
| `pos-event-receiver` | `event_type` | Event emitters register against this catalog at startup; event telemetry/audit flows rely on valid event types. |
| `pos-accounting` | `override_policy_threshold` | Price override authorization fails when no active policy exists for role. |
| `pos-accounting` | `refund_policy_config` | Refund authorization expects an active policy. |

## 2) Operationally Required Master/Reference Data (System Works, but POS Flows Are Not Usable Without It)

| Module | Table(s) | Why preload is needed for real operations |
|---|---|---|
| `pos-location` | `location_type` | Provides valid location classifications used by location records. |
| `pos-location` | `service_areas` | Mobile/service coverage routing depends on known service areas. |
| `pos-location` | `service_location_capabilities` | Bay/location capability matching needs predefined capability values. |
| `pos-location` | `travel_buffer_policies` | Mobile unit dispatch/scheduling relies on travel-time buffer policy values. |
| `pos-location` | `mobile_units`, `mobile_unit_coverage_rules` | Required for mobile-service dispatch operations. |
| `pos-location` | `bays`, `storage_location` | Shop capacity and inventory placement workflows depend on these records. |
| `pos-people` | `user_person_links` | Connects identity users to workforce/person records for staffing workflows. |
| `pos-people` | `person_location_assignment` | Scheduling and location-scoped operations need person-to-location assignments. |
| `pos-people` | `timekeeping_policy` | Exception/threshold logic uses active timekeeping policy values. |
| `pos-catalog` | `product`, `service`, `non_inventory_product` | Ordering/pricing/invoicing cannot run without sellable catalog items. |
| `pos-catalog` | `price_book`, `price_book_rule` | Location/customer-specific pricing depends on active price books/rules. |
| `pos-catalog` | `dimensions`, `uom_conversion` | Variant and unit conversion behavior requires valid reference values. |
| `pos-price` | `product_base_price` | Baseline pricing engine input. |
| `pos-price` | `customer_tier_pricing_rule`, `restriction_rule` | Tiered/restricted pricing behavior depends on rule data. |
| `pos-price` | `promotion_offer`, `promotion_eligibility_rule` | Promotion engine requires configured offers and eligibility rules. |
| `pos-price` | `location_price_override` | Site-specific pricing overrides require preload where used. |
| `pos-inventory` | `replenishment_policy` | Replenishment task generation requires policy thresholds/rules. |
| `pos-inventory` | `putaway_rule` | Receiving/putaway automation requires location routing rules. |
| `pos-inventory` | `approval_threshold_config` | Adjustment approval routing depends on threshold tiers. |
| `pos-accounting` | `gl_account` | Core posting/reconciliation account master data. |
| `pos-accounting` | `posting_category`, `mapping_key`, `gl_mapping` | Event-to-GL mapping requires these reference mappings. |
| `pos-accounting` | `default_gl_mapping` | Fallback posting path relies on default mappings. |
| `pos-accounting` | `posting_rule_set`, `posting_rule_version` | Rule-driven posting requires published rule versions. |
| `pos-accounting` | `statement_line_mappings` | Financial statement generation depends on configured line mappings. |
| `pos-invoice` | `billing_rules` | Commercial invoicing behavior depends on party billing policy records. |
| `pos-shop-manager` | `mechanic`, `mechanic_skill` | Appointment assignment/capability matching needs technician master data. |
| `pos-vehicle-fitment` | `manufacturer`, `make`, `model`, `vehicle_type` | Vehicle selector/fitment lookup depends on reference vehicle hierarchy. |
| `pos-vehicle-fitment` | `vehicle_variable`, `vehicle_variable_value`, `fitment_tags` | Fitment filtering and compatibility logic depends on these valid-value sets. |

## 3) Auto-Seeded vs Manual Preload Notes

| Area | Auto-seeded behavior | Manual preload still needed |
|---|---|---|
| `pos-security-service` roles | Default roles are created by startup initializer and Flyway seed scripts. | Real users and role assignments for your org/locations must still be loaded. |
| `pos-security-service` permissions | Most permissions are registered by module startup registration calls. | Role-to-permission curation and assignments must be configured for production access model. |
| `pos-accounting` override/refund policies | Default policies are inserted on startup if tables are empty. | Any org-specific policy thresholds/versions should be preloaded/managed. |
| `pos-vehicle-fitment` manufacturer/make/model/type | Service can hydrate cache from NHTSA API on demand. | Preload is recommended for deterministic startup and offline/no-egress environments. |
| `pos-event-receiver` event types | Services attempt startup registration of event types. | Preload/backfill may be required if startup ordering/network prevents registration. |

## 4) Recommended Deployment Sequence for Data Load

1. Load `pos-security-service` users/roles/permissions/assignments.
2. Load location and people linkage (`pos-location`, `pos-people`).
3. Load catalog/price/inventory reference data.
4. Load accounting mappings and posting rules.
5. Load shop-manager and vehicle fitment reference data.
6. Validate event type registration and accounting policy presence before opening traffic.
