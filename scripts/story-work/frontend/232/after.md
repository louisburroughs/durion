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

**Title:** [FRONTEND] Approval: Reflect Approval Invalidation After Estimate Financial Revision (Work Order → Pending Re-Approval)

**Primary Persona:** Service Advisor (primary). Secondary: Technician (read-only awareness).

**Business Value:** Prevents scope/price drift by ensuring any financially-relevant estimate revision forces re-approval before execution/promotion, with a clear audit trail visible in the POS UI.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** the POS UI to clearly indicate when a previously customer-approved work order has been invalidated due to an estimate financial revision and is now awaiting re-approval  
- **So that** I can re-seek customer approval and the system prevents continuing under an outdated approval.

### In Scope
- Frontend screens/components to:
  - Display Work Order approval status including **Pending Re-Approval**.
  - Display the reason/context for invalidation (old vs new totals, estimate version change, timestamp, and event/audit linkage if provided).
  - Block/disable UI actions that require a currently valid approval (e.g., “promote/continue workflow” actions) when status is Pending Re-Approval.
  - Provide navigation to re-approval capture flow (existing approval capture story/screen assumed) when re-approval is required.
  - Show audit/transition history entries related to the invalidation.

### Out of Scope
- Implementing backend event publishing/subscription (`pricing.EstimateFinanciallyRevised.v1`) or backend state machine logic.
- Defining pricing rounding rules or recalculation logic.
- Creating a new approval capture method UX (signature/email/etc.) beyond linking to existing flows.
- Introducing new workflow states beyond those already defined by `workexec`.

---

## 3. Actors & Stakeholders
- **Service Advisor:** needs to see invalidation and re-approve.
- **Technician:** must see that approval is no longer valid (informational); execution restrictions are enforced by backend, but UI must not present misleading “approved” indicators.
- **System:** backend transitions Work Order to `PendingReApproval` after pricing event.
- **Auditor/Manager:** needs traceability via transition history/audit entries.

---

## 4. Preconditions & Dependencies

### Preconditions
- A Work Order exists and references an Estimate (`workOrder.estimateId`).
- The Work Order had previously been in an approved state (“CustomerApproved” per backend reference) and then was invalidated by backend due to estimate financial revision.
- Backend exposes Work Order current status and transition/audit history via API.

### Dependencies (Blocking if absent/unknown)
- Backend must provide an API to load:
  - Work Order details including **current status** and **estimate reference/version/total fields** (at least enough to show “approval no longer valid”).
  - Transition/audit entries that include invalidation details (old/new totals in minor units, old/new estimate version, eventId, actorId) as referenced in backend story.
- Existing frontend routes/screens for:
  - Work Order detail view.
  - Approval capture / re-approval flow for an estimate/work order (link target must be confirmed).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Work Order list → select Work Order.
- Deep link: `/workorders/<workOrderId>` (exact route TBD by repo conventions).

### Screens to create/modify
- **Modify:** Work Order Detail screen to:
  - Render status `PendingReApproval` distinctly from “Approved”.
  - Render an “Approval invalidated” callout when status is Pending Re-Approval.
  - Provide CTA: “Request/Re-capture Approval” (navigates to existing approval capture screen).
- **Modify/Add:** Work Order “History” or “Audit” subview to show the transition entry to `PendingReApproval` including reason metadata.
- **Optional Modify:** Work Order list row badges/filters to include `PendingReApproval`.

### Navigation context
- Work Order Detail has tabs/sections: Summary, Items, Approvals (if exists), History.
- From invalidation callout, user can navigate to:
  - Estimate view (read-only) to see new totals.
  - Approval capture flow.

### User workflows

#### Happy path: Advisor sees invalidation and re-approves
1. Advisor opens Work Order.
2. UI shows status = `PendingReApproval` and a callout explaining approval invalidated due to estimate revision with relevant details.
3. Advisor clicks “Re-capture Approval”.
4. Approval capture completes (outside scope), backend transitions work order back to customer-approved state (outside scope).
5. On return/refresh, UI no longer shows invalidation callout; status reflects approved.

#### Alternate path: Technician views work order
1. Technician opens Work Order assigned to them.
2. UI indicates work is blocked awaiting customer re-approval; no “start/continue” actions presented if they depend on approval.

---

## 6. Functional Behavior

### Triggers
- Backend changes Work Order status to `PendingReApproval` after a pricing financial revision event is processed.
- Frontend detects status on load or via refresh/polling.

### UI actions
- On Work Order Detail load:
  - Fetch Work Order details.
  - Fetch transition history (or include in details) to find the latest invalidation transition record.
- If status is `PendingReApproval`:
  - Show blocking banner/callout: “Customer approval invalidated — re-approval required.”
  - Provide actions:
    - “View revised estimate” (link to estimate detail if exists)
    - “Re-capture approval” (link to approval capture flow)
- Disable/hide actions that would misrepresent approval validity (see Business Rules/UI mapping).

### State changes (frontend-local)
- No client-side state machine decisions; UI reflects backend status.
- Local UI state: loading, error, empty audit history, unauthorized.

### Service interactions
- Read: Work Order by id; transitions/audit by workOrderId.
- Navigation: route transitions only; no direct invalidation action from UI in this story.

---

## 7. Business Rules (Translated to UI Behavior)

> Authoritative rules come from backend reference + workexec guide. UI must not invent policy.

### Validation / gating (UI-level)
- When Work Order status is `PendingReApproval`, the UI MUST:
  - Indicate approval is not current.
  - Prevent initiating any frontend actions that assume a valid approval **if such actions exist on the Work Order UI** (e.g., “Start Work Order”, “Move to Ready for Pickup”, “Close/Complete”, “Generate invoice”, etc.).  
  - If the UI currently shows these actions, they must be disabled with an inline reason: “Blocked: estimate was revised; customer re-approval required.”

### Visibility rules
- Invalidation details panel is shown when:
  - `workOrder.status == PendingReApproval` OR
  - transition history contains a latest transition to PendingReApproval even if current status later changed (then show in History only, not banner).

### Error messaging expectations
- If invalidation details cannot be loaded (history endpoint fails), show a fallback message:
  - “Approval requires re-approval due to estimate revision. Details unavailable.”  
  - Do not block navigation to re-approval due to missing audit detail.

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **WorkOrder**
  - `id` (string/number)
  - `status` (enum/string; includes `PendingReApproval`)
  - `estimateId`
  - (If available) `approvedEstimateVersion`, `approvedEstimateTotalMinor`, current `estimateVersion`, current `estimateTotalMinor`
- **WorkOrderStateTransition** (from `GET /api/workorders/{id}/transitions` per workexec doc; backend story adds extra fields)
  - `id`
  - `workOrderId`
  - `fromStatus`
  - `toStatus`
  - `transitionedAt` (UTC)
  - `transitionedBy` (userId)
  - `reason` (text)
  - `metadata` (JSON) — expected to include keys such as:
    - `estimateId`
    - `oldVersion`, `newVersion`
    - `oldTotalMinor`, `newTotalMinor`
    - `eventId`
    - `actorId`

### Fields (type, required, defaults)
- `status`: required; if missing treat as error (cannot render gating).
- `transition history`: optional; empty list allowed.

### Read-only vs editable
- All fields in this story are read-only in UI.
- No editing of approval artifacts here.

### Derived/calculated fields
- “Approval invalidated at”: derived from latest transition where `toStatus == PendingReApproval` (timestamp).
- “Changed amount”: derived from `newTotalMinor - oldTotalMinor` if both available; display formatting handled by existing currency utility (must not invent currency).

---

## 9. Service Contracts (Frontend Perspective)

> Exact endpoints may differ in Moqui; below are required capabilities. If the project uses Moqui screens/services instead of REST, map accordingly during implementation.

### Load/view calls
1. **Load Work Order**
   - Capability: fetch work order detail by id
   - Expected response includes: `id`, `status`, `estimateId` (minimum)
2. **Load Work Order transitions/history**
   - `GET /api/workorders/{id}/transitions` (from WORKORDER_STATE_MACHINE.md)
   - Expected response: array of transition records
   - Must include the transition to `PendingReApproval` with `reason` and optionally `metadata`.

### Create/update calls
- None in scope.

### Submit/transition calls
- None in scope (backend performs invalidation automatically).

### Error handling expectations
- `401/403`: show unauthorized screen/state; do not show sensitive metadata.
- `404`: show “Work order not found”.
- `409`: if seen when attempting actions (if any remain clickable), show “Work order status changed, refresh required.”
- Generic errors: show toast/banner; keep read-only page usable if possible.

---

## 10. State Model & Transitions

### Allowed states (relevant subset)
From workexec FSM + backend reference:
- WorkOrder statuses include `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, etc. (see WORKORDER_STATE_MACHINE.md)
- Backend reference introduces/uses:
  - `CustomerApproved` (source state for invalidation check)
  - `PendingReApproval` (target state when invalidating)

### Role-based transitions (UI concerns)
- UI must not enable “start work order” when in `PendingReApproval` (even if technician has other permissions).
- UI should direct only Service Advisor to re-approval capture CTA (if role info is available). If roles are not available client-side, show CTA but backend must enforce permissions.

### UI behavior per state
- **PendingReApproval**
  - Show invalidation banner + CTA(s)
  - Disable/hide downstream actions that require approval
  - Show latest invalidation details if present
- **Other states**
  - No banner; history still shows prior invalidation transition if it occurred.

---

## 11. Alternate / Error Flows

### Validation failures
- Not applicable (read-only story), except gating of actions.

### Concurrency conflicts
- If Work Order status updates between load and user action (re-approval capture navigation or other button clicks), UI must handle `409` or stale display by offering refresh.

### Unauthorized access
- If user lacks permission to view transitions/audit:
  - Still show `PendingReApproval` status and banner based on Work Order status
  - Hide detailed metadata; show generic message
  - Provide navigation to re-approval only if permitted (unknown; see Open Questions)

### Empty states
- Transition list empty:
  - History tab shows “No transitions recorded.”
  - If status is PendingReApproval but no transition record returned: show banner with “Details unavailable”.

---

## 12. Acceptance Criteria

### Scenario 1: Work Order displays Pending Re-Approval status and banner
**Given** a Work Order has `status = PendingReApproval`  
**When** the user opens the Work Order detail screen  
**Then** the UI displays a clear indicator that customer approval is invalidated and re-approval is required  
**And** the UI does not display the Work Order as “Approved/Customer Approved”.

### Scenario 2: Invalidation details shown from transition history
**Given** a Work Order has `status = PendingReApproval`  
**And** the transitions endpoint returns a latest transition with `toStatus = PendingReApproval` including `transitionedAt`, `reason`, and `metadata.oldTotalMinor`/`metadata.newTotalMinor`  
**When** the user views the Work Order detail (or History section)  
**Then** the UI shows the invalidation timestamp and reason  
**And** the UI shows old vs new totals when available  
**And** the UI shows estimate version change when available.

### Scenario 3: Missing transition metadata degrades gracefully
**Given** a Work Order has `status = PendingReApproval`  
**And** the transitions endpoint returns an empty list OR returns records without `metadata`  
**When** the user opens the Work Order detail screen  
**Then** the UI still shows the “re-approval required” banner  
**And** the UI shows “Details unavailable” (or equivalent) without breaking the page.

### Scenario 4: Actions requiring valid approval are blocked in UI
**Given** a Work Order has `status = PendingReApproval`  
**When** the user views the Work Order detail screen  
**Then** any action buttons that would proceed as if approval is valid (e.g., start/advance/close/promote/invoice) are disabled or hidden  
**And** if disabled, they include an explanation: “Blocked: customer re-approval required due to estimate revision.”

### Scenario 5: Unauthorized to view audit history
**Given** a Work Order has `status = PendingReApproval`  
**And** calling transitions returns `403 Forbidden`  
**When** the user opens the Work Order detail screen  
**Then** the UI shows the Pending Re-Approval banner based on Work Order status  
**And** the UI does not show restricted audit details  
**And** the UI remains usable.

### Scenario 6: Work Order no longer pending re-approval after refresh
**Given** a Work Order was `PendingReApproval`  
**And** after re-approval the backend status becomes `CustomerApproved` (or equivalent approved state)  
**When** the user refreshes the Work Order detail screen  
**Then** the re-approval-required banner is no longer shown  
**And** the History still contains the prior invalidation transition record.

---

## 13. Audit & Observability

### User-visible audit data
- Work Order History view must show transition entry to `PendingReApproval` including:
  - who (userId or display name if available)
  - when (UTC timestamp, rendered in local time)
  - why (`reason`)
  - what changed (old/new totals and versions if present in metadata)

### Status history
- Must render transitions in chronological order with newest first (unless existing convention differs).

### Traceability expectations
- If `metadata.eventId` exists, display as a non-editable reference (copyable) for support/debug.

---

## 14. Non-Functional UI Requirements

- **Performance:** Work Order detail should load with at most 2 network calls (work order + transitions). Transitions can be lazy-loaded when opening History tab if needed.
- **Accessibility:** Banner/callout must be perceivable with screen readers; disabled buttons must include accessible description of why disabled.
- **Responsiveness:** Works on tablet resolutions used in POS environment.
- **i18n/timezone/currency:**  
  - Times: store in UTC from backend, display in user locale.  
  - Currency: display using existing currency formatting utilities; do not assume currency code without backend-provided context.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATES: Provide explicit empty-state messaging for missing transition history; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Alternate/Empty states, Acceptance Criteria.
- SD-ERR-MAPPING-STD: Standard handling for 401/403/404/409 with user-friendly messages; qualifies as safe because it does not alter domain policy. Impacted sections: Service Contracts, Alternate/Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **State naming alignment:** Backend reference uses `CustomerApproved` and `PendingReApproval`, while existing workexec FSM doc lists `APPROVED` and does not mention these approval-specific states. What are the exact Work Order status enum values exposed to frontend for “customer-approved” and “pending re-approval”?  
2. **Frontend routing/link targets:** What is the canonical route/screen for “Re-capture Approval” (estimate approval capture) in the Moqui frontend? Provide route name and required params (estimateId vs workOrderId).  
3. **Action gating list:** Which specific Work Order UI actions must be blocked when status is `PendingReApproval` in the current frontend (e.g., start, complete, close, invoice, pick parts)? Please list existing buttons/commands so gating is deterministic.  
4. **Audit metadata availability:** Will `GET /api/workorders/{id}/transitions` include the invalidation details (`oldTotalMinor`, `newTotalMinor`, `oldVersion`, `newVersion`, `eventId`, `actorId`) in `metadata`, or will there be a separate endpoint for approval invalidations?  
5. **Role/permission signals:** Does the frontend have access to the user’s roles/permissions to conditionally show the “Re-capture Approval” CTA only to Service Advisors, or should the CTA always show and rely on backend authorization?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Approval: Invalidate Approval on Estimate Revision — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/232

Labels: frontend, story-implementation, customer

## Frontend Implementation for Story

**Original Story**: [STORY] Approval: Invalidate Approval on Estimate Revision

**Domain**: customer

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
An approved (or pending approval) estimate is modified after submission/approval.

## Main Flow
1. System detects a change that affects scope, pricing, quantities, taxes, or terms.
2. System invalidates existing approval record(s) and marks them as superseded.
3. System transitions estimate back to Draft (or Revision) and requires resubmission.
4. System records invalidation reason and linkage between versions.
5. System prevents promotion until a new valid approval is captured.

## Alternate / Error Flows
- Minor change that does not affect customer-visible outcome → policy may allow non-invalidation (rare; configurable).

## Business Rules
- Any customer-visible change invalidates approval.
- Invalidation must preserve original approval artifact but mark it not-current.
- Promotion validation checks for latest valid approval.

## Data Requirements
- Entities: Estimate, ApprovalRecord, ApprovalSnapshot, AuditEvent
- Fields: invalidationReason, supersededByApprovalId, status, changedFields, changedBy, changedDate

## Acceptance Criteria
- [ ] Approval is invalidated when customer-visible changes occur.
- [ ] System requires resubmission and blocks promotion until re-approved.
- [ ] Audit shows why and when approval was invalidated.

## Notes for Agents
This is the guardrail against scope/price drift—keep it strict.

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x