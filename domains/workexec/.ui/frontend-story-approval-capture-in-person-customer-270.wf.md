# [FRONTEND] [STORY] Approval: Capture In-Person Customer Approval
## Purpose
Enable a Service Advisor to capture a customer’s in-person approval for an estimate directly from the estimate detail/edit screen. On confirmation, the estimate transitions to an approved state and an immutable approval record is created so downstream work order creation can proceed. The UI must handle invalid state, concurrency conflicts, and unauthorized attempts with clear messaging and recovery actions.

## Components
- Estimate detail/edit header (estimate identifier, status badge)
- Estimate summary section (customer/vehicle context as available; totals display if already present)
- Primary action button: “Capture In-Person Approval”
- Confirmation dialog/modal
  - Title: “Confirm In-Person Approval”
  - Body text explaining approval is recorded and estimate will be approved
  - Buttons: “Confirm” (primary), “Cancel” (secondary)
- Inline loading/disabled state for approval action (submitting)
- Error alert/toast/banner area for API errors
- Conflict resolution prompt (409) with “Refresh” action/button
- Refresh/reload mechanism for estimate detail (manual refresh action and/or automatic reload after success)

## Layout
- Top: Page header with Estimate title/ID and Status badge (e.g., Draft/Approved)
- Main: Estimate details and summary (including totals if already part of screen)
- Right or upper-main actions area: “Capture In-Person Approval” button near other estimate actions
- Overlay: Centered confirmation modal on approval attempt
- Inline: Error banner/toast near top of content; conflict prompt includes “Refresh” CTA

## Interaction Flow
1. Load estimate detail/edit screen
   1. Fetch and render Estimate data; show current status.
   2. Enable “Capture In-Person Approval” only when estimate loaded successfully and not currently submitting (and optionally when capability indicates approval allowed).
2. Scenario 1: Approve a Draft estimate in person (happy path)
   1. User clicks “Capture In-Person Approval”.
   2. Show confirmation dialog with Confirm/Cancel.
   3. User clicks “Confirm”; disable actions and show submitting state.
   4. Frontend calls the in-person approval endpoint.
   5. On success, refresh estimate data (use response or refetch).
   6. Update status display to Approved.
   7. Remove/disable the approval action so it is no longer available.
3. Cancel confirmation
   1. User clicks “Cancel” in the dialog.
   2. Close modal; no API call; no state change.
4. Scenario: Invalid state (400)
   1. User confirms approval; backend returns 400.
   2. Display error: “This estimate cannot be approved in its current state.”
   3. Keep estimate displayed; ensure submitting state clears; approval action availability re-evaluated after refresh (if performed).
5. Scenario 4: Concurrency conflict (409)
   1. User loads estimate; another user modifies/approves before confirmation.
   2. User confirms approval; backend returns 409.
   3. Display message: “This estimate was updated by someone else.”
   4. Provide a “Refresh” action that reloads estimate detail.
   5. After refresh, UI reflects server status and available actions update accordingly.
6. Scenario 5: Unauthorized approval attempt (403)
   1. User without permission attempts to confirm approval; backend returns 403.
   2. Display: “You don’t have permission to approve estimates.”
   3. Ensure estimate remains in its prior state in UI; after refresh, status remains unchanged.

## Notes
- Enable/disable rules: “Capture In-Person Approval” is enabled only if the estimate is loaded successfully and the UI is not currently submitting; optionally gate by a capability signal if available, otherwise rely on backend 403 handling.
- Error messaging must match expectations:
  - 400 invalid state: “This estimate cannot be approved in its current state.”
  - 409 conflict: “This estimate was updated by someone else.” and include a “Refresh” action.
  - 403 unauthorized: “You don’t have permission to approve estimates.”
- After successful approval, the approval action must no longer be available on the screen.
- Refresh behavior: after success or conflict refresh, the UI must reflect server truth (status and action availability).
- Currency/totals: if totals are displayed, use existing formatting utilities; do not compute totals in the frontend.
- Estimate view model fields required for config lookup and display should be treated as required where specified; optional fields should not block rendering.
