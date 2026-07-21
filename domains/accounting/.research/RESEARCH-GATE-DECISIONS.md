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

### R-T4 (filing) — REVISIT DUE (2026-07-21)
- Original deferral: do not build the T8 fidelity decision until Waves 2-4 land.
- **Trigger fired:** Waves 2-4 (and Wave 5 F2/D1) have all merged; T8 (#966) is data-unblocked (#947/#952/#937, AvaTax live). Decision request posted on #966 (2026-07-21): (A) Finance files from the AvaTax provider portal → T8 is a reconciliation/audit report (buildable now, core is R-T4-invariant); (B) Finance prepares filings from platform data → filing-grade precision + period-close alignment (larger scope). Plus sub-questions: reporting-period basis (finalization date vs accounting close), cash vs accrual, credit-memo/refund netting per jurisdiction, jurisdiction granularity. Awaiting Finance's A/B ruling before T8 implementation.

## Net effect on sequencing
- Wave 2 (T3, T4, T7): UNBLOCKED. T3 = full 5-code enum + expiry.
- Wave 3 (T5a/b/c): UNBLOCKED. T5b simplified to single additive field on InvoiceUpdatedV1.
- Wave 4 (T6 interface+test-mode+wiring, T9 remainder): UNBLOCKED. T6 external adapter deferred.
- Wave 5 (T8, T6 external): BLOCKED on user provider pick (T6-ext) + T4-filing decision (T8).
