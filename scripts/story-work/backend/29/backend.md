Title: [BACKEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/29
Labels: type:story, domain:inventory, status:needs-review

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:needs-review

### Recommended
- agent:inventory
- agent:story-authoring

---
**Rewrite Variant:** inventory-flexible
---

## Story Intent
**Goal:** To ensure parts required for a specific job are available when needed by creating durable reservations for stock items against work order lines.
**Purpose:** This mechanism prevents parts from being sold or used for other jobs, improving service predictability and reducing delays caused by stock unavailability. It formally links inventory state to the work execution lifecycle.

## Actors & Stakeholders
- **System (Primary Actor):** The `Work Execution System`, which initiates reservation requests on behalf of a user action (e.g., approving a work order).
- **System (Owner):** The `Inventory Management System`, which is the system of record for all stock levels, reservations, and allocations. It owns the business logic for this story.
- **Service Advisor (Stakeholder):** Needs visibility into whether parts for a job are secured to communicate confidently with the customer.
- **Parts Manager (Stakeholder):** Manages inventory levels and needs to understand demand from reserved stock to inform purchasing decisions.

## Preconditions
1. A valid, non-cancelled `Work Order` and `Work Order Line` must exist in the Work Execution system.
2. The part number (SKU) referenced by the work order line must exist in the Product/Parts catalog.
3. The `Inventory Management System` is available and can be reached by the `Work Execution System`.

## Functional Behavior

### 1. Create/Update Stock Reservation
- **Trigger:** The `Work Execution System` sends an `UpsertReservationRequest` to the `Inventory Management System`. This request is idempotent, identified by the `WorkOrderLineID`.
- **Process:**
    1. The `Inventory Management System` receives the request, containing the `WorkOrderLineID`, `SKU`, and `RequiredQuantity`.
    2. It validates the request against preconditions.
    3. It checks for an existing `Reservation` for the given `WorkOrderLineID`.
        - If none exists, it creates a new `Reservation` record in a `PENDING` state.
        - If one exists, it proceeds to update the existing reservation.
    4. The system creates `Allocation` records in **SOFT** state by default. SOFT allocations represent intent to use inventory but do **not** reduce ATP and may be reallocated by the system.
    5. The `Reservation` status is updated based on the outcome (e.g., `FULFILLED`, `PARTIALLY_FULFILLED`, `BACKORDERED`).
- **Outcome:** The `Inventory Management System` returns a `ReservationConfirmation` response, detailing the `ReservationID`, status, quantity requested, quantity allocated (SOFT), and quantity backordered.

### 2. Promote Allocation from SOFT to HARD
- **Trigger:** An explicit system or user action occurs:
    1. **Picking/Issuing begins** (e.g., Pick Task started or Parts Issued)
    2. **Workorder status transitions to irreversible execution state** (e.g., `IN_PROGRESS`)
    3. **Explicit user action** with permission (e.g., "Reserve Parts Now")
- **Process:**
    1. The system locates the `Allocation` records associated with the `Reservation`.
    2. It changes the `Allocation` status from `SOFT` to `HARD`.
    3. The system decrements the `ATP` quantity for the SKU by the allocated amount.
    4. An audit event (`AllocationHardened`) is created with reference to the triggering cause.
- **Outcome:** The allocation is now a HARD reservation, protected from reallocation, and ATP is reduced.
- **Permissions:** Converting SOFT → HARD requires the `inventory.reserve.hard` permission.

### 3. Cancel Stock Reservation
- **Trigger:** The `Work Execution System` sends a `CancelReservationRequest` when a work order line is cancelled or removed.
- **Process:**
    1. The system locates the `Reservation` associated with the `WorkOrderLineID`.
    2. It changes the `Reservation` status to `CANCELLED`.
    3. It deletes any associated `Allocation` records.
    4. If allocations were in HARD state, the system increments the `ATP` for the SKU by the previously allocated amount.
- **Outcome:** The system confirms the cancellation. The previously reserved stock (if HARD) is now available for other requests.

## Alternate / Error Flows
- **Invalid SKU:** If the SKU in the request does not exist, the system rejects the request with a `SKU_NOT_FOUND` error.
- **Invalid WorkOrderLineID:** If the `WorkOrderLineID` is malformed or invalid, the system rejects the request with an `INVALID_REQUEST` error.
- **Zero or Negative Quantity:** If the `RequiredQuantity` is ≤ 0, the system treats it as a cancellation request if a reservation exists, or rejects it as an `INVALID_QUANTITY` error if one does not.
- **Insufficient Stock (SOFT):** If no stock is available when creating SOFT allocations, a `Reservation` is still created but is immediately placed in a `BACKORDERED` state with zero allocations.
- **Insufficient ATP (HARD Promotion):** If attempting to promote to HARD and ATP is insufficient, the promotion fails with an `INSUFFICIENT_ATP` error.

## Business Rules
1. **System of Record:** The `Inventory Management System` is the single source of truth for `ATP`, reservations, and allocations.
2. **Idempotency:** All reservation creation and update operations (upserts) against a `WorkOrderLineID` MUST be idempotent. Sending the same request multiple times must not create duplicate reservations or over-allocate stock.
3. **ATP Calculation:** `ATP` = `Quantity-On-Hand` - `Quantity-Allocated-HARD` (SOFT allocations do NOT reduce ATP).
4. **Allocation States:**
    - **SOFT:** Intent to use inventory. Does NOT reduce ATP. Eligible for reallocation.
    - **HARD:** Committed inventory. REDUCES ATP. Protected from reallocation.
5. **SOFT → HARD Transition:** Only occurs via explicit trigger (picking, work start, or user action). Never automatic or time-based.
6. **No Implicit Promotion:** Time-based auto-promotion and implicit promotion based on workorder type are explicitly NOT allowed.
7. **Backorders:** A reservation is considered backordered (or partially backordered) if the allocated quantity is less than the required quantity. This status should be clearly queryable.

## Data Requirements

### Reservation (Entity)
- `ReservationID` (Primary Key, UUID)
- `WorkOrderLineID` (Foreign Key / Reference, Unique)
- `SKU` (string)
- `RequiredQuantity` (integer)
- `AllocatedQuantity` (integer, calculated from Allocations)
- `Status` (enum: `PENDING`, `PARTIALLY_FULFILLED`, `FULFILLED`, `BACKORDERED`, `CANCELLED`)
- `CreatedAt`, `UpdatedAt` (timestamps)

### Allocation (Entity)
- `AllocationID` (Primary Key, UUID)
- `ReservationID` (Foreign Key)
- `LocationID` (Identifier for warehouse/bin where stock resides)
- `AllocatedQuantity` (integer)
- `AllocationState` (enum: `SOFT`, `HARD`) **[CLARIFIED]**
- `Status` (enum: `ALLOCATED`, `PICKED`, `RELEASED`)
- `HardenedAt` (timestamp, nullable - set when promoted to HARD)
- `HardenedBy` (string, nullable - user or system that triggered promotion)
- `HardenedReason` (string, nullable - reason for promotion: PICKING, WORK_START, USER_ACTION)

## Acceptance Criteria

**Scenario 1: Sufficient stock is available for a new SOFT reservation**
- **Given** a work order line requires 5 units of SKU "FLTR-01".
- **And** the `On-Hand` quantity for "FLTR-01" is 10 units.
- **When** the `Work Execution System` requests a reservation for 5 units against the work order line.
- **Then** a new `Reservation` record is created with `Status: FULFILLED`.
- **And** the `AllocatedQuantity` on the reservation is 5.
- **And** `Allocation` records are created with `AllocationState: SOFT`.
- **And** the system's `ATP` for "FLTR-01" remains 10 (SOFT allocations do NOT reduce ATP).
- **And** a confirmation is sent back to the `Work Execution System`.

**Scenario 2: SOFT allocation is promoted to HARD when picking begins**
- **Given** an existing SOFT reservation for 5 units of SKU "FLTR-01".
- **And** the `ATP` for "FLTR-01" is 10 units.
- **When** a Pick Task is started for this work order line.
- **Then** the `Allocation` records are updated with `AllocationState: HARD`.
- **And** the system's `ATP` for "FLTR-01" is reduced to 5.
- **And** an audit event `AllocationHardened` is created with reason "PICKING".

**Scenario 3: HARD promotion fails due to insufficient ATP**
- **Given** an existing SOFT reservation for 5 units of SKU "FLTR-01".
- **And** the `ATP` for "FLTR-01" is only 2 units (other HARD reservations consumed 8 units).
- **When** an attempt is made to promote the allocation to HARD.
- **Then** the promotion fails with error `INSUFFICIENT_ATP`.
- **And** the allocation remains in SOFT state.
- **And** an error is returned to the requesting system.

**Scenario 4: A reservation request is updated with a different quantity**
- **Given** an existing `FULFILLED` SOFT reservation exists for 5 units of SKU "FLTR-01" for a specific work order line.
- **When** the `Work Execution System` sends an update request for the same work order line, now requiring 7 units.
- **Then** the existing `Reservation` is updated.
- **And** its `RequiredQuantity` is updated to 7.
- **And** the `AllocatedQuantity` becomes 7 (if sufficient On-Hand exists).
- **And** the allocations remain SOFT (no ATP impact).

**Scenario 5: An idempotent request is received**
- **Given** a reservation for 5 units of SKU "FLTR-01" has already been successfully created.
- **When** the exact same reservation request is received a second time.
- **Then** no new `Reservation` or `Allocation` records are created.
- **And** the system's `ATP` for "FLTR-01" is not changed.
- **And** the system returns the same successful `ReservationConfirmation` as the first request.

**Scenario 6: A SOFT reservation is cancelled**
- **Given** an existing SOFT reservation for 5 units of SKU "FLTR-01".
- **When** the `Work Execution System` sends a request to cancel the reservation for that work order line.
- **Then** the `Reservation` status is updated to `CANCELLED`.
- **And** the system's `ATP` for "FLTR-01" is unchanged (SOFT allocations don't affect ATP).

**Scenario 7: A HARD reservation is cancelled**
- **Given** an existing HARD reservation for 5 units of SKU "FLTR-01".
- **And** the `ATP` for "FLTR-01" is 10 (reduced from 15 by the HARD allocation).
- **When** the `Work Execution System` sends a request to cancel the reservation for that work order line.
- **Then** the `Reservation` status is updated to `CANCELLED`.
- **And** the system's `ATP` for "FLTR-01" is increased to 15.

## Audit & Observability
- **Audit Trail:** Every state change to a `Reservation` or `Allocation` entity must be recorded in an audit log. This includes creation, status changes (e.g., `PENDING` -> `FULFILLED`), quantity changes, SOFT -> HARD promotion, and cancellations. The log must include the timestamp, the responsible system or user, triggering cause, and the before/after values.
- **Logging:** All incoming API requests and outgoing responses for reservation operations must be logged with correlation IDs for traceability.
- **Metrics:** The system should expose metrics for:
    - Number of active SOFT reservations.
    - Number of active HARD reservations.
    - Total quantity of items on SOFT reservation (by SKU).
    - Total quantity of items on HARD reservation (by SKU).
    - Number of backordered line items.
    - Latency of reservation API endpoints.
    - Number of failed HARD promotions due to insufficient ATP.

## Clarification Resolution

**Decision from Issue #227:**

All allocations are created as **SOFT** by default. A **HARD reservation is created only via an explicit system action** when operational commitment is reached. This explicitly uses **Option C (Manual Promotion)** with the following rules:

- **SOFT allocations** do NOT reduce ATP and may be reallocated
- **HARD reservations** reduce ATP and are protected from reallocation
- **Transition to HARD** occurs only when:
  1. Picking/Issuing begins (Pick Task started or Parts Issued)
  2. Workorder status transitions to irreversible execution state (e.g., `IN_PROGRESS`)
  3. Explicit user action with `inventory.reserve.hard` permission

**What is explicitly NOT allowed:**
- ❌ Time-based auto-promotion
- ❌ Implicit promotion based on workorder type
- ❌ Silent ATP reduction without an event or user/system action

This decision is deterministic, operationally safe, auditable, and extensible for future policy additions.

---

## Original Story (Unmodified – For Traceability)
# Issue #29 — [BACKEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to reserve stock for workorder lines so that parts are held for the job.

## Details
- Soft allocation vs hard reservation.
- Handle partial reservations and backorders.

## Acceptance Criteria
- Reservation created/updated.
- ATP reflects allocations.
- Idempotent updates.
- Audited.

## Integrations
- Workexec requests reservation; inventory responds with allocations and pick tasks.

## Data / Entities
- Reservation, Allocation, WorkorderLineRef

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