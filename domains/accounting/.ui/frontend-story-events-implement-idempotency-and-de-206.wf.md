# [FRONTEND] [STORY] Events: Implement Idempotency and Deduplication

## Purpose
Provide Accounting users a permission-gated UI to search, review, and troubleshoot ingestion records by identifiers, status, and time range. Enable safe viewing of ingestion details (including errors, posting references, and curated payload) while preventing data leakage when access is denied. Allow eligible records to be retried asynchronously with required justification and job status polling, relying on backend-authoritative enums and enforcement.

## Components
- Accounting navigation menu item: “Event Ingestion” (permission-gated)
- Ingestion List screen
  - Search/filter form
    - Identifier filters (UUIDv7): ingestion record ID, event ID
    - Text filter(s): source/system or other string identifier
    - Status dropdowns (backend enums): ingestion status, processing status
    - Date range: from datetime, to datetime
    - Pagination controls: page index, page size
    - Submit/Search button, Clear/Reset button
  - Results table/list
    - Columns: IDs, statuses, created/updated timestamps, key domain linkage keys (if present), primary posting reference (if present)
    - Row action: View details
  - Global banners/toasts for safe errors (401/403/5xx) and generic validation
- Ingestion Detail screen
  - Header with record identifiers and copy-to-clipboard actions
  - Summary section (statuses, timestamps, identifiers, domain linkage keys)
  - Quarantine/Conflict banner area (when indicated by backend fields/status)
  - Errors section (error code, safe error message)
  - Posting References section (primary and secondary; link to Journal Entry view if route exists)
  - Payload section
    - Curated payload JSON viewer (default, if present)
    - Raw payload JSON viewer (only if permitted and provided)
  - Retry ingestion action
    - Retry button (permission-gated)
    - Retry modal/dialog with justification textarea + submit/cancel
    - Job status panel (polling indicator, latest status, refresh)
- Inline field validation messages (UUID/date/justification)
- Empty states (no results, missing optional fields)

## Layout
- Top: Accounting header + breadcrumb (Accounting → Event Ingestion → List/Detail)
- Left: Accounting navigation; hide “Event Ingestion” entry unless view permission
- Main (List): Filters panel above results table; pagination below table
- Main (Detail): Stacked sections: Header → Summary → (Banner) → Errors → Posting Refs → Payload → Retry/Job Status
- Right (optional): Quick actions card (Copy IDs, Retry button) aligned with detail header

## Interaction Flow
1. Navigate to Accounting → Event Ingestion.
2. If user lacks view permission, menu entry is hidden; direct route access shows “Access denied” without confirming record existence.
3. On Ingestion List, user enters optional filters (IDs/status/date range) and submits search.
4. Client validates UUIDv7 fields (ingestion record ID, event ID); if invalid, block submit and show inline “Enter a valid UUID”.
5. Client validates date range (from ≤ to); if invalid, block submit and show inline error.
6. UI calls backend list endpoint with query params (page default 0, size default 25); renders results table or “No records found”.
7. Error handling on list:
   1. 401/403: show access denied (safe, no existence leakage).
   2. 400/422: map known field errors to inline fields when possible; otherwise show generic validation banner.
   3. 5xx: show generic failure banner.
8. User selects a row to open Ingestion Detail by ingestion record ID.
9. On detail load, UI fetches record detail and renders:
   1. Summary identifiers, statuses (backend enums), timestamps, domain linkage keys (if present).
   2. Quarantine/conflict indicator banner when backend indicates quarantine/conflict; show related fields if present.
   3. Errors: error code and safe message if present.
   4. Posting references: show primary; show secondary if present; if missing show “Posting not created”.
   5. Payload: show curated payload if present; show raw payload only if user has raw permission and backend provides it.
10. Raw payload gating:
   1. If user lacks raw permission, hide raw payload section and avoid fetching raw payload when possible (prefer backend omission).
11. Retry ingestion:
   1. Retry button is hidden without retry permission.
   2. Retry button is enabled only when backend indicates allowed via a retry-eligible flag or when a fallback condition applies; otherwise disabled.
   3. Clicking Retry opens modal requiring justification (min 10 characters); client blocks submit and shows inline error if too short.
   4. Submit triggers async retry request; disable controls while in-flight.
   5. On success, display returned job ID/status and begin polling job status endpoint until terminal state; show current status and allow manual refresh.
12. Retry error handling:
   1. 409/422: show safe banner; if backend returns validation errors, map to justification field when key matches.
   2. 401/403: show access denied (safe).
   3. 404: show “Record not found” (safe).
   4. 5xx: show generic failure banner; keep detail visible.

## Notes
- UI must treat ingestion status and processing status as backend-authoritative enums; do not invent meanings or labels beyond displaying provided values.
- Permission gating per Accounting domain rules (Decision AD-013):
  - View ingestion controls menu visibility and detail access behavior.
  - View raw payload controls raw payload section visibility and should prevent unnecessary retrieval.
  - Retry ingestion controls retry button visibility and modal access.
- Client-side validation (Decision AD-006):
  - UUIDv7 format validation for identifier filters; block submit on invalid.
  - Date range validation (from must be ≤ to).
  - Retry justification required, minimum 10 characters.
- Standard safe error mapping:
  - 401/403: “Access denied” without confirming record existence.
  - 404: “Record not found”.
  - 400/422: map backend field errors to inline fields when keys match known fields; otherwise generic validation banner.
  - 409 and 422 during retry must be handled gracefully even if UI thought retry was allowed; backend enforcement is source of truth.
- Detail screen should support copy/link actions for identifiers (ingestion record ID, event ID) and any backend-provided domain keys.
- Posting reference linking: if a Journal Entry view exists, provide link; otherwise show reference as copyable text (route TBD).
- Optional fields must be tolerated as missing; sections should collapse to “Not available” rather than erroring.
- Async retry job polling should stop on terminal states and clearly indicate in-progress vs completed/failed.
