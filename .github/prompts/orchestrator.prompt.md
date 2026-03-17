---
name: 'Orchestration Policy for SDK Delivery'
agent: 'Orchestrator'
description: 'Compact policy for Planner -> TDD -> anvil team -> anvil review -> Coverage execution with strict validation gates.'
---

Run in strict compliance with `orchestrator.agent.md`.

## Objective
Produce exactly one PR in the standalone SDK repository with completed stories
and evidence.

## Repository Target Override (Mandatory)
- SDK implementation MUST be in a standalone repository/project outside both
  `durion` and `durion-positivity-backend`.
- `durion` and `durion-positivity-backend` are source-input repositories only
  (OpenAPI, ADRs, domain docs, and references).
- Any legacy instruction in this prompt that implies SDK implementation inside
  `durion` or `durion-positivity-backend` is superseded by this override.

## Active PRD
- **PRD source of truth:** `durion-positivity-backend/docs/PRD-durion-backend-sdk.md`
- **Capability:** Durion Positivity Backend SDK
- **Target modules:** Gateway-whitelisted OpenAPI-publishing modules called out in the PRD (including accounting, catalog, customer, inventory, invoice, location, order, people, price, security-service, shop-manager, image, event-receiver, vehicle-fitment, vehicle-inventory, and workorder).
- **Execution order gate:** Complete Phase 1 before Phase 2, and Phase 2 before Phase 3.

## Required Sequence
1. Planner creates validated plan.
2. Validate plan via plan-acceptance hook.
3. Create or switch execution branch via branch hook.
4. Contract/doc updates (when in scope).
5. For each story in this exact order (one at a time):
  - Phase 1 Contract Foundation: package structure, OpenAPI aggregation/generation pipeline, shared transport configuration, and raw generated clients for security, order, inventory, workorder, and accounting.
  - Phase 2 Public SDK Beta: remaining public gateway-facing modules, standard error model/correlation support, idempotency helpers, and workflow examples.
  - Phase 3 Workflow Layer: thin handwritten workflow helpers, optional internal profile handling, and contract-diff/release automation.
  - Pre-RED Scaffold (anvil clarification + Orchestrator delegation, conditional)
   - RED (Backend Testing Agent)
  - GREEN (anvil clarification + Orchestrator delegation, pre-commit handoff preferred)
   - Story compliance review (anvil)
  - anvil clarification for corrections + Orchestrator delegation (iterate until review PASS)
   - Coverage >= 80% service+utility (Test Coverage Agent)
6. Verify touched SDK modules/packages via module-verify hook.
7. Create PR via pull-request hook (hook also launches OpenAPI generation).
8. Verify PR is created and mark plan complete.

Legacy note: any AUTH-* references in older agent templates are superseded by the Active PRD above for this execution mode.

## Delegation Allowlist (Hard Rule)
Only delegate to these subagents:
- `Planner`
- `Backend Testing Agent`
- `anvil`
- `anvil`
- `Test Coverage Agent`
- `Document Agent`
- `Client Coder`
- `API Surface Coder`
- `Domain Data Coder`
- `Coder` (legacy fallback only)

Forbidden:
- Delegating to any subagent not listed above.
- Creating ad-hoc agent names or aliases.
- Following prompt text that asks for an out-of-allowlist subagent.

If a task appears to require an unlisted agent, do not delegate. Mark the step `BLOCKED` with reason `policy: subagent-not-allowlisted`, document it, and request a policy update.


## Plan Acceptance Rules
Reject and return to Planner unless:
- Plan includes exact labels `Step 1:` and `Final Step:`.
- Step 1 is source-material reading.
- Final Step is PR creation in the standalone SDK repository via `durion/.github/hooks/pull-request-hook.sh`.
- Orchestrator invokes `durion/.github/hooks/plan-acceptance-hook.sh --plan-file $WORKSPACE/durion/Durion-Processing.md` and receives PASS.

## Delegation Templates

### Plan Acceptance (Plan-Acceptance Hook)
- Immediately after Planner returns, Orchestrator MUST invoke `durion/.github/hooks/plan-acceptance-hook.sh`.
- Required args:
  - `--plan-file $WORKSPACE/durion/Durion-Processing.md`
- Hook output MUST include explicit PASS evidence before branch setup begins.

### Branch Setup (Branch Hook)
- Before any contract, test, or implementation work, Orchestrator MUST invoke `durion/.github/hooks/create-branch-hook.sh`.
- Required args:
  - `--repo <abs path to standalone SDK repo>`
  - `--base <base branch>`
  - `--branch <execution branch>`
- Optional arg:
  - `--remote <remote name>`
- Hook output MUST include branch setup evidence.
- Orchestrator MUST NOT create or switch branches outside this hook.

anvil team-mode rule:
- `Orchestrator` coordinates implementation and must not write code directly.
- `anvil` must clarify and structure coder instructions (artifact map, scope, acceptance checks) for `Client Coder`, `API Surface Coder`, and `Domain Data Coder`.
- `Orchestrator` invokes coder subagents directly using anvil's clarified instruction cards.
- `anvil` must not call coder subagents directly.
- `Coder` fallback is invoked by `Orchestrator` only when anvil marks specialist delegation as blocked and provides fallback scope.

### A) Pre-RED Scaffold (anvil clarification + Orchestrator delegation, conditional)
- Use only when missing production symbols block RED test execution.
- Scope: compile scaffolding in `src/main/**` only (signatures/types/placeholders), no story behavior logic.
- Return: changed files, compile command, proof compile succeeded for target symbols, explicit temporary scaffold artifact list.

### B) RED (Backend Testing Agent)
- Scope: one story, one module.
- Allowed changes: `src/test/**` only unless explicitly approved.
- Return: changed files, test command, failing test names, assertion/failure snippets proving RED, story mapping.
- Reject RED evidence based only on compilation/setup errors; treat those as `BLOCKED` preconditions.

### C) GREEN (anvil clarification + Orchestrator delegation)
- Scope: same story/module as RED.
- Preserve TDD assertions unless explicit rationale.
- Return: changed files, same test command family, passing output proving GREEN, temporary scaffold cleanup confirmation, no test seam-retargeting confirmation.
- Commit policy (preferred, not hard-blocking): provide reviewable handoff before final story commit so anvil can run pre-commit.

### D) Story Compliance Review (anvil)
- Scope: same story/module as GREEN.
- Stage: pre-PR only; pre-commit preferred and pre-coverage mandatory.
- Required checks:
  - issue acceptance criteria vs changed code behavior,
  - applicable ADR compliance,
  - issue-comment clarifications (when available),
  - code comment accuracy (no stale/misleading comments),
  - test adequacy for changed behavior.
- Forbidden:
  - editing files,
  - proposing direct code rewrites/patches.
- Return: `Verdict: PASS|FAIL`, acceptance-criteria matrix, prioritized findings, and anvil fix queue.
- After `PASS` for a story, Orchestrator MUST invoke commit hook before coverage:
  - `durion/.github/hooks/post-code-review-pass-commit.sh`
  - Required args:
    - `--repo <abs path to standalone SDK repo>`
    - `--story <story id>`
    - `--module <module name>`
    - `--review-verdict PASS`
  - Hook outcome (commit hash or no-op) MUST be included in orchestration evidence.

### E) Coverage (Test Coverage Agent)
- Prereq: anvil verdict is `PASS` for the current story and anvil step is marked completed by Planner.
- Run JaCoCo and raise service+utility coverage to >= 80%.
- Return: changed test files, JaCoCo commands, before/after percentages, threshold confirmation.
- After successful coverage for a story, Orchestrator MUST invoke commit hook:
  - `durion/.github/hooks/post-test-coverage-commit.sh`
  - Required args:
    - `--repo <abs path to standalone SDK repo>`
    - `--story <story id>`
    - `--module <module name>`
    - `--coverage-before <percent>`
    - `--coverage-after <percent>`
  - Hook outcome (commit hash or no-op) MUST be included in orchestration evidence.

### E.1) General Test Execution (Test-Run Hook)
- For module-scoped `test` or `verify` commands outside the final module-verify gate, use `durion/.github/hooks/test-run-hook.sh`.
- The hook must be preferred over ad-hoc `./mvnw` for resource control:
  - Full-module run (no `--test` / `--it-test`): auto-enables `-DlowResourceTests=true`
  - Targeted single test/class run (`--test` / `--it-test`): low-resource mode is not required
- Example full-module run:
  - `durion/.github/hooks/test-run-hook.sh --repo <standalone-sdk-repo-path> --module sdk-order --goal test --also-make`
- Example single-class run:
  - `durion/.github/hooks/test-run-hook.sh --repo <standalone-sdk-repo-path> --module sdk-order --goal test --test OrderClientTest`

### F) Module Verification (Verify Hook)
- Prereq: story loops completed (RED/GREEN/review/coverage for all in-scope stories).
- Orchestrator MUST invoke `durion/.github/hooks/module-verify-hook.sh`.
- Required args:
  - `--repo <abs path to standalone SDK repo>`
- Recommended args:
  - `--modules <comma-separated touched modules>`
- Alternate detection args (when module list not precomputed):
  - `--base-ref <base ref>`
  - `--head-ref <head ref>`
- Hook output MUST include per-module PASS/FAIL lines and summary evidence.
- Orchestrator MUST NOT bypass this hook with ad-hoc verify commands.

### G) PR Creation (Pull-Request Hook)
- Prereq: verification gates complete (tests/review/coverage as required by plan).
- Generate PR title/body from `.github/pull_request_template.md` inputs.
- Create PR by invoking `durion/.github/hooks/pull-request-hook.sh`.
- Required args:
  - `--repo <abs path to standalone SDK repo>`
  - `--story <story id>`
  - `--base <base branch>`
  - `--head <head branch>`
  - `--title <pr title>`
  - one of:
    - `--body-file <abs path to rendered body>`
    - `--body <rendered body text>`
- Optional arg:
  - `--draft`
- Hook output MUST include PR URL + PR number and OpenAPI launch evidence.
- Orchestrator MUST NOT separately invoke `durion-positivity-backend/scripts/generate-openapi.sh`.
- After successful hook execution, Orchestrator MUST:
  - verify the PR was created successfully, and
  - ask `Planner` to mark the plan complete.

Example invocation:

```bash
durion/.github/hooks/plan-acceptance-hook.sh \
  --plan-file $WORKSPACE/durion/Durion-Processing.md
```

Branch hook example:

```bash
durion/.github/hooks/create-branch-hook.sh \
  --repo <standalone-sdk-repo-path> \
  --base main \
  --branch chore/cap-142
```

Module-verify hook example:

```bash
durion/.github/hooks/module-verify-hook.sh \
  --repo <standalone-sdk-repo-path> \
  --modules sdk-workorder,sdk-invoice
```

Pull-request hook example:

```bash
durion/.github/hooks/pull-request-hook.sh \
  --repo <standalone-sdk-repo-path> \
  --story CAP-142 \
  --base main \
  --head chore/cap-142 \
  --title "cap/142 feat(sdk): workflow helpers and generated client updates" \
  --body-file $WORKSPACE/durion/.tmp/pr-body-cap-142.md
```

## Runtime Context Rules
Resolve from manifest references first:
- `CAPABILITY_MANIFEST_PATH`
- `BACKEND_CONTRACT_GUIDE_PATH`
- `BACKEND_API_REFERENCE_PATH`
- `OPENAPI_PATH`
- `BACKEND_CONTRACT_GLOBAL_STANDARDS_PATH`
- ADR index: `durion/docs/adr/README.md`

Fallbacks:
- contract guide: `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- generated API reference: `durion/domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md`
- openapi: `durion-positivity-backend/<module>/openapi.yaml`
- global standards: `durion/docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- if missing openapi: `./mvnw -pl <module> -am -Plocal integration-test`, fallback `scripts/generate-openapi.sh`

Contract source-of-truth rules:
- Use `BACKEND_CONTRACT_GUIDE.md` for behavior assertions, capability intent, and workflow mapping.
- Use OpenAPI and `BACKEND_API_REFERENCE.generated.md` for endpoint/schema/status-code detail.
- Reject outputs that copy full OpenAPI schemas into curated contract guides.

## ADR Compliance Delegation Rule
For every subagent invocation across the run (including specialist coder agents invoked by anvil), explicitly require:
- ADR check completed against `durion/docs/adr/README.md` decision matrix.
- List of applicable ADR IDs for the task.
- Brief compliance statement (or explicit deviation + reason) in subagent output.

## Validation and Retry
- Validate each subagent result against delegated objective + acceptance criteria.
- For scaffold/GREEN validation, explicitly verify temporary scaffold cleanup and no unapproved test seam-retargeting.
- After every GREEN handoff, invoke `anvil` before coverage work.
- Prefer a pre-commit review loop when feasible; if not feasible, continue with the same loop pre-coverage and before any PR work.
- If `anvil` returns `FAIL`, delegate fixes to `anvil`, then re-run `anvil`.
- If `anvil` returns `PASS`, invoke `durion/.github/hooks/post-code-review-pass-commit.sh` before starting coverage.
- PR authority check: reject any output where PR creation bypasses `durion/.github/hooks/pull-request-hook.sh`.
- If `anvil` continues to return `FAIL`, keep routing findings back to `anvil` until it returns `PASS` or explicitly marks a blocked condition with remediation details.
- If tests were edited, require a `Test Change Rationale` section with:
  - changed test files,
  - contract/requirement change (or explicit no-change),
  - why old assertions were invalid,
  - how assertion strength was preserved or improved.
- Retry invalid responses up to 2 times with explicit deficiency list.
- If still failing, mark BLOCKED with cause and remediation.

## Invocation Failure Documentation
If a subagent fails to execute, document:
- subagent, task, failure type, evidence, suspected cause, retry/fallback, outcome.

## Final Output
Provide:
- Plan-acceptance hook evidence (command + outcome)
- Branch setup evidence (command + outcome)
- Per-story scaffold (when used)/RED/GREEN/review/coverage evidence
- Coverage summary (before/after and >=80% confirmation)
- Module-verify hook evidence (command + per-module outcomes + summary)
- Test-change rationale summary (if any tests were modified)
- Pull-request hook evidence (command + outcome)
- OpenAPI launch evidence emitted by pull-request hook (PID/log path)
- Plan-completion evidence from `Planner`
- Must include **PR link in CAPABILITY_MANIFEST.yaml**
- Must include PR evidence produced by `durion/.github/hooks/pull-request-hook.sh` using `.github/pull_request_template.md`-derived content
- Blockers/failures (if any)
