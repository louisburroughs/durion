Title: [BACKEND] [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/130
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

**As a** system responsible for financial record-keeping,
**I want to** automatically create a Vendor Bill in a pending state upon receiving a notification of goods received or a vendor invoice,
**So that** Accounts Payable liabilities are recognized promptly and accurately, ensuring a clear audit trail from procurement to payment.

## Actors & Stakeholders

- **System (Accounting Service):** The primary actor responsible for consuming events and creating financial records.
- **AP Clerk (Accounts Payable):** The user who reviews, approves, and processes the created Vendor Bill for payment.
- **Inventory/Purchasing System:** The external system that is the source of the `GoodsReceivedEvent` or `VendorInvoiceReceivedEvent`.
- **Auditor:** A stakeholder who requires clear traceability from the physical event (receiving goods) to the financial records (Vendor Bill, General Ledger).

## Preconditions

- The system has access to a valid Chart of Accounts, including Accounts Payable, Inventory, and relevant Expense accounts.
- The `Vendor` and `Purchase Order` referenced in the incoming event exist and are in a valid state in the system.
- The event message bus/topic is configured, and the Accounting Service is subscribed to the relevant events.

## Functional Behavior

1.  **Event Consumption:** The Accounting Service shall listen for and consume either a `GoodsReceivedEvent` or a `VendorInvoiceReceivedEvent`.
2.  **Payload Validation:** Upon consumption, the system validates the event payload for required fields (e.g., `vendorId`, `purchaseOrderId`, `eventId`, line items).
3.  **Idempotency Check:** The system uses a unique identifier from the event (e.g., `eventId` or a composite key like `vendorInvoiceReference` + `vendorId`) to ensure the same event does not create a duplicate Vendor Bill. If a bill already exists for that identifier, the process stops and logs the duplicate attempt.
4.  **Vendor Bill Creation:** A new `VendorBill` entity is created in the system with an initial status (e.g., `Draft`, `Pending Approval`).
5.  **Data Mapping:** The `VendorBill` is populated with data from the event and the referenced Purchase Order, including vendor details, invoice reference, dates, and line items (product, quantity, price).
6.  **GL Impact Preview:** The system determines the correct General Ledger accounts based on the line items (e.g., Inventory Asset vs. Expense account) and stages the corresponding double-entry transaction (Debit Inventory/Expense, Credit Accounts Payable).
7.  **Traceability Linkage:** The new `VendorBill` record is explicitly linked to the source `eventId`, `purchaseOrderId`, and the resulting `JournalEntryId` once posted.

## Alternate / Error Flows

- **Duplicate Event:** If an event with a previously processed idempotency key is received, the system shall ignore the event, log a warning, and return a success confirmation to prevent retries.
- **Invalid References:** If the `vendorId` or `purchaseOrderId` in the event does not correspond to a valid record, the event is moved to a dead-letter queue (DLQ) for manual investigation, and an alert is raised.
- **Missing GL Account Configuration:** If the system cannot determine the correct debit-side GL account for a line item, the bill creation fails, the event is moved to a DLQ, and an alert is raised.
- **Data Validation Failure:** If the event payload is missing required fields, the event is rejected or moved to a DLQ, and an error is logged.

## Business Rules

- **Idempotency:** Bill creation must be idempotent based on a unique reference from the source event.
- **Default Due Date:** If an invoice due date is not provided, it should be calculated based on the vendor's default payment terms (e.g., Net 30 from the invoice date).
- **Three-Way Match Principle:** The creation logic must be designed to eventually support a three-way match (Purchase Order vs. Goods Receipt vs. Vendor Invoice), even if the initial trigger is a single event. The exact matching policy requires clarification.
- **Bill Status:** A newly created Vendor Bill must enter a non-payable state, such as `Draft` or `Pending Approval`, and cannot be paid until it is explicitly approved.

## Data Requirements

### VendorBill Entity
- `billId`: (UUID, PK)
- `vendorId`: (UUID, FK)
- `purchaseOrderId`: (UUID, FK)
- `sourceEventId`: (String, Indexed) - For traceability
- `vendorInvoiceReference`: (String) - Vendor's invoice number
- `status`: (Enum: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `PAID`, `VOID`)
- `billDate`: (Date)
- `dueDate`: (Date)
- `totalAmount`: (Decimal)
- `lineItems`: (Array of Objects)
    - `productId`: (UUID)
    - `description`: (String)
    - `quantity`: (Decimal)
    - `unitPrice`: (Decimal)
    - `lineTotal`: (Decimal)
    - `debitAccountId`: (UUID, FK to GL Account)
- `creditAccountId`: (UUID, FK to GL Account - typically the main AP account)

### Triggering Event (e.g., `VendorInvoiceReceivedEvent`)
- `eventId`: (UUID) - Idempotency key
- `vendorId`: (UUID)
- `purchaseOrderId`: (UUID)
- `invoiceReference`: (String)
- `invoiceDate`: (Date)
- `lineItems`: (Array of Objects)

## Acceptance Criteria

**AC-1: Successful Bill Creation from Goods Receipt**
- **Given** a valid `GoodsReceivedEvent` for an inventory item is published
- **And** no Vendor Bill exists for this `eventId`
- **When** the Accounting Service consumes the event
- **Then** a new Vendor Bill is created in a 'Pending Approval' state
- **And** the bill's line items and amounts match the event payload
- **And** the corresponding GL journal entry is staged, debiting an Inventory asset account and crediting Accounts Payable.

**AC-2: Idempotency Prevents Duplicate Bill Creation**
- **Given** a Vendor Bill has already been created from `eventId-123`
- **When** a second event with the identical `eventId-123` is consumed
- **Then** no new Vendor Bill is created
- **And** the system logs a 'Duplicate event ignored' warning.

**AC-3: Error Handling for Invalid Purchase Order**
- **Given** a `VendorInvoiceReceivedEvent` is published with a `purchaseOrderId` that does not exist
- **When** the Accounting Service consumes the event
- **Then** no Vendor Bill is created
- **And** an error is logged specifying "Invalid Purchase Order reference"
- **And** the event is routed to a dead-letter queue for manual review.

**AC-4: Correct GL Posting for Expense Item**
- **Given** a valid `VendorInvoiceReceivedEvent` for a non-inventory expense item (e.g., 'Consulting Services') is published
- **When** the Accounting Service consumes the event
- **Then** a new Vendor Bill is created
- **And** the corresponding GL journal entry is staged, debiting the appropriate Expense account (e.g., 'Professional Fees') and crediting Accounts Payable.

## Audit & Observability

- **Audit Log:** An immutable audit log must record the creation of every Vendor Bill, linking it to the source event ID, user/system creator, and timestamp.
- **Metrics:** The system should emit metrics for `vendor_bills_created`, `duplicate_events_ignored`, and `bill_creation_failed`.
- **Logging:** Structured logs must be generated at each key step: event received, validation result, idempotency check, bill created, error encountered.
- **Events:** Upon successful creation, the system should publish a `VendorBillCreated` event for downstream consumers (e.g., approval workflows, forecasting).

## Open Questions

1.  **Authoritative Trigger & Matching Policy:** The story mentions "VendorInvoiceReceived (or GoodsReceived)". Which is the primary trigger for bill creation?
    -   **Option A (Receipt Accrual):** `GoodsReceivedEvent` creates the bill, and a later `VendorInvoiceReceivedEvent` is matched against it. This recognizes liability immediately.
    -   **Option B (Invoice Trigger):** `VendorInvoiceReceivedEvent` creates the bill, which is then matched against a `GoodsReceivedEvent` (3-way match).
    -   **Decision Needed:** The business must define the authoritative accounting policy. This is the most critical blocker.
2.  **Initial Bill State:** What is the exact initial state of a newly created bill (e.g., `Draft`, `Pending Approval`)? This determines the immediate next step in the workflow (e.g., data entry completion vs. manager review).
3.  **Discrepancy Handling:** What is the automated process if the quantities or prices on the triggering event do not match the associated Purchase Order? Should the bill be created and flagged for review, or should the process halt and raise an exception for manual intervention?

---

## Original Story (Unmodified – For Traceability)
# Issue #130 — [BACKEND] [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event

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
AP: Create Vendor Bill from Purchasing/Receiving Event

## Acceptance Criteria
- [ ] VendorInvoiceReceived (or GoodsReceived) event creates an AP bill with PO/receipt refs
- [ ] GL postings: Dr Expense/Inventory, Cr AP (per rules)
- [ ] Traceability links bill ↔ event ↔ journal entry
- [ ] Idempotent by vendorInvoiceRef/eventId


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