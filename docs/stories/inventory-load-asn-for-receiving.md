## 🏷️ Labels (Proposed)

### Required

- type:story
- domain:inventory
- status:needs-review

### Recommended

- agent:inventory
- agent:accounting
- agent:story-authoring

### Blocking / Risk

- none

**Rewrite Variant:** inventory-flexible

**Story Intent**

As an Inventory Clerk, I want to load an Advance Shipping Notice (ASN) so that I can receive goods accurately against expected shipments and generate proper inventory and accrual accounting entries.

**Actors & Stakeholders**

- Primary actor: Inventory Clerk
- Supporting actors / stakeholders:
  - Purchasing (creates/approves PO)
  - Accounts Payable (consumes GRNI / AP data for matching)
  - Finance / Controller (oversight of accruals)
  - Vendor (provides ASN)
- Domain Ownership Decision
  - Primary Owner: domain:inventory
  - Secondary impact: domain:accounting (accruals/GRNI tracking; accounting is consumer of receipt data and creates AP on invoice)
  - Accounting responsibilities: accrual journal entries, GRNI tracking, enabling 3‑way match
  - Inventory responsibilities: ASN lifecycle, receipt processing, on-hand updates, event emission

**Preconditions**

- Vendor exists in system and vendor ID provided.
- Referenced PO(s) exist and are in APPROVED state.
- SKU(s) exist and are present on the referenced PO(s).
- Receiving location is valid and enabled for receipts.
- GRNI (Accrued Purchases) GL account is configured for the receiving location or company.
- User performing actions has appropriate permissions: create ASN, receive, and override (if applicable).
- System configuration values:
  - Over‑receipt allowed: FALSE (default; configurable)
  - ASN required for receipt: FALSE (configurable)
  - Accrual posting: synchronous (default)
  - Matching mode: 3‑way enabled

**Functional Behavior**

**Frontend Receiving Architecture Alignment**

- ASN-backed receiving is the default frontend receiving path.
- The expected operator sequence is `createAsn -> createReceivingSession -> receiveItemsIntoStaging`.
- ASN load/review happens inside the existing receiving workflow rather than through a separate dedicated ASN screen family.
- Trucks without an ASN remain supported as a fallback path through the same receiving entry point, but they are not the primary happy path.

1. ASN Creation / Load
   - System must accept creation of an ASN record with fields:
     - vendorId
     - asnReferenceNumber (external identifier)
     - relatedPoNumbers (one or more)
     - shipDate
     - expectedArrivalDate
     - lineItems[]:
       - sku
       - quantityShipped
       - optional lotNumber
       - optional serialNumbers[]
       - optional unitOfMeasure
       - optional package/gtin
   - Validation:
     - ASN must reference at least one APPROVED PO.
     - Each `sku` must exist on the referenced PO line.
     - `quantityShipped` must be positive and not exceed PO remaining quantity unless user has override permission.
     - Duplicate ASNs (same vendor + asnReferenceNumber) are rejected.
   - Initial ASN state after load: LOADED
   - System should perform lightweight syntactic validation (fields present, numeric ranges) synchronously and deeper semantic validations (PO exists/approved) before persisting.

2. ASN State Machine
   - States: LOADED -> READY_FOR_RECEIPT -> PARTIALLY_RECEIVED -> FULLY_RECEIVED -> CANCELLED
   - Transition rules:
     - LOADED -> READY_FOR_RECEIPT when ASN passes validations and expected arrival date configured OR manual mark.
     - Receipt events move ASN to PARTIALLY_RECEIVED or FULLY_RECECEIVED based on aggregated received quantity vs shipped quantity.
     - Cancelled can be set if vendor cancels and no receipts exist, or via business process with audit trail.

3. Receiving Against ASN
   - When creating a physical receipt, the system matches receipt lines to ASN line items using PO+SKU and updates:
     - PO ordered qty
     - ASN shipped qty (for traceability)
     - Actual received qty (receipt lines)
   - Receipt processing rules:
     - On receipt, update on-hand inventory (increase by received qty per SKU/lot/location).
     - Create accrual (GRNI) journal entry for received value (see Accounting Integration).
     - Over‑receipt:
       - If `actualReceivedQty > ASN.shippedQty` or exceeds PO remaining qty, require elevated permission (or config allowing over‑receipt).
       - System must record over‑receipt as a variance line and emit event for review.
     - Under‑receipt:
       - ASN remains open for remaining qty; ASN state becomes PARTIALLY_RECEIVED.
     - Partial receipts supported: multiple receipts against same ASN line until fully received.

4. Accounting Integration — At Physical Receipt
   - For inventory items:
     - Dr Inventory Asset
     - Cr Accrued Purchases (GRNI)
   - For expense/consumable items:
     - Dr Expense
     - Cr Accrued Purchases (GRNI)
   - No Accounts Payable liability is created at receipt stage.
   - AP liability is created only when invoice is posted and matched/approved (3‑way match).
   - The receipt must provide:
     - receiptId, poId, asnId, totalAccruedAmountMinor (cents), lineItems[], locationId
   - System must link receipt lines to PO lines and store landed cost or per-line unit cost used for accrual calculations.

5. Events & Contracts
   - Emit domain events:
     - ASNLoaded {asnId, vendorId, relatedPoNumbers, lineItems, loadedBy, timestamp}
     - ReceiptCreated {receiptId, asnId?, poId, lines[], createdBy, timestamp}
     - ReceiptCompleted {receiptId, poId, asnId?, totalAccruedAmountMinor, lineItems[], locationId, timestamp}
   - Events should include stable identifiers and value amounts (minor currency units) for GL posting downstream.

**Alternate / Error Flows**

- ASN references invalid or non‑approved PO:
  - Reject ASN creation; surface clear error referencing invalid PO(s).
- Duplicate ASN (same vendor + ASN reference):
  - Reject with de-duplication error; allow user to link to existing ASN if intended.
- SKU not on PO:
  - Reject ASN line or mark line with validation error; provide option to map if business allows (requires meta-permission).
- Over‑receipt without permission:
  - Block receipt creation or mark as exception requiring manager/receiving supervisor approval workflow.
- Accrual GL account missing:
  - Block receipt completion; surface `GRNI account required` error and create a support ticket or administrative alert.
- AP invoice arrives before receipt:
  - AP records as exception; system allows invoice to create liability but marks for investigative workflow if no receipt exists (configurable).
- ASN cancelled after partial receipts:
  - Mark remaining ASN qty cancelled; do not reverse posted accruals for already received qty unless a return is processed.

**Business Rules**

- ASN must reference at least one APPROVED PO.
- Accrual (GRNI) posts only at physical receipt (not on ASN load).
- AP liability created only upon invoice posting; 3‑way match consumes receipt + PO + invoice.
- Over‑receipt is disallowed by default; requires override permission to accept extra qty.
- Partial receipts are allowed; ASN remains open until shipped quantity satisfied or explicitly closed/cancelled.
- GRNI balance must be queryable per PO and per receipt for matching.
- For lot/serial tracked SKUs, lot/serial must be captured at receipt and linked to inventory record.
- All financial postings use minor currency units; currency must be inherited from PO/vendor/company.

**Data Requirements**

- ASN record:
  - asnId (system)
  - asnReferenceNumber (vendor)
  - vendorId
  - relatedPoNumbers[]
  - shipDate, expectedArrivalDate
  - status/state
  - createdBy, createdAt, updatedAt
  - lineItems[] {lineId, poLineId, sku, quantityShipped, unitOfMeasure, unitCostMinor, lotNumber?, serialNumbers?, packageInfo?}
- Receipt record:
  - receiptId, receiptNumber, poId, asnId (nullable), createdBy, createdAt, locationId
  - lines[] {lineId, poLineId, sku, quantityReceived, unitCostMinor, lotNumber?, serialNumbers?, lineAccruedAmountMinor}
- Accounting mapping:
  - GL account for Inventory Asset
  - GL account for Accrued Purchases (GRNI)
  - Cost source: PO unit cost, vendor ASN unit cost, or receipt capture override (traceable)
- Audit fields: changeReason, changedBy, changeTimestamp
- Events: standardized payload shapes for ASNLoaded, ReceiptCreated, ReceiptCompleted

**Acceptance Criteria**

1. ASN references invalid PO
   - Given a user attempts to create an ASN referencing a non‑existent or non‑APPROVED PO
   - When the ASN load is attempted
   - Then the system rejects the ASN and returns a clear error listing the invalid PO(s)

2. ASN creation produces no GL entry
   - Given a successfully created ASN (state LOADED or READY_FOR_RECEIPT)
   - When the ASN is saved
   - Then no GL journal entries are created or posted

3. Receipt creates accrual journal entry (GRNI)
   - Given a physical receipt is completed for inventory items tied to a PO/ASN
   - When the receipt is completed/confirmed
   - Then an accrual journal entry is created: Dr Inventory Asset / Cr Accrued Purchases (GRNI) for the correct total receipt value
   - And the event ReceiptCompleted is emitted with `totalAccruedAmountMinor`

4. Inventory on-hand updates correctly
   - Given a receipt of N units for SKU X at location L
   - When the receipt completes
   - Then on-hand qty for SKU X at location L increases by N and lot/serials are recorded if applicable

5. Variance rules enforced (over/under receipt)
   - Given a receipt with actualReceivedQty > PO remaining qty or ASN shipped qty and the user lacks override permission
   - When the user attempts to accept the receipt
   - Then the system blocks the receipt and marks an exception requiring elevated approval
   - And if override permission exists, the receipt can proceed but an over‑receipt variance record is created

6. GRNI balance available to AP and for 3‑way match
   - Given receipts have been posted for a PO
   - When Accounts Payable queries for matching
   - Then the system exposes GRNI balance per PO and links receipts to PO lines for 3‑way match

7. Partial receipts handled correctly
   - Given a PO line of Q units and a receipt for less than Q units
   - When the receipt completes
   - Then the PO and ASN line reflect received qty, ASN becomes PARTIALLY_RECEIVED, and remaining qty remains open for subsequent receipts

8. ASN duplicate detection
   - Given an ASN attempt with same vendor + asnReferenceNumber as an existing ASN
   - When the ASN is created
   - Then the system rejects with duplicate ASN error and suggests linking to existing ASN

**Audit & Observability**

- Emit structured events (ASNLoaded, ReceiptCreated, ReceiptCompleted) to event bus with correlation IDs (poId, asnId, receiptId).
- Log receipts and ASN state transitions with audit metadata (actor, timestamp, reason).
- Expose metrics:
  - asn.loaded.count
  - receipt.created.count
  - receipt.completed.count
  - receipt.exceptions.count (over‑receipts, missing GL)
  - grni.balance.gauge (per PO)
- Traces: end-to-end trace from ASN load -> receipt -> GL post for troubleshooting.
- Retention: audit logs retained per company policy (configurable), with immutable trail for accounting reconciliations.

**Open Questions**

- None required for implementable behavior given applied safe defaults. If business needs differ (e.g., allow ASN to create accruals), mark as clarification and add `blocked:clarification`.

## Original Story (Unmodified – For Traceability)

[BACKEND] [STORY] Inventory: Load ASN for Receiving
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

integration:general-ledger

Rewrite Variant: inventory-receiving-strict

Story Intent

As an Inventory Clerk,
I want to load an Advance Shipping Notice (ASN),
so that I can receive goods accurately against expected shipments and generate proper inventory and accrual accounting entries.

Actors & Stakeholders

Inventory Clerk (primary actor)

Purchasing

Accounts Payable

Finance / Controller

Vendor

Domain Ownership Decision

Primary Owner: domain:inventory

Accounting Impact: domain:accounting owns:

Accrual journal entries

GRNI tracking

3-way match eligibility

ASN is operational. Accrual occurs only at receipt.

Preconditions

Vendor exists.

Referenced PO exists and is APPROVED.

SKU exists on PO.

Receiving location valid.

Functional Requirements

1. ASN Load

System must allow creation of ASN record containing:

Vendor ID

ASN Reference Number

Related PO Number(s)

Ship Date

Expected Arrival Date

Line Items:

SKU

Quantity Shipped

Optional lot/serial info

Validation rules:

ASN must reference at least one APPROVED PO.

SKU must exist on referenced PO.

Quantity shipped cannot exceed PO ordered quantity unless override permission.

ASN State:

LOADED

READY_FOR_RECEIPT

PARTIALLY_RECEIVED

FULLY_RECEIVED

CANCELLED

1. Receiving Against ASN

During receipt:

System compares:

PO Ordered Qty

ASN Shipped Qty

Actual Received Qty

Rules:

Over-receipt requires elevated permission.

Under-receipt leaves ASN open.

Receipt updates on-hand inventory.

Accounting Integration
At Physical Receipt

For inventory items:

Dr Inventory Asset
Cr Accrued Purchases (GRNI)

For expense items:

Dr Expense
Cr Accrued Purchases (GRNI)

No Accounts Payable liability created at this stage.

AP liability created only upon Invoice posting.

Events

ASNLoaded

ReceiptCreated

ReceiptCompleted

ReceiptCompleted must include:

receiptId

poId

asnId

totalAccruedAmountMinor

lineItems[]

locationId

Accounting Contract Requirements

Accounting must:

Create accrual journal entry.

Track GRNI balance per PO.

Link receipt to PO.

Expose data for 3-way matching.

Acceptance Criteria

ASN cannot reference invalid PO.

ASN creation produces no GL entry.

Receipt creates accrual journal entry.

Inventory on-hand updates correctly.

Variance rules enforced.

GRNI balance available to AP.

Partial receipts handled correctly.

Applied Safe Defaults

Over-receipt allowed = FALSE.

ASN required for receipt = FALSE (configurable).

Accrual posting = synchronous.

Matching mode = 3-way enabled.

GRNI account required before receipt allowed. (See <attachments> above for file contents. You may not need to search or read the file again.)
