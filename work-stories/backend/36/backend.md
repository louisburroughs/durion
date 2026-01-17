Title: [BACKEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/36
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

---
**Rewrite Variant:** inventory-flexible
**Clarification Applied:** #233 (ATP formula, UOM scope, SLA, definitive ledger event types)
---

## Story Intent
**As a** Service Advisor,  
**I need to** know the real-time on-hand and available-to-promise (ATP) quantities for a specific part at a given location,  
**so that I can** provide accurate quotes and schedule customer work with confidence.

## Actors & Stakeholders
- **Primary Actor**: Service Advisor
- **System Actors**:
  - Inventory Service (computes and serves availability)
  - POS UI (consumes availability)
- **Stakeholders**:
  - Work Execution (confirms parts availability for scheduled jobs)
  - Pricing (may use ATP as an input)
  - Inventory Manager (operational planning and stock control)

## Preconditions
- An immutable inventory ledger exists and records stock movements with product, location, movement type, and quantity.
- A system for recording stock allocations (soft reservations for specific work orders/sales) exists and is accessible.
- Products (SKU + base UOM) are defined in Product domain.
- A location hierarchy exists (parent locations and optional storage/bin locations).

## Functional Behavior
The Inventory Service exposes an API endpoint providing on-hand and ATP for a specified product.

1. **Trigger**: Request for availability.
2. **Input**: `productSku`, `locationId`, optional `storageLocationId`.
3. **Processing**:
   - Compute `onHandQuantity` from the inventory ledger (net sum of physical stock movements; see Business Rules).
   - Query allocation system for `allocatedQuantity` for the same scope.
   - Compute `availableToPromiseQuantity` using the defined formula.
   - If only `locationId` is provided, aggregate across all child storage locations.
   - If `storageLocationId` is provided, scope is narrowed to that storage location.
4. **Output**: Return an `AvailabilityView` including `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, and `unitOfMeasure`.

## Alternate / Error Flows
- **Product Not Found**: If `productSku` is not found, return `404 Not Found`.
- **Location Not Found**: If `locationId` / `storageLocationId` is not found, return `404 Not Found`.
- **No Inventory History**: If product has no ledger entries for the scope, return success with quantities = 0.

## Business Rules

### On-Hand Calculation (Resolved)
On-hand is the net sum of **physical stock movements** (inbound/outbound) plus count variances. Allocations/reservations are **not** part of on-hand.

**Include in On-Hand (add/subtract by direction)**
- Inbound (positive):
  - `GOODS_RECEIPT`
  - `TRANSFER_IN`
  - `RETURN_TO_STOCK`
  - `ADJUSTMENT_IN`
  - `COUNT_VARIANCE_IN`
- Outbound (negative):
  - `GOODS_ISSUE`
  - `TRANSFER_OUT`
  - `SCRAP_OUT`
  - `ADJUSTMENT_OUT`
  - `COUNT_VARIANCE_OUT`

**Explicitly excluded from On-Hand**
- `RESERVATION_CREATED`, `RESERVATION_RELEASED`
- `ALLOCATION_CREATED`, `ALLOCATION_RELEASED`
- `BACKORDER_CREATED`, `BACKORDER_RESOLVED`
- `PICK_TASK_CREATED`, `PICK_TASK_COMPLETED` (unless these post one of the physical movement events above)

### ATP Calculation Formula (Resolved)
**ATP v1:** $\text{ATP} = \text{OnHand} - \text{Allocations}$  
**Expected receipts are out of scope** for this story.

Optional forward-compatibility: the API may return `expectedReceiptsQty` as nullable, but it MUST NOT be included in ATP.

### Allocation Definition
An allocation is a soft reservation for a specific purpose (e.g., scheduled work order) that has not yet been physically picked/issued.

### Unit of Measure (UOM) Consistency (Resolved)
All internal calculations and API responses are in the product’s **base UOM**.

Out of scope: request/response UOM conversions (e.g., case vs each).

### Location Aggregation
Querying a parent location aggregates all stock within the location across child storage locations.

## Data Requirements

### API Request
- `productSku` (string, required)
- `locationId` (UUID, required)
- `storageLocationId` (UUID, optional)

### API Response (`AvailabilityView`)
- `productSku` (string)
- `locationId` (UUID)
- `storageLocationId` (UUID, nullable)
- `onHandQuantity` (decimal, base UOM)
- `allocatedQuantity` (decimal, base UOM)
- `availableToPromiseQuantity` (decimal, base UOM)
- `unitOfMeasure` (string; product base UOM)

## Acceptance Criteria

**Scenario 1: Simple On-Hand Calculation**
- **Given** product `SKU-123` has ledger history at `LOC-A`: `GOODS_RECEIPT +10` and `GOODS_ISSUE -2`
- **When** availability is requested for `SKU-123` at `LOC-A`
- **Then** `onHandQuantity = 8`.

**Scenario 2: ATP Calculation with Allocations**
- **Given** `onHandQuantity = 8` for `SKU-123` at `LOC-A`
- **And** `allocatedQuantity = 3` at `LOC-A`
- **When** availability is requested for `SKU-123` at `LOC-A`
- **Then** `availableToPromiseQuantity = 5`.

**Scenario 3: Aggregate Calculation by Parent Location**
- **Given** `LOC-WAREHOUSE` contains `BIN-1` and `BIN-2`
- **And** ledger shows 5 units of `SKU-ABC` in `BIN-1` and 3 units in `BIN-2`
- **When** availability is requested for `SKU-ABC` at `LOC-WAREHOUSE` (no `storageLocationId`)
- **Then** `onHandQuantity = 8`.

**Scenario 4: Request for a Non-Existent Product**
- **Given** product catalog does not contain `SKU-DOES-NOT-EXIST`
- **When** availability is requested
- **Then** return `404 Not Found`.

**Scenario 5: Request for a Valid Product with No Inventory**
- **Given** `SKU-456` exists but has no ledger entries at `LOC-A`
- **When** availability is requested
- **Then** return success with `onHandQuantity = 0`, `allocatedQuantity = 0`, `availableToPromiseQuantity = 0`.

## Audit & Observability
- **Logging**: Log each availability request (`productSku`, `locationId`, optional `storageLocationId`) and resulting quantities. Log lookup/summation errors.
- **Metrics**:
  - Timer for endpoint latency.
  - Counters for success and error responses (4xx/5xx).
- **Tracing**: Distributed tracing from API gateway through Inventory Service to data stores.

## Performance Requirements (Resolved)
- Core endpoint (single product, single location, warm cache):
  - **P95 < 200ms**
  - P50 < 80ms
  - P99 < 400ms
- Measurement is at the service boundary, excluding caller network latency.

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP so that we can quote and schedule realistically.

## Details
- On-hand computed from ledger; ATP = on-hand - allocations + expected receipts (optional).
- Provide per location and optionally per storage location.

## Acceptance Criteria
- Availability query returns on-hand and ATP.
- Consistent UOM handling.
- SLA suitable for estimate UI.

## Integrations
- Product/workexec query availability; product may surface ATP to pricing.

## Data / Entities
- AvailabilityView, AllocationSummary, ExpectedReceiptSummary

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