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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Workexec: Priced Part Substitution Picker for Work Order/Estimate Part Lines

### Primary Persona
Service Advisor

### Business Value
When an original part is unavailable, the advisor can quickly choose an authorized substitute with correct customer pricing and audit traceability, reducing delays and avoiding manual pricing math.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to fetch authorized substitute candidates for an unavailable part line and see availability + pricing (including policy flags)  
- **So that** I can select a substitute confidently and keep the estimate/work order accurate and auditable without manual math.

### In-Scope
- UI action on a specific **part line item** to “Find substitutes”
- Display of substitute candidates with:
  - availability status
  - price quote (or “Price unavailable”)
  - substitution/policy flags (e.g., OEM/Aftermarket) as provided by backend
- Apply selected substitute to the target line item via backend call
- Enforce “cannot commit without price unless manual price permission” at UI level (based on backend response/permission signal)
- User cancel flow leaving the line unchanged
- Empty states and error states aligned to backend outcomes

### Out-of-Scope
- Authoring/editing substitution policies (parts/inventory domain configuration)
- Reprice workflow after a substitute is applied (explicit separate action/story)
- Inventory receiving/picking flows
- Notification delivery for any alerts
- Creating non-catalog parts or manual price entry UX (unless explicitly clarified as required here)

---

## 3. Actors & Stakeholders

### Actors
- **Service Advisor**: initiates substitute search and applies a substitute to a line
- **System (Moqui frontend)**: orchestrates calls to backend endpoints, renders candidates, blocks invalid commits

### Stakeholders
- **Parts Manager / Inventory team**: expects only policy-allowed substitutions shown
- **Service Manager**: expects fast workflow and correct pricing
- **Customer**: expects accurate estimate/invoice matching substituted part used
- **Audit/Compliance**: expects traceable, immutable substitution history

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated
- A Work Order or Estimate exists and is viewable by the user
- A target **part line item** exists and is eligible for substitution (original is unavailable per workflow context; backend is source of truth)

### Dependencies
- Backend endpoints exist and are reachable for:
  - retrieving substitute candidates (with availability + policy flags)
  - retrieving pricing (either embedded in candidate response or via backend aggregation)
  - applying a selected substitute to a line item
- Backend provides a permission/decision mechanism for “manual price allowed” if pricing unavailable (e.g., permission flag, capability field, or 403/409 responses)
- Moqui screen(s) already exist for viewing/editing work orders/estimates with part lines (this story will extend them)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Work Order detail** screen, within the **Parts** line items section, an action per line: `Find Substitutes`
- From **Estimate detail** screen, within the **Parts** line items section, an action per line: `Find Substitutes`

### Screens to create/modify
- **Modify** existing screens (expected):
  - `WorkOrderDetail` (or equivalent): add action & modal/dialog
  - `EstimateDetail` (or equivalent): add action & modal/dialog
- **Create** a reusable embedded screen/component:
  - `components/PartSubstitutionPicker` (Moqui screen include) to render candidate list and handle apply/cancel

> Exact screen names/paths must match repo conventions (see Open Questions).

### Navigation context
- Picker opens as a modal/dialog (Quasar `QDialog`) over the current detail screen
- On successful apply:
  - dialog closes
  - the line item row updates immediately (refresh line items) showing substitute part number/description and locked price

### User workflows

#### Happy path (priced candidate)
1. Advisor clicks `Find Substitutes` on a part line.
2. System loads candidates.
3. Advisor selects a candidate with an available price.
4. Advisor confirms apply.
5. System applies substitution; line updates; success message displayed.

#### Alternate path (pricing unavailable)
1. Candidate appears with `Price Unavailable`.
2. Advisor can view but cannot confirm apply unless manual price is allowed (per backend/permission).

#### Alternate path (no results)
- Show “No authorized substitutes found.” or “No substitutes are currently available.” depending on backend classification.

#### Cancel
- Advisor closes dialog without changes.

---

## 6. Functional Behavior

### Triggers
- User clicks `Find Substitutes` on a specific part line item for:
  - a Work Order, or
  - an Estimate

### UI actions
- Open picker dialog
- Show loading state while fetching candidates
- Show list of candidates:
  - part number, description
  - availability status text
  - price display (formatted currency) or “Price Unavailable”
  - policy flags badges (as returned)
- Candidate selection:
  - single select
- Confirm:
  - calls apply endpoint
- Cancel:
  - closes without calling apply endpoint

### State changes (frontend)
- Local dialog state: `loading`, `loaded`, `error`
- Selected candidate state
- After apply success: refresh the parent screen’s line item data from backend

### Service interactions
- Fetch candidates: 1 call on dialog open (or on explicit “Search” if required)
- Apply substitution: 1 call on confirm
- Refresh line: either:
  - re-fetch the full work order/estimate detail, or
  - re-fetch line items list, or
  - update line from apply response (preferred if backend returns updated line)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Must have `workOrderId` + `lineItemId` OR `estimateId` + `lineItemId` before opening picker
- Confirm/Apply button enabled only when:
  - a candidate is selected AND
  - candidate has a resolved price **OR** user is allowed to proceed via manual price override mechanism (see Open Questions)

### Enable/disable rules
- Disable `Find Substitutes` action if line is not eligible (only if backend provides an explicit flag like `canSubstitute=false`; otherwise show action but handle backend 409 with user-friendly message)

### Visibility rules
- Show policy flags only as provided; do not infer (no safe defaults)
- If backend indicates candidate exists but is not available:
  - either hide it or show it disabled; must align to backend payload semantics (Open Question)

### Error messaging expectations
- No substitutes: “No authorized substitutes found.”
- None available: “No substitutes are currently available.”
- Pricing failure for candidate: show candidate with “Price Unavailable”
- Apply failure due to stale state/lock: “This line changed since you opened substitutes. Refresh and try again.”
- Unauthorized: “You don’t have permission to substitute parts on this document.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
From backend story reference (for conceptual mapping):
- SubstituteLink
- SubstitutePolicy
- PriceQuoteResponse
- WorkOrder line item (part line)
- Estimate line item (part line)
- WorkOrderPartSubstitution (history record created server-side)

### Fields

#### Inputs (required)
- `workOrderId` (string/number) OR `estimateId` (string/number)
- `lineItemId` (string/number)
- `originalPartNumber` (string) **if required by backend** (Open Question: can backend derive from lineItemId?)

#### Candidate display fields (from backend)
- `partNumber` (string, required)
- `description` (string, optional but expected)
- `availabilityStatus` (enum/string, required)
- `availabilityMessage` (string, optional)
- `policyFlags` (string[], optional)
- `priceStatus` (enum: e.g., AVAILABLE | UNAVAILABLE, required)
- `unitPrice` / `finalPrice` (decimal, nullable)
- `currency` (ISO-4217 string, required if price present)
- optional: `pricingErrorCode` (string, optional; must be non-sensitive)

#### Apply payload fields (minimum)
- Selected substitute identifier:
  - `substitutePartNumber` (string) and/or `substituteProductId` (id)
- Optional: `reasonCode` (string) **(Open Question: required? UI not specified)**

### Read-only vs editable
- All candidate fields are read-only
- Selection is editable (radio/select)
- Apply confirm is an action, not editable data

### Derived/calculated fields
- Price formatting (currency)
- Availability label text mapping (frontend-only)

---

## 9. Service Contracts (Frontend Perspective)

> Backend reference provides high-level behavior but not exact endpoints for this substitution feature. Frontend must NOT guess final paths; see Open Questions.

### Load/view calls

**Contract A: Fetch substitution candidates**
- Purpose: return policy-filtered substitutes with availability + pricing (if available), plus flags
- Request (conceptual):
  - `GET /api/.../substitutes?workOrderId&lineItemId` or similar
- Response (conceptual):
  - `{ originalPartNumber, candidates:[{...fields above...}], constraints:{...} }`

### Create/update calls

**Contract B: Apply substitute to line item**
- Purpose: update line item to substitute and lock price; create substitution history
- Request (conceptual):
  - `POST /api/.../apply-substitute` with identifiers
- Response (conceptual):
  - updated line item snapshot and substitution id, or success + require refresh

### Submit/transition calls
- None beyond apply substitution

### Error handling expectations (HTTP mapping)
- `400` invalid request (missing ids)
- `401/403` unauthorized/forbidden
- `404` work order/estimate/line not found
- `409` conflict (line changed, state not eligible, price lock constraint, etc.)
- `5xx` backend failure → show generic error with retry

---

## 10. State Model & Transitions

### Allowed states (domain)
This story does **not** define new workflow states. It respects:
- Estimate state machine (DRAFT/APPROVED/DECLINED/EXPIRED) for whether editing is allowed (backend source of truth)
- WorkOrder state machine (DRAFT/APPROVED/ASSIGNED/WORK_IN_PROGRESS/...) for whether substitution is allowed (backend source of truth)

### Role-based transitions
- Apply substitution is an authorized action; frontend displays action based on existing screen permissions if available (otherwise rely on backend 403)

### UI behavior per state
- If backend indicates the document is not editable / substitution not allowed:
  - disable `Find Substitutes` and show tooltip/message indicating why (if backend provides reason)
- If state becomes invalid while dialog open:
  - apply returns 409; UI prompts refresh

---

## 11. Alternate / Error Flows

### Validation failures
- Missing identifiers: do not open dialog; show inline error/log

### Concurrency conflicts
- If apply returns `409` because line changed or was already substituted:
  - close dialog or keep open with “Refresh candidates” action
  - force refresh of parent line items on user action

### Unauthorized access
- If fetch candidates returns `403`:
  - show “You don’t have permission…” and do not render list

### Empty states
- No authorized substitutes: show message; no list
- Substitutes exist but none available: show message; optionally allow toggling “Show unavailable” if backend returns them (Open Question)

---

## 12. Acceptance Criteria

### Scenario 1: View priced substitute candidates and apply
**Given** I am a Service Advisor viewing a Work Order with a part line item that is unavailable  
**When** I click “Find Substitutes” for that part line  
**Then** I see a list of authorized substitute candidates including availability and final customer price (where available) and policy flags  
**When** I select a candidate with `priceStatus=AVAILABLE` and confirm  
**Then** the system applies the substitution via backend  
**And** the part line item updates to show the substitute part number/description and locked price  
**And** the original part is preserved for traceability (server-side)

### Scenario 2: No authorized substitutes
**Given** I am viewing a part line with no policy-authorized substitutes  
**When** I click “Find Substitutes”  
**Then** I see “No authorized substitutes found.”  
**And** no line item changes occur

### Scenario 3: Substitutes exist but none available
**Given** authorized substitutes exist but all have `availabilityStatus` indicating not available  
**When** I click “Find Substitutes”  
**Then** I see “No substitutes are currently available.” (or equivalent backend-provided message)  
**And** I cannot apply a substitute  
**And** no line item changes occur

### Scenario 4: Pricing unavailable for an available substitute
**Given** a substitute candidate is available but pricing fails and it is returned with `priceStatus=UNAVAILABLE` and `unitPrice=null`  
**When** I view the candidate list  
**Then** I see that candidate with “Price Unavailable”  
**And** the Apply/Confirm action is disabled for that candidate unless the backend indicates manual price is permitted

### Scenario 5: User cancels
**Given** the substitution picker is open  
**When** I cancel/close the dialog  
**Then** no backend apply call is made  
**And** the original line item remains unchanged

### Scenario 6: Concurrency conflict on apply
**Given** I opened substitutes for a line item  
**And** the line item changes on the server before I confirm  
**When** I attempt to apply a substitute  
**Then** I receive a conflict error (409)  
**And** the UI instructs me to refresh and try again  
**And** no partial UI state persists as “applied”

---

## 13. Audit & Observability

### User-visible audit data
- After apply success, show a success toast including substitute part number
- If the screen has an “History/Activity” section, refresh it (if available) so substitution appears (server-side audit remains authoritative)

### Status history
- Not creating a new state machine; rely on server audit entities/events

### Traceability expectations
- Frontend must pass identifiers sufficient for backend to:
  - record `selectedBy` and `selectedAt` (backend should derive user from auth context; do not send PII)
  - link substitution to workOrderId/estimateId and lineItemId
- Include correlation id header if project convention exists (Open Question)

---

## 14. Non-Functional UI Requirements

- **Performance:** Candidate fetch should render within 2s for typical lists (<50 candidates). Show loading state immediately.
- **Accessibility:** Dialog is keyboard navigable; focus trapped in dialog; selection accessible via keyboard; status text readable by screen readers.
- **Responsiveness:** Works on tablet layout; dialog content scrolls.
- **i18n/timezone/currency:** Currency formatting uses backend-provided currency code; no currency conversion performed on frontend.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty states (“No authorized substitutes found”, “No substitutes currently available”) to avoid blank lists; safe because it’s UI ergonomics and does not change business policy. (Impacted: UX Summary, Error Flows)
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/403/404/409/5xx to user-facing messages and retry behavior; safe because it’s generic error handling and does not define domain policy. (Impacted: Service Contracts, Alternate/Error Flows)

---

## 16. Open Questions

1. **Backend API contract (blocking):** What are the exact endpoints and payload schemas for:
   - fetching substitution candidates for a *work order part line*?
   - fetching substitution candidates for an *estimate part line*?
   - applying a selected substitute to the target line?
2. **Document scope (blocking):** Is substitution supported on **both** Estimates and Work Orders in the frontend for this story, or only Work Orders? Backend story mentions “Work Order (or Estimate)” but UI scope must be confirmed.
3. **Eligibility source (blocking):** How does frontend determine the “original is unavailable” condition?
   - Is there a line-level field like `availabilityStatus=UNAVAILABLE`?
   - Or should the action always be available and backend enforces?
4. **Manual price override (blocking/security):** How does backend indicate the user has permission `ENTER_MANUAL_PRICE`, and what is the expected frontend behavior?
   - Is manual price entry required in this story or explicitly out-of-scope?
5. **Candidate inclusion rules (blocking):** Should the backend return:
   - only available candidates, or
   - both available and unavailable candidates with availability statuses?
   The backend story includes messaging for both; UI behavior depends on payload.
6. **Identity/audit (non-blocking if convention exists):** Does the frontend need to send `approvedBy/userId` style fields, or does backend derive user from auth? (Prefer derived.)
7. **Moqui screen routing (blocking for implementation):** What are the actual screen paths/names for Work Order detail and Estimate detail in `durion-moqui-frontend` to attach this feature?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Handle Substitution Pricing for Part Substitutions  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/113  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Handle Substitution Pricing for Part Substitutions

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want substitutes priced when originals are unavailable so that work continues without manual math.

## Details
- Return substitute candidates with availability + prices.
- Enforce allowed substitution types.

## Acceptance Criteria
- Candidates returned with policy flags.
- Selection captured on estimate/WO line.

## Integrations
- Workexec integrates with substitution + availability queries.

## Data / Entities
- SubstituteLink, SubstitutePolicy, PriceQuoteResponse

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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