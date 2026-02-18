# Product Backend Contract Guide

**Version:** 0.2 (OpenAPI sync)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-17

---

## Overview

This guide defines the Product backend contract for the Product domain, implemented by `pos-catalog`.

Authoritative source for current endpoint inventory is (OpenAPI authoritative):
- `/home/louisb/Projects/durion-positivity-backend/pos-catalog/openapi.json`

---

## Base URL

- Via API Gateway (recommended): `http://localhost:8080/v1/products`
- Service-local OpenAPI server entry may differ by environment.

**Path Format Requirement (MANDATORY)**

- All endpoint paths MUST use the API Gateway format: `http://localhost:8080/v{version}/{domain}/{resource}`.
- Example: OpenAPI path `/v1/products/{productId}` -> gateway URL `http://localhost:8080/v1/products/{productId}`.

***

## Implementation Links / Backlog

- Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/52
- Capability manifest: /docs/capabilities/CAP-167/CAPABILITY_MANIFEST.yaml

Cross-reference: any path refactoring or gateway-routing changes should reference the backend issue(s) above. Prefer the manifest and backend issue referenced here for current coordination (CAP-167 / backend #52).

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

---

## Endpoint Inventory (from OpenAPI)

### Catalog endpoints

1. `POST /v1/products/catalog` (`addCatalog`)
   - Purpose: create catalog
   - Gateway URL: `http://localhost:8080/v1/products/catalog`
  - Response: `201 Created` + `CatalogEntity`, `400` for invalid body

2. `GET /v1/products/catalog/{catalogId}` (`getCatalogById`)
   - Purpose: load catalog by id
   - Gateway URL: `http://localhost:8080/v1/products/catalog/{catalogId}`
  - Response: `200 OK` + `CatalogEntity`, `404` if missing

3. `PUT /v1/products/catalog/{catalogId}` (`updateCatalog`)
   - Purpose: update catalog
   - Gateway URL: `http://localhost:8080/v1/products/catalog/{catalogId}`
  - Response: `200 OK` + `CatalogEntity`, `400` for invalid body, `404` if missing

4. `DELETE /v1/products/catalog/{catalogId}` (`deleteCatalog`)
   - Purpose: delete catalog
   - Gateway URL: `http://localhost:8080/v1/products/catalog/{catalogId}`
  - Response: `204 No Content`, `404` if missing

5. `GET /v1/products/catalog/name/{name}` (`getCatalogByName`)
  - Purpose: retrieve catalogs by display name
  - Gateway URL: `http://localhost:8080/v1/products/catalog/name/{name}`
  - Response: `200 OK` + `CatalogDto`

### Generic item mutation endpoints

1. `POST /v1/products/{type}` (`addCatalogItem`)
   - Allowed `type`: `product`, `service`, `noninventory`
  - Gateway URL: `http://localhost:8080/v1/products/{type}`
   - Response: `201 Created` for supported type/payload
   - Errors: `400` for unsupported type or mismatched payload

2. `PUT /v1/products/{type}/{catalogId}` (`updateCatalogItem`)
   - Allowed `type`: `product`, `service`, `noninventory`
  - Gateway URL: `http://localhost:8080/v1/products/{type}/{catalogId}`
   - Response: `200 OK`, `400` for invalid body, `404` if missing
   - Errors: `400` for unsupported type/payload mismatch

3. `DELETE /v1/products/{type}/{catalogId}` (`deleteCatalogItem`)
   - Allowed `type`: `product`, `service`, `noninventory`
  - Gateway URL: `http://localhost:8080/v1/products/{type}/{catalogId}`
   - Response: `204 No Content`, `400` for invalid type, `404` if missing

### Product/service/non-inventory read endpoints

1. `GET /v1/products/{productId}` (`getProductById`)
2. `GET /v1/products/name/{name}` (`getProductByName`)
  - Gateway URL: `http://localhost:8080/v1/products/name/{name}`
  - Response: `200 OK` + `ProductDto`
3. `GET /v1/products/service/{serviceId}` (`getServiceById`)
  - Gateway URL: `http://localhost:8080/v1/products/service/{serviceId}`
4. `GET /v1/products/service/name/{name}` (`getServiceByName`)
  - Gateway URL: `http://localhost:8080/v1/products/service/name/{name}`
5. `GET /v1/products/noninventory/{productId}` (`getNonInventoryProductById`)
  - Gateway URL: `http://localhost:8080/v1/products/noninventory/{productId}`
6. `GET /v1/products/noninventory/name/{name}` (`getNonInventoryProductByName`)
  - Gateway URL: `http://localhost:8080/v1/products/noninventory/name/{name}`
  - Response: `200 OK` + `NonInventoryProductDto`

 - Responses: `200 OK` when found, `404` for id lookups when missing

### Composed detail and substitutes endpoints

1. `GET /v1/products/product/{productId}` (`getProductDetailView`)
    - Query param: `location_id` (UUID, required)
    - Response: `200 OK` + `ProductDetailView`, `400` invalid input, `404` missing product, `500` server error

2. `GET /v1/products/substitutes/{productId}` (`getPartSubstitutes`)
    - Current behavior: `501 Not Implemented`

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

## CAP-167: Location Store Price Overrides with Guardrails

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
