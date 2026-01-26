# [FRONTEND] [STORY] Accounting: Ingest PaymentReceived Event

## Purpose
Enable Accounting Ops users to find and review ingested PaymentReceived events as persisted “Payment” records in the Accounting payment read model, with traceability back to ingestion records and related entities. Provide server-side filtering, stable sorting/pagination, and a payment detail view that exposes authoritative backend fields (including audit/traceability). Support a one-time “Assign customer” action with justification when eligible, and handle “payment not found/access denied” by safely redirecting users to ingestion monitoring with prefilled filters.

## Components
- Left navigation: Accounting → Receivables → Payments
- Payments List page
  - Page header + breadcrumbs
  - Server-side filter bar
    - Payment ID (UUIDv7 exact)
    - Ingestion ID (UUIDv7 exact, if supported on payment records)
    - External Transaction ID (exact)
    - Source / Processor (exact or enum-like string)
    - Status (backend enum; render as returned)
    - Assignment state (assigned/unassigned; derived; via `assigned=` param if supported)
    - Received timestamp range (from/to; inclusive; backend-defined basis)
  - Results table (sortable columns: received timestamp, amount, status; stable)
  - Pagination controls
  - Row click to open Payment Detail
  - Optional quick link: “Unapplied & Unassigned Queue” (pre-filtered view)
- Payment Detail page
  - Summary header (Payment ID, status, amount + currency, received timestamp)
  - Read-only field sections (see Notes for exact fields)
  - Related links panel (read-only)
    - Customer detail (conditional on customerId + permission)
    - Ingestion record detail (if ingestionId exists) or link to ingestion list filtered by paymentId/externalTransactionId
    - Journal entry detail (if journalEntryId exists)
  - “Assign customer” section (conditional)
    - Customer ID input (UUIDv7)
    - Justification textarea (min 10 chars)
    - Submit button + cancel/reset
    - Inline validation + error banner
- Ingestion Monitoring cross-link entry point
  - Left navigation: Accounting → Integrations / Events → Ingestion Monitoring
  - Prefilled filters when linked from payment not found / from payment detail
- Empty/error states
  - List: no results state
  - Detail: “Not found or access denied.” state with link to ingestion monitoring (permission-gated)

## Layout
- Top: Page title + breadcrumbs; global actions (if any)
- Left: Accounting navigation
- Main (List): Filter bar → Results table → Pagination
- Main (Detail): Summary header → Details sections (stacked) → Assign customer (if visible) → Related links (right rail or bottom)
- Right (Detail): Related links panel (or secondary column)

## Interaction Flow
1. User navigates to Accounting → Receivables → Payments.
2. User applies one or more server-side filters (e.g., Payment ID, externalTransactionId, status, received date range) and submits; list refreshes with paginated results.
3. User sorts by received timestamp, amount, or status; sorting remains stable across pagination.
4. User clicks a row to open Payment Detail.
5. Payment Detail loads and displays backend-authoritative fields, including audit fields and traceability identifiers (if present).
6. User uses related links:
   1. If customerId exists and user has permission, open Customer detail in the customer domain.
   2. If ingestionId exists, open Ingestion record detail; otherwise open Ingestion Monitoring list filtered by paymentId and/or externalTransactionId.
   3. If journalEntryId exists, open Journal Entry detail (read-only).
7. Assign customer (mutation) flow:
   1. “Assign customer” section is shown/enabled only if user has required permission, payment.customerId is null, and payment status is apply-eligible/available (backend authoritative; UI may also check an eligibility flag if returned).
   2. User enters customerId and justification (>= 10 chars); UI validates inline.
   3. On submit, call assign service; show loading state; on success refresh Payment Detail to show assigned customerId and updated audit fields; disable/hide assignment thereafter (one-time).
   4. On failure, show error banner with safe message; keep inputs for retry.
8. Unapplied & Unassigned Queue:
   1. User opens the pre-filtered queue view (assigned=unassigned + unapplied/unassigned criteria as defined).
   2. User drills into a payment and assigns customer if eligible.
9. Edge case: Payment detail 404/403:
   1. Show “Not found or access denied.”
   2. If user has ingestion monitoring permission, show link to Ingestion Monitoring with prefilled filters (ingestionId and/or externalTransactionId if the user had entered them).

## Notes
- UI must not invent field values; display only what backend returns (backend authoritative).
- Payment fields to display (read-only unless noted): paymentId (UUIDv7), status (enum; must support at least “RECEIVED”; other values backend-owned), amount (decimal; display with currency; no float math), currency (ISO-like code), unappliedAmount (decimal; ops visibility), receivedAt timestamp (or equivalent), externalTransactionId, source/processor, paymentMethod/type (string/enum, optional), customerId (UUIDv7 nullable; editable one-time from null → set), displayPayload (object/string, optional; preferred default display), rawPayload (JSON, optional; restricted), traceability IDs (e.g., eventId/ingestionId/journalEntryId if provided), standard audit fields (createdAt/updatedAt/createdBy/updatedBy or equivalent).
- Ingestion record fields (when linked/shown): ingestionId (UUIDv7), paymentId (UUIDv7), eventType (string), receivedAt (timestamp; default filter basis), status enums (ingestion outcome/state), errorCode/errorMessage (optional), displayPayload (optional).
- Assignment constraints (Decision AD-004): one-time assignment only; requires justification text >= 10 chars; visible/enabled only when eligible and permitted.
- Traceability constraints (Decisions AD-006/AD-007/AD-009/AD-011): UUIDv7 identifiers; ingestion monitoring integration; payload display restrictions; optional canonical posting/journal reference if present.
- Filtering is server-side; date range is inclusive; basis for payments is the backend-provided “payment received timestamp” field.
- Access control: customer link only if user has customer-view permission; ingestion monitoring link only if user has ingestion monitoring permission; detail 404/403 must use safe copy (“Not found or access denied.”).
