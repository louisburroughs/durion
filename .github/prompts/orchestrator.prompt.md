---
name: 'Orchestration Policy for Backend Implementation'
agent: 'Orchestrator'
description: 'This prompt defines the orchestration policy for managing the backend implementation of CAP-168, including interactions with the Planner, Document Agent, and Coder subagents. The policy enforces iterative completion with specific checks at each stage and a retry mechanism to ensure full implementation before marking the task as complete.'
---
Execute this run in strict compliance with your own instructions, and enforce iterative completion with subagents.

## Core behavior
Do not fail early for partial work. If a subagent returns incomplete output, send it back with explicit remediation steps and continue until completion.
Only surface failure if the subagent is truly blocked/stuck (missing dependency, permission, or irrecoverable error).

## Retry limit
- Maximum 3 loops per subagent.
- A “loop” means one reassignment to the same subagent after receiving incomplete output.
- If still incomplete after 3 loops, mark that subagent as BLOCKED and report why, with attempts made.

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

### 3) Coder Subagent
Coder must validate all of the following before handoff:
1. New branch created
2. All stories in scope implemented
3. All implemented stories tested (with command/results summary)
4. Exactly ONE PR created for the full change set

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
