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
[FRONTEND] [STORY] Promotion: Generate Workorder Items from Approved Scope

### Primary Persona
System (initiated by Service Advisor during Estimate → Work Order promotion)

### Business Value
Ensure that when an approved Estimate is promoted to a Work Order, the resulting Work Order Items are created deterministically from the approved scope with immutable pricing/tax snapshots and traceability, enabling execution, downstream invoicing, and variance explanations.

---

## 2. Story Intent

### As a / I want / So that
- **As the System**, I want the POS frontend to initiate and correctly handle the promotion of an **Approved Estimate** into a **Work Order** such that **Workorder Items are generated from the approved scope** with immutable financial snapshots and traceability, **so that** technicians can execute authorized work and billing can invoice based on preserved, explainable numbers.

### In-scope
- Frontend UI flow to initiate “Promote Estimate to Work Order”.
- Frontend handling of success response (navigate to created Work Order).
- Frontend display/behavior for promotion warnings (e.g., items requiring review).
- Frontend display/behavior for promotion failures (missing tax config, total mismatch, invalid state).
- Frontend read-only visibility of the created Workorder Items’ key snapshot/traceability fields (as returned by backend).
- Moqui screen flow, transitions, and service calls to the backend APIs described in provided references.

### Out-of-scope
- Implementing backend promotion logic, tax calculation, or catalog validity checks (backend responsibility).
- Editing Workorder Items post-promotion (not specified here).
- Work Order execution state machine transitions (start/in-progress/etc.) beyond viewing created items.
- Notification delivery mechanisms for warnings/errors.
- Configuration of tax rules or locations.

---

## 3. Actors & Stakeholders
- **Service Advisor (initiator):** clicks to promote an approved estimate.
- **System (primary actor):** executes promotion and item generation via backend.
- **Technician (downstream):** consumes generated Workorder Items for execution.
- **Billing/Accounting (downstream):** relies on snapshotted amounts for invoicing/variance explanation.
- **Audit/Compliance (stakeholder):** expects traceability and immutable audit events (as surfaced/visible where applicable).

---

## 4. Preconditions & Dependencies

### Preconditions
- Estimate exists and is eligible for promotion (expected: `APPROVED` per backend story reference).
- User is authenticated and authorized to promote an estimate (permission model not specified in provided inputs).

### Dependencies
- Backend endpoint that performs promotion (not explicitly provided in frontend story input; must be confirmed).
- Backend endpoints to load Estimate details and Work Order details after promotion.
- Backend emits audit/events; frontend should surface correlation IDs when available.

### External/System dependencies
- Tax configuration must be valid for the transaction context; if missing, promotion must fail atomically (per provided rules).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an **Estimate Detail** screen when the estimate status is `APPROVED`, provide an action: **“Promote to Work Order”**.

### Screens to create/modify
1. **Modify**: `EstimateDetail` screen
   - Add Promote action and status-gated availability.
2. **Create/Modify**: `WorkOrderDetail` screen (or existing equivalent)
   - Ensure it can display:
     - Work Order header linking to origin estimate
     - Workorder Items list including snapshot fields and `requiresReview`
3. **Optional (if no existing)**: `PromotionResultDialog` component (Quasar dialog) used within Moqui screen for confirmation/errors.

> Exact screen names/paths are not provided in inputs; implement using repo conventions (Moqui screen XML + Vue/Quasar components) and ensure routing is consistent with existing navigation.

### Navigation context
- Estimate Detail → Promote → on success navigate to Work Order Detail for the created Work Order.
- On failure remain on Estimate Detail and show blocking error.

### User workflows

#### Happy path
1. Service Advisor opens an approved estimate.
2. Clicks **Promote to Work Order**.
3. Confirms action (if confirmation pattern exists in project).
4. Frontend calls backend promotion service.
5. On success, frontend navigates to Work Order detail; shows created Workorder Items and any review flags.

#### Alternate paths
- Promotion succeeds but some items are flagged `requiresReview=true` → show non-blocking warning banner on Work Order.
- Promotion fails due to missing tax configuration → show blocking error, do not navigate.
- Promotion fails due to totals mismatch → show blocking error, do not navigate.
- Promotion fails due to invalid estimate state (e.g., not approved) → show blocking error and refresh estimate.

---

## 6. Functional Behavior

### Triggers
- User clicks **Promote to Work Order** from an eligible Estimate Detail page.

### UI actions
- Show loading state (disable Promote button while request in-flight).
- Show success notification (non-intrusive) on completion.
- Show error dialog/banner on failure with actionable message.

### State changes (frontend)
- No local state machine is created; frontend reflects backend state:
  - Estimate remains `APPROVED` or transitions per backend behavior (not specified).
  - Work Order is created and becomes the primary entity to display.

### Service interactions
- Call backend promotion endpoint (see **Service Contracts**).
- After success, load Work Order detail via backend and render.
- Optionally refresh Estimate to show any linkage (if backend updates estimate with workOrderId/origin link).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Before calling promotion**:
  - Ensure estimate status displayed as `APPROVED`. If not, disable Promote button and show tooltip/message “Estimate must be Approved to promote.”
  - Require estimate ID present in route/context.
- **After calling promotion**:
  - If backend returns missing tax configuration error → treat as blocking; user must correct configuration (message must not guess where; see Open Questions for where to direct).

### Enable/disable rules
- Promote action enabled only when:
  - Estimate status is `APPROVED`
  - User has permission (unknown; if backend returns 403, handle gracefully)
  - No in-flight promotion request is running

### Visibility rules
- On Work Order detail:
  - Display `requiresReview` indicator per item when true.
  - Display traceability field `originEstimateItemId` (read-only) in item details (or in an “Advanced/Traceability” section).

### Error messaging expectations
- Must map backend structured reasons into user-facing messages:
  - `MissingTaxConfiguration`: “Promotion blocked: tax configuration is missing or invalid for this estimate. Correct tax setup and retry.”
  - `TotalMismatch`: “Promotion failed: calculated totals do not match the estimate totals. No work order was created.”
  - `InvalidEstimateState`: “Promotion failed: estimate is not in an approvable/promotable state. Refresh and try again.”
  - Unknown error: generic “Promotion failed due to a system error. Try again or contact support.” plus show correlation/request ID if available.

---

## 8. Data Requirements

### Entities involved (frontend perspective; via backend responses)
- Estimate
- EstimateItem (approved scope subset)
- WorkOrder
- WorkorderItem
- TaxSnapshot (or equivalent nested snapshot structure)

### Fields (type, required, defaults)
**Estimate (read)**
- `estimateId` (string/uuid, required)
- `status` (enum: at least `APPROVED`, required)
- Totals:
  - `subtotal`, `taxTotal`, `grandTotal` (money, required for display/validation messaging; backend compares totals)

**WorkOrder (read after promotion)**
- `workOrderId` (string/uuid, required)
- `originEstimateId` (string/uuid, required)
- Totals: `subtotal`, `taxTotal`, `grandTotal` (money, required)
- `customerId` (string/uuid, optional for UI; referenced in backend event payload)

**WorkorderItem (read)**
- `workorderItemId` (string/uuid, required)
- `workorderId` (string/uuid, required)
- `originEstimateItemId` (string/uuid, required)
- `itemSeqId` (number/int, required)
- `itemType` (enum: `PART`, `LABOR`, `FEE`, required)
- `description` (string, required)
- `productId` (string/uuid, nullable)
- `quantity` (decimal, required, >0)
- `unitPrice` (money, required)
- `totalPrice` (money, required)
- `taxSnapshot` (object/json, required; includes `taxCode`, `taxRate`, `taxAmount` at minimum)
- `status` (enum, required; initial expected `Authorized`)
- `requiresReview` (boolean, required, default false)

### Read-only vs editable by state/role
- All snapshot and traceability fields are **read-only** in this story.
- No inline editing of Workorder Items is included.

### Derived/calculated fields (UI-only)
- Display-only: `lineTotal = totalPrice` as provided; do not recalc tax.
- Badge/flag: “Needs Review” from `requiresReview`.

---

## 9. Service Contracts (Frontend Perspective)

> Backend endpoints for promotion are not explicitly provided in the frontend story input. The backend reference mentions a command `PromoteEstimateToWorkOrder` and events, but not a REST path. This story therefore specifies required frontend-facing contracts and flags missing details as Open Questions.

### Load/view calls
- `GET /api/estimates/{estimateId}`
  - Needed to display estimate status and totals, and to gate Promote action.
- `GET /api/workorders/{workOrderId}` (or `/api/work-orders/{id}` depending on backend convention)
  - Needed to render created work order after promotion.
- `GET /api/workorders/{workOrderId}/items` (optional if not included in workorder payload)
  - Needed to render items list.

### Create/update calls
- None (frontend does not create items directly).

### Submit/transition calls
- **Promotion call (required):** one of the following must exist:
  - Option A: `POST /api/estimates/{estimateId}/promote-to-workorder`
  - Option B: `POST /api/workorders` with `{ originEstimateId }`
  - Option C: other backend-defined endpoint
- Expected success:
  - HTTP `201 Created` (preferred) or `200 OK`
  - Response includes at least `workOrderId` and `originEstimateId`
  - Include `requiresReview` flags per item either in immediate response or via subsequent GET
- Expected failures:
  - `400 Bad Request` for validation (e.g., invalid state)
  - `409 Conflict` for concurrency/total mismatch
  - `403 Forbidden` for authorization
  - `404 Not Found` if estimate not found

### Error handling expectations
- Backend should return a structured reason code (e.g., `MissingTaxConfiguration`, `TotalMismatch`, `InvalidEstimateState`).
- Frontend must:
  - Render reason-specific message (see Business Rules)
  - Preserve atomicity assumption: on failure, do not attempt to navigate to Work Order
  - Offer “Retry” for transient errors (network/5xx), not for policy errors (missing tax config)

---

## 10. State Model & Transitions

### Allowed states (relevant to this story)
- **Estimate**: must be `APPROVED` to promote (per backend reference).
- **WorkorderItem**: initial status must be `Authorized` (per backend reference).

### Role-based transitions
- Promotion is initiated by Service Advisor (role implied); actual enforcement is backend.
- Frontend must gracefully handle:
  - 403 Forbidden: show “You do not have permission to promote estimates.”

### UI behavior per state
- Estimate status `APPROVED`:
  - Show Promote action enabled.
- Any other estimate status:
  - Hide or disable Promote action (prefer disable with explanation).
- Work Order created:
  - Show items with status `Authorized` and flags.

---

## 11. Alternate / Error Flows

### Validation failures
- Estimate not `APPROVED` at time of click (stale UI):
  - Backend returns error; frontend refreshes estimate and shows error.

### Concurrency conflicts
- Another user promoted the estimate already:
  - If backend returns 409 with reason `AlreadyPromoted` (not specified), show message and navigate to existing work order if backend provides `workOrderId`. If not provided, show error and require manual lookup (Open Question).

### Unauthorized access
- Backend 403:
  - Show blocking message; do not change screens.

### Empty states
- Approved scope has zero items:
  - Not specified; if backend rejects, show error.
  - If backend allows, Work Order created with zero items—Work Order detail shows empty list with “No authorized items.” (This is a safe UX default only; backend behavior is unknown.)

### Catalog item no longer valid
- Promotion succeeds; specific Workorder Item has `requiresReview=true`:
  - Show warning banner “Some items require review” and highlight those items in list.

### Missing tax configuration
- Promotion fails atomically:
  - Show blocking error; keep user on Estimate detail.

### Totals mismatch
- Promotion fails atomically:
  - Show blocking error; optionally provide “Copy details” to include correlation ID and estimateId.

---

## 12. Acceptance Criteria

### Scenario 1: Promote approved estimate successfully and navigate to work order
**Given** I am a signed-in user viewing an Estimate with status `APPROVED`  
**And** the Estimate has at least one approved-scope line item with resolved pricing and tax  
**When** I click “Promote to Work Order”  
**Then** the frontend sends a promotion request for that Estimate ID  
**And** on success the frontend navigates to the Work Order detail for the returned `workOrderId`  
**And** the Work Order detail shows a list of Workorder Items created from the approved scope  
**And** each displayed Workorder Item includes `quantity`, `unitPrice`, `taxSnapshot.taxAmount`, and `originEstimateItemId` as read-only values.

### Scenario 2: Workorder items show Authorized status and traceability
**Given** a Work Order was created via promotion from an approved Estimate  
**When** I view the Work Order detail  
**Then** each Workorder Item shows status `Authorized`  
**And** each Workorder Item displays a non-empty `originEstimateItemId` value.

### Scenario 3: Promotion succeeds with invalid catalog item and flags requiresReview
**Given** an approved Estimate contains an approved-scope item referencing a deactivated/non-existent product  
**When** I promote the estimate to a work order  
**Then** the promotion succeeds and I am navigated to the created Work Order  
**And** the corresponding Workorder Item is marked `requiresReview=true` in the UI  
**And** the UI shows a non-blocking warning indicating items require review.

### Scenario 4: Promotion fails due to missing tax configuration (blocking)
**Given** I am viewing an approved Estimate  
**And** tax configuration is missing/invalid for the estimate context  
**When** I attempt to promote the estimate  
**Then** the frontend shows a blocking error message indicating tax configuration must be corrected  
**And** the frontend does not navigate to a Work Order screen  
**And** the Promote action becomes available again after the error is dismissed.

### Scenario 5: Promotion fails due to mismatched totals (atomic failure)
**Given** I am viewing an approved Estimate  
**And** the backend detects a totals mismatch during promotion  
**When** I attempt to promote the estimate  
**Then** the frontend shows a blocking error message indicating totals mismatch and that no work order was created  
**And** the frontend remains on the Estimate detail screen.

### Scenario 6: Unauthorized promotion attempt
**Given** I am viewing an approved Estimate  
**When** I attempt to promote the estimate but my user lacks permission  
**Then** the backend responds with HTTP 403  
**And** the frontend shows a blocking “not authorized” message  
**And** no Work Order is created or displayed.

---

## 13. Audit & Observability

### User-visible audit data
- On success, show:
  - `workOrderId`
  - `originEstimateId`
  - Promotion timestamp as returned by backend (if provided)
- If backend provides a correlation/request ID header or field, display it in error details (copyable).

### Status history
- Not implementing transition history UI here; ensure navigation does not obscure backend audit requirements.
- If Work Order detail includes existing audit tabs, link is acceptable.

### Traceability expectations
- UI must display `originEstimateItemId` per Workorder Item (read-only).
- UI must display `originEstimateId` on Work Order header (read-only).

---

## 14. Non-Functional UI Requirements

### Performance
- Promotion call should show an in-progress indicator within 250ms of click.
- After success, Work Order detail should render initial content quickly; lazy-load items list if payload large (implementation choice).

### Accessibility
- Promote button must be keyboard accessible and have clear disabled-state explanation.
- Error dialogs/banners must be screen-reader friendly (aria-live for alerts).

### Responsiveness
- Workorder Items list must be usable on tablet widths typical of shop floor.
- Item detail sections should stack vertically on narrow screens.

### i18n/timezone/currency
- Currency formatting must follow existing POS locale/currency configuration (do not hardcode).
- Timestamps shown (if any) should display in shop/user timezone; store UTC internally if displayed.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide standard empty-state messaging for Workorder Items list when no items returned; safe because it affects only UI ergonomics and not domain logic. Impacted sections: UX Summary, Alternate/Empty states.
- SD-ERR-MAP-HTTP: Map standard HTTP errors (400/403/404/409/5xx) to consistent UI notifications; safe because it’s generic error handling without changing business policy. Impacted sections: Business Rules, Service Contracts, Error Flows.
- SD-UX-LOADING-DISABLE: Disable primary action during in-flight request to prevent duplicate submission; safe because it’s a UI ergonomic/idempotency aid. Impacted sections: Functional Behavior, Error Flows.

---

## 16. Open Questions

1. What is the **exact backend REST endpoint** (method + path) to execute “Promote Estimate to Work Order”, and what is the **success response schema** (does it return `workOrderId` only, or also items/warnings)?
2. What structured **error response contract** is used for promotion failures (reason codes: `MissingTaxConfiguration`, `TotalMismatch`, `InvalidEstimateState`, etc.) and which HTTP statuses map to which reasons?
3. If an estimate is **already promoted** (concurrency case), does the backend return the existing `workOrderId` so the frontend can navigate directly?
4. Where in the existing frontend navigation is the canonical **Estimate Detail** and **Work Order Detail** route/screen IDs (Moqui screen paths) for linking and transitions?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotion: Generate Workorder Items from Approved Scope — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/229  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Generate Workorder Items from Approved Scope

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
A workorder header is created from an approved estimate.

## Main Flow
1. System iterates through approved scope line items.
2. System creates Workorder Items for parts and labor with stable identifiers.
3. System copies pricing/tax snapshot fields needed for downstream invoicing and variance explanations.
4. System marks items as 'Authorized' and sets execution flags (e.g., required vs optional).
5. System validates totals and quantity integrity.

## Alternate / Error Flows
- Approved scope contains an item no longer valid in catalog → allow as snapshot item and flag for review.
- Tax configuration missing → block promotion and require correction.

## Business Rules
- Only approved items are created on the workorder.
- Snapshot pricing/tax fields are preserved.
- Workorder items maintain traceability to estimate items.

## Data Requirements
- Entities: WorkorderItem, ApprovedScope, EstimateItem, TaxSnapshot
- Fields: itemSeqId, originEstimateItemId, authorizedFlag, quantity, unitPrice, taxCode, taxAmount, snapshotVersion

## Acceptance Criteria
- [ ] Workorder items match approved scope in quantity and pricing.
- [ ] Workorder items carry traceability back to estimate items.
- [ ] Promotion fails if required tax basis is missing (policy).

## Notes for Agents
Design for variance explanations later: preserve the numbers, not just totals.


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