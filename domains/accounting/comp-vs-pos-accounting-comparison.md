# Odoo Accounting vs pos-accounting — Capability Comparison Map

> Purpose: a working checklist for comparing `durion-positivity-backend/pos-accounting` against
> Odoo 19's `addons/account`. Odoo detail is in `odoo-accounting-overview.md` and
> `odoo-reconciliation-internals.md`. pos-accounting references below come from
> `pos-accounting/README.md`, `src/main/java/com/positivity/accounting/internal/entity/`, and
> `.../internal/service/` as of 2026-07-16.
>
> The two systems have different missions — Odoo is a general-purpose ERP ledger with statutory
> compliance across ~100 localizations; pos-accounting is an event-driven GL service for the ETSMS
> platform. "Gap" below means "Odoo has machinery pos-accounting doesn't", not necessarily "must
> build".

## 1. Ledger core

| Concern | Odoo (`account`) | pos-accounting | Notes for comparison |
|---|---|---|---|
| Journal entry model | `account.move` + `account.move.line`; one model for entries, invoices, bills, refunds, statements | `JournalEntry` + `JournalEntryLine`; invoices live in other services (`ExtInvoice` is a projection) | Odoo unifies documents on the ledger; Durion splits by bounded context with events between them |
| Balance invariant | SQL assertion `_check_balanced`: every move sums to 0 | Verify: where is debit=credit enforced (service layer, DB constraint)? | Odoo enforces at the storage boundary, not just the API |
| Posting lifecycle | draft → posted → cancel; posting assigns number, validates locks, creates side entries | Journal entries created via `JournalEntryService` with idempotency keys (`IdempotencyKey`, `ProcessedEvent`) | Odoo has a mutable-draft stage; pos-accounting is closer to append-only with idempotent ingestion |
| Sequence / numbering | Gapless per-journal sequences, gap detection, name/date alignment constraints | Compare: entry numbering scheme and gap policy | Matters for statutory audit; Odoo invests heavily here |
| Immutability / audit | Chained-hash inalterability (`_hash_moves`), audit-trail protection, storno | `AccountingAuditLog`, immutable audit trail, outbox (`EventOutbox`, `OutboxProcessor`) | Different mechanisms, same goal; hash-chaining is the piece pos-accounting lacks |
| Multi-company | Accounts shared across companies with per-company code mapping | Single-tenant per deployment (verify) | |

## 2. Posting automation

| Concern | Odoo | pos-accounting | Notes |
|---|---|---|---|
| What drives GL lines | `_sync_dynamic_lines` recomputes tax/payment-term/rounding lines from document lines inside the same model | `GLPostingService` + `PostingRuleEvaluator` + `PostingEngineOrchestrator` evaluate versioned rule sets (`PostingRuleSet`, `PostingRuleVersion`) against ingested events | Odoo: derivation inside the document. Durion: rules applied to events — closer to a classic posting-rules engine, arguably cleaner separation |
| Account resolution | Chart template + fiscal positions + tax repartition lines | `GLMapping`, `DefaultGLMapping`, `MappingKey`, `PostingCategory`, `GLMappingResolver` | Comparable concepts; Odoo's fiscal positions add per-country tax/account substitution |
| Tax computation | Full engine: percent/fixed/division/group, price-included, repartition, cash-basis, shared Python/JS pipeline | Tax amounts arrive on events from upstream (billing) — no tax engine in pos-accounting | Deliberate: tax belongs to billing in Durion. Check that repartition-style splitting (one tax → several accounts) is expressible in posting rules |
| Rounding policy | Cash rounding strategies, last-partial residual correction, range-based FX rounding | Verify rounding handling in `InvoiceBalanceCalculator` and posting rules | Odoo's rounding discipline is a good source of test cases |

## 3. Receivables / payables and payment application

This is the richest comparison area — Odoo's reconciliation engine vs pos-accounting's payment
application. See `odoo-reconciliation-internals.md` for the full Odoo mechanics.

| Concern | Odoo | pos-accounting | Notes |
|---|---|---|---|
| Matching primitive | `account.partial.reconcile`: debit line × credit line with amounts in 3 currencies; `account.full.reconcile` derived on zero residual | `PaymentApplication` (+ `PaymentApplicationReversal`), `APPaymentAllocation` | Same shape: an allocation edge between payment and invoice. Odoo's is account-level (any two lines on a reconcilable account), pos-accounting's is document-level |
| Residual / paid state | Derived per line: `amount_residual` in two currencies; invoice `payment_state` follows | `InvoiceBalanceCalculator`, `InvoicePaymentStatusService`, `InvoiceStatusView` | Compare recompute triggers and consistency guarantees under concurrent applications |
| Partial payments & ordering | Plans + maturity-date sort decide which installment is paid first; installment strategies in register wizard | Verify allocation ordering policy in `PaymentApplicationService` | Odoo makes ordering an explicit, caller-controllable concept |
| Unapplication / reversal | Delete the partial ⇒ cascade: full reconcile removed, FX and cash-basis entries *reversed* (not deleted), payment state flipped back | `PaymentApplicationReversal`, `RefundAuthorizationService` | Same audit-preserving philosophy (reverse, don't delete) — worth confirming symmetric GL effects |
| Write-offs / discounts | Write-off lines in the payment wizard; early-payment discounts (`epd` lines) | Credit memos (`CreditMemo`, configurable revenue/tax/AR accounts), `CustomerCredit` | Different framing: Odoo folds small differences into the payment; Durion issues explicit documents |
| Group payments | One payment covering N invoices; accumulator logic to flip payment state only when total covered | Check `APPayment` → `APPaymentAllocation` multi-bill handling | |
| Multi-currency matching | Core competency: reconciliation currency selection, payment-rate policy, automatic FX gain/loss entries | Not apparent in entities (verify) | Likely N/A for a USD-only POS platform — decide explicitly and record it |
| Vendor bills / AP | Vendor bills are `in_invoice` moves; 3-way matching lives in other modules | First-class: `Vendor`, `VendorBill`, `VendorBillLine`, `VendorBillMatchCandidate`, bill-matching docs | pos-accounting's bill matching is a feature Odoo community doesn't have in this form |

## 4. Bank reconciliation

| Concern | Odoo | pos-accounting | Notes |
|---|---|---|---|
| Statement ingestion | Statement lines are posted moves against a suspense account; statements are balance checkpoints | `StatementLineMapping`, `Reconciliation`, `ReconciliationRecord`; payment-cleared events from Kafka (Stripe) | Odoo reconciles *bank* reality against ledger; pos-accounting reconciles *processor* (Stripe) reality against payments — narrower but automated |
| Auto-matching rules | `account.reconcile.model` (amount/label/partner conditions, write-off templates); interactive widget in Enterprise | Bill-matching heuristics (`VendorBillMatchCandidate`); statement mapping | Compare rule expressiveness if bank feeds are ever added |
| Suspense handling | Explicit suspense account until matched; undo returns to suspense | Verify unmatched-line handling | |

## 5. Periods, controls, compliance

| Concern | Odoo | pos-accounting | Notes |
|---|---|---|---|
| Period locking | Five lock dates (fiscal year, tax, sale, purchase, irreversible hard lock) + per-user time-boxed exceptions with audit trail | `AccountingPeriodService` | Compare: can a closed period be reopened, by whom, with what trace? Odoo's lock-exception model is a good design reference |
| Statutory compliance | Hash chains, storno, localization charts, tax grids, EDI (UBL/Factur-X/Peppol) | Out of scope (US POS context) | Record as explicit non-goals where true |
| Audit trail | Chatter + tracking + hash + protected entries | `AuditTrailService`, `AccountingAuditLog`, traceability endpoint (`/journal-entries/{id}/traceability`) | pos-accounting's event-lineage traceability is arguably stronger than Odoo's here |
| Idempotency / delivery | Not event-driven; ORM transactions | `IdempotencyService`, outbox pattern, `ReprocessingAttemptHistory` | Durion strength — no Odoo equivalent needed |

## 6. Reporting

| Concern | Odoo | pos-accounting | Notes |
|---|---|---|---|
| Financial statements | Declarative `account.report` definitions (engines: tax_tags, account_codes, domain, aggregation); renderer in Enterprise | `FinancialReportingService`: income statement, balance sheet, GL drill-down endpoint | pos-accounting reports are code; Odoo's are data. If report variety grows, Odoo's expression-engine model is worth studying |
| Operational KPIs | Journal dashboard (drafts, late, sequence holes, unhashed) | Observability stack (Grafana) | Different layers, both valid |
| Exports | — (Enterprise) | `TimekeepingExportService`, `ReportExportService`, export job API | |

## 7. Suggested comparison exercises

1. **Payment application vs reconciliation plans**: walk one multi-invoice partial payment through
   `PaymentApplicationService` and through Odoo's `_reconcile_plan`; compare ordering policy,
   residual updates, and what happens on reversal.
2. **Posting rules vs dynamic lines**: take one business event (invoice finalized) and compare the
   pos-accounting rule-set evaluation with Odoo's `_sync_dynamic_lines` + repartition output —
   specifically whether one tax amount can be split across accounts.
3. **Balance invariant**: confirm where pos-accounting enforces debit=credit per journal entry and
   add a DB-level check if it is service-layer only (Odoo does it at the storage boundary).
4. **Period close**: compare `AccountingPeriodService` semantics with Odoo's five-lock model —
   especially exception handling (who may post into a soft-locked period, and how it is audited).
5. **Gapless numbering**: decide whether journal entries need statutory sequence guarantees;
   Odoo's `sequence.mixin` (prefix/number regex, gap detection) is the reference implementation.
6. **Rounding test vectors**: port Odoo's reconciliation rounding cases (range-overlap snapping,
   last-partial residual dump) into pos-accounting tests for `InvoiceBalanceCalculator`.
