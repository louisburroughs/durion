---
type: Domain Notes
title: Shop Management Domain Notes
description: This document provides comprehensive rationale and decision logs for the Shop Management (shopmgmt) domain. Shopmgmt manages appointment scheduling, resource assignment (bays/mobile units/mechanics...
domain: shopmgmt
tags: [domain, shopmgmt, domain-notes]
---

# SHOPMGMT_DOMAIN_NOTES.md

## Summary

This document provides comprehensive rationale and decision logs for the Shop Management (shopmgmt) domain. Shopmgmt manages appointment scheduling, resource assignment (bays/mobile units/mechanics), conflict detection, and facility-scoped scheduling policies. Each decision includes alternatives, architectural implications, audit guidance, and governance.

## Completed items

- [x] Documented 25 key shopmgmt decisions
- [x] Provided alternatives analysis
- [x] Included architectural schemas
- [x] Added auditor SQL queries
- [x] Defined governance strategies

## Decision details

### DECISION-SHOPMGMT-001 — Appointment-Source Document Immutability

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-001)
- **Decision:** Each appointment is linked to exactly one source document (Estimate or Work Order) at creation. This link is immutable once set. Appointments cannot switch between estimates and work orders.
- **Alternatives considered:**
  - **Option A (Chosen):** Immutable source document reference
    - Pros: Clear lineage, prevents confusion, simple state machine
    - Cons: Cannot "convert" appointment to different document
  - **Option B:** Mutable source (allow switching)
    - Pros: Flexible for workflow changes
    - Cons: Complex state management, audit confusion, data integrity risks
  - **Option C:** No source document link
    - Pros: Maximum flexibility
    - Cons: Orphaned appointments, cannot trace to work
- **Reasoning and evidence:**
  - Appointment lifecycle is tied to work document lifecycle
  - Switching source documents creates ambiguous history
  - Immutable references simplify state management and audit
  - Business process: estimate converts to work order (new appointment if needed)
  - Industry standard: scheduling systems link appointments to authoritative work documents
- **Architectural implications:**
  - **Components affected:**
    - Appointment service: Validates source document at creation
    - Database: Immutable foreign key to estimate OR work order
  - **Database schema:**

    ```sql
    CREATE TABLE appointment (
      id UUID PRIMARY KEY,
      facility_id UUID NOT NULL REFERENCES location(id),
      scheduled_start_datetime TIMESTAMPTZ NOT NULL,
      scheduled_end_datetime TIMESTAMPTZ NOT NULL,
      status VARCHAR(50) NOT NULL,
      estimate_id UUID REFERENCES estimate(id),
      work_order_id UUID REFERENCES work_order(id),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (
        (estimate_id IS NOT NULL AND work_order_id IS NULL) OR
        (estimate_id IS NULL AND work_order_id IS NOT NULL)
      ) -- exactly one source
    );
    
    CREATE INDEX idx_appointment_estimate ON appointment(estimate_id) WHERE estimate_id IS NOT NULL;
    CREATE INDEX idx_appointment_work_order ON appointment(work_order_id) WHERE work_order_id IS NOT NULL;
    ```

  - **Creation validation:**

    ```java
    @Transactional
    public Appointment createAppointment(CreateAppointmentRequest request) {
        // Validate exactly one source
        boolean hasEstimate = request.getEstimateId() != null;
        boolean hasWorkOrder = request.getWorkOrderId() != null;
        
        if (hasEstimate == hasWorkOrder) {
            throw new ValidationException(
                "Appointment must have exactly one source document (estimate OR work order)"
            );
        }
        
        Appointment appointment = new Appointment();
        appointment.setFacilityId(request.getFacilityId());
        appointment.setScheduledStartDatetime(request.getStartDatetime());
        appointment.setScheduledEndDatetime(request.getEndDatetime());
        appointment.setEstimateId(request.getEstimateId());
        appointment.setWorkOrderId(request.getWorkOrderId());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        
        return appointmentRepo.save(appointment);
    }
    
    // No update method that changes source document
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify all appointments have exactly one source, no source changes
  - **Query example:**

    ```sql
    -- Find appointments with invalid source (should be zero)
    SELECT id, estimate_id, work_order_id
    FROM appointment
    WHERE (estimate_id IS NULL AND work_order_id IS NULL)
       OR (estimate_id IS NOT NULL AND work_order_id IS NOT NULL);
    ```

  - **Expected outcome:** Zero invalid appointments
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add CHECK constraint to appointment table
    2. Audit existing appointments for invalid sources
    3. Fix data issues before enforcing constraint
    4. Deploy immutability enforcement
  - **Data fix:**

    ```sql
    -- Find and fix appointments with no source
    UPDATE appointment
    SET status = 'CANCELLED'
    WHERE estimate_id IS NULL AND work_order_id IS NULL;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain team
  - **Policy:** Source document is immutable by design
  - **Documentation:** Document source document lifecycle in user guide

### DECISION-SHOPMGMT-002 — Hard vs Soft Conflict Classification

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-002)
- **Decision:** Scheduling conflicts are classified as HARD (blocks scheduling, cannot override) or SOFT (warning, can override with manager approval). Examples: HARD = bay already booked, SOFT = mechanic approaching overtime.
- **Alternatives considered:**
  - **Option A (Chosen):** Two-tier classification with override capability
    - Pros: Clear semantics, supports business exceptions, audit trail
    - Cons: Requires manager approval workflow
  - **Option B:** All conflicts are warnings (no blocking)
    - Pros: Maximum flexibility
    - Cons: Double-bookings, no resource protection
  - **Option C:** All conflicts block (no overrides)
    - Pros: Strictest resource management
    - Cons: Inflexible, blocks legitimate exceptions
- **Reasoning and evidence:**
  - Some conflicts are business rules (bay cannot be double-booked)
  - Other conflicts are guidelines (prefer not to schedule overtime)
  - Manager judgment needed for soft conflicts
  - Override audit trail supports accountability
  - Industry pattern: scheduling systems use conflict severity levels
- **Architectural implications:**
  - **Components affected:**
    - Conflict detection service: Classifies conflicts
    - Scheduling API: Enforces hard conflicts, allows soft overrides
    - Override service: Records manager overrides
  - **Conflict schema:**

    ```sql
    CREATE TYPE conflict_severity AS ENUM ('HARD', 'SOFT');
    CREATE TYPE conflict_resource_type AS ENUM ('BAY', 'MECHANIC', 'CAPACITY', 'HOURS', 'SKILL');
    
    CREATE TABLE conflict_rule (
      id UUID PRIMARY KEY,
      code VARCHAR(50) NOT NULL UNIQUE,
      severity conflict_severity NOT NULL,
      resource_type conflict_resource_type NOT NULL,
      message_template TEXT NOT NULL,
      is_active BOOLEAN NOT NULL DEFAULT true
    );
    
    CREATE TABLE scheduling_conflict (
      id UUID PRIMARY KEY,
      appointment_id UUID REFERENCES appointment(id),
      conflict_rule_id UUID NOT NULL REFERENCES conflict_rule(id),
      severity conflict_severity NOT NULL,
      resource_id UUID, -- bay_id, mechanic_id, etc.
      detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      override_id UUID REFERENCES conflict_override(id)
    );
    
    CREATE TABLE conflict_override (
      id UUID PRIMARY KEY,
      conflict_id UUID NOT NULL REFERENCES scheduling_conflict(id),
      overridden_by UUID NOT NULL,
      override_reason TEXT NOT NULL,
      approved_by UUID,
      approved_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    ```

  - **Conflict detection:**

    ```java
    public List<Conflict> detectConflicts(ScheduleAppointmentRequest request) {
        List<Conflict> conflicts = new ArrayList<>();
        
        // Check bay availability (HARD)
        if (bayService.isBooked(request.getBayId(), request.getStartTime(), request.getEndTime())) {
            conflicts.add(new Conflict(
                "BAY_DOUBLE_BOOKED",
                ConflictSeverity.HARD,
                ConflictResourceType.BAY,
                "Bay is already booked for this time slot"
            ));
        }
        
        // Check mechanic availability (SOFT - can work overtime with approval)
        if (mechanicService.isApproachingOvertime(request.getMechanicId(), request.getDate())) {
            conflicts.add(new Conflict(
                "MECHANIC_OVERTIME",
                ConflictSeverity.SOFT,
                ConflictResourceType.MECHANIC,
                "Mechanic will exceed 40 hours this week"
            ));
        }
        
        return conflicts;
    }
    
    @Transactional
    public Appointment scheduleWithOverride(ScheduleRequest request, List<UUID> overrideIds) {
        List<Conflict> conflicts = detectConflicts(request);
        
        // Hard conflicts cannot be overridden
        List<Conflict> hardConflicts = conflicts.stream()
            .filter(c -> c.getSeverity() == ConflictSeverity.HARD)
            .toList();
        
        if (!hardConflicts.isEmpty()) {
            throw new SchedulingConflictException("Hard conflicts cannot be overridden", hardConflicts);
        }
        
        // Soft conflicts require overrides
        List<Conflict> softConflicts = conflicts.stream()
            .filter(c -> c.getSeverity() == ConflictSeverity.SOFT)
            .toList();
        
        if (!softConflicts.isEmpty() && overrideIds.isEmpty()) {
            throw new SchedulingConflictException("Soft conflicts require manager override", softConflicts);
        }
        
        // Create appointment with overrides
        Appointment appointment = createAppointment(request);
        linkOverrides(appointment, overrideIds);
        
        return appointment;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify hard conflicts never overridden, soft conflicts have approvals
  - **Query example:**

    ```sql
    -- Find hard conflicts with overrides (should be zero)
    SELECT sc.id, sc.appointment_id, cr.code, sc.severity
    FROM scheduling_conflict sc
    JOIN conflict_rule cr ON cr.id = sc.conflict_rule_id
    WHERE sc.severity = 'HARD'
      AND sc.override_id IS NOT NULL;
    
    -- Find soft conflict overrides without approval
    SELECT co.id, co.conflict_id, co.override_reason, co.overridden_by
    FROM conflict_override co
    JOIN scheduling_conflict sc ON sc.id = co.conflict_id
    WHERE sc.severity = 'SOFT'
      AND (co.approved_by IS NULL OR co.approved_at IS NULL);
    ```

  - **Expected outcome:** Zero hard overrides, all soft overrides approved
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create conflict tables
    2. Define initial conflict rules
    3. Deploy conflict detection
    4. Train managers on override process
  - **Initial rules:**

    ```sql
    INSERT INTO conflict_rule (id, code, severity, resource_type, message_template, is_active)
    VALUES
      (gen_random_uuid(), 'BAY_DOUBLE_BOOKED', 'HARD', 'BAY', 'Bay {bayName} is already booked', true),
      (gen_random_uuid(), 'MECHANIC_UNAVAILABLE', 'HARD', 'MECHANIC', 'Mechanic {mechanicName} is not available', true),
      (gen_random_uuid(), 'MECHANIC_OVERTIME', 'SOFT', 'MECHANIC', 'Mechanic will exceed 40 hours this week', true),
      -- Seeded is_active = false in pos-shop-manager: no timekeeping input exists for the evaluator to
      -- fire it from, and an active rule that is never evaluated advertises enforcement that does not
      -- exist (durion-positivity-backend#2045 review). Flipped when weekly hours arrive.
      (gen_random_uuid(), 'FACILITY_NEAR_CAPACITY', 'SOFT', 'CAPACITY', 'Facility is at 90% capacity', true),
      -- Extensions of the seeded rules, not amendments to the record (CAP-326, durion#483).
      -- SKILL rows per durion-positivity-backend#2035 and spec D10.1; HOURS rows per spec D18.1.
      -- The code is the API reason code verbatim: one namespace, no mapping table.
      (gen_random_uuid(), 'COMPETENT_MECHANIC_UNAVAILABLE', 'SOFT', 'SKILL', 'A mechanic holding {skills} works here but none is free for {start}–{end}', true),
      (gen_random_uuid(), 'NO_COMPETENT_MECHANIC_ROSTERED', 'SOFT', 'SKILL', 'No mechanic at this location holds {skills}', true),
      (gen_random_uuid(), 'OUTSIDE_OPERATING_HOURS', 'HARD', 'HOURS', '{start}–{end} falls outside the location''s operating hours for that day', true),
      (gen_random_uuid(), 'FACILITY_CLOSED', 'HARD', 'HOURS', 'The location is closed on {date}{reason}', true);
    ```

    Shipped by `pos-shop-manager`'s `R__seed_shop_manager_1_conflict_rules.sql` (md5-derived ids,
    `ON CONFLICT (code) DO UPDATE`); `conflict_rule` is platform-global there (spec D18.2).

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain with Operations oversight
  - **Policy:** Review conflict rules quarterly
  - **Monitoring:** Alert on high override rate (>10% of appointments)

### DECISION-SHOPMGMT-003 — Bay/Mobile Unit Assignment Exclusivity

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-003)
- **Decision:** An appointment can be assigned to exactly one of: Bay, Mobile Unit, or remain Unassigned. Cannot assign both bay and mobile unit simultaneously. Assignment type affects scheduling rules and conflict detection.
- **Alternatives considered:**
  - **Option A (Chosen):** Exclusive assignment with three states
    - Pros: Clear semantics, simple conflict detection, matches reality
    - Cons: Cannot model "backup" assignments
  - **Option B:** Allow multiple assignments
    - Pros: Flexible for "tentative" scenarios
    - Cons: Ambiguous primary assignment, complex conflicts
  - **Option C:** Always require assignment (no unassigned state)
    - Pros: Forces resource planning
    - Cons: Blocks early appointment creation
- **Reasoning and evidence:**
  - Physical reality: work happens in bay OR mobile unit, not both
  - Unassigned state supports early booking (assign resources later)
  - Exclusive assignment simplifies capacity management
  - Assignment type drives different scheduling rules
  - Industry pattern: scheduling systems use exclusive resource assignment
- **Architectural implications:**
  - **Components affected:**
    - Assignment service: Enforces exclusivity
    - Conflict detection: Different rules per assignment type
  - **Database schema:**

    ```sql
    CREATE TYPE assignment_type AS ENUM ('BAY', 'MOBILE_UNIT', 'UNASSIGNED');
    
    CREATE TABLE appointment_assignment (
      id UUID PRIMARY KEY,
      appointment_id UUID NOT NULL UNIQUE REFERENCES appointment(id),
      assignment_type assignment_type NOT NULL,
      bay_id UUID REFERENCES bay(id),
      mobile_unit_id UUID REFERENCES mobile_unit(id),
      mechanic_id UUID,
      assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      assigned_by UUID NOT NULL,
      CHECK (
        (assignment_type = 'BAY' AND bay_id IS NOT NULL AND mobile_unit_id IS NULL) OR
        (assignment_type = 'MOBILE_UNIT' AND mobile_unit_id IS NOT NULL AND bay_id IS NULL) OR
        (assignment_type = 'UNASSIGNED' AND bay_id IS NULL AND mobile_unit_id IS NULL)
      )
    );
    
    CREATE INDEX idx_assignment_bay ON appointment_assignment(bay_id, assigned_at) WHERE bay_id IS NOT NULL;
    CREATE INDEX idx_assignment_mobile ON appointment_assignment(mobile_unit_id, assigned_at) WHERE mobile_unit_id IS NOT NULL;
    ```

  - **Assignment logic:**

    ```java
    @Transactional
    public Assignment assignToBay(UUID appointmentId, UUID bayId, UUID mechanicId) {
        // Remove existing assignment if any
        assignmentRepo.findByAppointmentId(appointmentId)
            .ifPresent(existing -> assignmentRepo.delete(existing));
        
        Assignment assignment = new Assignment();
        assignment.setAppointmentId(appointmentId);
        assignment.setAssignmentType(AssignmentType.BAY);
        assignment.setBayId(bayId);
        assignment.setMobileUnitId(null); // explicit null
        assignment.setMechanicId(mechanicId);
        assignment.setAssignedBy(currentUser.getId());
        
        return assignmentRepo.save(assignment);
    }
    
    @Transactional
    public Assignment assignToMobileUnit(UUID appointmentId, UUID mobileUnitId, UUID mechanicId) {
        assignmentRepo.findByAppointmentId(appointmentId)
            .ifPresent(existing -> assignmentRepo.delete(existing));
        
        Assignment assignment = new Assignment();
        assignment.setAppointmentId(appointmentId);
        assignment.setAssignmentType(AssignmentType.MOBILE_UNIT);
        assignment.setBayId(null); // explicit null
        assignment.setMobileUnitId(mobileUnitId);
        assignment.setMechanicId(mechanicId);
        assignment.setAssignedBy(currentUser.getId());
        
        return assignmentRepo.save(assignment);
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify exclusivity constraint, no dual assignments
  - **Query example:**

    ```sql
    -- Find assignments violating exclusivity (should be zero)
    SELECT id, appointment_id, assignment_type, bay_id, mobile_unit_id
    FROM appointment_assignment
    WHERE (assignment_type = 'BAY' AND (bay_id IS NULL OR mobile_unit_id IS NOT NULL))
       OR (assignment_type = 'MOBILE_UNIT' AND (mobile_unit_id IS NULL OR bay_id IS NOT NULL))
       OR (assignment_type = 'UNASSIGNED' AND (bay_id IS NOT NULL OR mobile_unit_id IS NOT NULL));
    ```

  - **Expected outcome:** Zero violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add assignment_type column
    2. Migrate existing assignments to new schema
    3. Deploy CHECK constraint
    4. Update assignment UI for exclusive selection
  - **Migration:**

    ```sql
    -- Infer assignment type from existing data
    UPDATE appointment_assignment
    SET assignment_type = CASE
      WHEN bay_id IS NOT NULL THEN 'BAY'::assignment_type
      WHEN mobile_unit_id IS NOT NULL THEN 'MOBILE_UNIT'::assignment_type
      ELSE 'UNASSIGNED'::assignment_type
    END
    WHERE assignment_type IS NULL;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Policy:** Assignment type must match work order service type
  - **Documentation:** Document assignment rules in ops manual

### DECISION-SHOPMGMT-004 — Reschedule Policy with Count Limits

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-004)
- **Decision:** Appointments can be rescheduled up to 2 times without approval. 3rd+ reschedule requires `APPROVE_RESCHEDULE` permission and a non-empty approvalReason (permission-only gating; no separate approval workflow in the initial version). Policy is enforced server-side.
- **Alternatives considered:**
  - **Option A (Chosen):** Count-based limits with permission-only approval gating
    - Pros: Simple implementation, enforceable policy, auditable
    - Cons: Lacks multi-step manager workflow
  - **Option B:** Count-based limits with explicit approval workflow (pending/approve)
    - Pros: Stronger separation of duties
    - Cons: More complex UX and data model
  - **Option C:** No reschedule limits
    - Pros: Maximum flexibility
    - Cons: Abused by chronic reschedulers, poor resource utilization
  - **Option D:** Hard limit (no rescheduling after N)
    - Pros: Strictest control
    - Cons: Inflexible for legitimate needs
- **Reasoning and evidence:**
  - Excessive rescheduling wastes scheduling resources
  - 2 reschedules balances customer service and operational efficiency
  - Permission-gated approval for exceptions ensures accountability without workflow complexity
  - Industry pattern: policies use thresholds with escalation
- **Architectural implications:**
  - **Components affected:**
    - Appointment service: Tracks reschedule count
    - Reschedule API: Enforces limits and permission-only approval gating
  - **Database schema (minimum):**

    ```sql
    ALTER TABLE appointment
    ADD COLUMN reschedule_count INTEGER NOT NULL DEFAULT 0;
    ```

  - **Reschedule logic (permission-only gating):**

    ```java
    @Transactional
    public RescheduleResponse reschedule(RescheduleRequest request) {
        Appointment appointment = appointmentRepo.findById(request.getAppointmentId())
            .orElseThrow(() -> new NotFoundException("Appointment not found"));
        
        int currentCount = appointment.getRescheduleCount();
        boolean requiresApproval = currentCount >= 2;

        if (requiresApproval && !currentUser.hasPermission("APPROVE_RESCHEDULE")) {
            throw new PolicyException("RESCHEDULE_APPROVAL_REQUIRED");
        }

        if (requiresApproval && (request.getApprovalReason() == null || request.getApprovalReason().trim().isEmpty())) {
            throw new ValidationException("approvalReason is required");
        }

        appointment.setScheduledStartDatetime(request.getNewStart());
        appointment.setScheduledEndDatetime(request.getNewEnd());
        appointment.setRescheduleCount(appointment.getRescheduleCount() + 1);
        appointmentRepo.save(appointment);

        auditService.recordReschedule(appointment.getId(), currentUser.getId(), request.getApprovalReason());

        return new RescheduleResponse("RESCHEDULED", appointment.getId());
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify count enforcement, approvals for 3+ reschedules
  - **Query example:**

    ```sql
    -- Find appointments exceeding reschedule limit without recorded approval
    -- NOTE: audit table name is illustrative; use the platform audit store.
    SELECT a.id, a.reschedule_count
    FROM appointment a
    WHERE a.reschedule_count > 2
      AND NOT EXISTS (
        SELECT 1
        FROM appointment_audit_event e
        WHERE e.appointment_id = a.id
          AND e.action = 'RESCHEDULE_APPROVED'
      );
    ```

  - **Expected outcome:** Zero unapproved reschedules exceeding limit
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add reschedule_count column (default 0)
    2. Backfill counts from reschedule history
    3. Deploy limit enforcement
    4. Train staff on approval process
  - **Backfill:**

    ```sql
    WITH reschedule_counts AS (
      SELECT appointment_id, COUNT(*) as count
      FROM reschedule_history
      GROUP BY appointment_id
    )
    UPDATE appointment a
    SET reschedule_count = rc.count
    FROM reschedule_counts rc
    WHERE a.id = rc.appointment_id;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain with Operations
  - **Policy:** Review reschedule threshold annually
  - **Monitoring:** Alert on chronic reschedulers (>5 reschedules)

### DECISION-SHOPMGMT-005 — Assignment Notes Maximum Length

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-005)
- **Decision:** Assignment notes are limited to 500 characters and are editable only for authorized roles. Notes edits require optimistic concurrency (version) and a non-empty notesEditReason recorded to audit.
- **Alternatives considered:**
  - **Option A (Chosen):** 500 char limit with mutable current value + audited change history
    - Pros: Supports corrections, preserves auditability
    - Cons: Requires concurrency controls
  - **Option B:** Append-only notes history (no edits)
    - Pros: Simplest audit model
    - Cons: Poor UX for corrections
  - **Option C:** No length limit
    - Pros: Maximum flexibility
    - Cons: Database bloat, performance issues
- **Reasoning and evidence:**
  - Notes are operational communication (brief updates)
  - 500 characters is ~3-4 sentences (sufficient for context)
  - Audit history is important for dispute resolution
  - Optimistic concurrency prevents silent overwrites
  - Industry standard: short notes with history (Jira comments, Slack threads)
- **Architectural implications:**
  - **Components affected:**
    - Assignment notes service: Validates length
    - Database: Stores note history
  - **Database schema (example):**

    ```sql
    ALTER TABLE appointment_assignment
    ADD COLUMN assignment_notes VARCHAR(500),
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

    CREATE TABLE assignment_note_audit (
      id UUID PRIMARY KEY,
      assignment_id UUID NOT NULL REFERENCES appointment_assignment(id),
      prior_text VARCHAR(500),
      new_text VARCHAR(500),
      edit_reason VARCHAR(200) NOT NULL,
      edited_by UUID NOT NULL,
      edited_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    ```

  - **Validation (edit path):**

    ```java
    @Transactional
    public AppointmentAssignment updateNotes(UUID assignmentId, String noteText, int expectedVersion, String editReason) {
        if (noteText != null && noteText.length() > 500) {
            throw new ValidationException("Note text exceeds maximum length of 500 characters");
        }
        if (editReason == null || editReason.trim().isEmpty()) {
            throw new ValidationException("notesEditReason is required");
        }

        AppointmentAssignment assignment = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("Assignment not found"));
        if (assignment.getVersion() != expectedVersion) {
            throw new ConcurrencyException("VERSION_MISMATCH");
        }

        auditRepo.insert(new AssignmentNoteAudit(assignmentId, assignment.getAssignmentNotes(), noteText, editReason));
        assignment.setAssignmentNotes(noteText);
        assignment.setVersion(expectedVersion + 1);
        return assignmentRepo.save(assignment);
    }
    
    }
    ```

  - **Frontend validation (edit path):**
    - Enforce max length 500
    - Require notesEditReason when saving
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all notes within length limit, edits have audit records, and version increments
  - **Query example:**

    ```sql
    -- Find notes exceeding length (should be zero)
    SELECT id, appointment_id, LENGTH(assignment_notes) as length
    FROM appointment_assignment
    WHERE assignment_notes IS NOT NULL AND LENGTH(assignment_notes) > 500;
    ```

  - **Expected outcome:** Zero violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add length constraint to database
    2. Audit existing notes for violations
    3. Truncate or split long notes
    4. Deploy validation
  - **Data cleanup:**

    ```sql
    -- Find notes exceeding limit
    SELECT id, assignment_id, LENGTH(note_text) as length, note_text
    FROM assignment_note
    WHERE LENGTH(note_text) > 500
    ORDER BY length DESC;
    -- Manual review and split/truncate as needed
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Policy:** Notes should be brief operational updates
  - **Documentation:** Document note guidelines in user manual

### DECISION-SHOPMGMT-006 — Near-Real-Time Assignment Update Delivery

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-006)
- **Decision:** Assignment changes (bay, mechanic, notes) are delivered to active UI clients within 5 seconds via Server-Sent Events (SSE). Fallback to polling (30 second interval) for browsers without SSE support or when SSE fails.
- **Alternatives considered:**
  - **Option A (Chosen):** SSE with polling fallback
    - Pros: Near-real-time for modern browsers, reliable fallback, simple server implementation
    - Cons: Not true push (long polling for old browsers)
  - **Option B:** WebSockets
    - Pros: True bidirectional push
    - Cons: More complex server infrastructure, firewall issues
  - **Option C:** Polling only
    - Pros: Universal compatibility
    - Cons: Higher latency, more server load
- **Reasoning and evidence:**
  - Dispatchers need immediate visibility of assignment changes
  - SSE is simpler than WebSockets (unidirectional updates)
  - 5-second latency acceptable for operational workflows
  - Polling fallback ensures all browsers supported
  - Industry pattern: dashboards use SSE for real-time updates
- **Architectural implications:**
  - **Components affected:**
    - Assignment service: Publishes update events
    - SSE endpoint: Streams events to clients
    - Frontend: Subscribes via EventSource or polling
  - **Event schema:**

    ```typescript
    interface AssignmentUpdateEvent {
      eventType: 'ASSIGNMENT_UPDATED';
      appointmentId: string;
      assignmentId: string;
      assignmentType: 'BAY' | 'MOBILE_UNIT' | 'UNASSIGNED';
      bayId?: string;
      mobileUnitId?: string;
      mechanicId?: string;
      updatedAt: string;
      updatedBy: string;
    }
    ```

  - **SSE endpoint:**

    ```java
    @GetMapping(value = "/api/v1/assignments/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssignmentUpdates(@RequestParam UUID facilityId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // Register emitter for this facility
        subscriptionService.register(facilityId, emitter);
        
        // Cleanup on timeout/close
        emitter.onCompletion(() -> subscriptionService.unregister(facilityId, emitter));
        emitter.onTimeout(() -> subscriptionService.unregister(facilityId, emitter));
        
        return emitter;
    }
    
    // When assignment changes
    public void notifyAssignmentUpdate(Assignment assignment) {
        AssignmentUpdateEvent event = buildEvent(assignment);
        
        // Send to all subscribed clients for this facility
        subscriptionService.broadcast(appointment.getFacilityId(), event);
    }
    ```

  - **Frontend subscription:**

    ```typescript
    // Modern browsers: SSE
    function subscribeToUpdates(facilityId: string) {
      const eventSource = new EventSource(
        `/api/v1/assignments/stream?facilityId=${facilityId}`
      );
      
      eventSource.addEventListener('ASSIGNMENT_UPDATED', (e) => {
        const update: AssignmentUpdateEvent = JSON.parse(e.data);
        handleAssignmentUpdate(update);
      });
      
      eventSource.onerror = () => {
        eventSource.close();
        // Fallback to polling
        startPolling(facilityId);
      };
    }
    
    // Fallback: polling
    function startPolling(facilityId: string) {
      const interval = setInterval(async () => {
        const updates = await api.get(`/api/v1/assignments/updates?facilityId=${facilityId}&since=${lastCheck}`);
        updates.forEach(handleAssignmentUpdate);
        lastCheck = Date.now();
      }, 30000); // 30 seconds
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify update delivery latency
  - **Monitoring:**

    ```sql
    -- Track update delivery latency
    SELECT 
      AVG(EXTRACT(EPOCH FROM (delivered_at - updated_at))) as avg_latency_sec,
      PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (delivered_at - updated_at))) as p95_latency
    FROM assignment_update_log
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    ```

  - **Expected outcome:** P95 latency < 5 seconds
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Deploy SSE endpoint
    2. Update frontend to use SSE (feature flag)
    3. Monitor latency and client support
    4. Enable by default after validation
  - **Monitoring:** Track SSE connection count and failures
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain with Frontend team
  - **Monitoring:** Alert on SSE endpoint errors or high latency
  - **SLA:** 95% of updates delivered within 5 seconds

### DECISION-SHOPMGMT-007 — Conflict Override Audit Trail

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-007)
- **Decision:** All conflict overrides create immutable audit records with who/when/why. Audit records include conflict details, override reason, approver, and outcome (accepted/rejected). Retained for 2 years minimum.
- **Alternatives considered:**
  - **Option A (Chosen):** Immutable audit records with 2-year retention
    - Pros: Complete history, supports compliance, accountability
    - Cons: Storage overhead
  - **Option B:** Audit logs without structured data
    - Pros: Simpler
    - Cons: Cannot query/analyze, poor for reporting
  - **Option C:** No audit (trust managers)
    - Pros: No overhead
    - Cons: No accountability, compliance risk
- **Reasoning and evidence:**
  - Conflict overrides are business risk (double-booking, overtime)
  - Audit trail supports accountability and pattern analysis
  - Compliance may require override documentation
  - Structured data enables reporting (who overrides most, why)
  - Industry standard: override actions are always audited
- **Architectural implications:**
  - **Components affected:**
    - Override service: Creates audit records
    - Audit UI: Displays override history
  - **Database schema** *(superseded, CAP-326 / spec D18.3: the separate audit table below
    is retired. `conflict_override` itself is the immutable record — append-only by
    repository design, `overridden_by`, `override_reason`, `approved_by`, `approved_at`,
    `created_at`, one row per conflict — and the reporting queries join it directly. The
    original design is kept here as the decision's history, not as schema to build.)*:

    ```sql
    CREATE TABLE conflict_override_audit (
      id UUID PRIMARY KEY,
      override_id UUID NOT NULL REFERENCES conflict_override(id),
      conflict_rule_code VARCHAR(50) NOT NULL,
      conflict_severity conflict_severity NOT NULL,
      appointment_id UUID NOT NULL,
      resource_type conflict_resource_type NOT NULL,
      resource_id UUID,
      override_reason TEXT NOT NULL,
      overridden_by UUID NOT NULL,
      approved_by UUID,
      approved_at TIMESTAMPTZ,
      outcome VARCHAR(20) NOT NULL, -- APPROVED, REJECTED
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      -- Immutable: no updates
    );
    
    CREATE INDEX idx_override_audit_time ON conflict_override_audit(created_at);
    CREATE INDEX idx_override_audit_user ON conflict_override_audit(overridden_by, created_at);
    ```

  - **Audit creation:**

    ```java
    @Transactional
    public ConflictOverride createOverride(CreateOverrideRequest request) {
        Conflict conflict = conflictService.getConflict(request.getConflictId());
        
        // Create override
        ConflictOverride override = new ConflictOverride();
        override.setConflictId(conflict.getId());
        override.setOverriddenBy(currentUser.getId());
        override.setOverrideReason(request.getReason());
        override.setApprovedBy(request.getApprovedBy()); // manager
        override.setApprovedAt(Instant.now());
        overrideRepo.save(override);
        
        // Create audit record (immutable)
        ConflictOverrideAudit audit = new ConflictOverrideAudit();
        audit.setOverrideId(override.getId());
        audit.setConflictRuleCode(conflict.getRule().getCode());
        audit.setConflictSeverity(conflict.getSeverity());
        audit.setAppointmentId(conflict.getAppointmentId());
        audit.setResourceType(conflict.getResourceType());
        audit.setResourceId(conflict.getResourceId());
        audit.setOverrideReason(request.getReason());
        audit.setOverriddenBy(currentUser.getId());
        audit.setApprovedBy(request.getApprovedBy());
        audit.setApprovedAt(Instant.now());
        audit.setOutcome("APPROVED");
        auditRepo.save(audit);
        
        return override;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify all overrides audited, audit records immutable
  - **Query example:**

    ```sql
    -- Find overrides without audit records
    SELECT co.id, co.conflict_id, co.overridden_by
    FROM conflict_override co
    LEFT JOIN conflict_override_audit coa ON coa.override_id = co.id
    WHERE coa.id IS NULL;
    
    -- Analyze override patterns
    SELECT 
      overridden_by, 
      conflict_rule_code,
      COUNT(*) as override_count
    FROM conflict_override_audit
    WHERE created_at >= NOW() - INTERVAL '90 days'
    GROUP BY overridden_by, conflict_rule_code
    ORDER BY override_count DESC
    LIMIT 20;
    ```

  - **Expected outcome:** All overrides audited, patterns reviewed
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Create audit table
    2. Deploy audit creation logic
    3. Backfill audit records for recent overrides
    4. Deploy audit reporting UI
  - **Backfill:**

    ```sql
    INSERT INTO conflict_override_audit 
      (id, override_id, conflict_rule_code, conflict_severity, appointment_id, 
       resource_type, override_reason, overridden_by, approved_by, approved_at, outcome, created_at)
    SELECT 
      gen_random_uuid(),
      co.id,
      cr.code,
      sc.severity,
      sc.appointment_id,
      cr.resource_type,
      co.override_reason,
      co.overridden_by,
      co.approved_by,
      co.approved_at,
      'APPROVED',
      co.created_at
    FROM conflict_override co
    JOIN scheduling_conflict sc ON sc.id = co.conflict_id
    JOIN conflict_rule cr ON cr.id = sc.conflict_rule_id
    WHERE co.created_at >= NOW() - INTERVAL '2 years'
      AND NOT EXISTS (
        SELECT 1 FROM conflict_override_audit coa WHERE coa.override_id = co.id
      );
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Retention:** 2-year minimum, 7-year for compliance
  - **Review cadence:** Monthly override pattern review

### DECISION-SHOPMGMT-008 — Operating Hours Constraint Enforcement

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-008)
- **Decision:** Appointments can only be scheduled within facility operating hours (retrieved from Location domain). Scheduling outside hours generates HARD conflict (blocks scheduling). Holiday closures also block scheduling.
- **Alternatives considered:**
  - **Option A (Chosen):** Strict operating hours enforcement with holiday awareness
    - Pros: Prevents invalid appointments, respects business rules
    - Cons: Requires Location domain integration
  - **Option B:** Warning only (allow outside hours)
    - Pros: Flexible for exceptions
    - Cons: Creates invalid appointments, staff confusion
  - **Option C:** No hours validation (trust users)
    - Pros: Simplest
    - Cons: Data quality issues, operational problems
- **Reasoning and evidence:**
  - Appointments outside operating hours cannot be fulfilled
  - Holiday closures must be respected (no staff available)
  - Location domain is authoritative for operating hours
  - Hard conflict prevents invalid scheduling
  - Industry standard: scheduling systems enforce facility hours
- **Architectural implications:**
  - **Components affected:**
    - Scheduling service: Validates hours
    - Location service client: Retrieves hours
  - **Hours validation:**

    ```java
    public List<Conflict> validateOperatingHours(ScheduleRequest request) {
        List<Conflict> conflicts = new ArrayList<>();
        
        // Get facility operating hours from Location domain
        OperatingHours hours = locationService.getOperatingHours(
            request.getFacilityId(),
            request.getDate()
        );
        
        // Check if date is holiday closure
        if (hours.isHolidayClosure()) {
            conflicts.add(new Conflict(
                "FACILITY_CLOSED_HOLIDAY",
                ConflictSeverity.HARD,
                ConflictResourceType.HOURS,
                String.format("Facility closed for %s", hours.getHolidayName())
            ));
            return conflicts;
        }
        
        // Check if appointment within operating hours
        LocalTime appointmentStart = request.getStartTime().toLocalTime();
        LocalTime appointmentEnd = request.getEndTime().toLocalTime();
        
        if (appointmentStart.isBefore(hours.getOpenTime()) || 
            appointmentEnd.isAfter(hours.getCloseTime())) {
            conflicts.add(new Conflict(
                "OUTSIDE_OPERATING_HOURS",
                ConflictSeverity.HARD,
                ConflictResourceType.HOURS,
                String.format("Facility hours: %s - %s", hours.getOpenTime(), hours.getCloseTime())
            ));
        }
        
        return conflicts;
    }
    ```

  - **Location service client:**

    ```java
    @FeignClient(name = "location-service")
    public interface LocationServiceClient {
        @GetMapping("/api/v1/locations/{facilityId}/operating-hours")
        OperatingHours getOperatingHours(
            @PathVariable UUID facilityId,
            @RequestParam LocalDate date
        );
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify no appointments outside operating hours
  - **Query example:**

    ```sql
    -- Find appointments outside operating hours
    SELECT a.id, a.facility_id, a.scheduled_start_datetime,
           oh.open_time, oh.close_time
    FROM appointment a
    JOIN operating_hours oh ON oh.facility_id = a.facility_id 
      AND oh.effective_date = a.scheduled_start_datetime::date
    WHERE a.scheduled_start_datetime::time < oh.open_time
       OR a.scheduled_end_datetime::time > oh.close_time;
    ```

  - **Expected outcome:** Zero violations
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Integrate with Location service
    2. Deploy hours validation (warning mode initially)
    3. Audit existing appointments
    4. Enable hard constraint
  - **Data cleanup:**

    ```sql
    -- Find and cancel appointments outside hours
    UPDATE appointment a
    SET status = 'CANCELLED'
    FROM operating_hours oh
    WHERE oh.facility_id = a.facility_id
      AND oh.effective_date = a.scheduled_start_datetime::date
      AND (a.scheduled_start_datetime::time < oh.open_time
           OR a.scheduled_end_datetime::time > oh.close_time);
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain with Location domain coordination
  - **Policy:** Operating hours are authoritative from Location
  - **Monitoring:** Alert on Location service availability issues

### DECISION-SHOPMGMT-009 — Mechanic Assignment Foreign Reference

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-009)
- **Decision:** Mechanic assignment stores mechanic ID as foreign reference to People domain. Shopmgmt does not own mechanic data. Mechanic name/photo fetched on-demand for display. Stale data acceptable (eventual consistency).
- **Alternatives considered:**
  - **Option A (Chosen):** Foreign reference with on-demand fetch
    - Pros: Single source of truth, no data duplication, always current
    - Cons: Network dependency for display
  - **Option B:** Denormalized mechanic data in Shopmgmt
    - Pros: Fast display, no network calls
    - Cons: Data staleness, synchronization overhead
  - **Option C:** Shopmgmt owns mechanic data
    - Pros: Full control
    - Cons: Violates domain boundaries, duplicate data
- **Reasoning and evidence:**
  - People domain is authoritative for mechanic data
  - Mechanic name/photo may change (denormalized data goes stale)
  - Assignment only needs mechanic ID for operational workflows
  - Display can tolerate slight latency (fetch on page load)
  - Industry pattern: microservices use foreign references
- **Architectural implications:**
  - **Components affected:**
    - Assignment service: Stores only mechanic ID
    - People service client: Fetches mechanic details
    - Frontend: Resolves mechanic data for display
  - **Database schema:**

    ```sql
    ALTER TABLE appointment_assignment
    ADD COLUMN mechanic_id UUID; -- foreign reference only, no FK constraint
    
    -- No mechanic name/photo columns
    ```

  - **Display resolution:**

    ```java
    public AssignmentView getAssignmentView(UUID assignmentId) {
        Assignment assignment = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("Assignment not found"));
        
        AssignmentView view = new AssignmentView();
        view.setId(assignment.getId());
        view.setAssignmentType(assignment.getAssignmentType());
        view.setBayId(assignment.getBayId());
        view.setMobileUnitId(assignment.getMobileUnitId());
        view.setMechanicId(assignment.getMechanicId());
        
        // Fetch mechanic details from People domain
        if (assignment.getMechanicId() != null) {
            try {
                MechanicDetails mechanic = peopleService.getMechanic(assignment.getMechanicId());
                view.setMechanicName(mechanic.getDisplayName());
                view.setMechanicPhotoUrl(mechanic.getPhotoUrl());
            } catch (Exception e) {
                log.warn("Failed to fetch mechanic details", e);
                view.setMechanicName("Unknown");
                // Graceful degradation: ID available but name unavailable
            }
        }
        
        return view;
    }
    ```

  - **Caching:**

    ```java
    @Cacheable(value = "mechanicDetails", key = "#mechanicId")
    public MechanicDetails getMechanic(UUID mechanicId) {
        return peopleServiceClient.getMechanic(mechanicId);
    }
    // Cache TTL: 5 minutes (balance freshness and performance)
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify mechanic IDs reference valid People records
  - **Query example:**

    ```sql
    -- Find assignments with invalid mechanic IDs (via API check)
    -- Note: No FK constraint, so manual validation needed
    SELECT aa.id, aa.mechanic_id, a.appointment_id
    FROM appointment_assignment aa
    JOIN appointment a ON a.id = aa.appointment_id
    WHERE aa.mechanic_id IS NOT NULL
      AND aa.mechanic_id NOT IN (
        SELECT id FROM people_service.mechanic -- cross-service query or API validation
      );
    ```

  - **Expected outcome:** Zero invalid references
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Update assignment to store only mechanic ID
    2. Remove denormalized mechanic columns
    3. Deploy People service client
    4. Update UI to fetch mechanic details
  - **Data cleanup:**

    ```sql
    -- Remove denormalized columns
    ALTER TABLE appointment_assignment
    DROP COLUMN IF EXISTS mechanic_name,
    DROP COLUMN IF EXISTS mechanic_photo_url;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain with People domain SLA dependency
  - **Monitoring:** Alert on People service availability
  - **SLA:** Mechanic details fetch < 200ms (95th percentile)

### DECISION-SHOPMGMT-010 — Assignment Status State Machine

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-010)
- **Decision:** Assignment status follows strict state machine: UNASSIGNED → ASSIGNED → (optional AWAITING_SKILL_FULFILLMENT) → IN_PROGRESS → COMPLETED. Invalid transitions rejected server-side. Status tied to appointment status.
- **Alternatives considered:**
  - **Option A (Chosen):** Strict state machine with validation
    - Pros: Predictable workflow, prevents invalid states, clear semantics
    - Cons: Less flexible for ad-hoc changes
  - **Option B:** Free-form status (any transition allowed)
    - Pros: Maximum flexibility
    - Cons: Invalid states, workflow confusion
  - **Option C:** No explicit assignment status (derive from appointment)
    - Pros: Simpler schema
    - Cons: Cannot track assignment-specific states
- **Reasoning and evidence:**
  - Assignment lifecycle has clear progression
  - Invalid transitions indicate bugs or workflow issues
  - State machine enforces business rules
  - Status helps dispatchers understand assignment progress
  - Industry standard: workflow systems use state machines
- **Architectural implications:**
  - **Components affected:**
    - Assignment service: Enforces transitions
    - State machine validator: Defines allowed transitions
  - **Database schema:**

    ```sql
    CREATE TYPE assignment_status AS ENUM (
      'UNASSIGNED',
      'ASSIGNED',
      'AWAITING_SKILL_FULFILLMENT',
      'IN_PROGRESS',
      'COMPLETED',
      'CANCELLED'
    );
    
    ALTER TABLE appointment_assignment
    ADD COLUMN status assignment_status NOT NULL DEFAULT 'ASSIGNED';
    ```

  - **State machine:**

    ```java
    private static final Map<AssignmentStatus, Set<AssignmentStatus>> ALLOWED_TRANSITIONS = Map.of(
        AssignmentStatus.UNASSIGNED, Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CANCELLED),
        AssignmentStatus.ASSIGNED, Set.of(AssignmentStatus.AWAITING_SKILL_FULFILLMENT, AssignmentStatus.IN_PROGRESS, AssignmentStatus.CANCELLED),
        AssignmentStatus.AWAITING_SKILL_FULFILLMENT, Set.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CANCELLED),
        AssignmentStatus.IN_PROGRESS, Set.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED),
        AssignmentStatus.COMPLETED, Set.of(), // terminal state
        AssignmentStatus.CANCELLED, Set.of() // terminal state
    );
    
    @Transactional
    public Assignment updateStatus(UUID assignmentId, AssignmentStatus newStatus) {
        Assignment assignment = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("Assignment not found"));
        
        AssignmentStatus currentStatus = assignment.getStatus();
        
        // Validate transition
        if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(newStatus)) {
            throw new InvalidStateTransitionException(
                String.format("Cannot transition from %s to %s", currentStatus, newStatus)
            );
        }
        
        assignment.setStatus(newStatus);
        assignmentRepo.save(assignment);
        
        // Emit event
        eventPublisher.publish(new AssignmentStatusChangedEvent(
            assignment.getId(),
            currentStatus,
            newStatus
        ));
        
        return assignment;
    }
    ```

- **Auditor-facing explanation:**
  - **What to inspect:** Verify no invalid transitions, terminal states not changed
  - **Query example:**

    ```sql
    -- Find assignments in terminal states with recent updates
    SELECT id, status, updated_at
    FROM appointment_assignment
    WHERE status IN ('COMPLETED', 'CANCELLED')
      AND updated_at > created_at + INTERVAL '1 minute'; -- should have only initial update
    ```

  - **Expected outcome:** Terminal states immutable
- **Migration & backward-compatibility notes:**
  - **Steps:**
    1. Add status column with default
    2. Backfill statuses from appointment status
    3. Deploy state machine validation
    4. Monitor for rejected transitions
  - **Backfill:**

    ```sql
    -- Infer assignment status from appointment status
    UPDATE appointment_assignment aa
    SET status = CASE
      WHEN a.status = 'COMPLETED' THEN 'COMPLETED'::assignment_status
      WHEN a.status = 'IN_PROGRESS' THEN 'IN_PROGRESS'::assignment_status
      WHEN a.status = 'CANCELLED' THEN 'CANCELLED'::assignment_status
      ELSE 'ASSIGNED'::assignment_status
    END
    FROM appointment a
    WHERE a.id = aa.appointment_id;
    ```

- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Policy:** State machine transitions are immutable without architecture review
  - **Documentation:** Document state machine in ops manual
### DECISION-SHOPMGMT-011 — API Contract Naming + Moqui Exposure Conventions

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-011)
- **Decision:** Shopmgmt APIs use stable, resource-oriented service names and must publish a contract (request/response/error shape) before UI implementation begins.
- **Alternatives considered:**
  - Option A (Chosen): Service families documented per story + consistent namespace
  - Option B: Ad-hoc service naming per feature
- **Reasoning and evidence:**
  - Prevents UI from guessing routes and reduces rework.
- **Architectural implications:**
  - Provide a single contract source (OpenAPI or equivalent Moqui service contract docs).
  - Standardize error schema across create/reschedule/assignment.
- **Auditor-facing explanation:**
  - Inspect that contracts exist for each deployed UI screen and that error codes are stable.
- **Migration & backward-compatibility notes:**
  - Introduce contract docs before first production release; version contracts if fields change.
- **Governance & owner recommendations:**
  - Owner: shopmgmt domain; contract changes require review.

### DECISION-SHOPMGMT-012 — Facility Scoping + Authorization Enforcement

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-012)
- **Decision:** All writes require explicit facilityId; all reads enforce facility ownership by deriving facilityId from appointmentId and validating access.
- **Alternatives considered:**
  - Option A (Chosen): Explicit facilityId for writes + derived for reads
  - Option B: Implicit facilityId from session for all calls
- **Reasoning and evidence:**
  - Avoids cross-facility leakage and reduces reliance on session context.
- **Architectural implications:**
  - Enforce deny-by-default checks on appointmentId and facilityId.
- **Auditor-facing explanation:**
  - Probe cross-facility access attempts should consistently 403/404 without leaking existence.
- **Migration & backward-compatibility notes:**
  - Add facilityId to request schemas for write endpoints before UI rollout.
- **Governance & owner recommendations:**
  - Facility scoping changes require security review.

### DECISION-SHOPMGMT-013 — Appointment Status Enum + UI Gating Rules

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-013)
- **Decision:** Appointment status is backend-authoritative; UI gating is advisory only. Cancelled visibility is permission-gated server-side.
- **Alternatives considered:**
  - Option A (Chosen): Backend-owned enum + backend enforcement
  - Option B: UI hardcodes allowed statuses
- **Reasoning and evidence:**
  - Prevents policy drift between UI and backend.
- **Architectural implications:**
  - Include status and allowedActions in appointment read models.
- **Auditor-facing explanation:**
  - Verify cancelled assignment visibility is restricted and audited.
- **Migration & backward-compatibility notes:**
  - Add allowedActions field if missing; preserve legacy status values if already in use.
- **Governance & owner recommendations:**
  - Status additions require updating contract docs.

### DECISION-SHOPMGMT-014 — Idempotency Model for Create/Reschedule

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-014)
- **Decision:** Create/reschedule accept clientRequestId; duplicate keys return the original success result and must not create duplicates.
- **Alternatives considered:**
  - Option A (Chosen): Body field clientRequestId with 24h retention
  - Option B: Header-only idempotency key
- **Reasoning and evidence:**
  - Improves resilience to network retries and UI double-submits.
- **Architectural implications:**
  - Store idempotency outcomes keyed by (clientRequestId, operation).
- **Auditor-facing explanation:**
  - Inspect that duplicate requests do not create duplicate appointments.
- **Migration & backward-compatibility notes:**
  - Roll out idempotency checks as additive behavior.
- **Governance & owner recommendations:**
  - Monitor idempotency hit rate to detect UX issues.

### DECISION-SHOPMGMT-015 — Timezone Semantics for Scheduling + Display

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-015)
- **Decision:** Facility timezone is the source of truth; APIs return facilityTimeZoneId and timestamps with offset.
- **Alternatives considered:**
  - Option A (Chosen): Facility timezone
  - Option B: Browser/user timezone
- **Reasoning and evidence:**
  - Scheduling is an operational facility concern and must be unambiguous.
- **Architectural implications:**
  - Persist timestamps as instants; render in facility timezone.
- **Auditor-facing explanation:**
  - Inspect that stored times align with displayed facility-local times.
- **Migration & backward-compatibility notes:**
  - Introduce facilityTimeZoneId field before UI date/time picker rollout.
- **Governance & owner recommendations:**
  - Timezone changes require explicit review.

### DECISION-SHOPMGMT-016 — Notification Toggles + Partial Success Semantics

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-016)
- **Decision:** Notification behavior is backend-owned; UI does not send notify toggles in the initial version. If notifications fail/queue, reschedule/create may still be successful and must return a partial outcome summary.
- **Alternatives considered:**
  - Option A (Chosen): Backend-owned notification policy
  - Option B: UI-controlled toggles
- **Reasoning and evidence:**
  - Prevents UI from drifting from policy and simplifies future policy changes.
- **Architectural implications:**
  - Response schema includes notificationOutcomeSummary when relevant.
- **Auditor-facing explanation:**
  - Inspect notification request logs for reschedule events; reconcile with UI partial outcomes.
- **Migration & backward-compatibility notes:**
  - Add summary fields without breaking existing clients.
- **Governance & owner recommendations:**
  - Notification policy changes require ops review.

### DECISION-SHOPMGMT-017 — Audit Visibility + PII-safe Fields for UI

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-017)
- **Decision:** Audit endpoints return redacted, permission-gated entries; customer PII and free-text notes are not shown to unauthorized roles.
- **Alternatives considered:**
  - Option A (Chosen): Redacted audit read model
  - Option B: Full raw audit payload exposure
- **Reasoning and evidence:**
  - Audit data is valuable but sensitive; least-privilege visibility is required.
- **Architectural implications:**
  - Introduce AUDIT_VIEW permission (or equivalent) and redaction rules.
- **Auditor-facing explanation:**
  - Inspect that redaction is enforced consistently and access is logged.
- **Migration & backward-compatibility notes:**
  - Add audit endpoints as read-only; evolve fields via versioning.
- **Governance & owner recommendations:**
  - Security domain reviews redaction changes.

### DECISION-SHOPMGMT-018 — Unknown Operating Window vs Closure in Capacity Reads

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-018)
- **Decision:** A date whose operating window could not be determined is reported as `UNAVAILABLE` and is a distinct domain fact from a date the shop is known to have been shut (`CLOSED`/`HOLIDAY`). Four rules follow:
  1. `UNAVAILABLE` means the operating window is **unknown** — it is not a closure, and it is not an idle bay. `CLOSED`/`HOLIDAY` mean the shop is **known** to have had no operating window that day.
  2. An unknown day consumes a job's time as an open day would. It emits no occupancy of its own, and it must never be the anchor an overrun is measured from.
  3. A degraded read may withhold a number, but may never invent one, and may never place one on a date reporting `OK`.
  4. Per-date containment: a date that degrades affects that date only. Every other date in the range answers exactly as it would have had the degraded date assembled cleanly.
- **Alternatives considered:**
  - **Option A (Chosen):** Unknown is its own fact; an unknown day silently consumes the job's remaining time and stops the carry-over walk
    - Pros: never reports a number the read cannot support; the degradation stays visible on the day it belongs to; containment is preserved
    - Cons: the minutes that day would have reported are reported nowhere — the day says `UNAVAILABLE` instead
  - **Option B:** Clamp the carried duration to an assumed operating window for the unknown day
    - Pros: every day still carries a number
    - Cons: invents the one fact the read has just declared it does not have, and puts a fabricated number on a day reporting `OK`
  - **Option C:** Decline to carry over across an unknown day at all, reporting the source day's own occupancy only
    - Pros: the most conservative arithmetic
    - Cons: also drops the far side of a job that runs through the unknown day and out the other side, so a bay genuinely held reads as free — a wrong-low number in place of a wrong-high one
  - **Option D:** Treat unknown as closed
    - Pros: one code path, no new concept
    - Cons: a closure absorbs none of a job's time and an unknown day probably absorbed all of it; collapsing them is precisely the defect recorded below
- **Reasoning and evidence:**
  - DECISION-SHOPMGMT-008 makes Location authoritative for operating hours but is silent on what it means when that authority fails to deliver. This decision covers that gap and does not weaken 008: an unknown window is not permission to schedule out of hours.
  - The distinction is load-bearing, not cosmetic. Carry-over skips a `CLOSED` day and rolls the overrun forward, because a closed day absorbed none of the job's time. It stops at an `UNAVAILABLE` day the job was running through, because that day was most likely open and absorbed the remaining time, and the read cannot say how much. Same day-status enum, opposite treatment, and the reason is a domain one.
  - Recorded failure (`durion-positivity-backend#2086`): a location open 08:00–17:00 Mon–Fri with a malformed Tuesday hours entry, and a job running Monday 15:00 → Tuesday 11:00. Tuesday degraded to `UNAVAILABLE` and was skipped, so the overrun was measured from Monday's 17:00 close — 1080 minutes — and distributed across Wednesday (540) and Thursday (540). A three-hour bay hold became two fully booked days, on days reporting `OK`, with no sign of degradation anywhere on the board.
  - That is a worse failure than the one the degradation exists to avoid, because it is not visibly degraded. Hence rule 3: a withheld number is honest, a confident wrong number is not.
  - On the dispatch board the asymmetry of rule 2 is deliberate. A bay shown free when it is held produces a promise the shop cannot keep, discovered at the counter. A bay shown busier than it is costs a booking, which is recoverable and which the dispatcher already has an override path for.
  - `durion-positivity-backend#2023` AC4 stated only that a degraded date is still **present**, marked `UNAVAILABLE`, never omitted. Containment — that the degraded date affects no other date — was an inference from that story's framing rather than an explicit criterion. Rule 4 makes it a stated rule, because #2086 is exactly what happens when it is not written down.
- **Architectural implications:**
  - **Components affected:**
    - Schedule capacity read (`pos-shop-manager`, `ScheduleCapacityServiceImpl`): day-status precedence, the carry-over walk between days
    - Any future range or eligibility read that reports per-date capacity
    - Dispatch board UI: `UNAVAILABLE` must render distinguishably from `CLOSED`/`HOLIDAY`
  - **Day status is a four-way fact, not a two-way one:**

    | Status | Meaning | Absorbs a running job's time | Emits occupancy |
    | --- | --- | --- | --- |
    | `OK` | window known and open | yes | yes |
    | `CLOSED` | known: no window configured for that day | no | no |
    | `HOLIDAY` | known: dated closure for that date | no | no |
    | `UNAVAILABLE` | unknown: the window could not be determined | yes (assumed) | no |

  - **Carry-over across an unknown day:** the walk stops at an `UNAVAILABLE` day the job's effective window was still running through, and carries nothing past it. An `UNAVAILABLE` day strictly after the job's effective end cannot have absorbed anything, so it is skipped like a closure. Direct overlap is untouched: a job that overruns into an unknown day and out the other side is still reported in full on the far side, with its real overrun still carrying from there.
  - **Containment is a property of the assembly, not of a filter.** Per-date assembly is what makes rule 4 hold; any optimisation that shares derived state between dates has to preserve it explicitly.
  - A permanently unknown day is a data-quality defect in the location's hours payload, not a steady state. It is visible, bounded to its own date, and fixed upstream in the Location domain.
- **Auditor-facing explanation:**
  - **What to inspect:** that `UNAVAILABLE` dates are reported rather than omitted; that the numbers on neighbouring `OK` dates do not move when a date degrades; that no closure is being reported as unknown or the reverse.
  - **Containment check:** read the same range twice, once against a location whose hours payload is intact and once with one date's entry malformed. Every date except the degraded one must be byte-identical.
  - **Query example:**

    ```sql
    -- Locations whose replicated hours payload cannot yield a window for some day,
    -- i.e. the upstream data defect behind an UNAVAILABLE date.
    SELECT location_id, code, operating_hours, synced_at
    FROM ext_location
    WHERE operating_hours IS NULL
       OR operating_hours::text = '[]'
       OR timezone IS NULL;
    ```

  - **Expected outcome:** zero rows in steady state; any row is an upstream Location fix, and every date it degrades is contained to itself.
- **Migration & backward-compatibility notes:**
  - No schema, event or contract change. The statuses already exist; this decision states what two of them mean and what each obliges a read to do.
  - One visible behaviour change where it was adopted: dates following a degraded date report **less** occupancy than before. A board that was reading those saturated days as real load will see them empty. That is the correction, not a regression.
  - Adopted in `durion-positivity-backend#2086` (PR #2097). Any later read that reports per-date capacity inherits these rules rather than re-deciding them.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain, with Location domain coordination — Location owns the hours that make a day knowable (DECISION-SHOPMGMT-008)
  - **Policy:** a degraded read withholds; it does not estimate. Any proposal to fill an unknown day with an assumed window amends this decision rather than implementing around it.
  - **Monitoring:** alert on a sustained rate of `UNAVAILABLE` dates for a location — it means the hours facts are not arriving, and every such date is capacity the shop cannot see.

### DECISION-SHOPMGMT-019 — Booking Horizon (How Far Ahead an Appointment May Be Booked)

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-019)
- **Decision:** An appointment may be scheduled at most a bounded number of facility-local days ahead of the moment the booking is made. The bound is **configuration, not a constant**, and its default is **180 days**. A create or reschedule whose `startAt` falls beyond the horizon is a policy failure (422 with a machine-readable code), not a syntactic one. The horizon applies to every appointment alike: the domain has no separate "placeholder" booking type today, so a loosely-held future slot is an ordinary appointment and is bounded by the same number.
- **Alternatives considered:**
  - **Option A (Chosen):** a configurable horizon with a 180-day default, enforced on write
    - Pros: a shop that books seasonal work a season ahead can raise it; every read that wants to know how far the book runs has one number to consult; the bound is enforced where the bad data would enter
    - Cons: one more setting to operate, and a deployment that never revisits it inherits a number chosen here
  - **Option B:** a hard-coded constant
    - Pros: simplest; no configuration surface
    - Cons: the right horizon genuinely differs by trade — a tyre shop and a restoration shop do not book alike — and a constant makes that a code change
  - **Option C:** no limit at all
    - Pros: nothing to enforce, nothing to explain
    - Cons: leaves every forward-looking read unbounded in principle, and lets a typo'd year sit in the book as a real appointment with a bay held in 2124
  - **Option D:** a limit per location, stored on the location record
    - Pros: the most faithful to how shops actually differ
    - Cons: Location is authoritative for hours (DECISION-SHOPMGMT-008) but has no scheduling-policy surface, and nothing today asks for per-location variation. Revisit if a tenant does.
- **Reasoning and evidence:**
  - The question was escalated from `durion-positivity-backend#2094` while fixing `durion-positivity-backend#2085`: a candidate fix there needed to know how far ahead a booking could sit and still be a job that might start today, and no such number existed anywhere in `durion/`.
  - The fix that shipped does not depend on it — it bounds itself by the set of jobs actually in progress rather than by a day count — but the question survives the fix: the next thing that reasons about the length of the book needs an answer, and an inference made twice in two places will eventually be made two different ways.
  - 180 days is a default, chosen to be comfortably longer than any normal service interval and short enough that a mistyped year fails at the point of entry. It is not a claim about any particular shop; that is what the configuration is for.
  - A horizon belongs on the **write** path. Rejecting a booking at creation gives the advisor an error they can act on with the customer in front of them, while a read-side filter would leave a real, bay-holding appointment in the database that some reads honour and others silently drop.
- **Architectural implications:**
  - **Components affected:**
    - `pos-shop-manager` appointment create and reschedule validation
    - Deployment configuration (`pos.shop-manager.*`, per the module's existing convention, with a `POS_SHOP_MANAGER_*` environment override)
  - **Three different numbers, deliberately not shared.** The booking horizon is a write policy and is unrelated to the two read bounds already in the module. Do not collapse them:

    | Bound | What it limits | Where it lives |
    | --- | --- | --- |
    | Booking horizon (this decision) | how far ahead an appointment may be **booked** | configuration, default 180 days |
    | Opening-search horizon | how far forward one availability **search** may scan | `OpeningSearchServiceImpl.MAX_HORIZON_DAYS` (30) |
    | Capacity range limit | the span of one capacity **read** | `ScheduleCapacityServiceImpl.MAX_RANGE_DAYS` (42) |

  - **Not yet implemented.** As of this decision `pos-shop-manager` validates only that `startAt` precedes `endAt`; nothing bounds how far ahead `startAt` may be. The enforcement is tracked as its own implementation issue rather than assumed.
  - Reschedule is a write too, so a reschedule that moves an appointment past the horizon is refused on the same rule.
- **Auditor-facing explanation:**
  - **What to inspect:** that no appointment is scheduled further ahead than the configured horizon allowed at the time it was booked, and that the rejection is a policy error rather than a silent truncation.
  - **Query example:**

    ```sql
    -- Two queries, because the horizon is measured from each WRITE, not from now().
    -- Both assume the 180-day default; substitute the configured value where a
    -- deployment sets one. Where the value has changed over time, neither query can
    -- see that, and a real audit needs the configuration history alongside them.

    -- (1) Never-rescheduled appointments, measured from creation.
    SELECT a.appointment_id, a.location_id, a.created_at, a.start_at,
           (a.start_at::date - a.created_at::date) AS days_ahead
    FROM appointment a
    WHERE NOT EXISTS (SELECT 1 FROM reschedule_history r
                       WHERE r.appointment_id = a.appointment_id)
      AND a.start_at::date - a.created_at::date > 180
    ORDER BY days_ahead DESC;

    -- (2) Rescheduled appointments, measured from each reschedule's own write time.
    -- Creation is the wrong baseline here: a legitimate reschedule of a year-old
    -- appointment would read as a violation against created_at.
    SELECT a.appointment_id, a.location_id, r.rescheduled_at, r.new_start_at,
           (r.new_start_at::date - r.rescheduled_at::date) AS days_ahead
    FROM appointment a
    JOIN reschedule_history r ON r.appointment_id = a.appointment_id
    WHERE r.new_start_at::date - r.rescheduled_at::date > 180
    ORDER BY days_ahead DESC;
    ```

  - **Expected outcome:** once enforcement ships, no rows beyond the horizon in force when each row was created. Rows predating enforcement are grandfathered and are evidence of the gap, not of a violation.
- **Migration & backward-compatibility notes:**
  - Existing appointments beyond the horizon are not rewritten or cancelled. The rule governs new writes; a legacy row stays valid and readable.
  - Raising the horizon is always safe. Lowering it can strand existing bookings, so a deployment that lowers it should audit with the query above first.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Policy:** the horizon is a number a deployment may set; the *existence* of a horizon is not optional. A deployment that wants effectively no limit sets a large number rather than disabling the check.
  - **Review cadence:** revisit if a tenant asks for per-location horizons (Option D), which is the one alternative this decision leaves genuinely open.

### DECISION-SHOPMGMT-020 — Work May Start Before the Planned Window

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-020)
- **Decision:** A workorder's actual `workStartedAt` may precede its appointment's planned `startAt`. This is a normal shop-floor outcome, not a data defect. Three rules follow:
  1. `workStartedAt < startAt` must never be rejected — not by validation, not by a constraint, and not by a read that quietly drops the row.
  2. Occupancy is computed from the **effective** window, whose two ends fall back **independently**: the actual start where one is known, else the planned start; the actual finish where one is known, else the planned finish. So a job that has started but not finished is held from its actual start to its *planned* finish — a running job is never an open-ended hold, and its end is never `expectedEndAt` and never synthesised from the current time. The planned window survives as the promise, for promise-versus-reality reporting.
  3. Starting early is **not** a reschedule. It must not consume a reschedule allowance, require `APPROVE_RESCHEDULE`, or demand a reason code (DECISION-SHOPMGMT-004).
- **Alternatives considered:**
  - **Option A (Chosen):** early starts are legitimate; occupancy follows the effective window
    - Pros: the board shows the bay as held when it is actually held; the shop is not punished for accurate reporting
    - Cons: planned and actual can diverge without any audit trail saying why, so "why did this start Wednesday?" is answerable only from the actuals themselves
  - **Option B:** require the planned window to be rescheduled to match before work may start
    - Pros: planned always equals actual; one window to reason about
    - Cons: rationed by DECISION-SHOPMGMT-004, so a shop that starts early twice in a week needs an approval to keep its own records straight. It converts an operational nicety into an authorization problem, and the predictable outcome is that nobody reschedules and the data rots.
  - **Option C:** reject `workStartedAt < startAt` as invalid
    - Pros: a clean-looking invariant, and the one a reviewer is most likely to propose
    - Cons: it contradicts how the shop works, and it is exactly the regression this decision exists to prevent
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2095`. The behaviour was relied on by the `durion-positivity-backend#2085` fix but written down nowhere, and the concrete risk named there is the one to keep in view: someone later adds a `workStartedAt >= startAt` validation, it looks obviously correct in isolation, and it silently re-breaks the case #2085 fixed.
  - Two documented facts already imply it, which is why this decision records rather than invents:
    - Walk-ins are a first-class intake channel and the advisor is expected to fit them into the schedule (`domains/shopmgmt/archive/shop-management-guidelines.md`). A shop that absorbs walk-ins will start booked work early whenever a bay frees up.
    - Rescheduling is rationed (DECISION-SHOPMGMT-004: two free, then `APPROVE_RESCHEDULE` plus a reason). A domain that expected the planned window to be rewritten on every early start would be penalising its most accurate shops.
  - The operational case is ordinary: a bay frees up, a customer arrives early, or the shop simply starts a job booked for later in the week. `durion-positivity-backend#2085` is the read-side consequence — a job planned Friday whose work began Wednesday was invisible to a Monday-to-Thursday capacity request, so Thursday reported a bay free that had been held since Wednesday afternoon.
  - Nothing here licenses the *reverse* inference: a planned window is still a commitment to the customer, and a habitual gap between promise and actual is a scheduling-quality problem. It is a reporting question, not a validation one.
- **Architectural implications:**
  - **Components affected:**
    - `pos-shop-manager` capacity and schedule reads (effective-window occupancy)
    - Appointment validation — must **not** grow a planned-versus-actual ordering rule
    - Reschedule counting (DECISION-SHOPMGMT-004) — an early start is not an event it counts
  - **The effective window, stated once:**

    | Known | Effective start | Effective end |
    | --- | --- | --- |
    | no actuals | planned `startAt` | planned `endAt` |
    | started, not finished | `workStartedAt` | planned `endAt` — *not* `expectedEndAt`, and never "now" |
    | started and finished | `workStartedAt` | `completedAt` |

  - A read that filters appointments by planned columns alone will miss a job that started outside its planned window. Any range read over occupancy has to admit rows by their effective window, not their planned one — this is precisely the defect `durion-positivity-backend#2085` reported.
  - The planned window is never mutated to match the actual. Both are kept; the effective window is derived, not stored.
- **Auditor-facing explanation:**
  - **What to inspect:** that early starts exist and are unremarkable, and that none of them consumed a reschedule allowance.
  - **Query example:**

    ```sql
    -- (1) Informational: jobs that began before their planned window.
    -- These are expected, not exceptions. A bare count of reschedule_history rows
    -- would prove nothing here — an appointment may be rescheduled for reasons that
    -- have nothing to do with starting early.
    SELECT a.appointment_id, a.location_id, a.start_at AS planned_start, w.work_started_at
    FROM appointment a
    JOIN work_order_appointment_mapping m ON m.appointment_id = a.appointment_id
    JOIN ext_workorder w ON w.workorder_id = m.work_order_id
    WHERE w.work_started_at < a.start_at
    ORDER BY (a.start_at - w.work_started_at) DESC;

    -- (2) The actual check: a reschedule written to make the plan match an early
    -- start. Its new window opens at the actual start, and it was recorded at or
    -- after work began. Rule 3 says this must never happen.
    SELECT a.appointment_id, r.rescheduled_at, r.previous_start_at, r.new_start_at,
           w.work_started_at
    FROM appointment a
    JOIN work_order_appointment_mapping m ON m.appointment_id = a.appointment_id
    JOIN ext_workorder w ON w.workorder_id = m.work_order_id
    JOIN reschedule_history r ON r.appointment_id = a.appointment_id
    WHERE w.work_started_at < r.previous_start_at
      AND r.rescheduled_at >= w.work_started_at
      AND r.new_start_at = w.work_started_at;
    ```

  - **Expected outcome:** query (1) returns rows, and that is healthy. Query (2) returns none; each row it does return is a reschedule that was spent recording an early start, against rule 3.
- **Migration & backward-compatibility notes:**
  - No schema or contract change. This decision forbids a future validation rather than requiring a new one.
  - A reviewer proposing a `workStartedAt >= startAt` constraint should be pointed here; the ordering is intentional and load-bearing.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain, with Workorder Execution coordination — `workStartedAt` and `completedAt` are that domain's facts, consumed here through the replica
  - **Policy:** planned and actual are two different facts and both are kept. Do not reconcile one into the other.
  - **Monitoring:** a persistent gap between planned and actual starts at one location is worth a look as a scheduling-accuracy signal — never as a validation failure.

### DECISION-SHOPMGMT-021 — Bay Eligibility Is Enforced at Submit; Placement Checks Duty Class Only

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-021)
- **Decision:** Bay eligibility is one rule, owned by `pos-shop-manager`, applied wherever a bay is chosen for a booking. Six rules follow:
  1. The opening search, appointment submit and reschedule use the **same** eligibility function. The search filters; submit and reschedule refuse.
  2. Submit and reschedule refuse with **422** (ADR-0017 §2: the state of a referenced resource), with no override:

     | Condition | Code |
     | --- | --- |
     | Resource unknown, or at another location | `SERVICE_POSITION_INVALID` |
     | Resource out of service or retired | `SERVICE_POSITION_INACTIVE` |
     | Bay does not claim a specialty operation on the appointment | `SERVICE_POSITION_NOT_EQUIPPED` |
     | Vehicle GVWR class above the bay's `maxDutyClass` | `SERVICE_POSITION_DUTY_CLASS_EXCEEDED` |

     The first two already exist in `pos-workorder` (`durion-positivity-backend#2001`); the family is shared so one condition has one name and one status across modules.
  3. Workorder placement (`pos-workorder`) keeps its site and active checks and adds **duty class only**. It never refuses on specialty capability.
  4. **Specialty is defined by the bay-type specialty map** (DECISION-LOCATION-025, spec D14 rule 1), not by the claims of the bays that are active right now. A specialty operation that no active bay at the location claims is **unbookable** there: the search answers `NO_ELIGIBLE_BAY_AT_LOCATION`, submit answers `SERVICE_POSITION_NOT_EQUIPPED`. It never falls back to general work.
  5. When the vehicle's GVWR class is unknown, the duty check is skipped at every entry point (spec D11).
  6. **Ranking belongs to the search alone** and never changes eligibility. Order: specialty bays last (D14), then time, then a *weak* best-fit tiebreak — smallest adequate `maxDutyClass` first, a null ceiling read as class 8 — then `displayOrder`, then name.
- **Consequences recorded with the decision:**
  - Wash and detail services are ordinary catalog services, added to a workorder as a line item. They are not specialty operations and are not in the map. A `WASH_DETAIL` bay takes no general work (D14), so it is never offered for appointments; placement may still put a workorder on it.
  - The near-capacity rule (`FACILITY_NEAR_CAPACITY`) divides by bays that accept general work, not by all active bays.
  - Duty class stays a maximum. Heavy bays are not reserved for heavy work; the best-fit tiebreak only nudges light work elsewhere when the times are equal.
- **Alternatives considered:**
  - **Option A (Chosen):** enforce at submit and reschedule; duty class only at placement
    - Pros: the authoritative write enforces what the search already promises; the shop floor keeps moving a vehicle between bays within one workorder
    - Cons: two entry points apply different subsets, so the rule has to be stated per entry point (this table)
  - **Option B:** enforce both axes everywhere, including placement
    - Pros: one rule, stated once
    - Cons: refuses the ordinary case of an oil change in a general bay followed by an alignment on the rack, on the same workorder
  - **Option C:** advisory everywhere, with a SOFT warning
    - Pros: never blocks
    - Cons: a lift's rated capacity and a missing rack are physical limits, not preferences; SOFT is reserved for things a manager can reasonably override (D10 skill mismatch)
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2245` (origin `durion-positivity-frontend#395`). The owner accepted the split on 2026-09-26.
  - DECISION-SHOPMGMT-011 makes submit authoritative, yet submit validated nothing about the resource: `resourceId` was an unchecked string and `resourceType` was never written. The search enforced more than the write.
  - `OpeningSearchServiceImpl.eligibleBays` derived "specialty" from the claims of active bays at the location. At a location with no alignment bay, `WHEEL-ALIGNMENT-4-WHEEL` was offered in general bays. Rule 4 closes that gap for every location, not only when a bay goes out of service.
- **Architectural implications:**
  - **Components affected:**
    - `pos-shop-manager`: appointment create and reschedule validation; `resourceType` persisted (DECISION-SHOPMGMT-003); the eligibility function shared with the search; search ranking; the near-capacity divisor
    - `pos-shop-manager`: a replica of the specialty map (`location.bay-specialty-map.updated`, DECISION-LOCATION-025)
    - `pos-workorder`: GVWR class on the vehicle replica; duty-class check in `ServicePositionServiceImpl.resolvePosition`
  - Contract chain: the appointment request gains `resourceType`; OpenAPI, SDK and the `API Artifacts Sync` workflow follow.
- **Auditor-facing explanation:**
  - **What to inspect:** no held appointment sits in a bay that could not have accepted it at booking time.
  - **Query example:**

    ```sql
    -- Held appointments on a bay whose ceiling is below the vehicle's class.
    SELECT a.appointment_id, a.resource_id, b.max_duty_class, v.gvwr_class
    FROM appointment a
    JOIN ext_bay b ON b.bay_id::text = a.resource_id
    JOIN ext_vehicle v ON v.vehicle_id = a.crm_vehicle_id
    WHERE a.resource_type = 'BAY'
      AND a.status NOT IN ('CANCELLED', 'COMPLETED', 'INVOICED')
      AND b.max_duty_class IS NOT NULL
      AND v.gvwr_class > b.max_duty_class;
    ```

  - **Expected outcome:** zero rows for appointments created after enforcement; older rows are the pre-enforcement backlog.
- **Migration & backward-compatibility notes:**
  - Pre-production: no shim. Appointments created before enforcement are not re-validated; DECISION-SHOPMGMT-022 surfaces any that sit in an ineligible bay.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain (submit, search), with Workorder Execution (placement) and Location (the specialty map)
  - **Policy:** a new eligibility condition joins the `SERVICE_POSITION_*` family and is added to every entry point in the same change.

### DECISION-SHOPMGMT-022 — A Resource Leaving Service Flags Its Booked Appointments; It Is Never Blocked

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-022)
- **Decision:** When a bay or mobile unit goes out of service, is retired, or loses an eligibility its bookings relied on, the change takes effect at once and the affected appointments are listed for rescheduling. Five rules follow:
  1. `pos-location` never blocks a status change, retype or code change because of appointments. It cannot see them (ADR-0044), and the equipment is broken whatever the system says.
  2. `pos-shop-manager` derives **affected** appointments at read time: future, held appointments whose resource is out of service, retired, missing, or no longer eligible under DECISION-SHOPMGMT-021. The schedule view flags them and the reschedule queue filters on them. Nothing is stored, so returning the resource to service clears the flag by itself.
  3. Reschedule may change the resource (`newResourceId`), re-validated under DECISION-SHOPMGMT-021.
  4. A reschedule the shop causes — reason `EQUIPMENT_ISSUE`, or the resource became unavailable — does **not** count against the customer's reschedule allowance (DECISION-SHOPMGMT-004).
  5. Planned downtime (a future-dated unavailability) is **not** in scope now. When it is added, the window is owned by `pos-location` and enforced here as a HARD conflict `BAY_UNAVAILABLE` (409, a time-window collision under ADR-0017 §2).
- **Alternatives considered:**
  - **Option A (Chosen):** allow the change and list the affected appointments
    - Pros: matches reality; reaches the advisors who own the bookings; self-healing on reactivation
    - Cons: the list is only as good as its reader — an unworked queue still strands customers
  - **Option B:** block the status change while future appointments exist
    - Pros: nothing is ever stranded silently
    - Cons: the lift is still broken; staff learn to leave broken bays marked active, which is worse
  - **Option C:** warn only, at the moment of the change
    - Pros: cheap
    - Cons: the warning reaches the person changing the status, not the advisors who must call the customers
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2245`; owner confirmed 2026-09-26, including that downtime is out of scope and shop-caused reschedules do not count.
  - Field-service platforms treat resource absence the same way: the absence is recorded and the affected work goes to a reschedule queue.
  - `RescheduleAppointmentRequest` carried only times, so a booking could not be moved off a broken bay without cancelling it. Rule 3 closes that gap.
- **Architectural implications:**
  - **Components affected:**
    - `pos-shop-manager`: schedule read (affected flag), reschedule (new resource, allowance exemption)
    - `pos-location`: none beyond publishing status and codes on the existing facts
  - Consumers keep a retired resource's replica row (DECISION-LOCATION-026) so an affected appointment still resolves to a name.
- **Auditor-facing explanation:**
  - **What to inspect:** held future appointments on resources that are not active, and whether they are being worked.
  - **Query example:**

    ```sql
    -- A retired bay keeps its replica row with active = false (DECISION-LOCATION-026);
    -- the LEFT JOIN also catches rows orphaned by the former hard delete.
    SELECT a.appointment_id, a.start_at, a.resource_id, b.name, b.active
    FROM appointment a
    LEFT JOIN ext_bay b ON b.bay_id::text = a.resource_id
    WHERE a.resource_type = 'BAY'
      AND a.start_at > now()
      AND a.status = 'SCHEDULED'
      AND (b.bay_id IS NULL OR b.active = false);
    ```

  - **Expected outcome:** rows are expected after a bay leaves service; they should drain as the queue is worked.
- **Migration & backward-compatibility notes:**
  - No stored state is added. The allowance exemption applies to reschedules recorded after the change.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain; resource status is Location's fact
  - **Monitoring:** the age of the oldest affected appointment per location is the useful signal.

### DECISION-SHOPMGMT-023 — Mobile Units Serve Their Base Location, for the Work They Claim

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-023)
- **Decision:** A mobile unit takes work from its base location only, and only operations it claims. Six rules follow:
  1. **Base location only.** Eligibility is scoped to the unit's base location, matching `pos-workorder`'s same-site placement rule. There is no cross-location dispatch; moving work to another location is a workorder transfer (shop side: DECISION-SHOPMGMT-024).
  2. **Claimed work only.** A unit may perform only the operation codes it claims. Unlike a `GENERAL_SERVICE` bay, it has no general-work default.
  3. **Coverage** (data rules in DECISION-LOCATION-027): an inactive service area contributes nothing; coverage priority is one ranking across the location's units, 1 sent first, ties broken by unit id; validity windows are evaluated in UTC.
  4. **Hours** are the base location's operating hours, holiday closures and timezone. A unit has no hours of its own.
  5. **Travel buffer:** a `FIXED_MINUTES` policy (DECISION-LOCATION-015) adds a block before and after each mobile appointment, in the same way the location's check-in and cleanup buffers do.
  6. **Distance** coverage and distance-based buffers are wanted and apply once customer addresses can be geocoded; units follow DECISION-LOCATION-028.
- **Scope now versus later:**
  - Now: the eligibility read is scoped to the base location and filters by claimed codes; every setting not yet applied is labelled "stored, not yet applied" in its `@Schema`.
  - When mobile units become schedulable: submit applies the DECISION-SHOPMGMT-021 codes to mobile units (`SERVICE_POSITION_NOT_EQUIPPED`, `SERVICE_POSITION_DUTY_CLASS_EXCEEDED` against the unit's `maxDutyClass`), and the travel buffer is applied.
- **Alternatives considered:**
  - **Option A (Chosen):** base location, claimed work, the location's hours
    - Pros: every downstream rule (hours, pricing, tax, stock) keeps a single location; no new hours data
    - Cons: a van shared across two locations must be modelled as a workorder transfer, not a dispatch
  - **Option B:** cross-location dispatch ranked by global priority
    - Pros: flexible fleet use
    - Cons: `pos-workorder` already refuses cross-site placement, so the search would offer units the write rejects
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2245`; the owner ruled on 2026-09-26 that a unit's work comes from its location, and that workorders may later become transferable between locations.
  - `MobileUnitCoverageRuleRepository.findEligibleCoverageRules` ranked across every unit and ignored the base location, so it could offer a unit that `pos-workorder` then refused with `SERVICE_POSITION_INVALID`.
- **Architectural implications:**
  - **Components affected:**
    - `pos-location`: eligibility read gains a base-location scope and operation-code filter
    - `pos-shop-manager`: the mobile-unit replica keeps `serviceCapabilityCodes` (already on `MobileUnitUpdatedV1` v2) once mobile scheduling lands
  - ADR-0044 R1: `pos-shop-manager` does not call `pos-location` synchronously; coverage and areas travel as facts when mobile scheduling is built.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain for scheduling; Location for coverage data
  - **Follow-up (decided):** workorder transfer between locations — DECISION-INVENTORY-023 to -028 (workorder side), DECISION-SHOPMGMT-024 and DECISION-SHOPMGMT-025 (shop side).

### DECISION-SHOPMGMT-024 — Workorder Transfer, Shop Side: Old Holds End, the Target Books Afresh

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-024)
- **Decision:** A workorder transfer (DECISION-INVENTORY-023 to -028, owned by `pos-workorder`) ends every hold the workorder has at the old location and creates none at the new one. Seven rules follow:
  1. **Placement is released by `pos-workorder`.** The transfer releases the workorder's bay, mobile-unit or `HOLD` position in its own transaction and leaves the normal history row (DECISION-INVENTORY-024). The transfer never places the workorder at the target, so the workorder arrives unplaced. Placement at the target is an ordinary placement at the new site, with the site, active and duty-class checks (DECISION-SHOPMGMT-021 rule 3). Shopmgmt owns no placement, so it releases nothing itself.
  2. **The linked appointment ends; it does not move.** When `pos-shop-manager` consumes `workorder.workorder.transferred`, it ends every held appointment that is linked to the workorder and not at `toLocationId`:
     - the appointment becomes `CANCELLED`, with a new reason code `WORKORDER_TRANSFERRED`
     - its cancellation notes record the target location and the transfer's `reasonCode`
     - its planned assignment becomes `CANCELLED` (DECISION-SHOPMGMT-010)
     - its `work_order_appointment_mapping` row is removed, so a later workorder status fact cannot bring it back

     An appointment counts as linked if any of these hold: the fact's `appointmentId` names it, a `work_order_appointment_mapping` row points to it, its `sourceType` is `WORKORDER` with that `sourceId`, or its `workorderLinkRef` names the workorder. Appointments never change location (DECISION-SHOPMGMT-025).
  3. **The consumer never refuses, whatever the status.** It acts on every status in `AppointmentStatus.holdingAResource()`, not only `SCHEDULED` as the cancel endpoint does. Terminal appointments are left as they are. `pos-shop-manager` cannot refuse a transfer, because it learns of the transfer only after it has happened (ADR-0044). The old location cannot serve the booking either: `pos-workorder` refuses any placement there (`requireSameSite`).
  4. **The target books a new appointment, under every rule.** If the work still needs a slot, the target advisor books it after the transfer, with the ordinary create sourced from the workorder. The target applies every submit rule, as for any other booking:
     - the booking horizon (DECISION-SHOPMGMT-019)
     - eligibility (DECISION-SHOPMGMT-021, and DECISION-SHOPMGMT-023 for mobile units once they are schedulable)
     - HARD conflicts, including hours, closures and unknown days (DECISION-SHOPMGMT-008, DECISION-SHOPMGMT-018)
     - SOFT conflicts, overridable only under the target's location scope (DECISION-SHOPMGMT-002, DECISION-SHOPMGMT-007)

     A transfer gets no priority, capacity exemption or carried-over override at the target, and the old window and resource are not carried over. A booking at the target made before the transfer is refused with `WORKORDER_AT_ANOTHER_LOCATION` (DECISION-SHOPMGMT-025 rule 4). An advisor can still check the target's openings beforehand, because the opening search is keyed on location, not on the workorder.
  5. **A transfer is neither a reschedule nor a customer cancellation.** The ended appointment uses up no reschedule allowance and passes none on (DECISION-SHOPMGMT-004). It sends the customer no cancellation notice (DECISION-SHOPMGMT-016); the confirmation of the new booking is the customer's notice. `WORKORDER_TRANSFERRED` is assigned only by the system, and the cancel endpoint refuses it with 400 `VALIDATION_ERROR`.
  6. **Mobile units: transfer is how work reaches another depot.** If a unit from another depot is better placed, it serves the job only after the workorder is transferred to the unit's base location (transfer reason `MOBILE_DEPOT`, DECISION-INVENTORY-028). From then on the job runs on that location's hours, holidays, timezone and travel buffer (DECISION-SHOPMGMT-023 rules 4–5). Shopmgmt does not suggest a transfer, rank other locations or their units, or choose the target; the advisor does.
  7. **Ownership of each step:**

     | Step | Owner | Mechanism |
     | --- | --- | --- |
     | Allow or refuse the transfer (state, time, parts, reason, permission at both ends) | `pos-workorder` | DECISION-INVENTORY-024, DECISION-INVENTORY-028 |
     | Release the old position (and technician) | `pos-workorder` | Same transaction as the transfer |
     | Announce the transfer | `pos-workorder` | `workorder.workorder.transferred` (`WorkorderTransferredV1`) on `workorder.events.v1`, through the outbox, followed by the usual `WorkorderUpdatedV1` snapshot |
     | End the old appointment(s) and planned assignment | `pos-shop-manager` | Consumer of the transfer fact, idempotent through `processed_events` |
     | Show the workorder at the target on the dashboard | `pos-shop-manager` | The existing `WorkorderEventsListener`: `ext_workorder.location_id` follows the snapshot, with no new code |
     | Book at the target | `pos-shop-manager` | Ordinary appointment create, by the target advisor |
     | Place at the target | `pos-workorder` | Ordinary placement at the new site |

- **Signals, codes and permissions:**
  - **Consumed:** `workorder.workorder.transferred`. `pos-shop-manager` acts on this explicit fact and never infers a transfer from a change of `locationId` between two snapshots. A snapshot's site can change for reasons that are not a transfer: `WorkorderEventsListener` falls back from `locationId` to `shopId`, and until DECISION-INVENTORY-023 rule 4 lands, `WorkorderServiceImpl.handleAssignmentUpdated` and `overrideOperationalContext` rewrite `locationId` without any transfer rule. Treating either as a transfer would cancel a customer's booking.
  - **Published:** nothing across modules. `pos-shop-manager` publishes no appointment facts today, and `AppointmentEventListener` only logs. The in-process `AppointmentCancelledEvent` carries the reason `WORKORDER_TRANSFERRED`, and any appointment fact added later inherits it. Shopmgmt never uses `ASSIGNMENT_UPDATED` to move a workorder between sites; `pos-workorder` drops such an input (DECISION-INVENTORY-023 rule 4).
  - **Error codes:**
    - consuming a transfer adds none
    - create adds `WORKORDER_AT_ANOTHER_LOCATION` (422, DECISION-SHOPMGMT-025)
    - the cancel endpoint answers `VALIDATION_ERROR` (400) when a caller sends the system-assigned reason
    - the transfer's own refusals are Workorder Execution's (`WORKORDER_TRANSFER_*`, DECISION-INVENTORY-024)
  - **Permissions:** no new shopmgmt permission. The transfer is guarded by `workorder:workorder:transfer` at both locations (DECISION-INVENTORY-028). Booking at the target needs `appointments:create` scoped to the target (ADR-0061). The consumer acts as `SYSTEM`.
  - **Status sync:** after a transfer the workorder keeps its id (DECISION-INVENTORY-023). Its next status facts, for example `ASSIGNED` when it is placed at the target, would otherwise reach the old appointment through `WorkorderStatusEventServiceImpl.STATUS_MAPPING` (`ASSIGNED` maps to `CHECKED_IN`) and hold the old bay again. Removing the mapping in rule 2 prevents that. As a second guard, the status sync never moves an appointment out of `CANCELLED`.
- **When a transfer may happen:** shopmgmt agrees with DECISION-INVENTORY-024 that a transfer happens only before work starts and before any time is recorded. So the ended appointment never carries actual occupancy (DECISION-SHOPMGMT-020), and no capacity read loses real work. If that rule is ever relaxed, this decision must be revisited: past-date capacity reads would drop the old bay's actual occupancy between the start and the transfer, because the cancelled appointment no longer holds the bay.
- **Alternatives considered:**
  - **Option A (Chosen):** end the old holds automatically on the fact; the target books afresh
    - Pros: the old location stops holding capacity it can no longer use, the risk `durion-positivity-backend#2258` names. The target's rules are applied by the one path that already applies all of them.
    - Cons: the customer's visit spans two appointment ids. For a few seconds of replica lag, the old location's board shows the bay held. That is the safe direction: a bay shown held when it is free costs a booking, which can be recovered (the asymmetry DECISION-SHOPMGMT-018 argues).
  - **Option B:** move the appointment to the target automatically when the fact arrives, re-validated under DECISION-SHOPMGMT-021
    - Pros: the customer keeps one booking, and nobody has to act
    - Cons: a consumer cannot refuse. At that moment the target bay may be taken, closed or ineligible, which leaves either an invalid booking or one that silently disappears. Resource ids belong to one location, so the old bay id means nothing at the target. It also contradicts DECISION-SHOPMGMT-025.
  - **Option C:** flag the old appointment as affected, as DECISION-SHOPMGMT-022 does, and let the old location's advisor cancel it
    - Pros: a person sees the change
    - Cons: the old location can no longer serve the booking, because `pos-workorder` refuses placement there, so ending it is the only valid action. Leaving it open holds capacity the location could sell. DECISION-SHOPMGMT-022's flag fits a different case, where the booking can still be served on another resource at the same location.
  - **Option D:** make `pos-workorder` refuse a transfer while a held appointment exists
    - Pros: nothing is ended automatically
    - Cons: `pos-workorder` cannot see appointments. `Workorder` carries no appointment id, and ADR-0044 forbids a synchronous check across the wall.
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2258` (Q3, and the shop side of Q1, Q2 and Q7). The owner noted in `durion-positivity-backend#2245` that workorders could become transferable instead of allowing cross-location dispatch (DECISION-SHOPMGMT-023). Owner confirmed 2026-09-26, replacing their earlier answer that the appointment moved with the workorder.
  - The two holds are separate facts in separate modules, so each is released by its owner:

    | Hold | Table | Module | Exclusivity |
    | --- | --- | --- | --- |
    | The workorder's position | `service_position_assignment` | `pos-workorder` | `workorder_open_position_uniq` |
    | The appointment's booking | `appointment` | `pos-shop-manager` | `appointment_resource_no_overlap` |

    That is why rules 1 and 2 name different owners.
  - `pos-workorder`'s `resolvePosition` refuses a bay or unit whose site differs from the workorder's (`requireSameSite`), and it forces `HOLD` onto the workorder's own site. Once the site changes, no booking at the old location can be honoured on the floor.
  - `AppointmentsServiceImpl.cancelAppointment` accepts only `SCHEDULED`. An `ASSIGNED` workorder has a `CHECKED_IN` appointment, so the consumer needs its own path (rule 3) rather than that gate.
  - `WorkorderStatusEventServiceImpl` sets the mapped appointment's status whatever its current status is. That is why rule 2 removes the mapping.
- **Architectural implications:**
  - **Components affected:**
    - `pos-shop-manager`:
      - a consumer for `workorder.workorder.transferred`, next to `WorkorderEventsListener`
      - `CancellationReasonCode.WORKORDER_TRANSFERRED`
      - the cancel endpoint refuses that code
      - a status-sync guard for `CANCELLED`
      - the workorder-site check on create (DECISION-SHOPMGMT-025)
    - `pos-workorder` and `pos-domain-events`: the transfer and `WorkorderTransferredV1` (DECISION-INVENTORY-028)
  - **No schema change in `pos-shop-manager`.** `cancellation_reason` is a varchar, so the new value needs no migration.
  - **No production writer for links.** No production code writes `work_order_appointment_mapping` today; only tests do. So the consumer also matches on the fact's `appointmentId`, on `sourceType`/`sourceId` and on `workorderLinkRef` (rule 2). The missing writer is tracked as its own implementation issue.
- **Auditor-facing explanation:**
  - **What to inspect:**
    - no held appointment is stranded at a location its workorder has left: DECISION-SHOPMGMT-025 query (2)
    - no appointment ended by a transfer has come back
  - **Query example:**

    ```sql
    -- An appointment ended by a transfer must stay cancelled.
    SELECT appointment_id, location_id, status, updated_at
    FROM appointment
    WHERE cancellation_reason = 'WORKORDER_TRANSFERRED'
      AND status <> 'CANCELLED';
    ```

  - **Expected outcome:** zero rows. Each row is an appointment that a later status fact brought back, and it may be holding a bay at a location that cannot use it.
- **Migration & backward-compatibility notes:**
  - Nothing to migrate: no transfer fact exists yet, so no appointment carries the new reason. The shop side ships with or after the Workorder Execution transfer, never before it.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain for appointments and planned assignments. Workorder Execution owns the transfer, placement and the fact. Location owns sites and resources.
  - **Policy:** a transfer never earns an exemption at the target. Any proposal for priority, reserved capacity or automatic re-booking of transferred work amends this decision.
  - **Monitoring:** track the age of the oldest held appointment at a location other than its workorder's (DECISION-SHOPMGMT-025 query (2)). Anything older than a few minutes means the transfer consumer has stalled.

### DECISION-SHOPMGMT-025 — An Appointment's Location Is Fixed; Serving Elsewhere Is Cancel and Rebook

- **Normative source:** `AGENT_GUIDE.md` (Decision ID DECISION-SHOPMGMT-025)
- **Decision:** An appointment belongs to the location it was booked at for its whole life, and no operation moves it to another location. Four rules follow:
  1. **Location is written once.** `locationId` is set at create and never changes. A reschedule changes the window and, under DECISION-SHOPMGMT-022 rule 3, the resource. It never changes the location. A `newResourceId` at another location is refused with `SERVICE_POSITION_INVALID` (422), the DECISION-SHOPMGMT-021 code for a resource at another location.
  2. **Another location means two writes.** To serve the customer elsewhere, the appointment is cancelled at its own location and a new one is booked at the other. The new booking passes that location's full submit validation (DECISION-SHOPMGMT-019, -021, -002, -008). There is no "move" or "transfer" operation for an appointment.
  3. **History stays where it happened.** The cancelled appointment keeps its audit, reschedule history, conflict records and any override approval (DECISION-SHOPMGMT-007). None of it carries over, and the new booking starts with an empty reschedule history (DECISION-SHOPMGMT-004).
  4. **A workorder's appointment is booked at the workorder's site.** When `sourceType` is `WORKORDER`, create refuses with 422 `WORKORDER_AT_ANOTHER_LOCATION` if the `ext_workorder` replica shows the workorder at a different location. If the replica has no row for the workorder, create goes ahead (ADR-0044 R3: refuse on a known contradiction, never on absence). An appointment therefore cannot be used to move work to another location: that takes a workorder transfer (DECISION-SHOPMGMT-024, DECISION-INVENTORY-023 rule 5).
- **Alternatives considered:**
  - **Option A (Chosen):** the location is fixed; serving elsewhere is cancel and rebook
    - Pros:
      - Each rule that depends on location is checked once, at the location it describes: facility-local hours and timezone (DECISION-SHOPMGMT-015), the horizon counted in facility-local days (DECISION-SHOPMGMT-019), capacity, location scope (DECISION-SHOPMGMT-012, ADR-0061) and manager overrides (DECISION-SHOPMGMT-007).
      - History stays readable by the location it happened at.
      - It agrees with DECISION-SHOPMGMT-001, which makes the source link immutable.
    - Cons: the customer's visit gets a new appointment id, so a reader who follows one visit across two locations joins through the workorder or the customer.
  - **Option B:** a relocate operation that changes location, resource and window in one write, re-validated at the target
    - Pros: one id per visit, and one call
    - Cons: overrides approved by a manager at the old location, the old audit and the reschedule count would all move into another location's book, where nobody approved them. Read scope comes from the appointment's location (DECISION-SHOPMGMT-012), so the old location would lose sight of its own history. Every validator would also need a rule for which location's policy applies to which part of the record.
  - **Option C:** let a reschedule pick a resource at another location
    - Pros: no new concept
    - Cons: this is cross-location dispatch under another name, which DECISION-SHOPMGMT-023 rejected
- **Reasoning and evidence:**
  - Escalated from `durion-positivity-backend#2258` (Q3, origin `durion-positivity-backend#2245`). Owner confirmed 2026-09-26, replacing their earlier answer that the appointment moved with the workorder.
  - Rule 1 records current behaviour:
    - `Appointment.locationId` is non-nullable, and no write path sets it after create.
    - `AppointmentsServiceImpl.rescheduleAppointment` takes its conflict check and its horizon zone from `appointment.getLocationId()`.
    - The rule is written down because DECISION-SHOPMGMT-022's `newResourceId` would otherwise be the one field that could cross locations.
  - `SourceEligibilityServiceImpl.validateWorkOrderEligibility` is a stub today, so nothing stops a workorder's appointment from being booked at a location the workorder is not at. That is the cross-location dispatch DECISION-SHOPMGMT-023 forbids, reached through the book instead of through the van. Rule 4 closes the gap.
  - `ConflictOverrideServiceImpl` checks location scope against `appointment.getLocationId()`. An override approves a booking in one location's book and cannot be moved to another.
- **Architectural implications:**
  - **Components affected:** `pos-shop-manager` reschedule (`newResourceId` stays at the appointment's location) and create (a workorder-sourced booking is checked against `ext_workorder.location_id`).
  - **Contract chain:** create gains one 422 code. When it is implemented, the OpenAPI, the SDK and the `API Artifacts Sync` workflow follow.
  - **Replica lag:** a booking made seconds after its workorder changed site can be refused until the workorder fact arrives. The refusal names the location the replica holds, so the advisor can tell the replica is behind and retry.
- **Auditor-facing explanation:**
  - **What to inspect:** held appointments whose resource belongs to another location, or whose workorder is now at another location.
  - **Query example:**

    ```sql
    -- (1) Held appointments on a resource belonging to another location.
    SELECT a.appointment_id, a.location_id, a.resource_type, a.resource_id,
           COALESCE(b.location_id, u.base_location_id) AS resource_location_id
    FROM appointment a
    LEFT JOIN ext_bay b ON a.resource_type = 'BAY' AND b.bay_id::text = a.resource_id
    LEFT JOIN ext_mobile_unit u ON a.resource_type = 'MOBILE_UNIT' AND u.mobile_unit_id::text = a.resource_id
    WHERE a.status IN ('SCHEDULED', 'CHECKED_IN', 'WORK_IN_PROGRESS', 'WAITING_FOR_PARTS',
                       'QUALITY_CHECK', 'READY_FOR_PICKUP', 'REOPENED')
      AND COALESCE(b.location_id, u.base_location_id) <> a.location_id;

    -- (2) Held appointments whose workorder is now at another location.
    SELECT a.appointment_id, a.location_id AS appointment_location_id,
           w.workorder_id, w.location_id AS workorder_location_id
    FROM appointment a
    JOIN ext_workorder w
      ON w.workorder_id IN (SELECT m.work_order_id FROM work_order_appointment_mapping m
                             WHERE m.appointment_id = a.appointment_id)
      OR (a.source_type = 'WORKORDER' AND a.source_id = w.workorder_id::text)
    WHERE a.status IN ('SCHEDULED', 'CHECKED_IN', 'WORK_IN_PROGRESS', 'WAITING_FOR_PARTS',
                       'QUALITY_CHECK', 'READY_FOR_PICKUP', 'REOPENED')
      AND w.location_id <> a.location_id;
    ```

  - **Expected outcome:**
    - Query (1): zero rows.
    - Query (2): zero rows, except in the seconds between a workorder transfer and its consumption (DECISION-SHOPMGMT-024). A row that persists means that consumer has stalled.
- **Migration & backward-compatibility notes:**
  - Pre-production: no shim, and existing rows are not re-validated.
  - No schema change: `location_id` is already non-nullable and never updated.
- **Governance & owner recommendations:**
  - **Owner:** Shopmgmt domain
  - **Policy:** a proposal to move appointments between locations amends this decision rather than being built around it.

## End

End of document.
