# [FRONTEND] [STORY] AP: Execute Payment and Post to GL
## Purpose
Enable AP Clerks/Managers to execute an AP vendor payment from a Moqui screen flow by selecting a vendor and eligible open bills, entering payment details, and optionally specifying explicit allocations. After submission, users must be able to view the resulting payment record (including gateway transaction reference if any, allocations, and unapplied remainder) and monitor asynchronous GL posting status. The UI must enforce allocation validation consistent with Billing rules and provide idempotent behavior when re-submitting with the same paymentRef/idempotency key.

## Components
- Page header with breadcrumb: AP > Payments > New / Payment Detail
- Vendor lookup/select (search field + results dropdown/list)
- Vendor summary panel (vendor name/id)
- Eligible bills list (read-only rows with select checkboxes)
  - Columns: billId, billNumber (optional), billDate/dueDate (optional), openAmount, currency, status (must include OPEN)
- Payment form fields
  - grossAmount (decimal, > 0)
  - currency (string)
  - paymentRef / idempotency key (string; default generated UUIDv7; editable)
  - memo/notes (optional, if supported)
- Optional allocations grid (editable)
  - Rows: billId (UUIDv7), appliedAmount (decimal ≥ 0)
  - Add row / remove row actions
  - Inline validation messages
- Actions
  - Load Bills (after vendor select)
  - Execute Payment (primary)
  - Cancel / Reset
- Submission status area (inline spinner/progress + last request outcome)
- Payment Detail view (read-only)
  - billingPaymentId, vendorId, paymentRef, grossAmount, currency
  - Payment status (AP Vendor Payment Status enum)
  - Gateway transaction id/reference (if provided)
  - Allocations list (billId + appliedAmount)
  - Unapplied amount (if provided)
  - GL posting status (pending/posted/failed) + acknowledgement reference (if provided)
  - Posting error summary (sanitized) and optional detailed errors (permission-gated)
  - Status history/timeline (if provided) with timestamps
  - Created by + created timestamp (if provided)
- Refresh button (reload payment record)
- Error banner/toast area (422/409/503 and general failures)

## Layout
- Top: Header + breadcrumb + page title (New Payment / Payment Detail)
- Main (New Payment): Left = Vendor + Bills; Right = Payment Details + Allocations + Actions
- Main (Detail): Single-column summary cards stacked (Payment Summary, Allocations, GL Posting, Status History)
- Inline ASCII hint: [Vendor/Bills | Payment Form+Allocations] → [Execute] → [Payment Detail]

## Interaction Flow
1. Navigate to AP Payments > New Payment.
2. Search/select Vendor.
3. Click Load Bills to fetch eligible vendor bills; display only payable/open bills (server authoritative).
4. Select one or more bills (optional if backend supports unapplied payments).
5. Enter grossAmount, currency, and paymentRef (pre-filled UUIDv7; user may override).
6. (Optional) Enter explicit allocations:
   1) Add allocation rows for selected bills (billId) and appliedAmount.
   2) UI validates required fields when allocations present and appliedAmount ≥ 0; show inline errors.
7. Click Execute Payment:
   1) Send execute request with paymentRef idempotency key and optional allocations.
   2) On success, navigate to Payment Detail for returned billingPaymentId.
8. Payment Detail screen:
   1) Display payment status, gateway reference (if any), allocations, unappliedAmount (if any).
   2) Display GL posting status and acknowledgement reference when available.
   3) If GL posting status is FAILED, show sanitized error summary; show detailed errors only if permitted.
9. Refresh behavior:
   1) User clicks Refresh to reload payment record and observe transitions (e.g., GL_POST_PENDING → GL_POSTED/FAILED).
10. Edge case: Idempotent resubmit
   1) If user submits again with same paymentRef and identical payload, UI shows existing payment result (no duplicate execution) and navigates to its detail.
   2) If same paymentRef with different payload returns 409, show conflict message and prompt user to generate a new paymentRef.
11. Edge case: Validation failures (422)
   1) Show field-level errors (grossAmount <= 0, allocations invalid, bills not open/payable).
   2) Keep user inputs intact for correction.
12. Edge case: Downstream unavailable (503) or gateway failure status
   1) Show non-sensitive error banner; allow user to retry with same paymentRef (idempotent) or start new payment with new paymentRef.

## Notes
- Authorization is enforced server-side; UI should hide/disable Execute and sensitive error details when permissions are missing (execute payment, view payment detail, view vendor bills, view detailed posting errors).
- Allocation rules must align with Billing domain (server authoritative): bills must be open/payable; allocations must be valid; partial allocations are allowed; UI must not assume bill settlement unless backend indicates.
- Idempotency: paymentRef acts as idempotency key; same key + same payload returns existing payment; same key + different payload yields 409 conflict.
- GL posting acknowledgement is asynchronous and read-only: UI must only display backend-provided GL status fields and must not provide any manual “mark posted” controls.
- Observability: propagate W3C Trace Context headers on all Billing/AP calls from the UI client.
- Payment Detail should support audit needs: show allocations, GL status, acknowledgement reference, status timeline (if provided), and created-by metadata (if provided).
- Error handling must be sanitized for user display; detailed posting errors are permission-gated.
