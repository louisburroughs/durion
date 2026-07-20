# Research Gate R-T1 — US Sales-Tax Provider Comparison (accessed 2026-07-20)

Decision-support for T6 external adapter + T3 registry scope. NO final choice — user/business decides.
Plan interface assumes estimate -> commit -> void -> refund w/ idempotent commits.

## Decision matrix (5=strongest)
| Criterion | AvaTax | TaxJar | Stripe Tax |
|---|---|---|---|
| Breakdown fidelity (per-jur+per-line) | 5 summary[]+lines[].details[] | 5 breakdown+line_items[] | 4 tax_breakdown[]+per-line amount_tax |
| Lifecycle completeness | 5 Order/Invoice+Commit+Void+Return | 3 no commit/void; order-push+delete+refund | 4 calc->create_from_calculation->reversal |
| Refund support | 4 ReturnInvoice+neg; MANUAL orig linkage | 4 refunds endpoint, transaction_reference_id | 5 reversal full/partial, explicit original_transaction, immutable |
| Certificate mgmt (replace T3?) | 5 ECM/CertCapture validate+store+audit | 3 exempt-customer+basic upload; no OCR/audit | 1 no cert vault (exempt flags only) |
| Pricing transparency | 2 sales-led, no public price; overage 2-3x | 4 public plans, usage add-ons | 5 public $0.50/txn + $0.05/calc |
| Idempotency | 3 DocCode natural key, no header | 3 transaction_id natural key, no header | 5 Idempotency-Key header + unique reference + immutable |
| Integration effort (Java/Spring) | 3 richest/heaviest REST | 4 first-party Java SDK, simplest | 4 Stripe Java SDK, clean REST, 90-day calc expiry |

## Per-provider lifecycle mapping to TaxProviderClient
- AvaTax: estimate=CreateTransaction(SalesOrder); commit=CreateTransaction(SalesInvoice)+CommitTransaction; void=POST .../void; refund=ReturnInvoice(neg amounts). Only one w/ explicit named void. Orig linkage MANUAL (reference field). Historical rate: TaxOverride.TaxDate=original order date.
- TaxJar: NO estimate->commit model. estimate=/v2/taxes(stateless); commit=POST /v2/transactions/orders; void=DELETE order; refund=POST /v2/transactions/refunds(neg). Adapter must FAKE lifecycle.
- Stripe: estimate=POST /v1/tax/calculations (expires 90d); commit=create_from_calculation(unique reference, after payment); void=full reversal (mode=full); refund=partial reversal (up to 30/sale). Transactions immutable. Strongest linkage (original_transaction id).

## Certificate mgmt => T3 impact
- AvaTax ECM = only true buy-vs-build replacement for T3 registry (validated cert vault + OCR + audit + expiry). Priced separately.
- TaxJar = exempt-customer flags + basic cert upload (exemption_type: wholesale/government/marketplace/other/non_exempt; exempt_regions; expiry req). Reduces but likely NOT fully replaces T3.
- Stripe = NO cert vault => T3 STILL REQUIRED in full.

## Pricing (cite dates in full report)
- AvaTax: opaque, sales-led. 3rd-party est ~$0.49/txn@15k/yr down to ~$0.25@100k+; ECM+returns priced separately. FLAG secondary sources.
- TaxJar: Starter $39/mo, Professional $99/mo (2026, first raise in 6yr). Base ~200 orders/mo. AutoFile $50-55/return.
- Stripe: $0.50/txn (incl 10 calc calls), $0.05/calc beyond. ~$6k/yr @ $100k mo volume.

## Key tradeoff
- Want to REPLACE T3 => AvaTax (only real cert vault). Cost: opaque price, heaviest integration, manual refund linkage.
- Want clean lifecycle+idempotency+price => Stripe Tax. But no cert vault => T3 still built; handle 90-day calc expiry in adapter.
- Want simplest integration+public price => TaxJar. But no commit/void => adapter fakes lifecycle; lighter exemptions.

## Verification FLAGS (unconfirmed this session)
1. Rounding behavior — none confirmed for any (matters for repo's rounding-reconciliation work).
2. AvaTax/TaxJar idempotency — inferred natural-key, no dedicated header.
3. Stripe Idempotency-Key on /v1/tax/* — standard Stripe feature, confirm applies.
4. Stripe cert mgmt absence — confirm before finalizing T3 scope.
5. AvaTax pricing from analyst blogs, not vendor sheet — indicative only.

## DECISION HINGE for planning
T3 registry scope depends directly on provider: AvaTax => T3 MVP-thin (ECM becomes system of record later); Stripe/TaxJar => T3 built in full.
Since provider not yet chosen, build T3 registry MVP (survives either path per plan D-T1) and keep interface provider-agnostic.
