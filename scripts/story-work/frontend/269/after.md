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
[FRONTEND] [STORY] Approval: Record Partial Approval (Work Order)

### Primary Persona
Service Advisor

### Business Value
Enable a Service Advisor to record customer approval/decline decisions per line item so authorized work can proceed while declined work remains clearly tracked, auditable, and excluded from execution/billing.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to record partial approval for a Work Order by approving some line items and declining others (including capturing approval method/proof)  
- **So that** technicians only execute authorized work, totals reflect only approved items, and the system maintains a compliant audit trail.

### In-Scope
- A Work Order “Record Approval” UI that:
  - Loads work order + line items requiring approval
  - Allows per-line approval decisions
  - Enforces approval-window rules for re-approving previously declined items (if supported by backend)
  - Captures approval method according to configuration precedence (customer > location > default)
  - Submits approval decisions in a single atomic confirmation action
  - Displays resulting Work Order status and approved total
- Read-only display of relevant audit/approval metadata after submission (method, timestamp, proof reference if any)

### Out-of-Scope
- Creating or editing estimates/prices/line items themselves
- Customer communication (SMS/email collection or sending links)
- Scheduling “amendment workflow” UI (only trigger/hand off if backend provides it)
- Signature capture UX implementation details beyond basic integration hook (depends on backend contract)
- Any new workflow states not defined by authoritative workexec state machine documents

---

## 3. Actors & Stakeholders
- **Service Advisor (primary)**: Records customer decisions and approval artifact.
- **Customer**: Provides approval/decline decisions.
- **Technician/Mechanic**: Consumes outcomes to know what is authorized.
- **Manager/Auditor**: Reviews approval history and proof.
- **System (POS)**: Validates state, persists immutable approval records, emits audit events.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- Work Order exists and is accessible to the user.
- Work Order is in a state that permits recording approvals (see Open Questions; backend reference uses `AwaitingApproval`).

### Dependencies (Blocking if missing)
- Backend endpoints to:
  - Load Work Order details including line items and current approval statuses
  - Load applicable approval configuration (or include it in Work Order payload)
  - Submit partial approval decisions atomically with method/proof
  - Enforce approval window and return a deterministic error when expired
- Backend-defined enums/status values for:
  - Work Order status allowing approval entry
  - Line item approval statuses
  - Approval method identifiers

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: action button **“Record Approval”**
- Deep link route: `/workorders/{workOrderId}/approval/record` (screen path suggestion; exact route must match repo routing conventions)

### Screens to create/modify
1. **Modify** Work Order Detail screen to add conditional action:
   - Show “Record Approval” only when work order status is eligible (e.g., `AWAITING_APPROVAL`).
2. **Create** Work Order Partial Approval screen:
   - Loads work order header + list of approval-eligible line items
   - Provides per-line selection: Approved / Declined
   - Shows computed “Approved Total” preview
   - Shows approval method UI section determined by config
   - “Confirm Approval” and “Cancel” actions

### Navigation context
- Breadcrumb/back returns to Work Order detail.
- After successful confirmation: redirect to Work Order detail (or a read-only approval summary subsection) with a success message.

### User workflows
#### Happy path: approve some, decline some
1. Advisor opens Record Approval screen.
2. System loads work order + line items.
3. Advisor selects Approved/Declined per line.
4. Advisor provides required method proof (if signature).
5. Advisor confirms.
6. System saves, returns updated work order status + totals.
7. UI shows success and navigates back to Work Order detail.

#### Alternate: all declined
- Same flow, but UI shows that approved total becomes 0 and resultant status is Declined (terminal).

#### Alternate: user cancels
- Cancel returns to Work Order detail without saving; discard local changes.

---

## 6. Functional Behavior

### Triggers
- User clicks “Record Approval” action from Work Order detail
- User visits direct URL for approval recording

### UI actions
- **Load**: fetch Work Order and line items; fetch approval configuration if not present.
- **Edit**: for each line item, choose `Approved` or `Declined`.
- **Preview**: compute `totalApprovedAmountPreview = sum(price for items marked Approved)` (display-only; backend remains source of truth).
- **Confirm**: submit all decisions in one request.
- **Cancel**: discard changes, navigate back.

### State changes (frontend-visible)
- Line items’ approval statuses change from Pending to Approved/Declined after successful confirmation.
- Work Order status updates:
  - If ≥ 1 item approved → moves to “approved-for-work” state (exact enum TBD)
  - If all declined → moves to terminal “declined” state

### Service interactions (high level)
- GET Work Order detail (includes line items + current statuses)
- GET applicable approval configuration (customer > location precedence) OR use config embedded in Work Order
- POST partial approval submission (atomic)
- Handle errors: invalid state, approval window expired, auth/forbidden, conflicts

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Record Approval action is only enabled when Work Order is in the backend-defined “awaiting approval” status.
- Each approval-eligible line item must be set to either Approved or Declined before enabling Confirm.
- If approval method requires signature:
  - Signature/proof must be present before Confirm enabled.
- If approval window expired for re-approving a declined item:
  - UI must prevent submission if backend indicates expiration pre-check OR show backend error on submit.

### Enable/disable rules
- Disable Confirm until:
  - Data fully loaded
  - No validation errors
  - All line items have a selected decision
  - Required proof fields satisfied
- Disable per-line toggles if backend indicates line item not editable (read-only) due to state/time window.

### Visibility rules
- Approval method section renders based on resolved configuration:
  - `CLICK_CONFIRM` / “ServiceAdvisorElectronic” → show confirmation checkbox + advisor identity stamp (read-only display) if required
  - `SIGNATURE` / `DIGITAL_SIGNATURE` → show signature capture component placeholder + clear/reset action
  - `ELECTRONIC_SIGNATURE` / `VERBAL_CONFIRMATION` → show appropriate fields **only if backend explicitly supports these for Work Orders** (otherwise hide and treat as unsupported)

### Error messaging expectations
- Invalid state: “Approval can only be recorded when the Work Order is awaiting approval.”
- Window expired: “Approval Window has expired. A new estimate/work order is required.”
- Permission denied: “You don’t have permission to record approvals for this Work Order.”
- Conflict/concurrency: “This Work Order was updated by someone else. Reload and try again.”

---

## 8. Data Requirements

### Entities involved (frontend consumption)
- **WorkOrder**
  - `id`
  - `status` (enum/string)
  - `customerId`
  - `shopId/locationId` (for config resolution, if needed)
  - `totalApprovedAmount` (money/decimal)
  - `approvalWindowEnd` (datetime, optional)
  - `updatedAt` / `version` (for optimistic concurrency, if provided)
- **WorkOrderLineItem** (represents services/parts; exact entity types may differ)
  - `id`
  - `type` (SERVICE/PART)
  - `description`
  - `price` (money/decimal)
  - `approvalStatus` (PENDING/APPROVED/DECLINED)
  - `approvalTimestamp` (datetime, optional)
  - `declinedReason` (if supported; not defined in inputs)
  - `approvalMethodUsed` (enum, optional)
  - `approvalProofId` (optional)
- **ApprovalConfiguration** (resolved)
  - `approvalMethod` (enum)
  - `declineExpiryDays` or `approvalWindowDuration` (naming mismatch in backend references)
  - `requireSignature` (boolean)

### Fields: required/defaults
- For submission, required fields depend on backend contract:
  - WorkOrder id (required)
  - Line item decisions (required)
  - Approval method identifier (required)
  - Proof payload for signature (required only when method requires signature)

### Read-only vs editable by state/role
- Editable only when Work Order is in approval-recordable state.
- Line items editable only when:
  - `approvalStatus == PendingApproval` OR re-approval is allowed within window (backend driven)
- Everything is read-only after confirmation succeeds.

### Derived/calculated fields
- `totalApprovedAmountPreview` calculated client-side for immediate feedback; backend remains authoritative.
- `approvalWindowRemaining` can be derived if `approvalWindowEnd` is provided (display only).

---

## 9. Service Contracts (Frontend Perspective)

> NOTE: Backend reference documents show endpoints for **Estimates** approval configuration and approval actions; Work Order partial approval endpoints are not explicitly defined. This is a blocking clarification.

### Load/view calls
- `GET /api/workorders/{id}` (or `/api/work-orders/{id}`)  
  Expected response includes Work Order + line items with approval status.
- `GET /api/approval-configurations/applicable?locationId={id}&customerId={id}`  
  If Work Order payload does not already provide resolved approval configuration.

### Create/update calls (submit)
One of the following must exist (clarify which):
- `POST /api/work-orders/{workOrderId}/approvals/partial` (suggested)
- OR `POST /api/work-orders/{workOrderId}/approvals` with per-line statuses
- OR reuse estimate approval endpoints (unlikely; domain mismatch)

**Request (conceptual)**
```json
{
  "approvedBy": 123,
  "approvalMethod": "DIGITAL_SIGNATURE",
  "proof": { "signatureData": "..." },
  "lineItems": [
    { "lineItemId": "A", "approvalStatus": "APPROVED" },
    { "lineItemId": "B", "approvalStatus": "DECLINED" }
  ],
  "reasonNote": "optional"
}
```

**Response (conceptual)**
- 200/201 with updated Work Order summary:
  - `status`
  - `totalApprovedAmount`
  - updated line items approval fields

### Error handling expectations
- `400` validation (missing decisions, missing proof)
- `401` unauthenticated
- `403` unauthorized
- `404` not found
- `409` conflict (version mismatch / concurrent update / invalid transition)
- `422` (if used) for approval window expired; otherwise `400` with specific code

Frontend must map backend error codes/messages to user-friendly errors and keep raw details out of UI.

---

## 10. State Model & Transitions

### Allowed states (authoritative references)
WorkOrder state machine document defines:
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

### Approval-related expectations for this story
- Partial approval recording is allowed only when Work Order is in **`AWAITING_APPROVAL`** (per backend story reference wording “AwaitingApproval”; reconcile enum naming).
- After confirmation:
  - If at least one item approved → transition to a state that allows execution.  
    **Open question:** is this `APPROVED` or `WORK_IN_PROGRESS` or another state? The FSM doc does not mention `ApprovedForWork`.
  - If all declined → transition to **`CANCELLED`** or another terminal declined state. FSM doc includes CANCELLED but not DECLINED, while backend story reference includes DECLINED.

### Role-based transitions
- Service Advisor can perform approval recording transition(s). Exact RBAC permissions are enforced server-side; UI will hide/disable based on capability flags if provided.

### UI behavior per state
- If not in eligible approval state: hide “Record Approval” action; direct URL shows read-only error banner and back link.

---

## 11. Alternate / Error Flows

1. **Invalid state (user tries direct URL)**
   - Show error banner and disable form.
   - Provide “Back to Work Order” action.

2. **User cancels**
   - Prompt if dirty changes: “Discard changes?” (safe UI default)
   - Navigate back without calling backend.

3. **Backend rejects due to approval window expired**
   - Keep user selections, show error, disable Confirm until reload OR allow user to adjust statuses to valid set (backend-driven).

4. **Concurrency conflict**
   - If backend returns 409: show “Reload to continue” with one-click reload that refetches Work Order and rehydrates UI.

5. **Unauthorized**
   - If 403: show not-authorized message and link back; do not expose action buttons.

6. **Empty states**
   - If Work Order has zero approval-eligible items: show message and disable Confirm; link back.

---

## 12. Acceptance Criteria

### Scenario 1: Successful partial approval (some approved, some declined)
**Given** a Service Advisor is authenticated  
**And** a Work Order is in `AWAITING_APPROVAL` state  
**And** the Work Order has 3 line items with `approvalStatus = PENDING_APPROVAL`  
**When** the advisor marks item 1 and 2 as `APPROVED` and item 3 as `DECLINED`  
**And** the advisor provides any required approval proof based on the resolved approval method  
**And** the advisor clicks “Confirm Approval”  
**Then** the frontend sends a single atomic request containing all line item decisions and the approval method/proof  
**And** on success the UI displays updated line item approval statuses  
**And** the UI displays the backend-returned `totalApprovedAmount` equal to the sum of approved items  
**And** the UI navigates back (or refreshes) to show the Work Order in the post-approval status.

### Scenario 2: All items declined transitions to terminal decline/cancel state
**Given** a Work Order is in `AWAITING_APPROVAL`  
**And** it has 2 line items pending approval  
**When** the advisor marks both as `DECLINED` and confirms  
**Then** the UI shows the Work Order transitioned to the backend terminal “declined/cancelled” state  
**And** `totalApprovedAmount` is shown as 0  
**And** the approval action is no longer available.

### Scenario 3: Record Approval is blocked for invalid Work Order state
**Given** a Work Order is in `WORK_IN_PROGRESS`  
**When** the advisor attempts to open the Record Approval screen  
**Then** the UI blocks editing and displays “Approval can only be recorded when the Work Order is awaiting approval.”  
**And** no submit action is available.

### Scenario 4: Approval method configuration precedence (customer overrides location)
**Given** the applicable approval configuration for the Work Order’s customer specifies `DIGITAL_SIGNATURE`  
**And** the location default specifies `SERVICE_ADVISOR_ELECTRONIC`  
**When** the Record Approval screen loads  
**Then** the UI renders the digital signature proof capture requirement  
**And** submission is blocked until signature proof is provided  
**And** the submitted payload uses the customer-required method identifier.

### Scenario 5: Re-approval within approval window (if supported)
**Given** a line item is currently `DECLINED`  
**And** the backend indicates the Work Order is still within the approval window (e.g., `approvalWindowEnd` in the future)  
**When** the advisor changes that item to `APPROVED` and confirms  
**Then** the frontend submits the change successfully  
**And** the UI shows updated totals including the re-approved item  
**And** the UI displays any backend-provided indicator that a schedule amendment workflow was triggered (e.g., banner/message or returned flag).

### Scenario 6: Re-approval outside approval window is rejected
**Given** a line item is `DECLINED`  
**And** the backend indicates the approval window has expired  
**When** the advisor attempts to change the item to `APPROVED` and confirm  
**Then** the backend rejects the request  
**And** the UI displays “Approval Window has expired. A new estimate/work order is required.”  
**And** no state change is shown locally as committed.

---

## 13. Audit & Observability

### User-visible audit data
- After successful confirmation, show (read-only):
  - approval recorded timestamp (from backend)
  - recorded by (user name/id if returned)
  - approval method used
  - proof reference presence (e.g., “Signature on file”) without displaying sensitive payload

### Status history / traceability expectations
- Provide a link/button “View Approval/Transition History” if the Work Order detail already supports transitions history; otherwise defer.
- Ensure frontend includes/propagates correlation ID headers if the repo uses them (per workspace convention; otherwise Open Question).

---

## 14. Non-Functional UI Requirements

- **Performance**: initial load under 2s on typical store network for ≤ 100 line items; show skeleton/loading state.
- **Accessibility**: WCAG 2.1 AA; keyboard navigable line item decisions; form errors announced.
- **Responsiveness**: usable on tablet width; line items list supports vertical scrolling.
- **i18n**: all user-visible strings via standard localization mechanism.
- **Timezone**: display timestamps in store/user timezone if available; otherwise user locale.
- **Currency**: display money using store currency formatting; no client-side currency conversion.

---

## 15. Applied Safe Defaults
- SD-UX-01 (Dirty form confirm): Prompt before discarding unsaved changes on Cancel/back; safe as it’s UI ergonomics only. Impacted: UX Summary, Alternate Flows.
- SD-UX-02 (Loading/empty states): Use standard loading indicator and explicit empty-state message when no line items; safe as it does not alter business logic. Impacted: UX Summary, Error Flows.
- SD-ERR-01 (Generic error mapping): Map HTTP 401/403/404/409 to standard user-friendly banners while preserving backend error codes for logs; safe as it’s presentation-layer handling. Impacted: Service Contracts, Error Flows.

---

## 16. Open Questions

1. **Work Order status enums and mapping:** Is the approval-recordable Work Order status exactly `AWAITING_APPROVAL` (FSM doc) or `AwaitingApproval` (backend story text), and what are the exact serialized values in the API?
2. **Post-approval Work Order state:** After partial approval with ≥1 approved item, what exact Work Order status should result? (FSM doc lacks `ApprovedForWork`; backend story references it.)
3. **Terminal decline state:** When all items are declined, does the Work Order become `DECLINED`, `CANCELLED`, or something else in the authoritative workexec FSM?
4. **API endpoint contract for partial approval on Work Orders:** What is the exact endpoint path, request schema, and response schema for submitting partial approval decisions on a Work Order?
5. **Line item model:** Are line items represented as separate entities for services and parts (e.g., `WorkOrderService` and `WorkOrderPart`) or a unified list in the Work Order API? What identifiers should the frontend send back?
6. **Approval method set:** For Work Orders, which approval methods are supported now (`CLICK_CONFIRM`, `SIGNATURE`, `ELECTRONIC_SIGNATURE`, `VERBAL_CONFIRMATION`), and which require proof payloads?
7. **Approval window source of truth:** Is `approvalWindowEnd` provided on Work Order, or must the frontend compute it from configuration? (Computing from config may violate safe-default restrictions if unclear.)
8. **Schedule amendment trigger:** When re-approving within window, what does “trigger schedule amendment workflow” mean for frontend—does backend return a flag/message, or is there a separate UI flow to invoke?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Approval: Record Partial Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/269  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #22 - Approval: Record Partial Approval  
**URL**: https://github.com/louisburroughs/durion/issues/22  
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
*Generated by Missing Issues Audit System - 2025-12-26T17:37:46.434850072*