# [FRONTEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage)

## Purpose
Enable a warehouse user to execute an existing putaway task by scanning/selecting the source (staging) and destination (storage) locations, and conditionally capturing an item/pallet identifier when required by the task. The screen guides the user through a stepper flow, validates required inputs, and submits a single atomic “execute putaway” call. It then displays success/failure outcomes, handles authorization/validation conflicts safely, and reflects task completion with a completion reference.

## Components
- Page header: “Execute Putaway” + task identifier
- Breadcrumbs / back navigation: Inventory → Putaway → Tasks → Execute
- Task summary card (read-only): status, SKU, quantity, expected source (if provided), completion reference (if completed)
- Stepper (4 steps): Source → Item/Pallet (conditional) → Destination → Confirm
- Source step
  - Location picker (staging/source) with search
  - Scan/entry input (wedge/camera) to resolve human code to location
  - “Clear” action
  - Inline validation/error message area
- Item/Pallet step (only if task.requiresIdentifier = true)
  - Text input (scan/manual) for item/pallet identifier
  - “Clear” action
  - Inline required validation
- Destination step
  - Location picker (storage/destination) filtered by site/location rules (and bin filtering if applicable)
  - Scan/entry input to resolve human code to location
  - “Clear” action
  - Inline validation/error message area
- Confirm step
  - Read-only confirmation summary: task id, SKU, quantity, from, to, identifier (if provided/required)
  - Primary button: “Confirm Putaway”
  - Secondary button: “Back” (to previous step)
- Loading/submitting indicator + double-submit guard state
- Error states
  - Inline field errors (required/invalid)
  - Blocking alert panel for backend validation/capacity/incompatibility/inactive site
  - Forbidden (403) state panel with disabled confirm
  - Conflict (409) reload prompt modal/dialog
  - Generic server error alert with retry option
- Success outcome
  - Success banner/toast
  - Completed view with completion reference and disabled inputs
  - Navigation action: “View Task Detail” / “Back to Task List”

## Layout
- Top: Breadcrumbs + page title + task id
- Main: Task summary card (status/SKU/qty/expected source/completion ref)
- Main: Stepper with step content panel beneath
- Bottom (sticky footer): Back/Next controls; on Confirm step show “Confirm Putaway” primary action
- Inline: Error/alert area at top of step content; success banner near top of page after submit

## Interaction Flow
1. Load screen from route with taskId; fetch PutawayTask and render task summary (status, SKU, quantity, expected source if provided).
2. If task status is Completed: show completion summary + completion reference; disable/hide execute controls; provide navigation to Task Detail or Task List.
3. If task status is non-executable/unknown: show read-only task info and message “This task cannot be executed in its current status.” Disable confirm.
4. Step 1 — Source:
   1. If task provides a specific source location, pre-fill it and prompt user to confirm/match via scan or picker selection.
   2. User scans or selects a source location via picker (no free-text UUID entry).
   3. UI validates source is present; if mismatch with expected source (when enforced), show blocking error and keep user on Source step.
5. Step 2 — Item/Pallet (conditional):
   1. If task.requiresIdentifier is true, show this step; otherwise skip to Destination.
   2. User scans/types identifier; UI trims and requires non-empty value before proceeding.
6. Step 3 — Destination:
   1. User scans or selects destination via picker; picker enforces allowed site/location/bin relationships (filter bins to selected destination location when applicable).
   2. UI blocks selection if destination site is inactive/pending (based on picker metadata); show message that movements are not allowed for inactive/pending sites.
7. Step 4 — Confirm:
   1. Show read-only summary: task id, SKU, quantity (task-owned), from source, to destination, identifier (if required/provided).
   2. “Confirm Putaway” is enabled only if: taskId present, source selected, destination selected, identifier present when required, task status executable, and not submitting.
8. Submit:
   1. On click “Confirm Putaway”, send exactly one execute call with correlation ID propagated; do not send quantity by default.
   2. Disable confirm while submitting (double-submit guard).
9. Success (200/201):
   1. Display success outcome; update task status to Completed and show completion reference from response.
   2. Disable/hide execute controls; navigate to Task Detail (preferred) or back to Task List with task marked completed.
10. Error handling (stay safe, permission-aware, no sensitive logging):
   1. 401: redirect to login/session recovery.
   2. 403: show forbidden state; disable confirm until reload or navigation away.
   3. 409: show conflict prompt to reload; after reload show completed view if task is now completed/changed.
   4. 400/422 validation errors:
      - Destination invalid/incompatible: show blocking error; clear destination selection; keep user on Destination step to rescan/select.
      - Destination capacity/full: show blocking error; keep user on Destination step to rescan/select (no split/partial UI).
      - Source insufficient on-hand: show blocking error; keep user on Confirm step; provide next-step guidance (reconciliation required) without attempting adjustments.
      - Inactive/pending location returned by backend: show blocking error and keep user on the relevant selection step.
   5. 5xx: show generic error with retry; keep inputs intact; allow resubmit when not submitting.

## Notes
- Location selection must use pickers; scanning resolves human-readable codes via backend resolve/search (manual entry allowed but not UUID free-text in normal flow).
- Stepper flow: 1) Source 2) Item/Pallet (only when task requires) 3) Destination 4) Confirm; skip Item/Pallet when not required.
- Read-only always: task SKU, task quantity, task status, expected source (if provided). Editable until submit: destination selection; identifier when required. Source is pre-filled when task binds it; treat as match/confirm unless backend explicitly allows override.
- Client-side validation before enabling confirm: taskId present, source present, destination present, identifier present when required (trimmed non-empty), not submitting, task status executable.
- Block execution if selected site is inactive/pending (inventory-wide rule for new movement flows); enforce via picker metadata and handle backend error if returned.
- Confirm action must be single atomic execute call; no client-side quantity computation and no partial/split behavior unless backend explicitly supports it.
- Correlation ID must be propagated on requests and surfaced on errors; display safe, user-friendly messages without sensitive details.
- Acceptance criteria: successful execute returns non-empty completion reference and updated task status; UI reflects completion and disables execute controls; deterministic handling for 401/403/409/422/5xx with appropriate user guidance and navigation.
