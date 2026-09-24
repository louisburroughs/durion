---
type: Domain Guide
title: Inventory Documentation
description: Canonical inventory documentation with current operating rules, conceptual references, and historical implementation artifacts.
status: reference
---

Inventory documentation lives in this domain directory. The backend module retains setup and source navigation; edit the canonical documents here. The
[inventory catalog entry](../../knowledge-catalog/domains/inventory.md) indexes this domain, and the [module catalog entry](../../knowledge-catalog/backend/pos-inventory.md)
connects it to the implementation.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The business rules below define domain behavior subject to later accepted ADRs. The backend owns executable API
definitions, physical schema, runtime configuration, and implementation source. Historical artifacts preserve delivery context and are not current operational guidance.

For inventory ledger and tenancy boundaries, read [ADR-0001](../../docs/adr/0001-inventory-ledger-atp-computation.adr.md) and
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md).

## Business rules and contracts

- [Agent guide](.business-rules/AGENT_GUIDE.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Generated backend API reference](.business-rules/BACKEND_API_REFERENCE.generated.md)
- [Cross-domain integration contract](.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md)
- [Domain notes](.business-rules/DOMAIN_NOTES.md)
- [Permission taxonomy](.business-rules/PERMISSION_TAXONOMY.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)

## Operational references

- [Inventory ledger on-hand and ATP computation](inventory-ledger-atp.md)
- [Negative-stock policy matrix](negative-stock-policy.md)
- [Allocation consistency sweep](allocation-consistency.md)
- [Putaway validation business rules](putaway-validation-rules.md)
- [Inventory ledger event type reference](event-type-reference.md)

## References and history

- [Inventory adjustment GL posting specification](../accounting/SPEC-inventory-adjustment-gl-posting.md) - cross-domain spec for the `InventoryAdjustedV1` fact that
  cycle-count and manual adjustments must emit and its accounting consumer (durion-positivity-backend#2186; proposed).
- [Inventory control best practices and terminology](reference/inv-cntrl-rag.md) - conceptual RAG source, not an executable inventory contract.
- [Inventory ERD before multitenancy](archive/pos-inventory-erd.md) - historical schema snapshot.
- [Backend module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-inventory/README.md) - setup and runtime configuration.
- [Backend API definition](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-inventory/openapi.yaml) - executable API source.

## Maintaining this documentation

Add canonical documentation here and link it from this index. Do not add substantive documentation to the backend `pos-inventory/docs/` directory; it is a navigation pointer
only. Regenerate the knowledge catalog with `python3 scripts/generate-knowledge-catalog.py` when this index gains an authoritative document.
