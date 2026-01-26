# [FRONTEND] [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status
## Purpose
Expose backend-authoritative accounting ingestion/posting status directly on the POS Invoice Detail screen to reduce customer disputes and internal confusion. Authorized users can see the latest accounting ingestion outcome (status, type, timestamps, references) and drill into a history of ingestion records. The UI must be permission-gated, resilient to missing/extra backend fields, and avoid implying accounting policy beyond what the backend provides.

## Components
- Invoice Detail header (existing context)
- Accounting section (permission-gated)
  - Latest status summary row (status, type, last updated/created timestamp)
  - Posting references display (primary/secondary, if present)
  - “View history” button/link
- History view (modal or drawer)
  - Ingestion records list (paginated)
  - Filters/controls: page navigation, page size, optional sort selector (default backend-provided)
  - Record row fields: created timestamp, status, type, short message (user-safe), posting refs
  - Empty state (no records)
- Ingestion record detail view (within modal/drawer or separate panel)
  - Full field list (tolerate additional fields)
  - Curated/redacted details (object/string) if provided
  - Raw JSON payload section (render only if permitted)
- Error/alert components
  - Inline validation error (invalid invoice ID format)
  - Access denied message (do not leak existence)
  - Not found message
  - Conflict reload prompt
  - Temporarily unavailable/timeout retry prompt
- Loading states (skeleton/spinner) for latest status and history list

## Layout
- Top: Invoice Detail header (invoice number, customer, totals; existing)
- Main: Invoice detail content sections … then Accounting section
  - Accounting section header (left) + “View history” action (right)
  - Latest status summary beneath header; posting references below if present
- Overlay: History modal/drawer
  - Top: “Accounting ingestion history” title + close
  - Middle: list with pagination controls; selecting a row opens detail pane/section
  - Bottom/right: record detail (if split view) or navigates within modal

## Interaction Flow
1. User opens Invoice Detail screen.
2. System checks permission for accounting ingestion visibility.
3. If permitted, UI requests ingestion records list filtered by invoiceId (page=0, size=default).
4. While loading, show Accounting section skeleton/loading indicator.
5. On success with ≥1 record:
   1. Determine “latest” record (first item per backend sort/default) and display its status, type, and relevant timestamp(s).
   2. Display posting references (primary/secondary) if present.
   3. Enable “View history”.
6. On success with 0 records:
   1. Show Accounting section with “No accounting ingestion records found” and keep “View history” available (opens empty history view) or disabled per design choice.
7. User clicks “View history”:
   1. Open modal/drawer with paginated list of ingestion records.
   2. User navigates pages/changes page size; UI refetches with updated params.
   3. User selects a record to view details; show user-safe message and curated/redacted details.
   4. If user has additional permission for raw JSON, render raw payload section; otherwise hide it entirely.
8. Error handling (any fetch):
   1. 400/422: show inline “Invalid ID format” (primarily if invoiceId is malformed).
   2. 401/403: show “Access denied” in place of Accounting section/history; do not reveal whether records exist.
   3. 404: show “Not found” (invoice or records endpoint response).
   4. 409: show “Conflict; reload” with a reload action.
   5. 5xx/timeout: show “Temporarily unavailable; try again” with retry action.

## Notes
- Frontend stack: Vue.js 3 + TypeScript; Quasar components; integrate with Moqui backend endpoints.
- Data model: AccountingEventIngestionRecord fields must be treated as backend-authoritative; UI must tolerate additional fields and missing optional fields.
- Latest status display must include at minimum: ingestion status enum, ingestion type enum, and a timestamp (created/updated as available).
- Permission gating:
  - If unauthorized (401/403), show only “Access denied” and avoid leaking invoice/record existence.
  - Raw JSON payload must only render when explicitly permitted; otherwise omit entirely (not even placeholder).
- History list endpoint inputs: invoiceId (required UUIDv7), page (default 0), size (default per UI), sort (optional default).
- Accessibility/responsiveness: ensure modal/drawer is keyboard navigable, readable on small screens, and uses clear status text (not color-only).
- Error mapping must follow canonical error shape when provided; handle generic failures gracefully with retry.
- Avoid implying accounting policy; display only backend-provided statuses/messages and references.
