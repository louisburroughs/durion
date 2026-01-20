Title: [BACKEND] [STORY] Completion: Resolve Approval-Gated Change Requests
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/153
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev, clarification:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Service Advisor, I need to resolve all approval-gated change requests for a workorder, so that the workorder accurately reflects the authorized work and can be moved to the completion stage without ambiguity.

## Actors & Stakeholders
- **Primary Actor:** `Service Advisor`: The user responsible for managing the workorder lifecycle, communicating with the customer, and recording approval decisions.
- **Supporting Actor:** `Customer`: The individual who approves or rejects proposed changes to the workorder.
- **System Actor:** `POS System`: The system that enforces the state model, updates records, and manages completion blocks.
- **Stakeholders:**
    - `Back Office Manager`: Requires auditable records of all changes and approvals for compliance and dispute resolution.
    - `Technician`: Needs a clear and final list of authorized work items to perform.

## Preconditions
- A `Workorder` exists in an `In-Progress` state.
- The `Workorder` has one or more associated `ChangeRequest` entities, each with a status of `Pending Approval`.
- Each of these `ChangeRequest` entities is marked as `Approval-Gated`.
- The `Workorder` is currently in a `Completion Blocked` state due to these unresolved change requests.

## Functional Behavior

### Trigger
A `Service Advisor` accesses a `Workorder` and initiates the resolution process for all outstanding `ChangeRequest` entities.

### Main Workflow
1. The system presents the `Service Advisor` with a clear list of all `ChangeRequest` entities that are in the `Pending Approval` state for the selected `Workorder`.
2. For each `ChangeRequest`, the `Service Advisor` records the `Customer`'s decision (`Approved` or `Rejected`).
3. Upon recording an **`Approved`** decision:
    - The system updates the `ChangeRequest` status to `Approved`.
    - The system creates the corresponding `WorkorderItem` (e.g., a new part, labor line) and associates it with the `Workorder`.
    - The system creates an immutable `ApprovalRecord` linking the approval decision to the `ChangeRequest` and the newly created `WorkorderItem`, capturing the user and timestamp.
4. Upon recording a **`Rejected`** decision:
    - The system updates the `ChangeRequest` status to `Rejected`.
    - No `WorkorderItem` is added to the `Workorder`.
    - The system creates an immutable `ApprovalRecord` documenting the rejection, capturing the user and timestamp.
5. After all `Approval-Gated` `ChangeRequest` entities for the `Workorder` have been resolved (i.e., moved to a terminal status like `Approved` or `Rejected`), the system automatically removes the `Completion Block`.
6. The `Workorder` is now eligible for subsequent lifecycle states, such as `Ready for Invoicing` or `Completed`.

## Alternate / Error Flows
- **Customer Unreachable:** If the `Customer` cannot be contacted for a decision, the `Service Advisor` takes no action on the `ChangeRequest`. It remains in the `Pending Approval` state, and the `Workorder`'s `Completion Block` remains active.
- **Emergency Exception Override:**
    - **Trigger:** A user with `Manager` privileges invokes an `Emergency Override` for a `ChangeRequest`.
    - **Action:** The `Manager` must provide a non-empty `exceptionReason` (e.g., "Verbal approval given due to safety concern; formal approval pending").
    - **Outcome:** The system transitions the `ChangeRequest` status to `Approved-With-Exception`. The corresponding `WorkorderItem` is added, and the `Completion Block` for this specific request is resolved. The `ApprovalRecord` stores the `exceptionReason` and flags the approval as an override.

## Business Rules
- A `Workorder` cannot transition to any completion-related state (e.g., `Completed`, `Ready for Invoicing`) if it has any associated `ChangeRequest` entities with a `Pending Approval` status.
- Every `Approved` or `Approved-With-Exception` `ChangeRequest` MUST result in the creation of one or more `WorkorderItem` entities.
- A `Rejected` `ChangeRequest` MUST NOT result in the creation of any `WorkorderItem`.
- All resolution actions (`Approved`, `Rejected`, `Approved-With-Exception`) must be captured in an immutable `ApprovalRecord` for audit purposes.
- The `Emergency Override` action is restricted to users with a `Manager` role or equivalent permission.

## Data Requirements
- **`ChangeRequest` Entity:**
    - `changeRequestId` (PK)
    - `workorderId` (FK, Indexed)
    - `status` (Enum: `Pending Approval`, `Approved`, `Rejected`, `Approved-With-Exception`)
    - `isApprovalGated` (Boolean, must be `true` for this flow)
    - `description` (Text)
- **`Workorder` Entity:**
    - `workorderId` (PK)
    - `status` (Enum)
    - `completionBlockedReason` (Enum, e.g., `PendingChangeRequests`, `None`)
- **`ApprovalRecord` Entity:**
    - `approvalId` (PK)
    - `changeRequestId` (FK, Indexed)
    - `workorderId` (FK, Indexed)
    - `resolutionStatus` (Enum: `Approved`, `Rejected`, `Approved-With-Exception`)
    - `resolvedAt` (Timestamp)
    - `resolvedBy` (User ID, FK)
    - `exceptionReason` (Text, nullable)

## Acceptance Criteria
- **AC1: Completion is Blocked by Pending Requests**
    - **Given** a `Workorder` is `In-Progress` and has at least one `ChangeRequest` with status `Pending Approval`.
    - **When** a user attempts to transition the `Workorder` to a `Completed` state.
    - **Then** the system must reject the state transition and clearly indicate that unresolved change requests are the reason.

- **AC2: Successful Approval Flow**
    - **Given** a `Workorder` has a `ChangeRequest` with status `Pending Approval`.
    - **When** a `Service Advisor` records an `Approved` decision for that `ChangeRequest`.
    - **Then** the `ChangeRequest` status must be updated to `Approved`.
    - **And** a new `WorkorderItem` corresponding to the change must be added to the `Workorder`.
    - **And** an `ApprovalRecord` documenting the approval must be created and linked to the `ChangeRequest`.

- **AC3: Successful Rejection Flow**
    - **Given** a `Workorder` has a `ChangeRequest` with status `Pending Approval`.
    - **When** a `Service Advisor` records a `Rejected` decision for that `ChangeRequest`.
    - **Then** the `ChangeRequest` status must be updated to `Rejected`.
    - **And** no new `WorkorderItem` related to this change must be added to the `Workorder`.
    - **And** an `ApprovalRecord` documenting the rejection must be created.

- **AC4: Completion is Unblocked After Resolution**
    - **Given** a `Workorder` was blocked by one or more `ChangeRequests`.
    - **When** all associated `ChangeRequests` have been resolved (i.e., are no longer `Pending Approval`).
    - **Then** the `Completion Block` on the `Workorder` must be removed.
    - **And** the `Service Advisor` can now successfully transition the `Workorder` to a `Completed` state.

- **AC5: Emergency Override Flow**
    - **Given** a `Workorder` has a `ChangeRequest` with status `Pending Approval`.
    - **When** a user with `Manager` permissions applies an `Emergency Override` with a valid reason.
    - **Then** the `ChangeRequest` status must be updated to `Approved-With-Exception`.
    - **And** a new `WorkorderItem` must be added to the `Workorder`.
    - **And** the created `ApprovalRecord` must contain the provided `exceptionReason`.

## Audit & Observability
- Every state change for a `ChangeRequest` entity must be logged with the user ID, timestamp, and the old and new states.
- A structured event, `ChangeRequestResolved`, must be emitted when a `ChangeRequest` is resolved. The event payload must include `workorderId`, `changeRequestId`, `resolutionStatus`, and `resolvedBy`.
- The `ApprovalRecord` entity serves as the primary, immutable audit trail for all customer-facing decisions and overrides related to workorder changes. Access to modify this record post-creation should be highly restricted.

## Original Story (Unmodified – For Traceability)
# Issue #153 — [BACKEND] [STORY] Completion: Resolve Approval-Gated Change Requests

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Completion: Resolve Approval-Gated Change Requests

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Back Office

## Trigger
Workorder contains one or more approval-gated change requests.

## Main Flow
1. User views outstanding change requests tied to the workorder.
2. User submits requests for customer approval (if not already submitted).
3. System records approval/rejection outcomes.
4. Approved requests add authorized items to the workorder; rejected requests remain excluded.
5. System clears the completion block when all requests are resolved.

## Alternate / Error Flows
- Customer unreachable → leave request pending and keep workorder incomplete.
- Emergency exception policy invoked → record exception and proceed per policy.

## Business Rules
- All approval-gated requests must be resolved before completion (unless exception).
- Approvals must be linked to added items.

## Data Requirements
- Entities: ChangeRequest, ApprovalRecord, WorkorderItem
- Fields: changeRequestId, status, approvalId, resolutionAt, resolutionBy, exceptionReason

## Acceptance Criteria
- [ ] Pending change requests block completion.
- [ ] Resolved approvals correctly add or exclude items.
- [ ] Resolution is auditable.

## Notes for Agents
Make resolution visible; operators should never guess what’s blocking completion.


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