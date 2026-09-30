---
type: ADR
title: 'ADR-0069: Scope Graph for Pre-LLM Narrowing in pos-mcp-server'
description: pos-mcp-server narrows each chat turn's tools and retrieved documents with per-store similarity searches that share no model of the business; this ADR adds a generated, in-memory scope graph of entities, tools, documents, screens and permissions that runs alongside the RAG pipeline and narrows it.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md
path: durion/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md
tags: [adr]
status: draft
sources: [docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-09-30T01:49:05+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md) — `docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md`

**Status:** Pending since 2026-09-30

**Related:**

* [ADR-0026](../adr/0026-service-contract-boundary-policy.md)
* [ADR-0042](../adr/0042-openapi-annotation-standards.md)
* [ADR-0044](../adr/0044-platform-event-only-domain-walls.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0068](../adr/0068-mcp-pre-llm-question-tagging-decision-model.md)
