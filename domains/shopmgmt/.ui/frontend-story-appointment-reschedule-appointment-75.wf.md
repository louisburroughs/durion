# [FRONTEND] [STORY] Appointment: Reschedule Appointment with Notifications
## Purpose
Enable schedulers and authorized shop staff to reschedule an existing appointment by selecting a new start/end time in the facility timezone, providing a backend-owned reason, and adding notes when required. The UI must submit the reschedule request, render backend-returned conflicts (hard/soft) with suggested alternative slots, and support permitted soft-conflict overrides and policy-driven approvals. After success, the Appointment Detail must reflect the updated schedule and show a notification outcome summary when provided, without treating notification issues as reschedule failure.

## Components
- Appointment Detail page updates
  - “Reschedule” action/button (permission/allowed-action gated)
  - Success/result banner area (includes new schedule + notificationOutcomeSummary when present)
  - Optional “Audit” panel/section (permission-gated)
- Reschedule Appointment screen (routable; may be modal-style transition)
  - Context header: appointment identifier, current start/end, facilityTimeZoneId indicator, rescheduleCount (if provided)
  - Form fields
    - New start date/time (facility timezone)
    - New end date/time (facility timezone)
    - Reschedule reason (select; backend-owned enum)
    - Reason notes (textarea; conditional required)
    - Override soft conflicts toggle/CTA (conditional)
    - Override reason (textarea/input; conditional required)
    - Approval reason (textarea/input; conditional required)
  - Conflicts panel (shown after 409)
    - Grouped list by severity (HARD vs SOFT)
    - Conflict items: code, message, overridable flag
    - Suggested alternative time slots list (selectable) when provided
  - Inline field validation messages (400 fieldErrors mapping)
  - Global banners for errors (403/404/409/422/5xx) and policy messages
  - Actions: Cancel/Back, Submit, Retry (idempotent), Override and submit (when allowed)

## Layout
- Top: Page title “Reschedule Appointment” + back link to Appointment Detail
- Main (stacked):
  - Context summary card (current schedule, timezone, rescheduleCount)
  - Reschedule form (new start/end, reason, notes)
  - Conditional sections (approvalReason, overrideReason)
  - Conflicts/policy messages area (appears after submit errors)
- Bottom: Action row (Cancel/Back on left; Submit/Retry/Override on right)

## Interaction Flow
1. From Appointment Detail, user selects “Reschedule” (only shown/enabled when backend hints allow; otherwise show explanatory banner on attempt).
2. Reschedule screen loads context (appointmentId) and renders current schedule, facilityTimeZoneId, rescheduleCount, and reason options (from context or enum endpoint).
3. User enters new start/end date/time (displayed/collected in facility timezone) and selects a reschedule reason.
4. If reason = OTHER, require reason notes before enabling submit; otherwise notes optional unless later required by backend response.
5. On Submit:
   1. Generate clientRequestId once per submit attempt; reuse it for retries if timeout/network error occurs.
   2. Send write payload including facilityId (from load), appointmentId, newStart/newEnd (ISO-8601 with offset), reason, notes (if any), optional version, and clientRequestId.
6. If response is success:
   1. Navigate back to Appointment Detail.
   2. Refresh appointment header schedule fields to show the new scheduled time.
   3. Show success banner including notificationOutcomeSummary if provided (notification issues do not negate reschedule success).
7. If response is 409 SCHEDULING_CONFLICT:
   1. Render conflicts grouped by HARD and SOFT; show suggested alternative slots when provided.
   2. If any HARD conflicts exist: block override path; user must change time or pick a suggested slot, then resubmit.
   3. If only SOFT conflicts:
      - If user has OVERRIDE_SCHEDULING_CONFLICT: show overrideReason field and “Override and submit” action (sets overrideSoftConflicts=true).
      - If user lacks permission: disable submit and show “Manager override required” message.
8. If response is 422 POLICY_ERROR:
   1. Show banner with backend message.
   2. If backend indicates approval required and user has APPROVE_RESCHEDULE: reveal approvalReason field and allow resubmit including approvalReason.
   3. If approval required but user lacks permission: block and show guidance (cannot proceed).
9. If response is 409 VERSION_MISMATCH (if used): prompt reload; on reload, keep user-entered proposed values in form state and update any returned version.
10. If response is 400 VALIDATION_ERROR: map fieldErrors to inline messages; keep form state.
11. If response is 403 FORBIDDEN: show access denied (no cross-facility detail leakage) and provide return to Appointment Detail.
12. If response is 404 NOT_FOUND: show not found and navigate safely back.
13. If 5xx/network/timeout: show retry option; preserve form state; reuse the same clientRequestId on retry to achieve idempotent result.

## Notes
- Facility timezone is authoritative: display inputs in facility timezone and submit ISO-8601 timestamps with offset; do not infer operating hours—surface backend conflicts/policy messages instead.
- Writes must include facilityId explicitly in the request body; reads may omit facilityId (backend derives from appointmentId and enforces access).
- Conflict handling rules: HARD conflicts are never overridable; SOFT conflicts may be overridden only with OVERRIDE_SCHEDULING_CONFLICT permission and required overrideReason.
- Approval handling: show approvalReason only when backend indicates approval is required and user has APPROVE_RESCHEDULE; treat max lengths as backend-validated.
- Submit enablement: no pre-check required; enable submit only when required fields are present (new start/end, reason, facilityId, clientRequestId).
- Appointment Detail modifications:
  - Add Reschedule entry point with permission/allowed-action gating and clear error feedback when disallowed.
  - After success, refresh schedule fields and show result banner including notificationOutcomeSummary when present.
  - Optional Audit section: show only with AUDIT_VIEW (or backend allowedActions); list audit entries (who/why/when) when endpoint is available and permitted.
