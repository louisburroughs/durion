STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

## 1. Story Header

**Title**  
[FRONTEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate (Search + Select Snapshot)

**Primary Persona**  
Service Advisor

**Business Value**  
Reduce time and errors when identifying the correct customer asset so the advisor can start an estimate/workflow on the correct vehicle with owner context.

---

## 2. Story Intent

**As a** Service Advisor  
**I want** to search vehicles by VIN, unit number, or license plate (supporting partial search) and select a result  
**So that** I can quickly confirm the right vehicle and load a full vehicle + owner snapshot to begin an estimate.

### In-scope
- A dedicated vehicle lookup UI in the frontend (Vue 3 + Quasar) reachable from the POS workflow.
- Search by a single free-text query that matches VIN, unit number, or license plate.
- Display ranked results including owner account context.
- Selecting a result loads “full vehicle + owner snapshot” and returns it to the calling context (e.g., estimate creation flow) in a deterministic way.
- Error/empty/loading states, basic input validation, and accessibility.

### Out-of-scope
- Creating/editing vehicles, owners, or contacts (CRM is SoR; this story is lookup/select only).
- Vehicle deduplication/merge flows.
- Defining backend ranking algorithm, backend pagination strategy, or the definitive snapshot schema (frontend will follow backend contract once confirmed).
- Implementing estimate creation itself (only integration handoff).

---

## 3. Actors & Stakeholders

- **Service Advisor (end user):** Performs search and selection at counter/in shop intake.
- **Workorder Execution (downstream workflow):** Consumes selected `vehicleId` (and possibly snapshot payload) to initialize estimate context.
- **CRM domain services (backend):** Executes vehicle search and returns vehicle+owner snapshot.
- **Security/RBAC policy owners:** Define which roles/scopes can view vehicle and owner data in POS.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User has permission to search and view vehicle and owner/account context (exact permission/scope TBD → Open Questions).
- CRM backend endpoints for:
  - vehicle search (by query)
  - vehicle+owner snapshot retrieval by `vehicleId`
  are available and reachable from Moqui.

### Dependencies
- Backend story reference: `durion-positivity-backend#103` (currently indicates clarification required on ranking, partial match definition, snapshot schema, and result limits).
- Moqui frontend app routing/screen conventions from `durion-moqui-frontend` README (not provided here; must be followed by implementer).
- Workorder Execution integration: calling screen/workflow must define how it receives the selected vehicle context (callback/return parameters).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an “Start Estimate” (or similar intake) flow: user opens **Vehicle Lookup** to find/select a vehicle.
- Secondary: standalone “Vehicle Search” tool screen for quick lookup (optional; only if aligned with existing navigation).

### Screens to create/modify
1. **Screen:** `VehicleLookup` (new)
   - Contains:
     - Search input form
     - Results list/grid
     - Selection action per result
2. **(Optional) Subscreen/Dialog:** `VehicleLookupResults` (if project pattern separates search/results)
3. **Transition return target:** a transition that returns selected vehicle context to caller (details depend on existing Moqui screen flow conventions).

### Navigation context
- `VehicleLookup` should accept optional input parameters from caller:
  - `returnScreen` (string) or `returnUrl` (string) (TBD by Moqui convention)
  - `sourceWorkflow` (e.g., `estimate-create`) for audit/observability tags
- On selection, navigate back to caller with:
  - `vehicleId`
  - optionally `ownerAccountId` and/or snapshot payload (TBD)

### User workflows
#### Happy path
1. Advisor opens Vehicle Lookup.
2. Enters query (VIN/unit/plate) and submits.
3. Sees ranked results with enough detail to confirm the correct vehicle.
4. Clicks/selects a result.
5. System loads full vehicle+owner snapshot.
6. System returns to caller with selected vehicle context and indicates success.

#### Alternate paths
- Broad search returns many results → show “refine your search” guidance and limited list (limit behavior depends on backend).
- No matches → show empty state and allow user to change query.
- Backend errors → show inline error banner and allow retry.

---

## 6. Functional Behavior

### Triggers
- User submits search form (Enter key or Search button).
- User selects a vehicle from results.

### UI actions
- Search input:
  - Single text field `query`
  - Search button disabled until minimum length requirement met (TBD; if undefined, do not enforce beyond non-empty).
- Results:
  - Each row shows:
    - `description` (e.g., year/make/model or description string)
    - VIN (possibly masked/shortened display)
    - unit number
    - license plate
    - owner account name
  - “Select” action per result

### State changes (frontend)
- `idle` → `searching` → `showingResults | empty | error`
- On selection: `selecting` → `selected | error`
- Persist selected vehicle context only in-memory for the current workflow unless caller specifies persistence.

### Service interactions (Moqui-oriented)
- Search submit invokes a Moqui service call (or REST call through Moqui) to CRM search endpoint.
- Selection invokes a second service call to load full snapshot for selected `vehicleId`.
- Both calls must capture and surface:
  - `400` invalid input
  - `403` unauthorized
  - `404` vehicle not found (on snapshot load)
  - `409` conflict (if backend uses it for concurrent updates)
  - `5xx` server errors

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `query` is required (non-empty after trim).
- If backend defines minimum characters for partial search, enforce same rule client-side to reduce unnecessary calls (TBD → Open Questions).
- No client-side assumptions about VIN format beyond trimming whitespace; backend remains source of truth for validation.

### Enable/disable rules
- Disable Search button when:
  - query is empty/whitespace
  - currently searching
- Disable Select action while snapshot is loading for a selected row (prevent double select).

### Visibility rules
- Results area visible after first search attempt.
- Empty state visible when search returns 0 results.
- Error banner visible when service call fails; include retry action.

### Error messaging expectations
- Map backend validation errors to field-level message on `query` when possible.
- For authorization errors (403), show “You don’t have access to search vehicles” (do not leak details).
- For server errors, show generic message plus correlation/reference if available.

---

## 8. Data Requirements

> Note: Backend contract is not finalized; below lists required fields as implied by story, plus “TBD” placeholders.

### Entities involved (conceptual)
- **Vehicle** (CRM-owned)
- **Owner Account / Party** (CRM-owned)
- **Customer Snapshot / Vehicle Snapshot** (CRM read model)

### Fields (type, required, defaults)

#### Search request
- `query` (string, required, trimmed, no default)

#### Search result item (summary)
- `vehicleId` (UUID/string, required)
- `vin` (string, optional in display but expected present)
- `unitNumber` (string, optional)
- `licensePlate` (string, optional)
- `description` (string, required for identification in UI; if missing, construct from available fields only if backend provides year/make/model—otherwise show “(No description)”)
- `owner.accountId` (UUID/string, required)
- `owner.name` (string, required)

#### Snapshot on selection (minimum needed by downstream)
- `vehicleId` (required)
- `owner.accountId` (required)
- Additional snapshot fields: **TBD by backend contract** (Open Question)

### Read-only vs editable
- All fields are read-only in this story.

### Derived/calculated fields (frontend)
- `displayVin`: if VIN is long, show last 6–8 characters with prefix masking (ONLY if allowed by security policy; otherwise show full VIN or omit—TBD).
- `displayTitle`: prefer `description`; else combine `unitNumber`/`licensePlate`.

---

## 9. Service Contracts (Frontend Perspective)

> Backend references show proposed endpoints but not confirmed. Frontend must implement against the actual Moqui service layer once finalized.

### Load/view calls
- None on initial entry (unless caller passes a pre-filled query).

### Create/update calls
- None.

### Submit/transition calls
1. **Vehicle search**
   - Proposed: `POST /api/v1/vehicles/search` with body `{ "query": "<string>" }`
   - Response: `200 OK` with `{ results: [ ... ] }` or `[]` depending on final contract (TBD).
2. **Vehicle snapshot retrieval**
   - Proposed: `GET /api/v1/vehicles/{vehicleId}?include=owner`
   - Response: `200 OK` with snapshot payload.

### Error handling expectations
- `400`: show field-level error (query) if message indicates invalid query; otherwise show banner.
- `403`: show authorization error; do not retry automatically.
- `404` (snapshot): show “Vehicle no longer available” and return to results.
- `409`: show “Vehicle data changed; please retry” and allow reload.
- `5xx`: show banner with retry.

---

## 10. State Model & Transitions

### Allowed UI states
- `idle`
- `searching`
- `results`
- `empty`
- `error`
- `loadingSnapshot`
- `snapshotError`
- `completed` (handoff to caller)

### Role-based transitions
- If user lacks required permission:
  - entering screen: either block entirely (redirect/403 screen) or show disabled UI with message (TBD by app pattern).
- Otherwise:
  - `idle → searching → results|empty|error`
  - `results → loadingSnapshot → completed|snapshotError`

### UI behavior per state
- `searching`: show spinner, keep query editable but disable Search to prevent concurrent submissions.
- `results`: enable selection.
- `loadingSnapshot`: disable all selects; optionally show row-level progress.
- `error/snapshotError`: preserve last successful results if available; allow retry.

---

## 11. Alternate / Error Flows

1. **Empty query submission**
   - Prevent submit client-side; show “Enter VIN, unit number, or plate”.
2. **No matches**
   - Show “No vehicles found” with suggestion to broaden/adjust query.
3. **Too many matches / truncated results**
   - If backend indicates truncation (flag/field TBD), show “Results limited; refine search”.
4. **Unauthorized (403)**
   - Show access error and provide “Back” to caller.
5. **Network timeout**
   - Show retry banner; do not clear query.
6. **Concurrency/conflict (409) on snapshot**
   - Show message and allow reselect/reload.
7. **Vehicle deleted between search and select (404)**
   - Show message; remove/refresh results on next search.

---

## 12. Acceptance Criteria

### Scenario 1: Search by exact VIN returns ranked matches
**Given** the Service Advisor is authenticated and authorized to search vehicles  
**And** the CRM backend contains a vehicle with VIN `1FTFW1E54KFA12345` and owner account name `FleetCo Inc.`  
**When** the advisor enters `1FTFW1E54KFA12345` and submits search  
**Then** the UI displays a results list containing a row with VIN `1FTFW1E54KFA12345`  
**And** that row shows owner account context including `FleetCo Inc.`

### Scenario 2: Partial unit number search returns multiple matches
**Given** the advisor is authorized  
**And** vehicles exist with unit numbers `FLEET-A501` and `FLEET-A502`  
**When** the advisor searches for `FLEET-A5`  
**Then** the UI displays both matching vehicles in results  
**And** each result includes `vehicleId` and owner account name

### Scenario 3: No matches shows empty state
**Given** the advisor is authorized  
**When** the advisor searches for `NONEXISTENT`  
**Then** the UI shows “No vehicles found”  
**And** no results rows are displayed

### Scenario 4: Selecting a result loads full snapshot and returns context to caller
**Given** a prior search returned a result with `vehicleId = veh-abc-123`  
**When** the advisor selects that result  
**Then** the frontend requests the vehicle snapshot for `veh-abc-123`  
**And** on success, the frontend returns to the calling workflow with `vehicleId = veh-abc-123`  
**And** the calling workflow can access owner account context from the returned payload/params (exact mechanism TBD)

### Scenario 5: Backend validation error is shown to user
**Given** the advisor is authorized  
**When** the advisor submits an invalid query and the backend responds `400` with a validation message  
**Then** the UI displays an error message associated with the search input or as a banner  
**And** the advisor can correct the query and retry

### Scenario 6: Unauthorized access is handled safely
**Given** the user is authenticated but not authorized to search vehicles  
**When** the user navigates to Vehicle Lookup or submits a search  
**Then** the UI shows an authorization error state  
**And** no vehicle/owner data is displayed

---

## 13. Audit & Observability

### User-visible audit data
- Not required for this UI (lookup only).

### Status history
- Not applicable.

### Traceability expectations
- Frontend should include in logs/telemetry (per workspace conventions):
  - `sourceWorkflow` (if provided)
  - search query length (avoid logging full VIN/plate if considered sensitive; policy TBD)
  - result count
  - selected `vehicleId`
  - correlation/request ID from backend if available

---

## 14. Non-Functional UI Requirements

- **Performance:** search should render results quickly; avoid excessive calls (debounce optional; see Safe Defaults).
- **Accessibility:** keyboard submit (Enter), focus management to results, accessible labels for inputs and buttons, screen-reader friendly empty/error states.
- **Responsiveness:** usable on typical POS tablet/desktop sizes.
- **i18n/timezone/currency:** not applicable (no monetary fields). Text must be compatible with i18n if project uses it (no hard-coded concatenations that break translation, where feasible).

---

## 15. Applied Safe Defaults

- **SD-UI-EMPTY-STATE**
  - **Assumed:** Explicit empty state when results are empty (“No vehicles found”).
  - **Why safe:** Pure UI ergonomics; does not change domain rules.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria
- **SD-UI-LOADING-STATE**
  - **Assumed:** Loading indicators and disabling actions during in-flight requests.
  - **Why safe:** Standard UI behavior; no domain policy impact.
  - **Impacted sections:** Functional Behavior, State Model & Transitions
- **SD-ERR-MAP-HTTP**
  - **Assumed:** Standard mapping of HTTP 400/403/404/409/5xx to user-facing errors with retry where appropriate.
  - **Why safe:** Error-handling mapping derived from implied backend contract.
  - **Impacted sections:** Service Contracts, Alternate / Error Flows, Acceptance Criteria

---

## 16. Open Questions

1. **Backend API contract confirmation (blocking):** What are the exact endpoints, methods, and response shapes for (a) vehicle search and (b) vehicle+owner snapshot? (The story references `POST /api/v1/vehicles/search` and `GET /api/v1/vehicles/{vehicleId}?include=owner` but marks snapshot schema as unclear.)
2. **Ranking logic (blocking):** What ranking rules should the UI expect (exact match vs starts-with vs contains), and does backend provide an explicit `rankScore`/`matchType` field?
3. **Partial search rules (blocking):** Is there a minimum query length before search is allowed? Is matching `contains` or `startsWith` for VIN/unit/plate?
4. **Result limiting/pagination (blocking):** What is the maximum results count returned? Will backend indicate truncation and/or support pagination parameters?
5. **Permission model (blocking):** What roles/scopes authorize vehicle search and snapshot view in the POS frontend (e.g., `crm.vehicle.read`, `crm.snapshot.read`), and what should the UI do on missing permission (hard block vs read-only message)?
6. **PII/sensitive display policy (blocking):** Are full VIN and license plate allowed to be displayed and logged in the POS UI? If masking is required, what masking standard should be used?
7. **Caller handoff mechanism (blocking):** In Moqui screens, how should VehicleLookup return the selected context to the estimate creation flow (return transition with parameters, shared context, or redirect with query params)? Which calling screen/route is the primary integration target?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/167


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/167
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to search vehicles by VIN, unit number, or plate** so that **I can quickly start an estimate for the correct asset**.

## Details
- Partial VIN and unit searches.
- Return matches including owner account context.

## Acceptance Criteria
- Search returns ranked matches.
- Selecting a match returns full vehicle + owner snapshot.

## Integration Points (Workorder Execution)
- Estimate creation uses vehicle search/selection.

## Data / Entities
- Vehicle search endpoint/index

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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