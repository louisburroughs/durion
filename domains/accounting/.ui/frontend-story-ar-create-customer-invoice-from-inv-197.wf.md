# [FRONTEND] [STORY] AR: Create Customer Invoice from Invoice-Issued Event
## Purpose
Provide an AR Invoice List and Detail experience that lets users find customer invoices created from an Invoice-Issued event, primarily by sourceInvoiceId. Surface backend-provided ingestion indicators and posting references to help users understand whether the invoice was created successfully and where it posted. When no AR invoice exists (rejected path), guide users to ingestion monitoring to investigate.

## Components
- Page header: “AR Invoices”
- Permission-gated access handling for “View AR invoices”
- Search/filter bar
  - sourceInvoiceId (string)
  - arInvoiceId (UUIDv7; client-side validation)
  - customerId (string; optional)
  - invoice date range: from/to (inclusive)
  - status (dropdown; values from backend; treated as opaque)
  - Actions: Search, Clear/Reset
- Results table (paginated)
  - Columns: arInvoiceNumber, customerName, invoiceDate, totalAmount, status
  - Row selection to navigate to detail
  - Empty state messaging (including rejected/no-result guidance)
- AR Invoice Detail view
  - Read-only header fields (key/value sections)
  - Copy-to-clipboard actions for: arInvoiceId, arInvoiceNumber, sourceInvoiceId, ingestionRecordId (when present)
  - Ingestion indicators panel (with permission-gated navigation)
  - Posting references section (primary/secondary)
  - Journal entry link/summary (permission-gated navigation)
  - Warning banner area (backend-provided message/code; no inference)
- Permission-gated navigation links
  - “View ingestion detail” / “View ingestion monitoring”
  - “View journal entry”
- Standard UI states: loading, error, empty, validation error

## Layout
- Top: Page title + brief helper text (“Search by Source Invoice ID or AR Invoice ID”)
- Below top: Filter bar (single row or two-row wrap) + Search/Clear actions
- Main: Results table with pagination footer
- Detail page: Top summary (left: invoice identifiers; right: amounts/status) → below: sections for Dates/Customer, Ingestion Indicators, Posting Refs, Journal Entry link, Audit (optional)

## Interaction Flow
1. User opens AR Invoice List.
2. UI checks permission “View AR invoices”; if missing, show access denied/insufficient permissions state.
3. User enters sourceInvoiceId and clicks Search.
4. UI validates arInvoiceId if provided (UUIDv7 format); show inline validation error and block search if invalid.
5. UI requests paginated results; show loading state.
6. UI displays results table; each row shows arInvoiceNumber, customerName, invoiceDate, totalAmount, status.
7. User selects a row; navigate to AR Invoice Detail for that arInvoiceId.
8. Detail view loads and renders read-only fields (IDs, numbers, customer, dates, currency, amounts, status, sourceInvoiceId, optional audit).
9. If backend provides a warning banner/message, show it prominently (no client inference).
10. If ingestionRecordId is present:
    1. Show ingestion indicators (eventId, eventType, receivedAt, processedAt if present, status fields, error codes/messages if present, posting refs).
    2. If user has “View ingestion monitoring”, enable links to ingestion detail and to ingestion list filtered by eventId.
11. If journalEntryId is present:
    1. If user has “View journal entries”, enable navigation to journal entry detail.
    2. Otherwise, show the JE reference as read-only without link (or hide link affordance).
12. Copy-to-clipboard actions copy the selected identifier and show a lightweight confirmation state.
13. Edge case (rejected/no AR invoice created): Search returns zero results; show empty state that suggests checking ingestion monitoring with a link to ingestion list filtered by sourceInvoiceId/eventId (permission-gated).

## Notes
- Permissions (do not ship without mapping):
  - View AR invoices: required for list/detail access (new token).
  - View ingestion monitoring: required to see ingestion indicators and navigate to ingestion detail/list (existing).
  - View journal entries: required to navigate to JE detail (new token).
  - View raw payload JSON: optional; raw payload is never shown in this story and only belongs on ingestion detail.
- Status values are backend-provided enums; UI must treat them as opaque (no hardcoded mapping beyond display).
- Ingestion warning banner must display backend-provided code/message exactly; no inference or derived explanations.
- Posting references: show primary postingRefId and secondary postingRef (string/UUID) when provided.
- Date range filters are inclusive; ensure clear labeling and reset behavior.
- Minimum required fields are read-only; no create/edit actions in this UI.
- Include robust empty/loading/error states; ensure pagination is available on list results.
- Client-side validation: arInvoiceId must be UUIDv7; sourceInvoiceId is a free-form string.
