Title: [BACKEND] [STORY] Timekeeping: Record Break Start/End
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/84
Labels: type:story, domain:people, status:ready-for-dev

STOP: Clarification required before finalization

## 🏷️ Labels (Final)

### Required
- type:story
- domain:people
- status:ready-for-dev

### Recommended
- agent:people
- agent:story-authoring

---
**Rewrite Variant:** crm-pragmatic

---

## Story Intent

**As a** Mechanic,
**I want to** accurately record the start and end times of my breaks during a workday,
**so that** my timecard reflects my actual working hours, ensuring correct pay and compliance with labor regulations.

## Actors & Stakeholders

- **Mechanic (Primary Actor):** The user performing the action of starting and ending a break.
- **Shop Manager (Stakeholder):** Reviews and approves timecards, which will now include break data.
- **System (Actor):** The POS backend system responsible for validating rules and persisting time entries.

## Preconditions

- The Mechanic must have an active, open `ClockInSession` for the current workday.
- An open `Timecard` entity must exist for the Mechanic for the current workday, associated with the active `ClockInSession`.

## Functional Behavior

1. **Start Break (with Type Selection - NEW):**
   - **Trigger:** The Mechanic initiates the "Start Break" action via a client interface (e.g., POS terminal).
   - **System Action:**
     1. The system receives a request to start a break for the authenticated Mechanic.
     2. Request includes `breakType` (MEAL, REST, or OTHER).
     3. It validates that:
        - Mechanic has an active `ClockInSession`
        - No other break is currently `IN_PROGRESS`
        - `breakType` is provided and valid
     4. Upon successful validation, the system creates a new `Break` record.
     5. Record is associated with Mechanic's active `Timecard` with:
        - `startTime = current UTC timestamp`
        - `breakType` as provided
        - `status = IN_PROGRESS`
     6. The system returns a success confirmation with the created break data.
   - **UI Enhancement:** Default `breakType` to the mechanic's last-used break type to reduce friction.

2. **End Break:**
   - **Trigger:** The Mechanic initiates the "End Break" action.
   - **System Action:**
     1. The system receives a request to end a break for the authenticated Mechanic.
     2. It validates that there is an active break (`status = IN_PROGRESS`) for the Mechanic.
     3. Upon successful validation, the system updates the existing `Break` record:
        - `endTime = current UTC timestamp`
        - `status = COMPLETED`
        - `endReason = MANUAL_ENDED`
     4. The system returns a success confirmation.

3. **Automatic Break Ending at Clock-Out (NEW):**
   - **Trigger:** Mechanic clocks out while a break is `IN_PROGRESS`.
   - **System Action:**
     1. During clock-out processing, system checks if an active break exists.
     2. If found, system automatically ends the break:
        - `endTime = clockOutTime`
        - `status = COMPLETED`
        - `endReason = AUTO_ENDED_AT_CLOCKOUT`
        - `autoEnded = true` (audit flag)
        - `triggerEventId` = clock-out event ID
        - `changedBy = system`
     3. Clock-out proceeds normally without error or blocking.
     4. Audit event `BreakAutoEnded` is generated.

## Alternate / Error Flows

- **Flow 1: Attempt to start a break while another is active.**
  - **Trigger:** A Mechanic with an `IN_PROGRESS` break attempts to start another break.
  - **System Response:** The request is rejected with an error indicating that a break is already in progress.

- **Flow 2: Attempt to end a break when none is active.**
  - **Trigger:** A Mechanic with no `IN_PROGRESS` break attempts to end a break.
  - **System Response:** The request is rejected with an error indicating that there is no active break to end.

- **Flow 3: Attempt to start a break without specifying break type (NEW).**
  - **Trigger:** A Mechanic submits start-break request without `breakType` field.
  - **System Response:** The request is rejected with an error indicating that `breakType` is required.

- **Flow 4: Attempt to start a break when not clocked in.**
  - **Trigger:** A Mechanic who is not clocked in attempts to start a break.
  - **System Response:** The request is rejected with an error indicating that the user must be clocked in to start a break.

- **Flow 5: Clock-out attempted while break is active (NEW).**
  - **Trigger:** Mechanic attempts to clock out with an `IN_PROGRESS` break.
  - **System Response:** Break is automatically ended with `endReason = AUTO_ENDED_AT_CLOCKOUT`, and clock-out completes successfully (no error).

## Business Rules

- **R1: Exclusive Active Break:** A Mechanic can have a maximum of one `IN_PROGRESS` break at any given time.
- **R2: Clock-In Dependency:** A break can only be started or ended within the time boundaries of an active `ClockInSession`.
- **R3: Non-Overlapping Breaks:** The start and end times of any two `Break` records for the same `Timecard` must not overlap. The system must enforce this chronologically.
- **R4: Break Type Required (NEW):** Every break must have a `breakType` from the enum: `MEAL`, `REST`, `OTHER`.
- **R5: Automatic Closure at Clock-Out (NEW):** If a break is `IN_PROGRESS` at clock-out time, the system automatically ends it with `endReason = AUTO_ENDED_AT_CLOCKOUT` without blocking clock-out.

## Data Requirements

The implementation will require a new or modified entity to store break information, associated with a user's timecard.

- **Entity: `Break`**
  - `id`: Unique identifier (UUID)
  - `timecard_id`: Foreign key to the parent `Timecard` entity
  - `mechanic_id`: Foreign key to the `Mechanic` (User) entity
  - `breakType`: Enum (`MEAL`, `REST`, `OTHER`) — **required** (NEW)
  - `status`: Enum (`IN_PROGRESS`, `COMPLETED`)
  - `startTime`: Timestamp with UTC timezone (not null)
  - `endTime`: Timestamp with UTC timezone (nullable until break is completed)
  - `endReason`: Enum (`MANUAL_ENDED`, `AUTO_ENDED_AT_CLOCKOUT`) (NEW) — nullable until break ends
  - `autoEnded`: Boolean, default false (NEW) — indicates system-triggered closure
  - `triggerEventId`: UUID, nullable (NEW) — event ID that triggered auto-end (e.g., clock-out event ID)
  - `createdAt`: Timestamp, system-managed
  - `updatedAt`: Timestamp, system-managed
  - `createdBy`: UUID (authenticated mechanic)
  - `updatedBy`: UUID (authenticated mechanic or system)
  - Optional `notes`: String (for `OTHER` break type explanations)

## Acceptance Criteria

**Scenario 1: Successfully record a work break**
- **Given** a Mechanic is clocked in and has an active `Timecard`.
- **When** the Mechanic initiates "Start Break" with `breakType = MEAL`.
- **And** a period of time passes.
- **And** the Mechanic initiates "End Break".
- **Then** the system creates one `Break` record associated with their `Timecard`.
- **And** the record has a valid `startTime` and `endTime`.
- **And** the `endTime` is after the `startTime`.
- **And** `breakType = MEAL` is stored.
- **And** `endReason = MANUAL_ENDED`.

**Scenario 2: Attempt to start a break when one is already in progress**
- **Given** a Mechanic is clocked in and has an active break in the `IN_PROGRESS` state.
- **When** the Mechanic attempts to initiate "Start Break" again.
- **Then** the system rejects the request with an error message "A break is already in progress."
- **And** no new `Break` record is created.

**Scenario 3: Attempt to end a break when no break is active**
- **Given** a Mechanic is clocked in but does not have a break in the `IN_PROGRESS` state.
- **When** the Mechanic attempts to initiate "End Break".
- **Then** the system rejects the request with an error message "No active break to end."

**Scenario 4: Attempt to start a break without specifying type (NEW)**
- **Given** a Mechanic is clocked in and has an active `Timecard`.
- **When** the Mechanic submits a start-break request without providing `breakType`.
- **Then** the system rejects the request with an error message "`breakType` is required."

**Scenario 5: Attempt to start a break when not clocked in**
- **Given** a Mechanic is not clocked in.
- **When** the Mechanic attempts to initiate "Start Break".
- **Then** the system rejects the request with an error message "You must be clocked in to start a break."

**Scenario 6: Automatic break end at clock-out (NEW)**
- **Given** a Mechanic has an active break with `status = IN_PROGRESS`.
- **When** the Mechanic clocks out for the day.
- **Then** the system automatically ends the break:
  - `endTime = clockOutTime`
  - `endReason = AUTO_ENDED_AT_CLOCKOUT`
  - `autoEnded = true`
  - `triggerEventId = clockOutEventId`
- **And** the clock-out completes successfully (no error).
- **And** a `BreakAutoEnded` audit event is generated.

**Scenario 7: UI defaults break type to mechanic's last-used type (NEW)**
- **Given** a Mechanic has previously started breaks with `breakType = REST`.
- **When** the Mechanic initiates "Start Break" again.
- **Then** the UI pre-selects `breakType = REST` as the default.
- **And** the Mechanic can change it if needed.

## Audit & Observability

- **Audit Trail:**
  - Every successful `Start Break`, `End Break`, and auto-ended break action must generate an audit event.
  - Events must be immutable and include:
    - `eventType` (`BREAK_STARTED`, `BREAK_ENDED`, `BREAK_AUTO_ENDED`)
    - `mechanic_id`, `timecard_id`, `break_id`
    - `breakType` (on start)
    - `endReason` (on end) (NEW)
    - `autoEnded` flag (for auto-ended breaks) (NEW)
    - `triggerEventId` (for auto-ended breaks) (NEW)
    - Server timestamp
    - Actor (mechanic ID or "system" for auto-end) (NEW)

- **Logging:**
  - Log all validation failures (e.g., attempting to start an overlapping break) at a `WARN` level for monitoring.
  - Log all auto-end operations at `INFO` level for audit trail visibility.

- **Metrics:**
  - `break.started` (Counter, tagged by break type)
  - `break.ended_manual` (Counter)
  - `break.ended_auto_at_clockout` (Counter) (NEW)
  - `break.duplicate_prevented` (Counter)

## Original Story (Unmodified – For Traceability)
# Issue #84 — [BACKEND] [STORY] Timekeeping: Record Break Start/End

[Original story body preserved as provided in previous issue snapshot]
