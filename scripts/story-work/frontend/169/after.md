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
[FRONTEND] [STORY] CRM Vehicle: Create Vehicle Record (VIN, Unit #, Description, Optional Plate)

**Primary Persona**  
Service Advisor (POS user)

**Business Value**  
Creates a stable `vehicleId` in CRM so downstream flows (estimate/workorder creation, service history, billing) can reliably attach activity to the correct vehicle.

---

## 2. Story Intent

**As a** Service Advisor  
**I want** to create a vehicle record by entering VIN, unit number, description (make/model/year free text), and optional license plate  
**So that** workorders/estimates/invoices can reference a stable `vehicleId` and future visits can find the same vehicle quickly.

### In-scope
- A Moqui screen flow to create a Vehicle in CRM
- Client-side validations (basic VIN sanity, required fields)
- Submit to Moqui service/API and handle success/error responses
- Post-create navigation to a vehicle detail view (or confirmation view) with the new `vehicleId`

### Out-of-scope
- External VIN decode (optional stub only; no real integration)
- Vehicle ownership association (linking to account/party) unless explicitly required by backend contract
- Advanced dedupe/merge logic
- Editing vehicle after creation (separate story)

---

## 3. Actors & Stakeholders
- **Service Advisor**: creates vehicle records during customer intake or workorder prep
- **CRM domain (system of record)**: persists vehicles and enforces invariants
- **Workorder Execution** (consumer): stores `vehicleId` on workorders/estimates
- **Billing** (consumer): uses `vehicleId` to link invoices/history
- **Support/Admin**: cares about auditability of created records

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend and has permission to create vehicles (exact permission/scope TBD → see Open Questions).
- Moqui has an available service/API endpoint to create a vehicle and return `vehicleId` (backend story #105 indicates this exists but the exact route/service name is not provided).
- VIN uniqueness scope decision is made (global vs per-account) and reflected in backend responses (409 vs validation error).
- If “account/customer context” is required to create a vehicle (backend reference mentions `accountId`), the frontend must know the current `accountId` context and pass it.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
At least one entry path must exist (exact navigation depends on app IA):
- From a customer/account context: “Add Vehicle”
- From a general vehicle search/list: “Create Vehicle”

### Screens to create/modify
1. **Screen:** `VehicleCreate`  
   - Purpose: data entry + submit
2. **Screen:** `VehicleView` (or reuse an existing vehicle detail screen if present)  
   - Purpose: show created vehicle summary including `vehicleId`

### Navigation context
- If launched from within an account: preserve account context parameters (e.g., `accountId`) through the create flow and on success.
- If launched globally: no account context required unless backend mandates it (OQ).

### User workflows
**Happy path**
1. Service Advisor opens Create Vehicle screen.
2. Enters required fields: VIN, Unit Number, Description.
3. Optionally enters License Plate.
4. Submits.
5. UI shows success confirmation and navigates to vehicle view screen (or stays with success banner and a “View Vehicle” action).

**Alternate paths**
- User cancels → returns to previous screen without changes.
- Backend reports duplicate VIN (per uniqueness policy) → user remains on form with field-level error and/or top banner.
- Validation failures (missing required, invalid VIN format) → block submit and show inline errors.

---

## 6. Functional Behavior

### Triggers
- User clicks “Create Vehicle” action from allowed entry points.

### UI actions
- Form input for:
  - VIN
  - Unit Number
  - Description (free text “Make/Model/Year”)
  - License Plate (optional)
- Buttons:
  - **Save/Create** (primary)
  - **Cancel** (secondary)

### State changes (frontend)
- Form state: pristine/dirty, validation errors
- Submission state: loading/disabled while request in-flight
- On success: store returned `vehicleId` in navigation parameters and/or local state for the next screen

### Service interactions
- On load: none required (unless backend requires account context lookup)
- On submit: call Moqui service/API to create the vehicle record
- On success: navigate to VehicleView with `vehicleId`
- On failure: map error to user-visible messages (details in Service Contracts + Error Flows)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Required fields** (block submit; show inline error):
  - `vin` required
  - `unitNumber` required
  - `description` required
- **VIN basic sanity** (block submit; show inline error):
  - length must be exactly 17
  - must not include letters `I`, `O`, `Q`
  - allowed characters: A–Z and 0–9 (uppercase normalization allowed in UI)
- **License plate** optional; if provided, trim whitespace.

### Enable/disable rules
- “Create” button disabled while:
  - form invalid, or
  - submission in progress

### Visibility rules
- If VIN decode stub is present (optional), it must be clearly marked as “not yet implemented” and must not affect creation.

### Error messaging expectations
- Inline field errors for client-side validation.
- For backend validation (400): show top banner “Fix validation errors” plus field mapping when possible.
- For duplicate/conflict (409): show message indicating likely duplicate vehicle; ideally highlight VIN field.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Vehicle** (CRM-owned)
- **VehicleIdentifier** (if backend models identifiers separately; frontend treats as fields unless API requires nested structure)

### Fields
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `vin` | string | Yes | none | Normalize to uppercase; trim |
| `unitNumber` | string | Yes | none | Trim |
| `description` | string | Yes | none | Free text make/model/year |
| `licensePlate` | string | No | null/empty | Trim; store null if empty |
| `accountId` | UUID/string | TBD | none | Only if backend requires vehicle creation within account context |
| `vehicleId` | UUID/string | Returned | n/a | Read-only, generated by backend |

### Read-only vs editable
- On create screen: all inputs editable.
- On post-create view: `vehicleId` read-only; display the submitted fields as persisted values.

### Derived/calculated fields
- `vinNormalized` (UI-only): uppercase/trimmed VIN used for validation and submission.

---

## 9. Service Contracts (Frontend Perspective)

> Backend story reference exists but does not specify concrete endpoint/service names for Moqui. This story defines required frontend expectations; exact wiring must align with actual Moqui services.

### Load/view calls
- **VehicleView load**
  - Input: `vehicleId`
  - Output: vehicle fields (vin, unitNumber, description, licensePlate, accountId if applicable)

### Create/update calls
- **Create Vehicle**
  - Input payload (minimum):
    - `vin`
    - `unitNumber`
    - `description`
    - `licensePlate` (optional)
    - `accountId` (if required)
  - Output:
    - `vehicleId` (required)
    - persisted vehicle summary (optional but preferred)

### Submit/transition calls
- None beyond create.

### Error handling expectations
- `400 Bad Request`: validation error(s) with field identifiers if possible.
- `401/403`: show “Not authorized” and prevent access to create screen (or show error state).
- `409 Conflict`: duplicate vehicle according to uniqueness rule; show duplicate message (VIN-focused).
- `5xx`: show generic “Something went wrong” with retry option.

---

## 10. State Model & Transitions

### Allowed states
- No explicit vehicle lifecycle states were provided; treat Vehicle as “active” upon creation with no user-managed state.

### Role-based transitions
- Service Advisor can create (permission name TBD).

### UI behavior per state
- N/A (no lifecycle state in this story).

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing VIN/unit/description → block submit; show inline error messages per field.
- Invalid VIN format → block submit; message: “VIN must be 17 characters and cannot include I, O, or Q.”

### Backend validation failures (400)
- Display returned message(s).
- Keep user-entered values intact.
- If backend returns field list, map to fields; else show banner only.

### Concurrency conflicts
- Not applicable for create; but if backend returns 409 due to uniqueness, treat as conflict and guide user.

### Unauthorized access (401/403)
- If user navigates directly to create URL: show access denied screen (or route to login if unauthenticated).
- Do not reveal existence of vehicles in conflict messaging beyond “already exists” (no sensitive data).

### Empty states
- If VehicleView load fails with 404: show “Vehicle not found” and provide navigation back.

---

## 12. Acceptance Criteria

### Scenario 1: Successful vehicle creation
**Given** the Service Advisor is authenticated and authorized to create vehicles  
**And** they are on the Vehicle Create screen  
**When** they enter a valid VIN `1HGCM82633A004352`, unit number `TRK-102`, and description `2018 Ford F-150`  
**And** they optionally enter license plate `ABC123`  
**And** they submit the form  
**Then** the frontend sends a create request with those fields (VIN normalized to uppercase)  
**And** the backend returns a new `vehicleId`  
**And** the UI shows a success confirmation  
**And** the UI navigates to Vehicle View with that `vehicleId` (or displays the created record including `vehicleId`).

### Scenario 2: Client-side validation blocks missing required fields
**Given** the Service Advisor is on the Vehicle Create screen  
**When** they leave `unitNumber` empty  
**And** they click Create  
**Then** the form must not submit  
**And** an inline error indicates `unitNumber` is required.

### Scenario 3: Client-side validation blocks invalid VIN format
**Given** the Service Advisor is on the Vehicle Create screen  
**When** they enter VIN `12345`  
**And** they click Create  
**Then** the form must not submit  
**And** an inline error indicates VIN must be 17 characters.

### Scenario 4: Backend rejects duplicate VIN (409)
**Given** VIN uniqueness scope is defined by the backend  
**And** a vehicle already exists that conflicts with VIN `1HGCM82633A004352` per that policy  
**When** the Service Advisor submits a create request with VIN `1HGCM82633A004352` and other valid fields  
**Then** the backend responds with HTTP 409  
**And** the UI remains on the create form  
**And** the UI displays an error message indicating the VIN already exists (without exposing sensitive details).

### Scenario 5: Unauthorized user cannot access create
**Given** a user is authenticated but lacks permission to create vehicles  
**When** they attempt to open the Vehicle Create screen  
**Then** the UI denies access (403 handling) and does not show the create form.

---

## 13. Audit & Observability

### User-visible audit data
- On VehicleView, display at minimum:
  - `vehicleId`
  - created timestamp and created-by (only if backend exposes; otherwise omit)

### Status history
- Not applicable (no lifecycle states defined).

### Traceability expectations
- Frontend must include correlation/request id headers if standard in the project (if Moqui config provides).
- Log (frontend console/logger) a single info-level event on successful creation including `vehicleId` (no VIN in logs unless workspace policy allows PII-like data; VIN may be considered sensitive—treat cautiously).

---

## 14. Non-Functional UI Requirements
- **Performance:** Create submit should show loading state immediately; avoid duplicate submissions.
- **Accessibility:** All inputs labeled; validation errors announced to screen readers; keyboard navigable.
- **Responsiveness:** Form usable on tablet widths typical for POS.
- **i18n/timezone/currency:** No currency; timestamps (if shown) must render in user timezone per app standard; strings should be compatible with i18n (no hardcoded concatenation).

---

## 15. Applied Safe Defaults
- **SD-UI-EMPTY-STATE**: Provide a simple “Vehicle not found” empty state on 404 in VehicleView; safe because it’s UI ergonomics and does not alter domain logic. (Sections: UX Summary, Error Flows)
- **SD-UI-SUBMIT-LOCK**: Disable the Create button and show loading during in-flight request to prevent double submits; safe UX pattern with no domain policy impact. (Sections: Functional Behavior, Non-Functional)
- **SD-ERR-GENERIC-5XX**: Map unknown/5xx errors to a generic retryable message; safe because it’s standard error handling without changing business rules. (Sections: Service Contracts, Error Flows)

---

## 16. Open Questions
1. **VIN uniqueness scope (blocking):** Is VIN uniqueness **global** across all accounts or **scoped per account**? How should the frontend message differ (if at all)?
2. **Account context requirement (blocking):** Does vehicle creation require an `accountId`/customer context? If yes, what is the source in the frontend routing (route param, session context, selected customer)?
3. **Moqui service/API contract (blocking):** What are the exact Moqui service names or REST endpoints for:
   - create vehicle
   - load vehicle by `vehicleId`
   Include request/response schema and error payload shape for field-level mapping.
4. **Authorization model (blocking):** What permission/scope/role gates vehicle creation in the frontend? (e.g., `crm.vehicle.create`)
5. **License plate structure (clarification):** Is license plate stored as a single string, or plate + state/province? (Story implies single field; confirm.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/169


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/169
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Create Vehicle Record with VIN and Description

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to create a vehicle record with VIN, unit number, and description** so that **future service and billing can be linked to the correct vehicle**.

## Details
- Capture VIN, make/model/year (free text initially), unit number, license plate (optional).
- Minimal VIN format validation; external decode optional stub.

## Acceptance Criteria
- Can create vehicle with VIN.
- Vehicle has stable Vehicle ID.
- VIN uniqueness rule defined (global or per account).

## Integration Points (Workorder Execution)
- Workorder Execution selects vehicle for estimate/workorder; Vehicle ID stored on workorder.

## Data / Entities
- Vehicle
- VehicleIdentifier (VIN, unit, plate)

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