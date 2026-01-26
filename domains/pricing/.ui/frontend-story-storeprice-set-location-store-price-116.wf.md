# [FRONTEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails
## Purpose
Enable Store Managers to search/select a product and location, view base/effective pricing and any existing location override, and propose a new override price. The UI must submit the proposal to backend services and clearly reflect the outcome (auto-activated, pending approval, or rejected/invalid) while respecting guardrails and user permissions. The screen also provides read-only visibility into current override status, approval details, and audit/status history.

## Components
- Page header with title and context summary (Product, Location)
- Product selector (search/autocomplete)
- Location selector (search/autocomplete)
- “Load/Refresh Context” action
- Pricing context summary cards/rows:
  - Base price (money)
  - Effective price (money)
  - Cost (money, optional/redacted)
  - “As of” timestamp (optional)
- Existing override panel (read-only fields: status, current override price, created/updated/activated timestamps, reason/message if present)
- Override proposal form:
  - Override price input (money)
  - Submit button
  - Reset/Clear button
- Outcome messaging:
  - Success toast/alert (ACTIVE)
  - Pending approval banner (PENDING) with approval request details
  - Validation error display (field-level + global)
- Approval request details panel (read-only; shown when pending)
- Audit/status history list/table (read-only; chronological)
- Authorization-aware UI states (disabled inputs/buttons, hidden actions, read-only mode)
- Loading indicators (skeleton/spinner) and empty-state messaging

## Layout
- Top: Header (Title) + compact context chips (Product, Location) + Refresh
- Main (two-column on desktop; stacked on mobile): Left = Selectors + Pricing Summary; Right = Override (Existing + Proposal)
- Bottom: Approval Details (if any) + Audit/History table
- Inline sketch: Top Header/Context → [Left: Select + Prices] | [Right: Override] → Bottom: [Approval + History]

## Interaction Flow
1. Select context (product/location)
   1. User searches/selects Product and Location.
   2. UI enables “Load/Refresh Context” once both are selected.
   3. UI requests pricing context from backend for the selected product/location.
   4. UI displays base price and effective price; displays cost only if present/not redacted; shows “as of” timestamp if provided.
   5. If an override exists, UI displays its current status and override price (and relevant timestamps/details).

2. Submit override within auto-approval thresholds (result = ACTIVE)
   1. User enters an override price > 0 in the proposal input.
   2. UI validates required input (non-empty, numeric, > 0) before submit.
   3. User clicks Submit; UI calls backend submit endpoint with productId, locationId, and overridePrice.
   4. If backend returns ACTIVE, UI shows success message, then reloads pricing context.
   5. UI displays the override as ACTIVE in the existing override panel.

3. Submit override requiring approval (result = PENDING)
   1. User submits an override proposal as above.
   2. If backend returns PENDING, UI shows a “Pending approval” banner.
   3. UI reloads context and displays Approval Request details (status, approver/queue info if provided, submitted timestamp, optional notes).
   4. Override proposal input becomes read-only or remains editable per permissions; submit may be disabled to prevent duplicate requests (unless backend supports resubmission).

4. Submit override violating hard guardrails (validation error, HTTP 400)
   1. User submits an override that violates a hard guardrail.
   2. Backend responds with validation errors.
   3. UI displays backend-provided error message(s):
      1. Field-level errors near override price input (when applicable).
      2. Global errors in an alert area (policy missing, base price unavailable, service unavailable).
   4. UI does not display a new override as created.
   5. Entered override price remains in the input field for correction.

5. Authorization-aware behavior
   1. If user lacks permission for the location/product context, UI disables or hides Submit and makes proposal input read-only.
   2. Read-only panels (existing override, approval details, history) remain visible if allowed; otherwise show an access-restricted message.

6. History viewing
   1. After context load, UI renders audit/status history list/table (from context payload or separate call).
   2. User can scroll and review status transitions, timestamps, and rejection details (if present).

## Notes
- Guardrail/approval outcomes must be driven by backend response: ACTIVE vs PENDING vs validation failure; UI should not infer thresholds client-side.
- Error handling must standardize mapping:
  - Field errors (e.g., overridePrice) shown inline.
  - Global errors shown in a prominent alert area (policy missing, base price unavailable, service unavailable).
- Do not log money values or cost values in frontend logs; log only IDs and error codes.
- Override entity fields are largely read-only after selection; only override price is editable when allowed.
- Rejection details (reason/code/message, rejectedAt, rejectedBy) are read-only and shown when present.
- TODO for developers (per issue question): confirm exact backend endpoint paths and request/response payload shapes, including canonical error payloads for HTTP 400 (validation) vs 409 (conflict/state).
