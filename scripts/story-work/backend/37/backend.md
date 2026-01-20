Title: [BACKEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/37
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

---
**Rewrite Variant:** inventory-flexible
**Clarification Applied:** #234 (permissions model for ADJUST)
---

## Story Intent
**As an** Inventory Manager,  
**I want** all stock movements to be recorded as immutable transactions in an inventory ledger,  
**so that** on-hand quantity for any product is fully auditable, traceable, and explainable.

## Actors & Stakeholders
- **Inventory Manager (User Persona):** Primary user who needs to trust and audit inventory levels.
- **System (Actor):** Automated processes (Receiving, Work Execution, etc.) that trigger stock movements.
- **Service Advisor / Parts Counter Staff (User Persona):** Indirect users who consume inventory data and may initiate movements.
- **Auditor (Stakeholder):** Relies on ledger integrity for audits.
- **Work Execution System (Consumer):** Queries movement history for work order lines.

## Preconditions
1. A `Product` master record exists in Product Catalog.
2. Storage locations (bins, shelves, virtual locations) are defined in Location service.
3. The actor (user or system) initiating the movement is authenticated and identifiable.

## Functional Behavior

### 1. Ledger Entry Creation on Stock Movement
When any recognized stock movement event occurs, the system MUST create a new, immutable entry in the `InventoryLedger`. This action is append-only.

The following events MUST generate a ledger entry:
- **Receive:** Supplier receipt increases on-hand at a receiving location.
- **PutAway:** Move from receiving to final storage.
- **Pick:** Move from storage to staging/work-order location.
- **Issue/Consume:** Issue to work order / sale decreases on-hand.
- **Return:** Return increases on-hand at a returns location.
- **Transfer:** Move between two internal storage locations.
- **Adjust (posted):** A manual correction is posted to the ledger (up or down) after required authorization.

### 2. Adjustment (ADJUST) Authorization Flow (Resolved)
Adjustments are handled as a **two-step capability**:
1. **Create adjustment request** (draft/pending): requires permission `INVENTORY_ADJUST_CREATE`.
2. **Approve and post adjustment** (creates the ledger entry): requires permission `INVENTORY_ADJUST_APPROVE`.

Optional simplification (if the service chooses not to separate approve vs post): `INVENTORY_ADJUST_POST` may be used to collapse approve+post into a single step.

Permissions MUST be **scope-aware** (typical: location-scoped; optionally global).

## Alternate / Error Flows
1. **Invalid Product:** Reject with `PRODUCT_NOT_FOUND`; no ledger entry created.
2. **Invalid Location:** Reject with `LOCATION_NOT_FOUND`.
3. **Insufficient Quantity:** For decreasing movements (Pick, Issue, Transfer, negative Adjust), reject with `INSUFFICIENT_STOCK`.
4. **Permission Denied (Adjustment):**
   - Creating an adjustment request without `INVENTORY_ADJUST_CREATE` -> reject `PERMISSION_DENIED`.
   - Approving/posting without `INVENTORY_ADJUST_APPROVE` -> reject `PERMISSION_DENIED`.
5. **Missing Reason for Adjustment:** If an adjustment is created without `reasonCode`, reject with `REASON_CODE_REQUIRED`.

## Business Rules
1. **Immutability:** Ledger entries cannot be modified or deleted. Corrections are made via a new counteracting adjustment.
2. **Atomicity:** Stock movement and corresponding ledger entry creation MUST be atomic.
3. **On-Hand Calculation:** On-hand is computed by summing all `quantityChange` values for a product-location pair.
4. **Adjustment Policy:** Adjustments MUST include `reasonCode` (enum) and audit fields.
5. **Adjustment Authorization (Resolved):** Authorization is **permission-based**, not a single hard-coded role check.

## Data Requirements

### `InventoryLedgerEntry`
| Field | Type | Description | Constraints | Example |
| :--- | :--- | :--- | :--- | :--- |
| `ledgerEntryId` | UUID | Unique identifier for the ledger entry. | PK, Not Null | `a1b2c3d4-e5f6-7890-1234-567890abcdef` |
| `productId` | UUID | Foreign key to Product Catalog. | Not Null | `prod-98765` |
| `timestamp` | Timestamp UTC | When movement occurred. | Not Null, Indexed | `2024-05-21T14:30:00Z` |
| `movementType` | Enum | Stock movement type. | Not Null | `RECEIVE` |
| `quantityChange` | Decimal | Quantity moved. Positive=increase; negative=decrease. | Not Null | `10.00` |
| `unitOfMeasure` | String | UOM for `quantityChange`. | Not Null | `EA` |
| `fromLocationId` | String | Source location. | Nullable | `RECEIVING-DOCK-A` |
| `toLocationId` | String | Destination location. | Nullable | `BIN-A1-03` |
| `actorId` | String | User/system initiating the movement. | Not Null | `user-jane-doe` / `system-wms` |
| `reasonCode` | Enum | Required for adjustment requests and posted adjustments. | Nullable for non-adjust | `CYCLE_COUNT_CORRECTION` |
| `sourceTransactionId` | String | Link to source transaction (PO/WO/etc). | Nullable | `PO-12345` |

### Supporting Enums
- **`MovementType`**: `RECEIVE`, `PUT_AWAY`, `PICK`, `ISSUE`, `RETURN`, `TRANSFER`, `ADJUST`
- **`ReasonCode`** (non-exhaustive): `CYCLE_COUNT_CORRECTION`, `DAMAGED_GOODS`, `STOCK_FOUND`, `THEFT`

### Authorization Model (Resolved)
- Use **granular permissions** bundled into roles (do not hard-code a single "Inventory Manager" role check).
- Minimum v1 permissions:
  - `INVENTORY_ADJUST_CREATE`
  - `INVENTORY_ADJUST_APPROVE`
  - (`INVENTORY_ADJUST_POST` optional if approve+post are collapsed)
- Scope: `LOCATION` scope is typical; allow `GLOBAL` where applicable.

## Acceptance Criteria

**Scenario 1: Receiving New Stock**
- **Given** Product `SKU-123` exists
- **And** location `RCV-01` exists
- **When** the System processes a `RECEIVE` event for 50 units of `SKU-123` to location `RCV-01` from Purchase Order `PO-555`
- **Then** an `InventoryLedgerEntry` is created with:
  - `productId` = `SKU-123`
  - `movementType` = `RECEIVE`
  - `quantityChange` = `+50`
  - `toLocationId` = `RCV-01`
  - `fromLocationId` = `null`
  - `sourceTransactionId` = `PO-555`

**Scenario 2: Transferring Stock Internally**
- **Given** Product `SKU-123` has on-hand 50 at location `RCV-01`
- **And** location `BIN-C4` exists
- **When** an authorized user initiates a `TRANSFER` of 20 units of `SKU-123` from `RCV-01` to `BIN-C4`
- **Then** an `InventoryLedgerEntry` is created with:
  - `movementType` = `TRANSFER`
  - `quantityChange` reflecting the movement (implementation may model as one entry with both locations or two entries)
  - `fromLocationId` = `RCV-01`
  - `toLocationId` = `BIN-C4`

**Scenario 3: Creating an Adjustment Request (Authorized)**
- **Given** a user with permission `INVENTORY_ADJUST_CREATE` is logged in
- **When** they create an adjustment request for product `SKU-456` at location `SHELF-B2` to decrease stock by 2 units with reason `DAMAGED_GOODS`
- **Then** the request is accepted
- **And** `reasonCode` is required and stored

**Scenario 4: Approving/Posting an Adjustment (Authorized)**
- **Given** a pending adjustment request exists
- **And** a user with permission `INVENTORY_ADJUST_APPROVE` is logged in
- **When** they approve and post the adjustment
- **Then** an `InventoryLedgerEntry` is created with:
  - `movementType` = `ADJUST`
  - `quantityChange` = `-2`
  - `fromLocationId` = `SHELF-B2`
  - `toLocationId` = `null`
  - `reasonCode` = `DAMAGED_GOODS`

**Scenario 5: Unauthorized Adjustment Attempt**
- **Given** a user without `INVENTORY_ADJUST_CREATE` is logged in
- **When** they attempt to create an adjustment request
- **Then** the system rejects with `PERMISSION_DENIED`
- **And** no ledger entry is created.

**Scenario 6: Reconstructing On-Hand Quantity**
- **Given** ledger entries exist for product `SKU-789` at location `BIN-A1`:
  - `RECEIVE`, `+100`
  - `PICK`, `-10`
  - `ADJUST`, `+1` (reason `STOCK_FOUND`)
- **When** the system queries on-hand for `SKU-789` at `BIN-A1`
- **Then** the calculated on-hand MUST be `91`.

## Audit & Observability
- **Audit Trail:** Every `InventoryLedgerEntry` is an audit record.
- **Logging:**
  - Successful ledger entry creation: `INFO` with `ledgerEntryId`.
  - Failed attempts (insufficient stock, invalid data, permissions): `WARN` including actor, product, locations, reason.
- **Metrics:** Count ledger entries created per `movementType`.
- **Adjustment audit fields (required):** `requestedBy`, `approvedBy`, timestamps, and `policyVersion`.

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
# Issue #37 — [BACKEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Ledger: Record Stock Movements in Inventory Ledger

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want all stock movements recorded in a ledger so that on-hand is auditable and explainable.

## Details
- Movement types: Receive, PutAway, Pick, Issue/Consume, Return, Transfer, Adjust.
- Capture productId, qty, UOM, from/to storage, actor, timestamp, reason.

## Acceptance Criteria
- Every movement creates a ledger entry.
- Ledger is append-only.
- Can reconstruct on-hand by replay.
- Adjustments require reason and permission.

## Integrations
- Workexec can query movement history for a workorder line.

## Data / Entities
- InventoryLedgerEntry, MovementType, ReasonCode, AuditLog

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