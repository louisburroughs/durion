# [FRONTEND] [STORY] Security: Immutable Audit Trail for Price/Cost Changes and Rule Evaluations
## Purpose
Provide a permission-gated UI to search, list, and view immutable audit records for price/cost changes and pricing rule evaluations. Enforce required guardrails (date range limits and minimum filters) to prevent overly broad searches while still enabling cross-location investigations. Allow drill-down from audit/estimate contexts into Pricing Snapshot details and (when available) Pricing Rule Trace, with clear handling for redaction, restricted fields, and missing trace data.

## Components
- Global header with page title and breadcrumbs (Audit Search → Audit Detail → Snapshot Detail)
- Audit Search form
  - Date range picker (UTC) with 90-day max constraint
  - Required inputs: start date, end date
  - Additional filters (at least one required beyond date range): SKU, eventId, aggregateId, actorId, correlationId, reasonCode, eventType, etc.
  - Location selector (multi-select) with “at least one location required” validation (permission-gated cross-location controls)
  - Search/Submit button, Clear/Reset button
  - Inline field-level error messages (maps from 400 responses)
- Results list (paginated table)
  - Default sort: occurredAt descending
  - Columns (read-only): eventId, eventType, occurredAt, locationId, aggregateType, aggregateId, actor (if present), reasonCode, correlationId (if present)
  - Row action: View Detail
- Audit Detail view (read-only)
  - Normalized fields section (eventId/eventType/occurredAt/locationId/actor/aggregateType+aggregateId/reasonCode/correlationId)
  - Optional structured diff viewer (render if present)
  - Optional notes/message fields (escaped, length-limited)
  - Optional raw payload section (admin/debug only; escaped rendering)
  - Optional proof fields section (restricted permission): hash/signature/chain references (read-only)
- Pricing Snapshot Detail view (read-only; permission-gated)
  - Snapshot fields: snapshotId, occurredAt, money fields (if returned), traceId (if present)
  - Optional snapshot payload (restricted; escaped rendering)
  - “View Pricing Trace” section (permission-gated)
- Pricing Rule Trace section (read-only; permission-gated)
  - Trace header: traceId
  - Trace steps list (paged if needed)
    - Step index (stable), rule name, outcome/status
    - Optional input/output JSON (may be redacted; escaped rendering)
  - Pagination/truncation indicators: nextCursor and/or hasMore (+ optional total/limit indicators if provided)
- Standard pagination controls (SD-UX-PAGINATION): page size, next/prev, sort indicator

## Layout
- Top: Header + breadcrumbs
- Main (Audit Search): Search form at top; results table below; pagination at bottom
- Main (Audit Detail): Summary fields at top; diff panel mid; restricted sections (raw payload, proof) below with permission-based visibility
- Main (Snapshot Detail): Snapshot summary top; optional payload mid; trace section below (or “Trace unavailable” notice)
- Inline ASCII hint: Header/Breadcrumbs → [Search Form] → [Results Table + Pagination] → [Detail Panels]

## Interaction Flow
1. Open Audit Search (authenticated user with required permission)
   1) UI renders required Start Date and End Date inputs.
   2) UI displays guidance: “Add at least one filter beyond date range.”
   3) UI enforces max date range of 90 days (disable invalid selections and/or show inline error).
2. Submit search with guardrails
   1) If date range missing/invalid or >90 days, block submission and show inline errors.
   2) If no additional filter beyond date range, block submission and show inline error on filter area.
3. Successful search by SKU within valid date range
   1) User enters start/end dates (≤90 days) and SKU filter.
   2) User submits; UI calls search endpoint with date range + SKU (+ selected locations if applicable).
   3) UI shows loading state; then renders paginated results sorted by occurredAt desc by default.
   4) User changes page/sort; UI updates query params and refetches (presentation-only change per SD-UX-PAGINATION).
4. Location selection requirement (cross-location controls permission-gated)
   1) If user must select locations and none are selected, block submission and show “Select at least one location.”
   2) When one or more locations selected, submit includes locationIds in request.
5. View Audit Detail (normalized fields)
   1) User selects a result row “View Detail”; navigate to Audit Detail with eventId.
   2) UI calls audit detail endpoint; render read-only fields: eventId, eventType, occurredAt, locationId, actor (if present), aggregateType/aggregateId, reasonCode, correlationId (if present).
   3) If structured diff present, display diff viewer; otherwise hide section.
   4) If raw payload/proof fields exist but user lacks permission, hide sections or show “Restricted” placeholder (no data).
6. Error handling (400 field-level)
   1) If search endpoint returns 400 with field errors, map each to the corresponding input inline.
   2) Preserve user-entered values; do not clear form on error.
7. Drill estimate line → Pricing Snapshot Detail → Rule Trace
   1) From an estimate line containing a pricingSnapshotId, user clicks “View Pricing Trace.”
   2) Navigate to Pricing Snapshot Detail with snapshotId; UI calls snapshot detail endpoint and renders snapshot fields read-only.
   3) If trace is available and user has permission, UI calls trace endpoint and renders trace steps list (paged if needed).
   4) If snapshot found but trace unavailable (404 on trace or missing traceId), show snapshot section and a clear “Trace unavailable” message; do not block snapshot display.

## Notes
- All audit/snapshot/trace fields are read-only; immutable audit trail implies no edit actions anywhere in these views.
- Guardrails/AC: require start+end date; enforce ≤90-day range; require at least one additional filter beyond date range; require at least one location selection when applicable.
- Sorting/pagination: default occurredAt descending; use standard pagination controls; support cursor-based (nextCursor) and/or boolean (hasMore) truncation patterns.
- Security/permissions (navigation + visibility):
  - Audit Search/List, Audit Detail, Raw payload, Pricing Snapshot Detail, Pricing Trace, Proof fields, Cross-location search controls are permission-gated; UI must hide or restrict sections accordingly.
- Rendering safety: any JSON/text payloads and messages must be escaped; length-limited fields should truncate with “Show more” if needed (still read-only).
- Redaction: trace input/output may be redacted; UI should display “Redacted” placeholders without attempting to infer missing content.
- Correlation: display correlationId and trace context fields when present to support investigation workflows.
