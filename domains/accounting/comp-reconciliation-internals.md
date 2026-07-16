# Odoo Reconciliation Internals — Deep Dive (Reference for pos-accounting Comparison)

> Source: direct code review of Odoo 19.0 `addons/account`: `models/account_move_line.py:2138-3174`, `models/account_partial_reconcile.py`, `models/account_full_reconcile.py`.
> Companion to `odoo-accounting-overview.md`. Relevant pos-accounting counterparts: `PaymentApplicationService`, `InvoiceBalanceCalculator`, `Reconciliation` /
> `ReconciliationRecord` / `StatementLineMapping` entities — see `odoo-vs-pos-accounting-comparison.md`.

## The data model: three layers of state

Reconciliation state lives on the journal item (`account.move.line`) and in two link models:

- **`account.partial.reconcile`** is the atomic unit: one matched pair of a debit line and a credit line, always positive amounts, stored in _three_ currencies — `amount`
  (company currency), `debit_amount_currency` and `credit_amount_currency` (each line's own foreign currency) (`account_partial_reconcile.py:48-56`). It also carries pointers
  to the side-effect entries it caused: `exchange_move_id` and (indirectly, via `move.tax_cash_basis_rec_id`) cash-basis entries.
- **`account.full.reconcile`** is almost pure bookkeeping — two one2many fields. It exists when a connected group of lines has zero residual everywhere. Its `create()`
  bypasses the ORM and stamps `full_reconcile_id` onto lines and partials with raw `UPDATE ... FROM (VALUES %s)` SQL for speed (`account_full_reconcile.py:26-42`).
- On the line, everything is **derived**: `matched_debit_ids`/`matched_credit_ids` are the partials touching the line from each side; `_compute_amount_residual`
  (`account_move_line.py:811`) computes `amount_residual = balance − Σ(matched debits) + Σ(matched credits)` in both company and foreign currency, and sets `reconciled = True`
  only when **both** residuals are zero and the account allows reconciliation. `matching_number` is a denormalized Char (`<full id>` for full matches, `P<n>` marks partial
  groups, `I*` marks import-pending groups — regex-constrained at line 1591).

## Entry point: reconciliation "plans"

`reconcile()` is just `self._reconcile_plan([self])` (`account_move_line.py:3134`). A _plan_ is a nested list expressing order: `[amls_A, amls_B]` means reconcile A, then B;
wrapping them in an inner list additionally means "then try to reconcile the leftovers of A+B together". Callers like the payment wizard express "match payment against invoice
1, then invoice 2, then sweep the remainders" in one call, one balancing pass, and one batch of DB writes.

`_optimize_reconciliation_plan` (`:2681`) normalizes the plan into a tree: it sorts each leaf's lines by maturity date, currency, and amount; splits leaves that mix currencies
into per-currency sub-nodes (same-currency matches happen before cross-currency ones); and runs the eligibility gate `_check_amls_exigibility_for_reconciliation` (`:2643`):
all lines must be un-reconciled, not cancelled, on **one single account**, in **one company**, and the account must have `reconcile=True` (or be cash/credit-card).
Cross-account matching (e.g. a write-off to a different account) therefore always requires an intermediate entry — a partial can never span accounts.

Everything runs inside two context managers — `_check_balanced` and `_sync_dynamic_lines` (`:2809`) — so all entries created along the way (exchange, cash basis) are validated
once at the end.

## The pairing sweep

`_prepare_reconciliation_amls` (`:2537`) is a classic two-pointer merge: one iterator over debit-ish lines, one over credit-ish lines, looping
`_prepare_reconciliation_single_partial` on the current pair; whichever side is exhausted advances. Crucially, **residuals are tracked in plain Python dicts**
(`aml_values_map`, `:2829`), not re-read from the ORM — all partials for the whole plan are created in a single `create()` call afterwards (`:2858`), so compute fields can't
be trusted mid-loop. A deliberate performance trade: one batched insert instead of N, at the cost of manually mirroring the residual arithmetic.

`_prepare_reconciliation_single_partial` (`:2228`) is the heart; nearly all its complexity is multi-currency:

1. **Choose the reconciliation currency.** Via `_prepare_move_line_residual_amounts` (`:2150`), each line advertises which currencies it can offer a residual in — its own, the
   company's, and (for receivable/payable lines in company currency) a _simulated_ residual in the counterpart's foreign currency using a rate picked by `get_odoo_rate`. Rate
   selection encodes business policy: when an invoice meets a payment, the **payment's accounting rate** wins; invoices use their `invoice_date` rate;
   `forced_rate_from_register_payment` (from the wizard) overrides everything. If both sides can express themselves in the same foreign currency, reconciliation happens in
   that currency; otherwise it falls back to company currency.
2. **Compute the matched amount** as `min(debit residual, credit residual)` in the chosen currency, then convert to the other currencies. A subtle rounding device (`:2321`,
   `:2410`): since a rounded balance of 1000.00 could represent anything in `[999.995, 1000.005)`, converted amounts are computed as a _range_, and if the debit and credit
   ranges overlap the code snaps them equal — deliberately suppressing 1-cent "exchange differences" that would be rounding noise.
3. **Compute exchange-difference amounts** for whichever side got fully matched but still has a nonzero residual in the _other_ currency (`:2434-2502`). Two regimes:
   reconciling in company currency fixes leftover `amount_residual_currency`; reconciling in foreign currency fixes leftover `amount_residual` (a real fiscal gain/loss). The
   `no_exchange_difference` context key disables this — used when reconciling the exchange entries themselves, to stop recursion.

## Exchange difference entries

`_prepare_exchange_difference_move_vals` (`:2991`) builds one `entry` move in the company's `currency_exchange_journal_id`, with a pair of lines per fixed line: one on the
original account (to be reconciled with the residual) and one on the company's gain (`income_currency_exchange_account_id`) or loss account.
`_create_exchange_difference_moves` (`:3080`) creates them with `no_exchange_difference` in context and posts them only if both original moves were posted. Each partial is
linked to its exchange move (`partial.exchange_move_id`, `:2872-2883`) so unreconciling can find and reverse it.

## Side effects on partial creation

`AccountPartialReconcile.create/unlink` do three notable things (`account_partial_reconcile.py:105-231`):

- **Payment state**: creating partials flips matched payments from `in_process` to `paid` when the partial amount covers the payment (`_get_to_update_payments`, `:156` —
  including an accumulator for group payments covering several invoices); unlinking flips them back.
- **Matching number**: `_update_matching_number` (`:186`) treats matchings as a union-find problem — each partial is an edge between two line-nodes; graphs are merged and each
  connected component gets a stable number (the minimum partial id, prefixed `P`, or the full-reconcile id once full). Implemented as a single-pass graph merge plus one bulk
  SQL `UPDATE`.
- **Cascade on unlink**: deleting a partial deletes its full reconcile, then _reverses_ (not deletes) posted cash-basis and exchange entries, and unlinks draft ones
  (`:117-142`). So unreconciling a paid invoice under cash-basis taxes produces counter-entries, preserving the audit trail. `remove_move_reconcile()` on the line is simply
  "unlink all my partials."

## Cash-basis tax entries

If any involved company uses `tax_exigibility` and the matched account is receivable/payable, `_create_tax_cash_basis_moves` runs per plan (`account_move_line.py:2890`).
`_collect_tax_cash_basis_values` (`account_partial_reconcile.py:237`) computes, per partial, the **percentage** of the origin invoice that this partial pays (in company or
foreign currency depending on the invoice) and a **payment rate** (from the counterpart line, or on-the-fly at payment date when currencies differ; refund-vs-invoice matching
keeps each document's own rate). The generator (`:522`) then mirrors each base and tax line of the invoice at that percentage into the `tax_cash_basis_journal_id`: tax amounts
move from the _transition account_ to the real tax account, with the right tax grids stamped, grouped by (currency, partner, account, taxes, repartition line, analytic) to
minimize line count, and with a last-partial correction that dumps the exact remaining residual to kill cumulative rounding drift (`:569-579`). Transition-account lines are
themselves reconciled with the invoice's tax lines afterwards via a nested `_reconcile_plan` (`:701`) — which is why the transition account must be reconcilable.
`draft_caba_move_vals` snapshots the inputs so that if a draft invoice involved in the matching is later posted with changed amounts, Odoo can detect the partial is stale.

## Full reconcile detection

Back in `_reconcile_plan_with_sync` (`account_move_line.py:2896-2958`): after partials exist, the code walks each plan's lines, expands to the whole connected component via
matching numbers (`_reconciled_by_number`), and checks every involved line with `is_line_reconciled` — with a currency subtlety: if the component mixes currencies, zero
_company-currency_ residual suffices; if single-currency, zero _foreign-currency_ residual is required. Fully-settled components get an `account.full.reconcile`, whose
creation re-stamps matching numbers. Finally, cash-basis rounding lines on the transition account are auto-reconciled against the exchange entry (`:2963-2979`), and
`_reconcile_post_hook` fires `_invoice_paid_hook` for invoices that just transitioned to paid/in-payment.

## Things worth knowing if you touch (or reimplement) this logic

- **Order matters everywhere**: plan order, the maturity-date sort, and the debit/credit iterator sweep determine _which_ installment gets paid first. The
  `reduced_line_sorting` context key changes the sort for payment-term lines.
- **The residual dict shadow-state** (`aml_values_map`) must stay consistent with what the eventual `create()` produces; the same "shadow" idea appears as
  `shadowed_aml_values`, which lets Enterprise's bank-reconciliation widget _preview_ a reconciliation with hypothetical accounts/dates without writing anything.
- **Recursion guards** are all context keys: `no_exchange_difference`, `no_exchange_difference_no_recursive`, `no_cash_basis`, `move_reverse_cancel`, `add_caba_vals`.
  Reconciliation calls itself (exchange entries reconcile, cash-basis entries reconcile) and these flags are the only thing preventing infinite regress — fragile but
  pervasive.
- **Invariants enforced**: partials never cross accounts or companies; a line is `reconciled` only at zero residual in both currencies; full reconciles are strictly derived
  and rebuilt (never edited); import flows can pre-mark lines with `I…` matching numbers and `_reconcile_marked` (`:3149`) performs the real matching once all involved moves
  are posted, even force-enabling `reconcile` on the account.

**Design philosophy**: partials are the only source of truth; everything else (residuals, flags, matching numbers, fulls) is derived and bulk-recomputed; and every monetary
side effect (FX gain/loss, cash-basis tax) is materialized as a real journal entry linked back to the partial that caused it — so undoing a match is always "delete the edge,
reverse its entries."
