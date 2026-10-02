---
type: Specification
title: pos-mcp-server Scope Graph — Implementation Specification
description: What is built to implement ADR-0069 (scope graph for pre-LLM narrowing) in pos-mcp-server, what is deferred, and the decisions the ADR leaves to implementation.
domain: general
status: draft
created: '2026-09-30'
related: [ADR-0069, ADR-0068, ADR-0062, ADR-0026]
tags: [mcp-server, general, scope-graph]
---

Implements [ADR-0069](../../../docs/adr/0069-mcp-scope-graph-pre-llm-narrowing.adr.md). The ADR is the authority for *what* and
*why*; this document fixes the points it leaves open and states what each delivery wave contains. Where this document and the
ADR disagree, the ADR wins and this document is wrong. Section references (§n) are to the ADR.

Code paths are relative to `durion-positivity-backend/pos-mcp-server/`. All new types live under
`com.positivity.mcp.internal.scopegraph` (ADR-0026: `internal`).

## 1. What is delivered, and what is not

| Wave | Content | Runtime effect when merged |
| ---- | ------- | -------------------------- |
| 1 | Curated inputs (`entities.yaml`, `entities:` on every RAG document in both preload lists, the six header reconciliations), the graph model, builder, holder, and the build-time validation test | None. `mcp.scope-graph.mode` defaults to `off`; nothing is built or read |
| 2 | `ScopeResolver`, `ScopeSet`, the call in the shared selection seam, and shadow recording (eval trace, telemetry schema 2, metrics) | None in `off`. In `shadow` the scope is computed and recorded; no consumer acts on it |
| 3 | The three consumers that do not depend on ADR-0068: RAG scope filter, additive tool slots (facade and discovered), scope card; one switch per consumer | None until a consumer is listed in `mcp.scope-graph.enforce` |
| 4 | Documentation: `architecture.md`, `tool-selection-architecture.md`, module README | — |

**Deferred, with the reason:**

| Item | ADR | Why deferred |
| ---- | --- | ------------ |
| Tag seeds from the ADR-0068 tagger; option lists wired into the tagger | §5.1, §6 row 5 | **Done** (ADR-0068 wave 2, louisburroughs/durion-positivity-backend#2369): an `entity_<key>` tag seeds its `Entity` node at `MatchKind.TAG` (LOW); the `domain` tag is a `DomainSeed` (the `Domain` node(s) of that RAG scope plus its `RagDoc`s, no match kind, never tools), and a tag-only scope is LOW; the `domain` question is asked over the RAG-scope vocabulary ([question-tagging-spec.md](question-tagging-spec.md) §2.3) |
| Graph lookups replacing the keyword-fallback and `deriveWorkflowState` word lists | §6 row 3 | **Done as the `lookups` consumer** (ADR-0068 wave 2): when `lookups` is in `mcp.scope-graph.enforce`, the heuristic tagger reads `workflow_state` from the lexicon (acting `intent = ACTION` only) and the inventory/order guards are replaced by the scope's hop-1 facades; the word lists stay the fallback ([question-tagging-spec.md](question-tagging-spec.md) §2.6) |
| `lifecycles.yaml`, `TRANSITIONS_TO` | §3.3 | Phase 2 in the ADR. `HAS_STATE` is populated; the card prints states without "valid next states" |
| Promoting any consumer to `enforce` on alpha | §9 | Needs a recorded gate run per consumer against the same run in `shadow`. That is an operations step with its own evidence, recorded in the ADR Changelog |
| Lowering `mcp.agent.discovered-tool-limit` | Consequences | Needs the shadow data |

## 2. Decisions the ADR leaves open

### 2.1 Configuration

`ScopeGraphProperties` (`mcp.scope-graph`):

| Key | Default | Meaning |
| --- | ------- | ------- |
| `mode` | `off` | `off`: no graph is built, no scope is resolved. `shadow`: built, resolved, recorded. `enforce`: as `shadow`, and the consumers in `enforce` act |
| `enforce` | `[]` | The consumers that act when `mode` is `enforce`: any of `rag`, `tools`, `card`. **Addition to the ADR's key list**: §9 promotes each consumer separately, and one `mode` value cannot express that. With `mode: enforce` and an empty list the behaviour is `shadow` |
| `max-nodes` | 60 | §5.2 cap |
| `added-tool-slots` | 8 | §6 cap, facade and discovered together |
| `card-token-budget` | 400 | §7 |

`application-test.yml` pins `mode: off`.

### 2.2 The OpenAPI schema index (spec retention)

The graph needs, per discovered operation, the schema names of its request body and success response, and per schema the enum
values of its status field. The ADR says the builder must hold the parsed spec. Aggregate discovery merges only `Paths` from each
service spec (`OpenApiDocumentFetcher.fetchAndPrefixService`), so component schemas do not survive into the aggregate `OpenAPI`.

`OpenApiSchemaIndex` is therefore built **per service spec, before merging**, and keyed by the tool's domain (the routing prefix,
the same value `OpenApiToolMapper.extractDomain` writes to `mcp_tool.domain`):

- `operationSchemas`: tool name → the set of component schema names referenced by the request body and the 2xx response, following
  `$ref`, array `items` and one level of `allOf` / `oneOf`, and unwrapping page envelopes (a schema whose `content` / `items`
  property is an array of a `$ref`).
- `enums`: `(domain, schema name)` → property name → enum values, for properties named `status` or `state` (suffix match,
  case-insensitive).

A schema reference in the lexicon is `domain:SchemaName`, because schema names are not unique across services. The index is held
in memory by `OpenApiSchemaIndexHolder`, replaced on every successful discovery run; a failed or partial run keeps the entries of
the domains it could not fetch (the same rule as the per-prefix prune, #1632). It is not persisted. With `mode: off` it is not
built.

### 2.3 The validation fixture — deviation from the ADR text

§3 says the schema-name check runs against "a fixture aggregate in the module test", refreshed by `API Artifacts Sync`.
`pos-api-gateway/docs/openapi-aggregate.yaml` is a `$ref` index into each module's `openapi.yaml`, not a spec with schemas, so a
copy of it holds no schema names. The module test instead builds the `OpenApiSchemaIndex` **directly from the module specs in the
reactor checkout** (`../pos-*/openapi.yaml`), with the same code the runtime uses. Those files are what `API Artifacts Sync`
regenerates, so a renamed DTO reaches the test at the next sync, as the ADR intends, and there is no fixture to go stale and no
workflow change. The test fails with a clear message if no module spec is found (it is not skipped). Builder unit tests use small
hand-written fixture specs under `src/test/resources/scope-graph/`.

### 2.4 Catalog reads

The builder reads the whole catalog, not a caller's view of it. `ScopeGraphCatalogReader` (interface, in `scopegraph`) with a
JDBC implementation returns, in one pass each:

- tools: `name`, `domain`, `source`, `http_method`, `enabled`; only enabled tools become nodes;
- tool permissions: `tool name`, `permission_code`, `permission_group`;
- tool workflow states; tool prerequisites; screens (`screen_key`, `domain`, `required_perm`, `url_template`, `title`).

RAG documents come from `StaticRagPreloadProperties` (the effective configuration of the running profile), which gains
`entities` on `StaticDocEntry`. The glossary terms come from `BusinessGlossary`.

### 2.5 Runtime versus build-time strictness

The validation rules in §3 are **strict in the module test** and **lenient at runtime**. At runtime a violation is logged once
per build at WARN, counted, and degraded: an unmapped discovered tool attaches to its `Domain` only
(`mcp.scope_graph.unmapped_tools`); an unresolved lexicon reference drops that edge. A build that throws leaves the previous
snapshot in place (the empty graph on first build) and increments `mcp.scope_graph.build.failures`. The graph never fails
startup and never fails a turn: every resolver and consumer call is guarded, and a failure there is today's behaviour plus a
counter.

### 2.6 Rebuild triggers

`ScopeGraphHolder.rebuild()` runs, off the request thread and coalesced (a rebuild requested during a rebuild runs once more
afterwards):

- after `ToolBootstrapRunner` completes (startup, after tool bootstrap);
- after a `DiscoveryRefreshScheduler` refresh completes;
- after tool registration (`ToolRegistrationServiceImpl`);
- on `AgentCacheInvalidationEvent` with source `TOOL_PERMISSION`, after commit.

The snapshot swap is a single volatile write. The snapshot carries `contentHash` (SHA-256 over the sorted node and edge keys,
hex, first 16 characters) and `builtAt` (from the injected `Clock`, ADR-0024).

### 2.7 Seeding and confidence without the tagger

Seeds come from the message only (§5.1, first sentence):

- **Identifier pattern**: a lexicon regex matches → seed, `HIGH`.
- **Exact term**: a lexicon term matches on word boundaries, case-insensitively, as written → seed, `HIGH`. A term that denotes
  more than one entity seeds all of them, `LOW`.
- **Folded term**: a term matches only after folding (diacritics stripped, a trailing plural `s` / `es` / `x` removed on either
  side) → seed, `LOW`.
- Longest match wins: a term contained in a longer matched term at the same position does not seed (`work order` does not also
  seed `order`).

The scope's confidence is `HIGH` if any seed is `HIGH`, else `LOW`, else `NONE` (no seeds). Matching uses precompiled patterns
and `Matcher.find`, never `String.matches` (multi-line messages, see `ToolSelectionEngine.mentionsDateWindow`).

Which confidence each consumer acts on (§6: "`NONE`, or `LOW` for that consumer"):

| Consumer | Acts on | Reason |
| -------- | ------- | ------ |
| RAG filter | `HIGH` | A wrong filter removes the right document |
| Tool slots | `HIGH`, `LOW` | Additive; a wrong addition costs tokens only |
| Scope card | `HIGH` | A wrong card misleads the model |

### 2.8 Expansion

Breadth-first from the seed entities, two hops, in a fixed order so the cap cuts deterministically.

| Hop | From | Edges followed |
| --- | ---- | -------------- |
| 1 | seed `Entity` | `OWNED_BY` → Domain; `RELATES_TO` → Entity (both directions); `ACTS_ON` ← Tool; `ABOUT` ← RagDoc; `SHOWS` ← Screen; `HAS_STATE` → LifecycleState |
| 2 | `Entity` reached at hop 1 | `OWNED_BY`, `ACTS_ON` ←, `ABOUT` ←, `SHOWS` ← (not `RELATES_TO`: no third entity ring) |
| 2 | `Tool` reached at hop 1 | `PRODUCES_INPUT_FOR` in both directions |

`Term`, `IdentifierPattern`, `Permission` and `WorkflowState` nodes are never entered. A `Domain` is never expanded to its tools
(a domain holds up to a few hundred). Order within a hop: Entity, Domain, RagDoc, facade Tool, Screen, discovered Tool (`reads`
before `writes`), LifecycleState; then by key. Seeds are always kept; the cap applies to everything else.

### 2.9 Caller filter

Applied after expansion (§5.3). `ScopeCallerFilter` holds one predicate per source, each a mirror of the existing one:

| Node | Predicate | Mirrors |
| ---- | --------- | ------- |
| facade Tool | the caller holds every code of at least one `permission_group`; no rows → excluded; and the tool is `VALID_IN` the turn's workflow state | `ToolMetadataRepositoryImpl` AND-group SQL |
| discovered Tool | the caller holds any one code; no rows → excluded; and `VALID_IN` `IDLE` (the discovered path fixes the workflow state today) | `findDiscoveredCandidatesForPermissions` |
| RagDoc | empty `required_permissions`, or contains `AUTHENTICATED` and the caller has it, or shares a code | `PermissionAwareMetadataFilter.isVisible`, extracted to a static method both call |
| Screen | `required_perm` null, or the caller holds it | `ScreenRegistryRepositoryImpl` |

**The in-memory filter is not the security boundary.** It decides what appears in the `ScopeSet`, the trace and the card. Every
tool the scope *adds* to a request is re-gated by the existing SQL (§2.10), and documents still pass `permissionFiltered`.
A Postgres integration test asserts the in-memory tool predicates agree with the SQL for a matrix of callers.

### 2.10 Consumers

**Where the scope is resolved.** `ToolSelectionEngine.selectRoleTools` is the one selection entry point both session managers
call, so it is the "shared selection component" of §5 until ADR-0068 introduces its own. It resolves the scope after the workflow
state is known and before tool ranking, and returns it on `ToolSelectionResult`. Both managers publish it on
`RequestScopedUserContext` next to the caller (and clear it in the same `finally`), which is how the discovered-tool provider,
the RAG hook and the prompt supplier read it. The streaming manager must carry it the same way it carries the caller today.
The simple-chat fast path resolves no scope.

**RAG filter (`rag`).** `ScopeRagFilter` wraps the hybrid retriever beside `permissionFiltered`, after fusion and before the
top-5 rerank cut. When `rag` is enforced, both managers build the agent's retrievers over all scopes (as for `master` today).
Per request:

- confidence `HIGH`: keep a chunk when its `document_id` is in the scope's documents or its `rag_scope` is `master`;
- otherwise: keep a chunk when its `rag_scope` is the agent's scope or `master`; keep everything when the agent's scope is
  `master` (today's eligibility, §6).

When `rag` is not enforced, retriever construction is unchanged and the hook is not installed. Because the agent cache key does
not include the mode, the mode is read once at startup; changing it needs a restart.

**Tool slots (`tools`).** At most `added-tool-slots` tools per turn, facades first, then discovered operations with what is
left; ordered by hop, then `reads` before `writes`, then name.

- *Facades*: in `ToolSelectionEngine`, after the ranked cut. Candidates are the scope's facade tools that are present in the
  caller's gated set (`findEnabledByPermissionsAndWorkflow`, already fetched for the turn) and not already selected. Nothing is
  added when the ranked path failed closed (threw) — a gate that could not be evaluated adds nothing.
- *Discovered*: in `OpenApiToolProvider`, after the ANN cut. A new repository query,
  `findDiscoveredByNamesForPermissions(names, permissionCodes, workflowState)`, applies the same permission and workflow SQL as
  the ANN query to the scope's discovered tool names; results not already present are appended. Write-capability is recomputed
  over the union so the WRITE-GATE prompt layer stays correct.

No ranked tool is removed or displaced. Added tools count toward `recordSelectedTools` / `recordDiscoveredOpenapiTools`, and the
agent cache key (it is derived from the selected tools).

**Scope card (`card`).** `ScopeCardRenderer` renders from the caller-filtered `ScopeSet` only; the prompt supplier appends it
as a final system-prompt layer named `SCOPE_CARD` when present. Format, plain text, in this order, cut at the budget (estimated
with the module's existing token estimate) by dropping whole lines from the end:

```text
SCOPE (platform definitions for this question; orientation only, grants nothing)
Entities: workorder (domain workorder), estimate (domain workorder)
Relations: estimate promotes_to workorder
States: workorder: DRAFT, APPROVED, IN_PROGRESS, COMPLETED
Actions: WorkorderFacadeTool reads workorder [requires workorder:workorder:view]
Screens: Work Orders -> /workorders
```

An entity, relation or state line appears only when a Tool, RagDoc or Screen that passed §2.9 connects to the entity (§7). The
card never contains message text, terms, identifier patterns, or a permission code of a node the caller did not pass for. A
relation is printed only when both entities qualify.

### 2.11 Recording

`ScopeTrace` (a new nullable component on `EvalTurnTrace`, serialized with the payload; older payloads read it as null):
`mode`, `enforced` consumers, `graphHash`, `graphBuiltAt`, `confidence`, `seeds` (entity key and match kind — never the matched
text), counts of entities, tools, documents and screens, `addedTools`, `ragFilterApplied`, and, computed at turn completion,
`calledToolsInScope` / `calledTools` and `retrievedDocsInScope` / `retrievedDocs` (the final top-K passed to the model; cited documents are not knowable, there are no citation markers).

So that the ADR §9 gate can be scored offline from the traces, the trace also carries identities (added 2026-10-02,
louisburroughs/durion-positivity-backend#2386): `retrievedDocuments` (`documentId` and `ragScope` of each retrieved document,
in rank order), `scopeDocumentIds` and `scopeToolNames` (the resolved scope's documents and tools, each capped at 64 with a
`…Truncated` flag), and `addedToolNames` (the additive slots' tool names). Payloads written before the change read them as
null or empty. The gate procedure is: `scripts/gate_chat_run.sh` drives the `rag-lexical` and `rag-retrieval` fixtures
through chat in `shadow` and exports the traces; `scripts/scope_graph_gate_report.py` scores today's ranking against the
`rag` consumer's simulated filter (hit@k, MRR, recall@k, forbidden documents), lists regressions, the tools-consumer shadow
picture and documentation coverage per entity, and exits non-zero on FAIL.

`NltiRequestTelemetry` goes to `schemaVersion` 2 with `scopeMode`, `scopeGraphHash`, `scopeConfidence`, `scopeEntityCount`,
`scopeToolCount`, `scopeDocCount`, `scopeAddedToolCount`, `scopeRagFilterApplied`; all nullable, absent in `off`. Any checked-in
JSON schema and the telemetry documentation are updated in the same change.

Metrics (Micrometer): `mcp.scope_graph.build.duration`, `.build.failures`, `.nodes`, `.edges`, `.unmapped_tools`;
`mcp.scope.resolved{confidence}`, `mcp.scope.size{kind}`, `mcp.scope.fallback{consumer}`, `mcp.scope.called_tool{in_scope}`,
`mcp.scope.retrieved_doc{in_scope}`, `mcp.scope.errors`.

## 3. Curated inputs

### 3.1 `scope-graph/entities.yaml`

```yaml
domain_scopes:            # tool-catalog domain -> rag_scope (§2 Domain row)
  shop-manager: shopmanager
entities:
  - key: workorder
    domain: workorder
    terms:
      en: [work order, workorder, repair order]
      fr: [bon de travail, ordre de réparation]
      es: [orden de trabajo, orden de reparación]
    identifiers:
      - key: workorder-number
        pattern: '\bWO-\d{4,}\b'
    relates_to:
      - {entity: estimate, label: promoted_from}
      - {entity: invoice, label: billed_by}
    schemas: ['workorder:WorkorderResponse']
    facade_tools:
      - {tool: WorkorderFacadeTool, access: reads}
    screens: [workorders.list, workorders.wip]
unscoped_tools: [DateWindowFacadeTool, GlossaryFacadeTool, ExaWebSearchTool]
```

The first version covers the entities the 39 RAG documents and 18 facades are about; the working list is customer, vehicle,
workorder, estimate, appointment, invoice, payment, order, return, purchase-order, asn, stock-item, stock-transfer,
stock-adjustment, product, price, promotion, tax, location, supplier, warranty-claim, employee, user, role, journal-entry,
gl-account, financial-report. The author adds or merges entities as the documents and the OpenAPI schemas require; each addition
is reviewed in the PR. Identifier patterns are taken from `rag/glossary-identifiers.md`, not invented.

A discovered tool gets `ACTS_ON` when any of its operation schemas is in an entity's `schemas` (`reads` for GET, `writes`
otherwise). To keep the lexicon from listing hundreds of DTO names, an entity may also declare `schema_patterns` (a regex over
`domain:SchemaName`); the validation test requires every pattern to match at least one schema.

### 3.2 RAG document `entities:` and the six reconciliations

`entities:` is added to every entry of `mcp.rag.preload.docs` in `application.yml` and `application-alpha.yml`, identically.
A test asserts the two lists are equal entry for entry, so parity stops being a comment.

For the six documents whose file header disagrees with the enforced permissions, **the enforced configuration is kept and the
header is corrected to match it**. That changes no runtime behaviour. Each difference is listed in the PR description so a
reviewer can decide whether the header was the intended value; changing an enforced permission is a security decision and is
not made here.

## 4. Testing

Beyond the ADR's list (§ Implementation Notes, Testing):

- the real-configuration validation test runs under the default and the `alpha` profile;
- the preload-list parity test (§3.2);
- the SQL-parity integration test for the caller filter (§2.9);
- additive-slot tests: nothing is displaced, the cap holds across facade and discovered additions, nothing is added on the
  fail-closed path, and a tool the caller cannot use is never added even when the `ScopeSet` is forged to contain it;
- mode tests: in `off` no bean builds or resolves anything and every existing test passes unchanged; in `shadow` selection,
  retrieval and the prompt are byte-for-byte what they are in `off`;
- transport parity: the synchronous and streaming managers resolve, publish and clear the scope at the same points.

## 5. Risks

- **Streaming thread hand-off.** The scope is request-scoped state read from several places; a missed propagation in the
  streaming manager silently turns a consumer off. The transport-parity test covers it.
- **Lexicon quality decides everything downstream.** Shadow data (`calledToolsInScope`, `retrievedDocsInScope`) is the measure; no
  consumer is promoted without it.
- **Agent cache growth.** Added tools change the selected-tool set and therefore the cache key. The slot cap and deterministic
  ordering bound the number of distinct keys per role.
- **Telemetry schema 2.** Any consumer that rejects unknown fields or pins `schemaVersion` 1 must be checked before `shadow` is
  switched on in an environment that ships telemetry.
