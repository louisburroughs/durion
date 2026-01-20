Title: [BACKEND] [STORY] Estimate: Revise Estimate Prior to Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/170
Labels: type:story, domain:workexec, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a Service Advisor, I want to revise a draft or pending estimate, so that I can correct errors or update the scope before it becomes a committed work order, while maintaining a clear audit trail of all changes.

## Actors & Stakeholders
- **Primary Actor**: Service Advisor - The user performing the revision of the estimate.
- **System Actors**:
    - POS System: Facilitates the estimate lifecycle, including versioning and state transitions.
    - Audit Subsystem: Records all revision events for traceability.
- **Stakeholders**:
    - Customer: Receives the accurate and updated estimate for approval.
    - Shop Manager: Relies on accurate estimates for resource planning and financial forecasting.
    - Auditor: Reviews the estimate history to ensure compliance and track changes.

## Preconditions
- An estimate exists in the system with a unique identifier.
- The estimate is in a state that permits revision, such as `Draft` or `PendingApproval`.
- The Service Advisor is authenticated and has the necessary permissions to create and modify estimates.

## Functional Behavior
### Trigger
The Service Advisor selects the "Revise" action on an estimate that is in a `Draft` or `PendingApproval` state.

### Main Success Scenario
1.  The system validates that the estimate's current state (`Draft` or `PendingApproval`) allows revision.
2.  The system creates a new version of the estimate. This new version becomes the active, editable record.
3.  The system links the new version to the previous one, preserving the chronological revision history (e.g., `v2` points to `v1` as its predecessor).
4.  The previous version's state is transitioned to `Archived` and it becomes immutable.
5.  The new, active version's state is set to `Draft`.
6.  If the previous state was `PendingApproval`, the system invalidates any associated approval requests or signatures.
7.  The Service Advisor modifies the estimate's details (e.g., adds/removes line items, changes quantities, updates pricing, adds notes).
8.  With each modification, the system automatically recalculates all dependent totals (e.g., subtotal, taxes, grand total).
9.  Upon saving, the system persists the revised estimate and records an audit event for the revision action, capturing the user, timestamp, and a summary of the change.

## Alternate / Error Flows
- **Attempt to Revise a Non-Revisable Estimate**:
    - **Trigger**: The Service Advisor attempts to revise an estimate in a terminal state (e.g., `ConvertedToWorkOrder`, `Rejected`, `Closed`).
    - **Outcome**: The system blocks the action and displays a clear error message explaining why the estimate cannot be revised (e.g., "This estimate has already been converted to a work order and cannot be revised. Please create a change order instead."). This behavior is subject to the decision in OQ1.
- **Insufficient Permissions**:
    - **Trigger**: An authenticated user without `estimate:revise` permission attempts the action.
    - **Outcome**: The system denies the request, displays an "Access Denied" message, and logs a security warning event.
- **User Abandons Revision**:
    - **Trigger**: The Service Advisor initiates a revision but navigates away or cancels the action before saving.
    - **Outcome**: The newly created draft version is discarded. The original, pre-revision version remains the active version of the estimate with its state unchanged.

## Business Rules
- An estimate can only be revised if its status is `Draft` or `PendingApproval`.
- Each revision must create a new, distinct version of the estimate. The prior version must be preserved as an immutable historical record.
- The system must maintain a sequential version number for each revision (e.g., 1, 2, 3...).
- The most recent version is always considered the "active" or "current" version of the estimate.
- Revising an estimate that is in a `PendingApproval` state MUST invalidate any outstanding approval requests and revert the estimate's status to `Draft`.

## Data Requirements
- **`Estimate` Entity**: Represents the lifecycle container for a single estimate.
    - `estimateId`: (PK) Unique identifier for the estimate.
    - `customerId`: (FK) Reference to the customer.
    - `vehicleId`: (FK) Reference to the vehicle.
    - `activeVersionId`: (FK) Reference to the current `EstimateVersion`.
- **`EstimateVersion` Entity**: Represents a specific, point-in-time version of an estimate.
    - `estimateVersionId`: (PK) Unique identifier for this specific version.
    - `estimateId`: (FK) Parent `Estimate`.
    - `versionNumber`: (Integer) Sequential version number (e.g., 1, 2, 3).
    - `status`: (String Enum) e.g., `Draft`, `PendingApproval`, `Approved`, `Rejected`, `ConvertedToWorkOrder`, `Archived`.
    - `lineItems`: (JSON/Collection) The detailed list of parts and labor.
    - `totals`: (Object) Calculated totals including subtotal, taxes, and grand total.
    - `notes`: (Text) Any notes for this version.
    - `priorVersionId`: (FK to `EstimateVersion`) Self-referencing link to the previous version, null for v1.
    - `createdBy`: (String) User ID of the creator.
    - `createdAt`: (Timestamp) Creation timestamp of this version.

## Acceptance Criteria
- **AC1: Successful Revision of a Draft Estimate**
    - **Given** an estimate exists at version 1 with a status of `Draft`.
    - **When** the Service Advisor revises the estimate by adding a new line item and saves.
    - **Then** the system creates a new `EstimateVersion` record (version 2) with the updated line item.
    - **And** the original version 1 record is updated to a status of `Archived`.
    - **And** the new version 2 record has a status of `Draft` and becomes the active version for the estimate.
- **AC2: Revision of a "Pending Approval" Estimate Invalidates Approval**
    - **Given** an estimate exists at version 1 with a status of `PendingApproval`.
    - **When** the Service Advisor initiates a revision.
    - **Then** a new `EstimateVersion` (version 2) is created with a status of `Draft`.
    - **And** any outstanding approval requests associated with version 1 are marked as `Invalidated`.
    - **And** version 1 is marked as `Archived`.
- **AC3: Attempt to Revise a Converted Estimate is Blocked**
    - **Given** an estimate has a status of `ConvertedToWorkOrder`.
    - **When** the Service Advisor attempts to revise it.
    - **Then** the system prevents the action and displays an informative error message.
- **AC4: Revision History is Preserved and Accessible**
    - **Given** an estimate has been revised three times and is currently on version 4.
    - **When** a user with appropriate permissions views the estimate's history.
    - **Then** the system can retrieve and display read-only views of versions 1, 2, and 3.

## Audit & Observability
- **Audit Events to be Logged**:
    - `estimate.revision.initiated`: Fired when a revision process begins.
    - `estimate.revision.completed`: Fired when a revision is successfully saved.
    - `estimate.approval.invalidated`: Fired when a revision causes a pending approval to be cancelled.
- **Key Fields for Audit Logs**:
    - `traceId`, `timestamp`, `eventType`
    - `userId`: The ID of the Service Advisor performing the action.
    - `estimateId`, `priorVersionNumber`, `newVersionNumber`
    - `sourceIpAddress`
- **Metrics to be Emitted**:
    - `pos.estimate.revisions.count`: (Counter) Incremented for each successful revision.
    - `pos.estimate.revisions.error.count`: (Counter) Incremented for failed revision attempts, tagged by reason (e.g., `permission_denied`, `invalid_state`).

## Open Questions
- **OQ1: Revision Policy for Converted Estimates**
    - What is the definitive business policy for handling revision attempts on an estimate that has already been `Approved` and `ConvertedToWorkOrder`?
    - **Option A**: Strictly disallow revision. The user must initiate a separate "Change Order" process for the associated Work Order.
    - **Option B**: Automatically trigger a "Change Order" workflow from this action.
    - **Decision Needed**: This decision impacts the behavior defined in Alternate Flow #1 and AC3. Option A is the safer default assumption, but this must be confirmed.

## Original Story (Unmodified – For Traceability)
# Issue #170 — [BACKEND] [STORY] Estimate: Revise Estimate Prior to Approval

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Revise Estimate Prior to Approval

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
Service Advisor

## Trigger
A Draft or PendingApproval estimate requires changes (scope, price, quantities, fees, or notes).

## Main Flow
1. User opens the estimate and selects 'Revise'.
2. System creates a new estimate version (or revision record) linked to the prior version.
3. User edits line items and/or terms.
4. System recalculates totals and updates revision metadata.
5. System invalidates any prior approvals and sets state back to Draft (or Revision state) per policy.

## Alternate / Error Flows
- Estimate is already promoted to workorder → disallow revision or create a change request workflow (policy).
- User lacks permission to revise after approval submission → block and log.

## Business Rules
- Revision must preserve history and allow comparing versions.
- Any revision after approval invalidates approval.
- Revision increments version and records who/when/why.

## Data Requirements
- Entities: Estimate, EstimateRevision, ApprovalRecord, AuditEvent
- Fields: estimateId, version, status, revisionReason, revisedBy, revisedDate, priorVersionRef

## Acceptance Criteria
- [ ] System preserves revision history and allows retrieving prior versions.
- [ ] Any existing approval is invalidated on revision and recorded.
- [ ] Revised estimate totals are recalculated and stored.

## Notes for Agents
Revision history is a core audit artifact—do not overwrite approved snapshots.


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