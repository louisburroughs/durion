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

# 1. Story Header

## Title
[FRONTEND] [STORY] Execution: Handle Part Substitutions and Returns

## Primary Persona
Technician / Parts Counter Staff

## Business Value
Ensure parts usage on an in-progress Work Order remains inventory-accurate and auditable when a different part is used or unused parts are returned, while properly gating customer-visible price increases through approval when required.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Technician or Parts Counter Staff  
- **I want** to record a part substitution or return against an issued/consumed Work Order part line  
- **So that** inventory quantities reconcile correctly, billing remains accurate, and traceability/audit requirements are satisfied.

## In-scope
- UI entry from Work Order execution to:
  - Return unused quantity for an issued part usage line
  - Substitute an issued part usage line with a different catalog part
- UI validations that prevent invalid adjustments (over-return, invalid quantity, policy blocks)
- UI handling when substitution triggers an approval gate due to price increase (including “price match avoids approval” path when supported by backend)
- Display of substitution/return audit history at least at the Work Order level (read-only)

## Out-of-scope
- Defining substitution policy rules (backend/policy)
- Implementing inventory availability search/reservation rules beyond what backend exposes
- Implementing Service Advisor approval/rejection UI for substitution price increase (only route/flag visibility; separate story if needed)
- Accounting/COGS ledger posting logic (event emitted by backend)

---

# 3. Actors & Stakeholders
- **Technician / Parts Counter (Primary):** initiates substitution or return.
- **Service Advisor:** may need to approve price increase or apply price match (approval UI may be separate).
- **Inventory Manager:** depends on correct reconciliation; reviews audit.
- **Customer:** impacted by substitution price changes (approval required in some cases).
- **Downstream Accounting/Inventory Systems:** consume `InventoryAdjusted` event.

---

# 4. Preconditions & Dependencies
## Preconditions
- User is authenticated.
- Work Order exists and is in execution-eligible status (backend story indicates `IN_PROGRESS` required).
- Work Order has at least one issued part usage record to adjust.

## Dependencies
- Backend endpoints for:
  - Loading Work Order and part usage lines (read model)
  - Submitting substitution
  - Submitting return
  - (If required) evaluating/returning approval-required flags and price-match handling
- Backend event emission: `InventoryAdjusted` with idempotency behavior (frontend must pass/track idempotency key or receive one).
- Catalog lookup for substitute parts (search/select) and availability checks (backend enforced).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Work Order execution screen (e.g., `/workorders/{workOrderId}`) within “Parts” or “Materials” section:
  - Action on a part usage row: **Substitute**
  - Action on a part usage row: **Return**

## Screens to create/modify
1. **Modify** `WorkOrderDetail` (or equivalent execution screen)
   - Add row actions: `Substitute`, `Return`
   - Add a read-only “Usage & Adjustments” panel (or expandable section) to show prior substitutions/returns if available
2. **Create** modal/dialog screen for **Return Unused Part**
   - Fields: returnQuantity (+ optional reasonCode if backend supports)
3. **Create** modal/dialog screen for **Substitute Part**
   - Fields: substituteProduct selection, substituteQuantity, optional reasonCode, optional “price match to original” controls **only if backend supports**
4. **Create/Modify** lightweight “Pending Approval” indicator on Work Order (if backend flags)
   - When substitution triggers approval requirement, show banner and disable further execution actions if backend returns `PENDING_APPROVAL` (behavior must match backend response)

## Navigation context
- Remain within Work Order execution context; dialogs close back to Work Order detail.
- After successful submission, refresh affected part usage rows and totals.

## User workflows
### Happy path: Return
1. User opens Work Order → Parts list
2. User selects issued part usage → clicks **Return**
3. Dialog prompts for `returnQuantity`
4. User submits → UI shows updated consumed quantity and recorded return event

### Happy path: Substitute with no approval
1. User selects issued part usage → **Substitute**
2. User searches/selects substitute part + quantity
3. User submits → UI shows original consumed decremented, new substitute usage created, link shown in history; Work Order remains in-progress

### Alternate path: Substitute triggers approval
1. User submits substitution
2. Backend responds “approval required” and updates Work Order/line status to pending approval
3. UI shows “Pending approval” state and blocks further execution interactions consistent with returned Work Order status

### Alternate path: Price match avoids approval
1. User (Service Advisor or authorized user) submits substitution with price-match instruction (if supported)
2. Backend processes without setting pending approval; UI reflects success

---

# 6. Functional Behavior

## Triggers
- User invokes `Return` or `Substitute` action on a specific Work Order part usage line.

## UI actions
### Return action
- Open dialog pre-populated with:
  - Part name/SKU
  - Issued quantity, consumed quantity (read-only)
- User enters `returnQuantity` (required, numeric > 0, <= consumed)
- Submit button calls backend; on success refresh Work Order data and show success toast/message.

### Substitute action
- Open dialog pre-populated with:
  - Original part identity and current quantities
- User selects substitute part (catalog search/select)
- User enters `substituteQuantity` (required, numeric > 0, <= original consumed quantity **unless backend allows other behavior; see Open Questions**)
- Submit calls backend; on success refresh Work Order data.

## State changes (frontend-visible)
- Return:
  - Decrease consumed quantity by `returnQuantity`
  - Increase returned quantity (if returnedQuantity is tracked separately)
- Substitution:
  - Decrease original consumed quantity by substituted amount
  - Create substitute usage record with consumed quantity (or issued/consumed as backend defines)
  - Create substitution link record for traceability
  - Potentially set Work Order (or line) status to `PENDING_APPROVAL` (or `AWAITING_APPROVAL` depending on backend model)

## Service interactions
- Load: fetch Work Order detail including part usage lines and relevant flags
- Submit return: call a “return part usage” service/endpoint
- Submit substitution: call a “substitute part” service/endpoint
- Refresh: re-fetch Work Order detail and history after successful mutation

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Return quantity:
  - Must be > 0
  - Must not exceed current `consumedQuantity`
  - Must not cause consumedQuantity to go negative (client-side guard + backend enforcement)
- Substitution:
  - Must select a valid substitute catalog part
  - Quantity must be > 0
  - Substitution not allowed by policy → UI displays blocking error from backend
  - Substitute unavailable/insufficient inventory → block with backend error
- Approval policy:
  - If substitution increases customer-visible totals and is not price-matched, backend flags approval required; UI must reflect this clearly and prevent further execution actions if the Work Order becomes approval-blocked.

## Enable/disable rules
- Disable `Return` and `Substitute` actions when:
  - Work Order is not `IN_PROGRESS` (or backend indicates not modifiable)
  - Part usage line is not issued/eligible (backend-derived flag preferred)
  - Work Order is in an approval-blocked state (e.g., pending approval) if backend enforces
- Disable submit buttons while request in-flight (prevent double-submit)

## Visibility rules
- Show “Pending approval” banner when backend indicates approval-required status on Work Order or affected line
- Show substitution link traceability (original ↔ substitute) in history view when available

## Error messaging expectations
- Show backend-provided validation message verbatim where safe (no stack traces)
- Map common cases:
  - 400: inline field error + top summary
  - 403: “You do not have permission to adjust parts on this work order.”
  - 404: “Work order or part usage not found.”
  - 409: “This work order/line changed since you opened it. Refresh and try again.”

---

# 8. Data Requirements

## Entities involved (frontend view model)
> Backend is system-of-record; frontend displays/collects fields.

- **WorkOrder**
  - `id`
  - `status` (must include in-progress and approval-blocking statuses as backend returns)
- **WorkOrderItemPartUsage** (or equivalent)
  - `id` (usageId)
  - `workOrderItemId`
  - `productId`
  - `productName`/display fields
  - `issuedQuantity` (number)
  - `consumedQuantity` (number)
  - `returnedQuantity` (number, optional if backend provides)
  - eligibility flags (optional)
- **PartSubstitutionLink**
  - `sourceUsageId`
  - `substituteUsageId`
  - `substitutedQuantity`
  - `timestamp`
  - `reasonCode` (optional)
- **PartUsageEvent** (if backend exposes event history)
  - `eventType` (`PART_SUBSTITUTION`, `PART_RETURN`)
  - `actorUserId`
  - `timestamp`
  - product/usage references
- **ChangeRequest** (only if backend uses this mechanism for approval gating; display-only linkage)

## Fields (type, required, defaults)
### Return dialog input
- `usageId` (string/number, required; hidden)
- `returnQuantity` (decimal/number, required)
- `reasonCode` (string, optional; only if backend supports)

### Substitute dialog input
- `sourceUsageId` (required; hidden)
- `substituteProductId` (required)
- `substituteQuantity` (decimal/number, required)
- `reasonCode` (string, optional)
- `priceMatchToOriginal` (boolean, optional; only if backend supports)
- `priceMatchReason` (string, optional; only if backend requires)

## Read-only vs editable
- Read-only: issued/consumed/returned quantities, product identity, audit history
- Editable: returnQuantity, substitute selection, substituteQuantity, optional reason/price-match fields

## Derived/calculated fields (display)
- `remainingConsumed = consumedQuantity` (post-refresh)
- “Net adjustment preview” in dialogs (client-side): show “+X returned to inventory” and/or “-Y consumed for substitute” purely informational

---

# 9. Service Contracts (Frontend Perspective)
> Use these as Moqui service-call contracts even if actual REST paths differ; if Moqui calls REST, map these to endpoint calls.

## Load/view calls
1. **Get Work Order Detail**
   - Purpose: load part usage lines + status + approval flags
   - Expected: Work Order status, list of part usage lines, substitution/return history (if available)

2. **Search Catalog Parts** (for substitute selection)
   - Query: text, optional filters (category, availability)
   - Expected: productId, name, SKU, price (optional display), availability indicator (optional)

## Create/update calls
1. **Return Unused Part**
   - Input: `usageId`, `returnQuantity`, optional `reasonCode`
   - Output: updated usage line and/or updated Work Order snapshot
   - Errors:
     - 400 if returnQuantity invalid
     - 409 if concurrency conflict

2. **Substitute Part**
   - Input: `sourceUsageId`, `substituteProductId`, `substituteQuantity`, optional `reasonCode`, optional price-match fields
   - Output: created substitute usage id, substitution link id, updated Work Order status/flags
   - Errors:
     - 400 invalid qty/inputs
     - 403 no permission
     - 409 conflict
     - policy-block error (400/422)

## Submit/transition calls
- None directly beyond substitution/return mutations, but backend may transition Work Order/line into approval-blocking state; frontend must treat returned status as authoritative.

## Error handling expectations
- All mutation calls must be treated as non-idempotent from UI perspective; prevent double-click.
- If backend supports idempotency keys for `InventoryAdjusted`, frontend should pass a client-generated idempotency token **only if required by backend** (unclear; see Open Questions).

---

# 10. State Model & Transitions

## Allowed states (relevant subset)
Work Order states per workexec FSM (as provided):
- `DRAFT`, `APPROVED`, `ASSIGNED`, `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL`, `READY_FOR_PICKUP`, `COMPLETED`, `CANCELLED`

## Role-based transitions (frontend behavior)
- This story does not initiate Work Order FSM transitions directly.
- However, substitution may place Work Order/line into an approval-gated condition:
  - UI must reflect any backend-provided status such as `AWAITING_APPROVAL` or a line-level `PENDING_APPROVAL` state (backend story references `PENDING_APPROVAL` on Work Order/line—naming mismatch vs FSM; see Open Questions).

## UI behavior per state
- If Work Order is not in an execution-eligible state (expected: `WORK_IN_PROGRESS` and possibly in-progress substates):
  - Hide/disable Substitute/Return actions; show tooltip “Work order not in progress.”
- If Work Order indicates awaiting approval / pending approval:
  - Show banner with reason (if provided)
  - Disable further execution-affecting actions including Substitute/Return (unless backend explicitly allows)

---

# 11. Alternate / Error Flows

## Validation failures
- Return quantity > consumed → show inline error; submission blocked; if backend rejects, show returned error message.
- Substitute quantity invalid or missing substitute part → inline validation.

## Concurrency conflicts
- If backend returns 409 due to changed quantities/status:
  - Show modal “Work order has changed. Refresh to continue.”
  - Provide “Refresh” button that reloads Work Order detail.

## Unauthorized access
- 403 response:
  - Show “Not authorized” message
  - Keep dialogs open but disable submit until user closes; no partial UI updates.

## Empty states
- No issued parts:
  - Show “No issued parts to adjust.”
  - Hide Substitute/Return row actions.

---

# 12. Acceptance Criteria

## Scenario 1: Return unused part (success)
**Given** a Work Order is in `WORK_IN_PROGRESS` (or backend-defined in-progress state) and has a part usage line with `consumedQuantity = 5`  
**When** the user selects that line and submits a return with `returnQuantity = 2`  
**Then** the UI shows the updated part usage with `consumedQuantity = 3` (and `returnedQuantity` increased if provided) after refresh  
**And** an adjustment record/history entry is visible indicating a `PART_RETURN` was recorded.

## Scenario 2: Prevent over-return
**Given** a Work Order part usage line has `consumedQuantity = 3`  
**When** the user attempts to return `returnQuantity = 4`  
**Then** the UI blocks submission with a clear validation message  
**And** if submitted anyway (e.g., via direct request), the UI displays the backend rejection and no quantities change in the UI after refresh.

## Scenario 3: Substitute part (success, no approval required)
**Given** a Work Order is in `WORK_IN_PROGRESS` and has Part A consumed  
**When** the user substitutes Part A with Part B for quantity 1  
**Then** the UI shows:
- Part A consumed quantity reduced by 1  
- a new usage line for Part B reflecting the substituted quantity  
- a visible traceability link tying Part B usage to Part A usage  
**And** the Work Order remains executable (no approval banner is shown).

## Scenario 4: Substitute part triggers approval gate (price increase)
**Given** a Work Order is in `WORK_IN_PROGRESS` with Part A consumed  
**And** substituting to Part B increases customer-visible totals and is not price-matched per policy  
**When** the user submits the substitution  
**Then** the UI shows the substitution recorded  
**And** the Work Order (or affected line) is shown as awaiting/pending approval per backend response  
**And** execution-affecting actions (including additional Substitute/Return) are disabled until approval is resolved.

## Scenario 5: Substitution blocked by policy
**Given** a Work Order is in `WORK_IN_PROGRESS`  
**When** the user attempts a substitution that policy disallows  
**Then** the UI shows a blocking error message returned by the backend  
**And** no changes are reflected after refresh.

## Scenario 6: Duplicate submission protection (UI)
**Given** the user opens the Substitute dialog and clicks submit  
**When** the network is slow and the user clicks submit again  
**Then** the UI only issues one request (submit button disabled while in-flight)  
**And** the UI shows only one resulting substitution after refresh.

---

# 13. Audit & Observability

## User-visible audit data
- For each substitution/return, display (where backend provides):
  - event type (`PART_SUBSTITUTION` / `PART_RETURN`)
  - timestamp (UTC displayed in local time)
  - actor (user display name/id)
  - original part, substitute part (if applicable)
  - quantity adjusted
  - approval outcome/status if applicable

## Status history
- If Work Order status changes to an approval-blocked state as a result of substitution:
  - show latest status and (if available) a “View status history” link to transitions screen/section.

## Traceability expectations
- UI must preserve and display linkage between original usage and substitute usage (SubstitutionLink) so invoice explanations and audits can trace variance.
- If backend returns an idempotency key / event reference for `InventoryAdjusted`, display it in an expandable “Details” section for support/debug (optional if available).

---

# 14. Non-Functional UI Requirements

- **Performance:** Work Order detail refresh after mutation should complete within 2s on typical connection; show loading indicators.
- **Accessibility:** Dialogs keyboard-navigable; focus trapped in modal; error messages announced to screen readers.
- **Responsiveness:** Must work on tablet-sized screens used in shop floor context.
- **i18n/timezone:** Render timestamps in user locale/timezone; do not change stored UTC values.
- **Currency:** If showing price deltas, use store currency formatting (only if backend provides amounts).

---

# 15. Applied Safe Defaults
- SD-UX-LOADING-001: Disable submit buttons and show spinner during in-flight mutations; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-EMPTY-001: Provide explicit empty state when no issued parts exist; safe UI ergonomics; impacts UX Summary, Alternate / Error Flows.
- SD-ERR-MAP-001: Standard HTTP error-to-message mapping (400/403/404/409) without exposing internals; safe because it’s generic error handling; impacts Business Rules, Error Flows, Service Contracts.

---

# 16. Open Questions
1. **Backend contract alignment:** What are the exact backend endpoints (paths, methods) and payload schemas for:
   - return unused part
   - substitute part
   - loading part usage + substitution links + history  
   (Needed to implement Moqui service calls and screen actions precisely.)
2. **Approval state naming:** Does approval gating manifest as Work Order state `AWAITING_APPROVAL` (FSM) or a separate status like `PENDING_APPROVAL` (mentioned in backend story) and is it WorkOrder-level or line-level? (UI enable/disable rules depend on this.)
3. **Price match input:** Is “price match to original price” performed during substitution submission (needs UI fields) or as a separate Service Advisor action after substitution? If during submission, what permissions/fields are required (e.g., reason code)?
4. **Substitution quantity constraints:** Must substituted quantity be `<= original consumedQuantity` always, or can it exceed (e.g., original was partially consumed)? Confirm allowed rules so UI validations match backend.
5. **Idempotency key handling:** Does the frontend need to supply an idempotency key/version (`workorderId + originalPartId + adjustedPartId + adjustmentVersion`) or is it generated server-side? If client-supplied, define exact composition and where `adjustmentVersion` comes from.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Handle Part Substitutions and Returns  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/221  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Handle Part Substitutions and Returns

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
Parts Counter / Technician

## Trigger
A different part is used or unused parts are returned.

## Main Flow
1. User selects a part item and chooses 'Substitute' or 'Return'.
2. System records substitution linking original and substituted part references.
3. System records quantity returned and updates usage totals.
4. If substitution impacts price/tax, system flags for approval if required.
5. System records all events for audit and inventory reconciliation.

## Alternate / Error Flows
- Substitution not allowed by policy → block.
- Return would create negative consumed quantity → block.

## Business Rules
- Substitutions must preserve traceability to original authorized scope.
- Returns must be reconciled against issued/consumed quantities.
- Price/tax impacts may require customer approval.

## Data Requirements
- Entities: PartUsageEvent, WorkorderItem, SubstitutionLink, ChangeRequest
- Fields: originalProductId, substituteProductId, quantityReturned, eventType, requiresApprovalFlag

## Acceptance Criteria
- [ ] System records substitutions with traceability.
- [ ] Returns reconcile correctly without negative totals.
- [ ] Approval is triggered when substitution changes customer-visible totals (policy).
- [ ] Substituted or returned parts emit a single InventoryAdjusted event
- [ ] Adjustment references the original issued part record
- [ ] Inventory quantities reconcile correctly after adjustment
- [ ] COGS impact (if any) is reversible and auditable
- [ ] Duplicate adjustment events do not double-adjust inventory

## Integrations

### Accounting
- Emits Event: InventoryAdjusted
- Event Type: Non-posting (inventory / COGS correction)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part substitution or return after initial issue
- Idempotency Key: workorderId + originalPartId + adjustedPartId + adjustmentVersion

## Notes for Agents
Substitution is a classic variance driver—capture it cleanly for invoice explanations.


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