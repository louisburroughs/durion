---
title: "Capability Run Artifact Template"
owner: "louisburroughs/durion"
status: "active"
last_updated: "2026-03-17"
---

# Capability Run Artifact Template

Use this template for `docs/capabilities/<CAP-ID>/runs/latest.md`.

## 1. Run Metadata

- Capability: `<CAP-ID>`
- Run Timestamp (UTC): `<YYYY-MM-DDTHH:MM:SSZ>`
- Agent/Operator: `<name>`
- Branch(es): `<branch names>`
- Status: `in-progress | completed | blocked`

## 2. Inputs Used

- Manifest: `docs/capabilities/<CAP-ID>/CAPABILITY_MANIFEST.yaml`
- Workset: `docs/capabilities/<CAP-ID>/AGENT_WORKSET.yaml`
- PRD: `docs/capabilities/PRD-agent-capability-frontend-execution.md`

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| `<story title>` | `<#>` | `<#>` | `done/blocked/partial` | `<short note>` |

## 4. Implementation Changes

### Frontend Files Changed
- `<path/to/file1>`
- `<path/to/file2>`

### Behavior Implemented
- `<story behavior 1>`
- `<story behavior 2>`

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| `<story>` | `<operationId>` | `<openapi/spec + sdk>` | `<path>` | `done/blocked` |

## 6. Validation

### Commands Run
```bash
<command 1>
<command 2>
```

### Results
- Build: `pass/fail`
- Tests: `pass/fail`
- Lint: `pass/fail`
- Typecheck: `pass/fail`

## 7. Blockers and Decisions

- Blocker: `<description>`
  - Impact: `<scope>`
  - Needed: `<decision/input/fix>`

## 8. Follow-Up Actions

- [ ] `<action 1>`
- [ ] `<action 2>`

## 9. Completion Gate

Mark complete only if all are true:
- [ ] All workset stories processed.
- [ ] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria verified against story markdown and wireframe.
- [ ] Validation commands executed and results recorded.
- [ ] `runs/latest.md` reflects final state.
