# ADR-0057: Analytics Money-Measure Semantics and Ownership

**Status:** ACCEPTED 2026-09-01 — decisions recorded from durion-positivity-backend#1605; amended 2026-09-01 with decision 6 from durion-positivity-backend#1623  
**Date:** 2026-09-01  
**Deciders:** Architecture, Backend Lead, Accounting Domain, Invoicing Domain  
**Affected Issues:** durion-positivity-backend#1605 (premise dispute against plan decision D3), durion-positivity-backend#992 (customer-credit GL lifecycle), durion-positivity-backend#1623 (deposit-take double-count in `invoiced`)

---

## Context

The analytics capability plan (`durion-positivity-backend/pos-mcp-server/docs/analytics-capability-plan.md`) specifies a set of read-only analytics endpoints served by the
existing domain modules. Two of them are money measures over the receivables cycle:

- **E2** — `GET /v1/accounting/analytics/collections`, returning `invoiced`, `collected`, and `collectionRatePct` for a window;
- **E10** — `GET /v1/accounting/payment-applications`, a paged list of applications over an `appliedFrom`/`appliedTo` window.

- **Current State**: Design decision **D3** in that plan placed both endpoints in `pos-accounting`. Its stated rationale was that "pos-invoice holds only `PaymentIntent` and
  `Receipt` — pre-settlement artifacts," so the settlement picture lived wholly in accounting.
- **The Problem**: Issue durion-positivity-backend#1605 disputed that premise and was right to. `pos-invoice` also owns settlement-affecting artifacts:
  `DepositCredit` (`durion-positivity-backend/pos-invoice/src/main/java/com/positivity/invoice/internal/entity/DepositCredit.java`), `DepositCreditApplication` (same package), and `RefundRecord`
  (same package). A deposit credit is cash taken before the settlement event; a deposit-credit application settles invoice balance without cash; a refund is cash out. All
  three bear on whether and how an invoice is settled, so the original premise is factually false. The ownership conclusion it was used to justify is nonetheless correct —
  but on a different reason, and a false premise left in place invites someone to re-derive the opposite conclusion the next time the artifacts are examined.
- **Drivers**: The disputed questions are not local to E2. What `collected` counts, how a correction is dated, whether refunds net into a collection figure, and which module
  may read which artifact are cross-cutting rules that every current and future analytics endpoint must obey. A plan document is the wrong home for them: plans are executed
  and archived, while these rules outlive the wave that produced them. The plan itself now defers to this record (§W2.5, `analytics-capability-plan.md:294-334`).
- **Scope**: All analytics endpoints across the platform that report money measures, present and future — E1 (revenue-by-customer), E2, E10, E13 (customer margin), and the
  W3.1 `groupBy=month|week` periodized variants of the Wave 2 aggregates. The transport rules bind `pos-accounting` and `pos-invoice`.

Related decisions already in force and assumed here: [ADR-0044](0044-platform-event-only-domain-walls.adr.md) (event-only domain walls, replica rules) and
[ADR-0047](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md) (soft immutability plus a reversal lifecycle as accounting's correction model).

---

## Decision

Six decisions, all settled. They govern analytics money measures platform-wide, not just E2 and E10. Decisions 1–5 were recorded from durion-positivity-backend#1605;
decision 6 was added from durion-positivity-backend#1623, the follow-up that #1605's resolution filed on the `invoiced` denominator.

### 1. Analytics ownership follows the ledger, not the artifact

**Decision:** ✅ **Resolved** — **E2 and E10 stay in `pos-accounting`.** Ownership of a money-measure endpoint is determined by which module owns the *ledger effect* being
measured, not by which module happens to hold an artifact that appears in the story.

`collected` measures **A/R relief**, and every input to A/R relief is accounting-owned: `ReceivablePayment`, `PaymentApplication`, `PaymentApplicationReversal`,
`CustomerCredit`/`CustomerCreditTransaction`, plus the `ext_invoice` replica that supplies `invoiced`. `pos-invoice`'s `DepositCredit` and `RefundRecord` are **cash and
liability artifacts**; the ledger entries they cause are posted in accounting. Q18 reinforces the placement independently: it needs the A/R side and the A/P side
(`APPayment`/`APPaymentAllocation`) answered from one module. Moving E2 to `pos-invoice` would force `pos-invoice` to replicate `PaymentApplication` and
`PaymentApplicationReversal` — that is, to replicate the ledger, which is precisely what the module boundary exists to prevent.

**The original premise is withdrawn as false; the conclusion stands.** "pos-invoice holds only `PaymentIntent` and `Receipt` — pre-settlement artifacts" is not true and must
not be cited again in support of this or any adjacent placement. This paragraph is recorded deliberately so that a future reader who rediscovers `DepositCredit`,
`DepositCreditApplication`, or `RefundRecord` finds the correction already made rather than concluding that the placement was decided in ignorance.

### 2. `collected` is cash only — draw-downs are excluded by name and permanently

**Decision:** ✅ **Resolved** — `collected` counts **cash collections only**. Deposit-credit draw-downs (`DepositCreditApplication`, pos-invoice) and customer-credit
draw-downs (`CustomerCreditApplication`, pos-accounting) are **excluded**, permanently, and the exclusion is stated in the endpoint description rather than left implicit.

Counting a draw-down as a collection double-counts cash. Two independent lines of evidence:

- **Deposit path.** A deposit-take order is itself invoiced for the deposit amount:
  `durion-positivity-backend/pos-order/src/main/java/com/positivity/order/internal/service/SalesOrderServiceImpl.java:667-673` sets
  `depositAmount(order.getGrandTotal())` on the invoice-creation request for that same order. The deposit cash has therefore *already* entered `collected` through the
  ordinary invoice → `PaymentSettledV1` → `ReceivablePayment` → `PaymentApplication` path, in the take window. Counting the later draw-down as well would count the same cash
  twice.
- **GL grounding.** Applying a customer credit posts `Dr 2300 Customer Credit Liability / Cr 1200 Accounts Receivable` — a liability relief against an A/R relief, touching
  **no cash account** (`durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md:277-283`, issue durion-positivity-backend#992). A movement that touches no cash account is not a cash
  collection.

E2 already structurally excludes accounting's own `CustomerCreditApplication` draw-downs, so excluding pos-invoice's deposit draw-downs is the **consistent** choice, not a
new exclusion introduced here.

**Non-cash settlement is a separate measure, to be added later.** When it is added it takes its own fields — `nonCashSettled`, `settled`, `settlementRatePct` — and those
fields must be fed from **both** sources together (deposit-credit applications *and* customer-credit applications). A field fed from one source alone is prohibited: it would
read as a complete settlement figure while silently omitting half the non-cash settlement in the platform.

### 3. Corrections are dated at the correction — movement basis, no restatement

**Decision:** ✅ **Resolved** — `collected` = Σ applied amounts **dated in the window** − Σ application-reversal amounts **dated in the window**. A January payment reversed
in March reduces **March**; January is never restated.

Three reasons, each sufficient on its own:

- **Closed-period integrity.** Restating a prior window contradicts the period lifecycle: `PERIOD_CLOSED` and the never-overridable `PERIOD_HARD_LOCKED` exist precisely so
  that a reported figure for a closed period does not change afterwards.
- **Consistency with the accounting correction model.** [ADR-0047](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md) records soft immutability plus
  a reversal lifecycle: a `PaymentApplicationReversal` is a *new record*, and the original application is never mutated. An analytics measure that retroactively unwinds the
  original contradicts the storage model it reads from.
- **Cross-window additivity.** On a movement basis, twelve monthly buckets sum to the annual figure. On an exclusion basis they do not. Wave 3's planned
  `groupBy=month|week` on E2 makes this structural rather than cosmetic — a periodized endpoint whose buckets do not sum is wrong, not merely inelegant.

**Deliberate divergence from E10, recorded so it is not "unified."** E10 keeps its `includeReversed=false` default. A list endpoint answers a point-in-time question ("which
applications currently stand?"), for which excluding reversed rows is the right answer; E2 measures movement over a window, for which it is not. The two behaviours are
correct for their respective questions and must be documented at both endpoints. Any future change that makes E10 movement-based or E2 exclusion-based to "make them
consistent" is a regression against this ADR.

### 4. Refunds are cash-out, measured separately and never netted inside `collected`

**Decision:** ✅ **Resolved** — refunds are reported in their own field, **outside** `collected`, attributed to the **refund-completion window**. Only reversals with
`reversalType == "REFUND"` count. `"VOID"` — a released authorization that never captured funds — must never appear in E2 at all.

- **Heterogeneous shapes forbid a single netted scalar.** Refunds occur invoice-linked, standalone with no invoice at all, and as credit-balance refunds. Folding one scalar
  covering all three into an A/R-relief numerator would assert a debit-credit mapping that no ADR authorizes: a standalone refund relieves no receivable, and a credit-balance
  refund posts against the customer-credit liability (`Dr 2300 / Cr Undeposited Funds 1090`), not against A/R.
- **Netting would double-subtract.** The commonest shape — a refunded invoice payment — produces **both** a `RefundRecord` in `pos-invoice` **and** a
  `PaymentApplicationReversal` in `pos-accounting`. Decision 3 already subtracts the reversal from `collected`; netting the refund as well would subtract the same money
  twice.
- The `VOID`/`REFUND` distinction is carried on the event contract itself:
  `durion-positivity-backend/pos-domain-events/src/main/java/com/positivity/domainevents/payment/PaymentReversedV1.java` documents `reversalType` as "VOID (authorization
  released) or REFUND (captured funds returned)."

### 5. Transport is plain ADR-0044 replication — no amendment to ADR-0044 is required

**Decision:** ✅ **Resolved** — `pos-accounting` **must never call `pos-invoice`** for refund or deposit data. [ADR-0044](0044-platform-event-only-domain-walls.adr.md) R1 (no
domain-to-domain synchronous calls) is absolute on this edge; no scoped exception is sought and none applies. Both data needs are satisfied by event-fed read-only replicas
under R3, over the Kafka backbone under R5, with one owner per fact under R6.

- **Refunds.** Extend the existing `SettlementEventsListener`
  (`durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/service/SettlementEventsListener.java`), which already consumes
  `payment.events.v1` and today drops `payment.payment.reversed` at its event-type guard (that listener admits only `payment.payment.settled` and the settlement-reported
  type, logging and ignoring everything else). Admit the reversed type and persist it into a new replica table **`ext_invoice_payment_reversal`**.
- **Deposit draw-downs.** Require a new **`DepositCreditAppliedV1`** payload, published by `pos-invoice` through its **existing transactional outbox** onto the existing
  `payment.events.v1` topic — a draw-down is a settlement fact and belongs on the settlement topic alongside the events already carried there. Accounting consumes it into a
  new replica table **`ext_invoice_deposit_credit_application`**.

**Both are ordinary ADR-0044 R3/R5/R6 replicas and need no amendment to ADR-0044.** This is stated explicitly because the analytics plan's risk section carries language to
the effect that "an explicit ADR is needed if a replica is required," which has been misread as requiring an ADR-0044 amendment for any new replica. It does not: ADR-0044
already authorizes owner-published, event-fed, read-only replicas as *the* mechanism for cross-domain reads. An amendment is required only for an exception to R1 — a
synchronous call — which this decision explicitly declines to take.

### 6. Deposit-take invoices are excluded from `invoiced` and every revenue-shaped measure

**Decision:** ✅ **Resolved** (durion-positivity-backend#1623) — the invoice a deposit-take order renders for the down-payment itself is a **liability event**, not a revenue
event, and is **excluded** from `invoiced` (E2) and from every measure that sums invoice totals as a revenue proxy (E1 revenue-by-customer, E13 customer margin when it
lands). The exclusion is by deposit-take provenance carried on the invoice document (`depositSourceType` non-null), at the deposit-take document — **never** by netting the
deposit out of the settlement invoice.

The Accounting Domain ruling that settles the question left open by decision 2's evidence:

- **Two economic events, not one.** A deposit-take "invoice" is an advance payment received before the performance obligation is satisfied — a customer-deposit liability
  (contract liability under ASC 606): `Dr Cash / Cr Customer Deposit Liability`, **no revenue recognized**. The settlement invoice is the actual sale — `Cr Revenue` for the
  **full** workorder amount, with the deposit portion relieving the liability rather than A/R. The deposit-take document qualifies as an invoice only in the
  billing/document sense (rendered to the customer, `ExtInvoice.total` populated); it is not an invoice in the revenue-recognition sense.
- **The double-count this fixes.** Both documents finalize and replicate to `ext_invoice` with gross totals, so a $500 deposit on a $2,000 job reported `invoiced = $2,500`
  across the two windows while only $2,000 of work was ever sold — inflating `invoiced`, understating `collectionRatePct`'s complement, and overstating E1 revenue, in a
  direction invisible from the shape of the answer (the same failure mode #1605 was filed about).
- **Exclusion, not netting.** Netting the deposit out of the settlement invoice would understate the settlement document's own gross total and break traceability back to
  the workorder price. The deposit-take document never represented revenue in the first place, so it is the document that leaves the measure.
- **Domain boundary respected.** The ruling is scoped to the accounting/GL meaning of a deposit (liability vs. revenue) — squarely Accounting's authority. The data-model
  implementation (where the provenance lives, how it replicates) remained Order/Invoice's decision, per the issue's own routing.

**Implementation (durion-positivity-backend#1623):** `pos-invoice` stamps `invoices.deposit_source_type`/`deposit_source_id` at from-order creation under the same condition
that registers the `DepositCredit` (backfilled from `deposit_credit.order_id`, unique per taking order); the marker rides `InvoiceUpdatedV1` additively within schema v1
(ADR-0044 §3); `pos-accounting` replicates it to `ext_invoice.deposit_source_type`; E2's `invoiced` and E1's `revenueByCustomer` filter on it, and both endpoint
descriptions state the exclusion explicitly (decision 2's precedent). **Replica caveat:** `pos-invoice` has no facts/replay endpoint, so `ext_invoice` rows replicated
before the enrichment stay unmarked until their invoice next emits; historical windows containing pre-existing deposit-take invoices remain inflated until a replay or
one-shot re-publish lands (the equal-version-applies rule of `ReplicaVersionGuard` exists for exactly that repair).

**Deliberately not extended to E3 payment-lag cohorts.** E3 sums the same `ExtInvoice.total` per cohort, and a deposit-take document (paid instantly) skews the `<=30`
cohort — but the ruling covers revenue-shaped measures, and whether a deposit-take document belongs in payment-*behaviour* cohorts is a different question. Changing E3
requires its own ruling, not a silent extension of this one.

---

## Alternatives Considered

1. **Move E2/E10 to `pos-invoice` now that the settlement artifacts are known to live there.** Rejected: it inverts the dependency. `pos-invoice` would have to replicate
   `PaymentApplication` and `PaymentApplicationReversal` — the ledger itself — to compute A/R relief, and Q18 would then need the A/P side from a third module. The artifacts
   `pos-invoice` owns are cash and liability facts; the *measure* is a ledger effect.
2. **Count deposit-credit and customer-credit draw-downs inside `collected`.** Rejected: it double-counts. The deposit cash is invoiced and collected in the take window
   (`SalesOrderServiceImpl.java:667-673`), and the draw-down posting touches no cash account (`BACKEND_CONTRACT_GUIDE.md:277-283`). It would also make E2 internally
   inconsistent, since accounting's own credit applications are already excluded.
3. **Restate the original window when an application is reversed (point-in-time basis for E2).** Rejected: it rewrites closed and hard-locked periods, contradicts ADR-0047's
   reversal-lifecycle model, and breaks additivity — the failure becomes visible and structural the moment W3.1 adds `groupBy=month|week`.
4. **Unify E2 and E10 on one reversal treatment.** Rejected in both directions: the endpoints answer different questions (movement over a window vs. what currently stands),
   and forcing a single treatment makes one of them wrong. The divergence is documented at both endpoints instead.
5. **Net refunds into `collected` as a single scalar.** Rejected: refund shapes are heterogeneous enough that one scalar asserts an unauthorized debit-credit mapping, and for
   the commonest shape it double-subtracts against the reversal already handled by decision 3.
6. **Have `pos-accounting` call `pos-invoice` synchronously for refund and deposit data ("it is only a read").** Rejected: exactly the case ADR-0044 R1 names and forbids;
   analytics reads are the weakest possible justification for a new wall breach, since they are non-urgent, aggregate, and perfectly served by replicas.
7. **Ship `nonCashSettled` fed from deposit-credit applications alone (the source available first).** Rejected: a settlement figure missing customer-credit applications
   would present as complete while omitting half the platform's non-cash settlement. Both feeds land together or the field does not ship.
8. **Leave all of this in the analytics plan document.** Rejected: these are cross-cutting rules binding endpoints not yet designed; a plan is archived when its wave closes,
   and the questions would then recur endpoint by endpoint.
9. **Net the deposit out of the settlement invoice instead of excluding the deposit-take document (decision 6).** Rejected: the settlement invoice's gross total is the
   traceable record of the workorder price, and it is the document that actually recognizes revenue; mutating its reported total to fix a defect in a different document
   inverts the accounting. The deposit-take document is the one that never represented revenue.
10. **Treat the deposit-take document and settlement as one economic event (count the deposit, net the settlement down).** Rejected by the Accounting ruling on
    durion-positivity-backend#1623: deposit receipt (liability) and settlement (revenue) are distinct events under ASC 606 even though both touch the same workorder, and
    revenue attribution follows the performance obligation, not the cash schedule.

---

## Consequences

### Positive ✅

- ✅ A single, citable answer to "what does `collected` mean" — cash only, movement basis, refunds separate — that new analytics stories inherit instead of relitigating.
- ✅ The false premise behind D3 is recorded as withdrawn alongside the surviving conclusion, so rediscovering `DepositCredit`/`RefundRecord` cannot be used to reopen the
  ownership question.
- ✅ Periodization is safe by construction: because `collected` is additive across windows, W3.1's `groupBy=month|week` is a query change, not a semantic redesign, and
  monthly buckets provably sum to the annual figure.
- ✅ The A/R and A/P sides of Q18 stay in one module, so the weekly cash-in-vs-out question needs no cross-module join at query time.
- ✅ ADR-0044's wall holds with no new exception; the two new replicas are the ordinary mechanism, and the "explicit ADR needed" ambiguity in the plan's risk section is
  resolved in writing.
- ✅ `invoiced` now means revenue-recognizable billing: a deposit liability can no longer inflate it invisibly, and the deposit-take marker on the invoice document and its
  replica gives every future revenue-shaped measure a structural filter instead of a per-endpoint join against `deposit_credit`.

### Negative ⚠️

- ⚠️ `collectionRatePct` is genuinely **understated** in any window where deposit-funded invoices finalize, because the invoice enters `invoiced` in that window while its
  cash entered `collected` in the earlier take window. Mitigation: the exclusion is stated in the endpoint description, and the future `nonCashSettled`/`settled`/
  `settlementRatePct` fields are the designed remedy.
- ⚠️ E2 and E10 treat reversals differently, which looks like an inconsistency to a reader seeing only one of them. Mitigation: both endpoint descriptions must carry the
  divergence and its reason; this ADR is the citable authority when a future reviewer proposes unifying them.
- ⚠️ Refund and deposit measures depend on new event plumbing (`DepositCreditAppliedV1`, and admitting `payment.payment.reversed`), so those fields cannot ship in the same
  increment as the cash-only `collected` fix. Mitigation: the fields are additive; `collected` is correct without them.
- ⚠️ Replicas are eventually consistent, so an analytics figure can lag the owning module briefly. Accepted: analytics is a reporting surface, not a control surface, and
  ADR-0044's outbox and idempotency rails bound the lag and guarantee eventual convergence.
- ⚠️ Decision 6's exclusion is forward-only on the accounting side until a replay lands: `ext_invoice` rows replicated before the deposit-take marker existed stay unmarked
  until their invoice next emits, so historical windows containing pre-existing deposit-take invoices remain inflated. Mitigation: pos-invoice's own store is fully
  backfilled (E1 is historically correct), and `ReplicaVersionGuard`'s equal-version-applies rule already supports the one-shot re-publish that would repair the replica.

### Neutral

- The ownership placement of E2 and E10 is unchanged from what the plan already directed; only the rationale changes. No endpoint moves as a result of this ADR.
- Nothing here alters posting behaviour, the customer-credit lifecycle, or the period lifecycle. This ADR constrains what analytics *reports*, not what accounting *posts*.

### What every future analytics endpoint must do to comply

These are acceptance conditions, not advice. An analytics endpoint reporting a money measure is non-compliant unless it satisfies them.

- **Any endpoint reporting a cash-collection measure** must compute it on the movement basis of decision 3 (applications in window minus reversals in window), must exclude
  both draw-down kinds per decision 2, and must not net refunds into it per decision 4.
- **Any endpoint reporting an invoiced or revenue measure** (any sum over invoice totals) must exclude deposit-take invoices — non-null `depositSourceType` on the document
  or its replica — per decision 6, and must state the exclusion in its endpoint description rather than leave it implicit.
- **E1 — revenue-by-customer** (`GET /invoices/analytics/revenue-by-customer`): `revenue` is an *invoiced* measure, not a collected one, and must be labelled as such so it is
  never read as cash. It excludes deposit-take invoices per decision 6. Its window attribution follows invoice date; it must not be silently reconciled against E2's
  `collected`, since the two answer different questions and will legitimately differ for any deposit-funded or credit-settled invoice. If E1 ever gains a collections or
  net-of-refunds field, that field takes E2's semantics verbatim.
- **E13 — customer margin** (`…/analytics/customer-margin`): `revenue` is invoiced revenue on E1's basis, not collections; margin must never be computed from `collected`,
  and it inherits E1's deposit-take exclusion per decision 6. Refunds must not be netted into its revenue numerator — if refund-adjusted margin is wanted, refunds appear as
  their own component, per decision 4. E13's deferral to Wave 3 for parts cost does not exempt it: whatever cost source is chosen, the revenue side obeys this ADR.
- **Every W3.1 `groupBy=month|week` variant** (E1, E2, E4, E5, E8, and E13 if shipped): each bucket is computed on the same movement basis as the single-window form, so that
  the buckets sum to the single-window figure over the same span. Bucket attribution is by the event's own date — application date for applications, reversal date for
  reversals, refund-completion date for refunds — never by the date of the original transaction being corrected. A `groupBy` implementation that restates earlier buckets when
  a later correction arrives violates decision 3.
- **Any endpoint that later exposes non-cash settlement** must ship `nonCashSettled`/`settled`/`settlementRatePct` fed from deposit-credit **and** customer-credit
  applications together, per decision 2.
- **Any endpoint needing data owned by another module** obtains it through an owner-published, event-fed, read-only replica per ADR-0044 R3/R5/R6. A synchronous cross-domain
  call for analytics data is non-compliant, and needing a replica is not by itself grounds for an ADR-0044 amendment.

---

## Implementation Notes

- **Refund replica**: extend `SettlementEventsListener` (pos-accounting) to admit `payment.payment.reversed` at its event-type guard and persist to
  `ext_invoice_payment_reversal`. The listener already subscribes to `payment.events.v1`, so no new topic, consumer group, or security configuration is needed. Preserve the
  existing dedup/idempotency handling; store `reversalType` so that `VOID` rows are retained but excluded from E2.
- **Deposit draw-down replica**: define `DepositCreditAppliedV1` in `pos-domain-events` alongside the existing payment events, publish it from `pos-invoice` through the
  existing transactional outbox onto `payment.events.v1`, and consume it in accounting into `ext_invoice_deposit_credit_application`. Publishing is by the owner
  (`pos-invoice`) only, per R6.
- **Endpoint descriptions**: E2's OpenAPI description must state that `collected` is cash only, that deposit- and customer-credit draw-downs are excluded, and that reversals
  reduce the window in which they occur. E10's must state that `includeReversed=false` is a point-in-time default and deliberately differs from E2.
- **Testing**: cover the double-count case explicitly — a deposit-take order invoiced for the deposit amount, its cash collection in window A, and its draw-down in window B,
  asserting the cash appears exactly once and only in window A. Cover a reversal spanning windows, asserting no restatement of the earlier window and additivity across the
  two. Cover a `VOID` reversal, asserting it never contributes to E2.
- **Deposit-take exclusion (decision 6)**: shipped with durion-positivity-backend#1623 — `invoices.deposit_source_type`/`deposit_source_id` (pos-invoice V19, backfilled from
  `deposit_credit.order_id`), additive `depositSourceType`/`depositSourceId` on `InvoiceUpdatedV1`, `ext_invoice.deposit_source_type` (pos-accounting V29), and the E1/E2
  query filters. Tests cover the motivating shape: a $500 deposit-take plus a $2,000 gross settlement asserting `invoiced = $2,000` and E1 revenue of $2,000, and a
  deposit-take-only window asserting `invoiced = 0` with a null `collectionRatePct`.

---

## References

- **Related Issues:** durion-positivity-backend#1605 (disputed the D3 premise; source of decisions 1–5), durion-positivity-backend#992 (customer-credit liability lifecycle
  and its GL postings), durion-positivity-backend#1623 (deposit-take double-count in `invoiced`; source of decision 6, ruled by the Accounting Domain)
- **Related Plans:** `durion-positivity-backend/pos-mcp-server/docs/analytics-capability-plan.md` — §W2.4 decision D3 and its 2026-09-01 premise correction,
  §W2.5 decisions D5–D8 (D8 is the plan-side record of decision 6), §W3.1 `groupBy` periodization
- **Related ADRs:**
  - [ADR-0044: Event-Only Domain Walls and Module Communication Policy](0044-platform-event-only-domain-walls.adr.md) — R1 (no domain-to-domain synchronous calls), R3
    (read-only local replicas), R5 (Kafka backbone), R6 (one owner per fact); no amendment required by this ADR
  - [ADR-0047: Ledger Inalterability and Fiscal Positions Are Accounting Non-Goals](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md) — soft
    immutability plus reversal lifecycle, the correction model decision 3 aligns with
- **Related Documentation:**
  - `durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md:277-283` — customer-credit draw-down postings (`Dr 2300 / Cr 1200`; refund `Dr 2300 / Cr 1090`)
  - `durion-positivity-backend/pos-order/src/main/java/com/positivity/order/internal/service/SalesOrderServiceImpl.java:667-673` — deposit-take order sets
    `depositAmount(order.getGrandTotal())` on its own invoice request
  - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/service/SettlementEventsListener.java` — `payment.events.v1` consumer and its
    event-type guard
  - `durion-positivity-backend/pos-domain-events/src/main/java/com/positivity/domainevents/payment/PaymentReversedV1.java` — `reversalType`: `VOID` vs `REFUND`
  - `durion-positivity-backend/pos-invoice/src/main/java/com/positivity/invoice/internal/entity/` — `DepositCredit`, `DepositCreditApplication`, `RefundRecord`

---

## Timeline

- **Proposed**: 2026-09-01 (raised by durion-positivity-backend#1605 against plan decision D3)
- **Accepted**: 2026-09-01
- **Amended**: 2026-09-01 (decision 6 added from durion-positivity-backend#1623)

---

## Changelog

- **2026-09-01**: Initial record — analytics ownership follows the ledger (D3 premise withdrawn, conclusion upheld); `collected` is cash only; movement-basis corrections;
  refunds measured separately; replica-only transport under ADR-0044 (durion-positivity-backend#1605)
- **2026-09-01**: Decision 6 added — deposit-take invoices are liability documents, excluded by `depositSourceType` from `invoiced` and every revenue-shaped measure
  (E1/E2/E13), never netted against the settlement invoice; E3 payment-lag cohorts deliberately out of scope pending their own ruling (durion-positivity-backend#1623,
  Accounting Domain ruling)
