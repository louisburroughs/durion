## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Completion: Finalize Billable Scope Snapshot (Finalize Work Order for Billing)

### Primary Persona
Service Advisor (primary), Manager/FinanceManager (for correction initiation actions if present)

### Business Value
Create an immutable, versioned, invoice-ready snapshot of authorized completed work so invoicing uses a stable source of truth and is auditable, while blocking invalid billing (completed-but-unauthorized items) and gating price/tax variances.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** to finalize a Work Order for billing by creating a versioned Billable Scope Snapshot and marking items invoice-ready,  
**So that** Billing can generate an invoice from an immutable snapshot rather than mutable work order items, with a clear audit trail and variance controls.

### In-scope
- Frontend UI to initiate “Finalize for Billing” for a work order.
- Display of validation failures (unauthorized completed items, no billable items, variance detected).
- Variance approval UI gate (capture reason code; require elevated permission where applicable).
- Display snapshot result (version, totals, createdBy/createdAt, variance flags) and expose for back office review.
- Read-only behavior expectations after finalization (work order/item editing blocked in this UI surface).
- Ability to view snapshot versions/history for a work order (at least list + open details).

### Out-of-scope
- Implementing invoice generation itself (billing domain).
- Defining pricing/tax calculation logic (backend responsibility); frontend only displays results/variance payload.
- Full “correction workflow” implementation details beyond initiating action if API exists (see Open Questions).
- Notification delivery.

---

## 3. Actors & Stakeholders
- **Service Advisor**: initiates finalization; reviews variance and approves if permitted.
- **Manager / FinanceManager**: may initiate corrections and approve variance when elevated approval required.
- **Billing / Back Office**: views snapshot totals and versions for review.
- **Auditor**: relies on immutable snapshot and audit metadata.
- **System**: performs snapshot creation, versioning, and state transitions.

---

## 4. Preconditions & Dependencies
- User is authenticated in POS frontend.
- A Work Order exists and is in a state that permits finalization (backend-enforced).
- Work Order has work order items with execution/authorization status data available to UI (for pre-check display and/or backend error rendering).
- Backend endpoints exist for:
  - Finalize/create snapshot (and variance approval flow if separate).
  - Retrieve snapshot list/details by work order.
- Moqui screens framework routing and authz integration is configured for Work Order views.

**Dependencies / Contracts not provided in inputs (blocking)**
- Exact REST/API endpoints and payloads for snapshot finalization and retrieval on Moqui side (see Open Questions).
- Exact Work Order statuses relevant to “ready to complete / finalize” in frontend (backend story references `WorkComplete`, `FinalizedForBilling`, etc. but workexec FSM doc doesn’t list these explicitly).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: primary action button **“Finalize for Billing”** shown when work order is eligible.
- From back office review area (optional): link/tab **“Billable Snapshot(s)”** within Work Order.

### Screens to create/modify
1. **Modify**: `WorkOrderDetail` screen
   - Add “Finalize for Billing” action + status/totals panel for snapshot summary if exists.
2. **Create**: `WorkOrderBillableSnapshots` screen
   - List snapshots for a work order (version, status, totals, createdBy/createdAt, hasVariance).
3. **Create**: `BillableScopeSnapshotDetail` screen
   - Header totals + variance metadata + line items (read-only).
4. **(Optional, if correction initiation is supported now)**: `WorkOrderCorrectionInitiate` dialog/screen
   - Capture required reasonCode and submit.

### Navigation context
- Route pattern should be nested under Work Order:
  - `/workorders/:workOrderId/billing-snapshots`
  - `/workorders/:workOrderId/billing-snapshots/:snapshotId`
- “Finalize for Billing” stays on Work Order detail.

### User workflows
**Happy path**
1. Service Advisor opens Work Order.
2. Clicks “Finalize for Billing”.
3. UI calls finalize service.
4. On success: UI shows confirmation, updates Work Order status display, and navigates to Snapshot Detail (v1) or opens a summary panel.

**Alternate paths**
- Unauthorized completed items exist → UI shows blocking message listing items and instructs to authorize/remove (no finalize).
- No billable items → UI shows blocking message.
- Variance detected → UI shows variance details and a gated approval action (if user has permission); after approval, finalize proceeds and snapshot created.
- Already finalized → UI hides/disabled finalize and offers “View Snapshot(s)”.

---

## 6. Functional Behavior

### Triggers
- User clicks **Finalize for Billing** on an eligible Work Order.
- User clicks **Approve Variance & Finalize** when variance gate occurs (if supported).

### UI actions
- Render eligibility:
  - Show finalize action only when backend indicates eligible OR allow click and rely on backend error (prefer backend-driven eligibility flag if available).
- On finalize click:
  - Confirm dialog: “Finalizing creates an immutable snapshot for invoicing. Continue?”
  - Submit finalize request with `workOrderId` and current user context.
- On variance gate:
  - Display variance details (tax/price differences) from backend error payload.
  - Provide input for `varianceReasonCode` (and/or free-text if required by backend).
  - Provide submit action; enforce required fields client-side where specified.

### State changes (frontend-visible)
- Work Order status changes to `FinalizedForBilling` (or equivalent) after success.
- Work Order items involved change to `InvoiceReady` (or equivalent) after success.
- Snapshot list gains new `snapshotVersion` and `snapshotStatus: Active`; prior version becomes `Superseded` if this is a correction re-finalize.

### Service interactions
- Call finalize snapshot creation service.
- Call snapshot list/detail load services.
- (If separate) Call variance approval service and then re-attempt finalize, or single finalize endpoint that accepts variance approval payload.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI responsibilities vs backend)
- **BR: Completed but Unauthorized hard block**
  - UI must surface as a blocking error; do not present “force finalize”.
  - Error message must identify affected items (at minimum by description + ID).
- **BR: No billable items**
  - UI shows blocking error: “No billable items are available to be finalized.”
- **BR: Price/Tax variance hard stop with approval gate**
  - UI must stop the finalize flow and show variance details.
  - UI must require explicit user action to approve variance.
  - UI must collect `varianceReasonCode` (required) and pass approver identity (implicit from session unless backend requires explicit `approverId`).
  - If backend indicates elevated permission required, UI must show authorization error and instruct user to have a Manager/FinanceManager approve.

### Enable/disable rules
- “Finalize for Billing” button disabled when:
  - Work Order already finalized (has active snapshot / status indicates finalized).
  - A finalize call is in-flight (prevent double submission).
- “Approve variance” button disabled until required fields (reason code) are populated.

### Visibility rules
- Snapshot panels/links visible when:
  - At least one snapshot exists for work order.
- Variance approval UI only visible when variance error returned.

### Error messaging expectations
- Use user-safe language; do not leak internal stack traces.
- For 403: “You don’t have permission to approve this variance. Ask a Manager or Finance Manager.”
- For 409 concurrency: “This work order changed since you loaded it. Refresh and try again.”
- For 422/400 validation: show field-level messages (reasonCode required, etc.).

---

## 8. Data Requirements

### Entities involved (frontend read models)
- `WorkOrder`
- `WorkOrderItem` (service/part/labor lines as applicable)
- `BillableScopeSnapshot`
- `BillableScopeSnapshotItem`
- `TaxSnapshot` (as part of snapshot header or line taxDetails)

### Fields (type, required, defaults)

**WorkOrder (minimum required for UI)**
- `workOrderId` (string/number, required)
- `status` (enum/string, required)
- `billableSnapshotActiveId` (id, optional)
- `billableSnapshotActiveVersion` (int, optional)

**WorkOrderItem (for error display and invoice-ready confirmation)**
- `workOrderItemId` (id, required)
- `description` (string, required)
- `authorized` (boolean, required) *(or derived from status)*
- `status` (enum/string, required) including `Completed` and `InvoiceReady`

**BillableScopeSnapshot**
- `snapshotId` (id, required)
- `workOrderId` (id, required)
- `snapshotVersion` (int, required)
- `snapshotStatus` (enum: `Active|Superseded|PendingReview`, required)
- `subtotalAmount` (money, required)
- `taxTotalAmount` (money, required)
- `feeTotalAmount` (money, required)
- `grandTotalAmount` (money, required)
- `hasVariance` (boolean, required)
- `varianceApprovedBy` (id, nullable)
- `varianceApprovedAt` (datetime UTC, nullable)
- `varianceReasonCode` (string, nullable unless variance approved)
- `correctsSnapshotId` (id, nullable)
- `createdByUserId` (id, required)
- `createdAt` (datetime UTC, required)

**BillableScopeSnapshotItem**
- `snapshotItemId` (id, required)
- `sourceWorkOrderItemId` (id, required)
- `description` (string, required)
- `quantity` (number, required)
- `unitPrice` (money, required)
- `lineTotal` (money, required)
- `taxDetails` (json/structured, required/optional per backend)
- `feeDetails` (json/structured, required/optional per backend)

### Read-only vs editable by state/role
- Snapshot entities: always read-only in UI.
- Work Order and items: read-only once status is `FinalizedForBilling` (or snapshot exists), except via separate correction workflow (not implemented here unless clarified).

### Derived/calculated fields (UI)
- UI may compute display-only sums from snapshot items for verification, but must treat backend snapshot totals as authoritative.

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: Moqui service names and/or REST endpoints are not provided in inputs; below is the required contract shape. Final names must be confirmed.

### Load/view calls
1. Load work order detail (existing):
   - `GET /api/workorders/{workOrderId}` (or Moqui screen data)
2. List snapshots for a work order:
   - `GET /api/workorders/{workOrderId}/billable-snapshots`
   - Response: array of `BillableScopeSnapshot` headers
3. Load snapshot detail:
   - `GET /api/billable-snapshots/{snapshotId}`
   - Response: snapshot header + items

### Create/update calls
1. Finalize for billing (create snapshot):
   - `POST /api/workorders/{workOrderId}/finalize-billing`
   - Request body (minimum):
     - `reason` (optional?) *(not specified)*
     - `varianceApproval` (optional object if variance must be approved inline):
       - `varianceReasonCode` (required when provided)
   - Success:
     - `201` with `snapshotId`, `snapshotVersion`, totals, updated work order status
2. Variance approval (if separate step):
   - `POST /api/workorders/{workOrderId}/finalize-billing/approve-variance`
   - Body: `varianceReasonCode` (+ any required fields)

### Submit/transition calls (Moqui transitions)
- `transition` on WorkOrderDetail: `finalizeBilling`
- Optional `transition` for variance approval

### Error handling expectations (mapping)
- `400/422`: validation error (missing reasonCode, etc.) → show field errors
- `403`: permission denied → show role-required message
- `404`: work order or snapshot not found → show not-found empty state
- `409`: conflict (already finalized, concurrent changes) → offer refresh and link to existing snapshot
- `409` for “variance pending” or “unauthorized completed items” may be encoded as domain error; UI must parse structured error payload if provided, else display message.

---

## 10. State Model & Transitions

### Allowed states (known from provided references)
Work Order FSM doc (authoritative for execution) includes:
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

Backend snapshot story additionally references:
- `WorkComplete` (or similar) and `FinalizedForBilling`, `ReviewRequired`

**Conflict/Gap (blocking):**
- Finalization-related statuses (`FinalizedForBilling`, `ReviewRequired`, `WorkComplete`) are not present in the provided WorkOrder FSM doc. Frontend needs the actual enumerations to render status and eligibility deterministically.

### Role-based transitions
- Service Advisor: can initiate finalize-for-billing (assuming permission).
- Manager/FinanceManager: can approve variance when elevated approval required; can initiate correction (if endpoint exists).

### UI behavior per state
- If Work Order status indicates already finalized:
  - Disable/hide finalize action
  - Show link to active snapshot
- If Work Order is not in a “finalizable” completion state:
  - Hide finalize action or show disabled with tooltip “Work must be completed before finalizing for billing.”

---

## 11. Alternate / Error Flows

### Validation failures
1. **Completed but Unauthorized items exist**
   - Show blocking error with list of items requiring authorization/removal.
   - Provide navigation to items section (anchor) but do not implement authorization in this story.
2. **No billable items**
   - Show error and keep user on Work Order detail.
3. **Variance detected**
   - Show variance modal with details and required approval.
   - If user lacks permission, show 403-style messaging and stop.

### Concurrency conflicts
- If finalize returns conflict because snapshot already exists / version advanced:
  - Reload work order and snapshots
  - Navigate to active snapshot
  - Show toast: “Work order was already finalized.”

### Unauthorized access
- If user lacks permission to finalize:
  - Show authorization error; action remains disabled.

### Empty states
- Snapshot list empty: show “No billable snapshots yet.”
- Snapshot detail missing: show not found and link back to Work Order.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Finalize creates snapshot and marks invoice-ready (happy path)
**Given** a Work Order is eligible for billing finalization and has Work Order Items that are **Completed** and **Authorized**  
**When** the Service Advisor clicks “Finalize for Billing” and confirms  
**Then** the system creates a new Billable Scope Snapshot with the next sequential version and status `Active`  
**And** the UI navigates to the Snapshot Detail view showing snapshot totals and createdBy/createdAt  
**And** the Work Order status displayed in the UI updates to the finalized-for-billing status  
**And** the included Work Order Items show invoice-ready status (or the UI reflects invoice-ready via refreshed data)

### Scenario 2: Hard block when any completed item is not authorized
**Given** a Work Order has at least one item that is **Completed** but not **Authorized**  
**When** the Service Advisor attempts to “Finalize for Billing”  
**Then** the finalization request fails and no snapshot is created  
**And** the UI shows a blocking error identifying the unauthorized completed item(s)  
**And** the “Finalize for Billing” action remains available after the user addresses authorization outside this flow

### Scenario 3: Block when no billable items exist
**Given** a Work Order has no items that are both **Completed** and **Authorized**  
**When** the Service Advisor attempts to “Finalize for Billing”  
**Then** the request fails  
**And** the UI shows “No billable items are available to be finalized.”  
**And** no snapshot list entry is created

### Scenario 4: Variance detected requires explicit approval before snapshot creation
**Given** a Work Order has Completed and Authorized items  
**And** the backend detects a price or tax variance versus the authorized basis  
**When** the Service Advisor attempts to “Finalize for Billing”  
**Then** the UI shows a variance blocking message/modal including variance details  
**And** the UI requires a variance reason code before enabling “Approve Variance & Finalize”  
**When** the user submits variance approval with a reason code  
**Then** the snapshot is created with `hasVariance=true` and variance approval metadata is visible on the snapshot detail

### Scenario 5: Variance approval requires elevated permission
**Given** variance approval requires elevated permission for the detected variance  
**When** a non-privileged Service Advisor attempts to approve the variance  
**Then** the request is rejected with an authorization error  
**And** the UI instructs the user to have a Manager/FinanceManager approve  
**And** no snapshot is created

### Scenario 6: Snapshot versions are viewable for back office review
**Given** a Work Order has one or more billable snapshots  
**When** the user opens “Billable Snapshot(s)” from the Work Order  
**Then** the UI lists snapshots with version, status, totals, and created metadata  
**And** selecting a snapshot opens a read-only detail view with line items and totals

### Scenario 7: Concurrency—already finalized by another user
**Given** the Work Order is finalized by another user while the Service Advisor is viewing it  
**When** the Service Advisor attempts to finalize  
**Then** the UI receives a conflict response  
**And** the UI refreshes and navigates to the active snapshot (if available)  
**And** the UI shows a message indicating it was already finalized

---

## 13. Audit & Observability

### User-visible audit data
- Snapshot detail must display:
  - `snapshotVersion`, `snapshotStatus`
  - `createdByUserId` (render as username if available) and `createdAt` (UTC-aware display)
  - `hasVariance` and, if true: `varianceApprovedBy`, `varianceApprovedAt`, `varianceReasonCode`
  - `correctsSnapshotId` if present (link to prior snapshot)

### Status history
- Snapshot list provides version history; if correction exists, show relationship (e.g., “Corrects v1”).

### Traceability expectations
- Every snapshot item displays `sourceWorkOrderItemId` (may be hidden behind “Details” but must be accessible).
- Frontend should pass a correlation/request id header if project convention exists (not provided; see safe defaults/observability boilerplate allowed).

---

## 14. Non-Functional UI Requirements
- **Performance**: Snapshot list and detail should render within 2s for up to 200 snapshot items; use pagination/virtual scroll if needed.
- **Accessibility**: Modal dialogs focus-trapped; buttons labeled; errors announced via ARIA live region.
- **Responsiveness**: Works on tablet resolutions used at service desk.
- **i18n/timezone/currency**:
  - Currency formatting based on store locale (if available) or user locale.
  - Timestamps displayed in shop local time with UTC stored value indicated (do not convert stored value).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for snapshot list/detail; qualifies as safe UI ergonomics; impacts UX Summary, Alternate/Empty states.
- SD-UX-INFLIGHT-GUARD: Disable submit buttons during in-flight finalize/approve calls to prevent double submit; safe because it doesn’t alter domain rules; impacts Functional Behavior, Error Flows.
- SD-ERR-HTTP-MAP: Standard mapping of 400/403/404/409 to user messaging; safe because it follows implied backend contract and does not invent policy; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Backend/Moqui contract:** What are the exact Moqui service names or REST endpoints for:
   - finalizing for billing (snapshot creation),
   - listing snapshots by work order,
   - loading snapshot detail,
   - variance approval (inline vs separate)?
2. **Authoritative Work Order statuses:** What exact status enum values should the frontend use for:
   - “eligible to finalize” (e.g., `READY_FOR_PICKUP` vs `COMPLETED` vs `WorkComplete`),
   - “finalized for billing” (`FinalizedForBilling`),
   - “review required” (`ReviewRequired`)?
   The provided FSM doc does not include `FinalizedForBilling`/`ReviewRequired`.
3. **Variance payload shape:** When variance is detected, what structured fields are returned for display (line-level vs header-level variance, tax rate differences, thresholds, required approval role)?
4. **Variance approval input:** Is `varianceReasonCode` the only required field, or is free-text required as well? Is a controlled reason-code list required (and where is it sourced)?
5. **Who is recorded as initiator/approver:** Does backend infer `createdByUserId/varianceApprovedBy` from auth context, or must frontend pass explicit userId?
6. **Correction initiation scope:** Is “initiate correction” part of this frontend story (and is there an API), or should the frontend only enforce read-only state and show snapshot versions?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Completion: Finalize Billable Scope Snapshot  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/216  
Labels: frontend, story-implementation, user

### Story Description

/kiro  
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
Workorder is ready to be completed and prepared for invoicing.

## Main Flow
1. System compiles all authorized and completed items into a billable scope snapshot.
2. System validates quantities, pricing, taxes, and fees basis for invoicing.
3. System marks items as invoice-ready and stores snapshot version.
4. System records who initiated snapshot and when.
5. System exposes snapshot totals for back office review.

## Alternate / Error Flows
- Items completed but not authorized → block or flag for approval per policy.
- Tax configuration changes since estimate → store variance and require review.

## Business Rules
- Invoice derives from billable scope snapshot, not from live mutable items.
- Snapshot must be versioned and auditable.

## Data Requirements
- Entities: BillableScopeSnapshot, WorkorderItem, TaxSnapshot
- Fields: snapshotVersion, invoiceReadyFlag, taxAmount, feeTotal, grandTotal, varianceReason

## Acceptance Criteria
- [ ] System creates a billable scope snapshot that matches completed authorized work.
- [ ] Snapshot is versioned and retrievable.
- [ ] Items are marked invoice-ready.

## Notes for Agents
Snapshot is your source of truth for invoicing; do not compute invoices off mutable live items.