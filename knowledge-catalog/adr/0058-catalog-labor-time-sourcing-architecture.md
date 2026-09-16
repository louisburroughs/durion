---
type: ADR
title: 'ADR-0058: Labor-Time Sourcing Architecture (pos-catalog Estimated Service Time)'
description: Nothing in the platform stores an estimated service time. WorkorderSummary.estimatedLaborHours is declared and always null; workorder_service has quantity and price but no hours; the pos-catalog service table carried onl...
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0058-catalog-labor-time-sourcing-architecture.adr.md'
tags: [adr, product]
status: draft
sources: [docs/adr/0058-catalog-labor-time-sourcing-architecture.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-09-15T21:10:50-04:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0058-catalog-labor-time-sourcing-architecture.adr.md) — `docs/adr/0058-catalog-labor-time-sourcing-architecture.adr.md`

**Status:** Proposed since 2026-09-01
**Related:** [ADR-0026](/adr/0026-service-contract-boundary-policy.md), [ADR-0044](/adr/0044-platform-event-only-domain-walls.md), [ADR-0049](/adr/0049-supplier-integration-module-boundary.md), [ADR-0050](/adr/0050-supplier-vendor-profile-configuration.md), [ADR-0053](/adr/0053-supplier-pricat-ingestion-and-price-precedence.md), [ADR-0059](/adr/0059-labor-time-naming-and-operation-taxonomy.md)
