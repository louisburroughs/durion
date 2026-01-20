Title: [BACKEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/177
Labels: type:story, layer:functional, kiro, domain:inventory, status:ready-for-dev

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

**Rewrite Variant:** inventory-flexible
## Story Intent
**As a** Warehouse Manager or Service Advisor,
**I want to** return unused, saleable items from a completed work order back into stock, providing a reason for the return,
**so that** our inventory records are accurate, items are available for future orders, and we can track why parts are being returned.

## Actors & Stakeholders
- **Warehouse Manager**: The primary actor responsible for managing stock levels and processing returns.
- **Service Advisor**: A secondary actor who may also process returns associated with work orders they manage.
- **System**: The POS/Inventory management system responsible for processing the transaction.
- **Inventory Agent**: The domain agent responsible for the business logic and state management of inventory.
- **Accounting Department (Stakeholder)**: Relies on accurate inventory data for financial reporting and asset valuation.

## Preconditions
1.  The user is authenticated and has the necessary permissions (e.g., `inventory:return:create`).
2.  The work order (`workOrderId`) exists and is in a `Completed` or `Closed` state, making it eligible for returns.
3.  The items being returned were originally allocated to the specified work order.
4.  The system has a pre-configured, non-empty list of valid return reason codes.

## Functional Behavior
1.  **Trigger**: The user initiates the "Return Items to Stock" process, selecting a completed work order.
2.  The system presents a list of all items and quantities that were consumed by that work order.
3.  The user selects one or more items to return and specifies the quantity for each. The return quantity cannot exceed the quantity consumed by the work order.
4.  For each selected item, the user must choose a reason for the return from a predefined dropdown list (e.g., 'Not Needed', 'Wrong Part Ordered', 'Customer Refused').
5.  The user confirms the return transaction.
6.  **Outcome**: The system executes the following actions atomically:
    a. Creates a permanent `InventoryReturn` record linked to the original `WorkOrder`.
    b. For each item in the return, it creates an `InventoryLedger` entry of type `RETURN_TO_STOCK`.
    c. Increments the `quantityOnHand` for the corresponding item SKU at the designated stock location.
    d. Emits an `Inventory.ItemReturnedToStock` event for downstream consumers (e.g., Accounting).
7.  The system displays a success confirmation to the user, including a transaction ID for the return.

## Alternate / Error Flows
- **Error Flow 1: Attempt to return more items than consumed**
    - **Trigger**: User enters a return quantity for an item that is greater than the quantity consumed on the work order.
    - **Outcome**: The system rejects the submission, displaying an inline error message: "Return quantity cannot exceed the consumed quantity of [X]."

- **Error Flow 2: Missing return reason**
    - **Trigger**: User attempts to confirm the return without selecting a reason for one or more returned items.
    - **Outcome**: The system rejects the submission and highlights the fields requiring a reason.

- **Error Flow 3: Invalid work order state**
    - **Trigger**: A user attempts to initiate a return against a work order that is not in a `Completed` or `Closed` state.
    - **Outcome**: The system prevents the action and displays a notification: "Returns can only be processed for completed or closed work orders."

- **Error Flow 4: System or network failure**
    - **Trigger**: A database or network error occurs during the transaction processing.
    - **Outcome**: The entire inventory transaction is rolled back. No changes are committed to the inventory levels or ledger. An error is logged, and a user-friendly error message is displayed.

## Business Rules
- A return reason is mandatory for every item being returned to stock.
- Only items from a `Completed` or `Closed` work order can be returned.
- The quantity of an item being returned cannot exceed the quantity originally consumed by the work order.
- All inventory adjustments related to a single return transaction must be processed atomically.
- The financial reconciliation of returned items is out of scope for this story and will be handled by the Accounting domain, which consumes the event emitted by the Inventory domain.

## Data Requirements
The system must be able to create and persist an `InventoryReturn` entity with the following attributes:

```json
{
  "inventoryReturnId": "uuid", // Primary Key
  "workOrderId": "uuid",       // FK to WorkOrder
  "locationId": "uuid",        // FK to Location where stock is returned
  "processedByUserId": "uuid", // FK to User
  "processedAt": "timestamp_utc",
  "returnedItems": [
    {
      "sku": "string",
      "quantityReturned": "integer",
      "returnReasonCode": "enum" // e.g., NOT_NEEDED, WRONG_PART, CUSTOMER_REFUSED
    }
  ]
}
```

A corresponding `InventoryLedger` entry should be created for each item, referencing the `inventoryReturnId`.

## Acceptance Criteria
**AC 1: Successful Return of Unused Items**
- **Given** a `Completed` work order that consumed 5 units of `SKU-123`.
- **And** the current stock for `SKU-123` is 50.
- **When** a Warehouse Manager initiates a return for that work order, specifies a quantity of 2 for `SKU-123`, and selects the reason 'Not Needed'.
- **Then** the system successfully processes the return.
- **And** a new `InventoryReturn` record is created.
- **And** the `quantityOnHand` for `SKU-123` becomes 52.
- **And** an `Inventory.ItemReturnedToStock` event is emitted containing the details of the return.

**AC 2: Attempt to Return More Items Than Consumed**
- **Given** a `Completed` work order that consumed 5 units of `SKU-123`.
- **When** a user attempts to return 6 units of `SKU-123` against that work order.
- **Then** the system rejects the request.
- **And** an error message is displayed indicating the return quantity exceeds the consumed quantity.
- **And** the stock level for `SKU-123` remains unchanged.

**AC 3: Attempt to Return Without a Reason**
- **Given** a `Completed` work order with items eligible for return.
- **When** a user selects 1 unit of `SKU-123` to return but does not select a return reason.
- **And** the user tries to confirm the transaction.
- **Then** the system rejects the submission.
- **And** a validation message is displayed prompting the user to select a reason.
- **And** no inventory records are changed.

## Audit & Observability
- **Audit Event**: An `Inventory.ItemReturnedToStock` event MUST be emitted on successful completion. The event payload should include `workOrderId`, `inventoryReturnId`, `processedByUserId`, and a list of returned items with SKUs, quantities, and reasons.
- **Logging**:
    - INFO: Log the initiation and successful completion of the return transaction, including `workOrderId` and `inventoryReturnId`.
    - WARN: Log any failed validation attempts, such as returning excess quantity.
    - ERROR: Log any exceptions during the database transaction, including the full stack trace and relevant IDs.
- **Metrics**:
    - `inventory.returns.success.count`: Counter incremented for each successful return transaction.
    - `inventory.returns.failure.count`: Counter incremented for each failed return attempt.
    - `inventory.returns.items.returned`: Histogram tracking the number of items per return transaction.

## Original Story (Unmodified – For Traceability)
# Issue #177 — [BACKEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #237 - Fulfillment: Return Unused Items to Stock with Reason
**URL**: https://github.com/louisburroughs/durion/issues/237
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
*Generated by Missing Issues Audit System - 2025-12-26T17:37:57.987608191*