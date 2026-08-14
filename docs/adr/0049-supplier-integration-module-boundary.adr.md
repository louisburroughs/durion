# ADR-0049: Supplier Integration Module Boundary and Event Contracts (pos-supplier)

**Status:** ACCEPTED 2026-08-10 — revised for PRCR-003/004; amended 2026-08-14 (§1/§3 shipment tracking withdrawn, see Amendments)  
**Date:** 2026-08-10  
**Deciders:** Architecture, Backend Lead, Positivity (Integrations) Domain  
**Affected Issues:** durion#372–#379 (CAP-317..CAP-324), durion#371, durion-positivity-backend#1313

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
`SupplierWorkorderAuthorization`; `SupplierShipmentEvent` was withdrawn 2026-08-14, see Amendments), vendor profiles and endpoint bindings, protocol adapters/codecs, the
exchange audit log, and supplier-facing orchestration (outbox, retries, schedules). No other module may hold vendor credentials, speak a vendor wire format, or call a
vendor endpoint.

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
| `supplier.events.v1`   | `supplier.workorderauth.granted`   | 1             | pos-supplier  | pos-workorder          | result                          |
| `supplier.events.v1`   | `supplier.workorderauth.denied`    | 1             | pos-supplier  | pos-workorder          | result                          |

Additional inputs pos-supplier **consumes from other domains' topics**: `workorder.completed` on `workorder.events.v1` triggers the fleet completion-approval call (CAP-323)
— the approval flow needs no dedicated command event.

The table listed a tenth event, `supplier.shipment.event`, until 2026-08-14; it was **withdrawn before any implementation** because pos-supplier has no way to learn shipment
milestones (see Amendments). Any future shipment fact enters this table by amendment, with a producer that can actually source it and an exact consumer module name —
"receiving consumers" is not a valid ACL subject.

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

---

## Amendments

### 2026-08-14 — Shipment tracking is withdrawn: Durion is not a party to that exchange

**What changed.** §1 listed `SupplierShipmentEvent` in the canonical model and §3 listed `supplier.shipment.event` as an append-only fact from pos-supplier to the pos-order
purchase-order timeline. Both are removed. There is no shipment capability in `pos-supplier`, and no shipment event on `supplier.events.v1`.

**Why the contract could not be honoured.** CAP-322 (durion#377) paired shipment tracking with the stock report, and the SPI written for it under CAP-317 —
`SupplierShipmentTrackingPort.fetchTrackingEvents(supplierRef, partyContext, orderReference)` — encodes the assumption this ADR's table encodes too: that the vendor holds
tracking we can read. The referenced spec, `docs/ediwheel/ShipmentTrackingOAS_v1.yaml`, declares exactly one operation — `POST /shipment-tracking`, a **write** whose body
(`eventCode`, `carrier`, `eventSender`, `shipFrom`, `shipTo`) is a notice the sender announces. There is no GET and no query by order reference. The architecture document's
own port catalog hedged this, listing the port's operations as `sendShipmentEvents` / `fetchTracking` without deciding which Durion performs; the answer is neither.

**The decision, from the Positivity (Integrations) domain (durion-positivity-backend#1313).** EDIWheel shipment tracking is an exchange between **logistics providers and
suppliers**. A service provider is not a party to it in either direction: Durion is not the carrier announcing movements, and the norm gives the ordering side nothing to read
back. So the missing read is not a gap in the documentation we hold — there is nothing for a vendor to expose to us.

**What this is not.** It is not the inbound-posture question. The clarification considered whether the vendor might instead **push** notices to Durion, which would have
contradicted §12 decision 7 of the architecture document ("Inbound flows. None.") and required an authentication, replay-protection and idempotency design before any code.
That path is closed by the same reasoning rather than deferred: we would not be a recipient of these notices either. §12 decision 7 stands unchanged and unqualified.

**Consequences.**

- `pos-supplier` drops `SupplierShipmentTrackingPort`, `SupplierShipmentService`, `ShipmentEventView`, `SupplierShipmentEvent` and the `SHIPMENT_TRACKING` capability key;
  Flyway V12 removes the key from the endpoint-binding CHECK constraint, so the admin API now rejects it with `SUPPLIER_UNKNOWN_CAPABILITY`
  (durion-positivity-backend#1317). All of it was scaffolding — no codec was ever registered, so no exchange ran and no data is lost.
- **pos-order loses nothing**, because it never gained anything: no consumer for `supplier.shipment.event` was ever built, and the purchase-order timeline has no shipment
  section to unwind. Order lifecycle facts (`supplier.orderstatus.changed`) are unaffected and remain the vendor-sourced signal on that timeline.
- CAP-322 is delivered by its stock-report half alone (durion-positivity-backend#1228, #1314); its shipment half is closed as not-applicable rather than deferred.

**If shipment visibility is wanted later**, it comes from a source that actually has it — a carrier API, a Michelin S2S operation, a freight aggregator — and enters as a
new capability with its own spec, its own port and a new row in §3. It is not a revival of this key, and this amendment should not be read as a decision that Durion never
tracks shipments; it is a decision that EDIWheel is not where that data lives.

## References

- `docs/architecture/integration/SUPPLIER_INTEGRATION_EDIWHEEL_ARCHITECTURE.md`
- ADR-0044 (event-only domain walls, incl. 2026-08-10 amendment), ADR-0026, ADR-0013/0027
