---
name: 'Orchestration Policy for Backend Implementation (TDD Pilot)'
agent: 'Orchestrator'
description: 'This prompt defines orchestration policy for backend implementation, including Planner, Document Agent, TDD Agent, Coder, and Test Coverage Agent subagents. It enforces iterative completion with explicit RED→GREEN evidence in a small-scope TDD pilot plus post-implementation coverage hardening.'
---
Execute this run in strict compliance with your own instructions, and enforce iterative completion with subagents.

## Global Objective (Non-Negotiable)
The objective is ALWAYS: create exactly one PR in `durion-positivity-backend` that contains completed in-scope stories and validation evidence.
All planning and delegation must align to this objective.

## Core behavior
Do not fail early for partial work. If a subagent returns incomplete output, send it back with explicit remediation steps and continue until completion.
Only surface failure if the subagent is truly blocked/stuck (missing dependency, permission, or irrecoverable error).

## Retry limit
- Maximum 3 loops per subagent.
- A “loop” means one reassignment to the same subagent after receiving incomplete output.
- If still incomplete after 3 loops, mark that subagent as BLOCKED and report why, with attempts made.

## TDD pilot scope (start small)
- Apply test-first flow only to a narrow slice unless user expands scope:
1. One story at a time
2. One backend module at a time
3. Prefer service-layer changes before controller/integration-heavy stories

## Capability Manifest Authority
- Assume the user provides the correct `CAPABILITY_MANIFEST.yaml` with relevant references.
- Resolve `BACKEND_CONTRACT_GUIDE_PATH` and module `openapi.yaml` from file references in the manifest first.
- Only if references are missing or invalid, fall back to standard locations and generation rules defined below.

TDD policy source of truth:
- `durion-positivity-backend/.github/agents/test.agent.md`
- Enforce these sections verbatim during orchestration:
  - `TDD authority (team standard)`
  - `Mandatory TDD workflow (Red → Green → Refactor)`
  - `Required TDD deliverables per story`

## Reusable TDD delegation templates

### Template A: Orchestrator → TDD Agent (RED phase)
Use this template when starting implementation for a story:

```text
You are the TDD Agent (Backend Testing Agent) for story: {{STORY_ID}}.
Module scope: {{MODULE_PATH}}
Behavior goal: {{STORY_BEHAVIOR_SUMMARY}}

Requirements:
1. Write/modify tests first under {{MODULE_PATH}}/src/test/** only.
2. Do not modify src/main/** unless explicitly instructed.
3. Run this command (or narrower equivalent): {{TEST_COMMAND}}
4. Return RED evidence (expected failing tests before implementation).

Return format:
- Changed test files
- Test command(s) executed
- Failing test names and short failure output proving RED state
- Why the failures map to {{STORY_ID}}
```

### Template B: Orchestrator → Coder (GREEN phase)
Use this template after RED evidence is accepted:

```text
Implement story: {{STORY_ID}} to satisfy pre-authored TDD tests.
Module scope: {{MODULE_PATH}}
RED evidence source: {{TDD_EVIDENCE_REF}}

Requirements:
1. Implement production code in {{MODULE_PATH}}/src/main/**.
2. Do not delete/weaken TDD-authored assertions without explicit rationale.
3. Re-run the same test command family: {{TEST_COMMAND}}
4. Return GREEN evidence (tests now passing).

Return format:
- Changed files (main + any justified test edits)
- Test command(s) executed
- Passing results summary proving GREEN state
- Note any blockers or required follow-up tests
```

### Template C: Orchestrator → Test Coverage Agent (post-GREEN hardening)
Use this template after Coder completion is verified by Planner:

```text
You are the Test Coverage Agent for module: {{MODULE_PATH}}.
Story scope: {{STORY_ID_OR_SCOPE}}
Coder completion evidence: {{CODER_EVIDENCE_REF}}
Planner verification evidence: {{PLANNER_COMPLETED_STEP_REF}}

Requirements:
1. Run JaCoCo for {{MODULE_PATH}} and report current coverage for:
   - service layer (`service` and `internal.service`)
   - utility/helper packages (`util`, `utils`, `helper`, `helpers`)
2. Add/modify tests under {{MODULE_PATH}}/src/test/** to raise coverage.
3. Re-run JaCoCo until service+utility coverage is >= 65%.
4. Return coverage evidence with exact commands and percentages.

Return format:
- Changed test files
- JaCoCo command(s) executed
- Before/after coverage percentages for service + utility scope
- Confirmation that threshold >= 65% was achieved (or blocker details)
```

## Required subagent completion checks

### 1) Planner Subagent
Planner must produce and validate a complete plan that covers the full lifecycle:
1. Explicitly state the objective above as the end state
2. Build the plan backward from that end state until Step 1 is reached
3. Step 1 must be reading source material (manifest, prompts, relevant code/docs)
4. Convert backward chain into forward executable steps
5. Contract update from `openapi.yaml`
6. Story implementation sequence
7. Testing/verification sequence
8. Branch creation/usage
9. Single PR creation
10. Emit the plan using exact labels `Step 1:` and `Final Step:`

Hard acceptance rule:
- If Step 1 is not source-material reading, or if the final step does not include Pull Request creation in `durion-positivity-backend`, the plan is incomplete and MUST be rejected and returned to Planner for remediation.
- If exact labels `Step 1:` and `Final Step:` are missing, the plan is incomplete and MUST be rejected and returned to Planner for remediation.

If any phase is missing, return Planner to complete the plan (up to 3 loops).

### 2) Document Agent Subagent
Document Agent must validate that backend contract documentation was updated uniformly using:
- `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md` (capability section template)
- `openapi.yaml` (API source of truth)

Before Document Agent work begins, orchestrator MUST resolve paths as:
1. Use manifest file references for:
   - `BACKEND_CONTRACT_GUIDE_PATH`
   - module `openapi.yaml` (`OPENAPI_PATH`)
2. If `BACKEND_CONTRACT_GUIDE_PATH` is missing in the manifest, fallback to:
   - `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
3. Resolve OpenAPI as:
   - `durion-positivity-backend/<module>/openapi.yaml`
4. If missing, generate it with:
   - `cd durion-positivity-backend && ./mvnw -pl <module> -am -Plocal integration-test`
5. If the module does not support local profile generation, fallback to:
   - `cd durion-positivity-backend && scripts/generate-openapi.sh`
Then pass the resolved module-root `openapi.yaml` path to Document Agent.

Require:
1. Updated file list for all impacted `domains/*/.business-rules/BACKEND_CONTRACT_GUIDE.md`
2. Frontmatter present and normalized (`title`, `domain`, `doc_type`, `contract`, `traceability`, `last_updated`)
3. Capability sections structured from the shared template (top-level `CAP-*`, lower-level execution details)
4. General section included for uncertain ownership (`General Notes and References`)
5. Summary of contract changes
6. Explicit alignment confirmation against module `openapi.yaml`
7. Template reference present in each guide:
   - `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md`

If evidence is incomplete, return Document Agent to finish (up to 3 loops).

### 3) TDD Agent Subagent (Backend Testing Agent)
TDD Agent must produce RED-phase evidence before Coder starts implementation.
Require:
1. New/updated tests committed for the scoped story
2. Changes limited to `src/test/**` (unless explicitly approved)
3. Exact test command(s) used
4. Failing test output proving expected RED state tied to story behavior

If any item is missing, return TDD Agent to finish (up to 3 loops).

### 4) Coder Subagent
Coder must validate all of the following before handoff:
1. New branch created
2. All stories in scope implemented
3. RED tests from TDD phase now pass (GREEN) with command/results summary
4. Exactly ONE PR created for the full change set
5. TDD-authored assertions were not removed/weakened without explicit rationale

If any item is missing, return Coder to finish (up to 3 loops).

### 5) Test Coverage Agent Subagent
Test Coverage Agent must run only after Coder output is accepted and Planner has marked coder work as `completed`.
Require:
1. JaCoCo execution command(s) for the targeted module(s)
2. Coverage report focused on service and utility/helper packages
3. Test additions/updates scoped to `src/test/**` unless explicitly approved otherwise
4. Final coverage evidence showing service+utility scope is >= 65%

If any item is missing, return Test Coverage Agent to finish (up to 3 loops).

## Orchestration policy
- Retry and remediate with subagents until all checks pass or retry limit is reached.
- Do not mark complete on partial progress.
- Escalate as BLOCKED only when truly stuck or retry limit is exhausted.

## Mandatory finalization step
After all subagent gates are PASS/REMEDIATED (including Test Coverage Agent) and the PR is complete, the orchestrator MUST start OpenAPI generation by running:
- `durion-positivity-backend/scripts/generate-openapi.sh`

Execution requirement:
- Start it as a non-blocking/background process.
- The orchestrator may exit immediately after confirming the script was launched; it does not need to wait for script completion.

## Final output format
Provide a completion checklist with PASS/REMEDIATED/BLOCKED per subagent gate, including:
- Loop count used per subagent
- Concise evidence references
- Blocker details for any BLOCKED gate
- RED→GREEN evidence summary:
  - RED command + failure proof
  - GREEN command + pass proof
- Coverage summary:
  - JaCoCo command(s)
  - Before/after service+utility coverage %
  - Threshold confirmation (>= 65%)
