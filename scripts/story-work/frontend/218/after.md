## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Completion: Validate Completion Preconditions (Checklist + Block Completion)

### Primary Persona
Service Advisor (or any authorized user attempting to complete a Work Order)

### Business Value
Prevent invoice disputes and revenue leakage by ensuring a Work Order cannot be marked **COMPLETED** unless required work, parts/labor reconciliation, and approval-gated change requests are resolved, with clear actionable guidance for remediation.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor (or authorized user)
- **I want** the POS UI to validate Work Order completion preconditions and show a completion checklist
- **So that** I can only complete Work Orders when all required conditions pass, and I can quickly fix what is missing when they do not.

### In-scope
- A “Complete Work Order” user action that:
  - invokes a completion precondition validation
  - renders a structured checklist of failures (if any)
  - blocks the completion transition until all checks pass
  - proceeds to completion transition only when eligible
- Clear UX for the two explicitly-called out failure categories:
  - pending approval-gated change requests
  - unreconciled parts usage / labor entries
- Explicit Moqui screen flows, transitions, and service calls (frontend perspective)

### Out-of-scope
- Defining or changing backend policies/formulas for “reconciled” (SoR is backend)
- Implementing inventory reconciliation workflows themselves (UI may deep-link, but does not execute reconciliation logic)
- Notification delivery for pending approvals / expiring approvals
- Creating/approving/declining change requests (covered by other workexec stories)

---

## 3. Actors & Stakeholders
- **Primary User Actor:** Service Advisor (authorized to complete Work Order)
- **Secondary Actors:** Service Manager (oversight), Technician (impacted by readiness/closure), Billing (needs correct completion state)
- **System Actor:** Moqui UI + backend services enforcing gate

---

## 4. Preconditions & Dependencies
- A Work Order exists and is viewable in the POS UI.
- Work Order is in a state where completion is meaningful (see State Model section).
- User is authenticated.
- Backend provides:
  - a completion preconditions validation capability returning a list of failed checks (as per story description and backend reference)
  - an endpoint/service to execute the completion transition (or generic transition)
- Dependency: Work Order state machine rules (workexec) must be respected; completion must not bypass validation.
- Dependency: Change request workflow exists such that “approval-gated” and “pending” can be determined by backend.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: action button **Complete Work Order**
- (Optional) From Work Order list row actions: **Complete** (only if user has permission; otherwise hidden/disabled)

### Screens to create/modify
- **Modify:** `apps/pos/screen/workorder/WorkOrderDetail.xml` (or equivalent work order detail screen)
  - Add “Complete Work Order” action and checklist dialog/panel
- **Create (if not present):** `apps/pos/screen/workorder/WorkOrderCompletionChecklist.xml`
  - A reusable screen/dialog rendering validation result + actionable list + retry/refresh
- **Modify (optional):** `apps/pos/screen/workorder/WorkOrderList.xml` to include quick access

### Navigation context
- User remains on Work Order context (same workOrderId).
- If validation fails, user stays on detail screen with checklist visible.
- If completion succeeds, user returns to detail screen with updated status and transition history visible/refreshable.

### User workflows
**Happy path**
1. User opens Work Order detail.
2. User clicks **Complete Work Order**.
3. UI calls “Validate completion preconditions”.
4. UI receives `canComplete=true`.
5. UI calls “Complete/transition Work Order to COMPLETED”.
6. UI refreshes Work Order detail (status now COMPLETED) and shows confirmation.

**Alternate paths**
- Validation fails: UI shows checklist grouped by category; completion action remains blocked until revalidated and passing.
- Unauthorized: UI hides or disables button; if invoked, handles 403 with clear message.
- Concurrency: if state changed by another user, UI refreshes and shows conflict.

---

## 6. Functional Behavior

### Triggers
- User presses **Complete Work Order** on a specific `workOrderId`.

### UI actions
- Show a modal/dialog (or inline panel) titled “Completion Checklist”.
- Display loading state while validation runs.
- Render:
  - Overall eligibility (Eligible / Not eligible)
  - Failed checks list (each with code, message, linked entity when provided)
  - “Refresh checklist” action to re-run validation
  - “Proceed to Complete” action enabled only when `canComplete=true`

### State changes
- Only after successful validation:
  - Trigger backend state transition to `COMPLETED` (or `READY_FOR_PICKUP` then `COMPLETED` if backend requires intermediate step; **see Open Questions**).
- UI must not optimistically set status without backend confirmation.

### Service interactions (frontend perspective)
1. `validateCompletionPreconditions(workOrderId)`
2. If eligible: `completeWorkOrder(workOrderId, userId, reason?)` or generic transition call

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI-enforced behavior, backend-authoritative)
- UI MUST invoke validation before attempting completion.
- UI MUST surface **all** failed checks returned (do not stop at first).
- UI MUST block completion when `canComplete=false`.

### Enable/disable rules
- “Proceed to Complete” button:
  - disabled until validation returns `canComplete=true`
  - disabled while validation or completion request is in-flight
- “Complete Work Order” entry action:
  - disabled/hidden if user lacks permission (if permission info is available; otherwise handle 403)

### Visibility rules
- Checklist panel visible after completion attempt OR user explicitly opens it.
- If no failures, show a concise “All checks passed” summary and enable “Proceed”.

### Error messaging expectations
- Validation failure (business): show checklist items as actionable messages.
- System failure (5xx/network): show generic error “Unable to validate completion right now. Try again.” plus correlation/trace id if provided.
- 403: “You do not have permission to complete this work order.”
- 409: “Work order changed since you opened it. Refreshing…” then reload.

---

## 8. Data Requirements

### Entities involved (frontend consumption)
- `WorkOrder`
  - `id` (string/long)
  - `status` (enum: per workexec state machine)
- `WorkOrderItem`
  - `id`
  - `status`
  - `isRequiredForCompletion` (boolean) **(backend-defined)**
- `LaborEntry`
  - `id`
  - `status` (draft/pending/confirmed etc. **backend-defined**)
- `PartUsageEvent`
  - `id`
  - `status` (pending allocation, quantity mismatch, allocated/confirmed etc. **backend-defined**)
- `ChangeRequest`
  - `id`
  - `status`
  - `isApprovalGated` (boolean) **(backend-defined)**

### Checklist response model (required for UI rendering)
- `canComplete: boolean`
- `failedChecks: array` of:
  - `check: string` (e.g., `INCOMPLETE_REQUIRED_ITEMS`, `PENDING_APPROVALS`, `UNRECONCILED_PARTS`, `UNRECONCILED_LABOR`)
  - `message: string` (user-readable)
  - `entityType?: string` (e.g., `WorkOrderItem`, `ChangeRequest`, `PartUsageEvent`, `LaborEntry`)
  - `entityId?: string`
  - `entityRef?: object` (optional; minimal display fields if provided)
  - `severity?: 'BLOCKER'|'WARNING'` (if backend supports; otherwise omit)
  - `actionHint?: string` (optional hint text; safe default only if backend provides)

### Read-only vs editable
- Checklist is read-only.
- Completion “reason” field: **not defined in inputs**; do not add unless backend requires (Open Question).

### Derived/calculated
- “Eligible” computed from `canComplete`.
- Grouping by `check` prefix/category is derived client-side.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names are not provided in inputs. Frontend must integrate to existing backend API. This story defines expected contracts and error handling; implementation must map these to actual Moqui service-calls once confirmed.

### Load/view calls
- `GET WorkOrder detail` (already exists in UI)
  - Must include `status` at minimum.

### Validate completion preconditions
- **Expected:** `POST /api/workorders/{workOrderId}/completion/validate` OR equivalent
- **Request:** `{ workOrderId }` (or path param only)
- **Response 200:** `{ canComplete: boolean, failedChecks: [...] }`
- **Errors:**
  - 404 if workOrderId not found
  - 403 if user not authorized to complete/validate
  - 409 if work order state not eligible for completion validation (or changed)

### Complete transition call
- **Expected:** `POST /api/workorders/{workOrderId}/complete` OR generic transition endpoint
- **Request:** include `userId` if backend requires (many workexec endpoints do); otherwise rely on auth context (**Open Question**)
- **Response 200/201:** updated work order status and transition metadata
- **Errors:**
  - 400/409 if completion gate fails (must return failedChecks or a structured error that UI can render)
  - 403 unauthorized
  - 409 concurrency/state mismatch

### Error handling expectations (Moqui UI)
- Map backend structured failure payload (failedChecks) into checklist UI.
- If completion endpoint returns “gate failed”, UI should present returned checklist without requiring separate validate call.

---

## 10. State Model & Transitions

### Allowed states (from workexec state machine reference)
WorkOrder statuses include:
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

### Completion transition
- UI intent: transition to `COMPLETED`.
- Completion action should only be offered when Work Order is in an appropriate pre-completion state (likely `READY_FOR_PICKUP` or `WORK_IN_PROGRESS` variants). Exact allowed-from states for completion are **not explicitly provided** in the workexec state machine doc beyond general flow; treat as backend authoritative.

### Role-based transitions
- Roles/permissions are referenced but not enumerated in inputs.
- UI must rely on backend authorization and handle 403; if a permission flag is available in WorkOrder detail payload, use it to hide/disable completion action.

### UI behavior per state
- `COMPLETED` or `CANCELLED`: “Complete Work Order” action hidden/disabled; checklist can be view-only if needed.
- In other states: action visible if authorized; on attempt, always validate.

---

## 11. Alternate / Error Flows

### Validation failures
- If failed checks include pending approvals:
  - Show list of change requests with status and identifier if provided.
  - Provide “View Change Requests” navigation (link to existing work order change requests section/screen).
- If unreconciled parts usage:
  - Show each part usage event with mismatch/pending status.
  - Provide navigation to Parts/Inventory reconciliation screen if it exists; otherwise show message only and log Open Question for link target.
- If incomplete required work items:
  - Show list of WorkOrderItems not completed.

### Concurrency conflicts
- If validation returns 409:
  - Show message and prompt user to refresh.
  - Provide “Refresh Work Order” action that reloads detail and re-runs validation.

### Unauthorized access
- If 403 on validate or complete:
  - Show blocking toast/banner.
  - Ensure completion UI does not keep retrying automatically.

### Empty states
- If failedChecks is empty but canComplete=false (invalid response):
  - Show generic error “Completion eligibility could not be determined.” and log.
- If API unreachable:
  - Show retry.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Completion succeeds when all preconditions pass
**Given** a Work Order exists and backend validation returns `canComplete=true` with an empty `failedChecks` list  
**When** the user selects “Complete Work Order” and confirms completion  
**Then** the UI calls the completion transition endpoint  
**And** the Work Order status is refreshed and shows `COMPLETED`  
**And** the UI shows a success confirmation message.

### Scenario 2: Block completion when required WorkOrderItems are incomplete
**Given** a Work Order exists and validation returns `canComplete=false`  
**And** `failedChecks` contains an item with `check=INCOMPLETE_REQUIRED_ITEMS` referencing at least one WorkOrderItem  
**When** the user selects “Complete Work Order”  
**Then** the UI displays a completion checklist containing the incomplete item(s)  
**And** the “Proceed to Complete” action is disabled  
**And** no completion transition request is sent.

### Scenario 3: Block completion when approval-gated Change Requests are unresolved
**Given** a Work Order exists and validation returns `canComplete=false`  
**And** `failedChecks` contains an item with `check=PENDING_APPROVALS` referencing a ChangeRequest not in `APPROVED`  
**When** the user selects “Complete Work Order”  
**Then** the UI displays a checklist item describing the pending approval(s)  
**And** the UI provides a navigation action to view the Change Request(s) (where available)  
**And** the UI blocks completion.

### Scenario 4: Block completion when parts usage is unreconciled
**Given** a Work Order exists and validation returns `canComplete=false`  
**And** `failedChecks` contains an item with `check=UNRECONCILED_PARTS` referencing a PartUsageEvent in a pending/mismatch state  
**When** the user selects “Complete Work Order”  
**Then** the UI displays checklist items identifying the unreconciled parts usage  
**And** completion remains blocked until a re-validation returns `canComplete=true`.

### Scenario 5: Multiple failures are shown together
**Given** a Work Order exists and validation returns `canComplete=false`  
**And** `failedChecks` contains at least two different check types (e.g., `INCOMPLETE_REQUIRED_ITEMS` and `PENDING_APPROVALS`)  
**When** the user selects “Complete Work Order”  
**Then** the UI displays all failed checks in the checklist  
**And** the UI does not hide subsequent failures after the first.

### Scenario 6: Unauthorized user cannot complete
**Given** a Work Order exists  
**And** the current user is not authorized to complete the Work Order  
**When** the user attempts to validate or complete the Work Order  
**Then** the backend returns 403  
**And** the UI displays an authorization error message  
**And** the Work Order status is not changed.

### Scenario 7: Work order changed concurrently
**Given** a Work Order is open in the UI  
**And** another user changes the Work Order state such that completion attempt conflicts  
**When** the user attempts to validate or complete and the backend returns 409  
**Then** the UI prompts the user to refresh  
**And** after refresh, the UI reflects the latest Work Order status and checklist state.

---

## 13. Audit & Observability

### User-visible audit data
- After completion attempt (success or failure), UI should display:
  - timestamp of last validation attempt (client-side)
  - outcome (passed/failed)
- If Work Order detail has “Transition History” section (common in workexec), refresh it after completion.

### Status history
- On successful completion, UI must refresh and show the updated status and latest transition record (who/when/why if available).

### Traceability expectations
- All validate/complete requests should propagate correlation/trace id if the platform provides (Moqui typically supports request IDs); surface in error details when available.

---

## 14. Non-Functional UI Requirements
- **Performance:** validation call should complete within acceptable UX bounds; show spinner if >300ms; avoid duplicate calls on repeated clicks (debounce/in-flight guard).
- **Accessibility:** checklist must be keyboard navigable; modal must trap focus; errors announced via aria-live region.
- **Responsiveness:** usable on tablet resolutions commonly used in shops.
- **i18n/timezone/currency:** checklist messages are server-provided; UI chrome strings must be localizable. Timestamps displayed in shop/user timezone if already supported by app.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: If `failedChecks` is empty, show “No blocking issues reported” and treat `canComplete` as authoritative; qualifies as safe because it does not change domain policy; impacts UX Summary, Error Flows.
- SD-UX-INFLIGHT-GUARD: Disable action buttons during in-flight validation/completion to prevent double-submit; qualifies as safe because it is standard UI ergonomics; impacts Functional Behavior, Error Flows.
- SD-ERR-GENERIC-RETRY: For network/5xx, show generic retry with no domain assumptions; qualifies as safe because it does not alter business rules; impacts Error Flows, Non-Functional.

---

## 16. Open Questions
1. **Backend contract:** What are the exact API endpoints (paths) and payload schema for:
   - completion precondition validation
   - completing/transitioning a work order to `COMPLETED`  
   The frontend needs exact routes to implement Moqui service-calls deterministically.
2. **Allowed-from states:** Which WorkOrder statuses are allowed to transition to `COMPLETED` (e.g., only `READY_FOR_PICKUP` vs also `WORK_IN_PROGRESS`/`AWAITING_PARTS` once checks pass)?
3. **Auth context:** Does the completion endpoint require `userId` in the request body/query (as in `/start`), or is it derived from the authenticated session?
4. **Checklist linking:** Are there existing POS screens/routes for:
   - viewing change requests for a work order
   - reconciling part usage events / labor entries  
   If yes, provide the target screen paths for deep links; if no, should UI only display identifiers without navigation?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Completion: Validate Completion Preconditions  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/218  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Validate Completion Preconditions

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
System

## Trigger
User attempts to complete a workorder.

## Main Flow
1. System checks required workorder items are marked complete per policy.
2. System verifies parts usage and labor entries are reconciled (no invalid quantities).
3. System checks there are no unresolved approval-gated change requests.
4. System generates a completion checklist and prompts for missing items.
5. System allows completion only when all checks pass.

## Alternate / Error Flows
- Pending approval exists → block completion and show what is pending.
- Unreconciled parts usage → block and show reconciliation steps.

## Business Rules
- Completion checks are configurable but must be enforced consistently.
- Completion requires resolving approval-gated items.

## Data Requirements
- Entities: Workorder, WorkorderItem, LaborEntry, PartUsageEvent, ChangeRequest
- Fields: status, completionChecklist, pendingApprovals, unreconciledItems

## Acceptance Criteria
- [ ] System blocks completion when required conditions are not met.
- [ ] System provides a clear checklist of what to fix.
- [ ] System allows completion when all conditions pass.

## Notes for Agents
Completion gate is the last chance to prevent invoice disputes and leakage.

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