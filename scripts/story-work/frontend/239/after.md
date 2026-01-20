## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Estimate: Create Draft Estimate

### Primary Persona
Service Advisor / Front Desk

### Business Value
Enable fast creation of a Draft estimate tied to a specific customer + vehicle so the advisor can immediately begin quoting work (line items) with correct shop defaults and an auditable creation record.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor / Front Desk user  
- **I want** to create a new **Draft** estimate for a selected Customer and Vehicle  
- **So that** I can start building a quote and route to the estimate workspace with the newly created estimate identifier.

### In Scope
- UI flow to select/create Customer record (via navigation to existing customer/vehicle screens if needed).
- UI flow to select/create Vehicle record and capture context fields (VIN/plate, odometer, notes) when required.
- Create Draft Estimate action (frontend → Moqui → backend API) and routing to estimate workspace on success.
- Display created estimate identifier and status (Draft).
- Permission/authorization handling for estimate creation attempts.
- User-visible confirmation + error messaging.
- Audit visibility requirements (show audit/created metadata that backend returns; do not invent audit storage).

### Out of Scope
- Estimate approval workflow (methods, signatures, decline/reopen) beyond creating the initial Draft.
- Work order creation/conversion.
- Adding parts/labor line items (only navigate to workspace where that will occur).
- Configuring shop/location defaults, currency, tax region (configuration ownership is outside this story).
- Implementing backend persistence or audit generation (frontend consumes).

---

## 3. Actors & Stakeholders
- **Primary Actor:** Service Advisor / Front Desk
- **Stakeholders:** Customer, Shop Manager (oversight), Technician (downstream consumer), Accounting (downstream reporting), Security/Audit (traceability)

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS UI.
- User has permission to create estimates (permission name is referenced as `ESTIMATE_CREATE` in backend story; frontend must be prepared for 403 if missing).
- Customer and Vehicle context is available:
  - Either already exists, or the UI provides navigation to create them before estimate creation.
- Backend endpoint exists to create estimate and returns the created estimate with ID/number/status and defaulted fields (exact endpoint path is not provided in frontend inputs; see Open Questions).
- Moqui frontend project has a routing convention for “estimate workspace” screen (unknown; see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry Points
At least one of:
- Customer detail screen → “Create Estimate”
- Vehicle detail screen → “Create Estimate”
- Global “New Estimate” action that first collects Customer + Vehicle

(Exact existing entry screens are not provided; implement in a way consistent with current navigation, or add a single new entry screen and link from existing customer/vehicle screens.)

### Screens to create/modify
- **Create/Select Estimate Context Screen** (new or existing):
  - Collect/confirm `customerId`, `vehicleId`
  - Capture required context: VIN/plate, odometer, notes (either inline if missing, or via navigation to vehicle edit)
  - Action: **Create Estimate**
- **Estimate Workspace Screen** (existing or stubbed if not present):
  - Receives `estimateId` route parameter
  - Shows header summary: estimateNumber, status=Draft, customer, vehicle

### Navigation context
- After successful creation: transition to `EstimateWorkspace` with `estimateId`.
- Back navigation returns to previous context (customer/vehicle).

### User workflows
- **Happy path**
  1. User selects/creates customer
  2. User selects/creates vehicle + fills required context if prompted
  3. Clicks “Create Estimate”
  4. System creates Draft estimate, shows confirmation
  5. User is routed into estimate workspace/editor
- **Alternate paths**
  - Missing customer/vehicle: disable Create action and show required-field guidance
  - Backend validation errors (400): show field-specific errors
  - Unauthorized (403): show access blocked message
  - Not found (404): show record missing and offer to re-select
  - Network/server error: show retry option and do not navigate

---

## 6. Functional Behavior

### Triggers
- User presses **Create Estimate** from the estimate creation entry flow.

### UI actions
- Validate required context present before enabling submission:
  - `customerId` present
  - `vehicleId` present
- On submit:
  - Show loading state; prevent double-submit
  - Call Moqui transition that invokes backend create service
  - On success: route to estimate workspace with created `estimateId`
  - Display toast/banner confirmation including `estimateNumber` when available

### State changes (frontend)
- Local UI state: `submitting=true/false`, `errorState`, `createdEstimate` payload.
- No client-side persistence required beyond routing.

### Service interactions
- Create Draft Estimate request (frontend → Moqui → backend REST).
- Optional: load applicable defaults (location/currency/taxRegion) if backend does not return them; however backend story indicates defaults are set on creation and returned—frontend should rely on response.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **BR-UI-1:** Customer is required to create an estimate.
  - UI blocks submission until selected.
- **BR-UI-2:** Vehicle is required to create an estimate.
  - UI blocks submission until selected.
- **BR-UI-3:** If customer/vehicle is missing required fields needed for estimate creation (unspecified which), UI must prompt user to complete required context before allowing creation.
  - Implementation: if backend returns 400 with field errors, surface them and provide a CTA to edit missing data.

### Enable/disable rules
- Create button disabled when:
  - `customerId` is empty OR `vehicleId` is empty OR `submitting=true`.

### Visibility rules
- After creation, in estimate workspace header show:
  - `estimateNumber` (if provided)
  - `status` (must be Draft)
  - `customerId`/customer display name (if provided by API)
  - `vehicleId`/vehicle summary (if provided)

### Error messaging expectations
- 400: “Cannot create estimate. Please fix the highlighted fields.” + show field messages from backend.
- 403: “You don’t have permission to create estimates. Contact a manager.”
- 404: “Customer or vehicle not found. Please re-select.”
- 409 (if uniqueness collision or concurrency): “Estimate could not be created due to a conflict. Please retry.”
- 5xx/network: “Something went wrong creating the estimate. Try again.”

(Do not expose internal stack traces or IDs beyond correlationId if available.)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Estimate`
- `Customer`
- `Vehicle`
- `UserPermission` (checked implicitly via backend authorization; frontend may also gate UI if permissions are available in session payload)
- `AuditEvent` (view-only metadata; do not create from frontend)

### Fields
**Estimate (returned by backend create)**
- `estimateId` (UUID/string) — required
- `estimateNumber` (string) — required for display if present in response; otherwise show estimateId as fallback
- `status` (enum/string) — required, must equal `DRAFT`/`Draft` depending on API
- `customerId` (UUID/string) — required
- `vehicleId` (UUID/string) — required
- `shopId` or `locationId` (UUID/string) — required (backend references `locationId`; frontend input references `shop/location`)
- `currencyUomId` (string) — required
- `taxRegionId` (UUID/string) — required
- `createdBy` / `createdByUserId` — required for audit display if provided
- `createdDate` / `createdAtTimestamp` — required for audit display if provided

**Customer context (input to create)**
- `customerId` — required

**Vehicle context (input to create)**
- `vehicleId` — required
- Optional context capture (may be required by other domain rules not specified):
  - VIN / plate, odometer, notes (capture/edit via vehicle screen; do not invent requiredness)

### Read-only vs editable by state/role
- In this story, estimate is created; editing estimate fields is out of scope.
- `customerId` and `vehicleId` selection is editable before creation; not editable after redirect (handled by estimate workspace story later).

### Derived/calculated fields
- Defaulting: `shop/location`, `currencyUomId`, `taxRegionId` are derived by backend from configuration/session.

---

## 9. Service Contracts (Frontend Perspective)

> Note: exact Moqui service names and backend paths are not provided in inputs; below is an implementation contract the Moqui layer must map to the actual backend.

### Load/view calls
- (Optional) Search/select customer and vehicle:
  - `GET /api/customers?...` and `GET /api/vehicles?...` or existing Moqui entity-find screens.
  - If the project already has customer/vehicle selectors, reuse them.

### Create/update calls
- **Create Draft Estimate**
  - Preferred REST (from backend references): `POST /api/estimates`
  - Request body (minimum):
    ```json
    {
      "customerId": "<uuid>",
      "vehicleId": "<uuid>"
    }
    ```
  - Response: `201 Created` with created Estimate payload including `estimateId`, `estimateNumber`, `status`, defaulted fields.

### Submit/transition calls (Moqui)
- Moqui screen transition `createEstimate`:
  - Validates required parameters present
  - Invokes REST client to backend
  - On success sets `estimateId` in context and transitions to workspace screen.

### Error handling expectations
- Map backend HTTP errors to UI errors:
  - `400`: field errors or message shown inline
  - `403`: access denied screen/toast and keep user on same page
  - `404`: show not found and clear selection or allow re-select
  - `409`: show conflict retry message
  - `5xx`/timeout: show retry option; log correlationId if returned

---

## 10. State Model & Transitions

### Allowed states (Estimate lifecycle relevant to this story)
- `DRAFT` is the only state created by this flow.

### Role-based transitions
- Service Advisor initiates creation (authorization enforced by backend).
- No estimate status transitions in scope.

### UI behavior per state
- After creation, workspace header must show state = Draft and allow proceeding to add line items (out of scope for implementation, but navigation must land correctly).

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing customerId: prevent submit; show “Select a customer”
- Missing vehicleId: prevent submit; show “Select a vehicle”

### Validation failures (server-side 400)
- Show backend-provided messages; keep inputs intact.

### Concurrency conflicts
- If backend returns 409 (e.g., estimateNumber uniqueness collision), show retry. User can click Create again.

### Unauthorized access (403)
- Block creation; show permission guidance; do not retry automatically.
- If UI can detect permissions pre-emptively (session includes permission list), hide/disable Create Estimate entrypoint.

### Empty states
- No customers/vehicles found in selector: show empty-state guidance and CTA to create new customer/vehicle.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create draft estimate successfully
**Given** the Service Advisor is authenticated  
**And** the user has permission to create estimates  
**And** a Customer is selected with `customerId`  
**And** a Vehicle is selected with `vehicleId`  
**When** the user clicks “Create Estimate”  
**Then** the system sends a create-estimate request containing `customerId` and `vehicleId`  
**And** on success the UI routes to the Estimate Workspace for the returned `estimateId`  
**And** the Estimate Workspace displays the estimate `status` as `Draft`  
**And** the estimate identifier (`estimateNumber` or fallback `estimateId`) is visible.

### Scenario 2: Prevent creation when customer is missing
**Given** the Service Advisor is on the Create Estimate flow  
**And** no Customer is selected  
**When** the user attempts to create an estimate  
**Then** the UI must block submission  
**And** the UI must indicate that Customer is required  
**And** no create-estimate request is sent.

### Scenario 3: Prevent creation when vehicle is missing
**Given** the Service Advisor is on the Create Estimate flow  
**And** a Customer is selected  
**And** no Vehicle is selected  
**When** the user attempts to create an estimate  
**Then** the UI must block submission  
**And** the UI must indicate that Vehicle is required  
**And** no create-estimate request is sent.

### Scenario 4: Handle backend validation error (400)
**Given** the Service Advisor has selected a customer and vehicle  
**When** the create-estimate request is submitted  
**And** the backend responds with HTTP 400 and a message indicating missing/invalid required context  
**Then** the UI must remain on the creation screen  
**And** the UI must display the backend error message(s) in a user-readable way  
**And** the user must be able to correct the issue and retry.

### Scenario 5: Handle unauthorized attempt (403)
**Given** the user is authenticated  
**When** the user submits a create-estimate request  
**And** the backend responds with HTTP 403  
**Then** the UI must not navigate to the estimate workspace  
**And** the UI must display an access denied message  
**And** the attempt must be logged client-side with correlationId if available (without PII).

### Scenario 6: Handle not found (404)
**Given** the user selected a customerId or vehicleId that no longer exists  
**When** the create-estimate request is submitted  
**And** the backend responds with HTTP 404  
**Then** the UI must show a “not found” message  
**And** prompt the user to re-select the missing record  
**And** not navigate away.

---

## 13. Audit & Observability

### User-visible audit data
- In the estimate workspace header or “Details” panel, show (if returned by backend):
  - Created at (timestamp)
  - Created by (user id/name)
- If not returned, do not fabricate; show nothing or “—”.

### Status history
- Not in scope to display transitions; only show current status = Draft.

### Traceability expectations
- All create attempts should include/propagate correlationId if the Moqui HTTP client supports it.
- Client logs (console/dev logging per project conventions) must not include full customer PII; only IDs.

---

## 14. Non-Functional UI Requirements
- **Performance:** Create request should complete within 2s typical; show spinner if longer; allow retry on failure.
- **Accessibility:** All actions keyboard accessible; errors announced via ARIA live region; form fields have labels.
- **Responsiveness:** Works on tablet and desktop widths typical for POS.
- **i18n/timezone/currency:** Display timestamps in user locale/timezone if available; currency code shown as returned (no currency math in this story).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty state messaging and CTA when no customer/vehicle found; qualifies as safe UI ergonomics; impacts UX Summary, Alternate / Error Flows.
- SD-UX-DOUBLE-SUBMIT-GUARD: Disable submit and show loading during create call to prevent duplicate requests; safe and reversible UX behavior; impacts Functional Behavior, Error Flows.
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/403/404/409/5xx to user-friendly messages; safe error-handling mapping; impacts Business Rules, Alternate / Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. What is the **actual backend endpoint** and payload for creating an estimate in this system (confirm `POST /api/estimates` with `{customerId, vehicleId}`), and what is the exact response shape (field names: `status` casing, `locationId` vs `shopId`, `createdAt` name)?
2. What is the **canonical Moqui route/screen** for the “Draft estimate workspace” (screen path and required parameters)? If it does not exist yet, should this story create a minimal placeholder workspace screen that only shows header info?
3. Which **vehicle/customer fields** are considered “required context” for estimate creation (beyond IDs), so the frontend can proactively prompt before submit rather than relying only on backend 400 responses?
4. Is there an existing **permission/feature flag signal** in the frontend session to hide/disable “Create Estimate” pre-emptively, or should the UI rely solely on backend 403 handling?

---

## Original Story (Unmodified – For Traceability)
Title: [FRONTEND] [STORY] Estimate: Create Draft Estimate  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/239  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Create Draft Estimate

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Front Desk

## Trigger
A customer requests service or a quote for a vehicle (walk-in, phone, email, or fleet request).

## Main Flow
1. User selects or creates the Customer record.
2. User selects or creates the Vehicle record and captures context (VIN/plate, odometer, notes).
3. User clicks 'Create Estimate' and the system creates a Draft estimate with an identifier.
4. System sets default shop/location, currency, and tax region based on configuration.
5. User is taken to the Draft estimate workspace to add line items.

## Alternate / Error Flows
- Customer or Vehicle missing required fields → prompt user to complete required context.
- Estimate creation attempted without permissions → block and log access attempt.

## Business Rules
- Estimate starts in Draft state.
- Estimate identifier is unique per shop/location.
- Audit event is recorded on creation.

## Data Requirements
- Entities: Estimate, Customer, Vehicle, UserPermission, AuditEvent
- Fields: estimateId, status, customerId, vehicleId, shopId, currencyUomId, taxRegionId, createdBy, createdDate

## Acceptance Criteria
- [ ] A Draft estimate is created with required customer and vehicle context.
- [ ] Estimate status is Draft and visible.
- [ ] Creation is recorded in audit trail.

## Notes for Agents
Keep estimate creation decoupled from approval and workorder logic. Establish baseline validations and defaulting rules.


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