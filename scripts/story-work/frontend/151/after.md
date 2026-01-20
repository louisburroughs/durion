STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title**  
[FRONTEND] Location: Create/Update Location (pos-location) Including Timezone

**Primary Persona**  
Admin (location administrator)

**Business Value**  
Admins can create and maintain accurate location metadata (including timezone) so downstream systems (staffing, scheduling, timekeeping, work execution) can anchor activity to the correct site context.

---

## 2. Story Intent

**As a** Admin  
**I want** to create and update Locations (code, name, address, timezone, status, optional parent)  
**So that** the organization has an authoritative, up-to-date catalog of sites with correct timezone context.

### In-scope
- Frontend screens and flows to:
  - Create a new Location.
  - View an existing Location.
  - Edit/update an existing Location.
  - Soft-deactivate/reactivate a Location via `status` update (ACTIVE/INACTIVE), if supported by backend.
- Client-side validation aligned to known domain rules:
  - `code` immutable after creation.
  - `name` unique (case-insensitive, trimmed) (validated via backend; UI should surface conflicts).
  - `timezone` must be a valid IANA timezone identifier (UI should provide selection / validation).
  - Optional `parentLocationId` must reference an existing Location (UI selection).
- Error handling and feedback for typical backend errors (400/401/403/404/409).

### Out-of-scope (explicit)
- Enforcing “Inactive locations prevent new staffing assignments” in staffing/scheduling/workorder creation flows (belongs to consuming domains like People/Workexec per location domain guidance).
- Defining hierarchy cascade behavior (e.g., parent INACTIVE cascades to children) unless explicitly confirmed.
- Sync/import workflows (SyncLog) and external authoritative system ingestion.
- Managing Bays, Mobile Units, Service Areas, Travel Buffer Policies, site default storage locations (separate stories).

---

## 3. Actors & Stakeholders

- **Admin (primary user):** Creates/updates location records.
- **Location Domain Service (system of record):** Persists and validates Location data; returns errors for constraint violations.
- **Downstream consumers (stakeholders):** People/Scheduling/Workexec systems consuming `locationId`, `status`, and `timezone`.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to manage locations (permission name TBD; domain guidance suggests `location:manage`).

### Dependencies
- Backend endpoints for Location CRUD exist and are reachable from Moqui frontend (exact paths/payloads TBD; see Open Questions).
- A timezone reference source for IANA identifiers is available to the UI (either backend-provided list or frontend static list).
- Ability to query Locations for parent selection (list/search endpoint).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: Admin → Locations (or equivalent admin menu).
- Direct route: `/locations` list; `/locations/new`; `/locations/:locationId`.

### Screens to create/modify (Moqui)
1. **LocationList** (new or extend)
   - List Locations with filters (status).
   - Actions: “Create Location”, “View/Edit”.

2. **LocationDetail** (new or extend)
   - Read-only summary + “Edit” action.

3. **LocationForm** (create/edit)
   - Create mode: enter code, name, address, timezone, status (optional), parent (optional).
   - Edit mode: code read-only; allow updating name/address/timezone/status/parent.
   - Save/Cancel actions.

### Navigation context
- Breadcrumb: Admin → Locations → (New | {Location Name})
- After create: navigate to detail page for new location.
- After update: remain on detail page, showing updated values.

### User workflows
**Happy path: Create**
1. Admin opens Locations list.
2. Clicks “Create Location”.
3. Completes required fields and saves.
4. Sees success message and lands on Location detail.

**Happy path: Update**
1. Admin opens a Location.
2. Clicks “Edit”.
3. Updates allowed fields; saves.
4. Sees updated values and success message.

**Alternate paths**
- Attempt to change code on edit: code is disabled/read-only; no submission of changed code.
- Duplicate code/name: backend 409 surfaced inline.
- Invalid timezone: blocked client-side where possible; otherwise backend 400 surfaced.

---

## 6. Functional Behavior

### Triggers
- User navigates to create/edit screen.
- User submits form.
- User changes status to INACTIVE (soft-deactivation) or ACTIVE (reactivation), if allowed.

### UI actions
- Load list of Locations (for list screen and parent selector).
- Load Location details by `locationId` for detail/edit.
- Submit create/update to backend.
- Display field-level errors when provided; otherwise show banner/toast.

### State changes (frontend)
- Form state: pristine/dirty, submitting, submit success/failure.
- Optimistic locking: if backend indicates version conflict, show “data changed” prompt (see Error Flows).

### Service interactions (Moqui)
- Moqui screen actions call services (either REST client service-calls or Moqui service facade) to:
  - `createLocation`
  - `updateLocation`
  - `getLocation`
  - `listLocations` (for list and parent selector)

(Exact service names/endpoints TBD; see Service Contracts.)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side where safe; authoritative on server)
- **code**
  - Create: required, non-empty; UI should prevent leading/trailing spaces (trim on submit).
  - Edit: displayed read-only; not editable.
  - Duplicate: if backend returns conflict, show “Code already exists” on code field.

- **name**
  - Required, non-empty; trim on submit.
  - Duplicate name (case-insensitive): backend conflict surfaced as field error on name.

- **timezone**
  - Required.
  - Must be valid IANA identifier:
    - UI should use a controlled select/autocomplete of IANA timezones when possible.
    - If free text is allowed, validate format loosely client-side, but rely on backend for full validation.

- **status**
  - Enum: `ACTIVE` or `INACTIVE`.
  - Default on create: assume backend defaults to `ACTIVE` unless clarified; UI should either:
    - set `ACTIVE` explicitly, or
    - leave unset and show `ACTIVE` as selected default (blocking question; see Open Questions).

- **parentLocationId**
  - Optional.
  - Must be an existing Location. UI must provide search/select from Locations.
  - UI must prevent selecting itself as parent (on edit).

### Enable/disable rules
- Save button disabled until required fields valid and form dirty (edit mode) or valid (create).
- Code input disabled in edit mode.

### Visibility rules
- Parent selector visible in both create and edit.
- Status selector visible in both create and edit (unless backend disallows setting status on create; clarify).

### Error messaging expectations
- 400 validation: show field-level messages when backend provides field mapping; otherwise show banner with message.
- 409 conflict: show specific message for code/name duplicates.
- 403: show “You don’t have permission to manage locations.”
- 404 on edit/detail: show not-found screen with link back to list.

---

## 8. Data Requirements

### Entities involved
- **Location** (system of record in location domain)

### Fields (frontend model)
| Field | Type | Required | Default | Editable? |
|---|---|---:|---|---|
| locationId | UUID/string | server | n/a | read-only |
| code | string | yes (create) | none | create only (immutable) |
| name | string | yes | none | editable |
| address | object | no/yes (TBD) | none | editable |
| timezone | string | yes | none | editable |
| status | enum(ACTIVE, INACTIVE) | yes (server) | ACTIVE (assumed) | editable (TBD if allowed on create) |
| parentLocationId | UUID/string | no | null | editable |
| createdAt | timestamp | server | n/a | read-only |
| updatedAt | timestamp | server | n/a | read-only |
| (optional) version | number/string | TBD | n/a | hidden read-only (for optimistic locking) |

### Address structure (blocking: undefined)
- Provided input only says “address” without schema. UI must not invent structure without contract.
- Options:
  1) Treat as a single free-text field.
  2) Use structured fields (street, city, region, postalCode, countryCode).
Requires clarification (see Open Questions).

### Read-only vs editable by state/role
- By default: editable for Admin with location manage permission.
- No additional role matrix defined in inputs; permission details are blocking.

### Derived/calculated fields
- None in this story.

---

## 9. Service Contracts (Frontend Perspective)

> Backend API details are “TBD” per domain guide; therefore the frontend must align to whichever of the following is actually implemented. This story is blocked until endpoint paths/payloads are confirmed.

### Load/view calls
- **List Locations**
  - Intended: `GET /v1/locations?status=ACTIVE|INACTIVE|ALL`
  - Used by: LocationList, parent selector.
  - Response: array of Locations (at least `locationId`, `code`, `name`, `status`).

- **Get Location by ID**
  - Intended: `GET /v1/locations/{locationId}`
  - Used by: LocationDetail, LocationForm(edit).

### Create/update calls
- **Create Location**
  - Intended: `POST /v1/locations`
  - Request includes: `code`, `name`, `timezone`, optionally `address`, `status?`, `parentLocationId?`
  - Response: created Location.

- **Update Location (full or partial)**
  - Intended: `PUT /v1/locations/{locationId}` (full update) or `PATCH` (partial)
  - UI should send only editable fields; must never attempt to change `code`.

### Submit/transition calls
- Status change is treated as update (PATCH or PUT).

### Error handling expectations (HTTP)
- 400: validation errors (timezone invalid, missing required fields).
- 401: not authenticated.
- 403: not authorized.
- 404: location not found.
- 409: duplicate `code` or `name` conflict.
- 409/412: optimistic locking/version conflict (TBD).

---

## 10. State Model & Transitions

### Allowed states
- `ACTIVE`
- `INACTIVE`

### Allowed transitions
- `ACTIVE → INACTIVE` (soft-deactivation)
- `INACTIVE → ACTIVE` (reactivation) (not explicitly stated but implied by status enum; needs confirmation)

### Role-based transitions
- Only Admins with location management permission can change status.

### UI behavior per state
- In list: show status.
- In detail: show status and last updated timestamp.
- In edit: allow status selection unless backend forbids.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing name/code/timezone on create: prevent submit; show inline errors.
- Invalid timezone: prevent submit if using select; if free text, allow submit but surface backend error.

### Concurrency conflicts
- If backend indicates stale update (version conflict):
  - Show blocking dialog: “This location was updated by someone else. Reload to continue.”
  - Provide actions: Reload (discard local changes) / Cancel.

### Unauthorized access
- If 403 on any load/save: show permission error page/message and link back to home/list.

### Empty states
- LocationList with no locations: show empty state + “Create Location”.
- Parent selector when no locations exist: show “No parent locations available” and allow clearing.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create location (success)
**Given** I am an authenticated Admin with permission to manage locations  
**And** I am on the “Create Location” screen  
**When** I enter a unique `code`, a `name`, select a valid IANA `timezone`, and submit  
**Then** the system creates the location with `status` = `ACTIVE` (default)  
**And** I am navigated to the Location detail screen for the new location  
**And** I see a success confirmation

### Scenario 2: Update location fields (success)
**Given** I am an authenticated Admin with permission to manage locations  
**And** an existing Location exists  
**When** I open the Location edit screen  
**Then** the `code` field is displayed as read-only  
**When** I change the `name` and `timezone` and submit  
**Then** the updated values are displayed on the Location detail screen

### Scenario 3: Attempt to modify immutable code (prevented)
**Given** I am editing an existing Location  
**Then** I cannot edit the `code` field  
**And** the update request does not include any changed `code` value

### Scenario 4: Duplicate code on create (conflict)
**Given** a Location already exists with `code` = "MAIN-WH"  
**When** I attempt to create a new Location with `code` = "MAIN-WH"  
**Then** the save fails  
**And** I see a field-level error indicating the code already exists  
**And** no new Location is created

### Scenario 5: Invalid timezone (validation error)
**Given** I am on the create/edit Location screen  
**When** I submit a Location with an invalid `timezone` value  
**Then** the save fails  
**And** I see an error message on the timezone field (or form banner) indicating the timezone is invalid

### Scenario 6: Set location to inactive (success)
**Given** I am an authenticated Admin with permission to manage locations  
**And** an existing Location is `ACTIVE`  
**When** I edit the Location and set `status` to `INACTIVE` and submit  
**Then** the Location detail shows `status` = `INACTIVE`

### Scenario 7: Not authorized (forbidden)
**Given** I am authenticated but do not have permission to manage locations  
**When** I attempt to open the create/edit Location screen or submit changes  
**Then** I receive a forbidden error  
**And** the UI shows an authorization error message and does not persist changes

---

## 13. Audit & Observability

### User-visible audit data
- Display `createdAt` and `updatedAt` (if provided by backend) on the detail screen.

### Status history
- Not in scope unless backend provides an explicit status history endpoint/entity.

### Traceability expectations
- Frontend should log (client-side) key actions at INFO level (route entered, create submitted, update submitted) without PII beyond location identifiers.
- Correlation/request ID: if backend returns a correlation ID header, surface it in error details (developer console/log only).

---

## 14. Non-Functional UI Requirements

- **Performance:** Location list loads within 2 seconds for typical dataset sizes; parent selector supports search if list is large (exact threshold not specified).
- **Accessibility:** All form fields labeled; validation errors announced; keyboard navigable.
- **Responsiveness:** Usable on tablet and desktop; forms adapt to narrow viewports.
- **i18n/timezone/currency:** Timezone selection must use IANA identifiers; display can be localized, but stored value must be the canonical IANA string.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide standard empty state for list/selector; safe because it does not change domain behavior, only presentation. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-FORM-SUBMIT-GUARD: Disable submit while invalid/submitting to prevent duplicates; safe because it avoids accidental repeated requests. (Impacted: Functional Behavior, Error Flows)

---

## 16. Open Questions

1. **Backend contract (blocking):** What are the exact Moqui-accessible service names or REST endpoints, request/response payloads, and error formats for Location create/update/get/list? (The domain guide lists high-level endpoints but says detailed schemas are TBD.)
2. **Address schema (blocking):** Is `address` a structured object (street/city/region/postal/country) or a free-text string, and what fields are required?
3. **Permissions (blocking):** What permission(s)/roles gate create/update/status-change for locations in the frontend (e.g., `location:manage`), and how does the frontend check them?
4. **Status on create (blocking):** Can Admin set `status` during creation, or is it always defaulted to `ACTIVE` and only changeable after creation?
5. **Reactivation (needs confirmation):** Is `INACTIVE → ACTIVE` allowed, or is deactivation permanent?
6. **Parent hierarchy constraints (needs confirmation):** Are there constraints beyond “must reference existing” (e.g., preventing cycles, max depth), and should UI enforce any of them?
7. **Timezone source (blocking):** Should the UI use a backend-provided list of allowed IANA timezones, or ship a static list in the frontend?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Location: Create/Update Location (pos-location) Including Timezone  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/151  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Location: Create/Update Location (pos-location) Including Timezone

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As an **Admin**, I want **to create and update locations** so that **staffing, scheduling, and timekeeping are anchored to the correct site and timezone**.

## Details  
- Location fields: code, name, address, timezone, status, optional parent.

## Acceptance Criteria  
- Location can be created/updated.  
- Inactive locations prevent new staffing assignments.

## Integration Points (workexec/shopmgr)  
- shopmgr schedules are tied to locationId.  
- workexec workorders reference a service location.

## Data / Entities  
- Location

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: People Management

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