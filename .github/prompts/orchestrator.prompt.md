---
name: 'Orchestration Policy for Backend Delivery'
agent: 'Orchestrator'
description: 'Compact policy for Planner -> TDD -> Coder -> Coverage execution with strict validation gates.'
---

Run in strict compliance with `orchestrator.agent.md`.

## Objective
Produce exactly one PR in `durion-positivity-backend` with completed stories and evidence.

## Required Sequence
1. Planner creates validated plan.
2. Contract/doc updates (when in scope).
3. For each story (one at a time):
   - RED (TDD Agent)
   - GREEN (Coder)
   - Coverage >= 80% service+utility (Test Coverage Agent)
4. Final verification + single PR.
5. Start `durion-positivity-backend/scripts/generate-openapi.sh` non-blocking.

## Plan Acceptance Rules
Reject and return to Planner unless:
- Plan includes exact labels `Step 1:` and `Final Step:`.
- Step 1 is source-material reading.
- Final Step is PR creation in `durion-positivity-backend`.

## Delegation Templates

### A) RED (TDD Agent)
- Scope: one story, one module.
- Allowed changes: `src/test/**` only unless explicitly approved.
- Return: changed files, test command, failing output proving RED, story mapping.

### B) GREEN (Coder)
- Scope: same story/module as RED.
- Preserve TDD assertions unless explicit rationale.
- Return: changed files, same test command family, passing output proving GREEN.

### C) Coverage (Test Coverage Agent)
- Prereq: Coder step marked completed by Planner.
- Run JaCoCo and raise service+utility coverage to >= 80%.
- Return: changed test files, JaCoCo commands, before/after percentages, threshold confirmation.

## Runtime Context Rules
Resolve from manifest references first:
- `CAPABILITY_MANIFEST_PATH`
- `BACKEND_CONTRACT_GUIDE_PATH`
- `OPENAPI_PATH`

Fallbacks:
- contract guide: `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- openapi: `durion-positivity-backend/<module>/openapi.yaml`
- if missing openapi: `./mvnw -pl <module> -am -Plocal integration-test`, fallback `scripts/generate-openapi.sh`

## Validation and Retry
- Validate each subagent result against delegated objective + acceptance criteria.
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
- Per-story RED/GREEN/coverage evidence
- Coverage summary (before/after and >=80% confirmation)
- Test-change rationale summary (if any tests were modified)
- PR reference
- Blockers/failures (if any)
