Title: [BACKEND] [STORY] Completion: Finalize Billable Scope Snapshot
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/152
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---

**Rewrite Variant:** workexec-structured

---

## Story Intent
**As a** System,
**I want to** create an immutable, versioned snapshot of all billable items from a completed Work Order,
**So that** the Billing domain has a stable and authoritative source of truth for generating an accurate customer invoice, free from any subsequent changes to the live Work Order.

## Actors & Stakeholders
*   **Actors:**
    *   `Service Advisor` (or equivalent role): The user who initiates the finalization of the Work Order.
    *   `System`: The agent responsible for executing the snapshot creation and validation logic.
*   **Stakeholders:**
    *   `Billing Department`: The primary consumer of the `BillableScopeSnapshot`. Their workflow depends on its accuracy and stability.
    *   `Customer`: The end recipient of the invoice generated from the snapshot.
    *   `Shop Manager`: Responsible for the operational and financial outcomes of work performed.
    *   `Auditors`: Require a clear, auditable trail from work performed to amount billed.

## Preconditions
1.  A `WorkOrder` exists and is in a state that permits finalization (e.g., `WorkComplete`, `PendingFinalization`).
2.  The `Service Advisor` initiating the action has the necessary permissions to finalize work orders.
3.  The `WorkOrder` contains at least one `WorkOrderItem`.

## Functional Behavior

### Trigger
A `Service Advisor` triggers the "Finalize for Billing" action for a specific `WorkOrder` via the user interface.

### Main Success Scenario
1.  The System receives the request to finalize the specified `WorkOrder`.
2.  **Validation Phase (before snapshot creation):**
    - The System identifies all `WorkOrderItem`s associated with the `WorkOrder` that have status `Completed` AND `Authorized`.
    - The System validates that no `Completed` items exist without `Authorized` status (see Business Rules).
    - The System detects any price or tax variance (see Business Rules).
    - If either condition blocks finalization, the operation terminates and an error is returned.
3.  The System validates that at least one billable item exists.
4.  **Snapshot Creation Phase:**
    - The System creates a new `BillableScopeSnapshot` entity, linking it to the `WorkOrder`. A version number is assigned (e.g., `v1`).
    - For each valid billable item, the System creates an immutable `BillableScopeSnapshotItem` record, copying all financially relevant details (e.g., description, quantity, unit price, line total, tax rates, fees).
    - The System calculates and stores aggregate totals (subtotal, tax total, fee total, grand total) on the `BillableScopeSnapshot` header.
    - The System records the initiating user's ID and the current timestamp on the snapshot.
5.  **Post-Snapshot State Updates:**
    - The System updates the `WorkOrder` status to `FinalizedForBilling`.
    - The System updates the status of the corresponding `WorkOrderItem`s to `InvoiceReady`.
6.  **Event Emission:**
    - The System emits an event (e.g., `BillableScopeFinalized`) to notify downstream consumers like the Billing system.

### Variance Approval (if needed)
- If a price or tax variance is detected during validation:
  - The user receives error message with variance details
  - User must explicitly approve the variance (requires elevated permission if threshold exceeded)
  - Approval includes: `approverId`, `timestamp`, `reasonCode`
  - Only after approval proceeds finalization to snapshot creation phase

## Alternate / Error Flows

*   **Flow 1: Completed but Unauthorized Items Exist (HARD BLOCK)**
    *   **Trigger**: The System detects `WorkOrderItem`s that are `status: Completed` but not `status: Authorized`.
    *   **Outcome**: Finalization is blocked. No snapshot is created. The System returns an error to the user interface, enumerating which items require authorization before the Work Order can be finalized. User must either obtain authorization for items or remove them from Work Order.

*   **Flow 2: No Billable Items Found**
    *   **Trigger**: The System finds no `WorkOrderItem`s that are both `Completed` and `Authorized`.
    *   **Outcome**: The finalization process is blocked. The System returns an error message stating, "No billable items are available to be finalized."

*   **Flow 3: Price or Tax Variance Detected (HARD STOP with Approval Gate)**
    *   **Trigger**: During validation, the System detects that a current tax rate or item price differs from what was on the original, authorized estimate.
    *   **Outcome**: 
      - Finalization is blocked until variance is explicitly approved
      - User must approve variance (may require elevated permission depending on variance threshold)
      - Error message specifies variance details and required approval level
      - After approval, finalization proceeds to snapshot creation
      - Approved variance is recorded with `approverId`, `timestamp`, `reasonCode`

*   **Flow 4: Post-Finalization Correction (Controlled, Role-Gated)**
    *   **Trigger**: A user with elevated permission (`Manager` or `FinanceManager`) attempts to correct a finalized Work Order.
    *   **Outcome**: 
      - Finalized Work Orders are read-only by default
      - Correction requires explicit "Correction Initiation" action with mandatory `reasonCode`
      - Original snapshot is never modified; creates new corrective snapshot or delta record
      - New snapshot links to original via `correctsSnapshotId`
      - Full audit trail of correction is maintained
      - Note: Detailed correction workflow may be implemented as separate story, but this story enforces permission checks

## Business Rules

*   **BR1: Immutability as Source of Truth:** The `BillableScopeSnapshot` and its line items are immutable once created. All invoicing and financial reporting for the work performed must be derived exclusively from this snapshot.

*   **BR2: Authorization Prerequisite:** Only `WorkOrderItem`s that are explicitly `Authorized` shall be included in the billable scope. This is enforced as a hard block.

*   **BR3: Completion Prerequisite:** Only `WorkOrderItem`s marked as `Completed` shall be included in the billable scope.

*   **BR4: Completed but Unauthorized Hard Block:** If ANY `WorkOrderItem` has `status: Completed` but lacks `status: Authorized`, finalization is **blocked**. No snapshot is created until all completed items are authorized or removed.
    - **Rationale:** Prevents unbilled or disputed work from entering financial record; eliminates revenue leakage and reconciliation complexity; aligns with compliance and audit expectations.

*   **BR5: Price/Tax Variance Hard Stop:** If a price or tax variance is detected (current rates differ from authorized estimate):
    - Finalization is **blocked** until user explicitly acknowledges and approves the variance
    - Approval may require elevated permission depending on variance threshold
    - Variance approval is synchronous (not background review)
    - **Rationale:** Finalization produces immutable financial snapshot; variances must be resolved beforehand; avoids retroactive corrections and audit exceptions; keeps pricing/tax authority explicit and controlled.

*   **BR6: Mandatory Versioning:** Every snapshot generation for a `WorkOrder` must create a new, sequentially versioned record. The previous version (if any) must be marked as `Superseded`.

*   **BR7: Controlled Post-Finalization Corrections:**
    - Finalized Work Orders are **read-only by default**
    - Corrections require explicit "Correction Initiation" action with mandatory `reasonCode`
    - Only authorized roles (`Manager`, `FinanceManager`, or equivalent) can initiate corrections
    - Original snapshot is never modified; corrections create new snapshot/delta record with link to original (`correctsSnapshotId`)
    - All corrections are fully audited and append-only
    - **Rationale:** Preserves immutability of finalized financial records; supports compliance and traceability; prevents silent or ad-hoc edits.

## Data Requirements

*   **`BillableScopeSnapshot` Entity:**
    *   `snapshotId`: Unique identifier (PK).
    *   `workOrderId`: Foreign key to the `WorkOrder`.
    *   `snapshotVersion`: Integer (e.g., 1, 2, 3).
    *   `snapshotStatus`: Enum (`Active`, `Superseded`, `PendingReview`).
    *   `subtotalAmount`: Monetary value.
    *   `taxTotalAmount`: Monetary value.
    *   `feeTotalAmount`: Monetary value.
    *   `grandTotalAmount`: Monetary value.
    *   `hasVariance`: Boolean flag (true if variance was detected and approved).
    *   `varianceApprovedBy`: UUID, nullable (user who approved variance if applicable).
    *   `varianceApprovedAt`: Timestamp, nullable.
    *   `varianceReasonCode`: String, nullable.
    *   `correctsSnapshotId`: UUID, nullable (reference to original if this is a corrective snapshot).
    *   `createdByUserId`: Identifier for the user who initiated the snapshot.
    *   `createdAt`: Timestamp.

*   **`BillableScopeSnapshotItem` Entity:**
    *   `snapshotItemId`: Unique identifier (PK).
    *   `snapshotId`: Foreign key to the `BillableScopeSnapshot`.
    *   `sourceWorkOrderItemId`: Foreign key for traceability.
    *   `description`: Text (copied).
    *   `quantity`: Numeric (copied).
    *   `unitPrice`: Monetary value (copied).
    *   `lineTotal`: Monetary value (copied).
    *   `taxDetails`: JSON or structured data representing taxes applied (copied).
    *   `feeDetails`: JSON or structured data representing fees applied (copied).

*   **State Model Updates:**
    *   `WorkOrder`: The status model must include `FinalizedForBilling` and `ReviewRequired`.
    *   `WorkOrderItem`: The status model must include `InvoiceReady`.

## Acceptance Criteria

### AC1: Successful Snapshot Creation (Happy Path)
*   **Given** a `WorkOrder` is in a `WorkComplete` state with three `WorkOrderItem`s that are all `Completed` and `Authorized`
*   **When** the `Service Advisor` finalizes the `WorkOrder` for billing
*   **Then** a new `BillableScopeSnapshot` with `snapshotVersion: 1` and `snapshotStatus: Active` is created
*   **And** it contains exactly three `BillableScopeSnapshotItem`s corresponding to the source items
*   **And** the `WorkOrder` status is updated to `FinalizedForBilling`
*   **And** the three source `WorkOrderItem`s are updated to `status: InvoiceReady`
*   **And** a `BillableScopeFinalized` event is emitted.

### AC2: Hard Block on Unauthorized Items
*   **Given** a `WorkOrder` has two items: one is `Completed` and `Authorized`, and the other is `Completed` but not `Authorized`
*   **When** the `Service Advisor` attempts to finalize the `WorkOrder`
*   **Then** the operation fails immediately
*   **And** no `BillableScopeSnapshot` is created
*   **And** the system returns an error message identifying the unauthorized item and required actions.

### AC3: Hard Stop on Price/Tax Variance (with Approval Gate)
*   **Given** a `WorkOrder` with `Completed` and `Authorized` items
*   **And** the current system tax rate for a service has changed since the `WorkOrder` estimate was approved
*   **When** the `Service Advisor` attempts to finalize the `WorkOrder`
*   **Then** finalization is blocked
*   **And** the system returns an error detailing the variance and the approval requirement
*   **And** when the user explicitly approves the variance (with elevated permission if threshold exceeded)
*   **Then** a `BillableScopeSnapshot` is created
*   **And** its `hasVariance` flag is set to `true`
*   **And** variance approval details are recorded (`varianceApprovedBy`, `varianceApprovedAt`, `varianceReasonCode`)
*   **And** the `WorkOrder` status is updated to `FinalizedForBilling`.

### AC4: Snapshot Versioning on Correction
*   **Given** a `WorkOrder` has an existing `BillableScopeSnapshot` at `snapshotVersion: 1` with `snapshotStatus: Active`
*   **And** a correction flow is initiated which reverts the `WorkOrder` to an editable state
*   **When** the `Service Advisor` re-finalizes the `WorkOrder` for billing
*   **Then** the original snapshot is updated to `snapshotStatus: Superseded`
*   **And** a new `BillableScopeSnapshot` is created with `snapshotVersion: 2` and `snapshotStatus: Active`
*   **And** the new snapshot has `correctsSnapshotId` pointing to the original snapshot.

### AC5: Reject Correction Without Permission
*   **Given** a `WorkOrder` is in `FinalizedForBilling` status
*   **And** a user with standard `ServiceAdvisor` role (not `Manager` or `FinanceManager`) attempts to initiate correction
*   **When** the system evaluates the permission
*   **Then** the request is rejected with authorization error
*   **And** the original snapshot remains unmodified.

### AC6: Mandatory Reason Code for Correction
*   **Given** a user with elevated permission attempts to initiate a correction of a finalized Work Order
*   **When** the user attempts to proceed without providing a `reasonCode`
*   **Then** the system rejects the action with validation error
*   **And** correction cannot proceed until `reasonCode` is supplied.

## Audit & Observability

*   **Audit Trail:** The creation of every `BillableScopeSnapshot` and variance approval must generate audit log entries containing:
    *   `Event`: `BillableScopeSnapshotCreated` or `VarianceApproved`
    *   `Actor`: `userId` of the initiator
    *   `Target`: `workOrderId`, `snapshotId`
    *   `Timestamp`: Event timestamp
    *   `Details`: Result (e.g., `Success`, `SuccessWithVariance`), version number, variance details if applicable

*   **Logging:**
    *   `INFO`: Log successful snapshot creation, including version number, item count, and totals
    *   `WARN`: Log variance detections with threshold and required approval level
    *   `INFO`: Log variance approvals with approver and reason code
    *   `ERROR`: Log finalization failures (unauthorized items, variance rejection, etc.)

*   **Metrics:**
    *   `billing.snapshot.created.count`: Counter, incremented on successful snapshot creation
    *   `billing.snapshot.variance.count`: Counter, incremented when variance is detected
    *   `billing.snapshot.variance.approved.count`: Counter, incremented when variance is approved
    *   `billing.finalization.blocked.count`: Counter by reason (unauthorized_items, variance_pending, etc.)

## Clarification Resolution

All blocking policy questions have been resolved:

### 1. Policy for Completed but Unauthorized Items
**Decision:** **Option A — Strict (Hard Block)**

- **Finalization is blocked** if any **Completed but Unauthorized** items exist
- User must either obtain authorization or remove items from Work Order
- **No snapshot is created** until all completed work is authorized
- **Rationale:** Prevents unbilled or disputed work from entering financial record; eliminates revenue leakage; aligns with compliance and audit expectations

### 2. Policy for Price or Tax Variance Detection
**Decision:** **Option B — Hard Stop with Explicit Approval**

- If a price or tax variance is detected, **finalization is blocked**
- User must **explicitly acknowledge and approve** the variance
- Approval may require elevated permission depending on threshold
- Only after approval is **BillableScopeSnapshot** created
- Variance approval recorded with `approverId`, `timestamp`, `reasonCode`
- **Rationale:** Finalization produces immutable financial snapshot; variances must be resolved beforehand; avoids retroactive corrections; keeps pricing/tax authority explicit and controlled

### 3. Correction Workflow (Post-Finalization)
**Decision:** **Controlled correction with elevated permissions and full audit**

**Authorized Roles:**
- `Manager`
- `FinanceManager` (or equivalent)

**Policy:**
- Finalized Work Orders are **read-only by default**
- Corrections require:
  - Explicit **Correction Initiation** action
  - Mandatory **reasonCode**
  - Creation of **Correction Event** (append-only)
- Original snapshot is **never modified**; corrections create:
  - New corrective snapshot or delta record
  - Linkage to original snapshot via `correctsSnapshotId`
- **Rationale:** Preserves immutability of finalized financial records; supports compliance and traceability; prevents silent or ad-hoc edits

**Status:** All policy questions resolved; story is unblocked and ready for implementation.

## Original Story (Unmodified – For Traceability)
# Issue #152 — [BACKEND] [STORY] Completion: Finalize Billable Scope Snapshot

## Current Labels
- backend
- blocked:clarification
- domain:workexec
- status:draft
- story-implementation
- type:story
- user

## Original Scope
This story defines the mechanism for creating an immutable, versioned snapshot of billable items when a Work Order is finalized, ensuring the Billing domain has a stable source of truth for invoice generation.
