Title: [BACKEND] [STORY] Completion: Complete Workorder and Record Audit
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/151
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
**I want to** formally mark a work order as "Completed" in the system,
**so that** I can lock the scope of work, establish an official completion time, and trigger downstream processes like accounting and customer notification.

This action represents the finalization of all hands-on work and creates an immutable, auditable record of what was done, by whom, and when.

## Actors & Stakeholders

- **Primary Actor:** `Service Advisor` (or `Shop Manager`) - The user role responsible for managing the work order lifecycle and performing the completion action.
- **System Actor:** `POS System` - The application responsible for executing the state transition, creating audit logs, and emitting integration events.
- **Stakeholder:** `Accounting System` - A downstream system that consumes the `WorkCompleted` event to manage financial records (e.g., moving Work-in-Progress costs to Finished Goods).

## Preconditions

1. The user must have the necessary permissions to complete a work order (e.g., `WORKORDER_COMPLETE`).
2. The target `Workorder` must exist and be in a state that allows completion (e.g., `WORK_IN_PROGRESS`, `AWAITING_INSPECTION`).
3. All prerequisite validation checks for completion must pass. (Note: The specific rules for these checks are defined in a separate validation story).

## Functional Behavior

### Trigger
The `Service Advisor` initiates the "Complete Workorder" action via the user interface for a specific work order that meets all preconditions.

### Main Success Scenario
1.  **Request Reception:** The system receives a request to transition the work order to the `Completed` state. The request includes the `workorderId`, the completing user's identity (`completedBy`), and any optional `completionNotes` or inspection results.
2.  **State Transition:** The system atomically updates the `Workorder` entity's status from its current state to `Completed`.
3.  **Data Persistence:** The system records the following information:
    - `status`: Set to `Completed`.
    - `completedAt`: Set to the current system timestamp (UTC).
    - `completedBy`: Set to the ID of the user who initiated the action.
    - `completionNotes`: Stored as provided.
4.  **Scope Lock:** The system applies a lock to the `Workorder`, preventing further modifications to billable items (labor, parts, fees) unless a specific, permission-controlled "reopen" workflow is initiated.
5.  **Audit Log Creation:** The system creates an immutable `AuditEvent` record for this state transition, capturing the `workorderId`, the `fromState` and `toState` (`Completed`), the user, the timestamp, and the reason (`Work Order Completed`).
6.  **Event Emission:** The system publishes a `WorkCompleted` event to the message bus for downstream consumers. This event is emitted exactly once per successful completion transition.

## Alternate / Error Flows

- **Flow: Attempting to complete a non-completable work order**
  - **Trigger:** The user attempts to complete a work order that is already in a `Completed`, `Invoiced`, or `Closed` state.
  - **Outcome:** The system rejects the request with an error indicating the work order is not in a valid state for completion. No state change, audit log, or event occurs.

- **Flow: Precondition validation fails**
  - **Trigger:** The user attempts to complete a work order, but a required business rule (e.g., all parts must be assigned, all labor logged) is not met.
  - **Outcome:** The system rejects the request with a specific error message detailing the failed validation(s). No state change, audit log, or event occurs. (This flow relies on the implementation of the separate validation story).

- **Flow: User lacks permissions**
  - **Trigger:** A user without the `WORKORDER_COMPLETE` permission attempts the action.
  - **Outcome:** The system rejects the request with a `403 Forbidden` or equivalent authorization error.

## Business Rules

- **State Immutability:** The `Completed` state is a terminal state for the execution phase. A `Completed` work order cannot revert to a `WORK_IN_PROGRESS` state except through a formal, audited "reopen" process.
- **Idempotency:** The `WorkCompleted` event must be emitted exactly once for each unique completion of a work order. If the completion action is retried, it must not result in a duplicate event. The `idempotencyKey` for the event shall be a combination of `workorderId` and a unique identifier for this specific completion instance (e.g., `workorderId` + `completionTimestamp` or a version counter).
- **Financial Neutrality:** This `Complete` action is a work execution event, not a financial one. It MUST NOT create Accounts Receivable (AR), recognize revenue, or generate a customer invoice. These are responsibilities of the `Billing` domain, which acts upon a `Completed` work order.
- **WIP Accounting:** If Work-in-Progress (WIP) accounting is enabled, the emitted `WorkCompleted` event serves as the trigger for the Accounting system to finalize WIP calculations (e.g., move costs from WIP to a Finished Goods/Services asset account). The `workexec` domain is only responsible for providing the final data snapshot in the event payload.

## Data Requirements

- **Entity: `Workorder`**
  - `workorderId` [UUID]: Primary identifier.
  - `status` [String]: The lifecycle state of the work order. Transitions to `Completed`.
  - `completedAt` [TimestampZ]: The UTC timestamp when the work order was completed.
  - `completedBy` [UUID]: The ID of the user who completed the work order.
  - `completionNotes` [Text]: Optional notes provided by the user upon completion.

- **Entity: `AuditEvent`**
  - `eventId` [UUID]: Primary identifier for the audit record.
  - `entityType` [String]: "Workorder".
  - `entityId` [UUID]: The `workorderId`.
  - `eventType` [String]: "StateTransition".
  - `eventTimestamp` [TimestampZ]: UTC timestamp of the event.
  - `userId` [UUID]: The user who triggered the event.
  - `details` [JSONB]: A structured field containing `{ "fromState": "...", "toState": "Completed" }`.

- **Event Payload: `WorkCompleted`**
  - `eventId` [UUID]: Unique ID for this event instance.
  - `eventType` [String]: "WorkCompleted".
  - `eventTimestamp` [TimestampZ]: UTC timestamp of event creation.
  - `sourceDomain` [String]: "workexec".
  - `idempotencyKey` [String]: A key to prevent duplicate processing (e.g., `workorderId:completion_1`).
  - `payload` [JSON]:
    - `workorderId` [UUID]
    - `completedAt` [TimestampZ]
    - `completedBy` [UUID]
    - `finalBillableScope` [Object]: A snapshot of all billable items (parts, labor, fees) and their final calculated totals at the moment of completion.

## Acceptance Criteria

**Scenario 1: Successful Work Order Completion**
- **Given** a `Workorder` exists with the status `WORK_IN_PROGRESS`.
- **And** the logged-in user is a `Service Advisor` with `WORKORDER_COMPLETE` permissions.
- **And** all completion preconditions are met.
- **When** the user performs the "Complete Workorder" action.
- **Then** the `Workorder` status is updated to `Completed`.
- **And** the `completedAt` and `completedBy` fields are populated correctly.
- **And** an `AuditEvent` is created documenting the state transition to `Completed`.
- **And** a single `WorkCompleted` event is published, containing the final billable scope.

**Scenario 2: Completed Work Order is Locked from Edits**
- **Given** a `Workorder` has a status of `Completed`.
- **When** a user attempts to add a new billable labor item to the work order.
- **Then** the system rejects the operation with an error indicating the work order is locked.

**Scenario 3: Attempt to Complete an Already Completed Work Order**
- **Given** a `Workorder` has a status of `Completed`.
- **And** the logged-in user is a `Service Advisor`.
- **When** the user attempts to perform the "Complete Workorder" action again on the same work order.
- **Then** the system returns an error indicating the work order is already complete.
- **And** the `Workorder`'s state and data remain unchanged.
- **And** no new `WorkCompleted` event is published.

**Scenario 4: Accounting Event Contains Required Data**
- **Given** a work order has been successfully completed.
- **When** the downstream `Accounting System` inspects the `WorkCompleted` event.
- **Then** the event payload must contain the `workorderId`, `completedAt` timestamp, and a `finalBillableScope` object with accurate totals.
- **And** the event must have a unique `idempotencyKey`.

## Audit & Observability

- **Audit Log:** A permanent, immutable audit trail entry MUST be created for every successful work order completion. This record must capture the `workorderId`, `fromState`, `toState` (`Completed`), `userId`, and `timestamp`.
- **Integration Event:** The `WorkCompleted` event is a critical integration point.
  - **Event Name:** `WorkCompleted`
  - **Topic/Queue:** `workorder.events`
  - **Observability:** The emission of this event must be logged with its `eventId` and `workorderId`. Monitoring should be in place to alert on failures to publish this event after a successful completion.
- **Metrics:**
  - `workorders_completed_total`: A counter metric incremented for each successful completion.
  - `workorder_completion_errors_total`: A counter metric incremented for any failures during the completion process, tagged by error type (e.g., `permission_denied`, `invalid_state`).

## Open Questions

- None at this time.

## Original Story (Unmodified – For Traceability)
# Issue #151 — [BACKEND] [STORY] Completion: Complete Workorder and Record Audit

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Completion: Complete Workorder and Record Audit

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300007/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Shop Manager

## Trigger
Completion preconditions are satisfied.

## Main Flow
1. User selects 'Complete Workorder'.
2. System transitions workorder to Completed state.
3. System records completion timestamp and completing user.
4. System stores completion notes and optional inspection outcomes.
5. System locks execution edits except via controlled reopen workflow.

## Alternate / Error Flows
- Completion attempted with failing preconditions → block (covered by validation story).

## Business Rules
- Completion transition must be explicit and auditable.
- Completion locks billable scope unless reopened with permissions.

## Data Requirements
- Entities: Workorder, AuditEvent, InspectionRecord
- Fields: status, completedAt, completedBy, completionNotes, inspectionOutcome

## Acceptance Criteria
- [ ] Workorder transitions to Completed only when preconditions pass.
- [ ] Completion is auditable (who/when).
- [ ] Workorder is locked against uncontrolled edits.
- [ ] WorkCompleted event is emitted once per completion
- [ ] Event includes final billable scope and totals snapshot
- [ ] WIP accounting (if enabled) is finalized correctly
- [ ] Completion does not create AR or revenue
- [ ] Repeated completion attempts do not duplicate events

## Integrations

### Accounting
- Emits Event: WorkCompleted
- Event Type: Non-posting or Posting (WIP → Finished, if enabled)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Workorder transitioned to Completed state
- Idempotency Key: workorderId + completionVersion


## Notes for Agents
Treat completion as a state transition with strong validations and audit.


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