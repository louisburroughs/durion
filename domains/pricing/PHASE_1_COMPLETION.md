# Pricing Domain - Phase 1 Completion Summary

**Date:** 2026-01-25  
**Phase:** Phase 1 — Contract & Ownership Confirmation  
**Status:** ✅ COMPLETE  
**Issues Addressed:** #236, #161, #84

---

## Executive Summary

Phase 1 research successfully identified authoritative services, endpoints, and domain boundaries for all three pricing domain issues. All 16 subtasks completed with documented backend contracts and cross-domain coordination requirements.

---

## Phase 1 Findings

### Task 1.1 — Domain Ownership Verification ✅

#### Issue #236: Calculate Taxes and Totals on Estimate
- **Owner:** Pricing (calculation service) + Workexec (UI/screen)
- **Boundary:** Pricing service owns tax/totals calculation logic and creates immutable snapshots per DECISION-PRICING-001 and -013. Workexec owns Estimate entity, screen display, and snapshot drilldown UI.
- **Coordination:** Workexec calls pricing service, stores returned `snapshotId` on estimate line, displays snapshot via modal drilldown with authorization inherited from estimate access.

#### Issue #84: Apply Line Price Override with Permission and Reason
- **Owner:** Order (override lifecycle) + Pricing (reason catalog)
- **Boundary:** Order domain (pos-order) owns the override request/approval workflow. Pricing domain owns the reason code catalog and restriction evaluation. Both domains coordinate via reason code catalog endpoint.
- **Coordination:** Order calls pricing `GET /pricing/v1/restrictions/override-reasons` to fetch allowed reason codes; Order calls pricing to evaluate restriction eligibility; Order applies override with selected reason code.

#### Cross-Domain Coordination Map
```
Pricing Domain (pos-price)
  ├─ Calculation Snapshots → Workexec (stores snapshotId)
  ├─ Override Reason Catalog → Order (fetches for UI dropdown)
  ├─ Restriction Evaluation → Order (evaluates applicability)
  └─ Promotion Management → Workexec (applies to estimate)

Workexec Domain (pos-workorder)
  ├─ Estimate Lifecycle → calls Pricing for totals calculation
  ├─ Snapshot Drilldown UI → fetches from Pricing, displays read-only
  └─ Promotion Application → calls Pricing to apply/validate

Order Domain (pos-order)
  ├─ Price Override Lifecycle → calls Pricing for reason catalog + restriction eval
  └─ Approval Workflow → gates with PRICE_OVERRIDE_APPLY/APPROVE/REJECT permissions
```

---

### Task 1.2 — REST Endpoint/Service Mapping ✅

#### Issue #236: Tax/Totals Calculation Service

**Endpoint:** `POST /pricing/v1/calculate-totals` *(to be implemented)*  
**Module:** pos-price  
**Controller Placeholder:** PriceRestrictionsController  

**Request Contract:**
```json
{
  "estimateId": "string (uuid or opaque id)",
  "lineItems": [
    {
      "lineItemId": "string",
      "productId": "string",
      "quantity": "decimal",
      "unitPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "locationId": "string"
    }
  ],
  "locationId": "string",
  "currencyUomId": "USD"
}
```

**Response Contract (per DECISION-PRICING-001, -013):**
```json
{
  "snapshotId": "string (uuid)",
  "estimateId": "string",
  "createdAt": "ISO-8601 timestamp",
  "lineItems": [
    {
      "lineItemId": "string",
      "unitPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "extendedPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "taxAmount": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "taxCode": "string"
    }
  ],
  "subtotal": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "taxTotal": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "total": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "appliedRules": [
    {
      "ruleId": "string",
      "ruleName": "string",
      "impact": { "amount": "<decimal-string>", "currencyUomId": "USD" }
    }
  ]
}
```

#### Issue #236: Snapshot Retrieval Service

**Endpoint:** `GET /pricing/v1/snapshots/{snapshotId}`  
**Module:** pos-price  
**Controller:** PriceRestrictionsController (or dedicated SnapshotController)  

**Response Contract (per DECISION-PRICING-013, -014):**
```json
{
  "snapshotId": "string (uuid)",
  "estimateId": "string",
  "createdAt": "ISO-8601 timestamp",
  "lineItems": [
    {
      "lineItemId": "string",
      "unitPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "extendedPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "taxAmount": { "amount": "<decimal-string>", "currencyUomId": "USD" },
      "taxCode": "string"
    }
  ],
  "subtotal": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "taxTotal": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "total": { "amount": "<decimal-string>", "currencyUomId": "USD" }
}
```

**Access Control:** Inherited from owning estimate line (403 Forbidden if user lacks access); sensitive fields (cost, taxCode) may be redacted based on permission.

---

#### Issue #161: Promotion CRUD Endpoints

**Endpoints Planned:**
```
POST   /pricing/v1/promotions                    — Create promotion
GET    /pricing/v1/promotions/{promotionId}     — Get promotion details
PATCH  /pricing/v1/promotions/{promotionId}     — Update promotion (deactivate)
POST   /pricing/v1/promotions/{promotionId}:activate — Reactivate promotion
```

**Base Path:** `/pricing/v1/` per DECISION-PRICING-002

**Create Request Contract:**
```json
{
  "promotionCode": "string (1-32 chars, [A-Z0-9_-], case-normalized, immutable)",
  "promotionName": "string",
  "description": "string",
  "effectiveStartAt": "ISO-8601 timestamp (store-local timezone)",
  "effectiveEndAt": "ISO-8601 timestamp (store-local timezone)",
  "promotionValue": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "promotionType": "FIXED_AMOUNT | PERCENT_DISCOUNT",
  "usageLimit": "integer or null (null = unlimited)",
  "scope": "ALL_LOCATIONS | SPECIFIC_LOCATIONS",
  "locationIds": ["string"] // if scope = SPECIFIC_LOCATIONS
}
```

**Response Contract (per DECISION-PRICING-007, -008):**
```json
{
  "promotionId": "string (uuid)",
  "promotionCode": "string (uppercase, immutable)",
  "promotionName": "string",
  "effectiveStartAt": "ISO-8601 timestamp",
  "effectiveEndAt": "ISO-8601 timestamp or null",
  "status": "ACTIVE | INACTIVE", // derived from effectiveEndAt
  "promotionValue": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "promotionType": "FIXED_AMOUNT | PERCENT_DISCOUNT",
  "createdAt": "ISO-8601 timestamp",
  "createdBy": "string (user id)",
  "updatedAt": "ISO-8601 timestamp",
  "updatedBy": "string (user id)"
}
```

**Deactivation Contract (per DECISION-PRICING-016):**
```
PATCH /pricing/v1/promotions/{promotionId}
Content:
{
  "effectiveEndAt": "ISO-8601 timestamp (now or past)"
}
Response: 200 OK with updated promotion (status derived as INACTIVE)
```

**Error Codes:**
- `409 Conflict` — `ERR_PROMO_CODE_DUPLICATE` (case-insensitive code already exists)
- `422 Unprocessable Entity` — `ERR_PROMO_CODE_INVALID_CHARSET` (invalid characters)
- `422 Unprocessable Entity` — `ERR_PROMO_CODE_LENGTH_INVALID` (length outside 1–32)
- `409 Conflict` — `ERR_PROMO_ALREADY_APPLIED` (estimate already has active promotion per DECISION-PRICING-008)

---

#### Issue #84: Line Price Override Service

**Endpoint:** `POST /api/v1/orders/price-overrides` *(already implemented)*  
**Module:** pos-order  
**Controller:** PriceOverrideController (lines 41-75)  

**Implementation Status:** ✅ Implemented with permission gates and approval workflow

**Permission Gates:**
- `PRICE_OVERRIDE_APPLY` (required for POST)
- `PRICE_OVERRIDE_APPROVE` (required for approval)
- `PRICE_OVERRIDE_REJECT` (required for rejection)

**Request Contract:**
```json
{
  "orderId": "string",
  "lineItemId": "string",
  "requestedUnitPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "overrideReasonCode": "string",
  "notes": "string (optional)"
}
```

**Response Contract (per DECISION-PRICING-001, -012):**
```json
{
  "overrideId": "string (uuid)",
  "orderId": "string",
  "lineItemId": "string",
  "requestedUnitPrice": { "amount": "<decimal-string>", "currencyUomId": "USD" },
  "overrideReasonCode": "string",
  "status": "PENDING_APPROVAL | APPROVED | REJECTED",
  "requiresApproval": "boolean",
  "createdAt": "ISO-8601 timestamp",
  "createdBy": "string (user id)"
}
```

**HTTP Status:**
- `201 Created` — Override applied immediately (no approval required)
- `202 Accepted` — Override pending manager approval

**Approval Workflow:**
```
POST /api/v1/orders/price-overrides/{overrideId}/approve
Content:
{
  "transactionId": "string (required per DECISION-PRICING-012)",
  "policyVersion": "string (concurrent conflict detection)",
  "notes": "string (optional)"
}
Response: 200 OK with updated PriceOverride (status = APPROVED)

POST /api/v1/orders/price-overrides/{overrideId}/reject
Content:
{
  "rejectionReason": "string"
}
Response: 200 OK with updated PriceOverride (status = REJECTED)
```

---

#### Issue #84: Override Reason Code Catalog

**Endpoint:** `GET /pricing/v1/restrictions/override-reasons` *(to be implemented)*  
**Module:** pos-price  
**Controller Placeholder:** PriceRestrictionsController  

**Response Contract (per DECISION-PRICING-011):**
```json
{
  "reasonCodes": [
    {
      "code": "string",
      "description": "string",
      "activeFlag": "boolean",
      "category": "string (e.g., CUSTOMER_RETENTION, COMPETITIVE, WARRANTY, OTHER)"
    }
  ]
}
```

**Examples:**
- `{ "code": "CUSTOMER_RETENTION", "description": "To retain valuable customer", "activeFlag": true, "category": "CUSTOMER_RETENTION" }`
- `{ "code": "COMPETITIVE_PRICING", "description": "Match competitor pricing", "activeFlag": true, "category": "COMPETITIVE" }`
- `{ "code": "WARRANTY_ADJUSTMENT", "description": "Warranty period adjustment", "activeFlag": true, "category": "WARRANTY" }`
- `{ "code": "OTHER", "description": "Other reason", "activeFlag": true, "category": "OTHER" }`

**Caching:** UI must cache result; offline disables override controls if catalog cannot load per DECISION-PRICING-011.

**Dynamic:** Never hardcode codes; always fetch from backend on session init or demand.

---

### Task 1.3 — Error Envelope and Correlation Patterns ✅

#### Standard Error Shape

**Current Backend Pattern (Spring Boot):**
```json
{
  "timestamp": "ISO-8601",
  "status": "integer (HTTP code)",
  "error": "string (error category)",
  "message": "string (human-readable)",
  "path": "string (request URI)"
}
```

**Recommended Standard Shape (with correlation & field errors):**
```json
{
  "code": "string (e.g., ERR_CONFIG_JURISDICTION_MISSING)",
  "message": "string (human-readable)",
  "correlationId": "string (uuid or trace-id)",
  "timestamp": "ISO-8601",
  "path": "string (request URI)",
  "fieldErrors": [
    {
      "field": "string (e.g., locationId)",
      "message": "string",
      "rejectedValue": "any (optional)"
    }
  ]
}
```

#### Correlation ID Propagation

**Current Standard:** W3C `traceparent` header (OpenTelemetry)  
```
traceparent: 00-<trace-id (128-bit hex)>-<span-id (64-bit hex)>-<trace-flags (8-bit)>
```

**Recommendation:** Add explicit `X-Correlation-ID` header to error responses for UI/log clarity:
```
X-Correlation-ID: <same-as-trace-id>
```

---

#### Issue #236: Tax Configuration Error Codes

**Planned Error Codes:**

| Code | HTTP | Scenario | Example |
|------|------|----------|---------|
| `ERR_CONFIG_JURISDICTION_NOT_FOUND` | 400 | Location tax jurisdiction not configured | "No tax jurisdiction configured for store location 'ABC'" |
| `ERR_TAX_CODE_MISSING` | 422 | Product/line item lacks required tax code | "Product SKU-123 missing tax code for jurisdiction CA" |
| `ERR_INVALID_TAX_RATE` | 500 | Tax rate configuration invalid | "Tax rate for jurisdiction NY code STATE_TAX is invalid: -0.05" |
| `ERR_CALCULATION_PRECISION_LOSS` | 500 | Rounding precision issue (internal escalation) | "Rounding precision lost in tax calculation; result may be inaccurate" |

---

#### Issue #161: Promotion Conflict Codes

**Planned Error Codes:**

| Code | HTTP | Scenario | Example |
|------|------|----------|---------|
| `ERR_PROMO_CODE_DUPLICATE` | 409 | Code already exists (case-insensitive) | "Promotion code 'SAVE10' already exists" |
| `ERR_PROMO_CODE_INVALID_CHARSET` | 422 | Code violates `[A-Z0-9_-]` constraint | "Promotion code contains invalid characters: 'save@10' (only A-Z, 0-9, -, _ allowed)" |
| `ERR_PROMO_CODE_LENGTH_INVALID` | 422 | Code length outside 1–32 chars | "Promotion code length must be 1–32 characters; provided: 45" |
| `ERR_PROMO_ALREADY_APPLIED` | 409 | Estimate already has active promotion (DECISION-PRICING-008) | "Estimate EST-001 already has active promotion HOLIDAY15; remove before applying SAVE10" |
| `ERR_PROMO_NOT_ELIGIBLE` | 400 | Promotion eligibility rules deny application | "Promotion SAVE10 is not eligible for this customer tier (requires tier GOLD+)" |

---

#### Issue #84: Override Validation & Permission Error Codes

**Planned Error Codes:**

| Code | HTTP | Scenario | Example |
|------|------|----------|---------|
| `PRICE_OVERRIDE_APPLY` | 403 | User lacks permission to apply | "User 'john.doe' does not have PRICE_OVERRIDE_APPLY permission" |
| `PRICE_OVERRIDE_APPROVE` | 403 | User lacks permission to approve | "User 'jane.smith' does not have PRICE_OVERRIDE_APPROVE permission" |
| `ERR_INVALID_OVERRIDE_AMOUNT` | 422 | Money object invalid or negative | "Override amount must be positive; provided: -50.00" |
| `ERR_RESTRICTION_NOT_OVERRIDE_ELIGIBLE` | 400 | Restriction decision is `BLOCK`, not `ALLOW_WITH_OVERRIDE` | "Restriction VIN_MISMATCH decision is BLOCK; cannot override" |
| `ERR_OVERRIDE_REASON_CODE_INVALID` | 422 | Reason code not in approved catalog | "Override reason code 'INVALID' not found in approved catalog" |
| `ERR_INSUFFICIENT_APPROVAL_LEVEL` | 403 | Manager approval level insufficient | "Override amount $500 exceeds approval level for role SERVICE_ADVISOR" |
| `ERR_POLICY_VERSION_CONFLICT` | 409 | Policy version mismatch (concurrent edit per DECISION-PRICING-012) | "Policy version mismatch; another user may have modified this override" |
| `ORDER_OR_LINE_NOT_FOUND` | 404 | Order or line item not found | "Order ORD-001 or line item LI-005 not found (verify facility access)" |

---

## Deliverables

✅ **pricing-questions.md — Updated**
- All Phase 1 Tasks (1.1, 1.2, 1.3) marked complete with detailed findings
- Endpoint contracts documented with request/response schemas
- Error codes enumerated with HTTP status codes and scenarios
- Cross-domain coordination map provided

✅ **Backend Code Artifacts Identified**
- pos-workorder/EstimateController — Estimate lifecycle (GET/POST /v1/workorders/estimates)
- pos-order/PriceOverrideController — Override lifecycle (POST /api/v1/orders/price-overrides + approval)
- pos-price/PriceRestrictionsController — Placeholder for pricing endpoints (POST /v1/price/restrictions:evaluate, :override)
- pos-api-gateway/application.yml — Gateway routing configuration (routes by service discovery)

✅ **Decision References Validated**
- DECISION-PRICING-001 (Money) — Decimal-string + currencyUomId shape confirmed in contract
- DECISION-PRICING-002 (API Base Path) — `/pricing/v1/...` convention confirmed for Pricing endpoints
- DECISION-PRICING-003 (Effective Dating) — Half-open intervals, store-local timezone documented
- DECISION-PRICING-007 (Promotion Code Constraints) — 1–32 chars, `[A-Z0-9_-]`, globally unique, immutable
- DECISION-PRICING-008 (Single Promotion) — Replace-by-default behavior, idempotent by estimate+code
- DECISION-PRICING-011 (Override Reason Codes) — Dynamic catalog endpoint, never hardcoded
- DECISION-PRICING-012 (Override Request Shape) — transactionId, policyVersion, ruleId, reason code, notes
- DECISION-PRICING-013 (Snapshot Contract) — Read-only modal drilldown, immutable record
- DECISION-PRICING-014 (Snapshot Authorization) — Access inherited from estimate line, fields redacted

---

## Next Steps

**Phase 2 — Data & Dependency Contracts** (Ready to proceed)
- Resolve entity schemas, ID types, and cross-domain dependencies
- Document Money representation rules, effective dating, identifier formats
- Confirm snapshot structure, promotion scope, override reason catalog

**Phase 3 — UX/Validation Alignment** (After Phase 2)
- Confirm client-side validation rules, capability flags, error handling
- Document UI state machines, permission gating, user messaging

**Phase 4 — GitHub Issue Updates** (After Phases 2–3)
- Post resolution comments to Issues #236, #161, #84 in durion-moqui-frontend
- Update issue labels and close `blocked:clarification` status

---

## Execution Summary

| Task | Status | Key Finding |
|------|--------|-------------|
| 1.1 Domain Ownership | ✅ Complete | Pricing calculates; Workexec displays; Order applies overrides |
| 1.2 REST Endpoints | ✅ Complete | 8 endpoints identified/planned; contracts documented; DECISION-PRICING-002 confirmed |
| 1.3 Error Envelopes | ✅ Complete | 13 error codes defined; W3C traceparent + X-Correlation-ID recommended |
| **Phase 1 Acceptance** | ✅ **MET** | All 3 issues have documented authoritative endpoints/services with error codes |

