# [FRONTEND] [STORY] Execution: Assign Technician to Workorder
## Purpose
Enable Shop Managers/Dispatchers to assign or reassign a primary technician on a Work Order when the Work Order is in an allowed status. Provide a clear “Assignment” section showing the current primary technician and a read-only assignment history for auditability. Support a searchable technician picker with optional reason, and handle concurrency conflicts by refreshing to the latest Work Order state.

## Components
- Work Order Detail header (Work Order ID, status)
- Assignment section (new/modified)
  - Current primary technician display (name + id if needed)
  - Action button: Assign / Reassign (visibility/enablement gated by permission + Work Order status)
  - Helper text for status/permission gating (when disabled)
- Assignment history list (read-only)
  - Rows with: assignment record id, work order id, technician id/name, assigned by user id/name, assigned at, unassigned at (nullable), reason (nullable)
  - Empty state (no history)
  - Loading state + retry action (SD-UX-LOADING-RETRY)
- Assign/Reassign dialog (embedded modal or embedded section)
  - Technician picker (searchable)
    - Search input, results list, select action
    - Optional scoping by shopId (if available)
  - Reason field (optional)
  - Submit button
  - Cancel button
  - Inline validation / error message area
- Conflict/error messaging
  - 409 conflict banner/inline message including backend-provided message (if provided)
  - Generic load/submit failure message + retry

## Layout
- Top: Work Order header (ID + status)
- Main: Work Order details content
  - Assignment section (prominent, near top of details)
  - Assignment history list directly below Assignment section
- Right/Inline: Assign/Reassign action button aligned with current technician display
- Dialog overlay (when assigning): centered modal with picker + reason + actions

## Interaction Flow
1. Load Work Order detail view.
2. UI requests Work Order (read) including: id, status, primaryTechnicianId (nullable), primaryTechnicianName (nullable), shopId (optional), customerId (optional/informational).
3. UI loads assignment history (preferred: via entity-find related list by workOrderId; fallback: REST read) and renders list or empty state.
4. If user has permission and Work Order status is allowed, show enabled Assign (if no current tech) or Reassign (if current tech exists); otherwise show disabled action with explanation.
5. Assign technician (Scenario 1):
   1. User clicks Assign.
   2. Open Assign dialog with searchable technician picker (scoped by shopId if available) and optional Reason.
   3. User selects Technician A, optionally enters reason, clicks Submit.
   4. Show submitting/loading indicator; on success, close dialog.
   5. Refresh Work Order detail to show Technician A as current primary technician.
   6. Refresh assignment history; verify new entry exists showing Technician A and assigned-by/assigned-at fields; if backend transitions status on assignment, reflect updated status.
6. Reassign technician (Scenario 2):
   1. User clicks Reassign.
   2. Dialog opens with same fields; user selects Technician B and submits.
   3. On success, refresh Work Order and history.
   4. History shows Technician A entry with unassignedAt populated; Technician B entry is active (unassignedAt null).
7. Concurrency conflict on submit (Scenario 5):
   1. While dialog is open, another user changes Work Order status or assignment.
   2. User submits; backend returns 409 conflict.
   3. UI shows conflict message including backend message (if provided).
   4. UI refreshes Work Order detail and assignment history to latest state; dialog closes or remains open with a prompt to re-try based on UX decision.
8. Load failures / retry (SD-UX-LOADING-RETRY):
   1. If Work Order or history load fails, show standard loading error state with Retry.
   2. If submit fails (non-409), show error message and allow retry without losing selected technician/reason where possible.
9. Cancel:
   1. User clicks Cancel in dialog; dialog closes with no changes.

## Notes
- Assignment/reassignment is status-gated: Work Order must be in an allowed status (exact allowed statuses to be enforced per backend contract); assignment after start is not allowed in this story unless backend explicitly supports it (manager-only override concept does not apply here).
- Permission gating: only users with assign permission (e.g., Shop Manager/Dispatcher) can see/enable Assign/Reassign.
- Work Order fields: id is required; status required; primaryTechnicianId nullable; primaryTechnicianName nullable/derived; shopId optional for scoping technician search; customerId optional informational only.
- Assignment history is append-only and read-only in UI; minimum fields displayed include assigned/unassigned timestamps and optional reason.
- Ensure UI reflects backend-driven status transitions on assignment (if any).
- Concurrency: handle 409 conflicts explicitly and refresh both Work Order and history to avoid stale UI.
- Provide consistent loading indicators and retry affordances for Work Order and history loads, and safe ergonomics for submit failures.
