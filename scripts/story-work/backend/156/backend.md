Title: [BACKEND] [STORY] Execution: Request Additional Work and Flag for Approval
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/156
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev

## Story Intent
As a Service Advisor, manage Technician requests for out-of-scope work so customer approval is captured, billing is controlled, and an auditable trace of scope changes exists.

## Actors & Stakeholders
- **Technician:** Initiates additional work requests and can flag Emergency/Safety items.
- **Service Advisor:** Reviews requests, consults customer, records approval/denial.
- **System:** Enforces workflow, blocking execution until approval or exception handling is completed.
- **Customer:** Approves or declines additional work and associated costs.
- **Auditors:** Require traceable records of scope changes, approvals, and exceptions.

## Preconditions
1. Work Order exists with status `InProgress`.
2. Technician assigned to the Work Order and authenticated.
3. Approval capability configured and available.

## Functional Behavior
### Happy Path: Request and Approval
1. Trigger: Technician selects “Request Additional Work” on an active Work Order.
2. System presents a `ChangeRequest` form; Technician adds new `WorkOrderItem`s (parts/labor) marked `PendingApproval` with description.
3. System saves `ChangeRequest` as `AwaitingAdvisorReview`, associates to Work Order, and generates a supplemental PDF estimate containing only the requested items.
4. System notifies Service Advisor of pending review.
5. Service Advisor consults customer and records decision with an approval note (note is the approval artifact).
6. On approve: System sets `ChangeRequest` to `Approved`, moves associated items from `PendingApproval` to `Open/ReadyToExecute`, and links items to the `ChangeRequest` for traceability.
7. On decline: System sets `ChangeRequest` to `Declined`, moves associated items to `Cancelled`, keeps them out of the billable scope.

### Emergency/Safety Exception
1. Trigger: Technician flags a work order item as Emergency/Safety (vehicle unsafe to start/operate).
2. Any Technician may flag; flagging requires photo evidence and notes. If photo impossible, Technician must select “Photo not possible” and supply notes.
3. System requires customer acknowledgment of denial for any Emergency/Safety item before Work Order closure/vehicle return if the customer declines.
4. System still generates a supplemental PDF estimate for the flagged items and routes to Service Advisor; approval artifact is a Service Advisor note.

### Validation / Blocking
- Items marked `PendingApproval` cannot be executed or consume inventory until approved (unless Emergency/Safety proceeds after customer acknowledgment flow as defined above).
- ChangeRequest creation requires description and at least one item.

## Alternate / Error Flows
- Invalid request: missing description/items → reject with validation message.
- Customer declines: `ChangeRequest` → `Declined`; items → `Cancelled`.
- Emergency/Safety declined: must capture customer denial acknowledgment before closure/return; items not executed.

## Business Rules
1. No billable execution or part consumption for additional scope until tied to an `Approved` `ChangeRequest` (unless Emergency/Safety documented and acknowledged denial before closure).
2. Every added `WorkOrderItem` after initial authorization links to a unique `ChangeRequest` for traceability.
3. Supplemental estimate for additional work must be an updated PDF.
4. Approval artifact is a Service Advisor note (sufficient for approval/denial recording).
5. Emergency/Safety definition: any condition making the vehicle unsafe to start/operate; any Technician may flag.
6. Emergency/Safety documentation: photo evidence + notes required; if photo not possible, explicitly mark and supply notes.
7. If Emergency/Safety work is declined, customer denial must be acknowledged in-system before closing the Work Order and returning the vehicle.

## Data Requirements
- `ChangeRequest`: `changeRequestId`, `workOrderId`, `requestedByUserId`, `requestedAt`, `status` (`AwaitingAdvisorReview`, `Approved`, `Declined`, `Cancelled`), `description` (required), `isEmergencyException` (bool), `exceptionReason`, `approvalNote`, `supplementalEstimatePdfId` (ref), `approvalId` (optional link if needed).
- `WorkOrderItem` (enhancements): `changeRequestId` (FK), `status` includes `PendingApproval`, `Open`, `Cancelled`.
- Relationships: WorkOrder 1:M ChangeRequest; ChangeRequest 1:M WorkOrderItem.

## Acceptance Criteria
**AC1: Create request and block execution**
- Given a Technician on an `InProgress` Work Order
- When they create a ChangeRequest with parts/labor items and description
- Then a ChangeRequest is saved as `AwaitingAdvisorReview`, items set `PendingApproval`, supplemental PDF estimate generated, and items cannot be executed/consumed until approved.

**AC2: Approve additional work**
- Given a ChangeRequest `AwaitingAdvisorReview`
- When a Service Advisor approves and records a note
- Then ChangeRequest → `Approved`; associated items → `Open/ReadyToExecute`; traceability to ChangeRequest retained.

**AC3: Decline additional work**
- Given a ChangeRequest `AwaitingAdvisorReview`
- When a Service Advisor declines and records a note
- Then ChangeRequest → `Declined`; associated items → `Cancelled`; not billable.

**AC4: Emergency/Safety flag and documentation**
- Given a Technician flags an item as Emergency/Safety because the vehicle is unsafe to start/operate
- When submitting the ChangeRequest
- Then the system requires photo evidence and notes (or “photo not possible” + notes), generates a supplemental PDF, and routes to Advisor.

**AC5: Emergency/Safety declined requires acknowledgment**
- Given an Emergency/Safety item was flagged and the customer declines the work
- When attempting to close the Work Order / return the vehicle
- Then the system requires recorded customer denial acknowledgment before closure.

**AC6: Approval artifact**
- Given a ChangeRequest is approved or declined
- When the Service Advisor records the outcome
- Then the Advisor note is stored as the approval artifact and linked to the ChangeRequest.

## Audit & Observability
- Audit events: `ChangeRequestCreated`, `ChangeRequestStatusChanged`, `WorkOrderItemLinkedToChangeRequest`, `SupplementalEstimateGenerated`, `EmergencyFlagSet`, `CustomerDenialAcknowledged`.
- Metrics: count of ChangeRequests, approval/denial ratio, avg time to decision, Emergency/Safety frequency.

## Open Questions
- None. Clarification #322 resolved prior questions.

---
## Original Story (Unmodified – For Traceability)
# Issue #156 — [BACKEND] [STORY] Execution: Request Additional Work and Flag for Approval

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Request Additional Work and Flag for Approval

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
Technician / Service Advisor

## Trigger
Technician identifies additional work required beyond authorized scope.

## Main Flow
1. Technician creates a change request describing additional parts/labor needed.
2. System generates a supplemental estimate view for the additional work.
3. System routes request to advisor for customer approval using the approval capability.
4. System prevents execution of additional billable items until approved (policy).
5. Once approved, system adds authorized items to the workorder with traceability.

## Alternate / Error Flows
- Customer declines additional work → request closed; workorder continues with original scope.
- Emergency/safety exception → policy may allow proceed with documentation.

## Business Rules
- Additional work requires explicit customer approval unless exception policy applies.
- Change requests must be traceable and auditable.
- Added items must reference approval artifacts.

## Data Requirements
- Entities: ChangeRequest, Estimate, ApprovalRecord, WorkorderItem
- Fields: changeRequestId, description, requestedBy, requestedAt, approvalId, addedItemSeqIds, exceptionReason

## Acceptance Criteria
- [ ] Change requests can be created and tracked.
- [ ] Approval gate blocks unauthorized scope expansion.
- [ ] Approved additional work is added with traceability.

## Notes for Agents
This is where scope creep becomes revenue leakage—enforce gates.


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