# [FRONTEND] [STORY] Putaway: Replenish Pick Faces from Backstock (Optional)
## Purpose
Enable users to view and execute a replenishment task that moves inventory from backstock to pick faces. Provide a task detail screen that supports starting a pending task and completing an in-progress task with validated inputs and clear status transitions. Preserve list filters when navigating back to the tasks list for efficient workflow.

## Components
- Page header: “Replenishment Task Detail” + breadcrumb/back link to Replenishment Tasks list
- Status badge (PENDING / IN_PROGRESS / COMPLETED)
- Read-only task summary fields:
  - SKU
  - Requested quantity
  - Source site + optional source bin (with display labels if available)
  - Destination site + optional destination bin (with display labels if available)
  - Created at
  - Assigned to (if present)
  - Trigger metadata (read-only JSON rendered safely)
- Action buttons:
  - Start Task (only when status = PENDING)
  - Refresh (always)
- Complete Task form (only when status = IN_PROGRESS):
  - Quantity completed (required integer; default = task.quantity)
  - Ledger reference (optional; only if backend supports)
  - Complete Task submit button
- Inline validation messages (field-level) + form-level error summary
- Success banner on completion (includes ledger reference if provided/returned)
- “Technical details” expandable section:
  - requestId (from response header or error payload)
  - error code/message (when applicable)

## Layout
- Top: breadcrumb/back link (preserve list filters via query params) + page title + status badge
- Main (stacked sections):
  - Task Summary (read-only fields in 2-column grid)
  - Trigger Metadata (collapsible panel)
  - Actions row: [Start Task] (conditional) [Refresh] (always)
  - Complete Task (conditional form card)
  - Alerts area (success/error) + Technical details (collapsible)

## Interaction Flow
1. From Inventory module → Replenishment Tasks list, user selects a task to open Task Detail.
2. On Task Detail load, fetch task detail; render status and all read-only fields; show Refresh button.
3. If status = PENDING:
   1. Show Start Task button enabled only when user has required permission(s).
   2. On Start Task click: disable Start Task while request is in-flight (no auto-retry).
   3. If success: remain on detail page, update status to IN_PROGRESS, reveal Complete Task form.
   4. If 409 conflict (e.g., stale/already claimed): show error message + requestId; allow Refresh.
4. If status = IN_PROGRESS:
   1. Show Complete Task form with Quantity completed defaulted to requested quantity.
   2. Client-validate Quantity completed as required integer (and any additional constraints if provided by backend errors).
   3. If backend supports ledger reference, show optional Ledger reference field; otherwise hide it.
   4. Complete Task button enabled only when user has required permission(s) and form passes validation.
   5. If backend enforces assignee-only completion and assignedTo is present, disable completion when current user ≠ assignedTo only after this rule is confirmed (do not assume).
   6. On Complete Task submit: disable Complete Task while request is in-flight (no auto-retry).
   7. If success: remain on detail page, update status to COMPLETED, show success banner including ledger reference (if provided/returned).
5. If status = COMPLETED:
   1. Hide Start/Complete actions; keep Refresh available.
   2. Display completion success state if arriving immediately after completion; otherwise show normal read-only view.
6. Refresh flow (any status):
   1. On Refresh click: re-fetch task detail; update UI; preserve any visible banners only if still relevant.
7. Back navigation:
   1. “Back to list” returns to Replenishment Tasks list with prior filters preserved via query params.
8. Error handling (all reads/writes):
   1. 400/422: show validation errors (fieldErrors when available) and keep user inputs.
   2. 401: prompt login/session refresh per inventory standard; preserve navigation intent.
   3. 403: show forbidden message; disable actions accordingly.
   4. 404: show not found state with link back to list.
   5. 5xx/timeout: show transient error with retry option; always display requestId in Technical details.

## Notes
- Task detail actions must not navigate away; Start/Complete update status in-place.
- Prefer atomic “complete task” mutation (transfer + status update in one call) to avoid partial completion states.
- Double-submit protection is required: disable Start/Complete buttons while requests are in-flight; do not auto-retry mutations.
- Field naming must match backend exactly; note prior story used `taskId` while canonical elsewhere uses `replenishmentTaskId` (align with backend/DECISION-INVENTORY-014).
- Trigger metadata is read-only JSON; render safely (no HTML injection) per DECISION-INVENTORY-015.
- Always surface requestId in a “Technical details” section for both success (if available) and errors.
- Optional enhancements (only if backend supports): task history/audit panel; location lookups for friendly site/bin labels.
- Acceptance criteria: correct conditional rendering by status; permissions gate actions; validation + deterministic error mapping; back-to-list preserves filters; success banner on completion with ledger reference when available.
