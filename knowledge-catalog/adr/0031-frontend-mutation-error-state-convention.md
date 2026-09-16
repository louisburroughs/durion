---
type: ADR
title: 'ADR-0031: Frontend Mutation Error State Convention'
description: The Angular frontend uses a state = signal<PageState>('idle' | 'loading' | 'empty' | 'ready' | 'error') pattern in feature components. The page error banner is only rendered when state() === 'error'.
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0031-frontend-mutation-error-state-convention.adr.md'
tags: [adr, frontend]
status: stable
sources: [docs/adr/0031-frontend-mutation-error-state-convention.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-04-22T20:37:56-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0031-frontend-mutation-error-state-convention.adr.md) — `docs/adr/0031-frontend-mutation-error-state-convention.adr.md`

**Status:** Accepted since 2026-03-29
**Related:** [ADR-0029](/adr/0029-frontend-accessibility-baseline-policy.md)
