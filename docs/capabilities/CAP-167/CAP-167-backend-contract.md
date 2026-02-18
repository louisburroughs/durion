# CAP-167 — Backend Contract: MSRP & Base Pricing Policies

Domain: product
Version: 0.4
Last Updated: 2026-02-18

This document specifies the backend contract for CAP-167 (MSRP & Base Pricing Policies). It consolidates the backend stories tracked in the product backlog and provides endpoint contracts, TypeScript-like interfaces, JSON examples and ContractBehaviorIT hints for implementers and contract testers.

Backend child issues:
- https://github.com/louisburroughs/durion-positivity-backend/issues/54
- https://github.com/louisburroughs/durion-positivity-backend/issues/194

---

## Summary

CAP-167 adds two related capabilities:

1. Maintain per-product MSRP values over time with effective date ranges (Issue #194).
2. Define and manage Base Price Books and Price Book Rules (Issue #54) that are used to resolve effective prices for products in context (location, customer tier, company default).

Rule precedence for resolution: SKU/product rule > Category rule > Global rule > MSRP fallback.

All gateway URLs use the API Gateway format: `http://localhost:8080/v1/products/...`.

---

## Stories covered

- durion-positivity-backend#54 — Define Base Company Price Book Rules
- durion-positivity-backend#194 — Maintain MSRP per Product with Effective Dates

---

## Event types (to be emitted by pos-catalog)

- `CATALOG_MSRP_CREATE`
- `CATALOG_MSRP_UPDATE`
- `CATALOG_PRICE_BOOK_RULE_CREATE`
- `CATALOG_PRICE_BOOK_RULE_UPDATE`
- `CATALOG_PRICE_BOOK_RULE_DEACTIVATE`

---

## Section A — MSRP Management (Issue #194)

Base gateway prefix: `http://localhost:8080/v1/products/{productId}/msrp`.

### POST /v1/products/{productId}/msrp

- Method: POST
- Path: `/v1/products/{productId}/msrp`
- Purpose: Create a new MSRP record for the specified product.

Request (TypeScript-like):

```ts
interface CreateMsrpRequest {
  amount: string; // decimal as string, precision/scale: DECIMAL(19,4)
  currency: string; // ISO 4217, e.g. "USD"
  effectiveStartDate: string; // YYYY-MM-DD
  effectiveEndDate?: string | null; // YYYY-MM-DD or null
  createdByUserId: string; // UUID
}
```

Responses:
- `201 Created` + `ProductMsrpDto` (body)
- `400 Bad Request` for invalid payload or date logic (end < start)
- `403 Forbidden` for insufficient permissions
- `409 Conflict` when temporal overlap with existing MSRP records for the same `productId` is detected

Behavioral assertions (derived from Issue #194):
- Temporal uniqueness: the system MUST prevent overlapping effective ranges for the same `productId`. Overlap attempts return `409 Conflict`.
- Forward-only indefinite pricing: only the most recent record for a product may have `effectiveEndDate = null`. Attempts to create multiple open-ended records should be rejected.

ContractBehaviorIT hints:
- CP-NNN (happy path): create MSRP covering future dates and verify `GET active` returns correct record.
- VE-NNN (validation): create with `effectiveEndDate < effectiveStartDate` -> expect `400`.
- VE-NNN (conflict): create overlapping record -> expect `409`.

### PUT /v1/products/{productId}/msrp/{msrpId}

- Method: PUT
- Path: `/v1/products/{productId}/msrp/{msrpId}`
- Purpose: Update an MSRP record. Allowed only when record is not historical (see historical immutability rule).

Request: same shape as `CreateMsrpRequest` plus optional optimistic `version`.

Responses:
- `200 OK` + `ProductMsrpDto`
- `400 Bad Request` for invalid payload or attempting to modify immutable/historical record
- `403 Forbidden` for insufficient permissions
- `404 Not Found` when product or msrpId missing
- `409 Conflict` for temporal overlap or optimistic-locking mismatch

Behavioral assertions:
- Historical immutability: once `effectiveEndDate` is in the past (record fully historic), the record is considered read-only; implementations MUST reject updates to historic records (400 or 403).

ContractBehaviorIT hints:
- CP-NNN: update active MSRP and verify persisted change.
- VE-NNN: attempt update historic MSRP -> expect 4xx.

### GET /v1/products/{productId}/msrp/active

- Method: GET
- Path: `/v1/products/{productId}/msrp/active`
- Query: optional `?asOf=YYYY-MM-DD` (default today)
- Purpose: Retrieve the active MSRP record for the product at the given date.

Responses:
- `200 OK` + `ProductMsrpDto`
- `404 Not Found` when no active MSRP exists for the requested date

ContractBehaviorIT hints:
- CP-NNN: create several historical records; `GET active` returns expected record for given `asOf`.

### GET /v1/products/{productId}/msrp

- Method: GET
- Path: `/v1/products/{productId}/msrp`
- Purpose: Return all MSRP records (audit/history) for the product.
- Responses: `200 OK` + `ProductMsrpDto[]`

### ProductMSRP schema (TypeScript-like)

```ts
interface ProductMsrpDto {
  msrpId: string; // uuid
  productId: string; // uuid
  amount: string; // decimal(19,4) as string
  currency: string; // ISO 4217
  effectiveStartDate: string; // YYYY-MM-DD
  effectiveEndDate?: string | null; // YYYY-MM-DD or null (null allowed only for current latest record)
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
  updatedBy?: string; // uuid
}
```

---

## Section B — Base Price Books & PriceBookRules (Issue #54)

Base gateway prefix: `http://localhost:8080/v1/products/price-books`.

### Purpose

PriceBooks group pricing rules by scope (company default, location, customer tier). Rules (`PriceBookRule`) target SKU, Category or Global and contain `pricingLogic` (JSON) used to compute or override prices. The price resolution process must deterministically choose applicable rules and fallback to MSRP when no rule applies.

### Endpoints & Contracts

#### POST /v1/products/price-books

- Method: POST
- Purpose: Create a `PriceBook` container.

Request (TypeScript-like):

```ts
interface PriceBookCreateRequest {
  name: string;
  scope: 'COMPANY_DEFAULT' | 'LOCATION' | 'CUSTOMER_TIER';
  scopeId?: string | null;
  isDefault?: boolean;
  status?: 'ACTIVE' | 'INACTIVE';
}
```

Responses:
- `201 Created` + `PriceBookDto`
- `400 Bad Request`
- `403 Forbidden`

ContractBehaviorIT hints:
- CP-NNN: create default company price book.
- VE-NNN: invalid scopeId for LOCATION -> 400.

#### GET /v1/products/price-books/{priceBookId}

- Method: GET
- Responses: `200 OK` + `PriceBookDto`, `404 Not Found`.

#### PUT /v1/products/price-books/{priceBookId}

- Method: PUT
- Responses: `200 OK` + `PriceBookDto`, `400`, `404`, `409`.

#### POST /v1/products/price-books/{priceBookId}/rules

- Method: POST
- Purpose: Add a `PriceBookRule` to a `PriceBook`.

Request (TypeScript-like):

```ts
interface PriceBookRuleCreateRequest {
  targetType: 'SKU' | 'CATEGORY' | 'GLOBAL';
  targetId?: string | null;
  pricingLogic: any; // free-form JSON describing calculation/override
  conditionType?: 'CUSTOMER_TIER' | 'LOCATION' | 'NONE';
  conditionValue?: string | null;
  priority?: number; // higher wins
  effectiveStartAt: string; // ISO 8601
  effectiveEndAt?: string | null;
}
```

Responses:
- `201 Created` + `PriceBookRuleDto`
- `400 Bad Request` for invalid payload
- `409 Conflict` when rule conflict detected (overlap with same scope/target/priority)

Behavioral assertions:
- Implementations MUST detect and reject conflicting rules (return `409` when a new rule would create non-deterministic precedence overlaps).
- Persist audit events: `CATALOG_PRICE_BOOK_RULE_CREATE` on success.

ContractBehaviorIT hints:
- CP-NNN: create SKU-level rule and verify `resolve-price` returns rule-derived amount.
- VE-NNN: create duplicate/conflicting rule -> 409.

#### PUT /v1/products/price-books/{priceBookId}/rules/{ruleId}

- Method: PUT
- Responses: `200 OK` + `PriceBookRuleDto`, `400`, `404`, `409`.
- Persist `CATALOG_PRICE_BOOK_RULE_UPDATE` event on success.

#### DELETE /v1/products/price-books/{priceBookId}/rules/{ruleId}

- Method: DELETE
- Purpose: Deactivate the rule or mark `status` -> `INACTIVE`/`NOT_APPLICABLE_MISSING_BASE` depending on reason.
- Responses: `204 No Content`, `404 Not Found`.
- Persist `CATALOG_PRICE_BOOK_RULE_DEACTIVATE` event.

#### GET /v1/products/price-books/{priceBookId}/rules

- Method: GET
- Responses: `200 OK` + `PriceBookRuleDto[]`.

#### POST /v1/products/price-books/resolve-price

- Method: POST
- Purpose: Given a product + context, resolve the effective price using price book rules and fallback to MSRP.

Request (TypeScript-like):

```ts
interface ResolvePriceRequest {
  productId: string;
  priceBookId?: string | null;
  locationId?: string | null;
  customerTier?: string | null;
  asOf?: string | null; // ISO date
}
```

Response (TypeScript-like):

```ts
interface ResolvePriceResponse {
  resolvedAmount: string; // decimal as string
  currency: string; // ISO 4217
  source: 'PRICE_BOOK_RULE' | 'MSRP' | 'UNAVAILABLE';
  sourceRuleId?: string | null; // rule that produced price
  fallbackReason?: string | null; // e.g. "MSRP_FALLBACK", "MISSING_BASE_DATA"
}
```

Behavioral assertions for price resolution (key rules):
- Precedence: SKU/product rule > Category rule > Global rule > MSRP fallback.
- Deterministic tie-breaking: when rules have identical precedence and priority, implementations MUST apply a deterministic tie-breaker (e.g., lexicographic `ruleId`) and document it.
- Missing base data handling: where base data required by a rule is absent, implementers should return an explicit state (e.g., `source: UNAVAILABLE` or `fallbackReason: NOT_APPLICABLE_MISSING_BASE`) and avoid returning silently incorrect prices.

ContractBehaviorIT hints:
- CP-NNN: SKU rule present -> resolved price from SKU rule.
- VE-NNN: missing base data -> response indicates UNAVAILABLE / fallback code.
- LC-NNN: create rule -> update rule -> deactivate rule and verify `resolve-price` behavior changes accordingly.

### PriceBook DTOs (TypeScript-like)

```ts
interface PriceBookDto {
  priceBookId: string;
  name: string;
  scope: 'COMPANY_DEFAULT' | 'LOCATION' | 'CUSTOMER_TIER';
  scopeId?: string | null;
  isDefault: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}
```

```ts
interface PriceBookRuleDto {
  ruleId: string;
  priceBookId: string;
  targetType: 'SKU' | 'CATEGORY' | 'GLOBAL';
  targetId?: string | null;
  pricingLogic: any; // JSON blob
  conditionType?: 'CUSTOMER_TIER' | 'LOCATION' | 'NONE';
  conditionValue?: string | null;
  priority: number;
  effectiveStartAt: string;
  effectiveEndAt?: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'NOT_APPLICABLE_MISSING_BASE';
  createdByUserId?: string;
  createdAt?: string;
  updatedAt?: string;
}
```

---

## ContractBehaviorIT Naming Hints (recommended prefixes)

- CP-NNN: happy path scenarios (e.g., CP-167-001 for MSRP create happy path)
- VE-NNN: validation/error scenarios (e.g., VE-167-001 for date validation)
- LC-NNN: lifecycle scenarios (create -> update -> deactivate)

Provide test IDs and full request/response JSON in ContractBehaviorIT test files next to `pos-catalog` module tests.

---

## Story Fulfillment Handoff

```yaml
capability_label: CAP:167
capability_id: 167
domain: product
parent_capability_number: 167
parent_capability_url: https://github.com/louisburroughs/durion/issues/167
parent_capability_title: "[CAP] MSRP & Base Pricing Policies"
parent_stories_list: "- [durion#167](https://github.com/louisburroughs/durion/issues/167) — MSRP & Base Pricing Policies"
backend_child_issues: |
  - [durion-positivity-backend#54](https://github.com/louisburroughs/durion-positivity-backend/issues/54)
  - [durion-positivity-backend#194](https://github.com/louisburroughs/durion-positivity-backend/issues/194)
```

---

## JSON Examples

Create MSRP example:

```json
{
  "amount": "199.9900",
  "currency": "USD",
  "effectiveStartDate": "2026-03-01",
  "effectiveEndDate": null,
  "createdByUserId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a537"
}
```

Resolve price example (request):

```json
{
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "priceBookId": "e8f1a8b2-...",
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "asOf": "2026-03-05"
}
```

Resolve price example (response):

```json
{
  "resolvedAmount": "179.9900",
  "currency": "USD",
  "source": "PRICE_BOOK_RULE",
  "sourceRuleId": "a1b2c3d4-...",
  "fallbackReason": null
}
```

---

## Implementation notes for developers

- Use `@EmitEvent` annotations for rule create/update/deactivate and MSRP create/update mutations.
- Ensure temporal uniqueness checks are implemented transactionally to avoid race conditions.
- Document the deterministic tie-breaker used for rule conflicts in the module README and in OpenAPI description fields.

---

*End of CAP-167 backend contract spec.*
