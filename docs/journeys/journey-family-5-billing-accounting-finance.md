---
title: "Journey Family 5: Billing, Accounting, and Finance"
family: journey-family-5
status: draft
created: 2026-04-01
updated: 2026-04-01
---

## Journey Family 5: Billing, Accounting, and Finance

### Family Purpose

This is a journey family, not a single journey. It mixes front-counter checkout, AR operations,
accounting support, and finance governance, which are distinct user stories with different rhythms
and actors.

### Family Candidate Journeys

- Checkout to payment completion
- Invoice issuance to AR application
- Accounting event triage to journal resolution
- Period close and finance review

### Family Journey Overview

| Candidate Journey | Dominant Personas | Story IDs | Notes |
| --- | --- | --- | --- |
| `Checkout to payment completion` | `Customer Support Associate`, `Location Manager` | `#67`, `#69`, `#70`, `#71`, `#72`, `#73` | Clean counter-facing journey with checkout policy gate, card capture, and receipt closeout. |
| `Invoice issuance to AR application` | `Service Advisor`, `Accounting Associate`, `Location Manager` | `#177`, `#178`, `#179`, `#180`, `#209`, `#210`, `#211`, `#212` | Operational billing and receivables journey from draft invoice through AR reconciliation. |
| `Accounting event triage to journal resolution` | `Accounting Associate`, `System Administrator` | `#181`, `#186`, `#190`, `#200`, `#201`, `#205`, `#206` | Back-office ingestion monitoring, manual JE authoring, and ledger posting. |
| `Period close and finance review` | `Accounting Manager`, `Controller`, `Finance Manager` | `#188`, `#189`, `#191`, `#198`, `#199`, `#202`, `#203`, `#204` | Accounting configuration, trial balance, period lock, financial reporting, and audit sign-off. |

---

## Journey: Checkout to Payment Completion

### Checkout to Payment Purpose

This journey covers the front-counter path from checkout policy evaluation through card capture,
void/refund exceptions, and customer-facing closeout with receipt delivery and payment status
confirmation.

### Checkout to Payment Stages

- Checkout policy gate
- Card capture
- Exception handling
- Closeout confirmation

### Checkout to Payment Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
| --- | --- | --- | --- |
| `Customer Support Associate` | Checkout policy gate | `#67` | Billing checkout policy evaluation — PO capture (write-once), PO override with elevation token, and credit/terms gating before finalization. Blocks checkout when policy returns blockers. |
| `Customer Support Associate` | Card capture | `#73` | Initiate card authorization and capture (SALE_CAPTURE or AUTH\_ONLY → CAPTURE). Persists token/transaction references only; triggers receipt generation entry point. |
| `Location Manager` | Exception handling | `#72` | Void authorized payments or refund captured/settled payments with reason code, approval workflow, and reversal audit history. |
| `Customer Support Associate` | Closeout confirmation | `#71` | Print/email receipt or suppress delivery ("No Receipt"). Handles reprints with policy-gated reason code and elevation token. |
| `Customer Support Associate` | Closeout confirmation | `#69`, `#70` | Read-only accounting posting/ingestion status and invoice payment application status on Invoice Detail screen. Answers customer questions and confirms payment application without leaving POS. |

---

## Journey: Invoice Issuance to AR Application

### Invoice Issuance to AR Purpose

This journey covers the operational billing path from draft invoice review through finalization,
issuance, AR payment application, and exception handling for adjustments, credit memos, and
refund traceability.

### Invoice Issuance to AR Stages

- Invoice preparation
- Issuance and delivery
- AR application and reconciliation
- Adjustments and exceptions

### Invoice Issuance to AR Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
| --- | --- | --- | --- |
| `Service Advisor` | Invoice preparation | `#212` | Trigger backend tax/fee/total calculation on Draft invoice. Displays totals, per-line tax, calculation status, variance vs estimate snapshot, and gates Issue action when calculation is incomplete. |
| `Accounting Associate` | Invoice preparation | `#211` | Display immutable traceability snapshot (workorder, estimate/version, approval artifacts) and render backend-provided issuance blockers. Gates Issue action when blockers are present. |
| `Accounting Associate` | Issuance and delivery | `#209` | Finalize and Issue a Draft invoice via backend. Renders issuance policy and blockers pre-action; locks editing post-issuance; shows issued metadata and delivery status fields. |
| `Accounting Associate` | AR application and reconciliation | `#178` | Apply a cleared payment atomically to one or more eligible invoices. Explicit allocation required; remainder becomes customer credit per policy. Idempotent submission via UUIDv7 key. |
| `Accounting Associate` | AR application and reconciliation | `#179` | PaymentReceived ingestion visibility with unapplied/unassigned payments work queue. Supports one-time customer assignment with mandatory justification. |
| `Location Manager` | Adjustments and exceptions | `#210` | Authorize adjustments on Draft invoices — edit lines or apply invoice-level discount with required reason code. Backend emits `InvoiceAdjusted` or `CreditMemoIssued`. |
| `Accounting Associate` | Adjustments and exceptions | `#180` | Review `InvoiceAdjusted` / `CreditMemoIssued` ingestion records. Navigate to posting references and journal entries. Triage rejections and quarantine. |
| `Accounting Associate` | Adjustments and exceptions | `#177` | List and view refund transactions from `RefundIssued` events. Trace refundId → paymentId → invoiceId. Investigate quarantine/duplicate conflicts. Read-only; no refund initiation. |

---

## Journey: Accounting Event Triage to Journal Resolution

### Accounting Event Triage Purpose

This journey covers the back-office accounting operations path from incoming accounting event
ingestion monitoring through quarantine triage, manual and event-derived journal entry authoring,
and controlled posting to the ledger.

### Accounting Event Triage Stages

- Event ingestion monitoring
- Quarantine and suspense triage
- Journal entry authoring
- Ledger posting

### Accounting Event Triage Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
| --- | --- | --- | --- |
| `Accounting Associate` | Event ingestion monitoring | `#181` | `InvoiceIssued` ingestion monitoring — list/filter by processingStatus and idempotencyOutcome, view detail with posting references, optional async retry for eligible records. |
| `Accounting Associate` | Event ingestion monitoring | `#205` | General accounting event ingestion monitoring — validate completeness/integrity across event types. Inspect schema failures, quarantine indicators, and posting references. |
| `Accounting Associate` | Event ingestion monitoring | `#206` | Idempotency and deduplication outcomes visibility (`NEW`, `DUPLICATE_IGNORED`, `DUPLICATE_CONFLICT`). Trigger safe async retry with required justification and job status polling. |
| `System Administrator` | Quarantine and suspense triage | `#186` | Ingestion suspense/quarantine queue (QUARANTINED/REJECTED events). View failure details, trigger idempotent async retry with justification. Strict permission gating on view, raw payload, and retry actions. |
| `Accounting Associate` | Journal entry authoring | `#201` | View draft Journal Entry generated from a source event. Shows header traceability (eventId, mapping rule version) and balanced lines per currency. Read-only; validates balance before posting workflow. |
| `Accounting Associate` | Journal entry authoring | `#190` | Create manual Journal Entry with required reason code, balanced debit/credit lines, and period controls. Server-side and client-side validation. Results in immutable POSTED record. |
| `Accounting Associate` | Ledger posting | `#200` | Post a Journal Entry to the ledger with period eligibility check. Handles atomic success, period-closed rejection, and concurrency conflicts. Displays post result with JE reference and source event linkage. |

---

## Journey: Period Close and Finance Review

### Period Close and Finance Purpose

This journey covers the management-led path from accounting configuration and governance through
trial balance validation, period lock, financial statement generation, and auditable sign-off by
controllers and finance leadership.

### Period Close and Finance Stages

- Accounting configuration and governance
- Pre-close review
- Period close execution
- Audit trail and sign-off

### Period Close and Finance Swimlanes

| Canonical Persona | Stage | Story IDs | Notes |
| --- | --- | --- | --- |
| `Finance Manager` | Accounting configuration and governance | `#204` | Create and maintain Chart of Accounts (GL accounts) — create, edit limited fields, deactivate with effective dating, audit metadata. |
| `Finance Manager` | Accounting configuration and governance | `#199` | Configure accrual vs cash basis per business unit with effective fiscal-period boundary. Auditable basis history; permission-gated. |
| `Controller` | Accounting configuration and governance | `#203` | Manage Posting Categories, Mapping Keys, and effective-dated GL mappings. No-overlap validation; audit history per category. |
| `Controller` | Accounting configuration and governance | `#202` | Create, version, validate, publish, and archive Posting Rule Sets (EventType → accounting postings). Prevents publish when backend reports imbalance. |
| `Controller` | Pre-close review | `#198` | Generate Trial Balance for a selected accounting period with account/dimension filters. Drilldown: Trial Balance → ledger lines → journal entry → source event. CSV export. |
| `Controller` | Pre-close review | `#189` | Generate P&L and Balance Sheet with drilldown to contributing accounts, journal lines, and source event references. Export with access control enforcement. |
| `Accounting Manager` | Period close execution | `#191` | Create, close, and (with elevated permission) reopen accounting periods per business unit. Mandatory reason and elevated permission gate for reopen. Period open/close audit history. |
| `Controller` | Audit trail and sign-off | `#188` | Read-only ledger traceability and explainability viewer — Source Event → Mapping Version → Rule Version → Journal Entry → Ledger Lines, with reversal chain navigation. Immutability explicitly surfaced in UI. |
