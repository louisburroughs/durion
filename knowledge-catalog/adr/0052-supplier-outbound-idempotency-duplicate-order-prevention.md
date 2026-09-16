---
type: ADR
title: 'ADR-0052: Supplier Outbound Idempotency and Duplicate-Order Prevention'
description: CAP-320 transmits purchase orders to vendors over EDIWheel order creation. Vendor order APIs are not idempotent by default; a timeout after send is ambiguous (the vendor may or may not have accepted the order).
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md'
tags: [adr, order]
status: stable
sources: [docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-08-10T16:37:38-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md) — `docs/adr/0052-supplier-outbound-idempotency-duplicate-order-prevention.adr.md`

**Status:** Accepted since 2026-08-10
**Related:** [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md), [ADR-0040](/adr/0040-roles-jwt-permission-governance-policy.md), [ADR-0044](/adr/0044-platform-event-only-domain-walls.md), [ADR-0049](/adr/0049-supplier-integration-module-boundary.md)
