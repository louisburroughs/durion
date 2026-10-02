---
type: Gate Run Record
title: ADR-0068 tagging bake-off — hosted jev-latest on alpha — 2026-10-01
domain: general
status: historical
tags: [mcp-server, general, question-tagging]
---

The ADR-0068 §6 bake-off: both taggers scored against the ground-truth gate sets
(`pos-mcp-server/src/test/resources/eval/tagging-gate/{en,fr-CA,es}.json`) on the alpha cell, in `shadow`, with the
report `scripts/tagging_shadow_report.py --expected`. Backend at `3999cd40d` (waves 1–3 of #2366 merged).

## Local candidates: not viable on this host

The GPU-less `t3.2xlarge` (8 burstable vCPU, 32 GiB) that runs the alpha cell:

- `tev1:0.8b`: 325 of 325 calls timed out at the 800 ms budget. Cause: a model that is not resident is loaded by the first
  request, the client abandons it at 800 ms, Ollama drops it, repeat (louisburroughs/durion-positivity-backend#2376).
  Warmed by hand, one short question answered in 62 ms, but the 13-question request is 2,107 tokens against a 2,050-token
  context (`400 prompt 0 has 2107 tokens; expected 1–2050`), and a 7-question request took 5.2 s warm (Ollama evaluates
  each question as its own prompt; `input_tokens: 5824`).
- `tev1` (4B) has the same context; `nimble` (9B, 8k context) fits but is 11× the weights on the same CPU.

No local candidate meets the budget on this host, which is the §5 fork: raise the budget, an accelerator host, or keep
the heuristics. The hosted provider was chosen for alpha under §4's synthetic-data allowance.

## Hosted jev-latest (TypeSafe System One, `https://api.typesafe.ai`)

`MCP_TAGGING_ENTITY_QUESTIONS=true`, `MCP_SCOPE_GRAPH_MODE=shadow`, 44 questions per request (~21 KB), `keep_alive` omitted.
Run 21:54Z–01:28Z; en batch 3 and fr-CA batch 1 were redone after a config-sync restart at 23:05Z dropped the scope graph
and the rate limit (the first fr-CA traces were taken with 13 questions and discarded).

| Language | Utterances joined | Tagging p50 / p95 | Provider fallbacks | Fixture reviewed |
| --- | --- | --- | --- | --- |
| en | 330 / 330 | 169 / 206 ms | 0 | yes |
| fr-CA | 357 / 357 | 164 / 217 ms | 0 | no (translation) |
| es | 357 / 357 | 163 / 231 ms | 0 | no (translation) |

Accuracy against `expected_tags` — heuristic → model-plus-fallback at the chosen threshold (en / fr-CA / es):

| Tag | Threshold | en | fr-CA | es |
| --- | --- | --- | --- | --- |
| `simple_chat` (`:veto`) | 0.80 | 83.6 → 98.5 | 78.2 → 98.3 | 80.1 → 98.3 |
| `admin_account_question` (veto) | 0.60 | 95.2 → 96.1 | 93.6 → 95.5 | 91.3 → 95.8 |
| `workflow_state` (non-IDLE 0.80) | 0.70 | 89.4 → 99.4 | 89.6 → 98.6 | 89.6 → 98.9 |
| `about_inventory` | 0.75 | 86.1 → 97.9 | 88.2 → 97.8 | 84.0 → 98.6 |
| `about_orders` | 0.75 | 91.5 → 93.3 | 89.1 → 97.5 | 88.8 → 97.8 |
| `needs_web_search` | 0.85 | 92.4 → 98.8 | 93.3 → 98.6 | 93.3 → 99.2 |
| `implies_date_window` | 0.70 | 84.2 → 93.9 | 74.8 → 92.4 | 73.9 → 92.7 |
| `follows_previous_turn` | 0.70 | 90.9 → 96.7 | 89.6 → 96.4 | 91.0 → 96.1 |
| `compound_question` | 0.75 | 97.6 → 98.8 | 95.0 → 98.6 | 95.0 → 99.7 |
| `intent` | 0.50 | 7.3 → 94.2 | 6.7 → 92.7 | 6.7 → 93.0 |
| `domain` | 0.50 | 18.8 → 85.5 | 17.9 → 81.8 | 18.2 → 82.4 |
| `complexity` | 0.50 | 9.7 → 78.8 | 9.0 → 81.5 | 9.0 → 81.8 |
| `risk` | 0.50 | 13.3 → 82.4 | 12.3 → 83.2 | 12.3 → 84.0 |
| `entity` (set, seeds) | 0.85 | P 76.6 / R 92.0 | P 75.6 / R 89.5 | P 81.2 / R 90.2 |

Asymmetric checks: `simple_chat` model-true/heuristic-false precision 100 % at ≥ 0.80 in all three languages (80–86 %
below); `admin_account_question` model-true/heuristic-false precision 100 % everywhere; `workflow_state` false-non-IDLE
3.4 / 1.9 / 1.9 % (heuristic) → 0.7 / 0.9 / 0.9 % (model). The four router tags have no heuristic (`safeDefault()`), so
their baseline is the constant's hit rate; their confidence is spread across options, which is why they want 0.50.

Side findings during the run: the primary chat model `deepseek-v4-flash:0731` had been retired by ollama.com since
2026-09-25 and every turn had been failing over unnoticed (louisburroughs/durion-positivity-backend#2375); the es batches
also hit ollama.com's session usage limit (44 chat turns returned 500; tags unaffected). The per-actor chat rate limit of
100 broke the first batch (now `POS_NLTI_RATE_LIMIT_PER_SESSION`, #2377).

## Decision

All thirteen tags meet the §6 rule (model-plus-fallback at least as accurate as the heuristic in en, fr-CA and es) with
the caveat that fr-CA and es are unreviewed translations. The Platform Owner promoted all thirteen at once on 2026-10-02
(`MCP_TAGGING_MODE=enforce`, `MCP_TAGGING_ENFORCED_TAGS=simple_chat:veto,…`), thresholds as above
(louisburroughs/durion-positivity-backend#2378 makes them configurable; until it deploys every tag acts at 0.75, which the
table also supports). Model: `jev-latest`, hosted. Evidence on the alpha host: `~/durion-eval/bakeoff-2026-10-01/`
(trace exports, `bakeoff-report-jev-latest-{en,fr-CA,es}.json`, runner log).
