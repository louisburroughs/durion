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
- [ ] **Task 1.1 — Domain ownership verification**
  - [ ] Confirm Issue #236 should be `domain:pricing` vs `domain:workexec` (calculation is pricing, screen is workexec)
  - [ ] Confirm Issue #84 endpoint ownership (Pricing vs Order/Checkout domain)
  - [ ] Document cross-domain coordination requirements
  
- [ ] **Task 1.2 — REST endpoint/service mapping**
  - [ ] Issue #236: Identify tax/totals calculation service (Moqui service or REST path)
  - [ ] Issue #236: Identify calculation snapshot retrieval service
  - [ ] Issue #161: Identify promotion CRUD endpoints (`POST /pricing/v1/promotions`, `GET /pricing/v1/promotions/{id}`)
  - [ ] Issue #161: Identify activate/deactivate endpoints
  - [ ] Issue #84: Identify line price override create/apply endpoints
  - [ ] Issue #84: Identify override reason code catalog endpoint
  - [ ] Document all paths following DECISION-PRICING-002 (`/pricing/v1/...` convention)

- [ ] **Task 1.3 — Error envelope and correlation patterns**
  - [ ] Confirm standard error shape: `{ code, message, correlationId, fieldErrors? }`
  - [ ] Issue #236: Document tax configuration error codes (`ERR_CONFIG_JURISDICTION_MISSING`, `ERR_MISSING_TAX_CODE`)
  - [ ] Issue #161: Document promotion conflict codes (duplicate code)
  - [ ] Issue #84: Document override validation and permission error codes
  - [ ] Verify correlation ID propagation (header name, request/response)

**Acceptance:** All 3 issues have documented authoritative endpoints/services with error codes

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, and cross-domain dependencies

**Tasks:**
- [ ] **Task 2.1 — Money representation (DECISION-PRICING-001)**
  - [ ] Issue #236: Confirm estimate/line item currency field names (`currencyUomId` vs `currencyCode`)
  - [ ] Issue #161: Confirm fixed-amount promotion requires Money object `{ amount, currencyUomId }`
  - [ ] Issue #84: Confirm override requested price payload (`requestedUnitPrice.amount`, `.currencyUomId`)
  - [ ] Document decimal-string precision rules and rounding authority (backend)

- [ ] **Task 2.2 — Effective dating and timezone handling (DECISION-PRICING-003)**
  - [ ] Issue #236: Confirm `totalsCalculatedAt` timestamp format and timezone
  - [ ] Issue #161: Confirm promotion uses `effectiveStartAt`/`effectiveEndAt` (timestamp) vs date-only fields
  - [ ] Issue #161: Confirm store-local timezone interpretation and UI picker requirements
  - [ ] Document half-open interval semantics `[start, end)` for promotions

- [ ] **Task 2.3 — Identifier types and formats (DECISION-PRICING-003)**
  - [ ] Issue #236: Confirm `estimateId`, `calculationSnapshotId` types (UUID vs opaque string)
  - [ ] Issue #161: Confirm `promotionOfferId`, `promotionCode` types and immutability
  - [ ] Issue #84: Confirm `orderId`, `lineItemId`, `overrideId`, `managerApprovalId` types
  - [ ] Treat all IDs as opaque; no client-side validation beyond presence

- [ ] **Task 2.4 — Calculation snapshot structure**
  - [ ] Issue #236: Document snapshot entity fields (input line items, applied rules, output totals)
  - [ ] Issue #236: Confirm per-line tax breakdown availability (`taxAmount`, `taxCode`, `isTaxable`)
  - [ ] Issue #236: Confirm rounding adjustment visibility and non-zero display rules
  - [ ] Issue #236: Confirm snapshot history vs latest-only availability

- [ ] **Task 2.5 — Promotion scope and activation**
  - [ ] Issue #161: Document scope model (store code, location ID, "all stores" representation)
  - [ ] Issue #161: Confirm activation/deactivation mechanism (status transition vs end-dating per DECISION-PRICING-016)
  - [ ] Issue #161: Confirm code normalization (uppercase on input vs case-insensitive uniqueness)
  - [ ] Issue #161: Document usage limit semantics (null = unlimited)

- [ ] **Task 2.6 — Override reason catalog and approval flow**
  - [ ] Issue #84: Confirm price override reason endpoint (separate from restriction overrides in DECISION-PRICING-011)
  - [ ] Issue #84: Document reason code fields (`code`, `description`, `activeFlag`)
  - [ ] Issue #84: Confirm manager approval token acquisition (re-auth, scanned token, selection)
  - [ ] Issue #84: Document multiple overrides per line policy (supersede vs multiple active)

**Acceptance:** All entity schemas, ID types, and cross-domain contracts documented with examples

---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm client-side validation rules, capability flags, and UI gating

**Tasks:**
- [ ] **Task 3.1 — Estimate totals calculation workflow**
  - [ ] Issue #236: Confirm auto-trigger rules (after line item create/update/delete)
  - [ ] Issue #236: Confirm blocking rules (prevent approval when tax config missing)
  - [ ] Issue #236: Confirm totals panel state machine (`idle`, `recalculating`, `updated`, `blocked_config`, `error`)
  - [ ] Issue #236: Confirm serialization of concurrent recalculation requests

- [ ] **Task 3.2 — Promotion validation and constraints**
  - [ ] Issue #161: Confirm client-side validation (code length 1–32, charset `[A-Z0-9_-]`, date window)
  - [ ] Issue #161: Confirm `promotionValue > 0` enforcement (non-zero)
  - [ ] Issue #161: Confirm percent representation (decimal percent per DECISION-PRICING-006)
  - [ ] Issue #161: Confirm capability flags for activate/deactivate buttons

- [ ] **Task 3.3 — Override permission and eligibility**
  - [ ] Issue #84: Confirm permission strings (`pricing:override:request`, `pricing:override:apply`, etc.)
  - [ ] Issue #84: Confirm line eligibility flag (`canOverridePrice` on line item)
  - [ ] Issue #84: Confirm client-side validation (required reason, valid money format, no negatives)
  - [ ] Issue #84: Confirm idempotency key generation and retry logic

- [ ] **Task 3.4 — Error handling and user messaging**
  - [ ] All issues: Map HTTP codes to UI (400/401/403/404/409/422/5xx)
  - [ ] Issue #236: Confirm field error mapping for tax/line validation
  - [ ] Issue #161: Confirm duplicate code field error placement
  - [ ] Issue #84: Confirm concurrency conflict (409) reload prompt

**Acceptance:** All validation rules, capability flags, and error mappings documented

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post resolution comments on GitHub issues and update labels/status

**Tasks:**
- [ ] **Task 4.1 — Issue #236 clarification comment**
  - [ ] Post comment with: tax calculation contract, snapshot structure, error codes, state machine
  - [ ] Include references to DECISION-PRICING-001, -002, -003, -006, -011, -012
  - [ ] List remaining open questions (if any) with requested domain/owner
  - [ ] Update labels: remove `blocked:clarification` when resolved; confirm `domain:pricing`
  
- [ ] **Task 4.2 — Issue #161 clarification comment**
  - [ ] Post comment with: promotion endpoints, scope model, activation mechanism, code/date validation
  - [ ] Include references to DECISION-PRICING-002, -003, -006, -007, -016
  - [ ] List remaining open questions (if any) with requested domain/owner
  - [ ] Update labels: remove `blocked:clarification` when resolved

- [ ] **Task 4.3 — Issue #84 clarification comment**
  - [ ] Post comment with: override endpoints, reason catalog, permission strings, approval flow
  - [ ] Include references to DECISION-PRICING-001, -002, -011, -012, -014
  - [ ] List remaining open questions (if any) with requested domain/owner
  - [ ] Update labels: remove `blocked:clarification` when resolved; confirm domain ownership

- [ ] **Task 4.4 — Cross-issue dependencies**
  - [ ] Link Issue #236 to workexec estimate screen implementation (if separate issue)
  - [ ] Link Issue #84 to order/checkout line item UI (if separate issue)
  - [ ] Update domain guides with clarified contracts

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
