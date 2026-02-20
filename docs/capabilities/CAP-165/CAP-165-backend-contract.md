# CAP-165: Product Master Data (Parts & Tires) — Backend Contract

**Version:** v0.1
**Generated:** 2026-02-18
**Scope:** Backend contract for Product Master Data (parts & tires). This document complements the Product domain `BACKEND_CONTRACT_GUIDE.md` and contains the implementation-level endpoint spec intended for `pos-catalog`.

Base Gateway URL: `http://localhost:8080/v1/products`

Primary issues: backend #57 (Core Product CRUD + Search), #55 (Lifecycle & Replacements), #56 (UOM conversions).

Important: OpenAPI (`pos-catalog/openapi.json`) is the authoritative source for implemented endpoints. This document specifies planned additions required to implement CAP-165.

---

## Endpoint: Create Product

- HTTP: `POST /v1/products`
- Gateway: `http://localhost:8080/v1/products`
- Summary: Create a new product (parts or tire). Returns created product.
- Request body (JSON / TypeScript-like):

```ts
interface CreateProductRequest {
  name: string;
  shortDescription?: string;
  longDescription?: string;
  sku: string; // unique, immutable
  manufacturerId?: string | null; // uuid
  manufacturerPartNumber?: string | null; // MPN
  type?: 'PART' | 'TIRE' | 'SERVICE' | 'NONINVENTORY';
  dimensions?: Array<{ unit: string; value: number }>;
  specifications?: Record<string,string>;
  createdByUserId: string; // uuid
}
```

- Successful response: `201 Created`
  - Body: `ProductDto` (see OpenAPI `ProductDto` schema)
- Errors:
  - `400 Bad Request` — validation failure
  - `409 Conflict` — duplicate SKU OR duplicate manufacturerId+manufacturerPartNumber pair

- Behavioral assertions:
  - SKU must be globally unique and is immutable after creation.
  - Manufacturer + MPN pair must be unique; server enforces uniqueness (DB unique constraint preferred).
  - Newly created product defaults to `lifecycleState: ACTIVE` unless special lifecycle flags provided and allowed.
  - Emits `CATALOG_ITEM_CREATE` event.

---

## Endpoint: Get Product by ID

- HTTP: `GET /v1/products/{productId}`
- Gateway: `http://localhost:8080/v1/products/{productId}`
- Summary: Retrieve full product representation.
- Responses:
  - `200 OK` + `ProductDto`
  - `404 Not Found` if `productId` does not exist

---

## Endpoint: Update Product

- HTTP: `PUT /v1/products/{productId}`
- Gateway: `http://localhost:8080/v1/products/{productId}`
- Summary: Update mutable product fields. SKU immutable — clients must not attempt to change SKU.
- Request body:

```ts
interface UpdateProductRequest {
  name?: string;
  shortDescription?: string;
  longDescription?: string;
  sku?: string; // if present and differs from stored value -> 400
  manufacturerId?: string | null;
  manufacturerPartNumber?: string | null;
  dimensions?: Array<{ unit: string; value: number }>;
  specifications?: Record<string,string>;
  updatedByUserId: string; // uuid
  version?: number; // optimistic locking
}
```

- Responses:
  - `200 OK` + `ProductDto`
  - `400 Bad Request` if attempt to change SKU
  - `404 Not Found`
  - `409 Conflict` on optimistic locking or manufacturer+MPN uniqueness violation

- Behavioral assertions:
  - The SKU is immutable: any request where `sku` differs from stored SKU MUST return `400`.
  - Updating manufacturer+MPN must enforce uniqueness and return `409` on conflict.
  - Use `version` for optimistic locking where applicable; return `409` when update fails due to concurrent modification.

---

## Endpoint: Quick Status Change

- HTTP: `POST /v1/products/{productId}/status`
- Gateway: `http://localhost:8080/v1/products/{productId}/status`
- Summary: Convenience endpoint to set `ACTIVE` or `INACTIVE` status.
- Request:

```ts
interface ChangeStatusRequest { status: 'ACTIVE' | 'INACTIVE'; changedBy: string; }
```

- Responses: `200 OK` + `ProductDto`, `400 Bad Request`, `404 Not Found`, `403 Forbidden`
- Note: `DISCONTINUED` transitions must use the lifecycle API (below).

---

## Endpoint: Search Products

- HTTP: `GET /v1/products/search`
- Gateway: `http://localhost:8080/v1/products/search?q=<>&sku=<>&mpn=<>` (supports pagination: `page`, `size`, `sort`)
- Summary: Keyword and exact-match search across products. Exact filters (`sku`, `mpn`) take precedence over full-text `q`.
- Responses: `200 OK` + paged list of `ProductDto` objects

- Behavioral assertions:
  - If `sku` present, return exact SKU match and ignore `q` for relevance ordering.
  - Paginate results; include metadata (`total`, `page`, `size`).

---

## Endpoint: Set Lifecycle State

- HTTP: `PUT /v1/products/{productId}/lifecycle`
- Gateway: `http://localhost:8080/v1/products/{productId}/lifecycle`
- Summary: Set lifecycle state to `ACTIVE`, `INACTIVE`, or `DISCONTINUED` (OpenAPI already contains this path).
- Request schema: see OpenAPI `ProductLifecycleUpdateRequest` (supports `lifecycleState`, `effectiveAt`/`effectiveDate`, `changedBy`, `overrideReason`).

- Responses: `200 OK` (success), `400 Bad Request`, `403 Forbidden` (missing permission), `404 Not Found`, `409 Conflict` (business rule violation)

- Business rules:
  - Valid lifecycle states: `ACTIVE`, `INACTIVE`, `DISCONTINUED`.
  - Once set to `DISCONTINUED`, normal flows cannot set it back to `ACTIVE` or `INACTIVE` (irreversible). An override requires `product:lifecycle:override_discontinued` and must be audited; otherwise return `409` on attempts to reactivate.
  - Permission `product:lifecycle:update` is required for lifecycle changes. Additional override permission `product:lifecycle:override_discontinued` is required to override the discontinued rule.

---

## Endpoints: Replacements

- `POST /v1/products/{productId}/replacements` — add replacement option(s) for discontinued product (OpenAPI already defines POST)
  - Responses: `201 Created`, `400 Bad Request`, `404 Not Found`

- `GET /v1/products/{productId}/replacements` — list replacement options
  - Responses: `200 OK` + `ReplacementOption[]`, `404 Not Found`

---

## UOM Conversions

These endpoints manage unit-of-measure conversion factors.

- `POST /v1/products/uom-conversions`
  - Create conversion between two UOMs.
  - Request:
    ```ts
    interface CreateUomConversionRequest {
      fromUomId: string; // uuid
      toUomId: string; // uuid
      conversionFactor: number; // > 0
      createdByUserId: string;
    }
    ```
  - Responses: `201 Created` + `UomConversionDto`, `400 Bad Request`, `409 Conflict` on duplicate from/to pair

- `GET /v1/products/uom-conversions` — list all active conversions (`isActive=true`)
  - `200 OK` + `UomConversionDto[]`

- `GET /v1/products/uom-conversions/{id}` — get conversion by id
  - `200 OK` + `UomConversionDto`, `404 Not Found`

- `PUT /v1/products/uom-conversions/{id}` — update conversion factor only (UOM ids immutable)
  - Request: `{ conversionFactor: number; updatedByUserId: string; version?: number }`
  - Responses: `200 OK`, `400 Bad Request` if attempt to change UOM ids, `404 Not Found`, `409 Conflict`

- `DELETE /v1/products/uom-conversions/{id}` — soft-delete (set `isActive=false`)
  - Responses: `204 No Content` or `200 OK` with updated dto

Business rules:

- `conversionFactor` must be positive (> 0). Returns `400` for zero/negative.
- Duplicate (fromUomId,toUomId) pair must be rejected (`409`).
- Conversions are soft-deleted only; no hard-delete.

---

## Security and Permissions

- Read endpoints: `ROLE_ADMIN` or `ROLE_CATALOG_VIEW`.
- Create/update endpoints: `ROLE_ADMIN` or `ROLE_CATALOG_EDIT`.
- Lifecycle updates: `product:lifecycle:update` (and `product:lifecycle:override_discontinued` for overriding discontinued rule).

---

## Events

- `CATALOG_ITEM_CREATE` — on product create
- `CATALOG_ITEM_UPDATE` — on product update
- `PRODUCT_LIFECYCLE_CHANGE` — on lifecycle changes
- `UOM_CONVERSION_CREATE`, `UOM_CONVERSION_UPDATE`, `UOM_CONVERSION_DEACTIVATE`

---

## ContractBehaviorIT Test Hints

- Use these prefixes in provider contract tests:
  - CP-###: happy-path create/read/update
  - VE-###: validation / input errors
  - LC-###: lifecycle rules and irreversible transitions
  - CC-### / ID-###: concurrency and idempotency checks

Examples:

- `CP-001` — create product happy path (201)
- `VE-001` — create product missing sku (400)
- `VE-003` — update attempt to change SKU (400)
- `LC-003` — attempt to reactivate discontinued product without override (409)

---

## Implementation Notes

- Add OpenAPI paths for create/update/search/uom-conversions. Ensure schemas are added to `components.schemas` (UomConversionDto, CreateUomConversionRequest, etc.).
- Enforce uniqueness via DB constraints and translate constraint violations to `409 Conflict` with clear error codes/messages.
- Use optimistic locking (`version`) on update endpoints to detect concurrent modifications and return `409`.

Contact: Product domain owners and `pos-catalog` implementers for API reviews and OpenAPI PRs.
# CAP-165 Backend Contract Implementation (auto-generated)

Capability: CAP:165 — Product Master Data (Parts & Tires)

Source files:

- Capability manifest: /docs/capabilities/CAP-165/CAPABILITY_MANIFEST.yaml
- OpenAPI (authoritative): $WORKSPACE/durion-positivity-backend/pos-catalog/openapi.json
- Contract guide updated: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md

Backend child issues:

- <https://github.com/louisburroughs/durion-positivity-backend/issues/55>

Summary

- This document captures the current gateway-routed endpoint contract for CAP-165 based on the authoritative OpenAPI spec (pos-catalog/openapi.json).
- OpenAPI is the source of truth. The gateway format required by the platform is: `http://localhost:8080/v{version}/{domain}/{resource}`.

Endpoints (gateway URLs, methods, canonical response codes, primary schema refs)

- POST <http://localhost:8080/v1/products/catalog>
  - operationId: addCatalog
  - Response: 201 Created
  - Schema: `#/components/schemas/CatalogEntity`

- GET <http://localhost:8080/v1/products/catalog/{catalogId}>
  - operationId: getCatalogById
  - Response: 200 `CatalogEntity`, 404 if missing

- PUT <http://localhost:8080/v1/products/catalog/{catalogId}>
  - operationId: updateCatalog
  - Response: 200 `CatalogEntity`, 400, 404

- DELETE <http://localhost:8080/v1/products/catalog/{catalogId}>
  - operationId: deleteCatalog
  - Response: 204 No Content, 404

- POST <http://localhost:8080/v1/products/{type}>
  - operationId: addCatalogItem
  - Path param `type`: product|service|noninventory
  - Response: 201 Created
  - Request schema: `#/components/schemas/CatalogItem`

- PUT <http://localhost:8080/v1/products/{type}/{catalogId}>
  - operationId: updateCatalogItem
  - Response: 200, 400, 404

- DELETE <http://localhost:8080/v1/products/{type}/{catalogId}>
  - operationId: deleteCatalogItem
  - Response: 204, 400, 404

- GET <http://localhost:8080/v1/products/{productId}>
  - operationId: getProductById
  - Response: 200 `ProductEntity`, 404

- GET <http://localhost:8080/v1/products/name/{name}>
  - operationId: getProductByName
  - Response: 200 `ProductEntity[]`

- GET <http://localhost:8080/v1/products/service/{serviceId}>
  - operationId: getServiceById
  - Response: 200 `ServiceEntity`, 404

- GET <http://localhost:8080/v1/products/service/name/{name}>
  - operationId: getServiceByName
  - Response: 200 `ServiceEntity[]`

- GET <http://localhost:8080/v1/products/noninventory/{productId}>
  - operationId: getNonInventoryProductById
  - Response: 200 `NonInventoryProductEntity`, 404

- GET <http://localhost:8080/v1/products/noninventory/name/{name}>
  - operationId: getNonInventoryProductByName
  - Response: 200 `NonInventoryProductEntity[]`

- GET <http://localhost:8080/v1/products/product/{productId}>
  - operationId: getProductDetailView
  - Query param: `location_id` (UUID) **required by OpenAPI**
  - Response: 200 `ProductDetailView`, 400, 404, 500

- GET <http://localhost:8080/v1/products/substitutes/{productId}>
  - operationId: getPartSubstitutes
  - Response: 501 Not Implemented (per OpenAPI)

Notes and actionable TODOs

- All gateway URLs above were derived directly from `pos-catalog/openapi.json` (authoritative). If behavior is missing (for example, detailed error envelope), mark TODO and link to backend issue 55.
- The substitutes endpoint is explicitly `501` in the OpenAPI spec; implementers should correct the status and provide schema if enabling the feature — track in issue 55.
- Contract hardening items (error envelope, idempotency, permission model refactor) remain backlog tasks.

Per-story handoff payloads (for .github/prompts/backend-story-fulfillment.prompt.md)

Story: [CAP] Product Master Data (Parts & Tires)

- parent issue: <https://github.com/louisburroughs/durion/issues/165>
- backend issue: <https://github.com/louisburroughs/durion-positivity-backend/issues/55>
- contract guide path: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
- openapi source (authoritative): pos-catalog/openapi.json

Handoff checklist (suggested fields for the prompt file):

- summary: "Implement/validate endpoints listed in CAP-165 backend contract; ensure gateway routing and response codes match OpenAPI"
- tests_required: ["provider contract tests for each endpoint (status + schema)", "integration test for product detail view location_id behavior", "verify substitutes endpoint status or implementation"]
- docs_updated: ["domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md", "docs/capabilities/CAP-165/CAP-165-backend-contract.md"]
- related_issues: ["https://github.com/louisburroughs/durion-positivity-backend/issues/55"]

---

Generated: 2026-02-17 (automated)
