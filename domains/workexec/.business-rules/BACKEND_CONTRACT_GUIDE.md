# WorkExec Backend Contract Guide

**Version:** 0.1 (Phase 1 draft)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-01-24

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for WorkExec REST APIs. It reuses patterns from accounting and CRM backend contract guides and aligns with DECISION-INVENTORY (auth fail-closed, opaque IDs, standard error envelope, idempotency, capability signaling).

**Primary Goal (Phase 1):** Document the authoritative contracts for the endpoints referenced in [workexec-questions.md](../workexec-questions.md) so blocking questions can be resolved in Phase 2.

---

## Conventions

- JSON field naming: `camelCase`
- Enum values: `UPPER_CASE_WITH_UNDERSCORES`
- IDs: Opaque strings (do not validate client-side)
- Timestamps: ISO 8601 UTC (`yyyy-MM-dd'T'HH:mm:ss'Z'`)
- Money/quantities: decimal with two-to-four fractional digits; backend is source of truth
- Error envelope (DECISION-INVENTORY-011): `{ code, message, correlationId, fieldErrors[], existingResourceId? }`
- Idempotency (DECISION-INVENTORY-012): Mutations accept `Idempotency-Key` header and return same resource on retry
- Capability signaling (DECISION-INVENTORY-013): Surface boolean flags for UI gating; backend still enforces permissions

---

## Error Codes (Authoritative Set)

| Code | Meaning | Typical HTTP |
|------|---------|--------------|
| INVALID_STATE | Operation not allowed for current status | 409 |
| NOT_FOUND | Entity not found | 404 |
| FORBIDDEN | Permission denied | 403 |
| VALIDATION_ERROR | Field-level validation failed | 400 |
| CONFLICT | Version/ETag mismatch | 409 |
| ALREADY_PROMOTED | Estimate already promoted; may return `existingResourceId` | 409 |
| MISSING_TAX_CODE | Item missing tax configuration | 400/409 |
| TAX_CODE_NOT_CONFIGURED | Tax code not configured for location | 409 |
| TOTALS_MISMATCH | Calculated totals mismatch provided totals | 409 |
| APPROVAL_NOT_REQUIRED | Entity not awaiting approval | 400 |
| MISSING_LEGAL_TERMS | Legal terms required but unavailable | 409 |
| APPROVAL_EXPIRED | Approval window expired | 409 |

Backend MUST return stable codes above; extend with new codes as needed, keeping the envelope shape.

---

## Endpoint Inventory (Phase 2 Confirmed from pos-work-order Backend)

The paths follow the WorkExec namespace `GET/POST /v1/workorders/...` (actual backend routing). Mutations accept `Idempotency-Key` header. All request/response DTOs use standard error envelope on failure.

### Estimates (Confirmed APIs)

1. **Get All Estimates** — `GET /v1/workorders/estimates`
   - Response: `[ EstimateDTO ]`
   - No pagination in source; returns all estimates

2. **Get Estimate by ID** — `GET /v1/workorders/estimates/{estimateId}`
   - Path param: `estimateId` (Long, required)
   - Response: `EstimateDTO` (200) | 404 if not found
   - EstimateDTO: `{ id, estimateNumber, status, locationId, vehicleId, customerId, createdByUserId, createdAt, updatedAt, approvedAt, declinedAt, expiresAt, subtotal, taxAmount, total, version }`

3. **Get Estimates by Customer** — `GET /v1/workorders/estimates/customer/{customerId}`
   - Path param: `customerId` (Long, required)
   - Response: `[ EstimateDTO ]`

4. **Get Estimates by Location/Shop** — `GET /v1/workorders/estimates/shop/{locationId}` | `/location/{locationId}`
   - Path param: `locationId` (Long, required)
   - Response: `[ EstimateDTO ]`
   - **Note:** Both `/shop/{locationId}` and `/location/{locationId}` endpoints exist (deprecated `/shop/*`)

5. **Create Estimate** — `POST /v1/workorders/estimates`
   - Request: `CreateEstimateRequest { customerId (Long), vehicleId (Long) }`
   - Response: `CreateEstimateResponse { id, estimateNumber, status: DRAFT, locationId, createdAt }`
   - HTTP 200 (success), 400 (validation error), 500 (server error)
   - System generates unique `estimateNumber` (e.g., EST-2024-1001)
   - Requires: `X-User-Id` header (defaults to 1 if missing)

6. **Decline Estimate** — `POST /v1/workorders/estimates/{estimateId}/decline`
   - Path param: `estimateId` (Long)
   - Query param: `reason` (String, optional)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DRAFT → DECLINED

7. **Reopen Estimate** — `POST /v1/workorders/estimates/{estimateId}/reopen`
   - Path param: `estimateId` (Long)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DECLINED → DRAFT (within expiry window)
   - Constraint: Cannot reopen if expired

8. **Approve Estimate with Signature** — `POST /v1/workorders/estimates/{estimateId}/approval`
   - Path param: `estimateId` (Long)
   - Request: `ApproveEstimateRequest { customerId (Long), signatureData (String, base64 PNG), signatureMimeType (String), signerName (String, optional), notes (String, optional) }`
   - Response: `EstimateDTO { status: APPROVED, approvedAt, approvedBy, signatureData, signerName }` (200) | 400/404
   - Validation: customerId must match estimate
   - State transition: DRAFT → APPROVED

9. **Delete Estimate** — `DELETE /v1/workorders/estimates/{estimateId}`
   - Path param: `estimateId` (Long)
   - Response: 204 No Content (success) | 404 (not found)

### Estimate Status Enum (Confirmed)

From `EstimateStatus` enum in pos-work-order:
- `DRAFT` — Initial state, editable
- `APPROVED` — Customer approved or system auto-approved
- `DECLINED` — Customer declined
- `EXPIRED` — Approval window closed (time-based expiration)
- `PENDING_APPROVAL` — Awaiting customer approval

---

## Work Orders (Confirmed from pos-work-order)

1. **Load Work Order** — `GET /v1/workorders/{workOrderId}`
   - Path param: `workOrderId` (Long)
   - Response: `WorkorderDTO { id, shopId, vehicleId, customerId, approvalId, estimateId, status, services[], approvedAt, approvedBy, completedAt, completedBy }`
   - HTTP 200 (success) | 404 (not found)

2. **Get All Work Orders** — `GET /v1/workorders`
   - Response: `[ WorkorderDTO ]` (all work orders, no pagination)

### Work Order Status Enum (Confirmed)

From `WorkorderStatus` enum in pos-work-order:
- `DRAFT` — Initial state
- `APPROVED` — Approved (can transition to ASSIGNED)
- `ASSIGNED` — Assigned to technician (can transition to WORK_IN_PROGRESS)
- `WORK_IN_PROGRESS` — Being worked on
- `AWAITING_PARTS` — Waiting for parts availability
- `AWAITING_APPROVAL` — Awaiting approval for additional work/changes
- `READY_FOR_PICKUP` — Work complete, ready for customer pickup
- `COMPLETED` — Completed and delivered
- `CANCELLED` — Cancelled

### Work Order Services (Items)

**WorkorderService Entity Fields (Confirmed):**
- `id` (Long) — Primary key
- `workOrder` (FK to Workorder)
- `serviceEntityId` (Long) — Reference to ServiceEntity in pos-catalog
- `technicianId` (Long) — Reference to Technician
- `declined` (Boolean, default=false) — Flag: declined by customer during estimate approval
- `status` (Enum) — WorkorderItemStatus (see below)
- `changeRequestId` (Long, optional) — Reference to change request that added this item
- `isEmergencySafety` (Boolean, default=false)
- `photoEvidenceUrl` (String, optional)
- `emergencyNotes` (String, optional)
- `photoNotPossible` (Boolean, default=false)
- `customerDenialAcknowledged` (Boolean, optional)

**WorkorderItemStatus Enum (Confirmed):**
- `PENDING_APPROVAL` — Awaiting approval
- `OPEN` — Available to execute
- `READY_TO_EXECUTE` — Ready for technician
- `IN_PROGRESS` — Currently being worked on
- `COMPLETED` — Work complete
- `CANCELLED` — Cancelled

### Work Order Parts (Inventory Integration)

**WorkorderPart Entity Fields (Confirmed):**
- `id` (Long) — Primary key
- `workorderService` (FK to WorkorderService)
- `productEntityId` (Long) — Reference to pos-catalog Product
- `quantity` (BigDecimal) — Quantity used
- `unitPrice` (BigDecimal) — Price per unit
- `actualQuantityUsed` (BigDecimal, optional) — Actual qty consumed from inventory
- `changeRequestId` (Long, optional) — Reference to change request that added this part
- `status` (Enum) — PartStatus (see below)

**PartStatus Enum (Confirmed):**
- `PENDING_APPROVAL` — Awaiting approval
- `OPEN` — Available to consume
- `READY_TO_EXECUTE` — Ready for use
- `IN_PROGRESS` — Being used
- `COMPLETED` — Used/installed
- `CANCELLED` — Cancelled

**Business Methods (Confirmed):**
- `canExecute()` — Returns true if status != PENDING_APPROVAL OR (isEmergencySafety && customerDenialAcknowledged != null)
- `canConsumeInventory()` — Returns true if status != PENDING_APPROVAL

---

## Approval & Customer Signature

**ApproveEstimateRequest (Confirmed from EstimateController):**
```
{
  "customerId": Long (required) — Must match estimate customer
  "signatureData": String (base64 PNG image)
  "signatureMimeType": String (e.g., "image/png")
  "signerName": String (optional)
  "notes": String (optional)
}
```

**Response after Approval:**
```
EstimateDTO {
  "id": Long
  "estimateNumber": String (e.g., "EST-2024-1001")
  "status": "APPROVED"
  "locationId": Long
  "vehicleId": Long
  "customerId": Long
  "approvedAt": ISO 8601 timestamp
  "approvedBy": Long (user ID)
  "signatureData": String (base64)
  "signatureMimeType": String
  "signerName": String
  "approvalNotes": String (from request.notes)
  "subtotal": BigDecimal
  "taxAmount": BigDecimal
  "total": BigDecimal
  "version": Integer
}
```

---

## DTO Schemas (Confirmed from pos-work-order)

**WorkOrderItemDTO**
- `workOrderItemId`, `originEstimateItemId?`
- `itemType`, `description`, `quantity`, `unitPrice|unitRate|amount`, `lineTotal`, `taxCode`, `taxAmount`
- `status` (PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED) — confirmed from backend
- `requiresReview?`, `notes?`

**PromotionAuditDTO**
- `auditEventId`, `eventTimestamp`, `promotingUserId`
- `estimateId`, `workOrderId`, `estimateSnapshotId?`, `approvalId?`
- `promotionSummary { laborItemCount, partItemCount, feeItemCount, subtotal, taxTotal, grandTotal, currencyUomId }`
- `correlationId?`

**ApprovalRequestDTO**
- `approvalRequestId`, `resolutionStatus` (APPROVED, REJECTED, APPROVED_WITH_EXCEPTION) — confirmed from ApprovalRecord.ResolutionStatus enum
- `approvalMethod`, `requiresSignature`, `expiresAt?`, `createdAt`, `createdBy`

**Signature Payload (submission)**
- `customerSignatureData { signatureImage (base64 PNG), signatureStrokes? [ { x, y, t } ] }`
- `approvalPayload { documentDigest?, customerIdentifier? }`
- `approvalMethod` (CLICK_CONFIRM, SIGNATURE, ELECTRONIC_SIGNATURE, VERBAL_CONFIRMATION) — confirmed from ApprovalConfiguration.ApprovalMethod enum
- `version?` (Integer field in Estimate entity, not JPA @Version)

---

## Phase 2 Validation Results (January 27, 2026)

### Confirmed from Backend Source (durion-positivity-backend/pos-work-order)

**OpenAPI Specification Generated:** Successfully built pos-work-order module with Java 21 and extracted OpenAPI definitions.

**Confirmed Enums:**
- **EstimateStatus:** DRAFT, APPROVED, DECLINED, EXPIRED, PENDING_APPROVAL
- **WorkorderStatus:** DRAFT, APPROVED, ASSIGNED, WORK_IN_PROGRESS, AWAITING_PARTS, AWAITING_APPROVAL, READY_FOR_PICKUP, COMPLETED, CANCELLED
- **WorkorderItemStatus:** PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED
- **PartStatus:** PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED

**Confirmed Endpoints:**
- Estimate CRUD: GET /v1/workorders/estimates, POST /create, GET /{id}, POST /{id}/approval (signature), POST /{id}/decline, POST /{id}/reopen, DELETE /{id}
- Estimate Queries: GET /customer/{id}, GET /shop/{id}, GET /location/{id}
- WorkOrder Retrieval: GET /v1/workorders/{id}, GET /all
- No explicit item-level mutation endpoints in EstimateController; items managed via parent resource mutations

**Confirmed Signatures:**
- Estimate approval: Requires customerId, signatureData (base64 PNG), signatureMimeType, signerName (optional), notes (optional)
- Response includes: approvedAt, approvedBy, signatureData, signatureMimeType, signerName, approvalNotes

**Concurrency Model:**
- Estimate entity has `version` Integer field (business-level versioning, not JPA @Version)
- Version increments on financial changes (subtotal, taxAmount, total modifications)
- EstimateSequence uses JPA @Version for optimistic locking (separate concern)

**Architectural Observations:**
- Workorder references Estimate via estimateId (one-to-many relationship possible)
- WorkorderService (items) contains reference to ServiceEntity (pos-catalog external)
- WorkorderPart (parts) contains reference to Product (pos-catalog external)
- Path pattern: `/v1/workorders/*` (not `/api/v1/workexec/*` as initially assumed)

### Not Found in pos-work-order Source

- **Item-level add/remove endpoints:** No POST /items:add-part, POST /items:add-labor endpoints discovered
  - Possible: Items added via intermediate service or through change request workflow
- **Tax configuration:** No TaxCode entity or tax calculation logic in pos-work-order
  - Likely: Tax handled by accounting/order modules (pos-order, pos-accounting)
- **Legal terms/snapshot generation:** No summary snapshot or legal terms endpoints
  - Likely: Handled by separate document generation service
- **Promotion endpoints:** No explicit promote-to-workorder endpoint
  - Likely: WorkOrder creation may be independent or handled by workflow service
- **Approval configuration:** No approval method selection or requirement querying
  - Likely: Centralized in pos-customer or pos-approval module

### API Routing Clarification

**Actual:** `/v1/workorders/*` (in pos-work-order)  
**Previously assumed:** `/api/v1/workexec/*`

Frontend should adjust base paths to match actual routing. Controller declares `@RequestMapping("/v1/workorders")`.

---

## References

- [Accounting Backend Contract Guide](/durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [CRM Backend Contract Guide](/durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [WorkExec Business Rules](/durion/domains/workexec/.business-rules/)
- [DECISION-INVENTORY Governance](/durion/docs/governance/)
- pos-work-order OpenAPI Specification (generated 2026-01-27)
- Billing finalization API (#216)
- Timekeeping implementation (clock in/out, timers, time entries) (#149, #146, #145, #131)
- Scheduling implementation (create appointment, reschedule, assign, override conflict) (#138, #137, #134, #133, #129, #127)
- Sales order creation from workorder (#85)
- Estimate filtering enhancements (#79)
- Role-based visibility enforcement (#219)
- PO# requirement enforcement (#162)

### Cross-Domain Integrations Identified
1. **WorkExec → Inventory:** Parts usage events, stock adjustments
2. **WorkExec → People:** Work session events, timekeeping entries
3. **WorkExec → ShopMgmt:** Workorder status events, appointment scheduling
4. **WorkExec → Order:** Sales order creation from billable workorder
5. **WorkExec → CRM:** Customer reference display (read-only)
6. **WorkExec → Accounting:** Billable snapshot export, invoicing integration

### Next Steps (Phase 4.3)
1. Document acceptance criteria for 18 remaining issues using confirmed/pending API contracts
2. Mark validation status: CONFIRMED (implementation found) vs PENDING (stub/missing) vs BLOCKED (requires cross-domain coordination)
3. Link each issue acceptance criteria to relevant BACKEND_CONTRACT_GUIDE sections
4. Update CROSS_DOMAIN_INTEGRATION_CONTRACTS.md with detailed event schemas and API contracts

---

**Phase 4.2 Completion Date:** 2026-01-25  
**Status:** ✅ Backend discovery complete; proceed to Phase 4.3 (Issue Analysis & Acceptance Criteria)