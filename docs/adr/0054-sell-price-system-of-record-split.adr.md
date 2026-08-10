# ADR-0054: Sell-Price System of Record Split (pos-price Quoting, pos-catalog List/MSRP Reference)

**Status:** ACCEPTED 2026-08-10 — decisions recorded from durion#382  
**Date:** 2026-08-10  
**Deciders:** Pricing & Fees Domain, Product & Catalog Domain, Architecture  
**Affected Issues:** durion#382 (clarification), durion#371 (investigation that surfaced the conflict)

---

## Context

The durion#371 PRICAT investigation surfaced a pre-existing conflict, formalized as clarification durion#382: two sell-price models coexist with overlapping authority.

- **Current State**:
  - `pos-price`: effective-dated `ProductBasePrice` (primary key `productId` — overwrites on save, so base-price history is structurally unretainable), location overrides
    with retained effective windows, customer-tier discounts. Quote order: base price → location override → customer-tier discount.
  - `pos-catalog`: company-default/location/customer-tier **price books** with effective-dated rules, MSRP history, and location overrides. Rule precedence SKU → category →
    global; candidate book precedence explicit book → active location book → company default. The `CUSTOMER_TIER` book scope exists but the candidate resolver never selects
    it.
  - The overlap is acknowledged in the pos-catalog README but no decision named an authority; features could drift between models depending on which module they called.
- **The Problem**: Two sources of sell-price truth with no documented boundary.
- **Constraints honored**: supplier PRICAT cost enters neither resolver (ADR-0053 §4); inventory valuation is pos-inventory's (ADR-0048); `pos-price` remains a **utility**
  module callable synchronously (ADR-0044 §1) — this split preserves that classification.

---

## Decision

Decisions per durion#382 (2026-08-10):

### 1. System-of-record split

**Decision:** ✅ **Resolved** — **`pos-catalog` owns list/MSRP reference; `pos-price` owns transactional quoting.** Every transactional sell-price resolution (quotes,
workorder/estimate pricing, checkout) reads pos-price and only pos-price. pos-catalog's pricing surface is reference data: MSRP history, list prices, and reference series
(including PRICAT suggested retail per ADR-0053 §4) — displayable and reportable, never the source of a transactional price.

### 2. Disposition of the overlap

**Decision:** ✅ **Resolved** — Neither model is retired; each is **narrowed to a documented, non-overlapping role**. The catalog price-book model is scoped to producing
list/MSRP reference prices; any behavior in it that computes or serves a transactional sell price moves to (or is superseded by) pos-price. Module READMEs and the domain
business-rules guides must state the boundary so new stories route pricing work to the correct module.

### 3. CUSTOMER_TIER price-book scope

**Decision:** ✅ **Resolved** — The `CUSTOMER_TIER` book scope is a **feature to finish**, not dead code: the candidate resolver must learn to select customer-tier books,
within pos-catalog's narrowed reference role (customer-tier list/reference pricing). Its relationship to pos-price's customer-tier *discounts* (the transactional mechanism)
must be documented in the implementation story so the two remain reference vs applied, never competing resolvers.

### 4. pos-price base-price retention fix

**Decision:** ✅ **Resolved** — The `ProductBasePrice` retention defect (`productId` as primary key, overwrite on save) is **fixed as part of this reconciliation**: base
prices become history-retaining (own row identity, `productId` + effective window, append-on-change), with quote resolution selecting the applicable window. This also gives
pos-price the same retained-history property the location overrides and ADR-0053 supplier price entries already have.

---

## Consequences

**Positive:** one authoritative answer for "what does the customer pay" (pos-price) and one for "what is the reference/list price" (pos-catalog); pricing stories have a
routing rule; base-price history becomes auditable; the unreachable `CUSTOMER_TIER` scope gets an explicit destiny.
**Negative / accepted:** the narrowing and the base-price schema change are backend work that must be storied and scheduled with the Pricing & Fees and Product & Catalog
domains; until those stories land, the boundary exists on paper and reviewers must enforce it.
**Follow-ups:** implementation stories for (a) the base-price history-retaining schema in pos-price, (b) `CUSTOMER_TIER` candidate-book selection in pos-catalog, and (c) the
README/business-rules boundary documentation in both modules.

## References

- durion#382 (decision comment, 2026-08-10), durion#371 (investigation)
- ADR-0053 (PRICAT — unaffected by this split by design), ADR-0048, ADR-0044 §1
