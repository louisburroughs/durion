---
title: Product Catalog Backend Contract Guide
domain: product
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-catalog/openapi.yaml
openapi_commit: 0937c73e
last_verified_utc: 2026-09-07T15:30:00Z
last_updated: 2026-09-07
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
| CAP-324 | `durion-positivity-backend#1352`, `#1645` | draft | Vendor tread-design (MKCAT) enrichment matching and review — reads delivered by #1352; review/resolve delivered by backend PR-4 (#1645, ADR-0060, PR #1846) |
| Tier 0 | `durion-positivity-backend#1569`, `#1575` | draft | Estimated service time (book time) and Durion-owned service data — vehicle-keyed labor standards with provenance (#1569 Phases 0-1), plus shop-scoped times, source-conflict curation and service packages (#1575 Tier 0) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete a catalog item | `deleteCatalogItem` | DELETE | `/v1/catalog-items/{type}/{catalogId}` | Refer to generated API reference for payload details |
| List a service's labor standards | `listServiceLaborStandards` | GET | `/v1/catalog-items/service/{serviceId}/labor-standards` | Refer to generated API reference for payload details |
| Author a labor standard | `createServiceLaborStandard` | POST | `/v1/catalog-items/service/{serviceId}/labor-standards` | DURION-source rows only; corrections supersede |
| Correct a labor standard | `supersedeServiceLaborStandard` | POST | `/v1/catalog-items/service/{serviceId}/labor-standards/{standardId}/supersede` | Replaces rather than edits; the old row stays readable |
| Review where sources disagree | `listLaborStandardConflicts` | GET | `/v1/catalog/labor-standards/conflicts` | Curation report, not a quote path |
| Resolve a labor time for a vehicle | `resolveLaborTime` | POST | `/v1/catalog/labor-times/resolve` | Service-to-service edge; pos-workorder only |
| List service packages | `listServicePackages` | GET | `/v1/service-packages` | Fleet requirement sets excluded unless asked for |
| Create a service package | `createServicePackage` | POST | `/v1/service-packages` | Refer to generated API reference for payload details |
| Read one service package | `getServicePackage` | GET | `/v1/service-packages/{packageId}` | Includes inactive packages |
| Add an operation to a package | `addServicePackageMember` | POST | `/v1/service-packages/{packageId}/members` | Returns the whole package |
| Remove an operation from a package | `removeServicePackageMember` | DELETE | `/v1/service-packages/{packageId}/members/{memberId}` | Returns the whole package |
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
| List a tread design's candidate products | `listTreadDesignCandidates` | GET | `/v1/catalog/tread-designs/{treadDesignId}/candidates` | `catalog:tread_design:view`; 404 when no such design; empty array is a real answer (nothing scored above the review floor) |
| Resolve a tread design's match | `resolveTreadDesign` | POST | `/v1/catalog/tread-designs/{treadDesignId}/resolve` | `catalog:tread_design:resolve`; body `{action: ATTACH\|REJECT\|DEFER, productIds?, note?, deferUntil?}`; 400 for an action/payload mismatch, 404 for unknown design or product, 409 when a named product is already manually attached to a different design |

`listUnmatchedTreadDesigns` gained (ADR-0060, delivered #1645/PR #1846) a `matchState` filter (multi-value, default
`UNMATCHED,REVIEW`; accepts `UNMATCHED`, `REVIEW`, `MATCHED`, `REJECTED`, `DEFERRED`) and a `vendorProfileId`
filter, with response rows additionally carrying `matchState`, `matchStateAt` and up to 20 top candidates
(`productId`, `score`, `tier`).

UI notes for the review flow (#1645):

- The worklist row's age (`matchStateAt`) advances only when the match state actually moves — re-scoring the same
  state does not bump it.
- `ATTACH` marks the named products `MANUAL`; a manual attachment is never re-pointed by a later automatic
  matching pass, so this is how a reviewer's decision is made to stick.
- A `REJECTED` design re-enters matching only when the vendor changes the design's content — a reject alone does
  not get retried on its own.
- Matching thresholds are configurable: `pos.catalog.enrichment.auto-threshold` (default `0.80`),
  `pos.catalog.enrichment.review-threshold` (default `0.50`), and `pos.catalog.enrichment.brand-aliases`
  (brand-name normalization map used before scoring). These are the keys `CatalogEnrichmentProperties` binds
  (ADR-0060 §2–§3 corrected to match).

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

## Tier 0: Estimated Service Time & Durion-Owned Service Data

### Capability Metadata

- Capability ID: Tier 0 (issue-driven; no capability manifest)
- Parent Issue: `durion-positivity-backend#1569` (estimated service time), `durion-positivity-backend#1575` (sourcing tiers)
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-catalog/openapi.yaml`
- ADRs: ADR-0058 (labor-time sourcing architecture), ADR-0059 (naming and operation taxonomy), ADR-0044 amendment 2026-09-02 (the resolve edge), ADR-0026 (grant surface)

pos-catalog's `ServiceEntity` is the system of record for estimated service time. This section
covers the authoring and read surface delivered across #1569 Phases 0-1 and #1575 Tier 0; the
sourcing plan behind it is `durion-positivity-backend/pos-catalog/docs/service-time-sourcing-plan.md`.

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List a service's labor standards | `listServiceLaborStandards` | GET | `/v1/catalog-items/service/{serviceId}/labor-standards` |
| Author a labor standard | `createServiceLaborStandard` | POST | `/v1/catalog-items/service/{serviceId}/labor-standards` |
| Correct a labor standard | `supersedeServiceLaborStandard` | POST | `/v1/catalog-items/service/{serviceId}/labor-standards/{standardId}/supersede` |
| Review cross-source disagreement | `listLaborStandardConflicts` | GET | `/v1/catalog/labor-standards/conflicts` |
| Resolve the applicable labor time | `resolveLaborTime` | POST | `/v1/catalog/labor-times/resolve` |
| List service packages | `listServicePackages` | GET | `/v1/service-packages` |
| Create a service package | `createServicePackage` | POST | `/v1/service-packages` |
| Read one service package | `getServicePackage` | GET | `/v1/service-packages/{packageId}` |
| Add a package member | `addServicePackageMember` | POST | `/v1/service-packages/{packageId}/members` |
| Remove a package member | `removeServicePackageMember` | DELETE | `/v1/service-packages/{packageId}/members/{memberId}` |
| Bulk import service operations | `bulkIngestCatalogServices` | POST | `/v1/catalog/services/bulk-ingest` |
| Bulk import labor standards | `bulkIngestLaborStandards` | POST | `/v1/catalog/labor-standards/bulk-ingest` |
| Bulk import service packages | `bulkIngestServicePackages` | POST | `/v1/service-packages/bulk-ingest` |
| Bulk import package membership | `bulkIngestServicePackageMembers` | POST | `/v1/service-package-members/bulk-ingest` |

### Behavioral Assertions

**Labor standards are vehicle-keyed and provenance-bearing.**

- A stored time is keyed by (service operation, vehicle key, time type, owner). A null vehicle-key
  field is a wildcard, not "unknown"; a row stating a field the request leaves null does not match,
  so a request that does not know the engine never receives an engine-specific time.
- Every row carries `sourceCode` and `sourceRevision`. That is what makes a quoted number
  defensible on an invoice, and it is why corrections **supersede** rather than update: a quote
  made against revision N stays explainable after revision N+1 lands.
- Only `DURION`-source rows are editable through the API. An imported row is corrected by its
  source's next import, and `supersedeServiceLaborStandard` refuses one with `409`.
- No two ACTIVE rows may answer the same (service, time type, owner, vehicle key). The service
  layer pre-checks and answers `409`; a partial unique index catches what concurrent writers slip
  past that check.

**Ownership decides who sees a time (#1575 Tier 0).**

- `ownerScope` is `PLATFORM` (every location resolves it) or `SHOP` (one location's own number).
  `ownerLocationId` is required for `SHOP` and rejected for `PLATFORM`, both `422`.
- A `SHOP` row is still `DURION`-sourced: a shop cannot forge a vendor's provenance.
- The operation taxonomy itself stays platform-global. Only times are scoped.

**Resolution order is fixed and observable.**

1. A `SHOP` row owned by the requesting `locationId` outranks every platform row — ahead of vehicle
   specificity, because a shop that has priced its own work is quoting what it will actually
   charge. A shop row owned by any other location is not a candidate at all.
2. Then most specific vehicle match, then time-type preference, then source precedence, which is
   data (`labor_time_source_policy`) and is category-aware: a policy row naming an operation
   category beats a category-less row for that category.
3. Then live QUERY_ONLY sources under a licence-bounded TTL cache, never persisted (ADR-0058 §4).
4. Then the service's vehicle-agnostic `defaultLaborHours`, graded `DEFAULT_HOURS`.

**`resolveLaborTime` never throws for a miss.** Statuses are
`RESOLVED | NO_TIME_AVAILABLE | SOURCE_UNAVAILABLE`, with `matchGrade`, `sourceCode`,
`sourceRevision` and `ownerScope` on a `RESOLVED` answer. `SOURCE_UNAVAILABLE` means a live source
failed **and** nothing else answered; a clean "nobody publishes this" is `NO_TIME_AVAILABLE`.
Callers degrade — they never fail an estimate over a guide being unreachable.

**Conflicts are overlapping keys, not identical ones.** `listLaborStandardConflicts` reports pairs
from different sources whose vehicle keys *overlap* — every field one row states, the other states
identically or leaves wild. Identical-key cross-source pairs cannot exist today, because the
active-key index carries no `source_code`; the real disagreement is a manufacturer's wildcard
against an aggregator's year/make/model row. Time types are never compared across each other:
warranty time is meant to differ from retail. The report is advisory — resolution still answers
deterministically by precedence.

**Service packages carry authored hours (#1575 Tier 0).**

- `packageLaborHours` is authored, **not** derived from the members. The overlap arithmetic that
  turns member times into a workorder total lives in pos-workorder; deriving it here would give one
  question two answers.
- A member is `required` by default. `required: false` marks an upsell the package offers rather
  than includes.
- A **fleet requirement set** is a package with `fleetPartyId` set and required members. It is
  excluded from a general listing (it belongs to one account and is not on offer); naming a
  `fleetPartyId` includes it.
- Membership is validated against the real service catalog on write: a package naming a service
  that does not exist answers `404` at authoring time rather than failing at quote time. One
  operation appears at most once per package — wanting two is a quantity, and a second membership
  row answers `409`.

**Tier 0 data is invented, labelled, and loaded through the API.** Every Tier 0 labor standard
carries `sourceRevision = 'tier0-fake-2026-09'`. It is placeholder data, not a licensed guide, and
is removable in one statement.

- It arrives through the bulk-ingest operations above rather than through a database seed, because
  a direct insert publishes no fact and would leave every consumer's catalog replica cold against
  exactly these operations. The fixture packs are
  `durion-positivity-backend/scripts/fixtures/seed/alpha/catalog/tier0-*.csv`.
- The bulk operations answer `200` even when rows fail: read `successCount`, `failureCount` and the
  per-row `results`, not the HTTP status. A row the catalog refused names the reason; a row lost to
  a server-side fault carries only `errorCode: INTERNAL_ERROR` and a `correlationId` to quote.
- They upsert on the natural key — operation code, package code, `(package, operation)`, and for a
  labor standard the vehicle key plus source and revision. Re-running a pack converges; it does not
  duplicate. A standard already active under the same revision is a no-op, and a new revision
  supersedes the active row rather than editing it, so the audit trail above still holds.

### Frontend Usage Notes

- Use the operation IDs above as the stable integration keys; read payload shapes from the
  generated API reference.
- Show `sourceCode`, `sourceRevision` and `matchGrade` beside any prefilled time — an unattributed
  number is not defensible to a customer, and `matchGrade` says how confident the vehicle match was.
- `ownerScope: SHOP` is worth surfacing as "your shop's time" so a writer can tell a local number
  from a published one.
- Never treat a non-`RESOLVED` status as an error state; render the line without a prefill.

### ADR Constraints

- ADR-0058: pos-catalog is the system of record; vehicle-specific times live in a child table
  inside pos-catalog, never in pos-vehicle-fitment.
- ADR-0059: decimal hours in tenths, never minutes; `work_session` is never reused for this concept.
- ADR-0044 amendment 2026-09-02: `resolveLaborTime` is granted file-scoped to pos-workorder's
  `CatalogLaborTimeClientImpl`. No other caller, no write path.
- ADR-0026: `ServiceLaborTimeService` is the only granted type in `catalog.service`.

### Events & Dependencies

- `catalog.service.updated` carries `operationCode`, `operationCategory` and `defaultLaborHours`
  additively at `schemaVersion: 2`. Vehicle-specific rows never ride the fact — volume and
  licensing (ADR-0058 §4).
- Event ids: `CATALOG_LABOR_STANDARD_CREATE` / `_SUPERSEDE` / `_LIST` / `_CONFLICTS`,
  `CATALOG_LABOR_TIME_RESOLVE`, `CATALOG_LABOR_GUIDE_IMPORT`,
  `CATALOG_SERVICE_PACKAGE_CREATE` / `_LIST` / `_GET` / `_MEMBER_ADD` / `_MEMBER_REMOVE`.
- Permissions: `catalog:labor_standard:manage` / `:view` / `:import`, `catalog:labor_time:resolve`,
  `catalog:service_package:manage` / `:view`.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-catalog/src/test/java/com/positivity/catalog/internal/`
  — `service/ServiceLaborStandardServiceImplTest`, `service/LaborTimeResolutionServiceImplTest`,
  `service/LaborStandardConflictServiceImplTest`, `service/ServicePackageServiceImplTest`,
  `controller/LaborTimeResolveControllerTest`.
- Add or update tests covering each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-catalog/openapi.yaml`
- OpenAPI source revision: `0937c73e` (branch `claude/tier-0-spec-implementation-o5j539`; adds the #1569 labor-standard surface, #1575 Tier 0 service packages, and the four Tier 0 bulk-ingest operations)
- Last verified UTC: `2026-09-07T15:30:00Z`
- Generated API reference: `domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/product/.business-rules/AGENT_GUIDE.md`
- `domains/product/.business-rules/DOMAIN_NOTES.md`
- `domains/product/.business-rules/BACKEND_API_REFERENCE.generated.md`
