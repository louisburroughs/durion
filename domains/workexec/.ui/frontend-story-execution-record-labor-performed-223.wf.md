# [FRONTEND] [STORY] Execution: Record Labor Performed
## Purpose
Enable Technicians to record labor performed against a work order’s service items while the work order is in an eligible state. Provide a simple form to create time-based or flat-rate completion entries with backend-enforced validation, idempotency, and audit visibility. Ensure users can view existing labor entries per service item and understand errors or ineligible conditions.

## Components
- Page header: Work Order identifier + status
- Work order context panel (read-only): workOrderId, serviceItemId/name, work order status, service item status, assignment info, eligibility flag (if provided)
- Service items list (selectable rows/cards): item name/identifier, itemStatus, assignment indicator, labor eligibility indicator
- Service item detail section: selected service item summary + “Record labor” action
- Labor entries list (read-only): rows with created timestamp, technician name/ID, labor type, hours or completion, notes
- Empty state messages:
  - No service items available for labor entry
  - No labor recorded yet
- Record Labor form (inline panel or modal):
  - Labor Type selector (Time-based / Flat-rate completion)
  - Hours input (decimal) shown/required for Time-based
  - Completion confirmation checkbox/toggle shown/required for Flat-rate
  - Notes textarea (optional)
  - Submit button
  - Cancel button
- Inline validation/error messaging area (field-level + form-level)
- Permission/eligibility blocking banner (non-dismissable while blocked)

## Layout
- Top: Header with Work Order ID and status
- Main (two-column):
  - Left: Service Items list (select item)
  - Right: Selected Service Item detail
    - Context panel (read-only)
    - “Record labor” button
    - Record Labor form (appears inline under button or as modal)
    - Labor Entries list (below form/button)
- Footer area (optional): refresh action / last updated timestamp

## Interaction Flow
1. Load Work Order Detail screen.
2. System loads work order + service items (including statuses, assignment fields, and any eligibility flag/capability).
3. If no service items exist, show empty state: “No service items available for labor entry.”
4. User selects a service item from the list.
5. System displays service item context and labor entries (embedded in detail or loaded on selection).
6. If no labor entries exist for the selected item, show: “No labor recorded yet.”
7. If user lacks permission/capability to record labor, show blocking message: “You do not have permission to record labor.” Hide/disable the Record Labor action.
8. If work order status is not eligible (e.g., not WORK_IN_PROGRESS) or service item status is ineligible (e.g., PENDING_APPROVAL or CANCELLED), disable Record Labor and show a brief reason banner.
9. User clicks “Record labor” to open the form.
10. User selects “Time-based.”
11. Hours field appears and is required; user enters hours (e.g., 2.5). Optional notes may be entered.
12. On submit, UI sends create request with Idempotency-Key.
13. If hours missing, show inline error: “Hours is required.”
14. If hours <= 0, show inline error: “Hours must be greater than 0.”
15. If backend returns assignment/state validation failure, show inline/form error: “You are not assigned to this service item. Please see the Service Advisor.”
16. On success, close/reset form and refresh labor entries list; new entry appears with created timestamp and technician identity.
17. User selects “Flat-rate completion.”
18. Completion confirmation control appears and is required (must be true); hours input is hidden/disabled.
19. On submit, UI sends create request with Idempotency-Key; on success, refresh list showing FLAT_RATE entry with completion indicated.
20. Idempotency outcome handling: if backend indicates duplicate/replayed request, show non-blocking message and ensure list reflects the existing created entry (no duplicate rows).

## Notes
- Must extend existing Moqui workexec screens rather than replace them.
- Backend is source of truth for:
  - Assignment policy (assigned vs not assigned)
  - Valid work order/service item state rules
  - Validation for hours and flat-rate completion constraints
  - Audit logging + event emission
  - Idempotency keyed by Idempotency-Key; UI should supply one per submit and handle replay outcomes gracefully.
- Standard error envelope is expected; UI should normalize and map to field-level vs form-level messages.
- UI MUST NOT auto-call a “work order start” action on first labor entry unless backend explicitly requires it and exposes a capability/flag indicating this policy.
- Labor entry list must display user-visible audit data:
  - Created timestamp (display in user timezone; stored UTC)
  - Technician display name if available, else technician ID
  - Notes (if any)
  - Labor type and hours (nullable for FLAT_RATE) or completion indicator
- Acceptance criteria coverage:
  - Time-based: create TIME_BASED with laborHours > 0; appears after refresh with timestamp + technician identity.
  - Flat-rate: create FLAT_RATE with isComplete true; appears after refresh.
- TODO (blocking for implementation): confirm exact Moqui service names/REST endpoints and payload schemas for:
  - Load work order + service items (including identifiers/statuses/assignment/eligibility)
  - List labor entries for a service item (embedded vs separate)
  - Create labor entry request/response (including idempotency and any correction/supersede support)
