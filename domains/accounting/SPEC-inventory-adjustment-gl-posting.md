---
type: Specification
title: Inventory Adjustment GL Posting
description: Ruling on durion-positivity-backend#2186 and the specification of the event flow from an approved inventory adjustment (cycle count or manual) to the general ledger, mirroring the scrap-to-shrinkage precedent.
status: proposed
domain: accounting
tags: [accounting, inventory, events, gl-posting, adr-0044, adr-0048]
---

## SPEC — Inventory Adjustment GL Posting

> Status: PROPOSED · Created 2026-09-24 · Issue: [durion-positivity-backend#2186](https://github.com/louisburroughs/durion-positivity-backend/issues/2186) ·
> Branch: `claude/determined-wright-9s457t`
>
> Purpose: answer the six questions in #2186 with domain authority, specify the **correct flow of events** from an approved inventory adjustment to the general ledger,
> and record the design that closes the gap. Financial meaning and posting semantics were ruled by the Accounting Domain Agent
> (`.claude/agents/domains/accounting-domain.md`); the code facts were verified against source on 2026-09-24. Ten decisions in §7 need the platform owner before the
> work is cut into stories.
>
> Authority: [ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md) (event-only domain walls, consumer shape amendment 2026-09-23),
> [ADR-0048](../../docs/adr/0048-inventory-owned-valuation-configurable-costing-method.adr.md) (inventory-owned valuation, cost-bearing facts),
> [AGENT_GUIDE.md](.business-rules/AGENT_GUIDE.md) AD-006 / AD-007 / AD-009 / AD-011 / AD-014,
> [CROSS_DOMAIN_INTEGRATION_CONTRACTS.md](.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md) §3.1, the inventory
> [odoo-parity spec](../inventory/SPEC-pos-inventory-odoo-parity.md) D4 / J3 and [plan](../inventory/plan-odoo-parity-pos-inventory.md) D2 / J3.
>
> Catalog entries used: `knowledge-catalog/domains/accounting.md`, `knowledge-catalog/domains/inventory.md`, `knowledge-catalog/backend/pos-accounting.md`,
> `knowledge-catalog/backend/pos-inventory.md`, `knowledge-catalog/backend/pos-domain-events.md`, `knowledge-catalog/adr/0044-…`, `knowledge-catalog/adr/0048-…`.

---

## 1. Problem statement

Two inventory paths remove or add the same physical stock and post the same on-hand-affecting ledger entries, but only one of them tells accounting:

- a **scrap** posts `SCRAP_OUT` and emits `ScrapPostedV1`; pos-accounting consumes it and posts `Dr 5100 Inventory Shrinkage / Cr 1300 Inventory`;
- a **cycle-count adjustment** posts `COUNT_VARIANCE_IN` / `COUNT_VARIANCE_OUT` and emits nothing a consumer can see; a **manual adjustment request** posts
  `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` and emits nothing either.

So the books depend on which screen the clerk used. Every environment that runs cycle counts (every SDK accelerated-year run does, weekly) accumulates inventory value
changes with no GL entry, and no test would notice. #2186 asks whether that is intended (Q1), whether the scrap chain is actually switched on and how often its
"uncosted" skip fires (Q2), and whether the `InventoryAdjustment` ingestion path that frontend story #184 was written against exists (Q3).

## 2. Current state (verified against source, 2026-09-24)

All paths are relative to `durion-positivity-backend` unless stated. Line numbers are from the `main` head at the time of writing.

### 2.1 Producers on `inventory.events.v1`

`pos-inventory` publishes facts through `InventoryFactPublisher` (`pos-inventory/src/main/java/com/positivity/inventory/internal/service/InventoryFactPublisher.java`),
the ADR-0044 §6 transactional outbox: a service queues a fact inside its business transaction and the publisher drains the queue to `event_outbox` at `beforeCommit`.
The outbox writer bean exists only when `pos.inventory.kafka.enabled=true`; with the flag off every `record*` / `mark*` call is a no-op.

| Ledger path | Service | Ledger event type | Occurrence fact | Availability snapshot (`markEntry`) |
| --- | --- | --- | --- | --- |
| Scrap / write-off | `internal/scrap/service/ScrapServiceImpl.postApprovedScrap` (l. 365–460) | `SCRAP_OUT` | `ScrapPostedV1` (`inventory.scrap.posted`, schema v2) | yes (l. 412) |
| Cycle-count adjustment | `internal/cyclecount/service/CycleCountAdjustmentServiceImpl.postAdjustmentToLedger` (l. 343–405) | `COUNT_VARIANCE_IN` / `COUNT_VARIANCE_OUT` | **none** | **none** |
| Manual adjustment request | `internal/movement/service/StockMovementServiceImpl.approveAdjustmentRequest` (l. 169–211) | `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` | **none** | **none** |
| Manual cost revaluation | `internal/costing/service/RevaluationServiceImpl` (l. 206) | — (cost state only) | `ProductValueChangedV1` (`inventory.product-value.changed`) | n/a |

The cycle-count service does not reference `InventoryFactPublisher` at all. On approval it posts the `COUNT_VARIANCE_*` entry through the `LedgerPostingService` funnel and
publishes a Spring in-process `InventoryAuditEvent` (`publishMovementAdjustedEvent`, l. 451–478). Its javadoc promises that an "infrastructure listener" forwards it to
Kafka; no such listener exists in `pos-inventory` main code (the only consumer is a contract test). ADR-0044 R5 declares the `@EmitEvent` / audit pipeline audit-only in
any case. Two consequences follow:

- pos-accounting cannot see a count variance, so a write-off booked as a cycle count posts nothing to the GL while the same write-off booked as a scrap posts shrinkage;
- the availability replicas fed by `InventoryAvailabilityUpdatedV1` / `StorageLocationOnHandUpdatedV1` (pos-catalog, pos-order, pos-workorder, pos-location) are not told
  that on-hand moved, because `markEntry` is never called on this path either. That is a separate replica-staleness defect surfaced by the same omission.

**Cost is already computed.** `createAdjustment` (l. 77–150) sources `costAtTimeOfAdjustment` from the J1 costing engine (`resolveEngineCost`, l. 442–449:
`sku_cost_state.standardCost` under STANDARD, else `avgCost`) with the request-supplied cost as fallback, and `LedgerPostingServiceImpl.post` (l. 155) then runs
`LedgerCostingService.stampCosts` (l. 65–95), which stamps a method-derived `unitCost` on every on-hand-affecting entry — exactly what the scrap path reads back from the
saved entry (`saved.getUnitCost()`, l. 431) to fill `ScrapPostedV1.unitCost`. Inventory computes the cost accounting needs for a count variance and then discards it.

**Cost of a count gain is currently wrong.** `postAdjustmentToLedger` passes `.unitCost(costAtTimeOfAdjustment)` (l. 361) into the funnel. `AverageCostingStrategy.cost`
(`internal/service/AverageCostingStrategy.java` l. 57–70) treats *any* positive change carrying a document cost as a **receipt** and re-blends the running average with it;
`StandardCostingStrategy` (l. 43–45) overwrites its latest-receipt memo. A count gain is not a purchase: it must enter at the current method cost (the cost-less inbound
branch, `AverageCostingStrategy` l. 73–76), which is what the manual path already does (`StockMovementServiceImpl` sets no `unitCost`, l. 182–197). This is an
inventory-domain defect that the fact would otherwise propagate to the GL, so it is in scope (§5.2).

### 2.2 The accounting consumer

`pos-accounting/src/main/java/com/positivity/accounting/internal/service/InventoryEventsListener.java`:

- `@KafkaListener(topics = "${pos.accounting.kafka.inventory-events-topic:inventory.events.v1}", groupId = "pos-accounting-inventory-events")` (l. 78–80);
- `@ConditionalOnProperty(prefix = "pos.accounting.kafka", name = "enabled", havingValue = "true")` (l. 48) — the bean does not exist when the flag is off;
- l. 91: `if (!ScrapPostedV1.EVENT_TYPE.equals(eventType)) return;` — every other fact type on the topic is dropped without recording its `eventId` (by design: the topic's
  other types are high-volume snapshots);
- l. 96–99: `processed_events` dedupe on the envelope `eventId`;
- l. 127–141: **record-and-skip** when `unitCost == null || unitCost.signum() <= 0` — a WARN log (`"Skipping uncosted scrap fact"`) and a `processed_events` mark. There is no
  metric for this branch; the only counter on the class (`replica.payload.rejected`) counts Jackson databind failures. The posting key is **not** registered on this path;
- l. 143: `InventoryShrinkagePostingService.postShrinkage(fact)`.

`InventoryShrinkagePostingService` (same package) posts `Dr SHRINKAGE_EXPENSE (5100) / Cr INVENTORY_ASSET (1300)` for `quantity × unitCost`, accounts resolved through the
`INVENTORY_SHRINKAGE` posting category by `GLMappingResolver`, idempotent on `INVENTORY_SHRINKAGE_GL_POSTING:<scrapId>` via `IdempotencyService`, journal-entry
`sourceEventId = nameUUID("INVENTORY_SHRINKAGE:" + scrapId)`, transaction date = the fact's `occurredAt` (business time), period gate inside
`JournalEntryService.postJournalEntry`. `GLPostingServiceImpl.postInventoryShrinkage` (l. 355–394) builds the two-line entry. This chain is complete and reachable from a
real caller (`ScrapController` → `ScrapServiceImpl`).

`OrderEventsListener` → `RegisterOverShortPostingService` (odoo-parity G3, #1083) is the second consumer of the same shape (`order.session.closed` → `Dr 6115 Cash Short /
Cr 1095` or `Dr 1095 / Cr 4930 Cash Over`, sign-routed at l. 83–95), so a third consumer has two precedents to mirror, one of them two-sided.

**Listener transaction shape.** Both listeners annotate the `@KafkaListener` method `@Transactional` (`InventoryEventsListener` l. 81, `OrderEventsListener` l. 70). The
ADR-0044 amendment of 2026-09-23 (durion-positivity-backend#2146, "Consumer transaction shape") retires that shape: the listener method must not be transactional, the
handler and its `processed_events` mark commit together in the handler's own `REQUIRES_NEW` transaction, a permanent failure is marked in a transaction of its own, and
transient failures propagate. `LocationEventsListener` in the same module already has the amended shape (`TransactionTemplate`, `PROPAGATION_REQUIRES_NEW`, l. 74–143)
with `LocationEventsListenerTransactionTest` pinning it; the platform template is `pos-inventory`'s `InventoryCommandListenerTransactionTest`. Any new accounting consumer
must use the amended shape, and the two old-shape listeners are corrected in the same change (§5.3).

No Kafka fact consumer writes an `AccountingEvent` row (§2.4); the audit trail of a fact-driven posting is the journal entry itself (its `sourceEventId`), the idempotency
key, and the `processed_events` mark.

### 2.3 Configuration per environment

| Setting | Source | Value |
| --- | --- | --- |
| `pos.accounting.kafka.enabled` default | `pos-accounting/src/main/resources/application.yml` l. 68 | `${POS_ACCOUNTING_KAFKA_ENABLED:false}` |
| Compose (local / accelerated) | `docker-compose.yml` l. 551 | `POS_ACCOUNTING_KAFKA_ENABLED: ${POS_ACCOUNTING_KAFKA_ENABLED:-true}` |
| Alpha | `deployment/alpha/docker-compose.prod.yml` l. 92 | `POS_ACCOUNTING_KAFKA_ENABLED: "true"` |
| `pos.inventory.kafka.enabled` | `docker-compose.yml` l. 826, `deployment/alpha/docker-compose.prod.yml` l. 140 | `true` in both |
| `application-alpha.yml`, `application-prod.yml` (pos-accounting) | no `kafka` key | inherit the env-var default, i.e. **`false` unless the deployment sets `POS_ACCOUNTING_KAFKA_ENABLED`** |
| Runbook | `docs/OPERATIONS_RUNBOOK.md` l. 1140–1160 | flags documented as opt-in "until the Phase 0.4 tier-1 flip"; the warranty rollout (#927) turned both on for compose and alpha |

The scrap → shrinkage consumer **is** switched on in every environment that exists today (Compose and alpha), but by inheritance from the warranty rollout rather than by an
inventory-specific decision, and any environment built from `application-prod.yml` without the env var silently posts nothing. ADR-0044 §4 already decided "Kafka as
tier-1 infrastructure … no more `@ConditionalOnProperty` opt-in for domain flows" (l. 126–127); the opt-in is the transitional state the runbook calls temporary.

### 2.4 The REST ingestion read model and frontend story #184

pos-accounting has a second, independent ingestion architecture: `EventIngestionController` (`submitAccountingEvent`, `listAccountingEvents`, `getAccountingEvent`,
`retryAccountingEvent`, `getEventProcessingLog`) → `EventIngestionServiceImpl` → the `AccountingEvent` entity (`accounting_event`: `eventType` string, `sourceSystem`,
`transactionDate`, `payload` map, `status` RECEIVED / PROCESSING / PROCESSED / FAILED / SUSPENDED, `journalEntryId`, `errorMessage`, `idempotencyOutcome`, `ingestionId`,
`attemptCount`, `failureReasonCode`) → published `PostingRuleSet` versions evaluated by `PostingRuleEvaluator` ([POSTING_RULES_SCHEMA.md](.business-rules/POSTING_RULES_SCHEMA.md)).
This is the read model that AD-007 (Ingestion Monitoring Read Model) and AD-011 (posting reference = `journalEntryId`) describe, and the one durion-moqui-frontend#184
("Ingest InventoryAdjustment Event", CAP-049, `docs/capabilities/CAP-049/stories/frontend/CAP_049.184.frontend.md`) was written against, with `eventType` fixed to
`InventoryAdjusted`.

Findings:

- **No code path handles an event type named `InventoryAdjustment`, `InventoryAdjusted` or `inventory.inventoryAdjustment`.** The event type is a free string on
  `submitAccountingEvent`; nothing produces it and no posting rule set is seeded for it, so a hand-submitted event would end `FAILED` with `UNMAPPED_EVENT_TYPE`
  (`PostingRuleEvaluatorImpl` l. 158, 226). No Kafka consumer maps a fact onto it.
- The REST retry scheduler selects every `FAILED` / `SUSPENDED` row (`EventIngestionServiceImpl` l. 564–571) and `retryAccountingEvent` accepts them (l. 302–305); both
  re-run the event through posting rule sets. That matters for §4.9: a Kafka fact must never be written in a retryable state.
- `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md` §3.1 listed `inventory.inventoryAdjustment` (payload `{adjustmentId, productId, quantityDelta, adjustmentReason}`, posting
  pattern "Inventory variance (debit/credit Inventory, credit/debit COGS or misc)") as "🟨 Coordination needed" — the contract was never closed. It is updated with this spec.
- #184 hardcodes the AD-007 taxonomy `PROCESSED / REJECTED / QUARANTINED`; the backend enum is `RECEIVED / PROCESSING / PROCESSED / FAILED / SUSPENDED`, and AGENT_GUIDE
  itself says the UI must treat `processingStatus` as backend-owned. CAP-049 `runs/latest.md` marks story 184 "done" (2025-03-27) for the retired Moqui frontend. The story
  is therefore specified against a producer, a consumer and a status taxonomy that do not exist (Q3b), independent of whether its screens were ever built.

### 2.5 Test coverage

- `durion-positivity-sdk` suite E (`packages/sdk-integration-tests/src/suites/e-cycle-count.itest.ts`, E8–E10) creates and approves a cycle-count adjustment and asserts
  `approved.ledgerEntryId` — the **inventory** ledger entry. The accelerated-year `InventoryMaintenanceSimulator.runCycleCount` does the same on every weekly count day.
- No integration test calls `listAccountingEvents`, `getAccountingEvent`, `getEventProcessingLog`, `listJournalEntries` or `getJournalEntryTraceability` after a scrap or an
  adjustment. Nothing in the suite would notice the shrinkage consumer being switched off, the uncosted skip firing, or the adjustment producer being absent. The scrap
  suite has no accounting assertion at all.
- Backend: `InventoryShrinkageGLPostingIT` (pos-accounting) drives `InventoryEventsListener` directly with envelope JSON and asserts one balanced Dr 5100 / Cr 1300 entry
  with replay no-ops; `ScrapContractBehaviorIT` (pos-inventory) asserts a `ScrapPostedV1` row in `event_outbox`. There is no equivalent for either adjustment path —
  nothing can be asserted, because nothing is produced.
- The accounting REST API can find a journal entry by id, by `entryNumber`, or by traceability (`sourceEventId` on the response), but **not by `sourceEventId` as a
  filter** (`JournalEntryRepository.findBySourceEvent` exists; `listJournalEntries` exposes only `entryNumber`). An SDK assertion "the adjustment I approved produced JE X"
  needs the ingestion record of §4.9 or a `sourceEventId` filter (§6).

## 3. Rulings on the six questions

| # | Question | Ruling |
| --- | --- | --- |
| Q1a | Is the missing cycle-count producer intended? | **Not intended — the producer is missing.** Spec J3 explicitly plans cycle-count fact alignment; ADR-0048 §3 and IMP-002 require every on-hand-affecting posting to carry its cost to accounting; the cross-domain contract lists the adjustment event as required. Nothing documents a decision to keep count variances inventory-only. J3 (#1053) delivered the cost-source half and not the fact. |
| Q1b | Intended treatment of `COUNT_VARIANCE_OUT` | **A shrinkage-class loss that must reach the GL**: `Dr 5100 Inventory Shrinkage / Cr 1300 Inventory`, amount = abs(quantity delta) × the engine cost stamped on the ledger row. It is not inventory-only and there is no other route to the GL: the in-process audit event has no consumer, and ADR-0044 §6 makes accounting event-only. Until it posts, GL 1300 is overstated by every net count loss and understated by every gain, and the J2 valuation read model can never reconcile to the GL. |
| Q2a | Is the consumer switched on where it matters? | Alpha: **on**. Root compose: **on by default**. Application default: **off**, and neither `application-alpha.yml` nor `application-prod.yml` sets it, so a runtime that does not pass the env var silently posts nothing. Ruling: silent-off is not acceptable for a posting consumer; profile-level `true` plus a startup guard (§5.4, D3). |
| Q2b | How often does the uncosted skip fire? | **Unknowable today: there is no metric, only a WARN.** In the SDK fixtures and the simulator, products are routinely scrapped without a priced receipt, so `costSource = NONE` is expected to be the common case there. The absence of a counter is itself the finding. Recovery is not automatic and should not be (§4.8). |
| Q3a | Is there an `InventoryAdjustment` ingestion path? | **No.** No producer, no Kafka mapping, no posting rule set. A hand-submitted REST event of that type fails `UNMAPPED_EVENT_TYPE`. |
| Q3b | Descope or pair #184? | **Pair, and re-scope.** #184 is templated from AD-007 against a producer, a consumer and a status taxonomy that do not exist in code. Re-scope it as the Angular ingestion monitor for `inventory.adjustment.posted` **and** `inventory.scrap.posted`, blocked on the producer (§5.2), the consumer (§5.3) and the ingestion-record write (§4.9). Retry stays unsupported for Kafka facts (replay is via DLQ). CAP-049's "done" is wrong. |

Rulings folded into §4: a count **gain** (`COUNT_VARIANCE_IN`) **must post** — both directions, or the GL inventory balance drifts irrecoverably from the valuation read
model; the manual `ADJUSTMENT_IN/OUT` path gets **identical treatment through the same fact**; `ProductValueChangedV1` **must** be consumed (ADR-0048 §3 names revaluation
facts in the contract surface) but is a **separate follow-up** with its own owner decision on the counter account (D7).

## 4. Correct flow of events

```mermaid
sequenceDiagram
    participant U as Clerk / Manager
    participant INV as pos-inventory
    participant OB as event_outbox → inventory.events.v1
    participant ACC as pos-accounting InventoryEventsListener
    participant POST as InventoryAdjustmentPostingService
    participant GL as JournalEntryService (period gate)
    U->>INV: approve cycle-count adjustment / adjustment request
    INV->>INV: LedgerPostingService.post(COUNT_VARIANCE_* | ADJUSTMENT_*) → stampCosts (engine unitCost)
    INV->>INV: markEntry(saved) → availability snapshot facts
    INV->>OB: recordInventoryAdjusted(InventoryAdjustedV1) — same transaction, beforeCommit
    OB-->>ACC: envelope {eventId, eventType=inventory.adjustment.posted, aggregateId=adjustmentId, tenantId, payload}
    ACC->>ACC: parse; processed_events.existsById(eventId)? drop
    ACC->>POST: REQUIRES_NEW: postAdjustment(fact)
    POST->>POST: posting key INVENTORY_ADJUSTMENT_GL_POSTING:<kind>:<adjustmentId> already registered? return
    POST->>POST: unitCost null/≤0 → SKIPPED (metric + ingestion record), no JE
    POST->>GL: create + post JE at occurredAt (loss: Dr ADJUSTMENT_LOSS / Cr INVENTORY_ASSET; gain: Dr INVENTORY_ASSET / Cr ADJUSTMENT_GAIN)
    GL-->>POST: journalEntryId (PERIOD_CLOSED / MAPPING_NOT_FOUND propagate → retry → DLQ)
    POST->>POST: registerKey; AccountingEvent row PROCESSED + journalEntryId; markProcessed — one commit
```

### 4.1 Trigger points (producer, `pos-inventory`)

A fact is emitted **only when a ledger entry was actually posted**, in the same transaction, built from the funnel's saved row — mirroring `ScrapServiceImpl` l. 412–446:

| Path | Trigger | Ledger event type |
| --- | --- | --- |
| Cycle count, manual approval | `CycleCountAdjustmentServiceImpl.approveAdjustment` → `postApprovedAdjustment` → `postAdjustmentToLedger`, immediately after `ledgerPostingService.post` returns (l. 370) | `COUNT_VARIANCE_IN` / `COUNT_VARIANCE_OUT` |
| Cycle count, auto-approve below threshold | `createAdjustment` (l. 128–140) → the same `postAdjustmentToLedger` | same |
| Manual adjustment request | `StockMovementServiceImpl.approveAdjustmentRequest`, after `ledgerPostingService.post` (l. 211) | `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` |

No fact for: rejected adjustments; a zero recomputed variance (`postApprovedAdjustment` already skips posting, l. 323–329); a `FAILED` posting (the transaction rolled
back — the `FAILED` status is written in a transaction of its own per #2170 — so nothing reached the outbox). Re-approving a `FAILED` adjustment posts once and emits once.

At the same trigger points the producer also calls `inventoryFactPublisher.markEntry(saved)` so the `InventoryAvailabilityUpdatedV1` / `StorageLocationOnHandUpdatedV1`
snapshot facts are emitted for count variances and manual adjustments (the replica-staleness defect of §2.1; an inventory-domain fix, not an accounting ruling).

### 4.2 Fact contract — one fact, `InventoryAdjustedV1`

**Ruling: one generic fact covering both paths**, not a cycle-count-specific one (D1). The accounting treatment is identical (a governed, non-sale change to on-hand at
the engine cost); the consumer must not branch on the document kind to build the journal entry; the kind is a traceability and analytics dimension. Two facts would double
the consumer, the seed and the tests for zero accounting difference.

- Class `com.positivity.domainevents.inventory.InventoryAdjustedV1` in `pos-domain-events` (ADR-0044 §3), registered in `DomainEventContractTest`.
- `EVENT_TYPE = "inventory.adjustment.posted"` — the ADR-0044 naming convention (`inventory.scrap.posted`, `inventory.product-value.changed`). The pre-ADR catalog name
  `inventory.inventoryAdjustment` is retired (§8).
- `SCHEMA_VERSION = 1`; additive-only evolution.
- Topic `inventory.events.v1`; envelope `aggregateId = adjustmentId` (per-adjustment ordering); `sourceService = pos-inventory`; `tenantId` stamped by the outbox writer
  (ADR-0044 amendment 2026-09-09); `eventId` UUIDv7.

| Field | Type | Rule |
| --- | --- | --- |
| `adjustmentId` | UUID, required | `CycleCountAdjustment.adjustmentId` or `InventoryAdjustmentRequest.adjustmentRequestId` — the value the ledger row carries as `adjustmentId`. Business identity for idempotency. |
| `adjustmentKind` | String, required | `CYCLE_COUNT` or `MANUAL_ADJUSTMENT`. |
| `ledgerEventType` | String, required | `COUNT_VARIANCE_IN` / `COUNT_VARIANCE_OUT` / `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` — traceability to the inventory ledger; **not** used to build the JE. |
| `ledgerEntryId` | UUID, required | The posted `InventoryLedgerEntry.ledgerEntryId`. |
| `sku` | String, required | `stockItemId` (named `sku` as in `ScrapPostedV1`). |
| `locationId` | UUID, nullable | The posting location (`postingLocationOf`); null only for task-less, location-less variances. |
| `taskId` | UUID, nullable | The cycle-count task, when any. |
| `reasonCode` | String, required | `CycleCountAdjustment.reasonCode` / `InventoryAdjustmentRequest.reasonCode`. Carried into the JE description; **not** used for account routing in v1 (D9). |
| `quantityDelta` | BigDecimal, required, non-zero | **Sign convention: positive = gain (on-hand up, IN), negative = loss (on-hand down, OUT).** The consumer uses the sign to choose the JE side and `abs()` for the amount. Decimal-capable per ADR-0055 (bulk products count in fractional base units), like the ledger's `changeInQuantity` and `ProductValueChangedV1.onHandQuantity`. |
| `unitCost` | BigDecimal, nullable | The **method-derived cost stamped by `LedgerCostingService` on the posted row** (`saved.getUnitCost()`), exactly as scrap does. **Not** `costAtTimeOfAdjustment` — that is the create-time snapshot used for the value-based approval tier, not the cost at posting time (ADR-0048 §3). |
| `costSource` | String, required | `STANDARD` / `AVERAGE` / `NONE` — `methodResolver.resolve(sku).name()` when `unitCost != null`, else `NONE` (the `ScrapServiceImpl` l. 432–434 rule). |
| `occurredAt` | Instant, required | The ledger posting instant (`postedAt`); business time. |

Constructor validation as in `ScrapPostedV1`: non-null ids, non-blank strings, non-zero `quantityDelta`, non-blank `costSource`, non-null `occurredAt`. Invariant for the
consumer: `unitCost` and `costSource` are inventory's; accounting **never** infers, recomputes or substitutes a cost (ADR-0048 §1, §3).

### 4.3 Outbox and topic

`InventoryFactPublisher.recordInventoryAdjusted(fact)` queues the fact per transaction; `publishInventoryAdjustments` drains it at `beforeCommit` through
`OutboxEventWriter` to `event_outbox` on `inventory.events.v1`, keyed by `adjustmentId`. `OutboxEventWriter` is conditional on `pos.inventory.kafka.enabled`, so the
silent-off concern of Q2a applies to the producer too and the same hardening (§5.4) covers `pos-inventory`.

### 4.4 Consumer and idempotency

`InventoryEventsListener` (existing, group `pos-accounting-inventory-events`) becomes a dispatcher on `eventType`: `inventory.scrap.posted` →
`InventoryShrinkagePostingService` (unchanged), `inventory.adjustment.posted` → new `InventoryAdjustmentPostingService`. Unknown types stay ignored without a mark, as today.

Three idempotency layers, as the scrap precedent:

1. Envelope `eventId` in `processed_events` — dedupes redelivery of the same outbox row; checked before any transaction is opened.
2. Posting key `INVENTORY_ADJUSTMENT_GL_POSTING:<adjustmentKind>:<adjustmentId>` in `IdempotencyService` (`isKeyProcessed` / `registerKey(key, journalEntryId)`) — dedupes
   a re-emitted fact (new `eventId`, same business event). The kind is in the namespace so two UUIDv7 id spaces can never collide.
3. Journal-entry `sourceEventId = UUID.nameUUIDFromBytes("INVENTORY_ADJUSTMENT:" + adjustmentKind + ":" + adjustmentId)`; `JournalEntryRepository.findBySourceEvent` gives
   the reverse lookup.

`journalEntryId` is the canonical posting reference (AD-011).

### 4.5 Business-time transaction date and the period gate

- `transactionDate = LocalDateTime.ofInstant(fact.occurredAt(), clock.getZone())` — business time, so redeliveries land in the same period and resolve the same
  effective-dated GL mapping.
- `JournalEntryService.postJournalEntry` applies `AccountingPeriodGate.assertPostingAllowed`: closed → `PERIOD_CLOSED`, hard-locked → `PERIOD_HARD_LOCKED`. **Ruling:** for a
  Kafka fact these are *not* permanent failures to be marked processed — they are operations conditions that must alert and be replayed. They propagate (no mark), the
  container retries, the record goes to `inventory.events.v1.dlq` (ADR-0044 §4), and is replayed after reopen or override. Same for `MAPPING_NOT_FOUND`. This is the
  existing precedent in `InventoryEventsListener` (l. 105–108).
- Posting timing is therefore **immediate on consumption, asynchronous, at-least-once**, never deferred to a batch.

### 4.6 Journal entry lines

Amount = `unitCost × abs(quantityDelta)` (no rounding beyond the journal-entry column scale, as the shrinkage precedent). Two lines, balanced, description carrying
`adjustmentKind`, `adjustmentId`, `reasonCode`, `sku`, `quantityDelta`, `unitCost`, `ledgerEntryId`.

| Case | Debit | Credit | Basis |
| --- | --- | --- | --- |
| **Loss** (`quantityDelta < 0`) | mapping key `ADJUSTMENT_LOSS` → **5100 Inventory Shrinkage** | mapping key `INVENTORY_ASSET` → **1300 Inventory** | Direct precedent: scrap shrinkage (`GLPostingServiceImpl.postInventoryShrinkage`, seed `INVENTORY_SHRINKAGE`); `event-type-reference.md` classes `COUNT_VARIANCE_OUT` / `ADJUSTMENT_OUT` with `SCRAP_OUT` as write-off / shrink. **Supported by precedent; no new mapping invented.** |
| **Gain** (`quantityDelta > 0`) | mapping key `INVENTORY_ASSET` → **1300 Inventory** | mapping key `ADJUSTMENT_GAIN` → **account not in the seeded chart** | The debit side is fixed by the cross-domain contract ("debit/credit Inventory") and by double entry (asset up = debit). The credit counter is what the contract leaves open ("credit/debit COGS or misc"). **Requires owner confirmation (D2).** Recommendation: seed `ADJUSTMENT_GAIN` → **5100** (credit the same shrinkage account, so count over/short nets in one account), because a count gain is overwhelmingly the reversal of a booked or about-to-be-booked shortage, and recognising it as income would gross up the P&L for a correction. Alternatives: 5000 COGS (the contract's other named option) or a new "4940 Inventory Count Gain" following the `REGISTER_OVER_SHORT` precedent (separate 6115 Cash Short / 4930 Cash Over). |

Posting category: **new `INVENTORY_ADJUSTMENT`** with three mapping keys `ADJUSTMENT_LOSS`, `ADJUSTMENT_GAIN`, `INVENTORY_ASSET` — the exact shape of `REGISTER_OVER_SHORT`
(short / over / clearing), which the accounting owner already accepted for a two-sided variance (G3, #1083). Scrap stays on `INVENTORY_SHRINKAGE` (D4): the two are distinct
documents with distinct reason taxonomies, and finance may later want scrap write-offs and count corrections on different accounts; the mapping-key model exists precisely
so that is a configuration change, not code. Both loss keys seed to 5100 by default. No accounting dimensions are asserted (the shrinkage precedent sets none;
[DIMENSION_SCHEMA.md](.business-rules/DIMENSION_SCHEMA.md) decisions are the owner's).

**A `COUNT_VARIANCE_IN` posts.** Both directions must reach the GL; a one-directional feed guarantees drift between the GL 1300 balance and inventory's J2 valuation, and
reconciliation (ADR-0044 §4, "duplication without reconciliation is not permitted") becomes impossible. Same for `ADJUSTMENT_IN`.

### 4.7 Failure classes under the amended ADR-0044 (2026-09-23, #2146)

| Outcome | Action |
| --- | --- |
| Applied | Post JE + `registerKey` + ingestion record + `markProcessed` **atomically** in the handler's `REQUIRES_NEW` transaction. |
| Duplicate `eventId` | Drop before any transaction (`existsById`). |
| Duplicate posting key (re-emit) | Handler returns without posting; `markProcessed` in the same handler transaction. |
| Unparsable / `DatabindException` | Permanent: log, `replica.payload.rejected++`, mark processed in a transaction of its own. |
| Uncosted (`unitCost == null` or `<= 0`) | Permanent business skip: never post a zero or indeterminate JE; `accounting.inventory.fact.skipped{eventType, reason=UNCOSTED}++`; ingestion record `SKIPPED` (§4.9); mark processed in its own transaction. |
| `PERIOD_CLOSED`, `PERIOD_HARD_LOCKED`, `MAPPING_NOT_FOUND`, `TransientDataAccessException`, unexpected | Propagate; no mark; container retry → DLQ + alert; replay after the operations fix. |

### 4.8 The uncosted rule and recovery

Identical to scrap: record-and-skip, never post. Recovery is **not** automatic and must not be: the fact carries the cost at posting time (ADR-0048 §3); a later cost is a
different fact. Because the skip path registers only `processed_events` (by `eventId`) and not the posting key, an owner-initiated re-emit with a fresh `eventId` *would*
post — but inventory would have to re-emit with the cost that applied at the original posting instant, which it cannot reconstruct after the fact under AVERAGE. So:

- the skip is terminal;
- the skipped count is the KPI for ADR-0048 IMP-002 compliance on the inventory side ("must not leave cost blank");
- an uncosted on-hand-affecting posting is an inventory data-quality defect to prevent upstream (receive with a price, configure a standard cost), not to repair in
  accounting. Whether inventory should refuse to *post* an uncosted count variance at all is an inventory-domain decision (D6).

### 4.9 The `AccountingEvent` ingestion read model (AD-007, story #184)

**Ruling: Kafka-consumed posting facts must produce an AD-007 ingestion record.** AD-007 makes accounting the owner of "persisted ingestion records for canonical
accounting events (status, errors, idempotency outcome, posting references)"; `processed_events` (eventId, processedAt) is not that record, and the two outcomes operators
most need to see — the uncosted skip and the posted JE link — are invisible without log access. Minimal, safe form (D5, because it touches the REST read model):

- At the end of the handler transaction write one `AccountingEvent` row: `eventType` = the fact's event type string (`inventory.adjustment.posted` /
  `inventory.scrap.posted`), `sourceSystem = "pos-inventory"`, `domainKeyId` = `adjustmentId` (or `scrapId`) — the filter `listAccountingEvents` already exposes —
  and `eventReference` set to the same value, `transactionDate` = business date,
  `payload` = the curated fact (AD-009 raw-payload policy applies), `journalEntryId` on success, `idempotencyOutcome = NEW | DUPLICATE_IGNORED`, `ingestionId` = envelope
  `eventId`.
- **Terminal states only.** Add `AccountingEventStatus.SKIPPED` (terminal, not retryable) with `failureReasonCode = UNCOSTED_FACT`; check the `accounting_event.status`
  column length and any check constraint in `V1__baseline_accounting.sql`. Do **not** write `FAILED` / `SUSPENDED` rows for Kafka facts: the retry scheduler and
  `retryAccountingEvent` select those and would re-run the fact through posting rule sets that do not exist. Propagated failures are DLQ-visible instead.
- This gives #184 its backend (`listAccountingEvents?eventType=inventory.adjustment.posted`, `getAccountingEvent`, `journalEntryId` link) with retry hidden, and gives
  the SDK a lookup route (§6) without a new endpoint.
- The backend enum (`RECEIVED / PROCESSING / PROCESSED / FAILED / SUSPENDED` + `SKIPPED`) is authoritative; #184 is rewritten to it.

### 4.10 Neighbouring facts

- **Manual `ADJUSTMENT_IN/OUT`** (`InventoryAdjustmentRequest`): same fact, same posting category, `adjustmentKind = MANUAL_ADJUSTMENT`. Physically and financially
  identical to a count variance.
- **`ProductValueChangedV1`** (`inventory.product-value.changed`): **must be consumed** — ADR-0048 §3 names "revaluation facts for governed cost corrections" in the
  contract surface, and a revaluation changes the carrying value of 1300 by `totalValueDelta` (signed, already computed by inventory, so accounting recomputes nothing).
  Today: producer live, **no consumer**, no seeded revaluation gain/loss account, no category. Separate issue (D7); the `RevaluationServiceImpl` javadoc claim that
  accounting consumes it is corrected or made true.
- **Scrap**: unchanged in treatment; its listener is migrated to the amended shape and gains the skip metric and the ingestion record like the adjustment fact.

## 5. Design

Minimal and consistent with the `ScrapPostedV1 → InventoryShrinkagePostingService` precedent.

### 5.1 `pos-domain-events`

- `InventoryAdjustedV1` per §4.2, with constructor validation; add to `DomainEventContractTest`.

### 5.2 `pos-inventory` (producer)

- `InventoryFactPublisher.recordInventoryAdjusted(InventoryAdjustedV1)` + `publishInventoryAdjustments` drained at `beforeCommit`, aggregate id `adjustmentId` (copy of the
  scrap emitter).
- `CycleCountAdjustmentServiceImpl.postAdjustmentToLedger`: after `ledgerEntry = ledgerPostingService.post(...)` (l. 370) call `inventoryFactPublisher.markEntry(ledgerEntry)`
  and `recordInventoryAdjusted(...)` built from the **saved** row (`getUnitCost()`, `getLedgerEntryId()`, `getEventType()`), `costSource` per the scrap rule,
  `occurredAt = postedAt`.
- `StockMovementServiceImpl.approveAdjustmentRequest`: the same after its `ledgerPostingService.post` (capture the posted entry instead of returning `toResponse(post(...))`
  inline).
- **Cost-of-gain correction (required):** stop passing `.unitCost(costAtTimeOfAdjustment)` on `COUNT_VARIANCE_*` entries so the costing engine values a gain at the current
  method cost and does not re-blend the average or overwrite the standard-cost memo (§2.1). `costAtTimeOfAdjustment` stays on the adjustment entity for the approval tier
  only. Without this, the fact's `unitCost` for a gain is a stale snapshot or an operator-supplied number, contrary to ADR-0048 §3.
- Producer test (Spring-backed, real transaction): the fact is queued only after a successful post; not on the zero-variance, `FAILED` or rejected paths; its `unitCost`
  equals the stamped ledger cost; `markEntry` snapshot facts are queued alongside. Contract IT mirroring `ScrapContractBehaviorIT` asserting the `event_outbox` row.
- Flag hardening for `pos.inventory.kafka.enabled` (§5.4).

### 5.3 `pos-accounting` (consumer, seed, listener shape)

- Seed (idempotent rows in `R__seed_reference_accounting.sql`, following the `INVENTORY_SHRINKAGE` and `REGISTER_OVER_SHORT` blocks): posting category
  `INVENTORY_ADJUSTMENT`; mapping keys `ADJUSTMENT_LOSS` → 5100, `INVENTORY_ASSET` → 1300, `ADJUSTMENT_GAIN` → **D2**. No new accounts unless D2 chooses a new gain account.
- `InventoryAdjustmentPostingService` (mirror of `InventoryShrinkagePostingService` plus the two-sided routing of `RegisterOverShortPostingService`): posting key,
  business-time date, resolve the accounts via `GLMappingResolver.resolveGLAccount("INVENTORY_ADJUSTMENT", key, date)`, sign-route, call a new
  `GLPostingService.postInventoryAdjustment(sourceEventId, adjustmentId, debitAccountId, creditAccountId, amount, transactionDate, description, null)` (a two-line poster
  like `postInventoryShrinkage`), `registerKey(key, journalEntryId)`.
- `InventoryEventsListener`: **migrate to the amended shape** — remove `@Transactional` from the listener method; parse, `existsById`, then
  `TransactionTemplate(PROPAGATION_REQUIRES_NEW)` executing `switch (eventType)` → posting service → ingestion record → `markProcessed`; `TransientDataAccessException`
  rethrown; `DatabindException` → counter + mark in its own transaction; uncosted → skip metric + `SKIPPED` ingestion record + mark in its own transaction. In-module
  template `LocationEventsListener`; pin with an `InventoryEventsListenerTransactionTest` copied from `LocationEventsListenerTransactionTest` (a `@Transactional` handler
  that throws, bare and inside an enclosing transaction).
- `OrderEventsListener` (still old shape): migrate in the same change with its own transaction test — same defect class as #2145 / #2146, same module.
- Metrics: `accounting.inventory.fact.skipped{eventType, reason}`; `accounting.inventory.fact.posted{eventType}`.
- Ingestion record per §4.9 (gated on D5) and `AccountingEventStatus.SKIPPED`.
- README: add the adjustment fact beside the shrinkage bullet; document the `INVENTORY_ADJUSTMENT` mapping keys; state that Kafka facts create ingestion records and are
  not REST-retryable. The OpenAPI surface changes only if the `SKIPPED` status or a new filter lands, in which case `API Artifacts Sync` runs.

### 5.4 Configuration hardening (D3)

1. Set `pos.accounting.kafka.enabled: true` and `pos.inventory.kafka.enabled: true` as literals in the modules' `application-alpha.yml` and `application-prod.yml`,
   replacing the `${POS_*_KAFKA_ENABLED:false}` default in those profiles. This removes the silent default only: Spring's property precedence and relaxed binding
   still let `POS_ACCOUNTING_KAFKA_ENABLED=false` (or `POS_INVENTORY_KAFKA_ENABLED=false`) in the environment override the profile value, so an operator can still
   turn the rails off. Item 2 is what turns that override from a silent state into a refused one.
2. Add a startup guard in `pos-accounting` (and `pos-inventory`) for non-`dev` profiles that reads the effective property after binding: `false`, however it got
   there, fails startup with a message naming the property (preferred), or at minimum marks the health indicator DOWN with an ERROR log.
3. Schedule the Phase 0.4 tier-1 flip (remove the `@ConditionalOnProperty` opt-in for domain flows, per ADR-0044 §4).

### 5.5 Rollout order

Consumer and seed first (the dispatcher ignores the new type harmlessly until the producer ships), then the producer, then the flag hardening. Facts emitted while a
consumer is absent are never posted — which is the whole point of D3.

### 5.6 Follow-ups outside this change

- `ProductValueChangedV1` consumer and its counter account (D7).
- ADR-0008 ("Inventory/Accounting cost maintenance", accepted 2026-01-13) described accounting maintaining last/average cost and pushing it to inventory,
  contradicting ADR-0048 §1 and ADR-0044 §6. Resolved (D8): [louisburroughs/durion#507](https://github.com/louisburroughs/durion/pull/507) supersedes it by
  ADR-0048, whose new §6 carries forward the cost types, the weighted-average formula and the authorization split.
- Re-scoping durion-moqui-frontend#184 for the Angular frontend against the real backend (D10).
- `InventoryAuditEvent` javadoc promises a Kafka forwarder that does not exist; correct it or remove the event once the fact exists.
- The platform convention is one `STORY_VALIDATION_CHECKLIST.md` per domain; accounting's had never existed, so its agent contract pointed at a missing file.
  [`.business-rules/STORY_VALIDATION_CHECKLIST.md`](.business-rules/STORY_VALIDATION_CHECKLIST.md) is authored with this spec, grounded in the `AD-###` decisions,
  the schema, error, permission and contract guides, and §4 here; the rules the guides leave undecided are listed there as open questions. The product agent's
  reference to `domains/catalog/.business-rules/STORY_VALIDATION_CHECKLIST.md` still dangles (the domain folder is `product`, and it has no checklist either).

### 5.7 Suggested issue split

1. `pos-domain-events` + `pos-inventory` producer (fact, `markEntry`, cost-of-gain fix, tests) — inventory domain.
2. `pos-accounting` consumer + seed + listener migration (`InventoryEventsListener`, `OrderEventsListener`) + metrics + ingestion record + `SKIPPED` — accounting domain.
3. Flag hardening in both modules (D3).
4. SDK assertions (§6).
5. Documentation and ADR corrections (durion).
6. Follow-up: `ProductValueChangedV1` consumer (D7).
7. Rewrite #184 against the Angular frontend and the real backend (D10).

## 6. Tests

**Backend (Spring-backed, real transaction manager):**

- Producer: fact queued only after a successful post; not on zero-variance, `FAILED` or rejected paths; `unitCost` equals the stamped ledger cost; outbox row present.
- Consumer: duplicate `eventId` delivery posts once; re-emit with a new `eventId` posts once; loss posts Dr 5100 / Cr 1300 = `abs(delta) × unitCost` dated `occurredAt`;
  gain posts Dr 1300 / Cr `ADJUSTMENT_GAIN`; uncosted writes `SKIPPED`, increments the metric and posts no JE; `PERIOD_CLOSED` propagates and leaves no `processed_events`
  row; the listener transaction test (bare and enclosing) passes for both inventory and order listeners.

**SDK integration (`durion-positivity-sdk`).** Prerequisite: a lookup from adjustment or scrap id to journal entry. Preferred route: the AD-007 ingestion record
(`listAccountingEvents?eventType=inventory.adjustment.posted&domainKeyId=<adjustmentId>`, both filters already exposed by the endpoint), which needs no new
endpoint. Fallback: a `sourceEventId` filter on
`listJournalEntries` (a controller change, so OpenAPI regeneration and `API Artifacts Sync`), since `findBySourceEvent` already exists.

- Suite E (cycle count): **E11** priced-receipt fixture; create + approve a negative variance; poll until an accounting event `PROCESSED` with `journalEntryId`; assert via
  `getJournalEntry` two lines, Dr 5100 = Cr 1300 = `abs(delta) × unitCost`, `transactionDate` = the `occurredAt` date. **E12** positive variance; assert Dr 1300 / Cr the
  `ADJUSTMENT_GAIN` account. **E13** uncosted SKU; assert an ingestion record `SKIPPED` / `UNCOSTED_FACT` and no JE. **E14** zero recomputed variance (conflict path);
  assert no accounting event.
- Manual adjustment suite: the same three cases for `InventoryAdjustmentRequest`.
- Scrap suite: today it has **no** accounting assertion; add the posted and the uncosted cases.
- Simulator (`InventoryMaintenanceSimulator.runCycleCount`): at the end of the accelerated year assert Σ(posted adjustment JE amounts) = Σ(inventory ledger `COUNT_VARIANCE_*`
  value) for costed SKUs — the reconciliation ADR-0044 §4 demands.

## 7. Open decisions for the platform owner

| # | Decision | Recommendation |
| --- | --- | --- |
| D1 | One generic `InventoryAdjustedV1` for cycle-count and manual adjustments, or two facts? | **One**, with `adjustmentKind` as a field (§4.2). |
| D2 | Counter account for a count / adjustment **gain** (`ADJUSTMENT_GAIN` mapping key)? | **Credit 5100 Inventory Shrinkage** (net over/short in one account); alternatives 5000 COGS or a new "4940 Inventory Count Gain" per the `REGISTER_OVER_SHORT` precedent. *Requires owner confirmation — not in the seeded chart, not fixed by any document.* |
| D3 | Make `pos.accounting.kafka.enabled` / `pos.inventory.kafka.enabled` profile-level `true` in alpha and prod now and fail startup when off, ahead of the Phase 0.4 tier-1 flip? | **Yes, now** — a posting consumer must never be silently absent. |
| D4 | New posting category `INVENTORY_ADJUSTMENT` (loss / gain / asset keys) rather than widening `INVENTORY_SHRINKAGE`? | **New category**, both loss keys seeded to 5100; keeps scrap and count corrections separately mappable. |
| D5 | Should Kafka-consumed posting facts write `AccountingEvent` ingestion records (terminal states only, new `SKIPPED` status)? | **Yes** — it is what AD-007 requires and the only way #184 and the uncosted skip become visible without logs. |
| D6 | Should inventory refuse to post an uncosted (`costSource = NONE`) count variance or adjustment, or post it and let accounting skip? | **Post and skip in v1, with the skip counted**; tighten to refuse once ADR-0048 IMP-002 is enforced upstream (inventory-domain call). |
| D7 | Build the `ProductValueChangedV1` revaluation consumer now, and against which counter account? | **Separate issue, next wave**; account decision needed — nothing seeded, "COGS or misc" is the only guidance. *Requires owner confirmation.* |
| D8 | Mark ADR-0008 superseded by ADR-0048 for cost ownership? | **Resolved** — option 1 taken in [louisburroughs/durion#507](https://github.com/louisburroughs/durion/pull/507): ADR-0008 superseded, ADR-0048 §6 records what is retired and carries forward the cost types, the weighted-average formula and the authorization split as ADR-level rules. |
| D9 | Reason-code-based account routing (e.g. THEFT vs COUNT_ERROR to different accounts) in v1? | **No** — carry `reasonCode` on the JE description only; add a `reasonCode` mapping-key dimension later if finance asks. |
| D10 | Re-scope #184 to cover both inventory facts in Angular, blocked on D5? | **Yes**; retry stays unsupported for Kafka facts (DLQ replay instead). |

## 8. Documentation changes

Made with this specification (durion, branch `claude/determined-wright-9s457t`):

- [`../inventory/comp-vs-pos-inventory-comparison.md`](../inventory/comp-vs-pos-inventory-comparison.md) §4 "Scrap": the "no scrap document, endpoint, or workflow" claim
  replaced with the delivered D1 workflow and its GL consequence; the remaining gap is the adjustment fact.
- [`../inventory/plan-odoo-parity-pos-inventory.md`](../inventory/plan-odoo-parity-pos-inventory.md) D2: marked delivered (#1043, `InventoryShrinkagePostingService`);
  J3: marked half-delivered (cost source aligned, fact missing) with a pointer here.
- [`../inventory/SPEC-pos-inventory-odoo-parity.md`](../inventory/SPEC-pos-inventory-odoo-parity.md) J3: pointer here.
- [`.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`](.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md) §3.1 inventory rows: `inventory.inventoryAdjustment`
  renamed `inventory.adjustment.posted` with the §4.2 payload and the §4.6 posting pattern; `inventory.scrap.posted` (implemented) and `inventory.product-value.changed`
  (producer live, consumer missing) added.
- [`../inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md`](../inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md) required event types: the same
  rename and additions.
- [`.business-rules/AGENT_GUIDE.md`](.business-rules/AGENT_GUIDE.md) "Events / Integrations": the three inventory facts and their consumers added.
- [`.business-rules/STORY_VALIDATION_CHECKLIST.md`](.business-rules/STORY_VALIDATION_CHECKLIST.md): the accounting story-validation checklist, previously
  missing, authored and linked from the accounting index.

Landed separately: ADR-0008 superseded by ADR-0048 (D8) in [louisburroughs/durion#507](https://github.com/louisburroughs/durion/pull/507).

Still to do when the design is accepted: CAP-049 story 184 (durion-moqui-frontend, out of this workspace) re-scoped per D10; `pos-accounting/README.md` and
`pos-inventory/README.md` with the implementation.
