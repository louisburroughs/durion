Title: [BACKEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/24
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

**Rewrite Variant:** inventory-flexible

## Story Intent
**As a** Dispatcher,
**I want** the system to deterministically re-evaluate and reassign reserved stock when a work order’s schedule, priority, or “waiting on inventory” status changes,
**so that** limited inventory is allocated fairly to urgent jobs without starving lower-priority work, and allocations remain auditable and explainable.

## Actors & Stakeholders
- **Dispatcher:** Relies on accurate reservations aligned to priority and schedule.
- **System (Inventory Domain):** Owns reservations/allocations and executes reallocation.
- **System (Work Execution):** Source of base priority and lifecycle changes (e.g., completed/cancelled).
- **System (Shop Management):** Source of scheduling/due-time updates.

## Preconditions
1. Work orders exist with base priority and schedule fields.
2. One or more work orders require the same finite-stock item.
3. Inventory has existing `Allocation` records reserving quantities against work orders.
4. Work orders may be blocked waiting on inventory (`waitingSince` semantics).

## Functional Behavior

### Reallocation triggers
Reallocation is triggered when Inventory receives any of the following signals:
- **Schedule Change** (from Shop Management): due time and/or schedule start time changes.
- **Priority Change** (from Work Execution): base priority changes.
- **Work Order Lifecycle Change** (from Work Execution): cancelled/completed.
- **Inventory Availability Change** (from Inventory): replenishment/shortage detected for the stock item.
- **Manual Override** (from a user action, audited).

For each trigger, Inventory identifies the affected work order(s) and stock item(s), then re-runs deterministic allocation for each impacted stock item.

### Deterministic reallocation algorithm (per stock item)
1. Gather all work orders that currently have allocations for this stock item **plus** any work orders requiring this item that are currently unallocated.
2. For each work order, compute:
   - `basePriority` (from Work Execution)
   - `effectivePriority` (Inventory-computed; includes aging)
   - `dueDateTime` (from Shop Management)
   - `scheduleStartTime` (from Shop Management)
   - `waitingSince` (when the work became blocked on inventory)
3. Sort work orders using the stable multi-key sort in “Sorting Logic”.
4. Allocate available quantity in order, applying “Full Allocation Only” rule.
5. Persist updated allocations and emit audit records for each change.
6. Recompute ATP (`onHand - totalAllocated`) for the stock item.

## Business Rules

### Starvation Prevention (mandatory)
Inventory must implement deterministic **priority aging** while a work order is blocked waiting on inventory.

Default policy values (configurable):
- `agingGracePeriod = 24h`
- `agingInterval = 24h`
- `agingStep = +1 priority level`
- `maxEffectivePriority = CRITICAL`

Formula:

```
effectivePriority =
  min(
    basePriority + floor((now - waitingSince - agingGracePeriod) / agingInterval),
    maxEffectivePriority
  )
```

Constraints:
- Aging applies only while blocked on inventory.
- Aging resets when stock is successfully allocated.
- Manual priority override is allowed but must be audited.

### Sorting Logic (stable, deterministic)
Work orders must be sorted for allocation as follows:
1. `effectivePriority DESC`
2. `dueDateTime ASC`
3. `waitingSince ASC`
4. `scheduleStartTime ASC`
5. `workOrderCreatedAt ASC` (final tie-breaker)

### Full Allocation Only
A work order must receive its full required quantity for the stock item to get an allocation. Partial allocations are not supported.

## Data Requirements

### `Allocation`
| Field | Type | Description |
|---|---|---|
| `allocationId` | UUID | Primary key |
| `workOrderId` | UUID | Work order |
| `stockItemId` | UUID | Stock item |
| `quantityReserved` | Integer/Decimal | Reserved quantity |
| `createdAt`, `updatedAt` | Timestamp | Server-generated |

### Work order fields required for reallocation
- `basePriority`
- `dueDateTime`
- `scheduleStartTime`
- `waitingSince` (nullable)
- `workOrderCreatedAt`

### Audit record requirements
Each allocation change must create an audit record containing:
- `reasonCode` (enum)
- `previousAllocationState`
- `newAllocationState`
- `triggeredBy` (`USER` | `SYSTEM`)
- `triggerReferenceId` (scheduleId, userId, eventId, etc.)
- `occurredAt`

### Audit Reason Codes (required enum v1)
- `SCHEDULE_CHANGE`
- `PRIORITY_CHANGE`
- `PRIORITY_AGED`
- `MANUAL_OVERRIDE`
- `STOCK_SHORTAGE`
- `STOCK_REPLENISHED`
- `LOCATION_CHANGE`
- `WORK_ORDER_CANCELLED`
- `WORK_ORDER_COMPLETED`
- `SYSTEM_REBALANCE`

## Acceptance Criteria

**AC1: Deterministic reallocation on priority change**
- Given a stock item with limited on-hand
- And two work orders competing for the same item
- When WorkExec publishes a priority change
- Then Inventory recomputes allocations using the stable sort
- And the same inputs always yield the same allocation outcome
- And an audit record is written with reason `PRIORITY_CHANGE`.

**AC2: Starvation prevention via priority aging**
- Given a work order is blocked on inventory with `waitingSince = T0`
- And `now - T0` exceeds `agingGracePeriod`
- When Inventory recomputes allocations
- Then the work order’s `effectivePriority` increases deterministically (capped)
- And an audit record is written with reason `PRIORITY_AGED` when aging changes allocation outcome.

**AC3: Sorting includes fairness tie-breakers**
- Given multiple work orders share the same effective priority and due time
- When Inventory sorts for allocation
- Then the order is determined by `waitingSince`, then `scheduleStartTime`, then `createdAt`.

**AC4: Full allocation only**
- Given remaining available quantity is insufficient to fulfill a work order’s full requirement
- When Inventory allocates
- Then that work order receives no allocation for that item.

**AC5: ATP remains consistent**
- Given on-hand is constant and allocations are moved between work orders
- When reallocation occurs
- Then total allocated quantity remains constant
- And ATP (`onHand - totalAllocated`) remains constant.

## Audit & Observability
- Audit every allocation change with reason code and before/after state.
- Metrics:
  - `allocations_reallocated_total` (tagged by reasonCode)
  - `allocations_starvation_aged_total`
  - `allocation_recompute_duration_seconds`
- Logs: include correlation IDs for triggering events.

## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want reallocations so that reservations reflect updated schedule and priorities.

## Details
- Reallocation by priority and due time.
- Rules prevent starvation (optional).

## Acceptance Criteria
- Allocations updated deterministically.
- Audit includes reason.
- ATP updated.

## Integrations
- Workexec triggers priority changes; shopmgr schedule updates due times.

## Data / Entities
- Allocation, PriorityPolicy, AuditLog

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