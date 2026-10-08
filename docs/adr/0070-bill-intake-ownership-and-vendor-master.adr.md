---
type: ADR
title: 'ADR-0070: Bill Intake Ownership, Inbound Untrusted Files and the Vendor Master'
description: Places every human channel for supplier invoices (upload, photo, spreadsheet, email-in, vendor statements) in pos-accounting behind one bill-creation path, keeps pos-supplier to credentialed machine channels plus the vendor master, and adds an isolated extraction worker and an inbound-mail edge for untrusted input.
status: stable
adr_status: accepted
created: '2026-10-05'
related: [ADR-0020, ADR-0044, ADR-0049, ADR-0050, ADR-0051, ADR-0062, ADR-0064, ADR-0065]
tags: [adr, accounting, billing, positivity, supplier, events, security, rbac]
---
# ADR-0070: Bill Intake Ownership, Inbound Untrusted Files and the Vendor Master

**Status:** ACCEPTED **Date:** 2026-10-05 **Deciders:** Platform Owner, Accounting Domain, Positivity (Integrations) Domain, Invoicing & Payments
Domain, Architecture
**Affected Issues:** louisburroughs/durion#548 (specification ruling), louisburroughs/durion#549 (extraction provider)

> **How to read this.** ✅ **Resolved** marks a decision this ADR makes (TEMPLATE.adr.md sub-decision format); accepted by the Platform Owner on 2026-10-05
> (see Sign-Off). The domain rulings behind it are recorded as AW22–AW29 in
> [SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §4.9, §7.3, §7.4 and §10.

---

## Context

### Current state

- **One intake path.** Supplier invoices reach pos-accounting only through pos-supplier's EDI fetch (`POST /v1/supplier/invoices/{supplierRef}/fetches`), which
  publishes `SupplierInvoiceReceivedV1`; `SupplierInvoiceEventsListener` turns it into a `VendorBill`. Goods-receipt bills are created only when a caller posts
  a goods-received payload to `POST /v1/accounting/vendor-bills`.
- **ADR-0049** makes pos-supplier the single module for vendor wire formats and connections. Its architecture document records "Inbound flows. None." (§12
  decision 7): every exchange is initiated by Durion.
- **ADR-0020** makes pos-documents the platform's document *renderer* (outbound).
- **Duplicates.** The EDI listener looks for an existing bill by (`vendorId`, `billNumber`) only — no invoice date — and `vendor_bill` has no business-key
  unique constraint (`VendorBillRepository.java` l.187, `SupplierInvoiceEventsListener.java` l.205-208, `V1__baseline_accounting.sql` l.1541-1544).
- **Vendor identity is split three ways.** EDI bills set `vendorId` to the pos-supplier connection-profile id; goods-receipt bills use the purchase order's
  `vendor_id`, which the client supplies and nothing validates (`V1__baseline_order.sql` l.298-302); pos-accounting keeps its own `ap_vendor` directory.
  Goods-receipt bills also carry a generated bill number, not the vendor's (`VendorBillServiceImpl.java` l.120-122).

### The problem

The accounting workspace specification adds the channels a small shop actually receives bills through: PDF and photo upload, CSV / XLSX import, a per-tenant
email-in address and monthly vendor statements. Each brings an untrusted file, an unconfirmed reading of it, and a person who becomes the bill's creator for
separation of duties (creator ≠ approver, AW6.1). Nothing says which module owns these, where the files live, who parses them, how email arrives, or which
record says who the vendor is.

### Drivers

- ADR-0044: no domain-to-domain synchronous calls (R1), utilities may be called (R2), reads through local replicas (R3), one owner per fact (R6).
- A lost supplier invoice is a lost debt; a duplicated one is a double payment.
- Parsers of untrusted files must be isolated from the ledger, and extracted values are only suggestions.
- Pre-production: prefer the clean model over migrations and shims.

### Scope

pos-accounting, pos-supplier, pos-order, pos-inventory, pos-invoice, pos-documents, two new utility services (extraction worker, inbound-mail edge), and the
frontend's Bills to pay and vendor pages.

---

## Decision

### 1. Human intake belongs to pos-accounting

**Decision:** ✅ **Resolved** — pos-accounting owns every channel through which a *person* brings a supplier document: upload (PDF, JPG / PNG / HEIC, TIFF),
CSV / XLSX import with column mapping, documents received by email-in, and vendor-statement upload and reconciliation. It owns the read-back, confirmation,
cross-channel duplicate detection, bill creation, matching and approval. These channels enter by authenticated command; the person who confirms the read-back
is the bill's creator. They are never routed through `SupplierInvoiceReceivedV1`.

### 2. pos-supplier keeps credentialed machine channels and owns the vendor master

**Decision:** ✅ **Resolved** — Supplier connectivity means credentialed machine-to-machine exchanges with a vendor or its EDI / API provider (EDI X12 810,
distributor APIs, a CFDI pulled from a supplier API or portal). pos-supplier publishes `SupplierInvoiceReceivedV1` for those only and never stores or
republishes a document a person uploaded, imported or emailed. In addition, pos-supplier owns the **vendor master** (Decision 7).

### 3. One creation path: the `BillIntakeItem` draft and `BillIntakePort`

**Decision:** ✅ **Resolved** — An unconfirmed reading is its own aggregate, `BillIntakeItem`, never a `VendorBillStatus`, so it never reaches aged payables, the
cash outlook or any amount owed. Lifecycle: `QUARANTINED` → `FILE_REJECTED` | `EXTRACTING`; `EXTRACTING` → `READY_FOR_REVIEW`; `READY_FOR_REVIEW` →
`CONFIRMED` | `DISCARDED` (reason ≥ 10 characters). One internal entry point, `BillIntakePort`, is the only code path that creates a `VendorBill` (an ArchUnit
rule guards it). The `SupplierInvoiceReceivedV1` listener becomes an adapter into that port and records the event durably in the consumer transaction,
whether it becomes a draft or a bill. If extraction fails or the provider is down, the draft still reaches `READY_FOR_REVIEW` with every field empty and
marked for checking; a person types the bill in under the same rules, and the bill records `extractionOutcome = MANUAL`. Nothing is created automatically.

### 4. One duplicate rule for every channel

**Decision:** ✅ **Resolved** — A bill is a duplicate when (tenant, vendor, normalised bill number, bill date) matches a bill that is not `VOIDED` or
`REJECTED`; a partial unique index enforces it, so a legitimate re-issue after a void still goes through. Because goods-receipt bills carry a generated
number, an incoming invoice is also checked against them by delivery reference. A duplicate is refused (409 `AP_BILL_DUPLICATE`) with a link to the
original. The index ships first, as a defect fix, ahead of the intake work.

### 5. An isolated extraction worker reads untrusted files

**Decision:** ✅ **Resolved** — A new stateless **extraction worker** (working name; a utility under ADR-0044 §1) reads PDFs, images, spreadsheets and inbound
CFDI XML and returns fields with a per-field confidence and proposed splits for multi-invoice files. It holds no business logic and is the only component
that holds the extraction provider's credentials. pos-accounting invokes it under R2 as an asynchronous job while the draft sits in `EXTRACTING`; it runs
with CPU, memory, time and decompression-ratio limits. The provider is chosen in louisburroughs/durion#549.

*Amended 2026-10-08 (louisburroughs/durion#549; specification AW60–AW66).* The provider is **AWS Textract `AnalyzeExpense`** in the platform's own AWS
account and region; documents never go to a third-party vendor. The worker sends each page in a synchronous request and stages nothing in provider storage,
and the AWS AI-services opt-out policy keeps the documents out of AWS's service improvement. No retention is required too: Security verifies AWS's
retention terms before any provider call is enabled. It reads the tenant's primary language plus English, converts HEIC to
JPEG before sending, and groups pages into invoices to propose splits, since Textract reads each page on its own. Its configuration is an ordered list of
providers, Textract alone in v1, with manual entry as the last resort. pos-accounting meters a monthly reading allowance per tenant (default 20.00 USD) and
sends nothing past it. HEIC conversion, page grouping and who may change the allowance await the platform owner's confirmation (specification OI-25);
French and Spanish coverage is proven on a labelled sample set (OI-24).

### 6. An inbound-mail edge carries email

**Decision:** ✅ **Resolved** — A new **inbound-mail edge** (working name; a utility under ADR-0044 §1) owns mail transport: MX, SPF / DKIM / DMARC alignment,
rate limits, `Message-ID` idempotency and address provisioning. The tenant comes only from the receiving address. It hands file references to
pos-accounting as a command and never creates a bill. It is not a vendor-profile binding, sender allow-lists never come from profiles, and it never replies to
senders (it does not use pos-platform-sender); rejected mail is reported to the shop. pos-accounting owns the tenant-facing settings: address request and
rotation, and per-vendor sender allow-lists.

### 7. The vendor master is pos-supplier's; consumers keep copies

**Decision:** ✅ **Resolved** (Platform Owner, 2026-10-05) — pos-supplier owns one `Vendor` for each party the shop buys from or pays, with or without a
connection: `vendorId` (UUIDv7), `vendorNumber` (ADR-0064 business reference), `legalName`, `displayName`, `taxRegistrations[]`, `remitTo`,
`defaultPaymentTerms`, `defaultCurrency`, `status` (`ACTIVE` / `INACTIVE`, never deleted) and `remitToVersion`.

- Each connection profile belongs to exactly one vendor (ADR-0050 amendment); YAML-managed profiles name it by `vendorNumber` and never create one.
- A remit-to change is published only after a second person approves it (`supplier:vendor_remit:approve`). Other permissions: `supplier:vendor:read`,
  `supplier:vendor:write`.
- The fact `supplier.vendor.updated` (v1, on `supplier.events.v1`, keyed by `vendorId`) carries every vendor field; deactivation is `status = INACTIVE`.
  pos-supplier publishes per-tenant manifests on `supplier.manifest.v1` and re-sends on request (ADR-0044 §4).
- pos-accounting, pos-order and pos-inventory keep `ext_supplier_vendor` copies under R3. The copies hold **every** vendor
  with its status, because open bills and history still name deactivated vendors; only active vendors accept new bills, payments and purchase orders.
- `VendorBill.vendorId`, the AP-payment vendor id and the purchase-order vendor id are the pos-supplier vendor id. `SupplierInvoiceReceivedV1` gains a nullable
  `vendorId` that pos-supplier always sets. pos-order validates a new purchase order's vendor against its copy. pos-accounting's `ap_vendor` directory is
  retired; AP-only settings (default expense mapping, AP hold, 1099 / T4A flag) stay pos-accounting's in `ap_vendor_settings`, keyed by `vendorId`.
- A bill stores `approvedRemitToVersion` at approval; paying it while the vendor's current version differs is refused (409 `VENDOR_PAYMENT_DETAILS_CHANGED`)
  until an approver other than the payer confirms the change.
- Bank details for electronic payment are not part of this decision: they never travel on Kafka, and where they (or provider tokens) live is decided when
  electronic vendor payments are introduced (specification OI-14).

### 8. Source files stay in pos-accounting behind a `FileStore` port

**Decision:** ✅ **Resolved** — In v1 the source-file bytes stay in pos-accounting (the bank-statement file pattern) behind an internal `FileStore` port shaped
like a vault: opaque file id, tenant, sha256, content type, retention-until, legal-hold flag, purge. Object storage can sit behind it. Files are stored only
after validation and scanning pass, are served only through pos-accounting's authorized endpoint, and carry retention purge and legal hold from the first
release. Retention runs from the day the bill closes (fully paid, voided or rejected), so it cannot expire while anything is owed. pos-supplier's exchange
audit keeps its own 400-day policy (ADR-0050) outside this store. The second module that needs to keep source files (for example outbound CFDI XML in
pos-invoice) does not copy this pattern: its arrival triggers a platform file-vault utility, and pos-accounting's files move into it.

### 9. Every inbound file is untrusted

**Decision:** ✅ **Resolved** — The ten controls of the specification §7.4 are normative: quarantine first; type allow-list by magic bytes with size, page and
content limits (no encrypted PDFs, scripts, launch actions, embedded files or macros; CSV formula neutralisation on re-export); malware scanning before any
parser; parser isolation (Decision 5); a server-rendered or sandboxed preview, with extracted text rendered as text (ADR-0065); `tenant_id` under row-level
security on storage keys and rows (ADR-0062) and served only through an authorized endpoint; the email-in rules of Decision 6; retention and audited deletion
(Decision 8); an audit of upload, scan, extraction, every view, download and deletion; and no auto-trust: nothing posts or becomes owed until a person confirms
and the bill is approved.

### 10. Permissions

**Decision:** ✅ **Resolved** — New: `accounting:bill_intake:create` (upload, import, statement upload, edit a draft, confirm, discard, retry);
`accounting:bill_intake:manage` (email-in address, sender allow-lists, mapping templates, retention settings); `accounting:vendor_statement:reconcile`
(reconciling posts nothing); `accounting:bill_source_file:delete` (ADMIN; only a discarded draft's file or after retention expiry; never while the bill has an
open amount or is on legal hold; justification ≥ 10 characters; audited); and the `supplier:vendor:*` keys of Decision 7. Existing `accounting:ap:view`
previews and downloads source files and never grants `supplier:audit:read`, which opening the raw EDI payload behind an `exchangeId` requires. The word
"invoice" stays out of accounting paths and keys: `invoice:*` belongs to pos-invoice, and no `supplier:invoice:*` key covers documents people upload or type in.

### 11. CFDI in both directions

**Decision:** ✅ **Resolved** — An inbound CFDI (supplier → shop) is an intake format: parsed in the extraction worker, booked in pos-accounting. An outbound
CFDI (shop → customer) is pos-invoice's: PAC stamping, cancellation and the folio fiscal are part of invoice issuance; pos-tax computes tax; pos-documents
renders the PDF. A PAC or the SAT is a tax-authority integration, not a supplier, so ADR-0049 does not place it in pos-supplier. The two directions may share
only a non-deployed CFDI schema library. One amendment, written when Mexico is scheduled, places SAT validation for both directions in a single connector.

### 12. EDI invoices are pulled

**Decision:** ✅ **Resolved** (Platform Owner confirms when choosing the EDI provider) — pos-supplier polls the provider's mailbox or API per binding, with an
overlapping checkpoint, keyed for idempotency on control number plus document identity; architecture decision 7 stands. Push would need its own amendment to
ADR-0049, a dedicated ingress outside the JWT gateway, mTLS or HMAC authentication, replay protection, a 2xx only after the payload is durable, the tenant
taken from the connection, and a pull backfill.

### 13. pos-invoice and pos-documents own no intake

**Decision:** ✅ **Resolved** — Neither module owns any part of bill intake. pos-documents hosts no parsing or storage of inbound files; ADR-0020 covers
outbound rendering only.

---

## Alternatives Considered

1. **Intake in pos-supplier.** Rejected: ADR-0049 and decision 7 make pos-supplier the credentialed, Durion-initiated channel to vendors. A person's upload is
   not a vendor channel, its creator matters for approval (AW6.1), and republishing it as a supplier fact would give one fact two owners (R6).
2. **An unconfirmed upload as a `VendorBill` status (`DRAFT`).** Rejected: drafts would leak into aging, the outlook and AP totals unless every query excluded
   them, and the bill state machine would carry states that are about files, not debts.
3. **Parsing inside pos-accounting.** Rejected: untrusted parsers and provider credentials would run in the ledger's process.
4. **Email-in through pos-supplier or pos-platform-sender.** Rejected: pos-supplier is the credentialed outbound channel; pos-platform-sender sends mail and
   must not become a receiver.
5. **The vendor master in pos-accounting** (an accounts-payable "payee" with profile links), proposed by the domain agents. Rejected by the Platform Owner:
   purchasing, receiving and paying should name one vendor record, and pos-supplier already knows vendors through their connections.
6. **A platform file vault now.** Rejected for v1: one consumer; the vault-shaped port keeps the later move cheap.
7. **EDI push.** Deferred: it reverses decision 7 and needs the ingress and controls listed in Decision 12.

---

## Consequences

### Positive ✅

- Every bill, whatever its channel, passes one creation path, one duplicate rule and one approval flow.
- Unconfirmed drafts can never inflate what the shop appears to owe.
- Untrusted parsing and provider credentials are isolated from the ledger.
- One vendor id across purchase orders, receiving, bills and payments; aged payables by vendor become correct.
- The remit-to fraud vector is covered twice: a second person at change time, a version check at payment time.

### Negative ⚠️

- Two new deployable utilities to build, secure and operate (mitigated: both are stateless and narrow).
- pos-supplier grows from a connection module into the vendor master, with a manifest and re-send to build (no vendor manifest exists today).
- Replicas lag the master; a bill whose vendor was just created waits until the fact arrives (mitigated: the draft re-resolves on its own).
- Bank details for electronic payment remain undecided (OI-14).

### Neutral

- Existing alpha bills are reseeded rather than migrated to the new vendor key (pre-production policy).
- The extraction provider is a separate decision (louisburroughs/durion#549, made 2026-10-08: AWS Textract); without it, intake still works by manual entry.

---

## Implementation Notes

- **Order:** the duplicate index (Decision 4) first, as a defect fix; then the vendor master and copies (Decision 7); then `BillIntakeItem`, `BillIntakePort`,
  `FileStore` and the human channels; the extraction worker after louisburroughs/durion#549; the inbound-mail edge; vendor statements.
- **Error codes:** `AP_BILL_DUPLICATE`, `BILL_INTAKE_FIELDS_UNCHECKED`, `VENDOR_INACTIVE`, `VENDOR_NOT_FOUND`, `VENDOR_PAYMENT_DETAILS_CHANGED`.
- **Testing:** an ArchUnit rule that only `BillIntakePort` creates a `VendorBill`; module tests for each lifecycle branch, the duplicate index (including a
  re-issue after a void), the payment-time remit-to check and the durable EDI record; security tests for the §7.4 controls.
- **API Artifacts Sync** after each controller or permission change.

### Changes required in other ADRs

Applied 2026-10-05 as dated amendments in each ADR, except the v2 X12 855 / 856 rows, which wait for v2.

- **ADR-0044 §1:** add the extraction worker and the inbound-mail edge to the Utility class.
- **ADR-0049 §1:** "pos-supplier owns the vendor master and all credentialed machine-to-machine supplier connectivity; each connection profile belongs to one
  vendor. Supplier documents arriving any other way (uploaded, imported or emailed) are AP intake owned by pos-accounting (ADR-0070); pos-supplier neither
  stores nor republishes them. An inbound vendor channel requires an amendment to this ADR (architecture §12 decision 7)."
- **ADR-0049 §2:** "the vendor master and transmission / exchange state; bills, bank details, AP status, payments and approval stay pos-accounting's."
- **ADR-0049 §3:** add `supplier.vendor.updated` (consumers pos-accounting, pos-order, pos-inventory), `supplier.manifest.v1`, and `vendorId` on
  `SupplierInvoiceReceivedV1`. At v2, X12 855 and 856 get their own rows (856 consumed by pos-inventory; 855 mapped to `supplier.order.confirmed` only when a
  transmission intent exists).
- **ADR-0050:** connection profiles gain a required `vendorId`.

---

## References

- **Related Issues:** louisburroughs/durion#548, louisburroughs/durion#549
- **Related ADRs:** [ADR-0020](0020-documents-centralized-creation.adr.md), [ADR-0044](0044-platform-event-only-domain-walls.adr.md),
  [ADR-0049](0049-supplier-integration-module-boundary.adr.md), [ADR-0050](0050-supplier-vendor-profile-configuration.adr.md),
  [ADR-0051](0051-supplier-protocol-adapter-versioning.adr.md), [ADR-0062](0062-postgres-row-level-multitenancy.adr.md),
  [ADR-0064](0064-frontend-read-outcome-and-placeholder-copy-policy.adr.md), [ADR-0065](0065-frontend-untrusted-content-and-browser-persistence-policy.adr.md)
- **Related Documentation:** [SPEC-accounting-workspace.md](../../domains/accounting/SPEC-accounting-workspace.md) §4.8, §4.9, §7.3, §7.4;
  [SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md](../architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md) §12

---

## Sign-Off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Platform Owner | Louis Burroughs | 2026-10-05 | Accepted as written; vendor master placement decided 2026-10-05 |
| Accounting Domain | Accounting Domain Agent | 2026-10-05 | Ruling AW22–AW29 |
| Positivity (Integrations) Domain | Integrations Domain Agent | 2026-10-05 | Ruling AW22–AW29; vendor master detail |
| Invoicing & Payments Domain | Invoicing & Payments Domain Agent | 2026-10-05 | Ruling AW22, AW29 |

---

## Timeline

- **Proposed**: 2026-10-05
- **Accepted**: 2026-10-05

---

## Changelog

- **2026-10-05**: Initial draft from the OI-1 ruling and the Platform Owner's vendor-master decision.
- **2026-10-05**: Accepted by the Platform Owner.
- **2026-10-05**: ADR-0044 §1, ADR-0049 §1–§3 and ADR-0050 §2 / §6 amended as listed under "Changes required in other ADRs".
- **2026-10-08**: Decision 5 amended with the extraction provider and its terms chosen by the Platform Owner (louisburroughs/durion#549; specification
  AW60–AW66).
