---
name: mcp-sweep
description: Autonomously work through every open pos-mcp-server issue in durion-positivity-backend — triage, fix, validate, open a PR per batch — and keep going until no fixable issue remains. Stops only on genuine blockers, never to ask.
---

# MCP Issue Sweep — continuous resolution until blocked

## Objective

Drain the open `pos-mcp-server` issue queue in `louisburroughs/durion-positivity-backend`
without being fed issues one at a time. The session owns the whole queue: it discovers the
issues, decides what is fixable now, fixes it, proves it, ships it, and moves to the next one.
It stops when every open issue is either **shipped** or **blocked with a recorded reason**.

## Non-negotiables

1. **Never stop to ask.** A question the author could answer later is not a blocker. Pick the
   reading that is most consistent with the issue text and the current code, state the assumption
   in the PR body, and continue.
2. **Never widen scope.** One issue (or one tightly coupled pair) per branch. No drive-by refactors.
3. **Never fake green.** Do not skip, disable, or quarantine a test. If a validation step cannot run
   in this environment (needs Ollama, alpha, Postgres), say so in the PR body under
   *Not verified here* — do not claim it passed.
4. **Never close an issue by hand.** PRs carry `Closes #N`; the merge closes it.
5. **Repo rules still apply**: `AGENTS.md`, `CLAUDE.md`, ADR minimums for backend work
   (ADR-0011, 0013, 0014, 0017, 0018, 0023, 0024, 0026, 0027), `workorder` is one word,
   Palantir formatting via `./mvnw spotless:apply`.
6. **Backend code research** uses `mcp__tokensave-backend__*` (the default `mcp__tokensave__*`
   server does not index backend Java). Pass `seen_node_ids` forward via `exclude_node_ids`.

## Phase 0 — Discover the queue

Run once at the start and again at the top of every loop iteration (issues get filed while you work).

```
search_issues  owner=louisburroughs repo=durion-positivity-backend
               query="pos-mcp-server is:open"        (title prefix)
search_issues  query="mcp-server is:open"            (older prefix, also used)
list_issues    labels=["domain:mcp"] state=OPEN      (label, where applied)
```

Union the three result sets. For each issue read the full body and every comment before
triaging — the comments often carry the actual diagnosis or a "superseded by" note.

Also read `git log --oneline -30 -- pos-mcp-server` and the open PR list: an issue may already
have an unmerged fix on a `claude/fix-*` branch. If it does, that issue is **IN_FLIGHT**, not yours.

## Phase 1 — Triage every issue into exactly one bucket

| Bucket | Meaning | Action |
| --- | --- | --- |
| `FIX_NOW` | Cause is in code/config/tests/docs under `pos-mcp-server`, and it can be proven with something runnable here (unit test, replay fixture, ArchitectureTest, script). | Queue it. |
| `IN_FLIGHT` | An open PR already targets it. | Leave alone. Note the PR number. |
| `BLOCKED_DEPENDS` | The issue text says "only after #X" / "conditional on #Y" and X/Y is still open. | Record the dependency. Re-check each iteration; it becomes `FIX_NOW` the moment the dependency merges. |
| `BLOCKED_ENV` | Needs a live model run, alpha, Ollama, pgvector, or real gate-run data that this container cannot reach, **and** there is no code-level part that can be done first. | Record what environment is needed. |
| `BLOCKED_DECISION` | Two readings lead to materially different code and the issue text does not settle it, **and** picking wrong would be worse than waiting. This bucket should be nearly empty — prefer stating an assumption. | Record the two readings and your recommendation. |
| `ALREADY_FIXED` | `main` already contains the fix (verify by reading the code and running the relevant test). | Comment on the issue with the commit/PR that fixed it and the test that proves it. Do not close. |

Split mixed issues. If an issue is "confirm X on alpha, then re-run the gate" and part of it is
"add the code that would make confirming possible", the code part is `FIX_NOW` and the alpha part
is `BLOCKED_ENV`. Do the code part.

## Phase 2 — Order the FIX_NOW queue

1. Bugs (`bug`, `type:bug`) before enhancements.
2. Issues with no open dependency before those with any.
3. Issues that unblock others (they are named as a dependency by another issue) first.
4. Smaller blast radius first — a single class beats a prompt-architecture change.
5. Older first as the tiebreak.

Write the ordered queue to the scratchpad as `mcp-sweep-ledger.md` and keep it updated. It is the
only state across iterations. Format:

```
| # | title | bucket | branch/PR | validation | note |
```

## Phase 3 — Work one item (the loop body)

Repeat for the head of the queue:

1. **Branch** from fresh `origin/main` of the backend:
   `git fetch origin main && git checkout -B claude/fix-<issue> origin/main`.
   A tightly coupled pair may share a branch (`claude/fix-1705-1706` is the precedent); nothing larger.
2. **Reproduce first.** Write or extend the failing test (unit, `OfflineReplayEvalIT` fixture under
   `src/test/resources/eval/`, `FacadeContractManifestTest`, ArchitectureTest, or a script under
   `pos-mcp-server/scripts/`). If the failure genuinely cannot be reproduced here, this is where the
   issue re-buckets to `BLOCKED_ENV` — record it and move on.
3. **Fix** in `pos-mcp-server` only, following the patterns already in the touched package.
   Prefer code over prompt text when the issue itself says prompt iterations have not held
   (e.g. date-window computation belongs in `DateWindowFacadeTool`, not in a system prompt).
4. **Validate** — all of these, in this order:
   ```bash
   ./mvnw -pl pos-mcp-server spotless:apply
   ./mvnw -pl pos-mcp-server -am -DskipTests compile test-compile
   ./mvnw -pl pos-mcp-server -Dtest=<focused tests> test
   ./mvnw -pl pos-mcp-server -Dtest=ArchitectureTest test
   ./mvnw -pl pos-mcp-server test                       # full module, once per branch
   ./mvnw -pl pos-archunit -am -Dtest=ArchitectureTests test   # only if package layout changed
   ```
   Integration tests (`*IT`) that need a model or database: run them if the environment allows;
   otherwise list them under *Not verified here* in the PR body.
5. **Repair budget:** two attempts to get validation green. On the third failure, revert the
   branch, re-bucket the issue as `BLOCKED_DECISION` with the exact failing output, and continue
   to the next item. Do not sink the session into one issue.
6. **Docs:** if behaviour visible to the model, an operator, or an API caller changed, update
   `pos-mcp-server/README.md` (it is the authoritative module document) in the same commit.
7. **Commit** with `fix(mcp): <what changed> (#<issue>)` or `feat(mcp): ...`, following the
   existing `git log` style. Push with `git push -u origin <branch>`.
8. **PR** via the Pull Request agent / `.github/pull_request_template.md`, title
   `fix(mcp): <summary> (#<issue>)`, body containing `Closes #<issue>`, the assumption(s) you made,
   the exact validation commands run with pass/fail, and a *Not verified here* section when any
   step was skipped.
9. **Subscribe** to the PR (`subscribe_pr_activity`) so CI failures and review comments come back
   to this session; a red PR you opened is your work at the next check-in, before the next issue.
10. **Ledger** row → `SHIPPED (PR #n)`. Next item.

## Phase 4 — Loop control

After every item, re-run Phase 0 discovery (new issues, merged dependencies) and re-order.
Continue while any `FIX_NOW` item remains.

Stop only when **all** hold:

- Every open `pos-mcp-server` issue is `SHIPPED`, `IN_FLIGHT`, `ALREADY_FIXED`, or `BLOCKED_*`.
- Every PR you opened is green or has a comment saying exactly what is red and why you are not
  fixing it.
- The ledger is complete.

If you are running under `/loop` or a Routine, re-arm the next check-in before ending the turn
while any PR you opened is not yet merged.

## Final report (the only long message)

1. Shipped: issue → PR, one line each.
2. Already fixed: issue → proving commit/test.
3. Blocked, grouped by bucket, one line each with the concrete unblocker
   (which issue must merge, which environment is needed, which decision is needed and your
   recommendation).
4. PRs still red, with the failing check.
5. Anything you assumed that the author should sanity-check.

No narrative of the journey. The PRs are the record.
