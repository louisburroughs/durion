# [FRONTEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR
## Purpose
Enable Dispatch users to import the mechanic roster and associated skills from the HR system into the Dispatch application. Reduce manual data entry and ensure mechanic availability/skill data is consistent with HR records. Provide a guided import experience with preview, validation, and clear outcomes so users can confidently sync changes.

## Components
- Page header: “Import Mechanic Roster and Skills from HR”
- Context/help text describing what will be imported and overwritten/merged
- Source selector (fixed to HR if only one source; otherwise dropdown)
- “Fetch latest from HR” / “Refresh” button
- Import summary cards (counts): mechanics found, new, updates, conflicts, errors
- Preview table/list of mechanics with columns:
  - Name, Employee/HR ID, Location/Depot, Role/Status, Skills (tags), Last updated (HR), Import status
- Filters/search: search by name/ID; filter by new/updated/conflict/error
- Row-level details drawer/modal (mechanic details + skill list + validation messages)
- Conflict resolution controls (per row): choose HR value vs keep Dispatch value (if applicable)
- Validation/error banner area (top of table)
- Primary action button: “Import” / “Confirm Import”
- Secondary actions: “Cancel”, “Download error report” (CSV), “Retry”
- Progress indicator (loading state + import progress)
- Success confirmation panel with results and timestamp

## Layout
- Top: Header + brief instructions + source/refresh controls
- Main: Summary cards row above a searchable/filterable preview table
- Right (optional): Details drawer opens from table row selection
- Bottom: Sticky action bar with Cancel (left) and Import/Confirm (right)

## Interaction Flow
1. User navigates to the Import page from Dispatch People/Mechanics management.
2. User clicks “Fetch latest from HR” (or data auto-loads on entry); loading indicator shows.
3. System displays summary counts and populates the preview table with import statuses (new/updated/unchanged/conflict/error).
4. User searches/filters to review changes; selects a mechanic to open details drawer/modal.
5. If conflicts exist, user chooses resolution per conflicting field (e.g., keep Dispatch vs use HR) and sees status update in the table.
6. If validation errors exist (missing HR ID, invalid skill mapping, duplicates), system highlights affected rows and shows an error banner; “Import” is disabled until blocking errors are resolved or excluded (if exclusion is supported).
7. User clicks “Import/Confirm Import”; confirmation step appears summarizing impact (counts of creates/updates) and any overwrites.
8. System runs import with progress indicator; on completion, shows success panel with results and links to view updated mechanic list.
9. Edge case: HR fetch fails (network/auth/service down) → show error banner with “Retry” and keep last successful preview if available.
10. Edge case: Partial import failure → show completed counts + failed rows; enable “Download error report” and “Retry failed”.

## Notes
- Import should be implementation-ready: include preview, validation, and clear status states (new/updated/conflict/error/unchanged).
- Ensure mechanics are matched deterministically (prefer HR ID/employee ID); prevent duplicates.
- Skills must map to Dispatch skill taxonomy; unmapped skills should surface as validation issues with actionable messaging.
- Provide safe defaults: do not apply changes until user confirms import; show what will change.
- Accessibility: table supports keyboard navigation; status indicators not color-only; modals/drawers focus-managed.
- Acceptance criteria:
  - User can fetch HR roster, preview changes, and import successfully.
  - Blocking errors prevent import and are clearly listed per row.
  - Conflicts are visible and resolvable before import (or clearly defined merge rule if auto-resolved).
  - User receives a clear success/failure outcome with counts and timestamp.
- TODO (design/dev):
  - Define exact conflict rules (which fields can be overridden vs merged).
  - Define whether users can exclude specific rows from import.
  - Define error report format and which fields are included.
