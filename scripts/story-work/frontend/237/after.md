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

**Title:** [FRONTEND] [STORY] Estimate: Add Labor to Draft Estimate (Catalog + Optional Custom)

**Primary Persona:** Service Advisor

**Business Value:** Enables accurate, auditable quoting of labor/services on a draft estimate with deterministic labor-rate defaulting and immediate total recalculation, reducing quoting time and pricing errors.

---

## 2. Story Intent

**As a** Service Advisor  
**I want** to add labor/service line items to a Draft estimate by selecting from the service catalog (and optionally adding a custom labor line when allowed)  
**So that** the estimate reflects accurate labor charges and totals before customer approval.

### In-scope
- Add **catalog labor/service** line item(s) to an **Estimate in DRAFT** status.
- Search and select a **Service Catalog** entry by code or description.
- Capture **labor units** for time-based services OR select a **flat-rate** service.
- Default **labor rate** deterministically via backend/pricing rules and display it on the line.
- Add optional **notes/instructions** at line-item level.
- Trigger and display **recalculated totals** after adding a labor line.
- Controlled **custom labor** entry path **only if enabled** by policy/config (backend-driven).

### Out-of-scope
- Creating/editing the underlying Service Catalog.
- Defining pricing rule hierarchy / labor-rate policy (must be provided by backend).
- Editing/removing existing labor lines (unless already supported elsewhere).
- Customer approval capture flows and estimate state transitions (approve/decline/reopen).
- Any billing/payroll implications of labor time (domain clarification required if introduced later).

---

## 3. Actors & Stakeholders
- **Primary actor:** Service Advisor
- **Secondary actors:** None
- **Stakeholders:**
  - Workexec domain owners (estimate workflow + line items)
  - Pricing domain owners (labor rate determination)
  - Audit/Compliance (traceability of estimate financial changes)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated and authorized to edit estimates.
- An **Estimate** exists and is in **status = DRAFT**.
- Service Catalog is accessible for searching.

### Dependencies (blocking if absent)
- Backend endpoints (or Moqui services) must exist for:
  - Loading estimate details including status and current items/totals
  - Searching Service Catalog
  - Adding a labor estimate item (catalog and optionally custom)
  - Returning recalculated totals and updated line list

### Non-blocking but important
- Backend provides/derives tax code behavior for labor items (if required for totals).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate Detail** screen for an estimate in DRAFT status: action **“Add Labor/Service”**.

### Screens to create/modify
- **Modify**: `EstimateDetail` screen (existing) to:
  - show an “Add Labor/Service” action when estimate status is DRAFT
  - show updated labor line items + totals after changes
- **Create/Modify**: `EstimateAddLabor` screen/dialog flow (implementation choice):
  - Catalog search panel + selection
  - Conditional custom entry form (only if allowed)
  - Submit action to create the labor line

### Navigation context
- Route pattern should keep user in estimate context:
  - `.../estimates/:estimateId` (detail)
  - `.../estimates/:estimateId/add-labor` (child screen) or modal launched from detail

### User workflows
**Happy path (catalog labor):**
1. Service Advisor opens Draft Estimate detail.
2. Clicks Add Labor/Service.
3. Searches catalog (code/description).
4. Selects a service.
5. If time-based: enters labor units > 0.
6. Submits.
7. Returns to estimate detail with new line + updated totals.

**Alternate path (custom labor, if enabled):**
1. Search yields no results (or user chooses “Custom Labor”).
2. Custom entry form appears.
3. Enters required description + labor units > 0 (+ any required fields).
4. Submits.
5. Returns to estimate detail with new custom labor line + updated totals.

**Invalid input path:**
- Units <= 0 blocks submit with clear inline validation.

---

## 6. Functional Behavior

### Triggers
- User selects **Add Labor/Service** from Draft estimate.
- User submits catalog selection + units (or flat-rate selection).
- User submits custom labor entry (if allowed).

### UI actions
- Catalog search:
  - Input: `query` string
  - Submit triggers search request; results list shows at least: `serviceCode`, `description`, and indication of `flatRate` vs time-based.
- Selection:
  - Selecting a result populates a “Selected Service” context.
  - If selected service is time-based: show editable `laborUnits`.
  - If flat-rate: show read-only fields as allowed (see Open Questions).

### State changes (frontend-visible)
- Estimate lines list updated to include new labor item.
- Totals updated (subtotal/tax/grand total or equivalent).

### Service interactions
- `GET` estimate (load initial)
- `GET` service catalog search results
- `POST` add estimate labor item
- On success: refresh estimate detail (either by response payload or re-fetch)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Estimate must be DRAFT**:  
  - If not DRAFT, hide/disable Add Labor/Service and show message: “Estimate must be in Draft to add labor.”
- **Labor units must be > 0** for time-based labor:
  - Inline validation error: “Labor units must be a positive number.”
  - Disable submit until valid.
- **Custom labor description required** (if custom entry allowed):
  - Inline validation error: “Description is required.”

### Enable/disable rules
- Submit button disabled while:
  - Required fields invalid/missing
  - A request is in-flight
- Custom labor option visible only when backend indicates policy allows it (see Open Questions for how conveyed).

### Visibility rules
- Notes field visible for both catalog and custom labor items (optional) unless policy requires it (not specified; do not assume).

### Error messaging expectations
- Show backend validation messages when available; otherwise map to:
  - 400: “Please correct the highlighted fields.”
  - 403: “You don’t have permission to modify this estimate.”
  - 404: “Estimate or service not found.”
  - 409: “This estimate was updated by someone else. Refresh and try again.”
  - 5xx: “Something went wrong. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Estimate`
- `EstimateItem` (labor line type)
- `ServiceCatalog` (search and selection)
- (Optional/indirect) `LaborRateRule` (not directly manipulated by UI; rate comes from backend)
- `AuditEvent` (not directly created by UI; must be visible/traceable if exposed)

### Fields
**Estimate (read-only in this story)**
- `estimateId` (string/uuid; required)
- `status` (enum; required, must be `DRAFT` to enable feature)
- Totals fields (names TBD by API; required for display):
  - `subTotal` (money)
  - `totalTax` (money)
  - `grandTotal` (money)

**ServiceCatalog result item (read-only)**
- `serviceCode` (string; required)
- `description` (string; required)
- `isFlatRate` (boolean; required)
- (Optional) `defaultUnits` (decimal; if flat-rate uses fixed units; TBD)

**EstimateItem (labor)**
- `itemSeqId` (number/string; assigned by backend; read-only)
- `itemType` = `LABOR` (read-only, backend-set)
- `serviceCode` (string; required for catalog; optional/controlled for custom)
- `description` (string; required for custom; read-only for catalog unless clarified)
- `laborUnits` (decimal; required for time-based; behavior for flat-rate TBD)
- `laborRate` / `unitPrice` (money; defaulted by backend; editable only if explicitly allowed—NOT assumed)
- `flatRateFlag` / `isFlatRate` (boolean; backend-set)
- `notes` (string/text; optional)
- `taxCode` (string; backend-derived unless UI must set—unclear)

### Read-only vs editable by state/role
- Editable only when Estimate.status = `DRAFT`.
- Role: Service Advisor (assumed authorized); other roles not specified.

### Derived/calculated fields
- Line extended price and estimate totals are derived by backend and returned/displayed; frontend must not compute authoritative totals beyond display formatting.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/endpoints are not provided in inputs. Contracts below specify *required behaviors*; actual service names must align to backend/Moqui implementation.

### Load/view calls
1. **Load estimate detail**
   - Request: `GET /api/estimates/{estimateId}` (or Moqui screen/data call equivalent)
   - Response must include: `status`, existing items, totals.

2. **Search service catalog**
   - Request: `GET /api/service-catalog?query={q}` (or equivalent)
   - Response: list of services with `serviceCode`, `description`, `isFlatRate` (+ any fields needed for flat-rate display).

### Create/update calls
3. **Add labor line (catalog)**
   - Request: `POST /api/estimates/{estimateId}/items/labor` (placeholder)
   - Payload (minimum):
     - `serviceCode` (required)
     - `laborUnits` (required if time-based)
     - `notes` (optional)
   - Response:
     - created item (including `itemSeqId`, price fields)
     - updated estimate totals OR provide enough to refresh via GET

4. **Add labor line (custom, if allowed)**
   - Request: `POST /api/estimates/{estimateId}/items/labor-custom` (placeholder)
   - Payload (minimum):
     - `description` (required)
     - `laborUnits` (required)
     - `laborRate` (required?) **TBD** (inputs suggest manual rate entry; confirm)
     - `notes` (optional)
   - Response: same as above.

### Submit/transition calls
- None (estimate approval/decline out of scope).

### Error handling expectations
- Must support field-level validation errors (ideally structured). If not structured, frontend should still present a summary and mark relevant fields when possible.
- Concurrency: if estimate is modified concurrently, backend should return 409; frontend must offer refresh.

---

## 10. State Model & Transitions

### Allowed states (Estimate)
Per provided workexec approval workflow: `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`.

### Role-based transitions
- None in this story (no approve/decline/reopen actions here).

### UI behavior per state
- `DRAFT`: Add Labor/Service enabled.
- `APPROVED`, `DECLINED`, `EXPIRED`: Add Labor/Service disabled/hidden; show non-blocking info banner: “Estimate is not editable in this state.”

---

## 11. Alternate / Error Flows

1. **No catalog results**
   - Show empty state: “No services found.”
   - If custom allowed: show “Add Custom Labor” action.
   - If not allowed: show guidance “Try a different search term.”

2. **Invalid labor units (<= 0)**
   - Inline validation; prevent submit.

3. **Labor rate defaulting fails / no rule found**
   - Backend may return 4xx with message.
   - Frontend displays error and does not add line.
   - Whether manual override is allowed is **not assumed** (Open Question).

4. **Estimate not DRAFT**
   - If user deep-links to add-labor route: load estimate, detect non-DRAFT, block with message and navigate back to detail.

5. **Unauthorized (403)**
   - Show permission error; do not expose sensitive details.

6. **Conflict (409)**
   - Show “Estimate updated elsewhere” with action to refresh and retry.

7. **Network/5xx**
   - Show retry affordance; keep user input if safe to do so (do not resubmit automatically).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Add a standard time-based labor item to a Draft estimate
**Given** I am authenticated as a Service Advisor  
**And** an estimate with id "<estimateId>" exists in status "DRAFT"  
**When** I open the estimate detail screen  
**And** I choose "Add Labor/Service"  
**And** I search the service catalog for "SVC-OIL-CHG"  
**And** I select service code "SVC-OIL-CHG" that is time-based  
**And** I enter labor units "1.5"  
**And** I submit the add labor form  
**Then** a new labor line item is created and visible on the estimate  
**And** the line item displays service code "SVC-OIL-CHG" and labor units "1.5"  
**And** the labor rate shown is the backend-defaulted rate for the estimate context  
**And** the estimate totals shown reflect the new line item.

### Scenario 2: Prevent adding labor when estimate is not Draft
**Given** I am authenticated as a Service Advisor  
**And** an estimate with id "<estimateId>" exists in status "APPROVED"  
**When** I open the estimate detail screen  
**Then** the "Add Labor/Service" action is not available  
**And** I cannot submit any request to add a labor line from the UI.

### Scenario 3: Reject invalid labor units
**Given** I am authenticated as a Service Advisor  
**And** an estimate exists in status "DRAFT"  
**When** I select a time-based labor service from the catalog  
**And** I enter labor units "0"  
**Then** I see an inline validation message "Labor units must be a positive number."  
**And** the submit action is disabled  
**And** no labor line item is created.

### Scenario 4: Custom labor entry available only when policy allows
**Given** I am authenticated as a Service Advisor  
**And** an estimate exists in status "DRAFT"  
**When** I search the service catalog and receive no results  
**Then** I see an option "Add Custom Labor" only if the backend indicates custom labor is allowed  
**And** if custom labor is not allowed, I do not see the option.

### Scenario 5: Custom labor requires description
**Given** I am authenticated as a Service Advisor  
**And** custom labor entry is allowed  
**And** an estimate exists in status "DRAFT"  
**When** I choose "Add Custom Labor"  
**And** I leave description empty  
**Then** I see an inline validation message "Description is required."  
**And** the submit action is disabled.

### Scenario 6: Handle backend “no labor rate found” error deterministically
**Given** I am authenticated as a Service Advisor  
**And** an estimate exists in status "DRAFT"  
**When** I submit a catalog labor line  
**And** the backend responds with an error indicating no default labor rate is available  
**Then** I see a blocking error message returned by the backend (or a generic mapped message)  
**And** the labor line item is not added  
**And** the estimate totals remain unchanged in the UI.

---

## 13. Audit & Observability

### User-visible audit data
- Not required to display audit log in this story unless estimate detail already exposes it.
- If estimate detail shows “Last updated” / “Updated by”: must update after successful add.

### Status history
- No estimate status changes in this story.

### Traceability expectations
- Each successful add-labor action must result in backend audit event creation (e.g., `EstimateLaborItemAdded`), including:
  - `estimateId`, created `estimateItemId`/`itemSeqId`, `serviceCode` or custom marker, `laborUnits`, defaulted `laborRate`, `userId`, timestamp (UTC).
- Frontend must include correlation/request id headers if project convention exists (not provided; do not assume exact header name).

---

## 14. Non-Functional UI Requirements

- **Performance:** Catalog search should respond fast enough for interactive use; UI should debounce search input (implementation detail) and show loading state.
- **Accessibility:** All form fields must have labels; validation messages must be accessible to screen readers; keyboard navigation supported.
- **Responsiveness:** Must work on tablet widths typical for POS usage.
- **i18n/timezone/currency:** Currency formatting must follow app-wide settings; do not hardcode `$`. Timestamps (if shown) must respect shop/user timezone settings (if already supported).

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a standard empty state for “no catalog results” with guidance and optional custom entry action; qualifies as safe UX ergonomics; impacts UX Summary, Alternate/Error Flows.
- SD-UX-LOADING-STATE: Show loading and disable submit during in-flight requests to prevent duplicate submissions; qualifies as safe recoverability; impacts Functional Behavior, Alternate/Error Flows.
- SD-ERR-HTTP-MAP: Map common HTTP errors (400/403/404/409/5xx) to user-friendly messages without leaking internals; qualifies as safe error-handling; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Labor rate fallback behavior (blocking):** If no labor rate rule matches, should the UI (a) block and prevent adding the line, or (b) allow manual labor rate entry with permission and reason code? If (b), what permission and what reason-code capture is required?
2. **Flat-rate editability (blocking):** For flat-rate catalog services, are `laborUnits` and/or `laborRate` editable in the UI, or must they be locked to catalog/pricing output?
3. **Custom labor policy signal (blocking):** How does the frontend determine whether custom labor entry is allowed (e.g., field on estimate payload, dedicated config endpoint, or permission flag)?
4. **API/service contract specifics (blocking):** What are the exact Moqui screen/service endpoints and payload schemas for:
   - service catalog search
   - adding catalog labor line
   - adding custom labor line
   - returning updated totals (included in response vs requires re-fetch)
5. **Tax code handling (blocking):** Is `taxCode` chosen by user, derived from service, or derived from shop policy? If user-selectable, what are allowed values and defaults?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Estimate: Add Labor to Estimate  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/237  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Add Labor to Estimate

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
Service Advisor

## Trigger
A Draft estimate exists and labor/services need to be quoted.

## Main Flow
1. User searches service catalog by service code or description.
2. User selects a service and specifies hours/units or selects a flat-rate option.
3. System defaults labor rate based on shop, role/class, and pricing rules.
4. System adds the labor line item and recalculates totals.
5. User adds notes/instructions if required for execution.

## Alternate / Error Flows
- Service not found → allow controlled custom service entry (if enabled) with required description and labor units.
- Labor units invalid (<=0) → block and prompt correction.

## Business Rules
- Each labor line item references a service code (or controlled custom code).
- Labor rate defaulting must be deterministic (policy-driven).
- Totals must be recalculated on labor line changes.

## Data Requirements
- Entities: Estimate, EstimateItem, ServiceCatalog, LaborRateRule, AuditEvent
- Fields: itemSeqId, serviceCode, laborUnits, laborRate, flatRateFlag, notes, taxCode

## Acceptance Criteria
- [ ] User can add labor/service line items to a Draft estimate.
- [ ] Labor pricing defaults correctly per configured rules.
- [ ] Totals update immediately after adding/editing labor items.

## Notes for Agents
Keep labor structure compatible with time-based and flat-rate models.

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