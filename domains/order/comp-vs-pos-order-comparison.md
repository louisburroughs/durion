# Odoo Point of Sale vs pos-order — Capability Comparison Map

> Purpose: a working checklist for comparing `durion-positivity-backend/pos-order` against Odoo 19's `addons/point_of_sale` (+ `pos_sale`, `pos_repair`, `pos_discount`,
> `pos_loyalty`, `pos_hr`, `pos_online_payment`). Odoo detail is in `comp-odoo-pos-overview.md`. pos-order references below come from a code survey of
> `pos-order/src/main/java/com/positivity/order/` as of 2026-07-23 (entities, `internal/service/`, `internal/controller/`, `internal/client/` ports).
>
> The two systems have different shapes on purpose. Odoo POS is a monolithic register: one module owns order, tender, tax, stock, receipt, and the cash drawer. Durion is a
> microservice platform for **mechanical repair shops** where most sales originate in `pos-workorder` (estimates → workorders), are choreographed by `pos-shop-manager`
> (appointments, bays, technicians), and money-out lives in `pos-invoice` (invoices, payments, receipts, refunds), with `pos-price` (pricing/promotions), `pos-tax`,
> `pos-catalog` (product master), `pos-inventory` (stock), and `pos-accounting` (GL) as sibling authorities. "Gap" below therefore means "Odoo has machinery pos-order doesn't"
> — the disposition (build in pos-order, wire to a sibling, or non-goal) is recorded in `spec-pos-order-missing-functionality.md`.
>
> Current pos-order in one line: a DRAFT-cart service (create/add/update/remove/link-source) plus a price-override approval workflow and a cancellation saga — with **no
> checkout path, no tax/total beyond subtotal, no payment/receipt/refund surface, and every cross-service port stubbed** (`DefaultPricingPortAdapter` returns $10.00 for every
> SKU; inventory always available; workexec/billing/source-document adapters return canned values).

## 1. Order lifecycle

| Concern | Odoo (`point_of_sale`) | pos-order | Notes for comparison |
| --- | --- | --- | --- |
| State machine | `draft` → `paid` → `done`, + `cancel`; `paid` set only after full-payment validation (`action_pos_order_paid`), `done` at session close or invoicing | `SalesOrderStatus` has 11 values, but only `DRAFT` and the cancellation states are ever assigned. `QUOTED`, `COMPLETED`, `VOIDED` are dead enum members — no checkout, quote, complete, or void endpoint exists | Biggest single gap: pos-order orders are born DRAFT and can only stay DRAFT or be cancelled. There is no path to a completed sale |
| Edit locking after sale | `write()` guard: paid/done orders cannot be edited or reverted; printed orders lock payments; deletion only for draft/cancel | No lock needed yet (nothing ever leaves DRAFT); no `@Version` optimistic lock on `SalesOrder` | When checkout lands, both the state guard and a concurrency token are needed |
| Cancellation | `action_pos_order_cancel`: draft-only, no orchestration (stock/invoice handled by their own reversals) | Stronger: persisted cancellation saga (`OrderCancellationServiceImpl`) — workexec cancel → payment reversal → `CANCELLED`, failure states (`CANCEL_FAILED_WORKEXEC/BILLING`), idempotency key, retry endpoint. `CANCEL_REQUIRES_MANUAL_REVIEW` declared but never set | Durion wins on orchestration semantics (DECISION-ORDER-001..003); Odoo has nothing comparable. But the saga's downstream ports are stubs today |
| Order identity | Server-issued `name`, `pos_reference` (receipt no., per-config sequence), `tracking_number` (customer-facing short no.), session `sequence_number` | UUIDv7 `orderId` only; no order number, no receipt number, no per-location sequence | Human-facing numbering is absent; matters for receipts, phone lookups, disputes |
| Offline/idempotent intake | Client uuid on order and line (unique constraints); `sync_from_ui` rewrites replayed CREATEs into UPDATEs; non-draft replays ignored | Idempotency only on cancellation (`cancellationIdempotencyKey`) and price overrides (`idempotencyKey` unique). Cart create/add-item have none — replaying a create makes a second cart | Odoo's uuid-identity pattern is the reference for POS clients on flaky shop Wi-Fi |
| Draft parking / scheduled orders | Floating orders (named drafts reloadable on any register of the config); presets with time slots, pickup/delivery, return mode | DRAFT carts persist indefinitely (implicit parking); no naming, no list-my-drafts endpoint, no scheduling (appointments live in pos-shop-manager) | Light gap: parking exists de facto; retrieval/naming UX is missing |
| Edit audit | `order_edit_tracking`: qty reductions / deleted lines flagged + chatter-logged | Every mutating endpoint emits `@EmitEvent` audit events (`ORDER_CART_ITEM_UPDATE`, …) to pos-event-receiver | Rough parity via platform audit rails; per-line "was edited" flags not needed |

## 2. Order lines

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Line fields | product, qty, `price_unit`, `discount` %, `price_subtotal`, `price_subtotal_incl`, tax ids, price origin (`price_type`), notes, uuid | `itemSku`, `itemDescription` (set = SKU on add — no catalog lookup), `quantity`, `unitPrice`, `fulfillmentStatus`, `priceSource` (PRICING_SERVICE/CACHE/MANUAL), source linkage (`sourceType/sourceId/sourceLineId`) | pos-order line has **no discount %, no tax, no extended total, no notes**. `priceSource` ≈ Odoo's `price_type` — good parity on price provenance |
| Authoritative totals | Server recomputes line + order totals with tax engine on every sync (`_compute_prices`); client totals distrusted | `recalculateSubtotal` = Σ(unitPrice × qty), scale 4 HALF_UP. That is the entire monetary model: `SalesOrder.subtotal` only — no tax amount, no grand total, no discount total | pos-tax exists and is used by pos-workorder/pos-invoice, but pos-order never calls it |
| Lots / serials | `pos.pack.operation.lot` per line; lots served from stock quants; serials 1/unit | Nothing | Relevant for parts with serial/warranty tracking (batteries, tires); pos-inventory owns stock but the sale line can't carry a lot today |
| Kits / combos | Combo parent/child lines, invoice section rendering | Nothing | Repair-shop analog: service packages/bundles (e.g. brake job = pads + rotors + labor); pos-catalog has substitution groups but no bundle-on-order concept |
| Notes | Order-level customer + internal notes; per-line customer note; predefined note library | Nothing (only `reasonCode` on manually priced lines) | Cheap, high-value for shop workflows |

## 3. Pricing & discounts

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Price resolution | Pricelists (per-config, per-partner), `_get_product_price`; tax-included fixups | `PricingPort.resolvePrice(sku)` — sole adapter is a stub returning **$10.00, stale=true** for every SKU. Real engine exists in pos-price (`PriceQuoteService`: base price + promotions + restrictions, customer-tier and location overrides, `PricingSnapshot`) but is not wired | The Durion design is arguably stronger (dedicated pricing service with snapshots); the wire is simply missing |
| Manual price entry | `price_type='manual'`; config gates price edits to managers | Implemented: `manualPrice` on add-item requires permission `order:line:enter_manual_price`, records `PriceSource.MANUAL` + reason code | Parity — pos-order is stricter (server-side permission vs Odoo's UI gate) |
| Line/order discounts | Per-line `discount` %; `pos_discount` global % as a product line | Only mechanism is `PriceOverride`: per-line price replacement with reason codes, $50/10% auto-approval thresholds, approval workflow, `ApprovalRecord` audit, idempotency | pos-order's override/approval flow is richer than Odoo's price control; but there is no percentage-discount or order-level discount concept, and `appliedAt` is never set |
| Promotions / coupons / loyalty | `pos_loyalty`: programs, points, promo codes, coupons, gift cards, eWallet; server-side validation + confirmation | Nothing wired. pos-price has `PromotionOffer`/eligibility rules; pos-customer has `PromotionRedemption` counters. Neither is consulted by pos-order | Platform pieces exist at both ends; the cart never applies or records them |
| Margin / cost visibility | Line + order margin, cost computed real-time or at close; visibility config | Nothing (pos-catalog holds supplier/item costs) | Optional analytics concern; decide explicitly |

## 4. Payments & tender

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Payment model | N `pos.payment` rows per order across methods; server-recomputed `amount_paid`; card metadata | pos-order has a single nullable `paymentId` UUID column and a `BillingPort.reversePayment` stub. All real payment machinery lives in pos-invoice (`PaymentIntent`, Stripe gateway, idempotent `PaymentService`) | The boundary decision (money in pos-invoice) is sound; what's missing is the **handshake**: pos-order never creates an invoice/payment-intent, never learns paid status, never validates paid-in-full |
| Method types | cash / bank / pay_later (customer account); split per-customer receivables | pos-invoice models payment intents (Stripe); cash/on-account tender semantics unclear at platform level | "Charge to account" is core for commercial repair customers (fleet accounts, billing terms exist in pos-customer `BillingRulesEmbeddable`) |
| Split/multi-tender, change | Multiple payments; change as negative cash payment (`is_change`); full-payment check with rounding tolerance | Nothing | Needed for counter sales (part cash, part card) |
| Tips | Tip product, `tip_amount`, tip-later adjustment | Nothing | Likely non-goal for repair shops — record explicitly |
| Cash rounding | `account.cash.rounding` strategies | Nothing | Non-goal for USD cents; record explicitly |
| Payment terminals | Provider addon pattern (Adyen/Stripe/…): terminal drives, card metadata lands on `pos.payment` | pos-invoice owns the Stripe gateway port | Keep in pos-invoice; pos-order only needs status |

## 5. Refunds & returns

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Refund flow | `refund()`: negative-qty copy into open session; per-line cap `qty − refunded_qty` (re-refund prevention); one source order per refund; fresh receipt numbers | Nothing in pos-order. pos-invoice has `StandaloneRefundController`, `PaymentReversalController`, `RefundRecord`; pos-inventory has `ReturnController` | No return-order concept links the original sale line → refunded qty → money reversal → stock return. Odoo's qty-cap linkage is the reference model |
| Refund accounting | `out_refund` move linked via `reversed_entry_id` | pos-invoice/pos-accounting territory | Order's job is the document + qty caps + orchestration, not the GL |
| Exchange / return mode | Preset `is_return` flips register to negative qty | Nothing | Follows from the return-order model |

## 6. Sessions & cash management

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Register session | `pos.session` lifecycle (`opening_control → opened → closing_control → closed`); one open session per register config | **No session/register concept anywhere in the Durion platform.** pos-order has `terminalId`/`clerkId` strings on the order — unvalidated, unaggregated | Whole capability absent: no drawer accountability, no shift boundary for reporting or reconciliation |
| Cash drawer control | Opening float from last close, counted vs theoretical close, over/short to loss/profit accounts, authorized-difference limit | Nothing | pos-accounting can absorb the GL side via events; the operational session/count model has no home |
| Cash in/out | Permission-gated paid-in/paid-out with reasons + audit | Nothing | Needed for shop reality (petty cash, parts runs) |
| Session close → accounting | One consolidated closing journal entry; deferred pickings; orders flipped to done | pos-accounting ingests events and has posting rules — but no session-close event exists to consume | Design decision needed: per-order postings (current Durion direction) vs Odoo's per-session consolidation |
| Rescue/recovery | Rescue sessions adopt orphan orders | n/a until sessions exist | |

## 7. Invoicing, receipts, documents

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Invoice generation | `to_invoice` flag → posted `account.move` + payment reconciliation; partner required; portal self-request via ticket code | pos-order has no invoicing hook. pos-invoice generates invoices **from workorder completion** (`InvoiceFinalizationService`) — the workorder path is covered; the counter-sale path (cart → invoice) is not | Cash counter sale (walk-in buys wiper blades) currently has no way to produce an invoice/receipt |
| Receipts | Receipt + tracking numbers, reprint counter, email receipt w/ attachments, header/footer, printer routing | pos-invoice owns `Receipt`/`ReceiptController` (generate/reprint/email) — keyed to invoices | Fine to keep receipts in pos-invoice; pos-order must trigger and reference them |
| Customer display / kitchen | Customer display, preparation display hooks | Out of scope for backend; shop analog (technician displays) is pos-shop-manager/pos-workorder territory | Non-goal for pos-order |

## 8. Inventory & fulfillment

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Availability check | Client-side from loaded stock; server validates at picking | `InventoryPort.checkAvailability` — stub always returns available (MAX_VALUE). Real service exists (`InventoryAvailabilityController`); line carries `FulfillmentStatus` AVAILABLE/BACKORDER per WARN_AND_BACKORDER policy | Wire, don't build |
| Stock movement on sale | Order → `stock.picking` real-time or at session close; returns → return picking; failed pickings tracked without blocking the sale | Nothing. pos-inventory has pick lists for **workorder** demand; a counter sale never decrements stock | Counter-sale fulfillment event (order completed → inventory movement) is missing end-to-end |
| Ship later | Procurement routes, shipping date, picking policy | Nothing | Mostly non-goal for a repair counter; special-order parts may need a variant |
| Serial/lot at handover | Lots created/matched at picking | Nothing | Pairs with §2 lots gap |

## 9. Customer, vehicle, staff

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Customer link | `partner_id` with existence validation at sync; required for invoice/pay-later/split/ship-later | `customerId`/`vehicleId` are opaque **Strings** — never validated against pos-customer; required only when linking a WORKORDER source | Needs typed IDs + validation + required-customer policy (invoice, on-account) |
| Vehicle | n/a (Odoo POS has no vehicle concept) | `vehicleId` on order — a Durion-native strength for repair shops | Keep; validate against CRM vehicles |
| Cashier / employee | `pos_hr`: employee per order, register login, per-employee reporting | `clerkId` (required String) + `terminalId` (required String) | Conceptual parity; no validation against pos-people, no per-clerk reporting |
| Loyalty accrual | Points/cards mutated at confirmation | pos-customer promotion counters exist, unwired | Pairs with §3 promotions gap |

## 10. Document settlement (the repair-shop core path)

| Concern | Odoo (`pos_sale` / `pos_repair`) | pos-order | Notes |
| --- | --- | --- | --- |
| Load external doc into register | `load_sale_order_from_pos` returns SO + lines; repair orders surface via their SO lines | `PATCH /v1/orders/carts/{id}/source` + `SourceDocumentPort.fetchLines` — **stub returns an empty list**. Merge/dedup logic (by sourceId/sourceLineId) is implemented and tested | Durion's intended flow matches Odoo's settlement pattern; the fetch is unimplemented. pos-workorder's `Estimate`/`WorkorderServiceLine`/`WorkorderPart` are the real sources |
| Finalize-back to source doc | POS finalize confirms the SO, updates delivered qty, adjusts pickings | Nothing (no finalize at all); pos-workorder independently invoices via pos-invoice on completion | Two competing quote-to-cash paths must be reconciled: workorder → invoice (live today) vs cart → checkout (intended). Who settles a workorder's balance at the counter? |
| Down payments / deposits | `down_payment_product_id`, down-payment lines, SO amount-unpaid tracking | Nothing anywhere: no deposit concept on estimates, workorders, orders, or invoices | Real repair-shop need (deposit before ordering special parts) |
| Double-stock protection | `pos_repair` excludes repair lines from POS stock moves — source doc owns its stock | n/a yet — but same hazard: workorder-sourced lines must not also drive order-side inventory movements | Encode the ownership rule when fulfillment lands |

## 11. Platform rails (Durion-only concerns, for completeness)

| Concern | Odoo | pos-order | Notes |
| --- | --- | --- | --- |
| Permissions | Groups/UI gating | `permissions.yaml` → pos-security-service; `@PreAuthorize` on cancel/override endpoints. Cart CRUD endpoints are `isAuthenticated()` only — no `order:cart:*` authorities | Tighten during checkout work |
| Eventing | Bus notifications to registers | `@EmitEvent` → pos-event-receiver on all mutations; **no Kafka producer/consumer, no outbox** in pos-order (siblings have `OutboxEvent`/`ProcessedEvent`) | Order-completed/cancelled domain events for invoice/inventory/accounting consumers need the outbox rails |
| API contract | n/a | OpenAPI + Angular SDK chain per `durion/CLAUDE.md`; cart/cancel endpoints still missing from `openapi.yaml` per BACKEND_CONTRACT_GUIDE (issues #19/#21) | Contract debt to clear alongside new endpoints |

## 12. Suggested comparison exercises

1. **Checkout walk-through**: take one counter sale (2 parts + 1 labor line) through Odoo `sync_from_ui → action_pos_order_paid → _generate_pos_order_invoice` and design the
   equivalent Durion sequence (pos-order checkout → pos-tax quote → pos-invoice invoice+payment → paid confirmation → COMPLETED). Every arrow that has no existing endpoint or
   event is a line item in the spec.
2. **Workorder settlement**: walk a completed workorder through Odoo's `pos_sale` settle flow and through the current Durion flow (workorder completion → InvoiceFinalization).
   Decide where counter tender fits and whether pos-order fronts it (recommended) or pos-invoice grows a UI-facing tender API.
3. **Refund cap model**: port Odoo's `refunded_qty` linkage (line-level cap, one-source-order rule, negative-qty document) into a Durion return-order design; verify
   pos-invoice `RefundRecord` and pos-inventory `ReturnController` can be driven from it.
4. **Price parity test**: same cart priced by Odoo pricelist+tax vs pos-price `PriceQuoteService` + pos-tax; confirm pos-order persists enough (unit price, discount, tax
   breakdown, snapshot id) to reprint an identical receipt a year later.
5. **Session close postings**: compare Odoo's consolidated session `account.move` against event-driven per-order postings into pos-accounting; decide whether a register
   session entity (new) should emit a close event with counted-cash over/short.
6. **Idempotent intake**: replay Odoo's uuid-rewrite trick (`_process_order` lines 118-124) against pos-order's cart endpoints; specify client-supplied line uuid or
   Idempotency-Key semantics for create/add/update.
