# WorkExec Backend Contract Guide

**Version:** 0.2 (Synced with pos-workorder OpenAPI v1)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-13

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for WorkExec REST APIs. It reuses patterns from accounting and CRM backend contract guides and aligns with DECISION-INVENTORY (auth fail-closed, opaque IDs, standard error envelope, idempotency, capability signaling).

**Primary Goal (Phase 1):** Document the authoritative contracts for the endpoints referenced in [workexec-questions.md](../workexec-questions.md) so blocking questions can be resolved in Phase 2.

---

## Known Limitations (v1 Implementation)

The following capabilities are **required by CAP-002** but **not yet implemented** in pos-workorder v1:

### Item Management
**Missing Endpoints:**
- `POST /v1/workorders/estimates/{estimateId}/items` - Add line item (part or labor)
- `PATCH /v1/workorders/estimates/{estimateId}/items/{itemId}` - Update line item
- `DELETE /v1/workorders/estimates/{estimateId}/items/{itemId}` - Remove line item

**Impact:** Cannot build estimates incrementally; must create with full item set upfront or use workarounds.
**Tracking:** Backend issues #173 (parts), #172 (labor), #170 (revision)

### Tax Calculation
**Missing Endpoint:**
- `POST /v1/workorders/estimates/{estimateId}/calculate` - Trigger tax recalculation

**Current Behavior:** Tax calculation happens automatically during approval.
**Tracking:** Backend issue #171

### Summary Generation
**Missing Endpoints:**
- `GET /v1/workorders/estimates/{estimateId}/summary` - Customer-facing formatted summary
- `POST /v1/workorders/estimates/{estimateId}/snapshots` - Create historical snapshot

**Impact:** Cannot generate printable/PDF estimates for customer presentation.
**Tracking:** Backend issue #169

### Approval Workflow
**Missing Endpoint:**
- `POST /v1/workorders/estimates/{estimateId}/submit` - Submit for approval (DRAFT → PENDING_APPROVAL)

**Current Behavior:** Estimates transition directly from DRAFT → APPROVED via approval endpoint.
**Impact:** No explicit "submit for review" step in workflow.


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

## Endpoint Inventory (Phase 2 Confirmed from pos-workorder Backend)

All endpoint examples in this guide use the API Gateway format:

- `http://api-gateway.local/workorder/v1/workorders/...`

**Note:** The API Gateway routes requests to `pos-workorder` service using the path `/workorder/**` with StripPrefix=1 filter. Incoming requests like `/workorder/v1/workorders/estimates` are forwarded to the service as `/v1/workorders/estimates`.

Mutations accept `Idempotency-Key` header. All request/response DTOs use standard error envelope on failure.

### Estimates (Confirmed APIs)

1. **Get All Estimates** — `GET http://api-gateway.local/workorder/v1/workorders/estimates`
   - Response: `[ EstimateDTO ]`
   - No pagination in source; returns all estimates

2. **Get Estimate by ID** — `GET http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}`
   - Path param: `estimateId` (Long, required)
   - Response: `EstimateDTO` (200) | 404 if not found

   #### EstimateResponse (Current v1 API)

   **Current Implementation Returns:**
   ```json
   {
     "id": "550e8400-e29b-41d4-a716-446655440000",
     "estimateNumber": "EST-2024-1000",
     "customerId": "550e8400-e29b-41d4-a716-446655440001",
     "vehicleId": "550e8400-e29b-41d4-a716-446655440002",
     "locationId": "550e8400-e29b-41d4-a716-446655440003",
     "currencyUomId": "USD",
     "taxRegionId": "550e8400-e29b-41d4-a716-446655440004",
     "status": "DRAFT",
     "createdByUserId": "550e8400-e29b-41d4-a716-446655440005",
     "createdAt": "2024-01-27T10:30:00Z"
   }
   ```

   **Field Definitions:**
   - `id` (String/UUID) - Unique estimate identifier
   - `estimateNumber` (String) - Human-readable estimate number (e.g., "EST-2024-1000")
   - `customerId` (String/UUID) - Customer party ID
   - `vehicleId` (String/UUID) - Vehicle/asset ID
   - `locationId` (String/UUID) - Shop/facility location ID
   - `currencyUomId` (String) - Currency code (e.g., "USD")
   - `taxRegionId` (String/UUID) - Tax jurisdiction ID
   - `status` (EstimateStatus) - Current lifecycle status
   - `createdByUserId` (String/UUID) - User who created the estimate
   - `createdAt` (ISO 8601 UTC) - Creation timestamp

   **Fields Planned for Future Releases:**
   - `updatedAt` (ISO 8601) - Last modification timestamp
   - `approvedAt` (ISO 8601) - Approval timestamp
   - `approvedBy` (UUID) - Approving user ID
   - `declinedAt` (ISO 8601) - Decline timestamp
   - `expiresAt` (ISO 8601) - Approval expiration timestamp
   - `subtotal` (BigDecimal) - Subtotal before tax
   - `taxAmount` (BigDecimal) - Calculated tax amount
   - `total` (BigDecimal) - Grand total including tax
   - `version` (Integer) - Optimistic locking version for concurrency control
   - `lineItems` (LineItemDTO[]) - Array of estimate line items (parts/labor)
   - `signatureData` (String) - Base64-encoded signature image (populated after approval)
   - `signatureMimeType` (String) - Signature MIME type (e.g., "image/png")
   - `signerName` (String) - Name of person who signed
   - `approvalNotes` (String) - Notes provided during approval

3. **Get Estimates by Customer** — `GET http://api-gateway.local/workorder/v1/workorders/estimates/customer/{customerId}`
   - Path param: `customerId` (Long, required)
   - Response: `[ EstimateDTO ]`

4. **Get Estimates by Location/Shop** — `GET http://api-gateway.local/workorder/v1/workorders/estimates/shop/{locationId}` | `http://api-gateway.local/workorder/v1/workorders/estimates/location/{locationId}`
   - Path param: `locationId` (Long, required)
   - Response: `[ EstimateDTO ]`
   - **Note:** Both `/shop/{locationId}` and `/location/{locationId}` endpoints exist (deprecated `/shop/*`)

5. **Create Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates`
   - Request: `CreateEstimateRequest { customerId (Long), vehicleId (Long) }`
   - Response: `CreateEstimateResponse { id, estimateNumber, status: DRAFT, locationId, createdAt }`
   - HTTP 200 (success), 400 (validation error), 500 (server error)
   - System generates unique `estimateNumber` (e.g., EST-2024-1001)
   - Requires: `X-User-Id` header (defaults to 1 if missing)

6. **Decline Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/decline`
   - Path param: `estimateId` (Long)
   - Query param: `reason` (String, optional)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DRAFT → DECLINED

7. **Reopen Estimate** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/reopen`
   - Path param: `estimateId` (Long)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DECLINED → DRAFT (within expiry window)
   - Constraint: Cannot reopen if expired

8. **Approve Estimate with Signature** — `POST http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}/approval`
   - Path param: `estimateId` (Long)
   - Request: `ApproveEstimateRequest { customerId (Long), signatureData (String, base64 PNG), signatureMimeType (String), signerName (String, optional), notes (String, optional) }`
   - Response: `EstimateDTO { status: APPROVED, approvedAt, approvedBy, signatureData, signerName }` (200) | 400/404
   - Validation: customerId must match estimate
   - State transition: DRAFT → APPROVED

8. **Delete Estimate** — `DELETE http://api-gateway.local/workorder/v1/workorders/estimates/{estimateId}`
   - Path Parameters:
     - `estimateId` (UUID, required) - Estimate to delete
   - Response: 204 No Content (success) | 404 (not found) | 409 (invalid state)
   - Use Case: Remove draft estimates that are no longer needed
   - Constraint: Can only delete estimates in `DRAFT` status
   - Error: Returns `INVALID_STATE` (409) if estimate is not in DRAFT status

### Estimate Status Enum (Confirmed)

From `EstimateStatus` enum in pos-workorder:
- `DRAFT` — Initial state, editable
- `APPROVED` — Customer approved or system auto-approved
- `DECLINED` — Customer declined
- `EXPIRED` — Approval window closed (time-based expiration)
- `PENDING_APPROVAL` — Awaiting customer approval

---

## Workorders (Confirmed from pos-workorder)

1. **Load Workorder** — `GET http://api-gateway.local/workorder/v1/workorders/{workorderId}`
   - Path param: `workorderId` (Long)
   - Response: `WorkorderDTO { id, shopId, vehicleId, customerId, approvalId, estimateId, status, services[], approvedAt, approvedBy, completedAt, completedBy }`
   - HTTP 200 (success) | 404 (not found)

2. **Get All Workorders** — `GET http://api-gateway.local/workorder/v1/workorders`
   - Response: `[ WorkorderDTO ]` (all workorders, no pagination)

### Workorder Status Enum (Confirmed)

From `WorkorderStatus` enum in pos-workorder:
- `DRAFT` — Initial state
- `APPROVED` — Approved (can transition to ASSIGNED)
- `ASSIGNED` — Assigned to technician (can transition to WORK_IN_PROGRESS)
- `WORK_IN_PROGRESS` — Being worked on
- `AWAITING_PARTS` — Waiting for parts availability
- `AWAITING_APPROVAL` — Awaiting approval for additional work/changes
- `READY_FOR_PICKUP` — Work complete, ready for customer pickup
- `COMPLETED` — Completed and delivered
- `CANCELLED` — Cancelled

### Workorder Services (Items)

**WorkorderService Entity Fields (Confirmed):**
- `id` (Long) — Primary key
- `workorder` (FK to Workorder)
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

### Workorder Parts (Inventory Integration)

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

##### Selective Line Item Approval

The approval request supports **optional selective line item approval/rejection**:

```json
{
   "customerId": "550e8400-e29b-41d4-a716-446655440001",
   "signatureData": "data:image/png;base64,iVBORw0...",
   "signatureMimeType": "image/png",
   "signerName": "John Doe",
   "lineItemApprovals": [
      {
         "lineItemId": 123,
         "approved": true,
         "notes": "Customer approved oil change"
      },
      {
         "lineItemId": 124,
         "approved": false,
         "rejectionReason": "CUSTOMER_DECLINED",
         "notes": "Customer wants to get tires checked elsewhere"
      }
   ]
}
```

**Behavior:**
- If `lineItemApprovals` array is **omitted**, all line items are implicitly approved
- If `lineItemApprovals` array is **provided**, each line item must have an explicit entry
- Approved items (`approved: true`) transition to executable status
- Rejected items (`approved: false`) are flagged and excluded from workorder promotion
- The `rejectionReason` field uses predefined codes (e.g., `CUSTOMER_DECLINED`, `TOO_EXPENSIVE`, `NOT_NECESSARY`)

##### Purchase Order Enforcement (CAP:092)

For **commercial accounts** with purchase order enforcement enabled:

**Field:** `purchaseOrderNumber` (String, conditionally required)

**Validation Rules:**
- Required when customer billing rules indicate `purchaseOrderRequired=true`
- Format: 3-64 characters, pattern `^[A-Za-z0-9][A-Za-z0-9._-]*$`
- Backend queries billing rules via: `GET /v1/crm/accounts/parties/{partyId}/billingRules`

**Error Codes:**
- `MISSING_PO_NUMBER` (400) - PO required for this customer but not provided
- `INVALID_PO_NUMBER` (400) - PO format validation failed

**Request Example with PO:**
```json
{
   "customerId": "550e8400-e29b-41d4-a716-446655440001",
   "signatureData": "data:image/png;base64,iVBORw0...",
   "signatureMimeType": "image/png",
   "signerName": "Fleet Manager",
   "purchaseOrderNumber": "PO-2024-12345",
   "notes": "Approved for fleet vehicle maintenance"
}
```

**Backend Behavior:**
1. Backend receives approval request with `purchaseOrderNumber`
2. Backend calls CRM service to retrieve billing rules for `customerId`
3. If `billingRules.purchaseOrderRequired === true`:
    - Validate PO number is present and matches format
    - Reject with `MISSING_PO_NUMBER` or `INVALID_PO_NUMBER` if validation fails
4. If validation passes, approval proceeds normally with PO attached to estimate record

---

## DTO Schemas (Confirmed from pos-workorder)

**WorkorderItemDTO**
- `workorderItemId`, `originEstimateItemId?`
- `itemType`, `description`, `quantity`, `unitPrice|unitRate|amount`, `lineTotal`, `taxCode`, `taxAmount`
- `status` (PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED) — confirmed from backend
- `requiresReview?`, `notes?`

**PromotionAuditDTO**
- `auditEventId`, `eventTimestamp`, `promotingUserId`
- `estimateId`, `workorderId`, `estimateSnapshotId?`, `approvalId?`
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

### Confirmed from Backend Source (durion-positivity-backend/pos-workorder)

**OpenAPI Specification Generated:** Successfully built pos-workorder module with Java 21 and extracted OpenAPI definitions.

**Confirmed Enums:**
- **EstimateStatus:** DRAFT, APPROVED, DECLINED, EXPIRED, PENDING_APPROVAL
- **WorkorderStatus:** DRAFT, APPROVED, ASSIGNED, WORK_IN_PROGRESS, AWAITING_PARTS, AWAITING_APPROVAL, READY_FOR_PICKUP, COMPLETED, CANCELLED
- **WorkorderItemStatus:** PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED
- **PartStatus:** PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED

**Confirmed Endpoints:**
- Estimate CRUD:
   - GET http://api-gateway.local/workorder/v1/workorders/estimates
   - POST http://api-gateway.local/workorder/v1/workorders/estimates
   - GET http://api-gateway.local/workorder/v1/workorders/estimates/{id}
   - POST http://api-gateway.local/workorder/v1/workorders/estimates/{id}/approval
   - POST http://api-gateway.local/workorder/v1/workorders/estimates/{id}/decline
   - POST http://api-gateway.local/workorder/v1/workorders/estimates/{id}/reopen
   - DELETE http://api-gateway.local/workorder/v1/workorders/estimates/{id}
- Estimate Queries:
   - GET http://api-gateway.local/workorder/v1/workorders/estimates/customer/{id}
   - GET http://api-gateway.local/workorder/v1/workorders/estimates/shop/{id}
   - GET http://api-gateway.local/workorder/v1/workorders/estimates/location/{id}
- Workorder Retrieval: GET http://api-gateway.local/workorder/v1/workorders/{id}, GET http://api-gateway.local/workorder/v1/workorders
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
- pos-workorder internal routing: `/v1/workorders/*`
- API Gateway external routing (docs/examples): `http://localhost:8080/v1/workexec/workorders/*`

### Not Found in pos-workorder Source

- **Item-level add/remove endpoints:** No POST /items:add-part, POST /items:add-labor endpoints discovered
  - Possible: Items added via intermediate service or through change request workflow
- **Tax configuration:** No TaxCode entity or tax calculation logic in pos-workorder
  - Likely: Tax handled by accounting/order modules (pos-order, pos-accounting)
- **Legal terms/snapshot generation:** No summary snapshot or legal terms endpoints
  - Likely: Handled by separate document generation service
- **Promotion endpoints:** No explicit promote-to-workorder endpoint
   - Likely: Workorder creation may be independent or handled by workflow service
- **Approval configuration:** No approval method selection or requirement querying
  - Likely: Centralized in pos-customer or pos-approval module

### API Routing Clarification

**pos-workorder internal routing:** `/v1/workorders/*`

**API Gateway external routing (required for docs/examples):** `http://localhost:8080/v1/workexec/workorders/*`

Frontend should adjust base paths to match actual routing. Controller declares `@RequestMapping("/v1/workorders")`.

---

---

## References

- [Accounting Backend Contract Guide](/durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [CRM Backend Contract Guide](/durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [WorkExec Business Rules](/durion/domains/workexec/.business-rules/)
- [DECISION-INVENTORY Governance](/durion/docs/governance/)
- pos-workorder OpenAPI Specification (generated 2026-01-27)
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