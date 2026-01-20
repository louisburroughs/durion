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
[FRONTEND] [STORY] Approval: Submit Estimate for Customer Approval

### Primary Persona
Service Advisor

### Business Value
Enables a Service Advisor to formally submit a completed draft estimate for customer consent, producing an immutable approval snapshot and auditable submission record to support downstream approval workflows and compliance.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to submit a draft estimate for customer approval  
- **So that** the estimate is validated, moved into a pending-approval state, and an immutable approval snapshot + approval request payload are created for customer consent processing.

### In Scope
- Frontend action to submit an estimate for customer approval.
- Frontend-driven validation display (field/section-level errors) based on backend response.
- Moqui screen flow for:
  - Loading an estimate
  - Confirming submission
  - Executing submit service
  - Showing resulting status + approval request reference
- Audit visibility requirements (who/when) as read-only UI fields (if returned by backend).

### Out of Scope
- Customer-facing approval experience (signature capture, external links, customer portal).
- Editing estimate line items/taxes/terms (assumed handled in other stories).
- Notification delivery (email/SMS) and token/link distribution.
- Defining estimate completeness policy (requires clarification; see Open Questions).

---

## 3. Actors & Stakeholders

- **Service Advisor (Primary Actor):** initiates submission.
- **Customer (Stakeholder):** recipient of approval request (not interacting in this story).
- **Audit/Compliance (Stakeholder):** requires immutable snapshot and audit trail.
- **Backend workexec services (System):** enforce completeness checks, state transition, snapshot creation, approval payload generation.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS UI.
- Estimate exists and is retrievable by `estimateId`.
- Estimate is currently in a submit-eligible state (intended: `DRAFT`; see State Model section).

### Dependencies
- Backend endpoint/service to submit estimate for approval (not explicitly provided in inputs; required).
- Backend must return:
  - Updated estimate status (e.g., `PENDING_APPROVAL`)
  - Snapshot reference (e.g., `snapshotId` and/or `snapshotVersion`)
  - Approval request reference (e.g., `approvalRequestId`, approval method, consent text if needed)
- Backend completeness validation rules and error schema (required for actionable UI errors).

---

## 5. UX Summary (Moqui-Oriented)

### Entry Points
- From an Estimate detail screen (viewing a single estimate), a primary action button: **“Submit for Approval”** visible only when estimate is submit-eligible.

### Screens to Create/Modify
- **Modify:** `EstimateDetail` screen (or equivalent existing estimate view screen)
  - Add “Submit for Approval” action
  - Add read-only fields/section for approval metadata after submission (status, submittedBy/submittedDate if available, approvalRequestId, snapshotVersion)
- **Create (if not existing):** `EstimateSubmitConfirm` dialog/screen (can be a modal)
  - Shows irreversible action confirmation + key summary fields (estimate id, total, customer, method if known)
  - “Confirm Submit” / “Cancel”

### Navigation Context
- Route pattern (proposed; adjust to repo conventions):  
  - `#/estimates/:estimateId` → Estimate Detail  
  - Submission stays on detail screen and refreshes state after successful submit.

### User Workflows
#### Happy Path
1. Service Advisor opens Estimate Detail (status `DRAFT`).
2. Clicks **Submit for Approval**.
3. Confirm modal opens; user confirms.
4. UI calls submit service; shows loading.
5. On success: status updates to `PENDING_APPROVAL`, submission metadata appears, submit button disabled/hidden.

#### Alternate Paths
- If estimate already pending approval: button hidden/disabled; if attempted via deep link/action, show non-blocking info message and refresh.
- If backend returns completeness errors: remain in `DRAFT`, show actionable errors (grouped).
- If backend returns conflict (e.g., already submitted by another user): refresh estimate and show latest status.

---

## 6. Functional Behavior

### Triggers
- User clicks “Submit for Approval” on an estimate in `DRAFT`.

### UI Actions
- Open confirmation UI requiring explicit user confirmation.
- Execute submit request.
- After response:
  - Refresh estimate data (either from submit response payload or via re-load call).
  - Update UI state (status banner, disable actions, display approval request references).
  - Show toast/inline confirmation message: “Submitted for customer approval.”

### State Changes (Frontend)
- Frontend state is derived from backend estimate status.
- Submit action becomes unavailable when status is not `DRAFT`.

### Service Interactions
- `loadEstimate(estimateId)` on screen entry.
- `submitEstimateForApproval(estimateId, submittedByUserId?)` on confirmation.
- Optional: `loadApprovalRequest(approvalRequestId)` if approval details are not embedded in estimate response.

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Business rules for “completeness” are explicitly *unclear* in provided inputs beyond examples; frontend must rely on backend to be source of truth and return a structured error list.

### Validation
- Frontend must not attempt to replicate completeness logic beyond basic “must have estimateId loaded” gating.
- On submit:
  - If backend rejects due to completeness, UI must display:
    - A summary message: “Estimate is not complete. Fix the items below before submitting.”
    - A list of specific issues as returned (field/section + message).

### Enable/Disable Rules
- “Submit for Approval” visible/enabled only when:
  - Estimate is loaded
  - Estimate status is `DRAFT`
  - User has permission to submit (permission name unknown → Open Question)

### Visibility Rules
- Approval metadata section visible when estimate status is `PENDING_APPROVAL` (or equivalent).
- Snapshot reference visible once created.

### Error Messaging Expectations
- 400 validation: show actionable list; do not log sensitive info (e.g., customer contact) in UI errors.
- 409 conflict: “This estimate was updated or submitted by another user. Reloading latest state.”
- 403 forbidden: “You do not have permission to submit estimates for approval.”
- 404 not found: “Estimate not found.”

---

## 8. Data Requirements

### Entities Involved (Frontend View)
- **Estimate**
- **ApprovalRequest**
- **ApprovalSnapshot** (or `EstimateSnapshot`)
- **AuditEvent** (read-only; if exposed)
  
> Exact entity names in Moqui may differ; map to backend payload fields.

### Fields (Type / Required / Defaults)
#### Estimate (minimum fields needed by UI)
- `estimateId` (string/number, required)
- `status` (enum string, required)
- `customerId` (string/number, required for submission eligibility but enforced by backend)
- `shopId/locationId` (string/number, optional for display)
- `totalAmount` (decimal, optional but recommended for confirmation display)
- `currencyUomId` (string, optional)
- `submittedBy` (string/number, optional; backend-provided)
- `submittedDate` (datetime, optional; backend-provided)
- `approvalMethod` (enum string, optional; backend-provided or derived from configuration)
- `approvalRequestId` (string/number, optional; populated post-submit)
- `snapshotVersion` (integer, optional; populated post-submit)
- `snapshotId` (string/number, optional; populated post-submit)

#### ApprovalRequest (if separately loaded)
- `approvalRequestId` (required)
- `status` (enum, optional)
- `consentText` (string, optional)
- `approvalToken` / `approvalLink` (sensitive; **must not** be displayed unless explicitly required—Open Question)

### Read-only vs Editable
- All approval-related fields are read-only in frontend for this story.
- Estimate becomes non-editable in this flow once `PENDING_APPROVAL` (edit behavior may belong to a different story).

### Derived/Calculated
- None in frontend; totals displayed as returned.

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not fully defined in provided inputs for “submit for approval” for estimates (workexec guide lists approval capture endpoints but not “submit”). This story therefore requires clarification and/or alignment with backend issue #168.

### Load / View Calls
- `GET /api/estimates/{id}`  
  **Response:** Estimate with status and approval-related references if present.

### Submit / Transition Calls (Required)
- **Proposed:** `POST /api/estimates/{id}/submit-for-approval`  
  **Request:** `{ submittedBy: <userId>, reason?: <string> }` (reason optional; not specified)  
  **Response (200/201):** updated estimate + `approvalRequestId` + `snapshotId`/`snapshotVersion`

> If backend instead uses Moqui service name invocation, the frontend Moqui screen transition should call a Moqui service like `workexec.EstimateSubmitForApproval` and return the same fields.

### Error Handling Expectations
- `400 Bad Request`: completeness validation failures with structured list:
  - `errors: [{ code, field, message }]`
- `409 Conflict`: optimistic concurrency / already submitted
- `403 Forbidden`: permission denial
- `404 Not Found`: unknown estimateId
- `500`: show generic error “Could not submit estimate. Try again.”

---

## 10. State Model & Transitions

### Allowed States (Estimate)
Provided authoritative states in `CUSTOMER_APPROVAL_WORKFLOW.md` are: `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`.

However, this frontend story requires a `PendingApproval` state, which conflicts with that document unless “PendingApproval” is an additional internal state not documented there.

### Required Transition (Story)
- `DRAFT` → `PENDING_APPROVAL` (or equivalent) on submit.

### Role-based Transitions
- Service Advisor can initiate submit transition (permission details unknown → Open Question).

### UI Behavior per State
- `DRAFT`: show Submit button.
- `PENDING_APPROVAL`: hide/disable submit, show submission metadata and approval request reference.
- Other states: submit unavailable; show read-only status.

**Conflict Note:** Since estimate states are inconsistent across provided references, implementation must be blocked until canonical state enum is confirmed.

---

## 11. Alternate / Error Flows

### Validation Failures (Completeness)
- Backend returns 400 with list of missing/invalid elements (taxes, items, terms, totals).
- UI displays list; keeps estimate in `DRAFT`; no snapshot created.

### Duplicate Submission
- If estimate already pending approval:
  - Backend returns 409 or 400; UI shows “Already submitted” and refreshes.
  - UI must not create duplicate submission attempts (disable button while request in-flight).

### Concurrency Conflicts
- Another user submits while current user has page open.
- On submit: backend returns 409; UI refreshes estimate and shows updated status.

### Unauthorized Access
- 403: show message and do not alter local state.

### Empty States
- If estimate loads but missing required display fields, UI still renders with placeholders and relies on backend for submission eligibility.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Successful submission from Draft
**Given** I am a Service Advisor viewing an estimate with status `DRAFT`  
**When** I click “Submit for Approval” and confirm submission  
**Then** the system sends a submit request for that estimate  
**And** the estimate status is updated to `PENDING_APPROVAL` (or the configured pending-approval equivalent)  
**And** the UI displays a reference to the created approval snapshot (snapshot id and/or version)  
**And** the UI displays a reference to the created approval request (approvalRequestId)  
**And** the “Submit for Approval” action is no longer available.

### Scenario 2: Submission blocked due to completeness validation
**Given** I am viewing an estimate with status `DRAFT`  
**And** the estimate is incomplete per backend completeness rules  
**When** I attempt to submit for approval  
**Then** the system rejects the submission with validation errors  
**And** the estimate remains in `DRAFT`  
**And** the UI displays an actionable list of validation errors returned by the backend.

### Scenario 3: Prevent re-submitting an already pending approval estimate
**Given** I am viewing an estimate with status `PENDING_APPROVAL` (or equivalent)  
**When** I attempt to submit for approval  
**Then** the UI prevents the action (button hidden/disabled)  
**And** if a request is still made (e.g., via direct call), the UI shows an “Already submitted” message  
**And** the estimate status remains unchanged.

### Scenario 4: Concurrency conflict on submit
**Given** I am viewing an estimate that was `DRAFT` when loaded  
**And** another user submits the estimate for approval before I do  
**When** I submit for approval  
**Then** the backend responds with a conflict (409)  
**And** the UI reloads the estimate  
**And** the UI displays the current estimate status and indicates it was updated elsewhere.

### Scenario 5: Unauthorized user cannot submit
**Given** I am logged in without permission to submit estimates for approval  
**When** I attempt to submit a `DRAFT` estimate for approval  
**Then** the backend responds with 403  
**And** the UI shows an authorization error  
**And** the estimate remains unchanged.

---

## 13. Audit & Observability

### User-visible audit data
- Display (if available from backend):
  - `submittedBy`
  - `submittedDate` (UTC displayed in user’s locale)
  - `snapshotVersion` / `snapshotId`
  - `approvalRequestId`

### Status history
- If an endpoint exists to fetch estimate history, link to it; otherwise out of scope.

### Traceability expectations
- Frontend must include correlation/request ID support per project convention (if already present) and ensure submit action logs include `estimateId` (no PII).

---

## 14. Non-Functional UI Requirements

- **Performance:** Submit action should show immediate loading state; avoid duplicate submissions (disable confirm button while in-flight).
- **Accessibility:** Confirmation dialog must be keyboard-navigable; errors must be announced via ARIA-friendly mechanism provided by Quasar.
- **Responsiveness:** Works on tablet and desktop layouts used in POS.
- **i18n/timezone/currency:** Display money using `currencyUomId` if provided; timestamps rendered in local timezone; do not assume currency formatting rules beyond existing app utilities.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a generic empty/error placeholder when approval metadata is absent; safe because it affects only presentation and not business logic. (Impacted: UX Summary, Error Flows)
- SD-UX-INFLIGHT-GUARD: Disable submit/confirm controls while request is pending to prevent duplicate submissions; safe because it is UI-only idempotency support. (Impacted: Functional Behavior, Error Flows)
- SD-ERR-HTTP-MAP: Map common HTTP status codes (400/403/404/409/500) to user-friendly messages; safe because it does not change domain policy and relies on backend as SoR. (Impacted: Business Rules, Error Flows, Acceptance Criteria)

---

## 16. Open Questions

1. **Canonical Estimate State Model:** What are the official estimate statuses in this system for “pending customer approval”? The provided `CUSTOMER_APPROVAL_WORKFLOW.md` lists only `DRAFT/APPROVED/DECLINED/EXPIRED`, but this story requires `PENDING_APPROVAL`. What is the correct enum value and transition?
2. **Backend Submit Endpoint / Moqui Service Contract:** What is the exact API endpoint (or Moqui service name) to “submit estimate for approval”, and what request/response schema should the frontend use?
3. **Completeness Checklist:** What are the exact completeness requirements enforced at submission time (required fields/items/taxes/totals/terms)? Provide the backend error codes/fields so the frontend can render targeted messages.
4. **Permissions/RBAC:** What permission(s) gate the “Submit for Approval” action (role name or permission ID)? Should the UI hide the button or show it disabled with an explanation?
5. **Approval Method & Consent Text Display:** Should the Service Advisor see the selected approval method and consent text at submission time? If yes, should consent text be previewed in the confirm dialog?
6. **Approval Token/Link Sensitivity:** Will the backend return an approval token/link in the submission response? If yes, is the Service Advisor allowed to view/copy it in POS, or must it remain hidden for security?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Approval: Submit Estimate for Customer Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/233  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Approval: Submit Estimate for Customer Approval

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
A Draft estimate is complete and ready for customer consent.

## Main Flow
1. User selects 'Submit for Approval'.
2. System validates estimate completeness (required fields, items, taxes, totals, terms).
3. System transitions estimate to PendingApproval and freezes an approval snapshot.
4. System generates an approval request payload (method, link/token, consent text).
5. System logs the submission event for audit.

## Alternate / Error Flows
- Validation fails (missing taxes, missing items) → block and show actionable errors.
- Estimate already pending approval → prevent duplicate submissions and show status.

## Business Rules
- Only submit-ready estimates may enter PendingApproval.
- Submission creates an immutable approval snapshot version.
- Submission must be auditable (who/when).

## Data Requirements
- Entities: Estimate, ApprovalRequest, ApprovalSnapshot, AuditEvent
- Fields: status, approvalRequestId, snapshotVersion, consentText, submittedBy, submittedDate, approvalMethod

## Acceptance Criteria
- [ ] System blocks submission when required completeness checks fail.
- [ ] PendingApproval state is set and visible.
- [ ] An approval snapshot is created and referenced by the request.

## Notes for Agents
Approval snapshot must remain immutable; later revisions require resubmission.

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