---
type: ADR
title: 'ADR-0071: Per-Tenant Pluggable Tax Providers Behind pos-tax, Reached Through Front Doors'
description: Keeps pos-tax as the one internal tax port, which picks a provider plug-in per tenant and country on platform-held provider accounts, owns tenant tax-profile data and publishes tax registrations by outbox, while every operation a person starts reaches it only through an external-facing domain module.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0071-tax-per-tenant-pluggable-providers.adr.md
path: durion/docs/adr/0071-tax-per-tenant-pluggable-providers.adr.md
tags: [adr, accounting, multitenancy]
status: stable
sources: [docs/adr/0071-tax-per-tenant-pluggable-providers.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-08T13:18:10+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0071-tax-per-tenant-pluggable-providers.adr.md) — `docs/adr/0071-tax-per-tenant-pluggable-providers.adr.md`

**Status:** Accepted since 2026-10-08

**Related:**

* [ADR-0014](../adr/0014-gateway-internal-service-security.md)
* [ADR-0017](../adr/0017-api-controller-http-response-codes.md)
* [ADR-0018](../adr/0018-audit-actor-fields-from-security-context.md)
* [ADR-0021](../adr/0021-tax-api-consumption-and-internal-access-policy.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0067](../adr/0067-platform-tenant-functional-currency-and-multi-currency.md)
