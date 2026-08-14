# ADR-0053: Supplier PRICAT Ingestion, Effective Dating, and Price Precedence

**Status:** ACCEPTED 2026-08-10 — Pricing & Fees and Product & Catalog domain sign-off recorded (durion#371); amended 2026-08-14 (§5 matching path, see Amendments)  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Pricing & Fees Domain, Product & Catalog Domain, Positivity (Integrations) Domain  
**Affected Issues:** durion#371 (investigation), durion#373 (CAP-318), durion-positivity-backend#1224, durion-positivity-backend#1232,
durion-positivity-backend#1308, durion-positivity-backend#1309, durion-positivity-backend#1310

---

## Context

Findings from the durion#371 investigation (as-is analysis in the issue comments, spot-verified against the backend source):

- **Current State**:
  - `pos-price` owns an effective-dated `ProductBasePrice` whose **primary key is `productId`** — saving a price overwrites the row, so base-price history is structurally
    unretainable despite `effectiveFrom/effectiveTo`. Location overrides and customer-tier rules do retain effective windows. Quote order is base price → location override →
    customer-tier discount.
  - `pos-catalog` carries a **second sell-price model** (company-default/location/customer-tier price books, effective-dated rules, MSRP history) plus
    `supplier_item_cost`: **one mutable row per `(supplierId, itemId)`** with optional quantity tiers but no effective dates, no source-document identity, no market scope,
    and no history beyond `updatedAt` (verified in `SupplierItemCostEntity`).
  - ADR-0048 makes `pos-inventory` the sole owner of ledger **valuation** cost — supplier catalog cost and valuation cost are distinct facts.
  - Date semantics differ across modules: pos-price uses `Instant` with exclusive end; catalog MSRP uses `LocalDate` with inclusive end.
  - PRICAT B4.0 is scoped by **buyer account (`buyerParty` + `agencyCode`) and market** — there is no delivery-location parameter. It supplies EAN as primary product
    identity, `supplierCode` (supplier's own code), `xReferenceCode` (UPC/EAN cross-reference), suggested/gross/net values with effective-from dates, tax, and recycling fee.
  - pos-catalog has **no exact EAN/UPC lookup or uniqueness constraint** today (only SKU and `(manufacturerId, manufacturerPartNumber)` are unique).
  - No PRICAT event exists; ADR-0044/0049 fix the naming: topic `supplier.events.v1`, unversioned `eventType`, `schemaVersion`.
- **The Problem**: PRICAT data needs an owner, a retention/effective-dating model, a precedence rule against sell prices, a deterministic matching policy, and an event
  contract — none of which the current `supplier_item_cost` or either sell-price model provides.
- **Scope**: Supplier *catalog/offer* price data (PRICAT). Not in scope: reconciling the duplicate pos-price/pos-catalog sell-price models (pre-existing conflict, §6);
  inventory valuation (ADR-0048); vendor exchange mechanics (ADR-0049..0052).

---

## Decision

### 1. Ownership: four distinct facts, four owners

**Decision:** ✅ **Resolved**

| Fact | Owner | Store |
| --- | --- | --- |
| Received PRICAT document lines (import staging, unmatched quarantine) | `pos-supplier` | Immutable import records tied to the exchange audit (ADR-0049: exchange state, not a business pricing aggregate) |
| Supplier catalog/offer cost (the business fact) | `pos-catalog` | Effective-dated supplier price entries (§2), **replacing** mutable `supplier_item_cost` |
| Inventory valuation cost | `pos-inventory` | Unchanged (ADR-0048); PRICAT never writes valuation |
| Sell prices | `pos-price` (transactional quoting; pos-catalog narrows to list/MSRP reference per ADR-0054) | Unchanged; supplier cost participates in **no** sell-price resolver |

`pos-supplier`'s copy is the received-document record (supports audit, latest-selection at import, re-application after late product matches, and the unmatched worklist).
`pos-catalog`'s entries are the consumable business fact for purchasing, margin context, and reporting.

### 2. Supplier price entries: append-only, effective-dated, source-identified

**Decision:** ✅ **Resolved** — `supplier_item_cost` is replaced by append-only **supplier price entries** in pos-catalog:

- Row identity: own UUIDv7 id; content: `vendorProfileId`, market scope (`country`, `currency`, buyer-account identity), matched `productId`, `supplierCode` (stored as an
  alias, never an identifier), suggested/gross/net values, tax and recycling fee, quantity tiers (child rows), `effectiveFrom` (`LocalDate`, inclusive — the pos-catalog
  convention; PRICAT effective dates are vendor-local dates), source-document ID and date, import-manifest ID, and fetch timestamp.
- Rows are **immutable**; a new vendor price appends a new row. Effective windows are half-open `[effectiveFrom, nextEffectiveFrom)` per (vendorProfileId, productId, scope).
- **Latest** = greatest `effectiveFrom` ≤ today; ties resolve by source-document date, then fetch timestamp. Superseded rows are retained (cost history is a first-class
  query), satisfying the dating rule resolved 2026-08-10 in the supplier architecture document (§12, decision 9).
- The pos-price `ProductBasePrice` overwrite-on-save behavior is noted as a **pre-existing retention defect** but is out of PRICAT scope — supplier data never writes there.

### 3. Market scope, not location scope

**Decision:** ✅ **Resolved** — PRICAT's factual scope is the buyer account + country/market; entries store exactly that scope and **no per-location supplier cost is
invented**. Applicability to Durion locations derives from the vendor profile: locations carrying a delivery-account mapping in a profile (ADR-0050 §5) are covered by that
profile's catalog. Deployments needing different supplier costs per store group model them as separate vendor profiles (ADR-0050 §2), each with its own PRICAT binding.

### 4. Precedence: supplier prices never enter sell-price resolution

**Decision:** ✅ **Resolved**

- Neither the pos-price quote chain nor the pos-catalog price-book resolver reads supplier price entries. This makes the rule resolved in the supplier architecture document (§12, decision 9) — vendor prices never override
  service-provider or location prices — structural rather than procedural.
- PRICAT **suggested retail** may be surfaced as reference-only display data (alongside MSRP history), labeled with its PRICAT source, and is never auto-applied to any sell
  price.
- Quote/margin screens may read the **net cost** of the latest applicable entry as context via a catalog read API; displaying cost context is permitted, feeding it into
  price calculation is not.

### 5. Product matching: deterministic only, quarantine otherwise

**Decision:** ✅ **Resolved**

- Match order, exact matches only: (1) EAN against catalog product codes of type EAN; (2) UPC via `xReferenceCode` against type UPC; (3) `(manufacturerId,
  manufacturerPartNumber)` where the vendor profile maps the supplier to a manufacturer. No fuzzy matching; no auto-creation of products; `supplierCode` is never treated as
  a SKU or used for matching.
- **Prerequisite (new pos-catalog story under CAP-318):** an indexed, exact-match lookup and per-type uniqueness constraint for EAN/UPC product codes — without it,
  deterministic matching is impossible.
- **Where the match is evaluated: a local replica, not pos-catalog** (amended 2026-08-14, see Amendments). pos-supplier resolves codes against an `ext_product_code` replica
  fed by `catalog.product.updated` facts, because ADR-0044 R1 forbids the synchronous call this section originally implied and R3 makes replicas the sanctioned read path.
  Uniqueness stays pos-catalog's invariant; the replica is a copy of it. **Matching is therefore eventually consistent**: a product created shortly before an import may not
  be matchable yet, and that line quarantines rather than matching. This is safe by construction — no line is lost — but it is a behavioural property callers must expect,
  not an implementation detail.
- Unmatched lines stay quarantined in `pos-supplier` (CAP-318's unmatched-lines store) for admin review; when a later catalog fix makes a line matchable, re-application
  happens from the staged import records — no vendor re-fetch required. Replica lag is one such "later fix": a line unmatched only because the replica had not caught up
  matches on re-application, which is what keeps eventual consistency from costing data.

### 6. Duplicate sell-price models: reconciled by ADR-0054

**Decision:** ✅ **Resolved** — The overlap between pos-price (base/override/tier) and pos-catalog (price books, MSRP) was raised as clarification durion#382 and decided
2026-08-10 in **ADR-0054**: pos-catalog owns list/MSRP reference, pos-price owns transactional quoting, each narrowed to a documented non-overlapping role. This ADR was
drafted to be valid under any outcome (supplier cost enters neither resolver, §4) and is confirmed unaffected; PRICAT suggested retail slots into pos-catalog's reference
role per ADR-0054 §1.

### 7. Event contract: manifest-chunked import events

**Decision:** ✅ **Resolved** — Per ADR-0044/0049 naming: topic `supplier.events.v1`, producer `pos-supplier`, consumer `pos-catalog` (pos-price has no consumer under §4;
one may be added by ADR-0049 table amendment if margin features need it).

- **Aggregate** = the import manifest (`importManifestId`, UUIDv7) — one per PRICAT fetch; `aggregateVersion` is the chunk sequence.
- `eventType = supplier.pricecatalog.updated`, `schemaVersion = 1`: **chunk events** carrying manifest metadata (vendorProfileId, source-document ID/date, scope, norm
  version, chunk sequence/count) plus up to N matched line items (identity, values, effective-from, tiers).
- `eventType = supplier.pricecatalog.import.completed`, `schemaVersion = 1`: terminal event with totals (lines fetched/matched/unmatched/duplicate, checksum) enabling the
  consumer's completeness check; missing chunks are recovered via the owner re-emit path on the command topic per ADR-0044 §4 reconciliation — never a synchronous call.
- Chunk size default **500 lines** (~150 KB at ~300 bytes/line, comfortably under broker message limits for dealer catalogs estimated at 5k–50k lines), configurable per
  binding; the default MUST be validated against the first Michelin sandbox pull and recorded here.
- Consumer applies chunks incrementally and idempotently (`processed_events`, ADR-0044 §4); entries become queryable as applied, with the completion event driving the
  completeness metric.

---

## Consequences

**Positive:** supplier cost gets history, source identity, and market scope; the no-override rule is structural; matching is deterministic with an explicit quarantine path;
the event model bounds message size while keeping per-aggregate ordering and a completeness check.
**Negative / accepted:** `supplier_item_cost` and its admin/API surface must migrate to the new entries (pre-production, no compatibility shim per platform policy); the
EAN/UPC uniqueness prerequisite may surface existing dirty catalog data that needs cleanup before CAP-318's consumer lands; two persisted copies (staging in pos-supplier,
business fact in pos-catalog) are accepted for auditability and re-application; matching is eventually consistent against a replica (§5, amended 2026-08-14), so a newly
created product is matchable only after its fact has propagated.
**Follow-ups:** pos-catalog EAN/UPC uniqueness story under CAP-318 (durion-positivity-backend#1232, delivered); chunk-size validation against the Michelin sandbox
(durion#392); the pos-catalog consumer (durion-positivity-backend#1308); product-fact replay so the replica can be seeded (durion-positivity-backend#1309); quarantine
re-application (durion-positivity-backend#1310). The duplicate sell-price reconciliation and the pos-price base-price retention defect are resolved/owned by ADR-0054
(durion#382).

---

## Amendments

### 2026-08-14 — §5 matching resolves against a replica, and is eventually consistent

**What changed.** §5 said matching evaluates EAN/UPC "against catalog product codes" without saying where that evaluation happens, and the natural reading — the one the
story was written against — was a synchronous lookup from pos-supplier into pos-catalog. That reading is not permitted: ADR-0044 R1 forbids domain-to-domain synchronous
calls, R3 makes event-fed local replicas the read path, and `DomainWallsTest` in pos-archunit enforces it. The violation was caught by CI on
durion-positivity-backend#1304, not by review, which is why this is being recorded rather than left as an implementation note.

**Decision.** pos-supplier holds an `ext_product_code` replica fed by `catalog.product.updated`, and matching resolves against it. To make that possible the fact gained
`productCode` and `productCodeType`, additive within its existing schema v2 (ADR-0044 §3). pos-catalog remains the owner of product identity codes and of the per-type
uniqueness constraint; `GET /v1/products/by-code` remains its operator- and consumer-facing contract, and is simply not the path PRICAT matching takes.

**The consequence that matters.** Matching is now **eventually consistent**. A product created shortly before an import is not matchable until its fact has propagated, and
that line quarantines instead. The quarantine is what makes this safe rather than lossy: the line keeps its identifiers and values, and re-application (§5) matches it once
the replica catches up. Two operational obligations follow, and both are tracked:

- The replica is built only from facts published after its consumer starts, and pos-catalog has no replay mechanism today, so on a first deployment the replica is empty and
  every line quarantines as `CATALOG_UNAVAILABLE` — durion-positivity-backend#1309. ADR-0044 §4 already required owners to provide replay; this is the first consumer that
  cannot function without it.
- Re-application currently requires re-running the import; ADR-0053 §5's "no vendor re-fetch required" is not yet true in the implementation —
  durion-positivity-backend#1310.

**What was deliberately not decided here.** Whether a domain module may ever expose a synchronous read across the wall — the question the real-time stock inquiry story
(durion-positivity-backend#1225, durion#374) turns on — is an ADR-0044 amendment, not an ADR-0053 one. It is noted here only because the same wall is the reason this section changed,
and it should be decided once rather than per capability.

## References

- Investigation: durion#371 (as-is findings in comments, spot-verified 2026-08-10)
- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §8.1
- ADR-0044 §3–§4, ADR-0048, ADR-0049 §3, ADR-0050 §2/§5, ADR-0054 (sell-price split, durion#382); `docs/ediwheel/EDIWheel Price Catalog PROD_0.yaml`
