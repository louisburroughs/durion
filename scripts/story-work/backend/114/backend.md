Title: [BACKEND] [STORY] Accounting: Apply Payment to Invoice
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/114
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
As an Accounting System, I need to apply a cleared payment to one or more open invoices, so that Accounts Receivable (AR) is reduced correctly, invoice balances/statuses reflect the application, and any overpayment becomes an explicit AR customer credit.

## Actors & Stakeholders
- **Primary Actor:** Accounting System (automatic rules and/or clerk-initiated workflow)
- **Initiating User:** Accounting Clerk
- **Service Owner:** Accounting Service (AR sub-ledger + invoice balance lifecycle)
- **Upstream Domain:** Payment domain (owns payment authorization/capture/clearing)
- **Stakeholders:** Finance, Auditors

## Preconditions
- A payment has been **cleared/settled** in the Payment domain and is available for application.
- One or more invoices exist for the same customer in a payable state (e.g., `Open`, `PartiallyPaid`) with `balanceDue > 0`.
- Payment currency matches invoice currency.
- Caller is authenticated and authorized to apply payments.

## Functional Behavior

### Inter-Domain Contract (Payment → Accounting)
- **Decision:** Event-driven integration. The Payment domain publishes a `PaymentCleared` (or equivalent) event; Accounting consumes it.
- **Minimum required fields:** `paymentId`, `customerId`, `clearedAt`, `currency`, `amount` (plus recommended metadata like `eventId` / idempotency key).

### Payment Availability (Accounting internal state)
1. On `PaymentCleared`, Accounting creates/updates an internal “payment available to apply” record (e.g., `AccountingPayment` / `ReceivablePayment`) with:
   - `totalAmount`
   - `unappliedAmount = totalAmount`
   - status `AVAILABLE`

### Trigger (Apply payment)
A user or automated process submits an Accounting command to apply a payment across invoices, e.g.:
- `POST /accounting/payments/{paymentId}/applications`
- Request includes `applicationRequestId` (idempotency key) and a list of `{ invoiceId, amountToApply }`.

### Main Success Scenario
1. Accounting receives the apply request.
2. Validate payment is `AVAILABLE` and has sufficient remaining value.
3. Validate each target invoice is applicable (not `PaidInFull` / `Voided` / `Cancelled`).
4. Validate requested amounts:
   - Each `amountToApply` is `> 0` and `<= invoice.balanceDue`.
   - Sum of `amountToApply` does not exceed the payment’s available amount.
5. Create immutable `PaymentApplication` record(s) linking `paymentId` + `invoiceId` + `appliedAmount` + timestamps.
6. Update invoice derived state:
   - Decrease `invoice.balanceDue` by `appliedAmount`.
   - Set status to `PaidInFull` if balance becomes `0`, else `PartiallyPaid`.
7. Update payment available state:
   - Decrease `unappliedAmount` by total applied amount.
8. **Overpayment policy (Decision):** If payment value exceeds invoice application total, create an explicit AR credit balance:
   - Create `CustomerCredit` with `customerId`, `currency`, `amount = remaining`, `sourcePaymentId`, timestamps.
   - Represent remaining value consistently as a credit (recommended: set `unappliedAmount` to `0` after credit creation so the credit is the representation of remaining value).
9. Emit events for downstream consumers:
   - `PaymentAppliedToInvoice` (per invoice application)
   - `CustomerCreditCreated` (when overpayment yields credit)

## Alternate / Error Flows
- **Invoice not applicable:** Reject with `VALIDATION_ERROR:INVOICE_NOT_APPLICABLE`; no partial writes.
- **Insufficient funds:** Reject with `VALIDATION_ERROR:INSUFFICIENT_FUNDS`; no partial writes.
- **Currency mismatch:** Reject with `VALIDATION_ERROR:CURRENCY_MISMATCH`; no partial writes.

## Business Rules
- **Domain authority (Decision):** Accounting is the system of record for `PaymentApplication` lifecycle/state; Payment remains SoR for Payment lifecycle (auth/capture/settlement/refunds).
- One payment can be applied to multiple invoices; one invoice can have multiple payments.
- Application is **atomic** across all target invoices.
- Applications are **idempotent** via `applicationRequestId` (retries must not create duplicates).
- **Reversals (Decision):** No hard deletes. Reversal is a **compensating transaction** recorded as a reversal entity/event that offsets the original application and updates derived balances.

## Data Requirements
- `ReceivablePayment` (or equivalent): `paymentId`, `customerId`, `currency`, `totalAmount`, `unappliedAmount`, `status`, `clearedAt`, `sourceEventId`
- `PaymentApplication`: `paymentApplicationId`, `paymentId`, `invoiceId`, `customerId`, `currency`, `appliedAmount`, `applicationTimestamp`, `applicationRequestId`, `traceId`
- `CustomerCredit`: `creditId`, `customerId`, `currency`, `amount`, `sourcePaymentId`, `createdAt`, `traceId`
- `PaymentApplicationReversal`: `reversalId`, `originalPaymentApplicationId`, `reversedAt`, `reversedBy`, `reason`, `amount`

## Acceptance Criteria
- **Apply fully to one invoice:** invoice becomes `PaidInFull`, payment decreases appropriately, events emitted.
- **Apply partially to one invoice:** invoice becomes `PartiallyPaid`, payment decreases appropriately, events emitted.
- **Apply across multiple invoices:** all applications persist atomically; correct resulting balances/statuses.
- **Overpayment creates credit (Decision):** when payment exceeds applied total, `CustomerCredit` is created for remainder.
- **Reversal is compensating (Decision):** reversal creates a new record, does not delete original application, and restores derived balances as defined by policy.

## Audit & Observability
- Structured logs include `traceId`, `applicationRequestId`, `paymentId`, and all `invoiceId`s.
- Metrics:
  - `payment_applications_total{status=success|failure}`
  - `payment_application_latency_ms`
- Events emitted (names may be finalized as part of implementation):
  - `PaymentAppliedToInvoice`
  - `PaymentApplicationFailed`
  - `CustomerCreditCreated`
  - `PaymentApplicationReversed`

## Open Questions
- None. Decisions were supplied in the issue comments (see Decision Record dated 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #114 — [BACKEND] [STORY] Accounting: Apply Payment to Invoice

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Apply Payment to Invoice

**Domain**: payment

### Story Description

/kiro
Apply payments to invoices with clear AR reconciliation.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
User action or automated rule to apply a recorded payment to one or more invoices

## Main Flow
1. Select payment and target open invoice(s)
2. Validate invoice status and remaining balance
3. Apply payment amount to invoice(s)
4. Reduce Accounts Receivable balance accordingly
5. Update invoice payment status (partial/paid)
6. Persist application records

## Alternate / Error Flows
- Overpayment → create credit balance
- Invoice closed or voided → block application
- Partial application across multiple invoices

## Business Rules
- AR reduction occurs only when payment is applied
- One payment may apply to multiple invoices
- One invoice may have multiple payments
- Application must be reversible with audit

## Data Requirements
- Entities: PaymentApplication, Invoice, AR
- Fields: appliedAmount, invoiceId, applicationTimestamp

## Acceptance Criteria
- [ ] AR balance reduces correctly
- [ ] Invoice status updates accurately
- [ ] Partial payments are supported
- [ ] Application is auditable and reversible

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*