Title: [BACKEND] [STORY] Accounting: Ingest InventoryIssued Event
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/121
Labels: type:story, domain:accounting, status:ready-for-dev

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
**As an** Accounting System,
**I want to** process `InventoryIssued` events by creating corresponding double-entry journal postings,
**so that** inventory asset value is accurately decreased and the corresponding expense (Cost of Goods Sold or Work-in-Progress) is recognized in the general ledger at the time of use.

## Actors & Stakeholders
- **System Actors**:
    - `Accounting System`: The primary actor responsible for processing the event and maintaining the general ledger.
    - `Workorder Execution System`: The upstream system that is the source of the `InventoryIssued` event.
- **Human Stakeholders**:
    - `Accountant`: Relies on the accuracy and timeliness of these automated postings for financial reporting.
    - `Inventory Manager`: Is concerned with the accurate valuation and tracking of inventory assets.
    - `Auditor`: Requires clear traceability from financial statements back to source operations.

## Preconditions
- The `Accounting System` is successfully subscribed to the message topic/queue for `InventoryIssued` events.
- The company's Chart of Accounts, including specific GL accounts for Inventory Assets, Cost of Goods Sold (COGS), and Work-in-Progress (WIP), is configured in the system.
- The authoritative inventory valuation method (e.g., Average Cost, FIFO) is configured.
- Business rules for determining whether an inventory issue maps to a COGS vs. a WIP expense are defined and configured.

## Functional Behavior
### Trigger
The `Accounting System` receives an `InventoryIssued` event from the `Workorder Execution System`.

### Main Flow
1.  The system ingests the `InventoryIssued` event.
2.  The system validates the event payload against the `Durion Accounting Event Contract v1` schema.
3.  The system checks the event's idempotency key (`eventId`) against its record of processed events. If the key has already been processed, the flow terminates here (see Alternate Flow: Duplicate Event).
4.  The system retrieves the current valuation for the specified `inventoryItemId` using the configured valuation method (e.g., Average Cost).
5.  The system determines the target expense account (COGS or WIP) based on configured business rules (see Open Questions).
6.  The system constructs a double-entry journal transaction in a `pending` state:
    - **Credit:** The configured Inventory Asset GL account for the calculated value (`quantityIssued` × `unit_cost`).
    - **Debit:** The determined COGS or WIP GL account for the same value.
7.  The system persists the journal entry, linking it immutably to the source `eventId`, `workorderId`, and `inventoryIssueId` for full traceability.
8.  The system marks the `eventId` as successfully processed.
9.  The system acknowledges the event to the message bus.

## Alternate / Error Flows
- **Duplicate Event**: If the idempotency key (`eventId`) has already been processed, the system logs the detection of a duplicate, acknowledges the event to the message bus, and takes no further action.
- **Schema Validation Failure**: If the event payload fails schema validation, the system rejects the event, logs a structured error containing the payload and validation failures, and moves the message to a dead-letter queue (DLQ) for manual investigation.
- **Invalid Inventory Reference**: If the `inventoryItemId` from the event does not correspond to a known item in the accounting system's master data, the system logs a critical error, moves the event to the DLQ, and raises a high-priority alert.
- **GL Posting Failure**: If the journal entry fails to post to the General Ledger (e.g., due to a closed accounting period, inactive GL account), the system will initiate a configured retry policy. If all retries are exhausted, the event is moved to the DLQ and a high-priority alert is raised for manual intervention.

## Business Rules
- **Idempotency**: All event processing must be idempotent. A single `InventoryIssued` event, identified by its unique `eventId`, must result in exactly one corresponding journal entry.
- **Valuation**: The cost of issued inventory must be calculated using the system's configured authoritative inventory valuation method. This calculation must be logged and auditable.
- **Traceability**: Every resulting journal entry must be immutably traceable back to the unique `workorderId` and `inventoryIssueId` from the source event.
- **Balanced Transaction**: All generated journal entries must be balanced; total debits must equal total credits. The system must prevent the creation of unbalanced entries.

## Data Requirements
- **Incoming Event (`InventoryIssued` v1)**:
    - `eventId`: `UUID` - The unique identifier for the event, used as the idempotency key.
    - `eventTimestamp`: `ISO 8601 UTC` - The time the event occurred.
    - `inventoryItemId`: `String` - Authoritative identifier for the part/item.
    - `quantityIssued`: `Decimal` - The positive quantity of the item issued.
    - `unitOfMeasure`: `String` - The unit of measure for the quantity (e.g., `EACH`, `LITER`).
    - `workorderId`: `String` - Identifier for the source work order.
    - `inventoryIssueId`: `String` - Unique identifier for the specific issue transaction within the source system.
- **Internal Entities (`JournalEntry`, `JournalLine`)**:
    - `journalEntryId`: `UUID` - Primary key.
    - `transactionDate`: `Date` - The effective date of the accounting entry.
    - `description`: `String` - Human-readable description (e.g., "COGS for WO-12345, Part-X").
    - `sourceEventId`: `UUID` - The `eventId` from the source event.
    - `sourceSystem`: `String` - e.g., "WorkorderExecution".
    - `journalLines`: `Array<JournalLine>` - The set of debits and credits.
        - `glAccountId`: `String` - Identifier for the GL account being affected.
        - `debitAmount`: `Monetary`
        - `creditAmount`: `Monetary`

## Acceptance Criteria
- **AC1: Successful COGS Posting from Event**
    - **Given** the system is configured with the 'Average Cost' valuation method.
    - **And** the average cost of `Part-X` is $15.50.
    - **And** business rules are configured to post issues for 'Service' type work orders to the 'COGS' GL account.
    - **When** an `InventoryIssued` event is received for 2 units of `Part-X` against a 'Service' work order.
    - **Then** a new, balanced journal entry is created and persisted.
    - **And** the journal entry credits the 'Inventory Asset' GL account for $31.00.
    - **And** the journal entry debits the 'COGS' GL account for $31.00.
    - **And** the journal entry contains a reference to the source `eventId` and `workorderId`.

- **AC2: Idempotent Processing of Duplicate Event**
    - **Given** an `InventoryIssued` event with `eventId: "uuid-abc-123"` has already been successfully processed.
    - **And** this resulted in the creation of `journalEntryId: "je-xyz-789"`.
    - **When** a second event with the exact same `eventId: "uuid-abc-123"` is received.
    - **Then** the system acknowledges the event without creating a new journal entry.
    - **And** no changes are made to the balances of any GL accounts as a result of the second event.

- **AC3: Failed Processing of Invalid Event**
    - **Given** the system expects `quantityIssued` to be a positive decimal value.
    - **When** an `InventoryIssued` event is received where `quantityIssued` is `-5`.
    - **Then** the event fails schema validation and is not processed.
    - **And** the event message is moved to the dead-letter queue (DLQ).
    - **And** a structured error is logged detailing the validation failure.
    - **And** no journal entry is created.

## Audit & Observability
- **Audit Log**: An immutable audit trail must be created for every decision point, including:
    - Event received (with payload hash).
    - Idempotency check result (new or duplicate).
    - Valuation calculation performed (item, quantity, unit cost, total value).
    - Journal entry created (with `journalEntryId`).
- **Metrics**:
    - `events.ingested.count` (Counter, tagged by `event_type`, `source_system`)
    - `events.processed.success.count` (Counter)
    - `events.processed.duplicate.count` (Counter)
    - `events.processed.failure.count` (Counter, tagged by `failure_reason`: `schema`, `posting`, `unknown_item`)
    - `journal.entries.created.value` (Distribution Summary)
- **Alerting**: High-severity alerts must be triggered for:
    - Any event moved to the DLQ.
    - A sustained failure to connect to the General Ledger for posting.
    - A significant spike in the `events.processed.failure.count` metric.

## Open Questions
1.  **Valuation Method**: Where is the inventory valuation method (e.g., FIFO, Average Cost, Standard Cost) configured and maintained? Is this a global system setting, or can it vary by location, item category, or individual item?
2.  **COGS vs. WIP Logic**: What specific business logic or event attribute determines whether to post the expense to a Cost of Goods Sold (COGS) account versus a Work-in-Progress (WIP) account? (e.g., determined by work order type, item type, customer type, etc.)
3.  **GL Account Mapping**: How are the specific General Ledger (GL) account identifiers for `Inventory Asset`, `COGS`, and `WIP` determined? Are they hard-coded, configured globally, or mapped based on attributes like item category or location?

## Original Story (Unmodified – For Traceability)
# Issue #121 — [BACKEND] [STORY] Accounting: Ingest InventoryIssued Event

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryIssued Event

**Domain**: user

### Story Description

/kiro
Focus on inventory valuation, COGS timing, and idempotent posting.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryIssued` event from Workorder Execution

## Main Flow
1. Receive inventory issue event with part, quantity, and workorder reference
2. Validate event schema and idempotency key
3. Determine valuation method (configured, e.g., FIFO/average)
4. Reduce on-hand inventory quantity
5. Record corresponding COGS or WIP entry based on configuration
6. Persist posting references and source links

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Invalid inventory reference → reject and flag
- Posting failure → retry or dead-letter

## Business Rules
- Inventory may only be reduced once per issued quantity
- Valuation method is configuration-driven
- Posting must be traceable to source workorder and part issue

## Data Requirements
- Entities: InventoryItem, InventoryTransaction, WorkorderRef
- Fields: quantity, valuationAmount, issueTimestamp

## Acceptance Criteria
- [ ] Inventory quantity is reduced correctly
- [ ] COGS/WIP is recorded per configuration
- [ ] Event is idempotent
- [ ] Posting references original workorder

## Classification (confirm labels)
- Type: Story
- Layer: Functional
- Domain: Accounting

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