# [FRONTEND] [STORY] GL: Provide Trial Balance and Drilldown to Source
## Purpose
Provide a GL Trial Balance report screen that lets authorized users select an accounting period and optional filters, generate on-screen results, and export the same results to CSV. Enable a drilldown path from Trial Balance account lines to ledger lines, then to a journal entry, and finally to a read-only source event view. Ensure backend-authoritative permission gating and robust empty/error states while preserving report context across drilldowns.

## Components
- Page header: “Trial Balance”
- Criteria form
  - Accounting period selector (dropdown; populated from backend)
  - As-of datetime (required; default to “now” or backend-provided default)
  - Optional filters (single-select per dimension; account filter; other dimensions as supported)
  - Buttons: Generate Trial Balance, Reset/Clear
- Results toolbar
  - Summary: currency code, total debits, total credits
  - Button: Export CSV
- Trial Balance results table
  - Columns (as provided by backend): account identifiers/names, debit/credit/balance fields, totals, optional boolean flag (e.g., “isBalanced”/status)
  - Row action: click account line to drill down
- States/alerts
  - Inline validation errors (missing required criteria)
  - Empty state (no data for criteria)
  - Error banner (service failure)
  - Access denied (401/403) message panel
  - Not found (404) message panel for drilldown targets
- Drilldown screens
  - Account Ledger Lines screen (table + context header + Back to Trial Balance)
  - Journal Entry detail screen (read-only fields + lines if applicable + Back)
  - Source Event view screen (read-only summary; optional raw JSON payload section gated by permission)
- Navigation controls
  - Breadcrumbs or stacked “Back” actions: Back to Ledger Lines, Back to Trial Balance

## Layout
- Top: Page title + brief context line (selected period, as-of datetime)
- Main (upper): Criteria form (period + as-of + optional filters) with Generate/Reset on right
- Main (lower): Results toolbar (totals + currency + Export CSV) above results table
- Drilldown pages: Top context header + Back action; Main table/detail content; optional right-side panel for source payload (if permitted)

## Interaction Flow
1. Open Trial Balance screen.
2. System loads available accounting periods; if load fails, show error banner with retry.
3. User selects required period and required as-of datetime; optionally selects single values for supported dimensions and/or account filter.
4. User clicks “Generate Trial Balance”.
5. Frontend validates required fields; if invalid, show inline validation messages and do not call backend.
6. On success, display Trial Balance table plus totals (total debits/credits) and currency code returned by backend.
7. If backend returns no rows, show empty state (“No Trial Balance data for selected criteria”) while keeping criteria visible.
8. User clicks “Export CSV”.
9. Frontend calls CSV export using the same criteria as the displayed report; on success, download CSV; on 401/403 show access denied; on error show banner.
10. User clicks a Trial Balance account line.
11. Navigate to Ledger Lines screen, preserving report context (period, as-of, and optional filters) and the selected account; load ledger lines from backend.
12. Ledger Lines screen shows table; if empty, show empty state; if 404, show not found; if 401/403, show access denied.
13. User clicks a ledger line (journal entry reference).
14. Navigate to Journal Entry detail screen; fetch by journal entry ID; render read-only fields from backend (including status enum).
15. User clicks a source reference on the journal entry (or a dedicated “View Source Event” action).
16. Navigate to Source Event view; show summary payload; if user has raw payload permission, show raw JSON section; otherwise hide/replace with “Not authorized”.
17. User clicks “Back to Trial Balance” from any drilldown level.
18. Trial Balance screen restores criteria and re-runs the report server-side (no client-side caching required), returning the user to the results state.

## Notes
- Permission gating must be backend-authoritative: use Moqui artifact authorization plus explicit handling of 401/403 responses (no UI-only security).
- Define and map permissions for: (1) view Trial Balance + drilldowns, (2) export Trial Balance CSV, (3) view raw source payload JSON (reuse existing payload permission per AD-009); ensure compliance with AD-013 explicit permission mapping.
- Filters: single-select per dimension only (no multi-select) per decision rationale; avoid inventing dimension taxonomy/policy.
- Required report context to preserve across drilldowns: period and as-of datetime; optional filters (account filter + dimensions).
- Display fields are backend-driven; tables should render required columns for Trial Balance, Ledger Lines, and Journal Entry as provided, with read-only presentation.
- Error/edge states to implement across screens: validation errors, empty results, access denied, not found, and generic service failure with retry.
- AC1 coverage: authenticated Controller selects period “2024-08”, generates report, sees account lines, totals debits/credits, and currency code from backend.
