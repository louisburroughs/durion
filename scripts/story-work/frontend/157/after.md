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
[FRONTEND] [STORY] Workexec: Capture & Persist CRM Reference IDs on Estimate/Work Order Artifacts

### Primary Persona
System User (Service Advisor / POS User) operating the POS frontend

### Business Value
Ensures every Estimate and Work Order created/edited in the POS has durable CRM reference IDs (party, vehicle, contacts) for traceability and reporting, and preserves the original IDs even if CRM later merges entities.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor (POS user)  
- **I want** the Estimate and Work Order creation/edit flows to capture and send CRM reference IDs (`crmPartyId`, `crmVehicleId`, `crmContactIds`) to the backend and show them read-only on artifacts  
- **So that** work artifacts can always be correlated back to CRM records for reporting and audit, even if CRM later merges/aliases records.

### In-scope
- Frontend data-model + UI changes to:
  - include `crmPartyId`, `crmVehicleId`, `crmContactIds` in relevant create/update API requests for **Estimates** and **Work Orders**
  - display these fields on Estimate/Work Order detail views as read-only “integration references”
- Frontend validation and error handling for missing required CRM IDs where backend requires them.
- Optional frontend support to display “resolved canonical ID” if a CRM alias-resolution endpoint exists **and is provided** (see Open Questions).

### Out-of-scope
- Implementing CRM merge/alias resolution logic in the workexec backend (backend rules say workexec stores IDs as provided).
- Defining or changing CRM system-of-record policies, identifier formats, or merge rules.
- Creating/maintaining location/customer approval configuration (not related).
- Adding new workflow states to work orders/estimates.

---

## 3. Actors & Stakeholders
- **Actors**
  - Service Advisor / POS User: creates/edits estimates and work orders via Moqui screens.
  - POS Frontend (Moqui): collects CRM references from existing customer/vehicle/contact context and sends to backend.
- **Stakeholders**
  - Reporting/Analytics: relies on stable stored IDs.
  - Audit/Compliance: needs traceability between work artifact and CRM entities.
  - CRM System: authoritative for party/vehicle/contact entities and merge/alias resolution.

---

## 4. Preconditions & Dependencies
- A customer and vehicle are selected in the POS flow prior to creating an Estimate and/or Work Order (source of `crmPartyId` and `crmVehicleId`).
- The frontend has access to:
  - current selected customer’s CRM party ID
  - current selected vehicle’s CRM vehicle ID
  - associated contact IDs (0..n)
- Backend endpoints exist (or will exist) to accept these fields on create/update:
  - Estimate create/update
  - Work Order create/update (including conversion from Estimate → Work Order)
- If “alias resolution display” is desired, a CRM resolution endpoint must be defined and reachable from the frontend (currently unspecified).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Estimate creation flow (from customer/vehicle context)
- Estimate detail screen (view/edit while Draft)
- Work Order creation flow (direct or via conversion from approved Estimate)
- Work Order detail screen

### Screens to create/modify
- **Modify** Estimate create/edit screen(s):
  - Ensure CRM reference IDs are present in request payloads.
  - Add a read-only “CRM References” section on detail view.
- **Modify** Work Order create/edit screen(s):
  - Ensure CRM reference IDs are present in request payloads.
  - Add read-only “CRM References” section on detail view.
- **Optional**: a small read-only “Resolved CRM IDs” subsection if alias resolution is implemented (blocked pending contract).

*(Exact Moqui screen names/routes depend on repo conventions; implementer should wire into existing estimate/workorder screens rather than creating new navigation.)*

### Navigation context
- These fields should be visible on the artifact “Summary/Details” area and not interrupt primary workflows.
- No new top-level menu item.

### User workflows
- **Happy path (Estimate create)**:
  1. User selects customer + vehicle (and optionally contacts).
  2. User creates an Estimate.
  3. Frontend submits estimate with `crmPartyId`, `crmVehicleId`, `crmContactIds`.
  4. User can view the stored CRM references on the Estimate detail screen.
- **Happy path (Work Order create/convert)**:
  1. User creates Work Order directly *or* converts from an Estimate.
  2. Frontend ensures the Work Order payload includes the CRM references (either inherited from estimate or supplied from context).
  3. Work Order detail shows CRM references read-only.
- **Alternate path (contacts empty)**:
  - User has no contacts selected; frontend sends `crmContactIds: []` (not null) if supported by API contract.
- **Error path (missing party/vehicle)**:
  - User attempts create without required CRM IDs available; frontend blocks submission and shows actionable error.

---

## 6. Functional Behavior

### Triggers
- Submitting Estimate create
- Submitting Estimate update (where allowed)
- Submitting Work Order create
- Submitting Work Order update (where allowed)
- Converting Estimate → Work Order (if that is a frontend action)

### UI actions
- On load of Estimate/WO detail: display stored CRM reference fields read-only.
- On submit:
  - include CRM IDs in request payload consistently.
  - if missing required IDs, prevent submission (client-side) and show validation.

### State changes
- No new workexec workflow states introduced.
- These fields are treated as “artifact metadata”; once persisted, they should be treated as immutable in UI unless backend explicitly allows update (see Open Questions).

### Service interactions
- Calls to backend to create/update Estimate/Work Order must include these fields.
- Optional call to CRM alias resolution endpoint to display canonical IDs (blocked).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `crmPartyId` **required** for create (per backend rule).
- `crmVehicleId` **required** for create (per backend rule).
- `crmContactIds`:
  - must be present and **non-null**
  - may be empty array `[]` (backend assumption, pending confirmation)

### Enable/disable rules
- Submit buttons disabled while required CRM IDs are absent from current context OR while a submission is in-flight.
- CRM reference fields on detail screens are read-only (not directly editable by user) unless backend supports editing.

### Visibility rules
- Always show “CRM References” section on Estimate/Work Order detail.
- If any field is absent in the loaded record (older records), show as “Not set” with a non-blocking warning style.

### Error messaging expectations
- If backend returns 400 with missing/invalid CRM fields, surface message:  
  “Customer/Vehicle reference is missing. Re-select customer and vehicle and try again.”
- If backend returns 409 (conflict) on update, show:  
  “This record was updated elsewhere. Refresh to continue.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
- Estimate (workexec)
- Work Order (workexec)

### Fields
For both Estimate and Work Order artifacts:

| Field | Type | Required | Default | Editable |
|------|------|----------|---------|----------|
| crmPartyId | string (format TBD) | Yes (on create) | none | Read-only in UI by default |
| crmVehicleId | string (format TBD) | Yes (on create) | none | Read-only in UI by default |
| crmContactIds | string[] | Yes (must be non-null) | `[]` | Read-only in UI by default |

### Read-only vs editable by state/role
- **Read-only** across all states in the frontend unless backend explicitly supports updating these values.
- If an Estimate is `DRAFT` and backend permits update, allow update only indirectly by changing selected customer/vehicle context and resubmitting (but this is **blocked** until backend contract is explicit).

### Derived/calculated fields
- Optional derived display:
  - “Resolved Party ID” / “Resolved Vehicle ID” from CRM alias resolution endpoint (blocked).

---

## 9. Service Contracts (Frontend Perspective)

> Exact Moqui service names are unknown from provided inputs; frontend must integrate with the backend REST endpoints listed in the domain docs.

### Load/view calls
- `GET /api/estimates/{id}` returns estimate including CRM reference fields.
- `GET /api/workorders/{id}` (or `/api/work-orders/{id}` depending on backend routing) returns work order including CRM reference fields.
  - **Open Question:** actual path naming consistency (workorders vs work-orders).

### Create/update calls
- Estimate create:
  - `POST /api/estimates` with body including `crmPartyId`, `crmVehicleId`, `crmContactIds`.
- Estimate update (if exists):
  - `PUT/PATCH /api/estimates/{id}` (contract TBD) must preserve fields; frontend must not drop them on update.
- Work order create:
  - `POST /api/workorders` (or similar) includes CRM fields.
- Work order update:
  - `PUT/PATCH /api/workorders/{id}` must preserve fields; frontend must not drop them.

### Submit/transition calls
- If work order is created by converting an estimate:
  - Whatever endpoint/flow performs conversion must ensure CRM fields propagate (either backend does it, or frontend supplies them).
  - **Do not assume**; see Open Questions.

### Error handling expectations
- 400: show field-level validation summary (missing party/vehicle, contacts null).
- 401/403: show “Not authorized” and navigate to login/stop action.
- 404: show “Record not found” and provide back navigation.
- 409: prompt refresh/retry.
- 500: show generic error with correlation/trace id if provided by backend.

---

## 10. State Model & Transitions

### Allowed states
- No changes to existing Estimate state machine (`DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`) or Work Order state machine (per `WORKORDER_STATE_MACHINE.md`).

### Role-based transitions
- Not modified by this story.

### UI behavior per state
- Estimate:
  - In all states: CRM references visible read-only.
  - In `DRAFT`: if estimate editing is allowed, ensure CRM fields are preserved during saves.
- Work Order:
  - In all states: CRM references visible read-only.
  - During execution states: no special behavior besides display.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing selected customer → cannot compute `crmPartyId`:
  - Block submit; show “Select a customer to continue.”
- Missing selected vehicle → cannot compute `crmVehicleId`:
  - Block submit; show “Select a vehicle to continue.”

### Backend validation failures
- Backend 400 indicates missing `crmVehicleId`:
  - Show error and keep user on form with fields intact.

### Concurrency conflicts
- If update returns 409:
  - Prompt user to refresh; do not auto-resubmit.

### Unauthorized access
- 403 on load or submit:
  - Show authorization error and hide CRM references if record cannot load.

### Empty states / legacy records
- Loading an older Estimate/WO without CRM refs:
  - Display “Not set” values and a note: “This record was created before CRM references were required.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create Estimate includes CRM references
**Given** a POS user has selected a customer with `crmPartyId` and a vehicle with `crmVehicleId`  
**And** the user has selected zero or more contacts (yielding `crmContactIds`)  
**When** the user submits “Create Estimate”  
**Then** the frontend sends a create request containing `crmPartyId`, `crmVehicleId`, and `crmContactIds` (non-null; empty array allowed)  
**And** on successful creation, the Estimate detail screen displays the stored CRM reference fields read-only.

### Scenario 2: Prevent Estimate create when required CRM IDs are missing
**Given** a POS user has not selected a vehicle (no `crmVehicleId` available)  
**When** the user attempts to submit “Create Estimate”  
**Then** the frontend blocks submission  
**And** shows an error indicating vehicle selection is required.

### Scenario 3: Work Order detail displays CRM references
**Given** a work order exists with stored `crmPartyId`, `crmVehicleId`, and `crmContactIds`  
**When** the user opens the Work Order detail screen  
**Then** the CRM reference fields are displayed read-only  
**And** the UI does not allow direct editing of these fields.

### Scenario 4: Updates do not drop CRM references
**Given** an estimate exists with CRM references set  
**And** the estimate is edited in a manner that triggers an update call (e.g., adding a line item)  
**When** the frontend submits the update request  
**Then** the request payload preserves the existing CRM reference values (does not omit or null them)  
**Or** the frontend uses a backend endpoint that guarantees preservation (explicitly documented in implementation).

### Scenario 5: Backend 400 is shown as actionable error
**Given** the backend responds `400 Bad Request` indicating `crmPartyId` is missing  
**When** the frontend receives the response  
**Then** the UI displays an actionable error message instructing the user to re-select customer/vehicle and retry  
**And** the user’s in-progress form data is preserved.

---

## 13. Audit & Observability

### User-visible audit data
- On Estimate/WO detail, show:
  - CRM Party ID
  - CRM Vehicle ID
  - CRM Contact IDs count (and list if space allows)
- Do not expose any internal-only CRM fields beyond IDs.

### Status history
- No new status history requirements.

### Traceability expectations
- Frontend should pass through any backend correlation ID/trace ID in error banners if provided (e.g., from response headers).

---

## 14. Non-Functional UI Requirements
- **Performance**: Displaying CRM reference fields must not add additional blocking network calls; alias-resolution (if implemented) must be non-blocking and tolerant of failure.
- **Accessibility**: CRM reference section must be keyboard navigable and screen-reader labeled (IDs announced with clear field names).
- **Responsiveness**: CRM references render well on tablet layouts (wrap long IDs).
- **i18n/timezone/currency**: Not applicable (IDs only).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE-01: Show “Not set” for missing legacy CRM reference fields; safe because it’s a non-decision UX fallback that doesn’t alter domain policy. (Impacted: UX Summary, Error Flows)
- SD-ERR-MAP-01: Standard HTTP error-to-banner mapping (400/401/403/404/409/500) with non-destructive retry guidance; safe because it does not invent business rules, only displays backend outcomes. (Impacted: Service Contracts, Alternate/Error Flows)

---

## 16. Open Questions

1. **Backend contract for identifier types/formats**: What are the exact data types and validation formats for `crmPartyId`, `crmVehicleId`, and each element of `crmContactIds` (UUID, numeric, prefixed string)?  
2. **Exact API routes for Work Orders**: Are endpoints `/api/workorders/...` or `/api/work-orders/...` used by the backend the frontend will call? Provide canonical paths for load/create/update.  
3. **Immutability vs editability**: After creation, are CRM reference fields immutable on Estimate/Work Order? If customer/vehicle selection changes during Draft, should the frontend be allowed to update these IDs, or must a new Estimate be created?  
4. **Conversion behavior**: When converting an Estimate → Work Order, does the backend automatically copy CRM references from the Estimate, or must the frontend supply them in the conversion/create request?  
5. **Alias resolution endpoint**: Does a CRM alias/redirect resolution API exist for party/vehicle/contact IDs? If yes, provide URL + request/response schema and whether frontend is expected to call it (read-only display) or leave it to downstream systems only.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/157  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **System**, I want **workorders/estimates to store CRM partyId, vehicleId, and contactIds** so that **traceability and reporting are possible**.

## Details  
- Workorder domain stores foreign references to CRM.  
- CRM merges must not break references (aliases/redirects).

## Acceptance Criteria  
- Estimate/WO persist CRM references.  
- References resolvable back to CRM after merges.

## Integration Points (Workorder Execution)  
- Workorder Execution persists CRM IDs; CRM provides alias resolution endpoint if needed.

## Data / Entities  
- WO/Estimate reference fields  
- PartyAlias (optional)

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