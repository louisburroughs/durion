## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

**Title:** [FRONTEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual

**Primary Persona:** Fleet Account Manager

**Business Value:** Ensure workorders default to the correct billing entity and contacts by maintaining authoritative vehicle ownership (commercial account) and optional primary driver (individual), including reassignment history.

---

# 2. Story Intent

### As a / I want / So that
- **As a** Fleet Account Manager  
- **I want** to link a vehicle to a commercial account (owner) and optionally assign a primary driver (person)  
- **So that** downstream workflows (Workorder Execution, Billing) can reliably default to the correct billing entity and contact context.

### In-scope
- Frontend screens and flows to:
  - View current vehicle associations (active OWNER + optional active PRIMARY_DRIVER).
  - Create an initial OWNER association.
  - Reassign OWNER (implicit reassignment that end-dates prior owner, atomically).
  - Assign or change PRIMARY_DRIVER (requires active OWNER).
  - View association history (effective dating; non-destructive).
- Validation and error handling aligned to backend rules (400/404/409).
- Moqui screen/actions wiring and service calls to CRM endpoints/services.

### Out-of-scope
- Creating/editing the underlying Vehicle master record (VIN, plate, etc.) unless required for association flow navigation.
- Customer/Party creation, deduplication, merge, or identity rules.
- Authorization policy design (roles/scopes); frontend only enforces via guarded UI + handling 403 responses.
- Workorder Execution UI changes.

---

# 3. Actors & Stakeholders

- **Primary Actor:** Fleet Account Manager
- **Secondary Actors:** CSR / Dispatcher (read-only use cases may exist but not required)
- **Service Owner / System of Record:** **CRM** for vehicle↔party associations
- **Downstream Consumers (read-only):** Workorder Execution, Billing
- **Stakeholders:** Accounting/Billing operations (consume defaults), CRM administrators (data integrity/audit)

---

# 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User is authorized to manage vehicle associations for the commercial account(s) involved.
- Target **Vehicle** exists.
- Target **Commercial Account** exists (Organization party).
- Optional **Driver** exists (Person party).

### Dependencies
- CRM backend capability as defined in backend story #104:
  - Enforce “exactly one active OWNER per vehicle” and “zero or one active PRIMARY_DRIVER per vehicle”.
  - Implicit owner reassignment behavior (end-date old owner; idempotent no-op if same).
  - Driver assignment requires active owner.
  - Conflict detection for overlapping historical segments (409).
- Frontend must have access to:
  - Vehicle lookup (at least by vehicleId context).
  - Party lookup/search for selecting an owner account and driver person (exact endpoints/services TBD; see Service Contracts section).

---

# 5. UX Summary (Moqui-Oriented)

### Entry points
- From a **Vehicle detail** context screen (preferred): “Associations” or “Ownership & Driver”.
- From an **Account** context (optional): “Vehicles” list → select vehicle → manage associations.

### Screens to create/modify (Moqui)
1. **Vehicle Detail Screen** (existing, modify)
   - Add navigation to “Associations” sub-screen.
2. **Vehicle Associations Screen** (new)
   - Displays:
     - Current active OWNER association (account).
     - Current active PRIMARY_DRIVER association (person) if present.
     - Association history table (OWNER and PRIMARY_DRIVER segments with start/end).
   - Actions:
     - “Assign/Reassign Owner”
     - “Assign/Change Driver”
     - “Unassign Driver” (end-date active PRIMARY_DRIVER) **only if supported by backend contract** (see Open Questions if not available).
3. **Owner Selection Dialog/Screen** (new or embedded)
   - Search/select commercial account party.
   - Choose effective start date/time (default now).
4. **Driver Selection Dialog/Screen** (new or embedded)
   - Search/select person party.
   - Choose effective start date/time (default now).

### Navigation context
- URL should include `vehicleId` as path or parameter to maintain context.
- Breadcrumb: Vehicles → Vehicle <identifier> → Associations.

### User workflows
**Happy path: initial owner assignment**
1. User opens Vehicle Associations screen.
2. No active owner shown.
3. User selects “Assign Owner”, searches account, confirms.
4. UI shows success; active owner now displayed; history includes segment.

**Happy path: owner reassignment**
1. Active owner shown.
2. User selects “Reassign Owner”, chooses a different account, confirms.
3. UI refresh shows new active owner; history shows prior owner end-dated exactly at new start.

**Happy path: assign/change primary driver**
1. Active owner exists.
2. User selects “Assign/Change Driver”, searches person, confirms.
3. UI shows driver as active; history updated.

**Alternate path: driver assignment without owner**
- UI blocks action (disabled) and explains requirement; if attempted via direct route, backend 400 is handled and shown.

---

# 6. Functional Behavior

### Triggers
- Entering Vehicle Associations screen triggers load of:
  - Vehicle summary (identifier fields for header context if available).
  - Current active associations for vehicle.
  - Association history for vehicle.

### UI actions
- **Assign/Reassign Owner**
  - Opens selector for Organization party.
  - Collects `effectiveStartDate` (default: current date-time).
  - Submits create association request (`associationType=OWNER`).
- **Assign/Change Driver**
  - Enabled only if active owner exists.
  - Opens selector for Person party.
  - Collects `effectiveStartDate` (default: current date-time).
  - Submits create association request (`associationType=PRIMARY_DRIVER`).
- **View History**
  - Tabular list with filters (OWNER/PRIMARY_DRIVER, active-only toggle).

### State changes (frontend)
- After successful submit:
  - Re-fetch current associations + history to reflect server-side atomic end-dating and idempotency outcomes.
  - Show toast/inline confirmation message including key result (new owner/driver name, effective start).

### Service interactions (Moqui)
- Screen actions call Moqui services (which in turn call CRM backend endpoints or Moqui façade services depending on project architecture).
- All write actions are transactional on backend; frontend handles outcomes via status codes and response payload.

---

# 7. Business Rules (Translated to UI Behavior)

### Validation
- **Owner required fields:** `vehicleId`, selected `partyId` (organization), `effectiveStartDate`.
- **Driver required fields:** `vehicleId`, selected `partyId` (person), `effectiveStartDate`.
- **Driver requires active owner:** UI disables “Assign/Change Driver” when no active owner is present and shows helper text: “Assign an owner before selecting a primary driver.”
- **No destructive deletes:** UI must not present “delete association”; only “end-date/unassign” if explicitly supported.

### Enable/disable rules
- “Assign/Change Driver” disabled if:
  - No active OWNER association returned from backend.
- “Reassign Owner” label shown if active owner exists; otherwise “Assign Owner”.

### Visibility rules
- Show “Current Owner” and “Current Primary Driver” summary cards/sections when present.
- Show empty state messaging when none exists (especially no owner).

### Error messaging expectations
- `400 Bad Request`: show validation message from backend; map to field errors if error payload identifies fields.
- `404 Not Found`: show “Vehicle or selected party not found. Refresh and try again.”
- `403 Forbidden`: show “You do not have permission to manage vehicle associations for this account.”
- `409 Conflict`: show “Association dates conflict with existing history. Please adjust effective start date or review history.” Provide link/scroll to history section.

---

# 8. Data Requirements

### Entities involved (CRM-owned)
- `VehiclePartyAssociation`
- `Vehicle` (for context display; read-only)
- `Party` (Organization for owner; Person for driver; read-only selection/search)

### Fields (type, required, defaults)
**VehiclePartyAssociation (display & submit)**
- `associationId` (UUID, read-only, server-generated)
- `vehicleId` (UUID, required)
- `partyId` (UUID, required)
- `associationType` (enum, required): `OWNER` | `PRIMARY_DRIVER`
- `effectiveStartDate` (datetime, required; default in UI: now)
- `effectiveEndDate` (datetime, nullable; read-only in UI except possibly via “Unassign” action if supported)
- Audit fields (read-only): createdBy, createdDate, lastUpdated*, etc. (if exposed)

### Read-only vs editable (by state/role)
- All historical rows are read-only.
- User can only create new associations (and possibly end-date active driver if supported); cannot edit historical segments in-place.
- Role constraints are enforced server-side; UI should hide/disable actions if authorization info is available, otherwise handle 403.

### Derived/calculated fields
- “Active” computed client-side as `effectiveEndDate == null` (or endDate > now if backend uses timestamps); however, UI should prefer backend “current associations” response if provided.

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact endpoint/service names in Moqui project are not provided in inputs. The frontend story defines required contract shape; implementation must map to actual Moqui services/endpoints used in this repo.

### Load/view calls
1. **Get vehicle associations (current)**
   - Input: `vehicleId`
   - Output:
     - currentOwner: `{associationId, partyId, partyDisplayName, effectiveStartDate}`
     - currentPrimaryDriver (optional): same shape
2. **Get vehicle association history**
   - Input: `vehicleId`, optional filters: `associationType`, `activeOnly`
   - Output: list of associations with `partyDisplayName`, type, start/end

3. **Party search (owner selection)**
   - Input: query text, filters `partyType=ORGANIZATION` (commercial accounts)
   - Output: list `{partyId, displayName, keyIdentifiers...}`

4. **Party search (driver selection)**
   - Input: query text, filters `partyType=PERSON`
   - Output: list `{partyId, displayName, maybe phone/email for disambiguation (if allowed)}`

### Create/update calls
1. **Create association (OWNER or PRIMARY_DRIVER)**
   - Input:
     - `vehicleId` (UUID)
     - `partyId` (UUID)
     - `associationType` (`OWNER` or `PRIMARY_DRIVER`)
     - `effectiveStartDate` (datetime)
   - Behavior (backend enforced):
     - If creating OWNER and an active OWNER exists:
       - End-date existing active OWNER at new start and create new OWNER (atomic).
       - If same party as current owner: no-op / idempotent success.
     - If creating PRIMARY_DRIVER:
       - Requires active OWNER; ensures only one active primary driver (end-date prior driver if backend supports implicit reassignment; if not, backend should error—UI must handle either behavior via response).

### Submit/transition calls
- No additional workflow transitions beyond create association operations in this story.

### Error handling expectations
- Handle HTTP: 200/201 success, 400 validation, 403 forbidden, 404 missing, 409 conflict, 500 unexpected.
- Frontend must display backend-provided error messages; do not expose stack traces.

---

# 10. State Model & Transitions

### Allowed “states” (association lifecycle)
For each `VehiclePartyAssociation` record:
- **Active:** `effectiveEndDate = null` (or end > now)
- **Inactive (historical):** `effectiveEndDate != null` (and end <= now)

### Role-based transitions
- Fleet Account Manager:
  - Can create OWNER association (initial or reassignment).
  - Can create PRIMARY_DRIVER association when OWNER exists.
  - Can view history.
- Other roles: read-only or no access (enforced by backend; UI should be prepared for 403).

### UI behavior per state
- When no active OWNER:
  - Show empty state for owner.
  - Disable driver assignment action with explanation.
- When active OWNER exists:
  - Show owner summary and “Reassign Owner”.
  - Enable driver assignment.
- When active PRIMARY_DRIVER exists:
  - Show driver summary and “Change Driver” (and “Unassign” only if supported).

---

# 11. Alternate / Error Flows

### Validation failures (400)
- Assign driver without owner:
  - UI prevents; if still occurs, show backend error and keep user on dialog.
- Invalid date/time:
  - Show field-level error (e.g., “Effective start date must be a valid date-time”).

### Concurrency conflicts (409)
- If user selects an effectiveStartDate that overlaps with existing historical segments and backend returns 409:
  - UI shows conflict banner and highlights history list; user can retry with a different start date.

### Unauthorized access (403)
- Attempt to load associations:
  - Show “Not authorized” page/state.
- Attempt to submit changes:
  - Show inline error and disable further submits.

### Empty states
- No association history:
  - Show “No associations recorded yet.”
- Vehicle not found (404):
  - Show not-found state with link back to vehicle search/list.

---

# 12. Acceptance Criteria

### Scenario 1: View current owner and driver
**Given** I am an authorized Fleet Account Manager  
**And** a vehicle exists with an active OWNER association and an active PRIMARY_DRIVER association  
**When** I open the Vehicle Associations screen for that vehicle  
**Then** I see the current owner account displayed with its effective start date  
**And** I see the current primary driver displayed with its effective start date  
**And** I can view a history list that includes both associations.

### Scenario 2: Assign initial owner when none exists
**Given** I am an authorized Fleet Account Manager  
**And** a vehicle exists with no active OWNER association  
**When** I choose “Assign Owner”, select a commercial account, and confirm with an effective start date of now  
**Then** the system creates an OWNER association for the vehicle  
**And** the Vehicle Associations screen refreshes showing the selected account as the current owner  
**And** the association appears in history as active (no end date).

### Scenario 3: Reassign owner with implicit end-dating
**Given** I am an authorized Fleet Account Manager  
**And** a vehicle exists with an active OWNER association to Account A  
**When** I reassign the owner to Account B with effective start date T  
**Then** the system end-dates Account A’s OWNER association at T  
**And** creates a new active OWNER association to Account B starting at T  
**And** the history shows no overlap between the two OWNER associations.

### Scenario 4: Assign primary driver requires active owner (UI guard + backend)
**Given** I am an authorized Fleet Account Manager  
**And** a vehicle exists with no active OWNER association  
**When** I view the Vehicle Associations screen  
**Then** the “Assign/Change Driver” action is disabled with messaging indicating an owner is required.  

**And Given** I still attempt to call the driver assignment action (e.g., via direct route or forced submit)  
**When** the backend responds with 400 indicating an owner is required  
**Then** the UI shows the backend validation error and does not display a current primary driver.

### Scenario 5: Handle 409 conflict on overlapping history
**Given** I am an authorized Fleet Account Manager  
**And** a vehicle has existing historical OWNER associations  
**When** I attempt to create an OWNER association with an effective start date that conflicts with history  
**Then** the backend returns 409 Conflict  
**And** the UI displays a conflict message and prompts me to choose a different effective start date  
**And** no changes are shown as applied.

### Scenario 6: Authorization enforced
**Given** I am logged in without permission to manage vehicle associations for the target account  
**When** I attempt to assign or reassign an owner or driver  
**Then** the backend returns 403 Forbidden  
**And** the UI displays an authorization error and prevents further submission attempts.

---

# 13. Audit & Observability

### User-visible audit data
- On the history list, display:
  - associationType, party display name, effectiveStartDate, effectiveEndDate
  - (If available) createdBy and createdDate for traceability

### Status history
- History is the authoritative record (end-dated rows retained); UI must not hide inactive rows by default unless user toggles active-only.

### Traceability expectations
- All submit actions should include correlation identifiers if supported by the frontend framework (e.g., request ID header via Moqui/web client conventions).
- UI should log (client-side) minimal event: “vehicle_association_update_attempt” and result (success/error) without PII beyond IDs.

---

# 14. Non-Functional UI Requirements

- **Performance:** Association screen should load within 2 seconds under normal conditions; history list should paginate if large.
- **Accessibility:** All dialogs/forms keyboard navigable; labels associated with inputs; validation errors announced to screen readers (Quasar best practices).
- **Responsiveness:** Usable on tablet widths typical for POS operations.
- **i18n/timezone:** Display effective dates in the user’s local timezone; store/send date-times in ISO-8601 per backend expectation.
- **Security/PII:** Party search results should not expose sensitive fields beyond what is necessary to disambiguate (prefer displayName + minimal identifiers).

---

# 15. Applied Safe Defaults

- Default ID: UI-EMPTY-STATE-001  
  - What was assumed: Provide explicit empty-state messaging when no active owner/driver or no history exists.  
  - Why it qualifies as safe: Pure UI ergonomics; does not change domain rules or data.  
  - Impacted sections: UX Summary, Alternate / Error Flows.

- Default ID: UI-PAGINATION-001  
  - What was assumed: Paginate association history list (server- or client-side depending on API), with a sensible default page size.  
  - Why it qualifies as safe: Presentation-only; avoids performance issues without altering business behavior.  
  - Impacted sections: UX Summary, Non-Functional UI Requirements, Service Contracts.

- Default ID: UI-ERROR-MAP-001  
  - What was assumed: Standard mapping of HTTP 400/403/404/409 to user-friendly messages with inline field errors when possible.  
  - Why it qualifies as safe: Error handling is implied by backend contract; mapping does not invent policies.  
  - Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows.

---

# 16. Open Questions

- none

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/168  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Associate Vehicles to Account and/or Individual

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link a vehicle to a commercial account and optionally a primary driver** so that **workorders default to the right billing entity and contacts**.

## Details
- Vehicle associated to an account (owner) and optionally an individual (driver).
- Support reassignment with history.

## Acceptance Criteria
- Create/update associations.
- History preserved on reassignment.

## Integration Points (Workorder Execution)
- Workorder Execution fetches owner/driver for selected vehicle.

## Data / Entities
- VehiclePartyAssociation (owner/driver, dates)

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