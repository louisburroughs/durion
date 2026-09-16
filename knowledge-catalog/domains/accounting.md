---
type: Domain
title: accounting
description: The Accounting domain is responsible for authoritative financial calculations, invoice adjustments, issuance finalization ingestion visibility, chart of accounts
resource: https://github.com/louisburroughs/durion/blob/master/domains/accounting
tags: [domain, accounting]
sources: [domains/accounting/]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-16T09:45:18-04:00'}
---

[Domain folder](https://github.com/louisburroughs/durion/blob/master/domains/accounting) — `domains/accounting/` (77 documents)

[Canonical documentation index](https://github.com/louisburroughs/durion/blob/master/domains/accounting/index.md) — authority, current guidance, and historical records

**Implemented by:** [pos-accounting](/backend/pos-accounting.md), [pos-tax](/backend/pos-tax.md), [pos-invoice](/backend/pos-invoice.md), [pos-workorder](/backend/pos-workorder.md)

**Business rules:**

* [AGENT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/AGENT_GUIDE.md) — Agent Guide
* [BACKEND_API_REFERENCE.generated.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md) —
  API Reference
* [BACKEND_CONTRACT_GUIDE.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md) — Backend Contract
* [CROSS_DOMAIN_INTEGRATION_CONTRACTS.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md) —
  Integration Contract
* [DIMENSION_SCHEMA.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/DIMENSION_SCHEMA.md) — Schema
* [DOMAIN_MODEL.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/DOMAIN_MODEL.md) — Domain Model
* [DOMAIN_NOTES.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/DOMAIN_NOTES.md) — Domain Notes
* [ERROR_CODES.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/ERROR_CODES.md) — Error Catalog
* [PERMISSION_TAXONOMY.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/PERMISSION_TAXONOMY.md) — Permission Taxonomy
* [POSTING_RULES_SCHEMA.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/POSTING_RULES_SCHEMA.md) — Schema
* [accounting.md](https://github.com/louisburroughs/durion/blob/master/domains/accounting/.business-rules/accounting.md) — Reference Notes

**Documentation:**

* [Accounting Domain - Remaining Open Questions & Implementation Plan](https://github.com/louisburroughs/durion/blob/master/domains/accounting/accounting-questions.md)
* [Accounting Flyway Baseline Reset Plan](https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/flyway-baseline-reset-plan.md) —
  Historical Plan · superseded
* [Bill Matching Implementation Summary][document-3] — Implementation Report · historical
* [Bill Matching Improvements](https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/implementation/BILL_MATCHING_IMPROVEMENTS.md) —
  Implementation Report · historical
* [Vendor Bill GL Posting Event Implementation][document-5] — Implementation Report · historical
* [Event Outbox Pattern Implementation](https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/implementation/OUTBOX_PATTERN.md) —
  Implementation Report · historical
* [Accounting ERD Before Multitenancy](https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/pos-accounting-erd.md) — Schema Snapshot · historical
* [Odoo Accounting — Functional Overview (Reference for pos-accounting Comparison)][document-8]
* [Odoo Reconciliation Internals — Deep Dive (Reference for pos-accounting Comparison)][document-9]
* [Odoo Tax Engine — Deep Dive (Reference for pos-tax Comparison)](https://github.com/louisburroughs/durion/blob/master/domains/accounting/comp-tax-engine-deep-dive.md)
* [Odoo Accounting vs pos-accounting — Capability Comparison Map](https://github.com/louisburroughs/durion/blob/master/domains/accounting/comp-vs-pos-accounting-comparison.md)
* [Odoo Tax Engine vs pos-tax — Capability Comparison Map](https://github.com/louisburroughs/durion/blob/master/domains/accounting/comp-vs-pos-tax-comparison.md)
* [Accounting Implementation Reference](https://github.com/louisburroughs/durion/blob/master/domains/accounting/implementation-reference.md) —
  Implementation Reference · reference
* [plan-odoo-parity-pos-accounting](https://github.com/louisburroughs/durion/blob/master/domains/accounting/plan-odoo-parity-pos-accounting.md)
* [plan-odoo-parity-pos-tax](https://github.com/louisburroughs/durion/blob/master/domains/accounting/plan-odoo-parity-pos-tax.md)
* [Double-Entry Bookkeeping and Core Accounting Principles](https://github.com/louisburroughs/durion/blob/master/domains/accounting/reference/de-bookkeeping-rag.md) —
  Conceptual Reference · reference

[document-3]: https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/implementation/BILL_MATCHING_IMPLEMENTATION_SUMMARY.md
[document-5]: https://github.com/louisburroughs/durion/blob/master/domains/accounting/archive/implementation/GL_POSTING_EVENT_IMPLEMENTATION.md
[document-8]: https://github.com/louisburroughs/durion/blob/master/domains/accounting/comp-accounting-overview.md
[document-9]: https://github.com/louisburroughs/durion/blob/master/domains/accounting/comp-reconciliation-internals.md
