Title: [BACKEND] [STORY] Invoicing: Generate Invoice Draft from Completed Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/149
Labels: type:story, domain:billing, status:ready-for-dev

## 🏷️ Labels (Current)
### Required
- type:story
- domain:billing
- status:ready-for-dev

### Recommended
- agent:billing
- agent:story-authoring

---
**Rewrite Variant:** integration-conservative
**Conflict Resolution:** Resolved - domain:billing confirmed as primary owner
---

## Story Intent
As an Accounts Receivable Clerk, I want to generate a draft invoice from a single completed work order, so that I can initiate the customer billing process accurately and efficiently, ensuring all approved, billable work is captured without manual data entry.

## Actors & Stakeholders
- **Primary Actor:** Accounts Receivable Clerk (a user with permissions to manage invoices).
- **System Actor:** Billing Service (the service responsible for creating and managing the invoice lifecycle).
- **Stakeholders:**
    - **Work Execution Domain:** The authoritative source of the `Completed` work order and the `BillableScopeSnapshot`.
    - **Finance Department:** Relies on the accuracy and traceability of invoices for financial reporting and accounts receivable management.
    - **Service Advisor:** Accountable for the accuracy of the work order and the final billable scope that feeds the invoice.

## Preconditions
- The user is authenticated and authorized to perform invoice creation.
- A Work Order with a specified `workOrderId` exists and is in a terminal, invoice-ready state (e.g., `Completed`).
- The referenced Work Order has a finalized and versioned `BillableScopeSnapshot` containing all billable line items (parts, labor, fees).
- The Work Order is associated with a Customer Account that has valid billing information (billing address, billing contact method).

## Functional Behavior
- **Trigger:** The user invokes a "Create Invoice" action, providing the unique identifier of a completed Work Order.
- **Process:**
    1. The system first verifies that the source Work Order meets all preconditions (Work Execution service returns `invoiceReady=true`).
    2. The Billing Service synchronously requests and receives the finalized `BillableScopeSnapshot` associated with the given `workOrderId` from the Work Execution service.
    3. The system creates a new `Invoice` entity in the Billing domain with an initial `status` of `Draft`.
    4. The system populates the invoice header by copying required data:
        - Customer billing details from the associated Customer Account.
        - Reference fields like `poNumber` and `paymentTerms` from the Work Order or Customer Account.
    5. For each item within the received `BillableScopeSnapshot`, the system creates a corresponding `InvoiceItem` record, mapping all relevant fields (description, quantity, unit price, taxCategoryCode, taxable flag).
    6. The Billing Service calculates taxes using its own tax rules based on tax-relevant inputs from the snapshot (taxCategoryCode, serviceLocationId, customerAccountId, workOrderCompletedAt).
    7. The system calculates the invoice's financial totals (subtotal, taxAmount, totalAmount).
    8. The system establishes and persists immutable traceability links on the new invoice record, including `workOrderId`, `billableScopeSnapshotId`, and `customerAccountId`.
- **Outcome:** A new, complete draft invoice is successfully created. The system returns the `invoiceId` of the new draft and makes it available for review and further processing (e.g., posting).

## Alternate / Error Flows
- **Work Order Not Invoice-Ready:** If the source Work Order is not in a valid state for invoicing (Work Execution returns `invoiceReady=false`), the system rejects the request with a `409 Conflict` error and a clear message: "Work Order `{workOrderId}` is not in a state that allows invoicing."
- **Billable Scope Not Finalized:** If the `BillableScopeSnapshot` is missing or not marked as final, the system rejects the request with a `422 Unprocessable Entity` error and a message: "Cannot generate invoice: billable scope for Work Order `{workOrderId}` is not finalized."
- **Invoice Already Exists (Idempotency):**
    - If status is `DRAFT`: Return the existing `invoiceId` (idempotent success).
    - If status is `POSTED` or `PAID`: Reject with `409 Conflict` (corrections require credit notes, not regeneration).
    - If status is `VOID`: Regeneration allowed only via separate privileged endpoint with permission and reasonCode.
- **Missing Customer Data:** If Customer Account is missing required billing data (billing address or billing contact method), reject with `422 Unprocessable Entity` and explicit missing-field list.
- **Data Fetch Failure:** If the Billing Service fails to retrieve data from the Work Execution service due to a downstream error or timeout, the entire operation is rolled back, and a `503 Service Unavailable` error is returned.

## Business Rules
- **Rule-B1 (Domain Authority):** Billing domain owns the invoice lifecycle, validation rules, and idempotency enforcement. Work Execution domain owns the BillableScopeSnapshot contract.
- **Rule-B2 (Invoice-Ready States):** Invoice generation is allowed only when Work Execution reports `invoiceReady=true` (derived from terminal states: `COMPLETED`, `COMPLETED_PENDING_INVOICE`, or `CLOSED` if closed means completed).
- **Rule-B3 (Tax Authority):** Billing calculates taxes at invoice-generation time using its own tax rules. BillableScopeSnapshot provides tax-relevant inputs (taxCategoryCode, taxable flag, serviceLocationId) but not authoritative tax totals.
- **Rule-B4 (Idempotency):** One "primary" invoice per `workOrderId`. Existing Draft returns same invoiceId. Posted/Paid invoices block regeneration. Voided invoices allow controlled regeneration via privileged endpoint.
- **Rule-B5 (Snapshot Immutability):** The `BillableScopeSnapshot` is the immutable, single source of truth for all invoice line items. Changes to the Work Order after snapshot finalization MUST NOT affect any invoice generated from that snapshot.
- **Rule-B6 (Traceability):** All traceability links (`workOrderId`, `billableScopeSnapshotId`) on the `Invoice` entity are mandatory and immutable.
- **Rule-B7 (Data Completeness):** Invoice creation is hard-blocked if Customer Account is missing required billing data (billing address, billing contact method).

## Data Requirements
- **Invoice Entity Schema:**
    - `invoiceId` (PK, UUID)
    - `workOrderId` (FK, Indexed, Immutable)
    - `customerAccountId` (FK, Indexed)
    - `billableScopeSnapshotId` (FK, UUID/Version, Immutable)
    - `status` (String/Enum: `Draft`, `Posted`, `Paid`, `Void`)
    - `replacesInvoiceId` (FK, UUID, nullable - for regenerated invoices)
    - `issueDate` (Date)
    - `dueDate` (Date)
    - `poNumber` (String)
    - `paymentTermsId` (FK)
    - `subtotal` (Decimal)
    - `taxAmount` (Decimal)
    - `totalAmount` (Decimal)
    
- **InvoiceItem Entity Schema:**
    - `invoiceItemId` (PK, UUID)
    - `invoiceId` (FK, Indexed)
    - `sourceSnapshotItemId` (String/UUID, Immutable)
    - `description` (String)
    - `quantity` (Decimal)
    - `unitPrice` (Decimal)
    - `taxCategoryCode` (String)
    - `taxable` (Boolean)
    - `lineTotal` (Decimal)
    - `lineTaxAmount` (Decimal)

- **BillableScopeSnapshot DTO (Input Contract - Owned by Work Execution):**
    - Header fields:
        - `snapshotId` (UUID)
        - `workOrderId` (UUID)
        - `serviceLocationId` (UUID)
        - `customerAccountId` (UUID)
        - `workOrderCompletedAt` (Timestamp)
        - `snapshotVersion` (String/SemVer)
    - Line items array:
        - `itemId` (UUID)
        - `itemType` (Enum: `LABOR`, `PART`, `FEE`)
        - `description` (String)
        - `quantity` (Decimal)
        - `unitPrice` (Decimal)
        - `lineTotal` (Decimal)
        - `taxCategoryCode` (String)
        - `taxable` (Boolean)
        - `jurisdictionHints` (String, optional)

## Acceptance Criteria
- **AC1: Successful Invoice Draft Creation**
    - **Given** a Work Order is in the `Completed` state (invoiceReady=true)
    - **And** it has a finalized `BillableScopeSnapshot` with two billable line items
    - **And** the Customer Account has valid billing address and contact method
    - **When** an authorized user requests to create an invoice from that Work Order
    - **Then** the system successfully creates a new `Invoice` entity with a `status` of `Draft`
    - **And** the `Invoice.workOrderId` and `Invoice.billableScopeSnapshotId` match the source records
    - **And** the new invoice has exactly two `InvoiceItem` records that correspond to the items in the snapshot
    - **And** the `Invoice.taxAmount` is calculated by Billing using current tax rules
    - **And** the `Invoice.totalAmount` correctly reflects the sum of the `InvoiceItem` line totals plus calculated taxes.

- **AC2: Attempting to Invoice a Non-Ready Work Order**
    - **Given** a Work Order exists with invoiceReady=false (status `In-Progress`)
    - **When** a user attempts to create an invoice from that Work Order
    - **Then** the system must reject the request with a `409 Conflict` status code
    - **And** the response body must contain an error message explaining that the Work Order is not in an invoice-ready state.

- **AC3: Idempotent Invoice Creation (Existing Draft)**
    - **Given** a `Completed` Work Order already has a linked Invoice in `Draft` status
    - **When** a user attempts to create an invoice from the same Work Order
    - **Then** the system returns the existing `invoiceId` with success status (idempotent)
    - **And** no new invoice is created.

- **AC4: Block Re-Invoice of Posted Invoice**
    - **Given** a `Completed` Work Order already has a linked Invoice in `Posted` or `Paid` status
    - **When** a user attempts to create a new invoice from the same Work Order
    - **Then** the system must reject the request with a `409 Conflict` status code
    - **And** the response body must indicate invoice already exists and corrections require credit notes.

- **AC5: Block on Missing Customer Billing Data**
    - **Given** a `Completed` Work Order with finalized BillableScopeSnapshot
    - **And** the Customer Account is missing billing address
    - **When** a user attempts to create an invoice
    - **Then** the system must reject with `422 Unprocessable Entity`
    - **And** the response includes explicit missing-field list (e.g., "billingAddress").

- **AC6: Tax Calculation by Billing Domain**
    - **Given** a Work Order with BillableScopeSnapshot containing taxable and non-taxable items
    - **And** Billing's tax rules specify 8% for the tax category
    - **When** invoice is generated
    - **Then** the `Invoice.taxAmount` reflects Billing's calculation
    - **And** tax is not sourced from the snapshot (snapshot provides inputs only).

## Audit & Observability
- **Audit Log:** On successful creation, an `invoice.draft.created` event MUST be emitted. The event payload must contain `invoiceId`, `workOrderId`, `customerAccountId`, `actorUserId`, and a timestamp.
- **Metrics:**
    - `invoice.creation.success.count` (Counter, tagged by source: `workorder`)
    - `invoice.creation.failure.count` (Counter, tagged by reason: `not_ready`, `already_exists`, `missing_customer_data`, `downstream_error`)
    - `invoice.creation.duration.ms` (Timer/Histogram)
    - `invoice.idempotent_return.count` (Counter - when existing Draft is returned)
- **Logging:** Structured logs with correlation IDs must be generated for key steps: request received, precondition check (invoiceReady), downstream data fetch, tax calculation, invoice persistence, and final outcome (success/failure).

## Resolved Questions

All clarification questions have been resolved:

### 1. Primary Domain Ownership
**Decision:** `domain:billing` is the primary owner of the "Generate Invoice Draft" capability.

**Contract:**
- Billing owns: Invoice API, invoice lifecycle, validation rules, idempotency
- Work Execution owns: Work Order state machine and BillableScopeSnapshot DTO contract

**Rationale:** Invoice is a financial document with its own lifecycle belonging to Billing long-term.

### 2. Tax Calculation Authority
**Decision:** Billing calculates taxes at invoice-generation time.

**BillableScopeSnapshot provides inputs:** taxCategoryCode, taxable flag, serviceLocationId, customerAccountId, workOrderCompletedAt

**Billing produces outputs:** taxAmount, per-line tax breakdown, invoice totals using current tax rules

**Rationale:** Tax is policy-heavy and jurisdiction-dependent; must be owned where tax policy lives.

### 3. Idempotency & Re-generation Policy
**Decision:** One "primary" invoice per workOrderId with controlled regeneration.

**Rules:**
- Draft status: Return existing invoiceId (idempotent)
- Posted/Paid status: Hard block (corrections use credit notes)
- Void status: Regeneration allowed only via privileged endpoint with permission + reasonCode

**Regenerate capability:** Separate privileged endpoint `POST /billing/v1/invoices:regenerateFromWorkOrder` with permission and mandatory reasonCode.

### 4. Handling Missing Customer Data
**Decision:** Hard-block invoice creation if required billing data is missing.

**Required fields:** customerAccountId, billing address, billing contact method

**Behavior:** Reject with `422 Unprocessable Entity` and explicit missing-field list.

**Rationale:** Avoid creating Drafts that can never be posted.

### 5. Definitive Source States
**Decision:** Invoice generation allowed only when Work Execution reports `invoiceReady=true`.

**Invoice-ready terminal states:** `COMPLETED`, `COMPLETED_PENDING_INVOICE`, `CLOSED` (if closed means completed)

**Not invoice-ready:** `CANCELLED`, `VOIDED`, `IN_PROGRESS`, `ON_HOLD`, `LOCKED_FOR_REVIEW`, `REOPENED`

**Implementation:** Billing relies on Work Execution's `invoiceReady` flag to avoid hardcoding state semantics.

---
## Original Story (Unmodified – For Traceability)
# Issue #149 — [BACKEND] [STORY] Invoicing: Generate Invoice Draft from Completed Workorder

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

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


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*
