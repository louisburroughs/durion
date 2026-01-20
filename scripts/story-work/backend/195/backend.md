Title: [BACKEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/195
Labels: story-implementation, type:story, layer:functional, kiro, domain:inventory, status:ready-for-dev

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

**As an** Inventory Manager,
**I want to** define and store volume-based cost tiers for items provided by specific suppliers,
**So that** the system can accurately calculate the cost of goods for purchase orders and support optimized procurement decisions based on quantity price breaks.

## Actors & Stakeholders

- **Inventory Manager**: The primary user responsible for configuring and maintaining supplier cost data.
- **Purchasing System**: An automated or user-driven system that will consume this cost tier data to determine the correct unit cost when creating Purchase Orders.
- **System**: The backend application responsible for storing, validating, and retrieving the cost tier data.
- **Accounting System (Downstream Consumer)**: A system that will indirectly benefit from more accurate Cost of Goods Sold (COGS) data derived from these cost tiers.

## Preconditions

- The user performing the action is authenticated and has permissions to manage supplier and inventory cost data.
- The `Supplier` and `InventoryItem` entities for which cost tiers are being defined must already exist in the system.

## Functional Behavior

This story covers the creation and management of a `SupplierItemCost` entity, which can optionally contain a set of `CostTiers`. This allows the system to model purchasing costs that vary by quantity.

### 4.1. Core Scenario: Defining Cost Tiers for a Supplier Item

1.  **Trigger**: An Inventory Manager navigates to the cost management interface for a specific `InventoryItem` from a particular `Supplier`.
2.  **Action**: The user initiates the creation of a new cost tier structure. They define a series of tiers, each specifying a minimum quantity, a maximum quantity, and the per-unit cost for that range.
    - Example Tiers for Item "SKU-123" from "Supplier A":
        - Tier 1: `min_quantity: 1`, `max_quantity: 10`, `unit_cost: $5.00`
        - Tier 2: `min_quantity: 11`, `max_quantity: 50`, `unit_cost: $4.50`
        - Tier 3: `min_quantity: 51`, `max_quantity: null`, `unit_cost: $4.00`
3.  **System Validation**: The system validates that the submitted tiers are logical:
    - Quantity ranges are contiguous and do not overlap.
    - `min_quantity` is less than or equal to `max_quantity` (if `max_quantity` is not null).
    - `unit_cost` is a positive monetary value.
4.  **Outcome**: Upon successful validation, the system persists the cost tier set associated with the specific `Supplier` and `InventoryItem`. The system returns a success confirmation.

### 4.2. Supported Operations

- **Create**: Define a new set of cost tiers for a supplier-item combination that does not currently have one.
- **Read**: Retrieve the existing cost tiers for a given supplier-item.
- **Update**: Modify the quantities or costs of an existing set of tiers.
- **Delete**: Remove the entire cost tier structure for a supplier-item, reverting it to a simple, non-tiered cost model (if applicable) or no cost.

## Alternate / Error Flows

- **Error - Overlapping Tiers**: If the user attempts to save a set of tiers where quantity ranges overlap (e.g., 1-10 and 5-15), the system must reject the request with a `400 Bad Request` error and a descriptive message (e.g., `INVALID_TIER_STRUCTURE: Quantity ranges overlap.`).
- **Error - Gaps in Tiers**: If the user attempts to save a set of tiers with a gap between them (e.g., 1-10 and 20-30), the system must reject the request with a `400 Bad Request` error and a descriptive message (e.g., `INVALID_TIER_STRUCTURE: Quantity ranges must be contiguous.`).
- **Error - Non-Existent Entities**: If the request references a `supplier_id` or `item_id` that does not exist, the system must respond with a `404 Not Found` error.
- **Error - Invalid Data**: If any `unit_cost` is zero or negative, or `min_quantity` is less than 1, the system must reject the request with a `400 Bad Request` error.

## Business Rules

- A given `Supplier` and `InventoryItem` combination can have at most one active cost tier structure.
- Quantity ranges within a tier set must be contiguous, starting from a `min_quantity` of 1.
- The final tier in a set should have a `null` or infinite `max_quantity` to represent "and above".
- All `unit_cost` values within a single tier set must be in the same currency, which is determined by the supplier's configuration.

## Data Requirements

A new data model is required to store cost tiers. This will likely involve two new entities.

**`SupplierItemCost` (or similar)**
| Field | Type | Notes |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `supplier_id` | UUID | FK to `suppliers` table. Part of unique constraint. |
| `item_id` | UUID | FK to `items` table. Part of unique constraint. |
| `currency_code` | CHAR(3) | e.g., "USD". ISO 4217 code. |
| `base_cost` | DECIMAL | Fallback cost if no tiers are defined. Optional. |
| `created_at` | TIMESTAMPZ | |
| `updated_at` | TIMESTAMPZ | |

**`CostTier`**
| Field | Type | Notes |
| :--- | :--- | :--- |
| `id` | UUID | Primary Key |
| `supplier_item_cost_id` | UUID | FK to `supplier_item_costs` table. |
| `min_quantity` | INTEGER | The lower bound of the quantity range (inclusive). Must be >= 1. |
| `max_quantity` | INTEGER | The upper bound of the quantity range (inclusive). Nullable for the final tier. |
| `unit_cost` | DECIMAL | The per-unit cost for this quantity range. |

## Acceptance Criteria

**Scenario 1: Successfully Create a Valid Cost Tier Structure**
- **Given** an `InventoryItem` and a `Supplier` exist in the system.
- **When** a POST request is sent to the `/api/costs/supplier-item` endpoint with a valid, non-overlapping, and contiguous set of cost tiers.
- **Then** the system responds with a `201 Created` status.
- **And** the cost tiers are correctly persisted in the database, associated with the specified supplier and item.

**Scenario 2: Attempt to Create Overlapping Cost Tiers**
- **Given** an `InventoryItem` and a `Supplier` exist.
- **When** a POST request is sent with a set of cost tiers where at least two quantity ranges overlap.
- **Then** the system responds with a `400 Bad Request` status.
- **And** the response body contains an error code `INVALID_TIER_STRUCTURE` and a human-readable message explaining the overlap.

**Scenario 3: Attempt to Create Tiers with a Quantity Gap**
- **Given** an `InventoryItem` and a `Supplier` exist.
- **When** a POST request is sent with a set of cost tiers where a quantity gap exists between tiers (e.g., tier 1 ends at 10, tier 2 starts at 12).
- **Then** the system responds with a `400 Bad Request` status.
- **And** the response body contains an error code `INVALID_TIER_STRUCTURE` and a human-readable message about the lack of contiguity.

**Scenario 4: Retrieve Existing Cost Tiers**
- **Given** a cost tier structure has been saved for a specific `InventoryItem` and `Supplier`.
- **When** a GET request is made to the endpoint for that supplier-item's cost.
- **Then** the system responds with a `200 OK` status.
- **And** the response body contains the full, ordered list of cost tiers.

## Audit & Observability

- **Auditing**: All CUD (Create, Update, Delete) operations on `SupplierItemCost` and `CostTier` entities must be logged to an audit trail. The log entry must include the `user_id` of the actor, the timestamp, the entity IDs, and a summary of the change (e.g., "Cost tier for SKU-123 from Supplier A updated").
- **Logging**: Application logs should record the start and end of cost tier management API calls, including key identifiers (`supplier_id`, `item_id`). Validation failures should be logged at a `WARN` level.
- **Events**: Upon successful creation or update of a cost tier structure, the system should emit a domain event, such as `inventory.SupplierItemCostUpdated`, containing the `supplier_id` and `item_id`. This allows downstream systems (like a purchasing or analytics service) to react to the change.

---
## Original Story (Unmodified – For Traceability)
# Issue #195 — [BACKEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional)

## Current Labels
- backend
- story-implementation
- type:story
- layer:functional
- kiro

## Current Body
## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #197 - Cost: Store Supplier/Vendor Cost Tiers (Optional)
**URL**: https://github.com/louisburroughs/durion/issues/197
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
*Generated by Missing Issues Audit System - 2025-12-26T17:38:46.633180418*