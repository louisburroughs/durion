---
type: ADR
title: 'ADR-0049: Supplier Integration Module Boundary and Event Contracts (pos-supplier)'
description: The platform has no outbound supplier connectivity. Tire manufacturers (Michelin first) expose supplier APIs over the EDIWheel standard in multiple norm generations (A2.5/B-series/C1.x XML, C1.2 JSON) plus vendor-proprie...
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0049-supplier-integration-module-boundary.adr.md'
tags: [adr, events]
status: stable
sources: [docs/adr/0049-supplier-integration-module-boundary.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-09-15T21:10:50-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0049-supplier-integration-module-boundary.adr.md) — `docs/adr/0049-supplier-integration-module-boundary.adr.md`

**Status:** Accepted since 2026-08-10
**Related:** [ADR-0011](/adr/0011-api-gateway-security-architecture.md), [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md), [ADR-0026](/adr/0026-service-contract-boundary-policy.md), [ADR-0044](/adr/0044-platform-event-only-domain-walls.md), [ADR-0048](/adr/0048-inventory-owned-valuation-configurable-costing-method.md), [ADR-0050](/adr/0050-supplier-vendor-profile-configuration.md), [ADR-0051](/adr/0051-supplier-protocol-adapter-versioning.md), [ADR-0052](/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.md)
