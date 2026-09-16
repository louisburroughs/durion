---
type: Archive Index
title: pos-mcp-server Archive
description: Historical pos-mcp-server designs, plans, checklists, research notes and gate-run records, with the reason each was archived.
domain: general
status: historical
tags: [mcp-server, general, archive]
---

Historical designs, plans, checklists and records whose work has shipped or whose content was superseded. Kept for
provenance; do not treat any of them as current. Current behavior is in [architecture.md](../architecture.md) and the
[module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-mcp-server/README.md).

These documents moved from `durion-positivity-backend` on 2026-09-16. Paths in backticks without a repository prefix
(`src/...`, `scripts/...`, `observability/...`, `docs/...`) still refer to that repository, and to the state of it on
the date each document records. Issue numbers refer to `louisburroughs/durion-positivity-backend`.

## Phase-gate program (NL interface, Gates 0–7)

Every gate close-out issue (#1212–#1219) and both build-gap stories (#1367, #1368) are closed, so the program's
plans, runbook and evidence log are records now.

| Document | Why archived |
| --- | --- |
| [implementation_phase_gates.md](implementation_phase_gates.md) | Gate definitions for a program that has finished. |
| [implementation_checklist.md](implementation_checklist.md) | Execution log and sign-offs for Gates 0–7; all close-out issues are closed. |
| [gate-closeout-plan-1212-1219.md](gate-closeout-plan-1212-1219.md) | Sequencing for #1212–#1219; all closed. |
| [gate-verification-runbook.md](gate-verification-runbook.md) | Close-out procedure for #1212–#1219. It was un-archived on 2026-08-18 "while those issues are open"; they are closed. |
| [gate3-openapi-bridge-design.md](gate3-openapi-bridge-design.md) | Implemented and signed PASS 2026-08-08. |
| [gate4-tiered-router-design.md](gate4-tiered-router-design.md) | Implemented (#1192); tiering is dormant by default (`mcp.model.tiering-enabled=false`, #1683). |
| [gate5-rag-hybrid-design.md](gate5-rag-hybrid-design.md) | Implemented and cut over (bge-m3, hybrid lexical retrieval on by default). |
| [gate6-write-confirmation-design.md](gate6-write-confirmation-design.md) | Implemented (#1193); live verification closed with #1218. |
| [gate7-admin-observability-design.md](gate7-admin-observability-design.md) | Implemented (#785, #1195); live verification closed with #1219. |
| [phase0-fixtures-and-telemetry.md](phase0-fixtures-and-telemetry.md) | Draft Phase 0 design; the fixture formats now live in the backend's `src/test/resources/eval/README.md`. |
| [nl-interface-design.md](nl-interface-design.md) | Pre-Spring-AI (LangChain4j) master design, superseded by the architecture document and the gate designs. |

## Analytics gate, Wave 2

| Document | Why archived |
| --- | --- |
| [gate-closeout-plan-1660-1676.md](gate-closeout-plan-1660-1676.md) | #1660, #1663, #1675 and #1676 are closed. |
| [gate-runs/](gate-runs/wave-2/README.md) | Dated gate-run and baseline records (#1601, #1604, #1606, #1675, #1684, #1688). Evidence, not procedure; the active plan is [analytics-capability-plan.md](../analytics-capability-plan.md). |

## Spring AI migration

| Document | Why archived |
| --- | --- |
| [spring-ai-big-bang-migration-checklist.md](spring-ai-big-bang-migration-checklist.md) | Complete (#1197). |
| [spring-ai-migration-review.md](spring-ai-migration-review.md) | Post-migration review, written to close #1197. |
| [spring-ai-issues-delivery-plan.md](spring-ai-issues-delivery-plan.md) | All sequenced issues (#645, #778–#785) delivered; #645 is closed. |
| [mcp-hardening-session-plan.md](mcp-hardening-session-plan.md) | Session executed; every bundled issue, including #645's live check, is closed. |

## Designs and analyses

| Document | Why archived |
| --- | --- |
| [PRD-nlti-mcp-tool-registry.md](PRD-nlti-mcp-tool-registry.md) | March 2026 draft PRD; the registry and NLTI shipped in the shape the architecture document describes. |
| [master-domain-agent-design.md](master-domain-agent-design.md) | May 2026 design; implemented as `MasterAgentRegistry` and its loader. |
| [answer-resolution-ladder-design.md](answer-resolution-ladder-design.md) | Implemented (`AnswerResolutionLadder`); current behavior is under "Answer Resolution" in the architecture document. |
| [mcp-facade-reachability-1612.md](mcp-facade-reachability-1612.md) | Before/after analysis for #1612, which is closed. Regenerate a current matrix with the backend's `scripts/mcp-facade-reachability.py`. |
| [BACKLOG.md](BACKLOG.md) | Deferred items from the NL-interface work; BL-1 resolved as `pos-warranty`. Open items are in the architecture document's backlog. |

## RAG corpus

| Document | Why archived |
| --- | --- |
| [rag-hybrid-lexical-784-design.md](rag-hybrid-lexical-784-design.md) | Implemented via #1123; `mcp.rag.hybrid.lexical-enabled` defaults to `true`. |
| [rag-corpus-gap-harness-design.md](rag-corpus-gap-harness-design.md) | Implemented (#1125); the backend's `scripts/gap_harness/README.md` is the living document. |
| [rag-corpus-growth-and-flip-threshold-1124.md](rag-corpus-growth-and-flip-threshold-1124.md) | #1124 closed; the flip criterion was met. |
| [rag-corpus-growth-plan-1124.md](rag-corpus-growth-plan-1124.md) | Authoring backlog executed; the corpus grew to 39 documents. |
| `research-*-anchors.md` (waves 1–6) | Source-anchor research consumed by the wave authoring; the resulting RAG documents are in the backend's `src/main/resources/rag/`. |
| [gate5-rag-authoring-prompt.md](gate5-rag-authoring-prompt.md), [gate5-rag-SOURCES.md](gate5-rag-SOURCES.md) | One-shot G5.5 authoring prompt and source notes; the documents were authored and shipped. |
