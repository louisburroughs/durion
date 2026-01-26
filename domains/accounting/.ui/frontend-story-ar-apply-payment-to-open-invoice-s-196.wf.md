# [FRONTEND] [STORY] AR: Apply Payment to Open Invoice(s)
## Purpose
Enable an authorized user to allocate an eligible accounting payment across one or more open invoices for the same customer/currency. The UI must gate submission based on backend-authoritative eligibility and valid allocation totals, then submit an idempotent apply request using a UUIDv7. After a successful apply, the UI displays backend-returned application results, updated balances/settlement indicators, and any created customer credit.

## Components
- Page header: “Apply Payment to Open Invoice(s)”
- Read-only Payment summary panel
  - Payment ID (UUIDv7)
  - Customer ID (UUIDv7)
  - Currency
  - Status (includes “posted” when eligible)
  - Amount
  - Unapplied amount
  - Payment date/time (display-only if provided)
- Eligible invoices table (server-side paginated)
  - Columns (read-only): Invoice ID, Invoice number, Invoice date, Balance due, Currency, Eligibility reason/label (as provided)
  - Column (input): Allocation amount (decimal)
  - Row-level validation/error highlight
- Empty state panel (no eligible invoices)
- Submit button (“Apply” / “Submit”)
- Inline helper text (allocation rules, totals)
- Error banner (validation / network / timeout)
- Success summary panel
  - Request ID echoed back
  - Created payment application records list (invoice + amount)
  - Updated payment summary (unapplied amount, status if changed)
  - Updated invoices list (new balances + settlement indicator)
  - Optional customer credit created (ID + amount)

## Layout
- Top: Page header + brief instruction text
- Main (stacked):
  - Payment summary (read-only card)
  - Eligible invoices section:
    - Table with allocation inputs + pagination controls
    - Totals row (e.g., “Total allocated” vs “Unapplied amount”)
  - Footer actions: left = error/success messaging area; right = Submit button

## Interaction Flow
1. Load page with a selected payment; fetch and display Payment summary (read-only).
2. Fetch eligible invoices for the payment’s customer/currency; render table with server-side pagination when large.
3. If no eligible invoices returned, show empty state: “No eligible invoices for this customer/currency.” Hide/disable allocation table and keep Submit disabled.
4. User enters allocation amounts per invoice row (decimals); update “Total allocated” live.
5. Gate Submit (disabled unless all are true):
   1. User has required permission(s).
   2. Payment is eligible per backend (e.g., status indicates eligible such as “posted”; otherwise treat as ineligible).
   3. At least one allocation is entered.
   4. Sum of allocations is valid against payment/unapplied amount (no over-allocation beyond allowed policy).
   5. Each allocation is valid (non-negative, not exceeding invoice balance due, correct currency).
6. On Submit click:
   1. Generate request_id = UUIDv7.
   2. Send apply request with request_id and allocations.
   3. Disable Submit while request is in-flight.
7. If timeout/network error:
   1. Show error banner.
   2. Re-enable Submit and allow retry using the same request_id for that attempt (idempotency per AD-010).
8. On successful response:
   1. Display success summary including echoed request_id and created application records.
   2. Update Payment summary using returned payment object (including unapplied amount and status if changed).
   3. Update invoice rows using returned updated invoices (new balances and settlement indicators).
   4. If customer credit created (AD-003), display credit ID and amount.
   5. Make allocation inputs read-only for the loaded payment; user must reload to apply any remaining amount (if any).
9. Backend validation failure (atomic; no partial success):
   1. Show error banner.
   2. If error includes invoice_id-keyed details, highlight affected invoice row(s) and show inline error text.
   3. Keep existing user-entered allocations visible/editable (unless otherwise instructed) and do not display any “created applications” section.

## Notes
- Application date is backend-controlled (AD-012/E6); UI may display payment datetime if provided but must not allow editing application date.
- Treat any payment status not explicitly indicated as eligible by backend as ineligible; keep Submit disabled and show a clear reason if available.
- Allocation inputs must support decimals and enforce per-row constraints (<= invoice balance due) and overall constraints (sum allocations vs payment unapplied amount).
- Overpayment handling: when allocations cover all eligible invoice balances and payment amount exceeds total, backend may create customer credit; UI must render credit details and reflect payment unapplied amount per response (often 0.00 when credit created).
- Invoice eligibility reason/label is backend-provided; UI must not hardcode logic beyond using the eligibility list behavior.
- Server-side pagination required for large invoice lists (SD-UX-PAGINATION); ensure totals/validation consider allocations across pages (design decision: persist allocations client-side across pagination).
- After success, allocation inputs become read-only for the loaded payment to prevent applying against stale balances; require user reload to continue applying remaining amount if applicable.
