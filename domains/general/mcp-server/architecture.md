---
type: Architecture
title: pos-mcp-server Architecture
description: How pos-mcp-server selects tools, answers, retrieves context, tunes priorities and isolates tenants.
domain: general
status: current
tags: [mcp-server, general]
---

Canonical design documentation for the `pos-mcp-server` module in `durion-positivity-backend`. Setup, endpoints, configuration
and startup behavior stay in the [module README](https://github.com/louisburroughs/durion-positivity-backend/blob/main/pos-mcp-server/README.md).
Paths in backticks (`src/...`, `scripts/...`, `docs/...`) are relative to the backend repository or module.

## Architecture

```
Frontend / MCP clients
   │  (JWT → gateway → X-Authorities perm_bits)
   ▼
pos-mcp-server (Spring Boot 4.0.x, Java 25)
   ├─ SessionAgentManager / StreamingSessionAgentManager
   │     per-user agent cache (Caffeine, TTL) keyed by role::toolCacheKey
   │     ├─ permission-gated candidate tool set (ToolRegistryService)
   │     ├─ Exa web-search tool (always included)
   │     ├─ Tier-2 RAG ContentRetriever (shared pgvector store)
   │     └─ per-session ChatMemory (MessageWindowChatMemory + semantic store)
   ├─ Spring AI runtime — ChatModel / StreamingChatModel with
   │     tool-calling callbacks + Ollama embedding model
   ├─ Tool discovery (internal/discovery/) — fetch gateway aggregate OpenAPI,
   │     map operations → mcp_tool rows, build HTTP proxies
   ├─ Audit + adaptive tuning — every selection/execution logged; daily cron
   │     recomputes priority
   └─ Observability — Micrometer / OpenTelemetry, NLTI audit ledger
        │
        ├─ Ollama (chat + embedding model runtime)
        ├─ PostgreSQL + pgvector (registry, RAG, audit, chat memory)
        └─ Exa (external web-search SaaS)
```

The previous external orchestration platform was replaced by in-process orchestration. Prompt construction,
tool-use planning (tool-calling protocol), retrieval, and model abstraction now run locally in the Spring AI
runtime.


## Tool Selection

Tool visibility is **permission-gated**, not role-gated. On each chat request `ToolRegistryService.resolveCandidateTools()`
narrows the active tool set:

1. `ToolMetadataRepository.findTopKByEmbeddingForPermissions()` runs a pgvector ANN query (`<=>` cosine distance)
   against `mcp_tool`, `mcp_tool_permission`, and `mcp_workflow_state`. Only tools the caller's `permissionCodes`
   satisfy, and that are valid for the current workflow state, enter the scoring window. Gating happens **inside**
   the query, before the top-K cut.
2. `ToolScorer` ranks candidates by a weighted blend of semantic similarity and normalized priority
   (`Math.clamp(priority, 0.0, 1.0)`).
3. If no embeddings are stored yet, a deterministic fallback returns gated tools sorted by `priority DESC, name ASC`.
4. Selection is capped at `mcp.agent.candidate-tool-limit` (default 8; 24 on `alpha`, which must stay above the facade
   count, #1840). Discovered OpenAPI operations have their own cap, `mcp.agent.discovered-tool-limit`, which defaults
   to the candidate limit (16 on `alpha`).
5. `ToolSelectionEngine.fallbackToolsForMessage()` then adds keyword-matched tools **on top of** that cut,
   so they never displace a semantically ranked one: Exa web search (`current`, `news`, `online`, …),
   inventory (`stock`, `sku`, …), order (`order`, `po`, `sale`, …), and — since #1684 — `DateWindowFacadeTool`
   on calendar vocabulary (`month`, `quarter`, `year`, `week`, `days`, `ytd`, `to date`, `since`, …).
   The date-window tool has to be reachable this way: the `DATE_WINDOW` prompt layer requires
   `resolveDateWindow` before every dated tool argument, and nothing in a question like "which customers
   haven't bought in the last 90 days" ranks a date-arithmetic tool description highly, so the embedding
   cut alone would leave the model instructed to call a tool it cannot see.
6. The resolved agent is cached keyed by `role::toolCacheKey` (sorted tool names joined with `+`) and expires after
   `mcp.agent.cache-ttl-minutes` (default 30) so DB priority/prompt edits take effect without restart.

**AND-group gating (V40, #1606).** Every `mcp_tool_permission` row belongs to a `permission_group`, and a facade
tool is offered **iff the caller holds every code of at least one group**. A group is one `@Tool` method's required
permission codes, named after that method (`getCustomer`, `calculateTax`, …). Consequences:

- A composition's group contains only its `.require()`d legs. Optional legs contribute nothing, because
  `ToolComposition` degrades them individually — the section reports its own `not_authorized` status while the rest
  still answer.
- A method that requires no codes (e.g. `CustomerFacadeTool.getCustomerHistory`, which `.require()`s no leg)
  contributes **no group at all**. An empty group would make `bool_and` vacuously true and admit every caller.
- This replaced a flat OR over the union of a tool's codes, under which a composition's _least_-privileged leg
  admitted the whole tool: a technician holding only `workorder:workorder:view` was offered `CustomerFacadeTool`,
  whose `getCustomer` needs `crm:party:view` and 403s downstream (#1606 finding 1).
- Discovered (`source='openapi'`) operations deliberately keep **OR** semantics. That is enforced by the data, not a
  second query: each of their rows is its own singleton group (`permission_group = permission_code`), for which
  AND-within-a-group and OR coincide. `ToolMetadataRepositoryImpl.addToolPermission` writes the same shape.

**Fail-closed:** a tool with zero `mcp_tool_permission` rows is never selected for any caller — the qualifying
predicate is an `EXISTS` over that tool's groups, and `EXISTS` over no rows is false. The `AUTHENTICATED`
sentinel marks operations available to any authenticated caller.

**Permission-code extraction:** `CurrentUserContextResolver` derives bare `domain:resource:action` codes from the
`Authentication` authorities (mixed `ROLE_*`, `PERM_*`, and bare forms) and always includes `AUTHENTICATED`.

> **Workflow state (#778):** both session managers resolve the caller's persisted `NltiSession.workflowState`
> (their most-recently-updated session) and thread it into tool selection, so non-IDLE tool sets (`CREATING_PO`,
> `RECEIVING_ASN`, `INVENTORY_RECON`, `PROCESSING_RETURN`) activate when a session is in that state. Callers with no
> session fall back to message-heuristic derivation. Advance a session's state explicitly via
> `POST /v1/nlt/sessions/{sessionId}/workflow-state` (ownership-checked; guarded by `nlti:request:submit`).

### Facade tools

18 curated facade tools live in `internal/orchestration/tools/`: Accounting, Admin, Catalog, Customer, DateWindow,
Events, Glossary, Hr, Inventory, Invoice, Location, Order, Pricing, Reporting, ShopManager, Tax, Vehicle and
Workorder. The always-on Exa web search (`ExaWebSearchTool`) sits beside them and is not counted as a facade. The facades are the **primary curated natural-language surface** (#1519): the common
intents — lookup, search, status, and summary per domain — are answerable through facade tools alone, while the
OpenAPI-discovered operations (next section) complement the long tail. Every `@Tool` method calls a real backend
endpoint via a `@LoadBalanced` RestClient through the gateway, with one exception: `DateWindowFacadeTool`
(#1675) makes no HTTP call at all — it resolves a relative date range to concrete dates with pure `java.time`
arithmetic (`DateWindowResolver`) off the shared `Clock` bean, so every other date-taking tool call is preceded
by a resolver round instead of model-computed dates. The other split client is TaxFacadeTool, which uses
both: a direct (non-load-balanced) RestClient to `pos-tax:8091` for the calculate leg — pos-tax is internal-only and
unreachable via Eureka or the gateway (ADR-0021, #641) — and a load-balanced gateway client for everything pos-tax
does not serve (the location lookup feeding the calculation, and the accounting tax-liability report behind
`getTaxSummary`).

**Composition tools.** Where no single service publishes the resource a facade names (shop status, financial
summary, price-for-SKU, …), the facade coordinates multiple real service calls (`ToolComposition`) and returns a
sectioned JSON envelope: `{"composition":..,"status":..,"sections":{..},"sources":[..]}` with `status` `ok` or
`degraded`. Each downstream leg renders its own section; a failed leg degrades the answer instead of failing the
tool, and a 401/403 leg renders as `not_authorized` without relaying the downstream response body. The current
compositions and their legs:

| Tool                  | Downstream legs                                                                                                                               |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `getFinancialSummary` | accounting income-statement + balance-sheet + trial-balance                                                                                   |
| `getRevenueReport`    | accounting income-statement (revenue lines) + aged-receivables                                                                                |
| `getCustomerHistory`  | CRM snapshot + interactions + invoice line-item search by `partyId` (de-duped by invoice) + workorder search by customer                      |
| `getShopStatus`       | location record + shop-manager schedule board + workorder workexec WIP                                                                        |
| `getShopQueue`        | workorder workexec WIP + shop-manager schedule board                                                                                          |
| `getPriceForSku`      | catalog detailed product search (active MSRP); a supplied `locationId` adds a dependent effective-price leg fed by the first leg's product id |
| `calculateTax`        | gateway location lookup (destination address) + direct pos-tax `POST /v1/tax/calculate`                                                       |
| `getTaxRate`          | gateway location lookup (destination address) + direct pos-tax `GET /v1/tax/rates`                                                            |

**Contract chain.** What keeps the facades honest: every `@Tool` method's verb + path lives in
`src/test/resources/facade-contract.yaml` (compositions list every leg), and facade tests derive their
MockRestServiceServer expectations from that manifest — never from string literals duplicating the configuration —
with `FacadeContractManifestTest` locking each manifest template to its `application.yml` default. Independently,
`scripts/check-mcp-facade-paths.py` resolves every configured template and manifest entry through the gateway route
table and validates verb + path against the routed module's `openapi.yaml` (route-aware, verb-checking, with
enum-expansion annotations for constrained path segments like the event-summary window). The checker runs in CI
(`.github/workflows/pr-checks.yml`) with `scripts/mcp-facade-paths-baseline.json` gating new breaks — the baseline
is currently empty, so any new mismatch fails the build.

**Deferred methods.** None. The three methods previously removed for lacking a real backend endpoint —
`getEventHistory` (#1521), `getTaxRate` (#1522), and `searchEmployees` (#1523) — are all restored: pos-event-receiver
now serves per-entity event history (`GET /v1/events?entityId=`), pos-tax now serves a jurisdiction rate lookup
(`GET /v1/tax/rates`), and pos-people now serves employee search (`GET /v1/people/employees?q=`).

Permission mappings for these tools are seeded by migration `V18` (retargeted by `V35`/`V36`); the #1519
re-derivation migration (`V37`) re-derives the seeds against the restored targets above, `V38` adds
`tax:rates:view` to TaxFacadeTool for the restored `getTaxRate`, `V39` re-derives AccountingFacadeTool for the W1.2
aging methods, and `V40` (#1606) repartitions the 16 facades that existed then into per-method AND-groups. `V40`'s header carries the
full tool → group → codes derivation table; `FacadeToolPermissionSeedTest` replays the whole chain and asserts it.

The seed mirrors each downstream controller's _declared_ authorization, not the product intent of the facade: for
every backend endpoint a `@Tool` method calls, the merged class + method `@PreAuthorize` is read and
`hasAuthority('X')` / `hasAnyAuthority('X','Y')` contribute codes `X`, `Y`. Since `V40` those codes are grouped per
`@Tool` method rather than unioned across the tool class, and only a composition's `.require()`d legs contribute.
Some facade reads therefore fall back to the `AUTHENTICATED` sentinel instead of a "normal" business
permission code:

- **`isAuthenticated()` or no `@PreAuthorize` at all** (e.g. Order and Pricing reads, EventSummaryController) — there
  is no permission-coded guard for MCP to copy, so the seed uses the `AUTHENTICATED` sentinel by design. Note this
  reflects MCP's own selection gate, not a claim about the downstream endpoint: an endpoint with no `@PreAuthorize`
  (like EventSummaryController) declares no gate of its own.
- **Role-only guards** (`hasRole(...)`, e.g. Catalog reads) — `mcp_tool_permission` stores permission codes, not role
  names, so role-only controller guards cannot be mirrored here; the role gates are still enforced in the downstream
  service via Spring Security.

> **Do not edit `V18` (or any applied migration) in place** — even comment-only changes alter the Flyway checksum and
> fail validation on deployed environments. Document rationale here or add a new migration instead. (This is also why
> V18's in-file comment saying role gates are "enforced separately at the gateway" is left as-is despite being
> imprecise — the corrected statement is the one above: role authorization happens in the downstream service's
> Spring Security, the gateway only authenticates and forwards identity headers.)

### OpenAPI-discovered tools

`ToolBootstrapRunner` calls `ToolRegistrationService.registerDiscoveredTools()` on startup. `OpenApiDocumentFetcher`
pulls the gateway aggregate spec (`pos-api-gateway`, configurable via `MCP_AGGREGATE_SPEC_URL`), `OpenApiToolMapper`
maps operations to `mcp_tool` rows, and `OperationProxyFactory` builds the HTTP proxy used to execute a call.
`OpenApiToolProvider` then resolves permission-gated discovered operations into dynamic Spring AI `ToolCallback`s per
request. These expand the candidate pool beyond the facades.

Discovery runs once at startup. Set `mcp.server.discovery-refresh.enabled=true` (interval
`mcp.server.discovery-refresh.interval-ms`, first run after `mcp.server.discovery-refresh.initial-delay-ms`, both
default 5 min) to periodically re-discover and pick up new or changed backend operations without a restart —
re-registration is idempotent (each tool is removed then re-added). The **alpha** profile enables it at a 30-minute
interval with a 5-minute initial delay (#1632 follow-up) so a domain whose fetch and same-cycle fallback both failed
self-heals without a restart — and does so shortly after the deploys that cause stale routes; other profiles leave
it off.

**Spec-identity guard (#1632).** After a rolling deploy, a stale Eureka registration can route a domain's doc URL to a
_different_ service (on alpha, `/invoice/v3/api-docs` briefly served pos-price's spec — 200 OK and parseable, so no
transport or parse guard fires). Discovery therefore verifies each fetched per-service spec's `info.title` against its
routing token and treats a mismatch as a **failed fetch**: the wrong domain's ops are not registered under the prefix,
and the domain's previously-registered ops are kept, not pruned. Titles that are missing, blank, or springdoc's default
(`OpenAPI definition`) are unverifiable and always pass. Domains whose title doesn't contain their routing token get
extra accepted tokens via `mcp.server.spec-identity-aliases` (shipped defaults: `catalog: [product]`,
`people: [human resources]` — keep these in sync if a service's OpenAPI title changes; keys may be spelled as the
routing token, `vehicle-fitment`, or its normalized form, `vehiclefitment`). The guard is best-effort: it cannot
catch a stale route between token-nested domains (people-contact's title served at `/people`, workorder's at
`/order`, vehicle-inventory's at `/inventory` all pass containment) or a service with no configured title — in those
cases behavior is simply no worse than before the guard existed.

**Unseen-domain guard (#1819).** A registered domain that contributes no operation in a discovery cycle is kept, not
pruned, on every path — on the gateway-aggregate path nothing "fails" when a service is down mid-deploy; it is simply
absent from the aggregate, and on alpha (2026-09-06) an aggregate carrying one service's 70 ops pruned the other 965
rows as stale, after which the next container re-embedded them all before readiness (#1818). Each such cycle logs one
ERROR naming the failed-fetch domains and the unseen domains separately (`registered but contributed no operation
this cycle (#1819, kept not reconciled)`). A domain that is genuinely gone — retired service, renamed gateway prefix —
will repeat that line every cycle until an operator lists it in `mcp.discovery.prunable-when-unseen`
(`MCP_DISCOVERY_PRUNABLE_WHEN_UNSEEN`, comma-separated `mcp_tool.domain` keys), after which one cycle reconciles it.

## Answer Resolution

The model's reply is classified before it reaches the user (`ChatResponseText`): direct `CONTENT`,
`BLANK` (nothing but a reasoning channel), `TOOL_PAYLOAD` (a bare JSON object or array — the
model emitted a tool result instead of composing from it), or `PROTOCOL_MARKUP` (the model's raw
harmony channel protocol, `<|channel|>analysis<|message|>…`, with no `final` channel to recover;
#1834). Non-content replies never reach the user as-is. The streaming endpoints apply the same
classification through `StreamingAnswerGuard` (#1838): prose streams through token by token, a reply
that opens like markup, a payload, a fence or a think block is held and classified when complete, and
anything non-content (or an empty stream) is replaced by the safe fallback — there is no ladder or
re-render on the streaming path. `BLANK` and `PROTOCOL_MARKUP` go to the
answer-resolution ladder (deep link / capability hint). `TOOL_PAYLOAD`
first gets **one re-render turn** (#1708): the payload is fed back as the previous assistant message
with an instruction to answer the question from it as prose and never as JSON, with no tools offered,
so it cannot loop; the instruction says the JSON may be partial or a call's arguments and forbids
inventing figures. If that turn is direct content it is the reply, otherwise the ladder answers as for
`BLANK`. Log lines: `Bare tool payload re-rendered as prose (#1708)` on success (the first turn's
"no direct answer" warning is then suppressed); on failure, `re-render produced no direct answer
either: source=…` followed by the usual first-turn warning. The render turn carries the conversation
history but not the layered system prompt or RAG block, and costs a second full model call.

## Audit & Adaptive Tuning

Every tool decision is logged (selected tool, semantic rank, final score, `selected`, `success`, `fallback_invoked`,
latency) as a tenant-scoped row of `mcp_tool_invocation_log`. A daily cron (`mcp.tuning.cron`, default
`0 0 2 * * ?`) recomputes per-tool performance over the last 7 days for every tool with at least 10 executed calls
and blends it into the current priority:

```
performance_score = (success_rate * 0.6) + ((1 - min(avg_latency_ms / 2000, 1)) * 0.3) - (fallback_rate * 0.2)
new_priority      = current_priority * 0.7 + clamp(performance_score, 0.1, 1.0) * 0.3
```

Tuning runs per tenant with a global rollup (see [Per-tenant tool priorities](#per-tenant-tool-priorities)) and is
off by default: `mcp.tuning.mode` is `off`, `shadow` (proposals logged to `mcp.tuning.shadow` and counted, nothing
written) or `live` (written only behind a fresh, passing eval baseline; `mcp.tuning.eval-result-path`,
`mcp.tuning.eval-freshness-hours`). The legacy `mcp.tuning.enabled=true` still means `live`. Owning classes:
`ToolAuditService`, `ToolPriorityTuningService`, `ToolPriorityRepository`, `TenantToolPriorityResolver`.

## Offline Replay Eval (#1682)

Alpha-only, property-gated (`mcp.eval.turn-trace.enabled`, default on for alpha) capture of one immutable
JSON trace per completed/failed chat turn — assembled system prompt, offered tool definitions, ordered
tool calls with arguments/results, the final response or error, and the build that answered
(`serverBuild`, from `MCP_BUILD_ID`, the image tag on alpha; #1806), and how the reply was
produced (`answerSource`: `CONTENT`, `RE_RENDERED`, `LADDER`, or the raw non-content source —
`BLANK`, `THINKING`, `TOOL_PAYLOAD`, `PROTOCOL_MARKUP`; #1816; simple-chat replies record their raw source) — persisted
with a configurable
retention window and cleaned up on a schedule. Twelve committed fixtures replay the live analytics gate's
`in_chat_path_gate` questions against a real model with canned tool responses, so a candidate fix is
verifiable without an alpha deploy-and-run cycle. See `src/test/resources/eval/README.md` (§"Offline
replay eval") for capture, fixture, replay and grading details, and `OfflineReplayEvalIT` /
`scripts/analytics_gate_run.py --replay-report` to run it.

`mcp_tool_invocation_log` records _that_ a tool ran, never its arguments — those carry customer identifiers. One
exception is logged rather than stored, because its arguments carry none: `DateWindowFacadeTool` emits an INFO line
per resolution (#1684),

```
MCP date window resolved shape=CALENDAR_SPAN unit=MONTH count=12 comparison=NONE startDate=2025-09-01 endDate=2026-08-31
```

so the window a turn actually used can be graded on its own. The shape is the classification the model makes and the
one that has been getting the analytics gate's dated questions wrong; once the window leaves this tool it is a pair of
dates in some other tool's arguments, indistinguishable from a correctly shaped one. A rejected classification logs
nothing — no window was resolved.

## RAG Retrieval Pipeline (Tier 2)

Both session managers use a Tier-2 retrieval chain:

1. Baseline semantic retriever (`maxResults=10`, `minScore=0.6`).
2. Query-expanded retriever (`maxResults=20`, `minScore=0.55`) using deterministic paraphrases.
3. PostgreSQL full-text retrieval for literal identifiers (enabled by default), followed by reciprocal-rank fusion and de-duplication across all retrievers.
4. Final lexical-aware re-ranking to the top-5 contexts before prompt injection.

Retrieval is role-aware (`RoleAwareMetadataFilter`, `ScopedContentRetrieverFactory`) and chat memory is persisted via
`SemanticChatMemoryStore` with session summarization (`SessionSummary`).

### Troubleshooting identifier misses

Treat a missing VIN, SKU, event name, or permission code as a candidate-pipeline problem before
changing the answer prompt:

1. Run the same query against dense retrieval at both score floors and against PostgreSQL FTS;
   record document IDs at every stage (source retrieval, RRF, de-duplication, and final top-5).
2. Confirm the expected chunk has the selected tool's RAG scope and that its
   `required-permissions` are satisfied. A correct lexical match must never bypass scope or
   permission filtering.
3. Inspect the stored `search_vector` and query produced by `websearch_to_tsquery`. Punctuation in
   VINs, SKUs, and colon/dot-delimited codes can change lexemes; use a normalized exact-token
   fallback when PostgreSQL tokenization removes the distinguishing characters.
4. Compare the covering chunk's rank before and after `RerankedContentRetriever`. If it enters the
   candidate pool but misses the top-5, tune or diversify the final selection rather than lowering
   the dense threshold.
5. Check ingestion and chunking: verify the current source revision was embedded, the literal token
   was not split across chunks, and dense/lexical copies share a stable document ID for de-duplication.

Keep exact-identifier fixtures in `src/test/resources/eval/rag-lexical` and compare dense-only with
hybrid recall, hit@5, MRR, forbidden-document violations, and latency before changing fusion weights
or similarity floors.

## Data Model

Key tables (Flyway migrations under `src/main/resources/db/migration`):

- `system_prompt`, `llm_api_config` — prompt and model-config CRUD.
- `nlti_session`, `nlti_request`, `nlti_intent`, `nlti_audit_event` — NLTI session/request/intent tracking + audit.
- `mcp_tool`, `mcp_tool_permission`, `mcp_workflow_state` — tool registry, permission gating (`V17`/`V18`), workflow
  gating. `mcp_tool.source` distinguishes facade vs discovered operations.
- `mcp_role`, `mcp_tool_role` — legacy role gating, retained pending cleanup (see [Backlog](#backlog--missing-features)).
- `mcp_tool_invocation_log` — per-decision audit feeding adaptive tuning (tenant-scoped).
- `mcp_tool_priority` — per-tenant tool-priority overlay tuned from that tenant's invocation log (tenant-scoped);
  `mcp_tool.priority` stays the global row.
- `mcp_rag_*` — RAG ingestion jobs, preload tracking, and immutable preload audit records.

## Multitenancy (ADR-0062, WS3 wave 12)

This module runs on the ADR-0062 runtime: it depends on `pos-tenancy-common`; the conversation entities
(`NltiSession`, `NltiRequest`, `NltiIntent`, `NltiWritePlan`, `NltiAuditEvent`) extend `TenantScopedEntity`, and the
platform catalogs (tool catalog, screen registry, chat rules, prompts, LLM configuration, RAG corpus bookkeeping)
carry `@TenantGlobal` and are listed in `src/main/resources/db/tenancy-global-tables.txt`. The request tenant is
bound by `TenantContextFilter` from `X-Tenant-Id` (the gateway injects it from the token's `tid`), the Kafka tenant by
`TenantRecordInterceptor` on the role-events consumer, and every connection checkout binds `app.current_tenant` for
row-level security. `pos.tenancy.default-tenant-id` still binds the alpha default tenant on every unbound path.

The application pool connects as the non-owner `pos_app` role (Compose: `POS_MCP_DB_USER` / `POS_APP_PASSWORD`);
Flyway alone uses the owner credential (`SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`, read by Boot's Flyway
auto-configuration).

The three JDBC-written scoped tables, `mcp_tool_invocation_log`, `mcp_tool_priority` and `mcp_eval_turn_trace`, take
their `tenant_id` from the Postgres default of the bound connection; their repositories carry `@TenantAudited` and
name no tenant. The startup runners seed and embed platform tables only. There is no H2 equivalent: on H2 — the `dev` profile,
and `test` when it is active on its own — Flyway is off and Hibernate builds the entity-mapped tables, so these
three JDBC-written tables, like the tool catalog they feed, exist only on Postgres. Flyway still runs the real
`db/migration` chain wherever Postgres does: the `alpha` and `prod` profiles, and the `pg` profile that
`PostgresTenancyTestBase` activates alongside `test`, under which `TenancySchemaConformanceIT` and
`TenantIsolationIT` validate the entities against that baseline on a Testcontainers Postgres.

### Schedulers

| Job | Classification | What it touches |
| --- | --- | --- |
| `ToolPriorityTuningService.tuneToolPriorities` (`mcp.tuning.cron`) | Per tenant (`TenantIterator`), then the `@PlatformScoped` global rollup `recomputeGlobalPriorities` | Reads each tenant's `mcp_tool_invocation_log` under its binding and writes that tenant's `mcp_tool_priority` overlay, in a `TransactionTemplate` opened inside the binding; the rollup writes `mcp_tool.priority` (global) from the per-tenant aggregates summed in memory, in one transaction of its own |
| `AlphaEvalTraceRetentionScheduler.deleteExpiredTraces` (`alpha`, `mcp.eval.turn-trace.enabled`) | Per tenant (`TenantIterator`) | Deletes each tenant's expired `mcp_eval_turn_trace` rows |
| `DiscoveryRefreshScheduler.refresh` | `@PlatformScoped` | Refreshes the platform tool catalog (`mcp_tool` and embeddings); overlay rows of a pruned tool go with it (`ON DELETE CASCADE`) |
| `RolePersonaSyncRunner.scheduledRefresh` | `@PlatformScoped` | Refreshes the platform role personas (`system_prompt`) |
| `SiteMapEmbeddingWarmupRunner.configureTasks` (programmatic, `SchedulingConfigurer`, cadence `mcp.sitemap.cache-ttl`) | `@PlatformScoped` on the registering method | Re-embeds the platform site map (`mcp_screen_registry` and the section embedding cache) |

There is no session cleanup job: an NLTI session expires on resume (`pos.nlti.session.ttl-hours`, checked under the
caller's tenant binding) and its row is retained. The module `ArchitectureTest` and `pos-archunit` both fail on a
`@Scheduled` method that is neither per tenant nor `@PlatformScoped`; a job registered programmatically through
`SchedulingConfigurer.configureTasks` carries no `@Scheduled`, so the module rule classifies the registering method
instead (`programmatically_scheduled_jobs_should_be_classified_for_tenancy`).

### Per-tenant tool priorities

Tool priorities have a tenant dimension with a global rollup (plan WS6, decided 2026-09-10). `mcp_tool.priority` is
the global row: the catalog is a global, code-first table, so keeping the global value there leaves every catalog
read and the seed unchanged. `mcp_tool_priority (tenant_id, tool_id, priority, avg_latency_ms)` is the tenant-scoped
overlay under row-level security, so a request bound to tenant T can only ever see T's overlay rows and an unbound
connection sees none (a global row owned by the platform tenant in the same table would need one connection to read
two tenants' rows, which RLS forbids for `pos_app`).

- **Resolution** (`TenantToolPriorityResolver`, applied by `ToolRegistryService` to every candidate list of a
  request): the overlay is read once per resolution through the bound connection and applied tool by tool — an
  overlay row replaces the tool's priority and latency, a tool without one keeps the global row, and a tenant with
  no overlay at all (no invocation history, or tuning never run live) ranks on the global set unchanged.
- **Tuning** (`ToolPriorityTuningService`): the nightly run sweeps the active tenants through `TenantIterator`;
  bound to each, it reads that tenant's own log (RLS shows it nothing else) and tunes that tenant's overlay for every
  tool with at least 10 executed calls in the window, starting a new overlay from the global priority and drifting
  an existing one from its own previous value. The per-tenant aggregates are summed in memory and, after the sweep,
  the `@PlatformScoped` rollup recomputes the global row from all tenants' logs (the sum is the only way a non-owner
  connection sees every tenant's history). The threshold applies per scope: two tenants with 6 calls each tune the
  global row but keep no overlay. Shadow proposals on `mcp.tuning.shadow` carry `scope` (`tenant`/`global`) and
  `tenant_id`; the `mcp.tuning.proposals` counter is tagged `mode` and `scope`; the run logs one line per tenant
  (invocations, tools with history, proposals) and a summary. Tool invocation metrics are not tagged by tenant:
  `mcp.tool.execution.latency` is not tagged by tool either, so a tenant tag would have been a new dimension rather
  than a matching one.
- **Which tenants the sweep visits.** `TenantIterator` reads the module's `TenantRegistry`, and the default
  `pos.tenancy.registry.mode=STATIC` knows `pos.tenancy.tenants` or just the alpha default tenant. On that registry
  the "global" rollup is one tenant's rollup, so the service logs a WARN at startup when tuning is `shadow` or
  `live` on a static registry of one tenant. A multi-tenant deployment must set `pos.tenancy.registry.mode=REMOTE`
  (with `pos.tenancy.registry.url` and `.secret`; `pos-tenancy-common/README.md`, "Tenant registry") before enabling
  tuning. The module does not default to `REMOTE`: that needs the pos-tenant secret in every environment.
- **Atomicity and the catalog snapshot.** `JdbcTemplate` commits every statement on its own, so the run
  opens its own transactions. Each tenant's log read and overlay upserts are one `TransactionTemplate`
  unit executed *inside* the `TenantIterator` binding (a transaction opened around the sweep would
  check its connection out before a tenant is bound and run unbound -- `docs/TENANCY_SCHEMA.md`,
  "Per-tenant schedulers and transactions"); a failure on the third of five proposals rolls the first
  two back, and that tenant still counts as unfinished. The live rollup's `mcp_tool` updates are one
  transaction too, because the rollup is a single fleet-wide result: a failure part way through leaves
  the previous global set intact rather than a mixture of recomputed and stale priorities. Proposal
  counters are incremented only after the matching transaction commits. The global priorities are read
  once per run and that snapshot serves both the per-tenant seeding and the rollup arithmetic --
  `mcp_tool` is global, so the per-tool alternative cost O(tenants x tools) serial lookups. The
  snapshot cannot mask a value the same run wrote: the sweep only reads, and the rollup computes every
  proposal before opening its write transaction, so no read of the global priority ever follows a write
  of it. A run that cannot read the catalog at all tunes nothing and counts `mcp.tuning.incomplete`.
- **When the rollup is skipped.** The global row is a statement about the whole fleet, so it is written only from a
  sweep that covered it. Two things stop it, each counting the run in `mcp.tuning.incomplete` and logging a WARN
  while leaving the per-tenant overlays that did succeed in place:
  - a tenant that failed — an unreadable log, a rejected overlay write, anything `TenantIterator` caught and carried
    on past. That tenant's aggregates never reach the rollup either: they are merged only once its own tuning
    succeeded.
  - a registry that cannot vouch for its list (`TenantIterator.Sweep.completeTenantList()`, read by
    `TenantIterator.sweep()` in the same breath as the tenant list it hands the sweep — not by a separate,
    later call once every tenant is done, which would let a concurrent refresh flip the verdict in the
    meantime and hide that the sweep it graded ran over a stale list). A `REMOTE` registry answers from its
    static seed until pos-tenant replies once, and from its last good snapshot while a refresh is failing, so
    during an outage the sweep would otherwise visit one tenant and rewrite `mcp_tool.priority` as though it
    had visited all of them. The startup WARN above cannot see this — it is a run-time state — which is why
    the check is per run. A `STATIC` registry is authoritative by construction and never trips it.

### Session scoping

An NLTI session never serves another tenant (plan R-B6). `NltiSession` extends `TenantScopedEntity`, and every lookup
by id goes through `NltiSessionAccess`, which requires a bound tenant (`TenantContext.require()`, so an unbound
path fails loudly rather than reading nothing), relies on Hibernate's `@TenantId` filter and RLS to confine the
query, and treats a row of another tenant as absent even if one were returned. A session id the bound tenant does not
have is indistinguishable from one that never existed: `POST /v1/nlt/sessions/{sessionId}/workflow-state` and a
write-plan confirm/cancel answer 404 `SESSION_NOT_FOUND` (403 `SESSION_ACCESS_DENIED` is reserved for this tenant's
session owned by another subject), and `POST /v1/nlt/requests` starts a fresh session for the caller exactly as it
does for an unknown id. `SessionAgentManager` / `StreamingSessionAgentManager` key their two per-user caches beneath
the tenant, because a username is unique within a tenant only: the rate counter by the actor key
`tenant::username`, and chat memory by `tenant::username::role` (the blocking manager adds `::conversationId` when
the caller supplies one). `evict(username)` removes both for that actor in the bound tenant and nothing of the same
username in another tenant. The streaming manager captures the request's tenant before assembling the Flux and
re-binds it in every callback that writes tenant-scoped data (the audit row, the turn trace, the answer-source
record), since the stream is subscribed and completed on Reactor threads that never carried the binding. The tool
executions themselves are covered the same way and independently: Spring AI's tool loop runs them on
`boundedElastic`, so `RequestBoundToolCallback` captures the caller, their bearer token and their tenant when the
per-request callback list is assembled and re-binds all three around each execution — that is what puts a streamed
tool call's `mcp_tool_invocation_log` row under the caller's tenant instead of losing it to RLS (`ToolAuditService`
logs such a failure at WARN and returns, so it would not surface). `TenantContextPropagation` also registers the
tenant with Micrometer's `ContextRegistry`, so wherever Reactor's automatic context propagation is on it travels
with a context snapshot as well — but that JVM-wide hook is switched on only by `EvalTurnTracePropagation` under the
`alpha` profile, so nothing depends on it and the explicit re-binding above is what holds on every deployment.

Proof: `TenantIsolationIT` (tenant A's `nlti_session` row and `mcp_tool_priority` overlay are invisible to tenant B
and to an unbound connection, through the repository and through raw SQL) and `TenancySchemaConformanceIT` (every
non-whitelisted table has `tenant_id`, RLS enabled and forced, and the `tenant_isolation` policy; the pool is
`pos_app` with no bypass), both on Testcontainers Postgres (`./mvnw -pl pos-mcp-server -am verify`); the
service-level contract is unit-tested in `NltiSessionAccessTest`, `TenantToolPriorityResolverTest`,
`ToolPriorityTuningServiceTest` and `RequestBoundToolCallbackTest` (which executes callbacks on a plain executor,
with no Reactor propagation, so it proves the non-alpha case).

## Backlog / Missing Features

Tracked separately as GitHub issues. Open items not yet implemented in code:

- **Legacy role-gating cleanup** — drop the `mcp_role` / `mcp_tool_role` tables. `ToolRegistryRoleMapper` is gone and
  the repositories no longer query either table, but the schema still carries them.
- **`AUTHENTICATED` sentinel everywhere** — the customizer now lives in `pos-security-common`
  (`RequiredPermissionsOpenApiAutoConfiguration`); confirm every service's spec emits the sentinel so unguarded
  operations gate correctly.
- **Admin UI for `mcp_tool_permission`** — the backend endpoints exist (`ToolPermissionController`, #785); no frontend
  screen maintains them yet.

Delivered since this list was first written, and removed from it: the retrieval-quality regression harness (hit@5 /
MRR, `BaselineCaptureIT` and `src/test/resources/eval/`) and hybrid dense + lexical retrieval
(`mcp.rag.hybrid.lexical-enabled`, default `true`).

