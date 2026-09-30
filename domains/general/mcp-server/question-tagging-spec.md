---
type: Specification
title: pos-mcp-server Question Tagging — Implementation Specification
description: What is built to implement ADR-0068 (pre-LLM question tagging with a Jev-protocol decision model served by the cell's Ollama) in pos-mcp-server, how the tags feed the scope graph, RAG and tool selection, what is deferred, and the decisions the ADR leaves to implementation.
domain: general
status: draft
created: '2026-09-30'
related: [ADR-0068, ADR-0069, ADR-0026, ADR-0046, ADR-0062]
tags: [mcp-server, general, question-tagging, scope-graph]
---

Implements [ADR-0068](../../../docs/adr/0068-mcp-pre-llm-question-tagging-decision-model.adr.md) as revised for the local
provider. The ADR is the authority for *what* and *why*; this document fixes what it leaves open and states what each delivery
wave contains. Where they disagree, the ADR wins and this document is wrong. Section references (§n) are to ADR-0068 unless
prefixed `0069 §n`. The scope graph this document integrates with is specified in [scope-graph-spec.md](scope-graph-spec.md).

Code paths are relative to `durion-positivity-backend/pos-mcp-server/`. Package placement follows §1: `QuestionTagger`,
`QuestionTags` and the tag value types in `com.positivity.mcp.internal.domain`; `HeuristicQuestionTagger`,
`JevQuestionTagger` and the per-turn `TaggingService` in `internal.orchestration`; `JevClient` in `internal.client`;
`TaggingProperties` in `internal.config`.

## 1. What is delivered, and what is not

| Wave | Content | Runtime effect when merged |
| ---- | ------- | -------------------------- |
| 1 | The seam: `QuestionTags`, `QuestionTagger`, `HeuristicQuestionTagger` (today's rules moved behind the interface), `JevClient` + `JevQuestionTagger` against `POST /v1/systemone`, `TaggingService` (mode, merge, fallback), the single call per turn in both session managers, every consumer reading its decision from the tag record, shadow recording (eval trace, telemetry schema 3, metrics). Tag-added tools intersected with the permission-gated set (§2, §3.1) in every mode | With `mode: off` (default) every decision is today's decision, from the same rules, now taken once. One deliberate change (§2): a keyword-added facade tool the caller has no permission for is no longer offered |
| 2 | `enforce`, per tag: simple chat, workflow-state precedence (§3.3), tag-added tools, admin-fast-path veto (§3.4), compound gate, the router mapped from tags (§7, chat call removed), scope-graph integration (tag seeds, option lists, `lookups` consumer, lexicon `workflow_state`), the widened compound splitter, the T0 skip | None until a tag is listed in `mcp.tagging.enforced-tags` |
| 3 | Operations: `ollama` / `ollama-init` pinned to 0.35+, tagging model pull, `OLLAMA_MAX_LOADED_MODELS`, keep-alive; the shadow report used by the §6 bake-off; dashboard and alert; README; `durion` docs | — |

**Deferred, with the reason:**

| Item | ADR | Why deferred |
| ---- | --- | ------------ |
| Choosing the model, per-tag thresholds, promoting any tag | §6 | Requires the bake-off in `shadow` on the target host; an operations step whose evidence goes in the ADR Changelog. Wave 3 delivers the report it needs |
| Removing the `routerChatModel` bean and `mcp.model.router` | §7 | "once the router tags reach `enforce`". Wave 2 stops `NltiRouter` from calling it; the bean and property are removed at promotion |
| `IntentParserServiceImpl` adopting the seam | Scope | Out of scope by the ADR |
| Skipping the tagging call when the heuristic is certain, beyond the exact simple-chat rules | Consequences | Named as a possible follow-up; needs shadow latency data first. The exact-rule case is a Wave 2 item (§2.5) |
| Per-tenant opt-out | Out of scope | Moot while tagging is local |

## 2. Decisions the ADR leaves open

### 2.1 Configuration

`TaggingProperties` (`mcp.tagging`):

| Key | Default | Meaning |
| --- | ------- | ------- |
| `mode` | `off` | `off`: heuristic tagger only. `shadow`: both taggers run, consumers act on the heuristic result, the decision-model result is recorded. `enforce`: as `shadow`, and the tags in `enforced-tags` act |
| `enforced-tags` | `[]` | **Addition to the ADR's key list.** §6 promotes tags one at a time; one `mode` value cannot express that. A tag not listed behaves as in `shadow` even when `mode` is `enforce`. An entry may carry a direction: `<tag>` (symmetric) or `<tag>:veto` (the model may only turn the heuristic's `true` into `false`; acting value = heuristic AND model). `simple_chat` is promoted `:veto` first: a false positive loses the turn to the history-less T0 path, a false negative costs one LLM turn. `admin_account_question` is veto-only by §3.4 and is expressed the same way |
| `provider.base-url` | `http://ollama:11434` | §5; independent of `spring.ai.ollama.base-url` |
| `provider.model` | `tev1:0.8b` | The smallest candidate, so a fresh checkout works in `shadow`; the bake-off overrides it (§6) |
| `provider.timeout` | `800ms` | Connect + read budget (§5) |
| `provider.api-key` | unset | For an external provider only (`TYPESAFE_API_KEY` from the environment). Sent as `Authorization: Bearer`. Never logged |
| `provider.keep-alive` | `30m` | Sent as `keep_alive` when the provider is Ollama, so the model stays resident between turns (Implementation Notes → Container). Omitted when unset |
| `thresholds.<tag>` | `0.75` | Per-tag confidence threshold (§1) |
| `thresholds.workflow_state.non-idle` | `0.75` | A stricter threshold a non-`IDLE` `workflow_state` answer must meet (the one tag that removes tools, §2.6); `thresholds.workflow_state` still governs an `IDLE` answer |
| `max-state-chars` | `4000` | The message is cut at this length before it becomes the `state` (nimble: 8,192-token prompt; tev1: ~2,000 tokens; 64 KiB body). A cut message is still tagged; the cut is counted |

`application-test.yml` pins `mode: off`.

### 2.2 The wire contract

`JevClient` speaks the System One contract as Ollama 0.35 and TypeSafe publish it:

```json
POST {base-url}/v1/systemone
{ "model": "tev1:0.8b", "state": "<message text>", "keep_alive": "30m",
  "questions": {
    "simple_chat":    { "type": "noul",   "instructions": "..." },
    "workflow_state": { "type": "choice", "instructions": "...", "criteria": { "IDLE": "...", "CREATING_PO": "..." } },
    "risk":           { "type": "score",  "instructions": "...", "criteria": ["LOW", "MEDIUM", "HIGH"] } } }

200
{ "model": "tev1:0.8b",
  "answers": {
    "simple_chat":    { "type": "noul",   "noul": 0.93 },
    "workflow_state": { "type": "choice", "choice": "IDLE", "probabilities": { "IDLE": 0.88, "CREATING_PO": 0.07 }, "confidence": 0.81 },
    "risk":           { "type": "score",  "score": 0.41, "legend": { "0": "LOW", "1": "MEDIUM", "2": "HIGH" },
                        "probabilities": { "0": 0.55, "1": 0.30, "2": 0.15 }, "confidence": 0.35 } },
  "usage": { "input_tokens": 120, "output_tokens": 3 } }
```

- `state` is the message string (both providers accept a string; nimble also accepts an object).
- Every question carries `instructions` (Ollama requires it on every type).
- `choice.criteria` is a map label → description; `score.criteria` is the ordered level list (lowest first).
- A body containing `"error"`, a non-2xx status, a timeout, a missing answer for an asked question, an unknown `choice`
  label, or a probability outside `[0, 1]` is a **provider failure**: the whole turn takes the heuristic result (§2).
- **Values and confidence** (§1): Noul → value `p ≥ 0.5`, confidence `max(p, 1 − p)`. Choice → value the `choice` label,
  confidence the answer's `confidence`. Score → value the level with the highest probability (ties: the higher level), confidence
  the answer's `confidence`. `score` (the probability-weighted level) is recorded but not used for the value.
- Limits (nimble and tev1 alike): 64 questions per request, 2–26 options per Choice or Score, 64 KiB body. A test asserts the
  question set stays within them for the largest option lists the graph can produce (§2.4).
- No retries, no circuit state, plain `RestClient` with its own connect and read timeout equal to `provider.timeout`.
- Logging (§4): the client never logs `state` or any answer string at any level. Failure logs carry the error class, HTTP
  status, provider host, model and latency. The `error` text of an Ollama body is logged truncated to 200 characters only
  after a test proves it cannot contain the state (it is a server message about the model or request shape); if that cannot be
  shown, only its length is logged.

### 2.3 The tag set and its questions

`TaggingQuestions` builds the request from one closed list of tag definitions in code (§1: adding a tag is a code change).
`QuestionTags` holds, per tag, `value`, `confidence`, `source` (`JEV` | `HEURISTIC`) and, when both ran, the other tagger's
value and confidence for agreement. Tag names are the `questions` keys.

| Tag | Primitive | Options / criteria | Heuristic value (unchanged rules) |
| --- | --------- | ------------------ | --------------------------------- |
| `follows_previous_turn` | Noul | — | any `CONTINUATION_CUES` token present |
| `simple_chat` | Noul | — | `SimpleChatClassifier.isSimpleChat` (which already includes the cue check) |
| `workflow_state` | Choice | `WorkflowState` values, descriptions from the PO / ASN / recon phrases | `deriveWorkflowState` |
| `needs_web_search` | Noul | — | the web keyword guard |
| `about_inventory` | Noul | — | the inventory keyword guard |
| `about_orders` | Noul | — | the order keyword guard |
| `implies_date_window` | Noul | — | `mentionsDateWindow` |
| `admin_account_question` | Noul | — | admin keyword or phrase matched **and** no veto term |
| `compound_question` | Noul | — | `RerankedContentRetriever.splitSubQueries(message, max).size() ≥ 2` |
| `intent` | Choice | `QUERY`, `ACTION`, `UNKNOWN` | `safeDefault()` → `UNKNOWN` |
| `complexity` | Choice | `SINGLE_LOOKUP`, `MULTI_DOMAIN` | `safeDefault()` → `MULTI_DOMAIN` |
| `risk` | Score | `LOW`, `MEDIUM`, `HIGH` | `safeDefault()` → `HIGH` |
| `domain` | Choice | permanently the curated RAG-scope vocabulary: the distinct `rag-scope` values of `mcp.rag.preload.docs` plus `master` (15 today), never the graph's tool-catalog domains | `safeDefault()` → `master` |
| `entity.<key>` | Noul, one per lexicon entity | "Is this message about a <en terms> (<fr terms>; <es terms>)?"; see §2.4 | none: the heuristic tagger does not answer entity tags (the lexicon term match already seeds the scope, 0069 §5.1) |

The `domain` options are not the graph's tool-catalog domains: those number about 33, over the 26-option cap, and include
`bulk-loader`, `mcp-server` and `date-window`, which are not user vocabulary. Each scope's criteria sentence lives in a new
`domains:` block in `entities.yaml`; the real-config validation requires a sentence per scope in both profile lists. The scope
maps to a `Domain` node through the inverse of `domain_scopes` (§2.7); tool domains with no rag-scope (`vehicle-inventory`,
`supplier`, `location`, `catalog`, `marketing`, `people-contact`, `vehicle-fitment`) are not options. This also keeps
`TierSelector.RISKY_DOMAINS` (`accounting`, `tax`, `admin`, `security`) matching the acting domain.

A `entity.<key>` answer is a seed when p ≥ 0.5 and confidence ≥ `thresholds.entity`. With 31 to 33 entity Nouls and the 13 fixed
tags a request carries 44 to 46 questions, under the cap of 64.

The heuristic tagger reports confidence `1.0` for every tag it answers (a rule either fires or not) and `source = HEURISTIC`.
Instructions are fixed English text describing the decision (`nimble`'s guidance: a question phrased as a question). Every
instruction is prefixed with a fixed CONTEXT sentence naming the shop-management setting and stating that the message may be in
English, French or Spanish. They never include the message, caller or tenant.

The exact per-tag instructions and criteria are code (`TaggingQuestions`), reviewed by the NLTI domain agent; the review's
wording is adopted verbatim in Wave 1 and is deliberately not pasted here. The intent of the contested ones:

- A bare answer or confirmation counts as `follows_previous_turn` and is not `simple_chat`.
- `about_orders` covers sales orders and purchase orders, not revenue totals.
- `admin_account_question` excludes customer, supplier, vendor, bank and GL accounts.
- `compound_question` excludes a single request with several conditions, or a list of related items.
- `workflow_state` requires that the user is carrying out the workflow; `IDLE` includes questions about POs, shipments, counts
  and returns.

### 2.4 One Noul per entity

The lexicon holds 33 entities and grows; both local models accept at most 26 options per Choice. The `entity` question of §1 is
therefore asked as **one Noul per lexicon entity**, `entity.<key>`: "Is this message about a <en terms> (<fr terms>; <es
terms>)?", built from the lexicon's terms. Every answer with p ≥ 0.5 and confidence ≥ `thresholds.entity` yields a seed, so a
question that names two entities seeds both (0069 Consequences: multi-domain questions). `enforced-tags` lists `entity`, not the
individual questions. When the graph is empty (`mcp.scope-graph.mode: off`) no entity question is asked, per §1.

Grouped Choice questions (entities packed into groups of at most 24 plus `none`) were rejected:

- Co-mentioned entities are same-domain (invoice and payment; order, return and purchase order), so they land in one group, and a
  Choice returns one label per group. That contradicts 0069's multi-entity seeding.
- Unequal group sizes make one threshold incomparable across groups, and make `none` unpredictable.

The cost is a wider request. The question count and request body bytes are recorded on the trace (§2.8) and the bake-off measures
them (§2.9). If the width proves too slow on the target host, the fallback design is conflict-aware grouping: entities linked by
`RELATES_TO`, or sharing `OWNED_BY`, go in different groups; groups have equal sizes of at most 15 plus `none`.

### 2.5 Where the turn is tagged

`ToolSelectionEngine.tag(message)` (the shared selection component, §1) returns the `QuestionTags` for the turn. Both
session managers call it **once**, before `isSimpleChat` and before `routeTier`, and pass the record on:
`simpleChatFastPath.isSimpleChat(message, tags)`, `routeTier(tags)`, `selectRoleTools(role, codes, message, [state,] tags)`.
The record is published on `RequestScopedUserContext` beside the caller (for the per-request readers: the compound split in
`RerankedContentRetriever`, the prompt supplier's telemetry) and cleared with it; the streaming manager carries it across the
subscribe hop by parameter, as it carries the scope. The record is built by `TaggingService`:

1. Run the heuristic tagger (always; sub-millisecond).
2. If `mode` is not `off`, run the decision-model tagger inside the timeout budget. Any provider failure → the heuristic record,
   with `fallbackReason` set (`timeout`, `error`, `rate_limited`, `malformed`).
3. Merge per tag: the acting value is the decision-model value when `mode` is `enforce`, the tag is listed in `enforced-tags`,
   its confidence ≥ its threshold, and (for a `:veto` entry) the heuristic value is `true`; otherwise the heuristic value (with
   `fallbackReason = low_confidence` for that tag when the model answered below threshold in `enforce`). Both values are kept
   for the trace.

No decision is re-derived from the message by a consumer; every consumer receives the record. Warm-up (`prebuildRoleAgents`)
does not tag: it passes `QuestionTags.none()` (heuristic values for the role-name message would be meaningless and would
count as turns). `QuestionTags.none()` (warm-up, an absent record) makes every consumer behave exactly as `off`.

**T0 skip.** When the heuristic hits an exact `SimpleChatRuleCatalog` rule (a certain heuristic), the provider call is skipped for
that turn. This is a Wave 2 item; it is critical to conversation feel and needs no latency data.

### 2.6 Consumers, mode by mode

| Consumer | `off` and `shadow` (acting value = heuristic) | `enforce` with the tag listed | Where |
| -------- | --------------------------------------------- | ----------------------------- | ----- |
| Simple chat | `simple_chat` heuristic = today's classifier | `simple_chat` decides, promoted `:veto` first (§2.1); symmetric only when shadow shows the model-true/heuristic-false cases are correct. `follows_previous_turn` true at or above threshold forces `false` whatever `simple_chat` says | `SimpleChatFastPath` |
| Workflow state (session-less callers only) | heuristic phrase match | precedence (§3.3): persisted `NltiSession` → model `workflow_state` at or above threshold → heuristic. The 0069 graph lookup (entity seed's lexicon `workflow_state`) is a **heuristic source** inside `HeuristicQuestionTagger`, used when the graph is built and the 0069 `lookups` consumer is enforced, else the phrase match; it is not a separate step. See the notes below | `ToolSelectionEngine` |
| Keyword-added facade tools | heuristic guards add web search, inventory, order, date-window tools; glossary always | the four Noul tags add them; where `lookups` is enforced, `about_inventory` / `about_orders` are replaced by the lexicon's facade-tool list for the turn's entity seeds (0069 §6 row 3) | `ToolSelectionEngine.fallbackToolsForMessage(tags, scope)` |
| Permission intersection of tag-added tools | **always**: a tag-added facade tool is offered only if it is in the caller's gated set (`findEnabledByPermissionsAndWorkflow`), §2 | same | `ToolSelectionEngine` |
| Admin fast path | fires on keyword/phrase match without veto (today) | fires only when the heuristic matched without veto **and** `admin_account_question` (acting value) is true (§3.4). A model `false` at or above threshold vetoes it | `ToolRegistryService.resolveCandidateSelection(context, topK, tags)` |
| Compound split | today's split | a model `false` at or above threshold skips the split; a model `true` **widens** the splitter: a boundary at a conjunction (and, plus, but, et, y, mais, pero, `;`) is allowed without the English starter-word check. Promotion therefore has value, and fr/es get a compound route. `QuestionTags.none()` → today's split | `RerankedContentRetriever` reads the tags from `RequestScopedUserContext` |
| Router / tier | `NltiRouter.classify(tags)` maps `intent`, `risk`, `complexity`, `domain` → `RouterClassification`; heuristic values are `safeDefault()` so the tier is `T2_COMPLEX`, as the dormant router yields today. The chat-model call is removed in Wave 2 (§7) | model values where each tag is listed; a tag below threshold takes `safeDefault()`'s value for that field (§3.5: a low-confidence `risk` is `HIGH`). `TierSelector` unchanged | `NltiRouter` |
| Scope graph seeds | the heuristic yields no `domain` (always `master`) and no entity, so no tag seeds | `domain` (not `master`) and every `entity.<key>` answer at or above threshold are passed to `ScopeResolver.resolve(message, codes, state, tagSeeds)`; they seed with match kind `TAG`, confidence `LOW` (0069 §5.4 reserves `HIGH` for identifier and exact-term seeds) | `ToolSelectionEngine.resolveScope` |
| Scope graph option lists | — | the `domain` options are the RAG-scope vocabulary (§2.3); the entity Nouls come from the current graph snapshot's lexicon entities (0069 §6 row 5) | `TaggingQuestions` |

Workflow state, in `enforce`:

- `IDLE` is a value: a model `IDLE` at or above threshold overrides a phrase-matched `CREATING_PO`.
- Non-`IDLE` states are strict subsets of `IDLE` (`IDLE` offers 18 facades; `CREATING_PO` 6, `RECEIVING_ASN` 7,
  `INVENTORY_RECON` 7, `PROCESSING_RETURN` 9), so this is the one tag that removes tools. A non-`IDLE` answer must meet
  `thresholds.workflow_state.non-idle`, it is promoted last of the tool-affecting tags, and the bake-off reports the
  false-non-`IDLE` rate separately.
- The lexicon lookup fires only when the acting `intent` is `ACTION` ("how many POs are open?" must not select `CREATING_PO`).
- `PROCESSING_RETURN` has no heuristic source: it is model-only and needs its own promotion evidence.

Rules that hold in every mode (§3): permission gating first and unchanged; tags add permitted tools and never remove a ranked
one; persisted workflow state wins; the fast path is never fired by a tag alone; risk never downgrades; only typed values are
read from the response and none of them is ever placed in a prompt, a log template or a tool argument.

### 2.7 Scope-graph changes (ADR-0069 side)

- `ScopeResolver.resolve(message, codes, workflowState, tagSeeds)`: `tagSeeds` is a domain-level record of entity keys and an
  optional domain. An entity seed becomes a seed node with `MatchKind.TAG` (LOW); a domain seed adds the `Domain` node and, as
  its one hop, the `RagDoc`s whose `rag_scope` is that domain's scope (a `Domain` is still never expanded to its tools, 0069
  §2.8). Existing callers keep the three-argument form. The `domain` option is a RAG scope, mapped to its `Domain` node through
  the inverse of `domain_scopes`; that map is extended with `security-service: security` (identity rows are implicit).
- `mcp.scope-graph.enforce` gains the value `lookups` (0069 §6 row 3): entity → facade tools through the lexicon's
  `facade_tools`, entity → workflow state through a new optional lexicon field `workflow_state` (`purchase-order:
  CREATING_PO`, `asn: RECEIVING_ASN`; `INVENTORY_RECON` has no entity and stays heuristic-only). The field stays, but it is read
  only through the heuristic tagger under the ACTION-gated rule of §2.6. The loader validates the value against `WorkflowState`.
- `entities.yaml` gains a `domains:` block: one criteria sentence per RAG scope (§2.3), validated against both profile lists.
- The graph exposes `entityOptions()` (key, en/fr/es terms) for the entity Nouls of §2.4; the list is deterministic and tested.

### 2.8 Recording

`TagTrace` (a new nullable component on `EvalTurnTrace`, beside `scope`; older payloads read it as null): `mode`, `enforcedTags`,
`providerModel`, `latencyMs`, `fallbackReason`, `stateTruncated`, `optionListHash` (the domain options and entity keys asked),
`questionCount`, `requestBodyBytes`, `heuristicRule` where the heuristic exposes a rule id, and per tag `{ name, actingValue,
actingSource, heuristicValue, modelValue, modelConfidence, threshold, rawNoulProbability, agree }`; `threshold` is the threshold in
effect and `rawNoulProbability` the Noul's p before it became a value and confidence. Values are enum names, booleans or option
labels: never message text.

Agreement with the heuristic is not accuracy. The §6 promotion rule needs ground truth (`expected_tags` per gate utterance,
§2.9), and the report scores both taggers against it. Tool-selection accuracy is computed by joining `TagTrace` with
`ScopeTrace.calledToolsInScope` and the selection fields.

`NltiRequestTelemetry` goes to `schemaVersion` **3** (ADR-0069 took 2) with a nullable `tagging` block: `mode`, `providerModel`,
`latencyMs`, `fallbackReason`, `agreementRate` (share of tags where the two taggers agree, when both ran), and the acting
`intent`, `risk`, `complexity`, `domain`, `workflowState`, `simpleChat` values. The existing `Routing` block keeps its shape and
is now filled from the tags (§7), so its dormant fields carry values again. Any consumer that pins version 2 (the one docstring
and one dashboard description found for ADR-0069) is updated in the same change.

Metrics (Micrometer): `mcp.tagging.latency` (timer, tag `model`), `mcp.tagging.requests{model,outcome=ok|timeout|error|rate_limited|malformed}`,
`mcp.tagging.fallback{reason}` (per turn, including `low_confidence`), `mcp.tagging.agreement{tag,agree}` (shadow and enforce),
`mcp.tagging.state_truncated`. Nothing is registered when `mode` is `off`.

### 2.9 The bake-off report (§6)

The bake-off is run by operators, in `shadow`, once per candidate model, against the gate question sets in each of en, fr and
es. What Wave 3 provides so it can be run and recorded:

- **Gate sets.** New fixtures `pos-mcp-server/src/test/resources/eval/tagging-gate/{en,fr-CA,es}.json`, each utterance carrying
  `language` and `expected_tags` (every tag; `entity` as a set). Existing sets are reused: `eval/tool-selection/generated.json`
  (101 utterances: `about_*`, `workflow_state`, admin negatives), `eval/analytics-gate/QUESTIONS.json` (26:
  `implies_date_window`, `complexity`), `eval/analytics-gate/MULTI_TURN.json` (10: `follows_previous_turn`),
  `eval/write-safety/*.json` (38: `intent` ACTION, `risk`), `eval/gap-harness/questions.json` (23) and `SimpleChatRuleDefaults`
  (fr/es).
- **Minimum per language.** Each Noul: at least 15 positive and 15 negative, including at least 5 hard negatives the heuristic
  gets wrong today. Choice and Score: at least 5 per option, except `workflow_state` 25 (at least 10 `IDLE` with PO, ASN and
  return vocabulary), `intent` 15, `complexity` 10, `risk` 15, `domain` 75. Entities: at least 3 each, plus at least 10
  two-entity questions within one domain. The fr-CA and es sets are reviewed by a native reader, not machine-only.
- **Promotion evidence.** Both taggers' accuracy against `expected_tags`, per tag per language, plus the false-non-`IDLE` rate
  and the model-true/heuristic-false rate for the asymmetric tags.

- `scripts/tagging_shadow_report.py`: reads eval turn traces (the existing export / query endpoint) for a time window and
  prints, per tag, the accuracy of both taggers against `expected_tags` when a gate fixture is given, the agreement rate with the
  heuristic, the model's confidence distribution, the fallback rate by reason, and
  the tagging latency p50 / p95, plus the provider model in use. Per language when the trace's message language is known
  (the gate sets are language-tagged); otherwise overall.
- A `docs`/README section describing the procedure: pull the model, set `mcp.tagging.provider.model`, run the gate sets in
  `shadow`, run the report, record the result in the ADR Changelog, repeat for the next model.

The thresholds and the model are then set by configuration, not by code.

## 3. Curated inputs

- `TaggingQuestions` (code): instructions and criteria descriptions per tag. Reviewed like code. The descriptions of
  `workflow_state` options are the phrases `deriveWorkflowState` matches today, so the two taggers describe the same thing.
- `entities.yaml`: the optional `workflow_state` field and the `domains:` criteria block (§2.7). No other lexicon change.

## 4. Testing

Beyond the ADR's list (Implementation Notes → Testing):

- **Behaviour preservation**: for a fixture of messages covering every heuristic (simple chat en/fr/es, continuation cues,
  PO / ASN / recon phrases, each keyword guard, date-window shapes including multi-line, admin keywords with and without veto
  terms, compound questions), the tagged path in `off` makes the same decision as the pre-refactor code. Written **before** the
  refactor by capturing today's outputs into the fixture, so the move behind the seam is verified, not assumed.
- `shadow` == `off` for every decision, with a stubbed provider that returns the opposite of the heuristic for every tag.
- `enforce` with `enforced-tags` empty == `shadow`; each tag alone; below-threshold answers fall back per tag; the
  `follows_previous_turn` override; the admin veto in both directions (a model `true` never fires the path when no keyword
  matched); the workflow precedence chain with a persisted state, a tag, a lookup and a phrase; the compound gate; the router
  mapping incl. `safeDefault()` per field.
- `JevClient` against a stubbed server: happy path for all three primitives, timeout, 429, 529, 5xx, `{"error": …}` with 200,
  missing answer, unknown label, out-of-range probability, malformed JSON → each a provider failure with the right reason;
  request body contains only `model`, `state`, `keep_alive`, `questions` (§4); every question carries `instructions`; the
  option cap (26) and question cap (64) hold for the largest graph the lexicon can produce; the `state` cut at
  `max-state-chars`.
- Log capture: no message text in any log line on any failure path (§4), including the truncated Ollama `error` text.
- Transport parity: both managers tag once, before `isSimpleChat` and `routeTier`, publish and clear at the same points;
  warm-up never tags.
- Scope integration: tag seeds are `LOW`; a domain seed admits that scope's documents and no tools; `lookups` replaces the
  inventory/order guards only when enforced; lexicon `workflow_state` validated.
- Telemetry schema 3 serialization and old trace payload compatibility.
- The `domain` cap with the real `mcp.rag.preload.docs` lists: options within 26, a criteria sentence per scope in both profiles.
- `QuestionTags.none()` == `off` for every consumer.
- The `:veto` direction: a model `true` never acts over a heuristic `false`; a model `false` at or above threshold does over a
  heuristic `true`.
- The non-`IDLE` threshold: a non-`IDLE` answer between `thresholds.workflow_state` and `.non-idle` is not acted on.
- The widened splitter: a model `true` splits at a conjunction without the English starter-word check (fr and es examples); a
  model `false` or `none()` gives today's split.
- The T0 skip: an exact `SimpleChatRuleCatalog` hit makes no provider call.

## 5. Risks

- **CPU latency on the alpha host** is unmeasured; a model that misses the 800 ms budget makes every turn a `timeout` fallback
  (correct behaviour, but 800 ms of added latency per turn). The bake-off measures it before any promotion; the timer and the
  fallback counter show it immediately in `shadow`.
- **A wider request than the ADR assumed.** One Noul per entity makes a 44 to 46-question request. CPU latency on alpha decides
  whether Noul-per-entity survives; the fallback is conflict-aware grouping (§2.4).
- **Two taggers to keep in step.** A new tag must be answered by both. The behaviour-preservation fixture and a test that every
  tag definition has a heuristic (or is declared heuristic-less, like `entity.<key>`) guard it.
- **Option-list drift.** The entity Nouls change when the lexicon changes, so shadow agreement for `entity` is comparable only
  within one `optionListHash`; the trace carries it.
- **Telemetry schema 3** lands shortly after schema 2; the dashboards match `{"schemaVersion"` without a value, so nothing
  breaks, but the two version bumps should be noted in the release notes together.
