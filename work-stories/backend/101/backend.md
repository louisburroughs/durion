Title: [BACKEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/101
Labels: type:story, domain:crm, status:ready-for-dev

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent

As the System, I want to ingest `VehicleUpdated` events originating from the Workorder Execution domain. I will use the data from these events, such as VIN corrections, updated mileage, and service notes, to update the canonical Vehicle record. This ensures that the CRM's vehicle data remains accurate and reflects the most recent state captured during service operations.

## Actors & Stakeholders

- **Primary Actor:** `System (CRM)`: The service responsible for consuming events, applying business logic, and persisting updates to the Vehicle entity.
- **Triggering Actor:** `System (Workorder Execution)`: The external domain system that performs the vehicle service and emits the `VehicleUpdated` event upon completion or at a key milestone.
- **Stakeholders:**
    - `Service Advisor`: Relies on accurate vehicle information (mileage, VIN) for future customer interactions and service recommendations.
    - `Business Operations`: Depends on data integrity for reporting, analytics, and understanding the vehicle lifecycle.

## Preconditions

1.  A `Vehicle` entity with a unique, stable identifier (`vehicleId`) exists within the CRM datastore.
2.  The `Workorder Execution` domain has published a well-defined contract/schema for the `VehicleUpdated` event.
3.  The CRM system is subscribed to the message topic where `VehicleUpdated` events are published.
4.  The event transport mechanism (e.g., message queue) is operational.

## Functional Behavior

1.  **Event Consumption:** The CRM system's listener consumes a `VehicleUpdated` event from the message queue.
2.  **Payload Validation:** The system validates the incoming event payload against the expected schema. Any deviation results in an error flow.
3.  **Idempotency Check:** The system uses a unique identifier from the event (e.g., a combination of `workorderId` and a unique event ID) to check the `ProcessingLog`. If the event has already been processed successfully, the system acknowledges the message and stops further processing (see Alternate Flow).
4.  **Data Retrieval:** The system uses the `vehicleId` from the event to retrieve the corresponding `Vehicle` record from the CRM database.
5.  **Conflict Detection & Resolution:** The system compares the incoming data (e.g., `mileage`) with the existing data. If a conflict is detected (e.g., incoming mileage is lower than existing mileage), it applies the defined **Conflict Resolution Policy** (see Open Questions).
6.  **Data Persistence:** If validation and conflict checks pass, the system updates the relevant fields (VIN, mileage, notes) on the `Vehicle` record and persists the changes to the database.
7.  **Audit Logging:** Upon completion (success, duplicate, or error), the system creates a corresponding entry in the `ProcessingLog` table, including the source `workorderId`, `eventId`, and processing status.

## Alternate / Error Flows

- **Duplicate Event Received:** If the idempotency check finds that the event has already been processed, the system will log the duplicate attempt, acknowledge the message to remove it from the queue, and take no further action on the Vehicle record.
- **Invalid Event Payload:** If the event fails schema validation, it is moved to a Dead-Letter Queue (DLQ). An alert is triggered for engineering review.
- **Vehicle Not Found:** If the `vehicleId` from the event does not exist in the CRM database, the event is moved to a DLQ and an alert is triggered. This indicates a potential data synchronization issue.
- **Data Conflict Detected:** The behavior depends on the chosen policy. If the policy is `last-write-wins`, the new data overwrites the old. If the policy is `review-queue`, the update is suspended, and the event is flagged for manual review by a data steward.

## Business Rules

- **Idempotency:** All event processing must be idempotent. A given event, if received multiple times, must only result in a single state change to the system.
- **Source of Truth:** The Workorder Execution system is the authoritative source for vehicle data captured *during a specific service*. The CRM is the aggregate system of record for the vehicle's lifetime state.
- **Conflict Resolution Policy:** **[TO BE DEFINED]** - The specific policy for handling data conflicts (e.g., mileage decreasing, VIN changing) must be explicitly defined. This is a blocking requirement.

## Data Requirements

- **`VehicleUpdated` Event (Payload)**
    - `eventId`: `string` (UUID, unique per event)
    - `workorderId`: `string` (Identifier for the source workorder)
    - `vehicleId`: `string` (Identifier for the vehicle in CRM)
    - `eventTimestamp`: `datetime` (ISO 8601)
    - `updatedFields`: `object`
        - `vin`: `string` (optional)
        - `mileage`: `integer` (optional)
        - `notes`: `string` (optional, may append or replace)

- **`ProcessingLog` Entity**
    - `logId`: `PK`
    - `eventId`: `string` (indexed)
    - `workorderId`: `string` (indexed)
    - `vehicleId`: `string` (indexed)
    - `receivedTimestamp`: `datetime`
    - `processedTimestamp`: `datetime`
    - `status`: `enum` ('SUCCESS', 'DUPLICATE', 'ERROR_VALIDATION', 'ERROR_NOT_FOUND', 'PENDING_REVIEW')
    - `details`: `jsonb` or `text` (for error messages or context)

## Acceptance Criteria

**AC-1: Successful Vehicle Update**
- **Given** a Vehicle with `vehicleId: "V-123"` exists in the CRM with `mileage: 50000`.
- **When** a valid `VehicleUpdated` event is received for `vehicleId: "V-123"` with `mileage: 51500`.
- **Then** the Vehicle record in the CRM is updated to `mileage: 51500`, and a `ProcessingLog` entry is created with `status: 'SUCCESS'`.

**AC-2: Idempotent Processing of Duplicate Event**
- **Given** a `VehicleUpdated` event with `eventId: "E-456"` has already been successfully processed.
- **When** another event with the exact same `eventId: "E-456"` is received.
- **Then** the system does not modify the Vehicle record, and a `ProcessingLog` entry is created with `status: 'DUPLICATE'`.

**AC-3: Handling Vehicle Not Found**
- **Given** the CRM does not contain a vehicle with `vehicleId: "V-999"`.
- **When** a `VehicleUpdated` event is received for `vehicleId: "V-999"`.
- **Then** the event is moved to the DLQ, and a `ProcessingLog` entry is created with `status: 'ERROR_NOT_FOUND'`.

**AC-4: Handling Conflicting Data (Pending Policy)**
- **Given** a Vehicle with `vehicleId: "V-123"` exists in the CRM with `mileage: 50000` and the conflict policy is defined as **[POLICY_NAME]**.
- **When** a valid `VehicleUpdated` event is received for `vehicleId: "V-123"` with a conflicting `mileage: 49000`.
- **Then** the system executes the behavior defined by **[POLICY_NAME]** (e.g., rejects the update and logs a `PENDING_REVIEW` status, or accepts the update).

## Audit & Observability

- **Audit Trail:** A `ProcessingLog` record must be created for every event received, capturing its `eventId`, `workorderId`, and final processing status. This provides a complete audit trail of all attempted updates.
- **Metrics:** The service must emit metrics for:
    - `events.consumed.count`
    - `events.processed.success.count`
    - `events.processed.duplicate.count`
    - `events.processed.error.count`
- **Alerting:** High-priority alerts must be configured for any events being sent to the DLQ, as this indicates a potential systemic issue requiring manual intervention.

## Open Questions

1.  **CRITICAL: What is the business policy for handling data conflicts?** Specifically:
    - **Mileage Decrease:** What should happen if the incoming `mileage` is *less than* the currently stored mileage? Should it be rejected, flagged for review, or should last-write-wins apply?
    - **VIN Correction:** If a `vin` is changed, what is the procedure? Is it a simple overwrite, or does it require a review process, especially if the old VIN was associated with other historical records?
    - **Default Policy:** In the absence of specific field-level rules, should the default behavior be "last write wins" or "flag for manual review"?

---

## Original Story (Unmodified – For Traceability)
# Issue #101 — [BACKEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **to update vehicle details captured during service (VIN correction, mileage, notes)** so that **CRM remains accurate over time**.

## Details
- Accept updates via event envelope from Workorder Execution.
- Apply idempotency and audit against workorder reference.

## Acceptance Criteria
- Vehicle updates are processed once.
- Audit includes source Workorder/Estimate ID.
- Conflicts handled (last-write or review queue; define policy).

## Integration Points (Workorder Execution)
- Workorder Execution emits VehicleUpdated events.
- CRM persists updates and exposes updated snapshot.

## Data / Entities
- Vehicle
- EventEnvelope
- ProcessingLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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