---
type: ADR
title: 'ADR-0067: Tenant Functional Currency and Multi-Currency Support'
description: One functional currency per tenant (Stage A) before transactions in several currencies (Stage B - dual amounts and realized FX, then revaluation, then foreign-currency bank accounts); accepted by the platform owner on the section 2 recommendations subject to the section 2.1 rulings - the currency locked at tenant creation rather than activation, one cross-currency settlement case allowed in B1 rather than refused, and Canada as the first non-USD market.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0067-platform-tenant-functional-currency-and-multi-currency.adr.md
path: durion/docs/adr/0067-platform-tenant-functional-currency-and-multi-currency.adr.md
tags: [adr, multitenancy, platform]
status: stable
sources: [docs/adr/0067-platform-tenant-functional-currency-and-multi-currency.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-30T11:56:29+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0067-platform-tenant-functional-currency-and-multi-currency.adr.md) — `docs/adr/0067-platform-tenant-functional-currency-and-multi-currency.adr.md`

**Status:** Accepted since 2026-09-28

**Related:**

* [ADR-0013](../adr/0013-platform-uuid-identifier-strategy.md)
* [ADR-0017](../adr/0017-api-controller-http-response-codes.md)
* [ADR-0021](../adr/0021-tax-api-consumption-and-internal-access-policy.md)
* [ADR-0030](../adr/0030-frontend-internationalization-localization-policy.md)
* [ADR-0040](../adr/0040-roles-jwt-permission-governance-policy.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0047](../adr/0047-accounting-ledger-inalterability-and-fiscal-position-non-goals.md)
* [ADR-0048](../adr/0048-inventory-owned-valuation-configurable-costing-method.md)
