---
type: ADR
title: 'ADR-0068: Pre-LLM Question Tagging with a Decision Model (TypeSafe Jev) in pos-mcp-server'
description: pos-mcp-server classifies each chat turn with scattered keyword/regex heuristics and a dormant LLM router; this ADR puts one typed tagging seam in front of the LLM, backed by the TypeSafe Jev decision model with the heuristics as permanent fallback.
status: draft
adr_status: pending
created: '2026-09-30'
related: [ADR-0009, ADR-0026, ADR-0046, ADR-0062]
tags: [adr]
---
# ADR-0068: Pre-LLM Question Tagging with a Decision Model (TypeSafe Jev) in pos-mcp-server

**Status:** PROPOSED **Date:** 2026-09-30 **Deciders:** Architecture, NLTI (Natural Language Task Interpretation) Domain, Security & Authorization Domain
**Affected Issues:** — (none yet; to be opened for implementation)

---

## Context

### Current state

Before a chat turn reaches the executor LLM (`deepseek-v4-flash:0731` on Ollama), `pos-mcp-server` makes several decisions
about the question. Each is made by its own hand-written heuristic, in its own class, with no confidence and no shared result:

| Decision | Where | How it is made today |
| -------- | ----- | -------------------- |
| Is this a T0 simple-chat message, or does it lean on a previous turn? | `SimpleChatClassifier` (`CONTINUATION_CUES`, token/length caps) | en/fr/es word list (#1836) |
| Workflow state for session-less callers | `ToolSelectionEngine.deriveWorkflowState` | Substring match over PO / ASN / recon phrases; everything else `IDLE` |
| Extra facade tools on top of the semantic top-K | `ToolSelectionEngine.fallbackToolsForMessage` | Keyword guards (web search, inventory, order) |
| Does the question imply a date window? | `ToolSelectionEngine.mentionsDateWindow` | Phrase list + precompiled regexes (#1684) |
| Admin fast path (returns `AdminFacadeTool` **alone**) | `ToolRegistryService.resolveCandidateTools` | `ADMIN_QUERY_PHRASES` + `FAST_PATH_VETO_TERMS` |
| Is this a compound (multi-need) question? | RAG rerank (`CompoundRerankProperties`, #1180) | Sub-query split heuristic |
| Intent / risk / complexity / domain → model tier | `NltiRouter` (Gate 4, #1192) | `qwen3:4b` asked for strict JSON; **dormant** since #1683 (`MCP_MODEL_TIERING_ENABLED=false`) |

### The problem

- **Brittle and English-shaped.** Every guard is a vocabulary list. Each gate run adds words after a miss (#1684, #1688,
  #1836); a phrasing outside the list silently falls through. fr/es coverage is partial and maintained by hand.
- **No confidence.** A heuristic either fires or does not. Nothing distinguishes a sure match from a coincidental one, so
  the code cannot escalate an uncertain case differently from a certain one. The admin fast path is the sharpest instance:
  a false positive suppresses every other tool for the whole request, which is why it carries a hand-built veto list.
- **The LLM router did not pay.** The Gate 4 T1 router added a generative classification call per turn, parsed free text
  back into JSON (any malformed reply falls to the safe default), and was switched off because its cost bought nothing.
- **No shared result.** Each decision re-reads the raw message; there is no single per-turn record of *what kind of question
  this is* for telemetry, eval traces, or downstream consumers.

### Drivers

- A class of model now exists for exactly this job. TypeSafe **Jev** (early access since 2026-09-15) is a non-generative
  "System One" decision model: it takes text plus typed questions and returns typed answers with calibrated probabilities,
  never prose. Three primitives: **Noul** (yes/no probability), **Choice** (one of up to 255 labelled options, with a
  per-option distribution and confidence), **Score** (a 2–10 level ordinal rubric). All questions in one request are answered
  in one parallel pass, reported median ~275–310 ms, at $0.042 per million input tokens with output free.
- There is nothing to parse and no generated text to hallucinate, which removes the router's failure mode.
- The platform is pre-production (CLAUDE.md): the heuristics can be replaced cleanly rather than shimmed.

### Constraints

- **Jev is closed-weight and hosted only.** There is no self-hosted build. TypeSafe states it does not train on customer
  requests; zero data retention (ZDR) is offered to enterprise customers only, and operational telemetry (logs, hashes,
  classifications, metrics) may be processed. Every tagged message would leave the platform.
- User messages carry tenant business data (customer names, invoice totals, vehicle details). ADR-0062 §11 makes
  conversations and cached LLM context tenant-scoped rows; nothing yet governs sending message text to a new third party.
- Permission gating is the security boundary for tool access (gateway `X-Authorities` → `@PreAuthorize`, and
  `findEnabledByPermissionsAndWorkflow` before any ranking). No classifier may widen or bypass it.
- The chat surface is multilingual (en, fr-CA, es). Jev's non-English accuracy is not yet published.
- Rate-limit responses (429, 529 Overloaded) are documented as expected, not exceptional.

### Scope

`pos-mcp-server` only: the blocking and streaming chat paths (`SessionAgentManager`, `StreamingSessionAgentManager`) and
the selection, simple-chat, rerank and routing components they drive. No REST contract, event, permission, or schema changes.

---

## Decision

### 1. One tagging seam per chat turn

**Decision:** ✅ **Resolved** - Introduce a `QuestionTagger` interface in `com.positivity.mcp.internal` (ADR-0026: internal,
never a grant surface) that returns an immutable `QuestionTags` record: one value **and** one confidence per tag, plus the
`source` that produced it (`JEV` or `HEURISTIC`). Both session managers call it **once per turn**, before the simple-chat
check, and pass the record to every consumer in the table above. No consumer re-derives a tag from the raw message.

The initial tag set, all sent in a single Jev request:

| Tag | Primitive | Replaces |
| --- | --------- | -------- |
| `follows_previous_turn` | Noul | `CONTINUATION_CUES` |
| `simple_chat` | Noul | T0 rule catalog match (catalog remains the reply source) |
| `workflow_state` | Choice over `WorkflowState` | `deriveWorkflowState` |
| `needs_web_search`, `about_inventory`, `about_orders` | Noul each | `fallbackToolsForMessage` keyword guards |
| `implies_date_window` | Noul | `mentionsDateWindow` phrase/regex lists |
| `admin_account_question` | Noul | `ADMIN_QUERY_PHRASES` / `FAST_PATH_VETO_TERMS` (see §3) |
| `compound_question` | Noul | #1180 split detection (the split itself is unchanged) |
| `intent` (QUERY / ACTION / UNKNOWN), `domain`, `complexity` | Choice each | `NltiRouter` JSON output |
| `risk` (LOW / MEDIUM / HIGH) | Score | `NltiRouter` JSON output |

Adding a tag is a code change reviewed like any other; tags are not configurable at runtime.

### 2. Jev behind the seam; heuristics as the permanent fallback

**Decision:** ✅ **Resolved** - Two implementations:

- `JevQuestionTagger` — calls the Jev System One API.
- `HeuristicQuestionTagger` — today's rules, moved behind the interface unchanged. It is the **test-profile**
  implementation, the **fallback** on any Jev failure, and the per-tag fallback when a Jev answer's confidence is below that
  tag's threshold.

Any Jev error, timeout, 429/529, or malformed response yields the heuristic result for the whole turn. A chat turn never
fails because tagging failed.

### 3. Tags are advisory and can never widen access

**Decision:** ✅ **Resolved** - Tags influence *which* permitted tools and model the turn uses; they never decide *whether*
the caller may use them. Binding rules:

1. **Permission gating runs first and is untouched.** Tags act only on the set that survived
   `findEnabledByPermissionsAndWorkflow` and role scoping.
2. **Additive only for tools.** A tag may add a permitted facade tool on top of the semantic top-K; it may not remove or
   displace a semantically ranked tool.
3. **Persisted state wins.** `workflow_state` applies only to session-less callers; a persisted `NltiSession` state is never
   overridden.
4. **The admin fast path may be vetoed by a tag, never fired by one alone.** Because the path returns `AdminFacadeTool`
   alone, a Jev false positive would suppress every other candidate. The fast path fires only when the heuristic phrase
   match fires **and** `admin_account_question` is at or above threshold; a low Jev probability vetoes it.
5. **Risk never downgrades.** When tiering is enabled, `risk = HIGH`, `intent = ACTION`, or a risky domain (accounting, tax,
   admin, security) always selects T2-complex, whatever the confidence (Gate 4 drift guard, `TierSelector`). A low-confidence
   `risk` answer is treated as HIGH.
6. **Tags are typed values, not text.** Only enum/boolean/number values from the response are read; no response string is
   ever placed into a prompt, a log message template, or a tool argument.

### 4. Data minimisation and the egress condition

**Decision:** ✅ **Resolved** - The Jev request carries **only the current user message text** and the fixed question
definitions. It never carries conversation history, RAG context, tool results, the system/persona prompt, the username,
user id, tenant id, or any forwarded header. The only credential is the TypeSafe API key.

Enabling the hosted Jev provider in an environment that holds **real customer data** requires, first, an executed data
processing agreement with TypeSafe that includes zero data retention, recorded in this ADR's Changelog. Until then the
hosted provider may be enabled only in `dev` and in environments populated with synthetic data. `pos-mcp-server` does not
log the message text on a tagging failure (ADR-0046); the failure log carries the error class, HTTP status, and latency.

### 5. The Jev HTTP API is the contract, not the vendor

**Decision:** ✅ **Resolved** - `JevQuestionTagger` targets the System One wire contract through a thin client in
`internal/client` with a configurable base URL, so any service implementing that contract can be substituted without code
change. This keeps a self-hosted path open (for example the open-source `logan-markewich/jeff`, GLiFormer-based, which
implements the same API) should §4's condition not be met or should cost or latency require it.

The community Spring AI starter (`org.springaicommunity:spring-ai-starter-typesafe:0.1.0`) is **not** adopted: it is
pre-1.0, community-maintained, and its compatibility with the platform's Spring AI 2.0.1 is unverified. Revisit when it
reaches 1.0.

The client is a plain (not `@LoadBalanced`) `RestClient`: a connect+read timeout of 800 ms by default, **no retries** on the
chat path, and no circuit state beyond "fail to heuristic".

### 6. Rollout: off → shadow → enforce, per tag, by measurement

**Decision:** ✅ **Resolved** - `mcp.tagging.mode` is `off` | `shadow` | `enforce` (default `off`).

- **shadow** — both taggers run; consumers act on the heuristic result; the Jev result, confidences, agreement, and latency
  are recorded on the eval turn trace and `nlti.request.telemetry`.
- **enforce** — consumers act on the Jev result, per tag, where confidence ≥ that tag's threshold
  (`mcp.tagging.thresholds.<tag>`); below it, the heuristic value is used.

A tag is promoted to `enforce` only after a recorded gate run shows it at least as accurate as its heuristic on the gate
question sets **in each of en, fr-CA and es**. The promotion and its evidence are recorded in this ADR's Changelog.

### 7. The Gate 4 LLM router call is retired

**Decision:** ✅ **Resolved** - `NltiRouter` stops calling a chat model. It maps the `intent`, `risk`, `complexity` and
`domain` tags to a `RouterClassification` and keeps `TierSelector` unchanged. The `routerChatModel` bean and
`mcp.model.router` / `MCP_MODEL_ROUTER` are removed once the router tags reach `enforce` (pre-production policy: no dead
configuration kept). Tier routing itself (`mcp.model.tiering-enabled`, `simple`, `complex`) is unaffected and stays dormant
until a real T2-simple model is chosen.

### Out of scope

- Using Jev as a guardrail, reranker, tool index, or evaluator (the Spring AI integration offers these); each would need
  its own decision.
- Per-tenant opt-out of external tagging (see Negative consequences).
- Replacing semantic tool ranking; tags sit beside it, not in it.

---

## Alternatives Considered

1. **Keep and extend the heuristics.** Zero egress and zero latency, but it is the status quo that keeps missing phrasings,
   has no confidence, and costs a word-list change per gate miss in three languages. Retained as the fallback (§2), rejected
   as the only mechanism.
2. **Re-enable the Gate 4 LLM router (`qwen3:4b`) and widen its JSON.** Stays on Ollama, but it is the design already
   switched off (#1683): a generative call per turn, free-text JSON parsing with a safe-default failure mode, and no
   calibrated confidence.
3. **Embedding-similarity classifier over labelled exemplars (bge-m3, already deployed).** No new vendor and no egress, but
   needs a curated exemplar set per tag per language, produces similarities rather than calibrated probabilities, and
   duplicates what the semantic tool ranking already does. A reasonable fallback if §4 cannot be satisfied and the
   self-hosted Jev-compatible route (§5) underperforms.
4. **Self-hosted Jev-compatible model (`jeff`, GLiFormer ~400M) as the primary.** Removes egress entirely, but is a
   community project with no calibration evidence and adds a model runtime to operate. Kept reachable by §5 and to be
   benchmarked in shadow mode alongside hosted Jev; not chosen as the primary today.
5. **Let the executor LLM tag the question inside its own turn.** No extra call, but the tags are needed *before* the call
   (tool set, tier, simple-chat), so this cannot work for the decisions that matter.

---

## Consequences

### Positive ✅

- ✅ One typed, per-turn `QuestionTags` record replaces six scattered heuristics and one dormant router; every decision and
  its confidence appears in telemetry and eval traces.
- ✅ Calibrated confidence makes uncertain cases distinguishable: consumers fall back per tag instead of all-or-nothing.
- ✅ Phrasings outside a word list are no longer silently missed, and the fr/es vocabulary lists stop being maintained by hand.
- ✅ Cheaper and faster than the router it retires: one non-generative call at ~$0.00004 per turn in place of a `qwen3:4b`
  generation, with nothing to parse.
- ✅ Safety posture is unchanged by construction: permission gating first, additive tools, no risk downgrade, heuristic on
  any failure.

### Negative ⚠️

- ⚠️ **Every tagged message leaves the platform** to a hosted, closed-weight third party. Mitigated by §4 (message text
  only, DPA with ZDR before real data, no text in failure logs) and by §5 keeping a self-hosted path open.
- ⚠️ **New runtime dependency on an early-access vendor.** Outages, 429/529, and API changes are expected. Mitigated by
  fail-to-heuristic on every error, a tight timeout, and the heuristic tagger remaining the tested baseline.
- ⚠️ **Added per-turn latency** (~300 ms median, up to ~500 ms) on paths that are rule-only today, including the T0 fast path.
  Mitigated by the 800 ms cap; a follow-up may skip the call when the heuristic is certain (e.g. an exact T0 catalog hit).
- ⚠️ **Multilingual accuracy is unproven.** Mitigated by the per-language promotion rule in §6.
- ⚠️ **No per-tenant opt-out.** A tenant that forbids third-party processing cannot currently be excluded while others are
  tagged externally. Must be decided before the first external tenant is onboarded.
- ⚠️ **Two implementations to keep in step.** The heuristic tagger must keep answering every tag, so a new tag costs both.

### Neutral

- The tag set is code, reviewed in PRs; there is no runtime tag editor.
- Tier routing stays dormant; this ADR changes what feeds it, not whether it runs.

---

## Implementation Notes

- **Components:** `QuestionTagger`, `QuestionTags`, `HeuristicQuestionTagger`, `JevQuestionTagger` (orchestration package);
  `JevClient` (`internal/client`, plain `RestClient`); consumers updated in `SimpleChatFastPath`/`SimpleChatClassifier`,
  `ToolSelectionEngine`, `ToolRegistryService`, the #1180 rerank split, and `NltiRouter`. Both session managers must call the
  tagger at the same point (transport parity is structural, per `tool-selection-architecture.md`).
- **Configuration:** `mcp.tagging.mode` (`off`), `mcp.tagging.provider.base-url`, `mcp.tagging.provider.timeout` (`800ms`),
  `mcp.tagging.thresholds.<tag>`; API key from the environment secret `TYPESAFE_API_KEY`, never committed. `application-test.yml`
  pins `mode: off` so no test context calls out.
- **Testing:** unit tests per consumer against fixed `QuestionTags` (high/low confidence, `HEURISTIC` source); a `JevClient`
  test against a stubbed server covering timeout, 429, 529, and malformed body → heuristic; a test asserting the request body
  contains only the message and question definitions (§4); `ArchitectureTest` unchanged (all new types are `internal`).
- **Rollout:** `off` → `shadow` in dev with synthetic data → §4 condition met → `shadow` on alpha → per-tag `enforce` per §6.
- **Monitoring:** tagging latency histogram, fallback rate by reason (`timeout`, `rate_limited`, `error`, `low_confidence`),
  per-tag shadow agreement rate. Extend the NLTI overview dashboard; alert on sustained fallback rate above 20%.
- **Docs to update on implementation:** `domains/general/mcp-server/architecture.md` (request flow),
  `domains/general/mcp-server/tool-selection-architecture.md` (keyword fallback, admin fast path, workflow state), and the
  `pos-mcp-server` README (configuration and external dependency).

---

## References

- **Related ADRs:** [ADR-0009](0009-backend-domain-responsibilities-guide.adr.md) (pos-mcp-server: "configurable LLM provider
  integration"), [ADR-0026](0026-service-contract-boundary-policy.adr.md) (internal-only placement),
  [ADR-0046](0046-environment-log-level-policy.adr.md) (logging), [ADR-0062](0062-postgres-row-level-multitenancy.adr.md) §11
  (tenant-scoped conversations and LLM context).
- **Design records amended (not ADRs):** `domains/general/mcp-server/archive/gate4-tiered-router-design.md` (T1 router
  replaced by §7); `domains/general/mcp-server/archive/nl-interface-design.md` ("self-hosted Ollama" model strategy — narrowed
  by §4 for the tagging call only).
- **Related Documentation:** `domains/general/mcp-server/architecture.md`, `domains/general/mcp-server/tool-selection-architecture.md`.
- **External Resources:** [Spring AI and TypeSafe Jev](https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment/),
  [TypeSafe Jev project reference](https://gist.github.com/pjburnhill/adf8d28efcad9df037bfdece178ef965),
  [Introducing System One Models & Jev](https://typesafe.ai/blog/introducing-system-one-models-and-jev),
  [Jev data privacy guide](https://jev101.org/guides/jev-privacy-guide),
  [Self-hosting boundaries](https://befailproof.ai/jev/self-hosting/),
  [jeff — self-hosted Jev-compatible API](https://github.com/logan-markewich/jeff).

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | | | |
| NLTI Domain | | | |
| Security & Authorization | | | §4 egress condition |

---

## Timeline

- **Proposed**: 2026-09-30

---

## Changelog

- **2026-09-30**: Initial draft.
