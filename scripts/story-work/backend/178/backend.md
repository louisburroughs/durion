Title: [BACKEND] [STORY] Fulfillment: Issue/Consume Picked Items to Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/178
Labels: type:story, layer:functional, kiro, domain:inventory, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** inventory-flexible
---
## Story Intent
As an Inventory System, I need to process the consumption of picked parts against a specific workorder so that stock levels are accurately decremented and the cost of goods sold can be correctly attributed to the job.

## Actors & Stakeholders
- **Primary Actor:** `System` – The Inventory Service, which processes the consumption request. This action is typically initiated by a user in another system (e.g., a Parts Manager or Technician via a Work Execution UI).
- **Stakeholders:**
  - `Parts Manager`: Responsible for accurate inventory counts and fulfilling parts requests for workorders.
  - `Mechanic/Technician`: Consumes the parts to perform the work on the vehicle.
  - `Service Advisor`: Needs visibility into job progress, which includes parts consumption.
  - `Inventory Manager`: Responsible for the overall health and accuracy of the inventory system.
  - `Accounting`: Requires accurate data for job costing and tracking Cost of Goods Sold (COGS).

## Preconditions
- A `Workorder` exists in a state that permits parts consumption (e.g., `In Progress`).
- The inventory items intended for consumption have been successfully moved to a `Picked` (or `Staged`) status and are logically associated with the specific `Workorder`.
- The initiating user or system possesses the necessary permissions to execute inventory consumption transactions.

## Functional Behavior
### 4.1. Consume Picked Items
The system provides an endpoint to consume items from inventory that have been previously picked for a workorder.

- **Trigger:** An API request is received containing the `workorderId` and a list of `lineItemId` and `quantity` pairs to be consumed.
- **Process:**
  1. The system validates that the `Workorder` exists and is in a valid state for consumption.
  2. For each item in the request, the system verifies:
     - The item is currently in a `Picked` status.
     - The item is associated with the provided `workorderId`.
     - The requested `quantity` to consume does not exceed the `quantity` that was picked.
  3. The system transitions the status of the consumed items from `Picked` to `Consumed`.
  4. The `Quantity on Hand` for the corresponding SKU is decremented by the consumed quantity.
  5. For each consumed item, an immutable `InventoryLedger` transaction is created. This entry records the `transactionType` as `WORKORDER_CONSUMPTION`, the negative quantity change, the `workorderId`, the current item cost (COGS), a timestamp, and the user/system that initiated the action.
  6. The system emits a `WorkorderPartsConsumed` event containing the `workorderId` and details of the consumed items.
- **Outcome:** The inventory state is updated, a permanent ledger record is created, and downstream systems are notified of the consumption.

## Alternate / Error Flows
- **Workorder in Invalid State:** If the workorder is not in an active state (e.g., it is `Completed`, `On Hold`, or `Cancelled`), the system rejects the entire request with a `409 Conflict` error and a descriptive message.
- **Item Not in Picked State:** If an item is not in the `Picked` status for the given workorder, the system rejects the request with a `400 Bad Request` error detailing which item is invalid.
- **Insufficient Picked Quantity:** If the requested quantity to consume exceeds the quantity picked for an item, the system rejects the request with a `400 Bad Request` error.
- **Item Not Associated with Workorder:** If an item in the request was not picked for the specified `workorderId`, the system rejects the request with a `400 Bad Request` error.
- **Unauthorized Access:** If the initiator lacks permissions, the system rejects the request with a `403 Forbidden` error.

## Business Rules
- Inventory consumption is an immutable financial transaction. Once an item is consumed, it cannot be "un-consumed."
- To reverse a consumption, a separate, compensating "Return to Stock" transaction must be performed, which creates its own ledger entry.
- All consumption transactions must be tied to a valid `Workorder`. Consumption of general shop supplies not tied to a specific job is handled by a different transaction type.
- The cost recorded in the inventory ledger must be the item's cost at the exact time of the consumption transaction.

## Data Requirements
### 7.1. API Request: `POST /v1/inventory/consume`
```json
{
  "workorderId": "wo-12345",
  "items": [
    {
      "pickedItemId": "pick-abc-789",
      "sku": "SKU-OILFILTER-A",
      "quantity": 1
    },
    {
      "pickedItemId": "pick-def-456",
      "sku": "SKU-AIRFILTER-B",
      "quantity": 1
    }
  ]
}
```

### 7.2. Inventory Ledger Entry
- `transactionId`: Unique identifier for the ledger entry.
- `timestamp`: ISO 8601 timestamp of the transaction.
- `transactionType`: `WORKORDER_CONSUMPTION`.
- `sku`: The stock-keeping unit of the item.
- `quantityChange`: The change in quantity (e.g., `-1`).
- `newQuantityOnHand`: The resulting quantity on hand for the SKU.
- `context`: `{ "workorderId": "wo-12345", "userId": "parts-mgr-01" }`.
- `costAtTransaction`: The cost of the item at the time of consumption.

### 7.3. Event: `WorkorderPartsConsumed`
- `eventId`: Unique ID for the event.
- `timestamp`: Event creation timestamp.
- `workorderId`: The associated workorder.
- `consumedItems`: An array of objects detailing the SKU, quantity, and cost of each consumed item.

## Acceptance Criteria
**AC-1: Successful Consumption of Picked Items**
- **Given** a workorder `wo-123` is `In Progress`
- **And** item `SKU-A` has a quantity of 2 in `Picked` status for `wo-123`
- **And** the inventory `Quantity on Hand` for `SKU-A` is 10
- **When** a request is made to consume 2 units of `SKU-A` for `wo-123`
- **Then** the system returns a `200 OK` success response
- **And** the `Quantity on Hand` for `SKU-A` becomes 8
- **And** an `InventoryLedger` entry is created for `SKU-A` with a `quantityChange` of -2 and `transactionType` of `WORKORDER_CONSUMPTION`
- **And** a `WorkorderPartsConsumed` event is emitted.

**AC-2: Attempt to Consume Item Not Picked for the Workorder**
- **Given** a workorder `wo-123` is `In Progress`
- **And** item `SKU-B` has been picked for a different workorder, `wo-456`
- **When** a request is made to consume `SKU-B` for `wo-123`
- **Then** the system rejects the request with a `400 Bad Request` error
- **And** the response body indicates `SKU-B` is not associated with `wo-123`.

**AC-3: Attempt to Consume More Quantity Than Picked**
- **Given** a workorder `wo-123` is `In Progress`
- **And** item `SKU-C` has a quantity of 1 in `Picked` status for `wo-123`
- **When** a request is made to consume 2 units of `SKU-C` for `wo-123`
- **Then** the system rejects the request with a `400 Bad Request` error
- **And** the response body indicates the requested quantity exceeds the picked quantity.

**AC-4: Attempt to Consume Parts for a Completed Workorder**
- **Given** a workorder `wo-789` is in `Completed` status
- **And** item `SKU-D` was previously picked for it
- **When** a request is made to consume `SKU-D` for `wo-789`
- **Then** the system rejects the request with a `409 Conflict` error
- **And** the response body indicates the workorder is not in a valid state for consumption.

## Audit & Observability
- **Audit Trail:** Every consumption event must be recorded in an immutable audit log. The log must include the initiator (user/system), timestamp, `workorderId`, and details of all items consumed (SKU, quantity, cost).
- **Logging:** Structured logs (e.g., JSON) should be generated for the start and end of the transaction, including `workorderId` and a transaction correlation ID. Errors must be logged with a full stack trace and request context.
- **Metrics:**
  - `inventory.consumption.success`: Counter for successful consumption transactions.
  - `inventory.consumption.failure`: Counter for failed consumption transactions, tagged by error type.
  - `inventory.consumption.latency`: Histogram measuring the duration of the consumption API call.

## Open Questions
1. The original story body in issue #236 is not available. What are the specific, nuanced requirements, business rules, or edge cases from that original story? This rewrite is based on domain best practices for the given title.
2. What is the authoritative source for an item's cost (COGS) at the time of consumption? Is this value retrieved from a pricing or accounting service, or is it stored with the inventory record?
3. Should the entire consumption transaction be atomic? If a request contains five items and the third one fails validation, should the consumption of the first two items be rolled back? (Assumption: Yes, the entire operation should be atomic.)

## Original Story (Unmodified – For Traceability)
# Issue #178 — [BACKEND] [STORY] Fulfillment: Issue/Consume Picked Items to Workorder

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #236 - Fulfillment: Issue/Consume Picked Items to Workorder
**URL**: https://github.com/louisburroughs/durion/issues/236
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
*Generated by Missing Issues Audit System - 2025-12-26T17:38:00.563548597*