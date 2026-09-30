---
type: ADR
title: 'ADR-0068: Pre-LLM Question Tagging with a Decision Model (TypeSafe Jev) in pos-mcp-server'
description: Puts one typed QuestionTagger seam in front of the pos-mcp-server executor LLM, backed by a Jev-protocol decision model served by the platform's own Ollama container, with today's heuristics as the permanent fallback; replaces six scattered keyword heuristics and the dormant Gate 4 router.
status: draft
adr_status: pending
created: '2026-09-30'
related: [ADR-0009, ADR-0026, ADR-0046, ADR-0062, ADR-0069]
tags: [adr]
---
# ADR-0068: Pre-LLM Question Tagging with a Decision Model (TypeSafe Jev) in pos-mcp-server

**Status:** PROPOSED **Date:** 2026-09-30 **Deciders:** Architecture, NLTI (Natural Language Task Interpretation) Domain, Security & Authorization Domain
**Affected Issues:** — (none yet; to be opened for implementation)

> **How to read this.** ✅ **Resolved** marks a decision this ADR proposes (TEMPLATE.adr.md sub-decision format); nothing is
> accepted until the Sign-Off rows are filled.

---

## Context

### Current state

Before a chat turn reaches the executor LLM (`deepseek-v4-flash:0731` on Ollama), `pos-mcp-server` makes several decisions
about the question. Each is made by its own hand-written heuristic, in its own class, with no confidence and no shared result:

| Decision | Where | How it is made today |
| -------- | ----- | -------------------- |
| Is this a T0 simple-chat message, or does it lean on a previous turn? | `SimpleChatClassifier` (`CONTINUATION_CUES`, token/length caps) | en/fr/es word list (#1836) |
| Workflow state for session-less callers | `ToolSelectionEngine.deriveWorkflowState` | Substring match over PO / ASN / recon phrases; everything else `IDLE` |
| Extra facade tools on top of the semantic top-K | `ToolSelectionEngine.fallbackToolsForMessage` | Keyword guards (web search, inventory, order), the date-window tool via `mentionsDateWindow`, and an always-on glossary tool (#1688, no keyword guard) |
| Does the question imply a date window? | `ToolSelectionEngine.mentionsDateWindow` | Phrase list + precompiled regexes (#1684) |
| Admin fast path (returns `AdminFacadeTool` **alone**) | `ToolRegistryService.resolveCandidateTools` | `ADMIN_QUERY_KEYWORDS` + `ADMIN_QUERY_PHRASES`, vetoed by `FAST_PATH_VETO_TERMS` |
| Is this a compound (multi-need) question? | RAG rerank (`CompoundRerankProperties`, #1180) | Sub-query split heuristic |
| Intent / risk / complexity / domain → model tier | `NltiRouter` (Gate 4, #1192) | `qwen3:4b` asked for strict JSON; **dormant** since #1683 (`MCP_MODEL_TIERING_ENABLED=false`) |
| Intent / risk for the `/v1/nlt/requests` write gate | `IntentParserServiceImpl` | keyword classifier; out of scope here (see Scope) |

### The problem

- **Brittle and English-shaped.** Every guard is a vocabulary list. Each gate run adds words after a miss (#1684, #1840
  `IMPLIED_WINDOW_WORD_PATTERNS`, #1836); a phrasing outside the list silently falls through. fr/es vocabulary exists only
  in the T0 catalog and `CONTINUATION_CUES`; the other five heuristics (`deriveWorkflowState`, `fallbackToolsForMessage`,
  `mentionsDateWindow`, the admin lists, the #1180 split) are English-only.
- **No confidence.** A heuristic either fires or does not. Nothing distinguishes a sure match from a coincidental one, so
  the code cannot escalate an uncertain case differently from a certain one. The admin fast path is the sharpest instance:
  a false positive suppresses every other tool for the whole request, which is why it carries a hand-built veto list.
- **The LLM router did not pay.** The Gate 4 T1 router added a generative classification call per turn, parsed free text
  back into JSON (any malformed reply falls to the safe default), and was switched off because its cost bought nothing.
- **No shared typed result.** `nlti.request.telemetry` already carries a `Routing` block (intentType, riskLevel, domain,
  complexity, tier, simpleChatRule, workflowState; `NltiRequestTelemetry.java:93-100`) and `EvalTurnTrace` carries
  simpleChat, intent, modelTier and workflowState, but they are filled piecemeal and the router fields only by the dormant
  router. Each decision re-reads the raw message, and no single typed per-turn record is shared by the consumers.

### Drivers

- A class of model now exists for exactly this job. TypeSafe **Jev** (early access since 2026-09-15) is a non-generative
  "System One" decision model: it takes text plus typed questions and returns typed answers with calibrated probabilities,
  never prose. Three primitives: **Noul** (yes/no probability), **Choice** (one of up to 255 labelled options, with a
  per-option distribution and confidence), **Score** (a 2–10 level ordinal rubric). All questions in one request are answered
  in one parallel pass, reported median ~275–310 ms, at $0.042 per million input tokens with output free (vendor figures as
  published on 2026-09-30; unverified by this repo).
- **The protocol now runs locally.** Ollama 0.35 (2026-09-29) serves Jev-protocol decision models at `POST /v1/systemone`,
  the same wire contract as hosted Jev (`model`, `state`, `questions` in; `answers`, `usage` out), with no API key. Three
  models are published: `nimble` (9B, Bespoke Labs, 9.5 GB Q8_0), `tev1` (4B, Together AI, 4.5 GB, marked experimental) and
  `tev1:0.8b` (812 MB). Their authors report 75.7 %, 73.3 % and 63.5 % accuracy across Bespoke Labs' 13 public datasets. The
  published latencies (for example ~91 ms per decision for `nimble` on an Apple M5 Max; ~496 ms `nimble` versus ~61 ms
  `tev1:0.8b` warm in one community benchmark) come from GPU or Apple-silicon hosts. Differences from the hosted API: at most
  64 questions per request, every question must carry instructions, errors come back as `{"error": …}`, and there is no request
  id (vendor and community figures as published on 2026-09-30; unverified by this repo).
- There is nothing to parse and no generated text to hallucinate, which removes the router's failure mode.
- The platform is pre-production (CLAUDE.md): the heuristics can be moved behind a seam cleanly rather than shimmed.

### Constraints

- **TypeSafe's own Jev is closed-weight and hosted only.** TypeSafe states it does not train on customer requests; zero data
  retention (ZDR) is offered to enterprise customers only, and operational telemetry (logs, hashes, classifications, metrics)
  may be processed (vendor claims as published on 2026-09-30, unverified by this repo, like the vendor figures under Drivers).
  Using it would send every tagged message to TypeSafe. The Ollama-served models (Drivers) remove that: they run in the
  platform's own container.
- **Message text already leaves the platform.** On alpha it goes to hosted Ollama for the executor LLM (`application-alpha.yml:19`;
  backend `docker-compose.yml:1073-1077`) and, where configured, to Exa web search (`application.yml:410-413`) and, for voice
  input, to OpenAI-compatible speech-to-text (`application.yml:340-346`). Local tagging adds no recipient. The chat base URL
  (`OLLAMA_CHAT_BASE_URL`, `https://ollama.com` on alpha) is therefore **not** the tagging endpoint; the in-cell `ollama`
  container (`docker-compose.yml:475-495`, today serving only the `bge-m3` embeddings via `OLLAMA_EMBEDDING_BASE_URL`) is.
- **The alpha host has no GPU.** The cell runs 22 containers on one `t3.2xlarge` (8 burstable vCPUs, 32 GiB;
  `ALPHA_AWS_PROVISIONING_RUNBOOK.md:19`). The same `ollama` container embeds at ~1.2 s per tool description on that CPU
  (`application.yml:59-61`). CPU latency for the decision models is unmeasured, and the published figures do not transfer.
- User messages carry tenant business data (customer names, invoice totals, vehicle details). ADR-0062 §11 makes
  conversations and cached LLM context tenant-scoped rows; nothing yet governs sending message text to a new third party.
- Permission gating is the security boundary for tool access (gateway `X-Authorities` → `@PreAuthorize`, and
  `findEnabledByPermissionsAndWorkflow` before any ranking). No classifier may widen or bypass it.
- The chat surface is multilingual: en, fr and es in backend rule data (`SimpleChatRuleDefaults`, `CONTINUATION_CUES`); the
  frontend locales are en, fr-CA and es. No decision model's non-English accuracy is published.
- The hosted API documents rate-limit responses (429, 529 Overloaded) as expected, not exceptional. A local container has no
  rate limit, but it queues under load and shares its CPU with the embedding model.

### Scope

`pos-mcp-server` only: the blocking and streaming chat paths (`SessionAgentManager`, `StreamingSessionAgentManager`) and
the selection, simple-chat, rerank and routing components they drive. No REST contract, platform event, permission, or database
schema changes; the `nlti.request.telemetry` record gains fields (see Implementation Notes, Telemetry schema). The write-gate
classifier `IntentParserServiceImpl` (`IntentParserServiceImpl.java:39-58`, whose comment at `:39-42` names the T1 router as
its planned successor) is out of scope and may adopt the seam later.

---

## Decision

### 1. One tagging seam per chat turn

**Decision:** ✅ **Resolved** - Introduce a `QuestionTagger` interface that returns an immutable `QuestionTags` record: one
value **and** one confidence per tag, plus the `source` that produced it (`JEV` or `HEURISTIC`). Placement follows ADR-0026
D2/D3 and the ArchUnit rule `packages_should_be_free_of_cycles`. No cross-module grant names these types (D2), so the interface
is internal, and D3 would place it beside its implementations; but `orchestration` already depends on `service`, and
`ToolRegistryService` and `NltiRouter`, both in `internal.service`, consume the tags, so an interface in `orchestration` would
close a cycle. `QuestionTagger` and `QuestionTags` therefore go in `com.positivity.mcp.internal.domain` (beside the shared
`WorkflowState` and `RouterClassification`), `HeuristicQuestionTagger` and `JevQuestionTagger` in `internal.orchestration`, and
`JevClient` in `internal.client`. The admin keyword, phrase and veto lists stay in `ToolRegistryService` (`internal.service`);
`HeuristicQuestionTagger` reads them from there over the existing `orchestration → service` edge, so no cycle is added.

Each session manager makes **one** call per turn to a new method on the shared selection component both already use
(`ToolSelectionEngine`, `tool-selection-architecture.md:66-68`, whose selection entry points today are the `selectRoleTools`
overloads, `ToolSelectionEngine.java:172`, `:183`), and passes the returned record on. The call comes ahead of `isSimpleChat`
and of the manager's private `routeTier` (which calls `NltiRouter.classify`): today both managers run `isSimpleChat`, then
`routeTier`, then `selectRoleTools` (`SessionAgentManager.java:268, 306, 320`; `StreamingSessionAgentManager.java:252, 266, 274`;
`routeTier` is defined at `SessionAgentManager.java:549` and `StreamingSessionAgentManager.java:500`), and all three consume
tags, so a tagging step inside `selectRoleTools` alone is too late. Parity is structural because the tagging logic lives in one
component, not because the managers stop calling it. The record is passed to every consumer in the table above. No consumer
re-derives a tag from the raw message.

The initial tag set, sent in a single Jev request (the `entity` question joins it once ADR-0069's lexicon exists):

| Tag | Primitive | Replaces / role |
| --- | --------- | --------------- |
| `follows_previous_turn` | Noul | `CONTINUATION_CUES` |
| `simple_chat` | Noul | Replaces `SimpleChatClassifier.isSimpleChat` (rule catalog + caps + `CONTINUATION_CUES`, the last through `follows_previous_turn`); the T0 reply is still generated by the default model through `SimpleChatFastPath` (`SessionAgentManager.java:762-767`) |
| `workflow_state` | Choice over `WorkflowState` | `deriveWorkflowState` |
| `needs_web_search`, `about_inventory`, `about_orders` | Noul each | `fallbackToolsForMessage` keyword guards (the always-on glossary tool is unchanged) |
| `implies_date_window` | Noul | `mentionsDateWindow` phrase/regex lists |
| `admin_account_question` | Noul | Vetoes the admin fast path (`ADMIN_QUERY_KEYWORDS` / `ADMIN_QUERY_PHRASES` / `FAST_PATH_VETO_TERMS`, §3.4); never fires it alone |
| `compound_question` | Noul | #1180 split detection (the split itself is unchanged) |
| `intent` (QUERY / ACTION / UNKNOWN), `complexity` | Choice each | `NltiRouter` JSON output |
| `domain` | Choice | `NltiRouter` JSON output; option set: the RAG scope values (`mcp.rag.preload.docs`), as the Gate 4 design intended (`<rag-scope>`, `gate4-tiered-router-design.md:60`), until ADR-0069 supplies its Domain nodes. The shipped router prompt is free-form (`NltiRouter.java:37-46`: `<one lowercase business domain>` with examples, several not RAG scopes) |
| `entity` | Choice over the ADR-0069 entity lexicon | new: seeds ADR-0069 §5.1; not asked until that lexicon exists |
| `risk` (LOW / MEDIUM / HIGH) | Score | `NltiRouter` JSON output |

**Values and confidence.** Noul returns one probability *p*: the value is `p ≥ 0.5` and the confidence is `max(p, 1 − p)`.
Choice and Score carry Jev's own confidence. Every threshold in this ADR (`mcp.tagging.thresholds.<tag>`, §3.4, §6) applies to
that confidence; the default is 0.75 for every tag until shadow data sets per-tag values.

Adding a tag is a code change reviewed like any other; tags are not configurable at runtime.

### 2. Jev behind the seam; heuristics as the permanent fallback

**Decision:** ✅ **Resolved** - Two implementations:

- `JevQuestionTagger` — calls a System One (`/v1/systemone`) endpoint, by default the in-cell Ollama container (§5).
- `HeuristicQuestionTagger` — today's rules, moved behind the interface unchanged. It is the **test-profile**
  implementation, the **fallback** on any Jev failure, and the per-tag fallback when a Jev answer's confidence is below that
  tag's threshold.

Any tagging error, timeout, 429/529, Ollama `{"error": …}` body, or malformed response yields the heuristic result for the whole turn. A chat turn never
fails because tagging failed.

For the router-derived tags (`intent`, `risk`, `complexity`, `domain`) the heuristic value is
`RouterClassification.safeDefault()` (`domain/RouterClassification.java:21-24`): UNKNOWN, HIGH, MULTI_DOMAIN and `master`. So
"below threshold → heuristic value" and §3.5's "a low-confidence `risk` counts as HIGH" are the same rule: a router tag below
its threshold selects T2-complex.

The rules move unchanged, but how their results are applied changes in one respect. Today's keyword-added tools bypass
`mcp_tool_permission` at selection: `ToolSelectionEngine.java:189-190` merges `fallbackToolsForMessage` into `fallbackTools`,
which the managers then merge with the role tools (`SessionAgentManager.java:324-325`), without the permission gate
(`SharedOrchestrationSupport.java:25-28`), and relies on downstream `@PreAuthorize` to refuse an unpermitted call. Under this
ADR the tools added by **both** taggers' tags are intersected with the permission-gated set (§3.1). This is a deliberate
behaviour change.

### 3. Tags are advisory and can never widen access

**Decision:** ✅ **Resolved** - Tags influence *which* permitted tools and model the turn uses; they never decide *whether*
the caller may use them. Binding rules:

1. **Permission gating runs first and is untouched.** Tags act only on the set that survived
   `findEnabledByPermissionsAndWorkflow` and role scoping.
2. **Additive only for tools.** A tag may add a permitted tool on top of the semantic top-K (today's keyword-fallback additions
   are few and facade-only); it may not remove or displace a semantically ranked tool. Tools added by tags and tools added by
   ADR-0069's scope set are unioned on top of the ranked cuts; ADR-0069's `added-tool-slots` cap counts scope-added tools only.
3. **Persisted state wins.** `workflow_state` applies only to session-less callers; a persisted `NltiSession` state is never
   overridden. For a session-less caller the precedence is: persisted `NltiSession` state → the `workflow_state` tag at or above
   threshold in `enforce` → the ADR-0069 graph lookup where enforced → the heuristic phrase match; the first that yields a
   value wins.
4. **The admin fast path may be vetoed by a tag, never fired by one alone.** Because the path returns `AdminFacadeTool`
   alone, a Jev false positive would suppress every other candidate. The fast path fires only when a heuristic keyword or
   phrase matches and is not vetoed, **and** `admin_account_question`, resolved per §2 (Jev at or above threshold, otherwise the
   heuristic value), is true. A Jev `false` at or above threshold therefore vetoes it.
5. **Risk never downgrades.** When tiering is enabled, `intent = ACTION`, `risk ≥ MEDIUM`, `complexity = MULTI_DOMAIN`, or a
   risky domain (accounting, tax, admin, security) always selects T2-complex (`TierSelector.java:26-38`), whatever the
   confidence. A low-confidence `risk` answer is treated as HIGH: below threshold it takes the heuristic value, which is
   `safeDefault()`'s HIGH (§2).
6. **Tags are typed values, not text.** Only enum/boolean/number values from the response are read; no response string is
   ever placed into a prompt, a log message template, or a tool argument.

### 4. Local by default; data minimisation; the hosted-provider condition

**Decision:** ✅ **Resolved** - Tagging runs on a decision model served by the platform's **own Ollama container** on the
cell network (§5). The message is not sent outside the cell to tag it, and there is no tagging credential.

The request carries **only the current user message text** (the `state`) and the fixed question definitions, whichever
provider serves it. It never carries conversation history, RAG context, tool results, the system/persona prompt, the username,
user id, tenant id, or any forwarded header. This keeps the request caller-independent (ADR-0069 relies on it) and keeps a
provider change a configuration change.

This ADR does **not** enable TypeSafe's hosted Jev. Pointing the tagger at it, or at any provider outside the cell, in an
environment that holds **real customer data** requires, first, an executed data processing agreement with zero data retention,
recorded in this ADR's Changelog. Without one, an external provider may be used only in environments populated with synthetic
data. This ADR's rule: the tagging client never
logs message text at any level in any profile; the failure log carries the error class, HTTP status and latency (ADR-0046
sets only the levels). The existing message previews in `ToolSelectionEngine` (DEBUG at l.191-199; ERROR at l.275-280 when a
selection fails) and `ToolRegistryService` (`:126-136`, `:152-160`, `:218-227` and elsewhere) are outside this ADR. The session
managers (`SessionAgentManager.java:273,285,334`) also log previews; the list is not exhaustive.

### 5. The Jev HTTP API is the contract, not the vendor

**Decision:** ✅ **Resolved** - `JevQuestionTagger` targets the System One wire contract (`POST /v1/systemone`) through the thin
`JevClient` (§1), with its own base URL and model:

- **Base URL** `mcp.tagging.provider.base-url`, default the in-cell container `http://ollama:11434`. It is deliberately a
  separate setting from `spring.ai.ollama.base-url` (the chat endpoint, hosted `https://ollama.com` on alpha), so tagging
  can never follow the chat model off the cell by inheritance.
- **Model** `mcp.tagging.provider.model`, a model pulled into that container. The candidates are `nimble`, `tev1` (4B) and
  `tev1:0.8b`. This ADR fixes the protocol and the host, not the model: the model is chosen by the bake-off in §6 and can
  be changed by configuration.
- **Ollama version.** `/v1/systemone` needs Ollama 0.35 or later, so the `ollama` image stops floating on `latest` and is
  pinned to a 0.35+ release.
- **Ollama's differences** (Drivers) are met in the client: every question carries instructions, a `{"error": …}` body is a
  failure (heuristic fallback, §2), and the tag set stays well under the 64-question cap.

Any service implementing the contract can still be substituted by configuration, for example TypeSafe's hosted Jev (subject
to §4's condition) or the open-source `logan-markewich/jeff`.

The community Spring AI starter (`org.springaicommunity:spring-ai-starter-typesafe:0.1.0`) is **not** adopted. Its
`TypeSafeClient` is reported to work against Ollama unchanged, but it is pre-1.0, community-maintained, and unverified against
the platform's Spring AI 2.0.1. Revisit when it reaches 1.0.

The client is a plain (not `@LoadBalanced`) `RestClient` with **no retries** on the chat path and no circuit state beyond
"fail to heuristic". Its connect+read timeout is a latency budget, `mcp.tagging.provider.timeout`, default 800 ms. The budget
is not raised to fit a slow model. If no candidate meets it on the target host (§6), the choice is recorded in the Changelog:
raise the budget explicitly, move tagging to a host with an accelerator, or keep the heuristics.

### 6. Rollout: off → shadow → enforce, per tag, by measurement

**Decision:** ✅ **Resolved** - `mcp.tagging.mode` is `off` | `shadow` | `enforce` (default `off`).

- **shadow** — both taggers run; consumers act on the heuristic result; the Jev result, confidences, agreement, and latency
  are recorded on the eval turn trace and `nlti.request.telemetry`.
- **enforce** — consumers act on the Jev result, per tag, where confidence ≥ that tag's threshold
  (`mcp.tagging.thresholds.<tag>`); below it, the heuristic value is used (`safeDefault()` for the router-derived tags, §2).

ADR-0069 reads the tags a consumer would act on: the heuristic result in `shadow`, the Jev result at or above threshold in
`enforce`.

**Model selection comes first.** Before any tag is promoted, the candidate models (§5) run in `shadow` on the gate question
sets on the host that will serve them. Each is measured on per-tag accuracy against the heuristic in en, fr and es, on the
latency of the single batched tagging request (p50 and p95, warm, with the embedding model loaded beside it), and on
resident memory. The smallest model whose p95 fits the timeout budget and whose accuracy meets the promotion rule below is
chosen. The choice and its evidence are recorded in the Changelog. The models' calibration is unverified, so the per-tag
thresholds come from this shadow data, not from the 0.75 default. A later model change repeats the bake-off.

A tag is promoted to `enforce` only after a recorded gate run shows it at least as accurate as its heuristic on the gate
question sets **in each of en, fr and es**. The promotion and its evidence are recorded in this ADR's Changelog.

Chat orchestration exists only under `@Profile("alpha")` (`SessionAgentManager.java:74`, `StreamingSessionAgentManager.java:67`,
`NltiRouter.java:32`); the `dev` profile has no chat path, so `shadow` and `enforce` apply wherever the `alpha` profile runs.

### 7. The Gate 4 LLM router call is retired

**Decision:** ✅ **Resolved** - `NltiRouter` stops calling a chat model. It maps the `intent`, `risk`, `complexity` and
`domain` tags to a `RouterClassification` and keeps `TierSelector` unchanged. The `routerChatModel` bean and
`mcp.model.router` / `MCP_MODEL_ROUTER` are removed once the router tags reach `enforce` (pre-production policy: no dead
configuration kept). Tier routing itself (`mcp.model.tiering-enabled`, `simple`, `complex`) is unaffected and stays dormant
until a real T2-simple model is chosen.

### Out of scope

- Using Jev as a guardrail, reranker, tool index, or evaluator (the Spring AI integration offers these); each would need
  its own decision.
- Per-tenant opt-out of external tagging. It is moot while tagging is local (§4); it must be decided before an external
  provider is enabled for real data.
- Replacing semantic tool ranking; tags sit beside it, not in it.

---

## Alternatives Considered

1. **Keep and extend the heuristics.** Zero egress and zero latency, but it is the status quo that keeps missing phrasings,
   has no confidence, and costs a word-list change per gate miss and, for the five English-only heuristics, leaves fr and es
   unserved. Retained as the fallback (§2), rejected as the only mechanism.
2. **Re-enable the Gate 4 LLM router (`qwen3:4b`) and widen its JSON.** Stays with the existing Ollama provider (hosted on
   alpha), but it is the design already switched off (#1683): a generative call per turn, free-text JSON parsing with a
   safe-default failure mode, and no calibrated confidence.
3. **Embedding-similarity classifier over labelled exemplars (bge-m3, already deployed).** No new vendor and no egress, but
   needs a curated exemplar set per tag per language, produces similarities rather than calibrated probabilities, and
   duplicates what the semantic tool ranking already does. A reasonable fallback if no decision model meets §6 on the target
   host.
4. **`jeff` (GLiFormer ~400M, community Jev-compatible server) as the local provider.** Also removes egress, but adds a
   separate model runtime to operate, where the Ollama models run in the container the cell already has; it is a community
   project with no published accuracy or calibration. Reachable by configuration (§5); not chosen.
5. **TypeSafe's hosted Jev as the provider** (this ADR's first draft). No local CPU or memory cost, but
   every tagged message goes to a third party, real data needs a DPA with zero data retention first, and it adds an API key,
   per-token cost, rate limits and an early-access vendor dependency. Reachable by configuration under §4's condition; not
   the default.
6. **Let the executor LLM tag the question inside its own turn.** No extra call, but the tags are needed *before* the call
   (tool set, tier, simple-chat), so this cannot work for the decisions that matter.

---

## Consequences

### Positive ✅

- ✅ One typed, per-turn `QuestionTags` record replaces six scattered heuristics and one dormant router; every decision and
  its confidence appears in telemetry and eval traces.
- ✅ Calibrated confidence makes uncertain cases distinguishable: consumers fall back per tag instead of all-or-nothing.
- ✅ Phrasings outside a word list are no longer silently missed, and the primary path no longer depends on hand-maintained
  word lists; the heuristic fallback keeps them: they live in `HeuristicQuestionTagger` and, once the ADR-0069 lexicon exists,
  are read from it (the admin lists stay in `ToolRegistryService`, §1).
- ✅ Cheaper than re-enabling the router: one non-generative call instead of a `qwen3:4b` generation, with nothing to parse,
  and no per-token cost because the model runs in the cell. Against today's dormant router it adds one call per turn (see
  Negative).
- ✅ No new data recipient and no new vendor: the message is tagged inside the cell (§4), with no API key, rate limit or
  data processing agreement.
- ✅ Safety posture is not weakened: permission gating first (now also for tag-added tools), additive tools, no risk downgrade,
  heuristic on any failure.

### Negative ⚠️

- ⚠️ **CPU and memory on a shared, GPU-less host.** The model runs in the `ollama` container beside `bge-m3`, on the one
  `t3.2xlarge` that runs the whole alpha cell. Resident memory is ~0.8–9.5 GB depending on the model, and every turn spends
  CPU on it, including the burstable CPU credits the other 21 containers draw on. Mitigated by the §6 bake-off (the smallest
  model that meets the bar wins) and by keeping the model resident rather than reloading it per call.
- ⚠️ **Added per-turn latency, unmeasured on this hardware.** One tagging call on paths whose classification is rule-only today
  (the T0 reply itself is an LLM call), including the T0 fast path. The published figures are from GPU and Apple-silicon hosts.
  Mitigated by the timeout budget with fail-to-heuristic (§5), the bake-off measuring p95 on the target host (§6), and a
  possible follow-up that skips the call when the heuristic is certain (e.g. an exact T0 catalog hit).
- ⚠️ **Young models and a new endpoint.** `/v1/systemone` shipped in Ollama 0.35 on 2026-09-29, `tev1` is marked experimental,
  and the models' calibration is unpublished. Mitigated by pinning the Ollama version, deriving thresholds from shadow data,
  fail-to-heuristic on every error, and the heuristic tagger remaining the tested baseline.
- ⚠️ **Multilingual accuracy is unproven.** Mitigated by the per-language promotion rule in §6.
- ⚠️ **Two implementations to keep in step.** The heuristic tagger must keep answering every tag (the router-derived ones with
  `safeDefault()`, §2), so a new tag costs both.

### Neutral

- The tag set is code, reviewed in PRs; there is no runtime tag editor.
- Tier routing stays dormant; this ADR changes what feeds it, not whether it runs.
- Tag-added tools are intersected with the permission-gated set (§2, §3.1), so a tool the caller lacks permission for is no
  longer offered at selection; downstream `@PreAuthorize` is unchanged.

---

## Implementation Notes

- **Components:** `QuestionTagger`, `QuestionTags` (`internal.domain`), `HeuristicQuestionTagger`, `JevQuestionTagger`
  (`internal.orchestration`), `JevClient` (`internal.client`, plain `RestClient`); placement per §1. Consumers updated in
  `SimpleChatFastPath`/`SimpleChatClassifier`, `ToolSelectionEngine`, `ToolRegistryService`, the #1180 rerank split, and
  `NltiRouter`. Each session manager makes one call to a new `ToolSelectionEngine` method, ahead of `isSimpleChat` and its own
  private `routeTier` (which calls `NltiRouter.classify`), and passes the record on; transport parity is structural because
  the tagging logic lives in one component (per `tool-selection-architecture.md`), not because the managers stop calling it.
- **Configuration:** `mcp.tagging.mode` (`off`), `mcp.tagging.provider.base-url`, `mcp.tagging.provider.timeout` (`800ms`),
  `mcp.tagging.provider.model` (set by the §6 bake-off), `mcp.tagging.thresholds.<tag>` (default 0.75 until shadow data sets
  them, §1, §6). The base URL defaults to `http://ollama:11434` and is independent of `OLLAMA_CHAT_BASE_URL` (§5). No tagging
  credential for the local provider; an external one's key would come from an environment secret, never committed.
  `application-test.yml` pins `mode: off` so no test context calls out.
- **Container:** pin the `ollama` and `ollama-init` images to a 0.35+ release instead of `latest`; `ollama-init` pulls the
  chosen tagging model beside `${OLLAMA_EMBEDDING_MODEL}`; set `OLLAMA_MAX_LOADED_MODELS` to at least 2 and a
  `keep_alive` long enough that neither the embedding model nor the tagging model is evicted between turns (a cold load takes
  seconds and would blow the budget); add the model's resident memory to the cell's memory budget.
- **Testing:** unit tests per consumer against fixed `QuestionTags` (high/low confidence, `HEURISTIC` source); a parity test
  asserting both session managers tag before `isSimpleChat`; a `JevClient` test against a stubbed server covering timeout, 429,
  529, an Ollama `{"error": …}` body, and a malformed body → heuristic; a test that every question carries instructions; a test asserting the request body
  contains only the message and question definitions (§4); a log-capture test asserting no message text on a tagging failure
  (§4); `ArchitectureTest` unchanged (all new types are `internal`, and `packages_should_be_free_of_cycles` must stay green,
  which drives §1's placement).
- **Rollout:** `off` → `shadow` with each candidate model wherever the `alpha` profile runs (docker-compose `alpha`,
  `scripts/fixtures/seed/alpha`, then alpha environments; local tagging needs no data condition) → model chosen and recorded
  (§6) → per-tag `enforce` per §6.
- **Telemetry schema:** `nlti.request.telemetry` is `schemaVersion` 1 (`NltiRequestTelemetry.java:52-70`), so the new tag
  fields (values, confidences, source, agreement) are a schema version bump.
- **Monitoring:** tagging latency histogram, fallback rate by reason (`timeout`, `error`, `low_confidence`, and
  `rate_limited` for an external provider), per-tag shadow agreement rate, the model tag in use, and the `ollama` container's
  CPU and memory. Extend the NLTI overview dashboard; alert on sustained fallback rate above 20%.
- **Docs to update on implementation:** `domains/general/mcp-server/architecture.md` (request flow),
  `domains/general/mcp-server/tool-selection-architecture.md` (keyword fallback, admin fast path, workflow state), and the
  `pos-mcp-server` README (configuration and the tagging model dependency), and the backend `docker-compose.yml` comments
  for the `ollama` service.

---

## References

- **Related ADRs:** [ADR-0009](0009-backend-domain-responsibilities-guide.adr.md) (pos-mcp-server: "configurable LLM provider
  integration"), [ADR-0026](0026-service-contract-boundary-policy.adr.md) (internal-only placement),
  [ADR-0046](0046-environment-log-level-policy.adr.md) (logging), [ADR-0062](0062-postgres-row-level-multitenancy.adr.md) §11
  (tenant-scoped conversations and LLM context), [ADR-0069](0069-mcp-scope-graph-pre-llm-narrowing.adr.md) (scope graph:
  supplies the option sets for the `domain` and entity Choice questions and consumes the tags).
- **Related Documentation:** `domains/general/mcp-server/architecture.md`, `domains/general/mcp-server/tool-selection-architecture.md`.
- **External Resources:** [Spring AI and TypeSafe Jev](https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment/),
  [TypeSafe Jev project reference](https://gist.github.com/pjburnhill/adf8d28efcad9df037bfdece178ef965),
  [Introducing System One Models & Jev](https://typesafe.ai/blog/introducing-system-one-models-and-jev),
  [Jev data privacy guide](https://jev101.org/guides/jev-privacy-guide),
  [Self-hosting boundaries](https://befailproof.ai/jev/self-hosting/),
  [jeff — self-hosted Jev-compatible API](https://github.com/logan-markewich/jeff),
  [Ollama v0.35.0 release (`/v1/systemone`)](https://github.com/ollama/ollama/releases/tag/v0.35.0),
  [Ollama: Jev-style decision models](https://ollama.com/blog/ollama-now-supports-jev-style-decision-models),
  [`nimble`](https://ollama.com/library/nimble), [`tev1`](https://ollama.com/library/tev1),
  [spring-ai-typesafe #20 (Ollama System One notes)](https://github.com/spring-ai-community/spring-ai-typesafe/pull/20),
  [community benchmark: `tev1:0.8b` vs `nimble`](https://github.com/itumor/lazy-route/issues/35).

**Documents affected on acceptance** (listed only; each amended document would carry a dated amendment block pointing here,
applied on acceptance, not by this ADR):

| Document | Change | When |
| -------- | ------ | ---- |
| `domains/general/mcp-server/archive/gate4-tiered-router-design.md` | T1 router replaced by §7; dated amendment block | On acceptance |
| `domains/general/mcp-server/archive/nl-interface-design.md` | "self-hosted Ollama" is already stale: alpha chat runs on hosted `https://ollama.com` (`application-alpha.yml:19`); block noting hosted chat Ollama and that tagging runs on the in-cell Ollama container | On acceptance |
| `domains/general/mcp-server/archive/README.md` | "Why archived" rows | On acceptance |
| `docs/adr/README.md` | Decision-matrix row | On acceptance |
| `domains/general/mcp-server/architecture.md`, `domains/general/mcp-server/tool-selection-architecture.md` | Already listed under Implementation Notes | On implementation |

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | | | |
| NLTI Domain | | | |
| Security & Authorization | | | §4 local provider and external-provider condition |

---

## Timeline

- **Proposed**: 2026-09-30

---

## Changelog

- **2026-09-30**: Initial draft.
- **2026-09-30**: Linked ADR-0069 (scope graph) under Related ADRs.
- **2026-09-30**: Review round (PR #525): entity Choice tag, Noul confidence definition, permission intersect for tag-added
  tools, package placement, hosted Ollama egress noted, corrections against pos-mcp-server source.
- **2026-09-30**: Local provider. Ollama 0.35 serves Jev-protocol decision models (`nimble`, `tev1`, `tev1:0.8b`) at
  `/v1/systemone`, so tagging defaults to the in-cell `ollama` container: §4 rewritten (no egress; the hosted-provider DPA
  condition kept for any external provider), §5 (own base URL and model settings, Ollama 0.35+ pin, Ollama differences, the
  timeout as a budget), §6 (model bake-off on the target host before any promotion; thresholds from shadow data), Constraints
  and Drivers (GPU-less `t3.2xlarge`), Alternatives (hosted Jev moved to an alternative), Consequences, Implementation Notes.
