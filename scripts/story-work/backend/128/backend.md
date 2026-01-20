Title: [BACKEND] [STORY] AP: Execute Payment and Post to GL
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/128
Labels: type:story, domain:billing, status:ready-for-dev, agent:story-authoring, agent:accounting, agent:billing

## Story Intent
**As a** Billing/AP system,
**I want to** execute a vendor payment and allocate it across one or more bills,
**so that** bills are settled correctly, and accounting can post the corresponding journal entries to the GL.

## Actors & Stakeholders
- **Billing/AP system (primary):** Orchestrates payment execution and allocation.
- **Payment gateway / payment-adapter (external):** Executes the funds transfer.
- **Accounting service (downstream):** Posts immutable journal entries and owns posting rules / chart-of-accounts mapping.
- **AP Clerk / Manager:** Reviews and reconciles exceptions.
- **Auditors:** Require traceability from gateway transaction → allocations → journal entry.

## Preconditions
- One or more payable bills exist and are eligible for payment.
- A valid payment method/instrument is available.
- Billing can call the payment gateway (or adapter).
- Accounting has configuration for journal posting rules and any needed accounts (e.g., AP liability, cash/bank, fee expense, clearing).

## Functional Behavior
### 1) Execute Payment (Billing-owned orchestration)
1. **Trigger:** Billing receives a request to pay a vendor for a specified amount, optionally with explicit allocation instructions.
2. **Idempotency:** Billing generates/accepts a unique idempotency key (`paymentRef` / `paymentId`) and MUST prevent duplicate execution.
3. **Gateway call:** Billing calls the payment gateway/adapter to execute the payment.
4. **On gateway success:**
   - Persist payment success details (gateway transaction identifiers, timestamps, captured amounts).
   - Allocate the payment across bills (explicit allocation if provided; otherwise deterministic automatic allocation).
   - Update bill settlement status appropriately (paid/partially paid) based on allocation.
5. **Emit event (outbox):** In the same DB transaction that records `gateway_succeeded` + allocations, write an outbox event representing payment success.

### 2) Post to GL (Accounting-owned, asynchronous)
1. **Trigger:** Accounting consumes the billing payment-success event.
2. **Idempotency:** Accounting MUST be idempotent using a stable `(source_type=PAYMENT, source_id=billingPaymentId)` uniqueness rule.
3. **Posting:** Accounting posts the journal entry using its posting rules and COA mapping.
4. **Outcome:** Accounting emits an acknowledgement event (or otherwise signals) containing the created `journalEntryId` / posting reference.

### 3) Completion semantics (two truths)
- **Payment success truth:** based on payment gateway confirmation.
- **Accounting completion truth:** based on successful GL posting.

Suggested billing-owned state model:
- `INITIATED` → `GATEWAY_PENDING` → (`GATEWAY_FAILED` | `GATEWAY_SUCCEEDED`) → `GL_POST_PENDING` → (`GL_POSTED` | `GL_POST_FAILED`)

## Alternate / Error Flows
- **Gateway failure:** Record failure; do not allocate bills; do not emit payment-success for GL posting.
- **GL posting failure after gateway success:**
  - Billing remains in `GL_POST_PENDING` (or moves to `GL_POST_FAILED` after retry exhaustion).
  - Retry GL posting asynchronously with backoff; alert for manual remediation after N failed attempts.
  - No automatic refund solely due to GL posting failure.
- **Duplicate request (idempotency):** If a request is re-sent with the same `paymentRef/paymentId`, do not call the gateway; return the original outcome.

## Business Rules
- **BR-1 (Primary ownership):** `domain:billing` owns payment orchestration, allocations, idempotency, and payment workflow state.
- **BR-2 (Accounting responsibility):** `domain:accounting` owns journal entry creation/posting rules and COA mapping; it does not call the gateway.
- **BR-3 (Event-driven boundary):** GL posting is asynchronous/event-driven with eventual consistency; billing must guarantee eventual delivery via outbox.
- **BR-4 (Allocation policy):**
  - If caller provides explicit allocations, validate: non-negative amounts; sum ≤ payment amount; bills are payable/open.
  - If not provided, allocate deterministically: oldest due date first (nulls last), then invoice date, then bill ID.
  - Partial allocations are allowed.
- **BR-5 (Unapplied remainder):** If payment amount exceeds allocatable open balance, record remainder as **Unapplied Cash / Vendor Credit** (do not silently discard or auto-refund).
- **BR-6 (Fees):** Gateway fees are accounted for as a separate expense line in the journal entry. Billing must include `gross_amount` and (when known) `fee_amount` / `net_amount` in the event payload.

## Data Requirements
### Billing payment + allocations
- `billingPaymentId` (UUID)
- `paymentRef` (string/UUID; unique idempotency key)
- `vendorId` (UUID)
- `grossAmount` (money)
- `feeAmount` (money, optional if known at capture time)
- `netAmount` (money, optional)
- `currency`
- `gatewayTransactionId` (string)
- `status` (billing workflow state)
- Allocation line items: `(billingPaymentId, billId, appliedAmount)`

### Inter-domain contract
**Billing → Accounting event** (versioned): `Billing.PaymentSucceeded.v1` (name may be aligned to repo convention)
Minimum payload includes:
- `billingPaymentId` (used as `source_id`)
- `paymentRef`
- `vendorId`
- `grossAmount`, `feeAmount` (if known), `netAmount` (if known), `currency`
- Allocation breakdown per bill
- Gateway transaction identifiers and timestamps

**Accounting → Billing acknowledgement event** (optional but recommended): `Accounting.JournalEntryPosted.v1`
- `source_type=PAYMENT`, `source_id=billingPaymentId`, `journalEntryId`, `postedAt`

## Acceptance Criteria
- **AC-1: Gateway success creates allocations and GL-post request**
  - Given payable bills exist for a vendor
  - When billing executes a payment and the gateway confirms success
  - Then billing persists the payment, allocates amounts to bills, updates bill settlement state, and enqueues/publishes a payment-success event via outbox

- **AC-2: Idempotency prevents duplicate payment execution**
  - Given a payment request with `paymentRef` already processed
  - When the request is retried
  - Then billing does not call the gateway again and returns the original result

- **AC-3: GL posting is eventual and idempotent**
  - Given accounting receives the payment-success event
  - When it posts the journal entry
  - Then it creates exactly one journal entry for `(source_type=PAYMENT, source_id=billingPaymentId)` and acknowledges posting

- **AC-4: GL post failure is recoverable**
  - Given gateway success but accounting posting fails
  - When retries are attempted
  - Then billing remains in a GL-pending/failed state and alerts for manual remediation after retry exhaustion

## Audit & Observability
- Audit every state transition for payments and for GL posting acknowledgements.
- Structured logs for `PaymentInitiated`, `GatewaySucceeded/Failed`, `AllocationApplied`, `OutboxPublished`, `JournalEntryPosted/Failed`.
- Metrics: gateway success/failure counts, GL post latency, GL post failure counts, retry counts, and unapplied remainder totals.

## Open Questions
None. (Resolved in decision comment generated by `clarification-resolver.sh` on 2026-01-14.)

## Original Story (Unmodified – For Traceability)
# Issue #128 — [BACKEND] [STORY] AP: Execute Payment and Post to GL

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] AP: Execute Payment and Post to GL

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Execute Payment and Post to GL

## Acceptance Criteria
- [ ] Payment allocates across one or more bills (full/partial)
- [ ] GL postings: Dr AP, Cr Cash/Bank (per rules)
- [ ] Fees/unallocated amounts handled per policy
- [ ] Idempotent by paymentRef/eventId


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