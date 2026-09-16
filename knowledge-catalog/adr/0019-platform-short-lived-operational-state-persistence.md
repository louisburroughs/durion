---
type: ADR
title: 'ADR-0019: Persistence Strategy for Short-Lived Operational State'
description: Multiple backend workflows create high-churn operational records that are usually short-lived (for example active sessions, transient workflow state, and in-flight process markers).
resource: https://github.com/louisburroughs/durion/blob/main/docs/adr/0019-platform-short-lived-operational-state-persistence.adr.md
tags: [adr, platform]
status: stable
sources: [docs/adr/0019-platform-short-lived-operational-state-persistence.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T21:10:50-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0019-platform-short-lived-operational-state-persistence.adr.md) — `docs/adr/0019-platform-short-lived-operational-state-persistence.adr.md`

**Status:** Accepted since 2026-02-19

**Related:**

* [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md)
* [ADR-0014](/adr/0014-gateway-internal-service-security.md)
* [ADR-0018](/adr/0018-audit-actor-fields-from-security-context.md)
