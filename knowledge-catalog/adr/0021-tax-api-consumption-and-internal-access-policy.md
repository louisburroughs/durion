---
type: ADR
title: 'ADR-0021: Tax API Consumption and Internal Access Policy'
description: pos-tax is internal-only with strict address validation; it has no gateway route or SDK, people reach it only through front-door domain modules, and services call it directly from an allowlist (amended 2026-10-08 by ADR-0071).
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md
path: durion/docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md
tags: [adr, accounting, api-contract]
status: stable
sources: [docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-08T13:18:10+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md) — `docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md`

**Status:** Accepted since 2026-02-21

**Related:**

* [ADR-0014](../adr/0014-gateway-internal-service-security.md)
* [ADR-0018](../adr/0018-audit-actor-fields-from-security-context.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0071](../adr/0071-tax-per-tenant-pluggable-providers.md)
