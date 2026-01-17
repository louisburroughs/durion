Title: [BACKEND] [STORY] Putaway: Generate Put-away Tasks from Staging
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/32
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

---
**Rewrite Variant:** inventory-flexible
**Clarification Applied:** #230 (rule precedence, task granularity, assignment model, destination fallback)
---

## Story Intent
**As a** Warehouse Manager,  
**I want** the system to automatically generate put-away tasks for all items on a completed goods receipt,  
**so that** stock clerks can efficiently move items from staging to correct, system-suggested storage locations with strong auditability.

## Actors & Stakeholders
- **Primary Actor (System):** Inventory service (generates tasks).
- **Users:** Stock Clerk (claims/executes tasks), Warehouse Manager (supervises), Inventory Controller (audit/accuracy).

## Preconditions
1. A `GoodsReceipt` is successfully processed and transitions to `COMPLETED`.
2. Received items are represented as being in a known staging location.
3. Storage locations (bins/shelves/zones) exist in the storage topology.
4. Put-away rules are configured (product/category/etc) and validated at configuration time.

## Functional Behavior

### Trigger
- Automatically triggered when `GoodsReceipt` transitions to `COMPLETED`.

### System Process
1. Identify receipt line items currently in staging.
2. For each receipt line item, resolve a suggested destination storage location using put-away rules (see Business Rules).
3. Generate a `PutawayTask` for each receipt line item (default behavior) with:
   - product, quantity
   - source location (staging)
   - suggested destination location
   - reference to the source `GoodsReceipt`
   - status initialized per assignment rules
4. Publish tasks to a shared task pool for claiming/assignment.

### Assignment and Execution Model (Resolved)
- Tasks are created as **UNASSIGNED**.
- Users with permission `CLAIM_PUTAWAY_TASK` can claim and execute tasks.
- Users with `ASSIGN_PUTAWAY_TASK` can assign/reassign tasks and override claims.

### Destination Availability Handling (Resolved)
At task generation time, if the rule-suggested destination is full/unavailable/invalid:
1. Automatically find the **next-best valid location** consistent with rule precedence and compatibility constraints.
2. Record:
   - `originalSuggestedLocationId`
   - `finalSuggestedLocationId`
   - `fallbackReason` = `DESTINATION_FULL` or `UNAVAILABLE`

If **no valid location exists** that satisfies rules/constraints:
- Create the task with status `REQUIRES_LOCATION_SELECTION`
- Require a user with permission `SELECT_PUTAWAY_LOCATION` to select a destination before execution.

## Alternate / Error Flows
- **Invalid Receipt State:** Must not trigger for non-`COMPLETED` receipts.
- **Rule Conflicts:** Conflicts at the same precedence level are configuration errors and must be rejected at setup time.
- **No Matching Rule:** Use lower-precedence defaults and ultimately a system fallback location policy (“any valid location”) rather than producing a null destination.

## Business Rules

### Rule Precedence (Resolved)
Use a strict most-specific-wins hierarchy (highest → lowest):
1. Product-specific rule
2. Category-level rule
3. Supplier / receipt-type rule
4. Location default rule
5. System fallback (“any valid location”; last resort)

Higher-precedence rules override lower-precedence ones.

### Task Granularity (Resolved)
- **Default:** One receipt line item → one putaway task.
- **Optional controlled consolidation:** allowed only when all are true:
  - same `productId`
  - same `suggestedDestinationLocationId`
  - same receipt/session
  - same handling constraints (e.g., lot/expiry/serial constraints)

Explicitly disallowed:
- merging different SKUs
- merging items with different lot/expiry constraints

### Task Atomicity
Task generation for a single receipt is atomic: if any task fails to be created, roll back all tasks for that receipt.

## Data Requirements

### `PutawayTask`
- `taskId` (UUID, PK)
- `sourceReceiptId` (UUID, FK)
- `productId` (UUID, FK)
- `quantity` (Decimal)
- `sourceLocationId` (UUID, FK)
- `suggestedDestinationLocationId` (UUID, FK, nullable only when `status = REQUIRES_LOCATION_SELECTION`)
- `originalSuggestedLocationId` (UUID, FK, nullable)
- `finalSuggestedLocationId` (UUID, FK, nullable)
- `actualDestinationLocationId` (UUID, FK, nullable)
- `fallbackReason` (Enum, nullable): `DESTINATION_FULL`, `UNAVAILABLE`
- `status` (Enum): `UNASSIGNED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `REQUIRES_LOCATION_SELECTION`
- `assigneeId` (UUID, nullable)
- `createdAt`, `updatedAt` (timestamps)

### `PutawayRule`
- `ruleId` (UUID, PK)
- `priority` (Integer; lower evaluated first within same precedence tier)
- `criteria` (JSON)
- `destinationLocationId` (UUID)
- `isEnabled` (Boolean)

## Acceptance Criteria

### AC1: Successful Task Generation with Product/Category Rule
- **Given** a category rule routes Category “Electronics” to `BIN-A1`
- **And** a goods receipt line for 10 units of Product-X (Category “Electronics”) completes into staging
- **When** put-away tasks are generated
- **Then** a `PutawayTask` is created with `suggestedDestinationLocationId = BIN-A1` and `status = UNASSIGNED`.

### AC2: No Matching Specific Rule Uses Defaults/Fallback
- **Given** no product-specific or category rule matches Product-Y
- **And** a goods receipt for Product-Y completes into staging
- **When** put-away tasks are generated
- **Then** a `PutawayTask` is created with a non-null `suggestedDestinationLocationId` resolved via lower-precedence defaults/system fallback.

### AC3: Destination Unavailable Triggers Auto-Fallback
- **Given** a rule suggests destination `BIN-1`
- **And** `BIN-1` is full/unavailable at generation time
- **When** tasks are generated
- **Then** the task records `originalSuggestedLocationId = BIN-1`
- **And** assigns `finalSuggestedLocationId` to the next-best valid location
- **And** sets `fallbackReason` to `DESTINATION_FULL` or `UNAVAILABLE`.

### AC4: No Valid Location Requires Manual Selection
- **Given** no valid storage location satisfies applicable rules/constraints
- **When** tasks are generated
- **Then** the task is created with `status = REQUIRES_LOCATION_SELECTION`
- **And** a user with permission `SELECT_PUTAWAY_LOCATION` is required to resolve a destination.

### AC5: Shared Pool Self-Claim
- **Given** a task is `UNASSIGNED`
- **When** a Stock Clerk with permission `CLAIM_PUTAWAY_TASK` views available tasks
- **Then** the task is visible and can be claimed.

## Audit & Observability
- **Audit Log:** Create an immutable audit entry for every generated task linking `taskId` to `sourceReceiptId` and the applied rule (if any).
- **Logging:**
  - `INFO`: successful creation of putaway tasks for a receipt.
  - `WARN`: fallback used, manual selection required, or configuration errors.
- **Metrics:**
  - `putaway_tasks_created_total` tagged by outcome (success, fallback, requires_location_selection)
  - `putaway_generation_duration_seconds`

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
# Issue #32 — [BACKEND] [STORY] Putaway: Generate Put-away Tasks from Staging

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Putaway: Generate Put-away Tasks from Staging

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Stock Clerk**, I want put-away tasks generated so that received items are placed into proper storage locations.

## Details
- Rules: default bin by product category, manual destination.
- Tasks list product, qty, from staging, suggested destination.

## Acceptance Criteria
- Put-away tasks created after receipt.
- Suggested destinations provided.
- Tasks assignable.

## Integrations
- Uses storage topology and optional replenishment rules.

## Data / Entities
- PutawayTask, PutawayRule, StorageLocationRef

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