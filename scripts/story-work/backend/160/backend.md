Title: [BACKEND] [STORY] Execution: Start Workorder and Track Status
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/160
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---

**Rewrite Variant:** workexec-structured

## Story Intent
**As a** Technician or Shop Manager,
**I want to** formally start a work order and track its granular status changes,
**so that** the shop has an accurate, real-time view of work in progress and can measure operational efficiency.

## Actors & Stakeholders
- **Technician:** The primary actor who performs the work and updates the work order status.
- **Shop Manager:** A secondary actor who oversees shop operations, assigns work, and may also start work orders or track their status.
- **System:** The POS/Shop Management System responsible for enforcing state transitions, validating rules, and recording events.

## Preconditions
1. The user (Technician or Shop Manager) is authenticated and has the necessary permissions to view and modify the work order.
2. A `WorkOrder` entity exists with a unique identifier.
3. The `WorkOrder` is in one of the startable states: `APPROVED`, `ASSIGNED`, or `SCHEDULED`.
4. No active `ChangeRequest` with `status = PENDING_APPROVAL` and `requiresWorkHold = true` is blocking the work order.

## Functional Behavior

### Main Flow: Starting a Work Order

1. The user navigates to a specific, assigned `WorkOrder` that is in a startable state (`APPROVED`, `ASSIGNED`, or `SCHEDULED`).
2. The user initiates a "Start Work" action via `POST /workexec/v1/workorders/{id}:start`.
3. The System validates that:
   - The `WorkOrder` is in one of the three startable states.
   - No blocking `ChangeRequest` exists (see Business Rules below).
4. Upon successful validation, the System performs the following atomic operations:
   - Transitions the `WorkOrder` status to `WORK_IN_PROGRESS`.
   - Sets the `inProgressReason` to `ACTIVE_WORK` (default).
   - Records the current UTC timestamp as `workStartedAt`.
   - Creates an entry in the `WorkOrderStatusHistory` to log this transition.
5. The user can subsequently update the `WorkOrder`'s `inProgressReason` (sub-status) while remaining in `WORK_IN_PROGRESS` (e.g., `WAITING_PARTS`, `WAITING_CUSTOMER_APPROVAL`).
6. Each reason change is validated and recorded in the `WorkOrderStatusHistory`.

### Sub-Status (In-Progress Reason) Tracking

While a work order is in `WORK_IN_PROGRESS`, the system tracks the reason/sub-status via the `inProgressReason` enum field without transitioning the canonical status. Allowed values are:

- `ACTIVE_WORK` (default, work proceeding normally)
- `WAITING_PARTS` (awaiting parts arrival)
- `WAITING_CUSTOMER_APPROVAL` (awaiting customer decision)
- `WAITING_DIAGNOSIS` (awaiting diagnostic results)
- `WAITING_EXTERNAL_SERVICE` (e.g., sublet, vendor work)
- `PAUSED_BY_SHOP` (temporary operational pause)

**Audit Behavior:**
- Transitions into/out of `WORK_IN_PROGRESS` are audited as true status changes.
- Changes to `inProgressReason` are audited as reason changes (separate audit record type).

## Alternate / Error Flows

1. **Attempt to Start Non-Startable Work Order:**
   - **Trigger:** A user attempts to start a `WorkOrder` that is not in `APPROVED`, `ASSIGNED`, or `SCHEDULED` state.
   - **Outcome:** The System rejects the action with error message: "Work order cannot be started. It is currently in '[current_status]' status."

2. **Attempt to Start Work Order with Blocking Change Request:**
   - **Trigger:** A user attempts to start work, but the `WorkOrder` has an active `ChangeRequest` with `status = PENDING_APPROVAL` and `type` in `[SCOPE_CHANGE, PRICE_INCREASE, PART_SUBSTITUTION_REQUIRING_APPROVAL]`.
   - **Outcome:** The System blocks the "Start Work" action with message: "Cannot start work. A pending change request requires approval before proceeding."

3. **Change Reason Within WORK_IN_PROGRESS (Not a State Change):**
   - **Trigger:** Technician changes `inProgressReason` from `ACTIVE_WORK` to `WAITING_PARTS`.
   - **Outcome:** System updates the field without creating a status-change audit record; instead, a reason-change audit record is created.

## Business Rules

1. **Startable States (exhaustive, v1):**
   - A `WorkOrder` can transition to `WORK_IN_PROGRESS` **only** from: `APPROVED`, `ASSIGNED`, `SCHEDULED`.
   - All other states (`DRAFT`, `PENDING_APPROVAL`, `CANCELLED`, `CLOSED`, `INVOICED`, `VOIDED`, `ON_HOLD`, `COMPLETED`, `LOCKED_FOR_REVIEW`, `ARCHIVED`) are **not startable**.

2. **Status Irreversibility:**
   - Once a `WorkOrder` is in `WORK_IN_PROGRESS`, it cannot revert to a pre-work status like `APPROVED` or `ASSIGNED`.

3. **Blocking Change Requests:**
   - If a `ChangeRequest` linked to the `WorkOrder` exists where:
     - `status = PENDING_APPROVAL`
     - `type` is in `[SCOPE_CHANGE, PRICE_INCREASE, PART_SUBSTITUTION_REQUIRING_APPROVAL]` **or** `requiresWorkHold = true`
   - Then the work order **cannot be started** until the change request is resolved (approved or rejected).

4. **In-Progress Reason (Sub-Status):**
   - The `inProgressReason` field is **only valid** while `status = WORK_IN_PROGRESS`.
   - Changes to `inProgressReason` do not alter the canonical status; they are audited separately.

5. **Immutable History:**
   - All status transitions and reason changes must be recorded in an immutable `WorkOrderStatusHistory` log.

6. **Auto-Start is Out of Scope:**
   - This story implements **explicit start only** via API call.
   - Auto-start on first labor entry is a **separate follow-on story** and is not addressed here.

## Data Requirements

### WorkOrder Entity (additions)
- `id` (UUID, PK)
- `statusId` (ENUM/String, e.g., `WORK_IN_PROGRESS`)
- `inProgressReason` (ENUM, nullable, allowed values: `ACTIVE_WORK`, `WAITING_PARTS`, `WAITING_CUSTOMER_APPROVAL`, `WAITING_DIAGNOSIS`, `WAITING_EXTERNAL_SERVICE`, `PAUSED_BY_SHOP`)
- `workStartedAt` (TimestampZ, nullable)

### WorkOrderStatusHistory Entity
- `id` (UUID, PK)
- `workOrderId` (UUID, FK to `WorkOrder`, indexed)
- `fromStatusId` (ENUM/String, nullable for initial creation)
- `toStatusId` (ENUM/String, not null)
- `reason` (String, optional user-provided reason)
- `changedByUserId` (UUID, the user who initiated the change)
- `changedAtTimestamp` (TimestampZ, UTC)
- `changeType` (ENUM: `STATUS_CHANGE` or `REASON_CHANGE`, for audit distinction)

### ChangeRequest Entity (referenced)
- `id` (UUID, PK)
- `workOrderId` (UUID, FK to `WorkOrder`, indexed)
- `status` (ENUM, e.g., `PENDING_APPROVAL`, `APPROVED`, `REJECTED`)
- `type` (ENUM, e.g., `SCOPE_CHANGE`, `PRICE_INCREASE`, `PART_SUBSTITUTION_REQUIRING_APPROVAL`)
- `requiresWorkHold` (Boolean, derived from type or explicit flag)

## Acceptance Criteria

### AC1: Successfully Start a Work Order from APPROVED State
- **Given** a work order exists in an `APPROVED` state
- **And** I am an authenticated Technician
- **When** I trigger `POST /workexec/v1/workorders/{id}:start`
- **Then** the work order's status must transition to `WORK_IN_PROGRESS`
- **And** the `inProgressReason` must be set to `ACTIVE_WORK`
- **And** the `workStartedAt` field must be populated with the current UTC time
- **And** a new record must be created in `WorkOrderStatusHistory` with `changeType = STATUS_CHANGE`, documenting the change from `APPROVED` to `WORK_IN_PROGRESS`.

### AC2: Start from ASSIGNED State
- **Given** a work order exists in an `ASSIGNED` state
- **And** I am an authenticated Technician
- **When** I trigger the start action
- **Then** the transition succeeds with the same outcomes as AC1.

### AC3: Start from SCHEDULED State
- **Given** a work order exists in a `SCHEDULED` state
- **When** I trigger the start action
- **Then** the transition succeeds with the same outcomes as AC1.

### AC4: Reject Start from Non-Startable State
- **Given** a work order exists in a `COMPLETED` state
- **And** I am an authenticated Technician
- **When** I attempt to trigger the start action
- **Then** the system must reject the request with error message: "Work order cannot be started. It is currently in 'COMPLETED' status."
- **And** the work order's status must remain `COMPLETED`
- **And** no audit record is created.

### AC5: Block Start Due to Pending Change Request
- **Given** a work order exists in an `APPROVED` state
- **And** an active `ChangeRequest` exists where `status = PENDING_APPROVAL` and `type = SCOPE_CHANGE`
- **And** I am an authenticated Technician
- **When** I attempt to trigger the start action
- **Then** the system must reject the request with error message: "Cannot start work. A pending change request requires approval before proceeding."
- **And** the work order's status must remain `APPROVED`
- **And** no audit record is created.

### AC6: Change In-Progress Reason
- **Given** a work order is in `WORK_IN_PROGRESS` with `inProgressReason = ACTIVE_WORK`
- **When** I update the reason to `WAITING_PARTS`
- **Then** the `inProgressReason` field is updated
- **And** the canonical status remains `WORK_IN_PROGRESS`
- **And** a new record is created in `WorkOrderStatusHistory` with `changeType = REASON_CHANGE` documenting the reason change
- **And** no status-change audit record is created.

### AC7: Validate Allowed In-Progress Reasons
- **Given** a work order is in `WORK_IN_PROGRESS`
- **When** an attempt is made to set `inProgressReason` to an invalid value (not in the allowed enum)
- **Then** the system rejects the update with a validation error
- **And** the current `inProgressReason` is unchanged.

## Audit & Observability
- **Logging:**
  - `INFO`: Log successful work order start, including `workOrderId`, `workStartedAt`, and `changedByUserId`.
  - `INFO`: Log successful reason changes, including the old and new `inProgressReason`.
  - `WARN`: Log rejections due to non-startable state or blocking change request.
- **Metrics:**
  - `workexec.workorder.start.success.count`: Counter, incremented on successful start.
  - `workexec.workorder.start.failure.count`: Counter, incremented on start rejection (by reason: non_startable_state, blocking_change_request).
  - `workexec.workorder.reason_change.count`: Counter, incremented on each reason change.
- **Alerting:**
  - Monitor `workexec.workorder.start.failure.count` for systemic issues.

## Clarification Resolution

### 1. Auto-Start Behavior
**Decision:** **Out of scope for this story.** Treat "auto-start on first labor entry" as a **separate follow-on story**.

**Rationale:**
- Auto-start changes the trigger model (implicit vs. explicit) and introduces coupling with timekeeping/labor capture systems.
- It complicates auditability and operator intent (work order start becomes a side effect rather than an explicit action).

**This story's scope:** Explicit start via `POST /workexec/v1/workorders/{id}:start` only.

### 2. Startable States (Exhaustive List)
**Decision:** A work order is startable from exactly three states (v1):
- `APPROVED`
- `ASSIGNED`
- `SCHEDULED`

**Not startable:**
- `DRAFT`, `PENDING_APPROVAL`, `CANCELLED`, `CLOSED`, `INVOICED`, `VOIDED`, `ON_HOLD`, `COMPLETED`, `LOCKED_FOR_REVIEW`, `ARCHIVED`

**Rule:** Start transitions the work order to `WORK_IN_PROGRESS` and sets `workStartedAt` to the current UTC timestamp.

### 3. In-Progress Sub-Statuses
**Decision:** Use a **single primary status** `WORK_IN_PROGRESS` plus a constrained enum `inProgressReason` (sub-status) to avoid state explosion.

**Allowed `inProgressReason` values (v1):**
- `ACTIVE_WORK` (default)
- `WAITING_PARTS`
- `WAITING_CUSTOMER_APPROVAL`
- `WAITING_DIAGNOSIS`
- `WAITING_EXTERNAL_SERVICE` (e.g., sublet/vendor)
- `PAUSED_BY_SHOP` (temporary operational pause)

**Rules:**
- `WORK_IN_PROGRESS` remains the canonical status.
- `inProgressReason` may change without leaving WIP.
- Transitions into/out of `WORK_IN_PROGRESS` are audited as true status changes; changes to `inProgressReason` are audited separately as reason changes.

### 4. Pending Approval Change Request Identification
**Decision:** Yes—a **ChangeRequest entity linked to the WorkOrder** is used to evaluate this as a blocking condition.

**Relationship:**
- `WorkOrder (1) -> (0..*) ChangeRequest`
- Link: `changeRequest.workOrderId`

**Blocking Condition:**
Block start if there exists an active ChangeRequest where:
- `status = PENDING_APPROVAL`
- AND `type` is in the set that must be approved before work begins: `[SCOPE_CHANGE, PRICE_INCREASE, PART_SUBSTITUTION_REQUIRING_APPROVAL]`

**Query Logic (Deterministic):**
```
exists ChangeRequest where 
  workOrderId = :id 
  AND status = PENDING_APPROVAL 
  AND (type IN (...) OR requiresWorkHold = true)
```

**Status:** All clarifications resolved; story is unblocked and ready for implementation.

## Original Story (Unmodified – For Traceability)
# Issue #160 — [BACKEND] [STORY] Execution: Start Workorder and Track Status

## Current Labels
- backend
- blocked:clarification
- domain:workexec
- status:draft
- story-implementation
- type:story
- user

## Original Scope
This story defines the mechanism for formally starting a work order and tracking granular status changes during the work-in-progress phase, ensuring accurate operational visibility and audit compliance.
