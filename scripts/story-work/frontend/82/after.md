## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:order
- status:draft

### Recommended
- agent:order-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Order: Cancel Order with Controlled Void Logic (Moqui + Quasar)

### Primary Persona
Store Manager (and Service Advisor where permitted)

### Business Value
Reduce operational and financial risk by enabling controlled, policy-compliant order cancellation with explicit downstream outcomes (work cancellation/rollback and payment void/refund requirement) and complete auditability.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Store Manager (and Service Advisor if authorized),
- **I want** to cancel an order from the POS UI,
- **So that** mistaken orders can be voided safely, downstream work is not cancelled when irreversible, and payment reversal is initiated appropriately with clear operator guidance.

### In-scope
- A cancel action surfaced from an Order detail context in the Moqui/Quasar frontend.
- A cancellation dialog that collects required inputs (`reason`, optional `comments`) and confirms consequences.
- Frontend integration with Moqui services that invoke backend cancellation orchestration.
- UI handling of all response states: `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, `CANCELLATION_FAILED`, and “in progress”/conflict.
- Display of cancellation audit details (at minimum: cancellation id, who/when, reason/comments, payment/work statuses, correlation ids when available).
- Permission-gated availability of “Cancel Order”.

### Out-of-scope
- Implementing billing void/refund logic itself (owned by backend).
- Implementing workexec cancellation logic itself (owned by backend).
- Operator dashboard for resolving `CANCELLATION_FAILED` beyond presenting current state and a link/CTA placeholder if provided by existing navigation.
- Accounting/GL impacts and any refund processing UI beyond “manual refund required” messaging.

---

## 3. Actors & Stakeholders

- **Store Manager**: initiates cancellation; needs clear outcomes and next steps.
- **Service Advisor**: may initiate cancellation if permitted (permission-driven).
- **Operations / Admin**: resolves failures; needs audit and correlation data.
- **Billing domain/system**: authoritative for reversal/settlement outcomes (display-only here).
- **Work Execution domain/system**: authoritative for work cancellability (display-only here).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS.
- User has permission `ORDER_CANCEL` to initiate cancellation (must be checked server-side; UI should also gate).
- An Order exists and is viewable in the UI with an `orderId`.
- Order is not in a terminal state that disallows cancellation (per backend: `COMPLETED`, `CLOSED`, `CANCELLED`).

### Dependencies
- Moqui screen framework available for Order detail.
- Backend cancellation API/service exists and matches (or is mapped by Moqui) to:
  - Request: `orderId`, `reason` (enum), optional `comments` (<= 2000 chars)
  - Response: `orderId`, `status`, `message`, `cancellationId`, `paymentVoidStatus`, `workRollbackStatus`
  - Error codes: `400`, `403`, `409`, `500` as described in backend references.
- Optional: backend provides current order status, work status, and payment status for display (not fully specified in provided inputs).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Order Detail screen: primary “Cancel Order” action.
- (Optional) Orders list row action (only if already present in app patterns; otherwise out-of-scope).

### Screens to create/modify
- **Modify** existing Order detail screen (Moqui `screen`):
  - Add Cancel action/button (permission + state gated).
  - Add a Cancellation summary section when order is in any cancellation-related state.
- **Add** a modal/dialog screenlet (or embedded form) for cancellation input:
  - Reason (required enum select)
  - Comments (optional textarea, max 2000)
  - Confirmation text showing implications (work blocking, payment reversal/refund requirement).

### Navigation context
- User remains on Order detail after completion; UI refreshes order state and shows resulting status and message.

### User workflows
#### Happy path (cancellable; void succeeds / no payment)
1. User clicks **Cancel Order**
2. Dialog opens; user selects `reason`, optionally enters `comments`
3. User confirms
4. UI shows in-progress state (button disabled/spinner)
5. Service call returns `200` with status `CANCELLED`
6. UI refreshes and displays “Cancelled” outcome and cancellation details.

#### Alternate path: payment settled
- Same as above; response `200` with `CANCELLED_REQUIRES_REFUND`
- UI displays “Cancelled; manual refund required” and marks payment reversal status accordingly.

#### Alternate path: work blocks cancellation
- Service call returns `400` with message including work status
- Dialog remains open (or closes with toast) and shows deterministic error; no state change displayed.

#### Error path: downstream failure
- Service call returns `500`
- UI displays “Cancellation failed; manual intervention required” and renders order state `CANCELLATION_FAILED` if returned/loaded.

#### Concurrency path: cancellation already in progress
- Service call returns `409`
- UI shows conflict message and refreshes order to show `CANCELLING`/in-flight state.

---

## 6. Functional Behavior

### Triggers
- User clicks “Cancel Order” on Order detail.

### UI actions
- Open cancellation form dialog.
- Validate required inputs client-side before submit.
- Submit cancellation request via Moqui transition/service.
- Show progress indicator; disable submit to prevent double-click.
- On completion, refresh order view data; render new status and backend-provided message.

### State changes (frontend-visible)
- UI must reflect order status changes returned by backend (or after reload):
  - `CANCELLING` / in-flight (if exposed)
  - `CANCELLED`
  - `CANCELLED_REQUIRES_REFUND`
  - `CANCELLATION_FAILED`

### Service interactions
- Submit cancellation request to Moqui service that calls backend cancellation endpoint.
- Optional pre-load/refresh call to load current order state after submit (or rely on response payload + reload).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `reason` is required; block submit until chosen.
- `comments` optional; enforce max length **2000** characters client-side; also rely on server validation.

### Enable/disable rules
- “Cancel Order” action visible/enabled only when:
  - user has `ORDER_CANCEL` permission (or equivalent permission flag exposed to UI)
  - order is not in terminal disallowed states (`COMPLETED`, `CLOSED`, `CANCELLED`)
  - order is not currently in `CANCELLING` (if UI knows); otherwise handle via `409`.
- Submit button disabled while request is in-flight.

### Visibility rules
- If order status is `CANCELLED_REQUIRES_REFUND`, show prominent guidance: “Manual refund required” (no refund UI here).
- If order status is `CANCELLATION_FAILED`, show prominent guidance: “Manual intervention required” and display any failure details available (message, subsystem statuses).

### Error messaging expectations
Map backend outcomes to user-facing messages:
- `400`: show backend message verbatim only if it is user-safe (expected: includes work status). Otherwise show generic validation error and log details.
- `403`: “You do not have permission to cancel orders.”
- `409`: “Cancellation is already in progress. Please refresh and try again.”
- `500`: “Order cancellation failed. Manual intervention required.” plus backend message if safe.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Order** (read): `orderId`, `status`, possibly `workOrderId`, `paymentId`, `totalAmount`, `currency`.
- **CancellationRecord** (read after action): `cancellationId`, `reason`, `comments`, `cancelledBy`, `cancelledAt`, `paymentVoidStatus`, `workRollbackStatus`, `correlationIds` (if exposed).

> Note: Exact Moqui entities and field names are not provided; frontend must bind to existing Moqui facade/services.

### Fields
#### Cancel input (form)
- `orderId` (string/UUID, required; hidden context)
- `reason` (enum, required): `CUSTOMER_REQUEST | INVENTORY_UNAVAILABLE | PRICING_ERROR | DUPLICATE_ORDER | OTHER`
- `comments` (string, optional, max 2000)

#### Response fields (display)
- `status` (string enum): includes `CANCELLED | CANCELLED_REQUIRES_REFUND | CANCELLATION_FAILED` (and possibly `CANCELLING`)
- `message` (string)
- `cancellationId` (string/UUID)
- `paymentVoidStatus` (string enum): `VOIDED | VOID_FAILED | REFUND_REQUIRED | NOT_APPLICABLE`
- `workRollbackStatus` (string enum): `ROLLED_BACK | ROLLBACK_FAILED | NOT_APPLICABLE`

### Read-only vs editable
- All order and cancellation fields are read-only in this story.
- Only `reason` and `comments` are user-editable inputs in the cancel dialog.

### Derived/calculated fields
- Display-only derived label for `status` and reversal statuses (e.g., “Refund required” badge when `paymentVoidStatus=REFUND_REQUIRED` or order status `CANCELLED_REQUIRES_REFUND`).

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation should use screen transitions that call services; the actual backend endpoint paths are not specified for frontend repo. Define Moqui service interface names and map to backend in configuration once clarified.

### Load/view calls
- `OrderDetail.load` (existing): loads order summary including `orderId`, `status` and any cancellation summary needed.
- Optional: `OrderCancellation.getLatest` to display latest cancellation record (if not already part of Order detail response).

### Create/update calls
- None (beyond cancellation submit).

### Submit/transition calls
- `OrderCancellation.cancel` (new or existing Moqui service)
  - Inputs: `orderId`, `reason`, `comments`
  - Outputs: `orderId`, `status`, `message`, `cancellationId`, `paymentVoidStatus`, `workRollbackStatus`
  - Error handling: surface HTTP-equivalent errors or Moqui error codes mapped to 400/403/409/500 semantics.

### Error handling expectations
- Moqui service should return structured errors that the Vue/Quasar layer can map to:
  - field-level errors (e.g., missing reason, comments too long)
  - global errors (permission, conflict, downstream failure)
- Correlation ID should be accessible for support (header or response field) if provided.

---

## 10. State Model & Transitions

### Allowed states (frontend must render)
- Active/cancellable: `ACTIVE` (or equivalent)
- In-flight: `CANCELLING` (or `CANCEL_REQUESTED`/`CANCEL_PENDING` depending on backend)
- Terminal success: `CANCELLED`
- Terminal success w/ follow-up: `CANCELLED_REQUIRES_REFUND`
- Terminal operational failure: `CANCELLATION_FAILED` (or backend variants like `CANCEL_FAILED_*`)

### Role-based transitions
- Users with `ORDER_CANCEL` may initiate cancellation from eligible states.
- Users without permission must not see/execute cancel; server returns `403`.

### UI behavior per state
- `ACTIVE`: show enabled Cancel action if permission allows.
- `CANCELLING`: disable Cancel action; show “Cancellation in progress” status and advise refresh.
- `CANCELLED`: hide/disable Cancel action; show cancellation summary.
- `CANCELLED_REQUIRES_REFUND`: hide/disable Cancel; show “manual refund required” guidance.
- `CANCELLATION_FAILED`: hide/disable Cancel by default; show failure details and guidance for operator intervention (no retry action unless clarified).

---

## 11. Alternate / Error Flows

### Validation failures
- Missing reason → inline validation; no submit.
- Comments > 2000 chars → inline validation; no submit.
- Backend `400` for terminal state or work blocking → show error banner/toast; keep dialog open for user to review.

### Concurrency conflicts
- Backend `409` when status is `CANCELLING` → show conflict message; refresh order.

### Unauthorized access
- Backend `403` → show permission error; ensure Cancel action not available on refresh.

### Empty states
- If no cancellation record exists, do not render cancellation summary section.
- If statuses are unknown/unmapped, display raw status string and log a frontend warning.

---

## 12. Acceptance Criteria

### Scenario 1: Cancel order successfully (no blocking work; reversal succeeds)
**Given** I am authenticated and have `ORDER_CANCEL`  
**And** an order exists in a cancellable state  
**When** I open the Cancel Order dialog  
**And** I select a cancellation reason  
**And** I submit the cancellation  
**Then** the UI disables the submit action while processing  
**And** on success it displays status `CANCELLED`  
**And** it shows the returned `message` and `cancellationId`  
**And** it shows `paymentVoidStatus` and `workRollbackStatus` as provided.

### Scenario 2: Cancel order where payment is settled (refund required)
**Given** I have `ORDER_CANCEL` and an order exists with settled payment  
**When** I submit cancellation with a valid reason  
**Then** the UI displays status `CANCELLED_REQUIRES_REFUND`  
**And** the UI displays a message indicating “manual refund required”  
**And** the Cancel action is no longer available.

### Scenario 3: Work status blocks cancellation
**Given** I have `ORDER_CANCEL`  
**And** the order is linked to work that is in a blocking status  
**When** I submit cancellation  
**Then** the UI shows a validation error returned by the server  
**And** the error message includes the blocking work status (if provided)  
**And** the order remains not cancelled in the UI after refresh.

### Scenario 4: Cancellation already in progress
**Given** I have `ORDER_CANCEL`  
**And** the order is currently in a cancellation in-flight state  
**When** I attempt to cancel the order again  
**Then** the UI displays a conflict message (mapped from `409`)  
**And** the UI refreshes and shows the in-flight cancellation status.

### Scenario 5: Downstream failure results in operational failure state
**Given** I have `ORDER_CANCEL`  
**And** downstream systems fail during cancellation  
**When** I submit cancellation  
**Then** the UI shows an error message indicating manual intervention is required  
**And** after refresh the UI shows order status `CANCELLATION_FAILED` (or equivalent)  
**And** it displays any available failure details in the cancellation summary.

### Scenario 6: Permission denied
**Given** I am authenticated but do not have `ORDER_CANCEL`  
**When** I view an order  
**Then** I do not see an enabled Cancel Order action  
**And** if I attempt cancellation via direct request, I receive `403` and the UI shows a permission error.

---

## 13. Audit & Observability

### User-visible audit data
- Show (when available): `cancellationId`, `cancelledBy`, `cancelledAt`, `reason`, `comments`, `paymentVoidStatus`, `workRollbackStatus`.
- Provide a copyable correlation identifier if present in response or headers (for support).

### Status history
- If the Order detail already has status history, append/ensure cancellation transitions are visible.
- Otherwise, show current status + cancellation record summary only.

### Traceability expectations
- Frontend logs (console or app logger) must not include sensitive payment data; log only IDs and correlation IDs.
- Include `orderId` and `cancellationId` in any frontend telemetry events if existing.

---

## 14. Non-Functional UI Requirements

- **Performance:** cancel submit should show immediate feedback (<250ms UI response); do not block UI thread.
- **Accessibility:** dialog must be keyboard-navigable; focus trapped; labels for inputs; error messages associated to fields.
- **Responsiveness:** dialog and status panels work on tablet widths typical for POS.
- **i18n/timezone/currency:** display timestamps in configured locale/timezone; currency formatting only for existing order totals (no new calculations).

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATES: Render empty state when no cancellation record exists; safe because it does not alter domain behavior; impacts UX Summary, Error Flows.
- SD-UX-LOADING-DISABLE-SUBMIT: Disable submit and show spinner during in-flight request to prevent duplicate clicks; safe UI ergonomics; impacts Functional Behavior, Acceptance Criteria.
- SD-ERR-GENERIC-MAPPING: Map unknown server errors to a generic failure message while preserving correlation id for support; safe because it avoids leaking internals; impacts Service Contracts, Alternate/Error Flows.

---

## 16. Open Questions

1. **Domain label mismatch:** Frontend issue text says **Domain: payment** and “domain: Point of Sale”, but backend reference stories assert cancellation orchestration under **domain:order** (and/or `domain:positivity`). Confirm the frontend repo’s canonical domain label for this story: `domain:order` vs `domain:positivity`. (Blocking: label + routing.)
2. **Moqui integration contract:** What are the actual Moqui service names/endpoints to call for:
   - loading order detail (including cancellation summary),
   - submitting cancellation,
   - retrieving cancellation record (if separate)?
   Provide names, parameters, and response mappings.
3. **Order status enum in frontend:** Which exact status values will the frontend receive (`CANCELLING` vs `CANCEL_REQUESTED`/`CANCEL_PENDING`, `CANCEL_FAILED_*` vs `CANCELLATION_FAILED`)? Provide the definitive enum set to render.
4. **Permission exposure:** How does the frontend determine `ORDER_CANCEL` capability—via a permissions API, session context, or screen conditions in Moqui? Provide the standard pattern used in this repo.
5. **Work/payment advisory pre-check:** Should the frontend call any pre-check endpoints (e.g., show “work not cancellable” before submit), or rely solely on submit response for blocking? Backend references mention advisory pre-check in positivity guide but not mandated for frontend.
6. **Retry/admin re-trigger UI:** For `CANCELLATION_FAILED`, should the frontend present a “Retry cancellation” button (admin only) or only display status? Backend mentions admin re-trigger mechanism; frontend requirement not specified.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Order: Cancel Order with Controlled Void Logic  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/82  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Cancel Order with Controlled Void Logic

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Store Manager**, I want to cancel an order so that mistaken orders can be voided safely.

## Details
- If payment authorized/captured, require appropriate void/refund flow.
- Prevent cancel when work already started unless controlled.

## Acceptance Criteria
- Cancellation enforces policy.
- Proper payment reversal initiated.
- Workexec link handled (cancel link or create adjustment).

## Integrations
- Payment service integration required; workexec notified if linked.

## Data / Entities
- CancellationRecord, PaymentReversalRef, WorkexecLink, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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