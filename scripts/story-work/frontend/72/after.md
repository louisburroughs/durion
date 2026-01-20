## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

# 1. Story Header

## Title
[FRONTEND] Payment Reversal: Void Authorization or Refund Captured Payment (with approvals, reasons, and async refund status)

## Primary Persona
Store Manager

## Business Value
Enable safe, permissioned correction of payment mistakes (void auths / refund captured payments), with complete auditability and accurate invoice/payment status updates to prevent financial discrepancies and support reconciliation.

---

# 2. Story Intent

## As a / I want / So that
As a **Store Manager**, I want to **void authorized payments** or **refund captured/settled payments** (including partial refunds where allowed), so that payment corrections are handled safely with proper approvals, reasons, and traceable outcomes.

## In-scope
- UI to initiate **Void** on eligible payments in **AUTHORIZED** state.
- UI to initiate **Refund** on eligible payments in **CAPTURED/SETTLED** state (full and partial per policy).
- Capture **reason code** (+ notes for OTHER and for certain tiers), and show required **approvals** if the amount requires it.
- Display and update **payment reversal status** and **invoice status** (including refund pending lifecycle).
- Display reversal history (void/refund records) with audit metadata (actor, approver, timestamps, reason, notes).
- Handle common error cases and provide actionable messages.

## Out-of-scope
- Implementing payment gateway logic, webhooks, or backend reconciliation.
- Defining or changing tax/accounting posting behavior (frontend only reflects backend status).
- Creating new business policy beyond what backend already decided (e.g., settlement timing rules beyond exposed eligibility).
- Receipt generation/reprint UX unless explicitly returned by backend contract.

---

# 3. Actors & Stakeholders
- **Store Manager (primary)**: initiates void/refund, provides reason/notes, may need additional approvals.
- **District Manager / Finance Approver (secondary)**: provides approval for high-value reversals (if workflow requires separate approval step).
- **Cashier (secondary)**: may execute cash refund operational steps (display-only unless backend requires).
- **Billing/Payment services (system)**: evaluate eligibility, enforce policy, execute reversal, emit events.
- **Accounting (downstream)**: receives reversal events; frontend may show “synced/pending” if exposed.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated.
- User can access the invoice/payment context (tenant/account boundary enforced).
- A payment exists and is linked to an invoice.

## Dependencies (must exist server-side)
- Backend endpoints/services to:
  - Load invoice + payments + reversal history.
  - Determine eligibility and required action (void vs refund) and any approval requirement.
  - Create void/refund request with idempotency and return a status.
  - Expose async refund lifecycle (REQUESTED/PENDING/COMPLETED/FAILED).
- Permission model exposed to frontend (at least via 401/403; ideally via explicit permission flags in response).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Invoice Detail** screen: “Payments” section listing payments with actions.
- Optional: From a **Payment Detail** view (reachable by clicking a payment row).

## Screens to create/modify (Moqui)
1. **Modify** existing Invoice screen (assumed): `InvoiceDetail`
   - Add Payments panel with actions: **Void Authorization**, **Refund Payment**
   - Add Reversal History subpanel (void/refund records)
2. **Create** modal/dialog screen(s) or embedded forms:
   - `VoidPaymentDialog` (form + confirm)
   - `RefundPaymentDialog` (form + confirm + amount input if partial)
3. **Optional**: `RefundRequestDetail` or `PaymentReversalDetail` screen to view a specific reversal record and status history.

## Navigation context
- Route pattern should keep invoice context: `/invoices/:invoiceId` (exact route TBD by repo conventions).
- Dialogs should return to Invoice Detail and refresh data on completion.

## User workflows

### Happy path: Void an authorized payment
1. Manager opens Invoice Detail → Payments.
2. Selects an **AUTHORIZED** payment → clicks “Void Authorization”.
3. Dialog shows amount, tender type, time window/eligibility message (if provided), reason dropdown + notes when required.
4. Submit → success message; payment/reversal status updates; invoice status updates accordingly.

### Happy path: Refund a captured/settled payment (full)
1. Manager selects **CAPTURED/SETTLED** payment → “Refund Payment”.
2. Dialog shows refundable amount; chooses reason + notes if required.
3. Submit → refund created; status becomes PENDING if async; UI shows pending and allows refresh.

### Alternate path: Partial refund
- Manager toggles “Partial refund” (or amount input enabled by default) and enters amount within allowed bounds; submit.

### Alternate path: Requires higher approval
- UI indicates approval required and either:
  - Blocks submission and instructs to request approval (if separate approval flow exists), or
  - Allows submission but backend returns “approval required” with next-step instructions/status.

(Exact approval UX depends on backend contract; see Open Questions.)

---

# 6. Functional Behavior

## Triggers
- User clicks “Void Authorization” or “Refund Payment” on a payment row.

## UI actions
### Payments list
- Each payment row shows:
  - payment state (AUTHORIZED / CAPTURED / SETTLED / etc. as provided)
  - amount, refundable remaining (if provided), tender type, created date
  - action buttons based on eligibility:
    - **Void** visible/enabled only if backend says eligible for void (or state=AUTHORIZED and permitted)
    - **Refund** visible/enabled only if eligible for refund (or state in CAPTURED/SETTLED and permitted)

### Void dialog
- Inputs:
  - `voidReason` (enum)
  - `notes` required when reason=OTHER
- Submit calls backend void service.
- On success: close dialog; refresh invoice/payments; show reversal record entry.

### Refund dialog
- Inputs:
  - `refundReason` (enum)
  - `notes` required when reason=OTHER
  - `refundAmount` required; supports partial if allowed
- Client-side validations enforce numeric format; backend is authoritative for policy (windows, caps, min, max count).
- Submit calls backend refund service.
- On success: show status:
  - If immediate completed: “Refund completed”
  - If async: show “Refund pending” and display pending status in payment/reversal history

## State changes (frontend-visible)
- Payment shows reversal status (voided/refunded/pending/failed) as provided.
- Invoice status updated (backend-driven). Must display `REFUND_PENDING` if returned.

## Service interactions
- Initial load: fetch invoice + payment list + reversal records.
- Action submit: call void/refund endpoint; handle synchronous success and async pending.
- Refresh: after submit, re-fetch invoice details; optionally poll pending refunds.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Reason is required** for both void and refund.
- **Notes required** when reason=OTHER.
- For refund:
  - `refundAmount` must be > 0.
  - Must not exceed backend-provided refundableRemaining (if provided); otherwise allow entry and rely on backend validation.
- If backend returns “approval required”:
  - UI must present a blocking message with next-step guidance (e.g., “Requires District Manager approval”) and store/display the request status if created.

## Enable/disable rules
- Void action disabled if:
  - Payment not in eligible state
  - User lacks permission (403)
  - Backend indicates outside time window without override permission
- Refund action disabled if:
  - No refundable remaining
  - Payment not eligible
  - User lacks permission
  - Backend indicates max partial refund count reached (action disabled if returned as ineligible; otherwise backend error)

## Visibility rules
- Show “Void” only for AUTHORIZED payments (or `eligibleActions` includes VOID).
- Show “Refund” only for CAPTURED/SETTLED payments (or eligibleActions includes REFUND).
- Show approval tier hints (≤$100, etc.) only if backend returns tier metadata; do not hardcode thresholds in UI.

## Error messaging expectations
- 401/403: “You don’t have permission to void/refund payments.”
- 409: “Payment state changed; refresh and try again.”
- 422: Show field-level errors (amount/reason/notes) using backend error details.
- 503: “Payment service unavailable; try again later.”

---

# 8. Data Requirements

## Entities involved (frontend view models)
- Invoice (includes status, totals, amountPaid, amountDue)
- PaymentRecord / PaymentTransaction (id, invoiceId, amount, state, tenderType, gatewayRef, timestamps)
- VoidRecord (id, paymentId, reason, notes, actorUserId, approverUserId?, createdAt, status)
- RefundRecord (id, paymentId, amount, reason, notes, status, actorUserId, approverUserId?, createdAt, completedAt?, failureReason?)
- AuditLog entries (or embedded audit fields on records)

## Fields (type, required, defaults)
### Void request
- `paymentId` (string/uuid, required)
- `reason` (enum VOID_REASON, required)
- `notes` (string, required iff reason=OTHER)
- `idempotencyKey` (string, required if backend requires; otherwise optional)

### Refund request
- `paymentId` (string/uuid, required)
- `amount` (decimal string, required)
- `reason` (enum REFUND_REASON, required)
- `notes` (string, required iff reason=OTHER)
- `idempotencyKey` (string, required if backend requires; otherwise optional)

## Read-only vs editable
- Invoice/payment fields are read-only.
- Only dialog fields (reason, notes, amount) are editable.

## Derived/calculated fields (frontend)
- `refundableRemaining` display: prefer backend-provided value.
- Formatting: currency formatting based on invoice currency (do not infer if not provided).

---

# 9. Service Contracts (Frontend Perspective)

> Backend endpoints are referenced conceptually; exact names/paths must match the Moqui services/screens in this repo.

## Load/view calls
- `GET InvoiceDetail(invoiceId)` returns:
  - invoice summary + status
  - payments list with states and eligibility (preferred: `eligibleActions`, `refundableRemaining`, `requiresApproval`, `approvalTier`)
  - reversal history (refund/void records) OR separate endpoints

## Create/update calls
- `POST VoidPaymentAuthorization`
  - request: paymentId, reason, notes, idempotencyKey?
  - response: voidRecord + updated payment/invoice status (or ids to refresh)

- `POST CreateRefund`
  - request: paymentId, amount, reason, notes, idempotencyKey?
  - response: refundRecord with status (REQUESTED/PENDING/COMPLETED/FAILED) + updated invoice/payment status

## Submit/transition calls
- None beyond void/refund commands.

## Error handling expectations
- Map backend error codes:
  - 422 validation error → show field errors
  - 409 state conflict → refresh prompt
  - 403 forbidden → permission toast + disable action
  - 503 downstream unavailable → retry suggestion
- Do not log sensitive payment data; show masked tender info only if provided.

---

# 10. State Model & Transitions

## Allowed states (as surfaced to UI)
### Payment
- AUTHORIZED → (void) VOIDED
- CAPTURED/SETTLED → (refund) REFUND_REQUESTED / REFUND_PENDING → REFUNDED or REFUND_FAILED
(Exact state strings must match backend; UI should be tolerant and display unknown states generically.)

### Invoice
- Existing states per billing domain plus:
  - `REFUND_PENDING` (display and treat as non-final)
- Invoice status changes are backend-driven; UI simply reflects.

## Role-based transitions
- Store Manager: can initiate void/refund if has `VOID_PAYMENT` / `REFUND_PAYMENT`.
- Higher tiers: District Manager / Finance approval may be required for large refunds (workflow TBD).

## UI behavior per state
- If refund status is PENDING: show banner “Refund pending” and show last updated time; allow manual refresh.
- If FAILED: show failure reason (if provided) and guidance (contact finance/support).

---

# 11. Alternate / Error Flows

## Validation failures
- Missing reason/notes/amount → inline errors; prevent submit.
- Amount exceeds refundable remaining → show backend message and keep dialog open.

## Concurrency conflicts
- Payment moved from AUTHORIZED to CAPTURED while dialog open → backend returns 409; UI shows “Payment state changed; refresh.”

## Unauthorized access
- 403 on action: show message; action buttons become disabled for that session context.

## Empty states
- No payments: show “No payments found for this invoice.”
- No reversal history: show “No voids/refunds recorded.”

## Async refund pending too long
- If backend provides age/ETA: display “Pending since …”.
- Provide manual refresh; do not invent automated retry/recovery beyond optional polling (see Open Questions).

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Void an authorized payment successfully
Given I am a Store Manager with permission to void payments  
And an invoice has a payment in AUTHORIZED state eligible for void  
When I open the Void Authorization dialog and select a void reason  
And I submit the void request  
Then the system shows a success confirmation  
And the payment is shown as VOIDED (or equivalent backend status)  
And the invoice status and payment totals are refreshed from the backend  
And the void record appears in reversal history with my user identity and reason

## Scenario 2: Void requires notes when reason is OTHER
Given I am initiating a void for an eligible AUTHORIZED payment  
When I select void reason OTHER  
And I do not enter notes  
Then the UI prevents submission and displays a required-field error for notes

## Scenario 3: Refund a captured payment and receive pending status
Given I am a Store Manager with permission to refund payments  
And an invoice has a payment in CAPTURED/SETTLED state eligible for refund  
When I submit a refund for the full refundable amount with a valid refund reason  
Then the UI displays the refund record status returned by the backend  
And if the status is PENDING, the invoice displays REFUND_PENDING (if provided)  
And reversal history shows the refund with status PENDING  
And I can refresh and see the updated status when it changes

## Scenario 4: Partial refund amount validation
Given a payment has refundable remaining amount of $50.00  
When I enter a refund amount of $60.00 and submit  
Then the UI displays an actionable validation error from the backend  
And no refund is recorded as completed  
And the dialog remains open for correction

## Scenario 5: Permission denied
Given I lack REFUND_PAYMENT permission  
When I attempt to open or submit a refund for a payment  
Then the UI shows an authorization error  
And the refund action is disabled or blocked

## Scenario 6: Payment state changed (conflict)
Given I opened a refund/void dialog for a payment  
And the payment state changes before I submit  
When I submit the request  
Then the UI shows a conflict error instructing me to refresh  
And the UI reloads the latest invoice/payment state on user action

---

# 13. Audit & Observability

## User-visible audit data
- Reversal history list shows:
  - reversal type (VOID/REFUND)
  - amount (for refunds)
  - reason + notes (notes visible to authorized roles)
  - initiated by (actor) and approved by (if applicable)
  - timestamps
  - status + failure reason (if failed)

## Status history
- If backend provides status transitions for refund lifecycle, display a simple timeline (Requested → Pending → Completed/Failed).

## Traceability expectations
- All void/refund submissions include a client-generated correlation/idempotency key if required by backend.
- UI includes invoiceId/paymentId in logs/telemetry events (no PAN/CVV).

---

# 14. Non-Functional UI Requirements

- **Performance:** Invoice detail (including payments) should render with standard pagination if lists are large (do not block main thread).
- **Accessibility:** Dialogs keyboard-navigable; form fields have labels; errors announced; focus management on open/close.
- **Responsiveness:** Dialog usable on tablet; tables adapt to narrow widths.
- **i18n/timezone/currency:** Dates shown in user locale/timezone; currency formatting uses invoice currency if provided; do not assume USD if absent.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide clear empty-state copy for no payments / no reversal history; qualifies as UI ergonomics only; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Paginate payments/reversal history lists when count is large; qualifies as UI ergonomics only; impacts UX Summary, Data Requirements.
- SD-OBS-CORRELATION-ID: Include correlation id in frontend logs/requests when supported; qualifies as observability boilerplate; impacts Service Contracts, Audit & Observability.

---

# 16. Open Questions

1. What are the **exact Moqui screen names/routes** for Invoice Detail and Payment Detail in this repo (to correctly place transitions and screen paths)?
2. What are the **exact backend service names/endpoints** and request/response schemas for:
   - invoice detail load including payments + eligibility
   - void authorization
   - refund (full/partial)
   - refund status query/history
3. Does the backend require an **idempotency key** from the client for void/refund requests? If yes, what header/field name?
4. How is the **tiered approval workflow** represented for the frontend?
   - Is it a single call that may return “approval required”, or a separate Request→Approve→Execute flow with additional screens?
5. Should the frontend **poll** PENDING refunds automatically (and at what interval), or only provide manual refresh? (Do not invent retry/recovery policy.)
6. For **mixed tender / multi-payment** invoices: does the UI allow selecting which payment(s) to refund, and can one refund request target multiple payments?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Payment: Void Authorization or Refund Captured Payment — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/72

Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Void Authorization or Refund Captured Payment

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Store Manager**, I want to void/refund payments so that corrections are handled safely.

## Details
- Voids before settlement; refunds after settlement.
- Requires permission and reason.
- Partial refunds supported (basic).

## Acceptance Criteria
- Void/refund returns success or actionable error.
- Invoice status updated.
- Audit includes reason/approver.

## Integrations
- Payment service API; accounting status updated accordingly.

## Data / Entities
- RefundRecord, VoidRecord, PaymentTransactionRef, AuditLog

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