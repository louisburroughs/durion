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
[FRONTEND] [STORY] Promotion: Validate Promotion Preconditions (Estimate → Work Order)

### Primary Persona
Service Advisor (Back Office)

### Business Value
Prevents creation of invalid or duplicate work orders by enforcing estimate approval + snapshot integrity checks at the moment of promotion, reducing rework, billing errors, and operational confusion.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** the POS to validate all required preconditions when I click “Promote to Work Order” on an estimate,  
**so that** only estimates with current valid approval and approved scope tied to the correct snapshot can be promoted, and retries do not create duplicate work orders.

### In-scope
- Frontend “Promote to Work Order” action initiation from an Estimate context.
- UI-side orchestration to call backend validation/promotion endpoint(s).
- Displaying actionable, specific precondition failure messages (expired/missing/invalid approval; missing/incorrect approved scope; duplicate promotion).
- Idempotent retry handling (duplicate promotion returns existing work order reference).
- Audit/observability hooks from frontend (correlation ID, client-side event logging as applicable).

### Out-of-scope
- Actual creation/editing of work order details beyond routing to the created/existing work order screen (work order creation behavior is “defined in a subsequent story” per backend reference).
- Defining or changing estimate approval workflow itself.
- Defining permissions/roles (only enforce what backend returns).
- Generating PDFs / supplemental estimate documents.

---

## 3. Actors & Stakeholders
- **Primary actor:** Service Advisor / Back Office user
- **Secondary stakeholders:** Workshop Manager (expects integrity), Customer (expects only approved work), Audit/Compliance (expects traceability)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS UI.
- User is viewing an Estimate record with an `estimateId`.
- Estimate has a concept of `snapshotVersion` (the version that was approved / being promoted).

### Dependencies
- Backend endpoints for:
  - retrieving estimate details (including current status and snapshot/version context),
  - validating/promotion attempt that returns either success (allowed) or structured errors including duplicate reference.
- Backend must return structured error codes at minimum:
  - `APPROVAL_NOT_FOUND`
  - `APPROVAL_EXPIRED`
  - `APPROVAL_INVALID`
  - `PROMOTION_ALREADY_EXISTS` (with `workorderId`)
  - plus any “approved scope missing/mismatch” code (not explicitly named in provided inputs; see Open Questions).
- Moqui screens framework available for:
  - estimate view screen
  - work order view screen (existing or to be created if missing)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Estimate detail screen: action button **Promote to Work Order**.

### Screens to create/modify
- **Modify**: `EstimateDetail` screen (name TBD by repo conventions) to add:
  - action button
  - confirmation dialog (optional; see safe defaults)
  - promotion result handling (navigate or show error panel)
- **Existing/Target**: `WorkOrderDetail` screen route (must be linkable via `workorderId`)

### Navigation context
- From Estimate detail → promote action → either:
  - navigate to Work Order detail (new or existing)
  - stay on estimate and show blocking reasons + next steps

### User workflows
**Happy path**
1. Service Advisor opens an estimate.
2. Clicks “Promote to Work Order”.
3. UI calls backend with `estimateId` and `snapshotVersion`.
4. Backend confirms preconditions satisfied and returns a `workorderId` (or an “allowed” response that includes navigation target).
5. UI navigates to Work Order detail for returned `workorderId`.

**Alternate paths**
- Approval expired/missing/invalid → UI blocks, shows explicit next steps (e.g., “Obtain new approval”).
- Approved scope missing or snapshot mismatch → UI blocks, instructs to revise/resubmit approval.
- Duplicate promotion → UI informs user and offers “Open existing Work Order” action (default action navigates).

---

## 6. Functional Behavior

### Triggers
- User clicks `Promote to Work Order` on an estimate.

### UI actions
- Disable button and show progress while request is in flight.
- On response:
  - Success: show brief confirmation and route to work order screen.
  - Duplicate: show “Already promoted” banner and route to existing work order on user confirm (or immediate route; see safe defaults).
  - Validation error: show inline blocking error panel with actionable instructions.
  - Auth error: show “Not authorized” and keep user on estimate.

### State changes (frontend)
- No domain state mutation on frontend; all authoritative validation occurs server-side.
- Frontend stores transient UI state:
  - `promotionRequestStatus` = idle | loading | success | error
  - `promotionError` object with `errorCode`, `message`, `details` (if provided)

### Service interactions
- Call a single backend operation that:
  - performs idempotency check
  - validates approval validity + expiry + superseded
  - validates approved scope exists and matches snapshotVersion
  - either returns created/existing workorder reference, or returns structured failure

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI-facing)
- If backend indicates approval is not current/valid:
  - show blocking message and do not navigate.
- If backend indicates `PROMOTION_ALREADY_EXISTS`:
  - treat as non-fatal outcome; present existing `workorderId` and allow navigation.
- If backend indicates approved scope missing/mismatch:
  - block promotion and instruct user to ensure the approved scope matches the version being promoted.

### Enable/disable rules
- Promote button is disabled while request is in progress.
- Promote button visibility:
  - **Do not enforce estimate status rules purely client-side** (status ownership is domain/back-end); however, UI may hide/disable if estimate clearly not promotable *and* estimate status is available (see Open Questions on which statuses are promotable).

### Visibility rules
- Error panel visible only after a failed attempt.
- If duplicate promotion detected, show an “Open Work Order” call-to-action.

### Error messaging expectations
Messages must:
- include a short reason
- include “what to do next”
- avoid exposing internal stack traces or sensitive details

Example mappings (UI copy suggestions; final copy can be adjusted):
- `APPROVAL_NOT_FOUND`: “No customer approval is recorded for this estimate. Capture approval before promoting.”
- `APPROVAL_EXPIRED`: “The approval has expired. Request a new approval to continue.”
- `APPROVAL_INVALID`: “The approval is no longer valid (revoked/superseded). Review approvals and capture a new one.”
- `PROMOTION_ALREADY_EXISTS`: “This estimate version was already promoted. Open the existing work order: {workorderId}.”

---

## 8. Data Requirements

### Entities involved (frontend concerns)
- `Estimate`
- `ApprovalRecord` (read-only display context if surfaced)
- `ApprovedScope` (read-only validation context)
- `WorkOrder` (navigation target)
- `AuditEvent` (frontend emission/correlation only; not authoritative)

### Fields (type, required, defaults)
**From Estimate context (required to attempt promotion)**
- `estimateId` (string/number; required)
- `snapshotVersion` (string/number; required)

**From promotion response (required to route on success/duplicate)**
- `workorderId` (string/number; required when success or duplicate)

**From error response**
- `errorCode` (string enum; required)
- `message` (string; optional if UI maps by code)
- `details` (object; optional, e.g., `expiresAt`, `snapshotVersionExpected`, `snapshotVersionProvided`)

### Read-only vs editable
- All fields are read-only in this story; user does not edit data here.

### Derived/calculated fields
- `isPromoteEnabled` derived from UI state (not domain state).
- “Approved scope exists” is derived from backend result, not computed in UI.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend reference story describes behavior but does not provide an explicit “promotion validation” endpoint. This is blocking for exact implementation wiring.

### Load/view calls
- `GET /api/estimates/{id}` (or Moqui equivalent) to display estimate and obtain `snapshotVersion` context.

### Create/update calls (promotion attempt)
One of the following patterns must exist; confirm actual contract:

**Option A (single promote endpoint that validates + creates/returns work order)**
- `POST /api/estimates/{estimateId}/promote`
  - Body: `{ "snapshotVersion": "v3" }` (or query param)
  - Success: `201 Created` (new work order) or `200 OK` with `{ workorderId }`
  - Duplicate: `409 Conflict` with `{ errorCode: "PROMOTION_ALREADY_EXISTS", workorderId }`
  - Validation failures: `422` with `{ errorCode: "...", message, details }`

**Option B (validate-only endpoint used before separate create)**
- `POST /api/estimates/{estimateId}/promotion-preconditions:validate`
  - Response: `{ allowed: true }` or `{ allowed:false, errorCode... }`
- Followed by separate promote/create endpoint.

### Submit/transition calls
- N/A in Moqui “transition” sense unless backend exposes a transition-style service; if so, implement as Moqui screen transition calling service.

### Error handling expectations
- Map HTTP codes:
  - `401/403`: show not-authorized message; do not navigate.
  - `404`: show not-found (estimate missing).
  - `409`: duplicate promotion; show existing work order reference; offer navigation.
  - `422`: show specific precondition failure per `errorCode`.
  - `5xx`: show generic failure with retry option.

- Must display actionable messages for all 4 known error codes listed in provided inputs/reference.

---

## 10. State Model & Transitions

### Allowed states (domain awareness)
This story concerns Estimate promotion eligibility, but exact promotable estimate statuses are not fully specified in provided inputs (backend reference suggests “Approved”). From `CUSTOMER_APPROVAL_WORKFLOW.md`, Estimate statuses include:
- `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`

### Role-based transitions
- This story does not implement new state transitions in Moqui; it initiates backend validation/promotion action.
- Backend enforces permissions; frontend must gracefully handle 403.

### UI behavior per state (estimate)
- If estimate is `APPROVED`: show Promote CTA prominently.
- If estimate is not `APPROVED`: CTA may be disabled with explanation **only if** business confirms (see Open Questions). Otherwise rely on backend to block with actionable error.

---

## 11. Alternate / Error Flows

### Validation failures
- Approval missing (`APPROVAL_NOT_FOUND`) → show message + link/button “Go to Approvals” (if estimate screen has an approvals section; otherwise just message).
- Approval expired (`APPROVAL_EXPIRED`) → show expiry detail if provided; instruct “Resubmit approval”.
- Approval invalid (`APPROVAL_INVALID`) → instruct review approvals; possibly indicate “superseded” vs “revoked” if backend provides subreason.
- Approved scope missing/mismatch → show message and block (exact error code TBD).

### Concurrency conflicts
- Two users attempt promotion simultaneously:
  - One succeeds; the other receives `409 PROMOTION_ALREADY_EXISTS` with `workorderId`.
  - UI must treat as safe: offer navigation to the returned work order.

### Unauthorized access
- If backend returns 403, show “You don’t have permission to promote estimates to work orders.” Do not reveal additional detail.

### Empty states
- If estimate detail fails to load / missing `snapshotVersion`, disable Promote and show “Cannot promote: estimate version information not available.” (This indicates a data contract issue; should be rare.)

---

## 12. Acceptance Criteria

### Scenario 1: Promote succeeds with valid approval and scope
**Given** I am authenticated as a Service Advisor  
**And** I am viewing an estimate with `estimateId=E-100` and `snapshotVersion=v3`  
**And** the backend determines there is a current valid, non-expired approval for `snapshotVersion=v3` with approved scope  
**When** I click “Promote to Work Order”  
**Then** the UI sends a promotion request including `estimateId` and `snapshotVersion=v3`  
**And** the UI receives a success response containing `workorderId`  
**And** the UI navigates to the Work Order detail screen for that `workorderId`.

### Scenario 2: Block promotion when approval is expired
**Given** I am viewing an estimate `E-101`  
**And** the backend determines the approval is expired  
**When** I click “Promote to Work Order”  
**Then** the UI must not navigate away from the estimate  
**And** the UI must show a blocking error mapped to `errorCode=APPROVAL_EXPIRED`  
**And** the UI must instruct me to obtain/resubmit a new approval.

### Scenario 3: Block promotion when approval is missing
**Given** I am viewing an estimate `E-102`  
**And** the backend determines no approval exists  
**When** I click “Promote to Work Order”  
**Then** the UI shows a blocking error mapped to `errorCode=APPROVAL_NOT_FOUND`  
**And** the UI provides guidance to capture customer approval before retrying.

### Scenario 4: Block promotion when approval is invalid (revoked/superseded)
**Given** I am viewing an estimate `E-103`  
**And** the backend determines the most recent approval is invalid  
**When** I click “Promote to Work Order”  
**Then** the UI shows a blocking error mapped to `errorCode=APPROVAL_INVALID`  
**And** the UI instructs me to review approvals and capture a new valid approval.

### Scenario 5: Idempotent retry returns existing work order
**Given** estimate `E-104` with `snapshotVersion=v2` has already been promoted  
**And** the backend will return `409` with `{ errorCode: "PROMOTION_ALREADY_EXISTS", workorderId: "WO-123" }`  
**When** I click “Promote to Work Order” again  
**Then** the UI must not display a generic failure  
**And** the UI must display “Already promoted” with the returned `workorderId`  
**And** the UI provides an action to open the existing work order  
**And** when I choose to open it, the UI navigates to `WO-123`.

### Scenario 6: Network/server error shows retryable message
**Given** I am viewing an approved estimate  
**When** I click “Promote to Work Order” and the backend returns a `5xx` or the request times out  
**Then** the UI shows a non-sensitive error message indicating the promotion could not be validated/completed  
**And** the UI offers a retry action  
**And** the Promote button becomes enabled again after the error is shown.

---

## 13. Audit & Observability

### User-visible audit data
- Display is optional; not required by provided inputs. (Do not invent audit UI.)

### Status history
- Not in scope.

### Traceability expectations
- Frontend must include a correlation ID on the promotion request (header) if project conventions support it.
- Frontend should log a client-side event (console/telemetry if available in repo conventions) on:
  - promotion attempt
  - promotion success
  - promotion blocked with `errorCode`
  - duplicate detected (with workorderId)

---

## 14. Non-Functional UI Requirements

- **Performance:** Promotion action should provide immediate feedback (loading state within 100ms of click). No repeated polling.
- **Accessibility:** Button and error panel must be keyboard reachable; error messages announced to screen readers (aria-live region).
- **Responsiveness:** Works on tablet and desktop widths used in POS.
- **i18n/timezone/currency:** Not applicable beyond displaying `expiresAt` if present; if displayed, render in store/user timezone per existing app convention (needs confirmation).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-01: Provide a standard inline error panel with “Retry” action for transient failures; qualifies as safe because it does not change domain logic and only improves operator recovery. Impacted sections: UX Summary, Alternate/Error Flows, Acceptance Criteria.
- SD-UX-LOAD-01: Disable the Promote button while the request is in flight to prevent double-submission; qualifies as safe because it is purely client-side ergonomics and backend remains idempotent. Impacted sections: Functional Behavior, Business Rules, Acceptance Criteria.

---

## 16. Open Questions

1. **Backend endpoint contract (blocking):** What is the exact Moqui-accessible endpoint/service for “promote estimate to work order” (URL, method, request payload, response payload, HTTP codes)? Does it both validate and create/return a workorderId, or validate-only?
2. **Approved scope error code (blocking):** What `errorCode` is returned when approved scope is missing or `snapshotVersion` does not match the approved scope/approval record? (The story requires this check but no explicit code/name is provided.)
3. **Estimate snapshotVersion source (blocking):** Where does the frontend get the `snapshotVersion` to promote (field name on Estimate DTO, estimate version entity, or approval record reference)?
4. **Navigation route (blocking):** What is the canonical frontend route/screen path to open a work order by `workorderId` in this Moqui frontend (screen name + parameters)?
5. **Client correlation header (non-blocking if convention exists):** What header/key should be used for correlation ID propagation in this frontend (if standardized in repo)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Validate Promotion Preconditions  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/231  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Validate Promotion Preconditions

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
Service Advisor / Back Office

## Trigger
User attempts to promote an estimate to a workorder.

## Main Flow
1. User selects 'Promote to Workorder' on an estimate.
2. System verifies the estimate has a current valid approval (not expired, not superseded).
3. System verifies approved scope exists (full or partial) and references the correct snapshot version.
4. System checks that promotion has not already occurred for this estimate/snapshot (idempotency).
5. System either allows promotion or returns actionable errors.

## Alternate / Error Flows
- Approval missing/expired → block and instruct resubmission.
- Promotion already performed → return existing workorder reference.

## Business Rules
- Only current valid approvals can be promoted.
- Promotion must be idempotent.
- Approved scope governs what becomes executable work.

## Data Requirements
- Entities: Estimate, ApprovalRecord, ApprovedScope, Workorder, AuditEvent
- Fields: estimateId, snapshotVersion, approvalStatus, expiresAt, promotionRef, workorderId

## Acceptance Criteria
- [ ] System blocks promotion if approval is invalid or expired.
- [ ] System detects and handles re-tries without duplicates.
- [ ] System reports specific failed preconditions.

## Notes for Agents
Make precondition failures actionable—this saves operator time.

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