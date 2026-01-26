# [FRONTEND] [STORY] Accounting: Ingest InvoiceIssued Event
## Purpose
Provide Accounting Ops a Moqui/Quasar UI to list and inspect InvoiceIssued ingestion records with clear processing status, idempotency outcome, errors, and posting references. Enable fast troubleshooting and audit/reconciliation traceability while ensuring invoices post exactly once. Optionally allow permitted users to trigger an async retry for eligible failed/quarantined records with justification.

## Components
- Navigation entry: Accounting → Integrations / Event Ingestion → InvoiceIssued
- List screen
  - Page header (title + brief description)
  - Filter bar
    - Status (enum select)
    - Idempotency outcome (enum select)
    - Date range (from/to; inclusive; based on receivedAt)
    - Identifier inputs: ingestionRecordId, eventId, invoiceId (UUIDv7), invoiceNumber, customerId (as provided)
    - Apply/Update (implicit on change or explicit button)
    - Clear filters button
  - Results table (server-side pagination)
    - Columns: receivedAt, status (badge), idempotency outcome (badge), ingestionRecordId, eventId, invoiceId (if present), invoiceNumber (if present), posting reference summary (if present)
    - Row action: View details
  - Pagination controls (page, page size, total count)
  - Inline empty state / no results message
  - Error banner/toast area (network/validation)
- Detail screen (read-only)
  - Header with status badge + idempotency badge + key identifiers
  - Copy-to-clipboard controls for identifiers
  - Sections/cards:
    - Identifiers & envelope
    - Processing outcome (timestamps: receivedAt, processedAt, postedAt; status; idempotency outcome)
    - Posting references (journalEntryId primary; ledgerTransactionId secondary)
    - Error details (conditional)
    - Payload summary (curated)
    - Raw payload JSON (permission-gated; collapsed by default)
  - Actions
    - Retry ingestion button (permission + eligibility gated)
    - Refresh button (after retry submission / to re-fetch)
  - Retry modal/dialog (optional)
    - Justification textarea (min 10 chars)
    - Submit (disabled until valid), Cancel
    - Submitted state (job accepted / 202)

## Layout
- Top: Breadcrumbs + Page title; right-aligned actions (List: none; Detail: Refresh, Retry if eligible)
- Main (List): Filter bar above results table; pagination at bottom
- Main (Detail): Vertical stack of cards/sections; identifiers at top with copy icons; error and banners near top when applicable
- Inline sketch: Top header/actions → Filters/Table (List) OR Cards stack (Detail) → Footer pagination (List)

## Interaction Flow
1. Navigate to Accounting → Integrations / Event Ingestion → InvoiceIssued.
2. List loads by calling the ingestion records endpoint with fixed filter eventType=InvoiceIssued and default sort (receivedAt desc) plus pagination.
3. User applies filters (status, idempotency outcome, inclusive date range, identifiers); UI validates UUID/date formats and refreshes results server-side without clearing current state on errors.
4. User selects a row to open Detail; UI calls record detail endpoint by ingestionRecordId and renders:
   1) Status badge (from status enum) and idempotency outcome badge.
   2) Identifiers & envelope with copy-to-clipboard.
   3) Posting references: show journalEntryId as primary; deep-link only if a Journal Entry screen exists, otherwise show copyable text; show ledgerTransactionId as secondary copyable text.
5. Detail conditional rendering by state:
   1) SUCCESS: show posting references if present; hide Retry action.
   2) FAILED: show Error details section; show Retry if permitted.
   3) QUARANTINED/CONFLICT: show quarantine/conflict banner + Error details; show Retry if permitted and backend allows.
   4) DUPLICATE_IGNORED: show “duplicate ignored” banner; do not imply postings were created.
   5) DUPLICATE_CONFLICT: show “duplicate conflict” banner emphasizing investigation.
6. Payload handling:
   1) Always show curated payload summary when provided.
   2) Raw payload JSON remains hidden unless user has explicit permission; if permitted, allow expand/collapse.
7. Retry (optional, if backend supports):
   1) Retry button appears only if user has retry permission and record status is FAILED or QUARANTINED (backend authoritative).
   2) Clicking Retry opens modal requiring justification (min 10 chars).
   3) Submit calls retry endpoint; expect 202 Accepted; show “job submitted” state and provide Refresh action (optional polling only if a status endpoint exists).
8. Error handling edge cases:
   1) 401/403: show access denied without revealing whether a record exists.
   2) 404: show “Not found” safe message.
   3) 409: show conflict message (“Record state changed; refresh and try again”).
   4) 400/422: show validation errors; map field errors inline (filters, justification).
   5) 5xx/timeout: show retry affordance; preserve filters/page and do not clear results.

## Notes
- Fixed filter: eventType must be constrained to InvoiceIssued on the list screen.
- Standardized error rendering must follow canonical error schema; render error messages safely (no unsafe HTML).
- Raw payload JSON is permission-gated and must not be shown by default; curated payload summary should be the default view.
- Retry is async only (202); require justification (min 10 chars) before enabling submit; show submitted state and allow manual refresh.
- Posting references: journalEntryId is primary; ledgerTransactionId secondary; deep-link only if corresponding screens exist in this frontend, otherwise copy-only.
- Ensure server-side pagination and sorting; preserve filter state across refreshes and transient failures.
- Eligibility rules for retry are UI-gated but backend remains authoritative; handle 409 conflicts gracefully.
