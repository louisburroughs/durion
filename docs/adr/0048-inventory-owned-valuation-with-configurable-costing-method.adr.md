# ADR-0048: Inventory-Owned Valuation with Configurable Costing Method

**Status:** ACCEPTED 
**Date:** 2026-07-22
**Deciders:** Architecture, Backend Lead, Inventory Domain, Accounting Domain
**Affected Issues:** durion#365 (story J0 of the inventory Odoo-parity plan); implements plan decision D-6

---

## Context

The inventory Odoo-parity program (`domains/inventory/plan-odoo-parity-pos-inventory.md`, spec
`domains/inventory/SPEC-pos-inventory-odoo-parity.md` §10) found that `pos-inventory` has **no
valuation capability at all** (gap G16): no costing method, no on-hand value, and no cost on
consumption. Today `unitCost` is captured on ledger entries only when a source document happens to
carry it, and `costAtTimeOfAdjustment` on cycle-count adjustments snapshots whatever is available.
pos-accounting consumes inventory quantity events (GRNI accrual, encumbrance) but has no item-cost
source either — nobody owns "value of inventory on hand" or "cost of a consumption".

- **Current State**: quantity truth lives in pos-inventory's append-only `inventory_ledger_entry`;
  money-bearing attributes exist only as uninterpreted snapshots.
- **The Problem**: COGS on workorder consumption, shrinkage value on scrap, and balance-sheet
  inventory value all require a costing engine with a defined owner and method.
- **Drivers**: the parity program builds scrap (D-workstream), transfers (C), and replenishment
  (F) flows that emit value-bearing facts accounting must post; Odoo 19's redesign (value carried
  on the movement record, `stock.move.value` + `product.value` history) maps naturally onto
  Durion's ledger-entry model. Spec open question OQ-6 asked whether the recommended design
  ("inventory-owned, AVCO first") could be **made a configuration** — this ADR answers that.
- **Scope**: `durion-positivity-backend/pos-inventory` (engine, read models, facts) and
  `pos-accounting` (consumer of cost-bearing facts). Plan stories J1–J5.

---

## Decision

Two sub-decisions with different configurability rulings.

### 1. Valuation ownership — FIXED: inventory-owned (not configurable)

**Decision:** ✅ **Resolved** — pos-inventory owns valuation. It computes and stores a
method-derived `unitCost` on every on-hand-affecting ledger entry, maintains the valuation read
model, and emits **cost-bearing facts** (`ConsumptionRecordedV1` + cost, `ScrapPostedV1` + cost,
receipt facts + cost, `ProductValueChangedV1` for revaluations) on `inventory.events.v1`.
pos-accounting posts GL from those facts and **never computes item cost**.

- **Why**: the costing engine must sit next to the quantity ledger that feeds it (a FIFO stack or
  AVCO recompute is a function over the movement history pos-inventory already owns); splitting
  them across a domain wall would force accounting to mirror the entire quantity ledger.
  Odoo 19 reached the same conclusion structurally — value lives on the movement record.
- **Why not configurable**: a configurable owner would require building and maintaining two
  complete costing engines (inventory-side and accounting-side) plus dual event contracts in
  perpetuity, for zero operational benefit. Ownership questions are architecture decisions —
  revised by superseding this ADR, not by a flag.

### 2. Costing method — CONFIGURABLE (per SKU-category, with deployment default)

**Decision:** ✅ **Resolved** — the costing **method is configuration**, resolved at posting time:

- Engine built behind a `CostingStrategy` interface (plan story J1).
- Resolution order: per-SKU-category mapping (inventory-maintained table keyed by the catalog
  category replica) → per-deployment default `pos.inventory.valuation.default-method`.
- v1 strategies: `STANDARD` and `AVERAGE` (AVCO with incremental recompute on each receipt —
  Odoo's `_update_standard_price` mechanics as the reference test vectors).
- `FIFO` is a planned v2 strategy behind the same interface (remaining-qty/remaining-value stack
  per receipt entry, Odoo v19 `remaining_qty` design); the J1 interface and schema must
  accommodate it without rework, but it is not built in v1.
- Switching a category's method is a configuration change **plus a documented revaluation
  cut-over** (method-change history recorded: who/when/from/to; opening values restated via the
  revaluation workflow, plan story J4). Never a silent flip.

### 3. Interim rule until the engine ships

**Decision:** ✅ **Resolved** — stories that need a cost before J1 lands (scrap D1, shrinkage
posting D2) use **latest receipt cost** as an explicitly-labeled interim source, and are
re-pointed to the engine in story J3. The interim source must be identified as such in code and
fact payloads must not claim method-derived semantics before J3.

---

## Alternatives Considered

1. **Accounting-owned valuation** (spec J0 option (b)): inventory emits quantity facts with
   acquisition costs only; pos-accounting owns cost layers and COGS. Rejected — splits the FIFO
   stack/AVCO state from the quantity ledger that feeds it, forcing accounting to replicate
   inventory's movement history and creating two sources of truth for "what moved".
2. **Configurable ownership**: an owner flag choosing inventory-side vs accounting-side engines.
   Rejected — both engines would need to exist and stay correct; doubles the contract surface;
   see Decision 1.
3. **No formal valuation (status quo)**: keep opportunistic cost snapshots. Rejected — blocks
   COGS, shrinkage value, and inventory balance reporting; the parity program's D/C/F workstreams
   emit value-bearing facts that need a defined cost source.
4. **FIFO in v1**: rejected for sequencing, not on merits — AVCO covers the POS parts context
   first and ships faster; FIFO remains a v2 strategy behind the same interface.

---

## Consequences

### Positive ✅

- ✅ One owner for item cost; accounting consumes facts and never re-derives value.
- ✅ Method-per-category configurability without code changes (answers OQ-6 affirmatively where
  it is sane).
- ✅ Odoo-analogous design gives ready-made test vectors (AVCO recompute, negative-stock costing).
- ✅ The ledger's append-only history makes as-of valuation and full rebuild cheap (pairs with
  the A-workstream summary read model).

### Negative ⚠️

- ⚠️ pos-inventory takes on money semantics (rounding, restatement) it did not have — mitigated
  by minor-unit/scale conventions already used by its PO entities and by porting Odoo test vectors.
- ⚠️ Cost-bearing fact schema changes ripple to accounting consumers — mitigated by additive,
  schema-versioned fields and consumer contract tests (plan story J3).
- ⚠️ Method switches require a governed cut-over — accepted cost of configurability
  (revaluation workflow J4 + method-change history).

### Neutral

- Landed costs (J5) remain gated on accounting-side demand; this ADR defines where they would
  live (inventory engine adjusts cost basis; accounting posts the delta) without committing to
  build them.
- Per-lot valuation (Odoo v19 `lot_valuated`) is not planned; revisit only with a concrete need.

---

## Implementation Notes

- **Components**: `CostingStrategy` interface + `STANDARD`/`AVERAGE` impls (J1, backend #1048);
  valuation read model + `inventory:valuation:view` endpoints (J2, #1052); cost-bearing facts +
  adjustment alignment (J3, #1053); revaluation workflow + `inventory:valuation:adjust` (J4,
  #1054); gated landed costs (J5, #1056).
- **Configuration**: `pos.inventory.valuation.default-method` (deployment default); SKU-category
  → method mapping table (inventory-owned, admin-gated).
- **Interaction with negative stock**: costing behavior at/below zero on-hand follows the
  parity-K1 policy matrix (#1027); AVCO recompute must define its negative-branch rule (Odoo's
  `_run_fifo`/AVCO negative handling is the reference).
- **Testing**: Odoo-derived AVCO vectors; valuation report ties to Σ(qty × current cost);
  rebuild reproduces identical values.
- **Monitoring**: drift verifier extension (summary value vs ledger-derived value) alongside the
  quantity drift verifier from story A1.

---

## References

- **Related Issues**: durion#365 (J0), backend #1048 (J1), #1052 (J2), #1053 (J3), #1054 (J4),
  #1056 (J5, gated), #1027 (K1 matrix), #1043 (D2 shrinkage posting)
- **Related ADRs**: [ADR-0044: Platform Event-Only Domain Walls](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0047: Accounting Ledger Inalterability Non-Goals](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md)
- **Related Documentation**: `domains/inventory/plan-odoo-parity-pos-inventory.md` (§1 D-6, §2
  WS-J), `domains/inventory/SPEC-pos-inventory-odoo-parity.md` (§10),
  `domains/inventory/comp-odoo-inventory-overview.md` (§8 — Odoo v19 valuation redesign)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | | | |
| Backend Lead | | | |
| Inventory Domain | | | |
| Accounting Domain | | | Required before J1 (#1048) starts |

---

## Timeline

- **Proposed**: 2026-07-22
- **Accepted**: —
- **Implementation Started**: — (J1 gated on acceptance)

---

## Changelog

- **2026-07-22**: Initial draft from plan decision D-6 (OQ-6 ruling)
