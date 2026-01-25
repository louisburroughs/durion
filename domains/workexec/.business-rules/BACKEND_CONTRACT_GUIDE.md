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

## Endpoint Inventory (Phase 1 Draft)

The paths follow the WorkExec namespace `GET/POST /api/v1/workexec/...`. Mutations accept `Idempotency-Key`. All request/response DTOs use the standard error envelope on failure.

### Estimates

1. **Load Estimate** — `GET /api/v1/workexec/estimates/{estimateId}`
   - Response: `EstimateDTO { estimateId, status, customerId, locationId, activeVersionId, versionNumber, items[], totals{}, approvalConfig?, approvalStatus?, expiresAt?, lastUpdatedAt, lastUpdatedBy }`

2. **Create Estimate** — `POST /api/v1/workexec/estimates`
   - Request: `{ customerId, locationId, appointmentId?, notes? }`
   - Response: `EstimateDTO` (status=DRAFT)

3. **Add Catalog Part** — `POST /api/v1/workexec/estimates/{estimateId}/items:add-part`
   - Request: `{ productId, quantity, unitPrice?, notes?, taxCode? }`
   - Response: `EstimateDTO` (updated)

4. **Add Non-Catalog Part** — `POST /api/v1/workexec/estimates/{estimateId}/items:add-non-catalog-part`
   - Request: `{ description, quantity, unitPrice, partNumber?, notes?, taxCode? }`
   - Capability: `allowNonCatalogParts` flag; backend enforces permission

5. **Add Labor/Service** — `POST /api/v1/workexec/estimates/{estimateId}/items:add-labor`
   - Request: `{ serviceId, laborUnits?, unitRate?, pricingModel?, notes?, taxCode? }`
   - Custom labor: `POST /items:add-custom-labor { description, laborUnits, unitRate, pricingModel, notes?, taxCode? }`

6. **Update Item** — `PATCH /api/v1/workexec/estimates/{estimateId}/items/{estimateItemId}`
   - Request: `{ quantity?, unitPrice?, laborUnits?, unitRate?, notes?, taxCode? }`
   - Concurrency: `If-Match` or `version` field; 409 on mismatch

7. **Submit for Approval** — `POST /api/v1/workexec/estimates/{estimateId}:submit-for-approval`
   - Request: `{ approvalMethod?, consentText?, approvalPayload? }`
   - Response: `EstimateDTO { status: PENDING_APPROVAL or APPROVED, approvalRequestId?, snapshotId? }`

8. **Revise Estimate** — `POST /api/v1/workexec/estimates/{estimateId}/revisions`
   - Request: `{ revisionReason? }`
   - Response: `EstimateDTO { activeVersionId, versionNumber, status: DRAFT }`

9. **Generate Summary Snapshot** — `POST /api/v1/workexec/estimates/{estimateId}/summary-snapshots:generate`
   - Response: `{ snapshotId, legalTermsSource, legalTermsText, lines[], totals{}, expiresAt? }`

10. **Fetch Summary Snapshot** — `GET /api/v1/workexec/estimates/{estimateId}/summary-snapshots/{snapshotId}`
    - Response: Snapshot DTO (JSON structure + optional rendered artifact link)

11. **Promote to WorkOrder** — `POST /api/v1/workexec/estimates/{estimateId}:promote`
    - Request: `{ reason? }`
    - Response: `{ workOrderId, estimateId, originEstimateId?, warnings?, estimateSnapshot? }`
    - Errors: `ALREADY_PROMOTED`, `MISSING_TAX_CODE`, `TOTALS_MISMATCH`, `TAX_CODE_NOT_CONFIGURED`

12. **Promotion Audit Query** — `GET /api/v1/workexec/estimates/{estimateId}/promotion-audits`
    - Response: `[ PromotionAuditDTO ]`

### Approval & Partial Approval

13. **Record Approval with Signature** — `POST /api/v1/workexec/approvals`
    - Request: `{ estimateId|workOrderId, approvalMethod, customerSignatureData?, approvalPayload?, version? }`
    - Response: `{ approvalId, status: APPROVED, approvedAt, approvalMethod, snapshotId?, estimate|workOrder dto }`

14. **Record Partial Approval (Work Order Items)** — `POST /api/v1/workexec/workorders/{workOrderId}/approvals:record-partial`
    - Request: `{ lineDecisions: [ { workOrderItemId, decision: APPROVED|DECLINED, reason?, approvalMethod?, proof? } ], overallNotes?, version? }`
    - Response: `WorkOrderDTO` (with per-item approvalStatus)

15. **Approval Requirements** — `GET /api/v1/workexec/workorders/{workOrderId}/approvals/requirements`
    - Response: `{ method, requireSignature, requireReason?, expiresAt?, requireProofForMethods? }`

16. **Handle Expiration / Deny** — `POST /api/v1/workexec/approvals/{approvalId}/deny`
    - Request: `{ reason }`
    - Errors: `APPROVAL_EXPIRED`, `INVALID_STATE`

17. **Request New Approval** — `POST /api/v1/workexec/estimates/{estimateId}/approvals:request-new`
    - Response: `ApprovalRequestDTO { approvalRequestId, status: PENDING, expiresAt? }`

### Work Orders

18. **Load Work Order** — `GET /api/v1/workexec/workorders/{workOrderId}`
    - Response: `WorkOrderDTO { workOrderId, originEstimateId?, status, items[], totals{}, approvalStatus?, snapshots? }`

19. **List Work Order Items** — `GET /api/v1/workexec/workorders/{workOrderId}/items`
    - Response: `[ WorkOrderItemDTO ]`

20. **Promotion Audit (Work Order view)** — `GET /api/v1/workexec/workorders/{workOrderId}/promotion-audit`
    - Response: `PromotionAuditDTO` (most recent)

### Catalog Search (External Dependencies)

21. **Search Services** — `GET /api/v1/product/services/search?query=&category=&pageIndex=&pageSize=`
    - Response: `{ items[], pageIndex, pageSize, totalCount, hasNextPage }`

22. **Search Parts** — `GET /api/v1/product/parts/search?query=&categoryId=&availability=&pageIndex=&pageSize=`
    - Response: `{ items[], pageIndex, pageSize, totalCount, hasNextPage }`

---

## DTO Sketches (to align in Phase 2)

**EstimateDTO**
- `estimateId`, `status` (DRAFT, APPROVED, DECLINED, EXPIRED) — confirmed from backend
- `version` (Integer, increments on financial changes)
- `customerId`, `locationId`, `appointmentId?`
- `items[]` → `EstimateItemDTO`
- `totals { subtotal, taxTotal, grandTotal, currencyUomId }`
- `approvalConfig { method, requireSignature, requireReason, requireAddress?, expiresAt? }`
- `expiresAt?`, `createdAt`, `updatedAt`, `updatedBy`

**EstimateItemDTO**
- `estimateItemId`, `itemType` (LABOR, PART, FEE, DISCOUNT)
- `description`, `quantity`, `unitPrice|unitRate|amount`, `lineTotal`, `taxCode`, `taxAmount`
- `pricingModel?` (TIME_BASED, FLAT_RATE)
- `approvalStatus?` (PENDING_APPROVAL, APPROVED, DECLINED)
- `requiresReview?`, `notes?`

**WorkOrderDTO**
- `workOrderId`, `originEstimateId?`, `status` (DRAFT, APPROVED, ASSIGNED, WORK_IN_PROGRESS, AWAITING_PARTS, AWAITING_APPROVAL, READY_FOR_PICKUP, COMPLETED, CANCELLED) — confirmed from backend
- `items[]` → `WorkOrderItemDTO`
- `totals { subtotal, taxTotal, grandTotal, currencyUomId }`
- `approvalStatus?`, `snapshots?`

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

## Phase 2.1 Validation Results

### Confirmed from Backend Source (durion-positivity-backend/pos-work-order)

- **Estimate Status:** DRAFT, APPROVED, DECLINED, EXPIRED,PENDING_APPROVAL 
- **WorkOrder Status:** DRAFT, APPROVED, ASSIGNED, WORK_IN_PROGRESS, AWAITING_PARTS, AWAITING_APPROVAL, READY_FOR_PICKUP, COMPLETED, CANCELLED
- **WorkOrderItem Status:** PENDING_APPROVAL, OPEN, READY_TO_EXECUTE, IN_PROGRESS, COMPLETED, CANCELLED
- **ApprovalMethod:** CLICK_CONFIRM, SIGNATURE, ELECTRONIC_SIGNATURE, VERBAL_CONFIRMATION
- **ApprovalRecord ResolutionStatus:** APPROVED, REJECTED, APPROVED_WITH_EXCEPTION
- **Concurrency:** Uses `version` Integer field in Estimate entity (increments on financial changes); NOT using JPA @Version or If-Match header
- **EstimateSequence:** Uses JPA @Version for optimistic locking on sequence generation

### Pending Backend Validation (Not Found in Source)

- Tax code requirements and defaults per item type (no TaxCode entity found in pos-work-order)
- Legal terms policy endpoint and fields (not found in pos-work-order controllers)
- Capability/permission flags structure (allowNonCatalogParts, allowCustomLabor, canOverridePrice not found)
- Item approval statuses: WorkOrderItemStatus is separate from ApprovalRecord.ResolutionStatus

### Backend Findings Notes

- Estimate approval uses `POST /v1/workorders/estimates/{estimateId}/approval` endpoint
- Approval captures: customerId, signatureData, signatureMimeType, signerName, notes
- Decline estimate: `POST /v1/workorders/estimates/{estimateId}/decline` with optional reason
- Reopen estimate: `POST /v1/workorders/estimates/{estimateId}/reopen` (within expiry window)
- No explicit item-level endpoints found; item mutations likely happen via estimate/workorder mutations

---

## References

- Accounting Backend Contract Guide
- CRM Backend Contract Guide
- WorkExec CUSTOMER_APPROVAL_WORKFLOW
- WorkExec WORKORDER_STATE_MACHINE
- DECISION-INVENTORY (auth, error envelope, idempotency, capability signaling)

---

## Phase 4.2: Backend Contract Discovery (Additional APIs)

**Date:** 2026-01-25  
**Scope:** Discovered backend APIs for 18 remaining WorkExec issues, including parts usage, billing finalization, timekeeping (pos-people), scheduling (pos-shop-manager), and sales orders (pos-order)

---

### Parts Usage & Inventory Integration (Issue #222)

**Backend Module:** `pos-work-order`  
**Entity:** `WorkorderPart` (confirmed)  
**Status:** ✅ ENTITY CONFIRMED, ⏳ API ENDPOINTS PENDING

**Confirmed from Backend Source:**
- `WorkorderPart` entity exists with fields:
  - `productEntityId` (reference to pos-catalog)
  - `nonInventoryProductEntityId` (reference to pos-catalog non-inventory items)
  - `quantity` (Integer)
  - `status` (WorkorderItemStatus enum)
  - `declined` (Boolean) - customer declined during estimate approval
  - `isEmergencySafety` (Boolean) - emergency/safety item flag
  - `photoEvidenceUrl` (String) - photo documentation URL
  - `emergencyNotes` (TEXT) - emergency justification
  - `photoNotPossible` (Boolean) - flag when photo not captured
  - `customerDenialAcknowledged` (Boolean) - customer denial for emergency items
  - `changeRequestId` (Long) - reference to change request that added item
- Business methods:
  - `canExecute()` - checks if item can be worked on (not pending approval unless emergency exception)
  - `canConsumeInventory()` - checks if item can consume inventory (not pending approval)

**Missing API Endpoints (PENDING):**
- ❌ `POST /v1/workorders/{workorderId}/items/{itemId}/usage:record` - Record parts/fluids usage during work execution
  - **Suggested Request:** `{ partNumber?, quantityUsed, usedAt, technicianId?, notes?, photoEvidenceUrl? }`
  - **Suggested Response:** `{ usageRecordId, workorderItemId, inventoryAdjustmentId?, remainingStock?, status }`
  - **Error Codes:** `INSUFFICIENT_STOCK`, `PART_NOT_FOUND`, `ITEM_NOT_EXECUTABLE`
- ❌ Integration with `pos-inventory` module for stock checking and adjustment (module structure TBD)

**Cross-Domain Integration:**
- WorkExec → Inventory: Parts usage events should trigger inventory adjustments
- Event schema (suggested): `workorder.parts_usage_recorded { workorderId, itemId, partNumber, quantityUsed, timestamp }`

---

### Billing Finalization & Invoice-Ready Status (Issue #216)

**Backend Module:** `pos-work-order`  
**Entity:** `WorkorderSnapshot` (confirmed for billable snapshot capture)  
**Status:** ⏳ API ENDPOINTS PENDING

**Confirmed from Backend Source:**
- `WorkorderSnapshot` entity exists with fields:
  - `workorderId` (Long)
  - `status` (WorkorderStatus enum)
  - `capturedAt` (Instant)
  - `capturedBy` (Long - userId)
  - `snapshotType` (String - likely "BILLABLE", "COMPLETION", "APPROVAL")
  - `snapshotData` (TEXT - JSON blob of billable items/totals)
  - `reason` (TEXT - optional reason for snapshot)

**Missing API Endpoints (PENDING):**
- ❌ `POST /v1/workorders/{workorderId}:finalize` - Mark work order as billable/invoice-ready
  - **Suggested Request:** `{ finalizedBy, finalizationNotes?, varianceApproval?, snapshotType: "BILLABLE" }`
  - **Suggested Response:** `{ workorderId, status: "READY_FOR_INVOICING", snapshotId, billableTotal, invoiceReadyAt }`
  - **Error Codes:** `FINALIZATION_NOT_ALLOWED`, `VARIANCE_REQUIRES_APPROVAL`, `INCOMPLETE_WORK_ITEMS`
- ❌ `GET /v1/workorders/{workorderId}/billable-snapshot` - Retrieve billable snapshot for invoicing
  - **Suggested Response:** `{ snapshotId, capturedAt, billableItems[], totals{}, legalTerms?, varianceApproval? }`

**Business Rules (Inferred):**
- Finalization should create a `WorkorderSnapshot` with `snapshotType: "BILLABLE"`
- Variance approval required if actual costs exceed estimate by threshold (TBD from business rules)
- Snapshot captures point-in-time billable state (items, quantities, prices, taxes)

---

### Timekeeping APIs (Issues #149, #146, #145, #131, #132)

**Backend Module:** `pos-people` ✅ (confirmed via Phase 4.1 domain ownership resolution)  
**Controller:** `WorkSessionController.java` (confirmed)  
**Status:** ✅ CONTROLLER CONFIRMED, ⏳ IMPLEMENTATION PENDING (TODO stubs)

**Confirmed API Endpoints (Stubs Only):**
1. ✅ `POST /v1/people/workSessions/start` - Start work session
   - **Status:** TODO stub exists, implementation pending
   - **Suggested Request:** `{ personId, locationId, workorderId?, startedAt?, clockInMethod? }`
   - **Suggested Response:** `{ workSessionId, personId, startedAt, status: "ACTIVE" }`

2. ✅ `POST /v1/people/workSessions/stop` - Stop work session
   - **Status:** TODO stub exists, implementation pending
   - **Suggested Request:** `{ workSessionId, stoppedAt?, clockOutMethod?, notes? }`
   - **Suggested Response:** `{ workSessionId, stoppedAt, duration, status: "COMPLETED" }`

3. ✅ `POST /v1/people/workSessions/{id}/breaks/start` - Start break
   - **Status:** TODO stub exists, implementation pending
   - **Suggested Request:** `{ breakType?, startedAt? }`
   - **Suggested Response:** `{ breakId, workSessionId, startedAt, breakType }`

4. ✅ `POST /v1/people/workSessions/{id}/breaks/stop` - Stop break
   - **Status:** TODO stub exists, implementation pending
   - **Suggested Request:** `{ breakId, stoppedAt? }`
   - **Suggested Response:** `{ breakId, stoppedAt, duration }`

**Missing Endpoints (PENDING):**
- ❌ `POST /v1/people/timeEntries` - Submit time entry (for manual time entry submission, Issue #145)
- ❌ `POST /v1/people/timers/start` - Start task timer (Issue #146)
- ❌ `POST /v1/people/timers/{timerId}/stop` - Stop task timer (Issue #146)
- ❌ `POST /v1/people/travelTime` - Capture mobile travel time (Issue #131)
- ❌ `GET /v1/people/workSessions` - List work sessions with filters (date range, person, location)
- ❌ `GET /v1/people/timeEntries` - List time entries with filters

**Cross-Domain Integration:**
- WorkExec publishes `work_session.started`, `work_session.completed` events
- People domain subscribes to these events to create timekeeping entries
- Accounting domain exports timekeeping data for payroll (confirmed from Accounting business rules)

**Domain Ownership:** `domain:people` (confirmed via DECISION-WORKEXEC-001)

---

### Scheduling & Dispatch APIs (Issues #138, #137, #134, #133)

**Backend Module:** `pos-shop-manager` ✅ (confirmed via Phase 4.1 domain ownership resolution)  
**Controller:** `ScheduleController.java` (confirmed)  
**Status:** ✅ CONTROLLER CONFIRMED, ⏳ IMPLEMENTATION PENDING (TODO stub)

**Confirmed API Endpoints (Stubs Only):**
1. ✅ `GET /v1/shop-manager/{locationId}/schedules/view` - View schedules with filters
   - **Status:** TODO stub exists, implementation pending
   - **Suggested Query Params:** `?date={date}&technicianId={id}&workorderId={id}&status={status}`
   - **Suggested Response:** `{ schedules: [ { appointmentId, workorderId, technicianId, scheduledAt, status, bay?, estimatedDuration } ] }`

**Missing Endpoints (PENDING):**
- ❌ `POST /v1/shop-manager/appointments` - Create appointment from estimate/workorder (Issue #129)
  - **Suggested Request:** `{ estimateId?, workorderId?, customerId, vehicleId, scheduledAt, estimatedDuration, locationId, services[] }`
  - **Suggested Response:** `{ appointmentId, status: "SCHEDULED", scheduledAt, assignedTechnician?, bay? }`

- ❌ `PUT /v1/shop-manager/appointments/{appointmentId}/reschedule` - Reschedule work order (Issue #137)
  - **Suggested Request:** `{ newScheduledAt, reason?, overrideConflict: false }`
  - **Suggested Response:** `{ appointmentId, previousScheduledAt, newScheduledAt, status, conflicts[]? }`
  - **Error Codes:** `SCHEDULING_CONFLICT`, `TECHNICIAN_UNAVAILABLE`, `BAY_OCCUPIED`

- ❌ `POST /v1/shop-manager/appointments/{appointmentId}/assign` - Assign mechanic to work order item (Issue #134)
  - **Suggested Request:** `{ technicianId, workorderItemId?, assignedAt?, notes? }`
  - **Suggested Response:** `{ assignmentId, appointmentId, technicianId, workorderItemId, assignedAt }`

- ❌ `POST /v1/shop-manager/appointments/{appointmentId}/reschedule:override-conflict` - Override scheduling conflict (Issue #133)
  - **Suggested Request:** `{ newScheduledAt, overrideReason, conflictIds[] }`
  - **Suggested Response:** `{ appointmentId, scheduledAt, overrideAuditId, conflicts[] }`
  - **Capability Flag:** `OVERRIDE_SCHEDULING_CONFLICT` (confirmed from ShopMgmt business rules)

- ❌ `PUT /v1/shop-manager/appointments/{appointmentId}/status` - Update appointment status (Issue #127)
  - **Suggested Request:** `{ status: "IN_PROGRESS" | "COMPLETED" | "CANCELLED", workorderId?, timestamp?, reason? }`
  - **Suggested Response:** `{ appointmentId, previousStatus, currentStatus, updatedAt }`
  - **Event:** Publishes `appointment.status.changed` for WorkExec consumption

**Cross-Domain Integration:**
- WorkExec publishes `workorder.status_changed` (status: APPROVED) → ShopMgmt enables scheduling workflows
- ShopMgmt publishes `appointment.scheduled`, `appointment.rescheduled`, `appointment.status_changed` → WorkExec displays scheduling status
- WorkExec UI may embed ShopMgmt scheduling views (iframe or shared Vue component)

**Domain Ownership:** `domain:shopmgmt` (confirmed via DECISION-WORKEXEC-001)

---

### Sales Order Creation from Work Order (Issue #85)

**Backend Module:** `pos-order` (confirmed module exists)  
**Status:** ⏳ API ENDPOINTS PENDING (no Order-specific controllers found in quick search)

**Missing Endpoints (PENDING):**
- ❌ `POST /v1/orders/from-workorder` - Create sales order from work order items
  - **Suggested Request:** `{ workorderId, selectedItemIds[], customerId, paymentTerms?, shippingAddress?, notes? }`
  - **Suggested Response:** `{ orderId, orderNumber, totalAmount, status: "PENDING_PAYMENT", items[], createdAt }`
  - **Error Codes:** `WORKORDER_NOT_BILLABLE`, `CUSTOMER_MISMATCH`, `PRICING_UNAVAILABLE`

**Cross-Domain Integration:**
- WorkExec UI initiates sales order creation → calls `pos-order` APIs with work order item data
- Order domain creates sales order entity and returns `orderId`
- WorkExec stores reference to `orderId` in workorder entity (field: `salesOrderId`?)
- Event: `order.created_from_workorder { orderId, workorderId, totalAmount, createdAt }`

**Domain Ownership:** `domain:order` (confirmed via DECISION-WORKEXEC-001)

---

### Estimate Display/Filtering (Issue #79)

**Backend Module:** `pos-work-order`  
**Controller:** `EstimateController.java` ✅ (confirmed)  
**Status:** ✅ PARTIAL IMPLEMENTATION CONFIRMED

**Confirmed API Endpoints:**
1. ✅ `GET /v1/workorders/estimates` - Get all estimates (no filters)
   - **Status:** Implemented, returns `List<Estimate>`
   - **Enhancement Needed:** Add query parameters for filtering

2. ✅ `GET /v1/workorders/estimates/{estimateId}` - Get estimate by ID
   - **Status:** Implemented

3. ✅ `GET /v1/workorders/estimates/customer/{customerId}` - Get estimates by customer
   - **Status:** Implemented

4. ✅ `GET /v1/workorders/estimates/location/{locationId}` - Get estimates by location
   - **Status:** Implemented

**Enhancement Needed (PENDING):**
- ⏳ Add comprehensive filtering to `GET /v1/workorders/estimates`:
  - **Suggested Query Params:** `?customerId={id}&locationId={id}&status={status}&fromDate={date}&toDate={date}&vehicleId={id}&page={num}&size={num}`
  - **Response:** `{ estimates: [ EstimateDTO ], totalCount, page, size }`
- ⏳ Add sorting: `?sortBy=createdAt&sortOrder=desc`
- ⏳ Add pagination support (currently returns all results)

---

### CRM References Display (Issue #157)

**Backend Module:** `pos-crm` (external domain)  
**Status:** ✅ CONFIRMED (from CRM domain business rules)

**Confirmed API Endpoints (from CRM domain):**
- ✅ `GET /api/v1/crm/customers/{customerId}` - Get customer details
  - **Response:** `{ customerId, displayName, email, phone, preferredContact, addresses[], vehicles[] }`
  - **Usage in WorkExec:** WorkExec screens call this endpoint to display customer details in work order/estimate views

**Cross-Domain Integration:**
- WorkExec stores `customerId` (opaque ID) in Estimate/Workorder entities
- WorkExec UI calls CRM API to fetch customer display data (read-only)
- No write operations from WorkExec to CRM (CRM domain owns customer lifecycle)

---

### Role-Based Work Order Visibility (Issue #219)

**Backend Module:** `pos-work-order`  
**Status:** ⏳ AUTHORIZATION MODEL PENDING

**Security Model (Inferred from Architecture):**
- Permissions defined in `domain:security` (see `/home/louisb/Projects/durion/domains/security/docs/BASELINE_PERMISSIONS.md`)
- WorkOrder visibility controlled by:
  - `workorder:view:own` - see only assigned work orders
  - `workorder:view:team` - see team/shop work orders
  - `workorder:view:all` - admin/manager visibility (all work orders)

**Missing Implementation (PENDING):**
- ❌ Filter work orders by user permissions in `GET /v1/workorders` endpoint
- ❌ Capability flags to signal visibility scope:
  - **Suggested Response Fields:** `{ canViewAllWorkorders, canViewTeamWorkorders, canViewOwnWorkorders, scopedLocationIds[] }`
- ❌ Backend enforcement: filter query results based on user's assigned locations/teams

---

### PO# Requirement Before Finalization (Issue #162)

**Backend Module:** `pos-work-order`  
**Status:** ⏳ BUSINESS RULE ENFORCEMENT PENDING

**Missing Implementation (PENDING):**
- ❌ `customerPoNumber` field in Workorder entity (not found in backend source)
- ❌ Validation rule: Check if PO# required for customer before finalizing workorder
- ❌ Configuration endpoint: `GET /v1/workorders/config/po-requirements` to determine when PO# is mandatory
  - **Suggested Response:** `{ requiresPoNumber: true, enforceAt: "FINALIZATION" | "APPROVAL" | "CREATION" }`
- ❌ Finalization endpoint should return `MISSING_CUSTOMER_PO` error if PO# required but not provided

**Capability Flag (Suggested):**
- `requiresCustomerPo` (boolean) - returned in WorkorderDTO to signal UI to prompt for PO#

---

## Phase 4.2 Summary

### Confirmed Implementations ✅
- WorkorderController endpoints (start, complete, approve, transitions, snapshots)
- EstimateController endpoints (create, approve, decline, reopen, list by customer/location)
- WorkSessionController stubs (start/stop session, start/stop break) in pos-people
- ScheduleController stub (view schedules) in pos-shop-manager
- WorkorderPart entity with parts usage fields
- WorkorderSnapshot entity for billable snapshots

### Pending Implementations ⏳
- Parts usage recording API (#222)
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