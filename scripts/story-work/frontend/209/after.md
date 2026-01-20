## 🏷️ Labels (Proposed)

STOP: Clarification required before finalization

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
[FRONTEND] [STORY] Invoicing: Finalize and Issue Invoice

## Primary Persona
Back Office / Accounts Receivable Clerk

## Business Value
Convert a reviewed Draft invoice into an official, immutable Issued invoice with auditability and downstream accounting/event emission readiness, reducing billing errors and preventing duplicate issuance.

---

# 2. Story Intent

## As a / I want / So that
- **As an** Accounts Receivable Clerk  
- **I want to** issue a Draft invoice after review  
- **So that** it becomes an official Issued invoice (locked/immutable), auditable, and ready for delivery and downstream accounting processing.

## In Scope
- Frontend “Issue Invoice” action from an invoice view screen.
- Frontend display of invoice totals and traceability links (read-only review context).
- Frontend orchestration of issuance request to Moqui backend (service + transition).
- Handling validation errors, conflict errors (already issued), and authorization failures.
- Post-issuance UI behavior: lock editing actions, show issued metadata, and show delivery preparation status (as returned by backend).

## Out of Scope
- Credit/rebill, credit memo creation, refunds/chargebacks.
- Editing invoice lines/totals (except as already existing elsewhere).
- Payment capture/collection flows.
- Accounting postings / AR creation UI (Accounting consumes events asynchronously).
- Designing invoice numbering scheme policy.
- Implementing event bus/outbox mechanics (backend responsibility).

---

# 3. Actors & Stakeholders
- **Accounts Receivable Clerk (primary user):** reviews and issues invoices.
- **Customer (indirect):** receives invoice via email/print.
- **Billing domain / service (SoR):** owns invoice lifecycle state and issuance validations.
- **Accounting domain (downstream consumer):** creates AR and posts based on `InvoiceIssued` event; not directly controlled by frontend.
- **Auditor/Compliance:** requires non-repudiation and issuance audit trail.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated.
- User has permission to issue invoices (permission name referenced in backend: `invoice:issue`).
- Invoice exists and is accessible within tenant/businessUnit boundaries.
- Invoice is in **Draft** state at time user initiates issuance.

## Dependencies (must exist or be implemented in Moqui backend)
- A screen endpoint to view invoice details including:
  - header, customer, totals, tax breakdown, traceability links, delivery preference.
- A backend operation to issue the invoice (single atomic transaction) that:
  - validates issuance requirements,
  - transitions state Draft → Issued,
  - sets `invoiceNumber`, `issuedAt`, `issuedBy`,
  - locks invoice from edits,
  - records an audit event,
  - queues delivery preparation (email/print) according to preference,
  - triggers/queues `InvoiceIssued` event emission idempotently.
- Backend must return actionable validation errors (field + message + code), and 409 on invalid state.

---

# 5. UX Summary (Moqui-Oriented)

## Entry Points
- From invoice search/list screen: open invoice detail.
- From work order context (if present): navigate to associated invoice detail.

## Screens to Create/Modify
1. **Modify**: `InvoiceDetail` screen (existing or to be created if missing)
   - Add “Issue Invoice” primary action when invoice is Draft and user is authorized.
   - Add issued-state summary panel (issued metadata + delivery prep status).
   - Add audit/history section (issuance audit entry visible).

2. **(Optional) Create/Modify**: `InvoiceIssueConfirm` modal/dialog screenlet
   - Confirmation step showing immutability warning and delivery method recap.

## Navigation Context
- Path example (Moqui screen path to be aligned to repo conventions):  
  `/apps/pos/invoice/detail?invoiceId=...`
- After issuance, remain on Invoice Detail with refreshed state (Issued).

## User Workflows
### Happy Path
1. User opens Draft invoice detail.
2. Reviews totals and traceability.
3. Clicks **Issue Invoice**.
4. Confirms (if confirmation UI used).
5. UI calls issuance transition/service.
6. UI refreshes invoice; shows state **Issued**, shows `invoiceNumber`, `issuedAt`, `issuedBy`, and delivery prep status.
7. Edit actions are disabled/hidden.

### Alternate Paths
- Validation fails: UI stays on Draft invoice detail; shows a structured list of errors with direct links/anchors to affected sections (customer info, delivery, taxes/totals, traceability).
- Already issued (409): UI refreshes and displays banner “Invoice already issued” and shows current Issued metadata.
- Unauthorized: show “You don’t have permission to issue invoices” and do not attempt transition again.

---

# 6. Functional Behavior

## Triggers
- User clicks “Issue Invoice” on Draft invoice detail screen.

## UI Actions
- Show confirmation (required if any irreversible action; see Open Questions).
- Disable the Issue button while request is in-flight (prevent double submit).
- Submit issuance via Moqui transition calling a backend service.
- On success:
  - refresh invoice detail dataset from backend,
  - show success notification with invoice number,
  - disable/hide edit actions and “Issue Invoice” action.
- On failure:
  - map backend error codes to UI messages,
  - keep user on same screen and preserve scroll position,
  - show inline validation errors and/or banner error.

## State Changes (UI-observed)
- Invoice `status`: Draft → Issued
- Invoice becomes immutable: UI must treat all invoice fields as read-only when status != Draft.

## Service Interactions
- Load invoice view service on screen entry and after issuance.
- Issue invoice service on action.
- Optional: load audit history service after issuance to show the issuance audit entry.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation (frontend responsibilities)
Frontend must **not** implement financial validation logic; it must:
- Present backend validation failures clearly and actionably.
- Perform minimal client-side validation only for UX (e.g., confirm dialog acknowledgement), not for policy.

Backend validation errors that must be supported in UI (examples; exact codes are backend-defined and currently unclear):
- Missing customer billing address.
- Delivery method Email but missing/invalid email address.
- Totals inconsistent or tax calculation not finalized.
- Missing traceability links (workOrderId, billableScopeSnapshotId, customerAccountId).
- Invoice has no line items.
- Line item has invalid quantity or unit price.

## Enable/Disable Rules
- **Issue Invoice** button:
  - Enabled only when:
    - invoice.status == `Draft`
    - user has `invoice:issue`
    - invoice is not currently “issuing” (in-flight)
  - Hidden or disabled (with tooltip) otherwise.
- Edit actions (edit header/lines):
  - Enabled only when invoice.status == `Draft` and user has edit permission (existing behavior).
  - Disabled/hidden for Issued/Paid/Void.

## Visibility Rules
- Show issuance metadata block when status in (`Issued`, `Paid`, `Void`) if fields exist:
  - invoiceNumber, issuedAt, issuedBy.
- Show delivery preference summary always (read-only), but show delivery prep status only after issuance if backend provides it.

## Error Messaging Expectations
- Validation failures: show a summary banner “Cannot issue invoice” plus a list of errors.
- Conflict (already issued): “Invoice is already issued; page refreshed.”
- Unauthorized: “Not authorized to issue invoices.”
- Transient/server: “Issue failed due to server error. Try again.” with correlation/reference id if provided.

---

# 8. Data Requirements

## Entities Involved (frontend-view perspective)
- `Invoice` (SoR: Billing domain)
- `Customer` / `CustomerAccount` (SoR: CRM domain, but consumed here read-only)
- `DeliveryPreference` (likely Billing or CRM; unclear)
- `AuditEvent` / `InvoiceAuditEvent` (domain ownership unclear in frontend repo; must be readable)

## Fields (type, required, defaults)
> Types are UI-level; backend canonical types may differ.

### Invoice (read-only except where existing edit screens apply)
- `invoiceId` (string/UUID) — required
- `status` (enum: Draft, Issued, Paid, Void) — required
- `invoiceNumber` (string) — required after issuance; may be null in Draft
- `invoiceVersion` (number/int) — required for idempotency semantics (needed for display/debug; backend uses for event idempotency)
- `issuedAt` (datetime) — required after issuance
- `issuedBy` (string/UUID) — required after issuance
- `currencyUomId` (string) — required
- Totals (all read-only):
  - `subTotal` (decimal, currency scale)
  - `taxTotal` (decimal)
  - `feeTotal` (decimal) (if applicable)
  - `discountTotal` (decimal) (if applicable)
  - `grandTotal` (decimal) — required
- Tax breakdown:
  - list of tax lines: `taxAuthority/jurisdiction`, `taxRate` (decimal), `taxAmount` (decimal)

### Invoice Line Items (read-only here)
- `invoiceItemId` (string/UUID)
- `description` (string)
- `quantity` (decimal)
- `unitPrice` (decimal)
- `lineSubTotal` (decimal)
- `lineTaxTotal` (decimal)
- `lineTotal` (decimal)

### Customer / Billing Info (read-only display)
- `customerAccountId` (string/UUID) — required
- `billingAddress` (structured) — required to issue
- `emailAddress` (string) — conditionally required if deliveryMethod == Email

### Delivery Preference (read-only)
- `deliveryMethod` (enum: Email, Print, Both? TBD) — required to prepare delivery
- `deliveryStatus` (enum/string; e.g., Queued, Sent, Failed) — optional, likely after issuance

### Traceability Links (read-only; required to issue)
- `workOrderId` (string/UUID)
- `billableScopeSnapshotId` (string/UUID)

## Read-only vs Editable by State/Role
- In **Draft**:
  - Issuance action available with `invoice:issue`.
  - Any existing edit features remain as-is (not defined here).
- In **Issued/Paid/Void**:
  - All invoice header/lines/totals are read-only.
  - Issuance action not available.

## Derived/Calculated Fields
- Display-only:
  - `isIssueAllowed` derived from status + permission + backend hints (if provided).
  - Human-readable formatted totals/dates.

---

# 9. Service Contracts (Frontend Perspective)

> Exact service names/endpoints are not provided; Moqui implementation must map these to actual services/transitions.

## Load/View Calls
1. `Invoice.get#Detail`
   - **Input**: `invoiceId`
   - **Output**: invoice header, lines, totals, customer billing info, delivery preference, traceability, audit summary.

2. `Invoice.get#AuditHistory` (optional if included in detail payload)
   - **Input**: `invoiceId`
   - **Output**: list of audit events including issuance event.

## Create/Update Calls
- None in this story (issuance is a transition/command).

## Submit/Transition Calls
1. `Invoice.issue` (command)
   - **Input**: `invoiceId`
   - **Output (success)**:
     - updated invoice snapshot (preferred) OR minimal fields + require reload
     - `status=Issued`, `invoiceNumber`, `issuedAt`, `issuedBy`
     - delivery preparation info if available (`deliveryMethod`, `deliveryStatus`)
   - **Output (failure)**:
     - validation errors with codes and field references
     - conflict error when invoice not Draft

## Error Handling Expectations
- **422 Unprocessable Entity**: validation errors; show list; do not change local invoice state.
- **409 Conflict**: invalid state (already issued/paid/void); refresh invoice and show banner.
- **403 Forbidden**: user lacks permission; show unauthorized error.
- **404 Not Found**: invoiceId invalid or inaccessible; route to not-found screen.
- **503 Service Unavailable / timeout**: transient; show retry guidance.

---

# 10. State Model & Transitions

## Allowed States (minimum per billing guide)
- `Draft`
- `Issued`
- `Paid`
- `Void`

## Role-Based Transitions
- Accounts Receivable Clerk with `invoice:issue`:
  - `Draft` → `Issued` only.
- No other transitions in scope.

## UI Behavior per State
- **Draft**
  - Show Issue Invoice action (if authorized).
  - Show warning that issuance locks the invoice.
- **Issued**
  - Hide/disable Issue action.
  - Lock all edit actions.
  - Show issued metadata + delivery prep status.
- **Paid/Void**
  - Same as Issued for locking in this UI, plus any existing paid/void banners (not defined here).

---

# 11. Alternate / Error Flows

## Validation Failures
- Backend returns field-level errors (e.g., missing billing address).
- UI shows:
  - Summary banner
  - Error list with “Fix” guidance where possible (link to customer screen if that’s where billing address is edited; see Open Questions).

## Concurrency Conflicts
- Two clerks attempt issuance:
  - First succeeds.
  - Second receives 409.
  - UI refreshes and shows “Already issued” with issued metadata.

## Unauthorized Access
- User without `invoice:issue`:
  - Issue button not shown OR disabled.
  - Direct navigation to issuance action URL/transition returns 403; UI shows not authorized.

## Empty States
- Invoice has no lines:
  - Issuance attempt returns validation error; UI displays it.
- Missing traceability links:
  - validation error; UI displays.

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Issue a valid Draft invoice successfully
**Given** I am an authenticated Accounts Receivable Clerk with permission `invoice:issue`  
**And** an invoice exists with `status = Draft` and all required customer billing info, taxes/totals, and traceability links present  
**When** I click “Issue Invoice” and confirm the action  
**Then** the system issues the invoice successfully  
**And** the invoice detail view refreshes showing `status = Issued`  
**And** `invoiceNumber`, `issuedAt`, and `issuedBy` are displayed  
**And** invoice edit actions are disabled/hidden  
**And** “Issue Invoice” action is no longer available.

## Scenario 2: Missing billing address blocks issuance
**Given** I have permission `invoice:issue`  
**And** an invoice exists with `status = Draft`  
**And** the customer billing address is missing  
**When** I attempt to issue the invoice  
**Then** issuance is blocked  
**And** the invoice remains `status = Draft` in the UI after refresh  
**And** the UI displays an actionable error indicating billing address is required.

## Scenario 3: Email delivery requires email address
**Given** I have permission `invoice:issue`  
**And** an invoice exists with `status = Draft`  
**And** the invoice delivery method is “Email”  
**And** the email address is missing or invalid  
**When** I attempt to issue the invoice  
**Then** issuance is blocked  
**And** the UI displays an actionable error indicating a valid email address is required for email delivery.

## Scenario 4: Prevent duplicate issuance (already issued)
**Given** I have permission `invoice:issue`  
**And** an invoice exists with `status = Issued`  
**When** I attempt to issue the invoice (via UI action or direct URL)  
**Then** the system responds with a conflict error  
**And** the UI refreshes and continues to show the invoice in `status = Issued`  
**And** the UI indicates the invoice was already issued  
**And** no second issuance is performed.

## Scenario 5: Unauthorized user cannot issue invoice
**Given** I am authenticated but do not have permission `invoice:issue`  
**And** an invoice exists with `status = Draft`  
**When** I view the invoice detail  
**Then** I do not see an enabled “Issue Invoice” action  
**When** I attempt to issue the invoice via the issuance endpoint/transition  
**Then** I receive a forbidden/unauthorized error  
**And** the UI shows an authorization error message.

## Scenario 6: Transient backend failure during issuance
**Given** I have permission `invoice:issue`  
**And** an invoice exists with `status = Draft`  
**When** I attempt to issue the invoice and the backend returns a 503 or times out  
**Then** the UI shows a non-destructive error message with retry guidance  
**And** the UI re-enables the “Issue Invoice” action after the failure  
**And** the invoice remains `status = Draft` (no optimistic state change).

---

# 13. Audit & Observability

## User-visible audit data
- Invoice detail screen must display issuance audit metadata at minimum:
  - `issuedAt`, `issuedBy`, and optionally an “Issued” audit event row with timestamp and actor.
- If audit history list exists, include the issuance event entry after successful issuance (refresh).

## Status history
- UI should show current status and (if available) a status history timeline/list including transition Draft → Issued with timestamp and actor.

## Traceability expectations
- Invoice detail must show traceability links used for audit:
  - `workOrderId`, `billableScopeSnapshotId`, `customerAccountId`
- After issuance, these remain visible and uneditable.

---

# 14. Non-Functional UI Requirements

## Performance
- Invoice detail load: target < 2s on typical network for typical invoice size.
- Issuance action: show loading state immediately; if > 5s, show “Still working…” hint without duplicating request.

## Accessibility
- Buttons and error banners must be keyboard accessible.
- Error summary must be announced to screen readers (aria-live) and focus should move to error summary on validation failure.

## Responsiveness
- Invoice detail and issuance action usable on tablet widths; action buttons must remain reachable without horizontal scrolling.

## i18n / timezone / currency
- Display currency using `currencyUomId` and locale formatting.
- Display `issuedAt` in user’s timezone (or configured tenant timezone) consistently.

---

# 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE-01**: Show explicit empty-state messaging for missing audit history / missing delivery status rather than leaving blank UI; qualifies as safe because it does not change domain behavior, only presentation. (Impacted: UX Summary, Alternate / Error Flows)
- **SD-UX-DOUBLE-SUBMIT-02**: Disable primary action while request is in-flight to prevent duplicate submissions; safe because it is purely client-side ergonomics and reduces accidental retries. (Impacted: Functional Behavior, Alternate / Error Flows)
- **SD-ERR-MAP-HTTP-03**: Standard HTTP error mapping (422 validation, 409 conflict, 403 unauthorized, 404 not found, 503 transient) to consistent UI banners; safe because it follows conventional transport semantics without inventing domain policy. (Impacted: Service Contracts, Alternate / Error Flows, Acceptance Criteria)

---

# 16. Open Questions

1. **Rewrite Variant conflict:** Domain is `domain:billing`, but the required mapping table forces `accounting-strict` for billing. Confirm whether billing stories should use `accounting-strict` or whether this table should map `domain:billing` → a billing variant. (Blocks finalization due to process constraint.)
2. **Moqui routes/service names:** What are the canonical Moqui screen paths and service names for invoice detail and issuance in this frontend repo (e.g., `apps/pos/invoice/InvoiceDetail` and `InvoiceServices.issueInvoice`), so the story can specify exact transitions?
3. **Confirmation requirement:** Is an issuance confirmation modal required by product policy (because issuance is irreversible), or should it be a single-click action with server-side confirmation only?
4. **Delivery preference model:** Where is `DeliveryPreference` sourced (Billing vs CRM), and what are the allowed values (Email/Print/Both)? Does backend return a `deliveryStatus` after issuance?
5. **Validation error contract:** What is the backend error response shape (fields: `errorCode`, `message`, `field`, `details[]`, `correlationId`), so the UI can implement deterministic error rendering?
6. **Traceability navigation:** Should `workOrderId` and `customerAccountId` be clickable links to other screens? If yes, what are the target screen routes and required permissions?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Invoicing: Finalize and Issue Invoice — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/209


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Invoicing: Finalize and Issue Invoice
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/209
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Finalize and Issue Invoice

**Domain**: payment

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300023/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office / Accounts Receivable Clerk

## Trigger
Draft invoice is reviewed and ready to be issued.

## Main Flow
1. User reviews invoice totals and traceability links.
2. User selects 'Issue Invoice'.
3. System validates invoice completeness (customer details, taxes, totals, traceability).
4. System transitions invoice to Issued/Posted state per workflow.
5. System locks invoice lines and records issuance audit event; prepares delivery (email/print) per preference.

## Alternate / Error Flows
- Validation fails (missing billing info) → block issuance and show actionable errors.
- Invoice already issued → prevent duplicate issuance.

## Business Rules
- Issuance is a state transition with validations and locking.
- Issued invoice should be immutable except via credit/rebill (out of scope).

 ## Data Requirements
  - Entities: Invoice, Customer, AuditEvent, DeliveryPreference
  - Fields: status, issuedAt, issuedBy, deliveryMethod, emailAddress, billingAddress

## Acceptance Criteria
- [ ] Invoice can be issued only when validations pass.
- [ ] Issued invoice is locked against edits.
- [ ] Issuance is auditable and invoice is prepared for delivery.
- [ ] InvoiceIssued event is emitted exactly once per invoice version
- [ ] Event includes full line-item, tax, and total breakdown
- [ ] Accounts Receivable is created correctly
- [ ] Revenue and tax liabilities post accurately
- [ ] Duplicate or replayed events do not double-post

## Integrations

### Accounting
- Emits Event: InvoiceIssued
- Event Type: Posting
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Invoice finalized and issued
- Idempotency Key: invoiceId + invoiceVersion


## Notes for Agents
Issuance ends quote-to-cash; protect the integrity and lock the record.


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