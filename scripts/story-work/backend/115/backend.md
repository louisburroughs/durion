Title: [BACKEND] [STORY] Accounting: Ingest PaymentReceived Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/115
Labels: payment, type:story, domain:accounting, status:ready-for-dev

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

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
As the Accounting System, I need to ingest and process `PaymentReceived` events to accurately record cash receipts, create corresponding unapplied payment records, and ensure data integrity through idempotency, so that financial records are timely, accurate, and auditable.

## Actors & Stakeholders
- **System Actor:** Accounting System
- **Triggering Actor:** Any external system capable of receiving payments (e.g., POS System, Payment Gateway, Bank Feed Processor).
- **Stakeholders:**
    - **Accountant:** Needs to reconcile cash accounts and apply payments to open invoices.
    - **Financial Controller:** Relies on accurate and timely cash flow reporting.

## Preconditions
1. The Accounting System is configured with an event bus subscription to the `PaymentReceived` topic.
2. The schema for the `PaymentReceived` event conforms to the `Durion Accounting Event Contract v1`.
3. General ledger accounts for `Cash` (or specific bank accounts) and `Unapplied Payments` are configured in the system.
4. The system has a mechanism to persist and query idempotency keys (`externalTxnId`) to prevent duplicate processing.

## Functional Behavior
Upon receiving a `PaymentReceived` event, the system will execute the following flow:

1.  **Consume & Validate:** The system ingests the event from the message queue.
2.  **Idempotency Check:** The system checks if the `externalTxnId` from the event has been processed before. If it has, the event is acknowledged and ignored (see Alternate Flows).
3.  **Schema Validation:** The system validates the event payload against the `Durion Accounting Event Contract v1`.
4.  **Entity Creation:** If validation and idempotency checks pass, the system creates a new `Payment` entity with a status of `UNAPPLIED`.
5.  **Customer Association:** The system attempts to associate the payment with an existing `Customer` record using the reference information provided in the event. If no customer can be identified, the `customerId` on the `Payment` entity will be left null (see Open Questions).
6.  **Journal Entry Generation:** The system generates and persists a double-entry journal transaction to reflect the cash receipt:
    -   **Debit:** The appropriate `Cash` or `Bank` asset account.
    -   **Credit:** The `Unapplied Payments` liability/suspense account.
7.  **Persistence:** The `Payment` entity, its associated `JournalEntry`, and the idempotency key are committed to the database in a single atomic transaction.
8.  **Acknowledgement:** The system sends a final acknowledgement to the message queue to confirm successful processing and prevent redelivery.

## Alternate / Error Flows
-   **Duplicate Event:** If the `externalTxnId` already exists, the system logs the duplicate attempt, acknowledges the message to remove it from the queue, and takes no further action.
-   **Invalid Schema:** If the event payload fails schema validation, the system rejects the message, sending it to a Dead-Letter Queue (DLQ) for manual inspection. An alert is triggered.
-   **Currency Mismatch:** If the event's `currency` is not supported or configured for the business entity, the event is rejected and moved to the DLQ.
-   **Database/Persistence Failure:** If the database transaction fails for any reason (e.g., connection loss, constraint violation), the entire operation is rolled back. The message is not acknowledged, allowing the message bus to attempt redelivery according to its configured retry policy. After exhausting retries, the message is moved to the DLQ.

## Business Rules
-   **Idempotency:** Processing is strictly idempotent based on the `externalTransactionId` field from the event source. A unique constraint must be enforced on this field in the database.
-   **Cash Recognition:** Cash is recognized immediately upon successful ingestion of the event. The debit to a cash/bank account reflects this.
-   **Accounts Receivable (AR):** Ingesting a payment does *not* automatically reduce a customer's AR balance. The payment is recorded as "unapplied cash" until a separate business process (manual or automated) applies it to one or more specific invoices.
-   **Immutability:** Once a `Payment` and its associated journal entry are recorded, they are considered immutable. Any corrections must be made through explicit reversing journal entries.

## Data Requirements
### `Payment` Entity
| Field                 | Type          | Constraints                                          | Description                                                               |
| --------------------- | ------------- | ---------------------------------------------------- | ------------------------------------------------------------------------- |
| `paymentId`           | UUID          | Primary Key, Not Null                                | Internal unique identifier for the payment record.                        |
| `customerId`          | UUID          | Foreign Key (nullable)                               | Reference to the customer, if identifiable.                               |
| `status`              | Enum          | Not Null, Default: `UNAPPLIED`                       | The current state of the payment (e.g., `UNAPPLIED`, `APPLIED`).          |
| `amount`              | Decimal(19,4) | Not Null, > 0                                        | The monetary value of the payment.                                        |
| `currency`            | Char(3)       | Not Null, ISO 4217                                   | The currency of the payment.                                              |
| `paymentMethod`       | Enum          | Not Null                                             | Method of payment (e.g., `CASH`, `CREDIT_CARD`, `BANK_TRANSFER`).         |
| `receivedTimestamp`   | Timestamp UTC | Not Null                                             | Timestamp when the payment was received by the source system.             |
| `externalTransactionId` | String(255)   | Not Null, Unique                                     | The unique identifier from the source system, used for idempotency.       |
| `sourceSystem`        | String(50)    | Not Null                                             | The name of the system that originated the event (e.g., 'POS_TERMINAL_A'). |
| `sourceEventPayload`  | JSONB         | Not Null                                             | The original, unmodified event payload for audit and traceability.        |

## Acceptance Criteria
**AC1: Successful Ingestion of a Unique Payment Event**
-   **Given** the Accounting System has not previously processed a payment with `externalTransactionId` "TXN-123"
-   **When** a valid `PaymentReceived` event is received with `externalTransactionId` "TXN-123" for $100.00 USD
-   **Then** a new `Payment` record is created with status `UNAPPLIED` and amount 100.00.
-   **And** a journal entry is created debiting the `Cash` account by 100.00 and crediting the `Unapplied Payments` account by 100.00.
-   **And** the `externalTransactionId` "TXN-123" is recorded to prevent duplicates.

**AC2: Idempotent Handling of a Duplicate Payment Event**
-   **Given** the Accounting System has already successfully processed a payment with `externalTransactionId` "TXN-123"
-   **And** the `Cash` account balance is $500.00
-   **When** another `PaymentReceived` event is received with the same `externalTransactionId` "TXN-123"
-   **Then** the system identifies it as a duplicate and discards the event.
-   **And** no new `Payment` or `JournalEntry` records are created.
-   **And** the `Cash` account balance remains $500.00.

**AC3: Handling a Payment with an Unidentifiable Customer**
-   **Given** the system receives a valid `PaymentReceived` event
-   **And** the customer reference information in the event does not match any existing customer in the system
-   **When** the event is processed
-   **Then** a new `Payment` record is created successfully with a `null` `customerId`.
-   **And** the payment is available in a system-wide "Unapplied and Unassigned Payments" work queue for manual association.

**AC4: Rejection of an Event with an Unsupported Currency**
-   **Given** the system is configured to only accept "USD" and "CAD"
-   **When** a `PaymentReceived` event is received with currency "EUR"
-   **Then** the system rejects the event processing.
-   **And** no `Payment` or `JournalEntry` is created.
-   **And** the event is routed to the Dead-Letter Queue (DLQ) for investigation.

## Audit & Observability
1.  **Structured Logging:** All stages of event processing (ingestion, validation, persistence, rejection) must be logged with a correlation ID that tracks the event from ingress to completion.
2.  **Metrics:** The system must emit metrics for:
    -   `payments.ingested.count` (tagged by status: `success`, `duplicate`, `failed`)
    -   `payments.ingested.amount` (tagged by currency)
    -   `payments.ingestion.latency` (histogram)
3.  **Alerting:** Alerts should be configured for:
    -   A significant increase in the rate of schema validation failures.
    -   Any message landing in the Dead-Letter Queue (DLQ).
    -   Sustained high processing latency.
4.  **Traceability:** The created `Payment` record and its corresponding `JournalEntry` must be directly and easily traceable back to the source event via the `externalTransactionId` and the stored `sourceEventPayload`.

## Open Questions
1.  **Currency Mismatch Policy:** The original story states a currency mismatch should be "rejected or flagged for review". The proposed safe default is to reject and DLQ. Is this the correct final policy? Or should an unapplied payment be created and placed in a special "currency review" state?
2.  **Unidentified Customer Handling:** The current design allows creating a `Payment` with a `null` `customerId`. Is this acceptable, or should these payments be assigned to a generic "Suspense Customer" record to simplify reporting and GL structure?

## Original Story (Unmodified – For Traceability)
# Issue #115 — [BACKEND] [STORY] Accounting: Ingest PaymentReceived Event

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest PaymentReceived Event

**Domain**: payment

### Story Description

/kiro
Focus on cash recognition, AR reduction, and idempotency.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `PaymentReceived` event from an external payment source
(e.g., POS terminal, bank feed, payment processor, manual entry)

## Main Flow
1. Receive payment event with amount, currency, method, and reference(s)
2. Validate event schema and idempotency key
3. Identify target customer and candidate open invoices
4. Record cash receipt in appropriate cash/bank account
5. Create unapplied payment record or proceed to invoice application
6. Persist payment with full source metadata

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Unknown customer or reference → create unapplied payment
- Currency mismatch → reject or flag for review
- Posting failure → retry or dead-letter

## Business Rules
- Payment receipt reduces cash suspense or increases cash immediately
- Payment does not reduce AR until applied to invoice(s)
- Idempotency is enforced per external transaction reference

## Data Requirements
- Entities: Payment, CashAccount, Customer
- Fields: amount, currency, method, receivedTimestamp, externalTxnId

## Acceptance Criteria
- [ ] Cash/bank balance increases correctly
- [ ] Payment is recorded exactly once
- [ ] Unapplied payments are visible and traceable
- [ ] Payment references external source transaction

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