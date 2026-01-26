# [FRONTEND] [STORY] Accounting: Ingest InventoryIssued Event
## Purpose
Provide Accounting users a Moqui screen set to monitor and troubleshoot InventoryIssued event ingestion. Users can filter and review ingestion records, inspect processing outcomes and posting references, and (when permitted and supported) retry failed/quarantined ingestions with justification. The UI must enforce domain permissions, validate UUIDv7 inputs client-side, and protect raw payload access by default.

## Components
- Accounting navigation menu item: “InventoryIssued Ingestion” (visibility gated by View Ingestion permission)
- InventoryIssuedList screen
  - Filter form
    - Received At date range: From / To (inclusive)
    - Processing Status multi-select (backend-owned enum values)
    - Idempotency Outcome multi-select (backend-owned enum values)
    - Ingestion ID exact match (UUIDv7; client-side validation)
    - Optional identifier filters (only if backend supports): eventId, schemaVersion, sourceModule (opaque strings unless guaranteed UUIDv7)
    - Search button; Reset/Clear button
  - Inline validation message for invalid UUID; Search disabled when invalid
  - Results table (paginated, sortable)
    - Columns: receivedAt, ingestionId, eventType, processingStatus, idempotencyOutcome, processedAt (optional), plus any backend-provided domain keys (optional)
    - Row action: View Details (navigates by ingestionId)
  - Pagination controls (default page size 25; configurable)
  - Non-technical error banner with Retry load action (for 5xx)
- InventoryIssuedDetail screen (read-only)
  - Ingestion Record Summary section
  - Processing Outcome section (includes quarantine/duplicate indicators)
  - Posting References section (journalEntryId primary; ledgerTransactionId secondary)
  - Payload section
    - payloadSummary (always shown)
    - Raw payload JSON panel (permission-gated; do not fetch unless permitted)
  - Copy-to-clipboard buttons: ingestionId, eventId, journalEntryId, ledgerTransactionId
  - Retry ingestion action (permission + state + backend-support gated)
    - Retry modal/dialog with justification textarea (min 10 chars)
    - Submit + Cancel
    - Async job status display/polling area (jobId + status)

## Layout
- Top: Page title + brief subtitle (“InventoryIssued ingestion records”)
- Main (List): Filter panel at top; results table below; pagination at bottom
- Main (Detail): Stacked sections in order: Summary → Outcome (banners) → Posting References → Payload; actions (Copy, Retry) aligned top-right or within section headers
- Inline hint: [Top: Filters] → [Table] → [Pagination] ; Detail: [Summary][Outcome][Posting][Payload]

## Interaction Flow
1. Entry point visibility
   1. If user lacks View Ingestion permission, hide the Accounting menu item (deny-by-default).
   2. If user has permission, menu item navigates to InventoryIssuedList.
2. List: filter and search
   1. User sets Received At From/To (inclusive) and/or selects Processing Status and Idempotency Outcome.
   2. User enters ingestionId (UUIDv7) for exact match; UI validates format client-side.
   3. If UUID invalid, show inline error and disable Search until corrected.
   4. On Search, call server-side filtered list; default sort by receivedAt descending; show page size default 25.
3. List: view details
   1. User clicks a row action to open detail using ingestionId (not eventId).
   2. Detail loads and displays eventType (expected InventoryIssued but show actual), identifiers, timestamps, enums, and error fields if present.
4. Detail: state-specific UI
   1. If processingStatus indicates success/processed: show success status; show posting references if present; hide Retry action.
   2. If processingStatus indicates failed/error: show error banner with errorCode/errorMessage; show Retry action if permitted and supported.
   3. If quarantined (e.g., conflict/needs attention): show quarantine banner; show any backend-provided conflict details; show Retry action if permitted and supported.
   4. If idempotencyOutcome indicates duplicate ignored: show “Duplicate ignored” badge; do not imply new posting.
   5. If idempotencyOutcome indicates duplicate conflict: show “Duplicate conflict” badge + quarantine banner; do not suggest steps beyond retry if supported.
5. Detail: payload access
   1. Always show curated payloadSummary.
   2. Only if user has View Raw Payload permission: reveal Raw payload JSON section and fetch raw payload on demand (do not fetch otherwise).
6. Detail: retry ingestion (optional)
   1. Retry button is visible only when user has Retry permission, record status is failed/quarantined (never for processed), and backend indicates retry supported.
   2. Clicking Retry opens modal requiring justification (min 10 chars); Submit triggers async retry request.
   3. While request is in-flight, disable Retry controls.
   4. On response, show jobId and begin status polling; display current job status until completion/failure.
7. Error handling (list)
   1. If list endpoint returns 5xx, show non-technical banner (“Could not load ingestion records”) with a Retry button to reload without full app refresh.

## Notes
- Permissions (Accounting domain model): enforce View Ingestion for entry/list/detail; View Raw Payload gates raw JSON visibility and fetching; Retry Ingestion gates retry action and modal.
- UUID validation: client-side validate UUIDv7 format for ingestionId filter (and any other UUID-designated fields); block Search on invalid input.
- Filtering and enums: Processing Status and Idempotency Outcome are backend-owned enums; UI must not hardcode meanings beyond display labels and filtering options provided/known.
- Navigation key: list-to-detail must use ingestionId as the primary key, not eventId.
- Posting references: journalEntryId is primary (link if available); ledgerTransactionId is secondary (display if present); provide copy buttons for both.
- Payload safety: payloadSummary is curated/redacted and safe by default; raw payload JSON must not be fetched or displayed without explicit permission.
- Logging constraint: UI must not log raw payload bodies; only log identifiers and filter metadata for diagnostics.
- Retry support detection: only show retry if backend supports it (endpoint availability or a supported flag if provided); treat as optional feature.
- Acceptance scenarios to cover: processed detail shows posting reference when present; quarantined records are filterable and show quarantine banner; list 5xx shows friendly error and allows retry loading.
