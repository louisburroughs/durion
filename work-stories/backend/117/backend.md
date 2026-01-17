Title: [BACKEND] [STORY] Accounting: Ingest InvoiceIssued Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/117
Labels: type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** accounting-strict
---

## Story Intent
As the Accounting System, I want to process issued invoices from the Work Execution domain so that revenue, tax liabilities, and accounts receivable are accurately recorded in the general ledger in a timely and idempotent manner.

## Actors & Stakeholders
- **System**: Accounting System (The system responsible for processing the event and maintaining the General Ledger).
- **Upstream System**: Work Execution System (The system that originates the `InvoiceIssued` event).
- **Stakeholders**: Finance Department, Auditors (Rely on the accuracy and traceability of financial records).

## Preconditions
1. The Accounting System is subscribed to and can consume messages from the `InvoiceIssued` event topic.
2. The General Ledger is configured with the necessary control accounts for Accounts Receivable, Revenue, and Tax Liabilities.
3. The event payload schema is defined and available as per the `Durion Accounting Event Contract v1`.
4. The system has access to a persistent store to track processed event/invoice identifiers for idempotency.

## Functional Behavior

### Trigger
The system receives an `InvoiceIssued` event from the enterprise message bus.

### Main Success Scenario
1. The system consumes the `InvoiceIssued` event.
2. An idempotency check is performed using a composite key of `invoiceId` and `invoiceVersion` from the event payload. The check confirms this is a new, unprocessed event.
3. The event payload is validated against the schema defined in the `Durion Accounting Event Contract v1`.
4. A single, atomic database transaction is initiated for all subsequent ledger postings.
5. A new Accounts Receivable (AR) entry is created, debiting the AR control account for the invoice's total amount.
6. For each revenue line item in the event, a corresponding credit entry is posted to the specified revenue account.
7. For each tax breakdown in the event, a corresponding credit entry is posted to the specified tax liability account for that jurisdiction.
8. The system verifies that the sum of all credits (revenue + tax) equals the total AR debit.
9. All ledger entries are persisted, and a reference to the source `invoiceId` and `eventId` is stored with the transaction.
10. The `invoiceId`/`invoiceVersion` combination is recorded in the processed events log to ensure future idempotency.
11. The transaction is committed.

## Alternate / Error Flows
- **Duplicate Event Received**: If the idempotency check finds the `invoiceId`/`invoiceVersion` has already been processed, the system will discard the event, log the duplicate occurrence, and take no further action. No financial entries will be created.
- **Invalid Event Payload**: If the event fails schema validation (e.g., missing required fields, malformed data), the system will reject the event, move the message to a Dead-Letter Queue (DLQ) for investigation, and generate a high-priority alert. No financial entries will be created.
- **Unknown GL Account**: If a revenue or tax account code specified in the event payload does not exist in the Chart of Accounts, the system will reject the event, move it to the DLQ, and generate a high-priority alert. The entire transaction will be rolled back.

## Business Rules
- **BR-ACC-01 (Revenue Recognition Trigger)**: Revenue is officially recognized only upon the successful processing of a valid `InvoiceIssued` event. This event is the legal and financial trigger.
- **BR-ACC-02 (Ledger Segregation)**: Revenue and tax liabilities must be posted to separate and distinct general ledger accounts as defined by the Chart of Accounts. They cannot be combined.
- **BR-ACC-03 (Idempotency Guarantee)**: Each unique invoice version (defined by `invoiceId` and `invoiceVersion`) must be processed exactly once. Subsequent attempts to process the same version must be ignored.
- **BR-ACC-04 (Transactional Integrity)**: All ledger entries (AR Debit, Revenue Credits, Tax Credits) for a single invoice must be created as a single, atomic financial transaction. A failure in posting any single entry must result in a complete rollback of all entries for that invoice.

## Data Requirements

### Incoming Event Data (`InvoiceIssued` v1)
The event payload must conform to the `Durion Accounting Event Contract v1` and contain, at minimum:
- `eventId`: Unique identifier for the event message.
- `eventTimestamp`: ISO 8601 timestamp of event creation.
- `invoiceId`: Unique identifier for the invoice.
- `invoiceVersion`: A version number or timestamp for the invoice, for handling revisions.
- `issueDate`: The legal date of invoice issuance.
- `customerId`: Identifier for the customer.
- `totalAmount`: The total value of the invoice.
- `revenueItems`: An array of objects, each containing `amount`, `description`, and `revenueAccountCode`.
- `taxItems`: An array of objects, each containing `amount`, `jurisdiction`, and `taxLiabilityAccountCode`.

### Internal Entities
- `LedgerTransaction`: Header record for the set of financial postings.
- `LedgerEntry`: Individual debit or credit line item in a transaction.
- `ProcessedEventLog`: A record of processed `invoiceId`/`invoiceVersion` combinations to enforce idempotency.

## Acceptance Criteria

### AC-1: Successful Invoice Ingestion
- **Given** the system receives a valid `InvoiceIssued` event that has not been processed before
- **When** the event is processed
- **Then** a new atomic ledger transaction is created and persisted
- **And** the Accounts Receivable account is debited by the invoice `totalAmount`
- **And** each revenue account from `revenueItems` is credited with the corresponding amount
- **And** each tax liability account from `taxItems` is credited with the corresponding amount
- **And** the sum of all credits equals the total AR debit
- **And** the event's `invoiceId` and `invoiceVersion` are logged as processed.

### AC-2: Idempotent Processing of Duplicate Event
- **Given** an `InvoiceIssued` event for `invoiceId: "INV-123"` and `invoiceVersion: 1` has already been successfully processed
- **When** the system receives another event with `invoiceId: "INV-123"` and `invoiceVersion: 1`
- **Then** the system identifies it as a duplicate and discards it
- **And** no new ledger entries are created in the General Ledger
- **And** a log entry is created indicating a duplicate event was received and ignored.

### AC-3: Rejection of Invalid Event
- **Given** the system receives an `InvoiceIssued` event with a missing `totalAmount` field
- **When** the system attempts to validate the event
- **Then** the event is rejected
- **And** the event message is moved to a Dead-Letter Queue (DLQ)
- **And** a high-priority alert is generated for operational review
- **And** no ledger entries are created.

## Audit & Observability
- All created ledger transactions and their constituent entries must be traceable back to the originating `eventId` and `invoiceId`.
- A structured log event must be emitted for every successfully processed invoice, containing the `invoiceId` and the corresponding `ledgerTransactionId`.
- A warning-level log event must be emitted for every detected and ignored duplicate event.
- An error-level log event and a metric counter must be triggered for any event that fails validation and is sent to the DLQ.
- The system should expose metrics for the number of invoices processed, the number of duplicates ignored, and the number of processing failures.

## Original Story (Unmodified – For Traceability)
# Issue #117 — [BACKEND] [STORY] Accounting: Ingest InvoiceIssued Event

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InvoiceIssued Event

**Domain**: user

### Story Description

/kiro
Post AR, revenue, and tax liabilities from issued invoices.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InvoiceIssued` event from Workorder Execution

## Main Flow
1. Validate invoice payload and idempotency
2. Create Accounts Receivable entry
3. Post revenue by classification
4. Post tax liabilities by jurisdiction
5. Persist posting references

## Business Rules
- Invoice is the legal revenue trigger
- Taxes must be posted separately from revenue
- Posting must be idempotent per invoice version

## Data Requirements
- Entities: Invoice, AR, RevenueAccount, TaxLiability
- Fields: invoiceId, totals, taxBreakdown

## Acceptance Criteria
- [ ] AR balance increases correctly
- [ ] Revenue posted to correct accounts
- [ ] Tax liabilities recorded accurately
- [ ] Duplicate events do not double-post

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