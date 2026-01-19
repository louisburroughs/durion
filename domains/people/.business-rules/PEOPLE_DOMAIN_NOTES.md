# PEOPLE_DOMAIN_NOTES.md

## Summary

This document provides non-normative, verbose rationale and decision logs for the People domain within the Durion POS system. It supports auditors, architects, and engineers by documenting design choices around user lifecycle management, employee profiles, role assignments, location assignments, timekeeping, and cross-domain integration patterns. Each decision includes alternatives considered, architectural implications, and migration guidance.

## Completed items

- [x] Linked each Decision ID to a detailed rationale
- [x] Documented alternatives considered for user lifecycle and timekeeping
- [x] Provided architectural implications for role-based access and assignments
- [x] Included auditor-facing explanations with example queries
- [x] Documented cross-domain integration patterns

## Decision details

## DECISION-PEOPLE-001 - User lifecycle states and soft offboarding

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-001)
- **Decision:** User lifecycle includes three states: `ACTIVE` (can authenticate and be assigned), `DISABLED` (cannot authenticate but identity retained, reversible), and `TERMINATED` (employment ended, cannot authenticate, irreversible). Disabling a user is a logical soft delete with no physical data removal. Authentication is blocked immediately upon status change.
- **Alternatives considered:**
  - **Option A (Chosen):** Three-state lifecycle with DISABLED as reversible soft delete
    - Pros: Supports temporary offboarding, preserves historical data, clear semantics, audit compliance
    - Cons: Requires careful handling of DISABLED state across all systems
  - **Option B:** Two-state model (ACTIVE/TERMINATED only)
    - Pros: Simpler state machine
    - Cons: Cannot support temporary leave or reversible offboarding, forces permanent deletion or workarounds
  - **Option C:** Physical deletion on termination
    - Pros: Clean data removal
    - Cons: Loses audit trail, breaks historical references, violates compliance requirements
- **Reasoning and evidence:**
  - Employment scenarios require temporary access suspension (leave, suspension, investigation)
  - Audit and compliance requirements mandate preserving historical user actions
  - Payroll and work history must reference terminated employees indefinitely
  - Reversible offboarding supports rehires and correcting administrative errors
  - Authentication must be blocked immediately without waiting for downstream propagation
  - Industry standard pattern for enterprise identity management
- **Architectural implications:**
  - **Components affected:**
    - User service: Manages user status transitions
    - Authentication service: Checks user status before allowing login
    - Assignment services: Filter out DISABLED/TERMINATED users from pickers
    - Scheduling services: Exclude DISABLED/TERMINATED users from assignment
  - **State machine:**
    ```
    ACTIVE → DISABLED (reversible)
    ACTIVE → TERMINATED (irreversible)
    DISABLED → ACTIVE (reactivation)
    DISABLED → TERMINATED (finalize offboarding)
    TERMINATED → [no transitions allowed]
    ```
  - **Database schema:**
    ```sql
    CREATE TYPE user_status AS ENUM ('ACTIVE', 'DISABLED', 'TERMINATED');
    
    ALTER TABLE "user" ADD COLUMN status user_status NOT NULL DEFAULT 'ACTIVE';
    ALTER TABLE person ADD COLUMN employment_status user_status NOT NULL DEFAULT 'ACTIVE';
    
    CREATE INDEX idx_user_active ON "user"(id) WHERE status = 'ACTIVE';
    ```
  - **Authentication check:**
    ```java
    public boolean canAuthenticate(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }
    ```
  - **UI filtering:**
    - Assignment pickers: `WHERE status = 'ACTIVE'`
    - Historical views: Include all statuses with clear status indicators
    - Admin views: Show all statuses with appropriate actions
- **Auditor-facing explanation:**
  - **What to inspect:** Verify user status transitions follow rules and authentication is blocked for DISABLED/TERMINATED
  - **Query example:**
    ```sql
    -- Find authentication attempts by disabled/terminated users
    SELECT al.user_id, u.status, al.attempted_at, al.outcome
    FROM auth_log al
    JOIN "user" u ON u.id = al.user_id
    WHERE u.status IN ('DISABLED', 'TERMINATED')
      AND al.attempted_at > u.status_updated_at
      AND al.outcome = 'SUCCESS'; -- should be zero
    
    -- Find work assignments to disabled/terminated users after status change
    SELECT wa.id, wa.user_id, u.status, wa.assigned_at
    FROM work_assignment wa
    JOIN "user" u ON u.id = wa.user_id
    WHERE u.status IN ('DISABLED', 'TERMINATED')
      AND wa.assigned_at > u.status_updated_at;
    ```
  - **Expected outcome:** Zero successful authentications or new assignments after status change
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add status columns to user and person tables (nullable initially)
    2. Backfill existing users as ACTIVE
    3. Identify historically terminated users and mark as TERMINATED
    4. Deploy authentication checks
    5. Deploy assignment picker filters
    6. Make status column NOT NULL
  - **Historical data considerations:**
    - Users with no recent activity (> 2 years) may need status review
    - Coordinate with HR to identify terminated employees for backfill
  - **Communication:**
    - Notify users that DISABLED is temporary and reversible
    - Document reactivation process for HR/admin
- **Governance & owner recommendations:**
  - **Owner:** People domain team with coordination from Security
  - **Review cadence:** Annual review of DISABLED users for cleanup or reactivation
  - **Policy:** Define retention period for TERMINATED user data (e.g., 7 years)
  - **Monitoring:** Alert on DISABLED users attempting authentication (potential security issue)

## DECISION-PEOPLE-002 - User disable workflow with saga pattern

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-002)
- **Decision:** Disabling a user follows an atomic local update followed by asynchronous downstream propagation via saga pattern. User status changes immediately (blocking authentication), then downstream systems (assignment termination, timer stops) are notified via events with retry logic. Downstream failures do not rollback the disable; they are retried with exponential backoff and routed to Dead Letter Queue (DLQ) after 24 hours.
- **Alternatives considered:**
  - **Option A (Chosen):** Atomic local update + async saga with retries
    - Pros: Immediate authentication block, resilient to downstream failures, eventually consistent
    - Cons: Temporary inconsistency in downstream systems
  - **Option B:** Synchronous two-phase commit across all systems
    - Pros: Immediate consistency
    - Cons: Slow, brittle (any system down blocks disable), poor user experience
  - **Option C:** Manual downstream cleanup after disable
    - Pros: Simple implementation
    - Cons: Human error risk, slow, inconsistent outcomes
- **Reasoning and evidence:**
  - Authentication block must be immediate for security (terminated employees lose access instantly)
  - Downstream operations (ending assignments, stopping timers) are lower priority than access revocation
  - Network partitions and system outages are inevitable; disable must succeed despite them
  - Saga pattern is proven for distributed transactions with compensation logic
  - Retry with backoff handles transient failures without manual intervention
  - DLQ provides visibility for operations team to handle persistent failures
- **Architectural implications:**
  - **Components affected:**
    - User service: Executes disable command and initiates saga
    - Event bus: Publishes user.disabled event
    - Assignment service: Subscribes to event, ends active assignments
    - Timekeeping service: Subscribes to event, stops active timers
    - Saga orchestrator: Manages retries and DLQ routing
  - **Workflow:**
    ```
    1. API call: POST /users/{userId}/disable
    2. User service:
       a. Validate permission and user exists
       b. UPDATE user SET status='DISABLED' WHERE id=userId
       c. UPDATE person SET employment_status='DISABLED' WHERE user_id=userId
       d. INSERT disable_audit_record
       e. PUBLISH event: user.disabled(userId, timestamp, reason)
    3. Return 200 OK (disable complete, downstream async)
    4. Downstream subscribers receive event:
       - Assignment service: END active assignments
       - Timekeeping service: STOP active timers
    5. If downstream fails:
       - Saga orchestrator retries with exponential backoff
       - After 24h of failures → route to DLQ
       - Operations team alerted to resolve manually
    ```
  - **Event schema:**
    ```json
    {
      "eventType": "user.disabled",
      "userId": "usr-12345",
      "personId": "per-67890",
      "disabledAt": "2026-01-19T16:30:00Z",
      "disabledBy": "admin-001",
      "reason": "TERMINATION",
      "correlationId": "corr-abc123"
    }
    ```
  - **Retry configuration:**
    ```yaml
    retry_policy:
      initial_delay: 5s
      max_delay: 300s
      multiplier: 2.0
      max_attempts: 10
      timeout: 24h
      dlq_topic: user-disable-failures
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify disable operations complete locally and downstream propagation eventually succeeds or routes to DLQ
  - **Query example:**
    ```sql
    -- Find disabled users with active assignments (temporary inconsistency OK if recent)
    SELECT u.id, u.status, u.status_updated_at, 
           COUNT(wa.id) as active_assignments,
           NOW() - u.status_updated_at as time_since_disable
    FROM "user" u
    LEFT JOIN work_assignment wa ON wa.user_id = u.id AND wa.status = 'ACTIVE'
    WHERE u.status = 'DISABLED'
    GROUP BY u.id
    HAVING COUNT(wa.id) > 0
      AND NOW() - u.status_updated_at > INTERVAL '1 hour'; -- alert if inconsistent >1hr
    
    -- DLQ entries for disable operations
    SELECT * FROM dead_letter_queue
    WHERE topic = 'user-disable-failures'
      AND created_at > NOW() - INTERVAL '7 days'
    ORDER BY created_at DESC;
    ```
  - **Expected outcome:**
    - Recent disables (< 1 hour) may have active assignments (propagation in progress)
    - Old disables (> 1 hour) should have zero active assignments
    - DLQ entries should be investigated and resolved
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Implement saga orchestrator and retry logic
    2. Deploy event subscribers to downstream systems
    3. Update user disable API to publish events
    4. Set up DLQ monitoring and alerting
    5. Train operations team on DLQ resolution procedures
  - **Rollback strategy:**
    - Feature flag to switch between synchronous and async modes
    - DLQ can be drained and retried after fixes
  - **Testing:**
    - Simulate downstream failures (circuit breaker test)
    - Verify retries with exponential backoff
    - Verify DLQ routing after retry exhaustion
- **Governance & owner recommendations:**
  - **Owner:** People domain team with SRE for saga orchestration
  - **Monitoring:** Alert on DLQ growth, high retry rates, or downstream latency spikes
  - **SLA:** DLQ items should be resolved within 24 hours of creation
  - **Review cadence:** Monthly review of DLQ patterns and downstream reliability

## DECISION-PEOPLE-003 - Role assignment scopes (GLOBAL vs LOCATION)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-003)
- **Decision:** Role assignments support two scopes: `GLOBAL` (applies across all locations) and `LOCATION` (applies only to specific location). Role definitions declare allowed scopes. A role assignment must specify scope; LOCATION-scoped assignments require a location reference. Multiple location-scoped assignments for the same role are allowed. UI displays effective permissions as the union of all assignments.
- **Alternatives considered:**
  - **Option A (Chosen):** Explicit GLOBAL/LOCATION scopes with role-level constraints
    - Pros: Clear semantics, flexible, supports multi-location organizations, explicit modeling
    - Cons: More complex than single-scope model
  - **Option B:** GLOBAL scope only
    - Pros: Simplest model
    - Cons: Cannot limit permissions to specific locations, over-privileges users
  - **Option C:** Implicit scope (inferred from location assignment)
    - Pros: Fewer tables/fields
    - Cons: Ambiguous, hard to query, cannot represent "global admin at specific location"
- **Reasoning and evidence:**
  - Multi-location organizations need location-specific permissions (e.g., "Manager at Location A but not B")
  - Some roles are inherently global (e.g., system admin, corporate HR)
  - Explicit scope declaration prevents misconfiguration and privilege escalation
  - Union semantics (sum of all assignments) simplifies permission checks
  - Industry standard pattern (e.g., AWS IAM scopes, Azure RBAC scopes)
- **Architectural implications:**
  - **Components affected:**
    - Role service: Defines roles and allowed scopes
    - Role assignment service: Creates assignments with scope validation
    - Permission check service: Evaluates effective permissions from all assignments
    - UI: Shows effective permissions and allows creating scoped assignments
  - **Database schema:**
    ```sql
    CREATE TYPE assignment_scope AS ENUM ('GLOBAL', 'LOCATION');
    
    CREATE TABLE role (
      id UUID PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      allowed_scopes assignment_scope[] NOT NULL DEFAULT '{GLOBAL, LOCATION}'
    );
    
    CREATE TABLE role_assignment (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES "user"(id),
      role_id UUID NOT NULL REFERENCES role(id),
      scope assignment_scope NOT NULL,
      location_id UUID REFERENCES location(id),
      effective_start_at TIMESTAMPTZ NOT NULL,
      effective_end_at TIMESTAMPTZ,
      CHECK (scope = 'LOCATION' IMPLIES location_id IS NOT NULL),
      CHECK (scope = 'GLOBAL' IMPLIES location_id IS NULL)
    );
    
    CREATE INDEX idx_role_assignment_user ON role_assignment(user_id, effective_start_at, effective_end_at);
    ```
  - **Permission check logic:**
    ```java
    public boolean hasPermission(User user, String permission, Location location) {
        LocalDateTime now = LocalDateTime.now();
        
        List<RoleAssignment> assignments = roleAssignmentRepo.findActiveByUser(user, now);
        
        for (RoleAssignment assignment : assignments) {
            Role role = assignment.getRole();
            
            // Check if role grants the permission
            if (!role.getPermissions().contains(permission)) {
                continue;
            }
            
            // Check scope
            if (assignment.getScope() == Scope.GLOBAL) {
                return true; // Global scope applies everywhere
            }
            
            if (assignment.getScope() == Scope.LOCATION 
                && assignment.getLocationId().equals(location.getId())) {
                return true; // Location scope matches
            }
        }
        
        return false; // No matching assignment
    }
    ```
  - **UI assignment creation:**
    - User selects role from picker
    - If role allows GLOBAL scope: show GLOBAL radio button
    - If role allows LOCATION scope: show LOCATION radio button + location picker
    - Validate scope is allowed for selected role
- **Auditor-facing explanation:**
  - **What to inspect:** Verify role assignments have correct scopes and location references
  - **Query example:**
    ```sql
    -- Find LOCATION-scoped assignments without location reference (invalid)
    SELECT id, user_id, role_id, scope, location_id
    FROM role_assignment
    WHERE scope = 'LOCATION' AND location_id IS NULL;
    
    -- Find GLOBAL-scoped assignments with location reference (invalid)
    SELECT id, user_id, role_id, scope, location_id
    FROM role_assignment
    WHERE scope = 'GLOBAL' AND location_id IS NOT NULL;
    
    -- Find assignments with scope not allowed by role
    SELECT ra.id, ra.user_id, r.name, ra.scope, r.allowed_scopes
    FROM role_assignment ra
    JOIN role r ON r.id = ra.role_id
    WHERE NOT (ra.scope = ANY(r.allowed_scopes));
    ```
  - **Expected outcome:** Zero invalid assignments
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add scope and allowed_scopes columns (nullable initially)
    2. Identify existing assignments and determine appropriate scope
    3. Backfill scope based on legacy patterns
    4. Update role definitions with allowed_scopes
    5. Deploy scope validation
    6. Make scope NOT NULL
  - **Backfill strategy:**
    ```sql
    -- Default existing assignments to GLOBAL (conservative)
    UPDATE role_assignment SET scope = 'GLOBAL' WHERE scope IS NULL;
    
    -- Update roles to allow both scopes by default
    UPDATE role SET allowed_scopes = '{GLOBAL, LOCATION}' WHERE allowed_scopes IS NULL;
    ```
  - **Communication:**
    - Notify admins of new scoping capability
    - Provide training on creating LOCATION-scoped assignments
- **Governance & owner recommendations:**
  - **Owner:** People domain team with Security team oversight
  - **Review cadence:** Quarterly audit of role assignments for least privilege
  - **Policy:** Prefer LOCATION scope for operational roles, GLOBAL only for admin/corporate
  - **Documentation:** Maintain catalog of roles with recommended scopes

## DECISION-PEOPLE-004 - Person-location assignment primary flag semantics

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-004)
- **Decision:** Person-location assignments include a `primary` boolean flag. At most one assignment per person can be primary at any given point in time (within effective date range). When a new assignment is created with `primary=true`, the backend automatically demotes (sets `primary=false`) any existing primary assignment. UI displays primary status and reflects backend-driven demotion after creation.
- **Alternatives considered:**
  - **Option A (Chosen):** Backend-enforced uniqueness with automatic demotion
    - Pros: Data integrity guaranteed, no race conditions, clear semantics
    - Cons: UI must refresh to see demotion effects
  - **Option B:** UI-enforced uniqueness (validate before submit)
    - Pros: Immediate feedback
    - Cons: Race condition risk, complex client logic, not foolproof
  - **Option C:** Allow multiple primary assignments
    - Pros: Simpler model
    - Cons: Ambiguous "home location" concept, breaks downstream assumptions
- **Reasoning and evidence:**
  - Many business rules require a single "home location" per person (payroll, scheduling defaults)
  - Primary assignment is used for default routing and dispatch eligibility
  - Concurrent assignment creation could violate uniqueness without backend enforcement
  - Automatic demotion prevents manual cleanup burden on users
  - Industry pattern: "active/primary" flags with automatic promotion/demotion
- **Architectural implications:**
  - **Components affected:**
    - PersonLocationAssignment service: Enforces primary uniqueness
    - Database: Partial unique index on (person_id, primary) where primary=true
    - UI: Shows primary flag and refreshes after mutation
  - **Database schema:**
    ```sql
    CREATE TABLE person_location_assignment (
      id UUID PRIMARY KEY,
      person_id UUID NOT NULL REFERENCES person(id),
      location_id UUID NOT NULL REFERENCES location(id),
      role VARCHAR(50),
      "primary" BOOLEAN NOT NULL DEFAULT false,
      effective_start_at TIMESTAMPTZ NOT NULL,
      effective_end_at TIMESTAMPTZ,
      CHECK (effective_end_at IS NULL OR effective_end_at > effective_start_at)
    );
    
    -- Unique constraint: only one primary assignment per person at a time
    CREATE UNIQUE INDEX idx_person_location_primary
    ON person_location_assignment(person_id, "primary")
    WHERE "primary" = true AND effective_end_at IS NULL;
    ```
  - **Demotion logic:**
    ```java
    @Transactional
    public PersonLocationAssignment createAssignment(CreateAssignmentRequest req) {
        if (req.isPrimary()) {
            // Demote existing primary
            assignmentRepo.updatePrimary(req.getPersonId(), false);
        }
        
        PersonLocationAssignment assignment = new PersonLocationAssignment();
        assignment.setPersonId(req.getPersonId());
        assignment.setLocationId(req.getLocationId());
        assignment.setPrimary(req.isPrimary());
        assignment.setEffectiveStartAt(req.getEffectiveStartAt());
        
        return assignmentRepo.save(assignment);
    }
    ```
  - **UI behavior:**
    - User creates new assignment with primary=true
    - Submit request
    - Backend demotes old primary, creates new primary
    - Response includes new assignment ID
    - UI refreshes assignment list (shows old primary now secondary, new assignment primary)
- **Auditor-facing explanation:**
  - **What to inspect:** Verify at most one primary assignment per person at any time
  - **Query example:**
    ```sql
    -- Find persons with multiple active primary assignments (should be zero)
    SELECT person_id, COUNT(*) as primary_count
    FROM person_location_assignment
    WHERE "primary" = true
      AND (effective_end_at IS NULL OR effective_end_at > NOW())
    GROUP BY person_id
    HAVING COUNT(*) > 1;
    ```
  - **Expected outcome:** Zero persons with multiple primaries
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add primary column (default false, nullable initially)
    2. Identify existing "home location" assignments and mark as primary
    3. Resolve any conflicts (multiple potential primaries)
    4. Make primary NOT NULL and deploy unique index
    5. Deploy automatic demotion logic
  - **Conflict resolution:**
    ```sql
    -- Find persons with multiple potential primaries
    SELECT person_id, COUNT(*) as assignment_count
    FROM person_location_assignment
    WHERE effective_end_at IS NULL
    GROUP BY person_id
    HAVING COUNT(*) > 1;
    
    -- Manually review and keep most recent or most relevant as primary
    ```
  - **Communication:**
    - Notify users that creating a new primary demotes the old one
    - Document in UI help text
- **Governance & owner recommendations:**
  - **Owner:** People domain team
  - **Review cadence:** No regular review needed; stable invariant
  - **Monitoring:** Alert on uniqueness constraint violations (should not occur)

## DECISION-PEOPLE-005 - Timekeeping entry ingestion and deduplication

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-005)
- **Decision:** Timekeeping entries are ingested from Shop Management `WorkSessionCompleted` events. Each entry includes a source correlation key (e.g., `workSessionId`). The backend deduplicates entries using this key: if an entry with the same source key already exists, the ingestion is idempotent (no duplicate created). UI displays deduplicated entries only.
- **Alternatives considered:**
  - **Option A (Chosen):** Source key deduplication at ingestion
    - Pros: Idempotent, prevents duplicates, simple query model
    - Cons: Requires unique key management across systems
  - **Option B:** Allow duplicates, deduplicate in UI
    - Pros: Simpler ingestion
    - Cons: Confusing UI, data quality issues, inaccurate payroll totals
  - **Option C:** No deduplication (prevent duplicate events upstream)
    - Pros: Cleanest architecture
    - Cons: Unrealistic (events can be retried/replayed), brittle
- **Reasoning and evidence:**
  - Event-driven systems must handle retries and replay scenarios
  - Duplicate timekeeping entries cause payroll overpayment and audit issues
  - Source correlation key (workSessionId) is stable and unique per work session
  - Idempotent ingestion is standard pattern for event-sourced data
  - Deduplication at write time is more reliable than read-time filtering
- **Architectural implications:**
  - **Components affected:**
    - Timekeeping ingestion service: Subscribes to WorkSessionCompleted events
    - Database: Unique constraint on source_key
    - UI: Queries deduplicated entries
  - **Database schema:**
    ```sql
    CREATE TABLE timekeeping_entry (
      id UUID PRIMARY KEY,
      person_id UUID NOT NULL REFERENCES person(id),
      work_session_id UUID NOT NULL, -- source correlation key
      clock_in_at TIMESTAMPTZ NOT NULL,
      clock_out_at TIMESTAMPTZ,
      location_id UUID REFERENCES location(id),
      work_order_id UUID,
      approval_status VARCHAR(50) DEFAULT 'PENDING_APPROVAL',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE UNIQUE INDEX idx_timekeeping_source_key
    ON timekeeping_entry(work_session_id);
    ```
  - **Ingestion logic:**
    ```java
    @EventListener
    public void onWorkSessionCompleted(WorkSessionCompletedEvent event) {
        try {
            TimekeepingEntry entry = new TimekeepingEntry();
            entry.setPersonId(event.getMechanicId());
            entry.setWorkSessionId(event.getWorkSessionId()); // source key
            entry.setClockInAt(event.getStartTime());
            entry.setClockOutAt(event.getEndTime());
            entry.setLocationId(event.getLocationId());
            entry.setWorkOrderId(event.getWorkOrderId());
            
            timekeepingRepo.save(entry);
            log.info("Ingested timekeeping entry for session {}", event.getWorkSessionId());
        } catch (DataIntegrityViolationException e) {
            // Duplicate key, already ingested - idempotent
            log.debug("Duplicate timekeeping entry for session {}, skipping", event.getWorkSessionId());
        }
    }
    ```
  - **UI query:**
    ```sql
    SELECT * FROM timekeeping_entry
    WHERE person_id = ?
      AND clock_in_at BETWEEN ? AND ?
    ORDER BY clock_in_at DESC;
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify no duplicate entries for same work session, event replay is handled idempotently
  - **Query example:**
    ```sql
    -- Find duplicate work sessions (should be zero)
    SELECT work_session_id, COUNT(*) as entry_count
    FROM timekeeping_entry
    GROUP BY work_session_id
    HAVING COUNT(*) > 1;
    
    -- Verify all work sessions have corresponding timekeeping entries
    SELECT ws.id, ws.mechanic_id, ws.ended_at
    FROM work_session ws
    LEFT JOIN timekeeping_entry te ON te.work_session_id = ws.id
    WHERE ws.ended_at IS NOT NULL -- completed sessions
      AND te.id IS NULL; -- no entry ingested
    ```
  - **Expected outcome:**
    - Zero duplicate entries
    - All completed work sessions have entries (may lag slightly for recent completions)
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create timekeeping_entry table with unique constraint
    2. Backfill historical work sessions (one-time)
    3. Deploy event subscriber for ongoing ingestion
    4. Monitor for duplicate ingestion attempts (logged but not inserted)
  - **Backfill strategy:**
    ```sql
    -- Insert historical work sessions as timekeeping entries
    INSERT INTO timekeeping_entry (id, person_id, work_session_id, clock_in_at, clock_out_at, location_id, work_order_id)
    SELECT gen_random_uuid(), ws.mechanic_id, ws.id, ws.started_at, ws.ended_at, ws.location_id, ws.work_order_id
    FROM work_session ws
    WHERE ws.ended_at IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM timekeeping_entry te WHERE te.work_session_id = ws.id
      );
    ```
  - **Event replay safety:**
    - System can safely replay WorkSessionCompleted events
    - Duplicate attempts are ignored (logged)
- **Governance & owner recommendations:**
  - **Owner:** People domain team with coordination from Shop Management
  - **Monitoring:** Alert on high rate of duplicate ingestion attempts (may indicate event bus issue)
  - **Review cadence:** Monthly reconciliation of work sessions vs timekeeping entries
  - **Data quality:** Periodic audit to ensure no missing entries for completed sessions

## DECISION-PEOPLE-006 - Time period approval atomicity

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-006)
- **Decision:** Manager approval/rejection of time entries operates at the time period level (pay period) as an atomic action. All pending entries for an employee within a period are approved or rejected together. Each approval action creates an immutable `TimePeriodApproval` record capturing the manager, timestamp, and outcome. Multiple approval records per period-employee are allowed (history of approval cycles).
- **Alternatives considered:**
  - **Option A (Chosen):** Period-atomic approvals with append-only history
    - Pros: Matches payroll workflow, clear audit trail, batch efficiency
    - Cons: Cannot approve subset of entries within period
  - **Option B:** Entry-by-entry approval
    - Pros: Fine-grained control
    - Cons: Tedious for managers, not aligned with payroll periods, complex UI
  - **Option C:** Mutable approval records (update existing record)
    - Pros: Single record per period-employee
    - Cons: Loses approval history, not audit-compliant
- **Reasoning and evidence:**
  - Payroll processes time entries in batches by pay period
  - Manager reviews all entries for a period before payroll submission
  - Atomic approval/rejection prevents partial state during review cycle
  - Append-only audit trail satisfies compliance and troubleshooting needs
  - Multiple cycles support correction workflow (reject → fix → re-approve)
  - Industry standard pattern for time and attendance systems
- **Architectural implications:**
  - **Components affected:**
    - Time approval service: Creates approval records atomically
    - Database: TimePeriodApproval table with period-employee scope
    - UI: Manager approval screen shows entries grouped by period
  - **Database schema:**
    ```sql
    CREATE TABLE time_period_approval (
      id UUID PRIMARY KEY,
      time_period_id UUID NOT NULL REFERENCES time_period(id),
      employee_id UUID NOT NULL REFERENCES person(id),
      approved_by UUID NOT NULL REFERENCES "user"(id),
      approved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
      comments TEXT,
      entry_count INTEGER NOT NULL, -- snapshot of entries affected
      -- No unique constraint; allows multiple approval cycles
    );
    
    CREATE INDEX idx_time_period_approval_period_employee
    ON time_period_approval(time_period_id, employee_id, approved_at DESC);
    ```
  - **Approval logic:**
    ```java
    @Transactional
    public TimePeriodApproval approveTimeEntries(UUID timePeriodId, UUID employeeId, UUID approverId) {
        // Find all pending entries for period-employee
        List<TimeEntry> entries = timeEntryRepo.findByPeriodAndEmployee(timePeriodId, employeeId);
        
        if (entries.isEmpty()) {
            throw new ValidationException("No pending entries to approve");
        }
        
        // Update entry statuses
        for (TimeEntry entry : entries) {
            entry.setStatus(TimeEntryStatus.APPROVED);
        }
        timeEntryRepo.saveAll(entries);
        
        // Create approval record
        TimePeriodApproval approval = new TimePeriodApproval();
        approval.setTimePeriodId(timePeriodId);
        approval.setEmployeeId(employeeId);
        approval.setApprovedBy(approverId);
        approval.setOutcome(ApprovalOutcome.APPROVED);
        approval.setEntryCount(entries.size());
        
        return approvalRepo.save(approval);
    }
    ```
  - **UI behavior:**
    - Manager selects pay period and employee
    - System displays all time entries for that period-employee
    - Manager clicks "Approve All" or "Reject All" button
    - System creates approval record and updates entry statuses atomically
    - UI shows approval history with timestamp, approver, and outcome
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all time entries within a period are approved/rejected together, approval history is complete
  - **Query example:**
    ```sql
    -- Find approval records and verify entry counts match
    SELECT tpa.id, tpa.time_period_id, tpa.employee_id, 
           tpa.entry_count, COUNT(te.id) as actual_entry_count
    FROM time_period_approval tpa
    LEFT JOIN time_entry te ON 
      te.time_period_id = tpa.time_period_id
      AND te.employee_id = tpa.employee_id
      AND te.approved_at BETWEEN tpa.approved_at - INTERVAL '1 second' AND tpa.approved_at + INTERVAL '1 second'
    GROUP BY tpa.id
    HAVING tpa.entry_count != COUNT(te.id);
    
    -- Find time entries without corresponding approval record
    SELECT te.id, te.time_period_id, te.employee_id, te.status
    FROM time_entry te
    WHERE te.status = 'APPROVED'
      AND NOT EXISTS (
        SELECT 1 FROM time_period_approval tpa
        WHERE tpa.time_period_id = te.time_period_id
          AND tpa.employee_id = te.employee_id
          AND tpa.outcome = 'APPROVED'
      );
    ```
  - **Expected outcome:**
    - Entry counts match in approval records
    - All approved entries have corresponding approval record
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create time_period_approval table
    2. Backfill approval records for historical approved entries (best-effort)
    3. Deploy period-atomic approval logic
    4. Update UI for batch approval workflow
  - **Backfill strategy:**
    ```sql
    -- Create synthetic approval records for historical approvals
    INSERT INTO time_period_approval (id, time_period_id, employee_id, approved_by, approved_at, outcome, entry_count, comments)
    SELECT 
      gen_random_uuid(),
      te.time_period_id,
      te.employee_id,
      te.approved_by,
      MAX(te.approved_at),
      'APPROVED',
      COUNT(*),
      'Backfilled from historical data'
    FROM time_entry te
    WHERE te.status = 'APPROVED'
      AND te.approved_at IS NOT NULL
    GROUP BY te.time_period_id, te.employee_id, te.approved_by;
    ```
  - **Communication:**
    - Train managers on period-atomic approval workflow
    - Document that partial approval within period is not supported
- **Governance & owner recommendations:**
  - **Owner:** People domain team with input from Payroll
  - **Review cadence:** Annual review of approval workflow and manager feedback
  - **Policy:** Define approval SLA (e.g., manager must approve within 3 days of period close)
  - **Monitoring:** Alert on periods without approvals after submission deadline

## DECISION-PEOPLE-007 - Break type enumeration and auto-end flag

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-007)
- **Decision:** Break records include a `breakType` enum (`MEAL`, `REST`, `OTHER`) and an optional `autoEnded` boolean flag. The enum categorizes breaks for compliance reporting (e.g., meal breaks >30min). The autoEnded flag indicates system-initiated break ends (e.g., shift end auto-closes break) vs. user-initiated. Backend enforces break type validation; UI provides picker with standard types.
- **Alternatives considered:**
  - **Option A (Chosen):** Fixed enum with autoEnded flag
    - Pros: Structured data for compliance, clear semantics, supports audit
    - Cons: May not cover all break scenarios (addressed by OTHER)
  - **Option B:** Free-text break type
    - Pros: Maximum flexibility
    - Cons: Inconsistent categorization, difficult compliance reporting
  - **Option C:** No break type distinction
    - Pros: Simplest model
    - Cons: Cannot comply with labor law reporting requirements
- **Reasoning and evidence:**
  - Labor laws mandate tracking meal breaks separately (often >30 minutes, unpaid)
  - Compliance reporting requires categorization by break type
  - Auto-ended breaks indicate potential data quality issues (employee forgot to end break)
  - Fixed enum ensures consistent reporting across locations and employees
  - OTHER category provides escape hatch for edge cases
  - Industry standard pattern in workforce management systems
- **Architectural implications:**
  - **Components affected:**
    - Break service: Validates break type and sets autoEnded flag
    - Database: Store break type as enum
    - UI: Break picker with MEAL/REST/OTHER options
    - Reporting: Break compliance report by type
  - **Database schema:**
    ```sql
    CREATE TYPE break_type AS ENUM ('MEAL', 'REST', 'OTHER');
    
    CREATE TABLE break (
      id UUID PRIMARY KEY,
      person_id UUID NOT NULL REFERENCES person(id),
      break_type break_type NOT NULL,
      started_at TIMESTAMPTZ NOT NULL,
      ended_at TIMESTAMPTZ,
      auto_ended BOOLEAN DEFAULT false,
      location_id UUID REFERENCES location(id)
    );
    
    CREATE INDEX idx_break_person_date ON break(person_id, started_at DESC);
    ```
  - **Auto-end logic:**
    ```java
    // When shift ends, auto-close any open breaks
    public void endShift(UUID personId, LocalDateTime shiftEndTime) {
        List<Break> openBreaks = breakRepo.findOpenByPerson(personId);
        
        for (Break brk : openBreaks) {
            brk.setEndedAt(shiftEndTime);
            brk.setAutoEnded(true);
            log.warn("Auto-ended break {} for person {} at shift end", brk.getId(), personId);
        }
        
        breakRepo.saveAll(openBreaks);
    }
    ```
  - **UI behavior:**
    - Employee starts break: selects type from MEAL/REST/OTHER
    - Employee ends break: clicks End Break (normal flow)
    - System auto-ends: if shift ends with open break, autoEnded=true
    - Manager view: see break history with type and auto-end indicator
- **Auditor-facing explanation:**
  - **What to inspect:** Verify break types are valid, auto-ended breaks are flagged
  - **Query example:**
    ```sql
    -- Break type distribution (compliance reporting)
    SELECT break_type, COUNT(*) as count,
           AVG(EXTRACT(EPOCH FROM (ended_at - started_at))/60) as avg_duration_minutes
    FROM break
    WHERE ended_at IS NOT NULL
      AND started_at >= '2026-01-01'
    GROUP BY break_type;
    
    -- Find auto-ended breaks (potential data quality issue)
    SELECT id, person_id, break_type, started_at, ended_at, auto_ended
    FROM break
    WHERE auto_ended = true
      AND started_at >= NOW() - INTERVAL '30 days'
    ORDER BY started_at DESC;
    ```
  - **Expected outcome:**
    - Break types match expected distribution (MEAL ~30%, REST ~70%, OTHER <5%)
    - Auto-ended breaks are < 5% of total (low rate indicates good employee behavior)
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add break_type enum type
    2. Add break_type and auto_ended columns (nullable initially)
    3. Backfill historical breaks with best-guess type (based on duration)
    4. Make break_type NOT NULL
    5. Deploy break type picker UI
  - **Backfill strategy:**
    ```sql
    -- Classify historical breaks by duration
    UPDATE break
    SET break_type = CASE
      WHEN EXTRACT(EPOCH FROM (ended_at - started_at)) > 1800 THEN 'MEAL'::break_type -- >30 min
      WHEN EXTRACT(EPOCH FROM (ended_at - started_at)) > 600 THEN 'REST'::break_type -- >10 min
      ELSE 'OTHER'::break_type
    END,
    auto_ended = false -- assume historical breaks were manually ended
    WHERE break_type IS NULL;
    ```
  - **Communication:**
    - Train employees on selecting correct break type
    - Document labor law requirements for meal break duration
- **Governance & owner recommendations:**
  - **Owner:** People domain team with Legal/Compliance oversight
  - **Review cadence:** Quarterly compliance audit of break type usage
  - **Policy:** Document meal break duration requirements per jurisdiction
  - **Monitoring:** Alert on high rate of auto-ended breaks (indicates training issue)

## DECISION-PEOPLE-008 - Employee profile conflict handling (409 vs warnings)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-008)
- **Decision:** Employee profile create/update returns HTTP 409 Conflict for blocking errors (e.g., duplicate employee number) and HTTP 200 with warnings array for non-blocking validation issues (e.g., missing optional fields recommended for payroll). The UI must distinguish between blocking errors (prevent save, show error) and warnings (allow save, show warnings banner).
- **Alternatives considered:**
  - **Option A (Chosen):** 409 for blocking, 200+warnings for non-blocking
    - Pros: Clear semantics, allows warnings without blocking save, standard HTTP usage
    - Cons: Requires UI to handle both response types
  - **Option B:** 400 for all validation errors
    - Pros: Simple to implement
    - Cons: Cannot distinguish blocking vs non-blocking issues, poor UX
  - **Option C:** Always block on any validation issue
    - Pros: Simplest validation
    - Cons: Forces users to fix non-critical issues before saving, frustrating UX
- **Reasoning and evidence:**
  - Some profile issues are critical (duplicate employee number breaks payroll)
  - Some profile issues are warnings (missing secondary contact info)
  - Users need to save work-in-progress profiles without fixing all warnings immediately
  - HTTP 409 Conflict is standard for resource conflict scenarios
  - Warnings array allows communicating multiple non-blocking issues
  - Industry pattern: validation with severity levels
- **Architectural implications:**
  - **Components affected:**
    - Employee profile API: Validates and returns appropriate status
    - UI: Handles 409 (block save) and 200+warnings (save with banner)
  - **Response examples:**
    ```json
    // 409 Conflict - blocking error
    {
      "errorCode": "EMPLOYEE_NUMBER_CONFLICT",
      "message": "Employee number EMP-12345 is already in use",
      "field": "employeeNumber",
      "existingEmployeeId": "per-67890"
    }
    
    // 200 OK with warnings - non-blocking
    {
      "id": "per-67890",
      "employeeNumber": "EMP-12345",
      "status": "ACTIVE",
      "warnings": [
        {
          "code": "MISSING_EMERGENCY_CONTACT",
          "message": "Emergency contact information is recommended for payroll",
          "field": "emergencyContact",
          "severity": "WARNING"
        },
        {
          "code": "HIRE_DATE_FUTURE",
          "message": "Hire date is in the future; verify accuracy",
          "field": "hireDate",
          "severity": "INFO"
        }
      ]
    }
    ```
  - **UI handling:**
    ```typescript
    async function saveEmployeeProfile(profile) {
      try {
        const response = await api.post('/employee-profiles', profile);
        
        if (response.warnings && response.warnings.length > 0) {
          // Show warnings banner but allow save
          showWarningsBanner(response.warnings);
        }
        
        showSuccess('Employee profile saved');
        navigate(`/employees/${response.id}`);
      } catch (error) {
        if (error.status === 409) {
          // Blocking error - prevent save
          showFieldError(error.field, error.message);
        } else {
          showError('Failed to save employee profile');
        }
      }
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify blocking errors prevent profile creation, warnings are logged but don't block
  - **Query example:**
    ```sql
    -- Find employee profiles with duplicate employee numbers (should be zero)
    SELECT employee_number, COUNT(*) as count
    FROM person
    WHERE employee_number IS NOT NULL
    GROUP BY employee_number
    HAVING COUNT(*) > 1;
    
    -- Find profiles with warnings (for data quality review)
    SELECT id, employee_number, hire_date, emergency_contact
    FROM person
    WHERE emergency_contact IS NULL -- warning condition
      OR hire_date > NOW(); -- another warning condition
    ```
  - **Expected outcome:**
    - Zero duplicate employee numbers
    - Some profiles with warnings (acceptable if recent)
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define blocking vs warning validation rules
    2. Update API to return 409 for blocking, 200+warnings for non-blocking
    3. Update UI to handle both response types
    4. Test both scenarios (conflict and warnings)
  - **Validation rule catalog:**
    ```yaml
    blocking:
      - duplicate_employee_number
      - invalid_hire_date (past termination date)
      - invalid_status_transition
    
    warnings:
      - missing_emergency_contact
      - missing_secondary_phone
      - hire_date_in_future
      - missing_tax_withholding_info
    ```
  - **Communication:**
    - Train HR admins on difference between errors and warnings
    - Document that warnings can be addressed later
- **Governance & owner recommendations:**
  - **Owner:** People domain team with input from HR and Payroll
  - **Review cadence:** Quarterly review of validation rules and warning thresholds
  - **Policy:** Define which fields are required for payroll processing
  - **Monitoring:** Track warning rates to identify data quality trends

## DECISION-PEOPLE-009 - Mechanic roster as read model synced from HR

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-009)
- **Decision:** The Mechanic roster (used by Dispatch and Shop Management) is a read-only projection of authoritative Person/User data from the People domain. It is populated via HR sync events or backend-to-backend API calls. The roster includes status, home location, and skills snapshot. Roster data is eventually consistent with authoritative source. UI displays sync timestamp and refresh capability.
- **Alternatives considered:**
  - **Option A (Chosen):** Read model with event-driven sync
    - Pros: Decoupled domains, optimized for read queries, eventual consistency acceptable
    - Cons: Temporary inconsistency during sync lag
  - **Option B:** Direct query to People domain
    - Pros: Always consistent
    - Cons: High latency, tight coupling, scalability bottleneck
  - **Option C:** Shared database view
    - Pros: Simpler architecture
    - Cons: Violates domain boundaries, tight coupling, schema evolution issues
- **Reasoning and evidence:**
  - Dispatch and Shop Management need fast, read-optimized mechanic lookups
  - Authoritative Person data is in People domain (single source of truth)
  - Read model pattern separates read and write concerns
  - Event-driven sync supports independent scaling and evolution
  - Eventual consistency is acceptable for roster (not financial/critical data)
  - Industry pattern: CQRS (Command Query Responsibility Segregation)
- **Architectural implications:**
  - **Components affected:**
    - People domain: Publishes person/user lifecycle events
    - Dispatch/ShopMgmt domain: Subscribes to events, maintains Mechanic read model
    - Database: Mechanic table in consuming domain's schema
  - **Read model schema:**
    ```sql
    -- In Dispatch/ShopMgmt database
    CREATE TABLE mechanic (
      id UUID PRIMARY KEY, -- person_id from People domain
      employee_number VARCHAR(50),
      name VARCHAR(255),
      status VARCHAR(50), -- ACTIVE, INACTIVE, ON_LEAVE
      home_location_id UUID,
      last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    
    CREATE TABLE mechanic_skill (
      mechanic_id UUID NOT NULL REFERENCES mechanic(id),
      skill_id UUID NOT NULL,
      skill_name VARCHAR(100),
      level INTEGER,
      PRIMARY KEY (mechanic_id, skill_id)
    );
    
    CREATE INDEX idx_mechanic_status ON mechanic(status);
    CREATE INDEX idx_mechanic_location ON mechanic(home_location_id);
    ```
  - **Sync events:**
    ```json
    // person.created event
    {
      "eventType": "person.created",
      "personId": "per-12345",
      "employeeNumber": "EMP-67890",
      "name": "John Doe",
      "status": "ACTIVE",
      "homeLocationId": "loc-11111",
      "skills": [
        {"skillId": "skill-001", "name": "Engine Repair", "level": 3}
      ]
    }
    
    // person.updated event (similar schema)
    // person.terminated event (status change)
    ```
  - **Sync handler:**
    ```java
    @EventListener
    public void onPersonCreated(PersonCreatedEvent event) {
        Mechanic mechanic = new Mechanic();
        mechanic.setId(event.getPersonId());
        mechanic.setEmployeeNumber(event.getEmployeeNumber());
        mechanic.setName(event.getName());
        mechanic.setStatus(event.getStatus());
        mechanic.setHomeLocationId(event.getHomeLocationId());
        mechanic.setLastSyncedAt(LocalDateTime.now());
        
        mechanicRepo.save(mechanic);
        
        // Sync skills
        event.getSkills().forEach(skill -> {
            MechanicSkill ms = new MechanicSkill();
            ms.setMechanicId(event.getPersonId());
            ms.setSkillId(skill.getSkillId());
            ms.setSkillName(skill.getName());
            ms.setLevel(skill.getLevel());
            mechanicSkillRepo.save(ms);
        });
    }
    ```
  - **UI behavior:**
    - Display mechanic roster with status, location, skills
    - Show last sync timestamp
    - Provide "Refresh" button to trigger background sync (backend API call)
    - Display sync-in-progress indicator
- **Auditor-facing explanation:**
  - **What to inspect:** Verify mechanic roster is consistent with People domain (eventual), sync timestamps are recent
  - **Query example:**
    ```sql
    -- Compare mechanic roster count to People active employee count
    SELECT 
      (SELECT COUNT(*) FROM mechanic WHERE status = 'ACTIVE') as roster_count,
      (SELECT COUNT(*) FROM person WHERE employment_status = 'ACTIVE') as people_count;
    
    -- Find stale mechanic records (not synced recently)
    SELECT id, name, status, last_synced_at,
           NOW() - last_synced_at as sync_age
    FROM mechanic
    WHERE last_synced_at < NOW() - INTERVAL '24 hours'
    ORDER BY last_synced_at ASC;
    ```
  - **Expected outcome:**
    - Roster count matches People count (within margin for recent changes)
    - Most mechanics have recent sync timestamps (< 1 hour)
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create mechanic read model schema
    2. Implement event subscribers
    3. Perform initial bulk sync from People domain
    4. Enable event-driven incremental sync
    5. Deploy UI with sync status display
  - **Initial bulk sync:**
    ```sql
    -- One-time sync via backend API or data export
    INSERT INTO mechanic (id, employee_number, name, status, home_location_id, last_synced_at)
    SELECT p.id, p.employee_number, p.name, p.employment_status, pla.location_id, NOW()
    FROM person p
    LEFT JOIN person_location_assignment pla ON pla.person_id = p.id AND pla."primary" = true
    WHERE p.employment_status IN ('ACTIVE', 'ON_LEAVE');
    ```
  - **Monitoring:**
    - Alert on high sync lag (last_synced_at > 1 hour old)
    - Alert on roster count divergence from People count (> 10% difference)
- **Governance & owner recommendations:**
  - **Owner:** Consuming domain (Dispatch/ShopMgmt) with coordination from People team
  - **Review cadence:** Monthly sync health review
  - **SLA:** Sync lag should be < 5 minutes under normal operation
  - **Incident response:** Manual bulk sync procedure for sync failures

## DECISION-PEOPLE-010 - Time entry approval authorization

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-010)
- **Decision:** Time entry approval requires the `TIME_APPROVE` permission. Only users with this permission can approve/reject time entries. The backend enforces permission checks server-side. Managers can only approve entries for employees under their management (location-based or hierarchical). Unauthorized approval attempts return HTTP 403 Forbidden.
- **Alternatives considered:**
  - **Option A (Chosen):** Explicit TIME_APPROVE permission with manager scope
    - Pros: Fine-grained control, prevents unauthorized approvals, clear audit trail
    - Cons: Requires permission management and scope definition
  - **Option B:** Role-based (only "Manager" role)
    - Pros: Simple to understand
    - Cons: Inflexible, cannot grant approval to non-managers (e.g., payroll clerk)
  - **Option C:** No authorization check (allow any authenticated user)
    - Pros: Simplest implementation
    - Cons: Security risk, anyone could approve anyone's time
- **Reasoning and evidence:**
  - Time approval affects payroll and has financial impact
  - Managers should only approve time for their direct reports or location
  - Permission-based model allows flexible role assignment (managers, payroll clerks, HR admins)
  - Backend enforcement prevents privilege escalation via UI bypass
  - Audit trail requires knowing who had permission at time of approval
  - Industry standard for payroll and time approval systems
- **Architectural implications:**
  - **Components affected:**
    - Time approval API: Checks TIME_APPROVE permission and manager scope
    - Frontend: Conditionally displays approval actions based on permissions
    - Permission service: Manages and exposes user permissions
  - **Authorization check:**
    ```java
    @PreAuthorize("hasAuthority('TIME_APPROVE')")
    public TimePeriodApproval approveTimeEntries(UUID timePeriodId, UUID employeeId, UUID approverId) {
        // Check manager scope
        if (!isManagerOf(approverId, employeeId)) {
            throw new ForbiddenException("You can only approve time for your direct reports");
        }
        
        // ... approval logic
    }
    
    private boolean isManagerOf(UUID managerId, UUID employeeId) {
        // Check if manager has authority over employee (location-based or hierarchical)
        PersonLocationAssignment managerAssignment = assignmentRepo.findPrimaryByPerson(managerId);
        PersonLocationAssignment employeeAssignment = assignmentRepo.findPrimaryByPerson(employeeId);
        
        return managerAssignment.getLocationId().equals(employeeAssignment.getLocationId());
    }
    ```
  - **Frontend permission check:**
    ```typescript
    const userPermissions = inject('userPermissions');
    const canApproveTime = computed(() => 
      userPermissions.value.includes('TIME_APPROVE')
    );
    
    <button v-if="canApproveTime && isMyDirectReport(employee)" @click="approveTime">
      Approve Time
    </button>
    ```
  - **Error response:**
    ```json
    {
      "errorCode": "FORBIDDEN",
      "message": "You do not have permission to approve time entries",
      "requiredPermission": "TIME_APPROVE"
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all time approvals were performed by authorized users with correct scope
  - **Query example:**
    ```sql
    -- Find approvals by users without TIME_APPROVE permission
    SELECT tpa.id, tpa.approved_by, tpa.employee_id, tpa.approved_at
    FROM time_period_approval tpa
    LEFT JOIN user_permission_history uph ON 
      uph.user_id = tpa.approved_by
      AND uph.permission = 'TIME_APPROVE'
      AND uph.effective_from <= tpa.approved_at
      AND (uph.effective_to IS NULL OR uph.effective_to >= tpa.approved_at)
    WHERE uph.id IS NULL;
    
    -- Find approvals where manager has no authority over employee
    SELECT tpa.id, tpa.approved_by, tpa.employee_id, tpa.approved_at
    FROM time_period_approval tpa
    LEFT JOIN person_location_assignment mgr_asg ON 
      mgr_asg.person_id = tpa.approved_by AND mgr_asg."primary" = true
    LEFT JOIN person_location_assignment emp_asg ON 
      emp_asg.person_id = tpa.employee_id AND emp_asg."primary" = true
    WHERE mgr_asg.location_id != emp_asg.location_id;
    ```
  - **Expected outcome:** Zero unauthorized approvals
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Define TIME_APPROVE permission in permission system
    2. Assign permission to Manager and Payroll Clerk roles
    3. Deploy backend authorization checks
    4. Update frontend to check permission and gate UI
    5. Test with users having and lacking permission
  - **Rollout strategy:**
    - Phase 1: Deploy permission checks in warn-only mode
    - Phase 2: Review logs for unexpected access patterns
    - Phase 3: Enable strict enforcement (403 for unauthorized)
  - **Communication:**
    - Notify managers of permission requirement
    - Provide instructions for requesting TIME_APPROVE permission
- **Governance & owner recommendations:**
  - **Owner:** People domain team with coordination from Security
  - **Review cadence:** Quarterly audit of TIME_APPROVE permission assignments
  - **Policy:** Grant TIME_APPROVE to managers, payroll clerks, and HR admins only
  - **Monitoring:** Alert on unusual approval patterns (high volume, cross-location approvals)

## DECISION-PEOPLE-011 - Mechanic roster storage ownership

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-011)
- **Decision:** People is the system of record for `Person` identity and lifecycle status. The Mechanic roster is a read model that may be physically stored in the consuming domain (Dispatch/ShopMgmt) for query performance, but it must be derived from People/HR data and must not redefine lifecycle truth.
- **Alternatives considered:**
  - **Option A (Chosen):** Store roster read model with consumers; sync from People/HR
    - Pros: Local query performance; clear SoR boundary
    - Cons: Requires sync and reconciliation
  - **Option B:** Store roster read model in People and force consumers to query People
    - Pros: Single storage location
    - Cons: Couples consumers to People availability/performance
- **Implications:**
  - Define a sync contract (event-driven or pull) and idempotency keys.
  - Consumers must show status sourced from People.

## DECISION-PEOPLE-012 - Person-user cardinality and identifiers

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-012)
- **Decision:** Default to 1:1 `User`→`Person` in v1. Use `personId` as the stable identifier for employee-centric screens and read models.
- **Rationale:** Simplifies auditing, authorization, and routing.
- **Implications:**
  - APIs should prefer `personId` parameters for People workflows.
  - If shared logins become necessary, it must be introduced as an explicit model change.

## DECISION-PEOPLE-013 - Permission naming and UI capability exposure

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-013)
- **Decision:** UI gates actions using named permissions (not role name heuristics). Where policy options exist (e.g., disable-user options), backend must return capability flags/options. Safe default permission names are listed in `AGENT_GUIDE.md` and must be reconciled to the platform permission catalog.
- **Rationale:** Prevents UI from becoming the policy engine and reduces drift.
- **Implications:**
  - Backend provides `canX`/`allowedActions` style fields for complex flows.
  - Security service remains the enforcement point.

## DECISION-PEOPLE-014 - Assignment effective dating semantics (exclusive end)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-014)
- **Decision:** Use half-open intervals for effective dating: `effectiveStartAt` inclusive, `effectiveEndAt` exclusive.
- **Rationale:** Deterministic overlap checks and no boundary ambiguity.
- **Implications:**
  - Backend validators and UI validators must match.
  - Display layers may present “end date” in a user-friendly way, but the stored semantics remain exclusive.

## DECISION-PEOPLE-015 - Timezone display standard for People UIs

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-015)
- **Decision:** Store instants in UTC. Display in user profile timezone when available; otherwise fall back to the relevant location timezone for location-scoped records.
- **Rationale:** Preserves correct instants while maximizing UX clarity.
- **Implications:** UI must indicate the timezone used for critical timestamps.

## DECISION-PEOPLE-016 - Break notes requirement for OTHER

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-016)
- **Decision:** Notes are optional but recommended for `breakType=OTHER`.
- **Rationale:** Allows low-friction entry while enabling richer auditing.
- **Implications:** UI should prompt for notes but not hard-block.

## DECISION-PEOPLE-017 - Optimistic concurrency default (lastUpdatedStamp)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-017)
- **Decision:** When entities expose a concurrency token (prefer `lastUpdatedStamp`), require clients to submit it on update and return 409 on mismatch.
- **Rationale:** Prevents lost updates in admin workflows.
- **Implications:** UI must handle 409 by refreshing and reapplying intended changes.

## DECISION-PEOPLE-018 - Error response schema (400/409)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-018)
- **Decision:** Standardize error responses:
  - 400 includes `errorCode`, `message`, optional `fieldErrors`
  - 409 includes `errorCode`, `message`, optional `blockingIds`, optional `details`
- **Rationale:** Enables consistent UI rendering and testing.
- **Implications:** If a gateway standard exists, People must map to it consistently.

## DECISION-PEOPLE-019 - tenantId UI visibility policy

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-019)
- **Decision:** Treat `tenantId` as sensitive; hide by default and only show for approved support/admin use cases.
- **Rationale:** Minimize internal identifier exposure.
- **Implications:** Use personId/employeeId as primary UI identifiers.

## DECISION-PEOPLE-020 - technicianIds query encoding and report range

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-020)
- **Decision:** Encode multi-tech filters as repeated query params (`technicianId=<id>`). Backend enforces a maximum date range and may expose this as a capability.
- **Rationale:** Avoids quoting/escaping pitfalls and supports standard HTTP clients.
- **Implications:** UI should surface server range violations with a clear message.

## DECISION-PEOPLE-021 - People REST API conventions (paths, paging, shapes)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-021)
- **Decision:** Default People HTTP APIs are exposed under `/rest/api/v1/people` using consistent list conventions:
  - Lists accept `page` and `pageSize` (or `pageIndex`/`pageSize`), with stable default sort.
  - List responses return `{ items: [], page, pageSize, total }` (or equivalent) and must not require UI inference.
  - Error responses follow DECISION-PEOPLE-018.
- **Rationale:** UI must be able to implement screens consistently across People features.

## DECISION-PEOPLE-022 - Break API contract and identity derivation

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-022)
- **Decision:** Break start/end operate on the authenticated user’s active session; UI does not pass timecard/session IDs by default.
- **Implications:** 409 conflicts (already in progress / no active break) are treated as refresh-required.

## DECISION-PEOPLE-023 - Person-location assignment API contract and reason codes

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-023)
- **Decision:** Provide list/create/end assignment endpoints under People REST conventions. `changeReasonCode` is optional in v1; introduce reference lists later if policy requires.

## DECISION-PEOPLE-024 - Disable user API contract and UX defaults

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-024)
- **Decision:** Disable user is a privileged action with explicit confirmation. Backend communicates allowed options via capability fields. Default UX stays on refreshed detail view after success.

## DECISION-PEOPLE-025 - Employee profile defaults and terminated edit rules

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-025)
- **Decision:** Default employee status on create is `ACTIVE`. Terminated employees are read-only by default; limited edits require explicit HR-admin capability.

## DECISION-PEOPLE-026 - Role assignment API contract, dating, and history defaults

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-PEOPLE-026)
- **Decision:** Role assignment lists default to active-only, with an explicit `includeHistory` toggle. Allow future-dated ends; allow backdated ends only when server validation allows (return 409 otherwise).

## End

End of document.
