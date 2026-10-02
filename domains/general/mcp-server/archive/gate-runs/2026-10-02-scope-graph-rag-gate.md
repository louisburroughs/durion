---
type: Gate Run Record
title: ADR-0069 scope graph rag consumer gate on alpha — 2026-10-02
domain: general
status: historical
tags: [mcp-server, general, scope-graph]
---

The ADR-0069 §9 gate for the `rag` consumer: the `rag-lexical` and `rag-retrieval` fixtures (84 questions) asked through
chat on the alpha cell once with `MCP_SCOPE_GRAPH_MODE=shadow` and once with `enforce` / `MCP_SCOPE_GRAPH_ENFORCE=rag`,
scored by `scripts/scope_graph_gate_report.py` from the eval turn traces (louisburroughs/durion-positivity-backend#2386,
#2406). Runner: `scripts/gate_chat_run.sh` (#2380).

## Setup

- Backend image `sha-f123179` for both runs; one graph snapshot (`graphHash 19cf1a61886452c1`) across all 121 scored turns.
- Tagging as promoted on 2026-10-02 (ADR-0068): `enforce`, all thirteen tags, hosted `jev-latest`.
- Shadow run 16:39–16:52Z, enforce run 16:57–17:11Z; 84 turns each, no failed turn.
- Actor: `admin.alpha` (`ROLE_SYSTEM_ADMINISTRATOR`) as a proxy for every fixture actor (`--actor-proxy`). Alpha has no
  user per fixture role that can both chat (`mcp:chat:execute`) and export its traces (`mcp:eval_trace:view`, `ADMIN`
  only), and three fixture roles (`ROLE_ADMIN`, `ROLE_USER`, `ROLE_ACCOUNTING_ASSOCIATE`) do not exist there. The Platform
  Owner chose proxy evidence over an RBAC change. Proxy evidence measures whether the filter drops expected documents. It
  does not test per-role visibility, and forbidden lists are ignored on proxied samples, so the forbidden-document
  criterion was **not exercised** by this run. The caller filter's SQL-parity test is the evidence for visibility.

## Result: PASS

61 fixtures paired (shadow and enforce turn both joined). 23 need no trace: 19 whose question took the simple-chat path
(no scope, no retrieval, so the rag consumer cannot act on them; see Findings) and 4 visibility-only fixtures the proxy
cannot test.

| Slice | Samples | hit@5 shadow → enforce | MRR shadow → enforce | recall@5 shadow → enforce |
| --- | --- | --- | --- | --- |
| overall | 61 | 0.770 → 0.836 | 0.659 → 0.708 | 0.746 → 0.811 |
| rag-lexical | 22 | 0.955 → 1.000 | 0.833 → 0.856 | 0.955 → 1.000 |
| rag-retrieval | 39 | 0.667 → 0.744 | 0.560 → 0.624 | 0.628 → 0.705 |
| confidence HIGH | 40 | 0.800 → 0.900 | 0.729 → 0.804 | 0.763 → 0.863 |
| confidence LOW | 18 | 0.667 → 0.667 | 0.500 → 0.500 | 0.667 → 0.667 |
| confidence NONE | 3 | 1.000 → 1.000 | 0.667 → 0.667 | 1.000 → 1.000 |

- No fixture regressed: no expected document of the shadow top-5 was missing from the enforce top-5, and no MRR fell.
  The gain is all in HIGH-confidence turns, where the filter removed out-of-scope documents and in-scope ones moved up;
  LOW and NONE pass through unchanged, as §6 specifies.
- Tool selection: unchanged in 59 of 61 pairs. The other two changed because an acting ADR-0068 tag differed between the
  two turns of the question, with scope confidence the same in both. The report lists these as `tagDrift` and leaves them
  out of the tool criterion, because the rag consumer has no path into tool selection:
  - `rag-lexical-inventory-codes-receiving-transfer`: `workflow_state` (and `entity_employee`) near threshold; the
    enforce turn acted on `RECEIVING_ASN` and offered 7 facades instead of 18.
  - `rag-security-role-permission-matrix-pos-3`: `admin_account_question` near threshold; the enforce turn fell back to
    the heuristic and offered 3 facades instead of 18.
- The simulated preview (offline replay of §6 over the shadow top-5) also passed: 0 expected documents dropped, 26 of 187
  retrieved documents removed.

## Findings

- **Documentation questions answered without RAG.** 17 turns (15 distinct questions, e.g. "how do orders work", "what
  reports are available", "what does WO mean") took the simple-chat path, which offers no tools and retrieves nothing
  (`SimpleChatFastPath.prompt`). In 11 of the 15 the heuristic said `simple_chat=true` and jev said `false` but at
  confidence 0.54–0.79, below the 0.80 veto threshold, so the heuristic stood; in 1 jev gave no answer; in 3 ("what can
  the assistant do" and two like it) jev said `true` at 0.87 or higher. The behaviour is the heuristic's and predates
  ADR-0068.
- **Tag drift between runs.** jev confidences near a threshold vary between two asks of the same question, which is
  enough to flip an acting tag and with it the tool selection. Not a gate concern for `rag`; it will be for the `tools`
  consumer, whose gate compares tool selection directly.
- Documentation coverage from the same run is consistent with louisburroughs/durion-positivity-backend#2381–#2385:
  among others, `appointment`, `bank-reconciliation`, `campaign`, `credit-memo`, `financial-report`, `gl-account`,
  `stock-transfer`, `supplier` and `user` drew no scoped turn in these fixtures.

## Decision

The Platform Owner promoted the `rag` consumer on alpha on 2026-10-02 (`MCP_SCOPE_GRAPH_MODE=enforce`,
`MCP_SCOPE_GRAPH_ENFORCE=rag`, in `/opt/durion/alpha/.env`). The `tools` and `card` consumers stay in shadow until their
own gate runs. Evidence on the alpha host: `~/durion-eval/scope-gate-2026-10-02/` (trace exports `shadow-full/`,
`enforce-full/`, `report-gate.txt`, `report-gate.json`).
