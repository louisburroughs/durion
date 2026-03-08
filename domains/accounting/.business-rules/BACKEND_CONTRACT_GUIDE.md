---
title: Accounting Backend Contract Guide
domain: accounting
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-accounting/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:07:15Z
last_updated: 2026-02-24
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
- Posting is allowed only in open accounting periods (`AD-012`).
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
| CAP-278 | `durion#278` | draft | Posting rule engine and reprocessing orchestration |

## Implementation Links / Backlog

- Backend child issues for CAP-251:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/5
  - https://github.com/louisburroughs/durion-positivity-backend/issues/6

These links are the authoritative backlog items that implement CAP-251 behavior. Use them for traceability and to resolve TODOs in this guide.

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| List GL accounts | `listGLAccounts` | GET | `http://localhost:8080/v1/accounting/gl-accounts` | Use for account maintenance tables |
| Create GL account | `createGLAccount` | POST | `http://localhost:8080/v1/accounting/gl-accounts` | Enforce unique account codes |
| Manage posting categories | `listPostingCategories` | GET | `http://localhost:8080/v1/accounting/posting-categories` | Pair with create/update/deactivate actions |
| Resolve posting mapping | `resolveGLMapping` | POST | `http://localhost:8080/v1/accounting/mappings/resolve` | Used by posting config UX and diagnostics |
| Create journal entry | `createJournalEntry` | POST | `http://localhost:8080/v1/accounting/journal-entries` | Draft creation before post/reverse |
| Post journal entry | `postJournalEntry` | POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/post` | Must satisfy open-period constraint |
| Apply payment to invoices | `applyPayment` | POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` | Requires idempotency behavior (`AD-010`) |
| Reverse payment application | `reversePaymentApplication` | POST | `http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse` | Audit trail required |
| Create credit memo | `createCreditMemo` | POST | `http://localhost:8080/v1/accounting/credit-memos` | Use when negative adjustments would occur |
| List AP bills | `listBills` | GET | `http://localhost:8080/v1/accounting/ap/bills` | AP workflow entry point |
| Execute AP payment | `executePayment` | POST | `http://localhost:8080/v1/accounting/ap/payments` | Track gateway/GL status transitions |
| Review ingestion queue | `listEvents` | GET | `http://localhost:8080/v1/accounting/events` | Operational monitoring and reconciliation |
| Reprocess suspended event | `reprocessSuspendedEvent` | POST | `http://localhost:8080/v1/accounting/events/{eventId}/reprocess` | Requires strict permission gating |
| View income statement | `generateIncomeStatement` | GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` | Reporting workflow |
| View balance sheet | `generateBalanceSheet` | GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` | Reporting workflow |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Use `Authorization` and required authorities per endpoint policy.
- Use idempotency key behavior for mutation flows where required by capability rules.

## Capability Sections

## CAP-050: Maintain Chart of Accounts and Posting Categories

### Capability Metadata

- Capability ID: CAP-050
- Parent Issue: https://github.com/louisburroughs/durion/issues/50
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/51
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create/list/get journal entries | `createJournalEntry`, `listJournalEntries`, `getJournalEntry` | POST/GET/GET | `http://localhost:8080/v1/accounting/journal-entries...` |
| Post/reverse journal entries | `postJournalEntry`, `reverseJournalEntry` | POST/POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/...` |
| Trace journal lineage | `getJournalTraceability` | GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` |

### Behavioral Assertions

- Journal entries must be balanced before posting.
- Posting in closed periods must be rejected.
- Reversal must preserve traceability to original posting.

### Frontend Usage Notes

- Posting UI should surface period-closed and unbalanced-entry failures explicitly.
- Traceability view should expose source event and posting reference links.

### ADR Constraints

- `AD-011` posting references must be canonical and queryable.
- `AD-012` accounting period enforcement is mandatory.

### Events & Dependencies

- Integrates with posting rules and GL posting services.
- Consumes accounting events from ingestion workflows.

### Contract Test Traceability

- Provider tests: `JournalEntryContractBehaviorIT`
- Service tests: `JournalEntryServiceTest`, `GLPostingServiceTest`

## CAP-052: Accounts Receivable (Invoice -> Cash Application)

### Capability Metadata

- Capability ID: CAP-052
- Parent Issue: https://github.com/louisburroughs/durion/issues/52
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Apply payment to invoices | `applyPayment` | POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` |
| Reverse payment application | `reversePaymentApplication` | POST | `http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse` |
| Reverse/void payment | `reversePayment`, `voidPayment` | POST/POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/...` |
| View payment/invoice status | `getPaymentStatus`, `getInvoiceStatus` | GET/GET | `http://localhost:8080/v1/accounting/payments/{paymentId}/status`, `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status` |
| Credit memo support | `createCreditMemo`, `listCreditMemos`, `getCreditMemo` | POST/GET/GET | `http://localhost:8080/v1/accounting/credit-memos...` |

### Behavioral Assertions

- AR is reduced only by application records, not raw payment receipt (`AD-002`).
- Overpayment must create customer credit deterministically (`AD-003`).
- Apply command must be idempotent and not double-apply (`AD-010`).

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
- Parent Issue: https://github.com/louisburroughs/durion/issues/53
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create and query vendor bills | `createBillFromGoodsReceivedEvent`, `getBillById`, `getBillByOriginEventId` | POST/GET/GET | `http://localhost:8080/v1/accounting/vendor-bills...` |
| Bill matching workflow | `listMatchCandidates`, `selectMatchCandidate`, `matchVendorInvoice`, `resolveMatchException` | GET/POST/POST/POST | `http://localhost:8080/v1/accounting/vendor-bills/...` |
| AP payment workflow | `listBills`, `executePayment`, `getPayment`, `getPaymentByRef` | GET/POST/GET/GET | `http://localhost:8080/v1/accounting/ap/...` |

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
- Parent Issue: https://github.com/louisburroughs/durion/issues/54
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Income statement | `generateIncomeStatement` | GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` |
| Balance sheet | `generateBalanceSheet` | GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` |
| Financial drilldowns | `drilldownToAccounts`, `drilldownToJournalLines` | GET/GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/...` |
| Audit trail adjustments | `recordPriceOverride`, `recordRefund`, `recordCancellation` | POST/POST/POST | `http://localhost:8080/v1/accounting/audit/...` |

### Behavioral Assertions

- Reporting endpoints must reflect posted ledger state.
- Adjustments must leave immutable audit evidence.
- Closed-period constraints must be enforced where mutation is requested.

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
- Parent Issue: https://github.com/louisburroughs/durion/issues/55
- OpenAPI Source: `durion-positivity-backend/pos-accounting/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List/query event ingestion records | `listEvents`, `getEvent`, `getEventProcessingLog` | GET/GET/GET | `http://localhost:8080/v1/accounting/events...` |
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

## CAP-278: Posting Rule Engine

### Capability Metadata

- Capability ID: CAP-278
- Parent Issue: https://github.com/louisburroughs/durion/issues/278
- Backend Child Issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/472
  - https://github.com/louisburroughs/durion-positivity-backend/issues/473
  - https://github.com/louisburroughs/durion-positivity-backend/issues/474
  - https://github.com/louisburroughs/durion-positivity-backend/issues/475
  - https://github.com/louisburroughs/durion-positivity-backend/issues/476
  - https://github.com/louisburroughs/durion-positivity-backend/issues/477
  - https://github.com/louisburroughs/durion-positivity-backend/issues/478
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

### Frontend Usage Notes

- Rule-management UI should separate draft vs published versions clearly.
- Reprocessing UI should include failure reason and attempted mapping/rule context.

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

## Events & Cross-Domain Dependencies

- Billing emits invoice issuance/finalization events consumed by Accounting.
- Payment domain emits payment/refund events; Accounting maintains financial read models and applications.
- People/Timekeeping provides approved time entries for export/audit flows.
- Event bus/messaging infrastructure carries canonical accounting event contracts.
- GL/posting subsystem is downstream of posting-rule and journal-entry workflows.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-accounting/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:07:15Z`
- Generated API reference: `domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/accounting/.business-rules/AGENT_GUIDE.md`
- `domains/accounting/.business-rules/DOMAIN_NOTES.md`
- `domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- `domains/accounting/.business-rules/ERROR_CODES.md`
- `docs/capabilities/CAP-050/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-051/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-052/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-053/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-054/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-055/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/CAP-278/CAPABILITY_MANIFEST.yaml`
