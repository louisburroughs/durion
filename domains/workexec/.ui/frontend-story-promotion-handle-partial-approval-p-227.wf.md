# [FRONTEND] [STORY] Promotion: Handle Partial Approval Promotion
## Purpose
Enable Service Advisors to promote only approved estimate lines into a new Work Order while leaving non-approved lines deferred on the original estimate. Provide clear UI affordances (action entry point, confirmation summary, and status badges) so users understand what will be promoted vs deferred. After promotion, show read-only traceability links between the Estimate and the created Work Order (and optionally line-level links) to support navigation and auditing.

## Components
- Estimate Read page
  - Header: estimate identifier, status badge, read-only Work Order link when work order id (or equivalent) is present
  - Actions: “Promote” button or overflow menu item (partial approval aware)
  - Line items list/table
    - Line status badge(s) (must map from exact backend enum strings returned)
    - Line description/name
    - Optional amount/qty display
    - For promoted lines: read-only link to created Work Order (and optional Work Order Line link if provided)
- Promotion confirmation modal/dialog
  - Summary counts: number of approved items to be promoted; number of items remaining deferred
  - Informational note: deferred items are not executable until later approval flow
  - Primary confirm CTA; secondary cancel CTA
- Work Order Read page (post-create navigation target)
  - Header: work order identifier, status badge, read-only Estimate link to originating estimate
  - Work order lines list/table
    - Line details
    - Read-only “source estimate line id” (or equivalent) when present for traceability

## Layout
- Top: Page header (Estimate/Work Order title + id, status badge, read-only cross-link when available, actions on right)
- Main: Line items list/table (status badge column + description + optional fields + traceability link column for promoted lines)
- Overlay: Centered confirmation modal with summary counts and confirm/cancel actions

## Interaction Flow
1. User opens Estimate (read) view for an estimate whose status includes an eligible value (must include “approved” for eligibility).
2. UI displays estimate header status badge and the line list with status badges for each line (use exact backend enum values; no invented client enums).
3. User selects the Promote action (button or menu item).
4. UI opens confirmation dialog summarizing:
   1) count of approved items that will be promoted,
   2) count of items that will remain deferred on the estimate,
   3) note that deferred items are not executable until later approval flow.
5. User confirms promotion:
   1) UI sends promote request including an If-Match header (for concurrency control).
   2) On success, UI receives created Work Order id (and optionally updated estimate snapshot/lines).
6. UI navigates to Work Order (read) view for the created work order id.
7. Work Order view shows:
   1) only the approved items (no declined/deferred items),
   2) a read-only link in the header back to the originating estimate,
   3) each work order line displays source estimate line id (or equivalent) read-only when present.
8. Returning to the Estimate view (or if updated snapshot is returned):
   1) promoted lines show post-promotion status (promoted),
   2) non-approved lines show deferred status,
   3) promoted lines show read-only link(s) to the created Work Order (and optional Work Order Line link if provided).
9. Error/edge cases:
   1) Validation error (e.g., no approved lines): show server-provided message to user.
   2) Unauthorized: show appropriate error state/message.
   3) Missing estimate: show not-found error state/message.
   4) Stale state/concurrency (estimate no longer eligible): show error and prompt user to refresh/reload.
   5) Server error: show generic error message; display message when present.

## Notes
- Status mapping: UI must display status badges using exact enum strings returned by backend for Estimate.status and EstimateLine.status; do not invent or hardcode new client-side enums beyond mapping exact values.
- Eligibility: Estimate.status must include an “approved” value for promotion eligibility; handle backend “or equivalent” naming without assumptions.
- Traceability requirements:
  - After promotion, promoted estimate lines must have at least one traceability id available (workOrderId and/or workOrderLineId, nullable fields).
  - Work Order response/view model must include an estimate id field (name may vary) to render the header link back to the source estimate.
- Confirmation dialog is informational; it must clearly communicate that deferred items are not executable until later approval flow.
- API/response handling:
  - Prefer 201 Created; response may include created work order id and optionally updated estimate header/lines snapshot—UI should consume when present.
  - Include If-Match header on promote request.
- Error handling: errors follow standard envelope; display message when present; do not log sensitive data.
