STOP: Clarification required before finalization

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

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Order: Cancel Order with Controlled Void/Refund Logic (Moqui UI)

### Primary Persona
Store Manager (and permitted Service Advisor)

### Business Value
Enable safe order cancellation from POS with correct enforcement of work-status blocking, deterministic payment reversal initiation, and complete auditability—reducing financial/operational risk and improving supportability.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Store Manager  
- **I want** to cancel an order from the POS UI with required reason/comments and clear results  
- **So that** mistaken orders can be voided/refunded safely while preventing cancellation when work is already irreversibly started.

### In-scope
- UI entry point(s) to initiate cancellation from an order view
- Cancellation modal/form capturing `reason` (required) and `comments` (optional, max 2000)
- Moqui screen flow calling a backend cancel operation and presenting outcomes
- UI behavior for idempotency (`already cancelled`) and concurrency (`cancelling/in progress`)
- Display of cancellation result states: `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, `CANCELLATION_FAILED`
- Display of denial reasons when Work Execution status blocks cancellation
- Read-only display of cancellation/audit details as returned by backend

### Out-of-scope
- Implementing payment void/refund logic itself (backend-owned)
- Operator dashboard for resolving `CANCELLATION_FAILED`
- Editing/undoing a cancellation
- Accounting/GL impacts and refund processing workflow beyond “requires manual refund” messaging

---

## 3. Actors & Stakeholders
- **Store Manager**: initiates cancellations; must have `ORDER_CANCEL`
- **Service Advisor**: may initiate cancellation if granted `ORDER_CANCEL` (see Open Questions)
- **POS UI (Moqui screens)**: enforces UX validation; delegates policy enforcement to backend
- **Order domain backend**: validates order state, work-status rules, payment settlement rules; returns deterministic outcome
- **Work Execution system**: authoritative on work status gating (backend integration)
- **Payment/Billing system**: authoritative on void vs refund capability (backend integration)
- **Operations/Support**: needs clear status/audit for troubleshooting

---

## 4. Preconditions & Dependencies
- User is authenticated in POS UI.
- User has permission `ORDER_CANCEL` to see/execute cancel action.
- Order exists and is viewable in POS UI.
- Backend provides:
  - A cancel endpoint/service that accepts `orderId`, `reason`, `comments`
  - Deterministic response schema including cancellation status + metadata (see §9)
  - Error status mapping for validation/authorization/conflict/downstream failure
- Order view screen already exists (this story adds cancel capability to it).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Order Detail screen: primary action **Cancel Order** (visible only when cancellable by permission + state).

### Screens to create/modify
- **Modify**: `OrderDetail` screen (existing) to add:
  - Action to open cancel dialog
  - “Cancellation” panel/section to show current cancellation status + details when applicable
- **Create** (if pattern requires separate screen): `OrderCancelDialog` (subscreen or dynamic dialog)
- **Create/Modify**: `OrderCancellationHistory` subscreen (optional, if backend exposes multiple attempts)

### Navigation context
- User remains within the order context (`orderId` in parameters).
- After successful cancel submission, user stays on Order Detail with refreshed status.

### User workflows
**Happy path (payment voidable or no payment; work cancellable)**
1. User clicks **Cancel Order**
2. Modal opens with required `reason` and optional `comments`
3. User submits
4. UI shows in-progress state (disable submit, show spinner)
5. UI receives success response:
   - If `status=CANCELLED`: show confirmation banner + updated order status
   - If `status=CANCELLED_REQUIRES_REFUND`: show warning banner with “manual refund required” message
6. UI displays cancellation metadata (cancellationId, paymentVoidStatus, workRollbackStatus)

**Alternate path: work status blocks cancellation**
- Backend returns `400` with descriptive message; UI shows inline error + keeps modal open.

**Alternate path: cancellation already in progress**
- Backend returns `409`; UI shows non-destructive message “Cancellation already in progress—refreshing status” and refreshes order.

**Alternate path: cancellation failed**
- Backend returns `500` (or structured failure); UI shows error banner and updates status to `CANCELLATION_FAILED` if returned; presents “manual intervention required”.

---

## 6. Functional Behavior

### Triggers
- User selects **Cancel Order** action on Order Detail screen.

### UI actions
- Open modal/dialog:
  - Field: `reason` (required select)
  - Field: `comments` (optional textarea, max 2000)
  - Buttons: `Cancel` (close), `Submit Cancellation`
- On submit:
  - Client-side validate required fields + max length
  - Call Moqui transition to invoke backend cancel service
  - Disable inputs during request
  - On completion: close modal on success; keep open on validation errors

### State changes (UI-level)
- Order detail view refreshes data after cancellation attempt.
- UI reflects backend order status:
  - `ACTIVE` → (possibly transient `CANCELLING`) → terminal cancellation states
- The UI must not attempt to infer payment/work states; it displays returned results.

### Service interactions
- Load order summary/status for display (existing order view call)
- Submit cancel request to backend
- Refresh order detail after submission (or use response payload to update)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `reason` is required (UI blocks submit until selected)
- `comments` length ≤ 2000 (UI blocks submit and shows field error)
- `orderId` must be present in context; if missing, show fatal error and block action

### Enable/disable rules
- Show **Cancel Order** action only if:
  - User has `ORDER_CANCEL`
  - Order is not in terminal states: `COMPLETED`, `CLOSED`, `CANCELLED` (and not already `CANCELLED_REQUIRES_REFUND` / `CANCELLATION_FAILED` unless policy says allow retry—see Open Questions)
- Disable submit while request in flight.

### Visibility rules
- Cancellation result panel is visible when order status is one of:
  - `CANCELLING`, `CANCELLED`, `CANCELLED_REQUIRES_REFUND`, `CANCELLATION_FAILED`
- Show refund warning messaging only when `status=CANCELLED_REQUIRES_REFUND`.

### Error messaging expectations
- `400` (validation/work-block): show message returned from backend verbatim **if** it is safe/user-facing; otherwise map via error code (see Open Questions on error contract).
- `403`: show “You do not have permission to cancel orders.”
- `409`: show “Cancellation is already in progress. Please refresh.”
- `500`: show “Cancellation failed. Manual intervention required.” plus backend message if safe.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- Order (read)
- CancellationRecord (read, at least last attempt)
- CancelOrderRequest/Response (write/read DTO)

### Fields (type, required, defaults)
**Input (CancelOrderRequest)**
- `orderId`: string/UUID, required (from screen parameter)
- `reason`: enum/string, required
- `comments`: string, optional, default empty, max 2000

**Output (CancelOrderResponse)**
- `orderId`: string/UUID
- `status`: string enum (`CANCELLED|CANCELLED_REQUIRES_REFUND|CANCELLATION_FAILED` and possibly `CANCELLING`)
- `message`: string (user-facing)
- `cancellationId`: string/UUID
- `paymentVoidStatus`: enum (`VOIDED|VOID_FAILED|REFUND_REQUIRED|NOT_APPLICABLE`) (as provided)
- `workRollbackStatus`: enum (`ROLLED_BACK|ROLLBACK_FAILED|NOT_APPLICABLE`) (as provided)

### Read-only vs editable
- `orderId`: read-only
- `reason`, `comments`: editable only prior to submission; never editable after
- Cancellation results: read-only

### Derived/calculated fields
- UI “Can cancel?” derived from (permission + order status)

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact endpoints for frontend-to-backend are not provided in the inputs for the Moqui frontend. The contracts below describe **required** behavior; endpoint names are an Open Question.

### Load/view calls
- `GET OrderDetail(orderId)` (existing): must include at least `orderId`, `status`, and if available, latest cancellation summary.

### Create/update calls
- None.

### Submit/transition calls
- `CancelOrder` operation:
  - Input: `orderId`, `reason`, `comments`
  - Output: `CancelOrderResponse` (fields listed in §8)
  - Status codes:
    - `200 OK` success (cancelled or refund-required or idempotent already-cancelled)
    - `400 Bad Request` for invalid order/terminal state/work-block
    - `403 Forbidden` for missing permission
    - `409 Conflict` if `CANCELLING`/in-progress
    - `500 Internal Server Error` for downstream failure requiring manual intervention

### Error handling expectations
- Moqui screen should map HTTP/service errors into:
  - field-level errors for validation (`reason`, `comments`)
  - banner/toast errors for general failures
- Do not log/display sensitive payment details; only `paymentId` if ever shown (not required here).

---

## 10. State Model & Transitions

### Allowed states (must be supported in UI)
- Pre-cancel (examples): `ACTIVE` (and other non-terminal states used by system)
- In-flight: `CANCELLING` (or `CANCEL_REQUESTED` / `CANCEL_PENDING` depending on backend)
- Terminal:
  - `CANCELLED`
  - `CANCELLED_REQUIRES_REFUND`
  - `CANCELLATION_FAILED`

### Role-based transitions
- Only users with `ORDER_CANCEL` may initiate transition to cancellation flow.
- UI must not offer cancel to unauthorized users.

### UI behavior per state
- `ACTIVE` (or cancellable): show Cancel action enabled
- `CANCELLING` (or equivalent): hide/disable cancel action; show in-progress status and advise refresh
- `CANCELLED`: show cancelled banner and cancellation metadata; hide cancel action
- `CANCELLED_REQUIRES_REFUND`: show warning banner “manual refund required”; hide cancel action
- `CANCANCELLATION_FAILED`: show error banner “manual intervention required”; hide cancel action (unless retry is allowed—Open Question)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing reason → prevent submit; show “Reason is required.”
- Comments too long → prevent submit; show “Comments must be 2000 characters or fewer.”

### Concurrency conflicts
- If backend responds `409`, UI:
  - closes modal (optional) and refreshes order status, or
  - keeps modal open and shows non-field error with refresh action
  - (Exact UX choice is safe-default; see §15)

### Unauthorized access
- If user lacks permission:
  - Cancel action hidden by default
  - If direct URL/action attempted, backend `403` results in error banner and no state change

### Empty states
- If cancellation metadata missing, UI displays “No cancellation activity recorded.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Store Manager cancels an order successfully (voidable or no payment)
Given I am authenticated and have `ORDER_CANCEL` permission  
And I am viewing an order in a cancellable state  
When I open “Cancel Order” and submit a valid reason and optional comments  
Then the system submits the cancellation request for that orderId  
And I see a success confirmation  
And the order status updates to `CANCELLED`  
And I can view the returned `cancellationId`, `paymentVoidStatus`, and `workRollbackStatus`

### Scenario 2: Cancellation requires manual refund when payment is settled
Given I am authenticated and have `ORDER_CANCEL` permission  
And I am viewing an order in a cancellable state whose payment is settled  
When I submit a cancellation reason  
Then I receive a successful response  
And the order status updates to `CANCELLED_REQUIRES_REFUND`  
And I see messaging indicating “manual refund processing required”

### Scenario 3: Work status blocks cancellation
Given I am authenticated and have `ORDER_CANCEL` permission  
And I am viewing an order linked to work that is in a blocking status  
When I submit a cancellation request  
Then the cancellation is rejected  
And I see a clear error message stating cancellation cannot proceed due to work status  
And the order remains not cancelled

### Scenario 4: Cancellation request is rejected due to missing permission
Given I am authenticated without `ORDER_CANCEL` permission  
When I attempt to access the cancel action for an order  
Then the cancel action is not available  
And if I still submit a cancellation request  
Then I receive a `403 Forbidden` response  
And I see an authorization error message

### Scenario 5: Cancellation already in progress
Given I am authenticated and have `ORDER_CANCEL` permission  
And the order status is `CANCELLING`  
When I submit a cancellation request for that order  
Then I receive a `409 Conflict` response  
And I see messaging that cancellation is already in progress  
And the UI refreshes and shows the current cancellation status

### Scenario 6: Downstream failure results in cancellation failed
Given I am authenticated and have `ORDER_CANCEL` permission  
And I am viewing an order in a cancellable state  
When I submit a cancellation request and the backend reports downstream failure  
Then I receive an error response  
And the order status is shown as `CANCELLATION_FAILED` (if returned/loaded)  
And I see messaging that manual intervention is required

---

## 13. Audit & Observability

### User-visible audit data
- Show (when available in order response or cancel response):
  - `cancellationId`
  - `cancelledBy` (if provided)
  - `cancelledAt` (if provided)
  - `reason`
  - `comments` (if provided/allowed)
  - `paymentVoidStatus`
  - `workRollbackStatus`
  - correlation IDs (display only if explicitly deemed user-visible; otherwise keep backend-only)

### Status history
- If backend provides a cancellation history list, render as read-only table (most recent first).
- If not provided, show only latest attempt.

### Traceability expectations
- UI should include correlation id header propagation only if platform standard exists (Open Question). Otherwise rely on backend.

---

## 14. Non-Functional UI Requirements
- **Performance**: cancel submission should show immediate feedback; UI timeout handling must not freeze the screen.
- **Accessibility**: modal is keyboard navigable; errors announced; buttons have accessible labels.
- **Responsiveness**: modal works on tablet/mobile.
- **i18n/timezone**: timestamps displayed in user’s locale/timezone if present; no currency formatting required beyond message text.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide “No cancellation activity recorded” when no cancellation metadata is returned; safe because it does not change domain behavior. (Impacted: UX Summary, Alternate Flows)
- SD-UX-CONCURRENCY-REFRESH: On `409 Conflict`, refresh order detail automatically once and show “in progress” message; safe because it only changes presentation and avoids duplicate submissions. (Impacted: Error Flows, Acceptance Criteria)
- SD-ERR-GENERIC-BANNER: For `500`, show a generic “Manual intervention required” banner without exposing internals; safe because it reduces sensitive leakage and does not alter policy. (Impacted: Business Rules, Error Flows)

---

## 16. Open Questions
1. What is the **exact Moqui screen route** / screen name for Order Detail in this repo (e.g., `/apps/pos/order/detail?orderId=...`), so we can correctly specify transitions and parameters?
2. What is the **frontend-to-backend contract** for cancellation in this Moqui frontend?
   - Endpoint/path or Moqui service name
   - Request/response JSON (confirm fields in §8)
   - Error response schema (do we get stable `errorCode` vs only `message`?)
3. Which roles besides Store Manager can cancel orders? The backend reference includes Service Advisor—confirm if UI should allow this when permission `ORDER_CANCEL` is present.
4. When order is `CANCELLATION_FAILED`, should UI allow a **Retry cancellation** action, or is that strictly handled in an operator/admin dashboard (out of scope)?
5. Is `reason` a fixed enum list (e.g., `CUSTOMER_REQUEST|INVENTORY_UNAVAILABLE|PRICING_ERROR|DUPLICATE_ORDER|OTHER`) and should the UI hardcode it or load it from backend/config?
6. Should the UI display correlation IDs and downstream subsystem details, or are those restricted to logs/admin views?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Order: Cancel Order with Controlled Void Logic  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/83  
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