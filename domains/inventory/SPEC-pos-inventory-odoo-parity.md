# SPEC — pos-inventory Missing Functionality (Odoo Inventory Parity)

> Status: ACCEPTED · Created 2026-07-22 · Open questions resolved 2026-07-22 (§12 "Response" column; rulings in the plan §1) · Branch: `claude/odoo-pos-inventory-comparison-ywp0ev`
>
> Purpose: a detailed **specification of functionality missing from `durion-positivity-backend/pos-inventory`**, derived from the comparison against Odoo 19's inventory
> stack. This document defines WHAT to build and the constraints on HOW; the implementation plan (stories, waves, issue map) is
> **`plan-odoo-parity-pos-inventory.md`**, in the style of `../accounting/plan-odoo-parity-pos-accounting.md`.
>
> Sources: `comp-odoo-inventory-overview.md`, `comp-vs-pos-inventory-comparison.md`, code survey of `pos-inventory` (2026-07-22), `pos-inventory/docs/inventory-ledger-atp.md`,
> `pos-inventory/docs/putaway-validation-rules.md`, inventory business rules (`.business-rules/`), capability index (CAP-215…CAP-221, CAP-315), platform ADRs
> (`durion/docs/adr/`).
>
> Scope reminder: pos-inventory serves a workorder-centric POS platform (parts inventory for service shops), not a general warehouse. "Odoo has it" is evidence, not
> justification — every requirement below is included because it solves a POS-context problem, and Odoo is cited as the reference mechanic.

---

## 0. Constraints on any implementation (non-negotiable)

1. **ADR-0044 (event-only domain walls).** pos-inventory integrates via Kafka events and local `ext_*` replicas — no new synchronous RestClients to other domain services.
   New outbound facts extend the existing outbox (`event_outbox` → `inventory.events.v1`) and manifest reconciliation; new inbound data arrives on `{domain}.events.v1`
   topics with `processed_events` idempotency and `aggregateVersion` stale guards. These rails exist — reuse them.
2. **The ledger stays append-only and authoritative.** `inventory_ledger_entry` remains the source of truth for quantity state (DECISION-INVENTORY-005: immutable,
   corrections are new entries). Any stored balance introduced by this spec is a derived read model rebuildable from the ledger, never independently writable.
3. **Module conventions**: UUID v7 IDs (`@UUIDv7Id`), entities in `internal/entity`, public interfaces in `service/` (ArchUnit-enforced), `@EmitEvent` + `EventTypes`
   registry entry per mutating endpoint, permissions in `permissions.yaml`/`InventoryPermissionRegistry` following `inventory:<resource>:<action>`
   (PERMISSION_TAXONOMY.md), `ApiError` envelope, OpenAPI regeneration + SDK update on any controller change, Flyway migrations appended after V8 (V1–V8 are
   post-baseline-reset; archives untouched), Spotless/Checkstyle/SpotBugs/ArchUnit green.
4. **Location semantics**: two-level model per DECISION-INVENTORY-001 (`locationId` site, `storageLocationId` bin); movement eligibility per DECISION-INVENTORY-009
   (INACTIVE/PENDING sites blocked); location truth stays in pos-location, replicated via events.
5. **Approval-first governance is retained.** Where Odoo applies changes immediately (counts, adjustments), Durion's approval-tier model
   (`ApprovalThresholdConfig`, two-tier variance approval) is the standard to extend, not replace.
6. **Sensitive data**: quantities and availability remain sensitive-by-default (DECISION-INVENTORY-011) — no new endpoint may leak quantities without an
   `inventory:on_hand:*` (or stronger) permission.

**Where Odoo is the reference vs where Durion already wins.** Adopt from Odoo: forecast-quantity semantics (`incoming`/`projected available`), transfer-with-transit
lifecycle, scrap-as-document, count conflict detection, recurring count scheduling, removal-strategy ordering, orderpoint math (lead-time horizon, replenish-to-max,
order multiples), move-carried valuation. Keep Durion's approach (do NOT import Odoo's): event-driven replicas and outbox instead of shared DB, per-flow documents instead
of a generic picking/route engine, approval-gated adjustments, allocation reallocation with priority aging, GRNI accrual + encumbrance, vendor feeds
(`NormalizedAvailability`/`DistributorNormalizedInventory`) as the sourcing data backbone.

---

## 1. Gap register (evidence-based)

"Current state" cites pos-inventory code as of 2026-07-22. Severity: **H** = blocks a real POS operational scenario today; **M** = operational friction / analytics or
governance hole; **L** = hardening.

| #   | Gap vs Odoo                                                                 | Current state (evidence)                                                                                                                                             | Sev | Workstream |
| --- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --- | ---------- |
| G1  | No forecast quantities (incoming/outgoing/projected available)              | ATP = on-hand − allocations only; expected receipts explicitly out of scope v1 (`docs/inventory-ledger-atp.md`); open PO (`openQuantityDecimal`) and ASN data unused for availability | H   | A          |
| G2  | On-hand is an unbounded ledger aggregation                                   | Every read = `SUM(changeInQuantity)` (`InventoryLedgerEntryRepository` COALESCE/SUM queries, 1000-id chunking); no stored balance; P95 <200ms SLA at risk as ledger grows | M   | A          |
| G3  | No point-in-time (as-of) on-hand query                                       | Availability endpoints have no date parameter; ledger replay would support it trivially                                                                               | L   | A          |
| G4  | No UoM conversion                                                            | v1 rule: product base UoM only, no conversion (`docs/inventory-ledger-atp.md`); `unitOfMeasure` free-text on ledger rows, PO/ASN/receipt lines                        | H   | B          |
| G5  | Site-to-site transfer has no lifecycle or in-transit state                   | `POST /stock-movements` TRANSFER posts paired TRANSFER_OUT/TRANSFER_IN in one transaction (`StockMovementServiceImpl`); `InventorySourceType.TRANSIT` enum unused; nothing to dispatch/receive/short-close | H   | C          |
| G6  | No scrap/write-off workflow                                                  | `SCRAP_OUT` ledger event type defined but unreachable — no document, endpoint, service, or reason taxonomy; only generic adjustments available                        | M   | D          |
| G7  | No lot tracking beyond receipt capture                                       | `lotNumber` on `AsnLineEntity`/`GoodsReceiptLineEntity` only; never enters ledger, putaway, picking, consumption, or returns; no lot master                           | M   | E          |
| G8  | No serial tracking in stock                                                  | Serial numbers only on `WarrantyPartReturnHold` (warranty-event-fed); no serialized on-hand or issue                                                                  | M   | E          |
| G9  | No expiry / FEFO                                                             | No expiration dates anywhere; `ReplenishmentSourcingReason.FEFO` enum label with no engine                                                                            | M   | E          |
| G10 | Replenishment engine unfinished                                              | `ReplenishmentServiceImpl.runBatchReplenishmentScan` stubbed TODO (CAP-217); min/max only, on-hand-only math; no lead-time/forecast awareness, no MOQ/pack rounding, no snooze | H   | F          |
| G11 | No reorder→purchase suggestion path                                          | Replenishment produces internal transfer tasks only; vendor feed data (lead time, MOQ, pack size in `NormalizedAvailability`/`DistributorNormalizedInventory`) unused for sourcing | H   | F          |
| G12 | Shortage resolution is scaffolding                                           | `ShortageResolutionServiceImpl` returns static options + stub result; `ProductSubstituteClient`/`ExternalAvailabilityClient` injected, unwired; `BACKORDER_CREATED/RESOLVED` ledger types never written | H   | G          |
| G13 | No removal/sourcing strategy engine                                          | Consumption closes allocations oldest-first (hardcoded); no FIFO/FEFO/proximity policy for pick-location or replenishment-source selection despite enum labels        | M   | H          |
| G14 | No recurring cycle-count scheduling                                          | `CycleCountPlan.scheduledDate` is a one-shot; no per-location frequency, no auto-generation of next plan, no "due for count" surfacing                                | M   | I          |
| G15 | No count conflict detection                                                  | `CycleCountTask.expectedQuantity` snapshotted at task creation; movements between task creation and count submission silently absorbed into variance (Odoo `is_outdated` analog missing) | M   | I          |
| G16 | No inventory valuation / costing method / COGS                               | `unitCost` snapshots on ledger entries and `costAtTimeOfAdjustment` only; no AVCO/FIFO/standard method, no on-hand value, consumption events carry no cost            | H   | J (decision-first) |
| G17 | No landed costs                                                              | Nothing distributes freight/duty into item cost                                                                                                                       | L   | J          |
| G18 | Negative-stock policy implicit                                               | PICK/ISSUE guarded (`InsufficientStockException`); adjustment/receiving/transfer paths' behavior at or below zero undocumented and untested per event type            | L   | K          |
| G19 | Putaway lacks sublocation strategies and category/attribute matching         | `PutawayRule` = priority + criteria JSON → fixed destination; no last-used/closest-fill strategies; capacity via `maxUnitCapacity` + validation service (adequate)    | L   | K          |
| G20 | Receiving shortfall creates variance but no follow-up demand                 | RECEIVED_SHORT + `InventoryVariance` recorded; PO stays PARTIALLY_RECEIVED (acceptable) but nothing surfaces "still owed" as expected supply — folds into G1/G12      | M   | A/G        |

Resolved as explicit non-goals (record; do not build): see §11.

---

## 2. Workstream A — Availability read model, forecast quantities, as-of queries

**Odoo reference**: `qty_available` / `free_qty` / `incoming_qty` / `outgoing_qty` / `virtual_available` (`addons/stock/models/product.py`, `_compute_quantities_dict`);
back-dated on-hand via done-move replay. **Gaps**: G1, G2, G3, G20.

### A1. Stored on-hand snapshot (performance enabler)

- **Requirement**: introduce a derived balance table (working name `inventory_stock_summary`): one row per (stockItemId/SKU, locationId) — extended per lot in WS-E —
  holding `onHand`, `allocated`, `atp`, `lastLedgerEntryId`, `lastEventAt`, maintained transactionally with each ledger append (same-transaction upsert, not async).
- The ledger remains truth: a rebuild job must reconstruct the summary from the ledger from scratch, and a scheduled verifier compares summary vs `SUM(ledger)` per
  location (report-only drift metric, alert on mismatch). This mirrors the Odoo `_clean_reservations`/merge GC role but with the ledger as arbiter.
- All availability/rollup/inquiry reads (`InventoryAvailabilityService`, rollup services, `SiteInventoryQuantityLoader`) switch to the summary; existing endpoint contracts
  and response shapes are unchanged.
- **Acceptance shape**: availability P95 <200ms with ≥5M ledger rows; rebuild produces byte-identical summaries; concurrent movement test shows no lost updates
  (row-lock or optimistic-retry on the summary upsert).

### A2. Forecast quantities

- **Requirement**: extend the availability read model with, per SKU × site (bin-level not required v1):
  - `incomingQty` = Σ open supply: approved-PO line `openQuantityDecimal` + ASN lines not yet received (state LOADED/READY_FOR_RECEIPT/PARTIALLY_RECEIVED remainder)
    + inbound transfer orders in transit (WS-C).
  - `outgoingQty` = Σ open demand not yet decremented from on-hand: PENDING/PARTIALLY_FULFILLED reservation remainders + released-not-picked pick-task remainders +
    outbound transfer orders not yet dispatched.
  - `projectedAvailable` = `onHand + incomingQty − outgoingQty` (Odoo `virtual_available`).
  - Optional date-bounded variant: `projectedAvailable(asOfDate)` using PO `expectedDeliveryDate` / ASN `expectedArrivalDate` / reservation `dueDateTime` cutoffs.
- Surfaced on: availability endpoints (additive response fields), a new `InventoryAvailabilityUpdatedV1` fact field (additive, schema-versioned), and consumed by WS-F
  replenishment math.
- **Explicitly reverses** the v1 ATP scope decision in `docs/inventory-ledger-atp.md` — that doc must be updated in the same change; ATP definition itself
  (on-hand − allocations) does not change.

### A3. As-of on-hand

- **Requirement**: `GET /v1/inventory/availability/{productId}?asOf=<instant>` (and location-inquiry equivalent) computing historical on-hand by ledger aggregation with
  `timestamp <= asOf`. Read-only, `inventory:ledger:view` (it exposes history, not just current state). No summary involvement — direct ledger math is acceptable for an
  audit-frequency query.

### A4. Open-supply visibility for shorted receipts (G20)

- Received-short quantities remain in `incomingQty` as long as the PO line stays open; when a PO is CLOSED/CANCELLED with open quantity, emit a fact
  (`ExpectedSupplyDroppedV1`) so shortage tooling (WS-G) and projections react. No new document needed.

**Dependencies**: none (foundation). **Odoo mechanics to port as tests**: date-bounded forecast cutoffs; incoming/outgoing sign conventions.

---

## 3. Workstream B — Unit-of-measure handling

**Odoo reference**: moves in any UoM, quants in product reference UoM, conversion with down-rounding on reservation (`_get_reserve_quantity`,
`_prepare_move_line_vals`). **Gap**: G4.

- **B1. Canonical stocking UoM.** Every stock quantity in the ledger and summary is in the product's base UoM (already true by convention — make it enforced). Product
  base UoM and purchase-UoM conversion factors come from pos-catalog via events into an `ext_product_uom` replica (product events already flow platform-wide; extend the
  consumed payload rather than adding a client, per ADR-0044).
- **B2. Conversion at the document boundary.** PO lines, ASN lines, receiving lines, and return lines may carry a document UoM + quantity; the service converts to base
  UoM at ledger-posting time using the replica factors. Conversion metadata (`documentUom`, `documentQuantity`, `conversionFactor`) is stored on the posting document line
  for audit; the ledger row remains base-UoM only.
- **B3. Rounding policy**: conversions round HALF_UP at the base UoM's precision scale, except reservations/allocations which round DOWN (never promise more than exists —
  Odoo's rule, adopted verbatim). Precision scale per UoM comes from the catalog replica.
- **B4. Validation**: posting with a UoM that has no conversion path to base → 422 deterministic error (`UOM_CONVERSION_UNDEFINED`), never a silent 1:1 assumption.
  Existing free-text `unitOfMeasure` ledger column is retained but validated against the replica going forward.
- **Acceptance shape**: receive a PO line of `1 CASE(12 EA)` → ledger `+12 EA`; over-receipt guard operates in base UoM; availability displays base UoM; a product with no
  conversion factor rejects non-base documents.

**Dependencies**: catalog product-event payload extension (cross-domain contract addition — flag in plan). **Open question OQ-1** (§12).

---

## 4. Workstream C — Transfer orders and in-transit stock

**Odoo reference**: internal picking lifecycle + transit locations for inter-warehouse resupply (`stock.warehouse.resupply_wh_ids`, transit `usage`). **Gap**: G5.

- **C1. TransferOrder document.** New aggregate `TransferOrder` + `TransferOrderLine`: sourceLocationId/storageLocationId, destinationLocationId, lines
  (SKU, requestedQty, dispatchedQty, receivedQty), status machine `DRAFT → APPROVED(optional, config) → DISPATCHED → PARTIALLY_RECEIVED → RECEIVED | SHORT_CLOSED |
  CANCELLED`. Cancellation allowed only before DISPATCH; after dispatch, discrepancies resolve via short-close (C4).
- **C2. In-transit representation.** Dispatch posts `TRANSFER_OUT` at source **into a transit scope**: ledger rows carry the existing from/to columns plus the transfer
  order as `sourceTransactionId`; in-transit quantity per SKU = Σ dispatched − Σ received per transfer order (derivable; also maintained on the summary as `inTransitQty`).
  Receipt at destination posts `TRANSFER_IN`. On-hand at source decreases at dispatch; at destination increases at receipt; global on-hand across
  source+transit+destination is conserved at every step (the invariant to test).
- **C3. Partial receipt**: destination receives per line; remainder stays in transit until received or short-closed.
- **C4. Short-close with loss disposition**: closing a transfer with undelivered remainder requires a disposition — `LOST_IN_TRANSIT` (posts SCRAP_OUT from transit via
  WS-D reason taxonomy) or `RETURNED_TO_SOURCE` (posts TRANSFER_IN back at source). Permission-gated (`inventory:transfer:short_close`), reason + notes mandatory.
- **C5. Movement eligibility**: both endpoints validated against `LocationRef` status per DECISION-INVENTORY-009.
- **C6. API surface (sketch)**: `POST /v1/inventory/transfer-orders`, `GET /{id}`, `GET /` (filters: status, source, destination), `POST /{id}/dispatch`,
  `POST /{id}/receive` (line-level quantities), `POST /{id}/short-close`, `POST /{id}/cancel`. Permissions: `inventory:transfer:{create,view,dispatch,receive,short_close}`.
  Facts: `TransferOrderUpdatedV1` per state change; availability facts fire via existing `InventoryFactPublisher` key-touch mechanism.
- **C7. Existing immediate TRANSFER** via `POST /stock-movements` is retained for intra-site bin moves only; cross-site transfers through that endpoint are rejected
  (422 `CROSS_SITE_TRANSFER_REQUIRES_ORDER`) once C1 ships.
- Replenishment tasks (WS-F) whose source and destination are different sites materialize as TransferOrders.

**Dependencies**: WS-A summary (`inTransitQty` column), WS-D reason taxonomy for loss. **Odoo mechanics to port as tests**: conservation invariant across the three scopes;
partial-receipt remainder handling.

---

## 5. Workstream D — Scrap, damage, and write-off workflow

**Odoo reference**: `stock.scrap` (reason tags, insufficient-qty check, loss-location move, optional replenish trigger). **Gap**: G6.

- **D1. Scrap document.** `ScrapRecord`: SKU, quantity, locationId/storageLocationId, reasonCode (new enum: DAMAGED, EXPIRED, LOST, RECALLED, CONTAMINATED,
  WARRANTY_DESTROYED, OTHER — extensible), notes, optional workorderId linkage (part damaged during job), optional lot/serial (WS-E), photo/attachment reference (optional
  field, storage out of scope). Statuses `PENDING_APPROVAL → APPROVED → POSTED | REJECTED` reusing the `ApprovalThresholdConfig` tier mechanism with scrap-specific
  thresholds (value-based via cost snapshot); below-threshold auto-approves — same pattern as `CycleCountAdjustment`.
- **D2. Posting** writes `SCRAP_OUT` (finally reachable) with reasonCode, cost snapshot (unitCost at time of scrap — WS-J refines the cost source), and
  `sourceTransactionId` = scrap id. Insufficient on-hand → 422 with guided-reconciliation pointer (same UX contract as putaway source-on-hand rule in
  `docs/putaway-validation-rules.md`), overridable with `inventory:adjustment:override`.
- **D3. Optional replenish flag**: `shouldReplenish` triggers a WS-F pick-face evaluation for the scrapped SKU/location (Odoo's `do_replenish` analog).
- **D4. Facts & accounting**: `ScrapPostedV1` fact (SKU, location, qty, reason, cost) on `inventory.events.v1` — pos-accounting consumes for shrinkage GL posting
  (cross-domain contract addition; coordinate with accounting domain).
- **D5. API sketch**: `POST /v1/inventory/scraps`, `GET /{id}`, `GET /` (filters reason/status/location/date), `POST /{id}/approve`, `POST /{id}/reject`. Permissions
  `inventory:scrap:{create,view,approve}`.
- **Acceptance shape**: scrap below threshold auto-posts SCRAP_OUT and decrements on-hand; above threshold requires approval; reason analytics queryable from ledger +
  document; shrinkage fact consumed in an accounting contract test.

**Dependencies**: none hard; WS-J for the authoritative cost source (interim: latest receipt cost). WS-C consumes the reason taxonomy.

---

## 6. Workstream E — Lot and serial tracking, expiry, FEFO (phased)

**Odoo reference**: product `tracking` none/lot/serial; lot on quants and move lines; `product_expiry` dates + FEFO; traceability report. **Gaps**: G7, G8, G9. This is the
largest structural change — phase it; every phase independently shippable.

- **E1 (Phase 1) — Lot master + inbound capture.** `InventoryLot` entity: lotId, SKU, lotNumber (unique per SKU), receivedAt, vendorId, optional expirationDate,
  status (ACTIVE, QUARANTINED, RECALLED, CONSUMED). Tracking level per product from catalog replica (`NONE | LOT | SERIAL` — catalog owns the flag; contract addition).
  Goods-receipt/receiving lines for LOT-tracked products require a lotNumber (already captured on ASN/receipt lines — now validated and linked); ledger rows gain a
  nullable `lotId`; stock summary (WS-A) gains per-lot rows for tracked SKUs. Untracked products: zero behavior change, `lotId` null.
- **E2 (Phase 2) — Lot-aware outbound.** Picking/consumption/returns/transfer/scrap for LOT-tracked products record `lotId` per posting; pick tasks suggest a lot per the
  removal strategy (WS-H; FEFO when expiry present, else FIFO by receivedAt). Allocation stays location-level (Durion decision — allocations do not pin lots; the pick
  records what was actually taken; revisit only if regulatory hard-allocation appears). Lot on-hand = ledger aggregation by lotId; negative lot balances rejected.
- **E3 (Phase 3) — Expiry & quarantine.** `expirationDate` (+ optional alertDate) on lots; expired lots excluded from ATP/availability (Odoo semantics) while still in
  on-hand; scheduled job flags newly expired/alerting lots and emits `LotExpiryAlertV1`; quarantine status blocks picking suggestion and reservation-fill from that lot;
  RECALLED status (set via API, `inventory:lot:manage`) same blocking + surfaced in traceability. Site default quarantine location (`defaultQuarantineLocationId`, already
  on `LocationRef`) becomes the destination for quarantine transfer flows.
- **E4 (Phase 4) — Serial tracking.** SERIAL products: `InventorySerialUnit` (serialNo unique per SKU, status IN_STOCK/ISSUED/RETURNED/SCRAPPED, current locationId,
  lotId optional). Quantity postings for serial SKUs must enumerate serials (qty must equal serial count — Odoo's integer/one-per-unit rule); receipt captures serials,
  consumption/issue records serial → workorder linkage (feeds warranty: `WarrantyPartReturnHold.serialNumber` finally joins to a stock record). Serial on-hand derives from
  unit status, verified against ledger totals.
- **E5. Traceability query.** `GET /v1/inventory/lots/{lotId}/traceability`: upstream (ASN/PO/receipt) and downstream (putaway → picks → workorder consumption → returns →
  scraps) from ledger rows + document linkages — Durion's ledger + `sourceTransactionId` makes this a query, not a graph walk. Serial equivalent under
  `/serial-units/{id}/traceability`. Permission `inventory:ledger:view`.
- **Acceptance shape per phase**: E1 — receipt of tracked product without lot → 422; lot on-hand visible. E2 — consumption decrements the picked lot; FEFO suggestion
  ordering test. E3 — expired lot drops out of ATP same-day; recall blocks picking within one poll cycle. E4 — serial double-issue rejected; workorder consumption exposes
  serials in the fact payload.

**Dependencies**: catalog tracking-level flag (contract addition, OQ-2); WS-A per-lot summary rows; WS-H for suggestion ordering. **Non-goal within E**: lot-pinned
allocations; owner/consignment dimension.

---

## 7. Workstream F — Replenishment engine completion and purchase suggestions

**Odoo reference**: orderpoints (`stock.warehouse.orderpoint`): forecast at lead-time horizon, replenish-to-max, order multiples, snooze, deadline date;
`purchase_stock._run_buy` vendor selection. **Gaps**: G10, G11.

- **F1. Finish the batch scan (CAP-217 debt).** Implement `runBatchReplenishmentScan`: iterate active `ReplenishmentPolicy` rows, evaluate trigger, create/refresh
  `ReplenishmentTask`s, idempotent per (policy, day) — no duplicate open tasks (guard exists for event path; extend to batch). Scheduled via existing platform scheduling
  conventions; manual trigger endpoint `POST /v1/inventory/replenishment/scan` (`inventory:replenishment:manage` — new permission; current POST policies uses
  `inventory:adjustment:create`, which should migrate to the new resource-correct permission as part of this workstream).
- **F2. Forecast-aware trigger math.** Replace on-hand-only comparison with Odoo's orderpoint formula, adapted:
  `qtyToReplenish = max(0, maximumQuantity − projectedAvailable(leadHorizon))` where `projectedAvailable` comes from WS-A2 and `leadHorizon = now + leadTimeDays`
  (lead time from `InventoryLeadTimeServiceImpl` — vendor feeds — falling back to a policy-level default). Trigger fires when `projectedAvailable(leadHorizon) <
  minimumQuantity`. In-progress supply (open replenishment tasks, suggested POs from F4) counts as incoming to prevent double-ordering — Odoo's `qty_in_progress` rule.
- **F3. Policy enrichment.** `ReplenishmentPolicy` gains: `orderMultiple` (round qtyToReplenish UP to multiple — pack-size default from feed data),
  `leadTimeDaysOverride`, `preferredSourceType` (INTERNAL_TRANSFER | PURCHASE | EITHER), `snoozedUntil` (+ `POST /policies/{id}/snooze`, Odoo orderpoint snooze),
  `active` flag. Stock-out projection `deadlineDate` (first date projectedAvailable < 0) computed on the replenishment task/report for prioritization.
- **F4. Purchase suggestions.** When sourcing resolves to PURCHASE (policy preference, or no internal source has surplus): create `PurchaseSuggestion` (SKU, location, qty
  after multiple-rounding, suggested vendor, unit cost, lead time, earliest expected date; status SUGGESTED → ACCEPTED → CONVERTED | DISMISSED). Vendor selection ranks
  normalized feed rows (`NormalizedAvailability`/`DistributorNormalizedInventory`): availability ≥ qty, then lead time, then price — deterministic and explainable
  (`selectionReason` recorded). `POST /purchase-suggestions/{id}/convert` creates a DRAFT `PurchaseOrderEntity` through the existing PO service (approval workflow then
  applies — suggestions never auto-approve spend). Grouping: suggestions for one vendor convert into one multi-line PO. Permissions
  `inventory:replenishment:manage`, conversion additionally `inventory:purchase_order:create`.
- **F5. Internal sourcing.** When sourcing resolves to INTERNAL_TRANSFER, source-location selection uses WS-H strategy (surplus = onHand − allocated − own min at source);
  cross-site tasks materialize as WS-C TransferOrders; same-site tasks stay `ReplenishmentTask` bin moves. `ReplenishmentDecisionReason`/`SourcingReason` enums finally get
  populated by real logic.
- **F6. Replenishment report.** `GET /v1/inventory/replenishment/needs`: current computed state per policy (projected, deadline, suggested action) without side effects —
  Odoo's replenishment screen equivalent, for ops review before running the scan.
- **Acceptance shape**: below-min with inbound PO covering the gap → no trigger (forecast math proof); MOQ/pack rounding proof; snoozed policy skipped and logged;
  vendor-selection determinism test; convert produces a DRAFT PO passing existing PO contract tests; no duplicate suggestion while one is open.

**Dependencies**: WS-A2 (hard), WS-C (cross-site tasks), WS-H (source selection). **Open questions OQ-3, OQ-4** (§12).

---

## 8. Workstream G — Shortage resolution completion

**Odoo reference (loose)**: backorders + MTO procurement of shortfall; Durion's shape is its own (workorder-driven). **Gaps**: G12, G20.

- **G1. Backorder record.** When reservation promotion or pick confirmation hits insufficiency and the resolver chooses BACKORDER: persist `BackorderRecord`
  (workorderLineId, SKU, qty short, status OPEN → RESOLVED | CANCELLED, resolution source), post `BACKORDER_CREATED` (ATP-neutral ledger event — type exists, finally
  written), emit `BackorderCreatedV1` fact for workexec visibility. Resolution: on-hand becoming available (via availability facts / replenishment receipt) triggers
  re-promotion attempt oldest-priority-first, posts `BACKORDER_RESOLVED`, emits fact. Reuse `AllocationReallocationServiceImpl` fairness ordering for competing backorders.
- **G2. Real resolution options.** `GET /shortage/options` returns computed options instead of static list: (a) BACKORDER with estimated availability date (from WS-A
  projections / F4 suggestion lead time); (b) SUBSTITUTE — wire the injected-but-unused substitute lookup via catalog substitution data replica (contract addition, OQ-5 —
  ADR-0044 forbids the currently-scaffolded RestClient approach); (c) TRANSFER_IN — surplus at sibling sites (WS-A summary cross-site query + WS-C transfer creation);
  (d) EMERGENCY_PURCHASE — WS-F4 suggestion pre-filled; (e) CANCEL_LINE. Each option carries expected-resolution-date and cost delta where computable.
- **G3. Resolution execution.** `POST /shortage/resolve` executes the chosen option atomically (creates the backorder/transfer/suggestion/substitution reservation) and
  returns the created artifact reference; idempotency key required (retry-safe).
- **Acceptance shape**: end-to-end: short pick → options include transfer from a surplus site → resolve → TransferOrder created → receipt → backorder auto-resolves →
  workexec sees both facts.

**Dependencies**: WS-A (projections, cross-site surplus), WS-C, WS-F4. Substitution needs OQ-5 resolved.

---

## 9. Workstreams H & I — Sourcing strategy engine; cycle-count hardening

### WS-H. Removal/sourcing strategy (Gap G13)

**Odoo reference**: `_get_removal_strategy(_order)` — category → location → default resolution; FIFO/LIFO/FEFO/closest orderings.

- H1. A single `SourcingStrategyService` used by pick-task suggestion (which bin/lot), consumption allocation-close ordering (replacing hardcoded oldest-first),
  replenishment source selection (F5), and lot suggestion (E2). Strategies v1: `FIFO` (by ledger receivedAt), `FEFO` (by lot expirationDate, tracked SKUs only),
  `PROXIMITY` (storage-location topology distance — same-zone-first using `ext_location_parent` hierarchy), `HIGHEST_STOCK`. Resolution order: per-SKU-category config →
  per-site config → platform default `FIFO`; configuration is a small `sourcing_strategy_config` table + admin endpoints (`inventory:location:admin`).
- H2. Strategy decisions are recorded (`sourcingReason` on pick task / replenishment task — fields exist) so ops can audit "why this bin".
- Non-goal: LIFO and least-packages (no POS scenario).

### WS-I. Cycle-count hardening (Gaps G14, G15)

**Odoo reference**: `cyclic_inventory_frequency` / `next_inventory_date` scheduling; `is_outdated` conflict + keep-counted/keep-difference choice.

- I1. **Recurring schedules**: `CycleCountSchedule` (locationId, optional zone/SKU-category filter, frequencyDays, nextDueDate, autoCreatePlan flag). Scheduled job creates
  the next `CycleCountPlan` at due date (or surfaces it in a "due for count" view when autoCreate is off); completing a plan restamps `nextDueDate` (Odoo's
  last/next-inventory-date loop). ABC-style prioritization is out of scope v1 — frequency is per schedule row.
- I2. **Count conflict detection**: at count submission and again at adjustment approval, recompute current on-hand and compare with the task's snapshot
  `expectedQuantity`; if movements occurred in the window (delta ≠ 0), flag the task `CONFLICT` (new TaskStatus) and require an explicit reviewer choice — recount, or
  approve with the variance recomputed against **current** on-hand (never the stale snapshot). The ledger makes the interfering entries listable — show them
  (`GET /task/{id}/interfering-movements`). This is Odoo's `is_outdated` + conflict wizard, upgraded with Durion's approval flow.
- I3. Freeze option (explicitly rejected): blocking movements during counts contradicts continuous shop operation; conflict detection replaces freezing. Record in plan as
  a decided alternative.

**Dependencies**: I2 none; I1 none. Both independent quick wins.

---

## 10. Workstream J — Valuation & costing (decision-first)

**Odoo reference**: v19 move-carried value (`stock.move.value`, FIFO remaining stack, `product.value` history), AVCO incremental recompute, real-time COGS postings,
landed costs. **Gaps**: G16, G17. **This workstream starts with a cross-domain decision, not code.**

- **J0. Ownership decision (blocking).** Two viable designs; pick one with accounting domain before any story is cut:
  - **(a) Inventory-owned valuation (recommended; Odoo-analogous).** pos-inventory computes and stores cost on every ledger entry (extending the existing `unitCost`
    column from "snapshot when known" to "always populated, method-derived"), maintains per-SKU(-per-site) running AVCO or FIFO cost layers, and emits cost-bearing facts
    (`ConsumptionRecordedV1` + cost, `ScrapPostedV1` + cost, receipt facts + cost). pos-accounting posts GL from facts and never computes item cost. Odoo v19's
    "value on the movement record" maps directly onto Durion's ledger entry — architecturally the cleanest fit.
  - **(b) Accounting-owned valuation.** pos-inventory emits quantity facts with acquisition costs only (receipts); pos-accounting owns cost layers and COGS. Keeps
    inventory purely quantitative but splits the FIFO stack from the quantity ledger that feeds it — rejected by default; document why if chosen.
- **J1 (under (a)). Costing method**: per SKU-category, `STANDARD | AVERAGE` v1 (AVCO incremental recompute on each receipt — Odoo `_update_standard_price` mechanics);
  `FIFO` v2 (remaining-qty/remaining-value stack per receipt ledger entry — Odoo v19 `remaining_qty` design; the received-lot linkage from WS-E1 helps but must not be
  required). Method configured in inventory (catalog category replica keys it), historical method changes recorded.
- **J2. Value read model**: on-hand value per SKU × site on the WS-A summary (`onHandValue`, `unitCostCurrent`); `GET /v1/inventory/valuation` (site-filtered, permission
  `inventory:valuation:view` — quantities × cost are doubly sensitive) + as-of variant via ledger replay (pairs with A3).
- **J3. Cost-bearing facts**: every on-hand-affecting ledger posting emits cost in its fact; cycle-count adjustments already snapshot cost — align source
  (`costAtTimeOfAdjustment` becomes method-derived). Contract change coordinated with accounting (their GRNI/encumbrance consumption already exists).
- **J4. Revaluation**: manual standard-price/AVCO correction endpoint with approval tier + `ProductValueChangedV1` fact (Odoo `product.value` analog, as a governed
  workflow).
- **J5. Landed costs (optional, last)**: distribute a cost document across one or more receipts by quantity or value (weight/volume omitted — no dimension data), adjusting
  cost layers and emitting a revaluation fact. Only if accounting confirms AP-side demand.
- **Acceptance shape**: AVCO worked example ported from Odoo test vectors (receipts at different costs → issue cost); valuation report ties to Σ(qty × layer cost);
  negative-on-hand costing rule decided and tested (WS-K interaction).

**Dependencies**: J0 decision; WS-A summary. WS-D/WS-C consume the cost source. **Open question OQ-6** (§12).

---

## 11. Workstream K — Policy hardening (small items) & explicit non-goals

### WS-K items

- K1. **Negative-stock policy matrix (G18)**: document + enforce per event type: PICK/ISSUE/consumption — blocked (today's behavior, kept); TRANSFER dispatch — blocked;
  SCRAP — blocked with override; ADJUSTMENT_OUT / COUNT_VARIANCE_OUT — may take on-hand to ≥ 0 only (floor at zero; counts by definition set reality); GOODS_RECEIPT —
  unconstrained. DB-level check is impractical against an append-only ledger — enforce in the single posting path (all ledger writes already funnel through the ledger
  service; assert that with an ArchUnit rule).
- K2. **Putaway sublocation strategies (G19)**: extend `PutawayRule` with `LAST_USED` and `CLOSEST_AVAILABLE` (capacity-aware, topology-proximity via WS-H) destination
  strategies alongside fixed destination. Low priority; current rule+validation model is adequate.
- K3. **Reservation-consistency sweep**: scheduled verifier asserting per-allocation `Σ ALLOCATION_CREATED − Σ ALLOCATION_RELEASED ∈ {0, allocatedQuantity}` and
  summary `allocated` reconciliation (Odoo `_clean_reservations` analog; report-only, alerting — never auto-mutate the ledger).

### Explicit non-goals (decided; record in plan; each with revisit trigger)

| Non-goal | Rationale | Revisit trigger |
| --- | --- | --- |
| Push/pull routing engine, multi-step reception/delivery routes | Flows are explicit per-document services; POS shops have no pick/pack/ship pipeline | A true distribution-center deployment |
| Batch/wave picking | Single-mechanic picking at shop scale | Central parts warehouse with dedicated pickers |
| Packages/pallets, package types, least-packages removal | Parts move as eaches/cases (cases handled by WS-B UoM) | Palletized hub operations |
| Consignment (`owner` dimension on stock) | No vendor-owned-stock program | A consignment parts program contract |
| Dropshipping route | Cross-dock direct-to-workorder covers the POS-shaped need | Vendor-direct-to-customer fulfillment |
| Manufacturing/kitting/BOM, production locations | No manufacturing in platform scope | — |
| Multi-company/inter-company transit | Single-company platform | — |
| LIFO removal | No accounting or operational demand | — |

---

## 12. Open questions (resolve during planning, before stories are cut)

| #    | Question | Blocking | Default if unanswered | Response |
| ---- | --- | --- | --- | ---|
| OQ-1 | UoM conversion factors: does pos-catalog already model purchase-UoM/pack conversions per product, or must that be added catalog-side first? | WS-B | Add to catalog product contract; inventory consumes via replica | ACCEPTED|
| OQ-2 | Tracking-level flag (NONE/LOT/SERIAL) ownership — catalog product attribute (recommended) or inventory-side config? | WS-E | Catalog attribute, replicated | ACCEPTED |
| OQ-3 | Purchase suggestions: may inventory auto-create DRAFT POs on scan (no human accept step), or is SUGGESTED → human ACCEPT mandatory? | WS-F4 | Human accept mandatory (spend governance) | ACCEPTED |
| OQ-4 | Replenishment lead-time source of truth when feed data and policy override conflict | WS-F2 | Policy override wins; feed is fallback | ACCEPTED |
| OQ-5 | Product substitution data: replica of catalog substitution groups (ADR-0044-compliant) vs the currently-scaffolded REST client | WS-G2 | Event-fed replica; delete the scaffold client | ACCEPTED |
| OQ-6 | J0 valuation ownership (inventory-owned recommended) + v1 costing method (AVCO recommended over FIFO) | WS-J | (a) inventory-owned, AVCO first | ACCEPTED (costing method configurable; valuation ownership fixed inventory-owned) |
| OQ-7 | Should expired-lot exclusion from ATP be immediate (computed) or job-driven (eventual, simpler)? | WS-E3 | Job-driven daily + on-read guard for suggestion paths | ACCEPTED |
| OQ-8 | Transfer-order approval step: required for cross-site moves or DRAFT→DISPATCH direct? | WS-C1 | Config-flag per deployment, default off | ACCEPTED |

---

## 13. Dependency graph & suggested sequencing seed (input to the plan, not the plan)

```
WS-A (summary + forecast qtys)  ──►  WS-F (replenishment math)   ──►  WS-G (shortage options)
        │                                    │
        ├──► WS-C (transfers: inTransitQty)◄─┘ (cross-site sourcing)
        │            │
        │            └──► uses WS-D reason taxonomy (loss-in-transit)
        ├──► WS-J (valuation, after J0 decision)
        └──► WS-E per-lot summary rows (E1→E2→E3→E4 strictly ordered)
WS-B (UoM)      — independent; before WS-F4 PO suggestions ideally (MOQ/pack in correct UoM)
WS-D (scrap)    — independent quick win (interim cost source until WS-J)
WS-H (strategy) — after E1 for FEFO; PROXIMITY/FIFO independently earlier
WS-I (counts)   — independent quick wins (I2 conflict detection first)
WS-K            — K1/K3 early (cheap hardening); K2 anytime
```

Quick wins with no dependencies: **WS-I2 (count conflict), WS-D (scrap), WS-K1/K3, WS-F1 (finish the CAP-217 stub with current math, upgraded by A2 later)**.
Highest-leverage foundation: **WS-A** — four workstreams consume it. Decision to schedule first: **J0** (valuation ownership) and OQ-1/OQ-2 (catalog contract additions),
since they gate cross-domain contract work with the longest coordination lead time.
