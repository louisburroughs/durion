# [FRONTEND] [STORY] Accounting: Apply Payment to Invoice
## Purpose
Provide a dedicated UI to apply an existing Accounting payment to one or more eligible invoices for the same customer, with explicit user-entered allocations. The screen must load payment details and eligible invoices, validate allocations client-side, and submit a single atomic, idempotent apply command. After submission, the UI should immediately display the backend results (applications, updated balances/statuses, and any created customer credit) and offer navigation to related read-only records.

## Components
- Page header: “Apply Payment to Invoices”
- Breadcrumb/back link to Payment Detail
- Payment summary panel (read-only fields)
- Eligibility/status callouts (apply-eligible vs not eligible)
- Blocking message panel (e.g., missing customer, access denied)
- Permission warning banner (view-only path)
- Eligible invoices table/list
  - Columns: Invoice Number (optional), Invoice ID, Due Date (optional), Issue Date (optional), Status (display-only), Open Amount
  - Allocation amount input per invoice row (decimal, currency scale)
  - Row-level error display area
- Allocation summary strip
  - Total Allocated
  - Remaining Amount (payment unapplied minus allocated)
  - Overpayment note (“remaining becomes Customer Credit”) when applicable
- Actions
  - Submit “Apply Payment” button
  - Secondary “Reload” button
  - Optional “Clear allocations” action (enabled when allocations exist)
- Submit in-flight indicator (button spinner/disabled state)
- Result panel (post-success)
  - Response summary (payment updated balances/status)
  - Created application records list (read-only)
  - Updated invoice summaries list
  - Customer credit summary (if created) with reference/copy ID
- Error handling UI
  - Top banner for general errors
  - Inline field errors for allocation inputs
  - “Retry” actions (including “Retry same request” for idempotent retry)

## Layout
- Top: Header + back link to Payment Detail
- Main (stacked):
  - Payment Summary panel
  - Eligibility/permission/blocking banners
  - Eligible Invoices table (with allocation inputs in rightmost column)
  - Allocation Summary strip + Actions row (Submit/Reload/Clear)
  - Post-success Results panel (appears after successful submit)

## Interaction Flow
1. Screen entry (route includes paymentId)
   1) Validate paymentId format; if invalid UUID → show “Invalid payment ID.” and stop.
   2) Call “Load payment for AR apply”; on 403 show access denied (do not reveal existence); on 404 show “Payment not found.”; on 5xx show retry action and preserve paymentId.
2. After payment load
   1) Render payment summary: paymentId, status, customerId, currency, amount, unappliedAmount, optional reference/createdAt/postedAt.
   2) If customerId is null → show blocking message “Customer not assigned to this payment.” and show link to Payment Detail; do not load invoices; disable all allocation/submit UI.
   3) Determine apply-eligibility gating: payment must be in required statuses (per backend contract); if not eligible, show non-blocking explanation and disable allocation inputs + submit.
3. Load eligible invoices (only when customerId present and payment loaded)
   1) Call “Load eligible invoices” for customerId (and implicitly currency match via backend list); on error show inline list error + retry.
   2) If list is empty → show “No eligible invoices found.” and keep Submit disabled.
4. Allocation editing
   1) Maintain client-side allocation model keyed by invoiceId.
   2) For each invoice row, user enters allocation amount (decimal with currency scale).
   3) Compute and display Total Allocated and Remaining Amount (display-only).
   4) Client-side validations:
      - Allocation amount required if row is selected/edited; must be valid decimal (no floating point).
      - Allocation invoiceId must exist in loaded eligible list (prevent manual injection).
      - Across all allocations: Total Allocated ≤ payment unappliedAmount.
      - Require at least one allocation before enabling Submit.
   5) If Remaining Amount > 0, show message: “Any remaining amount will be converted to Customer Credit on submit.”
5. Permission handling (view-only path)
   1) If user lacks apply permission: show banner “You do not have permission to apply payments.”
   2) Disable allocation inputs (preferred) and keep Submit disabled; still allow viewing payment and eligible invoices.
6. Submit apply (atomic, idempotent)
   1) Enable Submit only when: user has permission, payment eligible, invoices loaded, at least one allocation, and all validations pass; disable while request in flight.
   2) On first click, generate idempotencyKey (UUIDv7) once per attempt and persist in screen state until resolved.
   3) Call Apply Payment endpoint with paymentId, idempotencyKey, allocations.
7. Post-success
   1) Display response summary immediately:
      - Echoed idempotencyKey
      - Updated payment summary (amount/unapplied/status/customerId)
      - Created applications list with links (or copy IDs if no route)
      - Updated invoice summaries (invoiceId, openAmount, status) with links if available
      - Customer credit (if present): creditId, amount, currency; show payment unappliedAmount as returned
   2) Provide navigation links: Payment Detail (same paymentId), each affected invoice (if route exists), customer credit detail (if route exists).
8. Post-error handling
   1) 400 validation: show banner + map backend per-invoice details to row errors; map allocation field errors to the corresponding input.
   2) 409 conflict: show “Reload required” with reload action; on reload, clear allocations after refresh.
   3) 403: show access denied.
   4) 5xx/network: show retry option; if user selects “Retry same request,” reuse the same idempotencyKey for that attempt.

## Notes
- No auto-allocation: user must explicitly allocate amounts; allocations array must be non-empty.
- Atomicity is backend-enforced; UI should communicate that submit applies all allocations together.
- Currency: no FX; invoices are loaded via eligibility endpoint and must match payment currency; if payment currency missing, block submit and show message.
- Overpayment handling: any remaining amount becomes Customer Credit on submit; show pre-submit informational message when Remaining Amount > 0; after success, show created credit if returned.
- Accounting period enforcement: backend may reject apply if period closed; surface as banner and/or row errors as provided.
- Idempotency: idempotencyKey must be UUIDv7; generate once per submit attempt; prevent double-submit by disabling Submit while in flight.
- Error privacy: on payment load 403, do not reveal whether payment exists.
- Out-of-scope: payment lifecycle, invoice creation/editing, reversing/unapplying, GL posting details, multi-currency conversion, and any “apply by policy” behavior.
