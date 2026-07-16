# Odoo Tax Engine — Deep Dive (Reference for pos-tax Comparison)

> Source: code review of Odoo 19.0 (development series), `addons/account/models/account_tax.py` (5,308 lines) plus `account_move_line_tax_details.py`, `partner.py` (fiscal
> positions), and the `account_tax_python` / `l10n_account_withholding_tax` addons. Companion documents: `comp-accounting-overview.md`, `comp-vs-pos-tax-comparison.md`.

## Big picture

Odoo's tax engine is a **declarative, data-driven calculator embedded in the ledger**. A tax is a configuration record (`account.tax`) whose math, ledger accounts, and
tax-return grid mappings are all data; the engine is a pure computation pipeline that both the Python backend and the JavaScript frontend (POS, invoice widget) execute
identically. Its output is not just amounts — it is fully-formed journal items with accounts and report tags attached. This is the fundamental contrast with a rate-lookup
service: Odoo answers "what are the tax lines of this document and where do they post", not just "how much tax".

## 1. The tax record: what drives computation

Key fields on `account.tax` (`account_tax.py:80-210`):

- **`amount_type`**: `percent` (base × rate), `fixed` (amount × quantity), `division` (tax-included percentage: a 10% division tax on 180 yields 20, i.e. `180/(1−10%)=200`
  gross), and `group` (a container of `children_tax_ids`; constraints forbid nesting and recursion). The `account_tax_python` addon adds `code` — a sandboxed formula (see §8).
- **`price_include`** is now _computed_ from `price_include_override` (`tax_included`/`tax_excluded`) falling back to the **company-level default** (`_compute_price_include`,
  `:302`). Price-included means the tax is extracted from the price rather than added on top — the VAT-country norm.
- **`include_base_amount`** / **`is_base_affected`**: a tax can feed the base of subsequent taxes (eco-taxes, surcharges), and a tax can opt out of being affected. These two
  flags plus `sequence` define the tax-on-tax topology.
- **`tax_exigibility`**: `on_invoice` vs `on_payment` (cash basis). Does not change the math; it changes _where and when_ the tax amount posts (see §7).
- **`has_negative_factor`** (`:210`): true when any repartition line has a negative factor — the engine then emits a mirrored **reverse-charge** line (self-assessed VAT: +tax
  and −tax simultaneously, hitting different grids).

## 2. Flattening and batching

`_flatten_taxes_and_sort_them` (`:897`) sorts by `(sequence, id)` and replaces each `group` tax in-place by its children. `_batch_for_taxes_computation` (`:925`) then walks
the flattened list in reverse, merging adjacent taxes into a _batch_ when they share `amount_type`, effective `price_include`, and `include_base_amount` (and the earlier tax
isn't required to feed the later one's base). Batching is not an optimization detail — the price-included and division formulas use the **summed batch percentage**, so two 10%
included taxes extract from the price together as 20%.

## 3. The evaluation algorithm — `_get_tax_details` (`:1139`)

Given `price_unit`, `quantity`, and the flattened taxes:

1. Optional `special_mode` overrides effective price inclusion for every tax: `'total_included'` (treat the given price as tax-inclusive), `'total_excluded'` (tax-exclusive),
   or `None` (respect each tax's own flag). This is how the same engine serves "price is gross" (POS) and "price is net" callers.
2. `raw_base = quantity × price_unit` (rounded now only in `round_per_line` mode).
3. **Three evaluation passes**: fixed taxes first (descending) since they can shift a price-included batch's base; then price-included taxes (descending), extracting tax from
   the price; then price-excluded taxes (ascending). Formulas (`_eval_tax_amount_*`, `:1084-1137`):
   - fixed: `sign(price_unit) × quantity × amount`
   - percent included: `raw_base / (1 + Σbatch%) × rate`
   - percent excluded: `raw_base × rate`
   - division included: `raw_base × rate`; division excluded: `raw_base × rate / (1 − Σbatch%)`
4. After each tax lands, `_propagate_extra_taxes_base` (`:978`) pushes `±tax_amount` into other taxes' `extra_base_for_tax`/`extra_base_for_base` according to a sign table
   over (price_include × special_mode × include_base_amount) — this implements tax-on-tax and price-extraction consistently. Reverse-charge taxes get a mirrored negative
   amount.
5. A final descending pass computes each tax's base (`raw_base + extra_base`, minus the batch's total tax when effectively price-included) and links each tax to the subsequent
   taxes it feeds. Output: per-tax `{base, amount}` plus `total_excluded` / `total_included`.

**Python/JS parity** is contractual: every engine method carries the marker `[!] Mirror of the same method in account_tax.js. PLZ KEEP BOTH METHODS CONSISTENT`, and product
records are pre-serialized to primitive dicts so both runtimes see identical inputs. One engine, two runtimes, no drift between the invoice the cashier sees and the entry the
ledger stores.

## 4. The base-line pipeline (documents → tax details → journal items)

The engine is wrapped by a normalization pipeline that all consumers share (account, sale, purchase, POS, hr_expense, EDI import):

1. **`_prepare_base_line_for_taxes_computation`** (`:1593`) converts any record or dict into a normalized "base line": price_unit, quantity, discount, currency + `rate`,
   accounting `sign`, `is_refund` (selects invoice vs refund repartition), `special_mode`, `special_type` (`early_payment` / `cash_rounding` / `non_deductible`), and optional
   _manual_ amounts (`manual_tax_amounts`, `computation_key`) that freeze earlier results (used by down payments and global discounts).
2. **`_add_tax_details_in_base_line(s)`** (`:1739/1813`) runs the engine per line and stores raw (unrounded) per-tax base/amount in both document currency and company
   currency.
3. **`_round_base_lines_tax_details`** (`:2184`) is the global rounding stage — see §5.
4. **`_add_accounting_data_in_base_lines_tax_details`** (`:2504`) expands each tax into its **repartition lines** (accounts + tags, invoice vs refund), applies cash-basis
   account redirection, handles reverse-charge negative factors, and attaches a `grouping_key` (partner, currency, account, analytic, tax, repartition line, tags…).
5. **Aggregation**: `_aggregate_base_lines_tax_details` (`:2647`) groups by any key; `_get_tax_totals_summary` (`:2715`) produces the invoice-footer structure — tax groups
   with `preceding_subtotal` buckets, special `display_base_amount` for fixed/division taxes, cash rounding applied via `add_invoice_line` or `biggest_tax` strategy.
6. **`_prepare_tax_lines`** (`:3038`) turns the grouped repartition data into concrete journal items and _diffs them against the existing tax lines_, returning
   `tax_lines_to_add/update/delete` — this is what `account_move._sync_tax_lines` applies whenever an invoice line changes.

Global discounts and down payments (`_prepare_global_discount_lines` `:3940`, `_prepare_down_payment_lines` `:4005`) reuse the pipeline: merge lines per tax, scale to the
target amount, re-round, distribute deltas, then freeze the result as manual amounts so later recomputations don't drift.

## 5. Rounding discipline

Two company-level modes:

- **`round_per_line`**: base and each tax amount are rounded at computation time. Required where law mandates per-line rounding (e.g. receipts).
- **`round_globally`**: raw amounts flow through unrounded; `_round_base_lines_tax_details` rounds at the _document_ level and then reconciles: per (tax, currency,
  refund-ness, price-include, computation_key) group it computes `delta = round(Σ raw) − Σ rounded` and spreads leftover cents via `_distribute_delta_amount_smoothly`
  (`:1842`) — biggest lines absorb cents first. Price-included groups round base+tax _together_ and back out the base so gross totals stay exact. User-edited tax lines are
  re-anchored so manual tweaks survive recomputes.

The docstring at `:2202` works a 17.79/17.80 split example. This delta-distribution logic is the part naive implementations get wrong (per-line rounding that doesn't sum to
the advertised total) — it is a rich source of test vectors.

## 6. Repartition lines, tags, and tax reports

`AccountTaxRepartitionLine` (`:5240`) is the bridge from "a tax amount" to "ledger + tax return": each tax has base and tax repartition lines, separately for invoice and
refund documents, each with a `factor_percent` (12-decimal precision), an optional account, and `tag_ids` (tax grids). Validation (`_validate_repartition_lines`, `:562`)
requires exactly one base line per document type, positive factors summing to 100% (and negative ones to −100% for reverse charge), and invoice/refund lists that pair up.
`use_in_tax_closing` flags which repartition lines the period-close entry sweeps into tax payable/receivable (accounts held on `account.tax.group`).

Tags stamped on move lines are what tax reports read. `account_move_line_tax_details.py` provides the SQL (`_get_query_tax_details`, `:28`) that maps every tax line back to
its base lines — prorating amounts, dispatching tax-on-tax, window-function rounding, and a fallback join for imported entries — one row per (tax line, base line) for the
reporting engine.

## 7. Cash basis and exigibility

For `on_payment` taxes, the invoice posts the tax to a **transition account** (`cash_basis_transition_account_id`, must be reconcilable); the real tax account is hit only when
payment reconciliation generates the cash-basis entry (see `comp-reconciliation-internals.md`). `account.move.always_tax_exigible` marks documents where exigibility is
immediate (misc entries with no cash-basis involvement). CABA grids are withheld from the invoice lines and stamped on the cash-basis entry instead (`include_caba_tags` gating
in the accounting-data step).

## 8. Jurisdiction/applicability determination

Odoo does **not** compute jurisdiction from address at calculation time. Instead:

- Default taxes cascade: product (`taxes_id` / `supplier_taxes_id`) → account → company default (`_get_computed_taxes`, `account_move_line.py:960`).
- **Fiscal positions** (`account.fiscal.position`, `partner.py:26`) then _substitute_ taxes and accounts per counterparty: auto-apply rules match country / country group /
  state / zip range / VAT-required, with manual override per partner. In Odoo 19 the mapping is inverted onto the tax itself (`original_tax_ids` / `replacing_tax_ids`;
  `map_tax`, `partner.py:154`).
- US-style address-level sales tax (Avalara/TaxCloud connectors) is layered on in separate modules that override the document's tax computation with provider results.

So jurisdiction resolution is configuration + substitution, not geocoding — the right model for VAT, weaker for US destination-based sales tax without a connector.

## 9. Extension points

- **`account_tax_python`**: `amount_type='code'` with an AST-whitelisted formula (arithmetic/comparison only, `price_unit`, `quantity`, `product` fields), normalized into
  paired `py_formula`/`js_formula` so both runtimes evaluate it — sandboxing plus parity.
- **Withholding** (`l10n_account_withholding_tax`): negative taxes flagged `is_withholding_tax_on_payment` are filtered out of invoice computation via `filter_tax_function`
  and instead instantiated as withholding lines on payments, generating extra move lines through the same repartition mechanism.
- **EDI/import**: the same base-line pipeline runs in reverse when importing UBL/Factur-X — imported amounts become manual amounts so recomputation doesn't fight the source
  document.

## 10. Design takeaways

1. **Taxes-as-data**: math type, inclusion, cascading, ledger accounts, and report grids are all configuration; ~100 country localizations ship as CSV/Python data on one
   engine.
2. **One engine, two runtimes**: enforced Python/JS parity removes an entire class of frontend/backend drift bugs.
3. **Raw-then-round**: keep unrounded amounts as the source of truth, round at the aggregation boundary, and distribute deltas deterministically (biggest first).
4. **Repartition is the killer feature** for compliance: one tax amount → N ledger postings + M report grids, different for invoices vs refunds, with reverse-charge as just a
   negative factor.
5. **Manual-amount freezing** (`manual_tax_amounts` + `computation_key`) is how the pipeline coexists with imported documents, down payments, and user edits without recompute
   drift.
6. Weaknesses to note: a 5,300-line module with subtle flag interactions (`special_mode` × `price_include` × `include_base_amount` sign tables), dead parameters awaiting
   cleanup, and complexity that only pays off if you need multi-country VAT semantics.
