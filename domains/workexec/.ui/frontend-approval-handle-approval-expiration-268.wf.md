# [FRONTEND] Approval: Handle Approval Expiration
## Purpose
Ensure the Approval Detail screen correctly reflects when an approval has expired and prevents users from taking invalid actions. If an approval is already expired, the UI must clearly indicate this and disable/hide Approve/Deny. If expiration occurs between viewing and submitting, the UI must handle the backend “expired approval” error by showing an error message, reloading, and updating the status to expired with actions no longer available.

## Components
- Page header (Approval Detail title + optional breadcrumb/back link)
- Approval summary panel (status badge/label, approval metadata)
- Status indicator (includes explicit “Expired” state)
- Inline alert/message area (info/warning/error)
- Primary action buttons: Approve, Deny
- Disabled state styling and/or hidden state logic for actions
- Loading indicator (initial load and reload after submit/error)
- Success confirmation message (toast or inline)
- Error message for “Approval expired” backend response

## Layout
- Top: Header (Back link | “Approval Detail”)
- Main: Summary panel (Status badge prominent; metadata below)
- Main (below summary): Message/alert area (shows expired notice or submit errors)
- Bottom/right of main: Action bar (Approve | Deny) or replaced by non-actionable state text

## Interaction Flow
1. View expired approval (on load)
   1. User navigates to Approval Detail for an approval whose status is expired (or equivalent expired terminal state).
   2. UI renders status as “Expired” (or explicit expired indicator).
   3. UI shows an inline message: “This approval has expired.”
   4. Approve and Deny are not actionable (disabled or hidden).
   5. No approve/deny request can be initiated from the UI in this state.

2. Expiration handled on submit (race condition)
   1. User opens an approval that appears actionable; Approve/Deny are enabled.
   2. Approval expires before the user clicks Approve or Deny.
   3. User clicks Approve (or Deny).
   4. UI sends the request; backend responds with an “expired approval” error code.
   5. UI displays an error message: “Approval expired.”
   6. UI reloads/refetches the approval detail.
   7. UI updates status display to “Expired.”
   8. Approve/Deny become non-actionable (disabled or hidden) after reload.

3. Non-expired approval can be approved (happy path)
   1. User opens an actionable approval; Approve/Deny are enabled.
   2. User clicks Approve.
   3. Backend returns success.
   4. UI shows a success confirmation.
   5. UI updates the status to the terminal approved status (via response binding or after reload).
   6. Approve/Deny are no longer actionable once in terminal approved state.

## Notes
- Acceptance criteria:
  - Expired approvals must be clearly indicated via status and an explicit message.
  - Approve/Deny must be disabled or hidden when approval is expired (or otherwise non-actionable/terminal).
  - When backend returns “expired approval” on submit, UI must show “Approval expired,” reload the detail, and reflect expired status with actions disabled/hidden.
  - For successful approval, UI must confirm success and transition to terminal approved status with actions disabled/hidden.
- Ensure “no approve/deny request is sent” when the UI already knows the approval is expired (i.e., actions are not actionable).
- Implement consistent handling for both Approve and Deny paths (same expiration error behavior).
- Reload behavior: show a loading state during refetch to avoid stale status/actions.
- TODO (design/dev): define exact wording, alert severity (warning vs error), and whether actions are disabled vs hidden for expired/terminal states.
