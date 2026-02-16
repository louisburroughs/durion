# WorkExec Backend Contract Guide

**Version:** 0.4 (Includes CAP-007 invoice contract design)
**Audience:** Backend developers, Frontend developers, API consumers
**Last Updated:** 2026-02-15

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for WorkExec REST APIs. It reuses patterns from accounting and CRM backend contract guides and aligns with DECISION-INVENTORY (auth fail-closed, opaque IDs, standard error envelope, idempotency, capability signaling).

**Primary Goal (Phase 1):** Document the authoritative contracts for the endpoints referenced in [workexec-questions.md](../workexec-questions.md) so blocking questions can be resolved in Phase 2.

---

## Known Limitations (v1 Implementation)

The OpenAPI v1 spec produced by `pos-workorder` is the authoritative source for implemented endpoints. Several items previously listed as "missing" are now present in the OpenAPI spec; the guide below reflects that reality. Specific gaps that still require work are noted inline and referenced to backend child issues.

### Item Management

Status: Endpoints to add estimate items are present in the OpenAPI spec at `/v1/workorders/estimates/{estimateId}/items` (POST). If PATCH/DELETE semantics are required for partial updates or removals, open follow-up backend issues and reference Implementation Links.

### Tax Calculation

Status: The explicit calculate endpoint (`/v1/workorders/estimates/{estimateId}/calculate`) is present in the OpenAPI spec. Clients can call this endpoint to recalc totals prior to promotion/approval.

### Summary / Snapshot Generation

Status: The snapshot endpoint (`/v1/workorders/estimates/{estimateId}/snapshots`) is present. If a dedicated `summary` endpoint is still required for printable/PDF output, track via a follow-up issue.

### Approval Workflow

Status: Approval endpoints (estimate approval and workorder approval) exist in the OpenAPI spec (e.g., `/v1/workorders/estimates/{estimateId}/approval`, `/v1/workorders/{workorderId}/approval`). If the UX requires a separate `submit` step (DRAFT → PENDING_APPROVAL) add a child backend issue to track.

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

All endpoint examples in this guide MUST use the API Gateway format:

- `http://localhost:8080/v1/workexec/{resource}`

Example: `POST http://localhost:8080/v1/workexec/estimates`

**Note:** The API Gateway routes requests using the `/v{version}/{domain}/...` prefix. For WorkExec APIs use `http://localhost:8080/v1/workexec/...`. The gateway will forward requests to the `pos-workorder` service; do NOT reference direct-service hostnames or ports (for example `localhost:8082`) in this guide.

Mutations accept `Idempotency-Key` header. All request/response DTOs use standard error envelope on failure.

### Estimates (Confirmed APIs)
<!-- contract-status: draft -->
<!-- anchor: cap-005-estimates -->

1. **Get All Estimates** — `GET http://localhost:8080/v1/workexec/estimates`
   - Response: `[ EstimateDTO ]`
   - No pagination in source; returns all estimates

2. **Get Estimate by ID** — `GET http://localhost:8080/v1/workexec/estimates/{estimateId}`
   - Path param: `estimateId` (opaque string, required)
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
   - `id` (opaque string) - Unique estimate identifier
   - `estimateNumber` (String) - Human-readable estimate number (e.g., "EST-2024-1000")
   - `customerId` (opaque string) - Customer party ID
   - `vehicleId` (opaque string) - Vehicle/asset ID
   - `locationId` (opaque string) - Shop/facility location ID
   - `currencyUomId` (String) - Currency code (e.g., "USD")
   - `taxRegionId` (opaque string) - Tax jurisdiction ID
   - `status` (EstimateStatus) - Current lifecycle status
   - `createdByUserId` (opaque string) - User who created the estimate
   - `createdAt` (ISO 8601 UTC) - Creation timestamp

   **Fields Planned for Future Releases:**
   - `updatedAt` (ISO 8601) - Last modification timestamp
   - `approvedAt` (ISO 8601) - Approval timestamp
   - `approvedBy` (opaque string) - Approving user ID
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

3. **Get Estimates by Customer** — `GET http://localhost:8080/v1/workexec/estimates/customer/{customerId}`
   - Path param: `customerId` (opaque string, required)
   - Response: `[ EstimateDTO ]`

4. **Get Estimates by Location/Shop** — `GET http://localhost:8080/v1/workexec/estimates/shop/{locationId}` | `GET http://localhost:8080/v1/workexec/estimates/location/{locationId}`
   - Path param: `locationId` (opaque string, required)
   - Response: `[ EstimateDTO ]`
   - **Note:** Both `/shop/{locationId}` and `/location/{locationId}` endpoints exist (deprecated `/shop/*`)

5. **Create Estimate** — `POST http://localhost:8080/v1/workexec/estimates`
   - Request: `CreateEstimateRequest { customerId (opaque string), vehicleId (opaque string) }`
   - Response: `CreateEstimateResponse { id, estimateNumber, status: DRAFT, locationId, createdAt }`
   - HTTP 200 (success), 400 (validation error), 500 (server error)
   - System generates unique `estimateNumber` (e.g., EST-2024-1001)
   - Requires: `X-User-Id` header (defaults to 1 if missing)

6. **Decline Estimate** — `POST http://localhost:8080/v1/workexec/estimates/{estimateId}/decline`
   - Path param: `estimateId` (opaque string)
   - Query param: `reason` (String, optional)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DRAFT → DECLINED

7. **Reopen Estimate** — `POST http://localhost:8080/v1/workexec/estimates/{estimateId}/reopen`
   - Path param: `estimateId` (opaque string)
   - Response: `EstimateDTO` (200) | 400/404
   - State transition: DECLINED → DRAFT (within expiry window)
   - Constraint: Cannot reopen if expired

8. **Approve Estimate with Signature** — `POST http://localhost:8080/v1/workexec/estimates/{estimateId}/approval`
   - Path param: `estimateId` (opaque string)
   - Request: `ApproveEstimateRequest { customerId (opaque string), signatureData (String, base64 PNG), signatureMimeType (String), signerName (String, optional), notes (String, optional) }`
   - Response: `EstimateDTO { status: APPROVED, approvedAt, approvedBy, signatureData, signerName }` (200) | 400/404
   - Validation: customerId must match estimate
   - State transition: DRAFT → APPROVED

9. **Delete Estimate** — `DELETE http://localhost:8080/v1/workexec/estimates/{estimateId}`
   - Path Parameters:
     - `estimateId` (opaque string, required) - Estimate to delete
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
<!-- contract-status: draft -->
<!-- anchor: cap-005-workorders -->

1. **Load Workorder** — `GET http://localhost:8080/v1/workexec/workorders/{workorderId}`
   - Path param: `workorderId` (opaque string)
   - Response: `WorkorderDTO { id, shopId, vehicleId, customerId, approvalId, estimateId, status, services[], approvedAt, approvedBy, completedAt, completedBy }`
   - HTTP 200 (success) | 404 (not found)

2. **Get All Workorders** — `GET http://localhost:8080/v1/workexec/workorders`
   - Response: `[ WorkorderDTO ]` (all workorders, no pagination)

---

## Workorder Execution & Status Tracking (CAP-005 Story #160)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-160 -->

### 3. Start Workorder

**Endpoint:** `POST http://localhost:8080/v1/workexec/workorders/{workorderId}/start`

**Description:** Start work on a work order, transitioning it to WORK_IN_PROGRESS status.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order to start

**Request Body:**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "Customer arrived and dropped off vehicle"
}
```

**Request Fields:**

- `userId` (opaque string, optional) — User who is starting the workorder
- `reason` (String, optional) — Reason for starting the workorder

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "previousStatus": "APPROVED",
  "currentStatus": "WORK_IN_PROGRESS",
  "transitionedAt": "2024-01-27T14:30:00Z",
  "message": "Work order started successfully"
}
```

**Response Fields:**

- `workorderId` (opaque string) — ID of the work order
- `previousStatus` (String) — Status before transition (APPROVED or ASSIGNED)
- `currentStatus` (String) — Current status after transition (WORK_IN_PROGRESS)
- `transitionedAt` (ISO 8601 UTC) — Timestamp when the transition occurred
- `message` (String) — Operation result message

**Error Responses:**

- **400 Bad Request** — Invalid state transition or pending change requests

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Cannot start workorder: pending change requests must be resolved first",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

- **404 Not Found** — Work order not found

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Workorder with ID 550e8400-e29b-41d4-a716-446655440001 not found",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Status Transition Rules:**

- Start is allowed **only** from `APPROVED` or `ASSIGNED` status
- Target status after start: `WORK_IN_PROGRESS`
- If change requests exist with status `AWAITING_ADVISOR_REVIEW`, start is rejected with `INVALID_STATE` error
- State transition is recorded in the transition history (append-only log)

**Business Rules:**

- Users must have `workorder:workorder:start` authority to call this endpoint
- The transition is logged with the user ID, reason, and timestamp
- A `WorkorderStateTransition` record is created capturing the from/to status

---

### 4. Get Transition History

**Endpoint:** `GET http://localhost:8080/v1/workexec/workorders/{workorderId}/transitions`

**Description:** Retrieve the state transition history for a work order in chronological order (newest first).

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Success Response (200 OK):**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440010",
    "workorderId": "550e8400-e29b-41d4-a716-446655440001",
    "fromStatus": "APPROVED",
    "toStatus": "WORK_IN_PROGRESS",
    "transitionedAt": "2024-01-27T14:30:00Z",
    "transitionedBy": "550e8400-e29b-41d4-a716-446655440000",
    "reason": "Customer arrived and dropped off vehicle",
    "metadata": null
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440011",
    "workorderId": "550e8400-e29b-41d4-a716-446655440001",
    "fromStatus": "DRAFT",
    "toStatus": "APPROVED",
    "transitionedAt": "2024-01-27T10:00:00Z",
    "transitionedBy": "550e8400-e29b-41d4-a716-446655440000",
    "reason": "Estimate approved and promoted",
    "metadata": null
  }
]
```

**Response Fields (Array of WorkorderStateTransitionResponse):**

- `id` (opaque string) — Unique identifier for this transition record
- `workorderId` (opaque string) — ID of the work order
- `fromStatus` (String) — The status the work order transitioned FROM
- `toStatus` (String) — The status the work order transitioned TO
- `transitionedAt` (ISO 8601 UTC) — Timestamp when the transition occurred
- `transitionedBy` (opaque string) — User ID who performed the transition
- `reason` (String, optional) — Reason for the transition
- `metadata` (String, optional) — Additional metadata about the transition

**Ordering:**

- Transitions are returned in chronological order with **newest first** (descending by `transitionedAt`)
- The array is append-only; transitions are never deleted or modified

**Business Rules:**

- Transition history is immutable and append-only
- Each state change creates exactly one transition record
- Users must have `workorder:workorder:view` authority to access transition history

---

### 5. Get Snapshot History

**Endpoint:** `GET http://localhost:8080/v1/workexec/workorders/{workorderId}/snapshots`

**Description:** Retrieve the snapshot history for a work order.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Success Response (200 OK):**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440020",
    "workorderId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "WORK_IN_PROGRESS",
    "capturedAt": "2024-01-27T14:30:00Z",
    "capturedBy": "550e8400-e29b-41d4-a716-446655440000",
    "snapshotType": "AUTOMATIC",
    "snapshotData": "{\"services\":[{\"id\":\"...\",\"status\":\"OPEN\"}],\"parts\":[]}",
    "reason": "Status transition to WORK_IN_PROGRESS"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440021",
    "workorderId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "APPROVED",
    "capturedAt": "2024-01-27T10:00:00Z",
    "capturedBy": "550e8400-e29b-41d4-a716-446655440000",
    "snapshotType": "MANUAL",
    "snapshotData": "{\"services\":[],\"parts\":[]}",
    "reason": "Workorder created from estimate promotion"
  }
]
```

**Response Fields (Array of WorkorderSnapshotResponse):**

- `id` (opaque string) — Unique identifier for this snapshot record
- `workorderId` (opaque string) — ID of the work order
- `status` (String) — Status of the work order at the time of this snapshot
- `capturedAt` (ISO 8601 UTC) — Timestamp when the snapshot was captured
- `capturedBy` (opaque string) — User ID who captured the snapshot
- `snapshotType` (String) — Type of snapshot (MANUAL, AUTOMATIC, SYSTEM)
- `snapshotData` (String) — Snapshot data (typically JSON, contains services and parts state)
- `reason` (String, optional) — Reason for capturing the snapshot

**Ordering:**

- Snapshots are returned in chronological order with **newest first** (descending by `capturedAt`)

**Business Rules:**

- Snapshots capture the complete state of workorder services and parts at a point in time
- Automatic snapshots are created on significant workorder state transitions
- Manual snapshots can be created by users with appropriate permissions
- Users must have `workorder:workorder:view` authority to access snapshot history

---

### OpenAPI Delta Summary (short)

- Added (present in current OpenAPI but not yet documented in detail in this guide):
  - `/v1/workexec/workorders/{workorderId}/technician` (technician assignment endpoints)
  - `/v1/workexec/workorders/{workorderId}/labor/{entryId}/adjust` (labor adjustments)
  - `/v1/workexec/workorders/{workorderId}/services/{serviceId}/labor/start` (start labor session)
  - `/v1/workexec/workorders/{workorderId}/parts/*` (issue/return/consume/substitute/correct endpoints)
  - `/v1/workexec/approvalConfigurations/*` (approval configuration CRUD)

- Changed (gateway mapping applied):
  - OpenAPI paths in `pos-workorder` are transformed to gateway format by prepending the domain `workexec` after the version. Example: OpenAPI `/v1/workorders` → Gateway `http://localhost:8080/v1/workexec/workorders`.

- Removed / Deprecated (documented here for traceability):
  - None detected in this pass; any guide-only endpoints not present in OpenAPI should be marked deprecated and tracked for removal.

### Implementation Links

- Backend child issues referenced by CAP-007 (manifest):
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/149>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/148>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/147>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/146>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/145>

Cross-reference notes:

- Path refactoring performed: any direct-service or non-gateway examples in this guide must be treated as gateway paths. If a backend child issue implements or references a direct-service URL (e.g., `localhost:8096`), map it to `http://localhost:8080/v1/workexec/...` in frontend wiring and provider tests.
- For missing DTO detail or ambiguous examples in OpenAPI, add `TODO` comments in the relevant backend issue above so maintainers can enrich OpenAPI examples or component schemas.
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/150>

These issues correspond to backend work for the CAP:006 stories listed in the capability manifest. Cross-reference these issues for implementation details, provider tests, and any path refactoring notes.

- <https://github.com/louisburroughs/durion-positivity-backend/issues/163>
- <https://github.com/louisburroughs/durion-positivity-backend/issues/162>

Refer to these issues for provider test responsibilities, missing behavior details, or follow-up API changes. Cross-reference these issues when adding provider ContractBehaviorIT tests or when clarifying behavioral TODOs in this guide.

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
<!-- contract-status: draft -->
<!-- anchor: cap-005-workorder-items -->

**WorkorderService Entity Fields (Confirmed):**

- `id` (opaque string) — Primary key
- `workorder` (FK to Workorder)
- `serviceEntityId` (opaque string) — Reference to ServiceEntity in pos-catalog
- `technicianId` (opaque string) — Reference to Technician
- `declined` (Boolean, default=false) — Flag: declined by customer during estimate approval
- `status` (Enum) — WorkorderItemStatus (see below)
- `changeRequestId` (opaque string, optional) — Reference to change request that added this item
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
<!-- contract-status: draft -->
<!-- anchor: cap-005-workorder-parts -->

**WorkorderPart Entity Fields (Confirmed):**

- `id` (opaque string) — Primary key
- `workorderService` (FK to WorkorderService)
- `productEntityId` (opaque string) — Reference to pos-catalog Product
- `quantity` (BigDecimal) — Quantity used
- `unitPrice` (BigDecimal) — Price per unit
- `actualQuantityUsed` (BigDecimal, optional) — Actual qty consumed from inventory
- `changeRequestId` (opaque string, optional) — Reference to change request that added this part
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

## Invoice Generation & Management (CAP-007 Stories #43-47)
<!-- contract-status: design-spec-phase-2 -->
<!-- anchor: cap-007-invoice-generation -->

This section defines the **design contract** for CAP-007 invoice generation and management APIs. These endpoints are not implemented yet; implementation is scheduled for Phase 3-4.

All paths in this section use API Gateway format:

- `http://localhost:8080/v1/workexec/*`

Routing and ownership model:

- Invoice generation is triggered by workorder APIs and routed to `pos-workorder`.
- `pos-workorder` may delegate invoice persistence and lifecycle operations to `pos-invoice` internally.
- This guide documents only the public, gateway-facing contract.

Implementation tracking issues:

- <https://github.com/louisburroughs/durion-positivity-backend/issues/149> (Story 43)
- <https://github.com/louisburroughs/durion-positivity-backend/issues/148> (Story 44)
- <https://github.com/louisburroughs/durion-positivity-backend/issues/147> (Story 45)
- <https://github.com/louisburroughs/durion-positivity-backend/issues/146> (Story 46)
- <https://github.com/louisburroughs/durion-positivity-backend/issues/145> (Story 47)

### Shared CAP-007 Schemas (Design)

```ts
type InvoiceStatus = 'DRAFT' | 'ISSUED';

type InvoiceAdjustmentType = 'discount' | 'fee' | 'correction';

type Money = number; // decimal, max 4 fractional digits

interface InvoiceTraceability {
  workorderId: string;
  estimateId: string;
  approvalId: string;
}

interface InvoiceTotals {
  subtotal: Money;
  taxAmount: Money;
  feesAmount: Money;
  total: Money;
  currencyUomId: string;
}

interface InvoiceAdjustment {
  adjustmentId: string;
  type: InvoiceAdjustmentType;
  amount: Money;
  reason: string;
  authorizedBy: string;
  appliedAt: string; // ISO 8601 UTC
}

interface InvoiceResponse {
  invoiceId: string;
  status: InvoiceStatus;
  traceability: InvoiceTraceability;
  totals: InvoiceTotals;
  adjustments: InvoiceAdjustment[];
  finalizedBy?: string;
  finalizedAt?: string; // ISO 8601 UTC
  issuedAt?: string; // ISO 8601 UTC
}
```

### 1. Generate Invoice Draft from Completed Workorder (Story 43/149)

**Endpoint:** `POST http://localhost:8080/v1/workexec/workorders/{workorderId}/generate-invoice`

**Description:** Generate an invoice draft from a completed workorder.

**Path Parameters:**

- `workorderId` (opaque string, required)

**Request Body (optional):**

```ts
interface GenerateInvoiceRequest {
  idempotencyKey?: string;
}
```

Body may be empty (`{}` or no body). Clients may provide `idempotencyKey` in body for compatibility; gateway/header-based `Idempotency-Key` remains the preferred idempotency mechanism.

**Success Response (200 OK | 201 Created):**

- `InvoiceResponse` with `status: 'DRAFT'`
- Includes calculated totals (`subtotal`, `taxAmount`, `feesAmount`, `total`)
- Includes traceability (`workorderId`, `estimateId`, `approvalId`)

**Behavioral Assertions:**

- Allowed only when source workorder is in `COMPLETED` state; otherwise return `INVALID_STATE` (409).
- Idempotent for the same workorder + idempotency key. Retries return the same `invoiceId` and payload.
- Caller must be authorized for invoice generation; unauthorized calls return `FORBIDDEN` (403).
- Tax/fee/total calculation (Story 44) executes in this flow before the response is returned.
- Traceability links (Story 45) are required in every success response.

**Error Responses:**

- `404 NOT_FOUND` — workorder does not exist
- `409 INVALID_STATE` — workorder not eligible for invoice generation
- `400 VALIDATION_ERROR` — invalid idempotency key payload
- `403 FORBIDDEN` — missing authority

### 2. Add Authorized Adjustment to Invoice Draft (Story 46/146)

**Endpoint:** `POST http://localhost:8080/v1/workexec/invoices/{invoiceId}/adjustments`

**Description:** Apply an authorized adjustment to an invoice draft before finalization.

**Path Parameters:**

- `invoiceId` (opaque string, required)

**Request Body:**

```ts
interface AddInvoiceAdjustmentRequest {
  type: 'discount' | 'fee' | 'correction';
  amount: number;
  reason: string;
  authorizedBy: string;
}
```

**Success Response (200 OK):**

- `InvoiceResponse` with updated `adjustments` and recalculated `totals`

**Behavioral Assertions:**

- Allowed only when invoice status is `DRAFT`; otherwise return `INVALID_STATE` (409).
- `authorizedBy` is required and must resolve to a principal with adjustment authority.
- `reason` must be non-blank.
- `amount` must be a valid decimal; backend applies sign/semantic rules by adjustment `type`.
- Recalculates taxes, fees, and total after applying the adjustment.

**Error Responses:**

- `404 NOT_FOUND` — invoice not found
- `409 INVALID_STATE` — invoice already finalized/issued
- `400 VALIDATION_ERROR` — invalid `type`, `amount`, `reason`, or `authorizedBy`
- `403 FORBIDDEN` — caller lacks adjustment authority

### 3. Finalize and Issue Invoice (Story 47/145)

**Endpoint:** `POST http://localhost:8080/v1/workexec/invoices/{invoiceId}/finalize`

**Description:** Finalize an invoice and mark it as issued. No further adjustments are allowed after success.

**Path Parameters:**

- `invoiceId` (opaque string, required)

**Request Body (optional):**

```ts
interface FinalizeInvoiceRequest {
  finalizedBy?: string;
  finalizedAt?: string; // ISO 8601 UTC
}
```

Body may be empty (`{}` or no body). If omitted, backend derives finalization actor/timestamp from authenticated principal and server time.

**Success Response (200 OK):**

- `InvoiceResponse` with `status: 'ISSUED'` and `issuedAt`

**Behavioral Assertions:**

- Allowed only when invoice status is `DRAFT`; otherwise return `INVALID_STATE` (409).
- Finalization is idempotent: retrying finalize on the same already issued invoice returns the same issued invoice representation.
- Finalized invoices become immutable for adjustments and draft mutations.
- Caller must be authorized for invoice issuance; unauthorized calls return `FORBIDDEN` (403).

**Error Responses:**

- `404 NOT_FOUND` — invoice not found
- `409 INVALID_STATE` — invoice cannot be finalized from current status
- `400 VALIDATION_ERROR` — invalid `finalizedAt` format or invalid request payload
- `403 FORBIDDEN` — missing authority to finalize

### CAP-007 Notes

- This section is contract design only; endpoint implementation and provider tests are deferred to Phase 3-4.
- Public API contract remains gateway-facing even when internal service delegation changes.
- Existing error envelope rules from this guide remain mandatory for all CAP-007 endpoints.

---

## Change Requests (Additional Work Requests)
<!-- contract-status: stable-for-ui -->
<!-- anchor: cap-005-story-156 -->

Change requests enable technicians to request authorization for additional work discovered during workorder execution. All items added via change requests start with `PENDING_APPROVAL` status until the service advisor approves or declines the request.

### Change Request Endpoints

#### 1. Create Change Request

`POST http://localhost:8080/workorder/v1/workorders/{workorderId}/changeRequests`

**Headers:**

- `Idempotency-Key` (optional, but recommended): Client-generated unique string to ensure idempotent creation
- **Note:** Idempotency implementation is currently NOT present in the controller. Clients should generate unique keys, but the backend does not yet enforce idempotency. Track implementation via follow-up issue.

**Path Parameters:**

- `workorderId` (UUID, required): ID of the work order

**Request Body (CreateChangeRequestDTO):**

```json
{
  "requestedByUserId": "550e8400-e29b-41d4-a716-446655440002",
  "description": "Technician discovered brake rotors below minimum thickness during pad replacement",
  "isEmergencyException": false,
  "exceptionReason": null,
  "services": [
    {
      "serviceEntityId": "550e8400-e29b-41d4-a716-446655440010",
      "quantity": 1,
      "isEmergencySafety": false,
      "photoEvidenceUrl": null,
      "emergencyNotes": null,
      "photoNotPossible": false
    }
  ],
  "parts": [
    {
      "productEntityId": "550e8400-e29b-41d4-a716-446655440020",
      "quantity": 2,
      "isEmergencySafety": false,
      "photoEvidenceUrl": "https://example.com/photos/brake-rotor-001.jpg",
      "emergencyNotes": null,
      "photoNotPossible": false
    }
  ]
}
```

**Request Body Rules:**

- `description` (required): Must not be blank
- At least one of `services` or `parts` must be provided with items
- `isEmergencyException`: If true, emergency documentation (photoEvidenceUrl or photoNotPossible flag + emergencyNotes) is required
- Items will be created with `status: PENDING_APPROVAL` until approved

**Success Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440111",
  "workorderId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedByUserId": "550e8400-e29b-41d4-a716-446655440002",
  "requestedAt": "2026-02-15T10:30:00Z",
  "status": "AWAITING_ADVISOR_REVIEW",
  "description": "Technician discovered brake rotors below minimum thickness during pad replacement",
  "isEmergencyException": false,
  "exceptionReason": null,
  "approvalNote": null,
  "isApprovalGated": true,
  "approvedAt": null,
  "approvedBy": null,
  "declinedAt": null
}
```

**Error Responses:**

- `400 Bad Request` — Missing description, no items provided, or validation failed

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Description is required for change request",
    "correlationId": "abc123",
    "fieldErrors": [
      {
        "field": "description",
        "message": "must not be blank"
      }
    ]
  }
  ```

- `404 Not Found` — Work order not found

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Work order not found: {workorderId}",
    "correlationId": "abc124"
  }
  ```

- `409 Conflict` — Work order not in valid state (INVALID_STATE)

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Work order must be in WORK_IN_PROGRESS status to create change request. Current status: DRAFT",
    "correlationId": "abc125"
  }
  ```

**Business Rules:**

- Work order must be in `WORK_IN_PROGRESS` status
- All added items start with `status: PENDING_APPROVAL`
- Emergency/safety items require photo evidence or explicit acknowledgment that photo is not possible

#### 2. Get Change Requests by Workorder

`GET http://localhost:8080/workorder/v1/workorders/{workorderId}/changeRequests`

**Path Parameters:**

- `workorderId` (UUID, required): ID of the work order

**Success Response (200 OK):**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440111",
    "workorderId": "550e8400-e29b-41d4-a716-446655440000",
    "requestedByUserId": "550e8400-e29b-41d4-a716-446655440002",
    "requestedAt": "2026-02-15T10:30:00Z",
    "status": "AWAITING_ADVISOR_REVIEW",
    "description": "Technician discovered brake rotors below minimum thickness",
    "isEmergencyException": false,
    "isApprovalGated": true,
    "approvedAt": null,
    "approvedBy": null,
    "declinedAt": null
  }
]
```

#### 3. Get Change Request by ID

`GET http://localhost:8080/workorder/v1/workorders/changeRequests/{changeId}`

**Path Parameters:**

- `changeId` (UUID, required): ID of the change request

**Success Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440111",
  "workorderId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedByUserId": "550e8400-e29b-41d4-a716-446655440002",
  "requestedAt": "2026-02-15T10:30:00Z",
  "status": "AWAITING_ADVISOR_REVIEW",
  "description": "Technician discovered brake rotors below minimum thickness",
  "isEmergencyException": false,
  "isApprovalGated": true
}
```

**Error Response:**

- `404 Not Found` — Change request not found

#### 4. Approve Change Request

`POST http://localhost:8080/workorder/v1/workorders/changeRequests/{changeId}/approve`

**Path Parameters:**

- `changeId` (UUID, required): ID of the change request to approve

**Request Body (ApproveChangeRequestDTO):**

```json
{
  "approvedBy": "550e8400-e29b-41d4-a716-446655440003",
  "approvalNote": "Approved: brake rotors required for safe operation"
}
```

**Request Body Rules:**

- `approvalNote` (required): Must not be blank; serves as approval artifact

**Success Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440111",
  "workorderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "APPROVED",
  "approvedAt": "2026-02-15T10:45:00Z",
  "approvedBy": "550e8400-e29b-41d4-a716-446655440003",
  "approvalNote": "Approved: brake rotors required for safe operation"
}
```

**Error Responses:**

- `400 Bad Request` — Invalid state or missing approval note

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Change request cannot be approved in current state: APPROVED",
    "correlationId": "abc126"
  }
  ```

- `404 Not Found` — Change request not found

**Business Rules:**

- Change request must be in `AWAITING_ADVISOR_REVIEW` status
- Approval note is mandatory
- Items transition from `PENDING_APPROVAL` to `READY_TO_EXECUTE`
- Creates immutable `ApprovalRecord` for audit trail

#### 5. Decline Change Request

`POST http://localhost:8080/workorder/v1/workorders/changeRequests/{changeId}/decline`

**Path Parameters:**

- `changeId` (UUID, required): ID of the change request to decline

**Request Body (DeclineChangeRequestDTO):**

```json
{
  "approvalNote": "Customer declined additional brake rotor service due to budget constraints"
}
```

**Request Body Rules:**

- `approvalNote` (required): Must not be blank; records decline decision

**Success Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440111",
  "workorderId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DECLINED",
  "declinedAt": "2026-02-15T10:50:00Z",
  "approvalNote": "Customer declined additional brake rotor service due to budget constraints"
}
```

**Error Responses:**

- `400 Bad Request` — Invalid state or missing note
- `404 Not Found` — Change request not found

**Business Rules:**

- Change request must be in `AWAITING_ADVISOR_REVIEW` status
- Items transition from `PENDING_APPROVAL` to `CANCELLED` (not billable)
- If items are emergency/safety, customer denial acknowledgment is required before workorder can be closed

#### 6. Apply Emergency Override

`POST http://localhost:8080/workorder/v1/workorders/changeRequests/{changeId}/emergency-override`

**Path Parameters:**

- `changeId` (UUID, required): ID of the change request

**Request Body (EmergencyOverrideDTO):**

```json
{
  "managerId": "550e8400-e29b-41d4-a716-446655440004",
  "exceptionReason": "Safety-critical brake failure requires immediate manager approval"
}
```

**Request Body Rules:**

- `exceptionReason` (required): Must not be blank; documents why emergency override is needed

**Success Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440111",
  "status": "APPROVED",
  "approvedAt": "2026-02-15T11:00:00Z",
  "approvedBy": "550e8400-e29b-41d4-a716-446655440004",
  "isEmergencyException": true,
  "exceptionReason": "Safety-critical brake failure requires immediate manager approval"
}
```

**Error Responses:**

- `400 Bad Request` — Invalid state or missing reason
- `403 Forbidden` — Insufficient permissions (Manager role required)
- `404 Not Found` — Change request not found

**Business Rules:**

- Requires `workorder:change_request:emergency_override` permission (Manager role)
- Items transition from `PENDING_APPROVAL` to `READY_TO_EXECUTE`
- Creates audit trail with emergency override flag

### Change Request Status Enum

- `AWAITING_ADVISOR_REVIEW` — Initial state after creation
- `APPROVED` — Service advisor approved
- `DECLINED` — Service advisor declined
- `EMERGENCY_OVERRIDE_APPLIED` — Manager applied emergency override

### Known Limitations

- **Idempotency:** `Idempotency-Key` header is currently NOT enforced in the controller. Clients should still provide it for future compatibility, but duplicate submissions will create separate change requests. Track implementation via follow-up issue.
- **Item Details:** The response does not include the list of services/parts; clients must query workorder items separately using `changeRequestId` filter.

---

## Approval & Customer Signature
<!-- contract-status: draft -->
<!-- anchor: cap-005-approval-signature -->

**ApproveEstimateRequest (Confirmed from EstimateController):**

```
{
  "customerId": opaque string (required) — Must match estimate customer
  "signatureData": String (base64 PNG image)
  "signatureMimeType": String (e.g., "image/png")
  "signerName": String (optional)
  "notes": String (optional)
}
```

**Response after Approval:**

```
EstimateDTO {
  "id": opaque string
  "estimateNumber": String (e.g., "EST-2024-1001")
  "status": "APPROVED"
  "locationId": opaque string
  "vehicleId": opaque string
  "customerId": opaque string
  "approvedAt": ISO 8601 timestamp
  "approvedBy": opaque string (user ID)
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
<!-- contract-status: draft -->
<!-- anchor: cap-005-selective-approval -->

The approval request supports **optional selective line item approval/rejection**:

```json
{
   "customerId": "550e8400-e29b-41d4-a716-446655440001",
   "signatureData": "data:image/png;base64,iVBORw0...",
   "signatureMimeType": "image/png",
   "signerName": "John Doe",
   "lineItemApprovals": [
      {
         "lineItemId": "550e8400-e29b-41d4-a716-446655440010",
         "approved": true,
         "notes": "Customer approved oil change"
      },
      {
         "lineItemId": "550e8400-e29b-41d4-a716-446655440011",
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
<!-- contract-status: draft -->
<!-- anchor: cap-005-po-enforcement -->

For **commercial accounts** with purchase order enforcement enabled:

**Field:** `purchaseOrderNumber` (String, conditionally required)

**Validation Rules:**

- Required when customer billing rules indicate `purchaseOrderRequired=true`
- Format: 3-64 characters, pattern `^[A-Za-z0-9][A-Za-z0-9._-]*$`
- Backend queries billing rules via: `GET /crm/v1/accounts/parties/{partyId}/billingRules`

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

## Technician Assignment (CAP-005 Story #161)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-161 -->

### 1. Assign Technician to Workorder

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/technician`

**Description:** Assign a technician to a work order. This operation is typically performed by a service advisor or shop manager when allocating work.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key to ensure idempotent assignment

**Request Body:**

```json
{
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "assignedByUserId": "550e8400-e29b-41d4-a716-446655440000",
  "notes": "Assigned to senior tech for brake system work"
}
```

**Request Fields:**

- `technicianId` (opaque string, required) — ID of the technician to assign
- `assignedByUserId` (opaque string, optional) — User ID performing the assignment (defaults from X-User-Id header if not provided)
- `notes` (String, optional) — Assignment notes or reason

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "technicianName": "John Smith",
  "assignedAt": "2024-01-27T14:30:00Z",
  "assignedBy": "550e8400-e29b-41d4-a716-446655440000",
  "previousTechnicianId": null,
  "status": "ASSIGNED",
  "message": "Technician assigned successfully"
}
```

**Response Fields:**

- `workorderId` (opaque string) — ID of the work order
- `technicianId` (opaque string) — ID of the assigned technician
- `technicianName` (String) — Display name of the technician
- `assignedAt` (ISO 8601 UTC) — Timestamp of assignment
- `assignedBy` (opaque string) — User who performed the assignment
- `previousTechnicianId` (opaque string, nullable) — Previous technician if reassignment
- `status` (String) — Current work order status after assignment
- `message` (String) — Operation result message

**Error Responses:**

- **404 Not Found** — Work order or technician not found

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Workorder with ID 550e8400-e29b-41d4-a716-446655440001 not found",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

- **400 Bad Request** — Invalid state transition

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Cannot assign technician: workorder must be in APPROVED status",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

- **403 Forbidden** — Permission denied

  ```json
  {
    "code": "FORBIDDEN",
    "message": "User lacks authority to assign technicians",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Status Transition Rules:**

- Assignment is allowed from `APPROVED` status
- Target status after assignment: `ASSIGNED`
- Reassignment is allowed from any status where technician is already assigned

**Business Rules:**

- Users must have `workorder:workorder:assign-technician` authority
- Reassignment automatically records the previous technician ID
- Assignment creates a state transition record with the assignment details

---

### 2. Reassign Technician

**Endpoint:** `PUT http://localhost:8080/workorder/v1/workorders/{workorderId}/technician`

**Description:** Reassign a work order to a different technician. Similar to initial assignment but explicitly indicates replacement.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "newTechnicianId": "550e8400-e29b-41d4-a716-446655440051",
  "reassignedByUserId": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "Original technician unavailable, reassigning to available tech",
  "notifyPreviousTechnician": true
}
```

**Request Fields:**

- `newTechnicianId` (opaque string, required) — ID of the new technician
- `reassignedByUserId` (opaque string, optional) — User performing reassignment
- `reason` (String, optional) — Reason for reassignment
- `notifyPreviousTechnician` (Boolean, default=false) — Whether to send notification to previous technician

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "newTechnicianId": "550e8400-e29b-41d4-a716-446655440051",
  "newTechnicianName": "Jane Doe",
  "previousTechnicianId": "550e8400-e29b-41d4-a716-446655440050",
  "previousTechnicianName": "John Smith",
  "reassignedAt": "2024-01-27T15:30:00Z",
  "reassignedBy": "550e8400-e29b-41d4-a716-446655440000",
  "reason": "Original technician unavailable, reassigning to available tech",
  "message": "Technician reassigned successfully"
}
```

**Error Responses:** Same as assignment endpoint

**Business Rules:**

- Reassignment requires an existing technician assignment
- Any labor already recorded remains attributed to the original technician
- Reassignment creates an audit trail entry

---

### 3. Get Current Assignment

**Endpoint:** `GET http://localhost:8080/workorder/v1/workorders/{workorderId}/technician`

**Description:** Retrieve the current technician assignment for a work order.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "technicianName": "John Smith",
  "technicianCertifications": ["ASE Master Technician", "Brake Specialist"],
  "assignedAt": "2024-01-27T14:30:00Z",
  "assignedBy": "550e8400-e29b-41d4-a716-446655440000",
  "currentStatus": "WORK_IN_PROGRESS",
  "assignmentHistory": [
    {
      "technicianId": "550e8400-e29b-41d4-a716-446655440050",
      "technicianName": "John Smith",
      "assignedAt": "2024-01-27T14:30:00Z",
      "assignedBy": "550e8400-e29b-41d4-a716-446655440000",
      "unassignedAt": null,
      "reason": null
    }
  ]
}
```

**Response Fields:**

- `workorderId` (opaque string) — ID of the work order
- `technicianId` (opaque string) — Current technician ID
- `technicianName` (String) — Display name of current technician
- `technicianCertifications` (String[], optional) — List of certifications
- `assignedAt` (ISO 8601 UTC) — When current technician was assigned
- `assignedBy` (opaque string) — User who assigned current technician
- `currentStatus` (String) — Current work order status
- `assignmentHistory` (AssignmentHistoryEntry[]) — Full assignment history

**AssignmentHistoryEntry Fields:**

- `technicianId` (opaque string) — Technician ID for this assignment period
- `technicianName` (String) — Technician display name
- `assignedAt` (ISO 8601 UTC) — Assignment timestamp
- `assignedBy` (opaque string) — Assigning user ID
- `unassignedAt` (ISO 8601 UTC, nullable) — When technician was unassigned (null for current)
- `reason` (String, optional) — Reason for reassignment/unassignment

**Error Responses:**

- **404 Not Found** — Work order not found or no assignment exists

  ```json
  {
    "code": "NOT_FOUND",
    "message": "No technician assigned to workorder 550e8400-e29b-41d4-a716-446655440001",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Returns current assignment if exists
- Assignment history ordered newest first
- Users must have `workorder:workorder:view` authority

---

## Labor Recording (CAP-005 Story #159)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-159 -->

### 1. Start Labor Session

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/services/{serviceId}/labor/start`

**Description:** Record the start of labor on a specific service item. Creates a labor session that tracks time until explicitly stopped.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `serviceId` (opaque string, required) — ID of the service item being worked on

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "startTime": "2024-01-27T14:30:00Z",
  "notes": "Beginning brake pad replacement"
}
```

**Request Fields:**

- `technicianId` (opaque string, required) — ID of technician performing work
- `startTime` (ISO 8601 UTC, optional) — Labor start time (defaults to request time if not provided)
- `notes` (String, optional) — Session notes

**Success Response (200 OK):**

```json
{
  "laborSessionId": "550e8400-e29b-41d4-a716-446655440100",
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "serviceId": "550e8400-e29b-41d4-a716-446655440010",
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "startTime": "2024-01-27T14:30:00Z",
  "status": "IN_PROGRESS",
  "message": "Labor session started successfully"
}
```

**Response Fields:**

- `laborSessionId` (opaque string) — Unique ID for this labor session
- `workorderId` (opaque string) — Work order ID
- `serviceId` (opaque string) — Service item ID
- `technicianId` (opaque string) — Technician performing work
- `startTime` (ISO 8601 UTC) — When labor started
- `status` (String) — Session status (IN_PROGRESS)
- `message` (String) — Operation result

**Error Responses:**

- **400 Bad Request** — Active session already exists

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Active labor session already exists for service 550e8400-e29b-41d4-a716-446655440010",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

- **409 Conflict** — Invalid state

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Service must be in READY_TO_EXECUTE or IN_PROGRESS status to start labor",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Service item must be in `READY_TO_EXECUTE` or `IN_PROGRESS` status
- Only one active labor session allowed per service item at a time
- Technician must match workorder assignment (unless supervisor override)
- Starting labor automatically transitions service status to `IN_PROGRESS`

---

### 2. Stop Labor Session

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/services/{serviceId}/labor/stop`

**Description:** Stop an active labor session and record the total time worked.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `serviceId` (opaque string, required) — ID of the service item

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "laborSessionId": "550e8400-e29b-41d4-a716-446655440100",
  "stopTime": "2024-01-27T16:30:00Z",
  "actualHours": 2.0,
  "completionStatus": "COMPLETED",
  "notes": "Brake pads replaced successfully, tested"
}
```

**Request Fields:**

- `laborSessionId` (opaque string, required) — ID of the session to stop
- `stopTime` (ISO 8601 UTC, optional) — Labor stop time (defaults to request time)
- `actualHours` (BigDecimal, optional) — Actual hours worked (calculated from start/stop if not provided)
- `completionStatus` (String, required) — COMPLETED, PARTIAL, PAUSED
- `notes` (String, optional) — Completion notes

**Success Response (200 OK):**

```json
{
  "laborSessionId": "550e8400-e29b-41d4-a716-446655440100",
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "serviceId": "550e8400-e29b-41d4-a716-446655440010",
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "startTime": "2024-01-27T14:30:00Z",
  "stopTime": "2024-01-27T16:30:00Z",
  "actualHours": 2.0,
  "elapsedHours": 2.0,
  "estimatedHours": 1.5,
  "status": "COMPLETED",
  "varianceHours": 0.5,
  "message": "Labor session completed successfully"
}
```

**Response Fields:**

- `laborSessionId` (opaque string) — Session ID
- `workorderId` (opaque string) — Work order ID
- `serviceId` (opaque string) — Service item ID
- `technicianId` (opaque string) — Technician ID
- `startTime` (ISO 8601 UTC) — Session start
- `stopTime` (ISO 8601 UTC) — Session stop
- `actualHours` (BigDecimal) — Recorded actual hours
- `elapsedHours` (BigDecimal) — Calculated elapsed time
- `estimatedHours` (BigDecimal) — Original estimate
- `status` (String) — Session status (COMPLETED)
- `varianceHours` (BigDecimal) — Difference from estimate (actual - estimated)
- `message` (String) — Operation result

**Error Responses:**

- **404 Not Found** — Session not found or not active

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Active labor session 550e8400-e29b-41d4-a716-446655440100 not found",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Session must be in `IN_PROGRESS` status
- Stop time must be after start time
- If `completionStatus` is `COMPLETED`, service status transitions to `COMPLETED`
- If `completionStatus` is `PAUSED`, session can be resumed later
- Variance tracking enables labor efficiency analysis

---

### 3. Get Labor History for Service

**Endpoint:** `GET http://localhost:8080/workorder/v1/workorders/{workorderId}/services/{serviceId}/labor`

**Description:** Retrieve all labor sessions for a specific service item, including active and completed sessions.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `serviceId` (opaque string, required) — ID of the service item

**Success Response (200 OK):**

```json
{
  "serviceId": "550e8400-e29b-41d4-a716-446655440010",
  "serviceName": "Brake Pad Replacement",
  "estimatedHours": 1.5,
  "totalActualHours": 2.0,
  "totalVarianceHours": 0.5,
  "sessions": [
    {
      "laborSessionId": "550e8400-e29b-41d4-a716-446655440100",
      "technicianId": "550e8400-e29b-41d4-a716-446655440050",
      "technicianName": "John Smith",
      "startTime": "2024-01-27T14:30:00Z",
      "stopTime": "2024-01-27T16:30:00Z",
      "actualHours": 2.0,
      "status": "COMPLETED",
      "notes": "Brake pads replaced successfully, tested"
    }
  ],
  "activeSessions": []
}
```

**Response Fields:**

- `serviceId` (opaque string) — Service item ID
- `serviceName` (String) — Service description
- `estimatedHours` (BigDecimal) — Estimated labor time
- `totalActualHours` (BigDecimal) — Sum of all completed session hours
- `totalVarianceHours` (BigDecimal) — Total variance from estimate
- `sessions` (LaborSessionDTO[]) — All labor sessions (completed)
- `activeSessions` (LaborSessionDTO[]) — Currently active sessions

**LaborSessionDTO Fields:**

- `laborSessionId` (opaque string)
- `technicianId` (opaque string)
- `technicianName` (String)
- `startTime` (ISO 8601 UTC)
- `stopTime` (ISO 8601 UTC, nullable for active)
- `actualHours` (BigDecimal, nullable for active)
- `status` (String) — IN_PROGRESS, COMPLETED, PAUSED
- `notes` (String, optional)

**Error Responses:**

- **404 Not Found** — Service not found

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Service 550e8400-e29b-41d4-a716-446655440010 not found",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Returns all sessions ordered by start time (newest first)
- Active sessions show elapsed time but no stop time
- Users must have `workorder:workorder:view` authority

---

### 4. Update Labor Hours

**Endpoint:** `PATCH http://localhost:8080/workorder/v1/workorders/{workorderId}/services/{serviceId}/labor/{sessionId}`

**Description:** Adjust recorded labor hours after session completion (e.g., supervisor correction).

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `serviceId` (opaque string, required) — ID of the service item
- `sessionId` (opaque string, required) — ID of the labor session to update

**Request Body:**

```json
{
  "actualHours": 1.75,
  "reason": "Adjusted to exclude lunch break (not billable)",
  "adjustedByUserId": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Request Fields:**

- `actualHours` (BigDecimal, required) — Corrected actual hours
- `reason` (String, required) — Reason for adjustment
- `adjustedByUserId` (opaque string, optional) — User making adjustment

**Success Response (200 OK):**

```json
{
  "laborSessionId": "550e8400-e29b-41d4-a716-446655440100",
  "previousActualHours": 2.0,
  "updatedActualHours": 1.75,
  "adjustedAt": "2024-01-27T17:00:00Z",
  "adjustedBy": "550e8400-e29b-41d4-a716-446655440001",
  "reason": "Adjusted to exclude lunch break (not billable)",
  "message": "Labor hours adjusted successfully"
}
```

**Error Responses:**

- **403 Forbidden** — Insufficient authority to adjust

  ```json
  {
    "code": "FORBIDDEN",
    "message": "User lacks authority to adjust labor hours",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Only users with `workorder:labor:adjust` authority can update hours
- Session must be in `COMPLETED` status
- Adjustments are logged in audit trail
- Original hours are preserved for comparison

---

## Parts Consumption (CAP-005 Story #158)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-158 -->

### 1. Issue Parts to Workorder

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/issue`

**Description:** Issue parts from inventory to a work order, reserving them for consumption.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "parts": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440200",
      "productEntityId": "550e8400-e29b-41d4-a716-446655440201",
      "quantityToIssue": 2,
      "locationId": "550e8400-e29b-41d4-a716-446655440003",
      "binLocation": "A-12-3"
    },
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "productEntityId": "550e8400-e29b-41d4-a716-446655440203",
      "quantityToIssue": 1,
      "locationId": "550e8400-e29b-41d4-a716-446655440003",
      "binLocation": "B-05-1"
    }
  ],
  "issuedByUserId": "550e8400-e29b-41d4-a716-446655440050",
  "notes": "Parts issued for brake service"
}
```

**Request Fields:**

- `parts` (PartIssueRequest[], required) — Array of parts to issue
  - `partId` (opaque string, required) — Workorder part line item ID
  - `productEntityId` (opaque string, required) — Product/SKU ID from catalog
  - `quantityToIssue` (BigDecimal, required) — Quantity to issue from inventory
  - `locationId` (opaque string, required) — Inventory location ID
  - `binLocation` (String, optional) — Physical bin/rack location
- `issuedByUserId` (opaque string, optional) — User issuing parts
- `notes` (String, optional) — Issue notes

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "issuedAt": "2024-01-27T14:45:00Z",
  "issuedBy": "550e8400-e29b-41d4-a716-446655440050",
  "partsIssued": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440200",
      "productEntityId": "550e8400-e29b-41d4-a716-446655440201",
      "productName": "Brake Rotor - Front",
      "quantityIssued": 2,
      "quantityRemaining": 8,
      "locationId": "550e8400-e29b-41d4-a716-446655440003",
      "status": "ISSUED"
    },
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "productEntityId": "550e8400-e29b-41d4-a716-446655440203",
      "productName": "Brake Pad Set",
      "quantityIssued": 1,
      "quantityRemaining": 5,
      "locationId": "550e8400-e29b-41d4-a716-446655440003",
      "status": "ISSUED"
    }
  ],
  "message": "Parts issued successfully"
}
```

**Response Fields:**

- `workorderId` (opaque string) — Work order ID
- `issuedAt` (ISO 8601 UTC) — Issue timestamp
- `issuedBy` (opaque string) — User who issued parts
- `partsIssued` (PartIssuanceDTO[]) — Details of issued parts
  - `partId` (opaque string) — Workorder part line item ID
  - `productEntityId` (opaque string) — Product ID
  - `productName` (String) — Product description
  - `quantityIssued` (BigDecimal) — Quantity issued
  - `quantityRemaining` (BigDecimal) — Remaining inventory after issue
  - `locationId` (opaque string) — Source location
  - `status` (String) — Part status after issue
- `message` (String) — Operation result

**Error Responses:**

- **409 Conflict** — Insufficient inventory

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Insufficient inventory for product 550e8400-e29b-41d4-a716-446655440201 at location 550e8400-e29b-41d4-a716-446655440003",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002",
    "fieldErrors": [
      {
        "field": "parts[0].quantityToIssue",
        "message": "Requested: 2, Available: 1"
      }
    ]
  }
  ```

- **400 Bad Request** — Invalid quantity or product not found

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Invalid quantity: must be greater than zero",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Parts must exist in workorder part list with `OPEN` status
- Inventory availability checked before issuance
- Issued parts reserved/allocated to workorder
- Inventory transaction created for audit trail
- Part status transitions to `ISSUED` after successful issuance

---

### 2. Consume Parts on Workorder

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/consume`

**Description:** Record actual consumption of parts on a work order. This may differ from authorized/issued quantity.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "parts": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440200",
      "quantityConsumed": 2,
      "technicianId": "550e8400-e29b-41d4-a716-446655440050",
      "serviceId": "550e8400-e29b-41d4-a716-446655440010"
    },
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "quantityConsumed": 0.75,
      "technicianId": "550e8400-e29b-41d4-a716-446655440050",
      "serviceId": "550e8400-e29b-41d4-a716-446655440010"
    }
  ],
  "consumedAt": "2024-01-27T16:15:00Z",
  "notes": "Brake rotors fully consumed, partial brake fluid used"
}
```

**Request Fields:**

- `parts` (PartConsumptionRequest[], required) — Array of parts consumed
  - `partId` (opaque string, required) — Workorder part line item ID
  - `quantityConsumed` (BigDecimal, required) — Actual quantity consumed
  - `technicianId` (opaque string, optional) — Technician who consumed parts
  - `serviceId` (opaque string, optional) — Service item part was used for
- `consumedAt` (ISO 8601 UTC, optional) — Consumption timestamp
- `notes` (String, optional) — Consumption notes

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "consumedAt": "2024-01-27T16:15:00Z",
  "partsConsumed": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440200",
      "productName": "Brake Rotor - Front",
      "authorizedQuantity": 2,
      "quantityConsumed": 2,
      "quantityRemaining": 0,
      "variance": 0,
      "status": "COMPLETED"
    },
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "productName": "Brake Fluid (DOT 4)",
      "authorizedQuantity": 1,
      "quantityConsumed": 0.75,
      "quantityRemaining": 0.25,
      "variance": -0.25,
      "status": "PARTIAL"
    }
  ],
  "message": "Parts consumption recorded successfully"
}
```

**Response Fields:**

- `workorderId` (opaque string) — Work order ID
- `consumedAt` (ISO 8601 UTC) — Consumption timestamp
- `partsConsumed` (PartConsumptionDTO[]) — Details of consumed parts
  - `partId` (opaque string) — Part line item ID
  - `productName` (String) — Product description
  - `authorizedQuantity` (BigDecimal) — Original authorized quantity
  - `quantityConsumed` (BigDecimal) — Actual quantity consumed
  - `quantityRemaining` (BigDecimal) — Unused quantity (authorized - consumed)
  - `variance` (BigDecimal) — Difference (consumed - authorized)
  - `status` (String) — COMPLETED (fully consumed), PARTIAL (under-consumed)
- `message` (String) — Operation result

**Error Responses:**

- **400 Bad Request** — Consumption exceeds issued quantity

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Consumption exceeds issued quantity for part 550e8400-e29b-41d4-a716-446655440200",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002",
    "fieldErrors": [
      {
        "field": "parts[0].quantityConsumed",
        "message": "Consumed: 3, Issued: 2"
      }
    ]
  }
  ```

**Business Rules:**

- Parts must have been issued before consumption
- Consumption creates inventory transaction (decreases stock)
- Over-consumption requires additional authorization (change request)
- Under-consumption flags potential return to inventory
- Part status transitions based on consumption:
  - `COMPLETED` if fully consumed (quantityConsumed == authorizedQuantity)
  - `PARTIAL` if under-consumed (quantityConsumed < authorizedQuantity)

---

### 3. Return Unused Parts

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/return`

**Description:** Return unused parts to inventory after partial consumption or service completion.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "parts": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "quantityToReturn": 0.25,
      "returnToLocationId": "550e8400-e29b-41d4-a716-446655440003",
      "condition": "NEW",
      "reason": "Partial consumption, returning unused portion"
    }
  ],
  "returnedByUserId": "550e8400-e29b-41d4-a716-446655440050",
  "returnedAt": "2024-01-27T17:00:00Z"
}
```

**Request Fields:**

- `parts` (PartReturnRequest[], required) — Array of parts to return
  - `partId` (opaque string, required) — Workorder part line item ID
  - `quantityToReturn` (BigDecimal, required) — Quantity to return
  - `returnToLocationId` (opaque string, required) — Target inventory location
  - `condition` (String, required) — NEW, USED, DAMAGED
  - `reason` (String, optional) — Return reason
- `returnedByUserId` (opaque string, optional) — User processing return
- `returnedAt` (ISO 8601 UTC, optional) — Return timestamp

**Success Response (200 OK):**

```json
{
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "returnedAt": "2024-01-27T17:00:00Z",
  "returnedBy": "550e8400-e29b-41d4-a716-446655440050",
  "partsReturned": [
    {
      "partId": "550e8400-e29b-41d4-a716-446655440202",
      "productName": "Brake Fluid (DOT 4)",
      "quantityReturned": 0.25,
      "returnToLocationId": "550e8400-e29b-41d4-a716-446655440003",
      "condition": "NEW",
      "inventoryAdjusted": true,
      "status": "RETURNED"
    }
  ],
  "message": "Parts returned to inventory successfully"
}
```

**Response Fields:**

- `workorderId` (opaque string) — Work order ID
- `returnedAt` (ISO 8601 UTC) — Return timestamp
- `returnedBy` (opaque string) — User who processed return
- `partsReturned` (PartReturnDTO[]) — Details of returned parts
  - `partId` (opaque string) — Part line item ID
  - `productName` (String) — Product description
  - `quantityReturned` (BigDecimal) — Quantity returned
  - `returnToLocationId` (opaque string) — Inventory location
  - `condition` (String) — Part condition
  - `inventoryAdjusted` (Boolean) — Whether inventory was updated
  - `status` (String) — RETURNED
- `message` (String) — Operation result

**Error Responses:**

- **400 Bad Request** — Return quantity exceeds unused quantity

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "Return quantity exceeds unused quantity for part 550e8400-e29b-41d4-a716-446655440202",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002",
    "fieldErrors": [
      {
        "field": "parts[0].quantityToReturn",
        "message": "Returning: 0.5, Unused: 0.25"
      }
    ]
  }
  ```

**Business Rules:**

- Parts must have unused quantity (issued - consumed > 0)
- Returns increase inventory at specified location
- Condition code affects inventory status (NEW → available, DAMAGED → quarantine)
- Inventory transaction created for audit trail
- Part status updated based on remaining balance

---

## Part Substitutions and Returns (CAP-005 Story #157)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-157 -->

### 1. Substitute Part on Workorder

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/{partId}/substitute`

**Description:** Substitute one part for another during workorder execution. Common when original part unavailable or customer upgrades.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `partId` (opaque string, required) — ID of the original part to substitute

**Headers:**

- `Idempotency-Key` (String, optional but recommended) — Client-generated unique key

**Request Body:**

```json
{
  "substituteProductEntityId": "550e8400-e29b-41d4-a716-446655440210",
  "substituteQuantity": 2,
  "reason": "UPGRADE",
  "reasonNotes": "Customer requested premium brake rotors",
  "priceAdjustment": 45.00,
  "requiresApproval": true,
  "requestedByUserId": "550e8400-e29b-41d4-a716-446655440050"
}
```

**Request Fields:**

- `substituteProductEntityId` (opaque string, required) — Product ID of substitute part
- `substituteQuantity` (BigDecimal, required) — Quantity of substitute part
- `reason` (String, required) — Substitution reason code:
  - `OUT_OF_STOCK` — Original part unavailable
  - `UPGRADE` — Customer requested upgrade
  - `DOWNGRADE` — Customer requested downgrade
  - `EQUIVALENT_PART` — Equivalent alternative part
  - `DEFECTIVE_ORIGINAL` — Original part defective
  - `OTHER` — Other reason (requiressubstitute notes)
- `reasonNotes` (String, optional) — Additional reason details
- `priceAdjustment` (BigDecimal, required) — Price difference (positive=increase, negative=decrease)
- `requiresApproval` (Boolean, default=true) — Whether substitution requires advisor approval
- `requestedByUserId` (opaque string, optional) — User requesting substitution

**Success Response (200 OK):**

```json
{
  "substitutionId": "550e8400-e29b-41d4-a716-446655440300",
  "workorderId": "550e8400-e29b-41d4-a716-446655440001",
  "originalPartId": "550e8400-e29b-41d4-a716-446655440200",
  "originalProductName": "Standard Brake Rotor",
  "substitutePartId": "550e8400-e29b-41d4-a716-446655440301",
  "substituteProductEntityId": "550e8400-e29b-41d4-a716-446655440210",
  "substituteProductName": "Premium Brake Rotor",
  "substituteQuantity": 2,
  "reason": "UPGRADE",
  "priceAdjustment": 45.00,
  "status": "PENDING_APPROVAL",
  "requestedAt": "2024-01-27T15:30:00Z",
  "requestedBy": "550e8400-e29b-41d4-a716-446655440050",
  "message": "Part substitution requested, awaiting approval"
}
```

**Response Fields:**

- `substitutionId` (opaque string) — Unique substitution request ID
- `workorderId` (opaque string) — Work order ID
- `originalPartId` (opaque string) — Original part line item ID
- `originalProductName` (String) — Original product description
- `substitutePartId` (opaque string) — New substitute part line item ID
- `substituteProductEntityId` (opaque string) — Substitute product ID
- `substituteProductName` (String) — Substitute product description
- `substituteQuantity` (BigDecimal) — Substitute quantity
- `reason` (String) — Substitution reason code
- `priceAdjustment` (BigDecimal) — Price difference
- `status` (String) — PENDING_APPROVAL, APPROVED, DECLINED
- `requestedAt` (ISO 8601 UTC) — Request timestamp
- `requestedBy` (opaque string) — Requesting user ID
- `message` (String) — Operation result

**Error Responses:**

- **404 Not Found** — Original part not found

  ```json
  {
    "code": "NOT_FOUND",
    "message": "Part 550e8400-e29b-41d4-a716-446655440200 not found on workorder",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

- **409 Conflict** — Substitution not allowed in current state

  ```json
  {
    "code": "INVALID_STATE",
    "message": "Cannot substitute part: already consumed or completed",
    "correlationId": "550e8400-e29b-41d4-a716-446655440002"
  }
  ```

**Business Rules:**

- Original part must not be consumed yet
- Price increases require customer/advisor approval
- Price decreases may auto-approve (configurable threshold)
- Substitution creates new part line item, marks original as SUBSTITUTED
- If `requiresApproval=true`, substitute part starts with PENDING_APPROVAL status
- Approval process same as change requests

---

### 2. Approve Part Substitution

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/substitutions/{substitutionId}/approve`

**Description:** Approve a pending part substitution.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `substitutionId` (opaque string, required) — ID of the substitution request

**Request Body:**

```json
{
  "approvedByUserId": "550e8400-e29b-41d4-a716-446655440000",
  "approvalNotes": "Customer approved premium upgrade via phone",
  "customerConfirmed": true
}
```

**Request Fields:**

- `approvedByUserId` (opaque string, optional) — User approving substitution
- `approvalNotes` (String, optional) — Approval notes
- `customerConfirmed` (Boolean, default=false) — Whether customer confirmed change

**Success Response (200 OK):**

```json
{
  "substitutionId": "550e8400-e29b-41d4-a716-446655440300",
  "status": "APPROVED",
  "approvedAt": "2024-01-27T15:45:00Z",
  "approvedBy": "550e8400-e29b-41d4-a716-446655440000",
  "substitutePartId": "550e8400-e29b-41d4-a716-446655440301",
  "substitutePartStatus": "OPEN",
  "message": "Part substitution approved"
}
```

**Response Fields:**

- `substitutionId` (opaque string) — Substitution ID
- `status` (String) — APPROVED
- `approvedAt` (ISO 8601 UTC) — Approval timestamp
- `approvedBy` (opaque string) — Approving user
- `substitutePartId` (opaque string) — Substitute part line item ID
- `substitutePartStatus` (String) — Part status after approval (typically OPEN)
- `message` (String) — Operation result

**Business Rules:**

- User must have `workorder:substitution:approve` authority
- Approval transitions substitute part from PENDING_APPROVAL to OPEN
- Original part marked as SUBSTITUTED and cannot be used
- Price adjustment applied to workorder totals

---

### 3. Decline Part Substitution

**Endpoint:** `POST http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/substitutions/{substitutionId}/decline`

**Description:** Decline a pending part substitution.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order
- `substitutionId` (opaque string, required) — ID of the substitution request

**Request Body:**

```json
{
  "declinedByUserId": "550e8400-e29b-41d4-a716-446655440000",
  "declineReason": "Customer declined upgrade due to cost"
}
```

**Request Fields:**

- `declinedByUserId` (opaque string, optional) — User declining substitution
- `declineReason` (String, required) — Reason for decline

**Success Response (200 OK):**

```json
{
  "substitutionId": "550e8400-e29b-41d4-a716-446655440300",
  "status": "DECLINED",
  "declinedAt": "2024-01-27T15:50:00Z",
  "declinedBy": "550e8400-e29b-41d4-a716-446655440000",
  "originalPartId": "550e8400-e29b-41d4-a716-446655440200",
  "originalPartStatus": "OPEN",
  "message": "Part substitution declined, original part restored"
}
```

**Response Fields:**

- `substitutionId` (opaque string) — Substitution ID
- `status` (String) — DECLINED
- `declinedAt` (ISO 8601 UTC) — Decline timestamp
- `declinedBy` (opaque string) — Declining user
- `originalPartId` (opaque string) — Original part line item ID
- `originalPartStatus` (String) — Original part status (restored to OPEN)
- `message` (String) — Operation result

**Business Rules:**

- Declining removes substitute part line item
- Original part restored to previous status (typically OPEN)
- No price adjustment applied

---

### 4. Get Substitution History

**Endpoint:** `GET http://localhost:8080/workorder/v1/workorders/{workorderId}/parts/substitutions`

**Description:** Retrieve all part substitution requests for a work order.

**Path Parameters:**

- `workorderId` (opaque string, required) — ID of the work order

**Query Parameters:**

- `status` (String, optional) — Filter by status (PENDING_APPROVAL, APPROVED, DECLINED)

**Success Response (200 OK):**

```json
[
  {
    "substitutionId": "550e8400-e29b-41d4-a716-446655440300",
    "workorderId": "550e8400-e29b-41d4-a716-446655440001",
    "originalPartId": "550e8400-e29b-41d4-a716-446655440200",
    "originalProductName": "Standard Brake Rotor",
    "substitutePartId": "550e8400-e29b-41d4-a716-446655440301",
    "substituteProductName": "Premium Brake Rotor",
    "reason": "UPGRADE",
    "priceAdjustment": 45.00,
    "status": "APPROVED",
    "requestedAt": "2024-01-27T15:30:00Z",
    "requestedBy": "550e8400-e29b-41d4-a716-446655440050",
    "approvedAt": "2024-01-27T15:45:00Z",
    "approvedBy": "550e8400-e29b-41d4-a716-446655440000"
  }
]
```

**Response Fields (Array of SubstitutionHistoryDTO):**

- `substitutionId` (opaque string) — Substitution ID
- `workorderId` (opaque string) — Work order ID
- `originalPartId` (opaque string) — Original part ID
- `originalProductName` (String) — Original product name
- `substitutePartId` (opaque string) — Substitute part ID
- `substituteProductName` (String) — Substitute product name
- `reason` (String) — Substitution reason
- `priceAdjustment` (BigDecimal) — Price difference
- `status` (String) — Current status
- `requestedAt` (ISO 8601 UTC) — Request timestamp
- `requestedBy` (opaque string) — Requesting user
- `approvedAt` (ISO 8601 UTC, optional) — Approval timestamp
- `approvedBy` (opaque string, optional) — Approving user
- `declinedAt` (ISO 8601 UTC, optional) — Decline timestamp
- `declinedBy` (opaque string, optional) — Declining user

**Business Rules:**

- Returns all substitutions ordered by requestedAt (newest first)
- Filter by status if specified
- Users must have `workorder:workorder:view` authority

---

## Role-Based Visibility & Capabilities (CAP-005 Story #155)
<!-- contract-status: stable-for-ui -->
<!-- anchor: #cap-005-story-155 -->

### Authorization Matrix

This section documents the authority requirements for each workorder endpoint and the capability signaling fields returned in responses to enable frontend conditional rendering.

#### Required Authorities by Endpoint

| Endpoint | Authority | Description |
|----------|-----------|-------------|
| `GET /workorder/v1/workorders/{id}` | `workorder:workorder:view` | View workorder details |
| `POST /workorder/v1/workorders/{id}/start` | `workorder:workorder:start` | Start work on workorder |
| `POST /workorder/v1/workorders/{id}/technician` | `workorder:workorder:assign-technician` | Assign technician |
| `PUT /workorder/v1/workorders/{id}/technician` | `workorder:workorder:assign-technician` | Reassign technician |
| `POST /workorder/v1/workorders/{id}/services/{serviceId}/labor/start` | `workorder:labor:record` | Start labor session |
| `POST /workorder/v1/workorders/{id}/services/{serviceId}/labor/stop` | `workorder:labor:record` | Stop labor session |
| `PATCH /workorder/v1/workorders/{id}/services/{serviceId}/labor/{sessionId}` | `workorder:labor:adjust` | Adjust labor hours |
| `POST /workorder/v1/workorders/{id}/parts/issue` | `workorder:parts:issue` | Issue parts from inventory |
| `POST /workorder/v1/workorders/{id}/parts/consume` | `workorder:parts:consume` | Consume parts |
| `POST /workorder/v1/workorders/{id}/parts/return` | `workorder:parts:return` | Return unused parts |
| `POST /workorder/v1/workorders/{id}/parts/{partId}/substitute` | `workorder:substitution:request` | Request part substitution |
| `POST /workorder/v1/workorders/{id}/parts/substitutions/{substitutionId}/approve` | `workorder:substitution:approve` | Approve substitution |
| `POST /workorder/v1/workorders/{id}/parts/substitutions/{substitutionId}/decline` | `workorder:substitution:approve` | Decline substitution |
| `POST /workorder/v1/workorders/{id}/changeRequests` | `workorder:changerequest:create` | Create change request |
| `POST /workorder/v1/workorders/{id}/changeRequests/{changeRequestId}/approve` | `workorder:changerequest:approve` | Approve change request |
| `POST /workorder/v1/workorders/{id}/changeRequests/{changeRequestId}/decline` | `workorder:changerequest:approve` | Decline change request |
| `GET /workorder/v1/workorders/{id}/transitions` | `workorder:workorder:view` | View transition history |
| `GET /workorder/v1/workorders/{id}/snapshots` | `workorder:workorder:view` | View snapshot history |

#### Capability Signaling Fields

All workorder-related GET endpoint responses SHOULD include a `capabilities` object with boolean flags indicating what actions the current user can perform. This enables frontend UI gating without redundant permission checks.

**Example Capabilities Object:**

```json
{
  "capabilities": {
    "canView": true,
    "canStart": false,
    "canAssignTechnician": true,
    "canRecordLabor": false,
    "canAdjustLabor": false,
    "canIssueParts": true,
    "canConsumeParts": false,
    "canReturnParts": true,
    "canRequestSubstitution": false,
    "canApproveSubstitution": true,
    "canCreateChangeRequest": false,
    "canApproveChangeRequest": true
  }
}
```

**Capability Field Definitions:**

- `canView` (Boolean) — User can view workorder details
- `canStart` (Boolean) — User can start workorder (status-dependent)
- `canAssignTechnician` (Boolean) — User can assign/reassign technicians
- `canRecordLabor` (Boolean) — User can start/stop labor sessions
- `canAdjustLabor` (Boolean) — User can adjust labor hours after completion
- `canIssueParts` (Boolean) — User can issue parts from inventory
- `canConsumeParts` (Boolean) — User can record parts consumption
- `canReturnParts` (Boolean) — User can return unused parts
- `canRequestSubstitution` (Boolean) — User can request part substitutions
- `canApproveSubstitution` (Boolean) — User can approve/decline substitutions
- `canCreateChangeRequest` (Boolean) — User can create change requests
- `canApproveChangeRequest` (Boolean) — User can approve/decline change requests

---

### Enhanced Workorder Response with Capabilities

**Endpoint:** `GET http://localhost:8080/workorder/v1/workorders/{workorderId}`

**Success Response (200 OK) with Capabilities:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "workorderNumber": "WO-2024-5001",
  "status": "WORK_IN_PROGRESS",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "vehicleId": "550e8400-e29b-41d4-a716-446655440002",
  "locationId": "550e8400-e29b-41d4-a716-446655440003",
  "technicianId": "550e8400-e29b-41d4-a716-446655440050",
  "technicianName": "John Smith",
  "estimateId": "550e8400-e29b-41d4-a716-446655440010",
  "services": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440020",
      "serviceEntityId": "550e8400-e29b-41d4-a716-446655440021",
      "serviceName": "Brake Pad Replacement",
      "status": "IN_PROGRESS",
      "estimatedHours": 1.5,
      "actualHours": 0.5
    }
  ],
  "parts": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440200",
      "productEntityId": "550e8400-e29b-41d4-a716-446655440201",
      "productName": "Brake Rotor - Front",
      "authorizedQuantity": 2,
      "issuedQuantity": 2,
      "consumedQuantity": 0,
      "status": "ISSUED"
    }
  ],
  "createdAt": "2024-01-27T10:00:00Z",
  "approvedAt": "2024-01-27T10:30:00Z",
  "startedAt": "2024-01-27T14:30:00Z",
  "capabilities": {
    "canView": true,
    "canStart": false,
    "canAssignTechnician": true,
    "canRecordLabor": true,
    "canAdjustLabor": false,
    "canIssueParts": true,
    "canConsumeParts": true,
    "canReturnParts": true,
    "canRequestSubstitution": true,
    "canApproveSubstitution": false,
    "canCreateChangeRequest": true,
    "canApproveChangeRequest": false
  }
}
```

---

### Permission Error Response

When a user attempts an operation they lack authority for, the backend returns a 403 Forbidden response with the standard error envelope.

**Error Response (403 Forbidden):**

```json
{
  "code": "FORBIDDEN",
  "message": "User lacks authority 'workorder:substitution:approve' required to approve part substitutions",
  "correlationId": "550e8400-e29b-41d4-a716-446655440002",
  "details": {
    "requiredAuthority": "workorder:substitution:approve",
    "userAuthorities": [
      "workorder:workorder:view",
      "workorder:labor:record",
      "workorder:parts:issue"
    ]
  }
}
```

**Error Fields:**

- `code` (String) — Error code (FORBIDDEN)
- `message` (String) — Human-readable error message including required authority
- `correlationId` (String) — Request correlation ID for debugging
- `details` (Object, optional) — Additional error context
  - `requiredAuthority` (String) — The authority required for the operation
  - `userAuthorities` (String[], optional) — Authorities the user currently has (for debugging)

**Business Rules:**

- Permission checks performed before operation execution
- Authorization failures return 403 (not 401)
- Error messages MUST NOT leak sensitive information about system structure
- `userAuthorities` field in details SHOULD be included only in development/test environments

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
  - GET <http://localhost:8080/workorder/v1/estimates>
  - POST <http://localhost:8080/workorder/v1/estimates>
  - GET <http://localhost:8080/workorder/v1/estimates/{id}>
  - POST <http://localhost:8080/workorder/v1/estimates/{id}/approval>
  - POST <http://localhost:8080/workorder/v1/estimates/{id}/decline>
  - POST <http://localhost:8080/workorder/v1/estimates/{id}/reopen>
  - DELETE <http://localhost:8080/workorder/v1/estimates/{id}>
- Estimate Queries:
  - GET <http://localhost:8080/workorder/v1/estimates/customer/{id}>
  - GET <http://localhost:8080/workorder/v1/estimates/shop/{id}>
  - GET <http://localhost:8080/workorder/v1/estimates/location/{id}>
- Workorder Retrieval: GET <http://localhost:8080/workorder/v1/{id}>, GET <http://localhost:8080/workorder/v1>
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
- pos-workorder internal routing: `/v1/*` (service-local)
- API Gateway external routing (docs/examples): `http://localhost:8080/workorder/v1/*`
- Gateway strips `/workorder` prefix before forwarding to pos-workorder service

### Phase 2 Implementation Notes

**Confirmed in OpenAPI Spec:**

- Item management endpoints exist at `/v1/estimates/{estimateId}/items` (POST)
- Tax calculation endpoint exists at `/v1/estimates/{estimateId}/calculate`
- Snapshot endpoint exists at `/v1/estimates/{estimateId}/snapshots`
- Approval workflow endpoints exist (estimate approval, workorder approval)

**Future Enhancements Tracked via Issues:**

- PATCH/DELETE semantics for estimate items (follow-up)
- Explicit promote-to-workorder endpoint (may be in workflow service)
- Approval method configuration (may be centralized in pos-customer)
- Legal terms attachment (may be in document generation service)

### API Routing Clarification

**pos-workorder internal routing:** `/v1/*` (service declares this in controllers)

**API Gateway external routing (required for docs/examples):** `http://localhost:8080/workorder/v1/*`

The gateway strips the `/workorder` prefix and forwards `/v1/*` to the pos-workorder service. Frontend should use gateway URLs with `/workorder/v1/*` prefix.

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
