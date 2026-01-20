Title: [BACKEND] [STORY] Timekeeping: Mechanic Clock In/Out
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/85
Labels: type:story, domain:workexec, status:ready-for-dev

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

**As a** Mechanic,
**I want to** clock in when I start my shift and clock out when I end it,
**so that** my work hours are accurately tracked for attendance and payroll purposes.

## Actors & Stakeholders

- **Mechanic (Primary Actor):** The employee performing the clock-in and clock-out actions.
- **Shop Manager (Stakeholder):** Relies on accurate time records for shift management, labor cost tracking, and operational oversight.
- **Payroll System (Downstream Consumer):** Consumes the finalized time entries to process payroll.
- **System (Facilitator):** The POS or shop management system that provides the timekeeping interface and records the data.

## Preconditions

1.  The Mechanic has an active and provisioned employee profile in the system.
2.  The Mechanic is authenticated to a POS terminal or authorized device at a specific shop location.
3.  The system has access to a reliable, synchronized time source (NTP).

## Functional Behavior

### 1. Clock In

1.  **Trigger:** A logged-in Mechanic initiates the "Clock In" action via the system interface.
2.  **System Action:**
    a. The system verifies that the Mechanic is not already in a `CLOCKED_IN` state.
    b. The system validates the Mechanic's eligibility to clock in at the current `LocationID` (See Business Rules & Open Questions).
    c. A new `TimeEntry` record is created.
    d. The record is populated with the `MechanicID`, `LocationID`, the current UTC timestamp as `ClockInTimestamp`, and the local `ClockInTimezone`.
    e. The Mechanic's timekeeping status is set to `CLOCKED_IN`.
3.  **Outcome:** The system confirms a successful clock-in to the Mechanic.

### 2. Clock Out

1.  **Trigger:** A `CLOCKED_IN` Mechanic initiates the "Clock Out" action.
2.  **System Action:**
    a. The system finds the current open `TimeEntry` for that `MechanicID`.
    b. The system updates the record, setting the `ClockOutTimestamp` to the current UTC timestamp and recording the `ClockOutTimezone`.
    c. The Mechanic's timekeeping status is set to `CLOCKED_OUT`.
3.  **Outcome:** The system confirms a successful clock-out to the Mechanic.

## Alternate / Error Flows

- **Attempting to Clock In while already Clocked In:**
  - The system MUST prevent the creation of a new `TimeEntry`.
  - The system MUST display an error message, "You are already clocked in. You must clock out before clocking in again."
- **Attempting to Clock Out while not Clocked In:**
  - The system MUST prevent the action.
  - The system MUST display an error message, "You are not currently clocked in."
- **Mechanic not authorized for location (pending clarification):**
  - If strict location validation is enabled, the system MUST prevent the clock-in/out attempt.
  - The system MUST display an error, "Clock-in failed: You are not authorized for this location."
- **System Failure:**
  - If the database or backend service is unavailable, the action MUST fail gracefully.
  - The system MUST inform the user of a system error and advise them to try again or contact a manager.

## Business Rules

- **BR1: State Exclusivity:** A Mechanic can only be in one timekeeping state (`CLOCKED_IN` or `CLOCKED_OUT`) at any given time. A clock-in action is only valid from a `CLOCKED_OUT` state, and a clock-out action is only valid from a `CLOCKED_IN` state.
- **BR2: Transaction Atomicity:** Each clock-in or clock-out event must be an atomic transaction. If any part of the process fails (e.g., writing to the database), the entire operation must be rolled back.
- **BR3: Timestamp Authority:** All primary timestamps (`ClockInTimestamp`, `ClockOutTimestamp`) MUST be recorded in UTC and sourced from the server to ensure a single source of truth. The local timezone is stored for display and context.
- **BR4: Location Association (Clarification Required):** All time entries must be associated with a specific `LocationID`. The policy for validating if a mechanic is allowed to clock in at a given location needs to be defined. See **OQ1**.

## Data Requirements

The core entity is `TimeEntry`.

| Field               | Type      | Constraints                          | Description                                                               |
| ------------------- | --------- | ------------------------------------ | ------------------------------------------------------------------------- |
| `TimeEntryID`       | UUID      | Primary Key, Not Null                | Unique identifier for the time entry record.                              |
| `MechanicID`        | UUID      | Foreign Key (Employee), Not Null     | The employee this time entry belongs to.                                  |
| `LocationID`        | UUID      | Foreign Key (Location), Not Null     | The shop location where the event occurred.                               |
| `ClockInTimestamp`  | Timestamp | UTC, Not Null                        | The server-generated UTC timestamp of the clock-in event.                 |
| `ClockInTimezone`   | String    | Not Null, IANA Format                | The IANA timezone name of the location at clock-in (e.g., 'America/Chicago'). |
| `ClockOutTimestamp` | Timestamp | UTC, Nullable                        | The server-generated UTC timestamp of the clock-out event.                |
| `ClockOutTimezone`  | String    | Nullable, IANA Format                | The IANA timezone name of the location at clock-out.                      |
| `CreatedAt`         | Timestamp | UTC, Not Null, System-Managed        | Timestamp of when the record was created.                                 |
| `UpdatedAt`         | Timestamp | UTC, Not Null, System-Managed        | Timestamp of the last update to the record.                               |

## Acceptance Criteria

**AC1: Successful Clock-In**
- **Given** a Mechanic is authenticated and is in a `CLOCKED_OUT` state
- **When** the Mechanic initiates the "Clock In" action
- **Then** the system creates a new `TimeEntry` record with a `ClockInTimestamp`
- **And** the Mechanic's status is updated to `CLOCKED_IN`.

**AC2: Successful Clock-Out**
- **Given** a Mechanic is in a `CLOCKED_IN` state with an open `TimeEntry`
- **When** the Mechanic initiates the "Clock Out" action
- **Then** the system updates the existing `TimeEntry` with a `ClockOutTimestamp`
- **And** the Mechanic's status is updated to `CLOCKED_OUT`.

**AC3: Prevent Double Clock-In**
- **Given** a Mechanic is already in a `CLOCKED_IN` state
- **When** the Mechanic attempts to "Clock In" again
- **Then** the system rejects the action
- **And** displays an error message indicating they are already clocked in.

**AC4: Prevent Clock-Out without Clock-In**
- **Given** a Mechanic is in a `CLOCKED_OUT` state
- **When** the Mechanic attempts to "Clock Out"
- **Then** the system rejects the action
- **And** displays an error message indicating they are not currently clocked in.

**AC5: Time Entries are Auditable**
- **Given** a `TimeEntry` has been created or modified
- **When** an authorized user or system queries the data
- **Then** the record must contain the `MechanicID`, `LocationID`, and immutable UTC timestamps for all clock events.

## Audit & Observability

- **Events:**
  - `MechanicClockedIn`: Emitted on successful clock-in. Payload includes `TimeEntryID`, `MechanicID`, `LocationID`, `ClockInTimestamp`.
  - `MechanicClockedOut`: Emitted on successful clock-out. Payload includes `TimeEntryID`, `MechanicID`, `LocationID`, `ClockInTimestamp`, `ClockOutTimestamp`.
  - `TimeEntryCorrectionRequired`: Emitted if a system process detects an anomaly (e.g., a shift open for >24 hours) that requires manual review.
- **Logging:**
  - **INFO:** Log successful clock-in and clock-out events.
  - **WARN:** Log failed business rule validations (e.g., attempt to double clock-in).
  - **ERROR:** Log any system failures during the timekeeping process (e.g., database connection error).

## Open Questions

- **OQ1: Location Validation Policy:** The original story states, "Optional validation that mechanic is assigned to the selected location." This ambiguity must be resolved. What is the definitive business rule?
  - **a) Not enforced:** A mechanic can clock in at any company location.
  - **b) Strict enforcement:** A mechanic can ONLY clock in at their pre-assigned "home" location or a list of explicitly approved locations.
  - **c) Soft enforcement:** A mechanic can clock in at any location, but clock-ins at non-assigned locations are flagged for manager review.
  - **Initial Assumption:** Strict enforcement (b) is the safest default for financial controls, but this requires explicit business confirmation. The implementation of this story is blocked pending a decision.

## Original Story (Unmodified – For Traceability)
# Issue #85 — [BACKEND] [STORY] Timekeeping: Mechanic Clock In/Out

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Mechanic Clock In/Out

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to clock in and out** so that **my attendance time is recorded accurately**.

## Details
- Capture UTC timestamp and local timezone.
- Optional validation that mechanic is assigned to the selected location.

## Acceptance Criteria
- One-tap clock in/out.
- Prevent double clock-in without clock-out.
- Entries are auditable.

## Integration Points (workexec/shopmgr)
- shopmgr shift can be displayed for context (optional).

## Data / Entities
- TimeEntry (attendance)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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