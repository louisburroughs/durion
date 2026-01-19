# SHOPMGMT_DOMAIN_NOTES.md

## Summary

This document provides comprehensive rationale and decision logs for the Shop Management (shopmgmt) domain. Shopmgmt manages appointment scheduling, resource assignment (bays/mobile units/mechanics), conflict detection, and facility-scoped scheduling policies. Each decision includes alternatives, architectural implications, audit guidance, and governance.

## Completed items

- [x] Documented 10 key shopmgmt decisions
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
            .collect(Collectors.toList());
        
        if (!hardConflicts.isEmpty()) {
            throw new SchedulingConflictException("Hard conflicts cannot be overridden", hardConflicts);
        }
        
        // Soft conflicts require overrides
        List<Conflict> softConflicts = conflicts.stream()
            .filter(c -> c.getSeverity() == ConflictSeverity.SOFT)
            .collect(Collectors.toList());
        
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
      (gen_random_uuid(), 'FACILITY_NEAR_CAPACITY', 'SOFT', 'CAPACITY', 'Facility is at 90% capacity', true);
    ```
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
- **Decision:** Appointments can be rescheduled up to 3 times without manager approval. 4th+ reschedule requires manager approval. Reschedule count resets on work order conversion. Policy enforced server-side.
- **Alternatives considered:**
  - **Option A (Chosen):** Count-based limits with approval workflow
    - Pros: Discourages serial rescheduling, balances flexibility and control
    - Cons: Arbitrary threshold
  - **Option B:** No reschedule limits
    - Pros: Maximum flexibility
    - Cons: Abused by chronic reschedulers, poor resource utilization
  - **Option C:** Hard limit (no rescheduling after 3)
    - Pros: Strictest control
    - Cons: Inflexible for legitimate needs
- **Reasoning and evidence:**
  - Excessive rescheduling wastes scheduling resources
  - 3 reschedules balances customer service and operational efficiency
  - Manager approval for exceptions ensures accountability
  - Estimate-to-work-order conversion is new commitment (reset count)
  - Industry pattern: policies use thresholds with escalation
- **Architectural implications:**
  - **Components affected:**
    - Appointment service: Tracks reschedule count
    - Reschedule API: Enforces limits and approval workflow
  - **Database schema:**
    ```sql
    ALTER TABLE appointment
    ADD COLUMN reschedule_count INTEGER NOT NULL DEFAULT 0;
    
    CREATE TABLE reschedule_request (
      id UUID PRIMARY KEY,
      appointment_id UUID NOT NULL REFERENCES appointment(id),
      original_start TIMESTAMPTZ NOT NULL,
      original_end TIMESTAMPTZ NOT NULL,
      new_start TIMESTAMPTZ NOT NULL,
      new_end TIMESTAMPTZ NOT NULL,
      reschedule_reason TEXT,
      requested_by UUID NOT NULL,
      requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      requires_approval BOOLEAN NOT NULL,
      approved_by UUID,
      approved_at TIMESTAMPTZ,
      status VARCHAR(20) NOT NULL -- PENDING, APPROVED, REJECTED, APPLIED
    );
    ```
  - **Reschedule logic:**
    ```java
    @Transactional
    public RescheduleResponse requestReschedule(RescheduleRequest request) {
        Appointment appointment = appointmentRepo.findById(request.getAppointmentId())
            .orElseThrow(() -> new NotFoundException("Appointment not found"));
        
        int currentCount = appointment.getRescheduleCount();
        boolean requiresApproval = currentCount >= 3;
        
        RescheduleRequest reschedule = new RescheduleRequest();
        reschedule.setAppointmentId(appointment.getId());
        reschedule.setOriginalStart(appointment.getScheduledStartDatetime());
        reschedule.setOriginalEnd(appointment.getScheduledEndDatetime());
        reschedule.setNewStart(request.getNewStart());
        reschedule.setNewEnd(request.getNewEnd());
        reschedule.setRescheduleReason(request.getReason());
        reschedule.setRequestedBy(currentUser.getId());
        reschedule.setRequiresApproval(requiresApproval);
        
        if (requiresApproval) {
            reschedule.setStatus("PENDING");
            rescheduleRepo.save(reschedule);
            notificationService.notifyManager(reschedule);
            
            return new RescheduleResponse("PENDING_APPROVAL", reschedule.getId());
        } else {
            // Auto-approve and apply
            reschedule.setStatus("APPROVED");
            reschedule.setApprovedBy(currentUser.getId());
            reschedule.setApprovedAt(Instant.now());
            rescheduleRepo.save(reschedule);
            
            applyReschedule(appointment, reschedule);
            
            return new RescheduleResponse("RESCHEDULED", appointment.getId());
        }
    }
    
    private void applyReschedule(Appointment appointment, RescheduleRequest reschedule) {
        appointment.setScheduledStartDatetime(reschedule.getNewStart());
        appointment.setScheduledEndDatetime(reschedule.getNewEnd());
        appointment.setRescheduleCount(appointment.getRescheduleCount() + 1);
        appointmentRepo.save(appointment);
    }
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify count enforcement, approvals for 4+ reschedules
  - **Query example:**
    ```sql
    -- Find appointments exceeding reschedule limit without approval
    SELECT a.id, a.reschedule_count, rr.status, rr.approved_by
    FROM appointment a
    JOIN reschedule_request rr ON rr.appointment_id = a.id
    WHERE a.reschedule_count > 3
      AND rr.requires_approval = true
      AND rr.approved_by IS NULL
      AND rr.status = 'APPLIED'; -- should be zero
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
- **Decision:** Assignment notes are limited to 500 characters. Enforced on client and server. Notes are append-only (immutable after creation). New notes create new records with timestamps.
- **Alternatives considered:**
  - **Option A (Chosen):** 500 char limit with append-only history
    - Pros: Prevents abuse, maintains history, manageable storage
    - Cons: Cannot edit typos
  - **Option B:** No length limit
    - Pros: Maximum flexibility
    - Cons: Database bloat, performance issues
  - **Option C:** Mutable notes (single record)
    - Pros: Clean schema
    - Cons: Loses edit history, audit gaps
- **Reasoning and evidence:**
  - Notes are operational communication (brief updates)
  - 500 characters is ~3-4 sentences (sufficient for context)
  - Append-only preserves conversation history
  - Edit history important for dispute resolution
  - Industry standard: short notes with history (Jira comments, Slack threads)
- **Architectural implications:**
  - **Components affected:**
    - Assignment notes service: Validates length
    - Database: Stores note history
  - **Database schema:**
    ```sql
    CREATE TABLE assignment_note (
      id UUID PRIMARY KEY,
      assignment_id UUID NOT NULL REFERENCES appointment_assignment(id),
      note_text VARCHAR(500) NOT NULL,
      created_by UUID NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      -- No updated_at or update logic (immutable)
    );
    
    CREATE INDEX idx_note_assignment ON assignment_note(assignment_id, created_at);
    ```
  - **Validation:**
    ```java
    @Transactional
    public AssignmentNote addNote(UUID assignmentId, String noteText, UUID userId) {
        if (noteText == null || noteText.trim().isEmpty()) {
            throw new ValidationException("Note text is required");
        }
        
        if (noteText.length() > 500) {
            throw new ValidationException(
                "Note text exceeds maximum length of 500 characters"
            );
        }
        
        AssignmentNote note = new AssignmentNote();
        note.setAssignmentId(assignmentId);
        note.setNoteText(noteText.trim());
        note.setCreatedBy(userId);
        
        return noteRepo.save(note);
    }
    
    public List<AssignmentNote> getNotes(UUID assignmentId) {
        return noteRepo.findByAssignmentId(assignmentId)
            .stream()
            .sorted(Comparator.comparing(AssignmentNote::getCreatedAt))
            .collect(Collectors.toList());
    }
    ```
  - **Frontend validation:**
    ```typescript
    const MAX_NOTE_LENGTH = 500;
    
    function validateNote(text: string): string | null {
      if (!text || text.trim().length === 0) {
        return "Note text is required";
      }
      if (text.length > MAX_NOTE_LENGTH) {
        return `Note exceeds maximum length of ${MAX_NOTE_LENGTH} characters`;
      }
      return null;
    }
    
    <textarea 
      v-model="noteText"
      :maxlength="MAX_NOTE_LENGTH"
      @input="updateCharCount"
    />
    <div class="char-count">{{ noteText.length }} / {{ MAX_NOTE_LENGTH }}</div>
    ```
- **Auditor-facing explanation:**
  - **What to inspect:** Verify all notes within length limit, no updates to existing notes
  - **Query example:**
    ```sql
    -- Find notes exceeding length (should be zero)
    SELECT id, assignment_id, LENGTH(note_text) as length
    FROM assignment_note
    WHERE LENGTH(note_text) > 500;
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
- **Decision:** Assignment changes (bay, mechanic, notes) are delivered to active UI clients within 5 seconds via Server-Sent Events (SSE). Fallback to polling (15 second interval) for browsers without SSE support.
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
      }, 15000); // 15 seconds
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
  - **Database schema:**
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

## End

End of document.
