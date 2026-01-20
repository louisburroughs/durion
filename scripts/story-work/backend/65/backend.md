Title: [BACKEND] [STORY] Workexec: Create Draft Estimate from Appointment
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/65
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
**As a** Service Advisor,
**I want to** initiate a draft work estimate directly from a scheduled customer appointment,
**So that** the service workflow begins with the established operational context (customer, vehicle, requested services), ensuring data consistency and reducing manual entry.

## Actors & Stakeholders
- **Primary Actor:**
  - **Service Advisor:** User initiating the estimate creation process via the Shop Management System.
- **System Actors:**
  - **Shop Management System (`shopmgr`):** The system of record for appointments. It is the client that sends the creation request.
  - **Work Execution System (`workexec`):** The system of record for estimates and repair orders. It is the service that receives the request and owns the estimate lifecycle.
- **Stakeholders:**
  - **Shop Manager:** Concerned with operational efficiency and the seamless flow from appointment to repair.
  - **Customer:** The ultimate recipient of the estimate.

## Preconditions
1. A valid, non-cancelled appointment exists in the Shop Management System (`shopmgr`).
2. The appointment record contains a valid `customerId`, `vehicleId`, `locationId`, and a list of requested services.
3. The `workexec` system is reachable and operational.
4. The `shopmgr` system is authorized to call the `workexec` API.

## Functional Behavior
1. **Trigger:** The Service Advisor selects an action (e.g., "Create Estimate") on a specific appointment within the `shopmgr` UI.
2. **Request Initiation:** The `shopmgr` system constructs and sends a `CreateEstimateFromAppointment` command to the `workexec` system.
   - The command payload MUST include an idempotency key (e.g., the unique `appointmentId` or a dedicated command UUID) to prevent duplicate estimate creation.
3. **Request Processing:** The `workexec` system receives the command and performs the following:
   a. **Idempotency Check:** Verifies if an estimate has already been created for the given `appointmentId`. If a match is found, it returns the existing `estimateId` without processing further (see Alternate Flow #1).
   b. **Validation:** Ensures all required data (`customerId`, `vehicleId`, etc.) is present and valid.
   c. **Estimate Creation:** Creates a new `Estimate` entity.
   d. **State Initialization:** The new estimate's initial status MUST be set to `Draft`.
   e. **Data Hydration:** Populates the new estimate with the customer, vehicle, and requested service information provided in the command.
   f. **Linkage:** Persists a link between the source `appointmentId` and the newly created `estimateId` for traceability.
4. **Response:** The `workexec` system returns a success response to `shopmgr`, including the newly created `estimateId`.
5. **Confirmation:** The `shopmgr` system receives the `estimateId` and stores it, linking it to the source appointment record on its side.

## Alternate / Error Flows
1. **Duplicate Request (Idempotency):**
   - **Scenario:** `workexec` receives a `CreateEstimateFromAppointment` request with an `appointmentId` that already has a linked `estimateId`.
   - **System Behavior:** The system MUST NOT create a new estimate. It will immediately return a success response containing the `estimateId` of the pre-existing estimate.
2. **Missing Required Data:**
   - **Scenario:** The incoming request is missing a mandatory field (e.g., `vehicleId`).
   - **System Behavior:** The system MUST reject the request with a `400 Bad Request` status and a clear error message indicating the missing field.
3. **Invalid Data:**
   - **Scenario:** The request contains an ID (e.g., `customerId`) that does not correspond to an existing entity known to `workexec`.
   - **System Behavior:** The system MUST reject the request with a `422 Unprocessable Entity` or `404 Not Found` status and an error message explaining the data validation failure.
4. **Upstream Service Unavailable:**
   - **Scenario:** The `workexec` service is down or unreachable.
   - **System Behavior:** The `shopmgr` system should handle the connection failure gracefully, log the error, and provide feedback to the Service Advisor. A retry mechanism may be implemented.

## Business Rules
- A single appointment can be used to generate exactly one estimate.
- The newly created estimate MUST always begin in a `Draft` status.
- The `appointmentId` from the source system (`shopmgr`) must be stored and indexed within `workexec` against the estimate for traceability.

## Data Requirements
### `CreateEstimateFromAppointment` Command
| Field | Type | Description | Constraints |
|---|---|---|---|
| `idempotencyKey` | UUID | Unique identifier for the command instance. | Required, UUIDv4 |
| `appointmentId` | String / UUID | The unique identifier of the appointment in `shopmgr`. | Required, Immutable |
| `customerId` | String / UUID | The unique identifier for the customer. | Required |
| `vehicleId` | String / UUID | The unique identifier for the vehicle. | Required |
| `locationId` | String / UUID | The unique identifier for the shop location. | Required |
| `requestedServices` | Array[Object] | List of services requested in the appointment. | Optional |
| `requestedServices[].code`| String | A standardized code for the requested service. | Required if parent exists |
| `requestedServices[].description`| String | Customer-facing description of the service. | Required if parent exists |

### `CreateEstimateFromAppointment` Response (Success)
| Field | Type | Description |
|---|---|---|
| `estimateId` | UUID | The unique identifier of the newly created estimate in `workexec`. |
| `status` | String | The initial status of the estimate (e.g., "DRAFT"). |

## Acceptance Criteria
**Scenario 1: Successful Estimate Creation from a Valid Appointment**
- **Given** a valid appointment exists in `shopmgr` with a customer, vehicle, and location.
- **When** the Service Advisor triggers the "Create Estimate" action for that appointment.
- **Then** the `workexec` system creates a new `Estimate` record in a `Draft` status.
- **And** the `workexec` system stores a link between the `appointmentId` and the new `estimateId`.
- **And** the `workexec` system returns the new `estimateId` to `shopmgr`.
- **And** the `shopmgr` system successfully associates the `estimateId` with the original appointment.

**Scenario 2: Idempotent Handling of a Duplicate Request**
- **Given** an estimate has already been successfully created in `workexec` for `appointment-123`.
- **When** `shopmgr` sends a second "Create Estimate" request for the same `appointment-123`.
- **Then** the `workexec` system does NOT create a new estimate.
- **And** the `workexec` system returns the `estimateId` of the original, existing estimate.

**Scenario 3: Request Fails Due to Missing Required Data**
- **Given** a `shopmgr` appointment is missing the `vehicleId`.
- **When** a "Create Estimate" request is sent for that appointment.
- **Then** the `workexec` system rejects the request with a client error (`400` series).
- **And** the response body contains a message indicating that `vehicleId` is required.
- **And** no new estimate record is created in `workexec`.

## Audit & Observability
- **Logging:**
  - Log the receipt of every `CreateEstimateFromAppointment` command, including the `appointmentId` and `idempotencyKey`.
  - Log the successful creation of a new estimate, including the new `estimateId` and the source `appointmentId`.
  - Log any validation errors or processing failures with structured context (e.g., which field failed validation).
  - Log idempotency key hits, clearly indicating that an existing record was found and returned.
- **Metrics:**
  - `estimates.created_from_appointment.count`: Counter for successful creations.
  - `estimates.creation.latency`: Histogram tracking the time taken to process the request.
  - `estimates.creation.failures.count`: Counter for failed requests, tagged by error type (e.g., `validation`, `duplicate`, `internal`).

## Open Questions
1. **Resource Specificity:** The original story mentions `location/resource`. Is the `locationId` sufficient, or do we also need to pass a specific `resourceId` (e.g., a service bay, a specific technician) from the appointment into the draft estimate?
2. **Callback Event:** The original story mentions a `Workexec→Shopmgr EstimateCreated` event. Is a synchronous response containing the `estimateId` sufficient for this story, or is a separate, asynchronous callback event also required? The current design assumes a synchronous response is sufficient.

---
## Original Story (Unmodified – For Traceability)
# Issue #65 — [BACKEND] [STORY] Workexec: Create Draft Estimate from Appointment

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Workexec: Create Draft Estimate from Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create a draft estimate in workexec from an appointment so that quote-to-cash starts with operational context.

## Details
- Appointment provides customerId, vehicleId, location/resource, requested services.
- Workexec returns estimateId and shopmgr stores linkage.

## Acceptance Criteria
- Estimate created and linked.
- Idempotent retry safe.

## Integrations
- Shopmgr→Workexec CreateEstimateFromAppointment; Workexec→Shopmgr EstimateCreated.

## Data / Entities
- WorkexecLink(appointmentId→estimateId), CommandEnvelope

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