---
type: ADR
title: 'ADR-0068: Pre-LLM Question Tagging with a Decision Model (TypeSafe Jev) in pos-mcp-server'
description: Puts one typed QuestionTagger seam in front of the pos-mcp-server executor LLM, backed by a Jev-protocol decision model served by the platform's own Ollama container, with today's heuristics as the permanent fallback; replaces six scattered keyword heuristics and the dormant Gate 4 router.
resource: https://github.com/louisburroughs/durion/blob/master/docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md
path: durion/docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md
tags: [adr]
status: stable
sources: [docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md]
generated: {by: 'script:generate-knowledge-catalog.py', at: '2026-10-01T01:58:23+00:00'}
---

[Canonical ADR](https://github.com/louisburroughs/durion/blob/master/docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md) — `docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md`

**Status:** Accepted since 2026-09-30

**Related:**

* [ADR-0009](../adr/0009-backend-domain-responsibilities-guide.md)
* [ADR-0026](../adr/0026-service-contract-boundary-policy.md)
* [ADR-0046](../adr/0046-environment-log-level-policy.md)
* [ADR-0062](../adr/0062-postgres-row-level-multitenancy.md)
* [ADR-0069](../adr/0069-mcp-scope-graph-pre-llm-narrowing.md)
