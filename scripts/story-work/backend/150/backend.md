Title: [BACKEND] [STORY] Completion: Reopen Completed Workorder (Controlled)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/150
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Current / Intended)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Supporting
- backend
- story-implementation

---

## Story Intent
As a Back Office Manager, I need to reopen a completed work order with a mandatory reason, so that I can correct errors before an invoice is generated, while ensuring the action is strictly controlled and audited.

## Actors & Stakeholders
- **Primary Actor:** Back Office Manager (or any user role with the `WORKORDER_REOPEN_COMPLETED` permission).
- **Stakeholders:**
  - Accounting System: Receives a `WorkorderReopened` event to signal the invalidation of any previous completion data.
  - Service Advisor: May need to edit the work order after it has been reopened.

## Preconditions
- A `Workorder` entity exists with a status of `COMPLETED`.
- No `Invoice` associated with this `Workorder` has been `ISSUED` or `FINALIZED`.
- The user initiating the action has the `WORKORDER_REOPEN_COMPLETED` permission granted to their role.

## Functional Behavior
1. **Trigger:** An authorized user selects the "Reopen Work Order" action for a work order in the `COMPLETED` state.
2. **Input:** The system prompts the user to provide a mandatory, non-empty `reopenReason` text.
3. **Validation:**
   - The system verifies the user has the `WORKORDER_REOPEN_COMPLETED` permission.
   - The system confirms the work order status is `COMPLETED`.
   - The system verifies that no `ISSUED` or `FINALIZED` invoice is linked to the work order.
   - The system validates that the `reopenReason` is not null or empty.
4. **State / Flag Update (Clarification #300 applied):** Upon successful validation, the system performs the following state changes atomically:
   - `Workorder.status` remains semantically `COMPLETED`.
   - Sets `Workorder.isReopened = true`.
   - Finds the active `BillableScopeSnapshot` associated with the last completion and transitions its status to `SUPERSEDED` (becomes read-only historical record).
   - Revokes the work order’s "invoice-ready" flag.
5. **Editability Controls (Clarification #300 applied):** After reopening, fields become editable only according to a configured editability policy (see Data Requirements). Field-level permissions must be enforced.
6. **Auditing & Eventing:**
   - An `AuditEvent` is created, recording the `WorkorderReopened` action, the responsible user, the timestamp, and the `reopenReason`.
   - A `WorkorderReopened` domain event is published to the event bus for downstream consumers like the Accounting domain. The event must be idempotent.
7. **Outcome:** The work order is flagged as reopened (`isReopened=true`), specific fields are unlocked for editing per policy, and it cannot be invoiced until it is taken through the completion workflow again.

## Alternate / Error Flows
- **Error - Unauthorized User:** If the user lacks the `WORKORDER_REOPEN_COMPLETED` permission, the system blocks the action and displays an "Access Denied" error message. No state changes occur.
- **Error - Invalid Work Order State:** If the work order is not in the `COMPLETED` state, the "Reopen" action is unavailable or, if invoked via API, returns an error indicating an invalid state.
- **Error - Invoice Already Issued:** If an invoice has already been issued for the work order, the system blocks the action and returns an error stating, "Cannot reopen a work order that has been invoiced." (credit/rebill workflow is out of scope)
- **Error - Missing Reason:** If the user submits the action without a `reopenReason`, the system displays a validation error and prevents the workflow from proceeding until a reason is provided.

## Business Rules
- **BR1: Permission Gated:** The ability to reopen a completed work order is a privileged action, controlled by the `WORKORDER_REOPEN_COMPLETED` permission.
- **BR2: Invoice Irreversibility:** Once a work order is invoiced, it cannot be reopened through this workflow. A separate credit/rebill process must be used.
- **BR3: Mandatory Justification:** All reopen events must be justified with a non-empty reason to ensure a clear audit trail.
- **BR4: Snapshot Invalidation:** Reopening a work order invalidates the billing snapshot taken at the time of its last completion. A new snapshot must be generated upon subsequent completion.
- **BR5: Re-Completion Required:** A reopened work order must be processed through the standard completion workflow again to become eligible for invoicing.
- **BR6: Policy-Driven Field Editability:** Editable fields after reopening are defined by policy and enforced via field-level permissions. All edits must be auditable with before/after values.

## Data Requirements
- **`Workorder` Entity:**
  - `status`: Remains `COMPLETED`.
  - `isReopened: boolean`: Set true on reopen; set false on subsequent completion.
- **`BillableScopeSnapshot` Entity:**
  - `status`: Transition from `ACTIVE` to `SUPERSEDED` on reopen.
  - `supersededAt`: Timestamp of the reopen action.
  - `supersededBy`: User ID of the actor who reopened.
  - `supersededReason`: The `reopenReason`.
- **`EditabilityPolicy` (config/table) (Clarification #300 applied):**
  - Configurable per work order type and role.
  - Defines `editableFields[]` and `readOnlyFields[]`.
  - Supports effective dating and location scoping.
- **`AuditEvent` Entity:**
  - A new record with `eventType: 'WORKORDER_REOPENED'`, `entityId`, `userId`, `timestamp`, and a `details` field containing `reopenReason`.
  - For subsequent field edits: track before/after values and correlate to the reopen event via `reopenEventId`.
- **`WorkorderReopened` Domain Event (integration):**
  - `eventId`: Unique event identifier.
  - `idempotencyKey`: `workorderId` + a version/timestamp identifier for the reopen action.
  - `workorderId`: The ID of the affected work order.
  - `reopenedAt`: ISO 8601 timestamp.
  - `reopenedBy`: User ID.
  - `reopenReason`: The mandatory justification text.
  - `supersededSnapshotId`: The ID of the now-invalidated billing snapshot.

## Acceptance Criteria
- **AC1: Successful Reopen by Authorized User**
  - **Given** a Back Office Manager with `WORKORDER_REOPEN_COMPLETED` permission
  - **And** a work order exists with status `COMPLETED` and no issued invoice
  - **When** the manager reopens the work order with a valid reason, "Corrected labor hours"
  - **Then** `Workorder.isReopened` is set to `true`
  - **And** the previously active `BillableScopeSnapshot` is marked as `SUPERSEDED`
  - **And** an audit event is created with the correct user, reason, and timestamp
  - **And** a `WorkorderReopened` domain event is published.

- **AC2: Attempted Reopen by Unauthorized User**
  - **Given** a user without `WORKORDER_REOPEN_COMPLETED` permission
  - **And** a work order exists with status `COMPLETED`
  - **When** the user attempts to reopen the work order
  - **Then** the system returns an authorization error
  - **And** the work order remains unchanged.

- **AC3: Attempted Reopen of an Invoiced Work Order**
  - **Given** an authorized Back Office Manager
  - **And** a work order exists with status `COMPLETED`
  - **And** an invoice associated with that work order has been `ISSUED`
  - **When** the manager attempts to reopen the work order
  - **Then** the system returns an error indicating the work order is already invoiced
  - **And** the work order remains unchanged.

- **AC4: Reopen Action Requires a Reason**
  - **Given** an authorized Back Office Manager
  - **And** a work order exists with status `COMPLETED`
  - **When** the manager attempts to reopen the work order without providing a reason
  - **Then** the system displays a validation error
  - **And** the work order remains unchanged.

- **AC5: Field Editability Policy Enforcement**
  - **Given** a reopened work order (`isReopened=true`) and an editability policy configured
  - **When** a user attempts to edit a field
  - **Then** only fields allowed by policy + field-level permissions can be edited
  - **And** all edits are auditable with before/after values.

## Audit & Observability
- **Audit Log:** A structured audit log entry must be created for every successful reopen attempt. It must contain the `workorderId`, `userId`, `reopenReason`, and a timestamp.
- **Metrics:** A counter metric (`workorders.reopened.count`) should be incremented for each successful reopen.
- **Eventing:** The successful publication of the `WorkorderReopened` event must be monitored. Failures to publish should trigger an alert.

## Clarification Resolution
- Clarifications were captured and resolved in issue #300. The resolution is applied in this story body.

## Original Story (Unmodified – For Traceability)
# Issue #150 — [BACKEND] [STORY] Completion: Reopen Completed Workorder (Controlled)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Completion: Reopen Completed Workorder (Controlled)

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300018/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A completed workorder needs correction after completion.

## Main Flow
1. Authorized user selects 'Reopen' and provides a mandatory reason.
2. System records reopen audit event and transitions workorder to Reopened (or InProgress per policy).
3. System unlocks specific editable fields per policy.
4. System marks prior billable scope snapshot as superseded.
5. System requires re-completion and re-snapshot before invoicing.

## Alternate / Error Flows
- User lacks permission → block.
- Invoice already issued → block reopen or require credit/rebill workflow (out of scope).

## Business Rules
- Reopen is an exception workflow with strict permissions and audit.
- Reopening invalidates invoice-ready snapshot.

## Data Requirements
- Entities: Workorder, BillableScopeSnapshot, AuditEvent
- Fields: status, reopenReason, reopenedBy, reopenedAt, supersededSnapshotVersion

## Acceptance Criteria
- [ ] Only authorized users can reopen completed workorders.
- [ ] Reopen is auditable and requires a reason.
- [ ] Invoice-ready snapshot is invalidated and must be regenerated.
- [ ] Reopen emits a single WorkorderReopened event
- [ ] Any completion-related accounting state is reversible
- [ ] Invoice eligibility is revoked if not yet invoiced
- [ ] Reopen requires authorization and records reason
- [ ] Repeated reopen attempts do not emit duplicate events

## Integrations

### Accounting
- Emits Event: WorkorderReopened
- Event Type: Non-posting (reversal / invalidation signal)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Authorized reopen of a completed workorder
- Idempotency Key: workorderId + reopenVersion


## Notes for Agents
Don’t allow silent edits after completion; reopen is the controlled escape hatch.


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*