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

**Title:** [FRONTEND] [STORY] Execution: Record Labor Performed (Work Order Service Item)  
**Primary Persona:** Technician  
**Business Value:** Capture auditable technician labor (time-based or flat-rate) per work order service item to support accurate execution tracking, progress visibility, and downstream job-cost/WIP reporting without posting revenue/AR.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Technician  
- **I want** to record labor performed against a specific work order service line item (either hours or flat-rate completion) with optional notes  
- **So that** work progress is tracked accurately and the system can emit a `LaborRecorded` event for job costing/WIP, while maintaining an auditable, permissioned correction model.

### In Scope
- View work order service items eligible for labor entry.
- Create a new labor entry for a selected service item:
  - time-based hours entry **or**
  - flat-rate completion entry.
- Optional technician notes/results captured with the entry.
- Display existing labor entries for the service item (read-only list).
- Handle validation and backend errors explicitly in UI.
- Ensure UI reflects item/work order progress updates after save (via refresh).
- Support “correction by superseding” flow *only if backend exposes it* (see Open Questions).

### Out of Scope
- Defining or changing backend accounting rules/event schema contents beyond what backend returns.
- Payroll, technician pay rates, or billing calculations.
- Creating/modifying work order state machine (only reflect/trigger allowed transitions if provided).
- Parts consumption, picking, inventory allocation.
- Configuration of assignment policy, roles, or permissions.

---

## 3. Actors & Stakeholders
- **Technician (Primary):** records labor on assigned work orders/items.
- **Service Advisor (Secondary/Stakeholder):** may review or correct labor entries (permission-dependent; UI may be read-only for technician).
- **Accounting/Reporting Consumers (Downstream Stakeholder):** consumes `LaborRecorded` events; frontend must not display accounting postings as a result of labor entry.
- **System/Audit (Stakeholder):** requires immutable audit trail of entries and corrections.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to record labor entries (exact permission string TBD; backend enforced).
- Work order exists and is in a status that allows labor recording (likely one of in-progress statuses).

### Dependencies
- Backend endpoints for:
  - loading a work order + service items + existing labor entries
  - creating a labor entry
  - (optional) superseding/correcting a labor entry
- Backend enforces:
  - assignment policy (assigned vs not assigned)
  - valid work order/item state rules
  - validation for hours and flat-rate completion constraints
  - audit logging and event emission (`LaborRecorded`)
- UI tech: Vue 3 + TypeScript + Quasar + Moqui screens integration (per repo conventions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Work Order detail** screen, within an **Execution / Services** section, technician selects a **service line item** and chooses **“Record Labor”**.

### Screens to create/modify
1. **Modify existing Work Order detail screen** to add:
   - “Labor” action per service item (enabled/disabled based on eligibility returned by backend or derived from status/assignment fields).
   - “Labor Entries” subpanel/list on the service item detail.
2. **New screen/dialog**: `WorkOrder/LaborEntryCreate` (modal or routed screen) to record labor.

> Moqui implementation note: prefer a screen with a form + transitions; can be opened as a dialog if project convention supports it.

### Navigation context
- Context parameters required:
  - `workOrderId`
  - `workOrderItemSeqId` (service item identifier; exact field name depends on backend)
- Return navigation:
  - on success → back to Work Order detail, focused on the same service item.

### User workflows
**Happy path (time-based):**
1. Select service item → Record Labor.
2. Choose “Time-based”.
3. Enter `hours` (decimal) and optional `notes`.
4. Submit → see success message.
5. Work order/service item refreshes; new labor entry appears in list.

**Happy path (flat-rate):**
1. Select service item → Record Labor.
2. Choose “Flat-rate completion”.
3. Confirm completion and optional `notes`.
4. Submit → success; refresh; entry appears.

**Alternate paths:**
- Not assigned → blocked with explicit message.
- Invalid hours → inline validation error; submit disabled until corrected.
- Work order not in eligible status → show blocked state and explain.
- Backend returns conflict (optimistic lock/version) → prompt to refresh and retry.

---

## 6. Functional Behavior

### Triggers
- User clicks **Record Labor** for a service item.

### UI actions
- Open labor entry form with:
  - Service item context header (read-only): service name/description, current item status, assigned technician (if available).
  - Input mode selector: `TIME_BASED` vs `FLAT_RATE`.
  - Inputs:
    - `hours` (only for TIME_BASED)
    - `notes` (optional, both)
  - Primary action: **Save Labor Entry**
  - Secondary action: **Cancel**

### State changes (frontend-visible)
- After successful save:
  - Labor entry list updates (via reload).
  - Work order/service item progress indicator updates (via reload).
  - If backend returns updated statuses, UI reflects them.

### Service interactions
- Load work order + items and existing labor entries on screen entry.
- Submit create labor entry on Save.
- If corrections are supported: submit “supersede” action that creates a new version/entry and marks prior as superseded (exact behavior depends on backend; see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **BR-L1:** Labor entry must be attributable to a technician and time → UI must send current user/technician identity if required by API OR rely on backend session identity (TBD).
- **BR-L2:** Support both flat-rate and time-based labor:
  - TIME_BASED requires `hours`.
  - FLAT_RATE requires `isComplete=true` (or equivalent).
- **BR-L3:** Negative or unrealistic hours:
  - Block `hours <= 0`.
  - “Unrealistic hours” threshold is undefined → UI will only enforce `> 0` and rely on backend for upper-bound validation (blocking clarification if threshold is required).
- **BR-L4:** Auditable and reversible only with permissions:
  - UI must not allow deleting or editing existing entries unless explicit permissions and endpoints exist.
  - If “correction” exists, it must be presented as “Create correction/superseding entry” and require reason/note as needed by backend (TBD).

### Enable/disable rules
- Disable “Record Labor” if:
  - work order status is not eligible (backend-provided eligibility preferred), OR
  - item is declined/cancelled/not executable, OR
  - item is in `PENDING_APPROVAL` (change request workflow invariant), OR
  - user not assigned and policy blocks (backend should return 403/validation; UI can pre-check if assignment data is present).

### Visibility rules
- Labor entry list visible when a service item is selected.
- Correction actions visible only if backend indicates permission and provides endpoint / capability flag.

### Error messaging expectations
- 403: “You don’t have access to record labor on this work order/item.”
- 409: “This work order changed since you opened it. Refresh and try again.”
- 400 validation: show field-level errors (hours, notes length, missing required fields).
- 404: “Work order or service item not found.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `WorkOrder`
- `WorkOrderService` (or `WorkorderItem` representing a service line)
- `WorkorderLaborEntry` (source entity per integration notes)
- `WorkOrderStateTransition` / audit view (read-only, if shown)

### Fields
#### Context (read-only in form)
- `workOrderId` (ID, required)
- `workOrderItemSeqId` / `serviceItemId` (ID, required; naming TBD)
- `workOrderStatus` (enum; used to gate actions)
- `itemStatus` (enum; used to gate actions)
- `assignedTechnicianId` (ID; used for gating/display if available)

#### Labor entry create (editable)
- `laborType` (enum: `TIME_BASED` | `FLAT_RATE`, required)
- `laborHours` (decimal, required if TIME_BASED; must be > 0)
- `isComplete` (boolean, required if FLAT_RATE; must be true)
- `notes` (string/text, optional; max length TBD)

#### Labor entry display (read-only list)
- `laborEntryId` (ID)
- `technicianId`
- `laborType`
- `laborHours`
- `isComplete`
- `notes`
- `entryTimestamp` / `createdAt`
- `version` (if exposed; helpful for idempotency/corrections)
- `supersededByLaborEntryId` or `status` (if exposed; TBD)

### Derived/calculated fields
- Display-friendly duration formatting for hours (e.g., `1.5` → “1.5 hours”).
- Progress indicator derived from backend fields (do not compute domain progress client-side).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not fully specified in provided inputs for frontend. The following is a *frontend-facing expectation* and may map to Moqui services that proxy the backend REST API.

### Load/view calls
- **Get Work Order details (incl. service items):**
  - `GET /api/workorders/{workOrderId}` *(path TBD; backend doc shows `/api/workorders/{id}/start` and `/api/work-orders/...` elsewhere; inconsistent)*
- **Get labor entries for a service item:**
  - Option A: included in work order detail response
  - Option B: `GET /api/workorders/{workOrderId}/items/{itemId}/labor-entries` (TBD)

### Create/update calls
- **Create labor entry**
  - `POST /api/workorders/{workOrderId}/items/{itemId}/labor-entries`
  - Request includes: `laborType`, `laborHours` (if TIME_BASED), `isComplete` (if FLAT_RATE), `notes`
  - Identity: inferred from auth context or explicit `technicianId` (TBD)
  - Response: created labor entry object (including `laborEntryId`, `version`)

### Submit/transition calls
- None required beyond create labor entry (unless backend also requires a work order transition on first labor; policy explicitly “deferred” in state machine doc; frontend should not auto-transition without explicit endpoint/capability).

### Error handling expectations
- 201/200 success: show confirmation and refresh work order/item + labor list.
- 400: map backend validation errors to form fields.
- 401: redirect to login/session expired flow per app convention.
- 403: show permission/assignment error.
- 409: prompt refresh.
- 5xx: show generic error + allow retry; log correlation id if provided.

---

## 10. State Model & Transitions

### Work order states (authoritative from `WORKORDER_STATE_MACHINE.md`)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Allowed states for recording labor (UI gating)
- **Eligible (assumed from “active work being performed”):**
  - `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`
- **Possibly eligible (needs clarification):**
  - `APPROVED`, `ASSIGNED` (start-eligible but not yet started)
- **Not eligible:**
  - `DRAFT`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

> Because eligibility is a domain policy decision, UI should prefer a backend-provided boolean like `canRecordLabor` at work order and/or item level. If absent, use the gating above but treat as provisional (see Open Questions).

### Role-based transitions
- This story does not introduce new work order transitions. It must not trigger `start` automatically unless explicitly required by backend policy.

### UI behavior per state
- If non-eligible: disable Record Labor and show tooltip/inline explanation using current status.
- If eligible but blocked by pending approvals/change requests (`PENDING_APPROVAL` items): disable and explain.

---

## 11. Alternate / Error Flows

### Validation failures
- TIME_BASED:
  - hours missing → inline error “Hours is required.”
  - hours <= 0 → inline error “Hours must be greater than 0.”
- FLAT_RATE:
  - completion not confirmed → disable Save until confirmed (or enforce boolean true).

### Concurrency conflicts
- If backend responds 409 due to optimistic locking/version mismatch:
  - show message
  - provide “Refresh Work Order” action that reloads data and reopens the form.

### Unauthorized access
- 403:
  - if due to assignment policy → message “You are not assigned…”
  - if due to permission → “You do not have permission…”

### Empty states
- No service items → show “No service items available for labor entry.”
- No labor entries yet → show “No labor recorded yet.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Record time-based labor successfully
Given I am logged in as a Technician  
And a work order exists in a labor-eligible status  
And the work order contains a service item eligible for labor recording  
When I open the service item and choose to record labor  
And I select "Time-based"  
And I enter "2.5" hours  
And I submit the labor entry  
Then a new labor entry is created for that service item with laborType "TIME_BASED" and laborHours "2.5"  
And the labor entry appears in the labor entries list after refresh  
And the work order/service item progress indicators refresh to reflect the updated labor state

### Scenario 2: Record flat-rate completion successfully
Given I am logged in as a Technician  
And a work order exists in a labor-eligible status  
And the work order contains a flat-rate service item eligible for labor recording  
When I open the service item and choose to record labor  
And I select "Flat-rate completion"  
And I confirm completion  
And I submit the labor entry  
Then a new labor entry is created for that service item with laborType "FLAT_RATE" and isComplete "true"  
And the labor entry appears in the labor entries list after refresh

### Scenario 3: Block negative or zero labor hours
Given I am logged in as a Technician  
And I am recording time-based labor for a service item  
When I enter "0" hours  
Then the Save action is disabled or the form shows a validation error for hours  
And no request is sent to create a labor entry  
When I enter "-1" hours  
Then the form shows a validation error for hours  
And I cannot submit the labor entry

### Scenario 4: Reject labor entry when technician is not assigned (policy-enforced)
Given I am logged in as a Technician  
And I am not assigned to the target work order or service item  
When I attempt to submit a labor entry  
Then the system rejects the request with an authorization/assignment error  
And I see the message "You are not assigned to this work order/item. Please see the Service Advisor."  
And no labor entry is added to the list

### Scenario 5: Work order in invalid state blocks labor recording
Given I am logged in as a Technician  
And a work order is in status "COMPLETED"  
When I view its service items  
Then the Record Labor action is disabled  
And the UI explains that labor cannot be recorded in status "COMPLETED"

### Scenario 6: Concurrency conflict requires refresh
Given I am logged in as a Technician  
And I have the labor entry form open for a service item  
When the backend responds with a 409 conflict while saving  
Then I see a message indicating the work order changed  
And I am offered an action to refresh and retry  
And no duplicate labor entry is shown

---

## 13. Audit & Observability

### User-visible audit data
- Show each labor entry with:
  - created timestamp
  - technician identifier (name if available; otherwise ID)
  - notes (if any)

### Status history
- If the backend exposes work order transition history, provide a link from work order detail to “Status History” (read-only). Not required to implement here unless already present.

### Traceability expectations
- Each labor entry displayed must include `laborEntryId` and (if available) `version` for support/debugging.
- On error responses, log (client-side) request id/correlation id header if provided by backend.

---

## 14. Non-Functional UI Requirements
- **Performance:** labor entry form opens within 300ms after data is loaded; submission shows loading state; refresh avoids full page reload if possible.
- **Accessibility:** all form fields have labels; validation errors are announced (aria) and associated with fields; keyboard navigable.
- **Responsiveness:** usable on tablet-sized devices (technician use case).
- **i18n/timezone:** display timestamps in local timezone with clear format; do not change stored UTC semantics.
- **Currency:** not applicable (no pricing display required).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messages for no service items / no labor entries; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-ERR-STD-MAP: Standard HTTP error-to-message mapping (400/401/403/404/409/5xx) without assuming domain policy; qualifies as safe error-handling; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend API contract:** What are the exact REST endpoints and payload schemas for:
   - loading work order + service items
   - listing labor entries for a service item
   - creating a labor entry
   - correcting/superseding an entry (if supported)?
2. **Identifier naming:** Is the service item identifier `workorderItemSeqId`, `serviceItemId`, or something else in the frontend/backend contract?
3. **Assignment policy UX:** When unassigned, should the UI *block* (no form) or *allow entry and let backend reject*? Is there a backend field/capability like `isAssignedToCurrentUser`?
4. **Labor eligibility statuses:** Can labor be recorded when work order is `APPROVED` or `ASSIGNED` (before `WORK_IN_PROGRESS`), or only during in-progress sub-statuses?
5. **“Unrealistic hours” rule:** Is there a numeric upper bound or heuristic (per entry or per day) that the UI must enforce, or is backend-only validation sufficient?
6. **Correction flow:** Are labor entries immutable with corrections via a new “superseding” entry endpoint? If yes, what permissions and required fields (reason code, note) are needed, and should technicians have access or only advisors?
7. **Technician identity:** Should `technicianId` be sent explicitly in the create request, or derived from the authenticated user session?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Record Labor Performed  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/223  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Record Labor Performed

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300002/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician

## Trigger
Technician performs labor associated with a workorder service item.

## Main Flow
1. Technician selects a labor/service line item.
2. Technician records time (start/stop or hours) or marks a flat-rate completion.
3. Technician adds notes/results (optional).
4. System validates permissions and records labor entry.
5. System updates workorder progress and completion indicators.

## Alternate / Error Flows
- Labor entry attempted without assignment → block or warn per policy.
- Negative or unrealistic hours → block and require correction.

## Business Rules
- Labor entries must be attributable to a technician and time.
- Support both flat-rate and time-based labor.
- Entries must be auditable and reversible only with permissions.

## Data Requirements
- Entities: Workorder, WorkorderItem, LaborEntry, AuditEvent
- Fields: workorderId, itemSeqId, technicianId, hours, flatRateFlag, notes, createdAt

## Acceptance Criteria
- [ ] Technicians can record labor entries on assigned workorders.
- [ ] Labor entries are auditable and tied to service items.
- [ ] Progress updates reflect labor completion.
- [ ] Labor entries emit LaborRecorded events when saved
- [ ] Labor events do not create AR or revenue
- [ ] Labor cost is available for job costing or WIP reporting
- [ ] Updates to labor entries supersede prior events
- [ ] Duplicate events do not create duplicate labor cost

## Integrations

### Accounting
- Emits Event: LaborRecorded
- Event Type: Non-posting (job cost / WIP tracking)
- Source Domain: workexec
- Source Entity: WorkorderLaborEntry
- Trigger: Labor entry recorded or completed
- Idempotency Key: workorderId + laborEntryId + version


## Notes for Agents
Even if prices are hidden, labor quantities must remain accurate for invoicing.


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