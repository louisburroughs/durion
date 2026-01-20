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

**Title:** [FRONTEND] [STORY] Workexec: Display & Edit Work Order Assignment Context (Location/Resource/Mechanics) Until Work Starts

**Primary Persona:** Service Advisor / Shop Manager (POS user managing a work order)

**Business Value:** Ensures work order execution and reporting reflect the correct operational assignment context (location, bay/resource, assigned mechanics) and preserves an audit trail for pre-start changes.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor / Shop Manager  
- **I want** to view and update a Work Order’s assignment context (location, resource, mechanics) before work begins  
- **So that** the correct operational context is available to technicians and reporting, and changes are traceable.

### In-scope
- Show assignment context fields on Work Order detail.
- Allow updates **only while the Work Order is start-eligible / pre-start** (per workexec state machine gating).
- Persist updates through a backend service call and show user-friendly validation/errors.
- Display audit/transition history relevant to assignment-context changes (as available from backend).

### Out-of-scope
- Consuming `AssignmentUpdated` events from shopmgr in the frontend (system-to-system integration).
- Configuration of locations/resources/mechanics master data (owned by other domains).
- Defining mechanic authorization levels or permissions policy (security domain).
- Changing/creating new work order states beyond documented workexec FSM.

---

## 3. Actors & Stakeholders

- **Service Advisor / Shop Manager (primary user):** edits assignment context pre-start.
- **Mechanic (consumer):** uses assignment context; expects it to be immutable once work starts.
- **System (Workexec backend):** enforces state gating, auditing, and persistence.
- **Audit/Compliance stakeholder:** relies on immutable audit trail of changes.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- A Work Order exists and can be loaded by ID.
- Work Order has a `status` field aligned to workexec FSM (`DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, etc.).

### Dependencies (blocking if missing)
- Backend endpoint(s) to:
  - Load Work Order details including current assignment context and status.
  - Update assignment context (locationId/resourceId/mechanicIds) with state gating.
  - Retrieve audit/transition history for display (optional but story requires “audit maintained” in UX).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order list → select a Work Order → navigate to Work Order detail screen.
- Direct route deep link: `/workorders/:workOrderId` (exact routing TBD per repo conventions).

### Screens to create/modify
- **Modify** existing Work Order detail screen to include an **“Assignment Context”** section.
- **Optional add** a dedicated sub-screen: `workorders/WorkOrderDetail/AssignmentContext` if the project uses sub-tabs.

### Navigation context
- Work Order detail is the parent context; assignment context is a section/tab within it.

### User workflows
#### Happy path (pre-start edit)
1. User opens Work Order detail.
2. User views current `locationId`, `resourceId`, `mechanicIds`.
3. If Work Order status is **start-eligible / pre-start**, user clicks “Edit”.
4. User selects/enters new values and saves.
5. UI confirms success and refreshes displayed values and audit/history section.

#### Alternate path (read-only after start)
1. User opens a Work Order already started (`WORK_IN_PROGRESS` or later).
2. Assignment context displays as read-only with a note: “Assignment context locked after work starts.”

#### Alternate path (backend rejects due to state race)
1. User opens Work Order in `ASSIGNED`, starts edit.
2. Another actor starts the Work Order (status becomes `WORK_IN_PROGRESS`).
3. User clicks save.
4. Backend returns conflict/validation; UI shows error and refreshes the Work Order.

---

## 6. Functional Behavior

### Triggers
- Screen load triggers retrieval of Work Order detail (status + assignment context).
- Save action triggers update call for assignment context.

### UI actions
- View assignment context fields.
- “Edit” toggles form state.
- “Save” submits changes.
- “Cancel” reverts unsaved changes.

### State changes (frontend)
- Local UI state: view mode ↔ edit mode.
- After save: re-fetch Work Order to reflect server truth.

### Service interactions
- Load: `GET WorkOrder` (details)
- Update: `POST/PUT WorkOrder assignment context` (exact endpoint TBD)
- Optional audit: `GET WorkOrder transitions/history` and/or `GET assignment sync log`

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Client-side validation (only ergonomic; backend is authoritative):
  - `locationId` optional/required is **unknown** → do not enforce requiredness unless backend indicates.
  - `mechanicIds` may be empty → allow empty unless backend rejects.
- Always validate types (IDs are string/UUID-like).

### Enable/disable rules
- **Editable only when Work Order is pre-start.**
  - Based on documented FSM: **start-eligible statuses are `APPROVED`, `ASSIGNED`**.
  - If status is anything else (including `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`), disable editing.

### Visibility rules
- Assignment Context section always visible.
- Edit controls visible only when status is `APPROVED` or `ASSIGNED` (unless overridden by permissions; see Open Questions).

### Error messaging expectations
- If backend rejects due to invalid status: show “Cannot update assignment context after work has started.”
- If backend returns 401/403: show “You do not have permission to update assignment context.”
- If backend returns 404: show “Work order not found.”
- If backend returns 409: show “Work order changed since you opened it. Reloading…”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `WorkOrder` (workexec-owned)
- `WorkOrderStateTransition` (audit trail; if exposed)
- Potentially `OperationalContext` / `AssignmentSyncLog` (mentioned in original synopsis; backend reference uses audit log concept)

### Fields
**WorkOrder**
- `id` (string/UUID, required, read-only)
- `status` (enum string, required, read-only)
- `locationId` (string/UUID, nullable, editable pre-start)
- `resourceId` (string/UUID, nullable, editable pre-start)
- `mechanicIds` (array of string/UUID, nullable/empty, editable pre-start)

**Audit/History (if available)**
- `transitionedAt` (datetime UTC, read-only)
- `transitionedBy` (user id/string, read-only)
- `fromStatus`, `toStatus` (enum string)
- `reason` (string, optional)

### Read-only vs editable by state/role
- By **state**: fields editable only in `APPROVED`/`ASSIGNED`.
- By **role**: unknown; do not implement role gating beyond what backend enforces (see Open Questions).

### Derived/calculated fields
- `isAssignmentEditable` derived from `status in [APPROVED, ASSIGNED]`.

---

## 9. Service Contracts (Frontend Perspective)

> Exact Moqui service names / REST paths are not provided in the inputs. This section defines required contracts; implementation must map to actual endpoints in the repo.

### Load/view calls
- `GET /api/workorders/{id}`
  - Response must include: `id`, `status`, `locationId`, `resourceId`, `mechanicIds`

### Create/update calls
- `PUT /api/workorders/{id}/assignment-context` (or equivalent)
  - Request body:
    ```json
    {
      "locationId": "uuid-or-null",
      "resourceId": "uuid-or-null",
      "mechanicIds": ["uuid", "uuid"]
    }
    ```
  - Success: 200 with updated WorkOrder (preferred) or 204 + subsequent GET

### Submit/transition calls
- None in this story (no start/stop transitions implemented here).

### Error handling expectations
- `400` validation error → show field-level or banner message from backend.
- `401/403` → show permission error; keep fields read-only.
- `404` → show not found page/state.
- `409` → show conflict banner; refresh Work Order from server.
- `5xx`/network → show retry affordance; do not lose unsaved local draft until user cancels.

---

## 10. State Model & Transitions

### Allowed states (from workexec FSM reference)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Role-based transitions
- Not in scope; only state-based edit gating is implemented in UI.

### UI behavior per state
- `APPROVED`, `ASSIGNED`: assignment context section editable.
- All other states: read-only; show locked message.

---

## 11. Alternate / Error Flows

### Validation failures
- Backend rejects missing/invalid IDs: highlight relevant field(s) if error provides field mapping; otherwise show banner.

### Concurrency conflicts
- If status changes between load and save, backend returns 409 or 400; UI:
  - shows message indicating status changed,
  - reloads Work Order and exits edit mode.

### Unauthorized access
- If user cannot view Work Order: show access denied.
- If user can view but cannot edit: hide/disable edit controls; show tooltip/banner on attempt.

### Empty states
- If assignment context is null/empty: display “Not assigned yet” for each field.
- If mechanicIds empty: show “No mechanics assigned”.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View assignment context on a work order
**Given** I am an authenticated user  
**And** a Work Order exists with status `APPROVED`  
**When** I open the Work Order detail screen  
**Then** I see the Assignment Context fields `locationId`, `resourceId`, and `mechanicIds` (or “Not assigned yet” when null)  

### Scenario 2: Edit assignment context when work order is pre-start
**Given** I am an authenticated user with permission to edit assignment context  
**And** a Work Order exists with status `ASSIGNED`  
**When** I enter edit mode in the Assignment Context section  
**And** I change `locationId`, `resourceId`, and `mechanicIds`  
**And** I save  
**Then** the frontend sends an update request containing the full context object  
**And** the UI shows the updated values after success  
**And** the UI refreshes the Work Order from the server  

### Scenario 3: Editing is disabled after work starts
**Given** I am an authenticated user  
**And** a Work Order exists with status `WORK_IN_PROGRESS`  
**When** I open the Work Order detail screen  
**Then** the Assignment Context section is read-only  
**And** I do not see enabled edit/save controls  
**And** I see messaging that assignment context is locked after work starts  

### Scenario 4: Save rejected because status changed to in-progress during edit
**Given** I am editing assignment context for a Work Order currently shown as `ASSIGNED`  
**And** the Work Order status changes on the server to `WORK_IN_PROGRESS` before I save  
**When** I click Save  
**Then** the UI displays an error indicating the update cannot be applied after work started  
**And** the UI reloads the Work Order and exits edit mode  
**And** the displayed assignment context matches the server response  

### Scenario 5: Audit/history is accessible after a successful update (if endpoint exists)
**Given** I updated assignment context successfully for a pre-start Work Order  
**When** I view the History/Audit section on the Work Order detail  
**Then** I can see an entry indicating assignment context changed, including timestamp and actor (as provided by backend)  

---

## 13. Audit & Observability

### User-visible audit data
- Show a “History” section (or reuse existing one) that can list:
  - status transitions (from `GET /workorders/{id}/transitions`), and/or
  - assignment-context change audit events (if provided by backend).

### Status history
- Display transition list sorted newest-first with UTC timestamps rendered in user locale.

### Traceability expectations
- On successful save, include correlation/request ID in console logs (frontend) if available via headers.
- Do not display sensitive internal identifiers beyond IDs already shown.

---

## 14. Non-Functional UI Requirements

- **Performance:** Work Order detail load within 2s on typical broadband for single Work Order; history loads lazily if large.
- **Accessibility:** All form controls keyboard-navigable; labels associated; error messages announced via ARIA live region where applicable.
- **Responsiveness:** Assignment section usable on tablet widths (shop floor use).
- **i18n/timezone:** Display timestamps in local timezone; store/submit timestamps not required for this story.
- **Security:** Do not cache sensitive data beyond session; respect backend authorization responses.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Show “Not assigned yet” for null `locationId`/`resourceId` and “No mechanics assigned” for empty `mechanicIds`; qualifies as safe because it is purely presentational and does not change domain behavior; impacted sections: UX Summary, Alternate/Empty states.
- SD-ERR-HTTP-MAP: Standard HTTP error-to-message mapping (400/401/403/404/409/5xx) without inventing domain policy; qualifies as safe because it follows generic transport semantics; impacted sections: Business Rules, Service Contracts, Error Flows.

---

## 16. Open Questions

1. **Backend contract:** What are the exact Moqui-facing endpoints/services for:
   - loading Work Order detail,
   - updating assignment context,
   - fetching audit/history for assignment-context changes?
2. **ID types & lookup UX:** Are `locationId`, `resourceId`, `mechanicIds` free-entry IDs, or should the UI provide searchable pickers backed by lookup endpoints? If pickers, what are the lookup APIs and display fields?
3. **Permission model:** Which roles are allowed to edit assignment context (Service Advisor only? Shop Manager? Dispatcher)? Should UI hide or disable edit controls based on a permission flag from backend?
4. **State gating nuance:** Is editability strictly limited to `APPROVED` and `ASSIGNED` only (per FSM “start-eligible”), or is `DRAFT` also editable? The current FSM doc defines start-eligible, but the story says “updates allowed until work starts.”
5. **Audit display source:** Should the frontend display:
   - generic work order transition history only,
   - a specific “AssignmentSyncLog” list,
   - or both?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Propagate Assignment Context to Workorder  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/128  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Propagate Assignment Context to Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want workorders to carry location/resource/mechanic context from shopmgr so that execution and reporting are accurate.

## Details
- Attach operational context at WO creation.
- Updates allowed until work starts.

## Acceptance Criteria
- Workorder has locationId/resourceId/mechanicIds.
- Updates applied pre-start.
- Audit maintained.

## Integrations
- Shopmgr emits AssignmentUpdated; workexec applies update rules; workexec emits StatusChanged.

## Data / Entities
- OperationalContext, AssignmentSyncLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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