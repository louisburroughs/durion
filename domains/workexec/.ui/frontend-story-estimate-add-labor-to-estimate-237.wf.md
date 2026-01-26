# [FRONTEND] [STORY] Estimate: Add Labor to Estimate
## Purpose
Enable a Service Advisor to add a labor/service line item to an existing estimate by searching and selecting from the Service Catalog and submitting the selection to the backend. The UI must enforce that only Draft estimates are editable and validate required inputs (e.g., labor units for time-based services). After a successful add, the estimate line items and totals must refresh to reflect backend-derived pricing and updated totals, with traceability-friendly behavior (idempotent submit, optional concurrency token).

## Components
- Estimate header summary (Estimate ID, Status)
- Totals summary panel (e.g., Subtotal/Tax/Fees/Total per API)
- “Add Labor/Service” entry point (button)
- Add Labor/Service panel or modal
  - Catalog search input (text)
  - Pagination controls (Prev/Next)
  - Results list/table (Service Code, Description, Service Type indicator: TIME_BASED vs FLAT_RATE)
  - Selected Service display (read-only summary)
  - Labor units input (decimal; shown/editable only for TIME_BASED)
  - Flat-rate indicator (read-only when FLAT_RATE)
  - Notes input (optional)
  - Inline validation/error messages
  - Submit button (disabled until valid)
  - Cancel/Close button
- Estimate line items list (shows newly added labor/service item with code, description, units, backend pricing fields)
- Non-editable state message banner (for non-DRAFT / deep-link)
- Loading states (searching, submitting) and error banner/toast (standard error envelope)

## Layout
- Top: Estimate header summary (ID + Status) and Totals summary aligned right
- Main: Estimate line items list; above list, “Add Labor/Service” button (disabled/hidden if not DRAFT)
- Overlay/Side panel: Add Labor/Service (Search at top → Results list → Selected Service + inputs → Actions at bottom)

## Interaction Flow
1. Open Estimate Details for a given estimateId.
2. System loads estimate details (status, existing items, totals, optional concurrency token) and renders header, totals, and line items.
3. If estimate status is DRAFT, show enabled “Add Labor/Service”; otherwise hide/disable it.
4. If user is deep-linked to add flow while estimate is not DRAFT:
   1) Show message “Estimate is not editable in this state.”
   2) Route back to Estimate Details view.
5. User clicks “Add Labor/Service” to open the add panel/modal.
6. User enters a search query and triggers catalog search; system calls Service Catalog search with pagination.
7. System displays results list with service code, description, and service type indicator (TIME_BASED vs FLAT_RATE).
8. User selects a catalog result; system sets “Selected Service” and updates the form:
   1) If TIME_BASED: show editable Labor Units input.
   2) If FLAT_RATE: show read-only flat-rate indicator; do not assume editable fields unless backend explicitly allows.
9. Validation while editing:
   1) For TIME_BASED, Labor Units must be > 0; show inline error “Labor units must be a positive number.” and keep Submit disabled until valid.
10. User clicks Submit:
   1) Send idempotent mutation request with required Idempotency-Key header.
   2) Include estimateId, serviceId, laborUnits (required for TIME_BASED; omitted/ignored for FLAT_RATE per contract), optional notes, and optional concurrency token if supported.
11. On success:
   1) Prefer updating UI from response payload (created/updated line(s), updated totals, updated concurrency token).
   2) If response does not include totals/lines, re-fetch estimate details and refresh line items + totals.
12. On error (standard error envelope):
   1) Show error banner/toast; keep panel open.
   2) Allow retry using the same Idempotency-Key for the same attempt.
13. After success, close panel/modal and ensure the new labor/service line is visible in the estimate line items list with backend-derived pricing fields and updated totals.

## Notes
- Editability constraint: Estimate must be in DRAFT to add labor/service; otherwise disable/hide add action and block deep-linked add with a clear message and redirect back.
- IDs are opaque strings; UI must not validate IDs as UUID/numeric and should rely on search/selection rather than manual ID entry.
- Pricing fields (rate/extended amount) are backend-derived and read-only unless the backend contract explicitly signals manual entry is allowed (do not assume).
- Submit must be idempotent (Idempotency-Key required) to support safe retries and audit/compliance expectations.
- Concurrency token support is optional but recommended; if provided by backend, include it in mutation and update stored token from response.
- Custom labor line is optional and only shown if backend contract allows; if enabled, require description and validate inline (“Description is required.”).
