---
type: Specification
title: Accounting Workspace
description: The single-pane accounting home for novice accounting users — cash position, bills to pay with intake and approval limits, customer payment matching, a plain-language ledger view and approval/drawer-cash settings — with the domain rulings (including who owns supplier-invoice intake), backend changes and phased delivery it needs.
status: proposed
domain: accounting
tags: [accounting, ui, accounts-payable, accounts-receivable, cash-position, bank-deposit, register-float, petty-cash, approvals, bill-intake, adr-0039, adr-0041, adr-0044, adr-0047, adr-0049, adr-0062, adr-0067]
---

## SPEC — Accounting Workspace

> Status: PROPOSED · Created 2026-10-05 · Reviewed by the Accounting Domain Agent 2026-10-05 (18 corrections applied) · Design canvas: [Accounting Command
> Center](https://claude.ai/artifact/46hejFzE3MLi94JEXRkXCh) (five artboards) ·
> Branch: `durion-claude/serene-fermi-bhiy62`
>
> Purpose: specify a new accounting workspace in `durion-positivity-frontend` for people who are **not** trained accountants — a shop owner, office manager or
> accounting clerk — so that from one home pane they can see the shop's cash, clear what needs a person (bills that don't match, payments to match, bank lines,
> deposits), and drill into four focused views: **Bills to pay**, **Customer payments**, **Your books** (the missing GL / AR / AP view) and **Approval limits**.
> The specification turns the reviewed design into implementable rules, screens, contracts and phases; stories are cut from §11.
>
> Authority: financial meaning and posting semantics were ruled by the Accounting Domain Agent (`.claude/agents/domains/accounting-domain.md`) on 2026-10-05, in
> five consultations, three of them on questions the platform owner delegated to it; business choices were made by the platform owner on 2026-10-05. Ownership
> of supplier-invoice intake (OI-1) was ruled jointly by the Accounting, Positivity (Integrations) and Invoicing & Payments Domain Agents on 2026-10-05, in two
> rounds, at the platform owner's request (AW22–AW29); the platform owner then placed the vendor master in pos-supplier (AW23). On 2026-10-07 the Accounting
> Domain Agent ruled AW32–AW35 at the platform owner's request: moving a register's float, AP default terms, the safety cushion and opening bank balances;
> AW36 records the platform owner's and Order's no-move-during-an-open-session rule.
> Every decision is recorded in §10 and cited in the body as "(AWn)". **EXISTING** means verified in code on 2026-10-05; **PROPOSED** means this specification.
> Applicable ADRs: [ADR-0010](../../docs/adr/) frontend architecture, ADR-0017 (status codes, `ApiError`), ADR-0018 (actor from the security context), ADR-0020
> (document rendering, outbound only), ADR-0029–0035, 0037, 0038 (frontend patterns), ADR-0039 (WCAG 2.2 AA), ADR-0041 (SDK-backed feature services), ADR-0044
> (event-only domain walls), ADR-0047 (ledger inalterability), ADR-0048 (inventory valuation), ADR-0049 (supplier integration boundary), ADR-0050 (vendor
> profiles), ADR-0051 (supplier protocol adapters), ADR-0057 (reporting measures), ADR-0062 (multitenancy), ADR-0064 (business references, never UUIDs, as display
> text), ADR-0067 (currency / Canada readiness).
>
> Related specifications: [SPEC-manual-bank-reconciliation.md](SPEC-manual-bank-reconciliation.md) (the monthly bank check-up this workspace links to; its
> preparer ≠ approver rule and outstanding items are reused unchanged), [SPEC-inventory-adjustment-gl-posting.md](SPEC-inventory-adjustment-gl-posting.md) (form).
>
> Catalog entries used: `knowledge-catalog/domains/accounting.md`, `knowledge-catalog/domains/order.md`, `knowledge-catalog/backend/pos-accounting.md`,
> `knowledge-catalog/backend/pos-order.md`, `knowledge-catalog/backend/pos-invoice.md`, `knowledge-catalog/backend/pos-supplier.md`,
> `knowledge-catalog/backend/pos-tax.md`, `knowledge-catalog/adr/`.

---

## 1. Findings from the existing implementation (EXISTING, verified 2026-10-05)

Frontend paths are relative to `durion-positivity-frontend/`, backend paths to `durion-positivity-backend/`. Line numbers are from the current checkouts.

### 1.1 Frontend

| Concern | EXISTING behaviour | Where |
| --- | --- | --- |
| Access model | Pages are gated by permission codes, never role names. `/app` has `canActivate: [authGuard]`, `canActivateChild: [rolesChildGuard]`; `/app/accounting` carries `ACCOUNTING_PERMISSIONS = permissionsInDomains('accounting:', 'reporting:view:financial-statements')`, `/app/billing` `BILLING_PERMISSIONS = permissionsInDomains('invoice:', 'accounting:payment:', 'accounting:customer-credit:')`; each page adds its own `data.permissions` from `ACCOUNTING_PAGE` | `src/app/app.routes.ts` l.71-75, l.116-125; `src/app/core/security/route-permissions.ts` l.29, l.83-93, `ACCOUNTING_PAGE` l.319, `ACCOUNTING_SECTION` l.354, `BILLING_PAGE` l.607, `BILLING_SECTION` l.658 |
| Landing | `''` → `AccountingLandingPageComponent`, using the shared `LandingPageComponent` and `ACCOUNTING_LANDING_CONFIG` (hero, CTAs, permission-filtered link cards); no figures | `src/app/features/accounting/accounting.routes.ts` l.30; `src/app/shared/landing/landing-page/landing-page.component.ts` l.40; `src/app/features/accounting/pages/landing/accounting-landing.config.ts` l.16 |
| Accounting pages | `events`, `events/contract`, `events/submit`, `events/failed` (redirect), `events/:eventId`; `posting-rules`, `posting-rules/:ruleSetId`; `payments/apply` (`accounting:payment:apply`); `credit-memos`, `/new`, `/:memoId`; `vendor-payments`, `/new` (`accounting:ap:pay`), `/:paymentId`; `payables/vendor-invoices` and `/exceptions` (`accounting:analytics:view`), `/:billId` (`accounting:ap:view`); `reports/labor-overhead`; `periods`; `bank-accounts`, `bank-accounts/:glAccountId/import`, `…/statements/new`, `bank-imports/:importId`, `reconciliations/:reconciliationId`; `invoices/:invoiceId/payment-status` | `accounting.routes.ts` l.25-220 |
| Billing pages | landing, invoice detail, payment capture, void/refund, receipts. **No invoice list page** (`InvoiceSearchService.searchInvoices` exists) | `src/app/features/billing/pages/`; `billing.routes.ts` |
| Apply payment | `paymentId` text input and an `applicationsJson` textarea ("Applications JSON", default `'[]'`), parsed with `JSON.parse` into `{invoiceId, amount}[]` | `src/app/features/accounting/pages/payment-apply/payment-apply-page.component.html` l.14-18; `.ts` l.36-38, l.53 |
| Navigation | `NAV_REGISTRY` flat list; `{ key: 'SHELL.NAV.ACCOUNTING', icon: 'account_balance', route: '/app/accounting', permissions: ACCOUNTING_PERMISSIONS, order: 5, group: 'main' }`; no domain sub-menus | `src/app/features/shell/services/navigation-registry.service.ts` l.31 |
| Dashboard | Static greeting, quick actions, recent and favourites; **no KPI cards** | `src/app/features/shell/dashboard/` |
| Feature services | `accounting.service.ts` (events, exports, AP payments, credit memos, financial reporting, location cost reporting, invoice payments, payment applications, posting rules, vendor directory), `bank-reconciliation.service.ts` (bank accounts, imports, statements, reconciliation), `payables.service.ts` (`VendorBillAPIService`), `period-close.service.ts` (`AccountingPeriodsService`), `reconciliation-workspace.service.ts` (reconciliation, bank transactions). **None injects `GLAccountsService`, `JournalEntriesService` or `AccountingAnalyticsService`** | `src/app/features/accounting/services/` (`accounting.service.ts` l.75-84, `bank-reconciliation.service.ts` l.94-97, `payables.service.ts` l.42, `period-close.service.ts` l.44, `reconciliation-workspace.service.ts` l.58-59) |
| SDK | 19 packages at `0.88.0-alpha` (generated 2026-10-05). `@durion-sdk/accounting` already offers GL accounts (incl. balance), journal entries (list/get/post/reverse/traceability), financial reports (income statement, balance sheet, trial balance, general ledger, aged receivables, aged payables, tax liability, drill-downs), analytics, vendor bills, AP payments, payment applications, credit memos, customer credits, bank accounts/statements/imports/transactions, bank and settlement reconciliation, periods; `@durion-sdk/invoice` (search, invoices, payments, receipts); `@durion-sdk/supplier` (`fetchSupplierInvoices`) | `.sdk-tarballs/manifest.json` |

### 1.2 Backend

`ACC` = `pos-accounting/src/main/java/com/positivity/accounting/internal/`; `ORD` = `pos-order/src/main/java/com/positivity/order/internal/`; `EVT` =
`pos-domain-events/src/main/java/com/positivity/domainevents/`.

| Concern | EXISTING behaviour | Where |
| --- | --- | --- |
| Roles | Template roles `ADMIN`, `SYSTEM_ADMINISTRATOR`, `DISPATCHER`, `SHOP_MANAGER`, `SELF_SERVICE_CUSTOMER`, `CONTROLLER`, `SUPPORT`; `CONTROLLER` is the only accounting role; `ACCOUNTANT`, `AP_CLERK`, `GL_ANALYST` are retired; `GENERAL_MANAGER` and `ACCOUNTING_ASSOCIATE` exist only as alpha fixtures. Fixture grants: `accounting:ap:pay` → `ACCOUNTING_ASSOCIATE`, `ADMIN`, `CONTROLLER`; `accounting:payment:apply` → `ACCOUNT_MANAGER`, `ADMIN` only (**`CONTROLLER` cannot apply payments**; `GENERAL_MANAGER` has neither) | `pos-security-service/src/main/resources/db/migration/R__seed_tenant_template.sql` l.41-42; `R__seed_role_permissions.sql` l.56-61; `scripts/fixtures/seed/alpha/security/roles.csv` l.2, l.7, l.8; `role-permissions.csv` l.2-5 |
| Accounting permissions | Registered constants include `accounting:ap:view`, `accounting:ap:pay`, `accounting:analytics:view`, `accounting:payment:apply`, `accounting:payment:reverse`, `accounting:customer-credit:view\|apply\|refund`, `accounting:coa:*`, `accounting:je:*`, `accounting:mapping-key:*`, `accounting:gl-mapping:create\|resolve`, `accounting:reconciliation:view\|adjust\|approve`, `accounting:period:view\|close\|reopen\|hard_lock`, `reporting:view:financial-statements`. `accounting:ap:approve` and `accounting:ap:reject` exist in the security catalog but pos-accounting neither registers nor enforces them. `accounting:period:override` is catalogued and enforced through `AccountingPeriodGate.OVERRIDE_AUTHORITY`, outside `AccountingPermissions`. `accounting:time:export` belongs to pos-people. `accounting:payment:assign-customer` (cited by AD-004) exists nowhere | `ACC/security/AccountingPermissions.java` l.21-195; `pos-security-service/…/enums/PermissionCode.java` l.572, l.575, l.763; `AccountingPeriodGate.OVERRIDE_AUTHORITY`; `pos-people/…/PeoplePermissions.java` l.177; `.business-rules/AGENT_GUIDE.md` l.462 |
| AP endpoints | `/v1/accounting/vendor-bills`: create from goods receipt, `/match`, `/{billId}/resolve-exception`, `/match-candidates/{candidateId}/select` (all `accounting:ap:pay`); reads `accounting:ap:view`; list `accounting:analytics:view`. `/v1/accounting/ap`: `POST /payments` (`ap:pay`), `GET /payments/{id}`, `/payments/by-ref/{ref}`, `/bills` (`ap:view`) | `ACC/controller/VendorBillController.java` l.57-511; `ACC/controller/APPaymentController.java` l.64-237 |
| Vendor bills | Statuses `PENDING_RECEIPT_MATCH`, `MATCH_EXCEPTION`, `CURRENCY_HOLD`, `APPROVED`, `REJECTED`, `PAID`, `VOIDED`; `REJECTED` and `PAID` have no write path (a paid bill stays `APPROVED`). HIGH matches are set `APPROVED`; **every** matched bill — including one left in `MATCH_EXCEPTION` — then receives `approvedBy`, `approvedAt` and "Auto-approved: three-way match successful" (defect G12). `resolveMatchException` and `selectMatchCandidate` take `operatorId` from the request body | `ACC/enums/VendorBillStatus.java` l.26-68; `ACC/service/VendorBillServiceImpl.java` l.288-305, l.318-368, l.692-727; `ACC/controller/VendorBillController.java` l.286-287, l.588 |
| Match score | Inline in `VendorBillServiceImpl`: amount within 10% +40; line-item Jaccard similarity ×30; date ≤ 7 days +20 (≤ 30 days +10); PO present +5 (the comment and Javadoc say 10); minimum 50; HIGH ≥ 70 (Javadoc says "> 70"); several candidates ≥ 50 → AMBIGUOUS | `ACC/service/VendorBillServiceImpl.java` l.476-595 |
| Supplier invoices | Only intake: pos-supplier EDI fetch `POST /v1/supplier/invoices/{supplierRef}/fetches` (`supplier:invoice:fetch`) → `SupplierInvoiceReceivedV1` → vendor bill (`CURRENCY_HOLD` for foreign currency, `MATCH_EXCEPTION` for a null gross, else `PENDING_RECEIPT_MATCH`); **no due date**; only `totalGrossAmount` is stored although the event carries `totalNetAmount` and `totalTaxAmount`. pos-inventory's goods receipts do not create bills on their own: a goods-receipt bill exists only when a caller posts a goods-received payload to `POST /v1/accounting/vendor-bills` (the in-process `VendorBillEventHandler` has no publisher) | `pos-supplier/…/controller/SupplierInvoiceFetchController.java` l.40-52; `ACC/service/SupplierInvoiceEventsListener.java` l.201-262, l.360; `EVT/supplier/SupplierInvoiceReceivedV1.java` l.50-59 |
| Bill duplicates and vendor identity | The EDI listener looks for an existing bill by (`vendorId`, `billNumber`) only — no invoice date — and sets `vendorId` = the pos-supplier `vendorProfileId`. Goods-receipt bills set `vendorId` from the inventory vendor id and a **generated** bill number, not the vendor's. `vendor_bill` has no business-key unique constraint (only `vendor_bill_pkey` and `vendor_bill_tenant_key`). pos-accounting keeps its own `ap_vendor` directory. A purchase order's `vendor_id` is supplied by the client and validated against nothing; pos-inventory copies it into its purchase-order replica | `ACC/repository/VendorBillRepository.java` l.187; `ACC/service/SupplierInvoiceEventsListener.java` l.205-208; `ACC/service/VendorBillServiceImpl.java` l.120-122; `pos-accounting/src/main/resources/db/migration/V1__baseline_accounting.sql` l.239, l.1541-1544; `ACC/service/VendorDirectoryService.java`; `pos-order/src/main/resources/db/migration/V1__baseline_order.sql` l.298-302 |
| Receivables | `ReceivablePaymentStatus` `AVAILABLE` / `FULLY_APPLIED`; applications via `POST /v1/accounting/payments/{paymentId}/applications` (`accounting:payment:apply`), reversal `POST /v1/accounting/payment-applications/{id}/reverse`; overpayment → `CustomerCredit` (`/v1/accounting/customer-credits`); settled payments are **not** applied to their invoice automatically | `ACC/entity/ReceivablePayment.java` l.94-95, l.193-215; `ACC/controller/PaymentApplicationController.java` l.65-365; `ACC/controller/CustomerCreditController.java` l.60-191; `.business-rules/AGENT_GUIDE.md` Decision Index l.27-40 (AD-001…AD-004, AD-010) |
| Anonymous sales | Carts start without a customer; a customer is required only for workorder links and on-account tender; pos-invoice drafts carry `partyId` null "for anonymous counter sales"; `PaymentSettledV1.partyId` is `@Nullable` and `SettlementEventsListener` skips such payments ("Known gap", issue #1537 D4); aged receivables excludes null-party invoices while the GL keeps them in 1200. Order spec R11.2 (DRAFT): "Anonymous walk-in cash sales stay customer-optional." | `ORD/service/SalesOrderServiceImpl.java` l.318, l.340-344, l.604-612, l.797-798; `pos-invoice/…/service/OrderInvoiceServiceImpl.java` l.130-133; `EVT/payment/PaymentSettledV1.java` l.39; `ACC/service/SettlementEventsListener.java` l.54-62, l.268-281, l.590-591; `ACC/service/FinancialReportingServiceImpl.java` l.1421-1428; `domains/order/spec-pos-order-missing-functionality.md` l.316-317 |
| Chart of accounts | Seeded: 1000 Cash (`BANK_CASH`), 1090 Undeposited Funds, 1095 Register Cash Clearing, 1200 AR, 1300 Inventory, 2000 AP, 2200 Sales Tax Payable, 2300 Customer Credit Liability, 2350 Settlement Suspense, 2360 Bank Reconciliation Adjustments, 4000 Service Revenue, 4900 Settlement Adjustments, 4920 Interest Income, 4930 Cash Over, 5000 COGS, 5100 Inventory Shrinkage, 6000 Payment Processor Fees, 6020 NSF Fees, 6030 Bank Service Charges, 6115 Cash Short. **No 1080, 1250, 1260 and no 3xxx equity.** `V2__seed_accounting.sql` adds the retread-plant labour-overhead accounts 6010–6530 and 6900 (including 6110–6170, with other meanings). Every seed binds only the default tenant: a tenant created through pos-tenant gets no chart at all (no module handles `tenant.created` in pos-accounting yet). Receipts post Dr 1090 / Cr 1200; settlements Dr 1000 (net) / Dr 6000 / Cr 1090 / Cr 2350 | `pos-accounting/src/main/resources/db/migration/R__seed_reference_accounting.sql` l.14-86, l.227-320, l.466-523 |
| Drawers | `/v1/orders/sessions`: open, current, cash movements, begin/confirm close, X/Z reports. `CashMovement` has `PAID_IN` / `PAID_OUT`, a free-text `reason` (500) and `clerkId` taken from the request. Movements don't post; over/short posts through posting category `REGISTER_OVER_SHORT` (short Dr 6115 / Cr 1095; over Dr 1095 / Cr 4930, via GL mapping). Opening float defaults to the previous session's counted close (R6.1). Variance limit is the global `pos.order.session.authorized-difference-limit:5.00`. `RegisterSessionClosedV1` carries opening float, counted and theoretical cash, over/short, tender totals and only a `cashMovementTotal` | `ORD/controller/RegisterSessionController.java` l.54-318; `ORD/entity/CashMovement.java` l.48-61; `ORD/dto/CashMovementRequest.java` l.38; `ORD/service/RegisterSessionServiceImpl.java` l.67, l.80-82, l.197, l.294-295; `ACC/service/OrderEventsListener.java` l.114; `ACC/service/RegisterOverShortPostingService.java` l.20-54; seed l.548-554; `EVT/order/RegisterSessionClosedV1.java` l.36-53 |
| Reports | `/v1/accounting/reports/financial`: income statement, balance sheet, trial balance, drill-down to accounts and journal lines, general ledger, aged receivables, aged payables, tax liability (`reporting:view:financial-statements`); `/v1/accounting/gl-accounts/{id}/balance` (`accounting:coa:view`); `/v1/accounting/journal-entries` with traceability, post and reverse; `/v1/accounting/analytics` collections, payment-lag cohorts, vendor spend | `ACC/controller/FinancialReportingController.java` l.53-559; `GLAccountController.java` l.385; `JournalEntryController.java` l.52-426; `AccountingAnalyticsController.java` l.42-225 |
| Bank reconciliation | `/v1/accounting/bank-accounts`, `/v1/accounting/reconciliations` (submit / finalize / return / cancel / supersede / review / report / audit), `/v1/accounting/settlements`; preparer ≠ approver enforced (`RECONCILIATION_SELF_APPROVAL`) unless `BANK_REC_ALLOW_SELF_APPROVAL` | `ACC/bankrec/controller/BankAccountController.java` l.38; `BankReconciliationController.java` l.66-760; `ACC/bankrec/service/ReconciliationApprovalServiceImpl.java` l.123-134; `BankRecPolicy.java` l.46 |
| Periods | `/v1/accounting/periods`: close, reopen, hard lock, close readiness, bank-reconciliation policy; `PERIOD_CLOSED` (422, overridable) and `PERIOD_HARD_LOCKED` (422, terminal) | `ACC/controller/AccountingPeriodController.java` l.56-457 |
| Elevation precedent | pos-invoice `POST /v1/billing/auth/elevate` (manager with `invoice:finalize:override`, 5-minute token) for the 500.00 service-advisor cap; pos-inventory two-tier `ApprovalThresholdEvaluatorImpl` | `pos-invoice/…/controller/ElevationController.java` l.42-71; `InvoiceFinalizationServiceImpl.java` l.71; `pos-inventory/…/service/ApprovalThresholdEvaluatorImpl.java` l.30-35 |
| Tax | No GST/HST/QST or input-tax handling; `GET /v1/tax/rates` answers 501 `TAX_RATE_LOOKUP_UNSUPPORTED` without provider coverage; ADR-0067 makes Canadian tax a launch gate | `pos-tax/…/controller/TaxController.java` l.245-271; `pos-tax/…/exception/TaxExceptionHandler.java` l.50; ADR-0067 |
| Payment terms | Default "Due on Receipt"; `ExtInvoice.dueDate`; purchase orders carry `paymentTermsId` | `ACC/service/BillingRulesServiceImpl.java` l.64-70; `ACC/entity/ExtInvoice.java` l.89-90; `ORD/dto/purchaseorder/PurchaseOrderResponse.java` l.98 |

### 1.3 Gaps this specification closes

| # | Gap | Consequence today | Closed by |
| --- | --- | --- | --- |
| G1 | No accounting home: the `/app/accounting` landing is a card grid of links | A novice has no single place to see cash, what is owed, what is due, or what needs a decision | §5.1 Accounting home |
| G2 | No GL, chart-of-accounts, journal-entry, aged-AR or aged-AP page, although the SDK has every service | Nobody can read the books in the app | §5.4 Your books |
| G3 | Apply payment asks for a raw payment id and a JSON allocation array | Cash application is unusable for anyone who isn't a developer | §5.3 Customer payments |
| G4 | No list of payments waiting to be matched; no eligible-invoices query (documented in `AGENT_GUIDE.md`, never built) | Unapplied payments are invisible | §7.1 |
| G5 | A settled payment becomes an `AVAILABLE` receivable but is never applied to the invoice it was taken against | Nothing reaches 1090 without manual cash application, even for named customers | AW14 |
| G6 | Anonymous counter sales: the invoice posts Dr 1200, the settlement listener skips the payment, aged AR drops the invoice | AR ledger and the GL disagree silently; cash waiting to be deposited is understated | AW12 |
| G7 | No cash-position read model, no deposit concept for drawer cash, drawer float not on the books, drawer movements don't post | The shop's cash can't be stated or reconciled | AW9, AW10, AW15–AW17 |
| G8 | No bill-approval command, no approval limit; a strong match approves itself; the same permission (`accounting:ap:pay`) resolves exceptions and pays | No separation of duties on payables | AW4–AW8 |
| G9 | Supplier invoices arrive only through the EDI fetch; no file, photo, email or spreadsheet intake | Every non-EDI vendor bill is outside the system | AW21, §7.4 |
| G10 | EDI bills arrive without a due date | They can't be placed in a cash outlook or aged correctly | AW11 |
| G11 | Vendor bills store the gross only; the vendor's stated tax is dropped | Canadian input-tax credits can't be claimed | AW20 |
| G12 | A medium-confidence bill left in `MATCH_EXCEPTION` is stamped `approvedBy` / `approvedAt` / "Auto-approved" (`VendorBillServiceImpl` l.300-305) | The audit trail shows approvals that never happened | §4.3 (approval fields are written only by an approval) |
| G13 | `CONTROLLER` lacks `accounting:payment:apply`; `accounting:payment:assign-customer` (AD-004) exists nowhere; `accounting:period:override` is enforced outside `AccountingPermissions`; the catalogued `accounting:ap:approve` / `accounting:ap:reject` are unused | The accounting role can't use Customer payments; customer assignment isn't governed; bill approval isn't enforced | §7.3 |
| G14 | The duplicate check on a vendor bill ignores the invoice date and nothing in the database enforces it | A PDF and its EDI copy, or a bill keyed twice, can become two bills and be paid twice; a vendor reusing a number in a later year collides with the old bill | AW22, §7.4 (one duplicate rule, partial unique index) |
| G15 | Vendor identity is split: EDI bills use the pos-supplier connection profile id, goods-receipt bills the purchase order's unvalidated vendor id, and pos-accounting keeps a third directory (`ap_vendor`) | One vendor appears as several; cross-channel duplicates and aged payables by vendor are wrong | AW23 (one vendor master in pos-supplier, copied by every consumer) |
| G16 | Goods-receipt bills carry a generated bill number, not the vendor's | The vendor's invoice for the same delivery can't be recognised by number | §7.4 (duplicate by delivery reference) |

---

## 2. Scope

### 2.1 In scope

1. **Accounting home** — the single pane: cash, 30-day outlook, three money lanes (Bills to pay, Money owed to you, Bank check-up), a to-do list whose items
   open in an in-place detail panel, getting-started guidance and a help guide.
2. **Bills to pay** — intake (upload, email-in, supplier connections, spreadsheet import), review with read-back of what was extracted, duplicate and delivery
   checks, match score explanation, and send-for-approval / approve / reject.
3. **Customer payments** — match a received payment to the invoices it pays; leftover kept as credit or refunded; customer assignment for unidentified payments.
4. **Your books** — plain-language balance summary with drill-down, who owes what (aged AR and AP), every entry (general ledger).
5. **Approval limits** — bill approval limits, drawer-cash limits, petty-expense categories, change history; Canadian tax-recovery columns.
6. The domain rules and backend capabilities those screens depend on (§4, §7), including counter and drawer rules in `pos-order` and the CASH customer in
   `pos-customer`.

### 2.2 Out of scope

- Redesign of the existing bank reconciliation workspace, period close, credit memo, posting-rule and event-ingestion pages. The workspace links to them
  (Bank → reconciliation, Month-end → period close) and only adds summary data.
- The register (POS) screens themselves. §6 states what the register must do; their layout belongs to an Order-domain design.
- OCR/extraction vendor selection and true purchase-order (2-way/3-way) matching — named as open items (§12).
- Multi-currency beyond the Canadian readiness items of AW20.

### 2.3 Users and access

| Person | Role (template) | Typical work in the workspace |
| --- | --- | --- |
| Accounting clerk | `ACCOUNTING_CLERK` (PROPOSED, AW4) | Clears the to-do list, adds and checks bills, approves bills up to the clerk limit, matches payments, records deposits, prepares the bank check-up |
| Controller ("Accounting") | `CONTROLLER` (EXISTING) | Everything a clerk does; approves any bill; pays bills; approves the bank check-up; manages categories, float and limits |
| General manager | `GENERAL_MANAGER` (PROPOSED: promoted from fixture, AW4) | Approves any bill; pays bills (AW7); sets limits; approves drawer exceptions |
| Administrator | `ADMIN` (EXISTING) | Everything |

The route is gated on permissions, never on role names (§8.2). `SYSTEM_ADMINISTRATOR` holds no accounting permission by design and does not see the workspace.

---

## 3. Design principles (normative)

| # | Principle | Rule |
| --- | --- | --- |
| P1 | Plain words first | Every label uses shop language ("Bills to pay", "Money owed to you", "Waiting to be deposited", "Bank check-up"). The accounting term and account number appear only when the person turns on **Show accounting terms** (a per-person preference, remembered across sessions). Debit/credit never appear on a primary surface; the ledger shows "Went up / Went down" with "(debit) / (credit)" under the preference. |
| P2 | Exceptions first | The to-do list holds only what needs a person. Work the system completed with confidence is listed under "Done automatically" with review and undo. |
| P3 | Every suggestion explains itself | A match shows its reasons (chips such as "Same customer", "Adds up exactly") and, for bills, the score breakdown (amount 40 · products 30 · date 20 · purchase order 5; 70 or more is strong). |
| P4 | Consequence before action | Every committing button is preceded by one sentence saying what will happen and whether it can be undone ("Approved bills can't be edited; a mistake is fixed with a credit note"). Cash-out actions (pay a bill, refund) confirm before money moves. |
| P5 | Separation of duties is visible | When a rule blocks the current person, the control stays visible with `aria-disabled="true"` and the reason ("You can't approve a check-up you prepared"). Controls for permissions the person lacks entirely are hidden, not disabled. |
| P6 | Help on every page | §5.6. |
| P7 | The server computes money | Balances, totals, aging, outlook, allocation outcomes, tax and status come from the backend. The UI may sum amounts the person typed only as an input preview (Customer payments), and the server result replaces it. |
| P8 | Business references, never ids | Display `INV-2026-01702`, `JE-202610-0131`, `NTS-448210`, `WO-2026-004771`; never a UUID (ADR-0064). Null shows as an em dash, never as an id. |

---

## 4. Domain rules (PROPOSED unless marked EXISTING)

### 4.1 Cash position (AW9)

The home pane states one figure, **Your cash**, as the sum of three components the backend returns individually and in total:

| Component (plain label) | Accounts | Explanation shown in help |
| --- | --- | --- |
| In the bank | Every active GL account with subtype `BANK_CASH` (1000 today), one row per bank account | What your books say is in your bank accounts |
| Waiting to be deposited | 1090 Undeposited Funds + 1095 Register Cash Clearing, netted | Card payments not yet paid out, and cash and checks taken in but not yet banked, including today's open drawers |
| Kept in drawers for change | 1080 Register Float (PROPOSED, AW16), subtype `CASH_ON_HAND` | The fixed change float in each register; it never leaves the shop |

- **Bank's view alongside, never blended.** For each bank account: latest committed statement `closingBalance` and `endDate`, and the reconciled-through date.
  A difference between books and bank is explained only by reconciliation outstanding items (`DEPOSIT_IN_TRANSIT`, `OUTSTANDING_CHECK`) and unmatched bank lines.
- **2350 Settlement Suspense is never cash.** A non-zero balance appears under "Needs attention".
- **Unpaid walk-in sales.** A non-zero balance on the CASH customer (AW12) appears on the home pane under "Money owed to you".
- Functional currency only (ADR-0067); foreign-currency bank accounts are excluded until ADR-0067 Stage B.
- Every figure carries an "as of" timestamp; event-fed figures can lag and must say so.
- Gates: `reporting:view:financial-statements`; statement figures additionally need `accounting:reconciliation:view`.

### 4.2 30-day outlook (AW11)

A projection, labelled as such, never posted.

- **Start:** In the bank + Waiting to be deposited. The float is excluded.
- **Money in:** finalized or posted customer invoices with an open balance, on `ExtInvoice.dueDate`; with no due date, the customer's payment terms (EXISTING
  default "Due on Receipt" → due at finalization). Overdue invoices are not assumed collected; they are listed separately.
- **Money out (committed):** open amounts of `APPROVED` bills by due date.
- **Possible out:** bills in `AWAITING_APPROVAL`, `PENDING_RECEIPT_MATCH` and `MATCH_EXCEPTION`, shown as a separate figure, not in the line.
- **Excluded:** `CURRENCY_HOLD`, `VOIDED`, `REJECTED`; vendor credit notes are shown separately because applying them is not supported.
- **Bills with no due date** (EDI bills): estimated due date = bill date + terms, terms taken in this order (AW33): the matched purchase order's
  `paymentTermsId` when it parses, the vendor's `defaultPaymentTerms` from pos-accounting's vendor copy (once S24 carries it), otherwise the tenant setting
  `AP_DEFAULT_TERMS` (default `NET30`). Each estimate names its `termsSource` (`PURCHASE_ORDER` | `VENDOR` | `TENANT_DEFAULT`). Shown as "estimated",
  **never stored on the bill**. A person may enter the real due date during approval
  review (audited). Aged payables uses the same estimate (today it ages these bills from the bill date).
- **Default AP terms** `AP_DEFAULT_TERMS` (AW33): `defaultTerms` on the AP approval policy (§7.1; S13, louisburroughs/durion-positivity-backend#2510).
  Values `DUE_ON_RECEIPT` or `NET<n>` with n 1–120 (pos-supplier's vendor vocabulary, not pos-invoice's `NET_30`), else 400 `VALIDATION_ERROR`; absent from
  a PUT = unchanged; a change is audited in the policy history with the PUT's justification. It changes estimates only: nothing stored, nothing posted.
- **Safety cushion** `CASH_SAFETY_CUSHION` (AW34): the least balance the owner wants the projected line to reach. One tenant-wide amount in functional
  currency, ≥ 0, with no more decimals than the currency has; unset = no line and no warning; 0 = warn when the projection goes negative. A threshold for the
  outlook only: never posted, not a reserve or restricted cash, never subtracted from Your cash, and it refuses nothing. The chart draws it as a reference
  line and the home pane warns (`BELOW_SAFETY_CUSHION`) when the projected low point is strictly below it; possible out and overdue receivables are not in
  the comparison, because they are not in the line. Written by `PUT /v1/accounting/configuration/cash-safety-cushion` with
  `{amount | null, currencyCode, justification (≥ 10 characters), requestId}`: null = unset; `currencyCode` is ISO 4217 (ADR-0067 R-1, R-3, R-5) and must be
  the functional currency (R-6), else 422 `CURRENCY_NOT_SUPPORTED` (ADR-0067 PC-9); an unknown code, a negative amount or too many decimals → 400; the GET
  returns `{amount, currencyCode}`; a change is audited old → new with actor and roles. The PUT is gated by `accounting:period:hard_lock`
  (CONTROLLER, ADMIN), the `/v1/accounting/configuration` family's gate; the warning is the only signal, with no notification (§12 OI-6).
- Output: daily points (the chart samples them), lowest point and its date, expected in, expected out, possible out, the count and amount of estimated-due-date
  bills.

### 4.3 Bills: lifecycle, approval limits and separation of duties (AW4–AW8)

**Statuses** (`VendorBillStatus`, EXISTING values in plain text, PROPOSED in bold):

| Status | Plain label | Meaning |
| --- | --- | --- |
| `PENDING_RECEIPT_MATCH` | Waiting on delivery / waiting on the invoice | Waiting for its counterpart: the vendor's invoice for a delivery, or a delivery for an invoice |
| `MATCH_EXCEPTION` | Doesn't match delivery / Pick a match | Needs a person: score 50–69, several candidates ≥ 50, a line check failed (quantity 0.1 %, price 5 %, total 5 %), the EDI amount was unreadable, or a re-issued invoice changed amount or currency |
| `CURRENCY_HOLD` | On hold: foreign currency | Can never be approved (ADR-0067) |
| **`AWAITING_APPROVAL`** | Sent for approval | Checked; waiting for an approver of the required tier. Carries `requiredTier` (`CLERK` \| `OVER_LIMIT`), `submittedAt`, `submittedBy` |
| `APPROVED` | Approved · to pay | Owed; locked (no edits). A paid bill stays `APPROVED` with open amount = total − allocations (EXISTING) |
| `REJECTED` | Rejected | **Gains a write path**; terminal; reason ≥ 10 characters |
| `VOIDED` | Voided | EXISTING |

Transitions: `PENDING_RECEIPT_MATCH | MATCH_EXCEPTION → AWAITING_APPROVAL → APPROVED | REJECTED`. "Send for approval without a delivery match" is allowed with
a justification (service bills, shop supplies, EDI bills that will never have a receipt). Selecting a match candidate is matching only; the bill then moves to
`AWAITING_APPROVAL`.

**Limits:**

| Setting | Scope | Default | Rule |
| --- | --- | --- | --- |
| Clerk approval limit `AP_CLERK_APPROVAL_LIMIT` | Tenant | unset = 0: every bill needs an over-limit approver until a manager sets it (design shows $2,500.00) | Clerks approve bills whose total **including tax**, in functional currency, is ≤ the limit; credit notes compare by absolute value; checked at approval time against the current limit |
| Automatic approval limit `AP_AUTO_APPROVAL_LIMIT` | Tenant | 0 (off) | The system approves a HIGH-confidence match (score ≥ 70) only up to this amount, never above the clerk limit; above it the bill goes to `AWAITING_APPROVAL` (replaces today's unconditional auto-approval). Approval fields (`approvedBy`, `approvedAt`, justification) are written only by an approval — the system approver is recorded as such — never on a bill that stays unapproved (G12) |

**Permissions:** `accounting:ap:approve` (clerk tier, up to the limit); `accounting:ap:approve_over_limit` (no ceiling: CONTROLLER, GENERAL_MANAGER, ADMIN);
`accounting:ap_approval_policy:manage` (set the limits: CONTROLLER, GENERAL_MANAGER, ADMIN). Reject and `VOID` use the catalogued `accounting:ap:reject` (no
amount limit), granted with `accounting:ap:approve`. Clerks **do not** hold `accounting:ap:pay`; CONTROLLER and
GENERAL_MANAGER do (AW7).

**Separation of duties** (each exception is a tenant switch, default off, audited on every use — the bank-reconciliation D3 pattern):

1. The creator of a bill may not approve it (`POST /vendor-bills` makes the caller the creator; for an upload, email-in document or import, the person who
   confirms the read-back is the creator, §4.9; EDI bills are created by the system today, and stay so unless OI-11 routes them through a read-back
   draft).
2. The approver of a bill may not pay it: 403 `AP_PAYMENT_SELF_APPROVED_BILL` naming the bills; oldest-due-first allocation must not silently skip them.
3. Match-exception actions: `ACCEPT` **is** approval (same permission, same limit, justification); `CORRECT` is not approval; `VOID` needs
   `accounting:ap:reject` and a reason.
4. Actor identity comes from the security context; the `operatorId` request field of `resolveMatchException` is removed (ADR-0018).

**Audit:** every decision records actor, tier used, the limit at that moment, bill total, match score and evidence; justification is mandatory for reject,
void, `ACCEPT`, approve-without-match and any self-approval exception; decisions emit `ACCOUNTING_VENDOR_BILL_APPROVE` / `_REJECT`; a limit change records old
and new values, actor and justification.

### 4.4 Customer payments and the CASH customer (AW12–AW14)

1. **Every sale needs a registered customer** (owner ruling). Checkout refuses tender until a customer is set (pos-order). Invoice finalization and payment
   capture refuse a null `partyId` as a backstop (pos-invoice); `PaymentSettledV1.partyId` becomes non-null. Accounting keeps its never-invent skip, adds an
   alert, and treats any occurrence as a defect. This **reverses Order spec R11.2** ("Anonymous walk-in cash sales stay customer-optional"); Order must sign off.
2. **CASH house account** — one per tenant, created by `pos-customer` at tenant provisioning (until pos-customer consumes `tenant.events.v1`, seeded per tenant
   at startup through `TenantIterator`). Flagged `houseAccount = CASH_SALE` on the CRM party fact. Not editable, mergeable (as source or survivor) or
   deactivatable; no PII; no marketing consent; on-account tender refused.
   - Usable **only** when the sale is paid in full immediately by cash or card: not for on-account, partial payment, deposits, workorder links or returns to
     store credit (checkout enforces).
   - Its receivable must net to zero every day; any balance is shown on the home pane as **Unpaid walk-in sales**, resolved by collecting, a credit memo or
     reassigning the invoice to the real customer (reassignment of a finalized invoice is a Billing decision, §12 OI-5).
   - Excluded from aging buckets, collections, statements and customer analytics (ADR-0057 measures).
   - The cashier picks it explicitly ("Walk-in customer" button beside customer search, enabled only when the tender covers the total now). It is never applied
     automatically. Walk-in share is reported per cashier.
3. Applies **from go-live only**; no backfill of earlier sales (owner, AW13).
4. **Automatic application of counter payments.** A settled payment is applied to the invoice it was taken against (`PaymentSettledV1.invoiceId` is non-null),
   posting Dr 1090 / Cr 1200 at `settledAt`. An application record is created (AD-002 holds). Manual matching remains for payments without an invoice.
   Applied only when the payment's party equals the invoice's party and the currency is functional; idempotency key `PAYMENT_SETTLED:<paymentIntentId>`
   (cross-path with `INVOICE_PAYMENT`); amount up to the invoice's open balance, any excess becomes a `CustomerCredit` (AD-003) except on the CASH customer,
   where it is refused and raised as an alert; a closed period suspends it with `PERIOD_CLOSED` for reprocessing.
5. **Manual matching rules (EXISTING AD-002/003/010):** atomic across invoices; idempotent on a frontend-generated `applicationRequestId`; each amount ≥ 0.01;
   total ≤ the payment's unapplied amount; no amount above an invoice's open balance; overpayment becomes a `CustomerCredit` (Dr 1090 / Cr 2300); no currency
   conversion (`CURRENCY_NOT_SUPPORTED`); application date not editable; applications are immutable — a reversal is a new compensating record.
6. **Customer assignment** for a payment with no customer requires a justification of at least 10 characters (AD-004 documented, not built). Such payments
   cannot exist today — `ReceivablePayment.customerId` is `nullable = false` (`ACC/entity/ReceivablePayment.java` l.115) and no path records an unidentified
   receipt — so this depends on OI-8.

### 4.5 Bank deposits of drawer cash (AW10)

- **Undeposited-sessions read model** in `pos-accounting`, built from `RegisterSessionClosedV1` (already consumed): counted cash, theoretical cash, over/short,
  cash and check tender totals, plus check receipts taken outside the register. No synchronous call to pos-order (ADR-0044).
- **Record bank deposit** command: a `BANK_CASH` account, deposit date, the selected sessions' `BANK_DROP` movements (bag numbers) plus selected checks — a session is
  deposited whole: all its drops in one deposit, which also clears its 1095 net —
  `requestId` (idempotency), optional deposit-slip reference. Posting category `BANK_DEPOSIT` (mapping keys `UNDEPOSITED_FUNDS` → 1090,
  `CASH_CLEARING` → 1095; accounts never hard-coded): Dr bank (amount deposited) / Cr 1090 (selected items' expected cash and checks) / Dr or Cr 1095 (the
  selected sessions' net). Unbalanced → 422 with the difference; no plug line. Card tenders excluded (settlement entries clear them).
- Permissions: `accounting:deposit:create` (clerk, CONTROLLER, ADMIN); `accounting:deposit:reverse` (CONTROLLER, ADMIN). Deposits are never edited; a wrong one
  is reversed and recorded again.
- The deposit's single bank debit line matches the statement credit 1:1 with the existing matcher; until the bank shows it, it is a non-posting
  `DEPOSIT_IN_TRANSIT` outstanding item (bank-reconciliation spec, unchanged).
- The deposit date passes the period gate (closed period → `accounting:period:override` + justification; hard-locked → refused).
- Money sitting in 1090 longer than N days raises a warning, not a close blocker (period close already excludes 1090).

### 4.6 Drawer movements, float and limits (AW15–AW19)

**Movement reasons** — a fixed list; free-text reasons, "Other" and "customer refund" are removed (cash refunds go through the refund flow, AD-001). Postings
resolve through posting category `REGISTER_CASH_MOVEMENT` at session close.

| Reason | Direction | Posting | Control |
| --- | --- | --- | --- |
| `PETTY_EXPENSE` + category | OUT | Dr expense (`PETTY_EXPENSE_<CODE>`) / Cr 1095 | Note and receipt reference; over the limit → manager approval at the drawer |
| `VENDOR_COD` + vendor | OUT | Dr 2000 (unapplied vendor payment) / Cr 1095 | Vendor required; limit; the vendor's later bill is matched to it and still needs approval. Recorded in pos-accounting as an AP payment with new method `CASH`, no gateway call, unapplied until the vendor's bill is allocated to it; the vendor id comes on the close fact v2; unapplied COD older than N days appears under Needs attention |
| `BANK_DROP` | OUT | none at close; consumed by the deposit command | Deposit bag number required |
| `FLOAT_INCREASE` / `FLOAT_DECREASE` | IN / OUT | none at close; balanced by an accounting float change | Always needs a manager; must match a recorded float change |

The bank drop equals counted cash minus the float. Petty expenses, COD payouts and over/short leave 1095 holding the session's net, which the deposit clears.
The close fact gains per-movement detail (`RegisterSessionClosedV1` v2, or a per-movement fact). `clerkId` comes from the security context (ADR-0018).

**Drawer limits** (owner ruling: configurable "allowed / amount"; Order owns the settings, AW19):

| Type | Allowed (default) | Cashier limit (default) |
| --- | --- | --- |
| Petty expenses | On | 50.00 |
| Vendor cash on delivery | **Off** until a manager turns it on | set when switched on |
| Bank drop | Always | none (bag number required) |
| Float change | Always | always needs a manager |
| Drawer over/short tolerance | — | 5.00 (replaces the global app setting) |

- Scope: one setting set per tenant, in functional currency (per-location overrides later).
- A limit is checked against the **running total of that type in the drawer session**, so splitting a receipt can't avoid it.
- Above a limit, a person holding `order:session:approve_cash_movement` approves at the drawer with their own sign-in (the pos-invoice elevation pattern);
  approver ≠ cashier; both actors from the security context. Over the variance tolerance, `order:session:approve_variance` (EXISTING) signs the close.
- Switched off mid-session: new movements of that type are refused at once; recorded ones stand and post at close; never retroactive.
- Settings permission `order:session_policy:manage` (CONTROLLER, GENERAL_MANAGER, ADMIN); every change audited (old/new, actor, justification ≥ 10 characters,
  event `ORDER_SESSION_POLICY_UPDATE`). The accounting Approval limits page reads and writes them through the pos-order SDK (ADR-0041).
- CAD: 0.05 cash rounding is not variance (ADR-0067 PC-7).

**Float** (AW16, AW17):

- New account **1080 Register Float** (ASSET, new subtype `CASH_ON_HAND`, location dimension per register); not `BANK_CASH`, so out of bank reconciliation.
- A fixed amount per register, set and changed only by **Change float** (`accounting:float:manage`, CONTROLLER, ADMIN), posting Dr/Cr 1080 against a chosen bank
  account. The opening float of a session equals the register's configured amount (replaces Order R6.1, which carries the previous close forward); a difference
  at open or close is over/short, posted through the EXISTING `REGISTER_OVER_SHORT` category (short Dr 6040 Cash Short, renumbered from 6115 by AW30, /
  Cr 1095; over Dr 1095 / Cr 4930).
- **Establish go-live float** — once per register: Dr 1080 / Cr **3900 Opening Balance Equity** (new; EQUITY), dated on the go-live date in an open period,
  `accounting:float:manage`, justification; a second attempt → 409 `FLOAT_ALREADY_ESTABLISHED`; correction = reverse and re-run. The accountant later clears
  3900 into **3000 Owner's Equity** (new; remappable to the legal form) by manual journal entry; period close warns while 3900 ≠ 0.
- **Move a register's float to another location** (AW32; S38, louisburroughs/durion-positivity-backend#2571) — one command for a location entered in error
  (`ENTERED_IN_ERROR`) and a physical move (`MOVED`); the reason is recorded and changes nothing in the posting. It posts a reclass within 1080 for the
  register's current amount, Dr 1080 [register, new location] / Cr 1080 [register, old location], dated on the effective date (default today; never in the
  future; never before the register's latest standing float entry); no bank or 3900 line. Posted lines and their dimensions are never edited (ADR-0047):
  location reports for earlier periods stay as posted and the history row explains them. The standard period gate applies, override included, as for Change
  float. `accounting:float:manage` at **both** locations (ADR-0061). A register with no float row has nothing to move (404); a zero float moves without
  posting; a negative float is fixed by Change float first. After a move no float entry may be dated before it; a relocation entry is never reversed (the
  register is moved again instead); an earlier entry reversed after a move is re-homed to the current location by a follow-up reclass dated on the reversal
  date. A register never moves while a session is OPEN or CLOSING (AW36): confirm-close, relocate, then open at the new location.
  Request `{fromLocationId, toLocationId, reason (ENTERED_IN_ERROR | MOVED), effectiveDate?, justification, requestId, overrideJustification?}`. Codes: 404
  `FLOAT_REGISTER_NOT_FOUND`; 422 `FLOAT_REGISTER_LOCATION_MISMATCH` (`fromLocationId` is not the register's location), `FLOAT_RELOCATION_SAME_LOCATION`,
  `FLOAT_RELOCATION_DATE_INVALID`, `FLOAT_AMOUNT_NEGATIVE`, `FLOAT_REGISTER_SESSION_OPEN` (the register's latest session is open, AW36), `PERIOD_CLOSED`,
  `PERIOD_HARD_LOCKED`; a later float command dated before a move → 422
  `FLOAT_DATE_BEFORE_RELOCATION`; reversing a relocation entry → 409 `FLOAT_RELOCATION_NOT_REVERSIBLE`; reversing an earlier entry with a reversal date
  before the move → 422 `FLOAT_REVERSAL_BEFORE_RELOCATION`.
- **Opening bank balances at go-live** (AW35, OI-10; S39, louisburroughs/durion-positivity-backend#2572) — once per `BANK_CASH` account in functional
  currency, `accounting:je:create` and `accounting:je:post`, justification. Dated on the cutover date (`asOfDate`: the day whose end-of-day bank balance is
  brought in, usually the day before go-live; never after today) in an open period, no override. Request `{asOfDate, statementBalance, currencyCode,
  outstandingItems[{type (OUTSTANDING_CHECK | DEPOSIT_IN_TRANSIT), reference, itemDate, amount}], justification, requestId}`: every `itemDate` on or before
  `asOfDate` and every item `amount` > 0, else 400 `VALIDATION_ERROR` with `fieldErrors`, as for an `asOfDate` after today; `currencyCode` is ISO 4217
  (ADR-0067 R-1, R-3, R-5) and must be the account's functional currency, else 422 `CURRENCY_NOT_SUPPORTED` (ADR-0067 PC-9). Posts through the new posting
  category `OPENING_BALANCE` (`OPENING_BALANCE_EQUITY` → 3900): the bank statement balance (Dr bank, or Cr for an overdraft) and one bank line per
  outstanding item — check: Cr bank; deposit in transit: Dr bank — each with its reference and date, against one 3900 line for the net. Refused when the
  account already has a standing line dated on or before `asOfDate` or a committed statement starting by then; correction = reverse and re-run. The first
  statement reconciled starts on `asOfDate` + 1 with the statement balance as its opening balance and the first-statement acknowledgement (bank
  reconciliation E2, D17); its preparer registers the itemized lines as outstanding items (bank reconciliation §4.2 step 2), so the opening difference is
  zero and no gap bridge is needed. Cash and checks not deposited by the cutover are deposited first and entered as deposits in transit. 3900 is cleared
  to 3000 as for the go-live float. AR, AP, inventory and loan openings are not covered.
  Codes: 409 `BANK_OPENING_BALANCE_ALREADY_ESTABLISHED`; 422 `BANK_OPENING_BALANCE_NOT_FIRST`, `BANK_OPENING_BALANCE_ACCOUNT_NOT_ELIGIBLE` (not an active
  functional-currency `BANK_CASH` account), `BANK_OPENING_BALANCE_EMPTY` (nothing to post), `CURRENCY_NOT_SUPPORTED`, `PERIOD_CLOSED`, `PERIOD_HARD_LOCKED`.

**Petty-expense categories** (AW18, numbered by AW30; Accounting owns the categories and their accounts). Six categories post to accounts the chart
already has; three accounts are new:

| Category (permanent code) | Account (`EXPENSE` / `OPERATING_EXPENSE`) |
| --- | --- |
| `SHOP_SUPPLIES` — rags, gloves, valve caps, lubricant not billed to a job | 6340 Shop Supplies & Consumables (EXISTING, renamed) |
| `SMALL_TOOLS` — hand tools below the capital threshold | 6430 Small tools and equipment (EXISTING) |
| `OFFICE_SUPPLIES` | 6370 Office supplies (EXISTING) |
| `BUILDING_REPAIRS` — building upkeep | 6210 Building maintenance (EXISTING) |
| `EQUIPMENT_REPAIRS` — shop equipment upkeep | 6410 Equipment maintenance (EXISTING) |
| `POSTAGE_SHIPPING` — outbound | 6380 Postage & Shipping (new) |
| `CLEANING_JANITORIAL` | 6375 Cleaning & Janitorial Supplies (new) |
| `STAFF_MEALS` | 6295 Staff Meals & Refreshments (new; separate for the Canadian 50% rule) |
| `VEHICLE_FUEL` — shop or service vehicles | 6250 Vehicle Gas & Oil (EXISTING) |

- Repairs are two categories, because the labour-overhead report keeps building and equipment maintenance apart.
- **Expense range plan (AW30):**

  | Block | Meaning |
  | --- | --- |
  | 6000–6099 | Bank, card and cash handling: 6000, 6020, 6030, 6040 Cash Short |
  | 6100–6199 | People: wages 6100–6105, payroll taxes and benefits 6110–6170 |
  | 6200–6299 | Premises, vehicles, travel; 6295 Staff Meals & Refreshments |
  | 6300–6399 | Insurance, property tax, supplies; 6375 Cleaning & Janitorial Supplies, 6380 Postage & Shipping |
  | 6400–6499 | Utilities and equipment |
  | 6500–6599 | Admin fees and production adjustments |
  | 6600–6899 | Reserved for operating groups tenants add |
  | 6900–6999 | No revenue in the expense range |

- **Renumbered existing accounts (AW30; no real data):** 6010 → 6100 Hourly wages and bonuses, 6015 → 6102 Management salaries, 6025 → 6105 Misc labor,
  6115 → 6040 Cash Short, 6900 Income from rubber dust sales → 4940 Rubber Dust Sales (REVENUE). A new versioned migration changes `account_code` by
  `gl_account_id` and the copied `statement_line_mappings.account_name`; it edits no applied migration and deletes nothing. A seed test asserts each account
  code is owned by exactly one seed file, so a repeatable seed's upsert can never rename another seed's account.
- **Retread add-on:** the retread-plant accounts (6350, 6450, 6470, 6510, 6520, 6530, 4940) and their `LABOR_OVERHEAD` statement lines are applied only to a
  tenant that runs a retread plant, as a tenant-setup choice (CONTROLLER, ADMIN), never by default; every other account above is in every tenant's chart.
- Stored as mapping keys `PETTY_EXPENSE_<CODE>` under `REGISTER_CASH_MOVEMENT`; add / relabel / deactivate with `accounting:mapping-key:create|edit|deactivate`;
  remap with `accounting:gl-mapping:create` (effective-dated, non-overlapping); CONTROLLER and ADMIN only. Codes are permanent; categories are deactivated,
  never deleted. No "Other". `accounting.petty-expense-category.changed` lets pos-order keep the cashier's picker (ADR-0044).
- **Never petty cash:** tires and parts (stock, workorder or sublet — purchasing/receiving/AP, ADR-0048); wages, tips, payroll or advances; owner draws and
  personal spending; customer refunds or goodwill; capital equipment; recurring bills and vendor bills other than COD.
- **Tax (US):** the cashier enters the receipt's gross total; sales tax paid is part of the expense; 2200 is never debited. The UI never computes tax.

### 4.7 Canadian input-tax recovery (AW20)

Applies only to CAD tenants whose GST/HST registration is recorded; the backend exposes `inputTaxRecoveryEnabled` and the UI follows it, never the currency code.

- Accounts (CAD tenants only, at provisioning): **1250 GST/HST Recoverable (ITC)** and **1260 QST Recoverable (ITR)**, ASSET, new subtype `TAX_RECOVERABLE`.
  PST is not recoverable and stays in the expense. QST recovery additionally requires a recorded QST registration.
- Drawer entry: receipt total, category, supplier name, "GST/HST shown on receipt" (Québec: also "QST shown on receipt") — shown only when recovery is on and
  the category is recoverable. The cashier copies the printed figure; blank or "not sure" = zero recovery (under-claiming is safe). Backend validation: 0 ≤ tax
  < total; plausibility against the location's rate from pos-tax (422 `TAX_AMOUNT_IMPLAUSIBLE`); if the rate can't be looked up, record with no recovery and flag.
- Posting at close: recoverable = tax entered × category recoverable %; Dr expense (total − recoverable) / Dr 1250 (and 1260) / Cr 1095.
- Evidence: supplier name and receipt reference always (photo recommended); from **$100** the supplier's GST/HST registration number is required, else the tax
  goes to expense (format check only). Thresholds are jurisdiction settings.
- Category settings `taxRecoverable` and `recoverablePercent`: 100% for all defaults except **Staff meals 50%**; new categories default to not recoverable.
- Vendor bills (CAD): split as the vendor stated — Dr inventory/expense (net + PST) / Dr 1250 / Dr 1260 / Cr AP (gross); never recalculated; the approver
  confirms; a vendor credit note reverses the recovery; recoverable tax stays out of inventory cost (ADR-0048); the EDI single tax total must be split by tax
  type (pos-supplier codec). US tenants keep booking the gross (correct for US).
- **Before the first CAD tenant:** pos-tax Canadian coverage and the PC-15 readiness sign-off; split output tax payables (proposed 2210 GST/HST, 2220 QST,
  2230 PST) using `LineItemTax.jurisdictions[]`; return filing frequency and ITC claim time limit; CAD 0.05 rounding; the vendor-bill tax gap; fr-CA.
  **A Canadian accountant must confirm** the 50% meals rule, the $100/$500 evidence thresholds and PST non-recoverability (rules taken from Canadian tax law,
  not from repository documentation).

### 4.8 Supplier-invoice intake formats (AW21)

| Priority | Formats and channels |
| --- | --- |
| v1 | PDF; photos JPG / PNG / HEIC; scanned TIFF — up to 25 MB per file, one invoice per file with an offer to split a multi-invoice PDF; per-tenant email-in address (about 20 attachments per email); CSV / XLSX import with column mapping and a downloadable template; monthly vendor statement upload (PDF/CSV) for statement reconciliation including credits (cores depend on OI-9) |
| v2 | ANSI X12 810 through an EDI provider for large tire distributors, collected by pull (AW28); distributor APIs through partners. 855 and 856 do not travel in `SupplierInvoiceReceivedV1`: 856 needs its own fact consumed by pos-inventory, 855 maps to `supplier.order.confirmed` only when a transmission intent exists — both need an ADR-0049 amendment at v2 |
| Later | Purchase-order matching (OI-7); inbound CFDI 4.0 XML for es-MX tenants, an intake format like any other (AW29; Mexico is not scheduled, ADR-0067 OP-9); UBL 2.1 / Peppol BIS 3.0 (DBNAlliance), embedded Factur-X/ZUGFeRD XML; DOC/DOCX/TXT are not bill sources |

Minimal CSV columns — required: `supplier_name`, `invoice_number`, `invoice_date`, `due_date` (or `terms`), `line_description`, `line_amount`; recommended:
`document_type` (invoice / credit memo), `po_number` or `supplier_order_number`, `part_number`, `quantity`, `unit_cost`, `core_charge`, `fees` (FET, tire and
environmental; treatment open, OI-9), `tax_amount`, `freight`, `invoice_total` (control total), `ro_number`, `currency` (default functional). Header fields repeat on each
line row.

Whatever the channel, nothing is owed until a person checks the read-back and the bill is approved (§4.3). Read-back fields carry a confidence state; a field the
reader is unsure of is marked "Check this" and blocks confirmation until a person accepts or corrects it.

### 4.9 Bill intake: ownership, drafts, vendors and statements (AW22–AW29)

**Who owns what (AW22).** A supplier invoice reaches the shop in two ways, and each has one owner:

| Concern | Owner | Rule |
| --- | --- | --- |
| Upload, photo, CSV / XLSX import, vendor-statement upload, email-in documents | `pos-accounting` | Entered by authenticated command; the person who confirms the read-back is the bill's creator (AW6.1). Never routed through `SupplierInvoiceReceivedV1` |
| The unconfirmed draft | `pos-accounting` | Its own aggregate `BillIntakeItem`, never a `VendorBillStatus`; never counted in aged payables, the cash outlook or any amount owed |
| Read-back, confirm, duplicate check across every channel, bill creation, matching, approval | `pos-accounting` | One internal entry point, `BillIntakePort`, is the only path that creates a vendor bill (ArchUnit-guarded) |
| The vendor master: every party the shop buys from or pays, with or without a connection | `pos-supplier` | §4.9 "Vendors (AW23)" |
| Credentialed machine-to-machine vendor channels: EDI, distributor APIs, a CFDI pulled from a supplier API or portal | `pos-supplier` (ADR-0049) | Publishes `SupplierInvoiceReceivedV1` for these only; never stores or republishes a document a person uploaded, imported or emailed (ADR-0044 R6) |
| Reading untrusted files (OCR, PDF, images, spreadsheets, inbound CFDI XML) | New **extraction worker** (utility) | Stateless and isolated; no business logic; holds the extraction-provider credentials; returns fields with confidence and proposed splits; invoked under ADR-0044 R2 as an asynchronous job while the draft sits in `EXTRACTING` |
| Email transport | New **inbound-mail edge** (non-domain) | MX, SPF/DKIM/DMARC, rate limits, `Message-ID` idempotency and address provisioning; the tenant comes only from the receiving address; hands file references to pos-accounting and never creates a bill; never a vendor-profile binding; never replies to senders (no pos-platform-sender) |
| Outbound CFDI (shop → customer) | `pos-invoice` | PAC stamping, cancellation and the folio fiscal are part of invoice issuance; pos-tax computes tax; pos-documents renders the PDF |
| — | `pos-invoice`, `pos-documents` | Own no part of bill intake; pos-documents hosts no parsing or storage of inbound files (ADR-0020 covers outbound rendering) |

Supplier connectivity therefore means credentialed machine-to-machine exchanges with a vendor or its EDI / API provider; supplier documents that arrive any
other way are accounts-payable intake. A new ADR records this boundary (§13).

**The draft (`BillIntakeItem`).** `QUARANTINED` → `FILE_REJECTED` | `EXTRACTING`; `EXTRACTING` → `READY_FOR_REVIEW`; `READY_FOR_REVIEW` → `CONFIRMED` (the
vendor bill exists) | `DISCARDED` (reason ≥ 10 characters). `FILE_REJECTED`, `CONFIRMED` and `DISCARDED` are terminal. `FILE_REJECTED` is reached only from
`QUARANTINED`, when validation or scanning fails, and carries a plain reason; malware stays quarantined. A multi-invoice file yields one draft
per proposed split. Nothing in this lifecycle posts, is owed or appears in aging.

**When extraction fails (AW26).** If the provider is down or can't read the file, the draft still moves to `READY_FOR_REVIEW` with every field empty and marked
"Check this", beside the file preview. A person types the bill in; the same confirm, duplicate and approval rules apply. Retry is offered; the bill records
`extractionOutcome = MANUAL`. Nothing is ever created automatically.

**One duplicate rule for every channel (G14, G16).** A bill is a duplicate when (tenant, vendor, normalised bill number, bill date) matches a bill that is not
`VOIDED` or `REJECTED`, so a legitimate re-issue after a void still goes through. A partial unique index enforces it. A goods-receipt bill carries a generated
number, so an incoming invoice is also checked against it by delivery reference. A duplicate is refused with a link to the original.

**Vendors (AW23).** The platform owner placed the vendor master in pos-supplier; pos-accounting, pos-order and pos-inventory keep copies.

- **The master.** pos-supplier owns one `Vendor` for each party the shop buys from or pays, whether or not it has a connection (a landlord, a utility):
  `vendorId` (UUIDv7), `vendorNumber` (the business reference people see, ADR-0064), `legalName`, `displayName`, `taxRegistrations[]`, `remitTo`,
  `defaultPaymentTerms`, `defaultCurrency`, `status` (`ACTIVE` / `INACTIVE`; never deleted) and `remitToVersion`. Each connection profile belongs to exactly one
  vendor; a vendor has zero or more profiles. YAML-managed profiles name their vendor by `vendorNumber`, and YAML never creates vendors. Permissions:
  `supplier:vendor:read`, `supplier:vendor:write`, `supplier:vendor_remit:approve`.
- **Remit-to changes need a second person.** A remit-to change is published only after someone other than the requester approves it
  (`supplier:vendor_remit:approve`), which raises `remitToVersion` (payment-fraud control).
- **The fact.** `supplier.vendor.updated` (v1, on `supplier.events.v1`, keyed by `vendorId`) carries every vendor field. Deactivation is `status = INACTIVE`.
  pos-supplier publishes per-tenant manifests on `supplier.manifest.v1` and re-sends on request (ADR-0044 §4).
- **Accounting's copy.** pos-accounting keeps `ext_supplier_vendor`, written only by that fact (ADR-0044 R3). It holds **every** vendor with its status, not
  only active ones, because open bills, history and aged payables still name vendors that have since been deactivated. Fields: `vendorId`, display name,
  vendor number, status, `statusChangedAt`, remit-to, payment terms, tax registration numbers, currency, `remitToVersion` with when and by whom it changed.
  pos-accounting's `ap_vendor` directory and its first-sight sync are retired.
- **The vendor key.** `VendorBill.vendorId` and the AP-payment vendor id are the pos-supplier vendor id. `SupplierInvoiceReceivedV1` gains a nullable `vendorId`
  that pos-supplier always sets; a message without one predates the change and is parked for a person. pos-order validates a new purchase order's vendor
  against its own `ext_supplier_vendor` copy, so goods-receipt bills carry the same id (G15). Alpha bills are reseeded, not migrated (pre-production).
- **Inactive vendors.** Their open bills stay in aging, history and reports. A new bill or payment for an inactive vendor is refused (422 `VENDOR_INACTIVE`);
  an EDI bill from one is recorded as `MATCH_EXCEPTION`, never dropped.
- **A vendor not set up yet.** When the read-back names a vendor that isn't in the copy, the draft stays `READY_FOR_REVIEW` marked "Vendor not set up yet" and
  confirm is refused (422 `VENDOR_NOT_FOUND`). "Add vendor" opens pos-supplier's vendor form from the frontend; pos-accounting never calls pos-supplier (ADR-0044
  R1) and never creates a placeholder. Waiting drafts re-resolve when the vendor fact arrives. Whoever created the vendor may not approve its first bill.
- **What stays accounting's**, keyed by `vendorId` (`ap_vendor_settings`): default expense mapping key, AP hold with a reason, the 1099 / T4A reportable flag
  and box, and derived balances. Vendor identity, remit-to, terms and tax registrations are pos-supplier's.
- **Paying checks the remit-to again.** At approval a bill stores `approvedRemitToVersion`. Paying it is refused (409 `VENDOR_PAYMENT_DETAILS_CHANGED`) while
  the vendor's current version differs, until a holder of `accounting:ap:approve` other than the payer confirms the change, with a justification saying how it
  was verified. The home pane shows "Vendor payment details changed".
- **Bank details** for electronic vendor payments do not exist yet; where they (or a provider's tokens) will live is open (OI-14). They never travel on Kafka.
- Vendor ids and customer `partyId`s are separate namespaces: a vendor is never a pos-customer or pos-invoice party, and no screen or API accepts one in place
  of the other.

**Vendor statements and credits (AW27).** The AP clerk reconciles a vendor's statement against open bills (`accounting:vendor_statement:reconcile`: clerk,
CONTROLLER, ADMIN). A statement reconciliation **posts nothing**, so it needs no second person. Each difference is resolved by taking in the missing bill through
intake (normal approval), asking the vendor for a credit note, or recording a vendor error. A statement credit never adjusts an approved bill (approved bills are
locked): the credit arrives as its own vendor credit note — a negative bill — through intake and approval, and is then allocated against open bills (new
capability, OI-13). Core charges follow OI-9.

**Partial payments (EXISTING).** An `APPROVED` bill may be paid in parts; open amount = total − allocations, never exceeded. The bill stays `APPROVED`; "Partly
paid" is a derived label, not a status. Approver ≠ payer (AW6.2) applies to every allocation.

---

## 5. Screens (PROPOSED)

Routes live under `/app/accounting` (lazy-loaded feature `features/accounting`). Every page uses the shell, the two-signal state machine (`state` + `errorKey`;
`state.set('error')` before `errorKey.set(...)`), effect-based loading with `onCleanup()`, and the four page states (idle, loading with skeletons, ready, error
with retry). All strings are i18n keys in the six locales; French and Spanish run 20–35% longer, so no label may rely on fixed widths.

### 5.0 Shared chrome

- **Accounting sub-navigation** under the page header: Home · Bills to pay · Customer payments · Bank · Your books · Month-end. Bank links to the EXISTING bank
  accounts / reconciliation pages; Month-end to the EXISTING period close page. Items the person can't use are hidden. The current page has
  `aria-current="page"`.
- **Show accounting terms** switch at the right of the sub-navigation (P1), a real checkbox with a label, persisted per person.
- Page header: overline "Accounting", h1, one-sentence standfirst, at most one primary action.
- Period chip in the home header: "October 2026 is open" (EXISTING period service).

### 5.1 Accounting home — `/app/accounting` (replaces the landing page)

Layout, top to bottom (all regions stack at phone width):

1. **Start here** banner (first visits; hidden per person once dismissed): three habits — every morning check your cash; every day clear your to-do list; once a
   month do the bank check-up — and "Open the help guide".
2. **Help guide** panel (opened from the header Help button or the banner; `aria-expanded`/`aria-controls`): *Words you'll see* glossary (bill, invoice,
   matching, waiting to be deposited, credit, bank check-up, reverse, month closed — each with the accountant's term in grey) and *How do I…* answers (pay a
   vendor; put drawer cash in the bank; match a customer's payment; pay for something small from the drawer; fix a mistake; send numbers to my accountant).
3. **Your cash** card (§4.1): hero figure (one per view, ≥ 48px, proportional numerals), a three-segment bar with a legend (colour never alone), the three
   component rows with sub-lines, the bank-versus-books box ("Your bank's statement says $X. N bank lines explain the difference" → shows bank items in the
   to-do list), and **Record the $X bank deposit** (opens §5.1.1). "What's this?" explainer.
4. **Next 30 days** card (§4.2): expected in, expected out, lowest point; a line chart with an area wash, today / lowest / end labels, the safety-cushion line,
   a hover crosshair whose readout is an `aria-live="polite"` line, and **Show as a table** (the accessible equivalent). Footnote on bills awaiting approval and
   estimated due dates with a link to add real due dates. "How to read this" explainer.
5. **Three money lanes** (grid, one column on phones):
   - *Bills to pay* — total owed on approved bills; segmented bar Overdue · Due in 7 days · Due later · Due date estimated, with a legend and amounts; note of
     bills still being checked or awaiting approval; actions Open bills to pay / Show the new bills.
   - *Money owed to you* — total on open invoices; bar Overdue · Not due yet; overdue-by-age badges (1–30, 31–60, 61–90, over 90); payments waiting to be matched;
     unpaid walk-in sales status; actions Match customer payments / Who owes what.
   - *Bank check-up* — per bank account: statement imported date and balance; reconciled-through date; the current month's status (including "waiting for a
     second person to approve"); bank lines not in the books; actions Open bank check-up / Show bank items. "Why two people?" explainer.
6. **Your to-do list** — filter buttons (`aria-pressed`) All · Bills · Customer payments · Bank, each with a count; a list of item buttons (`aria-pressed`) on the
   left and the **detail panel** on the right (stacks below on phones). Each item: icon, plain title, who · reference · date, one-line reason, amount, status badge.
   Footer: "Done automatically today: N matches — Review or undo them".

**Detail panel templates:**

| Item type | Panel content | Primary action |
| --- | --- | --- |
| Bill doesn't match delivery (`MATCH_EXCEPTION`) | Vendor, invoice, PO, amount, badge; warning sentence; Received / Billed / Difference table; score breakdown with meters and "What's a match score?"; radio choices Accept as billed · Correct the bill · Void the bill; required "Why?"; the approval-limit note (clerk: "over your $X limit, it goes to a controller or general manager"; approver: "you can approve any amount; someone else pays it") | Resolve bill / Resolve and send for approval |
| Pick a match (ambiguous) | Candidate deliveries compared | Compare deliveries |
| Waiting on delivery | What's missing and who acts (receive PO-…) | Open receiving |
| Bill sent for your approval (approvers) | Checks passed, who sent it, the limit | Approve bill / Reject bill |
| Bills due soon (payers only) | Amount due, bank balance, the self-approved-bill rule | Review and pay (EXISTING vendor payment flow) |
| Payment ready to match | Reason chips; invoices to apply; Payment / Applying / Left over; "Why match a payment?" | Apply payment |
| Customer paid extra | Apply and keep as credit · Apply and refund | per choice |
| Payment with no customer (depends on OI-8) | What to look for; justification | Choose customer |
| Cash and checks to deposit | Drawers, cash after float, checks | Record bank deposit |
| Card deposit to match | Batches + processor fees = bank line | Match deposit · Not this, find another · Exclude line |
| Bank fee not in books | What it is and where it will be recorded | Record as bank fee · Exclude this line |
| Bank check-up needs approval | Preparer; blocked for the preparer with the reason (P5) | Approve month |
| Unpaid walk-in sale | The invoice and how to resolve | Collect · Credit memo · Reassign |

Each generic panel has a **What to do** paragraph. With accounting terms on, panels add the posting sentence the server returns (e.g. "moves $4,615.00 from
Accounts receivable (1200) to Undeposited funds (1090)"); the UI never composes it from account rules.

**View by role.** Items appear only when the person holds the permission to act on them (e.g. "Bills due soon" for holders of `accounting:ap:pay`;
"Bill sent for your approval" for approvers of the required tier).

#### 5.1.1 Record bank deposit (dialog)

Native `<dialog>` (`appModalDialog`): bank account; deposit date (default today); the undeposited sessions as a checklist — each selected whole, showing its
bank drops (bag numbers) and amount — plus checks taken outside the register; the deposit total from the server; deposit slip reference; consequence
sentence; **Record deposit**. A 422 difference is shown
inside the dialog without losing input (`.dialog-error`).

### 5.2 Bills to pay — `/app/accounting/bills` (supersedes `payables/vendor-invoices` list routes; `bills/:billId` deep link)

1. Header actions: Approval limits (approvers), Fetch from suppliers, **Upload vendor invoice** (primary; opens the file picker; shown with
   `accounting:bill_intake:create`).
2. **Add bills** card with "Which way should I use?" explainer: drop zone (`<label for>` + visually hidden `<input type="file" multiple>` that stays in the tab
   order; formats and size from §4.8); forward-by-email address with copy button (Email-in settings — new address, allowed senders per vendor — with
   `accounting:bill_intake:manage`); links to spreadsheet import, the template and vendor-statement check; supplier connections with status, last fetch and
   **Fetch now** (`supplier:invoice:fetch`). Uploads and email-in documents are drafts (§4.9) until confirmed: the Check step shows them, and no amount owed
   includes them.
3. **How a bill moves** — four steps with live counts: Check (needs your review) · Approve (sent for approval) · Pay (approved to pay) · Done (paid this month).
4. **Needs your review** — list of bills (vendor, amount, source icon and channel, badge) and the **review panel**:
   - Header: channel and uploader, vendor, invoice number, date, total, badge.
   - Document preview with the location of each read value highlighted, open full size.
   - **What we read** form (vendor with "matches a vendor you buy from" / "Vendor not set up yet — add" opening pos-supplier's vendor form, AW23,
     invoice number, PO, invoice date, due date with "worked out from
     Net 30 — check it", tax on the invoice, total). Low-confidence fields carry "Check this".
   - Checks list: not a duplicate (vendor + invoice number + date, or the same delivery, §4.9); matches delivery `REC-…`; prices within tolerance of the delivery;
     within / over the clerk limit; vendor remit-to unchanged since the last bill (AW23).
   - Lines table: Item (+ SKU) · Ordered · Received · Billed · Price each · Amount; subtotal; tax as shown on the invoice; total.
   - Match score chip with breakdown and "What's a match score?".
   - Approval routing note and "Why can't I approve my own bill?" (AW6.1).
   - Consequence sentence; actions **Send for approval** (creator) / **Approve bill** (eligible approver) · Save for later · Discard upload (draft, reason
     required) / Reject bill. When extraction failed, the form opens empty with every field marked "Check this" and a Try reading again action (AW26).
   - No-delivery bills (shop supplies): "What was this for?" and a required note when sent for approval without a match.

### 5.3 Customer payments — `/app/accounting/payments` (replaces `payments/apply`)

1. Header: overline "Accounting · Money owed to you"; action Who owes what.
2. Four stat cards: customers owe you; overdue; payments waiting to be matched; credits customers can use.
3. Standfirst ("payments taken at the counter skip this list: they're matched as soon as they're taken and show under Matched automatically") and four
   numbered steps (pick · tick · check left over · apply).
4. Left: payments waiting (customer or "Check #…", method · date, amount, badge) and a "Matched automatically this week" disclosure with Undo per item.
5. Right — selected payment:
   - Reason chips and "Why these invoices?".
   - Invoice list (not a table — rows contain inputs): checkbox · invoice number (label for the checkbox) with workorder and description · due date with
     Overdue / Suggested badges · still owed · amount to apply (text input, `inputmode="decimal"`). Suggested invoices are pre-ticked.
   - Payment / Applying to N invoices / Left over (input preview, P7).
   - Over-application error (`role="alert"`): "You're applying $X more than this payment. Untick an invoice or lower an amount."
   - Leftover choice when Left over > 0: keep as credit (default) / refund (confirmed before money moves).
   - Terms-on posting sentence from the server; reversal sentence; actions **Apply $X [and keep $Y as credit]** (disabled while over-applied or nothing
     ticked) · Start over · Search other invoices.
   - Payment with no customer (depends on OI-8): search (by customer, invoice # or workorder #), suggested customers with reasons, required "Why this customer?" (≥ 10
     characters), Assign customer.

### 5.4 Your books — `/app/accounting/books`

1. Header: period select (open / closed months), **Export for your accountant**.
2. Tabs (buttons with `aria-pressed`, term suffix under P1): **Summary** (balance sheet) · **Who owes what** (aged AR and AP) · **All entries** (general ledger).
3. "New to this? How to read your books in one minute" disclosure.
4. Summary: *What you own* (In the bank 1000 · Waiting to be deposited 1090+1095 · Kept in drawers for change 1080 · Money customers owe you 1200 · Tires and
   parts on your shelves 1300), *What you owe* (Bills from vendors 2000 · Sales tax collected, not yet paid 2200 · Credits customers can still use 2300),
   *What's yours* (equity, with "October so far": earned 4000, cost of tires and parts sold 5000, card processing fees 6000, profit so far). Each line links to
   its entries. The selected account's latest entries: Entry · Date · What happened (+ business reference and party) · Went up · Went down · Balance after, with
   "Why 'went up' and 'went down'?" and the locked-entries sentence.
5. Who owes what: customers (Not due yet, 1–30, 31–60, 61–90, Over 90, Total; the CASH customer excluded from the aging rows and shown as a reconciling line
   so the total equals 1200; "Who to call first" tip) and vendors (Not due yet, Overdue, Due date estimated, Total; every open bill, with unapproved bills in
   their own column, so the total equals 2000).
6. All entries: search by entry #, invoice #, customer or vendor; account filter; Entry · Date · What happened · Amount · Status (Recorded, Correction, Reversed)
   with "What do Correction and Reversed mean?". An entry opens `books/entries/:journalEntryId` — routed by id so a reload or shared link can fetch it
   (`GET /v1/accounting/journal-entries/{id}`), while the page shows only the business reference `JE-YYYYMM-n` (P8) — with traceability; reverse with reason, `accounting:je:reverse`.

### 5.5 Approval limits — `/app/accounting/settings/approval-limits`

Sections with in-page links: **Bills** · **Drawer cash** · **Petty-expense categories** · **History**, and one **Save your changes** card (required "Why are
you changing this?" ≥ 10 characters; consequence sentence; Save · Undo changes).

- *Bills*: clerk limit; automatic approval limit with inline validation (≤ clerk limit); "What this means" examples recomputed from the typed values; a
  disclosure with the who-can-do-what table and the always-on rules.
- *Drawer cash*: per type an **Allowed** switch (`<input type="checkbox" role="switch">` with a label) and the cashier amount (disabled while off); bank drops
  and float changes as read-only rows; the over/short tolerance; "How are limits counted?"; "What this means at the register" examples.
- *Petty-expense categories*: Category · Examples · Recorded in (name + account number) · Status; **Add category**, relabel and deactivate shown only with
  `accounting:mapping-key:create` / `:edit` / `:deactivate`, and remapping an account only with `accounting:gl-mapping:create` (P5: hidden, not disabled);
  without them the list is read-only with the note "Changes to categories are made by someone who manages your chart of accounts"; the never-from-the-drawer list; the tax
  sentence. CAD tenants with recovery on: the tax-registration panel (read-only, link to tax settings),
  a **GST/HST claimed back** column (All of it / Half (50%)), and the cashier's three steps.
- *History*: Date · Changed by (name and role) · Change (old → new) · Reason.

Bill settings save through `pos-accounting`; drawer settings through `pos-order` (two calls, both idempotent; a partial failure states which part was saved).

### 5.6 Help pattern (every page)

- A **What's this?** (or question-specific) disclosure on every card and tricky control: native `<details>/<summary>` styled as a link with the help icon, two or
  three sentences and an example; keyboard and screen-reader operable without script.
- Focused pages open with numbered steps; every to-do panel has **What to do**; every committing action has its consequence sentence first (P4).
- Empty states name what's missing ("No bills need your review"), and the home to-do list's empty state says "You're all caught up".
- Help text is i18n keys like all other copy; the glossary pairs each plain word with the accountant's term.

### 5.7 Accessibility (ADR-0039, WCAG 2.2 AA in both themes)

- Real buttons, links, inputs and labels; icon-only buttons carry `aria-label`; selection lists use `aria-pressed`; tables have captions and `scope="col"`.
- Status is always text + colour (badges, chips, legends); chart marks have legends and an equivalent table; chart colours validated for colour-vision deficiency
  in both themes (the canvas tokens `--chart-1/2/3/-alert`).
- Focus is visible; the detail panel receives focus when an item is picked on small screens; dialogs return focus to the trigger.
- Touch targets ≥ 44px for list rows and primary actions; tabular numerals in columns, proportional numerals in hero figures.

---

## 6. Register (POS) behaviour required by this specification

Owned by the Order domain; listed so the stories are cut together.

1. Checkout refuses tender until a customer is set; a **Walk-in customer** button selects CASH and is enabled only when the tender covers the total now.
2. **Pay out** at the drawer with the fixed reasons (§4.6): petty expense (category picker from accounting's published list; receipt total; Canadian tax fields
   when enabled), vendor cash on delivery (vendor picker), bank drop (bag number), float change (manager).
3. Over a limit, manager elevation at the drawer with their own sign-in; approver ≠ cashier.
4. Opening float = the register's configured float; variance tolerance from the tenant setting.
5. Close fact with per-movement detail.

---

## 7. Backend changes (PROPOSED)

Endpoints follow each module's existing base-path convention (§1.2); permission strings are registered in the module's permission registry, controllers carry
OpenAPI annotations and `@EmitEvent`, and **API Artifacts Sync** runs after every controller or permission change.

### 7.1 `pos-accounting`

| Capability | Contract (method · path · permission) | Notes |
| --- | --- | --- |
| Cash position read | `GET /v1/accounting/cash-position` · `reporting:view:financial-statements` (+ `accounting:reconciliation:view` for statement fields) | §4.1; returns total, components, per-bank rows, `asOf`, needs-attention items, unpaid walk-in sales |
| Cash outlook | `GET /v1/accounting/cash-outlook?days=30` · `reporting:view:financial-statements` | §4.2; daily points, low point, in/out/possible, estimated-due-date bills |
| Work items (to-do) | `GET /v1/accounting/work-items?area=&cursor=` · per item type | Read model over bill exceptions and approvals, unapplied payments, unmatched bank lines, undeposited sessions, reconciliations awaiting approval, unpaid walk-in sales; filtered server-side by the caller's permissions; each item carries type, business references, plain reason code, amount, status and the actions the caller may take |
| Done automatically | `GET /v1/accounting/work-items/automatic?since=` · as above | Auto-applied payments, auto-matched bank lines, auto-approved bills, with undo where the underlying command supports reversal |
| Unapplied payments | `GET /v1/accounting/receivable-payments?status=AVAILABLE` · `accounting:payment:apply` | Closes G4; with match suggestions (customer, exact total, remittance references) |
| Assign customer (one time) | `POST /v1/accounting/receivable-payments/{paymentId}/customer-assignment` · `accounting:payment:assign-customer` | Depends on OI-8 (an unidentified receipt must exist first). Body `{customerId, justification (≥ 10 characters), requestId}`; response: the payment with its customer, `assignedAt`, `assignedBy`. One time only: a payment that already has a customer → 409 `PAYMENT_CUSTOMER_ALREADY_ASSIGNED` (a wrong assignment is corrected by reversing the payment's applications and the payment, never by reassigning). Idempotent on `requestId`: a replay returns the first result. Actor from the security context (ADR-0018); one audit row with actor, customer, justification and request id; event `ACCOUNTING_PAYMENT_CUSTOMER_ASSIGN` |
| Eligible invoices | `GET /v1/accounting/customers/{customerId}/open-invoices` · `accounting:payment:apply` | Derived balance due (net of credits, memos and deposits, contract guide CAP-052); pos-invoice search does not net them |
| Automatic application | listener on `PaymentSettledV1` | AW14; Dr 1090 / Cr 1200 at `settledAt` |
| Vendor-bill approval | `POST /v1/accounting/vendor-bills/{id}/submit-for-approval` · `…/approve` · `…/reject` (same base) · `accounting:ap:approve` / `…:approve_over_limit`; reject `accounting:ap:reject` | §4.3; 403 `AP_APPROVAL_LIMIT_EXCEEDED`, `AP_BILL_SELF_APPROVAL`; 422 `AP_BILL_NOT_APPROVABLE` (e.g. `CURRENCY_HOLD`) |
| Real due date during review | `PUT /v1/accounting/vendor-bills/{id}/due-date` · `accounting:ap:approve` | audited; replaces the estimate |
| System approver | recorded on automatic approvals | identity and the automatic limit in force |
| Bill GL posting at approval | depends on OI-2 / OI-3 | EDI bills |
| AP approval policy | `GET/PUT /v1/accounting/ap-approval-policy` · `accounting:ap_approval_policy:manage` | clerk limit, automatic limit, history; `defaultTerms` (`AP_DEFAULT_TERMS`, AW33; S13, louisburroughs/durion-positivity-backend#2510): values and audit in §4.2 |
| Cash safety cushion | `GET/PUT /v1/accounting/configuration/cash-safety-cushion` · PUT `accounting:period:hard_lock` (OI-6) | AW34; body `{amount \| null, currencyCode, justification, requestId}`; validation, codes and audit in §4.2; event `ACCOUNTING_CONFIGURATION_CASH_SAFETY_CUSHION_SET` |
| Pay guard | EXISTING `POST /v1/accounting/ap/payments` | 403 `AP_PAYMENT_SELF_APPROVED_BILL` |
| Resolve match exception | EXISTING `POST /v1/accounting/vendor-bills/{billId}/resolve-exception` and `POST …/match-candidates/{candidateId}/select` | `operatorId` removed from both requests; `ACCEPT` subject to the limit; candidate selection moves the bill to `AWAITING_APPROVAL` |
| Undeposited sessions | `GET /v1/accounting/undeposited-sessions` · `accounting:deposit:create` | from `RegisterSessionClosedV1` |
| Record / reverse deposit | `POST /v1/accounting/deposits` · `accounting:deposit:create`; `POST /v1/accounting/deposits/{id}/reversal` · `accounting:deposit:reverse` | §4.5; `requestId`; posting category `BANK_DEPOSIT` |
| Float | `POST /v1/accounting/registers/{registerId}/float` (change) · `…/float/go-live` (once) · `…/float/relocation` (AW32; S38, louisburroughs/durion-positivity-backend#2571) · `accounting:float:manage`, for a relocation at both locations | §4.6; 409 `FLOAT_ALREADY_ESTABLISHED`; the relocation request and codes are in §4.6 |
| Opening bank balance | `POST /v1/accounting/bank-accounts/{glAccountId}/opening-balance` · `accounting:je:create` and `accounting:je:post` | AW35 (S39, louisburroughs/durion-positivity-backend#2572); posting category `OPENING_BALANCE`; request, guards and codes in §4.6 |
| Drawer movement posting | listener on the close fact v2 | `REGISTER_CASH_MOVEMENT` |
| Vendor cash on delivery | listener on the close fact v2 → AP payment, method `CASH` (new), no gateway call | unapplied until the vendor's bill is allocated; Needs attention after N days |
| Petty-expense categories | EXISTING mapping-key and gl-mapping endpoints under `REGISTER_CASH_MOVEMENT` | publishes `accounting.petty-expense-category.changed` |
| Estimated due dates | in cash outlook and aged AP | never stored; terms from the purchase order, then the vendor's default, then `AP_DEFAULT_TERMS`, named by `termsSource` (AW33; S19, louisburroughs/durion-positivity-backend#2515) |
| Seed | accounts 1080, 3000, 3900, 6295, 6375, 6380 (AW30); renumbering (§4.6); retread add-on per tenant; (CAD) 1250, 1260; subtypes `CASH_ON_HAND`, `TAX_RECOVERABLE`; posting categories `BANK_DEPOSIT`, `REGISTER_CASH_MOVEMENT`, `REGISTER_FLOAT`, `OPENING_BALANCE`; settings `AP_CLERK_APPROVAL_LIMIT`, `AP_AUTO_APPROVAL_LIMIT`, `AP_DEFAULT_TERMS`, `CASH_SAFETY_CUSHION` | repeatable seeds |
| Status | `VendorBillStatus.AWAITING_APPROVAL`; `REJECTED` write path | DB check constraint |
| Permissions | register and enforce the catalogued `accounting:ap:approve` and `accounting:ap:reject`; new `accounting:ap:approve_over_limit`, `accounting:ap_approval_policy:manage`, `accounting:deposit:create`, `accounting:deposit:reverse`, `accounting:float:manage` | registry + security catalog |
| Events | `ACCOUNTING_PAYMENT_CUSTOMER_ASSIGN`, `ACCOUNTING_VENDOR_BILL_SUBMIT`, `ACCOUNTING_VENDOR_BILL_APPROVE`, `ACCOUNTING_VENDOR_BILL_REJECT`, `accounting.deposit.recorded`, `accounting.float.changed`, `accounting.petty-expense-category.changed` | event-type registry thresholds: `approval` / `write` |
| Events (AW32–AW35) | `accounting.float.changed` gains kind `RELOCATION` and a nullable `previousLocationId`, additive with a `schemaVersion` bump (ADR-0044 §3; S38); `ACCOUNTING_REGISTER_FLOAT_RELOCATE`, `ACCOUNTING_BANK_OPENING_BALANCE_ESTABLISH`, `ACCOUNTING_CONFIGURATION_CASH_SAFETY_CUSHION_SET` | threshold `write` |
| CAD | `inputTaxRecoveryEnabled`; 1250/1260 postings; vendor-bill tax split | §4.7 |

### 7.2 `pos-order`

Checkout customer requirement and Walk-in selection; movement reasons enum with category/vendor/bag number; elevation for over-limit movements; session policy
`GET/PUT /v1/orders/session-policy` (`order:session_policy:manage`, event `ORDER_SESSION_POLICY_UPDATE`); `order:session:approve_cash_movement`; opening float from
configuration; variance tolerance from the policy; `clerkId` from the security context; close fact v2 (or movement facts); CAD rounding exclusion. A register
moving between locations (AW32, AW36): no move while a session is OPEN or CLOSING; pos-order publishes `order.session.opened` (S40) and pos-accounting
refuses a relocation from its session replica (422 `FLOAT_REGISTER_SESSION_OPEN`, S38); drawer postings at close take the session's
location, never the float row's.
**Requires Order sign-off on the R11.2 reversal and the R6.1 replacement.**

### 7.3 `pos-invoice`, `pos-customer`, `pos-security-service`, `pos-tax`, `pos-supplier`

- `pos-invoice`: refuse finalization and payment capture without `partyId`; `PaymentSettledV1.partyId` non-null (contract change in `pos-domain-events`).
- `pos-customer`: CASH house account per tenant, guards, `houseAccount` on the party fact; exclusions in analytics.
- `pos-security-service`: template roles `ACCOUNTING_CLERK` (replaces fixture `ACCOUNTING_ASSOCIATE`) and `GENERAL_MANAGER` (promoted from fixture); grants per
  §2.3 and §4; clerks lose `accounting:ap:pay`; `CONTROLLER`, `ACCOUNTING_CLERK` and `GENERAL_MANAGER` gain `accounting:payment:apply` (today only
  `ACCOUNT_MANAGER` and `ADMIN` hold it, G13). **Requires security-domain sign-off.**
- `pos-accounting` permission registry: register `accounting:payment:assign-customer` (AD-004); move `accounting:period:override` into `AccountingPermissions` and
  its registration; register and enforce the catalogued `accounting:ap:approve` and `accounting:ap:reject` (G13).
- `pos-tax` (CAD): Canadian rates and registrations; plausibility lookup.
- `pos-supplier`: additive fields on `SupplierInvoiceReceivedV1` — `channel` (ADR-0051 protocol-family values), `exchangeId` (provenance only: pos-accounting
  never calls pos-supplier to resolve it, ADR-0044 R1), due date or terms, and tax by type (CAD: split the EDI tax total). All nullable; a missing value
  means today's behaviour (ADR-0044 §3); `vendorId` is always set from now on. The exchange audit keeps its own 400-day policy (ADR-0050) outside the bill
  file store. pos-supplier never stores or republishes uploaded documents (AW22).
- `pos-supplier` (AW23): the vendor master (`Vendor`, endpoints under `/v1/supplier/vendors`, `supplier:vendor:read|write`, remit-to approval
  `supplier:vendor_remit:approve`), profiles gain a required `vendorId`, the `supplier.vendor.updated` fact and the per-tenant `supplier.manifest.v1` with
  re-send (new: pos-supplier publishes no vendor manifest today). `SUPPORT` keeps `supplier:vendor:read`, read-only (§12 OI-16).
- `pos-order`: an `ext_supplier_vendor` copy; a new purchase order's vendor must exist and be active in it (G15). `pos-inventory` takes the vendor id through its
  existing purchase-order copy and keeps its own vendor copy, which its purchase suggestions need to name a pos-supplier vendor.

### 7.4 Bill intake (AW22–AW29)

Owners and rules are in §4.9. Contracts below are PROPOSED; paths and permissions avoid the word "invoice", which belongs to pos-invoice (`invoice:*`) and to
pos-supplier's machine channels (`supplier:invoice:fetch`).

| Capability | Contract (method · path · permission) | Notes |
| --- | --- | --- |
| Upload files | `POST /v1/accounting/bill-intake/uploads` (multipart) · `accounting:bill_intake:create` | One `BillIntakeItem` per file in `QUARANTINED`; `requestId` |
| Spreadsheet import | `POST /v1/accounting/bill-intake/imports` (file + column mapping) · `accounting:bill_intake:create` | §4.8 columns; one draft per invoice; errors per row |
| List and read drafts | `GET /v1/accounting/bill-intake?status=` · `GET …/{intakeId}` · `accounting:ap:view` | Fields carry confidence and "Check this" |
| Edit the read-back | `PUT /v1/accounting/bill-intake/{intakeId}` · `accounting:bill_intake:create` | Only in `READY_FOR_REVIEW` |
| Confirm | `POST /v1/accounting/bill-intake/{intakeId}/confirmation` · `accounting:bill_intake:create` | Through `BillIntakePort`; the caller becomes the creator; 409 `AP_BILL_DUPLICATE` with the original's reference; 422 `BILL_INTAKE_FIELDS_UNCHECKED` |
| Discard | `POST /v1/accounting/bill-intake/{intakeId}/discard` · `accounting:bill_intake:create` | Reason ≥ 10 characters |
| Retry extraction | `POST /v1/accounting/bill-intake/{intakeId}/extraction-retry` · `accounting:bill_intake:create` | §4.9 (AW26) |
| Email-in received | command from the inbound-mail edge | File references + tenant from the receiving address; drafts as for an upload |
| Source file | `GET /v1/accounting/bill-source-files/{fileId}/preview` and `…/content` · `accounting:ap:view`; `DELETE …/{fileId}` · `accounting:bill_source_file:delete` | Controls 5, 6 and 8 below |
| Vendor statements | `POST /v1/accounting/vendor-statements` · `accounting:bill_intake:create`; `POST …/{statementId}/reconciliation` · `accounting:vendor_statement:reconcile` | Posts nothing (AW27) |
| Intake settings | `GET/PUT /v1/accounting/bill-intake/settings` · `accounting:bill_intake:manage` | Email-in address request and rotation, allowed senders per vendor, mapping templates, retention period |
| Vendor copy | `ext_supplier_vendor` consumer of `supplier.vendor.updated`; `ap_vendor_settings`; `ap_vendor` retired | AW23; `VENDOR_INACTIVE`, `VENDOR_NOT_FOUND` |
| Remit-to check at payment | `approvedRemitToVersion` on approval; confirm a changed remit-to · `accounting:ap:approve` (not the payer) | 409 `VENDOR_PAYMENT_DETAILS_CHANGED` |
| EDI adapter | the `SupplierInvoiceReceivedV1` listener becomes an adapter into `BillIntakePort` | Records the event durably in the consumer transaction, whether it becomes a draft or a bill (a lost event is a lost debt); OI-11 |
| Data fixes | partial unique index on (`tenant_id`, `vendor_id`, normalised bill number, `bill_date`) excluding `VOIDED` / `REJECTED`, shipped first as a defect fix (G14); vendor key = pos-supplier vendor id on every bill and AP payment, alpha bills reseeded (G15); delivery-reference duplicate check (G16) | G14–G16 |
| Permissions | new `accounting:bill_intake:create`, `accounting:bill_intake:manage`, `accounting:vendor_statement:reconcile`, `accounting:bill_source_file:delete` | Registry + security catalog; no new `supplier:invoice:*` key covers documents people upload or type in |
| Events | `ACCOUNTING_BILL_INTAKE_CONFIRM`, `ACCOUNTING_BILL_INTAKE_DISCARD`, `ACCOUNTING_BILL_SOURCE_FILE_DELETE`, `ACCOUNTING_VENDOR_STATEMENT_RECONCILE` | Event-type registry thresholds `write` |

**Permissions (AW25).**

| Permission | Allows | Holders (PROPOSED; Security sign-off, OI-5) |
| --- | --- | --- |
| `accounting:bill_intake:create` | Upload, import, statement upload, edit a draft, confirm, discard with a reason, retry | Clerk, CONTROLLER, ADMIN |
| `accounting:bill_intake:manage` | Email-in address and rotation, allowed senders, mapping templates, retention settings | CONTROLLER, ADMIN |
| `accounting:vendor_statement:reconcile` | Reconcile a vendor statement (posts nothing) | Clerk, CONTROLLER, ADMIN |
| `accounting:bill_source_file:delete` | Delete a source file — only for a `DISCARDED` draft or after retention expiry; never while its bill has an open amount, is within its retention period or is on legal hold; justification ≥ 10 characters; audited | ADMIN |
| `accounting:ap:view` (EXISTING) | Preview and download source files | As today; it never grants `supplier:audit:read`, which opening the raw EDI payload behind `exchangeId` requires |
| `supplier:invoice:fetch`, `supplier:profile:write`, `supplier:audit:read` (EXISTING) | Machine channels, profiles and their audit | Unchanged |

Approval keys are unchanged (§4.3).

**File storage (AW24).** In v1 the source-file bytes stay in pos-accounting (the bank-statement file pattern, SPEC-manual-bank-reconciliation D13) behind an
internal `FileStore` port:

- The port is vault-shaped: opaque file id, tenant, sha256, content type, retention-until, legal-hold flag, purge. It must allow object storage behind it;
  25 MB files don't belong in Postgres.
- v1 already carries the retention purge and legal hold (the `BankImportRetentionJob` pattern).
- Files are stored only after validation and scanning pass; until then they sit in quarantine. They are served only through pos-accounting's authorized
  endpoint.
- The second module that needs to keep source files (expected: outbound CFDI XML in pos-invoice, or billing approval artifacts) does not copy this pattern. Its
  arrival triggers a platform file-vault utility, and pos-accounting's bank-statement and bill files move into it.

**Security controls (normative).** Every uploaded file and email attachment is untrusted input.

1. **Quarantine first.** Files land in tenant-scoped quarantine storage and are not parsed, previewed or downloadable until validation and scanning pass.
2. **Content validation.** Allow-list of types by magic bytes, not by extension or declared MIME type (PDF, JPEG, PNG, HEIC, TIFF, CSV, XLSX); size ≤ 25 MB per file
   and ≤ 20 attachments per email; PDFs ≤ 30 pages; reject encrypted or password-protected PDFs and PDFs with JavaScript, launch actions or embedded files;
   XLSX without macros (refuse `.xlsm` and VBA parts); CSV treated as data only, and any cell beginning `=`, `+`, `-` or `@` neutralised on every re-export.
3. **Malware scanning** before any parser runs; a positive or failed scan rejects the file with a plain message and keeps no parsed output.
4. **Parser isolation.** Extraction runs in the extraction worker (§4.9), as an asynchronous job, with CPU, memory, time and decompression-ratio limits; a limit
   breach rejects the file.
5. **Preview.** The browser shows a server-rendered image of each page or a sandboxed viewer with scripting disabled; extracted text is rendered as text, never as
   HTML (ADR-0065).
6. **Tenant isolation and authorization.** Storage keys and metadata rows carry `tenant_id` under row-level security (ADR-0062); a file is served only through
   pos-accounting's authorized endpoint (`accounting:ap:view`) that checks tenant and permission on every request, never through a public or long-lived URL;
   a 403 or 404 never reveals whether the file exists.
7. **Email-in.** One unguessable address per tenant, rotatable; mail must pass SPF/DKIM/DMARC alignment; optional sender allow-list per vendor; rate limits per
   address; a rejected email is reported to the shop, never to the sender. The inbound-mail edge owns transport and address provisioning; pos-accounting owns
   the tenant-facing settings (`accounting:bill_intake:manage`). An allow-list entry needs an existing vendor and is audited with a justification; allow-lists
   never come from vendor profiles.
8. **Retention and deletion.** Source files are kept with the bill for a configurable period (default 7 years, matching bank-statement files, SPEC-manual-
   bank-reconciliation D13): a paid bill's source document is tax evidence. The period runs from the day the bill is closed (fully paid, voided or
   rejected), never from upload, so it cannot expire while anything is owed. Deletion needs `accounting:bill_source_file:delete` and is allowed only for a
   discarded draft or after retention expiry, never while the bill has an open amount and never on legal hold; the retention purge obeys the same guards.
   Rejected and discarded uploads are purged after 30 days.
9. **Audit.** Upload, scan result, extraction, every view and download, and deletion are audited with actor, tenant and file hash.
10. **No auto-trust.** Extracted values are suggestions; nothing posts or becomes owed until a person confirms the read-back and the bill is approved (§4.3,
    §4.8).

---

## 8. Frontend implementation notes

### 8.1 Routes

| Route | Page | Gate (`data.permissions`, any-of) |
| --- | --- | --- |
| `''` | `AccountingHomePageComponent` (replaces the landing) | any accounting permission (existing group gate) |
| `bills`, `bills/:billId` | `BillsPageComponent` | `accounting:ap:view` |
| `payments` | `CustomerPaymentsPageComponent` | `accounting:payment:apply` |
| `books` | `BooksPageComponent` | any of `reporting:view:financial-statements`, `accounting:coa:view`, `accounting:je:view`; each tab is shown only with its own permission (Summary and Who owes what: `reporting:view:financial-statements`; All entries: `accounting:je:view`; account drill-down lists: `accounting:coa:view` or `reporting:view:financial-statements`) |
| `books/entries/:journalEntryId` | `JournalEntryDetailPageComponent` | `accounting:je:view` only (its own gate; the any-of gate above never reaches it) |
| `settings/approval-limits` | `ApprovalLimitsPageComponent` | `accounting:ap_approval_policy:manage` or `order:session_policy:manage` or `accounting:mapping-key:edit` |

Inside Bills to pay, the Add bills controls need `accounting:bill_intake:create`, Email-in settings need `accounting:bill_intake:manage`, the vendor-statement
check needs `accounting:vendor_statement:reconcile`, and Fetch now needs `supplier:invoice:fetch`; each is hidden without its permission.

Old routes `payments/apply` and `payables/vendor-invoices*` redirect to the new ones (pre-production: no shims beyond redirects). Constants are added to
`route-permissions.ts`; the nav-registry spec keeps routes and nav in agreement.

### 8.2 Services and state

- Feature services wrap the generated SDK (ADR-0041): EXISTING `FinancialReportingService`, `GLAccountsService`, `JournalEntriesService`,
  `PaymentApplicationsService`, `VendorBillAPIService`, `APPaymentsService`, `BankAccountsService`, `BankReconciliationService`, `AccountingPeriodsService`,
  `InvoiceSearchService`, `SupplierInvoicesService`; PROPOSED operations of §7.
- Idempotency keys (`applicationRequestId`, `requestId`, `paymentRef`) are generated in the frontend **once per user intent** — when the form or dialog
  opens — and reused for double clicks and retries, including timeouts and unknown outcomes. A key is rotated only after a confirmed success, or when the
  person explicitly starts over (Start over, closing the dialog, choosing a different payment). The submit button is also disabled while a request is in flight.
- The **Show accounting terms** and **Start here** preferences persist per person (server-side user preference; `localStorage` only as a cache).
- Never send a tenant id; never hard-code enums (adjustment types, categories, reasons come from the server; unknown values render as "Unknown").
- No `HttpClient` in features; no client-side money arithmetic beyond the input preview of §5.3.

### 8.3 Styling

Durion Positivity design system: `--themeBackground` page, `.card`, `.inset`, `.btn` modifiers, `.badge--*`, `.dur-status`, `.data-table`, `.alert-*`,
`.field`; Barlow Semi Condensed for headings, Noto Sans for body, mono for references; light and dark themes. Chart colours are four new tokens added to
`src/styles.css` for both themes (validated): `--chart-1` (#2b4c78 / #668fc2), `--chart-2` (#7fa4d1 / #d3e3f6), `--chart-3` (#cc9030 / #e3bd78),
`--chart-alert` (#ba1a1a / #ffb4ab), plus `--meter-track` and `--chart-wash`; each listed in `design/source/theme-tokens.md` and `tokens.json`.

---

## 9. Acceptance criteria and focused tests

### 9.1 Accounting home

- Your cash shows the server's total and three components with an "as of" time; the UI performs no addition (spy on the service: one call, values rendered
  verbatim). With 2350 ≠ 0 a Needs-attention row appears; with a CASH balance ≠ 0 "Unpaid walk-in sales" appears.
- The outlook draws the server's points; the table view lists the same values; bills with estimated due dates are labelled; the float is excluded.
- To-do items appear only for permissions held (clerk vs approver fixtures); the bill-exception panel shows the limit note matching the caller's tier;
  the preparer sees "Approve month" `aria-disabled` with the reason.
- Start here hides on dismiss and stays hidden on reload; Help guide toggles with `aria-expanded`.
- Record bank deposit: success posts once per `requestId` (double click → one deposit); a 422 shows the difference inside the dialog.

### 9.2 Bills

- A bill created by the caller shows Send for approval, never Approve; an approver over the clerk limit sees Approve only with `approve_over_limit`.
- `ACCEPT` over the limit returns `AP_APPROVAL_LIMIT_EXCEEDED` for a clerk; automatic approval never exceeds the automatic limit (default 0 → every bill to a
  person).
- Paying a bill one approved returns `AP_PAYMENT_SELF_APPROVED_BILL`.
- Read-back fields marked "Check this" block confirmation until accepted or corrected (422 `BILL_INTAKE_FIELDS_UNCHECKED`).
- A second bill with the same vendor, normalised number and date is refused with `AP_BILL_DUPLICATE` and a link to the original, whichever channel brought
  either copy (upload + EDI, import + upload); after the original is voided the re-issue is accepted.
- An uploaded invoice for a delivery that already has a goods-receipt bill is flagged as a duplicate by delivery reference.
- An unconfirmed draft never appears in aged payables, the cash outlook or any amount owed.
- With the extraction provider unavailable, an upload reaches `READY_FOR_REVIEW` with every field empty and marked "Check this"; the confirmed bill records
  `extractionOutcome = MANUAL`.
- The person who confirms an upload is the bill's creator and can't approve it.
- An EDI invoice without a `vendorId` is parked for a person and one from an inactive vendor becomes `MATCH_EXCEPTION`; neither is dropped, and the EDI
  event is stored durably even when the bill can't be created yet.
- Confirming a draft whose vendor isn't in the copy is refused with `VENDOR_NOT_FOUND`; when the vendor fact arrives the draft re-resolves. The person who
  created a vendor can't approve its first bill.
- Paying a bill whose vendor's remit-to changed after approval is refused with `VENDOR_PAYMENT_DETAILS_CHANGED` until a second approver confirms the change;
  a new bill or payment for an inactive vendor is refused with `VENDOR_INACTIVE`.
- Deleting a source file is refused while its bill has an open amount (including a partly paid `APPROVED` bill), within the retention period counted from
  the bill's closing, or on legal hold, and the retention purge skips such files; deleting a discarded draft's file without a ≥ 10-character justification
  is refused.

### 9.3 Customer payments

- Suggested invoices are pre-ticked; unticking updates the preview; over-application disables Apply and announces the error; leftover offers credit/refund;
  refund asks for confirmation.
- Submitting twice with the same `applicationRequestId` applies once; the server's result replaces the preview.
- Assigning a customer without a ≥ 10-character reason is refused (422).
- Assigning a customer to an unidentified payment succeeds once: the response carries the customer, `assignedAt` and `assignedBy`, and the payment then appears
  with that customer's suggested invoices.
- A second assignment to the same payment → 409 `PAYMENT_CUSTOMER_ALREADY_ASSIGNED`; replaying the first request with the same `requestId` returns the first
  result and writes no second audit row.

### 9.4 Counter and drawer (backend)

- Checkout without a customer refuses tender; CASH with partial tender is refused; invoice finalize with null party is refused.
- A settled payment for an invoice posts Dr 1090 / Cr 1200 once (idempotent on the settlement).
- Two petty expenses whose running total exceeds the limit require elevation on the second; switching petty off mid-session refuses the next one only.
- Deposit posting balances (Dr bank = Cr 1090 ± 1095); unbalanced → 422; reversal restores 1090/1095.
- Go-live float twice → 409; change float posts against the chosen bank account.
- A register moved from A to B posts Dr 1080 [B] / Cr 1080 [A] for its amount and no other line; a caller without float management at either location → 403
  (AW32).
- An opening bank balance with an outstanding check posts the check as its own bank line; once it is registered, the first reconciliation's opening
  difference is zero (AW35).

### 9.5 Your books and settings

- Summary lines equal the balance-sheet response; drill-down lists entries with business references; reversed entries show both rows.
- Approval limits: automatic > clerk shows the inline error and disables Save; a save without a reason is refused; drawer settings and bill settings save
  through their own services and report a partial failure precisely.
- CAD tenant with `inputTaxRecoveryEnabled`: the recovery column and registration panel appear; a USD tenant never sees them.

### 9.5a Rules that need their own criterion

- An unapproved bill never carries approval fields (G12).
- The uploader of a bill can't approve it.
- A request carrying `operatorId` / `clerkId` is ignored; the actor comes from the security context.
- A party-mismatched settled payment is not auto-applied.
- A CASH balance at day end raises Unpaid walk-in sales.
- A deposit into a closed period needs an override; into a hard-locked period → 422.
- A USD tenant has no 1250 / 1260 accounts.

### 9.6 Cross-cutting

- i18n keys present in all six locales (`npm run i18n:check`); a11y smoke on every new route with no serious violations; Vitest in ChromiumHeadless; both
  themes.

---

## 10. Decisions record (2026-10-05, 2026-10-07)

| # | Decision | Decided by | Consequence |
| --- | --- | --- | --- |
| AW1 | A single pane is the home and triage hub; items open in an in-place detail panel; heavy work lives in focused pages reached from a sub-navigation | Design, accepted by the platform owner for specification | §5 |
| AW2 | Audience: accounting roles plus admin, gated by permissions | Platform owner (brief); domain mapping | §2.3, §8.1 |
| AW3 | Plain language first with a "Show accounting terms" preference; help on every page; pages must be intuitive and self-explanatory | Platform owner ("lots of help … intuitive") | §3, §5.6 |
| AW4 | Clerks approve bills up to a configurable limit; above it Accounting (CONTROLLER) or General Manager approves; Accounting and GM set the limit. Roles: new `ACCOUNTING_CLERK`, promote `GENERAL_MANAGER` | Platform owner; role mapping by the Accounting Domain Agent | §4.3, §7.3 |
| AW5 | One clerk limit and one automatic-approval limit per tenant (default 0), compared with the total including tax; permissions `ap:approve`, `ap:approve_over_limit`, `ap_approval_policy:manage` | Accounting Domain Agent | §4.3 |
| AW6 | Creator ≠ approver; approver ≠ payer; `ACCEPT` is approval; clerks don't pay; exceptions are tenant switches, default off | Accounting Domain Agent | §4.3 |
| AW7 | General managers pay bills (`accounting:ap:pay`) | Platform owner | §2.3 |
| AW8 | New `AWAITING_APPROVAL`; `REJECTED` write path; send without match with justification | Accounting Domain Agent | §4.3 |
| AW9 | Your cash = In the bank (`BANK_CASH`) + Waiting to be deposited (1090 + 1095) + Kept in drawers (1080); statement alongside; 2350 never cash | Accounting Domain Agent (delegated by the owner) | §4.1 |
| AW10 | Record bank deposit command and undeposited-sessions read model in pos-accounting | Accounting Domain Agent (delegated) | §4.5 |
| AW11 | Outlook counts approved bills; pending bills separate; overdue invoices not assumed; estimated due dates (PO terms, else Net 30) never stored; terms order amended by AW33 | Accounting Domain Agent (delegated) | §4.2 |
| AW12 | Every sale needs a registered customer; a system CASH customer, picked explicitly, only for paid-in-full sales; nets to zero daily | Platform owner (rule and CASH fallback); controls by the Accounting Domain Agent | §4.4 |
| AW13 | From go-live only; no backfill of past sales | Platform owner | §4.4 |
| AW14 | Settled payments apply automatically to the invoice they were taken against | Accounting Domain Agent | §4.4 |
| AW15 | Fixed drawer-movement reasons with postings; no free text, no Other | Accounting Domain Agent (delegated) | §4.6 |
| AW16 | Float on the books: 1080 Register Float, fixed per register, Change float command | Accounting Domain Agent (delegated) | §4.6 |
| AW17 | Go-live float against 3900 Opening Balance Equity, cleared to 3000 Owner's Equity | Accounting Domain Agent (delegated) | §4.6 |
| AW18 | Nine petty-expense categories (repairs split into building and equipment), mapped to accounts per AW30; never-allowed list; tax-included entry (US) | Accounting Domain Agent (delegated) | §4.6 |
| AW19 | Drawer limits configurable as allowed / amount per type, per tenant, per session running total; over/short tolerance joins; Order owns the settings | Platform owner (allowed / amount); details by the Accounting Domain Agent | §4.6 |
| AW20 | Canadian input-tax recovery: 1250/1260, recovery flag, copied amounts, $100 registration-number rule, category recoverable % (meals 50%) | Accounting Domain Agent (delegated); **pending confirmation by a Canadian accountant** | §4.7 |
| AW21 | Intake formats by priority (v1 / v2 / later) and the minimal CSV columns | Research presented to the platform owner | §4.8 |
| AW22 | pos-accounting owns every human intake channel (upload, import, email-in documents, vendor statements), the `BillIntakeItem` draft, confirm, cross-channel duplicates and bill creation through one `BillIntakePort`; pos-supplier keeps only credentialed machine channels and never republishes uploads; a new extraction worker (utility) reads files; a new inbound-mail edge carries email; pos-invoice and pos-documents own none of it. Needs a new ADR, an ADR-0044 §1 classification and an ADR-0049 §1 scope note | Accounting, Positivity (Integrations) and Invoicing & Payments Domain Agents, jointly (OI-1) | §4.9, §7.4 |
| AW23 | The vendor master is pos-supplier's (every vendor, with or without a connection; profiles belong to one vendor; remit-to changes need a second person); pos-accounting, pos-order and pos-inventory keep copies from `supplier.vendor.updated`, holding every vendor with its status; bills and AP payments key on the pos-supplier vendor id; payment re-checks the remit-to version; AP settings stay accounting's | Platform owner (master in pos-supplier, accounting keeps a copy); details by the Positivity (Integrations) and Accounting Domain Agents | §4.9, §7.3, §7.4 |
| AW24 | Source files stay in pos-accounting behind a vault-shaped `FileStore` port with retention purge and legal hold from v1; a platform file vault replaces it when a second module needs one; the supplier exchange audit stays separate | Same three agents | §7.4 |
| AW25 | New permissions `accounting:bill_intake:create`, `accounting:bill_intake:manage`, `accounting:vendor_statement:reconcile`, `accounting:bill_source_file:delete`; no "invoice" in accounting paths or keys | Same three agents (final say: Accounting) | §7.4 |
| AW26 | Extraction failure falls back to manual entry under the same rules; nothing is created automatically | Accounting Domain Agent | §4.9 |
| AW27 | Vendor statements are reconciled by the AP clerk and post nothing; credits arrive as vendor credit notes through intake, never by adjusting an approved bill; partial payment of approved bills stays as it is | Accounting Domain Agent | §4.9 |
| AW28 | EDI invoices are collected by pull; push would reverse architecture decision 7 and needs an ADR-0049 amendment and a dedicated authenticated ingress | Positivity (Integrations) Domain Agent; **platform owner confirms when choosing the EDI provider** | §4.8 |
| AW29 | Inbound CFDI is an intake format (extraction worker → pos-accounting); outbound CFDI is pos-invoice's; the two share at most a non-deployed schema library; one later ADR amendment places SAT/PAC validation for both directions (one connector) | Same three agents | §4.8, §4.9 |
| AW30 | Expense range plan 6000–6999; petty categories post to existing 6340, 6430, 6370, 6210, 6410, 6250 and new 6380, 6375, 6295; existing accounts renumbered (6010→6100, 6015→6102, 6025→6105, 6115→6040, 6900→4940) by a new migration; the retread add-on is a tenant-setup choice | Accounting Domain Agent (the platform owner delegated the numbers and allowed renumbering: no real data) | §4.6, §7.1 |
| AW31 | Sign-offs given: Order (customer required at checkout, R11.2 reversed; cart-customer change and tendered amount; fixed drawer reasons, allowed / amount limits, manager approval; opening float from configuration), Security (roles `ACCOUNTING_CLERK` and `GENERAL_MANAGER`; `accounting:ap:approve` / `accounting:ap:reject` reinstated; manager approval at a shared register by a **step-up endpoint** returning a single-use approval token, no second sign-in), CRM (CASH house account), Invoicing & Payments (missing customer refused; `PaymentSettledV1.partyId` required, schema 2), Inventory (only mapped, active vendors are purchase candidates; admin maintains the mapping) | Platform owner | §4.4, §4.6, §7.2, §7.3 |
| AW32 | A register's float moves to another location by one relocation command (reason `ENTERED_IN_ERROR` \| `MOVED`): a 1080 reclass between location dimensions for the current amount; posted lines never edited; `accounting:float:manage` at both locations | Accounting Domain Agent (2026-10-07). Open sessions: Order (OI-15) | §4.6, §7.1, §7.2; S38 |
| AW33 | `AP_DEFAULT_TERMS` is written through the AP approval policy, audited; estimated due dates take terms from the purchase order, then the vendor's default, then `AP_DEFAULT_TERMS` (amends AW11) | Accounting Domain Agent (2026-10-07) | §4.2, §7.1; louisburroughs/durion-positivity-backend#2510, #2515 |
| AW34 | Safety cushion: one tenant-wide threshold in functional currency for the outlook only, never posted; a warning when the projected low point is below it; written through its configuration endpoint | Accounting Domain Agent (2026-10-07); writer and warning-only: platform owner (OI-6) | §4.2, §7.1; louisburroughs/durion-positivity-backend#2515, #2574 |
| AW35 | Opening bank balances: once per bank account, dated on the cutover date, the bank statement balance plus one bank line per outstanding item against 3900; the first reconciled statement starts the next day and registers the items as outstanding | Accounting Domain Agent (2026-10-07; OI-10) | §4.6, §7.1; S39 |
| AW36 | No register move while its session is OPEN or CLOSING. pos-order publishes `order.session.opened`; pos-accounting refuses a relocation from a session replica (422 `FLOAT_REGISTER_SESSION_OPEN`); pos-order refuses an open at a location other than the float's (422 `REGISTER_FLOAT_LOCATION_MISMATCH`) | Platform owner; Order Domain Agent (2026-10-07; OI-15) | §4.6; S16, S38, S40 |

---

## 11. Phased delivery (story cut)

| Phase | Contents | Depends on |
| --- | --- | --- |
| 1 — Read and match | Shared chrome and help pattern; Your books (existing endpoints); automatic application of settled payments (AW14, accounting-only); Customer payments (unapplied-payments list + open invoices); home with lanes from existing aged AR/AP, bank accounts and reconciliation status; to-do list v1 (payments, bank lines, reconciliations) | `GET receivable-payments`, `GET customers/{id}/open-invoices`; chart tokens |
| 2 — Counter correctness | Customer required at checkout; CASH house account; partyId non-null; unpaid walk-in sales | Order, CRM, Invoicing & Payments sign-off |
| 3 — Bill approvals | Roles and permissions; `AWAITING_APPROVAL` / `REJECTED`; approval endpoints and limits; SoD guards; Bills to pay review for EDI bills; Approval limits (Bills section) | Security sign-off; OI-2, OI-3 |
| 4 — Cash and drawers | Float account and commands; drawer movement reasons, postings and limits; session policy; petty categories; undeposited sessions and Record bank deposit; cash-position and outlook read models; Approval limits (Drawer cash, Categories) | Phase 2; Order sign-off (R6.1) |
| 5 — Bill intake | Vendor master in pos-supplier and the copies in pos-accounting and pos-order (G15); delivery-reference check (G16); `BillIntakeItem` and `BillIntakePort`; upload, spreadsheet import, read-back and confirm; extraction worker; inbound-mail edge and email-in; vendor statements; EDI adapter through `BillIntakePort`; PO matching (later) | New intake ADR accepted with the ADR-0044 §1, ADR-0049 and ADR-0050 amendments; the G14 defect fix; extraction provider (OI-1); OI-11, OI-14 |
| 6 — Canada | §4.7 items | ADR-0067 Stage A Canadian launch work and the PC-15 readiness sign-off (OP-9); Canadian accountant (OI-4) |

### 11.1 Stories (capability CAP:550, louisburroughs/durion#550)

Cut on 2026-10-05. Each story records its own open points under "Dependencies" and "Spec discrepancy". Three stories were added while cutting:
S35 (report and payment prerequisites the frontend needs) and S37 (pos-accounting provisions each new tenant on `tenant.created`; every seed
binds only the default tenant today). S36 makes pos-inventory name pos-supplier vendors.

| Story | Phase | Title | Issue |
| --- | --- | --- | --- |
| S0 | 0 | Vendor bills: one duplicate rule (vendor, normalised bill number, bill date) enforced by a partial unique index | louisburroughs/durion-positivity-backend#2501 |
| S1 | 1 | Customer payments: unapplied-payments list and a customer's open invoices | louisburroughs/durion-positivity-backend#2502 |
| S2 | 1 | Settled payments apply automatically to the invoice they were taken against | louisburroughs/durion-positivity-backend#2503 |
| S3 | 1 | Accounting roles: ACCOUNTING_CLERK and GENERAL_MANAGER template roles and Phase-1 grants | louisburroughs/durion-positivity-backend#2504 |
| S35 | 1 | Workspace report and payment prerequisites: seeded statement line mappings, an aging split by due state, crediting a payment's remainder, customer names on aged receivables, export without organizationId | louisburroughs/durion-positivity-backend#2524 |
| S37 | 1 | pos-accounting provisions each new tenant: chart of accounts, GL mapping defaults and statement lines on tenant.created | louisburroughs/durion-positivity-backend#2526 |
| S4 | 1 | Accounting workspace shell and home (v1): sub-navigation, help pattern, money lanes and to-do list from existing reads | louisburroughs/durion-positivity-frontend#460 |
| S5 | 1 | Your books: balance summary, who owes what, all entries and the journal-entry page | louisburroughs/durion-positivity-frontend#461 |
| S6 | 1 | Customer payments: match a payment to the invoices it pays (replaces Apply payment) | louisburroughs/durion-positivity-frontend#462 |
| S7 | 2 | CASH house account per tenant in pos-customer | louisburroughs/durion-positivity-backend#2505 |
| S8 | 2 | Checkout requires a customer; walk-in (CASH) only when paid in full | louisburroughs/durion-positivity-backend#2506 |
| S9 | 2 | Invoices and payments refuse a missing customer; PaymentSettledV1.partyId becomes non-null | louisburroughs/durion-positivity-backend#2507 |
| S10 | 2 | Register checkout: pick a customer before tender, with Walk-in for paid-in-full sales | louisburroughs/durion-positivity-frontend#463 |
| S11 | 2 | Unpaid walk-in sales: CASH balance read and day-end needs-attention item | louisburroughs/durion-positivity-backend#2508 |
| S12 | 3 | Vendor-bill approval lifecycle: AWAITING_APPROVAL, approve, reject, and approval fields written only by an approval | louisburroughs/durion-positivity-backend#2509 |
| S13 | 3 | Approval limits and separation of duties for bills | louisburroughs/durion-positivity-backend#2510 |
| S14 | 3 | Bills to pay (EDI and goods-receipt bills) and the Bills section of Approval limits | louisburroughs/durion-positivity-frontend#464 |
| S15 | 4 | Chart of accounts, float and petty-expense categories | louisburroughs/durion-positivity-backend#2511 |
| S16 | 4 | Drawer movements: fixed reasons, session policy (allowed / amount), elevation and the close fact v2 | louisburroughs/durion-positivity-backend#2512 |
| S17 | 4 | Drawer movements post to the ledger; vendor cash on delivery becomes an AP payment | louisburroughs/durion-positivity-backend#2513 |
| S18 | 4 | Bank deposits of drawer cash: undeposited sessions and Record / reverse deposit | louisburroughs/durion-positivity-backend#2514 |
| S19 | 4 | Cash position, 30-day outlook and the to-do (work items) read models | louisburroughs/durion-positivity-backend#2515 |
| S20 | 4 | Home: Your cash, the 30-day outlook, Record bank deposit and the full to-do list | louisburroughs/durion-positivity-frontend#465 |
| S21 | 4 | Approval limits: Drawer cash and Petty-expense categories sections | louisburroughs/durion-positivity-frontend#466 |
| S22 | 4 | Register: drawer cash in / out with fixed reasons and manager elevation | louisburroughs/durion-positivity-frontend#467 |
| S38 | 4 | Register float relocation: fix a wrong location or move a register (AW32) | louisburroughs/durion-positivity-backend#2571 |
| S39 | 4 | Opening bank balances through 3900 with outstanding items (AW35, OI-10) | louisburroughs/durion-positivity-backend#2572 |
| S23 | 5 | pos-supplier vendor master: Vendor, remit-to approval, supplier.vendor.updated and the vendor manifest | louisburroughs/durion-positivity-backend#2516 |
| S24 | 5 | Vendor copies and one vendor key: ext_supplier_vendor in pos-accounting and pos-order, ap_vendor retired, remit-to check at payment | louisburroughs/durion-positivity-backend#2517 |
| S25 | 5 | Bill intake core: BillIntakeItem, BillIntakePort, FileStore and the upload / import / confirm commands | louisburroughs/durion-positivity-backend#2518 |
| S26 | 5 | Extraction worker: an isolated utility that reads untrusted invoice files | louisburroughs/durion-positivity-backend#2519 |
| S27 | 5 | Email-in: the inbound-mail edge and pos-accounting's email-in settings | louisburroughs/durion-positivity-backend#2520 |
| S28 | 5 | Vendor statements: upload, reconcile (posts nothing) and vendor credit notes through intake | louisburroughs/durion-positivity-backend#2521 |
| S29 | 5 | Bills to pay: add bills (upload, spreadsheet import, email-in) and check the read-back | louisburroughs/durion-positivity-frontend#468 |
| S30 | 5 | Vendors: the pos-supplier vendor master pages, remit-to change approval and "Add vendor" | louisburroughs/durion-positivity-frontend#469 |
| S36 | 5 | pos-inventory: purchase suggestions name the pos-supplier vendor (vendor copy and feed mapping) | louisburroughs/durion-positivity-backend#2525 |
| S31 | 6 | pos-tax: Canadian rates and registrations, and the tax-amount plausibility lookup | louisburroughs/durion-positivity-backend#2522 |
| S32 | 6 | Canadian input-tax recovery in pos-accounting | louisburroughs/durion-positivity-backend#2523 |
| S33 | 6 | Canada: recovery columns and the registration panel | louisburroughs/durion-positivity-frontend#470 |
| S34 | all | Accounting workspace: documentation, ADR-0070 amendments and API Artifacts Sync | louisburroughs/durion#552 |

S38 and S39 were added on 2026-10-07 from the AW32 and AW35 rulings. The AW33 terms order is carried by S13 and S19 (comments on
louisburroughs/durion-positivity-backend#2510 and #2515).

Clarifications: C1 vendor-bill posting (OI-2, OI-3) louisburroughs/durion#551 · C2 Canada louisburroughs/durion#553 · extraction provider louisburroughs/durion#549.

---

## 12. Open items and sign-offs

| # | Item | Owner |
| --- | --- | --- |
| OI-1 | **Ownership resolved 2026-10-05 (AW22–AW29).** Remaining: choose the extraction provider (louisburroughs/durion#549) and the EDI provider (confirming AW28); accept ADR-0070 | Platform owner / architecture |
| OI-2 | Reconcile the two vendor-bill GL posting triggers (goods-receipt bills post at creation; EDI bills are meant to post at approval but don't) and confirm their accounts | Accounting Domain Agent — **blocks Phase 3** (louisburroughs/durion#551) |
| OI-3 | Seed or tenant-publish posting rules for `VENDOR_BILL_GL_POSTING` and `AP_PAYMENT_GL_POSTING` (today `NO_RULE_VERSION` without them) | Accounting Domain Agent — **blocks Phase 3** (louisburroughs/durion#551) |
| OI-4 | Canadian accountant confirmation of the 50% meals rule, the $100/$500 thresholds and PST treatment | Platform owner (Phase 6 questions: louisburroughs/durion#553) |
| OI-5 | **Signed off 2026-10-05 (AW31)** by the platform owner for Order, CRM, Invoicing & Payments, Security and Inventory. Still open: reassignment of a finalized invoice to another customer (Invoicing & Payments) | Platform owner |
| OI-6 | **Resolved 2026-10-07 (AW34):** `CASH_SAFETY_CUSHION`'s meaning, unit and write contract (Accounting); the PUT is CONTROLLER-level, gated by `accounting:period:hard_lock` like the rest of `/v1/accounting/configuration`; the warning is the only signal, no notification (platform owner, louisburroughs/durion-positivity-backend#2574) | Platform owner |
| OI-7 | True 2-way / 3-way purchase-order matching (the "Ordered" column) | Accounting + Order |
| OI-8 | Intake path for unidentified customer receipts (bank credit or mailed check) and the `assign-customer` endpoint | Accounting Domain Agent |
| OI-9 | Treatment of FET, tire/environmental fees and core charges on vendor bills | Accounting Domain Agent (must ask the owner) |
| OI-10 | **Resolved 2026-10-07 (AW35):** opening bank balances at go-live through 3900, with outstanding items as their own bank lines (louisburroughs/durion-positivity-backend#2572) | Accounting Domain Agent |
| OI-11 | Whether EDI invoices pass through a read-back draft (the confirming person then becomes the creator under AW6.1) or keep becoming bills directly, created by the system as today | Accounting Domain Agent |
| OI-12 | Where SAT / PAC validation of CFDI lives, for both directions (one connector), when Mexico is scheduled | Accounting + Invoicing & Payments (ADR amendment) |
| OI-13 | Allocating vendor credit notes against open bills (new capability) | Accounting Domain Agent |
| OI-14 | Bank details for electronic vendor payments: where they (or a payment provider's tokens) live, who approves a change, and how the payment instruction carries the approved version so a mismatch is refused; never on Kafka | Accounting + Positivity (Integrations) + Security |
| OI-15 | **Resolved 2026-10-07 (AW36):** no move while a session is OPEN or CLOSING; enforced by both sides (louisburroughs/durion-positivity-backend#2573) | Order |
| OI-16 | **Resolved 2026-10-07:** `SUPPORT` keeps `supplier:vendor:read`, read-only and never bank or payment data (platform owner; louisburroughs/durion-positivity-backend#2575) | Security + Positivity (Integrations) |

---

## 13. Documentation changes

Made with this specification: [`index.md`](index.md) links it; `knowledge-catalog/` regenerated; addition recorded in `knowledge-catalog/log.md`.

With the bill-intake ruling (AW22–AW29), before Phase 5 stories:

- A new ADR, [ADR-0070](../../docs/adr/0070-bill-intake-ownership-and-vendor-master.adr.md) (accepted), recording AW22–AW29, the `FileStore` port and the
  security controls of §7.4.
- ADR-0044 §1: classify the extraction worker (utility) and the inbound-mail edge. *Done 2026-10-05.*
- ADR-0049 amendments (AW22, AW23). §1: "pos-supplier owns the vendor master and all credentialed machine-to-machine supplier connectivity; each connection
  profile belongs to one vendor. Supplier documents arriving any other way (uploaded, imported or emailed) are AP intake owned by pos-accounting;
  pos-supplier neither stores nor republishes them. An inbound vendor channel requires an amendment to this ADR (architecture §12 decision 7)." §2: "the
  vendor master and transmission / exchange state; bills, bank details, AP status, payments and approval stay pos-accounting's." §3: the
  `supplier.vendor.updated` row (consumers pos-accounting, pos-order, pos-inventory), `supplier.manifest.v1`, and `vendorId` on `SupplierInvoiceReceivedV1`. *Done 2026-10-05.*
- ADR-0050 amendment: connection profiles gain a required `vendorId`; YAML names the vendor by `vendorNumber` and never creates one. *Done 2026-10-05.*
- ADR-0049 amendment at v2 for X12 855 and 856 (§4.8).
- Billing documentation, once the ADR is accepted: retire BILL-DEC-013 and the `billing:ap:*` keys and state that AP payments belong to pos-accounting
  (`domains/billing/.business-rules/AGENT_GUIDE.md`, `DOMAIN_NOTES.md`, `STORY_VALIDATION_CHECKLIST.md`); replace the BILL-DEC-008 `billing:*` permission table
  with the registered `invoice:*` keys (`InvoicePermissions.java`); pin `pos-invoice` to `billing` in `MODULE_DOMAIN`
  (`scripts/generate-knowledge-catalog.py`), regenerate the catalog and log it.

To change when the stories land: `pos-accounting/README.md` (endpoints, settings, error codes, events); `.business-rules/ERROR_CODES.md`
(`AP_APPROVAL_LIMIT_EXCEEDED`, `AP_BILL_SELF_APPROVAL`, `AP_BILL_NOT_APPROVABLE`, `AP_PAYMENT_SELF_APPROVED_BILL`, `FLOAT_ALREADY_ESTABLISHED`,
`TAX_AMOUNT_IMPLAUSIBLE`, `PAYMENT_CUSTOMER_ALREADY_ASSIGNED`, `AP_BILL_DUPLICATE`, `BILL_INTAKE_FIELDS_UNCHECKED`, `VENDOR_INACTIVE`, `VENDOR_NOT_FOUND`,
`VENDOR_PAYMENT_DETAILS_CHANGED`; from AW32, AW35 and AW36: `FLOAT_REGISTER_NOT_FOUND`, `FLOAT_RELOCATION_SAME_LOCATION`, `FLOAT_RELOCATION_DATE_INVALID`,
`FLOAT_REGISTER_SESSION_OPEN`, `REGISTER_FLOAT_LOCATION_MISMATCH` (pos-order),
`FLOAT_AMOUNT_NEGATIVE`, `FLOAT_DATE_BEFORE_RELOCATION`, `FLOAT_RELOCATION_NOT_REVERSIBLE`, `FLOAT_REVERSAL_BEFORE_RELOCATION`,
`BANK_OPENING_BALANCE_ALREADY_ESTABLISHED`, `BANK_OPENING_BALANCE_NOT_FIRST`, `BANK_OPENING_BALANCE_ACCOUNT_NOT_ELIGIBLE`, `BANK_OPENING_BALANCE_EMPTY`);
`VendorBillServiceImpl` Javadoc and
comments (PO weight is 5, HIGH is ≥ 70);
`.business-rules/PERMISSION_TAXONOMY.md` (the new keys; remove the unregistered `accounting:ap:approve` placeholder text in favour of
the registered one); `.business-rules/DOMAIN_MODEL.md` (vendor-bill statuses, `BillIntakeItem`, vendor copy); `.business-rules/AGENT_GUIDE.md` (AW decisions
as AD entries; the eligible-invoices
and payments-list endpoints); `domains/order/spec-pos-order-missing-functionality.md` (R11.2, R6.1); `domains/security/` RBAC audit (roles); frontend
`design/source/theme-tokens.md` (chart tokens); `pos-order/README.md` (session policy, reasons).
