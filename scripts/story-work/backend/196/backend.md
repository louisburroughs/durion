Title: [BACKEND] [STORY] Cost: Maintain Standard/Last/Average Cost with Audit
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/196
Labels: type:story, layer:functional, kiro, domain:inventory, status:needs-review

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:story-authoring

---

**Rewrite Variant:** integration-conservative

## Story Intent
**As a** system architect,
**I want** to establish a robust mechanism to maintain and audit three distinct cost types (Standard, Last, and Average) for each inventory item,
**so that** the business has accurate, traceable inventory valuation data for financial reporting, operational analysis, and auditing purposes.

## Actors & Stakeholders
- **System:** The primary actor, responsible for automatically updating Last and Average costs based on inventory events.
- **Inventory Manager:** A user responsible for viewing inventory costs and managing the Standard Cost.
- **Finance Manager:** A user with accounting oversight who may also adjust Standard Cost.
- **Accountant / Finance Team:** Key stakeholders who rely on accurate and auditable cost data for calculating Cost of Goods Sold (COGS), valuing inventory on the balance sheet, and performing financial analysis.
- **Auditor:** A stakeholder who requires a clear, immutable history of all changes to inventory costs for compliance and verification.

## Preconditions
- A canonical `Inventory Item` or `SKU` entity exists within the system and can be uniquely identified.
- System events that trigger cost changes, such as 'Purchase Order Received', are published and consumable by the Inventory service.
- User roles and permissions framework exists to gate access to manual cost adjustments.
- `InventoryManager` and `FinanceManager` roles are defined and assignable.

## Functional Behavior

### 1. Standard Cost Management
- The `Standard Cost` is a predetermined or planned cost for an item used for **reference, planning, and variance analysis** purposes.
- It is **NOT** used for financial valuation (COGS or balance sheet).
- It is only updated through an explicit, permissioned manual action by an authorized user (`InventoryManager` or `FinanceManager`).
- It does not change automatically based on purchasing or receiving activities.
- **Permission Required:** `inventory.cost.standard.update`
- **Audit Required:** Every manual change must include a `reasonCode` field explaining the adjustment.

### 2. Last Cost Update (Event-Driven)
- The `Last Cost` represents the most recent unit purchase price of an item.
- It is **informational only** and not used for financial valuation.
- **Trigger:** The system processes a 'Purchase Order Received' event for an inventory item.
- **Action:** The system automatically updates the `Last Cost` for the item to the unit cost specified in the received purchase order line.
- **Manual Updates:** Users are **prohibited** from manually editing `Last Cost`. This field is exclusively system-managed.

### 3. Average Cost Recalculation (Event-Driven)
- The `Average Cost` is a **Weighted Average Cost (WAC)** of all units of an item currently in stock.
- This is the **authoritative costing method** for:
  - Cost of Goods Sold (COGS)
  - Inventory valuation on the balance sheet
- **Trigger:** The system processes a 'Purchase Order Received' event for an inventory item.
- **Action:** The system recalculates the `Average Cost` using the following formula:
  ```
  NewAverageCost = ((OldQtyOnHand * OldAverageCost) + (ReceivedQty * ReceivedUnitCost)) / (OldQtyOnHand + ReceivedQty)
  ```
- **Manual Updates:** Users are **prohibited** from manually editing `Average Cost`. This field is exclusively system-calculated.
- **Authority:** The Inventory domain agent is responsible for defining and maintaining this calculation logic. Accounting domain must NOT re-derive or reinterpret this formula.

### 4. Cost Change Auditing
- Every change to `Standard Cost`, `Last Cost`, or `Average Cost` must generate an immutable audit log entry.
- The audit entry must capture:
  - Item identifier
  - Cost type that changed
  - Old value
  - New value
  - Timestamp
  - Source of change (e.g., `user_id` for manual updates, `purchase_order_id` for automatic updates)
  - User/system process that initiated it
  - For manual Standard Cost changes: a required `reasonCode` field

## Alternate / Error Flows
- **Error - Receiving Item with Invalid Cost:** If a 'Purchase Order Received' event contains a non-positive (`<= 0`) unit cost, the cost update transaction for that item must be rejected, and an error must be logged. The existing `Last Cost` and `Average Cost` should remain unchanged.
- **Error - Transaction Failure:** All cost updates (e.g., updating Last and Average cost simultaneously) and the creation of their corresponding audit log entries must occur within a single atomic transaction. If any part of the process fails, the entire transaction must be rolled back to maintain data integrity.
- **Flow - Manual Update of System-Managed Costs:** Users attempting to manually edit `Last Cost` or `Average Cost` fields must receive a validation error. These fields are exclusively managed by the system in response to events.
- **Flow - Manual Standard Cost Update Without Reason:** If a user attempts to update `Standard Cost` without providing a `reasonCode`, the system must reject the request with a validation error.

## Business Rules
- All monetary values for cost must be stored with a minimum precision of 4 decimal places.
- The weighted average cost calculation must be deterministic and follow the specified formula precisely.
- **Initial values for new items:** All three cost fields (`standardCost`, `lastCost`, `averageCost`) must be initialized to `null` (NOT zero) when a new inventory item is created. Zero implies a real cost and creates accounting ambiguity.
- **On first receipt:** `lastCost` and `averageCost` are set to the unit cost from the first receipt. `standardCost` remains `null` unless explicitly set.
- **Costing method hierarchy:**
  - **Primary (authoritative):** Weighted Average Cost (WAC) for COGS and financial valuation
  - **Reference only:** Standard Cost (for planning and variance analysis)
  - **Informational only:** Last Cost (recent purchase price)
- **FIFO/LIFO:** Not in scope. These methods are explicitly NOT assumed or supported.
- **Domain ownership:** Inventory domain is the system of record for all item costs. Accounting domain consumes but does not own or recalculate cost data.

## Data Requirements

### `InventoryItem` Entity (Proposed additions)
- `standardCost` (Decimal/Money, nullable, precision: 4 decimal places)
- `lastCost` (Decimal/Money, nullable, precision: 4 decimal places)
- `averageCost` (Decimal/Money, nullable, precision: 4 decimal places)

### `ItemCostAudit` Entity (New)
- `auditId` (UUID, Primary Key)
- `itemId` (UUID/String, Foreign Key to InventoryItem, indexed)
- `timestamp` (Timestamp, indexed)
- `costTypeChanged` (Enum: 'STANDARD', 'LAST', 'AVERAGE')
- `oldValue` (Decimal/Money, nullable)
- `newValue` (Decimal/Money, nullable)
- `changeSourceType` (Enum: 'MANUAL', 'PURCHASE_ORDER')
- `changeSourceId` (String, e.g., User ID or Purchase Order ID)
- `actor` (String, e.g., 'system' or 'user:john.doe')
- `reasonCode` (String, required for MANUAL changes to standardCost, nullable otherwise)

## Acceptance Criteria

**Scenario 1: Manually Updating Standard Cost with Reason**
- **Given** an inventory item with a `Standard Cost` of `10.00`.
- **And** an authorized Inventory Manager (with `inventory.cost.standard.update` permission) is logged in.
- **When** the manager updates the `Standard Cost` to `12.50` with reason code "SUPPLIER_PRICE_INCREASE".
- **Then** the item's `Standard Cost` in the database is `12.50`.
- **And** a new `ItemCostAudit` record is created showing the change from `10.00` to `12.50` for the 'STANDARD' cost type, with a 'MANUAL' source, and the provided reason code.

**Scenario 2: Manual Standard Cost Update Rejected Without Reason**
- **Given** an inventory item with a `Standard Cost` of `10.00`.
- **And** an authorized Inventory Manager is logged in.
- **When** the manager attempts to update the `Standard Cost` to `12.50` without providing a `reasonCode`.
- **Then** the update is rejected with a validation error.
- **And** the item's `Standard Cost` remains `10.00`.
- **And** no audit record is created.

**Scenario 3: Receiving a Purchase Order Updates Last and Average Cost**
- **Given** an inventory item with `QtyOnHand` of `100`, a `Last Cost` of `5.00`, and an `Average Cost` of `5.50`.
- **When** a 'Purchase Order Received' event is processed for `50` units of this item at a `ReceivedUnitCost` of `6.00`.
- **Then** the item's `Last Cost` is updated to `6.00`.
- **And** the item's `Average Cost` is recalculated to `5.6667` ( (100 * 5.50) + (50 * 6.00) ) / (100 + 50).
- **And** two new `ItemCostAudit` records are created: one for the `Last Cost` change and one for the `Average Cost` change, both linked to the Purchase Order ID with 'PURCHASE_ORDER' source type.

**Scenario 4: First Receipt Initializes Costs**
- **Given** a newly created inventory item with `standardCost`, `lastCost`, and `averageCost` all `null`.
- **And** this is the first receipt for the item.
- **When** a 'Purchase Order Received' event is processed for `20` units at a `ReceivedUnitCost` of `8.00`.
- **Then** the item's `Last Cost` is set to `8.00`.
- **And** the item's `Average Cost` is set to `8.00`.
- **And** the item's `Standard Cost` remains `null`.
- **And** audit records are created for both `Last Cost` and `Average Cost` changes (from `null` to `8.00`).

**Scenario 5: Receiving a Purchase Order with Zero Cost**
- **Given** an inventory item with a `Last Cost` of `5.00` and an `Average Cost` of `5.50`.
- **When** a 'Purchase Order Received' event is processed with a `ReceivedUnitCost` of `0.00`.
- **Then** the cost update transaction is rejected.
- **And** the item's `Last Cost` remains `5.00` and `Average Cost` remains `5.50`.
- **And** an error is logged detailing the rejection reason and the source Purchase Order.
- **And** no audit records are created.

**Scenario 6: Unauthorized User Cannot Manually Update Standard Cost**
- **Given** an inventory item with a `Standard Cost` of `10.00`.
- **And** a user without `inventory.cost.standard.update` permission is logged in.
- **When** the user attempts to update the `Standard Cost` to `12.50`.
- **Then** the update is rejected with an authorization error.
- **And** the item's `Standard Cost` remains `10.00`.
- **And** no audit record is created.

**Scenario 7: User Cannot Manually Update Average Cost**
- **Given** an inventory item with an `Average Cost` of `5.50`.
- **And** an authorized Inventory Manager is logged in.
- **When** the manager attempts to manually update the `Average Cost` to `6.00`.
- **Then** the update is rejected with a validation error stating that Average Cost is system-calculated only.
- **And** the item's `Average Cost` remains `5.50`.
- **And** no audit record is created.

**Scenario 8: Transaction Rollback on Audit Failure**
- **Given** an inventory item requires cost updates from a purchase order receipt.
- **When** the cost calculations succeed but the audit log write fails.
- **Then** the entire transaction is rolled back.
- **And** the item's costs remain unchanged.
- **And** an error is logged indicating the transaction failure.

## Audit & Observability
- **Audit Trail:** The `ItemCostAudit` table serves as the primary, non-repudiable log of all cost changes. It must be queryable by item ID, date range, cost type, and change source type.
- **Logging:** Structured logs should be emitted for:
  - Every successful cost update transaction (including all calculated values)
  - Detailed error logs for any failed transactions, including the event payload that caused the failure
  - Manual cost adjustment attempts (both successful and rejected)
- **Metrics:** The system should expose metrics for:
  - `inventory.cost.updates.count` (counter, tagged by cost_type: standard/last/average, outcome: success/failure, source: manual/purchase_order)
  - `inventory.cost.manual_adjustments.rejected.count` (counter, tagged by reason: no_reason_code/unauthorized/invalid_cost_type)
  - `inventory.cost.calculation.duration.ms` (timer for WAC calculations)
  - `inventory.cost.audit.write.failures.count` (counter for audit failures)

## Domain Clarification Resolution

All blocking domain conflicts have been resolved:

### 1. Domain Ownership
**Decision:** **Inventory domain** is the system of record for inventory item costs.
- Inventory service owns data tables and APIs for reading current/historical costs
- Accounting service treats cost as authoritative input and does not recalculate or override item cost
- This follows standard ERP patterns (Inventory owns cost; Accounting posts using it)

### 2. Authoritative Logic for Cost Calculation
**Decision:** **Inventory-domain-agent** is responsible for defining and implementing cost calculation logic, including Weighted Average Cost (WAC).
- Inventory domain calculates and maintains `averageCost`, updates `lastCost`, and manages `standardCost`
- Accounting domain uses provided costs for postings and must NOT re-derive or reinterpret cost formulas
- Any change to cost calculation logic requires explicit inventory-domain clarification and audit coverage

### 3. Primary Costing Method
**Decision:** **Weighted Average Cost (WAC)** is the default and authoritative costing method for:
- Cost of Goods Sold (COGS)
- Inventory valuation on the balance sheet

**Notes:**
- Standard Cost is for reference/planning/variance analysis, not authoritative for valuation
- Last Cost is informational, not used for financial valuation
- FIFO/LIFO are out of scope and not assumed
- Hybrid valuation is not permitted without explicit accounting approval

### 4. Permissions for Manual Cost Adjustments
**Decision:** Manual updates to `standardCost` only are permitted and restricted.

**Authorized Roles:**
- `InventoryManager`
- `FinanceManager` (accounting oversight)

**Rules:**
- ✅ `standardCost` → allowed with permission + audit + reasonCode
- ❌ `averageCost` → never allowed (system-calculated only)
- ❌ `lastCost` → never allowed (receipt-driven only)

### 5. Initial State for New Inventory Items
**Decision:** Costs are initialized to `null` (NOT zero) to avoid accounting ambiguity.

**On Item Creation (before any receipts):**
- `standardCost` = `null`
- `lastCost` = `null`
- `averageCost` = `null`

**On First Receipt:**
- `lastCost` → unit cost from receipt
- `averageCost` → equals `lastCost` (first receipt baseline)
- `standardCost` → remains `null` unless explicitly set

## Original Story (Unmodified – For Traceability)
# Issue #196 — [BACKEND] [STORY] Cost: Maintain Standard/Last/Average Cost with Audit

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #196 - Cost: Maintain Standard/Last/Average Cost with Audit
**URL**: https://github.com/louisburroughs/durion/issues/196
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for backend development
- Coordinate with corresponding frontend implementation if needed

### Technical Requirements
**Backend Implementation Requirements:**
- Use Spring Boot with Java 21
- Implement RESTful API endpoints following established patterns
- Include proper request/response validation
- Implement business logic with appropriate error handling
- Ensure database operations are transactional where needed
- Include comprehensive logging for debugging
- Follow security best practices for authentication/authorization


### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Backend agents: Focus on Spring Boot microservices, Java 21, REST APIs, PostgreSQL. Ensure API contracts align with frontend requirements.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `backend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:38:49.367996314*