Title: [BACKEND] [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/28
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

**Rewrite Variant:** inventory-flexible

## Story Intent
**As a** Dispatcher,
**I want** the system to automatically generate a deterministic pick list when parts for a work order are confirmed/reserved,
**so that** Mechanics have a clear, efficient, and ordered set of tasks for gathering the correct parts, minimizing errors and accelerating vehicle service preparation.

## Actors & Stakeholders
- **Dispatcher (Primary Actor):** Oversees allocation of work and parts.
- **Mechanic (End User):** Executes the pick tasks.
- **System (Inventory Domain):** System of record for inventory locations, on-hand, and pick list generation.
- **System (Work Execution Domain):** System of record for work order scheduling and SLA/urgency inputs.

## Preconditions
1. A Work Order exists in Work Execution.
2. The Inventory system has reservations for the Work Order’s required products/quantities.
3. Inventory storage locations have an assigned layout ordering (Zone → Aisle → Rack → Bin).

## Functional Behavior

### Trigger
The process starts when Inventory receives a `WorkOrderPartsReservationConfirmed` event from Work Execution.

**Required trigger inputs** (either on the event or retrievable by Inventory via WorkExec query):
- `workOrderId`
- Reserved line items: `productId`, `quantityRequired`
- Work order SLA/urgency inputs: `workOrderPriority` and one of:
  - `scheduledStartAt` (preferred) and/or
  - `dueAt`

### Happy Path (Success Flow)
1. Inventory validates the trigger payload.
2. Inventory creates a `PickList` associated to `workOrderId`.
3. For each reserved product requirement, Inventory generates **one or more** `PickTask`s (a task is always **one product from one location**):
   - Select the **primary suggested pick location** using the deterministic decision hierarchy in “Location Suggestion”.
   - If the primary location cannot fulfill the full `quantityRequired`, create additional `PickTask`s for the remaining quantity using the same deterministic location selection rules.
   - Populate each task with `productId`, `quantityRequired` (for that task), and `suggestedLocationId`.
4. Inventory assigns each `PickTask`:
   - `priority` using “Priority Determination”
   - `dueAt` using “Due Time Determination”
5. Inventory assigns `sortOrder` to tasks using the deterministic “Sorting Logic”.
6. Inventory finalizes the `PickList` with status `ReadyToPick`.
7. Inventory publishes `PickListCreated` containing `pickListId` and `workOrderId`.

## Alternate / Error Flows
- **Reserved item has no actionable storage location:**
  - If a reserved product has no eligible location (or location data is incomplete), Inventory creates the corresponding task(s) with `status = NeedsReview`.
  - The `PickList` remains in `Draft` until all tasks are actionable.
  - Emit an operational alert/metric for manual intervention.

- **Invalid trigger event:**
  - If `workOrderId` or reserved lines are missing/invalid, reject the event and route it to a DLQ.
  - Log the validation failure with correlation IDs.

## Business Rules

### Priority Determination (Deterministic)
- **Authority:** Base urgency comes from Work Execution; Inventory may apply **bounded adjustments**.

1. Start with `basePriority = workOrderPriority`.
2. Apply inventory-specific modifiers (each adds **+1** if applicable):
   - **Stock risk:** low on-hand for the product/location selection.
   - **Backorder resolution:** this pick unblocks waiting work.
   - **Critical part type:** safety/immobilizing component.
3. Cap at `MAX_PRIORITY` (configurable) to prevent runaway escalation.

$$
EffectivePriority = \min(basePriority + modifiers,\ MAX\_PRIORITY)
$$

### Due Time Determination (Deterministic)
- Default: `pickDueAt = scheduledStartAt − pickLeadTimeBuffer`
- Default `pickLeadTimeBuffer`: **30 minutes** (configurable)
- If no `scheduledStartAt`, inherit `pickDueAt = workOrder.dueAt`.
- Inventory must not set a pick due time later than the work order’s SLA-driven due time.

### Sorting Logic (Route / Location)
- **Decision:** Use a deterministic, layout-aware sort (no route optimization).

Warehouse layout order is:

```
Zone → Aisle → Rack → Bin
```

Each storage location must provide:
- `zoneOrder` (int)
- `aisleOrder` (int or normalized string)
- `rackOrder` (int)
- `binOrder` (int)
- `locationCode` (string)

Stable sorting keys (in order):
1. `zoneOrder ASC`
2. `aisleOrder ASC`
3. `rackOrder ASC`
4. `binOrder ASC`
5. `locationCode ASC` (tie-breaker)

Non-goals (v1): shortest-path optimization, picker-specific routing, dynamic reordering mid-pick.

### Location Suggestion (Single Primary Suggested Location)
When a product exists in multiple locations, select the **primary suggested location** using this strict hierarchy:

1. **Dedicated Pick Zone**
   - If any location has `isPickZone = true` and sufficient on-hand, select it.
2. **FEFO / FIFO compliance**
   - If lot/expiry controlled, select earliest expiry (or earliest receipt date).
3. **Single-location sufficiency**
   - Prefer a single location that can fulfill the full required quantity.
4. **Proximity in layout**
   - Lowest `(zoneOrder, aisleOrder, rackOrder, binOrder)`.
5. **Highest on-hand quantity**
   - Final tie-breaker.

**Partial fulfillment rule:**
- If no single location can fulfill the entire quantity:
  - Suggest the best primary location using the hierarchy above,
  - Create additional `PickTask`s for remaining quantity using the same hierarchy.

Explicit exclusions:
- Do not split picks unnecessarily.
- Do not pick from reserve/bulk locations unless no pick-zone stock exists.

## Data Requirements

### `PickList`
| Field | Type | Description |
|---|---|---|
| `pickListId` | UUID | Primary key |
| `workOrderId` | UUID | Work order reference |
| `status` | Enum | `Draft`, `ReadyToPick`, `InProgress`, ... |
| `createdAt` | Timestamp | Server-generated |

### `PickTask`
| Field | Type | Description |
|---|---|---|
| `pickTaskId` | UUID | Primary key |
| `pickListId` | UUID | Parent pick list |
| `productId` | UUID | Product |
| `quantityRequired` | Integer/Decimal | Quantity for this task |
| `suggestedLocationId` | UUID | Suggested storage location |
| `sortOrder` | Integer | Deterministic order within list |
| `priority` | Integer | Derived priority |
| `dueAt` | Timestamp | Derived due time |
| `status` | Enum | `Pending`, `NeedsReview`, `Picked`, ... |

### `StorageLocation` (required fields for v1)
| Field | Type | Description |
|---|---|---|
| `locationId` | UUID | Primary key |
| `locationCode` | String | Human-readable code |
| `zoneOrder` | Integer | Route sort key |
| `aisleOrder` | String/Integer | Route sort key |
| `rackOrder` | Integer | Route sort key |
| `binOrder` | Integer | Route sort key |
| `isPickZone` | Boolean | Dedicated pick-zone indicator |

## Acceptance Criteria

**Scenario 1: Successful Pick List Generation (single location per product)**
- **Given** a `WorkOrderPartsReservationConfirmed` trigger for a work order with three reserved products.
- **And** each product has at least one eligible location with sufficient on-hand.
- **When** Inventory processes the trigger.
- **Then** a `PickList` is created with status `ReadyToPick`.
- **And** the list contains pick tasks that cover exactly the reserved quantities.
- **And** each task has `priority` and `dueAt` derived from the work order SLA inputs.
- **And** tasks are ordered by the deterministic layout sort.
- **And** a `PickListCreated` event is published.

**Scenario 2: Priority is inherited and modifiers are bounded**
- **Given** a work order with `workOrderPriority = P`.
- **And** a reserved product qualifies for exactly one modifier (e.g., stock risk).
- **When** tasks are created.
- **Then** the task `priority = min(P + 1, MAX_PRIORITY)`.

**Scenario 3: Due time derived from scheduled start**
- **Given** a work order with `scheduledStartAt = T`.
- **When** tasks are created.
- **Then** each task `dueAt = T − pickLeadTimeBuffer`.

**Scenario 4: Location suggestion prefers pick zone**
- **Given** a product is available in two locations.
- **And** one location is `isPickZone = true` with sufficient quantity.
- **When** selecting the primary suggested location.
- **Then** the pick-zone location is selected.

**Scenario 5: Partial fulfillment creates multiple tasks**
- **Given** a product requires quantity 10.
- **And** no single location has 10 on-hand.
- **When** Inventory generates tasks.
- **Then** Inventory creates multiple `PickTask`s that sum to 10.
- **And** the first task uses the primary suggested location per the deterministic hierarchy.

**Scenario 6: Missing location data triggers NeedsReview**
- **Given** a reserved product has no eligible/actionable location.
- **When** Inventory generates tasks.
- **Then** the task is created with `status = NeedsReview`.
- **And** the `PickList` remains in `Draft`.

## Audit & Observability
- **Audit trail:** Log `PickList` creation and state changes with `workOrderId`, `pickListId`, timestamps, and correlation IDs.
- **Metrics:**
  - `picklists_created_total`
  - `pick_tasks_created_total`
  - `picklist_generation_duration_seconds`
  - `pick_tasks_needs_review_total`
- **Events:** Publish `PickListCreated` when a list is finalized to `ReadyToPick`.

## Original Story (Unmodified – For Traceability)
# Issue #28 — [BACKEND] [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want a pick list so that mechanics know what to pull for a workorder.

## Details
- Pick tasks include product, qty, suggested storage locations, priority, and due time.

## Acceptance Criteria
- Pick tasks generated when reservation confirmed.
- Sorted by route/location.
- Printable or mobile view.

## Integrations
- Workexec provides workorder context; shopmgr may surface to mechanics.

## Data / Entities
- PickTask, PickList, RouteHint

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