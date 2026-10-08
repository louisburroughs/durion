---
type: ADR
title: 'ADR-0072: Data Classification and Minimisation for Event Payloads, Replicas, Logs and DLQs'
description: Four data classes; RESTRICTED values stay encrypted in their owner, revealed only by permission with audit, and only masked forms such as last4 travel.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0072-data-classification-event-payload-minimisation.adr.md
path: durion/docs/adr/0072-data-classification-event-payload-minimisation.adr.md
tags: [adr, events, platform, security, supplier]
status: draft
sources: [docs/adr/0072-data-classification-event-payload-minimisation.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-08T10:42:58-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0072-data-classification-event-payload-minimisation.adr.md) — `docs/adr/0072-data-classification-event-payload-minimisation.adr.md`

**Status:** Proposed since 2026-10-08

**Related:**

* [ADR-0018](../adr/0018-audit-actor-fields-from-security-context.md)
* [ADR-0022](../adr/0022-audit-stable-person-identifier-claim-policy.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0046](../adr/0046-environment-log-level-policy.md)
* [ADR-0050](../adr/0050-supplier-vendor-profile-configuration.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0065](../adr/0065-frontend-untrusted-content-and-browser-persistence-policy.md)
* [ADR-0070](../adr/0070-bill-intake-ownership-and-vendor-master.md)
