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
[FRONTEND] [STORY] Estimate: Revise Estimate Prior to Approval (Versioned + Approval Invalidation)

### Primary Persona
Service Advisor

### Business Value
Ensure Service Advisors can correct or adjust a customer estimate before commitment while preserving immutable history, maintaining auditability, and preventing stale/invalid approvals from being used.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to “Revise” an estimate by creating a new editable version linked to the prior version  
- **So that** I can adjust scope/pricing/notes before customer approval while the system preserves a full version history and invalidates any prior approvals tied to earlier versions.

### In-scope
- Revise action available on an estimate when in an allowed state (see **State Model**).
- Creating a new estimate version linked to the prior version (immutable prior version).
- Editing the new version (line items + terms/notes) and saving.
- Recalculation of totals on save (and optionally on edit, see safe defaults).
- Invalidation of prior approvals when revision occurs from an approval-pending context.
- Viewing revision/version history and comparing prior versions (read-only).

### Out-of-scope
- Creating or managing approval configurations (method selection) beyond displaying current method if present.
- Notifications (email/SMS) to customers about new revision.
- Converting estimate to work order.
- Change request workflow after conversion to work order (explicitly open question).
- Defining pricing/tax calculation logic (assumed backend-owned).

---

## 3. Actors & Stakeholders

- **Primary Actor:** Service Advisor (creates revisions, edits, submits)
- **Secondary Actors:**  
  - Shop Manager (read-only oversight of history)  
  - Auditor/Compliance (needs immutable trail)  
- **System Dependencies:** workexec backend services for estimates/versions/approvals; audit/event subsystem (backend).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to revise estimates (exact permission string is unknown; must be confirmed).
- Estimate exists and can be loaded by ID.
- Estimate is in a state that permits revision (must be confirmed against backend; see Open Questions).

### Dependencies
- Backend must support:
  - Creating a new estimate version/revision linked to a prior version.
  - Fetching current estimate and version history.
  - Persisting edits to the active version.
  - Invalidation of approvals tied to a previous version when revision is initiated from an approval-pending state.
- Moqui screens must be able to call these endpoints/services (exact Moqui service names/routes are not provided).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Estimate Detail screen: action button **Revise** (visible/enabled only in eligible states and with permission).
- From Estimate List: contextual action **Revise** (optional; if present, must follow same eligibility rules).

### Screens to create/modify
1. **`EstimateDetail.xml` (modify)**  
   - Add Revise action, version indicator (e.g., “v3”), and link to “Version History”.
2. **`EstimateRevise.xml` (new or modify existing edit screen)**  
   - Revision initiation confirmation + revision reason capture (if required by policy; see Open Questions).
   - Edit form for the new version (line items, notes/terms).
3. **`EstimateVersionHistory.xml` (new)**  
   - List versions with status, createdAt, createdBy, approval status.
   - View read-only prior version details.
   - Compare two versions (minimal: show side-by-side summary; details in functional behavior).

### Navigation context
- Route pattern (example; confirm actual project conventions):
  - `/estimates/:estimateId` → Estimate detail
  - `/estimates/:estimateId/revise` → Revision workflow (creates new version then opens editor)
  - `/estimates/:estimateId/history` → Version history

### User workflows
- **Happy path**
  1. Advisor opens Estimate Detail (current version visible).
  2. Clicks **Revise**.
  3. Confirms and (optionally) enters revision reason.
  4. System creates new version, marks it active, and navigates to edit screen.
  5. Advisor edits items/notes and saves.
  6. Totals are recalculated; save succeeds; advisor returns to Estimate Detail showing new version.
- **Alternate paths**
  - User cancels before confirming revision: no new version created.
  - User confirms revision, then cancels edits: behavior depends on backend (discard draft version vs keep as draft). This is an Open Question and must be confirmed.
  - Attempt revise in non-eligible state: action disabled or blocked with error.

---

## 6. Functional Behavior

### Triggers
- User clicks **Revise** on Estimate Detail.

### UI actions
- **Revise click**
  - Client requests latest estimate state (to avoid stale UI).
  - If eligible, show confirmation modal:
    - Summary: current version number, status, and warning that revision preserves history and may invalidate approvals.
    - Input: revision reason (only if required).
    - Actions: Confirm / Cancel.
- **Confirm**
  - Call backend to create a new version/revision record.
  - On success: navigate to edit screen for the new active version.
- **Edit**
  - Allow modifying line items (add/remove/edit qty/price if allowed), notes/terms.
  - Save triggers backend update and totals recalculation.
- **Version history**
  - List all versions; allow opening prior version read-only.
  - Compare: user selects two versions → show differences (at minimum totals + line items changed).

### State changes (frontend-visible)
- On successful revision initiation:
  - Active version becomes the newly created version with status per policy (likely `DRAFT`).
  - Prior version becomes immutable and may display as `ARCHIVED` or equivalent (backend-defined).
- If revision initiated from a “pending approval” context:
  - Prior approval(s) for the previous version become invalidated (backend-owned state).

### Service interactions
- Load estimate + current active version.
- Create revision (new version).
- Update version (save edits).
- Load version history + retrieve specific prior version.
- (Optional) Load compare diff if backend provides; otherwise compute diff client-side from two loaded versions.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Revise action allowed only when estimate is in eligible state(s). UI must:
  - Hide/disable Revise when not eligible.
  - Still handle backend denial gracefully (409/400) if state changed concurrently.
- Revision must preserve history:
  - UI must treat prior versions as read-only (no edit controls when viewing older versions).
- Any revision after approval invalidates approval:
  - If estimate is in an approval-pending state, show a warning in confirmation modal.
  - After revision creation, UI should display a banner on the new version: “Prior approval invalidated; new approval required” (wording not domain-policy; can be generic).

### Enable/disable rules
- Disable “Save” if required fields missing (e.g., empty line item qty) based on backend validation responses (since exact field-level requirements aren’t fully specified).
- Disable “Confirm Revise” while request is in-flight to prevent double-submit.

### Visibility rules
- Version history tab/link visible to users with view permission; if permission unknown, default to same permission as estimate view.
- Show version number and status prominently on detail and edit screens.

### Error messaging expectations
- Permission denied: “You do not have permission to revise this estimate.”
- Invalid state: “This estimate cannot be revised in its current state.”
- Concurrency: “This estimate changed since you opened it. Reload and try again.”
- Generic failure: “Unable to revise estimate. Please try again or contact support.”

---

## 8. Data Requirements

> Note: Backend entity model in provided inputs conflicts/varies (Estimate vs EstimateVersion vs EstimateRevision). Frontend must rely on API responses. Fields below are “frontend required fields” for display and form binding; exact schema must be confirmed.

### Entities involved (conceptual)
- `Estimate`
- `EstimateVersion` (or `EstimateRevision`)
- `ApprovalRecord` (for invalidation/visibility)
- `AuditEvent` (read-only display of revision metadata, if exposed)

### Fields
#### Estimate (read)
- `estimateId` (string/number, required)
- `customerId` (string/number, required for display link)
- `vehicleId` (string/number, optional display)
- `shopId/locationId` (string/number, optional display)
- `activeVersionId` (string/number, required if versioned model)
- `status` (enum/string, required) **(state name must be confirmed)**
- `createdAt` (datetime, read-only)

#### Estimate Version / Revision (read/write on active version)
- `estimateVersionId` (id, read-only)
- `versionNumber` (int, read-only)
- `status` (enum/string, read-only in UI except via transitions)
- `priorVersionId` (id, read-only)
- `revisionReason` (string, required? Open Question)
- `revisedBy/createdBy` (user id/name, read-only)
- `revisedAt/createdAt` (datetime, read-only)
- `lineItems[]` (collection, editable)
  - `lineItemId` (id, read-only)
  - `type` (PART|LABOR|FEE?) (enum, required) **unknown**
  - `description` (string, required)
  - `productId/serviceId` (id, optional depending on type)
  - `quantity` (decimal, required, >0)
  - `unitPrice` (money/decimal, editable only if permitted)
  - `declined` (bool, optional per approval workflow; default false)
- `notes` / `terms` (string, editable)
- `totals` (object, read-only; calculated)
  - `subtotal`, `tax`, `fees`, `grandTotal` (money/decimal)

### Read-only vs editable by state/role
- Prior versions: all fields read-only.
- Active draft version: editable fields include lineItems and notes/terms; price override fields editable only if user has permission (permission string unknown → Open Question).

### Derived/calculated fields
- Totals are calculated by backend; UI displays returned totals and does not compute tax/fees authoritatively.

---

## 9. Service Contracts (Frontend Perspective)

> Exact Moqui service names/endpoints for frontend are not provided in inputs. The backend reference provides REST endpoints for estimate approve/decline/reopen but not revise/versioning. This is blocking for a truly buildable story; contracts below define required capabilities and expected HTTP semantics.

### Load/view calls
- `GET /api/estimates/{estimateId}`  
  - Returns estimate with current active version summary (must include active version id/number and status).
- `GET /api/estimates/{estimateId}/versions` (or similar)  
  - Returns list of versions with metadata.
- `GET /api/estimates/{estimateId}/versions/{versionId}`  
  - Returns read-only version details.

### Create/update calls
- **Create revision/version**
  - `POST /api/estimates/{estimateId}/revise` (proposed)
  - Request:
    - `requestedBy` (userId) (if backend requires; otherwise derived from auth)
    - `revisionReason` (optional/required per policy)
  - Response: new active version details (`versionId`, `versionNumber`, status)
  - Status: `201 Created`
- **Update active version**
  - `PUT /api/estimates/{estimateId}/versions/{versionId}` (proposed) or `PUT /api/estimates/{estimateId}` if backend uses active version
  - Request: updated line items + notes/terms
  - Response: updated version + recalculated totals
  - Status: `200 OK`

### Submit/transition calls
- None beyond revision initiation in this story (approval submission is separate story).

### Error handling expectations
- `400 Bad Request`: validation errors (return field-level errors)
- `401 Unauthorized`: login required
- `403 Forbidden`: no permission (revise or price override)
- `404 Not Found`: estimate/version not found
- `409 Conflict`: invalid state due to concurrent changes, or attempting to revise non-revisable status
- UI must map these to user-friendly messages and preserve form state where possible.

---

## 10. State Model & Transitions

### Allowed states (must be confirmed)
Provided domain doc `CUSTOMER_APPROVAL_WORKFLOW.md` defines:
- `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`

But story inputs mention “PendingApproval”, and backend reference story used “Archived/ConvertedToWorkOrder” which conflicts with authoritative approval workflow doc.

**Therefore: state model for “revision eligibility” is blocked until clarified.**

### Required transition behavior (frontend)
- If estimate is in eligible state (at least `DRAFT`; possibly also `APPROVED` depending on policy), Revise is available.
- If estimate is in any non-eligible or terminal state, Revise is disabled and backend denial handled.

### Role-based transitions
- Service Advisor can initiate revision if authorized.
- Others (e.g., technician) likely view-only; exact RBAC not specified (Open Question).

### UI behavior per state (minimum)
- `DRAFT`: Revise available (creates new version).
- Approval-pending state (name TBD): Revise available but warns that approval will be invalidated.
- `APPROVED`: policy unclear whether revise is allowed; must be confirmed.
- `DECLINED`/`EXPIRED`: unclear (reopen exists, but revise semantics unclear); must be confirmed.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required revision reason (if required): block confirm and show inline error.
- Invalid line item quantity/unit price: show inline error from backend response; do not lose user edits.

### Concurrency conflicts
- If estimate status/version changes between load and revise:
  - Backend returns `409 Conflict`.
  - UI shows “Estimate changed; reload” with a reload action.
- If user double-clicks Revise:
  - UI disables confirm during in-flight call and treats backend idempotently if supported; otherwise show single created version.

### Unauthorized access
- If user lacks revise permission:
  - Revise button hidden or disabled.
  - If direct navigation to `/revise`, show Access Denied and link back.

### Empty states
- Version history empty (should not happen if v1 exists): show “No versions found” and provide reload.
- Compare without selecting two versions: disable compare action and explain requirement.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Revise a Draft estimate creates a new version and opens it for editing
**Given** an estimate exists with status `DRAFT` and current version number `1`  
**And** I am logged in as a Service Advisor with permission to revise estimates  
**When** I open the estimate detail screen and click **Revise** and confirm  
**Then** the system creates a new estimate version linked to version `1`  
**And** the UI navigates to the edit screen for the new version  
**And** the UI shows the new version number `2` and status `DRAFT` (or backend-provided editable status)

### Scenario 2: Saving edits recalculates totals and persists changes
**Given** I have created a new revised version of an estimate  
**When** I change a line item quantity and click **Save**  
**Then** the system persists the updated version  
**And** the UI displays recalculated totals returned by the backend  
**And** the updated version remains the active version on the estimate detail screen

### Scenario 3: Revising from an approval-pending context invalidates prior approval
**Given** an estimate exists in an approval-pending state (state name per backend) with an outstanding approval record  
**When** I click **Revise** and confirm  
**Then** the system creates a new version in an editable state  
**And** the system invalidates the outstanding approval tied to the previous version  
**And** the UI displays an indicator that prior approval is no longer valid for the new version

### Scenario 4: Attempting to revise in a non-eligible state is blocked
**Given** an estimate exists in a non-revisable state (e.g., converted to work order or terminal state per policy)  
**When** I attempt to revise the estimate  
**Then** the UI prevents the action (Revise disabled) **or** the backend returns an error  
**And** the UI shows a clear message explaining why revision is not allowed

### Scenario 5: Permission denied when revising
**Given** I am logged in as a user without permission to revise estimates  
**When** I navigate to an estimate and attempt to revise it  
**Then** the system returns `403 Forbidden`  
**And** the UI shows “Access Denied” and does not create a new version

### Scenario 6: Concurrency conflict during revision initiation
**Given** I have an estimate detail screen open  
**And** another user changes the estimate status so it is no longer revisable  
**When** I click **Revise** and confirm  
**Then** the backend returns `409 Conflict`  
**And** the UI prompts me to reload the estimate and try again  
**And** no new version is shown as active in the UI

### Scenario 7: Version history shows prior versions as read-only
**Given** an estimate has versions `1`, `2`, and `3` and version `3` is active  
**When** I open Version History and select version `1`  
**Then** the UI displays version `1` in read-only mode  
**And** editing controls are not available for version `1`  
**And** I can return to the active version `3`

---

## 13. Audit & Observability

### User-visible audit data
- On Version History list, show:
  - versionNumber, createdAt, createdBy
  - status
  - “Revision reason” if captured/stored and exposed
  - approval invalidation indicator if applicable (e.g., “Approval invalidated” badge)

### Status history
- Version status is displayed per version.
- If backend exposes revision events/audit events, show a simple event log section (optional; do not block).

### Traceability expectations
- UI must include estimateId and versionId in log context (frontend console logs kept minimal).
- For API calls, include correlation/trace id header if project convention supports it (safe default only if already in repo conventions; otherwise Open Question).

---

## 14. Non-Functional UI Requirements

- **Performance:**  
  - Version history list should load within 2s under normal conditions; paginate if large (safe default).
- **Accessibility:**  
  - Revise confirmation modal must be keyboard navigable; buttons have accessible labels.
- **Responsiveness:**  
  - Works on tablet form factor (service counter usage).
- **i18n/timezone/currency:**  
  - Display dates in shop/user timezone if available; otherwise ISO/local browser (needs repo convention).  
  - Currency formatting uses backend-provided currency code if present; otherwise shop default (Open Question if not provided).

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide clear empty-state messaging and reload action for Version History when no data is returned; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION-01: Paginate Version History list after 25 items with server-side parameters if supported; safe for ergonomics and performance; impacts UX Summary, Service Contracts.
- SD-ERR-MAP-01: Map HTTP 400/401/403/404/409/5xx to consistent toast/banner messages and preserve form state on 400; safe standard error handling; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Estimate states & revision eligibility:** What are the authoritative estimate statuses in this system for the frontend (is there a `PENDING_APPROVAL` state, or only `DRAFT/APPROVED/DECLINED/EXPIRED`)? In which exact states is “Revise” permitted?
2. **Revision after APPROVED / Converted-to-WorkOrder policy:** If an estimate is already `APPROVED` and/or converted to a work order, is revision:
   - disallowed, or
   - allowed but creates a new version requiring re-approval, or
   - redirected into Change Request workflow?
3. **Revision reason requirement:** Is `revisionReason` required on revise initiation? If yes, is there a reason code list or free text only?
4. **Abandon revision semantics:** If a new version is created on “Confirm Revise” but the advisor cancels without saving edits, should the system:
   - discard/delete the new version, or
   - keep it as the active draft version?
5. **API contract for versioning:** What are the exact backend endpoints and payloads for:
   - creating a revision/version,
   - listing versions,
   - loading a specific version,
   - saving edits to the active version?
6. **Permissions/RBAC strings:** What permission(s) control:
   - ability to revise,
   - ability to edit line items,
   - ability to override prices (and whether a reason code is required)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Estimate: Revise Estimate Prior to Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/235  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Revise Estimate Prior to Approval

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
A Draft or PendingApproval estimate requires changes (scope, price, quantities, fees, or notes).

## Main Flow
1. User opens the estimate and selects 'Revise'.
2. System creates a new estimate version (or revision record) linked to the prior version.
3. User edits line items and/or terms.
4. System recalculates totals and updates revision metadata.
5. System invalidates any prior approvals and sets state back to Draft (or Revision state) per policy.

## Alternate / Error Flows
- Estimate is already promoted to workorder → disallow revision or create a change request workflow (policy).
- User lacks permission to revise after approval submission → block and log.

## Business Rules
- Revision must preserve history and allow comparing versions.
- Any revision after approval invalidates approval.
- Revision increments version and records who/when/why.

## Data Requirements
- Entities: Estimate, EstimateRevision, ApprovalRecord, AuditEvent
- Fields: estimateId, version, status, revisionReason, revisedBy, revisedDate, priorVersionRef

## Acceptance Criteria
- [ ] System preserves revision history and allows retrieving prior versions.
- [ ] Any existing approval is invalidated on revision and recorded.
- [ ] Revised estimate totals are recalculated and stored.

## Notes for Agents
Revision history is a core audit artifact—do not overwrite approved snapshots.

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x