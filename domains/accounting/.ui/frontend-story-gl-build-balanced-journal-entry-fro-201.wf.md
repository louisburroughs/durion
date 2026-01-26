# [FRONTEND] [STORY] GL: Build Balanced Journal Entry from Event
## Purpose
Provide Moqui screens to search and view persisted draft General Ledger Journal Entries generated from backend events. Users need to quickly confirm traceability (event/source refs, mapping rule version) and verify that each Journal Entry is balanced per currency (debits = credits) without editing or posting. The UI must handle empty results, authorization failures, and data integrity issues (missing/invalid lines or traceability fields).

## Components
- Global navigation: Accounting → General Ledger → Journal Entries
- Journal Entries List screen
  - Filter bar: Status (default Draft), Date range, Event ID, Source system/module, Currency
  - Results table (sortable columns as available)
  - Row action: Open detail
  - Empty state panel (no results)
  - Error banner (load failure)
- Journal Entry Detail screen
  - Header summary panel (read-only fields)
  - Traceability panel (event/source refs, mapping rule version)
  - Copy-to-clipboard actions: journalEntryId, eventId/sourceEventId, mappingRuleVersionId
  - Balance summary per currency (computed UI check: Balanced/Not balanced)
  - Lines table (read-only): account, category, dimensions, debit, credit, currency
  - Data integrity warning banner (missing/invalid lines or missing required traceability)
  - Access denied state (no permission)
  - Loading state / retry action

## Layout
- Top: Page title + breadcrumbs (Accounting / GL / Journal Entries) + primary status indicator (List: current filters; Detail: JE status)
- Main (List): Filter bar above results table; empty/error states replace table area when applicable
- Main (Detail): Header/traceability panels at top; balance summary strip beneath; lines table fills remaining space; warning banner pinned above content when triggered

## Interaction Flow
1. Navigate via menu: Accounting → General Ledger → Journal Entries.
2. System loads list view with Status filter pre-set to “Draft”; fetches JE list from backend.
3. User adjusts filters (status, date range, eventId, source system/module, currency) and applies; list refreshes.
4. If no results (e.g., search by eventId returns none), show empty state explaining no Journal Entry found for that event and keep filters visible for adjustment.
5. User selects a row (or “Open”) to view Journal Entry detail; system loads header + lines.
6. Detail view displays read-only header fields including: journalEntryId, status, transactionDate, source event references (eventId/sourceEventId and source module/system), mappingRuleVersionId (flag if missing), optional additional refs if present.
7. UI computes balance per currency from lines (sum debits vs sum credits grouped by currency) and displays Balanced/Not balanced per currency.
8. User reviews lines table showing account, category, dimensions, debit, credit, and currency for each line.
9. User clicks copy icons/buttons to copy journalEntryId, eventId/sourceEventId, and mappingRuleVersionId to clipboard (UI convenience only).
10. Edge case: user opens detail without permission → show access denied screen/message with navigation option back to list.
11. Edge case: detail loads but lines missing/invalid → show data integrity warning banner; still render header and any available lines; provide copy of identifiers for support.
12. Edge case: API load failure (list or detail) → show error banner/state with retry action.

## Notes
- In-scope: list/search draft JEs, open detail, view header traceability fields, view lines, computed balance status per currency, error/empty/unauthorized states, and basic navigation entry points.
- Out-of-scope: creating JEs from events in UI, editing headers/lines, posting to ledger, and managing mapping rules/suspense/COA data.
- Required header fields (read-only): journalEntryId, status (at least Draft), transactionDate, event/source references (eventId/sourceEventId and source module/system), mappingRuleVersionId (required for traceability; if missing, UI flags). Optional: additional refs (e.g., correlation/external ids) and header currency if stored.
- Lines must display: account references, category references, dimension references, debit/credit amounts, and currency.
- Balance check is UI-computed from loaded lines; display per currency (supports multi-currency at line level).
- Acceptance criteria alignment (frontend-visible): JE shows event/rule version traceability; lines include category/account/dimensions; balance per currency is clearly indicated; no partial/interactive correction flows in this story.
- TODO (implementation): map exact backend field names for event/source refs and mappingRuleVersionId; align with standard error envelope and RBAC permission token(s) for view access.
