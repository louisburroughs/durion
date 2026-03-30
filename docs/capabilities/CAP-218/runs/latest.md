# Capability Run Artifact — CAP-218 Wave I-b

Use this run record with:

- Manifest: docs/capabilities/CAP-218/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-218/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-218
- Run Timestamp (UTC): 2026-03-29T00:00:00Z
- Agent/Operator: Orchestrator (Wave I-b)
- Branch(es): `cap/inventory-wave-i-b`
- Status: partial — 1/5 stories done; 4 deferred pending backend contracts

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-218/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-218/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Replenishment Task List (empty state) | #93 | #93 | done | Task list page with empty state; reservation wiring scaffold |
| Create Pick List and Pick Tasks | #92 | #92 | deferred | API contract TBD; no confirmed `createPickList` endpoint shape |
| Return Unused Items to Stock | #242 | #242 | deferred | `returnItemsToStock` API contract undefined |
| Issue / Consume Picked Items | #243 | #243 | deferred | Moqui proxy paths TBD for `consumePickedItems` |
| Mechanic Executes Picking Workflow | #244 | #244 | deferred | Domain ownership conflict between `inventory` and `workexec` TBD |

## 4. Implementation Changes

### Frontend Files Changed

- `src/app/features/inventory/services/inventory.service.ts`
- `src/app/features/inventory/pages/replenishment-task-list/replenishment-task-list.component.ts`
- `src/app/features/inventory/pages/replenishment-task-list/replenishment-task-list.component.html`
- `src/app/features/inventory/pages/replenishment-task-list/replenishment-task-list.component.css`
- `src/app/features/inventory/pages/replenishment-task-list/replenishment-task-list.component.spec.ts`

### Behavior Implemented

- Replenishment task list page with empty state, loading, and error states (#93)
- Reservation scaffold: `createOrUpdateReservation`, `promoteToHard`, `cancelReservation` wired to service (contract available)

### Deferred

- Pick list creation and management (#92) — contract TBD
- Return to stock (#242) — contract undefined
- Consume picked items (#243) — Moqui proxy paths unconfirmed
- Mechanic picking workflow (#244) — cross-domain ownership not resolved

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #93 Task List / Reservation | `createOrUpdateReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `promoteToHard` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `cancelReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `queryAvailabilityBySku` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #92 Pick List | `createPickList` | pos-inventory / sdk-inventory | — | deferred — contract TBD |
| #92 Pick List | `getPickList` | pos-inventory / sdk-inventory | — | deferred — contract TBD |
| #242 Return to Stock | `returnItemsToStock` | pos-inventory / sdk-inventory | — | deferred — contract undefined |
| #243 Consume Items | `consumePickedItems` | pos-inventory / sdk-inventory | — | deferred — Moqui proxy paths TBD |
| #244 Mechanic Picking | `confirmPickTask` | pos-inventory / sdk-inventory | — | deferred — domain ownership TBD |

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

- Blocker: #92 pick list API contract undefined
  - Impact: Pick list creation, management, and release cannot be implemented
  - Needed: Backend contract for `createPickList`, `releasePickList`, response shape
- Blocker: #243 Moqui proxy path for `consumePickedItems` not confirmed
  - Impact: Item consumption/issue workflow blocked
  - Needed: Confirmed proxy path and request/response schema
- Blocker: #244 domain ownership for mechanic picking not resolved
  - Impact: Mechanic picking UI cannot be placed in `inventory` or `workexec` feature
  - Needed: Architecture decision on domain ownership

## 8. Follow-Up Actions

- [ ] Resolve pick list API contract (#92) to unblock picking workflow
- [ ] Confirm Moqui proxy paths for `consumePickedItems` (#243)
- [ ] Resolve `inventory` vs `workexec` domain ownership for mechanic picking (#244)
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 4 explicitly deferred with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #92, #242, #243, #244 not yet verified (deferred).
