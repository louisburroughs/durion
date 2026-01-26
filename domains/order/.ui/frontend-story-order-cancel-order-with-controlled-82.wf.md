# [FRONTEND] [STORY] Order: Cancel Order with Controlled Void Logic
## Purpose
Enable authorized users to cancel an order via a controlled cancel/void flow with reason and comments, while preventing duplicate submissions and reflecting backend canonical cancellation states. Provide clear, state-driven UI feedback (in-progress, success, refund required, failure) and show a cancellation summary when available. Ensure the UI refreshes order details after any cancel attempt to render the authoritative status and outcomes.

## Components
- Order Detail header (Order ID, current status badge/label)
- Status badges/labels (safe fallback for unknown raw status)
- Cancel action button (hidden/disabled based on canCancel/status)
- Cancel Order modal/dialog
  - Reason selector (required)
  - Comments textarea (optional, max length with character counter)
  - Inline validation errors
  - Submit button (disabled while in-flight)
  - Cancel/Close button
  - Non-blocking error banner/alert area (for backend errors)
- Cancellation Summary section (conditional visibility)
  - Latest cancellation record fields: reason, comments, requested by, requested at
  - Outcome fields (when provided): payment outcome, work outcome
  - Guidance callouts: “Manual refund required”, “Manual intervention required”
  - Support/admin-only identifiers area (conditionally shown if permitted and present)
- Refresh affordance (button/link) when cancellation is in progress
- Empty state for cancellation summary (“No cancellation activity”) when applicable

## Layout
- Top: Order header + status badge; right side: Cancel button (if allowed)
- Main (stacked sections): Order details → Cancellation Summary (conditional) → Guidance callouts (conditional)
- Modal overlay: Cancel Order dialog centered with form fields and actions at bottom

## Interaction Flow
1. Load Order Detail page.
2. Render status label/badge from order status; if unknown, show safe fallback “Status: <raw>”.
3. Determine Cancel action visibility:
   1. If order indicates canCancel (or equivalent capability) and status is not cancellation-related terminal/in-progress, show enabled Cancel button.
   2. Otherwise hide or disable Cancel button (do not allow cancel unless canCancel is true).
4. Cancellation Summary visibility:
   1. Show summary section if order status is one of the canonical cancellation-related statuses (CANCELLING, CANCELLED, CANCELLED_REFUND_REQUIRED, CANCELLATION_FAILED), OR if a latest cancellation record exists.
   2. If no record and status not cancellation-related, hide summary or show “No cancellation activity” empty state.
5. User clicks Cancel:
   1. Open Cancel Order modal with Reason (required) and Comments (optional) inputs.
   2. Client-side validation:
      - Missing reason: block submit; show inline error.
      - Comments too long: block submit; show inline error + character counter.
6. User submits cancellation:
   1. Disable submit immediately; prevent duplicate submissions while request is in-flight.
   2. Call cancel order command service with orderId, reason, comments (and any required identifiers per service contract).
7. On cancel response (success):
   1. Close modal (or show success state briefly), then refresh Order Detail (and latest cancellation record if not embedded).
   2. Render updated status:
      - CANCELLED: show cancellation summary and any outcomes (payment/work) if provided.
      - CANCELLED_REFUND_REQUIRED: show “Manual refund required” guidance and payment outcome label (e.g., “Refund required”).
8. On cancel response (error):
   1. Show user-safe error message (from error contract) in the modal (or page-level alert if modal closes), including any user-safe details if provided.
   2. If backend returns 400 validation error: keep dialog open for correction; re-enable submit when user changes inputs.
   3. If backend returns 409 conflict indicating cancellation already in progress:
      - Show conflict warning.
      - Trigger refresh of Order Detail; render CANCELLING if returned.
   4. If backend indicates cancellation blocked by work state (non-correctable block):
      - Show user-safe message; keep dialog open but do not imply changing inputs will fix work-state block.
      - Allow resubmission only if user changes reason/comments; still expect backend to block.
   5. After any error, refresh Order Detail (and latest cancellation record if separate) to reflect authoritative state.
9. State-driven page behavior after refresh:
   1. CANCELLING: hide/disable Cancel; show “Cancellation in progress” + refresh affordance; show summary if record exists.
   2. CANCELLED: hide/disable Cancel; show cancellation summary and outcomes.
   3. CANCELLED_REFUND_REQUIRED: hide/disable Cancel; show “Manual refund required” guidance and payment outcome.
   4. CANCELLATION_FAILED: hide/disable Cancel; show “Manual intervention required” guidance; show summary if available.
10. Support/admin-only identifiers:
   1. Display correlation/support IDs only if present in responses/records and user is permitted; otherwise omit/redact.

## Notes
- Backend canonical statuses to support in UI: CANCELLING, CANCELLED, CANCELLED_REFUND_REQUIRED, CANCELLATION_FAILED; treat these as authoritative for state-driven rendering.
- Error handling must use stable error code plus user-safe message; optionally show user-safe details when provided.
- Always refresh Order Detail after cancel attempt completes (success or error); also refresh latest cancellation record if it is not embedded in Order Detail.
- “Manual refund required” guidance is shown when order status is CANCELLED_REFUND_REQUIRED (authoritative) and/or latest cancellation record indicates refund required.
- “Manual intervention required” guidance is shown when order status is CANCELLATION_FAILED.
- Prevent duplicate submissions: disable submit while in-flight; ensure idempotent UX even if user double-clicks.
- Ensure cancellation summary omits/redacts any support/admin-only subsystem fields unless permission is confirmed.
- Acceptance criteria highlights:
  - Cancel dialog validates reason/comments client-side.
  - 409 conflict shows warning and triggers refresh; UI reflects CANCELLING if applicable.
  - Failure path renders CANCELLATION_FAILED guidance and shows support identifiers only when permitted and present.
- TODO (design/dev): confirm exact field names for canCancel/capabilities, reason enum values, comments max length, and where latest cancellation record is sourced (embedded vs separate service).
