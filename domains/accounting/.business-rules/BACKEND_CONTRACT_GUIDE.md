---
title: Accounting Backend Contract Guide
domain: accounting
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-accounting/openapi.yaml
openapi_commit: 3ed498c
last_verified_utc: 2026-07-18T19:29:21Z
last_updated: 2026-07-18
api_reference_generated: domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Accounting Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Accounting domain behavior.

- Use this guide for capability intent, domain invariants, ADR constraints, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-accounting/openapi.yaml`
- Generated API reference: `domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/accounting/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Implement behavior in conformance with listed ADR constraints.
3. Validate operation-level request/response details in OpenAPI or generated API reference.
4. Verify behavior against mapped contract/provider tests.

Frontend developer workflow:

1. Start with `Frontend API Lookup` table.
2. Identify the target `operationId` for the UI action.
3. Open generated API reference for exact payloads and response codes.
4. Apply error handling and header requirements listed in this guide.

## Domain Invariants

These are normative behavior rules for Accounting and are not replaced by OpenAPI schema.

- Payment receipt does not reduce AR until application records are created (`AD-002`).
- Overpayment becomes customer credit deterministically (`AD-003`).
- Apply payment flow must be idempotent (`AD-010`) and atomic.
- Posting is allowed only in open accounting periods (`AD-012`, ENFORCED as of Wave 2 B2 backend#944):
  the period gate covers every posting and reversal path in order hard lock > closed > override.
  Dates before the org-level hard-lock date are never postable; CLOSED periods accept postings only
  with `accounting:period:override` plus a non-blank, audit-logged justification.
- Posting references are canonical and traceable (`AD-011`).
- Event ingestion status and idempotency outcomes are backend-authoritative (`AD-007`).
- Refund execution is external to Accounting; Accounting exposes read model views (`AD-001`).
- Correlation and trace propagation follow domain standard (`AD-008`).

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-050 | `durion#50` | draft | Chart of Accounts, posting categories, mapping keys |
| CAP-051 | `durion#51` | draft | Journal entries and ledger posting workflow |
| CAP-052 | `durion#52` | stable-for-ui | Accounts receivable and payment application |
| CAP-053 | `durion#53` | stable-for-ui | Accounts payable bills and vendor payment |
| CAP-054 | `durion#54` | stable-for-ui | Period close, adjustments, and financial reporting |
| CAP-055 | `durion#55` | draft | Reconciliation, audit, and event controls |
| CAP-251 | `durion#251` | draft | Invoice payment status sync and POS accounting reconciliation |
| CAP-278 | `durion#278` | draft | Posting rule engine and reprocessing orchestration |

## Implementation Links / Backlog

- Backend child issues for CAP-251:
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/5>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/6>

These links are the authoritative backlog items that implement CAP-251 behavior. Use them for traceability and to resolve TODOs in this guide.

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| List GL accounts | `listGLAccounts` | GET | `http://localhost:8080/v1/accounting/gl-accounts` | Use for account maintenance tables |
| Create GL account | `createGLAccount` | POST | `http://localhost:8080/v1/accounting/gl-accounts` | Enforce unique account codes |
| Manage posting categories | `listPostingCategories` | GET | `http://localhost:8080/v1/accounting/posting-categories` | Pair with create/update/deactivate actions |
| Resolve posting mapping | `resolveGLMapping` | POST | `http://localhost:8080/v1/accounting/mappings/resolve` | Used by posting config UX and diagnostics |
| List journal entries | `listJournalEntries` | GET | `http://localhost:8080/v1/accounting/journal-entries` | Supports `entryNumber` filter (Wave 2 A2) |
| Create journal entry | `createJournalEntry` | POST | `http://localhost:8080/v1/accounting/journal-entries` | Draft creation before post/reverse |
| Post journal entry | `postJournalEntry` | POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/post` | Assigns `entryNumber`; period gate applies (422 `PERIOD_CLOSED`/`PERIOD_HARD_LOCKED`); optional body `overrideJustification` with `accounting:period:override` |
| Reverse journal entry | `reverseJournalEntry` | POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/reverse` | Mandatory `reason`; optional `reversalDate` (defaults: original date if OPEN, else today); 409 `JE_ALREADY_REVERSED`/`JE_NOT_POSTED`; period gate applies |
| Apply payment to invoices | `applyPayment` | POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` | Requires idempotency behavior (`AD-010`) |
| Reverse payment application | `reversePaymentApplication` | POST | `http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse` | Audit trail required |
| Create credit memo | `createCreditMemo` | POST | `http://localhost:8080/v1/accounting/credit-memos` | Use when negative adjustments would occur |
| List AP bills | `listApBills` | GET | `http://localhost:8080/v1/accounting/ap/bills` | AP workflow entry point |
| Execute AP payment | `executePayment` | POST | `http://localhost:8080/v1/accounting/ap/payments` | Track gateway/GL status transitions |
| Review ingestion queue | `listAccountingEvents` | GET | `http://localhost:8080/v1/accounting/events` | Operational monitoring and reconciliation |
| Reprocess suspended event | `reprocessSuspendedEvent` | POST | `http://localhost:8080/v1/accounting/events/{eventId}/reprocess` | Requires strict permission gating |
| View income statement | `generateIncomeStatement` | GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` | Reporting workflow |
| View balance sheet | `generateBalanceSheet` | GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` | Reporting workflow |
| List accounting periods | `listAccountingPeriods` | GET | `http://localhost:8080/v1/accounting/periods` | Requires `accounting:period:view` |
| Close accounting period | `closeAccountingPeriod` | POST | `http://localhost:8080/v1/accounting/periods/{periodCode}/close` | Requires `accounting:period:close`; 422 lists blocking DRAFT entries |
| Reopen accounting period | `reopenAccountingPeriod` | POST | `http://localhost:8080/v1/accounting/periods/{periodCode}/reopen` | Requires `accounting:period:reopen`; mandatory justification |
| View hard-lock date | `getAccountingHardLockDate` | GET | `http://localhost:8080/v1/accounting/periods/hard-lock` | Requires `accounting:period:view`; `hardLockDate: null` when unset |
| Set hard-lock date | `setAccountingHardLockDate` | PUT | `http://localhost:8080/v1/accounting/periods/hard-lock` | Requires `accounting:period:hard_lock`; mandatory justification; forward-only (422 `HARD_LOCK_DATE_REGRESSION`) |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Use `Authorization` and required authorities per endpoint policy.
- Use idempotency key behavior for mutation flows where required by capability rules.

## Capability Sections

## CAP-050: Maintain Chart of Accounts and Posting Categories

### Capability Metadata

- Capability ID: CAP-050
- Parent Issue: <https://github.com/louisburroughs/durion/issues/50>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List/create/manage GL accounts | `listGLAccounts`, `createGLAccount`, `updateGLAccount`, `deactivateGLAccount` | GET/POST/PUT/POST | `http://localhost:8080/v1/accounting/gl-accounts...` |
| List/create/manage posting categories | `listPostingCategories`, `createPostingCategory`, `updatePostingCategory`, `deactivatePostingCategory` | GET/POST/PUT/POST | `http://localhost:8080/v1/accounting/posting-categories...` |
| Manage mapping keys | `createMappingKey`, `updateMappingKey`, `deactivateMappingKey` | POST/PUT/POST | `http://localhost:8080/v1/accounting/mapping-keys...` |

### Behavioral Assertions

- `categoryCode` and mapping key uniqueness must be enforced.
- Deactivation is preferred over hard delete for auditable entities.
- Mapping resolution must remain deterministic for a given key/context.
- GL accounts carry `reconcilable` (boolean, default `false`) and an optional `accountSubtype`
  enum: `RECEIVABLE`, `PAYABLE`, `BANK_CASH`, `UNDEPOSITED_FUNDS`, `TAX_PAYABLE`, `CURRENT_ASSET`,
  `FIXED_ASSET`, `CURRENT_LIABILITY`, `SALES`, `COST_OF_SALES`, `OPERATING_EXPENSE`, `OTHER`.
  Both fields are exposed on COA list/detail responses.
- GL mapping creation performs a non-blocking subtype plausibility check: an implausible
  posting-category/subtype pairing produces a warning, never a request failure.

### Frontend Usage Notes

- COA and posting-category screens should use list endpoints as primary table sources.
- Provide explicit conflict messaging for uniqueness violations.

### ADR Constraints

- `AD-011` posting reference canonicalization impacts downstream posting traceability.
- `AD-013` permission gating applies to all mutating admin/configuration operations.

### Events & Dependencies

- Depends on posting rule services and GL mapping resolver.
- Configuration changes influence journal posting behavior in CAP-051/CAP-278 workflows.

### Contract Test Traceability

- Provider tests: `GLAccountContractBehaviorIT`, `PostingCategoryContractBehaviorIT`, `MappingKeyContractBehaviorIT`, `PostingCategoryMappingKeyContractBehaviorIT`
- Service tests: `MappingKeyServiceTest`, `GLMappingResolverTest`, `DefaultGLMappingServiceTest`

## CAP-051: Post Journal Entries to the General Ledger

### Capability Metadata

- Capability ID: CAP-051
- Parent Issue: <https://github.com/louisburroughs/durion/issues/51>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create/list/get journal entries | `createJournalEntry`, `listJournalEntries`, `getJournalEntry` | POST/GET/GET | `http://localhost:8080/v1/accounting/journal-entries...` |
| Post/reverse journal entries | `postJournalEntry`, `reverseJournalEntry` | POST/POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/...` |
| Trace journal lineage | `getJournalTraceability` | GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` |

### Behavioral Assertions

- Journal entries must be balanced before posting.
- Posting in closed periods must be rejected (see period-gate assertions below).
- Reversal must preserve traceability to original posting.
- Posted entries carry `entryNumber` in format `JE-{YYYYMM}-{seq}` (month from `transactionDate`),
  assigned at POST time only inside the posting transaction (Wave 2 A2 backend#942, decision `D-1`).
  DRAFT/PENDING and pre-migration entries are unnumbered (`entryNumber: null`). Gapless as a
  side effect of post-time assignment; **no statutory gapless guarantee is claimed**.
- `entryNumber` is exposed on journal-entry responses and as an exact-match filter on `listJournalEntries`.
- Reversal contract (Wave 2 A3 backend#943): `reverseJournalEntry` creates and immediately posts an
  inverse entry (own `entryNumber`) and transitions the original POSTED → REVERSED race-safely
  (a lost concurrent-reversal race returns 409). Bidirectional linkage plus `reversedAt` and acting
  user are recorded; a non-blank `reason` is mandatory.
- `reversalDate` is optional: it defaults to the original entry's transaction date if that period is
  OPEN, otherwise to today; the resolved date must pass the period gate.
- Reversal error codes: `JE_ALREADY_REVERSED` (409), `JE_NOT_POSTED` (409, DRAFT/PENDING entries),
  `PERIOD_CLOSED` (422), `PERIOD_HARD_LOCKED` (422).
- Period gate (Wave 2 B2 backend#944, `AD-012` now ENFORCED): both `postJournalEntry` and
  `reverseJournalEntry` check hard lock > closed > override. A date in a CLOSED period is accepted
  only when the caller holds `accounting:period:override` and supplies a non-blank
  `overrideJustification` in the request body; the override is audit-logged. A date strictly before
  the hard-lock date is never postable — no override path.
- Reversal emits a `JournalEntryReversed` outbox domain event in the same transaction for downstream
  read models; API-level events `ACCOUNTING_JOURNAL_ENTRY_POST` / `ACCOUNTING_JOURNAL_ENTRY_REVERSE`
  are registered.

### Frontend Usage Notes

- Posting UI should surface period-closed and unbalanced-entry failures explicitly, and distinguish
  `PERIOD_CLOSED` (recoverable via override or reopen) from `PERIOD_HARD_LOCKED` (terminal).
- Offer the override-justification input only to callers holding `accounting:period:override`.
- Traceability view should expose source event and posting reference links; entry lists should show
  `entryNumber` (blank for unnumbered DRAFT/legacy rows) and support lookup by it.
- Reversal UX must collect a mandatory reason and treat `reversalDate` as optional with
  backend-side defaulting.

### ADR Constraints

- `AD-011` posting references must be canonical and queryable.
- `AD-012` accounting period enforcement is mandatory — ENFORCED across all posting/reversal paths
  as of Wave 2 B2 (backend#944).

### Events & Dependencies

- Integrates with posting rules and GL posting services.
- Consumes accounting events from ingestion workflows.

### Contract Test Traceability

- Provider tests: `JournalEntryContractBehaviorIT`
- Service tests: `JournalEntryServiceTest`, `GLPostingServiceTest`

## CAP-052: Accounts Receivable (Invoice -> Cash Application)

### Capability Metadata

- Capability ID: CAP-052
- Parent Issue: <https://github.com/louisburroughs/durion/issues/52>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Apply payment to invoices | `applyPayment` | POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` |
| Reverse payment application | `reversePaymentApplication` | POST | `http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse` |
| Reverse/void payment | `reversePayment`, `voidPayment` | POST/POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/...` |
| View invoice status | `getInvoiceStatus` | GET | `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status` | Payment-status endpoint (`/payments/{paymentId}/status`) is not in OpenAPI — use `getPayment`/`getPaymentByRef` |
| Credit memo support | `createCreditMemo`, `listCreditMemos`, `getCreditMemo` | POST/GET/GET | `http://localhost:8080/v1/accounting/credit-memos...` |

### Behavioral Assertions

- AR is reduced only by application records, not raw payment receipt (`AD-002`).
- Overpayment must create customer credit deterministically (`AD-003`).
- Apply command must be idempotent and not double-apply (`AD-010`).
- Concurrent applications against the same payment are serialized via optimistic locking:
  the backend retries a conflicting apply exactly once with fresh state and full revalidation
  (`AD-010` preserved); a second consecutive conflict returns `409` and the caller should retry.
  Unapplied amount never goes negative under concurrency.

### Frontend Usage Notes

- Payment application UX must show unapplied amount and resulting invoice balances.
- Reversal actions should require explicit operator confirmation and audit reason.

### ADR Constraints

- `AD-002`, `AD-003`, `AD-010` are mandatory for AR behavior.
- `AD-013` permission gating applies to apply/reverse endpoints.

### Events & Dependencies

- Consumes payment-domain events and invoice status data.
- Produces accounting-side payment application artifacts and credits.

### Contract Test Traceability

- Provider tests: `InvoicePaymentContractBehaviorIT`, `CreditMemoContractBehaviorIT`
- Service tests: `PaymentApplicationServiceTest`, `CreditMemoServiceTest`, `InvoicePaymentStatusServiceTest`

## CAP-053: Accounts Payable (Bill -> Payment)

### Capability Metadata

- Capability ID: CAP-053
- Parent Issue: <https://github.com/louisburroughs/durion/issues/53>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create and query vendor bills | `createBillFromGoodsReceivedEvent`, `getBillById`, `getBillByOriginEventId` | POST/GET/GET | `http://localhost:8080/v1/accounting/vendor-bills...` |
| Bill matching workflow | `listMatchCandidates`, `selectMatchCandidate`, `matchVendorInvoice`, `resolveMatchException` | GET/POST/POST/POST | `http://localhost:8080/v1/accounting/vendor-bills/...` |
| AP payment workflow | `listApBills`, `executePayment`, `getPayment`, `getPaymentByRef` | GET/POST/GET/GET | `http://localhost:8080/v1/accounting/ap/...` |

### Behavioral Assertions

- Matching and exception resolution must preserve traceability to source events.
- Payment execution status transitions must be explicit and queryable.
- AP flows must remain auditable and recoverable.

### Frontend Usage Notes

- AP screens should surface event linkage (`originEventId`) and match state.
- Payment execution UI should poll/read final gateway plus GL posting status.

### ADR Constraints

- `AD-007` ingestion/read-model visibility applies to AP event-driven bill creation.
- `AD-011` posting references must connect AP payment to ledger artifacts.

### Events & Dependencies

- Depends on upstream Purchasing/Receiving and Payment/Treasury integrations.
- Uses posting engine and ingestion status workflows for financial consistency.

### Contract Test Traceability

- Provider tests: `APPaymentContractBehaviorIT`
- Service tests: `VendorBillServiceGLPostingTest`
- Controller tests: `APPaymentControllerTest`

## CAP-054: Period Close, Adjustments, and Reporting

### Capability Metadata

- Capability ID: CAP-054
- Parent Issue: <https://github.com/louisburroughs/durion/issues/54>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Income statement | `generateIncomeStatement` | GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` |
| Balance sheet | `generateBalanceSheet` | GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` |
| Financial drilldowns | `drilldownToAccounts`, `drilldownToJournalLines` | GET/GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/...` |
| Audit trail adjustments | `recordPriceOverride`, `recordRefund`, `recordCancellation` | POST/POST/POST | `http://localhost:8080/v1/accounting/audit/...` |
| List accounting periods | `listAccountingPeriods` | GET | `http://localhost:8080/v1/accounting/periods` |
| Close accounting period | `closeAccountingPeriod` | POST | `http://localhost:8080/v1/accounting/periods/{periodCode}/close` |
| Reopen accounting period | `reopenAccountingPeriod` | POST | `http://localhost:8080/v1/accounting/periods/{periodCode}/reopen` |
| View hard-lock date | `getAccountingHardLockDate` | GET | `http://localhost:8080/v1/accounting/periods/hard-lock` |
| Set hard-lock date | `setAccountingHardLockDate` | PUT | `http://localhost:8080/v1/accounting/periods/hard-lock` |

### Behavioral Assertions

- Reporting endpoints must reflect posted ledger state.
- Adjustments must leave immutable audit evidence.
- Closed-period constraints must be enforced where mutation is requested.
- Accounting periods follow a monthly cadence keyed by `YYYY-MM` period code (`D-7`) with a
  two-state OPEN → CLOSED lifecycle (no soft-close state in v1).
- A missing period row means OPEN; periods are auto-provisioned on first posting into a new month.
- Close is rejected with `422 PERIOD_HAS_DRAFT_ENTRIES` listing the DRAFT journal-entry IDs still
  in the period; reopen requires a mandatory justification recorded in the audit trail.
- Period error codes: `PERIOD_NOT_FOUND` (404), `PERIOD_ALREADY_CLOSED` (409),
  `PERIOD_ALREADY_OPEN` (409), `PERIOD_HAS_DRAFT_ENTRIES` (422), `PERIOD_CLOSED` (422),
  `PERIOD_HARD_LOCKED` (422), `HARD_LOCK_DATE_REGRESSION` (422).
- Permission gating: `accounting:period:view` / `accounting:period:close` / `accounting:period:reopen`
  / `accounting:period:hard_lock` (set the hard-lock date) / `accounting:period:override`
  (post/reverse into a CLOSED period with justification).
- `AD-012` note: ENFORCED as of Wave-2 story B2 (durion-positivity-backend#944). The
  `AccountingPeriodGate` is the single choke point on `postJournalEntry` and `reverseJournalEntry`,
  covering every posting path (manual, posting engine, credit memo, payment application, AP
  transitively). Check order: hard lock > closed > override.
- Hard lock (backend#944): a single org-level hard-lock date; journal entries dated strictly before
  it are permanently rejected with `422 PERIOD_HARD_LOCKED` and **no override path**. The date is
  monotonic-forward-only (backward move → `422 HARD_LOCK_DATE_REGRESSION`); setting it requires a
  mandatory justification and is audit-logged. `GET /periods/hard-lock` returns `hardLockDate: null`
  when unset. Events: `ACCOUNTING_PERIOD_HARD_LOCK_VIEW` / `ACCOUNTING_PERIOD_HARD_LOCK_SET`.
- Closed-period override: `accounting:period:override` plus a non-blank `overrideJustification`
  allows posting/reversing into a CLOSED period; the override is audit-logged
  (`PERIOD_OVERRIDE_POST`). Without it the request fails `422 PERIOD_CLOSED`.
- Posting engine: autoPost events blocked by a closed period become SUSPENDED with
  `failureReasonCode=PERIOD_CLOSED`; the auto-retry loop skips them, and they are reprocessable
  after the period is reopened. Reprocess-with-override is a recorded follow-up (not in Wave 2).

### Frontend Usage Notes

- Reporting UI should treat drilldown endpoints as detail navigation from statement lines.
- Audit trail views should include correlation and actor filtering paths.

### ADR Constraints

- `AD-012` period enforcement governs post-period behavior.
- `AD-008` correlation and trace standards apply to reporting and audit workflows.

### Events & Dependencies

- Consumes journal and ledger data produced by posting workflows.
- Depends on audit event records for adjustment transparency.

### Contract Test Traceability

- Provider tests: `FinancialReportingContractBehaviorIT`, `AuditTrailContractBehaviorIT`
- Controller tests: `FinancialReportingControllerTest`
- Service tests: `AccountingPeriodServiceTest`, `AuditTrailServiceTest`

## CAP-055: Reconciliation, Audit, and Controls

### Capability Metadata

- Capability ID: CAP-055
- Parent Issue: <https://github.com/louisburroughs/durion/issues/55>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List/query event ingestion records | `listAccountingEvents`, `getEvent`, `getEventProcessingLog` | GET/GET/GET | `http://localhost:8080/v1/accounting/events...` |
| Retry/reprocess failed events | `retryEventProcessing`, `reprocessSuspendedEvent`, `getReprocessingHistory` | POST/POST/GET | `http://localhost:8080/v1/accounting/events/{eventId}/...` |
| Audit trail lookup | `getByActor`, `getByInvoiceId`, `getByOrderId`, `getByDateRange`, `getByType` | GET | `http://localhost:8080/v1/accounting/audit/...` |

### Behavioral Assertions

- Event processing status and idempotency outcome are backend-authoritative.
- Reprocessing attempts must maintain immutable attempt history.
- Manual retry/reprocess actions require explicit authorization.

### Frontend Usage Notes

- Ops dashboards should use event list plus processing log endpoints together.
- Reprocess UI must show prior attempt history and current status transitions.

### ADR Constraints

- `AD-007` ingestion read model controls workflow semantics.
- `AD-013` permission gating model applies to retry/reprocess actions.
- `AD-014` async retry semantics govern eventual outcomes.

### Events & Dependencies

- Consumes canonical accounting events from message infrastructure.
- Produces operational status data used by reconciliation and support tooling.

### Contract Test Traceability

- Provider tests: `EventIngestionContractBehaviorIT`, `SuspenseQueueContractBehaviorIT`, `AuditTrailContractBehaviorIT`
- Service tests: `EventIngestionServiceTest`, `IdempotencyServiceTest`

## CAP-251: Invoice Payment Status Sync (Accounting Coordination)

### Capability Metadata

- Capability ID: CAP-251
- Parent Issue: <https://github.com/louisburroughs/durion/issues/251>
- Backend Child Issues:
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/6> (Story #6: Update Invoice Payment Status from Payment Outcomes)
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/5> (Story #5: Reconcile POS Status with Accounting Authoritative Status)
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Apply payment to invoice | `applyPayment` | POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` |
| View invoice accounting status | `getInvoiceStatus` | GET | `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status` |
| List/query ingestion events | `listAccountingEvents` | GET | `http://localhost:8080/v1/accounting/events` |

### Story #6 — Update Invoice Payment Status from Payment Outcomes

#### Behavioral Assertions

- Payment outcomes must map deterministically to canonical invoice payment statuses:
  `Paid`, `PartiallyPaid`, `Unpaid`, `Failed`, `Chargeback` using minor-unit arithmetic.
- Idempotency keys on `transactionId` first; `applicationRequestId`/`idempotencyKey` fallback.
  Duplicate `transactionId` is a strict no-op — no state mutation and no event emission.
- Full payment: `outstandingAmountMinor` reaches zero → `invoiceStatus=Paid`,
  `InvoicePaymentRecorded` event emitted, posting intent created in outbox.
- Partial payment: `invoiceStatus=PartiallyPaid`, `paidAmountMinor` incremented,
  posting intent created for applied portion.
- Posting is asynchronous via outbox; invoice and payment state updates are transactional
  and authoritative.
- SLA/retry/escalation (mandatory): target posting completion ≤ 5 minutes,
  exponential retry with `maxRetries = 10`.
- Posting failure on retry exhaustion: `postingError=true`, `InvoicePostingFailed` event
  emitted, reconciliation record created.
- Chargeback: explicit chargeback event triggers automatic reversal posting.
  No heuristic trigger logic in v1.
- Overpayment: customer credit balance created; invoice transitions to `Paid`.

#### Frontend Usage Notes

- Payment application UX must show applied amount, resulting outstanding balance,
  and whether posting is in-progress or errored.
- `postingError` flag on the invoice status response surfaces a posting-failure indicator
  to ops/support UX.

#### ADR Constraints

- `AD-002`, `AD-003`, `AD-010` (AR reduction, overpayment credit, idempotency) are mandatory.
- `AD-011` posting reference canonicalization applies to outbox-posted intents.
- `AD-014` async retry semantics govern outbox retry and escalation.
- ADR-0017 response code mapping is required for payment outcome endpoints.
- ADR-0018 audit actor derivation from security context applies to all state transitions.

#### Contract Test Traceability

- Service tests: `PaymentOutcomeProcessingServiceTest`
- Provider tests: `InvoicePaymentContractBehaviorIT`

### Story #5 — Reconcile POS Status with Accounting Authoritative Status

#### Behavioral Assertions

- POS-facing accounting status enum (v1, fixed): `PENDING_POSTING`, `POSTED`,
  `RECONCILED`, `REJECTED`, `REVERSED`, `VOIDED`, `ON_HOLD`, `DISPUTED`.
- Accounting status is financially authoritative; POS workflow state remains separate
  and visible alongside accounting status.
- Backward status transitions (e.g. `POSTED → PENDING_POSTING`) are abnormal:
  blocked by ordering guard, logged as alert/incident, return 409.
  TODO: exact alerting channel unspecified — update after resolution in issue #5.
- Event delivery model: at-least-once with idempotent consumer, bounded retry, DLQ on
  exhaustion, and max event-age enforcement.
- Three-tier permission authorities are required:
  - `VIEW_ACCOUNTING_STATUS` — view current accounting status on invoice
  - `REFRESH_ACCOUNTING_STATUS` — trigger manual status refresh
  - `VIEW_ACCOUNTING_DETAIL` — access accounting drilldown data
- Archived invoices continue to receive status synchronization and audit logging
  (archive-not-delete policy).
- Staleness indicator: when `accountingStatusUpdatedAt` is > 1 hour old, surface
  `stale=true` flag in `getInvoiceStatus` response.
- SLA tiers (observability target, not a hard contract):
  - Critical statuses (`POSTED`, `REJECTED`, `REVERSED`, `VOIDED`): p95 < 5s, p99 < 30s
  - Non-critical statuses (`PENDING_POSTING`, `ON_HOLD`, `DISPUTED`, `RECONCILED`):
    p95 < 30s, p99 < 2m

#### Frontend Usage Notes

- Invoice status display must show both POS workflow state and accounting authoritative
  status as separate, clearly labelled fields.
- Stale indicator (`stale=true`) should surface a visual warning prompting manual refresh.
- Discrepancy between POS and accounting state must surface as an explicit UI indicator;
  do not silently hide reconciliation failures.
- Permission gating: hide/disable drilldown and refresh controls for callers lacking
  `VIEW_ACCOUNTING_DETAIL` or `REFRESH_ACCOUNTING_STATUS` respectively.

#### ADR Constraints

- ADR-0017 response code mapping is required for status inquiry and reconciliation actions.
- ADR-0018 audit actor derivation applies to all synchronization and access-control events.
- `AD-007` event ingestion read-model visibility governs status sync processing semantics.
- `AD-013` permission gating applies to refresh, drilldown, and exception workflows.
- `AD-014` async retry semantics govern DLQ promotion and exhausted-retry behavior.

#### Contract Test Traceability

- Service tests: `AccountingStatusSyncServiceTest`, `AccountingStatusReconciliationServiceTest`
- Provider tests: `InvoicePaymentContractBehaviorIT`

## CAP-278: Posting Rule Engine

### Capability Metadata

- Capability ID: CAP-278
- Parent Issue: <https://github.com/louisburroughs/durion/issues/278>
- Backend Child Issues:
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/472>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/473>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/474>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/475>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/476>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/477>
  - <https://github.com/louisburroughs/durion-positivity-backend/issues/478>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Manage posting rule sets | `listPostingRuleSets`, `createPostingRuleSet`, `getPostingRuleSet`, `updatePostingRuleSet` | GET/POST/GET/PUT | `http://localhost:8080/v1/accounting/posting-rules...` |
| Publish/archive posting rule sets | `publishPostingRuleSet`, `archivePostingRuleSet`, `listPostingRuleVersions` | POST/POST/GET | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/...` |
| Reprocess with rule engine path | `reprocessSuspendedEvent`, `retryEventProcessing` | POST/POST | `http://localhost:8080/v1/accounting/events/{eventId}/...` |

### Behavioral Assertions

- Rule evaluation must produce balanced journal drafts.
- Idempotent handling must prevent duplicate postings across retries.
- Reprocessing history must capture attempt outcomes for auditability.
- Rules definition schema is authoritative in
  `domains/accounting/.business-rules/POSTING_RULES_SCHEMA.md`.
- Proportional split lines (Wave 2 E1 backend#945, schema §4): lines sharing a `splitGroup` within a
  condition split one `amountField` by `factorPercent` (0–100, 4dp); factors must sum to 100 and
  mixed DEBIT/CREDIT groups are forbidden. Shares round HALF_UP to 2dp with the residual assigned to
  the largest raw share (first-in-order tie-break), so each group sums exactly to the source amount.
  Non-split lines behave identically to pre-E1 rules.
- Condition predicates (Wave 2 E2 backend#946, schema §2.1): whitelist grammar only —
  `eventType` / `payload.<path>` clauses, operators `== != > >= < <=`, `&&` conjunction,
  string/number literals; no expression engine or scripting. Missing/non-scalar payload paths make a
  clause a non-match (safe default), never an error.
- Publish-time validation: split-group violations and predicate parse errors are aggregated and
  rejected with `422 UNBALANCED_RULES` carrying per-violation `fieldErrors` locators
  (e.g. `conditions[1].lines[2].factorPercent`, `conditions[0].condition`). Conditions on
  already-published pre-E2 versions that do not parse stay WARN + non-match at evaluation time.

### Frontend Usage Notes

- Rule-management UI should separate draft vs published versions clearly.
- Reprocessing UI should include failure reason and attempted mapping/rule context.
- Publish-failure UX should map `UNBALANCED_RULES` `fieldErrors` locators back to the offending
  condition/line in the rule editor so authors can fix all violations in one pass.

### ADR Constraints

- `AD-007` ingestion visibility and state model.
- `AD-011` canonical posting reference.
- `AD-014` asynchronous retry semantics.

### Events & Dependencies

- Core dependency chain: ingestion -> rule evaluation -> journal posting -> traceability.
- Integrates with CAP-051 journal posting and CAP-055 controls workflows.

### Contract Test Traceability

- Provider tests: `EventIngestionContractBehaviorIT`, `SuspenseQueueContractBehaviorIT`
- Service tests: `PostingRuleEvaluatorDefaultMappingTest`, `PostingRuleEvaluatorFeatureFlagTest`, `PostingEngineOrchestratorTest`

## CAP-316: Location Labor & Overhead Cost Report (read-only)

### Capability Metadata

- Capability ID: CAP-316
- Parent Issue: <https://github.com/louisburroughs/durion/issues/328>
- Backend Story: <https://github.com/louisburroughs/durion-positivity-backend/issues/724>
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Labor & Overhead cost report | `generateLaborOverheadReport` | GET | `http://localhost:8080/v1/accounting/reports/location/labor-overhead?locationId={id}&fiscalYear={yyyy}[&asOfMonth={1-12}]` |

> Path note: the published backend story (#724) and this guide use `/reports/location/labor-overhead`.
> This supersedes the `/reports/retread/labor-overhead` path in the older CAP-316 spec markdown.

### Behavioral Assertions

- Read-only: derives the canonical CAP-316 cost-line matrix from posted GL data; performs no posting,
  mutation, or inference. Reflects posted ledger state only (consistent with CAP-054).
- Response carries every canonical line (Labor §1, Overhead §2, section subtotals, Total Labor &
  Overhead) in contractual order, each with `monthly[12]` (index 0 = January) and `ytd`.
- Leaf monthly amounts equal the net (debit − credit) of posted journal-entry lines for the line's
  mapped GL account(s), filtered by the `locationId` dimension and transaction month within the year.
- Subtotal rows are computed column-wise from their children and are never mapped directly.
- `ytd` = sum of monthly values for elapsed months (1 … `asOfMonth`; default 12).
- Sign is preserved for credit/income lines (2.13 Inventory charge, 2.14 Rubber dust income).
- Unmapped / zero-activity lines return `0` with the full layout preserved; unknown location → all `0`.
- Currency context: `currency`, `localCurrencyPerUsd`, `averageRate` (1.00 for US plants). v1 assumes a
  US plant (rate 1.00) and echoes `locationId` as the label; FX/label enrichment is a documented follow-up.
- Report-line → GL-account mapping is persisted master-data: `statement_line_mappings` rows under
  `statement_type = LABOR_OVERHEAD`. v1 seeds a representative subset (migration `V3`); the full
  domain-authored account set is a follow-up.

### Frontend Usage Notes

- The screen renders the returned matrix directly (no client-side aggregation); preserve line order.
- Per-line `definition` and `costType` (`FIXED|VARIABLE|FIXED_IF_LOW_VOLUME`) drive inline help.

### ADR Constraints

- `AD-008` correlation and trace standards apply (auditable, non-mutating read).
- Gated by the financial-reporting permission family (`reporting:view:financial-statements`);
  unauthorized → `403`.

### Events & Dependencies

- Consumes posted journal/ledger data (CAP-051) and the chart of accounts (CAP-050).
- Follows CAP-054 financial-reporting conventions (`/v1/accounting/reports/...`, posted-state semantics).

### Contract Test Traceability

- Provider tests: `LaborOverheadReportContractBehaviorIT`
- Controller tests: `LaborOverheadReportControllerTest`
- Service tests: `LaborOverheadReportServiceImplTest`

## Events & Cross-Domain Dependencies

- Billing emits invoice issuance/finalization events consumed by Accounting.
- Payment domain emits payment/refund events; Accounting maintains financial read models and applications.
- People/Timekeeping provides approved time entries for export/audit flows.
- Event bus/messaging infrastructure carries canonical accounting event contracts.
- GL/posting subsystem is downstream of posting-rule and journal-entry workflows.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-accounting/openapi.yaml`
- OpenAPI source revision: `3ed498c`
- Last verified UTC: `2026-07-18T19:29:21Z`
- Generated API reference: `domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/accounting/.business-rules/AGENT_GUIDE.md`
- `domains/accounting/.business-rules/DOMAIN_NOTES.md`
- `domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- `domains/accounting/.business-rules/ERROR_CODES.md`
- `domains/accounting/.business-rules/POSTING_RULES_SCHEMA.md`
- `docs/capabilities/CAP-050/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-051/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-052/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-053/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-054/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-055/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-251/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-278/CAPABILITY_MANIFEST.yaml`
