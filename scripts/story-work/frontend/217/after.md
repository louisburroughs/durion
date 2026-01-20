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
[FRONTEND] [STORY] Completion: Resolve Approval-Gated Change Requests

### Primary Persona
Service Advisor (Back Office / Front Counter)

### Business Value
Ensure work orders cannot be completed while approval-gated change requests are unresolved, and provide an explicit, auditable workflow to approve/decline/override change requests so the work order reflects authorized work and completion blockers are transparent.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to view and resolve all approval-gated change requests for a work order (approve, decline, and handle emergency denial acknowledgment where applicable)  
- **So that** the work order can move to completion states only when authorized work is correctly reflected and all compliance/audit requirements are met.

### In Scope
- Work order “Completion blockers” UI that highlights unresolved approval-gated change requests.
- List + detail view of change requests associated to a work order.
- Advisor actions to:
  - Approve a change request (with required approval note).
  - Decline a change request (with required approval note).
  - For emergency exceptions that were declined: record customer denial acknowledgment (to unblock closure).
- Read-only display of audit-relevant fields: who/when/what decision, approval notes, status.
- Integration with backend endpoints listed in `CHANGE_REQUEST_WORKFLOW.md` and work order “can-close” check.

### Out of Scope
- Creating change requests (technician flow).
- Editing change request item lines, prices, or inventory allocation/consumption.
- Customer-facing approval capture methods (signature, electronic signature) for **estimates** (separate workflow).
- Work order state transitions UI beyond showing “blocked/unblocked” and “can close” status (unless already exists).

---

## 3. Actors & Stakeholders

- **Primary Actor:** Service Advisor
- **Supporting Actors:**
  - Back Office Manager (may handle escalations; see open questions re: override)
  - Technician (downstream consumer of approved items)
- **System Actors:** POS/Moqui UI, Workexec backend APIs
- **Stakeholders:** Audit/compliance, Shop management

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- A Work Order exists and is in an execution lifecycle state where change requests are relevant (backend rules mention `IN_PROGRESS` for creation; resolution should be allowed at least when CR exists).
- Work order has **one or more** associated Change Requests retrievable via:
  - `GET /api/work-orders/{workOrderId}/change-requests`

### Dependencies
- Backend endpoints available (from authoritative workexec docs):
  - `GET /api/work-orders/{workOrderId}/change-requests`
  - `GET /api/change-requests/{id}`
  - `POST /api/change-requests/{id}/approve`
  - `POST /api/change-requests/{id}/decline`
  - `POST /api/change-requests/{id}/acknowledge-denial`
  - `GET /api/work-orders/{workOrderId}/can-close`
- Work order status machine exists (from `WORKORDER_STATE_MACHINE.md`) and completion gating is enforced by backend.
- Moqui frontend project conventions for:
  - Screen routing / parameter naming (unknown from provided inputs; see Open Questions).
  - Auth token propagation to backend.

---

## 5. UX Summary (Moqui-Oriented)

### Entry Points
- From an existing Work Order detail screen: a “Change Requests” panel/section and/or a “Resolve blockers” callout shown when unresolved approval-gated change requests exist.
- From a “Completion” step/section: if completion is blocked, provide a direct navigation link to “Resolve Change Requests”.

### Screens to Create/Modify
1. **Modify**: Work Order detail screen (existing)
   - Add a “Completion Blockers” summary widget:
     - Shows if pending change requests exist.
     - Shows `canClose` result (true/false) and, if false, “why” summary (minimum: “Pending change requests” and/or “Emergency denial acknowledgment required”).
     - Link to Change Requests screen.
2. **Create**: `WorkOrderChangeRequests` screen
   - Route expects `workOrderId` parameter.
   - Displays list of change requests for the work order, grouped by status (at minimum: awaiting advisor review, approved, declined, cancelled).
   - Each list item navigates to detail/resolution screen.
3. **Create**: `ChangeRequestDetail` screen
   - Route expects `changeRequestId` and includes `workOrderId` for back navigation.
   - Shows request description, emergency flags/evidence fields, and associated items (services/parts) with their statuses.
   - Provides action buttons (Approve/Decline/Acknowledge Denial) depending on state.

### Navigation Context
- Breadcrumb-like behavior:
  - Work Order → Change Requests → Change Request Detail
- After action success (approve/decline/ack):
  - Return to Change Requests list with refreshed statuses and updated “can close” status.

### User Workflows

#### Happy Path: Approve
1. Advisor opens work order; sees completion blocked due to pending change requests.
2. Advisor opens Change Requests list, selects a pending request.
3. Advisor clicks “Approve”, enters required approval note, confirms.
4. UI shows success; request moves to APPROVED; list refreshes; if all resolved and no other blockers, “can close” becomes true.

#### Happy Path: Decline + Emergency Acknowledgment
1. Advisor declines an emergency exception request (status becomes DECLINED).
2. UI displays a required next step: “Customer denial acknowledgment required before closing.”
3. Advisor records acknowledgment via “Acknowledge Denial”.
4. UI refreshes `canClose` and removes the emergency acknowledgment blocker once satisfied.

#### Alternate: Customer Unreachable
- Advisor takes no action; request remains awaiting review; completion remains blocked.

---

## 6. Functional Behavior

### Triggers
- Screen load of Work Order detail triggers:
  - Load change request summary for the work order.
  - Load `can-close` status.
- Entering Change Requests list triggers:
  - Load list of change requests for work order.
- Entering Change Request detail triggers:
  - Load change request detail (optional if list payload is insufficient).

### UI Actions
- From Change Request detail:
  - **Approve**: opens modal form requiring `approvalNote`, then submits approve endpoint.
  - **Decline**: opens modal form requiring `approvalNote`, then submits decline endpoint.
  - **Acknowledge Denial**: confirmation dialog, then submits acknowledge endpoint.

### State Changes (Frontend-Visible)
- ChangeRequest `status` updates after approve/decline.
- For emergency denial acknowledgment:
  - associated emergency items get `customerDenialAcknowledged=true` (may not be returned; UI should refresh detail and/or can-close).
- Work order closure eligibility updates based on `GET can-close`.

### Service Interactions (Moqui-Oriented)
- Each action is implemented as a Moqui screen transition calling a service (service wraps REST call).
- Services must be invoked with:
  - authenticated context
  - correlation/request id if project supports it (safe default only for observability plumbing)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Approve/Decline requires **non-empty** `approvalNote`.
  - UI: block submit, show inline error “Approval note is required.”
- Acknowledge Denial:
  - Only enabled if change request is flagged emergency exception **and** is in `DECLINED` status (per backend rules).
  - UI: hide/disable otherwise.

### Enable/Disable Rules
- Approve/Decline buttons enabled only when ChangeRequest status is `AWAITING_ADVISOR_REVIEW`.
- If work order is not in an allowed state for resolution (unclear), UI should still render read-only and rely on backend errors; see Open Questions.

### Visibility Rules
- Show “Emergency/Safety” section if:
  - change request `isEmergencyException=true` OR any item has emergency/safety flag.
- Show “Completion blocked” banner on work order if:
  - Any change request is awaiting advisor review OR
  - `can-close=false` due to emergency denial acknowledgment missing.

### Error Messaging Expectations
- For 400 validation errors: show field-level message when possible; otherwise show “Unable to submit: <message>”.
- For 403: show “You don’t have permission to perform this action.”
- For 409: show “This change request was updated by another user. Refresh and try again.”
- For 404: show “Change request not found.”

---

## 8. Data Requirements

### Entities Involved (Frontend View Models)
- **WorkOrder** (read-only in this story)
  - `workOrderId`
  - `status` (from work order FSM)
- **ChangeRequest**
  - `id`
  - `workOrderId`
  - `requestedByUserId`
  - `requestedAt`
  - `status` (expected: `AWAITING_ADVISOR_REVIEW`, `APPROVED`, `DECLINED`, `CANCELLED`)
  - `description` (required)
  - `isEmergencyException` (boolean)
  - `exceptionReason` (string)
  - `approvalNote` (string; used as artifact for approve/decline per rules)
  - `approvedAt`, `approvedBy`
  - `declinedAt`
  - `supplementalEstimatePdfId` (optional, read-only)
- **WorkOrderService / WorkOrderPart** (as child display of change request)
  - `id`
  - `changeRequestId`
  - `status` (expected: `PENDING_APPROVAL`, `READY_TO_EXECUTE`, `CANCELLED`, etc.)
  - Emergency fields:
    - `isEmergencySafety` (boolean)
    - `photoEvidenceUrl` (string)
    - `photoNotPossible` (boolean)
    - `emergencyNotes` (string)
    - `customerDenialAcknowledged` (boolean)

### Field Types / Required / Defaults
- `approvalNote`: required on approve/decline; default empty in UI form.
- `exceptionReason`: display-only here; if backend supports entry for override, that’s a separate action (see Open Questions).

### Read-only vs Editable
- Editable in this story:
  - `approvalNote` input when approving/declining.
- Everything else read-only.

### Derived/Calculated Fields
- `hasPendingChangeRequests` derived from list where `status==AWAITING_ADVISOR_REVIEW`.
- `requiresEmergencyAcknowledgment` derived if:
  - exists change request with `isEmergencyException==true` AND `status==DECLINED` AND any associated emergency item `customerDenialAcknowledged==false`
  - If backend only returns `can-close` boolean, UI may not be able to derive “why”; see Open Questions.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend contracts are partially specified; schemas are not fully defined. Frontend must treat response bodies as TBD beyond fields used.

### Load / View Calls
1. **List change requests for work order**
   - `GET /api/work-orders/{workOrderId}/change-requests`
   - Expected: array of ChangeRequest summaries; ideally includes status, description, emergency flag, requestedAt
2. **Load change request detail**
   - `GET /api/change-requests/{id}`
   - Expected: ChangeRequest plus associated services/parts (or references)
3. **Check if work order can close**
   - `GET /api/work-orders/{workOrderId}/can-close`
   - Expected: boolean response body (`true|false`) per doc

### Submit Calls
1. **Approve**
   - `POST /api/change-requests/{id}/approve`
   - Request body (per doc):
     ```json
     { "approvedBy": <userId>, "approvalNote": "<text>" }
     ```
2. **Decline**
   - `POST /api/change-requests/{id}/decline`
   - Request body (per doc):
     ```json
     { "approvalNote": "<text>" }
     ```
3. **Acknowledge Denial**
   - `POST /api/change-requests/{id}/acknowledge-denial`
   - No body

### Error Handling Expectations
- Map HTTP codes:
  - 400 → validation (show message)
  - 401 → session expired (redirect to login)
  - 403 → permission denied
  - 404 → missing entity
  - 409 → concurrency conflict / invalid state transition
  - 5xx → “System error, try again”

---

## 10. State Model & Transitions

### ChangeRequest State (from authoritative doc)
- `AWAITING_ADVISOR_REVIEW` (actionable)
- `APPROVED` (terminal for this story)
- `DECLINED` (terminal for decision; may require acknowledgment for emergency)
- `CANCELLED` (terminal)

### Allowed Transitions (UI-enforced)
- `AWAITING_ADVISOR_REVIEW` → `APPROVED` via Approve action
- `AWAITING_ADVISOR_REVIEW` → `DECLINED` via Decline action
- `DECLINED` + `isEmergencyException=true` → (acknowledgment recorded; state may remain `DECLINED`)

### Role-based Transitions (UI expectations; authorization enforced by backend)
- Service Advisor: approve/decline
- Manager/Advisor: acknowledge denial (unclear if restricted)
- Technician: no actions in this story UI

### UI Behavior per State
- AWAITING_ADVISOR_REVIEW: show Approve/Decline actions; show blocking banner.
- APPROVED: show read-only decision info; no actions.
- DECLINED: show read-only decision info; if emergency exception, show acknowledgment status and “Acknowledge Denial” if not yet acknowledged.
- CANCELLED: read-only.

---

## 11. Alternate / Error Flows

### Validation Failures
- Missing approval note on approve/decline:
  - UI prevents submit; if server returns 400, show message from server.
- Attempt acknowledge when not allowed:
  - UI should not present action; if forced (deep link), handle 400/409 gracefully.

### Concurrency Conflicts
- Another advisor resolves the change request while current user is viewing:
  - On submit → backend returns 409 or 400 invalid state; UI shows “Already resolved” and refreshes detail.

### Unauthorized Access
- If user lacks permission:
  - Action returns 403; UI keeps state, shows permission message, logs event.

### Empty States
- No change requests for work order:
  - Change Requests list shows “No change requests for this work order.”
  - Work order blockers widget shows none.

### Backend Unreachable / Timeout
- Show non-destructive error and retry option; do not change UI state optimistically unless project standard supports it (not specified).

---

## 12. Acceptance Criteria

### Scenario 1: Pending change requests are visible and block completion
**Given** a work order has at least one change request with status `AWAITING_ADVISOR_REVIEW`  
**When** the Service Advisor opens the Work Order detail screen  
**Then** the UI shows a completion blocker indicating pending change requests  
**And** the UI provides a navigation link to the Change Requests screen for that work order.

### Scenario 2: List change requests for a work order
**Given** a work order has change requests in multiple statuses  
**When** the Service Advisor navigates to the Work Order Change Requests screen  
**Then** the UI lists all change requests returned by `GET /api/work-orders/{workOrderId}/change-requests`  
**And** each list entry shows at minimum: description, status, requestedAt, and emergency flag (if present)  
**And** selecting an entry navigates to the Change Request detail view.

### Scenario 3: Approve requires an approval note and updates UI state
**Given** a change request is in status `AWAITING_ADVISOR_REVIEW`  
**When** the Service Advisor clicks “Approve” and submits an empty approval note  
**Then** the UI blocks submission and displays “Approval note is required.”  

**Given** a change request is in status `AWAITING_ADVISOR_REVIEW`  
**When** the Service Advisor submits “Approve” with a non-empty approval note  
**Then** the UI calls `POST /api/change-requests/{id}/approve` with `approvedBy` and `approvalNote`  
**And** on success, the UI refreshes the change request data showing status `APPROVED`  
**And** the approve/decline actions are no longer available.

### Scenario 4: Decline requires an approval note and updates UI state
**Given** a change request is in status `AWAITING_ADVISOR_REVIEW`  
**When** the Service Advisor submits “Decline” with a non-empty approval note  
**Then** the UI calls `POST /api/change-requests/{id}/decline` with `approvalNote`  
**And** on success, the UI refreshes the change request data showing status `DECLINED`  
**And** the approve/decline actions are no longer available.

### Scenario 5: Declined emergency exception requires customer denial acknowledgment to close
**Given** a work order has a change request where `isEmergencyException=true` and status `DECLINED`  
**And** emergency items under the request have `customerDenialAcknowledged=false`  
**When** the Service Advisor opens the work order  
**Then** the UI indicates the work order cannot close (via `GET /api/work-orders/{workOrderId}/can-close` returning `false`)  
**And** the Change Request detail screen shows an “Acknowledge Denial” action.

### Scenario 6: Acknowledge denial updates closure eligibility
**Given** a change request is flagged emergency exception and is in status `DECLINED`  
**When** the Service Advisor confirms “Acknowledge Denial”  
**Then** the UI calls `POST /api/change-requests/{id}/acknowledge-denial`  
**And** the UI refreshes `GET /api/work-orders/{workOrderId}/can-close`  
**And** if the backend returns `true`, the UI indicates the completion blocker is cleared.

### Scenario 7: Concurrency protection on approve/decline
**Given** a Service Advisor has a change request detail open in status `AWAITING_ADVISOR_REVIEW`  
**And** another user resolves the change request before submission  
**When** the first advisor submits approve/decline  
**Then** the UI shows a conflict message and refreshes the change request detail to show the latest status.

### Scenario 8: Permission denied
**Given** a user without permission attempts to approve a change request  
**When** they submit approval  
**Then** the backend returns 403  
**And** the UI shows a permission error and leaves the change request unchanged in the UI.

---

## 13. Audit & Observability

### User-visible Audit Data
- On Change Request detail, show:
  - status
  - requestedAt/requestedBy (if available)
  - approvedAt/approvedBy (if approved)
  - declinedAt (if declined)
  - approvalNote (read-only display; consider masking policy if required—unknown)
  - emergency acknowledgment state per item (if returned)

### Status History
- Not implementing full transition history UI unless backend provides it for change requests (not specified).  
- At minimum, show current status + timestamps from the ChangeRequest resource.

### Traceability Expectations
- Each approve/decline/ack action should:
  - include workOrderId and changeRequestId in logs (frontend console logging is insufficient; use project logging mechanism if present).
  - preserve backend correlation id if Moqui supports it (implementation detail).

---

## 14. Non-Functional UI Requirements

- **Performance:** Change Requests list should load within 2 seconds on typical broadband for up to 50 change requests (pagination behavior depends on API; see safe defaults).
- **Accessibility:** All actions reachable via keyboard; dialogs have focus trap; form errors announced to screen readers.
- **Responsiveness:** Mobile-friendly layout; list and detail usable on tablet.
- **i18n/timezone:** Display timestamps in store/user-local time if application provides; otherwise display ISO with timezone indicator (needs project convention; see Open Questions).

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for “no change requests” because it is UI ergonomics and does not alter domain behavior. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-RETRY: Provide a manual retry affordance on transient load failures because it is recoverable and does not change domain logic. Impacted sections: Alternate/Error flows.
- SD-UX-LIST-PAGINATION-CLIENT: If the API does not support pagination, render a client-side “show first 50 + filter” behavior to avoid UI stalls; safe as it’s presentation-only. Impacted sections: Non-functional, UX Summary.

---

## 16. Open Questions

1. **Moqui routing & screen placement:** What are the canonical screen paths/parameter names for Work Order detail in `durion-moqui-frontend` (e.g., `/workorders/ViewWorkOrder?workOrderId=`)? We need this to wire entry points and transitions correctly.  
2. **Backend payload shapes:** Do `GET /api/work-orders/{workOrderId}/change-requests` and `GET /api/change-requests/{id}` return associated service/part items (including `customerDenialAcknowledged`), or only the ChangeRequest header? UI needs item-level acknowledgment visibility.  
3. **Approval “submitted for customer approval” step:** The provided frontend story mentions “submit requests for customer approval (if not already submitted)” but the authoritative change request workflow only describes advisor approve/decline (internal). Is there a separate “send to customer” action/endpoint for change requests? If yes, provide endpoint and states.  
4. **Emergency override semantics conflict:** The backend reference story describes “Approved-With-Exception” and a manager override with `exceptionReason`, but the authoritative `CHANGE_REQUEST_WORKFLOW.md` does not include that state/action (it instead has emergency handling + denial acknowledgment). Which model is correct for frontend? If override exists, provide endpoint, required fields, and authorization.  
5. **Authorization rules:** Which roles can approve/decline/acknowledge-denial in the frontend? Are there separate permissions (e.g., advisor vs manager)? UI needs to hide/disable actions appropriately.  
6. **Work order completion gating UX:** Should the frontend attempt a “complete/ready-for-pickup” transition and display the backend’s failure reason (AC1 in backend story), or is completion handled elsewhere? If completion action exists in UI, provide the endpoint and error schema for “blocked by pending change requests.”

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Completion: Resolve Approval-Gated Change Requests  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/217  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Resolve Approval-Gated Change Requests

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
Workorder contains one or more approval-gated change requests.

## Main Flow
1. User views outstanding change requests tied to the workorder.
2. User submits requests for customer approval (if not already submitted).
3. System records approval/rejection outcomes.
4. Approved requests add authorized items to the workorder; rejected requests remain excluded.
5. System clears the completion block when all requests are resolved.

## Alternate / Error Flows
- Customer unreachable → leave request pending and keep workorder incomplete.
- Emergency exception policy invoked → record exception and proceed per policy.

## Business Rules
- All approval-gated requests must be resolved before completion (unless exception).
- Approvals must be linked to added items.

## Data Requirements
- Entities: ChangeRequest, ApprovalRecord, WorkorderItem
- Fields: changeRequestId, status, approvalId, resolutionAt, resolutionBy, exceptionReason

## Acceptance Criteria
- [ ] Pending change requests block completion.
- [ ] Resolved approvals correctly add or exclude items.
- [ ] Resolution is auditable.

## Notes for Agents
Make resolution visible; operators should never guess what’s blocking completion.

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