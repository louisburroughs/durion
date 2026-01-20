Title: [BACKEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/34
Labels: backend, story-implementation, user, type:story, domain:inventory, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** inventory-flexible
---

## Story Intent

**As a** Receiver,
**I want to** process and record the receipt of items against a purchase order or ASN into a default staging location,
**so that** the system's on-hand inventory is accurately increased and the items are made available for the subsequent put-away process.

## Actors & Stakeholders

- **Receiver** (Primary Actor): The warehouse team member responsible for physically receiving and counting incoming stock.
- **System** (System Actor): The POS/WMS platform responsible for processing transactions and maintaining the inventory ledger.
- **Warehouse Manager** (Stakeholder): Responsible for overall inventory accuracy and operational efficiency.
- **Work Execution System** (Downstream System): A system that may consume availability updates or receipt information to schedule tasks like put-away.

## Preconditions

- A Purchase Order (PO) or Advance Shipping Notice (ASN) exists in the system with a status of 'Open' or 'In-Transit'.
- The items expected on the PO/ASN are defined in the product catalog.
- A default "Staging" location is configured in the system for the receiving warehouse.
- The Receiver is authenticated and has the necessary permissions to perform receiving functions.

## Functional Behavior

1.  The Receiver initiates a new receiving session, referencing a specific PO or ASN number.
2.  The system retrieves and displays the expected items, units of measure (UOM), and quantities for the referenced order.
3.  For each item, the Receiver physically counts the delivered goods and enters the actual quantity received into the system.
4.  The Receiver confirms the counts and finalizes the receiving session.
5.  Upon finalization, the system executes the following atomic operations:
    -   Creates immutable `InventoryLedgerEntry` records for each item received.
    -   Increases the on-hand quantity for each item in the designated staging location.
    -   Calculates any variance (overage or shortage) between expected and received quantities.
    -   Creates `InventoryVariance` records for any discrepancies.
    -   Updates the status of the corresponding PO/ASN lines to reflect the receipt (e.g., 'Partially Received', 'Received').

## Alternate / Error Flows

- **PO / ASN Not Found**: If the Receiver enters an identifier that does not correspond to an open order, the system displays a "Not Found" error and prevents proceeding.
- **Transaction Failure**: If the system fails to create ledger entries or update on-hand quantities (e.g., database error), the entire transaction is rolled back. The user is notified of the failure, and no inventory state is changed.
- **Item Not on PO**: Receiving an item not listed on the original PO is considered out of scope for this story and will be handled by a separate "Unplanned Receipt" process.

## Business Rules

- All received items must be placed into the default 'Staging' location for the facility.
- **Variance Handling**: The system must accept and record quantities that are over or under the expected amount.
    -   **Shortage (Under-Receipt)**: The received quantity is recorded, and a variance record is created for the deficit.
    -   **Overage (Over-Receipt)**: The received quantity is recorded, and a variance record is created for the surplus. The business policy for accepting or rejecting overages is managed outside of this transaction.
- Each inventory movement must generate a corresponding, immutable ledger entry.

## Data Requirements

- **`InventoryLedgerEntry`**: An immutable record of an inventory movement.
    -   `TransactionType`: "RECEIVE"
    -   `ItemID`: Foreign key to the item being moved.
    -   `LocationID`: Foreign key to the 'Staging' location.
    -   `QuantityChange`: The positive quantity of items received.
    -   `UnitOfMeasure`: The UOM for the quantity (e.g., EACH, CASE).
    -   `CorrelationID`: Identifier linking to the source receiving session/document.
    -   `Timestamp`: UTC timestamp of the transaction.

- **`InventoryVariance`**: A record of a discrepancy found during a process.
    -   `ItemID`: Foreign key to the item.
    -   `ExpectedQuantity`: The quantity on the PO/ASN line.
    -   `ActualQuantity`: The quantity physically received.
    -   `VarianceType`: "SHORTAGE" or "OVERAGE".
    -   `CorrelationID`: Identifier linking to the source receiving session/document.

- **`ReceivingLine`**:
    -   `State` must be updated (e.g., from 'EXPECTED' to 'RECEIVED', 'RECEIVED_SHORT', or 'RECEIVED_OVER').

- **`Inventory` / `LocationStock`**:
    -   `OnHandQuantity` must be incremented by the received amount for the specific item in the staging location.

## Acceptance Criteria

### AC1: Successful Receipt of Exact Quantity
- **Given** a Purchase Order expects 10 units of "ITEM-A".
- **When** the Receiver receives and confirms a quantity of 10 units of "ITEM-A" into the staging location.
- **Then** the system creates an `InventoryLedgerEntry` for +10 units of "ITEM-A" in the staging location.
- **And** the on-hand quantity of "ITEM-A" in the staging location is increased by 10.
- **And** the PO line for "ITEM-A" is marked as 'Received'.
- **And** no `InventoryVariance` record is created.

### AC2: Handling a Short-Receipt (Underage)
- **Given** a Purchase Order expects 10 units of "ITEM-A".
- **When** the Receiver receives and confirms a quantity of 8 units of "ITEM-A" into the staging location.
- **Then** the system creates an `InventoryLedgerEntry` for +8 units of "ITEM-A" in the staging location.
- **And** the on-hand quantity of "ITEM-A" in the staging location is increased by 8.
- **And** an `InventoryVariance` record is created for "ITEM-A" indicating a "SHORTAGE" of 2 units.
- **And** the PO line for "ITEM-A" is marked as 'Received-Short'.

### AC3: Handling an Over-Receipt (Overage)
- **Given** a Purchase Order expects 10 units of "ITEM-A".
- **When** the Receiver receives and confirms a quantity of 12 units of "ITEM-A" into the staging location.
- **Then** the system creates an `InventoryLedgerEntry` for +12 units of "ITEM-A" in the staging location.
- **And** the on-hand quantity of "ITEM-A" in the staging location is increased by 12.
- **And** an `InventoryVariance` record is created for "ITEM-A" indicating an "OVERAGE" of 2 units.
- **And** the PO line for "ITEM-A" is marked as 'Received-Over'.

## Audit & Observability

- **Audit Trail**: Every confirmed receipt must generate an immutable audit log entry containing the user, timestamp, PO number, items, and quantities involved.
- **Events**: Upon successful completion of a receiving session, the system must publish a `ReceiptCompleted` event. This event should contain sufficient data for downstream systems (like Work Execution) to be notified of new stock availability in staging.
- **Metrics**: The system should expose metrics for the number of items received, and the number and type of variances recorded.

## Open Questions

- None at this time.

## Original Story (Unmodified – For Traceability)
# Issue #34 — [BACKEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Receiver**, I want to receive items into staging so that on-hand increases and put-away can follow.

## Details
- Default to staging location.
- Record qty and UOM; handle over/short.

## Acceptance Criteria
- Ledger entries created for Receive.
- Items visible as 'in staging'.
- Variances recorded.

## Integrations
- Availability updates; workexec may see expected receipts.

## Data / Entities
- InventoryLedgerEntry(Receive), StagingLocation, ReceivingLineState

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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