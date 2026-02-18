# Product Backend Contract Guide

**Version:** 0.3 (OpenAPI sync)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-18

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

- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/52
  - https://github.com/louisburroughs/durion-positivity-backend/issues/53
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
