## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** workexec-structured

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Execution: Request Additional Work and Flag for Approval

## Primary Persona
- Technician (primary creator)
- Service Advisor (primary reviewer/decider)

## Business Value
Prevents revenue leakage and compliance risk by enforcing an approval gate for out-of-scope work, preserving traceability from requested items → approval artifact → billable work order scope.

---

# 2. Story Intent

## As a / I want / So that
- **As a Technician**, I want to request additional parts/labor on an in-progress work order and flag emergency/safety exceptions so that out-of-scope work is reviewed and properly approved/declined with auditability.
- **As a Service Advisor**, I want to review and approve/decline change requests with a required decision note so that customer approval is captured and execution/billing controls are enforced.

## In-Scope
- Create a Change Request for a specific Work Order (must be `IN_PROGRESS`).
- Add requested items (services and/or parts) to the Change Request.
- Mark items as Emergency/Safety and enforce required documentation (photo evidence OR photo-not-possible + notes).
- View Change Request list and detail for a Work Order.
- Advisor decision actions: Approve / Decline with required approval note (approval artifact).
- Emergency declined acknowledgment action (record customer denial acknowledgment) and show “can close” gating.
- Enforce “blocked from execution” UX cues for items in `PENDING_APPROVAL` or `CANCELLED`.

## Out-of-Scope
- Upload/storage implementation for photos (only capture a URL/identifier if already available).
- Notification delivery mechanics to advisors (only reflect server state; no bespoke push).
- Pricing calculations, taxes, invoice generation, inventory consumption execution flows (must only respect statuses returned).
- Configuration of approval methods/location policy; use backend behavior.

---

# 3. Actors & Stakeholders
- **Technician**: creates Change Requests; flags emergency/safety details.
- **Service Advisor**: reviews Change Requests; records approval/decline note; acknowledges customer denial for declined emergency requests.
- **Customer**: external approver/decliner (captured indirectly via advisor note).
- **Auditor/Manager**: expects immutable, traceable records (notes, timestamps, state history).

---

# 4. Preconditions & Dependencies
## Preconditions
- User is authenticated.
- Work Order exists and is accessible to the user.
- Work Order status is `IN_PROGRESS` (per backend validation).
- Backend endpoints described in `CHANGE_REQUEST_WORKFLOW.md` are available.

## Dependencies
- Backend REST APIs:
  - `POST /api/work-orders/{workOrderId}/change-requests`
  - `GET /api/work-orders/{workOrderId}/change-requests`
  - `GET /api/change-requests/{id}`
  - `POST /api/change-requests/{id}/approve`
  - `POST /api/change-requests/{id}/decline`
  - `POST /api/change-requests/{id}/acknowledge-denial`
  - `GET /api/work-orders/{workOrderId}/can-close`
- Work Order screen/detail exists in Moqui frontend and provides `workOrderId` context.
- Role/permission enforcement is performed server-side; frontend must handle 401/403 cleanly.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Work Order Detail** (workexec context): action “Request Additional Work”.
- From **Work Order Detail**: section/tab “Change Requests” listing existing requests.

## Screens to create/modify
1. **Modify**: Work Order Detail screen
   - Add navigation to Change Requests list
   - Add action to open Create Change Request flow
   - Add “Can Close Work Order” indicator (based on `GET /can-close`)
2. **Create**: `workorder/changeRequest/Create` (screen/form)
3. **Create**: `workorder/changeRequest/List` (screen)
4. **Create**: `workorder/changeRequest/Detail` (screen)
   - Includes decision actions for advisor (approve/decline)
   - Includes emergency denial acknowledgment (when applicable)

## Navigation context
- All screens are scoped to a single `workOrderId` except Change Request detail which is scoped to `changeRequestId` and shows linked `workOrderId`.

## User workflows
### Happy path (Technician)
1. Open Work Order Detail (`IN_PROGRESS`)
2. Click “Request Additional Work”
3. Fill description, add at least one item (service and/or part)
4. Optionally flag emergency/safety on item(s) with required evidence/notes
5. Submit → returns created Change Request in `AWAITING_ADVISOR_REVIEW` and items `PENDING_APPROVAL`
6. User is routed to Change Request detail

### Happy path (Advisor)
1. Open Work Order Detail → Change Requests list → select one `AWAITING_ADVISOR_REVIEW`
2. Review description + requested items
3. Enter decision note (required)
4. Click Approve (or Decline)
5. System updates Change Request status and item statuses accordingly; detail refresh shows updated timestamps and note

### Alternate (Emergency declined acknowledgment)
1. Advisor declines an emergency exception request
2. Work Order “Can Close” becomes false until acknowledgment is recorded
3. Advisor triggers “Acknowledge Customer Denial” on the declined Change Request
4. Work Order “Can Close” becomes true (assuming no other blockers)

---

# 6. Functional Behavior

## Triggers
- Technician initiates change request creation from a Work Order.
- Advisor initiates approve/decline from Change Request detail.
- Advisor initiates acknowledgment from Change Request detail (only when emergency exception + declined).

## UI actions
### Create Change Request (Technician)
- Action: Add Service line (references `serviceEntityId`)
- Action: Add Part line (references `productEntityId` + `quantity`)
- Action: Mark line as emergency/safety (boolean)
  - Provide either:
    - `photoEvidenceUrl` OR
    - `photoNotPossible=true` AND `emergencyNotes` (required)
- Action: Submit

### Review/Decide (Advisor)
- Action: Approve with required `approvalNote`
- Action: Decline with required `approvalNote`
- Action: Acknowledge denial (no body per spec)

## State changes (as reflected in UI)
- On create:
  - ChangeRequest: `AWAITING_ADVISOR_REVIEW`
  - Items: `PENDING_APPROVAL`
- On approve:
  - ChangeRequest: `APPROVED`
  - Items: `READY_TO_EXECUTE`
- On decline:
  - ChangeRequest: `DECLINED`
  - Items: `CANCELLED`
- On acknowledge-denial:
  - For emergency/safety items: `customerDenialAcknowledged=true` (reflected on item rows)

## Service interactions (high level)
- Load Work Order context and change requests list.
- POST create request with nested items payload.
- POST approve/decline with approval note and advisor identity fields as required.
- POST acknowledge denial.
- Refresh detail/list after every mutation.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
### Create Change Request
- **Description is required** (non-empty).
- Must include **at least one** service or part item.
- If `isEmergencyException=true`:
  - At least one requested item must be `isEmergencySafety=true`.
- For each item where `isEmergencySafety=true`:
  - Require (`photoEvidenceUrl` present) OR (`photoNotPossible=true` AND `emergencyNotes` non-empty).

### Approve/Decline
- Only allowed when Change Request status is `AWAITING_ADVISOR_REVIEW`.
- `approvalNote` is required for both approve and decline.
- `approvedBy` is required for approve payload (per backend doc).

### Acknowledge denial
- Only allowed when:
  - Change Request `isEmergencyException=true`
  - Change Request status is `DECLINED`

## Enable/disable rules
- Disable Submit if create-form validations fail.
- Disable Approve/Decline buttons if status != `AWAITING_ADVISOR_REVIEW`.
- Disable Acknowledge button unless emergency+declined.
- Show read-only view for requests in terminal states (APPROVED/DECLINED/CANCELLED).

## Visibility rules
- Show “Emergency Exception” section only if toggled or any item is flagged emergency/safety.
- Show customer denial acknowledgment status only when `isEmergencyException=true`.

## Error messaging expectations
- 400: show validation message(s) inline at form top and per-field where possible.
- 403: show “You don’t have permission to perform this action.”
- 409: show “This request was updated by someone else. Refresh and try again.”
- 404: show “Change request/work order not found.”

---

# 8. Data Requirements

## Entities involved (frontend view-model)
- `ChangeRequest`
- `WorkOrder`
- `WorkOrderService` (as change request item)
- `WorkOrderPart` (as change request item)

## Fields (type, required, defaults)

### ChangeRequest
- `id` (number, read-only)
- `workOrderId` (number, required, read-only from route)
- `requestedByUserId` (number, required; populated from session/user context if available, else required input **(see Open Questions: identity source)**)
- `requestedAt` (datetime UTC, read-only)
- `status` (enum: `AWAITING_ADVISOR_REVIEW|APPROVED|DECLINED|CANCELLED`, read-only except via transitions)
- `description` (string, required)
- `isEmergencyException` (boolean, optional default false)
- `exceptionReason` (string, required iff `isEmergencyException=true`)
- `approvalNote` (string, required for approve/decline, read-only otherwise)
- `approvedAt` (datetime, read-only)
- `approvedBy` (number, required for approve action)
- `declinedAt` (datetime, read-only)
- `supplementalEstimatePdfId` (number, read-only; used to link/view supplemental estimate if frontend has a PDF viewer route)

### Service item (WorkOrderService-like payload)
- `serviceEntityId` (number, required)
- `status` (enum: `PENDING_APPROVAL|READY_TO_EXECUTE|OPEN|IN_PROGRESS|COMPLETED|CANCELLED`; read-only in this story)
- `changeRequestId` (number, read-only after create)
- `isEmergencySafety` (boolean, optional default false)
- `photoEvidenceUrl` (string/url, required iff emergency and `photoNotPossible!=true`)
- `photoNotPossible` (boolean, optional default false)
- `emergencyNotes` (string, required iff emergency and (`photoNotPossible=true` OR `photoEvidenceUrl` present?); backend requires notes at least when photo not possible; UI requires notes whenever emergency for audit clarity **(see Applied Safe Defaults)**)
- `customerDenialAcknowledged` (boolean, read-only)

### Part item (WorkOrderPart-like payload)
- `productEntityId` (number, required)
- `quantity` (number, required, min 1)
- Same emergency fields as service items.

## Read-only vs editable by state/role
- Technician: can create only; after creation, all fields are read-only.
- Advisor: can add approval note and decide (approve/decline); cannot edit requested items in this story.
- Acknowledge denial: advisor-only (assumed enforced by backend).

## Derived/calculated fields
- “Can close work order” boolean derived from `GET /api/work-orders/{workOrderId}/can-close`.
- Item counts by status derived client-side for display (optional).

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls
1. List change requests for a work order  
   - `GET /api/work-orders/{workOrderId}/change-requests`  
   - Expect: array of ChangeRequest summaries or full objects
2. Get change request detail  
   - `GET /api/change-requests/{id}`  
   - Expect: ChangeRequest including associated items (services/parts). If backend returns items separately, frontend must call additional endpoints **(Open Question if not included)**.
3. Can-close gating  
   - `GET /api/work-orders/{workOrderId}/can-close` → boolean

## Create/update calls
1. Create change request  
   - `POST /api/work-orders/{workOrderId}/change-requests`  
   - Request body (per backend doc; include only fields relevant):
     ```json
     {
       "requestedByUserId": 123,
       "description": "string",
       "isEmergencyException": true,
       "exceptionReason": "string",
       "services": [
         {
           "serviceEntityId": 456,
           "isEmergencySafety": true,
           "photoEvidenceUrl": "https://...",
           "photoNotPossible": false,
           "emergencyNotes": "string"
         }
       ],
       "parts": [
         {
           "productEntityId": 789,
           "quantity": 1,
           "isEmergencySafety": false
         }
       ]
     }
     ```
   - Response: created ChangeRequest (prefer) or `{id: ...}`; on success route to detail.

## Submit/transition calls
1. Approve  
   - `POST /api/change-requests/{id}/approve`
   - Body:
     ```json
     { "approvedBy": 789, "approvalNote": "string" }
     ```
2. Decline  
   - `POST /api/change-requests/{id}/decline`
   - Body:
     ```json
     { "approvalNote": "string" }
     ```
3. Acknowledge denial  
   - `POST /api/change-requests/{id}/acknowledge-denial`
   - No body

## Error handling expectations
- Map HTTP codes:
  - 400 → show validation errors (if payload includes field errors, bind; else show message)
  - 401 → redirect to login
  - 403 → show permission dialog/toast; keep user on page
  - 404 → show not found page
  - 409 → show concurrency message + offer refresh
  - 5xx → show generic error + correlation id if available in headers/body

---

# 10. State Model & Transitions

## ChangeRequest allowed states
- `AWAITING_ADVISOR_REVIEW` (initial)
- `APPROVED` (terminal)
- `DECLINED` (terminal; may require acknowledgment if emergency exception)
- `CANCELLED` (terminal; created but cancelled before decision via system action—UI read-only)

## Role-based transitions (UI gating; server is source of truth)
- Technician:
  - Can create (implicit transition to `AWAITING_ADVISOR_REVIEW`)
- Service Advisor:
  - `AWAITING_ADVISOR_REVIEW` → `APPROVED` (Approve)
  - `AWAITING_ADVISOR_REVIEW` → `DECLINED` (Decline)
  - `DECLINED` + emergency exception → acknowledgment action (does not change ChangeRequest status; updates item fields)

## UI behavior per state
- `AWAITING_ADVISOR_REVIEW`: show decision actions (advisor), show “execution blocked” banner for items.
- `APPROVED`: show decision note + approved timestamp/by; items shown as `READY_TO_EXECUTE`.
- `DECLINED`: show decision note + declined timestamp; items shown as `CANCELLED`; if emergency exception, show acknowledgment status and action if not acknowledged.
- `CANCELLED`: show read-only; no actions.

---

# 11. Alternate / Error Flows

## Validation failures
- Missing description → inline error; block submit.
- No items → inline error; block submit.
- Emergency item missing evidence and photoNotPossible not checked → inline error on item row.
- Emergency item photoNotPossible checked but missing notes → inline error.

## Concurrency conflicts
- Advisor opens request; another advisor approves; first advisor attempts decline → backend returns 409; UI shows “already decided” and refreshes detail.

## Unauthorized access
- Technician tries to approve/decline → 403; hide buttons proactively if role info available; otherwise handle response.

## Empty states
- Change Requests list empty → show “No change requests yet” and a create action if user can create.

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Technician creates a change request (happy path)
Given a Technician is viewing a Work Order with status "IN_PROGRESS"  
When the Technician submits a Change Request with a non-empty description and at least one requested service or part  
Then the system creates the Change Request with status "AWAITING_ADVISOR_REVIEW"  
And all requested items are created with status "PENDING_APPROVAL"  
And the user is navigated to the Change Request detail view showing the created request and items.

## Scenario 2: Create change request blocked by missing description
Given a Technician is on the Create Change Request screen  
When the Technician leaves Description empty and attempts to submit  
Then the UI blocks submission  
And shows a required-field validation error for Description.

## Scenario 3: Create change request blocked by missing items
Given a Technician is on the Create Change Request screen  
When the Technician provides a Description but adds no services and no parts and attempts to submit  
Then the UI blocks submission  
And shows an error stating at least one item is required.

## Scenario 4: Emergency/safety item requires documentation
Given a Technician is creating a Change Request  
When the Technician marks an item as Emergency/Safety  
And does not provide photoEvidenceUrl  
And does not select "photo not possible" with notes  
Then the UI blocks submission  
And shows an error indicating emergency items require photo evidence or "photo not possible" with notes.

## Scenario 5: Advisor approves with required note
Given a Service Advisor is viewing a Change Request with status "AWAITING_ADVISOR_REVIEW"  
When the Advisor enters an approval note and clicks Approve  
Then the system updates the Change Request status to "APPROVED"  
And the UI refreshes to display approved timestamps/user  
And associated items display status "READY_TO_EXECUTE".

## Scenario 6: Advisor cannot approve without note
Given a Service Advisor is viewing a Change Request with status "AWAITING_ADVISOR_REVIEW"  
When the Advisor clicks Approve without entering an approval note  
Then the UI blocks the action  
And shows a required-field validation error for the approval note.

## Scenario 7: Advisor declines additional work
Given a Service Advisor is viewing a Change Request with status "AWAITING_ADVISOR_REVIEW"  
When the Advisor enters a decline note and clicks Decline  
Then the system updates the Change Request status to "DECLINED"  
And associated items display status "CANCELLED"  
And the UI indicates the items are not billable/executable.

## Scenario 8: Emergency declined requires customer denial acknowledgment before close
Given a Work Order has a declined Change Request flagged as emergency exception  
And at least one emergency/safety item has customerDenialAcknowledged = false  
When the user checks the work order close eligibility  
Then `GET /api/work-orders/{workOrderId}/can-close` returns false  
And the UI indicates the work order cannot be closed until customer denial is acknowledged.

## Scenario 9: Advisor records customer denial acknowledgment
Given a Change Request is flagged as emergency exception and has status "DECLINED"  
When the Advisor triggers "Acknowledge Customer Denial"  
Then the system records acknowledgment for all emergency/safety items for that change request  
And `GET /api/work-orders/{workOrderId}/can-close` eventually returns true (if no other blockers)  
And the UI reflects acknowledgment as completed.

---

# 13. Audit & Observability

## User-visible audit data
- On Change Request detail, display:
  - requestedBy, requestedAt
  - status + approvedAt/approvedBy or declinedAt (if provided)
  - approvalNote (decision artifact)
  - emergency exception reason (if any)

## Status history
- If backend exposes history, display it; otherwise at minimum show current status and timestamps present on entity.

## Traceability expectations
- Each item row must show it is linked to the Change Request (via `changeRequestId`) and show its current item status.
- Supplemental estimate PDF:
  - If `supplementalEstimatePdfId` exists, show a “View Supplemental Estimate” link that navigates to existing PDF-view capability (or downloads) **(Open Question if no PDF viewer route exists)**.

---

# 14. Non-Functional UI Requirements

- **Performance**: List and detail loads should complete within 2s on typical LAN; avoid redundant refresh loops (debounce can-close checks).
- **Accessibility**: All actions reachable via keyboard; validation errors announced via ARIA; sufficient contrast.
- **Responsiveness**: Mobile-friendly forms; item rows usable on tablet.
- **i18n/timezone**: Display timestamps in user’s locale but store/submit UTC as provided by backend; no currency handling required in this story.

---

# 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE-001**
  - Assumed: Provide standard empty-state messaging and a primary action (“Request Additional Work”) when list is empty.
  - Why safe: Pure UI ergonomics; does not change domain policy or state.
  - Impacted sections: UX Summary, Alternate / Error Flows.

- **SD-ERR-HTTP-MAP-001**
  - Assumed: Standard mapping of 400/401/403/404/409/5xx to user-facing messages and refresh suggestions.
  - Why safe: Error mapping is presentation-layer behavior implied by REST semantics.
  - Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows.

- **SD-AUDIT-NOTES-EMERGENCY-001**
  - Assumed: Require `emergencyNotes` whenever an item is marked Emergency/Safety (even if photoEvidenceUrl is present).
  - Why safe: Adds documentation rigor without altering approvals/state machine; backend already requires notes in some emergency cases.
  - Impacted sections: Business Rules, Acceptance Criteria, Data Requirements.

---

# 16. Open Questions
- none

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Request Additional Work and Flag for Approval  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/220  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Request Additional Work and Flag for Approval

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
Technician / Service Advisor

## Trigger
Technician identifies additional work required beyond authorized scope.

## Main Flow
1. Technician creates a change request describing additional parts/labor needed.
2. System generates a supplemental estimate view for the additional work.
3. System routes request to advisor for customer approval using the approval capability.
4. System prevents execution of additional billable items until approved (policy).
5. Once approved, system adds authorized items to the workorder with traceability.

## Alternate / Error Flows
- Customer declines additional work → request closed; workorder continues with original scope.
- Emergency/safety exception → policy may allow proceed with documentation.

## Business Rules
- Additional work requires explicit customer approval unless exception policy applies.
- Change requests must be traceable and auditable.
- Added items must reference approval artifacts.

## Data Requirements
- Entities: ChangeRequest, Estimate, ApprovalRecord, WorkorderItem
- Fields: changeRequestId, description, requestedBy, requestedAt, approvalId, addedItemSeqIds, exceptionReason

## Acceptance Criteria
- [ ] Change requests can be created and tracked.
- [ ] Approval gate blocks unauthorized scope expansion.
- [ ] Approved additional work is added with traceability.

## Notes for Agents
This is where scope creep becomes revenue leakage—enforce gates.

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