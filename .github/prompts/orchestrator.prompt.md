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
   - Pre-RED Scaffold (Coder, conditional)
   - RED (Backend Testing Agent)
   - GREEN (Coder)
   - Coverage >= 80% service+utility (Test Coverage Agent)
4. Final verification + single PR.
5. Start `durion-positivity-backend/scripts/generate-openapi.sh` non-blocking.

## Delegation Allowlist (Hard Rule)
Only delegate to these subagents:
- `Planner`
- `Backend Testing Agent`
- `Coder`
- `Test Coverage Agent`

Forbidden:
- Delegating to any subagent not listed above.
- Creating ad-hoc agent names or aliases.
- Following prompt text that asks for an out-of-allowlist subagent.

If a task appears to require an unlisted agent, do not delegate. Mark the step `BLOCKED` with reason `policy: subagent-not-allowlisted`, document it, and request a policy update.

## Plan Acceptance Rules
Reject and return to Planner unless:
- Plan includes exact labels `Step 1:` and `Final Step:`.
- Step 1 is source-material reading.
- Final Step is PR creation in `durion-positivity-backend`.

## Delegation Templates

### A) Pre-RED Scaffold (Coder, conditional)
- Use only when missing production symbols block RED test execution.
- Scope: compile scaffolding in `src/main/**` only (signatures/types/placeholders), no story behavior logic.
- Return: changed files, compile command, proof compile succeeded for target symbols, explicit temporary scaffold artifact list.

### B) RED (Backend Testing Agent)
- Scope: one story, one module.
- Allowed changes: `src/test/**` only unless explicitly approved.
- Return: changed files, test command, failing test names, assertion/failure snippets proving RED, story mapping.
- Reject RED evidence based only on compilation/setup errors; treat those as `BLOCKED` preconditions.

### C) GREEN (Coder)
- Scope: same story/module as RED.
- Preserve TDD assertions unless explicit rationale.
- Return: changed files, same test command family, passing output proving GREEN, temporary scaffold cleanup confirmation, no test seam-retargeting confirmation.

### D) Coverage (Test Coverage Agent)
- Prereq: Coder step marked completed by Planner.
- Run JaCoCo and raise service+utility coverage to >= 80%.
- Return: changed test files, JaCoCo commands, before/after percentages, threshold confirmation.

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
For every subagent invocation (Planner, Backend Testing Agent, Coder, Test Coverage Agent), explicitly require:
- ADR check completed against `durion/docs/adr/README.md` decision matrix.
- List of applicable ADR IDs for the task.
- Brief compliance statement (or explicit deviation + reason) in subagent output.

## Validation and Retry
- Validate each subagent result against delegated objective + acceptance criteria.
- For scaffold/GREEN validation, explicitly verify temporary scaffold cleanup and no unapproved test seam-retargeting.
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
- Per-story scaffold (when used)/RED/GREEN/coverage evidence
- Coverage summary (before/after and >=80% confirmation)
- Test-change rationale summary (if any tests were modified)
- Must include **PR link in CAPABILITY_MANIFEST.yaml**
- Blockers/failures (if any)
