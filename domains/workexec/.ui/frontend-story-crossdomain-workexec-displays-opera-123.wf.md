# [FRONTEND] [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View
## Purpose
Display a work order’s Operational Context within the Work Order screen, loading asynchronously from the Shopmgr system of record. Allow managers to override Operational Context values in Workexec with auditing and optimistic concurrency safety. Provide clear user feedback for loading, refresh, and error states (including conflict handling).

## Components
- Work Order header (existing)
- Operational Context panel/section
  - Loading indicator (skeleton/spinner)
  - Read-only fields (context summary)
  - Lists (e.g., assigned mechanics/participants)
  - “Refresh” action/link
  - Error banner/inline error state with retry
- Manager-only “Override Operational Context” button
- Override modal
  - Warning callout when work has started (audited)
  - Form fields (only supported override keys)
  - Field-level validation messages
  - Generic form error message area
  - Primary action: “Submit override”
  - Secondary action: “Cancel”
- Toast/inline success message (optional) on successful override + refresh

## Layout
- Top: Work Order header
- Main content:
  - [Work Order Details …]
  - [Operational Context (async)] right below/within details as a distinct card/section
    - Header row: “Operational Context” + (Refresh) + (Override button if manager)
    - Body: key fields + lists; or loading/error state
- Modal overlay: Override Operational Context form

## Interaction Flow
1. User opens a Work Order view.
2. UI immediately renders the Work Order screen and shows the Operational Context section in a loading state (asynchronous fetch).
3. On successful load:
   1. UI renders Operational Context using the minimum DTO needed (IDs/texts/lists; handle missing optional text by falling back to IDs where specified, e.g., show mechanicId if mechanic text absent).
   2. If lists are absent, treat as empty where defaults are specified.
4. If Operational Context load fails:
   1. 404: show “Operational context not found for this work order.”
   2. Other errors: show “Unable to load operational context. Refresh and try again.” with a Refresh/Retry action.
5. User clicks “Refresh” in the Operational Context section:
   1. UI re-fetches and updates the section; keep the rest of the Work Order screen usable.
6. Manager override (capability-gated):
   1. If user has override capability, show “Override Operational Context” button.
   2. User opens override modal.
   3. If the work order is started (started/status is in-progress or later), show a warning: work has started and the override will be audited.
   4. User edits at least one allowed field and submits.
7. Override submission outcomes:
   1. Success: close modal (or show success), then refresh Operational Context display from GET to reflect latest server state.
   2. 400: if field-level errors are provided, display them on corresponding fields; otherwise show “Please correct the highlighted fields.”
   3. 409 conflict (when version token exists): show a conflict message (e.g., “Operational context was updated by someone else. Refresh and try again.”) and prompt refresh; do not overwrite displayed data until refreshed.
   4. 404: show “Operational context not found for this work order.”
   5. Other errors: show a generic failure message and keep modal open for retry.

## Notes
- Operational Context is sourced from Shopmgr (system of record) and displayed in Workexec Work Order view; loading must be asynchronous and non-blocking.
- Override is manager-only, audited, and concurrency-safe; support optimistic concurrency via a version token if provided by GET and required by backend.
- Override request must include an object with at least one field; only send supported keys. Do not send actor/manager identifiers from the client unless explicitly required by an existing backend contract (actor derived from auth context to avoid spoofing).
- DTO rendering constraints:
  - Some fields are optional but expected; UI must tolerate missing values.
  - For mechanic display: if mechanic text is absent, display mechanicId.
  - Lists may be required but empty; optional lists default to empty.
- Error copy requirements:
  - Load failure (non-404): “Unable to load operational context. Refresh and try again.”
  - Override 400: field-level errors if present; otherwise “Please correct the highlighted fields.”
  - 404: “Operational context not found for this work order.”
- TODO (design/dev): confirm which override fields are supported by backend (e.g., list<id> field) and hide/omit unsupported fields from the UI.
