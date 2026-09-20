---
type: ADR
title: 'ADR-0044: Event-Only Domain Walls and Module Communication Policy'
description: Domain modules may not call each other synchronously — cross-domain data moves as events into local replicas — with named scoped exceptions (pos-warranty, pos-order to pos-invoice) enforced by pos-archunit's DomainWallsTest.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0044-platform-event-only-domain-walls.adr.md
path: durion/docs/adr/0044-platform-event-only-domain-walls.adr.md
tags: [adr, events, platform]
status: stable
sources: [docs/adr/0044-platform-event-only-domain-walls.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T22:15:27-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0044-platform-event-only-domain-walls.adr.md) — `docs/adr/0044-platform-event-only-domain-walls.adr.md`

**Status:** Accepted since 2026-07-08

**Related:**

* [ADR-0006](../adr/0006-workexec-domain-ownership-boundaries.md)
* [ADR-0009](../adr/0009-backend-domain-responsibilities-guide.md)
* [ADR-0011](../adr/0011-api-gateway-security-architecture.md)
* [ADR-0012](../adr/0012-vehicle-party-relationships-in-customer.md)
* [ADR-0013](../adr/0013-platform-uuid-identifier-strategy.md)
* [ADR-0014](../adr/0014-gateway-internal-service-security.md)
* [ADR-0015](../adr/0015-identity-entity-relationships.md)
* [ADR-0016](../adr/0016-location-entity-semantics.md)
