Title: [BACKEND] [STORY] Appointment: Reschedule Appointment with Notifications
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/11
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

Enable Schedulers to reschedule appointments while preserving referential integrity to estimates and work orders, preventing scheduling conflicts with resource availability, capturing reasons for the change, and notifying affected parties (customer, mechanic, downstream systems) so operational workflows remain coordinated.

---

## Actors & Stakeholders

- Scheduler (Service Advisor / Dispatcher)
- Shop Management Service (system of record for schedules)
- Customer
- Assigned Mechanic
- Work Execution Service
- Notification Service
- Audit Service

---

## Preconditions

1. Appointment exists and is reschedulable (SCHEDULED, CONFIRMED, AWAITING_PARTS)
2. Caller has `RESCHEDULE_APPOINTMENT` permission
3. Appointment linked to estimate/work order
4. Target date/time within operating hours

---

## Functional Behavior (Summary)

1. POS collects new date/time, reschedule reason (enum), optional notes, and notify flag.
2. Shop Management Service validates conflicts (bay, mechanic, capacity, overlapping appointments).
3. Hard conflicts block reschedule unless overridden with `OVERRIDE_SCHEDULING_CONFLICT` and reason; soft conflicts warn and allow override.
4. If valid (or overridden), appointment record is updated; previous scheduled time stored in history; `AppointmentRescheduled` event emitted to Work Execution and Notification services.
5. Assignment preservation: preserve bay+mechanic only if both available at new time; otherwise clear unavailable parts and flag for reassignment.
6. Notify customer and mechanic per rules; log audit event.

---

## Resolved Decisions (Answers to Open Questions)

- Q1 — Conflicts: Distinguish Hard (block) vs Soft (warn). Hard includes outside business hours, non-reschedulable states, required resource unavailable, mechanic unavailable when required, and hard capacity exceed. Soft includes capacity near limit, mechanic overload, customer overlap, and preferences.
- Q2 — Permissions: RBAC with `RESCHEDULE_APPOINTMENT`, `OVERRIDE_SCHEDULING_CONFLICT`, and `APPROVE_RESCHEDULE`. Default: Scheduler/Service Advisor have `RESCHEDULE_APPOINTMENT` scoped to facility; Shop Manager/Dispatcher may override.
- Q3 — Reasons: Reason is mandatory (enum) with optional notes; enum values: CUSTOMER_REQUEST, SHOP_CAPACITY, EQUIPMENT_ISSUE, MECHANIC_UNAVAILABLE, PARTS_DELAY, WEATHER, EMERGENCY, MANAGER_DISCRETION, OTHER. Free-text required for OTHER or when overriding.
- Q4 — Notifications: Customer (default on; mandatory for customer-initiated or within 24h), mechanic (if assigned), internal watchers optional. Channels: SMS (primary), Email (secondary); retries: SMS 1x, Email 2x; emit `NotificationFailed` and surface manual follow-up if delivery fails.
- Q5 — Minimum notice: Standard ≥24h. Same-day (<24h) requires `APPROVE_RESCHEDULE` or manager override. Emergency (<4h) requires Shop Manager approval and customer confirmation.
- Q6 — Assignment: Conditional preserve — only preserve assignment if both bay and mechanic available; otherwise clear and flag for dispatcher reassignment. Shopmgmt enforces deterministically.
- Q7 — Recurring: Out of scope; handle single-instance only. If part of a series, prompt scheduler and defer series-wide edits to a separate story.

---

## Business Rules

- Reschedule eligibility: Only SCHEDULED/CONFIRMED/AWAITING_PARTS may be rescheduled.
- Minimum notice/enforcement as above.
- Reschedule frequency limit: max 2 reschedules without manager approval.
- Audit trail required for all reschedules and overrides; immutable retention policy applies.

---

## Data Models (excerpt)

- AppointmentUpdate: appointmentId, previousScheduledDateTime, newScheduledDateTime, rescheduleReason (enum), rescheduleReasonNotes, rescheduledBy, rescheduledAt, conflictsDetected[], conflictOverridden (bool), assignmentStatus (PRESERVED|CLEARED|REASSIGNED), notifyCustomer, notificationStatus, estimateId, workOrderId.

- NotificationOutbox: notificationId, appointmentId, recipientType, recipientId, notificationType, notificationChannel, notificationContent, notificationStatus, retryCount, createdAt.

- AppointmentRescheduledEvent: eventId, appointmentId, estimateId, workOrderId, previousScheduledDateTime, newScheduledDateTime, rescheduleReason, rescheduledBy, rescheduledAt, assignmentStatus, mechanicId.

- AuditLog: auditId, eventType, appointmentId, actorId, actorRole, action=RESCHEDULE, previousValues, newValues, reason, conflictsDetected, overrideApplied, timestamp.

---

## Acceptance Criteria (key)

- AC1: Reschedule succeeds when no conflicts; appointment updated, event emitted, work order updated, customer notified, estimate/work order links preserved.
- AC2: Conflicts detected (BAY_UNAVAILABLE etc.) and suggested alternatives shown; scheduler can choose alternatives or request override if permitted.
- AC3: Hard conflict override requires `OVERRIDE_SCHEDULING_CONFLICT`; override logged with reason and approver.
- AC4: If mechanic unavailable at new time, mechanic assignment cleared and flagged for reassignment; mechanic notified of unassignment.
- AC5: If bay+mechanic available, assignment preserved and mechanic notified of time change.
- AC6: Reschedule within minimum notice blocked without approval; override requires manager approval.
- AC7: Customer notifications sent via SMS/email with calendar invite; notification status tracked and failures surfaced.
- AC8: Notification failures emit `NotificationFailed` and prompt manual contact.
- AC9: Audit log contains who, why, conflict details, override metadata.
- AC10: Work Execution updates planned time on receiving `AppointmentRescheduled` event.

---

## Audit & Observability

Emit and capture: AppointmentRescheduleAttempted, ConflictDetected, ConflictOverridden, AppointmentRescheduled, NotificationSent, NotificationFailed, AssignmentCleared, WorkOrderUpdated. Track metrics: reschedule success rate, conflict detection/override rates, assignment preservation rate, notification success rate, average processing time.

---

## Original Story (for traceability)

Original issue content preserved in comments and history. See: https://github.com/louisburroughs/durion-positivity-backend/issues/11
