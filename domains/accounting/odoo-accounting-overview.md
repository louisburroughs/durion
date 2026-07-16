# Odoo Accounting — Functional Overview (Reference for pos-accounting Comparison)

> Source: review of Odoo 19.0 (development series), `addons/account` (~36,000 lines of Python, ~50 models)
> plus sibling addons. Prepared as reference material for comparing against
> `durion-positivity-backend/pos-accounting`. See also:
> - `odoo-reconciliation-internals.md` — deep dive on Odoo's matching engine
> - `odoo-vs-pos-accounting-comparison.md` — side-by-side capability map

## Architecture in one paragraph

Everything in Odoo accounting reduces to one double-entry primitive: the journal entry
(`account.move`) and its journal items (`account.move.line`). Invoices, bills, credit notes,
payments, and bank statement lines are all either subtypes of, or thin wrappers around, that same
move model. Around this core sit four subsystems: a tax engine, a chart-of-accounts/localization
loader, a payment-and-reconciliation layer, and a declarative reporting framework. The community
`account` module (branded "Invoicing") contains all the ledger mechanics; Enterprise
(`account_reports`, `account_accountant`) adds the report renderer and the interactive
reconciliation UI on top.

## 1. The ledger core — `account.move` / `account.move.line`

- **One model for all documents.** `move_type` distinguishes plain entries from customer
  invoices/refunds, vendor bills/refunds, and receipts. Invoice lines are just move lines filtered
  by `display_type` (`product`, `tax`, `payment_term`, `cogs`, rounding, sections/notes), so the
  accounting view and the invoice view are two projections of the same rows.
- **Lifecycle** is draft → posted → cancelled. Posting (`action_post` → `_post`,
  `account_move.py:5526`) validates partners, dates, archived records, and lock dates, then assigns
  the sequence number, creates analytic lines, and reconciles reversal pairs. A SQL assertion
  (`_check_balanced`) guarantees every move sums to zero.
- **Amounts** are stored as a signed `balance` (source of truth); debit/credit are derived from its
  sign, with storno (negative-amount) accounting supported for countries that mandate it.
  Multi-currency is handled per line via `amount_currency` and a computed rate.
- **Dynamic line sync.** `_sync_dynamic_lines` (`account_move.py:3747`) is a context manager around
  create/write that keeps derived lines (payment-term installments, tax lines, cash rounding,
  discount allocations) automatically consistent with the product lines the user edits. This is
  what makes an invoice recompute its receivable and tax lines live.
- **Integrity controls**: gapless per-journal sequences with gap detection, name/date alignment
  constraints, and an optional chained-hash inalterability mode (`inalterable_hash`,
  `_hash_moves`) that makes posted entries tamper-evident and immutable — required by several
  fiscal jurisdictions.
- **Journals** (`account.journal`) partition the ledger by type (sale, purchase, bank, cash,
  credit card, general) and carry the controls: hash restriction (a one-way switch), separate
  refund/payment sequences, default accounts.

## 2. Taxes and the chart of accounts

- **`account.tax`** supports percent, fixed, division (tax-included), and grouped taxes, with
  price-included/excluded behavior defaulted at company level. The engine is a batching pipeline
  (`_get_tax_details` and the "base line" helpers in `account_tax.py`) shared between Python and
  JS so backend, POS, and frontend compute identically; the legacy `compute_all` is a wrapper.
- **Repartition lines** split each tax between accounts and report tags, separately for invoices
  vs refunds — this is how one tax feeds multiple ledger accounts and tax-return grids.
- **Cash-basis taxes** (tax due on payment) route through a transition account and generate
  entries at reconciliation time (see the reconciliation internals doc).
- **`account.account`** carries the account-type taxonomy (receivable, payable, liquidity, income,
  expense, …), a per-company code mapping (accounts are shareable across companies), the
  `reconcile` flag, and reporting tags. Tax "grids" are just named tags matched by name to report
  expressions.
- **Localization is code-driven**: `chart_template.py` loads each country's chart, taxes, and
  fiscal positions from CSV/Python data registered with the `@template` decorator
  (`try_loading` → `_load`) — there are no database template records.

## 3. Payments, bank, reconciliation

- **`account.payment`** is a record that lazily generates its journal entry (liquidity line on an
  "outstanding" account, counterpart on the partner's receivable/payable). Its state machine
  (draft → in_process → paid) is driven by reconciliation: matched against invoices ⇒ reconciled,
  matched against a bank statement ⇒ paid.
- **The register-payment wizard** batches invoice lines by partner/currency/account, supports
  partial payments, write-offs, early-payment discounts, and installment strategies, then posts
  and reconciles in one flow.
- **Bank statement lines *are* moves** (`_inherits account.move`): importing a line immediately
  posts an entry between the bank account and a suspense account; reconciliation later replaces
  the suspense side with the real counterpart. Statements themselves are just balance checkpoints
  (`is_complete`, `is_valid`).
- **Reconciliation** is modeled by `account.partial.reconcile` (matched debit/credit pairs, with
  automatic exchange-difference entries and cash-basis tax moves) rolled up into
  `account.full.reconcile` when residuals hit zero. `account.reconcile.model` defines
  auto-matching/write-off rules (the interactive matching UI is Enterprise).

## 4. Reporting, sending, and controls

- **`account.report`** in community is purely declarative — lines, columns, and expressions with
  engines like `tax_tags`, `account_codes`, `domain`, `aggregation`. The renderer lives in
  Enterprise; community ships the invoice PDF (QWeb), an SQL invoice-analysis view, and the
  journal kanban dashboard with KPIs (drafts, late bills, sequence holes, unhashed entries).
- **`account.move.send`** is the outbound pipeline: batched PDF generation, email templates, and a
  set of EDI hooks that addons override for UBL/CII/Factur-X, Peppol, etc. A mirror import mixin
  decodes uploaded PDFs/XML into draft bills.
- **Company-level controls** include five lock dates (fiscal year, tax, sale, purchase, plus an
  irreversible hard lock) with time-boxed, per-user lock exceptions and an audit trail; also
  fiscal-year settings, storno, and anglo-saxon accounting flags.
- **Analytic accounting** (dimensions/cost centers) rides on move lines via JSON distributions
  across multiple analytic plans.

## 5. Ecosystem

The core is deliberately extended by small addons: `account_payment` (portal payment via payment
providers), `account_edi` + `account_edi_ubl_cii` (e-invoicing framework and UBL/Factur-X
formats), `account_peppol` (Peppol network), `account_check_printing`, `account_debit_note`, plus
~100 `l10n_*` localizations that supply charts, taxes, and country reports through the template
mechanism.

## Overall assessment

A mature, tightly integrated design. Strengths: the single-move abstraction (one audit trail for
everything), the synced dynamic-line mechanism, a tax engine shared across Python/JS, code-driven
localization, and serious compliance features (hashing, hard locks, storno). Trade-offs:
`account_move.py` and `account_tax.py` are 7,400 and 5,300 lines respectively with heavy
compute-field interdependencies; the payment/statement models maintain two-way synchronization
with their underlying moves that is powerful but fragile; and full usability (report rendering,
reconciliation widget) depends on Enterprise modules.
