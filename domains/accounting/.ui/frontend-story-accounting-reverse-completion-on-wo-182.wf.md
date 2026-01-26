# [FRONTEND] [STORY] Accounting: Reverse Completion on Workorder Reopen
## Purpose
Provide an Accounting Event Ingestion Monitoring UI that lets authorized users find ingestion records by workorderId and inspect processing outcomes. Enable a detail view that surfaces processing status, idempotency outcome, and posting references (e.g., journalEntryId) to support troubleshooting and traceability. Gate raw payload visibility behind explicit permission and ensure safe handling (no caching/logging).

## Components
- Page header: “Accounting Event Ingestion”
- Permission-gated access wrapper (view monitoring)
- Filter form
  - Workorder ID input (UUIDv7)
  - Submit/Search button
  - Clear/Reset button
  - Inline validation/error message for invalid UUID
- Results table (paginated)
  - Columns: ingestionId, eventId, eventType, receivedAt, processingStatus, idempotencyOutcome
  - Row action: View details (click row or “Details” link)
- Pagination controls (page size, next/prev)
- Ingestion Detail view
  - Summary fields: ingestionId, eventId, eventType, receivedAt, processedAt (if present), failedAt (if present)
  - Status badges: processingStatus, idempotencyOutcome (state-based styling; success styling for processed)
  - Optional fields: source, errorCode, errorMessage (user-safe), correlationId (if present)
- Posting References section (always shown)
  - Primary posting reference (journalEntryId) with Copy button
  - Secondary reference (if present) with Copy button
  - Optional link to Journal Entry detail screen if route exists; otherwise copy-only
  - Empty-state text when missing
- Payload section
  - Redacted summary display (if present)
  - Raw payload JSON accordion/expand panel (permission-gated)
  - “Copy raw payload” (optional) with warning text (if allowed)
- Global UI states
  - Loading spinner/skeleton
  - Empty states (list + detail)
  - Error banner/toast for API failures

## Layout
- Top: Header + brief helper text (“Search ingestion records by Workorder ID”)
- Main (List): Filter form above a paginated table; empty/error states inline below filter
- Main (Detail): Two-column feel within main content: left = key fields/status; right = posting references + payload panels; footer area for timestamps and optional metadata

## Interaction Flow
1. Access control (monitoring)
   1. User navigates to Accounting Event Ingestion list route.
   2. If user lacks “View ingestion monitoring” permission, show an access denied state (no data rendered).
2. Scenario 1: View ingestion list filtered by workorderId
   1. User enters a UUIDv7 into the Workorder ID filter and clicks Search.
   2. UI validates UUID format; if invalid, show inline validation and do not call the API.
   3. On valid submit, UI calls the backend ingestion monitoring list endpoint with workorderId filter.
   4. UI renders a paginated table of matching ingestion records.
   5. Each row displays: ingestionId, eventId, eventType, receivedAt, processingStatus, idempotencyOutcome.
   6. If no results, show explicit empty state (“No ingestion records found for this workorderId.”).
3. Navigate to ingestion detail
   1. User clicks a row (or Details) to open the ingestion detail screen for ingestionId.
   2. UI calls the backend ingestion monitoring detail endpoint for that ingestionId.
   3. UI shows loading state, then renders detail fields and status badges.
4. Scenario 3: Processed status with journal entry reference
   1. If processingStatus indicates processed/success, apply success styling to the status area.
   2. In Posting References, show journalEntryId as the primary posting reference when present.
   3. User can copy the journalEntryId.
   4. If a Journal Entry detail route exists, journalEntryId is also a link; otherwise display as text with copy only.
5. Posting references empty state (always visible)
   1. If no primary posting reference exists, show: “No posting reference available for this ingestion record.”
   2. Keep the Posting References section visible even when empty.
6. Scenario 6: Raw payload visibility with permission
   1. If user has both “View ingestion monitoring” and “View raw payload JSON” permissions, show the Raw Payload expand control.
   2. User expands Raw Payload to view JSON safely (read-only).
   3. UI must not cache or log raw payload content (no persistence in local storage; avoid console logging).
   4. If user lacks raw payload permission, hide/disable raw payload section and show a brief note (“You do not have permission to view raw payload.”).
7. Error handling
   1. If list or detail API call fails, show an error banner with retry action.
   2. If ingestionId not found, show a not-found empty state with link back to list.

## Notes
- Data model fields to support (list + detail): ingestionId (UUIDv7), eventId (UUIDv7), eventType (string), source (optional), receivedAt (datetime), processedAt/failedAt (optional), processingStatus (backend-owned enum), idempotencyOutcome (backend-owned enum), errorCode/errorMessage (optional; user-safe), primaryPostingReference (journalEntryId; UUIDv7/string), secondaryReference (optional), referenceType/referenceValue (optional), redactedSummary (optional), rawPayload (optional; permission-gated).
- Acceptance criteria highlights:
  - List filter by workorderId calls the backend with the filter and shows paginated results with required columns.
  - Detail view displays processingStatus and idempotencyOutcome.
  - Posting References section is always shown; if empty, show explicit message.
  - Primary posting reference is copyable and navigable only if a Journal Entry screen/route exists; otherwise copy-only ID.
  - Raw payload JSON is only viewable with explicit permission and must not be cached or logged.
- Safe defaults: Provide explicit empty states for missing list/detail data (SD-UX-EMPTY-STATE).
- TODO (dependency check): Confirm whether a Journal Entry detail screen/route exists; if not, implement copy-only behavior for journalEntryId.
