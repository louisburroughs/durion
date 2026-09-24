---
type: Domain Guide
title: Accounting Documentation
description: Canonical accounting documentation with business rules, reconciled backend implementation references, conceptual material, research and historical records.
status: reference
---

# Accounting Documentation

Accounting documentation lives in this domain directory. The backend module retains setup/source navigation and
compatibility pointers; edit the canonical documents here. The generated
[accounting catalog entry](../../knowledge-catalog/domains/accounting.md) indexes these documents, and the
[module catalog entry](../../knowledge-catalog/backend/pos-accounting.md) connects them to the backend.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. Business rules below define domain contracts, subject to later
accepted ADRs. The [implementation reference](implementation-reference.md) records inspected backend behavior and known
documentation discrepancies; it does not silently amend domain policy. Physical schema and executable API definitions
remain with the backend source. Historical reports preserve delivery context, including old test results and unfinished
plans; those statements are not current verification or instructions.

For event boundaries and tenancy, read [ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md) and
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md). Older organization/single-tenant assumptions do not
override ADR-0062. Existing Moqui-era UI/service guidance also needs checking against the current frontend before reuse.

## Business rules and contracts

- [Agent guide and accounting decisions](.business-rules/AGENT_GUIDE.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Generated backend API reference](.business-rules/BACKEND_API_REFERENCE.generated.md)
- [Cross-domain integration contracts](.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md)
- [Conceptual domain model](.business-rules/DOMAIN_MODEL.md)
- [Posting rule schema](.business-rules/POSTING_RULES_SCHEMA.md)
- [Dimension schema](.business-rules/DIMENSION_SCHEMA.md)
- [Permission taxonomy](.business-rules/PERMISSION_TAXONOMY.md)
- [Error catalog](.business-rules/ERROR_CODES.md)
- [Design rationale and domain notes](.business-rules/DOMAIN_NOTES.md)
- [Additional accounting notes](.business-rules/accounting.md)

## Implementation and conceptual references

- [Reconciled implementation reference](implementation-reference.md) — bill matching, vendor-bill event ingestion,
  local/Kafka outboxes, schema navigation, and known discrepancies checked against backend `e5d7ec82e`.
- [Double-entry bookkeeping and core principles](reference/de-bookkeeping-rag.md) — conceptual RAG source;
  illustrative examples, not executable posting or authorization contracts.
- [Backend module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/README.md) — setup and runtime configuration.
- [Backend API definition](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-accounting/openapi.yaml) — executable API source.

## Historical backend records

All seven former `pos-accounting/docs/` documents have one canonical destination. Their original paths remain short backend pointers.

| Document | Status and use |
| --- | --- |
| [Bill matching implementation summary](archive/implementation/BILL_MATCHING_IMPLEMENTATION_SUMMARY.md) | Historical delivery report; current scoring is reconciled above. |
| [Bill matching improvements](archive/implementation/BILL_MATCHING_IMPROVEMENTS.md) | Historical follow-up report; overlaps the summary and contains a superseded roadmap. |
| [GL posting event implementation](archive/implementation/GL_POSTING_EVENT_IMPLEMENTATION.md) | Historical event/handler report; ingestion is implemented, but ingestion is not journal posting. |
| [Outbox pattern implementation](archive/implementation/OUTBOX_PATTERN.md) | Historical local-outbox report; does not describe today's Kafka outbox. |
| [Flyway baseline reset plan](archive/flyway-baseline-reset-plan.md) | Superseded migration plan; use current backend migration/runbook sources. |
| [Accounting ERD](archive/pos-accounting-erd.md) | Historical physical schema snapshot before multitenancy. |

## Comparisons, plans and research

These existing documents support investigation and delivery planning; comparisons and old completion claims are not current contracts.

- [Inventory adjustment GL posting specification](SPEC-inventory-adjustment-gl-posting.md) — ruling on durion-positivity-backend#2186 and the fact-to-journal-entry
  flow for cycle-count and manual inventory adjustments (proposed; open decisions pending)
- [Accounting comparison overview](comp-accounting-overview.md)
- [Odoo versus pos-accounting](comp-vs-pos-accounting-comparison.md)
- [Reconciliation internals comparison](comp-reconciliation-internals.md)
- [Tax engine deep dive](comp-tax-engine-deep-dive.md)
- [Odoo versus pos-tax](comp-vs-pos-tax-comparison.md)
- [Accounting parity plan and delivery history](plan-odoo-parity-pos-accounting.md)
- [Tax parity plan and delivery history](plan-odoo-parity-pos-tax.md)
- [Accounting clarification questions](accounting-questions.md) and [original text worksheet](accounting-questions.txt)
- [Research gate decisions](.research/RESEARCH-GATE-DECISIONS.md), [tax-provider comparison](.research/R-T1-provider-comparison.md),
  and [invoice event inventory](.research/R-T2-invoice-event-inventory.md)
- [UI story wireframes and metadata](.ui/) — historical story/design artifacts; verify against the current frontend.

## Maintaining this documentation

Add documentation here and link it from this index. Visible Markdown documents are included automatically in the
accounting knowledge-catalog entry because this index declares `type: Domain Guide`. Add `type`, `title`, `description`
and `status` frontmatter to make each new document's purpose and lifecycle explicit. Hidden business rules retain their
own catalog section; hidden research/UI artifacts are reached through this index.

Run `python3 scripts/generate-knowledge-catalog.py` from the `durion` root after source changes; use `--check` to validate
OKF structure. Do not hand-edit generated catalog entries. The
[reconciliation plan](../../docs/superpowers/plans/2026-09-16-accounting-documentation-reconciliation.md) records this migration and verification.
