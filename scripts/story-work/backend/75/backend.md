Title: [BACKEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/75
Labels: type:story, domain:workexec, status:ready-for-dev

**Rewrite Variant:** integration-conservative  
**Status:** Ready-for-dev

## Story Intent
As a **Service Advisor**, I need to create a new service appointment by selecting an existing customer and their vehicle from the CRM system, so that work can be scheduled accurately with pre-verified customer context and preserved historical appointment details.

## Actors & Stakeholders
- **Primary actor:** Service Advisor
- **Work Execution (`domain:workexec`):** System of Record (SoR) for the `Appointment` entity and its lifecycle
- **CRM (`durion-crm`, external system):** SoR for customer + vehicle identity and association
- **Downstream consumers:** Notifications, estimating/billing, analytics/reporting

## Preconditions
- Caller is authenticated and authorized to create appointments (authorization enforcement is required; specific permission naming is out of scope for this story).
- CRM is reachable for validation at appointment creation time.

## Functional Behavior
1) **Request received**
- The system receives a create request containing at minimum:
  - `crmCustomerId`
  - `crmVehicleId`
  - requested services (one or more)
  - scheduled time (`startAt` + `endAt`, or equivalent)
  - notes (optional)

2) **Validate against CRM (required before create)**
- Validate:
  - `crmCustomerId` exists
  - `crmVehicleId` exists
  - vehicle is associated with customer

3) **Create Appointment (workexec owns lifecycle)**
- Persist a new `Appointment` in Work Execution.
- Persist CRM references (`crmCustomerId`, `crmVehicleId`).
- Snapshot key customer/vehicle fields for historical accuracy and read-time resilience.

4) **Initial status**
- When customer + vehicle are confirmed and time is provided/valid, create the appointment in **`SCHEDULED`** status.
- **Draft behavior is out of scope** for this story.

5) **Publish `AppointmentCreated` event (mandatory)**
- On successful creation of a **`SCHEDULED`** appointment, publish `AppointmentCreated`.
- Delivery: **at-least-once** using an outbox (or equivalent) so event publication is consistent with DB commit.
- Consumers must be idempotent.

## Alternate / Error Flows
- **CRM unavailable:** If CRM is unreachable or returns 5xx/timeout, reject creation with `503 Service Unavailable`.
- **Customer not found:** Reject with `404 Not Found` and error code `CUSTOMER_NOT_FOUND`.
- **Vehicle not found:** Reject with `404 Not Found` and `VEHICLE_NOT_FOUND`.
- **Vehicle/customer mismatch:** Reject with `409 Conflict` and `VEHICLE_CUSTOMER_MISMATCH`.
- **Invalid input:** Reject missing/invalid fields with `400 Bad Request`.

## Business Rules
- Appointment cannot be created without validated `crmCustomerId` and `crmVehicleId` association.
- Appointment snapshots are immutable for historical accuracy (do not rewrite snapshots when CRM data changes).
- `AppointmentCreated` must be emitted for scheduled appointments; consumers must be idempotent.

## Data Requirements
### Appointment
- `appointmentId`: UUID (PK)
- `crmCustomerId`: string/UUID (indexed)
- `crmVehicleId`: string/UUID (indexed)
- `status`: enum (includes `SCHEDULED`)
- `notes`: text
- `startAt`, `endAt` (or `durationMinutes`)
- `customerSnapshot`: JSON/structured snapshot captured-at-create
- `vehicleSnapshot`: JSON/structured snapshot captured-at-create
- `createdAt`, `updatedAt`

**Snapshot minimum (recommended):**
- Customer: `fullName` (or `firstName`/`lastName`), `phone`, `email` (and optionally address/preferred contact)
- Vehicle: `year`, `make`, `model`, `trim`, `vin` (if available), `licensePlate` (if available)

### AppointmentServiceRequest
- `serviceRequestId`: UUID (PK)
- `appointmentId`: UUID (FK)
- `description` (or service code/id as applicable)

## Acceptance Criteria
### Scenario 1: Successful Appointment Creation
- Given CRM is available
- And a valid `crmCustomerId` exists
- And a valid `crmVehicleId` exists and is associated to the customer
- When the Service Advisor creates an appointment with required fields (including time)
- Then the system creates an Appointment with status `SCHEDULED`
- And the Appointment stores CRM IDs and snapshots
- And the system returns `201 Created` with `appointmentId`.

### Scenario 2: Vehicle Not Associated with Customer
- Given CRM is available
- And `crmCustomerId` exists
- And `crmVehicleId` exists but is not associated with that customer
- When the create request is submitted
- Then the API returns `409 Conflict` with error `VEHICLE_CUSTOMER_MISMATCH`.

### Scenario 3: CRM Unavailable
- Given CRM is unavailable
- When the create request is submitted
- Then the API returns `503 Service Unavailable`
- And no Appointment is created.

### Scenario 4: AppointmentCreated Event Emitted
- Given a `SCHEDULED` appointment is created successfully
- When the transaction commits
- Then an `AppointmentCreated` event is published (at-least-once)
- And the payload includes `appointmentId`, `crmCustomerId`, `crmVehicleId`, scheduled time, and a snapshot subset.

## Audit & Observability
- Audit log entry on successful appointment creation (who/what/when).
- Correlation ID propagated/logged for create + CRM validation.
- Metrics:
  - `appointments.created.count`
  - `crm.validation.latency`
  - `crm.validation.errors.count` partitioned by failure type

## Resolved Decisions (from issue comments)
- **SoR:** `domain:workexec` owns `Appointment` lifecycle; CRM owns customer/vehicle identity + association.
- **Initial status:** `SCHEDULED` when customer+vehicle+time are confirmed (draft out of scope here).
- **Snapshotting:** store CRM IDs + immutable snapshots at create time.
- **Events:** `AppointmentCreated` is mandatory for scheduled appointments; at-least-once via outbox; consumers idempotent.

## Original Story (Unmodified – For Traceability)
# Issue #75 — [BACKEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle

**Domain**: payment

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create an appointment selecting customer and vehicle from durion-crm so that the shop has accurate context for service and billing.

## Details
- Capture: crmCustomerId, crmVehicleId, requested services, notes, preferred time window, contact hints.

## Acceptance Criteria
- Appointment created with status Draft/Scheduled.
- Customer/vehicle references validated.
- Audited.

## Integrations
- CRM lookup/snapshot.
- Optional AppointmentCreated event.

## Data / Entities
- Appointment, AppointmentServiceRequest, CRM references

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