STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Workexec: Create Draft Estimate from Appointment

### Primary Persona
Service Advisor

### Business Value
Reduce manual re-entry and ensure quote-to-cash starts from the authoritative appointment context (customer/vehicle/location/requested services) by creating a draft estimate directly from an appointment with idempotent behavior.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to create a **Draft** estimate in **workexec** from an appointment  
- **So that** the estimate is initialized with operational context and can immediately be reviewed/edited/approved through the normal estimate workflow.

### In-scope
- Add a **frontend action** from an Appointment context: “Create Draft Estimate”.
- Call the backend operation to create the estimate from the appointment (idempotent).
- Handle “already created” idempotent responses by navigating to the existing estimate.
- Persist/linkage visibility in UI (at least show resulting `estimateId` and that it’s linked to this appointment).

### Out-of-scope
- Appointment creation/editing (shop management domain).
- Estimate line-item editing workflows beyond confirming the created draft exists (those belong to estimate authoring stories).
- Asynchronous event handling beyond what the frontend needs to proceed (unless clarified as required).
- Any changes to approval configuration logic or estimate state machine beyond ensuring initial status is `DRAFT`.

---

## 3. Actors & Stakeholders
- **Primary actor:** Service Advisor (POS user)
- **System actors:**
  - **Frontend (Moqui/Quasar UI)**: initiates create-from-appointment and routes to estimate view.
  - **Workexec backend**: system of record for Estimate lifecycle.
  - **Shopmgr backend (implied)**: system of record for Appointment (frontend likely loads appointment from here or via Moqui integration).
- **Stakeholders:** Shop Manager, Customer (indirect)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS.
- User can view the target appointment.
- Appointment is not cancelled (rule referenced in backend story).

### Dependencies (blocking to implement precisely)
1. **Backend endpoint contract for “Create Estimate From Appointment” as available to the Moqui frontend** is not specified in provided frontend story text. Backend reference describes a command/event integration, but the Moqui UI needs a concrete callable interface.
2. Appointment retrieval contract (how the frontend obtains appointment context) is not provided.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an **Appointment detail** screen: primary action button **“Create Draft Estimate”**.
- Optional: from Appointment list row action (if appointment list exists; only if already present in app navigation).

### Screens to create/modify
1. **Modify**: Appointment detail screen (existing) to add:
   - Action: “Create Draft Estimate”
   - Display: linked `estimateId` (if already created)
2. **Create** (if not existing): A minimal **Estimate detail** screen route that can accept `estimateId` and show core status and header fields (or route to an existing estimate screen if already implemented).
3. **Create**: A lightweight **confirmation/result** panel or toast pattern after successful creation (may be inline, not a separate screen).

### Navigation context
- User is on `/appointments/:appointmentId` (exact route TBD per repo conventions).
- On success, navigate to `/workexec/estimates/:estimateId` (exact Moqui screen path TBD).

### User workflows
**Happy path**
1. SA opens an appointment.
2. Clicks “Create Draft Estimate”.
3. UI shows loading state; calls create-from-appointment service.
4. UI receives `estimateId` and routes to estimate detail.

**Alternate paths**
- Estimate already exists for appointment: UI routes to that existing estimate and shows “Estimate already created” (non-error).
- Validation error (missing appointment fields): UI shows actionable error and stays on appointment.
- Backend unavailable/timeouts: UI shows retry affordance; request is safe to retry.

---

## 6. Functional Behavior

### Triggers
- User clicks “Create Draft Estimate” on appointment detail.

### UI actions
- Disable the button while request is in-flight.
- Show spinner/progress indicator.
- On success:
  - show a non-blocking success message including `estimateId`
  - navigate to estimate detail
- On idempotent duplicate success:
  - show “Existing estimate found” message
  - navigate to estimate detail

### State changes (frontend)
- Maintain a local UI state: `createEstimateRequestStatus = idle|pending|success|error`.
- If appointment data model includes `linkedEstimateId`, update it from response (or refetch appointment).

### Service interactions
- Frontend calls a Moqui service (or REST call) that invokes workexec create-from-appointment.
- Frontend calls a view service to load the created estimate header for display after navigation.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Client-side pre-check before enabling click (only if appointment payload is present in UI model):
  - Must have `appointmentId`
  - Must have `customerId`
  - Must have `vehicleId`
  - Must have `locationId` (or shop/site id)
- If any missing: show inline error “Appointment is missing required fields: …” and do not call backend.

### Enable/disable rules
- “Create Draft Estimate” button is:
  - disabled while request is pending
  - disabled if appointment is cancelled (if appointment has a `status` field available to UI)
  - optionally hidden if user lacks permission (permission contract is unclear → see Open Questions)

### Visibility rules
- If an estimate is already linked to appointment (known `estimateId`):
  - show “Estimate: <estimateId>” with a “View Estimate” link
  - show “Create Draft Estimate” either hidden or replaced with “Open Estimate” (behavior depends on whether duplicates are permitted; business rule says exactly one estimate per appointment → so hide/replace)

### Error messaging expectations
- Map backend errors to user-actionable messages:
  - 400: “Cannot create estimate: appointment data is incomplete.”
  - 404: “Appointment not found or no longer available.”
  - 409: “Estimate creation conflict. Refresh and try again.” (for concurrency)
  - 5xx/network: “Service unavailable. You can retry safely.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Appointment** (shopmgr-owned; fields consumed by UI)
- **Estimate** (workexec-owned; created and then viewed)
- **WorkexecLink** (appointmentId → estimateId) (workexec-owned; UI may display result)

### Fields
**Appointment (read-only in this story)**
- `appointmentId` (string/uuid, required)
- `customerId` (string/uuid, required)
- `vehicleId` (string/uuid, required)
- `locationId` (string/uuid, required)
- `requestedServices[]` (optional)
  - `code` (string, required if item exists)
  - `description` (string, required if item exists)
- `status` (string, optional but needed to enforce “non-cancelled” rule)

**Estimate (view after creation)**
- `estimateId` (uuid, required)
- `status` (enum string, required; must be `DRAFT` on create)
- `customerId`, `vehicleId`, `shopId/locationId` (for header display; exact naming TBD)

### Read-only vs editable by state/role
- In this story:
  - Appointment fields are read-only.
  - Estimate is not edited here; only displayed/navigated-to.

### Derived/calculated fields
- `hasLinkedEstimate` = boolean derived from appointment having `estimateId` link OR backend returning existing estimate on create.

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: concrete Moqui service names/paths are not provided. Below specifies required semantics; implementation may be via Moqui service facade over REST.

### Load/view calls
1. **Load appointment detail**
   - Input: `appointmentId`
   - Output: appointment fields listed above (+ any existing linked `estimateId`)
2. **Load estimate header**
   - Input: `estimateId`
   - Output: minimal estimate header fields (id, status, customer, vehicle, location)

### Create/update calls
1. **Create estimate from appointment (idempotent)**
   - Input (minimum): `appointmentId`
   - Output: `{ estimateId, status }`
   - Semantics:
     - If already exists for appointment, returns existing `estimateId` as success (200 or 201 acceptable; frontend must not rely on code alone).

### Submit/transition calls
- None in this story (estimate approval/decline handled elsewhere).

### Error handling expectations
- Must surface a stable error structure from Moqui service calls:
  - `errorCode` (string) and `errorMessage` (string) at minimum.
- Network/timeout: retryable; UI must offer retry without warning about duplicates (idempotent).

---

## 10. State Model & Transitions

### Estimate states (relevant subset)
- `DRAFT` (created state)
- Other states exist but are not transitioned here.

### Allowed transitions (in this story)
- None initiated; only ensure newly created estimate is `DRAFT`.

### Role-based transitions
- Not applicable in this story.

### UI behavior per state
- If loaded estimate status is not `DRAFT` after creation, show warning banner:
  - “Estimate is not Draft (status: X). Verify backend workflow.” (helps detect integration issues)

---

## 11. Alternate / Error Flows

1. **Idempotent duplicate**
   - User clicks create twice (double click or retry after timeout).
   - Backend returns same `estimateId`.
   - UI routes to estimate; show informational message.

2. **Missing required appointment data**
   - Backend returns 400 with missing-field details.
   - UI highlights that appointment data is incomplete and blocks creation.

3. **Appointment cancelled**
   - If UI can see appointment status = CANCELLED:
     - button disabled
     - message: “Cannot create estimate from a cancelled appointment.”

4. **Concurrency / conflict**
   - Another advisor creates estimate simultaneously.
   - Backend returns either existing estimate (preferred) or 409.
   - If 409: UI prompts user to refresh appointment; after refresh show linked estimate if present.

5. **Unauthorized**
   - Backend returns 403.
   - UI shows “You don’t have permission to create estimates from appointments.”

6. **Empty state**
   - Appointment has no requested services: still allowed (per backend: requestedServices optional). UI should not block.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create draft estimate from appointment (happy path)
**Given** I am a Service Advisor viewing an appointment with `customerId`, `vehicleId`, and `locationId`  
**When** I click “Create Draft Estimate”  
**Then** the system creates or retrieves an estimate in workexec in `DRAFT` status  
**And** I am navigated to the Estimate detail screen for the returned `estimateId`  
**And** the appointment displays a link to the created estimate.

### Scenario 2: Idempotent retry returns existing estimate
**Given** an estimate already exists in workexec linked to appointment `A1`  
**When** I click “Create Draft Estimate” on appointment `A1` again (or retry after a timeout)  
**Then** no second estimate is created  
**And** the same existing `estimateId` is returned  
**And** I am navigated to that estimate.

### Scenario 3: Appointment missing required fields blocks creation
**Given** I am viewing an appointment missing `vehicleId`  
**When** I attempt to create a draft estimate  
**Then** the UI prevents submission and displays an error indicating `vehicleId` is required  
**And** no create call is made.

### Scenario 4: Backend validation failure is shown to user
**Given** I am viewing an appointment  
**And** the create-from-appointment request is sent  
**When** the backend responds with `400 Bad Request` indicating a missing required field  
**Then** the UI shows an actionable error message  
**And** the user remains on the appointment screen  
**And** the “Create Draft Estimate” action becomes available again.

### Scenario 5: Unauthorized user
**Given** I am logged in without permission to create estimates  
**When** I click “Create Draft Estimate”  
**Then** the backend responds with 403  
**And** the UI shows a permission error  
**And** no navigation occurs.

---

## 13. Audit & Observability

### User-visible audit data
- On the estimate detail screen, display:
  - `createdAt` and `createdBy` (if available from backend; otherwise omit without inventing)
- On appointment screen, display:
  - linked `estimateId` and (if available) “Created at …”

### Status history
- Not required to render full history here, but ensure navigation to estimate allows later inspection.

### Traceability expectations
- Frontend must include a correlation ID header if the Moqui frontend conventions support it (otherwise rely on backend-generated request IDs).
- Log client-side action: `create_estimate_from_appointment` with appointmentId and returned estimateId (avoid customer PII).

---

## 14. Non-Functional UI Requirements

- **Performance:** create call should show feedback within 200ms of click (spinner) and complete under normal conditions without blocking UI thread.
- **Accessibility:** button and error messaging must be keyboard accessible; aria-live region for async error/success messages.
- **Responsiveness:** works on tablet viewport used at service desk.
- **i18n:** all user-facing strings via localization keys (project convention).
- **Timezone/currency:** not applicable in this story.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Allow appointments with no requested services; show “No requested services” text because requestedServices is optional and this is a UI-only ergonomics choice. (Impacted: UX Summary, Error Flows)
- SD-UX-LOADING-DISABLE: Disable primary action during in-flight request to prevent double submits; safe because backend is idempotent and this reduces accidental retries. (Impacted: Functional Behavior)
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/403/404/409/5xx to user messages; safe because it does not change domain policy, only presentation. (Impacted: Business Rules, Error Flows)

---

## 16. Open Questions

1. **Backend callable contract for Moqui UI:** What is the exact endpoint/service the frontend should call to “Create Estimate From Appointment”?  
   - REST path? (Backend reference discusses `CreateEstimateFromAppointment` command but not a concrete HTTP path for Moqui.)  
   - Request body: is it only `appointmentId` or does frontend pass full appointment payload?

2. **Appointment source in this frontend:** Does the Moqui frontend load appointments from `shopmgr` via an existing Moqui service/screen, or is appointment data already present in this repository? Provide the existing screen path/entity/service used for appointment detail.

3. **Routing conventions:** What are the canonical Moqui screen paths for:
   - Appointment detail
   - Estimate detail (workexec)
   so navigation can be specified precisely?

4. **Link visibility & storage:** Should the appointment detail UI rely on reading a link from shopmgr (appointment has estimateId), or from workexec (lookup WorkexecLink by appointmentId), or both?

5. **Permissions:** What permission/role gates should control showing/enabling “Create Draft Estimate”? (Do not want to assume advisor-only without the project’s security model.)

6. **Resource specificity:** The original notes say “location/resource”. Do we need to pass and store a `resourceId` (bay/technician) in the draft estimate, and if so, is it available on appointment payload?

7. **Asynchronous event requirement:** Is the `Workexec→Shopmgr EstimateCreated` event required for frontend correctness (e.g., appointment UI won’t show link until event processed), or can the frontend proceed purely from synchronous response?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Create Draft Estimate from Appointment — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/129

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Workexec: Create Draft Estimate from Appointment
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/129
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Create Draft Estimate from Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create a draft estimate in workexec from an appointment so that quote-to-cash starts with operational context.

## Details
- Appointment provides customerId, vehicleId, location/resource, requested services.
- Workexec returns estimateId and shopmgr stores linkage.

## Acceptance Criteria
- Estimate created and linked.
- Idempotent retry safe.

## Integrations
- Shopmgr→Workexec CreateEstimateFromAppointment; Workexec→Shopmgr EstimateCreated.

## Data / Entities
- WorkexecLink(appointmentId→estimateId), CommandEnvelope

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