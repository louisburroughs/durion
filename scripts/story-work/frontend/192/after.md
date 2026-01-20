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
[FRONTEND] [STORY] AP: Execute Vendor Payment (Allocate Across Bills) and Track GL Posting Status

## Primary Persona
AP Clerk (or AP Manager) using POS Back Office (Billing/AP area)

## Business Value
Enable AP users to execute a vendor payment, allocate it across one or more open bills with idempotency safety, and see the GL posting status/acknowledgement for auditability and reconciliation.

---

# 2. Story Intent

## As a / I want / So that
**As an** AP Clerk,  
**I want** to initiate a vendor payment and allocate the payment across one or more payable bills,  
**So that** vendor bills are settled correctly and the system can track downstream GL posting completion.

## In-scope
- A Moqui screen flow to:
  - Select a vendor and one or more open bills
  - Enter payment details (amount, currency, paymentRef/idempotency key)
  - Optionally specify explicit allocations by bill
  - Submit payment execution
  - View payment result (gateway transaction id, allocations, unapplied remainder)
  - View GL posting status (pending/posted/failed) and acknowledgement reference when available
- UI enforcement of allocation validation rules from backend story (non-negative, sum ≤ payment amount, bills payable/open)
- Idempotent UX behavior when re-submitting with same `paymentRef`
- Read-only detail view of a payment record including allocations and status history

## Out-of-scope
- Defining accounting posting rules / COA mapping UI (owned by Accounting)
- Implementing payment gateway adapter behavior (backend)
- Retry/backoff controls for GL posting (backend)
- Refunds/chargebacks and payment reversal flows (not specified here)
- Vendor/bill creation flows (assumed existing)

---

# 3. Actors & Stakeholders
- **Primary user:** AP Clerk / AP Manager
- **System:** Billing/AP services (Moqui backend integration)
- **Downstream system:** Accounting (asynchronous GL posting, acknowledgement event)
- **Audit/Compliance:** Auditors requiring traceability from payment → allocations → journal entry reference

---

# 4. Preconditions & Dependencies
- User is authenticated and authorized for payment execution actions.
- Vendor exists and has one or more payable/open bills.
- Backend endpoints/services exist (or will exist) consistent with backend story #128:
  - Execute payment (idempotent by `paymentRef`)
  - Retrieve payment by id / paymentRef
  - Retrieve vendor open bills eligible for payment
  - Retrieve bill details needed for allocation (open balance, due date, invoice date)
- Optional: backend exposes GL posting acknowledgement reference and payment workflow status.
- UI must not require knowledge of settlement timing (explicitly prohibited by billing agent contract).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Back Office navigation: **Billing/AP → Vendor Bills → “Make Payment”**
- Alternate entry: **Billing/AP → Payments → New Payment**
- Deep link: `/ap/payments/new` and `/ap/payments/view?billingPaymentId=...`

## Screens to create/modify (Moqui)
1. **`ap/payments/NewPayment.xml`** (new)
   - Vendor selection + eligible bills list
   - Allocation editor (auto or explicit)
   - Submit action: execute payment
2. **`ap/payments/PaymentDetail.xml`** (new)
   - Read-only payment summary, allocations, status timeline, GL posting acknowledgement if present
   - “Re-fetch status” action (reload)
3. **`ap/vendors/VendorBills.xml`** (modify or confirm existing)
   - Add “Make Payment” action pre-filtered to selected vendor and selected bills

## Navigation context
- Breadcrumbs: Billing/AP > Payments > New / Payment Detail
- After successful execution: route to Payment Detail screen with `billingPaymentId`

## User workflows
### Happy path
1. User opens New Payment
2. Select vendor
3. System loads open bills for vendor and displays balances
4. User selects bills and enters payment amount and `paymentRef`
5. User chooses allocation mode:
   - Automatic allocation (deterministic backend policy), or
   - Explicit allocation amounts per selected bill
6. User submits
7. UI shows success result and navigates to Payment Detail; GL status initially shows pending until acknowledgement arrives

### Alternate paths
- Submit with same `paymentRef` again → UI displays existing payment result without duplicating gateway call
- Payment amount exceeds open balances → UI shows unapplied remainder field in result (read-only)
- Gateway fails → UI shows failure state; no allocations applied

---

# 6. Functional Behavior

## Triggers
- User clicks **Execute Payment** on New Payment screen.
- User navigates to Payment Detail for existing payment.

## UI actions
### New Payment
- Vendor lookup/select
- Load eligible bills for vendor
- Select one or more bills
- Enter:
  - `grossAmount`
  - `currency` (if not implied by tenant/location)
  - `paymentRef` (idempotency key; user-entered or generated—see Open Questions)
  - Optional `feeAmount` (only if backend expects it from UI—see Open Questions)
- Allocation mode toggle:
  - **Auto allocate** (no explicit lines sent), or
  - **Explicit allocate** (grid: billId, appliedAmount)

### Payment Detail
- Display:
  - payment identifiers, status, amounts (gross/fee/net if present)
  - allocations list
  - status timeline (state transitions)
  - GL posting acknowledgement reference if available (`journalEntryId` or postingRef)
- Manual refresh: reload payment record

## State changes (frontend-visible)
- On submit: show in-progress state; disable submit to prevent duplicate click
- On response:
  - If success: show `GATEWAY_SUCCEEDED` and likely `GL_POST_PENDING`
  - If failure: show `GATEWAY_FAILED`
- Payment record may later transition to `GL_POSTED` or `GL_POST_FAILED` upon acknowledgement processing (reflected on reload)

## Service interactions (Moqui)
- `transition` from New Payment form calls a Moqui service façade that hits backend payment execution endpoint (or Moqui service if monolithic).
- Payment Detail uses `screen` actions to load payment and allocations.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Vendor must be selected (required)
- At least one bill must be selected (required)
- `grossAmount` must be > 0
- `paymentRef` required and must be unique for idempotency (uniqueness enforced server-side; UI should treat duplicate as “already processed” not error)
- If explicit allocations provided:
  - Each `appliedAmount` must be ≥ 0
  - Sum of `appliedAmount` across lines must be ≤ `grossAmount`
  - Only bills in “payable/open” status may be allocated (validated server-side; UI should prevent selection of non-eligible bills)
- If no explicit allocations provided:
  - UI sends no allocation lines; backend performs deterministic allocation (oldest due date first, nulls last, then invoice date, then bill ID)

## Enable/disable rules
- Disable Execute Payment button until required fields satisfied (vendor, bills, amount, paymentRef)
- When submit is in flight: disable all inputs and show progress indicator
- Allocation grid editable only when allocation mode = explicit

## Visibility rules
- Show “Unapplied remainder” only when backend returns remainder > 0
- Show fee/net fields only if backend returns them (do not assume fee known at capture time)

## Error messaging expectations
- Validation errors (422): map field-level errors to corresponding inputs; show summary at top
- Conflict/idempotency (409 or “already processed” semantic): show message “Payment already processed for this paymentRef” and link to Payment Detail
- Downstream unavailable (503): show retryable error, keep form state intact

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- `BillingPayment` (payment record, workflow status)
- `BillingPaymentAllocation` (payment → bill applied amounts)
- `Bill` (AP bill header with open balance and eligibility)
- Optional: `StatusHistory` / `AuditLog` projection for payment transitions
- Optional: `AccountingAcknowledgement` projection containing `journalEntryId`

## Fields
### New Payment input model
- `vendorId` (UUID, required)
- `currencyUomId` (string, required) **(source unclear; see Open Questions)**
- `grossAmount` (decimal/money, required, > 0)
- `paymentRef` (string, required; idempotency key)
- `allocations[]` (optional)
  - `billId` (UUID, required when allocations present)
  - `appliedAmount` (decimal/money, required when allocations present, ≥ 0)

### Payment detail view model (read-only)
- `billingPaymentId` (UUID)
- `paymentRef`
- `vendorId`
- `gatewayTransactionId` (string, nullable on failure)
- `status` (enum)
- `grossAmount`, `feeAmount?`, `netAmount?`, `currencyUomId`
- `unappliedAmount?` (derived by backend; UI displays if present)
- `allocations[]`: `billId`, `appliedAmount`
- `glPosting`:
  - `journalEntryId?` or `postingRef?`
  - `postedAt?`
  - `glStatus` (if separate from payment status)

## Read-only vs editable by state/role
- New Payment fields editable until submit.
- Payment Detail: all fields read-only.
- Access controlled by permissions (see Open Questions for exact permission IDs).

## Derived/calculated fields
- Allocation sum (UI-calculated for validation/display)
- Remaining-to-allocate = `grossAmount - sum(allocations)`
- Unapplied remainder is backend-authoritative and displayed from response (do not compute as financial truth in UI)

---

# 9. Service Contracts (Frontend Perspective)

> Note: backend API paths are not defined in provided frontend issue; contracts below specify required semantics and payloads. If actual endpoints differ, map them via Moqui service layer.

## Load/view calls
1. **List vendor payable bills**
   - Input: `vendorId`
   - Output: list of bills with `billId`, `billNumber`, `dueDate?`, `invoiceDate?`, `openBalance`, `currency`, `status`
   - Errors: 401/403 unauthorized; 404 vendor not found

2. **Get payment detail**
   - Input: `billingPaymentId` (or `paymentRef`)
   - Output: payment + allocations + status history + gl ack reference (if available)
   - Errors: 404 not found; 403 unauthorized

## Create/submit calls
1. **Execute payment (idempotent)**
   - Input:
     - `paymentRef` (idempotency key)
     - `vendorId`
     - `grossAmount`, `currency`
     - `allocations[]` optional (explicit allocation)
   - Output:
     - `billingPaymentId`
     - `status` (e.g., `GATEWAY_SUCCEEDED` and/or `GL_POST_PENDING`)
     - `gatewayTransactionId` when succeeded
     - `allocationsApplied[]`
     - `unappliedAmount` if any
   - Errors:
     - 422 validation (invalid amounts, invalid bills, missing required fields)
     - 409 conflict/state (e.g., duplicate with different payload, or bills not payable/open)
     - 503 gateway unavailable/downstream error
   - Idempotency behavior:
     - Same `paymentRef` returns same `billingPaymentId` and outcome without re-executing gateway

## Error handling expectations (frontend)
- 422: show inline errors; keep user inputs
- 409 with idempotency: navigate to existing Payment Detail (if `billingPaymentId` provided) or show link to search by `paymentRef`
- 503: show retry; do not clear form

---

# 10. State Model & Transitions

## Allowed states (from backend reference)
- `INITIATED`
- `GATEWAY_PENDING`
- `GATEWAY_FAILED`
- `GATEWAY_SUCCEEDED`
- `GL_POST_PENDING`
- `GL_POSTED`
- `GL_POST_FAILED`

## Role-based transitions
- AP Clerk can initiate payment (INITIATED → GATEWAY_PENDING)
- Only system/back-end processes transition gateway and GL statuses after submission
- UI should not present manual state transition controls (no “mark posted”)

## UI behavior per state
- `GATEWAY_PENDING`: show “Processing payment”; disable edits; allow leaving screen, but show status on detail
- `GATEWAY_FAILED`: show failure reason if provided; allow starting a new payment (new paymentRef)
- `GL_POST_PENDING`: show “Payment succeeded; GL posting pending”
- `GL_POSTED`: show posted reference and timestamp
- `GL_POST_FAILED`: show “GL posting failed; requires back office remediation” (no retry button unless backend provides explicit action—out of scope)

---

# 11. Alternate / Error Flows

## Validation failures
- Missing vendor/bills/amount/paymentRef → block submit with client-side validation
- Allocation sum > grossAmount → block submit and highlight allocation section
- Negative applied amounts → block submit

## Concurrency conflicts
- Bill open balance changed between load and submit → backend returns 409/422; UI shows message “Bill balances changed, reload bills” and provides Reload action

## Unauthorized access
- 401: redirect to login
- 403: show “Not authorized to execute payments” and hide submit actions

## Empty states
- No open bills for vendor: show empty state and disable submit (unless policy allows unapplied/vendor credit payments—see Open Questions)

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Execute payment with explicit allocations (success)
Given I am an authorized AP Clerk  
And vendor "V" has payable bills "B1" and "B2" with open balances  
When I create a new payment for vendor "V" with gross amount 100.00 and paymentRef "PR-001"  
And I enter explicit allocations of 60.00 to "B1" and 40.00 to "B2"  
And I submit Execute Payment  
Then the system creates or returns a BillingPayment with a billingPaymentId  
And the payment status is "GATEWAY_SUCCEEDED" or later  
And the allocations displayed match 60.00 for "B1" and 40.00 for "B2"  
And the UI navigates to the Payment Detail screen for that billingPaymentId

## Scenario 2: Execute payment without allocations (auto allocation)
Given I am an authorized AP Clerk  
And vendor "V" has three payable bills with distinct due dates  
When I create a new payment for vendor "V" with gross amount 200.00 and paymentRef "PR-002"  
And I do not provide explicit allocations  
And I submit Execute Payment  
Then the payment executes successfully  
And the Payment Detail shows allocations created by the system  
And the UI does not require the user to manually allocate before submission

## Scenario 3: Partial payment allocation allowed
Given I am an authorized AP Clerk  
And vendor "V" has a payable bill "B1" with open balance 500.00  
When I submit a payment of 100.00 with an explicit allocation of 100.00 to "B1"  
Then the payment is accepted  
And the allocation of 100.00 is shown on Payment Detail  
And the bill is not treated as fully settled by the UI (no “Paid in full” assertion unless returned by backend)

## Scenario 4: Payment amount exceeds allocatable balance results in unapplied remainder
Given I am an authorized AP Clerk  
And vendor "V" has payable bills totaling an open balance of 50.00  
When I submit a payment of 100.00 with paymentRef "PR-003"  
Then the payment is accepted  
And the Payment Detail shows allocations totaling 50.00  
And the Payment Detail shows an unapplied remainder of 50.00

## Scenario 5: Idempotency prevents duplicate execution
Given I am an authorized AP Clerk  
And I have already submitted a payment with paymentRef "PR-004"  
When I submit another payment request with the same paymentRef "PR-004"  
Then the system does not execute the gateway payment again  
And the UI receives the same billingPaymentId and outcome as the original  
And the UI shows the existing Payment Detail instead of creating a duplicate

## Scenario 6: Allocation validation blocks submit (sum exceeds gross)
Given I am an authorized AP Clerk  
And vendor "V" has payable bills "B1" and "B2"  
When I enter gross amount 100.00  
And I enter allocations 80.00 to "B1" and 30.00 to "B2"  
Then the UI blocks submission  
And I see an error indicating allocations exceed the payment amount

## Scenario 7: Gateway failure shows failure and no allocations assumed
Given I am an authorized AP Clerk  
When I submit Execute Payment and the gateway returns a failure  
Then the UI shows a payment status of "GATEWAY_FAILED" on the Payment Detail  
And the UI does not show allocations as applied unless returned by backend  
And the UI provides a way to start a new payment with a new paymentRef

## Scenario 8: GL posting pending then posted is visible on refresh
Given a payment has status "GL_POST_PENDING"  
When I open Payment Detail and refresh after the accounting acknowledgement is processed  
Then I see the payment status "GL_POSTED"  
And I see a journal entry reference (journalEntryId/postingRef) and posted timestamp if provided

---

# 13. Audit & Observability

## User-visible audit data
- Payment Detail displays:
  - `paymentRef`, `billingPaymentId`, `gatewayTransactionId`
  - Status timeline: initiated, gateway result, GL posting result (with timestamps and actor/system)
- Display “Created by” (user) and created timestamp if available from backend

## Status history
- UI should render status transition list if backend provides it; otherwise show current status only.

## Traceability expectations
- Payment Detail must show linked vendor and linked bills (via allocations) for audit navigation.

---

# 14. Non-Functional UI Requirements
- **Performance:** Vendor bills list loads within 2s for up to 200 bills; show loading state and allow filtering.
- **Accessibility:** All form controls labeled; errors announced; keyboard navigable allocation grid.
- **Responsiveness:** Works on tablet width; allocation grid supports horizontal scroll.
- **i18n/timezone/currency:** Currency formatting uses tenant locale; timestamps shown in user timezone; do not assume single currency if backend returns `currencyUomId`.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit empty state when vendor has no payable bills; qualifies as safe UX ergonomics. Impacted: UX Summary, Error Flows.
- SD-UX-INFLIGHT-SUBMIT-GUARD: Disable submit during in-flight request to prevent double-click duplicates; safe because backend is idempotent but UX reduces accidental retries. Impacted: Functional Behavior, Error Flows.
- SD-ERR-STANDARD-MAPPING: Map 422/409/503 to inline validation vs conflict vs retryable toast; safe because it’s standard HTTP semantics handling without changing domain policy. Impacted: Service Contracts, Error Flows.

---

# 16. Open Questions
1. **Payment method/instrument capture:** What payment instrument fields are required from the UI for AP vendor payments (e.g., bank account, ACH, check, card), and how are they represented? (Blocking: cannot build input form/service payload.)
2. **Currency source-of-truth:** Is currency always implied by tenant/location/vendor, or must user select `currencyUomId`? If selectable, what are allowed currencies per vendor?
3. **paymentRef generation:** Should the UI generate a UUID by default, or must users enter an external reference (e.g., bank transfer reference)? What uniqueness scope applies (tenant-wide vs vendor-wide)?
4. **Permissions:** What exact permission IDs/roles gate: execute payment, view payment detail, view vendor bills, view GL posting references?
5. **API endpoints/contracts:** What are the concrete Moqui service names or REST endpoints for:
   - listing vendor payable bills
   - executing payment (idempotent)
   - fetching payment detail by id/paymentRef
   - fetching status history / GL acknowledgement
6. **Fee capture:** Should `feeAmount`/`netAmount` ever be entered in UI, or are they always returned by gateway/backend? If returned asynchronously, how is it updated on detail screen?
7. **Unapplied remainder handling UX:** When payment exceeds allocatable open balance, should UI allow submitting anyway without any bill selected (creating pure vendor credit/unapplied cash), or must at least one bill be selected?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] AP: Execute Payment and Post to GL — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/192

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] AP: Execute Payment and Post to GL
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/192
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AP: Execute Payment and Post to GL

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Execute Payment and Post to GL

## Acceptance Criteria
- [ ] Payment allocates across one or more bills (full/partial)
- [ ] GL postings: Dr AP, Cr Cash/Bank (per rules)
- [ ] Fees/unallocated amounts handled per policy
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