---
title: 'ADR-0060: Catalog Enrichment Matching and Review (pos-catalog Tread-Design Confidence Tiers)'
created: 2026-09-06
status: accepted
---

## ADR-0060: Catalog Enrichment Matching and Review (pos-catalog Tread-Design Confidence Tiers)

**Status:** ACCEPTED 2026-09-06
**Date:** 2026-09-06
**Deciders:** Architecture, Backend Lead, Product Catalog Domain, Positivity (Integrations) Domain
**Affected Issues:** durion-positivity-backend#1645, durion-positivity-backend#1352 (PR #1359), durion-positivity-backend#1638 decision 4, durion#1230

---

### Context

- **Current State**: #1352 (PR #1359, merged 2026-08-17) already ships a working matcher: `TreadDesignEntity` (plus text/image child tables and `product.tread_design_id`),
  a `SupplierCatalogEnrichmentListener` consuming `supplier.catalog.updated`, a vendor-scoped character-trigram Jaccard `TreadDesignMatcher` at a single threshold of 0.50,
  and two pos-catalog reads — `getTreadDesignForProduct` and `listUnmatchedTreadDesigns` (permission `catalog:tread_design:view`) — already in `@durion-sdk/catalog`.
- **The Problem**: Every candidate scoring `≥ 0.50` is auto-attached, and a later import that scores higher against a different design silently re-points the product —
  last write wins, with no review step and no memory of the decision. There is no ambiguity handling: two designs both scoring above threshold for one product just means
  whichever the listener processes last wins. There is no way for an operator to say "not this one" and have that stick.
- **Drivers**: #1638 decision 4 (restated on #1645) requires that ambiguity be parked for review, not guessed at, and that a manual decision never be silently overwritten by
  a later automatic pass. #1230 assumed MKCAT variants carry an EAN identity; they do not — MKCAT is a marketing catalogue of designs, not a priced, identified article. That
  wrong assumption is corrected here rather than carried forward.
- **Scope**: pos-catalog's matching, confidence tiering, and the review/resolve contract. pos-supplier's contribution is unchanged (it already publishes
  `supplier.catalog.updated`) except for an optional `hasUnresolvedImages` filter on its own variant list, which is not part of this ADR's data model and does not touch
  the unmatched-product queue this ADR governs.

---

### Decision

#### 1. Identity: a design, not an article

**Decision:** ✅ **Resolved** — An MKCAT variant is a marketing design, not a priced, uniquely identified article: it carries no EAN. Matching is never keyed on identifier
codes. PRICAT's priced-product set (from CAP-318/ADR-0053) is the **candidate scope** a design's matching is narrowed to — a design is only ever matched against products
the same vendor has actually priced — never the match **key**. This is what #1352 already does structurally; this ADR corrects #1230's EAN assumption and makes the
candidate-scope rule explicit policy rather than an implementation detail nobody wrote down.

#### 2. Confidence tiers and the brand gate

**Decision:** ✅ **Resolved** — Matching becomes a two-step decision instead of one threshold. First, a **brand gate**: the design's normalised brand must equal the
candidate product's normalised brand, or the candidate is not scored at all — brand disagreement is disqualifying, never a partial-credit input to the trigram score.
Second, for every candidate that passes the gate, the existing character-trigram Jaccard score against the design text places it in a tier:

| Score (post brand-gate) | Tier | Action |
| --- | --- | --- |
| `≥ AUTO` (default `0.80`) | AUTO | Auto-attach if unambiguous (§4) |
| `[REVIEW, AUTO)` (default `[0.50, 0.80)`) | REVIEW | Parked for a person; nothing attached |
| `< REVIEW` (default `< 0.50`) | — | No match recorded as a candidate |

The existing single threshold of `0.50` becomes the review floor: nothing that matches today stops matching. Some candidates move from silently auto-attached to
parked for review, which is the point. Both thresholds are configuration (`pos.catalog.enrichment.auto-threshold`, `pos.catalog.enrichment.review-threshold`),
not hard-coded, so a deployment can retune without a code change.

#### 3. Brand normalisation source: YAML, not a table

**Decision:** ✅ **Resolved** — Brand aliases are **not** a database table. They are a YAML map, `pos.catalog.enrichment.brand-aliases` (normalised alias → canonical
brand), loaded as ordinary Spring configuration and consulted by a `BrandNormalizer` alongside the existing lower-case/strip-punctuation/strip-legal-suffix
normalisation. The originally recommended `brand_alias` table (seeded empty, admin CRUD deferred) is explicitly rejected in favour of this simpler, deployment-time-owned
form: alias lists change by configuration change and redeploy, not by an admin UI this pass does not build, and there is no runtime-editability requirement driving the
extra schema and CRUD surface. If a future story needs operators to edit aliases without a deploy, that is a new ADR amendment, not an assumption carried by this one.

#### 4. Ambiguity: park, never guess

**Decision:** ✅ **Resolved** — Ambiguity is decided per matching pass, not per candidate in isolation. A product claimed at **AUTO tier by two different designs** in the
same pass is parked: neither design attaches to it, both candidate rows are recorded, and the product's designs (if any were REVIEW-tier candidates too) all surface on
the review worklist. A design whose own top candidates tie across different brands is likewise parked rather than resolved by an arbitrary tie-break. Only an AUTO-tier
candidate that is the sole AUTO-tier claimant of its product, on a product that is not already MANUAL-attached, is auto-attached.

#### 5. MANUAL is sticky

**Decision:** ✅ **Resolved** — A product whose `tread_design_source` is `MANUAL` (set only by `resolveTreadDesign` ATTACH, §7) is never touched by an automatic matching
pass, regardless of what score a later design earns against it. An operator's decision is the new fact; a later vendor catalogue update does not get to silently
disagree with a person. Reassigning a MANUAL attachment is itself a manual act — a further `resolveTreadDesign` ATTACH — never an automatic re-match.

#### 6. REJECTED designs re-enter matching on content change

**Decision:** ✅ **Resolved** — A design an operator has REJECTED (§7) stays `REJECTED` and out of matching until its `contentHash` changes — i.e., until the vendor sends
different content for it. At that point it re-enters matching exactly as an `UNMATCHED` design would. The alternative (rejection permanent until an operator explicitly
un-rejects) is rejected: a rejection is a statement about the content the vendor sent, not a permanent ban on the design id, and the vendor changing what they said is a
new fact the same way a fresh unmatched design is.

`MATCHED`-by-`MANUAL` designs are the other state left alone by a content-hash change — a MANUAL attachment does not get re-evaluated just because the vendor edited
unrelated copy on the design record it is attached to (§5 governs it, not this rule).

#### 7. Ownership, transport, and the contract surface

**Decision:** ✅ **Resolved** — No change to cross-module transport: pos-supplier publishes `supplier.catalog.updated` (ADR-0049 §3, unchanged); pos-catalog consumes,
matches, attaches, and owns both the product-scoped read (`getTreadDesignForProduct`) and the whole review/resolve contract, per ADR-0044 R1/R3 and ADR-0049 §2 (business
aggregates and their read/write surfaces stay with the domain that computed them; pos-supplier's role ends at publishing the fact). No new cross-module call is added.

`listUnmatchedTreadDesigns` is **kept** as the worklist operationId — already generated in the SDK — and **widened** rather than replaced by a second worklist
operation (the alternative of a new operationId is rejected: nothing forces API consumers to migrate for what is additive data on an existing shape). It gains a
`matchState` filter (multi-value, default `UNMATCHED,REVIEW`) and a `vendorProfileId` filter; each row gains `matchState`, `matchStateAt`, and its top scored candidates
(`productId`, `score`, `tier`).

Two new operations, both under `/v1/catalog/tread-designs`, both `catalog:tread_design:view` for reads:

- `GET /{treadDesignId}/candidates` → `listTreadDesignCandidates` — every scored candidate for one design, `fastRead` event preset (`CATALOG_TREAD_DESIGN_CANDIDATES_LIST`).
- `POST /{treadDesignId}/resolve` → `resolveTreadDesign`, permission `catalog:tread_design:resolve`, `approval` event preset (`CATALOG_TREAD_DESIGN_RESOLVE`). Body:
  `{ action: ATTACH | REJECT | DEFER, productIds?: UUID[], note?: string, deferUntil?: Instant }`.
  - `ATTACH` requires at least one `productId`; sets each product's `tread_design_id` and `tread_design_source = MANUAL`; design `match_state = MATCHED`.
  - `REJECT` clears nothing already `MANUAL`-attached elsewhere; design `match_state = REJECTED`.
  - `DEFER` sets `match_state = DEFERRED` with `defer_until` from the request body, for content an operator cannot yet decide on.
  - 404 unknown design; 409 on `ATTACH` to a product already `MANUAL`-attached to a **different** design (§5); 400 envelope (ADR-0017 codes via `pos-web-common`) on an
    invalid action/field combination (e.g. `ATTACH` with no `productIds`).

`catalog:tread_design:resolve` is registered in `pos-catalog/src/main/resources/permissions.yaml` and is deliberately separate from `catalog:tread_design:view` — viewing
the worklist and asserting a match are different authorities, the same separation ADR-0050/pos-supplier draws between reading and writing.

#### 8. Data model

**Decision:** ✅ **Resolved** — Migration `V20__tread_design_review.sql` in pos-catalog:

- `tread_design` gains `match_state` (`UNMATCHED` | `REVIEW` | `MATCHED` | `REJECTED` | `DEFERRED`), `match_state_at`, `resolved_by`, `resolution_note`, `defer_until`
  (nullable), and an index on `match_state`. Backfill: a design already referenced by a product becomes `MATCHED`; every other existing row becomes `UNMATCHED`.
- `tread_design_match_candidate(id, tread_design_id FK cascade, product_id FK cascade, score numeric(5,4), tier, created_at)`, unique on `(tread_design_id, product_id)` —
  the record of what scored against what, independent of what got attached.
- `product` gains `tread_design_source` (`AUTO` | `MANUAL`, nullable, null exactly when `tread_design_id` is null). Backfill: every existing attachment becomes `AUTO`
  (§5's stickiness rule takes effect only from this migration forward; nothing pre-existing is retroactively treated as an operator decision).

No table for brand aliases (§3). No new table for the matcher's trigram/tokenisation machinery — that stays in-process, unchanged from #1352 except for the tiering and
brand-gate logic layered on top (`TreadDesignMatcher` gains a `BrandNormalizer` and returns a tier per candidate instead of a boolean).

---

### Alternatives Considered

- **`brand_alias` database table with deferred admin CRUD** (the original recommendation) — rejected in favour of YAML config (§3); see that decision for the reasoning.
- **A second worklist operationId** alongside `listUnmatchedTreadDesigns` — rejected (§7): widening the existing, already-generated operation is strictly simpler for SDK
  consumers and there is no compatibility reason to keep two shapes.
- **Rejection permanent until explicit un-reject** — rejected (§6) in favour of content-hash-triggered re-entry, because a rejection is a judgment about specific vendor
  content, not a standing ban on a design id.
- **Arbitrary tie-break on ambiguity** (e.g., highest score wins, most-recent-import wins) — rejected (§4): an automatic tie-break is exactly the guessing #1638 decision 4
  prohibits; parking both is the only option that does not silently pick a side.

---

### Consequences

**Positive:**

- Every attachment is now traceable to either a specific automatic score or a specific operator decision (`resolved_by`, `resolution_note`), closing the audit gap #1352
  left open.
- A MANUAL decision is permanent until another MANUAL decision changes it — no more silent re-pointing by a later import.
- The confidence-tier split means today's matching behaviour does not regress: everything that matches today still matches, some of it moves to a review step instead of
  auto-attaching.
- `listUnmatchedTreadDesigns` stays the one worklist operationId frontend code already calls, so the read-only panel (WS-B) can ship before the resolve action exists.

**Negative / Risks:**

- The review worklist introduces an operational queue that must actually be worked; a deployment that ignores it accumulates REVIEW-tier designs with genuinely
  low-confidence auto-matching in the meantime (this is the same trade #1638 decision 4 explicitly asked for over the status quo).
- YAML-config brand aliases (§3) are not editable at runtime; a rename or new alias requires a deploy. Accepted as out of scope for this pass (see §6 "Explicitly out of
  scope" in the originating plan); an admin-editable table remains available as a future amendment if that need materializes.
- Ambiguity parking (§4) can leave a product with no attachment even when a human would consider one candidate obviously right, until the review worklist is worked —
  accepted because the alternative is an automatic pick that will sometimes be wrong with no record of why.

**Neutral:**

- The trigram scorer and its tokenisation rationale are unchanged from #1352 (`TreadDesignMatcher` javadoc); only the tiering and brand gate are new.

---

### References

- **Related Issues**: durion-positivity-backend#1645, durion-positivity-backend#1352 (PR #1359), durion-positivity-backend#1638, durion#1230
- **Related ADRs**: ADR-0044 §§R1/R3 (event-only domain walls, replica reads), ADR-0049 §2/§3 (pos-supplier module boundary and event contracts),
  ADR-0053 (PRICAT ingestion — candidate-scope source), ADR-0017 (HTTP response code / error envelope conventions), ADR-0025 (permissions.yaml registration)
- **Related Documentation**: `domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md` (CAP-324 rows),
  `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` §11.3
