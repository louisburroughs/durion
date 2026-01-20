Title: [BACKEND] [STORY] Accounting: Handle Refund Issued
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/113
Labels: backend, story-implementation, payment, type:story, domain:accounting, status:ready-for-dev, risk:financial-inference

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:financial-inference

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
As the Accounting System, I need to process `RefundIssued` events to accurately reverse or contra-account for the financial effects of original sales transactions. This ensures the General Ledger remains correct, provides full traceability from the refund back to the original payment, and complies with financial reporting standards.

## Actors & Stakeholders
- **Primary Actor:** `Accounting System` - The system responsible for processing financial events and maintaining the General Ledger.
- **Initiating Actor:** `Payment System` - The system that processes the refund with the payment provider and emits the `RefundIssued` event.
- **Stakeholders:**
    - `Finance Team` - Relies on accurate financial records for reporting and reconciliation.
    - `Auditors` - Require clear, traceable transaction histories for compliance verification.
    - `Customer Support Representatives` - Indirectly involved, as their actions to issue a refund trigger this downstream process.

## Preconditions
1.  The `Accounting System` is configured to subscribe to and consume `RefundIssued` events.
2.  The original payment transaction associated with the refund has been successfully processed and recorded in the General Ledger.
3.  The `RefundIssued` event payload conforms to the `Durion Accounting Event Contract v1`.
4.  A chart of accounts, including relevant asset (Cash/Bank), liability (Accounts Receivable), revenue, and contra-revenue (e.g., Sales Returns and Allowances) accounts, is defined in the system.

## Functional Behavior
When the `Accounting System` receives a `RefundIssued` event, it must perform the following actions:
1.  **Ingestion & Validation:**
    -   Consume the event from the message queue.
    -   Validate the event payload against its schema, ensuring all required fields (`originalTransactionId`, `refundAmount`, `reasonCode`, etc.) are present and valid.
    -   Verify that the `originalTransactionId` exists within the system and is in a refundable state.
2.  **Financial Calculation:**
    -   Retrieve the details of the original transaction.
    -   Confirm that the requested `refundAmount` does not exceed the remaining refundable balance of the original transaction.
3.  **Journal Entry Creation:**
    -   Based on the `reasonCode` and internal business rules, determine the correct accounts for the journal entry.
    -   For a standard refund, this typically involves:
        -   **Credit** an asset account (e.g., `Cash`, `Bank Account`) to reflect the cash outflow.
        -   **Debit** a revenue or contra-revenue account (e.g., `Sales Revenue`, `Sales Returns and Allowances`) to reverse the income.
        -   **Debit** a liability account like `Accounts Receivable` if the original sale was on credit.
4.  **Persistence:**
    -   Atomically save the new `Refund` transaction record and its associated `JournalEntry` to the database.
    -   The `Refund` record must maintain a direct, non-mutable link to the `originalTransactionId`.
5.  **Post-Processing:**
    -   Emit a `RefundProcessed` or `RefundProcessingFailed` event for downstream consumers (e.g., auditing, reporting).
    -   Update the status of the original transaction to reflect that a refund has been applied.

## Alternate / Error Flows
-   **Refund Exceeds Original Amount:** If the `refundAmount` is greater than the refundable balance of the original transaction, the event is rejected. A `RefundProcessingFailed` event is emitted with the reason `REFUND_EXCEEDS_ORIGINAL_AMOUNT`.
-   **Original Transaction Not Found:** If the `originalTransactionId` does not correspond to an existing transaction, the event is rejected. A `RefundProcessingFailed` event is emitted with the reason `ORIGINAL_TRANSACTION_NOT_FOUND`. The event is moved to a Dead Letter Queue (DLQ) for manual investigation.
-   **Duplicate Event Received:** If an event with an identical `eventId` or `refundId` is received, the system must recognize it as a duplicate and idempotently ignore it, ensuring no double-entry occurs.
-   **Invalid Reason Code:** If the `reasonCode` in the event is not a valid, recognized code, the event is rejected. A `RefundProcessingFailed` event is emitted with the reason `INVALID_REASON_CODE`.

## Business Rules
-   **Traceability Mandate:** Every refund transaction must be immutably linked to its original payment or invoice transaction.
-   **Authorization Requirement:** The `RefundIssued` event is considered proof of authorization; the Accounting System does not perform a separate authorization check but relies on the upstream `Payment System`.
-   **Reason Code Dictates Treatment:** The `reasonCode` provided in the event dictates the specific GL accounts used. For example, a "Goodwill" credit might debit a contra-revenue account, whereas a "Pricing Error" might directly reverse the original revenue account. (See Open Questions).
-   **Balance Constraint:** The cumulative amount of all refunds associated with an original transaction cannot exceed the total amount of that original transaction.

## Data Requirements
-   **`RefundIssued` Event (Input):**
    -   `eventId`: `UUID` (Unique identifier for the event)
    -   `refundId`: `UUID` (Unique identifier for the refund action)
    -   `originalTransactionId`: `UUID` (Reference to the original payment)
    -   `refundAmount`: `Decimal`
    -   `currency`: `String` (ISO 4217)
    -   `reasonCode`: `String` (Enum, e.g., `PRICING_ERROR`, `GOODWILL`, `RETURNED_GOODS`)
    -   `authorizerId`: `String` (Identifier for the user/system that approved the refund)
    -   `timestamp`: `ISO-8601 DateTime`
-   **`RefundTransaction` Entity (Output/Persistence):**
    -   `refundTransactionId`: `UUID` (Primary Key)
    -   `fk_originalTransactionId`: `UUID` (Foreign Key)
    -   `fk_journalEntryId`: `UUID` (Foreign Key to the GL entry)
    -   `amount`: `Decimal`
    -   `currency`: `String`
    -   `reasonCode`: `String`
    -   `status`: `String` (e.g., `COMPLETED`, `FAILED`)
    -   `createdAt`: `Timestamp`

## Acceptance Criteria
**Scenario 1: Full Refund for a Cash Sale**
-   **Given** a completed cash sale transaction of $100 exists for `TXN-123`, crediting `Sales Revenue` and debiting `Cash`.
-   **When** the Accounting System receives a `RefundIssued` event for $100 against `TXN-123` with reason `RETURNED_GOODS`.
-   **Then** the system must create a journal entry that credits `Cash` for $100 and debits `Sales Returns and Allowances` for $100.
-   **And** a `RefundTransaction` record is created linking the new journal entry to `TXN-123`.

**Scenario 2: Partial Refund for a Cash Sale**
-   **Given** a completed cash sale transaction of $100 exists for `TXN-456`.
-   **When** the Accounting System receives a `RefundIssued` event for $25 against `TXN-456` with reason `GOODWILL`.
-   **Then** the system must create a journal entry that credits `Cash` for $25 and debits the appropriate contra-revenue account for $25.
-   **And** the refundable balance for `TXN-456` is now $75.

**Scenario 3: Refund Amount Exceeds Original Transaction**
-   **Given** a completed sale transaction of $50 exists for `TXN-789`.
-   **When** the Accounting System receives a `RefundIssued` event for $50.01 against `TXN-789`.
-   **Then** the system must reject the transaction.
-   **And** it must emit a `RefundProcessingFailed` event with a reason of `REFUND_EXCEEDS_ORIGINAL_AMOUNT`.
-   **And** no journal entry or `RefundTransaction` record is created.

**Scenario 4: Refund for a Non-Existent Transaction**
-   **Given** no transaction exists with the ID `TXN-BOGUS`.
-   **When** the Accounting System receives a `RefundIssued` event for any amount against `TXN-BOGUS`.
-   **Then** the system must reject the transaction.
-   **And** it must emit a `RefundProcessingFailed` event with a reason of `ORIGINAL_TRANSACTION_NOT_FOUND`.
-   **And** the failed event must be routed to a DLQ for manual review.

## Audit & Observability
-   **Audit Trail:** A detailed, immutable audit trail must be created for each processed refund, linking the `RefundTransaction`, the `JournalEntry`, the original transaction, and the `authorizerId` from the event.
-   **Structured Logging:** Generate structured logs for key stages: `EventReceived`, `ValidationSuccess`, `ValidationFailure`, `JournalEntryCreated`, `TransactionCommitted`. Logs must include correlation IDs (`refundId`, `originalTransactionId`).
-   **Metrics:**
    -   `refunds.processed.count`: Counter, tagged by `reasonCode` and `currency`.
    -   `refunds.failed.count`: Counter, tagged by `failureReason`.
    -   `refunds.processed.amount`: Distribution summary, tagged by `currency`.

## Open Questions
1.  **Reason Code to GL Account Mapping:** The `Durion Accounting Event Contract v1` is referenced but unavailable. What are the specific `reasonCodes` for refunds, and what is the required accounting treatment (i.e., the specific General Ledger accounts to be debited and credited) for each one? For example:
    -   `RETURNED_GOODS`: Debit `Sales Returns and Allowances` (a contra-revenue account)?
    -   `PRICING_ERROR`: Debit `Sales Revenue` (a direct reversal)?
    -   `GOODWILL`: Debit `Goodwill Expense`?
2.  **Refunds on Credited Invoices:** What is the expected behavior if a refund is issued against a transaction/invoice that has already been fully or partially credited via a separate Credit Note? Should the event be rejected as a potential duplicate financial action, or is this a valid scenario?

---
## Original Story (Unmodified – For Traceability)
# Issue #113 — [BACKEND] [STORY] Accounting: Handle Refund Issued

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Handle Refund Issued

**Domain**: payment

### Story Description

/kiro
Reverse cash and revenue effects with full traceability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `RefundIssued` event or authorized refund action

## Main Flow
1. Validate refund authorization and reference
2. Identify original payment and/or invoice
3. Reduce cash/bank balance
4. Adjust AR and/or revenue as appropriate
5. Record refund transaction with reason code
6. Persist linkage to original payment/invoice

## Alternate / Error Flows
- Refund exceeds original payment → block
- Partial refund → supported
- Refund against already credited invoice

## Business Rules
- Refunds must reference an original transaction
- Revenue impact depends on refund reason (pricing error vs goodwill)
- Refunds require explicit authorization

## Data Requirements
- Entities: Refund, Payment, Invoice
- Fields: refundAmount, reasonCode, originalTxnRef

## Acceptance Criteria
- [ ] Cash/bank balance reduces correctly
- [ ] AR and/or revenue adjust appropriately
- [ ] Refund is traceable to original transaction
- [ ] Audit trail captures reason and authorizer

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