# [FRONTEND] [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)

## Purpose
Invoice Detail must display a read-only Traceability snapshot that preserves links between an invoice and its originating Estimate, Approval, and Work Order context. Add an asynchronously loaded Artifacts list with secure download behavior, while keeping issuance actions gated by invoice status, permissions, and backend-provided blockers. Ensure users can view traceability and artifacts without being able to edit traceability fields, and safely handle elevation requirements during issuance.

## Components
- Page header: Invoice identifier + status badge
- Invoice summary panel (existing)
- Traceability panel (new, read-only)
  - Fields: Work Order ID (required), Estimate ID (required), Approval ID (required), optional fields (e.g., customer/project refs, additional reference IDs)
  - Optional link metadata rendering (e.g., clickable links per field when provided)
- Artifacts panel/list (new; async load)
  - Loading state (skeleton/spinner)
  - Empty state message
  - Error state with retry action
  - Artifact row: name, type, created at, optional description/size
  - Download action per artifact
- Issue action area (existing/updated)
  - “Issue Invoice” primary button
  - Disabled state with reason(s)
  - Blockers/validation messages list (when present)
  - Busy state (prevent double-submit)
- Elevation modal/prompt (only when required)
  - Explanation text + confirm/cancel
  - Elevation token input/collection (mechanism per app standard)

## Layout
- Top: Header (Invoice ID + status) + primary actions aligned right (Issue area)
- Main (two-column on desktop; stacked on mobile):
  - Left/Main column: Invoice Summary (top) → Traceability panel (below)
  - Right/Secondary column: Artifacts list (async)
- Inline states within panels: loading/empty/error messages inside Traceability/Artifacts areas as applicable

## Interaction Flow
1. Navigate to Invoice Detail.
2. Render Invoice Summary immediately (existing behavior).
3. Render Traceability panel:
   1) Populate from invoice detail response traceability object.
   2) Display fields as read-only; no edit controls.
   3) If link metadata is provided, render fields as links (open in new tab or in-app route per standard).
4. Load Artifacts list asynchronously:
   1) Show loading state.
   2) On success, render artifact rows with download actions.
   3) If empty, show “No artifacts available for this invoice.”
   4) If failure, show retryable error; do not infer traceability completeness from this failure.
5. Download artifact:
   1) Preferred: request artifact download via backend flow that returns a downloadable resource without exposing secrets.
   2) Optional: if a signed URL is returned only when explicitly requested and authorized, treat it as secret (never log); initiate download immediately.
6. Issue Invoice action gating:
   1) Show/enable “Issue Invoice” only when invoice is eligible (status/permissions) and blockers allow issuance.
   2) If blockers/validation errors exist, show list and keep action disabled.
   3) If blockers indicate elevation required, prompt for elevation token before calling issue.
7. Issue Invoice submission:
   1) On submit, set busy state; prevent double-submit.
   2) Handle responses:
      - 422: render structured blockers/validation errors; remain on detail.
      - 409: show conflict message; force refresh invoice detail.
      - 403: show “Not authorized to issue invoices.”
      - 503: show retryable error; keep busy state cleared after failure.
8. Optional entry path: Get Invoice by Work Order:
   1) User enters/arrives with Work Order context; call “invoice by work order.”
   2) On success, navigate to Invoice Detail by invoice ID.
   3) On 422/409/403/503, show appropriate error and remain in entry context (no navigation).
9. Status-based display:
   1) DRAFT: show blockers section (required for gating UX) and Issue action area (if permitted).
   2) PAID: show Traceability (if permitted), hide Issue action, keep artifacts viewable/downloadable if permitted.
   3) UNKNOWN/ERROR: show read-only invoice with warning “Unknown invoice state”; disable issuance; provide refresh.

## Notes
- Traceability snapshot fields are display-only and must never be editable.
- Invoice detail response must include required identifiers (invoice/work order/traceability IDs) and traceability object; issuance gating relies on blockers/validation payload for DRAFT invoices.
- Artifacts list loads asynchronously; failures must not block viewing invoice summary/traceability.
- Signed URLs (if used) must be requested only when needed, authorized, treated as secret, and never logged (BILL-DEC-005).
- Elevation is required only when backend policy indicates; UI must collect elevation token before issuing (BILL-DEC-010).
- Conflict handling (409) should trigger a refresh to avoid stale state/version mismatch.
- Ensure consistent permission handling: if user lacks authorization to issue, show clear messaging and keep issuance disabled/hidden while allowing permitted read-only views.
