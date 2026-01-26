# [FRONTEND] [STORY] Putaway: Generate Put-away Tasks from Staging

## Purpose
Provide a UI to list and manage put-away tasks generated from staging, enabling warehouse users to quickly find tasks, review destination suggestions, and claim work. The experience must handle cases where the destination was adjusted (showing original vs final suggestions) and cases where manual destination selection is required. The UI must integrate with Moqui proxy routes for listing and claiming tasks (endpoints TBD).

## Components
- Page header: “Put-away Tasks”
- Filter/search bar
  - Site/facility selector (optional)
  - Status filter (e.g., Unassigned/Assigned/In progress; exact values TBD)
  - Assignee filter (optional; includes “Me” shortcut)
  - Task type/source filter (optional; conceptual)
  - Pagination controls (page size, page number)
  - Free-text search (optional; string)
- Tasks list/table
  - Columns: Task ID, Status, Assignee, From (staging), Destination (suggested/final), Site, Created/Updated (as available)
  - Row actions: “View”, “Claim” (when eligible)
- Task detail panel/page
  - Task summary (ID, status, assignee, site)
  - Source/staging info
  - Destination section
    - Final suggested destination (nullable)
    - Original suggested destination (shown when fallback applies)
    - Fallback indicator (when destination adjusted)
    - “Destination selection required” banner (when applicable)
  - Actions: “Claim” (when unassigned + permitted), “Select destination” (when required + permitted)
- Destination selection modal/drawer
  - Storage location picker (filtered to task site)
  - List/search within allowed locations (no free-text UUID entry)
  - Confirm/Cancel buttons
- Inline permission/eligibility messaging (e.g., disabled actions with tooltip text)

## Layout
- Top: Page header + primary filters (site, status, assignee=Me, search) + pagination summary
- Main: Tasks list/table with row actions aligned right
- Right (or separate route): Task detail view when a task is selected
- Modal/Drawer: Destination selection picker overlay from detail
- Inline banners within detail: fallback indicator; destination selection required

## Interaction Flow
1. User opens Put-away Tasks page.
2. UI calls Moqui proxy “List tasks” endpoint (TBD) with default query params (e.g., status, pagination).
3. User refines filters (status, assignee=Me, site, search); UI refetches list with updated query params.
4. User selects a task row; UI opens task detail (panel or navigates to detail route) and displays task metadata including destination fields.
5. If task has destination adjusted metadata (non-null indicator) and includes both original and final suggested destinations:
   1. UI shows a fallback indicator in the destination section.
   2. UI displays both original suggested destination and final suggested destination.
6. Claim from list:
   1. For an unassigned task in the claimable status, user clicks “Claim”.
   2. UI calls Moqui proxy “Claim task” endpoint (TBD) with task identifier; server sets assignee = current user.
   3. On success, UI updates the row to show Assigned to current user (or refetches list) and keeps status consistent.
7. Claim from detail:
   1. User clicks “Claim” in task detail (same eligibility rules).
   2. UI calls claim endpoint; on success, UI updates detail assignee/status and ensures list reflects the change (refetch or local update).
8. Manual destination selection required:
   1. When user opens task detail and task indicates destination selection is required, UI shows a prominent banner.
   2. If user has destination-selection permission, UI shows “Select destination”; otherwise show read-only message.
   3. User clicks “Select destination”; UI opens destination picker filtered to storage locations within the task’s site.
   4. User selects a location from allowed results (no free-text UUID entry) and confirms; UI applies selection (save endpoint TBD) and updates destination display.

## Notes
- Endpoints are blocking/TBD: both “List tasks” and “Claim task” must be Moqui proxy routes; exact paths and param names are not finalized.
- List tasks response must include task objects (or summary objects) sufficient for list display and to open detail; ensure destination-related fields are present for fallback scenario (original vs final suggested destinations + indicator).
- Claim behavior: server determines assignee = current user; UI should handle either “updated task returned” or “204 + refetch” pattern.
- Permissions:
  - Claim requires put-away task claim permission (exact string per inventory permission convention; DECISION-INVENTORY-010).
  - Claim endpoint selection per proxy convention (DECISION-INVENTORY-002).
  - Destination selection action only visible/enabled when user has destination-selection permission.
- Destination fields are nullable; UI must handle null suggested/final destination and show “requires selection” state when applicable.
- Destination picker constraints: filter to storage locations within the task’s facility/site; do not allow free-text UUID entry.
- Status values and exact filtering options are conceptual; align UI labels with backend enums once defined.
