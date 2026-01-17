Title: [BACKEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/68
Labels: type:story, domain:workexec, status:ready-for-dev

## Story Intent
**As a** Mechanic,
**I want** to start and stop a work session for an assigned work order task,
**so that** labor time is captured accurately for operational execution and job costing, and can be consumed downstream (e.g., payroll) via events.

## Actors & Stakeholders
- **Mechanic (Primary Actor)**: starts/stops sessions and records breaks.
- **Workexec Service (SoR)**: owns `WorkSession` lifecycle and invariants.
- **Payroll / HR (Downstream Consumer)**: consumes work session events for payroll processing; does not mutate sessions.
- **Job Costing (Downstream Consumer)**: consumes labor actuals.
- **Service Manager / Admin (Approver)**: approves/locks sessions post-completion.

## System Boundary / Ownership
- **Decision (resolved):** `workexec` is the system of record for `WorkSession` and owns full lifecycle (create/start/stop/complete/lock).
- Downstream domains treat emitted events as source data and derive their own artifacts (pay periods, totals, etc.).

## Preconditions
- Mechanic is authenticated and authorized.
- `WorkOrder` and `WorkOrderTask` exist and are assigned to the mechanic.
- `WorkOrderTask` is in a state that permits starting work (e.g., `READY_FOR_WORK`).
- By default, mechanic has no other `IN_PROGRESS` work session.

## Functional Behavior
### Start work session
- Mechanic triggers “Start Work” on a specific assigned `WorkOrderTask`.
- System validates:
  - task is startable
  - overlap policy (see Business Rules)
- System creates `WorkSession`:
  - sets `startAt = now()` (UTC)
  - sets `status = IN_PROGRESS`
  - links `mechanicId`, `workOrderId`, `workOrderTaskId`, `locationId` (and optionally `resourceId` if relevant)
- Emit `workexec.WorkSessionStarted` after commit.

### Stop work session
- Mechanic triggers “Stop Work”.
- System finds the single active session for mechanic (or the active session for the given task, per API choice).
- System updates session:
  - sets `endAt = now()` (UTC)
  - sets `status = COMPLETED`
  - computes payable duration: `(endAt - startAt) - sum(breakDurations)`
- Emit `workexec.WorkSessionStopped` (or `workexec.WorkSessionCompleted`) after commit.

### Breaks
- Breaks are captured as explicit, manual `BreakSegment`s inside a `WorkSession`.
- Break rules:
  - break segments must be fully contained within the session window
  - break segments must not overlap each other
  - break segments become immutable once session is locked/approved

### Approval / Locking (state model support)
- Approval is manager-driven and locks the session.
- Minimal state model:
  - `IN_PROGRESS` → `COMPLETED` (mechanic stops)
  - `COMPLETED` → `APPROVED` (manager approves)
  - `APPROVED` implies `locked=true` (no edits)
- This story enforces “no edits when locked”; the dedicated approval endpoint/story may follow.

## Alternate / Error Flows
- Start while another session is `IN_PROGRESS` and overlap is not allowed → reject with conflict.
- Stop when no session is `IN_PROGRESS` → reject.
- Start on task not in valid state (ON_HOLD/COMPLETED/etc.) → reject.
- Attempt to edit session/breaks when locked/approved → reject.

## Business Rules
### Domain ownership
- `workexec` owns and persists work sessions; downstream consumers must not mutate them.

### Overlap policy
- **Default:** overlapping `WorkSession`s are not allowed.
- **Overlap is only allowed when all are true:**
  1. Config enabled: `company.timekeeping.allowOverlappingSessions = true` (or location-level equivalent)
  2. Actor has permission `timekeeping:overlap_override`
  3. Overlap is created via explicit override path and is fully audited (`overrideReason`, `overriddenByUserId`, timestamp)

### Time recording
- All timestamps recorded in UTC.

### Immutability
- Sessions and break segments are immutable when `locked=true` (typically status `APPROVED`).

## Data Requirements
### WorkSession
- `workSessionId` (UUID, PK)
- `mechanicId` (UUID)
- `workOrderId` (UUID)
- `workOrderTaskId` (UUID)
- `locationId` (UUID)
- `resourceId?` (UUID, optional)
- `startAt` (UTC timestamp)
- `endAt?` (UTC timestamp)
- `status` (`IN_PROGRESS`, `COMPLETED`, `APPROVED`, `REJECTED`)
- `locked` (boolean)
- `totalDurationSeconds` (int)
- Approval support fields (for next story but persisted now):
  - `approvedAt?`, `approvedByUserId?`, `approvalNotes?`, `lockedAt?`
- Overlap override audit fields (when used):
  - `overlapOverrideUsed` (bool), `overrideReason`, `overriddenByUserId`, `overrideAt`

### BreakSegment
- `breakSegmentId` (UUID, PK)
- `workSessionId` (UUID, FK)
- `breakStartAt` (UTC timestamp)
- `breakEndAt` (UTC timestamp)
- `breakType?` (enum: `MEAL`, `REST`, `OTHER`)
- `notes?`

## API (Minimum)
- `POST /work-sessions/start` (or task-scoped start endpoint)
- `POST /work-sessions/stop`
- Break management (can be separate endpoints):
  - `POST /work-sessions/{id}/breaks/start`
  - `POST /work-sessions/{id}/breaks/stop`

## Acceptance Criteria
- Start creates `WorkSession` in `IN_PROGRESS` with UTC `startAt`.
- Stop updates same session to `COMPLETED` with UTC `endAt` and computed duration net of breaks.
- Default overlap prevention blocks starting a session when another is in progress.
- Overlap is only possible with config + permission + explicit override/audit.
- Breaks are manual segments, non-overlapping, contained, and immutable once locked.
- Locked/approved sessions reject any mutation.

## Audit & Observability
- Emit events after commit:
  - `workexec.WorkSessionStarted`
  - `workexec.WorkSessionStopped` (or `workexec.WorkSessionCompleted`)
  - future: `workexec.WorkSessionApproved` / `workexec.WorkSessionLocked`
- Log all state transitions with correlation IDs.
- Metrics (minimum):
  - active sessions gauge
  - completed session duration histogram
  - overlap override count

## Open Questions
None.

## Resolved Questions / Decisions Applied
- Decision record in issue comments resolves the prior STOP/domain conflict:
  - `workexec` is SoR for `WorkSession`
  - overlap policy (default deny; allow only with config + permission + explicit override)
  - breaks are manual `BreakSegment`s
  - approval/locking is manager-driven; enforce immutability when locked

## Original Story (Unmodified – For Traceability)
# Issue #68 — [BACKEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Start/Stop Work Session for Assigned Work

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want to start/stop a work session tied to a workorder/task so that time is captured for payroll and costing.

## Details
- Work session includes mechanicId, workorderId, location/resource, start/end, breaks.
- Prevent overlap unless permitted.

## Acceptance Criteria
- Start/stop supported.
- Overlaps prevented.
- Lock after approval.

## Integrations
- Shopmgr→HR WorkSession events/API.
- Optional: Workexec consumes labor actuals.

## Data / Entities
- WorkSession, BreakSegment, ApprovalStatus

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