## 🏷️ Labels (Proposed)

STOP: Clarification required before finalization

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

# 1. Story Header

## Title
[FRONTEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle (Moqui Screens)

## Primary Persona
Service Advisor

## Business Value
Enable service advisors to schedule work with verified customer/vehicle context from CRM and persist an immutable snapshot for accurate downstream execution, billing context, and auditability.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor  
- **I want** to create a new appointment by selecting an existing CRM customer and one of their vehicles, then specifying requested services and a scheduled time window  
- **So that** the shop has reliable, validated context for future work and communications, even if CRM data changes later.

## In-Scope
- Frontend flow to **create** an Appointment in `SCHEDULED` status (draft creation is out of scope per backend reference).
- CRM-backed lookup/selection of:
  - `crmCustomerId`
  - `crmVehicleId` (validated association)
- Capture and submit appointment details:
  - time window (`startAt`, `endAt`)
  - requested service(s)
  - notes (optional)
  - contact hints (captured if supported by API; otherwise excluded and flagged)
- Display creation success and navigate to the created appointment detail view (or confirmation state).
- Moqui screen/form implementation consistent with project conventions.

## Out-of-Scope
- Appointment rescheduling/cancellation flows.
- Editing an appointment after creation.
- Draft appointment creation (explicitly out of scope in backend story).
- Notification delivery mechanics for `AppointmentCreated` (backend responsibility).
- Defining permissions/roles beyond enforcing “must be authorized” error handling.

---

# 3. Actors & Stakeholders
- **Primary Actor:** Service Advisor (frontend user)
- **Systems:**
  - **Workexec backend**: System of Record for Appointment lifecycle and creation.
  - **CRM system**: System of Record for customer/vehicle identity and association; used for validation/selection.
- **Stakeholders/Consumers:** Work order execution, billing, analytics/reporting (consume AppointmentCreated event indirectly).

---

# 4. Preconditions & Dependencies
## Preconditions
- User is authenticated in the POS frontend and has access to Scheduling features.
- Backend endpoints for appointment creation and CRM lookup/validation are reachable.

## Dependencies
- Backend story reference indicates required behavior (validate CRM IDs; create `SCHEDULED`; snapshot immutable; emit event).
- **Blocking dependency (clarification):** exact frontend-to-backend API routes for CRM lookup and appointment create are not provided in the frontend issue body.

---

# 5. UX Summary (Moqui-Oriented)

## Entry Points
- Global navigation: `Scheduling → Appointments → Create`
- Optional contextual entry: `Customer → Appointments → Create` (only if existing navigation exists; otherwise not implemented)

## Screens to Create/Modify
### New Screens
1. `apps/pos/scheduling/AppointmentCreate.xml` (or project-standard location)
   - Single page wizard-like form (can be one screen with sections)
2. `apps/pos/scheduling/AppointmentDetail.xml`
   - Minimal detail display for post-create confirmation (if an existing detail screen doesn’t already exist)

### Modified Screens (if already present)
- `AppointmentsList` screen: add “Create Appointment” action linking to create screen.

## Navigation Context
- Breadcrumb: `Scheduling / Appointments / Create`
- After successful creation: redirect to `AppointmentDetail?appointmentId=<id>` (or list with toast + highlight)

## User Workflows
### Happy Path
1. Service Advisor opens **Create Appointment**.
2. Searches and selects **CRM Customer**.
3. Selects **CRM Vehicle** (filtered by selected customer).
4. Adds **one or more requested services**.
5. Sets **startAt** and **endAt**.
6. Optionally adds notes.
7. Submits; sees success; navigates to appointment detail.

### Alternate Paths
- User changes customer after selecting a vehicle → vehicle selection is cleared and must be reselected.
- CRM returns no results → show empty state and guidance.
- Submit blocked due to validation errors (missing fields, invalid time range).

---

# 6. Functional Behavior

## Triggers
- User clicks “Create Appointment” entry point.
- User types in customer search input.
- User selects customer → triggers vehicle list load.
- User submits create form.

## UI Actions
- Customer lookup component:
  - search text input
  - results list
  - select action sets `crmCustomerId` + displays snapshot preview (name/phone/email if returned)
- Vehicle selection component:
  - disabled until customer selected
  - list filtered by `crmCustomerId`
  - select sets `crmVehicleId` + displays vehicle preview (year/make/model/vin/licensePlate if returned)
- Service request entry:
  - Add/remove “requested service” rows
  - Each row has `description` (free text) unless catalog-backed service selection is supported (not specified)
- Date/time inputs:
  - `startAt` and `endAt` required
- Notes:
  - optional free text

## State Changes (Frontend)
- Local form state transitions:
  - `idle → editing → submitting → success|error`
- No local “draft appointment” persistence (out of scope)

## Service Interactions
- CRM search/list calls for customers and vehicles (contract unknown; see Open Questions).
- Create appointment call (must return `201` and `appointmentId` on success per backend story reference).
- Handle specific error codes: `CUSTOMER_NOT_FOUND`, `VEHICLE_NOT_FOUND`, `VEHICLE_CUSTOMER_MISMATCH`.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation (client-side, before submit)
- `crmCustomerId` is required.
- `crmVehicleId` is required.
- At least **1** requested service is required.
- `startAt` and `endAt` required.
- `endAt` must be after `startAt`.
- Notes optional.
- Do **not** attempt to compute or display backend snapshots; backend owns immutable snapshot creation.

## Enable/Disable Rules
- Vehicle selector disabled until customer selected.
- Submit button disabled while:
  - submitting, or
  - required fields missing, or
  - invalid time range.

## Visibility Rules
- Show selected customer and vehicle preview panels after selection.
- Show inline validation errors per field after blur or on submit attempt.

## Error Messaging Expectations
Map backend outcomes to user-readable messages:
- `404 CUSTOMER_NOT_FOUND`: “Selected customer no longer exists in CRM. Please re-search and select again.”
- `404 VEHICLE_NOT_FOUND`: “Selected vehicle no longer exists in CRM. Please re-select a vehicle.”
- `409 VEHICLE_CUSTOMER_MISMATCH`: “That vehicle is not associated with the selected customer. Please choose a different vehicle.”
- `503`: “CRM is temporarily unavailable. Try again later.”
- Generic 400 validation: show field-level messages when possible; otherwise show summary banner.

---

# 8. Data Requirements

## Entities Involved (Frontend View Models)
- **Appointment (create request)**
- **AppointmentServiceRequest (create request line items)**
- **CRM Customer (read-only lookup)**
- **CRM Vehicle (read-only lookup)**

## Fields
### Appointment (Create)
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| crmCustomerId | string/uuid | yes | none | Selected from CRM |
| crmVehicleId | string/uuid | yes | none | Selected from CRM (must belong to customer) |
| startAt | datetime (ISO-8601) | yes | none | Send in UTC or with offset (clarify) |
| endAt | datetime (ISO-8601) | yes | none | Must be after startAt |
| notes | string | no | empty | Free text |
| contactHints | object/string | unclear | none | Mentioned in original story; backend contract not specified |

### AppointmentServiceRequest (Create)
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| description | string | yes | none | At least 1 row required |

## Read-only vs Editable
- CRM customer/vehicle fields are read-only previews.
- All create fields are editable until submit.

## Derived/Calculated Fields
- `durationMinutes` can be computed client-side for display only (do not send unless API expects it).

---

# 9. Service Contracts (Frontend Perspective)

> Backend API shapes are partially known from the backend reference, but exact URLs for CRM lookup are not provided in the frontend inputs. Contracts below define the minimum needed; unknowns are flagged.

## Load/View Calls
1. **Search CRM Customers**
   - **Purpose:** find customers to select
   - **Endpoint:** **TBD** (Open Question)
   - **Request:** query string `q` (search term), pagination optional
   - **Response:** list of customers with at least `crmCustomerId` + display fields

2. **List Vehicles for CRM Customer**
   - **Purpose:** select a vehicle associated with customer
   - **Endpoint:** **TBD** (Open Question)
   - **Request:** `crmCustomerId`
   - **Response:** list of vehicles with at least `crmVehicleId` + display fields

## Create/Update Calls
1. **Create Appointment**
   - **Endpoint:** **TBD** (backend reference does not provide exact path)
   - **Method:** `POST`
   - **Request body (minimum):**
     ```json
     {
       "crmCustomerId": "string",
       "crmVehicleId": "string",
       "startAt": "2026-01-18T15:00:00Z",
       "endAt": "2026-01-18T16:00:00Z",
       "notes": "string (optional)",
       "serviceRequests": [
         { "description": "Oil change" }
       ]
     }
     ```
   - **Success:** `201 Created` with:
     ```json
     { "appointmentId": "uuid-or-string" }
     ```

## Submit/Transition Calls
- None (creation results in `SCHEDULED` status directly per backend reference)

## Error Handling Expectations
- `400` validation errors: show field errors when returned as structured list; else show generic banner.
- `401/403`: show “You are not authorized” and route user back to safe screen.
- `404` with codes above: handle as described in Business Rules.
- `409 VEHICLE_CUSTOMER_MISMATCH`: show specific message; keep user on form.
- `503`: show retry guidance; keep entered data intact.

---

# 10. State Model & Transitions

## Allowed States (Appointment)
- `SCHEDULED` is the initial and only state relevant for this story (created appointments must be scheduled).
- `DRAFT` creation is out of scope (even though original prompt mentioned Draft/Scheduled).

## Role-based Transitions
- Not defined here; creation requires authorization. Any further transitions not part of this story.

## UI Behavior per State
- N/A for create flow; after creation, detail screen should display status `SCHEDULED` prominently as read-only field.

---

# 11. Alternate / Error Flows

## Validation Failures (client-side)
- Missing required fields → inline errors; prevent submit.
- endAt <= startAt → inline error on endAt; prevent submit.

## Backend Validation Failures (400)
- Backend rejects payload → show banner “Unable to create appointment” + show any returned field errors; keep form data.

## Concurrency Conflicts
- Not expected for create; if backend returns `409` for scheduling conflict (not specified), show generic conflict banner and keep data (Open Question: do we need conflict handling?).

## Unauthorized Access
- `401`: redirect to login.
- `403`: show access denied and hide submit.

## Empty States
- Customer search returns none → “No customers found. Refine your search.”
- Vehicle list empty for selected customer → “No vehicles found for this customer.”

---

# 12. Acceptance Criteria

## Scenario 1: Create Scheduled Appointment (Happy Path)
**Given** CRM is available  
**And** the Service Advisor is authenticated and authorized  
**And** a CRM customer exists  
**And** a CRM vehicle exists and is associated with that customer  
**When** the user selects the customer and vehicle  
**And** enters at least one requested service description  
**And** enters a valid start and end time  
**And** submits the form  
**Then** the system sends a create request with `crmCustomerId`, `crmVehicleId`, `startAt`, `endAt`, and service requests  
**And** receives `201 Created` with an `appointmentId`  
**And** navigates to Appointment Detail (or shows confirmation) for that `appointmentId`  
**And** the status displayed is `SCHEDULED`.

## Scenario 2: Vehicle Not Associated with Customer
**Given** CRM is available  
**And** the user selected a customer  
**When** the user attempts to submit with a vehicle that is not associated to that customer  
**And** the backend returns `409 Conflict` with error code `VEHICLE_CUSTOMER_MISMATCH`  
**Then** the UI shows a blocking error message explaining the mismatch  
**And** the appointment is not created  
**And** the user remains on the create form with previously entered fields intact.

## Scenario 3: CRM Unavailable During Create
**Given** CRM is unavailable  
**When** the user submits the create form  
**And** the backend returns `503 Service Unavailable`  
**Then** the UI shows “CRM is temporarily unavailable. Try again later.”  
**And** the appointment is not created  
**And** the form data remains intact for retry.

## Scenario 4: Customer Not Found
**Given** CRM is available  
**When** the user submits with a `crmCustomerId` that no longer exists  
**And** the backend returns `404` with error `CUSTOMER_NOT_FOUND`  
**Then** the UI shows an error instructing the user to re-search/select a customer  
**And** clears the selected customer and vehicle fields (to avoid invalid references).

## Scenario 5: Client-side Validation Prevents Submit
**Given** the Create Appointment screen is open  
**When** the user has not selected a customer, vehicle, or has not added any requested services  
**Or** the end time is not after start time  
**Then** the Submit button is disabled (or submit shows inline errors)  
**And** no API create call is made.

---

# 13. Audit & Observability

## User-visible audit data
- On Appointment Detail, show:
  - created timestamp (from response if provided; otherwise loaded via detail endpoint—TBD)
  - created by (if available)
- If these fields are not available via API, omit and track as Open Question (do not invent).

## Status history
- Not required for creation-only story; no UI for state transitions.

## Traceability expectations
- Frontend must include correlation/request ID headers if project standard supports it (Moqui often supports request tracking). If not defined, rely on backend logging.
- Log frontend errors in console (dev) and use standard error banner for user.

---

# 14. Non-Functional UI Requirements

## Performance
- Customer search should debounce input (e.g., 250–400ms) to avoid excessive calls.
- Vehicle list should load once per customer selection and cache in-memory for the current form session.

## Accessibility
- All form controls labeled.
- Error messages associated to inputs.
- Keyboard navigable selection lists.

## Responsiveness
- Works on tablet-width layouts (common in service desk).

## i18n/timezone/currency
- Date/time entry and display must respect store/user timezone (clarify how timezone is determined) and send ISO timestamps per API expectation.

---

# 15. Applied Safe Defaults
- **SD-UX-SEARCH-DEBOUNCE**: Debounce CRM search input by ~300ms to reduce calls; safe UI ergonomics choice. Impacted sections: UX Summary, Functional Behavior, Non-Functional.
- **SD-UX-EMPTY-STATES**: Provide explicit empty-state messages for no results; safe UI ergonomics choice. Impacted sections: UX Summary, Alternate / Error Flows.
- **SD-ERR-STANDARD-BANNER**: Use standard error banner + inline field errors when available; safe mapping of backend errors to UX without changing business rules. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **What are the exact API endpoints for CRM customer search and customer vehicle listing** used by the Moqui frontend (paths, query params, response shapes)?
2. **What is the exact appointment creation endpoint/path** in the Moqui backend layer (or gateway) for creating an appointment (method, path, request/response schema)?
3. **Timezone contract:** should `startAt`/`endAt` be sent in UTC (`Z`) always, or with local offset? How is the store/user timezone determined in the frontend?
4. **Requested services structure:** are service requests free-text `description` only, or should the UI select from a catalog (service IDs/codes)? If IDs are required, provide endpoint/lookup.
5. **“Contact hints” field:** is there a supported field in the create API for contact hints (preferred contact method, best phone, etc.)? If yes, specify shape and validation.
6. **Post-create navigation:** is there an existing Appointment Detail screen/route naming convention we must use, or should this story add a new detail screen?
7. **Do we need to handle schedule conflicts** (e.g., overlapping appointments) with a defined backend error code? If yes, what is the error code and expected UX?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/139  
Labels: frontend, story-implementation, payment  

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle

**Domain**: payment

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create an appointment selecting customer and vehicle from durion-crm so that the shop has accurate context for service and billing.

## Details
- Capture: crmCustomerId, crmVehicleId, requested services, notes, preferred time window, contact hints.

## Acceptance Criteria
- Appointment created with status Draft/Scheduled.
- Customer/vehicle references validated.
- Audited.

## Integrations
- CRM lookup/snapshot.
- Optional AppointmentCreated event.

## Data / Entities
- Appointment, AppointmentServiceRequest, CRM references

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