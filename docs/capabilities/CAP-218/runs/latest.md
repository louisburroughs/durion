# Capability Run Artifact — CAP-218 Wave I-b

Use this run record with:

- Manifest: docs/capabilities/CAP-218/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-218/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-218
- Run Timestamp (UTC): 2026-03-30T00:00:00Z
- Agent/Operator: Orchestrator (Wave I-b)
- Branch(es): `cap/inventory-wave-i-b`
- Status: partial — 1/5 stories done; 4 deferred on branch, 2 still awaiting contract or architecture decisions

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-218/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-218/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Replenishment Task List (empty state) | #93 | #93 | done | Task list page with empty state; reservation wiring scaffold |
| Create Pick List and Pick Tasks | #92 | #92 | deferred | WorkExec/workorder ownership is clarified and inventory raw endpoints exist; frontend-facing facade mapping, display enrichment, and print-policy details remain unresolved |
| Return Unused Items to Stock | #242 | #242 | deferred | Read-side, reason-code, and destination mapping are now documented; implementation remains deferred |
| Issue / Consume Picked Items | #243 | #243 | deferred | Backend ownership note documented; frontend should target a workorder-owned facade that orchestrates inventory consumption |
| Mechanic Executes Picking Workflow | #244 | #244 | deferred | WorkExec ownership and permission direction are documented, but the frontend still depends on the workorder-facing pick scaffold identified in #92 |

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

- Pick list creation and management (#92) — WorkExec ownership is clarified and inventory raw endpoints exist, but frontend retrieval/contract mapping, display enrichment, and print-policy details are still open
- Return to stock (#242) — contract note now documents read-side source, reason-code lookup, and destination behavior; implementation remains deferred
- Consume picked items (#243) — backend ownership note documented; implementation remains deferred until the workorder-owned facade contract is added
- Mechanic picking workflow (#244) — mechanic-picking rules are clearer, but the screen still depends on the workorder-facing pick scaffold from #92

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #93 Task List / Reservation | `createOrUpdateReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `promoteToHard` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `cancelReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `queryAvailabilityBySku` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #92 Pick List | `createPickList` | pos-inventory / sdk-inventory | — | deferred — backend contract exists; frontend should still wait for a WorkExec/workorder-facing facade contract |
| #92 Pick List | `getPickListsForWorkorder` | pos-inventory / sdk-inventory | — | deferred — raw inventory list contract exists, but final facade mapping, enrichment, and print policy remain pending |
| #242 Return to Stock | `returnItemsToStock` | pos-inventory / sdk-inventory | — | deferred — contract documented; implementation pending |
| #243 Consume Items | `consumePickedItems` | pos-inventory / sdk-inventory | — | deferred — inventory consume contract exists, but frontend should wait for a workorder-owned facade contract instead of wiring browser-to-inventory directly |
| #244 Mechanic Picking | `confirmPickTask` | pos-inventory / sdk-inventory | — | deferred — frontend should wait for the workorder-facing pick scaffold from #92 rather than wire inventory execution directly |

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

- Blocker: #92 pick-list contract exists, and ownership is now resolved toward WorkExec, but the frontend still lacks a settled retrieval/contract shape
  - Impact: Pick-list create/load flow still cannot be wired safely without choosing the canonical workorder-based retrieval path and finalizing print-policy behavior
  - Resolved: parts picking for workorder execution is Workorder Execution-owned
  - Needed: Confirm canonical frontend-facing contract mapping, workorder-based retrieval path, whether tasks are embedded or separate, and any release/print policy
- Decision: #242 contract note is now documented
  - Resolved: use workorder detail as the returnable-items source
  - Resolved: use integration-owned reason-code lookup and eligible `locationId` with optional `storageLocationId`
  - Resolved: retries remain manual because idempotency is not documented
  - Remaining: implement the documented return-to-stock flow in frontend code
- Decision: #243 ownership note is now documented
  - Resolved: frontend should target a workorder-owned facade; backend should orchestrate inventory consumption server-to-server
  - Resolved: inventory owns pick-list/task and `consumePickedItems`; workorder detail owns part totals such as `quantityIssued` and `quantityConsumed`
  - Remaining: implement the frontend once the workorder-owned facade route and normalized response contract exist
- Blocker: #244 is no longer blocked on ownership, but it still lacks the workorder-facing pick scaffold needed to load, scan, confirm, and complete picks
  - Impact: Mechanic picking UI still cannot be wired safely because `pos-workorder` does not yet expose pick-task endpoints; current parts usage endpoints are not a substitute for the pick workflow
  - Tracking: backend issue `durion-positivity-backend#179` should own the WorkExec/workorder-facing facade, while `durion-positivity-backend#28` remains the inventory-side pick-list/task contract
  - Needed: Implement the #92 scaffold, then settle the canonical endpoint family, route key (`workOrderId` vs `pickTaskId`), scan semantics, and pick task/line state model for #244

## 8. Follow-Up Actions

- [ ] Implement the backend pick-list scaffold for #92 and expose the WorkExec/workorder-facing facade contract for pick-list load/view
- [ ] Implement the documented return-to-stock flow for #242
- [ ] Add or confirm the workorder-owned facade contract for consume-picked-items (#243)
- [ ] Implement the workorder-facing pick scaffold for #92/#244, then wire the mechanic picking screen against that contract
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 4 explicitly deferred with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #92, #242, #243, #244 not yet verified (deferred).
