---
name: 'Orchestration Policy for Backend Implementation (TDD Pilot)'
agent: 'Orchestrator'
description: 'This prompt defines orchestration policy for backend implementation, including Planner, Document Agent, TDD Agent, and Coder subagents. It enforces iterative completion with explicit RED→GREEN evidence in a small-scope TDD pilot.'
---
Execute this run in strict compliance with your own instructions, and enforce iterative completion with subagents.

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

## Required subagent completion checks

### 1) Planner Subagent
Planner must produce and validate a complete plan that covers the full lifecycle:
1. Contract update from `openapi.json`
2. Story implementation sequence
3. Testing/verification sequence
4. Branch creation/usage
5. Single PR creation

If any phase is missing, return Planner to complete the plan (up to 3 loops).

### 2) Document Agent Subagent
Document Agent must validate that backend contract documentation was updated using `openapi.json` as source of truth.
Require:
1. Updated file list
2. Summary of contract changes
3. Explicit alignment confirmation against `openapi.json`

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

## Orchestration policy
- Retry and remediate with subagents until all checks pass or retry limit is reached.
- Do not mark complete on partial progress.
- Escalate as BLOCKED only when truly stuck or retry limit is exhausted.

## Final output format
Provide a completion checklist with PASS/REMEDIATED/BLOCKED per subagent gate, including:
- Loop count used per subagent
- Concise evidence references
- Blocker details for any BLOCKED gate
- RED→GREEN evidence summary:
  - RED command + failure proof
  - GREEN command + pass proof
