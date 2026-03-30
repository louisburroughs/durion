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
| Create Pick List and Pick Tasks | #92 | #92 | deferred | Backend pick-list contract exists; workorder retrieval, proxy mapping, and ownership/orchestration choices remain unresolved |
| Return Unused Items to Stock | #242 | #242 | deferred | Submit contract exists; returnable-items load, reason-code lookup, and destination semantics remain unresolved |
| Issue / Consume Picked Items | #243 | #243 | deferred | Inventory and workorder consume contracts both exist; picked-items read model and canonical command choice remain unresolved |
| Mechanic Executes Picking Workflow | #244 | #244 | deferred | WorkExec ownership is likely, but canonical API surface, route parameter, and state model remain unresolved |

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

- Pick list creation and management (#92) — contract exists, but frontend retrieval/proxy/ownership details are still open
- Return to stock (#242) — submit contract exists, but read-side support and destination semantics are still open
- Consume picked items (#243) — inventory and workorder consume contracts exist, but picked-items read model and canonical command path are still open
- Mechanic picking workflow (#244) — ownership direction is clearer, but concrete API/state model is still open

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #93 Task List / Reservation | `createOrUpdateReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `promoteToHard` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `cancelReservation` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #93 Task List / Reservation | `queryAvailabilityBySku` | pos-inventory / sdk-inventory | `inventory.service.ts` | done |
| #92 Pick List | `createPickList` | pos-inventory / sdk-inventory | — | deferred — backend contract exists; frontend proxy/ownership pending |
| #92 Pick List | `getPickListsForWorkorder` | pos-inventory / sdk-inventory | — | deferred — backend contract exists; workorder retrieval/proxy pending |
| #242 Return to Stock | `returnItemsToStock` | pos-inventory / sdk-inventory | — | deferred — submit contract exists; read-side contracts pending |
| #243 Consume Items | `consumePickedItems` | pos-inventory / sdk-inventory | — | deferred — inventory consume contract exists; picked-items read model and canonical write-surface choice remain pending |
| #244 Mechanic Picking | `confirmPickTask` | pos-inventory / sdk-inventory | — | deferred — ownership and canonical API/state model pending |

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

- Blocker: #92 inventory pick-list contract exists, but the frontend still lacks a settled retrieval/orchestration shape
  - Impact: Pick-list create/load flow cannot be wired safely without deciding whether the screen is inventory-owned or WorkExec-facing
  - Needed: Confirm canonical frontend surface, Moqui proxy mapping, workorder-based retrieval path, and any release/print policy
- Blocker: #242 return submit contract exists, but the frontend lacks the read-side data needed to render the flow
  - Impact: Return-to-stock UI cannot load returnable items, reason codes, or destination options deterministically
  - Needed: Confirm returnable-items endpoint, reason-code lookup, destination model (`locationId` vs `storageLocationId`), and retry/idempotency guidance
- Blocker: #243 consume contracts now exist in both inventory and workorder modules, but the frontend still lacks a canonical picked-items source and write-surface choice
  - Impact: Consume flow cannot safely choose between inventory movement semantics and existing workorder part-usage semantics
  - Resolved: inventory exposes `POST /v1/inventory/consumption`; `pos-workorder` exposes `POST /v1/workorders/{workorderId}/parts/consume`; success identifiers and quantity shape differ by surface
  - Needed: Confirm picked-items read model/system of record, choose canonical consume surface, align quantity precision rules, and decide which success/reference identifiers the UI should show
- Blocker: #244 ownership is directionally resolved toward WorkExec, but the concrete screen contract is still undefined
  - Impact: Mechanic picking UI still cannot be placed or routed confidently
  - Needed: Confirm owning domain, canonical endpoint family, route key (`workOrderId` vs `pickTaskId`), scan semantics, and pick task/line state model

## 8. Follow-Up Actions

- [ ] Confirm canonical frontend surface and proxy mapping for pick-list creation/load (#92)
- [ ] Define returnable-items/reason-code/destination contracts for return-to-stock (#242)
- [ ] Confirm picked-items read model and canonical consume surface for consumption (#243)
- [ ] Finalize WorkExec vs Inventory API/state model for mechanic picking (#244)
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 4 explicitly deferred with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #92, #242, #243, #244 not yet verified (deferred).
