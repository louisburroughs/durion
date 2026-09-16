---
type: Domain Guide
title: People Documentation
description: Canonical People domain documentation, current contracts, and historical delivery artifacts.
status: reference
---

People documentation lives in this domain directory. The backend module retains setup and source navigation; edit canonical documentation here. The
[People catalog entry](../../knowledge-catalog/domains/people.md) indexes this domain, and the [module catalog entry](../../knowledge-catalog/backend/pos-people.md) connects
it to the implementation.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The business rules and backend contract below define current behavior subject to later accepted ADRs. The backend owns
executable API definitions, physical schema, runtime configuration, and implementation source. Historical artifacts preserve delivery context and are not current operational
guidance.

For identity ownership and event boundaries, read [ADR-0015](../../docs/adr/0015-identity-entity-relationships.adr.md),
[ADR-0043](../../docs/adr/0043-user-person-linkage-authority.adr.md), [ADR-0044](../../docs/adr/0044-platform-event-only-domain-walls.adr.md), and
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md).

## Business rules and contracts

- [Agent guide](.business-rules/AGENT_GUIDE.md)
- [Backend contract guide](.business-rules/BACKEND_CONTRACT_GUIDE.md)
- [Generated backend API reference](.business-rules/BACKEND_API_REFERENCE.generated.md)
- [Domain notes](.business-rules/DOMAIN_NOTES.md)
- [Story validation checklist](.business-rules/STORY_VALIDATION_CHECKLIST.md)
- [People domain questions](people-questions.md)

## References and history

- [HR functions guide](archive/hr-functions-guide.md) - historical pre-Phase 3.2 functional guide; identity and contact ownership moved to `pos-people-contact`.
- [ADR-0044 Phase 3.2 cutover](archive/ADR-0044-PHASE-3.2-CUTOVER.md) - historical identity-ownership cutover procedure.
- [Phase 3 baseline cutover runbook](archive/PHASE3-CUTOVER-RUNBOOK.md) - historical reset and deployment procedure.
- [Plan 726 person-name reconciliation](archive/PLAN-726-person-name-reconciliation.md) - historical name-model reconciliation plan.
- [pos-people ERD](archive/pos-people-erd.md) - historical schema snapshot.
- [Backend module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-people/README.md) - setup and runtime configuration.
- [Backend API definition](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-people/openapi.yaml) - executable API source.

## Maintaining this documentation

Add canonical documentation here and link it from this index. Do not add substantive documentation to the backend `pos-people/docs/` directory; it is a navigation pointer
only. Regenerate the knowledge catalog with `python3 scripts/generate-knowledge-catalog.py` when this index gains an authoritative document.
