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

# AGENT_GUIDE.md

## Summary

The shopmgmt domain is responsible for appointment scheduling, assignment visibility, and enforcement of facility-scoped scheduling policies.
This update normalizes shopmgmt business rules into a decision-indexed format, resolves previously open questions with safe defaults,
and defines what must be treated as backend-authoritative vs UI hints.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to DOMAIN_NOTES.md
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-SHOPMGMT-001 | Appointment-source document immutability |
| DECISION-SHOPMGMT-002 | Conflict severity model (hard vs soft) |
| DECISION-SHOPMGMT-003 | Assignment type exclusivity (bay/mobile/unassigned) |
| DECISION-SHOPMGMT-004 | Reschedule policy (count limits + minimum notice) |
| DECISION-SHOPMGMT-005 | Assignment notes rules (length + mutability) |
| DECISION-SHOPMGMT-006 | Near-real-time assignment updates (SSE + fallback) |
| DECISION-SHOPMGMT-007 | Conflict override audit trail requirements |
| DECISION-SHOPMGMT-008 | Operating hours constraint enforcement |
| DECISION-SHOPMGMT-009 | Mechanic reference model (foreign reference to People) |
| DECISION-SHOPMGMT-010 | Assignment status model (state machine) |
| DECISION-SHOPMGMT-011 | API contract naming + Moqui exposure conventions |
| DECISION-SHOPMGMT-012 | Facility scoping and authorization enforcement |
| DECISION-SHOPMGMT-013 | Appointment status enum + UI gating rules |
| DECISION-SHOPMGMT-014 | Idempotency model for create/reschedule |
| DECISION-SHOPMGMT-015 | Timezone semantics for scheduling + display |
| DECISION-SHOPMGMT-016 | Notifications toggles + partial success semantics |
| DECISION-SHOPMGMT-017 | Audit visibility + PII-safe fields for UI |

## Domain Boundaries

### What shopmgmt owns (system of record)

- Appointment lifecycle within a facility:
  - Create appointment from Estimate or Work Order
  - Reschedule appointment
  - Cancel/no-show within shop policy (if supported)
- Assignment representation and visibility:
  - Bay vs mobile unit vs unassigned
  - Mechanic assignment references (identity details may be derived)
  - Assignment notes (policy + audit)
  - Assignment status (workflow state machine)
- Scheduling policy enforcement:
  - Operating hours constraints
  - Minimum notice windows
  - Reschedule limits and approval requirements
- Conflict detection and classification:
  - Hard vs soft conflicts
  - Suggested alternatives (when available)
- Audit trail for scheduling/assignment changes and overrides

### What shopmgmt does not own

- Work duration calculation and work execution workflow (Work Execution domain)
- Mechanic HR profile and qualifications system of record (People domain)
- Notification delivery implementation (Notification domain)
- Financial documents and payments (Billing/Accounting domains)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| Appointment | Scheduled service event linked to exactly one source document (estimate or work order). |
| Assignment | Current resource allocation for an appointment: bay, mobile unit, or unassigned (exclusive). |
| AssignmentView | Frontend read model for assignment display (appointmentId/facilityId/assignmentType/bay/mobile/mechanic/notes/version/timestamps). |
| Conflict | Scheduling constraint violation with severity, code, and safe message. |
| AuditEntry | Immutable record of sensitive actions (create/reschedule/override/notes-change/assignment-change). |

## Invariants / Business Rules

- Appointment must be linked to exactly one source document at creation; link is immutable.
- A source document may not have multiple active appointments unless backend explicitly supports it.
- Conflicts:
  - HARD conflicts block scheduling and are not overridable.
  - SOFT conflicts may be overridden only with explicit permission + reason and are always audited.
- Operating hours are enforced server-side; the UI must not infer hours.
- Reschedule requires:
  - reason enum
  - notes when reason is OTHER
  - notes and permission when overriding a SOFT conflict or policy exception
- Assignment notes:
  - maximum length 500 characters
  - treated as potentially sensitive; never logged client-side
- Cancelled appointment assignment visibility is permission-gated server-side.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-SHOPMGMT-001 | Appointment is created from a single immutable source. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-002 | Conflicts are classified HARD vs SOFT. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-003 | Exactly one assignment type applies at a time. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-004 | Reschedule limits and minimum notice are enforced. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-005 | Notes length and update semantics are defined. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-006 | Assignment updates are near-real-time with fallback. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-007 | Overrides require audit artifacts. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-008 | Operating-hours enforcement policy is explicit. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-009 | Mechanic identity is a foreign reference. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-010 | Assignment status transitions are constrained. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-011 | Contract naming patterns are standardized. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-012 | Facility scoping is deny-by-default. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-013 | Status enums and UI gating are defined. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-014 | Create/reschedule are idempotent by requestId. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-015 | Facility timezone is source of truth. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-016 | Notification toggles are backend-owned. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-017 | Audit UI is permission-gated and redacted. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |

## Open Questions (from source)

### Q: What are the canonical appointment status enum values?

- Answer: Use a single backend-owned enum; UI must treat values as opaque strings and rely on backend-provided allowed actions. A recommended canonical set is: SCHEDULED, CONFIRMED, IN_PROGRESS, AWAITING_PARTS, READY, COMPLETED, CANCELLED, NO_SHOW.
- Assumptions:
  - Backend returns status in appointment payloads used by Appointment Detail.
  - Backend also returns an allow-list such as allowedActions[] when needed.
- Rationale:
  - Prevents frontend hardcoding from drifting away from backend policy.
- Impact:
  - APIs: appointment detail payload must include status.
  - Tests: add contract tests validating status + allowedActions presence.
- Decision ID: DECISION-SHOPMGMT-013

### Q: What are the eligible estimate statuses for creating an appointment?

- Answer: Eligible estimate statuses are APPROVED and QUOTED. Anything else is ineligible and returns a deterministic eligibility error with a machine-readable code.
- Assumptions:
  - Estimate lifecycle is owned elsewhere; shopmgmt consumes status as input.
- Rationale:
  - Limits scheduling to estimates ready for operational commitment.
- Impact:
  - APIs: create-from-estimate returns 422 with code ESTIMATE_NOT_ELIGIBLE.
  - UI: shows blocked state and guidance.
- Decision ID: DECISION-SHOPMGMT-013

### Q: What are the eligible work order statuses for creating an appointment?

- Answer: Work orders are eligible unless they are COMPLETED or CANCELLED.
- Assumptions:
  - Work order terminal statuses are stable and backend-owned.
- Rationale:
  - Prevents scheduling work already finished or void.
- Impact:
  - APIs: create-from-workorder returns 422 with code WORK_ORDER_NOT_ELIGIBLE.
- Decision ID: DECISION-SHOPMGMT-013

### Q: Are HARD conflicts overridable?

- Answer: No. HARD conflicts are not overridable.
- Assumptions:
  - HARD conflicts represent physical impossibilities or safety constraints.
- Rationale:
  - Avoids double-booking and invalid schedules.
- Impact:
  - UI: remove “override” affordance for HARD conflicts.
  - APIs: return conflicts with severity=HARD and overridable=false.
- Decision ID: DECISION-SHOPMGMT-002

### Q: Are SOFT conflicts overridable, and what is required?

- Answer: Yes. SOFT conflicts may be overridden only when the actor has OVERRIDE_SCHEDULING_CONFLICT and provides a non-empty overrideReason; the override is always audited.
- Assumptions:
  - Permission checks are enforced server-side.
- Rationale:
  - Allows operational exceptions with accountability.
- Impact:
  - APIs: conflict override requires overrideReason.
  - Tests: ensure override path logs audit entry.
- Decision ID: DECISION-SHOPMGMT-007

### Q: Is conflict checking a separate call or returned on submit?

- Answer: Canonical contract is “conflicts returned on submit” (Pattern B). A separate pre-check may exist for UX but must not be required for correctness.
- Assumptions:
  - Backend can compute conflicts deterministically at submit time.
- Rationale:
  - Prevents token/version coupling and simplifies clients.
- Impact:
  - APIs: create/reschedule return 409 with conflicts payload.
- Decision ID: DECISION-SHOPMGMT-011

### Q: Do conflict responses include suggestedAlternatives[]? What is the shape?

- Answer: If present, suggestedAlternatives[] contains suggested time slots (startDateTime/endDateTime) only; resource suggestions are optional and must be treated as hints.
- Assumptions:
  - Alternatives are computed from facility schedule constraints.
- Rationale:
  - Keeps payload stable and avoids embedding HR/WorkExec optimization logic.
- Impact:
  - UI: render alternative times without assuming resource assignments.
- Decision ID: DECISION-SHOPMGMT-011

### Q: Is facilityId required explicitly in requests, or inferred?

- Answer: facilityId must be explicit in all write requests. Read requests may omit facilityId if backend resolves it from appointmentId and still enforces facility authorization.
- Assumptions:
  - appointmentId is globally unique, but authorization remains facility-scoped.
- Rationale:
  - Prevents ambiguity and reduces session-context coupling.
- Impact:
  - APIs: create/reschedule accept facilityId.
  - Tests: deny cross-facility access.
- Decision ID: DECISION-SHOPMGMT-012

### Q: What is the idempotency key name and retention window for create/reschedule?

- Answer: Use clientRequestId in the request body for Moqui-style services; retain idempotency keys for at least 24 hours. Duplicate requestId returns the original success response.
- Assumptions:
  - UI can persist requestId for retries after timeouts.
  - safe_to_defer: true (re-evaluate after first production learnings).
- Rationale:
  - Makes retry safe without depending on transport-level retries.
- Impact:
  - APIs: accept clientRequestId; store outcome.
  - Tests: verify duplicates do not create duplicates.
- Decision ID: DECISION-SHOPMGMT-014

### Q: What is the reschedule limit and how is approval represented?

- Answer: Allow up to 2 reschedules without approval; 3rd and beyond require APPROVE_RESCHEDULE permission and an approvalReason. There is no separate approver workflow in the initial version.
- Assumptions:
  - safe_to_defer: true (workflow may be added later).
- Rationale:
  - Keeps policy enforceable while minimizing workflow complexity.
- Impact:
  - APIs: policy errors return code RESCHEDULE_APPROVAL_REQUIRED.
  - UI: prompt for reason when approval required.
- Decision ID: DECISION-SHOPMGMT-004

### Q: Are assignment notes editable now, and what concurrency behavior applies?

- Answer: Notes are editable only for users with EDIT_ASSIGNMENT_NOTES, and updates are protected by optimistic concurrency (version; 409 on mismatch).
- Assumptions:
  - Backend includes version in AssignmentView.
  - safe_to_defer: true (may become append-only later if audit needs evolve).
- Rationale:
  - Prevents silent overwrites while keeping UI simple.
- Impact:
  - APIs: update-notes endpoint must accept expectedVersion.
  - UI: handle 409 by prompting reload.
- Decision ID: DECISION-SHOPMGMT-005

### Q: Is an edit “reason” required for notes updates?

- Answer: Yes, a notesEditReason is required when editing notes; it is stored in audit records but not shown by default in UI.
- Assumptions:
  - Reason is short free-text (<= 200 chars).
- Rationale:
  - Ensures traceability for operational notes edits.
- Impact:
  - APIs: update-notes requires notesEditReason.
  - Tests: audit entry contains reason.
- Decision ID: DECISION-SHOPMGMT-017

### Q: What is the real-time update mechanism for assignment updates?

- Answer: Preferred mechanism is Server-Sent Events (SSE) delivering AssignmentUpdated events; fallback is polling every 30 seconds while Appointment Detail is visible.
- Assumptions:
  - UI supports EventSource; fallback exists for degraded cases.
  - safe_to_defer: true (WebSocket may replace SSE later).
- Rationale:
  - SSE is simpler than WebSockets for one-way updates.
- Impact:
  - UI: implement SSE with reconnect/backoff.
  - Backend: publish events scoped by facility/appointment.
- Decision ID: DECISION-SHOPMGMT-006

### Q: What are channel/topic naming and subscription identifiers for push updates?

- Answer: Use a facility-scoped stream and filter by appointmentId client-side. Recommended topic name: shopmgmt.assignment.facility.<facilityId>.
- Assumptions:
  - Facility streams reduce topic explosion.
  - safe_to_defer: true (could move to appointment-scoped topics if needed).
- Rationale:
  - Simplifies subscription model.
- Impact:
  - Backend: provide SSE endpoint accepting facilityId.
  - UI: subscribe once per facility context.
- Decision ID: DECISION-SHOPMGMT-006

### Q: Does AssignmentUpdated deliver full AssignmentView or a delta?

- Answer: Deliver full AssignmentView.
- Assumptions:
  - Payload size is small enough for UI use.
- Rationale:
  - Avoids follow-up fetch thundering-herd.
- Impact:
  - Backend: event payload includes version/lastUpdatedAt.
  - UI: ignore out-of-order updates by version.
- Decision ID: DECISION-SHOPMGMT-006

### Q: Is mechanic identity embedded in AssignmentView or loaded from People?

- Answer: AssignmentView contains mechanicId and may optionally contain displayName/photoUrl when authorized; UI must support degraded mode and must not require People calls.
- Assumptions:
  - People domain remains SoR; shopmgmt exposes a minimal derived view.
- Rationale:
  - Keeps appointment display resilient during HR outages.
- Impact:
  - UI: show mechanicId-only fallback.
- Decision ID: DECISION-SHOPMGMT-009

### Q: What timezone is the source of truth for scheduling input/output and display?

- Answer: Facility timezone is the source of truth for scheduling. APIs accept/return ISO-8601 timestamps with offset, and also return facilityTimeZoneId so the UI can display unambiguously.
- Assumptions:
  - Facility has a configured IANA timezone ID.
  - safe_to_defer: true (could add user-preference rendering later).
- Rationale:
  - Scheduling is an operational facility concern.
- Impact:
  - APIs: include facilityTimeZoneId.
  - UI: display in facility timezone.
- Decision ID: DECISION-SHOPMGMT-015

### Q: Are notifyCustomer/notifyMechanic toggles supported for reschedule?

- Answer: Not in the initial contract. Notification behavior is backend-owned; UI may show informational text but does not send toggles.
- Assumptions:
  - safe_to_defer: true (toggles may be added later).
- Rationale:
  - Avoids UI/business policy drift.
- Impact:
  - APIs: reschedule response includes notificationOutcomeSummary if relevant.
- Decision ID: DECISION-SHOPMGMT-016

### Q: Is there an API to retrieve audit entries for an appointment, and what fields are allowed?

- Answer: Provide a read-only audit endpoint returning redacted entries (actorId, action, occurredAt, reasonCode, and identifiers). Do not return customer PII or free-text notes to unauthorized roles.
- Assumptions:
  - Audit detail visibility is permission-gated (AUDIT_VIEW).
  - safe_to_defer: true (field set may evolve).
- Rationale:
  - Audit data is high value but sensitive.
- Impact:
  - APIs: add audit list endpoint with redaction.
- Decision ID: DECISION-SHOPMGMT-017

### Q: Should policy/eligibility failures be 422 or 400?

- Answer: Use 422 for business rule/policy failures (eligibility, operating hours, reschedule limit) and 400 for syntactic validation.
- Assumptions:
  - Backend supports a machine-readable error code field.
- Rationale:
  - Improves UI messaging and analytics.
- Impact:
  - APIs: unify error shape.
- Decision ID: DECISION-SHOPMGMT-011

### Q: What is the standard correlation/request header?

- Answer: Use X-Correlation-Id as the client-generated correlation header and propagate it end-to-end.
- Assumptions:
  - A gateway/service mesh preserves the header.
- Rationale:
  - Enables tracing and log correlation without PII.
- Impact:
  - UI: set X-Correlation-Id on calls.
- Decision ID: DECISION-SHOPMGMT-011

### Q: What are the UI route conventions for Estimate Detail, Work Order Detail, and Appointment screens?

- Answer: UI routes are Moqui screen paths and must be documented per repository conventions as part of each story. This guide requires that each story includes: screen path, required parameters, and permission gates.
- Assumptions:
  - safe_to_defer: true (route names are repo-specific).
- Rationale:
  - Prevents “guessing routes” during implementation.
- Impact:
  - Stories: must include route details.
- Decision ID: DECISION-SHOPMGMT-011

### Q: Which story variant should be used when domain:shopmgmt is missing from variant mapping?

- Answer: Use the same variant as other Moqui “frontend-first domain docs” stories until domain:shopmgmt is added (recommended: the generic frontend-first Moqui variant).
- Assumptions:
  - Variant mapping is tooling-only and does not affect runtime.
- Rationale:
  - Keeps authoring flow unblocked.
- Impact:
  - Process: update variant mapping table to include domain:shopmgmt.
- Decision ID: DECISION-SHOPMGMT-011

## Todos Reconciled

- Original todo: "CLARIFY exact appointment status enums" → Resolution: Resolved (backend-owned enum + recommended set) | Decision: DECISION-SHOPMGMT-013
- Original todo: "CLARIFY rescheduleCount field name/source" → Resolution: Replace with task: TASK-SHOP-001 (confirm field in schema + expose in API)
- Original todo: "CLARIFY idempotency contract" → Resolution: Resolved with safe default (clientRequestId, 24h retention) | Decision: DECISION-SHOPMGMT-014
- Original todo: "CLARIFY conflict-check token/version" → Resolution: Resolved (submit-time conflicts; no token required) | Decision: DECISION-SHOPMGMT-011
- Original todo: "CLARIFY push mechanism" → Resolution: Resolved with safe default (SSE + polling fallback) | Decision: DECISION-SHOPMGMT-006
- Original todo: "CLARIFY facilityId inferred vs explicit" → Resolution: Resolved (explicit for writes) | Decision: DECISION-SHOPMGMT-012
- Original todo: "CLARIFY timezone standard" → Resolution: Resolved (facility timezone) | Decision: DECISION-SHOPMGMT-015
- Original todo: "CLARIFY audit visibility + PII" → Resolution: Resolved (redacted audit endpoint) | Decision: DECISION-SHOPMGMT-017

## End

End of document.
