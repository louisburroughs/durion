# ADR-0049: Supplier Integration Module Boundary and Event Contracts (pos-supplier)

**Status:** PROPOSED
**Date:** 2026-08-10
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain
**Affected Issues:** durion#372–#379 (CAP-317..CAP-324), durion#371

---

## Context

- **Current State**: The platform has no outbound supplier connectivity. Tire manufacturers (Michelin first) expose supplier APIs over the EDIWheel standard in multiple norm generations (A2.5/B-series/C1.x XML, C1.2 JSON) plus vendor-proprietary APIs (Michelin S2S workorder authorization, OAuth2). Specs are collected in `docs/ediwheel/`.
- **The Problem**: Supplier capabilities (price catalog, stock inquiry, ordering, invoices, shipment, fleet workorder authorization) touch many domains — pricing, catalog, inventory, order, accounting, workexec. Without a single owner, vendor wire formats, credentials, and retry logic would leak into every domain module.
- **Drivers**: Reuse across vendors and deployments; per-deployment configurability; ADR-0044 event-only domain walls; auditability of commercial exchanges.
- **Scope**: Backend module boundary, canonical model ownership, and the supplier event contract set. Full architecture: `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`.

---

## Decision

### 1. Single owning module

**Decision:** ✅ **Resolved** — All outbound supplier connectivity lives in one new **domain** module, `pos-supplier`. It owns: the vendor-neutral canonical model (`SupplierPurchaseOrder` transmission state, `SupplierStockInquiry`, `SupplierPriceCatalogEntry`, `SupplierInvoice`, `SupplierShipmentEvent`, `SupplierWorkorderAuthorization`), vendor profiles and endpoint bindings, protocol adapters/codecs, the exchange audit log, and supplier-facing orchestration (outbox, retries, schedules). No other module may hold vendor credentials, speak a vendor wire format, or call a vendor endpoint.

### 2. What pos-supplier does not own

**Decision:** ✅ **Resolved** — Business aggregates stay with their domains: the purchase-order aggregate is **pos-order**; sell prices are **pos-price**; product identity is **pos-catalog**; owned inventory is **pos-inventory**; AP voucher posting is **pos-accounting**; workorder state is **workexec**. `pos-supplier` holds transmission/exchange state only, and vendor references (`SupplierOrderNumber`, `DocumentID`) are attributes, never keys (ADR-0013/0027).

### 3. Event contract set

**Decision:** ✅ **Resolved** — Cross-module integration is event-only per ADR-0044 (envelope and DTOs in `pos-domain-events`; transactional outbox; idempotent consumers). Initial contract set:

| Topic/event | Producer → Consumer | Kind |
| --- | --- | --- |
| `supplier.order.requested.v1` | pos-order → pos-supplier | command |
| `supplier.order.confirmed.v1` / `supplier.order.rejected.v1` | pos-supplier → pos-order | result |
| `supplier.orderstatus.changed.v1` | pos-supplier → pos-order | fact |
| `supplier.pricecatalog.updated.v1` | pos-supplier → pos-price / pos-catalog | fact |
| `supplier.stockreport.updated.v1` | pos-supplier → pos-inventory | fact |
| `supplier.invoice.received.v1` | pos-supplier → pos-accounting | fact |
| `supplier.shipment.event.v1` | pos-supplier → order/receiving consumers | fact (append-only) |
| `supplier.workorderauth.granted.v1` / `.denied.v1` | pos-supplier → workexec | result |

Payload changes within a version are additive-only; breaking changes take a new topic version with dual-publish, per ADR-0044 §3.

### 4. Synchronous surface

**Decision:** ✅ **Resolved** — Exactly one synchronous cross-module read exists: `SupplierStockService` live stock inquiry, callable by the positivity Product Detail composition and pos-order procurement, governed by the ADR-0044 amendment dated 2026-08-10 and enforced in `DomainWallsTest`. Admin/profile/audit controllers are frontend-facing via the gateway (ADR-0011/0014), not cross-module surfaces.

---

## Consequences

**Positive:** vendor onboarding is contained to `pos-supplier` adapters + configuration; domains consume stable canonical events; one place audits every commercial exchange.
**Negative / accepted:** `pos-supplier` becomes a coupling point for supplier-facing flows; batch consumers must tolerate event lag; an extra hop (event) between business intent and vendor transmission.
**Follow-ups:** ADR-0050 (vendor profile configuration), ADR-0051 (adapter/codec versioning), ADR-0052 (outbound idempotency); PRICAT precedence ADR after durion#371.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`
- ADR-0044 (event-only domain walls, incl. 2026-08-10 amendment), ADR-0026, ADR-0013/0027
