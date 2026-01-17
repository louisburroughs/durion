Title: [BACKEND] [STORY] Accounting: Ingest InventoryAdjustment Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/120
Labels: type:story, domain:accounting, status:needs-review

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
As the Accounting System, I need to ingest `InventoryAdjusted` events from the Workorder Execution domain to create correcting journal entries. This ensures that the General Ledger (specifically Inventory Asset and Cost of Goods Sold accounts) accurately reflects the real-world state of inventory, maintaining financial integrity and providing a complete audit trail for all inventory adjustments.

## Actors & Stakeholders
- **Primary Actor:** Accounting System (the system responsible for processing the event and updating financial records).
- **Initiating System:** Workorder Execution System (the system that detects an inventory discrepancy and publishes the `InventoryAdjusted` event).
- **Stakeholders:**
  - **Accountant:** Relies on the accuracy of the generated journal entries for financial reporting and reconciliation.
  - **Financial Controller:** Oversees the integrity of the General Ledger and requires a clear audit trail for all adjustments.
  - **Auditor:** Requires traceable, verifiable records of all financial transactions, including corrections.

## Preconditions
- The `InventoryAdjusted` event contract is formally defined, versioned, and agreed upon by both the `domain:accounting` and `domain:workexec` teams.
- The Accounting System is subscribed to the appropriate message topic/queue where `InventoryAdjusted` events are published.
- The original inventory transaction (which is being adjusted) has a unique identifier that is present in the `InventoryAdjusted` event payload.
- The General Ledger accounts for "Inventory Asset" and "Cost of Goods Sold" are configured in the Accounting System.

## Functional Behavior
1.  The Accounting System's event listener consumes an `InventoryAdjusted` event.
2.  The system validates the event payload against the agreed-upon schema (e.g., presence of required fields, correct data types).
3.  The system uses the `sourceTransactionId` from the event to look up the original financial transaction (e.g., the journal entry for the initial sale or stock movement).
4.  The system calculates the financial impact of the adjustment based on the delta between `originalQuantity` and `adjustedQuantity`, using the item's valuation from the original transaction.
5.  A new, two-sided journal entry is created to reflect the adjustment. For example, a decrease in inventory would result in a credit to "Inventory Asset" and a debit to "COGS" (or a specific "Inventory Adjustment Expense" account).
6.  The new journal entry is permanently recorded and linked to both the original transaction and the triggering `InventoryAdjusted` event for full traceability. The description must include the `adjustmentReasonCode`.
7.  Upon successful processing, the event is acknowledged to prevent reprocessing.

## Alternate / Error Flows
- **Invalid Event Payload:** If the event fails schema validation, it is rejected, moved to a dead-letter queue (DLQ), an alert is generated, and processing for that event stops.
- **Original Transaction Not Found:** If the `sourceTransactionId` does not correspond to an existing transaction in the Accounting System, the event is rejected, moved to a DLQ, an alert is generated, and no journal entry is created.
- **Duplicate Event:** If an event with a previously processed `eventId` is received, the system identifies it as a duplicate, acknowledges the message without taking action, and logs the occurrence.
- **Prohibited Negative Inventory:** If the adjustment would cause the inventory asset account's value to become negative and this is prohibited by policy (see OQ1), the event is rejected, a business rule violation is logged, and no journal entry is created.

## Business Rules
- **BR-ACC-101 (Traceability):** All adjusting journal entries must contain immutable references to the source `eventId` and the `sourceTransactionId` of the transaction being corrected.
- **BR-ACC-102 (Reason Codes):** The `adjustmentReasonCode` provided in the event must be stored and included in the description field of the resulting journal entry.
- **BR-ACC-103 (Prohibition of Negative Asset Value):** An inventory adjustment must not result in a negative monetary value for the associated inventory asset account on the general ledger unless an explicit override flag is present in the event and the governing business policy allows it. (See O-1)
- **BR-ACC-104 (Idempotency):** Event processing must be idempotent. Processing the same `InventoryAdjusted` event multiple times must result in exactly one set of corresponding journal entries.

## Data Requirements
The `InventoryAdjusted` event payload MUST contain, at a minimum:
```json
{
  "eventId": "string (UUID)",
  "eventTimestamp": "string (ISO-8601)",
  "sourceTransactionId": "string (UUID)",
  "adjustmentId": "string (UUID)",
  "productId": "string",
  "locationId": "string",
  "originalQuantity": "number",
  "adjustedQuantity": "number",
  "adjustmentReasonCode": "string (enum: 'DAMAGED', 'THEFT', 'RECOUNT_CORRECTION', etc.)",
  "adjustingUserId": "string"
}
```

## Acceptance Criteria
### AC1: Successful Processing of a Standard Inventory Adjustment
- **Given** the Accounting System has a record of an original inventory transaction
- **And** a valid `InventoryAdjusted` event is received referencing that transaction
- **When** the event is processed
- **Then** a new, balanced journal entry is created that correctly adjusts the Inventory Asset and COGS accounts
- **And** the journal entry's description contains the `adjustmentReasonCode` from the event
- **And** the journal entry is linked to both the `eventId` and the `sourceTransactionId`.

### AC2: Rejection of an Adjustment Leading to Prohibited Negative Inventory
- **Given** the business policy prohibits negative inventory asset values
- **And** an `InventoryAdjusted` event is received that, if processed, would result in a negative value for an inventory asset account
- **When** the event is processed
- **Then** no journal entry is created
- **And** the event is moved to a dead-letter queue
- **And** an error is logged indicating a business rule violation.

### AC3: Rejection of an Event with a Missing Source Transaction
- **Given** an `InventoryAdjusted` event is received with a `sourceTransactionId` that does not exist in the Accounting System
- **When** the event is processed
- **Then** no journal entry is created
- **And** the event is moved to a dead-letter queue
- **And** an error is logged indicating the source transaction was not found.

### AC4: Idempotent Processing of Duplicate Events
- **Given** a specific `InventoryAdjusted` event has already been successfully processed
- **And** the same event (with the same `eventId`) is received again
- **When** the duplicate event is processed
- **Then** the system recognizes it as a duplicate and takes no further financial action
- **And** no new journal entries are created.

## Audit & Observability
- **Logging:**
  - Log the receipt of every `InventoryAdjusted` event with its `eventId`.
  - Log the outcome of processing (Success, Failure, Duplicate) for each event.
  - On failure, log the specific reason (e.g., "Invalid Schema", "Source Transaction Not Found", "Negative Inventory Prohibited").
- **Metrics:**
  - `events.inventory_adjusted.processed.count` (counter, tagged by outcome: success, failure, duplicate)
  - `events.inventory_adjusted.processing.duration` (timer)
- **Auditing:** The created journal entry must provide a clear and permanent audit trail linking back to the `adjustmentId` and `adjustingUserId` from the source system.

## Open Questions
- **OQ1 (Policy on Negative Inventory):** The rule "Negative inventory positions are prohibited unless explicitly allowed" requires clarification.
  - Under what specific business conditions (e.g., for specific item types, specific reason codes) is a negative inventory asset value permissible?
  - Should the `InventoryAdjusted` event contract be updated to include an `allowNegativeValueOverride: boolean` flag, to be set by an authorized user in the source system?
  - Who is the business stakeholder authorized to define this policy?

## Original Story (Unmodified – For Traceability)
# Issue #120 — [BACKEND] [STORY] Accounting: Ingest InventoryAdjustment Event

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryAdjustment Event

**Domain**: user

### Story Description

/kiro
Handle inventory corrections with full auditability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryAdjusted` event from Workorder Execution

## Main Flow
1. Validate adjustment reason and quantities
2. Reverse or adjust prior inventory/COGS entries
3. Apply corrected inventory quantities
4. Record adjustment journal with reason code

## Business Rules
- Adjustments must reference original issue
- Negative inventory positions are prohibited unless explicitly allowed

## Acceptance Criteria
- [ ] Adjustments reconcile inventory correctly
- [ ] Prior postings are traceable and reversible

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

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