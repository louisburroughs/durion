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
[FRONTEND] [STORY] Promotion: Promote Approved Scope from Partially Approved Estimate (create Work Order + defer remaining)

### Primary Persona
Service Advisor (Back Office / POS user)

### Business Value
Enable advisors to start work immediately on the customer-approved portion of an estimate while preserving non-approved items for future approval flows, ensuring technicians only execute authorized work and maintaining end-to-end traceability and auditability.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** to promote the approved scope of a partially approved Estimate into a new Work Order,  
**So that** work can begin on authorized items immediately while unapproved items remain deferred on the Estimate for potential future approval via proper gates.

### In-scope
- Frontend UI action available on an Estimate in `PartiallyApproved` state: “Promote Approved Scope”.
- Confirmation UX and submission to backend to perform the promotion.
- Post-action navigation and clear indicators on both:
  - the created Work Order (contains only approved items)
  - the source Estimate (promoted vs deferred line statuses)
- Display of traceability links between:
  - WorkOrder ↔ source Estimate
  - WorkOrderLine ↔ source EstimateLine (read-only display)
- Error handling for invalid state / no approved items / concurrency / authorization.

### Out-of-scope
- Any workflow for “later approval of deferred items” that creates Change Requests or supplemental Work Order items (explicitly out of scope per backend reference).
- Defining or changing domain policies for conflict resolution when later approvals conflict with work already performed.
- Implementing backend services/entities/state machines (frontend integrates only).

---

## 3. Actors & Stakeholders
- **Primary Actor:** Service Advisor
- **Secondary Stakeholders:** Technician (expects Work Order contains only authorized scope), Shop Manager (traceability & revenue opportunity), Customer (clear scope communication)
- **System:** Moqui-based POS frontend + workexec backend services

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to create Work Orders / promote estimates (permission name TBD by security model).
- An Estimate exists and is currently in state `PartiallyApproved`.
- Estimate has line items with per-line approval statuses (at least one is approved for a successful promotion).

### Dependencies
- Backend endpoint/service for “promote approved scope” exists and is accessible from Moqui (contract TBD; see Open Questions).
- Backend provides:
  - created WorkOrderId
  - updated Estimate + lines statuses (Promoted/Deferred)
  - traceability identifiers (sourceEstimateId, sourceEstimateLineId, and/or promotedToWorkOrderLineId)
- Routing: existing Estimate detail screen and Work Order detail screen exist (or are created by separate stories).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate Detail** screen when Estimate status is `PartiallyApproved`.

### Screens to create/modify
- **Modify:** `EstimateDetail` screen (Moqui Screen)
  - Add an action button/menu item: `Promote Approved Scope`
  - Add/ensure display of line item status badges including `Promoted` and `Deferred`
  - Add/ensure link-out from promoted estimate lines to the created Work Order / Work Order line (when identifiers available)
- **Modify:** `WorkOrderDetail` screen
  - Ensure the header shows `Source Estimate` link (sourceEstimateId)

> If these screens don’t exist with these names, implement equivalent screens consistent with the repo’s routing conventions (Open Question if naming must be exact).

### Navigation context
- User is viewing an Estimate (e.g., route contains `estimateId`).
- After successful promotion, user is redirected to Work Order detail (preferred) with a link back to the source Estimate.

### User workflows
#### Happy path
1. Advisor opens Estimate in `PartiallyApproved`.
2. Clicks `Promote Approved Scope`.
3. Confirmation modal summarizes:
   - number of approved items to be promoted
   - number of items that will be deferred
   - warning that deferred items will not be executable until later approval (informational)
4. Advisor confirms.
5. UI calls promote service.
6. UI navigates to newly created Work Order and shows success message.
7. Advisor can return to Estimate and see:
   - approved lines now `Promoted`
   - unapproved lines now `Deferred`

#### Alternate paths
- If no approved items: button disabled OR action shows error message (see Functional Behavior).
- If backend returns state conflict (Estimate not PartiallyApproved anymore): UI refreshes Estimate and explains action is no longer valid.
- If unauthorized: show “not permitted” error and do not change UI state.

---

## 6. Functional Behavior

### Triggers
- User clicks `Promote Approved Scope` on an Estimate in `PartiallyApproved`.

### UI actions
- **Render rule:** Show action only when:
  - Estimate.status == `PartiallyApproved`
  - AND UI has loaded line items (to compute approved count)
- **Enable rule:** Enable only when approved line count ≥ 1.
- **Click action:**
  1. Open confirmation dialog.
  2. On confirm: call backend promote operation.
  3. While pending: disable confirm button + show loading state.
  4. On success:
     - show toast/banner: “Work Order created from approved scope.”
     - navigate to WorkOrder detail (`/workorders/{workOrderId}` or equivalent)
  5. On failure: show error banner with mapped message (see Error Flows).

### State changes (frontend-visible)
- Estimate overall status expected to become `PromotedWithDeferredItems` (per backend reference).
- Estimate line statuses expected:
  - Approved → Promoted
  - Non-approved (PendingApproval/Declined/etc.) → Deferred
- Work Order created with only the promoted lines.

### Service interactions
- Load Estimate detail (existing call).
- Submit promotion action (new call).
- Reload/refresh Estimate data after promotion (optional if response returns updated Estimate; otherwise required).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Promotion action must not be possible unless Estimate is in `PartiallyApproved`.
  - UI must hide/disable the action outside that status.
  - Backend remains source of truth; UI must handle backend rejection.
- Promotion requires at least one line item with status `Approved`.
  - UI must compute approved count from loaded line items.
  - If approved count is 0: disable button and show helper text: “At least one approved item is required to promote.”

### Enable/disable rules
- Disable `Promote Approved Scope` if:
  - Estimate not `PartiallyApproved`
  - OR line items not loaded
  - OR approvedCount == 0
  - OR user lacks permission (if permission signal available in session/context; otherwise handle 403 on submit)

### Visibility rules
- In Estimate line list:
  - Display status badge for each line: `Approved`, `Declined`, `PendingApproval`, plus new/expected `Promoted`, `Deferred`.
- For `Promoted` lines:
  - Show read-only traceability link(s) if IDs available:
    - “Promoted to Work Order #X” (link)
- For `Deferred` lines:
  - Show non-executable indicator text: “Deferred (not on Work Order)”.

### Error messaging expectations
- No approved items: “Promotion failed: At least one item must be approved to create a Work Order.”
- Invalid estimate state: “Promotion unavailable: estimate is no longer partially approved. Refresh to see latest status.”
- Unauthorized: “You don’t have permission to promote estimates.”
- Generic/server: “Promotion failed due to a system error. Try again or contact support.”

---

## 8. Data Requirements

### Entities involved (frontend view)
- `Estimate`
- `EstimateLine`
- `WorkOrder`
- `WorkOrderLine`
- (Future/out-of-scope but referenced) `ChangeRequest`

### Fields (type, required, defaults)
#### Estimate (read)
- `estimateId` (string/number, required)
- `status` (enum string, required): includes at least `PartiallyApproved`, `PromotedWithDeferredItems`
- `customerId` (id, optional for this story)
- `shopId/locationId` (id, optional)

#### EstimateLine (read)
- `estimateLineId` (id, required)
- `status` (enum string, required): includes `Approved`, `Declined`, `PendingApproval`, plus expected `Promoted`, `Deferred`
- `description` (string, required for display)
- `quantity` (number, optional)
- `authorizedFlag` (boolean, **unclear**; do not assume)
- `deferredFlag` (boolean, **unclear**; do not assume)
- `promotedToWorkOrderLineId` (id, nullable; used for link if provided)

#### WorkOrder (read after create)
- `workOrderId` (id, required)
- `status` (enum string, required)
- `sourceEstimateId` (id, required)

#### WorkOrderLine (read)
- `workOrderLineId` (id, required)
- `sourceEstimateLineId` (id, required)

### Read-only vs editable by state/role
- All fields in this story are read-only in UI; the only mutation is invoking the “promote” action.

### Derived/calculated fields (frontend)
- `approvedCount` = number of estimate lines where `status == Approved`
- `deferredCount` = number of estimate lines where `status != Approved` (or specifically non-approved statuses; depends on backend status set)
- `promotionEligible` boolean from `(estimate.status == PartiallyApproved && approvedCount > 0)`

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not fully specified for frontend/Moqui; below is the **required** behavior the frontend expects. If the API differs, adjust screen transitions accordingly.

### Load/view calls
- `GET /api/estimates/{id}`
  - returns Estimate + line items with statuses (or separate endpoint for lines)
- If lines are separate:
  - `GET /api/estimates/{id}/lines`

### Create/update calls (promotion)
- **Required operation:** “Promote approved scope”
  - Proposed: `POST /api/estimates/{id}/promote-approved-scope`
  - Request body: `{ "promotedBy": <userId> }` (userId requirement unclear; Moqui session may suffice)
  - Response (minimum):
    - `createdWorkOrderId`
    - updated `estimateStatus`
    - optionally lists of `promotedLineItemIds`, `deferredLineItemIds`

### Submit/transition calls
- The promote action is a state transition on Estimate and a create on WorkOrder, performed atomically server-side.

### Error handling expectations
Frontend must map:
- `400` validation: show backend message (no approved lines, missing required fields)
- `401/403`: show unauthorized message; do not retry automatically
- `404`: show not found; offer navigation back
- `409` conflict (concurrency/state): prompt user to refresh Estimate; optionally auto-refresh and re-evaluate eligibility
- `5xx`: generic error; preserve current view

---

## 10. State Model & Transitions

### Allowed states (as referenced)
#### Estimate status (subset relevant)
- `PartiallyApproved` (eligible to promote)
- `PromotedWithDeferredItems` (result of successful promotion)

#### EstimateLine status (subset relevant)
- `Approved` → `Promoted`
- `Declined`/`PendingApproval`/other non-approved → `Deferred`

### Role-based transitions
- Service Advisor initiates promotion (role enforcement handled server-side; frontend hides action if permission info available).

### UI behavior per state
- Estimate `PartiallyApproved`:
  - Show Promote action if eligible
- Estimate `PromotedWithDeferredItems`:
  - Hide Promote action
  - Show promoted/deferred indicators and traceability links

---

## 11. Alternate / Error Flows

### Validation failures
- User attempts promote with zero approved items (edge case if UI stale):
  - Backend returns 400; UI shows error; refresh Estimate.
- Estimate not in `PartiallyApproved`:
  - Backend returns 409 or 400; UI shows state conflict message; refresh.

### Concurrency conflicts
- Another user promotes/changes status before this user confirms:
  - Backend returns 409; UI reloads estimate and disables action.

### Unauthorized access
- Backend returns 403:
  - UI shows permission error and hides/locks action on subsequent renders if possible.

### Empty states
- Estimate has no lines:
  - Promote disabled; show message “No line items available.”

---

## 12. Acceptance Criteria

### Scenario 1: Successful promotion creates work order with only approved scope
**Given** an Estimate exists with status `PartiallyApproved` and has 2 line items with status `Approved` and 1 line item with status `Declined`  
**And** the Service Advisor is viewing the Estimate detail screen  
**When** the user clicks `Promote Approved Scope` and confirms the action  
**Then** the system creates a new Work Order linked to the source Estimate  
**And** the UI navigates to the created Work Order detail screen showing the Work Order id  
**And** the Work Order contains exactly the 2 approved items (and not the declined item)  
**And** the source Estimate status becomes `PromotedWithDeferredItems`  
**And** the two previously approved Estimate lines show status `Promoted`  
**And** the previously declined Estimate line shows status `Deferred`.

### Scenario 2: Promote action is unavailable when estimate is not partially approved
**Given** an Estimate is in status `Draft` (or any status other than `PartiallyApproved`)  
**When** the user views the Estimate detail screen  
**Then** the `Promote Approved Scope` action is not shown or is disabled  
**And** the user cannot submit a promotion request from the UI.

### Scenario 3: Promotion blocked when no items are approved
**Given** an Estimate is in status `PartiallyApproved`  
**And** the Estimate has 0 line items with status `Approved`  
**When** the user views the Estimate detail screen  
**Then** the `Promote Approved Scope` action is disabled  
**And** the UI displays helper text indicating at least one approved item is required  
**When** the user attempts to trigger the action via a direct call (edge case)  
**Then** the UI displays: “Promotion failed: At least one item must be approved to create a Work Order.”  
**And** no navigation to a Work Order occurs.

### Scenario 4: Concurrency/state conflict during promotion
**Given** an Estimate is initially loaded as `PartiallyApproved` with at least one approved line  
**When** another user changes the Estimate status such that it is no longer eligible  
**And** the Service Advisor confirms promotion  
**Then** the backend responds with a conflict (HTTP 409 or equivalent)  
**And** the UI displays a message indicating the estimate changed and refresh is required  
**And** after refresh, the promote action is hidden/disabled based on the latest status.

### Scenario 5: Audit/traceability is visible in UI after promotion
**Given** a Work Order was created from partial promotion  
**When** the user views the Work Order detail screen  
**Then** the Work Order displays a read-only `Source Estimate` link referencing the originating Estimate  
**And** each Work Order line displays a read-only reference to its originating Estimate line identifier (or an accessible link if supported).

---

## 13. Audit & Observability

### User-visible audit data
- On Estimate detail, show read-only metadata if available (do not invent fields):
  - “Promoted by”, “Promoted at” (only if returned by backend)
- If not available, do not display.

### Status history
- If the UI already supports status history panels:
  - Ensure promotion creates a visible entry for Estimate status change and line status changes (requires backend support).

### Traceability expectations
- Must be possible to navigate:
  - WorkOrder → source Estimate
  - Promoted EstimateLine → corresponding WorkOrder/line (if backend provides IDs)

---

## 14. Non-Functional UI Requirements

- **Performance:** Promotion action should complete within 3 seconds under normal conditions; show loading indicator if longer.
- **Accessibility:** Confirmation dialog and status indicators must be keyboard navigable; status conveyed with text (not color-only).
- **Responsiveness:** Works on tablet width for advisor counter use.
- **i18n/timezone:** Display timestamps (if shown) in user locale/timezone; send/receive UTC timestamps as provided (do not convert business logic).
- **Security:** Do not expose internal-only fields; rely on backend authorization; avoid logging PII in browser console.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show a deterministic empty-state message when an Estimate has no line items; qualifies as safe because it is UI-only and does not change business rules. (Impacted sections: UX Summary, Alternate / Error Flows)
- SD-ERR-MAP-HTTP: Standard HTTP status to user message mapping (400/401/403/404/409/5xx); qualifies as safe because it only affects presentation of errors implied by backend contracts. (Impacted sections: Service Contracts, Alternate / Error Flows)

---

## 16. Open Questions

1. **Backend promote endpoint contract:** What is the exact endpoint/path, request body/query params, and response payload for “Promote Approved Scope” (including the created workOrderId and updated statuses)?  
2. **Exact frontend route/screen IDs:** What are the canonical Moqui screen paths for Estimate detail and Work Order detail in this repo (so the story can specify precise transitions)?  
3. **Exact status enums in frontend payloads:** Are the Estimate/EstimateLine statuses exactly `PartiallyApproved`, `PromotedWithDeferredItems`, `Promoted`, `Deferred` as strings, or different constants?  
4. **Permission signal:** Is there a frontend-accessible permission flag/role check to hide the action preemptively, or should we rely exclusively on handling 403 responses?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Handle Partial Approval Promotion  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/227  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Handle Partial Approval Promotion

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
An estimate is PartiallyApproved and user promotes approved scope.

## Main Flow
1. System creates workorder using only approved scope items.
2. System marks unapproved items as deferred and keeps them on the estimate.
3. System allows later approval of deferred items to create a change request or supplemental workorder items per policy.
4. System maintains traceability between deferred items and later approvals.
5. System shows clear indicators of partial promotion.

## Alternate / Error Flows
- Later approvals conflict with work already performed → require advisor resolution before adding.

## Business Rules
- Only approved scope is promotable.
- Deferred items remain visible but non-executable until approved.
- Later additions should flow through approval gates.

## Data Requirements
- Entities: ApprovedScope, Workorder, Estimate, ChangeRequest
- Fields: authorizedFlag, deferredFlag, scopeVersion, changeRequestId, status

## Acceptance Criteria
- [ ] Only approved items appear on the initial workorder.
- [ ] Deferred items remain on estimate and can be approved later.
- [ ] System maintains audit trace from deferred items to later changes.

## Notes for Agents
Partial approval adds “later” work—route that through change/approval, not silent edits.

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