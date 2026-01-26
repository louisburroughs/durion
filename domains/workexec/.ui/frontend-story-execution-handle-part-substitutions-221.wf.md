# [FRONTEND] [STORY] Execution: Handle Part Substitutions and Returns

## Purpose
Enable technicians to adjust issued parts during Work Order execution by returning unused quantities and substituting an issued part with another catalog part. Provide guided dialogs with validation and policy/permission-aware controls, while relying on backend-authoritative enforcement and approval gating. Ensure safe, idempotent submissions and clear feedback, including read-only audit history visibility at the Work Order level.

## Components
- Work Order Execution header (Work Order ID, status, approval-blocked indicator)
- Parts/Materials section within WorkOrderEdit
- Part usage lines table/list
  - Columns: Part (name/SKU), Issued, Consumed, Returned (if available), Returnable (optional), Line status (optional)
  - Row actions: Return unused, Substitute
  - Disabled state + tooltip/reason text
- Return Unused modal dialog
  - Read-only context panel (part name/SKU, issued/consumed/returned, returnable if provided)
  - Form fields: Return quantity (required), Note/Reason (optional if supported)
  - Buttons: Cancel, Submit Return
  - Inline validation + error summary area
- Substitute Part modal dialog
  - Read-only context panel (original part name/SKU, issued/consumed/returned)
  - Part picker (search input, results list, pagination, select action)
  - Form fields: Substitute quantity (required), Note/Reason (optional if supported)
  - Price match controls (optional; only if capability/permission allows and backend supports)
  - Buttons: Cancel, Submit Substitution
  - Inline validation + error summary area
- Approval-blocked banner/state
  - Banner message when backend indicates approval required/blocked
  - UI lock/disable execution-affecting actions when blocked
- Parts Adjustments History (read-only)
  - Work Order-level list of events (Return/Substitution), timestamp, actor, details (part IDs/names if available), quantity, note, price match flag (optional)
- Toast/snackbar notifications (success/failure)
- Loading/in-flight indicators (button spinner, modal disabled state)

## Layout
- Top: Work Order header + status chip + approval-blocked banner (if applicable)
- Main: WorkOrderEdit content
  - Parts/Materials section
    - Part usage lines table with per-row actions (Return unused | Substitute)
  - Below/side panel: Parts Adjustments History (read-only list)
- Overlays: Return modal / Substitute modal centered; backdrop prevents background interaction during submit

## Interaction Flow
1. User opens a started Work Order execution view (WorkOrderEdit) and navigates to Parts/Materials.
2. For each part usage line, UI evaluates enable/disable:
   1) Disable Return/Substitute if Work Order not started (status < started) or backend indicates not started.
   2) Disable if Work Order is approval-blocked (status indicates awaiting approval or boolean flag if provided).
   3) Disable if line is not adjustable (line-level flags if provided).
3. Return unused quantity (success path):
   1) User clicks “Return unused” on a line.
   2) Return modal opens showing read-only context (part name/SKU; issued/consumed/returned; returnable if provided).
   3) User enters Return quantity (required, numeric > 0; must be ≤ returnable if provided, else ≤ issued-consumed as UI guard).
   4) User optionally enters Note/Reason if supported.
   5) On Submit: generate idempotency key (UUID) and attach as Idempotency-Key header; disable submit while in-flight.
   6) If request succeeds: close modal, refresh WorkOrderEdit data, show success toast; history list shows a new Return event.
4. Return unused (error/edge cases):
   1) 400/422: show field-level errors and/or error summary from standard error envelope; keep modal open; allow retry using the same idempotency key for the same attempt.
   2) 409 conflict: show “Data changed” message; prompt user to refresh/reload; on refresh, update quantities and re-validate.
   3) 403/404: show blocking error message; close modal or keep open with disabled submit per UX convention.
5. Substitute part (success path):
   1) User clicks “Substitute” on a line.
   2) Substitute modal opens with original part context and current quantities.
   3) User selects substitute part via part picker (search + paginated results; no raw ID entry).
   4) User enters Substitute quantity (required, numeric > 0).
   5) Optional inputs: Note/Reason (if supported); Price match controls only if capability/permission allows and backend supports.
   6) On Submit: generate idempotency key (UUID) and attach as Idempotency-Key; disable submit while in-flight.
   7) If request succeeds: close modal, refresh WorkOrderEdit; show success toast; history reflects substitution event and updated lines.
6. Substitute (approval gating path):
   1) If response indicates approval required/blocked (work order status/flag or line status): show approval-blocked banner.
   2) Disable further execution-affecting actions (including Return/Substitute) per returned state; keep history visible.
7. Substitute (error/edge cases):
   1) Policy/eligibility disallow (400/422): show structured error; keep modal open for correction.
   2) 409 conflict: show conflict message; refresh and reattempt if needed (reuse idempotency key only for the same submit attempt).
   3) Price match not permitted: do not render controls; if backend rejects, show error and remove/disable option on next render.

## Notes
- Backend is authoritative for eligibility/policy checks, permission/capability checks, approval gating, and final quantity enforcement; UI provides guardrails but must handle backend rejection gracefully.
- Idempotency: every submit action must include Idempotency-Key; reuse the same key when the user retries the same attempt (e.g., after transient error), but generate a new key for a new attempt.
- Concurrency: handle 409 conflicts by prompting refresh and reflecting updated quantities/status; support optimistic concurrency token if provided.
- Validation rules:
  - Return quantity: required, numeric > 0; ≤ returnable quantity if provided; otherwise ≤ issued-consumed as a UI guard.
  - Substitute: substitute part required via picker; quantity required, numeric > 0.
- Approval-blocked state: when indicated, show prominent banner and disable Return/Substitute row actions and other execution-affecting actions.
- History: display read-only Parts Adjustments History at Work Order level with event type (Return/Substitution), timestamp (UTC), actor, quantity, and optional note/price match flag when available.
- Submit buttons must be disabled while in-flight to prevent double-submit; show progress indicator and surface standard error envelope messages.
