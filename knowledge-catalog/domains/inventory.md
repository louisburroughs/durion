---
type: Domain
title: inventory
description: This guide defines the normative business rules and frontend-facing contracts for the Inventory domain in Durion.
resource: https://github.com/louisburroughs/durion/blob/master/domains/inventory
tags: [domain, inventory]
sources: [domains/inventory/]
generated: { by: "script:generate-knowledge-catalog.py", at: "2026-09-15T21:43:29-04:00" }
---

[Domain folder](https://github.com/louisburroughs/durion/blob/master/domains/inventory) — `domains/inventory/` (51 documents)

[Canonical documentation index](https://github.com/louisburroughs/durion/blob/master/domains/inventory/index.md) — authority, current guidance, and historical records

**Implemented by:** [pos-inventory](/backend/pos-inventory.md), [pos-location](/backend/pos-location.md), [pos-catalog](/backend/pos-catalog.md),
[pos-workorder](/backend/pos-workorder.md)

**Business rules:**

- [AGENT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/AGENT_GUIDE.md) — Agent Guide
- [BACKEND_API_REFERENCE.generated.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md) — API
  Reference
- [BACKEND_CONTRACT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md) — Backend Contract
- [CROSS_DOMAIN_INTEGRATION_CONTRACT.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md) —
  Integration Contract
- [DOMAIN_NOTES.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/DOMAIN_NOTES.md) — Domain Notes
- [PERMISSION_TAXONOMY.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/PERMISSION_TAXONOMY.md) — Permission Taxonomy
- [STORY_VALIDATION_CHECKLIST.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/STORY_VALIDATION_CHECKLIST.md) — Checklist

**Documentation:**

- [Inventory Ledger: On-Hand and ATP Computation](https://github.com/louisburroughs/durion/blob/master/domains/inventory/inventory-ledger-atp.md)
- [Negative-Stock Policy Matrix](https://github.com/louisburroughs/durion/blob/master/domains/inventory/negative-stock-policy.md)
- [Allocation Consistency Sweep](https://github.com/louisburroughs/durion/blob/master/domains/inventory/allocation-consistency.md)
- [Putaway Validation Business Rules](https://github.com/louisburroughs/durion/blob/master/domains/inventory/putaway-validation-rules.md)
- [Inventory Ledger Event Type Reference Card](https://github.com/louisburroughs/durion/blob/master/domains/inventory/event-type-reference.md)
- [Inventory Control Best Practices and Terminology](https://github.com/louisburroughs/durion/blob/master/domains/inventory/reference/inv-cntrl-rag.md) — Conceptual Reference
  · reference
- [Inventory ERD Before Multitenancy](https://github.com/louisburroughs/durion/blob/master/domains/inventory/archive/pos-inventory-erd.md) — Schema Snapshot · historical
