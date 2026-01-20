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
[FRONTEND] [STORY] Approval: Capture In-Person Customer Approval (Estimate)

### Primary Persona
Service Advisor

### Business Value
Provide a fast, auditable in-POS flow to record a customer’s in-person approval on an estimate so authorized work can proceed, with a clear timestamped trail and correct enforcement of estimate state rules.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** to capture a customer’s in-person approval on an estimate directly in the POS,  
**so that** the estimate is transitioned to an approved state with an immutable approval record and downstream work order creation can proceed.

### In-scope
- Viewing an estimate and its current approval configuration (method) as context for capture.
- Capturing **in-person** approval using the configured method (at minimum `CLICK_CONFIRM`).
- Optional entry of approval notes (if supported by backend; see Open Questions).
- Handling invalid state, authorization failures, and concurrency conflicts during approval.
- Showing updated estimate status and relevant audit metadata post-approval.

### Out-of-scope
- Electronic signature flows (email/SMS) and delivery of notifications.
- Signature capture UX (drawing pad) unless backend supports it explicitly.
- Full decline workflow and decline reason capture (separate story).
- Work order creation from estimate (separate story).
- Approval configuration management UI (separate story).

---

## 3. Actors & Stakeholders
- **Service Advisor (Primary)**: initiates capture and confirms approval.
- **Customer (External)**: provides consent in person (not a system user).
- **Technician/Mechanic (Stakeholder)**: relies on estimate being approved to begin work order execution.
- **Billing/Audit (Stakeholder)**: requires traceable approval record.

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- User has permission to view the estimate and approve it (exact permission keys TBD; see Open Questions).
- Backend endpoints exist and are reachable:
  - `GET /api/estimates/{id}`
  - `POST /api/estimates/{id}/approve?approvedBy={userId}`
  - plus (optional/context) `GET /api/approval-configurations/applicable?locationId=&customerId=`
- Estimate exists and is in a start-eligible approval state:
  - Must be `DRAFT` per backend `approveEstimate()` contract in `CUSTOMER_APPROVAL_WORKFLOW.md`.
- Dependency: identity of logged-in user (userId) must be available to frontend.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Estimate detail screen: primary action button **“Capture In-Person Approval”**.
- (Optional) From Estimate list row action menu: **“Approve (In Person)”** that navigates to detail and opens confirmation dialog.

### Screens to create/modify
- **Modify**: `EstimateDetail` screen (or equivalent in repo) to include:
  - Current estimate status display.
  - Approval method display (derived from applicable configuration).
  - Action control to initiate approval.
  - A confirmation dialog/modal screenlet for approval confirmation and optional notes.
- **No new standalone screens required**; use dialog + transition.

### Navigation context
- Route: `/estimates/:estimateId` (exact path TBD to match project routing conventions; see Open Questions).
- After successful approval: remain on Estimate detail; refresh header/status panel.

### User workflows
**Happy path**
1. Service Advisor opens estimate detail.
2. Clicks “Capture In-Person Approval”.
3. Sees confirmation dialog summarizing: estimate id, customer, total (if available on estimate), approval method.
4. Confirms.
5. UI shows success toast/banner; status updates to `APPROVED`; approval timestamp and approved-by fields appear.

**Alternate paths**
- If estimate not in `DRAFT`: approval action hidden/disabled with a reason message.
- If backend returns 409 conflict: show “Estimate was updated elsewhere; refresh” with reload action.
- If unauthorized (401/403): show access error and prevent action.
- If backend unavailable: show retryable error.

---

## 6. Functional Behavior

### Triggers
- User clicks “Capture In-Person Approval” on estimate detail.
- User confirms in modal/dialog.

### UI actions
- On click:
  - Validate locally that estimate status == `DRAFT` (based on loaded estimate).
  - Open confirmation dialog.
- On confirm:
  - Disable confirm button and show loading state.
  - Call backend approve endpoint with `approvedBy=<loggedInUserId>`.
  - On success: refresh estimate detail via GET and update UI.

### State changes (frontend-visible)
- Estimate status changes from `DRAFT` → `APPROVED` in UI.
- Display of approval metadata becomes visible (approvedAt, approvedBy) if present in response.

### Service interactions
- Load estimate: `GET /api/estimates/{id}` on screen load and after successful approval.
- Optional config context:
  - If estimate response includes `approvalConfigurationId` and/or `approvalMethod`, display directly.
  - If not included, call applicable config endpoint using `shopId/locationId` and `customerId` from estimate (see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Approval may only be attempted when estimate status is `DRAFT`.
  - UI must disable/hide the approval action when status != `DRAFT`.
- Confirm step is required (at least click-to-confirm).

### Enable/disable rules
- “Capture In-Person Approval” button enabled only if:
  - Estimate loaded successfully
  - Status == `DRAFT`
  - User has approve permission (if frontend can infer; otherwise rely on backend 403 and show message)

### Visibility rules
- Show approval panel (approvedAt/approvedBy) only when status is `APPROVED` (or when fields exist).
- Show configuration/method label when available (e.g., `CLICK_CONFIRM`, `SIGNATURE`, etc.).

### Error messaging expectations
- Invalid state (400/409 depending on backend):  
  “This estimate cannot be approved in its current state.”
- Already approved:  
  “This estimate is already approved.”
- Concurrency conflict (409):  
  “This estimate was updated by someone else. Refresh to continue.”
- Forbidden (403):  
  “You don’t have permission to approve estimates.”
- Not found (404):  
  “Estimate not found.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **Estimate**
  - `id: number` (required)
  - `status: enum(DRAFT, APPROVED, DECLINED, EXPIRED)` (required)
  - `shopId: number` (required for config lookup)
  - `customerId: number` (required for config lookup)
  - `approvalConfigurationId: number|null` (optional)
  - `approvedAt: datetime|null` (optional)
  - `approvedBy: number|null` (optional)
  - `declinedAt, expiresAt, declineReason` (out of scope but may appear)
- **ApprovalConfiguration** (read-only context)
  - `id: number`
  - `approvalMethod: enum(CLICK_CONFIRM, SIGNATURE, ELECTRONIC_SIGNATURE, VERBAL_CONFIRMATION)`
  - `declineExpiryDays: number`
  - `requireSignature: boolean`
  - `locationId: number|null`
  - `customerId: number|null`

### Fields: required/defaults
- No new persistent frontend fields.
- Confirmation dialog requires no mandatory input besides user confirmation.

### Read-only vs editable
- Estimate status and approval metadata: read-only.
- Any “approval notes” field: **blocked** until backend contract confirmed.

### Derived/calculated fields
- `canApprove = (estimate.status == DRAFT)`
- `effectiveApprovalMethod`:
  - Prefer estimate-provided method/config id if present
  - Else resolved via applicable config call (if implemented)

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
1. `GET /api/estimates/{id}`
   - Expected: 200 + Estimate JSON; includes at least `id`, `status`, `shopId`, `customerId`.
   - Error: 404 if not found; 401/403 if unauthorized.

2. (Optional) `GET /api/approval-configurations/applicable?locationId={shopId or locationId}&customerId={customerId}`
   - Expected: 200 + config JSON.
   - Error: 404/200-null behavior unknown (see Open Questions).

### Submit/transition calls
1. `POST /api/estimates/{id}/approve?approvedBy={userId}`
   - Expected: 200 OK (or 201) returning updated Estimate, or minimal ack (if minimal, frontend must re-GET).
   - Validation error: 400 if status invalid.
   - Conflict: 409 if already approved or concurrent modification (backend-specific).
   - Forbidden: 403.

### Error handling expectations
- Map HTTP codes to user messages (see section 7).
- Always surface correlation/request id if response includes it (safe default: show in “details” expandable area only).

---

## 10. State Model & Transitions

### Allowed states (Estimate)
- `DRAFT`
- `APPROVED`
- `DECLINED`
- `EXPIRED`

### Transitions relevant to this story
- `DRAFT` → `APPROVED` via approve action.
- All other transitions are not performed by this story.

### Role-based transitions
- Service Advisor can perform approval (subject to RBAC).
- Other roles: read-only unless authorized.

### UI behavior per state
- `DRAFT`: show approval action enabled.
- `APPROVED`: hide/disable approval action; show approval metadata.
- `DECLINED`/`EXPIRED`: hide/disable approval action; show status; no reopen in this story.

---

## 11. Alternate / Error Flows

### Validation failures
- User opens dialog but estimate has changed state since load:
  - On submit, backend returns 400/409 → show error; refresh estimate; close dialog or keep open with disabled confirm.

### Concurrency conflicts
- If backend returns 409:
  - Show conflict message with “Refresh” button; trigger reload GET.
  - Do not retry automatically.

### Unauthorized access
- If 401:
  - Redirect to login (per project pattern) and preserve return URL.
- If 403:
  - Show permission error; keep user on detail screen.

### Empty states
- Estimate detail load returns 404:
  - Show “Estimate not found” empty state with link back to estimate list.

---

## 12. Acceptance Criteria

### Scenario 1: Approve a Draft estimate in person (click confirm)
**Given** I am logged in as a Service Advisor  
**And** an Estimate exists with status `DRAFT`  
**When** I open the Estimate detail screen  
**And** I click “Capture In-Person Approval”  
**And** I confirm the approval in the confirmation dialog  
**Then** the frontend calls `POST /api/estimates/{id}/approve?approvedBy={myUserId}`  
**And** the Estimate status displayed updates to `APPROVED` after success  
**And** the approval action is no longer available on the screen

### Scenario 2: Prevent approval when estimate is not Draft
**Given** I am viewing an Estimate with status `APPROVED`  
**When** the Estimate detail screen renders  
**Then** the “Capture In-Person Approval” action is disabled or hidden  
**And** if I attempt to call approval (e.g., via stale UI), the frontend displays “This estimate is already approved.” (based on backend response)  
**And** the displayed status remains `APPROVED`

### Scenario 3: Backend rejects approval due to invalid state
**Given** I am viewing an Estimate with status `DECLINED`  
**When** I attempt to approve it  
**Then** the backend responds with an error (400/409)  
**And** the frontend displays “This estimate cannot be approved in its current state.”  
**And** no status change is shown

### Scenario 4: Concurrency conflict during approval
**Given** an Estimate is `DRAFT` when I load it  
**And** another user approves or modifies it before I confirm  
**When** I confirm in-person approval  
**Then** the backend responds `409 Conflict`  
**And** the frontend informs me the estimate was updated elsewhere  
**And** provides a “Refresh” action that reloads the estimate detail

### Scenario 5: Unauthorized approval attempt
**Given** I am logged in without permission to approve estimates  
**And** I view a `DRAFT` estimate  
**When** I attempt to confirm approval  
**Then** the backend responds `403 Forbidden`  
**And** the frontend displays “You don’t have permission to approve estimates.”  
**And** the estimate remains `DRAFT` in the UI after refresh

---

## 13. Audit & Observability

### User-visible audit data
- Show (if present in estimate payload):
  - `approvedAt` (UTC rendered in user locale)
  - `approvedBy` (user display name if resolvable; else show id)

### Status history
- Not required to implement full transition history UI in this story.
- If an existing “history” screenlet exists, ensure approval transition appears after refresh.

### Traceability expectations
- Frontend logs (console/dev) should include:
  - estimateId
  - action “approveEstimateInPerson”
  - correlation id if returned by API headers/body (do not log PII)

---

## 14. Non-Functional UI Requirements

- **Performance**: initial estimate load < 2s on typical shop network; approval submit should show immediate loading feedback (<100ms).
- **Accessibility**: confirmation dialog must be keyboard-navigable; focus trapped within modal; buttons have accessible labels.
- **Responsiveness**: usable on tablet resolution used at service desk.
- **i18n/timezone**: display timestamps in user locale; store/submit timestamps are backend-owned UTC.
- **Currency**: if total shown, use existing formatting utilities; do not compute totals in frontend.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standardized empty/not-found state with link back to list; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows.
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/401/403/404/409/5xx to user-friendly messages without exposing internals; qualifies as safe error-handling; impacts Business Rules, Error Flows, Acceptance Criteria.
- SD-LOAD-SPINNER: Show loading indicator and disable submit during POST to prevent double-submit; qualifies as safe UX ergonomics; impacts Functional Behavior, Error Flows.

---

## 16. Open Questions

1. **Entity ambiguity (Estimate vs Work Order):** The backend reference for approval is **Estimate** (`POST /api/estimates/{id}/approve?approvedBy=`). The frontend issue title says “Work Order” in places. Should this frontend story implement **Estimate approval only**, or also Work Order approval? (Blocking: domain/contract)
2. **Approval configuration display:** Does `GET /api/estimates/{id}` include the effective `approvalMethod` (or `approvalConfigurationId`) needed to display method context? If not, should frontend call `GET /api/approval-configurations/applicable` and what is the exact parameter name: `locationId` vs `shopId`?
3. **Approval notes:** Is there a supported backend field for “approvalNotes” on estimate approval capture? If yes, what endpoint and payload/params support it? (Backend contract currently uses query param only.)
4. **RBAC/permissions:** What permission(s) or roles should gate visibility/enabled state of the approve action in the frontend (e.g., `ESTIMATE_APPROVE`)? Or must frontend rely solely on backend 403?
5. **Routing/screen names:** What are the canonical Moqui screen paths for estimate list/detail in this repo (e.g., `/apps/pos/estimate/Detail`)? Provide the expected route so the story can specify exact navigation metadata.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Approval: Capture In-Person Customer Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/270  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #21 - Approval: Capture In-Person Customer Approval  
**URL**: https://github.com/louisburroughs/durion/issues/21  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:49.112725374*