Title: [BACKEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/73
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
**As a** Dispatcher,
**I want to** reschedule or cancel a previously scheduled customer appointment,
**so that** the work schedule remains accurate, changes are auditable, and dependent systems are notified.

## Actors & Stakeholders
- **Dispatcher:** The primary user responsible for managing the daily work schedule and making adjustments.
- **Work Execution System (System):** The system of record for appointments, schedules, and work orders.
- **Audit Subsystem:** A centralized service that consumes and stores immutable audit trails for significant business events.
- **Downstream Systems:** Other internal systems (e.g., CRM, Customer Notifications) that subscribe to appointment-related domain events to trigger their own workflows.

## Preconditions
- A customer `Appointment` must exist in the system with a status of `Scheduled`.
- The `Dispatcher` must be authenticated and authorized to perform scheduling modifications.

## Functional Behavior

### Flow 1: Reschedule an Appointment
1.  **Trigger:** The `Dispatcher` initiates a reschedule action for a specific `Appointment`.
2.  **Input:**
    - `AppointmentID`: The unique identifier of the appointment to be changed.
    - `NewScheduledTimeWindow`: The new date and time block for the appointment.
    - `NewAssignedResourceID` (Optional): The identifier for the technician or resource assigned to the new time slot.
3.  **Process:**
    - The system validates that the `Appointment` exists and is in a `Scheduled` state.
    - The system verifies the availability of the specified resource for the new time window.
    - The system updates the `Appointment` record with the new `ScheduledTimeWindow` and `AssignedResourceID`.
    - An immutable `AppointmentAudit` record is created, capturing the "before" and "after" state of the modified fields.
    - If the `Appointment` is linked to a `WorkOrder` or `Estimate`, the system publishes an `AppointmentRescheduled` domain event.
4.  **Outcome:** The appointment is successfully rescheduled, an audit trail is created, and a domain event is published if applicable.

### Flow 2: Cancel an Appointment
1.  **Trigger:** The `Dispatcher` initiates a cancellation action for a specific `Appointment`.
2.  **Input:**
    - `AppointmentID`: The unique identifier of the appointment to be cancelled.
    - `CancellationReasonCode`: A structured code representing the reason for cancellation (e.g., `CUSTOMER_REQUEST`, `NO_SHOW`, `RESOURCE_UNAVAILABLE`).
    - `CancellationNotes` (Optional): Free-text notes providing additional context.
3.  **Process:**
    - The system validates that the `Appointment` exists and is in a state that allows cancellation (e.g., `Scheduled`).
    - The system updates the `Appointment` record's status to `Cancelled`.
    - The `CancellationReasonCode` and `CancellationNotes` are stored against the `Appointment` record.
    - An immutable `AppointmentAudit` record is created, capturing the state change to `Cancelled` and the associated reason.
    - If the `Appointment` is linked to a `WorkOrder` or `Estimate`, the system publishes an `AppointmentCancelled` domain event.
4.  **Outcome:** The appointment is successfully cancelled, an audit trail is created, and a domain event is published if applicable.

## Alternate / Error Flows
- **Appointment Not Found:** If the provided `AppointmentID` does not exist, the system returns a `404 Not Found` error.
- **Invalid State:** If an attempt is made to reschedule or cancel an appointment that is not in a modifiable state (e.g., `InProgress`, `Completed`, `Cancelled`), the system returns a `409 Conflict` error with a descriptive message.
- **Resource Conflict:** If a reschedule request is made for a time window where the requested resource is unavailable, the system returns a `409 Conflict` error indicating a scheduling conflict.
- **Invalid Input:** If the `CancellationReasonCode` is not from the predefined list of valid codes, the system returns a `400 Bad Request` error.

## Business Rules
- Appointments may only be rescheduled or cancelled if their current status is `Scheduled`.
- All `CancellationReasonCode`s must be selected from a centrally managed, predefined list.
- All modifications (reschedule, cancel) to an `Appointment` must generate an immutable audit record.
- Domain events (`AppointmentRescheduled`, `AppointmentCancelled`) are only published if the `Appointment` has a direct link to an existing `WorkOrder` or `Estimate`.

## Data Requirements
- **`Appointment` Entity:**
    - `AppointmentID` (PK)
    - `Status` (Enum: `Scheduled`, `InProgress`, `Completed`, `Cancelled`)
    - `ScheduledTimeWindow` (Start/End Timestamps)
    - `AssignedResourceID` (FK)
    - `WorkOrderLinkRef` (FK, Nullable)
    - `CancellationReasonCode` (String, Nullable)
    - `CancellationNotes` (Text, Nullable)
- **`AppointmentAudit` Entity:**
    - `AuditID` (PK)
    - `AppointmentID` (FK)
    - `Timestamp`
    - `ActorID` (Who made the change)
    - `Action` (Enum: `CREATED`, `RESCHEDULED`, `CANCELLED`)
    - `Changes` (JSON/Text field detailing the "from" and "to" values)

## Acceptance Criteria

### AC1: Successful Rescheduling of an Appointment
**Given** a `Dispatcher` is logged in
**And** an `Appointment` exists with `Status: Scheduled`
**When** the `Dispatcher` reschedules the appointment to a new, available time window
**Then** the system must update the `Appointment`'s `ScheduledTimeWindow`
**And** the system must create an `AppointmentAudit` record with `Action: RESCHEDULED`
**And** the system must return a `200 OK` success response.

### AC2: Successful Cancellation of an Appointment
**Given** a `Dispatcher` is logged in
**And** an `Appointment` exists with `Status: Scheduled`
**When** the `Dispatcher` cancels the appointment with a valid `CancellationReasonCode`
**Then** the system must update the `Appointment`'s `Status` to `Cancelled`
**And** the system must store the `CancellationReasonCode`
**And** the system must create an `AppointmentAudit` record with `Action: CANCELLED`
**And** the system must return a `200 OK` success response.

### AC3: Domain Event Published for Linked Appointment
**Given** an `Appointment` is linked to a `WorkOrder`
**When** that `Appointment` is successfully rescheduled or cancelled
**Then** the system must publish an `AppointmentRescheduled` or `AppointmentCancelled` domain event to the message bus.

### AC4: No Domain Event for Unlinked Appointment
**Given** an `Appointment` is not linked to a `WorkOrder` or `Estimate`
**When** that `Appointment` is successfully rescheduled or cancelled
**Then** the system must **not** publish any domain event.

### AC5: Attempt to Modify a Completed Appointment Fails
**Given** a `Dispatcher` is logged in
**And** an `Appointment` exists with `Status: Completed`
**When** the `Dispatcher` attempts to reschedule or cancel that appointment
**Then** the system must reject the request with a `409 Conflict` error
**And** the `Appointment`'s state must remain unchanged.

## Audit & Observability
- **Audit Trail:** An `AppointmentAudit` record MUST be created for every successful reschedule or cancellation action. This record is immutable and must contain the actor, timestamp, action type, and a payload detailing the changed data.
- **Domain Events:**
    - `AppointmentRescheduled`: Published when a linked appointment's time or resource is changed.
        - Payload should include: `AppointmentID`, `WorkOrderLinkRef`, `OldTimeWindow`, `NewTimeWindow`.
    - `AppointmentCancelled`: Published when a linked appointment is cancelled.
        - Payload should include: `AppointmentID`, `WorkOrderLinkRef`, `CancellationReasonCode`.
- **Metrics:** The service should expose metrics for:
    - `appointments.rescheduled.count`
    - `appointments.cancelled.count` (tagged by `CancellationReasonCode`)

## Open Questions
1.  **Cancellation Reasons:** What is the definitive, initial list of `CancellationReasonCode`s?
2.  **Notification Scope:** The story specifies publishing events only when linked to a `WorkOrder` or `Estimate`. Please confirm this is the desired behavior, as other systems (like customer notifications) might want to know about all appointment changes, regardless of linkage.

## Original Story (Unmodified – For Traceability)
# Issue #73 — [BACKEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Scheduling: Reschedule or Cancel Appointment with Audit

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to reschedule or cancel an appointment so that the plan stays accurate.

## Details
- Reschedule: change window/resource; Cancel: reason.
- Notify workexec if linked to estimate/WO.

## Acceptance Criteria
- Reschedule updates schedule.
- Cancel sets status+reason.
- Changes audited.

## Integrations
- Emit AppointmentUpdated/Cancelled to workexec when linked.

## Data / Entities
- Appointment, AppointmentAudit, WorkexecLinkRef

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