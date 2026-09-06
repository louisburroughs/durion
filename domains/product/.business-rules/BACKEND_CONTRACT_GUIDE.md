---
title: Product Catalog Backend Contract Guide
domain: product
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-catalog/openapi.yaml
openapi_commit: 5c7e840
last_verified_utc: 2026-09-06T00:00:00Z
last_updated: 2026-09-06
api_reference_generated: domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Product Catalog Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Product Catalog domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-catalog/openapi.yaml`
- Generated API reference: `domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/product/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Product Catalog behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-165 | `durion#165` | draft | [CAP] Product Master Data (Parts & Tires) |
| CAP-166 | `durion#166` | draft | [CAP] Cost Management (Acquisition & Cost Models) |
| CAP-167 | `durion#167` | draft | [CAP] MSRP & Base Pricing Policies |
| CAP-168 | `durion#168` | draft | [CAP] Location Store Pricing (Overrides by Location) |
| CAP-170 | `durion#170` | draft | [CAP] Availability & Inventory Visibility (Internal + External) |
| CAP-247 | `durion#247` | draft | [CAP] Catalog Search & Product Viewing (Live Data) |
| CAP-324 | `durion-positivity-backend#1352`, `#1645` | draft | Vendor tread-design (MKCAT) enrichment matching and review — reads delivered by #1352; review/resolve pending backend PR-4 (#1645, ADR-0060) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete a catalog item | `deleteCatalogItem` | DELETE | `/v1/catalog-items/{type}/{catalogId}` | Refer to generated API reference for payload details |
| Delete a catalog | `deleteCatalog` | DELETE | `/v1/catalogs/{catalogId}` | Refer to generated API reference for payload details |
| Deactivate price book rule | `deactivateRule` | DELETE | `/v1/products/price-books/{priceBookId}/rules/{ruleId}` | Refer to generated API reference for payload details |
| Delete supplier cost structure | `deleteCostStructure` | DELETE | `/v1/products/supplier-costs/{id}` | Refer to generated API reference for payload details |
| Deactivate conversion | `deactivateUomConversion` | DELETE | `/v1/products/uom-conversions/{id}` | Refer to generated API reference for payload details |
| Get catalogs by name | `getCatalogByName` | GET | `/v1/catalogs/name/{name}` | Refer to generated API reference for payload details |
| Get a catalog by ID | `getCatalogById` | GET | `/v1/catalogs/{catalogId}` | Refer to generated API reference for payload details |
| Get current item costs | `getItemCosts` | GET | `/v1/products/items/{itemId}/costs` | Refer to generated API reference for payload details |
| Get item cost audit history | `getAuditHistory` | GET | `/v1/products/items/{itemId}/costs/audit` | Refer to generated API reference for payload details |
| Get products by name | `getProductByName` | GET | `/v1/products/name/{name}` | Refer to generated API reference for payload details |
| Get non-inventory products by name | `getNonInventoryProductByName` | GET | `/v1/products/noninventory/name/{name}` | Refer to generated API reference for payload details |
| Get a non-inventory product by ID | `getNonInventoryProductById` | GET | `/v1/products/noninventory/{productId}` | Refer to generated API reference for payload details |
| Get price book | `getPriceBook` | GET | `/v1/products/price-books/{priceBookId}` | Refer to generated API reference for payload details |
| List price book rules | `listRules` | GET | `/v1/products/price-books/{priceBookId}/rules` | Refer to generated API reference for payload details |
| Get effective location price | `getEffectiveLocationPrice` | GET | `/v1/products/pricing/effective-price/{locationId}/{productId}` | Refer to generated API reference for payload details |
| Get a product's vendor tread-design enrichment | `getTreadDesignForProduct` | GET | `/v1/catalog/tread-designs/for-product/{productId}` | `catalog:tread_design:view`; 404 (no body) when the product matches no tread design — an ordinary outcome |
| Work the unmatched tread-design worklist | `listUnmatchedTreadDesigns` | GET | `/v1/catalog/tread-designs/unmatched` | `catalog:tread_design:view`; see `matchState` filter note below |
| List a tread design's candidate products *(pending backend PR-4, #1645)* | `listTreadDesignCandidates` | GET | `/v1/catalog/tread-designs/{treadDesignId}/candidates` | `catalog:tread_design:view`; not yet implemented — see ADR-0060 |
| Resolve a tread design's match *(pending backend PR-4, #1645)* | `resolveTreadDesign` | POST | `/v1/catalog/tread-designs/{treadDesignId}/resolve` | `catalog:tread_design:resolve`; not yet implemented — see ADR-0060 |

`listUnmatchedTreadDesigns` is planned (ADR-0060) to gain a `matchState` filter (multi-value, default
`UNMATCHED,REVIEW`) and a `vendorProfileId` filter, with response rows additionally carrying `matchState`,
`matchStateAt` and top candidates (`productId`, `score`, `tier`); until backend PR-4 (#1645) ships, the operation
returns only the current unmatched set with no state field.

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-165: [CAP] Product Master Data (Parts & Tires)

### Capability Metadata

- Capability ID: CAP-165
- Parent Issue: https://github.com/louisburroughs/durion/issues/165
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete a catalog item | `deleteCatalogItem` | DELETE | `/v1/catalog-items/{type}/{catalogId}` |
| Delete a catalog | `deleteCatalog` | DELETE | `/v1/catalogs/{catalogId}` |
| Deactivate price book rule | `deactivateRule` | DELETE | `/v1/products/price-books/{priceBookId}/rules/{ruleId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-166: [CAP] Cost Management (Acquisition & Cost Models)

### Capability Metadata

- Capability ID: CAP-166
- Parent Issue: https://github.com/louisburroughs/durion/issues/166
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete supplier cost structure | `deleteCostStructure` | DELETE | `/v1/products/supplier-costs/{id}` |
| Deactivate conversion | `deactivateUomConversion` | DELETE | `/v1/products/uom-conversions/{id}` |
| Get catalogs by name | `getCatalogByName` | GET | `/v1/catalogs/name/{name}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-167: [CAP] MSRP & Base Pricing Policies

### Capability Metadata

- Capability ID: CAP-167
- Parent Issue: https://github.com/louisburroughs/durion/issues/167
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get a catalog by ID | `getCatalogById` | GET | `/v1/catalogs/{catalogId}` |
| Get current item costs | `getItemCosts` | GET | `/v1/products/items/{itemId}/costs` |
| Get item cost audit history | `getAuditHistory` | GET | `/v1/products/items/{itemId}/costs/audit` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Reference Price Resolution & Customer-Tier Books (ADR-0054)

- `resolvePrice` (`POST /v1/products/price-books/resolve-price`) resolves **reference/list
  price data only** — never a transactional sell price. Transactional quoting (what a customer
  pays) is owned exclusively by `pos-price` (ADR-0054 §1).
- Candidate price book resolution selects exactly one book in precedence order: explicit
  `priceBookId` → active `LOCATION` book (`locationId`) → active `CUSTOMER_TIER` book
  (`customerTierId`) → `COMPANY_DEFAULT` (`isDefault=true`). A supplied context whose book is
  missing or inactive falls through to the next step (ADR-0054 §3).
- `customerTierId` (UUID) selects a `CUSTOMER_TIER` book by matching the book's `scopeId`;
  the separate `customerTier` (string label) matches rule-level `CUSTOMER_TIER` conditions
  inside the selected book. They are distinct inputs with distinct purposes.
- Catalog customer-tier books define **reference/list prices for a tier**; `pos-price`
  customer-tier *discounts* are the applied transactional mechanism. The two are never
  competing resolvers: pos-price discounting applies on top of, never in competition with,
  the catalog reference price.
- Rules inside a selected customer-tier book resolve by the existing SKU → category → global
  precedence (priority desc, effective-start desc, rule UUID tie-break) — no tier-specific
  rule semantics.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-168: [CAP] Location Store Pricing (Overrides by Location)

### Capability Metadata

- Capability ID: CAP-168
- Parent Issue: https://github.com/louisburroughs/durion/issues/168
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get products by name | `getProductByName` | GET | `/v1/products/name/{name}` |
| Get non-inventory products by name | `getNonInventoryProductByName` | GET | `/v1/products/noninventory/name/{name}` |
| Get a non-inventory product by ID | `getNonInventoryProductById` | GET | `/v1/products/noninventory/{productId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-170: [CAP] Availability & Inventory Visibility (Internal + External)

### Capability Metadata

- Capability ID: CAP-170
- Parent Issue: https://github.com/louisburroughs/durion/issues/170
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get price book | `getPriceBook` | GET | `/v1/products/price-books/{priceBookId}` |
| List price book rules | `listRules` | GET | `/v1/products/price-books/{priceBookId}/rules` |
| Get effective location price | `getEffectiveLocationPrice` | GET | `/v1/products/pricing/effective-price/{locationId}/{productId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-247: [CAP] Catalog Search & Product Viewing (Live Data)

### Capability Metadata

- Capability ID: CAP-247
- Parent Issue: https://github.com/louisburroughs/durion/issues/247
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`

### API Operation References

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get product details with live price + availability | `getProductDetailView` | GET | `/v1/products/{productId}/detail?location_id=` |
| Search catalog by keyword, brand, category | `searchProducts` | GET | `/v1/products/search?q=&brand=&category=&sku=&cursor=&limit=` |

### Behavioral Assertions — Issue #16 (Product Detail View)

| ID | Scenario | Expected HTTP | Body Assertions |
| --- | --- | --- | --- |
| PD-001 | Product exists, both services return valid data | 200 OK | `pricing.status="OK"`, `availability.status="OK"`, `confidence="HIGH"` |
| PD-002 | Product exists, pricing service unavailable | 200 OK | `pricing.status="UNAVAILABLE"`, `availability.status="OK"`, `confidence="MEDIUM"` |
| PD-003 | Product exists, inventory service unavailable | 200 OK | `pricing.status="OK"`, `availability.status="UNAVAILABLE"`, `confidence="MEDIUM"` |
| PD-004 | Product exists, both services unavailable | 200 OK | `pricing.status="UNAVAILABLE"`, `availability.status="UNAVAILABLE"`, `confidence="LOW"` |
| PD-005 | Product not found | 404 Not Found | n/a |

**Cross-service call rules:**
- `pos-catalog` MUST call `pos-price` via `POST /v1/price/quotes` — never compute price locally
- `pos-catalog` MUST call `pos-inventory` via `GET /v1/inventory/availability/query` — never duplicate ATP logic
- Service failures MUST produce graceful degradation (200 partial), not 5xx

### Behavioral Assertions — Issue #17 (Catalog Search)

| ID | Scenario | Expected HTTP | Body Assertions |
| --- | --- | --- | --- |
| CS-001 | No matching products | 200 OK | `data=[]`, `nextCursor=null` |
| CS-002 | Results < page limit | 200 OK | matching items in `data`, `nextCursor=null` |
| CS-003 | Results exceed page limit | 200 OK | `data.length = limit`, `nextCursor` non-null |
| CS-004 | Cursor used for next page | 200 OK | next page items returned |
| CS-005 | SKU exact match present | 200 OK | SKU-matching product first in `data` |
| CS-006 | brand filter applied | 200 OK | only matching brand products in `data` |
| CS-007 | category filter applied | 200 OK | only matching category products in `data` |
| CS-008 | limit > 100 | 200 OK | clamped to 100 results max |

**Search response shape:** `{ data: List<ProductSummary>, nextCursor: String|null }`

`ProductSummary` fields: `productId`, `name`, `sku`, `category`, `thumbnailUrl`, `manufacturerBrand`

### Contract Test Traceability

- `ProductDetailContractBehaviorIT` — covers PD-001 through PD-005
- `ProductSearchContractBehaviorIT` — covers CS-001 through CS-008

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-catalog/openapi.yaml`
- OpenAPI source revision: `5c7e840` (tread-design enrichment reads added #1352; candidates/resolve pending #1645)
- Last verified UTC: `2026-09-06T00:00:00Z`
- Generated API reference: `domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/product/.business-rules/AGENT_GUIDE.md`
- `domains/product/.business-rules/DOMAIN_NOTES.md`
- `domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md`
