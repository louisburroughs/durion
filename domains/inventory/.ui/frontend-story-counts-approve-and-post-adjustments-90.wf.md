# [FRONTEND] [STORY] Counts: Approve and Post Adjustments from Cycle Count
## Purpose
Provide an approval queue and detail view for inventory adjustments generated from cycle counts so authorized users can approve/post or reject adjustments. The UI must clearly show computed variances and required approval tier, enforce permission-aware gating, and surface status/audit history. Users need reliable feedback for validation, authorization, and concurrency failures, plus visibility into posted outcomes (ledger reference).

## Components
- Global header with breadcrumbs: Inventory → Counts → Adjustment Approvals
- Page title: “Adjustment Approvals”
- Optional entry point: “Pending approvals (N)” badge/link (only if an Inventory landing screen and count endpoint exist)
- Filters/search bar:
  - Status (default: Pending)
  - Required tier
  - Location
  - Product SKU
  - Date range (created/updated)
- Results table (approval queue) with:
  - Adjustment ID (shortened) / created timestamp
  - Product SKU
  - Location (and Bin if present)
  - Counted qty, Expected qty (if provided)
  - Variance qty, Variance value (if provided)
  - Required tier
  - Status
- Pagination controls (cursor-based “Next/Previous”)
- Row click / “View” action to open detail
- Adjustment Detail view:
  - Summary header: status badge + required tier + identifiers
  - Read-only fields section (quantities/values; marked sensitive: do not log)
  - Derived/computed variance section (display only what is provided)
  - Ledger reference panel (shown when posted)
  - Audit/status history timeline (created/updated/approved/rejected + actors if provided)
- Action panel (permission gated; only when status = Pending):
  - Primary button: “Approve/Post”
  - Secondary button: “Reject”
- Reject modal/drawer:
  - Textarea: rejection reason (required)
  - Buttons: “Submit Rejection”, “Cancel”
- Inline alerts/toasts for success and errors (validation/authorization/concurrency/terminal failure)

## Layout
- Top: Breadcrumbs + Page Title + (optional) Pending approvals badge entry point
- Main: Filters row above a full-width results table; pagination at bottom-right
- Detail (navigated page or right-side panel): Header/status + two-column info (Identifiers/Workflow left, Quantities/Variance right), then Audit History and Ledger Reference below; Action panel pinned top-right (only when Pending)

## Interaction Flow
1. Navigate via main nav: Inventory → Counts → Adjustment Approvals.
2. System loads approval queue (pending adjustments) with cursor pagination; show loading state and empty state (“No pending adjustments”).
3. User filters by SKU/location/tier/date; list refreshes; preserve filters on navigation back from detail.
4. User selects an adjustment row to open Adjustment Detail.
5. Detail loads and displays:
   1) Identifiers (adjustmentId, productSku, location, optional bin),
   2) Quantities/values (read-only; do not log),
   3) Variances/derived fields if provided,
   4) Required tier (when status is pending),
   5) Audit/status history.
6. Permission gating:
   1) If user lacks approve permission for required tier, hide/disable “Approve/Post”.
   2) If user lacks reject permission, hide/disable “Reject” and rejection reason input.
7. Approve/Post (Scenario: Tier 1 approver):
   1) With status = Pending and user authorized, click “Approve/Post”.
   2) UI calls approve/post endpoint via Moqui proxy (optionally with idempotency token if platform supports).
   3) On success, refresh detail to updated status (Posted/Approved as returned), disable actions, and display ledger reference if returned.
   4) Remove item from pending queue on return to list (or auto-refresh list).
8. Reject (Scenario: authorized reject):
   1) With status = Pending and user authorized, click “Reject”.
   2) Modal opens; user enters required rejection reason and submits.
   3) UI calls reject endpoint; on success refreshes to status Rejected, shows rejection reason read-only, disables actions, and item no longer appears in pending queue.
9. Error/edge handling:
   1) Validation errors (e.g., missing rejection reason): show inline field error; do not submit.
   2) Authorization failure: show error alert; keep actions disabled/hidden after refresh.
   3) Concurrency/state change (no longer pending): show conflict message; refresh detail and list.
   4) Terminal failure state (if backend returns): show failure message; no retry unless backend explicitly supports a retry transition.

## Notes
- All fields are read-only except rejection reason, which is editable only during Reject when status = Pending and user is authorized to reject.
- Quantities/values are sensitive: ensure UI does not log these values (client logs/analytics/error payloads).
- Prefer backend-provided derived fields (variance qty/value); if absent, display only provided fields (do not invent calculations unless explicitly supported).
- Status behavior:
  - Pending: show action panel (permission gated).
  - Approved (informational): no actions.
  - Posted: show ledger reference; no actions.
  - Rejected: show rejection reason; no actions.
  - Failed (terminal): show failure message if provided; no retry unless supported.
- List API and detail/transition endpoints are TBD (Moqui proxy); implement with cursor pagination token (or equivalent).
- Optional “Pending approvals (N)” entry point should be included only if an Inventory landing screen exists and a count endpoint is available; otherwise omit entirely.
- If backend uses stockItemId instead of productSku, backend must also provide productSku for UX (otherwise list/detail must still show a human-meaningful identifier).
