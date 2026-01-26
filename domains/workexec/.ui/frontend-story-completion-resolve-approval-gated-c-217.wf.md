# [FRONTEND] [STORY] Completion: Resolve Approval-Gated Change Requests
## Purpose
Enable Service Advisors to see when pending, approval-gated change requests block work order completion and quickly navigate to resolve them. Provide a Work Order–scoped Change Requests list and detail navigation so advisors can review requests across statuses. Ensure the UI reflects the current completion-blocking state derived from change request status and shows clear empty states when none exist.

## Components
- Work Order detail/edit screen
  - Completion blockers panel/section
  - Blocker row: “Pending change requests” with count and status summary
  - Link/button: “View change requests” (navigates to Work Order Change Requests screen)
  - Display field: current completion-related value from WorkOrder (read-only)
- Work Order Change Requests screen
  - Header with Work Order context (ID/identifier) and back navigation
  - Change Requests list (all returned by API)
  - List item fields: description, status, requestedAt, emergency flag indicator (if present)
  - Empty state message: “No change requests for this work order.”
- Change Request detail view (navigation target)
  - Read-only summary fields (at minimum those shown in list)
  - Items display section (services/parts; display-only)
  - Emergency fields display (if present)

## Layout
- Work Order detail/edit
  - Top: Work Order header + key identifiers
  - Main: editable work order sections
  - Right or upper-main panel: Completion Blockers
    - Blocker row + “View change requests” link
- Change Requests screen
  - Top: Back ← Work Order / “Change Requests”
  - Main: List of change requests (stacked cards/rows)
  - Empty state replaces list when none
- Detail navigation
  - List item tap/click opens Change Request detail view

## Interaction Flow
1. Open Work Order detail/edit screen.
2. System evaluates change requests for the work order:
   1. If at least one ChangeRequest has status = pending (approval-gated), show a completion blocker indicating pending change requests.
   2. Display the current completion-related value from the WorkOrder (read-only) alongside/within the blockers area.
3. User selects “View change requests” from the blocker panel.
4. Navigate to the Work Order Change Requests screen (route requires workOrderId).
5. System loads and lists all change requests returned by the backend for that work order (multiple statuses allowed).
6. User scans list entries; each entry shows description, status, requestedAt, and an emergency indicator when present.
7. User selects a change request entry.
8. Navigate to Change Request detail view for that change request (treat changeRequestId as an opaque string).
9. Empty/edge cases:
   1. If no change requests exist: show “No change requests for this work order.” on the list screen.
   2. If no completion-blocking change requests exist: show “No completion blockers from change requests.” in the Work Order blockers panel.

## Notes
- Entities are frontend view models: WorkOrder is read-only in this story; ChangeRequest IDs are treated as opaque strings even if backend uses numeric IDs.
- Acceptance criteria:
  - Pending change requests visibly block completion on the Work Order screen.
  - Blocker includes navigation to the Work Order Change Requests screen.
  - Change Requests screen lists all returned change requests and supports navigation to detail.
  - List entries must include: description, status, requestedAt, and emergency flag when present.
  - Required empty states must match exact copy provided.
- Integration-contract risk: statuses and enums are opaque; UI must not assume values beyond the specified pending status check and display of returned status text.
- ChangeRequest Items and emergency fields are display-only; they may arrive embedded or by reference—UI should render what is available without requiring edits.
