## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:location
- status:draft

### Recommended
- agent:location-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Pricing Scope Admin: View Sync’d Locations & Sync Logs (durion-hr → Location Replica)

### Primary Persona
System Administrator / Pricing Administrator (user in POS backoffice UI)

### Business Value
Provide a UI to verify which locations exist locally (synced from `durion-hr`) and their active/inactive status, plus the latest sync outcomes, so admins can confidently scope pricing overrides to valid locations and quickly diagnose sync issues.

---

## 2. Story Intent

### As a / I want / So that
- **As a** System Administrator / Pricing Admin  
- **I want** to view the locally synced location roster (id, name, status, optional region/tags) and the most recent sync run logs  
- **So that** I can confirm pricing scope uses valid locations and I can troubleshoot sync failures without backend access.

### In-scope
- New/updated Moqui screens to:
  - List/search/filter the locally stored synced locations (replica) used for pricing scope validation.
  - View a location detail page (read-only) including status and metadata (region/tags if available).
  - View sync logs (list + detail).
- UI handling for empty/error states and unauthorized access.
- Moqui service interactions to load locations and sync logs.

### Out-of-scope
- Implementing the actual sync job from `durion-hr` (backend story #53).
- Creating/updating locations from the UI (source-of-truth is `durion-hr`).
- Enforcing pricing override creation rules in UI flows unrelated to this roster (those belong to pricing UI stories).
- Building/altering pricing override screens (only display roster/log visibility here).

---

## 3. Actors & Stakeholders
- **Primary Actor:** System Administrator / Pricing Admin (backoffice user)
- **Secondary Actor:** Support/Operations (reads logs for troubleshooting)
- **System Stakeholders:** Location domain service (backend), Pricing domain consumers
- **Auditors:** Need traceability of sync outcomes (read-only)

---

## 4. Preconditions & Dependencies
- Backend provides endpoints/services to:
  - List and retrieve local synced `Location` records (replica).
  - List and retrieve `SyncLog` records for location sync runs.
- AuthN/AuthZ is enforced (via security domain / gateway); UI must handle 401/403.
- Data model exists in Moqui entities (or is accessible via REST) for:
  - `Location` replica with `locationId`, `name`, `status`, optional `region`, `tags`, timestamps.
  - `SyncLog` with run status and counts.

**Dependency Reference:** Backend issue `durion-positivity-backend#53` (domain:location) defines required fields and behaviors.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Backoffice navigation: **Location Management** (or **Admin → Integrations → Location Sync**), plus a cross-link from **Pricing** area (“Choose location from roster”).

### Screens to create/modify
1. **`apps/pos/screen/location/LocationList.xml`** (new)
   - List of synced locations with filters.
2. **`apps/pos/screen/location/LocationDetail.xml`** (new)
   - Read-only detail view for one location.
3. **`apps/pos/screen/location/LocationSyncLogList.xml`** (new)
   - List sync logs.
4. **`apps/pos/screen/location/LocationSyncLogDetail.xml`** (new)
   - Sync log details including counts and notes.
5. Add menu entries in the app nav (existing menu screen component).

### Navigation context
- Routes under a consistent prefix, e.g.:
  - `/location/locations` (list)
  - `/location/locations/:locationId` (detail)
  - `/location/sync-logs` (list)
  - `/location/sync-logs/:syncId` (detail)

### User workflows
- **Happy path (verify roster):**
  1. User opens Locations list.
  2. Filters by status = ACTIVE.
  3. Opens a location detail to confirm `locationId`, name, status.
- **Happy path (check sync health):**
  1. User opens Sync Logs list.
  2. Sees latest run status SUCCESS/PARTIAL_FAILURE/FAILURE.
  3. Opens log to see counts and notes.
- **Alternate paths:**
  - No locations exist yet → empty state with link to Sync Logs.
  - Latest sync failed → prominent warning banner linking to latest failure log.
  - Unauthorized → show access denied screen.

---

## 6. Functional Behavior

### Triggers
- Screen load triggers data fetch for lists and details.
- User interactions (filters, pagination, selecting a record) trigger refresh or navigation.

### UI actions
- Locations list:
  - Filter by `status` (ACTIVE/INACTIVE/ALL).
  - Search by `locationId` and/or `name` (case-insensitive).
  - Sort by `name` (default) and optionally `updatedAt`.
  - Paginate.
- Location detail:
  - Display read-only fields and timestamps.
  - Link to “View Sync Logs” (global, not per-location unless backend supports).
- Sync log list:
  - Filter by run status (SUCCESS/PARTIAL_FAILURE/FAILURE/ALL).
  - Sort by `syncStartedAt` desc (default).
  - Paginate.
- Sync log detail:
  - Display counts, status, timestamps, notes.

### State changes
- None in this frontend story (read-only UI). No create/update/deactivate actions.

### Service interactions
- Calls to Moqui services (or REST resources proxied by Moqui) for:
  - `Location` list and detail
  - `SyncLog` list and detail

---

## 7. Business Rules (Translated to UI Behavior)

- Locations are **replicated from `durion-hr`** and **not editable** in POS UI.
  - UI must render location fields read-only and omit edit actions.
- Location `status` determines eligibility for pricing scoping:
  - UI must clearly show `status` and allow filtering by it.
  - If `status != ACTIVE`, UI should display “Inactive (not eligible for new pricing overrides)” helper text (informational only; enforcement is elsewhere).
- Sync is idempotent:
  - UI must not imply manual rerun unless such a backend action exists (not in scope).
- If a location is missing from feed, it becomes INACTIVE:
  - UI should not delete/hide it by default; it should appear when status filter includes INACTIVE/ALL.

**Error messaging expectations**
- Validation errors are not expected (read-only), but API errors must be surfaced:
  - “Unable to load locations. Try again.” with technical details hidden behind expandable section if available in standard app pattern.
- Unauthorized:
  - 401 → redirect to login (if app supports)
  - 403 → show “Access denied: location management permission required.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `Location` (replica)
- `SyncLog` (sync run record)

### Fields

#### Location (replica)
- `locationId` (String; required; display; read-only)
- `name` (String; required; display; read-only)
- `status` (Enum/String; required; display; read-only; values include at least ACTIVE/INACTIVE)
- `region` (String; optional; display if present; read-only)
- `tags` (List<String> or comma-separated String; optional; display if present; read-only)
- `createdAt` (Timestamp; display optional; read-only)
- `updatedAt` (Timestamp; display optional; read-only)

#### SyncLog
- `syncId` (UUID/String; required; display; read-only)
- `syncStartedAt` (Timestamp; required; display; read-only)
- `syncFinishedAt` (Timestamp; optional if running; display; read-only)
- `status` (Enum/String: SUCCESS, PARTIAL_FAILURE, FAILURE; required)
- `recordsProcessed` (Integer; required)
- `recordsCreated` (Integer; required)
- `recordsUpdated` (Integer; required)
- `recordsSkipped` (Integer; required)
- `notes` (Text; optional)

### Read-only vs editable by state/role
- All fields in this story are **read-only** for all roles.

### Derived/calculated fields (UI-only)
- “Duration” = `syncFinishedAt - syncStartedAt` (only if both present).
- “Last sync status” badge on Locations list header (derived from most recent SyncLog if loaded).

---

## 9. Service Contracts (Frontend Perspective)

> Backend API contract details are noted as TBD in domain guide; frontend must integrate via Moqui services/resources. The exact service names/endpoints require confirmation.

### Load/view calls

**Locations list**
- Input:
  - `status` = ACTIVE|INACTIVE|ALL (default ACTIVE or ALL—see Open Question)
  - `search` (optional)
  - `pageIndex`, `pageSize`
  - `sortBy`, `sortOrder`
- Output:
  - `items: Location[]`
  - `totalCount`

**Location detail**
- Input: `locationId`
- Output: `Location`

**SyncLog list**
- Input:
  - `status` filter optional
  - `pageIndex`, `pageSize`
- Output:
  - `items: SyncLog[]`
  - `totalCount`

**SyncLog detail**
- Input: `syncId`
- Output: `SyncLog`

### Create/update calls
- None.

### Submit/transition calls
- None.

### Error handling expectations
- Network/5xx: show inline error state with retry.
- 404 on detail: show “Not found” and link back to list.
- 409 (optimistic locking) not expected in read-only flows.
- 401/403: handle per app standard (login/forbidden).

---

## 10. State Model & Transitions

### Location states
- `ACTIVE`
- `INACTIVE`
- (Potentially other backend statuses; UI must display unknown statuses as raw value with neutral styling)

### Allowed transitions
- Not performed by UI; transitions occur via sync.

### Role-based transitions
- None (read-only).

### UI behavior per state
- ACTIVE: normal display.
- INACTIVE: visually mark as inactive; show helper text “Not eligible for new pricing overrides.”
- Unknown: show as “<STATUS_VALUE>” without assumptions.

---

## 11. Alternate / Error Flows

### Validation failures
- None expected (no input forms).

### Concurrency conflicts
- If list refresh returns different data mid-session, just re-render (no optimistic locking needed).

### Unauthorized access
- 401: redirect to login (if available); otherwise show session expired.
- 403: access denied page; do not display partial data.

### Empty states
- Locations list empty:
  - Show message: “No synced locations found.”
  - Suggest checking Sync Logs.
- Sync logs empty:
  - Show message: “No sync runs recorded yet.”

### Backend unavailable
- Display error banner and retry action.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View ACTIVE locations
Given I am an authenticated user with permission to view locations  
When I navigate to the Locations list screen  
Then I see a list of synced locations with fields locationId, name, and status  
And I can filter the list to show only status "ACTIVE"  
And locations with status "INACTIVE" are not shown when the ACTIVE filter is applied

### Scenario 2: View INACTIVE locations with eligibility note
Given I am viewing the Locations list with status filter set to "INACTIVE"  
When the list loads  
Then each location row shows status "INACTIVE"  
And the UI displays an informational note that INACTIVE locations are not eligible for new pricing overrides

### Scenario 3: View location detail
Given I am on the Locations list  
When I select a location with locationId "loc-1"  
Then I am navigated to the Location detail screen for "loc-1"  
And I see locationId, name, status, and any available region/tags as read-only fields

### Scenario 4: View sync logs list and latest status
Given I am an authenticated user with permission to view sync logs  
When I navigate to the Location Sync Logs list screen  
Then I see sync runs sorted by syncStartedAt descending  
And each entry shows status and record counts (processed/created/updated/skipped)

### Scenario 5: View sync log detail
Given I am on the Sync Logs list  
When I open a sync log entry  
Then I see syncStartedAt, syncFinishedAt, status, counts, and notes (if present)

### Scenario 6: Handle backend failure
Given the backend service is unavailable  
When I load the Locations list screen  
Then I see an error state indicating locations could not be loaded  
And I can retry the request from the UI

### Scenario 7: Unauthorized access
Given I am not authenticated or lack required permission  
When I navigate to the Locations list or Sync Logs screens  
Then I am blocked from viewing the data  
And I see either a login prompt (401) or an access denied message (403)

---

## 13. Audit & Observability

### User-visible audit data
- Display `updatedAt` on location detail (and optionally in list) to show recency of sync.
- Display `syncStartedAt`/`syncFinishedAt` and `status` for each sync run.

### Status history
- Out of scope to build per-location status history unless backend provides it.
- Sync log detail provides run-level history.

### Traceability expectations
- Include `syncId` in SyncLog detail.
- If backend returns correlation/request IDs in headers, UI should log them to console/debug log per app standard (no secrets).

---

## 14. Non-Functional UI Requirements
- **Performance:** Lists must support pagination; initial load should not fetch all records without pagination.
- **Accessibility:** All interactive elements keyboard accessible; status conveyed via text not color alone.
- **Responsiveness:** Works on desktop/tablet widths (admin screens).
- **i18n/timezone:** Display timestamps in user’s locale/timezone as per app standard; do not invent location timezone conversions.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Added standard empty-state messaging and navigation links; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows.
- SD-UI-PAGINATION: Use pagination for list screens with default page size; safe for large datasets and non-domain; impacts UX Summary, Service Contracts.
- SD-ERR-RETRY: Provide retry action for transient load failures; safe error-handling pattern; impacts Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend contract:** What are the exact Moqui service names or REST endpoints for:
   - listing locations, retrieving a location by `locationId`
   - listing sync logs, retrieving a log by `syncId`?
2. **Permissions:** What permission(s)/roles gate access to these screens (e.g., `location:manage` vs read-only `location:view`)? Should pricing admins without location-manage be allowed read-only access?
3. **Default filter:** On Locations list, should default status filter be `ACTIVE` or `ALL`?
4. **Tags type:** Are `tags` delivered as an array, a CSV string, or another structure? Any max length/count constraints?
5. **Region semantics:** Is `region` a free-text field or a controlled vocabulary? Should it be filterable?
6. **Menu placement:** Where in the POS frontend navigation should these screens live (Admin, Location Management, Pricing)?  

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/117  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to sync location identifiers from durion-hr so that store pricing can be scoped to valid locations.

## Details
- Import locationId, name, status.
- Optional region/tags.

## Acceptance Criteria
- Locations present in product domain.
- Deactivated locations cannot receive new overrides.
- Sync idempotent.

## Integrations
- HR → Product location roster API/events.

## Data / Entities
- LocationRef, SyncLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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