# Capability Run Artifact

Use this run record with:
- Manifest: docs/capabilities/CAP-006/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-006/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-006
- Run Timestamp (UTC): 2026-03-27T01:33:01Z
- Agent/Operator: Copilot Orchestrator — Wave C
- Branch(es): cap/workexec-wave-c
- Status: completed

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-006/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-006/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md
- Contract guide: domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md
- Stories: docs/capabilities/CAP-006/stories/ (218, 217, 216, 215, 214)

## 3. Story Execution Summary

| Story | Title | Result | Notes |
| --- | --- | --- | --- |
| 218 | Validate Completion Preconditions | done | Completion checklist panel; pending CR count surfaced |
| 217 | Resolve Approval-Gated CRs | done | Blocker banner on change-requests page |
| 216 | Finalize Billable Scope Snapshot | done | WorkorderFinalizePageComponent; snapshot history ledger |
| 215 | Complete Work Order | done | Complete modal with notes + failedChecks inline errors |
| 214 | Reopen Work Order | done | Reopen modal with required reopen reason |

## 4. Implementation Changes

### Frontend Files Changed
- `src/app/features/workexec/models/workexec.models.ts` — CAP-006 types appended
- `src/app/features/workexec/services/workexec.service.ts` — 5 new methods
- `src/app/features/workexec/pages/workorder-detail/workorder-detail-page.component.ts` — complete/reopen/invoice signals + handlers
- `src/app/features/workexec/pages/workorder-detail/workorder-detail-page.component.html` — CTAs, checklist panel, complete modal, reopen modal
- `src/app/features/workexec/pages/workorder-detail/workorder-detail-page.component.css` — checklist, modal, badge styles
- `src/app/features/workexec/pages/workorder-change-requests/workorder-change-requests-page.component.ts` — blockers computed + resolvedCount
- `src/app/features/workexec/pages/workorder-change-requests/workorder-change-requests-page.component.html` — blocker banner + clear-to-complete alert
- `src/app/features/workexec/pages/workorder-change-requests/workorder-change-requests-page.component.css` — blocker-banner styles
- `src/app/features/workexec/pages/workorder-finalize/workorder-finalize-page.component.ts` (NEW)
- `src/app/features/workexec/pages/workorder-finalize/workorder-finalize-page.component.html` (NEW)
- `src/app/features/workexec/pages/workorder-finalize/workorder-finalize-page.component.css` (NEW)
- `src/app/features/workexec/workexec.routes.ts` — finalize route added

### Behavior Implemented
- Completion checklist panel shows pending change-request count before allowing completion
- Complete Work Order modal calls `POST /v1/workorders/:id/complete`; surfaces `failedChecks[]` inline
- Reopen Work Order button + modal calls `POST /v1/workorders/:id/reopen` with required reopen reason
- Finalize page calls `POST /v1/workorders/:id/finalize` creating immutable billable scope snapshot
- Snapshot history loaded via `GET /v1/workorders/:id/snapshots`
- Blocker banner on change-requests page identifies AWAITING_ADVISOR_REVIEW CRs

## 5. API Wiring Evidence

| Story | operation_id | Source | Service File | Status |
| --- | --- | --- | --- | --- |
| 215 | completeWorkorder | POST /v1/workorders/{id}/complete | workexec.service.ts | done |
| 214 | reopenWorkorder | POST /v1/workorders/{id}/reopen | workexec.service.ts | done |
| 216 | finalizeWorkorder | POST /v1/workorders/{id}/finalize | workexec.service.ts | done |
| 216 | getSnapshotHistory | GET /v1/workorders/{id}/snapshots | workexec.service.ts | done |
| 213 | generateInvoice | POST /v1/workorders/{id}/generate-invoice | workexec.service.ts | done |

## 6. Validation

### Commands Run
```bash
npm run build
npm test -- --watch=false
```

### Results
- Build: PASS (0 errors; pre-existing CSS budget warnings only)
- Tests: PASS (66/66)

## 7. Blockers and Decisions

None.

## 8. Follow-Up Actions

None required.

## 9. Completion Gate

- [x] All workset stories processed.
- [x] All required operations wired.
- [x] Acceptance criteria verified against story markdown.
- [x] Validation commands executed and results recorded.
- [x] runs/latest.md reflects final state.


## 2. Inputs Used

- Manifest: docs/capabilities/CAP-006/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-006/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| <story title> | <#> | <#> | done/blocked/partial | <short note> |

## 4. Implementation Changes

### Frontend Files Changed
- <path/to/file1>
- <path/to/file2>

### Behavior Implemented
- <story behavior 1>
- <story behavior 2>

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| <story> | <operationId> | <openapi/spec + sdk> | <path> | done/blocked |

## 6. Validation

### Commands Run
```bash
<command 1>
<command 2>
```

### Results
- Build: pass/fail
- Tests: pass/fail
- Lint: pass/fail
- Typecheck: pass/fail

## 7. Blockers and Decisions

- Blocker: <description>
  - Impact: <scope>
  - Needed: <decision/input/fix>

## 8. Follow-Up Actions

- [ ] <action 1>
- [ ] <action 2>

## 9. Completion Gate

Mark complete only if all are true:
- [ ] All workset stories processed.
- [ ] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria verified against story markdown and wireframe.
- [ ] Validation commands executed and results recorded.
- [ ] runs/latest.md reflects final state.
