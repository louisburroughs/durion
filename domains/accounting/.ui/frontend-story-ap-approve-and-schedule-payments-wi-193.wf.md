# [FRONTEND] [STORY] AP: Approve and Schedule Payments with Controls
## Purpose
Enable AP Clerks/Managers to load a bill’s details, approve Draft bills, and schedule payments for Approved bills from the POS frontend. The UI must enforce workflow state rules and permission/threshold controls, while providing an audit trail (or a clear “unavailable” fallback). The goal is to manage AP obligations with readiness for payment execution and clear, reviewable status transitions.

## Components
- Page header: “Bill Details” + bill identifier (billNumber) and status badge
- Bill summary panel (read-only fields): vendorName, amount, currency, dueDate, createdAt/updatedAt, billId/vendorId
- Status timeline / key timestamps (read-only): approvedAt, scheduledAt (and others if present)
- Primary actions area:
  - Approve button (with optional confirmation modal)
  - Schedule Payment button (opens modal/drawer)
- Schedule Payment modal/drawer:
  - Scheduled Date field (date picker)
  - Payment Method dropdown (ACH, CHECK, WIRE, CREDIT_CARD, OTHER)
  - Payment Method Reference field (conditional; required based on backend signal)
  - Submit / Cancel buttons
  - Inline field-level validation messages
- Scheduling details panel (read-only when status ≥ SCHEDULED): scheduledDate, paymentMethod, paymentMethodRef, scheduledBy, scheduledAt, schedule/payment reference (paymentId/scheduleId)
- Audit history section:
  - Audit list/table (eventType, actor, timestamp, message/notes)
  - Empty/fallback state: “Audit history unavailable.”
- Error/alert components:
  - Unauthorized/permission error banner
  - Conflict/invalid state/concurrency banner with “Reload” action
  - General API failure toast/banner

## Layout
- Top: Header (Bill Details) + status badge + key identifiers
- Main (two-column):
  - Left: Bill Summary (read-only) + key timestamps
  - Right: Actions (Approve / Schedule Payment) + Scheduling Details (when applicable)
- Bottom: Audit History list (or “unavailable” message)

## Interaction Flow
1. Load bill detail
   1. User navigates to Bill Details.
   2. Frontend fetches bill detail (and audit/history if available).
   3. Render read-only bill fields and current status; show audit list or “Audit history unavailable.”
2. Approve a Draft bill (Scenario: approve successfully)
   1. Preconditions: bill status = DRAFT; user has approval permission and within threshold.
   2. UI shows Approve button enabled; Schedule action hidden/disabled.
   3. User clicks Approve (optionally confirm in modal).
   4. Frontend submits approve command.
   5. On success: refetch bill detail; status updates to APPROVED; approvedAt and audit entry (approver + timestamp) appear if returned.
   6. On unauthorized: show permission error; keep state unchanged.
   7. On invalid state/concurrency: show conflict/state error and refetch bill.
3. Schedule payment for an Approved bill (Scenario: schedule successfully)
   1. Preconditions: bill status = APPROVED; user has schedule permission.
   2. UI shows Schedule Payment button enabled; Approve hidden/disabled.
   3. User opens Schedule Payment modal/drawer.
   4. User enters Scheduled Date and selects Payment Method.
   5. If backend indicates method requires a reference, user must enter Payment Method Reference.
   6. User submits; frontend validates:
      1. Missing scheduledDate → inline error; block submit.
      2. Missing paymentMethod → inline error; block submit.
      3. Invalid date format → inline error; block submit.
      4. Missing paymentMethodRef when required → inline error; block submit.
   7. Frontend submits schedule command.
   8. On success: refetch bill detail; status updates to SCHEDULED; scheduling fields become read-only; show schedule/payment reference (paymentId/scheduleId) if returned; show scheduling audit entry if returned.
   9. On validation errors from backend: map field-level errors inline; keep modal open.
   10. On invalid state/concurrency: show conflict/state error, close modal (or disable submit), and refetch bill.
4. Scheduling blocked when bill not approved (Scenario: blocked)
   1. User attempts to schedule via stale UI or direct URL while bill status = DRAFT (or otherwise not APPROVED).
   2. Backend rejects with invalid state/conflict.
   3. Frontend shows conflict/state error banner and refetches bill; bill remains in DRAFT; Schedule action remains hidden/disabled.
5. Read-only behavior when already scheduled
   1. Preconditions: bill status ≥ SCHEDULED.
   2. UI shows no Approve/Schedule actions.
   3. Scheduling details displayed read-only (scheduledDate, method, reference, scheduledBy/at, schedule/payment id if present).

## Notes
- Workflow must support Draft → Approved → Scheduled; actions are state-gated:
  - DRAFT: Approve available (if permitted); Schedule hidden/disabled.
  - APPROVED: Schedule available (if permitted); Approve hidden/disabled.
  - SCHEDULED (and beyond): no actions; show schedule details read-only.
- Permissions/controls: enforce role permissions and approval thresholds (under/over threshold tokens) and scheduling permission; show clear unauthorized messaging.
- Audit trail: display audit entries when provided; otherwise show “Audit history unavailable.” Still display latest approved/scheduled fields if present.
- Error handling: must handle unauthorized, invalid state/concurrency (conflict), and field-level validation errors; on conflict/state errors, refetch bill to reconcile UI.
- Payment methods: support ACH, CHECK, WIRE, CREDIT_CARD, OTHER; paymentMethodRef requirement is conditional and must be driven by backend-provided signal.
- Backend/API contract is a blocking dependency: endpoints and schemas needed for list bills (filters/pagination), bill detail (with optional audit), approve command, schedule command (including returned schedule/payment reference).
