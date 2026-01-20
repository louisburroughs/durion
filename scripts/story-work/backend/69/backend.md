Title: [BACKEND] [STORY] Dispatch: Override Conflict with Manager Permission
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/69
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

**Goal:** To provide authorized users with a formal, auditable mechanism to bypass scheduling conflicts for high-priority work orders, ensuring operational flexibility without compromising process integrity.

**Problem:** Standard scheduling rules prevent double-booking or other resource conflicts. However, real-world emergencies or high-priority customer needs sometimes require forcing an appointment into a slot despite a conflict. The current system lacks a safe and traceable way to handle these exceptions.

**Value:** This feature allows Shop Managers to make informed operational decisions to accommodate urgent jobs, improving customer satisfaction and shop throughput, while creating a clear audit trail for accountability and future process analysis.

## Actors & Stakeholders

- **Shop Manager (Primary Actor):** An authorized user responsible for managing the shop's schedule and staff. They require the ability to override system-detected conflicts.
- **System (Supporting Actor):** The POS/scheduling system responsible for detecting scheduling conflicts, checking user permissions, and recording the override action.
- **Work Execution System (`workexec`):** The primary domain system of record for work orders and schedules. It needs to be aware that a work order was scheduled via a conflict override.
- **Security Service:** The authoritative service for validating user permissions (e.g., `shopmgr.appointment.override`).
- **Auditor:** A stakeholder who reviews override events to ensure policy compliance and identify patterns in scheduling exceptions.

## Preconditions

1.  A Shop Manager is authenticated and has an active session in the system.
2.  The Shop Manager is in the process of creating or modifying an appointment/work order.
3.  The system has detected a scheduling conflict that would normally prevent the action (e.g., technician double-booked, required bay unavailable).
4.  The scheduling conflict is presented to the Shop Manager.

## Functional Behavior

1.  **Conflict Detection & Permission Check:** Upon detecting a scheduling conflict, the System shall check if the active user possesses the `shopmgr.appointment.override` permission.
2.  **Override Affordance:** If the user has the required permission, the System will present an "Override Conflict" option alongside the conflict details.
3.  **Rationale Capture:** When the Shop Manager selects the override option, the System must prompt them to enter a mandatory, non-empty reason for the override.
4.  **Override Execution:** Upon submission of a valid reason, the System will:
    a.  Bypass the conflict-validation rule that initially blocked the action.
    b.  Persist the appointment/work order in the schedule.
    c.  Create a permanent, immutable `OverrideRecord` linking the user, appointment, timestamp, reason, and details of the conflict that was bypassed.
    d.  Flag the appointment entity to indicate it was created via a conflict override.
5.  **Downstream Notification:** The System will ensure that when the appointment data is published or sent to downstream systems (specifically `workexec`), it includes a context flag or attribute indicating that it is the result of a conflict override (e.g., `overrideContext: { type: "SCHEDULING_CONFLICT", reason: "Customer waiting on-site" }`).

## Alternate / Error Flows

1.  **Insufficient Permissions:** If the user attempts to schedule an appointment that causes a conflict but lacks the `shopmgr.appointment.override` permission, the "Override Conflict" option will not be available. The scheduling action will be blocked as per standard system behavior.
2.  **Missing Rationale:** If the Shop Manager initiates an override but fails to provide a reason (or provides an empty/whitespace-only reason), the System will display a validation error and will not proceed with the override until a valid reason is supplied.

## Business Rules

- The `shopmgr.appointment.override` permission is the sole authority for enabling this functionality.
- The override reason is mandatory and must be a non-empty string.
- The record of the override event is immutable and must be preserved for auditing purposes.
- The original conflict details (e.g., type of conflict, conflicting resource ID) must be stored as part of the override record.

## Data Requirements

**Appointment / WorkOrder Entity:**
- Must be updated to include a boolean flag or state to indicate an override occurred.
  - `isConflictOverride: boolean`

**OverrideRecord Entity (New):**
- A new data entity to capture the details of the override event.
- `overrideId`: Unique identifier for the record.
- `appointmentId`: Foreign key to the affected appointment/work order.
- `overriddenByUserId`: Identifier for the Shop Manager who performed the override.
- `overrideTimestamp`: The exact date and time of the override.
- `overrideReason`: The text reason provided by the manager.
- `conflictDetails`: A structured field (e.g., JSONB) containing data about the original conflict, such as `{ "type": "TECHNICIAN_DOUBLE_BOOKED", "resourceId": "tech-123", "conflictingAppointmentId": "appt-456" }`.

## Acceptance Criteria

**AC-1: Successful Override by Authorized Manager**
- **Given** a Shop Manager with the `shopmgr.appointment.override` permission is logged in
- **And** they are scheduling an appointment that creates a resource conflict
- **When** they choose to override the conflict and provide a valid reason like "Emergency customer vehicle"
- **Then** the appointment is successfully scheduled
- **And** an `OverrideRecord` is created with the correct user ID, appointment ID, reason, and conflict details
- **And** the appointment is flagged as a conflict override.

**AC-2: Override Denied Due to Lack of Permission**
- **Given** a user without the `shopmgr.appointment.override` permission is logged in
- **And** they are scheduling an appointment that creates a resource conflict
- **When** the system displays the conflict
- **Then** the option to override the conflict is not presented
- **And** the appointment cannot be scheduled in the conflicting slot.

**AC-3: Override Blocked by Missing Reason**
- **Given** a Shop Manager with the `shopmgr.appointment.override` permission is logged in
- **And** they are presented with a scheduling conflict
- **When** they attempt to execute the override without providing a reason
- **Then** the system displays a validation error message
- **And** the appointment is not scheduled until a valid reason is provided.

**AC-4: Downstream System is Notified of Override**
- **Given** a scheduling conflict has been successfully overridden by a Shop Manager
- **When** the appointment data is published for downstream consumers
- **Then** the data payload must contain a flag or context indicating that it was an override (e.g., `isConflictOverride: true`).

## Audit & Observability

- **Audit Event:** An `AppointmentScheduleConflictOverridden` event must be emitted upon successful completion of the override.
- **Event Payload:** The event must contain the full `OverrideRecord` data, including `appointmentId`, `overriddenByUserId`, `overrideTimestamp`, `overrideReason`, and `conflictDetails`.
- **Metrics:** The system should expose a metric to count the number of conflict overrides, which can be dimensioned by `shopId` or `userId`.

## Original Story (Unmodified – For Traceability)
# Issue #69 — [BACKEND] [STORY] Dispatch: Override Conflict with Manager Permission

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Dispatch: Override Conflict with Manager Permission

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to override a scheduling conflict with a reason so that urgent jobs can proceed with auditability.

## Details
- Requires shopmgr.appointment.override.
- Records rationale and impacts.

## Acceptance Criteria
- Permission required.
- Reason required.
- Conflict flagged.

## Integrations
- Workexec receives override/expedite flag via context.

## Data / Entities
- OverrideRecord, PermissionCheck

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