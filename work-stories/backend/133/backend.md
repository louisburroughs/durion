Title: [BACKEND] [STORY] AR: Create Customer Invoice from Invoice-Issued Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/133
Labels: type:story, domain:accounting, status:ready-for-dev

## Story Intent
As the Accounting / Accounts Receivable (AR) system, I need to consume `InvoiceIssued` events and create corresponding AR invoice records and balanced general ledger journal entries, so that customer obligations and company revenue are accurately and traceably recorded in the financial system of record.

## Actors & Stakeholders
- **Primary Actor (System):** Accounting / AR Service (owns AR sub-ledger + GL posting)
- **Initiator (Upstream):** Billing/Sales service publishing `InvoiceIssued`
- **Stakeholders:** Finance/Accounting team, Auditors

## Preconditions
- Accounting service subscribes to the `InvoiceIssued` event stream.
- Event schema for `InvoiceIssued` is versioned and accessible.
- Chart of Accounts is configured (AR, Revenue, Tax Payable at minimum).
- Failure handling exists (retry policy + DLQ).

## Functional Behavior
1. **Consume event:** Accounting service receives `InvoiceIssued`.
2. **Idempotency:** If an AR invoice already exists for `sourceInvoiceId` (or other unique key), acknowledge and stop.
3. **Validate payload:** Required identifiers/amounts must be present and non-negative.
4. **Resolve payment terms (`paymentTermsDays`) (resolved):**
   - If `event.paymentTermsDays` is non-null and > 0: use it.
   - Else lookup customer default terms in Accounting by `customerId`.
   - If still unresolved: fail processing (do not create invoice) and route to DLQ with `MissingPaymentTerms`.
5. **Create AR invoice:** Persist invoice header + lines.
   - `dueDate = invoiceDate + paymentTermsDays`.
   - `status = Posted/Open`.
6. **Resolve revenue GL account per line (resolved):**
   - If `lineItem.revenueGlAccountCode` present: use it.
   - Else if header-level revenue account exists in the event schema: use it.
   - Else resolve deterministically via Accounting mappings (priority):
     1) product/service → revenue account (e.g., `productId`/`sku`/`serviceCode`)
     2) customer → default revenue account
   - If still unresolved: fail processing and route to DLQ with `MissingRevenueGlAccountCode` (include identifiers).
7. **Generate balanced journal entry:** In one atomic DB transaction:
   - **Debit** AR for `totalAmount`
   - **Credit** Revenue for `subtotalAmount` (split by line if required by your GL model)
   - **Credit** Tax Payable for `taxAmount`
8. **Traceability:** Persist `sourceEventId` (`eventId`) and `sourceInvoiceId` on both AR invoice and journal entry, and link the records.
9. **Acknowledge event** on success.

## Alternate / Error Flows
- **Duplicate event:** log as duplicate; ack; no-op.
- **Invalid payload:** fail validation; no records created; DLQ + alert.
- **Missing terms / missing revenue account mapping:** fail; no records created; DLQ + alert.
- **DB failure:** rollback; retry per policy; DLQ after retries exhausted.

## Business Rules
- **Idempotency:** exactly one AR invoice + one journal entry per `sourceInvoiceId` (or chosen unique key).
- **Balanced JE:** total debits must equal total credits.
- **System of record:** Accounting owns AR invoices + GL journal entries.
- **No silent defaults:** missing payment terms or revenue account mapping must fail deterministically.

## Data Requirements
### `InvoiceIssued` event (minimum)
- `eventId` (UUID)
- `sourceInvoiceId` (string)
- `customerId` (string)
- `invoiceDate` (date)
- `currency` (ISO 4217)
- `lineItems[]` including amounts and optional `revenueGlAccountCode`
- `subtotalAmount`, `taxAmount`, `totalAmount`
- optional: `paymentTermsDays`

### AR Invoice
- `arInvoiceId` (PK)
- `sourceInvoiceId`, `sourceEventId`
- `customerId`, `invoiceDate`, `dueDate`
- `totalAmount`, `amountDue`, `currency`
- `status`
- `journalEntryId` (FK)

### Journal Entry
- `journalEntryId` (PK)
- `transactionDate`
- `sourceEventId`
- `lines[]` (glAccountCode, debit/credit, amount)

## Acceptance Criteria
- **AC1 (Happy path):** Valid `InvoiceIssued` ($115 total = $100 subtotal + $15 tax) creates:
  - AR Invoice with `totalAmount=115`, `dueDate` computed from terms
  - Balanced JE: Dr AR 115; Cr Revenue 100; Cr Tax Payable 15
  - Both records persist `sourceInvoiceId` + `eventId`
- **AC2 (Idempotency):** Re-delivery with same `sourceInvoiceId` creates no new records.
- **AC3 (Missing customerId):** Event fails validation; no records; DLQ + alert.
- **AC4 (Terms resolution):**
  - Uses `event.paymentTermsDays` when present and > 0
  - Else falls back to customer default terms in Accounting
  - If neither available: fail with `MissingPaymentTerms` and DLQ
- **AC5 (Revenue account resolution):**
  - Uses line-level revenue account when present
  - Else uses allowed fallback(s)
  - If still unresolved: fail with `MissingRevenueGlAccountCode` and DLQ

## Audit & Observability
- Structured logs at each stage with `eventId` + `sourceInvoiceId`.
- Metrics: processed success, duplicate count, validation failures, DLQ count.
- Alerts on DLQ events.

## Open Questions
None.

---

## Original Story (Unmodified – For Traceability)
(See original content in the issue history prior to this rewrite.)