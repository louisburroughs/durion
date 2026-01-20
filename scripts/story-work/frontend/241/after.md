STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Counts: Plan Cycle Counts by Location/Zone

### Primary Persona
Inventory Manager

### Business Value
Enable Inventory Managers to schedule targeted cycle count plans by storage location and zone to improve inventory accuracy and operational planning.

---

## 2. Story Intent

### As a / I want / So that
**As an** Inventory Manager,  
**I want** to create a Cycle Count Plan by selecting a Location and one or more Zones and a future scheduled date,  
**so that** warehouse staff can execute planned counts and the organization can maintain accurate stock records.

### In-scope
- UI flow to create a new Cycle Count Plan:
  - Select **Location**
  - Select **one or more Zones** belonging to the selected Location
  - Choose **scheduled date** (must not be in the past)
  - Optional **plan name/description**
- Frontend validation + backend validation error display
- Successful creation confirmation and navigation to view/list
- Read-only visibility of plan “created” metadata after submit (as returned by API)

### Out-of-scope
- Executing the count (starting plan, entering count results, adjustments)
- Editing plan scope after creation (location/zones) unless explicitly supported by backend
- Recurring plans
- Defining how SKUs are selected for inclusion (scope logic) beyond displaying what backend returns
- Location/Zone administration (creating zones/locations)

---

## 3. Actors & Stakeholders
- **Inventory Manager (primary):** creates scheduled cycle count plans.
- **Warehouse Staff (downstream):** will execute counts generated from plans (not in scope).
- **Auditor / Ops Manager (stakeholder):** expects traceable history of created plans (display only, if available).
- **System (Moqui app):** enforces permissions, validation, and persists plan via services/APIs.

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission to create cycle count plans (permission string is currently unclear; backend reference uses `INVENTORY_PLAN_CREATE`).
- At least one Location exists.
- Selected Location has one or more Zones (or UI must handle empty zones).
- Backend endpoints/services exist for:
  - Listing Locations
  - Listing Zones for a Location
  - Creating a Cycle Count Plan
- Backend returns structured validation errors for:
  - Past scheduled date
  - Missing location
  - Missing zones
  - Unauthorized

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Left-nav or menu entry: **Inventory → Counts → Cycle Counts**
- Primary CTA on list screen: **“New Cycle Count Plan”**

### Screens to create/modify (Moqui)
1. **Screen:** `apps/pos/inventory/counts/PlanList.xml` (or equivalent module path)
   - Shows list of existing plans (at minimum: plan name, location, scheduled date, status)
   - CTA navigates to create screen
2. **Screen:** `apps/pos/inventory/counts/PlanCreate.xml`
   - Form to create plan
   - Loads locations on entry
   - Loads zones dynamically after location selection
3. **Screen (optional but recommended):** `apps/pos/inventory/counts/PlanDetail.xml`
   - After create, navigate to detail view for created planId (if backend supports fetch-by-id)

> Exact file paths depend on repo conventions (must align with `durion-moqui-frontend` screen structure).

### Navigation context
- Breadcrumb example: Inventory > Counts > Cycle Count Plans > New Plan
- Back action returns to Plan List preserving filters (safe default)

### User workflows
**Happy path**
1. User opens Cycle Count Plans list
2. Clicks “New Cycle Count Plan”
3. Selects Location
4. Selects one or more Zones
5. Sets scheduled date to today or future (see Open Questions re: “today”)
6. Optionally enters plan name/description
7. Submits
8. Sees success message and is redirected to Plan Detail (or Plan List with new plan highlighted)

**Alternate paths**
- Location selected but has no zones → show empty state + disable submit
- Scheduled date in past → inline validation; on submit show backend error mapping if returned
- Backend 403 → show “Not authorized” and disable form submission thereafter

---

## 6. Functional Behavior

### Triggers
- User visits Plan Create screen
- User selects a Location
- User submits create form

### UI actions
- **On screen load**: fetch Locations
- **On location change**:
  - Clear any previously selected zones
  - Fetch Zones for chosen location
- **On submit**:
  - Validate required fields client-side
  - Call create service
  - On success: show toast/notification and navigate
  - On failure: show field-level errors (if provided) else show page-level error

### State changes (frontend)
- Form state: `idle → loadingLocations → ready → loadingZones → ready → submitting → success|error`
- Disable submit while `submitting`
- Disable zone multi-select until zones loaded

### Service interactions (Moqui)
- Use Moqui `transition` invoking a `service-call` (or `rest-call` if backend is external) for:
  - `listLocations`
  - `listZonesByLocation`
  - `createCycleCountPlan`
- Ensure transitions capture and render errors in the screen messages area consistent with Moqui patterns.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Location is required**
  - If missing: block submit; show message “Location is required.”
- **At least one Zone is required**
  - If none selected: block submit; show message “Select at least one zone.”
- **Scheduled date cannot be in the past**
  - Client-side: if date < today (timezone rules are an Open Question), show inline error and block submit
  - Server-side: display returned error message verbatim if present (e.g., “Scheduled date must be in the future.”)
- **Plan name/description optional**
  - If present, enforce max length (Open Question: backend limit). Until clarified, do not enforce a max beyond UI component reasonable limit; rely on backend and show returned validation error.

### Enable/disable rules
- Zone selector disabled until a Location is selected and zones are loaded.
- Submit disabled until all required fields valid and zones loaded.

### Visibility rules
- If user lacks create permission (403 on load or submit):
  - Show non-editable “You do not have permission to create cycle count plans.”
  - Hide/disable submit button.

### Error messaging expectations
- Field-level errors displayed adjacent to corresponding field when backend identifies a field (needs backend error schema; otherwise show general error at top).
- 404 for invalid location/zone (stale selection) shown as: “The specified location or zone could not be found.”

---

## 8. Data Requirements

### Entities involved (conceptual; frontend-facing)
- `CycleCountPlan`
- `Location`
- `Zone` (scoped to Location)

### Fields (type, required, defaults)
**CycleCountPlan (create payload)**
- `locationId` (string/UUID, required)
- `zoneIds` (array of string/UUID, required, min 1)
- `scheduledDate` (date string, required; format per API—Open Question)
- `planName` (string, optional) OR `description` (string, optional) — naming mismatch must be clarified

**CycleCountPlan (response/display)**
- `planId` (string/UUID)
- `status` (string enum; at least `PLANNED`)
- `createdBy` (string/userId; display if returned)
- `createdAt` (timestamp; display if returned)
- `updatedAt` (timestamp; display if returned)

**Location**
- `locationId` (string/UUID)
- `locationName` (string)

**Zone**
- `zoneId` (string/UUID)
- `zoneName` (string)

### Read-only vs editable by state/role
- On Create screen: all inputs editable
- After creation:
  - If Plan is `PLANNED`: details are read-only in this story (no edit requirements provided)
  - Once `IN_PROGRESS`: scope immutable (informational only unless edit exists)

### Derived/calculated fields
- None calculated in frontend.
- If backend returns “item count included in plan”, display as read-only metadata (only if available; otherwise omit).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not provided in the frontend issue; below is a **frontend-perspective contract** that must be aligned with backend before implementation.

### Load/view calls
1. **List Locations**
   - Method: GET
   - Path (proposed): `/v1/inventory/locations`
   - Response: `[{ locationId, locationName }]`
   - Errors: 401/403
2. **List Zones by Location**
   - Method: GET
   - Path (proposed): `/v1/inventory/locations/{locationId}/zones`
   - Response: `[{ zoneId, zoneName }]`
   - Errors: 404 (location), 401/403

### Create/update calls
3. **Create Cycle Count Plan**
   - Method: POST
   - Path (proposed): `/v1/inventory/cycle-count-plans`
   - Request:
     ```json
     {
       "locationId": "uuid",
       "zoneIds": ["uuid"],
       "scheduledDate": "YYYY-MM-DD",
       "planName": "string (optional)"
     }
     ```
   - Success: 201 with body including `planId`, `status`, and echo fields
   - Errors:
     - 400 validation (missing zoneIds, past date, etc.)
     - 404 invalid location/zone
     - 403 unauthorized

### Submit/transition calls (Moqui)
- `transition name="createPlan"` calls the create service and on success transitions to detail/list.

### Error handling expectations
- Validation errors map to form fields when possible.
- Unknown errors show a generic message: “Could not create cycle count plan. Try again or contact support.” plus correlation/trace id if available from response headers (Open Question: do we have one?).

---

## 10. State Model & Transitions

### Allowed states (minimum known)
- `PLANNED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

### Role-based transitions
- Create action allowed only for users with create permission (string TBD).
- No other transitions are implemented in this story.

### UI behavior per state
- Create screen always creates a plan in `PLANNED` (as per backend reference).
- List/Detail screens show status as read-only label.
- If backend returns a special status for “no items” (e.g., `PLANNED_NO_ITEMS`), UI must display it without special logic (status rendering is tolerant of unknown enums).

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields: prevent submit client-side.
- Past scheduled date:
  - If caught client-side: show inline error
  - If server rejects: display server message and keep user inputs intact

### Concurrency conflicts
- If zones list changes after selection and submit returns 404:
  - Show error and prompt user to re-select location/zones; keep scheduled date and plan name

### Unauthorized access
- If any call returns 403:
  - Show “Not authorized”
  - Disable create CTA and/or form submit
  - Provide navigation back to list

### Empty states
- No locations: show empty state “No locations available. Contact an administrator.” and disable form.
- Location has zero zones: show “No zones configured for this location.” and disable submit.
- Plan created but includes no items (if backend indicates): show non-blocking warning on success page/list (requires backend signal—Open Question).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create plan successfully
**Given** I am logged in as an Inventory Manager with permission to create cycle count plans  
**And** at least one Location exists with at least one Zone  
**When** I navigate to Inventory > Counts > Cycle Count Plans and choose “New Cycle Count Plan”  
**And** I select a Location  
**And** I select one or more Zones for that Location  
**And** I set a scheduled date that is not in the past  
**And** I submit the form  
**Then** the system creates the Cycle Count Plan successfully  
**And** I see a success confirmation  
**And** I am navigated to the created plan’s detail view or the plans list where the new plan is visible.

### Scenario 2: Prevent submit when no zones selected
**Given** I am on the New Cycle Count Plan screen  
**When** I select a Location  
**And** I do not select any Zones  
**Then** the Submit action is disabled or blocked  
**And** I see a validation message indicating at least one zone is required.

### Scenario 3: Reject scheduled date in the past (client-side)
**Given** I am on the New Cycle Count Plan screen  
**When** I enter a scheduled date that is in the past  
**Then** I see an inline validation error  
**And** I cannot submit the plan until the date is corrected.

### Scenario 4: Handle backend validation error for past date
**Given** I am on the New Cycle Count Plan screen  
**And** I submit the form with a scheduled date the backend deems invalid  
**When** the backend responds with HTTP 400 and message “Scheduled date must be in the future.”  
**Then** the UI displays the backend error message  
**And** the form remains populated for correction.

### Scenario 5: Handle stale location/zone selection
**Given** I selected a Location and Zone that later becomes invalid  
**When** I submit the plan and the backend responds with HTTP 404  
**Then** I see an error message “The specified location or zone could not be found.”  
**And** I can reselect Location/Zones and resubmit.

### Scenario 6: Unauthorized user cannot create plan
**Given** I am logged in without permission to create cycle count plans  
**When** I navigate to the New Cycle Count Plan screen or attempt to submit  
**Then** I receive a “Not authorized” message  
**And** the create/submit controls are disabled or hidden.

---

## 13. Audit & Observability

### User-visible audit data
- After creation, display (when returned by API): `createdBy`, `createdAt`, `status`.
- If not available, omit without placeholder fields.

### Status history
- Not required to implement unless an endpoint exists; no requirements provided.

### Traceability expectations
- Frontend should log (client-side) a structured event on submit attempt and result:
  - `cycleCountPlanCreate.attempt`, `cycleCountPlanCreate.success`, `cycleCountPlanCreate.failure`
  - Include `locationId`, count of `zoneIds`, and returned `planId` on success
  - Do not log sensitive user info.

---

## 14. Non-Functional UI Requirements
- **Performance:** Locations and zones lists should load within 2 seconds on typical network; show loading indicators.
- **Accessibility:** WCAG 2.1 AA; all form controls labeled; validation errors announced to screen readers.
- **Responsiveness:** Usable on tablet form factor typical for warehouse/POS operations.
- **i18n/timezone:** Date handling must respect user/site timezone (exact rule is an Open Question); display dates in local format, send in API-required format.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for “no locations” and “no zones”; qualifies as safe because it does not change domain behavior, only improves UX clarity. (Impacted: UX Summary, Alternate/Errors)
- SD-UX-LOADING-STATES: Show loading indicators and disable dependent controls while fetching; safe as it only affects ergonomics. (Impacted: Functional Behavior, Alternate/Errors)
- SD-ERR-GENERIC: For unexpected 5xx/network errors, show a generic retry message while preserving form inputs; safe because it doesn’t alter business logic. (Impacted: Error Flows, Service Contracts)

---

## 16. Open Questions
1. **API contracts:** What are the exact backend endpoints (paths), request/response schemas, and error format for:
   - list locations
   - list zones by location
   - create cycle count plan?
2. **Permission name(s):** What permission(s) gate plan creation in this app? Backend reference mentions `INVENTORY_PLAN_CREATE`; inventory domain guide uses permission-style strings like `inventory.*`. Which is authoritative for frontend checks/messages?
3. **Field naming:** Is the optional free-text field `planName`, `description`, or both? What are max lengths and allowed characters?
4. **Scheduled date rule:** Is “today” allowed, or must it be strictly future? Backend text says “cannot be in the past” but error message says “must be in the future.” Confirm exact validation.
5. **Timezone definition:** Is “past” evaluated in user timezone, site/location timezone, or UTC?
6. **Empty zones/items behavior:** If selected zones contain no inventory items:
   - Should creation be allowed?
   - Should UI show a warning on success?
   - Is there a distinct status like `PLANNED_NO_ITEMS`?
7. **Post-create navigation:** Should we navigate to a Plan Detail screen (requires GET by `planId`) or back to list only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Counts: Plan Cycle Counts by Location/Zone — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/241


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Counts: Plan Cycle Counts by Location/Zone
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/241
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #238 - Counts: Plan Cycle Counts by Location/Zone
**URL**: https://github.com/louisburroughs/durion/issues/238
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns


### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:36:25.984782271*

---

*End of preserved original content.*