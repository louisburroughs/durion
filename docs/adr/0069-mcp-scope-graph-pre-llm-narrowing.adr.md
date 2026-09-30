---
type: ADR
title: 'ADR-0069: Scope Graph for Pre-LLM Narrowing in pos-mcp-server'
description: Adds a generated, in-memory scope graph of entities, tools, documents, screens and permissions to pos-mcp-server that runs alongside RAG to narrow retrieval and steer tool selection and the prompt per turn; definitions only, no new datastore.
status: stable
adr_status: accepted
created: '2026-09-30'
related: [ADR-0026, ADR-0042, ADR-0044, ADR-0062, ADR-0068]
tags: [adr]
---
# ADR-0069: Scope Graph for Pre-LLM Narrowing in pos-mcp-server

**Status:** ACCEPTED **Date:** 2026-09-30 **Deciders:** Architecture, NLTI (Natural Language Task Interpretation) Domain, Security & Authorization Domain
**Affected Issues:** — (none yet; to be opened for implementation)

> **How to read this.** ✅ **Resolved** marks a decision this ADR makes (TEMPLATE.adr.md sub-decision format); accepted by the
> Platform Owner on 2026-09-30 (see Sign-Off).

---

## Context

### Current state

Before the executor LLM sees a chat turn, `pos-mcp-server` narrows two things independently:

- **Tools.** Two paths. *Facades:* `ToolRegistryService.resolveCandidateTools` runs a permission- and workflow-gated pgvector
  ANN query over `mcp_tool.embedding` for `source <> 'openapi'` rows (18 facades), scores by ANN rank plus tenant-overlaid
  priority minus latency and cost (`ToolRegistryService.java:314-334`), and cuts to `mcp.agent.candidate-tool-limit` (24 on
  alpha, which must stay at or above the facade count, #1840). *Discovered operations* (~866 of ~884 tools):
  `OpenApiToolProvider` calls `findDiscoveredCandidatesForPermissions` (`ToolMetadataRepositoryImpl.java:142-177`, OR predicate
  at `:164`), with OR-semantics permissions, the workflow fixed to IDLE, and its own cap `mcp.agent.discovered-tool-limit` (16
  on alpha). Keyword guards in `ToolSelectionEngine` add facade tools on top.
- **Documents.** The Tier-2 RAG chain (dense, query-expanded, Postgres FTS, reciprocal-rank fusion, lexical rerank to the top 5)
  searches `mcp_document_embedding`, filtered by one domain `rag_scope` plus `master` when tool selection resolves to a single
  domain, and by permissions only (all scopes) when it resolves to `master` (`ScopedContentRetrieverFactory.java:57-62`,
  `LexicalDocumentRetriever.java:85-88`, `MasterAgentRegistry.java:178-199`). Permissions are applied through
  `required_permissions` (`PermissionAwareMetadataFilter`).

The structure that relates these things already exists, but is spread across stores and never joined:

| Source | Relationships it holds |
| ------ | ---------------------- |
| `mcp_tool` (`domain` on all rows; `service_id`, `input_schema` — query params only — on discovered rows; `operation_id` exists but is never written), `mcp_tool_permission`, `mcp_tool_workflow` | tool → domain, tool → permission, tool → workflow state |
| `mcp_tool_prerequisite` (`tool_name`, `required_param`, `producing_tool`, `producing_field`) | tool → tool that produces its input |
| Gateway aggregate OpenAPI (tool discovery, `OpenApiToolMapper`): `x-required-permissions` is persisted (`OpenApiToolMapper.java:147-168`); request/response schemas and enums are in the fetched spec but not retained today — the graph builder must hold the parsed spec | operation → request/response schemas, `x-required-permissions`, schema enums (lifecycle states) |
| RAG documents (`src/main/resources/rag/*.md`, 39 files): runtime metadata comes from `mcp.rag.preload.docs` (`document_id`, `source_path`, `rag_scope`, `required_permissions`; `StaticRagPreloadServiceImpl.java:193-199`) | document → `rag_scope`, document → `required_permissions` |
| `mcp_screen_registry` (`domain`, `required_perm`, `url_template`) | screen → domain, screen → permission |
| `BusinessGlossary` (#1688, ratified) | analytical phrase → agreed metric |

The RAG file headers (20 YAML, 13 inline, 6 none) are documentation only: they are not read at runtime, and for 6 documents they
disagree with the permissions the runtime enforces. `mcp.rag.preload.docs` is itself declared twice: in `application.yml`
(`:144-315`) and repeated in `application-alpha.yml` (`:90-265`, "kept in parity", `:150-153`). A profile list replaces the base
list wholesale, so alpha enforces its own copy.

### The problem

- **Scope is inferred, never known.** Each store is searched by text similarity alone. Nothing records that "estimate",
  "quote" and "devis" name the same entity, that an estimate is promoted to a workorder, or that
  `workorder.status-lifecycle` and the workorder facade are about the same thing. Every narrowing step guesses again.
- **Scope is a by-product of tool selection.** A single-domain selection is narrowed to that domain plus `master`; any other
  outcome (a shared tool, an unowned tool, several domains) falls to `master`, which is unfiltered. Both failure modes exist:
  too tight, and no narrowing at all. A question spanning two domains (a customer's open invoices *and* their vehicle's
  workorders) falls to `master` and is not narrowed; #1180 already had to widen the single-domain filter to `master` because a
  strict single scope made the glossary unreachable.
- **Misses are selection misses.** The troubleshooting guidance in `architecture.md` treats a missing identifier as "a
  candidate-pipeline problem before changing the answer prompt". The right tool or chunk exists but falls outside the
  window. Widening the windows costs prompt tokens and tool-choice accuracy.
- **The LLM gets no map.** The prompt carries the top-5 chunks and a tool list, but not the shape of the question: which
  entities it names, how they relate, what state they can be in, and which permission the next action needs. The model
  has to reconstruct that from prose each turn.

### Drivers

- The goal is to hand the pre-LLM stage, and then the LLM, as much **pre-filtered, structured** scope as possible.
- ADR-0068 introduces typed per-turn tags. A Jev **Choice** question accepts up to 255 labelled options, so it needs a
  closed, curated option set. The RAG scope values give one for domains today; nothing in the module provides one for
  entities.
- The catalogue is small: ~884 tools, 39 RAG documents, an estimated tens of entities and a few hundred terms once the lexicon
  exists (today `BusinessGlossary` holds 10 definitions and ~35 aliases). A graph of this size fits in memory.

### Constraints

- **Tenant business records stay in their owning service.** ADR-0044 §1 classes `pos-mcp-server` as a gateway client; its R3
  and R6 (one owner per fact, minimal event-fed replicas) apply by analogy, and ADR-0026 governs the contract surface.
  ADR-0062 scopes tenant data by row-level security. A graph of actual customers, vehicles or workorders would duplicate
  systems of record across a tenant boundary.
- **Permission gating is the security boundary** and must stay first and unchanged. The graph may narrow within what the
  caller can see; it may never add what they cannot.
- Every catalog table in this module is tenant-global (`pos-mcp-server/src/main/resources/db/tenancy-global-tables.txt`; ADR-0062
  Changelog 2026-09-10, WS3 waves 12-13); the tenant-scoped `mcp_tool_priority` overlay is outside the graph. The graph
  describes the platform, not a tenant.
- The chat surface is en, fr and es in backend rule data (`SimpleChatRuleDefaults`, `CONTINUATION_CUES`); the frontend locales
  are en, fr-CA and es.

### Scope

`pos-mcp-server` only: tool selection, the Tier-2 RAG retrievers, prompt assembly, and the ADR-0068 tagging seam. The
graph's sources are all already deployed with or discovered by the module. No REST contract, platform event, permission or
database schema change; the eval turn trace and `nlti.request.telemetry` gain fields (see Implementation Notes, Telemetry
schema).

---

## Decision

### 1. A scope graph alongside RAG, not in place of it

**Decision:** ✅ **Resolved** - Add a **scope graph**: a typed graph of the platform's business entities and everything that
refers to them. It is used before retrieval and tool selection to decide *where to look*. The RAG corpus and its retrieval
pipeline stay: the graph selects which documents are eligible, and the documents still supply the prose explanations the
graph cannot hold. Live data keeps flowing through tools.

### 2. Closed node and edge vocabulary

**Decision:** ✅ **Resolved** - Node and edge types are a closed set defined in code. Adding a type is a reviewed code change.

| Node | Identity | Source |
| ---- | -------- | ------ |
| `Domain` | a facade `mcp_tool.domain` value or a RAG scope; the two vocabularies differ (`shop-manager` vs `shopmanager`; discovered-tool domains come from the URL's first path segment, `OpenApiToolMapper.java:242-246`), so the lexicon carries an explicit domain-to-scope map | tool catalog, `mcp.rag.preload.docs`, entity lexicon (domain-to-scope map) |
| `Entity` | curated key (e.g. `workorder`, `estimate`, `invoice`, `customer`, `vehicle`, `purchase-order`, `asn`) | entity lexicon (§3) |
| `Term` | normalized phrase, per language | entity lexicon, `BusinessGlossary` |
| `IdentifierPattern` | regex key (VIN, SKU, workorder number, …) | entity lexicon |
| `Tool` | `mcp_tool.name` | tool catalog |
| `Permission` | permission code; the semantics differ per source (§5.3) | `mcp_tool_permission`, `x-required-permissions`, RAG `required_permissions`, `mcp_screen_registry.required_perm` |
| `WorkflowState` | `mcp_workflow_state` value | tool catalog |
| `LifecycleState` | `<entity>.<enum value>` | OpenAPI schema enums of the entity's status field |
| `RagDoc` | `document_id` | `mcp.rag.preload.docs` |
| `Screen` | `screen_key` | `mcp_screen_registry` |

| Edge | From → To | Source |
| ---- | --------- | ------ |
| `DENOTES` | Term → Entity | entity lexicon |
| `IDENTIFIES` | IdentifierPattern → Entity | entity lexicon |
| `OWNED_BY` | Entity → Domain | entity lexicon |
| `RELATES_TO` | Entity → Entity (labelled, e.g. `promotes_to`, `billed_by`, `belongs_to`) | entity lexicon |
| `ACTS_ON` (`reads` / `writes`) | Tool → Entity | Discovered operations: OpenAPI schema → entity mapping (§3), the HTTP method giving reads/writes. Facades: the lexicon's per-entity facade-tool list with a declared `reads` / `writes` (§3); facade rows carry no method or schema (`V2__seed_mcp_server.sql:7-15`) |
| `REQUIRES` | Tool / RagDoc / Screen → Permission | existing permission metadata; the semantics differ per source (§5.3) |
| `VALID_IN` | Tool → WorkflowState | `mcp_tool_workflow` |
| `PRODUCES_INPUT_FOR` | Tool → Tool | `mcp_tool_prerequisite` |
| `ABOUT` | RagDoc → Entity | `entities:` in `mcp.rag.preload.docs` (§3) |
| `SHOWS` | Screen → Entity | entity lexicon screen list, else screen `domain` |
| `HAS_STATE` | Entity → LifecycleState | OpenAPI enums |
| `TRANSITIONS_TO` | LifecycleState → LifecycleState | curated lifecycle file (§3), phase 2 |

### 3. Generated from existing sources; the curated additions are small and reviewed as code

**Decision:** ✅ **Resolved** - The graph is **built, never hand-drawn and never LLM-extracted**. The builder reads the
sources in §2. There are three curated inputs, reviewed in PRs: the first and third in
`pos-mcp-server/src/main/resources/scope-graph/`, the second in `mcp.rag.preload.docs` (both copies, item 2):

1. **Entity lexicon** (`entities.yaml`): per entity, its owning domain, en/fr/es terms and synonyms, identifier patterns,
   related entities, the OpenAPI schema names that represent it, the facade tools that act on it (each with a declared
   `reads` or `writes`: the 18 facade rows have no HTTP method or schema to derive it from,
   `db/migration/V2__seed_mcp_server.sql:7-15`), and the screens that show it, plus the one domain-to-scope map (§2). This is
   the only new source of truth. Glossary terms link to it rather than repeating it.
2. **RAG document `entities:`**: `entities:` is declared in `mcp.rag.preload.docs`, beside `rag_scope` and
   `required_permissions`, because that is the source the runtime enforces; file headers stay optional documentation. The list
   exists in `application.yml` and in `application-alpha.yml` (Current state), and a profile list replaces the base list
   wholesale, so `entities:` and the six reconciliations below go into **both** lists (the alternative is to
   remove the alpha duplicate). The validation reads the effective configuration of the running profile, so the module test
   runs it under the default and the `alpha` profiles. The six documents whose headers disagree with the enforced permissions
   (`accounting-codes-rag.md`, `accounting-journal-entries-rag.md`, `inventory-codes-rag.md`,
   `inventory-purchase-orders-rag.md`, `customer-vehicle-guide.md`, `pricing-guide.md`) are reconciled first.
3. **Lifecycle transitions** (`lifecycles.yaml`, phase 2): allowed transitions per entity status. Business rules state these
   in prose today; the file makes them explicit. Until it exists, `HAS_STATE` is populated and `TRANSITIONS_TO` is empty.

**Build-time validation**, as a module test that fails the build:

- every enabled tool has at least one `ACTS_ON` edge, or is listed in the lexicon's explicit `unscoped_tools`;
- every RAG document declares at least one entity, or `entities: [none]` for platform-wide documents such as the glossary;
- a RAG document header, where present, must agree with its `mcp.rag.preload.docs` entry;
- every referenced entity, domain, permission and schema name resolves;
- every entity has at least one term in each of en, fr and es.

The full aggregate spec lives in `pos-api-gateway/docs/openapi-aggregate.yaml`; the module's test resources hold only a minimal
aggregate. The schema-name check therefore runs against a fixture aggregate in the module test, and against the discovered spec
at startup on `alpha`. The fixture is refreshed from `pos-api-gateway/docs/openapi-aggregate.yaml` by the same
`API Artifacts Sync` run that regenerates that file, so a renamed DTO reaches the module test at the next sync, and the
startup check on `alpha` catches it earlier. Tools discovered at runtime that match no lexicon schema are attached to their
`domain` only and counted by a metric, so a new service degrades to today's behaviour instead of failing startup.

Two sources are sparse today: `mcp_tool_prerequisite` has 2 seeded rows and `mcp_screen_registry` has 3. Screens resolved from
the frontend site-map fallback (`ScreenLinkResolverImpl`, role-gated) are outside the graph.

### 4. In memory, rebuilt with the catalog; no new tables or datastore

**Decision:** ✅ **Resolved** - The graph is an **immutable in-memory snapshot** (plain adjacency maps, no new dependency),
built at startup after tool bootstrap and rebuilt, then swapped atomically, whenever the catalog changes: on tool
registration, on a `DiscoveryRefreshScheduler` refresh, or on a `ToolPermissionAdminService` grant or revoke
(`AgentCacheInvalidationEvent.toolPermissionChanged`). `DiscoveryRefreshScheduler` is opt-in
(`mcp.server.discovery-refresh.enabled`), so it is not a trigger where it is off. Everything in the graph can be derived again
from its sources, so it is not persisted. It carries a content hash and build timestamp, which are recorded on each turn's
eval trace so a turn can be tied to the graph version it used.

### 5. Per-turn scope resolution

**Decision:** ✅ **Resolved** - A `ScopeResolver` runs once per turn, through the same shared selection component as ADR-0068
tagging, after the tagging step and before tool ranking. It produces an immutable `ScopeSet`:

1. **Seed.** Link entities from the message by lexicon term match and identifier patterns (no model call). When ADR-0068
   tagging is enabled, add its `domain` and `entity` answers at or above threshold, reading the tags a consumer would act on:
   the heuristic result while ADR-0068 is in `shadow`, the Jev result in `enforce`. The graph supplies those Choice questions'
   option lists, so Jev chooses among real entities and domains. The option lists are global and never filtered per caller
   (ADR-0068 §4 keeps the request caller-independent).
2. **Expand.** Walk at most **two hops** from the seeds over a fixed edge whitelist per hop, capped at
   `mcp.scope-graph.max-nodes` (default 60).
3. **Filter by caller.** Drop every Tool, RagDoc and Screen the caller cannot reach, and every Tool not `VALID_IN` the turn's
   workflow state. Permission semantics differ per source, so the filter reuses each source's existing predicate instead of one
   uniform `REQUIRES` check: a facade tool qualifies when the caller holds every code of at least one of its permission groups
   (`ToolMetadataRepositoryImpl.java:43-46,119-125`); a discovered tool qualifies on any one of its codes (OR, `:150-164`); a
   RAG document is visible when its `required_permissions` is empty (public), contains the `AUTHENTICATED` sentinel, or shares
   at least one code with the caller (`PermissionAwareMetadataFilter.java:21-24`); a screen has a single nullable
   `required_perm`. A tool with no permission rows stays excluded. Filtering runs *after* expansion, so the walk does not depend
   on the caller: a permitted node reached through an unpermitted one stays in scope, and the unpermitted node itself never
   appears in the scope set or the scope card.
4. **Result.** Seed and reached entities, domains, tool names, `document_id`s, screen keys, and a confidence (`HIGH` when a seed
   came from an identifier pattern or an exact term, `LOW` otherwise, `NONE` when there are no seeds).

### 6. How the scope set is used

**Decision:** ✅ **Resolved** - Each consumer applies the scope set, and each falls back to today's behaviour when confidence
is `NONE`, or `LOW` for that consumer:

| Consumer | Use | Fallback |
| -------- | --- | -------- |
| RAG retrieval (dense, expanded, lexical) | Filter `document_id IN (scope docs) OR rag_scope = 'master'`, applied through a request-scoped hook after fusion and before the top-5 cut (below). In `enforce`, this replaces the single-domain `rag_scope` filter. | Today's eligibility re-applied by the hook (one domain plus `master`, or all scopes when `master`) |
| Tool selection | Scope tools are **added on top of** both cuts (the facade ANN cut and the discovered-operation cut), at most `mcp.scope-graph.added-tool-slots` (default 8) added, the way the keyword fallback adds facade tools today. They never remove or displace a ranked tool (the same rule as ADR-0068 §3.2), and the scope never excludes a permitted tool. | Today's ranking unchanged |
| Keyword fallback tools, `deriveWorkflowState` | Graph lookups (entity → facade tool through the lexicon's facade-tool list, entity → workflow state) replace the word lists | Word lists remain in `HeuristicQuestionTagger` (ADR-0068) |
| Prompt | A **scope card** appended to the system prompt (§7) | No card |
| ADR-0068 tagger | Option lists for the `domain` and entity Choice questions | Static option list for `domain`; the `entity` question is not asked without the lexicon |

Tools get additive slots rather than a hard filter because a missing tool fails the turn, while an extra one costs a few
prompt tokens. Tools added by ADR-0068 tags (today's keyword-fallback facades, few and facade-only) and tools added by the scope
set are unioned on top of the ranked cuts; the `added-tool-slots` cap counts scope-added tools only.

The keyword-fallback and workflow-state word lists have one owner and one reader: they live in `HeuristicQuestionTagger` and,
once the lexicon exists, are read from `entities.yaml` (the admin lists stay in `ToolRegistryService`, ADR-0068 §1); on the
primary path graph lookups replace them.

Retrievers are built per cached agent with a fixed scope (`SessionAgentManager.java:466-499`), so the scope filter is applied
through a request-scoped hook like `permissionFiltered` (`:584-595`), after fusion and before the top-5 cut. Such a hook can
only narrow: it cannot admit a document the agent's fixed-scope retrievers never fetched. For the scope set to reach a second
domain's documents, the retrievers must therefore be built over all scopes, as the `master` agent's are today, and the scope
set plus `master` does the narrowing per request. That construction applies only where the RAG consumer is in `enforce`; in
`off` and `shadow` today's scoped retrievers stay (`ScopedContentRetrieverFactory.java:58-67`,
`LexicalDocumentRetriever.java:85-88`). On a `NONE` or `LOW` turn under `enforce`, the hook re-applies the agent's own
`rag_scope IN (domain, 'master')`. That reproduces today's eligibility but not today's candidate pool: the 20-candidate fusion
pool (`SessionAgentManager.java:81,474-488`) is then drawn from all documents and cut to the eligible ones only after fusion
(`:495-497`). A single-domain agent's own documents stay eligible, but fewer than five in-scope survivors is possible, which
is the recall@5 risk the §9 gate measures. A question whose tool selection fell to `master` (unfiltered today) is narrowed to
the scope's documents plus `master`.

### 7. The scope card

**Decision:** ✅ **Resolved** - A compact, typed block of at most `mcp.scope-graph.card-token-budget` (default 400) tokens:
the recognised entities and their relationships, the entity lifecycle states and, once `lifecycles.yaml` exists, valid next
states, the permission each surfaced action requires, and the deep link to the relevant screen. The card is rendered only
from graph nodes the caller passed §5.3 for: an entity, relationship or lifecycle state appears only when at least one Tool,
RagDoc or Screen the caller passed §5.3 for connects to it, and `Term` and `IdentifierPattern` nodes never appear. It never
echoes user text and never carries instance data. It orients the model; it grants nothing, and tool calls are still
authorised per call.

### 8. Definitions only, never instance data

**Decision:** ✅ **Resolved** - The graph holds platform definitions only: entity *types*, their terms, tools, documents,
screens, permissions and states. It never holds a tenant's records (a specific customer, vehicle or workorder) or anything
read from another service's data. A per-conversation entity memory ("that customer" → a party id) is out of scope. It
would be tenant-scoped conversation state under ADR-0062 §11 and needs its own decision.

### 9. Rollout: off → shadow → enforce, gated on the eval harness

**Decision:** ✅ **Resolved** - `mcp.scope-graph.mode` is `off` | `shadow` | `enforce` (default `off`).

- **shadow**: the graph builds, the scope set is computed and recorded on the eval turn trace (seeds, confidence, scope
  sizes, whether the tools the model actually called and the documents it cited were inside the scope). No consumer acts
  on it.
- **enforce**: consumers apply §6. Each consumer is promoted separately.

A consumer is promoted only after a recorded gate run shows, against the same run in `shadow`, no regression in RAG hit@5,
MRR and recall@k (the existing `rag-lexical` and gate fixtures), no increase in forbidden-document violations, and a tool
selection hit rate at least equal. The promotion and its evidence are recorded in this ADR's Changelog.

### Out of scope

- Replacing the RAG corpus, embeddings or fusion pipeline.
- An external graph database, a graph query language, or an admin endpoint for browsing the graph (any endpoint would carry
  the full OpenAPI → SDK chain and its own permission).
- A new OpenAPI vendor extension for entities. Schema-name mapping in the lexicon is used instead, so ADR-0042 is not
  amended; revisit if the mapping proves brittle.
- Instance data and conversation entity memory (§8).

---

## Alternatives Considered

1. **Replace RAG with the graph.** Rejected. The corpus is prose (lifecycles, playbooks, code tables) and the model needs it
   as prose; a graph can say *which* document answers a question but not *what it says*.
2. **LLM-extracted GraphRAG (entities and relations mined from documents by a model).** Suits large unstructured corpora.
   This corpus is small and curated, and the structure already exists in typed sources. Extraction would add model cost,
   non-determinism and unreviewable edges.
3. **Neo4j or another graph database.** A new datastore to operate, secure and back up, outside Postgres row-level security,
   for a graph of a few thousand nodes that fits in memory.
4. **Apache AGE (openCypher in Postgres).** Adds an extension to every Postgres image and a query language to the team for
   one- and two-hop walks that plain maps do in microseconds.
5. **Persist the graph in `mcp_kg_node` / `mcp_kg_edge` tables.** Adds a migration, tenancy classification and a
   synchronisation problem for data that is fully derivable. Revisit if the graph needs runtime editing.
6. **Instance-level knowledge graph (actual customers, vehicles, workorders).** Rejected under ADR-0044 (R3 and R6, by
   analogy for a gateway client), ADR-0026 and ADR-0062: it duplicates systems of record, crosses service walls and tenant
   boundaries, and goes stale.
7. **Tune the existing pipeline (wider windows, lower similarity floors, more keyword lists).** The status quo. Every
   widening costs prompt tokens and tool-choice accuracy, and the word lists are the brittleness ADR-0068 sets out to remove.
8. **Use `durion/knowledge-catalog` as the graph.** It indexes ADRs, domains, modules and platform documents for engineering
   agents, is not deployed with the service, and has no entity, tool or document granularity.

---

## Consequences

### Positive ✅

- ✅ One shared model of the business narrows every pre-LLM step. Tools, documents, screens and tags agree on what the
  question is about instead of each guessing.
- ✅ Multi-domain questions retrieve from each domain involved: a question that names two entities is narrowed to both
  domains' documents plus `master`, instead of one domain (single-domain selection) or every scope (`master`).
- ✅ Smaller, better-aimed windows: scope tools are added on top of the ranked cuts, so once the shadow data supports it the
  discovered-operation cap (`mcp.agent.discovered-tool-limit`, 16) can come down, cutting prompt tokens and wrong-tool calls.
  The facade limit cannot: `McpServerPropertiesDefaultsTest` asserts alpha's limit is at least the scanned facade count (18
  today).
- ✅ The LLM starts from a scope card that names the entities, states and required permissions, instead of reconstructing
  them from chunks.
- ✅ ADR-0068's Choice questions get a closed, curated, trilingual option set.
- ✅ The keyword-fallback and workflow-state word lists have one owner (`HeuristicQuestionTagger`) and, once the lexicon
  exists, one reviewed source (`entities.yaml`), with fr/es terms checked by a test; the primary path replaces them with graph
  lookups.
- ✅ No new infrastructure. The graph is in memory, derived from deployed sources, and versioned by hash on every trace.

### Negative ⚠️

- ⚠️ **Over-narrowing risk.** A wrong seed could steer retrieval away from the right document. Mitigated by per-consumer
  fallback, additive tool slots rather than exclusion, `master` documents always admitted, and shadow evidence before
  enforcing.
- ⚠️ **New curated artefact.** The entity lexicon, the `entities:` annotation in `mcp.rag.preload.docs` and later
  `lifecycles.yaml` must be maintained as services change. Mitigated by build-time validation and a metric for unmapped
  discovered tools.
- ⚠️ **Schema-name mapping is brittle.** A renamed DTO silently detaches a tool from its entity. Mitigated by the validation
  test, which fails when a lexicon schema name matches nothing in the fixture aggregate (refreshed from
  `pos-api-gateway/docs/openapi-aggregate.yaml` by the `API Artifacts Sync` run, so a rename reaches the test at the next sync),
  and by the startup check against the discovered spec on `alpha`, which catches it earlier.
- ⚠️ **Up-front work.** Every RAG document needs an `entities:` annotation in both `mcp.rag.preload.docs` lists, and six documents
  whose headers disagree with the enforced permissions must be reconciled first (in both lists).
- ⚠️ **Lifecycle transitions are a second copy of business rules** once `lifecycles.yaml` exists. Mitigated by keeping it
  phase 2 and by domain-agent review of each change.

### Neutral

- The graph lives entirely inside `pos-mcp-server`; nothing new crosses a service boundary.
- Per-turn cost is a few in-memory hops and one request-scoped filter over the fused RAG candidates: sub-millisecond compared
  to the model call.

---

## Implementation Notes

- **Components:** `ScopeGraph` (immutable snapshot), `ScopeGraphBuilder` (sources → graph, validation),
  `ScopeGraphHolder` (atomic swap on catalog change), `ScopeResolver` (§5), `ScopeSet`, `ScopeCardRenderer` (§7). Consumers
  updated: the RAG retrieval chain in both session managers (a request-scoped scope filter beside `permissionFiltered`, and
  retriever construction over all scopes where the RAG consumer is in `enforce`, §6), `ToolRegistryService` (the facade cut) and
  `OpenApiToolProvider` (`OpenApiToolProvider.java:193,221`, which owns the discovered-operation cut), the two places that add
  scope tools, `ToolSelectionEngine` (graph lookups), prompt assembly in both session managers, and the ADR-0068 tagger (option
  lists). All types are `internal` (ADR-0026). The resolver is called through
  the shared selection component, so both session managers resolve scope at the same point (transport parity).
- **Configuration:** `mcp.scope-graph.mode` (`off`), `max-nodes` (60), `added-tool-slots` (8), `card-token-budget` (400).
  `application-test.yml` pins `mode: off` except in the graph's own tests.
- **Data:** `src/main/resources/scope-graph/entities.yaml` (including the per-entity facade-tool list); `entities:` in both
  `mcp.rag.preload.docs` lists (`application.yml`, `application-alpha.yml`); phase 2 `lifecycles.yaml`. No Flyway
  migration.
- **Testing:** builder tests over fixture sources, including the validation failures; resolver tests for seeding,
  hop and node caps, and permission filtering after expansion (a permitted node reachable only through an unpermitted one is
  still reachable, and an unpermitted node never appears); filter tests showing the `document_id` scope filter admits `master`
  documents and multi-domain scopes; a card renderer test proving the card contains no user text, no node the caller lacks
  permission for, and no entity, relationship or state that no permitted Tool, RagDoc or Screen connects to.
- **Rollout:** annotate and validate first (build fails on gaps), then `shadow` wherever the `alpha` profile runs on synthetic
  seed data (chat orchestration exists only under `@Profile("alpha")`), then `shadow` on alpha environments holding real data,
  then per-consumer `enforce` per §9. RAG filter first (cheapest to measure), then additive tool slots, then the scope card.
- **Telemetry schema:** `nlti.request.telemetry` is `schemaVersion` 1 (`NltiRequestTelemetry.java:52-70`), so the new scope
  fields are a schema version bump.
- **Monitoring:** scope confidence distribution, scope sizes, share of called tools and cited documents inside the scope
  (shadow and enforce), fallback rate per consumer, unmapped discovered tools, graph build duration and hash per deploy.
- **Docs to update on implementation:** `domains/general/mcp-server/architecture.md` (Tool Selection, RAG Retrieval
  Pipeline), `domains/general/mcp-server/tool-selection-architecture.md`, and the `pos-mcp-server` README (configuration,
  RAG document metadata contract).

---

## References

- **Related ADRs:** [ADR-0068](0068-mcp-pre-llm-question-tagging-decision-model.adr.md) (tagging seam; this ADR supplies
  its option sets and consumes its tags), [ADR-0044](0044-platform-event-only-domain-walls.adr.md) (§1 gateway client; R3 and
  R6 apply by analogy, hence definitions only), [ADR-0026](0026-service-contract-boundary-policy.adr.md) (contract surface),
  [ADR-0062](0062-postgres-row-level-multitenancy.adr.md) (catalog tables are tenant-global, Changelog 2026-09-10, WS3 waves
  12-13; conversation state is tenant-scoped), [ADR-0042](0042-openapi-annotation-standards.adr.md) (OpenAPI as a graph
  source; not amended).
- **Related Documentation:** `domains/general/mcp-server/architecture.md`, `domains/general/mcp-server/tool-selection-architecture.md`.

**Documents affected on acceptance** (listed only; each amended document carries a dated amendment block pointing here,
applied on acceptance, not by this ADR):

| Document | Change | When |
| -------- | ------ | ---- |
| `domains/general/mcp-server/archive/gate5-rag-hybrid-design.md` | Retrieval pipeline: the scope filter changes from one `rag_scope` to a `document_id` set plus `master`; fusion and rerank unchanged; dated amendment block | On acceptance |
| `domains/general/mcp-server/archive/README.md` | "Why archived" row for the gate 5 design | On acceptance |
| `docs/adr/README.md` | Index row (the decision matrix stops at ADR-0062 and is not extended here) | On acceptance |
| `domains/general/mcp-server/architecture.md`, `domains/general/mcp-server/tool-selection-architecture.md` | Already listed under Implementation Notes | On implementation |

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Platform Owner | Louis Burroughs | 2026-09-30 | Accepted as written. ADR-0068 is still pending: the §5.1 tag seeds and the §6 tagger option lists take effect when ADR-0068 is accepted; until then the §6 fallbacks apply |
| Architecture | | | |
| NLTI Domain | | | |
| Security & Authorization | | | §5.3 filter order, §7 card contents |

---

## Timeline

- **Proposed**: 2026-09-30
- **Accepted**: 2026-09-30

---

## Changelog

- **2026-09-30**: Initial draft.
- **2026-09-30**: Review round (PR #525): both tool paths and the `master` scope described; RAG metadata source is
  `mcp.rag.preload.docs`; per-source permission predicates; additive tool slots; card visibility rule; citations corrected.
- **2026-09-30**: Accepted by the Platform Owner. Documents listed under "Documents affected on acceptance" amended.
- **2026-09-30**: ADR-0068 accepted. The §5.1 tag seeds and the §6 tagger option lists take effect when its tagger is built; until then the §6 fallbacks still apply.
