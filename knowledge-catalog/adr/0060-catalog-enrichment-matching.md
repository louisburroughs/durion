---
type: ADR
title: 'ADR-0060: Catalog Enrichment Matching and Review (pos-catalog Tread-Design Confidence Tiers)'
description: Every candidate scoring ≥ 0.50 is auto-attached, and a later import that scores higher against a different design silently re-points the product — last write wins, with no review step and no memory of the decision.
resource: 'https://github.com/louisburroughs/durion/blob/main/docs/adr/0060-catalog-enrichment-matching.adr.md'
tags: [adr, product]
status: stable
sources: [docs/adr/0060-catalog-enrichment-matching.adr.md]
generated: { by: script:generate-knowledge-catalog.py, at: 2026-09-06T17:16:40+00:00 }
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/main/docs/adr/0060-catalog-enrichment-matching.adr.md) — `docs/adr/0060-catalog-enrichment-matching.adr.md`

**Status:** Accepted since 2026-09-06
**Related:** [ADR-0017](/adr/0017-api-controller-http-response-codes.md), [ADR-0025](/adr/0025-permissions-yaml-registration-policy.md), [ADR-0044](/adr/0044-platform-event-only-domain-walls.md), [ADR-0049](/adr/0049-supplier-integration-module-boundary.md), [ADR-0050](/adr/0050-supplier-vendor-profile-configuration.md), [ADR-0053](/adr/0053-supplier-pricat-ingestion-and-price-precedence.md)
