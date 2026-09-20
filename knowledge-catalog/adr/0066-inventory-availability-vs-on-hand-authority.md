---
type: ADR
title: 'ADR-0066: Availability and On-Hand Are Different Questions, With Different Permissions'
description: Availability and on-hand are separate questions carrying separate permission families, and neither implies the other; inventory:availability:read (bit 311) gates the scope-limited netted answer, inventory:availability:search (bit 470) gates the per-location breakdown, and the availability endpoints move off the inventory:on_hand:* gates.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0066-inventory-availability-vs-on-hand-authority.adr.md
path: durion/docs/adr/0066-inventory-availability-vs-on-hand-authority.adr.md
tags: [adr, inventory, rbac]
status: stable
sources: [docs/adr/0066-inventory-availability-vs-on-hand-authority.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-20T11:54:34+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0066-inventory-availability-vs-on-hand-authority.adr.md) — `docs/adr/0066-inventory-availability-vs-on-hand-authority.adr.md`

**Status:** Accepted since 2026-08-24

**Related:**

* [ADR-0001](../adr/0001-inventory-ledger-atp-computation.md)
* [ADR-0025](../adr/0025-permissions-yaml-registration-policy.md)
* [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.md)
* [ADR-0061](../adr/0061-location-scope-authorization-ownership.md)
