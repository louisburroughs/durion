---
title: "PRD: CAP-218 Backend Fulfillment Completion"
owner: "louisburroughs/durion"
status: "draft"
last_updated: "2026-03-31"
---

# Product Requirements Document — CAP-218 Backend Fulfillment Completion

## 1. Objective

Complete the backend code build required to unblock the remaining CAP-218 fulfillment frontend stories by finishing the work split across:
- `durion-positivity-backend#28` — inventory-side pick-list and pick-task system of record
- `durion-positivity-backend#179` — WorkExec/workorder-facing pick facade for load, scan, confirm, and complete
- `durion-positivity-backend#178` — workorder-facing consume-picked-items orchestration

This PRD exists to convert the current contract-triage results into an implementation-ready backend delivery plan.

## 2. Problem Statement

The frontend CAP-218 stories are no longer blocked by complete uncertainty, but they are still blocked by missing backend orchestration in `pos-workorder`.

Today:
- `pos-inventory` already owns raw pick-list and pick-task state.
- `pos-workorder` already owns workorder detail and local part-usage endpoints.
- The browser still lacks a canonical WorkExec/workorder-facing contract for:
  - loading pick list/task state for a workorder
  - resolving scans during mechanic picking
  - confirming pick progress
  - completing a pick task
  - consuming picked items through a workorder-owned facade

As a result, frontend stories `durion#92`, `durion#243`, and `durion#244` cannot be implemented safely without browser-direct calls to inventory, which is explicitly not the desired architecture.

## 3. Scope

### In Scope
- Finish and validate the backend surfaces needed for CAP-218 fulfillment flows.
- Preserve the ownership split:
  - `pos-inventory` owns raw pick-list/task state and inventory movement state
  - `pos-workorder` owns browser-facing orchestration for workorder fulfillment flows
- Define and implement the canonical `pos-workorder` facade for:
  - pick-list load/view
  - mechanic picking execution
  - picked-items consume flow
- Update OpenAPI and tests for all new or changed backend endpoints.
- Normalize permission and error behavior so frontend route/action gating can be implemented deterministically.

### Out of Scope
- Frontend implementation itself.
- New inventory business-policy invention beyond already documented story decisions.
- Reworking existing inventory pick-list internals unless required to satisfy the facade contract.
- Generalized warehouse routing optimization beyond existing deterministic pick ordering.

## 4. Source Stories and Dependencies

### Frontend Stories Being Unblocked
- `durion#92` — View & Print Pick List / Pick Tasks for Workorder
- `durion#243` — Issue / Consume Picked Items to Workorder
- `durion#244` — Mechanic Executes Picking

### Backend Stories to Complete
- `durion-positivity-backend#28` — Create Pick List / Pick Tasks for Workorder
- `durion-positivity-backend#178` — Issue/Consume Picked Items to Workorder
- `durion-positivity-backend#179` — Mechanic Executes Picking

### Architectural Dependency Map
1. Inventory generates and stores pick-list/task state.
2. Workorder facade loads and reshapes that state for browser use.
3. Mechanic picking actions go to the workorder facade.
4. Workorder facade orchestrates server-to-server calls into inventory.
5. Consume flow also goes through the workorder facade rather than browser-direct inventory calls.

## 5. Current Baseline

### Already Available
- `pos-inventory`
  - create pick list
  - get pick list
  - list pick lists for workorder
  - release pick list
  - list pick tasks for pick list
  - confirm pick task
- `pos-workorder`
  - workorder detail read model
  - workorder part issue / consume / return usage endpoints
  - workorder part usage history

### Confirmed Gap
`pos-workorder` does not yet expose the WorkExec/workorder-facing pick scaffold needed by the frontend. Existing workorder part-usage endpoints are not a substitute for pick-task orchestration.

## 6. Product Requirements

### 6.1 Ownership Model
- `pos-inventory` remains system of record for:
  - pick-list state
  - pick-task state
  - inventory-side pick confirmation semantics
  - inventory consumption transactions
- `pos-workorder` owns:
  - browser-facing routes
  - response normalization for workorder context
  - orchestration across workorder + inventory
  - frontend-safe error and permission behavior

### 6.2 Canonical Route Strategy
- Primary browser route key for load/view should be `workorderId`.
- Responses may include `pickListId` and `pickTaskId` for mutation calls and traceability.
- If a direct `pickTaskId` route is also supported, it must be secondary and documented as such.

### 6.3 Required Workorder-Facing Pick Facade

`pos-workorder` must expose a canonical facade for the following capabilities:

1. Load pick list for a workorder
2. Load pick tasks and lines in frontend-ready shape
3. Resolve scan input
4. Confirm line-level or task-level picking progress
5. Complete a pick task
6. Load picked items eligible for consumption
7. Submit consume-picked-items command

### 6.4 Proposed Facade Surface

The exact path names can be finalized during implementation, but the contract must cover this shape:

- `GET /v1/workorders/{workorderId}/pick-list`
- `GET /v1/workorders/{workorderId}/pick-list/tasks`
- `POST /v1/workorders/{workorderId}/pick-tasks/{pickTaskId}:resolve-scan`
- `POST /v1/workorders/{workorderId}/pick-tasks/{pickTaskId}/lines/{pickLineId}:confirm`
- `POST /v1/workorders/{workorderId}/pick-tasks/{pickTaskId}:complete`
- `GET /v1/workorders/{workorderId}/picked-items`
- `POST /v1/workorders/{workorderId}/picked-items:consume`

Implementation may collapse some load routes if the pick-list response embeds tasks and lines, but the browser contract must make that explicit and stable.

### 6.5 Response Normalization Requirements

The workorder-facing facade must normalize inventory and workorder data into frontend-ready responses that include:

#### Pick List View
- `workorderId`
- `workorderReference`
- `pickListId`
- `status`
- `createdAt`
- `workorderPriority` when available
- `scheduledStartAt` / `dueAt` when available
- embedded tasks or a documented companion task route

#### Pick Task / Pick Line View
- `pickTaskId`
- `pickLineId` if lines are modeled separately in the facade
- `productSku`
- `productDisplayName`
- `requiredQty`
- `pickedQty`
- `remainingQty`
- `uom`
- `suggestedLocationId`
- `suggestedLocationLabel` or `storageLocationCode`
- `status`
- `version` or equivalent optimistic concurrency token when used

#### Picked Items for Consumption
- stable facade line identifier
- `pickTaskId` and/or inventory linkage ids where needed for traceability
- `productSku`
- `description`
- `qtyPicked`
- `qtyConsumed`
- `qtyRemaining`
- `uom`
- eligibility/status fields if the backend wants frontend disable rules to be deterministic

### 6.6 Picking Rules to Preserve

The backend build must preserve or document the currently established rules:
- WorkExec owns browser-facing orchestration.
- Inventory remains the raw pick-list/task owner.
- Partial picks are supported.
- Over-pick should be rejected unless an explicit later policy is introduced.
- Frontend must not compute route ordering; deterministic order must come from backend.
- Picking completion rules must be explicit:
  - whether completion with remainder is allowed
  - whether exceptions require a separate status/reason path

### 6.7 Consume Flow Requirements

`durion-positivity-backend#178` must be completed as a workorder-facing orchestration flow, not just local workorder part-usage tracking.

The canonical consume flow must:
- load picked items in a workorder-facing view
- validate quantities against picked/remaining quantities
- call inventory consumption server-to-server
- return a normalized success response the frontend can display
- keep workorder detail totals coherent after consumption

## 7. Security and Authorization

### Token Model
- Current-user permissions are carried in JWT token `authorities` claims.
- Backend guards must use canonical permission names that match the manifest/registry.

### Required Pick Permissions
- view/load pick-list/task routes: `inventory:pick_list:view`
- resolve/confirm/complete pick routes: `inventory:pick_list:execute`

### Consume Permission
- `#178` must explicitly document and implement the canonical permission for consume-picked-items.
- If existing workorder part-usage permissions are retained, they must be reconciled with the canonical inventory permission model and documented in the OpenAPI/issue notes.

## 8. Error Handling and Observability

### Error Contract
The workorder-facing facade must return deterministic API errors for:
- `400` / `422` validation failures
- `401` authentication failures
- `403` permission failures
- `404` missing workorder / pick list / pick task
- `409` optimistic concurrency or stale-state conflicts

Errors should include:
- machine-readable code
- human-readable message
- correlation id / request id when available

### Observability
- support correlation id pass-through
- log orchestration boundaries between `pos-workorder` and `pos-inventory`
- emit actionable logs for:
  - pick facade load failures
  - scan resolution failures
  - confirm failures
  - completion failures
  - consumption orchestration failures

## 9. Build Plan

### Phase 1 — Audit and Close Raw Inventory Contract Gaps
- Verify `#28` endpoints and OpenAPI are complete and current.
- Confirm the inventory response fields required by the facade are available or add them.
- Document any remaining inventory-side assumptions the workorder facade will depend on.

### Phase 2 — Build Workorder Pick View Facade
- Implement `pos-workorder` read endpoints for pick list/task retrieval by `workorderId`.
- Normalize workorder header context and inventory pick-task payloads into a frontend-ready response.
- Publish OpenAPI for the new facade routes.

### Phase 3 — Build Mechanic Picking Execution Facade
- Implement scan resolution route.
- Implement confirm-pick route.
- Implement complete-pick route.
- Add optimistic concurrency handling where needed.
- Add permission guards using canonical pick-list permission names.

### Phase 4 — Complete Consume Picked Items Facade
- Implement picked-items read route in `pos-workorder`.
- Complete the orchestrated consume route for `#178`.
- Ensure the flow calls inventory server-to-server instead of using browser-direct inventory commands.

### Phase 5 — Validation and Documentation
- Update `pos-workorder/openapi.yaml`.
- Add controller/service tests for all facade routes.
- Add contract/integration tests covering the orchestrated handoff to inventory.
- Update issue comments and CAP-218 run artifacts if route names or contracts change.

## 10. Acceptance Criteria

This backend PRD is complete when:

1. `pos-workorder` exposes a documented, browser-safe pick facade for CAP-218.
2. The pick facade covers load, scan, confirm, and complete actions.
3. `pos-workorder` exposes a documented picked-items facade and consume route for CAP-218.
4. `pos-inventory` remains the raw pick-list/task system of record without browser-direct dependence.
5. Canonical permission guards are documented and enforced.
6. OpenAPI is updated for all new or changed `pos-workorder` routes.
7. Tests pass for the affected modules and cover the orchestration boundary.
8. The resulting backend surface is sufficient to unblock frontend stories `#92`, `#243`, and `#244`.

## 11. Validation Commands

Minimum validation should include:

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-backend
./mvnw -pl pos-workorder,pos-inventory -am test
./mvnw -pl pos-workorder -am compile
./mvnw -pl pos-inventory -am compile
```

Targeted contract tests should be added for:
- workorder pick facade load
- scan resolution
- pick confirmation
- pick completion
- consume-picked-items facade orchestration

## 12. Risks and Open Decisions

### Risks
- Inventory and workorder models may not align cleanly for line identity.
- Existing `pos-workorder` consume endpoint semantics may conflict with the new orchestration contract.
- Pick line identity may require a new facade-level line model rather than reusing raw inventory ids directly.

### Open Decisions to Close During Build
- whether pick tasks are embedded in the pick-list response or fetched separately
- whether scan resolution is task-scoped or workorder-scoped
- whether completion with remainder is allowed
- canonical permission for consume-picked-items
- exact response shape for optimistic concurrency/versioning

## 13. Delivery Notes

This PRD intentionally treats `durion-positivity-backend#179` as the primary workorder-facing pick-facade tracker and `durion-positivity-backend#28` as the supporting inventory-state tracker. `durion-positivity-backend#178` completes the adjacent consume orchestration needed for the same fulfillment slice.
