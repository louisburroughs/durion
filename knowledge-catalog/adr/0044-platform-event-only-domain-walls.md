---
type: ADR
title: 'ADR-0044: Event-Only Domain Walls and Module Communication Policy'
description: Backend domain modules are coupled by ~24 synchronous REST clients across 10 modules (full call matrix in durion-positivity-backend/docs/module-coupling/issue-823-event-only-domain-walls-assessment.md).
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0044-platform-event-only-domain-walls.adr.md'
tags: [adr, events, platform]
status: stable
sources: [docs/adr/0044-platform-event-only-domain-walls.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-09-15T21:10:50-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0044-platform-event-only-domain-walls.adr.md) — `docs/adr/0044-platform-event-only-domain-walls.adr.md`

**Status:** Accepted since 2026-07-08

**Related:**

* [ADR-0006](/adr/0006-workexec-domain-ownership-boundaries.md)
* [ADR-0009](/adr/0009-backend-domain-responsibilities-guide.md)
* [ADR-0011](/adr/0011-api-gateway-security-architecture.md)
* [ADR-0012](/adr/0012-vehicle-party-relationships-in-customer.md)
* [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md)
* [ADR-0014](/adr/0014-gateway-internal-service-security.md)
* [ADR-0015](/adr/0015-identity-entity-relationships.md)
* [ADR-0016](/adr/0016-location-entity-semantics.md)
