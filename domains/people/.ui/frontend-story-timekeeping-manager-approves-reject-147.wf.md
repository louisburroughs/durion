# [FRONTEND] [STORY] Timekeeping: Manager Approves/Rejects Time Entries
## Purpose
Enable a manager/approver to select an authorized employee and a pay period, review read-only timekeeping entries and the period’s decision history, then approve or reject the entire period atomically. The UI must prevent invalid decisions via local validation and backend-provided capability flags, and must re-fetch entries/history after any decision to reflect server truth. Provide clear empty states and user-facing messages when actions are unavailable.

## Components
- Page header: “Timekeeping Approvals” + brief helper text
- Selection form
  - Employee/Person picker (manager-scoped/authorized only)
  - Pay Period picker (shows date range + status when available)
  - Refresh button (re-fetch current selection)
- Status/validation message area (inline banner near actions)
- Actions
  - Approve Period button (with confirm dialog)
  - Reject Period button (opens modal)
- Entries list (read-only, paged)
  - Columns: date(s), hours, project/task (if available), entry status, last updated (optional)
- Decision history list (read-only)
  - Columns: decision timestamp (timezone-correct), decision (Approved/Rejected), decided-by display (name if provided else ID), comments (if present), version/sequence (if provided)
  - Empty-state message when no history
- Reject modal dialog
  - Optional comments textarea
  - Submit Reject button + Cancel
- Loading/empty states
  - Loading indicators for employees, periods, details (entries/history)
  - Empty-state messages for no employees, no entries, no history
- Error handling UI
  - Inline error banner/toast for failed loads or failed approve/reject

## Layout
- Top: Header + helper text
- Below header: Selection form in a single row (Employee picker | Pay Period picker | Refresh)
- Below selection: Action bar (left: status/validation message; right: Reject Period, Approve Period)
- Main content split:
  - Upper/main: Entries list (paged)
  - Lower/secondary: Decision history list
- Inline ASCII hint: Header → [Employee][Pay Period][Refresh] → [Msg……][Reject][Approve] → Entries → History

## Interaction Flow
1. Load page
   1. Fetch manager-scoped employees for the Employee picker.
   2. Show empty-state if no employees are available/authorized.
2. Select Employee
   1. On employee selection, fetch available pay periods for that employee (or load periods globally if designed that way).
   2. Clear entries/history panels until a pay period is selected.
3. Select Pay Period
   1. Fetch timekeeping entries for selected person + period (paged).
   2. Fetch period decision history (TimePeriodApproval list) for selected person + period.
   3. Display entries with each entry’s status; display history or an explicit “No approval history yet” empty state.
4. Enable/disable Approve/Reject (evaluated whenever selection/details change)
   1. Disable both if no person or no pay period selected.
   2. Disable both if detail (entries/history) not loaded.
   3. Disable with message if period status indicates approval not yet available (message: “Approval available after submission closes.”).
   4. Disable with message if payroll is closed for the period (message: “Payroll is closed for this period.”).
   5. Disable with message if any entry status is not Pending Approval (message: “All entries must be Pending Approval to decide this period.”).
   6. If backend capability flags are returned, honor them (e.g., canApprove/canReject false disables respective action even if local checks pass).
5. Approve Period (atomic)
   1. User clicks “Approve Period” → show confirm dialog.
   2. On confirm, call approve endpoint once with a generated idempotency key.
   3. On success, re-fetch entries and decision history for the current selection.
   4. Show updated entry statuses as returned by server and a new history record with decision = Approved and timestamp.
6. Reject Period (atomic, optional comments)
   1. User clicks “Reject Period” → open Reject modal.
   2. User optionally enters comments and submits.
   3. Call reject endpoint once with a generated idempotency key and comments (if provided).
   4. On success, re-fetch entries and decision history.
   5. Show new history record with decision = Rejected and any comments returned by server; entries reflect server-defined post-reject state.
7. Refresh
   1. User clicks Refresh → re-fetch entries and history for the current person + period (no selection changes).
8. Error/edge cases
   1. If any fetch fails, show an error banner and keep actions disabled until a successful reload.
   2. If approve/reject fails, show error feedback; do not optimistically change lists; allow retry (idempotent key per attempt).
   3. If history/entries are empty, show explicit empty-state messaging (no silent blank panels).

## Notes
- Read-only data expectations (no editing of entries on this screen); approved entries are immutable per policy.
- Decision history is append-only; display audit fields:
  - Decision timestamp in correct timezone.
  - “Decided by” shown as provided display name; otherwise show the ID (no extra lookup unless an authorized People lookup endpoint exists).
  - Comments shown if present.
- Approve/Reject are period-atomic; UI should not allow per-entry decisions.
- Idempotency: generate a client idempotency/trace key per approve/reject call; treat actions as idempotent from the UI perspective.
- Concurrency: if an updated-at/version field is provided by API, pass/handle it as required for optimistic concurrency (otherwise rely on server responses).
- Ensure pay period picker surfaces status + date range to help users understand why actions may be disabled.
- Provide clear disabled-state messaging near the action buttons (single message area preferred to avoid clutter).
- TODO (requirements risk): confirm exact period statuses and entry statuses used for gating (e.g., “Submitted/Closed”, “Pending Approval”, “Approved/Rejected”) and the exact shape/names of backend capability flags.
