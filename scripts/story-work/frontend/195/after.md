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
[FRONTEND] [STORY] AR: Create & Post Credit Memo Against Issued Invoice (Traceable, Audited)

### Primary Persona
Accounts Receivable (AR) Clerk

### Business Value
Enable AR to correct billing errors or returns by issuing an auditable credit memo tied to an issued invoice, ensuring accurate AR balance impact and traceability for audit/compliance (with downstream refund handled separately).

---

## 2. Story Intent

### As a / I want / So that
**As an** AR Clerk,  
**I want** to create and post a Credit Memo that references a specific issued invoice with a required reason code and justification,  
**so that** the customer’s AR balance is reduced appropriately and the action is fully traceable for audit review.

### In-scope
- Frontend screens and flows to:
  - Locate an issued invoice and initiate “Issue Credit Memo”.
  - Enter credit memo details (amount, reason code, justification).
  - Submit to backend to create/post the credit memo.
  - View created credit memo details including link back to original invoice and audit metadata.
- UI validation enforcing required fields and basic constraints (non-negative, cannot exceed allowable amount per backend).
- Display backend-calculated results (e.g., totals, status, timestamps, reference IDs).
- Error handling for invalid state, validation failures, concurrency, and unauthorized access.

### Out-of-scope
- Executing a **cash refund** or payment transaction (Payment domain).
- Defining GL account mappings, debit/credit lines, tax jurisdiction rules, or period-close accounting policies (must be backend-defined).
- Approval workflows unless explicitly confirmed.

---

## 3. Actors & Stakeholders
- **AR Clerk (primary user):** creates/posts credit memo.
- **Accounting Manager / Approver (stakeholder):** may require review/approval depending on policy (unknown).
- **Auditor (stakeholder):** needs immutable traceability (who/when/why/what changed).
- **Billing domain / Invoice system (dependency):** provides invoice lifecycle and “issued” status.
- **Payment module (downstream):** may later refund cash using credit balance (explicitly out-of-scope here).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in Moqui and has permission to create credit memos (exact permission string TBD).
- Target invoice exists and is **Issued/Finalized** (exact status values TBD).
- Reason codes list is configured and retrievable.

### Dependencies (frontend contracts required)
- Backend endpoints/services to:
  - Load invoice summary/details including outstanding balance and currency.
  - Load credit memo reason codes.
  - Create/post credit memo against invoice.
  - Load credit memo details after creation.
- Moqui security artifacts for permissions (screen auth + service auth).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Invoice Detail** screen: action button/link “Issue Credit Memo”.
- (Optional) From AR menu: “Credit Memos” → “Create from Invoice” (only if existing nav supports it; otherwise only via invoice detail).

### Screens to create/modify
1. **Modify** existing Invoice Detail screen (path TBD)  
   - Add action: “Issue Credit Memo” (enabled only when invoice is in eligible state).
2. **Create** Credit Memo Create screen  
   - Screen shows immutable invoice context + input form for credit memo.
3. **Create/Modify** Credit Memo Detail screen  
   - View-only credit memo data + traceability (linked invoice, createdBy/postedAt/reason/notes/status).

> Moqui implementation expectation: `screen` with `subscreens`, `forms`, `actions` invoking services, and `transitions` for submit/cancel navigation.

### Navigation context
- Breadcrumb: AR / Invoices / {invoiceId} / Issue Credit Memo
- After successful submit: redirect to Credit Memo Detail screen; include link back to invoice.

### User workflows

#### Happy path
1. AR Clerk opens an issued invoice.
2. Clicks “Issue Credit Memo”.
3. System loads invoice context + reason code list.
4. Clerk enters credit amount (full or partial), selects reason code, optionally enters justification note.
5. Submit.
6. System creates/posts credit memo and shows resulting credit memo detail with reference to invoice.

#### Alternate paths
- Credit amount equals full invoice outstanding balance.
- Partial credit; backend returns computed allocation (e.g., tax reversal) to display.
- Cancel: returns to invoice detail without changes.

---

## 6. Functional Behavior

### Triggers
- User clicks “Issue Credit Memo” on an invoice.
- User submits the Credit Memo form.

### UI actions
- **On entering create screen:**
  - Load invoice details (`invoiceId`).
  - Load reason codes for credit memos.
- **On submit:**
  - Perform client-side validations (required fields, numeric formatting, non-negative).
  - Call create/post service.
  - On success, navigate to credit memo detail.
  - On failure, show field errors or banner error with actionable message.

### State changes (frontend-observable)
- Invoice itself is not edited in UI; it may show updated outstanding balance after memo creation (reload invoice detail or show returned values).
- Credit Memo is created in backend with a status (e.g., `Posted`).

### Service interactions
- `loadInvoice` (read)
- `listCreditMemoReasonCodes` (read)
- `createCreditMemo` (write/post)
- `getCreditMemo` (read)

(Exact names TBD; see Service Contracts section.)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI-enforced; backend remains source of truth)
- **BR-UI-1 Required reason code:** cannot submit without selecting a reason code; show inline error “Reason code is required.”
- **BR-UI-2 Credit amount required:** cannot submit with empty amount; show inline error.
- **BR-UI-3 Non-negative amount:** disallow negative input; show inline error “Amount must be ≥ 0.00”.
- **BR-UI-4 Invoice eligibility:** if invoice is not in eligible issued/final state, hide/disable “Issue Credit Memo” action; if navigated directly, show blocking error state.

### Enable/disable rules
- “Issue Credit Memo” action enabled only when:
  - invoice status is eligible (exact values TBD), AND
  - user has permission.
- Submit button disabled while request in-flight to prevent double-submit.

### Visibility rules
- Invoice context fields always read-only (invoice number/id, customer, totals, outstanding balance, currency).
- Credit memo status/timestamps visible on detail after creation.

### Error messaging expectations
- Map backend error codes to user-friendly messages when provided (see Service Contracts).

---

## 8. Data Requirements

### Entities involved (conceptual; backend SoR)
- `Invoice` (owned by Billing for lifecycle; Accounting for financial totals per domain guide—must be clarified which fields frontend reads here)
- `CreditMemo` (Accounting-owned)
- `ReasonCode` / `CreditMemoReasonCode` (Accounting configuration)
- `AuditEvent` / `InvoiceAuditEvent` (immutable audit trail)

### Fields

#### Inputs (Credit Memo Create Form)
- `invoiceId` (string/UUID; required; from route)
- `creditAmount` (decimal; required; currency-scale; >= 0)
- `reasonCodeId` or `reasonCode` (string; required; from list)
- `justificationNote` (string; optional; max length TBD)

#### Read-only invoice context (display)
- `invoiceNumber` (string)
- `invoiceStatus` (string/enum)
- `customerId` + display name (string)
- `currencyUomId` (string)
- `invoiceTotal` (decimal)
- `outstandingBalance` (decimal) **(required to support “cannot exceed outstanding” UX; backend authoritative)**

#### Credit Memo (display on success/detail)
- `creditMemoId` (string/UUID)
- `creditMemoNumber` (string, if exists)
- `status` (enum; expected includes `Draft`/`Posted` etc. but must be confirmed)
- `totalAmount` (decimal)
- `taxAmountReversed` (decimal) (if backend returns)
- `createdAt`, `createdBy`
- `postedAt`, `postedBy` (if applicable)
- `originalInvoiceId` / `invoiceId`
- `reasonCode`
- `justificationNote`

### Read-only vs editable by state/role
- On Create screen: only the four input fields editable; invoice context read-only.
- On Detail screen:
  - Everything read-only (this story assumes posted credit memo is immutable).
  - If backend supports Draft saving/editing, that is out-of-scope unless confirmed.

### Derived/calculated fields
- UI may compute and display “Remaining balance after credit” as a **preview**:
  - `remaining = outstandingBalance - creditAmount`
  - Must be labeled as “Estimated” until backend confirms.
- Tax reversal allocation is backend-calculated; UI only displays returned values.

---

## 9. Service Contracts (Frontend Perspective)

> Exact endpoints/service names are not provided in inputs; these are required for buildability. Until confirmed, frontend should integrate via Moqui service calls defined by backend module.

### Load/view calls
1. **Get invoice detail**
   - Request: `{ invoiceId }`
   - Response must include: invoice status, currency, total, outstandingBalance, customer display.
   - Errors:
     - `404 INVOICE_NOT_FOUND`
     - `403 FORBIDDEN`

2. **List credit memo reason codes**
   - Request: none or `{ activeOnly: true }`
   - Response: list of `{ reasonCode, description, activeFlag }`
   - Errors: `403 FORBIDDEN`

3. **Get credit memo detail**
   - Request: `{ creditMemoId }`
   - Response: all display fields + linked invoice reference.
   - Errors: `404 CREDIT_MEMO_NOT_FOUND`, `403 FORBIDDEN`

### Create/update calls
4. **Create/Post credit memo**
   - Request:
     - `invoiceId` (required)
     - `creditAmount` (required)
     - `reasonCode` (required)
     - `justificationNote` (optional)
     - (Optional but recommended) `idempotencyKey` (string) to prevent double submit
   - Response:
     - `creditMemoId` (required)
     - updated `outstandingBalance` (optional but helpful)
   - Errors (examples expected from backend):
     - `VALIDATION_FAILED` (field-level)
     - `INVOICE_NOT_ISSUED` / `INVALID_INVOICE_STATE` (409)
     - `CREDIT_EXCEEDS_OUTSTANDING` (422/409)
     - `ACCOUNTING_PERIOD_CLOSED_POLICY_BLOCK` or similar (409) if policy disallows (unclear)
     - `UNAUTHORIZED` / `FORBIDDEN`

### Error handling expectations
- Field-level errors: map to form field messages.
- Conflict/state errors: show banner with next action (e.g., “Invoice is not eligible for credit memo. Refresh invoice.”).
- Idempotency duplicate: treat as success and navigate to returned/existing credit memo if backend supports.

---

## 10. State Model & Transitions

### Invoice (relevant states only; exact enums TBD)
- Eligible: `Issued` / `Finalized` (must confirm)
- Ineligible: `Draft`, `Void`, etc.

UI behavior:
- If eligible: show enabled “Issue Credit Memo”.
- If not: hide/disable and show tooltip/help text (if visible).

### Credit Memo (assumed minimal states; must confirm)
- `Posted` (created and immutable)
- (Optional future) `Draft`, `Voided`, `Applied`

UI behavior per state:
- `Posted`: view-only detail; show posted timestamp/user.
- If backend returns other states, UI must display status badge and remain view-only unless explicitly required.

Role-based transitions
- AR Clerk can create/post (permission TBD).
- No void/apply transitions in this story.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing reason code → inline error, no service call.
- Missing/invalid amount format → inline error.
- Amount > outstanding balance:
  - If UI has outstanding balance, can warn and block submit client-side.
  - Backend remains authoritative; if backend rejects, show returned error message.

### Concurrency conflicts
- If invoice outstanding balance changed since screen load (another credit memo/payment applied):
  - Backend rejects with conflict code; UI shows banner “Invoice balance changed. Reload and try again.”
  - Provide “Reload invoice” action.

### Unauthorized access
- If user lacks permission:
  - Hide action on invoice detail.
  - If direct navigation to create screen: show “Not authorized” page/section and do not load sensitive data beyond minimal.

### Empty states
- Reason code list empty:
  - Block submission; show message “No credit memo reason codes configured. Contact administrator.”
  - Provide link back to invoice.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create full credit memo successfully
**Given** an invoice `INV-123` is in an eligible issued/finalized status with `outstandingBalance = 110.00` in currency `USD`  
**And** the user has permission to issue credit memos  
**And** credit memo reason code `RETURNED_GOODS` is available  
**When** the user initiates “Issue Credit Memo” from `INV-123`  
**And** enters credit amount `110.00`  
**And** selects reason code `RETURNED_GOODS`  
**And** submits the form  
**Then** the system creates a credit memo and returns a `creditMemoId`  
**And** the UI navigates to the Credit Memo Detail screen for that `creditMemoId`  
**And** the Credit Memo Detail displays a reference/link to invoice `INV-123`  
**And** the Credit Memo Detail displays the selected reason code and the submitting user and timestamp.

### Scenario 2: Create partial credit memo successfully
**Given** invoice `INV-123` is eligible with `outstandingBalance = 110.00`  
**And** reason code `PRICING_ERROR` is available  
**When** the user creates a credit memo for amount `55.00` with reason `PRICING_ERROR`  
**Then** the created credit memo total shown on the detail screen is `55.00` (or displays backend-returned total)  
**And** the credit memo remains linked to `INV-123`.

### Scenario 3: Block submit when reason code missing
**Given** the user is on the Issue Credit Memo screen for an eligible invoice  
**When** the user enters a valid credit amount  
**And** does not select a reason code  
**And** clicks Submit  
**Then** the UI does not call the create credit memo service  
**And** the UI shows an inline validation error that a reason code is required.

### Scenario 4: Backend rejects credit exceeding outstanding balance
**Given** invoice `INV-123` is eligible with `outstandingBalance = 50.00`  
**When** the user submits a credit memo for `60.00`  
**Then** the backend responds with an error indicating the credit exceeds the allowable amount  
**And** the UI displays an error banner/message explaining the issue  
**And** no navigation to credit memo detail occurs.

### Scenario 5: Invoice not eligible
**Given** invoice `INV-999` is in `Draft` (or any ineligible status)  
**When** the user views the invoice detail screen  
**Then** the “Issue Credit Memo” action is disabled or hidden  
**And** if the user navigates directly to the Issue Credit Memo URL for `INV-999`  
**Then** the UI displays a blocking error indicating the invoice is not eligible.

---

## 13. Audit & Observability

### User-visible audit data
- Credit Memo Detail must display:
  - created timestamp and user
  - posted timestamp and user (if distinct)
  - reason code
  - justification note
  - linked invoice reference

### Status history
- If backend provides status history/events, show a read-only list; otherwise omit.

### Traceability expectations
- UI must include identifiers in logs/telemetry (where available):
  - `invoiceId`, `invoiceNumber`, `creditMemoId`
- Ensure correlation ID (if Moqui provides) is preserved across requests and surfaced in error details for support (do not show sensitive internals).

---

## 14. Non-Functional UI Requirements
- **Performance:** Invoice + reason codes load within 2s on typical network; show loading state; avoid duplicate loads.
- **Accessibility:** All form controls labeled; validation errors announced; keyboard navigable.
- **Responsiveness:** Works on tablet widths used in POS backoffice.
- **i18n/timezone/currency:** Display money using `currencyUomId` formatting; display timestamps in user timezone (must use Moqui/Quasar standard utilities).

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **Refund scope confirmation (blocking):** Should this frontend story explicitly exclude refund execution and only create/post the credit memo (Accounting), with a separate Payment story for refunds? (The provided inputs mention “refund”.)
2. **Invoice eligibility statuses (blocking):** What are the exact invoice states considered “finalized/issued” in this system (enum values) for enabling the action and for backend validation messaging?
3. **Service/API contract names (blocking):** What are the Moqui service names (or REST endpoints) for: load invoice, list reason codes, create/post credit memo, and fetch credit memo details?
4. **Credit memo state model (blocking):** Does the system support Draft vs Posted credit memos (save draft, edit, then post), or is creation always immediately Posted?
5. **Period-close behavior exposure (blocking):** If the original invoice period is closed, what should the UI show? (e.g., informational banner “Posted as prior period adjustment” and which fields indicate that.)
6. **Permissions (blocking):** What is the exact permission(s) required to see the action and submit the credit memo (`AR:CreateCreditMemo` was referenced in backend draft; needs confirmation in this Moqui app)?
7. **Reason code source (blocking):** What entity/service provides reason codes and what fields are required (code, description, active flag, effective dating)?
8. **Max justification length and requirements (blocking):** Any limits or required formatting for `justificationNote`? Is it required for some reason codes?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] AR: Issue Credit Memo / Refund with Traceability — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/195  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AR: Issue Credit Memo / Refund with Traceability

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
AR: Issue Credit Memo / Refund with Traceability

## Acceptance Criteria
- [ ] Credit memo references original invoice and offsets balances
- [ ] GL postings reverse revenue/tax and reduce AR (or drive refund payment)
- [ ] Reason code required and actions audited
- [ ] Period-close policies handled (adjusting entries if needed)


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