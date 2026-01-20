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
[FRONTEND] [STORY] Invoicing: Support Authorized Invoice Adjustments (Draft invoices)

### Primary Persona
Back Office Manager

### Business Value
Enable authorized corrections and goodwill adjustments on **Draft** invoices while preserving **accounting correctness, auditability, and traceability**, preventing negative invoice totals and ensuring downstream accounting events are produced.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Back Office Manager  
- **I want** to adjust a **Draft** invoice (edit invoice line items and/or apply an invoice-level discount) with required reason capture  
- **So that** invoice totals/taxes are recalculated correctly, the change is fully audited, and the accounting system can emit the appropriate event (`InvoiceAdjusted` or `CreditMemoIssued`).

### In-scope
- Frontend UI to initiate and submit an adjustment for an invoice that is currently `Draft`
- Capturing **reason code** and **justification** when required by configuration
- Displaying before/after totals impact prior to submit (where supported by backend)
- Handling backend rejections: unauthorized, not-draft, negative-total-requires-credit-memo, missing reason code/justification, concurrency conflict
- Displaying adjustment audit history relevant to adjustments (at least the latest adjustment outcome; ideally the list)

### Out-of-scope
- Creating/issuing a **Credit Memo** (separate story unless backend provides a concrete endpoint and UI is requested explicitly)
- Defining tax policy, GL account mappings, revenue/tax/AR posting semantics
- Editing **Issued** invoices
- Admin management of Reason Codes and configuration flags (assumed existing)

---

## 3. Actors & Stakeholders
- **Back Office Manager**: performs authorized adjustments and provides reason/justification
- **Accounting domain services**: system of record for invoice financial state; recalculates totals; writes audit event; emits `InvoiceAdjusted` / `CreditMemoIssued`
- **Auditor/Compliance**: consumes immutable audit trail (who/when/what/why)
- **Support/Operations**: needs actionable error feedback and trace identifiers

---

## 4. Preconditions & Dependencies
- Invoice exists and is retrievable in the frontend by `invoiceId`
- Invoice is in **status = `Draft`** at time of submit (must be revalidated server-side)
- User is authenticated
- User has permission **`invoice.adjust`** (or equivalent permission enforced by backend)
- Reason codes exist for invoice adjustments, and backend can validate “active” status
- Backend endpoints/services exist for:
  - Loading invoice details (including status, line items, totals)
  - Submitting an adjustment command (atomic update + recalculation + audit + event emission)
  - (Optional but preferred) loading audit events/history for the invoice

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Invoice view screen: action “Adjust Invoice” visible only when invoice is `Draft` and user is authorized.

### Screens to create/modify
1. **Modify**: `apps/pos/screen/invoice/InvoiceDetail.xml` (name illustrative)
   - Add action to open adjustment flow
   - Add read-only “Adjusted” indicator (from `isAdjusted`)
   - Add section to view adjustment audit events (read-only list)
2. **Create**: `apps/pos/screen/invoice/InvoiceAdjust.xml`
   - Contains editable invoice content (line items + invoice-level discount)
   - Reason capture form
   - Totals preview section
   - Submit / Cancel transitions

> Note: Exact screen paths must match repo conventions; implementer should align with existing screen hierarchy in `durion-moqui-frontend`.

### Navigation context
- `InvoiceDetail` → `InvoiceAdjust` (pass `invoiceId`)
- On success: return to `InvoiceDetail` (same `invoiceId`) and show confirmation plus updated totals/audit summary.

### User workflows
**Happy path**
1. Manager opens Draft invoice
2. Clicks “Adjust Invoice”
3. Edits line item(s) and/or invoice-level discount
4. Selects reason code; enters justification if required
5. Submits
6. Backend recalculates totals, persists changes, writes audit event, emits `InvoiceAdjusted`
7. UI returns to invoice detail with updated totals and audit entry visible

**Alternate paths**
- User not authorized: “Adjust Invoice” not shown; direct navigation shows “Not Authorized”
- Invoice becomes non-draft before submit: show conflict and prompt reload; disable submit until refreshed
- Backend rejects due to negative total: show explicit error “Requires Credit Memo” and keep user edits intact (no local commit)
- Missing reason/justification when required: inline validation + backend error mapping

---

## 6. Functional Behavior

### Triggers
- User clicks “Adjust Invoice” from invoice detail
- User submits adjustment form

### UI actions
- Edit invoice line items (supported fields are limited to what backend allows; see Open Questions)
- Edit invoice-level discount (if supported)
- Choose `reasonCode`
- Enter `justification` (conditionally required)
- Submit adjustment

### State changes (frontend-observable)
- Invoice `isAdjusted` becomes `true` after first successful adjustment
- Invoice totals update after recalculation
- An audit event entry appears in adjustment history

### Service interactions
- Load invoice detail on entry to both screens
- (Optional) request “recalculate preview” after edits (if backend provides a preview service)
- Submit adjustment command with an idempotency anchor (if required by backend contract)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Draft-only**: If invoice status != `Draft`, the adjustment UI must be read-only and show message: “Invoice is not Draft and cannot be directly adjusted.”
- **Authorization**: If user lacks permission, block access and show unauthorized.
- **Reason required when configured**:
  - If backend indicates reason required, enforce selection of a valid reason code before enabling submit
  - If justification required, enforce non-empty free text before enabling submit
- **Negative totals are not allowed**:
  - On backend error `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO`, show a blocking banner explaining a credit memo is required; do not apply changes locally

### Enable/disable rules
- Disable “Submit Adjustment” while:
  - invoice not loaded
  - invoice status not `Draft`
  - required reason/justification missing
  - request in-flight
- Disable editing while submit is in-flight

### Visibility rules
- “Adjust Invoice” action visible only when:
  - invoice status == `Draft`
  - user authorized (`invoice.adjust`)
- Show “Adjusted” badge/flag if `isAdjusted == true`
- Show audit history section when available

### Error messaging expectations
- Unauthorized → “You do not have permission to adjust invoices.”
- Not Draft → “Invoice is no longer Draft. Reload to see latest status.”
- Concurrency/optimistic lock conflict → “Invoice changed since you opened it. Reload required.”
- Negative total requires credit memo → explicit + actionable (no silent failure)
- Invalid/missing reason code → inline + show backend-provided error code/message

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Invoice`
- `InvoiceItem`
- `InvoiceAuditEvent` (or equivalent audit entity/view)
- `ReasonCode`

### Fields
**Invoice (read)**
- `invoiceId` (string/UUID) required
- `status` (enum) required; includes `Draft` at minimum
- `isAdjusted` (boolean) default false
- Totals (all currency decimals; scale per currency):
  - `subtotal`
  - `taxTotal`
  - `feeTotal` (if applicable)
  - `grandTotal`
- Currency:
  - `currencyUomId` (string) required
- (Optional for concurrency)
  - `version` / `lastUpdatedStamp` required if backend uses optimistic locking

**InvoiceItem (edit where allowed)**
- `invoiceItemId` (string/UUID) required
- `description` (string) read-only unless backend allows edits (Open Question)
- `quantity` (decimal) editable? (Open Question)
- `unitPrice` (decimal) editable? (Open Question)
- `lineDiscount` (decimal or percent) editable? (Open Question)
- `taxCode`/taxability fields are **not** assumed editable (denylist: tax policy)

**Adjustment command fields (write)**
- `invoiceId` required
- `adjustmentType` (string/enum) optional (Open Question: required?)
- `reasonCode` (string) conditionally required
- `justification` (string/text) conditionally required
- Edited line items and/or invoice-level discount payload (exact shape Open Question)
- `idempotencyKey` / `adjustmentId` (string/UUID) (Open Question: frontend responsibility?)

**AuditEvent (read)**
- `auditEventId`
- `invoiceId`
- `adjustedBy` (userId)
- `adjustedAt` (timestamp)
- `reasonCode`
- `justification` (may be sensitive; show per permissions? Open Question)
- `beforeTotals` / `afterTotals` (structured)
- `changeDetails` (summary)

### Read-only vs editable by state/role
- If invoice status != `Draft`: everything read-only; adjustment screen should not allow submit.
- Only authorized users can edit; unauthorized can view only.

### Derived/calculated fields
- Totals are backend-calculated and must be displayed as authoritative.
- UI may compute a *client-side estimate* for immediate feedback only if explicitly allowed; otherwise rely on backend preview/service response.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may use service calls via screen actions (`<service-call>`) or REST calls if the frontend is SPA; align with existing repo integration patterns.

### Load/view calls
1. **Get Invoice**
   - Input: `invoiceId`
   - Output: invoice header, items, status, totals, currency, `isAdjusted`, concurrency token
2. **Get Reason Codes (Invoice Adjustment)**
   - Input: `reasonCodeType = INVOICE_ADJUSTMENT` (or similar)
   - Output: list of active reason codes (code, label, active flag)
3. **Get Invoice Audit Events (optional but recommended)**
   - Input: `invoiceId`, filter `eventType=ADJUSTMENT` (if available)
   - Output: list ordered by `adjustedAt desc`

### Create/update calls
1. **Submit Invoice Adjustment**
   - Input (minimum):
     - `invoiceId`
     - modified invoice content (items/discount)
     - `reasonCode` / `justification` if required
     - concurrency token (`version`/`lastUpdatedStamp`) if required
     - idempotency anchor if required by backend
   - Output:
     - updated invoice (status, totals, `isAdjusted`)
     - created audit event summary
     - emitted event reference (optional)

### Submit/transition calls
- None beyond “Submit Adjustment”; invoice lifecycle transitions (e.g., issue) are out of scope.

### Error handling expectations (map to UI)
- `403` → unauthorized screen/banner
- `409` conflict:
  - not-draft
  - optimistic lock conflict
- `422`/`400` validation:
  - missing reason/justification
  - `INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO`
- Error payload should include `errorCode` and `message`; if missing, show generic error and log correlationId

---

## 10. State Model & Transitions

### Allowed states (invoice)
- `Draft`
- Non-Draft states exist (e.g., `Issued`) but are not enumerated here; behavior is conditional: “not Draft” is blocked.

### Role-based transitions
- No invoice state transition is performed in this story.
- Role gating:
  - `invoice.adjust` required to submit adjustments

### UI behavior per state
- **Draft**: adjustment action available (if authorized)
- **Not Draft**: adjustment action hidden/disabled; direct nav to adjust shows read-only + guidance to use credit memo/reversal process

---

## 11. Alternate / Error Flows

### Validation failures
- Missing reason code when required:
  - show inline error on reason selector
  - keep user edits intact
- Missing justification when required:
  - show inline error on justification field

### Concurrency conflicts
- If backend indicates optimistic lock / stale version:
  - show banner “Invoice updated by another user; reload required”
  - provide “Reload Invoice” action
  - do not auto-merge changes

### Unauthorized access
- If user hits adjust URL without permission:
  - show 403 page or in-screen banner
  - do not load editable controls

### Empty states
- No reason codes returned:
  - block submission
  - show “No active adjustment reasons configured. Contact administrator.”
- Invoice has no line items:
  - still allow invoice-level discount adjustment only if backend supports; otherwise block submit with explanation (Open Question)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Authorized user adjusts a Draft invoice successfully
Given an invoice exists with status "Draft"  
And the current user has permission "invoice.adjust"  
And at least one valid active adjustment reason code exists  
When the user opens "Adjust Invoice" for that invoice  
And edits allowed invoice fields (line items and/or invoice-level discount)  
And selects a reason code  
And provides justification when required by configuration  
And submits the adjustment  
Then the system saves the adjustment and returns the updated invoice  
And the displayed totals reflect the backend recalculation (subtotal/tax/fees/grand total)  
And the invoice indicates it has been adjusted (`isAdjusted = true`)  
And an adjustment audit entry is visible with actor, timestamp, reason, and before/after totals.

### Scenario 2: Unauthorized user cannot adjust invoices
Given an invoice exists with status "Draft"  
And the current user does not have permission "invoice.adjust"  
When the user attempts to access the invoice adjustment screen or submit an adjustment  
Then the UI blocks the action  
And the backend response (if called) is handled as 403 Forbidden  
And the UI displays an authorization error message.

### Scenario 3: Invoice is no longer Draft at submit time
Given an invoice exists  
And the user opens the adjustment screen while it is "Draft"  
When the invoice status changes to a non-Draft state before submission  
And the user submits the adjustment  
Then the backend response is handled as a conflict (409 or equivalent)  
And the UI shows "Invoice is no longer Draft and cannot be directly adjusted"  
And the UI provides a reload action  
And no local success confirmation is shown.

### Scenario 4: Adjustment rejected because it would make total negative
Given an invoice exists with status "Draft"  
And the current user has permission "invoice.adjust"  
When the user submits an adjustment that would result in a grand total less than 0.00  
Then the backend response is handled as a validation/conflict error  
And the error code "INVOICE_TOTAL_NEGATIVE_REQUIRES_CREDIT_MEMO" is displayed (or mapped to a user-friendly message)  
And the invoice is not updated in the UI  
And the user’s entered changes remain visible for revision.

### Scenario 5: Reason code and justification enforcement
Given an invoice exists with status "Draft"  
And the system configuration requires a reason code  
And the system configuration requires justification text  
When the user attempts to submit without selecting a reason code or without justification  
Then the UI prevents submission and highlights the missing fields  
And if submitted anyway, backend validation errors are surfaced inline without losing edits.

### Scenario 6: Multiple adjustments do not corrupt totals (frontend verification)
Given an invoice exists with status "Draft"  
And the current user has permission "invoice.adjust"  
When the user performs an adjustment successfully  
And then performs a second adjustment successfully  
Then the invoice totals displayed match the backend totals after each adjustment  
And the audit history shows both adjustments in chronological order  
And the UI does not display duplicated or stale totals.

---

## 13. Audit & Observability

### User-visible audit data
- Display at minimum:
  - adjustedAt
  - adjustedBy
  - reasonCode
  - beforeTotals and afterTotals (subtotal/tax/fees/grand total)
  - justification (visibility may require permission clarification)

### Status history
- Not a state transition story, but must show:
  - invoice `isAdjusted` indicator
  - adjustment audit events list (immutable)

### Traceability expectations
- UI logs (frontend console/logger) should include:
  - `invoiceId`
  - `adjustmentId`/idempotency key if present
  - backend `correlationId` / `traceId` if returned
- No PII or sensitive justification text in logs unless workspace convention explicitly allows (assume “no”)

---

## 14. Non-Functional UI Requirements

- **Performance**: invoice detail and adjustment screen load should render primary data within 2s on typical network; reason codes loaded asynchronously if needed.
- **Accessibility**: all form controls labeled; validation errors announced; keyboard navigable.
- **Responsiveness**: usable on tablet/desktop; line-item editing supports horizontal constraints.
- **i18n/timezone/currency**:
  - Display money with `currencyUomId` formatting
  - Display timestamps in user timezone
  - Do not assume currency = USD (backend story mentions $0.00 but UI must format per currency)

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Show explicit empty-state messaging for missing reason codes and missing audit events; qualifies as safe because it does not change domain policy, only improves clarity. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-ERR-MAP-HTTP: Standard mapping of HTTP 403/409/422 to UI banners/inline errors; qualifies as safe because it follows backend-implied semantics without inventing business rules. Impacted sections: Service Contracts, Alternate / Error Flows, Acceptance Criteria.
- SD-OBS-CORRELATION: Display/store backend correlationId/traceId in error details panel (non-PII) for support; qualifies as safe because it’s observability boilerplate and does not alter domain behavior. Impacted sections: Audit & Observability, Error Flows.

---

## 16. Open Questions

1. **Backend contract details (blocking):** What are the exact Moqui service names / REST endpoints and request/response schemas for:
   - load invoice (including concurrency token),
   - submit adjustment,
   - load reason codes,
   - load audit events?
2. **Editable fields (blocking):** Which invoice and invoice item fields are permitted to be changed in an “adjustment”?
   - quantity, unitPrice, lineDiscount, description, taxCode? (tax fields likely not editable)
3. **Reason/justification configuration (blocking):** How does the frontend determine when `reasonCode` and/or `justification` is required?
   - returned flags on invoice? system parameter? reason code metadata?
4. **Idempotency anchor (blocking):** Must the frontend generate/provide an `adjustmentId`/idempotency key (`invoiceId + adjustmentVersion` mentioned), or is it entirely server-generated?
5. **Credit memo path UX (blocking):** When negative total is requested, should the UI:
   - only block with message “requires credit memo” (current story), or
   - offer a guided “create credit memo” action/link (requires endpoint + separate permission)?
6. **Justification visibility (risk/security):** Is justification considered sensitive, and should it be visible to all who can view the invoice, or only to certain roles?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Invoicing: Support Authorized Invoice Adjustments  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/210  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Support Authorized Invoice Adjustments

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300028/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A Draft invoice requires an adjustment (goodwill discount, correction).

## Main Flow
1. Authorized user edits invoice line items or applies a discount.
2. System requires a reason code and free-text justification if configured.
3. System recalculates taxes and totals.
4. System records adjustment audit event including before/after values.
5. System flags invoice as adjusted for reporting.

## Alternate / Error Flows
- Unauthorized user attempts adjustment → block.
- Adjustment would cause negative totals → block or require special permission.

## Business Rules
- Adjustments require permissions and audit trail.
- Adjustments must not break traceability; they must be explainable.

## Data Requirements
- Entities: Invoice, InvoiceItem, AuditEvent, ReasonCode
- Fields: adjustmentType, reasonCode, justification, beforeTotal, afterTotal, adjustedBy, adjustedAt

## Acceptance Criteria
- [ ] Only authorized roles can adjust invoices.
- [ ] Adjustments require reason codes and are auditable.
- [ ] Totals are recalculated correctly after adjustments.
- [ ] Invoice adjustments emit a corresponding accounting event
- [ ] Revenue, tax, and AR are adjusted correctly
- [ ] Adjustments reference the original invoice
- [ ] Authorization and reason code are required
- [ ] Multiple adjustments do not corrupt invoice totals

## Integrations

### Accounting
- Emits Event: InvoiceAdjusted or CreditMemoIssued
- Event Type: Posting (reversal / amendment)
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Authorized adjustment or credit issuance
- Idempotency Key: invoiceId + adjustmentVersion


## Notes for Agents
Keep adjustments rare and transparent; otherwise you erode trust in the system.


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