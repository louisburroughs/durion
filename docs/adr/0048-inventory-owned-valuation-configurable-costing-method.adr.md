# ADR-0048: Inventory-Owned Valuation with Configurable Costing Method

**Status:** ACCEPTED  
**Date:** 2026-07-23  
**Deciders:** Architecture, Inventory Domain, Accounting Domain  
**Affected Issues:** durion#365

---

## Context

`pos-inventory` currently has no valuation decision on record: no costing-method policy, no durable ownership ruling for item cost, no on-hand valuation, and no
cost-of-goods basis for inventory consumption. The Odoo parity inventory plan resolves the open question in workstream J (decision D-6), but that ruling needs an ADR before
the costing stories are implemented.

Two coupled decisions must be fixed now:

1. **Ownership** — whether inventory or accounting computes and owns item cost.
2. **Method** — whether the costing algorithm is fixed or configurable.

The plan and spec already constrain the solution:

- workstream J requires `STANDARD` and `AVERAGE` in v1, with `FIFO` deferred behind the same abstraction;
- [ADR-0044](0044-platform-event-only-domain-walls.adr.md) requires cross-domain integration through versioned events, not new synchronous service calls;
- workstream K1 defines the negative-on-hand policy matrix that the eventual costing engine must honor when determining cost on stock-decreasing events;
- the accounting domain needs cost-bearing inventory facts for GL posting, but it must not become a second inventory-cost engine by accident.

This ADR records the durable boundary before story J1 introduces valuation logic.

---

## Decision

Two related sub-decisions are resolved.

### 1. Valuation ownership

**Decision:** ✅ **Resolved** — valuation is **inventory-owned** and this ownership is **not configurable**.

`pos-inventory` computes and stores the method-derived `unitCost` on every on-hand-affecting ledger entry and emits cost-bearing facts on `inventory.events.v1`.
`pos-accounting` consumes those facts to post GL entries and **never** computes item cost independently.

- **Why:** the inventory ledger is the authoritative record of stock movement, so it is the only place where quantity and cost can stay aligned without duplication.
- **Why not configurable:** a configurable owner would require two complete costing engines (inventory-owned and accounting-owned) plus dual event contracts and reconciliation
  rules in perpetuity. That complexity is rejected.
- **Boundary:** if the accounting domain later objects to inventory-owned valuation, that is an ADR amendment or replacement, not a runtime flag.

### 2. Costing method

**Decision:** ✅ **Resolved** — costing method is **configurable** behind a `CostingStrategy` interface.

- **Resolution model:** method resolves per SKU category, with a per-deployment default from `pos.inventory.valuation.default-method`.
- **v1 methods:** `STANDARD` and `AVERAGE`.
- **v2 method:** `FIFO`, added behind the same interface when that story is scheduled.
- **Method change rule:** switching a category from one method to another is a configuration change plus a documented revaluation cut-over; it is not a code change and not a
  hidden compatibility shim.

### 3. Accounting-domain interface

**Decision:** ✅ **Resolved** — inventory publishes cost-bearing facts on `inventory.events.v1`; accounting posts from those facts and does not infer or recalculate unit cost.

The contract surface includes:

- receipts and other stock-increasing facts carrying the inventory-owned cost basis;
- stock-decreasing facts such as consumption and scrap carrying the cost used at posting time;
- revaluation facts for governed cost corrections when workstream J4 is implemented.

This keeps accounting on the consumer side of the boundary and preserves [ADR-0044](0044-platform-event-only-domain-walls.adr.md) event-only integration.

### 4. Negative-on-hand linkage

**Decision:** ✅ **Resolved** — the costing engine introduced by J1 must apply the workstream K1 negative-on-hand policy matrix when deciding whether a stock-decreasing event
may post and, if it may post, which cost basis is attached to that posting.

This ADR does not redefine the negative-stock policy. It links valuation to the already-planned K1 enforcement so cost behavior cannot drift from stock-policy behavior.

### 5. Revisit triggers

**Decision:** ✅ **Resolved** — revisit this ADR only when one of these conditions occurs:

1. the accounting domain rejects inventory-owned valuation as the authoritative boundary;
2. a statutory or customer-mandated requirement demands `FIFO` or specific identification as the only permissible method;
3. a future architecture change proposes moving cost ownership out of the inventory ledger.

Any such change requires ADR revision or supersession, not a feature flag.

---

## Alternatives Considered

1. **Accounting-owned valuation** — rejected: it would separate the cost engine from the authoritative stock ledger, force accounting to maintain its own layers, and create
   duplicate cost logic plus duplicate event semantics.
2. **Configurable ownership** — rejected: it would commit the platform to two parallel costing implementations and two long-lived contracts for the same business fact.
3. **Single hard-coded method** — rejected: parity scope requires `STANDARD` and `AVERAGE` in v1 and a pluggable path to `FIFO`; hard-coding would turn every future method
   into a rewrite instead of an extension.
4. **Deferred decision until J1 implementation** — rejected: J1, J2, J3, and accounting coordination all depend on the ownership and configurability ruling being settled
   first.

---

## Consequences

### Positive ✅

- ✅ Inventory valuation has one owner, one ledger-aligned source of truth, and one outward contract surface.
- ✅ Accounting integration stays event-driven: GL posting consumes cost-bearing facts instead of embedding stock-cost logic.
- ✅ `CostingStrategy` gives a clean extension point for `FIFO` without reopening the ownership decision.
- ✅ Method variability is preserved where it is operationally useful (per category / deployment) without introducing backward-compatibility shims.

### Negative ⚠️

- ⚠️ Inventory now carries the full correctness burden for cost derivation; valuation bugs would propagate into accounting facts until corrected.
- ⚠️ Method changes require explicit operational revaluation handling rather than a casual setting flip.
- ⚠️ Teams that need statutory `FIFO` or specific identification cannot treat `AVERAGE`/`STANDARD` as a permanent substitute; that demand would trigger follow-on work.

### Neutral

- The accounting domain still owns posting rules, accounts, and journals; this ADR only fixes where item cost is computed and published.

---

## Implementation Notes

- **IMP-001**: Story J1 introduces the `CostingStrategy` abstraction and resolves the method per SKU category with `pos.inventory.valuation.default-method` as the deployment
  fallback.
- **IMP-002**: Every on-hand-affecting ledger posting in J1 must receive a method-derived `unitCost`; inventory must not leave cost blank on some movement types and populated
  on others.
- **IMP-003**: Story J3 extends `inventory.events.v1` facts so downstream accounting consumers receive the cost basis used at posting time.
- **IMP-004**: Story J4 is the governed mechanism for revaluation and method-switch cut-over.
- **IMP-005**: Workstream K1 remains the policy source for negative-on-hand behavior; J1 must reference that matrix when attaching cost to exceptional stock-decreasing flows.

---

## References

- **Related Issues:** durion#365 — parity J0 ADR for valuation ownership and costing configurability
- **Related Plans:** `../../domains/inventory/plan-odoo-parity-pos-inventory.md` decision D-6 and stories J0/J1/J3/K1
- **Related Specifications:** `../../domains/inventory/SPEC-pos-inventory-odoo-parity.md` workstream J (§10), workstream K1 (§11), and OQ-6 (§12)
- **Related ADRs:** [ADR-0044: Event-Only Domain Walls](0044-platform-event-only-domain-walls.adr.md), [ADR-0047: Ledger Inalterability and Fiscal Positions Are Accounting Non-Goals](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md)

---

## Timeline

- **Proposed**: 2026-07-23
- **Accepted**: 2026-07-23

---

## Changelog

- **2026-07-23**: Initial ADR recording D-6 for inventory-owned valuation and configurable costing method
