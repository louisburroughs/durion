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

# 1. Story Header

**Title:** [FRONTEND] [STORY] Configuration: Define Default Staging and Quarantine Storage Locations for a Site

**Primary Persona:** Inventory Manager

**Business Value:** Standardize receiving topology per site by configuring authoritative default storage locations for staging and quarantine, enabling consistent downstream receiving/quality workflows.

---

# 2. Story Intent

## As a / I want / So that
- **As an** Inventory Manager  
- **I want** to configure a site’s default **Staging** and **Quarantine** storage locations  
- **So that** receiving and quality-hold processes can reliably place inventory into the correct initial storage area.

## In-scope
- A frontend configuration UI (Moqui screen + Vue/Quasar components) to:
  - View current site default staging/quarantine storage locations
  - Select/update each default location from eligible storage locations within the site
  - Validate and submit changes
- Enforce the business rule that staging and quarantine defaults **must be distinct**
- Display current configuration and last-updated metadata (if available from backend)

## Out-of-scope
- Receiving execution behavior (“receiving uses staging by default”) — handled by separate stories
- Permission model and UI flows for moving inventory **out of** quarantine — separate inventory/security execution stories
- CRUD for Storage Locations themselves (creation/editing) — separate location/inventory stories depending on SoR
- Cross-site transfers, hierarchy edits, or barcode scanning flows

---

# 3. Actors & Stakeholders

## Actors
- **Inventory Manager (primary):** configures defaults per site.

## Stakeholders
- **Receiving / Work Execution workflows:** consume this configuration downstream.
- **Audit/Compliance:** expects traceability of configuration changes.
- **Location Operations:** relies on correct site topology.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in the frontend.
- The system has:
  - A **Site** (a Location acting as a site) with a unique `siteId`
  - **Storage Locations** that belong to a site

## Dependencies
- Backend capability to:
  - Retrieve a site’s current default location configuration
  - List storage locations for a given site (for picker options)
  - Update the site’s default staging/quarantine storage location IDs
- Authorization enforcement exists server-side (frontend must handle 401/403)
- Backend enforces:
  - storage location belongs to site
  - staging != quarantine (error code `DEFAULT_LOCATION_ROLE_CONFLICT`)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Site/Location administration context:
  - “Site Details” → “Default Locations” (new sub-screen), or
  - “Inventory Topology” → select Site → configure defaults

## Screens to create/modify (Moqui)
- **Create:** `apps/pos/screen/location/SiteDefaults.xml` (name/path illustrative)
  - A screen for viewing and editing `defaultStagingLocationId` and `defaultQuarantineLocationId`
- **Modify:** Site details screen/navigation to include link/tab to “Default Locations”

## Navigation context
- URL pattern (Moqui screen path):  
  - `/location/siteDefaults?siteId=<id>` (or nested under site detail: `/location/siteDetail?siteId=...#defaults`)
- Breadcrumb: Locations → Site → Default Locations

## User workflows
### Happy path
1. Inventory Manager opens Site Default Locations screen for a site.
2. Screen loads current defaults and eligible storage locations for that site.
3. User selects a staging location and a quarantine location (must be different).
4. User clicks **Save**.
5. UI shows success confirmation and updated values.

### Alternate paths
- User selects the same location for both fields → client-side validation blocks Save with clear message.
- Backend rejects due to role conflict / wrong site → show server validation error inline and preserve user selections.
- Unauthorized (403) → show permission error and disable editing.

---

# 6. Functional Behavior

## Triggers
- Navigating to the Site Default Locations screen with `siteId`
- Clicking **Save** to update defaults

## UI actions
- Load:
  - Fetch site info (name/code) for context (if endpoint exists)
  - Fetch current default IDs for staging/quarantine
  - Fetch list of storage locations belonging to the site for selection controls
- Edit:
  - Two selection controls:
    - Default Staging Location
    - Default Quarantine Location
  - Optional: a “Clear” action is **not allowed** because defaults are required (see Open Questions if backend allows nulls)
- Save:
  - Submit update to backend with both IDs
  - Display success toast/banner
  - Refresh displayed configuration from backend response (or re-GET)

## State changes (frontend)
- Local form state:
  - `selectedDefaultStagingLocationId`
  - `selectedDefaultQuarantineLocationId`
  - `isDirty`, `isSaving`
- No domain state machine in frontend beyond edit/view mode.

## Service interactions (Moqui)
- Use Moqui `transition` actions to call services for:
  - Load current configuration + options
  - Persist update

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Required fields:** both staging and quarantine must be selected (non-null).
- **Distinctness:** staging location ID must not equal quarantine location ID.
- **Belongs-to-site:** frontend filters picker options to locations for the current site; still rely on backend validation.

## Enable/disable rules
- Save button enabled only when:
  - both fields selected
  - staging != quarantine
  - user has edit permission (or backend indicates editable)
- During save: disable inputs and show loading state.

## Visibility rules
- Always show current values (or “Not configured” if backend returns null; see Open Questions because backend story says “exactly one” each)
- If user lacks permission:
  - show read-only view of current defaults (if allowed)
  - show message “You do not have permission to update site defaults.”

## Error messaging expectations
- `DEFAULT_LOCATION_ROLE_CONFLICT`: “Staging and quarantine locations must be different.”
- `401`: prompt re-authentication (standard app behavior)
- `403`: “You do not have permission to update default locations for this site.”
- `404` site not found: “Site not found or you no longer have access.”
- Validation `400/422`: surface field-level errors if backend provides them; otherwise show general error banner.

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- **Site/Location** (SoR: Location domain)
  - `siteId` (UUID)
  - `code` (string) (display)
  - `name` (string) (display)
  - `defaultStagingLocationId` (UUID)
  - `defaultQuarantineLocationId` (UUID)
- **StorageLocation** (SoR: Inventory domain or Location/Inventory shared; unclear in provided inputs)
  - `storageLocationId` (UUID)
  - `siteId` (UUID)
  - `name` (string)
  - `barcode` (string, optional display)
  - `status` (ACTIVE/INACTIVE) (for filtering eligibility)

## Fields (type, required, defaults)
- `defaultStagingLocationId: UUID` (required)
- `defaultQuarantineLocationId: UUID` (required)

## Read-only vs editable (by state/role)
- Editable only for users with appropriate permission (exact permission string is not defined for this configuration story; see Open Questions)
- Read-only for users lacking permission.

## Derived/calculated fields
- Display label for a storage location: `name` plus optional `barcode` for disambiguation.

---

# 9. Service Contracts (Frontend Perspective)

> Backend API paths are referenced from backend story #38 as examples. Frontend must align to actual implemented endpoints.

## Load/view calls
1. **Get current site defaults**
   - `GET /api/v1/sites/{siteId}` (if includes defaults) **OR**
   - `GET /api/v1/sites/{siteId}/default-locations` (preferred explicit contract)
   - Response must include:
     - `defaultStagingLocationId`
     - `defaultQuarantineLocationId`

2. **List eligible storage locations for site**
   - `GET /api/v1/sites/{siteId}/storage-locations?status=ACTIVE` (example)
   - Response list items: `{ storageLocationId, name, barcode?, status }`

## Create/update calls
- **Update site default locations**
  - `PUT /api/v1/sites/{siteId}/default-locations`
  - Request:
    ```json
    {
      "defaultStagingLocationId": "<uuid>",
      "defaultQuarantineLocationId": "<uuid>"
    }
    ```
  - Success: `200 OK` with updated config (or `204 No Content`)

## Submit/transition calls
- Moqui `transition` for Save triggers service call that wraps the HTTP call (or direct service if Moqui is SoR).

## Error handling expectations
- `400/422` with field errors and/or `errorCode`
- Specific code to map:
  - `DEFAULT_LOCATION_ROLE_CONFLICT` → field-level error on both selectors
- `403` forbidden
- `404` site not found

---

# 10. State Model & Transitions

## Allowed states
- Screen state only:
  - `viewing`
  - `editing` (form dirty)
  - `saving`
  - `error`

## Role-based transitions
- Only permitted users can transition `editing → saving`.
- Unauthorized users remain `viewing` only.

## UI behavior per state
- Viewing: show current defaults; show Edit controls enabled if authorized.
- Editing: show selectors active; Save enabled when valid.
- Saving: disable inputs, show spinner/progress.
- Error: show banner; keep form state for correction.

---

# 11. Alternate / Error Flows

## Validation failures
- Same staging/quarantine selected:
  - Block Save; show inline message.
- Missing selection:
  - Block Save; show required indicator.

## Concurrency conflicts
- If backend uses optimistic locking and returns `409 Conflict`:
  - Show message: “This configuration was updated by someone else. Reload and try again.”
  - Provide “Reload” action.

## Unauthorized access
- `401`: route to login per app standard, then return to same screen.
- `403`: show read-only with permission warning; do not allow save.

## Empty states
- No active storage locations returned:
  - Show empty state: “No active storage locations exist for this site. Create one before configuring defaults.”
  - Provide navigation link to storage locations list (if available).

---

# 12. Acceptance Criteria

## Scenario 1: View current defaults
**Given** I am an authenticated user  
**And** Site “WH-1” exists  
**When** I navigate to the Site Default Locations screen for “WH-1”  
**Then** I see the current Default Staging Location and Default Quarantine Location displayed  
**And** I see selection controls populated with storage locations that belong to “WH-1”.

## Scenario 2: Update defaults successfully
**Given** I am authenticated as an Inventory Manager (authorized to update site defaults)  
**And** Site “WH-1” has storage locations “STAGING-A” and “QUARANTINE-B”  
**When** I set Default Staging Location to “STAGING-A”  
**And** I set Default Quarantine Location to “QUARANTINE-B”  
**And** I click Save  
**Then** the frontend sends `PUT /api/v1/sites/{siteId}/default-locations` with both IDs  
**And** I see a success confirmation  
**And** reloading the screen shows the saved defaults.

## Scenario 3: Client-side reject duplicate role assignment
**Given** I am authorized to update site defaults  
**And** “COMMON-AREA” is a storage location in site “WH-1”  
**When** I select “COMMON-AREA” for both staging and quarantine  
**Then** the Save action is disabled (or blocked)  
**And** I see an inline validation message stating the locations must be distinct  
**And** no update request is sent.

## Scenario 4: Server-side reject duplicate role assignment (defense in depth)
**Given** I am authorized to update site defaults  
**And** I submit an update where staging and quarantine IDs are the same (via any client manipulation)  
**When** the backend responds with `400` and error code `DEFAULT_LOCATION_ROLE_CONFLICT`  
**Then** the frontend shows an inline error on the selectors  
**And** the message states staging and quarantine must be distinct.

## Scenario 5: Reject unauthorized update
**Given** I am an authenticated user without permission to update site defaults  
**When** I attempt to Save changes to default staging/quarantine locations  
**Then** the backend responds with `403 Forbidden`  
**And** the frontend displays a permission error  
**And** the configuration remains unchanged in the UI after reload.

## Scenario 6: Reject selecting a storage location from another site
**Given** I am authorized to update site defaults  
**And** storage location “STAGING-X” belongs to site “WH-2”  
**When** I attempt to set “STAGING-X” as the default staging location for site “WH-1”  
**Then** the backend responds with `400` or `422` indicating the storage location does not belong to the site  
**And** the frontend displays the error and preserves my current selections for correction.

---

# 13. Audit & Observability

## User-visible audit data
- Display (if available from backend):
  - “Last updated at”
  - “Last updated by”
- If not available, omit without guessing.

## Status history
- Not applicable as a state machine; configuration history is backend/audit-driven.

## Traceability expectations
- Frontend logs (console/logger) should include:
  - siteId
  - correlation/request ID if exposed by HTTP layer
  - outcome (success/failure) without leaking sensitive data

---

# 14. Non-Functional UI Requirements

- **Performance:** initial load should complete with at most 2 network calls (defaults + storage locations). Avoid N+1.
- **Accessibility:** selectors and validation messages must be screen-reader accessible; keyboard navigation supported.
- **Responsiveness:** usable on tablet widths; avoid overflow in long location names.
- **i18n:** all labels/messages use translation keys (no hardcoded strings).
- **Timezone:** display timestamps in site timezone only if backend provides timezone context; otherwise use user’s locale (see Open Questions).

---

# 15. Applied Safe Defaults

- **SD-UI-EMPTY-STATE-001**: Provide a standard empty state when no eligible storage locations exist; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Alternate / Error Flows.
- **SD-UI-LOADING-STATE-001**: Disable inputs and show loading indicator during save/load to prevent double-submit; safe UX ergonomics. Impacted sections: Functional Behavior, State Model.
- **SD-ERR-MAP-001**: Map common HTTP statuses (401/403/404/409/422) to standard UI banners and inline errors when field-level info is present; safe because it does not alter domain policy. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **Domain label conflict:** This story configures *site default locations* (Location domain) but uses *storage locations* (Inventory domain). Confirm the frontend story should be owned by **domain:location** (configuration SoR) and not domain:inventory. If not, specify the correct single domain label.
2. **Backend endpoints:** What are the exact API endpoints and response shapes for:
   - fetching current defaults
   - listing storage locations for a site
   - updating defaults  
   The backend story provides examples but not final contracts.
3. **Authorization:** What permission(s) gate updating site default locations (e.g., `location:manage`, `inventory:topology.manage`)? Frontend needs to know whether to:
   - hide/disable Save preemptively, or
   - allow attempt and rely on 403 only.
4. **Null handling:** Backend rules say each site MUST have exactly one staging and one quarantine default. Can existing sites have nulls during rollout/migration? If yes, how should UI behave (require setting both before leaving screen? block other workflows?).
5. **Eligibility filtering:** Should inactive storage locations be selectable for defaults? (Location domain guide mentions statuses for locations/bays; inventory guide mentions location deactivation constraints, but not picker rules.)
6. **Audit metadata:** Does the backend expose `updatedAt`/`updatedBy` for this configuration so the frontend can display it?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Topology: Define Default Staging and Quarantine Locations for Receiving — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/102

Labels: frontend, story-implementation, user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Inventory Manager**, I want default receiving staging and quarantine locations so that receiving workflows are consistent.

## Details  
- Each site can define staging and quarantine locations.  
- Quarantine requires approval to move into available stock.

## Acceptance Criteria  
- Staging/quarantine configured per location.  
- Receiving uses staging by default.  
- Quarantine moves require permission.

## Integrations  
- Distributor receiving may land in staging; quality hold uses quarantine.

## Data / Entities  
- ReceivingPolicy, StorageLocationRef, PermissionCheck

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Inventory Management

### Frontend Requirements
- Implement Vue.js 3 components with TypeScript  
- Use Quasar framework for UI components  
- Integrate with Moqui Framework backend  
- Ensure responsive design and accessibility