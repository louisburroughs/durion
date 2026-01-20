## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:location
- status:draft

### Recommended
- agent:location-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Locations: Create and Maintain Shop Locations

### Primary Persona
Admin (Location Manager)

### Business Value
Ensure scheduling, appointments, and downstream operational rules use accurate per-site location metadata (address, timezone, operating hours, holiday closures, and buffers), with auditable changes.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** to create, view, edit, and deactivate shop locations (including address, timezone, operating hours, holiday closures, and optional buffer overrides)  
- **So that** systems that schedule and execute work can apply correct per-site rules and context.

### In-scope
- Moqui/Vue/Quasar UI to:
  - List locations with status filtering (ACTIVE default).
  - Create a location (status defaults to ACTIVE).
  - View a location’s details.
  - Edit location details with validation feedback.
  - Soft-deactivate a location (ACTIVE → INACTIVE).
  - Display audit/status metadata (created/updated/version; and if exposed, audit events).
- Frontend integration with Moqui backend endpoints for Locations CRUD and status update.
- Client-side validation that mirrors backend constraints (without replacing server validation).

### Out-of-scope
- Enforcing cross-domain rules (e.g., preventing staffing assignments to inactive locations, workexec behaviors).
- Distance/travel time computations.
- Any location hierarchy behaviors beyond selecting an optional parent (no cascading rules invented).
- Sync/import flows from external authoritative sources (SyncLog).

---

## 3. Actors & Stakeholders
- **Admin (Primary user):** creates/updates/deactivates locations.
- **Ops Manager / Scheduler (Consumer stakeholder):** relies on accurate timezone/hours/closures and buffers.
- **Downstream systems (Consumers):** Work Execution, HR availability (read-only consumers; not implemented here).
- **Auditor / Compliance stakeholder:** expects traceable change history.

---

## 4. Preconditions & Dependencies
- Admin is authenticated in the frontend session.
- Admin has authorization to manage locations (permission name enforced by backend/security domain; frontend should gracefully handle 403).
- Backend APIs exist (per reference backend story #78):
  - `POST /locations`
  - `GET /locations/{locationId}`
  - `GET /locations?status=ACTIVE|INACTIVE|ALL` (default ACTIVE)
  - `PUT /locations/{locationId}`
  - `PATCH /locations/{locationId}` (used for deactivation via `{status:"INACTIVE"}`)
- Backend returns structured error responses for validation and conflicts:
  - 400 with `code` such as `INVALID_TIMEZONE`, `INVALID_OPERATING_HOURS`
  - 409 with `LOCATION_NAME_TAKEN`, `OPTIMISTIC_LOCK_FAILED`
- Location resource includes `version` for optimistic locking.
- UI routes/screen tree exists for admin configuration area (exact route path TBD by repo conventions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation: **Administration → Locations** (or equivalent admin menu).
- Deep links:
  - Locations list
  - Location create
  - Location detail/edit

### Screens to create/modify (Moqui screens + Vue components)
Create/extend a Moqui screen set (names illustrative; align with repo conventions):
- `LocationList` screen
- `LocationCreate` screen
- `LocationDetailEdit` screen (view + edit in one, or separate view/edit screens)

Each screen should render Vue 3 + Quasar components and use Moqui transitions/actions for service calls.

### Navigation context
- Breadcrumbs: Admin → Locations → (Create | {Location Name})
- After create: navigate to detail screen for newly created location.
- After deactivate: remain on detail screen with status shown as INACTIVE; provide link back to list.

### User workflows
**Happy paths**
1. List → Create → Save → View details
2. List → Select location → Edit → Save → View updated values
3. Detail → Deactivate → Confirm → Status becomes INACTIVE

**Alternate paths**
- Attempt to save with invalid timezone/hours → show field-level errors and preserve entered data.
- Attempt to rename to a duplicate name → show conflict error tied to name field.
- Concurrent update causes optimistic lock failure → prompt user to reload latest and re-apply changes.

---

## 6. Functional Behavior

### Triggers
- Entering list screen triggers load of locations (default `status=ACTIVE`).
- Entering detail screen triggers load of selected location by `locationId`.
- Create/Save/Deactivate triggers API calls and UI state updates.

### UI actions
**Locations list**
- Status filter control: `ACTIVE | INACTIVE | ALL` (default ACTIVE).
- Table/list rows show at minimum: Name, Status, Timezone, UpdatedAt (if available).
- Row click navigates to detail.

**Create location**
- Form fields (see Data Requirements).
- Save action disabled while request in-flight.
- On success: show success notification and route to detail.

**Edit location**
- Load existing values into form.
- `code` is immutable per domain rules; if present in resource, show read-only (or omit if not part of resource).
- Save calls PUT for full resource update (preferred for simplicity) including `version`.

**Deactivate**
- Button visible/enabled only when status is ACTIVE.
- Confirmation dialog required.
- Calls PATCH `{status:"INACTIVE", version:<current>}` (include version to avoid lost updates).
- On success: update UI status and disable editing controls as appropriate (see state behavior).

### State changes
- `status` transitions only: ACTIVE → INACTIVE (no reactivation implied).
- `version` increments on successful update (frontend stores latest returned resource).

### Service interactions
- List: GET locations with status filter.
- Detail: GET location by id.
- Create: POST location payload.
- Update: PUT location payload with `locationId` and `version`.
- Deactivate: PATCH location status with `version`.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side mirrors server rules)
- **Name**
  - Required, trimmed.
  - UI should trim leading/trailing whitespace before submit.
  - Uniqueness enforced by backend; on `LOCATION_NAME_TAKEN`, show inline error on name.
- **Timezone**
  - Required.
  - Input must be an IANA timezone ID; UI should provide:
    - A searchable select list of common IANA zones (preferred) OR free-text with validation hint.
  - On backend `INVALID_TIMEZONE`, show inline error on timezone.
- **Operating hours**
  - Local times (`HH:mm`) in the location timezone.
  - Closed days are represented by omitting that day from the list.
  - No duplicate day entries.
  - No overnight ranges; must satisfy `open < close`.
  - On backend `INVALID_OPERATING_HOURS`, show error associated to operating hours editor with specifics if provided.
- **Holiday closures**
  - Array of `{date: YYYY-MM-DD, reason?}`.
  - No duplicate dates; UI should prevent duplicates client-side.
- **Buffers**
  - `checkInBufferMinutes`, `cleanupBufferMinutes` are nullable integers, `>= 0`.
  - UI should allow blank (meaning “use global default”) and non-negative integer entry.
  - If user enters negative or non-integer, show inline validation error.

### Enable/disable rules
- Disable Save while network request in-flight.
- If location status is INACTIVE:
  - Deactivate button hidden/disabled.
  - Editing is **read-only** by default (see Open Questions; backend story doesn’t state if edits to INACTIVE are allowed—treat as read-only until clarified).

### Visibility rules
- Show status badge (ACTIVE/INACTIVE) prominently in detail header.
- Show created/updated timestamps and version (read-only metadata) if returned.

### Error messaging expectations
- For 400: show field-level errors where `field` is provided; otherwise show form-level banner with error `message`.
- For 409:
  - `LOCATION_NAME_TAKEN`: inline on name.
  - `OPTIMISTIC_LOCK_FAILED`: modal/banner explaining someone else updated it; offer Reload.

---

## 8. Data Requirements

### Entities involved (frontend-consumed resources)
- **Location** (primary)
- **OperatingHours** (embedded array or separate resource; treat as embedded per backend story JSONB)
- **AuditLog/Audit events** (read-only display if API exists; otherwise show metadata available on Location)

### Fields (type, required, defaults)

**Location (create/edit)**
- `locationId` (UUID, read-only; required for edit)
- `name` (string, required)
- `status` (enum: `ACTIVE|INACTIVE`, read-only except via deactivate action; defaults ACTIVE on create)
- `address` (object, required)
  - **Open question**: exact schema. At minimum UI should collect standard postal address fields (see Open Questions).
- `timezone` (string, required; IANA)
- `operatingHours` (array, required; empty allowed)
  - Entry shape: `{ dayOfWeek: MON|TUE|WED|THU|FRI|SAT|SUN, open: "HH:mm", close: "HH:mm" }`
- `holidayClosures` (array, optional/nullable)
  - Entry shape: `{ date: "YYYY-MM-DD", reason?: string }`
- `checkInBufferMinutes` (integer, nullable, >=0)
- `cleanupBufferMinutes` (integer, nullable, >=0)
- `version` (integer/long, required for updates; read-only display but included in update payload)
- `createdAt` (timestamp, read-only)
- `updatedAt` (timestamp, read-only)

### Read-only vs editable by state/role
- Role: Admin can create and edit ACTIVE locations.
- `code` (if exists): read-only always (immutable).
- INACTIVE locations: read-only in UI until clarified (no reactivation, no edits assumed).

### Derived/calculated fields
- Display-only “Effective buffers” are **not** calculated in frontend (global defaults not provided in this story); show “Uses global default” when buffer override is null.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Implement via Moqui transitions/actions calling backend endpoints. Exact service names depend on repo conventions; define them in-screen as `service-call`/`rest-call` style as appropriate.

### Load/view calls
- **List**
  - `GET /locations?status=<ACTIVE|INACTIVE|ALL>`
  - Success: 200 with array of Location summaries or full resources.
- **Detail**
  - `GET /locations/{locationId}`
  - Success: 200 with full Location
  - 404: show “Location not found” and link back to list.

### Create/update calls
- **Create**
  - `POST /locations`
  - Request body includes: name, address, timezone, operatingHours, holidayClosures (optional), buffers (nullable)
  - Success: 201 with created Location (including `locationId`, `version`, status)
- **Update**
  - `PUT /locations/{locationId}` (full update)
  - Request body includes full Location editable fields plus `version`
  - Success: 200 with updated Location (preferred) or no body (if no body, refetch)

### Submit/transition calls
- **Deactivate**
  - `PATCH /locations/{locationId}` with `{ status: "INACTIVE", version: <current> }`
  - Success: 200 with updated Location (preferred) or no body (refetch)

### Error handling expectations (UI mapping)
- 400: render validation errors; preserve form state.
- 403: show “Not authorized to manage locations” and disable editing actions.
- 404: detail screen not found state.
- 409:
  - name taken → mark name invalid
  - optimistic lock → offer reload; if user accepts, refetch and show diff notice (minimal: “Your changes were not saved”).

---

## 10. State Model & Transitions

### Allowed states
- `ACTIVE`
- `INACTIVE`

### Role-based transitions
- Admin can transition:
  - ACTIVE → INACTIVE (via Deactivate action)
- No other transitions defined in inputs.

### UI behavior per state
- **ACTIVE**
  - All editable fields enabled.
  - Deactivate action enabled.
- **INACTIVE**
  - Fields displayed read-only.
  - Deactivate action hidden/disabled.
  - List default filter excludes INACTIVE; user must choose INACTIVE/ALL to see it.

---

## 11. Alternate / Error Flows

### Validation failures
- Timezone invalid: show timezone field error, no navigation.
- Operating hours invalid (duplicate day, close <= open, overnight): show operating hours editor error.
- Buffers invalid: show numeric input error.

### Concurrency conflicts
- On `OPTIMISTIC_LOCK_FAILED`:
  - Show blocking banner/modal: “This location was updated by another user.”
  - Actions: **Reload** (refetch) and **Cancel** (stay on form, changes retained but not submittable until reload).
  - After reload, form resets to latest server state.

### Unauthorized access
- If GET list/detail returns 403: show access denied state.
- If create/update/deactivate returns 403: show toast/banner and keep user on page.

### Empty states
- List with no results for filter: show “No locations found” and CTA to create (if authorized).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: List active locations by default
**Given** I am an authenticated Admin  
**When** I open the Locations list screen  
**Then** the UI requests `GET /locations?status=ACTIVE` (or omits status if backend defaults to ACTIVE)  
**And** only ACTIVE locations are shown  
**And** I can change the status filter to INACTIVE or ALL and see matching results.

### Scenario 2: Create location (happy path)
**Given** I am an authenticated Admin with permission to manage locations  
**When** I navigate to Create Location  
**And** I enter a name, a valid address, a valid IANA timezone, and valid operating hours  
**And** I click Save  
**Then** the UI sends `POST /locations` with the entered data  
**And** I see a success confirmation  
**And** I am navigated to the new location detail screen showing `status=ACTIVE`.

### Scenario 3: Update location (happy path)
**Given** I am viewing an existing ACTIVE location  
**When** I edit the timezone and operating hours with valid values  
**And** I click Save  
**Then** the UI sends `PUT /locations/{locationId}` including the current `version`  
**And** the UI displays the updated values from the response (or after refetch).

### Scenario 4: Deactivate location (soft deactivate)
**Given** I am viewing an ACTIVE location  
**When** I click Deactivate and confirm  
**Then** the UI sends `PATCH /locations/{locationId}` with `{status:"INACTIVE", version:<current>}`  
**And** the UI shows the location status as INACTIVE  
**And** the Deactivate action is no longer available  
**And** the location is not shown in the list when the filter is ACTIVE.

### Scenario 5: Reject invalid timezone
**Given** I am creating or editing a location  
**When** I enter a timezone that the backend rejects  
**And** I click Save  
**Then** the backend responds `400` with code `INVALID_TIMEZONE`  
**And** the UI displays an inline error on the timezone field  
**And** no navigation occurs.

### Scenario 6: Reject invalid operating hours
**Given** I am creating or editing a location  
**When** I configure operating hours with a duplicate day or with `close <= open` or an overnight range  
**And** I click Save  
**Then** the backend responds `400` with code `INVALID_OPERATING_HOURS`  
**And** the UI displays an error on the operating hours editor  
**And** my inputs remain visible for correction.

### Scenario 7: Reject duplicate location name
**Given** a location already exists with the name "Downtown Auto Repair"  
**When** I create or rename another location to "  downtown auto repair  "  
**Then** the backend responds `409` with code `LOCATION_NAME_TAKEN`  
**And** the UI displays an inline error on the name field indicating the name is already in use.

### Scenario 8: Optimistic lock conflict on update
**Given** I loaded a location for editing  
**And** another user updates the same location before I save  
**When** I click Save with my stale version  
**Then** the backend responds `409` with code `OPTIMISTIC_LOCK_FAILED`  
**And** the UI informs me the record changed  
**And** offers a Reload action that refreshes the form with the latest server values.

---

## 13. Audit & Observability

### User-visible audit data
- Display read-only metadata on detail screen:
  - `createdAt`, `updatedAt`, `status`, `version`
- If an audit endpoint exists in Moqui conventions, add an “Audit” tab/section that lists audit events for this location (event type, actor, timestamp, summary).  
  - If not available, do not block implementation; show only available metadata.

### Status history
- Minimum: show current status and updated timestamp.
- If audit events are accessible: show `LOCATION_CREATED`, `LOCATION_UPDATED`, `LOCATION_DEACTIVATED` entries.

### Traceability expectations
- All create/update/deactivate UI actions should log (frontend) a debug-level trace (non-PII) with correlation ID if available from backend headers, and include locationId on success.

---

## 14. Non-Functional UI Requirements
- **Performance:** list loads within 2s for up to 200 locations; use pagination if backend supports it (otherwise render all).
- **Accessibility:** keyboard navigable forms, labeled inputs, error text associated to fields; confirmation dialogs focus-trapped.
- **Responsiveness:** usable on tablet widths; forms stack vertically on narrow screens.
- **i18n/timezone/currency:** timezone selection uses IANA IDs; date entry for holiday closures is `YYYY-MM-DD` and treated as date-only (no timezone conversion in UI).

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**
  - **Assumed:** Provide empty-state messaging and CTA on list screens when no locations match filter.
  - **Why safe:** UI-only ergonomics; does not change domain behavior or policy.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria.
- **SD-ERR-STD-MAPPING**
  - **Assumed:** Standard mapping of HTTP 400/403/404/409 to inline/form errors and banners based on `{code,message,field}` when present.
  - **Why safe:** Error-handling glue consistent with backend contract; no business logic inferred.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions
1. **Address schema:** What exact `address` object fields does the backend expect/return (e.g., line1/line2/city/region/postalCode/countryCode)? Are any fields required beyond a single line?
2. **Operating hours representation:** Confirm the exact payload shape for `operatingHours` (day enum values, property names) and whether an empty list is allowed vs required.
3. **Holiday closures constraints:** Is `holidayClosures` nullable vs empty array preferred, and is `reason` length/format constrained?
4. **Editing INACTIVE locations:** Should INACTIVE locations be editable (aside from status), or read-only in UI? Backend rules don’t explicitly forbid edits.
5. **Audit UI endpoint:** Is there an API/screen pattern in this Moqui frontend for fetching and displaying audit events per entity (Location)? If yes, what endpoint and shape?
6. **Routing conventions:** What are the canonical route/screen paths for admin maintenance screens in this repo (e.g., `/admin/locations`, `/locations`), and required menu placement?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Locations: Create and Maintain Shop Locations  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/142  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create and Maintain Shop Locations

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Admin**, I want to create/edit shop locations with address, hours, and timezone so that appointments and scheduling rules are correct per site.

## Details
- Store location name/address/timezone/operating hours/holiday closures.
- Defaults: check-in and cleanup buffers.

## Acceptance Criteria
- Create/update/deactivate location.
- Hours/timezone validated.
- Changes audited.

## Integrations
- Workexec stores locationId on Estimate/WO/Invoice context.
- HR availability can be filtered by location affiliation.

## Data / Entities
- Location, OperatingHours, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management

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