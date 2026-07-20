# Research Gate R-T2 — Invoice-Lifecycle Event Inventory (Wave 3 / story T5b)

## Headline
Plan assumption CONFIRMED and stronger: `InvoiceUpdatedV1` is the ONLY invoice domain event that exists.
pos-invoice does NOT emit separate created/finalized/issued/cancelled/voided domain events. Every state
transition re-emits `invoice.invoice.updated` (`InvoiceUpdatedV1`), distinguished only by `status`. So
adding `taxBreakdown[]` to `InvoiceUpdatedV1` rides EVERY lifecycle transition — no sibling events to patch.

## Event inventory
| Event | File:line | Trigger | Tax field | Version |
|---|---|---|---|---|
| InvoiceUpdatedV1 (record) | pos-domain-events/.../invoice/InvoiceUpdatedV1.java:23 | every invoice mutation (create DRAFT, adjust, finalize, revert, GL post, GL fail) | scalar `@Nullable BigDecimal tax` :32 | V1 (SCHEMA_VERSION=1 :39; EVENT_TYPE="invoice.invoice.updated" :38) |
| BillingRulesUpdatedV1 | pos-domain-events/.../invoice/BillingRulesUpdatedV1.java:23 | billing-rules row change | NONE | V1 |
| InvoiceFinalizedEvent | pos-invoice/.../internal/dto/InvoiceFinalizedEvent.java:25 | in-process Spring ApplicationEvent DRAFT->FINALIZED (InvoiceFinalizationServiceImpl:340) | NONE (scalar grandTotal :60) | not a domain/Kafka event |

## Scalar tax vs none
- Carries scalar tax: InvoiceUpdatedV1 ONLY (:32)
- None: BillingRulesUpdatedV1, internal InvoiceFinalizedEvent

## Confirm "only InvoiceUpdatedV1 carries tax": CONFIRMED. Single scalar, no breakdown. No *V2, no taxBreakdown/TaxLine on any event today.

## Finalized/issued events: NO dedicated event. All transitions -> publishInvoiceUpdated -> InvoiceUpdatedV1.
Call sites (pos-invoice src/main):
- create DRAFT: InvoiceServiceImpl.java:216
- add adjustment: InvoiceServiceImpl.java:166
- finalize DRAFT->FINALIZED: InvoiceFinalizationServiceImpl.java:169
- revert FINALIZED->DRAFT: InvoiceFinalizationServiceImpl.java:247
- GL post ->POSTED: InvoiceFinalizedEventHandler.java:89
- GL fail ->ERROR: InvoiceFinalizedEventHandler.java:103
InvoiceStatus has NO CANCELLED/VOIDED — lifecycle DRAFT/FINALIZED/POSTED/ERROR; void=revert to DRAFT.
=> additive taxBreakdown[] on InvoiceUpdatedV1 covers "must ride finalized/issued" with ONE field add,
   provided publisher (InvoiceEventPublisher.publishInvoiceUpdated :45-58) is populated on every path
   and finalize path has non-null tax at emit.

## Consumers
pos-accounting InvoiceEventsListener (:34): @KafkaListener invoice.events.v1, group pos-accounting-invoice-events.
Filters eventType==InvoiceUpdatedV1.EVENT_TYPE (:54). Idempotent via processed_events; stale-drop via aggregateVersion.
Persists into ext_invoice replica (applyInvoiceUpdate :82-116): stores scalar tax (:109).
=> taxBreakdown[] needs a new persistence sink here (new column/child table); no breakdown sink exists today.
Second consumer: pos-workorder InvoiceEventsListener also consumes invoice.events.v1 — account for if breakdown must reach workorder.

## V2/dual-publish precedent: NONE in code. Only docs mention (DomainTopics.java:13-14).
Governing pattern ADR-0044 §3 (docs/adr-0044-event-only-domain-walls.md:108-109): additive-only stays on V1.
=> @Nullable taxBreakdown[] added to InvoiceUpdatedV1 = additive, stays V1, NO V2/dual-publish needed.
   accounting listener uses treeToValue (:83) — tolerates unknown/absent fields. V2 only if breaking (required field / rename / remove tax).
