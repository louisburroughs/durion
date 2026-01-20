STOP: Clarification required before finalization

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

## 1. Story Header

**Title:** [FRONTEND] [STORY] Billing: View Invoice & Request Finalization (Controlled) from Work Order  
**Primary Persona:** Service Advisor  
**Business Value:** Enables Service Advisors to review billing-accurate invoice details and initiate a controlled finalization request so the invoice can be locked and proceed to payment, with permissions and auditability enforced.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to view an invoice (items, taxes/fees, totals, status) for a completed Work Order and request invoice finalization when eligible  
- **So that** we can lock the invoice for payment processing and ensure accounting-posting readiness with proper controls

### In-scope
- Read-only invoice detail view (line items, taxes, fees, totals, traceability, statuses).
- Eligibility messaging for finalization (why allowed/blocked).
- Controlled “Request Finalization” UI flow:
  - Permission gating and amount-limit handling.
  - Manager approval input when required (per backend rules).
  - Submit finalize request and render resulting status.
- Display posting-to-accounting status (e.g., POSTED/ERROR) as returned by Billing.

### Out-of-scope
- Editing invoice line items/prices/taxes (invoice is displayed, not authored here).
- Taking/processing payments (separate story).
- Implementing accounting retry logic, SLA alerts, or backend state machine (backend-owned).
- Revert-to-draft / unfinalize flow (explicitly excluded unless separately specified).

---

## 3. Actors & Stakeholders

- **Service Advisor** (initiates view and finalization request)
- **Shop Manager** (provides approval for override scenarios)
- **Billing system** (SoR for invoice lifecycle, totals, tax calculation)
- **Work Execution (Workexec)** (SoR for Work Order state; provides linkage/context)
- **Accounting system** (receives posting events; status displayed in UI via Billing view)
- **Audit/Compliance stakeholders** (need traceability of who requested/finalized/overrode)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- A Work Order context exists (e.g., user navigated from a Work Order screen) with a `workOrderId`.
- Billing has (or can create idempotently) an invoice associated to the Work Order **OR** an invoice already exists and is retrievable by `workOrderId`/`invoiceId`.

### Dependencies
- Backend Billing API(s) to:
  - Load invoice view by `workOrderId` and/or `invoiceId`.
  - Provide computed totals, tax breakdown, invoiceStatus, and “finalizable reason/eligibility” fields.
  - Submit a finalize request (including manager approval fields when required).
- Authorization/permission service exposure to frontend (either via API-provided flags or a session permission model) for:
  - `FINALIZE_INVOICE` (and any override/manager-approval related permission if separate).
- Workexec UI navigation must provide a stable route into this invoice screen.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order details screen: “View Invoice” action (or a Billing/Invoice tab) that navigates into invoice view for that work order.

### Screens to create/modify (Moqui)
1. **New/Updated Screen:** `WorkOrder/InvoiceView.xml` (name indicative; final path must match repo conventions)
   - Parameters: `workOrderId` (required) and optionally `invoiceId` (if available).
2. **Optional embedded panel:** Invoice summary panel within existing Work Order screen that links to full invoice view.

### Navigation context
- Breadcrumb: Work Orders → Work Order `<workOrderId>` → Invoice
- Back navigation returns to Work Order view.

### User workflows
#### Happy path (eligible finalize)
1. Service Advisor opens invoice view from Work Order.
2. System loads invoice view (line items/taxes/fees/totals/status).
3. If status is DRAFT/EDITABLE and eligible, “Request Finalization” action is enabled.
4. User clicks “Request Finalization”.
5. If manager approval not required: confirm dialog → submit → show updated status FINALIZED (and subsequent POSTED/ERROR changes as they occur).
6. If manager approval required: modal requests manager approval code + override reason → submit → show updated status.

#### Alternate paths
- Invoice not eligible: show clear blocking reasons and keep action disabled.
- Permission denied: show “insufficient permissions” and disable action; no submit.
- Accounting posting pending/error: show posting status (read-only) with timestamp and any non-sensitive error summary returned by backend.

---

## 6. Functional Behavior

### Triggers
- Screen load with `workOrderId` (and/or `invoiceId`).
- User clicks “Request Finalization”.

### UI actions
- **On load:**
  - Call invoice view service.
  - Render sections:
    - Header: invoiceId, status, workOrderId, customer, totals.
    - Line items list.
    - Taxes/fees breakdown.
    - Audit/traceability fields (finalizedBy/finalizedAt, glEntryId if posted).
- **On “Request Finalization”:**
  - Perform client-side pre-checks only for presence of required fields for submission (e.g., manager approval code when required per API response).
  - Submit finalize request.
  - On success: refresh invoice view and display status transition result.
  - On failure: map errors to inline/global messages.

### State changes (frontend)
- Local UI state: loading, loaded, submitting, submitSuccess, submitError.
- No client-side mutation of invoice content; all authoritative state comes from backend response.

### Service interactions
- `loadInvoiceView` (read)
- `requestFinalizeInvoice` (command)
- `reloadInvoiceView` after finalize request result

---

## 7. Business Rules (Translated to UI Behavior)

> Backend rules are authoritative; UI must reflect them without inventing policy.

### Validation
- If backend indicates invoice is not eligible (e.g., work order not completed, data incomplete, already posted), UI must:
  - Disable finalization action.
  - Display backend-provided reason(s) in a human-readable form.
- If backend indicates manager approval required (based on role + amount thresholds):
  - UI must require manager approval code and override reason fields before enabling submit.

### Enable/disable rules
- “Request Finalization” is enabled only when:
  - Backend indicates `invoiceStatus` is in an eligible pre-final state (e.g., DRAFT/EDITABLE), **and**
  - Backend indicates `finalizationAllowed=true` (or equivalent), **and**
  - User has permission (either from session permissions or backend returns `canFinalize=true`).
- If manager approval required, submit enabled only when required approval inputs are present and non-empty.

### Visibility rules
- Show posting status fields (POSTED/ERROR) only when provided.
- Show manager approval inputs only when backend indicates required.

### Error messaging expectations
- Permission denial: “You do not have permission to finalize invoices.”
- Validation/integrity errors: “Invoice cannot be finalized: <reason>” (using backend reason codes/messages).
- Downstream/accounting unavailable: show non-technical message; keep correlation/reference ID if provided.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `InvoiceView` (read model)
- `FinalizeRequest` (command payload)
- `AuditLog` (read-only snippets, if included in InvoiceView)

### Fields (type, required, defaults)

#### InvoiceView (read)
Minimum required to render:
- `invoiceId` (string/UUID, required)
- `workOrderId` (string/UUID, required)
- `customerAccountId` (string/UUID, required)
- `invoiceStatus` (enum/string, required; e.g., DRAFT, FINALIZED, POSTED, ERROR, VOID)
- `lineItems[]` (array, required; may be empty)
  - `description` (string)
  - `quantity` (number)
  - `unitPrice` (decimal/money)
  - `lineTotal` (decimal/money)
- `taxes[]` (array)
  - `taxType` (string)
  - `amount` (decimal/money)
- `fees[]` (array)
  - `feeType` (string)
  - `amount` (decimal/money)
- `subtotal` (decimal/money, required)
- `grandTotal` (decimal/money, required)
- `currencyUomId` (string, required)
Finalization guidance:
- `finalizationAllowed` (boolean, required)
- `finalizationBlockReasons[]` (array of strings/codes, required when not allowed)
- `managerApprovalRequired` (boolean, required)
- `amountLimit` (decimal/money, optional)
Posting fields:
- `glEntryId` (string, optional)
- `postingStatus` (enum/string, optional; e.g., PENDING, POSTED, ERROR)
- `postingErrorSummary` (string, optional; must be non-sensitive)
Audit fields:
- `finalizedByUserId` (string, optional)
- `finalizedAt` (datetime, optional)

> Note: exact field names must match backend; if unknown, treat as contract-open-question (see Open Questions).

#### FinalizeRequest (write)
- `invoiceId` (string/UUID, required)
- `overrideReason` (string, required if managerApprovalRequired=true)
- `managerApprovalCode` (string, required if managerApprovalRequired=true)

### Read-only vs editable by state/role
- Invoice content: always read-only in this screen.
- Finalize request inputs: editable only prior to submission; hidden unless required.

### Derived/calculated fields
- Totals/taxes are derived by Billing and must be displayed as-is (no frontend recalculation beyond formatting).

---

## 9. Service Contracts (Frontend Perspective)

> Moqui screen should call backend services via standard Moqui service-calls; actual service names/paths must align with backend implementation.

### Load/view calls
- **Service:** `billing.InvoiceView.get` (placeholder)
- **Input:** `workOrderId` (or `invoiceId`)
- **Output:** `InvoiceView` DTO

### Create/update calls
- None (no invoice editing in scope).

### Submit/transition calls
- **Service:** `billing.Invoice.finalize.request` (placeholder)
- **Input:** `FinalizeRequest` (invoiceId + approval fields when needed)
- **Output:** Updated status and/or a refreshed `InvoiceView` payload.

### Error handling expectations
- Map HTTP/service errors:
  - `401/403`: show permission message; disable action.
  - `409`: show state conflict (e.g., already finalized/posted); force refresh.
  - `422`: show validation reasons (missing data, integrity issues); keep action disabled until refresh.
  - `503`: show service unavailable; allow retry (manual).

---

## 10. State Model & Transitions

### Allowed states (displayed)
- DRAFT/EDITABLE (eligible for request depending on validations)
- FINALIZED (read-only; indicates finalize succeeded and payment can proceed)
- POSTED (read-only; shows GL linkage)
- ERROR (read-only; posting failed; show error summary if provided)
- VOID (read-only; not finalizable)

### Role-based transitions (frontend initiation)
- Service Advisor:
  - Can request finalization when allowed and under amount limit, otherwise triggers manager approval requirement (backend-driven).
- Shop Manager:
  - Can request finalization without amount limit (backend-driven).

### UI behavior per state
- DRAFT/EDITABLE: show request finalization control (enabled/disabled based on eligibility).
- FINALIZED/POSTED/ERROR/VOID: hide or disable request finalization; display status and audit/posting information.

---

## 11. Alternate / Error Flows

### Validation failures
- Backend returns `422` with reason codes/messages (e.g., missing customer billing address, incomplete line items).
- UI displays reasons and disables finalization; provides “Refresh” action.

### Concurrency conflicts
- Another user finalizes while viewing:
  - Finalize request returns `409`.
  - UI refreshes invoice view and shows “Invoice status changed. Page updated.”

### Unauthorized access
- User lacks permission:
  - On load: view still allowed (if permitted); finalize action hidden/disabled.
  - On submit: `403` → show error and log audit/telemetry event.

### Empty states
- No invoice found for workOrderId:
  - Show “No invoice available for this work order.”
  - Provide guidance: “Ensure work order is completed and invoice-ready.”
  - (Whether frontend should trigger invoice draft creation is **not assumed**; see Open Questions.)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View invoice details for completed work order
**Given** I am an authenticated Service Advisor  
**And** I navigate to the Invoice view for a Work Order with an existing invoice  
**When** the page loads  
**Then** I see invoice line items, taxes/fees, subtotal, and grand total  
**And** I see the invoice status and traceability fields (invoiceId, workOrderId)

### Scenario 2: Finalization action disabled when invoice is not eligible
**Given** I am viewing an invoice with status DRAFT/EDITABLE  
**And** the backend indicates `finalizationAllowed=false` with one or more blocking reasons  
**When** the invoice view renders  
**Then** the “Request Finalization” action is disabled  
**And** the blocking reasons are displayed to the user

### Scenario 3: Permission required to request finalization
**Given** I am viewing an eligible invoice  
**And** I do not have permission to finalize invoices  
**When** the invoice view renders  
**Then** I cannot request finalization (action hidden or disabled)  
**And** I see a message indicating insufficient permission

### Scenario 4: Request finalization without manager approval
**Given** I am viewing an invoice where `finalizationAllowed=true` and `managerApprovalRequired=false`  
**When** I click “Request Finalization” and confirm  
**Then** the system submits a finalize request for that invoice  
**And** the invoice status updates to FINALIZED (after refresh)  
**And** finalized audit fields (finalizedBy/finalizedAt) are displayed when provided

### Scenario 5: Request finalization with manager approval required
**Given** I am viewing an invoice where `finalizationAllowed=true` and `managerApprovalRequired=true`  
**When** I open the finalization modal  
**Then** I must enter manager approval code and override reason before I can submit  
**When** I submit the request with both fields populated  
**Then** the request is sent to the backend  
**And** the resulting invoice status is displayed (FINALIZED or an error state/message)

### Scenario 6: Conflict on finalize due to concurrent update
**Given** I am viewing an invoice that was eligible a moment ago  
**And** another user finalizes the invoice before I submit  
**When** I submit my finalize request  
**Then** I receive a conflict error  
**And** the UI refreshes to show the current invoice status

### Scenario 7: Posting status is displayed when available
**Given** an invoice has been finalized and backend provides posting status  
**When** I view the invoice  
**Then** I see POSTED with a GL Entry ID or ERROR with a non-sensitive error summary (if provided)

---

## 13. Audit & Observability

- Display user-visible audit fields when available: `finalizedBy`, `finalizedAt`, `postingStatus`, `glEntryId`.
- Frontend telemetry/logging (consistent with repo conventions):
  - Log events: `invoice_view_loaded`, `invoice_finalize_clicked`, `invoice_finalize_submitted`, `invoice_finalize_failed`, `invoice_finalize_succeeded`.
  - Include correlation ID from backend responses if provided.
- Do not log sensitive inputs (e.g., manager approval code); only log presence/attempt and masked metadata.

---

## 14. Non-Functional UI Requirements

- **Performance:** Invoice view should render within acceptable UX limits; show loading state while fetching.
- **Accessibility:** Actions and modal inputs must be keyboard-navigable; validation errors announced (ARIA).
- **Responsiveness:** Works on tablet resolutions used in shop floor.
- **i18n/timezone/currency:** Currency formatting must use `currencyUomId`; datetime shown in shop-local timezone (mechanism must follow existing app conventions).

---

## 15. Applied Safe Defaults

- **SD-UI-EMPTY-STATE-01**: Provide standard loading/empty/error states for invoice view; qualifies as UI ergonomics. (Impacted: UX Summary, Alternate/Error Flows)  
- **SD-UI-REFRESH-AFTER-COMMAND-01**: After successful finalize request, re-load InvoiceView from backend instead of locally mutating; safe because backend is SoR. (Impacted: Functional Behavior, Service Contracts)  
- **SD-ERR-MAP-HTTP-01**: Map 401/403/409/422/503 to standard user messages and retry affordances; safe as generic error handling without policy invention. (Impacted: Service Contracts, Alternate/Error Flows, Business Rules)

---

## 16. Open Questions

1. **Invoice retrieval contract:** Should the frontend load invoice by `workOrderId`, by `invoiceId`, or both? What are the exact service names and parameter names exposed in Moqui for Billing invoice view?  
2. **Draft creation behavior:** If no invoice exists for a completed work order, should the frontend:
   - (a) show “no invoice” only, or
   - (b) call an idempotent “create draft from work order” service automatically or via a button?
3. **Manager approval input semantics:** What is the exact “manager approval code” format and validation rules (length, numeric/alphanumeric, expiration)? Is it a code, a credential re-auth, or a separate approval workflow token?  
4. **Permission source:** Are permissions exposed to frontend via session/identity claims, or should the invoice view response include `canFinalize` / `managerApprovalRequired` fully computed?  
5. **Posting/error display policy:** For `postingErrorSummary`, what content is allowed to be shown to Service Advisors (and what must be hidden)?  
6. **State naming:** Backend references DRAFT/EDITABLE and FINALIZED/POSTED/ERROR; what are the exact enum values returned so UI can map consistently?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Display Invoice and Request Finalization (Controlled) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/77

FRONTEND STORY (FULL CONTEXT)

Title: [FRONTEND] [STORY] Workexec: Display Invoice and Request Finalization (Controlled)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/77  
Labels: frontend, story-implementation, payment

Frontend Implementation for Story

Original Story: [STORY] Workexec: Display Invoice and Request Finalization (Controlled)

Domain: payment

Story Description

/kiro  
# User Story

## Narrative  
As a Service Advisor, I want to display invoice details and request finalization so that we can proceed to payment.

## Details  
- Show invoice items, taxes/fees, totals.  
- If invoice not finalized, request finalization workflow (controlled).

## Acceptance Criteria  
- Invoice view consistent with workexec.  
- Finalize request requires permission.  
- Resulting status shown.

## Integrations  
- Workexec invoice APIs; accounting status follows after posting.

## Data / Entities  
- InvoiceView, FinalizeRequest, AuditLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Experience  
- domain : Point of Sale

Frontend Requirements
- Implement Vue.js 3 components with TypeScript  
- Use Quasar framework for UI components  
- Integrate with Moqui Framework backend  
- Ensure responsive design and accessibility

Technical Stack
- Vue.js 3 with Composition API  
- TypeScript 5.x  
- Quasar v2.x  
- Moqui Framework integration