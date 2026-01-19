# AGENT_GUIDE.md — shopmgmt Domain

---

## Purpose

The **shopmgmt** domain manages appointment scheduling and resource assignment within the modular POS system for automotive service shops. It is responsible for creating, rescheduling, and displaying assignments of appointments linked to estimates and work orders, ensuring operational efficiency, referential integrity, and coordination with downstream services.

This domain also supports **near-real-time visibility** of assignment changes (bay/mobile/mechanic/notes) for operational roles in the POS.

---

## Domain Boundaries

### Authoritative for
- **Appointment lifecycle** within the shop context:
  - Create appointment from **Estimate** or **Work Order**
  - Reschedule appointment (date/time changes)
  - Appointment visibility rules as they relate to scheduling/assignment (e.g., cancelled visibility)
- **Assignment representation** for an appointment:
  - Bay vs Mobile Unit vs Unassigned
  - Mechanic assignment reference (may be a foreign reference to People/HR)
  - Assignment notes (read-only by default; optional edit for authorized roles)
  - Assignment status (e.g., awaiting skill fulfillment)
- **Conflict detection** and conflict classification:
  - Hard vs Soft conflicts
  - Overridable vs non-overridable conflicts
  - Suggested alternatives (if supported)
- **Facility-scoped scheduling policy enforcement**:
  - Operating hours constraints
  - Minimum notice windows
  - Reschedule limits and approval requirements
- **Audit trail** for scheduling/assignment changes and overrides (who/why/when)

### Not responsible for
- **Work duration calculation** (delegated to Work Execution service; shopmgmt consumes/uses it)
- **Mechanic HR profile** (name/photo/certs) and qualifications source of truth (People/HR)
- **Notification delivery** (Notification service handles delivery; shopmgmt triggers/requests)
- **Financial transactions**, invoicing, parts inventory
- **UI-specific diffing** of assignment changes (backend may emit full view or delta; frontend can render generic “updated”)

### Boundary clarifications (from frontend stories)
- **Assignment display** is a first-class read model (`AssignmentView`) used by Appointment Detail and optionally list contexts.
- **Near-real-time updates** are required for assignment display; mechanism (push vs polling) is an integration concern (see Open Questions).

---

## Key Entities / Concepts

> Names below reflect domain concepts; actual Moqui entity/service names may differ. Where unknown, marked **TODO/CLARIFY**.

### Appointment
A scheduled service event, linked to exactly one source document (Estimate or Work Order) for the “create from source” flows.

Key fields (conceptual):
- `appointmentId`
- `facilityId`
- `scheduledStartDateTime` (timezone-sensitive)
- `status/state` (e.g., SCHEDULED, CONFIRMED, IN_PROGRESS, CANCELLED, COMPLETED — **CLARIFY exact enums**)
- `estimateId` or `workOrderId` (immutable link once created)
- `rescheduleCount` (for policy enforcement; **CLARIFY field name/source**)
- `version` (preferred for optimistic concurrency and change detection; **CLARIFY**)

### Assignment
Allocation of resources to an appointment.

- Exactly one of:
  - **Bay assignment** (facility bay)
  - **Mobile unit assignment**
  - **Unassigned**
- May include mechanic assignment (optional/nullable)

### AssignmentView (read model)
Frontend-facing view model for displaying assignment details on Appointment Detail.

Expected fields (from story #74; treat as contract shape):
- `appointmentId`, `facilityId`
- `assignmentType`: `BAY | MOBILE_UNIT | UNASSIGNED`
- `bay { bayId, bayNameOrNumber, locationName? }`
- `mobileUnit { mobileUnitId, mobileUnitName?, lastKnownLat?, lastKnownLon?, lastUpdatedAt? }`
- `mechanic { mechanicId?, displayName?, photoUrl? }` (may be partial)
- `assignmentNotes` (max 500 chars)
- `assignedAt`, `lastUpdatedAt`
- `version` (preferred)
- `assignmentStatus` (optional enum, e.g., `AWAITING_SKILL_FULFILLMENT`)

**Relationship notes**
- `Appointment (1) -> Assignment (0..1 current)` (historical assignments exist via audit/history)
- `Assignment (0..1) -> Mechanic (People/HR)` by `mechanicId`
- `Assignment (0..1) -> Bay (facility resource)`
- `Assignment (0..1) -> MobileUnit (fleet resource; may include GPS telemetry timestamps)`

### Conflict
Represents a scheduling/resource constraint violation.

Conflict fields (conceptual, from stories #75/#76):
- `severity`: `HARD | SOFT`
- `code` (stable identifier for UI mapping)
- `message` (safe-to-display text)
- `resourceType`: `BAY | MECHANIC | CAPACITY | HOURS | OTHER`
- `overridable` (boolean; **CLARIFY if provided**)
- `suggestedAlternatives[]` (optional)

### Audit Entry
Immutable record of sensitive actions:
- appointment created/rescheduled
- conflict override applied
- duration override applied
- assignment notes edited (if enabled)
- assignment updated (resource changes)

**PII constraints**: audit payloads displayed in UI must avoid customer PII unless explicitly allowed (**CLARIFY allowed fields**).

---

## Invariants / Business Rules

Backend is authoritative. Frontend stories introduce additional UI-facing expectations that should be backed by enforceable invariants.

### Appointment creation (from Estimate/Work Order)
- **Eligibility**:
  - Estimate must be in eligible statuses (previous guide: APPROVED/QUOTED).  
    **CLARIFY exact enum values** and whether additional statuses apply.
  - Work Order must not be COMPLETED/CANCELLED.  
    **CLARIFY exact enum values**.
- **Unique active link**:
  - A source (estimate/work order) must not be linked to another *active* appointment unless backend explicitly allows multiple (`allowsMultipleAppointments=true` or similar).  
  - Link is **immutable** once created.
- **Conflict handling**:
  - Hard conflicts block create unless:
    - conflict is marked overridable, and
    - actor has `OVERRIDE_SCHEDULING_CONFLICT`, and
    - override reason is provided (non-empty).
  - Soft conflicts warn but allow create.
- **Operating hours**:
  - Scheduling outside facility hours should be blocked or returned as a conflict/policy error.  
    **CLARIFY whether hours are returned for UI display**.
- **Idempotency**:
  - Create should support an idempotency key (`clientRequestId`) to prevent duplicate appointments on retries.  
    **TODO/CLARIFY contract (field/header name, retention window)**.

### Appointment reschedule
- **Reschedulable states**:
  - Stories mention reschedulable: `SCHEDULED`, `CONFIRMED`, `AWAITING_PARTS`.  
    **CLARIFY canonical list**.
  - Not reschedulable: `COMPLETED`, `CANCELLED` (and others).
- **Reason required**:
  - Reschedule requires a reason enum.
  - Notes required when reason = `OTHER`.
  - Notes also required when applying overrides (hard conflict override and/or policy override).
- **Reschedule limits & minimum notice**:
  - Existing guide: max 2 reschedules without manager approval; minimum notice enforced with escalation.  
  - Frontend needs a clear backend representation of “approval required” vs “not allowed”.  
    **CLARIFY how approval is represented (permission-only vs workflow)**.
- **Conflict check contract**:
  - Either:
    - explicit conflict-check endpoint returning conflicts/suggestions, or
    - conflicts returned on submit (409) with payload.
  - If backend requires a token/version from conflict check to submit, frontend must enforce it.  
    **CLARIFY**.
- **Assignment impact**:
  - Reschedule may preserve or clear assignment depending on backend rules (resource availability, policy). Backend should return outcome so UI can render it.

### Assignment display & notes
- **Visibility gating by appointment status** (UI expectation from story #74):
  - Show assignment for: `SCHEDULED`, `IN_PROGRESS`, `AWAITING_PARTS`, `READY`
  - For `CANCELLED`: hide unless user has `VIEW_CANCELLED_APPOINTMENTS`
  - Backend must still enforce authorization; frontend gating is a UX optimization only.
- **Notes constraints**:
  - `assignmentNotes` max length 500 characters (client-side validation + server-side enforcement).
  - Notes are read-only by default; editable only with `EDIT_ASSIGNMENT_NOTES` and only if backend exposes a write endpoint.
  - If notes editing uses optimistic concurrency, backend should enforce `version` and return 409 on mismatch (**CLARIFY**).
- **Mobile GPS staleness** (UI expectation; backend should provide timestamps):
  - If `assignmentType=MOBILE_UNIT` and `mobileUnit.lastUpdatedAt` exists:
    - >30 minutes: warn “Location may be stale”
    - >60 minutes: treat as “unavailable (stale location)” and de-emphasize location fields
  - Backend should not require frontend to compute business policy beyond these thresholds unless explicitly defined (**CLARIFY whether thresholds are configurable per facility**).

---

## Events / Integrations

### Emitted Events
Existing list remains, with additional specificity needed for frontend near-real-time updates:

- `AppointmentCreatedFromEstimate`
- `AppointmentCreatedFromWorkOrder`
- `AppointmentRescheduled`
- `AssignmentUpdated` (used for near-real-time UI updates)
- `ConflictDetected`
- `ConflictOverridden`
- `AppointmentCreationAttempted`
- `ReferentialLinkEstablished`
- `AssignmentDecision`
- `WorkExecutionNotified`
- `AuditLog` entries

**Event contract expectations**
- Events are idempotent and include:
  - `eventId`
  - `appointmentId`
  - `facilityId` (recommended for routing/scoping)
  - `version` and/or `lastUpdatedAt`
  - `actorId` (internal identifier; avoid PII)
- For `AssignmentUpdated`, frontend needs to know whether payload contains:
  - full `AssignmentView`, or
  - partial delta requiring a follow-up fetch  
  **CLARIFY**.

### Consumed Services
- **Work Execution Service**
  - Duration authority and skill requirements for appointment defaults
  - Receives reschedule updates for planned time changes
- **People/HR Service**
  - Mechanic identity details (displayName/photo/certs summary)
  - Qualifications/availability (used indirectly by shopmgmt)
- **Notification Service**
  - Reschedule notifications (customer/mechanic)
  - Create notifications (if any policy triggers)
  - Frontend may expose toggles; backend remains authoritative (**CLARIFY**)
- **Audit Service**
  - Immutable audit logging
  - Optional audit retrieval for UI display (**CLARIFY**)

### Real-time update mechanism (frontend requirement)
Frontend story #74 requires near-real-time assignment updates:
- Preferred: push subscription (WebSocket or SSE)
- Fallback: polling every 30 seconds while Appointment Detail is visible
- Manual refresh action always available

**CLARIFY**: exact push mechanism, channel/topic naming, and subscription identifiers.

---

## API Expectations (High-Level)

> This section remains high-level but is updated with concrete contract shapes required by the frontend stories. Exact Moqui service names/routes are **TODO/CLARIFY** until confirmed.

### 1) Create Appointment (from Estimate or Work Order)
**Intent**: Convert eligible source into an appointment with defaults, conflict visibility, and immutable linkage.

**Request (conceptual)**
- `sourceType`: `ESTIMATE | WORK_ORDER`
- `sourceId`
- `facilityId` (**CLARIFY if inferred from session**)
- `scheduledStartDateTime` (ISO-8601 with timezone)
- Optional:
  - `durationMinutes` (only if backend allows override)
  - `bayPreferenceId`, `mechanicPreferenceId` (if supported)
  - `overrideConflict`, `overrideReason`
  - `clientRequestId` (idempotency)

**Response (conceptual)**
- `appointmentId`
- `appointmentStatus`
- `assignmentStatus`
- `scheduledStartDateTime`
- Optional: assignment summary fields for confirmation screen (bay/mechanic)
- Optional: conflicts (if create embeds conflict evaluation)

**Errors**
- 400 validation (missing datetime, invalid timezone format, missing override reason)
- 403 permission/facility scope
- 409 conflict/duplicate link/concurrency (payload should include conflict details or existing appointment reference)
- 422 policy/eligibility failure (or 400 depending on conventions) — **CLARIFY**
- 5xx/503 downstream dependency failures (WorkExec/People) with degraded behavior if allowed

### 2) Reschedule Appointment
**Intent**: Change scheduled time with required reason/notes, conflict handling, audit, and notifications.

**Request (conceptual)**
- `appointmentId`
- `newScheduledDateTime`
- `rescheduleReason` enum
- `rescheduleReasonNotes` (required for OTHER and overrides)
- Optional:
  - `notifyCustomer`, `notifyMechanic` (**CLARIFY if accepted**)
  - `overrideHardConflicts`, `overrideReason`
  - `clientRequestId` (recommended)

**Response (conceptual)**
- Updated appointment schedule fields
- Assignment outcome: preserved/cleared + current assignmentStatus
- Notification outcome summary (queued/sent/failed) if available
- Audit reference/summary if available

**Errors**
- 403 permission
- 409 conflicts or version mismatch (if optimistic concurrency is used)
- 400/422 policy blocks (min notice, reschedule limit) with machine-readable codes for UI

### 3) View Assignment (Appointment Detail)
**Intent**: Read-only assignment display with near-real-time updates.

**Request**
- `appointmentId`
- Optional: `facilityId` (**CLARIFY**)

**Response**
- `AssignmentView` (see entity section)

**Errors**
- 403 facility scope / permission
- 404 not found / not accessible
- 503 unavailable (frontend should show degraded state)

### 4) Update Assignment Notes (Optional)
**Intent**: Allow authorized roles to edit assignment notes.

**Request**
- `appointmentId`
- `assignmentNotes` (<= 500 chars)
- Optional: `expectedVersion` (optimistic concurrency)
- Optional: `reason` (if required by audit policy) **CLARIFY**

**Response**
- Updated `AssignmentView` (preferred) or updated fields + new version

**Errors**
- 403 permission
- 409 version mismatch
- 400 validation

### 5) Conflict Checking (Create/Reschedule)
Two supported patterns; backend must pick one and document it:

- **Pattern A: Explicit conflict-check endpoint**
  - Returns `conflicts[]`, `suggestedAlternatives[]`, and optionally a `conflictCheckToken/version` required for submit.
- **Pattern B: Conflicts returned on submit**
  - Submit returns 409 with conflict payload; override requires resubmission with override fields.

**CLARIFY** which pattern is canonical for shopmgmt.

---

## Security / Authorization Assumptions

### RBAC permissions (scoped by facility)
Existing permissions remain, with additions/clarifications from frontend stories:

- `CREATE_APPOINTMENT`
- `RESCHEDULE_APPOINTMENT`
- `OVERRIDE_SCHEDULING_CONFLICT`
- `APPROVE_DURATION_OVERRIDE`
- `APPROVE_RESCHEDULE` (policy exceptions: min notice, reschedule count) **CLARIFY semantics**
- `VIEW_ASSIGNMENTS`
- `EDIT_ASSIGNMENT_NOTES`
- `VIEW_CANCELLED_APPOINTMENTS` (required to view assignment details for cancelled appointments; from story #74)

### Facility scoping
- All operations must be scoped to a facility.
- **CLARIFY** whether Moqui services infer `facilityId` from session/context or require explicit parameter.
- Never allow cross-facility access via predictable IDs; backend must enforce facility ownership checks on `appointmentId`.

### Data minimization / PII
- Assignment display may include mechanic displayName/photo; treat as PII-adjacent and avoid logging it.
- Audit display must not expose customer PII unless explicitly allowed (**CLARIFY**).
- Frontend logs should include only identifiers (`appointmentId`, `facilityId`, conflict codes), not names/notes.

### Concurrency & integrity
- Prefer optimistic concurrency for mutable fields exposed to UI (notes, reschedule) using `version`.
- On mismatch, return 409 with current version and/or current state to allow UI refresh.

---

## Observability (Logs / Metrics / Tracing)

### Logs (backend)
Add/ensure structured logs include:
- `appointmentId`, `facilityId`, `actorId`, `action`
- Create/reschedule attempts and outcomes
- Conflict detection results (counts by severity + codes; avoid free-text that may contain PII)
- Override usage (permission, reason presence, policy code)
- Assignment updates (resource changes; avoid mechanic name)
- Notes edits (length only; do not log note content)
- Integration call failures (WorkExec/People/Notification) with dependency name and error class

### Metrics
Recommended counters/timers (tagged by `facilityId` where cardinality is acceptable; otherwise aggregate):
- `shopmgmt_appointment_create_total{result=success|failure, reasonCode}`
- `shopmgmt_appointment_reschedule_total{result, reasonEnum}`
- `shopmgmt_conflict_detected_total{severity, resourceType, code}`
- `shopmgmt_conflict_override_total{severity=HARD, code}`
- `shopmgmt_assignment_view_load_latency_ms`
- `shopmgmt_assignment_updated_total{assignmentType}`
- `shopmgmt_notes_edit_total{result=success|conflict|denied}`
- `shopmgmt_notification_request_total{type=create|reschedule, result=queued|failed}`

### Tracing
- Propagate a correlation ID across:
  - create/reschedule request → conflict check → WorkExec/People/Notification calls → event emission
- Include `appointmentId` as a trace attribute (not as span name).
- For near-real-time updates:
  - trace event publication latency for `AssignmentUpdated`
  - if push gateway exists, trace delivery latency to subscription service (**CLARIFY architecture**)

### Frontend observability expectations (from stories)
- Log (client-side) subscription failures and polling fallback activation (no PII).
- Include `appointmentId` in frontend logs for troubleshooting.
- Propagate request/correlation headers if supported (e.g., `X-Request-Id`) **CLARIFY standard header**.

---

## Testing Guidance

### Unit Tests (backend)
- Eligibility evaluation for create from estimate/work order (status enums, already-linked rules)
- Conflict classification (hard/soft) and overridability
- Policy enforcement:
  - operating hours
  - minimum notice
  - reschedule count limit and approval gating
- Notes validation (<= 500 chars) and permission enforcement
- Cancelled appointment assignment visibility gating (`VIEW_CANCELLED_APPOINTMENTS`)
- Mobile GPS staleness computation if backend provides derived flags (or ensure timestamps are present)

### Contract / API Tests
- `AssignmentView` schema stability (fields, enums, nullability)
- Conflict payload shape for both create and reschedule flows
- 409 behavior:
  - conflict payload on submit (if Pattern B)
  - version mismatch payload for notes/reschedule (if optimistic concurrency)
- Idempotency behavior for create/reschedule when `clientRequestId` is reused (**TODO**)

### Integration Tests
- WorkExec dependency:
  - duration/skills retrieval success and degraded behavior when unavailable
- People/HR dependency:
  - mechanic identity retrieval path (embedded vs separate call) **CLARIFY**
- Notification dependency:
  - reschedule triggers notification request; failure surfaces as partial success if supported
- Event emission:
  - `AssignmentUpdated` emitted on assignment changes and notes edits (if notes are part of assignment view)
  - idempotency of events

### End-to-End Tests (system)
- Create appointment from eligible estimate/work order:
  - no conflicts
  - soft conflicts
  - hard conflicts with/without override permission
  - duplicate link scenario
- Reschedule:
  - reason OTHER requires notes
  - hard conflict override requires permission + reason
  - policy block (min notice / reschedule limit) and approval path (**CLARIFY expected behavior**)
- Assignment display:
  - unassigned/partial assignment rendering
  - cancelled appointment visibility gating
  - People/HR outage degraded mechanic display
  - near-real-time update: push event updates UI or polling detects within 30s

### Performance Tests
- Conflict check under concurrent scheduling attempts (same facility, same bay/mechanic)
- Assignment view load under high read volume (Appointment Detail usage)
- Event-driven update fanout (if WebSocket/SSE) vs polling load (30s interval)

---

## Common Pitfalls

- **Assuming service names/routes**: Moqui service/screen route conventions must be confirmed; do not hardcode guessed paths. (See Open Questions.)
- **Facility scoping leaks**: caching `AssignmentView` or appointment snapshots across facility context can expose data across shops. Ensure cache keys include `facilityId` (or disable caching).
- **Timezone drift**:
  - Submitting datetimes without explicit timezone can shift schedules.
  - Displaying in browser timezone when facility timezone is required causes operational errors. (**CLARIFY standard**.)
- **Conflict-check mismatch**:
  - Implementing a separate conflict check when backend only supports submit-time conflicts (or vice versa) leads to broken UX.
  - If backend requires a conflict-check token/version, skipping it will cause submit failures.
- **Notes edit without concurrency**:
  - Without `version`/409 handling, notes edits can silently overwrite changes.
- **Logging sensitive data**:
  - Do not log `assignmentNotes`, mechanic names, or customer details.
- **Over-polling**:
  - Poll only while the Appointment Detail screen is visible; back off on repeated failures.
- **Assuming mechanic identity is always embedded**:
  - Frontend story explicitly allows degraded mode; backend contract must be clear whether People/HR call is required.
- **Cancelled appointment visibility**:
  - Frontend gating is not sufficient; backend must enforce `VIEW_CANCELLED_APPOINTMENTS` (or equivalent) to prevent data leakage.

---

## Open Questions from Frontend Stories

Consolidated and organized from issues #74, #75, #76.

### A) Moqui API / Service Contract Names (blocking)
1. **AssignmentView load**
   - Exact Moqui-accessible endpoint/service to load `AssignmentView` by `appointmentId`
   - Request params (is `facilityId` required?)
   - Response schema (fields, enums, nullability)
2. **Mechanic identity**
   - Is mechanic identity embedded in `AssignmentView` (displayName/photoUrl), or must frontend call People/HR?
   - If separate: exact endpoint/service name and minimal response fields
3. **Create appointment**
   - Exact endpoints/services for:
     - load defaults/eligibility from estimate/work order
     - conflict check (if separate)
     - create appointment
   - Provide request/response schemas and error shapes (400/403/409/422)
4. **Reschedule appointment**
   - Exact endpoints/services for:
     - load appointment for reschedule (including eligibility, rescheduleCount, allowed reasons)
     - conflict checking (if separate)
     - submit reschedule
   - Provide request/response schemas and error shapes

### B) Real-time updates / events (blocking for near-real-time UX)
1. Push mechanism: **WebSocket vs SSE** (or none)
2. Channel/topic naming and subscription identifiers:
   - How to subscribe for a specific `appointmentId` (and/or `facilityId`)
3. `ASSIGNMENT_UPDATED` / `AssignmentUpdated` payload:
   - full `AssignmentView` vs partial delta
   - includes `version`/`lastUpdatedAt`?
4. If no push: confirm polling endpoint and recommended interval (frontend assumes 30s)

### C) Facility scoping & authorization (needs confirmation)
1. Is `facilityId` required explicitly in requests, or inferred from session/context in Moqui services?
2. Appointment status gating for cancelled appointments:
   - Does Appointment Detail already provide status for frontend gating?
   - Or should backend enforce “hide CANCELLED unless permitted” entirely?
3. Permission model clarifications:
   - Confirm `VIEW_CANCELLED_APPOINTMENTS` (or actual permission name)
   - Confirm `APPROVE_RESCHEDULE` semantics (permission-only vs workflow)

### D) Conflict-check UX contract (create + reschedule)
1. Is conflict checking required as a separate call before submit, or embedded in submit?
2. If separate:
   - does backend return a `conflictCheckToken/version` required for submit?
3. Do conflict responses include `suggestedAlternatives[]`? If so, shape (datetimes only vs resource suggestions)

### E) Notes editing scope (assignment display story)
1. Should notes editing be included now or remain read-only?
2. If included:
   - endpoint/service and payload
   - optimistic concurrency via `version` and 409 behavior
   - is an edit “reason” required?

### F) Timezone & timestamp conventions (cross-cutting)
1. What is the standard timezone for:
   - scheduling input/output (create/reschedule)
   - timestamp display (assignedAt/lastUpdatedAt)
   Options: facility timezone vs user profile timezone vs browser timezone
2. Is facility timezone returned in appointment/assignment payloads?

### G) UI route conventions (repo-specific)
1. Exact Moqui screen route conventions in this repo for:
   - Estimate Detail
   - Work Order Detail
   - Appointment Create
   - Appointment Detail  
   Include screen paths and parameter names.

### H) Notifications & audit visibility (reschedule story)
1. Notification toggles:
   - Does frontend send `notifyCustomer` / `notifyMechanic`, or is behavior backend-only?
   - Defaults and forced-on rules (e.g., within 24h)
2. Audit visibility:
   - Is there an API to retrieve audit entries for an appointment?
   - Or does reschedule response include audit metadata?
   - What fields are allowed to display (PII constraints)?

### I) Variant mapping / process (documentation/process)
1. Story tooling “Rewrite Variant conflict”:
   - Variant mapping table lacks `domain:shopmgmt`. Which variant should be used for shopmgmt stories? (**CLARIFY**)

---

*End of AGENT_GUIDE.md*
