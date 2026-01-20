Title: [BACKEND] [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/70
Labels: type:story, domain:workexec, status:ready-for-dev

## Story Intent
**As a** Dispatcher,
**I want** to assign mechanic(s) and a primary resource (bay/mobile unit) to a scheduled appointment,
**so that** work is properly allocated, scheduled without conflicts, audited, and can be executed.

## Actors & Stakeholders
- **Dispatcher**: creates/reassigns assignments.
- **Mechanic(s)**: assigned personnel with skills/availability.
- **Workshop/Operations Manager**: oversight of utilization and exceptions.
- **System**: enforces constraints, persists assignments, emits events.

## Preconditions
- `Appointment` exists and is in `SCHEDULED` state.
- Appointment has `scheduledStartAt` and `scheduledEndAt`.
- Mechanic roster + skills exist (imported from HR per Issue #72).
- Resource roster exists (bays/mobile units) with availability derived from assignments.
- AuthZ:
  - Base create: `workexec.assignment.create`
  - Override create (if used): `dispatch:assignment:override_create`

## Functional Behavior
### Core operation: create assignment
1. Dispatcher selects an appointment and invokes “Assign”.
2. Request includes:
   - `resourceId` (single primary resource)
   - `mechanics[]` (one or more mechanics; roles optional for single mechanic)
   - optional `override` block
3. System resolves time window from the appointment: `[scheduledStartAt, scheduledEndAt)`.
4. System validates:
   - appointment is `SCHEDULED` (hard block)
   - roles (see Business Rules)
   - skills coverage (see Business Rules)
   - availability conflicts (mechanics + resource) using assignment overlap (see Availability)
   - override requirements/permissions if override is used
5. On success, system persists:
   - `Assignment` (status `CONFIRMED`, `version=1`)
   - join rows for mechanics + roles
   - audit log entry
6. After commit, system emits `AssignmentCreated`.

### Availability model (SoT)
- **Assignments are the source of truth** for booked time blocks.
- Availability checks query for overlapping assignments within blocking statuses.

### Reassignment
- Allowed, but only **one active assignment per appointment** at a time.
- Reassignment creates a new `Assignment` row and marks the prior active assignment `CANCELLED` (or `SUPERSEDED`) for auditability.
- Emit `AssignmentUpdated` on reassign/cancel/status changes; maintain monotonic `version`.

## Alternate / Error Flows
- Availability conflict → reject with `409 Conflict` + code `MECHANIC_UNAVAILABLE` or `RESOURCE_UNAVAILABLE`.
- Skill mismatch / invalid role set → reject with `422 Unprocessable Entity` + code `SKILL_MISMATCH` or `INVALID_ROLE_SET`.
- Missing permissions → `403 Forbidden` (+ `OVERRIDE_NOT_PERMITTED` when override is attempted).
- Malformed request / missing required fields → `400 Bad Request`.

## Business Rules
### Assignment constraints
- Assignment links to exactly one appointment.
- Exactly one primary resource is persisted on `Assignment.resourceId`.
- Appointment must be `SCHEDULED` (hard block; not overrideable).

### Assignment statuses
- `CONFIRMED` (blocks availability)
- `IN_PROGRESS` (blocks availability)
- `COMPLETED` (does not block)
- `CANCELLED` (does not block)

### One active assignment per appointment
- Enforce: only one assignment with status in `(CONFIRMED, IN_PROGRESS)` per `appointmentId`.

### Mechanic roles
- If exactly one mechanic is assigned, role may be omitted; backend defaults to `LEAD`.
- If more than one mechanic is assigned:
  - each mechanic must have a role
  - exactly one mechanic must be `LEAD`
  - role enum: `LEAD`, `ASSIST`

### Skill validation (team coverage with optional lead constraint)
Let `requiredSkills = {(skillId, level, leadRequiredFlag)}`.
Validation passes if:
1. For every required skill where `leadRequiredFlag=true`: the lead mechanic has the skill at required level.
2. For every required skill where `leadRequiredFlag=false`: at least one assigned mechanic has the skill at required level.

### Override policy
**Overrideable categories**:
- `MECHANIC_AVAILABILITY`
- `RESOURCE_AVAILABILITY`
- `SKILL_MISMATCH`
- `LOCATION_MISMATCH`
- `POLICY_CONSTRAINT`

**Hard blocks (not overrideable)**:
- appointment not `SCHEDULED`
- appointment cancelled
- work order closed/invoiced (if linked)
- safety/regulatory constraint flag (if present)

**Override requirements**:
- `override.used=true` requires:
  - `overrideReasonCode` (enum)
  - `overrideNotes` (required)
  - `overriddenChecks[]` non-empty
- Requires permission `dispatch:assignment:override_create`.

Recommended reason codes (v1):
- `CUSTOMER_WAITING`, `EMERGENCY_SERVICE`, `MANAGER_DIRECTIVE`, `SHORT_STAFFED`, `TRAINING_SUPERVISED_WORK`, `SYSTEM_DATA_INCORRECT`

## Data Requirements
### Entities
- `Assignment`
  - `assignmentId` (UUID)
  - `appointmentId`
  - `resourceId`
  - `status` (`CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED`)
  - `version` (monotonic; create = 1)
  - override fields: `isOverridden`, `overrideReasonCode`, `overrideNotes`, `overriddenChecks[]`, `approvedBy?`
- `AssignmentMechanic` (join)
  - `assignmentId`, `mechanicId`, `role (LEAD|ASSIST)`

### Indexes/constraints (minimum)
- Unique “active assignment” constraint per `appointmentId` for statuses `(CONFIRMED, IN_PROGRESS)`.

## API (Minimum)
- `POST /appointments/{appointmentId}/assignments` (create)
- `GET /appointments/{appointmentId}/assignments` (history + active)
- Optional (can be deferred if out of scope): `PATCH /assignments/{assignmentId}` for status transitions / reassign

Create request example:
```json
{
  "resourceId": "UUID",
  "mechanics": [{ "mechanicId": "UUID", "role": "LEAD|ASSIST" }],
  "override": {
    "used": false,
    "overriddenChecks": [],
    "reasonCode": null,
    "notes": null
  }
}
```

## Concurrency & Correctness
- Prevent double-booking using DB-enforced correctness:
  - transaction + row locks (`SELECT ... FOR UPDATE`) on involved mechanic/resource rows (or schedule rows if present)
  - re-check overlap after locks
  - enforce unique active assignment per appointment

## Audit & Observability
- Always write audit entry for create/update/cancel.
- Override usage produces a high-priority audit entry.
- Emit events after commit:
  - `AssignmentCreated` on create
  - `AssignmentUpdated` on reassign/status changes

Metrics (minimum):
- `assignments.created.count`
- `assignments.failed.count` (tagged by reason)
- `assignments.overridden.count`

## Resolved Questions / Decisions Applied
- Clarification Issue #253 decisions applied: availability source-of-truth, reassign rules, statuses, time window source, resource/role rules, override policy, concurrency strategy, API surface, error codes, and event topic/schema.

## Original Story (Unmodified – For Traceability)
# Issue #70 — [BACKEND] [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to assign mechanics and a resource to an appointment so that work can be executed efficiently.

## Details
- Validate mechanic/resource availability + required skills.
- Support team assignment (lead/helper).

## Acceptance Criteria
- Assignment created only if checks pass (or override).
- Changes audited.
- Schedule updated.

## Integrations
- Emit AssignmentCreated/Updated to workexec when linked.

## Data / Entities
- Assignment, AssignmentRole, AuditLog

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