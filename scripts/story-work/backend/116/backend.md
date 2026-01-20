Title: [BACKEND] [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/116
Labels: backend, story-implementation, user, type:story, domain:accounting, status:ready-for-dev, risk:financial-inference

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
**As the** Accounting System,
**I need to** process `InvoiceAdjusted` and `CreditMemoIssued` events,
**so that** financial records for revenue, tax, and accounts receivable are accurately corrected in the general ledger, and the changes are fully auditable.

## Actors & Stakeholders
- **Primary Actor:** `Accounting System` (the automated service responsible for processing financial events).
- **Stakeholders:**
  - `Finance Team`: Relies on the accuracy of the ledger for financial reporting.
  - `Auditors`: Require a clear and immutable audit trail for all financial transactions.
  - `Upstream Systems`: Systems (e.g., Billing, POS) that generate the `InvoiceAdjusted` or `CreditMemoIssued` events.

## Preconditions
- An original, posted `Invoice` record exists in the system with its corresponding, balanced journal entries (debit to Accounts Receivable, credits to Revenue and Tax Liability).
- A defined and versioned event schema exists for `InvoiceAdjusted` and `CreditMemoIssued` events, as specified in the `Durion Accounting Event Contract v1`.
- The system has access to a configured Chart of Accounts to identify the correct GL accounts for Accounts Receivable, Revenue, and Tax.
- The event bus/messaging queue topic for these events is configured and the service has the necessary permissions to consume from it.

## Functional Behavior
### Trigger
The Accounting System consumes an `InvoiceAdjusted` or `CreditMemoIssued` event from the enterprise event bus.

### Main Success Scenario
1.  **Event Ingestion & Validation:** The system ingests the event and validates its payload against the `Durion Accounting Event Contract v1` schema. A unique correlation ID is assigned to the event for end-to-end tracing.
2.  **Original Transaction Lookup:** The system uses the `originalInvoiceId` from the event payload to locate the original, posted invoice and its associated journal entries.
3.  **Authorization & Duplication Check:** The system verifies that the adjustment is authorized and that the `eventId` has not been processed previously to prevent duplicate transactions.
4.  **Journal Entry Generation:**
    - The system generates a new set of balanced, double-entry journal entries to counteract or adjust the original financial postings.
    - For a `CreditMemoIssued` event, this typically involves crediting the Accounts Receivable account and debiting the relevant Revenue and Tax Liability accounts.
    - For an `InvoiceAdjusted` event, the entries will reflect the specific changes detailed in the event payload.
5.  **Ledger Record Creation:** A new, immutable ledger record (e.g., `CreditMemo`, `InvoiceAdjustment`) is created.
6.  **Traceability Linkage:** The new ledger record and its associated journal entries are explicitly linked to the `originalInvoiceId`.
7.  **Transaction Commit:** All new database records (Journal Entries, Credit Memo/Adjustment record) are committed within a single, atomic database transaction to ensure data integrity.

## Alternate / Error Flows
- **Invalid Event Schema:** If the event payload fails validation against the contract, the event is rejected, logged as a critical error, and moved to a Dead Letter Queue (DLQ) for manual review.
- **Original Invoice Not Found:** If the `originalInvoiceId` does not correspond to a posted invoice in the system, the event is rejected, a critical error is logged, and the message is moved to a DLQ.
- **Duplicate Event:** If an event with a previously processed `eventId` is received, the event is acknowledged and discarded idempotently, and a warning is logged.
- **Unbalanced Journal Entries:** If the generated journal entries are not balanced (Debits != Credits), the entire transaction is rolled back, a critical alert is triggered, and the event is moved to a DLQ.
- **Authorization Failure:** If the event fails the authorization check, it is rejected, a security/warning event is logged, and the message is moved to a DLQ.

## Business Rules
- All financial postings MUST adhere to double-entry bookkeeping principles. The sum of debits must equal the sum of credits for the transaction.
- Adjustments and credit memos can only be applied to invoices in a `Posted` state.
- All ledger entries are immutable. Corrections must be made by posting new, counteracting journal entries, never by updating or deleting existing ones.
- A permanent, auditable link MUST be maintained between a credit memo/adjustment and its original invoice.

## Data Requirements
### `CreditMemoIssued` / `InvoiceAdjusted` Event Payload
- `eventId`: (UUID) Unique identifier for the event.
- `eventTimestamp`: (ISO 8601) Timestamp of event creation.
- `sourceSystem`: (String) The system that originated the event (e.g., "BillingSystem").
- `originalInvoiceId`: (UUID) The identifier of the invoice being adjusted.
- `reason`: (String) Business reason for the adjustment.
- `adjustments`: (Array of Objects) Detailed breakdown of changes.
  - `lineItemId`: (UUID, Optional) Link to original invoice line item.
  - `glAccountId`: (String) The GL Account being adjusted (e.g., Revenue, Tax).
  - `adjustmentAmount`: (Decimal) The value of the adjustment.
- `totalAdjustmentAmount`: (Decimal) The total value of the adjustment.
- `totalTaxAdjustmentAmount`: (Decimal) The tax portion of the adjustment.

### `JournalEntry` Entity
- `journalEntryId`: (UUID) Primary key.
- `transactionId`: (UUID) Groups all entries for a single financial event.
- `glAccountId`: (String) Foreign key to the Chart of Accounts.
- `debitAmount`: (Decimal)
- `creditAmount`: (Decimal)
- `description`: (String) Narrative explaining the entry.
- `effectiveDate`: (Date)
- `sourceEventId`: (UUID) Link to the triggering event.

## Acceptance Criteria
**Scenario 1: Successful Processing of a Full Credit Memo**
- **Given** an invoice exists with a total of $110, posted as a $110 debit to Accounts Receivable, a $100 credit to Revenue, and a $10 credit to Sales Tax Payable.
- **When** a valid `CreditMemoIssued` event is received for the full amount of $110 against that invoice.
- **Then** the system must create a new transaction with balanced journal entries:
  - Credit Accounts Receivable for $110.
  - Debit Revenue for $100.
  - Debit Sales Tax Payable for $10.
- **And** a new `CreditMemo` record must be created and linked to the original invoice's ID.
- **And** the customer's AR balance for this invoice must now be $0.

**Scenario 2: Processing a Partial Downward Invoice Adjustment**
- **Given** an invoice exists with a total of $110 (AR Debit), comprising $100 in Revenue (Credit) and $10 in Tax (Credit).
- **When** a valid `InvoiceAdjusted` event is received that reduces the revenue by $20 and tax by $2.
- **Then** the system must create a new transaction with balanced journal entries:
  - Credit Accounts Receivable for $22.
  - Debit Revenue for $20.
  - Debit Sales Tax Payable for $2.
- **And** an `InvoiceAdjustment` record must be created, linked to the original invoice, detailing the changes.

**Scenario 3: Rejecting an Event for a Non-Existent Invoice**
- **Given** the system is listening for accounting events.
- **When** an `InvoiceAdjusted` event is received with an `originalInvoiceId` that does not exist in the ledger.
- **Then** the system must not create any journal entries.
- **And** the event must be moved to a Dead Letter Queue (DLQ).
- **And** a critical error must be logged containing the `eventId` and the non-existent `originalInvoiceId`.

## Audit & Observability
- **Logging:**
  - Log every consumed event payload with its `eventId` and assigned correlation ID upon receipt.
  - Log the successful creation of each financial transaction, including the new `transactionId` and the source `eventId`.
  - Log all validation failures (schema, business logic, authorization) as structured errors, including the `eventId` and reason for failure.
- **Auditing:**
  - An immutable audit trail must exist, allowing a user to trace from an `InvoiceAdjustment` or `CreditMemo` record back to its specific journal entries, the triggering event, and the original `Invoice`.
- **Alerting:**
  - Configure critical alerts for any event that results in an unhandled exception or is sent to the DLQ.
  - Configure alerts for any attempt to post an unbalanced journal entry transaction.

## Open Questions
1.  **Chart of Accounts Specifics:** What specific General Ledger (GL) account IDs from our Chart of Accounts should be used for debiting/crediting Accounts Receivable, different types of Revenue, and various Tax liabilities?
2.  **Authorization Mechanism:** The story requires "validating adjustment authorization." What is the authoritative source and mechanism for this? Is it a field in the event payload (e.g., `authorizedByUserId`), a system-level trust relationship, or a rules-engine check?
3.  **Event Type Distinction:** Are the business rules and journal entry patterns identical for `InvoiceAdjusted` vs. `CreditMemoIssued`? For example, is a credit memo always a reduction, while an adjustment could be an increase, decrease, or re-allocation of funds?
4.  **Upward Adjustments:** What is the expected behavior for an `InvoiceAdjusted` event that *increases* the total amount of an invoice? Does this follow the same flow but with reversed debit/credit signs, or is this an invalid/separate use case?

---
## Original Story (Unmodified – For Traceability)
# Issue #116 — [BACKEND] [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InvoiceAdjusted or CreditMemo Event

**Domain**: user

### Story Description

/kiro
Handle revenue, tax, and AR changes from invoice adjustments.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InvoiceAdjusted` or `CreditMemoIssued` event

## Main Flow
1. Validate adjustment authorization
2. Reverse or amend prior AR, revenue, and tax entries
3. Post adjusted values
4. Maintain linkage to original invoice

## Acceptance Criteria
- [ ] Adjustments reconcile prior postings
- [ ] Credit memos reduce AR and revenue correctly
- [ ] Full audit trail preserved

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