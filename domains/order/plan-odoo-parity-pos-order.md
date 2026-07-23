## Odoo Parity Plan — pos-order

> Status: ACCEPTED · Created 2026-07-23 · Branch: `claude/odoo-pos-comparison-nru5g0`
>
> Goal: bring `durion-positivity-backend/pos-order` to functional parity with the Odoo 19 `point_of_sale` capabilities that matter for a US, single-currency,
> mechanical-repair-shop POS — without breaking established Durion conventions or duplicating what sibling services own. Companion docs: `comp-odoo-pos-overview.md`,
> `comp-vs-pos-order-comparison.md`, and the authoritative gap spec `spec-pos-order-missing-functionality.md` (all §/G#/R#/Q#/V# references below point into that spec).
> Template precedent: `domains/accounting/plan-odoo-parity-pos-accounting.md`.
>
> Sources: gap spec (Q1–Q9 resolved 2026-07-23), code survey of pos-order and siblings (2026-07-23), order business rules (`domains/order/.business-rules/` —
> DECISION-ORDER-001..003, BACKEND_CONTRACT_GUIDE CAP-246), platform ADRs (`durion/docs/adr/`).

---

## 0. Ground rules for the executing agent team

Non-negotiable constraints; every story must be validated against them before merge.

1. **Boundary model (spec §0).** pos-order = counter sales, settlement front-end for estimates/workorders, and order-lifecycle authority. It performs **no** price/tax math
   beyond summation, holds **no** payment authority, generates **no** invoices/receipts, and moves **no** stock. Siblings own those (pos-price, pos-tax, pos-invoice,
   pos-inventory, pos-catalog, pos-customer, pos-workorder, pos-accounting); pos-order orchestrates and persists results. Cross-module stories below are explicitly labelled
   with the owning module and belong to that domain's backlog even though they're tracked in this plan.
2. **ADR-0013/0027**: UUID v7 IDs; UUID-typed identifiers in DTOs/services. **ADR-0024**: `createdAt`/`updatedAt` via auditing with injected `Clock` (pos-order already uses
   `TimeSource`/`Clock` — keep). **ADR-0018**: actor fields from `SecurityContextHelper`, never from request body. **ADR-0017/0042**: canonical status codes, `ApiError`
   envelope, full OpenAPI annotations; controller change ⇒ regenerate `openapi.yaml` ⇒ Angular SDK ⇒ refresh
   `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md` (spec R13.4 — this also clears the standing cart/cancel OpenAPI debt from issues #19/#21).
3. **Module conventions**: entities in `internal/entity`, services behind `service/` interfaces (ADR-0026, ArchUnit-enforced); `@EmitEvent` + `EventTypes` registry entry for
   every mutating endpoint; permissions added to `pos-order/src/main/resources/permissions.yaml` (pos-order is YAML-driven like pos-accounting — keep the pattern); Flyway
   migrations start at **V2** (only `V1__baseline_order_schema.sql` exists; story V-numbers below are indicative — take the next available at implementation time); Spotless +
   Checkstyle + SpotBugs green; ArchUnit after any package change; `./mvnw -pl pos-order -am test`.
4. **Event rails**: pos-order currently has no Kafka/outbox. Workstream D adopts the sibling `OutboxEvent`/`ProcessedEvent` pattern (transactional outbox, idempotent
   consumers) before any story that publishes or consumes domain events. Contracts live in `pos-domain-events` (`order.events.v1`; consumed: pos-invoice payment/settlement
   events). Ports/adapters (`internal/client`) remain the pattern for synchronous calls — replace the stub adapters, don't bypass them.
5. **Order-domain decisions already ratified — do not relitigate**: DECISION-ORDER-001..003 (cancellation orchestration, work-status blocking, settled-payment handling) and
   spec Q1–Q9 resolutions (see spec §15). Gates V1–V4 (spec §15 residual verifications) block only the stories that cite them.
6. **Scope guard (spec §14 non-goals, confirmed):** tips, cash-rounding strategies, multi-currency/localization/EDI receipts, ship-later procurement routes,
   kitchen/customer displays and kiosks, barcode/scale hardware, loyalty points/gift-card/eWallet engines (beyond promotion-redemption recording), Odoo-style consolidated
   session journal entry, cost/margin denormalized onto sale lines. Do not build these even where Odoo has them.

**Where Odoo is the reference vs where Durion already wins.** Adopt from Odoo: the draft→paid→done lifecycle discipline (edit locks after sale, full-payment gate before
completion), server-authoritative totals, uuid-identity idempotent intake, qty-capped non-re-refundable returns, session/cash-drawer accountability, the
`pos_sale`/`pos_repair` settlement pattern (source document owns work+stock; register owns tender), and the repair rule that source-doc lines never double-move stock. Keep
Durion's approach (do NOT import Odoo's): microservice ownership boundaries, the cancellation saga (richer than Odoo's draft-only cancel), price-override governance with
approval workflow (stronger than `restrict_price_control`), pricing snapshots, per-order GL postings with a session-variance event instead of one consolidated closing entry,
and pro-rata order discounts instead of Odoo's negative-discount-product line.

---

## 1. Gap → workstream map

| Spec gap | Workstream | Spec gap | Workstream |
| --- | --- | --- | --- |
| G1 checkout/lifecycle | A, C | G11 line model | B, H3 |
| G2 totals/tax | B | G12 customer/vehicle | I2 |
| G3 pricing stub | B1 | G13 promotions | B1, D2 |
| G4 payment handshake | C | G14 events/outbox | D |
| G5 returns | F | G15 deposits | E4 |
| G6 sessions/cash | G | G16 locking/guards | A1 |
| G7 source settlement | E | G17 parking | A2 |
| G8 numbering | A2 | G18 authorities | A4 |
| G9 counter-sale stock | H | G19 dead code | A4 |
| G10 idempotent intake | I1 | G20 non-goals | §0.6 |

---

## 2. Workstream A — Lifecycle & aggregate foundation

Odoo reference: `pos_order.py::write` edit guards, per-config sequences, floating orders.

### Story A1 — Centralized state machine, mutation guards, optimistic locking (G1 partial, G16)

- **Change**: single transition-guard service (allowed `from → to` map covering the full spec §2.1 machine incl. the existing cancellation states); all `setStatus` call
  sites route through it; every transition appends to a new `order_status_history` table and emits the platform audit event. Cart-mutation endpoints (add/update/remove
  item, link source) reject non-`DRAFT` orders with 409 `ORDER_NOT_EDITABLE`; price-override apply window per spec R2.5 (planning default: `DRAFT` only — widen later if the
  counter workflow demands it). Add `@Version` to `SalesOrder`; concurrent mutation vs transition → 409 `ORDER_CONFLICT`.
- **Files**: Flyway `V2` (version column + `order_status_history`); `SalesOrderServiceImpl`, `OrderCancellationServiceImpl` (route through guard, semantics unchanged).
- **AC**: transition-matrix unit test (every enum pair, allowed and rejected); two-thread IT (mutation vs checkout stub) — one serializes or 409s; cancellation saga behavior
  regression-green; history rows carry actor from `SecurityContextHelper` (ADR-0018).
- **Effort**: M. **Deps**: none. Spec: R2.5–R2.8.

### Story A2 — Order identity: numbering, location, draft parking (G8, G17)

- **Change**: `orderNumber` (`SO-{locationCode}-{yyMM}-{seq}`, per-location Postgres sequence rows locked at assignment, assigned at creation, unique; gapless NOT required —
  recorded decision, unlike accounting A2) and `locationId` (UUID, required at creation until G1 sessions supply it) on `SalesOrder`; optional `label` (parking name, Odoo
  `floating_order_name` analog); `GET /v1/orders/carts?clerkId=&terminalId=&status=&page=` list endpoint.
- **Files**: Flyway `V3`; `SalesOrderController` (+ OpenAPI/SDK chain).
- **AC**: concurrent creates at one location get distinct sequential numbers (IT); number immutable thereafter; parking list filters and pages; `orderNumber` on all read
  DTOs.
- **Effort**: M. **Deps**: none. Spec: R2.9–R2.12.

### Story A3 — QUOTED lifecycle (counter quotes only)

- **Change**: `POST /v1/orders/{orderId}/quote` (DRAFT→QUOTED: final reprice via B1/B3, persist snapshot refs, set `quoteExpiresAt` from config), reopen
  (QUOTED→DRAFT, reprices on reopen), and checkout accepts QUOTED (C1). Per resolved Q3: reject the quote transition on orders with a `WORKORDER` source link (422
  `QUOTE_NOT_ALLOWED_FOR_WORKORDER`) — pos-workorder Estimates own repair quoting; expired quotes reject confirm until reopened.
- **AC**: quote→reopen→quote cycle reprices (price change between cycles reflected); workorder-linked cart cannot quote; expiry enforced; permission `order:order:quote`.
- **Effort**: M. **Deps**: A1, B1, B3 (schedule Wave 2). Spec: R2.3.

### Story A4 — Authority & dead-code housekeeping (G18, G19)

- **Change**: (1) cart endpoints gain `order:order:create|view|edit` / `order:line:*` enforcement (authorities already exist in `permissions.yaml` — wire the
  `@PreAuthorize`s); (2) wire `CANCEL_REQUIRES_MANUAL_REVIEW`: terminal cancellation-retry failure and the DECISION-ORDER-003 settled-payment path land there with an alert
  event instead of being dead; (3) set `PriceOverride.appliedAt` when the override price lands on the line (spec R13.3; the commission event itself is D3).
- **AC**: 403 matrix test for cart endpoints; manual-review state reachable + alert emitted; `appliedAt` populated in override read DTOs.
- **Effort**: S. **Deps**: none. Spec: R13.1–R13.3.

---

## 3. Workstream B — Server-authoritative pricing, tax, totals

Odoo reference: `_compute_prices` / `_compute_amount_line_all` — server recomputes everything. Durion shape: orchestrate pos-price + pos-tax, persist results (spec §3).

### Story B1 — Real pricing adapter + catalog descriptions + promotion capture (G3, G13 partial)

- **Change**: replace `DefaultPricingPortAdapter` with a load-balanced RestClient adapter on pos-price's quote API — request `{sku, qty, customerId, locationId, at}`;
  persist per line: unit price, `pricingSnapshotId`, applied `promotionId` (nullable). Resolve `itemDescription` from pos-catalog at add-item and denormalize (receipt must
  not change when catalog does — today description = SKU). Staleness policy (spec R3.2): `PriceSource.CACHE` + `stale` retained; checkout blocks stale-priced lines unless
  the caller holds `order:line:enter_manual_price`-equivalent elevation (planning decision: reuse that permission, no new one).
- **AC**: stub deleted; IT against seeded pos-price data (base + tier + location override + promotion all reflected, snapshot id persisted); catalog-down fallback keeps
  add-item working with SKU-as-description + warning flag; stale-block test.
- **Effort**: M. **Deps**: none (pos-price API exists). Spec: R3.1–R3.3, R3.11(a).

### Story B2 — Monetary model: line/order totals, order-level discount, notes (G2 partial, G11 partial)

- **Change**: `SalesOrderLine` gains `discountPercent`, `discountAmount`, `lineSubtotal`, `taxAmount`, `lineTotal`, `customerNote`, `internalNote`; `SalesOrder` gains
  `discountTotal`, `taxTotal`, `grandTotal`, `taxStale`, `generalNote`, and `orderDiscount` (percent **or** amount + reason code, permission `order:order:discount`)
  allocated **pro-rata across lines** (spec R3.10 — not a negative synthetic line) with HALF_UP-per-line + largest-line residual correction (port the accounting E1 rounding
  approach). Line mutations recompute subtotal/discount totals locally and set `taxStale`; document the `subtotal` definition in OpenAPI (post-line-discount, pre-order-discount, pre-tax).
- **Files**: Flyway `V4`; recompute logic in `SalesOrderServiceImpl`.
- **AC**: rounding vectors — $100 order-discount 33.33% across 3 lines sums exactly; Σ`lineTotal` + rounding = `grandTotal`; notes round-trip; discount permission enforced;
  `PriceOverride` interplay regression (override then order-discount recomputes correctly).
- **Effort**: M. **Deps**: B1 (fields feed from real prices). Spec: R3.6–R3.8, R3.10, R10.1.

### Story B3 — Tax integration (G2 remainder)

- **Change**: new `TaxPort` + adapter on pos-tax `/v1/tax` — batch call at quote (A3) and checkout (C1), never per line-mutation; jurisdiction address resolved from the
  order's `locationId` (pos-location, mirroring the pos-workorder pattern); pass the customer's exemption-certificate reference when pos-customer carries one. Persist per
  line `taxAmount` + jurisdiction-breakdown reference, order `taxTotal`; clear `taxStale`.
- **AC**: checkout totals match pos-tax response cent-exact (IT with test-mode flat rates); exempt customer → zero tax with exemption reference persisted; tax-service-down
  at checkout → 503 `TAX_UNAVAILABLE` (checkout never completes with stale/absent tax).
- **Effort**: M. **Deps**: B2. Spec: R3.4–R3.5.

---

## 4. Workstream C — Checkout & payment settlement

Odoo reference: `action_pos_order_paid` full-payment gate; `pay_later` customer-account tender. Durion shape: pos-invoice owns money; pos-order orchestrates the handshake
(spec §4 flow diagram).

### Story C1 — Checkout endpoint (G1 core)

- **Change**: `POST /v1/orders/{orderId}/checkout` (`Idempotency-Key` required, spec R9.3): validates ≥1 line, customer policy (I2 matrix), availability re-check (H1),
  no stale prices (B1), then final B2/B3 recompute, line freeze, `DRAFT|QUOTED → PENDING_PAYMENT`, invoke `InvoicingPort.createInvoiceForOrder` (C2/C3) and return the
  priced order + invoice reference. Synchronous endpoint; settlement completion is async (C3). Failure of invoice creation rolls the status transition back (checkout is
  atomic from the client's view).
- **AC**: happy-path IT cart→checkout returns invoice ref and PENDING_PAYMENT; replayed key returns same result; each validation failure → distinct ApiError code;
  post-checkout mutation 409 (A1 guard); permission `order:order:checkout`.
- **Effort**: L. **Deps**: A1, B1–B3, H1, C2. Spec: R2.1.

### Story C2 — Counter-sale invoice creation endpoint (cross-module: **pos-invoice**)

- **Change** (per resolved Q1): new pos-invoice endpoint creating an invoice from an order payload `{orderId, workorderId?, customerId?, lines[incl. tax breakdown refs,
  deposits], totals}` — idempotent on `orderId` (replay returns existing); reuses low-level invoice-assembly primitives but does **not** generalize
  `InvoiceFinalizationService`; when `workorderId` present, dedupe against workorder-sourced invoices (returns the existing invoice for tender — E2 contract). Receipt
  generation keyed off this invoice via existing `ReceiptController` machinery.
- **AC**: order-invoice round trip; replay-safe; workorder dedupe returns same invoice; anonymous cash sale (no customerId) produces a receiptable invoice.
- **Effort**: M. **Deps**: none. Spec: R4.1, R7.2. Owner: billing/invoicing domain.

### Story C3 — Settlement handshake & completion (G4 core)

- **Change**: `InvoicingPort` adapter for C2 (extending the reversal-only `BillingPort`); new `order_payment_record` read-model table (paymentId, methodType
  CASH/CARD/ON_ACCOUNT/OTHER, amount, settledAt, reference — Flyway `V5`); consume pos-invoice payment settled/reversed events (`ProcessedEvent` dedup, D1 rails; **gate
  V1** — verify pos-invoice publishes them; if not, the V1 gap story in pos-invoice precedes this); maintain `amountPaid`/`balanceDue`; transition
  `PENDING_PAYMENT → COMPLETED` exactly when `balanceDue == 0` (spec R2.2, cent-exact, no rounding tolerance); `amountPaid > grandTotal` → integrity alert event, never
  silent. Migrate the cancellation saga's payment-reversal step to iterate payment records (spec R4.6), then drop `SalesOrder.paymentId`.
- **AC**: settle-in-full event flips order COMPLETED (IT with embedded Kafka); partial settlement stays PENDING_PAYMENT with correct `balanceDue`; replayed settlement event
  no-ops; reversal event reduces `amountPaid`; saga reverses all recorded payments.
- **Effort**: L. **Deps**: C1, C2, D1, gate V1. Spec: R4.2–R4.4, R4.6, R2.2.

### Story C4 — On-account tender (charge to customer account)

- **Change**: checkout tender-intent `ON_ACCOUNT` (Odoo `pay_later` analog): requires a **validated** commercial customer with billing terms (pos-customer
  `BillingRulesEmbeddable`; hard-block under `VALIDATION_PENDING` per resolved Q8) and permission `order:order:charge_on_account`; pos-invoice invoice carries AR terms;
  order COMPLETEs with `balanceDue` carried by the AR invoice — an accepted on-account invoice event counts as settlement (spec R4.5).
- **AC**: on-account checkout for validated fleet customer completes with AR invoice; anonymous or pending-validation customer → 403/422; permission enforced.
- **Effort**: M. **Deps**: C3, I2. Spec: R4.5.

### Story C5 — Void endpoint + saga alignment residue

- **Change**: `POST /v1/orders/{orderId}/void` — `PENDING_PAYMENT` only, rejected if any settled payment record exists (caller must use the cancellation saga), permission
  `order:order:void`; voids the pos-invoice invoice (C2 cancellation call); terminal `VOIDED`.
- **AC**: void before settlement → VOIDED + invoice cancelled; void after a settled payment → 409 pointing at cancel; void from DRAFT → 409 (drafts are deleted/cancelled,
  not voided).
- **Effort**: S. **Deps**: C3. Spec: R2.4.

---

## 5. Workstream D — Order domain events (outbox rails)

Spec §12; sibling pattern (`OutboxEvent`/`ProcessedEvent`) is the reference implementation — copy pos-invoice/pos-inventory's shape, don't invent.

### Story D1 — Outbox + consumer rails + `order.cancelled`

- **Change**: transactional outbox tables (Flyway `V6`) + publisher on `order.events.v1`; `ProcessedEvent` dedup for consumers; first producer wired: `order.cancelled`
  emitted at the cancellation saga's terminal `CANCELLED` (and `CANCEL_REQUIRES_MANUAL_REVIEW` alert from A4 rides the same rail).
- **AC**: same-tx enqueue proven (rollback discards event); publisher retries; consumer harness dedups replays; `order.cancelled` observed on topic in IT.
- **Effort**: M. **Deps**: none. Spec: R12.1 partial, R12.3.

### Story D2 — `OrderCompletedV1` contract + emission (+ `order.returned` schema)

- **Change**: `pos-domain-events` PR defining `OrderCompletedV1` per resolved Q9 — identifiers, line items (sku, qty, monetary totals, pricing/tax/promotion snapshot
  **references**, `sourceType`/`sourceLineId`, `returnable`, serials), tender summary, fulfillment metadata (fulfillable lines exclude WORKORDER-sourced per spec R7.5),
  `workorderId?`/`sessionId?`, customer/vehicle **by ID only**; plus the `OrderReturnedV1` schema (consumed later by F2 consumers). Emit on COMPLETED via D1. Consumers
  registered by their own stories (H2, E3, G3, pos-customer redemption).
- **AC**: schema review vs Q9 payload policy (no embedded PII); emission IT; promotion redemption fields sufficient for pos-customer to record `PromotionRedemption` without
  re-fetch.
- **Effort**: M. **Deps**: D1, C3. Spec: R12.1, R3.11(b), R7.5.

### Story D3 — Commission event (override impact)

- **Change**: contract + emission per resolved Q7: `affectsCommission` boolean, discount delta, order/line ids, approver identity, effective timestamp — emitted when an
  override is applied (replaces the `COMMISSION_EVENT_TODO` log line). Consumer named "commission domain"; **gate V3** resolves the owning module (likely pos-people) and
  registers the consumer — the contract does not hardcode it.
- **AC**: auto-approved and approved-after-pending overrides both emit exactly once; payload matches Q7 contract; no emission for rejected overrides.
- **Effort**: S. **Deps**: D1, gate V3 (consumer side only — emission ships regardless). Spec: R3.9.

---

## 6. Workstream E — Source-document settlement (estimates/workorders)

Odoo reference: `pos_sale`/`pos_repair` settlement. The repair-shop core path; merge/dedup plumbing already exists and is tested — the integrations don't (spec §7).

### Story E1 — Real SourceDocumentPort (estimate + workorder line fetch)

- **Change**: replace `DefaultSourceDocumentPortAdapter` with a RestClient adapter on pos-workorder: estimates (`Estimate`/`EstimateItem`) and workorders
  (`WorkorderServiceLine` labor + `WorkorderPart` parts). Imported lines arrive **priced-as-approved** — no reprice via pos-price (spec R7.1); new
  `PriceSource.SOURCE_DOCUMENT`; import the explicit `returnable` flag (resolved Q6 — pos-workorder must expose it on settled lines; small paired pos-workorder change);
  existing merge/dedup by `sourceId`/`sourceLineId` and customer-required-for-workorder rules unchanged.
- **AC**: linking a seeded workorder imports labor+parts at approved prices with `SOURCE_DOCUMENT` provenance; estimate import same; re-link dedups; `returnable` persisted
  per line.
- **Effort**: M. **Deps**: none. Spec: R7.1, R5.4 (flag import).

### Story E2 — Settlement-path reconciliation (workorder dedupe) — **gate V4**

- **Change**: implements the V4-ratified rule (pos-order fronts counter tender; pos-invoice is the single invoice authority keyed by workorderId): checkout of a
  workorder-linked order passes `workorderId` to C2, which returns the existing workorder invoice (with balance) instead of creating a duplicate; C3's settlement then
  completes the order against that invoice's remaining balance. Deposits previously taken (E4) appear as credits.
- **AC**: workorder already invoiced by completion flow → counter checkout tenders the existing invoice, no duplicate; workorder not yet invoiced → single invoice created
  carrying workorderId; end-to-end IT across pos-order + pos-invoice.
- **Effort**: M. **Deps**: C2, C3, E1, **gate V4** (Workorder Execution + Invoicing domain sign-off; V4 must be ratified before this story starts — everything else in this
  plan is independent of it). Spec: R7.2.

### Story E3 — Finalize-back to workorder (cross-module: **pos-workorder**)

- **Change**: pos-workorder consumes `OrderCompletedV1` where `workorderId` present → marks the workorder settled/closed per its own state machine (no synchronous write
  from pos-order); `ProcessedEvent` dedup.
- **AC**: completing a workorder-linked order transitions the workorder; replay-safe; non-workorder orders ignored.
- **Effort**: S. **Deps**: D2, E2. Spec: R7.3. Owner: workexec domain.

### Story E4 — Deposits / down payments — **gate V2** (cross-module with **pos-invoice**)

- **Change** (per resolved Q5): pos-order supports a `DEPOSIT` line type (no inventory, no source line, no tax? — tax treatment of deposits to be confirmed with the
  accounting/tax domain during implementation) tendered through normal checkout against an estimate/workorder reference; pos-invoice models the deposit as a dedicated
  credit-application artifact — traceable liability with source identity, remaining balance, application audit — applied when the final settlement invoice is created
  (`InvoiceAdjustment` reuse only if **gate V2** verification confirms provenance is preserved). Deposit rides `OrderCompletedV1` and the E2 settlement flow consumes prior
  deposits as credits.
- **AC**: deposit-take → later settlement shows deposit applied with full provenance chain; partial deposit application accounted; refundable-deposit path (workorder
  cancelled after deposit) routes through the cancellation saga to a pos-invoice refund.
- **Effort**: L (split pos-order/pos-invoice at implementation). **Deps**: C2, C3, E1, gate V2. Spec: R7.4.

---

## 7. Workstream F — Returns & refunds

Odoo reference: `refund()` negative-qty copy, `refunded_qty` caps, one-source-order rule (adopt all three invariants — spec §5).

### Story F1 — ReturnOrder aggregate + qty-cap invariants

- **Change**: `return_order` + `return_order_line` tables (Flyway `V7`) — exactly one `originalOrderId` (COMPLETED orders only), lines reference `originalOrderLineId`
  with `returnQty > 0`, reason code, condition (`RESTOCK|SCRAP|WARRANTY`), refund method (original tender / store credit / on-account credit). Transactional invariant: per
  original line, Σ returned qty across non-cancelled returns ≤ sold qty (row-lock the original line on return creation). Expose `returnableQty` per line on the order read
  API (respects the E1 `returnable` flag — workorder-consumed lines without it are not returnable here, per resolved Q6). Approval above threshold reuses the
  `ApprovalRecord` pattern; permissions `order:return:create|approve|view`; `Idempotency-Key` on creation.
- **AC**: over-cap return → 422 listing per-line `returnableQty`; two concurrent returns of the same remainder — one wins; second return of remainder allowed up to cap;
  WARRANTY-condition on a non-returnable workorder line → 422 routing to pos-warranty.
- **Effort**: L. **Deps**: A1, C3 (completed orders exist), E1 (flag). Spec: R5.1–R5.2, R5.4–R5.5.

### Story F2 — Return orchestration saga

- **Change**: persisted saga mirroring the cancellation pattern: `RETURN_REQUESTED → REFUND_ISSUED → STOCK_RETURNED → COMPLETED` with failure states + retry endpoint —
  (a) pos-invoice refund/credit (original-tender via payment reversal API; store credit / on-account credit paths per refund method), (b) pos-inventory return receipt
  (`ReturnController`) for `RESTOCK` lines only, (c) `OrderReturnedV1` emitted at completion (D2 schema). Store-credit refunds require a validated customer (I2).
- **AC**: full-flow IT (refund + restock + event); refund failure halts pre-stock with retry; SCRAP lines skip inventory; replay-safe on idempotency key.
- **Effort**: L. **Deps**: F1, C3, D1/D2. Spec: R5.3.

---

## 8. Workstream G — Register sessions & cash management

Odoo reference: `pos.session` + cash control. Placement ratified (Q4): sub-domain inside pos-order; per-order GL postings stay, session adds drawer variance + checkpoint
only (spec §6 — no Odoo-style consolidated closing entry).

### Story G1 — RegisterSession + CashMovement core

- **Change**: `register_session` + `cash_movement` tables (Flyway `V8`): session (terminalId, locationId, openedByClerkId, `OPEN → CLOSING → CLOSED`, openingFloat,
  counted/theoretical closing cash, overShort, timestamps) with **partial unique index: one OPEN per terminalId**; opening float defaults from the terminal's previous
  counted close. Cash movements `PAID_IN|PAID_OUT` with reason, permission `order:session:cash_movement` (Odoo `try_cash_in_out` analog). Endpoints: open, current-by-terminal,
  movement create/list. Order↔session binding: orders created while a session is open carry `sessionId`; `terminalId`/`clerkId` validated against the open session;
  session supplies `locationId` (A2's explicit parameter becomes the no-session fallback).
- **AC**: second open on same terminal → 409; float carry-forward proven across close/open cycle; movements permission-gated and audited; orders bind automatically.
- **Effort**: L. **Deps**: A2. Spec: R6.1–R6.2, R6.6.

### Story G2 — Session close, variance, X/Z reports

- **Change**: two-step close (begin-close with counted cash → confirm); theoretical cash = openingFloat + Σ session CASH payment records (C3 read-model) + Σ movements;
  over/short beyond config `authorizedDifferenceLimit` requires `order:session:approve_variance`; close blocked while session orders sit in `PENDING_PAYMENT` (drafts may
  park across sessions — recorded planning decision per spec R6.2); emit `RegisterSessionClosedV1` (totals by tender type, over/short, movements) via D1. X-report
  (mid-day) and Z-report (close summary) read endpoints.
- **AC**: counted≠theoretical beyond limit blocks without approval permission; close event on topic; X/Z figures reconcile with payment records in IT; PENDING_PAYMENT
  order blocks close.
- **Effort**: M. **Deps**: G1, C3, D1. Spec: R6.3–R6.5.

### Story G3 — Session-variance GL mapping (cross-module: **pos-accounting**)

- **Change**: pos-accounting consumes `RegisterSessionClosedV1` → posts over/short via a new posting category (`REGISTER_OVER_SHORT` → configured loss/profit accounts,
  seeded mapping keys), idempotent on sessionId, period-gated. No revenue re-posting (per-order postings remain authoritative — spec §14).
- **AC**: shortage posts Dr loss / Cr cash-clearing (exact accounts per accounting's mapping); replay-safe; zero-variance close posts nothing.
- **Effort**: S. **Deps**: G2. Spec: R6.4. Owner: accounting domain.

---

## 9. Workstream H — Inventory & fulfillment

### Story H1 — Real availability adapter

- **Change**: replace `DefaultInventoryPortAdapter` with a RestClient adapter on pos-inventory's availability API; WARN_AND_BACKORDER policy and `FulfillmentStatus`
  semantics unchanged (contract-guide behavior); checkout re-check is C1's call site.
- **AC**: stub deleted; low/zero stock yields `BACKORDER` flag with warning payload (not rejection); inventory-down degrades to warning (availability is advisory —
  checkout proceeds; stock truth reconciles via H2).
- **Effort**: S. **Deps**: none. Spec: R8.1.

### Story H2 — Counter-sale stock consumption (cross-module: **pos-inventory**)

- **Change**: pos-inventory consumes `OrderCompletedV1` → posts a counter-sale issue movement (its own movement-type design) for the event's fulfillable lines;
  consumption failure emits an alert event and never affects the completed sale (Odoo failed-pickings analog). The fulfillable set already excludes WORKORDER-sourced lines
  (D2 builds it per spec R7.5) — consumer asserts the rule defensively. Backordered lines emit a distinct fulfillment-pending signal (special-order pickup flow itself
  remains future work, spec R8.3).
- **AC**: counter sale decrements on-hand in pos-inventory ledger; workorder-settled order moves nothing; failure alert observed; replay-safe.
- **Effort**: M. **Deps**: D2. Spec: R8.2–R8.3, R7.5. Owner: inventory domain.

### Story H3 — Serial/lot capture

- **Change**: `serialNumbers[]` on order lines (element count ≤ qty; Flyway `V9` child table); required at checkout when the catalog product's `ProductTrackingLevel`
  demands it (pos-catalog lookup at checkout validation); serials ride `OrderCompletedV1` (warranty/returns traceability) and are copied onto F1 return lines.
- **AC**: tracked product without serials → 422 at checkout; serials in completed-event payload; return references original serials.
- **Effort**: M. **Deps**: B1 (catalog client), C1. Spec: R8.4, R10.2.

---

## 10. Workstream I — Intake hardening & customer integration

### Story I1 — Idempotent intake (G10)

- **Change**: `Idempotency-Key` on `POST /v1/orders/carts` (replay → 200 with original cart; payload mismatch → 409, mirroring `PriceOverrideServiceImpl` semantics);
  client-supplied `lineUuid` (unique per order, Flyway column) on add-item — replayed add with known uuid becomes an update (Odoo `_process_order` CREATE→UPDATE rewrite
  analog); update/remove idempotent by lineId. Checkout/return keys are C1/F1's scope.
- **AC**: replayed create returns identical cart id; double-tap add-item yields one line; mismatch 409; offline-resync simulation IT (same ops replayed in order → same
  final state).
- **Effort**: M. **Deps**: none. Spec: R9.1–R9.2.

### Story I2 — Customer & vehicle integration (G12)

- **Change**: `customerId`/`vehicleId` become UUIDs validated against pos-customer at assignment (existence check via RestClient); per resolved Q8: CRM-down ⇒
  `VALIDATION_PENDING` flag, cart work proceeds, but hard-block at any financially consequential transition requiring a validated customer — on-account tender (C4),
  business invoicing, store-credit return (F2), contractual workorder settlement. Required-customer matrix (spec R11.2): anonymous cash counter sales stay customer-free.
  Vehicle rides `OrderCompletedV1` for CRM service history (pos-customer consumer is a small paired story with their domain).
- **AC**: unknown customerId → 422; CRM-down assignment flags pending; pending customer blocks on-account checkout but not cash checkout; validation matrix IT.
- **Effort**: M. **Deps**: none (event part D2). Spec: R11.1–R11.3.

---

## 11. Sequencing (waves for the agent team)

Each story = one backend story issue (§13). Cross-module stories are tracked here but executed under their owning domain's conventions.

| Wave | Stories (parallelizable within wave) | Theme |
| ---- | ---- | ---- |
| 1 | A1, A2, A4, D1, H1, I1, I2 | Foundations: state machine, identity, outbox rails, real availability, idempotency, customer validation |
| 2 | B1, B2, B3, A3 | Money truth: real pricing, totals/discount model, tax, quotes |
| 3 | C2, C1, C3, D2, D3 | Checkout & settlement: invoice handshake, payment records, completion, completed/commission events |
| 4 | C4, C5, E1, E2, H2, H3 | Tender variants, void, workorder settlement, stock consumption, serials |
| 5 | G1, G2, G3, E3, E4 | Register sessions & cash, finalize-back, deposits |
| 6 | F1, F2 | Returns |

Gates (spec §15 residual verifications) with their blocking points: **V1** (pos-invoice settlement events exist?) before C3 · **V2** (`InvoiceAdjustment` provenance) before
E4 · **V3** (commission owner) before D3's consumer registration · **V4** (settlement-path sign-off from workexec + billing domains) before E2. Resolve V4 and V1 during
Waves 1–2 so Wave 3–4 are unblocked.

Definition of done per story: code + Flyway + `@EmitEvent`/EventTypes + permissions.yaml + OpenAPI regeneration + Angular SDK (where frontend-visible) + unit & contract IT +
ArchUnit green + `./mvnw -pl pos-order -am test` (or owning module) + Spotless + README/contract-guide update (`BACKEND_CONTRACT_GUIDE.md` per capability).

---

## 12. Decisions carried into this plan (from spec §15, resolved 2026-07-23)

| ID | Decision | Where applied |
| --- | --- | --- |
| Q1 | New counter-sale invoice endpoint in pos-invoice; no generalization of `InvoiceFinalizationService`; pos-invoice owns API shape | C2 |
| Q2 | Kafka settlement events are the settlement signal; callback only as temporary bridge if V1 finds a gap | C3, D1 |
| Q3 | `QUOTED` kept, counter-only; quote transition rejected on WORKORDER-sourced orders | A3 |
| Q4 | RegisterSession is a pos-order sub-domain; shop-manager consumes facts, never owns drawer semantics | G1–G3 |
| Q5 | Deposits are a dedicated credit-application concept with provenance; `InvoiceAdjustment` only if V2 verifies | E4 |
| Q6 | pos-order returns = counter-sale lines + explicitly-`returnable` settled lines; warranty returns → pos-warranty | E1, F1 |
| Q7 | Commission event: explicit `affectsCommission` + delta + ids + approver + timestamp; consumer = commission domain (V3) | D3 |
| Q8 | `VALIDATION_PENDING` tolerated in carts; hard-block at financially consequential transitions | I2, C4, F2 |
| Q9 | `OrderCompletedV1` rich but PII-minimal (customer/vehicle by ID); consumers never re-fetch for core processing | D2 |

---

## 13. Issue tracking (durion-positivity-backend)

Issues created 2026-07-23 in `durion-positivity-backend`; labels `domain:order` + `odoo-parity` (cross-module stories carry their owning domain's routing label instead:
C2 `domain:billing`, H2 `domain:inventory`, E3 `domain:workexec`, G3 `domain:accounting`).

| Wave | Story → Issue |
| ---- | ---- |
| 1 | A1 → #1059 · A2 → #1060 · A4 → #1061 · D1 → #1062 · H1 → #1063 · I1 → #1064 · I2 → #1065 |
| 2 | B1 → #1066 · B2 → #1067 · B3 → #1068 · A3 → #1069 |
| 3 | C2 → #1070 · C1 → #1071 · C3 → #1072 · D2 → #1073 · D3 → #1074 |
| 4 | C4 → #1075 · C5 → #1076 · E1 → #1077 · E2 → #1078 · H2 → #1079 · H3 → #1080 |
| 5 | G1 → #1081 · G2 → #1082 · G3 → #1083 · E3 → #1084 · E4 → #1085 |
| 6 | F1 → #1086 · F2 → #1087 |

Gate owners before their blocked stories start: V1 → #1072 (C3) · V2 → #1085 (E4) · V3 → #1074 (D3, consumer side) · V4 → #1078 (E2).
