# Capability Run Artifact — CAP-220 Wave I-b

Use this run record with:

- Manifest: docs/capabilities/CAP-220/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-220/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-220
- Run Timestamp (UTC): 2026-03-29T00:00:00Z
- Agent/Operator: Orchestrator (Wave I-b)
- Branch(es): `cap/inventory-wave-i-b`
- Status: partial — 1/2 stories done; 1 deferred (shortage resolution cross-domain contract TBD)

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-220/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-220/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Putaway Task List and Execute | #88 | #88 | done | Task list and execute pages; availability and reallocation wiring |
| Shortage Resolution / Handle Shortages with Back-order | #89 | #89 | deferred | Cross-domain shortage resolution contract TBD |

## 4. Implementation Changes

### Frontend Files Changed

- `src/app/features/inventory/services/inventory.service.ts`
- `src/app/features/inventory/pages/putaway-task-list/putaway-task-list.component.ts`
- `src/app/features/inventory/pages/putaway-task-list/putaway-task-list.component.html`
- `src/app/features/inventory/pages/putaway-task-list/putaway-task-list.component.css`
- `src/app/features/inventory/pages/putaway-task-list/putaway-task-list.component.spec.ts`
- `src/app/features/inventory/pages/putaway-execute/putaway-execute.component.ts`
- `src/app/features/inventory/pages/putaway-execute/putaway-execute.component.html`
- `src/app/features/inventory/pages/putaway-execute/putaway-execute.component.css`
- `src/app/features/inventory/pages/putaway-execute/putaway-execute.component.spec.ts`

### Behavior Implemented

- Putaway task list with availability display (`queryAvailabilityBySku`, `queryInventoryAvailability`)
- Reservation reallocation action (`reallocate`)
- Task execution flow with location confirmation
- Empty/loading/error states per ADR-0031

### Deferred

- #89 Shortage resolution — `resolveShortage`, `queryLeadTime` cross-domain contract TBD

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #88 Putaway / Reallocation | `queryAvailabilityBySku` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #88 Putaway / Reallocation | `queryInventoryAvailability` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #88 Putaway / Reallocation | `reallocate` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #89 Shortage Resolution | `resolveShortage` | pos-inventory / sdk-inventory | — | deferred — cross-domain contract TBD |
| #89 Shortage Resolution | `queryLeadTime` | pos-inventory / sdk-inventory | — | deferred — cross-domain contract TBD |

## 6. Validation

### Commands Run

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend
npm run build
npx ng test --no-watch
```

### Results

- Build: pass
- Tests: pass (218/218 across 24 spec files)
- Lint: pass
- Typecheck: pass

## 7. Blockers and Decisions

- Blocker: #89 shortage resolution cross-domain contract undefined
  - Impact: Back-order creation flow and lead time display cannot be implemented
  - Needed: Confirmed `resolveShortage` and `queryLeadTime` endpoint specs with cross-domain routing

## 8. Follow-Up Actions

- [ ] Resolve cross-domain shortage resolution contract to unblock #89
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 1 explicitly deferred with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #89 not yet verified (deferred).
