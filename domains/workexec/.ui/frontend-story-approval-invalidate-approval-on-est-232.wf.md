# [FRONTEND] [STORY] Approval: Invalidate Approval on Estimate Revision
## Purpose
Ensure the Work Order detail UI clearly communicates when customer approval has been invalidated due to an estimate revision and re-approval is required. Surface the invalidation context (timestamp, reason, and change details) from transition/history data so users understand what changed. Prevent the Work Order from appearing as “Approved/Customer Approved” while re-approval is required, and remove the banner once the backend indicates re-approval is no longer needed after refresh.

## Components
- Work Order detail header (Work Order identifier/title)
- Status badge/label (current Work Order status)
- Re-approval required banner/alert (prominent, dismiss behavior unspecified)
- Approval state indicator (e.g., “Customer approval invalidated”)
- Estimate summary panel (current total, estimate version)
- Change details sub-panel (old vs new totals; version change)
- History/Transitions section
  - Transition list items (type/status, timestamp)
  - Transition detail row/expandable area (reason, old/new totals, version change)
- Refresh behavior (page reload / pull-to-refresh / re-fetch on navigation)

## Layout
- Top: Work Order header + status badge (ensure status does not read “Approved/Customer Approved” when re-approval required)
- Main (above fold): Re-approval required banner + brief explanation text
- Main (details): Estimate summary panel; if available, show “Old vs New” totals + estimate version change near estimate info
- Lower main: History/Transitions section listing events; latest invalidation transition highlighted or easily discoverable

## Interaction Flow
1. Load Work Order detail screen.
2. If backend status indicates re-approval required:
   1. Display a prominent banner stating customer approval is invalidated and re-approval is required.
   2. Ensure the status/approval indicator does not show “Approved/Customer Approved.”
3. User scrolls to (or opens) History/Transitions section.
4. If transitions/history includes a latest transition indicating re-approval required:
   1. Show invalidation timestamp (from transition) and reason (from transition).
   2. If provided, display old vs new totals (e.g., previous total → revised total).
   3. If provided, display estimate version change (e.g., vX → vY).
5. User refreshes the Work Order detail screen after re-approval occurs.
6. If backend no longer indicates re-approval required and status reflects approved:
   1. Remove/hide the re-approval-required banner.
   2. Allow the Work Order to display as approved per normal status rules.
   3. Keep prior invalidation transition visible in History (if History is accessible).

## Notes
- Acceptance criteria:
  - When re-approval is required, UI must show a clear indicator/banner and must not present the Work Order as “Approved/Customer Approved.”
  - Invalidation details must be sourced from the latest relevant transition in history: timestamp + reason; show old/new totals and estimate version change when available.
  - After refresh and backend indicates re-approval is no longer required, banner must not be shown; historical invalidation record remains in History.
- Data dependency: requires backend-defined “re-approval required” status and a transitions/history payload that includes a transition type/status indicating invalidation plus optional fields (reason, old/new totals, estimate version info).
- Display rules:
  - If old/new totals or version change are missing, omit those rows rather than showing placeholders.
  - Prefer highlighting the latest invalidation transition to reduce user effort to find context.
- TODO (design/dev):
  - Define exact copy for banner and approval indicator.
  - Define whether banner is dismissible and whether dismissal persists.
  - Define formatting for totals (currency) and version labels (e.g., “Estimate v2”).
