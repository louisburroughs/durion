---
type: ADR
title: 'ADR-0067: Tenant Functional Currency and Multi-Currency Support'
description: One functional currency per tenant (Stage A) before transactions in several currencies (Stage B - dual amounts and realized FX, then revaluation, then foreign-currency bank accounts); accepted by the platform owner on the section 2 recommendations subject to the section 2.1 rulings - the currency locked at tenant creation rather than activation, one cross-currency settlement case allowed in B1 rather than refused, and Canada as the first non-USD market.
status: stable
adr_status: accepted
created: '2026-09-28'
related: [ADR-0013, ADR-0017, ADR-0021, ADR-0030, ADR-0040, ADR-0044, ADR-0047, ADR-0048, ADR-0049, ADR-0053, ADR-0054, ADR-0057, ADR-0062]
tags: [adr, multitenancy, platform]
---
# ADR-0067: Tenant Functional Currency and Multi-Currency Support

**Status:** ACCEPTED  
**Date:** 2026-09-28  
**Deciders:** Platform Owner (every row of §2); advising: Chief Architect, Accounting Domain, Pricing Domain, Tax, Frontend Lead  
**Affected Issues:** none filed yet; §10 lists the issues to file. Opened by decision D18 of the accepted bank reconciliation specification, which names this ADR
(`domains/accounting/SPEC-manual-bank-reconciliation.md` l.1509).  
**Amends:** ADR-0047 (Context driver, D-8 rationale), ADR-0062 §7, ADR-0030 §4, ADR-0021; ADR-0057 and ADR-0048 at Stage B1. Full list in §9.

> **How to read this.** §1 and §2 are enough to decide: a summary, then one table row per decision with its options, a recommendation and the consequence.
> Rows marked **A** are needed before Stage A starts; rows marked **B1** or **B2** can wait until Stage B is scheduled. Everything after §2 is the evidence and
> the detail behind each row. **Every row was decided by the platform owner on 2026-09-28; §2.1 records the rulings, and two of them depart from
> the table's recommendation: MC-1's lock timing and MC-7.** EXISTING marks a fact verified in code or in a ratified document on 2026-09-28;
> PROPOSED marks a recommendation of this ADR, accepted as written unless §2.1 says otherwise. Rows with an `MC-` id come from the Accounting Domain's requirements memo of 2026-09-28 and keep its ids and
> recommendations; where the architect disagrees, §12.1 says so. Rows with a `PC-` id are the platform-level decisions this ADR adds.
>
> **Citations.** Backend Java: `<module> <File>.java:<line>` under `durion-positivity-backend/<module>/src/main/java/`. Flyway: `<module> <file>.sql:<line>`
> under the module's `src/main/resources/db/migration/`. Events: the record name in `pos-domain-events`. Frontend: `FE <path>` under
> `durion-positivity-frontend/`. Documents: path from the durion repository root. Line numbers are as of 2026-09-28.

---

## 1. Decision Summary

The platform owner, 2026-09-28: *"We need to prepare for other currencies and multiple currencies. We initially rejected them for simplicity's sake, but it is
time to revisit."*

DECIDED by the platform owner on 2026-09-28 (rulings in §2.1):

1. **Two capabilities, in this order.** *Stage A, other currencies:* each tenant has exactly one **functional currency**, the currency of its single ledger,
   which may be any ISO 4217 currency; everything the tenant prices, invoices, collects, pays, taxes and books is in it. *Stage B, multiple currencies:*
   documents in currencies other than the functional one, delivered as **B1** (two amounts on every ledger line, booking rates, realized FX), **B2**
   (period-end revaluation that reverses automatically) and **B3** (foreign-currency bank accounts). **Translation** (a presentation currency, consolidation
   across tenants) is out of scope for both. Stage A comes first even if Stage B is what the business wants: FX is always measured against a functional
   currency, and a currency such as JPY needs rounding by its own number of decimals (Accounting memo §1).
2. **The tenant is the currency boundary** (PC-1). One tenant, one ledger, one functional currency. A business trading in two countries runs two tenants
   under one account (ADR-0062 §7).
3. **pos-tenant masters the functional currency on the tenant, not on the owning account, and publishes it** additively on the tenant projection. Every
   module that handles money keeps its own copy (MC-1, PC-2). Existing tenants get USD.
4. **Every amount states its currency.** No `USD` literal, column default, fallback or deployment-wide setting remains. Amounts that change hands are rounded
   to the ISO 4217 exponent of their currency, and every `0.01` tolerance becomes one minor unit (PC-3 to PC-6, MC-8).
5. **Accounting owns booking rates and the conversion into the functional currency** (MC-3, PC-17). No other module converts, except inventory receipt cost,
   which uses accounting's rate because inventory owns valuation (ADR-0048).
6. **Jurisdictions, not currency mechanics, decide go-live** (PC-15). A non-USD tenant goes live per jurisdiction after a readiness sign-off: tax first, then
   payments, cash handling, documents and a check against the recorded statutory non-goals.
7. **Some defects exist today, even for USD-only tenants** (§10): a supplier invoice's currency is dropped, a documented currency 409 is never raised, a
   fabricated 0.01 can reach the ledger, and money is formatted en-US whatever locale the user picked. They should be filed and fixed whatever the owner
   decides here.
8. **Canada is the first non-USD market,** launched with USD or right after it (OP-9). PC-7 cash rounding, Canadian multi-level sales tax and an en-CA
   locale are therefore Stage A launch work, not deferred jurisdiction work.

---

## 2. Owner Decision Table

One row per decision. "Needed by" is the stage that cannot start without the answer. Detail: §5 for `PC-` rows, §6 for `MC-` rows.

### Decide before Stage A

| ID | Needed by | Question | Options | Recommendation | Consequence if accepted |
| --- | --- | --- | --- | --- | --- |
| PC-1 | A | What is the currency boundary? | (a) tenant = one ledger = one functional currency; a business in two countries runs two tenants under one account · (b) several ledgers or functional currencies inside one tenant (multi-company) | **(a)**. Multi-company is a recorded non-goal (accounting plan l.33) and SPEC D6 made the tenant the only entity boundary | No group view across tenants; that would be translation, which is out of scope |
| MC-1 | A | Who masters the functional currency, and what do existing tenants get? | (a) a pos-tenant attribute, published in the tenant projection · (b) an accounting setting · (c) the owning account's `home_currency` | Memo: **(a)**, locked when the tenant becomes ACTIVE; never (c). Existing tenants and their ledger lines: USD at rate 1. Architect: lock at creation instead (OP-1) | ADR-0062 §7 amended; every module reads the same value |
| PC-2 | A | How does every module learn it? | (a) additive field on `TenantCreatedV1` and `TenantProjectionV1`, kept in each money-handling module's own replica · (b) the cached `RemoteTenantRegistry` lookup · (c) configured per module · (d) a token claim | **(a)**, read through one shared accessor that fails closed. The value never changes, so a replica is never stale once written | pos-security-service's `ext_tenant` and `/v1/tenants/me` carry it, so the frontend learns it too |
| PC-3 | A | Must every amount state its currency? | (a) yes: an ISO 4217 code on every money-bearing record, DTO and event, no implicit default (DECISION-PRICING-001 made platform-wide) · (b) no: an amount without a code is in the functional currency | **(a)**. Where a default is wanted it is the tenant's functional currency, read explicitly. Money thresholds become configuration in the functional currency | About 20 USD literals and defaults and 3 deployment-wide settings go (§3.3) |
| PC-4 | A | Is there a shared money type? | (a) a library `Money` value, currency-unit helper and one ISO validator in `pos-shared-dtos`; wire shapes stay flat · (b) `Money` objects in every DTO and event · (c) nothing shared | **(a)** | One ISO list instead of two; no `×100` left |
| PC-5 | A | Decimals or minor units? | (a) decimals for new contracts; existing minor-unit fields stay, each beside its currency code, converted by exponent only · (b) convert every minor-unit field now · (c) minor units everywhere | **(a)** | Goods-receipt events gain a currency; the fixed `×100` and `÷100` conversions (three backend classes, one frontend service) go |
| PC-6 | A | Scale and rounding | (a) the ISO 4217 exponent of each amount's currency; HALF_UP per line, then sum; tolerance = one minor unit; rates, unit prices and unit costs keep declared extra precision · (b) fixed two decimals with per-currency exceptions | **(a)**. It answers AGENT_GUIDE G21 | Tax, estimates, quotes, credit memos, split postings, bank reconciliation and reports change; two-decimal columns widen |
| MC-8 | A | Ledger scale; conversion rounding residual | keep 4-decimal pass-through (PRS l.201-202) / round to the currency's decimals; the largest line absorbs the residual (as in PRS §4.2) / a rounding account | Memo: **round to the currency's decimals**. The largest line absorbs up to half a minor unit per line; anything more fails the posting | PRS §3.1 changes; no rounding account needed |
| PC-7 | A, for some currencies | Cash rounding where the smallest coin is larger than the minor unit (for example CAD 0.05) | (a) keep the recorded non-goal · (b) round the cash tender in pos-order; document totals unchanged; the difference posts to an account and mapping key the owner names · (c) round document totals | **(b)**, before such a tenant takes cash | Reopens a non-goal of the tax plan (l.27) and the order spec (l.348) |
| PC-8 | A | How do `.v1` money events gain a currency? | (a) additive field with an in-place `schemaVersion` bump (ADR-0044 §3 precedent) · (b) `.v2` topics with dual publishing · (c) no field; consumers assume the functional currency | **(a)**. While producers catch up, an absent currency means the tenant's functional currency (USD for every existing tenant); afterwards it is malformed | About 16 event types gain a field; 4 need only their producer fixed |
| PC-9 | A | What does a Stage A tenant do with a document in another currency? | (a) never book at par: inbound facts parked in a visible exception state, requests refused with 422 `CURRENCY_NOT_SUPPORTED` · (b) book at face value, as supplier invoices are today · (c) convert at a hand-entered rate | **(a)**, generalizing D18 and the settlement guard | Foreign supplier invoices wait for B1 or manual handling |
| PC-10 | A, B1 | How are selling prices held per currency? | (a) explicit prices per currency; default = the tenant's functional currency; no conversion at quote time · (b) convert functional-currency prices at quote time · (c) both | **(a)**. `pos.price.default-currency` becomes per tenant; DECISION-PRICING-004 restated as "each price carries its currency" | Stage A: one price list, as today. B1: a list per sold currency |
| PC-11 | A | Which currency does tax use? | (a) pos-tax calculates in the document currency; `currencyCode` required; rounding at the exponent; response echoes the currency · (b) keep the USD default | **(a)** | One `pos-tax-common` field tightened; regime support becomes a go-live gate (PC-15) |
| PC-12 | A, B1 | Payment gateway currency | (a) gateway requests carry the currency; minor units by exponent; Stage A charges only the functional currency; B1 charges the invoice currency where the processor account supports it · (b) only ever charge the processor account's currency | **(a)** | Gateway port records and payment-intent columns change |
| PC-13 | A, B1 | Supplier prices, purchase orders and bills in foreign currencies | (a) supplier prices keep their market currency (ADR-0053 unchanged); a PO or bill in a non-functional currency is parked in Stage A and allowed from B1 · (b) convert supplier prices at ingest | **(a)** | Vendor selection compares like currencies only until B1 |
| PC-14 | A | Frontend money display and input | (a) format with the record's own currency and the user's locale through one allowlisted core formatter; no `'USD'` literal or fallback; minor units by exponent; currency inputs are lists; an arch rule enforces it · (b) a tenant-wide `DEFAULT_CURRENCY_CODE` provider | **(a)**. In Stage A only, a DTO without a currency field falls back to the tenant's functional currency; the fallback goes before B1 | ADR-0030 §4 amended; 39 bare pipes, 16 literals and 17 fallbacks change |
| MC-10 | A | Translation; meaning of the CAP-316 plant currency | translation in / out; the plant currency is a transaction currency / a report-only view / a separate entity | Memo: **translation out**; the owner states what CAP-316 intends; a separate entity is excluded by SPEC D6. Architect: report-only view (§5.15) | `LocationFxRate` retired or labelled as not a ledger rate |
| PC-15 | A | What gates a non-USD tenant's go-live? | (a) a per-jurisdiction readiness sign-off: tax first, then payments, cash rounding, statutory checks, documents and display · (b) Stage A completion alone | **(a)** | Stage A can be finished before any jurisdiction is ready |

### Decide before Stage B

| ID | Needed by | Question | Options | Recommendation | Consequence if accepted |
| --- | --- | --- | --- | --- | --- |
| MC-2 | B1 | Which transaction currencies may a tenant use? | any currency / a per-tenant allow-list | Memo: **allow-list** | The owner defines the permission tokens (`accounting:currency:*` is only listed as a future family, PERMISSION_TAXONOMY l.1304) |
| PC-16 | B1 | Where does the allow-list live? | (a) pos-accounting, published as a fact so documents are refused when created · (b) pos-tenant, beside the functional currency · (c) no list; the ledger refuses at posting | **(a)** | Price, order, workorder, invoice and purchasing refuse a disallowed currency up front |
| MC-3 | B1 | Booking rates: owner, source and date rule | accounting / a reference-data module / the sending modules convert; exact date / latest on or before the date / average | Memo: **accounting**; manual entry and import first, a feed later; the latest rate on or before the date, within N days set by Finance | A rate store with audit |
| PC-17 | B1 | Who converts, and how do rates reach other modules? | (a) accounting converts every posting; inventory converts receipt cost with accounting's published rate; nobody else converts · (b) a synchronous FX utility module · (c) each module converts | **(a)**. Rates are tenant-scoped and published as facts | Inventory keeps a rate replica; cost facts carry the rate used |
| MC-9 | B1 | AR/AP control accounts | one control account for all currencies / one per currency | Memo: **one control account**, with the currency on each open item; bank accounts stay single-currency | Smaller chart of accounts |
| MC-6 | B1 | FX accounts and mapping keys | one FX account / separate realized and unrealized, gain or loss by sign | Memo: **one new posting category with separate realized and unrealized keys**; the owner picks the accounts; nothing is seeded before ratification | Seed data only after ratification. Plan decision D-14 already anticipates a category named `FX_GAIN_LOSS` |
| MC-7 | B1 | Settlement or `TRANSFER` across two currencies | allow with FX / refuse | Memo: **refuse at first**. Architect: decide it together with B3's timing (OP-2) | FX arises only from rate changes over time |
| MC-11 | B1 | Rate for converting tax; filing currency | the document's rate / a rate the jurisdiction mandates; functional / other | Memo: **must be asked**. Default: the document's rate, filing in the functional currency | Belongs to pos-tax |
| MC-4 | B2 | Revaluation style | reversing / non-reversing | Memo: **reversing** | Realized FX is always measured against the historical rate |
| MC-5 | B2 | Which balances are revalued | by account class | Memo: bank, undeposited funds, AR, AP, refundable customer credits, and 2360 when it holds foreign-currency lines. Finance confirms that deposits for future service are non-monetary | Sets the revaluation and readiness scope |

The architect differs from the Accounting memo in two places, both in §12.1: when the functional currency locks (OP-1, MC-1) and whether B1 is usable before
B3 while MC-7 refuses cross-currency settlement (OP-2). The table keeps the memo's recommendations; §2.1 records which one the owner chose.

### 2.1 Owner rulings (2026-09-28)

The platform owner accepted the recommendations in the two tables above subject to these rulings, which settle the contested and open items. Two rulings
depart from a table row's recommendation, MC-1's lock timing and MC-7; where a ruling departs, the ruling governs.

| Item | Ruling |
| --- | --- |
| All other §2 rows | **Accepted as recommended** |
| MC-1 / OP-1 | **Lock at creation** (architect's view). The functional currency is a required field of the tenant create request and immutable from then on; a wrong value is corrected by decommissioning the PENDING tenant and creating another. Existing tenants and their ledger lines: USD at rate 1 |
| MC-7 / OP-2 | **Option 2: allow one cross-currency case in B1.** A foreign-currency item may be settled through a functional-currency bank account or payout, with the FX difference posted as realized FX (plan D-14's `FX_GAIN_LOSS` category, MC-6's realized key). Every other cross-currency settlement or `TRANSFER` stays refused until B3. The Accounting Domain rules on F2's shape (a currency per line, or a two-step clearing pattern) before B1 starts. B1 is usable without B3 |
| MC-10 / OP-8 | **The CAP-316 plant currency is a report-only view** (§5.15). `LocationFxRate` survives, labelled as not a ledger rate; translation stays out of scope |
| OP-3 | **Accepted:** a receipt without a booking rate for its date is recorded, its valuation parked until the rate exists, then valued at the receipt-date rate |
| OP-4 | **Accepted:** the cost fact carries the rate reference, so inventory valuation and accounting's accrual use one functional amount |
| OP-5 | **`account.home_currency` is the currency Durion bills the account in.** It stays on the account and only prefills the tenant create form's functional currency; it never becomes a tenant's functional currency by itself (MC-1 option c stays rejected) |
| OP-6 | Tax-provider regime coverage, tax-inclusive pricing and MC-11's filing rules are items of each jurisdiction's PC-15 readiness sign-off. MC-11 default holds: the document's rate, filing in the functional currency, unless the jurisdiction mandates otherwise |
| OP-7 | **The accounting framework is a per-tenant setting** (`US_GAAP` or `IFRS`, as CAP-054's configuration already names). MC-5's monetary and non-monetary classification follows the tenant's framework |
| OP-9 | **Canada is the first non-USD market, launched with USD or right after it.** CAD tenants need PC-7 cash rounding (CAD 0.05), GST/HST/PST/QST support in pos-tax (OP-6) and an en-CA locale (OP-15) as Stage A launch work. Mexico and France are not scheduled |
| OP-10 | A precondition of Stage A's backfill: a data check for non-USD rows before the USD backfill runs; any found are resolved by hand first |
| OP-11 | **One rule: HALF_UP.** pos-price's HALF_EVEN unit-price rounding changes to HALF_UP; unit prices keep their declared extra precision (PC-6) |
| OP-12 | An item of each jurisdiction's PC-15 readiness sign-off (documents): which locale renders invoices and estimates is decided there, first for Canada. The currency always comes from the document (F-1) |
| OP-13 | **Accepted:** a test compares backend (JDK ISO) and frontend (CLDR) exponents for every currency in any tenant's allow-list, and for the functional currency of every tenant |
| OP-14 | **Accepted:** amended accounting documents say tenant (ADR-0062 §4), never organization |
| OP-15 | **en-CA is added** for the Canadian launch (ADR-0030); further locales follow each jurisdiction's PC-15 sign-off |

---

## 3. Context

### 3.1 Why now

Single currency was chosen for simplicity and recorded in several places (§3.2). The owner reopened it on 2026-09-28 for two needs: tenants whose functional
currency is not USD, and transactions in several currencies. The accepted bank reconciliation specification already defers to this record: its decision D18
refuses any currency but USD "until that ADR is accepted" and names "proposed ADR-0067" (`domains/accounting/SPEC-manual-bank-reconciliation.md` l.1509).

The research behind this record (a backend currency inventory, a frontend and documentation inventory, and the Accounting Domain's requirements memo, all
dated 2026-09-28) found that the platform is single-currency partly **by design**, through recorded decisions, and partly **by accident**, through code that
assumes USD with no decision behind it. The two need different treatment: the first is amended by this ADR when accepted; the second is either a defect
(§10) or Stage A work.

### 3.2 Single currency by design (recorded decisions, EXISTING)

| Record | Location | What it says |
| --- | --- | --- |
| ADR-0047 driver | `docs/adr/0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md` l.28 | "positivity is a single-currency, event-driven POS platform" |
| ADR-0047 D-8 rationale | same, l.60 | "single-currency, jurisdiction-first tax delegation" |
| Accounting parity plan | `domains/accounting/plan-odoo-parity-pos-accounting.md` l.5, l.33-34 | "US, single-currency"; ground rule 6 makes multi-currency and FX gain/loss non-goals; "Currency columns stay `USD`-defaulted" |
| Plan decision D-14 | same, l.398 | One currency per settlement is a hard contract invariant; cross-currency payouts are normalized by the adapter, with any FX difference on an adjustment line through "a future `FX_GAIN_LOSS` category"; multi-currency GL is out of v1 |
| Tax parity plan | `domains/accounting/plan-odoo-parity-pos-tax.md` l.7, l.27-28 | A "US sales-tax service"; non-goals include multi-currency tax, price-included pricing and cash rounding (0.05) |
| Order parity plan and spec | `domains/order/plan-odoo-parity-pos-order.md` l.5, l.36; `domains/order/spec-pos-order-missing-functionality.md` l.348-349 | "US, single-currency"; multi-currency and localized receipts are non-goals; "USD cent precision suffices" |
| Bank reconciliation D18 | `domains/accounting/SPEC-manual-bank-reconciliation.md` l.1509, §3.1 l.271 | Refuse other currencies (`CURRENCY_NOT_SUPPORTED`); profiles are USD; "`USD` is the only ledger currency" |
| Ledger shape | same, l.74; pos-accounting `V1__baseline_accounting.sql`:572-591, 671-714 | No currency on `gl_account`, `journal_entry` or `journal_entry_line` ("USD implied") |
| Settlement guard | pos-accounting `SettlementReconciliationServiceImpl.java`:76-83, :99-101 | A deployment-wide `accounting.ledger.base-currency` (default USD); settlements in any other currency are rejected at ingest |
| DECISION-PRICING-004 | `domains/pricing/.business-rules/DOMAIN_NOTES.md` l.76-80 | "single-currency per book" |
| CAP-054 | `docs/capabilities/CAP-054/CAP-054-backend-implementation.md` l.28, l.604-609 | "Single entity, single currency for v1.0"; `primary-currency: "USD"` |
| Labor & Overhead report v1 | `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` l.803-804; `docs/capabilities/CAP-316/CAP-316-spec.md` l.144-147 | v1 assumes a US plant (rate 1.00); the local-per-USD rate is "Presentation context only" |

### 3.3 Single currency by accident (assumptions with no decision behind them, EXISTING)

| Assumption | Where | Effect for a non-USD tenant |
| --- | --- | --- |
| USD stamped on events and payloads | pos-invoice `PaymentEventPublisher.java`:39 (used :62, :162); pos-order `OrderDomainEventPublisher.java`:43, `RegisterSessionServiceImpl.java`:55, `ReturnOrderServiceImpl.java`:73, `OrderCancellationServiceImpl.java`:183; the Javadoc of `PaymentSettledV1`, `PaymentReversedV1`, `OrderCompletedV1` and `RegisterSessionClosedV1` says "USD platform-wide" | Every payment and order fact says USD |
| USD on every tax call | pos-invoice `TaxServiceClient.java`:26; pos-order `OrderTaxService.java`:57; pos-workorder `EstimateServiceImpl.java`:1275; pos-tax-common `TaxCalculationRequest.java`:79-88 defaults to USD | Tax calculated in the wrong currency |
| USD defaults in code and schema | pos-invoice `DepositCredit.java`:75, `DepositCreditServiceImpl.java`:65-68, `V1__baseline_invoice.sql`:46 (`DEFAULT 'USD'`); pos-accounting `APPayment.java`:103, :159, `CreditMemoServiceImpl.java`:65, :319-320, `BillingRulesServiceImpl.java`:70; pos-workorder `EstimateServiceImpl.java`:96; pos-inventory `PurchaseSuggestionServiceImpl.java`:68; pos-catalog `PricingClientImpl.java`:63 | Records silently become USD |
| Deployment-wide settings | pos-price `application.yml`:69-71 (`pos.price.default-currency`); `accounting.ledger.base-currency` (above); the seed contract's `environment.currency` (`docs/architecture/deployment/data-migration/SEED_INPUT_CONTRACT.md` l.51); 121 `USD` values in pos-price `R__seed_reference_price.sql` | One value for every tenant; a non-USD tenant's settlements are all rejected |
| Two decimals, `×100` | pos-accounting `StripePaymentGateway.java`:67-71, `PaymentOutcomeProcessingServiceImpl.java`:179; pos-inventory `VendorSelectionService.java`:222-234; `FE src/app/features/inventory/services/inventory-purchase-order.service.ts`:73-116; scale 2 in pos-tax `AvalaraTaxProvider.java`:89 and `TaxTotalsReconciler.java`:36-39, pos-accounting `CreditMemoServiceImpl.java`:68 and `PostingRuleEvaluatorImpl.java`:118, pos-workorder `EstimateItem.java`:221, pos-price `PriceQuoteServiceImpl.java`:98 | JPY amounts wrong by a factor of 100, KWD by 10 |
| Fixed tolerances, minimums and column scales | 0.01 in pos-accounting `BankReconciliationServiceImpl.java`:85 and `FinancialReportingServiceImpl.java`:89, :120; 0.0001 in `JournalEntry.java`:229-231; `@DecimalMin("0.01")` on 9 request fields (7 in pos-accounting); payment intents `numeric(15,2)` (pos-invoice `V1__baseline_invoice.sql`:217-219); workorder lines `numeric(19,2)`, estimate totals `numeric(38,2)` | Wrong for currencies with 0 or 3 decimals |
| Currency-less money thresholds | pos-invoice `PaymentServiceImpl.java`:42 and `InvoiceFinalizationServiceImpl.java`:71 (500.00); pos-order `PriceOverrideServiceImpl.java`:63 (50.0); pos-customer `AccountTierServiceImpl.java`:49-53 (50,000 to 1,000,000) | 500.00 JPY is a few dollars' worth |
| US formatting | pos-accounting `AuditTrailServiceImpl.java`:90, :203 (`"$%.2f"`); `BankStatementCsvParser.java`:123 (strips `$` and `,`) | Audit text says `$`; non-US statement formats mis-parse |
| Money stored or sent with no currency | invoices, payment intents, refunds, sales orders, journal entries and lines, vendor bills, invoice replicas, the inventory ledger and costs, warranty; about 16 event types (§5.8) | A second currency cannot be held |
| A tenant currency exists but nothing uses it | pos-tenant `V1__baseline_tenant.sql`:24-25 (`account.home_currency`, on the owning account); `AccountServiceImpl.java`:89-91 (updatable at any time); absent from `TenantCreatedV1` and `TenantProjectionV1`; the `tenant` table has none (:63-78) | No module can learn a tenant's currency |
| USD-pivoted FX | pos-accounting `V1__baseline_accounting.sql`:129-141 (`local_currency_per_usd`, `numeric(12,6)`); `LaborOverheadReportServiceImpl.java`:60, :95-122, :276-290 | The only FX logic assumes USD reporting |
| Frontend | `FE src/app/app.config.ts`:82-138 has no `LOCALE_ID` or `DEFAULT_CURRENCY_CODE`, and no currency pipe passes a locale; 39 bare currency pipes (render USD); 16 pipes with a literal `'USD'`; 17 fallbacks to `'USD'`; amount inputs with `step="0.01"`; free-text currency inputs prefilled USD; `AccountSummary.homeCurrency` mapped and never shown (`FE src/app/features/platform/models/tenant.models.ts`:55) | Everything renders as en-US USD |

### 3.4 Constraints this ADR keeps (EXISTING)

1. **DECISION-PRICING-001** (`domains/pricing/.business-rules/DOMAIN_NOTES.md` l.22-30): money is an amount plus a required currency code, "to avoid implicit
   defaults"; the backend owns rounding and the UI never recomputes totals.
2. **ADR-0044**: payload changes within a version are additive only; breaking changes need `.v2` with dual publishing (§3, l.107). No domain-to-domain
   synchronous calls; reads go through replicas fed by the owner's events (§2, R1 and R3).
3. **ADR-0062**: the tenant is the isolation boundary (§4); account, contact and billing data never leave pos-tenant (§7, l.200-203); tables are
   tenant-scoped unless whitelisted (§5); no new `organizationId`.
4. **ADR-0053**: supplier price entries are scoped by market, including currency (l.65), and never enter sell-price resolution (§4, l.79-88).
5. **ADR-0054**: pos-price is the transactional quote system of record; pos-catalog holds list and MSRP reference prices.
6. **ADR-0048** §3 (l.68-73): inventory owns valuation; accounting posts from cost facts and does not recalculate unit cost.
7. **ADR-0030** §4 (l.58-66): money is formatted through Intl/CLDR, never by hand; rule I18N-07 enforces it (`FE arch/rules/i18n.rules.ts`:264-285).
8. **Accounting AGENT_GUIDE** l.147: HALF_UP at currency scale, rounded per line, then summed. Journal entries must balance (entity check and a database
   trigger).
9. **ADR-0047**: a non-goal changes only by amending or superseding that ADR (l.38). Its two decisions, no chained-hash ledger (D-2) and no fiscal-position
   layer (D-8), are not reopened here; only the single-currency driver is.
10. **TEST_COVERAGE_POLICY** l.1021-1023: currency selection refuses to guess.
11. **ADR-0057**: analytics money follows the ledger (§1) on a movement basis (§3).

### 3.5 Plumbing to build on (EXISTING)

- pos-tenant validates `homeCurrency` against `^[A-Z]{3}$`; `TenantProjectionV1` states that "New fields may be added additively within schema version 1".
- pos-price keys base prices, location overrides and labor rates by currency (`V1__baseline_price.sql`:35, 67, 93; indexes :240, :250), filters quote
  selection by currency and answers 404 `PRICE_BASE_UNAVAILABLE` when none applies (pricing `BACKEND_CONTRACT_GUIDE.md` l.124-129).
- pos-catalog price-book rules hold per-currency amounts plus a `defaultCurrency` (`PriceBookServiceImpl.java`:228-297); MSRP carries a currency; supplier
  price entries are scoped by country and currency.
- Supplier codecs refuse a currency-less amount (pos-supplier `EdiwheelB33InvoiceCodec.java`:230-235: "An amount with no currency is not a sum of money");
  CHECK constraints tie an amount to a currency (`V1__baseline_supplier.sql`:504; pos-workorder `V1__baseline_workorder.sql`:652-666).
- Purchase orders carry a currency with minor units; `ReceiptUnitCosts.java`:32-58 converts by the currency's fraction digits.
- pos-accounting has currency columns on AP payments, bank reconciliations, credit memos, customer credits, payment applications, processor settlements and
  receivable payments, and D-14's one-currency-per-settlement contract.
- pos-tax-common's `@IsoCurrencyCode` is a real ISO 4217 check; `TaxCalculationRequest.currencyCode` is already passed to AvaTax
  (`AvalaraTaxProvider.java`:200).
- Estimates carry `currency_uom_id` and `rate_currency`.
- The frontend registers five locales and holds the user's choice (`FE src/app/core/services/locale.service.ts`); I18N-07 allows one allowlisted core
  formatter; the Labor & Overhead page already formats with the user's locale.

### 3.6 Scope

**In:** every backend module that handles money, pos-tenant and pos-security-service, the shared libraries, the frontend, the SDKs, and the documents in §9.
**Out:** translation and consolidation; multi-company (a recorded non-goal); and statutory localization (chart templates, tax grids, e-invoicing, fiscal
receipts) and ledger inalterability, which stay governed by their own non-goals and revisit triggers (accounting plan ground rule 6; ADR-0047 D-2) and are
checked per jurisdiction at go-live (PC-15).

---

## 4. The Two Capabilities, Staged

| Stage | Adds | Unlocks | Does not unlock | Starts when |
| --- | --- | --- | --- | --- |
| **A — other currencies** | A functional currency per tenant; an explicit currency on every money record; scale and rounding by exponent; no USD literals or deployment-wide settings; the Stage A guard (PC-9) | A tenant whose prices, documents, payments, tax, books and bank accounts are all in one currency other than USD, subject to its jurisdiction's go-live (PC-15). Correct 0- and 3-decimal currencies. The §10 defects fixed for USD tenants too | Any document in another currency (parked or refused); rates, FX postings and revaluation; foreign-currency bank accounts; translation; statutory localization | The owner decides the A rows |
| **B1 — transactions in other currencies** | The allow-list; two amounts on every entry and line; booking rates; realized FX at settlement; per-currency price lists; foreign POs and bills; tax converted into the functional currency | Invoices, payments, credits, POs and bills in allowed currencies; aged AR/AP and statements in the transaction currency | Revaluation (open items stay at their historical rate until settled); foreign bank accounts; settlement or transfer across two currencies (MC-7; see OP-2) | Stage A exited; the B1 rows decided; FX accounts chosen by the owner |
| **B2 — revaluation** | Period-end revaluation at the closing rate, reversed the next day; a close readiness check | Foreign monetary balances carried at closing rates; unrealized FX | Translation | B1 live; MC-4 and MC-5 decided; the accounting framework known (OP-7) |
| **B3 — foreign-currency bank accounts** | Bank-account profiles in allowed currencies; reconciliation on transaction amounts | Holding, depositing and reconciling foreign money | Transfers across two currencies (MC-7) | B2 live |
| **Out of scope: translation** | — | — | A presentation currency, consolidation across tenants, average-rate translation, a translation reserve | Not planned |

Stage A is useful on its own. Stage B is not useful before Stage A, because every FX amount is measured against the functional currency and every rounding
rule needs the currency's exponent (Accounting memo §1).

---

## 5. Decision (detail by decision)

None of the options below is decided. Each subsection gives the EXISTING state, the options with their trade-offs, and the PROPOSED recommendation. The
`MC-` decisions are the Accounting Domain's; their requirements are in §6.

### 5.1 PC-1 — The currency boundary is the tenant

**EXISTING.** SPEC D6 (l.1497) decided that the tenant is the only entity boundary, with no legal-entity dimension; no legal-entity concept exists in code
(SPEC l.75). The accounting plan lists multi-company as a non-goal (l.33). One account may own several tenants (ADR-0062 §7, l.188-193).

| Option | For | Against |
| --- | --- | --- |
| (a) One tenant = one ledger = one functional currency; a business trading in two countries runs one tenant per country under one account | Matches D6, ADR-0062 and every single-ledger invariant; each country's books are in its own currency, which local tax and statutory reporting need | No group view across tenants (that is translation); customers, products and staff shared by both countries are set up in each tenant |
| (b) Several ledgers or functional currencies inside one tenant | One login and one catalogue for a group | Reopens multi-company; every money table gains an entity dimension; ADR-0062's isolation boundary stops matching the books |

**Recommendation (PROPOSED): (a).** Stage B then means foreign-currency *transactions* of one entity, never several entities in one tenant.
**Decision:** pending, owner.

### 5.2 PC-2 — How every module learns the functional currency

**EXISTING.**
- The `tenant` row has no currency (pos-tenant `V1__baseline_tenant.sql`:63-78). The owning `account` has `home_currency varchar(3) NOT NULL` (:24-25),
  seeded USD (`V2__seed_tenant.sql`:10, :20), updatable at any time (`AccountServiceImpl.java`:89-91) and never published: `TenantCreatedV1` and
  `TenantProjectionV1` carry id, slug, display name and status only (ADR-0062 l.200-203). Because one account may own several tenants, `home_currency`
  cannot be a tenant's ledger currency (memo §2.1).
- Only pos-security-service replicates the projection (`ext_tenant`, `V3__ext_tenant.sql`) and serves `GET /v1/tenants/me` from it; the frontend loads that
  once per tenant binding (`FE src/app/core/services/auth.service.ts`:83-100). pos-location consumes `tenant.created` to seed per-tenant defaults
  (pos-location `TenantEventsListener.java`). Other modules enumerate tenants through pos-tenancy-common's `TenantRegistry`, whose remote variant
  (`RemoteTenantRegistry`) is a cached, shared-secret REST lookup against pos-tenant that returns the projection shape, lists ACTIVE tenants only and keeps
  its last snapshot during an outage (ADR-0062 changelog, 2026-09-10).
- pos-tenancy-common already ships the shared parse for projection consumers (`TenantProjectionEvent.java`).

| Option | For | Against |
| --- | --- | --- |
| (a) `functionalCurrency` on `TenantCreatedV1` and `TenantProjectionV1` (additive, schema version 1); every module that creates, validates or books money keeps it in its own replica, seeded from `tenant.created` as ADR-0062 §7 already directs and backfilled by the owner's re-emit (ADR-0044 §4) | ADR-0044 R3 as written; durable and replayable; the shared parse exists | One more consumer in roughly eight modules |
| (b) Read it from the `RemoteTenantRegistry` snapshot | No new consumer: the endpoint already returns the projection shape | Turns an infrastructure lookup, decided for tenant iteration, into a business-data read that ADR-0044 would have to grant; ACTIVE tenants only; after a restart during a pos-tenant outage a module knows only its static seed |
| (c) Configure it per module | Fast | Modules drift apart: the defect class this ADR removes |
| (d) A token claim or gateway header | Present on every request | Identity tokens are not reference data (an ADR-0040 claim-contract change); absent in Kafka consumers and scheduled jobs |

**Recommendation (PROPOSED): (a).**
- `TenantCreateRequest` gains a required, ISO-validated `functionalCurrency`. The platform-admin form may prefill it from the owning account's
  `home_currency`, but the value is chosen explicitly. The account's field keeps whatever meaning it has (OP-5).
- The value never changes (MC-1; OP-1 on exactly when it locks). An immutable value makes every replica correct once written; the only gap is between
  creation and the moment a consumer processes `tenant.created`, and the fail-closed rule below covers it.
- A shared accessor in pos-tenancy-common reads the module's replica. If a module has not learned the value yet, a money-creating operation fails with a
  retryable error in the ADR-0017 envelope; it never falls back to a default.
- Stage A consumers: pos-accounting, pos-invoice, pos-order, pos-workorder, pos-price, pos-catalog, pos-inventory, pos-customer and pos-security-service.

**Decision:** pending, owner.

### 5.3 PC-3 — Every amount states its currency

**EXISTING.** DECISION-PRICING-001 already requires a currency code with every amount, and TEST_COVERAGE_POLICY l.1021-1023 says currency selection refuses
to guess. The rule is not followed elsewhere (§3.3). Only one validator checks the ISO list (`@IsoCurrencyCode`); the others check `^[A-Z]{3}$`, a length,
or nothing. Currency columns are `varchar` of length 3, 8, 16 or 255.

| Option | For | Against |
| --- | --- | --- |
| (a) An explicit ISO 4217 code with every amount, no implicit default | Detects a missing currency; Stage B needs no second contract migration | Adds a field to currency-less contracts |
| (b) An amount without a code is in the tenant's functional currency | Cheapest for Stage A | Every contract migrates again for Stage B; contradicts DECISION-PRICING-001; a missing currency becomes undetectable |

**Recommendation (PROPOSED): (a), as six rules.**
- **R-1** Every aggregate, DTO and event that carries money states the ISO 4217 code that governs it. A document (estimate, order, invoice, payment, refund,
  credit, purchase order, vendor bill, settlement, journal entry) has one **document currency** for all its amounts; an amount in another currency carries
  its own code beside it, for example a functional-currency equivalent in accounting.
- **R-2** No implicit default in main code: no `'USD'` literal, `DEFAULT 'USD'` column, `@Builder.Default` or `@PrePersist` currency, and no deployment-wide
  property. Where a default is genuinely wanted, such as a quote requested without a currency, it is the tenant's functional currency, read through PC-2's
  accessor.
- **R-3** Codes are validated against the ISO 4217 list (PC-4), not a pattern.
- **R-4** Currency columns are three characters and `NOT NULL` wherever the row carries money.
- **R-5** New fields are named `currencyCode`. Existing `currency` and `currencyUomId` names stay, because renaming an event field is a breaking change
  (ADR-0044 §3) with no functional gain.
- **R-6** A money threshold or limit is tenant configuration stated in the functional currency, never a code constant (the thresholds in §3.3).

**Decision:** pending, owner.

### 5.4 PC-4 — A shared money library, not a shared wire type

**EXISTING.** There is no shared money type: pos-price has an internal `MoneyAmount` DTO and pos-catalog a private record (`PricingClientImpl.java`:79).
The codebase uses two ISO sources: nv-i18n's `CurrencyCode` behind `@IsoCurrencyCode` (pos-tax-common `IsoCurrencyCodeValidator.java`; `pom.xml`:61-62)
and the JDK's `java.util.Currency` (pos-inventory `ReceiptUnitCosts.java`:52). `pos-shared-dtos` already holds platform value types (`UUIDv7Id`, ADR-0013)
and is a dependency of `pos-domain-events`.

| Option | For | Against |
| --- | --- | --- |
| (a) In `pos-shared-dtos`: a `Money` value (amount and code) for computation, a currency-unit helper (exponent, minor-unit conversion, representability check) and the one `@IsoCurrencyCode`; wire shapes stay flat (a document currency plus decimal amounts) | One ISO list and one exponent source; the `×100` bug class has one fix | One more shared type to govern |
| (b) A `Money` object in every DTO and event | Self-describing amounts | Breaks every OpenAPI spec, SDK and event (`.v2` everywhere) to say what a document currency already says |
| (c) Nothing shared | No coupling | Exponent logic re-implemented per module |

**Recommendation (PROPOSED): (a).** The exponent source is ISO 4217 as shipped with the JDK (`java.util.Currency`), pinned by the JDK version; nv-i18n is
retired from the validator so that the platform has one list.
**Decision:** pending, owner.

### 5.5 PC-5 — Decimals for new contracts; minor units only beside their code

**EXISTING.** Most money is decimal (`numeric(19,4)`, `(19,2)`, `(15,2)`, `(12,2)`, `(38,2)`, `(10,4)`). Minor units (`bigint`) are used by pos-order
purchase orders (`V1__baseline_order.sql`:299-303, 339-341), pos-inventory `ext_purchase_order` (`V1__baseline_inventory.sql`:319-324), `GoodsReceiptRecordedV1`
and `GoodsReceiptLine` (which carry no currency) and some accounting columns (`V1__baseline_accounting.sql`:1067-1070). DECISION-PRICING-001 chose decimal
strings over minor units. Conversion is exponent-aware only in `ReceiptUnitCosts.java` (falling back to 2) and a fixed `×100` elsewhere (§3.3).

| Option | For | Against |
| --- | --- | --- |
| (a) Decimals for new contracts; existing minor-unit fields stay, each in the same record as its currency code, converted only through the PC-4 helper | No churn in purchase-order contracts; ends the `×100` bug class | Two representations remain |
| (b) Convert every minor-unit field to decimals now | One representation | Rewrites purchase-order contracts and events for no Stage A gain |
| (c) Minor units everywhere | Exact integers | Contradicts DECISION-PRICING-001 and every ledger column; an amount is unreadable without the exponent, which an ISO 4217 amendment can change |

**Recommendation (PROPOSED): (a).** `GoodsReceiptRecordedV1` gains a currency (PC-8): a minor-unit amount without one cannot be read for JPY or KWD.
**Decision:** pending, owner.

### 5.6 PC-6 — Scale and rounding follow the ISO 4217 exponent

**EXISTING.** Two decimals are fixed in tax, accounting, estimates and quotes, and tolerances are 0.01 or 0.0001 (§3.3). The rule "HALF_UP with
currency-scale precision; round per line, then sum" exists (accounting AGENT_GUIDE l.147), but "currency scale" was never defined: question G21
(l.1039-1041) is still marked TODO. pos-price rounds unit prices HALF_EVEN (`PriceQuoteServiceImpl.java`:98) while the other modules use HALF_UP.

| Option | For | Against |
| --- | --- | --- |
| (a) Exponent-driven, by the rules below | One rule is correct for 0-, 2- and 3-decimal currencies | Touches every rounding site |
| (b) Keep two decimals, add per-currency exceptions | Smallest change for USD | Exceptions multiply; each new currency is a code change |
| (c) Four decimals for everything | Simple | Payable amounts become unrepresentable (JPY 0.5) |

**Recommendation (PROPOSED): (a).**
- An amount that changes hands (a line total, tax amount, document total, payment, refund, credit or ledger amount) has the scale of its currency's ISO 4217
  exponent: 0 for JPY, 2 for USD, 3 for KWD.
- Round HALF_UP per line, then sum (AGENT_GUIDE l.147). The pricing domain reconciles or justifies pos-price's HALF_EVEN (OP-11).
- Unit prices, unit costs, rates and percentages may carry a declared extra precision; they are never presented as payable amounts.
- Every 0.01 tolerance becomes one minor unit of the currency concerned. `@DecimalMin("0.01")` becomes "greater than zero and representable in the
  currency". An amount with more decimals than its currency allows is refused at the edge with 422.
- Storage stays `numeric(19,4)`, which covers exponents 0 to 4; columns at scale 2 widen to 4.
- The ledger's own scale and the conversion residual are MC-8. The invented 0.01 amount goes (§10, DF-7).

**Decision:** pending, owner.

### 5.7 PC-7 — Cash rounding

**EXISTING.** Cash rounding is a recorded non-goal: tax plan l.27 ("cash rounding (0.05)"), order plan l.36, order spec l.348 ("USD cent precision
suffices").

**Why it matters.** In some currencies the smallest coin in circulation is larger than the ISO minor unit, so cash payments are rounded while card payments
are not. The example to confirm with each jurisdiction: Canada withdrew its one-cent coin in 2013 and rounds cash totals to CAD 0.05. This is not verified
in this repository (OP-9).

| Option | For | Against |
| --- | --- | --- |
| (a) Keep the non-goal | No work | A tenant in such a currency cannot take cash correctly |
| (b) Round the cash tender in pos-order's register; document totals and tax stay at the minor unit; the difference posts through accounting to an account and mapping key the owner names | The tax base and card payments are untouched | A tender rounding line and a posting mapping (an owner decision, never invented) |
| (c) Round the document total | One place | Changes the tax base; card payers pay the rounded total as well |

**Recommendation (PROPOSED): (b)**, required only for currencies whose cash increment is larger than the minor unit, and decided before such a tenant takes
cash. Cash increments per currency are reference data, confirmed per jurisdiction.
**Decision:** pending, owner.

### 5.8 PC-8 — Event contracts gain the currency additively

**EXISTING.** ADR-0044 §3 (l.107) allows additive changes within a version and requires `.v2` with dual publishing for breaking ones. The in-place
`schemaVersion` bump has precedent: the ADR-0044 amendment of 2026-09-02 moved `catalog.service.updated` to schemaVersion 2, following `ProductUpdatedV1`.
The tax plan repeats the rule (l.22).
- **Currency present, stamped USD by producers,** Javadoc "USD platform-wide": `PaymentSettledV1`, `PaymentReversedV1`, `OrderCompletedV1`,
  `RegisterSessionClosedV1`.
- **Currency present and real:** `SettlementReportedV1` (validated), `SupplierInvoiceReceivedV1`, `SupplierPriceCatalogUpdatedV1`,
  `SupplierPriceCatalogImportCompletedV1`, `SupplierWorkorderAuthGrantedV1`, `PurchaseOrderRequestedV1`, `PurchaseOrderUpdatedV1`, `BillingRulesUpdatedV1`.
- **Money with no currency:** `InvoiceUpdatedV1` (with `TaxBreakdownLine`), `EstimateUpdatedV1`, `WorkorderUpdatedV1`, `WorkorderServiceCompletedV1`,
  `DepositCreditAppliedV1`, `OrderReturnedV1`, `OrderCommissionImpactV1`, the warranty claim and reimbursement events, `GoodsReceiptRecordedV1` and
  `GoodsReceiptLine` (minor units), and the inventory cost events `ProductValueChangedV1`, `ConsumptionRecordedV1`, `ScrapPostedV1` and
  `InventoryAdjustedV1`.
- Accounting's `OrderEventsListener` and `RegisterOverShortPostingService` never read `currencyCode`.

| Option | For | Against |
| --- | --- | --- |
| (a) Additive field with an in-place schemaVersion bump | No topic migration; precedent exists; an absent currency has a well-defined meaning today, because every tenant is USD | Consumers need a transition rule |
| (b) `.v2` topics with dual publishing | A required field from day one | Consumers of about 16 event types migrate and their producers dual-publish, for one field |
| (c) No field; consumers use the tenant's functional currency | Nothing to change for Stage A | Stage B impossible without (a) or (b) later; hides a missing currency |

**Recommendation (PROPOSED): (a), with five rules.**
- **E-1** Producers stamp the document currency, never a constant, from the first release that carries the field. The four "USD platform-wide" events need
  only the producer fix and new Javadoc.
- **E-2** No consumer assumes USD.
- **E-3** Until the Stage A exit (every producer stamps the field), a money-bearing event without it means the producing tenant's functional currency. That
  is exact for every event already on the topics, because every existing tenant is USD (MC-1).
- **E-4** After the Stage A exit, a missing currency is malformed and takes the consumer's documented failure path (ADR-0044 §4); it is never defaulted.
- **E-5** A consumer that can handle only the functional currency, which is every consumer in Stage A, compares and parks a mismatch visibly (PC-9).

Renaming a field still requires `.v2` (R-5). **Decision:** pending, owner.

### 5.9 PC-9 — Stage A never books another currency at par

**EXISTING.** The settlement guard is the pattern: a settlement in a non-base currency is rejected deterministically at ingest and persisted as `REJECTED` for
visibility (`SettlementReconciliationServiceImpl.java`:76-83, :99-101, :146-150). Bank reconciliation D18 refuses with 422 `CURRENCY_NOT_SUPPORTED`. The
counter-example: a supplier invoice in another currency becomes a vendor bill at face value (`SupplierInvoiceEventsListener.java`:190-219). pos-order accepts
any non-blank PO currency (`CreatePurchaseOrderRequest.java`:36-41) and pos-price any three letters (`PriceQuoteRequest.java`:56).

| Option | For | Against |
| --- | --- | --- |
| (a) Inbound facts parked in a visible exception state with a currency reason; requests refused with 422 `CURRENCY_NOT_SUPPORTED`, the code D18 introduced, reused platform-wide | Nothing is mis-booked; one error code | Foreign documents wait for B1 or manual handling |
| (b) Book at face value | No work | Mis-states payables and inventory (today's defects DF-1 and DF-6) |
| (c) Convert at a hand-entered rate | Unblocks foreign documents | Stage B without its invariants (rates, realized FX, audit) |

**Recommendation (PROPOSED): (a).** For a Stage A tenant the allowed set is the functional currency alone; B1 widens it to the allow-list (MC-2, PC-16).
**Decision:** pending, owner.

### 5.10 PC-10 — Selling prices per currency

**EXISTING.** pos-price, the transactional quote system of record (ADR-0054), keys prices by currency and refuses to guess (§3.5). An omitted currency falls
back to the deployment-wide `pos.price.default-currency` (`application.yml`:69-71), and the reference seed holds 121 USD values. Labor-rate resolution does
not filter by currency (`LaborRateResolutionServiceImpl.java`), and estimates copy the rate's currency without comparing it (pos-workorder
`EstimateServiceImpl.java`:1030). In pos-catalog (reference role), a price-book rule holds `{"amounts":{"USD":…},"defaultCurrency":…}` while `price_book`
itself has no currency column (`V1__baseline_catalog.sql`:267-280), which contradicts DECISION-PRICING-004's "currencyUomId required (single-currency per
book)" (DOMAIN_NOTES l.76-80). Catalog location overrides have no currency (:228-251). The catalog's quote client sends no currency and turns a missing MSRP
currency into USD (`PricingClientImpl.java`:39, :63).

| Option | For | Against |
| --- | --- | --- |
| (a) Explicit prices per currency; the default is the tenant's functional currency; resolution never converts | Predictable, round (and, where required, tax-inclusive) prices; no rates in a utility module; how pos-price already works | A list to maintain per sold currency (B1) |
| (b) Convert functional-currency prices at quote time | One list | Prices move with the rate every day; pos-price would need a rate replica; odd amounts on quotes and shelves |
| (c) Explicit first, converted fallback | Coverage | Both sets of problems; "refuses to guess" no longer holds |

**Recommendation (PROPOSED): (a).**
- Stage A: the default currency is the tenant's functional currency (PC-2); `pos.price.default-currency` goes; seeds are written per tenant in its
  functional currency; the catalog's MSRP fallback and currency-less quote request go.
- B1: a quote in a currency outside the allow-list is refused; labor-rate resolution filters by the estimate's currency.
- DECISION-PRICING-004 is restated as "each price carries its currency; a book or scope may hold prices in several currencies; resolution never converts or
  guesses". That matches both pos-price's rows and pos-catalog's rule map. Catalog location overrides need no currency column: under ADR-0054 any
  transactional use of them moves to pos-price, whose overrides carry one.

**Decision:** pending, owner.

### 5.11 PC-11 — Tax in the document currency

**EXISTING.** `TaxCalculationRequest.currencyCode` defaults to USD and is ISO-validated (pos-tax-common `TaxCalculationRequest.java`:79-88);
`TaxCalculationResponse` has no currency. pos-tax passes the currency to AvaTax (`AvalaraTaxProvider.java`:200) but rounds at `MONEY_SCALE = 2` (:89, :506)
and distributes residual cents at scale 2 (`TaxTotalsReconciler.java`:36-39). All three callers send USD (§3.3). ADR-0021 l.55 says `currencyCode` "should
be supplied when available". The tax plan scopes pos-tax as a "US sales-tax service" (l.7), lists multi-currency tax, price-included pricing and cash
rounding as non-goals (l.27-28) and allows only additive changes to `pos-tax-common` (l.22).

| Option | For | Against |
| --- | --- | --- |
| (a) Callers send the document currency; `currencyCode` required with no default; rounding and residual distribution at the currency's exponent; the response echoes the currency | Tax in the right currency and precision; a mismatch is detectable | Tightens a shared DTO beyond the plan's additive rule; no current caller breaks, since all three send the field |
| (b) Keep the default | No change | A caller that forgets the field gets USD tax silently |

**Recommendation (PROPOSED): (a).** ADR-0021 l.55 and the tax plan's l.22 rule are amended for this one field. Which jurisdictions pos-tax can serve is not
a currency question: it is the first item of the go-live gate (PC-15). Converting tax into the functional currency and the filing currency are MC-11 (B1).
**Decision:** pending, owner.

### 5.12 PC-12 — Payment gateway currency

**EXISTING.** pos-invoice's gateway port records carry an amount only (`PaymentGatewayRequest.java`, `GatewayCaptureRequest.java`:14,
`GatewayRefundRequest.java`:5), and its only adapter is `UnavailablePaymentGatewayAdapter`. pos-accounting's `StripePaymentGateway` sends the request's
currency but converts to cents with `movePointRight(2)` (`StripePaymentGateway.java`:64-76). Payment events are stamped USD. Payouts: one settlement per
payout currency (plan D-14 l.398; pos-invoice `SettlementSourcePort.java`:29).

| Option | For | Against |
| --- | --- | --- |
| (a) Gateway requests carry the currency; minor units via the PC-4 helper plus each adapter's own rules for zero- and three-decimal currencies; Stage A charges only the functional currency; B1 charges the invoice currency where the tenant's processor account supports it | Correct amounts for every exponent; foreign cards possible in B1 | Adapter tests per currency |
| (b) Only ever charge the processor account's currency | Simple | A foreign-currency invoice (B1) cannot be paid by card |

**Recommendation (PROPOSED): (a).** Payment-intent amount columns widen to `numeric(19,4)`. Payouts follow D-14 and MC-7 (see OP-2).
**Decision:** pending, owner.

### 5.13 PC-13 — Supplier prices, purchase orders and bills in foreign currencies

**EXISTING.** Supplier price entries carry market scope, country and currency, and never enter sell-price resolution (ADR-0053 l.65 and §4;
`V1__baseline_catalog.sql`:547-548). The codecs insist on a currency (pos-supplier `EdiwheelB40PricatCodec.java`:117-118, `EdiwheelB33InvoiceCodec.java`:230-235,
`MichelinS2SWorkorderAuthCodec.java`:259-265) and `supplier_invoice.currency` is `NOT NULL` (`V1__baseline_supplier.sql`:173). One vendor may have several
profiles, for example Michelin EU and Michelin NA (`docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md` l.289), so
foreign-currency supplier documents can arrive today. Downstream the currency is lost: vendor bills (DF-1), purchase suggestions (DF-5), receipt cost (DF-6).

| Option | For | Against |
| --- | --- | --- |
| (a) Supplier prices keep their market currency (ADR-0053 unchanged). POs and bills must be in the functional currency in Stage A (others parked, PC-9) and in an allowed currency from B1. The receipt converts cost into the functional currency at the receipt date with accounting's rate (memo §2.4) | No conversion hidden in ingestion; valuation stays inventory's (ADR-0048) | Foreign-currency supplier documents wait for B1 |
| (b) Convert supplier prices at ingest | Comparable numbers immediately | A rate baked into reference data, stale the next day, from an unstated source |

**Recommendation (PROPOSED): (a).** Vendor selection compares like-currency offers only until B1; from B1 it may rank offers across currencies with the
published booking rate, for comparison only, never for booking. This also answers `domains/inventory/inventory-questions.md` l.730-733: the currency is a
fact of the supplier's price entry and is read-only.
**Decision:** pending, owner.

### 5.14 PC-14 — Frontend money display and input

**EXISTING.** See §3.3: every amount is formatted as en-US because no provider sets a locale and no pipe passes one; bare pipes render USD; literals and
fallbacks say USD; minor units use `×100` and `÷100`. `LocaleService` holds the user's locale. Rule I18N-07 bans `toLocale*`, `new Intl.*` and `toFixed` in
pages and components and allows one allowlisted core formatter; nothing checks where a currency code comes from. ADR-0030 §4 requires Intl/CLDR formatting
but says nothing about the currency's source.

| Option | For | Against |
| --- | --- | --- |
| (a) Rules F-1 to F-7 below | Correct in Stage A and Stage B; enforced by a rule | Every money template changes once |
| (b) A tenant-wide `DEFAULT_CURRENCY_CODE` provider | Stage A in one line | Wrong in Stage B; hides a missing currency; separators stay en-US |
| (c) Leave it | No work | Every non-USD tenant sees USD |

**Recommendation (PROPOSED): (a).**
- **F-1** The currency comes from the record: every amount is formatted with the currency of the record it belongs to. The locale decides separators and
  symbol placement, never the currency. This corrects `domains/accounting/accounting-questions.md` l.1163 ("`$` or `USD` based on user locale") and
  `docs/I18N/I18N_ECOSYSTEM_SUMMARY.md` l.110 ("EUR in Spain/France, USD in US").
- **F-2** The locale is the user's selected locale, applied through one allowlisted core money formatter (the mechanism I18N-07 already provides). No global
  `DEFAULT_CURRENCY_CODE`.
- **F-3** No `'USD'` literal or fallback in feature code. In Stage A, where a DTO has no currency field yet, the formatter uses the tenant's functional
  currency from `/v1/tenants/me`, which is exact in Stage A by definition. The fallback is removed before B1, when every money DTO carries its currency.
- **F-4** Minor units convert by the currency's exponent through one core helper. Amount inputs take their step and maximum decimals from the exponent.
  Currency inputs are lists (the functional currency in Stage A, the allow-list in B1), not free text.
- **F-5** Show the symbol where one currency is on screen (CLDR disambiguates `CA$` and `US$` by locale); show the ISO code wherever one screen shows more
  than one currency.
- **F-6** Arch rules beside I18N-07: no currency pipe without a currency argument; no ISO-code literal in pages, components or services outside specs and
  fixtures; no `×100` or `÷100` on minor-unit fields.
- **F-7** The frontend never rounds or recomputes money (DECISION-PRICING-001); the order cart's in-template line total is a defect (§10, DF-11).

**Decision:** pending, owner.

### 5.15 MC-10 and the CAP-316 plant currency (architect's view)

**EXISTING.** `accounting_location_fx_rate` holds a yearly `local_currency_per_usd` and `average_rate` per location at `numeric(12,6)`
(`V1__baseline_accounting.sql`:129-141). `LocationProfile.currencyCode` is the plant's "reporting currency" (`LocationProfile.java`:61-64). The Labor &
Overhead report converts only line 2.11.4 from local currency to USD at the average rate (`LaborOverheadReportServiceImpl.java`:95-122, :276-290). CAP-316
calls the rate "Presentation context only" (`docs/capabilities/CAP-316/CAP-316-spec.md` l.144-147). In a one-ledger tenant a location has no currency of its
own.

**Architect's view (the owner decides, memo MC-10):** read CAP-316 as a report-only view. A location profile's currency must equal the tenant's functional
currency, or the field goes; the table is relabelled as a report presentation rate between the functional currency and USD, and never becomes a booking rate.

### 5.16 PC-15 — Jurisdictions gate go-live

The memo (§1, §2.7): "Tax, not accounting, decides when a non-USD tenant can go live"; a non-USD tenant operates under a non-US tax regime, which accounting
cannot rule on. The same holds beyond tax. The recorded non-goals a new jurisdiction can trigger are ADR-0047 D-2 (tamper-evident ledgers; its revisit
trigger at l.53-54 names a new jurisdiction), accounting plan ground rule 6 (statutory localization: chart templates, tax grids, EDI and Peppol; l.33-34), the
tax plan's non-goals (price-included pricing, cash rounding; l.27-28) and the order plan's (localized receipts; l.36).

| Option | For | Against |
| --- | --- | --- |
| (a) A per-jurisdiction readiness sign-off (checklist in §11), tax first | Stage A can finish early; each market opens on evidence | One sign-off per market |
| (b) Stage A completion alone | Faster | A tenant could go live where the platform does not meet the tax, cash or statutory rules |

**Recommendation (PROPOSED): (a).** **Decision:** pending, owner.

### 5.17 PC-16 — Where the allow-list lives (B1)

**EXISTING.** No allow-list exists. The memo recommends one (MC-2) and leaves its permission tokens to the owner (PERMISSION_TAXONOMY l.1304 lists
`accounting:currency:*` only as a future family).

| Option | For | Against |
| --- | --- | --- |
| (a) pos-accounting owns it and publishes it as a fact; selling and purchasing modules replicate it and refuse a disallowed currency when a document is created | The ledger is where a currency becomes real (rates, revaluation, bank accounts); refusal comes early | Selling modules consume an accounting fact |
| (b) pos-tenant, beside the functional currency | One place for a tenant's currency settings | pos-tenant is reachable by platform staff only (ADR-0062 §7), so every change to a finance setting would need platform staff |
| (c) No list; the ledger refuses at posting | No new surface | Refusal comes after the customer has the invoice |

**Recommendation (PROPOSED): (a).** The functional currency is always allowed. **Decision:** pending, owner.

### 5.18 PC-17 — Who converts, and how rates travel (B1)

**EXISTING.** The only rate table is `accounting_location_fx_rate`, which is report-only (memo §2.3). No rate facts exist. ADR-0048 §3: accounting does not
infer or recalculate unit cost.

| Option | For | Against |
| --- | --- | --- |
| (a) Accounting converts every posting from the transaction amounts other modules send; inventory converts receipt cost with accounting's published rate; no other module converts. Rates are tenant-scoped (the ADR-0062 §5 default) and published as facts | The rate and the entry lock together with the period (memo §2.3); one conversion path | Inventory keeps a rate replica |
| (b) A synchronous FX utility module owning rates and conversion | Any module can convert, for example for quotes | A second home for rates that accounting must lock and audit anyway; one more utility under ADR-0044 §1 |
| (c) Each module converts with its own rates | Local autonomy | Two functional amounts for one fact |

**Recommendation (PROPOSED): (a).** A future provider feed (memo MC-3) lives in its own module on the ADR-0049 pattern and feeds accounting's store; it does
not own booking rates. Inventory's cost facts carry the transaction amount, the currency and the rate reference used, so that accounting's accrual lines use
the same functional amount (OP-4). **Decision:** pending, owner.

---

## 6. Accounting Requirements (Accounting Domain memo, 2026-09-28)

The memo is the accounting authority for this ADR. This section restates its requirements so that the record stands on its own; the `MC-` rows of §2 are its
owner decisions. Citations in this section are the memo's.

### 6.1 Functional currency per tenant (Stage A)

- **EXISTING.** `gl_account`, `journal_entry` and `journal_entry_line` have no currency. The only base currency is the deployment-wide property read by the
  settlement guard. USD is hard-coded elsewhere (`CreditMemoServiceImpl.java`:65, :319-320; `APPayment.java`:103), and event contracts say "USD
  platform-wide".
- **PROPOSED.** Each tenant has one functional currency, the currency of its single ledger. Accounting uses it at every posting, in rounding, in bank-account
  profiles, in report headers, in revaluation and in every tolerance, and it replaces the deployment property. It is set when the tenant is provisioned and
  locked before the first posting; changing it later means provisioning the tenant again. Existing tenants get USD. Accounting keeps its own copy (ADR-0044).
  **Stage A adds only a functional-currency stamp on the journal-entry header** and no rate columns before they are used (ADR-0047 l.76-77).

### 6.2 Transaction currency on entries and lines (B1)

- **EXISTING.** Each line has one debit/credit pair at `numeric(19,4)` (`JournalEntryLine.java`:76-80). An entry must balance within 0.0001, checked in the
  entity and by a database trigger (`JournalEntry.java`:229-231; `V1__baseline_accounting.sql`:18-44). Subledger rows carry a currency, but `ext_invoice` and
  `vendor_bill` do not.
- **PROPOSED.** Entry header: the functional currency, the entry's one transaction currency, the rate, rate date, rate type and rate source. Line: debit and
  credit in the transaction currency and in the functional currency, plus the rate applied to that line. Today's debit and credit columns become the
  functional amounts, so balances, reports and the trigger keep working. Other modules send transaction amounts and accounting converts; inventory cost is
  the exception (§6.4).

| Invariant | Rule |
| --- | --- |
| **F1** | The functional amounts balance exactly, enforced in the database |
| **F2** | The transaction amounts balance exactly. FX lines and rounding lines carry a transaction amount of 0 and exist only in the functional currency |
| **F3** | A line that creates an item uses the entry rate. A line that settles an existing foreign-currency item carries that item's original (historical) functional amount. The only exception is the rounding residual under MC-8 |
| **F4** | A line posted to an account that has its own account currency must be in that currency |
| **F5** | A reversal inverts every amount at the original rate (extending `JournalEntryServiceImpl.java`:447-448) |
| **F6** | All of this is immutable once POSTED (ADR-0047) |

### 6.3 Exchange rates (B1)

- **EXISTING.** `accounting_location_fx_rate` holds a yearly period-end rate and a yearly average rate per location at six decimals; only the Labor & Overhead
  report reads it. It is not a booking rate, and six decimals lose precision for pairs such as IDR to USD.
- **PROPOSED.** Rate types: the spot rate on the transaction date for postings; the closing rate for revaluation; an average rate only for translation, which
  is out of scope. A rate record holds the currency pair, type, date, rate (at least ten decimals), source (manual, import or feed) and the person who
  entered it, and is never edited once an entry has used it. If no rate exists, the posting fails visibly; it never falls back to 1:1. Sources: manual entry
  and file import first, a provider feed later in its own module (ADR-0049 pattern). Accounting owns booking rates, because the rate is part of what an entry
  means and is locked with its period (MC-3).

### 6.4 Realized FX (B1) and revaluation (B2)

- **EXISTING.** No FX, rounding or translation account is seeded (`R__seed_reference_accounting.sql`). Plan decision D-14 anticipates a future
  `FX_GAIN_LOSS` category. Payment application posts one amount to both sides of the entry (`PaymentApplicationGLPostingEventHandler.java`:118-124). Period
  close checks only for DRAFT entries (`AccountingPeriodServiceImpl.java`:142-146).
- **PROPOSED, realized FX (B1).** When a foreign-currency receivable or payable is settled (payment application, AP payment, customer-credit application or
  refund, processor settlement), relieve the item at its historical functional amount, value the cash at the settlement-date spot rate, and post the
  difference in the same entry on a line that exists only in the functional currency. Cash does not track individual rates; revaluation absorbs its drift.
- **PROPOSED, unrealized FX (B2).** At period end, revalue open foreign-currency monetary balances (MC-5) at the closing rate, dated at period end and
  reversed the next day (MC-4), which matches ADR-0057's movement basis. Non-monetary items are never revalued. Accounting does not recalculate inventory
  cost (ADR-0048), so inventory converts a foreign purchase cost with accounting's booking rate and its cost events arrive in the functional currency.
  Period close gains the readiness check "revaluation posted at the current closing rate". Revaluation passes the period gate (AD-012), has no extra effect
  when run twice for the same period, account and currency, and is redone when a reopened period receives new foreign-currency postings.

### 6.5 Rounding (Stage A)

- **EXISTING.** Two decimals hard-coded (POSTING_RULES_SCHEMA l.259; `PostingRuleEvaluatorImpl.java`:118; `CreditMemoServiceImpl.java`:68; DOMAIN_MODEL
  l.226); minor-unit conversions assume two decimals; requests validated with `@DecimalMin("0.01")`; tolerances of 0.01; a zero-amount default-mapping event
  posts an invented 0.01 (`PostingRuleEvaluatorImpl.java`:369-370).
- **PROPOSED.** The scale is the number of decimals ISO 4217 defines for the currency concerned; every 0.01 becomes one minor unit of that currency; round
  HALF_UP per line, then sum; `numeric(19,4)` covers currencies with 0 to 4 decimals; the invented 0.01 is removed. This answers AGENT_GUIDE G21. The
  ledger scale and the conversion rounding residual are MC-8.

### 6.6 Tax

pos-tax calculates in the document's currency. Accounting stores tax in both transaction and functional amounts, reports tax in the functional currency, and
compares functional amounts in the tax-liability snapshot (which today reconciles to GL account 2200 within 0.01, `FinancialReportingServiceImpl.java`:117-120,
:1177). Which rate converts tax and whether a jurisdiction requires filing in another currency are jurisdiction rules to be asked (MC-11).

### 6.7 Reporting

Ledger reports are in the functional currency. Aged AR/AP and customer statements also show the open amount in the transaction currency. Analytics money
measures follow the ledger (ADR-0057 §1) and are never summed across currencies. Translation into a presentation currency is out of scope: no consolidation,
no average rates, no translation reserve. The Labor & Overhead report's plant currency conflicts with a one-currency ledger (MC-10, §5.15).

---

## 7. Consequences per Module and Stage (PROPOSED)

| Module | Stage A | B1 | B2 | B3 |
| --- | --- | --- | --- | --- |
| pos-tenant | `tenant.functional_currency`, required on create and immutable; carried on `TenantCreatedV1` and `TenantProjectionV1`; existing tenants backfilled USD; `account.home_currency` unchanged (prefill only) | — | — | — |
| pos-security-service | `ext_tenant` gains the column; `/v1/tenants/me` returns it | — | — | — |
| pos-tenancy-common | Shared accessor over the module's replica, failing closed; replica listener template | — | — | — |
| pos-shared-dtos | `Money` value, currency-unit helper, `@IsoCurrencyCode` moved from pos-tax-common (one ISO source) | — | — | — |
| pos-domain-events | Currency on money-bearing events (additive, schemaVersion bump); "USD platform-wide" Javadoc removed from four events | Rate fact and allow-list fact (accounting); inventory cost facts carry transaction amount, currency and rate reference | Revaluation facts only if a consumer is named (ADR-0044 R6) | — |
| pos-accounting | Functional currency replaces `accounting.ledger.base-currency`; journal-entry header stamp; exponent rounding and one-minor-unit tolerances; USD defaults removed (AP payment, credit memo, billing-rules replica); vendor bill keeps its currency and a mismatch is parked; payment and invoice currency compared; order-event currency read; invented 0.01 removed; bank reconciliation Stage A (§8); Labor & Overhead semantics (MC-10) | Dual amounts, rate store, F1 to F6 (F1 and F2 in the database); realized FX in payment application, AP payment, credit application and refund, settlement; the allow-list (PC-16); aged AR/AP in transaction currency; tax in both amounts | Reversing revaluation job and close readiness check | Foreign-currency bank-account profiles; bank reconciliation Stage B (§8) |
| pos-invoice | Document currency on invoices, payment intents, refunds and deposits, with no USD default; payment events carry the real currency; tax in the document currency; gateway port carries the currency; payment intents widen to `numeric(19,4)`; document content carries the currency; 500.00 limits become functional-currency configuration | Invoices in allowed currencies; payment currency equals invoice currency; settlements per D-14 and MC-7 | — | — |
| pos-order | Sales orders, payment records, cash movements and register sessions carry a currency; USD literals removed (events, reversals, tax); reversals forward the currency; override threshold becomes configuration; cash rounding if PC-7 applies | Orders in allowed currencies; register sessions count cash per currency if foreign cash is accepted | — | — |
| pos-price | Default currency per tenant; deployment property and USD seeds removed (seeds per tenant); quote rounding at the exponent | Per-currency price lists; quotes refused outside the allow-list; labor rates filtered by currency | — | — |
| pos-catalog | MSRP fallback to USD removed; quote client sends the currency; rule-currency rule restated (DECISION-PRICING-004); location overrides left to ADR-0054 | Reference prices per currency | — | — |
| pos-tax, pos-tax-common | `currencyCode` required; rounding at the exponent in the provider, reconciler and test-mode calculator; response echoes the currency; regime readiness per jurisdiction | Tax conversion per MC-11 | — | — |
| pos-supplier | No change: codecs already require a currency; facts already carry it | — | — | — |
| pos-inventory | Exponent helper replaces `×100` (vendor selection); purchase-suggestion conversion refuses mixed currencies and drops the USD default; goods-receipt events carry a currency; PO currency must be the functional currency (PC-9) | Receipt cost converted with accounting's rate; cost facts carry transaction amount, currency and rate reference; rate replica; vendor selection ranks across currencies | — | — |
| pos-workorder | Estimate currency required (no USD default); tax in the estimate currency; `setScale(2)` replaced by the exponent; rate currency compared with the estimate currency; two-decimal columns widened; estimate document formats in its currency | Estimates in allowed currencies | — | — |
| pos-customer | Billing-rule currency stays null when unset (and accounting's replica stops inventing USD); tier thresholds become functional-currency configuration | Billing currency per customer from the allow-list | — | — |
| pos-documents | Callers pass the document currency and a document locale; templates format money with both (OP-12) | — | — | — |
| pos-warranty | Money on warranty events gains a currency | Reimbursements in a foreign currency | — | — |
| pos-location | No change: a location has no currency of its own (PC-1) | — | — | — |
| pos-mcp-server and analytics | Money measures labelled with the functional currency | Measures in the functional currency, never summed across currencies (ADR-0057 amended) | — | — |
| Frontend | Core money formatter (record currency plus user locale); no USD literals or fallbacks except the Stage A functional-currency fallback; exponent-based minor units; arch rules; tenant-create form gains the functional currency | Currency lists from the allow-list; transaction and functional amounts on accounting screens; aged AR/AP by currency | Revaluation screens | Bank-account currency; reconciliation screens in the account currency |
| SDK (`@durion-sdk/*`) | Regenerated through `API Artifacts Sync` after every contract change: `functionalCurrency` on tenant responses; currency on sales-order, vendor-bill and invoice-detail responses; "defaults to USD" descriptions removed | Rate and allow-list operations | Revaluation operations | Bank-profile currency |

---

## 8. Bank Reconciliation Specification Changes (from the Accounting memo §4)

`domains/accounting/SPEC-manual-bank-reconciliation.md` is accepted; D18 governs its phase 1 until this ADR is accepted. On acceptance:

| Stage | Change |
| --- | --- |
| A | D18 (l.1509) becomes: refuse (`CURRENCY_NOT_SUPPORTED`) any bank-account profile, statement or feed in a currency other than the tenant's functional currency. A profile's currency is the functional currency, including the profile the intake creates. §3.1 (l.267-272), §4.2 (l.611-612), §4.10 (l.911), §6.1 (l.1161) and §6.4 (l.1213) change to match. "No FX terms in E3" still holds |
| A | ±0.01 becomes one minor unit of the functional currency in E1 (l.242) and M1 (l.336), the opening terms and E3/E4 (l.472, l.491-495), candidate scoring (l.738), the residual bound (l.371, l.820-821) and clearing-account aging (l.825, l.1015). The constant at `BankReconciliationServiceImpl.java`:85 goes |
| A | Import rejects amounts with more decimals than the currency allows; the `$`-specific parsing (l.55) generalizes through `decimalFormat` (l.315); `BANK_REC_OTHER_APPROVAL_THRESHOLD` (l.817) is stated in the functional currency; the §8.2 tests (l.1389-1390) add currencies with 0 and 3 decimals |
| B3 | D18 becomes: a profile's currency can be any currency the tenant allows (MC-2), fixed from the account's first committed statement or first posted line, equal to the account currency of its GL account (F4); each currency held at the bank gets its own GL account; a statement or feed in any other currency is refused; E1 to E4 run in the account's currency on transaction amounts with a one-minor-unit tolerance; FX differences are revaluation and settlement postings, never reconciliation items |
| B3 | That rule covers every E3 term, M1, `approvedGlEndingBalance` and `BALANCE_AGREEMENT` (l.1008); the as-of balance query (`JournalEntryLineRepository.java`:59-64) sums `debit_amount − credit_amount`, which become the functional columns, so for a foreign-currency account it must read the transaction amounts instead |
| B3 | Functional-only lines (revaluation, realized FX, rounding) are not bank events: they are excluded from E3, match candidates, matches, E4 and `UNEXPLAINED_LEDGER_LINES`. Only revaluation changes the account's functional carrying amount; the §5.3 close readiness adds "revaluation posted" for foreign-currency bank accounts |
| B3 | Adjustments (§3.5) post in the account's currency, with the functional amount at the spot rate on the date D7 assigns; the 2360 counter line follows MC-5; a `TRANSFER` between accounts in different currencies (D9, l.1500) is refused (MC-7); the Plaid currency rules (l.1277) stand |

The memo also records, as EXISTING, that today's reconciliation (story F2) stores the currency sent in the request without checking it
(`BankReconciliationServiceImpl.java`:124) and then compares amounts one to one with the ledger. The copies of D18 in the CAP-055 stories
(`docs/capabilities/CAP-055/stories/frontend/CAP_055.406.frontend.md` l.131, `.../backend/CAP_055.2301.backend.md` l.94,
`.../backend/CAP_055.2302.backend.md` l.90) follow the specification.

---

## 9. Documents Superseded or Amended on Acceptance

Listed only; none is edited by this ADR. Each amended document would carry a dated amendment block pointing here, as ADR-0062's amendments did.

| Document | Location | Change on acceptance | Stage |
| --- | --- | --- | --- |
| ADR-0047 | Context driver l.28; D-8 rationale l.60 | "single-currency" struck from the driver and the D-8 rationale; both decisions (D-2, D-8) stand; a note that choosing accounts by currency uses the recorded `dimensions` escape hatch (l.62-64) and that the mapping resolver's fallback must never drop a currency dimension (today none exists, `GLMappingResolverImpl.java`:49-50) | A |
| ADR-0062 | §7 (l.188-203) | The `tenant` aggregate gains an immutable functional currency, carried on the public projection; account, contact and billing data still never leave pos-tenant | A |
| ADR-0030 | §4 (l.58-66) | Adds F-1 and F-2: the currency comes from the record; the locale formats it | A |
| ADR-0021 | l.55 | `currencyCode` must be supplied (not "should") | A |
| ADR-0057 | §1 and the measure definitions | Money measures are in the functional currency and never summed across currencies | B1 |
| ADR-0048 | §3 (l.68-73) | Cost facts are in the functional currency and carry the purchase's transaction amount, currency and rate reference | B1 |
| Accounting parity plan | `domains/accounting/plan-odoo-parity-pos-accounting.md` l.5, ground rule 6 (l.33-34) | Multi-currency and FX gain/loss leave the non-goal list; "Currency columns stay `USD`-defaulted" is withdrawn. D-14's one currency per settlement (l.398) stays | A |
| Tax parity plan | `domains/accounting/plan-odoo-parity-pos-tax.md` l.7, l.22, l.27-28 | Scope no longer US-only; `currencyCode` becomes required (exception to the additive rule for this field); "multi-currency tax" leaves the non-goals at B1; cash rounding reopened by PC-7 where it applies | A, B1 |
| Order parity plan and spec | `domains/order/plan-odoo-parity-pos-order.md` l.5, l.36; `domains/order/spec-pos-order-missing-functionality.md` l.348-349 | "US, single-currency" and "USD cent precision suffices" withdrawn; cash rounding per PC-7 | A |
| Bank reconciliation spec | `domains/accounting/SPEC-manual-bank-reconciliation.md` D18 (l.1509), §3.1, E1 to E4 and the sections in §8; CAP-055 story copies | As §8 | A, B3 |
| DECISION-PRICING-004 | `domains/pricing/.business-rules/DOMAIN_NOTES.md` l.76-80; `STORY_VALIDATION_CHECKLIST.md` l.181 | Restated: each price carries its currency; resolution never converts or guesses (PC-10) | A |
| Pricing contract guide | `domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md` l.124-129 | The omitted-currency fallback is the tenant's functional currency, not `pos.price.default-currency` | A |
| Accounting AGENT_GUIDE | `domains/accounting/.business-rules/AGENT_GUIDE.md` l.163, D5 (l.836-839), G21 (l.1039-1041) | l.163 becomes "Same currency; the difference in functional value posts as realized FX" (B1); D5 assumes a check the code must now perform (DF-2); G21 answered by PC-6 | A, B1 |
| Accounting domain model | `domains/accounting/.business-rules/DOMAIN_MODEL.md` l.226 | "2 decimal places" becomes "the currency's ISO 4217 exponent" | A |
| Posting rules schema | `domains/accounting/.business-rules/POSTING_RULES_SCHEMA.md` §3.1 (l.199-203), §4.2 (l.259) | Per MC-8 | A |
| Cross-domain contracts | `domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` l.55 | CURRENCY "1 per Organization" becomes one functional currency per tenant (ADR-0062 §4 wording) | A |
| Accounting contract guide | `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` l.803-804 | Labor & Overhead currency context per MC-10 | A |
| Accounting domain notes and questions | `domains/accounting/.business-rules/DOMAIN_NOTES.md` l.435; `domains/accounting/accounting-questions.md` l.695-697, l.1163 | "Multi-currency AR" becomes B1 scope; "Defaults to USD" answered by the functional currency; display follows F-1 | A, B1 |
| Accounting comparison | `domains/accounting/comp-vs-pos-accounting-comparison.md` l.42 | "decide explicitly and record it": this ADR is that record | A |
| CAP-054 | `docs/capabilities/CAP-054/CAP-054-backend-implementation.md` l.28, l.604-609 | "single currency for v1.0" and `primary-currency: "USD"` become the tenant's functional currency | A |
| CAP-316 | `docs/capabilities/CAP-316/CAP-316-spec.md` §3.1 (l.144-147) | Per MC-10 | A |
| Seed input contract | `docs/architecture/deployment/data-migration/SEED_INPUT_CONTRACT.md` l.51 | `environment.currency` becomes a per-tenant functional currency | A |
| Frontend guidance | `.claude/instructions/angular-i18n.md` l.166; `docs/I18N/I18N_ECOSYSTEM_SUMMARY.md` l.110 | The `currency:'USD'` example and the "EUR in Spain/France, USD in US" rule replaced by F-1 | A |
| Open questions this answers | `docs/capabilities/CAP-051/CAP-051-TODOS-AND-INTEGRATION-POINTS.md` l.743-745; `docs/capabilities/CAP-278/CAP-278-backend-implementation.md` l.45; `docs/capabilities/CAP-002/stories/frontend/CAP_002.236.frontend.md` l.462; `domains/inventory/inventory-questions.md` l.730-733; `domains/pricing/pricing-questions.md` l.392 | Answered by PC-9 and MC-7 (cross-currency application refused at first), PC-3 (currency on the payload), PC-13 (supplier currency is read-only) and PC-10 (functional currency, not "organization default") | A, B1 |
| `docs/adr/README.md` | Current ADRs table and decision matrix | A row for ADR-0067 (the table is regenerated by `scripts/adr_okf_frontmatter.py`) | On acceptance |

Unchanged and consistent with this ADR: ADR-0053, ADR-0054, TEST_COVERAGE_POLICY l.1021-1023, the inventory contract guide's minor units plus currency code
(`domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md` l.759, l.787-788), workexec `STORY_VALIDATION_CHECKLIST.md` l.36, and the positivity rule
that pricing returns its currency (`domains/positivity/.business-rules/DOMAIN_NOTES.md` l.98-100).

---

## 10. Defects That Exist Today (issues to file)

These are defects in today's code, several of them live for USD-only tenants. They are listed as issues to file, not as decisions of this ADR, and each can
be fixed before the owner rules on anything above.

| ID | Defect | Evidence (EXISTING) | Effect today |
| --- | --- | --- | --- |
| DF-1 | A supplier invoice's currency is dropped when the vendor bill is created | pos-accounting `SupplierInvoiceEventsListener.java`:190-219: the total is stored at face value (:198) and the currency is only logged (:218); `vendor_bill` has no currency column. The codec refuses a currency-less invoice for exactly this reason (`EdiwheelB33InvoiceCodec.java`:230-235) | An invoice from a foreign vendor profile (Michelin EU, for example) becomes a USD payable at face value |
| DF-2 | Payment and invoice currency are never compared, although a 409 is documented | `PaymentApplicationServiceImpl.java`:178 (Javadoc: CONFLICT on currency mismatch) and `PaymentApplicationController.java`:219, :234 (409 "Currency mismatch"); the code says "No currency check" (:662-668); AGENT_GUIDE D5 assumes rejection | The API promises a check that does not exist; a mismatched application is applied one to one |
| DF-3 | `ReversePaymentCommand.currency` is never forwarded | pos-order `ReversePaymentCommand.java`:11-12; `RestInvoicingPortAdapter.java`:62-72 builds the refund body from amount, reason, notes and external reference; the field is set to a literal USD (`OrderCancellationServiceImpl.java`:183; `ReturnOrderServiceImpl.java`:73) | A dead field; pos-invoice refunds in the payment intent's implied currency |
| DF-4 | Accounting ignores the currency on order events | pos-accounting `OrderEventsListener.java` and `RegisterOverShortPostingService.java` read no currency, while `OrderCompletedV1` and `RegisterSessionClosedV1` carry `currencyCode` | A non-USD order would post at par |
| DF-5 | Purchase-suggestion conversion uses the first currency for the whole PO | pos-inventory `PurchaseSuggestionServiceImpl.java`:174-178 (first non-null currency, else USD); `validateConvertible` checks acceptance, vendor and unit cost but not currency (:248-269) | Lines priced in another currency enter the PO at face value |
| DF-6 | A foreign-currency PO flows into inventory cost at face value | pos-order accepts any non-blank PO currency (`CreatePurchaseOrderRequest.java`:36-41, `varchar(255)`); receipt cost is converted to major units and stored without a currency (`ReceiptUnitCosts.java`:32-44; inventory ledger and cost tables have no currency) | Inventory valuation mixes currencies silently |
| DF-7 | A zero-amount default-mapping event posts an invented 0.01 | pos-accounting `PostingRuleEvaluatorImpl.java`:364-370, when `require-amount-field` is false | A fabricated amount in the ledger |
| DF-8 | Accounting's billing-rules replica invents USD where the owner holds none | `BillingRulesServiceImpl.java`:64-72 (`defaults()` sets USD); pos-customer `br_currency` is nullable (`V1__baseline_customer.sql`:41-42); the replica column is a nullable `varchar(16)` (`V1__baseline_accounting.sql`:463) | The replica disagrees with its owner (ADR-0044 R3 and R6) |
| DF-9 | Money is formatted en-US whatever locale the user picks | `FE src/app/app.config.ts`:82-138 (no `LOCALE_ID`); no currency pipe passes a locale; the user can pick es-US, es-MX, fr-CA or fr-FR | Breaks ADR-0030 §4 for every user not on en-US |
| DF-10 | Pages ignore a currency the API returns | The `PaymentApplication` and `CreditMemo` models carry `currency` (`FE src/app/features/accounting/models/accounting.models.ts`:251, :286), but the result panels print a literal USD (`FE src/app/features/accounting/pages/payment-apply/payment-apply-page.component.html`:34, :36; `FE src/app/features/accounting/pages/credit-memo/credit-memo-create/credit-memo-create-page.component.html`:46) | Harmless while every record is USD; wrong the day one is not |
| DF-11 | The order cart recomputes a line total in the template | `FE src/app/features/order/pages/order-cart/order-cart-page.component.html`:68 multiplies quantity by unit price | Contrary to DECISION-PRICING-001; floating-point money |
| DF-12 | Labor-rate currency is neither filtered nor compared | pos-price `LaborRateResolutionServiceImpl.java` (no currency filter); pos-workorder `EstimateServiceImpl.java`:1030 copies the rate's currency | A rate row in another currency would price an estimate at face value |
| DF-13 | Invoice document content carries no currency | pos-invoice `InvoiceArtifactService.java`:145-156 | Invoices state totals without a currency |

DF-1, DF-2 and DF-5 to DF-9 can affect a USD-only tenant now (a foreign vendor document, a payment recorded in another currency, a PO or supplier offer in
another currency, a zero-amount event, a user on another locale). DF-3, DF-4 and DF-10 to DF-13 are latent until a record is not USD.

---

## 11. Phased Plan (Implementation Notes)

Nothing here is authorized until the owner accepts the relevant rows. The §10 issues can be filed and fixed now. Every contract change (controller, DTO,
`@Schema`, permissions) is followed by the `API Artifacts Sync` workflow (backend `docs/DEVELOPMENT_GUIDE.md`, "Generating OpenAPI Specs", Method 3). New
tables follow ADR-0062 (`docs/architecture/deployment/TENANCY_SCHEMA.md`); schema changes are new Flyway versions per module, and baselines are not edited.

### Stage A — other currencies

**Entry criteria.** The A rows of §2 decided; the §10 issues filed.

| Step | Work | Modules |
| --- | --- | --- |
| A0 | Fix the live defects of §10 (DF-1, DF-2, DF-5 to DF-9); the latent ones (DF-3, DF-4, DF-10 to DF-13) are fixed within A2, A3, A6 and A7 | accounting, order, inventory, frontend |
| A1 | Functional currency on `tenant` (required on create, immutable), on `TenantCreatedV1` and `TenantProjectionV1`, in `ext_tenant` and `/v1/tenants/me`; the shared accessor; the money library | pos-tenant, pos-domain-events, pos-security-service, pos-tenancy-common, pos-shared-dtos |
| A2 | Every money-handling module replicates the functional currency; USD literals, column defaults and deployment-wide properties removed; money thresholds become functional-currency configuration | accounting, invoice, order, workorder, price, catalog, inventory, customer |
| A3 | Currency on money-bearing events (E-1); consumers compare and park (E-5, PC-9) | pos-domain-events, every producer and consumer |
| A4 | Scale and rounding by exponent (PC-6, MC-8): tax, accounting, estimates, quotes, gateway minor units, inventory; one-minor-unit tolerances; widened columns | tax, accounting, workorder, price, invoice, inventory |
| A5 | Journal-entry header stamp; `accounting.ledger.base-currency` replaced; bank reconciliation Stage A (§8); Labor & Overhead per MC-10 | accounting |
| A6 | F-1 to F-7; the tenant-create form gains the functional currency; SDKs regenerated | frontend, SDK |
| A7 | Documents format money with the document currency and a document locale | invoice, workorder, pos-documents |
| A8 | Cash rounding (PC-7), where a target currency needs it | order, accounting |

**Tests that prove it.**
- Every rounding, tolerance and minor-unit path parameterized over currencies with exponent 0 (JPY), 2 (USD) and 3 (KWD or BHD): line rounding then sum;
  tolerance of one minor unit; minor-to-major round trips; an amount with too many decimals refused with 422.
- Property-based tests (jqwik): random multi-line documents in each exponent produce journal entries that balance exactly, with every amount representable
  in its currency.
- An end-to-end run in the integration cell for a JPY tenant and a KWD tenant (quote, estimate, workorder, invoice, payment, journal entries, bank
  reconciliation) with no USD in any resulting row, event or document.
- A build guard: no `"USD"` literal in backend main sources outside seeds and tests (ArchUnit or a CI check), and the frontend rules of F-6.
- USD regression: the existing suites pass unchanged for the alpha tenant.
- Event contract tests: every money-bearing event carries a valid ISO code once E-4 applies; replayed events without the field read as the functional
  currency (E-3).

**Migration.**
- pos-tenant: `functional_currency` backfilled USD for every existing tenant (all are USD, `V2__seed_tenant.sql`:10, :20) before it becomes `NOT NULL`.
- Other modules: nullable currency columns on money rows backfilled with the owning tenant's functional currency (USD), then `NOT NULL`; column defaults
  dropped; journal entries stamped USD.
- A data check comes first (OP-10): any existing row in a currency other than USD is listed and resolved by hand, never backfilled.
- Kafka: events already on the topics keep their meaning under E-3.

**Exit.** No USD literal in main code; every producer stamps a currency (E-4 in force); the JPY and KWD runs pass.

**Go-live of a non-USD tenant, per jurisdiction (PC-15).**

| Check | Signs | Evidence |
| --- | --- | --- |
| Tax: regime supported by pos-tax's provider; the jurisdiction's rounding rules; tax-inclusive pricing if required (reopens tax plan l.27); registration ids on documents | Tax (first) | Provider confirmation and test transactions |
| Payments: a processor account in the currency; minor-unit rules tested | Payments | Test charges, refunds and payouts |
| Cash rounding (PC-7), if the smallest coin is larger than the minor unit | Order, Accounting | Register tests |
| Statutory: tamper-evident ledger (ADR-0047 D-2 trigger), e-invoicing or fiscal receipts (accounting plan ground rule 6, order plan l.36), chart of accounts | Accounting, Platform Owner | A written finding per jurisdiction |
| Documents and display in the tenant's locales | Frontend, Documents | Review |
| Seeds and configuration in the functional currency; no USD rows | Platform | Data check |

### Stage B1 — transactions in other currencies

**Entry criteria.** Stage A exited; MC-2, MC-3, MC-6, MC-7 (with OP-2), MC-9, MC-11, PC-16 and PC-17 decided; FX accounts chosen by the owner and seeded
only after ratification; the frontend's functional-currency fallback removed (F-3).

**Work.**
- pos-accounting: dual amounts on headers and lines; the rate store (tenant-scoped, audited, immutable once used) and its facts; F1 to F6, with F1 and F2
  enforced in the database; realized FX in payment application, AP payment, customer-credit application and refund, and settlement; the allow-list and its
  fact; aged AR/AP and statements in the transaction currency; tax in both amounts.
- pos-inventory: rate replica; receipt cost in the functional currency with the rate reference; cost facts carry the purchase's amount and currency.
- pos-price: per-currency lists; allow-list refusal; labor rates filtered by currency.
- pos-invoice, pos-order, pos-workorder and pos-customer: document currency from the allow-list; payment currency equal to invoice currency; purchase
  orders (pos-order) and vendor bills (from pos-supplier's invoice facts) in the supplier's currency.
- pos-tax: MC-11 conversion. Analytics: ADR-0057 amended. Frontend: currency lists; transaction and functional amounts on accounting screens.

**Tests.** Realized FX with the rate up and down; F1 to F6, including database rejection of an entry unbalanced in either currency; MC-8 residual bounds with
a JPY functional currency and USD or KWD transactions; reversal at the original rate (F5); a missing rate fails the posting, never 1:1; a used rate cannot
change; idempotent replays; allow-list refusal when a document is created.

**Migration.** Existing lines: transaction currency = functional currency, transaction amounts = functional amounts, rate 1 (MC-1).

**Go-live per added currency.** Rates maintained within MC-3's window; processor support; MC-11 confirmed for the jurisdiction.

### Stage B2 — revaluation

- **Entry criteria.** B1 live; MC-4 and MC-5 decided; the tenant's accounting framework known (OP-7).
- **Work.** Reversing period-end revaluation per period, account and currency at the closing rate; the close readiness check; reopened-period handling.
- **Tests.** An idempotent re-run; the reversal on the next day; a re-run after a reopened period gains foreign postings; non-monetary accounts untouched;
  close blocked until revaluation is posted.
- **Migration.** None.

### Stage B3 — foreign-currency bank accounts

- **Entry criteria.** B2 live; the §8 Stage B text accepted.
- **Work.** Bank-account profiles in allowed currencies, one GL account per currency held (F4); reconciliation on transaction amounts in the account's
  currency; functional-only lines excluded; a cross-currency `TRANSFER` refused (MC-7).
- **Tests.** Reconciliation of JPY and KWD accounts with one-minor-unit tolerances; functional-only lines never matched; a cross-currency transfer refused;
  revaluation of foreign bank accounts in close readiness.
- **Migration.** Existing profiles keep their currency (USD).

---

## 12. Open Points and Unverified Items

### 12.1 Where the architect differs from the Accounting memo

> **Resolved 2026-09-28** (§2.1): OP-1 as the architect proposed (lock at creation); OP-2 by option 2 (one cross-currency case allowed in B1).

**OP-1 — Lock at creation, not at activation (MC-1, timing only).** The memo locks the functional currency "when the tenant becomes ACTIVE". A tenant moves
from PENDING to ACTIVE automatically once pos-security-service answers `tenant.provisioned` (ADR-0062 §7), and modules seed their per-tenant defaults from
`tenant.created`. A value still changeable while PENDING would have to be re-seeded by every consumer on `tenant.updated`, racing provisioning. Architect's
view: make it a required field of the create request and immutable from then on; a wrong value is corrected by decommissioning the PENDING tenant and
creating another, which loses nothing because nothing has been posted. The memo's intent, fixed before the first posting, is kept either way.

**OP-2 — B1 without B3 leaves foreign money with no path to a bank (MC-7 and the stage order).** The memo gives each entry one transaction currency (§6.2),
refuses settlements and transfers across two currencies at first (MC-7), and ships foreign-currency bank accounts last (B3). Together these mean that in B1
and B2 a foreign-currency receipt can be recorded (in undeposited funds or a clearing account, at the spot rate) but never deposited into a
functional-currency bank account; a foreign-currency payable cannot be paid from one; and a card payment in a foreign currency cannot be settled into a
functional-currency payout. Plan decision D-14 had anticipated that last case: the adapter normalizes to the payout currency and "any FX difference rides an
`ADJUSTMENT` line posted via a future `FX_GAIN_LOSS` category" (plan l.398). Options for the owner and the Accounting Domain:

1. Ship B1 and B3 together for any tenant that will hold or pay foreign money.
2. Allow MC-7 for one case only: a foreign-currency item settled through a functional-currency bank account or payout, with the FX difference posted. This
   needs F2 to accept a conversion entry, either with a currency per line or with a two-step clearing pattern.
3. Accept that foreign money waits in clearing until B3.

Architect's view: decide MC-7 and B3's timing together, before B1 starts. If the first Stage B use is foreign suppliers or foreign cards, B1 is not usable on
its own without option 1 or 2. This questions the memo's order and MC-7's default, not its invariants; the Accounting Domain rules on F2's shape.

### 12.2 Open questions

> **Resolved 2026-09-28.** Every question below has a ruling in §2.1. OP-6 and OP-12 are carried as items of each jurisdiction's PC-15 readiness
> sign-off, Canada first; OP-10 is a precondition of Stage A's backfill. The table is kept as the record of what was asked.

| ID | Question | Why it matters | Who answers |
| --- | --- | --- | --- |
| OP-3 | What happens to an inventory receipt when no booking rate exists for its date? Proposed: record the receipt, park its valuation until the rate exists, then value at the receipt-date rate | The memo fails a posting without a rate; goods that physically arrived must still be counted | Accounting, Inventory |
| OP-4 | Rate agreement between inventory valuation and accounting's accrual: the cost fact carries the rate reference, so both use one functional amount (F3) | A refinement of memo §2.4, not a disagreement | Accounting, Inventory |
| OP-5 | What does `account.home_currency` mean? Plausibly the currency Durion bills the account in (ADR-0062 §7 lists it among the account's commercial data). If so, it stays on the account and only prefills the tenant form | The memo could not verify its intent (memo §5) | Platform Owner |
| OP-6 | Does pos-tax's provider support the target regimes? Is tax-inclusive pricing (a tax-plan non-goal, l.27) required for consumer prices there? Which rate converts tax and which currency is filed (MC-11)? | Tax gates go-live (PC-15) | Tax |
| OP-7 | Which accounting framework (US GAAP, IFRS or local) do prospective tenants report under? CAP-054's configuration already names `US_GAAP` or `IFRS` (`CAP-054-backend-implementation.md` l.604) | It decides MC-5's monetary and non-monetary classification | Platform Owner, Accounting |
| OP-8 | What does CAP-316's plant currency intend (MC-10)? | Decides whether `LocationFxRate` survives, and as what | Platform Owner |
| OP-9 | Which markets come first? None is named. The offered locales (en-US, es-US, es-MX, fr-CA, fr-FR) suggest Canada, Mexico and France, and each would reopen a recorded non-goal: Canada, cash rounding (PC-7) and multi-level sales tax; Mexico, mandatory electronic invoicing (statutory localization, accounting plan ground rule 6); France, certified point-of-sale software with ledger inalterability (ADR-0047 D-2's revisit trigger). These are outside facts, not verified in this repository, to confirm with counsel | Most go-live work is jurisdictional, not currency work | Platform Owner |
| OP-10 | Are there non-USD rows today (location profiles, billing-rule currencies, pos-price rows, supplier price entries)? Only code was read | Stage A's backfill assumes every row is USD | Platform (data check) |
| OP-11 | pos-price rounds unit prices HALF_EVEN; everything else rounds HALF_UP | One rounding rule per amount class (PC-6) | Pricing |
| OP-12 | Which locale renders server-side documents (invoice, estimate): the location's, the customer's or the tenant's? | PC-14 covers screens only | Documents, Platform Owner |
| OP-13 | Exponent sources can differ between the backend (JDK ISO data) and the frontend (Angular/CLDR digits) for a few currencies | Displayed decimals must match stored decimals; a test should compare both for every currency in any tenant's allow-list | Frontend, Platform |
| OP-14 | `DOMAIN_MODEL.md` and CROSS_DOMAIN_INTEGRATION_CONTRACTS l.55 still say "Organization"; the memo reads them as tenant | Amended wording says tenant (ADR-0062 §4), never organization | Accounting |
| OP-15 | The frontend has no en-CA or en-GB locale | Locale is independent of currency (F-1), but a target market may need more locales (ADR-0030) | Frontend, Platform Owner |

### 12.3 Not verified

- The `mcp__tokensave-backend__*` tools were not available; backend and frontend code was read directly with search and file reads.
- The load-bearing citations in §3, §5 and §10 were re-read on 2026-09-28 for this record. The rest, and every citation in §6 and §8, come from the three
  research inputs of the same date (backend currency inventory; frontend and documentation inventory; Accounting Domain requirements memo), which are not
  committed; their load-bearing content is reproduced here.
- SDK type line numbers (inside the packaged tarballs) were not re-verified.
- No database was inspected (OP-10).
- Tax-provider coverage, payment-processor rules and every jurisdictional statement (OP-7, OP-9) are outside facts.

---

## 13. Alternatives Considered

1. **Stay USD-only.** Keeps D18 and every non-goal; costs nothing now. Rejected as the recommendation because the owner asked to prepare for other
   currencies, and it forecloses every non-US market; the §10 defects remain either way.
2. **Stage B before Stage A (multi-currency on a USD ledger for everyone).** Rejected: FX is measured against a functional currency, and a non-US business
   whose books are in USD cannot meet local tax and statutory reporting.
3. **A USD ledger for every tenant, with translation for presentation.** Rejected for the same reason; translation is out of scope.
4. **The location (plant) as the currency boundary.** Rejected: SPEC D6 made the tenant the only entity boundary, and a location currency inside one ledger
   reintroduces multi-company.
5. **Stages A and B as one delivery.** Rejected: Stage A delivers value alone and fixes defects, while Stage B needs owner decisions (MC-2 to MC-7) that
   Stage A does not.
6. **`Money` objects in every contract** (PC-4 b), **conversion at quote time** (PC-10 b), **an FX utility module** (PC-17 b) and **a tenant-wide frontend
   default currency** (PC-14 b): rejected in their sections.

---

## 14. Consequences

### Positive

- A tenant in a currency other than USD becomes possible, one jurisdiction at a time, on evidence.
- Defects that mis-book money today are fixed for USD tenants too (§10).
- One money rule across APIs, events, storage and screens; currencies with 0 and 3 decimals are correct.
- Stage B builds on explicit currencies everywhere instead of retrofitting them a second time.
- The frontend formats money in the user's locale, as ADR-0030 already requires.

### Negative

- A broad retrofit: about fifteen backend modules, the frontend and the SDKs; every money contract changes, additively, and the `API Artifacts Sync`
  workflow runs repeatedly. Mitigated by the stage order and by the additive event rule.
- The test matrix gains currencies with 0 and 3 decimals.
- A tenant's functional currency is irreversible short of provisioning a new tenant.
- Jurisdictions still need tax, cash and statutory work outside this ADR before a non-USD tenant goes live.
- Stage B adds accounting complexity (dual amounts, rates, revaluation) and a standing duty for Finance to keep booking rates current; a missing rate stops
  postings by design.

### Neutral

- USD tenants see no functional change beyond the defect fixes.
- Translation stays out of scope; a multi-country business runs several tenants.
- ADR-0047's two decisions stand; only their single-currency driver changes.
- ADR-0053 and ADR-0054 are unchanged; ADR-0044's event rules are applied, not changed.

---

## 15. References

- **Related ADRs:** [ADR-0013](0013-platform-uuid-identifier-strategy.adr.md), [ADR-0017](0017-api-controller-http-response-codes.adr.md),
  [ADR-0021](0021-tax-api-consumption-and-internal-access-policy.adr.md), [ADR-0030](0030-frontend-internationalization-localization-policy.adr.md),
  [ADR-0040](0040-roles-jwt-permission-governance-policy.adr.md), [ADR-0044](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0047](0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.adr.md),
  [ADR-0048](0048-inventory-owned-valuation-configurable-costing-method.adr.md), [ADR-0049](0049-supplier-integration-module-boundary.adr.md),
  [ADR-0053](0053-supplier-pricat-ingestion-and-price-precedence.adr.md), [ADR-0054](0054-sell-price-system-of-record-split.adr.md),
  [ADR-0057](0057-analytics-money-measure-semantics-and-ownership.adr.md), [ADR-0062](0062-postgres-row-level-multitenancy.adr.md)
- **Specifications and plans:** [Manual Bank Reconciliation](../../domains/accounting/SPEC-manual-bank-reconciliation.md) (D6, D18),
  [Accounting parity plan](../../domains/accounting/plan-odoo-parity-pos-accounting.md) (ground rule 6, D-14),
  [Tax parity plan](../../domains/accounting/plan-odoo-parity-pos-tax.md), [Order parity plan](../../domains/order/plan-odoo-parity-pos-order.md),
  [Order missing-functionality spec](../../domains/order/spec-pos-order-missing-functionality.md)
- **Business rules:** `domains/pricing/.business-rules/DOMAIN_NOTES.md` (DECISION-PRICING-001, -004); `domains/accounting/.business-rules/` (AGENT_GUIDE,
  POSTING_RULES_SCHEMA, DOMAIN_MODEL, CROSS_DOMAIN_INTEGRATION_CONTRACTS, PERMISSION_TAXONOMY)
- **Architecture:** [Tenancy schema conventions](../architecture/deployment/TENANCY_SCHEMA.md), [Error envelope](../architecture/api/ERROR_ENVELOPE.md),
  [Seed input contract](../architecture/deployment/data-migration/SEED_INPUT_CONTRACT.md), `docs/architecture/TEST_COVERAGE_POLICY.md`
- **Knowledge catalog entries used:** `knowledge-catalog/adr/` 0021, 0030, 0044, 0047, 0048, 0053, 0054, 0057, 0062, 0066; `knowledge-catalog/backend/`
  pos-tenant, pos-accounting, pos-price, pos-tax, pos-tax-common, pos-domain-events; `knowledge-catalog/domains/` accounting, pricing
- **Research inputs (2026-09-28, not committed):** backend currency inventory; frontend and documentation currency inventory; Accounting Domain requirements
  memo "revisiting the single-currency posture (input to proposed ADR-0067)"
- **External:** [ISO 4217 currency codes](https://www.iso.org/iso-4217-currency-codes.html); [Unicode CLDR](https://cldr.unicode.org/)

---

## Sign-Off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Platform Owner | Louis Burroughs | 2026-09-28 | Accepted §2 subject to the §2.1 rulings (MC-1, MC-7 depart from the table) |
| Chief Architect | | | |
| Accounting Domain | | | MC rows, §6, §8, OP-1, OP-2 |
| Pricing Domain | | | PC-10, OP-11 |
| Tax | | | PC-11, PC-15, MC-11, OP-6 |
| Frontend Lead | | | PC-14 |

---

## Timeline

- **Proposed:** 2026-09-28
- **Under Review:** 2026-09-28
- **Accepted:** 2026-09-28

---

## Changelog

- **2026-09-28:** Initial draft, pending the owner's decision. Built from the backend currency inventory, the frontend and documentation inventory, and the
  Accounting Domain's requirements memo of the same date, with the load-bearing citations re-read against the source trees.
- **2026-09-28:** Accepted by the platform owner: the §2 recommendations subject to the rulings in the new §2.1, two of which depart from the table: functional currency locked at tenant
  creation (OP-1), one cross-currency settlement case allowed in B1 (OP-2, MC-7), the CAP-316 plant currency a report-only view (MC-10), `home_currency`
  the account's billing currency (OP-5), the accounting framework a per-tenant setting (OP-7), and Canada the first non-USD market, launched with USD or
  right after it (OP-9). The remaining open questions were answered or assigned to the PC-15 readiness sign-off.
