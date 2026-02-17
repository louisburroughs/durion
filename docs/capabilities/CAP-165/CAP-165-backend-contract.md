# CAP-165 Backend Contract Implementation (auto-generated)

Capability: CAP:165 — Product Master Data (Parts & Tires)

Source files:
- Capability manifest: /docs/capabilities/CAP-165/CAPABILITY_MANIFEST.yaml
- OpenAPI (authoritative): /home/louisb/Projects/durion-positivity-backend/pos-catalog/openapi.json
- Contract guide updated: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md

Backend child issues:
- https://github.com/louisburroughs/durion-positivity-backend/issues/55

Summary
- This document captures the current gateway-routed endpoint contract for CAP-165 based on the authoritative OpenAPI spec (pos-catalog/openapi.json).
- OpenAPI is the source of truth. The gateway format required by the platform is: `http://localhost:8080/v{version}/{domain}/{resource}`.

Endpoints (gateway URLs, methods, canonical response codes, primary schema refs)

- POST http://localhost:8080/v1/products/catalog
  - operationId: addCatalog
  - Response: 201 Created
  - Schema: `#/components/schemas/CatalogEntity`

- GET http://localhost:8080/v1/products/catalog/{catalogId}
  - operationId: getCatalogById
  - Response: 200 `CatalogEntity`, 404 if missing

- PUT http://localhost:8080/v1/products/catalog/{catalogId}
  - operationId: updateCatalog
  - Response: 200 `CatalogEntity`, 400, 404

- DELETE http://localhost:8080/v1/products/catalog/{catalogId}
  - operationId: deleteCatalog
  - Response: 204 No Content, 404

- POST http://localhost:8080/v1/products/{type}
  - operationId: addCatalogItem
  - Path param `type`: product|service|noninventory
  - Response: 201 Created
  - Request schema: `#/components/schemas/CatalogItem`

- PUT http://localhost:8080/v1/products/{type}/{catalogId}
  - operationId: updateCatalogItem
  - Response: 200, 400, 404

- DELETE http://localhost:8080/v1/products/{type}/{catalogId}
  - operationId: deleteCatalogItem
  - Response: 204, 400, 404

- GET http://localhost:8080/v1/products/{productId}
  - operationId: getProductById
  - Response: 200 `ProductEntity`, 404

- GET http://localhost:8080/v1/products/name/{name}
  - operationId: getProductByName
  - Response: 200 `ProductEntity[]`

- GET http://localhost:8080/v1/products/service/{serviceId}
  - operationId: getServiceById
  - Response: 200 `ServiceEntity`, 404

- GET http://localhost:8080/v1/products/service/name/{name}
  - operationId: getServiceByName
  - Response: 200 `ServiceEntity[]`

- GET http://localhost:8080/v1/products/noninventory/{productId}
  - operationId: getNonInventoryProductById
  - Response: 200 `NonInventoryProductEntity`, 404

- GET http://localhost:8080/v1/products/noninventory/name/{name}
  - operationId: getNonInventoryProductByName
  - Response: 200 `NonInventoryProductEntity[]`

- GET http://localhost:8080/v1/products/product/{productId}
  - operationId: getProductDetailView
  - Query param: `location_id` (UUID) **required by OpenAPI**
  - Response: 200 `ProductDetailView`, 400, 404, 500

- GET http://localhost:8080/v1/products/substitutes/{productId}
  - operationId: getPartSubstitutes
  - Response: 501 Not Implemented (per OpenAPI)

Notes and actionable TODOs
- All gateway URLs above were derived directly from `pos-catalog/openapi.json` (authoritative). If behavior is missing (for example, detailed error envelope), mark TODO and link to backend issue 55.
- The substitutes endpoint is explicitly `501` in the OpenAPI spec; implementers should correct the status and provide schema if enabling the feature — track in issue 55.
- Contract hardening items (error envelope, idempotency, permission model refactor) remain backlog tasks.

Per-story handoff payloads (for .github/prompts/backend-story-fulfillment.prompt.md)

Story: [CAP] Product Master Data (Parts & Tires)
- parent issue: https://github.com/louisburroughs/durion/issues/165
- backend issue: https://github.com/louisburroughs/durion-positivity-backend/issues/55
- contract guide path: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
- openapi source (authoritative): pos-catalog/openapi.json

Handoff checklist (suggested fields for the prompt file):
- summary: "Implement/validate endpoints listed in CAP-165 backend contract; ensure gateway routing and response codes match OpenAPI"
- tests_required: ["provider contract tests for each endpoint (status + schema)", "integration test for product detail view location_id behavior", "verify substitutes endpoint status or implementation"]
- docs_updated: ["domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md", "docs/capabilities/CAP-165/CAP-165-backend-contract.md"]
- related_issues: ["https://github.com/louisburroughs/durion-positivity-backend/issues/55"]

---

Generated: 2026-02-17 (automated)
