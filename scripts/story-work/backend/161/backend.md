Title: [BACKEND] [STORY] Execution: Assign Technician to Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/161
Labels: type:story, domain:workexec, status:needs-review

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
**As a** Shop Manager or Dispatcher,
**I want to** assign a specific Technician to a Workorder that is ready for execution,
**so that** accountability for the work is established, the Technician is enabled to perform the work, and the assignment is tracked for operational reporting.

## Actors & Stakeholders
- **Primary Actor:** `Shop Manager` / `Dispatcher` - The user responsible for assigning work.
- **Secondary Actor:** `Technician` - The user who is assigned the work and requires visibility into the Workorder.
- **System Actor:** `POS System` - The system of record that processes the assignment, enforces rules, and records audit trails.
- **Stakeholders:**
    - `Service Advisor`: Needs to know which Technician is responsible for a customer's Workorder.
    - `Operations Manager`: Reviews assignment history and metrics for shop performance.

## Preconditions
- A Workorder exists with a status that permits assignment (e.g., `SCHEDULED`, `READY_FOR_WORK`).
- A list of eligible `Technician` users exists in the system.
- The `Shop Manager` is authenticated and possesses the `WORKORDER_ASSIGN` permission.

## Functional Behavior

### 4.1. Happy Path: Assigning a Technician
1.  **Trigger:** The `Shop Manager` selects a Workorder in an assignable state and initiates the "Assign Technician" action.
2.  The system presents a list of available Technicians.
3.  The `Shop Manager` selects a Technician from the list.
4.  The system validates that the selected Technician is eligible for assignment.
5.  The system creates a new `TechnicianAssignment` record, linking the `Workorder`, the `Technician`, and the `Shop Manager` (`assignedByUserId`). The record is marked as `isActive=true` and `assignedAt` is set to the current timestamp.
6.  If a previous active assignment exists for this Workorder, the system deactivates it by setting `isActive=false` and populating `unassignedAt`.
7.  The system updates the `Workorder`'s status to `ASSIGNED`.
8.  The system grants the assigned `Technician` the necessary visibility/permissions to view and interact with the Workorder.
9.  The system emits a `WorkorderTechnicianAssigned` event for downstream consumers (e.g., auditing, notifications).

## Alternate / Error Flows
- **Flow 5.1: Unauthorized Assignment Attempt**
    1. A user without the `WORKORDER_ASSIGN` permission attempts to assign a Technician.
    2. The system rejects the request with a `403 Forbidden` error and logs the security violation.
- **Flow 5.2: Assignment to a Workorder in a Non-Assignable State**
    1. A `Shop Manager` attempts to assign a Technician to a Workorder with a status of `COMPLETED` or `CANCELED`.
    2. The system rejects the request with a `409 Conflict` or `400 Bad Request` error, indicating the Workorder is not in a valid state for assignment.
- **Flow 5.3: Assignment of an Ineligible User**
    1. A `Shop Manager` attempts to assign a user who is not a `Technician` (e.g., a `Service Advisor`).
    2. The system rejects the request with a `400 Bad Request` error.
- **Flow 5.4: Technician is Unavailable (Requires Clarification)**
    1. See Open Questions. The system behavior (warn vs. block) for assigning a Technician who is unavailable (e.g., on leave, already over-allocated) is undefined.

## Business Rules
- **Rule 6.1 (Single Primary Assignment):** A Workorder can have only one `isActive=true` `TechnicianAssignment` at any given time.
- **Rule 6.2 (Immutable History):** `TechnicianAssignment` records are immutable. A reassignment deactivates the existing record and creates a new one; records are never deleted.
- **Rule 6.3 (Role-Based Access):** Only users with the `WORKORDER_ASSIGN` permission can create or modify assignments.
- **Rule 6.4 (Visibility Control):** A Technician can only view the details of a Workorder if they have an active assignment to it (or possess higher-level permissions).

## Data Requirements
- **Entity: `TechnicianAssignment`**
    - `assignmentId` (UUID, PK): Unique identifier for the assignment record.
    - `workorderId` (UUID, FK): Foreign key to the `Workorder` entity.
    - `technicianId` (UUID, FK): Foreign key to the `User` entity representing the technician.
    - `assignedByUserId` (UUID, FK): Foreign key to the `User` entity who performed the assignment.
    - `assignedAt` (TimestampTZ): The exact time the assignment was made.
    - `unassignedAt` (TimestampTZ, Nullable): The time the assignment was superseded.
    - `isActive` (Boolean): Flag indicating if this is the current, active assignment.

## Acceptance Criteria

### AC 8.1: Successful Technician Assignment
- **Given** a `Workorder` exists with status `SCHEDULED`.
- **And** the `Shop Manager` has the `WORKORDER_ASSIGN` permission.
- **When** the `Shop Manager` assigns `Technician A` to the `Workorder`.
- **Then** the system creates a new `TechnicianAssignment` record with `workorderId`, `technicianId` for `Technician A`, and `isActive=true`.
- **And** the `Workorder` status transitions to `ASSIGNED`.
- **And** `Technician A` can now view the details of that `Workorder`.

### AC 8.2: Reassignment Creates History
- **Given** `Technician A` is actively assigned to a `Workorder`.
- **When** the `Shop Manager` reassigns the `Workorder` to `Technician B`.
- **Then** the original `TechnicianAssignment` record for `Technician A` is updated to `isActive=false` and `unassignedAt` is populated.
- **And** a new `TechnicianAssignment` record is created for `Technician B` with `isActive=true`.
- **And** the `Workorder` status remains `ASSIGNED`.
- **And** `Technician A` loses default access to the Workorder, while `Technician B` gains it.

### AC 8.3: Unauthorized User Cannot Assign
- **Given** a `Workorder` exists with status `SCHEDULED`.
- **And** a user with the `SERVICE_ADVISOR` role (lacking `WORKORDER_ASSIGN` permission) is logged in.
- **When** the user attempts to assign a Technician to the `Workorder`.
- **Then** the system must reject the operation with a `403 Forbidden` status.
- **And** no `TechnicianAssignment` record is created or modified.

## Audit & Observability
- **Event Logging:**
    - An event `WorkorderTechnicianAssigned` MUST be published on successful assignment. Event payload should include `workorderId`, `technicianId`, `assignedByUserId`, and `assignedAt`.
    - An event `WorkorderTechnicianUnassigned` MUST be published upon reassignment for the previous technician. Payload should include `workorderId`, `technicianId`, and `unassignedAt`.
- **Application Logging:**
    - Log successful assignment actions at `INFO` level, including relevant IDs.
    - Log failed assignment attempts (e.g., permission denied, invalid state) at `WARN` level.
    - Log unexpected system errors during assignment at `ERROR` level.

## Open Questions
1.  **Technician Availability Policy:** The original story mentions "Technician unavailable → system prevents assignment or warns based on schedule policy."
    - **Question:** What is the authoritative source for technician availability/schedule? Is it a separate `Schedules` domain?
    - **Question:** What is the precise business rule? Should the system hard-block an assignment or show a soft warning that the manager can override?
2.  **Technician Notification:** The original story mentions "System optionally notifies technician."
    - **Question:** What channels are required for notification (e.g., in-app, SMS, email)?
    - **Question:** Who configures this "optional" behavior (e.g., system-wide, per-location, per-user)?
    - **Question:** What is the required content of the notification message?

## Original Story (Unmodified – For Traceability)
# Issue #161 — [BACKEND] [STORY] Execution: Assign Technician to Workorder

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Assign Technician to Workorder

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
Shop Manager / Dispatcher

## Trigger
A workorder is Ready/Scheduled and needs assignment.

## Main Flow
1. User selects a workorder and opens assignment controls.
2. User assigns a primary technician or crew.
3. System records assignment timestamp and assigns visibility to the technician.
4. System optionally notifies technician.
5. System records assignment history on reassignment.

## Alternate / Error Flows
- Technician unavailable → system prevents assignment or warns based on schedule policy.
- Unauthorized role tries assignment → block.

## Business Rules
- Assignment history must be retained.
- Workorder visibility is role-based.

## Data Requirements
- Entities: Workorder, TechnicianAssignment, User, Notification
- Fields: workorderId, technicianId, assignedBy, assignedAt, unassignedAt, reason

## Acceptance Criteria
- [ ] Technician can be assigned and sees the workorder.
- [ ] Assignment changes are tracked with history.
- [ ] Unauthorized users cannot assign.

## Notes for Agents
Assignment data feeds execution metrics; keep it clean and auditable.


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