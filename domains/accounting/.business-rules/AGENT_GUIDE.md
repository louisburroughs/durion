# AGENT_GUIDE.md — Accounting Domain (Normative)

---

## Purpose

The Accounting domain is responsible for authoritative financial calculations, invoice adjustments, issuance finalization **ingestion visibility**, chart of accounts management, posting category mappings, posting rule configurations, journal entry creation, ledger posting, **AR cash application**, and **operational monitoring of accounting integrations**. It ensures financial data integrity, auditability, and compliance across the Durion POS system.

This guide is written for engineers and agents implementing Moqui services/screens and integrations for the Accounting domain. This document is **normative**.

**Non-normative companion:** `ACCOUNTING_DOMAIN_NOTES.md` (design rationale, options, rejected alternatives).

---

## Decision Index (Authoritative)

| Decision ID | Title |
| --- | --- |
| **AD-001** | Refund System of Record |
| **AD-002** | Payment Receipt vs AR Reduction |
| **AD-003** | Overpayment Handling via Customer Credit |
| **AD-004** | Manual Customer Assignment on Payments |
| **AD-005** | Timekeeping Export Mode |
| **AD-006** | Identifier Strategy (UUIDv7) |
| **AD-007** | Ingestion Monitoring Read Model |
| **AD-008** | Correlation & Trace Standard |
| **AD-009** | Raw Payload Visibility Policy |
| **AD-010** | Apply Payment Idempotency Model |
| **AD-011** | Posting Reference Canonical Identifier |
| **AD-012** | Accounting Period Enforcement |
| **AD-013** | Permission Gating Model |
| **AD-014** | Asynchronous Retry Semantics |
| **AD-015** | Export Timezone Semantics |

---

## Domain Boundaries

### What Accounting owns (system of record)

- **Financial documents & state**
  - Invoice financial state and calculations (totals, taxes/fees results, snapshots)
  - Invoice adjustments (draft-only) and audit events
  - Credit memos (AR reductions/credits) and audit events
- **Receivables accounting**
  - Accounting-side representation of received payments (e.g., `ReceivablePayment` / `Payment` read model)
  - Payment applications (`PaymentApplication`) and resulting customer credits (`CustomerCredit`)
  - Refund accounting records as a **read model** (`RefundTransaction`) derived from Payment domain events (**Decision AD-001**)
- **General ledger configuration**
  - Chart of Accounts (GL accounts)
  - Posting categories, mapping keys, GL mappings (effective-dated)
  - Posting rule sets (versioned)
- **Posting artifacts**
  - Journal entries (draft/posted) and ledger entries (immutable)
  - Accounting periods (open/close/reopen controls) (**Decision AD-012**)
- **Integration monitoring read models**
  - Persisted ingestion records for canonical accounting events (status, errors, idempotency outcome, posting references) (**Decision AD-007**)
  - Quarantine/DLQ flags as part of ingestion record status taxonomy (**Decision AD-007**)

### What Accounting does *not* own

- **Timekeeping**: time entry creation/approval state is owned by People/Timekeeping domain. Accounting owns export + export auditing.
- **Billing lifecycle**: invoice issuance/finalization is owned by Billing domain; Accounting consumes `InvoiceIssued` events and exposes ingestion status.
- **Payment capture/clearing**: payment authorization/capture/settlement is owned by Payment domain; Accounting consumes `PaymentReceived` and provides AR application workflows.
- **Work execution**: work order completion events and operational state are owned by Work Execution; Accounting consumes events and posts.

### Integration points (expanded)

- **Tax Configuration Service**: authoritative tax/fee rules.
- **Billing domain**: emits `InvoiceIssued`; Accounting ingests and posts AR/revenue/tax.
- **Payment domain / external processors**: emits `PaymentReceived` and `RefundIssued`.
- **People/Timekeeping domain**: provides approved time entries; Accounting exports approved time for payroll/cost accounting.
- **Event Bus / Message Broker**: canonical accounting event ingestion.
- **General Ledger / Ledger subsystem**: posting and balances (internal or external).
- **Schema repository**: canonical event contracts (“Durion Accounting Event Contract v1”).

---

## Key Entities / Concepts

| Entity | Description |
|---|---|
| **Invoice** | Billing document with financial totals, status, and audit snapshots. |
| **InvoiceItem** | Line items on an invoice, including pricing and taxability attributes. |
| **CalculationSnapshot** | Immutable record of tax/fee calculation details for audit and traceability. |
| **Variance** | Records differences between invoice totals and estimate snapshots, with reason codes. |
| **InvoiceAuditEvent** | Immutable audit records for invoice adjustments capturing before/after states and reasons. |
| **CreditMemo** | Separate document for credit adjustments when invoice totals would become negative. |
| **GLAccount** | Chart of Accounts entry with effective dating and classification (Asset, Liability, etc.). |
| **PostingCategory** | Business abstraction for financial transaction types, mapped to GL accounts. |
| **GLMapping** | Effective-dated mapping from PostingCategory to GLAccount and financial dimensions. |
| **PostingRuleSet** | Versioned rules mapping EventTypes to balanced journal entry lines with conditional logic. |
| **JournalEntry** | Draft or posted financial record with balanced debit/credit lines linked to source events. |
| **LedgerEntry** | Immutable posted ledger lines updating GL account balances. |
| **AccountingPeriod** | Defines open/closed periods controlling posting eligibility. |
| **ReceivablePayment / Payment (Accounting read model)** | Accounting-side record of received cash/receipts, including source metadata and unapplied amount. |
| **PaymentApplication** | Immutable record of applying a payment amount to an invoice (many-to-many over time). |
| **CustomerCredit** | Credit created when payment exceeds applied invoice amounts (overpayment policy). |
| **RefundTransaction** | Accounting-side read-only record of refund issuance and linkage to original transaction. |
| **AccountingEventIngestionRecord** | Persisted ingestion outcome for canonical events (status, idempotency outcome, errors, posting references). |
| **ExportRequest / ExportAuditEvent** | Audit record for user-initiated exports (e.g., approved time export), including parameters and outcome. |

### Relationships (actionable)

- `Invoice (1) -> (N) InvoiceItem`
- `Invoice (1) -> (N) CalculationSnapshot` (immutable history)
- `Invoice (1) -> (N) InvoiceAuditEvent` (adjustments)
- `Invoice (0..N) <- (N) PaymentApplication -> (1) ReceivablePayment`
- `ReceivablePayment (0..1) -> (0..1) CustomerCredit` (created on overpayment; policy-driven) (**Decision AD-003**)
- `AccountingEventIngestionRecord (0..1) -> (0..1) JournalEntry` (posting reference) (**Decision AD-011**)
- `RefundTransaction (0..1) -> (0..1) ReceivablePayment` and optionally `Invoice` (linkage is event-derived) (**Decision AD-001**)

---

## Invariants / Business Rules

### Invoice calculations & adjustments (existing + clarified)

- Use only the Tax Configuration Service as the authoritative source for tax and fee rules.
- Calculations must be immutable once invoice is issued.
- Monetary rounding uses HALF_UP with currency-scale precision; round per line, then sum.
- Invoice cannot be issued if tax basis data is incomplete or calculation failed.
- Variances must be recorded with canonical reason codes; large variances require approval.
- Adjustments allowed only on Draft invoices; negative totals disallowed; credit memos required for over-credit.
- Audit trail mandatory for all financial state changes.

### Receivables: payment ingestion vs application (normative)

- **Payment receipt does not reduce AR until applied** (**Decision AD-002**):
  - `PaymentReceived` creates/updates an accounting payment record (e.g., `ReceivablePayment`) in `AVAILABLE` state with `unappliedAmount`.
  - AR reduction occurs only when `PaymentApplication` records are created.
- **Application is atomic**:
  - Applying one payment to one or more invoices must be a single atomic command: either all applications succeed or none do.
- **Eligibility constraints**:
  - Payment must be in apply-eligible status (`AVAILABLE`) and have `unappliedAmount > 0`.
  - Invoices must be eligible: issued/open, not voided/cancelled, not paid-in-full, and with positive balance due.
  - Currency must match (no FX in this workflow).
- **Overpayment handling** (**Decision AD-003**):
  - If payment value exceeds applied total, remainder becomes `CustomerCredit`.
  - Backend returns deterministic results: `unappliedAmount` becomes `0` after credit creation.
- **Idempotency** (**Decision AD-010**):
  - Apply command must accept an idempotency key (`applicationRequestId`).
  - Duplicate submissions with same idempotency key must not double-apply.

### Refund visibility (read-only UI implications) (**Decision AD-001**)

- Refund execution is owned by Payment domain.
- Accounting exposes refunds as a read-only view (`RefundTransaction`) derived from refund events.
- Refund status model is backend-authoritative; UI must not infer postings.
- Conflict/quarantine outcomes must be visible via ingestion/processing status fields.

### Event ingestion monitoring (expanded) (**Decision AD-007**)

- Events must conform to canonical accounting event schema.
- Idempotency enforced by `eventId` (and for some event types also by business keys like `invoiceId + invoiceVersion`).
- Conflicting duplicates are flagged using `idempotencyOutcome = DUPLICATE_CONFLICT` and `processingStatus = QUARANTINED`.
- Unknown event types are `REJECTED` (no suspense behavior in this workflow).
- UI must treat `processingStatus`, `idempotencyOutcome`, and error codes as backend-owned enums; do not invent new meanings.

### Timekeeping export (new) (**Decision AD-005**, **Decision AD-015**)

- Export includes **only APPROVED** time entries (approval state owned by Timekeeping).
- Export is parameterized by inclusive date range and one-or-more locations.
- Export activity must be auditable (who/when/parameters/outcome).
- Missing payroll identity mappings may cause entries to be skipped; UI shows counts. Optional skipped artifact is auditor-only.

### Chart of Accounts / mappings / rules / posting (existing + UI-driven clarifications)

- Account codes must be unique; deactivation blocked by policy (non-zero balance/usage).
- GL mappings are effective-dated and non-overlapping.
- Posting rule sets must produce balanced journal entries; published versions immutable.
- Posting allowed only in open accounting periods; posting is atomic (**Decision AD-012**).

---

## Key Workflows

### Invoice Totals Calculation

- Triggered when invoice is created or line items change.
- Fetch tax/fee rules from Tax Configuration Service.
- Calculate line taxes, fees, subtotal, total tax, total fees, rounding adjustment, grand total.
- Compare with estimate snapshot; create variance if needed.
- Persist calculation snapshot and update invoice status.

### Authorized Invoice Adjustments

- Allowed only on Draft invoices by authorized users.
- Adjust line items or apply discounts with reason codes and justification.
- Recalculate totals; reject if total < $0.00.
- Persist audit event and mark invoice as adjusted.
- Emit `InvoiceAdjusted` or `CreditMemoIssued` events.

### Invoice Issuance (Billing-owned, Accounting-observed)

- Billing finalizes invoice and emits `InvoiceIssued`.
- Accounting ingests `InvoiceIssued`, applies posting rules, creates journal entry/ledger postings.
- Accounting persists ingestion outcome record for ops visibility (status, idempotency outcome, posting references).

### PaymentReceived ingestion (ops visibility + work queue)

- Ingest `PaymentReceived` event and persist an accounting payment record with source metadata.
- Expose a work queue for `AVAILABLE` and/or `customerId is null` payments for ops triage.
- Allow assigning `customerId` to an ingested payment **once only**, with justification (**Decision AD-004**).

### Apply Payment to Invoice (command workflow) (**Decision AD-002**, **AD-003**, **AD-010**)

- Load payment availability by `paymentId`.
- List eligible invoices for `customerId` + `currencyUomId`.
- Submit atomic apply command with:
  - `applicationRequestId` (idempotency key; UUIDv7)
  - allocations `{invoiceId, amountToApply}[]`
- Backend returns created `PaymentApplication` records and updated invoice/payment balances; may create `CustomerCredit`.

### RefundIssued review (read-only) (**Decision AD-001**)

- List refunds/refund events with filters (refundId/eventId/originalTxnRef/status/date).
- View refund detail with linkage to original payment/invoice and processing status/errors.
- Show conflict/quarantine indicators when status indicates `QUARANTINED` or `DUPLICATE_CONFLICT`.

### Timekeeping: Export Approved Time (user-initiated export) (**Decision AD-005**, **AD-015**)

- User selects inclusive date range, one-or-more locations, and format (CSV/JSON).
- Export is async (job + later download).
- Persist export audit record and show outcome counts and identifiers.

### Event Ingestion Monitoring (InvoiceIssued, InvoiceAdjusted, CreditMemoIssued, etc.) (**Decision AD-007**, **AD-014**)

- List ingestion records by event type, status, date range, identifiers.
- Detail view shows:
  - envelope identifiers (eventId, schemaVersion, sourceModule)
  - timestamps (receivedAt, processedAt; occurredAt optional)
  - idempotency outcome and duplicate linkage
  - posting references (journalEntryId primary; ledgerTransactionId optional)
  - error code/message and quarantine/DLQ markers
- Retry/reprocess is an async job if enabled.

---

## Events / Integrations

| Event Name | Source Domain | Description | Consumer Domain(s) |
|---|---|---|---|
| `InvoiceAdjusted` | Accounting | Emitted on authorized invoice adjustment. | Accounting ingestion monitor, downstream systems |
| `CreditMemoIssued` | Accounting | Emitted when a credit memo is created/issued. | Accounting ingestion monitor, downstream systems |
| `InvoiceIssued` | Billing | Emitted when invoice is finalized and issued. | Accounting ingestion + AR systems |
| `PaymentReceived` | Payment / External | Emitted when cash receipt is recorded/cleared. | Accounting payment ingestion + ops UI |
| `RefundIssued` | Payment / External | Emitted when a refund is issued. | Accounting refund visibility + ops UI |
| CanonicalAccountingEvent | Various | Standardized financial event envelope. | Accounting ingestion service |
| Export audit events | Accounting | Records export requests/outcomes (e.g., timekeeping export). | Auditors/Compliance |

### Integration patterns (actionable) (**Decision AD-007**)

- **Ingestion records as first-class read model**:
  - Use `AccountingEventIngestionRecord` with:
    - `processingStatus`, `idempotencyOutcome`, `errorCode`, `errorMessage`, `postingRefs[]`
  - List endpoints return summaries; detail returns `payloadSummary` + full error detail.
- **Linkage keys**:
  - Always include `eventId` and a domain key (e.g., `invoiceId`, `paymentId`, `refundId`) for navigation.
  - Provide `journalEntryId` (primary) and optionally `ledgerTransactionId` (**Decision AD-011**).
- **Retry**:
  - Retry is async job semantics with `jobId` + status endpoint (**Decision AD-014**).

---

## API Expectations (High-Level)

> Moqui service names and REST routes must be consistent and versionable. UI must not invent schemas.

### General API patterns (recommended)

- **List endpoints**: server-side pagination (`pageIndex`, `pageSize`), stable sorting (`orderBy`), and filter params.
- **Detail endpoints**: fetch by primary identifier; do not embed large payloads in list responses.
- **Command endpoints**: accept idempotency key; return created record IDs and updated aggregates.
- **Error responses**: standardize on `{ errorCode, message, details? }` with optional per-field/per-row errors (**Decision AD-013**).

### Canonical endpoint families (normative naming)

#### Timekeeping export (Accounting UI) (**Decision AD-005**)

- `POST /accounting/export/request`
- `GET  /accounting/export/status?exportId=...`
- `GET  /accounting/export/download?exportId=...`
- `GET  /accounting/export/history` (optional but recommended)

#### Payments (Accounting read model)

- `GET  /accounting/payments/list`
- `GET  /accounting/payments/detail?paymentId=...`
- `POST /accounting/payments/assignCustomer` (mutation; gated) (**Decision AD-004**)

#### Apply payment to invoices (command) (**Decision AD-010**)

- `GET  /accounting/ar/payment?paymentId=...`
- `GET  /accounting/ar/eligibleInvoices?customerId=...&currencyUomId=...`
- `POST /accounting/ar/apply` (includes `applicationRequestId` + allocations)

#### Refunds (read-only) (**Decision AD-001**)

- `GET /accounting/refunds/list`
- `GET /accounting/refunds/detail?refundId=...`

#### Ingestion monitoring (InvoiceIssued / InvoiceAdjusted / CreditMemoIssued) (**Decision AD-007**)

- `GET  /accounting/ingestion/list`
- `GET  /accounting/ingestion/detail?ingestionId=...`
- `POST /accounting/ingestion/retry` (optional; async) (**Decision AD-014**)

---

## Security / Authorization Assumptions (**Decision AD-013**)

> Do not ship screens without explicit permission mapping.

### Principles (secure-by-default)

- **Deny by default**: if permission is unknown, hide entry points and rely on backend 403 for enforcement.
- **Least privilege**: separate permissions for:
  - viewing lists/details
  - viewing raw payload JSON (`sourceEventPayload`, event payloads)
  - executing commands (apply payment, retry ingestion, export)
  - sensitive mutations (assign customerId)
- **No data leakage**:
  - On 403/404, do not reveal whether a specific ID exists.
  - Do not display raw payloads unless explicitly permitted.

### Permission mapping (normative)

- Timekeeping export:
  - view: `accounting:time-export:view`
  - execute: `accounting:time-export:execute`
- Payments:
  - view list/detail: `accounting:payment:view`
  - view `sourceEventPayload`: `accounting:events:view-payload`
  - assign `customerId`: `accounting:payment:assign-customer` (**Decision AD-004**)
- Apply payment:
  - submit apply command: `accounting:ar:apply-payment`
  - read-only access without submit permission: allowed (view-only screens rely on `accounting:payment:view` and invoice view permissions)
- Refunds:
  - view refund list/detail: `accounting:refund:view`
  - auditors: read-only access only (never granted mutation permissions)
- Ingestion monitoring:
  - view lists/details: `accounting:events:view`
  - view raw payload JSON: `accounting:events:view-payload` (**Decision AD-009**)
  - retry/reprocess: `accounting:events:retry` (**Decision AD-014**)

### Audit requirements (actionable)

- Any mutation (apply payment, assign customer, retry ingestion, export request) must record:
  - actor userId
  - timestamp
  - request identifiers (idempotency key / exportId / ingestionId)
  - parameters (redacted where needed)
  - outcome (success/failure + errorCode)
- Justification required:
  - customer assignment: mandatory (>= 10 chars) (**Decision AD-004**)
  - ingestion retry: mandatory (>= 10 chars) for human-initiated retries

---

## Observability (Logs / Metrics / Tracing) (**Decision AD-008**)

### Correlation & tracing

- Propagate W3C Trace Context headers:
  - `traceparent` (required)
  - `tracestate` (optional)
- Include key identifiers in logs and traces:
  - `eventId`, `invoiceId`, `paymentId`, `refundId`, `applicationRequestId`, `exportId`, `ingestionId`.

### Logging guidance (backend + Moqui screens)

- **Never log** raw payload bodies (payment source payload, event payloads, export file contents).
- Log structured fields:
  - operation name, userId, identifiers, status, latency, errorCode.
- For list screens, log only filter metadata (avoid logging free-text that may contain PII).

### Metrics (expanded)

Add/ensure metrics for:

- **Exports**
  - `accounting_time_export_requests_total{format,status}`
  - `accounting_time_export_latency_ms`
  - `accounting_time_export_records_exported_total`
  - `accounting_time_export_records_skipped_total{reason}` (e.g., missing mapping)
- **Payment ingestion visibility**
  - `accounting_payment_list_queries_total`
  - `accounting_payment_payload_view_denied_total`
  - `accounting_payment_customer_assignment_total{status}`
- **Payment application**
  - `accounting_payment_apply_requests_total{status}`
  - `accounting_payment_apply_conflicts_total`
  - `accounting_payment_apply_validation_errors_total{code}`
  - `accounting_customer_credit_created_total`
- **Ingestion monitoring**
  - `accounting_ingestion_records_total{eventType,processingStatus}`
  - `accounting_ingestion_retry_requests_total{eventType,status}`
- **Refund visibility**
  - `accounting_refund_queries_total{status}`
  - `accounting_refund_unresolved_reference_total`

### Traces

- Ensure spans for:
  - export request → async job → download
  - apply payment command → invoice updates → credit creation
  - ingestion record detail fetch → journal entry fetch (if UI chains calls)

---

## Testing Guidance

### Unit tests

- Business rule validators:
  - date range inclusive validation (exports, filters)
  - allocation validation (sum <= unapplied, per-invoice <= balance, >0)
  - UUIDv7 format validation for identifiers (**Decision AD-006**)
- Permission gating:
  - ensure UI hides restricted payload sections and actions when permission absent.

### Integration tests (API + UI)

- **Apply payment**
  - happy path single invoice, multi-invoice, partial, overpayment → credit created
  - 409 conflict (stale balances) → UI prompts reload
  - per-invoice validation errors → row-level highlighting (requires error schema)
  - idempotency: repeat submit with same `applicationRequestId` returns same result
- **Payment screens**
  - list filters and pagination
  - payload visibility gating (403 or redaction)
  - customer assignment allowed/denied based on status and permission
- **Ingestion monitoring**
  - list + detail for processed/duplicate/rejected/quarantined
  - payload summary vs raw payload policy enforcement
  - retry action (if supported) including async job polling
- **Timekeeping export**
  - async job flow (request → status → download)
  - empty dataset export still downloadable
  - skipped count surfaced without leaking PII
- **Refund screens**
  - unresolved reference banner when original transaction missing
  - conflict/quarantine banner when backend indicates duplicate conflict

### Contract tests

- Canonical event schema adherence for ingestion records:
  - ensure backend returns stable field names for `processingStatus`, `idempotencyOutcome`, timestamps, posting references.
- Error response schema:
  - standardize and test `{errorCode, message, details}` including per-row errors for allocations.

### Security tests

- Verify 403 behavior does not leak existence of records.
- Verify raw payload access is restricted and audited.
- Verify exports do not leak PII in UI logs or error messages.

---

## Common Pitfalls

- **System-of-record confusion**: Refund execution is Payment domain; Accounting is read-only visibility. Payments exist as accounting read models and must not be treated as payment processor truth. (**Decision AD-001**)
- **Enum drift**: Do not hardcode statuses beyond the enumerations in this guide; treat backend as authoritative for any additional enums.
- **Payload leakage**: Rendering or logging `sourceEventPayload` or raw event payloads without permission gating is a security incident. (**Decision AD-009**)
- **Timezone mistakes**: Date range filters/exports must use location timezone semantics. (**Decision AD-015**)
- **Idempotency gaps**: Apply-payment must use an idempotency key; without it, retries can double-apply. (**Decision AD-010**)
- **Overpayment UX mismatch**: UI must reflect `CustomerCredit` creation and `unappliedAmount` going to `0`. (**Decision AD-003**)
- **Ingestion visibility claims**: UI must display what ingestion records contain; do not imply completeness beyond persisted ingestion outcomes. (**Decision AD-007**)
- **Overlapping GL mappings**: Overlapping effective dates cause ambiguous posting resolutions.
- **Ignoring accounting period status**: Posting/applying actions may be blocked by closed periods; ensure error codes are surfaced and handled. (**Decision AD-012**)

---

## Open Questions from Frontend Stories — Resolved (Full Q/A)

> All questions are reproduced verbatim, followed by a clearly marked response.

### A) Cross-cutting: API naming, error schema, correlation

#### A1. **(blocking)** What are the exact Moqui service names / REST endpoints and request/response schemas for each new capability (exports, payments, refunds, ingestion monitoring, apply payment)?

**Response:**  
Use the canonical endpoint families defined in **API Expectations (High-Level)**:

- Exports: `/accounting/export/*` (request, status, download, history)
- Payments: `/accounting/payments/*` (list, detail, assignCustomer)
- Apply payment: `/accounting/ar/*` (payment, eligibleInvoices, apply)
- Refunds: `/accounting/refunds/*` (list, detail)
- Ingestion: `/accounting/ingestion/*` (list, detail, retry)

Request/response schemas are backend-authoritative; UI must not invent or reinterpret fields.

#### A2. **(blocking)** What is the standard correlation/trace header for this project (name, generation rules, propagation)?

**Response:**  
W3C Trace Context:

- `traceparent` (required)
- `tracestate` (optional)  
Frontend generates `traceparent` only if absent and propagates headers unchanged. (**Decision AD-008**)

#### A3. **(blocking)** What is the canonical error response schema (including per-field/per-row validation errors)? Provide examples

**Response:**  
Canonical shape:

```json
{
  "errorCode": "ACCOUNTING_VALIDATION_FAILED",
  "message": "Validation failed",
  "details": {
    "fieldName": "REQUIRED",
    "invoiceId-<UUID>": "INSUFFICIENT_BALANCE"
  }
}
````

Rules:

- `errorCode` is stable and machine-readable.
- `message` is user-safe.
- `details` is optional; can include field errors and per-invoice row errors keyed by invoiceId.

---

### B) Timekeeping export (Accounting → Timekeeping Exports)

#### B1. **(blocking)** Backend contract: endpoints/services for requesting export, downloading file, and status polling (if async)

**Response:**

- `POST /accounting/export/request` → returns `exportId`
- `GET  /accounting/export/status?exportId=...`
- `GET  /accounting/export/download?exportId=...`
- `GET  /accounting/export/history` (recommended)

#### B2. **(blocking)** Delivery mode: synchronous download vs async job (or both)

**Response:**
Async job only. Status lifecycle:

- `QUEUED` → `PROCESSING` → `READY` | `FAILED` (**Decision AD-005**)

#### B3. **(blocking)** Authorization: permission(s)/scope(s) for viewing screen and executing export

**Response:**

- View screen: `accounting:time-export:view`
- Execute export: `accounting:time-export:execute` (**Decision AD-013**)

#### B4. **(blocking)** Location selector source: which endpoint provides locations and how to filter by user access/business unit?

**Response:**
Locations are sourced from the Location domain and filtered by user authorization and business unit membership:

- Only active locations.
- Only locations the user is authorized to view/select.
  UI must not allow arbitrary location IDs.

#### B5. **(blocking)** Time zone semantics: are `startDate/endDate` interpreted in location, business unit, or user timezone?

**Response:**
Location timezone. UI must label the timezone used. (**Decision AD-015**)

#### B6. **(non-blocking)** Skipped-entry reporting: counts only vs downloadable skipped report; what fields are safe to include?

**Response:**
Default: counts only (exported vs skipped).
Optional skipped report: allowed only for auditor-authorized users; minimize PII (e.g., mapping identifiers, reason codes, internal keys; avoid names/addresses).

---

### C) Refunds (Accounting → Refunds)

#### C1. **(blocking)** System of record: Accounting `RefundTransaction` vs Payment domain refund log vs both?

**Response:**
Payment domain is system of record for refund execution. Accounting exposes a read-only `RefundTransaction` derived from refund events. (**Decision AD-001**)

#### C2. **(blocking)** Identifier formats: are `refundId`/`eventId` UUIDs (which version) or arbitrary strings?

**Response:**
UUIDv7. UI validates UUID format client-side. (**Decision AD-006**)

#### C3. **(blocking)** Refund status model: authoritative statuses and whether status history exists

**Response:**
Statuses:

- `PENDING`, `COMPLETED`, `FAILED`, `QUARANTINED`
  Status history may exist but is backend-provided and read-only.

#### C4. Reason codes: allowed set for `reasonCode` and whether UI should label them or treat as opaque

**Response:**
Reason codes are backend enums. UI must treat them as opaque values and display backend-provided labels (no inference).

#### C5. Linkage rules: does refund link to payment only, invoice only, or both? What linkage fields are returned?

**Response:**

- `paymentId` is mandatory.
- `invoiceId` is optional.
- `originalTxnRef` may be present for processor correlation.

#### C6. **(blocking)** Authorization: exact permission tokens for refund screens (read-only) and auditor access

**Response:**

- View refunds: `accounting:refund:view`
  Auditors are read-only and never granted mutation permissions.

#### C7. Conflict/quarantine visibility: does backend expose conflict/DLQ/quarantine records and fields?

**Response:**
Yes via `processingStatus` and `idempotencyOutcome` on ingestion/refund records, plus error fields. `QUARANTINED` must be visible and filterable.

---

### D) Payments ingestion visibility (Accounting → Payments)

#### D1. **(blocking)** Backend contract: list/get payment endpoints/services and Moqui screen path conventions

**Response:**

- `GET /accounting/payments/list`
- `GET /accounting/payments/detail?paymentId=...`
  Moqui screens must follow Accounting menu conventions and use these services.

#### D2. **(blocking)** Permission model: view list/detail, view `sourceEventPayload`, assign/change `customerId`

**Response:**

- View list/detail: `accounting:payment:view`
- View `sourceEventPayload`: `accounting:events:view-payload`
- Assign customer: `accounting:payment:assign-customer` (**Decision AD-013**)

#### D3. **(blocking)** Customer assignment policy: allowed at all? change after set? reason required and stored where?

**Response:**
Allowed once only, cannot be changed after set, justification required (>= 10 chars). Stored in audit record for the mutation and linked to the payment record. (**Decision AD-004**)

#### D4. **(blocking)** Ingestion outcome visibility: is there an entity/service for duplicates/rejected/DLQ/quarantine/ingestion logs?

**Response:**
Yes. Use `AccountingEventIngestionRecord` list/detail via `/accounting/ingestion/\*` for visibility into:

- `PROCESSED`, `REJECTED`, `QUARANTINED`
- `NEW`, `DUPLICATE_IGNORED`, `DUPLICATE_CONFLICT` (**Decision AD-007**)

#### D5. **(blocking)** Currency mismatch visibility: should UI show rejected events even if no Payment record exists?

**Response:**
Yes. Currency mismatch is represented as an ingestion record with `processingStatus = REJECTED` and error fields; it must be visible even without a persisted payment entity.

---

### E) Apply payment to invoice (Accounting → Receivables)

#### E1. **(blocking)** Permissions/roles: exact permission string(s) to gate submit; is read-only access allowed without it?

**Response:**

- Submit apply: `accounting:ar:apply-payment`
  Read-only access is allowed without submit permission (view requires `accounting:payment:view` and invoice view permissions).

#### E2. **(blocking)** Service/API naming & shapes: load payment, list eligible invoices, submit apply; include error schema for per-invoice errors

**Response:**

- Load payment: `GET /accounting/ar/payment?paymentId=...`
- Eligible invoices: `GET /accounting/ar/eligibleInvoices?customerId=...&currencyUomId=...`
- Submit apply: `POST /accounting/ar/apply` with:

  - `applicationRequestId` (UUIDv7)
  - `allocations[]` of `{invoiceId, amountToApply}`
    Per-invoice errors use the canonical `{errorCode, message, details}` where `details` may map `invoiceId` → error code/message.

#### E3. **(blocking)** Invoice status enum values: canonical strings for eligible vs ineligible

**Response:**
Eligibility is rule-based:

- Eligible: issued/open and positive balance due
- Ineligible: draft, voided, cancelled, paid-in-full
  UI should not hardcode additional enums; it should rely on eligible list from backend.

#### E4. Overpayment UX: should UI always expect `unappliedAmount=0` when credit created, and hide remaining payment?

**Response:**
Yes. Overpayment creates `CustomerCredit` and backend returns `unappliedAmount = 0`. UI must display credit creation and show the credit reference. (**Decision AD-003**)

#### E5. Idempotency identifier: should frontend generate `applicationRequestId` (UUIDv7?) or backend generate it?

**Response:**
Frontend generates `applicationRequestId` as UUIDv7 and reuses it on retry. (**Decision AD-010**, **AD-006**)

#### E6. Application date: editable vs fixed to payment effective date; constraints (open period, not future, etc.)

**Response:**
Not editable in UI. Backend controls application effective date subject to accounting period rules (must be in an open period; not future-dated). (**Decision AD-012**)

#### E7. Auto-allocation: does backend support “apply by policy” when allocations omitted?

**Response:**
No. Allocations are required. Auto-allocation is out of scope for this workflow.

#### E8. Invoice settlement fields: does apply response include updated balances/statuses or must UI refetch?

**Response:**
Response includes updated balances/statuses for affected invoices and the payment. UI may refetch for confirmation but must treat the response as authoritative for immediate display.

#### E9. Reversal workflow: separate story planned or include “Reverse” action?

**Response:**
Separate story. Apply workflow does not include reversal actions.

---

### F) Ingestion monitoring (InvoiceIssued / InvoiceAdjusted / CreditMemoIssued and other events)

#### F1. **(blocking)** Backend read model & endpoints: list/get ingestion records, field names, and entity names/PKs

**Response:**
Read model: `AccountingEventIngestionRecord` with primary key `ingestionId` (UUIDv7).
Endpoints:

- `GET /accounting/ingestion/list`
- `GET /accounting/ingestion/detail?ingestionId=...` (**Decision AD-007**, **AD-006**)

#### F2. **(blocking)** Status taxonomy: authoritative `processingStatus` and `idempotencyOutcome` enums and meanings (DLQ vs quarantined vs rejected vs duplicate/conflict)

**Response:**

- `processingStatus`: `PROCESSED`, `REJECTED`, `QUARANTINED`
- `idempotencyOutcome`: `NEW`, `DUPLICATE_IGNORED`, `DUPLICATE_CONFLICT`
  DLQ/quarantine is represented via `processingStatus = QUARANTINED` plus error fields. (**Decision AD-007**)

#### F3. **(blocking)** Timestamp filter basis: default to `occurredAt` vs `receivedAt`; which fields exist?

**Response:**
Default filtering uses `receivedAt`. Guaranteed fields:

- `receivedAt`, `processingStatus`, `idempotencyOutcome`, identifiers
  `occurredAt` may be present but is optional.

#### F4. **(blocking)** Permissions: view screens, view payload, retry/reprocess; auditor read-only access?

**Response:**

- View: `accounting:events:view`
- View payload: `accounting:events:view-payload`
- Retry: `accounting:events:retry`
  Auditors are read-only; they do not receive retry permissions. (**Decision AD-013**)

#### F5. **(blocking)** Posting references: display `ledgerTransactionId` vs `journalEntryId` vs both; which is primary for navigation?

**Response:**
Primary navigation reference: `journalEntryId`.
Secondary display reference: `ledgerTransactionId` if present. (**Decision AD-011**)

#### F6. **(blocking/security)** Payload display policy: raw JSON allowed or must use curated `payloadSummary` with redaction?

**Response:**
Default: curated `payloadSummary` only.
Raw JSON allowed only with `accounting:events:view-payload` permission and must not be cached or logged. (**Decision AD-009**)

#### F7. Retry semantics: synchronous vs async job; if async, polling mechanism (jobId + status endpoint)?

**Response:**
Retry is async only:

- `POST /accounting/ingestion/retry` returns `jobId`
- `GET /accounting/ingestion/retryStatus?jobId=...` (or equivalent job status endpoint) (**Decision AD-014**)

#### F8. UUID validation behavior: should UI block non-UUID searches or rely on backend validation?

**Response:**
UI blocks non-UUID identifiers for fields that are UUIDv7 (`eventId`, `invoiceId`, `paymentId`, `refundId`, `ingestionId`, `applicationRequestId`, `exportId`). (**Decision AD-006**)

---

## End

End of document.
