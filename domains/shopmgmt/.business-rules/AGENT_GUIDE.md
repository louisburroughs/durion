---
type: Agent Guide
title: Shop Management Agent Guide
description: The shopmgmt domain manages appointment scheduling and resource assignment within the modular POS system for automotive service shops. It is responsible for creating, rescheduling, and displaying a...
domain: shopmgmt
tags: [domain, shopmgmt, agent-guide]
---

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
- `estimateId` or `workorderId` (immutable link once created)
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
| DECISION-SHOPMGMT-018 | Degraded-day semantics for capacity reads (unknown hours vs closed) |
| DECISION-SHOPMGMT-019 | Booking horizon (configurable, 180-day default) |
| DECISION-SHOPMGMT-020 | Work may start before the planned window |
| DECISION-SHOPMGMT-021 | Bay eligibility enforced at submit; placement checks duty class only |
| DECISION-SHOPMGMT-022 | A resource leaving service flags its booked appointments |
| DECISION-SHOPMGMT-023 | Mobile units serve their base location, for the work they claim |

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
- Capacity and schedule reads keep a **known** absence of hours (`CLOSED`/`HOLIDAY`) distinct from an **unknown**
  operating window (`UNAVAILABLE`); the two are never collapsed, and an unknown window is not permission to
  schedule out of hours.
- A date that degrades is reported, never omitted, and affects no other date's numbers.
- A degraded read may withhold a number; it may never invent one, nor place one on a date reporting `OK`.
- An appointment may be booked at most a configured number of days ahead (default 180); beyond it is a policy
  failure on write, not a read-side filter.
- A workorder's `workStartedAt` may precede the planned `startAt`. Occupancy follows the effective window
  (each end falling back independently: actual where known, planned otherwise), and starting early is
  not a reschedule.
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
| DECISION-SHOPMGMT-018 | An unknown operating window is not a closure, and a degraded date affects only itself. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-019 | How far ahead a booking may sit is configuration, defaulting to 180 days. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-020 | An early start is legitimate; occupancy follows the effective window. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-021 | Submit and reschedule enforce specialty and duty class (422); placement enforces duty class; the specialty map defines specialty. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-022 | Status changes are never blocked; affected appointments are listed; shop-caused reschedules are free. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |
| DECISION-SHOPMGMT-023 | A mobile unit takes claimed work from its base location only, on that location's hours. | [DOMAIN_NOTES.md](DOMAIN_NOTES.md) |

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

### Q: What does it mean when a date's operating window cannot be determined, and how does that differ from a closure?

- Answer: an unknown operating window is its own fact, distinct from a known closure. Four rules follow:
  - `UNAVAILABLE` means the operating window is unknown — not closed, and not an idle bay.
    `CLOSED`/`HOLIDAY` mean the shop is known to have had no operating window that day.
  - An unknown day consumes a running job's time as an open day would, emits no occupancy of its
    own, and is never the anchor an overrun is measured from.
  - A degraded read may withhold a number, but may never invent one, nor place one on a date
    reporting `OK`.
  - A date that degrades affects that date only.
- Assumptions:
  - A date is degraded because a fact is missing or malformed upstream, not because the shop was shut.
  - Location remains authoritative for operating hours (DECISION-SHOPMGMT-008); this answers only what happens when that authority fails to deliver.
- Rationale:
  - A closure absorbed none of a job's time; an unknown day most likely absorbed all of it. Treating them alike measures an overrun from the wrong day and distributes time that never elapsed.
  - A confidently wrong number on a day reporting `OK` is a worse failure than a withheld one, because nothing on the board marks it as degraded.
- Impact:
  - APIs: per-date status is a four-way fact; `UNAVAILABLE` dates are always present in a range response.
  - UI: `UNAVAILABLE` must render distinguishably from `CLOSED`/`HOLIDAY`.
- Decision ID: DECISION-SHOPMGMT-018

### Q: How far in advance may an appointment be booked?

- Answer: at most a bounded number of facility-local days ahead of the booking, the bound being deployment configuration with a default of **180 days**. A create or reschedule beyond it is a policy failure (422), not a syntactic one. There is no separate "placeholder" booking type, so the same bound governs a loosely-held future slot.
- Assumptions:
  - The horizon is enforced on the write path, so an out-of-horizon appointment never reaches the database.
  - Enforcement is not in `pos-shop-manager` yet; it is tracked as its own implementation issue.
- Rationale:
  - The right horizon differs by trade, so the number is configuration; the existence of a horizon is not optional, since an unbounded book lets a mistyped year hold a bay indefinitely.
- Impact:
  - APIs: create and reschedule gain a policy rejection with a machine-readable code.
  - Config: one value under `pos.shop-manager.*`, with the usual environment override.
- Decision ID: DECISION-SHOPMGMT-019

### Q: May a workorder's actual start precede its appointment's planned start?

- Answer: yes, and it must not be rejected. Three rules follow:
  - `workStartedAt < startAt` is a normal shop-floor outcome — a bay frees up, a customer arrives early, or
    the shop starts a job booked for later in the week.
  - Occupancy is computed from the effective window, whose ends fall back independently: the actual start
    where known else the planned start, the actual finish where known else the planned finish. A job that
    has started but not finished is therefore held to its planned finish, never open-ended. The planned
    window survives as the promise.
  - Starting early is not a reschedule and must not consume a reschedule allowance.
- Assumptions:
  - `workStartedAt`/`completedAt` are Workorder Execution facts, consumed here through the replica.
  - The planned window is retained, never rewritten to match the actual.
- Rationale:
  - Walk-ins are a first-class intake channel, and reschedules are rationed (DECISION-SHOPMGMT-004) — a domain that expected the plan to be rewritten on every early start would penalise its most accurate shops.
- Impact:
  - APIs: no validation may impose a planned-versus-actual ordering.
  - Reads: a range read must admit rows by their effective window, not their planned one.
- Decision ID: DECISION-SHOPMGMT-020

### Q: Is bay eligibility (specialty capability, duty class) enforced, and where?

- Answer: yes, at appointment submit and reschedule; only duty class at workorder placement.
  - Opening search, submit and reschedule share one eligibility function in `pos-shop-manager`. Submit and reschedule refuse with 422: `SERVICE_POSITION_INVALID` (unknown resource, or another location's), `SERVICE_POSITION_INACTIVE` (out of service or retired), `SERVICE_POSITION_NOT_EQUIPPED` (the bay does not claim a specialty operation on the appointment), `SERVICE_POSITION_DUTY_CLASS_EXCEEDED` (the vehicle's GVWR class is above the bay's `maxDutyClass`).
  - Workorder placement (`pos-workorder`) keeps its site and active checks and adds duty class only. It never refuses on specialty capability.
  - What is specialty comes from the bay-type specialty map (DECISION-LOCATION-025), not from what active bays happen to claim. A specialty operation no active bay at the location claims is unbookable there; it never falls back to general work.
  - An unknown vehicle class skips the duty check at every entry point.
  - The search ranks, the writes never do. Ranking order: specialty bays last (D14), then time, then a weak best-fit tiebreak (smallest adequate `maxDutyClass`, null read as 8), then `displayOrder`, then name. Ranking never changes eligibility.
- Assumptions:
  - The appointment records `resourceType` (`BAY` | `MOBILE_UNIT` | `UNASSIGNED`, DECISION-SHOPMGMT-003) so submit knows what it is validating.
  - Wash and detail services are ordinary catalog line items, not specialty operations; a `WASH_DETAIL` bay takes no general work and so is not offered for appointments.
- Rationale:
  - A booking reserves one bay for the whole visit, so every booked operation must be possible there; a vehicle legitimately moves between bays within one workorder, so its current bay is not a capability promise. A lift's rated capacity is a physical limit at every point.
- Impact:
  - APIs: appointment create and reschedule gain the four 422 codes and a `resourceType`; the contract chain (OpenAPI, SDK, API Artifacts Sync) follows.
  - `pos-workorder` needs the vehicle's GVWR class on its vehicle replica.
- Decision ID: DECISION-SHOPMGMT-021

### Q: What happens to booked appointments when a bay or mobile unit leaves service?

- Answer: the status change is never blocked. `pos-shop-manager` lists the affected appointments for rescheduling.
  - "Affected" is derived at read time: a future, held appointment whose resource is out of service, retired, missing, or no longer eligible for the appointment's operations. It is flagged on the schedule view and filterable as a reschedule queue. Nothing is stored, so reactivating the resource clears the flag.
  - Reschedule may move a booking to another resource (`newResourceId`), re-validated under DECISION-SHOPMGMT-021.
  - A reschedule the shop causes (reason `EQUIPMENT_ISSUE`, or the resource became unavailable) does not count against the customer's allowance (DECISION-SHOPMGMT-004).
  - Planned (future-dated) downtime is not in scope now.
- Assumptions:
  - An open workorder stays on a resource that leaves service (`durion-positivity-backend#2001`).
- Rationale:
  - A broken lift is broken whatever the system says, so blocking the change only hides the problem; a warning to whoever changed the status never reaches the advisors who own the bookings.
- Impact:
  - APIs: reschedule accepts a new resource; the schedule read carries an affected flag.
- Decision ID: DECISION-SHOPMGMT-022

### Q: Which work may a mobile unit be scheduled for?

- Answer: work from its own base location only, for operations it claims.
  - Eligibility is scoped to the unit's base location, matching `pos-workorder`'s same-site placement rule. Moving work to another location is a workorder transfer, not cross-location dispatch.
  - A unit may perform only the operation codes it claims; it has no general-work default.
  - Coverage: an inactive service area contributes nothing; coverage priority is one ranking across the location's units (1 is sent first, ties broken by unit id); validity windows are evaluated in UTC (DECISION-LOCATION-027).
  - Hours, holiday closures and timezone are the base location's. A unit has no hours of its own.
  - Travel buffer: a `FIXED_MINUTES` policy (DECISION-LOCATION-015) adds a block before and after each mobile appointment. Distance-based coverage and buffers apply once addresses can be geocoded (DECISION-LOCATION-028).
- Assumptions:
  - Enforcement lands when mobile units become schedulable; until then the unused settings are labelled "stored, not yet applied" in the API docs.
- Rationale:
  - Field-service vans work from a limited service menu out of one depot; a van dispatched outside its location's book breaks every downstream rule (hours, pricing, stock).
- Impact:
  - Submit uses the DECISION-SHOPMGMT-021 codes for mobile units once they are schedulable.
- Decision ID: DECISION-SHOPMGMT-023

## Todos Reconciled

- Original todo: "CLARIFY exact appointment status enums" → Resolution: Resolved (backend-owned enum + recommended set) | Decision: DECISION-SHOPMGMT-013
- Original todo: "CLARIFY rescheduleCount field name/source" → Resolution: Replace with task: TASK-SHOP-001 (confirm field in schema + expose in API)
- Original todo: "CLARIFY idempotency contract" → Resolution: Resolved with safe default (clientRequestId, 24h retention) | Decision: DECISION-SHOPMGMT-014
- Original todo: "CLARIFY conflict-check token/version" → Resolution: Resolved (submit-time conflicts; no token required) | Decision: DECISION-SHOPMGMT-011
- Original todo: "CLARIFY push mechanism" → Resolution: Resolved with safe default (SSE + polling fallback) | Decision: DECISION-SHOPMGMT-006
- Original todo: "CLARIFY facilityId inferred vs explicit" → Resolution: Resolved (explicit for writes) | Decision: DECISION-SHOPMGMT-012
- Original todo: "CLARIFY timezone standard" → Resolution: Resolved (facility timezone) | Decision: DECISION-SHOPMGMT-015
- Original todo: "CLARIFY audit visibility + PII" → Resolution: Resolved (redacted audit endpoint) | Decision: DECISION-SHOPMGMT-017
- Escalation from durion-positivity-backend#2096: "CLARIFY unknown operating hours vs closed" → Resolution: Resolved (unknown is its own fact; consumes time, emits nothing, contained to its own date) | Decision: DECISION-SHOPMGMT-018
- Escalation from durion-positivity-backend#2094: "CLARIFY booking horizon" → Resolution: Resolved (configurable, 180-day default; enforcement tracked separately) | Decision: DECISION-SHOPMGMT-019
- Escalation from durion-positivity-backend#2095: "CLARIFY workStartedAt vs planned startAt" → Resolution: Resolved (early start is legitimate; effective window governs occupancy; not a reschedule) | Decision: DECISION-SHOPMGMT-020
- Escalation from durion-positivity-backend#2245: "CLARIFY bay and mobile-unit setup rules" → Resolution: Resolved (eligibility enforced at submit, duty class at placement; affected appointments listed; mobile units serve their base location) | Decision: DECISION-SHOPMGMT-021, DECISION-SHOPMGMT-022, DECISION-SHOPMGMT-023; location data rules DECISION-LOCATION-025 to 029

## End

End of document.
