# Odoo Point of Sale — Functional Overview (Reference for pos-order Comparison)

> Source: review of Odoo 19.0 (development series), `addons/point_of_sale` plus link addons (`pos_sale`, `pos_repair`, `pos_discount`, `pos_loyalty`, `pos_hr`,
> `pos_online_payment`, `pos_self_order`, payment-terminal providers). Prepared as reference material for comparing against `durion-positivity-backend/pos-order`.
> See also:
>
> - `comp-vs-pos-order-comparison.md` — side-by-side capability map
> - `spec-pos-order-missing-functionality.md` — gap specification derived from the comparison
>
> Method references use `file.py::method` notation within `addons/point_of_sale/models/` unless another addon is named.

## Architecture in one paragraph

Odoo POS is a client-heavy, server-authoritative design. A JavaScript register builds orders offline against preloaded master data, then syncs them to the server
(`pos_order.py::sync_from_ui`), which treats the client as untrusted: prices, taxes, totals, and paid-state are all recomputed server-side before an order is accepted.
Everything hangs off three models — `pos.order` (+ `pos.order.line`), `pos.payment` (+ `pos.payment.method`), and `pos.session` — with the session doubling as the cash-drawer
accounting boundary: orders accumulate in a session, and closing the session produces the consolidated journal entry, inventory pickings (optionally), and cash over/short
handling. Link addons graft on global discounts, loyalty/gift cards, sale-order and repair-order settlement, employee tracking, online payment, and self-ordering without
changing the core lifecycle.

## 1. Order lifecycle — `pos.order`

- **States**: `draft` → `paid` → `done`, plus `cancel` (`pos_order.py:326`). `paid` is set by `action_pos_order_paid()` after full-payment validation; `done` is set at session
  close (`pos_session.py::_validate_session`) or when an invoice is generated. There is no stored `invoiced` state — invoicing is tracked via `account_move` /
  `invoice_status`.
- **Edit locking**: once `paid`/`done`, the order cannot be edited or reverted to draft (`pos_order.py::write`, guard at lines 600-602). Deletion is only possible for
  draft/cancel orders (`_unlink_except_draft_or_cancel`). Payments on a printed receipt (`nb_print > 0`) cannot be changed.
- **Cancellation**: `action_pos_order_cancel` — draft orders only; blocked when a scheduled preset time (pickup/delivery) is in the future.
- **Intake pipeline**: `sync_from_ui` → `_process_order` → `_process_saved_order`. `_process_order` resolves a valid open session (falling back to another open session on the
  same config, else erroring "No open session available"), validates the partner still exists, and merges line/payment commands. Offline resyncs are made idempotent by
  **uuid identity**: incoming `CREATE` commands whose line uuid already exists are rewritten into `UPDATE`s (lines 118-124); a non-draft order arriving again is ignored
  (tip-later tolerance). Orders carry a unique `uuid` (DB constraint) as the client's stable identity.
- **Identity & numbering**: three human-facing identifiers, all server-issued: `name` (Order Ref, assigned lazily when the order first becomes `paid`; refunds get
  `"{original} REFUND"`), `pos_reference` (Receipt Number, format `{yy}{device}-{configId}-{seq}` from a per-config `ir.sequence`), and `tracking_number` (customer-facing
  short number, `seq % 1000`). Plus `sequence_number` unique within the session.
- **Edit audit**: with `order_edit_tracking` enabled, qty reductions and line deletions are flagged (`is_edited`, `has_deleted_line`) and posted to chatter; edited orders are
  logged at session close.
- **Floating orders / parking**: draft orders persist server-side (`floating_order_name`) and are reloaded into any register on the same config
  (`_load_pos_data_domain = [('state','=','draft'),('config_id','=',config.id)]`). Presets (`pos.preset`) add scheduled orders (time slots, pickup/delivery), identification
  requirements (none/name/address), a return mode, and per-preset pricelist/fiscal position.

## 2. Order lines — `pos.order.line`

- **Fields**: product, qty, `price_unit`, `discount` (%), `price_subtotal` (tax-excluded), `price_subtotal_incl` (tax-included), `price_extra`,
  `price_type` (`original` / `manual` / `automatic` — records whether the price came from the pricelist or was hand-keyed), `tax_ids` +
  `tax_ids_after_fiscal_position`, `full_product_name`, per-line `customer_note` and `note`, line uuid (unique), JSON `extra_tax_data`.
- **Price math** (`_compute_amount_line_all`): `price = price_unit × (1 − discount/100)`, then the tax engine (`compute_all`) produces both subtotals; sign flips for refunds.
  The **order-level authoritative recompute** is `pos_order.py::_compute_prices` using `account.tax._get_tax_totals_summary` — server never trusts client totals.
- **Lots/serials**: `pos.pack.operation.lot` rows per line; available lots served from `stock.quant` in the POS source location (`get_existing_lots`); serial products get one
  lot per unit at picking time.
- **Refund linkage**: `refunded_orderline_id` points a refund line at the original line; `refunded_qty` is computed from all non-cancelled refund lines. Only the
  un-refunded remainder can be refunded again (`_prepare_refund_data` uses `qty = −(qty − refunded_qty)`) — this is the re-refund prevention mechanism.
- **Combos & attributes**: parent/child combo lines (`combo_parent_id`/`combo_line_ids`, `product.combo`), configurable attribute values carried onto receipts and stock moves.
- **Cost/margin**: `total_cost`, `margin`, `margin_percent` per line and order; computed real-time for standard-costed products, at session close for FIFO/AVCO. Config gates
  visibility to non-managers.

## 3. Pricing & discounts

- **Pricelists**: order-level `pricelist_id`, defaulted from config or partner; per-product price via `pricelist._get_product_price`.
- **Manual line discount**: `discount` % per line, gated by config `manual_discount`.
- **Global order discount**: `pos_discount` addon — a configured discount product inserted as a negative line for a percentage of the order (`discount_pc`,
  `discount_product_id`).
- **Price-change restriction**: config `restrict_price_control` limits price edits to managers; `price_type='manual'` records that an override happened. Enforcement is
  UI/group-level, not a server-side approval workflow.
- **Loyalty / promotions** (`pos_loyalty`): full `loyalty.program` engine — loyalty points, promotions, promo codes, coupons, next-order coupons, gift cards, eWallet — with
  reward lines flagged on the order (`is_reward_line`, `reward_id`, `coupon_id`, `points_cost`), server-side balance validation at order validation
  (`validate_coupon_programs`) and card/point mutation at confirmation (`confirm_coupon_programs`).

## 4. Payments — `pos.payment` / `pos.payment.method`

- **Model**: N payments per order across methods; `amount_paid = Σ payments` recomputed server-side; `amount_difference` tracked. Card metadata fields (brand, last digits,
  auth code, transaction id, ticket) captured from terminals.
- **Method types**: derived from the backing journal — `cash`, `bank`, or `pay_later` ("Customer Account" — on-account/charge sales). `split_transactions` forces a customer
  and splits receivable entries per customer. Integration flavors: `none`, `terminal` (Adyen, Stripe, Razorpay, Mercado Pago, Viva, Mollie, Pine Labs, QFPay…), `qr_code`.
- **Change**: recorded explicitly as a negative cash payment flagged `is_change` (`_process_payment_lines`); requires a cash method.
- **Full-payment validation**: `action_pos_order_paid` — `total − amount_paid` must be zero within currency precision (or within cash-rounding tolerance); underpayment on a
  paid/done order is a hard error, overpayment posts a warning.
- **Tips**: config-driven tip product; tip amount tracked on the order (`is_tipped`, `tip_amount`); restaurant "tip later" adjusts an existing payment after the fact
  (`_update_payment_line_for_tip`).
- **Cash rounding**: optional `account.cash.rounding` (round total or only the cash residual), with `biggest_tax` / `add_invoice_line` strategies.
- **Constraints**: payment method must belong to the session's config; payments on `done`/invoiced orders are immutable; a cash method cannot be shared across shops.

## 5. Refunds & returns

- Backend `refund()` copies the order into the current open session with negative quantities, capped at the un-refunded remainder per line; fresh receipt/tracking numbers;
  `is_refund=True`; the UI flow enforces **one source order per refund**.
- A full refund's paid-check target is `−original.amount_paid`; partial refunds target the returned-item value.
- Refund invoices are `out_refund` moves linked to the original invoice (`reversed_entry_id`).
- Negative lines generate a **return picking** back into stock, restoring lot owners.
- Presets can flip the register into return mode (`is_return`).

## 6. Sessions & cash management — `pos.session`

- **Lifecycle**: `opening_control` → `opened` → `closing_control` → `closed`. One non-rescue open session per config (SQL-backed constraint); opening cash defaults from the
  previous session's counted closing cash.
- **Cash control**: `cash_register_balance_start`, counted `..._end_real`, theoretical `..._end`, `cash_register_difference`; per-method breakdown at closing
  (`get_closing_control_data`); `amount_authorized_diff` limits unexplained differences.
- **Cash in/out**: `try_cash_in_out` creates bank-statement lines with reasons, permission-gated (`_has_cash_move_permission`), deletable only with a second permission and
  chatter logging.
- **Closing**: blocks while draft orders exist; `_validate_session` computes closing costs, creates deferred pickings, builds one consolidated closing `account.move`
  (sales/tax/payment-method lines), posts cash over/short to configured loss/profit accounts, flips all `paid` orders to `done`, reconciles, closes. Imbalances route through
  a Force Close wizard that writes an explicit balancing line.
- **Rescue sessions**: auto-created recovery sessions adopt orphan orders when the original session was closed under a syncing client.
- **Stock timing**: company setting `point_of_sale_update_stock_quantities` = `realtime` or `closing`, snapshotted per session (`update_stock_at_closing`).

## 7. Invoicing & receipts

- **Invoice on demand**: `to_invoice` flag → `_generate_pos_order_invoice` creates and posts an `account.move` (`out_invoice`/`out_refund`), creates the payment moves, and
  reconciles — requires a partner. `pay_later` payments put a payment term on the invoice. Ship-later + anglo-saxon forces real-time picking so COGS aligns.
- **Portal invoice request**: receipts carry a `ticket_code` + access token letting the customer self-request an invoice later.
- **Receipts**: receipt number + short tracking number; reprint counter `nb_print` (blocks payment edits once printed); email receipt with JPG ticket and invoice PDF
  attachments (`action_send_receipt`); configurable header/footer; auto-print and kitchen/order-printer routing; customer-facing display.

## 8. Inventory integration

- Order → `stock.picking` creation either real-time per order or batched at session close; positive and negative lines split into delivery vs return pickings; pickings are
  auto-validated in a savepoint with failures tracked (`failed_pickings`) rather than blocking the sale.
- Ship-later runs through procurement routes (`_launch_stock_rule_from_pos_order_lines`) with per-order shipping date and picking policy.
- Lots/serials created or matched at picking time; destination from partner stock locations.
- `pos_mrp` explodes phantom BOMs; `pos_repair` **excludes** repair-linked lines from POS stock moves (the repair order owns its own stock).

## 9. Settlement of external documents — `pos_sale` / `pos_repair`

The closest Odoo analog to Durion's workorder-driven sales:

- `pos_sale` lets the register **load and settle a sale order**: `sale.order::load_sale_order_from_pos` returns the document; lines link back via `sale_order_origin_id` /
  `sale_order_line_id`; finalizing the POS order confirms the linked quotation, records **down payments** (config `down_payment_product_id`, `down_payment_details`,
  invoice lines flagged `is_downpayment`), tracks delivered qty against the SO, and adjusts or cancels the SO's waiting pickings. The SO gains
  `amount_unpaid` / `amount_to_invoice` / `amount_invoiced` computations that account for POS-collected money.
- `pos_repair` is a thin bridge on top of that: repair orders (`repair.order`) surface through their linked sale-order lines for settlement in POS
  (`sale.order.line.is_repair_line`), and POS stock-move creation skips repair lines because the repair order manages its own parts consumption. **Pattern: the repair
  document owns work + parts; the POS owns pricing presentation, tender, and receipt.**

## 10. Staff, hardware, channels

- `pos_hr`: per-order cashier (`employee_id` + denormalized `cashier` name), employee-based register login/permissions, per-employee sales reporting.
- Barcode: nomenclature-driven product/coupon/gift-card scanning (`find_product_by_barcode`); electronic-scale integration for weighed products; cash denominations
  (`pos.bill`) for tender UI.
- `pos_online_payment`: customer pays a POS order via an online provider link/QR; the payment transaction closes out the order.
- `pos_self_order`: QR-menu/kiosk self-ordering feeding the same `pos.order` sync path, with online payment or pay-at-counter.
- Preparation/kitchen display hooks (`last_order_preparation_change`) and multi-register order sharing via bus notifications (`trusted_config_ids`).

## Load-bearing method index

| Concern | Methods |
| --- | --- |
| Order intake / idempotent sync | `pos_order.py::sync_from_ui`, `::_process_order`, `::_process_saved_order`, `::_process_payment_lines` |
| Authoritative totals | `::_compute_prices`, `pos.order.line::_compute_amount_line_all` |
| Paid validation & rounding | `::action_pos_order_paid`, `::_get_rounded_amount`, `::_is_pos_order_paid` |
| Refunds | `::refund` / `::_refund`, `pos.order.line::_prepare_refund_data`, `::_compute_refund_qty` |
| Invoicing | `::_generate_pos_order_invoice`, `::_prepare_invoice_vals`, `pos_payment.py::_create_payment_moves` |
| Stock | `::_create_order_picking`, `stock_picking.py::_create_picking_from_pos_order_lines` |
| Session & cash | `pos_session.py::_validate_session`, `::_create_account_move`, `::try_cash_in_out`, `::get_closing_control_data` |
| Settlement | `pos_sale/models/sale_order.py::load_sale_order_from_pos`, `pos_sale/models/pos_order.py::sync_from_ui` override |
