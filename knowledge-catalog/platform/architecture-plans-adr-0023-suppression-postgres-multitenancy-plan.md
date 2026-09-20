---
type: Platform Document
title: 'ADR-0023 Suppression: Postgres Row-Level Multitenancy Plan'
description: 'Cross-repo plan and effort assessment for replacing the single-organization decision of ADR-0023 with shared-database multitenancy under ADR-0062: Hibernate @TenantId discrimination plus Postgres row-level security on a per-request tenant GUC, a new pos-tenant module owning the tenant registry and the account behind each tenancy, tenant-scoped roles and Spring caches, gateway stripping of X-Tenant-* headers, composite foreign keys, and the workstream-by-workstream (WS1, WS2a, WS2b, ...) breakdown per repository with measured footprint.'
resource: https://github.com/louisburroughs/durion/blob/master/docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md
path: durion/docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md
tags: [platform, architecture]
kind: Plan
status: stable
doc_status: active
sources: [durion/docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-11T18:16:51+00:00'}
---

[Document](https://github.com/louisburroughs/durion/blob/master/docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md) — `durion/docs/architecture/plans/adr-0023-suppression-postgres-multitenancy-plan.md`

**Kind:** Plan
**Area:** Platform architecture
**Repository:** durion — platform knowledge
**Document status:** active
