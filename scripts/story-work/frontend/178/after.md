## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting: Apply Payment to Invoice (Single Payment → One or More Invoices)

### Primary Persona
Accounting Clerk (human user) operating within the Accounting domain UI

### Business Value
Ensure cleared payments are applied to invoices accurately and atomically, keeping invoice balances/statuses correct, producing auditable application records, and handling overpayments via explicit customer credit—reducing AR reconciliation errors and audit risk.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Accounting Clerk  
- **I want** to apply a cleared payment to one or more open invoices (with validation, atomic submission, and clear outcome messaging)  
- **So that** invoice balances/statuses update correctly, remaining payment value is handled deterministically (including customer credit for overpayment), and the action is auditable and reversible.

### In-scope
- A dedicated frontend workflow to:
  - Load a cleared/available payment and its unapplied amount
  - Search/select eligible invoices for the same customer & currency
  - Allocate amounts across one or more invoices
  - Validate constraints before submit
  - Submit the application as a single atomic command with an idempotency key (`applicationRequestId`)
  - Display resulting invoice statuses/balances and any created customer credit
  - View application records created by the submission

### Out-of-scope
- Payment authorization/capture/clearing lifecycle (owned by Payment domain)
- Creating/editing invoices (Billing/Work Execution domains)
- Reversing/unapplying a payment application (mentioned as a rule but not specified as a UI in the provided frontend story)
- GL posting details, chart of accounts, posting rule configuration
- Multi-currency conversions (currency must match; no FX)

---

## 3. Actors & Stakeholders
- **Primary user:** Accounting Clerk
- **Secondary stakeholders:** Finance leadership, Auditors/Compliance
- **System interactions:** Moqui backend services for Accounting domain (apply command, view payment/invoices, view results)

---

## 4. Preconditions & Dependencies

### Preconditions
- Payment has been cleared/settled upstream and is represented in Accounting as an available payment record (backend reference: `ReceivablePayment` with `status=AVAILABLE` and `unappliedAmount > 0`).
- User is authenticated.
- User is authorized (permission must exist for applying payments; exact permission string is an open question).

### Dependencies
- Backend endpoints/services exist (or will exist) consistent with backend story #114:
  - View payment availability (by `paymentId`)
  - Search/list eligible invoices for a customer
  - Submit atomic apply request:
    - `POST /accounting/payments/{paymentId}/applications` with `applicationRequestId` and allocation list
  - Return created `PaymentApplication` records and (if applicable) created `CustomerCredit`

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Accounting navigation: **Accounting → Receivables → Payments → Apply to Invoices**
- Deep link route supported:
  - `/accounting/payments/:paymentId/apply` (paymentId required)

### Screens to create/modify
1. **New Screen:** `apps/accounting/screen/receivables/PaymentApply.xml` (name indicative)
   - Purpose: Apply one payment across one or more invoices
2. **Optional supporting screen (if not existing):**
   - `PaymentView.xml` to view payment details & existing applications (can be a simple view section embedded in apply screen)

### Navigation context
- Breadcrumb: Accounting / Receivables / Payments / Apply
- Back navigation returns to payment list or payment detail (depends on entry path)

### User workflows

#### Happy path: apply payment across invoices
1. User opens Apply screen for a payment.
2. System loads payment details and shows `totalAmount`, `unappliedAmount`, `currency`, `customer`.
3. System loads list/search of eligible invoices for same customer/currency with `balanceDue > 0` and applicable states.
4. User selects one or more invoices and enters `amountToApply` per invoice.
5. UI validates totals and per-invoice constraints.
6. User submits.
7. On success, UI shows confirmation + results:
   - Created application records
   - Updated invoice balances/statuses
   - If overpayment credit created: show credit identifier and amount

#### Alternate path: apply full amount to a single invoice
- Same as above, but single invoice selection; “Apply full remaining amount” convenience action is allowed (safe default UX).

#### Error paths
- Validation errors returned by service (invoice not applicable, insufficient funds, currency mismatch) displayed inline and/or as a banner, with no partial updates shown as successful.
- Concurrency conflicts (invoice balance changed; payment unapplied changed) prompt user to reload and re-allocate.

---

## 6. Functional Behavior

### Triggers
- Screen load with `paymentId`
- User actions: select invoice(s), enter allocation amounts, submit application

### UI actions
- **Load payment** on screen init:
  - Fetch payment availability record by `paymentId`
- **Load invoices**:
  - Fetch eligible invoices for `customerId` and `currencyUomId` from payment record
  - Support filtering by invoice number and status (safe default)
- **Allocate amounts**:
  - For each selected invoice, user enters `amountToApply` (decimal currency)
  - Provide actions:
    - “Apply remaining” to auto-fill amount up to invoice balance (safe default)
    - “Clear allocations”
- **Submit**:
  - Generate `applicationRequestId` (UUID) client-side per submission attempt
  - Send allocations list to backend
  - Disable submit while request in flight; prevent double-submit

### State changes (frontend view state)
- `screenState`: Loading → Ready → Submitting → Success | Error
- Store the submission response:
  - application records
  - updated invoices (or re-fetch invoices)
  - created customer credit (if any)

### Service interactions
- View services: payment details, eligible invoices
- Command service: apply payment allocations atomically
- Error mapping:
  - validation errors mapped to field/invoice rows and banner
  - conflicts mapped to “Reload needed” UI

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, before submit)
For each allocation row `{invoiceId, amountToApply}`:
- `amountToApply` required for selected invoices
- `amountToApply > 0`
- `amountToApply <= invoice.balanceDue` (based on loaded data; still enforced by backend)
Across all selected invoices:
- `sum(amountToApply) <= payment.unappliedAmount` (based on loaded data; still enforced by backend)
Eligibility rules:
- Invoices must not be in non-applicable statuses (backend reference: not `PaidInFull`, `Voided`, `Cancelled`; exact status values are an open question for frontend display mapping)

### Enable/disable rules
- Submit disabled if:
  - payment is not `AVAILABLE`
  - `unappliedAmount <= 0`
  - no invoices selected
  - any selected invoice missing/invalid `amountToApply`
  - sum exceeds `unappliedAmount`

### Visibility rules
- Show “Overpayment will create customer credit” informational text when:
  - payment total > sum(amountToApply) AND the backend policy is to convert remainder to credit on submit  
  (Note: backend story says credit created if payment value exceeds application total; UI should reflect this clearly.)

### Error messaging expectations
- Use backend error codes when available and present actionable guidance:
  - `VALIDATION_ERROR:INVOICE_NOT_APPLICABLE` → “Invoice is not eligible for payment application (status prevents applying).”
  - `VALIDATION_ERROR:INSUFFICIENT_FUNDS` → “Applied total exceeds remaining unapplied amount.”
  - `VALIDATION_ERROR:CURRENCY_MISMATCH` → “Payment currency must match invoice currency.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `ReceivablePayment` (or equivalent accounting payment-availability record)
- `Invoice` (read-only for this workflow)
- `PaymentApplication` (result records)
- `CustomerCredit` (result record when overpayment)

### Fields

#### Payment (ReceivablePayment)
- `paymentId` (string/UUID; required; route param)
- `customerId` (string/UUID; required)
- `currencyUomId` (string; required, e.g., `USD`)
- `totalAmount` (decimal; required)
- `unappliedAmount` (decimal; required)
- `status` (enum/string; required; must allow `AVAILABLE`)
- `clearedAt` (datetime; required)
- Optional display:
  - `paymentRef` / external reference (if available)

#### Invoice (for selection)
- `invoiceId` (string/UUID; required)
- `invoiceNumber` (string; required for display/search) **(open question if available)**
- `status` (enum/string; required)
- `balanceDue` (decimal; required)
- `invoiceDate` (date/datetime; optional)
- `dueDate` (date; optional)

#### Allocation input (client-only until submit)
- `invoiceId` (required)
- `amountToApply` (decimal; required; currency-scale precision)

#### Submit result
- `PaymentApplication`:
  - `paymentApplicationId` (required)
  - `paymentId`, `invoiceId`, `customerId`, `currencyUomId` (required)
  - `appliedAmount` (required)
  - `applicationTimestamp` (required)
  - `applicationRequestId` (required)
- `CustomerCredit` (if created):
  - `creditId` (required)
  - `amount` (required)
  - `currencyUomId` (required)
  - `sourcePaymentId` (required)
  - `createdAt` (required)

### Read-only vs editable
- Payment fields: read-only
- Invoice fields: read-only
- `amountToApply`: editable per selected invoice row only

### Derived/calculated fields (frontend)
- `appliedTotal = sum(amountToApply)` (client computed)
- `remainingAfterApply = payment.unappliedAmount - appliedTotal` (client computed; informational)

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may use REST endpoints, screen transitions calling services, or `service-call` within screens. Names below specify required behavior; exact service names are open questions unless already standardized in the repo.

### Load/view calls

1. **Load payment availability**
- **Request:** `GET /accounting/payments/{paymentId}`
- **Response (min):** ReceivablePayment fields listed above + optionally existing applications summary
- **Errors:**
  - 404 → show “Payment not found”
  - 403 → show “Not authorized”

2. **Load eligible invoices**
- **Request:** `GET /accounting/customers/{customerId}/invoices?currencyUomId=XXX&eligibleForPayment=true`
- **Response:** list of invoices with fields above
- **Errors:**
  - 503 (dependency down) → show retry option

### Create/update calls

3. **Apply payment allocations (atomic command)**
- **Request:** `POST /accounting/payments/{paymentId}/applications`
- **Body:**
  - `applicationRequestId` (UUID; required; idempotency)
  - `applications`: array of `{ invoiceId, amountToApply }` (required; at least 1)
- **Response (success):**
  - created `PaymentApplication` records
  - updated payment (`unappliedAmount`, maybe status)
  - updated invoices or an indicator to re-fetch
  - `customerCredit` object when remainder becomes credit
- **Errors (expected):**
  - 400 validation: includes error code(s) listed in business rules
  - 409 conflict: optimistic locking / changed balances
  - 403 unauthorized

### Error handling expectations
- Display backend-provided `errorCode` and `message` (message sanitized) in a user-friendly mapping.
- For per-invoice errors, highlight the invoice row(s) affected when backend returns invoiceId-specific errors (open question: error response shape).

---

## 10. State Model & Transitions

### Allowed states (domain concepts surfaced in UI)
- Payment availability status: `AVAILABLE` (required for apply)
  - Other statuses may exist (e.g., `FULLY_APPLIED`, `ON_HOLD`) **(open question)**
- Invoice statuses relevant to eligibility:
  - Eligible: `Open`, `PartiallyPaid` (from backend reference)
  - Ineligible: `PaidInFull`, `Voided`, `Cancelled` (from backend reference)

### Role-based transitions
- User with apply permission can execute “Apply” command.
- Others can view but cannot submit (UI should hide/disable submit) **(exact permission open question)**

### UI behavior per state
- If payment status not `AVAILABLE` or `unappliedAmount <= 0`:
  - screen shows payment details + existing applications
  - allocation and submit controls disabled with explanation

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- User enters amount > invoice balanceDue → inline error, submit disabled
- User enters total > payment unappliedAmount → show banner “Total exceeds remaining payment amount”, submit disabled

### Backend validation failures
- Invoice becomes ineligible between load and submit:
  - backend returns `INVOICE_NOT_APPLICABLE`
  - UI shows error and offers “Reload invoices”
- Insufficient funds due to concurrent application elsewhere:
  - backend returns `INSUFFICIENT_FUNDS` or 409
  - UI prompts reload payment and re-allocate

### Concurrency conflicts
- If backend indicates optimistic lock conflict (409):
  - UI shows “Data changed since you started. Reload required.”
  - Provide “Reload” action (re-fetch payment + invoices; clear allocations)

### Unauthorized access
- 403 on load or submit:
  - show not authorized page/message
  - do not reveal sensitive payment/invoice details beyond what is already loaded

### Empty states
- No eligible invoices:
  - show “No open invoices available for this customer in this currency.”
  - still allow apply? No—submit disabled; user can navigate to invoice list (link)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Load apply screen with available payment
**Given** I am an authenticated Accounting Clerk with permission to apply payments  
**When** I open `/accounting/payments/{paymentId}/apply` for a payment with `status=AVAILABLE` and `unappliedAmount>0`  
**Then** the system displays payment details including `unappliedAmount` and `currency`  
**And** the system lists eligible invoices for the same customer and currency with `balanceDue>0`.

### Scenario 2: Apply full payment to one invoice successfully
**Given** an available payment with `unappliedAmount = 100.00`  
**And** an eligible invoice with `balanceDue = 100.00`  
**When** I allocate `amountToApply = 100.00` to that invoice and submit  
**Then** the application request is submitted with a generated `applicationRequestId`  
**And** the UI shows a success confirmation including created `PaymentApplication` record(s)  
**And** the invoice is shown as `PaidInFull` with `balanceDue = 0.00` (either from response or after refresh)  
**And** the payment `unappliedAmount` is shown as `0.00`.

### Scenario 3: Apply partial payment to one invoice successfully
**Given** an available payment with `unappliedAmount = 50.00`  
**And** an eligible invoice with `balanceDue = 100.00`  
**When** I allocate `amountToApply = 50.00` and submit  
**Then** the UI shows success and the invoice status becomes `PartiallyPaid`  
**And** the invoice `balanceDue` decreases by `50.00`  
**And** the payment `unappliedAmount` becomes `0.00`.

### Scenario 4: Apply payment across multiple invoices atomically
**Given** an available payment with `unappliedAmount = 120.00`  
**And** two eligible invoices with `balanceDue = 70.00` and `balanceDue = 80.00`  
**When** I allocate `70.00` to the first invoice and `50.00` to the second and submit  
**Then** the UI shows success with two `PaymentApplication` records  
**And** the first invoice becomes `PaidInFull` with `balanceDue=0.00`  
**And** the second invoice becomes `PartiallyPaid` with `balanceDue` reduced by `50.00`  
**And** the payment `unappliedAmount` becomes `0.00`.

### Scenario 5: Overpayment creates customer credit
**Given** an available payment with `unappliedAmount = 200.00`  
**And** an eligible invoice with `balanceDue = 150.00`  
**When** I allocate `150.00` and submit  
**Then** the UI shows success  
**And** a `CustomerCredit` is displayed with `amount = 50.00` and `currency` matching the payment  
**And** the payment is shown with no remaining unapplied amount (per backend policy).

### Scenario 6: Client-side validation prevents submitting invalid amounts
**Given** I am allocating amounts to invoices  
**When** my applied total exceeds the payment’s `unappliedAmount`  
**Then** the Submit action is disabled  
**And** the UI displays an error indicating the total exceeds the remaining payment amount.

### Scenario 7: Backend rejects ineligible invoice
**Given** I selected an invoice that becomes `Voided` before submission  
**When** I submit the application  
**Then** the UI shows an error mapped from `VALIDATION_ERROR:INVOICE_NOT_APPLICABLE`  
**And** no success confirmation is shown  
**And** the UI offers to reload invoices.

### Scenario 8: Concurrency conflict requires reload
**Given** the payment’s unapplied amount changes due to another application  
**When** I submit allocations based on stale data  
**Then** the backend returns a conflict (HTTP 409 or equivalent)  
**And** the UI prompts me to reload payment and invoice data before retrying.

---

## 13. Audit & Observability

### User-visible audit data
- After a successful submit, show:
  - `applicationRequestId`
  - `applicationTimestamp`
  - list of created `PaymentApplication` IDs (or references)
  - (if returned) backend `traceId` or correlation ID **(open question if exposed)**

### Status history
- Display existing payment applications for this payment (if load endpoint provides it) or provide a link to a Payment Applications view.

### Traceability expectations
- Frontend must include `applicationRequestId` on submit.
- Frontend should propagate correlation headers if used by the project (e.g., `X-Trace-Id`) **(open question: standard header in this repo).**

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load (payment + invoice list) should render usable state within 2 seconds on typical LAN; invoice list should support pagination if large.
- **Accessibility:** keyboard navigable allocation table; errors announced via aria-live; sufficient contrast for validation states.
- **Responsiveness:** usable on tablet width; allocation inputs remain accessible.
- **i18n/timezone/currency:** display amounts using `currencyUomId` formatting; timestamps in user timezone (unless project standard differs—open question).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit empty-state messaging when no eligible invoices are returned; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Paginate invoice results when list exceeds page size; safe for ergonomics and does not change business logic; impacts UX Summary, Data Requirements.
- SD-UX-PREVENT-DOUBLE-SUBMIT: Disable submit while request is in flight to avoid duplicate submissions; safe because backend is idempotent but UX should prevent accidental repeats; impacts Functional Behavior, Error Flows.
- SD-ERR-STANDARD-MAPPING: Map HTTP 400/403/404/409/503 to banner + actionable guidance; safe because it does not assume domain policy beyond status codes; impacts Service Contracts, Error Flows.

---

## 16. Open Questions

1. **Permissions / roles:** What is the exact permission string(s) to gate “Apply Payment” submit in the frontend (e.g., `accounting.payment.apply` vs `invoice.adjust`-style)?  
2. **Service/API naming & shapes:** What are the actual Moqui service names or REST endpoints for:
   - loading `ReceivablePayment` by `paymentId`
   - listing eligible invoices for customer/currency
   - submitting payment applications  
   Include expected request/response JSON (especially error response structure for per-invoice errors).
3. **Invoice status enum values:** What are the canonical invoice status values in the frontend contract (exact strings) for eligible vs ineligible states?  
4. **Overpayment UX certainty:** Backend story indicates remainder becomes `CustomerCredit` and recommends setting `unappliedAmount` to `0` after credit creation. Should the UI always expect `unappliedAmount=0` on success when credit is created, and should it hide “remaining payment” in that case?  
5. **Correlation/trace header standard:** Does this frontend project set/provide a standard correlation ID header (name and behavior) that should be included on submit and logged client-side?  
6. **Reversal workflow:** Business rules mention reversibility with audit. Is a separate frontend story planned for reversing applications, or should this story include a “Reverse” action from the success/results view?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Accounting: Apply Payment to Invoice — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/178

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Accounting: Apply Payment to Invoice  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/178  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Apply Payment to Invoice

**Domain**: payment

### Story Description

/kiro  
Apply payments to invoices with clear AR reconciliation.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
User action or automated rule to apply a recorded payment to one or more invoices

## Main Flow
1. Select payment and target open invoice(s)
2. Validate invoice status and remaining balance
3. Apply payment amount to invoice(s)
4. Reduce Accounts Receivable balance accordingly
5. Update invoice payment status (partial/paid)
6. Persist application records

## Alternate / Error Flows
- Overpayment → create credit balance
- Invoice closed or voided → block application
- Partial application across multiple invoices

## Business Rules
- AR reduction occurs only when payment is applied
- One payment may apply to multiple invoices
- One invoice may have multiple payments
- Application must be reversible with audit

## Data Requirements
- Entities: PaymentApplication, Invoice, AR
- Fields: appliedAmount, invoiceId, applicationTimestamp

## Acceptance Criteria
- [ ] AR balance reduces correctly
- [ ] Invoice status updates accurately
- [ ] Partial payments are supported
- [ ] Application is auditable and reversible

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)


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

====================================================================================================