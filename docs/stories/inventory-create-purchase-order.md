## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:inventory
- status:needs-review

### Recommended

- agent:inventory
- agent:accounting
- agent:story-authoring
- impacts:accounting
- integration:ap
- integration:general-ledger

### Blocking / Risk

- none

**Rewrite Variant:** inventory-flexible

**Story Intent**

As a Purchasing Manager, I want to create and approve a Purchase Order (PO) so that procurement is formally authorized, inventory replenishment is controlled, and financial commitments are visible to Accounts Payable and the General Ledger.

**Actors & Stakeholders**

- **Primary Actor:** Purchasing Manager (user role able to create/revise/submit POs)
- **Secondary Actors / Systems:**
  - Inventory Control (manages stock lifecycle and receipts)
  - Accounts Payable (AP) (invoice matching, liability creation)
  - Controller / Finance (GL oversight, reporting)
  - Vendor (external counterparty)
  - Receiving Clerk (performs receipts)
  - Tax Calculation Service (if separate)
  - Budgeting/Encumbrance Service (if enabled)
- **Domain Ownership Decision**
  - Primary Owner: `domain:inventory` — owns PO lifecycle, state model, and operational behaviors.
  - Accounting Integration: `domain:accounting` — owns GL postings, encumbrance / accrual semantics, and AP liability lifecycle. Inventory emits events/contracts consumed by Accounting.

**Preconditions**

- Vendor exists and is marked Active in vendor master.
- Line Items reference existing SKUs in Product Catalog.
- Required GL accounts are configured and available for use:
  - Inventory Asset
  - Expense (non-stock)
  - Accrued Purchases (GRNI / GR/IR)
  - Accounts Payable Liability
  - Optional Encumbrance / Budget Reserve account (if encumbrance enabled)
- System currency and vendor currency mapping exist; currency conversion service available if multi-currency.
- User has appropriate role/permission to create/approve POs per approval policy.

**Functional Behavior**

1. PO Creation

- System shall allow creating a PO with:
  - Header: vendorId, poDate, currency, paymentTermsId, expectedDeliveryDate, shipToLocationId, requestedBy, comment, customTags
  - Lines: lineNumber, skuId, description, quantity (decimal), unitCostMinor (integer), taxCodeId, glAccountId (optional override), requestedDeliveryDate
- Computed fields:
  - lineTotalMinor = quantity × unitCostMinor (apply per-line rounding rules)
  - subtotalMinor = sum(lineTotalMinor)
  - taxMinor = computeTaxPerLineOrHeader(taxCodeId) aggregated to tax totals
  - grandTotalMinor = subtotalMinor + taxMinor
- Persist line-level cost and currency (store minor units and currency code).
- Allow attaching supporting documents (quotes, attachments).

1. PO States (strict lifecycle)

- States: DRAFT → (SUBMITTED_FOR_APPROVAL) → APPROVED → PARTIALLY_RECEIVED → FULLY_RECEIVED → CLOSED
- CANCELLED is a terminal state allowed from DRAFT or APPROVED (subject to business rules).
- Only `APPROVED` POs may be used as the basis for receipts and AP three-way matching.

1. Approval Workflow

- Draft POs may be submitted for approval.
- Approval thresholds configurable by total value and may map to approval groups/roles.
- On Approval:
  - Lock price and quantity (fields become read-only for receiving/matching; revisions allowed via revision workflow).
  - Emit `PurchaseOrderApproved` event.
  - Record approverId, approvalTimestamp, approvalReason.
  - If encumbrance enabled, trigger encumbrance posting contract (see Accounting Integration).

1. Revision Workflow

- Approved PO can be revised via an explicit revision operation producing a new version:
  - Preserve immutable audit trail of previous versions.
  - Revisions increment `versionNumber` and emit `PurchaseOrderRevised` with priorVersion and delta.
  - Revisions that increase value beyond configured thresholds must re-trigger approval flow (re-approval).

1. Receiving & Inventory Integration

- Receiving records reference an `approved` PO and decrement open quantities.
- Partial receipts allowed; each receipt reduces `openQuantity` and `openValueMinor` per line and at PO level.
- When all line open quantities reach zero, PO transitions to `FULLY_RECEIVED`.
- Receipts must include receiptId, receivedBy, receivedAt, quantity, condition, and reference to shipment/ASN if provided.

1. Accounting Integration

- On Approval:
  - If encumbrance accounting is enabled (config flag):
    - Emit or request encumbrance posting: Dr Purchase Commitments / Cr Budget Reserve (encumbrance account mapping from budget service).
  - If encumbrance disabled: no GL posting at approval time.
- On Receipt (or GR as configured internationally):
  - Emit event for accrual processing so Accounting can create GRNI accrual entries: Dr Inventory Asset / Cr Accrued Purchases (or expense account for non-stock).
- On Invoice Match:
  - AP will create payable: Dr Accrued Purchases / Cr Accounts Payable (or reverse accruals and post expense entries).
- All GL posting responsibilities, account validation, and final posting decisions are owned by `domain:accounting`.

1. Search / Visibility / APIs

- Provide query APIs for AP to retrieve:
  - PO by poId, vendorId, status, openBalanceMinor, openQuantity per line.
  - List of POs eligible for 2-way/3-way matching and their matched status.
- Provide pagination, filters by date ranges, currency, and locations.
- Events include sufficient metadata for downstream consumers (see Events section).

**Alternate / Error Flows**

- Creating PO with missing or inactive vendor → reject with validation error (400) and clear error code.
- Creating PO with SKU not found → reject with validation error referencing skuId.
- Approval attempted without required approvals or by unauthorized user → return 403 and record attempt in audit trail.
- Receipt against non-APPROVED PO → blocked; API should return 409 with descriptive message.
- Revision that reduces quantities below already-received quantities → reject or require adjustment of receipts (business decision; by default reject).
- Currency mismatch between PO and invoice → AP to handle via matching rules; PO records currency and conversions must be applied consistently.
- GL posting failure (accounting service down) for encumbrance or accrual → retry with exponential backoff and alert; record failure event for operational triage.

**Business Rules**

- Only APPROVED POs may be used to create receipts or be matched to invoices.
- PO totals and signed-off line unit costs/quantities are immutable after approval; changes only via revision workflow with a new version.
- Approval thresholds determine if single approver or multi-approver flow required.
- Encumbrance accounting is toggleable per-tenant/config (`encumbranceEnabled: boolean`). Default = OFF.
- Tax calculation is by default line-level unless tenant-specific config overrides to header-level.
- Matching modes: default 3-way (PO ↔ Receipt ↔ Invoice). System must support 2-way and configurable matching rules.
- Rounding and currency arithmetic follow financial rounding rules specified in tenant config (round half-to-even unless overridden).
- For heterogeneous lines (some inventory, some expense), GL account per-line governs whether receipt creates inventory asset postings or expense postings.
- All money fields stored in minor units plus currency code; conversions use authoritative FX service.

**Data Requirements**

- PO Header:
  - poId: UUID
  - vendorId: UUID
  - poNumber: string (human-friendly, unique per tenant)
  - status: enum
  - versionNumber: integer
  - currency: ISO4217
  - totalMinor: integer
  - taxMinor: integer
  - createdBy, createdAt, updatedBy, updatedAt
  - approval: { approverId, approvalTimestamp, approvalNotes }
  - encumbranceRef?: string
- PO Line:
  - lineId: UUID
  - lineNumber: integer
  - skuId?: UUID
  - description: string
  - quantityDecimal: decimal(18,6)
  - unitCostMinor: integer
  - lineTotalMinor: integer
  - taxCodeId?: string
  - glAccountId?: string
  - openQuantityDecimal: decimal
- Events (payload must include):
  - poId, tenantId, vendorId, totalAmountMinor, currency, lineItems[], status, versionNumber, timestamp, actorId

**Acceptance Criteria**

- Given a PO in DRAFT, When a receiving attempt is made against that PO, Then the system rejects the receipt with HTTP 409 and logs the attempt.
- Given encumbranceEnabled = true, When a PO is Approved, Then the system emits an encumbrance posting event and the Accounting subscriber records a DR/CR encumbrance pair (or returns confirmation).
- Given encumbranceEnabled = false, When a PO is Approved, Then no GL encumbrance posting is created at approval time.
- Given an APPROVED PO with line open quantities, When partial receipts are recorded, Then line `openQuantity` and PO `openBalanceMinor` decrement correctly and system emits `PurchaseOrderReceipt` events.
- Given receipts that fully satisfy all line quantities, When final receipt is processed, Then PO transitions to `FULLY_RECEIVED` and `openBalanceMinor` is zero.
- Given a PO with line items and tax codes, When PO is created, Then tax totals aggregate correctly and `grandTotalMinor = subtotalMinor + taxMinor`.
- Given an Approved PO, When AP requests open POs for vendor, Then API returns PO with `openBalanceMinor` and per-line `openQuantity` suitable for 2/3-way matching.
- Given a PO revision is performed, When revision completes, Then `versionNumber` increments, previous version remains immutable in audit, and `PurchaseOrderRevised` event includes priorVersion and delta.
- Given approval occurs, When approval completes, Then `PurchaseOrderApproved` event is emitted with required payload: poId, vendorId, totalAmountMinor, currency, lineItems[], approvalStatus.
- Given GL posting errors on encumbrance or accrual, When posting fails, Then system retries and records a `PurchaseOrderAccountingError` event with failureReason and retry metadata.

**Audit & Observability**

- Audit log entries for: PO created, PO submitted for approval, PO approved, PO revised, PO cancelled, Receipt created, Invoice matched.
- Audit must record actorId, timestamp, before/after snapshots for state transitions, and change reason.
- Metrics:
  - counter: purchase_orders_created_total
  - counter: purchase_orders_approved_total
  - gauge: purchase_orders_pending_approval
  - histogram: po_approval_latency_seconds
  - counter: po_accounting_errors_total
- Tracing:
  - Correlate PO lifecycle operations with a `poId` trace/span attribute for cross-service traces (Inventory → Accounting → AP).
- Events:
  - Publish domain events to message bus (schema v1) for `PurchaseOrderCreated`, `PurchaseOrderApproved`, `PurchaseOrderRevised`, `PurchaseOrderCancelled`, `PurchaseOrderReceipt`.
  - Ensure events are idempotent-consumer-friendly (include versionNumber and eventId).
- Logging:
  - Log level INFO for lifecycle events; WARN/ERROR for failed integrations.
  - Include tenantId, poId, vendorId, totalMinor in structured logs.

**Open Questions**

- Approval policy specifics: Should the approval flow support delegated approvers and multi-stage approvals (beyond threshold)? (If yes, we need approval matrix config).
- Revision-to-receipt conflict: Should the system allow revision that reduces quantities below already-received quantities (default = disallow)?
- Encumbrance GL mapping: What explicit GL account mapping should be used for encumbrances (tenant budget service or static mapping)?
- Multi-currency: What FX timing should be used for encumbrance vs. eventual invoice settlement?

(If any of these require decisions, add `blocked:clarification` and set status accordingly.)

## Original Story (Unmodified – For Traceability)

STORY 1 — [BACKEND] [STORY] Inventory: Create Purchase Order (Procure-to-Pay Initiation)
🏷️ Labels (Proposed)
Required

type:story

domain:inventory

status:needs-review

Recommended

agent:inventory

agent:accounting

agent:story-authoring

Cross-Domain

impacts:accounting

integration:ap

integration:general-ledger

Rewrite Variant: inventory-accounting-strict

Story Intent

As a Purchasing Manager,
I want to create and approve a Purchase Order (PO),
so that procurement is formally authorized, inventory replenishment is controlled, and financial commitments are visible to Accounts Payable and the General Ledger.

Actors & Stakeholders

Primary Actor: Purchasing Manager

Inventory Control (Stock oversight)

Accounts Payable (Invoice matching & liability creation)

Controller / Finance (GL oversight)

Vendor (external counterparty)

Domain Ownership Decision

Primary Owner: domain:inventory

Accounting Integration: domain:accounting owns:

GL entries

AP liability lifecycle

Accrual recognition

Inventory owns PO lifecycle and operational state.

Preconditions

Vendor exists and is Active.

Items exist in Product Catalog.

GL Accounts configured:

Inventory Asset

Expense (non-stock)

Accrued Purchases (GRNI)

Accounts Payable Liability

Optional Encumbrance Account

Functional Requirements

1. PO Creation

System shall allow creation of a Purchase Order with:

Vendor ID

PO Date

Currency

Payment Terms

Expected Delivery Date

Ship-To Location

Line Items:

SKU

Description

Quantity

Unit Cost (minor units)

Tax Code

GL Account (inventory or expense)

System calculates:

Line Total = Quantity × Unit Cost

Subtotal

Tax

Grand Total

PO totals must be immutable after approval except through revision workflow.

1. PO States

DRAFT

APPROVED

PARTIALLY_RECEIVED

FULLY_RECEIVED

CLOSED

CANCELLED

Only APPROVED POs may be received against.

1. Approval Workflow

Draft POs require approval.

Approval threshold configurable by total value.

Approval must:

Lock price and quantity.

Emit PurchaseOrderApproved event.

1. Accounting Integration
A. Encumbrance (Configurable)

If encumbrance accounting enabled:

On Approval:

Dr Purchase Commitments (Encumbrance)
Cr Budget Reserve / Encumbrance Offset

If disabled:

No GL posting at approval stage.

Configuration flag must control behavior.

1. Accounts Payable Visibility

Approved PO must:

Be queryable by AP module.

Expose open quantities and remaining financial balance.

Be eligible for:

2-way match (PO ↔ Invoice)

3-way match (PO ↔ Receipt ↔ Invoice)

Events

PurchaseOrderCreated

PurchaseOrderApproved

PurchaseOrderCancelled

PurchaseOrderRevised

Each event must include:

poId

vendorId

totalAmountMinor

currency

lineItems[]

approvalStatus

Accounting Contract Requirements

Accounting module must:

Subscribe to PO approval events.

Validate GL configuration.

Record encumbrance (if enabled).

Maintain PO reference for later accrual and invoice matching.

Acceptance Criteria

Cannot receive against DRAFT PO.

GL entry created only if encumbrance enabled.

PO balances decrement correctly as receipts occur.

AP can retrieve open PO balances.

Tax totals calculated correctly.

Revisions require version increment.

Applied Safe Defaults

Encumbrance accounting default = OFF.

Approval threshold default = unlimited (manual approval required).

Tax calculation default = line-level.

Currency default = system base currency.

Matching mode default = 3-way enabled.  --> Let's try this again (See <attachments> above for file contents. You may not need to search or read the file again.)
