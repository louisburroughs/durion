---
type: ADR
title: 'ADR-0069: Scope Graph for Pre-LLM Narrowing in pos-mcp-server'
description: Adds a generated, in-memory scope graph of entities, tools, documents, screens and permissions to pos-mcp-server that runs alongside RAG to narrow retrieval and steer tool selection and the prompt per turn; definitions only, no new datastore.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md
path: durion/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md
tags: [adr]
status: stable
sources: [docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-02T16:17:19-04:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md) — `docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md`

**Status:** Accepted since 2026-09-30

**Related:**

* [ADR-0026](../adr/0026-service-contract-boundary-policy.md)
* [ADR-0042](../adr/0042-openapi-annotation-standards.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0068](../adr/0068-mcp-pre-llm-question-tagging-decision-model.md)
