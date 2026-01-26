# [FRONTEND] [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event
## Purpose
Enable AP Clerks to find and review Vendor Bills created from purchasing/receiving origin events, with full traceability to the source event and downstream accounting postings. Provide a list view with status/date filtering and a detail view that exposes header, line items, audit fields, and linked references (PO/Receipt/Journal Entry/Ingestion). Handle alternate outcomes where no bill exists or multiple bills exist for the same source event without UI deduplication.

## Components
- Breadcrumbs: Accounting → Accounts Payable → Vendor Bills → Vendor Bill Detail
- Vendor Bills List
  - Filter bar: Bill date range (from/to), Status (single or multi-select), optional search inputs (originEventId, purchaseOrderId, receiptId, vendor name/id)
  - Results table (paged, sortable): Bill #/ID, Vendor, Status, Bill Date, Due Date, Total (amount + currency)
  - Pagination controls (page size, next/prev)
  - Warning banner area (for multiple bills found for same source event)
  - Empty state panel with guidance
- Vendor Bill Detail (read-only unless future edit actions are added)
  - Header summary: Vendor, Status, Bill Date, Due Date, Total + Currency
  - Traceability section: Origin Event, Purchase Order reference, Receipt/Goods Received reference, Source event type
  - Posting/Accounting section: Journal Entry reference (conditional on permission)
  - Ingestion section: Ingestion record reference and processing/idempotency fields (if available)
  - Audit section: Created/Updated timestamps and actor fields
  - Line items table: description, quantity, unit price, line amount, tax/discount (as provided), optional accounting display fields (account/accountName, taxCode/taxRate)
  - Linked record buttons/links: View Purchase Order, View Receipt, View Journal Entry, View Ingestion Record (or display IDs only if screens don’t exist)
- Global UI elements
  - Loading state (skeleton/spinner)
  - Error state banner (API failures / permission denied)
  - Copy-to-clipboard for UUID fields (optional)

## Layout
- Top: Page title + breadcrumbs; right side shows key actions (none required) and status pill
- Main (List): Filter bar above results table; banners directly under filters; table centered; pagination at bottom-right
- Main (Detail): Two-column header area (left: vendor/status/dates; right: totals/currency); below: stacked sections (Traceability → Posting → Ingestion → Audit) then Line Items table

## Interaction Flow
1. Open Vendor Bills list screen.
2. Set filters: select bill date range and status value(s); submit/apply filters.
3. View paged results; each row displays bill identifier, vendor, status, bill date, due date (if present), and total with currency.
4. Sort results by stable sort key (e.g., billDate then id) and navigate pages via pagination controls.
5. Select a row to open Vendor Bill Detail.
6. On Vendor Bill Detail, review header fields (IDs, vendor display, status, dates, currency, totals) and audit fields.
7. Review traceability: originEventId and originEventType; purchaseOrderId and/or receiptId; source event identifier fields displayed read-only.
8. Navigate to linked records:
   1. If Purchase Order screen exists, open it; otherwise show PO id as non-clickable text.
   2. If Receipt/Goods Received screen exists, open it; otherwise show receipt id as non-clickable text.
   3. If Journal Entry screen exists and user has permission, show link; otherwise hide link or show “No access”.
   4. If Ingestion record screen exists, show link; otherwise show ingestion id as text.
9. Alternate path: search by a specific source event identifier (originEventId/receiptId/PO id).
   1. If exactly one bill found, open detail and verify traceability fields match the source.
   2. If none found, show empty state with guidance (adjust filters, verify event processed, check ingestion status if available).
   3. If multiple bills found for the same source event, show warning banner “Multiple bills found for the same source event; investigate idempotency outcome” and list all matching bills (no UI dedupe).

## Notes
- Status workflow is read-only and backend-authoritative: DRAFT → IN_REVIEW → APPROVED → SCHEDULED → PAID.
- VendorBill header fields include required UUIDs (billId, vendorId) and optional traceability UUIDs (purchaseOrderId, receiptId, originEventId/sourceEventId), plus vendor display name, currency, totals, billDate, dueDate, and audit timestamps/actors.
- Line items must display backend-provided values only (do not infer accounting fields); include optional display-only accounting fields when present.
- Permissions: enforce tokens for viewing list/detail and for viewing linked Journal Entry detail; hide/disable links when not permitted.
- Ingestion visibility: show processingStatus/idempotencyOutcome/errorCode/errorMessage if provided; raw payload access must be permission-gated (summary vs raw policy).
- Acceptance criteria: list supports date+status filtering, paging, stable sorting; detail shows full traceability chain and linked references; empty and multiple-results states behave as specified; UI does not dedupe multiple bills.
- TODO for implementation: confirm exact API endpoints/service names and schemas for list/detail/traceability/ingestion/posting references; confirm whether ingestion fields are embedded on VendorBill or linked via ingestionRecordId; confirm exact field names for source event identifiers.
