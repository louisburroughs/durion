# [FRONTEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability

## Purpose
Provide Moqui frontend screens to search and view posted Journal Entries and their Ledger Lines with a clear, immutable audit trail. Users must be able to inspect status/posting info, traceability identifiers, source event snapshot (redacted as needed), and reversal relationships without any ability to edit/delete posted records. The UI must support drilldowns between Journal Entry, ledger lines, and (optionally) mapping/rule version details when backend endpoints exist, while handling common error states safely.

## Components
- Global header with page title and breadcrumbs
- Journal Entry Search/List
  - Filter form: Journal Entry ID, status, posted timestamp range, traceability IDs (e.g., source event ID, mapping version ID, rule version ID, correlation/trace IDs as available)
  - Results table (paged) with key columns: ID, status, postedAt, traceability summary, reversal indicators
  - Row action: “View” (no edit/delete)
- Journal Entry Detail (read-only)
  - Read-only field grid: id, status, postedAt, traceability IDs, optional audit fields (created/updated/by)
  - Immutability banner (posted-like state)
  - Warning banner if expected traceability fields missing for posted
  - Reversal relationship panel with links (original/reversal JE)
  - Source snapshot panel: default redacted summary; expandable read-only JSON/object viewer
  - Ledger lines list (paged or scroll) with drill-in to line detail
  - Optional panels/links: mapping version detail, rule version detail (enabled only if supported)
- Ledger Line Detail (read-only)
  - Read-only field grid: line id, parent journalEntryId, account and/or ledger identifiers, debit/credit, currency, timestamp, optional dimensions
  - Link back to parent Journal Entry
- Error/empty states
  - Inline error banners for 401/403, 404, 409, 5xx/timeouts
  - Retry button; Reload button for conflict
  - Empty results state for searches
- Loading indicators (list and detail)

## Layout
- Top: Global header + breadcrumbs (e.g., Audit > Journal Entries > {JE ID})
- Main (JE Search/List): Filters panel above results table; pagination at bottom
- Main (JE Detail): Top banners (immutability/warnings/errors) → read-only JE field grid → reversal panel + mapping/rule panels → source snapshot panel → ledger lines table
- Main (Ledger Line Detail): Read-only line field grid with “Back to Journal Entry” link at top

## Interaction Flow
1. Load Journal Entry list (search)
   1. User opens Journal Entry Search/List screen.
   2. User enters filters (optional) and submits; UI shows loading state and then paged results.
   3. User selects a row “View” to open Journal Entry Detail by ID.
2. View Journal Entry detail (read-only)
   1. On page load, fetch JE detail by ID; render read-only fields only (no edit/delete actions anywhere).
   2. If status indicates posted and/or postedAt present, show banner: “Posted entries are immutable; corrections occur via reversal.”
   3. Display traceability identifiers; if expected posted traceability fields are missing, show a warning banner (still read-only).
   4. Display source snapshot summary by default (redacted summary if provided); allow expand to view full snapshot object/JSON if permitted by backend.
3. View ledger lines for a Journal Entry
   1. After JE detail loads, fetch ledger lines by JE ID; render list/table of lines.
   2. User clicks a ledger line row to open Ledger Line Detail by line ID.
   3. Ledger Line Detail shows read-only fields and a link back to the parent JE.
4. Reversal navigation (reversal chain)
   1. If originalJournalEntryId present: show “Reversal of {originalJournalEntryId}” with enabled link to that JE.
   2. If reversalJournalEntryId present: show “Reversed by {reversalJournalEntryId}” with enabled link to that JE.
   3. Following either link loads the referenced JE detail; user can continue navigating the chain.
5. Mapping/rule version navigation (optional)
   1. If mappingVersionId present, attempt to enable “View mapping version” only when backend detail endpoint is supported and returns 200.
   2. If 404 or capability absent, show the ID as text only; disable link with explanation (e.g., “Details unavailable”).
   3. Repeat same behavior for ruleVersionId.
6. Error handling expectations
   1. 401/403: show “Access denied” (do not reveal whether the ID exists); provide navigation back to list.
   2. 404: show “Not found” with link back to search.
   3. 409: treat as unexpected for read-only; show “Conflict: record changed; please reload” with Reload action.
   4. 5xx/timeouts: show retry affordance; preserve user-entered filters on list screen.

## Notes
- All Journal Entry and Ledger Line screens are strictly read-only; UI must not present mutation actions for POSTED records (and ideally none at all on these audit views).
- Backend is authoritative for status; posted-like state is determined by status and/or postedAt presence.
- Source snapshot handling: prefer showing a redacted summary by default; allow expanded view only as provided by backend (may be fully redacted).
- Traceability identifiers to display include (as available): source event ID, mapping version ID, rule version ID, and other correlation/trace IDs referenced by backend; missing expected IDs for posted should trigger a warning banner.
- Optional audit fields (createdAt/createdBy/updatedAt/updatedBy) should display when present, read-only.
- Mapping/rule version detail screens are conditional: if backend does not support, show ID only and disable navigation with a clear explanation.
- Ensure list/detail loading states, empty states, and error banners are consistent; preserve search filters on retry and pagination changes.
