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
[FRONTEND] [STORY] Promotion: Create Workorder from Approved Estimate

### Primary Persona
Service Advisor (Back Office/POS user)

### Business Value
Convert an approved customer estimate into a trackable Work Order (with immutable links and audit trail) so execution can be scheduled/assigned and downstream workflows can proceed without duplicate creation or loss of approval traceability.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to promote an **Approved** Estimate into a Work Order from the POS UI  
- **So that** authorized work is captured as a Work Order with immutable references to the estimate snapshot/version and approval record, and the promotion is auditable and idempotent.

### In-scope
- Frontend UI action to “Promote to Workorder” from an Estimate detail context.
- Calling backend to create the Work Order (atomic) and handling:
  - success response (navigate to Work Order),
  - idempotent “already promoted” response,
  - rejection for non-approved estimate,
  - configuration/validation/system failures.
- Displaying and preserving promotion linkage data surfaced by API (estimateId/version, approval/audit reference, createdBy/createdAt).
- Basic role-based *UI visibility handling if and only if the API already provides role/permission/visibility signals* (no new RBAC policy invented).

### Out-of-scope
- Defining/implementing RBAC policy rules (explicitly out of scope per backend story).
- Changing Work Order state machine beyond creation (start/transition execution).
- Generating supplemental PDFs or customer-facing artifacts.
- Any pricing/security policy not explicitly returned by API.

---

## 3. Actors & Stakeholders
- **Service Advisor (primary)**: initiates promotion, reviews result, navigates to created Work Order.
- **System**: performs atomic promotion creation and returns Work Order reference.
- **Mechanic/Technician (downstream)**: consumes the Work Order later (not part of this UI story).
- **Back Office/Manager (audit)**: needs traceability of who promoted and when.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- An Estimate exists and is viewable by user.
- Estimate is in **APPROVED** state (per backend acceptance criteria).

### Dependencies (must exist or be confirmed)
- Backend endpoint for promotion exists and is reachable from frontend (exact route TBD; see Open Questions).
- Estimate detail API exposes:
  - estimate `id`,
  - `status`,
  - (preferred) `approvedAt`, `approvedBy`, and an approval/audit reference needed for display.
- Work Order detail route/screen exists or will exist to navigate to after success (or implement minimal view route stub).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Estimate Detail screen for an estimate in APPROVED status.

### Screens to create/modify
1. **Modify** `EstimateDetail` screen:
   - Add action button: **Promote to Workorder**
   - Add confirmation dialog (to prevent accidental duplicate clicks).
2. **(Optional minimal)** `WorkOrderDetail` navigation target:
   - If already exists: use it.
   - If not exists: create minimal Work Order summary screen that can show `workOrderId`, `status`, and links back to estimate.

### Navigation context
- From estimate list → estimate detail → promote → redirect to work order detail (or show banner with link).

### User workflows

#### Happy path
1. Service Advisor views an APPROVED estimate.
2. Clicks **Promote to Workorder**.
3. Confirms in modal (shows estimate id and warns idempotent behavior).
4. Frontend calls promotion service.
5. On success, show success toast and navigate to Work Order detail for returned `workOrderId`.

#### Alternate paths
- Estimate not approved: button hidden/disabled; if forced (deep link or stale UI) show error from backend.
- Promotion already done: show info “Workorder already exists” and navigate to existing Work Order.
- Backend config/validation failure: show error, remain on estimate.
- Network/system error: show retry affordance.

---

## 6. Functional Behavior

### Triggers
- User clicks “Promote to Workorder” on Estimate Detail.

### UI actions
- Confirm dialog:
  - Primary: “Create Workorder”
  - Secondary: “Cancel”
- While request in-flight:
  - Disable primary CTA and show loading state.
  - Prevent double-submit.

### State changes (frontend)
- None persisted client-side beyond request state.
- On success: route transition to Work Order detail.

### Service interactions
- Call backend “promote estimate to workorder” operation with:
  - `estimateId`
  - `promotingUserId` (if backend expects; else derived server-side from auth)
  - optional `idempotencyKey` header if supported (preferred; see Open Questions)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Client-side eligibility check (non-authoritative):**
  - Show button only when `estimate.status == APPROVED`.
- **Server-authoritative checks to handle:**
  - Reject if estimate status not APPROVED.
  - Enforce “one workorder per approved estimate” (idempotency).

### Enable/disable rules
- Promote button:
  - Enabled only when estimate is APPROVED and user has required permission signal (if available).
  - Disabled while promotion request in progress.

### Visibility rules
- Do not implement “hide prices for mechanics” unless API already provides a boolean/permission field indicating price visibility; otherwise defer (out-of-scope).

### Error messaging expectations
- Display backend error message when safe; otherwise map to friendly messages:
  - 400/422: “Cannot promote this estimate. Please review estimate status/details.”
  - 403: “You don’t have permission to create workorders.”
  - 404: “Estimate not found.”
  - 409: “Workorder already exists for this estimate.” (and navigate to it if ID provided)
  - 5xx/network: “Unable to create workorder right now. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Estimate` (read)
- `WorkOrder` (created/returned)
- `WorkOrderSnapshot` (not created by frontend, but promotion should surface that it exists)
- Approval/Audit reference (returned by API for display/audit traceability)

### Fields (type, required, defaults)

#### Estimate (read)
- `id` (string|number, required)
- `status` (enum string, required; must include `APPROVED`)
- `shopId` (string|number, required for context if present)
- `approvedAt` (datetime, optional)
- `approvedBy` (string|number, optional)
- `estimateVersion` or `approvedEstimateVersionId` (string|number, **required for linkage display** if available)

#### Promotion response (required for navigation and confirmation)
- `workOrderId` (string|number, required)
- `status` (enum string, required; expected `APPROVED` per backend story)
- `sourceEstimateId` (string|number, required; should equal input estimateId)
- `sourceApprovalEventId` or `approvalId` (string|number, required for traceability)
- `createdByUserId` (string|number, optional but preferred)
- `createdTimestamp` (datetime, optional but preferred)

### Read-only vs editable
- All fields in this story are read-only in UI; creation is performed via promotion action.

### Derived/calculated fields
- None on frontend (no policy calculations allowed).

---

## 9. Service Contracts (Frontend Perspective)

> **Note:** Backend story describes behavior but not the exact endpoint for “promotion”. This is blocking.

### Load/view calls
- `GET /api/estimates/{id}` (assumed existing from approval workflow doc)  
  Used to render estimate detail and eligibility.

### Create/update calls (promotion)
One of the following must exist:

- Preferred: `POST /api/estimates/{id}/promote-to-workorder`
  - Request body: `{ "promotedBy": <userId?> }` (only if required)
  - Response (200/201): `{ workOrderId, status, sourceEstimateId, sourceApprovalEventId, ... }`
  - Response (409 idempotent): `{ workOrderId, message }` or 200 with `alreadyExisted=true`

**OR**
- `POST /api/workorders` with `estimateId` in payload and backend handles promotion semantics.

### Submit/transition calls
- None beyond promotion.

### Error handling expectations
- Must support:
  - 400/422 validation failures (non-approved estimate, missing snapshot, etc.)
  - 403 permission denied
  - 404 estimate not found
  - 409 already promoted OR deterministic idempotent “return existing workorder”
  - 5xx on system/config failures (atomic rollback)

---

## 10. State Model & Transitions

### Allowed states (relevant to this story)
- **Estimate**: must be `APPROVED` to allow promotion (per backend).
- **WorkOrder**: initial status must be `APPROVED` (per backend story and workorder state machine doc).

### Role-based transitions
- Not implemented here; only creation action is performed by Service Advisor.

### UI behavior per state
- If Estimate status != APPROVED: hide/disable promote action; show explanatory tooltip/message “Estimate must be approved to create a workorder.”
- If Estimate status == APPROVED: enable promote action.

---

## 11. Alternate / Error Flows

### Validation failures
- If backend rejects because estimate not approved:
  - Show error and refresh estimate state (re-fetch `GET /api/estimates/{id}`).
- If backend rejects because promotion prerequisites missing (e.g., missing approval snapshot):
  - Show error “Estimate approval data missing; cannot promote.” (exact text from API if provided).

### Concurrency conflicts
- Two advisors click promote simultaneously:
  - One creates; the other receives 409 or “already exists”.
  - UI must handle by navigating to returned existing `workOrderId` if provided; else show “already exists” and offer a “Go to Workorder” button once ID is retrieved by follow-up call (see Open Questions on lookup endpoint).

### Unauthorized access
- On 403:
  - Show “not permitted”
  - Do not retry automatically.

### Empty states
- If estimate detail cannot load (404): show “Estimate not found” and provide link back to estimate list.

---

## 12. Acceptance Criteria

### Scenario 1: Promote approved estimate successfully
**Given** I am a Service Advisor viewing an Estimate with status `APPROVED`  
**When** I click “Promote to Workorder” and confirm  
**Then** the frontend calls the promotion API with the estimate id  
**And** the UI shows a success message containing the new `workOrderId`  
**And** the UI navigates to the Work Order detail view for that `workOrderId`  
**And** the Work Order detail view displays `status = APPROVED` and `sourceEstimateId` matching the original estimate.

### Scenario 2: Prevent promotion UI for non-approved estimate
**Given** I am viewing an Estimate with status `DRAFT` (or any status not `APPROVED`)  
**Then** the “Promote to Workorder” action is not available (hidden) or is disabled with a message  
**And** if I attempt promotion via a direct UI trigger (e.g., stale page)  
**When** the backend responds with a validation error  
**Then** the UI displays “Estimate must be approved before creating a workorder” (or backend-provided equivalent)  
**And** no navigation occurs.

### Scenario 3: Idempotent promotion returns existing workorder
**Given** an approved Estimate has already been promoted and has an existing Work Order `WO-123`  
**When** I click “Promote to Workorder” again  
**Then** the UI does not create a duplicate workorder  
**And** the UI informs me that a workorder already exists  
**And** the UI navigates to Work Order `WO-123` (using the id returned in the response).

### Scenario 4: Handle backend configuration/system error atomically
**Given** I am viewing an approved Estimate  
**When** I attempt to promote it  
**And** the backend responds with a 5xx error indicating a configuration or system failure  
**Then** the UI shows an error message and offers a retry action  
**And** the UI remains on the Estimate detail screen  
**And** a second attempt behaves safely (no duplicate creation; still idempotent).

### Scenario 5: Permission denied
**Given** I am logged in without permission to create workorders  
**When** I attempt to promote an approved Estimate  
**Then** the backend responds with 403  
**And** the UI shows “You don’t have permission to create workorders”  
**And** the UI does not retry automatically.

---

## 13. Audit & Observability

### User-visible audit data
- After promotion success, show in Work Order detail (or a promotion result panel):
  - `createdByUserId` (or name if API provides)
  - `createdTimestamp`
  - `sourceEstimateId`
  - `sourceApprovalEventId` (or approval/audit reference)
  - Snapshot indicator: “Promotion snapshot captured” if API returns snapshot type/id; otherwise omit.

### Status history
- Out of scope to display full transition history (but ensure navigation doesn’t break if such screens exist).

### Traceability expectations
- Frontend must include correlation/request id if the project convention supports it (e.g., pass through header) and log client-side errors without leaking PII.

---

## 14. Non-Functional UI Requirements

- **Performance:** Promotion action should show loading within 100ms; avoid duplicate API calls; fetch estimate once on page load.
- **Accessibility:** Confirm dialog and toasts must be keyboard accessible; focus returns to the promote button on cancel/error.
- **Responsiveness:** Works on tablet sizes (Quasar layout).
- **i18n/timezone:** Display timestamps in user’s local timezone (format per app conventions); do not assume currency formatting since pricing is out-of-scope here.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a clear empty/error state on estimate not found and a “Back to Estimates” action; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows.
- SD-UX-LOADING-DISABLE-SUBMIT: Disable CTA during in-flight request to prevent double-submit; safe ergonomics; impacts Functional Behavior, Error Flows.
- SD-ERR-HTTP-MAPPING: Standard mapping of HTTP 400/403/404/409/5xx to user-friendly messages while preserving backend message when safe; qualifies as standard error-handling; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Promotion API endpoint contract (blocking):** What is the exact backend route and method for “promote estimate to workorder”? (e.g., `POST /api/estimates/{id}/promote-to-workorder` vs `POST /api/workorders` with `estimateId`).
2. **Idempotency response shape (blocking):** On “already promoted”, does backend return `409` with existing `workOrderId`, or `200` with a flag? Frontend needs deterministic behavior to navigate.
3. **Approval/audit linkage fields (blocking):** What exact field(s) will backend expose for `sourceApprovalEventId` / approval record reference so UI can display traceability?
4. **User identity requirement (blocking):** Does promotion require an explicit `userId` in request payload/query, or is it derived from auth context?
5. **Work Order detail route/screen:** What is the canonical Moqui/Vue route for Work Order detail (`/workorders/:id` or similar) in this frontend repo?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Create Workorder from Approved Estimate  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/230  
Labels: frontend, story-implementation, order  

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Create Workorder from Approved Estimate

**Domain**: order

### Story Description

/kiro  
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Back Office

## Trigger
Promotion preconditions are satisfied for an approved estimate.

## Main Flow
1. System creates a Workorder header using customer and vehicle context from the estimate snapshot.
2. System links workorder to estimate version and approval record.
3. System sets workorder initial state (e.g., Ready or Scheduled) per configuration.
4. System applies role-based visibility rules (e.g., hide prices for mechanics).
5. System records the promotion event for audit.

## Alternate / Error Flows
- Workorder creation fails due to validation/config error → rollback and report error.
- Promotion retried after partial failure → idempotent recovery.

## Business Rules
- Workorder must reference estimate snapshot and approval record.
- Initial state is policy-driven.
- Promotion must be auditable.

## Data Requirements
- Entities: Workorder, Estimate, ApprovalRecord, AuditEvent
- Fields: workorderId, status, estimateId, estimateVersion, approvalId, shopId, createdBy, createdDate

## Acceptance Criteria
- [ ] A workorder is created and linked to the approved estimate snapshot.
- [ ] Initial workorder state matches configuration.
- [ ] Audit trail shows who promoted and when.

## Notes for Agents
Keep promotion atomic: either you have a valid workorder, or you have nothing.

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