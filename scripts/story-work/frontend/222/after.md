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
[FRONTEND] [STORY] Execution: Issue and Consume Parts (Work Order Part Usage Events)

### Primary Persona
Technician / Parts Counter Staff

### Business Value
Ensure parts issued to (picked) and consumed on (installed) a work order are recorded with full traceability, enforce authorized quantity limits, and reflect parts availability issues in work order execution—so inventory/accounting downstream processes can rely on accurate, idempotent events.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Technician or Parts Counter Staff  
- **I want** to record parts **issued** and **consumed** against a specific work order part line item  
- **So that** work progress is accurate, audit trails are immutable, and downstream inventory/accounting systems receive correct, idempotent issuance events.

### In Scope
- View work order part line items and their authorized vs issued/consumed quantities.
- Record part usage as **events** (issue and consume for a work order part item).
- Validate quantity integrity (cannot exceed authorized; cannot exceed available if inventory-integrated).
- Handle insufficient inventory by flagging and moving the work order to a parts-waiting execution status (see Open Questions re exact status mapping).
- Display usage event history (who/when/what) per work order item for auditability.
- Idempotent submission behavior from the frontend (prevent double-submit; handle retry responses safely).

### Out of Scope
- Creating/modifying estimate scope or approvals to increase authorized quantities (approval workflows are separate).
- Defining accounting policy (WIP vs COGS) or ledger postings (downstream/accounting-owned); frontend only surfaces returned policy if available.
- Implementing inventory allocation/picking list scanning workflows (separate “picking” domain features).
- Notification delivery mechanics (only show messages/alerts in UI).

---

## 3. Actors & Stakeholders

### Actors
- **Technician**: records consumed/installed parts during execution.
- **Parts Counter Staff**: records issued/picked parts to the job.

### Stakeholders
- **Service Advisor**: needs visibility when parts shortages block progress.
- **Inventory** (downstream system): relies on issued events to decrement on-hand / reservations.
- **Accounting** (downstream system): relies on events and policy metadata (WIP/COGS).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- A **Work Order** exists and is in a non-terminal status that permits recording parts usage (not `COMPLETED` / `CANCELLED`).
- Work order contains at least one **WorkOrderPart** (or equivalent) item with:
  - `productId`
  - authorized quantity
  - current issued/consumed totals
- Backend endpoints exist (or will exist) to:
  - Load work order + part items + usage history
  - Record a usage event (issue/consume)
  - Return validation errors deterministically

### Dependencies
- Work order execution status model (from `WORKORDER_STATE_MACHINE.md`) for any UI-driven status update behavior.
- Inventory availability lookup and “insufficient inventory” validation behavior (backend integration contract).
- Idempotency behavior for usage event recording (backend must support deterministic dedupe or return created event).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: action “Parts” → “Issue / Consume”.
- From Work Order line item detail (part item row): action “Record Usage”.

### Screens to create/modify
1. **Modify**: `WorkOrderDetail` screen to include a “Parts” section/tab and link to part usage.
2. **Create**: `WorkOrderPartUsage` screen (work order scoped) to:
   - List part items
   - Show remaining authorized qty
   - Launch “Record Usage” form
3. **Create/Embed**: `RecordPartUsage` dialog/form (screen include or subscreen) for a selected part line item.
4. **Create** (optional but recommended): `WorkOrderPartUsageHistory` subscreen showing immutable event list.

### Navigation context
- URL pattern (proposed):  
  - `/workorders/<workOrderId>/parts` (list + history)
  - `/workorders/<workOrderId>/parts/<workOrderPartId>` (detail + record usage)
- Breadcrumb: Work Orders → Work Order # → Parts

### User workflows
**Happy path**
1. User opens Work Order → Parts.
2. Selects a part line item.
3. Enters quantities: issued and/or consumed (see Open Questions on whether both are captured separately vs single combined).
4. Submits.
5. UI shows updated totals and appends an immutable event in history.

**Alternate paths**
- Insufficient inventory: UI shows error, offers “Mark Waiting for Parts” (if backend does not auto-transition) and shows shortage indicator.
- Exceeds authorized: UI blocks submission and provides link/CTA to “Request approval to add parts” (navigation only; approval workflow out of scope).
- Work order locked (terminal status): UI renders read-only with explanation.

---

## 6. Functional Behavior

### Triggers
- User clicks “Record Usage” on a work order part item.

### UI actions
- Display current state:
  - authorized quantity
  - total issued quantity to date
  - total consumed quantity to date
  - remaining authorized quantity (derived)
- Allow user to enter a **new usage event**:
  - `quantityIssued` (>= 0)
  - `quantityConsumed` (>= 0)
  - (optional) note/reason (if backend supports; not defined in provided inputs)

### State changes
- On successful record:
  - A new immutable **PartUsageEvent** exists (backend-owned).
  - Work order part item totals update (backend-owned).
  - If completion indicators apply, UI reflects any `completed/partsComplete` flags returned by backend.

### Service interactions
- Load work order parts and current totals on screen entry.
- Submit usage event via a single service call.
- Refresh the part item + history after successful submission.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side + server-side)
Client-side (safe, non-authoritative):
- Required: a part line item must be selected.
- `quantityIssued` and `quantityConsumed` must be numbers, `>= 0`.
- At least one of issued/consumed must be `> 0` (otherwise no-op).
- Prevent obvious overages based on displayed remaining authorized (still must be validated server-side).

Server-side (authoritative; UI must display errors):
- **No consumption/issuance** allowed when work order is `COMPLETED` or `CANCELLED`.
- **Cannot exceed authorized quantity** without separate approval.
- **Insufficient inventory** must be rejected or handled per backend policy (see Open Questions).

### Enable/disable rules
- Disable submit when:
  - Work order is terminal (`COMPLETED`, `CANCELLED`)
  - Form invalid
  - Submission in-flight (prevent double-submit)

### Visibility rules
- If backend indicates inventory integration is disabled/unavailable:
  - Hide “available on hand” indicators (if any)
  - Still allow recording usage events unless backend rejects.

### Error messaging expectations
- Quantity exceeds authorized: “Exceeds authorized quantity. Request approval to increase scope.”
- Insufficient inventory: “Insufficient inventory to issue/consume requested quantity.”
- Conflict/idempotency duplicate: “This usage event was already recorded. Totals have been refreshed.”
- Locked state: “Work order is locked and cannot be modified.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `WorkOrder` (read)
- `WorkOrderPart` or `WorkorderItem` (read/update via service)
- `PartUsageEvent` (create/read history)
- `InventoryReservation` (read-only indicators, if exposed)

### Fields
**WorkOrder**
- `workOrderId` (string/number, required)
- `status` (enum: from `WORKORDER_STATE_MACHINE.md`)

**WorkOrderPart / WorkorderItem (part line)**
- `workOrderItemId` / `workOrderPartId` (required)
- `productId` (required)
- `authorizedQuantity` (decimal, required)
- `quantityIssuedTotal` (decimal, required)
- `quantityConsumedTotal` (decimal, required)
- `originEstimateItemId` (optional)
- Derived (UI): `remainingAuthorized = authorizedQuantity - quantityConsumedTotal` (or other definition; see Open Questions)

**PartUsageEvent**
- `partUsageEventId` (required)
- `eventType` (enum; must support issue/consume/return per inputs)
- `quantityIssued` (decimal)
- `quantityConsumed` (decimal)
- `eventAt` (timestamp, UTC)
- `performedBy` (user id)
- `idempotencyKey` (string; see Open Questions on format alignment)

### Read-only vs editable
- Read-only: all history/event records.
- Editable: only the new usage event form inputs; existing events cannot be edited.

### Derived/calculated fields
- Remaining authorized quantity
- Completion indicator for the part item (if backend provides; otherwise only reflect totals)

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not fully specified in provided inputs; below is the **frontend-required contract**. If actual endpoints differ, story remains blocked until confirmed.

### Load/view calls
1. `GET /api/workorders/{workOrderId}`
   - returns `status`, basic header fields
2. `GET /api/workorders/{workOrderId}/parts`
   - returns list of part line items with authorized/issued/consumed totals
3. `GET /api/workorders/{workOrderId}/parts/{workOrderPartId}/usage-events`
   - returns immutable event list ordered by `eventAt desc`

### Create/update calls
1. `POST /api/workorders/{workOrderId}/parts/{workOrderPartId}/usage-events`
Request (proposed):
```json
{
  "productId": "PART-123",
  "quantityIssued": 2,
  "quantityConsumed": 2,
  "eventType": "ISSUE_CONSUME",
  "performedBy": 123,
  "clientRequestId": "uuid-optional"
}
```
Response (proposed):
- `201 Created` with created `PartUsageEvent` and updated item totals, OR
- `200 OK` with same payload if deduped (idempotent retry)

### Submit/transition calls
- If backend requires explicit WO status change on insufficient inventory:
  - `POST /api/workorders/{workOrderId}/transition` (not defined; must be clarified)
- Otherwise: backend automatically sets `AWAITING_PARTS` and returns updated work order.

### Error handling expectations
- `400` validation: map field errors to form fields.
- `403` unauthorized: show “You do not have permission”.
- `404` missing WO/item: show “Not found”.
- `409` conflict/idempotency: refresh data and show non-blocking message.

---

## 10. State Model & Transitions

### Allowed states (Work Order)
From `WORKORDER_STATE_MACHINE.md`:
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

### Role-based transitions
- This story does **not** define new transitions; it only:
  - Reads state
  - May trigger a change to “awaiting parts” as a consequence of insufficient inventory (needs clarification whether frontend triggers transition or backend sets it)

### UI behavior per state
- `COMPLETED`, `CANCELLED`: read-only; cannot record usage.
- `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`: usage allowed unless backend rejects due to policy.
- `AWAITING_PARTS`: show prominent banner “Waiting for parts”; allow recording usage events if parts become available.

---

## 11. Alternate / Error Flows

### Validation failures
- Over authorized:
  - Block submit, show server error.
  - Provide CTA link to out-of-scope “Request approval / change request”.
- Zero quantities:
  - Client blocks with “Enter issued or consumed quantity.”

### Concurrency conflicts
- If totals changed since load:
  - Backend returns `409` with latest totals; UI refreshes and prompts user to retry.

### Unauthorized access
- If user lacks permission:
  - UI disables “Record Usage” and shows “Insufficient permissions”.

### Empty states
- Work order has no part items:
  - Show “No parts on this work order.” with guidance (out-of-scope) to add parts via estimate/workorder editing.

---

## 12. Acceptance Criteria

### Scenario 1: Record part issue+consume successfully
**Given** a work order in `WORK_IN_PROGRESS` with a part line authorizing 2 units of `PART-123`  
**And** the user has permission to record part usage  
**When** the user records `quantityIssued=2` and `quantityConsumed=2` for that part line  
**Then** the system creates an immutable part usage event with `performedBy` and UTC timestamp  
**And** the part line totals reflect +2 issued and +2 consumed  
**And** the usage event appears in the history list without requiring a full page reload.

### Scenario 2: Prevent exceeding authorized quantity
**Given** a work order in `WORK_IN_PROGRESS` with a part line authorizing 2 units of `PART-123`  
**When** the user attempts to record `quantityConsumed=3`  
**Then** the submission is rejected  
**And** the UI displays an error “Exceeds authorized quantity”  
**And** no new usage event appears in history.

### Scenario 3: Insufficient inventory handling
**Given** a work order in `WORK_IN_PROGRESS` with a part line authorizing 2 units of `PART-123`  
**And** the backend indicates insufficient inventory for the requested quantity  
**When** the user submits a usage event for 2 units  
**Then** the UI displays “Insufficient inventory”  
**And** no usage event is recorded  
**And** the work order status is shown as `AWAITING_PARTS` **if and only if** the backend transitions it (or a separate transition call succeeds).

### Scenario 4: Work order locked (terminal)
**Given** a work order with status `COMPLETED`  
**When** the user navigates to the Parts Usage screen  
**Then** the UI renders usage history read-only  
**And** “Record Usage” actions are disabled  
**And** submitting is not possible.

### Scenario 5: Idempotent retry does not double-record
**Given** the user submits a usage event and the network times out after submit  
**When** the user retries the same submission  
**Then** the backend responds with the previously created event (or a dedupe indication)  
**And** the UI shows only one new event in history  
**And** totals reflect a single increment.

---

## 13. Audit & Observability

### User-visible audit data
- Per usage event show:
  - event type
  - quantities issued/consumed
  - performed by (user display name if available)
  - event timestamp (rendered in user locale but stored/displayed as UTC source)

### Status history
- If work order transitions to `AWAITING_PARTS`, display status change time and reason if backend provides.

### Traceability expectations
- UI must display identifiers when needed for support:
  - workOrderId
  - workOrderPartId/workOrderItemId
  - partUsageEventId
  - idempotency key (optional display in an “advanced” expander)

---

## 14. Non-Functional UI Requirements

- **Performance**: Parts list load < 2s for up to 200 part lines; history pagination supported.
- **Accessibility**: All form inputs labeled; errors announced; keyboard navigable.
- **Responsiveness**: Works on tablet (shop floor).
- **i18n/timezone/currency**: Timestamps displayed in local timezone; store UTC in payloads; quantities are decimal-safe.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty state messaging for “no parts” and “no history”; safe because it doesn’t affect domain policy. (Impacted: UX Summary, Error Flows)
- SD-UX-PAGINATION: Paginate usage event history (default page size 25) to avoid long lists; safe UI ergonomics only. (Impacted: UX Summary, Performance)
- SD-ERR-MAP-HTTP: Standard mapping of 400/403/404/409 to user messages and field errors; safe because it follows implied backend HTTP semantics. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Backend endpoint contract (blocking):** What are the exact Moqui service names / REST endpoints for:
   - loading work order parts
   - listing part usage events
   - creating a usage event (issue/consume/return)
2. **Event model clarity (blocking):** Are “issue” and “consume” captured as:
   - a single combined event (`ISSUED_AND_CONSUMED`), or
   - separate event types (`ISSUED`, `CONSUMED`, `RETURNED`) with separate quantities?
3. **Authorized quantity rule (blocking):** Is authorization enforced against **consumed total**, **issued total**, or both? (The inputs say “consumption exceeds authorized” but UI captures both.)
4. **Insufficient inventory behavior (blocking):** On insufficient inventory, does the backend:
   - reject without changing work order status, or
   - automatically transition work order to `AWAITING_PARTS`, or
   - require an explicit transition call from frontend?
5. **Idempotency key format conflict (blocking):** Provided inputs mention `workorderId + partId + usageSequence`, while backend reference uses `{workorderId}-{workorderItemId}-{partUsageEventId}`. Which is authoritative for the frontend to pass/display?
6. **Permissions (blocking):** What permissions/roles control:
   - viewing parts usage
   - recording issuance/consumption
   - performing “waiting for parts” transition (if frontend-triggered)?
7. **Inventory integration signal (blocking):** How does the frontend detect whether on-hand availability should be shown/validated (flag in response, separate endpoint, or always backend-enforced)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Issue and Consume Parts  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/222  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Issue and Consume Parts

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299981/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician / Parts Counter

## Trigger
Parts are picked/issued for installation on a workorder.

## Main Flow
1. User selects a workorder part item.
2. User records parts issued (picked) and parts consumed (installed).
3. System validates quantities and updates on-hand commitments (if integrated).
4. System records consumption event with timestamp and user.
5. System updates item completion indicators where applicable.

## Alternate / Error Flows
- Insufficient inventory → flag and move workorder to waiting parts status.
- Consumption exceeds authorized quantity → block or require approval per policy.

## Business Rules
- Parts usage must be recorded as events (issue/consume/return).
- Consumption should not silently change authorized scope without approval.
- Traceability must be preserved.

## Data Requirements
- Entities: WorkorderItem, PartUsageEvent, InventoryReservation
- Fields: productId, quantityIssued, quantityConsumed, eventType, eventAt, performedBy, originEstimateItemId

## Acceptance Criteria
- [ ] Parts issued/consumed can be recorded and audited.
- [ ] System enforces quantity integrity and policy limits.
- [ ] Workorder status reflects parts availability issues.
- [ ] Each issued part emits exactly one InventoryIssued event
- [ ] Inventory on-hand quantity is reduced correctly
- [ ] COGS or WIP impact follows configured accounting model
- [ ] Issued quantities are traceable to workorder and technician
- [ ] Replayed events do not double-reduce inventory

## Integrations

### Accounting
- Emits Event: InventoryIssued
- Event Type: Non-posting or Posting (configurable: WIP vs immediate COGS)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part is issued/consumed for a workorder item
- Idempotency Key: workorderId + partId + usageSequence


## Notes for Agents
Keep parts usage consistent with promotion snapshot; changes route through approvals.


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