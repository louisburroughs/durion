# [FRONTEND] [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder
## Purpose
Provide a manager-facing Moqui screen flow to review submitted time entries filtered by work date and/or work order, including status, decision metadata, and exception badges. Enable managers to approve or reject one or more pending entries (with required rejection reasons) while enforcing blocking-exception rules and locking entries after decision. Allow creation of separate adjustment records (proposed times or minutes delta) without editing the original entry, and support exception actions (acknowledge/resolve/waive) with required notes where applicable.

## Components
- Page header: “Timekeeping Approval Queue”
- Filter bar
  - Work Date picker (single date)
  - Work Order selector/search
  - Status filter (default: PENDING_APPROVAL; optional: APPROVED/REJECTED view)
  - Apply/Reset buttons
- Results table/list (paginated)
  - Row selection checkboxes (multi-select)
  - Columns: Employee, Work Date, Work Order, Submitted At, Hours/Time Range (read-only), Status, Exception badges (severity + count), Decision metadata summary
  - Row actions: View Details
- Bulk action toolbar (enabled when selection > 0)
  - Approve Selected button
  - Reject Selected button
- Entry detail drawer/page (from row)
  - Read-only entry summary (employee, work date, work order, start/end, total, status, submittedAt)
  - Decision metadata panel (approved/rejected by, at, reason if rejected)
  - Exceptions section
    - Exception list with severity, status, message, required action indicator
    - Per-exception actions: Acknowledge, Resolve, Waive
    - Waive modal with required “Waive reason/notes”
  - Adjustments section
    - Existing adjustments list (id, createdAt, reasonCode, proposed times or delta)
    - Create Adjustment button → modal/form
- Reject modal (single or batch)
  - Per-entry reason input (required, non-blank) or repeated reason fields per selected entry
  - Confirm Reject / Cancel
  - Inline validation message: “Rejection reason is required”
- Approve confirmation (optional lightweight confirm)
- Toast/inline error banners for API errors and field-level validation errors

## Layout
- Top: Page header + brief helper text (e.g., “Approve/reject pending time; resolve blocking exceptions first”)
- Below header: Filter bar (Work Date | Work Order | Status | Apply/Reset)
- Main: Results table with pagination; bulk action toolbar pinned above table when selections exist
- Right side (or full-page navigate): Entry Detail drawer/page with tabs/sections: Summary → Exceptions → Adjustments → Decision Metadata
- Footer area within modals: primary/secondary action buttons

## Interaction Flow
1. Manager opens Approval Queue; default filter loads time entries in PENDING_APPROVAL with pagination.
2. Manager sets Work Date and/or Work Order filters and selects Apply; list refreshes with matching entries and updated exception badges/status.
3. Manager reviews list:
   1. If an entry has an OPEN BLOCKING exception, the Approve action (row and bulk) is disabled for that entry; UI shows a hint like “Blocking exceptions must be resolved or waived before approval.”
   2. Non-blocking exceptions still display badges; approval remains available unless blocked by backend rules.
4. Manager selects one or more eligible entries and clicks Approve Selected:
   1. UI calls batch approve service.
   2. On success, each approved entry updates to APPROVED, becomes read-only/locked, and displays decision metadata (approved by/at).
   3. If any entry fails validation/state, UI shows per-entry error results and leaves those entries actionable as appropriate.
5. Manager selects one or more entries and clicks Reject Selected:
   1. Reject modal opens requiring a non-blank reason for each entry.
   2. If reason is missing and user submits, UI blocks submission and shows “Rejection reason is required.”
   3. On submit with valid reasons, UI calls batch reject service.
   4. On success, entries update to REJECTED, become read-only/locked, and display rejection reason + decision metadata.
6. Manager opens an entry’s details:
   1. Exceptions section lists exceptions with severity/status and available actions.
   2. Manager acknowledges an OPEN exception → status updates to ACKNOWLEDGED (if allowed).
   3. Manager resolves an ACKNOWLEDGED/OPEN exception → status updates to RESOLVED (if allowed).
   4. Manager waives an OPEN (or ACKNOWLEDGED) exception:
      - Waive modal requires non-blank waive reason/notes; on submit, status updates to WAIVED.
      - If waive reason missing, UI shows field-level validation error from 400 response.
   5. After blocking exceptions are RESOLVED/WAIVED, Approve becomes enabled; manager can approve the entry.
7. Manager creates an adjustment from entry details:
   1. Click Create Adjustment → modal opens with fields:
      - reasonCode (required, non-blank)
      - Either proposedStartAt + proposedEndAt OR minutesDelta (XOR rule)
   2. UI validates XOR rule client-side (and also handles backend 400 errors):
      - Prevent submit if both provided or neither provided.
   3. On submit, UI calls create adjustment service:
      - If entry not found → show error (404).
      - If entry not in PENDING_APPROVAL → show state error (422) and disable further mutation actions.
   4. On success, adjustment appears in adjustments list; original entry times remain unchanged.

## Notes
- Approval/rejection operations are batch-capable; UI should support selecting multiple entries and showing per-entry outcomes.
- After APPROVED or REJECTED, entry is locked/read-only in UI; no further manager actions except viewing details and audit metadata.
- Blocking rule: entries with any BLOCKING severity exception in OPEN status cannot be approved until that exception is RESOLVED or WAIVED.
- Exception state guards: terminal states (RESOLVED, WAIVED) cannot transition; UI should hide/disable actions accordingly.
- Waive requires non-blank resolution notes; rejection requires non-blank reason; adjustment requires non-blank reasonCode.
- Adjustment creation constraints: exactly one of (proposedStartAt & proposedEndAt) OR minutesDelta; enforce client-side and display backend field-level errors from 400 responses.
- Service calls are via Moqui actions; mutation actions should support idempotency if required by backend (e.g., correlation/idempotency header).
- Error handling: display validation errors (400) with field-level details; display 422 invalid state distinctly (e.g., “Entry no longer pending approval”).
