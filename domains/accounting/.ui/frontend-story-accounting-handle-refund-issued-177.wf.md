# [FRONTEND] [STORY] Accounting: Handle Refund Issued
## Purpose
Provide Accounting users a Moqui screen flow to list and inspect refunds from the backend read model with server-side pagination and sorting. Enable fast filtering by identifiers, status, and date range, and expose backend-authoritative status plus processing/ingestion diagnostics (including duplicate/conflict signals). Support traceability via copyable identifiers and optional navigation to linked entities, while safely handling missing/unresolved references and permission-gated access to raw payload JSON.

## Components
- Global header + breadcrumb: Accounting → Refunds → Refund Detail
- Refunds List screen
  - Filter panel/form
    - refundId (UUIDv7) input with inline validation
    - eventId (UUIDv7) input with inline validation
    - paymentId (UUIDv7) input with inline validation
    - invoiceId (UUIDv7) input with inline validation
    - originalTxnRef (free-text) input
    - status (enum select; backend-provided values)
    - Date range: from / to (inclusive; basis uses occurredAt when available, else backend timestamp)
    - Apply/Search button, Clear/Reset button
  - Results table (server-side)
    - Columns: refundId, status, amount, currency, paymentId, invoiceId, eventId, originalTxnRef, occurredAt/alt timestamp
    - Optional diagnostics columns (if provided): processingState, ingestionState, errorCode
    - Row action: View details
  - Table controls: pagination (page number, page size), sorting (default occurredAt if available)
  - Empty state (no results) and error state (load failure with retry)
- Refund Detail screen
  - Status banner area (error/quarantine/processing/none)
  - Summary card (key fields: refundId, status, amount, currency, occurredAt/alt timestamp)
  - Traceability/Linkage section
    - Identifiers block with copy actions: refundId, eventId, paymentId, invoiceId, originalTxnRef
    - Linked navigation buttons/links (Payment, Invoice) shown only when ID present and user has permission
  - Unresolved reference banner (data integrity / unresolved linkage / unable to open linked record)
  - Diagnostics accordion/expandable section
    - errorCode, message (user-safe), processing/ingestion fields, timestamps (receivedAt/processedAt if provided)
    - Duplicate/conflict indicators (as provided by backend)
  - Payload accordion/expandable section
    - Always show payload summary (redacted/summary object/string)
    - Raw JSON viewer (permission-gated)
  - Standard error panels for 403/404/409/5xx/timeout with retry/refresh actions

## Layout
- Top: Header + breadcrumb
- Refunds List: Left filter panel | Main results table + pagination/sort controls
- Refund Detail: Top status banner; Main stacked sections: Summary → Traceability/Links → Unresolved banner (if any) → Diagnostics (collapsed) → Payload (collapsed)

## Interaction Flow
1. User opens Accounting → Refunds.
2. UI loads refunds list using server-side pagination and default sorting by occurredAt (or backend default if occurredAt unavailable).
3. User enters any combination of filters and clicks Apply; UI requests filtered list and updates table/pagination.
4. Client-side validation: if user enters an invalid UUID in refundId/eventId/paymentId/invoiceId, show inline error and do not call backend until corrected.
5. User changes sort column/direction or page/page size; UI re-queries server preserving current filters.
6. Empty results: show “No refunds found” with option to clear filters.
7. List load error (5xx/timeout): show retry action; preserve filters and current page.
8. User selects a row to open Refund Detail; UI navigates with breadcrumb Accounting → Refunds → Refund Detail and fetches detail by refundId.
9. Detail renders status banner based on backend status:
   1) ERROR: show error banner; display errorCode/message safely.
   2) QUARANTINED: show quarantine banner; show quarantine reason/details if provided.
   3) PROCESSING: show info banner “Processing” and relevant timestamps.
   4) COMPLETED/normal: no banner.
10. Detail shows identifiers with copy actions; copy should work per-field (refundId/eventId/paymentId/invoiceId/originalTxnRef).
11. Linked navigation: show Payment/Invoice links only if corresponding ID is present and user has permission; do not probe existence.
12. If paymentId is missing (data integrity) or backend indicates unresolved linkage, show “Unresolved reference” banner.
13. If user clicks a linked entity and receives 403/404, show generic “Unable to open linked record” without implying existence.
14. User expands Diagnostics to view processing/ingestion states, duplicate/conflict indicators, and error details.
15. User expands Payload: always show payload summary; show raw JSON only if user has the raw-payload permission.
16. Detail error handling:
   1) 403: show access denied without revealing whether refundId exists.
   2) 404 (authorized): show “Refund not found”.
   3) 409: show conflict banner “Record changed; refresh”.
   4) 5xx/timeout: show retry; keep user on same detail view.

## Notes
- Refund data is read-only and derived from refund events; UI must treat status and enums as backend-authoritative (do not invent new statuses/labels).
- Identifiers are UUIDv7 where specified (refundId, eventId, paymentId, invoiceId if present); enforce client-side UUID format validation for filter inputs.
- Date range filtering is inclusive and should use occurredAt when available; if not available, use the backend-provided alternative timestamp field consistently for filtering/sorting.
- Permission enforcement is required at screen + action level; raw payload JSON must be hidden by default and only shown when permitted.
- Safe handling of unresolved references is required: tolerate nulls (especially paymentId though expected mandatory) and display a data integrity/unresolved banner rather than breaking the screen.
- Error messaging must follow standard error shape and safe messaging rules: 403 must not reveal existence; 404 only shown when authorized; 409 prompts refresh; network errors offer retry while preserving state.
- Table should support server-side pagination/sorting; avoid client-side sorting that could conflict with backend ordering.
