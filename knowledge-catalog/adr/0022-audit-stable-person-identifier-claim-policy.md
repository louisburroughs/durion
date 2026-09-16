---
type: ADR
title: 'ADR-0022: Audit Stable Person Identifier Claim Policy'
description: ADR-0018 established that audit actor fields must be sourced from authenticated security context and stored as strings. Current JWT usage commonly includes username, but username can change over time.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0022-audit-stable-person-identifier-claim-policy.adr.md
tags: [adr, audit]
status: stable
sources: [docs/adr/0022-audit-stable-person-identifier-claim-policy.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T22:15:27-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0022-audit-stable-person-identifier-claim-policy.adr.md) — `docs/adr/0022-audit-stable-person-identifier-claim-policy.adr.md`

**Status:** Accepted since 2026-02-21

**Related:**

* [ADR-0014](/adr/0014-gateway-internal-service-security.md)
* [ADR-0018](/adr/0018-audit-actor-fields-from-security-context.md)
