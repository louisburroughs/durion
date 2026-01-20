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
[FRONTEND] [STORY] Execution: Assign Technician to Workorder

### Primary Persona
Shop Manager / Dispatcher

### Business Value
Ensures each execution-ready work order has clear technician accountability, proper technician visibility in the POS, and a complete reassignment history for operational reporting and auditability.

---

## 2. Story Intent

### As a / I want / So that
**As a** Shop Manager / Dispatcher,  
**I want** to assign (and reassign) a primary technician (or crew) to a work order that is ready to be worked,  
**so that** the right technician can see and execute the work order and the assignment is tracked with an auditable history.

### In-scope
- View current assignment for a Work Order.
- Assign a technician to an assignable Work Order.
- Reassign a Work Order to a different technician while retaining history.
- Display assignment history (who, when, reason, unassigned time).
- Enforce authorization failures in the UI (no silent failures).
- Ensure assigned technician visibility is reflected in UI behavior (e.g., technician can see the work order after assignment) **to the extent supported by backend**.

### Out-of-scope
- Defining/implementing technician availability and schedule policy (warn vs block) (needs clarification).
- Implementing notification delivery channels or templates (optional behavior, needs clarification).
- Creating/updating Work Order status lifecycle beyond “assignment may move status to ASSIGNED” as provided by backend state machine doc; any additional transition policy not specified is out of scope.
- Backend entity/schema design (frontend will consume exposed APIs).

---

## 3. Actors & Stakeholders

- **Primary Actor:** Shop Manager / Dispatcher (assigns/reassigns).
- **Secondary Actor:** Technician (recipient of assignment; gains default visibility).
- **Stakeholders:**
  - Service Advisor (needs to see who is assigned).
  - Operations Manager (needs assignment history for metrics/audits).
- **System Actors/Dependencies:**
  - Authorization/RBAC system (permission checks).
  - Work Order state machine/audit trail (domain:workexec).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- A Work Order exists and is in an assignable state (exact statuses need confirmation; at minimum, a “Ready/Scheduled” concept exists).
- A list of eligible technicians exists.

### Dependencies (blocking where undefined)
- **Backend API contract for assignment** (endpoints, payloads, and error codes are not provided for frontend story; must be confirmed).
- **Definition of “assignable” Work Order statuses** for assignment action in UI (conflicts with workexec state machine list that does not include “SCHEDULED/READY”; needs clarification).
- **Crew assignment support**: story mentions “technician or crew” but data requirements list only `technicianId`; needs clarification.
- **Technician visibility mechanism**: whether visibility is implied by backend filtering, explicit ACL grants, or both; needs clarification.
- **Notification behavior** (optional) and its API; needs clarification.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order List screen: action “Assign Technician…” on each row or within Work Order detail.
- From Work Order Detail screen: “Assignment” section with current assignment and “Assign/Reassign” action.

### Screens to create/modify (Moqui)
- **Modify** `WorkOrderDetail` screen: add “Assignment” section:
  - current assigned technician (if any)
  - assign/reassign action
  - assignment history sub-panel/table
- **Modify** `WorkOrderList` screen: expose quick action to open assignment dialog (optional, can be deferred if detail screen is primary).
- **Create** `WorkOrderAssignDialog` (embedded screen/dialog):
  - technician selection control
  - optional reason field (see Open Questions—field exists in requirements)
  - submit/cancel actions

### Navigation context
- Users remain in Work Order context; assignment dialog returns to the same Work Order detail/list with refreshed data.
- After successful assignment, UI reflects:
  - assigned technician name
  - assignment timestamp
  - updated status badge if backend transitions status to `ASSIGNED`

### User workflows
- **Happy path (assign):**
  1. Open Work Order detail.
  2. Click “Assign Technician”.
  3. Select technician.
  4. (If required/available) enter reason.
  5. Submit → success toast/banner → refresh assignment + history.
- **Happy path (reassign):**
  1. Work Order already has active assignment.
  2. Click “Reassign”.
  3. Select a different technician.
  4. Submit → history shows prior assignment ended + new active assignment.
- **Alternate paths:**
  - Work Order not assignable → action hidden/disabled with explanation.
  - Unauthorized → action hidden; if attempted via direct URL/action, show forbidden.
  - Technician unavailable (policy-dependent) → warn or block (clarification required).

---

## 6. Functional Behavior

### Triggers
- User clicks “Assign/Reassign Technician” for a selected Work Order.

### UI actions
- Load eligible technicians list.
- Present selection UI (searchable dropdown recommended).
- Validate required fields client-side (selection; reason if required).
- Submit assignment request.
- Refresh Work Order summary and assignment history on success.

### State changes (frontend-observed)
- Work Order may transition to `ASSIGNED` upon successful assignment (per backend reference).
- Active assignment swaps on reassignment:
  - previous assignment gets an `unassignedAt`
  - new assignment created with `assignedAt`
- UI must treat assignment history as append-only (no delete/edit controls).

### Service interactions (high-level)
- Fetch Work Order detail (includes status and assignment info OR requires separate calls).
- Fetch technicians (eligible list).
- Create assignment / reassign (single endpoint or same endpoint with overwrite semantics).
- Fetch assignment history (if not included in Work Order detail).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- A technician must be selected to submit.
- Work Order must be in an assignable status; otherwise submit is blocked.
- Only one active assignment is allowed; UI should not attempt to create a second active assignment—backend is source of truth.
- If “reason” is required by backend/policy, UI must require non-empty input (currently unclear → Open Question).

### Enable/disable rules
- “Assign” button disabled until technician selected (and reason filled if required).
- If current status is not assignable, hide/disable assignment controls and show read-only messaging: “This work order cannot be assigned in its current status.”

### Visibility rules
- Assignment section visible to roles that can view Work Orders.
- Assignment action visible only if user has assignment permission (permission key needs clarification; backend ref mentions `WORKORDER_ASSIGN`).
- Assignment history visible read-only to permitted viewers.

### Error messaging expectations
- 403 → “You don’t have permission to assign technicians.”
- 404 → “Work order not found or you no longer have access.”
- 409/400 invalid state → “Work order is not in a valid status for assignment.”
- Technician eligibility failure (400) → “Selected user is not eligible to be assigned as a technician.”
- Availability-policy failure (409) → message depends on backend response; must surface backend message safely.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `WorkOrder` (work order being assigned)
- `TechnicianAssignment` (assignment history records)
- `User` (technician and assigner)
- `Notification` (only if implemented; currently optional/unclear)

### Fields (type, required, defaults)
**WorkOrder (read)**
- `workOrderId` (string/UUID/long; required)
- `status` (enum/string; required)
- `assignedTechnician` (object; optional)  
  - `technicianId` (id; required if present)  
  - `displayName` (string; required if present)
- `shopId/locationId` (id; optional but likely needed to filter technicians; unclear)

**TechnicianAssignment (read-only history list)**
- `workOrderId` (id; required)
- `technicianId` (id; required)
- `technicianName` (string; required for display)
- `assignedBy` (id; required)
- `assignedByName` (string; required for display)
- `assignedAt` (timestamp UTC; required)
- `unassignedAt` (timestamp UTC; nullable)
- `reason` (string; nullable unless policy says required)

**Assignment request (write)**
- `workOrderId` (path param or body; required)
- `technicianId` (required)
- `reason` (optional/required? → Open Question)

### Read-only vs editable by state/role
- Editable: only by users with assignment permission and only when Work Order status is assignable.
- Read-only: history is always read-only.

### Derived/calculated fields
- “Current Technician” is the record where `unassignedAt` is null (or `isActive=true` if backend uses that).
- “Assignment age” (optional UI) derived from `assignedAt` (safe ergonomic default if desired).

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: exact Moqui endpoints/services are not provided in inputs. Below defines required contracts the frontend needs. If the backend differs, adjust to actual services once confirmed.

### Load/view calls
1. **Get Work Order detail**
   - `GET /api/workorders/{workOrderId}`
   - Response must include at least: `workOrderId`, `status`, and current assignment summary (or enough to render current technician).
2. **Get assignment history**
   - Option A: included in Work Order detail as `assignments[]`
   - Option B: separate endpoint:
     - `GET /api/workorders/{workOrderId}/technician-assignments`

3. **List eligible technicians**
   - `GET /api/technicians?locationId={locationId}` (or equivalent)
   - Must return: `technicianId`, `displayName`, and optionally availability flags if policy exists.

### Create/update calls
- **Assign/Reassign technician**
  - Option A (preferred): `POST /api/workorders/{workOrderId}/assign-technician`
    - Body: `{ "technicianId": <id>, "reason": <string?> }`
  - Option B: `POST /api/technician-assignments`
    - Body: `{ "workOrderId": <id>, "technicianId": <id>, "reason": <string?> }`

### Submit/transition calls
- If assignment changes Work Order status to `ASSIGNED`, backend should do it atomically; frontend must not separately call a “transition status” endpoint for assignment.

### Error handling expectations
- `201/200` success → refresh Work Order + history.
- `400` validation → show field-level errors when possible; otherwise show banner.
- `403` forbidden → show permission error; keep UI read-only.
- `404` not found → navigate back to list with message.
- `409` conflict (invalid state, concurrency, availability blocking) → show conflict message and refresh Work Order.

---

## 10. State Model & Transitions

### Allowed states (as provided by authoritative workexec state machine doc)
WorkOrder statuses include:
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

### Role-based transitions (relevant to this story)
- Assignment action is expected to result in Work Order status `ASSIGNED` when successful (backend reference).
- Assignment should be allowed when work order is start-eligible or “ready/scheduled”; however the canonical state machine lists start-eligible as `APPROVED` and `ASSIGNED`. “SCHEDULED/READY_FOR_WORK” are mentioned in the backend story reference but not in the FSM doc → **clarification required**.

### UI behavior per state (minimum deterministic behavior)
- `DRAFT`: assignment controls hidden/disabled (unless clarified otherwise).
- `APPROVED`: assignment controls enabled (assumed based on “start-eligible” set including APPROVED; needs confirmation but is most consistent).
- `ASSIGNED`: reassign enabled; current technician visible.
- `WORK_IN_PROGRESS` and other in-progress states: reassign behavior unclear (often restricted) → Open Question; UI should default to disabled until clarified.
- `COMPLETED`/`CANCELLED`: assignment controls disabled; history still visible.

---

## 11. Alternate / Error Flows

### Validation failures
- No technician selected → inline validation; no request sent.
- Backend rejects due to invalid status → show conflict message and refresh.

### Concurrency conflicts
- If another user assigns/reassigns while dialog open:
  - On submit, backend returns `409` (or returns updated assignment).
  - UI shows “Assignment has changed; please review latest assignment.” then refresh and re-open dialog if user chooses.

### Unauthorized access
- User lacks permission:
  - UI hides assignment action where feasible.
  - If user navigates directly (deep link), call fails with `403`; show read-only page state.

### Empty states
- No eligible technicians returned:
  - Show “No technicians available to assign.” with guidance (e.g., “Check technician setup”) without inventing ownership.

---

## 12. Acceptance Criteria

### Scenario 1: Assign technician to an APPROVED work order
**Given** I am logged in as a Shop Manager/Dispatcher with permission to assign technicians  
**And** a Work Order exists with status `APPROVED`  
**When** I open the Work Order detail and assign Technician A  
**Then** the system records the assignment with `assignedAt` and `assignedBy`  
**And** the Work Order displays Technician A as the current assigned technician  
**And** the Work Order status is `ASSIGNED` (if backend transitions on assignment)  
**And** the assignment history shows a new active assignment entry for Technician A

### Scenario 2: Reassign technician preserves history
**Given** I am logged in with permission to assign technicians  
**And** a Work Order exists with status `ASSIGNED`  
**And** Technician A is currently assigned  
**When** I reassign the Work Order to Technician B  
**Then** the previous assignment entry for Technician A shows an `unassignedAt` timestamp  
**And** a new assignment entry exists for Technician B with `assignedAt` and no `unassignedAt`  
**And** the Work Order displays Technician B as current assigned technician

### Scenario 3: Unauthorized user cannot assign
**Given** I am logged in without the technician-assignment permission  
**And** a Work Order exists with status `APPROVED`  
**When** I attempt to access the assign action (via UI or direct request)  
**Then** the UI prevents assignment (action hidden or disabled)  
**And** if a request is attempted, the system responds with `403 Forbidden`  
**And** no assignment history changes are visible after refresh

### Scenario 4: Work order in non-assignable status is blocked
**Given** I am logged in with permission to assign technicians  
**And** a Work Order exists with status `COMPLETED` (or `CANCELLED`)  
**When** I view the Work Order  
**Then** assignment controls are disabled or not shown  
**And** the UI explains that assignment is not allowed in the current status  
**And** assignment history remains viewable

### Scenario 5: Backend rejects selected technician as ineligible
**Given** I am logged in with permission to assign technicians  
**And** a Work Order exists with an assignable status  
**When** I submit an assignment selecting a user that backend deems not eligible as a technician  
**Then** the UI shows a validation error message returned by the backend (sanitized)  
**And** the Work Order assignment remains unchanged after refresh

### Scenario 6: Technician availability policy enforcement (placeholder until clarified)
**Given** I am logged in with permission to assign technicians  
**And** a Work Order exists with an assignable status  
**When** I attempt to assign a technician who is marked unavailable by the backend policy  
**Then** the UI either (a) blocks assignment with a clear reason or (b) shows a warning allowing override **according to the clarified policy**  
**And** the final behavior matches the backend response codes and payloads

---

## 13. Audit & Observability

### User-visible audit data
- Show assignment history table with:
  - technician, assignedBy, assignedAt, unassignedAt, reason (if present)
- Show last updated time if provided by backend (optional).

### Status history
- If Work Order status changes to `ASSIGNED`, the UI should show updated status and allow viewing status transitions if such screen exists; otherwise, this story only reflects current status.

### Traceability expectations
- Each assignment/reassignment should be traceable in logs/audit by workOrderId and technicianId (frontend: include correlation/request ID if available in client infrastructure).
- UI should not allow deleting or editing historical assignment records.

---

## 14. Non-Functional UI Requirements

- **Performance:** technician list load < 2s under normal conditions; show loading indicator and allow retry.
- **Accessibility:** keyboard navigable dialog, labeled form controls, focus management on open/close; error messages announced to screen readers.
- **Responsiveness:** usable on tablet and desktop layouts (Quasar responsive components).
- **i18n/timezone:** display timestamps in store/user timezone if the app has a standard; otherwise display ISO/localized consistently (needs project convention confirmation).

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging when no technicians or no history entries are returned; qualifies as safe UI ergonomics; impacts UX Summary, Alternate/Empty states.
- SD-UX-LOADING-RETRY: Standard loading spinner and retry button on failed list/detail loads; safe error-handling ergonomics; impacts Service Contracts, Error Flows.
- SD-ERR-HTTP-MAP: Map HTTP 400/403/404/409/5xx to consistent banners/toasts without exposing internal details; safe because it doesn’t assume business policy beyond status codes; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Assignable statuses:** Which exact Work Order statuses allow assignment and reassignment? Inputs mention “Ready/Scheduled”, backend reference mentions `SCHEDULED/READY_FOR_WORK`, but the authoritative FSM doc enumerates `APPROVED` and `ASSIGNED` (no SCHEDULED).  
2. **Reassignment during execution:** Is reassignment allowed in `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`? If allowed, does it require additional reason/permission?  
3. **Crew assignment:** Does the system support assigning a “crew” (multiple technicians) or only a single primary technician? If crew is supported, what is the data model and UI behavior (one primary + secondary list, or many-to-many)?  
4. **Permission key / RBAC:** What is the exact permission/role required in the frontend to show/enable assignment controls (e.g., `WORKORDER_ASSIGN`)?  
5. **Backend API contract:** What are the exact endpoints, request/response schemas, and where does assignment history come from (embedded vs separate endpoint)?  
6. **Technician availability policy:** What is the system of record for availability and what is the required behavior: hard-block vs warn-and-override? What response shape indicates a warning vs an error?  
7. **Notification (optional):** Is notification required for this story? If yes, what channels (in-app/SMS/email), what triggers, and is it configurable per store/location/user? Is there a backend endpoint/event the frontend must call or is it automatic?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Assign Technician to Workorder  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/225  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Assign Technician to Workorder

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
Shop Manager / Dispatcher

## Trigger
A workorder is Ready/Scheduled and needs assignment.

## Main Flow
1. User selects a workorder and opens assignment controls.
2. User assigns a primary technician or crew.
3. System records assignment timestamp and assigns visibility to the technician.
4. System optionally notifies technician.
5. System records assignment history on reassignment.

## Alternate / Error Flows
- Technician unavailable → system prevents assignment or warns based on schedule policy.
- Unauthorized role tries assignment → block.

## Business Rules
- Assignment history must be retained.
- Workorder visibility is role-based.

## Data Requirements
- Entities: Workorder, TechnicianAssignment, User, Notification
- Fields: workorderId, technicianId, assignedBy, assignedAt, unassignedAt, reason

## Acceptance Criteria
- [ ] Technician can be assigned and sees the workorder.
- [ ] Assignment changes are tracked with history.
- [ ] Unauthorized users cannot assign.

## Notes for Agents
Assignment data feeds execution metrics; keep it clean and auditable.

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