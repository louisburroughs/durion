# Odoo Parity Plan — pos-inventory

> Status: ACCEPTED · Created 2026-07-22 · Branch: `claude/odoo-pos-inventory-comparison-ywp0ev`
>
> Goal: implement the missing functionality specified in `SPEC-pos-inventory-odoo-parity.md` — bringing `durion-positivity-backend/pos-inventory` to functional parity with
> the Odoo 19 inventory capabilities that matter for a workorder-centric, single-company POS platform — without breaking established Durion conventions. All eight spec open
> questions are resolved (owner responses recorded in the spec §12 "Response" column; rulings in §1 below).
>
> Sources: `SPEC-pos-inventory-odoo-parity.md` (requirement detail — stories below cite spec section IDs rather than restating them),
> `comp-vs-pos-inventory-comparison.md`, `comp-odoo-inventory-overview.md`, code survey of `pos-inventory` (2026-07-22), platform ADRs (`durion/docs/adr/`), inventory
> business rules (`.business-rules/`). Companion precedent: `../accounting/plan-odoo-parity-pos-accounting.md` (structure, wave mechanics, issue conventions).

---

## 0. Ground rules for the executing agent team

Spec §0 constraints are non-negotiable and are restated in every issue's acceptance bar. Summary for executors:

1. **ADR-0044**: event-only domain walls — no new synchronous RestClients; new inbound data via `{domain}.events.v1` + `ext_*` replicas + `processed_events` idempotency;
   new outbound facts via the existing outbox (`event_outbox` → `inventory.events.v1`) and manifest reconciliation.
2. **Ledger is truth**: `inventory_ledger_entry` stays append-only and authoritative; every stored balance in this plan is a derived, rebuildable read model.
3. **Module conventions**: UUID v7 (`@UUIDv7Id`), `internal/` vs `service/` split (ArchUnit), `@EmitEvent` + `EventTypes` entry per mutating endpoint, permissions via
   `permissions.yaml`/`InventoryPermissionRegistry` (`inventory:<resource>:<action>`), `ApiError` envelope, OpenAPI regeneration + Angular SDK update on controller change,
   Flyway from **V9** (V1–V8 are post-baseline-reset history; archives untouched), Spotless/Checkstyle/SpotBugs/ArchUnit green, `./mvnw -pl pos-inventory -am test` green.
4. **Location semantics**: DECISION-INVENTORY-001 (site vs bin), -009 (INACTIVE/PENDING movement block), pos-location remains location truth.
5. **Approval-first governance**: extend the `ApprovalThresholdConfig` tier pattern; never introduce apply-immediately mutations for value-bearing changes.
6. **Sensitive data**: DECISION-INVENTORY-011 — no quantity leaves an endpoint without an `inventory:on_hand:*`-or-stronger permission; valuation adds
   `inventory:valuation:view` (quantities × cost).
7. **Scope guard (non-goals, confirmed in spec §11)**: routing engine, batch/wave picking, packages/pallets, consignment owner dimension, dropshipping, manufacturing/BOM,
   multi-company, LIFO. Do not build these even where Odoo has them.

---

## 1. Decision register (spec §12 responses → rulings)

| #   | Source | Ruling                                                                                                                                                                                                                                                                                                                                                                                                                                 | Affects |
| --- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| D-1 | OQ-1 ACCEPTED | UoM conversion factors are added to the pos-catalog product contract; inventory consumes via an `ext_product_uom` replica. Catalog-side work is Story X1.                                                                                                                                                                                                                                                                   | B, X1   |
| D-2 | OQ-2 ACCEPTED | Tracking level (`NONE`/`LOT`/`SERIAL`) is a catalog product attribute, replicated to inventory. Catalog-side work rides Story X1.                                                                                                                                                                                                                                                                                            | E, X1   |
| D-3 | OQ-3 ACCEPTED | Purchase suggestions require human ACCEPT before a DRAFT PO is created; the scan never creates POs. Existing PO approval workflow then applies — two human gates on spend.                                                                                                                                                                                                                                                   | F4      |
| D-4 | OQ-4 ACCEPTED | Lead-time precedence: policy `leadTimeDaysOverride` wins; vendor-feed lead time is the fallback; policy-level default constant is the last resort.                                                                                                                                                                                                                                                                           | F2      |
| D-5 | OQ-5 ACCEPTED | Product substitution data arrives as an event-fed replica of catalog substitution groups (rides Story X1). The scaffolded `ProductSubstituteClient`/`ExternalAvailabilityClient` REST clients are **deleted** in Story G2 (pre-production policy: no dead shims).                                                                                                                                                             | G2, X1  |
| D-6 | OQ-6 "Can we make this a configuration?" | **Costing method: yes — configuration.** The valuation engine (J1) is built behind a `CostingStrategy` interface with the method resolved from configuration: per SKU-category setting (replicated catalog category → method mapping table maintained in inventory) with a per-deployment default (`pos.inventory.valuation.default-method`). `STANDARD` and `AVERAGE` ship in v1; `FIFO` is a pluggable v2 strategy behind the same interface — switching method per category is a config change plus a documented revaluation cut-over, not a code change. **Valuation ownership: not configurable.** Ownership (inventory-owned, option (a) in spec J0) is a fixed architectural decision — a configurable owner would require building and maintaining two complete costing engines and dual event contracts in perpetuity. If accounting domain later objects, that is an ADR revision, not a flag. Both halves are recorded in [ADR-0048](../../docs/adr/0048-inventory-owned-valuation-configurable-costing-method.adr.md). | J (all) |
| D-7 | OQ-7 ACCEPTED | Expired-lot exclusion from ATP is job-driven (daily) plus an on-read guard on suggestion paths (pick/lot suggestion never proposes an expired lot even before the job runs).                                                                                                                                                                                                                                                  | E3      |
| D-8 | OQ-8 ACCEPTED | Transfer-order approval step is a per-deployment config flag (`pos.inventory.transfer.approval-required`), default **off** (DRAFT→DISPATCHED direct).                                                                                                                                                                                                                                                                        | C1      |

---

## 2. Story catalog

Story IDs continue the spec's workstream lettering; each story cites its spec section for full requirement detail. Effort: S ≈ ≤2 days, M ≈ 3–5 days, L ≈ 1–2 weeks.
GitHub issues: see §3 issue map. Issue bodies carry the per-story scope/acceptance; this catalog is the authoritative ordering and dependency record.

### Cross-domain enabler

- **X1 — Catalog product-contract additions (pos-catalog)** — spec §3 B1, §6 E1, §9 G2; D-1/D-2/D-5. Extend the catalog product event payload (and replica manifest) with:
  UoM conversion set (per-product purchase/pack UoMs with factors and precision scales), `trackingLevel` (NONE/LOT/SERIAL, default NONE), and substitution-group membership.
  Additive, schema-versioned; catalog README + contract guide updated. **Effort M · Deps none · Blocks B1, E1, G2.**

### Workstream A — Availability read model & forecast (spec §2)

- **A1 — Stock summary read model** — spec A1. `inventory_stock_summary` (SKU × location; per-lot rows arrive with E1), same-transaction upsert from the single ledger
  posting path, rebuild job, scheduled drift verifier (report-only). Switch availability/rollup/inquiry reads; response contracts unchanged. Includes the load benchmark
  (P95 < 200 ms @ ≥5M ledger rows) as an acceptance gate. **Effort L · Deps none.**
- **A2 — Forecast quantities** — spec A2 + A4. `incomingQty` / `outgoingQty` / `projectedAvailable` (+ date-bounded variant) per SKU × site; additive availability-endpoint
  fields and `InventoryAvailabilityUpdatedV1` fact fields; `ExpectedSupplyDroppedV1` on PO close/cancel with open quantity; update
  `pos-inventory/docs/inventory-ledger-atp.md` (reverses the v1 expected-receipts exclusion; ATP definition unchanged). **Effort L · Deps A1.**
- **A3 — As-of on-hand** — spec A3. `?asOf=` variants on availability + location-inquiry reads, direct ledger aggregation, `inventory:ledger:view`. **Effort S · Deps none.**

### Workstream B — UoM handling (spec §3)

- **B1 — UoM replica + conversion service** — spec B1. `ext_product_uom` replica fed by X1 events (idempotent, stale-guarded); internal `UomConversionService` (to-base
  conversion, precision scales, HALF_UP / reservation-DOWN rounding per spec B3). **Effort M · Deps X1.**
- **B2 — Document-boundary conversion** — spec B2–B4. PO/ASN/receiving/return lines accept document UoM + qty; convert at ledger-posting time; persist conversion metadata
  on document lines; 422 `UOM_CONVERSION_UNDEFINED` when no path; ledger stays base-UoM; over-receipt guard operates in base UoM. **Effort M · Deps B1.**

### Workstream C — Transfer orders & in-transit (spec §4)

- **C1 — TransferOrder aggregate & lifecycle** — spec C1, C5, C6; D-8. Entities + status machine (approval step config-flagged, default off), CRUD + list endpoints,
  movement-eligibility validation, permissions `inventory:transfer:{create,view,dispatch,receive,short_close}`, `TransferOrderUpdatedV1` facts. **Effort L · Deps none.**
- **C2 — Dispatch/receive posting & in-transit stock** — spec C2 + C7. Dispatch posts TRANSFER_OUT into transit scope; receive posts TRANSFER_IN; summary `inTransitQty`;
  conservation invariant IT (source + transit + destination constant at every step); `POST /stock-movements` rejects cross-site TRANSFER (422
  `CROSS_SITE_TRANSFER_REQUIRES_ORDER`). **Effort L · Deps C1, A1.**
- **C3 — Partial receipt & short-close** — spec C3, C4. Line-level partial receipt; short-close requires disposition `LOST_IN_TRANSIT` (posts SCRAP_OUT from transit using
  D1's reason taxonomy) or `RETURNED_TO_SOURCE`; reason + notes mandatory; permission-gated. **Effort M · Deps C2, D1.**

### Workstream D — Scrap workflow (spec §5)

- **D1 — Scrap document & posting** — spec D1–D3, D5. `ScrapRecord` + reason taxonomy, approval tiers via `ApprovalThresholdConfig` pattern (value-based, auto-approve below
  threshold), posting writes SCRAP_OUT with cost snapshot (interim source: latest receipt cost until J1), insufficient-on-hand → guided-reconciliation 422 with override,
  `shouldReplenish` hook, `ScrapPostedV1` fact, permissions `inventory:scrap:{create,view,approve}`. **Effort M · Deps none.**
- **D2 — Shrinkage GL posting (pos-accounting)** — spec D4. pos-accounting consumes `ScrapPostedV1` → shrinkage journal entry via posting-rule machinery; new posting
  category + seed; contract test against the fact schema. Coordinate with accounting domain owners. **Effort S · Deps D1.**

### Workstream E — Lots, serials, expiry (spec §6; strict phase order)

- **E1 — Lot master & inbound capture** — spec E1. `InventoryLot`; tracking level from X1 replica; receipt lines for LOT-tracked SKUs require lotNumber (validated +
  linked); nullable `lotId` on ledger rows; per-lot summary rows (A1 extension); untracked SKUs zero behavior change. **Effort L · Deps X1, A1.**
- **E2 — Lot-aware outbound** — spec E2. Pick/consumption/return/transfer/scrap postings record `lotId` for tracked SKUs; lot suggestion via H1 (FEFO when expiry present,
  else FIFO by receivedAt); allocations stay location-level (decided; do not pin lots); negative lot balances rejected. **Effort L · Deps E1, H1.**
- **E3 — Expiry & quarantine** — spec E3; D-7. Lot expiration/alert dates; daily job excludes expired lots from ATP + emits `LotExpiryAlertV1`; on-read guard on suggestion
  paths; QUARANTINED/RECALLED statuses block suggestion + reservation-fill; `inventory:lot:manage`; site default quarantine location used for quarantine moves.
  **Effort M · Deps E2.**
- **E4 — Serial tracking** — spec E4. `InventorySerialUnit`; serial enumeration = quantity invariant on postings for SERIAL SKUs; receipt captures serials; consumption
  records serial → workorder linkage (joins `WarrantyPartReturnHold.serialNumber` to stock); serial on-hand vs ledger verifier. **Effort L · Deps E1.**
- **E5 — Traceability queries** — spec E5. `GET /lots/{id}/traceability` + serial equivalent: upstream (PO/ASN/receipt) and downstream (putaway→pick→consumption→return→
  scrap) from ledger + document linkages. **Effort M · Deps E2 (serial arm: E4).**

### Workstream F — Replenishment & purchase suggestions (spec §7)

- **F1 — Finish the CAP-217 batch scan** — spec F1. Implement `runBatchReplenishmentScan` with **current** on-hand math (upgraded by F2): iterate active policies,
  idempotent per (policy, day), no duplicate open tasks; scheduled + manual `POST /replenishment/scan`; new permission `inventory:replenishment:manage`; migrate the
  policy-CRUD endpoints off `inventory:adjustment:create` onto it. **Effort M · Deps none.**
- **F2 — Forecast-aware trigger math** — spec F2; D-4. Orderpoint formula on `projectedAvailable(leadHorizon)`; lead time precedence per D-4; in-progress supply (open
  tasks + open suggestions) counts as incoming — no double-ordering. Odoo-derived test vectors. **Effort M · Deps A2, F1.**
- **F3 — Policy enrichment & needs report** — spec F3, F6. Policy fields `orderMultiple`, `leadTimeDaysOverride`, `preferredSourceType`, `snoozedUntil` (+ snooze
  endpoint), `active`; stock-out `deadlineDate` on tasks; side-effect-free `GET /replenishment/needs`. **Effort M · Deps F2.**
- **F4 — Purchase suggestions** — spec F4; D-3. `PurchaseSuggestion` lifecycle SUGGESTED→ACCEPTED→CONVERTED|DISMISSED; deterministic vendor ranking over normalized feeds
  (availability ≥ qty → lead time → price, `selectionReason` recorded); ACCEPT is human-gated; convert creates DRAFT PO via existing PO service (multi-line per vendor);
  MOQ/pack rounding in correct UoM. **Effort L · Deps F2 (B2 strongly recommended first for UoM-correct MOQ).**
- **F5 — Internal sourcing & cross-site materialization** — spec F5. Source-location selection via H1 (surplus = onHand − allocated − source min); cross-site tasks
  materialize as C1 TransferOrders; same-site tasks remain bin-move `ReplenishmentTask`s; `decisionReason`/`sourcingReason` populated by real logic. **Effort M · Deps F2,
  H1, C1.**

### Workstream G — Shortage resolution (spec §8)

- **G1 — Backorder lifecycle** — spec G1. `BackorderRecord`; first real writers of `BACKORDER_CREATED`/`BACKORDER_RESOLVED`; `BackorderCreatedV1`/`ResolvedV1` facts for
  workexec; auto-resolution on availability (oldest-priority-first, reusing reallocation fairness ordering). **Effort M · Deps A2 (resolution triggers ride availability
  facts).**
- **G2 — Computed shortage options & resolution execution** — spec G2, G3; D-5. Replace static options with computed BACKORDER / SUBSTITUTE (substitution replica from X1)
  / TRANSFER_IN (cross-site surplus → C1 order) / EMERGENCY_PURCHASE (F4 suggestion pre-filled) / CANCEL_LINE, each with expected-resolution date and cost delta where
  computable; `POST /shortage/resolve` atomic + idempotency key; **delete** the scaffolded `ProductSubstituteClient`/`ExternalAvailabilityClient`. **Effort L · Deps G1,
  C1, F4, X1.**

### Workstream H — Sourcing strategy engine (spec §9 WS-H)

- **H1 — SourcingStrategyService** — spec H1, H2. Strategies FIFO / PROXIMITY (topology via `ext_location_parent`) / HIGHEST_STOCK now, FEFO activating with E-phase data;
  resolution SKU-category → site → platform default FIFO; `sourcing_strategy_config` + admin endpoints (`inventory:location:admin`); wire into pick-task suggestion and
  consumption allocation-close ordering (replacing hardcoded oldest-first); decisions recorded in existing `sourcingReason` fields. LIFO/least-packages explicitly not
  built. **Effort M · Deps none (FEFO arm: E1).**

### Workstream I — Cycle-count hardening (spec §9 WS-I)

- **I1 — Recurring count schedules** — spec I1. `CycleCountSchedule` (frequencyDays, nextDueDate, autoCreatePlan); job creates next plan or surfaces "due for count";
  completion restamps nextDueDate. ABC prioritization out of scope. **Effort M · Deps none.**
- **I2 — Count conflict detection** — spec I2, I3. Recompute on-hand at submission and at approval; delta vs task snapshot ⇒ `CONFLICT` status + mandatory reviewer choice
  (recount, or approve with variance recomputed against **current** on-hand — never the stale snapshot); `GET /task/{id}/interfering-movements` from ledger. Freeze-during-
  count recorded as rejected alternative. **Effort M · Deps none.**

### Workstream J — Valuation & costing (spec §10; D-6)

- **J0 — ADR: inventory-owned valuation, configurable costing method** — records D-6 both halves (ownership fixed = inventory-owned; method = configuration via
  `CostingStrategy`), the accounting-domain interface (cost-bearing facts), revisit triggers. Delivered in
  [ADR-0048](../../docs/adr/0048-inventory-owned-valuation-configurable-costing-method.adr.md). **Effort S · Deps none — schedule first (longest coordination lead).**
- **J1 — Costing engine (STANDARD + AVERAGE, config-resolved)** — spec J1 as amended by D-6 and
  [ADR-0048](../../docs/adr/0048-inventory-owned-valuation-configurable-costing-method.adr.md). `CostingStrategy` interface; method resolution per SKU-category with
  deployment default; AVCO incremental recompute on receipts (Odoo `_update_standard_price` mechanics as test vectors); every on-hand-affecting ledger posting gets
  method-derived `unitCost`; method-change history recorded; negative-on-hand costing rule per K1 matrix. FIFO deferred to a v2 story behind the same interface (not in
  this plan's waves). **Effort L · Deps J0, A1.**
- **J2 — Valuation read model & endpoints** — spec J2. Summary `onHandValue`/`unitCostCurrent`; `GET /v1/inventory/valuation` (site-filtered) + as-of variant (pairs with
  A3); new permission `inventory:valuation:view`. **Effort M · Deps J1.**
- **J3 — Cost-bearing facts & adjustment alignment** — spec J3. Cost on `ConsumptionRecordedV1`, `ScrapPostedV1`, receipt facts (additive, schema-versioned);
  `costAtTimeOfAdjustment` sourced from the engine; contract-test alignment with pos-accounting consumers. **Effort M · Deps J1.**
- **J4 — Revaluation workflow** — spec J4. Manual standard-price/AVCO correction with approval tier + `ProductValueChangedV1` fact (Odoo `product.value` analog).
  **Effort M · Deps J1.**
- **J5 — Landed costs (GATED)** — spec J5. Distribute a cost document across receipts by quantity or value, adjust cost basis, emit revaluation fact. **Build only on
  confirmed accounting-side demand** — the issue stays open as a gate marker until accounting rules. **Effort L · Deps J1, gate.**

### Workstream K — Hardening (spec §11)

- **K1 — Negative-stock policy matrix** — spec K1. Documented + enforced per event type (PICK/ISSUE/consumption/TRANSFER-dispatch blocked; SCRAP blocked-with-override;
  ADJUSTMENT_OUT/COUNT_VARIANCE_OUT floor at zero; GOODS_RECEIPT unconstrained); single-posting-path funnel asserted by an ArchUnit rule; per-event-type ITs. **Effort S ·
  Deps none.**
- **K2 — Putaway sublocation strategies** — spec K2. `LAST_USED` + `CLOSEST_AVAILABLE` (capacity-aware, H1 proximity) destination strategies on `PutawayRule`. **Effort S ·
  Deps H1.**
- **K3 — Reservation-consistency sweep** — spec K3. Scheduled verifier: per-allocation CREATED−RELEASED invariant + summary `allocated` reconciliation; report-only +
  alerting, never auto-mutates the ledger. **Effort S · Deps A1.**

---

## 3. Wave schedule and issue map

Waves follow the spec §13 dependency graph. A wave starts when its blocking predecessors merge; stories inside a wave are parallelizable. Issues live in
`louisburroughs/durion-positivity-backend` (labels `domain:inventory` + `odoo-parity`; X1 also `domain:product`, D2 also `domain:accounting`) except J0, which lives in
`louisburroughs/durion` (deliverable is an ADR in `durion/docs/adr/`).

| Wave | Stories → Issues (created 2026-07-22) |
| ---- | --- |
| 1 — decisions & quick wins | J0 → durion#365 · X1 → #1023 · A1 → #1024 · F1 → #1025 · I2 → #1026 · K1 → #1027 |
| 2 — foundation reads & governance | A2 → #1028 · A3 → #1029 · D1 → #1030 · I1 → #1031 · K3 → #1032 · B1 → #1033 |
| 3 — movement structures | B2 → #1034 · C1 → #1035 · C2 → #1036 · H1 → #1037 · E1 → #1038 |
| 4 — flows on foundations | C3 → #1039 · F2 → #1040 · F3 → #1041 · E2 → #1042 · D2 → #1043 |
| 5 — sourcing & resolution | F4 → #1044 · F5 → #1045 · G1 → #1046 · E3 → #1047 · J1 → #1048 |
| 6 — completion | G2 → #1049 · E4 → #1050 · E5 → #1051 · J2 → #1052 · J3 → #1053 |
| 7 — tail | J4 → #1054 · K2 → #1055 · J5 (gated) → #1056 |

Wave-1 scheduling note: J0 and X1 first — they carry the cross-domain coordination lead time (accounting review of the valuation ADR; catalog contract additions) that
gates Waves 3+ (B/E/G) and 5+ (J). A1 is the largest Wave-1 item and the highest-leverage foundation (A2, C2, E1, J1/J2, K3 all consume it).

---

## 4. Cross-domain coordination summary

| Counterpart | What they must do | Carried by | When |
| --- | --- | --- | --- |
| pos-catalog / product domain | Extend product events with UoM conversion set, `trackingLevel`, substitution groups; update contract guide | X1 | Wave 1 |
| pos-accounting / accounting domain | Review + accept J0 ADR (valuation ownership, cost-bearing fact contracts); build shrinkage posting (D2); align GRNI/encumbrance consumers with cost-bearing fact changes (J3); rule on landed-costs demand (J5 gate) | J0, D2, J3, J5 | Waves 1, 4, 6, 7 |
| workexec domain | Consume `BackorderCreatedV1`/`BackorderResolvedV1` for workorder visibility (read-side only; no contract change required from them to unblock G1) | G1, G2 | Wave 5+ |
| pos-location | None — existing location/storage-location events suffice (C/E reuse current replicas) | — | — |

## 5. Verification & rollout notes

- **Odoo-derived test vectors**: AVCO recompute worked examples (J1), orderpoint math incl. lead-horizon and in-progress netting (F2), reservation down-rounding across
  UoMs (B2), transfer conservation invariant (C2), FEFO ordering (E2). Port semantics, not code.
- **Ledger-rebuild drill**: after A1 and again after E1 (per-lot rows), run the summary rebuild against a production-shaped dataset and diff against live summaries — the
  rebuild path is the disaster-recovery story for every read model this plan adds.
- **Fact schema discipline**: all fact changes (A2, D1, G1, J3) are additive with `schemaVersion` bumps; downstream replicas (pos-catalog availability replica,
  pos-workorder listeners) must tolerate unknown fields — add consumer contract tests where they exist.
- **Permission catalog**: new permissions land per story (`inventory:transfer:*`, `inventory:scrap:*`, `inventory:replenishment:manage`, `inventory:lot:manage`,
  `inventory:valuation:view`) and are registered idempotently at startup; update `.business-rules/PERMISSION_TAXONOMY.md` in the same PR as each introducing story.
- **Doc propagation**: `pos-inventory/docs/inventory-ledger-atp.md` updated in A2; `.business-rules/BACKEND_CONTRACT_GUIDE.md` capability sections extended as each wave
  merges (CAP-217 by F-stories; CAP-220 by G-stories; new sections for transfers/scrap/lots/valuation as they land); README per changed behavior.
