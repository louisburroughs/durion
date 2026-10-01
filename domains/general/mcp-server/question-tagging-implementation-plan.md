---
type: Plan
title: pos-mcp-server Question Tagging — Implementation Plan
description: Wave-by-wave execution plan for ADR-0068 in pos-mcp-server - tasks, order, verification and the state of each wave.
domain: general
status: draft
created: '2026-09-30'
related: [ADR-0068, ADR-0069]
tags: [mcp-server, general, question-tagging]
---

Executes [question-tagging-spec.md](question-tagging-spec.md) for
[ADR-0068](../../../docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md). Spec references are written "spec §n".

## Shape

Three backend PRs (`durion-positivity-backend`), each inert until configuration turns it on, then the operations and
documentation PR. Waves 1 and 2 are stacked; Wave 3 touches compose, scripts, dashboards and docs and can run beside Wave 2.

| Wave | Branch | State |
| ---- | ------ | ----- |
| 1 Seam, heuristic and decision-model taggers, shadow | `feat/adr-0068-w1-tagging-seam` | merged: louisburroughs/durion-positivity-backend#2367 (2026-10-01) |
| 2 Enforce per tag, router from tags, scope-graph integration | `feat/adr-0068-w2-tagging-enforce` | not started |
| 3 Operations: container, bake-off report, dashboard, docs | `feat/adr-0068-w3-tagging-ops` + `durion` docs branch | in review: louisburroughs/durion-positivity-backend#2368; durion docs: alerts and dashboard pages done; 3.4 architecture pages still to do now that #2367 is merged |

Common verification for every backend wave, run from the repository root:

```bash
./mvnw spotless:apply -pl pos-mcp-server
./mvnw -pl pos-mcp-server -am verify
./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test
```

## Wave 1 — seam, both taggers, shadow

| # | Task | Notes |
| - | ---- | ----- |
| 1.1 | **Behaviour-preservation fixture first**: capture today's decisions (simple chat, workflow state, keyword-added tools, date window, admin fast path incl. veto, compound split) for a message set in en/fr/es into a test fixture, with a test that asserts them against the current code | spec §4, written before any refactor |
| 1.2 | `TaggingProperties` (`mcp.tagging`), `application.yml` block with comments, `application-test.yml` pins `mode: off` | spec §2.1 |
| 1.3 | `QuestionTags`, `QuestionTagger`, tag value types (`internal.domain`); `TaggingQuestions` (tag definitions, instructions, criteria; `domain` options from the preload scopes; one entity Noul per lexicon entity per spec §2.4) | spec §2.3, §2.4; domain options = RAG scopes; entity Nouls |
| 1.4 | `HeuristicQuestionTagger`: the six heuristics moved behind the interface unchanged; the admin lists stay in `ToolRegistryService` and are read from there (§1) | spec §2.3 |
| 1.5 | `JevClient` (`internal.client`, plain `RestClient`, own timeouts, no retries) and `JevQuestionTagger`; answer parsing to values and confidences; every failure → provider failure with reason | spec §2.2 |
| 1.6 | `TaggingService`: mode logic, merge, fallback reasons, metrics; warm-up passes `QuestionTags.none()` | spec §2.5 |
| 1.7 | The one call per turn in both managers before `isSimpleChat` and `routeTier`; the record passed to `SimpleChatFastPath`, `routeTier`, `selectRoleTools`, published on `RequestScopedUserContext`; every consumer reads the heuristic acting value (no enforce yet); tag-added tools intersected with the gated set (§2) | spec §2.5, §2.6 |
| 1.8 | `TagTrace` on `EvalTurnTrace`, recorder methods, `NltiRequestTelemetry` schema 3 with the `tagging` block, `Routing` filled from tags | spec §2.8 |
| 1.9 | Tests: 1.1 fixture green after the refactor; `shadow` == `off` with an adversarial stub; `JevClient` stub-server matrix; request-body minimisation; instructions on every question; caps; log capture; transport parity; warm-up never tags; telemetry v3 and old payloads | spec §4 |
| 1.10 | README: configuration keys, the tagging model dependency, what `shadow` records | |
| 1.11 | NLTI domain review corrections applied (option sets, wording, trace fields) | spec §2.3, §2.8 |

Order: 1.1 alone first; 1.2–1.6 (one agent); 1.7–1.8 (same agent, after 1.6); 1.9 throughout; 1.10 last.

Exit: common verification green; with `mode: shadow` and a running Ollama 0.35 with `tev1:0.8b` pulled, a chat turn's eval
trace carries a `TagTrace` with both taggers' values; with `mode: off` no tagging log line, meter or provider call.

## Wave 2 — enforce per tag, router from tags, scope integration

| # | Task | Notes |
| - | ---- | ----- |
| 2.1 | `enforced-tags` honoured in the merge, including the `:veto` direction and `thresholds.workflow_state.non-idle`; per-tag `low_confidence` fallback | spec §2.1, §2.5 |
| 2.2 | Simple chat from `simple_chat` with the `follows_previous_turn` override | spec §2.6 |
| 2.3 | Workflow-state precedence chain for session-less callers; lexicon lookup gated on acting `intent` ACTION; `IDLE` as a value | §3.3; spec §2.6 |
| 2.4 | Tag-added tools from the four Noul tags; `lookups` replaces the inventory/order guards when enforced | spec §2.6 |
| 2.5 | Admin fast path veto (`ToolRegistryService.resolveCandidateSelection(context, topK, tags)`) | §3.4 |
| 2.6 | Compound gate in `RerankedContentRetriever` from the published tags; a model `true` widens the splitter (conjunctions without the starter-word check) | spec §2.6 |
| 2.7 | `NltiRouter.classify(tags)`: chat call removed, `safeDefault()` per field below threshold; `routerChatModel` no longer injected (bean kept until promotion, §7) | spec §2.6 |
| 2.8 | Scope graph: `ScopeResolver.resolve(..., tagSeeds)`, `MatchKind.TAG` (LOW), domain seed → that scope's `RagDoc`s; `lookups` consumer; lexicon `workflow_state` + loader validation; entity groups from the graph | spec §2.7 |
| 2.9 | Tests: each tag alone; thresholds; override; veto both directions; precedence chain; compound gate; router mapping; tag seeds LOW and domain-seed documents; `lookups` isolation; `enforce` + empty list == `shadow` | spec §4 |
| 2.10 | README: `enforced-tags`, per-tag promotion rule, precedence chain | |
| 2.11 | T0 skip: no provider call when the heuristic hits an exact `SimpleChatRuleCatalog` rule | spec §2.5 |

Exit: common verification green; `enforce` with `enforced-tags: []` equals `shadow` on every decision.

## Wave 3 — operations, bake-off report, docs

| # | Task | Notes |
| - | ---- | ----- |
| 3.1 | `docker-compose.yml`: `ollama` and `ollama-init` pinned to a 0.35+ tag; `ollama-init` pulls `${OLLAMA_TAGGING_MODEL}` beside the embedding model; `OLLAMA_MAX_LOADED_MODELS` ≥ 2 (3 shipped: room for a second candidate during a bake-off) and `OLLAMA_KEEP_ALIVE`; comments; `.env.example` (`OLLAMA_TAGGING_MODEL=tev1:0.8b`, `MCP_TAGGING_MODE=off`); `pos-mcp-server` env wiring; alpha memory note | ADR Implementation Notes → Container |
| 3.2 | `scripts/tagging_shadow_report.py` + the bake-off procedure section | spec §2.9 |
| 3.3 | NLTI overview dashboard: tagging latency, fallback by reason, agreement per tag, model in use, `ollama` container CPU and memory; Loki alert on provider-failure fallback rate > 20 % sustained (ADR Changelog 2026-10-01); `operations/alerts/nlti-alerts.md` rule 12 and `operations/dashboards/nlti-overview.md` tagging row | ADR Monitoring |
| 3.4 | `durion`: `architecture.md` (request flow: tag → simple chat → tier → selection → scope), `tool-selection-architecture.md` (keyword fallback, admin fast path, workflow state now tag-driven), this plan's state table, `scope-graph-spec.md` cross-reference for `lookups` and `MatchKind.TAG` | ADR Docs to update |
| 3.5 | `tagging-gate` fixtures en / fr-CA / es with `expected_tags`; the report scores both taggers against them | spec §2.9 |
| 3.6 | File a separate issue: extend the admin fast-path veto list with customer, supplier, vendor, bank and GL terms (behaviour change, outside the move-unchanged rule) | spec §2.3 |

## After the waves (not in this plan)

1. Pull each candidate (`tev1:0.8b`, `tev1`, `nimble`) on the alpha host; `mode: shadow`; run the gate sets in en, fr, es;
   `tagging_shadow_report.py`; record per model in the ADR Changelog; choose the smallest that fits the budget and the
   accuracy rule; set `provider.model` and `thresholds.<tag>`.
2. Promote tags one at a time into `enforced-tags` after a recorded gate run; the router tags last, then remove
   `routerChatModel` and `mcp.model.router` (§7).
3. Consider the "skip the call when the heuristic is certain" follow-up from the latency data.
