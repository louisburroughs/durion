# ADR-0049: Supplier Integration Module Boundary and Event Contracts (pos-supplier)

**Status:** PROPOSED — revised 2026-08-10 (PRCR-003/004)  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain  
**Affected Issues:** durion#372–#379 (CAP-317..CAP-324), durion#371

---

## Context

- **Current State**: The platform has no outbound supplier connectivity. Tire manufacturers (Michelin first) expose supplier APIs over the EDIWheel standard in multiple norm
  generations (A2.5/B-series/C1.x XML, C1.2 JSON) plus vendor-proprietary APIs (Michelin S2S workorder authorization, OAuth2). Specs are collected in `docs/ediwheel/`.
- **The Problem**: Supplier capabilities (price catalog, stock inquiry, ordering, invoices, shipment, fleet workorder authorization) touch many domains — pricing, catalog,
  inventory, order, accounting, workexec. Without a single owner, vendor wire formats, credentials, and retry logic would leak into every domain module.
- **Drivers**: Reuse across vendors and deployments; per-deployment configurability; ADR-0044 event-only domain walls (topics `{domain}.events.v1` / `{domain}.commands.v1`,
  envelope with unversioned `eventType` + `schemaVersion`); auditability of commercial exchanges.
- **Scope**: Backend module boundary, canonical model ownership, and the supplier event contract set. Full architecture:
  `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`.

---

## Decision

### 1. Single owning module

**Decision:** ✅ **Resolved** — All outbound supplier connectivity lives in one new **domain** module, `pos-supplier` (added to the ADR-0044 §1 Domain classification). It
owns: the vendor-neutral canonical model (`SupplierPurchaseOrder` transmission state, `SupplierStockInquiry`, `SupplierPriceCatalogEntry`, `SupplierInvoice`,
`SupplierShipmentEvent`, `SupplierWorkorderAuthorization`), vendor profiles and endpoint bindings, protocol adapters/codecs, the exchange audit log, and supplier-facing
orchestration (outbox, retries, schedules). No other module may hold vendor credentials, speak a vendor wire format, or call a vendor endpoint.

### 2. What pos-supplier does not own

**Decision:** ✅ **Resolved** — Business aggregates stay with their domains: the purchase-order aggregate is **pos-order**; sell prices are **pos-price**; product identity is
**pos-catalog**; owned inventory is **pos-inventory**; AP voucher posting is **pos-accounting**; workorder state is **pos-workorder** (workexec domain). `pos-supplier` holds
transmission/exchange state only, and vendor references (`SupplierOrderNumber`, `DocumentID`) are attributes, never keys (ADR-0013/0027).

### 3. Event contract set

**Decision:** ✅ **Resolved** — Cross-module integration is event-only per ADR-0044: **topics** follow the platform convention (`supplier.commands.v1` for requests to
pos-supplier, `supplier.events.v1` for facts/results it publishes), while the **event type** is an unversioned envelope field with `schemaVersion` carrying payload evolution.
Envelope and payload DTOs live in `pos-domain-events`; producers use the transactional outbox and consumers are idempotent (ADR-0044 §4). Initial contract set:

| Topic                  | eventType                          | schemaVersion | Producer      | Consumer(s)            | Kind                            |
| ---------------------- | ---------------------------------- | ------------- | ------------- | ---------------------- | ------------------------------- |
| `supplier.commands.v1` | `supplier.order.requested`         | 1             | pos-order     | pos-supplier           | command                         |
| `supplier.commands.v1` | `supplier.workorderauth.requested` | 1             | pos-workorder | pos-supplier           | command                         |
| `supplier.events.v1`   | `supplier.order.confirmed`         | 1             | pos-supplier  | pos-order              | result                          |
| `supplier.events.v1`   | `supplier.order.rejected`          | 1             | pos-supplier  | pos-order              | result                          |
| `supplier.events.v1`   | `supplier.orderstatus.changed`     | 1             | pos-supplier  | pos-order              | fact                            |
| `supplier.events.v1`   | `supplier.pricecatalog.updated`    | 1             | pos-supplier  | pos-price, pos-catalog | fact                            |
| `supplier.events.v1`   | `supplier.stockreport.updated`     | 1             | pos-supplier  | pos-inventory          | fact                            |
| `supplier.events.v1`   | `supplier.invoice.received`        | 1             | pos-supplier  | pos-accounting         | fact                            |
| `supplier.events.v1`   | `supplier.shipment.event`          | 1             | pos-supplier  | pos-order              | fact (append-only; PO timeline) |
| `supplier.events.v1`   | `supplier.workorderauth.granted`   | 1             | pos-supplier  | pos-workorder          | result                          |
| `supplier.events.v1`   | `supplier.workorderauth.denied`    | 1             | pos-supplier  | pos-workorder          | result                          |

Additional inputs pos-supplier **consumes from other domains' topics**: `workorder.completed` on `workorder.events.v1` triggers the fleet completion-approval call (CAP-323)
— the approval flow needs no dedicated command event. If receiving flows later need shipment events beyond the pos-order timeline, the consumer is added to this table by
amendment with an exact module name — "receiving consumers" is not a valid ACL subject.

Payload changes within a `schemaVersion` are additive-only; breaking changes increment `schemaVersion` (and, where topic compatibility breaks, take a new topic version with
dual-publish), per ADR-0044 §3.

### 4. Synchronous surface

**Decision:** ✅ **Resolved** — Exactly one synchronous cross-module read exists: `SupplierStockService` live stock inquiry, callable by **pos-catalog** (Product Detail
composition, which already serves product-detail display from its `ext_*` replicas) and **pos-order** (procurement), governed by the ADR-0044 amendment dated 2026-08-10. The
amendment requires class-level enforcement — the allowlisted edge is a named stock-inquiry client, not a whole module-to-module grant — encoded in `DomainWallsTest`.
Admin/profile/audit controllers are frontend-facing via the gateway (ADR-0011/0014), not cross-module surfaces.

---

## Consequences

**Positive:** vendor onboarding is contained to `pos-supplier` adapters + configuration; domains consume stable canonical events; one place audits every commercial exchange.
**Negative / accepted:** `pos-supplier` becomes a coupling point for supplier-facing flows; batch consumers must tolerate event lag; an extra hop (event) between business
intent and vendor transmission.
**Follow-ups:** ADR-0050 (vendor profile configuration), ADR-0051 (adapter/codec versioning), ADR-0052 (outbound idempotency); PRICAT precedence ADR after durion#371.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`
- ADR-0044 (event-only domain walls, incl. 2026-08-10 amendment), ADR-0026, ADR-0013/0027
