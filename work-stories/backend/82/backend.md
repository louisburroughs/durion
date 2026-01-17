Title: [BACKEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/82
Labels: type:story, domain:workexec, status:ready-for-dev

## Story Intent
As a **Mechanic**, I want **to start and stop a timer against an assigned work order task**, so that **the system can accurately track my labor time for that task without manual calculation**.

## Actors & Stakeholders
- **Mechanic (Primary Actor)**: Starts/stops timer on assigned work.
- **System (Actor)**: Enforces timer constraints, writes immutable time entries, and flags exceptions.
- **Service Advisor (Stakeholder)**: Reviews labor times for billing accuracy.
- **Billing/Accounting (Downstream)**: Consumes completed time entries for job costing/invoicing.

## Preconditions
1. Mechanic is authenticated.
2. Mechanic has permission to use timers.
3. Work order exists, is in a “workable” state, and is assigned to the authenticated mechanic.

## Functional Behavior
### 1) Get Active Timer (Support UI + Recovery)
- **Endpoint:** `GET /api/workexec/time-entries/timer/active`
- **Behavior:** returns the current ACTIVE timer(s) for the authenticated mechanic.

### 2) Start Timer
- **Endpoint:** `POST /api/workexec/time-entries/timer/start`
- **Request body:**
```json
{
  "workOrderId": "uuid",
  "workOrderItemId": "uuid|null",
  "laborCode": "string|null"
}
```
- **System actions (single transaction):**
  1. Derive `mechanicId` from the authenticated principal (never from request payload).
  2. Validate permission and assignment:
     - work order exists
     - work order state is workable (default: `IN_PROGRESS`)
     - mechanic is assigned to the work order (or the specific item if `workOrderItemId` is provided)
     - if `workOrderItemId` provided, verify it belongs to `workOrderId`
  3. Enforce active timer constraints (see Business Rules).
  4. Create a new `TimeEntry` with:
     - `status = ACTIVE`
     - `startTime = now (server time, UTC)`
     - link to `workOrderId` and optionally `workOrderItemId` / `laborCode`
  5. Emit audit/event `TimerStarted`.

- **Success response:** `201 Created` with the created time entry summary.

### 3) Stop Timer
- **Endpoint:** `POST /api/workexec/time-entries/timer/stop`
- **Request body:** none.
- **System actions (single transaction):**
  1. Locate ACTIVE timer(s) for the authenticated mechanic.
  2. If none exist, fail (see Alternate/Error Flows).
  3. For each timer being stopped:
     - set `endTime = now (server time, UTC)`
     - compute `durationInSeconds = endTime - startTime`
     - set `status = COMPLETED`
  4. Emit audit/event `TimerStopped`.

- **Success response:** `200 OK` with `stopped[]` containing the final details.

## Alternate / Error Flows
- **Start with existing active timer (single-timer mode):** `409 Conflict` with `error=TIMER_ALREADY_ACTIVE`. Client should call `GET …/active` to recover.
- **Stop when no timer is active:** `409 Conflict` with `error=NO_ACTIVE_TIMER`.
- **Invalid/unauthorized work order assignment:**
  - `403 Forbidden` if permission missing
  - `404 Not Found` if work order/item not found (avoid leaking existence)
  - `409 Conflict` if not in workable state / not assigned.

## Business Rules
### BR1: Mechanic identity
- `mechanicId` is derived from authentication context only.

### BR2: Active timer constraints
- Default policy is single timer:
  - `allowConcurrentTaskTimers = false`
  - `maxConcurrentTaskTimers = 1`
- Concurrent timers are enabled only by policy + role permission:
  - roles with `timekeeping:timer:concurrent` may run multiple timers up to `maxConcurrentTaskTimersForRole`.

### BR3: Multi-timer stop semantics (v1)
- In concurrent-timer mode, `POST …/stop` stops **all ACTIVE timers** for the mechanic and returns them in `stopped[]`.

### BR4: Time entry immutability
- Once a timer entry is `COMPLETED` or `AUTO_STOPPED`, it is immutable through start/stop timer APIs.
- Any changes require a separate adjustment workflow (out of scope for this story).

### BR5: Abandoned timers (auto-stop + exception)
- Auto-stop triggers:
  1. **Clock-out handler**: auto-stop ACTIVE timers at clock-out time.
  2. **Scheduled job**: detect ACTIVE timers older than `maxTimerDurationMinutes` (default 720) and auto-stop them.
- Auto-stopped timers are flagged with a `TimeException`:
  - `exceptionCode = ABANDONED_TIMER`
  - `severity = WARNING` unless policy config sets `BLOCKING`
- Policy defaults:
  - `maxTimerDurationMinutes = 720`
  - `autoStopOnClockOut = true`

### BR6: Breaks / pausing
- Timer pausing is **out of scope** for v1.
- Break policy may stop timers based on separate break tracking policy.

## Data Requirements
### Entity: `TimeEntry`
- Fields: `timeEntryId`, `mechanicId`, `workOrderId`, `workOrderItemId?`, `laborCode?`, `startTime (UTC)`, `endTime? (UTC)`, `durationInSeconds?`, `status`.
- Status enum includes:
  - `ACTIVE`
  - `COMPLETED` (user stop)
  - `AUTO_STOPPED` (system stop)

### Entity: `TimeException`
- Fields (minimum): `timeExceptionId`, `timeEntryId`, `exceptionCode`, `severity`, `createdAt`, `notes?`.

### Timekeeping policy configuration
- `allowConcurrentTaskTimers` (bool, default false)
- `maxConcurrentTaskTimers` (int, default 1)
- `maxTimerDurationMinutes` (int, default 720)
- `autoStopOnClockOut` (bool, default true)

## Acceptance Criteria
### AC1: Start + stop timer creates a completed time entry
- Given mechanic is assigned to a valid workable work order and has no active timer (single-timer mode)
- When mechanic starts a timer
- Then a `TimeEntry(status=ACTIVE, startTime=now)` is created
- When mechanic stops the timer
- Then the entry is updated with `endTime`, `durationInSeconds`, and `status=COMPLETED`.

### AC2: Prevent second timer in default single-timer mode
- Given mechanic already has an ACTIVE timer
- When mechanic starts another timer
- Then API returns `409 TIMER_ALREADY_ACTIVE` and no new entry is created.

### AC3: Stop fails when none active
- Given mechanic has no ACTIVE timers
- When mechanic stops
- Then API returns `409 NO_ACTIVE_TIMER`.

### AC4: Auto-stop on clock-out
- Given mechanic clocks out with an ACTIVE timer
- When clock-out is processed
- Then timer is auto-stopped with `status=AUTO_STOPPED`
- And a `TimeException(ABANDONED_TIMER)` is created.

### AC5: Auto-stop on duration threshold
- Given an ACTIVE timer runs longer than `maxTimerDurationMinutes`
- When the scheduled job detects it
- Then timer is auto-stopped with `status=AUTO_STOPPED`
- And a `TimeException(ABANDONED_TIMER)` is created.

## Audit & Observability
- Emit audit/event records (or equivalent) for:
  - `TimerStarted`
  - `TimerStopped`
  - `TimerStartFailed` (include reason code)
  - `TimerAutoStopped` (include stopReason: `CLOCK_OUT|MAX_DURATION_EXCEEDED`)
- Metrics:
  - Gauge: active timers count
  - Counters: start/stop rate, failure rate by reason, abandoned timers detected

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
# Issue #82 — [BACKEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to start and stop a job timer for a workorder task** so that **I can accurately capture job time without manual calculations**.

## Details
- Timer references workOrderId and optional workOrderItemId/laborCode.
- Enforce one active timer per mechanic (default).

## Acceptance Criteria
- Start/stop timer produces a job time entry.
- Prevent multiple active timers unless configured.
- Audited.

## Integration Points (workexec)
- Inbound: WorkOrderAssigned for context.

## Data / Entities
- TimeEntry (job)
- JobLink

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