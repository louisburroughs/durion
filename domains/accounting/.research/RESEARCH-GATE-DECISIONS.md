# pos-tax Odoo-Parity — Research Gate Decisions (2026-07-20)

For API Orchestrator to consume when executing Waves 2-5 of
`durion/domains/accounting/plan-odoo-parity-pos-tax.md`.
Full reports: `R-T1-provider-comparison.md`, `R-T2-invoice-event-inventory.md` (same scratchpad dir).

## Prior state
Wave 1 (T1 #939, T2 #940, T9a #941) DONE + merged to main (commits a2c890199, 6c6148153; PR #980).
Remaining: Waves 2-5.

## Gate resolutions

### R-T1 (provider) — RESEARCHED, business pick still pending
- No provider chosen yet. Matrix: AvaTax (cert vault ECM, opaque price), Stripe Tax (clean lifecycle+idempotency+public price, NO cert vault), TaxJar (simple Java SDK, no commit/void).
- DECISION: Build T6 **interface + TestModeTaxProvider + invoice lifecycle wiring** provider-agnostic now. **T6 external adapter = BLOCKED** until user picks (Wave 5).
- T3 registry: build MVP regardless (plan D-T1 says it survives either path).

### R-T2 (invoice events) — RESOLVED
- `InvoiceUpdatedV1` is the ONLY invoice domain event; every transition re-emits it w/ different `status`.
- T5b = add `@Nullable taxBreakdown[]` to `InvoiceUpdatedV1` = ADDITIVE, stays V1, **NO InvoiceUpdatedV2 / no dual-publish** (ADR-0044 §3; accounting listener treeToValue tolerates unknown fields).
- T5c: needs NEW persistence sink in pos-accounting (ext_invoice stores only scalar tax today).
- Watch 2nd consumer: pos-workorder InvoiceEventsListener also on invoice.events.v1.
- InvoiceStatus has NO CANCELLED/VOIDED — void = revert to DRAFT. **T6 void() must hook `revert`, not a cancel event.**

### R-T3 (exemptions) — DECIDED: FULL
- Enum: **RESALE, GOVERNMENT, NONPROFIT, AGRICULTURAL, OTHER** (all 5, per plan default).
- **Track certificate effective + expiry dates** at MVP.
- => T3 `V1__exemption_certificate.sql` schema includes effective_from + expires_at columns; expired/missing cert w/ taxExempt=true => tax anyway + exemptionDenied=true flag (plan D-T2).

### R-T4 (filing) — RESOLVED (2026-07-21): Decision A (reconciliation-grade)
- **Authoritative record:** the decision was made and documented in the #966 comment thread —
  blocker statement <https://github.com/louisburroughs/durion-positivity-backend/issues/966#issuecomment-5027666114>,
  A/B decision request + sub-questions 1–4 <https://github.com/louisburroughs/durion-positivity-backend/issues/966#issuecomment-5037257792>,
  and the resolution <https://github.com/louisburroughs/durion-positivity-backend/issues/966#issuecomment-5037473205>.
  This file is a summary of that thread, not the source of truth.
- Note on provenance: the resolution comment adopts Decision A as a **common-sense default to unblock
  T8 now** (AvaTax is already the calculation SoR and all data prerequisites had merged) rather than as
  a Finance sign-off. Moving to Decision B is therefore a business decision that has not been taken —
  not one that was taken and rejected.
- Resolved on #966: **Decision A** — Finance files from the AvaTax provider portal, so T8 v1 is a **reconciliation-grade** liability report (confirms platform-collected == GL-posted). Policy: jurisdiction granularity = state+county+city; accrual basis (recognized at invoice/credit posting); period = tax-effective posting period; credits/refunds netted within jurisdiction+period (gross preserved); exempt rows explicit per jurisdiction.
- **Deferred to Phase 2** (issues filed): filing-grade return-package output #998, cash-basis variant #998, amendment/true-up #998, provider-vs-platform variance workflow #998; plus true per-jurisdiction credit attribution #996, credit-lifecycle-beyond-POSTED #997, real CSV/PDF export rendering #999.
- **Built + merged:** T8 = PR #995 (#966). See `plan-odoo-parity-pos-tax.md` §2 T8 and `plan-odoo-parity-pos-accounting.md` Wave-5 status.

### R-T4 follow-on rulings (2026-07-22)

Recorded while implementing #992 / #996 / #997 (branch `cap/odoo-parity-accounting-followups`):

- **#997 — credit lifecycle beyond POSTED: RESOLVED.** A credit memo counts toward the period it
  **posted** in, whatever status it currently carries. Posting writes a permanent `Dr 2200`; a later
  `POSTED → APPLIED` / `→ VOIDED` transition does not remove that entry, so filtering on the current
  status would drop the credit and surface as GL drift on an otherwise-clean ledger. T8 selects
  `status <> DRAFT` with the posting timestamp in-period. A void that should reverse the tax must post
  its own reversing entry, which then nets in the period of the reversal. (`APPLIED`/`VOIDED` are not
  set anywhere in main code today, so the drift was dormant.)
- **#996 — per-jurisdiction credit attribution: RESOLVED + BUILT.** Attribution is frozen on the credit
  memo at creation (`credit_memo_tax`), derived from the invoice's `ext_invoice_tax` breakdown, with a
  per-jurisdiction residual on the final credit. The read-time pro-rata `TaxCreditAllocator` is demoted
  to a fallback for pre-migration credits. Unattributable reversals are reported explicitly
  (`reconciliation.unattributedCredits`) instead of silently inflating drift.
- **#993 — due-date aging: REQUIRED (build deferred).** `OLDEST_FIRST` should age by invoice **due
  date**, not issue date: an invoice issued earlier with longer terms is less overdue than a later
  net-0 invoice. Implementation spans pos-invoice + pos-domain-events + the pos-workorder consumer, so
  it is scoped to its own change; #993 stays open carrying this ruling.
- **#998 — filing-grade output: STILL GATED.** The R-T4 resolution comment
  (<https://github.com/louisburroughs/durion-positivity-backend/issues/966#issuecomment-5037473205>)
  explicitly lists filing-grade platform output, the cash-basis variant, amendment/true-up, and the
  provider-vs-platform variance workflow as **Phase 2, non-blocking** — i.e. #998's scope was deferred
  by the same decision that unblocked T8, not merely left unaddressed. Nothing was built. Do not start
  #998 without a ruling that moves Decision A → Decision B.
- **T8 v1 policy from that comment is implemented as specified:** jurisdiction granularity
  state + county + city; tax-effective posting period; accrual basis; credits/refunds netted within
  jurisdiction + period with gross preserved; exempt rows explicit per jurisdiction. #996 and #997
  tighten policy item 4 (credit netting) — #996 replaces the pro-rata approximation with attribution
  frozen on the credit, and #997 fixes which credits fall in the bucket.

## Net effect on sequencing
- Wave 2 (T3, T4, T7): UNBLOCKED. T3 = full 5-code enum + expiry.
- Wave 3 (T5a/b/c): UNBLOCKED. T5b simplified to single additive field on InvoiceUpdatedV1.
- Wave 4 (T6 interface+test-mode+wiring, T9 remainder): UNBLOCKED. T6 external adapter deferred.
- Wave 5 (T8, T6 external): BLOCKED on user provider pick (T6-ext) + T4-filing decision (T8).
