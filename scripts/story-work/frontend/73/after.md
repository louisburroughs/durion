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

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] Payment: Initiate Card Authorization and Capture (Checkout)

## Primary Persona
Cashier

## Business Value
Enable cashiers to take compliant card payments against an invoice at checkout (sale/capture and auth→capture), generate a receipt, and ensure billing/audit traceability for accounting reconciliation.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Cashier  
- **I want** to initiate a card payment for an invoice (sale/capture or auth then capture)  
- **So that** the invoice can be paid at checkout, a receipt is produced, and the transaction is auditable without storing card data.

## In-scope
- From an invoice checkout context, start a card payment
- Support **SALE_CAPTURE** default and **AUTH_ONLY → CAPTURE** flow (per backend decisions)
- Display payment status/progress and final outcome
- Persist and show only **token/transaction references** (no PAN/CVV)
- Trigger receipt generation and allow immediate receipt viewing/printing entrypoint
- Record/show audit-friendly metadata visible to user (who/when/terminal) where exposed by backend
- Handle error responses and “unknown outcome” states safely (frontend UX + polling/inquiry hooks)

## Out-of-scope
- Multi-gateway selection/failover UI
- Refunds/chargebacks UI
- Back-office “manual capture later” workflows (unless explicitly needed for POS checkout screen)
- Invoice regeneration/voiding flows
- Receipt reprint authorization flow details (separate story)

---

# 3. Actors & Stakeholders
- **Cashier**: initiates and completes payment at POS
- **Customer**: presents card and may request receipt email/print
- **Manager** (optional): approval if amount exceeds cashier limit or for flow selection (permission-based)
- **Billing/Payment services (backend)**: create payment intent, authorize, capture, update invoice, emit events
- **Receipt service (backend)**: generates receipt after capture
- **Accounting (downstream)**: receives payment success event (not directly handled by frontend)

---

# 4. Preconditions & Dependencies
- User is authenticated in POS
- Cashier has permission to process payments (backend: `PROCESS_PAYMENT`)
- Invoice exists and is payable (outstanding balance > 0) and in a state allowing payment
- POS terminal/location is configured with a gateway (single gateway MVP)
- Frontend can call Moqui screens/services that proxy to backend payment APIs
- Backend provides endpoints for:
  - create/initiate payment intent (sale/capture or auth-only)
  - capture against existing auth (for same-session flow)
  - payment status inquiry (for unknown outcomes) and/or status fetch by intent id
  - receipt retrieval after success (or receipt id returned)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Invoice Detail / Checkout** screen: action “Pay by Card”
- (Optional) from “Outstanding Balance” banner or payment panel

## Screens to create/modify
1. **Modify**: `apps/pos/screen/Invoice/InvoiceDetail.xml` (or equivalent invoice checkout screen)
   - Add “Pay by Card” action and payment status panel (if not already present)
2. **Create**: `apps/pos/screen/Payment/CardPaymentFlow.xml` (new dedicated flow screen)
   - Hosts the payment initiation UI, progress, and outcome handling
3. **Create/Modify**: `apps/pos/screen/Receipt/ReceiptView.xml` (if receipt viewer exists, wire to payment outcome)

> Exact file paths/names may differ in repo; implement using existing Moqui screen hierarchy and menu patterns from README conventions.

## Navigation context
- Route includes `invoiceId`
- Return path back to Invoice Detail after completion/cancel
- Preserve correlation identifiers in URL params only if non-sensitive (no tokens)

## User workflows

### Happy path A: SALE_CAPTURE (default)
1. Cashier clicks “Pay by Card”
2. Frontend creates payment intent as SALE_CAPTURE for amount due
3. UI shows “Processing…” with spinner and disables duplicate submission
4. On success, show “Payment captured” + receipt link/button, and invoice shows Paid/updated balance

### Happy path B: AUTH_ONLY then CAPTURE (same session)
1. Cashier selects (if permitted) “Authorize now, capture after confirmation”
2. Create auth-only intent; on success show “Authorized”
3. Cashier clicks “Capture” (immediate) and completes capture
4. On capture success show receipt link/button

### Alternate path: Amount exceeds threshold / requires approval
- If backend returns an authorization failure/permission requirement, show blocking message and guide to request manager credentials per existing POS pattern (if exists)

### Cancel/Back
- Cashier can cancel out of the UI before submission; once submitted, cancellation behavior depends on backend support (see Open Questions)

---

# 6. Functional Behavior

## Triggers
- User action “Pay by Card”
- User action “Capture” (for auth-only flow)
- Screen mount loads invoice/payment context
- Automatic status polling when payment outcome is pending/unknown

## UI actions
- Start payment:
  - Choose flow type (default SALE_CAPTURE; AUTH_ONLY available only if permission `SELECT_PAYMENT_FLOW`)
  - Confirm amount (read-only from invoice balance due; editable only if partial payments are allowed—currently unclear)
  - Submit “Process Card”
- Show progress with deterministic states:
  - “Creating payment…”, “Authorizing…”, “Capturing…”, “Finalizing…”
- Show outcome summary:
  - Success: captured amount, timestamp, gateway reference (masked), receipt action
  - Failure: error reason, retry button (only when safe), and “Return to invoice”
  - Unknown/timeout: show “We’re confirming status…” and continue status inquiry/poll

## State changes (frontend view state)
- Local UI state machine:
  - `idle` → `submitting` → (`succeeded` | `failed` | `unknown`)  
  - For auth-only: `authorized` → `capturing` → outcome
- Disable primary actions while `submitting/capturing` to prevent double-charge attempts; allow “Close” only after resolution or if backend indicates safe to exit.

## Service interactions
- Load invoice summary + existing payment intents (if any) for this invoice
- Initiate payment intent (sale/capture or auth-only)
- If unknown outcome, call status inquiry using idempotency key/intent id (backend supports inquiry per decisions)
- On success, load receipt content/metadata and provide print/email actions per existing receipt screen capabilities

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Must have `invoiceId`
- Invoice must have `balanceDue > 0`
- Payment amount defaults to full balance due
- Enforce **no PAN/CVV storage** in frontend state, logs, or persisted local storage
- Flow selection:
  - Default is **SALE_CAPTURE**
  - AUTH_ONLY selectable only if backend indicates invoice flags or user permission allow it (backend decision references invoice flags such as `requiresManagerApproval`, `amountMayChange`, partial fulfillment). Frontend must rely on backend-provided capability flags; do not infer.

## Enable/disable rules
- “Pay by Card” disabled if invoice is already paid/void or `balanceDue == 0`
- “Capture” enabled only when intent status is `AUTHORIZED` and user has permission for capture in-session (backend: `PROCESS_PAYMENT`; later capture is `MANUAL_CAPTURE`—not in scope unless returned by backend)

## Visibility rules
- Show flow selector only if user has `SELECT_PAYMENT_FLOW` and backend indicates auth-only is allowed for this invoice
- Show manager-approval messaging only when backend returns explicit error codes for limit/approval

## Error messaging expectations
- Map backend errors to user-safe messages:
  - 422: show validation message (missing customer data, invalid state)
  - 409: show conflict (“Invoice already paid / payment already processed”)
  - 503: show service unavailable + retry guidance
  - Gateway decline: show “Card declined” without sensitive details; include a reason code only if non-sensitive and provided
- Unknown outcome/timeouts: do not suggest retry that could double-charge; instead “Checking status…” and offer “Return to invoice” once status is known or inquiry fails.

---

# 8. Data Requirements

## Entities involved (frontend-visible)
- **Invoice** (read): `invoiceId`, `status`, `balanceDue`, `currencyUomId`, `customerAccountId`
- **PaymentIntent** (read/create): `paymentIntentId`, `invoiceId`, `paymentMethodType= कार्ड`, `flowType (SALE_CAPTURE|AUTH_ONLY)`, `status`, `authorizedAmount`, `capturedAmount`, `voidedRemainderAmount`, `gatewayProvider`, timestamps, `idempotencyKey`
- **PaymentTransactionRef** (read): `gatewayTransactionId`, `authCode`, `cardBrand`, `last4` (masked)
- **Receipt** (read): `receiptId`, `status`, `deliveryStatus`, content reference
- **AuditLog/AuditEvent** (read optional): `eventType`, `actorUserId`, `timestamp`, `entityRef`

## Fields (type, required, defaults)
- `invoiceId` (string, required)
- `amount` (decimal, required; default = invoice balance due; editability TBD)
- `currencyUomId` (string, required; default from invoice)
- `flowType` (enum, required; default SALE_CAPTURE)
- `idempotencyKey` (string, required; generated client-side per attempt; must not include PII)

## Read-only vs editable by state/role
- Amount:
  - Read-only unless partial payments are explicitly allowed (Open Question)
- Flow selector:
  - Read-only/hidden unless permission and backend allows
- Transaction references:
  - Always read-only and masked

## Derived/calculated fields
- “Balance due after payment” is derived from invoice refresh after payment success (do not calculate locally beyond display hints)

---

# 9. Service Contracts (Frontend Perspective)

> Moqui screen actions should call services (e.g., `services.xml` or remote REST) consistent with repo conventions. Endpoint names below are **contract placeholders** unless already defined; align to actual backend API routing.

## Load/view calls
- `GET InvoiceSummary(invoiceId)` → invoice status, balance due, currency
- `GET PaymentIntentsByInvoice(invoiceId)` → list current/previous intents for display and to prevent duplicates
- `GET PaymentIntent(paymentIntentId)` → latest status + refs (used for polling)

## Create/update calls
- `POST CreatePaymentIntent`
  - Request: `invoiceId`, `amount`, `currency`, `flowType`, `idempotencyKey`, `terminalId/locationId` (if required)
  - Response: `paymentIntentId`, `status`, refs (masked), receipt pointer if immediately available
- `POST CapturePayment(paymentIntentId)`
  - Request: `amount` (if partial capture supported; else omit), `idempotencyKey`
  - Response: updated intent + receipt pointer

## Submit/transition calls
- `POST PaymentStatusInquiry(paymentIntentId or idempotencyKey)` (for unknown outcomes)
  - Response: definitive status or `PENDING`

## Error handling expectations
- All calls return structured errors with:
  - `errorCode`, `message`, `fieldErrors[]` (if 422)
  - `correlationId` for support
- Frontend must display correlationId in an expandable “Details” section (non-sensitive)

---

# 10. State Model & Transitions

## Allowed states (as presented to UI)
(Exact enum values must match backend; map if backend differs)
- `CREATED` / `PENDING`
- `AUTHORIZED`
- `CAPTURED` (success)
- `DECLINED` / `FAILED`
- `EXPIRED` (auth window exceeded)
- `UNKNOWN` (client timed out; awaiting inquiry result)

## Role-based transitions
- Cashier with `PROCESS_PAYMENT`:
  - Can initiate SALE_CAPTURE
  - Can initiate AUTH_ONLY if `SELECT_PAYMENT_FLOW`
  - Can capture immediately after auth (same session) if backend allows
- Manager permissions (if applicable):
  - `OVERRIDE_PAYMENT_LIMIT` for amounts over threshold (backend defined $500 default configurable)

## UI behavior per state
- `PENDING`: show progress + disable submit
- `AUTHORIZED`: show “Capture” primary action (if allowed), plus “Void” only if explicitly in scope (not in this story)
- `CAPTURED`: show success + receipt actions, navigate back to invoice
- `FAILED/DECLINED`: show failure + safe retry (new idempotency key) only if backend indicates no side effect occurred
- `UNKNOWN`: lock re-submit; run inquiry/poll loop and present outcome

---

# 11. Alternate / Error Flows

## Validation failures
- Missing `invoiceId`: show error and route back to invoice list
- Invoice not payable (0 balance / Paid / Void): show “No payment needed” and disable actions

## Concurrency conflicts
- If another terminal/user pays invoice while this screen is open:
  - backend returns 409 or invoice refresh shows paid
  - UI stops polling and shows “Invoice already paid” with return action

## Unauthorized access
- If user lacks `PROCESS_PAYMENT`: hide entry point; if deep-linked, show 403 screen

## Empty states
- No prior payment intents: show empty “No card payments attempted yet”
- Receipt not yet available after capture: show “Receipt generating…” and poll receipt status if endpoint exists; otherwise provide “Refresh” and return to invoice

## Downstream/gateway timeouts
- If create/capture times out:
  - UI transitions to `UNKNOWN`
  - call status inquiry; if still unknown after N attempts (safe default—see below), show “Unable to confirm; check invoice payment status” and navigate back without offering blind retry

---

# 12. Acceptance Criteria

### Scenario 1: Start SALE_CAPTURE card payment and succeed
**Given** a cashier with `PROCESS_PAYMENT` is viewing an invoice with `balanceDue > 0`  
**When** the cashier selects “Pay by Card” and submits payment with flow type `SALE_CAPTURE`  
**Then** the UI shows processing state and prevents duplicate submission  
**And** on success the UI shows captured amount, masked card details (brand/last4 only), and a receipt action  
**And** returning to the invoice shows updated paid status or reduced balance due per backend response.

### Scenario 2: AUTH_ONLY then CAPTURE in the same session
**Given** a cashier with `PROCESS_PAYMENT` and `SELECT_PAYMENT_FLOW` is viewing an eligible invoice  
**When** the cashier selects flow type `AUTH_ONLY` and submits  
**Then** the UI shows the payment intent in `AUTHORIZED` state with a “Capture” action  
**When** the cashier clicks “Capture”  
**Then** the UI completes capture and shows receipt access on `CAPTURED`.

### Scenario 3: Card declined (non-success)
**Given** a cashier initiates a card payment  
**When** the backend returns a declined/failed status with a non-sensitive reason  
**Then** the UI displays “Card declined” (no PAN/CVV) and provides options to retry or choose another payment method  
**And** the UI does not mark the invoice as paid.

### Scenario 4: Timeout/unknown outcome triggers inquiry instead of blind retry
**Given** a cashier submits a card payment  
**When** the request times out or returns an unknown outcome  
**Then** the UI enters “Confirming status” state and calls payment status inquiry  
**And** the UI does not enable “Retry” until a definitive failed status is returned  
**And** once definitive status is received, the UI reflects that outcome.

### Scenario 5: Prevent storing card data
**Given** the cashier completes any payment attempt  
**When** the UI stores or logs client state  
**Then** no PAN, CVV, or full track data exists in browser storage, logs, or URLs  
**And** only token/transaction references and masked card metadata may be displayed.

### Scenario 6: Permission-gated flow selection
**Given** a cashier without `SELECT_PAYMENT_FLOW`  
**When** the cashier opens the card payment screen  
**Then** the flow type defaults to `SALE_CAPTURE` and AUTH_ONLY selection is not available.

---

# 13. Audit & Observability

## User-visible audit data
- Show (if provided): payment timestamp, cashier user id/name, terminal/location id, correlation id
- Show gateway references only in masked form (txn id may be shown if allowed by policy)

## Status history
- Display a simple timeline for the current payment intent: created → authorized → captured/failed (based on backend fields)

## Traceability expectations
- All frontend calls include a correlation id header (or Moqui standard) and pass/receive `idempotencyKey`
- UI should display backend `correlationId` for support without exposing sensitive payloads

---

# 14. Non-Functional UI Requirements

- **Performance**: Payment screen actions should respond to user input immediately; polling intervals must not overload backend
- **Accessibility**: All actions keyboard accessible; progress updates announced (ARIA live region) for screen readers
- **Responsiveness**: Works on typical POS tablet and desktop breakpoints
- **i18n/timezone/currency**: Amount formatting uses invoice currency; timestamps rendered in location timezone (if available) or user locale; no currency conversions performed client-side

---

# 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**: Provide standard empty-state messaging for “no prior payment intents” because it’s UI ergonomics and does not affect domain policy. (Sections: UX Summary, Alternate/Empty states)
- **SD-UX-PAGINATION-NONE-SMALL-LISTS**: Assume payment intents list is short and can render without pagination in POS context. Safe UI assumption only. (Sections: UX Summary, Data Requirements)
- **SD-ERR-HTTP-MAP**: Map 422/409/503 to validation/conflict/unavailable UI messages when backend implies these statuses. Standard error handling only. (Sections: Business Rules, Error Flows, Acceptance Criteria)
- **SD-OBS-CORRELATION-ID**: Include correlation id in requests and surface response correlation id in UI details. Observability boilerplate consistent with workspace defaults. (Sections: Service Contracts, Audit & Observability)

---

# 16. Open Questions

1. **Partial payments**: Is partial payment (amount less than invoice balance due) allowed at POS for card payments in this flow? If yes, what constraints (min amount, remaining balance handling, multiple payments per invoice)?
2. **Failure handling policy**: For gateway declines vs technical failures vs unknown outcomes, what exact retry affordances should the UI provide (immediate retry allowed? require manager? require waiting)?
3. **Void/cancel behavior**: If an AUTH_ONLY succeeds but cashier cancels the checkout, should the UI offer “Void authorization” (requires `VOID_PAYMENT`) or leave the auth to expire automatically?
4. **Receipt delivery**: Print is default per backend decision; is **email receipt** in-scope here, and what is the required consent UX and email capture source (CRM vs prompt)?
5. **Concrete service endpoints**: What are the exact Moqui service names / REST routes for create intent, capture, inquiry, receipt fetch in this frontend repo (so screens can be wired without guesswork)?
6. **Invoice state update**: After capture, should the frontend rely on an event-driven refresh, or immediately re-fetch invoice summary; is there a canonical “invoice payment status” endpoint?

---

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Payment: Initiate Card Authorization and Capture — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/73

Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Initiate Card Authorization and Capture

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want to accept card payments so that invoices can be paid at checkout.

## Details
- Support auth then capture (or sale/capture).
- Store only tokens + transaction IDs.
- Produce receipt.

## Acceptance Criteria
- Auth/capture outcomes handled.
- Receipt produced.
- No card data stored.
- Audit recorded.

## Integrations
- Payment service API integration; accounting notified after success.

## Data / Entities
- PaymentIntent, PaymentTransactionRef, Receipt, AuditLog

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