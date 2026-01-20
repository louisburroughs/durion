Title: [BACKEND] [STORY] Users: Disable User (Offboarding) Without Losing History
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/90
Labels: type:story, domain:people, status:ready-for-dev

## 🏷️ Labels (Final)
### Required
- type:story
- domain:people
- status:ready-for-dev

### Recommended
- agent:people
- agent:story-authoring

**Rewrite Variant:** crm-pragmatic

## Story Intent
**As an** Admin,
**I want to** deactivate a user's account,
**so that** their system access is immediately revoked while ensuring all their historical data, such as completed work and time entries, is preserved for reporting, payroll, and audit purposes.

## Actors & Stakeholders
- **Admin**: The user performing the deactivation.
- **System (People Service)**: The primary service and system of record for the user/person lifecycle state.
- **System (Security Service)**: A downstream service that consumes the deactivation event to block authentication.
- **System (Work Execution Service)**: A downstream service that must react to the deactivation by stopping active timers and excluding the user from future work assignments.
- **System (Scheduling Service)**: A downstream service that must exclude the deactivated user from being added to future schedules.

## Preconditions
1. The user account to be deactivated exists in the system and is in an `ACTIVE` state.
2. The Admin is authenticated and possesses the necessary permissions (e.g., `user.disable`) to perform this action.

## Functional Behavior
1. The Admin navigates to the user management interface and selects the target user account.
2. The Admin initiates the "Disable User" action.
3. The system presents a confirmation dialog to prevent accidental deactivation.
4. **(Optional, if policy allows):** The Admin is presented with assignment termination options:
   - `END_ASSIGNMENTS_NOW` (default, always available)
   - `END_ASSIGNMENTS_AT_DATE` (schedule end, if policy allows)
   - `LEAVE_ASSIGNMENTS_ACTIVE` (requires elevated permission and policy configuration)
5. Upon confirmation, the **People Service** orchestrates the deactivation process:
   a. Atomically updates the `User` entity's status to `DISABLED` and the `Person` entity's status to `DISABLED`.
   b. Sets `statusEffectiveAt` to current timestamp and records optional `statusReasonCode`.
   c. Based on policy and admin selection, issues commands to terminate or schedule termination of active location/role assignments.
   d. Issues a command to the **Work Execution Service** to find and terminate any active `TimeEntry` records associated with the user, calculating the final duration.
   e. Emits a `user.disabled` event to an enterprise message bus for downstream consumers.
6. The **Security Service**, subscribed to the `user.disabled` event, updates its internal records to **immediately** block any subsequent authentication attempts for the disabled user.
7. The **Work Execution** and **Scheduling** services, upon receiving the event or on their next lookup, will exclude this user from any lists of available personnel for future assignments or schedules.
8. If any downstream command fails (e.g., stop timer), the failure is logged and the command enters a **saga retry queue** with exponential backoff (1m, 5m, 15m, 1h, 6h). After 24 hours, unresolved commands move to a Dead Letter Queue (DLQ) for manual intervention.

## Alternate / Error Flows
- **Error: User Not Found**: If the specified user ID does not exist, the system displays a "User not found" error.
- **Error: User Already Disabled**: If the user is already in a `DISABLED` or `TERMINATED` state, the system displays a message indicating "User is already disabled" and takes no further action.
- **Error: Insufficient Permissions**: If the Admin does not have the required permissions, the action is blocked, and an "Access Denied" error is shown.
- **Error: Downstream Service Failure**: If a downstream service (e.g., Work Execution Service) fails to process a command (e.g., stop timer), the failure is logged, and the command is placed in a retry queue with bounded retry policy (max 24 hours). The user deactivation in the People Service is **not** rolled back to ensure the primary goal of revoking access is met. **Primary guarantee:** Authentication is blocked immediately regardless of downstream lag.

## Business Rules
1. A disabled user **MUST NOT** be able to authenticate with the system via any method.
2. The `Person` record and all associated historical records (e.g., completed `TimeEntry`, labor records on `WorkOrder`) **MUST** be preserved and remain associated with the disabled person.
3. Deactivation is a logical "soft delete." No user or person data is physically removed from the database as part of this process.
4. All active work timers and sessions for the user **MUST** be forcibly terminated at the moment of deactivation (or queued for eventual termination via saga).
5. Disabled users **MUST NOT** appear in user pickers or lists for assigning future work or creating new schedules.
6. **Status Lifecycle (Authoritative):**
   - **`ACTIVE`** — can authenticate and be assigned work
   - **`DISABLED`** — cannot authenticate; identity retained; reversible offboarding
   - **`TERMINATED`** — employment ended; cannot authenticate; irreversible
   - This story sets status to `DISABLED`.
7. **Assignment Termination Policy:**
   - **Default behavior:** End all active staffing/location assignments immediately on disable.
   - **Optional exceptions:** Configurable via policy for specific `disableReason` values (e.g., `LEAVE_OF_ABSENCE`, `TEMP_SUSPENSION`).
   - **Configuration:**
     - `endAssignmentsOnDisable = true` (default)
     - `allowKeepAssignmentsOnDisable = false` (default)
     - `allowedDisableReasonsForKeepAssignments = []` (default empty)

## Data Requirements
- **`User` Entity**:
  - Requires a `status` field: `ENUM('ACTIVE', 'DISABLED', 'TERMINATED')`.
  - `statusEffectiveAt` (timestamp)
  - `statusReasonCode` (optional enum/string)
  - Foreign key relationship to `Person`.
- **`Person` Entity**:
  - Requires a `status` field: `ENUM('ACTIVE', 'DISABLED', 'TERMINATED')`.
  - `statusEffectiveAt` (timestamp)
  - `statusReasonCode` (optional enum/string)
- **`TimeEntry` Entity**:
  - Must be queryable by `userId` and `status` to find active entries.
  - Requires a `forcedStopReason` field to indicate system-initiated termination.
- **`Assignment` Entity**:
  - Must have a status or end-date that can be updated to terminate the assignment.
- **`AuditLog`**:
  - Table to store records of significant system events, including user deactivation.
- **Saga/Retry Infrastructure**:
  - Retry queue with exponential backoff configuration
  - Dead Letter Queue (DLQ) for failed commands after 24h
  - Manual intervention workflow UI/API

## Acceptance Criteria
**AC1: Successful Deactivation Revokes Access**
- **Given** an active user "jane.doe" exists in the system.
- **When** an Admin deactivates the "jane.doe" account.
- **Then** the `User` record for "jane.doe" has its status set to `DISABLED`.
- **And** the related `Person` record has its status set to `DISABLED`.
- **And** `statusEffectiveAt` is set to the deactivation timestamp.

**AC2: Disabled User Cannot Authenticate**
- **Given** the user "jane.doe" has been deactivated.
- **When** an authentication attempt is made with "jane.doe"'s credentials.
- **Then** the Security Service rejects the authentication request with a "User account is disabled" error.
- **And** authentication is blocked **immediately**, even if downstream saga steps are still pending.

**AC3: Active Job Timers are Forcibly Stopped**
- **Given** user "jane.doe" is active and has a running job timer (`TimeEntry` record with `endTime` = NULL).
- **When** an Admin deactivates the "jane.doe" account.
- **Then** a command is issued to stop the active `TimeEntry`.
- **And** if successful, the `TimeEntry` record is updated with an `endTime` equal to the deactivation timestamp and flagged as a forced stop.
- **And** if the command fails, it enters the saga retry queue with exponential backoff.

**AC4: Assignment Termination Follows Policy**
- **Given** `endAssignmentsOnDisable = true` (default).
- **When** an Admin deactivates a user.
- **Then** all active location/role assignments for that user are ended immediately (or scheduled based on admin selection if policy allows).
- **And** if policy allows keeping assignments (`allowKeepAssignmentsOnDisable = true` and reason matches `allowedDisableReasonsForKeepAssignments`), the Admin may choose to keep assignments active.

**AC5: Deactivation is Audited**
- **Given** any user deactivation attempt.
- **When** the deactivation action is confirmed by the Admin.
- **Then** a new record is created in the audit log containing the `timestamp`, `actorId` (the Admin), `targetUserId`, `targetPersonId`, `statusReasonCode`, and the event type `user.disabled`.

**AC6: Disabled Users Excluded from Future Work**
- **Given** user "jane.doe" has been deactivated.
- **When** a Service Advisor attempts to assign a new work order.
- **Then** "jane.doe" does not appear in the list of available technicians.

**AC7: Downstream Failure Handling (Saga)**
- **Given** a downstream command (e.g., stop timer) fails.
- **When** the retry window (24 hours) expires without success.
- **Then** the command is moved to the Dead Letter Queue (DLQ) with status `REQUIRES_MANUAL_INTERVENTION`.
- **And** an alert is triggered for operators.

**AC8: Manual Intervention Workflow**
- **Given** a command in the DLQ.
- **When** an operator views the offboarding case.
- **Then** the operator can:
  - Re-run the failed command(s)
  - Mark as "accepted risk" with audited justification
  - Export details for incident tracking

## Audit & Observability
- **Audit Event**: A `user.disabled` event must be logged to the central audit trail.
  - **Payload**: `{ "timestamp": "...", "actor": { "type": "Admin", "id": "admin123" }, "subject": { "type": "User", "id": "user456", "personId": "person789", "status": "DISABLED", "statusReasonCode": "..." }, "outcome": "success" }`
- **Metrics**:
  - `user.deactivation.success` (Counter): Incremented on successful deactivation.
  - `user.deactivation.failure` (Counter): Incremented on any failed attempt (e.g., permissions, downstream failure).
  - `user.deactivation.saga.retry` (Counter): Incremented when downstream commands enter retry queue.
  - `user.deactivation.saga.dlq` (Counter): Incremented when commands move to DLQ.
  - `user.deactivation.saga.queue_depth` (Gauge): Current depth of retry queue.
  - `user.deactivation.saga.oldest_retry_age` (Gauge): Age of oldest retry in queue.
- **Logging**:
  - INFO level log on initiation and successful completion.
  - WARN level log if a downstream command (e.g., stop timer) fails and is queued for retry.
  - ERROR level log if the core deactivation in the People Service fails.
  - CRITICAL level log when commands move to DLQ.
- **Alerting**:
  - Alert when retry queue depth > 100 (default threshold)
  - Alert when oldest retry age > 30 minutes (default threshold)
  - Alert on any DLQ message of type `OFFBOARDING_CRITICAL`

## Original Story (Unmodified – For Traceability)
# Issue #90 — [BACKEND] [STORY] Users: Disable User (Offboarding) Without Losing History

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Users: Disable User (Offboarding) Without Losing History

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to disable a user account** so that **access is removed while historical labor and timekeeping records remain intact**.

## Details
- Disable login in pos-security-service.
- Optionally end active location assignments.
- Force-stop any active job timers.

## Acceptance Criteria
- Disabled users cannot authenticate.
- Person record retained and marked inactive (policy-driven).
- All forced stops and changes are audited.

## Integration Points (workexec/shopmgr)
- workexec excludes disabled users from assignment.
- shopmgr excludes disabled users from future schedules.

## Data / Entities
- User
- Person
- Assignment
- TimeEntry

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
