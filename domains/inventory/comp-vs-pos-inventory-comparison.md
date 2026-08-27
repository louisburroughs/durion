# Odoo Inventory vs pos-inventory — Capability Comparison Map

> Purpose: a working checklist for comparing `durion-positivity-backend/pos-inventory` against Odoo 19's `addons/stock` (+ `stock_account`, `product_expiry`,
> `stock_picking_batch`, `stock_landed_costs`, `purchase_stock`). Odoo detail is in `comp-odoo-inventory-overview.md`. pos-inventory references come from
> `pos-inventory/README.md`, `docs/inventory-ledger-atp.md`, `docs/putaway-validation-rules.md`, `src/main/java/com/positivity/inventory/internal/` (entities, services,
> controllers) and Flyway `V1__baseline_inventory_schema.sql`…`V8`, as of 2026-07-22.
>
> The two systems have different missions — Odoo is a general-purpose WMS/ERP for warehouses of any shape; pos-inventory is an event-driven parts-inventory service for a
> workorder-centric POS platform (auto-service context: parts received against POs/ASNs, picked and consumed against workorders). "Gap" below means "Odoo has machinery
> pos-inventory doesn't", not necessarily "must build". The derived build spec is `SPEC-pos-inventory-odoo-parity.md`.

## 0. Foundational model difference (read first)

| Concern | Odoo (`stock`) | pos-inventory | Notes for comparison |
| --- | --- | --- | --- |
| On-hand primitive | `stock.quant`: stored current balance per (product, location, lot, package, owner), `reserved_quantity` first-class column | `inventory_ledger_entry`: append-only event ledger; on-hand = `SUM(changeInQuantity)` over on-hand-affecting event types, computed at query time | Odoo stores state and derives history (moves); Durion stores history and derives state. Durion's model is stronger for audit, weaker for read cost and dimension richness (no lot/owner/package key) |
| Movement primitive | `stock.move` (+ `stock.move.line`) with state machine draft→confirmed→assigned→done; every flow is a move between two locations incl. virtual (supplier/customer/loss/production/transit) | Ledger event types (GOODS_RECEIPT, TRANSFER_IN/OUT, PUTAWAY, GOODS_ISSUE, WORKORDER_CONSUMPTION, SCRAP_OUT, ADJUSTMENT_IN/OUT, COUNT_VARIANCE_IN/OUT…) written at execution time; planning state lives in domain documents (receiving session, putaway task, pick task) | Odoo has one planned-work primitive; Durion has per-flow documents + a shared executed-work ledger. Durion's ledger has from/to location on one row — no virtual-location double entry |
| Consistency model | Single DB, synchronous, row locks + quant merge GC | Event-driven: transactional outbox (`event_outbox` → `inventory.events.v1`), consumed replicas (`location_ref`, `ext_storage_location`, `ext_workorder*`), `processed_events` idempotency, manifest reconciliation | Durion strength — Odoo has no cross-service story at all. Keep. |
| UoM handling | Moves in any UoM, quants in product reference UoM, converted everywhere with deliberate down-rounding on reservations | v1 explicitly: quantities in product base UoM, **no conversion** (`docs/inventory-ledger-atp.md`); `unitOfMeasure` is a free string on ledger rows | Real gap if purchasing/receiving UoM ≠ stocking UoM (cases vs eaches) — common for parts |

## 1. Locations, topology, putaway

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Location model | Hierarchical `stock.location` with `usage` types (internal/view/supplier/customer/loss/production/transit); reservation bypass for non-internal | Two-level canonical model (DECISION-INVENTORY-001): `LocationRef` (site) + `StorageLocation` (bin), replicated from pos-location via events; typed hierarchy edges (`ext_location_parent`) | Durion has no virtual-location concept; "outside world" is implicit (ledger rows with only one location side). Transit/loss/supplier as first-class would firm up transfer and scrap semantics |
| Putaway rules *(updated 2026-08-27, #1514)* | `stock.putaway.rule` (product/category/package-type → destination, sublocation strategies none/last-used/closest), rule specificity resolution | `PutawayRule` (priority + `match_type`/`match_value` → destination + `destinationStrategy`), resolved **per line** by `PutawayRuleMatcher` in the strict precedence SKU > SUBCATEGORY > CATEGORY > ANY; `PutawayDestinationResolver` implements FIXED/LAST_USED/CLOSEST_AVAILABLE; `PutawayGenerationServiceImpl` suggestion + fallback reasons, task claim with pessimistic lock; rules managed over `/v1/inventory/putaway/rules` | Parity on the core. Durion matches at a *subcategory* level Odoo has no direct analogue for, and requires exactly one enabled ANY rule as a terminal fallback. Odoo still adds package-type matching (an explicit Durion non-goal) |
| Storage capacity *(updated 2026-08-27, #1514)* | `stock.storage.category`: max weight, per-product/package-type qty capacity, `allow_new_product` (empty/same/mixed), checked against current+forecast | `storage_compatibility` matrix (catalog category/subcategory id → accepted storage classes, `requires_containment`) evaluated by `StorageCompatibilityEvaluator` against the replicated `storageCategoryCode`/`hazardContainment`; `ext_storage_location.maxUnitCapacity` + `PutawayValidationServiceImpl` (capacity + tolerance override, source on-hand check; override permissions). `allowNewProduct` is replicated but unenforced | Durion has real enforcement with an override/audit model Odoo lacks, and now a genuine class-fitness matrix rather than a replenishment-policy proxy. Odoo is still richer: max weight, per-product capacity, forecast-aware checks, and an enforced `allow_new_product` |
| Warehouse config | `stock.warehouse` auto-generates 1/2/3-step reception & delivery routes, per-step picking types, resupply routes | No warehouse abstraction; single-step receive→stage→putaway flow hardcoded (staging/cross-dock virtual location codes in `application.yml`) | Multi-step routing is likely overkill for shops; staged receive (dock→QC→stock) partially exists via staging + putaway tasks |
| Location lifecycle | Archive; no destination-required deactivation | Deactivation with destination-required semantics for non-empty bins (DECISION-INVENTORY-007), site default staging/quarantine locations | Durion ahead here |

## 2. Receiving & procurement inbound

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Purchase orders | Separate `purchase` module; `purchase_stock` links receipts, vendor lead time → scheduled date | First-class in-module: PO lifecycle DRAFT→APPROVED→PARTIALLY/FULLY_RECEIVED→CLOSED/CANCELLED, minor-unit money, approvals, revisions with field deltas, encumbrance events, base-36 PO numbering | Durion's PO with approval/encumbrance is richer than Odoo community purchasing in governance terms |
| ASN | No ASN concept in core | `AdvanceShippingNotice` + lines (lot number, unit cost), dedup by vendor+reference, PO linkage | Durion ahead |
| Receipt execution | Incoming picking: reserve→scan lots→validate; backorder on shortfall; reception report allocates to outstanding demand | `ReceivingSession`/`ReceivingLine` (PO/ASN source, manual/scan), short/over statuses + `InventoryVariance` rows, GRNI accrual on goods receipt (`totalAccruedAmountMinor`), over-receipt guard vs PO open balance with override permission | Different framing, both complete. Odoo's backorder = Durion's SHORT variance without a follow-up document (PO stays PARTIALLY_RECEIVED — acceptable) |
| Cross-dock | Cross-dock route (input→output) via rules | Explicit receive-line→workorder cross-dock endpoint posting paired GOODS_RECEIPT+GOODS_ISSUE, workorder state + part-match validation with override | Durion's direct-to-workorder is the POS-shaped equivalent; ahead for its context |
| Vendor supply data | Vendor pricelists (price, delay, MOQ) on product | Manufacturer/distributor normalized feeds (`NormalizedAvailability`, `DistributorNormalizedInventory` with lead times, MOQ, pack size), exception queue, unmapped-part tracking, dynamic lead-time service | Durion ahead — but this data is not yet consumed by replenishment (see §5) |

## 3. Reservations, picking, outbound

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Reservation model | Move-level against quants: `reserved_quantity` on the quant, move → `partially_available`/`assigned`; timing policies at_confirm/manual/by_date | `ReservationEntity` per workorder line (unique) + `AllocationEntity` (SOFT→HARD) per location; HARD promotion checks ATP and writes ALLOCATION_CREATED ledger events; statuses incl. BACKORDERED | Same intent, different granularity: Odoo reserves specific stock (incl. lot/package/owner); Durion reserves quantity at a location. Fine while stock is undifferentiated; breaks down once lots/serials matter |
| Removal strategy | FIFO/LIFO/FEFO/closest/least-packages engine ordering quant candidates; category→location resolution | `ReplenishmentSourcingReason` enum names FEFO/FIFO/PROXIMITY but no engine; consumption closes HARD allocations oldest-first; pick suggested locations from putaway/allocation data | Gap: no configurable source-location/lot selection policy for picks and replenishment moves |
| Pick execution | Picking + move lines, scan support in Barcode (Enterprise); batch/wave picking module | `PickListEntity`/`PickTaskEntity` per workorder (release, confirm with scan-mismatch check, cancel, status transitions), priorities, due dates | Core parity for workorder picking. No batch/wave grouping across workorders (probably unnecessary at shop scale) |
| Consumption/issue | Manufacturing/delivery moves consume reserved stock | `ConsumptionServiceImpl`: WORKORDER_CONSUMPTION + oldest-first ALLOCATION_RELEASED with per-allocation invariant, `ConsumptionRecordedV1` fact | Parity for the POS flow |
| Shortage handling | Backorder documents; MTO/MTSO procures shortfall; forecast warnings | `ShortageResolutionServiceImpl` returns static BACKORDER/CANCEL options, stub resolution; substitute/external-availability clients injected but unwired; `BACKORDER_CREATED/RESOLVED` ledger types exist unused | Gap: shortage→resolution (backorder doc, substitution, emergency PO) is scaffolding only |
| Returns to stock | Return picking (reverse of delivery) | `InventoryReturn` + lines, returnable-item lookup, reason codes, RETURN_TO_STOCK ledger entries | Parity for workorder returns |
| Reallocation | None (manual unreserve/re-reserve) | Deterministic priority-based reallocation with priority aging, fairness tie-breakers, full `AllocationAudit` trail | Durion ahead |

## 4. Adjustments, counts, scrap

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Cycle counts | Count on the quant (`inventory_quantity`), apply posts loss-location move; conflict detection (`is_outdated`) with keep-counted/keep-difference wizard; per-location cyclic frequency + annual date auto-scheduling; request-count assignment wizard | `CycleCountPlan` (location+zones, scheduled date) → `CycleCountTask` → `CountEntry` recount chains (max 3, auditor vs manager, REQUIRES_INVESTIGATION); `CycleCountAdjustment` with two-tier threshold approval (unit/value/percentage, OR logic), AUTO_APPROVED below threshold, posts COUNT_VARIANCE_IN/OUT with cost-at-time | Durion's approval/recount governance is far stronger. Odoo's wins: recurring auto-scheduling from location frequency, and count-vs-movement conflict detection (Durion snapshots `expectedQuantity` at task creation; movements during the count window aren't detected) |
| Ad-hoc adjustments | Inventory mode quant edit (immediate) | Two workflows: `InventoryAdjustmentRequest` (create PENDING→approve→post) and direct stock movements; append-only per DECISION-INVENTORY-006 | Durion ahead on governance |
| Scrap | `stock.scrap` document: reason tags, lot, insufficient-qty warning, optional auto-replenish, loss-location move | `SCRAP_OUT` ledger event type exists; **no scrap document, endpoint, or workflow** — only expressible as a generic adjustment | Gap: no first-class scrap/damage/write-off flow with reason taxonomy (matters for shrinkage analytics and accounting) |
| Negative stock | Allowed (quants go negative); valuation handles it | PICK/ISSUE guard via `InsufficientStockException`; policy for other paths implicit | Decide and document an explicit negative-on-hand policy per event type |

## 5. Replenishment & forecasting

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Reorder rules | Orderpoints: min/max per product×location, auto/manual trigger, snooze, replenish-to-max using forecast at lead-time horizon, replenishment multiples, stock-out `deadline_date` | `ReplenishmentPolicy` (min/max per SKU×location) + `ReplenishmentTask`; event-driven pick-face evaluation exists; **batch below-min scan is a stubbed TODO (CAP-217)**; on-hand-only math | Gap: engine unfinished; no forecast/lead-time awareness; no order multiples/MOQ rounding |
| Forecast quantities | `qty_available`, `free_qty`, `incoming_qty`, `outgoing_qty`, `virtual_available` computed per product/location/date | On-hand and ATP (= on-hand − allocations) only; expected receipts explicitly out of ATP v1 (`docs/inventory-ledger-atp.md`); open PO/ASN quantities exist in-module but aren't surfaced as incoming supply | Gap: no `incoming`/`projected` quantities, so no forecast-based anything. All inputs already on hand (open PO lines `openQuantityDecimal`, ASN expected arrivals, reservations) |
| Procurement generation | Orderpoint → route → buy rule → draft PO with vendor lead time; MTO chains; inter-warehouse resupply | Replenishment tasks are internal transfer work only; no policy→PO path; vendor feed data (lead time, MOQ, pack size) unused for sourcing | Gap: no suggested-PO generation from reorder needs. Durion has better vendor data to feed it than Odoo does |
| Routing engine | Push/pull rules in routes; MTO/MTSO; multi-step chains | None; flows are explicit per-document services | Deliberate simplification — keep. Only the outcomes (staged receive, transfer chains) matter, not the rule engine |

## 6. Transfers & multi-site

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Internal transfer | Internal picking with reserve→done lifecycle, backorders | `POST /stock-movements` TRANSFER posts paired TRANSFER_OUT/TRANSFER_IN immediately — no document, no planned state, no in-transit representation | Gap for site-to-site moves: nothing to dispatch, track, or receive against; quantity teleports |
| In-transit stock | Transit locations between warehouses/companies | `InventorySourceType.TRANSIT` enum value exists; unused in ledger flows | Same gap: goods on a truck are invisible or double-counted |
| Inter-site resupply | Resupply routes + transit, MTO chains across warehouses | Replenishment task has source/destination locations (single-site pick-face focus) | Multi-site transfer order is the POS-shaped need (hub → satellite shops) |

## 7. Lots, serials, expiry, traceability

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Tracking granularity | Product `tracking` none/lot/serial; quants and move lines carry `lot_id`; serial = integer qty 1 lines | `lotNumber` captured on ASN/goods-receipt lines only — never enters the ledger; serial numbers only on `WarrantyPartReturnHold` (fed by warranty events) | Gap: no lot/serial on-hand, picking, or consumption. For auto parts: batteries, fluids (shelf life), recalled parts, warranty-serialized components |
| Expiry / FEFO | `product_expiry`: expiration/use/removal/alert dates per lot; FEFO removal; expired stock excluded from availability; alert cron | None | Follows from lot gap; relevant for fluids/chemicals |
| Traceability | Upstream/downstream lot/move tree report; lot→delivery resolution | Ledger is queryable per SKU/location (immutable, DECISION-INVENTORY-005) but has no lot dimension and no linkage graph rendering | Ledger + `sourceTransactionId` gives transaction traceability; product-genealogy traceability needs lots |

## 8. Valuation & costing

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Costing methods | standard / AVCO / FIFO per product category; per-lot valuation (v19); value carried on moves (`stock.move.value`, FIFO remaining stack), `product.value` history | `unitCost` snapshot on ledger entries; `costAtTimeOfAdjustment` on cycle-count adjustments; PO line costs in minor units; GRNI accrual amounts; **no costing method, no on-hand value, no COGS valuation** | Gap — but ownership must be decided with accounting domain: pos-accounting consumes receipt/GRNI/encumbrance events. Someone must own "value of inventory on hand" and "cost of a consumption" |
| COGS on issue | Real-time COGS entries per outgoing move | Consumption events carry quantity, not cost | Same decision |
| Landed costs | `stock_landed_costs`: distribute by qty/weight/volume/equal/cost, adjust move values, post JE | None | Optional; freight-in matters for margin on parts |
| Revaluation | Manual value changes recorded in `product.value` | None | Follows costing decision |

## 9. Availability, reporting, analytics

| Concern | Odoo | pos-inventory | Notes |
| --- | --- | --- | --- |
| Availability queries | Product quantity fields with location/lot/owner/date context; forecast report pairing supply vs demand | Availability endpoints (per product, by SKU list, lead-time), location inventory inquiry/items, site & parent-location rollups (depth-first, capped tree expansion), sensitive-by-default quantities (DECISION-INVENTORY-011) | Rollups are a Durion strength (Odoo has no cross-site hierarchy rollup). Missing: as-of/point-in-time on-hand (ledger makes this cheap) and any forecast view |
| Movement history | Move/move-line lists; quantity history SQL view + point-in-time wizard | Paged ledger query with filters (`inventory:ledger:view`), immutable | Parity-ish; ledger is arguably better raw material |
| Traceability report | Lot tree PDF | None (see §7) | |
| Turnover / aging | 12-month move counts; no core ABC/aging | None | Both thin; POS margin analytics likely lives in reporting domain anyway |
| Reconciliation | Quant merge + `_clean_reservations` GC | Outbox manifests (`inventory.manifest.v1`), replica reconciliation, allocation CREATED−RELEASED invariant | Durion ahead on cross-service; Odoo's reservation-vs-actual sweep has a Durion analog worth confirming (allocation invariant checks exist in service logic) |

## 10. Deliberate non-overlaps (record, don't build)

| Odoo capability | Assessment for Durion |
| --- | --- |
| Push/pull routing engine, multi-step delivery routes (pick/pack/ship) | Non-goal — flows are explicit services; shops don't pack-and-ship |
| Consignment (`owner_id`) | Non-goal unless a parts-consignment program appears; revisit trigger recorded in spec |
| Nested packages / package types / least-packages removal | Non-goal at shop scale |
| Batch/wave picking | Non-goal at shop scale |
| Dropshipping route | Covered differently: cross-dock direct-to-workorder; vendor-direct-to-customer isn't a POS flow today |
| Manufacturing/production locations, kits/BOM | Out of module scope (no manufacturing) |
| Barcode UI, reception report auto-print | Frontend/device concerns, not backend gaps |

## 11. Suggested comparison exercises

1. **Availability math under load**: benchmark `SUM(changeInQuantity)` aggregation vs Odoo's stored quant for the 20 busiest SKUs at 1M+ ledger rows; decide whether a
   materialized on-hand snapshot (with ledger as source of truth) is needed before adding forecast quantities on top.
2. **Transfer teleportation**: walk a site-to-site transfer through `POST /stock-movements` and through an Odoo internal picking with transit; enumerate what an ops user
   cannot see or correct in Durion (dispatched-not-received, partial receipt, loss in transit).
3. **Count conflict**: run a Durion cycle count task while posting a GOODS_ISSUE for the same SKU/location between task creation and count submission; confirm the variance
   silently absorbs the movement (Odoo's `is_outdated` would flag it).
4. **Lot pull-through**: trace a lot number from ASN line → goods receipt line → …and confirm it dies there (never reaches ledger, putaway, pick, consumption, return).
5. **Replenishment dry run**: with a below-min pick face, verify `runBatchReplenishmentScan` no-ops (CAP-217 stub) and that no path exists from policy breach to PO
   suggestion despite `NormalizedAvailability` holding vendor lead time/MOQ/pack size.
6. **Costing hand-off**: with accounting domain, decide where AVCO/FIFO lives — pos-inventory valuing its own ledger vs pos-accounting valuing from receipt/consumption
   events. Odoo's v19 move-value design (value on the movement record) maps naturally onto Durion's ledger-entry `unitCost` if inventory owns it.
