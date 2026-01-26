# [FRONTEND] [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess
## Purpose
Provide Moqui screens to monitor accounting event ingestion records that are unmapped, failed, quarantined, or rejected, and to reprocess eligible records via an async retry. Enable admins to filter/sort/paginate records (defaulting to problem statuses), inspect record details (including idempotency/conflict outcomes and error context), and track retry job progress to completion. Enforce strict permission gating for list/detail access, raw payload visibility, and retry actions, with backend-authoritative enums and error messaging.

## Components
- Global page header: “Event Ingestion”
- List screen
  - Filter bar: status multi-select (default QUARANTINED + REJECTED), date basis selector (uses createdAt), date range, search fields (eventType, sourceSystem, externalId), domain linkage keys (optional)
  - Sort controls (column headers) and pagination controls
  - Results table with columns: createdAt, status, idempotencyOutcome, eventType, sourceSystem, externalId, ingestionId
  - Row action: “View” (navigates to detail)
  - Empty state and inline error banner
- Detail screen
  - Summary header: ingestionId + status badge + timestamps
  - Identifiers section: eventType, sourceSystem, externalId, idempotencyKey (if present)
  - Domain linkage section (only if present): accountingEventId, journalEntryId, postingBatchId (names per backend fields)
  - Posting references section (only if present): journalEntryId (primary), secondary reference
  - State banners: processed success, rejected error, quarantined/conflict
  - Error details panel: errorCode, errorMessage, backend-provided details
  - Payload panel:
    - Always show payloadSummary if provided
    - “View raw payload JSON” button/link (permission-gated); loads and renders payloadRaw/payloadJson
  - Retry panel (permission-gated):
    - Justification textarea (required, min 10 chars)
    - Retry button (async)
    - Retry job status widget (jobId, status, startedAt, finishedAt, message/details)
  - Refresh indicator / last-updated timestamp
- Modal or inline confirmation for Retry submission (optional)
- Toast/banner messaging for backend outcomes (403/404/409/422/500)

## Layout
- Top: Page title + breadcrumbs (List → Detail)
- List: [Filter Bar] above [Results Table] with [Pagination] at bottom-right
- Detail: Left/main column sections stacked (Summary → Identifiers → Linkage/Posting → Banners → Error → Payload → Retry); right side optional “Job Status” card when retry in progress

## Interaction Flow
1. Open Event Ingestion list screen (user has list permission).
2. Screen loads records using createdAt as date basis; default status filter includes QUARANTINED and REJECTED.
3. User adjusts filters/sorting/pagination; list refreshes accordingly; show loading state and handle errors.
4. User selects a row (View) to open ingestion record detail (requires detail permission).
5. Detail loads and displays required fields (ingestionId, eventId, eventType, sourceSystem, createdAt, status, idempotencyOutcome) plus optional identifiers/linkage/posting references if present.
6. If status is PROCESSED: show posting references (journalEntryId primary) when present; Retry may be hidden/disabled but backend remains authority.
7. If status is REJECTED: show error details; if user has retry permission, show Retry panel (subject to backend eligibility).
8. If status is QUARANTINED: show quarantine/conflict banner; show error details and idempotency outcome; Retry available if permitted.
9. Payload behavior:
   1) Always render payloadSummary if provided.
   2) If user has raw-payload permission, show “View raw payload JSON”; on click, fetch and render raw payload; otherwise omit control entirely.
10. Retry behavior (user has retry permission):
   1) User enters justification (min 10 chars); Retry button enabled only when valid.
   2) On Retry click, submit async retry request; show “Retry submitted” and display jobId + initial job status.
   3) While job is in progress, poll on a timer; update job status widget.
   4) On terminal job status, refresh ingestion detail from backend and update UI (status, error details, posting refs).
11. Alternate path: retry completes but record remains QUARANTINED or REJECTED; UI shows updated error details and keeps Retry available (if backend still allows).
12. Concurrency edge case: if another admin retried first and backend returns 409, show backend message (“Retry not allowed…”) and refresh detail; keep UI consistent with backend state.
13. Error mapping (show banner/toast and keep user on current screen):
   1) 403 → “Access denied.”
   2) 404 → “Record not found.”
   3) 409 → “Retry not allowed for this record (already processed or not eligible).”
   4) 422 → “Retry request invalid: <backend message>” (include field-level details if provided)
   5) 500 → “System error while retrying; you can try again.”

## Notes
- Strict permission gating:
  - List/detail view permission required to access screens.
  - Raw payload JSON is a separate permission; do not fetch/render raw payload without it.
  - Retry is a separate permission; hide Retry panel entirely if missing.
- Backend-authoritative enums:
  - Status enum supports at least PROCESSED, REJECTED, QUARANTINED; render unknown values safely.
  - Retry job status enum is backend-owned/opaque; UI must support at least QUEUED, RUNNING, SUCCESS/COMPLETED, FAILED (and tolerate additional values).
- UI must tolerate additional backend fields; treat record as read-only.
- Default list behavior acceptance criteria: default filter shows quarantined/rejected; rows show createdAt, status, idempotencyOutcome, eventType, sourceSystem, externalId, ingestionId.
- Retry eligibility is backend-final; even if UI enables Retry, handle 409 gracefully and refresh.
- Do not imply any accounting “suspense account” GL behavior; this is operational visibility only.
- Out of scope: editing posting rules/mappings/policies, bulk retry, purge/retention UI, operating ingestion pipeline/consumer/posting engine.
