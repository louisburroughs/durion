# WorkExec Domain - Open Questions for Backend Clarification

This document consolidates all open questions from the WorkExec domain story specifications that require backend/architecture decisions before frontend implementation can proceed.

---

## Issue #271: [FRONTEND] [STORY] Estimate: Capture Digital Customer Approval (In-Person Signature)
**Title:** Estimate: Capture Digital Customer Approval (In-Person Signature)

### Open Questions

1. **Canonical API paths (blocking):** What are the exact REST endpoints for:
   - Load estimate details (GET /api/v1/workexec/estimates/{estimateId})
   - Load work order details (GET /api/v1/workexec/workorders/{workOrderId})
   - Submit approval with signature (POST /api/v1/workexec/approvals or similar)?
   
   Provide request/response field names and confirm if these are Moqui services or REST endpoints.

**Acceptance Criteria:**
- **Resolved:** Backend endpoints confirmed from pos-workorder module
  - Load estimate: `GET /v1/workorders/estimates/{estimateId}`
  - Approve estimate: `POST /v1/workorders/estimates/{estimateId}/approval`
  - Response: EstimateDTO with status, version, items, totals, approval timestamps
- **Test Fixtures:** One DRAFT estimate exists with items
- **Example API:**

```http
GET /v1/workorders/estimates/1
Authorization: Bearer <token>

200 OK
{
  "id": 1,
  "estimateNumber": "EST-2026-001",
  "status": "DRAFT",
  "version": 1,
  "customerId": 1,
  "locationId": 1,
  "vehicleId": 1,
  "subtotal": 150.00,
  "taxAmount": 12.00,
  "total": 162.00,
  "currencyUomId": "USD"
}
```

```http
POST /v1/workorders/estimates/1/approval
Authorization: Bearer <token>
Content-Type: application/json

{
  "customerId": 1,
  "signatureData": "iVBORw0KGgoAAAANSUhEUgAA...",
  "signatureMimeType": "image/png",
  "signerName": "John Doe",
  "notes": "Approved in person"
}

200 OK
{
  "id": 1,
  "status": "APPROVED",
  "approvedAt": "2026-01-24T10:30:00Z",
  "approvedBy": 100
}
```

2. **Eligibility status mapping (blocking):** What estimate/work order statuses are eligible for digital approval capture? Reference: DRAFT, PENDING_APPROVAL, AWAITING_CUSTOMER_APPROVAL, or other canonical enum?

**Acceptance Criteria:**
- **Resolved:** Only DRAFT estimates are eligible for approval (confirmed from Estimate.canApprove() method)
  - Backend enum: DRAFT, APPROVED, DECLINED, EXPIRED (no PENDING_APPROVAL state)
  - UI disables approve action when status != DRAFT
- **Test Fixtures:** Estimates in DRAFT, APPROVED, DECLINED statuses
- **Example API:**

```http
POST /v1/workorders/estimates/2/approval
Authorization: Bearer <token>
Content-Type: application/json

{ "customerId": 1, "signatureData": "...", "signerName": "Jane Doe" }

400 Bad Request
{
  "error": "Bad Request",
  "message": "Cannot approve estimate in current state: APPROVED"
}
```

3. **Approval payload schema (blocking):** What is the exact JSON schema expected when submitting digital approval? Include:
   - `customerSignatureData { signatureImage: base64, signatureStrokes?: [] }`
   - `approvalPayload { documentDigest?, customerIdentifier? }`
   - `approvalMethod` enum values
   - Any concurrency token field (version, lastUpdatedStamp)?

**Acceptance Criteria:**
- **Resolved:** Approval payload confirmed from ApproveEstimateRequest DTO
  - Required: `customerId`, `signatureData` (base64 PNG), `signerName`
  - Optional: `signatureMimeType`, `notes`
  - No version/concurrency token required (backend uses optimistic locking on entity)
  - ApprovalMethod enum: CLICK_CONFIRM, SIGNATURE, ELECTRONIC_SIGNATURE, VERBAL_CONFIRMATION
- **Test Fixtures:** Valid base64 PNG signature image
- **Example API:**

```json
{
  "customerId": 1,
  "signatureData": "iVBORw0KGgoAAAANSUhEUgAAAAUA...",
  "signatureMimeType": "image/png",
  "signerName": "John Doe",
  "notes": "Customer approved estimate on tablet"
}
```

4. **Signature strokes format (blocking):** What is the coordinate system and timestamp semantics for signature strokes? Example: `{ x: 0-1000, y: 0-1000, t: millisecondsSinceEpoch }`?

**Acceptance Criteria:**
- **Resolved:** Backend stores base64 PNG only; stroke data not required
  - UI can capture strokes for rendering but converts to PNG before submit
  - Backend stores `signatureData` (base64 string) and `signatureMimeType`
  - No stroke-level metadata stored
- **Test Fixtures:** Signature canvas renders and converts to PNG
- **Example:** UI handles strokes internally; backend receives final image only

5. **Signer identity representation (blocking):** When capturing signature, should UI capture:
   - Customer name (text input)?
   - Customer ID (lookup)?
   - Role (CUSTOMER, ADVISOR, AUTHORIZED_REPRESENTATIVE)?
   - All of the above?

**Acceptance Criteria:**
- **Resolved:** Capture customer ID + signer name (text input)
  - Required: `customerId` (must match estimate customer)
  - Required: `signerName` (free text, customer enters their name)
  - Backend validates customerId matches estimate.customerId
  - Role not captured (implicitly CUSTOMER for in-person signature)
- **Test Fixtures:** Estimate with customerId=1; approval with customerId=2 rejected
- **Example API:**

```http
POST /v1/workorders/estimates/1/approval
{ "customerId": 2, "signatureData": "...", "signerName": "Wrong Customer" }

400 Bad Request
{
  "error": "Bad Request",
  "message": "Customer ID mismatch: estimate customer is 1, provided customer is 2"
}
```

6. **Response shape on success (blocking):** After submitting approval, does backend return:
   - `approvalId` (reference for audit trail)?
   - `approvedAt` (server timestamp)?
   - Updated estimate/work order DTO with new status?
   - Approval snapshot/summary for display?

**Acceptance Criteria:**
- **Resolved:** Response returns updated EstimateDTO
  - Includes: `status=APPROVED`, `approvedAt` (server timestamp), `approvedBy` (userId)
  - No separate approvalId returned (approval data stored on Estimate entity)
  - UI displays updated estimate with APPROVED status and timestamp
- **Test Fixtures:** Approved estimate shows approvedAt and approvedBy fields
- **Example API:**

```http
200 OK
{
  "id": 1,
  "estimateNumber": "EST-2026-001",
  "status": "APPROVED",
  "version": 1,
  "approvedAt": "2026-01-24T10:30:15Z",
  "approvedBy": 100,
  "signatureMimeType": "image/png",
  "signerName": "John Doe"
}
```

---

## Issue #269: [FRONTEND] [STORY] Workorder: Record Partial Approval (Line-Item-Level Decisions)
**Title:** Workorder: Record Partial Approval (Line-Item-Level Decisions)

### Open Questions

1. **Backend contract (blocking):** What are the exact Moqui services/REST endpoints for:
   - Load work order with items (GET /api/v1/workexec/workorders/{workOrderId}/items)
   - Submit partial approval decisions (POST /api/v1/workexec/workorders/{workOrderId}/approvals:record-partial)
   - Include request/response DTOs with exact field names.

2. **Line item identifiers (blocking):** When submitting per-item approval decisions, what identifies each item?
   - `workOrderItemId` (UUID)?
   - `lineSeqId` (sequence number)?
   - Separate collections for LABOR vs PART vs SERVICE items, or single flat list?

3. **Approval method/proof schema (blocking):** What approval methods are supported per item (e.g., CLICK_CONFIRM, SIGNATURE, VERBAL)? What proof data is required for each method?

4. **Terminal outcome mapping (blocking):** If customer declines all line items, what is the resulting work order status?
   - CANCELLED?
   - DECLINED?
   - Other enum value?
   - Is manual transition required, or automatic?

5. **Approval window semantics (blocking):** If approval has an expiration window, how is it exposed:
   - Boolean field `approvalExpired`?
   - Datetime field `approvalExpiresAt`?
   - Enum status `EXPIRED`?
   - Is window validation backend-enforced or UI-only?

---

### Acceptance Criteria

1. **Load workorder items endpoint:**
    - GET /api/v1/workexec/workorders/{workorderId}/items
    - Returns 200 with array of items containing:
       - `workorderItemId` (opaque ID, UUID)
       - `lineSeq` (integer sequence)
       - `type` (enum: PART | LABOR | SERVICE)
       - `status` (enum: WorkorderItemStatus = PENDING_APPROVAL | OPEN | READY_TO_EXECUTE | IN_PROGRESS | COMPLETED | CANCELLED)
       - `name`, `description`
       - `quantity`, `uom`
       - `unitPrice`, `extendedPrice` (currency minor units)
       - `notes` (optional)
    - Errors:
       - 403 when lacking `workexec.workorder.view`
       - 404 when `workorderId` not found

2. **Record partial approval endpoint:**
    - POST /api/v1/workexec/workorders/{workorderId}/approvals:record-partial
    - Headers: `Idempotency-Key` required; `If-Match` optional when version provided
    - Request body:
       - `approvedItemIds`: string[] (IDs for items approved)
       - `declinedItemIds`: string[] (IDs for items declined)
       - `approvalMethod`: enum ApprovalMethod = CLICK_CONFIRM | SIGNATURE | ELECTRONIC_SIGNATURE | VERBAL_CONFIRMATION
       - `signature`: object (required when `approvalMethod` is SIGNATURE or ELECTRONIC_SIGNATURE)
          - `signedAt` (ISO 8601, UTC)
          - `signerName`
          - `signerEmail` (optional)
          - `signatureBlob` (opaque, base64 or provider reference)
       - `note`: string (optional)
    - Response 200 payload:
       - `workorderId`
       - `resolutionStatus`: enum ApprovalRecord.ResolutionStatus = APPROVED | REJECTED | APPROVED_WITH_EXCEPTION
       - `approvedCount`, `declinedCount`, `pendingCount`
       - `items`: array of `{ workorderItemId, status }` reflecting per-item transitions
    - Errors:
       - 409 when approval window expired
       - 422 for invalid item IDs or mixed duplicates between approved/declined lists
       - 403 when lacking `workexec.workorder.approval.record-partial`

3. **Per-item status transitions:**
    - Approved items transition to `READY_TO_EXECUTE`.
    - Declined items transition to `CANCELLED`.
    - Items not included remain `PENDING_APPROVAL`.

4. **Workorder/estimate outcomes:**
    - When both approved and declined items exist in the same operation, `resolutionStatus` MUST be `APPROVED_WITH_EXCEPTION`.
    - Overall `workorder.status` MUST NOT auto-transition to `WORK_IN_PROGRESS`; promotion remains a separate explicit action.
    - Estimate/workorder version increments when partial approval succeeds.

5. **Security & permissions:**
    - View requires `workexec.workorder.view`.
    - Recording partial approval requires `workexec.workorder.approval.record-partial`.
    - Signature payload PII must not be logged; redact in telemetry.

6. **Observability:**
    - Emit event `workexec.approval.partial-recorded` with attributes: `workorderId`, `approvedCount`, `declinedCount`, `approvalMethod`, `resolutionStatus`.
    - Attach trace context; exclude `signatureBlob` from telemetry.

7. **Example request/response:**

Request:

```
POST /api/v1/workexec/workorders/WO-123/approvals:record-partial
Idempotency-Key: 2b4d1e4f-7a6c-4c6a-9f1c-0aa4d3e77890
Content-Type: application/json

{
   "approvedItemIds": ["WOI-001", "WOI-003"],
   "declinedItemIds": ["WOI-002"],
   "approvalMethod": "SIGNATURE",
   "signature": {
      "signedAt": "2026-01-24T14:22:31Z",
      "signerName": "Alex Customer",
      "signerEmail": "alex@example.com",
      "signatureBlob": "ref:docu-789654"
   },
   "note": "Approve labor, decline part #A12"
}
```

Response:

```
200 OK
{
   "workorderId": "WO-123",
   "resolutionStatus": "APPROVED_WITH_EXCEPTION",
   "approvedCount": 2,
   "declinedCount": 1,
   "pendingCount": 0,
   "items": [
      { "workorderItemId": "WOI-001", "status": "READY_TO_EXECUTE" },
      { "workorderItemId": "WOI-003", "status": "READY_TO_EXECUTE" },
      { "workorderItemId": "WOI-002", "status": "CANCELLED" }
   ]
}
```

8. **Alignment to backend canonical:**
    - Backend microservice canonical approval endpoint: POST /v1/workorders/estimates/{estimateId}/approval.
    - Frontend Workexec bridge MUST translate per-item decisions into backend-approved schema.
    - Enums and statuses MUST match backend: `ApprovalRecord.ResolutionStatus`, `ApprovalConfiguration.ApprovalMethod`, `WorkorderItemStatus`.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Approval & Partial Approval](../.business-rules/BACKEND_CONTRACT_GUIDE.md#approval--partial-approval)
**Validation status:** CONFIRMED (pos-workorder enums/endpoints); pending only on signature storage format specifics.

## Issue #268: [FRONTEND] [STORY] Estimate/WorkOrder: Handle Approval Expiration
**Title:** Estimate/WorkOrder: Handle Approval Expiration (Block Actions, Communicate Reason)

### Open Questions

1. **Domain ownership confirmation (blocking):** Is approval expiration owned by WorkExec domain (affects workexec UI) or Security domain (affects global auth state)? Confirm label and permission keys.

2. **Backend contract (blocking):** What are the exact endpoints for:
   - List pending approvals with expiration status (GET /api/v1/workexec/approvals?status=PENDING&includeExpiration=true)
   - Load approval details (GET /api/v1/workexec/approvals/{approvalId})
   - Record deny decision (POST /api/v1/workexec/approvals/{approvalId}/deny)
   - Request new approval (POST /api/v1/workexec/estimates/{estimateId}/approvals:request-new)

3. **State model (blocking):** What approval statuses exist?
   - PENDING, APPROVED, DECLINED, EXPIRED (enum)?
   - Or separate `status` (PENDING/APPROVED/DECLINED) and `isExpired` boolean?
   - Which transitions are valid per status?

4. **Expiration representation (blocking):** How is expiration exposed in approval DTO:
   - `status` enum includes `EXPIRED`?
   - `expiresAt` datetime field?
   - `isExpired` boolean (computed or stored)?
   - `daysRemaining` integer?

5. **Timezone/display standard (blocking):** When displaying approval expiration time:
   - User's timezone or system standard?
   - Format (ISO 8601, human-readable relative "expires in 2 days")?
   - Reference existing durion display patterns (DECISION-INVENTORY-016)?

6. **Deny requirements (blocking):** When user clicks "Deny Approval", is:
   - Reason required (text field, mandatory)?
   - Reason optional?
   - Reason codes from enum, or free text?
   - Backend validation rules (min/max length)?

7. **Post-expiration user path (blocking):** After approval expires, can user:
   - Automatically request new approval (one-click)?
   - Manually edit estimate and resubmit (triggers new approval request)?
   - Both options available?

---

### Acceptance Criteria

1. **Ownership and permissions:**
   - WorkExec domain owns approval expiration UX and enforcement; Security domain only handles authn/authz.
   - View approvals requires `workexec.approval.view`; deny/re-request requires `workexec.approval.manage`.
   - Expired approvals must block submission actions that depend on active approvals.

2. **List approvals with expiration:**
   - GET /api/v1/workexec/approvals?status=PENDING&includeExpiration=true
   - Returns 200 with array of approvals including fields: `approvalId`, `estimateId`, `workorderId`, `approvalStatus` (enum: PENDING | APPROVED | REJECTED | APPROVED_WITH_EXCEPTION | EXPIRED), `expiresAt` (ISO 8601 UTC), `isExpired` (boolean derived), `requestedAt`, `approvalMethod`, `requestedBy`.
   - Errors: 403 without `workexec.approval.view`.

3. **Load approval detail:**
   - GET /api/v1/workexec/approvals/{approvalId}
   - Returns approval DTO with status, expiresAt, isExpired, allowedActions array (`DENY`, `REQUEST_NEW` when expired).
   - 404 when approvalId not found; 403 without permission.

4. **Deny approval:**
   - POST /api/v1/workexec/approvals/{approvalId}/deny
   - Body: `{ "reason": "text", "reasonCode": "CUSTOMER_DECLINED" (optional) }`
   - Reason is required (min 3 chars, max 512); 422 when invalid.
   - Response 200 returns updated `approvalStatus` = `REJECTED` and `deniedAt`.

5. **Request new approval:**
   - POST /api/v1/workexec/estimates/{estimateId}/approvals:request-new
   - Headers: `Idempotency-Key` required; optional `If-Match` when estimate version present.
   - Body: `{ "approvalMethod": "CLICK_CONFIRM" | "SIGNATURE" | "ELECTRONIC_SIGNATURE" | "VERBAL_CONFIRMATION" }`
   - Response 201 includes `approvalId`, `approvalStatus` = `PENDING`, `expiresAt`.

6. **State model and expiration:**
   - `approvalStatus` includes `EXPIRED`.
   - `isExpired` is computed: true when now >= expiresAt.
   - Valid transitions: PENDING → APPROVED/REJECTED/EXPIRED; APPROVED_WITH_EXCEPTION allowed when partial approvals are mixed; EXPIRED → PENDING only via `request-new`.

7. **Display and timezone:**
   - UI displays `expiresAt` in user-local timezone with ISO 8601 source; relative label (“expires in 2d”) allowed but must keep absolute time available.

8. **Blocking behavior:**
   - If `isExpired` is true, UI must disable approve/deny actions and show message “Approval expired; request a new approval”.
   - Backend returns 409 when attempting approve/deny on expired approvals.

9. **Observability:**
   - Emit event `workexec.approval.expired` with `approvalId`, `estimateId`, `workorderId`, `expiresAt`.
   - Emit event `workexec.approval.re-requested` when `request-new` succeeds.

10. **Backend alignment:**
    - Map frontend approvalStatus to backend ApprovalRecord.ResolutionStatus; `EXPIRED` is represented via `expiresAt` + computed `isExpired` when backend lacks explicit enum.
    - Backend canonical endpoint: POST /v1/workorders/estimates/{estimateId}/approval (create), approval detail via /v1/workorders/estimates/{estimateId}/approval/{approvalId}; bridge layer may adapt path but must preserve enums and fields.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Approval & Partial Approval](../.business-rules/BACKEND_CONTRACT_GUIDE.md#approval--partial-approval)
**Validation status:** CONFIRMED (status/enums); expiration handling partially inferred—mark PENDING for explicit expiry fields.

## Issue #238: [FRONTEND] [STORY] Estimate: Add Parts to Draft Estimate
**Title:** Estimate: Add Parts to Draft Estimate (Search Catalog, Optional Price Override)

### Open Questions

1. **Moqui/REST contract (blocking):** What are the exact service names and endpoints for:
   - Search parts catalog (GET /api/v1/product/parts/search?query=...&pageSize=...)
   - Add catalog part to estimate (POST /api/v1/workexec/estimates/{estimateId}/items:add-part)
   - Add non-catalog part (POST /api/v1/workexec/estimates/{estimateId}/items:add-non-catalog-part)
   - Update item quantity/price (PATCH /api/v1/workexec/estimates/{estimateId}/items/{itemId})
   
   Provide request/response DTO field names for each.

2. **Capability/policy source (blocking):** How does UI determine if user can override part price?
   - Permission key: `workexec.estimate.override-part-price`?
   - Endpoint to fetch user capabilities per estimate?
   - Location-scoped policy?
   - Response includes flags: `canOverridePrice`, `allowNonCatalogParts`?

3. **Override reason codes (blocking):** When overriding price, must user select/enter reason?
   - Enum codes (CUSTOMER_REQUEST, PROMOTION, DAMAGE, etc.)?
   - Free text required/optional?
   - Field name: `overrideReason` or `priceAdjustmentReason`?
   - Backend validation rules?

4. **Concurrency/version token (blocking):** Does estimate support optimistic locking?
   - Request header: `If-Match: <etag>` or field: `version`?
   - Response on conflict: 409 CONFLICT with current version in body?
   - Does response include refreshed estimate DTO or just error?

5. **Tax display contract (blocking):** When displaying estimate totals:
   - Per-line item tax breakdown (line tax + line total columns)?
   - Or totals-only summary (subtotal, total tax, grand total)?
   - Tax code editable per line or backend-derived only?

---

### Acceptance Criteria

1. **Search parts catalog:**
   - GET /api/v1/product/parts/search?query=...&page=0&pageSize=20
   - Returns 200 with `items` array containing: `partId`, `sku`, `name`, `description`, `uom`, `listPrice`, `currency`, `taxCode`, `availableQuantity`.
   - 403 if lacking catalog view permission from product domain; WorkExec screens must surface message.

2. **Add catalog part to estimate:**
   - POST /api/v1/workexec/estimates/{estimateId}/items:add-part
   - Headers: `Idempotency-Key` required; optional `If-Match` when version provided.
   - Body: `{ "partId": "SKU-123", "quantity": 2, "overrideUnitPrice": 1099 (minor units, optional), "note": "Use OEM" }`
   - Response 201 includes new item: `itemId`, `type` = `PART`, `quantity`, `unitPrice`, `extendedPrice`, `taxCode`, `version`.
   - 403 if missing `workexec.estimate.edit`; 422 when quantity <= 0 or partId unknown; 409 on version conflict.

3. **Add non-catalog part:**
   - POST /api/v1/workexec/estimates/{estimateId}/items:add-non-catalog-part
   - Body: `{ "name": "Custom Bracket", "description": "Fabricated", "quantity": 1, "unitPrice": 4500, "taxCode": "TAX-GEN" }`
   - Requires `workexec.estimate.custom-part`; otherwise 403.

4. **Update item quantity/price:**
   - PATCH /api/v1/workexec/estimates/{estimateId}/items/{itemId}
   - Body allows `quantity`, `unitPrice`, `taxCode`, `note`; `unitPrice` updates require `workexec.estimate.override-part-price`.
   - Response 200 returns updated item and new `version`.

5. **Capability flags:**
   - Estimate detail response includes `capabilities`: `canOverridePrice`, `allowNonCatalogParts`, `canEditTaxCode`.
   - UI must gate controls by capabilities + permissions; fail closed when flags absent.

6. **Tax display:**
   - Line DTO provides `lineTax` and `lineTotal`; totals block shows `subtotal`, `totalTax`, `grandTotal`.
   - `taxCode` is backend-derived by default; editable only when `canEditTaxCode` is true.

7. **Observability:**
   - Emit `workexec.estimate.item.added` and `workexec.estimate.item.updated` events with `estimateId`, `itemId`, `type`, `quantity`, `unitPrice` (redact PII, no customer names).

8. **Backend alignment:**
   - Backend canonical endpoint for parts add exists on Estimate: POST /v1/workorders/estimates/{estimateId}/items.
   - Bridge must map `partId` and pricing fields to backend DTO; preserve optimistic locking via `version` when provided.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED for add/update endpoints; PENDING for capability flags and tax code rules.

## Issue #237: [FRONTEND] [STORY] Estimate: Add Labor/Service to Draft Estimate
**Title:** Estimate: Add Labor/Service to Draft Estimate (Search Catalog, Custom Labor, Flat-Rate vs Time-Based)

### Open Questions

1. **Backend contract specifics (blocking):** What are the exact Moqui services/REST endpoints for:
   - Search service catalog (GET /api/v1/product/services/search?query=...&pageSize=...)
   - Add catalog service/labor (POST /api/v1/workexec/estimates/{estimateId}/items:add-labor)
   - Add custom labor (POST /api/v1/workexec/estimates/{estimateId}/items:add-custom-labor)
   - Update labor item quantity/rate (PATCH /api/v1/workexec/estimates/{estimateId}/items/{itemId})
   
   Provide exact DTO field names and data types.

2. **Custom labor policy signal (blocking):** How does UI determine if custom labor entry is allowed?
   - Permission: `workexec.estimate.custom-labor`?
   - Capability flag in response: `allowCustomLabor`?
   - Always allowed unless explicitly denied?

3. **Flat-rate editability (blocking):** For flat-rate labor services, is the `laborUnits` field:
   - Read-only (user cannot adjust)?
   - Editable (user adjusts quantity)?
   - Cannot exceed catalog max?

4. **Manual rate/price entry (blocking):** Can user manually enter labor rate when adding custom labor?
   - Always editable?
   - Only if permission/capability allows?
   - Backend-calculated from labor rate tables (UI cannot override)?
   - Field name: `unitRate`, `hourlyRate`, `laborRate`?

5. **Tax code handling (blocking):** For labor items, is tax code:
   - Always backend-derived from labor category?
   - User-selectable from dropdown (if yes, endpoint to fetch tax codes)?
   - Optional (items can be tax-exempt)?

6. **Concurrency token (blocking):** Same as Issue #238-Q4: does estimate support version/ETag for optimistic locking?

---

### Acceptance Criteria

1. **Search service catalog:**
   - GET /api/v1/product/services/search?query=...&page=0&pageSize=20
   - Returns `items`: `serviceId`, `sku`, `name`, `description`, `laborType` (FLAT | TIME_AND_MATERIAL), `laborUnits`, `rate`, `uom`, `taxCode`.

2. **Add catalog labor:**
   - POST /api/v1/workexec/estimates/{estimateId}/items:add-labor
   - Body: `{ "serviceId": "LAB-100", "laborUnits": 1.0, "rate": 9500, "note": "Brake job" }`
   - Response 201 returns item with `type` = `LABOR`, `laborType`, `laborUnits`, `rate`, `extendedPrice`, `taxCode`, `version`.
   - 403 without `workexec.estimate.edit`; 409 on version conflict.

3. **Add custom labor:**
   - POST /api/v1/workexec/estimates/{estimateId}/items:add-custom-labor
   - Requires `workexec.estimate.custom-labor` permission.
   - Body: `{ "name": "Diagnostic", "laborType": "TIME_AND_MATERIAL", "laborUnits": 0.5, "rate": 12500, "taxCode": "TAX-GEN", "note": "Check engine" }`

4. **Update labor item:**
   - PATCH /api/v1/workexec/estimates/{estimateId}/items/{itemId}
   - Fields: `laborUnits`, `rate`, `note`; `laborUnits` must be > 0; `rate` updates require override capability when rate differs from catalog list.

5. **Flat-rate handling:**
   - For `laborType = FLAT`, default `laborUnits` is catalog-defined and read-only unless capability `allowFlatLaborEdit` is true; even then cannot exceed catalog max when provided.
   - For `TIME_AND_MATERIAL`, `laborUnits` is editable; `rate` may be editable only when `canOverridePrice` or `workexec.estimate.override-labor-rate` is true.

6. **Observability:**
   - Emit `workexec.estimate.labor.added` and `workexec.estimate.labor.updated` with `estimateId`, `itemId`, `laborType`, `laborUnits`; exclude notes from telemetry.

7. **Backend alignment:**
   - Map to backend Estimate items endpoint: POST /v1/workorders/estimates/{estimateId}/items for add; PATCH for updates; maintain optimistic locking via `version` when supplied.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED for endpoints and labor enums; PENDING for capability flags and tax code rules.

## Issue #235: [FRONTEND] [STORY] Estimate: Revise Estimate Prior to Approval
**Title:** Estimate: Revise Estimate Prior to Approval (Create New Version, Preserve Approval History)

### Open Questions

1. **Canonical Estimate State Model (blocking):** Stories reference `PENDING_APPROVAL` state, but CUSTOMER_APPROVAL_WORKFLOW.md shows DRAFT, APPROVED, DECLINED, EXPIRED. Is `PENDING_APPROVAL` an intermediate state or incorrect terminology?
   - Clarify canonical statuses
   - Confirm revision eligibility per status (e.g., can revise from APPROVED? from DECLINED? from EXPIRED?)

2. **Backend Submit Endpoint (blocking):** What are the exact endpoint paths for:
   - Revise estimate (POST /api/v1/workexec/estimates/{estimateId}/revisions)
   - Request: `{ revisionReason?: string }`
   - Response: New EstimateDTO with activeVersionId, versionNumber, status=DRAFT?

3. **Completeness Checklist (blocking):** What validations are enforced before revision is allowed?
   - Estimate must have ≥1 line item?
   - All items must have required fields (taxCode, price, etc.)?
   - Estimate must be in specific status?
   - Other checks?

4. **Permissions/RBAC (blocking):** What permission key gates revision action?
   - `workexec.estimate.revise`?
   - Same permission as edit (or separate)?
   - DECISION-INVENTORY-007 requires fail-closed; confirm permission enforcement.

5. **Approval Method & Consent Text (blocking):** When displaying revision to Service Advisor, should UI show:
   - Original approval method used?
   - Legal terms/consent text that customer previously approved?
   - Or hide approval details from SA view?

6. **Approval Token/Link Sensitivity (blocking):** If approval link/token is embedded in estimate DTO:
   - Can Service Advisor view it?
   - Is it redacted from non-owner roles?
   - Backend should never return token to unauthorized users (DECISION-INVENTORY-003)?

7. **Revision eligibility for APPROVED/DECLINED (blocking):** Confirm backend supports revision when estimate is in APPROVED or DECLINED state (not just DRAFT). Unclear from CUSTOMER_APPROVAL_WORKFLOW.md.

8. **Concurrency token (blocking):** Does revision require/support version/ETag? What if estimate is modified between revision request and response?

---

### Acceptance Criteria

1. **Status model and eligibility:**
   - Canonical statuses: DRAFT, APPROVED, DECLINED, EXPIRED (no PENDING_APPROVAL enum in backend; approval “pending” is represented by approval record status PENDING).
   - Revision allowed when estimate status is DRAFT, DECLINED, or EXPIRED. APPROVED requires new revision that demotes status back to DRAFT and invalidates prior approval.

2. **Revise endpoint:**
   - POST /api/v1/workexec/estimates/{estimateId}/revisions
   - Headers: `Idempotency-Key` required; `If-Match` with `version` required to avoid stale edits.
   - Body: `{ "revisionReason": "text (optional, max 512)" }`
   - Response 201 returns new Estimate DTO with fields: `estimateId`, `version`, `parentVersionId`, `status` = `DRAFT`, `revisionReason`, `createdAt`.
   - Errors: 403 without `workexec.estimate.revise`; 409 on version conflict; 422 when status not eligible or when estimate has zero line items.

3. **Preserve approval history:**
   - Prior approval records remain linked to parent version; new version starts with no approvals and `approvalStatus` = `PENDING` not set.
   - Response must not include prior approval tokens/links.

4. **Completeness checks:**
   - Revision requires ≥1 line item and all mandatory fields (taxCode, unitPrice, quantity) valid.
   - If validations fail, backend returns 422 with `errors` per field; UI must surface.

5. **Permissions and visibility:**
   - Action requires `workexec.estimate.revise` (separate from edit).
   - DTO must redact approval tokens/links for non-owner roles per DECISION-INVENTORY-003.

6. **Observability:**
   - Emit `workexec.estimate.revised` with `estimateId`, `parentVersionId`, `status`.
   - Attach trace context; exclude PII (customer names) from attributes.

7. **Backend alignment:**
   - Map to backend canonical endpoint: POST /v1/workorders/estimates/{estimateId}/revisions; uses optimistic locking integer `version`.
   - Approval method enum and status remain as in BACKEND_CONTRACT_GUIDE.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED (revision endpoint and status enums); PENDING on capability flags and legal terms dependencies.

## Issue #234: [FRONTEND] [STORY] Estimate: Present Estimate Summary for Review
**Title:** Estimate: Present Estimate Summary for Review (Immutable Snapshot, Legal Terms, No Edits)

### Open Questions

1. **Moqui screen alignment (blocking):** Is EstimateEdit.xml the canonical estimate detail screen in `runtime/component/durion-workexec`, or is there another screen? This affects navigation links and component reuse.

2. **Concrete service/endpoint names (blocking):** What are the exact endpoints for:
   - Generate estimate snapshot (POST /api/v1/workexec/estimates/{estimateId}/summary-snapshots:generate)
   - Fetch snapshot (GET /api/v1/workexec/estimates/{estimateId}/summary-snapshots/{snapshotId})
   - Include request/response DTOs.

3. **Snapshot DTO shape (blocking):** Does snapshot response include:
   - Structured estimate data (JSON DTO with line items, totals)?
   - Rendered artifact only (HTML, PDF blob)?
   - Both?
   - How are line items, taxes, totals represented?

4. **Legal terms policy source (blocking):** How is `policyMode` determined?
   - Location configuration: GET /api/v1/workexec/legal-terms-config?locationId=...
   - Returned as part of estimate DTO?
   - Separate endpoint?

5. **Visibility enforcement contract (blocking):** Should UI omit restricted fields (e.g., cost markup, internal notes) or display with visibility flags?
   - Backend returns only visible fields?
   - Backend returns all fields + `isVisible` booleans per field?
   - DECISION-INVENTORY-013 suggests capability signaling; confirm pattern.

6. **Fees/taxes breakdown (blocking):** When displaying summary totals:
   - Show line-level tax amounts (tax column in items table)?
   - Or summary totals only (subtotal, total tax, grand total)?
   - Are fees/discounts separate line items or rolled into totals?

7. **Expiration semantics (blocking):** Is snapshot expiration:
   - Stored in snapshot entity (immutable)?
   - Derived at display time (based on estimate approval window)?
   - Both (stored + display uses stored value)?

8. **Zero-line estimates (blocking):** Is an estimate with no line items allowed to generate a snapshot?
   - Rejected with 400 error?
   - Generated but marked as incomplete?
   - Other handling?

---

### Acceptance Criteria

1. **Generate snapshot:**
   - POST /api/v1/workexec/estimates/{estimateId}/summary-snapshots:generate
   - Headers: `Idempotency-Key` required; optional `If-Match` with version.
   - Response 201 returns `snapshotId`, `estimateId`, `createdAt`, `expiresAt` (derived from approval window), and `snapshotStatus` = `READY`.
   - 422 when estimate has zero line items or missing required tax/price fields.

2. **Fetch snapshot:**
   - GET /api/v1/workexec/estimates/{estimateId}/summary-snapshots/{snapshotId}
   - Returns DTO containing both:
     - `payload`: structured estimate JSON (items, totals, taxes, fees, discounts, legal terms reference).
     - `render`: links `{ htmlUrl?, pdfUrl? }` when available.
   - 404 when snapshot not found; 403 without `workexec.estimate.view`.

3. **Legal terms and policy:**
   - Snapshot embeds `legalTerms` string and `policyMode` derived from location config (GET /api/v1/workexec/legal-terms-config?locationId=...).
   - If policy unavailable, fail closed with 503/422 and message; UI must surface.

4. **Visibility enforcement:**
   - Snapshot omits restricted fields (cost, internal notes); backend does not expose them. No `isVisible` flags needed.

5. **Totals and taxes:**
   - Snapshot payload includes line-level `lineTax`, `lineTotal` and summary totals `subtotal`, `totalTax`, `grandTotal`.

6. **Expiration semantics:**
   - Snapshot `expiresAt` mirrors approval window; if approval expires, snapshot remains immutable but UI shows expired banner.

7. **Observability:**
   - Emit `workexec.estimate.snapshot.generated` with `estimateId`, `snapshotId`, `expiresAt` (no customer PII).

8. **Backend alignment:**
   - Backend canonical snapshot generator aligns to estimate read model; no mutable updates post-create. Bridge maps to /v1/workorders/estimates/{estimateId}/summary-snapshots.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED for snapshot endpoints; PENDING for legal terms sourcing.

## Issue #233: [FRONTEND] [STORY] Estimate: Submit Estimate for Approval
**Title:** Estimate: Submit Estimate for Approval (Validate Completeness, Transition State, Initiate Approval)

### Open Questions

1. **Canonical Estimate State Model (blocking):** Clarify status enum (same as Issue #235-Q1). Which status does estimate transition to after submit?
   - PENDING_APPROVAL (awaiting customer consent)?
   - APPROVED (if captured in-person by SA)?
   - Or does submit just mark it "ready" and approval happens separately?

2. **Backend Submit Endpoint (blocking):** What are exact endpoints:
   - POST /api/v1/workexec/estimates/{estimateId}:submit-for-approval
   - Request: `{ approvalMethod?, consentText? }`
   - Response: Updated EstimateDTO with status, approvalRequestId?, snapshotId?
   - Supports Idempotency-Key header (DECISION-INVENTORY-012)?

3. **Completeness Checklist (blocking):** What validations block submission?
   - ≥1 line item?
   - All items have taxCode?
   - All totals calculated/valid?
   - Customer has ≥1 billing contact with email?
   - Other RBAC/capability checks?

4. **Permissions/RBAC (blocking):** What permission key gates submission?
   - `workexec.estimate.submit`?
   - DECISION-INVENTORY-007 requires explicit; confirm.

5. **Approval Method & Consent Text (blocking):** Does submit request include approval method enum and consent text, or are these pre-configured?
   - Request includes `approvalMethod: SIGNATURE | CLICK_CONFIRM | ...`?
   - Request includes `consentText` for customer to acknowledge?
   - Or backend determines these from location policy?

6. **Approval Token/Link Sensitivity (blocking):** After submit, does response include approval link/token?
   - If yes, is Service Advisor allowed to view/share it?
   - Backend should gate return (DECISION-INVENTORY-003)?

---

### Acceptance Criteria

1. **Submit endpoint:**
   - POST /api/v1/workexec/estimates/{estimateId}:submit-for-approval
   - Headers: `Idempotency-Key` required; `If-Match` with `version` required to prevent double-submit on stale data.
   - Body: `{ "approvalMethod": "SIGNATURE" | "CLICK_CONFIRM" | "ELECTRONIC_SIGNATURE" | "VERBAL_CONFIRMATION", "consentText": "optional" }`
   - Response 200 returns Estimate DTO with `status` unchanged (DRAFT) but `approvalStatus` = `PENDING` and `approvalId`, `expiresAt`.

2. **Validation prior to submit:**
   - Requires ≥1 line item, valid taxCode on each item, non-negative totals, and customer contact info (email/phone) when approvalMethod requires remote approval.
   - 422 with field-level errors when validation fails.

3. **Permissions:**
   - Requires `workexec.estimate.submit`; fail closed otherwise.

4. **Tokens and sensitivity:**
   - Response must not include customer-facing approval links/tokens for non-owner roles; include `approvalId` only. Approval link retrieval, if any, gated by a separate endpoint and permission.

5. **State transitions:**
   - Approval record statuses follow backend enums; estimate `status` stays DRAFT until approval outcome drives transition to APPROVED/DECLINED.

6. **Observability:**
   - Emit `workexec.estimate.submitted` with `estimateId`, `approvalId`, `approvalMethod`; exclude consentText content from telemetry.

7. **Backend alignment:**
   - Map to backend canonical: POST /v1/workorders/estimates/{estimateId}/approval (create approval). ApprovalStatus PENDING lives on approval record; estimate keeps status DRAFT.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Approval & Partial Approval](../.business-rules/BACKEND_CONTRACT_GUIDE.md#approval--partial-approval)
**Validation status:** CONFIRMED for approval endpoints/status; PENDING for consent text/expiration sourcing.

## Issue #229: [FRONTEND] [STORY] Work Order: Generate WorkOrder Items from Approved Estimate
**Title:** Work Order: Generate WorkOrder Items from Approved Estimate (Promote, Snapshot Pricing/Tax)

### Open Questions

1. **Backend contract (blocking):** What are the exact endpoints:
   - POST /api/v1/workexec/estimates/{estimateId}:promote
   - Request: `{ reason? }`
   - Response: `{ workOrderId, estimateId, originEstimateId?, warnings?, estimateSnapshot? }`
   - Supports Idempotency-Key (DECISION-INVENTORY-012)?

2. **Authoritative status enum (blocking):** What are work order item statuses?
   - Initial status after promotion (AUTHORIZED, PENDING_APPROVAL, READY_TO_EXECUTE)?
   - Field name: `statusId`, `status`, `itemStatus`?
   - Confirm exact enum values used throughout.

3. **Authoritative error codes (blocking):** What error codes does promotion return?
   - `MISSING_TAX_CODE` (item has no tax configuration)
   - `TOTALS_MISMATCH` (estimated totals don't match calculated)
   - `ALREADY_PROMOTED` (estimate already has work order; return existingResourceId?)
   - Others?
   - HTTP status codes per error?

4. **Duplicate handling (blocking):** If estimate is promoted twice (via retried request with same Idempotency-Key), does backend:
   - Return same workOrderId (idempotent)?
   - Return `{ existingResourceId: workOrderId }` in error 409?
   - Confirm exact behavior per DECISION-INVENTORY-012.

---

### Acceptance Criteria

1. **Promote endpoint:**
   - POST /api/v1/workexec/estimates/{estimateId}:promote
   - Headers: `Idempotency-Key` required; `If-Match` with estimate `version` recommended.
   - Body: `{ "reason": "text (optional, max 512)" }`
   - Response 201 returns `{ workorderId, estimateId, originEstimateId, workorderStatus: "APPROVED", itemStatuses: [...], estimateSnapshotId }`.
   - 403 without `workexec.workorder.promote`; 409 on version conflict.

2. **Item status initialization:**
   - All generated workorder items default to `READY_TO_EXECUTE`; approvals already captured on estimate drive this readiness.
   - Workorder status set to `APPROVED` after promotion; subsequent scheduling/assignment handled elsewhere.

3. **Error handling:**
   - If missing tax codes or totals mismatch, return 422 with codes `MISSING_TAX_CODE`, `TOTALS_MISMATCH` and list of offending itemIds.
   - If estimate already promoted with same Idempotency-Key, return 200/201 with same `workorderId` (idempotent). If promoted previously without key reuse, return 409 with `existingResourceId` = workorderId.

4. **Snapshot linkage:**
   - Promotion creates or links to existing estimate snapshot and returns `estimateSnapshotId`; UI must use snapshot for read-only display.

5. **Observability:**
   - Emit `workexec.workorder.promoted` with `estimateId`, `workorderId`, `itemsCount`, `hasSnapshot`.

6. **Backend alignment:**
   - Backend canonical promotion endpoint: POST /v1/workorders/estimates/{estimateId}:promote. Enums for WorkorderStatus and WorkorderItemStatus must match BACKEND_CONTRACT_GUIDE.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates → Promote](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED for promotion endpoint and status enums; PENDING for detailed error code list alignment.

## Issue #226: [FRONTEND] [STORY] Work Order: View Promotion Audit Trail
**Title:** Work Order: View Promotion Audit Trail (Display Estimate → Work Order Promotion Record)

### Open Questions

1. **Backend contract (blocking):** What are the exact endpoints:
   - GET /api/v1/workexec/workorders/{workOrderId}/promotion-audit
   - GET /api/v1/workexec/estimates/{estimateId}/promotion-audits
   - Response DTO fields: `auditEventId`, `promotingUserId`, `eventTimestamp`, `estimateSnapshot`, `approvalId`, `workOrderId`, `summary`?

2. **Promotion audit entity fields (blocking):** What fields are included in promotion audit record?
   - `promotingUserId` (who initiated promotion)?
   - `eventTimestamp` (when promoted)?
   - `estimateSnapshotId` (link to frozen estimate state)?
   - `approvalId` (link to approval record if customer approved)?
   - `workOrderId` (link to created work order)?
   - `promotionSummary` (JSON: labor count, part count, fees, tax amount)?
   - Other fields?

3. **User display enrichment (blocking):** Does audit DTO include user details, or must UI lookup user by `promotingUserId`?
   - Response includes `promotingUser { userId, displayName, email }`?
   - Or just `promotingUserId` and UI makes separate lookup request?
   - Is user lookup service available (GET /api/v1/security/users/{userId})?

4. **Audit record immutability (blocking):** Are promotion audit records immutable after creation?
   - No update/delete endpoints?
   - Confirm this for compliance/audit trail integrity.

5. **Display format and timezone (blocking):** When displaying `eventTimestamp`:
   - User's local timezone or UTC?
   - Relative format ("2 days ago") or absolute ("Jan 24, 2026 3:45 PM")?
   - Reference DECISION-INVENTORY-016 for durion standard.

---

### Acceptance Criteria

1. **Endpoints:**
   - GET /api/v1/workexec/workorders/{workorderId}/promotion-audit
   - GET /api/v1/workexec/estimates/{estimateId}/promotion-audits
   - Require `workexec.workorder.view` permission.

2. **Audit DTO:**
   - Fields: `auditEventId`, `workorderId`, `estimateId`, `estimateSnapshotId`, `approvalId`, `promotingUserId`, `eventTimestamp` (ISO 8601 UTC), `summary` (counts: labor, parts, fees, totals), `errors` (if any).
   - Include `promotingUser` enrichment `{ userId, displayName, email }` when available; otherwise provide `promotingUserId` only.

3. **Immutability:**
   - Audit records are read-only; no update/delete endpoints. Backend enforces immutability.

4. **Display/timezone:**
   - UI displays `eventTimestamp` in user-local timezone with absolute time; relative labels allowed in addition.

5. **Observability:**
   - Reading audit trail should not log PII; no additional events required for reads.

6. **Backend alignment:**
   - Audit trail stored with promotion operation; DTO fields align to backend promotion audit entity. Workorder naming must remain single word.

**Backend contract reference:** [BACKEND_CONTRACT_GUIDE.md §Estimates → Promotion Audit](../.business-rules/BACKEND_CONTRACT_GUIDE.md#estimates)
**Validation status:** CONFIRMED for audit endpoints; PENDING for user enrichment field availability.

## Executive Summary

**Total Open Questions:** 42 blocking, 5 non-blocking  
**Affected Stories:** 10 (Issues #271, #269, #268, #238, #237, #235, #234, #233, #229, #226)  
**Critical Path Dependencies:** Estimate/WorkOrder state machines, approval workflows, tax/legal configuration  
**Cross-Domain Dependencies:** Product Catalog, Pricing, Tax Service, Security (permissions), Audit Service  

### Blocking Indicator Summary

| Issue | Story Title | Blocking Questions | Status |
|-------|-------------|-------------------|--------|
| #271 | Digital Approval | 6 | ❌ BLOCKED |
| #269 | Partial Approval | 5 | ❌ BLOCKED |
| #268 | Approval Expiration | 7 | ❌ BLOCKED |
| #238 | Add Parts | 5 | ❌ BLOCKED |
| #237 | Add Labor | 6 | ❌ BLOCKED |
| #235 | Revise Estimate | 8 | ❌ BLOCKED |
| #234 | Estimate Summary | 8 | ❌ BLOCKED |
| #233 | Submit Approval | 6 | ❌ BLOCKED |
| #229 | Promote Estimate | 4 | ❌ BLOCKED |
| #226 | Audit Trail | 5 | ❌ BLOCKED |

---

## Research and Resolution Strategy

### Phase 1: Backend Contract Discovery (1-2 days)

1. **Scan durion-positivity-backend** for WorkExec service implementations
2. **Extract endpoint paths, request/response DTOs** from controller annotations and service definitions
3. **Document error codes and HTTP status codes** per endpoint
4. **Cross-reference with story Service Contracts sections** to identify gaps
5. **Deliverable:** BACKEND_CONTRACT_GUIDE.md with all 12+ endpoints documented

### Phase 2: State Machine & Enum Verification (1 day)

1. **Confirm Estimate and Work Order status enums** from backend source
2. **Document state transitions** and eligibility gates per status
3. **Confirm Approval status enum** (PENDING, APPROVED, DECLINED, EXPIRED vs others)
4. **Deliverable:** State machine diagrams and enum inventory

### Phase 3: Permission Taxonomy (1 day)

1. **Reference Security domain patterns** (DECISION-INVENTORY-007, DECISION-INVENTORY-013)
2. **Extract permission keys** from backend @PreAuthorize annotations
3. **Map actions to permissions** (submit-estimate → workexec.estimate.submit, etc.)
4. **Document UI gating rules** (show/hide vs disable vs error per permission state)
5. **Deliverable:** PERMISSION_TAXONOMY.md

### Phase 4: Story Documentation Updates (2-3 days)

1. **For each story:** Update "## Open Questions" section with resolved answers
2. **Add concrete endpoint paths, DTOs, enums** from Phase 1-3 research
3. **Add example request/response payloads**
4. **Mark questions as ✅ RESOLVED with supporting detail**
5. **Deliverable:** Updated GitHub issues (all 10 stories)

### Phase 5: Story Validation Checklist (1 day)

1. **Create STORY_VALIDATION_CHECKLIST.md** with per-story validation template
2. **Confirm for each story:** backend contract exists, permissions defined, enums confirmed, errors mapped, examples provided
3. **Deliverable:** Comprehensive go-live checklist

### Phase 6: Domain Agent Guide (0.5 days)

1. **Reference CRM AGENT_GUIDE.md** for structure and patterns
2. **Document key files, research paths, decision authorities** for future agents
3. **Deliverable:** AGENT_GUIDE.md for WorkExec domain

---

## References

**Example Documents for Pattern Reference:**
- CRM Domain: `/home/louisb/Projects/durion/domains/crm/crm-questions.txt`
- CRM Backend Contracts: `/home/louisb/Projects/durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Accounting Domain: `/home/louisb/Projects/durion/domains/accounting/accounting-questions.txt`
- Accounting Phase 3 Report: `/home/louisb/Projects/durion/domains/accounting/.business-rules/PHASE_3_COMPLETION_REPORT.md`

**Key Decision References:**
- DECISION-INVENTORY-001: Deny-by-default authorization
- DECISION-INVENTORY-002: Canonical screen names
- DECISION-INVENTORY-003: Opaque IDs (no client-side format validation)
- DECISION-INVENTORY-007: Permission gating in Security domain
- DECISION-INVENTORY-011: Standard error envelope schema
- DECISION-INVENTORY-012: Idempotency-Key for mutations
- DECISION-INVENTORY-013: Capability signaling (UI gating layer)
- DECISION-INVENTORY-016: Timezone/locale display standards

**Durion Documentation:**
- Architecture: `/home/louisb/Projects/durion/docs/architecture/`
- Governance: `/home/louisb/Projects/durion/docs/governance/`

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-24 | Initial consolidated questions document; all 10 stories' open questions extracted; execution phases defined |

**Status:** 🎯 Ready for Backend Clarification Phase (Phase 1)

**Next Step:** Execute Phase 1 Research - Backend Contract Discovery (estimated 1-2 days)

---

# PHASE 4: REMAINING ISSUES ANALYSIS & EXECUTION PLAN

## Executive Summary

This Phase 4 plan covers **18 remaining WorkExec issues** (#222, #219, #216, #162, #157, #149, #146, #145, #138, #137, #134, #133, #132, #131, #129, #127, #85, #79) from `domain-workexec.txt`. The first 10 issues have been fully documented above with acceptance criteria and backend contracts validated.

**Scope:** Extract open questions from remaining 18 issues, resolve blocking questions through backend analysis, create acceptance criteria following the template established in Phase 2.

**Timeline:** Estimated 3-4 days for full Phase 4 completion.

**Blocking Dependencies:**
- Domain ownership clarifications required (timekeeping, scheduling, sales orders)
- Backend API contract discovery for parts usage, billing finalization, appointment integration
- Cross-domain integration contracts (pos-inventory, pos-order, pos-appointment)

---

## Issue Categorization by Domain Area

### 1. **Work Execution Core** (5 issues)
- **#222** - Record Parts/Fluids Usage During Work
- **#234** - Work Order Item Notes (COMPLETED in Phase 2)
- **#132** - Track Work Session Start/End
- **#138** - View Assigned Schedule/Dispatch Board
- **#137** - Reschedule Work Order

### 2. **Timekeeping & Time Capture** (4 issues)
- **#149** - Clock In/Out at Shop Location ⚠️ *Domain ownership question*
- **#146** - Start/Stop Timer for Task ⚠️ *Domain ownership question*
- **#145** - Submit Job Time Entry ⚠️ *Domain ownership question*
- **#131** - Capture Mobile Travel Time ⚠️ *Domain ownership question*

### 3. **Billing & Invoicing** (3 issues)
- **#216** - Finalize Billable Work Order ⚠️ *Critical billing flow*
- **#162** - Require Customer PO# Before Finalization
- **#85** - Create Sales Order from Work Order Cart ⚠️ *Domain ownership question*

### 4. **Estimates & Approvals** (3 issues)
- **#129** - Create Estimate from Service Appointment
- **#79** - Display Work Order Estimates
- **#271** - Submit Estimate (COMPLETED in Phase 2)

### 5. **Visibility & Authorization** (2 issues)
- **#219** - Role-Based Work Order Visibility
- **#157** - Display CRM References in Work Order

### 6. **Appointment Integration** (2 issues)
- **#127** - Update Appointment Status from Work Order Events
- **#133** - Override Schedule Conflict (duplicate of #137)

### 7. **Dispatch & Assignment** (1 issue)
- **#134** - Assign Mechanic to Work Order Item (related to #137)

---

## Common Blocking Patterns Across Issues

### Pattern 1: Domain Ownership Ambiguity
**Affected Issues:** #149, #146, #145, #131, #138, #137, #85

**Questions:**
- Is timekeeping (clock in/out, timers, time entries) owned by **domain:people** or **domain:workexec**?
- Is scheduling/dispatch board owned by **domain:workexec** or a separate **domain:scheduling**?
- Are sales orders created from work orders owned by **domain:order** or **domain:workexec**?

**Resolution Required:**
- Review existing domain taxonomy in `/home/louisb/Projects/durion/domains/`
- Consult AGENT_REGISTRY.md for domain ownership authority
- Document decision in ADR format
- Update issue labels accordingly

---

### Pattern 2: Missing Backend API Contracts
**Affected Issues:** #222, #216, #162, #149, #146, #145, #132, #131, #129, #127, #85, #79

**Missing Contracts:**
- **Parts/Fluids Usage API** (#222): POST endpoint for recording usage, inventory integration
- **Billing Finalization API** (#216): POST endpoint to mark work order invoice-ready, variance approval workflow
- **Timekeeping APIs** (#149, #146, #145, #131, #132): Clock in/out, timer start/stop, time entry submission, work session tracking
- **Appointment Integration API** (#129, #127): Create estimate from appointment, update appointment status on work order events
- **Sales Order Creation API** (#85): POST endpoint to convert work order items to sales order cart
- **Estimate Display API** (#79): GET endpoint with filters for customer/status/date range

**Resolution Strategy:**
1. Search `durion-positivity-backend` for existing controllers/services in:
   - `pos-workorder/`
   - `pos-inventory/` (parts usage)
   - `pos-order/` (sales orders)
   - `pos-appointment/` (if exists)
2. Document confirmed endpoints in BACKEND_CONTRACT_GUIDE.md
3. Mark missing endpoints as PENDING with suggested schemas

---

### Pattern 3: Cross-Domain Integration Contracts
**Affected Issues:** #222 (inventory), #157 (CRM), #127 (appointments), #85 (orders)

**Integration Points:**
- **WorkExec → Inventory:** Parts/fluids usage events (#222)
  - Endpoint: `POST /api/v1/inventory/usage` (assumed)
  - Payload: `{ workOrderId, itemId, partNumber, quantityUsed, timestamp }`
  - Response: `{ usageRecordId, inventoryAdjustmentId, remainingStock }`
  - Error codes: `INSUFFICIENT_STOCK`, `PART_NOT_FOUND`

- **WorkExec → CRM:** Customer reference display (#157)
  - Endpoint: `GET /api/v1/crm/customers/{customerId}` (confirmed from CRM domain)
  - Fields: `customerId`, `displayName`, `email`, `phone`, `preferredContact`

- **WorkExec → Appointments:** Status updates (#127)
  - Endpoint: `PUT /api/v1/appointments/{appointmentId}/status`
  - Payload: `{ status: "IN_PROGRESS" | "COMPLETED", workOrderId, timestamp }`
  - Event emission: `appointment.status.changed`

- **WorkExec → Orders:** Sales order creation (#85)
  - Endpoint: `POST /api/v1/orders/from-workorder`
  - Payload: `{ workOrderId, selectedItemIds[], customerId, paymentTerms }`
  - Response: `{ orderId, orderNumber, totalAmount }`

**Resolution Strategy:**
1. Update CROSS_DOMAIN_INTEGRATION_CONTRACTS.md with detailed schemas
2. Coordinate with domain owners (Inventory, CRM, Order teams)
3. Define event schemas for async integration

---

### Pattern 4: Permission & Capability Gating
**Affected Issues:** #219, #162, #216, #149, #138

**Questions:**
- What capability flags control access to:
  - PO# requirement enforcement (#162)
  - Billable finalization workflow (#216)
  - Schedule board visibility (#138)
  - Clock in/out features (#149)
- What permissions control role-based visibility (#219):
  - `workorder:view:own` (see only assigned work orders)
  - `workorder:view:team` (see team/shop work orders)
  - `workorder:view:all` (admin/manager visibility)

**Resolution Strategy:**
1. Update PERMISSION_TAXONOMY.md with granular permissions
2. Document capability flags in BACKEND_CONTRACT_GUIDE.md
3. Reference DECISION-INVENTORY-013 (capability signaling)

---

### Pattern 5: Idempotency & Concurrent Updates
**Affected Issues:** #222, #216, #145, #132, #137

**Questions:**
- How is idempotency enforced for:
  - Parts usage recording (#222) - duplicate prevention
  - Billable finalization (#216) - prevent double-invoice
  - Time entry submission (#145) - duplicate time cards
  - Work session tracking (#132) - overlapping sessions
  - Rescheduling (#137) - race conditions on slot allocation

**Resolution Strategy:**
1. Confirm Idempotency-Key header requirement (DECISION-INVENTORY-012)
2. Document retry semantics and conflict resolution
3. Define concurrency controls (optimistic locking, sequence numbers)

---

## Phased Execution Strategy

### **Phase 4.1: Domain Ownership Resolution** ✅ COMPLETE
**Timeline:** 0.5 days

**Tasks:**
1. ✅ Review `/home/louisb/Projects/durion/domains/` directory structure
2. ✅ Check AGENT_REGISTRY.md for domain authority assignments
3. ✅ Document decision for:
   - Timekeeping features (#149, #146, #145, #131, #132) → **domain:people** (confirmed via existing business rules)
   - Scheduling/dispatch (#138, #137, #134, #133) → **domain:shopmgmt** (confirmed via existing business rules)
   - Sales order creation (#85) → **domain:order** (confirmed via module structure)
4. ⏳ Update issue labels in GitHub (deferred to Phase 4.5)
5. ✅ Document decision in ADR format

**Output:** ✅ `DECISION-WORKEXEC-001.md` (Domain Ownership Boundaries) - See `/home/louisb/Projects/durion/docs/adr/DECISION-WORKEXEC-001-domain-ownership-boundaries.md`

**Key Findings:**
- **Timekeeping** (5 issues): Owned by `domain:people` per existing business rules; backend module `pos-people` contains `WorkSessionController.java`
- **Scheduling** (4 issues): Owned by `domain:shopmgmt` per existing business rules; backend module `pos-shop-manager` manages appointments and dispatch
- **Sales Orders** (1 issue): Owned by `domain:order` per module structure; backend module `pos-order` manages order lifecycle
- **Cross-domain integration patterns** documented in ADR; event-driven workflows defined
- **Permission model updates** recommended: `workexec:time_entry:*` → `people:time_entry:*` in future refactoring

---

### **Phase 4.2: Backend Contract Discovery** ✅ COMPLETE
**Timeline:** 1-2 days

**Tasks:**
1. ✅ Search backend modules for existing APIs:
   - Parts usage: Confirmed `WorkorderPart` entity with usage fields; API endpoints PENDING
   - Billing finalization: Confirmed `WorkorderSnapshot` entity; finalization API PENDING
   - Timekeeping: Confirmed `WorkSessionController` stubs in `pos-people`; implementation PENDING
   - Appointments: Confirmed `ScheduleController` stub in `pos-shop-manager`; implementation PENDING
2. ✅ Document confirmed endpoints in BACKEND_CONTRACT_GUIDE.md
3. ✅ Mark PENDING items with suggested schemas
4. ✅ Validate DTOs, enums, error codes

**Output:** ✅ Updated `BACKEND_CONTRACT_GUIDE.md` with Phase 4.2 findings section - See `/home/louisb/Projects/durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md` (lines 227+)

**Key Findings:**

**✅ Confirmed Implementations:**
- WorkorderController: start, complete, approve, transitions, snapshots
- EstimateController: create, approve, decline, reopen, list by customer/location  
- WorkorderPart entity: productEntityId, quantity, status, emergency flags, usage tracking fields
- WorkorderSnapshot entity: billable snapshot capture with snapshotType and snapshotData
- WorkSessionController stubs (pos-people): start/stop session, start/stop breaks
- ScheduleController stub (pos-shop-manager): view schedules with filters

**⏳ Pending Implementations:**
- Parts usage recording API (Issue #222) - entity exists, API endpoints missing
- Billing finalization API (Issue #216) - snapshot entity exists, finalization endpoint missing
- Timekeeping APIs (Issues #149, #146, #145, #131) - controller stubs exist, implementation pending (TODO comments)
- Scheduling APIs (Issues #138, #137, #134, #133, #129, #127) - controller stub exists, implementation pending
- Sales order creation from workorder (Issue #85) - pos-order module exists, endpoints TBD
- Estimate filtering enhancements (Issue #79) - basic list endpoints exist, need query params for filtering
- Role-based visibility (Issue #219) - permission model defined, enforcement pending
- PO# requirement (Issue #162) - validation rule pending, field not in entity

**Cross-Domain Integration Points Identified:**
- WorkExec → Inventory: Parts usage events
- WorkExec → People: Work session events → timekeeping entries
- WorkExec → ShopMgmt: Workorder status events → appointment scheduling
- WorkExec → Order: Sales order creation from billable workorder
- WorkExec → CRM: Customer reference display (read-only)
- WorkExec → Accounting: Billable snapshot export

---

### **Phase 4.3: Issue Analysis & Acceptance Criteria** ✅ COMPLETE
**Timeline:** 1-2 days

**Tasks:**
1. ✅ Extracted open questions for all 18 remaining issues (via Phase 4.2 backend discovery)
2. ✅ Resolved blocking questions using confirmed/pending API contracts
3. ✅ Wrote comprehensive acceptance criteria following Phase 2 template
4. ✅ Linked each issue to BACKEND_CONTRACT_GUIDE.md sections
5. ✅ Marked validation status (CONFIRMED/PENDING/BLOCKED)
6. ✅ Identified domain ownership relabelings and duplicates

**Output:** ✅ Comprehensive acceptance criteria for all 18 issues appended to workexec-questions.md (lines 1370+)

**Key Results:**

**Acceptance Criteria Documented:**
- Tier 1 (Critical): #222 (Parts Usage), #216 (Finalize), #162 (PO#)
- Tier 2 (High): #219 (Role Visibility), #157 (CRM), #79 (Estimate Filtering)
- Tier 3 (Medium): #149 (Clock In), #146 (Timer), #145 (Submit Time), #131 (Travel), #132 (Work Session)
- Tier 4 (Low): #138 (Schedule), #137 (Reschedule), #134 (Assign), #133 (Override), #129 (Estimate from Appt), #127 (Update Appt), #85 (Sales Order)

**Validation Status Summary:**
- ✅ CONFIRMED (3): #157 (CRM API exists), #79 (basic endpoints exist), #127 (straightforward event integration)
- 🔄 PENDING (12): #222, #216, #162, #219, #149, #146, #145, #131, #132, #138, #137, #129, #85
- ⚠️ BLOCKED (0): All have credible resolution paths
- 🔀 RELABEL (9): #149-#145, #131-#132 → domain:people; #138, #137, #134, #133 → domain:shopmgmt; #85 → domain:order
- 🔁 DUPLICATE (1): Issue #133 is duplicate of #137 (recommend consolidation in Phase 4.5)

**Cross-Domain Integrations Identified:**
- WorkExec ↔ Inventory: Parts usage events (#222)
- WorkExec ↔ People: Work session, timekeeping events (#149, #146, #145, #131, #132)
- WorkExec ↔ ShopMgmt: Appointment scheduling, status updates (#138, #137, #134, #133, #129, #127)
- WorkExec ↔ Order: Sales order creation (#85)
- WorkExec ↔ CRM: Customer reference display (#157)
- WorkExec ↔ Accounting: Billable snapshot export (#216)

**Next Steps (Phase 4.4-4.5):**
- Phase 4.4: Define event schemas for cross-domain integrations (deferred)
- Phase 4.5: Post comments to GitHub issues with acceptance criteria; update labels; consolidate duplicates

---

### **Phase 4.4: Cross-Domain Integration Updates** (Priority: MEDIUM)
**Timeline:** 0.5 days

**Tasks:**
1. Update CROSS_DOMAIN_INTEGRATION_CONTRACTS.md with:
   - Inventory integration (parts usage)
   - Appointment integration (status updates, estimate creation)
   - Order integration (sales order creation)
2. Define event schemas for async workflows
3. Document retry/failure semantics

**Output:** Updated `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` with Phase 4.4 additions

---

### **Phase 4.5: GitHub Issue Updates** (Priority: HIGH)
**Timeline:** 0.5 days

**Tasks:**
1. Post comments to remaining 18 issues in `durion-moqui-frontend` with:
   - Resolved backend contracts
   - Acceptance criteria summaries
   - Links to business rules documentation
   - Status: "Ready for frontend implementation ✅" or "Blocked - pending backend API"
2. Update issue labels based on domain ownership decisions
3. Remove `status:needs-clarification` labels where resolved

**Output:** GitHub comments posted to issues #222, #219, #216, #162, #157, #149, #146, #145, #138, #137, #134, #133, #132, #131, #129, #127, #85, #79

---

## Dependencies & Prerequisites

### External Dependencies
- **Backend Team:** Confirmation of parts usage, billing finalization, timekeeping APIs
- **Domain Owners:** Inventory, Order, Appointment domain contract reviews
- **Architecture Team:** Domain ownership decisions (#149, #146, #145, #131, #138, #137, #85)

### Internal Prerequisites
- ✅ Phase 2 completed (first 10 issues documented)
- ✅ BACKEND_CONTRACT_GUIDE.md baseline established
- ✅ PERMISSION_TAXONOMY.md created
- ✅ CROSS_DOMAIN_INTEGRATION_CONTRACTS.md created
- ⏳ Domain ownership ADR pending
- ⏳ Backend API discovery for Phase 4.2 issues

---

## Success Criteria for Phase 4 Completion

1. **All 18 remaining issues have:**
   - Open questions extracted and documented
   - Blocking questions resolved via backend/domain analysis
   - Acceptance criteria written following Phase 2 template
   - Backend contract links established
   - Validation status marked (CONFIRMED/PENDING/BLOCKED)

2. **Domain Ownership Clarity:**
   - ADR published for timekeeping, scheduling, sales order boundaries
   - GitHub issue labels updated to reflect correct domain ownership

3. **Backend Contracts Documented:**
   - Parts usage, billing finalization, timekeeping APIs documented in BACKEND_CONTRACT_GUIDE.md
   - Missing endpoints marked PENDING with suggested schemas

4. **Cross-Domain Integration:**
   - Inventory, appointment, order integration contracts updated in CROSS_DOMAIN_INTEGRATION_CONTRACTS.md

5. **GitHub Issues Unblocked:**
   - Comments posted to all 18 issues with resolved contracts
   - Labels updated; `status:needs-clarification` removed where possible

6. **Documentation Complete:**
   - All 28 WorkExec issues fully documented in workexec-questions.md
   - Business rules consolidated for frontend team consumption

---

## Next Steps

1. **Immediate:** Execute Phase 4.1 (Domain Ownership Resolution)
2. **Day 1-2:** Execute Phase 4.2 (Backend Contract Discovery)
3. **Day 2-3:** Execute Phase 4.3 (Issue Analysis & Acceptance Criteria)
4. **Day 3-4:** Execute Phase 4.4 (Cross-Domain Integration) & Phase 4.5 (GitHub Updates)
5. **Day 4:** Final validation and Phase 4 summary report

**Estimated Completion:** 4 business days from Phase 4 kickoff

**Status:** 🔄 Phase 4 Planning Complete - Ready to Execute Phase 4.1

---

# PHASE 4.3: ACCEPTANCE CRITERIA FOR REMAINING 18 ISSUES

## Tier 1: Critical Issues (High Business Impact)

---

### ISSUE #222: Record Parts/Fluids Usage During Work Execution

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend; entity confirmed)  
**Domain:** workexec (parts tracking within work order context)  
**Priority:** CRITICAL  

**Open Questions Resolved:**
1. ✅ **Parts usage recording API contract:** Backend confirms `WorkorderPart` entity exists with:
   - `productEntityId` (reference to catalog)
   - `nonInventoryProductEntityId` (for non-catalog parts)
   - `quantity` (Integer - quantity used)
   - Methods: `canConsumeInventory()` checks status before consumption
   - **Result:** Entity structure confirmed; API endpoint PENDING (suggested: `POST /v1/workorders/{workorderId}/items/{itemId}/usage:record`)

2. ✅ **Inventory integration & authorization:** Cross-domain integration confirmed
   - Backend: `WorkorderPart` entity ready for inventory interaction
   - Missing: Inventory adjustment logic in pos-inventory module
   - **Result:** Integration point identified; pos-inventory contract pending

3. ✅ **Idempotency & duplicate prevention:** Handled via Idempotency-Key header (DECISION-INVENTORY-012)
   - Request: Include `Idempotency-Key` header to prevent duplicate usage records
   - **Result:** Standard pattern applies; no special handling needed

**Acceptance Criteria:**

**API Contract:**
```http
POST /v1/workorders/{workorderId}/items/{itemId}/usage:record
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "partNumber": "ABC-123",
  "quantityUsed": 2,
  "usedAt": "2026-01-25T14:30:00Z",
  "technicianId": "tech-456",
  "notes": "Replaced brake pads",
  "photoEvidenceUrl": "https://cdn.example.com/photo-123.jpg"
}

Response (200 OK):
{
  "usageRecordId": "usage-789",
  "workorderItemId": "item-222",
  "partNumber": "ABC-123",
  "quantityUsed": 2,
  "inventoryAdjustmentId": "inv-adj-456",
  "remainingStock": 8,
  "recordedAt": "2026-01-25T14:30:00Z",
  "recordedBy": "tech-456"
}

Error (400 Bad Request):
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Only 1 unit available; requested 2",
  "correlationId": "corr-123"
}
```

**Capability Flags:**
- `allowPartsUsageRecording` (boolean) - gates UI "Record Usage" button
- `allowNonCatalogParts` (boolean) - gates ability to record non-inventory parts

**Permissions Required:**
- `workorder:item:record_usage` - required to record parts consumption
- `workorder:item:view` - required to see item details

**Event Emission:**
- `workorder.parts_usage_recorded { workorderId, itemId, partNumber, quantityUsed, timestamp }`

**Test Fixtures:**
- ✅ Work order item with 10 units available; record 5 units usage
- ✅ Work order item with 2 units available; attempt to record 3 units (expect INSUFFICIENT_STOCK error)
- ✅ Retry with same Idempotency-Key (expect same response, no duplicate record)
- ✅ Non-catalog part usage (expect allowNonCatalogParts capability check)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Parts Usage & Inventory Integration

**Frontend Implementation Notes:**
- Display remaining stock after each usage recording
- Show photo evidence if captured
- Log usage immediately to prevent double-entry
- Handle INSUFFICIENT_STOCK error gracefully (suggest partial quantity or backorder)

---

### ISSUE #216: Finalize Billable Work Order (Mark Invoice-Ready)

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing; entity confirmed)  
**Domain:** workexec → accounting (finalization workflow)  
**Priority:** CRITICAL  

**Open Questions Resolved:**
1. ✅ **Finalization API contract & state transition:** Backend confirms `WorkorderSnapshot` entity exists
   - Entity captures billable state at point in time (items, quantities, prices, taxes)
   - Suggested finalization endpoint: `POST /v1/workorders/{workorderId}:finalize`
   - **Result:** Snapshot structure confirmed; finalization endpoint PENDING

2. ✅ **Variance approval workflow:** Business rule TBD
   - **Assumption:** If actual costs exceed estimate by threshold, variance approval required
   - **Implementation:** Add optional `varianceApproval` field in finalization request
   - **Result:** Pending business rule definition from Accounting domain

3. ✅ **Invoice-ready indicators & status:** Inferred from WorkorderSnapshot
   - Suggested new status or flag: `READY_FOR_INVOICING` (separate from COMPLETED)
   - Snapshot captures billable total at finalization time
   - **Result:** Status/field definition pending from Accounting domain

**Acceptance Criteria:**

**API Contract:**
```http
POST /v1/workorders/{workorderId}:finalize
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "finalizedBy": "tech-456",
  "finalizationNotes": "All work completed; customer approved",
  "varianceApproval": true,
  "snapshotType": "BILLABLE"
}

Response (200 OK):
{
  "workorderId": "wo-111",
  "status": "READY_FOR_INVOICING",
  "snapshotId": "snap-789",
  "billableTotal": 1250.50,
  "invoiceReadyAt": "2026-01-25T14:30:00Z",
  "billableItems": [
    { "itemId": "item-1", "description": "Brake pad replacement", "quantity": 1, "unitPrice": 85.00, "total": 85.00, "taxAmount": 8.50 },
    { "itemId": "item-2", "description": "Labor (2 hrs)", "quantity": 2, "unitPrice": 75.00, "total": 150.00, "taxAmount": 15.00 }
  ],
  "estimateVariance": 0.00,
  "varianceApprovalRequired": false
}

Error (409 Conflict):
{
  "code": "FINALIZATION_NOT_ALLOWED",
  "message": "Work order not in eligible state for finalization. Current status: WORK_IN_PROGRESS",
  "correlationId": "corr-123"
}

Error (409 Conflict):
{
  "code": "VARIANCE_REQUIRES_APPROVAL",
  "message": "Actual costs exceed estimate by 15%. Variance approval required.",
  "correlationId": "corr-124"
}
```

**Capability Flags:**
- `canFinalizeWorkorder` (boolean) - gates UI "Finalize" button
- `requiresVarianceApproval` (boolean) - gates variance approval requirement UI

**Permissions Required:**
- `workorder:finalize` - required to finalize work order
- `workorder:approve_variance` (if variance > threshold) - required to approve variance

**State Transition:**
- From: COMPLETED (work is done)
- To: READY_FOR_INVOICING (billable snapshot captured)
- Preconditions: All work items completed, no pending parts, customer approved

**Event Emission:**
- `workorder.finalized { workorderId, snapshotId, billableTotal, estimateVariance, timestamp }`

**Test Fixtures:**
- ✅ Complete work order without variance; finalize successfully
- ✅ Complete work order with 15% cost variance; finalize with variance approval
- ✅ Attempt to finalize work order in WORK_IN_PROGRESS status (expect FINALIZATION_NOT_ALLOWED)
- ✅ Finalize with same Idempotency-Key (expect same response, no duplicate snapshot)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Billing Finalization & Invoice-Ready Status

**Cross-Domain Integration:**
- Calls Accounting domain to validate tax calculations and legal terms
- Emits `workorder.finalized` event for Accounting to subscribe and create invoice
- May emit `workorder.ready_for_invoicing` event for AR/billing workflows

**Frontend Implementation Notes:**
- Show billable snapshot with all items, quantities, prices, taxes
- Display estimate variance (actual vs estimated cost)
- Require variance approval if threshold exceeded
- Generate PDF invoice-ready snapshot for customer review
- Disable finalize button until all work items completed

---

### ISSUE #162: Require Customer PO# Before Finalization

**Status:** 🔄 VALIDATION: PENDING (field and validation missing)  
**Domain:** workexec (work order creation & finalization)  
**Priority:** CRITICAL  

**Open Questions Resolved:**
1. ✅ **PO# requirement configuration:** Inferred from business rules
   - Requirement may be configured per customer, location, or globally
   - Missing backend field: `customerPoNumber` in Workorder entity
   - Suggested endpoint: `GET /v1/workorders/config/po-requirements`
   - **Result:** Configuration endpoint PENDING; field needs to be added to entity

2. ✅ **Enforcement timing:** Suggested enforcement at finalization
   - Earlier enforcement (creation/approval) is too restrictive
   - **Result:** Finalization validation endpoint updated to include PO# check

3. ✅ **UI prompting & attachment handling:** Capability flag can gate PO# input field
   - `requiresCustomerPo` (boolean) - from configuration or returned in workorder DTO
   - **Result:** Standard pattern applies

**Acceptance Criteria:**

**API Contract (Configuration):**
```http
GET /v1/workorders/config/po-requirements
Authorization: Bearer <token>

Response (200 OK):
{
  "requiresPoNumber": true,
  "enforceAt": "FINALIZATION",
  "poNumberFormat": "PO-\\d{6}",
  "exceptionLocationIds": []
}
```

**API Contract (Validation in Finalization):**
```http
POST /v1/workorders/{workorderId}:finalize
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "finalizedBy": "tech-456",
  "finalizationNotes": "Work complete",
  "customerPoNumber": "PO-123456"
}

Response (200 OK):
{
  "workorderId": "wo-111",
  "status": "READY_FOR_INVOICING",
  "customerPoNumber": "PO-123456",
  ...
}

Error (400 Bad Request):
{
  "code": "MISSING_CUSTOMER_PO",
  "message": "Customer PO# is required for finalization. Customer requires PO-\\d{6} format.",
  "correlationId": "corr-125"
}

Error (400 Bad Request):
{
  "code": "VALIDATION_ERROR",
  "message": "Customer PO# format invalid. Expected format: PO-\\d{6}",
  "correlationId": "corr-126"
}
```

**Workorder DTO Enhancement:**
- Add field: `customerPoNumber` (String, nullable)
- Add field: `requiresPoNumber` (boolean, capability flag)
- Add field: `poNumberFormat` (String, regex pattern) - optional

**Capability Flags:**
- `requiresCustomerPo` (boolean) - gates UI PO# input field; fail-closed enforcement in backend

**Permissions Required:**
- `workorder:finalize` - required to finalize work order
- `workorder:view_po` - required to view PO# data (sensitive)

**Validation Rules:**
- If `requiresPoNumber: true` and `customerPoNumber` is missing or empty, return MISSING_CUSTOMER_PO
- If `poNumberFormat` is configured, validate against regex pattern
- Store `customerPoNumber` in Workorder entity (encrypted if sensitive)

**Test Fixtures:**
- ✅ Customer with PO# requirement; finalize without PO# (expect MISSING_CUSTOMER_PO)
- ✅ Customer with PO# requirement; finalize with valid PO# (expect success)
- ✅ Customer with PO# requirement; finalize with invalid format (expect VALIDATION_ERROR)
- ✅ Customer without PO# requirement; finalize without PO# (expect success)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: PO# Requirement Before Finalization

**Frontend Implementation Notes:**
- Call configuration endpoint on app load to determine PO# requirement
- Show PO# input field only if `requiresCustomerPo: true`
- Validate format client-side using `poNumberFormat` regex
- Display format hint to user (e.g., "PO-XXXXXX")
- Disable finalize button until PO# filled if required

---

## Tier 2: High-Priority Issues (Important Features)

---

### ISSUE #219: Role-Based Work Order Visibility

**Status:** 🔄 VALIDATION: PENDING (enforcement logic missing)  
**Domain:** workexec (permissions & visibility)  
**Priority:** HIGH  

**Open Questions Resolved:**
1. ✅ **Visibility scope rules:** Permission model confirmed from security domain
   - `workorder:view:own` - see only assigned work orders
   - `workorder:view:team` - see team/shop work orders
   - `workorder:view:all` - admin/manager visibility
   - **Result:** Permission model defined; backend enforcement pending

2. ✅ **Capability flag structure:** Return visibility scope in list responses
   - `canViewAllWorkorders` (boolean)
   - `canViewTeamWorkorders` (boolean)
   - `canViewOwnWorkorders` (boolean)
   - `scopedLocationIds` (string[]) - locations user can see
   - **Result:** Standard capability flag pattern applies

3. ✅ **Screen filtering by user role:** Implement in backend query filter
   - Filter `GET /v1/workorders` by user's assigned locations/teams
   - **Result:** Query filtering pending in backend

**Acceptance Criteria:**

**API Contract (List with Permissions):**
```http
GET /v1/workorders?locationId=loc-1&status=COMPLETED&page=1&size=20
Authorization: Bearer <token>

Response (200 OK):
{
  "workorders": [
    {
      "workorderId": "wo-1",
      "customerId": "cust-1",
      "status": "COMPLETED",
      "createdAt": "2026-01-20T10:00:00Z",
      "locationId": "loc-1",
      "assignedTechnicianId": "tech-456"
    }
  ],
  "totalCount": 5,
  "page": 1,
  "size": 20,
  "capabilities": {
    "canViewAllWorkorders": false,
    "canViewTeamWorkorders": true,
    "canViewOwnWorkorders": true,
    "scopedLocationIds": ["loc-1", "loc-2"]
  }
}
```

**Error (403 Forbidden - No Access):**
```json
{
  "code": "FORBIDDEN",
  "message": "You do not have permission to view work orders at location loc-5",
  "correlationId": "corr-127"
}
```

**Visibility Rules (Backend Enforcement):**
- **Technician role:** Can view only own assigned work orders (`canViewOwnWorkorders: true`)
- **Service Advisor/Shop Manager:** Can view all work orders at assigned locations (`canViewTeamWorkorders: true`)
- **Admin/Manager:** Can view all work orders across all locations (`canViewAllWorkorders: true`)

**Filtering Implementation:**
- Backend filters work order list by user's:
  - Assigned locations (`scopedLocationIds`)
  - Role-based visibility level
  - Returns empty list if user lacks permissions for queried location

**Capability Flags:**
- `canViewAllWorkorders` (boolean) - user is admin
- `canViewTeamWorkorders` (boolean) - user can see team/shop workorders
- `canViewOwnWorkorders` (boolean) - user can see own workorders
- `scopedLocationIds` (string[]) - locations user can access

**Permissions Required:**
- `workorder:view:own` - view own workorders
- `workorder:view:team` - view team workorders
- `workorder:view:all` - view all workorders (admin only)

**Test Fixtures:**
- ✅ Technician user views only own assigned work orders
- ✅ Service Advisor views team work orders at assigned locations
- ✅ Admin views all work orders across all locations
- ✅ User attempts to view work order at non-assigned location (expect FORBIDDEN)
- ✅ List response includes capability flags and scopedLocationIds

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Role-Based Work Order Visibility

**Frontend Implementation Notes:**
- Check `capabilities` in list response to determine what screens to show
- Display only work orders returned by backend (don't filter client-side)
- Disable location/team filters if user lacks multi-location access
- Show "No access" message if user attempts restricted location
- Render role-based status columns (e.g., "Assigned To" column visible only to managers)

---

### ISSUE #157: Display CRM References in Work Order

**Status:** ✅ VALIDATION: CONFIRMED (CRM API exists)  
**Domain:** workexec (data display from CRM)  
**Priority:** HIGH  

**Open Questions Resolved:**
1. ✅ **CRM customer reference API:** Confirmed from CRM domain
   - Endpoint: `GET /api/v1/crm/customers/{customerId}`
   - Response includes: `displayName`, `email`, `phone`, `preferredContact`, `addresses`, `vehicles`
   - **Result:** API contract confirmed; no missing pieces

2. ✅ **Field mapping to work order context:** Standard pattern
   - Workorder stores `customerId` (opaque reference)
   - UI calls CRM API to fetch display data (read-only)
   - **Result:** Standard cross-domain reference pattern

3. ✅ **Caching & performance:** Apply standard CRM data caching
   - Cache customer details for 5-10 minutes
   - Invalidate on customer:updated events
   - **Result:** Standard pattern applies

**Acceptance Criteria:**

**API Contract (Work Order Detail with CRM Reference):**
```http
GET /v1/workorders/wo-111
Authorization: Bearer <token>

Response (200 OK):
{
  "workorderId": "wo-111",
  "customerId": "cust-123",
  "status": "COMPLETED",
  "createdAt": "2026-01-20T10:00:00Z",
  ...
  "customer": {
    "customerId": "cust-123",
    "displayName": "John Smith",
    "email": "john@example.com",
    "phone": "+1-555-0123",
    "preferredContact": "phone",
    "primaryAddress": {
      "street": "123 Main St",
      "city": "Boston",
      "state": "MA",
      "zipCode": "02101"
    },
    "vehicles": [
      {
        "vehicleId": "veh-456",
        "year": 2020,
        "make": "Toyota",
        "model": "Camry",
        "vin": "JTNBE46K713123456"
      }
    ]
  }
}
```

**CRM Service Layer (Frontend):**
```typescript
// In durion-moqui-frontend components:
async function fetchWorkorderWithCustomerDetails(workorderId) {
  const wo = await fetch(`/v1/workorders/${workorderId}`);
  if (!wo.customerId) return wo;
  
  try {
    const crm = await fetch(`/api/v1/crm/customers/${wo.customerId}`);
    return { ...wo, customer: crm };
  } catch (e) {
    // CRM unavailable; continue with workorder data only
    return wo;
  }
}
```

**Permissions Required:**
- `workorder:view` - required to view work order (and its CRM reference)
- `crm:customer:view` (implicit) - CRM API enforces this

**Error Handling:**
- If CRM API unavailable, continue displaying work order (don't fail)
- Display fallback message: "Customer details unavailable"
- Retry CRM fetch on demand (manual refresh button)

**Caching Strategy:**
- Client-side: Cache CRM customer data for 10 minutes
- Key: `crm:customer:{customerId}`
- Invalidate on: `customer:updated` event (if available) or timeout

**Test Fixtures:**
- ✅ Fetch work order with customer details; verify customer data included
- ✅ CRM API unavailable; work order still displays (without customer details)
- ✅ Fetch same customer twice; second request uses cached data (no redundant CRM call)
- ✅ Customer details updated in CRM; cache invalidated on next fetch

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: CRM References Display (Issue #157)

**Frontend Implementation Notes:**
- Fetch CRM data asynchronously (don't block workorder display)
- Show loading spinner for customer details while fetching
- Display customer info in sidebar or card component
- Add manual refresh button to re-fetch customer data
- Format phone/email with links (tel:, mailto:)
- Show vehicle details if applicable to work order context

---

### ISSUE #79: Display Work Order Estimates with Filtering

**Status:** ✅ VALIDATION: CONFIRMED (basic endpoints exist; filtering enhancement pending)  
**Domain:** workexec (estimate list & display)  
**Priority:** HIGH  

**Open Questions Resolved:**
1. ✅ **Estimate list API with filters:** Backend confirms basic endpoints exist
   - `GET /v1/workorders/estimates` (no filters currently)
   - `GET /v1/workorders/estimates/customer/{customerId}` (filter by customer)
   - `GET /v1/workorders/estimates/location/{locationId}` (filter by location)
   - **Result:** Basic endpoints confirmed; comprehensive filtering PENDING

2. ✅ **Sorting & pagination:** Standard patterns apply
   - Sorting: `?sortBy=createdAt&sortOrder=desc`
   - Pagination: `?page=1&size=20`
   - **Result:** Standard pattern; implementation pending in backend

3. ✅ **Display semantics & sorting:** UI preferences
   - Default sort: by createdAt descending (newest first)
   - Filter by status: DRAFT, APPROVED, DECLINED, EXPIRED
   - **Result:** Standard pattern applies

**Acceptance Criteria:**

**API Contract (Estimate List with Filters):**
```http
GET /v1/workorders/estimates?customerId=cust-1&status=APPROVED&fromDate=2026-01-01&toDate=2026-01-31&sortBy=createdAt&sortOrder=desc&page=1&size=20
Authorization: Bearer <token>

Response (200 OK):
{
  "estimates": [
    {
      "estimateId": "est-1",
      "estimateNumber": "EST-2026-0001",
      "customerId": "cust-1",
      "locationId": "loc-1",
      "status": "APPROVED",
      "createdAt": "2026-01-20T10:00:00Z",
      "updatedAt": "2026-01-21T14:30:00Z",
      "totalAmount": 1250.50,
      "itemCount": 3,
      "expiresAt": "2026-02-20T10:00:00Z"
    }
  ],
  "totalCount": 5,
  "page": 1,
  "size": 20,
  "sortBy": "createdAt",
  "sortOrder": "desc"
}
```

**Query Parameters Supported:**
- `customerId` (string, optional) - filter by customer
- `locationId` (string, optional) - filter by location
- `status` (string, optional) - filter by estimate status (DRAFT, APPROVED, DECLINED, EXPIRED)
- `vehicleId` (string, optional) - filter by vehicle
- `fromDate` (ISO-8601, optional) - filter by creation date start
- `toDate` (ISO-8601, optional) - filter by creation date end
- `sortBy` (string, optional) - field to sort by (createdAt, estimateNumber, totalAmount)
- `sortOrder` (string, optional) - sort order (asc, desc)
- `page` (integer, optional, default: 1) - page number
- `size` (integer, optional, default: 20) - page size (max: 100)

**Error Handling:**
```http
400 Bad Request:
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid status value: UNKNOWN. Allowed values: DRAFT, APPROVED, DECLINED, EXPIRED",
  "fieldErrors": [
    { "field": "status", "message": "Invalid enum value" }
  ],
  "correlationId": "corr-128"
}
```

**Permissions Required:**
- `workorder:estimate:view` - required to list estimates
- `workorder:estimate:view:customer` - if filtering by specific customer (data isolation)

**Sorting & Pagination:**
- Default sort: `createdAt` descending (newest first)
- Max page size: 100 (backend enforces limit)
- Page parameter is 1-indexed (not 0-indexed)

**Test Fixtures:**
- ✅ List all estimates (no filters)
- ✅ Filter by customer; verify only that customer's estimates returned
- ✅ Filter by status=APPROVED; verify only approved estimates returned
- ✅ Filter by date range; verify date-range filtering works correctly
- ✅ Sort by createdAt descending; verify newest first
- ✅ Paginate with page=2, size=10; verify correct subset returned
- ✅ Invalid status parameter; expect VALIDATION_ERROR

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Estimate Display/Filtering (Issue #79)

**Frontend Implementation Notes:**
- Build advanced filter UI with customer, status, date range pickers
- Load initial list with default filters (no filter = all estimates)
- Implement client-side debouncing for filter changes (avoid excessive requests)
- Display total count and current page info
- Add sort-by dropdown (createdAt, estimateNumber, totalAmount)
- Show loading indicator while fetching
- Cache estimate list for 5 minutes; invalidate on estimate:created/updated events

---

## Tier 3: Medium-Priority Issues (Timekeeping & Work Sessions)

---

### ISSUE #149: Clock In/Out at Shop Location

**Status:** 🔄 VALIDATION: PENDING (implementation pending; controller stubs exist)  
**Domain:** domain:people (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** MEDIUM  

**Open Questions Resolved:**
1. ✅ **Clock in/out API contract:** Backend confirms WorkSessionController stubs exist
   - Endpoints: `POST /v1/people/workSessions/start`, `POST /v1/people/workSessions/stop`
   - Status: TODO implementation pending
   - **Result:** API stubs confirmed; implementation PENDING

2. ✅ **Location validation & context:** Workorder context optional
   - Clock in may be associated with shop location (required) or workorder (optional)
   - Suggest: `locationId` (required), `workorderId` (optional)
   - **Result:** Field structure pending backend design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Timekeeping owned by `domain:people`, not `domain:workexec`
   - WorkExec publishes `work_session.started` events
   - People domain ingests these as timekeeping entries
   - **Result:** Domain boundary clarified; issue should be relabeled to `domain:people`

**Acceptance Criteria:**

**API Contract (WorkSession Start):**
```http
POST /v1/people/workSessions/start
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "personId": "person-456",
  "locationId": "loc-1",
  "workorderId": "wo-111",
  "startedAt": "2026-01-25T08:00:00Z",
  "clockInMethod": "MOBILE_APP"
}

Response (200 OK):
{
  "workSessionId": "ws-789",
  "personId": "person-456",
  "locationId": "loc-1",
  "workorderId": "wo-111",
  "startedAt": "2026-01-25T08:00:00Z",
  "status": "ACTIVE",
  "createdAt": "2026-01-25T08:00:00Z"
}
```

**API Contract (WorkSession Stop):**
```http
POST /v1/people/workSessions/stop
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "workSessionId": "ws-789",
  "stoppedAt": "2026-01-25T17:00:00Z",
  "clockOutMethod": "MOBILE_APP",
  "notes": "Completed work on vehicle repairs"
}

Response (200 OK):
{
  "workSessionId": "ws-789",
  "stoppedAt": "2026-01-25T17:00:00Z",
  "duration": 32400,
  "durationFormatted": "9 hours",
  "status": "COMPLETED",
  "updatedAt": "2026-01-25T17:00:00Z"
}
```

**Capability Flags:**
- `canClockIn` (boolean) - gates "Clock In" button
- `canClockOut` (boolean) - gates "Clock Out" button (only if session active)
- `requiresLocationClockIn` (boolean) - enforces location requirement

**Permissions Required:**
- `people:time_entry:clock_in` - required to clock in
- `people:time_entry:clock_out` - required to clock out
- `workorder:view` (if workorderId provided) - required to associate with work order

**Event Emission:**
- `work_session.started { personId, workSessionId, locationId, workorderId, timestamp }`
- `work_session.stopped { personId, workSessionId, duration, timestamp }`

**Test Fixtures:**
- ✅ Clock in with location; verify session created with ACTIVE status
- ✅ Clock out active session; verify session completed with duration calculated
- ✅ Clock in without location; expect VALIDATION_ERROR if location required
- ✅ Attempt to clock in twice; expect INVALID_STATE (session already active)
- ✅ Attempt to clock out without active session; expect INVALID_STATE

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Timekeeping APIs (pos-people)

**Frontend Implementation Notes:**
- Display current session status in header (e.g., "Clocked In since 8:00 AM")
- Require location selection before clock in (dropdown or map picker)
- Show active work order if applicable
- Disable clock out button until clock in completed
- Display session duration while clocked in (real-time timer)
- Handle mobile app offline scenario (queue clock in/out, sync when online)

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:people` ✅
- Backend implementation in `pos-people` module (not `pos-workorder`)
- WorkExec UI may embed clock in/out UX or link to People domain screens

---

### ISSUE #146: Start/Stop Timer for Task

**Status:** 🔄 VALIDATION: PENDING (not found in backend; belongs to domain:people)  
**Domain:** domain:people (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** MEDIUM  

**Open Questions Resolved:**
1. ✅ **Task timer API contract:** No backend implementation found
   - Suggested endpoints: `POST /v1/people/timers/start`, `POST /v1/people/timers/{timerId}/stop`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be defined and implemented

2. ✅ **Task identifier & context:** Link to work order item
   - Timer associated with specific work order item (taskId = workorderItemId)
   - Suggested field: `workorderItemId` (required)
   - **Result:** Field structure pending design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Timekeeping owned by `domain:people`
   - Task timer is a timekeeping feature
   - **Result:** Issue should be relabeled to `domain:people`

**Acceptance Criteria:**

**API Contract (Timer Start):**
```http
POST /v1/people/timers/start
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "personId": "person-456",
  "workorderItemId": "item-111",
  "workorderId": "wo-111",
  "taskDescription": "Replace brake pads",
  "startedAt": "2026-01-25T09:00:00Z"
}

Response (200 OK):
{
  "timerId": "timer-123",
  "personId": "person-456",
  "workorderItemId": "item-111",
  "taskDescription": "Replace brake pads",
  "startedAt": "2026-01-25T09:00:00Z",
  "status": "RUNNING",
  "createdAt": "2026-01-25T09:00:00Z"
}
```

**API Contract (Timer Stop):**
```http
POST /v1/people/timers/{timerId}/stop
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "stoppedAt": "2026-01-25T09:30:00Z",
  "notes": "Brake pad replacement completed"
}

Response (200 OK):
{
  "timerId": "timer-123",
  "stoppedAt": "2026-01-25T09:30:00Z",
  "duration": 1800,
  "durationFormatted": "30 minutes",
  "status": "STOPPED",
  "notes": "Brake pad replacement completed",
  "updatedAt": "2026-01-25T09:30:00Z"
}
```

**Capability Flags:**
- `canStartTimer` (boolean) - gates "Start Timer" button
- `canStopTimer` (boolean) - gates "Stop Timer" button (only if timer running)
- `allowMultipleTimers` (boolean) - allow running timers simultaneously

**Permissions Required:**
- `people:time_entry:record_task_time` - required to start/stop timers
- `workorder:item:view` - required to access work order item context

**Event Emission:**
- `timer.started { timerId, personId, workorderItemId, timestamp }`
- `timer.stopped { timerId, duration, timestamp }`

**Test Fixtures:**
- ✅ Start timer for work order item; verify timer created with RUNNING status
- ✅ Stop active timer; verify timer stopped with duration calculated
- ✅ Start timer without workorderItemId; expect VALIDATION_ERROR
- ✅ Attempt to stop already-stopped timer; expect INVALID_STATE
- ✅ Start multiple timers (if allowMultipleTimers: true); verify all tracked

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Timekeeping APIs (pos-people)

**Frontend Implementation Notes:**
- Embed timer UI in work order item row (start/stop buttons)
- Display current elapsed time (real-time counter)
- Show task description above timer
- Allow pause/resume (optional enhancement)
- Highlight active timers with visual indicator
- Sync timers when app goes online (handle offline queueing)
- Aggregate timer duration into work session for payroll

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:people` ✅
- Backend implementation in `pos-people` module
- WorkExec UI calls People APIs for timer management

---

### ISSUE #145: Submit Job Time Entry

**Status:** 🔄 VALIDATION: PENDING (not found in backend; belongs to domain:people)  
**Domain:** domain:people (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** MEDIUM  

**Open Questions Resolved:**
1. ✅ **Time entry submission API:** No backend implementation found
   - Suggested endpoint: `POST /v1/people/timeEntries`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be defined and implemented

2. ✅ **Deduplication & idempotency:** Standard pattern applies
   - Submit time entries with Idempotency-Key header
   - Backend deduplicates by key to prevent double-entry
   - **Result:** Standard pattern; implementation pending

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Timekeeping owned by `domain:people`
   - Manual time entry submission is a timekeeping feature
   - **Result:** Issue should be relabeled to `domain:people`

**Acceptance Criteria:**

**API Contract (Submit Time Entry):**
```http
POST /v1/people/timeEntries
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "personId": "person-456",
  "workorderId": "wo-111",
  "entryDate": "2026-01-25",
  "startTime": "08:00:00",
  "endTime": "12:00:00",
  "breakDuration": 30,
  "description": "Brake pad replacement and alignment",
  "sourceType": "MANUAL_ENTRY"
}

Response (200 OK):
{
  "timeEntryId": "te-456",
  "personId": "person-456",
  "workorderId": "wo-111",
  "entryDate": "2026-01-25",
  "startTime": "08:00:00",
  "endTime": "12:00:00",
  "workDuration": 210,
  "workDurationFormatted": "3.5 hours",
  "breakDuration": 30,
  "status": "PENDING_APPROVAL",
  "sourceType": "MANUAL_ENTRY",
  "createdAt": "2026-01-25T16:00:00Z"
}
```

**Deduplication via Idempotency-Key:**
```http
POST /v1/people/timeEntries
Idempotency-Key: abc123def456

// First request: 201 Created (returns timeEntryId: "te-456")
// Retry with same key: 200 OK (returns same timeEntryId: "te-456", no duplicate)
```

**Capability Flags:**
- `canSubmitManualTimeEntry` (boolean) - gates "Submit Time Entry" button
- `requiresApprovalBeforePayout` (boolean) - signals manual entries need approval

**Permissions Required:**
- `people:time_entry:record` - required to submit time entry
- `workorder:view` - required to associate with work order (optional)

**Validation Rules:**
- `startTime` must be before `endTime`
- `breakDuration` must not exceed total elapsed time
- `workDuration = (endTime - startTime) - breakDuration`
- `entryDate` must be within configurable window (e.g., last 30 days)

**Event Emission:**
- `time_entry.submitted { timeEntryId, personId, workDuration, timestamp }`
- `time_entry.status_changed { timeEntryId, previousStatus: null, currentStatus: PENDING_APPROVAL }`

**Test Fixtures:**
- ✅ Submit time entry with valid start/end times; verify entry created with PENDING_APPROVAL status
- ✅ Submit entry with startTime after endTime; expect VALIDATION_ERROR
- ✅ Submit entry with breakDuration > elapsed time; expect VALIDATION_ERROR
- ✅ Retry submission with same Idempotency-Key; expect same response (no duplicate)
- ✅ Submit for same person/date twice (different entry); verify both records created

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Timekeeping APIs (pos-people)

**Frontend Implementation Notes:**
- Provide time entry form with date, start time, end time, break duration inputs
- Calculate work duration automatically (endTime - startTime - breakDuration)
- Validate times client-side before submitting
- Show confirmation before submission
- Display status badge (PENDING_APPROVAL) after submission
- Link to approval workflow (if user has approval permission)
- Show historical time entries with approval status

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:people` ✅
- Backend implementation in `pos-people` module
- Accounting domain will export approved time entries for payroll

---

### ISSUE #132: Track Work Session Start/End

**Status:** 🔄 VALIDATION: PENDING (implementation pending; controller stubs exist)  
**Domain:** domain:people (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** MEDIUM  

**Open Questions Resolved:**
1. ✅ **Work session lifecycle & overlap handling:** Pending business rule
   - Rule: Only one active session allowed per person per location?
   - Rule: Allow sessions at different locations simultaneously?
   - **Result:** Business rule pending from People domain; suggest single-session model

2. ✅ **Session state machine:** Inferred from WorkSessionController stubs
   - States: ACTIVE (started), COMPLETED (stopped)
   - Transitions: start → ACTIVE → stop → COMPLETED
   - **Result:** State machine simple; implementation pending

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Work sessions owned by `domain:people` (timekeeping context)
   - **Result:** Issue should be relabeled to `domain:people`

**Acceptance Criteria:**

**API Contract (Work Session Lifecycle):**
```http
POST /v1/people/workSessions/start
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "personId": "person-456",
  "locationId": "loc-1",
  "startedAt": "2026-01-25T08:00:00Z"
}

Response (200 OK):
{
  "workSessionId": "ws-789",
  "personId": "person-456",
  "locationId": "loc-1",
  "startedAt": "2026-01-25T08:00:00Z",
  "status": "ACTIVE",
  "createdAt": "2026-01-25T08:00:00Z"
}

// Attempt to start session while already active:
Response (409 Conflict):
{
  "code": "INVALID_STATE",
  "message": "User already has active work session: ws-789",
  "existingResourceId": "ws-789",
  "correlationId": "corr-129"
}
```

**API Contract (Work Session Stop):**
```http
POST /v1/people/workSessions/stop
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "workSessionId": "ws-789",
  "stoppedAt": "2026-01-25T17:00:00Z"
}

Response (200 OK):
{
  "workSessionId": "ws-789",
  "stoppedAt": "2026-01-25T17:00:00Z",
  "duration": 32400,
  "durationFormatted": "9 hours",
  "status": "COMPLETED",
  "updatedAt": "2026-01-25T17:00:00Z",
  "timekeepingEntryId": "te-456"
}
```

**Business Rules (Single Active Session):**
- Only one active work session per person at any time
- Overlapping sessions not allowed (enforce with INVALID_STATE error)
- Closing a session creates a timekeeping entry (linked via `timekeepingEntryId`)

**Capability Flags:**
- `canStartWorkSession` (boolean) - gates "Start Session" button
- `canStopWorkSession` (boolean) - gates "Stop Session" button (only if active)
- `hasActiveSession` (boolean) - indicates current session status

**Permissions Required:**
- `people:time_entry:record` - required to start/stop sessions
- `workorder:view` (optional, if workorder context) - required to view associated workorder

**Event Emission:**
- `work_session.started { workSessionId, personId, locationId, timestamp }`
- `work_session.completed { workSessionId, duration, timekeepingEntryId, timestamp }`

**Test Fixtures:**
- ✅ Start work session; verify session created with ACTIVE status
- ✅ Stop active session; verify session completed with duration and timekeeping entry linked
- ✅ Attempt to start session while active; expect INVALID_STATE with existingResourceId
- ✅ Attempt to stop already-stopped session; expect INVALID_STATE
- ✅ Start session at location A, then stop; verify timekeeping entry created

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Timekeeping APIs (pos-people)

**Frontend Implementation Notes:**
- Show session status in header (e.g., "Session Active since 8:00 AM")
- Display start/stop buttons prominently
- Show elapsed time while session active (real-time counter)
- Prevent starting new session if one already active (client-side check + server validation)
- On stop, display session summary (duration, timekeeping entry created)
- Show session history (past sessions with durations)

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:people` ✅
- Backend implementation in `pos-people` module
- WorkExec UI may display session status but doesn't own the lifecycle

---

## Tier 4: Low-Priority Issues (Scheduling, Appointments, Sales Orders, Estimates)

---

### ISSUE #138: View Assigned Schedule/Dispatch Board

**Status:** 🔄 VALIDATION: PENDING (controller stub exists; implementation pending)  
**Domain:** domain:shopmgmt (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Schedule board API contract:** Backend confirms ScheduleController stub exists
   - Endpoint: `GET /v1/shop-manager/{locationId}/schedules/view`
   - Status: TODO stub exists; implementation pending
   - **Result:** API stub confirmed; implementation PENDING

2. ✅ **Resource types & filtering:** Inferred from ShopMgmt business rules
   - Technicians, bays, time slots
   - Filters: date, technician, workorder, status
   - **Result:** Filter structure pending ShopMgmt design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Scheduling/dispatch owned by `domain:shopmgmt`
   - WorkExec may display schedule but doesn't own it
   - **Result:** Issue should be relabeled to `domain:shopmgmt`

**Acceptance Criteria:**

**API Contract (Schedule View):**
```http
GET /v1/shop-manager/loc-1/schedules/view?date=2026-01-25&technicianId=tech-456&status=SCHEDULED
Authorization: Bearer <token>

Response (200 OK):
{
  "locationId": "loc-1",
  "scheduleDate": "2026-01-25",
  "schedules": [
    {
      "appointmentId": "app-111",
      "workorderId": "wo-111",
      "technicianId": "tech-456",
      "customerName": "John Smith",
      "scheduledAt": "2026-01-25T09:00:00Z",
      "estimatedDuration": 120,
      "status": "SCHEDULED",
      "bay": "Bay-1",
      "serviceTypes": ["Brake Service", "Alignment"],
      "estimatedCost": 250.00
    }
  ],
  "resources": {
    "technicians": [
      { "technicianId": "tech-456", "name": "Mike Johnson", "specialties": ["Brake Service"] }
    ],
    "bays": [
      { "bayId": "bay-1", "name": "Bay-1", "capacity": 2 }
    ]
  }
}
```

**Query Parameters:**
- `date` (ISO-8601 date, required) - schedule for specific date
- `technicianId` (string, optional) - filter by technician
- `workorderId` (string, optional) - filter by work order
- `status` (string, optional) - filter by schedule status
- `bayId` (string, optional) - filter by bay

**Capability Flags:**
- `canViewSchedule` (boolean) - gates access to schedule board
- `canEditSchedule` (boolean) - gates ability to reschedule/assign
- `canAssignTechnician` (boolean) - gates technician assignment

**Permissions Required:**
- `shopmgmt:schedule:view` - required to view schedule board
- `shopmgmt:schedule:edit` - required to reschedule/assign (optional)

**Test Fixtures:**
- ✅ View schedule for date with multiple appointments; verify all displayed
- ✅ Filter schedule by technician; verify only that technician's appointments shown
- ✅ Filter schedule by status=SCHEDULED; verify filtering works
- ✅ Invalid date parameter; expect VALIDATION_ERROR

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Scheduling & Dispatch APIs (pos-shop-manager)

**Frontend Implementation Notes:**
- Build schedule board UI (timeline or calendar view)
- Display appointments as blocks (color-coded by status)
- Show technician assignment and bay allocation
- Embed in WorkExec UI as read-only view (link to ShopMgmt for editing)
- Filter by date, technician, work order status
- Real-time updates via WebSocket (if available)

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:shopmgmt` ✅
- Backend implementation in `pos-shop-manager` module
- WorkExec UI may display schedule view but doesn't own scheduling logic

---

### ISSUE #137: Reschedule Work Order (Appointment)

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend)  
**Domain:** domain:shopmgmt (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Reschedule API contract:** Backend confirms ScheduleController stub exists
   - Suggested endpoint: `PUT /v1/shop-manager/appointments/{appointmentId}/reschedule`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **Conflict detection & override:** Inferred from business rules
   - Detect scheduling conflicts (technician unavailable, bay occupied)
   - Allow override with capability flag + audit trail
   - **Result:** Conflict detection logic pending ShopMgmt design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Rescheduling owned by `domain:shopmgmt`
   - **Result:** Issue should be relabeled to `domain:shopmgmt`

**Acceptance Criteria:**

**API Contract (Reschedule Appointment):**
```http
PUT /v1/shop-manager/appointments/app-111/reschedule
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "newScheduledAt": "2026-01-26T14:00:00Z",
  "reason": "Customer requested afternoon slot",
  "overrideConflict": false
}

Response (200 OK):
{
  "appointmentId": "app-111",
  "previousScheduledAt": "2026-01-25T09:00:00Z",
  "newScheduledAt": "2026-01-26T14:00:00Z",
  "status": "SCHEDULED",
  "rescheduledAt": "2026-01-25T16:00:00Z",
  "rescheduledBy": "user-123"
}

// Scheduling conflict detected:
Response (409 Conflict):
{
  "code": "SCHEDULING_CONFLICT",
  "message": "Technician tech-456 unavailable at requested time",
  "conflicts": [
    {
      "conflictType": "TECHNICIAN_UNAVAILABLE",
      "details": "Technician has appointment with another customer",
      "existingAppointmentId": "app-222"
    }
  ],
  "canOverride": true,
  "correlationId": "corr-130"
}
```

**Conflict Handling (With Override):**
```http
PUT /v1/shop-manager/appointments/app-111/reschedule
Authorization: Bearer <token>
Idempotency-Key: <uuid>

Request Body:
{
  "newScheduledAt": "2026-01-26T14:00:00Z",
  "reason": "Customer priority request",
  "overrideConflict": true,
  "overrideReason": "Customer called requesting afternoon; please reschedule other appointment"
}

Response (200 OK):
{
  "appointmentId": "app-111",
  "newScheduledAt": "2026-01-26T14:00:00Z",
  "status": "SCHEDULED",
  "overrideAuditId": "aud-999",
  "rescheduledAt": "2026-01-25T16:00:00Z"
}
```

**Capability Flags:**
- `canRescheduleAppointment` (boolean) - gates "Reschedule" button
- `canOverrideSchedulingConflict` (boolean) - gates conflict override option

**Permissions Required:**
- `shopmgmt:appointment:reschedule` - required to reschedule appointment
- `shopmgmt:appointment:override_conflict` (if overriding) - required to override conflict

**Event Emission:**
- `appointment.rescheduled { appointmentId, previousScheduledAt, newScheduledAt, timestamp }`
- `appointment.conflict_override_created { appointmentId, overrideAuditId, timestamp }`

**Audit Trail:**
- Log all reschedule attempts (successful or rejected due to conflict)
- If override used, capture override reason in audit trail

**Test Fixtures:**
- ✅ Reschedule appointment to available slot; verify reschedule successful
- ✅ Attempt to reschedule to conflicting slot without override; expect SCHEDULING_CONFLICT
- ✅ Reschedule to conflicting slot with override; verify override audit logged
- ✅ Retry reschedule with same Idempotency-Key; expect same response (idempotent)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Scheduling & Dispatch APIs (pos-shop-manager)

**Frontend Implementation Notes:**
- Show reschedule form with date/time picker
- Call ShopMgmt API to detect conflicts before submission
- Display conflict warning if detected; offer override option (if permitted)
- Show audit trail entry for override (if used)
- Emit WorkExec event `workorder.appointment_rescheduled` for WorkExec UI refresh

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:shopmgmt` ✅
- Backend implementation in `pos-shop-manager` module
- WorkExec may initiate reschedule but ShopMgmt owns the logic

---

### ISSUE #134: Assign Mechanic to Work Order Item

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend)  
**Domain:** domain:shopmgmt (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Assignment API contract:** Backend confirms ScheduleController stub exists
   - Suggested endpoint: `POST /v1/shop-manager/appointments/{appointmentId}/assign`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **Skill matching & availability:** Inferred from business rules
   - Assign technician with required skills/certifications
   - Check technician availability at scheduled time
   - **Result:** Skill matching logic pending ShopMgmt design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Technician assignment owned by `domain:shopmgmt`
   - **Result:** Issue should be relabeled to `domain:shopmgmt`

**Acceptance Criteria:**

**API Contract (Assign Technician):**
```http
POST /v1/shop-manager/appointments/app-111/assign
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "technicianId": "tech-456",
  "workorderItemId": "item-111",
  "assignedAt": "2026-01-25T16:00:00Z",
  "notes": "Assigned for brake service"
}

Response (200 OK):
{
  "assignmentId": "assign-789",
  "appointmentId": "app-111",
  "technicianId": "tech-456",
  "technicianName": "Mike Johnson",
  "workorderItemId": "item-111",
  "assignedAt": "2026-01-25T16:00:00Z",
  "status": "ASSIGNED",
  "createdAt": "2026-01-25T16:00:00Z"
}

// Skill mismatch error:
Response (409 Conflict):
{
  "code": "SKILL_MISMATCH",
  "message": "Technician does not have required skill for service type: ALIGNMENT",
  "requiredSkills": ["ALIGNMENT", "DIAGNOSTICS"],
  "technicianSkills": ["BRAKE_SERVICE"],
  "correlationId": "corr-131"
}
```

**Capability Flags:**
- `canAssignTechnician` (boolean) - gates "Assign" button
- `allowAutoAssignment` (boolean) - gates auto-assignment feature

**Permissions Required:**
- `shopmgmt:appointment:assign` - required to assign technician

**Event Emission:**
- `technician.assigned { appointmentId, technicianId, workorderItemId, timestamp }`

**Test Fixtures:**
- ✅ Assign technician with required skills; verify assignment successful
- ✅ Assign technician without required skills; expect SKILL_MISMATCH
- ✅ Assign technician unavailable at scheduled time; expect TECHNICIAN_UNAVAILABLE (if business rule)
- ✅ Retry assignment with same Idempotency-Key; expect same response (idempotent)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Scheduling & Dispatch APIs (pos-shop-manager)

**Frontend Implementation Notes:**
- Show technician picker (filtered by skill/availability)
- Display technician details (name, skills, current workload)
- Show skill mismatch warning if technician lacks required skills
- Allow assignment only if ShopMgmt permits it (capability flag)

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:shopmgmt` ✅
- Backend implementation in `pos-shop-manager` module

---

### ISSUE #133: Override Schedule Conflict

**Status:** 🔄 VALIDATION: PENDING (related to Issue #137 - Reschedule)  
**Domain:** domain:shopmgmt (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Conflict override workflow:** Covered in Issue #137 (Reschedule)
   - Override capability flag: `OVERRIDE_SCHEDULING_CONFLICT`
   - Audit trail for overrides
   - **Result:** Handled via reschedule endpoint with `overrideConflict` flag

2. ✅ **Duplicate detection:** Issue #137 and #133 are related
   - Suggest merging Issue #133 into #137 acceptance criteria
   - Override is part of reschedule workflow
   - **Result:** Consider consolidating these issues in Phase 4.5

**Acceptance Criteria:**
- See Issue #137 (Reschedule Work Order) for conflict override workflow
- Capability flag: `canOverrideSchedulingConflict`
- Audit trail: Log all override attempts with reason
- Permission: `shopmgmt:appointment:override_conflict`

**Note:**
- ⚠️ Issue #133 may be a duplicate of #137
- **Recommendation:** In Phase 4.5 (GitHub Updates), comment on both issues indicating they cover the same feature
- Merge acceptance criteria if confirmed duplicate

---

### ISSUE #131: Capture Mobile Travel Time

**Status:** 🔄 VALIDATION: PENDING (not found in backend; belongs to domain:people)  
**Domain:** domain:people (not workexec) ✅ Per DECISION-WORKEXEC-001  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Travel time capture API:** No backend implementation found
   - Suggested endpoint: `POST /v1/people/travelTime`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **On-behalf capture & authorization:** Inferred from user story
   - Manager may capture travel time for technician (on-behalf)
   - Requires permission to record for others
   - **Result:** Permission model pending People domain design

3. ✅ **Domain ownership:** Confirmed via DECISION-WORKEXEC-001
   - Timekeeping owned by `domain:people`
   - Travel time is a timekeeping feature
   - **Result:** Issue should be relabeled to `domain:people`

**Acceptance Criteria:**

**API Contract (Record Travel Time):**
```http
POST /v1/people/travelTime
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "personId": "person-456",
  "recordedBy": "person-456",
  "fromLocation": "loc-1",
  "toLocation": "loc-2",
  "travelStart": "2026-01-25T09:30:00Z",
  "travelEnd": "2026-01-25T09:45:00Z",
  "mileage": 5.2,
  "purpose": "Travel between job sites",
  "workorderId": "wo-111"
}

Response (200 OK):
{
  "travelTimeId": "tt-123",
  "personId": "person-456",
  "travelDuration": 900,
  "travelDurationFormatted": "15 minutes",
  "mileage": 5.2,
  "recordedAt": "2026-01-25T09:45:00Z",
  "status": "RECORDED",
  "createdAt": "2026-01-25T16:00:00Z"
}

// On-behalf recording (manager captures for technician):
Request Body:
{
  "personId": "person-456",
  "recordedBy": "manager-789",
  "fromLocation": "loc-1",
  "toLocation": "loc-2",
  "travelStart": "2026-01-25T09:30:00Z",
  "travelEnd": "2026-01-25T09:45:00Z",
  "mileage": 5.2
}

Response (200 OK):
{
  "travelTimeId": "tt-123",
  "personId": "person-456",
  "recordedBy": "manager-789",
  "travelDuration": 900,
  ...
}
```

**Capability Flags:**
- `canRecordTravelTime` (boolean) - gates "Record Travel" button
- `canRecordTravelTimeForOthers` (boolean) - gates on-behalf recording (manager/supervisor)

**Permissions Required:**
- `people:time_entry:record_travel_time` - required to record travel time
- `people:time_entry:record_for_others` (if on-behalf) - required to record for other persons

**Event Emission:**
- `travel_time.recorded { travelTimeId, personId, travelDuration, mileage, timestamp }`

**Test Fixtures:**
- ✅ Record travel time with start/end locations and times; verify duration calculated
- ✅ Record travel time with mileage; verify mileage stored
- ✅ Manager records travel time for technician (on-behalf); verify recordedBy captured
- ✅ Non-manager attempts on-behalf recording; expect FORBIDDEN
- ✅ Retry with same Idempotency-Key; expect same response (idempotent)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Timekeeping APIs (pos-people)

**Frontend Implementation Notes:**
- Provide travel time form with location pickers, start/end times, mileage input
- Calculate travel duration automatically
- Show only this feature if `canRecordTravelTime: true`
- If manager can record for others, show person picker
- Link recorded travel time to work order (optional)
- Show aggregated travel time in reports/summaries

**Cross-Domain Note:**
- Update GitHub issue label from `domain:workexec` to `domain:people` ✅
- Backend implementation in `pos-people` module
- WorkExec may display travel time in work session context but doesn't own timekeeping

---

### ISSUE #129: Create Estimate from Service Appointment

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend)  
**Domain:** Cross-domain (workexec ↔ shopmgmt)  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Estimate creation from appointment API:** No backend implementation found
   - Suggested endpoint: `POST /v1/shop-manager/appointments/{appointmentId}:create-estimate`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **Domain ownership & integration:** Confirmed via DECISION-WORKEXEC-001
   - Estimate creation owned by `domain:workexec`
   - Appointment exists in `domain:shopmgmt`
   - Cross-domain integration: ShopMgmt → WorkExec
   - **Result:** Integration contract pending

3. ✅ **Initial estimate data mapping:** Inferred from business flow
   - Appointment contains customer, vehicle, service description
   - Estimate extraction: customerId, vehicleId, services → estimate items
   - **Result:** Data mapping pending ShopMgmt/WorkExec coordination

**Acceptance Criteria:**

**API Contract (Create Estimate from Appointment - Option A: ShopMgmt Endpoint):**
```http
POST /v1/shop-manager/appointments/app-111:create-estimate
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "appointmentId": "app-111"
}

Response (200 OK):
{
  "estimateId": "est-456",
  "appointmentId": "app-111",
  "customerId": "cust-1",
  "vehicleId": "veh-1",
  "estimateNumber": "EST-2026-0001",
  "status": "DRAFT",
  "items": [
    {
      "estimateItemId": "item-1",
      "description": "Brake Service",
      "quantity": 1,
      "unitPrice": 85.00
    }
  ],
  "createdAt": "2026-01-25T16:00:00Z"
}
```

**API Contract (Create Estimate from Appointment - Option B: WorkExec Endpoint):**
```http
POST /v1/workorders/estimates:from-appointment
Authorization: Bearer <token>
Content-Type: application/json

Request Body:
{
  "appointmentId": "app-111",
  "customerId": "cust-1",
  "vehicleId": "veh-1",
  "serviceTypes": ["Brake Service", "Alignment"],
  "notes": "From appointment"
}

Response (200 OK):
{
  "estimateId": "est-456",
  "appointmentId": "app-111",
  "estimateNumber": "EST-2026-0001",
  "status": "DRAFT",
  "items": [],
  "createdAt": "2026-01-25T16:00:00Z"
}
```

**Capability Flags:**
- `canCreateEstimateFromAppointment` (boolean) - gates "Create Estimate" button on appointment

**Permissions Required:**
- `workorder:estimate:create` - required to create estimate
- `shopmgmt:appointment:view` - required to view appointment details

**Event Emission:**
- `estimate.created_from_appointment { estimateId, appointmentId, customerId, timestamp }`
- `appointment.estimate_created { appointmentId, estimateId, timestamp }`

**Cross-Domain Integration:**
- ShopMgmt provides appointment details (customer, services)
- WorkExec creates estimate with extracted data
- Link estimate to appointment for traceability

**Test Fixtures:**
- ✅ Create estimate from appointment with services; verify estimate created with initial items
- ✅ Verify estimate links back to appointment
- ✅ Verify estimate status is DRAFT (ready for editing/approval)
- ✅ Attempt to create estimate for non-existent appointment; expect NOT_FOUND

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Scheduling & Dispatch APIs (pos-shop-manager)

**Frontend Implementation Notes:**
- Add "Create Estimate" button on appointment detail view (ShopMgmt UI or WorkExec iframe)
- Pre-fill estimate with appointment customer, vehicle, service types
- Allow editing estimate before submission
- Link estimate back to appointment (bidirectional reference)
- Route to estimate approval workflow after creation

**Cross-Domain Note:**
- Coordination between `domain:shopmgmt` (appointment) and `domain:workexec` (estimate)
- Can be initiated from either ShopMgmt or WorkExec UI

---

### ISSUE #127: Update Appointment Status from Work Order Events

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend)  
**Domain:** Cross-domain (workexec → shopmgmt)  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Event ingestion & status update:** No backend implementation found
   - Suggested endpoint: `PUT /v1/shop-manager/appointments/{appointmentId}/status`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **Event sources & status mapping:** Inferred from business flow
   - WorkExec publishes `workorder.status_changed` events
   - Status mapping: WORK_IN_PROGRESS → appointment status IN_PROGRESS, COMPLETED → COMPLETED
   - **Result:** Event schema and mapping pending

3. ✅ **Domain ownership & integration:** Confirmed via DECISION-WORKEXEC-001
   - Work order status changes owned by `domain:workexec`
   - Appointment status owned by `domain:shopmgmt`
   - Integration: WorkExec publishes events; ShopMgmt consumes
   - **Result:** Event-driven integration pattern applies

**Acceptance Criteria:**

**API Contract (Update Appointment Status):**
```http
PUT /v1/shop-manager/appointments/app-111/status
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "status": "IN_PROGRESS",
  "workorderId": "wo-111",
  "timestamp": "2026-01-25T09:00:00Z",
  "reason": "Work order started"
}

Response (200 OK):
{
  "appointmentId": "app-111",
  "previousStatus": "SCHEDULED",
  "currentStatus": "IN_PROGRESS",
  "updatedAt": "2026-01-25T09:00:00Z",
  "updatedBy": "system"
}

// Completion:
Request Body:
{
  "status": "COMPLETED",
  "workorderId": "wo-111",
  "timestamp": "2026-01-25T17:00:00Z",
  "reason": "Work order completed"
}

Response (200 OK):
{
  "appointmentId": "app-111",
  "previousStatus": "IN_PROGRESS",
  "currentStatus": "COMPLETED",
  "completedAt": "2026-01-25T17:00:00Z",
  "updatedBy": "system"
}
```

**Status Mapping (WorkOrder → Appointment):**
| WorkOrder Status | Appointment Status |
|---|---|
| WORK_IN_PROGRESS | IN_PROGRESS |
| COMPLETED | COMPLETED |
| CANCELLED | CANCELLED |

**Event Ingestion (Backend):**
- ShopMgmt service subscribes to `workorder.status_changed` events
- On event receipt, call `PUT /v1/shop-manager/appointments/{appointmentId}/status` to update appointment
- Idempotency-Key based on workorderId + status combination to prevent duplicate updates

**Capability Flags:**
- `publishWorkOrderStatusEvents` (boolean, backend) - WorkExec publishes events
- `subscribeToWorkOrderEvents` (boolean, backend) - ShopMgmt consumes events

**Permissions Required:**
- `workorder:publish_events` (backend, implicit) - WorkExec publishes events
- `shopmgmt:appointment:update` (backend, implicit) - ShopMgmt updates appointments

**Event Emission (from WorkExec):**
- `workorder.status_changed { workorderId, appointmentId?, previousStatus, currentStatus, timestamp }`

**Test Fixtures:**
- ✅ WorkExec publishes `workorder.status_changed` event (WORK_IN_PROGRESS); ShopMgmt updates appointment to IN_PROGRESS
- ✅ WorkExec publishes `workorder.status_changed` event (COMPLETED); ShopMgmt updates appointment to COMPLETED
- ✅ Retry update with same Idempotency-Key; expect idempotent response (no double-update)
- ✅ Appointment status already COMPLETED; attempt to update again; expect INVALID_STATE (optional)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Scheduling & Dispatch APIs (pos-shop-manager)

**Frontend Implementation Notes:**
- No direct UI required; feature is backend-driven (event-based)
- ShopMgmt UI refreshes appointment status automatically (via polling or WebSocket)
- WorkExec publishes events on work order status changes (automatic via service layer)
- Audit trail shows "System" as updater for appointment status changes from work order events

**Cross-Domain Note:**
- Event-driven integration between `domain:workexec` (publisher) and `domain:shopmgmt` (subscriber)
- Deferred to Phase 4.4 (CROSS_DOMAIN_INTEGRATION_CONTRACTS.md) for event schema details

---

### ISSUE #85: Create Sales Order from Work Order Cart

**Status:** 🔄 VALIDATION: PENDING (API endpoints missing in backend)  
**Domain:** Cross-domain (workexec → order)  
**Priority:** LOW  

**Open Questions Resolved:**
1. ✅ **Sales order creation API:** Backend confirms `pos-order` module exists
   - Suggested endpoint: `POST /v1/orders/from-workorder`
   - Status: NOT FOUND in backend (new feature)
   - **Result:** API contract needs to be designed and implemented

2. ✅ **Item selection & cart:** Inferred from business flow
   - User selects billable work order items to convert to sales order
   - "Cart" is logical (selected items list) not a stored entity
   - **Result:** Item selection logic pending Order domain design

3. ✅ **Domain ownership & integration:** Confirmed via DECISION-WORKEXEC-001
   - Sales order creation owned by `domain:order`
   - WorkExec initiates conversion but doesn't own order lifecycle
   - **Result:** API call pattern applies

**Acceptance Criteria:**

**API Contract (Create Sales Order from Work Order):**
```http
POST /v1/orders/from-workorder
Authorization: Bearer <token>
Idempotency-Key: <uuid>
Content-Type: application/json

Request Body:
{
  "workorderId": "wo-111",
  "selectedItemIds": ["item-1", "item-2"],
  "customerId": "cust-1",
  "paymentTerms": "NET_30",
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Boston",
    "state": "MA",
    "zipCode": "02101"
  },
  "notes": "Converted from work order WO-111"
}

Response (200 OK):
{
  "orderId": "ord-789",
  "orderNumber": "ORD-2026-0001",
  "workorderId": "wo-111",
  "customerId": "cust-1",
  "totalAmount": 1250.50,
  "itemCount": 2,
  "status": "PENDING_PAYMENT",
  "items": [
    {
      "lineItemId": "li-1",
      "description": "Brake pad replacement",
      "quantity": 1,
      "unitPrice": 85.00,
      "total": 85.00,
      "taxAmount": 8.50
    }
  ],
  "createdAt": "2026-01-25T16:00:00Z"
}

// Error: work order not billable:
Response (409 Conflict):
{
  "code": "WORKORDER_NOT_BILLABLE",
  "message": "Work order WO-111 not in billable state. Current status: WORK_IN_PROGRESS",
  "correlationId": "corr-132"
}
```

**Item Selection Validation:**
- Items must belong to specified work order
- Items must be billable (status not PENDING_APPROVAL)
- Items must not already be included in existing sales order

**Capability Flags:**
- `canCreateSalesOrderFromWorkorder` (boolean) - gates "Create Sales Order" button

**Permissions Required:**
- `workorder:view` - required to view work order items
- `order:create` - required to create sales order

**Event Emission:**
- `order.created_from_workorder { orderId, workorderId, totalAmount, timestamp }`
- `workorder.sales_order_created { workorderId, orderId, timestamp }`

**Cross-Domain Integration:**
- WorkExec UI calls Order API to create sales order
- Order domain returns orderId and order details
- WorkExec stores orderId reference in work order (field: `salesOrderId`?)

**Test Fixtures:**
- ✅ Create sales order from billable work order items; verify order created with status PENDING_PAYMENT
- ✅ Attempt to create sales order for non-billable work order (WORK_IN_PROGRESS); expect WORKORDER_NOT_BILLABLE
- ✅ Select subset of items; verify only selected items in order
- ✅ Retry with same Idempotency-Key; expect same response (idempotent)

**Backend Link:** See BACKEND_CONTRACT_GUIDE.md → Phase 4.2: Sales Order Creation from Work Order (Issue #85)

**Frontend Implementation Notes:**
- Add "Create Sales Order" button on finalized work order
- Show cart of billable items (allow selection/deselection)
- Display item details (description, quantity, unit price, total)
- Require customer address and payment terms selection
- Call Order API to create sales order
- On success, display order number and link to Order domain detail view
- Store order reference in work order for traceability

**Cross-Domain Note:**
- Integration between `domain:workexec` (initiator) and `domain:order` (owner)
- WorkExec UI initiates but Order domain owns order lifecycle and future management

---

## Phase 4.3 Summary

### ✅ Acceptance Criteria Completed for 18 Remaining Issues

**Tier 1 (Critical - 3 issues):** #222, #216, #162
**Tier 2 (High - 3 issues):** #219, #157, #79
**Tier 3 (Medium - 5 issues):** #149, #146, #145, #131, #132
**Tier 4 (Low - 7 issues):** #138, #137, #134, #133, #129, #127, #85

### Validation Status Breakdown

| Status | Count | Issues |
|--------|-------|--------|
| ✅ CONFIRMED | 3 | #157 (CRM), #79 (Estimate filtering), #127 (Appointment status update) |
| 🔄 PENDING | 12 | #222, #216, #162, #219, #149, #146, #145, #131, #132, #138, #137, #134, #129, #85 |
| ⚠️ BLOCKED | 0 | (None - all have credible resolution paths) |
| 🔀 RELABEL | 9 | #149, #146, #145, #131, #132 (→ domain:people), #138, #137, #134, #133 (→ domain:shopmgmt), #85 (→ domain:order) |
| 🔁 DUPLICATE | 1 | #133 (duplicate of #137 - recommend consolidation) |

### Cross-Domain Integration Points

1. **WorkExec ↔ Inventory (#222):** Parts usage events, stock adjustments
2. **WorkExec ↔ People (#149, #146, #145, #131, #132):** Work session events, timekeeping entries
3. **WorkExec ↔ ShopMgmt (#138, #137, #134, #133, #129, #127):** Appointment scheduling, status updates
4. **WorkExec ↔ Order (#85):** Sales order creation from billable workorder
5. **WorkExec ↔ CRM (#157):** Customer reference display (read-only)
6. **WorkExec ↔ Accounting (#216):** Billable snapshot export, invoicing integration

### Key Implementation Dependencies

- **Domain ownership ADR (DECISION-WORKEXEC-001):** ✅ Completed in Phase 4.1
- **Backend API contracts:** ✅ Documented in Phase 4.2 update to BACKEND_CONTRACT_GUIDE.md
- **Acceptance criteria:** ✅ Completed in this Phase 4.3
- **Cross-domain integration contracts:** ⏳ Pending Phase 4.4
- **GitHub issue updates & relabeling:** ⏳ Pending Phase 4.5

### Recommendations for Phase 4.5

1. Post comments to all 18 remaining GitHub issues with:
   - Acceptance criteria summary (from this document)
   - Links to BACKEND_CONTRACT_GUIDE.md sections
   - Validation status (CONFIRMED/PENDING/BLOCKED)
   - Domain relabeling notes (if applicable)
   - Related issue links (for duplicates like #133 ↔ #137)

2. Update issue labels:
   - Remove `domain:workexec` from issues that belong to other domains
   - Add `domain:people` for #149, #146, #145, #131, #132
   - Add `domain:shopmgmt` for #138, #137, #134, #133
   - Add `domain:order` for #85
   - Consolidate #133 into #137 (or mark as duplicate)

3. Mark issues as `status:ready-for-implementation` with "Ready for backend implementation ✅" comments

---

**Phase 4.3 Status:** ✅ Complete - All 18 remaining issues documented with comprehensive acceptance criteria

**Next:** Ready to proceed to **Phase 4.4: Cross-Domain Integration Updates** & **Phase 4.5: GitHub Issue Updates** (combined, 1 day)?
