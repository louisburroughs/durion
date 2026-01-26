# [FRONTEND] [STORY] Completion: Reopen Completed Workorder (Controlled)
## Purpose
Enable authorized Back Office Managers to reopen a completed work order in a controlled way by providing a reopen reason and confirming the action. Handle stale UI and concurrency conflicts gracefully by refreshing the work order state and capabilities when the backend rejects the action. Ensure invoiced/issued/finalized work orders cannot be reopened and communicate the restriction clearly.

## Components
- Work Order Detail header (work order identifier, current status badge)
- Capability-gated primary action: “Reopen Completed Work Order”
- Invoice gating indicator / invoice eligibility indicator (read-only)
- Reopen confirmation modal/dialog
  - Reopen reason text input (required)
  - Confirm button
  - Cancel button
- Inline banners/toasts
  - Success confirmation (single)
  - Conflict/refresh banner (reopened by another user / state changed)
  - Unauthorized/forbidden message (403)
  - Domain error message for invoiced restriction
- Work order detail refresh mechanism (loading state/spinner)
- Audit metadata panel/section
  - Standard created/updated metadata (if available)
  - Reopen metadata (if provided): reopened by, reopened at, reopen reason (conditional)
- Status/transition history panel (conditional on endpoint availability)
  - Latest reopen-related transition entry (if available)
  - Latest completion snapshot note indicating no longer valid for invoicing (wording only)

## Layout
- Top: Work Order header (ID/title) + Status badge (e.g., Completed/Reopened) + invoice gating indicator
- Main: Work order details content (existing sections) with a right/top action area containing “Reopen Completed Work Order” (only when eligible)
- Below main details: Audit metadata panel; then Status/History panel (if available)
- Modal overlays page on action: centered dialog with reason field + confirm/cancel

## Interaction Flow
1. Authorized reopen (completed, not invoiced)
   1. User views a completed work order that is not invoiced (invoice gating indicator does not indicate issued/finalized).
   2. User clicks “Reopen Completed Work Order”.
   3. System opens modal requiring a reopen reason.
   4. User enters reason (e.g., “Corrected labor hours”) and clicks Confirm.
   5. UI sends reopen request to backend including the reason in the request body and includes an If-Match header.
   6. On success, UI shows a single success confirmation.
   7. UI refreshes work order detail and reflects reopened state.
   8. UI indicates invoice eligibility is revoked until re-completion.

2. Concurrency conflict: another user reopens first
   1. User attempts reopen, but backend responds with 409 invalid state (or equivalent).
   2. UI shows a conflict/state-changed message and triggers a work order detail refresh.
   3. After refresh, UI shows the reopened state (and, if available via history, indicates who/when reopened).

3. Unauthorized access due to stale UI/capabilities
   1. UI shows the action, but backend responds 403 on attempt.
   2. UI displays a 403/unauthorized message.
   3. UI refreshes capabilities and work order detail.
   4. Work order remains unchanged; action visibility updates based on refreshed capabilities.

4. Invoice issued/finalized: cannot reopen
   1. Authorized user attempts reopen with a valid reason.
   2. Backend responds 4xx with a domain error code indicating invoiced work orders cannot be reopened.
   3. UI displays: “Cannot reopen a work order that has been invoiced.”
   4. UI refreshes work order detail; work order remains unchanged.

## Notes
- Action must be capability-gated (Back Office Manager with required capability) and state-gated (completed + not invoiced/issued/finalized).
- Reopen modal requires a reason; do not allow confirm until reason is present.
- Always include If-Match header on reopen request to support concurrency control; handle 409 by refreshing and reflecting latest state.
- On 403, treat as stale UI: show message, refresh capabilities + work order detail, and do not mutate UI state beyond refresh.
- User-visible audit data:
  - Always show standard created/updated metadata if available on the work order.
  - If backend returns reopen metadata, show reopened by and reopened at (render user display name if available via existing directory lookup).
  - Show reopen reason only if backend returns it (do not assume it is always returned).
- Status history:
  - If a transition history endpoint exists, show the latest reopen-related entry (event type or transition reason).
  - If a snapshot history endpoint exists, show the latest completion snapshot and add wording that it is no longer valid for invoicing (do not invent snapshot statuses).
- Ensure success confirmation is shown once (avoid duplicate toast + banner for the same success).
