STOP: Clarification required before finalization

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

---

## 1. Story Header

### Title
[FRONTEND] [STORY] AR: Apply Payment to Open Invoice(s)

### Primary Persona
Back Office Accountant (AR Clerk)

### Business Value
Enable accurate, auditable cash application by allocating a posted/settled customer payment across one or more open invoices, updating invoice settlement status, and recording unapplied cash when payment exceeds open balances.

---

## 2. Story Intent

### As a / I want / So that
**As a** Back Office Accountant,  
**I want** to apply a posted/settled customer payment to one or more open invoices (full/partial),  
**so that** AR balances and invoice settlement statuses are correct, and the allocation is traceable and idempotent.

### In-scope
- UI to search/select a customer payment that is eligible for application (posted/settled, with unapplied amount).
- UI to list eligible customer invoices (Open / Partially Paid) and allocate payment amounts across invoices.
- Submit payment application to Accounting (SoR for allocation), receive computed results (applied allocations + unapplied amount), and display outcome.
- Enforce UI validations (amounts, currency consistency, invoice eligibility) and show backend errors.
- View allocation history for a payment (read-only) if returned by backend.

### Out-of-scope
- Capturing/settling payments (Payments domain).
- Issuing/voiding invoices (Billing domain).
- Creating credit memos automatically for overpayments.
- Write-offs, reversals, refunds, or dispute workflows.
- Defining GL account mappings or posting rule configuration UI (no CoA/PostingRuleSet management here).

---

## 3. Actors & Stakeholders
- **Back Office Accountant (User):** Performs allocations and reviews results.
- **Accounting Domain (SoR):** Validates and persists `PaymentApplication` records; updates invoice settlement state/open balance; generates accounting postings.
- **Payments Domain (External/Upstream):** Owns payment lifecycle; provides posted/settled payment data and reference identifiers.
- **Billing Domain (Downstream/Adjacent):** Owns invoice lifecycle; consumes settlement state changes as needed (read-only from frontend POV).
- **Auditor/Compliance:** Requires immutable allocation record with actor/time and idempotent behavior.

---

## 4. Preconditions & Dependencies
1. A payment exists with:
   - `paymentId`
   - `customerId`
   - `currencyUomId`
   - `totalAmount`
   - `unappliedAvailableAmount`
   - `status` = posted/settled (not authorized/pending)
   - `effectiveAt` (accounting date basis)
2. One or more invoices exist for the same `customerId` in eligible settlement status:
   - `Open` or `Partially Paid`
   - with `openBalance > 0`
   - same currency as payment
3. User has permission to apply payments (exact permission string TBD; see Open Questions).
4. Backend service endpoints exist (or will be created) for:
   - Loading payment + eligible invoices
   - Submitting an application (idempotent)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main menu: **Accounting → Accounts Receivable → Apply Payment**
- Secondary entry: From **Payment Detail** screen: action **Apply to Invoices** (if the app already has payment detail navigation; otherwise only main menu entry)

### Screens to create/modify
1. **Screen:** `apps/accounting/ar/ApplyPayment.xml` (new)
   - Search/lookup for an eligible payment (by paymentRef/paymentId/customer)
   - Displays payment summary + eligible invoices grid + allocation form
2. **Screen (optional):** `apps/accounting/ar/PaymentDetail.xml` (modify if exists)
   - Add transition link to ApplyPayment with `paymentId`

### Navigation context
- Breadcrumb: Accounting / AR / Apply Payment
- Deep link supports `?paymentId=<id>` to load directly into allocation UI.

### User workflows
#### Happy path (manual allocation)
1. User navigates to Apply Payment.
2. User selects a payment.
3. System loads:
   - payment summary (amount, currency, effective date, unapplied available)
   - eligible invoices list (invoice number/date, open balance, settlement status)
4. User enters applied amounts per invoice (one or more).
5. User submits.
6. System shows confirmation with:
   - allocations actually applied
   - resulting invoice settlement statuses
   - unapplied amount remaining

#### Alternate path (auto-allocation)
- User clicks “Auto-allocate” (if supported by backend policy) to populate suggested amounts, then reviews and submits.

#### Alternate path (no eligible invoices)
- If none eligible, screen shows empty state and indicates payment will remain unapplied unless invoices are opened/created (no mutation).

---

## 6. Functional Behavior

### Triggers
- Screen load with `paymentId` (deep link) triggers fetch of payment and eligible invoices.
- Payment selection triggers same fetch.
- Submit triggers creation of `PaymentApplication`.

### UI actions
- **Select Payment**
  - Provide search inputs: payment reference (external ref), paymentId, customerId/name (if available)
  - Result list shows only payments with unapplied available amount > 0 and status posted/settled.
- **Allocate amounts**
  - Each invoice row has an editable `appliedAmount` input (currency).
  - Provide “Apply max” action per row to fill `min(openBalance, remainingUnapplied)`.
  - Provide “Apply remaining to oldest” (optional) if auto allocation supported; otherwise omit.
- **Submit Application**
  - Disabled until:
    - at least one `appliedAmount > 0`
    - sum(appliedAmount) <= payment.unappliedAvailableAmount
    - all `appliedAmount <= invoice.openBalance`
    - currency consistency (payment vs invoices)

### State changes (frontend visible)
- After successful submit:
  - Show resulting allocations and backend-calculated unapplied amount.
  - Mark invoices in the list with updated settlement status and new open balances (if returned; otherwise refresh from backend).
  - Lock inputs (read-only) if backend indicates payment is fully applied or application is immutable.

### Service interactions
- Load payment & eligible invoices (read-only).
- Submit application (create).
- Optional: load application history for payment (read-only) after submit.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, before submit)
1. **Eligibility**
   - Payment must be posted/settled and have `unappliedAvailableAmount > 0` to enable allocation UI.
2. **Allocation amount rules**
   - `appliedAmount` must be numeric, >= 0.
   - `appliedAmount` cannot exceed invoice `openBalance`.
   - Total applied cannot exceed payment `unappliedAvailableAmount`.
3. **Currency**
   - Payment currency must match invoice currency; otherwise invoice is not selectable/editable and UI shows “Currency mismatch” message.
4. **Overpayment handling**
   - UI must allow submitting less than full unapplied amount (remaining becomes unapplied cash per accounting policy).
   - UI must not auto-create credit memo.

### Enable/disable rules
- Submit button disabled if validations fail or if backend indicates payment not eligible.
- Invoice row input disabled if invoice not eligible (Paid/Closed/Void, openBalance=0, currency mismatch).

### Visibility rules
- Show “Unapplied after apply” computed preview (client-side) = payment.unappliedAvailableAmount - sum(appliedAmount).
- If backend returns an application record id, show it in confirmation panel.

### Error messaging expectations
- Surface backend error codes/messages verbatim in a user-friendly banner and map common cases:
  - `PAYMENT_NOT_SETTLED` → “Payment is not posted/settled and cannot be applied.”
  - `INVOICE_NOT_ELIGIBLE` → “One or more selected invoices can’t receive payments.”
  - `ALLOCATION_EXCEEDS_OPEN_BALANCE` → “Applied amount exceeds invoice balance.”
  - `ALLOCATION_EXCEEDS_UNAPPLIED` → “Total applied exceeds payment’s available amount.”
  - `IDEMPOTENT_REPLAY` → “This payment application was already processed; showing existing result.”
  - (Exact codes TBD; see Open Questions)

---

## 8. Data Requirements

### Entities involved (conceptual; Moqui entity names TBD)
- `Payment` (SoR: Payments domain; Accounting reads)
- `Invoice` (SoR: Billing for lifecycle; Accounting for financial settlement fields per backend story)
- `PaymentApplication` (SoR: Accounting)
- `PaymentApplicationAllocation` (line-level allocations to invoices)

### Fields (type, required, defaults)

#### Payment (read-only in this UI)
- `paymentId` (string/UUID, required)
- `paymentRef` (string, optional)
- `customerId` (string/UUID, required)
- `currencyUomId` (string, required)
- `totalAmount` (decimal, required)
- `unappliedAvailableAmount` (decimal, required)
- `status` (enum, required) — must be posted/settled
- `effectiveAt` (datetime, required)

#### Invoice (read-only except appliedAmount input)
- `invoiceId` (string/UUID, required)
- `invoiceNumber` (string, required for display)
- `invoiceDate` (date/datetime, required for sorting)
- `settlementStatus` (enum: Open/Partially Paid/Paid, required)
- `openBalance` (decimal, required)
- `currencyUomId` (string, required)

#### PaymentApplication (created by submit; read-only afterwards)
- `paymentId` (string/UUID, required)
- `applicationEventId` (UUID, required for idempotency)
- `applicationDate` (datetime/date, required; defaults to payment.effectiveAt unless overridden—TBD)
- `unappliedAmount` (decimal, returned by backend)
- `allocations[]` (required; at least one allocation with appliedAmount > 0)

#### Allocation (input per invoice row)
- `invoiceId` (string/UUID, required)
- `appliedAmount` (decimal, required)

### Read-only vs editable by state/role
- Read-only: Payment summary, invoice openBalance/status fields.
- Editable: allocation amounts only while payment eligible and user authorized.
- After successful application: allocation inputs become read-only for the submitted application result.

### Derived/calculated fields (frontend)
- `totalToApply` = sum(all appliedAmount inputs)
- `previewUnappliedAfter` = payment.unappliedAvailableAmount - totalToApply

---

## 9. Service Contracts (Frontend Perspective)

> Moqui naming/paths are placeholders until confirmed; frontend must integrate via Moqui screen transitions calling services.

### Load/view calls
1. **Get eligible payment by id**
   - Service: `accounting.ar.PaymentApplicationFacade.getPaymentForApplication`
   - Inputs: `paymentId`
   - Outputs: payment summary fields + eligibility flags
2. **List eligible invoices for customer/payment**
   - Service: `accounting.ar.PaymentApplicationFacade.listEligibleInvoices`
   - Inputs: `customerId`, `currencyUomId` (and optionally `asOfDate=effectiveAt`)
   - Outputs: list of invoices with openBalance, settlementStatus
3. **(Optional) Load existing applications for payment**
   - Service: `accounting.ar.PaymentApplicationFacade.listPaymentApplications`
   - Inputs: `paymentId`
   - Outputs: application records + allocations

### Create/update calls
1. **Apply payment**
   - Service: `accounting.ar.PaymentApplicationFacade.applyPaymentToInvoices`
   - Inputs:
     - `paymentId` (required)
     - `applicationEventId` (required; generated client-side UUIDv7 preferred, else server-generated—TBD)
     - `applicationDate` (optional/required—TBD)
     - `allocations[] = { invoiceId, appliedAmount }` (required)
   - Outputs:
     - `paymentApplicationId` (or composite key)
     - `allocationsApplied[]` with appliedAmount per invoice
     - updated invoice balances/statuses (preferred)
     - `unappliedAmount`
     - `idempotentReplay` flag (optional)

### Submit/transition calls (Moqui)
- Screen transition `applyPayment` calls `applyPaymentToInvoices` service.
- On success: transition to same screen with `paymentId` and a `paymentApplicationId` (optional) to show confirmation.
- On error: stay on same screen and show error banner, keeping user inputs.

### Error handling expectations
- Validation errors return structured messages; UI maps them to field errors where possible.
- Concurrency/idempotency:
  - If payment unapplied amount changed since load, backend should return a conflict; UI prompts refresh.
  - If idempotent replay, backend returns prior result; UI displays it without reapplying.

---

## 10. State Model & Transitions

### Allowed states (relevant to UI)

#### Payment eligibility state (derived)
- `EligibleForApplication` (posted/settled AND unappliedAvailableAmount > 0)
- `NotEligible` (not settled OR no unapplied amount)

#### Invoice settlement status (display-only but updated by result)
- `Open`
- `Partially Paid`
- `Paid`

### Role-based transitions
- User with AR apply permission can:
  - create `PaymentApplication` for eligible payment
- User without permission:
  - can view payment/invoice info (if allowed) but cannot submit (exact policy TBD)

### UI behavior per state
- Payment NotEligible: show explanation; disable allocation grid and submit.
- Invoice Paid/NotEligible: row disabled and excluded from totals.
- After application: show immutable result; disable editing for that application.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- User enters appliedAmount > openBalance → row error; submit disabled.
- Total applied > unappliedAvailableAmount → summary error; submit disabled.
- No allocations > 0 → submit disabled.

### Backend validation failures
- Payment not settled/eligible → show banner; refresh payment eligibility.
- Invoice not tied to customer / not eligible → show banner and mark rows if identifiable.
- Currency mismatch → show banner; disable mismatched invoices.

### Concurrency conflicts
- Another user/process applies the same payment, reducing available unapplied amount:
  - Backend returns conflict (409 or service error code)
  - UI shows message: “Payment availability changed. Refresh to continue.”
  - Provide “Refresh” action to reload payment+invoices and keep user inputs where possible.

### Unauthorized access
- Backend denies (403): show “Not authorized to apply payments” and disable submit.

### Empty states
- No eligible payments in search → show “No posted payments with unapplied amount found.”
- No eligible invoices for customer → show “No open invoices to apply this payment to. Unapplied amount will remain as unapplied cash unless applied later.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Apply payment across multiple invoices (full/partial)
Given I am an authorized AR user  
And a customer has a posted/settled payment with unapplied available amount 100.00 in currency USD  
And the customer has two eligible invoices with open balances 60.00 and 80.00 USD  
When I allocate 60.00 to the first invoice and 40.00 to the second invoice and submit  
Then the system creates a payment application record linked to the payment  
And the first invoice open balance becomes 0.00 and settlement status becomes Paid  
And the second invoice open balance becomes 40.00 and settlement status becomes Partially Paid  
And the payment shows unapplied amount 0.00 remaining (or unapplied available amount reduced to 0.00)  
And reloading the screen shows the same application result

### Scenario 2: Overpayment leaves unapplied cash (no auto credit memo)
Given I am an authorized AR user  
And a customer has a posted/settled payment with unapplied available amount 200.00 USD  
And the customer has eligible invoices totaling 150.00 USD open balance  
When I apply 150.00 across the invoices and submit  
Then the system records allocations totaling 150.00  
And the system records the remaining 50.00 as unapplied amount for the payment/customer  
And the UI does not create or prompt an automatic credit memo creation

### Scenario 3: Short-pay leaves invoice partially paid (no write-off)
Given I am an authorized AR user  
And a customer has a posted/settled payment with unapplied available amount 25.00 USD  
And the customer has an invoice with open balance 100.00 USD  
When I allocate 25.00 to the invoice and submit  
Then the invoice remains Partially Paid  
And the invoice open balance becomes 75.00  
And no write-off is created by default

### Scenario 4: Prevent applying more than invoice open balance (client-side)
Given I have loaded an eligible payment and an eligible invoice with open balance 10.00  
When I enter an applied amount of 11.00 for that invoice  
Then the UI shows a field error for that row  
And the Submit action is disabled

### Scenario 5: Backend rejects ineligible invoice
Given I am an authorized AR user  
And I allocate an amount to an invoice that becomes ineligible before I submit  
When I submit the payment application  
Then the backend returns an ineligible-invoice error  
And the UI shows an error banner describing the failure  
And no allocation is persisted (no partial apply)

### Scenario 6: Idempotent replay returns existing result
Given I am an authorized AR user  
And I submit a payment application with applicationEventId X  
When I submit the same application again with the same applicationEventId X  
Then the backend returns success with the original application outcome  
And the UI shows the existing allocations without double-applying amounts

---

## 13. Audit & Observability

### User-visible audit data
- Display (if available from backend):
  - paymentApplicationId / application reference
  - applied by (userId/displayName)
  - applied at (timestamp)
  - applicationEventId
  - allocations list
  - unapplied amount remaining

### Status history
- Provide a read-only “Application History” panel listing prior applications for the payment (if backend supports).

### Traceability expectations
- All service calls log/trace with correlationId (Moqui requestId) and include `paymentId`, `customerId`, and `applicationEventId`.
- UI should propagate correlation headers if frontend gateway supports it (implementation detail; non-blocking).

---

## 14. Non-Functional UI Requirements
- **Performance:** Load eligible invoices within 2 seconds for up to 200 invoices; if more, require pagination/filtering.
- **Accessibility:** Keyboard navigable allocation inputs; proper labels; error messages announced to screen readers.
- **Responsiveness:** Usable on tablet widths; invoice list supports horizontal scroll if needed.
- **i18n/timezone/currency:**
  - Currency formatting based on `currencyUomId`
  - Dates shown in user locale/timezone
  - No multi-currency conversion assumptions

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide explicit empty states for “no eligible invoices” and “no eligible payments” because it’s UI ergonomics and does not change domain policy. (Impacted: UX Summary, Error Flows)
- SD-UX-PAGINATION: Paginate invoice list when >200 rows to preserve performance; does not alter business logic. (Impacted: Non-Functional UI Requirements, UX Summary)
- SD-ERR-STANDARD-MAPPING: Map backend validation/conflict/unauthorized errors to banner + field errors when identifiers provided; standard error-handling pattern only. (Impacted: Business Rules, Error Flows, Service Contracts)

---

## 16. Open Questions
1. **Permissions/roles:** What exact permission(s) gate “apply payment” in this frontend (e.g., `ar.payment.apply`, `invoice.adjust`, etc.) and should read-only access be allowed without it?
2. **Backend contract names:** What are the definitive Moqui service names/paths and request/response payload schemas for:
   - load payment eligibility
   - list eligible invoices
   - apply payment (including returned updated invoice balances/statuses)?
3. **Idempotency identifier:** Should the frontend generate `applicationEventId` (UUIDv7) or must the backend generate it? If frontend-generated, where is it stored for retry UX?
4. **Application date:** Can/should the user edit `applicationDate` (accounting date) or must it always equal `payment.effectiveAt`? If editable, what constraints apply (open accounting period, not future date, etc.)?
5. **Auto-allocation:** Does backend support an “apply by policy” mode when allocations are omitted, or must the user always specify allocations explicitly?
6. **Invoice settlement fields ownership:** Will the backend return updated `openBalance` and `settlementStatus` directly in the apply response, or must the UI refetch invoices after submit?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] AR: Apply Payment to Open Invoice(s)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/196  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AR: Apply Payment to Open Invoice(s)

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Receivable (Invoice → Cash Application)

## Story
AR: Apply Payment to Open Invoice(s)

## Acceptance Criteria
- [ ] Payment can be applied to one or more invoices (full/partial)
- [ ] GL postings: Dr Cash/Bank, Cr AR (per rules)
- [ ] Unapplied and overpayment scenarios are supported per policy
- [ ] Idempotent by paymentRef/eventId


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