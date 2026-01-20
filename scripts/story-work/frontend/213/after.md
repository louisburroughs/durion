## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

# 1. Story Header

## Title
[FRONTEND] Invoicing: Generate Draft Invoice from Invoice-Ready Completed Work Order

## Primary Persona
Accounts Receivable (AR) Clerk (Back Office)

## Business Value
Create an auditable, deterministic Draft Invoice from an invoice-ready Work Order without manual re-entry, ensuring all billable items and traceability links are captured consistently for downstream issuance and payment.

---

# 2. Story Intent

## As a / I want / So that
- **As an** AR Clerk  
- **I want** to generate a **Draft** invoice from a single completed, invoice-ready work order  
- **So that** I can review and proceed with billing using system-calculated totals and required traceability links.

## In-scope
- UI action “Create Invoice” from a Work Order context (completed + invoiceReady)
- Frontend calls to create an invoice draft (idempotent behavior)
- Display of resulting Draft invoice details (header, line items, totals, traceability)
- UI handling of validation and error responses (409/422/503) per backend contract reference
- Audit/observability surfacing (where visible) such as created-by/created-at and correlation id handling

## Out-of-scope
- Issuing/finalizing invoices (Draft → Issued/Posted)
- Taking payments, refunds, chargebacks
- Editing invoice line items (snapshot is immutable)
- Regenerating invoices from Voided invoices (privileged endpoint; not part of this story)
- Configuration of billing rules, tax rules, payment terms

---

# 3. Actors & Stakeholders
- **Primary Actor:** AR Clerk
- **Secondary Actors:** Service Advisor (indirect stakeholder), Accounting (downstream consumer), Work Execution system (source of Work Order readiness & snapshot)
- **System Actors:** Billing service (invoice creation), Work Execution service (invoiceReady & BillableScopeSnapshot provider)

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in the POS/backoffice UI.
- User has permission to create invoices (exact permission string TBD by security model; see Open Questions).
- A Work Order exists and is viewable by the user.
- Work Order is **Completed** and **invoiceReady=true** (authoritative from Work Execution).
- Customer Account linked to the Work Order exists and has required billing details; otherwise backend returns 422.

## Dependencies
- Backend endpoint exists to create invoice draft from a workOrderId and returns invoiceId (idempotent behavior):
  - If Draft exists: return existing invoiceId (success)
  - If Posted/Paid exists: 409
  - If not invoice-ready: 409
  - If missing billing data: 422 with missing fields list
  - If Work Execution fetch fails: 503
- Backend endpoint exists to load invoice details for display once created (contract specifics TBD; see Open Questions).
- Work Order details screen already exists and can host an action button (or must be created/extended).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Work Order detail screen (Back Office) for a **Completed** Work Order.

## Screens to create/modify
1. **Modify:** `apps/pos/screen/workorder/WorkOrderDetail.xml` (name illustrative; align to repo conventions)
   - Add a **Create Invoice** action visible when invoice creation is allowed (see rules below).
2. **Create or Modify:** `apps/pos/screen/billing/InvoiceDetail.xml`
   - Display invoice header, line items, totals, and traceability links for a Draft invoice.
   - If already exists (idempotent), navigation should still land here.

## Navigation context
- From Work Order Detail → Create Invoice → redirect to Invoice Detail for returned `invoiceId`.
- Provide a “Back to Work Order” link using stored `workOrderId` on invoice.

## User workflows

### Happy path
1. AR Clerk opens a Completed Work Order.
2. Clicks **Create Invoice**.
3. UI shows progress state (blocking action, spinner).
4. On success, UI routes to Invoice Detail and displays the created Draft invoice.

### Alternate path: idempotent existing Draft
1. AR Clerk clicks **Create Invoice** again.
2. Backend returns same `invoiceId`.
3. UI routes to Invoice Detail (no duplicate created) and shows non-blocking message “Draft invoice already exists; opened existing draft.”

### Error path: not invoice-ready (409)
- UI displays error banner with backend message and keeps user on Work Order screen.

### Error path: missing customer billing data (422)
- UI displays a “Cannot create invoice” panel listing missing fields, with navigation link to Customer Account (if route exists; otherwise show read-only message and field names).

### Error path: downstream failure (503)
- UI shows retry affordance and does not navigate away.

---

# 6. Functional Behavior

## Triggers
- User clicks “Create Invoice” action/button from Work Order Detail.

## UI actions
- Disable the Create Invoice button while request is in-flight to prevent double submits.
- On success:
  - Route to Invoice Detail with `invoiceId`
- On handled failure:
  - Show inline banner/toast and preserve current screen state.

## State changes (frontend-visible)
- Work Order screen: “Create Invoice” action state changes:
  - enabled → disabled (in-flight)
  - optionally disabled permanently if invoice already Posted/Paid (based on Work Order + invoice summary if available; otherwise rely on backend response)

## Service interactions
- Call Billing service to create invoice draft (see Service Contracts).
- After creation, load invoice details for display (or use create response if it returns full invoice; see Open Questions).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation (frontend)
- UI must require a `workOrderId` in context to initiate creation.
- UI must not attempt to edit line items in Draft created from snapshot (display only).

## Enable/disable rules
- **Create Invoice** action is:
  - **Visible** only when Work Order is in a terminal/completed view AND user has invoice-create permission.
  - **Enabled** only when UI has `invoiceReady=true` for the Work Order.
  - If UI cannot determine invoiceReady (missing field), action remains visible but disabled with tooltip “Invoice readiness unavailable” and relies on backend error mapping when invoked (see Open Questions: should it be hidden vs disabled?).

## Visibility rules
- Invoice Detail must show immutable traceability links:
  - workOrderId, billableScopeSnapshotId, customerAccountId
  - estimateId / approval trail references if provided by backend

## Error messaging expectations
- 409: show message: Work Order not invoice-ready OR invoice already exists and cannot be regenerated.
- 422: show “Missing required customer billing data” plus list of fields.
- 503: show “Service unavailable; try again.”

(Use backend-provided message as primary, with a generic fallback.)

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- **Invoice** (Billing-owned)
- **InvoiceItem** (Billing-owned)
- **WorkOrder** (Work Execution-owned)
- **BillableScopeSnapshot** (Work Execution-owned; referenced, not edited)
- **ApprovalRecord** (upstream traceability; displayed if provided)

## Fields (type, required, defaults)

### Create request (minimum)
- `workOrderId` (UUID, required)

### Invoice (display)
- `invoiceId` (UUID, required, read-only)
- `status` (Enum/String: Draft/Issued(or Posted)/Paid/Void; required, read-only)
- `workOrderId` (UUID, required, read-only)
- `customerAccountId` (UUID, required, read-only)
- `billableScopeSnapshotId` (UUID, required, read-only)
- `snapshotVersion` (String, optional display)
- `estimateId` (UUID/String, optional display)
- `approvalId` or approval references (String/UUID, optional display)
- `poNumber` (String, optional display)
- `paymentTermsId` (UUID/String, optional display)
- `subtotal` (Decimal, required, read-only)
- `taxAmount` (Decimal, required, read-only)
- `totalAmount` (Decimal, required, read-only)
- `issueDate` (Date, optional in Draft)
- `dueDate` (Date, optional in Draft)
- Audit fields for display if available: `createdByUserId`, `createdDate`

### InvoiceItem (display)
- `invoiceItemId` (UUID, required)
- `sourceSnapshotItemId` (UUID/String, required)
- `itemType` (Enum: LABOR/PART/FEE, if provided)
- `description` (String, required)
- `quantity` (Decimal, required)
- `unitPrice` (Decimal, required)
- `lineTotal` (Decimal, required)
- `taxCategoryCode` (String, optional but expected)
- `taxable` (Boolean, required)
- `lineTaxAmount` (Decimal, optional if backend provides)

## Read-only vs editable by state/role
- For this story, all displayed invoice fields are **read-only** in Draft because they derive from immutable snapshot + billing calculations.
- No frontend editing of PO number/terms in this story (even if present); treat as display-only.

## Derived/calculated fields
- Totals (subtotal/tax/total) are computed by backend and displayed by UI; UI must not recompute for authority (can format only).

---

# 9. Service Contracts (Frontend Perspective)

> Note: Endpoint paths are not confirmed in provided frontend inputs; backend reference indicates likely `/billing/v1/invoices` create. Treat paths as Open Questions unless already standardized in the repo.

## Load/view calls
- **Get Work Order detail** (existing): must include `workOrderId`, completion status, and `invoiceReady` boolean.
- **Get Invoice detail**: by `invoiceId` to display header + items + traceability.

## Create/update calls
- **Create Draft Invoice from Work Order**
  - Request: `{ workOrderId }` (and optionally idempotency key header if required by backend; see Open Questions)
  - Response success: `{ invoiceId, status }` at minimum
  - Behavior:
    - 200/201 success for newly created
    - 200 success for idempotent existing Draft return

## Submit/transition calls
- None (no Draft→Issued in this story)

## Error handling expectations
- Map HTTP status to UI:
  - **409 Conflict**: show non-field error banner; do not navigate
  - **422 Unprocessable Entity**: show validation panel; display `missingFields[]` if provided
  - **503 Service Unavailable**: show retry message; keep action available
  - **401/403**: show “Not authorized” and hide/disable action on subsequent renders

---

# 10. State Model & Transitions

## Allowed states (Invoice)
- Draft
- Issued/Posted (backend reference uses Posted; billing guide uses Issued; UI must display whatever backend returns)
- Paid
- Void

## Role-based transitions
- Only “Create Draft” is implemented here:
  - From Work Order invoiceReady=true → Invoice Draft creation action available to authorized AR Clerk.

## UI behavior per state
- If invoice status is:
  - **Draft**: show banner “Draft” and read-only details
  - **Posted/Issued/Paid/Void**: still viewable; creation action should be prevented at source (via backend 409 if attempted)

---

# 11. Alternate / Error Flows

## Validation failures (422)
- Missing customer billing data:
  - UI displays missing fields list exactly as returned (e.g., `billingAddress`, `billingContactMethod`)
  - UI provides guidance text: “Update customer billing profile before creating invoice.”
  - No invoice detail navigation occurs.

## Concurrency conflicts / idempotency
- Double click / two users:
  - If backend returns existing Draft: navigate to that Draft invoice.
  - If backend returns 409 due to already Posted/Paid: show message; do not navigate.

## Unauthorized access (401/403)
- If user lacks permission:
  - UI should not render Create Invoice action (preferred) when permission known.
  - If called anyway and backend returns 403: show “You do not have permission to create invoices.”

## Empty states
- If Invoice detail loads but has zero items (unexpected):
  - Show “No invoice items found” and include invoiceId/workOrderId for support; treat as error state but render page.

---

# 12. Acceptance Criteria

## Scenario 1: Successful Draft Invoice Creation
**Given** I am an authenticated AR Clerk with permission to create invoices  
**And** I am viewing a Work Order in a completed state with `invoiceReady=true`  
**And** the customer account has billing address and billing contact method on file  
**When** I click “Create Invoice”  
**Then** the UI calls the billing create-invoice endpoint with the Work Order ID  
**And** the UI navigates to the Invoice Detail screen for the returned `invoiceId`  
**And** the Invoice Detail shows `status=Draft`, the Work Order reference, and at least one invoice item  
**And** the Invoice Detail shows populated totals including `subtotal`, `taxAmount`, and `totalAmount`.

## Scenario 2: Idempotent Return When Draft Already Exists
**Given** I am viewing a completed Work Order with `invoiceReady=true`  
**And** an invoice already exists for that Work Order in `Draft` status  
**When** I click “Create Invoice”  
**Then** the UI receives success with the existing `invoiceId`  
**And** the UI navigates to Invoice Detail for that `invoiceId`  
**And** the UI indicates non-blocking feedback that an existing draft was opened (message text may vary).

## Scenario 3: Block When Work Order Not Invoice-Ready (409)
**Given** I am viewing a Work Order with `invoiceReady=false`  
**When** I attempt to create an invoice (via button if enabled erroneously or via direct action invocation)  
**Then** the UI receives a `409 Conflict` response  
**And** the UI remains on the Work Order screen  
**And** the UI displays an error stating the Work Order is not in a state that allows invoicing.

## Scenario 4: Block When Posted/Paid Invoice Already Exists (409)
**Given** a Work Order already has a linked invoice in `Posted` or `Paid` status  
**When** I click “Create Invoice”  
**Then** the UI receives a `409 Conflict` response  
**And** the UI displays an error indicating an invoice already exists and regeneration is not allowed (corrections require credit notes)  
**And** no new invoice is created and the UI does not navigate to a new invoice.

## Scenario 5: Missing Customer Billing Data (422)
**Given** I am viewing a completed Work Order with `invoiceReady=true`  
**And** the customer account is missing required billing data (e.g., billing address)  
**When** I click “Create Invoice”  
**Then** the UI receives a `422 Unprocessable Entity` response  
**And** the UI displays a “Cannot create invoice” message  
**And** the UI lists the missing fields returned by the API.

## Scenario 6: Downstream Failure (503)
**Given** I am viewing a completed Work Order with `invoiceReady=true`  
**When** I click “Create Invoice” and the backend cannot fetch required data due to downstream unavailability  
**Then** the UI receives a `503 Service Unavailable` response  
**And** the UI displays an error with a retry option  
**And** the UI does not navigate away from the Work Order screen.

---

# 13. Audit & Observability

## User-visible audit data
- Invoice Detail should display (if provided by backend):
  - created date/time
  - created by user (or userId)
  - correlation/request id (optional, but include in dev console logs)

## Status history
- Not implemented in this story; if Invoice API returns status history, display read-only “History” section; otherwise omit.

## Traceability expectations
- Invoice Detail must show immutable references:
  - workOrderId
  - billableScopeSnapshotId
  - customerAccountId
  - estimate/approval references if present

Frontend must not allow editing of these fields.

---

# 14. Non-Functional UI Requirements

- **Performance:** Create action should provide immediate feedback; Invoice Detail load should render skeleton/loader until data arrives.
- **Accessibility:** Create Invoice control must be keyboard accessible; error banners must be announced (ARIA live region).
- **Responsiveness:** Work Order and Invoice Detail screens must remain usable on tablet widths (Quasar responsive layout).
- **i18n/timezone/currency:** Display money fields using configured currency/locale formatting; dates rendered in user’s timezone (no business-rule assumptions about due dates).

---

# 15. Applied Safe Defaults

- SD-UI-EMPTY-STATE: Show a standard empty-state panel when invoice items array is empty; qualifies as safe because it affects only UI ergonomics and not business policy. Impacted sections: UX Summary, Alternate/Error Flows.
- SD-UI-INFLIGHT-GUARD: Disable primary action button while request is in-flight to prevent accidental duplicate submits; safe because backend remains authoritative and this only reduces user error. Impacted sections: Functional Behavior, Acceptance Criteria.
- SD-ERR-MAP-HTTP: Map 409/422/503/403 to standard UI banners/toasts with backend message fallback; safe because it does not change domain logic, only presents errors consistently. Impacted sections: Service Contracts, Alternate/Error Flows.

---

# 16. Open Questions

1. What are the exact Moqui screen paths/names for the existing **Work Order Detail** screen in this frontend repo (to correctly place the “Create Invoice” action)?
2. What are the exact backend endpoints and payloads for:
   - Create Draft Invoice from Work Order (path, method, request/response schema)
   - Fetch Invoice Detail (path, includes items? includes traceability fields?)
3. What permission identifiers should the frontend check to show/enable “Create Invoice” (e.g., `invoice:create`), and where are permissions exposed in the current Moqui session/user context?
4. Should the UI **hide** the Create Invoice action when `invoiceReady=false`, or show it disabled with an explanation? (Current story proposes disabled when known.)
5. Does the create endpoint require an **Idempotency-Key** header from the client? If yes, what key format/source should frontend use?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Invoicing: Generate Invoice Draft from Completed Workorder — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/213

Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Generate Invoice Draft from Completed Workorder

**Domain**: payment

### Story Description

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
A workorder is Completed and invoice-ready.

## Main Flow
1. User selects 'Create Invoice' on the completed workorder.
2. System creates a Draft invoice using the billable scope snapshot.
3. System carries over customer billing details and references (PO number, terms).
4. System populates invoice line items and initial totals.
5. System links invoice to workorder, estimate version, and approval trail.

## Alternate / Error Flows
- Workorder not invoice-ready → block and show missing prerequisites.

## Business Rules
- Invoices are created from the billable scope snapshot.
- Traceability links are required.

## Data Requirements
- Entities: Invoice, InvoiceItem, BillableScopeSnapshot, Workorder, ApprovalRecord
- Fields: invoiceId, status, snapshotVersion, workorderId, estimateId, approvalId, termsId, poNumber

## Acceptance Criteria
- [ ] System creates a Draft invoice with all billable items present.
- [ ] Invoice references workorder and upstream approval trail.
- [ ] Invoice totals are populated.

## Notes for Agents
Keep invoice generation deterministic; the snapshot is the single source of truth.

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