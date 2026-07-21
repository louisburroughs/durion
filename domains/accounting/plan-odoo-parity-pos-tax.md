## Odoo Parity Plan — pos-tax (and the tax data path through pos-invoice / pos-accounting)

> Status: ACCEPTED · Created 2026-07-17 · Branch: `claude/pos-accounting-odoo-parity-idfuje`
>
> Goal: give the platform the tax-engine guarantees Odoo 19 provides — line/total rounding consistency, refund-aware calculation, persisted jurisdiction breakdown, reportable
> exemptions, freeze-after-finalize, provider document lifecycle — while keeping Durion's deliberately different architecture: a stateless, jurisdiction-first,
> provider-delegating US sales-tax service. Companion plan: `plan-odoo-parity-pos-accounting.md` (workstream D there consumes T5 here).
>
> Sources: `comp-tax-engine-deep-dive.md`, `comp-vs-pos-tax-comparison.md`, code survey of `pos-tax`, `pos-tax-common`, `pos-invoice`, `pos-workorder`, `pos-domain-events`
> (2026-07-17), ADR-0021, ADR-0044.

---

## 0. Ground rules for the executing agent team

1. **Keep the mission.** pos-tax stays a **stateless-by-default, synchronous, internal-only utility service** (ADR-0021, ADR-0044 utility list): no gateway route, Eureka
   registration disabled, strict fail-fast address validation (400s are deterministic data errors — callers must not auto-retry). Do NOT turn it into an Odoo-style stateful
   ledger-embedded engine; its output is amounts + breakdown, never journal items. Where this plan adds persistence (T3 exemptions, T6 provider log) it is narrow and justified
   per story — challenge any scope creep beyond those tables.
2. **One calculator, one runtime.** Never re-derive tax client-side or in calling services; Odoo's dual Python/JS parity burden is the cautionary tale. pos-workorder's
   divergent hardcoded fallback (8.25%) is a bug this plan removes, not a pattern.
3. **Contract discipline**: `pos-tax-common` DTO changes are additive only (shared by pos-tax, pos-invoice, pos-workorder); event schema changes in `pos-domain-events` are
   additive within `.v1` else a `.v2` with dual-publish (ADR-0044 §3). ADR-0017/0042 for controllers/OpenAPI; ADR-0013/0027 UUIDs; ADR-0018/0024 audit fields; `@EmitEvent` +
   `EventTypes` registration; permissions via `pos-tax/src/main/resources/permissions.yaml` (`tax:calculate`, `tax:mode:view` exist).
4. **Money math**: BigDecimal, HALF_UP at currency scale, raw-then-round with deterministic delta distribution (Odoo's rule 3 takeaway) — rounding is decided here, not by
   pricing (pricing domain agent MUST NOT decide rounding per its charter).
5. **Non-goals (record, do not build)**: price-included ("gross") pricing, tax-on-tax cascading topology, cash-basis exigibility, VAT repartition/tax grids, cash rounding
   (0.05), multi-currency tax, withholding. These are the ~5,000 lines of Odoo machinery a US auto-service platform does not need (comp doc §6). If a US edge case needs one
   (some states tax fees), handle it via provider configuration, not engine features.

**Odoo-superior pieces we adopt** (from comp-vs-pos-tax §6): rounding reconciliation, refund-aware semantics, persisted per-line jurisdiction breakdown,
exemption-as-reportable-zero-tax, freeze-after-finalize. **Durion-superior pieces we keep**: jurisdiction-first model, provider delegation, statelessness, resilience4j retry,
single runtime.

---

## 1. Gap register (evidence-based)

| #     | Gap                                                                                                                                                              | Current state (evidence)                                                                                                                                                      | Story    |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| T-G1  | Σ lineItemTaxes ≠ totalTax possible; two independent rounding passes                                                                                             | `TestModeTaxCalculator` rounds per-jurisdiction on aggregate base (:104,120,136) and per-line with combined rate (:163-173); no reconciliation; tests use clean fixtures only | T1       |
| T-G2  | `effectiveTaxRate` divides by unfiltered subtotal → wrong with exempt lines                                                                                      | `TestModeTaxCalculator:64-66`                                                                                                                                                 | T1       |
| T-G3  | `customerId`, `transactionDate`, `taxCategory` accepted but never consumed                                                                                       | dead in both calculator paths                                                                                                                                                 | T2/T3/T7 |
| T-G4  | Exemptions: boolean only; no reason/certificate; no per-line zero-rate jurisdiction rows                                                                         | filter at `:97-99`, zeroed at `:171-172`; no per-line×jurisdiction matrix exists                                                                                              | T3       |
| T-G5  | No refund/credit calculation mode; `@Positive` validation rejects negatives                                                                                      | `TaxLineItem:54,62`                                                                                                                                                           | T4       |
| T-G6  | Breakdown discarded: pos-invoice stores one scalar `tax_amount`; events carry one scalar                                                                         | `TaxServiceClient.calculateTax` returns `getTotalTax()` only (:81); `Invoice.java:67-68`; `InvoiceUpdatedV1.tax`                                                              | T5       |
| T-G7  | pos-accounting credit memos hardcode 10% tax                                                                                                                     | `CreditMemoServiceImpl:149` (fixed by accounting plan D1, fed by T5)                                                                                                          | T5       |
| T-G8  | Provider path is a stub: generic URL, no commit/void lifecycle, no idempotency, retries 4xx                                                                      | `ExternalTaxServiceClient`, `TaxConfiguration:56-66` (no retry predicate)                                                                                                     | T6       |
| T-G9  | Flat global rates; `postalCodeMapping` config declared but never read; no effective dating                                                                       | `TaxProperties`, `application.yml:56-59`                                                                                                                                      | T7       |
| T-G10 | No in-platform tax liability reporting possible (breakdown not persisted)                                                                                        | consequence of T-G6                                                                                                                                                           | T8       |
| T-G11 | Caller inconsistencies: pos-workorder fallback 8.25% + silent `testMode=true`; `TaxClient` sends no auth headers; `EstimateItem.taxCode` captured but never sent | `EstimateServiceImpl:67`, `TaxClient:23-28`                                                                                                                                   | T9       |

---

## 2. Stories

### T1 — Rounding correctness: one matrix, one reconciliation

The core computational fix; everything else layers on it.

- **Design**: restructure `TestModeTaxCalculator` around a **per-line × per-jurisdiction raw matrix**: for each non-exempt line and each applicable jurisdiction, compute the
  raw (unrounded) tax; then a single rounding stage — round jurisdiction totals from raw sums, round line totals from raw sums, and distribute any residual cents
  deterministically **largest-raw-amount-first** (Odoo `_distribute_delta_amount_smoothly`) so that these invariants always hold:
  `Σ lineItemTaxes == totalTax == Σ jurisdictions.taxAmount` and per-line amounts are stable/deterministic. Expose the invariant as a package-private `TaxTotalsReconciler` so
  T4/T6 reuse it (also on provider responses — validate the external result satisfies the invariants; log + correct via the same delta rule if the provider drifts by a cent).
- **Also**: fix `effectiveTaxRate` to divide by the taxable (exempt-filtered) base; document the semantics in the OpenAPI description (ADR-0042).
- **Response addition (additive, pos-tax-common)**: `LineItemTax.jurisdictions[]` — per-line jurisdiction rows (type, code, rate, amount). This is the per-line×jurisdiction
  matrix surfaced to callers; T5 persists it.
- **Tests**: port Odoo's global-rounding vectors (17.79/17.80 split, three-way 33.33/33.33/33.34, odd-priced multi-line carts across 3 jurisdictions); property-based test
  asserting the three-way invariant for random carts.
- **Effort**: M. **Deps**: none. **Files**: `TestModeTaxCalculator`, `pos-tax-common` DTOs, `TestModeTaxCalculatorTest`.

### T2 — Honor `transactionDate` (rate effectivity)

- **Design**: test-mode rates become effective-dated: `TaxProperties.testMode.rateSchedule` — list of `{effectiveFrom, rates{STATE,COUNTY,CITY,...}}`; calculator selects the
  schedule row where `effectiveFrom <= transactionDate` (default `transactionDate = today` when absent — current behavior preserved). Provider path already forwards the field;
  add a contract test asserting it is serialized.
- **Why**: prerequisite for T4 (refunds must price at original sale date) and for honest rate-drift testing (comp-tax exercise 3).
- **Effort**: S. **Deps**: none.

### T3 — Exemptions as reportable zero-tax

- **Design** (Odoo's stance: exemption is "0% with paperwork", not "no rows"):
  1. **Contract**: add optional `exemptionReasonCode` (enum: `RESALE`, `GOVERNMENT`, `NONPROFIT`, `AGRICULTURAL`, `OTHER`) and `exemptionCertificateId` (UUID) to
     `TaxLineItem`; add request-level `customerExemption {reasonCode, certificateId}` applying to all lines. Response: exempt lines get **zero-rate jurisdiction rows** (rate =
     jurisdiction rate, amount = 0, `exempt=true`, reason echoed) so liability reports can show exempt sales per jurisdiction — the reportable-zero-tax model.
  2. **customerId honored**: introduce an **exemption registry** so `customerId` alone can drive exemption. **Decision (D-T1)**: the certificate registry lives in **pos-tax**
     (small table: certificate id, customerId, state scope, reason, effective/expiry, status) — it is tax semantics, not CRM identity — with certificate CRUD endpoints
     (`/v1/tax/exemption-certificates`, permissions `tax:exemption:view|manage`, `@EmitEvent` `TAX_EXEMPTION_CERT_CREATE|UPDATE` approval preset). This makes pos-tax minimally
     stateful (first Flyway migration `V1__exemption_certificate.sql`); the calculate path remains a pure function of request + config + registry lookup. Build the MVP-thin
     version only; when the external provider is selected (R-T1), evaluate whether its certificate management (e.g. AvaTax CertCapture) becomes the system of record with this
     registry as a cache — the API contract above is designed to survive that swap.
  3. **Decision (D-T2)**: expired/missing certificate with `taxExempt=true` → **tax anyway and flag** (`exemptionDenied=true` in the response, reason echoed) so the POS flow
     never hard-blocks a sale; the flag persists through T5 so exemption-denied sales are auditable. No reject-422 mode in v1.
- **Tests**: exemption trace test per comp-tax exercise 2 (workorder → tax → invoice → accounting evidence chain, coordinated with T5).
- **Effort**: M–L. **Deps**: T1 (per-line jurisdiction rows).

### T4 — Refund/credit calculation mode

- **Design**: add `calculationType` (`SALE` default, `REFUND`) and `originalReferenceId` to `TaxCalculationRequest`. `REFUND` semantics: amounts computed **positive** with
  `calculationType` echoed (callers negate at posting — keeps `@Positive` validation and avoids sign bugs), rates resolved as of `transactionDate` = **original sale date**
  (caller passes it; with T2 this reprices correctly), and in provider mode maps to the provider's refund-document type (T6). Response carries `calculationType` through.
- **Primary consumer**: pos-invoice credit-memo flow — but note the platform rule established in the accounting plan (D1): finalized-invoice credits use **stored** breakdown,
  never a recompute. T4's REFUND mode is for _partial/line-level_ refunds where a fresh proration is genuinely needed and for provider-side refund documents; document this
  decision boundary in the OpenAPI description and the contract guide so agents don't recompute where they should read (freeze-after-finalize, comp-tax §5).
- **Effort**: S–M. **Deps**: T1, T2.

### T5 — Persist the breakdown; propagate it to accounting (the keystone story)

Closes T-G6/T-G7/T-G10 and unblocks accounting plan D1 + T8. Spans three modules — split into three coordinated sub-stories with the event contract first (merge order per
capability convention: durion contract PR → pos-domain-events → pos-invoice → pos-accounting).

- **T5a — pos-invoice persistence**: new tables `invoice_line_tax` (invoiceId, lineItemId, jurisdictionType, jurisdictionCode, rate, taxAmount, exempt, exemptionReasonCode)
  and rollup `invoice_tax_summary` (invoiceId, jurisdictionType/code, taxableBase, taxAmount) populated from the full `TaxCalculationResponse` at calculation time
  (`TaxServiceClient` must stop truncating to `getTotalTax()`). **Freeze-after-finalize**: once the invoice reaches its finalized/issued state the rows are immutable (service
  guard + no update path) — the platform's `manual_tax_amounts` equivalent. Re-pricing before finalization replaces the rows wholesale. Flyway: next available pos-invoice
  version (verify at implementation time).
- **T5b — event contract**: extend `InvoiceUpdatedV1` (additive) with
  `taxBreakdown[] {lineItemId?, jurisdictionType, jurisdictionCode, rate, taxableBase, taxAmount, exempt, exemptionReasonCode}` alongside the existing scalar `tax` (kept for
  compatibility). If the additive rule can't hold, mint `InvoiceUpdatedV2` + dual-publish per ADR-0044 §3 — decide in the contract PR.
- **T5c — pos-accounting consumption**: `InvoiceEventsListener` stores the breakdown in a new `ext_invoice_tax` replica table (ADR-0044 R3 naming: owner=invoice);
  `ExtInvoice.tax` scalar retained. Accounting plan D1 then reads real tax for credit memos; T8 reads it for liability reporting.
- **AC**: end-to-end IT: calculate → invoice stores matrix → finalize freezes → event carries breakdown → accounting replica matches to the cent (T1 invariants) → credit memo
  posts real `taxReversed`.
- **Effort**: L (coordination-heavy). **Deps**: T1 (per-line rows exist). **Research flag R-T2**: confirm the full set of events that carry invoice tax today (only
  `InvoiceUpdatedV1` found; check invoice-finalized/issued events) so the breakdown rides every relevant schema.

### T6 — Provider abstraction with document lifecycle

- **Design**: define a `TaxProviderClient` interface with the Avalara-shaped lifecycle Odoo's connectors and the comp doc assume: `estimate(request)` (uncommitted),
  `commit(referenceId)` (on invoice finalization), `void(referenceId)` (on cancellation), `refund(request, originalReferenceId)` (T4). Implementations: `TestModeTaxProvider`
  (wraps T1 calculator; commit/void are no-ops recorded in log), `ExternalTaxProvider` (today's generic HTTP client refactored behind the interface).
  - **Idempotency**: send `referenceId` as the provider document code / idempotency key on every call; retries must not double-commit.
  - **Retry hygiene**: add a retry predicate — retry only on `5xx`/timeouts/connect errors; never on `4xx` (ADR-0021: 400s are deterministic).
  - **Lifecycle triggering**: pos-invoice calls `commit` at finalization and `void` at cancellation — both are synchronous utility calls (permitted: pos-tax is on the ADR-0044
    utility list). Failure policy at finalization when provider is down: **do not block the sale** — finalize with the last estimate, record a `PENDING_COMMIT` row in a small
    `tax_provider_transaction` log table (pos-tax, Flyway `V2`), and reconcile via a scheduled re-commit job. **Decision (D-T3)**: estimate-and-true-up is the platform policy
    — a provider outage must never block invoice finalization; surface the `PENDING_COMMIT` backlog as an operational metric so true-up lag is visible.
  - `externalTransactionId` finally populated from provider responses.
- **Research flag R-T1 (blocking for the external impl, not for the interface)**: select the actual provider (Avalara AvaTax vs TaxJar vs Stripe Tax) and map the real API. The
  interface + test-mode implementation + lifecycle wiring proceed now; the external adapter story is scheduled once the provider is chosen.
- **Effort**: M (interface/test-mode/wiring) + M (real adapter, later). **Deps**: T1, T4.

### T7 — Config-driven rate resolution in test mode

- **Design**: make the dead `postalCodeMapping` config real, or replace it with a clearer structure:
  `testMode.jurisdictions[] {match: {stateCode, county?, city?, postalCodePrefix?}, rates: {...}, effectiveFrom}` resolved from `destinationAddress` + `transactionDate` (T2).
  Optional per-`taxCategory` overrides (e.g. `LABOR` exempt in some states — pos-workorder already sends categories that are currently ignored) — this is the minimal,
  config-only sliver of Odoo's fiscal-position idea that a test/dev calculator needs; anything richer belongs to the provider.
- **AC**: two different destination states produce different breakdowns in test mode; unknown address falls back to `defaultRates` (current behavior); `taxCategory` override
  honored and documented.
- **Effort**: M. **Deps**: T1, T2.

### T8 — Sales-tax liability reporting

> **DONE — merged via PR #995 (#966), 2026-07-21.** R-T4 resolved as Decision A (reconciliation-grade v1; Finance files via the AvaTax portal). `GET /v1/accounting/reports/financial/tax-liability?startDate=&endDate=` (start/end params match the sibling reports) on `FinancialReportingController`, `reporting:view:financial-statements`: `ext_invoice_tax` aggregated state/county/city with taxable base, exempt base + reasons, gross tax collected, credits netted per jurisdiction+period (`TaxCreditAllocator` pro-rata split of `CreditMemo.taxAmountReversed` across the invoice's jurisdictions), net tax, and a GL-drift column vs the 2200 Sales Tax Payable balance (drift 0 on a clean ledger — proven cent-exact by a real-Postgres IT). Accrual basis. Phase-2 follow-ons filed: #996 (true per-jurisdiction credit attribution), #997 (credit lifecycle beyond POSTED), #998 (filing-grade output = R-T4 Decision B), #999 (real CSV/PDF export rendering — `ReportExportService` is still a stub).

- **Design**: "tax collected by jurisdiction for period" now answerable from platform data (T5). **Decision (D-T4)**: pos-accounting owns the report (it owns reporting
  infrastructure — `ReportExportService`, pos-documents rendering, period model from accounting plan B) reading its `ext_invoice_tax` replica:
  `GET /v1/accounting/reports/financial/tax-liability?period=` grouped by state/county/city with taxable base, exempt base (T3 zero-rows make this possible), and tax
  collected; reconciliation column vs the tax-payable GL account balance (flags drift between calculated and posted). Export CSV/PDF via existing rails.
- **Effort**: M. **Deps**: T3, T5, accounting plan B1 (period), G1 helpful.

### T9 — Caller hygiene cleanup (pos-workorder, pos-invoice)

- Remove pos-workorder's divergent `FALLBACK_TAX_RATE=0.0825` silent-fallback — on tax-service failure, surface a degraded-estimate state honestly (estimate flagged
  `taxPending`) instead of inventing a rate and silently flipping `testMode`. **Decision (D-T5)**: **flag-and-continue for estimates** (`taxPending=true`, no invented amount),
  **hard-require tax for invoice finalization** per the domain rule "invoice cannot issue if tax calculation failed" — with D-T3's estimate-and-true-up covering the case where
  a calculation succeeded earlier but the provider commit is pending.
- Fix `TaxClient` (pos-workorder) missing auth headers (`X-Authorities: tax:calculate`) to match `TaxServiceClient` (pos-invoice) — currently would 403 wherever enforcement is
  active.
- Send `EstimateItem.taxCode`/category through as `taxCategory` (consumed after T7); delete or wire `Estimate.taxRegionId` (recommend delete — destination address is the
  jurisdiction driver; pre-production policy says remove dead members).
- **Effort**: S–M. **Deps**: T7 for category consumption (header/auth fixes independent — do first).

---

## 3. Sequencing

| Wave | Stories                                                               | Notes                                                                   |
| ---- | --------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| 1    | T1, T2, T9(auth-header fix only)                                      | Pure pos-tax + trivial caller fix; no contract coordination             |
| 2    | T3, T4, T7                                                            | Contract additions to pos-tax-common (one coordinated additive release) |
| 3    | T5a→T5b→T5c                                                           | The keystone; strict merge order contract→events→invoice→accounting     |
| 4    | T6 (interface + test-mode + invoice lifecycle wiring), T9 (remainder) |                                                                         |
| 5    | T8, T6 external adapter (after provider selection R-T1)               | T8 also needs accounting plan Wave 1 (periods)                          |

Definition of done per story: code + tests (incl. the T1 invariant property test in every story touching amounts) + OpenAPI regeneration + contract-guide/README updates +
`@EmitEvent`/EventTypes/permissions where endpoints change + Spotless/Checkstyle/SpotBugs/ArchUnit + module `./mvnw -pl <module> -am test`.

## 4. Where more research is required

- **R-T1 (before T6 external adapter)**: provider selection (AvaTax / TaxJar / Stripe Tax) — pricing, API shape, refund/void semantics, certificate-management overlap with T3
  (AvaTax CertCapture could replace the in-platform registry — evaluate before building T3's registry beyond MVP).
- **R-T2 (before T5b)**: full inventory of invoice-lifecycle events in `pos-domain-events` (only `InvoiceUpdatedV1` was confirmed to carry `tax`); breakdown must ride
  finalization events too.
- **R-T3 (before T3)**: which states the business operates in and their exemption-certificate rules — determines the minimal reason-code enum and whether expiry tracking is
  needed at MVP.
- **R-T4 (before T8)**: Finance's filing workflow — if filing happens entirely in a provider portal, T8 is a reconciliation/audit report (lower fidelity bar); if filings are
  prepared from platform data, T8 needs taxable/exempt base precision and period alignment with accounting close.
- **R-T5 (before T5a)**: pos-invoice's current Flyway ceiling and whether invoice re-pricing after tax calculation exists anywhere today (would violate freeze-after-finalize;
  if found, that flow needs the T4/T5 decision boundary applied).

## 5. Decisions (resolved 2026-07-17; supersede the draft's open questions)

| ID   | Decision                                                                                                                                                                                                                                                                                                                                                                                  | Where applied |
| ---- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- |
| D-T1 | Exemption-certificate registry lives in pos-tax (MVP-thin); re-evaluate as a cache over provider certificate management when the provider is selected (R-T1) — the CRUD contract is designed to survive that swap                                                                                                                                                                         | T3            |
| D-T2 | `taxExempt=true` with missing/expired certificate → tax anyway, flag `exemptionDenied=true`, persist the flag through T5 for audit; no reject-422 mode in v1                                                                                                                                                                                                                              | T3, T5        |
| D-T3 | Provider-down at invoice finalization → estimate-and-true-up: finalize on last estimate, `PENDING_COMMIT` log + scheduled re-commit, backlog exposed as an operational metric; never block the sale                                                                                                                                                                                       | T6            |
| D-T4 | Tax-liability report is owned by pos-accounting, built on its `ext_invoice_tax` replica and period model                                                                                                                                                                                                                                                                                  | T8            |
| D-T5 | pos-workorder estimates: flag-and-continue (`taxPending`) on tax-service failure, no invented rates; invoice finalization hard-requires a successful tax calculation                                                                                                                                                                                                                      | T9            |
| D-T6 | Scalar `tax` fields (`Invoice.taxAmount`, `InvoiceUpdatedV1.tax`) are kept **within the v1 event schema** as denormalized rollups (additive-only rule); pos-invoice's own scalar column stays as a rollup with an invariant test (scalar == Σ breakdown); removal happens only at the next major event-schema version (`InvoiceUpdatedV2`), when consumers have migrated to the breakdown | T5            |

Any of these may be reopened by product/finance — reopening one reopens only the stories in its "where applied" column.

Note on payment-processor coupling: nothing in this plan binds to a specific payment processor; the settlement-reconciliation design that touches accounting (accounting plan
D-5/F1) is processor-agnostic with configuration owned by the payment service, and pos-tax's provider abstraction (T6) is likewise adapter-based — Stripe appears only as one
candidate among several in R-T1.

## 6. Issue tracking (durion-positivity-backend, created 2026-07-17)

| Wave | Story → Issue                                                                                          |
| ---- | ------------------------------------------------------------------------------------------------------ |
| 1    | T1 → #939 · T2 → #940 · T9a(auth fix) → #941                                                           |
| 2    | T3 → #947 · T4 → #948 · T7 → #949                                                                      |
| 3    | T5b(events) → #950 · T5a(invoice) → #951 · T5c(accounting) → #952 · D1(credit memo, cross-plan) → #953 |
| 4    | T6 → #961 · T6x(external adapter, blocked on provider selection) → #964 · T9(remainder) → #967         |
| 5    | T8 → #966                                                                                              |

Accounting-plan issues: see `plan-odoo-parity-pos-accounting.md` §13.

## 7. Execution status

Status — Wave 1 (T1 #939 · T2 #940 · T9a #941): **code-complete, PR #980** (branch `cap/odoo-parity-tax-w1`, off `main`). `pos-tax` verify 49 tests green (incl. the Σ-invariant property test — seed `424242L`, 2000 carts), `pos-tax-common` green; Spotless/lint clean; Code-Review PASS (1 contract-accuracy finding fixed in-wave, 3 deferred forward). New reusable `TaxTotalsReconciler` (T4/T6 will reuse). Additive contract `LineItemTax.jurisdictions[]` → **Angular SDK regen required** (frontend follow-up). No permission change. **Pre-existing blocker #979**: `pos-workorder` `EstimateTaxCalculationContractBehaviorIT` rollback (2 ITs) — proven pre-existing on pristine `main`; not in Wave 1 scope, fix before any wave needing pos-workorder verify green.

Waves 2–5 pending. Reminder: **T5 (Wave 3, keystone)** is what unblocks accounting-plan D1-breakdown-upgrade + T8 — waves run sequentially (Wave 2 T3/T4/T7 → Wave 3 T5a→T5b→T5c). Research gates still open before their waves: R-T3 (before T3), R-T2/R-T5 (before T5), R-T4 (before T8).
