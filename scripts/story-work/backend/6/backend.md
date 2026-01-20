Title: [BACKEND] [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/6
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting, risk:financial-inference, layer:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:ready-for-implementation

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- risk:financial-inference

**Rewrite Variant:** accounting-strict

## Story Intent
As the POS accounting subsystem, ensure Invoice payment statuses are updated reliably from payment outcomes so customer balances, ledger postings, and downstream reporting remain correct and auditable.

## Actors & Stakeholders
- Accounting service (primary): owns invoice status, posting semantics, and accounting events.
- Payment Gateway / Payment Service: emits payment outcomes (success, partial, failed, chargeback).
- POS / Order Service: originates invoices and displays invoice status in UI.
- Customer: affected by invoice status and balance.
- Finance & Reconciliation team: consumers of accurate ledger postings and reports.

## Preconditions
- An `Invoice` exists with `invoiceId`, a current `status`, `totalAmountMinor`, and `outstandingAmountMinor`.
- Payment outcomes are produced by Payment Service and include `transactionId`, minor-unit `amountMinor`, currency, and optional `idempotencyKey`.
- Accounting service has an outbox for posting intents and an idempotent GL posting consumer.

## Functional Behavior
1. Payment Outcome Handling
   - Validate event authenticity and map gateway outcomes to canonical statuses: `Paid`, `PartiallyPaid`, `Unpaid`, `Failed`, `Chargeback`.
   - Apply `amountMinor` to `paidAmountMinor` and decrement `outstandingAmountMinor`.
   - Transition `invoiceStatus` deterministically based on resulting balances and configured rules.
   - Emit an immutable `InvoicePaymentRecorded` event and create an audit entry with transaction refs and before/after balances.
2. Idempotency
   - Enforce idempotency primarily on `transactionId`; if absent, use provided `idempotencyKey`.
   - Duplicate deliveries are detected and treated as no-ops.
3. Ledger Posting (Resolved: Asynchronous Outbox)
   - Invoice state updates and payment application are transactional and synchronous.
   - Ledger postings are recorded as posting intents in the outbox and emitted asynchronously; posting is eventually consistent and does not block invoice state transitions.
   - Posting consumer must be idempotent on `postingIntentId` and `transactionId`.
4. Retries, SLA & Escalation
   - Target SLA: ≤ 5 minutes from payment application to successful GL posting.
   - Retries: exponential backoff with `maxRetries = 10`.
   - On exhaustion: mark invoice with `postingError=true`, emit `InvoicePostingFailed` event, and create a reconciliation/escalation record with `correlationId`.
5. Chargeback Handling
   - On confirmed chargeback event: automatically create reversal ledger postings, set `invoiceStatus = Chargeback`, and record an immutable audit entry referencing the original transaction. No heuristic inference; only explicit chargeback events trigger reversal.
6. Multi-currency & Partial/Overpayments
   - All amounts are processed in invoice currency minor units; no FX conversion in this flow.
   - Partial payments create partial payment records; invoice remains `PartiallyPaid` until `outstandingAmountMinor = 0`.
   - Overpayments create a customer credit balance; invoice moves to `Paid` and credit is tracked separately.

## GL Account Mapping (Defaults & Config)
- Baseline mappings (configurable per business unit / chart of accounts):
  - Payment receipt: Debit `Cash / Undeposited Funds` (e.g., `1010`), Credit `Accounts Receivable` (e.g., `1200`).
  - Payment fees: Debit `Payment Processing Fees Expense` (e.g., `6105`), Credit `Cash / Clearing` (e.g., `1010`).
  - Chargebacks: Debit `Chargeback Expense` (e.g., `6110`), Credit `Cash / Clearing` (e.g., `1010`) and reverse AR reductions if previously posted.
  - Refunds/Reversals: Debit `Accounts Receivable` or `Refund Expense`, Credit `Cash / Clearing`.
- Mapping resolution: Accounting service resolves mappings via local config or `gl-mapping-service` if present.

## Data Requirements
- Invoice Entity:
  - `invoiceId` (PK)
  - `invoiceStatus` ENUM {Draft, Issued, PartiallyPaid, Paid, Failed, Chargeback}
  - `totalAmountMinor`, `paidAmountMinor`, `outstandingAmountMinor`
  - `currency`, `lastPaymentTransactionId`, `postingError` (boolean)
  - Audit timestamps and `updatedBy` fields
- PostingIntent (outbox) table:
  - `postingIntentId` (UUID PK), `invoiceId`, `transactionId`, `entries` (jsonb), `status` (Pending/Posted/Failed), `attempts`, `lastAttemptAt`, `createdAt`
- Idempotency table: map `transactionId`/`idempotencyKey` to applied effects
- Reconciliation/Issue table for manual interventions

## Acceptance Criteria
- Full payment applied
  - Given `outstandingAmountMinor = 50000` and a payment outcome of `amountMinor = 50000` with `transactionId = T123`
  - When applied
  - Then `paidAmountMinor = 50000`, `outstandingAmountMinor = 0`, `invoiceStatus = Paid`, `InvoicePaymentRecorded` event emitted, and posting intent created in outbox.

- Partial payment
  - Given `outstandingAmountMinor = 50000` and `amountMinor = 20000`
  - Then invoice becomes `PartiallyPaid`, `paidAmountMinor` increments, and posting intent is created for the applied portion.

- Duplicate delivery
  - Given the same `transactionId` is delivered twice
  - Then second delivery is a no-op and state remains unchanged.

- Posting failure handling
  - Given persistent posting failures after retries
  - Then `postingError=true`, `InvoicePostingFailed` emitted, and reconciliation record created.

## Audit & Observability
- Immutable audit records for each applied payment, including `transactionId`, `previousPaidMinor`, `newPaidMinor`, and `correlationId`.
- Metrics: `invoice.payments.processed`, `invoice.payments.duplicates`, `invoice.posting.failures`.
- Tracing: spans for payment ingestion → invoice update → outbox publish → posting consumer.
- Events: `InvoicePaymentRecorded`, `InvoicePostingCompleted`, `InvoicePostingFailed` exported to the event bus.

## Resolved Decisions (from clarification #408)
1. Posting timing & atomicity: Use asynchronous ledger postings with an outbox; invoice updates remain transactional and authoritative.
2. SLA & retries: Target GL posting SLA ≤ 5 minutes; exponential backoff with `maxRetries = 10`; on exhaustion emit `InvoicePostingFailed` and flag invoice for reconciliation.
3. GL mapping defaults: Provided baseline account mappings; mappings are configurable per BU/region.
4. Chargebacks: Automatic reversal + status transition on explicit chargeback events; create audit entries and reversal postings.

**Recommended next steps:**
- Remove `STOP: Clarification required before finalization` and `blocked:clarification`; set `status:ready-for-implementation` (done).
- Add GL posting matrix and example `InvoicePosting` event schema to implementation PR.

---

## Appendix A: GL Posting Matrix

| Scenario | Debit Account | Credit Account | Notes |
|---|---|---|---|
| Payment receipt (customer payment applied) | Cash / Undeposited Funds (1010) | Accounts Receivable (1200) | Post on payment application; posting intent via outbox |
| Payment processing fees | Payment Processing Fees Expense (6105) | Cash / Clearing (1010) | Post when fee notification received |
| Chargeback | Chargeback Expense (6110) | Cash / Clearing (1010) | Reverse prior AR reduction if already posted |
| Refund / Reversal | Accounts Receivable / Refund Expense | Cash / Clearing | Post on confirmed refund |
| Overpayment | Customer Credit (2xxx) | Cash / Clearing | Create credit balance entry; invoice moves to Paid |

## Appendix B: Sample `InvoicePosting` Event Schema

Schema (informal JSON Schema):

{
  "type": "object",
  "properties": {
    "eventId": {"type":"string"},
    "occurredAt": {"type":"string","format":"date-time"},
    "postingIntentId": {"type":"string"},
    "invoiceId": {"type":"string"},
    "transactionId": {"type":"string"},
    "currency": {"type":"string"},
    "entries": {"type":"array","items": {"type":"object","properties": {"accountCode":{"type":"string"},"debitMinor":{"type":"integer"},"creditMinor":{"type":"integer"},"description":{"type":"string"}}}}
  },
  "required": ["eventId","postingIntentId","invoiceId","entries"]
}

Example payload:

{
  "eventId": "uuidv7",
  "occurredAt": "2026-01-17T21:00:00Z",
  "postingIntentId": "uuidv7-post",
  "invoiceId": "INV-2026-0001",
  "transactionId": "T123",
  "currency": "USD",
  "entries": [
    {"accountCode":"1010","debitMinor":50000,"creditMinor":0,"description":"Cash receipt"},
    {"accountCode":"1200","debitMinor":0,"creditMinor":50000,"description":"AR reduction"}
  ],
  "correlationId": "cor-abc-123"
}

Notes: Posting consumers must validate that total debits == total credits and be idempotent on `postingIntentId`. On posting failure after retries, consumer must emit `InvoicePostingFailed` with `postingIntentId` and `correlationId`.

---

## Original Story (Unmodified – For Traceability)

**Original Story**: [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes

(Original story body preserved below for auditability.)

