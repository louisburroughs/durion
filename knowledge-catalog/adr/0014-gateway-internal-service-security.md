---
type: ADR
title: 'ADR-0014: Internal Service Security via Gateway Route Control'
description: The Durion Positivity Backend uses a microservices architecture where some services are intended for public access (e.g., pos-order, pos-catalog) while others are strictly internal (e.g., pos-tax, pos-events).
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0014-gateway-internal-service-security.adr.md
tags: [adr, gateway, security]
sources: [docs/adr/0014-gateway-internal-service-security.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T22:15:27-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0014-gateway-internal-service-security.adr.md) — `docs/adr/0014-gateway-internal-service-security.adr.md`

**Related:**

* [ADR-0044](/adr/0044-platform-event-only-domain-walls.md)
