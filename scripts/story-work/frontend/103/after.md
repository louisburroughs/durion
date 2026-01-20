## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Topology: Create/Update/Deactivate Storage Locations (Floor/Shelf/Bin/Cage/Yard/MobileTruck/Quarantine) with Site Hierarchy

### Primary Persona
Inventory Manager

### Business Value
Enables precise stock tracking and operational picking/putaway by allowing the business to define an auditable, cycle-free storage location hierarchy within each site, with barcode uniqueness enforcement and controlled deactivation.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Inventory Manager  
- **I want** to create, edit, and deactivate storage locations within a site and organize them into a parent/child hierarchy  
- **So that** inventory can be tracked and referenced accurately by storageLocationId across operational workflows (e.g., picking)

### In-scope
- Moqui/Vue/Quasar frontend screens and flows to:
  - List storage locations by site
  - Create a storage location (optionally as a child of another location)
  - Update a storage location (including changing parent, with cycle prevention)
  - Deactivate a storage location
    - If non-empty: require selecting a destination location in the same site for transfer (per backend reference behavior)
- Frontend validation and error display aligned to backend rules:
  - Barcode unique per site
  - Prevent hierarchy cycles
  - Only enumerated storage types
- Display read-only audit metadata if available via API (created/updated/by, status)

### Out-of-scope
- Rendering a graphical topology map (tree visualization beyond basic hierarchical navigation is optional and not required)
- Managing inventory quantities themselves, transfers outside deactivation workflow, cycle counts
- Workexec/Shopmgr feature changes (only ensure this UI produces/maintains storageLocationId usable by them)
- Defining new storage types in the UI (types are consumed from backend enumeration/list)

---

## 3. Actors & Stakeholders
- **Inventory Manager (primary user):** creates and maintains locations/hierarchy.
- **Warehouse/Store Staff (secondary):** may search/scan barcodes to find a location (future use; this story supports by capturing barcode).
- **Work Execution (system stakeholder):** references `storageLocationId` for pick tasks.
- **Shop Manager (system stakeholder):** may display storage hints (explicitly optional per inputs).

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend and has permission to manage storage locations.
- At least one **Site** exists and is selectable/known in navigation context.
- Backend capabilities exist (or will exist) to:
  - Create/update storage locations with validation (barcode uniqueness, no cycles)
  - Deactivate location; if non-empty require `destinationLocationId` and perform atomic transfer + deactivate
  - Provide storage type values (`Floor`, `Shelf`, `Bin`, `Cage`, `Yard`, `MobileTruck`, `Quarantine`)
- Dependency: backend story reference indicates non-empty deactivation behavior; frontend must support destination selection workflow.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Inventory module navigation: **Inventory → Topology → Storage Locations**
- Deep links:
  - `/inventory/topology/locations` (site context required or selected on page)
  - `/inventory/topology/locations/<storageLocationId>` (details/edit)

### Screens to create/modify (Moqui)
Create/extend the following screens (names may align to repo conventions; implementer to place under the inventory app):
1. **StorageLocationList** screen
   - Site selector (or uses site context from session)
   - Table/list of locations (filterable)
   - Actions: View/Edit, Create New, Deactivate
2. **StorageLocationDetail** screen
   - Read-only summary + edit form
   - Hierarchy controls (parent selection)
   - Audit/status display
3. **StorageLocationCreate** screen (may be same as detail with create mode)
4. **StorageLocationDeactivate** dialog/screen (confirm + destination selection if required)

### Navigation context
- Site context must be visible and drive list/filter and creation.
- When creating a child location from within a parent detail screen, parent is pre-selected.

### User workflows
#### Happy path: create top-level location
1. Inventory Manager opens StorageLocationList
2. Selects Site
3. Clicks “Create Location”
4. Enters required fields and saves
5. Redirect to detail screen with success message

#### Happy path: create child location
1. From parent location detail, user clicks “Add Child Location” (or Create with prefilled parent)
2. Saves
3. Child appears under same site and links back to parent

#### Happy path: update location including parent change
1. Open detail
2. Change fields (including parent)
3. Save; UI handles validation errors from backend (cycle, invalid parent, duplicate barcode)

#### Happy path: deactivate empty location
1. From detail or list, choose Deactivate
2. Confirm
3. Status becomes Inactive; location becomes non-selectable as parent/destination (except for historical display)

#### Alternate path: deactivate non-empty location (requires transfer destination)
1. User clicks Deactivate
2. Backend indicates destination required (either by pre-check endpoint or error response)
3. UI prompts to select destination location (Active, same site, not the same as source)
4. Submit; show progress and success/failure

---

## 6. Functional Behavior

### Triggers
- User visits list/detail routes
- User submits create/update form
- User triggers deactivate action

### UI actions
- **List**
  - Filter by: status (Active/Inactive/All), storageType, search (name or barcode)
  - Select site
  - Open create
- **Create/Update**
  - Form submit
  - Cancel returns to list/detail without changes
- **Deactivate**
  - Confirmation modal
  - If needed: destination selection control + submit

### State changes (frontend)
- After successful create: route to detail, show toast/banner
- After successful update: remain on detail, show success, refresh data
- After successful deactivation: update status to Inactive and disable editing actions that backend disallows

### Service interactions (Moqui)
- Screen load actions call service(s) to fetch:
  - Site list (or current site)
  - Storage types
  - Storage location list for site
  - Storage location detail including parent/site/status
- Form submit actions call create/update services
- Deactivate flow calls deactivate service (with optional destinationLocationId)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Required fields on create:
  - `siteId` (required)
  - `name` (required)
  - `barcode` (required)
  - `storageType` (required, must be one of allowed values)
- Optional fields:
  - `parentLocationId` (nullable)
  - `capacity` (optional structured input; see Data Requirements)
  - `temperature` (optional structured input; see Data Requirements)
- **Barcode uniqueness within site**
  - UI should do basic client-side check: non-empty and trim whitespace
  - Server is authoritative; display specific error returned for duplicates
- **Prevent hierarchy cycles**
  - UI should prevent selecting self as parent
  - UI should rely on backend for deep cycle detection and display returned error clearly

### Enable/disable rules
- If `status=Inactive`:
  - Disable “Deactivate” action (already inactive)
  - Disable edits that backend disallows (at minimum: prevent saving changes unless backend supports editing inactive; if unknown, allow attempt but handle 4xx gracefully)
  - Exclude inactive locations from parent picker by default (configurable; see Open Questions)
- Parent picker:
  - Must only show locations within the same site
  - Must exclude the current location and (ideally) its descendants (if API supports); otherwise rely on backend rejection

### Visibility rules
- Show destination selector only when required for deactivation of non-empty location (see Service Contracts / Error Flows)
- Show audit metadata section when provided by backend

### Error messaging expectations
- Display backend validation message next to corresponding field when possible (barcode, parent)
- For deactivation:
  - If destination required, show inline error “Destination required to deactivate non-empty location”
  - If invalid destination, show “Destination must be Active and in the same site”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `StorageLocation`
- `Site` (for selection/context)
- `AuditLog` / audit metadata (if exposed)
- `StorageType` (enumerated values or entity/list)

### StorageLocation fields
| Field | Type | Required | Default | Editable? | Notes |
|---|---|---:|---|---|---|
| storageLocationId | string/UUID | yes (system) | n/a | read-only | immutable identifier |
| siteId | string/UUID | yes | none | create-only (recommended) | changing site is not specified; treat as non-editable after create unless backend supports |
| parentLocationId | string/UUID/null | no | null | editable | must be same site; cannot form cycle |
| name | string | yes | none | editable | |
| barcode | string | yes | none | editable | unique per site |
| storageType | string enum | yes | none | create-only (recommended) | not specified if editable; if backend allows, allow edit; otherwise lock after create |
| status | enum (Active/Inactive) | yes | Active | read-only via deactivate | |
| capacity | object/json | no | null | editable | structure not fully specified |
| temperature | object/json | no | null | editable | structure not fully specified |
| createdAt/updatedAt | datetime | yes | system | read-only | display if available |
| createdBy/updatedBy | string/userId | ? | system | read-only | display if available |

### Derived/calculated fields (UI)
- `displayPath` (derived): e.g., `Floor > Shelf > Bin` for list/detail breadcrumbs if hierarchy info is available
- `isDeactivationTransferRequired` (derived from backend response or pre-check)

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/endpoints are not provided in inputs. The frontend must integrate with Moqui backend services consistent with project conventions. Where contract is unknown, the UI must be built to map standard 4xx errors to field/form messages.

### Load/view calls
- **List sites**
  - Purpose: populate site selector
  - Expected: `[{siteId, name}]`
- **List storage types**
  - Expected: `["Floor","Shelf","Bin","Cage","Yard","MobileTruck","Quarantine"]` or `{enumId, description}`
- **List storage locations by site**
  - Inputs: `siteId`, optional filters (`status`, `storageType`, `search`, pagination)
  - Expected: list of locations with minimal fields for display
- **Get storage location detail**
  - Inputs: `storageLocationId`
  - Expected: full record + parent info + audit metadata (if available)

### Create/update calls
- **Create storage location**
  - Inputs: `siteId, name, barcode, storageType, parentLocationId?, capacity?, temperature?`
  - Success: returns created `storageLocationId`
  - Errors:
    - 400 validation (duplicate barcode, invalid parent, invalid type)
    - 403 unauthorized
- **Update storage location**
  - Inputs: `storageLocationId` + editable fields
  - Errors:
    - cycle detected
    - duplicate barcode (within same site)
    - invalid parent/site mismatch

### Submit/transition calls
- **Deactivate storage location**
  - Inputs: `storageLocationId`, optional `destinationLocationId`
  - Success: status becomes Inactive
  - Errors:
    - `DESTINATION_REQUIRED` when non-empty without destination
    - `INVALID_DESTINATION` when destination invalid/inactive/different site
    - transfer failure (generic 409/500 with message)

### Error handling expectations (frontend)
- Map:
  - `400` → show validation messages; keep user inputs
  - `403` → show “You don’t have permission…” and disable actions if possible
  - `404` → show “Location not found” and route back to list
  - `409`/transfer failure → show non-field error banner; allow retry

---

## 10. State Model & Transitions

### Allowed states (StorageLocation.status)
- `Active`
- `Inactive`

### Role-based transitions
- Inventory Manager:
  - `Active` → `Inactive` via Deactivate action
- Reactivation is not specified (not in scope)

### UI behavior per state
- **Active**
  - Editable fields enabled
  - Deactivate action available
- **Inactive**
  - Display read-only
  - Deactivate hidden/disabled
  - Excluded from parent/destination pickers by default

---

## 11. Alternate / Error Flows

### Validation failures
- Duplicate barcode within site:
  - On create/update submit, backend returns field error; UI highlights barcode and shows message
- Cycle detected:
  - On parent change submit, backend rejects; UI shows message near parent selector
- Invalid parent:
  - UI shows error and does not change hierarchy display

### Concurrency conflicts
- If backend returns optimistic lock/conflict (not specified):
  - UI shows “This location was updated by someone else. Refresh and try again.”
  - Provide Refresh action

### Unauthorized access
- User without required permission:
  - List may be viewable or not (unclear); if forbidden, show access denied page
  - For create/update/deactivate, show disabled buttons if permission info is available; otherwise handle 403 on submit

### Empty states
- No locations in site:
  - Show empty state with CTA “Create first location”
- No sites:
  - Show blocking message “No sites available” (cannot proceed)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create a top-level storage location
**Given** I am logged in as an Inventory Manager  
**And** I have selected a Site `S1`  
**When** I create a storage location with name `Main Floor`, barcode `FL-01`, storageType `Floor`, and no parent  
**Then** the system saves the location with status `Active`  
**And** I am navigated to the location detail page  
**And** the detail page shows parent as empty/null and site as `S1`.

### Scenario 2: Create a child storage location under a parent
**Given** I am on the detail page for an existing location `FL-01` in site `S1`  
**When** I create a new location with name `Shelf A1`, barcode `SH-A1`, storageType `Shelf`, and parent `FL-01`  
**Then** the system saves the new location  
**And** the new location detail shows parent `FL-01`  
**And** navigating back to the list filtered by site `S1` shows both locations.

### Scenario 3: Reject duplicate barcode within the same site
**Given** a storage location exists in site `S1` with barcode `BIN-X99`  
**When** I attempt to create another storage location in site `S1` with barcode `BIN-X99`  
**Then** the UI displays a validation error on the barcode field indicating the barcode must be unique within the site  
**And** the record is not created.

### Scenario 4: Reject hierarchy changes that would create a cycle
**Given** a hierarchy exists in site `S1` where `Floor-1` is parent of `Shelf-A` and `Shelf-A` is parent of `Bin-A1`  
**When** I attempt to edit `Floor-1` and set its parent to `Bin-A1`  
**Then** the UI displays an error indicating a hierarchy cycle was detected  
**And** the parent value remains unchanged after refresh.

### Scenario 5: Deactivate an empty location
**Given** an Active storage location `Cage-03` exists and contains no stock  
**When** I deactivate `Cage-03` and confirm  
**Then** the UI shows the location status as `Inactive`  
**And** the deactivate action is no longer available.

### Scenario 6: Require destination when deactivating a non-empty location
**Given** an Active storage location `Bin-12` exists and contains stock  
**When** I deactivate `Bin-12` without selecting a destination location  
**Then** the UI shows an error that a destination is required  
**And** `Bin-12` remains `Active`.

### Scenario 7: Deactivate non-empty location with valid transfer destination
**Given** an Active storage location `Bin-12` exists and contains stock in site `S1`  
**And** an Active storage location `Bin-13` exists in the same site `S1`  
**When** I deactivate `Bin-12` and select `Bin-13` as the destination  
**Then** the UI reports success  
**And** `Bin-12` becomes `Inactive`.

---

## 13. Audit & Observability

### User-visible audit data
- On detail page, show (when provided):
  - createdAt, createdBy
  - updatedAt, updatedBy
  - status change timestamp (if available)
- For deactivation with transfer, show a confirmation success message that includes source and destination identifiers (names/barcodes) if returned.

### Status history
- If backend exposes status history/audit log entries, provide a read-only “Activity” section listing create/update/deactivate events with timestamp and actor.
- If not available, do not fabricate; show only current metadata.

### Traceability expectations
- Frontend should include correlation/request ID in console logs if available via standard API response headers (implementation-dependent).
- Ensure errors are surfaced with enough context for support: locationId, siteId (not PII).

---

## 14. Non-Functional UI Requirements

- **Performance:** List screen should support pagination and server-side filtering when available; initial load should not fetch entire hierarchy for very large sites unless required.
- **Accessibility:** Forms must be keyboard-navigable; validation errors must be announced (aria) and associated with fields.
- **Responsiveness:** Usable on tablet; critical actions (save/deactivate) must remain reachable.
- **i18n/timezone:** Display timestamps in user locale/timezone if the app supports it; otherwise display in ISO with timezone indicator. (No currency requirements.)

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty state with CTA on the list screen; qualifies as safe because it does not affect business logic; impacts UX Summary, Alternate/Empty states.
- SD-UX-PAGINATION: Default to paginated listing with simple filters (status/type/search); qualifies as safe because it is presentation-only and does not change domain rules; impacts UX Summary, Service Contracts.
- SD-ERR-HTTP-MAP: Map common HTTP statuses (400/403/404/409/500) to inline and banner errors; qualifies as safe because it follows standard error handling without inventing policy; impacts Service Contracts, Error Flows.

---

## 16. Open Questions
1. What are the exact Moqui service names / REST endpoints and response schemas for:
   - list sites, list storage types, list locations by site, get location detail, create, update, deactivate?
2. Are `capacity` and `temperature` structured JSON inputs expected to follow a defined schema (keys/units), or should the frontend treat them as freeform JSON text?
3. Can an **Inactive** storage location be edited (e.g., name/barcode fixes), or must it be fully read-only?
4. Is changing `storageType` allowed after creation, or is it immutable?
5. Should parent selection exclude **Inactive** locations strictly, or allow choosing an inactive parent for historical organization (while still preventing operational use)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/103

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/103  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Inventory Manager**, I want to define storage locations and hierarchy so that stock can be tracked precisely.

## Details  
- Types: Floor, Shelf, Bin, Cage, Yard, MobileTruck, Quarantine.  
- Parent/child relationships within a site.  
- Attributes: barcode, capacity (optional), temperature (optional).

## Acceptance Criteria  
- Create/update/deactivate storage locations.  
- Prevent cycles in hierarchy.  
- Uniqueness of barcode within site.  
- Audited.

## Integrations  
- Workexec pick tasks reference storageLocationId.  
- Shopmgr can display storage hints (optional).

## Data / Entities  
- StorageLocation, StorageType, StorageHierarchy, AuditLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Inventory Management

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript  
- Use Quasar framework for UI components  
- Integrate with Moqui Framework backend  
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API  
- TypeScript 5.x  
- Quasar v2.x  
- Moqui Framework integration

---  
*This issue was automatically created by the Durion Workspace Agent*

====================================================================================================