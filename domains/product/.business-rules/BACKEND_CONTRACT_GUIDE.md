# Product Backend Contract Guide

**Version:** 0.1 (initialized from `pos-catalog/openapi.json`)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-17

---

## Overview

This guide defines the Product backend contract for the Product domain, implemented by `pos-catalog`.

Authoritative source for current endpoint inventory is:
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

- Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/55
- Capability manifest: /docs/capabilities/CAP-165/CAPABILITY_MANIFEST.yaml

Cross-reference: any path refactoring or gateway-routing changes should reference the backend issue above.

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
   - Purpose: search catalogs by name
   - Gateway URL: `http://localhost:8080/v1/products/catalog/name/{name}`
  - Response: `200 OK` + `CatalogEntity[]`

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
3. `GET /v1/products/service/{serviceId}` (`getServiceById`)
  - Gateway URL: `http://localhost:8080/v1/products/service/{serviceId}`
4. `GET /v1/products/service/name/{name}` (`getServiceByName`)
  - Gateway URL: `http://localhost:8080/v1/products/service/name/{name}`
5. `GET /v1/products/noninventory/{productId}` (`getNonInventoryProductById`)
  - Gateway URL: `http://localhost:8080/v1/products/noninventory/{productId}`
6. `GET /v1/products/noninventory/name/{name}` (`getNonInventoryProductByName`)
  - Gateway URL: `http://localhost:8080/v1/products/noninventory/name/{name}`

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
