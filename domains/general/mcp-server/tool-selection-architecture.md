---
type: Architecture
title: Tool Selection & Runtime Configuration Architecture
domain: general
status: current
tags: [mcp-server, general]
---

# Tool Selection & Runtime Configuration Architecture

Status: current as of #637 / #639 delivery. This document describes the behavior that is actually
implemented, including what is intentionally deferred. It supersedes the aspirational parts of the
earlier NLQ-to-API analyses referenced by issue #639.

## Scope

Covers the runtime path shared by blocking (`POST /v1/mcp/chat`) and streaming
(`POST /v1/mcp/chat/stream`) chat:

0. question tagging (ADR-0068), once per turn, ahead of everything below
1. role resolution
2. system-prompt assembly (with fallback) — #637
3. permission/workflow-gated tool selection — #639
4. role-agent caching (TTL + invalidation) — #639
5. static RAG preload at startup — #637

## 1. Shared role resolution

Both chat controllers resolve the caller through the same components — there is no
controller-local role-priority logic:

- `McpRoleResolver` (`McpRoleResolverImpl`) picks the primary role from the caller's
  `ROLE_*` authorities using `SystemPromptDefaults.MCP_ROLE_PRIORITY`, falling back to
  `ROLE_USER` when no prioritized role is present.
- `CurrentUserContextResolver` wraps that into a `CurrentUserContext` (username, userId,
  primary role, all roles, authorities, permission codes) consumed by both orchestration
  services.

Startup agent prebuild covers `SystemPromptDefaults.PRELOADABLE_ROLE_IDENTIFIERS` —
`MCP_ROLE_PRIORITY` plus `ROLE_USER` — so `ROLE_TECHNICIAN` and `ROLE_USER` are never omitted.
Each prebuilt role's permission set is fetched from `pos-security-service`
(`RoleDefaultPermissionsClient`, fail-soft) so the warm cache matches the role's real gated tool
set; callers whose actual permission codes differ get a cache miss and an on-demand build.

## 2. Prompt assembly and fallback (#637)

`RolePromptResolverImpl.assemble(role, ragScope)` layers, in order:

| Layer | Source (`system_prompt.name`) | Missing behavior |
|---|---|---|
| BASE | `master` | built-in default text (`SystemPromptDefaults.DEFAULT_PROMPT_TEXT`) |
| ROLE | the role name, e.g. `ROLE_CASHIER` | layer skipped, warning + metric |
| DOMAIN | domain prompt keyed by RAG scope | layer skipped (silent; master scope has no DOMAIN layer) |
| TOOL_USE | built-in constant | always present |

`resolvePrompt(name)` precedence: requested prompt → `master` → built-in text.

**Scope card (ADR-0069).** When `card` is in `mcp.scope-graph.enforce` and the turn's scope confidence is `HIGH`, the prompt
supplier appends one more layer after TOOL_USE, named `SCOPE_CARD`: a plain-text block of the recognised entities, their relations
and lifecycle states, the actions (tools) with the permission each requires, and screen deep links, cut to
`mcp.scope-graph.card-token-budget` by dropping whole lines from the end. `ScopeCardRenderer` renders it from the caller-filtered
`ScopeSet` only: an entity, relation or state appears only when a tool, document or screen the caller passed the filter for connects
to it, and the card never contains user text, lexicon terms, identifier patterns, instance data or the permission code of a node the
caller did not pass for. It orients the model and grants nothing; tool calls are still authorised per call. With the card off (the
default) the prompt is unchanged. The layer is reported with the other prompt layers in telemetry.

Prompt records are managed only through the existing secured `SystemPromptController` CRUD API;
`SystemPromptSeedRunner` seeds role/domain prompts best-effort at startup.

Observability: every fallback past the requested prompt increments
`mcp.prompt.fallback{reason, requested}` with reasons `master-prompt`, `built-in`, or
`missing-role-layer`, alongside the existing warn logs.

## 3. Gated tool selection (#639)

`ToolSelectionEngine.selectRoleTools(role, permissionCodes, message[, workflowState], tags)` is the
single selection contract used by **both** blocking and streaming orchestration — transport parity
is structural, not duplicated logic.

**Per-turn order (ADR-0068).** Each manager calls `ToolSelectionEngine.tag(message)` once and passes the resulting
`QuestionTags` record to every consumer: tag → simple chat (T0) → tier (`NltiRouter`) → selection (+ scope) → retrieval → prompt.
The T0 check, the tier and the selection read the record; none re-derives a decision from the raw message. `HeuristicQuestionTagger`
always runs and holds the former keyword rules unchanged; with `mcp.tagging.mode` `shadow` or `enforce` a decision-model tagger
(`JevQuestionTagger` over `JevClient`, the cell's Ollama `POST /v1/systemone`, 800 ms, fail-to-heuristic) answers too. A consumer acts
on the model's value only for a tag listed in `mcp.tagging.enforced-tags` whose confidence meets `thresholds.<tag>`; in `off`, in
`shadow` and for every other tag it acts on the heuristic value. Warm-up passes `QuestionTags.none()` (equal to `off`). When the
heuristic hits an exact simple-chat catalog rule (T0) the provider call is skipped for the turn (`heuristic_certain`).
Tags are advisory: they act only on the permission-gated set and never widen access.

Order of operations inside `ToolRegistryService.resolveCandidateTools`:

1. **Gate first**: `findEnabledByPermissionsAndWorkflow(permissionCodes, workflowState)` — only
   tools the caller is authorized for in the active workflow state enter consideration.
2. **Admin fast path**: if the query matches admin keywords/phrases, is *not* vetoed by
   business-domain vocabulary, *and* `AdminFacadeTool` survived the gate, it is returned alone.
   This is the explicit confidence-based deterministic recovery path; it never bypasses
   permission gating. Since ADR-0068 the acting `admin_account_question` tag must also be `true`
   (`resolveCandidateSelection(context, topK, tags)`): a model `false` at or above threshold, when
   enforced, vetoes the path, and a tag never fires it without a keyword or phrase match. In `off` and
   `shadow` the tag's heuristic value is the keyword/veto rule above, so behaviour is unchanged.

   Because the fast path returns the admin tool **alone**, a false positive suppresses every
   other candidate for the whole request. Two guards keep that from firing on business
   questions: `account`/`accounts` are not bare keywords (they collide with "accounts
   receivable", "chart of accounts", "GL account" — the admin senses live in
   `ADMIN_QUERY_PHRASES` as "user account", "account state", …), and `FAST_PATH_VETO_TERMS`
   vetoes the path outright when finance/workorder vocabulary is present, so a mixed query
   ("who has access to the receivables ledger") still reaches semantic ranking.
3. **Gated semantic ranking**: ANN search is restricted to the gated set
   (`findTopKByEmbeddingForPermissions`), so unauthorized tools can never crowd authorized ones
   out of the candidate window. Candidates are scored (`semantic rank + priority − latency −
   cost`) and cut to `mcp.agent.candidate-tool-limit`.
4. **No-embedding fallback**: highest-priority gated tools by `priority`.
5. **Failure/empty fallback**: full role tool set (fail-open within the role's own domain tools,
   never beyond the caller's role scope).

Fallback tools (web search / inventory / order / date-window facades) are merged in separately per
message, so short operational messages like `stock part 1234` still reach the inventory facade
even when semantic routing is weak. Since ADR-0068 the fallback is read from the tag record
(`needs_web_search`, `about_inventory`, `about_orders`, `implies_date_window`); in `off` and `shadow`
these are the heuristic keyword guards, and the glossary tool is always offered. Every tag-added
facade is **intersected with the caller's permission-gated set**, in every mode: a facade the caller
cannot reach is not added (before ADR-0068 these additions bypassed `mcp_tool_permission` at selection
and relied on downstream `@PreAuthorize`). The glossary and Exa web-search tools have no catalog row or
permission and are offered as before. Tag-added tools and ADR-0069 scope-added tools are unioned on top
of the ranked cut; only scope-added tools count against `added-tool-slots`.

**Additive scope slots (ADR-0069).** When `tools` is in `mcp.scope-graph.enforce` and the scope confidence is `HIGH` or `LOW`,
tools from the turn's `ScopeSet` are added after the ranked cut and the keyword fallback, at most
`mcp.scope-graph.added-tool-slots` per turn, facades first, then discovered operations with what is left (ordered by hop, `reads`
before `writes`, then name). Facades are added in `ToolSelectionEngine` from the caller's already-gated set; discovered operations
are added in `OpenApiToolProvider` after the ANN cut through `findDiscoveredByNamesForPermissions`, which applies the same
permission and workflow SQL, and write-capability is recomputed over the union. No ranked tool is removed or displaced, and
nothing is added on the failure/empty fallback (fail-closed), the admin fast path or warm-up. With the `tools` switch off the
scope is resolved and recorded only. When the `lookups` switch is enforced and the turn has an entity seed, the lexicon's
`facade_tools` for the seed entities replace the inventory and order tags' facades. Enforced `entity_<key>` and `domain` tags seed
the scope with match kind `TAG` at `LOW` confidence.

### Workflow state

Authoritative state is the persisted `NltiSession` value (`NltiWorkflowStateService`); session-less
callers take the `workflow_state` tag. Implemented states: `IDLE`, `CREATING_PO`,
`RECEIVING_ASN`, `INVENTORY_RECON`, `PROCESSING_RETURN`. Heuristic derivation covers only the PO /
ASN / inventory-recon phrasings; anything else resolves to `IDLE`. For a session-less caller the
precedence chain (ADR-0068 §3.3) is: persisted `NltiSession` state → the model's `workflow_state` at or
above threshold, when enforced (a non-`IDLE` answer must also meet `thresholds.workflow_state.non-idle`;
`IDLE` is a value and overrides a phrase match; `PROCESSING_RETURN` has no heuristic source) → the
scope-graph lexicon lookup, when `lookups` is enforced and the acting `intent` is `ACTION` → the phrase
match. The first that yields a value wins; a persisted state is never overridden. In `off` and `shadow`
only the last two apply. Richer workflow-state
transitions (e.g. automatic state advancement from tool executions) are **intentionally deferred**
behind `NltiWorkflowStateService` — the selection contract already accepts the state as input, so
implementing them requires no orchestration changes.

## 4. Role-agent caching (#639)

Both agent managers (`SessionAgentManager`, `StreamingSessionAgentManager`, profile `alpha`):

- cache role agents keyed by `role::toolCacheKey` in Caffeine with
  `expireAfterWrite(mcp.agent.cache-ttl-minutes)` (default 30) and
  `maximumSize(mcp.agent.max-cached-agents)`;
- prebuild agents for the preloadable role set at startup (fail-soft per role);
- listen for `AgentCacheInvalidationEvent` and drop **all** cached role agents when runtime
  configuration changes. The event is published by:
  - `SystemPromptServiceImpl` on prompt create / update / delete, and
  - `ToolPermissionAdminService` on `mcp_tool_permission` grant / revoke.

  Listeners run `AFTER_COMMIT` (with fallback execution for non-transactional publishers), so a
  rebuild always re-reads committed configuration. Between a change and the next request there is
  no stale-serving window bounded by TTL anymore; TTL remains as the backstop for out-of-band
  changes (e.g. direct DB edits).

## 5. Static RAG preload (#637)

`RagPreloadRunner` (ApplicationRunner, non-test profiles) → `StaticRagPreloadService`:

- Documents are registered in `mcp.rag.preload.docs` (`StaticRagPreloadProperties`) with a
  **deterministic document id** (e.g. `accounting.de-bookkeeping`, `inventory.inv-cntrl`), a
  classpath source, a RAG scope, and optional required permissions.
- Content hash (SHA-256) is tracked per document id in `mcp_rag_preload_record`; unchanged
  content is skipped, changed content is re-ingested and **supersedes** prior embeddings via
  replace-on-document-id.
- Startup is best-effort: per-document failures are logged and recorded, never block boot.
- Metrics: `mcp.rag.preload.loaded` / `.skipped` / `.failed` (tagged by `documentId`) plus a
  preload duration timer.

Each entry also carries `entities:` (ADR-0069), the `scope-graph/entities.yaml` keys the document substantively explains, or
`[none]` for a platform-wide document. The `alpha` profile's list replaces the base list wholesale, so both lists carry every entry
with identical values; `RagPreloadProfileParityTest` asserts that, and `RagDocumentHeaderAgreementTest` asserts that a document's
header agrees with its entry on id, scope and permissions.

Adding a new static document = adding one entry to `mcp.rag.preload.docs` and the markdown file
under `src/main/resources/rag/`; no code changes.

## Observability summary

| Signal | Meaning |
|---|---|
| `mcp.prompt.fallback{reason,requested}` | prompt resolution fell back (#639) |
| `mcp.rag.preload.loaded/skipped/failed{documentId}` | startup preload outcomes (#637) |
| `nlti.request.telemetry` events | per-request role, selected tools, prompt layers, workflow state, latency |
| `mcp.scope_graph.build.duration`, `.build.failures`, `.nodes`, `.edges`, `.unmapped_tools` | scope graph build time, failed builds, snapshot size, discovered tools with no lexicon match (ADR-0069) |
| `mcp.scope.resolved{confidence}`, `mcp.scope.size{kind}`, `mcp.scope.errors` | per-turn scope confidence, size and resolver failures (mode not `off`) |
| `mcp.scope.called_tool{in_scope}`, `mcp.scope.retrieved_doc{in_scope}` | called tools and retrieved documents inside the scope, counted at eval-trace completion |
| `mcp.scope.fallback{consumer=rag\|tools\|card}` | an enforced consumer did not act on a turn (mode `enforce` only) |
| `nlti.request.telemetry` `scope*` fields | `scope*` fields (introduced in `schemaVersion` 2): `scopeMode`, `scopeGraphHash`, `scopeConfidence`, `scopeEntityCount`, `scopeToolCount`, `scopeDocCount`, `scopeAddedToolCount`, `scopeRagFilterApplied` |
| `mcp.tagging.latency{model}`, `mcp.tagging.requests{model,outcome}`, `mcp.tagging.fallback{reason}` | tagging provider call time, outcomes (`ok`, `timeout`, `error`, `rate_limited`, `malformed`) and per-turn heuristic fallbacks (mode not `off`, ADR-0068) |
| `mcp.tagging.agreement{tag,agree}`, `mcp.tagging.low_confidence{tag}`, `mcp.tagging.skipped{reason}`, `mcp.tagging.state_truncated` | model vs heuristic agreement per tag, enforced tags below threshold, calls skipped on an exact simple-chat rule, messages cut at `max-state-chars` |
| `nlti.request.telemetry` `tagging` block | `schemaVersion` 3: `mode`, `providerModel`, `latencyMs`, `fallbackReason`, `agreementRate`, `questionCount`, `requestBodyBytes` and the acting `intent`, `risk`, `complexity`, `domain`, `workflowState`, `simpleChat`; the eval turn trace carries a matching `tags` component |
| "Invalidated MCP … role-agent cache" logs | configuration-triggered cache flush |
| Debug logs in `ToolRegistryService` / `ToolSelectionEngine` | per-request gating, scoring, fallback decisions |
