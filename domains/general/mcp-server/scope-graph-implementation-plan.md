---
type: Plan
title: pos-mcp-server Scope Graph — Implementation Plan
description: Wave-by-wave execution plan for ADR-0069 in pos-mcp-server - tasks, order, verification and the state of each wave.
domain: general
status: draft
created: '2026-09-30'
related: [ADR-0069]
tags: [mcp-server, general, scope-graph]
---

Executes [scope-graph-spec.md](scope-graph-spec.md) for
[ADR-0069](../../../docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md). Spec section references are written "spec §n".

## Shape

Three stacked backend PRs (`durion-positivity-backend`), each inert until configuration turns it on, then one documentation PR
(`durion`). Each wave branches from the previous wave's branch and is retargeted to `main` as its base merges.

| Wave | Branch | State |
| ---- | ------ | ----- |
| 1 Graph and curated inputs | `feat/adr-0069-w1-scope-graph` | merged (louisburroughs/durion-positivity-backend#2358) |
| 2 Resolver and shadow recording | `feat/adr-0069-w2-scope-resolver` | PR open (#2364) |
| 3 Consumers | `feat/adr-0069-w3-scope-consumers` | in progress |
| 4 Documentation | `docs/adr-0069-implementation` (`durion`) | this PR |

Common verification for every backend wave, run from the repository root:

```bash
./mvnw spotless:apply -pl pos-mcp-server
./mvnw -pl pos-mcp-server -am verify
./mvnw -pl pos-archunit -am clean test        # full suite, reactor + clean
```

## Wave 1 — graph and curated inputs

Nothing here runs with `mode: off`.

| # | Task | Notes |
| - | ---- | ----- |
| 1.1 | `ScopeGraphProperties` (`mcp.scope-graph`), registered; `application.yml` block with the defaults and a comment per key; `application-test.yml` pins `mode: off` | spec §2.1 |
| 1.2 | Graph model: `NodeType`, `EdgeType` (closed enums), `NodeId`, `ScopeGraph` (immutable adjacency maps, `contentHash`, `builtAt`, `entityOptions()`, `domainOptions()`) | ADR §2, §4 |
| 1.3 | `EntityLexicon` records and `EntityLexiconLoader` (YAML → records, structural errors reported with the entity key) | spec §3.1 |
| 1.4 | `OpenApiSchemaIndex`, its builder from an `OpenAPI` + domain, and `OpenApiSchemaIndexHolder`; captured per service spec in `OpenApiDocumentFetcher` before paths are merged, and for the single-spec path | spec §2.2. Capture is skipped when `mode: off` |
| 1.5 | `ScopeGraphCatalogReader` and the JDBC implementation | spec §2.4 |
| 1.6 | `ScopeGraphBuilder`: sources → graph; returns the graph plus a list of validation findings (typed, not strings) | ADR §3; spec §2.5 |
| 1.7 | `ScopeGraphHolder`: empty snapshot, coalesced asynchronous rebuild, the four triggers, build metrics | spec §2.6 |
| 1.8 | `entities.yaml`: the first lexicon | spec §3.1. Terms in en, fr, es for every entity |
| 1.9 | `StaticDocEntry.entities`; `entities:` on all 39 entries in both preload lists; correct the six file headers to the enforced permissions and list each difference for the PR | spec §3.2 |
| 1.10 | Tests: builder over fixtures including each validation failure; lexicon loader; schema index; holder swap and coalescing; real-configuration validation under default and `alpha`; preload-list parity; header-agreement | ADR §3 validation list; spec §2.3, §4 |
| 1.11 | README: configuration keys, the RAG document metadata contract (`entities:`), how to add an entity | |

Order: 1.1–1.7 and their unit tests first (one agent); then 1.8–1.9 against the validation test until it is green (second
agent); 1.11 last.

Exit: common verification green; with `mode: shadow` set in a local run the graph builds and logs its node and edge counts and
hash; with `mode: off` no scope-graph log line appears.

## Wave 2 — resolver and shadow recording

| # | Task | Notes |
| - | ---- | ----- |
| 2.1 | `TermMatcher` (precompiled patterns, exact and folded, longest match) and identifier-pattern matching | spec §2.7 |
| 2.2 | `ScopeSet` (immutable; `empty()` with confidence `NONE`), `ScopeResolver`: seed, expand, cap | ADR §5; spec §2.8 |
| 2.3 | `ScopeCallerFilter`; `PermissionAwareMetadataFilter.isVisible` extracted to a shared static method | spec §2.9 |
| 2.4 | Resolve in `ToolSelectionEngine.selectRoleTools`; carry on `ToolSelectionResult`; publish and clear on `RequestScopedUserContext` in both session managers | spec §2.10 "Where the scope is resolved" |
| 2.5 | `ScopeTrace` on `EvalTurnTrace`, recorder methods on `ToolInvocationRecorder` / `AlphaEvalTurnTraceRecorder`, in-scope shares at turn completion | spec §2.11 |
| 2.6 | `NltiRequestTelemetry` schema 2, factory, any checked-in schema file | spec §2.11 |
| 2.7 | Metrics | spec §2.11 |
| 2.8 | Tests: seeding kinds and confidence; hop and node caps and their ordering; filter-after-expansion (a permitted node behind an unpermitted one stays, the unpermitted one never appears); SQL-parity IT; `shadow` is byte-for-byte `off` for selection, retrieval and prompt; transport parity; old trace payloads still deserialize | ADR Testing; spec §4 |

Exit: common verification green; a `shadow` turn's eval trace carries a `ScopeTrace`.

## Wave 3 — consumers

| # | Task | Notes |
| - | ---- | ----- |
| 3.1 | `ScopeRagFilter`; all-scope retriever construction when `rag` is enforced, in both managers | ADR §6; spec §2.10 |
| 3.2 | Facade slots in `ToolSelectionEngine` | spec §2.10 |
| 3.3 | `findDiscoveredByNamesForPermissions`; discovered slots in `OpenApiToolProvider`; write-capability over the union | spec §2.10 |
| 3.4 | `ScopeCardRenderer`; `SCOPE_CARD` prompt layer in both managers; layer reported in telemetry prompt layers | ADR §7; spec §2.10 |
| 3.5 | Tests: filter admits `master` and multi-domain scopes, and re-applies today's eligibility on `LOW` / `NONE`; slot tests; card tests (no user text, no unpermitted node, nothing unconnected); per-consumer switch isolation | ADR Testing; spec §4 |
| 3.6 | README: the `enforce` list and the promotion rule | ADR §9 |

Exit: common verification green; with `mode: enforce`, `enforce: []` behaviour equals `shadow`.

## Wave 4 — documentation (`durion`)

`domains/general/mcp-server/architecture.md` (Tool Selection, RAG Retrieval Pipeline),
`domains/general/mcp-server/tool-selection-architecture.md`; this plan's state table; knowledge-catalog regeneration for the
changed entries only.

## After the waves (not in this plan)

1. `mode: shadow` on alpha with synthetic seed data; read `calledToolsInScope` / `citedDocsInScope` and the fallback rates;
   tune the lexicon.
2. Per consumer, RAG filter first: a recorded gate run in `enforce` against the same run in `shadow` (§9 criteria), then the
   promotion and its evidence in the ADR Changelog.
3. When ADR-0068 is accepted and built: tag seeds, tagger option lists, and the word-list replacement.
4. `lifecycles.yaml` with domain-agent review.
