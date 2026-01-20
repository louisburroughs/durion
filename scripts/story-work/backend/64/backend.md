Title: [BACKEND] [STORY] Workexec: Propagate Assignment Context to Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/64
Labels: backend, story-implementation, user, type:story, domain:workexec, status:ready-for-dev

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
As the System, I want to enrich newly created Workorders with operational context (Location, Resource, Mechanic assignments) from an external assignment system, and keep this context synchronized until work commences, to ensure accurate execution, tracking, and reporting.

## Actors & Stakeholders
- **System (Work Execution Service)**: The primary actor responsible for creating, managing, and executing Workorders. It owns the Workorder lifecycle.
- **System (Shop Management Service)**: The external system of record for operational assignments. It is the authoritative source for the context data that is propagated to the Workorder.
- **Service Advisor / Shop Manager**: Indirect stakeholders who manage assignments in the Shop Management Service and expect the context to be correctly reflected on the Workorder for mechanics.
- **Mechanic**: The end-consumer of the Workorder data, who relies on accurate assignment context to perform their job.

## Preconditions
- A `Workorder` has been created in the Work Execution system.
- The `Workorder` is in a pre-execution state (e.g., `PENDING`, `SCHEDULED`).
- A message-based integration contract exists for the Work Execution service to consume `AssignmentUpdated` events from the Shop Management service.

## Functional Behavior
### Scenario 1: Initial Context Propagation
- **Trigger**: The Work Execution system receives a valid `AssignmentUpdated` event for a `Workorder` that currently has no assignment context.
- **Action**:
    1. The system validates the event schema and the existence of the target `Workorder`.
    2. The system verifies the `Workorder` is in a modifiable state (pre-execution).
    3. The system updates the target `Workorder` entity, populating its `locationId`, `resourceId`, and `mechanicIds` fields with the data from the event.
- **Outcome**: The `Workorder` is enriched with the assignment context. An audit log entry is created detailing the change.

### Scenario 2: Context Update Before Work Commences
- **Trigger**: The Work Execution system receives a valid `AssignmentUpdated` event for a `Workorder` that already has assignment context and is still in a pre-execution state.
- **Action**:
    1. The system performs the same validations as in Scenario 1.
    2. The system replaces the existing assignment context on the `Workorder` with the new context from the event payload.
- **Outcome**: The `Workorder`'s assignment context is updated. A new audit log entry is created, capturing both the previous and new context values.

## Alternate / Error Flows
### Flow 1: Update Attempted After Work Has Started
- **Trigger**: An `AssignmentUpdated` event is received for a `Workorder` that is in an `IN_PROGRESS`, `COMPLETED`, or other non-modifiable state.
- **Behavior**: The system rejects the update.
- **Outcome**: The `Workorder` record remains unchanged. A `WARN` level log is generated, stating the rejection reason (e.g., "Update for Workorder [ID] rejected; status is [STATUS]"). The event is acknowledged to prevent retries.

### Flow 2: Event for Non-Existent Workorder
- **Trigger**: An `AssignmentUpdated` event is received with a `workorderId` that does not exist in the system.
- **Behavior**: The system logs an `ERROR`.
- **Outcome**: The event is acknowledged and potentially routed to a dead-letter queue (DLQ) for investigation.

### Flow 3: Malformed Event Received
- **Trigger**: An event is received that fails schema validation (e.g., missing `workorderId`, malformed payload).
- **Behavior**: The system logs an `ERROR` with details of the validation failure.
- **Outcome**: The event is rejected and routed to a DLQ.

## Business Rules
- **BR1: State-Gated Mutability**: Assignment context (`locationId`, `resourceId`, `mechanicIds`) can only be created or modified if the `Workorder` status is pre-execution (e.g., `PENDING`, `SCHEDULED`).
- **BR2: Immutability Post-Execution**: Once a `Workorder` transitions to `IN_PROGRESS` or any subsequent terminal state, its assignment context is considered immutable and cannot be changed.
- **BR3: Source of Truth**: The Shop Management service is the sole source of truth for assignment context. The Work Execution service must treat incoming data as authoritative, provided BR1 is met.
- **BR4: Idempotent Updates**: Each `AssignmentUpdated` event contains the complete context object. Applying an update replaces the entire context on the `Workorder`; it is not a partial patch.
- **BR5: Mandatory Auditing**: Every successful change to the assignment context must generate an immutable audit trail entry.

## Data Requirements
### Workorder Entity (Fields to be updated)
- `locationId` (UUID, Nullable): Reference to the assigned physical location.
- `resourceId` (UUID, Nullable): Reference to the assigned primary resource (e.g., bay, lift).
- `mechanicIds` (List<UUID>, Nullable): List of identifiers for assigned mechanics.

### AssignmentUpdated Event (Incoming)
- `eventId` (UUID): Unique identifier for the event.
- `timestamp` (ISO 8601): Time the event was generated.
- `workorderId` (UUID): The target Workorder.
- `payload`:
    - `locationId` (UUID)
    - `resourceId` (UUID)
    - `mechanicIds` (List<UUID>)

### AuditLog Entity (Record to be created)
- `entityId` (UUID): The `workorderId`.
- `entityType`: "Workorder"
- `changeType`: "AssignmentContextUpdated"
- `oldValue` (JSONB): The assignment context before the change.
- `newValue` (JSONB): The assignment context after the change.
- `timestamp` (ISO 8601): Time the change was applied.
- `actor` (String): "System:ShopManagementService"

## Acceptance Criteria
### AC1: Initial context is successfully applied to a new Workorder
- **Given** a `Workorder` exists in a `PENDING` state with null assignment context.
- **When** a valid `AssignmentUpdated` event is received for that `Workorder`.
- **Then** the `Workorder` record is updated with the `locationId`, `resourceId`, and `mechanicIds` from the event.
- **And** an audit log is created capturing the change from null to the new values.

### AC2: Context is successfully updated on a pre-existing Workorder
- **Given** a `Workorder` exists in a `PENDING` state with existing assignment context.
- **When** a new, valid `AssignmentUpdated` event is received for that `Workorder`.
- **Then** the `Workorder` record's context is replaced with the new values from the event.
- **And** an audit log is created showing the previous and new context values.

### AC3: Context update is rejected after work has started
- **Given** a `Workorder` exists in an `IN_PROGRESS` state.
- **When** an `AssignmentUpdated` event is received for that `Workorder`.
- **Then** the `Workorder`'s assignment context remains unchanged.
- **And** a warning is logged indicating the rejection was due to the `Workorder`'s status.

### AC4: System handles events for non-existent Workorders gracefully
- **Given** no `Workorder` exists with ID `12345`.
- **When** an `AssignmentUpdated` event is received for `workorderId: 12345`.
- **Then** an error is logged stating the `Workorder` was not found.
- **And** the system does not crash or enter an invalid state.

## Audit & Observability
- **Audit Trail**: Every successful create or update operation on a `Workorder`'s assignment context MUST be recorded in an immutable audit log. The log must capture the previous state, new state, timestamp, and the system principal responsible for the change (`ShopManagementService`).
- **Logging**:
    - `INFO`: Log successful application of context.
    - `WARN`: Log rejected updates due to business rules (e.g., `Workorder` already in progress).
    - `ERROR`: Log processing failures due to invalid data, missing entities, or unexpected system errors.
- **Metrics**:
    - `workexec.assignment.events.received.total`: Counter for total `AssignmentUpdated` events consumed.
    - `workexec.assignment.updates.success.total`: Counter for successful context updates.
    - `workexec.assignment.updates.rejected.total`: Counter for rejected updates, tagged by reason (e.g., `invalid_state`, `not_found`).

---
## Original Story (Unmodified – For Traceability)
# Issue #64 — [BACKEND] [STORY] Workexec: Propagate Assignment Context to Workorder

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Workexec: Propagate Assignment Context to Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want workorders to carry location/resource/mechanic context from shopmgr so that execution and reporting are accurate.

## Details
- Attach operational context at WO creation.
- Updates allowed until work starts.

## Acceptance Criteria
- Workorder has locationId/resourceId/mechanicIds.
- Updates applied pre-start.
- Audit maintained.

## Integrations
- Shopmgr emits AssignmentUpdated; workexec applies update rules; workexec emits StatusChanged.

## Data / Entities
- OperationalContext, AssignmentSyncLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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