---
type: ADR
title: 'ADR-0045: Autonomous Environment Lifecycle Management'
description: Durion tenant cells run at static capacity around the clock. The alpha substrate is a single EC2 host running all services as Docker containers; the target substrate is ECS Fargate.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0045-autonomous-environment-lifecycle-management.adr.md
tags: [adr]
status: stable
sources: [docs/adr/0045-autonomous-environment-lifecycle-management.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T22:15:27-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0045-autonomous-environment-lifecycle-management.adr.md) — `docs/adr/0045-autonomous-environment-lifecycle-management.adr.md`

**Status:** Accepted since 2026-07-10

**Related:**

* [ADR-0019](/adr/0019-platform-short-lived-operational-state-persistence.md)
* [ADR-0044](/adr/0044-platform-event-only-domain-walls.md)
* [ADR-0062](/adr/0062-postgres-row-level-multitenancy.md)
