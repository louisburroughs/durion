# [FRONTEND] [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes
## Purpose
Enable POS users viewing an invoice to quickly confirm the invoice’s current AR/payment application status and the latest accounting ingestion/posting outcome. The screen should clearly indicate whether the invoice is unpaid/partially paid/paid (including balance due) and whether accounting processing succeeded or failed with actionable references/errors. This reduces follow-up time by surfacing payment and posting state directly on the invoice detail.

## Components
- Invoice Detail header (invoice number, customer, date, total)
- “Payment & Posting” section container/card
- Payment Status display field (e.g., Unpaid / Partially Paid / Paid)
- Balance Due display field (currency-formatted)
- Optional payment outcome references list (latest outcome ID/reference, timestamp)
- Accounting ingestion/posting outcome display (status, reference ID, timestamp)
- Error message area (inline alert for posting/ingestion errors)
- Loading state (skeleton/spinner for section)
- Empty state messaging (when outcomes are unavailable)
- Responsive layout wrappers (Quasar grid/columns)

## Layout
- Top: Invoice Detail header summary
- Main (stacked sections):
  - Invoice line items / totals (existing invoice detail content)
  - Payment & Posting (card)
    - Row: Payment Status (left) | Balance Due (right)
    - Row: Accounting Outcome (full width)
    - Row: References (optional list) + Errors (inline alert if present)

## Interaction Flow
1. User navigates to an invoice detail screen.
2. System loads invoice detail data, including balance due and any payment/accounting outcome fields.
3. “Payment & Posting” section renders:
   1. Payment Status is derived from balance due:
      - If Balance Due = 0 → show Payment Status = “Paid”.
      - Otherwise show an appropriate non-paid status (e.g., Unpaid/Partially Paid) based on remaining balance.
   2. Balance Due is displayed formatted in the invoice currency.
4. System displays latest accounting ingestion/posting outcome:
   1. If success → show status and reference identifiers (and timestamp if available).
   2. If failure → show status = failed and display error details in an inline alert area.
5. Edge cases:
   1. If outcome/reference data is missing → show “No posting/outcome information available” (do not block invoice viewing).
   2. While loading → show section-level loading state; avoid layout shift where possible.

## Notes
- Acceptance criteria: When invoice balance due is 0, the “Payment & Posting” section must display Payment Status = “Paid” and Balance Due = 0 formatted in invoice currency.
- Display both payment application status and latest accounting ingestion/posting outcome with references and errors when present.
- Implement in Vue.js 3 with TypeScript using Quasar components; ensure responsive behavior and accessibility (readable labels, semantic structure, contrast for status/error states).
- Integrate with Moqui backend response fields provided by invoice detail API; handle null/undefined fields gracefully.
- TODO (dev): Confirm exact mapping rules for “Unpaid” vs “Partially Paid” when Balance Due > 0 (e.g., compare to invoice total if available) and confirm field names for outcome references/errors from backend payload.
