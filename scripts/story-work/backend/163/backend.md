Title: [BACKEND] [STORY] Promotion: Handle Partial Approval Promotion
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/163
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
**As a** Service Advisor,
**I want to** generate a Work Order from a partially approved Estimate,
**So that** I can begin work on the customer-approved scope immediately while preserving the unapproved items as deferred scope for future follow-up and revenue opportunities.

## Actors & Stakeholders
- **Primary Actor**: `Service Advisor` – The user who initiates the promotion of an Estimate to a Work Order.
- **System**: The POS / Shop Management System – The platform responsible for executing the business logic, creating entities, and maintaining data integrity.
- **Stakeholders**:
    - `Customer` – Benefits from clear communication about what work was approved and what was deferred.
    - `Technician` – Receives a clear, unambiguous Work Order containing only the scope they are authorized to perform.
    - `Shop Manager` – Maintains visibility into the full sales opportunity, including deferred work that can be followed up on later.

## Preconditions
- An `Estimate` exists in the system with an overall status of `PartiallyApproved`.
- The `Estimate` must contain at least one `EstimateLine` with a status of `Approved`.
- The `Estimate` must contain at least one `EstimateLine` with a non-approved status (e.g., `PendingApproval`, `Declined`).
- The `Service Advisor` is authenticated and has the necessary permissions to create Work Orders.

## Functional Behavior

### Happy Path: Promotion of Partially Approved Estimate
1.  **Trigger**: The `Service Advisor` selects the "Promote Approved Scope" action for an `Estimate` in the `PartiallyApproved` state.
2.  **Validation**: The `System` confirms there is at least one `EstimateLine` with `status: Approved`.
3.  **Work Order Creation**: The `System` creates a new `WorkOrder` entity, linking it back to the source `Estimate` (e.g., via `sourceEstimateId`). The new `WorkOrder` is created with an initial status of `Open` or equivalent.
4.  **Scope Transfer**:
    - For each `EstimateLine` with `status: Approved`, the `System` creates a corresponding `WorkOrderLine` on the new `WorkOrder`.
    - A clear, persistent reference is established between the new `WorkOrderLine` and its originating `EstimateLine` (e.g., `sourceEstimateLineId`).
    - The status of the original `Approved` `EstimateLine` is updated to `Promoted`.
5.  **Deferred Scope Handling**:
    - For each `EstimateLine` that was not approved (e.g., `PendingApproval`, `Declined`), the `System` updates its status to `Deferred`.
    - These `Deferred` lines remain exclusively on the `Estimate` and are NOT copied to the `WorkOrder`.
6.  **State Transition**: The `System` updates the overall status of the source `Estimate` to `PromotedWithDeferredItems`.
7.  **User Feedback**: The `System` notifies the `Service Advisor` of the successful creation of the `WorkOrder` and provides a clear link or navigation path to view it. The view of the original `Estimate` clearly distinguishes between `Promoted` and `Deferred` items.

## Alternate / Error Flows
- **Flow: Attempting Promotion with No Approved Items**
    - **Trigger**: A user attempts to "Promote Approved Scope" on an `Estimate` where no line items have the `Approved` status.
    - **Outcome**: The `System` prevents the action and displays a user-friendly error message, such as "Promotion failed: At least one item must be approved to create a Work Order." No `WorkOrder` is created, and the `Estimate`'s state remains unchanged.

- **Flow: Action Unavailable in Incorrect State**
    - **Trigger**: A user views an `Estimate` that is not in the `PartiallyApproved` state (e.g., it is `Draft`, `FullyApproved`, or `Declined`).
    - **Outcome**: The "Promote Approved Scope" action is not available (e.g., the button is disabled or hidden).

## Business Rules
- Only `EstimateLine`s with an `Approved` status can be promoted to a `WorkOrder`.
- `Deferred` `EstimateLine`s must remain associated with the original `Estimate` for tracking and future sales opportunities.
- The creation of a `WorkOrder` from an `Estimate` is an immutable event; the resulting `WorkOrder` and its lines are new records, not modifications of the `Estimate`.
- The subsequent approval and promotion of `Deferred` items is a separate business process (e.g., a "Change Order") and is considered out of scope for this story.
- Traceability between `WorkOrderLine`s and their source `EstimateLine`s must be permanent and non-repudiable.

## Data Requirements
This feature impacts the following data entities and their states:

- **`Estimate` Entity**:
    - Required Fields: `estimateId`, `status`
    - State Model for `status`: `Draft` -> `Presented` -> `PartiallyApproved` -> `PromotedWithDeferredItems`

- **`EstimateLine` Entity**:
    - Required Fields: `estimateLineId`, `estimateId`, `status`
    - State Model for `status`: `PendingApproval` -> `Approved` -> `Promoted` OR `PendingApproval` -> `Declined` -> `Deferred`
    - New/Updated Fields: `promotedToWorkOrderLineId` (Nullable foreign key to `WorkOrderLine`)

- **`WorkOrder` Entity**:
    - Required Fields: `workOrderId`, `status`
    - New/Updated Fields: `sourceEstimateId` (Non-nullable foreign key to `Estimate`)

- **`WorkOrderLine` Entity**:
    - Required Fields: `workOrderLineId`, `workOrderId`
    - New/Updated Fields: `sourceEstimateLineId` (Non-nullable foreign key to `EstimateLine`)

## Acceptance Criteria
- **AC1: Successful partial promotion generates a correct Work Order**
    - **Given** an `Estimate` exists with status `PartiallyApproved`, containing two `Approved` lines and one `Declined` line.
    - **When** the Service Advisor promotes the approved scope to a Work Order.
    - **Then** a new `WorkOrder` is created with a link to the source `Estimate`.
    - **And** the `WorkOrder` contains exactly two `WorkOrderLine`s, corresponding to the two `Approved` `EstimateLine`s.
    - **And** the original `Estimate`'s status is updated to `PromotedWithDeferredItems`.
    - **And** the `Declined` `EstimateLine`'s status is updated to `Deferred`.
    - **And** the `Approved` `EstimateLine`s' statuses are updated to `Promoted`.

- **AC2: Deferred items remain on the Estimate and are clearly marked**
    - **Given** an `Estimate` has been promoted via the partial approval flow.
    - **When** a user views the original `Estimate`.
    - **Then** the line item that was not approved is displayed with a status of `Deferred`.
    - **And** the line items that were approved are displayed with a status of `Promoted` and include a link to the corresponding `WorkOrder`.

- **AC3: Data traceability is maintained between entities**
    - **Given** a `WorkOrder` was created from a partially approved `Estimate`.
    - **When** an application service queries a `WorkOrderLine` from that `WorkOrder`.
    - **Then** the `WorkOrderLine` record contains a non-null `sourceEstimateLineId` that correctly points to the originating `EstimateLine`.

- **AC4: Promotion is blocked if no items are approved**
    - **Given** an `Estimate` is in the `PartiallyApproved` state, but through some other process, all of its lines are now in a non-approved state (e.g. `Declined`).
    - **When** the Service Advisor attempts to promote the approved scope.
    - **Then** the system displays an error message indicating no items are approved.
    - **And** no `WorkOrder` is created.
    - **And** the state of the `Estimate` and its lines remains unchanged.

## Audit & Observability
- **Audit Trail**: Upon successful promotion, a structured audit event must be logged with the following information:
    - `eventType`: `estimate.promoted_partially`
    - `timestamp`: The UTC timestamp of the event.
    - `principalId`: The identifier for the `Service Advisor` who performed the action.
    - `resourceIds`: `{ "sourceEstimateId": "...", "createdWorkOrderId": "..." }`
    - `eventPayload`: A structured object containing a list of `promotedLineItemIds` and `deferredLineItemIds`.
- **Metrics**: The system should expose a metric `workexec_promotions_total` with a label `type="partial"` to monitor the frequency of this business event.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #163 — [BACKEND] [STORY] Promotion: Handle Partial Approval Promotion

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotion: Handle Partial Approval Promotion

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
An estimate is PartiallyApproved and user promotes approved scope.

## Main Flow
1. System creates workorder using only approved scope items.
2. System marks unapproved items as deferred and keeps them on the estimate.
3. System allows later approval of deferred items to create a change request or supplemental workorder items per policy.
4. System maintains traceability between deferred items and later approvals.
5. System shows clear indicators of partial promotion.

## Alternate / Error Flows
- Later approvals conflict with work already performed → require advisor resolution before adding.

## Business Rules
- Only approved scope is promotable.
- Deferred items remain visible but non-executable until approved.
- Later additions should flow through approval gates.

## Data Requirements
- Entities: ApprovedScope, Workorder, Estimate, ChangeRequest
- Fields: authorizedFlag, deferredFlag, scopeVersion, changeRequestId, status

## Acceptance Criteria
- [ ] Only approved items appear on the initial workorder.
- [ ] Deferred items remain on estimate and can be approved later.
- [ ] System maintains audit trace from deferred items to later changes.

## Notes for Agents
Partial approval adds “later” work—route that through change/approval, not silent edits.


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