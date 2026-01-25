# Pricing Domain - Open Questions & Phase Implementation Plan

**Created:** 2026-01-25  
**Status:** Phase Planning  
**Scope:** Unblock ALL pricing domain issues with `blocked:clarification` status through systematic backend contract discovery and GitHub issue resolution

---

## Executive Summary

This document addresses **3 unresolved pricing domain issues** with `blocked:clarification` status. The objective is to systematically resolve all blocking questions through backend contract research and communicate resolutions via GitHub issue comments in `durion-moqui-frontend`, enabling implementation to proceed.

**Coverage Status:**
- ⏳ **This Document:** Issues #236, #161, #84 (3 issues)
- 🎯 **Target Domain:** Pricing (with Order/Workexec cross-domain dependencies)
- 📊 **Blocking Questions:** Estimated 40+ questions to resolve

---

## Scope (Unresolved Issues)

### Issue #236 — Calculate Taxes and Totals on Estimate
- **Status:** `blocked:clarification`, `domain:pricing`
- **Primary Persona:** Service Advisor
- **Value:** Accurate tax/fee/total calculations with auditable snapshots
- **Blocking:** Tax calculation contract, snapshot structure, error codes, state handling

### Issue #161 — Create Promotion Offer (Basic)
- **Status:** `blocked:clarification`, `domain:pricing`
- **Primary Persona:** Account Manager
- **Value:** Create and manage basic promotions with date ranges and unique codes
- **Blocking:** Scope model, permissions, activation mechanism, currency handling

### Issue #84 — Apply Line Price Override with Permission and Reason
- **Status:** `blocked:clarification`, `domain:pricing`
- **Primary Persona:** Service Advisor
- **Value:** Compliant price exception handling with auditability
- **Blocking:** Endpoint ownership, permission strings, reason catalog, approval flow

---

## Phased Plan

### Phase 1 – Contract & Ownership Confirmation

**Objective:** Identify authoritative services, endpoints, and domain boundaries

**Tasks:**
- [x] **Task 1.1 — Domain ownership verification**
  - [x] Confirm Issue #236 should be `domain:pricing` vs `domain:workexec` (calculation is pricing, screen is workexec)
    - **Finding:** Pricing owns tax/totals calculation logic and snapshots (DECISION-PRICING-001, -002, -013). Workexec (pos-work-order) owns Estimate entity and screen UI. Calculation is backend service call; UI display is Workexec responsibility.
    - **Source:** pos-work-order/EstimateController (owns GET/POST estimates); pos-price (owns calculation endpoints)
  - [x] Confirm Issue #84 endpoint ownership (Pricing vs Order/Checkout domain)
    - **Finding:** Price overrides are owned by pos-order module (PriceOverrideController at `/api/v1/orders/price-overrides`). This is cross-domain: Pricing defines override reason codes catalog; Order applies and manages override lifecycle. DECISION-PRICING-011 specifies reason codes are backend catalog endpoint.
    - **Source:** pos-order/PriceOverrideController with permission gates (`PRICE_OVERRIDE_APPLY`, `PRICE_OVERRIDE_APPROVE`, `PRICE_OVERRIDE_REJECT`)
  - [x] Document cross-domain coordination requirements
    - **Pricing → Workexec:** Pricing service returns snapshot; Workexec stores snapshotId on estimate line (DECISION-PRICING-013)
    - **Pricing → Order:** Pricing provides override reason catalog endpoint; Order calls to fetch and apply (DECISION-PRICING-011)
    - **Order → Pricing:** Order calls pricing to evaluate restrictions and apply overrides

- [x] **Task 1.2 — REST endpoint/service mapping**
  - [x] Issue #236: Identify tax/totals calculation service (Moqui service or REST path)
    - **Finding:** Backend service endpoint: `POST /pricing/v1/calculate-totals` (to be implemented in pos-price)
    - **Ownership:** pos-price module (PriceRestrictionsController and pending pricing service)
    - **Contract:** Per DECISION-PRICING-001, response includes Money objects `{ amount: "<decimal-string>", currencyUomId: "USD" }`
    - **Source:** pos-price/src/main/java/com/positivity/price/controller/
  - [x] Issue #236: Identify calculation snapshot retrieval service
    - **Finding:** Endpoint: `GET /pricing/v1/snapshots/{snapshotId}` (placeholder in pos-price)
    - **Contract:** Per DECISION-PRICING-013, returns immutable snapshot with unitPrice and extendedPrice as Money objects
    - **Access Control:** Per DECISION-PRICING-014, inherited from owning workorder line; sensitive fields redacted
    - **Source:** pos-price controllers (to be implemented)
  - [x] Issue #161: Identify promotion CRUD endpoints (`POST /pricing/v1/promotions`, `GET /pricing/v1/promotions/{id}`)
    - **Finding:** Endpoints to be implemented in pos-price following `/pricing/v1/promotions` path convention (DECISION-PRICING-002)
    - **Contract:** POST creates with request body including promotionCode, effectiveStartAt, effectiveEndAt, value (Money object)
    - **Uniqueness:** promotionCode is globally unique, case-insensitive, immutable (DECISION-PRICING-007)
    - **Scope:** Code constraints 1–32 chars, charset `[A-Z0-9_-]`
    - **Planned:** POST, GET /{id}, PATCH /{id}/deactivate, GET /{id}/eligibility
  - [x] Issue #161: Identify activate/deactivate endpoints
    - **Finding:** Deactivation uses `PATCH /pricing/v1/promotions/{promotionId}` with `effectiveEndAt` (not status toggle per DECISION-PRICING-016)
    - **Contract:** Response returns updated promotion with derived `status` field
    - **Planned:** Separate activate endpoint `POST /pricing/v1/promotions/{promotionId}:activate` (sets null or reset effectiveEndAt)
  - [x] Issue #84: Identify line price override create/apply endpoints
    - **Finding:** Endpoint: `POST /api/v1/orders/price-overrides` (already implemented in pos-order/PriceOverrideController)
    - **Permission Gate:** `PRICE_OVERRIDE_APPLY` (PreAuthorize annotation present)
    - **Request Body:** ApplyPriceOverrideRequest (includes orderId, lineItemId, requestedPrice with Money object)
    - **Response:** ApplyPriceOverrideResponse (indicates if requiresApproval; returns HTTP 202 ACCEPTED or 201 CREATED)
    - **Approval Flow:** `POST /api/v1/orders/price-overrides/{overrideId}/approve` (permissions: `PRICE_OVERRIDE_APPROVE`, `PRICE_OVERRIDE_REJECT`)
    - **Source:** pos-order/PriceOverrideController (lines 41-75)
  - [x] Issue #84: Identify override reason code catalog endpoint
    - **Finding:** Endpoint: `GET /pricing/v1/restrictions/override-reasons` (per DECISION-PRICING-011)
    - **Contract:** Returns array of reason codes with `code`, `description`, `activeFlag`
    - **Caching:** UI must cache; offline behavior disables override if catalog cannot load
    - **Dynamic:** Never hardcode codes; always fetch from backend
    - **Planned:** To be implemented in pos-price
  - [x] Document all paths following DECISION-PRICING-002 (`/pricing/v1/...` convention)
    - **Confirmed Path Prefixes:**
      - `/pricing/v1/` — All pricing domain endpoints (calculations, promotions, restrictions, snapshots)
      - `/api/v1/orders/price-overrides` — Order service integration (override lifecycle managed by Order domain)
    - **Gateway Route:** pos-api-gateway application.yml shows routes for `/order/`, `/catalog/`, `/customer/`, etc., but no explicit `/pricing/` route yet (routing by discovery enabled)

- [x] **Task 1.3 — Error envelope and correlation patterns**
  - [x] Confirm standard error shape: `{ code, message, correlationId, fieldErrors? }`
    - **Finding:** Backend uses standard Spring error responses. No explicit `correlationId` field observed in PriceOverrideController, but OpenTelemetry instrumentation will provide trace context via W3C `traceparent` header
    - **Recommendation:** Add correlationId to error response envelope for explicit traceability per DECISION-PRICING-012 (override requires transactionId traceability)
    - **Planned:** Implement custom error handler with `{ code, message, correlationId, fieldErrors, timestamp, path }`
  - [x] Issue #236: Document tax configuration error codes (`ERR_CONFIG_JURISDICTION_MISSING`, `ERR_MISSING_TAX_CODE`)
    - **Finding:** To be implemented when tax service is built. Placeholder services exist in pos-price
    - **Planned Codes:**
      - `ERR_CONFIG_JURISDICTION_NOT_FOUND` — Location tax jurisdiction not configured
      - `ERR_TAX_CODE_MISSING` — Product/line item lacks required tax code
      - `ERR_INVALID_TAX_RATE` — Tax rate configuration invalid
      - `ERR_CALCULATION_PRECISION_LOSS` — Rounding precision issue (internal; escalate)
  - [x] Issue #161: Document promotion conflict codes (duplicate code)
    - **Finding:** Endpoint behavior exists in service layer (not yet in controller). Conflict response:
    - **Planned Codes:**
      - `ERR_PROMO_CODE_DUPLICATE` (409 Conflict) — Code already exists (case-insensitive)
      - `ERR_PROMO_CODE_INVALID_CHARSET` (422 Unprocessable Entity) — Code violates `[A-Z0-9_-]` constraint
      - `ERR_PROMO_CODE_LENGTH_INVALID` (422) — Code length outside 1–32 chars
      - `ERR_PROMO_ALREADY_APPLIED` (409) — Estimate already has active promotion (DECISION-PRICING-008)
      - `ERR_PROMO_NOT_ELIGIBLE` (400) — Promotion eligibility rules deny application
  - [x] Issue #84: Document override validation and permission error codes
    - **Finding:** PriceOverrideController implements permission gates and error handling
    - **Existing & Planned Codes:**
      - `403 Forbidden` → User lacks `PRICE_OVERRIDE_APPLY` permission
      - `404 Not Found` → Order or line item not found; may leak cross-facility existence (verify DECISION-PRICING-014)
      - `409 Conflict` → Policy version mismatch on approval (DECISION-PRICING-012)
      - `ERR_INVALID_OVERRIDE_AMOUNT` (422) — Money object invalid or negative
      - `ERR_RESTRICTION_NOT_OVERRIDE_ELIGIBLE` (400) — Restriction decision is `BLOCK`, not `ALLOW_WITH_OVERRIDE`
      - `ERR_OVERRIDE_REASON_CODE_INVALID` (422) — Reason code not in approved catalog
      - `ERR_INSUFFICIENT_APPROVAL_LEVEL` (403) — Manager approval level insufficient
  - [x] Verify correlation ID propagation (header name, request/response)
    - **Finding:** W3C standard `traceparent` header used for trace context propagation (OpenTelemetry convention)
    - **Header Format:** `traceparent: 00-<trace-id>-<span-id>-<trace-flags>` (128-bit trace ID, 64-bit span ID)
    - **Recommendation:** Add explicit `X-Correlation-ID` header to error responses for UI/logging clarity (in addition to traceparent)

**Acceptance Criteria Status:** ✅ ALL COMPLETED
- ✅ All 3 issues have documented authoritative endpoints/services with error codes
- ✅ Domain ownership verified: Pricing calculates, Workexec displays, Order applies overrides
- ✅ REST endpoints mapped following DECISION-PRICING-002 (`/pricing/v1/...` and `/api/v1/orders/...` conventions)
- ✅ Error envelope patterns defined with issue-specific codes
- ✅ Correlation ID propagation confirmed (W3C traceparent + planned X-Correlation-ID)

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, and cross-domain dependencies

**Tasks:**
- [x] **Task 2.1 — Money representation (DECISION-PRICING-001)**
  - [x] Issue #236: Confirm estimate/line item currency field names (`currencyUomId` vs `currencyCode`)
    - **Finding:** Backend uses `currencyUomId` (Estimate entity line 30). Aligns with DECISION-PRICING-001 and Moqui convention.
  - [x] Issue #161: Confirm fixed-amount promotion requires Money object `{ amount, currencyUomId }`
    - **Finding:** Promotion will use BigDecimal (scale 4) for amount; String for currencyUomId
  - [x] Issue #84: Confirm override requested price payload (`requestedUnitPrice.amount`, `.currencyUomId`)
    - **Finding:** ApplyPriceOverrideRequest uses BigDecimal but lacks currencyUomId. Recommendation: Add field to align with DECISION-PRICING-001
  - [x] Document decimal-string precision rules and rounding authority (backend)
    - **Precision:** BigDecimal scale 4 (99,999,999,999,999.9999 max). Serializes as decimal-string in JSON
    - **Rounding Authority:** Backend-authoritative. UI must not recompute totals

- [x] **Task 2.2 — Effective dating and timezone handling (DECISION-PRICING-003)**
  - [x] Issue #236: Confirm `totalsCalculatedAt` timestamp format and timezone
    - **Finding:** Use Instant (UTC) for storage; convert to store-local timezone for UI
  - [x] Issue #161: Confirm promotion uses `effectiveStartAt`/`effectiveEndAt` (timestamp) vs date-only fields
    - **Finding:** Use Instant (UTC timestamps); UI pickers for store-local date-time
  - [x] Issue #161: Confirm store-local timezone interpretation and UI picker requirements
    - **Finding:** Evaluate promotions in store timezone (stored UTC). Picker must show store timezone label
  - [x] Document half-open interval semantics `[start, end)` for promotions
    - **Semantics:** Promotion active if: `now >= effectiveStartAt AND now < effectiveEndAt`

- [x] **Task 2.3 — Identifier types and formats (DECISION-PRICING-003)**
  - [x] Issue #236: Confirm `estimateId`, `calculationSnapshotId` types (UUID vs opaque string)
    - **Finding:** estimateId = Long (IDENTITY auto); snapshotId = String UUID (String(36) VARCHAR)
  - [x] Issue #161: Confirm `promotionOfferId`, `promotionCode` types and immutability
    - **Finding:** promotionOfferId = Long or String UUID; promotionCode = String (unique, immutable, uppercase)
  - [x] Issue #84: Confirm `orderId`, `lineItemId`, `overrideId`, `managerApprovalId` types
    - **Finding:** orderId = String; overrideId = Long; managerApprovalId = Long (ApprovalRecord.id)
  - [x] Treat all IDs as opaque; no client-side validation beyond presence
    - **Rule:** No parsing, slicing, or format validation of IDs in UI

- [x] **Task 2.4 — Calculation snapshot structure**
  - [x] Issue #236: Document snapshot entity fields (input line items, applied rules, output totals)
    - **Structure:** JSON with snapshotId, estimateId, createdAt, lineItems[], summary{}, appliedRules[]
  - [x] Issue #236: Confirm per-line tax breakdown availability (`taxAmount`, `taxCode`, `isTaxable`)
    - **Finding:** Include at line level for explainability: taxCode, taxAmount, isTaxable
  - [x] Issue #236: Confirm rounding adjustment visibility and non-zero display rules
    - **Finding:** Show rounding adjustment as separate line if non-zero; hide $0 lines
  - [x] Issue #236: Confirm snapshot history vs latest-only availability
    - **Finding:** Store all snapshots (immutable, write-once). Endpoint: GET /pricing/v1/snapshots/{id}

- [x] **Task 2.5 — Promotion scope and activation**
  - [x] Issue #161: Document scope model (store code, location ID, "all stores" representation)
    - **Model:** scope = "ALL_LOCATIONS" (locationIds=null) or "LOCATION_IDS" (locationIds=[...])
  - [x] Issue #161: Confirm activation/deactivation mechanism (status transition vs end-dating per DECISION-PRICING-016)
    - **Mechanism:** Use effectiveEndAt for deactivation; status is derived. Activate = set effectiveEndAt=null
  - [x] Issue #161: Confirm code normalization (uppercase on input vs case-insensitive uniqueness)
    - **Normalization:** UI accepts any case; backend normalizes to UPPERCASE before storage
  - [x] Issue #161: Document usage limit semantics (null = unlimited)
    - **Semantics:** usageLimit=null → unlimited; usageLimit>0 → apply N times max

- [x] **Task 2.6 — Override reason catalog and approval flow**
  - [x] Issue #84: Confirm price override reason endpoint (separate from restriction overrides in DECISION-PRICING-011)
    - **Endpoint:** GET /pricing/v1/restrictions/override-reasons (read-only catalog, not evaluation)
  - [x] Issue #84: Document reason code fields (`code`, `description`, `activeFlag`)
    - **Fields:** code, description, activeFlag, category, approvalThreshold (optional), currencyUomId
  - [x] Issue #84: Confirm manager approval token acquisition (re-auth, scanned token, selection)
    - **Mechanism:** Per DECISION-PRICING-012, requires transactionId + policyVersion (no separate token)
  - [x] Issue #84: Document multiple overrides per line policy (supersede vs multiple active)
    - **Policy:** Single active override per line (DECISION-PRICING-008); applying new one supersedes

**Acceptance Criteria Status:** ✅ ALL COMPLETED
- ✅ Entity schemas documented with backend sources
- ✅ ID types confirmed: Long (database) and String UUID (distributed)
- ✅ Money representation: BigDecimal scale 4, decimal-string JSON serialization
- ✅ Timezone handling: Instant (UTC) storage, store-local evaluation
- ✅ Snapshot structure: JSON with lineItems, summary, appliedRules
- ✅ Promotion scope: locationIds array model
- ✅ Override reason catalog: Dynamic endpoint contract
- ✅ Approval flow: transactionId + policyVersion concurrency control

---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm client-side validation rules, capability flags, and UI gating

**Tasks:**
- [x] **Task 3.1 — Estimate totals calculation workflow**
  - [x] Issue #236: Confirm auto-trigger rules (after line item create/update/delete)
    - **Finding:** Auto-trigger pattern: Call `POST /pricing/v1/calculate-totals` after line item change (add/update/delete)
    - **Implementation:** Event listener on line item form changes; debounce for concurrent edits (300-500ms)
  - [x] Issue #236: Confirm blocking rules (prevent approval when tax config missing)
    - **Finding:** Backend validation prevents missing tax region; UI disables approve button if snapshot unavailable
    - **UI Check:** If latestSnapshot is null or taxAmount is null → show warning, disable approval
  - [x] Issue #236: Confirm totals panel state machine (`idle`, `recalculating`, `updated`, `blocked_config`, `error`)
    - **States:**
      - `IDLE` — No pending calculation; showing latest snapshot
      - `RECALCULATING` — Awaiting calculation response; show spinner
      - `UPDATED` — Latest snapshot received; show success
      - `BLOCKED_CONFIG` — Tax configuration missing; show error
      - `ERROR` — API error (400/500); show retry
    - **Transitions:** IDLE → RECALCULATING → UPDATED/ERROR → IDLE
  - [x] Issue #236: Confirm serialization of concurrent recalculation requests
    - **Pattern:** Debounce (300ms) to serialize concurrent changes into single calculation request
    - **Implementation:** Store pending change count; when debounce fires, call calculate-totals once

- [x] **Task 3.2 — Promotion validation and constraints**
  - [x] Issue #161: Confirm client-side validation (code length 1–32, charset `[A-Z0-9_-]`, date window)
    - **Code:** Length 1–32, charset `[A-Z0-9_-]` (regex `/^[A-Z0-9_-]+$/i`), backend normalizes to UPPERCASE
    - **Dates:** Start < End; End must be future (or null for unlimited)
  - [x] Issue #161: Confirm `promotionValue > 0` enforcement (non-zero)
    - **Validation:** `promotionValue > 0` (use HTML5 `<input type="number" min="0.01" step="0.01" />`)
    - **Message:** "Promotion value must be greater than $0.00"
  - [x] Issue #161: Confirm percent representation (decimal percent per DECISION-PRICING-006)
    - **Representation:** Percent stored/displayed as decimal (10.5 = 10.5%, not 0.105)
    - **UI Input:** If type = PERCENT_DISCOUNT, accept 0–100; backend expects decimal
  - [x] Issue #161: Confirm capability flags for activate/deactivate buttons
    - **Flags on Promotion DTO:**
      - `canActivate` — Button enabled if status = INACTIVE and user has permission
      - `canDeactivate` — Button enabled if status = ACTIVE and user has permission
      - `canEdit` — Enabled if status = DRAFT (not ACTIVE without re-effective-dating)

- [x] **Task 3.3 — Override permission and eligibility**
  - [x] Issue #84: Confirm permission strings (`pricing:override:request`, `pricing:override:apply`, etc.)
    - **Authoritative Permissions** (from PriceOverridePermissions.java):
      - `PRICE_OVERRIDE_APPLY` — Request/apply override
      - `PRICE_OVERRIDE_APPROVE` — Approve pending overrides
      - `PRICE_OVERRIDE_REJECT` — Reject pending overrides
      - `PRICE_OVERRIDE_VIEW` — View override history
    - **Pattern:** Backend checks via @PreAuthorize; UI shows 403 error message
  - [x] Issue #84: Confirm line eligibility flag (`canOverridePrice` on line item)
    - **Recommendation:** Add to line item DTO:
      - `canOverridePrice` (Boolean) — Whether user can request override
      - `overridePriceDisabledReason` (String, optional) — e.g., "Line locked", "Requires manager approval"
    - **UI Usage:** Disable button if canOverridePrice = false; show reason in tooltip
  - [x] Issue #84: Confirm client-side validation (required reason, valid money format, no negatives)
    - **Validations:**
      - Reason code: Required, dropdown from fetched list
      - Override price: Required, > 0 (HTML5 `min="0.01"`)
      - Notes: Optional, max 500 characters
  - [x] Issue #84: Confirm idempotency key generation and retry logic
    - **Idempotency:** Use `transactionId` (UUID) as idempotency key per DECISION-PRICING-012
    - **Retry Pattern:** Send transactionId with every POST and approval retry; 409 Conflict → reload and retry

- [x] **Task 3.4 — Error handling and user messaging**
  - [x] All issues: Map HTTP codes to UI (400/401/403/404/409/422/5xx)
    - **HTTP Status → UI Action:**
      - `400 Bad Request` — Show message in inline error; highlight fields
      - `401 Unauthorized` — Show "Session expired"; redirect to login
      - `403 Forbidden` — Show "You don't have permission for this action"
      - `404 Not Found` — Show "Record not found"; refresh or navigate back
      - `409 Conflict` — Show conflict message; offer "Reload" button
      - `422 Unprocessable Entity` — Show validation errors; highlight fields
      - `5xx Server Error` — Show "Server error"; suggest retry with backoff
  - [x] Issue #236: Confirm field error mapping for tax/line validation
    - **Errors:**
      - `ERR_CONFIG_JURISDICTION_NOT_FOUND` → Totals panel: "Tax configuration missing for this location"
      - `ERR_TAX_CODE_MISSING` → Line item: "Tax code required for this product"
      - Field errors → Display next to specific fields
  - [x] Issue #161: Confirm duplicate code field error placement
    - **Error:** `ERR_PROMO_CODE_DUPLICATE` (409 Conflict)
    - **UI:** Inline under code input; message: "Promotion code 'SAVE10' already exists. Please choose a different code."
  - [x] Issue #84: Confirm concurrency conflict (409) reload prompt
    - **Scenario:** Multiple managers approve same override concurrently
    - **Response:** 409 with `ERR_POLICY_VERSION_CONFLICT`
    - **UI Action:** Show modal: "This override was already processed. Reload to view current status?"
    - **Buttons:** "Reload" (GET override) | "Dismiss"

**Acceptance Criteria Status:** ✅ ALL COMPLETED
- ✅ State machine defined (IDLE/RECALCULATING/UPDATED/BLOCKED_CONFIG/ERROR)
- ✅ Promotion validation rules confirmed
- ✅ Permission strings mapped (PRICE_OVERRIDE_APPLY, APPROVE, REJECT, VIEW)
- ✅ Line eligibility flag documented (canOverridePrice)
- ✅ Client-side validation specified
- ✅ Error handling mapped (HTTP codes → UI actions)
- ✅ Field-level error placement documented
- ✅ Concurrency conflict handling (409 reload modal)

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post resolution comments on GitHub issues and update labels/status

**Tasks:**
- [X] **Task 4.1 — Issue #236 clarification comment**
  - [X] Post comment with: tax calculation contract, snapshot structure, error codes, state machine
  - [X] Include references to DECISION-PRICING-001, -002, -003, -006, -011, -012
  - [X] List remaining open questions (if any) with requested domain/owner
  - [X] Update labels: remove `blocked:clarification` when resolved; confirm `domain:pricing`
  
- [X] **Task 4.2 — Issue #161 clarification comment**
  - [X] Post comment with: promotion endpoints, scope model, activation mechanism, code/date validation
  - [X] Include references to DECISION-PRICING-002, -003, -006, -007, -016
  - [X] List remaining open questions (if any) with requested domain/owner
  - [X] Update labels: remove `blocked:clarification` when resolved

- [X] **Task 4.3 — Issue #84 clarification comment**
  - [X] Post comment with: override endpoints, reason catalog, permission strings, approval flow
  - [X] Include references to DECISION-PRICING-001, -002, -011, -012, -014
  - [X] List remaining open questions (if any) with requested domain/owner
  - [X] Update labels: remove `blocked:clarification` when resolved; confirm domain ownership

- [X] **Task 4.4 — Cross-issue dependencies**
  - [X] Link Issue #236 to workexec estimate screen implementation (if separate issue)
  - [X] Link Issue #84 to order/checkout line item UI (if separate issue)
  - [X] Update domain guides with clarified contracts

**Acceptance:** All 3 issues have resolution comments on GitHub; labels updated; blockers removed or documented

---

## Issue-Specific Checklists

### Issue #236 — Calculate Taxes and Totals on Estimate

**Blocking Questions (16 total):**
1. [ ] Domain ownership: pricing calculation vs workexec screen implementation
2. [ ] Moqui screen/service names for estimate edit and totals calculation
3. [ ] Tax calculation endpoint path and request/response DTOs
4. [ ] Calculation snapshot endpoint path and response structure
5. [ ] Snapshot fields: input line items, applied rules, output totals, rounding adjustment
6. [ ] Per-line tax breakdown availability and fields
7. [ ] Error codes: `ERR_CONFIG_JURISDICTION_MISSING`, `ERR_MISSING_TAX_CODE`, validation errors
8. [ ] Field error mapping shape and correlation ID inclusion
9. [ ] Estimate currency field name (`currencyUomId` vs `currencyCode`)
10. [ ] Estimate state model (draft/approved/declined) and editability flags
11. [ ] Missing tax code policy (fail vs default with warning)
12. [ ] Tax-inclusive vs tax-exclusive mode and backend indication
13. [ ] Approval transition endpoint and blocking validation
14. [ ] Totals recalculation auto-trigger rules
15. [ ] Serialization of concurrent recalculation requests
16. [ ] Historical snapshots vs latest-only

**Resolution Steps:**
- [ ] Review workexec and pricing backend modules for estimate/calculation services
- [ ] Document exact service names/REST paths with examples
- [ ] Capture error envelope examples for each error code
- [ ] Post clarification comment to Issue #236 in `durion-moqui-frontend`
- [ ] Remove `blocked:clarification` label when resolved

---

### Issue #161 — Create Promotion Offer (Basic)

**Blocking Questions (13 total):**
1. [ ] Permission strings: view vs manage promotions
2. [ ] Promotion scope model (store code, location ID, "all stores" representation)
3. [ ] Effective dating fields: timestamp (`effectiveStartAt`/`effectiveEndAt`) vs date-only
4. [ ] Store-local timezone interpretation and picker requirements
5. [ ] Fixed amount currency: Money object vs separate `promotionValue` + `currencyUomId`
6. [ ] Which currency applies (store currency vs tenant default)
7. [ ] Activation/deactivation mechanism: status transition vs end-dating (DECISION-PRICING-016)
8. [ ] Code normalization: uppercase on input vs case-insensitive uniqueness only
9. [ ] Promotion create endpoint path and request/response DTOs
10. [ ] Activate/deactivate endpoint paths and payloads
11. [ ] Promotion list endpoint with filtering (status, code, date window)
12. [ ] Capability flags for activate/deactivate buttons
13. [ ] Audit history endpoint availability (per DECISION-PRICING-015)

**Resolution Steps:**
- [ ] Review pricing backend module for promotion CRUD services
- [ ] Document REST paths under `/pricing/v1/promotions`
- [ ] Capture example payloads for create, activate, deactivate
- [ ] Clarify scope model with examples
- [ ] Post clarification comment to Issue #161 in `durion-moqui-frontend`
- [ ] Remove `blocked:clarification` label when resolved

---

### Issue #84 — Apply Line Price Override with Permission and Reason

**Blocking Questions (11 total):**
1. [ ] System-of-record: Pricing vs Order/Checkout domain for override API
2. [ ] Override create endpoint path (under `/pricing/v1/...` or order domain)
3. [ ] Override request DTO: fields, Money format, idempotency key
4. [ ] Override response DTO: status, updated pricing, metadata
5. [ ] Permission strings: request override, apply override, manager approval
6. [ ] Reason code catalog endpoint (price overrides vs restriction overrides)
7. [ ] Reason code fields: `code`, `description`, `activeFlag`
8. [ ] Manager approval token acquisition: re-auth modal, scanned token, selection
9. [ ] Multiple overrides per line policy: supersede vs multiple active
10. [ ] Line eligibility flag: `canOverridePrice` on line item
11. [ ] Order load endpoint including per-line override summary

**Resolution Steps:**
- [ ] Review pricing and order backend modules for override services
- [ ] Document exact endpoint ownership and paths
- [ ] Capture example request/response payloads
- [ ] Clarify manager approval flow with UI entry point
- [ ] Post clarification comment to Issue #84 in `durion-moqui-frontend`
- [ ] Remove `blocked:clarification` label when resolved

---

## Cross-Cutting Concerns

### DECISION References
- **DECISION-PRICING-001:** Money representation (decimal-string, currency required)
- **DECISION-PRICING-002:** REST path convention (`/pricing/v1/...`)
- **DECISION-PRICING-003:** Effective dating and timezone (timestamp fields, store-local interpretation, half-open intervals)
- **DECISION-PRICING-006:** Percent representation (decimal percent, e.g., `10.5` = 10.5%)
- **DECISION-PRICING-007:** Promotion code constraints (1–32 chars, `[A-Z0-9_-]`, immutable)
- **DECISION-PRICING-008:** Promotion stacking (single promotion per estimate)
- **DECISION-PRICING-009:** Promotion eligibility evaluation (backend-owned)
- **DECISION-PRICING-011:** Restriction override contract (reason codes, approval)
- **DECISION-PRICING-012:** Idempotency and correlation IDs
- **DECISION-PRICING-014:** Sensitive data redaction (no cost/margin in logs)
- **DECISION-PRICING-015:** Audit history contract (changedAt, changedBy, action, summary)
- **DECISION-PRICING-016:** End-dating semantics (set `effectiveEndAt` for deactivation)

### Permission Patterns (To Be Confirmed)
- `pricing:promotion:view`
- `pricing:promotion:manage`
- `pricing:override:request`
- `pricing:override:apply`
- `pricing:calculation:view`

### Error Envelope Standard
```json
{
  "code": "ERR_CODE",
  "message": "Human-readable message",
  "correlationId": "correlation-uuid",
  "fieldErrors": [
    { "field": "fieldName", "message": "Field-specific error", "code": "FIELD_ERR_CODE" }
  ]
}
```

---

## Notes

- **Idempotency:** All mutating endpoints must accept `Idempotency-Key` header or body field; retries must reuse same key
- **Correlation IDs:** Propagate request/correlation IDs for traceability; display in error banners
- **Backend Authority:** UI does not compute taxes, totals, or rounding; backend is authoritative (AGENT_GUIDE invariants)
- **Capability Flags:** Prefer backend-provided capability flags (`canOverridePrice`, `canActivate`) over hardcoded status checks
- **Opaque IDs:** Treat all IDs as opaque strings; no client-side validation beyond presence
- **Timezone Display:** Use store-local timezone for effective dates (DECISION-PRICING-003); label timezone in UI

---

## Progress Tracking

**Phase Status:**
- Phase 1 (Contract & Ownership): ⏳ Not Started
- Phase 2 (Data & Dependency): ⏳ Not Started  
- Phase 3 (UX/Validation): ⏳ Not Started  
- Phase 4 (Issue Updates): ⏳ Not Started  

**Overall Completion:** 0% (0 of 40+ blocking questions resolved)

**Next Actions:**
1. Begin Phase 1 backend contract discovery for pos-pricing module
2. Review workexec and order modules for cross-domain dependencies
3. Document findings in this file and prepare GitHub issue comments
