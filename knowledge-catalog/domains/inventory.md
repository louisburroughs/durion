---
type: Domain
title: inventory
description: This guide defines the normative business rules and frontend-facing contracts for the Inventory domain in Durion.
resource: https://github.com/louisburroughs/durion/blob/master/domains/inventory
path: durion/domains/inventory/
tags: [domain, inventory]
sources: [domains/inventory/]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-23T21:23:03+00:00'}
---

[Domain folder](https://github.com/louisburroughs/durion/blob/master/domains/inventory) — `domains/inventory/` (51 documents)

[Canonical documentation index](https://github.com/louisburroughs/durion/blob/master/domains/inventory/index.md) — authority, current guidance, and historical records

**Implemented by:** [pos-inventory](../backend/pos-inventory.md), [pos-location](../backend/pos-location.md), [pos-catalog](../backend/pos-catalog.md),
[pos-workorder](../backend/pos-workorder.md)

**Business rules:**

* [AGENT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/AGENT_GUIDE.md) — Agent Guide
* [BACKEND_API_REFERENCE.generated.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md) —
  API Reference
* [BACKEND_CONTRACT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md) — Backend Contract
* [CROSS_DOMAIN_INTEGRATION_CONTRACT.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md) —
  Integration Contract
* [DOMAIN_NOTES.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/DOMAIN_NOTES.md) — Domain Notes
* [PERMISSION_TAXONOMY.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/PERMISSION_TAXONOMY.md) — Permission Taxonomy
* [STORY_VALIDATION_CHECKLIST.md](https://github.com/louisburroughs/durion/blob/master/domains/inventory/.business-rules/STORY_VALIDATION_CHECKLIST.md) — Checklist

**Documentation:**

* [Inventory Domain - Phase 3 Planning Guide](https://github.com/louisburroughs/durion/blob/master/domains/inventory/INVENTORY_PLANNING_README.md)
* [Inventory Domain Phase 3 Planning - Summary & Execution Guide](https://github.com/louisburroughs/durion/blob/master/domains/inventory/PHASE_3_SUMMARY.md)
* [Inventory Domain Phase 3 - Quick Reference Card](https://github.com/louisburroughs/durion/blob/master/domains/inventory/QUICK_REFERENCE.md)
* [Specification: Inventory-by-Location Frontend View (Hierarchy Rollup)][document-4]
* [Specification: Site Inventory Rollup by Storage Location Hierarchy][document-5]
* [SPEC — pos-inventory Missing Functionality (Odoo Inventory Parity)][document-6]
* [Allocation Consistency Sweep](https://github.com/louisburroughs/durion/blob/master/domains/inventory/allocation-consistency.md)
* [pos-inventory-erd](https://github.com/louisburroughs/durion/blob/master/domains/inventory/archive/pos-inventory-erd.md)
* [Odoo Inventory (stock) — Functional Overview (Reference for pos-inventory Comparison)][document-9]
* [Odoo Inventory vs pos-inventory — Capability Comparison Map](https://github.com/louisburroughs/durion/blob/master/domains/inventory/comp-vs-pos-inventory-comparison.md)
* [Inventory Ledger Event Type Reference Card](https://github.com/louisburroughs/durion/blob/master/domains/inventory/event-type-reference.md)
* [Inventory Ledger: On-Hand and ATP Computation](https://github.com/louisburroughs/durion/blob/master/domains/inventory/inventory-ledger-atp.md)
* [Inventory Domain - Open Questions & Phase Implementation Plan](https://github.com/louisburroughs/durion/blob/master/domains/inventory/inventory-questions.md)
* [Negative-Stock Policy Matrix](https://github.com/louisburroughs/durion/blob/master/domains/inventory/negative-stock-policy.md)
* [Inventory Domain Phase 3 - Documentation Index](https://github.com/louisburroughs/durion/blob/master/domains/inventory/phase-3-planning-index.md)
* [Odoo Parity Plan — pos-inventory](https://github.com/louisburroughs/durion/blob/master/domains/inventory/plan-odoo-parity-pos-inventory.md)
* [Putaway Validation Business Rules](https://github.com/louisburroughs/durion/blob/master/domains/inventory/putaway-validation-rules.md)
* [Inventory Control Best Practices & Terminology](https://github.com/louisburroughs/durion/blob/master/domains/inventory/reference/inv-cntrl-rag.md)

[document-4]: https://github.com/louisburroughs/durion/blob/master/domains/inventory/SPEC-inventory-location-rollup-frontend.md
[document-5]: https://github.com/louisburroughs/durion/blob/master/domains/inventory/SPEC-inventory-location-rollup.md
[document-6]: https://github.com/louisburroughs/durion/blob/master/domains/inventory/SPEC-pos-inventory-odoo-parity.md
[document-9]: https://github.com/louisburroughs/durion/blob/master/domains/inventory/comp-odoo-inventory-overview.md
