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

# 1. Story Header

## Title
[FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity

## Primary Persona
Shop Administrator (Admin)

## Business Value
Enables accurate scheduling/dispatch by allowing admins to define each service bay’s operational status, capacity, and constraint references (supported services + required skills), reducing mis-assignments and improving throughput.

---

# 2. Story Intent

## As a / I want / So that
**As a** Shop Administrator  
**I want** to create a service bay under a location with bay type, status, capacity, and constraint references  
**So that** scheduler/dispatch can select an appropriate ACTIVE bay that meets service/skill requirements.

## In-Scope
- Admin UI to **create** a Bay under a selected Location.
- Capture and validate Bay fields: `name`, `bayType`, `status`, `capacity.maxConcurrentVehicles`, `supportedServiceIds[]`, `requiredSkillRequirements[]`.
- Basic admin UI to confirm the created bay exists (post-create view/return to list).
- Error handling for validation and conflict responses (`400`, `401`, `403`, `404`, `409`).
- Moqui screens/forms/transitions wired to backend endpoints described in backend story #77.

## Out-of-Scope
- Implementing scheduling/dispatch assignment logic (consumer responsibility).
- Enforcing “out-of-service blocks assignments” beyond surfacing/storing status and ensuring list filtering supports `status=ACTIVE`.
- Creating/updating other location resources (mobile units, service areas, travel buffer policies).
- Advanced bay editing flows (PATCH/update) unless already present; this story focuses on **create** plus immediate confirmation.

---

# 3. Actors & Stakeholders
- **Shop Administrator (Primary user)**: Creates and configures bays for a location.
- **Scheduler/Dispatch (Downstream consumer)**: Reads bays filtered by status and constraints for assignment decisions.
- **Work Execution (Downstream consumer)**: Displays bay context (read-only usage of bay metadata).
- **Security/Authorization system**: Determines whether the user can manage bays (frontend must handle forbidden/unauthenticated responses).

---

# 4. Preconditions & Dependencies
- A `Location` already exists and is navigable in the frontend.
- User is authenticated.
- Backend endpoints available (per backend story reference):
  - `POST /locations/{locationId}/bays`
  - `GET /locations/{locationId}/bays`
  - `GET /locations/{locationId}/bays/{bayId}`
- Dependency for constraint picking UX:
  - Service Catalog list/search endpoint(s) for services (authoritative IDs) **must exist**.
  - People/Skills list/search endpoint(s) for skills (authoritative IDs) **must exist**.
  - If these do not exist in the frontend today, the story will surface Open Questions and/or create follow-on stories (see Open Questions).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Location detail screen: “Bays” section/tab → “Create Bay” action.
- Optional: From Location list → select location → navigate to Location detail → Bays.

## Screens to create/modify
1. **Modify**: `LocationDetail` screen to include navigation to Bays list (if not already present).
2. **Create**: `LocationBays` screen (list context for a location) with an action to create a bay.
3. **Create**: `BayCreate` screen/form under a location context.
4. **Create/Modify**: `BayDetail` screen (or reuse existing) to display the created bay after submission.

> Moqui screen path suggestion (adjust to repo conventions):  
`apps/pos/screen/location/LocationDetail.xml`  
`apps/pos/screen/location/bay/BayList.xml`  
`apps/pos/screen/location/bay/BayCreate.xml`  
`apps/pos/screen/location/bay/BayDetail.xml`

## Navigation context
- All bay screens are scoped under a selected location: `locationId` is required in the URL and screen context.
- Breadcrumb: Locations → {Location Name} → Bays → Create Bay

## User workflows
### Happy path
1. Admin navigates to a Location’s Bays.
2. Admin clicks “Create Bay”.
3. Admin enters:
   - Name
   - Bay Type (enum)
   - Status (default ACTIVE)
   - Capacity (max concurrent vehicles)
   - Supported Services (multi-select IDs)
   - Required Skills (multi-row: skillId + optional minLevel)
4. Admin submits.
5. App shows success and routes to Bay Detail (preferred) or back to Bay List with the new bay visible.

### Alternate paths
- Admin cancels → returns to Bay List without changes.
- Admin submits with missing/invalid data → inline errors + form remains editable.
- Duplicate name conflict → error shown on name field and as global form error.

---

# 6. Functional Behavior

## Triggers
- User presses “Create Bay” submit action on the create form.

## UI actions
- Load screen context:
  - Ensure `locationId` exists; if missing, show not-found/invalid route state.
  - Optionally load location summary (name/code) for page context.
- In-form actions:
  - Add/remove supported services selections.
  - Add/remove required skill requirement rows.
  - Change status between `ACTIVE` and `OUT_OF_SERVICE`.
- Submit action:
  - Client-side validation (required fields, numeric bounds).
  - Call backend create endpoint.
  - On success: navigate to Bay Detail or return to list.

## State changes (frontend)
- Form state: `dirty/clean`, `submitting`, `submitError`.
- After success: clear transient form state; persist created bay ID in route.

## Service interactions (Moqui)
- Use Moqui `service-call` / `rest` integration (per project conventions) to call backend endpoints.
- Map backend validation errors to field-level messages where possible.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Required fields on create**:
  - `name` (non-empty, trimmed)
  - `bayType` (must be one of allowed enum values)
  - `status` (default `ACTIVE`)
  - `capacity.maxConcurrentVehicles` (required; integer; must be >= 1)
- **Name uniqueness** is enforced server-side per location:
  - If backend returns `409 Conflict`, show:  
    - Field error on `name`: “Bay name must be unique within this location.”
    - Global error summary: “A bay with this name already exists in this location.”
- **Constraint references must be IDs** (no free text):
  - `supportedServiceIds[]` values must come from service catalog picker (IDs).
  - `requiredSkillRequirements[].skillId` values must come from skills picker (IDs).
  - If backend returns `400` with invalid IDs, show which selections are invalid and allow user to remove/fix.

## Enable/disable rules
- Disable submit while:
  - Submitting request
  - Required fields invalid
- `code` immutability does not apply (Bay has no code field here); no UI for immutable bay identifiers.

## Visibility rules
- Always show status selector; default to ACTIVE.
- Only show `minLevel` input when a `skillId` is selected for a row.

## Error messaging expectations
- Form-level error banner for general errors (network, 500, unexpected payload).
- Field-level error messages for validation issues.
- Preserve user-entered data on error.

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- **Location** (read context): `locationId`, display name/code.
- **Bay** (create payload; returned resource).
- **Bay constraint relationships**:
  - Supported services (service IDs)
  - Skill requirements (skill IDs + optional minLevel)

## Fields (type, required, defaults)

### Bay (Create)
- `locationId` (UUID, required) — from route context; not user-editable.
- `name` (string, required)
  - Trim whitespace before submit.
- `bayType` (enum, required) — allowed values (from backend story):
  - `GENERAL_SERVICE`
  - `ALIGNMENT`
  - `TIRE_SERVICE`
  - `HEAVY_DUTY`
  - `INSPECTION`
  - `WASH_DETAIL`
- `status` (enum, required; default `ACTIVE`)
  - `ACTIVE`
  - `OUT_OF_SERVICE`
- `capacity` (object, required)
  - `maxConcurrentVehicles` (integer, required, >= 1)

### Constraints (Create)
- `supportedServiceIds` (array of IDs/UUIDs/strings, optional; default empty)
- `requiredSkillRequirements` (array, optional; default empty)
  - item: `{ skillId: <id>, minLevel?: number }`
  - `minLevel` optional; if present must be integer >= 1 (exact bounds unknown → validate as positive integer client-side, enforce server response handling)

## Read-only vs editable by state/role
- Create screen: all above fields editable.
- `locationId` is read-only and derived from navigation context.
- Authorization is enforced by backend; frontend must handle forbidden.

## Derived/calculated fields
- None required beyond trimming and basic normalization of `name` for client-side checks.

---

# 9. Service Contracts (Frontend Perspective)

> Note: Backend story gives REST endpoints. Moqui frontend will call these (direct REST or via Moqui service façade per repo conventions).

## Load/view calls
- **Location context (optional but recommended)**  
  `GET /v1/locations/{locationId}`  
  Purpose: display location name in header/breadcrumb and validate location exists.  
  If not available in frontend today, fallback to showing `locationId` only and rely on create endpoint 404 handling.

- **Post-create confirm**  
  `GET /v1/locations/{locationId}/bays/{bayId}`  
  Purpose: show created bay details after redirect.

- **List bays (for return-to-list flow)**  
  `GET /v1/locations/{locationId}/bays?status=ALL` (or omit status)  
  Supports optional filters: `status`, `bayType`.

## Create/update calls
- **Create bay**  
  `POST /v1/locations/{locationId}/bays`  
  Request body (minimum):
  ```json
  {
    "name": "Alignment Rack 1",
    "bayType": "ALIGNMENT",
    "status": "ACTIVE",
    "capacity": { "maxConcurrentVehicles": 1 },
    "supportedServiceIds": ["svc-..."],
    "requiredSkillRequirements": [{ "skillId": "skill-...", "minLevel": 2 }]
  }
  ```
  Response: `201 Created` with full bay payload including `bayId`.

## Submit/transition calls
- None (no separate workflow state machine beyond status enum).

## Error handling expectations
- `400 Bad Request`: show field errors; if response includes invalid IDs, highlight invalid selections.
- `401 Unauthorized`: route to login (or show auth required) per app convention.
- `403 Forbidden`: show “You do not have permission to manage bays for this location.”
- `404 Not Found`: show location not found (if on create) or bay not found (if redirect failed).
- `409 Conflict`: duplicate bay name; show name field error as above.
- Network/5xx: show retryable global error; preserve form state.

---

# 10. State Model & Transitions

## Allowed states (Bay.status)
- `ACTIVE`
- `OUT_OF_SERVICE`

## Role-based transitions
- Shop Administrator can set status on create.
- (Editing status later is out-of-scope here but UI should not prevent future support.)

## UI behavior per state
- If status is `OUT_OF_SERVICE`, UI should display advisory text: “Out-of-service bays are excluded from availability queries.”
- No additional conditional validation based on status in v1.

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- Missing name → block submit; message “Name is required.”
- Missing bayType → block submit; message “Bay type is required.”
- Capacity not integer or < 1 → block submit; message “Max concurrent vehicles must be a whole number ≥ 1.”

## Validation failures (server-side)
- Unknown/invalid service IDs or skill IDs → show which items invalid; allow user to remove and resubmit.
- Unknown locationId → show not found state and provide link back to Locations list.

## Concurrency conflicts
- If backend uses optimistic locking for updates, not applicable to create.  
- If duplicate name created concurrently: handle `409` on submit.

## Unauthorized access
- `401`: redirect to login; after login return to create page if app supports returnUrl.
- `403`: show forbidden screen; do not show form fields.

## Empty states
- If service/skill pickers have no available options (due to missing integration or empty catalogs), show empty-state help text and allow creating bay without constraints (constraints optional).

---

# 12. Acceptance Criteria

### Scenario 1: Admin creates a bay under a location (happy path)
**Given** I am authenticated as a Shop Administrator  
**And** a Location exists with id `<locationId>`  
**When** I open “Create Bay” for `<locationId>`  
**And** I enter a unique name, select a bay type, keep status as ACTIVE, and set capacity maxConcurrentVehicles to 1  
**And** I submit the form  
**Then** the system creates the bay via `POST /locations/<locationId>/bays`  
**And** I see a success outcome  
**And** I am navigated to the created bay detail (or the bay list) showing the new bay.

### Scenario 2: Duplicate bay name is rejected within the same location
**Given** a bay named “Alignment Rack 1” already exists under `<locationId>`  
**When** I attempt to create another bay under `<locationId>` with name “Alignment Rack 1”  
**Then** the backend responds with `409 Conflict`  
**And** the UI shows a field error on the Name input indicating the name must be unique within this location  
**And** my other form inputs remain intact for correction.

### Scenario 3: Create bay with constraints (services + skills) persists and is queryable
**Given** I am on the Create Bay form for `<locationId>`  
**And** I select supported services `[S1, S2]` and required skills `[{skillId: K1, minLevel: 2}]`  
**When** I submit the form  
**Then** the create request includes `supportedServiceIds` and `requiredSkillRequirements`  
**And** on the Bay Detail screen (via `GET /locations/<locationId>/bays/<bayId>`), the constraints and capacity are displayed.

### Scenario 4: Out-of-service status is stored and communicated
**Given** I am creating a bay under `<locationId>`  
**When** I set status to `OUT_OF_SERVICE` and submit successfully  
**Then** the created bay detail shows status `OUT_OF_SERVICE`  
**And** the UI displays an advisory that out-of-service bays are excluded from availability queries.

### Scenario 5: Unknown constraint references return validation errors
**Given** I attempt to create a bay under `<locationId>`  
**When** I submit `supportedServiceIds` or `requiredSkillRequirements.skillId` values that do not exist  
**Then** the backend responds with `400 Bad Request`  
**And** the UI shows a validation error indicating which IDs/selections are invalid  
**And** I can remove/fix the invalid selections and resubmit.

### Scenario 6: Unauthorized users cannot access create bay
**Given** I am not authenticated  
**When** I navigate to the Create Bay URL for `<locationId>`  
**Then** I am prompted to authenticate (or redirected to login).

**Given** I am authenticated but lack permission to manage bays  
**When** I navigate to the Create Bay URL for `<locationId>`  
**Then** the backend responds `403 Forbidden` (on attempted load/submit as applicable)  
**And** the UI shows an access denied state.

---

# 13. Audit & Observability

## User-visible audit data
- On Bay Detail (post-create), show read-only metadata if provided by backend:
  - `createdAt`, `updatedAt`
  - (Optional) `createdBy` if available

## Status history
- Not required for v1 UI unless backend exposes it; do not invent history.

## Traceability expectations
- Frontend should log (console or app logger per conventions) a structured event on create attempt/success/failure:
  - locationId, bay name, bayType, status, correlation/request ID if available.
- Ensure correlation ID from backend responses is surfaced in error UI (if the app has a standard “Error reference id” pattern).

---

# 14. Non-Functional UI Requirements
- **Performance**: Create form should render quickly; pickers must handle large service/skill lists via search/autocomplete if available.
- **Accessibility**: All form inputs labeled; validation errors announced (ARIA); keyboard navigable.
- **Responsiveness**: Usable on tablet; form sections stack vertically on narrow screens.
- **i18n/timezone/currency**: Not applicable (no monetary values; timezone not involved).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: If service/skills catalogs are empty/unavailable, show an empty-state message and allow proceeding without constraints (constraints are optional). Qualifies as safe because it does not change domain policy—only UI ergonomics. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-ERR-STANDARD-MAP: Standard mapping of HTTP 400/401/403/404/409/5xx to inline vs global errors and preserving form state. Qualifies as safe because it is generic error handling without altering business rules. Impacted sections: Service Contracts, Alternate / Error Flows, Acceptance Criteria.

---

# 16. Open Questions
1. What are the **actual endpoints** (and response schemas) available for **Service Catalog** and **Skills** lookup to power the supported services and required skills pickers (search, pagination, ID/displayName fields)?
2. In the backend API, are `supportedServiceIds` and `skillId` values **UUIDs** or **string codes**? The frontend needs type expectations to validate and display selections correctly.
3. Does the backend return a **standard error payload** (fieldErrors structure, invalidIds list, correlationId) that the frontend should map to field-level errors? If so, provide an example.
4. Confirm frontend route/screen conventions in this repo for location subresources: should URLs be `/locations/:locationId/bays/new` and `/locations/:locationId/bays/:bayId` or another pattern?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/141

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/141
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create Bays with Constraints and Capacity

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define bays with constraints (lift/equipment) so that scheduler assigns the right work to the right bay.

## Details
- Bay attributes: type (lift/alignment), supported services, capacity, required skills, status (active/out-of-service).

## Acceptance Criteria
- Bay created under a location.
- Out-of-service blocks assignments.
- Constraints queryable.

## Integrations
- Dispatch validates assignments against bay constraints.
- Workexec displays bay context during execution.

## Data / Entities
- Resource(Bay), ResourceConstraint, ResourceStatus

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

====================================================================================================