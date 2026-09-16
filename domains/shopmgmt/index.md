---
type: Domain Guide
title: Shop Management Documentation
description: Canonical Shop Management domain documentation, current contracts, and historical delivery artifacts.
status: reference
---

Shop Management documentation lives in this domain directory. The backend module retains setup and source navigation; edit canonical documentation here. The
[Shop Management catalog entry](../../knowledge-catalog/domains/shopmgmt.md) indexes this domain, and the
[module catalog entry](../../knowledge-catalog/backend/pos-shop-manager.md) connects it to the implementation.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The business rules and backend contract below define current behavior subject to later accepted ADRs. The backend owns
executable API definitions, physical schema, runtime configuration, and implementation source. Historical artifacts preserve delivery context and are not current operational
guidance.

For event boundaries, location scope, and tenancy, read [ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md),
[ADR-0061](../../docs/adr/0061-location-scope-authorization-ownership.adr.md), and [ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md).

## Business rules and contracts

- [Agent guide](.business-rules/AGENT_GUIDE.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Generated backend API reference](.business-rules/BACKEND_API_REFERENCE.generated.md)
- [Domain notes](.business-rules/DOMAIN_NOTES.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)
- [Shop Management domain questions](shopmgmt-questions.md)

## References and history

- [Tire and service shop management guide](archive/shop-management-guidelines.md) - historical operating guidance.
- [Shop Management and workorder guide](archive/shop-management-rag.md) - historical RAG source; this is not the runtime corpus.
- [pos-shop-manager ERD](archive/pos-shop-manager-erd.md) - historical schema snapshot.
- [Backend module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-shop-manager/README.md) - setup and runtime configuration.
- [Backend API definition](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-shop-manager/openapi.yaml) - executable API source.

## Maintaining this documentation

Add canonical documentation here and link it from this index. Do not add substantive documentation to the backend `pos-shop-manager/docs/` directory; it is a navigation
pointer only. Regenerate the knowledge catalog with `python3 scripts/generate-knowledge-catalog.py` when this index gains an authoritative document.
