# [FRONTEND] [STORY] Estimate: Revise Estimate Prior to Approval

## Purpose
Enable a Service Advisor to revise an estimate by creating a new editable version linked to the prior version, preserving a complete version history. Revising must create a new active version and make prior versions read-only. If the estimate was previously approved, revising must invalidate prior approval and clearly indicate that approval is required again.

## Components
- Page header: Estimate identifier + current status + active version number
- Primary actions: Revise button, Save/Update button(s), Cancel/Back
- Revise confirmation modal (with confirm/cancel)
- Approval-required banner (shown when revising from an approved version)
- Estimate edit form (active version only)
  - Line items list/table (editable on active version)
  - Pricing/amount fields (editable only if permitted)
  - Notes / internal notes fields (as applicable)
  - Optional revision reason field (if stored/exposed)
- Version History panel/list
  - Rows: versionNumber, createdAt, createdBy, (status at time of version if available), revisionReason (if available)
  - Version row click to view read-only snapshot
- Read-only version view state (when viewing non-active versions)
  - Disabled form fields / non-editable line items
  - Indicator “Read-only (historical version)”
- Inline messaging/toasts for success/failure (e.g., version created, save failed)

## Layout
- Top: Header (Estimate ID + Status + “Active Version: vN”) + primary actions (Revise, Save)
- Main (left/center): Estimate edit form (active version) or read-only snapshot (historical version)
- Right sidebar: Version History list (scrollable), with active version highlighted
- Inline (top of main): Conditional banner area (e.g., “Approval required”)

## Interaction Flow
1. Open Estimate Edit screen
   1. System displays estimate status and active version number.
   2. Version History shows all versions; active version is highlighted; prior versions are selectable and read-only.
2. Revise a Draft estimate (creates new version and opens for editing)
   1. User clicks Revise.
   2. Confirmation modal appears explaining a new version will be created and prior version becomes read-only.
   3. User confirms.
   4. System creates a new estimate version linked to the previous active version.
   5. UI reloads/navigates to the edit screen with the new active version number shown.
   6. Version History updates: prior version appears as read-only; new version is active and editable.
3. Revise an Approved estimate (requires re-approval)
   1. User clicks Revise and confirms in the modal.
   2. System creates a new active version in an editable draft-like state.
   3. UI shows a prominent banner: “Approval required” (generic wording).
   4. Version History retains the previously approved version as read-only; new version is active and editable.
4. Revise a Declined estimate (creates new draft version)
   1. User clicks Revise and confirms.
   2. System creates a new active version in Draft state.
   3. UI allows editing the new version; declined version remains read-only in Version History.
5. View historical versions
   1. User selects a non-active version from Version History.
   2. UI switches to read-only snapshot view; editing controls are disabled/hidden.
   3. User can return to active version by selecting it in Version History.
6. Error/edge handling
   1. If version creation fails, keep user on current screen and show an error message; do not change active version indicator.
   2. If user lacks revise capability, hide/disable Revise action (or show permission error on attempt).

## Notes
- Revising always creates a new version and links it to the prior version; prior versions must be read-only.
- Active version is the only editable version; ensure clear visual distinction between active vs historical versions.
- When revising from Approved, prior approvals tied to earlier versions must be invalidated; show “Approval required” banner on the new active version.
- Version History should display: versionNumber, createdAt, createdBy; include estimate status at time of version if provided, otherwise show current status alongside version metadata.
- Include revisionReason in Version History only if stored/exposed by backend; otherwise omit without placeholder.
- Status values and editability rules depend on backend enums; TODO: confirm allowed status values and exact “editable when status is …” conditions.
- Pricing fields may be editable only if permitted; UI should respect permissions/capabilities and backend-calculated read-only totals.
- Confirmation modal copy should avoid specifying approval method; keep generic per requirement.
