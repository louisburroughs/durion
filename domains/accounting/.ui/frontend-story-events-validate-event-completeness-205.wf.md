# [FRONTEND] [STORY] Events: Validate Event Completeness and Integrity
## Purpose
Provide Moqui (Vue/Quasar) screens for Accounting Operations to monitor AccountingEventIngestionRecord ingestion health, validate event completeness/integrity, and quickly diagnose failures. Enable list/search and a detailed record view that surfaces backend-authoritative statuses, errors, quarantine indicators, and posting references. Optionally allow permission-gated retry/reprocess with required justification and job status visibility, without the UI inventing domain meanings.

## Components
- Global page header with title and breadcrumb/back navigation
- List screen
  - Filter bar: Processing Status (enum), Validation Status (enum), Event Type (string), Event ID (UUIDv7 w/ client validation), Received At date range (inclusive)
  - Search/apply and clear/reset controls
  - Results table with sortable columns (default sort by receivedAt desc)
  - Pagination controls (page size + page navigation)
  - Row click navigation to detail
  - Inline badges: processing/validation status, quarantine/DLQ indicator
  - Empty state and loading state
  - Error banner/toast for fetch failures
- Detail screen
  - Summary header: ingestionId, eventId, eventType, schemaVersion, sourceModule (copy-to-clipboard)
  - Status section: processingStatus, validationStatus, quarantine/DLQ banner/indicator
  - Timestamps section: receivedAt, processedAt, occurredAt (if present)
  - Posting references section: journalEntryId (primary), ledgerTransactionId (optional) with link to Journal Entry detail when present
  - Errors section: errorCode, errorMessage, structured errorDetails (if provided), copy-to-clipboard
  - Payload section:
    - Curated/redacted payload JSON (always shown if provided)
    - Raw payload JSON (permission-gated) or “Restricted” placeholder
  - Actions: Refresh, Retry/Reprocess (permission-gated + eligibility-gated), Copy buttons
- Retry modal/dialog (optional feature)
  - Justification textarea (min 10 chars) with inline validation
  - Submit/Cancel buttons
  - Result panel showing returned jobId
  - “Check status” button and optional controlled polling indicator/backoff messaging
  - Error banner for 400/422/403 and job failure status

## Layout
- Top: Page title + breadcrumb/back
- List: Filters row above table; table in main; pagination at bottom-right
- Detail: Two-column feel (left: identifiers/status/timestamps; right: posting refs/errors); payload panels below; actions top-right
- Inline sketch: Top Header → [Filters] → [Table] → [Pagination] / Detail: [Summary+Actions] → [Info Columns] → [Payload Panels] → [Retry Modal]

## Interaction Flow
1. User opens ingestion list screen; UI fetches ingestion records and renders table with default sort (receivedAt desc).
2. User sets filters (processingStatus, validationStatus, eventType, eventId UUIDv7, receivedAt date range); UI validates UUIDv7 client-side and refreshes list server-side on apply/change.
3. User changes sort or pagination; UI refreshes list accordingly; show loading state and handle empty results.
4. User selects a row; UI navigates to detail screen and fetches ingestion detail by ingestionId.
5. Detail renders backend-provided identifiers, timestamps (only show occurredAt/processedAt if present), statuses, and quarantine/DLQ representation.
6. If status indicates success: show success badge; show posting refs if present; hide Retry by default unless backend indicates eligible and user has permission.
7. If record is rejected/failed: show error banner with backend-provided errorCode/errorMessage; render structured errorDetails if provided; show Retry only if permitted and backend indicates eligible (or explicit override flag is returned).
8. If record is duplicate ignored: show informational note “Duplicate ignored; no new postings created” and do not imply postings unless posting refs are present.
9. If record is conflict: show conflict banner and any backend-provided linkage identifiers (e.g., linked ingestionId) when available.
10. Payload handling: always show curated/redacted payload if provided; show raw payload only with permission; otherwise render “Restricted” placeholder while still showing curated payload if provided.
11. User clicks copy icons for IDs and error fields; UI copies to clipboard and shows brief confirmation toast.
12. User clicks Refresh; UI re-fetches detail and updates all sections.
13. (Optional) User clicks Retry; UI opens modal, requires justification ≥ 10 chars; blocks submit with inline error if too short.
14. On Retry submit: UI calls retry endpoint; on success displays returned jobId and enables “Check status” (manual refresh) and optional controlled polling.
15. Retry error cases: 400/422 show inline justification error if applicable or banner with backend message; 403 shows access denied without revealing record existence; job failure shows backend-defined failed status and any error code/message.

## Notes
- UI must display backend-authoritative enums/values verbatim; do not invent meanings or present “suspense” unless backend returns it (unknown event types are rejected in this workflow).
- Client-side validate UUIDv7 for eventId/ingestionId and any linked IDs before submitting filters/navigation; show clear inline validation message.
- Permission gating (UI + backend enforced): view detail, view raw payload, and retry are separate permissions; raw payload must not be rendered or hinted when not permitted (use “Restricted” placeholder).
- Quarantine/DLQ must be clearly represented (banner/badge) and emphasized for conflicts/duplicates per backend indicators.
- Posting references: journalEntryId is primary; ledgerTransactionId optional; only show navigation links when IDs are present and route exists.
- Retry is async: show jobId, provide status refresh; polling (if implemented) should be controlled with backoff and user-visible state.
- Error handling should be user-safe and actionable for upstream engineering teams (surface errorCode/message and structured details when provided).
