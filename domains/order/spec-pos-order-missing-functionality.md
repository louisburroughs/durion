# Specification — Functionality Missing from pos-order (Odoo POS Parity, Repair-Shop Scope)

> Status: DRAFT for planning · Created 2026-07-23 · Branch: `claude/odoo-pos-comparison-nru5g0`
>
> Purpose: the definitive gap specification for `durion-positivity-backend/pos-order`, derived from the Odoo 19 Point of Sale comparison. This document is the input to a
> parity plan (companion to `plan-odoo-parity-pos-accounting.md` in the accounting domain). It specifies **what** is missing and **where it should live**; the plan will
> decide sequencing, stories, and effort.
>
> Sources: `comp-odoo-pos-overview.md`, `comp-vs-pos-order-comparison.md`, code survey of pos-order and siblings (2026-07-23), order business rules
> (`domains/order/.business-rules/` — DECISION-ORDER-001..003, BACKEND_CONTRACT_GUIDE CAP-246), platform ADRs.

---

## 0. Scope model — what pos-order is FOR

Positivity is a POS for **mechanical repair shops**. The dominant sales path is: appointment (pos-shop-manager) → estimate → workorder (pos-workorder) → invoice + payment +
receipt (pos-invoice). pos-order is NOT a duplicate of that path. Its mission, confirmed by this comparison, is:

1. **Counter sales** — over-the-counter parts/merchandise sales with no workorder (walk-in buys wiper blades). This is where pos-order must provide the full
   Odoo-register-equivalent flow: cart → priced+taxed totals → checkout → tender (via pos-invoice) → receipt → stock decrement → COMPLETED.
2. **Settlement front-end for source documents** — the Odoo `pos_sale`/`pos_repair` analog: pull estimate/workorder lines into an order, take the tender at the counter,
   and report settlement back. The workorder remains the authority on work and parts consumption; the order is the tender/checkout vehicle.
3. **Order lifecycle authority** — the state machine, cancellation orchestration (already built), price-override governance (already built), returns, and the
   order-completed contract that downstream services (invoice, inventory, accounting, CRM) consume.

Explicitly NOT pos-order's job (owned by siblings — gaps there are flagged `WIRE`, not `BUILD`):

| Responsibility | Owner |
| --- | --- |
| Price computation, promotions, restrictions | pos-price |
| Tax computation | pos-tax |
| Product master, descriptions, kits/bundles definitions | pos-catalog |
| Invoices, payments, receipts, payment refunds | pos-invoice |
| Stock movements, availability, physical returns | pos-inventory |
| GL postings | pos-accounting (event-driven) |
| Customers, vehicles, billing terms, promotion redemptions | pos-customer |
| Appointments, bays, technicians | pos-shop-manager |
| Estimates, workorders, labor, parts usage | pos-workorder |

Disposition legend: **BUILD** = new capability in pos-order · **WIRE** = capability exists in a sibling; pos-order must integrate · **DECIDE** = ownership/design decision
required before planning · **NON-GOAL** = deliberately not built; record and move on.

---

## 1. Gap register (evidence-based)

| # | Gap vs Odoo POS | Current state (evidence) | Disposition | Spec § |
| --- | --- | --- | --- | --- |
| G1 | No checkout/finalization — order can never become a sale | `QUOTED`/`COMPLETED`/`VOIDED` never assigned; no endpoint or service method sets them (`SalesOrderServiceImpl`) | BUILD | §2 |
| G2 | No tax, no grand total — `subtotal` is the only money field | `SalesOrder.subtotal`; `recalculateSubtotal` = Σ(unitPrice×qty); pos-tax never called | BUILD + WIRE | §3 |
| G3 | Pricing is a stub — every SKU costs $10.00 | `DefaultPricingPortAdapter` (canned $10, `stale=true`); pos-price `PriceQuoteService` unwired | WIRE | §3 |
| G4 | No payment handshake — single unused `paymentId` column | `SalesOrder.paymentId`; `DefaultBillingPortAdapter` stub; real machinery in pos-invoice | WIRE + BUILD | §4 |
| G5 | No returns/refunds model | Nothing in pos-order; pos-invoice `RefundRecord` and pos-inventory `ReturnController` exist but nothing links a return to original sale lines with qty caps | BUILD + WIRE | §5 |
| G6 | No register session / cash-drawer management anywhere in the platform | `terminalId`/`clerkId` opaque strings; no session entity in any module | BUILD, new sub-domain | §6 |
| G7 | Source-document settlement unimplemented | `DefaultSourceDocumentPortAdapter.fetchLines` returns empty list; no finalize-back to workorder; two unreconciled quote-to-cash paths | WIRE + DECIDE | §7 |
| G8 | No human-facing order/receipt numbering | UUIDv7 only | BUILD | §2.4 |
| G9 | No counter-sale inventory decrement | `DefaultInventoryPortAdapter` always "available"; no stock movement on completion | WIRE | §8 |
| G10 | No idempotent intake for cart create/mutation | Idempotency only on cancel + overrides; replayed cart-create duplicates the cart | BUILD | §9 |
| G11 | Line model too thin: no discount %, no tax fields, no notes, no lot/serial capture | `SalesOrderLine` fields (§2 of comparison doc) | BUILD | §3.3, §10 |
| G12 | Customer/vehicle are unvalidated strings; no required-customer policy | `SalesOrder.customerId`/`vehicleId` (String, nullable) | WIRE | §11 |
| G13 | Promotions/loyalty never applied or recorded on the cart | pos-price `PromotionOffer` + pos-customer `PromotionRedemption` unwired | WIRE | §3.4 |
| G14 | No order-domain events for downstream consumers (Kafka/outbox) | No `@KafkaListener`/`KafkaTemplate`/outbox in pos-order | BUILD | §12 |
| G15 | No deposits/down payments on orders sourced from estimates/workorders | Nothing platform-wide | BUILD | §7.4 |
| G16 | No optimistic locking, no completion edit-guard | No `@Version` on `SalesOrder`; guards only in cancellation service | BUILD | §2.3 |
| G17 | Draft parking exists implicitly but has no retrieval/naming surface | Only `GET /carts/{orderId}` by id | BUILD (small) | §2.5 |
| G18 | Cart CRUD endpoints lack fine-grained authorities | `SalesOrderController` = `isAuthenticated()` only | BUILD (small) | §13 |
| G19 | `CANCEL_REQUIRES_MANUAL_REVIEW` and override `appliedAt` are dead code | Enum member never set; `PriceOverride.appliedAt` never written | BUILD (small) | §13 |
| G20 | Tips, cash rounding, ship-later routes, kitchen/customer displays, scale/barcode hardware, self-order kiosks | n/a | NON-GOAL | §14 |

---

## 2. Order lifecycle completion (G1, G8, G16, G17)

### 2.1 Target state machine

Extend the implemented machine (DRAFT + cancellation states) with the missing sale path. Odoo reference: `draft → paid → done` with `action_pos_order_paid` full-payment
validation and post-paid edit locks.

```
DRAFT ──confirm──▶ PENDING_PAYMENT ──paid-in-full──▶ COMPLETED
  │                     │    ▲
  │                     └────┘ (partial payment recorded; stays PENDING_PAYMENT)
  ├──quote──▶ QUOTED ──confirm──▶ PENDING_PAYMENT
  └──cancel saga──▶ (existing CANCEL_* states) ──▶ CANCELLED
VOIDED: terminal, from PENDING_PAYMENT only, before any settled payment (manager permission)
```

Requirements:

- R2.1 `POST /v1/orders/{orderId}/checkout` (new): validates the cart (≥1 line, customer policy §11, availability re-check §8), triggers the final price/tax recompute (§3),
  freezes lines (no further mutation), transitions `DRAFT|QUOTED → PENDING_PAYMENT`, and initiates settlement (§4). Returns the priced order + settlement reference.
  `202`-style async is not required; checkout is synchronous, and settlement completion is async.
- R2.2 `COMPLETED` is set only by the payment-settlement confirmation (§4.3) when amount paid covers the grand total — the Durion analog of `action_pos_order_paid`'s
  `float_is_zero(total − amount_paid)` check. Tolerance: exact to the cent (no cash-rounding tolerance; see §14).
- R2.3 `QUOTED`: `POST /v1/orders/{orderId}/quote` converts a cart to a priced, held quote (persists the pricing snapshot reference, sets an expiry). A quote can be
  re-opened to DRAFT (repricing on reopen) or confirmed. Decide in planning whether QUOTED duplicates pos-workorder's Estimate enough to drop it — see Open Question Q3.
- R2.4 `VOIDED`: `POST /v1/orders/{orderId}/void` — abandonment of a checkout that never settled (payment failed/abandoned). Requires `order:order:void`. Distinct from the
  cancellation saga, which handles workorder/payment unwinding; void must be rejected if any settled payment exists (else caller must use cancel).
- R2.5 Mutation guards: all cart-mutation endpoints (add/update/remove item, link source, price override apply) return `409` unless status is `DRAFT` (overrides: `DRAFT` or
  `PENDING_PAYMENT`-before-settlement — decide in planning). Mirrors Odoo's `write()` guard.

### 2.2 State-transition enforcement

- R2.6 Centralize transitions in one guard (service-level map of allowed `from → to`), replacing scattered `setStatus` calls; every transition writes an order-status audit
  record (reuse the platform `@EmitEvent` rail + a `status_history` table for point-in-time queries). The cancellation saga keeps its existing persisted-saga semantics.

### 2.3 Concurrency & edit locking

- R2.7 Add `@Version` optimistic lock to `SalesOrder`. Concurrent line mutation vs checkout must fail cleanly (`409` with `ORDER_CONFLICT` code), not last-write-win.
- R2.8 After `COMPLETED`, the aggregate is immutable except: append return references (§5) and receipt reprint counters (owned by pos-invoice).

### 2.4 Order numbering

Odoo reference: per-config `ir.sequence` receipt numbers + short tracking numbers.

- R2.9 Server-issued `orderNumber` on the `SalesOrder`, assigned at **creation** (not lazily as Odoo does — simpler, and drafts are customer-visible in a shop): format
  `SO-{locationCode}-{yyMM}-{seq}` with a per-location monotonic sequence (Postgres sequence per location row; gapless NOT required — record that decision, unlike
  pos-accounting's journal numbering which may need gapless).
- R2.10 Requires a `locationId` on `SalesOrder` (currently absent; terminal→location resolution comes with §6 sessions, or is passed at creation until sessions exist).
- R2.11 Receipt numbers remain pos-invoice's concern; the order stores the invoice/receipt references it receives back (§4.3).

### 2.5 Draft parking / retrieval

- R2.12 `GET /v1/orders/carts?clerkId=&terminalId=&status=DRAFT` — list resumable carts (paged); optional `label` field on the order ("blue F-150 waiting on customer")
  matching Odoo's `floating_order_name`. Small, high-value for shop counters.

---

## 3. Server-authoritative pricing, tax, and totals (G2, G3, G11-pricing, G13)

Odoo reference: `_compute_prices` / `_compute_amount_line_all` — the server recomputes everything, always. Durion equivalent: pos-order orchestrates pos-price and pos-tax
and **persists the results**; it performs no price/tax math of its own beyond summation.

### 3.1 Real pricing integration (replaces stub)

- R3.1 Implement `PricingPort` against pos-price's quote API (load-balanced RestClient per platform convention). Request carries: SKU(s), quantity, customerId (tier
  pricing), locationId (location overrides), timestamp. Response carries: unit price, applied promotion/restriction identifiers, and the **`pricingSnapshotId`** — persist it
  per line for audit/reprint (Odoo has no equivalent; Durion's snapshot is stronger — keep it).
- R3.2 Cache/fallback policy: the existing `PriceSource.CACHE` + `stale` flag stay; define staleness rules (a checkout MUST NOT complete on stale prices without the
  `order:line:enter_manual_price`-equivalent elevation — decide exact permission in planning).
- R3.3 On add-item, resolve `itemDescription` from pos-catalog (today it's set = SKU); persist the description as sold (denormalized — the receipt must not change when the
  catalog does).

### 3.2 Tax integration (new)

- R3.4 At quote/checkout (not on every line mutation — batch, like Odoo recomputes on sync), call pos-tax `/v1/tax` with line items + the location's postal address
  (jurisdiction resolution follows the pos-workorder pattern: address from pos-location). Persist per line: taxable amount, tax amount, jurisdiction breakdown reference;
  per order: `taxTotal`.
- R3.5 Tax exemption: pass the customer's exemption certificate reference (pos-tax `ExemptionCertificate`) when present on the CRM account.

### 3.3 Monetary model on the aggregate

- R3.6 `SalesOrderLine` gains: `discountPercent` (nullable), `discountAmount`, `lineSubtotal` (extended, post-discount, pre-tax), `taxAmount`, `lineTotal`,
  `pricingSnapshotId`, `promotionId` (nullable). All BigDecimal(19,4), HALF_UP at cent on presentation.
- R3.7 `SalesOrder` gains: `discountTotal`, `taxTotal`, `grandTotal` (+ existing `subtotal` = Σ lineSubtotal pre-discount or post-discount — pick one definition and document
  it in the OpenAPI description; Odoo's `amount_total`/`amount_tax` split is the reference). `amountPaid` and `balanceDue` come from §4.
- R3.8 Recompute triggers: every line mutation recomputes subtotal/discount totals (cheap, local); tax recompute at quote/checkout and whenever a mutation follows a
  tax-bearing state (invalidate `taxTotal`, mark order `taxStale=true` until next recompute).

### 3.4 Discounts and promotions

- R3.9 Keep `PriceOverride` as the manual-discount governance mechanism (it exceeds Odoo's `restrict_price_control`). Fix: set `appliedAt` when the override price lands on
  the line; emit the commission event that is currently a `COMMISSION_EVENT_TODO` log line (target: pos-people/commission consumer via §12 events — confirm consumer in
  planning).
- R3.10 Order-level percentage/amount discount (Odoo `pos_discount` analog): implement as a first-class `orderDiscount` (percent or amount, reason code, permission
  `order:order:discount`), applied pro-rata across lines for tax correctness — NOT as a negative synthetic line (Odoo's discount-product trick is an artifact of its data
  model; a pro-rata field is cleaner and pos-tax needs per-line taxable amounts).
- R3.11 Promotions: pos-price's quote response already nets eligible promotions into the price (R3.1). pos-order must (a) persist the applied `promotionId` per line, and
  (b) on COMPLETED, emit the order-completed event carrying promotion usage so pos-customer can record `PromotionRedemption` (§12). No promotion evaluation logic in
  pos-order.
- R3.12 Loyalty points/gift cards/eWallet (Odoo `pos_loyalty` breadth): NON-GOAL for this phase beyond R3.11's redemption recording. Gift-card tender would be a pos-invoice
  payment method later. Record explicitly.

---

## 4. Payment settlement handshake with pos-invoice (G4)

Odoo folds tender into the order (`pos.payment` rows). Durion's boundary keeps money in pos-invoice — correct, but the handshake must exist. Target flow:

```
pos-order checkout (R2.1)
   └─▶ pos-invoice: create invoice for order {orderId, lines, totals, customerId}   (REST, idempotent on orderId)
         └─▶ pos-invoice: payment intents / tender capture (cash, card via Stripe, on-account)  [existing machinery]
               └─▶ payment settled events ──▶ pos-order records payment refs, updates amountPaid
                     └─▶ paid-in-full ⇒ order → COMPLETED (R2.2); receipt issued by pos-invoice
```

- R4.1 New `InvoicingPort` (replaces the reversal-only `BillingPort` or extends it): `createInvoiceForOrder(order)` → `{invoiceId}`, idempotent per orderId. pos-invoice
  needs a counter-sale invoice creation API (it currently invoices from workorder completion) — cross-module requirement to flag in the plan.
- R4.2 New `OrderPaymentRecord` entity in pos-order (read-model, not authority): paymentId, method type (CASH / CARD / ON_ACCOUNT / OTHER), amount, settledAt, reference.
  Populated from pos-invoice payment events (§12 consume) or synchronous confirmation callback. Replaces the single `SalesOrder.paymentId` column (keep the column during
  migration for the cancellation saga, then migrate the saga to the records).
- R4.3 `amountPaid` = Σ settled payment records; `balanceDue = grandTotal − amountPaid`. Transition to COMPLETED exactly when `balanceDue == 0` and status is
  `PENDING_PAYMENT` (R2.2). Overpayment: reject at pos-invoice (authority); pos-order treats `amountPaid > grandTotal` as an integrity alert event, never silently.
- R4.4 Split/multi-tender: naturally supported by N payment records; no change math in pos-order (cash drawer/change is a register concern — §6; pos-invoice records the
  net cash payment).
- R4.5 On-account ("pay later"/charge sales, Odoo `pay_later`): checkout with tender type ON_ACCOUNT requires a validated commercial customer with billing terms
  (pos-customer `BillingRulesEmbeddable`); order still completes (`COMPLETED`) with `balanceDue` carried by the AR invoice — the order's paid-check treats an accepted
  on-account invoice as settlement. Requires explicit permission `order:order:charge_on_account`.
- R4.6 Cancellation saga alignment: `BillingPort.reversePayment` gets a real adapter against pos-invoice's reversal API (`PaymentReversalController`), driven by the payment
  records from R4.2 (all settled payments, not one paymentId).

---

## 5. Returns & refunds (G5)

Odoo reference model (adopt its invariants): refund is a **new negative document linked line-by-line to the original**, capped at the un-refunded remainder, one source
order per return.

- R5.1 New `ReturnOrder` aggregate in pos-order (or a `SalesOrder` with `type=RETURN` and negative quantities — planning decision; separate aggregate recommended for
  cleaner authorization and reporting): fields — originalOrderId (required, exactly one), lines each referencing `originalOrderLineId`, `returnQty > 0`, reason code,
  condition (RESTOCK / SCRAP / WARRANTY), refund method (original tender / store credit / on-account credit).
- R5.2 Invariant (the `refunded_qty` cap): per original line, `Σ returned qty across non-cancelled returns ≤ sold qty`, enforced transactionally in pos-order (it owns the
  sale lines). Expose `returnableQty` per line on the order read API.
- R5.3 Orchestration on return completion: (a) pos-invoice refund/credit (`StandaloneRefundController` / credit memo path — confirm API in planning), (b) pos-inventory
  return receipt (`ReturnController`) for RESTOCK-condition lines, (c) events (§12). Follow the cancellation saga's persisted-state pattern for the multi-service flow
  (states: `RETURN_REQUESTED → REFUND_ISSUED → STOCK_RETURNED → COMPLETED`, with failure states and retry, mirroring DECISION-ORDER-003 semantics).
- R5.4 Workorder-sourced lines: returning a part consumed by a workorder is a workorder/warranty concern (pos-warranty exists) — pos-order returns apply only to
  counter-sale lines and workorder-settled lines explicitly flagged returnable. Coordinate with the Workorder Execution domain in planning.
- R5.5 Permissions: `order:return:create`, `order:return:approve` (approval above threshold, reusing the PriceOverride approval pattern and `ApprovalRecord`).

---

## 6. Register sessions & cash management (G6) — platform capability decision

Odoo: `pos.session` is the drawer-accountability and accounting boundary (opening float, counted close, over/short, cash in/out, one open session per register). Durion has
**nothing** — in any module. This is the largest wholly-missing capability and it is bigger than pos-order alone.

Recommendation (to ratify in planning): a `RegisterSession` sub-domain **inside pos-order** (not pos-shop-manager, which is scheduling-centric; not a new microservice yet —
per pre-production policy, avoid premature service proliferation). GL effects flow to pos-accounting via events, matching its posting-rules design.

- R6.1 Entities: `RegisterSession` (terminalId, locationId, openedByClerkId, status `OPEN → CLOSING → CLOSED`, openingFloat, countedClosingCash, theoreticalClosingCash,
  overShortAmount, openedAt/closedAt) and `CashMovement` (session, type `PAID_IN | PAID_OUT`, amount, reason, clerkId — permission-gated, Odoo `try_cash_in_out` analog).
- R6.2 Invariants: at most one OPEN session per terminalId (DB partial unique index); orders created while a session is open carry `sessionId`; closing blocked while the
  session has orders in `PENDING_PAYMENT` (Odoo blocks on drafts; PENDING_PAYMENT is our sharper guard — drafts may park across sessions, decide in planning); opening float
  defaults from the terminal's previous counted close.
- R6.3 Theoretical cash = openingFloat + Σ session cash-payment records (from §4.2 read-model) + Σ cash movements; over/short = counted − theoretical; an
  `authorizedDifferenceLimit` config requires manager approval (`order:session:approve_variance`) beyond it.
- R6.4 Session close emits a `register-session.closed` event (totals by tender type, over/short, movements) for pos-accounting to post (over/short → configured loss/profit
  mapping via its posting rules) and for reporting. Per-order revenue postings stay per-order (Durion direction); the session event adds only drawer variance and the
  reconciliation checkpoint — do NOT replicate Odoo's consolidated session journal entry.
- R6.5 Endpoints: open / close (two-step: begin-close with counted cash → confirm) / current-session-by-terminal / cash-movement create+list / X-report (mid-day totals) and
  Z-report (close summary) reads.
- R6.6 Until sessions ship, R2.10's `locationId` is passed explicitly at order creation; once shipped, session supplies terminal→location and clerk context, and
  `terminalId`/`clerkId` on orders are validated against the open session.

---

## 7. Source-document settlement — estimates & workorders (G7, G15)

The repair-shop core path; Odoo analog is `pos_sale`/`pos_repair` settlement. The merge/dedup plumbing in pos-order is already built and tested; the integration is not.

- R7.1 Implement `SourceDocumentPort` for real: fetch estimate lines (pos-workorder `Estimate`/`EstimateItem`) and workorder lines (`WorkorderServiceLine` labor +
  `WorkorderPart` parts) via REST. Lines arrive priced-as-approved (the estimate approval price is contractual — do NOT reprice via pos-price on import; record
  `priceSource=SOURCE_DOCUMENT`, a new enum value).
- R7.2 Settlement-state reconciliation (the two-paths decision, must be ratified before build): **pos-order fronts all counter settlement**. pos-invoice's
  workorder-completion invoicing remains, but when a workorder's balance is tendered at the counter, the flow is: link workorder → order (existing PATCH), import lines
  (R7.1), checkout (§2), invoice-for-order references the workorder (R4.1 carries `workorderId` so pos-invoice reconciles instead of double-invoicing). Odoo's
  double-invoice protection (`amount_unpaid` on the SO) maps to: pos-invoice is the single invoice authority keyed by workorderId — creating an order-invoice for an
  already-invoiced workorder returns the existing invoice for tender instead of a duplicate.
- R7.3 Finalize-back: on order COMPLETED with a workorder source, emit `order.completed` with workorderId + settlement summary; pos-workorder consumes it to mark the
  workorder settled/closed (its own state machine's concern). No synchronous write into pos-workorder.
- R7.4 Deposits/down payments (Odoo `down_payment_product_id` analog, DECIDE): support taking a deposit against an estimate/workorder before work: a `DEPOSIT`-type order
  line (no inventory, no source line) tendered normally; the deposit amount rides the `order.completed` event and the invoice, and pos-invoice applies it as a credit when
  the final settlement invoice is created. Requires pos-invoice cooperation — flag as a cross-module workstream in the plan.
- R7.5 Double-stock protection (the `pos_repair` rule): lines with `sourceType=WORKORDER` never trigger pos-order-side inventory movements (§8) — parts consumption was
  already recorded by pos-workorder/pos-inventory pick flows. Encode as a hard rule in the fulfillment dispatcher.

## 8. Counter-sale fulfillment & inventory (G9)

- R8.1 Replace `DefaultInventoryPortAdapter` with a real adapter on pos-inventory's availability API; keep the WARN_AND_BACKORDER policy and `FulfillmentStatus` flags
  (contract guide already specifies this). Re-check availability at checkout (R2.1).
- R8.2 On COMPLETED, emit `order.completed` including fulfillable lines (counter-sale lines only, per R7.5); pos-inventory consumes it to post the stock decrement
  (ledger movement, negative adjustment or a lightweight "counter sale issue" movement type — pos-inventory's design choice). Odoo's tracked-but-non-blocking failed
  pickings maps to: inventory consumption failures alert (event) but never roll back a completed sale.
- R8.3 Backordered lines: `FulfillmentStatus.BACKORDER` lines complete financially but emit a distinct fulfillment-pending event; special-order pickup flow is out of scope
  for this spec (record as future work; Odoo ship-later NON-GOAL §14).
- R8.4 Lot/serial capture (with §10): for serialized parts sold over the counter, capture lot/serial at checkout so warranty (pos-warranty) and returns (§5) can trace.

## 9. Idempotent intake (G10)

Odoo reference: client uuid identity with CREATE→UPDATE rewrite. Durion equivalents, required on the cart surface:

- R9.1 `POST /v1/orders/carts` accepts an `Idempotency-Key` header (platform pattern already used by pos-shop-manager appointments); replay returns the original cart
  (200, not 201), mismatched-payload replay returns 409 — same semantics as `PriceOverrideServiceImpl`'s idempotency contract.
- R9.2 Line mutations: client-supplied `lineUuid` (unique per order) on add-item; replayed add with a known lineUuid updates instead of duplicating (Odoo's rewrite trick).
  Update/remove are naturally idempotent by lineId.
- R9.3 Checkout (R2.1) and return creation (§5) require `Idempotency-Key` (money-adjacent operations).

## 10. Line-model enrichment (G11 remainder)

- R10.1 Notes: `customerNote` and `internalNote` per line; `generalNote` per order. Included in the invoice/receipt payload to pos-invoice.
- R10.2 Lot/serial: optional `serialNumbers[]` per line (validated count ≤ qty; required when the catalog product's `ProductTrackingLevel` demands it — pos-catalog lookup).
- R10.3 Kits/bundles: NON-GOAL in pos-order for this phase. If pos-catalog ships bundle definitions, explosion happens at add-item via catalog (one line per component,
  grouped by a `bundleGroupId` for receipt rendering). Record as future work; do not model combo parent/child lines now.
- R10.4 Line pricing/tax fields per R3.6.

## 11. Customer & vehicle integration (G12)

- R11.1 Convert `customerId`/`vehicleId` to UUID-typed references validated against pos-customer at assignment (existence check via REST; tolerate CRM downtime with a
  `VALIDATION_PENDING` flag rather than blocking cart work — decide strictness in planning).
- R11.2 Required-customer policy (Odoo: invoice/pay-later/ship-later force a partner): customer required for — on-account tender (R4.5), returns refunded to store credit,
  workorder-linked orders (already enforced), and any order the customer wants invoiced to a business account. Anonymous walk-in cash sales stay customer-optional.
- R11.3 Vehicle stays optional on counter sales; when present, ride it on `order.completed` so CRM service history (pos-customer `CrmVehicles`) reflects counter purchases.

## 12. Order domain events (G14)

pos-order currently has zero Kafka integration; siblings use outbox + `ProcessedEvent`. Adopt the same rails (per ADR-0044-style event walls used in the accounting plan):

- R12.1 Outbox-published events on `order.events.v1`: `order.completed` (the load-bearing contract: order header, lines with pricing/tax/promotion snapshot ids, tender
  summary, workorderId?, sessionId?, serials, customer/vehicle), `order.cancelled`, `order.returned`, `register-session.closed` (§6.4). Consumers: pos-invoice (already has
  its own flow — consumes for reconciliation), pos-inventory (R8.2), pos-accounting (posting rules), pos-customer (promotion redemption R3.11, vehicle history R11.3),
  pos-people/commission (R3.9).
- R12.2 Consumed events (with `ProcessedEvent` dedup): pos-invoice payment settled/reversed (R4.2), invoice finalized (R7.2 reconciliation).
- R12.3 Keep `@EmitEvent` audit emissions on all new endpoints with `EventTypes` registry entries (platform requirement).

## 13. Hardening & housekeeping (G18, G19)

- R13.1 Add `order:cart:create|update|view` authorities to cart endpoints (currently `isAuthenticated()` only) + `permissions.yaml` entries; add the new authorities from
  §§2–6 (`order:order:void`, `order:order:discount`, `order:order:charge_on_account`, `order:return:*`, `order:session:*`).
- R13.2 Wire `CANCEL_REQUIRES_MANUAL_REVIEW`: terminal failure of a cancellation retry (or a settled-payment cancel per DECISION-ORDER-003) lands here with an alert event,
  instead of the state being dead.
- R13.3 Set `PriceOverride.appliedAt` when the override lands on the line; emit the pending commission event (R3.9).
- R13.4 Contract chain: every new/changed controller updates OpenAPI annotations → regenerate `openapi.yaml` → Angular SDK → refresh
  `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md` (the guide's cart/cancel TODOs from issues #19/#21 are still open).

## 14. Explicit non-goals (record; do not build)

Confirmed against repair-shop scope and platform boundaries:

- **Tips** (Odoo tip product/tip-later) — not a repair-shop counter norm; revisit only on business request.
- **Cash rounding strategies** — USD cent precision suffices.
- **Multi-currency, fiscal positions, statutory receipt/EDI localizations** (`l10n_*_pos`) — US single-currency platform (matches the accounting plan's scope guard).
- **Ship-later / procurement routes** from the order — special-order parts flow through pos-inventory purchasing, not order-side routing.
- **Kitchen/preparation displays, customer-facing display, self-order kiosks, online-payment-at-counter QR** — channel/hardware concerns out of backend-order scope;
  technician-facing views are pos-workorder/pos-shop-manager territory.
- **Barcode nomenclatures, scale hardware** — pos-catalog owns identification data; register hardware is a frontend concern.
- **Loyalty points/gift cards/eWallet engines** — beyond promotion-redemption recording (R3.11).
- **Odoo-style consolidated session journal entry** — pos-accounting stays per-order + session-variance events (R6.4).
- **Margin/cost on the order** — cost analytics belong to reporting over pos-catalog cost data; do not denormalize cost onto sale lines now.

## 15. Open questions (resolve before or during planning)

| # | Question | Blocks | Response |
| --- | --- | --- | --- |
| Q1 | Counter-sale invoice API in pos-invoice: new endpoint, or generalize `InvoiceFinalizationService`? Who owns the story? | §4, §7.2 ||
| Q2 | Payment-settled signal: consume pos-invoice Kafka events (preferred, matches rails) or synchronous confirmation callback? Does pos-invoice publish settlement events today? | §4.2, R12.2 ||
| Q3 | Is `QUOTED` worth keeping given pos-workorder Estimates? (Counter-quote for parts without a vehicle/workorder is the remaining use case.) | §2.1 R2.3 ||
| Q4 | RegisterSession placement ratification (pos-order sub-domain vs pos-shop-manager vs new module) and whether shop-manager needs session awareness (clerk shift ↔ schedule). | §6 ||
| Q5 | Deposit application mechanics in pos-invoice (credit on final invoice) — does `InvoiceAdjustment` cover it? | §7.4 ||
| Q6 | Returns of workorder-consumed parts: pos-warranty vs pos-order return-order boundary. | §5.4 ||
| Q7 | Commission event consumer (pos-people?) and contract for `affectsCommission` overrides. | R3.9 ||
| Q8 | Strictness of customer validation under CRM outage (block vs `VALIDATION_PENDING`). | R11.1 ||
| Q9 | Does `order.completed` carrying full line detail violate any event-size/PII policy, or should consumers re-fetch by orderId? | §12 ||

---

## 16. Coverage check against Odoo capability map

Traceability: every Odoo POS capability from `comp-odoo-pos-overview.md` is either specified above (§ ref) or recorded as a non-goal (§14) — lifecycle §2, lines/pricing/tax
§3+§10, payments §4, refunds §5, sessions/cash §6, invoicing/receipts §4/§7, inventory §8, sync/idempotency §9, customer/loyalty §11/R3.11, settlement (`pos_sale`/
`pos_repair`) §7, staff (`pos_hr`) §6/§11, hardware/channels §14. The reverse is also true: nothing in this spec builds machinery a sibling service already owns — every
sibling-owned capability is a WIRE with the sibling named.
