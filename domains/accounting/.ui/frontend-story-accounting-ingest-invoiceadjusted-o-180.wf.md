# [FRONTEND] [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event
## Purpose
Provide Accounting Ops a dedicated set of Moqui screens to list and inspect ingestion records for InvoiceAdjusted and CreditMemo events, including envelope metadata, timestamps, processing outcomes, errors/quarantine indicators, and posting references. Enable fast operational triage with server-side filtering/pagination/sorting and deep-linkable, copyable identifiers for support. Ensure payload visibility and access are permission-gated per Accounting domain rules, preventing unauthorized users from inferring record existence.

## Components
- Global page header with breadcrumb: Accounting → Event Ingestion → Adjustments & Credit Memos → (List/Detail)
- List screen
  - Filter panel/form
    - Event Type multi-select (fixed options: InvoiceAdjusted, CreditMemo; default selected both)
    - Status multi-select (backend-owned enum; fallback to free-text filter)
    - Quarantine Status multi-select (backend-owned enum; fallback to free-text filter)
    - Date range filter (basis: receivedAt; from/to inclusive)
    - eventId exact match (UUIDv7)
    - ingestionId exact match (UUIDv7)
    - Optional invoiceId exact match (UUIDv7) if backend provides linkage key
    - Optional sourceModule filter (exact/contains per backend support)
    - Actions: Apply, Clear filters
  - Results table (server-side pagination/sorting)
    - Columns: receivedAt, eventType, status, quarantineStatus, sourceModule, eventId, ingestionId, Has Journal Entry indicator, Error indicator
    - Row click navigates to detail by ingestionId
  - Empty state with “No results” + Clear filters action
  - Error banner area (for 4xx/5xx)
- Detail screen
  - Summary header with key identifiers + copy buttons (ingestionId, eventId; optionally schemaVersion/sourceModule)
  - Envelope/meta section: eventType, schemaVersion, sourceModule
  - Timestamps section: receivedAt (required), occurredAt (optional), processedAt (optional)
  - Outcome section: status, quarantineStatus
  - Error section (conditional): errorCode, errorMessage, quarantine indicators
  - Posting references section (primary navigation by journalEntryId)
    - Journal Entry link/button (shown only if journalEntryId present)
    - Ledger Transaction link/button (shown only if ledgerTransactionId present)
  - Related invoice section (conditional)
    - Link to invoice view only if safe link exists and user permitted; otherwise show invoiceId as copyable text only
  - Payload section
    - Curated payload (safe/redacted) shown by default if provided
    - Raw payload JSON section shown only with explicit permission and only if backend returns it
  - Standard error states: Access denied, Not found, Retry for server errors

## Layout
- Top: Breadcrumb + page title (List: “Adjustments & Credit Memos”; Detail: “Ingestion Detail”)
- Main (List): Left filter panel; Right results table with pagination controls
- Main (Detail): Stacked sections: Summary → Envelope/Meta → Timestamps → Outcome → Errors (if any) → Posting References → Related Invoice → Payload (curated; raw gated)

## Interaction Flow
1. User opens Accounting → Event Ingestion → Adjustments & Credit Memos.
2. List loads with default Event Type filter set to InvoiceAdjusted + CreditMemo and date basis on receivedAt; table populated via server-side query.
3. User adjusts filters (status/quarantine/date range/eventId/ingestionId/optional invoiceId/sourceModule) and clicks Apply; list refreshes with server-side pagination/sorting preserved.
4. User clicks a row; navigate to detail route using ingestionId (deep linkable URL).
5. Detail screen fetches latest record; if values differ from list snapshot, display latest backend values and optionally note “Updated since list view.”
6. If journalEntryId is present, show primary “View Journal Entry” navigation; if absent, do not show journal entry link or any implied posting impact statements.
7. If ledgerTransactionId is present, show secondary “View Ledger Transaction” navigation.
8. If invoice linkage exists:
   1. If a permitted, safe invoice link exists, show “View Invoice” link.
   2. Otherwise display invoiceId as copyable text only (no cross-domain fetch).
9. If record is rejected/quarantined and errorCode/errorMessage exist, display them prominently; keep posting references section limited to backend-provided IDs/links only.
10. Payload visibility:
    1. Without raw-payload permission: show curated payload (if provided); hide raw payload section entirely.
    2. With raw-payload permission: show raw payload JSON section if backend returns it.
11. Error handling:
    1. 403: show “Access denied” with no record data and no existence leakage.
    2. 404: show “Not found” with safe messaging and link back to list.
    3. 409: show generic “Conflict; refresh and retry.”
    4. 5xx/network: show retry affordance; on list preserve current filters.

## Notes
- Permission gating must follow Accounting domain model rules; unauthorized users must not infer whether a given ingestionId exists (403 behavior).
- Primary posting navigation is by journalEntryId (Decision AD-011); ledger transaction is secondary.
- Curated payload is shown by default; raw payload requires explicit permission and should only render if returned by backend (Decision AD-009).
- Read model is backend-authoritative AccountingEventIngestionRecord (Decision AD-007); UI should treat enums (status/quarantineStatus) as backend-owned and support free-text filtering if enum lists are not available.
- List must support standard operational needs: server-side pagination (page/size), sorting, and robust empty state with “Clear filters” (SD-UX-EMPTY-STATE).
- Deep links and copyable identifiers are required for support workflows (eventId, ingestionId; plus any envelope identifiers available: eventType, schemaVersion, sourceModule).
- Date range filtering is by receivedAt (default basis) with inclusive from/to.
- Detail view must not add any inferred “posting impact” messaging beyond backend-provided fields, especially for rejected/quarantined records.
