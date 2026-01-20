Title: [BACKEND] [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/39
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

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
**Goal:** To establish a foundational capability for defining a physical storage topology within a site, enabling precise inventory tracking and management.
**Rationale:** Accurate stock placement and retrieval depend on a well-defined and managed hierarchy of storage locations. This feature provides the necessary structure for all subsequent inventory movements and work execution tasks.

## Actors & Stakeholders
- **Actor:** `Inventory Manager` - The primary user responsible for defining, organizing, and maintaining the storage location hierarchy.
- **Actor:** `System` - The software agent responsible for enforcing business rules like uniqueness and hierarchical integrity.
- **Stakeholder:** `Work Execution System` - Consumes `storageLocationId` to direct picking, putting, and transfer tasks.
- **Stakeholder:** `Shop Manager System` - May consume location data to provide operational hints or display inventory layouts.

## Preconditions
1. The user performing the action is authenticated and authorized as an `Inventory Manager`.
2. A parent `Site` entity must exist to which the storage locations will be associated.
3. The set of valid `StorageType` values is defined and available to the system.

## Functional Behavior
### 1. Create Storage Location
- **Trigger:** The `Inventory Manager` initiates the creation of a new storage location.
- **Process:**
    1. The user provides the required attributes: `name`, `barcode`, `storageType`, and a parent `siteId`.
    2. The user may optionally provide a `parentLocationId` to place the new location within the hierarchy.
    3. The user may optionally provide attributes like `capacity` (e.g., volume, weight, unit count) and `temperature` constraints.
    4. The `System` validates the provided data against business rules (see below).
    5. Upon successful validation, the `System` persists the new `StorageLocation` record with an initial `status` of `Active`.
- **Outcome:** A new, active storage location is created and addressable within the site's hierarchy.

### 2. Update Storage Location
- **Trigger:** The `Inventory Manager` modifies an existing storage location.
- **Process:**
    1. The user selects an existing storage location to edit.
    2. The user modifies attributes such as `name`, `barcode`, `capacity`, or `temperature`.
    3. The `System` validates the changes. Note: Changing the `parentLocationId` is a valid but sensitive operation that must be checked for hierarchy cycles.
- **Outcome:** The storage location's attributes are updated.

### 3. Deactivate Storage Location
- **Trigger:** The `Inventory Manager` deactivates a storage location that is no longer in use.
- **Process:**
    1. The user selects an existing, active storage location.
    2. The user initiates the deactivation command.
    3. The `System` evaluates current stock at the location:
       - If the location is empty, proceed to deactivate.
       - If the location contains stock, the user must provide a `destinationLocationId` (within the same `Site`). The `System` will programmatically move all stock to the destination using an atomic transfer operation, then deactivate the source location. If the transfer fails, no changes are committed and the location remains `Active`.
    4. The `System` changes the location's `status` from `Active` to `Inactive` once prerequisites are satisfied.
- **Outcome:** The storage location is marked as `Inactive` and cannot be used for new inventory operations. Historical records are preserved.

## Alternate / Error Flows
- **Duplicate Barcode:** If a user attempts to create or update a location with a barcode that already exists within the same `Site`, the `System` must reject the operation and return a descriptive error.
- **Hierarchy Cycle:** If a user attempts to set a `parentLocationId` that would result in a cyclical relationship (e.g., making a location its own grandparent), the `System` must reject the operation and return an error.
- **Invalid Parent:** If the specified `parentLocationId` or `siteId` does not exist, the operation must fail with an appropriate error message.
- **Deactivation Missing Destination:** If the location contains stock and no `destinationLocationId` is provided, the operation must fail with `DESTINATION_REQUIRED`.
- **Deactivation Invalid Destination:** If `destinationLocationId` is invalid, belongs to a different `Site`, or is `Inactive`, the operation must fail with `INVALID_DESTINATION`.
- **Deactivation Transfer Failure:** If the stock transfer cannot be completed, the operation must fail and no changes are committed.

## Business Rules
1.  **Barcode Uniqueness:** A `barcode` must be unique within a given `Site`. The same barcode may exist in different sites.
2.  **Acyclic Hierarchy:** The parent-child relationships for storage locations must form a directed acyclic graph (DAG). No cycles are permitted.
3.  **Site as Root:** Every storage location must ultimately belong to a single `Site`.
4.  **Defined Types:** The `storageType` for a location must be one of the enumerated, pre-defined values: `Floor`, `Shelf`, `Bin`, `Cage`, `Yard`, `MobileTruck`, `Quarantine`.
5.  **Immutability:** The `storageLocationId` (primary key), once created, is immutable.
6.  **Deactivation Policy (Decided):** Non-empty locations cannot be deactivated without a stock transfer. The user must provide a valid `destinationLocationId` within the same `Site`. The `System` performs a transactional transfer of all stock to the destination and then deactivates the source. Orphaning stock is prohibited.

## Data Requirements
### `StorageLocation` Entity
| Field | Type | Constraints | Description |
|---|---|---|---|
| `storageLocationId` | UUID | Primary Key, Not Null, Immutable | Unique system identifier for the location. |
| `siteId` | UUID | Foreign Key, Not Null, Indexed | The site this location belongs to. |
| `parentLocationId` | UUID | Foreign Key, Nullable | The parent location in the hierarchy. Null if it's a top-level location within the site. |
| `name` | String | Not Null | Human-readable name for the location (e.g., "Aisle 5, Shelf 3"). |
| `barcode` | String | Not Null, Indexed | Barcode value for scanning. Unique per site. |
| `storageType` | Enum | Not Null | The type of location (e.g., Shelf, Bin, Cage). |
| `status` | Enum | Not Null, Default: `Active` | The current state of the location (`Active`, `Inactive`). |
| `capacity` | JSONB | Nullable | Optional field to define capacity constraints (e.g., `{ "weight_kg": 100, "volume_m3": 2.5 }`). |
| `temperature` | JSONB | Nullable | Optional field for temperature constraints (e.g., `{ "min_celsius": 2, "max_celsius": 8 }`). |
| `createdAt` | Timestamp | Not Null | Timestamp of creation. |
| `updatedAt` | Timestamp | Not Null | Timestamp of last update. |

### `DeactivateLocationRequest` (API input)
| Field | Type | Constraints | Description |
|---|---|---|---|
| `destinationLocationId` | UUID | Required if source has stock; must be `Active` and same `Site` | Stock transfer destination prior to deactivation. |

## Acceptance Criteria
### Scenario: Successfully Create a Top-Level Storage Location
- **Given** an `Inventory Manager` is logged in
- **And** a `Site` with `siteId` "S1" exists
- **When** the manager creates a new storage location with `name` "Main Floor", `barcode` "FL-01", `storageType` "Floor", and `siteId` "S1"
- **Then** the system successfully creates the new storage location
- **And** the location's `status` is `Active`
- **And** its `parentLocationId` is null.

### Scenario: Successfully Create a Child Storage Location
- **Given** a storage location "FL-01" exists within `siteId` "S1"
- **When** the manager creates a new location with `name` "Shelf A1", `barcode` "SH-A1", `storageType` "Shelf", and `parentLocationId` of "FL-01"
- **Then** the system successfully creates the new storage location
- **And** its `parentLocationId` correctly points to "FL-01".

### Scenario: Prevent Creation of a Location with a Duplicate Barcode
- **Given** a storage location with `barcode` "BIN-X99" already exists in `siteId` "S1"
- **When** the manager attempts to create another location in `siteId` "S1" with `barcode` "BIN-X99"
- **Then** the system must reject the request with a "Duplicate Barcode" error.

### Scenario: Prevent Creation of a Hierarchy Cycle
- **Given** a location hierarchy exists: "Site S1" -> "Floor-1" -> "Shelf-A" -> "Bin-A1"
- **When** the manager attempts to update "Floor-1" to have "Bin-A1" as its parent
- **Then** the system must detect a cycle and reject the update with a "Hierarchy Cycle Detected" error.

### Scenario: Successfully Deactivate an Empty Location
- **Given** an empty, `Active` storage location "Cage-03" exists
- **When** the manager deactivates "Cage-03"
- **Then** the system updates its `status` to `Inactive`.

### Scenario: Deactivate a Non-Empty Location with Transfer
- **Given** an `Active` storage location "Bin-12" contains stock
- **And** an `Active` destination location "Bin-13" exists within the same `Site`
- **When** the manager attempts to deactivate "Bin-12" and provides `destinationLocationId = Bin-13`
- **Then** the system transfers all stock from "Bin-12" to "Bin-13" atomically
- **And** the system sets "Bin-12" to `Inactive`.

### Scenario: Reject Deactivation of Non-Empty Location without Destination
- **Given** an `Active` storage location "Bin-12" contains stock
- **When** the manager attempts to deactivate "Bin-12" without providing a destination
- **Then** the system rejects the operation with `DESTINATION_REQUIRED`.

## Audit & Observability
- **Audit Trail:** All create, update, deactivate (including transfer) operations on `StorageLocation` must generate an audit log entry. The log must include the actor (`userId`), the change details (before/after), and a timestamp, plus transfer details (`source`, `destination`, `quantities`).
- **Metrics:** The system should expose metrics for the total number of storage locations per site and per type, plus `deactivation_transfers_total` and `deactivation_transfer_failures_total`.