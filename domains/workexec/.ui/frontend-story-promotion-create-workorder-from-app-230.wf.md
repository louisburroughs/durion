# [FRONTEND] [STORY] Promotion: Create Workorder from Approved Estimate
## Purpose
Enable a Service Advisor to promote an Approved Estimate into a Work Order directly from the POS Estimate UI. Ensure the promotion is permission-checked, auditable, and idempotent (re-promoting opens the existing Work Order rather than creating duplicates). Provide clear UI states and error handling when the estimate is not approved, not found, or the user lacks permission.

## Components
- Estimate header (estimate identifier, customer/vehicle summary, current status badge)
- Primary action button: “Promote to Work Order”
- Disabled/hidden state helper text (why promotion is unavailable)
- Confirmation modal (promote confirmation, brief warning about immutability/audit)
- Loading state on action (spinner/disabled button)
- Toast/inline alert messaging area (success/info/error)
- Error-specific messages (403, 404, 409, validation 400/422, 5xx/network)
- Navigation handler to Work Order detail route (using returned work order id)
- Estimate state refresh trigger (after validation errors / stale state)

## Layout
- Top: Estimate header + status badge (Approved / other)
- Main: Estimate details content (existing page content) with actions aligned top-right
- Right/top of main actions: [Promote to Work Order] button (or disabled with helper text)
- Overlay: Confirmation modal centered; alerts/toasts near top of page

## Interaction Flow
1. Promote approved estimate (happy path)
   1. User views an Estimate with status = Approved.
   2. UI shows enabled “Promote to Work Order” button.
   3. User clicks “Promote to Work Order”; confirmation modal appears.
   4. User confirms; UI disables action and shows loading state.
   5. Frontend sends POST request to create/promote work order with body containing the estimate id and includes an Idempotency-Key header.
   6. On 200/201 success, read response fields: work order id (required) and status enum (required; expected initial status per state machine).
   7. UI navigates to the Work Order route for the returned id.
   8. Work Order screen displays its status and, when available, a reference to the source estimate.

2. Prevent promotion for non-approved estimate (UI gating + stale state handling)
   1. User views an Estimate with status != Approved.
   2. UI hides the action or shows it disabled with explanation (e.g., “Estimate must be Approved to promote.”).
   3. If user attempts promotion via stale UI state and backend returns 400/422 validation error:
      1. UI displays error indicating the estimate must be approved.
      2. UI triggers a refresh/reload of the estimate state and updates the action visibility/disabled state accordingly.

3. Idempotent promotion (existing work order)
   1. User attempts to promote an Approved Estimate that was already promoted.
   2. Backend responds with either:
      1. 409 Conflict including existing work order id, or
      2. 200/201 including the existing work order id.
   3. UI navigates to the existing Work Order.
   4. UI shows an informational message (e.g., “Work order already exists; opened existing work order. No duplicate created.”).

4. Error handling (permission, not found, transient failures)
   1. If backend returns 403: show “You don’t have permission to create work orders.” and keep user on Estimate.
   2. If backend returns 404: show “Estimate not found.” and prompt user to refresh or navigate back.
   3. If backend returns 5xx/network: show “Unable to create work order right now.” and allow retry (re-enable button after failure).

## Notes
- Promotion must only be allowed when estimate status is Approved; UI should proactively gate but also handle backend validation (400/422) for stale states.
- Request must include Idempotency-Key header to support safe retries and prevent duplicates.
- Work order create response must include: id (opaque string; required for navigation) and status (string enum; required; expected initial status per work order state machine).
- Idempotency behavior: if a work order already exists, backend may return 409 with existing id or 200/201 with existing id; frontend must treat both as “open existing” and communicate that no duplicate was created.
- Error copy requirements:
  - 403: “You don’t have permission to create work orders.”
  - 404: “Estimate not found.”
  - 409: “Work order already exists; opening it now.”
  - 5xx/network: “Unable to create work order right now.”
- Work Order screen should show status and reference to the source estimate when available (immutable/auditable linkage implied).
- TODO (design): decide whether the action is hidden vs disabled for non-approved estimates; ensure explanation text is visible and consistent with POS UI patterns.
