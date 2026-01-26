# [FRONTEND] [STORY] Accounting: Ingest InventoryAdjustment Event
## Purpose
Provide Accounting Ops users a Moqui screen flow to list and inspect InventoryAdjustment ingestion records from the Accounting ingestion read model. Enable quick triage of failures/quarantine states by showing envelope identifiers, timestamps, status/idempotency outcomes, curated payload summary, and error details. Allow navigation to linked accounting artifacts (journal entry primary; ledger transaction secondary if present) and optionally trigger an ingestion retry as an async job when supported and permitted.

## Components
- Page header: “Inventory Adjustment Ingestion”
- Breadcrumbs: Accounting → Ingestion → Inventory Adjustments
- Filter panel (form)
  - Received At range: From (datetime), To (datetime) (inclusive)
  - Status (multi-select): Success/Failed/Quarantined (backend taxonomy)
  - Idempotency outcome (multi-select): e.g., Applied/Duplicate/Conflict (backend taxonomy)
  - Ingestion Record ID (UUIDv7 exact)
  - Envelope ID (UUIDv7 exact)
  - Posting Reference ID (canonical, UUIDv7/string; exact/contains per backend)
  - Search (free-text; backend-defined)
  - Buttons: Apply, Reset/Clear
  - Inline validation messages (UUID/date range)
- Results table (server-paginated)
  - Columns: Received At, Status, Idempotency Outcome, Ingestion Record ID, Envelope ID, Event Type (fixed to InventoryAdjustment), Producer (if provided), Posting Ref (if provided; linkable), Journal Entry ID (if provided; linkable)
  - Row action: “View details”
- Pagination controls: page size (default 25), next/prev, total count (if provided)
- Detail view (separate screen)
  - Summary header: status badge + key identifiers
  - Panels/sections:
    - Envelope & identifiers (ingestionId, envelopeId, eventType, producer, producerEventId if present)
    - Timestamps (receivedAt, processedAt, postedAt if present)
    - Status & idempotency (backend-authoritative)
    - Posting references (canonical postingRefId; journal entry link; ledger transaction link if present)
    - Error info (error code/message/details if present)
    - Curated payload summary (always shown if provided)
    - Raw payload JSON viewer (only when permitted)
  - Optional actions:
    - Retry ingestion button (conditional)
    - Retry justification input (>= 10 chars)
    - Retry job tracking panel (jobId, status, message; refresh/poll)
- Banners/alerts
  - Quarantine banner (conflict/duplicate messaging when provided)
  - Permission/authorization notice (when raw payload or retry not allowed)

## Layout
- Top: Header + breadcrumbs
- Below header: Filter panel (full width) → Results table (full width) → Pagination (bottom)
- Detail screen: Top summary bar; main column stacked panels; right-side (optional) “Actions” card for Retry + Job Status

## Interaction Flow
1. User opens “Inventory Adjustment Ingestion” list screen.
2. UI loads first page via list endpoint with required query `eventType=InventoryAdjustment`, default sort (stable, backend-defined), default pagination (page=0, size=25).
3. User sets optional filters (receivedAt From/To, status, idempotency outcome, IDs, posting ref, search) and clicks Apply.
4. Validation:
   1. If UUID fields are invalid → show inline error; do not call backend.
   2. If date range invalid (From > To) → show inline error; do not call backend.
5. UI refreshes table with server-side pagination only; user can change page/size; sorting remains stable via backend sort key.
6. User clicks “View details” on a row → navigates to detail screen for that ingestionId.
7. Detail screen loads and displays:
   1. Envelope identifiers, timestamps, status/idempotency outcome.
   2. Curated payload summary by default (redacted/curated as provided).
   3. Error panel if status indicates failure; include backend-provided error info.
8. Permission gating:
   1. If user has “View ingestion” permission → can access list/detail.
   2. If user lacks “View raw payload” permission → raw JSON viewer hidden; show only curated summary.
9. Linked artifacts:
   1. If journal entry ID present → show as primary link to Journal Entry view.
   2. If ledger transaction present → show secondary link to Ledger view.
   3. If posting refs absent (e.g., failed) → show “No posting references available” and keep links hidden/disabled.
10. Quarantine/duplicate/conflict path:
   1. If status is Quarantined and idempotency outcome indicates conflict/duplicate → show quarantine banner and any backend-provided duplicate linkage.
11. Retry (optional, conditional):
   1. Retry button visible only when backend supports retry for this event type, user has retry permission, and record status is eligible (e.g., Failed/Quarantined per backend rules).
   2. User enters justification (>= 10 chars); if too short → inline error; do not call retry endpoint.
   3. On submit, UI shows returned jobId and opens “View retry status” panel; poll with backoff and refresh record status when job completes.

## Notes
- Event type is fixed to InventoryAdjustment for this list but should still be displayed for clarity.
- Ingestion status and idempotency taxonomy are backend-authoritative; UI must not hardcode meanings beyond labels and conditional display rules.
- List response must not include raw payload; raw payload is only fetched/used when user has explicit permission.
- Permission enforcement must follow Accounting domain rules (view ingestion, view raw payload, retry ingestion); UI should hide/disable restricted controls and handle backend denial gracefully.
- Posting reference canonical identifier is postingRefId; treat as primary reference for navigation when present.
- Acceptance criteria highlights:
  - Server-side pagination only; stable sorting via backend sort key.
  - Filters are optional; invalid UUID/date range blocks requests with inline validation.
  - Detail shows curated payload by default; raw JSON only when permitted.
  - Retry requires justification (>= 10 chars), is conditional on backend support + permission, and provides job tracking with polling/backoff.
