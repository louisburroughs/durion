# Odoo Inventory (stock) — Functional Overview (Reference for pos-inventory Comparison)

> Source: review of Odoo 19.0 (`odoo/release.py` → `(19, 0, 0, FINAL)`), `addons/stock` plus sibling addons (`stock_account`, `product_expiry`, `stock_picking_batch`,
> `stock_landed_costs`, `purchase_stock`, `stock_dropshipping`). Prepared as reference material for comparing against `durion-positivity-backend/pos-inventory`. See also:
>
> - `comp-vs-pos-inventory-comparison.md` — side-by-side capability map
> - `SPEC-pos-inventory-odoo-parity.md` — specification of missing functionality derived from the comparison
>
> Two v19 architecture shifts worth knowing before reading older Odoo material: (1) procurement groups were replaced by a lightweight `stock.reference` model
> (`addons/stock/models/stock_reference.py`); (2) `stock.valuation.layer` was removed — inventory value now lives directly on `stock.move.value` with a `product.value`
> history model (`addons/stock_account/models/product_value.py`).

## Architecture in one paragraph

Everything in Odoo inventory reduces to two primitives: the **quant** (`stock.quant` — current on-hand, keyed by product × location × lot × package × owner, with
`reserved_quantity` as a first-class column) and the **move** (`stock.move` — a planned or executed quantity change with a full state machine:
draft → confirmed/waiting → partially_available → assigned → done). Every flow — receipt, delivery, internal transfer, scrap, inventory adjustment, manufacturing issue —
is a move between two locations; virtual locations (supplier, customer, inventory-loss, production, transit) close the double-entry loop so that quantity, like money in a
ledger, is never created or destroyed, only moved. Around this core sit four subsystems: a **routing engine** (push/pull rules organized into routes, driving multi-step
receipts/deliveries, MTO, and inter-warehouse resupply), a **replenishment engine** (min/max orderpoints with forecast- and lead-time-aware quantity calculation), a
**reservation engine** (removal strategies FIFO/LIFO/FEFO/closest/least-packages applied when moves reserve quants), and a **valuation engine** (`stock_account`:
standard/AVCO/FIFO costing carried on the moves themselves).

## 1. On-hand core — `stock.quant`

- One row per (product, location, lot, package, owner) with `in_date` for FIFO/FEFO ordering. `quantity` is physical on-hand; `reserved_quantity` is held stock;
  `available_quantity = quantity − reserved_quantity` (computed).
- Quants are always stored in the product's reference UoM; moves may be expressed in any UoM and are converted (with deliberate down-rounding so reservations never exceed
  the requested quantity).
- Housekeeping (`_quant_tasks`): merge duplicate quants created by concurrent transactions (raw SQL dedup), reconcile `reserved_quantity` against the sum of reserving move
  lines (`_clean_reservations`), and garbage-collect zero quants. Concurrency is handled by row locks plus the merge pass.
- Quants cannot be created or edited directly except through "inventory mode" (the counting workflow, §4). Negative quants are possible (forced moves, bypass locations) and
  valuation explicitly handles negative on-hand.
- Inventory-count fields live on the quant itself: `inventory_quantity` (counted), `inventory_diff_quantity`, `inventory_date` (next scheduled count),
  `inventory_quantity_set`, `is_outdated` (stock moved after the count was keyed — conflict detection), `user_id` (assigned counter), `last_count_date`.

## 2. Locations, warehouses, putaway

- **`stock.location`** is hierarchical with a `usage` type: supplier, view, internal, customer, inventory (loss), production, transit. Non-internal usages bypass
  reservation — moves from them do not consume quants, which is how the double-entry loop tolerates the outside world.
- Locations carry the operational policy: fallback `removal_strategy_id`, `storage_category_id`, putaway rules, cyclic count frequency (`cyclic_inventory_frequency` →
  `next_inventory_date`), and a `replenish_location` flag.
- **Putaway**: `stock.putaway.rule` maps (product | category | package type) arriving at a location to a destination sublocation, with `sublocation` strategies
  none / last_used / closest, honoring **storage categories** (`stock.storage.category`): max weight, per-product and per-package-type quantity capacity, and an
  `allow_new_product` policy (empty / same-product-only / mixed). Capacity checks consider current + forecast quantity per candidate child location.
- **`stock.warehouse`** generates its own operational topology from two settings: `reception_steps` (1/2/3-step: input → QC → stock) and `delivery_steps`
  (ship / pick+ship / pick+pack+ship). It owns a picking type per step and auto-builds the connecting routes. Inter-warehouse resupply (`resupply_wh_ids`) creates routes
  through a company transit location.

## 3. Movements — `stock.move`, `stock.move.line`, `stock.picking`

- **`stock.move`** is the planned quantity change (demand). State machine: draft → (waiting | confirmed) → partially_available → assigned → done, plus cancel.
  `procure_method` distinguishes make-to-stock from make-to-order (chained to an upstream move via `move_orig_ids`/`move_dest_ids`); v19 adds MTSO (`mts_else_mto` — procure
  only the shortfall vs `free_qty`).
- **`stock.move.line`** is the executed detail (lot, source package, destination package, owner, done quantity). `_action_done` writes the quant deltas: release source
  reservation, decrement source, increment destination, propagating `in_date`.
- **`stock.picking`** groups moves into an operator-facing transfer document; its state is computed from its moves. **`stock.picking.type`** (incoming / outgoing /
  internal, extended by dropship and manufacturing) carries the behavior config: default source/destination locations, lot creation/selection policy
  (`use_create_lots` / `use_existing_lots`), reservation timing (`at_confirm` / `manual` / `by_date` with days-before settings), and backorder policy
  (`create_backorder` = ask / always / never).
- **Backorders**: validating a partially-done picking splits the shortfall into a linked backorder picking (`backorder_id`), or cancels the remainder, per policy.
- **Batch & wave picking** (`stock_picking_batch`): `stock.picking.batch` groups pickings for one operator pass; `is_wave` groups at line level.
- **Scrap** (`stock.scrap`): a small document (product, qty, lot, source location, scrap location, reason tags, optional auto-replenish) that posts a done move into an
  inventory-loss location, with an insufficient-quantity warning wizard.

## 4. Inventory adjustments & cycle counts

- Counting is done **on the quant**: enter `inventory_quantity`, apply (`_apply_inventory`) — which posts a done move between the location and the product's inventory-loss
  location sized to the difference. There is no separate adjustment document; the move ledger is the audit trail.
- **Conflict detection**: if physical stock moved after the count was keyed (`is_outdated`), apply diverts to a conflict wizard: keep counted quantity vs keep difference.
- **Scheduling**: per-location cyclic frequency or a company annual inventory date computes `inventory_date` per quant; a "request count" wizard assigns count dates and
  counters; applying a count restamps `last_inventory_date` and reschedules the next one.
- No approval workflow in core — applying is permission-gated (`group_stock_user`+) but immediate.

## 5. Reservation & removal strategies

- Reservation happens at `_action_assign`: `_gather` selects candidate quants (strict or child-of location matching; lot/package/owner constraints), ordered by the
  **removal strategy**: FIFO (`in_date ASC`), LIFO, FEFO (`removal_date, in_date` — added by `product_expiry`), closest (by location name), least_packages (A* search over
  package quantities to minimize opened packages). Strategy resolves product-category first, then location ancestry, default FIFO.
- `_get_reserve_quantity` returns quant-level allocations, down-rounds to the move UoM, enforces integer quantities for serials, nets out negative quants, and supports
  full-package-only reservation (`packaging_reserve_method = 'full'`).
- Partial reservation is native (`partially_available`); the scheduler re-attempts reservation ordered by `reservation_date, priority, date`.

## 6. Product quantity fields (forecasting inputs)

Computed per product, context-sensitive to location/warehouse/lot/owner/package and date:

- `qty_available` — Σ quant quantity in scope (supports back-dating by replaying done moves).
- `free_qty` — `qty_available − reserved`.
- `incoming_qty` / `outgoing_qty` — Σ of open (not-done) inbound/outbound move quantities.
- `virtual_available` — forecast = `qty_available + incoming − outgoing`.

These four drive the replenishment engine, the forecast report, and MTSO decisions.

## 7. Lot/serial tracking & expiry

- Product `tracking` = none / lot / serial. Serial forces integer quantities and one move line per unit. Picking types gate whether lots may be created at receipt or must
  be pre-existing.
- `stock.lot` is unique per (product, company); its on-hand is computed from quants; full downstream traceability (which deliveries a lot ended up in) is resolved through
  the move graph. A traceability report renders the upstream/downstream tree.
- **Expiry** (`product_expiry`): lots gain `expiration_date`, `use_date`, `removal_date`, `alert_date` (derived from product-level durations); FEFO removal orders by
  `removal_date`; expired quants are excluded from availability; a cron raises alerts on expiring lots.

## 8. Valuation & costing (`stock_account`) — redesigned in v19

- `cost_method` = standard / fifo / average (AVCO), resolved product category → company. `valuation` = periodic (manual) / real_time (perpetual). New in v19:
  per-lot valuation (`lot_valuated`).
- Value is carried on the move: `stock.move.value`, with `remaining_qty`/`remaining_value` forming the FIFO stack; `product.value` records the history of manual
  revaluations and standard-price changes. Incoming value resolution is a priority stack: manual value → invoice/bill → production → SO/PO → standard price.
- Outgoing moves are valued per cost method (FIFO consumes the remaining stack; AVCO uses standard price; lot-valuated uses per-lot price). `_create_account_move` posts
  interim/stock/COGS entries in real-time mode.
- **Landed costs** (`stock_landed_costs`): distribute freight/duty/etc. across received moves by quantity, weight, volume, equal, or current cost, adjusting move values and
  posting the journal entry.

## 9. Routing & replenishment

- **`stock.rule`** (action pull / push / pull_push) inside sequenced **`stock.route`s** selectable per product / category / warehouse / package type. Pull rules create the
  upstream move for a demand (MTS / MTO / MTSO); push rules chain or redirect a done move onward. Rule resolution walks location ancestry and route priority. This one
  engine expresses multi-step receipt/delivery, MTO, inter-warehouse resupply, and (with `purchase_stock`) buy-from-vendor.
- **Orderpoints** (`stock.warehouse.orderpoint`): min/max per product × location, `trigger` auto/manual, replenish-to-max via
  `qty_to_order = max − virtual_available(at lead-time horizon) − qty in progress`, rounded up to a replenishment multiple. Lead times combine rule delay + vendor delay +
  company horizon; `deadline_date` estimates the stock-out date. Manual orderpoints can be snoozed. The Replenishment report materializes suggested orderpoints for any
  product going negative in the forecast.
- **Buy route** (`purchase_stock`): `_run_buy` matches a vendor pricelist entry, creates or extends a draft PO, and schedules dates from vendor lead time. The scheduler
  (`run_scheduler`) runs orderpoints, then reserves confirmed moves by reservation date/priority, then quant housekeeping.

## 10. Consignment, dropshipping, packages

- **Consignment**: `owner_id` on quants/move lines keeps partner-owned stock segregated through reservation and counting (gated by a settings group).
- **Dropshipping** (`stock_dropshipping`): a dropship picking type (supplier → customer) driven by a route that buys directly to the customer; valuation treats dropship as
  its own valued type.
- **Packages**: v19 `stock.package` supports nested packages with a package-type (dims, max weight, routes); whole-package moves are detected and preserved; "packaging" as
  a purchasable multiple folded into UoM (`packaging_uom_id` on the move).

## 11. Reporting

- **Forecasted report** (per product): pairs incoming supply against outgoing demand and free stock — the availability planning view.
- **Stock quantity history** (`report.stock.quantity`): SQL view over moves/quants by date/state/warehouse powering the forecast graph; a point-in-time wizard renders
  historical on-hand via back-dating.
- **Traceability report**: upstream/downstream lot/move tree, PDF export.
- **Reception report**: allocate an incoming receipt against outstanding demand.
- **Routing report**: diagram of a product's routes/rules. No dedicated ABC-analysis or stock-aging report ships in core; 12-month move counts on the product give a
  lightweight turnover signal.

## Overall assessment

A mature, tightly unified design. Strengths: the double-entry quant/move core (every quantity change is one auditable primitive), reservation with pluggable removal
strategies, forecast quantities as first-class computed fields feeding one replenishment engine, count conflict detection, and valuation folded onto the same move records.
Trade-offs: the routing engine's generality costs comprehension (rule resolution walks locations × routes × sequences); counting has no approval workflow; everything is
synchronous in one database — there is no event contract, so none of it addresses cross-service consistency, which is pos-inventory's home turf.
