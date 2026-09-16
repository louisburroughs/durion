---
type: Implementation Reference
title: Accounting Implementation Reference
description: Reconciled bill matching, vendor-bill event ingestion, local and Kafka outboxes, and schema navigation based on the backend source.
status: reference
reviewed: 2026-09-16
---

# Accounting Implementation Reference

This reference reconciles the older backend documentation against backend commit `e5d7ec82e` on 2026-09-16.
It describes inspected implementation; it does not certify runtime behavior or change domain policy.
Start with the [accounting index](index.md) for authority and document status.

## Bill matching

The [vendor-bill service][vendor-bills] persists bill lines and PO references, calculates candidate confidence,
persists ambiguous candidates, and supports manual candidate selection. The two historical bill-matching reports
overlap; their old roadmap is not a list of work still outstanding.

| Score component | Current calculation |
| --- | --- |
| Amount | 40 points when the difference is within 10% |
| Products | Rounded `30 × Jaccard(product-ID sets)` |
| Date | 20 points within 7 days; 10 within 30 days |
| Purchase order | 5 points for a PO reference being present, not an exact PO match |

The maximum is **95**. A single qualified candidate scoring at least 70 is HIGH; a single candidate scoring
50–69 is MEDIUM. Multiple candidates scoring at least 50 produce AMBIGUOUS, even when one scores higher.
For `[A, B, C]` versus `[A, B, D]`, Jaccard similarity is `2 / 4 = 0.5`, yielding 15 product points.

Confidence alone does not approve a bill. Further checks require equal line counts, quantity differences within
0.1%, and unit-price and overall-total differences within 5%. These checks pair lines by list position.
Exact PO matching and product-based line pairing are limitations of the inspected implementation; changing them
requires a separate behavior change.

Historical sources:

- [Implementation summary](archive/implementation/BILL_MATCHING_IMPLEMENTATION_SUMMARY.md)
- [Improvements and roadmap](archive/implementation/BILL_MATCHING_IMPROVEMENTS.md)

## Vendor-bill events and GL ingestion

The [vendor-bill service][vendor-bills] publishes `VendorBillGLPostingEvent` directly through its application event publisher.
The [handler][vendor-handler] is implemented: it converts the payload, preserves organization/dimension fields,
and calls `EventIngestionService.submitEvent`. The [ingestion service][ingestion] validates, deduplicates,
and stores an accounting event in `RECEIVED` state. Acceptance into ingestion is not proof that a journal entry was posted.

This path uses ordinary `@EventListener` / `@Transactional` handling. The inspected accounting module does not
configure asynchronous dispatch for it, and the vendor-bill event is not registered in `OutboxProcessor`.
Do not rely on the old report's asynchronous delivery or bill-success-despite-handler-failure claims.
This local dispatch also does not replace the cross-domain event channel required by
[ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md).

Historical source: [GL posting event implementation](archive/implementation/GL_POSTING_EVENT_IMPLEMENTATION.md).

## Two separate outboxes

| Path | Implementation | Delivery and retry behavior |
| --- | --- | --- |
| Local Spring events | `event_outbox`, [OutboxServiceImpl][local-writer], [OutboxProcessor][local-processor] | Dispatches registered in-process events; stops retrying after five failures. Processing can continue to later events, so creation-time ordering is not a strict FIFO guarantee. |
| Kafka domain facts | `kafka_event_outbox`, [OutboxEventWriter][kafka-writer], [OutboxPublisher][kafka-publisher] | Writes events transactionally and publishes with broker acknowledgement. Stops a batch at the first failure and retries on later polls; consumers must tolerate duplicate delivery. |

Both global outbox tables carry the producing tenant as row data. The local processor binds that tenant during
dispatch; the Kafka publisher propagates it in the record header. See
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md) for tenant isolation and
[ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md) for domain communication rules.

The Kafka path is conditional on `pos.accounting.kafka.enabled`. Its invoice fact publication is described in the
[module README][module-readme]. The old report's `event_outbox` schema, migration numbers, retry guarantees and
production-readiness label must not be applied to the Kafka outbox or to the current tenant-aware schema.

Historical source: [original outbox report](archive/implementation/OUTBOX_PATTERN.md).

## Schema and migration references

The reviewed [active migration directory][migrations] contains:

- `V1__baseline_accounting.sql`
- `V2__seed_accounting.sql`
- `V3__outbox_tenant_id.sql`
- `R__seed_reference_accounting.sql`

Use these files for the physical schema, the [domain model](.business-rules/DOMAIN_MODEL.md) for conceptual entities,
and the backend [tenancy schema guide][tenancy] and [operations runbook][operations] for current migration/reset procedures.
The old baseline name `V1__baseline_accounting_schema.sql` is obsolete.

The [archived ERD](archive/pos-accounting-erd.md) has 33 tables, compared with 62 in the reviewed baseline.
It omits tenant columns and 30 current tables and includes a removed `reconciliation` table.
The [archived reset plan](archive/flyway-baseline-reset-plan.md) records an earlier flattening effort and is not a current execution recipe.

## Conceptual bookkeeping and packaged RAG

[Double-entry bookkeeping](reference/de-bookkeeping-rag.md) is explanatory material. Its example accounts,
permissions and events do not replace the [posting rule schema](.business-rules/POSTING_RULES_SCHEMA.md),
[permission taxonomy](.business-rules/PERMISSION_TAXONOMY.md), or accepted ADRs.
In particular, [ADR-0048](../../docs/adr/0048-inventory-owned-valuation-configurable-costing-method.adr.md)
governs inventory valuation ownership; accounting examples do not grant accounting a second valuation engine.

The [packaged MCP RAG resource][packaged-rag] is a separately maintained runtime artifact loaded from the classpath.
It has already diverged from the former backend document. This documentation reconciliation preserves its bytes
and loading configuration; any content synchronization requires a separate review of the retrieval corpus.

[vendor-bills]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/service/VendorBillServiceImpl.java
[vendor-handler]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/handler/VendorBillGLPostingEventHandler.java
[ingestion]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/service/EventIngestionServiceImpl.java
[local-writer]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/service/OutboxServiceImpl.java
[local-processor]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/service/OutboxProcessor.java
[kafka-writer]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/config/OutboxEventWriter.java
[kafka-publisher]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/src/main/java/com/positivity/accounting/internal/config/OutboxPublisher.java
[module-readme]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/README.md
[migrations]: https://github.com/louisburroughs/durion-positivity-backend/tree/main/pos-accounting/src/main/resources/db/migration
[tenancy]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/docs/TENANCY_SCHEMA.md
[operations]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/docs/OPERATIONS_RUNBOOK.md
[packaged-rag]: https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-mcp-server/src/main/resources/rag/de-bookkeeping-rag.md
