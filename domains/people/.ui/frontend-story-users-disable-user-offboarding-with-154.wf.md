# [FRONTEND] [STORY] Users: Disable User (Offboarding) Without Losing History
## Purpose
Enable admins to disable (offboard) a user from the user detail page without deleting historical data. The UI must present read-only user/person/employment details and conditionally allow a “Disable User” action when the backend indicates it’s permitted. The flow must support optional reason inputs and handle concurrency conflicts by refreshing to the latest server state.

## Components
- Page header: “User Detail” + user identifier (name/email) + status badge (ACTIVE/DISABLED/other)
- Read-only user detail section
  - User ID (UUID)
  - Display name
  - Status (enum aligned with lifecycle)
  - Disabled at (datetime; shown when status != ACTIVE)
  - Optional fields (e.g., notes/metadata if present)
  - Optional “canDisable”/action-allowed indicator (implicit; used to show/hide action)
  - Optional tags/roles/attributes list (array)
  - Optional updatedAt/version (for optimistic concurrency if required)
- Read-only person/employment section
  - Person ID (UUID)
  - Preferred display name (prefer person name if provided)
  - Employment/user lifecycle status (aligned enum)
- Primary action button: “Disable User” (visible only when allowed and user is ACTIVE)
- Disable confirmation modal (editable inputs)
  - Reason (text; optional unless backend requires)
  - Additional note/comment (text; optional)
  - Disable reason code (select; optional; constrained to backend-provided options if present)
  - Buttons: Cancel, Confirm Disable (primary/destructive)
- Inline alerts/toasts
  - Success message (includes disabled status + disabled timestamp from response)
  - Conflict/error message (409 concurrency conflict)
- Loading/refresh states (spinner/skeleton for detail reload)

## Layout
- Top: Breadcrumb/back link + Page title + Status badge
- Main (single column):
  - User Detail (read-only card)
  - Person/Employment (read-only card)
  - Actions row (right-aligned): [Disable User] (conditional)
- Modal overlay centered: “Disable User” confirmation + inputs + action buttons

## Interaction Flow
1. View user detail (default)
   1. Load user detail payload and render read-only fields (user + person/employment).
   2. If status is ACTIVE and backend indicates disable action is allowed, show “Disable User”; otherwise hide it.
2. Scenario 1: Admin disables an active user successfully
   1. Admin clicks “Disable User”.
   2. Open confirmation modal with optional inputs (reason text, note, reason code if available).
   3. Admin confirms and submits.
   4. Frontend calls the disable endpoint with selected reason code (if any) and optional reason fields (and concurrency token if required).
   5. On success:
      1. Refresh/reload user detail from server.
      2. Display success feedback and show updated status (DISABLED) and disabled-at timestamp from backend response.
      3. Hide “Disable User” action (no longer available for this user).
3. Scenario 5: Conflict on disable due to concurrent change (HTTP 409)
   1. Admin has the disable modal open for an ACTIVE user.
   2. Another admin disables the user before submission.
   3. First admin submits; backend returns 409 with an error message.
   4. UI shows the conflict message (inline in modal or as alert).
   5. UI refreshes/reloads user detail and reflects latest server status (DISABLED).
   6. Close modal (or disable submit) and hide “Disable User” action.

## Notes
- Disabling must not remove historical records; user detail remains viewable read-only with updated status and disabled timestamp.
- “Disable User” must be conditional: only for ACTIVE users and only when backend indicates the action is allowed (e.g., via a flag or allowed-actions list).
- Disabled-at datetime is required to display when status != ACTIVE; ensure formatting is consistent with the app’s datetime conventions.
- Concurrency handling: if backend uses optimistic concurrency (e.g., updatedAt/version), include it in the disable request; on mismatch, treat as conflict and reload.
- Error handling: for 409, show backend-provided message and force a detail refresh so the UI matches server truth; ensure the disable action disappears after refresh.
- Requirements are partially incomplete; keep modal inputs flexible (optional fields, optional reason-code select constrained to backend-provided options when present).
