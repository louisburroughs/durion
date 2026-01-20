Title: [BACKEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/86
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
**As a** Manager,
**I want to** manage employee assignments to specific locations, including setting a primary location and effective dates,
**so that** downstream systems can accurately determine staffing eligibility, build rosters, and enforce operational rules.

## Actors & Stakeholders
- **Manager:** The primary user performing the assignment action via a management UI.
- **Employee:** The `Person` being assigned to one or more locations.
- **System (People Domain Service):** The service of record responsible for storing assignment data and enforcing business rules.
- **System (Work Execution Service):** A downstream consumer of assignment data that uses it to determine technician eligibility for jobs at a specific location.
- **System (Shop Manager Service):** A downstream consumer of assignment data used to generate operational rosters.
- **Auditor:** A stakeholder who needs to review the history of assignment changes for compliance and operational review.

## Preconditions
- A `Manager` user is authenticated and has the necessary permissions to manage employee assignments.
- The `Person` (Employee) to be assigned exists in the system with a unique `personId`.
- The `Location` to be assigned to exists in the system with a unique `locationId`.

## Functional Behavior
The system must provide capabilities to manage the lifecycle of `PersonLocationAssignment` records.

1. **Create Assignment:** A Manager can create a new assignment linking a `Person` to a `Location`. The assignment must include an `effectiveStartAt` and a boolean `isPrimary` flag. An `effectiveEndAt` is optional.
2. **Update Assignment:** A Manager can modify an existing assignment's attributes, such as its `effectiveStartAt`, `effectiveEndAt`, or `isPrimary` status.
3. **Deactivate Assignment:** A Manager can end an assignment by setting its `effectiveEndAt` to a past or current timestamp. A hard delete is not permitted to preserve historical records.
4. **Query Assignments:** The system must provide a way to query for all active and/or historical assignments for a given `Person` or `Location`.

### Primary Assignment Automatic Demotion (NEW)
- When creating a new primary assignment for `(personId, role)` (NEW):
  - System automatically finds any existing active primary assignment
  - If overlap detected: sets `effectiveEndAt = new.effectiveStartAt - 1 unit` (atomic transaction)
  - If no overlap: existing primary remains unchanged
  - Operation is **atomic**: demotion and creation succeed or both fail together

## Alternate / Error Flows
1. **Invalid References:** The system must return an error if an assignment is attempted with a non-existent `personId` or `locationId`.
2. **Invalid Timestamps:** The system must return an error if an `effectiveEndAt` is set before the `effectiveStartAt`.
3. **Overlapping Assignments (NEW):** The system must reject any transaction that would create overlapping assignments for the same `(personId, locationId, role)`. This prevents ambiguity in staffing/scheduling.
4. **Authorization Failure:** The system must reject attempts to manage assignments by users without the required permissions.

## Business Rules
- **BR1: Multiple Assignments Allowed:** A `Person` can have multiple `PersonLocationAssignment` records simultaneously (to different locations or time periods).
- **BR2: Single Primary Assignment:** For any given point in time, a `Person` must have **at most one** `PersonLocationAssignment` where `isPrimary=true`. Automatic demotion enforces this.
- **BR3: Effective Dating (Timestamps - NEW):**
  - Assignments are time-bound by `effectiveStartAt` (inclusive) and optional `effectiveEndAt` (inclusive)
  - Both fields are ISO-8601 timestamps in UTC
  - Assignment with null `effectiveEndAt` is active indefinitely
  - Active assignment: `effectiveStartAt <= now < effectiveEndAt` (or `effectiveEndAt` null)
- **BR4: No Overlapping Assignments (NEW):** For a given `(personId, locationId, role)`, there must be **no overlapping effective windows** among assignments. Prevents ambiguity.
- **BR5: Immutability of History:** Past assignments (where `effectiveEndAt` is in the past) must not be deleted.
- **BR6: Auditability:** All state-changing operations (Create, Update) on a `PersonLocationAssignment` must be logged in an audit trail, capturing the old/new values, the user performing the action, and a timestamp.
- **BR7: Event Emission (Strict Schema - NEW):** Any state change to an assignment must trigger publication of `PersonLocationAssignmentChanged` domain event with versioned strict JSON schema (see Audit & Observability).

## Data Requirements
The core entity is `PersonLocationAssignment`.

| Field Name           | Type      | Constraints                                        | Description                                                                 |
| -------------------- | --------- | -------------------------------------------------- | --------------------------------------------------------------------------- |
| `assignmentId`       | UUID      | Primary Key, Not Null                              | Unique identifier for the assignment record.                                |
| `personId`           | UUID      | Foreign Key (Person), Not Null                     | The identifier of the assigned employee.                                    |
| `locationId`         | UUID      | Foreign Key (Location), Not Null                   | The identifier of the location for the assignment.                          |
| `role`               | String    | Not Null                                           | The role/function at this location (e.g., MECHANIC, MANAGER, etc.).        |
| `isPrimary`          | Boolean   | Not Null                                           | If true, this is the person's primary work location for the effective period. |
| `effectiveStartAt`   | Timestamp | Not Null, ISO-8601 UTC                             | The moment the assignment becomes active (inclusive).                       |
| `effectiveEndAt`     | Timestamp | Nullable, ISO-8601 UTC                             | The moment the assignment expires (inclusive). Null means ongoing.          |
| `createdAt`          | Timestamp | Not Null, System-managed, ISO-8601 UTC             | Timestamp of record creation.                                               |
| `updatedAt`          | Timestamp | Not Null, System-managed, ISO-8601 UTC             | Timestamp of the last update to the record.                                 |
| `createdBy`          | UUID      | Not Null, Foreign Key (User)                       | The user who created the record.                                            |
| `updatedBy`          | UUID      | Not Null, Foreign Key (User)                       | The user who last updated the record.                                       |
| `changeReasonCode`   | String    | Nullable                                           | Optional code documenting why the assignment was changed (NEW).              |
| `version`            | Integer   | Not Null, >= 1                                     | Version number for optimistic locking and event deduplication (NEW).        |

## Acceptance Criteria

**Scenario 1: Create a person's first and primary location assignment**
- **Given** a `Person` exists with no current assignments
- **When** a Manager creates a new assignment for that `Person` with `isPrimary=true` and a valid `effectiveStartAt`
- **Then** the system successfully creates the `PersonLocationAssignment` record
- **And** a `PersonLocationAssignmentChanged` event is published with event type `PersonLocationAssignmentCreated`.

**Scenario 2: Add a secondary (non-primary) assignment for a person**
- **Given** a `Person` has an existing primary assignment at "Location A"
- **When** a Manager creates a new assignment for that `Person` at "Location B" with `isPrimary=false`
- **Then** the system successfully creates the new non-primary assignment
- **And** the person now has two active assignments
- **And** the primary assignment at "Location A" remains active and primary.

**Scenario 3: Automatic demotion when creating new primary assignment**
- **Given** a `Person` has an active primary assignment at "Location A" (effective 2026-01-01 to null)
- **When** a Manager creates a new assignment for that `Person` at "Location B" with `isPrimary=true` and `effectiveStartAt=2026-06-01`
- **Then** the system automatically sets the old assignment's `effectiveEndAt=2026-05-31T23:59:59Z`
- **And** the new assignment is created with `isPrimary=true`
- **And** both operations complete in a single atomic transaction
- **And** a `PersonLocationAssignmentChanged` event is published showing the demotion + creation.

**Scenario 4: Reject overlapping assignments for same location/role**
- **Given** a `Person` has an assignment to "Location A" (role=MECHANIC) from 2026-01-01 to 2026-12-31
- **When** a Manager attempts to create a new assignment for that `Person` to "Location A" (role=MECHANIC) from 2026-06-01 to 2026-08-01
- **Then** the system rejects the request with a `409 Conflict` or `400 Bad Request` error indicating overlapping assignments are not allowed.

**Scenario 5: An assignment becomes inactive based on its end timestamp**
- **Given** a `Person` has an active assignment with an `effectiveEndAt` of 2026-01-10T23:59:59Z
- **When** the current time is 2026-01-11T00:00:00Z
- **Then** that assignment is not considered active and the person is not eligible for work at that location based on this assignment.

**Scenario 6: Update assignment effective dates (no overlap)**
- **Given** a `Person` has an assignment to "Location A" from 2026-01-01 to 2026-06-30
- **When** a Manager updates the `effectiveEndAt` to 2026-05-31
- **Then** the update succeeds
- **And** the assignment now ends one month earlier
- **And** an audit entry and `PersonLocationAssignmentChanged` event (type: `PersonLocationAssignmentUpdated`) are generated.

## Audit & Observability

- **Audit Log:** Every create and update operation on the `PersonLocationAssignment` entity must generate an audit entry. The entry must contain `assignmentId`, `personId`, the user performing the change, a timestamp, and a representation of the changes made (e.g., JSON diff of old vs. new values).

- **Domain Event (Versioned Strict Schema - NEW):**
  - **Event Topic:** `people.PersonLocationAssignmentChanged.v1` (versioned for evolution)
  - **Key:** `personId` (for ordering/partitioning)
  - **Event Types:**
    - `PersonLocationAssignmentCreated`
    - `PersonLocationAssignmentUpdated`
    - `PersonLocationAssignmentEnded`
  - **Required Envelope:**
    ```json
    {
      "eventId": "UUIDv7",
      "eventType": "PersonLocationAssignmentCreated|Updated|Ended",
      "occurredAt": "2026-01-11T14:00:00Z",
      "producer": "people-service",
      "schemaVersion": 1,
      "payload": { ... }
    }
    ```
  - **Required Payload:**
    ```json
    {
      "assignmentId": "UUIDv7",
      "personId": "UUIDv7",
      "locationId": "UUIDv7",
      "role": "string",
      "isPrimary": true,
      "effectiveStartAt": "2026-01-11T14:00:00Z",
      "effectiveEndAt": null,
      "changeReasonCode": "string|null",
      "changedBy": "UUIDv7",
      "version": 12
    }
    ```

- **Metrics:**
  - `assignment.created` (Counter)
  - `assignment.updated` (Counter)
  - `assignment.primary_demoted` (Counter) (NEW)
  - `assignment.overlap_rejected` (Counter) (NEW)
  - `assignment.active_count` (Gauge)

## Original Story (Unmodified – For Traceability)
# Issue #86 — [BACKEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates

[Original story body preserved as provided in previous issue snapshot]
