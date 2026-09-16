---
type: ADR
title: 'ADR-0027: UUID-Typed Identifier Contract Policy'
description: Across backend modules, identifier fields are not consistently typed. Some IDs are modeled as String in service contracts or DTOs even when the underlying identifier is a platform UUID.
resource: https://github.com/louisburroughs/durion/blob/main/docs/adr/0027-uuid-typed-id-contract-policy.adr.md
tags: [adr, identifiers]
status: stable
sources: [docs/adr/0027-uuid-typed-id-contract-policy.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-15T21:10:50-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0027-uuid-typed-id-contract-policy.adr.md) — `docs/adr/0027-uuid-typed-id-contract-policy.adr.md`

**Status:** Accepted since 2026-02-28

**Related:**

* [ADR-0013](/adr/0013-platform-uuid-identifier-strategy.md)
