---
type: Checklist
title: Accounting Checklist
description: Checklist for accounting-domain stories covering ledger integrity, posting configuration, event-driven GL posting, AR/AP, tenancy and permissions.
domain: accounting
tags: [domain, accounting, checklist]
---

# STORY_VALIDATION_CHECKLIST.md

## Summary

Checklist for accounting-domain stories covering ledger integrity, posting configuration, event-driven GL posting, AR/AP, tenancy and permissions.
Use it alongside `AGENT_GUIDE.md` (decisions `AD-001` … `AD-015`), the schema, error, permission, model, integration and contract guides in this
directory, and `../SPEC-inventory-adjustment-gl-posting.md` §4 (Kafka-fact-driven postings).

## Completed items

- [x] Aligned checklist items with the `AD-###` decision index in `AGENT_GUIDE.md` and the capability assertions in `BACKEND_CONTRACT_GUIDE.md`
- [x] Captured the consumer, idempotency and tenancy invariants of ADR-0044, ADR-0048, ADR-0061 and ADR-0062
- [x] Listed every rule the guides leave undecided under "Open Questions (require owner ruling)" instead of inventing it

A story that needs a rule this checklist cannot cite adds it to "Open Questions" and escalates a CLARIFICATION issue before implementation.

---

## Scope/Ownership

- [ ] Story is labeled `domain:accounting` and names its capability (`CAP-050` … `CAP-055`, `CAP-251`, `CAP-278`, `CAP-316`).
- [ ] Story touches only what accounting owns (`AGENT_GUIDE.md` "Domain Boundaries"): GL accounts, posting categories, mapping keys, GL mappings,
  posting rule sets, journal and ledger entries, periods (`AD-012`), payment applications, customer credits, credit memos, ingestion read models (`AD-007`).
- [ ] Story does not implement what upstream domains own: invoice issuance (Billing), payment capture and settlement (Payment), refund execution
  (`AD-001`, read-only `RefundTransaction`), time-entry approval (People), work execution, inventory valuation (ADR-0048 §1).
- [ ] Accounting is event-only inbound and outbound (ADR-0044 §6): no synchronous call to another domain module (R1), reads come from event-fed
  `ext_*` replicas (R3), cross-module writes are command events with a pending state and an idempotency key (R4).
- [ ] Story references only posting categories, mapping keys and GL accounts documented in `BACKEND_CONTRACT_GUIDE.md` or the spec; a new account,
  mapping or debit/credit pairing goes under "Open Questions", never invented (agent contract "MUST NOT").
- [ ] Story states whether posting is immediate on consumption or deferred and cites the decision that fixes it (spec §4.5 for Kafka facts).

---

## Data Model & Validation

- [ ] Every identifier is UUIDv7 (`AD-006`, ADR-0013): `@GeneratedValue` + `@UUIDv7Id`, never `@PrePersist`; DTO identifiers stay `UUID`-typed
  (ADR-0027); a non-UUIDv7 event id answers `400 INVALID_UUIDV7`.
- [ ] No `organizationId` field, column, filter or DTO property is introduced (ADR-0062 §4); the `organizationId` columns in `DOMAIN_MODEL.md` are
  the documented remnant and are not extended. The tenant comes from `TenantContext`, bound from `X-Tenant-Id`.
- [ ] A journal entry balances within `0.0001` (`POSTING_RULES_SCHEMA.md` §3.2), else `422 JE_UNBALANCED`; no zero-amount lines; amounts are
  positive with the side carried by `lineType` (`DOMAIN_MODEL.md`).
- [ ] A POSTED entry is immutable (`409 JE_ALREADY_POSTED`); a correction is a reversal that posts an inverse entry, links both ways, records
  `reversedAt` and the actor, and requires a non-blank `reason`; guards are `409 JE_ALREADY_REVERSED` and `409 JE_NOT_POSTED`; `reversalDate`
  defaults to the original date if that period is OPEN, else today (`BACKEND_CONTRACT_GUIDE.md` CAP-051).
- [ ] `entryNumber` (`JE-{YYYYMM}-{seq}`) is assigned only at POST time inside the posting transaction (CAP-051 `D-1`); DRAFT entries are `null`;
  no statutory gapless guarantee is claimed.
- [ ] GL mappings are effective-dated and append-only; an overlap for the same category and key answers `409 MAPPING_OVERLAP` with the conflicting
  ranges; a mapping for an INACTIVE category is rejected.
- [ ] `categoryCode` and `mappingKey` are unique; a key resolves to exactly one category; categories and keys are deactivated, never deleted
  (`409 CATEGORY_HAS_ACTIVE_MAPPINGS`).
- [ ] Rule-set versions move `DRAFT → PUBLISHED → ARCHIVED`, at most one PUBLISHED per set; a PUBLISHED version is immutable
  (`409 RULES_ALREADY_PUBLISHED`) and changes create a new DRAFT version (`POSTING_RULES_SCHEMA.md` §1, §5).
- [ ] Publish rejects every split-group and predicate violation in one pass with `422 UNBALANCED_RULES` and `fieldErrors` locators
  (`conditions[i].lines[j].factorPercent`, `conditions[i].splitGroup[name]`, `conditions[i].condition`).
- [ ] Split groups obey §4.1: `factorPercent` on every member (0–100, 4 dp), same non-blank `amountField`, same side, factors sum to exactly 100;
  shares round HALF_UP to 2 dp and the whole residual lands on the largest raw share, first in order on a tie (§4.2).
- [ ] Condition predicates use only the §2.1 grammar (`eventType` / `payload.<path>`, `== != > >= < <=`, `&&`); a missing or non-scalar path is a non-match, never an error.
- [ ] Periods are monthly `YYYY-MM`, OPEN → CLOSED, auto-provisioned on first posting, missing row means OPEN (CAP-054 `D-7`); close is refused
  with `422 PERIOD_HAS_DRAFT_ENTRIES`; the hard-lock date moves forward only (`422 HARD_LOCK_DATE_REGRESSION`) with a justification.
- [ ] The period gate runs on every posting and reversal path in the order hard lock > closed > override (`AD-012`, enforced): before the hard lock
  is `422 PERIOD_HARD_LOCKED` with no override; a CLOSED period is `422 PERIOD_CLOSED` unless the caller holds `accounting:period:override` and
  supplies a non-blank, audit-logged `overrideJustification`.
- [ ] Payment receipt does not reduce AR until a `PaymentApplication` exists (`AD-002`); a payment applies only from `AVAILABLE` with
  `unappliedAmount > 0`, to issued/open invoices with positive balance due, in the same currency, atomically.
- [ ] Overpayment creates a `CustomerCredit` and the response shows `unappliedAmount = 0` (`AD-003`); credit status is derived
  (`AVAILABLE → PARTIALLY_CONSUMED → CONSUMED`), only the open amount may be drawn down, an over-draw is `409` (CAP-052).
- [ ] `customerId` on an ingested payment is assigned once only, never changed, with a justification of at least 10 characters (`AD-004`).
- [ ] Dimensions are `Map<String, String>` values of at most 50 characters, optional unless the posting category requires them (then `422`); a new
  key is added to `DIMENSION_SCHEMA.md` first; the location dimension is code-keyed (issue #1797).
- [ ] Audit actor fields (`createdBy`, `updatedBy`, `postedBy`) are `String` resolved in the service layer from `SecurityContextHelper`, never the request body (ADR-0018).

---

## API Contract

- [ ] Every non-2xx body is the `ApiError` envelope (`code`, `message`, `status`, `timestamp`, `correlationId`, `fieldErrors[]`) per ADR-0017 §3;
  `code` values come from `ERROR_CODES.md`, and a new code is catalogued there before it is thrown.
- [ ] Status codes follow ADR-0017 §2: `403` for who the caller is; `409` only for the closed list (version, duplicate key, idempotency replay with a
  different payload, the target's lifecycle status); `422` for every other documented refusal; `400` for request shape only; one condition, one code.
- [ ] Canonical codes are used unchanged: `UNBALANCED_RULES` 422, `JE_UNBALANCED` 422, `PERIOD_CLOSED` 422, `PERIOD_HARD_LOCKED` 422,
  `MAPPING_OVERLAP` 409, `JE_ALREADY_POSTED` 409, `DUPLICATE_EVENT` 409, `PERMISSION_DENIED` 403.
- [ ] `X-Correlation-Id` is echoed or generated and mapped to `correlationId` (ADR-0017 §4); `traceparent` is propagated (`AD-008`).
- [ ] List endpoints paginate server-side with a stable sort and never embed raw payloads; detail fetches by primary identifier (`AGENT_GUIDE.md`
  "General API patterns"); parameter names match the module's existing OpenAPI, not a new convention.
- [ ] Commands accept a caller-generated UUIDv7 idempotency key (`applicationRequestId` for apply-payment, `requestId` for customer-credit
  draw-downs); a replay returns the original result (`replayed: true`) and never applies twice (`AD-010`).
- [ ] A mutation that enqueues a command event answers `202 Accepted` with a tracking reference and a pending-state resource; asynchronous rejection
  surfaces there, never as a late HTTP error (ADR-0017 §1, ADR-0044 R4).
- [ ] A read from a replica that has not caught up answers `503` with `Retry-After` and a pending `code`; an empty replica never yields `404` (ADR-0017 §1).
- [ ] Retry and reprocess are asynchronous jobs (`jobId` + status endpoint, `AD-014`) with a justification of 10+ characters; Kafka facts are not REST-retryable (spec §4.9).
- [ ] Responses beside ids carry nullable display values (`creditMemoReference`, `originalInvoiceReference`, `customerDisplayName`,
  `payloadReferences[].displayName`) and never a UUID as display text (issues #1779, #1778); the ids stay the command and link keys.
- [ ] Ingestion responses expose `processingStatus`, `idempotencyOutcome`, error fields and posting references (`journalEntryId` primary,
  `ledgerTransactionId` optional, `AD-011`) as backend-owned enums the UI never reinterprets (`AD-007`); `payloadSummary` is the default (`AD-009`).

---

## Events & Idempotency

- [ ] Every fact uses the ADR-0044 §3 envelope: `eventId` (UUIDv7), `eventType` (`{domain}.{entity}.{verb}`), `schemaVersion`, `aggregateId`,
  `aggregateVersion`, `occurredAtUtc`, `sourceService`, `correlationId`, `actor` (audit only) and the required `tenantId`; the DTO lives in
  `pos-domain-events` and is registered in `DomainEventContractTest`.
- [ ] The `organizationId` and `transactionDate` fields in the §3.2 example of `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` are not reproduced:
  ADR-0062 §4 and ADR-0044 §3 override that example; the tenant travels only as envelope `tenantId` and the Kafka header.
- [ ] Topics are `{domain}.events.v1` / `{domain}.commands.v1`, keyed by `aggregateId`; payload changes are additive-only; a breaking change is a new topic version.
- [ ] Outbound facts (`accounting.invoice.gl-posted`, `JournalEntryReversed`) go through `event_outbox` in the same transaction; no direct Kafka publish (ADR-0044 §4).
- [ ] The `@EmitEvent` → `pos-event-receiver` pipeline is audit-only (ADR-0044 R5): mutating controllers carry `@EmitEvent` with a registered id; no data flows through it.
- [ ] A Kafka-fact-driven posting has three idempotency layers (spec §4.4): the envelope `eventId` in `processed_events`, checked before any
  transaction; a namespaced posting key (`<NAMESPACE>:<kind>:<businessId>`) in `IdempotencyService` registered with the `journalEntryId`; and a
  deterministic journal-entry `sourceEventId = nameUUIDFromBytes(...)` found through `findBySourceEvent`.
- [ ] `transactionDate` is business time from the fact (`occurredAt`, or the invoice's `finalizedAt` for revenue), so redeliveries land in the same
  period and resolve the same effective-dated mapping (spec §4.5, CAP-052); a reversal mirror is dated at the event's `occurredAtUtc`.
- [ ] GL accounts are resolved at the business date through posting category + mapping key via `GLMappingResolver`; no account number is hardcoded
  in code; seed rows are idempotent in the reference-data migration.
- [ ] Accounting never infers, recomputes or substitutes an inventory unit cost (ADR-0048 §1, §3); `unitCost` and `costSource` are used as delivered.
- [ ] An uncosted fact (`unitCost` null or ≤ 0, `costSource = NONE`) is recorded and skipped, never posted: skip metric incremented, terminal
  ingestion outcome written, `processed_events` mark in its own transaction; the skip is terminal and recovery is upstream (spec §4.7, §4.8).
- [ ] Listener shape follows the ADR-0044 amendment of 2026-09-23: the `@KafkaListener` method is not `@Transactional`; it parses and checks
  `processed_events` before any transaction; handler and mark commit together in a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`; a
  permanent failure (`DatabindException`) is marked in a transaction of its own; `TransientDataAccessException` is rethrown with no mark.
- [ ] `PERIOD_CLOSED`, `PERIOD_HARD_LOCKED`, `MAPPING_NOT_FOUND` and transient errors on a Kafka fact propagate unmarked to the container error
  handler, retry, then land on `{topic}.dlq` with an alert, and are replayed after the operations fix (spec §4.5, §4.7).
- [ ] A Kafka fact writes only terminal `AccountingEvent` ingestion states (`PROCESSED`, or the proposed `SKIPPED`), never `FAILED` or `SUSPENDED`,
  because the REST retry scheduler re-runs those through posting rule sets (spec §4.9; see D5 below).
- [ ] A record without the `tenantId` header goes to the dead-letter path; `event_outbox` and `processed_events` are `@TenantGlobal` with a plain
  `tenant_id` column (ADR-0044 amendment 2026-09-09, ADR-0062 §8).

---

## Security

- [ ] Every endpoint is gated with `@PreAuthorize("hasAuthority('accounting:<resource>:<action>')")` using a string in `PERMISSION_TAXONOMY.md`;
  a permission the taxonomy lacks is added there first and registered through the module's permission registry (`AD-013`).
- [ ] Deny by default: an unknown permission hides the entry point and the backend answers `403`; `403` and `404` never reveal whether an id exists.
- [ ] Raw payloads are shown only with `accounting:events:view-payload` (`AD-009`), rendered as escaped text, never cached or logged; the stored
  payload is byte-identical for audit and is never rewritten for display (issue #1778).
- [ ] High-risk operations record a justification: post and reverse a journal entry, approve a vendor bill, deactivate a GL account, publish a rule
  set, reopen a period, set the hard lock, override a closed period (`PERMISSION_TAXONOMY.md` "Audit Requirements", CAP-054).
- [ ] Every mutation writes an audit record with actor, timestamp, request identifiers, redacted parameters and outcome (`AGENT_GUIDE.md` "Audit requirements").
- [ ] Tenant isolation (ADR-0062): the tenant is bound only from `X-Tenant-Id` into `TenantContext`; bodies and query parameters never carry a tenant;
  a `/v1/**` request without it is `401`; RLS is authoritative and every repository test runs on Testcontainers Postgres (§12).
- [ ] A new tenant-scoped table follows ADR-0062 §9 (`tenant_id NOT NULL`, RLS, constraints scoped by `(tenant_id, …)`); a global table is
  whitelisted in `db/tenancy-global-tables.txt` with `@TenantGlobal`; the module stays in `TenancyArchitectureTest` `ADOPTED_MODULES`.
- [ ] Scheduled work is per-tenant (`TenantIterator.forEachActiveTenant`) or `@PlatformScoped` touching only global tables (ADR-0062 §8).
- [ ] Where an endpoint accepts a `locationId` (time export per `AGENT_GUIDE.md` B4, location-scoped reports such as CAP-316), scope is checked after
  `@PreAuthorize` with `LocationScope.covers(permission, locationId)` (ADR-0061 §3), failing closed; accounting roles (`ACCOUNTANT`,
  `CONTROLLER`, `ACCOUNT_MANAGER`) traverse the `FINANCIAL` hierarchy (ADR-0061 §2).
- [ ] The event-channel `actor` is audit metadata only and never re-derives a permission check (ADR-0044 §5).

---

## Observability

- [ ] W3C Trace Context is propagated: `traceparent` required, `tracestate` optional; the frontend generates `traceparent` only when absent (`AD-008`).
- [ ] Logs are structured (operation, userId, identifiers, status, latency, errorCode) with the `AD-008` identifiers (`eventId`, `invoiceId`,
  `paymentId`, `journalEntryId`, `ingestionId`, `postingRuleSetId`, …); raw payload bodies and export contents are never logged.
- [ ] Metrics use the `AGENT_GUIDE.md` families (`accounting_ingestion_records_total{eventType,processingStatus}`, `accounting_payment_apply_requests_total{status}`, …).
- [ ] A Kafka fact consumer emits `accounting.inventory.fact.posted{eventType}` and `accounting.inventory.fact.skipped{eventType,reason}` (spec §5.3)
  plus the replica counters (`replica.payload.rejected`, `replica.persist.failed`, `replica.manifest.skipped{reason}`).
- [ ] Consumer lag and DLQ depth for every consumed topic are visible in the observability stack (ADR-0044 §4).
- [ ] A tenant appears in metrics only as a bounded label or exemplar, never a high-cardinality label (ADR-0062 §11).

---

## Performance & Failure Modes

- [ ] Concurrent applications against one payment are serialized by optimistic locking with exactly one server-side retry, then `409`;
  `unappliedAmount` never goes negative; customer-credit draw-downs cannot both pass the open-amount check (CAP-052).
- [ ] A posting-engine event blocked by a closed period is `SUSPENDED` with `failureReasonCode = PERIOD_CLOSED`, skipped by auto-retry, reprocessable after reopen (CAP-054).
- [ ] Outbox-posted intents retry with exponential backoff, `maxRetries = 10`, and on exhaustion set `postingError = true` and emit the failure
  event (CAP-251); every retry path has a DLQ and a max event age.
- [ ] The failure classes of spec §4.7 are each handled explicitly: applied, duplicate `eventId`, duplicate posting key, unparsable, uncosted, propagated.
- [ ] Backfill commands (`reconcileInvoiceRevenue`) are idempotent on the live path's key, one transaction per invoice, support `dryRun`, report per item (CAP-052).
- [ ] Replica drift is reconciled through per-tenant manifests on `{domain}.manifest.v1` and re-emit requests, never a synchronous call (ADR-0044 §4).
- [ ] Frontend failure states exist for `401`, `403`, `404`, `409`, `422`, `503` and timeouts, distinguishing `PERIOD_CLOSED` (recoverable) from
  `PERIOD_HARD_LOCKED` (terminal).

---

## Testing

- [ ] Unit tests cover the balance tolerance, the §4.3 split-distribution examples (including the `0.01` tie-break), the §2.1 predicate grammar
  rejections, UUIDv7 validation, allocation validation and every `@PreAuthorize` string.
- [ ] Every listener is pinned by a Spring-backed transaction test with a real transaction manager and a `@Transactional` handler that throws, bare and
  inside an enclosing transaction (templates: `LocationEventsListenerTransactionTest`, `InventoryCommandListenerTransactionTest`).
- [ ] Contract ITs (`*ContractBehaviorIT` named in `BACKEND_CONTRACT_GUIDE.md`) pin status codes, the `ApiError` envelope, canonical enums and field
  names; fact DTOs are covered by `DomainEventContractTest`.
- [ ] Posting ITs on real Postgres assert the posted lines, amounts and `transactionDate`; that redelivery and re-emission are no-ops; that an
  uncosted fact writes the terminal outcome and no entry; and that `PERIOD_CLOSED` propagates leaving no `processed_events` row (spec §6).
- [ ] Security tests prove `403` without existence leakage, raw-payload gating, role coverage (`AP_CLERK`, `GL_ANALYST`, `ACCOUNTANT`, `CONTROLLER`)
  and tenant isolation (`TenancySchemaConformanceIT`).
- [ ] SDK integration tests (`durion-positivity-sdk`) assert the journal entry an upstream action produced, via the ingestion record or
  `getJournalEntry`, and the accelerated-year simulator reconciles Σ posted amounts to Σ ledger value (spec §6).
- [ ] `pos-archunit` (`DomainWallsTest`, `TenancyArchitectureTest`) and the module `ArchitectureTest` pass after any package or client change.

---

## Documentation

- [ ] `pos-accounting/README.md` documents any new endpoint, event, mapping key, error code or configuration property the story adds.
- [ ] `BACKEND_CONTRACT_GUIDE.md` gains or updates the capability section (assertions, ADR constraints, test traceability); the generated API reference is regenerated.
- [ ] A controller, DTO, `@Operation`/`@Schema`, `@PreAuthorize` or permission-registry change regenerates `openapi.yaml`, updates the Angular SDK
  and runs the `API Artifacts Sync` workflow once pushed.
- [ ] New error codes are catalogued in `ERROR_CODES.md` before use (`UNMAPPED_EVENT_TYPE` and `UNCOSTED_FACT` are referenced by the schema and
  the spec but not yet catalogued); new permissions in `PERMISSION_TAXONOMY.md`; new dimensions in `DIMENSION_SCHEMA.md`; new event types in
  `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` §3.1 per its §10.1; rule-schema extensions as a new numbered section of `POSTING_RULES_SCHEMA.md`.
- [ ] A story that resolves an `AGENT_GUIDE.md` `TODO/CLARIFY` or `G#` question updates the guide and its decision index in the same change.
- [ ] `scripts/domain_okf_frontmatter.py --check` passes for every touched business-rules file; the knowledge catalog is regenerated when a document is added.

---

## Acceptance Criteria (Resolved Open Questions)

### Receivables and refunds

- [ ] Refund execution is the Payment domain's; accounting shows a read-only `RefundTransaction` with statuses `PENDING`, `COMPLETED`, `FAILED`,
  `QUARANTINED`, `paymentId` mandatory and `invoiceId` optional (`AD-001`, C1–C5).
- [ ] AR is reduced only by `PaymentApplication` records; overpayment becomes a `CustomerCredit` with `unappliedAmount = 0` (`AD-002`, `AD-003`).
- [ ] The frontend generates `applicationRequestId` as UUIDv7 and reuses it on retry; allocations are required, auto-allocation is out of scope, the
  application date is backend-controlled within an open period, and reversal is a separate story (`AD-010`, E5–E9).
- [ ] Customer-credit issuance recognizes the liability and every draw-down relieves it, so Σ open credits equals the control-account balance (#992).

### Ledger, periods, rules and ingestion

- [ ] `AccountingEventIngestionRecord` (`ingestionId` UUIDv7) is the read model; default list filtering is by `receivedAt`; `journalEntryId` is the
  primary posting reference and `ledgerTransactionId` secondary (`AD-007`, `AD-011`, F1–F5).
- [ ] Retry is an asynchronous job behind `accounting:events:retry`; raw payload display needs `accounting:events:view-payload`; auditors hold neither
  (`AD-009`, `AD-014`, F4–F7).
- [ ] `entryNumber` is `JE-{YYYYMM}-{seq}` assigned at POST time (CAP-051 `D-1`); reversal is race-safe with bidirectional linkage (#943).
- [ ] Periods are monthly, two-state, auto-provisioned, hard lock > closed > override (`AD-012` enforced, #944, CAP-054 `D-7`).
- [ ] Invoice revenue posts at `finalizedAt` through category `INVOICE_REVENUE` and keys `ACCOUNTS_RECEIVABLE`, `SERVICE_REVENUE`,
  `SALES_TAX_PAYABLE`, one open posting per `(invoiceId, finalizedAt)`, with `accounting.invoice.gl-posted` as the outbound fact (#1843).

### Inventory facts and exports

- [ ] The missing cycle-count and manual-adjustment producer is a defect, not a decision (spec Q1a); `COUNT_VARIANCE_OUT` is a shrinkage-class loss
  that must reach the GL and a count gain must post too (Q1b).
- [ ] A posting consumer must never be silently absent (Q2a); the uncosted skip must be counted (Q2b); no `InventoryAdjustment` REST path exists (Q3a).
- [ ] Timekeeping export is asynchronous (`QUEUED → PROCESSING → READY | FAILED`), interprets dates in the location timezone and exports APPROVED
  entries only (`AD-005`, `AD-015`, B2, B5).

---

## Open Questions (require owner ruling)

- [ ] Which account credits an inventory count or adjustment gain (`ADJUSTMENT_GAIN` mapping key: 5100, 5000 COGS or a new gain account)? (spec D2)
- [ ] Which counter account and category receive `inventory.product-value.changed` revaluations, and when is that consumer built? (spec D7)
- [ ] Do Kafka-consumed facts write `AccountingEvent` ingestion records with a new terminal `SKIPPED` status? (spec D5, D10)
- [ ] One generic `InventoryAdjustedV1` fact or two, and a new `INVENTORY_ADJUSTMENT` category or a widened `INVENTORY_SHRINKAGE`? (spec D1, D4)
- [ ] Should inventory refuse to post an uncosted variance rather than emit a fact accounting skips? (spec D6)
- [ ] Make `pos.accounting.kafka.enabled` / `pos.inventory.kafka.enabled` profile-level true and fail startup when off? (spec D3)
- [ ] Reason-code-based account routing for inventory variances in v1? (spec D9)
- [ ] Which HTTP status applies when a posting is refused by a missing mapping or dimension: `404`/`400` per `ERROR_CODES.md` or `422` per ADR-0017 §2?
- [ ] Which ingestion status taxonomy is canonical: `AGENT_GUIDE.md` F2 (`PROCESSED/REJECTED/QUARANTINED`) or the backend enum the spec records?
- [ ] Which family gates posting configuration, `accounting:mapping:*` or `accounting:posting_config:*`, and should the taxonomy list the
  `accounting:period:*`, `accounting:customer-credit:*`, `accounting:gl:reconcile`, `accounting:reconciliation:adjust` and `accounting:ar:*` tokens?
- [ ] Which dimension key spelling is canonical: `locationId` (`DIMENSION_SCHEMA.md`) or `location_id` (`DOMAIN_MODEL.md`)?
- [ ] How does ADR-0061 location scope apply to accounting's code-keyed location dimension (#1797) and to reports filtered by `locationId`?
- [ ] Does sales tax post as one `SALES_TAX_PAYABLE` line or per jurisdiction now that pos-tax returns `jurisdictions[]` (T1 #939)?
- [ ] What rounding and scale rule governs the UI balance check and multi-currency lines (4 dp storage vs currency scale)? (`AGENT_GUIDE.md` G21)
- [ ] Posting patterns for `order.shipmentCreated` / `order.orderFulfilled` (COGS accrual, deferred revenue) are still "coordination needed" — who owns them?
- [ ] Who is system of record for AP bills, which upstream events create vendor bills, and what is the status enum? (`AGENT_GUIDE.md` G23–G27)
- [ ] Are posting categories and mapping keys editable in place or versioned, can a key be re-pointed, are overlaps rejected or auto-end-dated, and
  may an ARCHIVED version seed a new one? (`AGENT_GUIDE.md` G13, G14, "Posting Rule Sets")
- [ ] What are the path, response schema and permissions of the synchronous ingestion submit tool, and is it allowed in production? (G1–G3)

---

## End

End of document.
