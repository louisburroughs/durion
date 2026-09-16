---
type: ADR
title: 'ADR-0062: Postgres Row-Level Multitenancy'
description: ADR-0023 (2026-02-21) removed tenantId from every contract and declared the platform single-organization.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0062-postgres-row-level-multitenancy.adr.md
path: durion/docs/adr/0062-postgres-row-level-multitenancy.adr.md
tags: [adr, multitenancy]
status: stable
sources: [docs/adr/0062-postgres-row-level-multitenancy.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T22:15:27-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0062-postgres-row-level-multitenancy.adr.md) — `docs/adr/0062-postgres-row-level-multitenancy.adr.md`

**Status:** Accepted since 2026-09-09
**Supersedes:** [ADR-0023](../adr/0023-remove-tenantid-single-organization-context.md)

**Related:**

* [ADR-0011](../adr/0011-api-gateway-security-architecture.md)
* [ADR-0013](../adr/0013-platform-uuid-identifier-strategy.md)
* [ADR-0017](../adr/0017-api-controller-http-response-codes.md)
* [ADR-0025](../adr/0025-permissions-yaml-registration-policy.md)
* [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0045](../adr/0045-autonomous-environment-lifecycle-management.md)
* [ADR-0061](../adr/0061-location-scope-authorization-ownership.md)
