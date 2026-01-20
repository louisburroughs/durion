Title: [BACKEND] [STORY] Integration: Inbound Event Handler for Workorder-Originated Updates
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/92
Labels: backend, story-implementation, user, type:story, domain:crm, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** crm-pragmatic
---
## Story Intent
**Goal:** To ensure CRM data accurately reflects real-world operations by asynchronously processing customer and vehicle data updates originating from the Workorder Execution domain.

**Business Value:** This integration provides a single, consistent view of customer and vehicle information, preventing data drift between operational and relationship management systems. Service Advisors and other front-office staff can trust that the CRM data is current, improving customer interactions and service quality.

## Actors & Stakeholders
### System Actors
- **CRM System:** The primary actor responsible for consuming, validating, and applying data changes from inbound events.
- **Workorder Execution System (via Positivity Service):** The external system of record for operational data, which is the source of the update events.
- **Message Broker:** The infrastructure component that facilitates asynchronous event delivery.
- **Suspense / Dead-Letter Queue (DLQ):** The system component designated for storing and isolating events that fail processing.

### Human Stakeholders
- **Service Advisor:** Benefits from having up-to-date vehicle, contact, and note information within the CRM, leading to better-informed customer interactions.
- **Data/Integration Engineer:** Implements, tests, and maintains the event handling service.
- **Support Engineer:** Investigates and resolves processing failures by analyzing events in the Suspense/DLQ and reviewing processing logs.

## Preconditions
- The inter-domain event contract with the Workorder Execution system (via Positivity) is defined, versioned, and accessible. This includes the schemas for `VehicleUpdated`, `ContactPreferenceUpdated`, and `PartyNoteAdded` events.
- A dedicated message queue/topic is provisioned and configured for the CRM system to subscribe to.
- A Suspense/DLQ is provisioned and accessible to the event handler for routing unprocessable messages.
- The event handler service has the necessary credentials and permissions to read from the source queue, write to the DLQ, and modify CRM database records.

## Functional Behavior
### Trigger
An event message (`VehicleUpdated`, `ContactPreferenceUpdated`, or `PartyNoteAdded`) is received on the designated message queue topic consumed by the CRM system.

### Core Processing Logic
1.  **Consume & Log:** The event handler consumes the message and creates an initial "processing started" log entry with the unique event ID.
2.  **Idempotency Check:** The system checks the `ProcessingLog` to determine if the event ID has been successfully processed before.
    - If yes, the process terminates, a `SKIPPED_DUPLICATE` log is recorded, and the message is acknowledged.
    - If no, processing continues.
3.  **Schema Validation:** The system validates the event's structure and payload against its corresponding, versioned schema.
    - If invalid, the message is routed to the Suspense/DLQ with a "Schema Validation Failed" reason, and processing for this event stops.
4.  **Business Logic Application:**
    - The system extracts the event payload.
    - It identifies the target CRM entity (e.g., Vehicle, Party) using identifiers from the payload.
    - It applies the update to the CRM database (e.g., updates vehicle attributes, modifies contact preferences, appends a new note).
5.  **Successful Outcome:**
    - Upon successful database commit, the system updates the `ProcessingLog` with a `SUCCESS` status, including the source workorder reference for traceability.
    - The event message is acknowledged, confirming successful processing and removing it from the queue.

## Alternate / Error Flows
- **Business Rule Violation:** If the event payload is structurally valid but contains logically invalid data (e.g., refers to a `vehicleId` that does not exist in the CRM), the message is routed to the Suspense/DLQ with a "Business Rule Violation" reason.
- **Downstream System Failure:** If the CRM database or another dependent service is temporarily unavailable, the handler must not acknowledge the message. This allows the message broker to redeliver the event according to its configured retry policy.
- **Retry Exhaustion:** After all configured retry attempts for a transient error have failed, the message is moved to the Suspense/DLQ with a "Processing Failed After Retries" reason.

## Business Rules
- **Idempotency:** Event processing MUST be idempotent. An event with the same unique identifier must result in the same system state as if it were processed only once, preventing duplicate data creation or updates.
- **Atomicity:** The database update and the creation of the final `ProcessingLog` entry should be part of a single atomic transaction.
- **Failure Isolation:** Any single malformed or invalid event MUST NOT block the processing of subsequent valid events in the queue.
- **Source Authority:** The Workorder Execution system is considered the authoritative source for the data contained within these events. The CRM system acts as a replicating consumer.

## Data Requirements
### `EventEnvelope` (Inbound)
- `eventId`: `UUID` - Unique identifier for the event instance. (Required, Indexed for idempotency check)
- `eventType`: `String` (Enum: `VehicleUpdated`, `ContactPreferenceUpdated`, `PartyNoteAdded`) - The type of event. (Required)
- `eventVersion`: `String` (e.g., "1.0") - The schema version of the payload. (Required)
- `sourceSystem`: `String` (Const: "WorkorderExecution") - The originating domain. (Required)
- `correlationId`: `String` - A reference to the originating transaction, e.g., `workorderId`. (Required)
- `timestamp`: `ISO 8601 DateTime` - The time the event was generated. (Required)
- `payload`: `JSON Object` - The event-specific data payload. (Required)

### `ProcessingLog` (Internal)
- `eventId`: `UUID` - Foreign key to the processed event. (Primary Key)
- `correlationId`: `String` - Copied from the event envelope for traceability.
- `status`: `String` (Enum: `SUCCESS`, `FAILURE`, `SKIPPED_DUPLICATE`) - The final outcome of processing.
- `processedTimestamp`: `ISO 8601 DateTime` - When processing concluded.
- `notes`: `String` - Additional details, especially for failures.

## Acceptance Criteria
### Scenario 1: Successful Processing of a New Event
- **Given** a new `VehicleUpdated` event is published to the CRM topic
- **And** the event has a unique `eventId` that has not been processed before
- **When** the CRM event handler consumes and processes the event
- **Then** the corresponding vehicle record in the CRM database is updated with the data from the event payload
- **And** a `ProcessingLog` entry is created with the `eventId` and a `status` of `SUCCESS`
- **And** the event message is acknowledged and removed from the queue.

### Scenario 2: Idempotent Handling of a Duplicate Event
- **Given** an event with `eventId` "abc-123" has already been successfully processed
- **And** a new message arrives on the topic with the same `eventId` "abc-123"
- **When** the CRM event handler consumes the duplicate event
- **Then** no update operations are performed on the CRM database
- **And** a `ProcessingLog` entry is created or updated for `eventId` "abc-123" with a `status` of `SKIPPED_DUPLICATE`
- **And** the duplicate event message is acknowledged and removed from the queue.

### Scenario 3: Handling of an Invalid Event
- **Given** an event is published with a payload that fails schema validation
- **When** the CRM event handler consumes the event
- **Then** the event message is moved to the Suspense/DLQ
- **And** the metadata on the suspense message includes a reason indicating a schema validation failure
- **And** no changes are made to the CRM database
- **And** the original event message is acknowledged and removed from the main processing queue.

### Scenario 4: Handling of a Business Logic Failure
- **Given** a `PartyNoteAdded` event is published for a `partyId` that does not exist in the CRM system
- **When** the CRM event handler consumes the event
- **Then** the event message is moved to the Suspense/DLQ
- **And** the metadata on the suspense message includes a reason indicating "Entity Not Found" or a similar business rule violation
- **And** the original event message is acknowledged and removed from the main processing queue.

## Audit & Observability
- **Structured Logging:** Every event consumed must generate a structured log with its `eventId`, `correlationId`, `eventType`, and final processing `status`.
- **Metrics:** The service must emit metrics for:
  - `events.processed.count` (Counter, tagged by type and status)
  - `events.processing.latency` (Timer, tagged by type)
  - `events.dlq.count` (Counter, tagged by type and failure reason)
- **Alerting:** An alert must be configured to trigger if the number of messages in the Suspense/DLQ exceeds a predefined threshold within a specific time window.
- **Traceability:** All logs and metrics related to a single event must be linked via the `eventId` and `correlationId` to allow for end-to-end tracing of the event's lifecycle.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #92 — [BACKEND] [STORY] Integration: Inbound Event Handler for Workorder-Originated Updates

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Integration: Inbound Event Handler for Workorder-Originated Updates

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CRM System**, I want **to ingest workorder events that update CRM data** so that **CRM stays current based on operational reality**.

## Details
- Handle: VehicleUpdated, ContactPreferenceUpdated, PartyNoteAdded.
- Validate event envelope; idempotent processing; route failures to suspense queue.

## Acceptance Criteria
- Events processed once.
- Invalid events routed to suspense/dead-letter.
- Audit includes source workorder reference.

## Integration Points (Workorder Execution)
- Workorder Execution emits events via Positivity service layer; CRM consumes and applies changes.

## Data / Entities
- EventEnvelope
- ProcessingLog
- SuspenseQueue

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