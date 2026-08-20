# ADR-0055: Per-Product Inventory Quantity Divisibility

**Status:** ACCEPTED **Date:** 2026-08-20 **Deciders:** Architecture, Inventory Domain, Workorder Execution Domain, Product & Catalog Domain **Affected Issues:**
durion-positivity-backend#1365, #1413, #1414, #1415, #1416, #1417

---

## Context

### State at the time of ADR creation

Inventory quantities are integers end-to-end. Workorder part demand is not.

- **Demand side is fractional.** `workorder_part.quantity`, `quantity_issued`, `quantity_consumed`, `quantity_returned` are `numeric(19,4)`, and `IssuePartRequest` validates
  `@DecimalMin("0.0001")`.
- **Supply side is integral.** `inventory_ledger_entry.change_in_quantity` and `quantity_after` are `integer`; so are the reservation, allocation and backorder entities and
  columns, `ReservationOutcomeV1.requiredQuantity`, the three quantity fields on `InventoryAvailabilityUpdatedV1`, and the `ext_inventory_availability` replicas in pos-order,
  pos-workorder and pos-catalog.
- **pos-order is unaffected** — `SalesOrderLine.quantity` is `int`.

The seam is currently papered over. `WorkorderPartUsageServiceImpl.reservableQuantity` rounds a fractional issue **up** to the nearest whole unit before publishing the
reservation request. Issuing `0.5` reserves `1`, holding half a unit against demand nobody asked for. The rounding direction is the safe one — reserving less than what
physically left the shelf is the unrecoverable error — but the behavior is still wrong, and it was introduced as an acknowledged workaround rather than a design.

### The Problem

Two facts about the platform are individually true and jointly incompatible:

1. **The supply side already refuses fractional postings, deliberately, in three places.** `ReceivingServiceImpl.toWholeLedgerQuantity`, `AsnServiceImpl` and
   `ReturnServiceImpl` all call `intValueExact()` on the base quantity and throw on a fraction.
2. **The catalog already declares that some products are divisible.** `product_uom.precision_scale` exists specifically to record a non-zero base-unit scale,
   `UomConversionServiceImpl.baseScale()` honors it via `setScale`, and `UomConversionServiceImplTest` carries a fixture for a base-`LB`-at-scale-2 product asserting
   `1 BAG → 1.01 LB`.

Feed that existing scale-2 test product through receiving and it throws. **The contradiction is latent in `main` today**, waiting for the first product with a non-zero base
scale to be seeded.

### Drivers

- **Bulk-dispensed stock is on the roadmap** (confirmed by the product owner): fluids dispensed from bulk, refrigerant by weight, cut-to-length hose.
- **A bulk SKU already exists in the seeded catalog.** `PARK-387TC-4-FT` (Parker Hydraulic Hose) carries `unit_of_measure = 'FT'` with no package qualifier — correctly
  modelled as divisible. Issuing 3.5 ft of it today hits the ceiling workaround and reserves 4.
- **Fractional demand in practice describes time and money, not stock.** `estimate_item.quantity` is a single `numeric(19,4)` column shared between `PART` and `LABOR` rows.
  Labor needs 1.5 hours; parts inherited the scale by sharing the column. The scale on `workorder_part` is an artifact of that sharing, not evidence of a fractional-stock
  requirement.
- **Every SKU in the catalog today is integral.** Package sizes are modelled as separate SKUs (`VALV-ATF-ML-QT` 1qt vs `VALV-ATF-ML-GA` 1gal). The one exception is the hose
  above.
- **The reverse decision is expensive.** Deciding "inventory is integral" as a permanent domain rule, then reversing it when bulk arrives, is a reversal across four modules
  rather than a widening.

### Scope

`pos-inventory` (ledger, reservation, allocation, backorder, availability), `pos-workorder` (part demand and issue), `pos-catalog` (divisibility declaration), and the
`ext_inventory_availability` replicas in pos-order, pos-workorder, and pos-catalog.

---

## Decision

Inventory quantity divisibility is a **per-product property declared by the catalog**, not a platform-wide type choice.

### 1. Divisibility is per-product, declared by `product_uom.precision_scale`

**Decision:** ✅ **Resolved** — a product's stock divisibility is the `precision_scale` of its `BASE` row in `product_uom`, owned by `pos-catalog` and replicated to consuming
modules.

- `precision_scale = 0` → stock is **integral**. Fractional quantities are rejected.
- `precision_scale > 0` → stock is **divisible to that scale**. Quantities carrying no more decimal places than declared are accepted; quantities exceeding the declared scale
  are rejected.

This is not a new mechanism. `ProductUomType.BASE` already documents the field as _"Declares the product's base (stocking) UoM precision; factor must be 1 and the code should
match the product's `unitOfMeasure`"_, `UomConversionServiceImpl` already honours it, and it is already replicated to `ext_product_uom` in pos-inventory and pos-order. This
ADR wires the declaration through to the ledger, which is the one place it was never connected.

### 2. Undeclared means integral

**Decision:** ✅ **Resolved** — a product with **no `product_uom` rows** is treated as `precision_scale = 0`.

`product_uom` is currently unpopulated: there are no `INSERT INTO product_uom` statements anywhere in the repository, and no `ext_product_uom` seed in pos-inventory. The
conversion subsystem is built and empty. Defaulting to integral is what makes it safe to install the per-product rule before any seeding exists, and it preserves today's
behavior exactly for every existing product.

### 3. Enforcement is symmetric across demand and supply

**Decision:** ✅ **Resolved** — the same declared scale governs both sides of the ledger.

The existing `intValueExact()` guards in receiving, ASN, and returns are **re-driven off `precision_scale`**, not removed. They are correct for the products they currently
protect; what changes is that the rule becomes derived rather than hard-coded. The demand side (work-order part creation and issue) gains the equivalent check, which it lacks
today.

### 4. Quantity types become decimal-capable, gated by the declaration

**Decision:** ✅ **Resolved** — the ledger, reservation, allocation, backorder, and availability quantities carry a decimal type, and the declared scale — not the type — is
what constrains a given product.

Widening the type does not make stock divisible. A product declaring scale 0 is rejected at a fraction regardless of what the column can hold. The type change exists so that a
product declaring scale > 0 has somewhere truthful to land.

### 5. The unit of measure travels with the work-order line

**Decision:** ✅ **Resolved** — the work-order part line carries the unit in which its quantity is expressed, and conversion to base happens through the existing
`DocumentQuantityConverter`.

`workorder_part` has no UOM column today, so a line cannot express "4.5 QT" — its quantity is a bare number implicitly in base units. `DocumentQuantityConverter` is already
wired into purchase-order, ASN, receiving, and return lines; the work-order issue path is the gap. Reservation conversion uses the existing `DOWN` rounding mode, so a
reservation never over-promises.

### Boundaries

- This ADR does **not** make inventory quantities universally decimal. It makes them decimal _where a product says so_.
- It does **not** change `SalesOrderLine.quantity` in pos-order, which remains `int`. Sales-order demand is out of scope until a divisible product is sold through that path.
- Partial-container policy is settled separately and recorded here as context: when a technician opens an inventory-tracked container and uses part of it, **the whole
  container is issued and billed**. That policy is what makes integral stock workable for packaged goods, and it is why this ADR is not a license to make everything
  fractional.

---

## Alternatives Considered

1. **Option A — uniform decimal.** Widen the whole chain to `BigDecimal` unconditionally. **Rejected:** it deletes three deliberate, correct guards in receiving, ASN, and
   returns in order to serve a minority of products; it is larger than it first appears because the ledger itself (`inventory_ledger_entry.change_in_quantity` /
   `quantity_after`) must widen too; and it removes the platform's ability to say "this product is not divisible" at all. The capability it buys is real, but scoping it costs
   nothing extra and preserves an invariant on which 99% of the catalog depends.

2. **Option B — uniform integral.** Reject fractional issues for any part carrying a `productEntityId`. **Rejected:** correct for every SKU in the catalog except one, and
   correct for the business as it operates today — but bulk-dispensed stock is confirmed on the roadmap and `PARK-387TC-4-FT` already exists. Adopting B would mean reversing
   it, and a reversal across four modules is materially worse than a widening. B's _invariant_ is preserved by this decision; what is rejected is hardcoding it as a permanent
   domain rule.

3. **Option C-prime — fine-grained integer base units.** Choose base units small enough that every realistic dispense is integral (fluids `ML`, refrigerant `GRAM`, hose `MM`),
   keeping the integer ledger. **Rejected**, despite being genuinely attractive — it avoids all schema and payload changes. It fails on five counts: it contradicts
   `precision_scale`, whose entire purpose is to declare non-zero base scales, reducing a load-bearing field to permanent dead weight; it breaks bulk cycle counting, since
   `SubmitCountRequest.actualQuantity` is `Integer` and no counter reports a drum's contents in millilitres; it renders every stored quantity and every operational query
   unreadable (`208198` for a drum); it forces an irreversible per-category base-unit choice now, under uncertainty; and it still requires the work-order UOM work anyway, so
   it does not even avoid the change it was chosen to avoid. A cross-location availability aggregate at the `ML` base also approaches the 32-bit `int` range for a
   several-hundred-site chain.

4. **Catalog-encoded divisibility.** Express divisible products as distinct SKUs at each dispense size, as the catalog already does for package sizes. **Rejected** for
   genuinely bulk stock: it works when the shop holds discrete sealed containers, which is why it is the right model for the existing oil and coolant SKUs, but a drum drawn
   down continuously has no finite set of sizes to enumerate.

---

## Consequences

### Positive ✅

- **Resolves a live contradiction.** The scale-2 fixture in `UomConversionServiceImplTest` and the `intValueExact()` guard in `ReceivingServiceImpl` cannot both be right
  today. After this decision, they can.
- **Preserves the invariant for the products that need it.** Every currently seeded SKU remains integral and enforced, with no change in behavior.
- **Removes a known-wrong workaround.** The `RoundingMode.CEILING` in `reservableQuantity` — which reserves 1 for an issue of 0.5 — is deleted rather than documented.
- **Makes the decision reversible in the cheap direction.** Adding divisibility to a product is a data change. Under Option B, it would have been a code change across four
  modules.
- **Uses machinery already built.** `product_uom`, `UomConversionService`, `DocumentQuantityConverter` and the `ext_product_uom` replicas all exist and are already
  event-replicated in the manner ADR-0044 requires.
- **Gives correctness a single source.** Divisibility is declared once by the domain that owns product identity, rather than inferred independently by each consuming service.

### Negative ⚠️

- **A breaking payload change across three replicas.** `ReservationOutcomeV1` and `InventoryAvailabilityUpdatedV1` are consumed by pos-order, pos-workorder, and pos-catalog.
  _Mitigation:_ staged rollout with schema-version handling settled before the widening ships; note the availability contract is already mixed-width (its v2 forecast fields
  are `long` alongside the older `int`s), so it is not a pristine surface being disturbed.
- **pos-catalog is easy to miss.** It consumes `InventoryAvailabilityUpdatedV1`, is outside the #1315 gate work, and has no test coverage tying it to the contract.
  _Mitigation:_ explicit acceptance criterion on the widening issue.
- **Correctness now depends on data that does not exist yet.** With `product_uom` unpopulated, a mis-seeded `precision_scale` becomes a correctness bug rather than a cosmetic
  one. _Mitigation:_ the undeclared-means-integral default fails safe; #1417 corrects the ambiguous `unit_of_measure` seed values before any `product_uom` seeding is authored
  against them.
- **Bulk stock introduces shrinkage that the platform does not model.** A drum has evaporation, residue and meter variance a sealed bottle does not, and
  `NegativeStockPolicy.forEventType` currently floors `COUNT_VARIANCE_OUT` and `ADJUST_CYCLE_COUNT` at zero, absorbing small real losses silently. _Unmitigated — see Open
  Questions._
- **UOM-aware display becomes mandatory across a wide surface.** Roughly 94 quantity-bearing DTOs carry no `unitOfMeasure`. _Mitigation:_ The availability path already carries
  UOM end-to-end, so the #1315 gate is unaffected; the remainder is internal-ops surface that can be staged. Note that this cost is incurred by supporting bulk at all, not by
  this particular encoding — Option A would incur it identically.
- **Rounding does not disappear; it relocates.** Conversion uses `HALF_UP` generally and `DOWN` for reservations, so a reservation can under-hold by up to one unit of the
  declared scale. At scale 0 that is today's behaviour; at higher scale it shrinks proportionally but never reaches zero.

### Neutral

- **Valuation interacts with this decision but is not changed by it.** ADR-0048 makes `pos-inventory` the owner of method-derived `unitCost` on every on-hand-affecting ledger
  entry. A decimal quantity multiplies into that cost basis; the ownership boundary and costing-method configurability are untouched.
- **The integer read model is already 64-bit.** `inventory_stock_summary` quantities are `bigint`, and the availability math is `long` throughout, with all narrowing done
  through checked `Math.toIntExact` that throws rather than wraps. The widening is narrower in practice than the issue's initial framing suggested.
- **The `estimate_item` shared PART/LABOR quantity column is left as-is.** Splitting it is defensible but out of scope; labor rows remain legitimately fractional and are
  unaffected by the part-side rule.

---

## Implementation Notes

Staged deliberately so that the reversible, non-breaking work lands first.

- **Stage 1 — durion-positivity-backend#1413.** Install the per-product invariant reading `precision_scale` (defaulting to 0), enforce at estimate-item creation and part
  promotion with issue as backstop, and delete the `CEILING` workaround. No schema change, no payload change.
  - **Critical constraint:** this stage must **not** hardcode `productEntityId != null → integral`, and must not encode "inventory-tracked parts are always whole" as a domain
    rule. It must read the declared scale. Getting this wrong turns Stage 2 into a reversal rather than a widening — the same trap the ceiling workaround fell into.
  - **Gate placement is load-bearing.** Gating only at issue leaves `outstandingQuantity = quantity - quantityIssued` permanently non-zero for a fractional line, stranding the
    workorder in `AWAITING_PARTS` indefinitely.
- **Stage 2 — #1414.** Widen the ledger, reservation chain, availability contract and three replicas; re-drive the `intValueExact()` guards off `precision_scale`. Breaking
  payload change: rollout ordering must be stated before it ships.
- **Stage 3 — #1415.** UOM column on the work-order part line, UOM on the reservation command, `DocumentQuantityConverter` wired into the issue path. Required under any
  option.
- **Stage 4 — #1416.** UOM-aware quantity display across the ~94 DTOs, and decimal-capable cycle counting (`SubmitCountRequest.actualQuantity` and `CountEntryResponse.*` are
  `Integer` today).
- **Prerequisite — #1417.** Correct the ambiguous `unit_of_measure` seed values (five 5-quart jugs declared as `QT`) before `product_uom` rows are authored against them, since
  `ProductUomType.BASE` requires the base code to match the product's `unitOfMeasure`.

**Testing strategy.** The existing scale-2 fixture in `UomConversionServiceImplTest` should round-trip through receiving without throwing — that is the single clearest proof
that the contradiction is resolved. `PARK-387TC-4-FT` is the natural first real product to seed `product_uom` for and the natural first integration subject.

**Validation before rollout.** Confirm against alpha/production data that fractional `quantity_issued` values for parts with a `productEntityId` are absent or negligible, and
that fractional `estimate_item.quantity` values concentrate on `LABOR` rows. A material fractional population on `PART` rows clustered on one product family would indicate
that the family needs `precision_scale > 0` seeded, not that the decision is wrong.

---

## Open Questions

- **Bulk shrinkage tolerance.** Must be settled before Stage 2, not before Stage 1. A drum's evaporation, residue and meter variance are real, and
  `NegativeStockPolicy.forEventType` currently makes `COUNT_VARIANCE_OUT` and `ADJUST_CYCLE_COUNT` `FLOOR_AT_ZERO` — downward variance truncates silently. The question is what
  tolerance is acceptable and whether it should be visible rather than absorbed.
- **Billing confirmation from the pricing domain.** Work-order invoicing reads `part.getQuantity()` rather than `quantityIssued`, so constraining quantity also constrains the
  billed amount. Under the "bill the whole container" policy this is the desired behaviour, but it is a pricing consequence of an inventory decision and warrants explicit
  confirmation rather than assumption.

---

## References

- **Related Issues**: durion-positivity-backend#1365 (decision and analysis), #1413 (stage 1), #1414 (stage 2), #1415 (stage 3), #1416 (stage 4), #1417 (seed-data
  prerequisite), #1363 (introduced the ceiling workaround), #1361 (Phase 1 reservation contract), #1315 (owned-ATP gate)
- **Related ADRs**: [ADR-0044: Platform Event-Only Domain Walls](0044-platform-event-only-domain-walls.adr.md) — §4 commands, §6 event-fed replicas;
  [ADR-0048: Inventory-Owned Valuation with Configurable Costing Method](0048-inventory-owned-valuation-configurable-costing-method.adr.md) — cost basis that decimal
  quantities multiply into
- **Related Documentation**: [pos-inventory README](../../durion-positivity-backend/pos-inventory/README.md),
  [pos-workorder README](../../durion-positivity-backend/pos-workorder/README.md), [Error Envelope](../../durion-positivity-backend/docs/ERROR_ENVELOPE.md)

---

## Sign-Off

| Role                       | Name | Date | Notes                                              |
| -------------------------- | ---- | ---- | -------------------------------------------------- |
| Architecture               |      |      |                                                    |
| Inventory Domain           |      |      |                                                    |
| Workorder Execution Domain |      |      |                                                    |
| Product & Catalog Domain   |      |      |                                                    |
| Pricing & Fees Domain      |      |      | Billing coupling confirmation (see Open Questions) |

---

## Timeline

- **Proposed**: 2026-08-20
- **Under Review**: —
- **Accepted**: —
- **Implementation Started**: —
- **Implementation Complete**: —
- **Deployed to Production**: —

---

## Changelog

- **2026-08-20**: Initial draft. Records the Option D decision from durion-positivity-backend#1365, superseding the Option A / Option B framing in that issue.
