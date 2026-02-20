---
title: Product Backend Contract Guide
domain: product
doc_type: backend_contract
contract:
  status: draft
  owner_repo: louisburroughs/durion
  guide_path: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
  openapi_source: durion-positivity-backend/pos-catalog/openapi.json
traceability:
  capability_manifest: docs/capabilities
last_updated: 2026-02-19
---

# Product Backend Contract Guide

**Version:** v0.6 (OpenAPI sync)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-18

---

## Overview

This guide defines the Product backend contract for the Product domain, implemented by `pos-catalog`.

Authoritative source for current endpoint inventory is (OpenAPI authoritative):
- `$WORKSPACE/durion-positivity-backend/pos-catalog/openapi.json`

---

## Base URL

- Via API Gateway (recommended): `http://localhost:8080/v1/products`
- Service-local OpenAPI server entry may differ by environment.

**Path Format Requirement (MANDATORY)**

- All endpoint paths MUST use the API Gateway format: `http://localhost:8080/v{version}/{domain}/{resource}`.
- Example: OpenAPI path `/v1/products/{productId}` -> gateway URL `http://localhost:8080/v1/products/{productId}`.

***

## Implementation Links / Backlog

- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/52
  - https://github.com/louisburroughs/durion-positivity-backend/issues/53
  - https://github.com/louisburroughs/durion-positivity-backend/issues/54
  - https://github.com/louisburroughs/durion-positivity-backend/issues/194
- Capability manifest: /docs/capabilities/CAP-168/CAPABILITY_MANIFEST.yaml

Cross-reference: any path refactoring or gateway-routing changes should reference the backend issue(s) above. Primary capability manifest for this guide: `docs/capabilities/CAP-168/CAPABILITY_MANIFEST.yaml`.

---

## ADR References

- [ADR-0009: Backend Domain Responsibilities Guide](../../../docs/adr/0009-backend-domain-responsibilities-guide.adr.md)
- [ADR-0011: API Gateway Security Architecture](../../../docs/adr/0011-api-gateway-security-architecture.adr.md)
- [ADR-0013: Platform UUID Identifier Strategy](../../../docs/adr/0013-platform-uuid-identifier-strategy.adr.md)
- [ADR-0014: Gateway Internal Service Security](../../../docs/adr/0014-gateway-internal-service-security.adr.md)
- [ADR-0017: API Controller HTTP Response Codes](../../../docs/adr/0017-api-controller-http-response-codes.adr.md)

## Conventions

- JSON field naming: `camelCase`
- IDs: opaque UUID strings
- Enum values: `UPPER_CASE_WITH_UNDERSCORES`
- Timestamps: ISO 8601 UTC when present
- Errors: use standard envelope (recommended hardening target)
  - `{ code, message, correlationId, fieldErrors[]? }`

---

## Authorization Contract

Current implementation is role-based via `@PreAuthorize`:

- Read endpoints: `ROLE_ADMIN` or `ROLE_CATALOG_VIEW`
- Create/update endpoints: `ROLE_ADMIN` or `ROLE_CATALOG_EDIT`
- Delete endpoints: `ROLE_ADMIN` or `ROLE_CATALOG_DELETE`

---

## Event Contract

Mutation endpoints emit events:

- `CATALOG_ITEM_CREATE`
- `CATALOG_ITEM_UPDATE`
- `CATALOG_CATALOG_CREATE`
- `CATALOG_CATALOG_UPDATE`

-- CAP-167 event contracts (MSRP & Price Book):
- `CATALOG_MSRP_CREATE`
- `CATALOG_MSRP_UPDATE`
- `CATALOG_PRICE_BOOK_RULE_CREATE`
- `CATALOG_PRICE_BOOK_RULE_UPDATE`
- `CATALOG_PRICE_BOOK_RULE_DEACTIVATE`

---


## Endpoint Inventory (OpenAPI-sourced)

Note: OpenAPI (pos-catalog/openapi.json) is authoritative. All gateway examples below use the API Gateway format `http://localhost:8080{path}` where `{path}` is the OpenAPI path (which includes `/v1/...`).

### Catalog endpoints (mapped from OpenAPI)

- `POST /v1/catalogs` (gateway: `http://localhost:8080/v1/catalogs`) — `addCatalog`
  - Purpose: create catalog
  - Responses: `201 Created` + `CatalogDto`, `400 Bad Request`

- `GET /v1/catalogs/{catalogId}` (gateway: `http://localhost:8080/v1/catalogs/{catalogId}`) — `getCatalogById`
  - Purpose: retrieve catalog by id
  - Responses: `200 OK` + `CatalogDto`, `404 Not Found`

- `PUT /v1/catalogs/{catalogId}` (gateway: `http://localhost:8080/v1/catalogs/{catalogId}`) — `updateCatalog`
  - Purpose: update catalog
  - Responses: `200 OK` + `CatalogDto`, `400 Bad Request`, `404 Not Found`

- `DELETE /v1/catalogs/{catalogId}` (gateway: `http://localhost:8080/v1/catalogs/{catalogId}`) — `deleteCatalog`
  - Responses: `204 No Content`, `404 Not Found`

- `GET /v1/catalogs/name/{name}` (gateway: `http://localhost:8080/v1/catalogs/name/{name}`) — `getCatalogByName`
  - Responses: `200 OK` + `CatalogDto`

### Catalog-item endpoints (mapped from OpenAPI)

- `POST /v1/catalog-items/{type}` (gateway: `http://localhost:8080/v1/catalog-items/{type}`) — `addCatalogItem`
  - Purpose: add a product/service/noninventory item (type path param)
  - Responses: `201 Created` + `CatalogItemResponseDto`, `400 Bad Request`

- `PUT /v1/catalog-items/{type}/{catalogId}` (gateway: `http://localhost:8080/v1/catalog-items/{type}/{catalogId}`) — `updateCatalogItem`
  - Responses: `200 OK`, `400 Bad Request`, `404 Not Found`

- `DELETE /v1/catalog-items/{type}/{catalogId}` (gateway: `http://localhost:8080/v1/catalog-items/{type}/{catalogId}`) — `deleteCatalogItem`
  - Responses: `204 No Content`, `400 Bad Request`, `404 Not Found`

### Product / Service / Non-inventory endpoints

- `GET /v1/products/{productId}` (gateway: `http://localhost:8080/v1/products/{productId}`) — `getProductById`
  - Responses: `200 OK` + `ProductDto`, `404 Not Found`

- `GET /v1/products/name/{name}` (gateway: `http://localhost:8080/v1/products/name/{name}`) — `getProductByName`
  - Responses: `200 OK` + `ProductDto`

- `GET /v1/products/services/{serviceId}` (gateway: `http://localhost:8080/v1/products/services/{serviceId}`) — `getServiceById`
  - Responses: `200 OK` + `ServiceDto`, `404 Not Found`

- `GET /v1/products/services/name/{name}` (gateway: `http://localhost:8080/v1/products/services/name/{name}`) — `getServiceByName`
  - Responses: `200 OK` + `ServiceDto`

- `GET /v1/products/noninventory/{productId}` (gateway: `http://localhost:8080/v1/products/noninventory/{productId}`) — `getNonInventoryProductById`
  - Responses: `200 OK` + `NonInventoryProductDto`, `404 Not Found`

- `GET /v1/products/noninventory/name/{name}` (gateway: `http://localhost:8080/v1/products/noninventory/name/{name}`) — `getNonInventoryProductByName`
  - Responses: `200 OK` + `NonInventoryProductDto`

### Composed and product-detail endpoints

- `GET /v1/products/{productId}/detail` (gateway: `http://localhost:8080/v1/products/{productId}/detail`) — `getProductDetailView`
  - Query parameters: `location_id` (UUID, required)
  - Responses: `200 OK` + `ProductDetailView` (partial responses allowed), `400 Bad Request`, `404 Not Found`, `500 Server Error`

### CAP-170 — Availability & Inventory Visibility

- Consolidated product detail endpoint (aggregates availability from `pos-inventory`):
  - `GET http://localhost:8080/v1/products/{productId}/detail?location_id={locationId}` — returns `ProductDetailView` which includes `availability: AvailabilityInfo`.

- Purpose: Provide location-specific availability and lead-time information as part of the product detail response. The `pos-catalog` service queries `pos-inventory` at request time and performs graceful degradation when inventory is unavailable.

- `ProductDetailView` (excerpt — availability-related fields):

  - `productId`: string (UUID)
  - `description`: string
  - `pricing`: `PricingInfo`
  - `availability`: `AvailabilityInfo`
  - `generatedAt`: string (date-time)
  - `confidence`: enum [LOW, MEDIUM, HIGH]

- `AvailabilityInfo` schema (as returned by the product API):

  - `onHandQuantity`: integer (int32) — On-hand quantity at the requested location
  - `availableToPromiseQuantity`: integer (int32) — Quantity available to promise
  - `leadTime`: `LeadTimeInfo` — see LeadTimeInfo schema (minDays, maxDays, displayText, source)
  - `status`: enum { OK, UNAVAILABLE, STALE, ERROR }
    - `OK` — availability data is fresh and authoritative
    - `UNAVAILABLE` — inventory indicates zero availability at location
    - `STALE` — availability data may be out-of-date (e.g., inventory service returned cached/stale values)
    - `ERROR` — inventory service error or unable to retrieve availability
  - `asOf`: string (date-time) — timestamp for availability measurement
  - `confidence`: enum { LOW, MEDIUM, HIGH } — confidence level in availability data

- Implementation notes / links (backend child issues):
  - https://github.com/louisburroughs/durion-positivity-backend/issues/46 — manufacturer feed normalization (related mapping integration)
  - https://github.com/louisburroughs/durion-positivity-backend/issues/47 — distributor feed normalization
  - https://github.com/louisburroughs/durion-positivity-backend/issues/48 — on-hand / ATP exposure from inventory

- Provider test hints (ContractBehaviorIT):
  - Provider tests should seed inventory rows for the location before calling the product-detail endpoint and assert `availability.onHandQuantity` and `availability.availableToPromiseQuantity` match expectations.
  - Use the repository's contract test patterns (seed methods + BaseContractIntegrationTest / RestAssured integration helpers) to ensure real DB-backed tests rather than in-memory stubs.
  - Include tests for graceful degradation: when `pos-inventory` returns an error or times out, the provider should still return `200 OK` with `availability.status` set to `STALE` or `ERROR` and appropriate `confidence` (e.g., LOW).

- Behavioral assertions (must be asserted by provider tests):
  - When `pos-inventory` is reachable and returns current data, `availability.status` == `OK` and `confidence` in {MEDIUM,HIGH}.
  - When `pos-inventory` indicates zero stock at location, `availability.status` == `UNAVAILABLE` and `onHandQuantity` == 0.
  - When `pos-inventory` is unreachable or returns an error, `availability.status` == `ERROR` (or `STALE` if cached fallback used) and `confidence` == `LOW`.
  - `ProductDetailView` responses must never omit the `availability` field entirely; instead use `status` to communicate degraded data.

- Notes on semantics:
  - `pos-catalog` is the consolidation owner for `ProductDetailView`. Source-of-truth for availability numeric values is `pos-inventory`.
  - `availability.leadTime` may be sourced from catalog master data or from inventory/supply-chain feeds; the `leadTime.source` field indicates origin.

---

### Manufacturer Part Mapping (related to Issue #46)

- Endpoint (gateway): `GET http://localhost:8080/v1/products/manufacturerPartMap/resolve`
  - Purpose: Resolve a manufacturer/distributor part identifier to a normalized product identifier in the catalog (used for matching external feeds to internal products).
  - Query parameters (suggested): `mpn` (manufacturer part number, string), `manufacturerId` (UUID, optional), `upc` (optional). Implementations MAY accept other lookup keys.
  - Responses:
    - `200 OK` — returns mapping object: `{ manufacturerPartNumber, mappedProductId, source, confidence }`
    - `404 Not Found` — no mapping available
    - `400 Bad Request` — invalid query parameters
  - Example response:

```json
{
  "manufacturerPartNumber": "MPN-12345",
  "mappedProductId": "550e8400-e29b-41d4-a716-446655440000",
  "source": "MANUFACTURER_FEED|DISTRIBUTOR_FEED|MANUAL",
  "confidence": "HIGH"
}
```

- Test and implementation notes:
  - Provider tests should include cases for exact MPN match, fallback to UPC, and manufacturer-disambiguation behavior.
  - This endpoint is referenced by CAP-170 Issue #46 and should be implemented alongside feed normalization work.


- `GET /v1/products/{productId}/substitutes` (gateway: `http://localhost:8080/v1/products/{productId}/substitutes`) — `getPartSubstitutes`
  - Current response: `501 Not Implemented`

### Pricing & location overrides (CAP-168 scope)

- `POST /v1/products/pricing/location-overrides` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides`) — `createLocationPriceOverride`
  - Purpose: create a location-specific price override; enforces guardrails
  - Responses: `201 Created` + `LocationPriceOverrideResponseDto`, `400 Bad Request` (guardrail violations), `403 Forbidden`

- `POST /v1/products/pricing/location-overrides/{overrideId}/approve` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/approve`) — `approveLocationPriceOverride`
  - Responses: `200 OK` + `LocationPriceOverrideResponseDto`, `404 Not Found`, `409 Conflict`, `400 Bad Request`

- `POST /v1/products/pricing/location-overrides/{overrideId}/reject` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/reject`) — `rejectLocationPriceOverride`
  - Responses: `200 OK`, `404 Not Found`, `409 Conflict`, `400 Bad Request`

- `GET /v1/products/pricing/effective-price/{locationId}/{productId}` (gateway: `http://localhost:8080/v1/products/pricing/effective-price/{locationId}/{productId}`) — `getEffectiveLocationPrice`
  - Purpose: resolve effective price (ACTIVE override preferred, otherwise base)
  - Responses: `200 OK` + `EffectiveLocationPriceResponseDto`, `404 Not Found`

- `POST /v1/products/pricing/guardrail-policies` (gateway: `http://localhost:8080/v1/products/pricing/guardrail-policies`) — `upsertLocationGuardrailPolicy`
  - Purpose: upsert guardrail policy used by location overrides
  - Responses: `200 OK`, `400 Bad Request`

### Supplier cost endpoints (mapped)

- `POST /v1/suppliers/costs/items` (gateway: `http://localhost:8080/v1/suppliers/costs/items`) — `createSupplierItemCost`
  - Responses: `201 Created`, `400 Bad Request`, `403 Forbidden`

- `GET /v1/suppliers/{supplierId}/items/{itemId}/costs` (gateway: `http://localhost:8080/v1/suppliers/{supplierId}/items/{itemId}/costs`) — `getSupplierItemCost`
  - Responses: `200 OK`, `404 Not Found`

- `PUT /v1/suppliers/{supplierId}/items/{itemId}/costs` (gateway: `http://localhost:8080/v1/suppliers/{supplierId}/items/{itemId}/costs`) — `updateSupplierItemCost`
  - Responses: `200 OK`, `400 Bad Request`, `404 Not Found`, `409 Conflict`

- `DELETE /v1/suppliers/{supplierId}/items/{itemId}/costs` (gateway: `http://localhost:8080/v1/suppliers/{supplierId}/items/{itemId}/costs`) — `deleteSupplierItemCost`
  - Responses: `204 No Content`, `404 Not Found`

### Delta summary (high level)

- Changed / renamed paths (guide -> OpenAPI):
  - `POST /v1/products/catalog` -> `POST /v1/catalogs`
  - `GET/PUT/DELETE /v1/products/catalog/{catalogId}` -> `/v1/catalogs/{catalogId}`
  - Catalog-item endpoints moved from `/v1/products/{type}` to `/v1/catalog-items/{type}`
  - Product detail and substitutes path shapes updated (e.g., `/v1/products/{productId}/detail`, `/v1/products/{productId}/substitutes`)
  - Service endpoints use `services` segment (`/v1/products/services/...`)
  - Supplier cost endpoints moved to `/v1/suppliers/...` paths

- Added (present in OpenAPI, missing/renamed in guide):
  - `/v1/suppliers/{supplierId}/items/{itemId}/costs`
  - `/v1/products/pricing/location-overrides` and approve/reject endpoints

- Removed: none detected in OpenAPI vs guide; many path names were refactored/renamed.

Any guide endpoint must match the OpenAPI `paths` object; OpenAPI is the source of truth.

---

## Core Schema References

From OpenAPI components:

- `ProductEntity`
- `ServiceEntity`
- `NonInventoryProductEntity`
- `CatalogEntity`
- `ProductDetailView`
- `CatalogItem`
- Supporting schemas: `PricingInfo`, `AvailabilityInfo`, `SubstitutionHint`, etc.

---

## Error Semantics (Current + Target)

### Current observed behavior

- `400`: bad type/payload mismatch
- `404`: id not found
- `501`: substitutes endpoint not implemented

### Recommended standardized codes (target)

| Code | Meaning | HTTP |
| --- | --- | --- |
| `VALIDATION_ERROR` | Invalid request field(s) | 400 |
| `NOT_FOUND` | Resource not found | 404 |
| `FORBIDDEN` | Missing required role/authority | 403 |
| `UNSUPPORTED_OPERATION` | Endpoint/type present but not implemented | 501 |
| `CONFLICT` | State/version conflict (future hardening) | 409 |

---

## Example Payloads

### Create catalog request

```json
{
  "name": "Retail Catalog",
  "description": "Primary product catalog"
}
```

### Create product via typed endpoint (`POST /v1/products/product`)

```json
{
  "name": "Heavy Duty Wrench",
  "shortDescription": "A versatile wrench",
  "longDescription": "Hardened steel wrench for shop use",
  "sku": "SKU12345"
}
```

### Product detail request

`GET /v1/products/product/{productId}?location_id={locationId}`

---

## Contract Hardening Backlog

1. Standardize error envelope on all endpoints.
2. Add explicit idempotency behavior for mutation endpoints (`Idempotency-Key`).
3. Decide substitutes contract ownership and finalize endpoint semantics.
4. Consider migration from role-based guards to permission-based guards.

---

## CAP-167/CAP-168 Combined: Location Pricing (Guardrails & Store Overrides)

> Note: Location store pricing override content was implemented under CAP-168. The CAP-167 scope covers MSRP management and base price book rules documented in the section below.

Status: `draft`

### CAP-167 Endpoints

1. `POST /v1/products/pricing/guardrail-policies`
   - Purpose: Create or update the active LOCATION guardrail policy.
   - Response: `200 OK` + `LocationPriceOverrideResponseDto` (location context only).
   - Errors:
     - `400 Bad Request` for invalid policy fields.
     - `403 Forbidden` when user lacks edit permissions.

2. `POST /v1/products/pricing/location-overrides`
   - Purpose: Create a location-specific price override and evaluate guardrails.
   - Response: `201 Created` + `LocationPriceOverrideResponseDto`.
   - Behavior:
     - Creates `ACTIVE` override when discount is within auto-approval threshold.
     - Creates `PENDING_APPROVAL` override and approval request when threshold is exceeded but hard limits pass.
   - Errors:
     - `400 Bad Request` for hard guardrail violations (`MIN_MARGIN_VIOLATION`, `MAX_DISCOUNT_EXCEEDED`).
     - `403 Forbidden` when user lacks edit permissions.

3. `GET /v1/products/pricing/effective-price/{locationId}/{productId}`
   - Purpose: Resolve effective price for a location/product pair.
   - Response: `200 OK` + `EffectiveLocationPriceResponseDto`.
   - Precedence:
     - ACTIVE override price when present.
     - Base price when latest override is pending approval.
   - Errors:
     - `404 Not Found` when no applicable pricing context exists.

4. `POST /v1/products/pricing/location-overrides/{overrideId}/approve`
   - Purpose: Approve pending override and activate price.
   - Response: `200 OK` + `LocationPriceOverrideResponseDto`.
   - Errors:
     - `400 Bad Request` for invalid status or request body.
     - `404 Not Found` when override/approval request is missing.
     - `409 Conflict` on optimistic locking version mismatch.

5. `POST /v1/products/pricing/location-overrides/{overrideId}/reject`
   - Purpose: Reject pending override with required reason and notes.
   - Response: `200 OK` + `LocationPriceOverrideResponseDto`.
   - Errors:
     - `400 Bad Request` for invalid rejection payload.
     - `404 Not Found` when override/approval request is missing.
     - `409 Conflict` on optimistic locking version mismatch.

### Guardrail Rules

- `min_margin_percent` is a hard limit.
- `max_discount_percent` is a hard limit.
- `auto_approval_threshold_percent` is a soft limit for routing to manual approval.
- Rejected overrides remain persisted with status `REJECTED` and rejection metadata.

### Example Request (Create Override)

```json
{
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "basePrice": 100.00,
  "cost": 50.00,
  "overridePrice": 88.00,
  "createdByUserId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a537"
}
```

### Example Response (Pending Approval)

```json
{
  "overrideId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a538",
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "basePrice": 100.00,
  "overridePrice": 88.00,
  "discountPercent": 12.0000,
  "marginPercent": 43.1818,
  "status": "PENDING_APPROVAL",
  "assignedApproverId": "4b7e0f8e-26f1-3ac8-bde8-e2cb8f8ad7e8",
  "assignmentStrategy": "LOCATION_SCOPE_PRIMARY_THEN_POOL"
}
```

### Example Error (Hard Guardrail Violation)

```json
"MIN_MARGIN_VIOLATION: Margin below 15% minimum."
```

---

## CAP-166: Cost Management

This section documents the planned backend contract for CAP-166: Cost Management (Acquisition & Cost Models). The endpoints below are greenfield for the backend (`durion-positivity-backend` issues #195 and #196) and are not yet present in `pos-catalog/openapi.json`. The OpenAPI file is authoritative for implemented endpoints; these entries represent the planned contract implementers should add to OpenAPI.

Gateway base URL (MANDATORY format): `http://localhost:8080/v1/products` (append path below)

### Summary (issues)
- Issue #195 — SupplierItemCost / CostTier (volume-based supplier-item cost tiers)
- Issue #196 — Item costs: `standardCost`, `lastCost`, `averageCost` with audit trail

### Issue #195 — SupplierItemCost (Cost Tiers)

1) `POST /v1/products/supplier-costs` (gateway: `http://localhost:8080/v1/products/supplier-costs`)
   - Purpose: Create a `SupplierItemCost` resource that contains an ordered list of `CostTier` entries for a given supplier + item combination.
   - Request (TypeScript-like):
     ```ts
     interface CreateSupplierItemCostRequest {
       supplierId: string; // uuid
       itemId: string; // uuid
       currencyCode: string; // ISO 4217
       baseCost?: string | null; // decimal string, scale 4
       tiers: Array<{
         minQuantity: number; // >=1
         maxQuantity?: number | null; // nullable for final open tier
         unitCost: string; // decimal string, > 0
       }>;
     }
     ```
   - Responses:
     - `201 Created` + body: `{ id: string, supplierId, itemId, currencyCode, tiers: [...] }`
     - `400 Bad Request` — validation errors (e.g., negative costs, min>max)
     - `400 INVALID_TIER_STRUCTURE` — overlapping or non-contiguous tiers (see business rules)
     - `409 Conflict` — supplier+item cost structure already exists
     - `403 Forbidden` — insufficient permission
   - Behavioral assertions:
     - Only authorized inventory or catalog editors may create supplier costs (guarded by roles/permissions in service).
     - Validation MUST enforce contiguous, non-overlapping tier ranges starting at `minQuantity=1`.
     - Final tier MUST allow `maxQuantity=null` to indicate open-ended range.
   - ContractBehaviorIT hints:
     - Happy path: POST valid contiguous tiers -> expect `201` and persisted tiers in DB.
     - Validation tests: overlapping tiers -> expect `400` and error code `INVALID_TIER_STRUCTURE`.
     - Duplicate tests: POST same supplier+item twice -> expect `409`.

2) `GET /v1/products/supplier-costs/{supplierItemCostId}` (gateway: `http://localhost:8080/v1/products/supplier-costs/{supplierItemCostId}`)
   - Purpose: Retrieve a `SupplierItemCost` and its `CostTier` list, ordered by `minQuantity`.
   - Response schema:
     ```ts
     interface SupplierItemCostResponse {
       id: string;
       supplierId: string;
       itemId: string;
       currencyCode: string;
       baseCost?: string | null;
       tiers: Array<{ minQuantity: number; maxQuantity?: number | null; unitCost: string }>;
       createdAt: string; // ISO timestamp
       updatedAt: string; // ISO timestamp
     }
     ```
   - Responses: `200 OK`, `404 Not Found`, `403 Forbidden`
   - Behavioral assertions:
     - Returned `tiers` MUST be ordered ascending by `minQuantity` and include null `maxQuantity` for final tier.
   - ContractBehaviorIT hints:
     - Persist a structure, GET it back and assert order and exact values.

3) `PUT /v1/products/supplier-costs/{supplierItemCostId}`
   - Purpose: Replace/modify an existing cost tier structure for the resource id.
   - Request: same shape as `CreateSupplierItemCostRequest` (except `supplierId`/`itemId` may be immutable depending on design).
   - Responses:
     - `200 OK` + updated resource
     - `400 INVALID_TIER_STRUCTURE` for overlapping/gaps
     - `404 Not Found`
     - `409 Conflict` for concurrent modification if optimistic locking used
   - Behavioral assertions:
     - Update MUST enforce the same tier contiguity and positivity rules as create.
     - If the implementation uses optimistic locking, return `409` on version mismatch.

4) `DELETE /v1/products/supplier-costs/{supplierItemCostId}`
   - Purpose: Remove an existing tier set and associated tiers.
   - Responses: `204 No Content`, `404 Not Found`, `403 Forbidden`
   - Behavioral assertions:
     - Deletion removes the supplier-item cost structure; if no structure exists, return `404`.

Business rules (from Issue #195):
- A given `supplierId` + `itemId` combination may have at most one active `SupplierItemCost`.
- Tiers MUST be contiguous without gaps or overlaps and MUST start at `minQuantity = 1`.
- Final tier MUST use `maxQuantity = null` to indicate open-ended "and above".
- All `unitCost` values MUST be positive decimals (> 0).
- `409 Conflict` on attempts to create a duplicate supplier+item combination.
- `400 INVALID_TIER_STRUCTURE` on overlapping or non-contiguous tier submissions.

---

### Issue #196 — Item Costs: Standard / Last / Average (with audit)

1) `PUT /v1/products/items/{itemId}/standard-cost` (gateway: `http://localhost:8080/v1/products/items/{itemId}/standard-cost`)
   - Purpose: Manually set or update the `standardCost` for an inventory item. This is a permissioned operation and requires a `reasonCode` to be recorded for audit.
   - Request (TypeScript-like):
     ```ts
     interface UpdateStandardCostRequest {
       standardCost: string | null; // decimal string, nullable to unset
       currencyCode?: string; // optional, if present must match item/supplier currency rules
       reasonCode: string; // required string explaining change
       modifiedByUserId?: string; // optional actor id
     }
     ```
   - Responses:
     - `200 OK` + updated cost representation
     - `400 Bad Request` when `reasonCode` missing or invalid payload
     - `403 Forbidden` when caller lacks `inventory.cost.standard.update` permission
     - `404 Not Found` when `itemId` not found
   - Behavioral assertions:
     - This endpoint requires `inventory.cost.standard.update` permission (map to service auth/roles).
     - `reasonCode` is mandatory — reject requests without it with `400` and descriptive message.
     - Create an `ItemCostAudit` record for the change (oldValue/newValue/reasonCode/actor/timestamp) in the same transaction as the update.
   - ContractBehaviorIT hints:
     - Authorized user with a `reasonCode` updates the `standardCost` -> assert `200` and `ItemCostAudit` record exists.
     - Missing `reasonCode` -> expect `400` and no DB update.
     - Unauthorized user -> expect `403`.

2) `GET /v1/products/items/{itemId}/costs` (gateway: `http://localhost:8080/v1/products/items/{itemId}/costs`)
   - Purpose: Read the current costing trio for an item: `{ standardCost, lastCost, averageCost }`.
   - Response schema:
     ```ts
     interface ItemCostsResponse {
       itemId: string;
       standardCost?: string | null;
       lastCost?: string | null;
       averageCost?: string | null;
       currencyCode?: string | null;
     }
     ```
   - Responses: `200 OK`, `404 Not Found`, `403 Forbidden`
   - Behavioral assertions:
     - Initial values for a newly created item MUST be `null` (not zero).
     - `lastCost` and `averageCost` are system-managed and MUST NOT be editable via this endpoint.
   - ContractBehaviorIT hints:
     - For new item, GET -> all three costs `null`.
     - After simulated purchase receipt (unit-tested or via event ingestion), GET -> `lastCost` and `averageCost` updated per WAC formula.

3) `GET /v1/products/items/{itemId}/costs/audit` (gateway: `http://localhost:8080/v1/products/items/{itemId}/costs/audit`)
   - Purpose: Query the `ItemCostAudit` history for an item.
   - Query parameters: `fromTimestamp?`, `toTimestamp?`, `costType?` (STANDARD|LAST|AVERAGE), `page`, `size`
   - Response schema (page):
     ```ts
     interface ItemCostAuditEntry {
       auditId: string;
       itemId: string;
       timestamp: string;
       costTypeChanged: 'STANDARD' | 'LAST' | 'AVERAGE';
       oldValue?: string | null;
       newValue?: string | null;
       changeSourceType: 'MANUAL' | 'PURCHASE_ORDER';
       changeSourceId?: string | null;
       actor?: string | null;
       reasonCode?: string | null; // required for MANUAL standard cost changes
     }
     ```
   - Responses: `200 OK` + paged audit entries, `404 Not Found` (if item missing), `403 Forbidden`
   - Behavioral assertions:
     - Audit writes for cost changes MUST be atomic with the cost update; on audit write failure the cost update must roll back (transactional integrity).
     - `reasonCode` MUST be present for MANUAL `STANDARD` changes and surfaced in the audit record.
   - ContractBehaviorIT hints:
     - After a standard-cost manual change, query audit -> expect an entry with `changeSourceType=MANUAL` and provided `reasonCode`.
     - After a purchase-order receipt (simulate event), audit entries for `LAST` and `AVERAGE` should exist and be linked to the source purchase order id.

Business rules (from Issue #196):
- `standardCost`: manual-only updates, requires permission `inventory.cost.standard.update` and `reasonCode`.
- `lastCost`: system-managed only (updated on purchase receipts), manual edits rejected with `400`.
- `averageCost`: system-managed only (WAC), manual edits rejected with `400`.
- Initial values for `standardCost`, `lastCost`, and `averageCost` MUST be `null`.
- WAC formula MUST be implemented exactly as specified:
  ```
  NewAverageCost = ((OldQtyOnHand * OldAverageCost) + (ReceivedQty * ReceivedUnitCost)) / (OldQtyOnHand + ReceivedQty)
  ```
- Every cost change MUST create an `ItemCostAudit` record; audit writes are part of the same transaction.
- `403 Forbidden` on unauthorized access.

Error codes & semantics (recommended):
- `INVALID_TIER_STRUCTURE` -> 400
- `NOT_FOUND` -> 404
- `CONFLICT` -> 409
- `FORBIDDEN` -> 403

---

**Notes for implementers**
- These endpoints are greenfield relative to current `pos-catalog/openapi.json`. Add OpenAPI `paths` and `components/schemas` for `SupplierItemCost`, `CostTier`, `ItemCostsResponse`, and `ItemCostAuditEntry` when implementing.
- Ensure controller methods are annotated with `@EmitEvent` where state changes are significant (create/update/delete) per project event auditing guidance.
- Add ArchUnit tests where applicable to maintain package encapsulation.
- Add ContractBehaviorIT coverage for the scenarios described above (happy path, validation, authorization, audit transactional semantics).

## CAP-167: MSRP & Base Pricing Policies

Status: `draft`

### Section 1: MSRP Management Endpoints

All gateway URLs: `http://localhost:8080/v1/products/{productId}/msrp{...}`

1. `POST /v1/products/{productId}/msrp` (gateway: `http://localhost:8080/v1/products/{productId}/msrp`) — create MSRP record
   - Purpose: Create a time-bound MSRP record for a product.
   - Request (TypeScript-like):
     ```ts
     interface CreateMsrpRequest {
       amount: string; // decimal as string, scale 4
       currency: string; // ISO 4217, e.g. "USD"
       effectiveStartDate: string; // YYYY-MM-DD
       effectiveEndDate?: string | null; // YYYY-MM-DD or null for open-ended
       createdByUserId: string;
     }
     ```
   - Responses:
     - `201 Created` + `ProductMsrpDto`
     - `400 Bad Request` for invalid dates or payload
     - `403 Forbidden` for insufficient permissions
     - `409 Conflict` when temporal overlap detected
   - Behavioral assertions:
     - Temporal uniqueness: no overlapping effective ranges for the same `productId` — return `409 Conflict` on overlap.
     - `effectiveEndDate` may be `null` only for the currently latest record.
   - ContractBehaviorIT hints: CP-167-MSRP-001 (happy path create), VE-167-MSRP-001 (invalid date logic), VE-167-MSRP-002 (overlap -> 409)

2. `PUT /v1/products/{productId}/msrp/{msrpId}` (gateway: `http://localhost:8080/v1/products/{productId}/msrp/{msrpId}`) — update MSRP record
   - Purpose: Update an existing MSRP record prior to its `effectiveEndDate` (subject to historical immutability rules below).
   - Request (TypeScript-like): same as `CreateMsrpRequest` plus optional `version` field for optimistic locking.
   - Responses:
     - `200 OK` + `ProductMsrpDto`
     - `400 Bad Request` for invalid dates
     - `403 Forbidden` for insufficient permissions
     - `409 Conflict` for overlap or optimistic locking
   - Behavioral assertions:
     - Historical immutability: once `effectiveEndDate` is in the past for a record, implementations SHOULD treat the record as read-only; updates to historic records are disallowed (return `400` or `403` depending on policy).
   - ContractBehaviorIT hints: CP-167-MSRP-002 (update active msrp), VE-167-MSRP-003 (attempt update historic record -> 4xx)

3. `GET /v1/products/{productId}/msrp/active` (gateway: `http://localhost:8080/v1/products/{productId}/msrp/active`) — get active MSRP
   - Purpose: Return the effective MSRP for the product as of `?asOf=YYYY-MM-DD` (default `today`).
   - Responses:
     - `200 OK` + `ProductMsrpDto`
     - `404 Not Found` when no active MSRP exists for the requested date
   - ContractBehaviorIT hints: CP-167-MSRP-003 (retrieve active msrp), VE-167-MSRP-004 (asOf in future with no record -> 404)

4. `GET /v1/products/{productId}/msrp` (gateway: `http://localhost:8080/v1/products/{productId}/msrp`) — list MSRP history
   - Purpose: Return all MSRP records for audit and history.
   - Responses: `200 OK` + `ProductMsrpDto[]`

**Entity schema: ProductMSRP (from issue #194)**
```ts
interface ProductMsrpDto {
  msrpId: string; // UUID
  productId: string; // UUID
  amount: string; // decimal(19,4) as string
  currency: string; // ISO 4217
  effectiveStartDate: string; // YYYY-MM-DD
  effectiveEndDate?: string | null; // YYYY-MM-DD or null
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
  updatedBy?: string;
}
```

---

## CAP-165: Product Master Data (Parts & Tires)

Status: draft

Gateway base URL (MANDATORY format): `http://localhost:8080/v1/products`

Summary: Product Master Data for parts & tires including core product CRUD, lifecycle management, replacements, search, and UOM conversions. Backend issues: #57 (core product CRUD), #55 (lifecycle state + replacements), #56 (UOM conversions).

Note (OpenAPI authoritative): the current `pos-catalog/openapi.json` includes some lifecycle and replacement paths (e.g., `GET/PUT /v1/products/{productId}/lifecycle` and `POST /v1/products/{productId}/replacements`) but several CAP-165 endpoints are greenfield and must be added to OpenAPI during implementation. Where OpenAPI already defines a path, use OpenAPI as the source-of-truth and extend behavior here only for planned additions.

Endpoints (planned additions / contract):

1) Core Product Record (Issue #57)

- `POST /v1/products` — create product
  - Gateway URL: `http://localhost:8080/v1/products`
  - Purpose: create a new product record (parts or tire). Server assigns `id` (UUID).
  - Request (TypeScript-like):
    ```ts
    interface CreateProductRequest {
      name: string;
      shortDescription?: string;
      longDescription?: string;
      sku: string; // unique, immutable after create
      manufacturerId?: string | null; // uuid
      manufacturerPartNumber?: string | null; // MPN
      type?: 'PART' | 'TIRE' | 'SERVICE' | 'NONINVENTORY';
      dimensions?: Array<{ unit: string; value: number }>;
      specifications?: Record<string,string>;
      createdByUserId: string; // uuid
    }
    ```
  - Responses:
    - `201 Created` + `ProductDto` (created resource)
    - `400 Bad Request` for validation errors
    - `409 Conflict` when SKU already exists OR (manufacturerId + manufacturerPartNumber) pair duplicates existing product
  - Behavior:
    - SKU is globally unique and immutable after creation. Attempts to create product with duplicate SKU return 409.
    - Manufacturer+MPN pair must be unique; server returns 409 on duplicate.
    - Product defaults to `ACTIVE` on creation unless lifecycle explicitly provided and permitted by policy.
    - Emits `CATALOG_ITEM_CREATE` / `CATALOG_ITEM_UPDATE` events as appropriate.
  - ContractBehaviorIT hints: CP-001 (happy path create), VE-001 (validation: missing/invalid sku), CC-001 (concurrent create duplicate SKU -> 409)

- `GET /v1/products/{productId}` — get product by ID
  - Gateway URL: `http://localhost:8080/v1/products/{productId}`
  - Purpose: fetch full product representation
  - Responses: `200 OK` + `ProductDto`, `404 Not Found` if id missing
  - Auth: read roles (`ROLE_ADMIN` or `ROLE_CATALOG_VIEW`) per guide
  - Test hints: CP-002 (happy path fetch), VE-002 (invalid UUID), LC-002 (product moved to DISCONTINUED still retrievable)

- `PUT /v1/products/{productId}` — update product
  - Gateway URL: `http://localhost:8080/v1/products/{productId}`
  - Purpose: update mutable product fields
  - Request (TypeScript-like):
    ```ts
    interface UpdateProductRequest {
      name?: string;
      shortDescription?: string;
      longDescription?: string;
      // SKU is immutable; if provided and differs, return 400
      sku?: string;
      manufacturerId?: string | null;
      manufacturerPartNumber?: string | null;
      dimensions?: Array<{ unit: string; value: number }>;
      specifications?: Record<string,string>;
      updatedByUserId: string; // uuid
      version?: number; // optimistic locking optional
    }
    ```
  - Responses: `200 OK` + `ProductDto`, `400 Bad Request` if attempt to change SKU, `404 Not Found`, `409 Conflict` on optimistic locking or business-rule conflicts (e.g., duplicate MPN+manufacturer)
  - Behavior:
    - SKU is immutable: if `sku` present in payload and not equal to stored SKU, return `400 Bad Request` (client error)
    - Updating manufacturer+mpn pair must enforce uniqueness (409 on conflict)
    - Idempotency: PUT is idempotent for the same payload + version
  - Test hints: CP-003 (happy path update), VE-003 (attempt change sku -> 400), CC-002 (optimistic lock -> 409)

- `POST /v1/products/{productId}/status` — change status (ACTIVE/INACTIVE)
  - Gateway URL: `http://localhost:8080/v1/products/{productId}/status`
  - Purpose: quick status toggle endpoint for UI convenience (separate from lifecycle which supports DISCONTINUED)
  - Request:
    ```ts
    interface ChangeStatusRequest { status: 'ACTIVE' | 'INACTIVE'; changedBy: string; }
    ```
  - Responses: `200 OK` + `ProductDto`, `400 Bad Request`, `404 Not Found`, `403 Forbidden` if missing role
  - Behavior: emits lifecycle event; treat as a convenience wrapper around lifecycle update (no DISCONTINUED allowed here)
  - Test hints: CP-004, VE-004 (invalid status)

- `GET /v1/products/search?q=...&sku=...&mpn=...` — keyword + exact search
  - Gateway URL: `http://localhost:8080/v1/products/search`
  - Purpose: simple product search supporting full-text `q` and exact `sku` and `mpn` filters
  - Query params:
    - `q` (string, optional) — keyword search across name/description/specs
    - `sku` (string, optional) — exact match
    - `mpn` (string, optional) — manufacturer part number exact match
    - `page`, `size`, `sort` optional pagination
  - Responses: `200 OK` + `Paged<ProductDto>` (page metadata + items), `400 Bad Request` for invalid params
  - Behavior:
    - If `sku` provided, return exact match results and ignore `q` full-text (prefer exact filters first)
    - Search must be stable and paginated
  - Test hints: CP-005 (keyword hits), VE-005 (invalid page size), ID-001 (search is safe to repeat)

2) Product Lifecycle State (Issue #55)

- `PUT /v1/products/{productId}/lifecycle` — set lifecycle state
  - Gateway URL: `http://localhost:8080/v1/products/{productId}/lifecycle`
  - Purpose: set lifecycle state to `ACTIVE` | `INACTIVE` | `DISCONTINUED`
  - Note: this path exists in OpenAPI; implementers should follow OpenAPI definitions and the business rules below.
  - Auth: requires `product:lifecycle:update` permission for lifecycle updates; `product:lifecycle:override_discontinued` required to override DISCONTINUED business rule (see rules)
  - Responses: `200 OK`, `400 Bad Request`, `403 Forbidden`, `404 Not Found`, `409 Conflict` when attempting invalid transitions
  - Business rules:
    - Once `DISCONTINUED`, lifecycle is irreversible by normal flows; reactivation requires `product:lifecycle:override_discontinued` permission and will still return `409` unless override permission present and audit metadata supplied.
  - Test hints: LC-001 (discontinue), LC-003 (attempt reactivate -> 409), VE-006 (invalid state)

- `POST /v1/products/{productId}/replacements` — add replacement product(s)
  - Gateway URL: `http://localhost:8080/v1/products/{productId}/replacements`
  - Purpose: add suggested replacement product(s) for discontinued items
  - Note: path is present in OpenAPI as POST; responses: `201 Created`, `400`, `404` per OpenAPI
  - Test hints: CP-006, VE-007

- `GET /v1/products/{productId}/replacements` — list replacement products
  - Gateway URL: `http://localhost:8080/v1/products/{productId}/replacements`
  - Purpose: list replacement options
  - Responses: `200 OK` + `ReplacementOption[]`, `404 Not Found`
  - Test hints: CP-007

3) UOM Conversions (Issue #56)

- `POST /v1/products/uom-conversions` — create UOM conversion
  - Gateway URL: `http://localhost:8080/v1/products/uom-conversions`
  - Purpose: create a conversion factor between two units of measure
  - Request:
    ```ts
    interface CreateUomConversionRequest {
      fromUomId: string; // uuid
      toUomId: string; // uuid
      conversionFactor: number; // positive non-zero
      createdByUserId: string; // uuid
    }
    ```
  - Responses: `201 Created` + `UomConversionDto`, `400 Bad Request` for invalid factor, `409 Conflict` on duplicate from/to pair
  - Business rules:
    - `conversionFactor` must be > 0
    - Duplicate from/to pair is rejected with `409`
  - Test hints: CP-008, VE-008 (zero/negative factor), CC-003 (duplicate create -> 409)

- `GET /v1/products/uom-conversions` — list all active conversions
  - Gateway URL: `http://localhost:8080/v1/products/uom-conversions`
  - Purpose: return all active (isActive=true) conversions
  - Responses: `200 OK` + `UomConversionDto[]`
  - Test hints: CP-009

- `GET /v1/products/uom-conversions/{id}` — get specific conversion
  - Gateway URL: `http://localhost:8080/v1/products/uom-conversions/{id}`
  - Responses: `200 OK` + `UomConversionDto`, `404 Not Found`
  - Test hints: CP-010

- `PUT /v1/products/uom-conversions/{id}` — update conversion factor
  - Gateway URL: `http://localhost:8080/v1/products/uom-conversions/{id}`
  - Purpose: update only the `conversionFactor` (UOM pair immutable)
  - Request:
    ```ts
    interface UpdateUomConversionRequest { conversionFactor: number; updatedByUserId: string; version?: number }
    ```
  - Responses: `200 OK`, `400 Bad Request` if attempt to change UOM ids, `404 Not Found`, `409 Conflict` on optimistic locking
  - Test hints: CP-011, VE-009

- `DELETE /v1/products/uom-conversions/{id}` — deactivate conversion (soft delete)
  - Gateway URL: `http://localhost:8080/v1/products/uom-conversions/{id}`
  - Purpose: soft-delete by setting `isActive=false`; returns `204 No Content` or `200 OK` with updated record
  - Behavior: conversions are only soft-deleted, never hard-deleted
  - Test hints: CP-012

Key business rules (summary):

- SKU is globally unique and immutable after creation. Any request attempting to create a duplicate SKU or change an existing SKU must return `409` (create) or `400` (update attempt to change SKU).
- Manufacturer + MPN pair must be unique across products. Duplicate pair causes `409 Conflict` on create/update.
- Product defaults to `ACTIVE` on creation.
- Lifecycle states: `ACTIVE`, `INACTIVE`, `DISCONTINUED`. `DISCONTINUED` is irreversible under normal flows; reactivation requires `product:lifecycle:override_discontinued` permission and explicit audit metadata. Attempts to reactivate without override should return `409`.
- UOM conversion `conversionFactor` must be positive (>0); duplicate from/to pair is rejected (`409`); conversions are soft-deleted by setting `isActive=false`.
- Permission: `product:lifecycle:update` required for lifecycle changes; `product:lifecycle:override_discontinued` required to override discontinued irreversible rule.

ContractBehaviorIT naming hints (recommended):

- Happy paths: `CP-###` (e.g., `CP-001` create product, `CP-002` get product)
- Validation errors: `VE-###` (e.g., `VE-001` create missing SKU, `VE-008` UOM conversion factor invalid)
- Lifecycle / lifecycle constraints: `LC-###` (e.g., `LC-001` discontinue, `LC-003` attempt reactivate -> 409)
- Idempotency / concurrency: `ID-###` / `CC-###` as needed

Events emitted (recommended): `CATALOG_ITEM_CREATE`, `CATALOG_ITEM_UPDATE`, `PRODUCT_LIFECYCLE_CHANGE`, `UOM_CONVERSION_CREATE/UPDATE/DEACTIVATE`

Implementation notes for engineers:

- Align OpenAPI with the above planned endpoints: add paths for create/update/search/uom-conversions and status endpoint. Where lifecycle/replacements exist in OpenAPI, rely on OpenAPI definitions and ensure business rules are enforced.
- Ensure SKU and MPN+Manufacturer uniqueness are enforced at DB constraint level with clear 409 semantics.
- Use optimistic locking `version` on update endpoints to return 409 on conflict.


Key behavioral assertions:
- Temporal uniqueness: implementations MUST prevent overlapping ranges per `productId` (return `409 Conflict`).
- Forward-only indefinite pricing: only the latest record may have `effectiveEndDate = null`.
- Historical immutability: records with `effectiveEndDate` in the past are read-only.

### Section 2: Base Price Book Endpoints

All gateway URLs: `http://localhost:8080/v1/products/price-books{...}`

1. `POST /v1/products/price-books` (gateway: `http://localhost:8080/v1/products/price-books`) — create price book
   - Purpose: Create a PriceBook container describing scope and defaults.
   - Request (TypeScript-like):
     ```ts
     interface PriceBookCreateRequest {
       name: string;
       scope: 'COMPANY_DEFAULT' | 'LOCATION' | 'CUSTOMER_TIER';
       scopeId?: string | null; // optional UUID when scope != COMPANY_DEFAULT
       isDefault?: boolean;
       status?: 'ACTIVE' | 'INACTIVE';
     }
     ```
   - Responses: `201 Created` + `PriceBookDto`, `400 Bad Request`, `403 Forbidden`
   - ContractBehaviorIT hints: CP-167-PB-001 (create default pricebook), VE-167-PB-001 (invalid scopeId)

2. `GET /v1/products/price-books/{priceBookId}` — get price book
   - Responses: `200 OK` + `PriceBookDto`, `404 Not Found`

3. `PUT /v1/products/price-books/{priceBookId}` — update price book
   - Responses: `200 OK` + `PriceBookDto`, `400 Bad Request`, `404 Not Found`, `409 Conflict`

4. `POST /v1/products/price-books/{priceBookId}/rules` — add rule
   - Purpose: Add a `PriceBookRule` to match SKUs, categories, or global rules within a `PriceBook`.
   - Request (TypeScript-like):
     ```ts
     interface PriceBookRuleCreateRequest {
       targetType: 'SKU' | 'CATEGORY' | 'GLOBAL';
       targetId?: string | null; // SKU or category id when applicable
       pricingLogic: any; // JSON describing pricing calculation or override (implementation-specific)
       conditionType?: 'CUSTOMER_TIER' | 'LOCATION' | 'NONE';
       conditionValue?: string | null;
       priority?: number; // higher priority wins when applicable
       effectiveStartAt: string; // ISO 8601
       effectiveEndAt?: string | null; // ISO 8601 or null
     }
     ```
   - Responses: `201 Created` + `PriceBookRuleDto`, `400 Bad Request`, `409 Conflict` (conflicting rule)
   - Behavioral assertions: detect rule conflicts (409), persist audit event on create

5. `PUT /v1/products/price-books/{priceBookId}/rules/{ruleId}` — update rule
   - Responses: `200 OK` + `PriceBookRuleDto`, `400 Bad Request`, `404 Not Found`, `409 Conflict`

6. `DELETE /v1/products/price-books/{priceBookId}/rules/{ruleId}` — deactivate rule
   - Purpose: Soft-deactivate rule (set `status` -> `INACTIVE` or `NOT_APPLICABLE_MISSING_BASE` as required)
   - Responses: `204 No Content`, `404 Not Found`

7. `GET /v1/products/price-books/{priceBookId}/rules` — list rules
   - Responses: `200 OK` + `PriceBookRuleDto[]`

8. `POST /v1/products/price-books/resolve-price` (gateway: `http://localhost:8080/v1/products/price-books/resolve-price`) — resolve effective price
   - Purpose: Given product context (productId, locationId?, customerTier?, priceBookId?) return the effective price.
   - Request (TypeScript-like):
     ```ts
     interface ResolvePriceRequest {
       productId: string;
       priceBookId?: string | null;
       locationId?: string | null;
       customerTier?: string | null;
       asOf?: string | null; // ISO date when evaluating temporal rules
     }
     ```
   - Responses: `200 OK` + `ResolvePriceResponse` (includes resolvedAmount, currency, sourceRuleId?, fallbackReason?)
   - Behavioral assertions:
     - Rule precedence: SKU/product rule > Category rule > Global rule > MSRP fallback.
     - Deterministic tie-breaking: when multiple rules have equal precedence and priority, implementations MUST apply deterministic tie-breaking (e.g., ruleId lexicographic) and document the tie-breaker in API docs.
     - Missing base data: when required base data is absent, return a domain-specific state or code; AC defines `NOT_APPLICABLE_MISSING_BASE` status for rule records.
   - ContractBehaviorIT hints: CP-167-PB-002 (sku rule overrides msrp), VE-167-PB-002 (resolve when missing base -> special status), LC-167-PB-003 (rule lifecycle create/update/deactivate)

**Entity schema: PriceBook**
```ts
interface PriceBookDto {
  priceBookId: string; // UUID
  name: string;
  scope: 'COMPANY_DEFAULT' | 'LOCATION' | 'CUSTOMER_TIER';
  scopeId?: string | null;
  isDefault: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}
```

**Entity schema: PriceBookRule**
```ts
interface PriceBookRuleDto {
  ruleId: string; // UUID
  priceBookId: string; // UUID
  targetType: 'SKU' | 'CATEGORY' | 'GLOBAL';
  targetId?: string | null;
  pricingLogic: any; // JSON blob describing calculation
  conditionType?: 'CUSTOMER_TIER' | 'LOCATION' | 'NONE';
  conditionValue?: string | null;
  priority: number;
  effectiveStartAt: string; // ISO 8601
  effectiveEndAt?: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'NOT_APPLICABLE_MISSING_BASE';
  createdByUserId?: string;
  createdAt?: string;
  updatedAt?: string;
}
```

Event contract entries (implementation links):
- https://github.com/louisburroughs/durion-positivity-backend/issues/54
- https://github.com/louisburroughs/durion-positivity-backend/issues/194

---

## CAP-168: Location Store Pricing (Overrides by Location)

Status: `draft`

This capability extends CAP-167 with additional implementation guidance and test scenarios for location-specific price overrides and guardrail management. The authoritative endpoint definitions and schema shapes come from the Product OpenAPI (pos-catalog/openapi.json).

All gateway URLs use the API Gateway format: `http://localhost:8080/v1/products/pricing/...`.

### Endpoints (CAP-168 scope)

1. `POST /v1/products/pricing/location-overrides` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides`) — `createLocationPriceOverride`
  - Method: POST
  - Purpose: Create a location-specific price override and enforce configured guardrails (margin, discount, auto-approval threshold).
  - Request schema: `LocationPriceOverrideCreateRequestDto` (see CAP-168 backend contract file)
  - Successful response: `201 Created` + `LocationPriceOverrideResponseDto`
  - Errors: `400 Bad Request` (guardrail validation failed), `403 Forbidden`
  - Notes: When discount is within `autoApprovalThresholdPercent` the implementation MAY create `ACTIVE` override; when above threshold but within hard limits the implementation MAY create `PENDING_APPROVAL` and route to approver.

2. `POST /v1/products/pricing/location-overrides/{overrideId}/approve` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/approve`) — `approveLocationPriceOverride`
  - Method: POST
  - Purpose: Approve a pending override and activate it as effective location price.
  - Request schema: `LocationPriceOverrideDecisionRequestDto`
  - Successful response: `200 OK` + `LocationPriceOverrideResponseDto`
  - Errors: `404 Not Found`, `409 Conflict` (optimistic locking / version mismatch), `400 Bad Request`

3. `POST /v1/products/pricing/location-overrides/{overrideId}/reject` (gateway: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/reject`) — `rejectLocationPriceOverride`
  - Method: POST
  - Purpose: Reject a pending override, persist rejection metadata, and mark request as terminal.
  - Request schema: `LocationPriceOverrideDecisionRequestDto`
  - Successful response: `200 OK` + `LocationPriceOverrideResponseDto`
  - Errors: `404 Not Found`, `400 Bad Request`, `409 Conflict`

4. `GET /v1/products/pricing/effective-price/{locationId}/{productId}` (gateway: `http://localhost:8080/v1/products/pricing/effective-price/{locationId}/{productId}`) — `getEffectiveLocationPrice`
  - Method: GET
  - Purpose: Resolve the effective price for a given location/product pair. Precedence: `ACTIVE` override preferred, otherwise base price.
  - Successful response: `200 OK` + `EffectiveLocationPriceResponseDto`
  - Errors: `404 Not Found` when no pricing context exists

5. `POST /v1/products/pricing/guardrail-policies` (gateway: `http://localhost:8080/v1/products/pricing/guardrail-policies`) — `upsertLocationGuardrailPolicy`
  - Method: POST
  - Purpose: Create or update the active guardrail policy used to evaluate location price overrides.
  - Request schema: `GuardrailPolicyUpsertRequestDto`
  - Successful response: `200 OK`
  - Errors: `400 Bad Request`

### Authorization and Roles

Follow the guide's Authorization Contract: Create/update mutation endpoints are expected to require `ROLE_ADMIN` or `ROLE_CATALOG_EDIT`. Read endpoints (like `getEffectiveLocationPrice`) should require `ROLE_ADMIN` or `ROLE_CATALOG_VIEW`.

### Behavioral Assertions (non-exhaustive)

- Guardrail enforcement: implementations MUST validate `minMarginPercent` and `maxDiscountPercent` and return `400 Bad Request` when hard limits are violated (OpenAPI `400` responses). Use standardized error envelope when available.
- Approval flow: if `autoApprovalThresholdPercent` is exceeded, record override as `PENDING_APPROVAL` and create an approval request; approvers use the approve/reject endpoints to transition state.
- Optimistic locking: approval/rejection endpoints return `409 Conflict` on version mismatch (schema includes `version` field).

### Test hints (ContractBehaviorIT)

- CP-101 (happy path): create override within auto-approval threshold -> `201 Created` with `status: ACTIVE` or `PENDING_APPROVAL` depending on threshold semantics; `getEffectiveLocationPrice` returns the expected effective price.
- VE-101 (validation error): create override that violates `minMarginPercent` -> `400 Bad Request` with guardrail error code in response.
- AUTH-101 (authorization): request without required role -> `403 Forbidden`.
- ID-101 (idempotency): repeated identical create requests — assert behavior consistent with service idempotency policy (if `Idempotency-Key` is supported return same resource or idempotent response; otherwise expect either creation of distinct overrides or a defined conflict). Document expected chosen behavior in PR.
- LC-101 (lifecycle): create override that is `PENDING_APPROVAL`, then `POST /approve` -> `200 OK` and `getEffectiveLocationPrice` reflects the activated override.

Refer to the CAP-168 backend contract document for full schema definitions, JSON examples, and ContractBehaviorIT payload hints.


## CAP-166: Supplier/Vendor Cost Tiers (Optional)

Status: `draft`

### Endpoints

1. `POST /v1/products/costs/supplier-item`
   - Purpose: Create supplier-item cost structure with optional quantity tiers
   - Response: `201 Created` + `SupplierItemCostResponseDto`
   - Errors:
     - `400 Bad Request` for invalid tier shape or invalid monetary values
     - `403 Forbidden` for missing edit authority

2. `GET /v1/products/costs/supplier-item/{supplierId}/{itemId}`
   - Purpose: Retrieve cost structure for supplier-item combination
   - Response: `200 OK` + `SupplierItemCostResponseDto`
   - Errors:
     - `404 Not Found` when no cost structure exists

3. `PUT /v1/products/costs/supplier-item/{supplierId}/{itemId}`
   - Purpose: Update currency/base cost/tier list for an existing supplier-item structure
   - Response: `200 OK` + `SupplierItemCostResponseDto`
   - Errors:
     - `400 Bad Request` for invalid tier shape or invalid monetary values
     - `404 Not Found` when no cost structure exists
     - `409 Conflict` on optimistic-locking conflict

4. `DELETE /v1/products/costs/supplier-item/{supplierId}/{itemId}`
   - Purpose: Delete supplier-item cost structure and all tiers
   - Response: `204 No Content`
   - Errors:
     - `404 Not Found` when no cost structure exists

### Validation Rules

- Quantity tiers MUST start at `min_quantity = 1`.
- Quantity tiers MUST be contiguous and non-overlapping.
- Only the final tier can have `max_quantity = null`.
- Final tier MUST have `max_quantity = null`.
- `unit_cost` must be positive.
- `base_cost` cannot be negative.
- `currency_code` must be a 3-letter ISO code.

### Example Request (Create)

```json
{
  "supplierId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "itemId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "currencyCode": "USD",
  "baseCost": 6.25,
  "tiers": [
    { "minQuantity": 1, "maxQuantity": 10, "unitCost": 5.00 },
    { "minQuantity": 11, "maxQuantity": 50, "unitCost": 4.50 },
    { "minQuantity": 51, "maxQuantity": null, "unitCost": 4.00 }
  ]
}
```

### Error Example (Overlapping Tiers)

```json
"INVALID_TIER_STRUCTURE: Quantity ranges overlap."
```

---

## Capability Contract Template

Use the shared template for capability sections:

- `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md`
