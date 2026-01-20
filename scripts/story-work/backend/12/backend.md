Title: [BACKEND] [STORY] Appointment: Create Appointment from Estimate or Order
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/12
Labels: type:story, domain:shopmgmt, status:ready-for-dev, agent:story-authoring, agent:shopmgmt

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:shopmgmt
- status:ready-for-dev

### Recommended
- agent:shopmgmt
- agent:story-authoring

---

## Story Intent

Create appointments directly from estimates or work orders with service and resource context pre-populated, validate scheduling conflicts, calculate service duration using the authoritative Work Execution service when available, and assign bays and mechanics based on skills and equipment requirements.

---

## Actors & Stakeholders

- Scheduler (Service Advisor / Dispatcher)
- Shop Management Service (system of record)
- Work Execution Service (duration & skill authority)
- People/HR Service (mechanic qualifications)
- Customer
- Mechanic
- Notification Service
- Audit Service

---

## Preconditions

1. Source exists and is eligible (Estimate in APPROVED/QUOTED or Work Order not COMPLETED/CANCELLED)
2. Source linked to customer and vehicle
3. Caller has `CREATE_APPOINTMENT` permission
4. Shop operating hours and bay inventory defined
5. WorkExec and People services available (optional for richer data)

---

## Functional Behavior (Summary)

1. Scheduler selects an estimate or work order; POS validates eligibility and non-duplication.
2. Shop Management pre-populates appointment skeleton: service description, estimated duration (WorkExec authoritative when present), required skills, eligible bay types, mobile eligibility.
3. Scheduler selects preferred date/time (today→90 days) and preferred time slots.
4. Shop Management runs conflict checks (bay, mechanic skills/availability, capacity, overlapping appointments).
5. Hard conflicts block creation unless permitted override; soft conflicts warn and allow override.
6. Assignment strategy (auto, deferred, manual) applies per facility/service; default: auto-assign bay, defer mechanic for complex services.
7. Appointment record created with immutable links to estimate/work order; emit `AppointmentCreatedFromEstimate`/`AppointmentCreatedFromWorkOrder` event to WorkExec.

---

## Resolved Decisions (from comments)

- Duration authority: **Work Execution** is authoritative for work orders; otherwise derive from estimate or service catalog. If durations differ by >20% (or 30 minutes), default to WorkExec and surface a warning. Scheduler may override within ±25% (reason required); larger overrides require `APPROVE_DURATION_OVERRIDE`.
- Skills: Model `required` vs `preferred` tags. Mechanic must possess ALL required skills and be active/on-shift to be auto-assigned. People/HR is source of truth for qualifications; shopmgmt caches a read model.
- Bay types (MVP): `GENERAL_SERVICE_BAY`, `LIFT_BAY`, `ALIGNMENT_BAY`, `TIRE_BAY`, `INSPECTION_BAY`, `MOBILE_UNIT`. Mobile eligibility controlled by service and geo constraints.
- Conflicts: Categorize Hard (non-overridable in many cases) vs Soft (warn/override). Overrides require reason and are audited.
- Assignment strategy: Configurable per facility; default is to assign bay immediately and defer mechanic for complex or long-duration work. SLA for deferred assignment: assign by prior business day end or within 2 hours for same-day.
- Cardinality: Default one-to-one estimate→appointment; multi-appointment mode allowed only when `source.allowsMultipleAppointments=true`.
- Integration: Event-driven contract to WorkExec; events are idempotent and include `eventId`, `appointmentId`, and `version`.

---

## Business Rules

- Appointment eligibility: only APPROVED/QUOTED/APPROVED work orders or estimates not already linked.
- Service duration precedence: WorkExec → Estimate defaults → Service-type standard → Scheduler override.
- Skill matching: All required skills must be present for auto-assign; otherwise appointment is created with `assignmentStatus=AWAITING_SKILL_FULFILLMENT` and emits `AppointmentNeedsSkillResolution`.
- Referential integrity: Links to estimate/work order are immutable and enforced by unique constraints for active states.
- Conflict handling: Hard blocks (no eligible bay/equipment, outside hours, hard capacity) are non-overridable; other hard conflicts (mechanic required) may be overridable with permission.

---

## Data Models (excerpt)

- AppointmentRequest: sourceType (ESTIMATE|WORK_ORDER), sourceId, preferredDateTimeOptions[], bayTypePreference, mechanicPreference, overrideConflict, overrideReason, createdBy.

- AppointmentRef: appointmentId, customerId, vehicleId, scheduledDateTime, estimatedDurationMinutes, serviceDescription, requiredSkills[], bayTypeRequired, assignedBay, assignedMechanic, appointmentStatus, estimateId, workOrderId, createdBy, createdAt.

- ConflictResult: hasConflicts, conflictDetails[], suggestedAlternatives[]

- AppointmentCreatedEvent: eventId, appointmentId, sourceId, customerId, vehicleId, scheduledDateTime, durationMinutes, assignedBay, assignedMechanic, requiredSkills, createdBy

---

## Acceptance Criteria (key)

- AC1: Create appointment from eligible estimate/work order when no conflicts: appointment created, links preserved, WorkExec notified, estimate status → SCHEDULED.
- AC2: Surface conflicts (bay/mechanic/skills/capacity) with suggested alternatives; allow override with permissions and reason.
- AC3: Auto-assignment and deferred assignment behaviors per facility policy; deferred assignments meet SLA.
- AC4: Duration authority follows WorkExec precedence; scheduling warns/disambiguates large mismatches and enforces override rules.
- AC5: If no mechanics match required skills, appointment created with assignmentStatus=AWAITING_SKILL_FULFILLMENT and event emitted for dispatcher/manager.

---

## Audit & Observability

Emit AppointmentCreationAttempted, ConflictDetected, ConflictOverridden, AppointmentCreated, EstimateStatusChanged, ReferentialLinkEstablished, AssignmentDecision, WorkExecutionNotified. Track metrics: creation success rate, conflict/override rates, deferred assignment queue time, duration estimation accuracy, estimate→appointment conversion rate.

---

## Original Story (for traceability)

Original issue content and comments retained in issue history: https://github.com/louisburroughs/durion-positivity-backend/issues/12
