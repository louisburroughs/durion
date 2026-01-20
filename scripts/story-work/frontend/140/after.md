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

**Title:** [FRONTEND] Locations: Create & Manage Mobile Units, Coverage Rules, and Travel Buffer Policies

**Primary Persona:** Shop Administrator (Admin)

**Business Value:** Enables configuration of mobile service resources (capabilities, base location, coverage, travel buffer policy, max daily jobs) so scheduling/work execution can select eligible **ACTIVE** mobile units and apply travel buffers reliably.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Shop Administrator  
- **I want** to create and manage Mobile Units, define their capabilities and coverage rules, and configure Travel Buffer Policies  
- **So that** mobile appointments can be scheduled with correct eligibility filtering and travel buffer behavior, and out-of-service units are not schedulable.

### In-Scope
- Frontend screens and flows to:
  - Create, view, and update **Mobile Units**
  - Assign **capability IDs** (validated by Service Catalog via backend)
  - Define **coverage rules** that link Mobile Units to **Service Areas** with priority and effective windows
  - Create and view **Travel Buffer Policies** (FIXED_MINUTES, DISTANCE_TIER)
- UI validation aligned to backend rules (immutability, required fields, tier validation shape, etc.)
- Moqui screen/form/service wiring needed to call backend endpoints and manage transitions
- Basic audit visibility (created/updated timestamps; status history if available via API)

### Out-of-Scope
- Computing travel distance (caller/workexec owns distance computation per backend reference)
- Scheduling availability computation (workexec owns)
- HR “TravelTimeApproved” event emission (explicitly out of scope per backend reference)
- Authorization model design (security domain); frontend will respect backend 401/403 responses and hide actions if permissions are available in session context (if not, show read-only + errors)

---

## 3. Actors & Stakeholders
- **Shop Administrator (Admin):** configures mobile units, coverage, and policies.
- **Scheduler / Work Execution user/system:** consumes mobile unit eligibility for scheduling (not configured here, but depends on correct configuration).
- **Service Catalog domain/system:** authoritative source for capabilities; backend validates IDs.
- **Security/Access Control stakeholders:** define who can manage mobile units/policies.

---

## 4. Preconditions & Dependencies
- User is authenticated.
- Backend location APIs exist and are reachable (see Service Contracts).
- Base Locations exist in Location system (baseLocationId is required and must exist).
- Capability ID lookup/validation depends on Service Catalog availability (backend may return 503 if unavailable).
- Service Areas exist (or must be created elsewhere). This frontend story includes coverage rule linking to Service Areas, but **Service Area CRUD UI is not specified in provided inputs** (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Left nav / admin menu: **Locations → Mobile Units**
- (Optional) Locations → Travel Buffer Policies
- (Optional) Locations → Service Areas (only if included/confirmed; otherwise linking picker only)

### Screens to create/modify (Moqui)
1. **`/locations/mobile-units`** (list)
   - Search/filter (status, base location)
   - Row actions: View/Edit
   - Primary action: Create Mobile Unit
2. **`/locations/mobile-units/create`** (create form)
3. **`/locations/mobile-units/:mobileUnitId`** (detail)
   - Read-only summary + editable sections
4. **`/locations/mobile-units/:mobileUnitId/edit`** (edit form; may be same as detail with edit mode)
5. **`/locations/mobile-units/:mobileUnitId/coverage`** (coverage rules management)
6. **`/locations/travel-buffer-policies`** (list)
7. **`/locations/travel-buffer-policies/create`** (create form)
8. **`/locations/travel-buffer-policies/:travelBufferPolicyId`** (detail)

> Moqui implementation note: these can be implemented as a screen tree under `component://.../screen/locations/MobileUnits.xml` etc., using subscreens for list/create/detail/coverage.

### Navigation context
- Breadcrumbs: Locations > Mobile Units > {Unit Name}
- Return-to-list after create/update.

### User workflows
**Happy path: Create Mobile Unit**
1. Admin opens Mobile Units list → Create.
2. Enters name, selects base location, sets status (default ACTIVE), max daily jobs.
3. Selects capability IDs (multi-select).
4. Selects travel buffer policy (required when status ACTIVE).
5. Saves → redirected to detail page.

**Alternate: Configure coverage**
1. From unit detail, navigate to Coverage tab/page.
2. Add one or more coverage rules linking to Service Areas, set priority and effective dates.
3. Save changes atomically (replace set) to avoid partial config.

**Alternate: Create travel buffer policy**
1. Open Travel Buffer Policies → Create.
2. Choose type FIXED_MINUTES or DISTANCE_TIER.
3. Enter configuration; validate tiers.
4. Save and reuse in Mobile Unit form.

**Alternate: Out-of-service blocks scheduling**
- Admin sets status OUT_OF_SERVICE; UI communicates that unit is not schedulable (informational).

---

## 6. Functional Behavior

### Triggers
- User opens list/detail/create screens.
- User submits create/update forms.
- User adds/edits/removes coverage rules.
- User creates a travel buffer policy.

### UI actions
- **List:** fetch paged list; filter by status/base location.
- **Create Mobile Unit:** client-side validation then POST.
- **Edit Mobile Unit:** PATCH (preferred) or PUT depending on backend; do not allow editing immutable fields (code immutability applies to Location.code; MobileUnit has no immutable field specified besides IDs).
- **Coverage management:** edit a grid of rules; submit as a single “replace coverage rules” action if backend supports it (per backend story requirement for atomic replacement).
- **Policy creation:** dynamic form based on type; validate tier constraints client-side before submit.

### State changes (frontend-observed)
- MobileUnit.status changes among ACTIVE / INACTIVE / OUT_OF_SERVICE.
- Coverage rules effective windows change eligibility at time T (informational; eligibility query is separate endpoint).

### Service interactions (high-level)
- Load base locations list for selection.
- Load capabilities list for selection (endpoint TBD; may be via backend aggregator).
- Load service areas list for selection.
- CRUD Mobile Units.
- CRUD/Read Travel Buffer Policies.
- Manage coverage rules per mobile unit.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
Mobile Unit:
- `name` required; show inline error if empty.
- `baseLocationId` required; must be a valid selection.
- `maxDailyJobs` required integer, `>= 0`.
- `status` required enum: `ACTIVE | INACTIVE | OUT_OF_SERVICE`.
- `travelBufferPolicyId`:
  - Required when `status = ACTIVE` (enforce in UI).
  - Optional/disabled when status is not ACTIVE (UI should allow blank).
- Capabilities:
  - UI allows selecting multiple capability IDs.
  - If backend returns `400` unknown capability IDs, show error and keep user selections.

Coverage Rule:
- Must select `serviceAreaId`.
- `priority` integer; lower = higher priority (display helper text).
- Effective window:
  - If both provided, enforce `effectiveEndAt > effectiveStartAt`.
  - Allow null start (immediate) and/or null end (indefinite).

Travel Buffer Policy:
- `name` required.
- `policyType` required: FIXED_MINUTES or DISTANCE_TIER.
- For `FIXED_MINUTES`: require `bufferMinutes >= 0` (exact config key TBD).
- For `DISTANCE_TIER`:
  - At least one tier required.
  - Each tier has `bufferMinutes >= 0`.
  - `maxDistance` must be strictly increasing for non-null values.
  - Must include a catch-all tier with `maxDistance = null`.
  - `unit` default “KM” (per backend reference) but must not be guessed if backend expects different; see Open Questions.

### Enable/disable rules
- Disable “Save” until required fields are valid.
- In Mobile Unit form:
  - Travel buffer policy selector enabled + required only when status ACTIVE.
- In tier editor:
  - Prevent adding multiple catch-all tiers.
  - Enforce numeric-only distance inputs (where applicable).

### Visibility rules
- Show Coverage management only after Mobile Unit is created (needs mobileUnitId).
- Show an informational banner when status is OUT_OF_SERVICE: “Not schedulable while out of service.”

### Error messaging expectations
Map backend errors:
- `400 Bad Request`: show field-level errors when possible; otherwise show summary “Validation error”.
- `401/403`: show “You do not have permission” and disable edit actions.
- `409 Conflict`: show “A Mobile Unit with this name already exists for the selected base location.”
- `503 Service Unavailable` (capability validation dependency down): show “Service Catalog unavailable; please retry.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **MobileUnit**
  - `mobileUnitId` (UUID, read-only)
  - `name` (string, required)
  - `status` (enum, required)
  - `baseLocationId` (Long, required)
  - `travelBufferPolicyId` (UUID, required when ACTIVE)
  - `maxDailyJobs` (int, required, >=0)
  - `capabilityIds[]` (string/UUID IDs, required? optional; backend allows empty not specified)
  - `createdAt`, `updatedAt` (timestamps, read-only)
- **ServiceArea** (for picker)
  - `serviceAreaId` (UUID)
  - `name` (string)
  - `countryCode` (string)
- **MobileUnitCoverageRule**
  - `coverageRuleId` (UUID, read-only if editing existing; may be generated by backend)
  - `serviceAreaId` (UUID, required)
  - `priority` (int, required)
  - `effectiveStartAt` (timestamp, nullable)
  - `effectiveEndAt` (timestamp, nullable)
- **TravelBufferPolicy**
  - `travelBufferPolicyId` (UUID, read-only)
  - `name` (string, required)
  - `policyType` (enum, required)
  - `policyConfiguration` (JSON; edited via typed UI)

### Read-only vs editable by state/role
- If user lacks `location.mobile-unit.manage`, all forms are read-only; list/detail still viewable if backend allows.
- `mobileUnitId`, timestamps are always read-only.

### Derived/calculated fields
- “Schedulable” indicator derived in UI: `status === ACTIVE` (and optionally “has travel buffer policy + coverage rules”, but backend eligibility logic is separate; do not over-assert).

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend reference provides high-level endpoints but states “detailed API contracts TBD”. This creates blocking clarification needs for exact payloads, list endpoints, and coverage replacement semantics.

### Load/view calls
- `GET /v1/mobile-units` (list; query params TBD: status, baseLocationId, paging)
- `GET /v1/mobile-units/{mobileUnitId}` (detail)
- `GET /v1/locations?status=...` (for base location picker) OR existing Moqui location screen service (TBD)
- `GET /v1/service-areas` (for service area picker; endpoint not specified in backend excerpt)
- Capabilities lookup:
  - `GET /v1/capabilities` OR service-catalog proxy endpoint (TBD)

### Create/update calls
- `POST /v1/mobile-units`
- `PATCH /v1/mobile-units/{mobileUnitId}` (preferred)
- Coverage rules:
  - Either `PUT /v1/mobile-units/{mobileUnitId}/coverage-rules:replace`
  - Or `POST/PATCH` per rule under `/v1/locations/{locationId}/...` (not specified)
  - Must be atomic replacement per backend reference.
- Travel buffer policies:
  - `POST /v1/travel-buffer-policies`
  - `GET /v1/travel-buffer-policies/{id}`
  - (Optional) `GET /v1/travel-buffer-policies` for picker/list (not specified but implied)

### Submit/transition calls
- Status update is via PATCH on mobile unit:
  - Set `status = OUT_OF_SERVICE` or `INACTIVE` etc.

### Error handling expectations
- Parse JSON error responses into:
  - fieldErrors map (if provided)
  - general message
  - correlationId (if included)
- Show toast/banner for non-field errors; keep user input intact on failures.

---

## 10. State Model & Transitions

### Allowed states (Mobile Unit)
- `ACTIVE`
- `INACTIVE`
- `OUT_OF_SERVICE`

### Role-based transitions
- Users with `location.mobile-unit.manage` can transition between any states (backend excerpt implies status lifecycle controlled via status; no other constraints specified).

### UI behavior per state
- ACTIVE:
  - Requires travel buffer policy selection.
  - Show “Schedulable” indicator.
- INACTIVE / OUT_OF_SERVICE:
  - Travel buffer policy becomes optional; UI may allow clearing.
  - Show “Not schedulable” banner.
  - Coverage editing remains allowed (no rule forbids), but consider warning: “Changes won’t affect scheduling until ACTIVE.”

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields: block submit; show inline messages.
- Invalid effective window (end <= start): block submit on coverage rule editor.

### Concurrency conflicts
- If backend uses optimistic locking/versioning and returns `409 Conflict` or `412 Precondition Failed` on stale update:
  - Show “This record was updated by someone else. Reload to continue.”
  - Provide reload action; preserve local edits if feasible (draft state).

### Unauthorized access
- On `401`: redirect to login (per app convention).
- On `403`: show not authorized; disable create/edit buttons; keep user on view mode.

### Empty states
- No mobile units: show empty state with “Create Mobile Unit”.
- No travel buffer policies: show empty picker state with “Create Policy”.
- No service areas: block coverage creation and show link/instructions (destination TBD).

---

## 12. Acceptance Criteria

### Scenario 1: Create Mobile Unit (Happy Path)
**Given** I am authenticated and authorized with permission `location.mobile-unit.manage`  
**And** at least one base location exists  
**And** at least one travel buffer policy exists  
**When** I navigate to Locations → Mobile Units → Create  
**And** I enter a unique name, select a base location, set status to ACTIVE, set max daily jobs to a non-negative integer, select a travel buffer policy, and optionally select capability IDs  
**And** I click Save  
**Then** the system creates the Mobile Unit via the backend  
**And** I am redirected to the Mobile Unit detail screen  
**And** I see the persisted values including the generated `mobileUnitId`.

### Scenario 2: Enforce travel buffer policy required when ACTIVE
**Given** I am creating or editing a Mobile Unit  
**When** I set status to ACTIVE  
**Then** the Travel Buffer Policy field becomes required  
**And** Save is disabled (or submit shows an inline error) until a policy is selected.

### Scenario 3: Duplicate Mobile Unit name returns conflict
**Given** a Mobile Unit named “Mobile Van 1” already exists for the selected base location  
**When** I submit a new Mobile Unit with name “Mobile Van 1” and the same base location  
**Then** I see an error message indicating the name is already in use  
**And** the form remains populated for correction.

### Scenario 4: Configure coverage rules atomically
**Given** a Mobile Unit exists  
**When** I open the Coverage page and add/update/remove coverage rules (service area, priority, effective window)  
**And** I click Save Coverage  
**Then** the frontend submits the coverage set in a single atomic request (replace semantics)  
**And** on success I see the updated rules list exactly as saved.

### Scenario 5: Reject invalid effective window in UI
**Given** I am editing a coverage rule  
**When** I set `effectiveEndAt` to a time that is less than or equal to `effectiveStartAt`  
**Then** I see an inline validation error  
**And** I cannot save coverage until corrected.

### Scenario 6: Create DISTANCE_TIER travel buffer policy
**Given** I am authorized with `location.mobile-unit.manage`  
**When** I create a Travel Buffer Policy with type DISTANCE_TIER  
**And** I enter tiers with strictly increasing `maxDistance` values and include a catch-all tier with `maxDistance = null`  
**Then** the policy is created successfully  
**And** viewing the policy shows the same tier configuration.

### Scenario 7: Reject invalid DISTANCE_TIER tier configuration
**Given** I am creating a DISTANCE_TIER policy  
**When** I attempt to save tiers that are not strictly increasing or omit the catch-all tier  
**Then** the UI blocks submission with clear validation messages  
**Or** if submitted and backend returns 400, the UI shows the backend validation error and keeps the entered tiers for correction.

### Scenario 8: OUT_OF_SERVICE messaging and effect
**Given** a Mobile Unit exists  
**When** I set its status to OUT_OF_SERVICE and save  
**Then** the detail screen indicates the unit is not schedulable while OUT_OF_SERVICE.

---

## 13. Audit & Observability
- Display on detail screens (if returned by API): `createdAt`, `updatedAt`, `updatedBy` (if available).
- Include `X-Correlation-Id` on frontend requests if the app already supports it; otherwise surface backend-provided correlation ID in error dialogs when present.
- Log (frontend console / app logger) key actions at INFO level: create/update mobile unit, save coverage, create policy (no PII beyond names/IDs).

---

## 14. Non-Functional UI Requirements
- **Performance:** Lists should load within 2s on typical broadband for up to 50 items/page; use pagination.
- **Accessibility:** All form inputs labeled; keyboard navigable; validation messages announced via ARIA live region (Quasar support).
- **Responsiveness:** Usable on tablet widths (admin may use tablet on shop floor).
- **i18n/timezone:** Effective date/time fields must clearly indicate timezone context; do not assume conversion rules (see Open Questions). Display times in location timezone if provided; otherwise user/session timezone.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state messaging and primary CTA on list screens; qualifies as safe UI ergonomics; impacts UX Summary, Error/Empty Flows.
- SD-UX-PAGINATION: Default server-side pagination for list views (page size 25); qualifies as safe UI ergonomics for large datasets; impacts UX Summary, Service Contracts.
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/401/403/409/503 to inline vs banner errors; qualifies as safe because it does not change domain policy; impacts Business Rules, Error Flows.

---

## 16. Open Questions
1. **Service Area management scope:** Does the frontend need **CRUD screens for Service Areas** (postal-code sets), or only a picker to link existing Service Areas to coverage rules? If CRUD is required, confirm endpoints and required fields.
2. **Coverage rules API contract:** What are the exact endpoints and payloads for creating/updating coverage rules, and what is the **atomic replacement** mechanism (single “replace” endpoint vs batch semantics)?
3. **Capabilities lookup endpoint:** What endpoint should the frontend call to list valid capability IDs/names for selection (service-catalog directly, or a location-service proxy)? Provide response shape.
4. **Mobile unit list endpoint + filters:** Confirm `GET /v1/mobile-units` exists and supports filtering by `status` and `baseLocationId`, plus pagination parameters and response envelope shape.
5. **Travel buffer policy configuration schema (FIXED_MINUTES):** What is the exact `policyConfiguration` JSON shape for FIXED_MINUTES (key names/types)?
6. **DISTANCE_TIER unit handling:** Is `unit` always `"KM"` in stored config, or can UI allow MI entry? Backend states MI may be provided as input for tier selection (caller side), but for **policy config** it shows `unit: "KM"`. Confirm what UI should send/store.
7. **Timezone display rule for coverage effective windows:** Should effectiveStartAt/effectiveEndAt be entered/displayed in the **base location timezone**, the **user/session timezone**, or as UTC with offsets? (Location domain contract forbids assuming timezone conversion rules.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Locations: Create Mobile Units and Coverage Rules — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/140

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Locations: Create Mobile Units and Coverage Rules  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/140  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create Mobile Units and Coverage Rules

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Admin**, I want to define mobile units with service capabilities and coverage so that mobile appointments can be scheduled with travel buffers.

## Details  
- Mobile unit attributes: capabilities, base location, service area tags, travel buffer policy, max daily jobs.

## Acceptance Criteria  
- Mobile unit created and assignable.  
- Coverage/buffers configurable.  
- Out-of-service blocks scheduling.

## Integrations  
- HR receives travel time.  
- Workexec stores mobileUnitId context for mobile workorders.

## Data / Entities  
- Resource(MobileUnit), CoverageRule, TravelBufferPolicy

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