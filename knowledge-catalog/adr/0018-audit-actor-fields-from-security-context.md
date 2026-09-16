---
type: ADR
title: 'ADR-0018: Audit Actor Fields from Security Context as Strings'
description: 'Backend services currently use mixed patterns for audit actor fields (createdBy, updatedBy, changedBy):'
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0018-audit-actor-fields-from-security-context.adr.md'
tags: [adr, audit, security]
status: stable
sources: [docs/adr/0018-audit-actor-fields-from-security-context.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-03-08T22:38:02-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0018-audit-actor-fields-from-security-context.adr.md) — `docs/adr/0018-audit-actor-fields-from-security-context.adr.md`

**Status:** Accepted since 2026-02-18
**Related:** [ADR-0011](/adr/0011-api-gateway-security-architecture.md), [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md), [ADR-0014](/adr/0014-gateway-internal-service-security.md)
