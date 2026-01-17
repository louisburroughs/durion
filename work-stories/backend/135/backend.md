Title: [BACKEND] [STORY] GL: Support Accrual vs Cash Basis Modes
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/135
Labels: type:story, domain:accounting, status:ready-for-dev

## Story Intent
**As a** Finance Manager or Accountant,
**I want to** configure the accounting basis (Accrual or Cash) for a business unit,
**so that** financial transactions are recorded in the General Ledger according to the correct accounting principles, ensuring compliant and accurate financial reporting.

## Actors & Stakeholders
- **Finance Manager / Accountant (User):** Configures the accounting basis for a business unit.
- **Accounting Service (System of Record):** Stores the accounting basis policy and generates/publishes journal entries.
- **Billing Service (Producer):** Emits invoice-related business events (e.g., `InvoiceIssued`).
- **Payments Service (Producer):** Emits payment-related business events (e.g., `PaymentReceived`, `PaymentApplied`, refunds/reversals).
- **Auditor:** Reviews accounting-basis changes and posting outcomes.

## Preconditions
- A Business Unit exists.
- Chart of Accounts is configured with required accounts for AR revenue cycle (e.g., Cash/Bank, Accounts Receivable, Revenue).
- User has permission to administer accounting policy (e.g., `ACCOUNTING_ADMIN`).
- Fiscal calendar exists (at minimum, the concept of a fiscal period boundary; recommended: fiscal month).

## Functional Behavior

### 1) Configure Accounting Basis (per Business Unit)
- A user with `ACCOUNTING_ADMIN` can set a business unit accounting basis to `ACCRUAL` or `CASH`.
- Default for a new business unit is `ACCRUAL`.
- The system records an immutable audit log entry for any change.

### 2) Posting Rules — Scope
- This story applies to **AR / revenue-cycle GL posting behavior only**.
- Accounts Payable (AP) treatment is explicitly out of scope.

### 3) Posting Rules — Accrual Basis (AR)
When basis is `ACCRUAL`:
- **On `InvoiceIssued`**: create a journal entry to recognize revenue and AR (per configured accounts).
- **On payment events**: payment should clear/settle AR (and should not create additional revenue recognition postings).

### 4) Posting Rules — Cash Basis (AR)
When basis is `CASH`:
- **No GL entry** on `InvoiceIssued`.
- Recognize revenue on **payment events**:
  - **On `PaymentReceived` / payment capture**: post revenue recognition at time of payment.
  - **Partial payments**: recognize revenue proportional to cash received.
  - **Refund / chargeback / reversal**: reverse the cash-basis revenue posting for the refunded/reversed amount.
  - **Payment void / failed capture after previously marked paid**: reverse any previously created cash-basis entries tied to that payment.
- No GL entries should be created for unpaid invoice cancellation/void.

### 5) Basis Change Policy (Resolved)
- Basis changes are **only permitted effective at the start of a fiscal period** (recommended: fiscal month). No mid-period changes.
- A basis change must specify an **effective period start date/time**.
- Transactions are posted according to the basis effective at the posting date:
  - Accrual: invoice posting date
  - Cash: payment date

### 6) Boundary / Transition Rules (Resolved)
- **No retroactive re-posting** of already-posted GL entries.
- **No automatic catch-up conversion** for in-flight items at the boundary.
- Implementation must prevent double-recognition across the boundary.
  - Example: invoice issued under accrual (posted at issuance), then later paid after switching to cash → payment must not create revenue again.

## Alternate / Error Flows
- **Unauthorized Basis Change**: reject with `403 Forbidden` and log.
- **Invalid Basis Value**: reject with `400 Bad Request`.
- **Invalid Effective Date**: reject if not aligned to fiscal period boundary (e.g., not at fiscal month start).
- **Closed/Locked Period**: reject basis change if effective period is closed/locked (if period close is implemented).
- **Journal Post Failure**: route source event to DLQ and trigger alert.

## Business Rules
- Exactly one active basis per business unit (`ACCRUAL` or `CASH`).
- Default basis is `ACCRUAL`.
- Basis changes are permission-controlled and audited.
- Basis changes must align to fiscal period boundaries; no mid-period changes.
- Scope is AR only for this story; AP basis behavior requires a separate story.

## Data Requirements
- **BusinessUnit**
  - `accountingBasis`: enum `ACCRUAL|CASH` (non-null, default `ACCRUAL`).

- **BusinessUnitAccountingBasisPolicy (recommended for history + effective dating)**
  - `businessUnitId`
  - `basis`: enum `ACCRUAL|CASH`
  - `effectiveFrom`: timestamp (must align to fiscal period start)
  - `changedByUserId`
  - `changedAt`: timestamp

- **Audit Log**
  - Immutable record of basis changes (old/new, who/when, effectiveFrom).

## Acceptance Criteria

**AC1: Configure Accounting Basis Successfully**
- **Given** I have `ACCOUNTING_ADMIN`
- **And** a business unit is currently `ACCRUAL`
- **When** I submit a change to `CASH` with an effective date aligned to fiscal period start
- **Then** the basis policy is updated
- **And** an audit log entry is created

**AC2: Accrual Basis — Invoice then Payment**
- **Given** basis is `ACCRUAL`
- **When** `InvoiceIssued` is received for $100
- **Then** a journal entry is created to recognize revenue and AR
- **And when** a payment event is received for that invoice
- **Then** the journal posting clears/settles AR and does not recognize revenue again

**AC3: Cash Basis — Invoice then Payment**
- **Given** basis is `CASH`
- **When** `InvoiceIssued` is received for $100
- **Then** no journal entry is created
- **And when** `PaymentReceived` is received for $100
- **Then** a journal entry is created to recognize revenue and cash/bank receipt

**AC4: Cash Basis — Partial Payment**
- **Given** basis is `CASH`
- **When** `PaymentReceived` is received for $40 against a $100 invoice
- **Then** revenue is recognized for $40 (proportional to cash received)

**AC5: Cash Basis — Refund/Reversal**
- **Given** basis is `CASH`
- **And** a prior `PaymentReceived` of $100 created cash-basis revenue postings
- **When** a refund/chargeback/reversal event is received for $25
- **Then** the system creates reversing entries for $25 against the original cash-basis posting

**AC6: Basis Change Constraints**
- **Given** I have `ACCOUNTING_ADMIN`
- **When** I attempt to change basis with an effective date not at fiscal period start
- **Then** the request is rejected
- **And** no basis policy change occurs

## Audit & Observability
- Emit `AccountingBasisChanged` on successful change including: `businessUnitId`, `oldBasis`, `newBasis`, `effectiveFrom`, `changedByUserId`, `timestamp`.
- Logs:
  - `INFO` for successful journal posts (include journal entry id + source event id).
  - `WARN` for unauthorized change attempts.
  - `ERROR` for event processing/posting failures (include event id + error).
- Metrics:
  - `journal_entries.posted.count` tagged by `basis` and `status`.

## Open Questions
None.

---

## Original Story (Unmodified – For Traceability)
# Issue #135 — [BACKEND] [STORY] GL: Support Accrual vs Cash Basis Modes

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] GL: Support Accrual vs Cash Basis Modes

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Support Accrual vs Cash Basis Modes

## Acceptance Criteria
- [ ] Business unit can be configured as accrual or cash basis
- [ ] Accrual: invoices post AR/AP and payments clear AR/AP
- [ ] Cash basis behavior is policy-defined and consistent across posting/reporting
- [ ] Basis changes are audited and permission-controlled


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