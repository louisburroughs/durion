Title: [BACKEND] [STORY] AR: Apply Payment to Open Invoice(s)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/132
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting

## Story Intent
**As a** Back Office Accountant,
**I want to** apply a posted/settled customer payment to one or more open invoices,
**so that** the customer’s AR balance is settled correctly and accounting records remain accurate and auditable.

**Rewrite Variant:** integration-conservative

## Actors & Stakeholders
- **Back Office Accountant:** Initiates or reviews payment applications.
- **Accounting Service (`domain:accounting`):** System of record (SoR) for AR subledger application/allocation logic and invoice settlement state.
- **Payments Service (`domain:payments`):** System of record for payment capture/settlement lifecycle; triggers Accounting when a payment is posted/settled.
- **Billing Service (`domain:billing`):** Originates invoices; may consume invoice settlement status changes.
- **Auditor / Compliance:** Requires immutable and explainable allocation history.

## Preconditions
1. A payment exists and is **posted/settled** (not merely authorized) and is available for application.
2. The payment references a `customerId`.
3. One or more invoices for the customer are in `Open` or `Partially Paid` (or equivalent) state.
4. Actor is authorized to perform AR actions.

## Functional Behavior
### Trigger / Inputs
1. **Trigger:** Accounting receives a signal that a payment is available to apply.
   - Preferred: consume an event from Payments such as `payments.payment_posted` / `payments.payment_settled`.
2. **Input fields required (from Payments):**
   - `payment_id`, `customer_id`, `amount`, `currency`, `effective_at`
   - Optional: `payment_method`, `external_reference`
   - Optional: `allocation_hint[] = { invoice_id, amount }` if Payments supports user-directed allocations

### Application Flow (Accounting-owned)
1. Validate payment exists, is posted/settled, and has available unapplied amount.
2. Determine allocation list:
   - If `allocation_hint` present: validate invoice IDs belong to `customer_id` and amounts are valid.
   - Otherwise: apply by Accounting policy (exact policy can be configured later; the minimum requirement is deterministic behavior).
3. Apply amounts to invoices:
   - Reduce invoice open balance by applied amount.
   - Update invoice settlement state (`Open` → `Partially Paid` → `Paid`) based on remaining balance.
4. Persist an immutable application record linking `payment_id` to invoice allocations.
5. Produce the accounting-side financial postings (balanced journal entry) consistent with the posting model.
6. Ensure idempotency (replays do not double-apply) using `applicationEventId` / `eventId` and/or `payment_id` as a natural key.

## Alternate / Error Flows
- **Overpayment:** If payment amount exceeds eligible invoice balances:
  - Apply to invoices until none remain eligible
  - Record remaining amount as **Unapplied Cash** at the customer level (do not auto-create a Credit Memo)
- **Short-pay:** If payment is less than the invoice open balance:
  - Leave invoice as `Partially Paid`
  - Do not auto-write-off by default
- **Invalid invoice target:** If allocation references an invoice that is closed/void/not found/not tied to customer:
  - Reject the transaction with a clear validation error
- **Idempotent replay:** Duplicate application requests/events return success based on original outcome without reprocessing.

## Business Rules
1. **Domain ownership:** `domain:accounting` owns applying payments to invoices (AR subledger allocation, invoice settlement state, unapplied cash).
2. **Payments boundary:** `domain:payments` owns payment capture and settlement lifecycle; Accounting should only act on posted/settled payments.
3. **Overpayment default:** Excess funds are held as **Unapplied Cash**; Credit Memo creation is manual/explicit, not automatic.
4. **Short-pay default:** No automatic write-off; invoices remain `Partially Paid`.
5. **Balanced postings:** Financial postings must satisfy double-entry balance (Debits = Credits).

## Data Requirements
- **PaymentApplication**
  - `paymentId`
  - `applicationEventId` (idempotency)
  - `applicationDate` (use `effective_at` for accounting date)
  - `allocations[] = { invoiceId, appliedAmount }`
  - `unappliedAmount` (if any)
- **Invoice**
  - `openBalance` (or equivalent)
  - `settlementStatus` (`Open`/`Partially Paid`/`Paid`)

## Acceptance Criteria
- **Scenario 1: Apply payment across multiple invoices**
  - Given a posted payment for a customer and two open invoices
  - When Accounting applies the payment to both invoices
  - Then invoice balances are reduced correctly and statuses updated
  - And an immutable application record is persisted
  - And the operation is idempotent on replay

- **Scenario 2: Overpayment becomes Unapplied Cash**
  - Given a posted payment that exceeds all eligible invoice balances
  - When applied
  - Then remaining amount is recorded as Unapplied Cash (no auto Credit Memo)

- **Scenario 3: Short-pay leaves invoice Partially Paid**
  - Given a posted payment less than an invoice open balance
  - When applied
  - Then invoice remains Partially Paid with remaining balance due
  - And no automatic write-off is created

## Audit & Observability
- Record an immutable audit event for each payment application, including `paymentId`, `customerId`, invoice allocations, timestamps, and actor.
- Emit structured logs for success/failure with correlation IDs.
- Emit metrics for application success/failure, overpayment/unapplied creation, and idempotent replays.

## Resolved Decisions (from issue comments)
These decisions were applied from the resolution comment posted on 2026-01-14 ("Decision Doc — Issue #132", generated by `clarification-resolver.sh`):
1. **Owner:** `domain:accounting` owns applying payments to invoices; Payments triggers availability.
2. **Overpayment:** Default policy is Unapplied Cash (no auto Credit Memo).
3. **Short-pay:** Default policy is Partially Paid (no automatic de minimis write-off by default).
