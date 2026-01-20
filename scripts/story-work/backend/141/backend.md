Title: [BACKEND] [STORY] Events: Validate Event Completeness and Integrity
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/141
Labels: general, type:story, domain:accounting, status:ready-for-dev

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
As a System Architect, I require a robust validation layer for all incoming accounting events to ensure that every event is structurally sound, internally consistent, and contains all necessary data before it is accepted for downstream processing. This gatekeeping function is critical to protect the integrity of the general ledger and prevent data corruption in financial reporting systems.

## Actors & Stakeholders
- **System Actor**: `Accounting Event Ingestion Service` - The microservice responsible for receiving and validating events.
- **Upstream Systems**: Any internal service (e.g., Sales, Inventory, Billing) that produces and sends accounting-relevant events.
- **Stakeholders**:
  - `Accounting Team`: Defines the validation rules and policies; relies on the accuracy of the processed data for financial reporting.
  - `System Auditors`: Require a clear and traceable record of all event processing decisions, especially for rejections and suspense items.
  - `Development Teams`: Consume the validation feedback (error codes) from the Ingestion Service to debug and correct their event-producing systems.

## Preconditions
- The `Accounting Event Ingestion Service` is running and has an exposed endpoint to receive events.
- The service has access to the authoritative schemas for all known event types.
- The service has read-access to data sources required for validating references (e.g., customer database, product catalog).

## Functional Behavior
1. The `Accounting Event Ingestion Service` receives a new accounting event payload via its API endpoint.
2. Upon receipt, the service immediately creates a processing record for the event and sets its initial status to `Received`.
3. The service then executes a series of validation steps in a predefined order:
    a. **Schema Validation**: Checks if the event payload conforms to the registered JSON schema for its `eventType`.
    b. **Referential Integrity Validation**: Verifies that all entity identifiers (e.g., `customerId`, `orderId`, `productId`) within the event exist in the corresponding source-of-truth systems.
    c. **Financial Consistency Validation**: Enforces business rules on monetary values (e.g., `subtotal + tax = total`).
4. If all validation steps pass successfully, the service updates the event's status to `Validated` and places it in a queue for the next stage of processing (e.g., Mapping).
5. If any validation step fails, the service immediately halts processing for that event, updates its status to `Rejected`, and follows the defined error handling flow.

## Alternate / Error Flows
- **Invalid Schema**: If the event payload does not conform to the schema for its `eventType`, its status is transitioned to `Rejected`. A structured error log is created detailing the specific schema violations. An HTTP `400 Bad Request` response with an actionable error code (`SCHEMA_VALIDATION_FAILED`) is returned to the calling system.
- **Missing Required Reference**: If a referenced entity ID does not exist in the source system, the event status is transitioned to `Rejected`. A structured error log is created, and an HTTP `400 Bad Request` with a specific error code (e.g., `REFERENCE_NOT_FOUND`) is returned.
- **Unknown Event Type**: If the `eventType` is not recognized by the system, the event is processed according to the defined business policy (see **Open Questions**). It is either rejected with an `UNKNOWN_EVENT_TYPE` error or its status is transitioned to `Suspense` for manual investigation.
- **Financial Inconsistency**: If the monetary values in the event fail consistency checks, its status is transitioned to `Rejected`. An HTTP `400 Bad Request` with a `FINANCIAL_INCONSISTENCY` error code is returned.

## Business Rules
- **BR1: Schema Adherence**: All incoming events MUST conform to their registered JSON schema.
- **BR2: Referential Integrity**: All foreign key identifiers within an event payload MUST correspond to an active, valid record in the referenced domain's system of record.
- **BR3: Financial Consistency**: Monetary fields within an event MUST adhere to defined accounting principles and checks (e.g., debits must equal credits, or line items must sum to a total). (See OQ2)
- **BR4: Idempotency**: The combination of `sourceSystem` and `eventId` must be unique to prevent duplicate processing. A duplicate submission should be acknowledged with a success response but not re-processed.
- **BR5: Unknown Event Policy**: The handling of unknown `eventType` values must follow a deterministic policy. (See OQ1)

## Data Requirements
### `AccountingEvent` (Incoming Payload)
- `eventId`: (string, UUID) Unique identifier for the event from the source system.
- `eventType`: (string, enum) The specific type of event (e.g., `ORDER_COMPLETED`, `PAYMENT_PROCESSED`).
- `eventTimestamp`: (timestamp, ISO 8601) The time the event occurred in the source system.
- `sourceSystem`: (string) The name of the service that generated the event.
- `payload`: (JSON object) The event-specific data, containing financial amounts, taxes, and entity references.

### `EventProcessingRecord` (Internal State)
- `processingId`: (UUID) The internal unique ID for this processing attempt.
- `eventId`: (string, UUID) Foreign key to the incoming event's ID.
- `status`: (string, enum) The current state of the event (`Received`, `Validated`, `Rejected`, `Suspense`, `Mapped`, `Posted`).
- `receivedTimestamp`: (timestamp) When the service first received the event.
- `lastUpdatedTimestamp`: (timestamp) When the status was last changed.
- `validationErrors`: (JSON object, nullable) A structured object containing details of any validation failures.

## Acceptance Criteria
**AC1: Successful Validation of a Correct Event**
- **Given** the `Accounting Event Ingestion Service` receives a valid event with a known `eventType`, a valid schema, correct references, and consistent financial data
- **When** the event is processed
- **Then** the event's status is recorded sequentially as `Received` and then `Validated`, and it is enqueued for the next processing stage.

**AC2: Rejection Due to Invalid Schema**
- **Given** the service receives an event with a known `eventType` but a payload that violates the corresponding schema
- **When** the event is processed
- **Then** the event's status is updated to `Rejected`, a detailed schema violation error is logged, and a `400 Bad Request` response with error code `SCHEMA_VALIDATION_FAILED` is returned to the caller.

**AC3: Rejection Due to Missing Reference**
- **Given** the service receives an event that is schema-valid but contains a `customerId` that does not exist
- **When** the event is processed
- **Then** the event's status is updated to `Rejected`, an error referencing the invalid `customerId` is logged, and a `400 Bad Request` response with error code `REFERENCE_NOT_FOUND` is returned.

**AC4: Rejection Due to Financial Inconsistency**
- **Given** the service receives a schema-valid event where `payload.subtotal` + `payload.tax` does not equal `payload.total`
- **When** the event is processed
- **Then** the event's status is updated to `Rejected`, the specific consistency failure is logged, and a `400 Bad Request` response with error code `FINANCIAL_INCONSISTENCY` is returned.

**AC5: Handling of Unknown Event Type**
- **Given** the service receives an event with an `eventType` that is not registered in the system
- **When** the event is processed
- **Then** the system follows the defined policy for unknown types (reject or suspense) as determined by the outcome of OQ1.

## Audit & Observability
- **Audit Trail**: Every status transition for an event (`Received`, `Validated`, `Rejected`, `Suspense`, etc.) MUST be recorded in an immutable audit log with timestamps and the responsible service principal.
- **Logging**: Detailed, structured logs MUST be generated for all rejected events, including the full event payload and the specific reason(s) for rejection.
- **Metrics**: The service MUST emit metrics for:
  - `events.received.count` (tagged by `eventType`)
  - `events.validated.count` (tagged by `eventType`)
  - `events.rejected.count` (tagged by `eventType` and `rejectionReason`)
  - `events.suspense.count` (tagged by `eventType`)
- **Alerting**: An alert MUST be configured to fire if the rate of rejected or suspense events exceeds a predefined threshold.

## Open Questions
- **OQ1: Policy for Unknown Event Types**: What is the definitive business policy for handling an event with an unknown `eventType`? Should it be (a) immediately rejected with an error, or (b) routed to a `Suspense` queue/ledger for manual review and classification?
- **OQ2: Financial Consistency Rules**: What are the specific, exhaustive financial consistency checks that must be enforced? Please provide the exact formulas and any acceptable tolerances for rounding (e.g., `grossAmount == netAmount + taxAmount`, `sum(lineItems.total) == order.total`).
- **OQ3: Error Code Granularity**: Please provide or point to the canonical list of actionable error codes to be returned to clients for each validation failure class (e.g., `SCHEMA_VIOLATION`, `MISSING_REQUIRED_FIELD`, `INVALID_REFERENCE_ID`, `INCONSISTENT_AMOUNTS`, `UNKNOWN_EVENT_TYPE`).

---
## Original Story (Unmodified – For Traceability)
# Issue #141 — [BACKEND] [STORY] Events: Validate Event Completeness and Integrity

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Events: Validate Event Completeness and Integrity

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Validate Event Completeness and Integrity

## Acceptance Criteria
- [ ] Invalid schema or missing required references are rejected with actionable error codes
- [ ] Unknown eventType is rejected or routed to suspense per policy
- [ ] Amount and tax consistency checks are enforced per policy
- [ ] Processing status transitions are recorded (Received→Validated→Mapped→Posted/Rejected/Suspense)


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