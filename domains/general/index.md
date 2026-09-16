---
type: Domain Guide
title: General Documentation
description: Cross-domain platform documentation, led by the pos-mcp-server natural-language assistant, plus UI artifacts no business domain owns.
status: reference
---

The general domain holds documentation that no single business domain owns. Its main subject is `pos-mcp-server`, the
natural-language assistant and MCP server that reaches every domain through facade tools and discovered OpenAPI
operations. The backend module keeps setup, configuration and source navigation; edit canonical documentation here. The
[general catalog entry](../../knowledge-catalog/domains/general.md) indexes this domain, and the
[module catalog entry](../../knowledge-catalog/backend/pos-mcp-server.md) connects it to the implementation.

## Authority and status

Accepted [ADRs](../../docs/adr/) govern architecture. The architecture document below describes current behavior, subject
to later accepted ADRs. The backend owns executable API definitions, physical schema, runtime configuration, the RAG
corpus, eval fixtures, and implementation source. Archived artifacts preserve delivery context and are not current
operational guidance.

For analytics semantics and tenancy, read
[ADR-0057](../../docs/adr/0057-analytics-money-measure-semantics-and-ownership.adr.md) and
[ADR-0062](../../docs/adr/0062-postgres-row-level-multitenancy.adr.md). For the direct pos-tax call made by the tax
facade, read [ADR-0021](../../docs/adr/0021-tax-api-consumption-and-internal-access-policy.adr.md).

Paths in backticks without a repository prefix (`src/...`, `scripts/...`, `observability/...`) refer to
`durion-positivity-backend`.

## pos-mcp-server

- [Architecture](mcp-server/architecture.md) - tool selection, facade and discovered tools, answer resolution, RAG,
  audit and tuning, data model, multitenancy, backlog.
- [Tool selection and runtime configuration](mcp-server/tool-selection-architecture.md) - role resolution,
  system-prompt assembly, agent caching and static RAG preload.
- [Role persona sourcing](mcp-server/role-persona-sourcing.md) - accepted design (#1613): role personas come from
  `pos-security-service`, not a compiled list.
- [Analytics capability plan](mcp-server/analytics-capability-plan.md) - active plan: Wave 1 delivered, Wave 2 built
  with its exit gate not yet passed (last recorded run 5/12), Wave 3 proposed.

## Operations

- [NLTI alert rules](mcp-server/operations/alerts/nlti-alerts.md) - authoritative alert intent and thresholds. Only the
  Loki rules are provisioned.
- [Tool discovery alert rules](mcp-server/operations/alerts/tool-discovery-alerts.md)
- [NLTI dashboards](mcp-server/operations/dashboards/nlti-overview.md)
- [Tool discovery failure runbook](mcp-server/operations/runbooks/tool-discovery-failure.md)
- Draft runbooks: [audit storage failure](mcp-server/operations/runbooks/audit-storage-failure.md),
  [AuthZ outage](mcp-server/operations/runbooks/authz-outage.md),
  [confirmation gate mismatch](mcp-server/operations/runbooks/confirmation-gate-mismatch.md),
  [downstream timeout](mcp-server/operations/runbooks/downstream-timeout.md),
  [planning failure](mcp-server/operations/runbooks/planning-failure.md).

## References and history

- [Archive index](mcp-server/archive/README.md) - the NL-interface phase-gate program (Gates 0-7), Wave 2 gate runs,
  the Spring AI migration, RAG corpus designs and research notes, each with the reason it was archived.
- [Backend module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-mcp-server/README.md) -
  endpoints, configuration and startup behavior.
- [Backend API definition](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-mcp-server/openapi.yaml) -
  executable API source.
- `.ui/` - wireframes for cross-domain screens that no business domain owns.

## Maintaining this documentation

Add canonical documentation here and link it from this index. When a pos-mcp-server change alters behavior the model,
an operator or an API caller can see, update `mcp-server/architecture.md` or `mcp-server/operations/` in a durion PR
that accompanies the backend PR. Do not add substantive documentation to the backend `pos-mcp-server/docs/` directory;
it is a navigation pointer only. Move a document to `mcp-server/archive/` when its work ships or its issues close, and
record why in the archive index. Regenerate the knowledge catalog with `python3 scripts/generate-knowledge-catalog.py`
when this index gains an authoritative document.
