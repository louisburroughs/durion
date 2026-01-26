# [FRONTEND] [STORY] Order: Cancel Order with Controlled Void Logic
## Purpose
Enable authorized users to cancel an order from the existing Order Detail screen using a controlled, Order-owned backend cancel operation. Provide a guided cancellation dialog that captures a required reason and optional comments, then presents deterministic outcomes (success, blocked, in-progress, unauthorized, validation errors). Ensure the UI reflects canonical cancellation-related statuses and displays cancellation/audit metadata (with support-only identifiers when permitted).

## Components
- Order Detail page (existing)
- “Cancel Order” action button/link (permission + capability gated)
- Read-only “Cancellation” section/panel on Order Detail
  - Status banner (Cancelled / Manual refund required / Cancellation in progress / Manual intervention required)
  - Cancellation summary fields (reason, comments if present, cancelledAt, cancelledBy, payment/work outcomes, blocking reason)
  - Support/admin-only identifiers (e.g., operational IDs) when returned and permitted
- Cancel Order dialog/modal (subscreen pattern consistent with Moqui)
  - Reason select (required; backend-provided allowed list when available; fallback list otherwise)
  - Comments textarea (optional; max 2000; character counter)
  - Inline field error messages
  - General error alert area
  - Buttons: Cancel (dismiss), Submit/Confirm Cancel
  - Loading/in-flight state (disable inputs, prevent duplicate submit)
- Global/local banners/toasts on Order Detail for result messaging
- Refresh affordance (especially for “in progress” state)

## Layout
- Top: Order Detail header (order identifier + primary status)
- Main content:
  - Existing order detail sections
  - Actions area near header or top-right: [Cancel Order] (only when allowed)
  - “Cancellation” panel below key order info, visible when cancellation-related status or cancellation summary exists
- Dialog overlay (modal): centered form with reason + comments, actions at bottom-right

## Interaction Flow
1. Enter Order Detail screen; UI loads Order Detail data including orderId, status, and canCancel (or equivalent capability), plus optional cancellation summary.
2. If user lacks permission or canCancel=false, hide/disable “Cancel Order” action; if a 403 occurs later, ensure action is not shown on subsequent renders.
3. User clicks “Cancel Order”; open Cancel Order dialog.
4. Dialog loads allowed cancellation reasons (preferred) or uses safe fallback list; render reason select (required) and comments textarea with counter (0–2000).
5. User submits:
   1) Validate reason is selected.
   2) Validate comments length ≤ 2000.
   3) Call single Order-owned cancel command with orderId, reason, comments.
   4) Disable inputs and prevent duplicate submissions while in-flight.
6. Response handling:
   1) 200: Close dialog; show success banner/message based on returned canonical status; refresh Order Detail to show updated status and cancellation metadata.
   2) 200 idempotent already-cancelled: Treat as success; close dialog; refresh; optionally show “Order was previously cancelled” message if provided; do not create/assume a new attempt.
   3) 400 with fieldErrors: Keep dialog open; show field-level errors (e.g., invalid reason, comments too long).
   4) 400 without fieldErrors: Keep dialog open; show general error message; refresh Order Detail.
   5) 403: Show authorization error; close or keep dialog per pattern; refresh Order Detail; ensure Cancel action is hidden thereafter.
   6) 409: Show “Cancellation is already in progress” message; refresh Order Detail; respect retryAfter if provided (e.g., suggest waiting/auto-refresh).
   7) 500/other: Show generic failure banner; refresh Order Detail.
7. Order Detail state rendering after refresh:
   1) Pre-cancel (canCancel=true): Cancel action enabled; Cancellation panel hidden unless summary exists.
   2) Cancellation in progress: Hide/disable Cancel action; show “Cancellation in progress” banner/panel and refresh affordance.
   3) Cancelled: Hide Cancel action; show cancelled banner + cancellation metadata.
   4) Manual refund required: Hide Cancel action; show warning banner “Manual refund required” + metadata.
   5) Manual intervention required: Hide Cancel action; show error banner + user-safe message; include support identifiers only when permitted.

## Notes
- UI must delegate policy enforcement to Order backend; no direct UI calls to Payment or Work Execution systems.
- Canonical statuses to display include cancellation-in-progress and final outcomes (e.g., cancelled, manual refund required, manual intervention required); map banners deterministically to returned status.
- Work Execution blocking: if backend returns a blocking reason/status, keep dialog open and show a clear message that cancellation cannot proceed due to work status; order remains not cancelled after refresh.
- Idempotency: repeating cancel on an already-cancelled order returns 200 and must not create side effects; UI treats as success and refreshes.
- Concurrency: 409 indicates in-flight cancellation; show in-progress messaging and refresh; optionally honor retryAfter.
- Audit display (when provided): cancellationId, cancelledAt (localized), cancelledBy, reason, comments (may be omitted/redacted), paymentOutcome/workOutcome, and blockingReason.
- Support/admin-only identifiers: render only if backend includes them and user has permission; otherwise omit entirely.
- Validation/error mapping must be deterministic: invalid reason/work-block/comments-too-long → 400; missing permission → 403; in-progress → 409; orchestration failure → 500/other with generic messaging.
- Optional/deferred: cancellation history table if backend returns multiple immutable cancellation entries; otherwise show latest summary only.
