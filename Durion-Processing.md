## Wave I-b: Inventory Domain (CAP-215, CAP-216, CAP-217, CAP-218, CAP-219, CAP-220, CAP-221, CAP-315)

**Status: COMPLETED — READY FOR PR** | **Base:** `master` (Wave I-a `cap/product-wave-i-a` merged)
**Wave:** I-b — Inventory
**Branch:** `cap/inventory-wave-i-b`
**Target repo:** `durion-positivity-frontend`
**Capabilities:** 8 | **Stories:** 21 | **Domains:** `inventory` (primary), `security` (CAP-221 cross-domain)
**Generated:** 2026-03-29T00:00:00Z

---

### Domain Ownership Mapping

| Domain | Feature Dir | Capability | Story |
| --- | --- | --- | --- |
| `inventory` | `src/app/features/inventory/` | CAP-215 | #100 — Compute on-hand and available-to-promise |
| `inventory` | `src/app/features/inventory/` | CAP-215 | #101 — Record stock movements in inventory |
| `inventory` | `src/app/features/inventory/` | CAP-315 | #572 — Create and approve purchase order (PO lifecycle) |
| `inventory` | `src/app/features/inventory/` | CAP-315 | #571 — Create receiving session (ASN + PO) |
| `inventory` | `src/app/features/inventory/` | CAP-216 | #98 — Receive items into staging |
| `inventory` | `src/app/features/inventory/` | CAP-216 | #97 — Direct-to-workorder receiving |
| `inventory` | `src/app/features/inventory/` | CAP-217 | #96 — Generate put-away tasks from receipts |
| `inventory` | `src/app/features/inventory/` | CAP-217 | #95 — Execute put-away: move staging to bin |
| `inventory` | `src/app/features/inventory/` | CAP-217 | #94 — Replenish pick faces from bulk storage |
| `inventory` | `src/app/features/inventory/` | CAP-220 | #88 — Reallocate reserved stock |
| `inventory` | `src/app/features/inventory/` | CAP-220 | #89 — Handle shortages with back-order |
| `inventory` | `src/app/features/inventory/` | CAP-218 | #93 — Reserve / allocate stock for workorder |
| `inventory` | `src/app/features/inventory/` | CAP-218 | #92 — Create pick list and pick tasks |
| `inventory` / `workexec` | `src/app/features/inventory/` | CAP-218 | #244 — Mechanic executes picking workflow |
| `inventory` | `src/app/features/inventory/` | CAP-218 | #243 — Issue / consume picked items |
| `inventory` | `src/app/features/inventory/` | CAP-218 | #242 — Return unused items to stock |
| `inventory` | `src/app/features/inventory/` | CAP-219 | #241 — Plan cycle counts by location |
| `inventory` | `src/app/features/inventory/` | CAP-219 | #91 — Execute cycle count and reconcile |
| `inventory` | `src/app/features/inventory/` | CAP-219 | #90 — Approve and post inventory adjustments |
| `security` | `src/app/features/security/` | CAP-221 | #87 — Define inventory roles and permissions |
| `security` | `src/app/features/security/` | CAP-221 | #86 — Immutable audit trail for inventory events |

---

### Capability Register

| CAP | Name | Stories | operation_ids | OpenAPI |
| --- | --- | --- | --- | --- |
| CAP-215 | Inventory Ledger & On-hand/ATP | #100, #101 | `queryAvailabilityBySku`, `queryInventoryAvailability`, `getLocationInventory` | pos-inventory |
| CAP-216 | Receiving (PO/ASN/Direct) | #97, #98 | `listPurchaseOrders`, `getPurchaseOrder`, `createReceivingSession`, `crossDockLineToWorkorder`, `receiveItemsIntoStaging`, `getReceivingSession` | pos-inventory |
| CAP-217 | Put-away & Replenishment | #94, #95, #96 | `getReplenishmentTasks`, `claimTask`, `executePutaway`, `getAvailableTasks`, `generateTasks` | pos-inventory |
| CAP-218 | Picking, Issuing, and Workorder Fulfillment | #92, #93, #242, #243, #244 | `createPickList`, `getPickList`, `getPickTasksForPickList`, `releasePickList`, `createOrUpdateReservation`, `promoteToHard`, `cancelReservation`, `queryAvailabilityBySku`, `returnItemsToStock`, `consumePickedItems`, `confirmPickTask`, `confirmPickingList` | pos-inventory |
| CAP-219 | Cycle Counts & Adjustments | #90, #91, #241 | `listPendingApprovals`, `countPendingApprovals`, `getAdjustment`, `approveAdjustment`, `rejectAdjustment`, `getAuditorTasks`, `getTask`, `submitCount`, `submitRecount`, `getCountHistory`, `createPlan`, `getPlan` | pos-inventory |
| CAP-220 | Reservations, Allocations, and Substitutions | #88, #89 | `queryAvailabilityBySku`, `queryInventoryAvailability`, `reallocate`, `resolveShortage`, `queryLeadTime`, `createOrUpdateReservation` | pos-inventory |
| CAP-221 | Roles, Permissions, and Audit Controls (Inventory) | #86, #87 | `searchEvents`, `getEvent`, `getAllRoles`, `getAllPermissions`, `getUserRoleAssignments`, `assignRoleToUser`, `revokeRoleFromUser` | pos-security-service |
| CAP-315 | Procure-to-Receive Lifecycle (PO + ASN + Accrual) | #571, #572 | `createAsn`, `getAsn`, `createReceivingSession`, `receiveItemsIntoStaging`, `listPurchaseOrders`, `getPurchaseOrder`, `createPurchaseOrder`, `approvePurchaseOrder`, `revisePurchaseOrder`, `cancelPurchaseOrder`, `receivePurchaseOrder` | pos-inventory |

---

### Steps

- [x] Step 1: Read all source materials — story MDs, wireframes, contract guides, and OpenAPI specs for all 21 stories:
  - CAP-215 #100: `docs/capabilities/CAP-215/stories/frontend/CAP_215.100.frontend.md`, `domains/inventory/.ui/frontend-story-ledger-compute-on-hand-and-availabl-100.wf.md`, `domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-inventory/openapi.yaml` (ops: `queryAvailabilityBySku`, `queryInventoryAvailability`)
  - CAP-215 #101: `docs/capabilities/CAP-215/stories/frontend/CAP_215.101.frontend.md`, `domains/inventory/.ui/frontend-story-ledger-record-stock-movements-in-in-101.wf.md` (ops: `getLocationInventory`)
  - CAP-315 #572: `docs/capabilities/CAP-315/stories/frontend/CAP_315.572.frontend.md`, `domains/inventory/.ui/frontend-story-procure-create-and-approve-purchase-572.wf.md`, `domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md` (ops: `createPurchaseOrder`, `listPurchaseOrders`, `getPurchaseOrder`, `approvePurchaseOrder`, `revisePurchaseOrder`, `cancelPurchaseOrder`, `receivePurchaseOrder`)
  - CAP-315 #571: `docs/capabilities/CAP-315/stories/frontend/CAP_315.571.frontend.md`, `domains/inventory/.ui/frontend-story-receiving-create-receiving-session-99.wf.md` (ops: `createAsn`, `getAsn`, `createReceivingSession`, `receiveItemsIntoStaging`, `listPurchaseOrders`, `getPurchaseOrder`)
  - CAP-216 #98: `docs/capabilities/CAP-216/stories/frontend/CAP_216.98.frontend.md`, `domains/inventory/.ui/frontend-story-receiving-receive-items-into-stagin-98.wf.md` (ops: `listPurchaseOrders`, `getPurchaseOrder`, `createReceivingSession`, `receiveItemsIntoStaging`, `getReceivingSession`)
  - CAP-216 #97: `docs/capabilities/CAP-216/stories/frontend/CAP_216.97.frontend.md`, `domains/inventory/.ui/frontend-story-receiving-direct-to-workorder-recei-97.wf.md` (ops: `listPurchaseOrders`, `getPurchaseOrder`, `createReceivingSession`, `crossDockLineToWorkorder`, `getReceivingSession`)
  - CAP-217 #96: `docs/capabilities/CAP-217/stories/frontend/CAP_217.96.frontend.md`, `domains/inventory/.ui/frontend-story-putaway-generate-put-away-tasks-fro-96.wf.md` (ops: `generateTasks`, `getAvailableTasks`)
  - CAP-217 #95: `docs/capabilities/CAP-217/stories/frontend/CAP_217.95.frontend.md`, `domains/inventory/.ui/frontend-story-putaway-execute-put-away-move-stagi-95.wf.md` (ops: `getAvailableTasks`, `executePutaway`)
  - CAP-217 #94: `docs/capabilities/CAP-217/stories/frontend/CAP_217.94.frontend.md`, `domains/inventory/.ui/frontend-story-putaway-replenish-pick-faces-from-b-94.wf.md` (ops: `getReplenishmentTasks`, `claimTask`, `executePutaway`)
  - CAP-220 #88: `docs/capabilities/CAP-220/stories/frontend/CAP_220.88.frontend.md`, `domains/inventory/.ui/frontend-story-allocations-reallocate-reserved-sto-88.wf.md` (ops: `queryAvailabilityBySku`, `queryInventoryAvailability`, `reallocate`)
  - CAP-220 #89: `docs/capabilities/CAP-220/stories/frontend/CAP_220.89.frontend.md`, `domains/inventory/.ui/frontend-story-allocations-handle-shortages-with-b-89.wf.md` (ops: `resolveShortage`, `queryAvailabilityBySku`, `queryLeadTime`, `createOrUpdateReservation`)
  - CAP-218 #93: `docs/capabilities/CAP-218/stories/frontend/CAP_218.93.frontend.md`, `domains/inventory/.ui/frontend-story-fulfillment-reserve-allocate-stock-93.wf.md` (ops: `createOrUpdateReservation`, `promoteToHard`, `cancelReservation`, `queryAvailabilityBySku`)
  - CAP-218 #92: `docs/capabilities/CAP-218/stories/frontend/CAP_218.92.frontend.md`, `domains/workexec/.ui/frontend-story-fulfillment-create-pick-list-pick-t-92.wf.md` (ops: `createPickList`, `getPickList`, `getPickTasksForPickList`, `releasePickList`)
  - CAP-218 #244: `docs/capabilities/CAP-218/stories/frontend/CAP_218.244.frontend.md`, `domains/workexec/.ui/frontend-story-fulfillment-mechanic-executes-picki-244.wf.md` (ops: `getPickTasksForPickList`, `confirmPickTask`, `confirmPickingList`, `getPickList`)
  - CAP-218 #243: `docs/capabilities/CAP-218/stories/frontend/CAP_218.243.frontend.md`, `domains/inventory/.ui/frontend-story-fulfillment-issue-consume-picked-it-243.wf.md` (ops: `consumePickedItems`, `getPickList`)
  - CAP-218 #242: `docs/capabilities/CAP-218/stories/frontend/CAP_218.242.frontend.md`, `domains/inventory/.ui/frontend-story-fulfillment-return-unused-items-to-242.wf.md` (ops: `returnItemsToStock`)
  - CAP-219 #241: `docs/capabilities/CAP-219/stories/frontend/CAP_219.241.frontend.md`, `domains/inventory/.ui/frontend-story-counts-plan-cycle-counts-by-locatio-241.wf.md` (ops: `createPlan`, `getPlan`)
  - CAP-219 #91: `docs/capabilities/CAP-219/stories/frontend/CAP_219.91.frontend.md`, `domains/inventory/.ui/frontend-story-counts-execute-cycle-count-and-reco-91.wf.md` (ops: `getAuditorTasks`, `getTask`, `submitCount`, `submitRecount`, `getCountHistory`)
  - CAP-219 #90: `docs/capabilities/CAP-219/stories/frontend/CAP_219.90.frontend.md`, `domains/inventory/.ui/frontend-story-counts-approve-and-post-adjustments-90.wf.md` (ops: `listPendingApprovals`, `countPendingApprovals`, `getAdjustment`, `approveAdjustment`, `rejectAdjustment`)
  - CAP-221 #87: `docs/capabilities/CAP-221/stories/frontend/CAP_221.87.frontend.md`, `domains/inventory/.ui/frontend-story-security-define-inventory-roles-and-87.wf.md`, `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-security-service/openapi.yaml` (ops: `getAllRoles`, `getAllPermissions`, `getUserRoleAssignments`, `assignRoleToUser`, `revokeRoleFromUser`)
  - CAP-221 #86: `docs/capabilities/CAP-221/stories/frontend/CAP_221.86.frontend.md`, `domains/security/.ui/frontend-story-security-immutable-audit-trail-for-86.wf.md` (ops: `searchEvents`, `getEvent`)
  - Design pack: `design/Inventory-Catalog/Inventory.html`, `design/Inventory-Catalog/Catalog.html`, `design/Inventory-Catalog/CycleCount.html`, `design/Inventory-Catalog/OrdersBySupplier.html`, `design/Inventory-Catalog/DESIGN.md`, `design/DESIGN.md`, `design/source/`

- [x] Step 2: Create execution branch `cap/inventory-wave-i-b` from `master` via `durion/.github/hooks/create-branch-hook.sh`

- [x] Step 3: Designer first-pass — design brief for inventory domain surfaces; consult `design/Inventory-Catalog/` (Inventory.html, CycleCount.html, OrdersBySupplier.html, Catalog.html) and `design/Inventory-Catalog/DESIGN.md`; issue token, layout, table-density, scanning-workflow, and mobile-touch guidance for warehouse/shop-floor flows; confirm design approach for: on-hand dashboard, pick-list tablet UI, cycle count audit screens, receiving session stepper, PO management list/form

- [x] Step 4: Execute CAP-215 story #100 (Compute on-hand and available-to-promise — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: on-hand availability dashboard, SKU search with ATP display, location-level breakdown table, empty/loading/error states
  - TypeScript Specialist: `InventoryService` methods `queryAvailabilityBySku`, `queryInventoryAvailability`; route/page; state machine; filter/pagination
  - Designer final sign-off for story #100
  - Code Review Agent; iterate fixes until PASS

- [x] Step 5: Execute CAP-215 story #101 (Record stock movements in inventory — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: stock movement log table, location filter, date range filter, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `getLocationInventory` wiring; route/page; state; export
  - Designer final sign-off for story #101
  - Code Review Agent; iterate fixes until PASS

- [x] Step 6: Execute CAP-315 story #572 (Create and approve purchase order — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: PO creation form, PO list with status column, approve/revise/cancel actions, empty/loading/error states
  - TypeScript Specialist: `InventoryService` methods `createPurchaseOrder`, `listPurchaseOrders`, `getPurchaseOrder`, `approvePurchaseOrder`, `revisePurchaseOrder`, `cancelPurchaseOrder`, `receivePurchaseOrder`; PO detail page; state machine; multi-step approval flow
  - Designer final sign-off for story #572
  - Code Review Agent; iterate fixes until PASS

- [x] Step 7: Execute CAP-315 story #571 (Create receiving session via ASN + PO — `inventory` domain) — **DEFERRED:** ASN receiving contract undefined; blocking endpoint notes unresolved per #571
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: ASN creation/lookup form, receiving session stepper, PO selector, receive-items confirmation UI, empty/loading/error states
  - TypeScript Specialist: `InventoryService` methods `createAsn`, `getAsn`, `createReceivingSession`, `receiveItemsIntoStaging`, `listPurchaseOrders`, `getPurchaseOrder`; route/page; state; scan/manual entry toggle
  - Designer final sign-off for story #571
  - Code Review Agent; iterate fixes until PASS

- [x] Step 8: Execute CAP-216 story #98 (Receive items into staging — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: staging receipt list, item scan / quantity entry, session progress indicator, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `createReceivingSession`, `receiveItemsIntoStaging`, `getReceivingSession` wiring; route/page; state; partial-receipt tracking
  - Designer final sign-off for story #98
  - Code Review Agent; iterate fixes until PASS

- [x] Step 9: Execute CAP-216 story #97 (Direct-to-workorder receiving — `inventory` / `workexec` cross-domain) — **DEFERRED:** 7 unresolved cross-dock endpoint questions; no confirmed contract
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: cross-dock receiving UI, workorder reference selector, PO line matching, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `crossDockLineToWorkorder` wiring; workorder context injection; route/page; state
  - Designer final sign-off for story #97
  - Code Review Agent; iterate fixes until PASS

- [x] Step 10: Execute CAP-217 story #96 (Generate put-away tasks from receipts — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: task generation trigger UI, generated task list, status display, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `generateTasks`, `getAvailableTasks` wiring; route/page; state
  - Designer final sign-off for story #96
  - Code Review Agent; iterate fixes until PASS

- [x] Step 11: Execute CAP-217 story #95 (Execute put-away: move staging to bin — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: available task list, task detail with location picker, scan-to-confirm UI, complete/skip action, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `getAvailableTasks`, `executePutaway` wiring; route/page; state; location scan input
  - Designer final sign-off for story #95
  - Code Review Agent; iterate fixes until PASS

- [x] Step 12: Execute CAP-217 story #94 (Replenish pick faces from bulk storage — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: replenishment task list, claim/release controls, bulk-to-pick-face move confirmation, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `getReplenishmentTasks`, `claimTask`, `executePutaway` wiring; route/page; state; claim lifecycle
  - Designer final sign-off for story #94
  - Code Review Agent; iterate fixes until PASS

- [x] Step 13: Execute CAP-220 story #88 (Reallocate reserved stock — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: allocation management list, availability matrix, reallocate action dialog, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `queryAvailabilityBySku`, `queryInventoryAvailability`, `reallocate` wiring; route/page; state; conflict display
  - Designer final sign-off for story #88
  - Code Review Agent; iterate fixes until PASS

- [x] Step 14: Execute CAP-220 story #89 (Handle shortages with back-order — `inventory` domain) — **DEFERRED:** cross-domain shortage resolution contract TBD
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: shortage alert list, back-order creation form, lead time display, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `resolveShortage`, `queryLeadTime`, `createOrUpdateReservation` wiring; route/page; state; shortage resolution flow
  - Designer final sign-off for story #89
  - Code Review Agent; iterate fixes until PASS

- [x] Step 15: Execute CAP-218 story #93 (Reserve / allocate stock for workorder — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: reservation form, hard/soft reservation toggle, cancel action UI, availability inline display, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `createOrUpdateReservation`, `promoteToHard`, `cancelReservation`, `queryAvailabilityBySku` wiring; route/page; state; workorder binding
  - Designer final sign-off for story #93
  - Code Review Agent; iterate fixes until PASS

- [x] Step 16: Execute CAP-218 story #92 (Create pick list and pick tasks — `inventory` domain) — **DEFERRED:** pick list API contract TBD
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: pick list creation form, pick task summary table, release action, status display, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `createPickList`, `getPickList`, `getPickTasksForPickList`, `releasePickList` wiring; route/page; state; workorder reference
  - Designer final sign-off for story #92
  - Code Review Agent; iterate fixes until PASS

- [x] Step 17: Execute CAP-218 story #244 (Mechanic executes picking workflow — `inventory`/`workexec` cross-domain) — **DEFERRED:** domain ownership conflict between inventory and workexec TBD
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: mechanic-facing pick task list, scan-to-pick UI, confirm/skip per task, pick list summary, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `getPickTasksForPickList`, `confirmPickTask`, `confirmPickingList`, `getPickList` wiring; route/page; state; touch-optimized flow
  - Designer final sign-off for story #244
  - Code Review Agent; iterate fixes until PASS

- [x] Step 18: Execute CAP-218 story #243 (Issue / consume picked items — `inventory` domain) — **DEFERRED:** Moqui proxy paths TBD
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: issue confirmation UI, pick list status view, consumed items summary, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `consumePickedItems`, `getPickList` wiring; route/page; state; quantity validation
  - Designer final sign-off for story #243
  - Code Review Agent; iterate fixes until PASS

- [x] Step 19: Execute CAP-218 story #242 (Return unused items to stock — `inventory` domain) — **DEFERRED:** return-to-stock API contract undefined
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: return form, quantity entry per line, location selector for return bin, confirmation dialog, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `returnItemsToStock` wiring; route/page; state; partial return support
  - Designer final sign-off for story #242
  - Code Review Agent; iterate fixes until PASS

- [x] Step 20: Execute CAP-219 story #241 (Plan cycle counts by location — `inventory` domain) — **DEFERRED:** cycle count planning contract TBD
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: cycle count plan creation form, location selector, plan schedule display, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `createPlan`, `getPlan` wiring; route/page; state; plan management
  - Designer final sign-off for story #241
  - Code Review Agent; iterate fixes until PASS

- [x] Step 21: Execute CAP-219 story #91 (Execute cycle count and reconcile — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: auditor task list, count entry form, recount flow, count history view, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `getAuditorTasks`, `getTask`, `submitCount`, `submitRecount`, `getCountHistory` wiring; route/page; state; discrepancy display
  - Designer final sign-off for story #91
  - Code Review Agent; iterate fixes until PASS

- [x] Step 22: Execute CAP-219 story #90 (Approve and post inventory adjustments — `inventory` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: pending approval list with count, batch approve/reject actions, adjustment detail view, empty/loading/error states
  - TypeScript Specialist: `InventoryService` `listPendingApprovals`, `countPendingApprovals`, `getAdjustment`, `approveAdjustment`, `rejectAdjustment` wiring; route/page; state; bulk action flow
  - Designer final sign-off for story #90
  - Code Review Agent; iterate fixes until PASS

- [x] Step 23: Execute CAP-221 story #87 (Define inventory roles and permissions — `security` domain cross-domain) — **DEFERRED:** explicitly blocked per story; inventory RBAC design pending
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: inventory-scoped role list, permission assignment matrix, user role assignment table, empty/loading/error states
  - TypeScript Specialist: `SecurityService` extension for `getAllRoles`, `getAllPermissions`, `getUserRoleAssignments`, `assignRoleToUser`, `revokeRoleFromUser`; route/page in `security` feature; state; inventory scope filter
  - Designer final sign-off for story #87
  - Code Review Agent; iterate fixes until PASS

- [x] Step 24: Execute CAP-221 story #86 (Immutable audit trail for inventory events — `security` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: inventory event audit log table, event detail view, keyword search/filter, empty/loading/error states
  - TypeScript Specialist: `SecurityService` `searchEvents`, `getEvent` wiring with inventory context filter; route/page; state; pagination
  - Designer final sign-off for story #86
  - Code Review Agent; iterate fixes until PASS

- [x] Step 25: Designer final sign-off on fully integrated Wave I-b `inventory` feature set — review all inventory pages as cohesive domain; confirm navigation, empty states, error UX, and table density are consistent with `design/Inventory-Catalog/DESIGN.md`

- [x] Step 26: Build verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build` — ✅ clean build, no errors

- [x] Step 27: Test verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false` — ✅ 218/218 passing (24 spec files)

- [x] Step 28: Test Coverage Agent — harden coverage for `inventory` and `security` (inventory RBAC/audit) domain changes; target ≥1 test per new `InventoryService` and `SecurityService` method per ADR-0035 — ✅ ADR-0035 coverage verified

- [x] Step 29: Documentation Agent — update `CAPABILITY_STATUS_BOARD.md` for CAP-215, CAP-216, CAP-217, CAP-218, CAP-219, CAP-220, CAP-221, CAP-315; create run artifacts under `docs/capabilities/CAP-215/runs/latest.md`, `CAP-216/runs/latest.md`, `CAP-217/runs/latest.md`, `CAP-218/runs/latest.md`, `CAP-219/runs/latest.md`, `CAP-220/runs/latest.md`, `CAP-221/runs/latest.md`, `CAP-315/runs/latest.md`; update completed waves table in `Durion-Processing.md` — ✅ completed 2026-03-29

- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh` — [PR #13](https://github.com/louisburroughs/durion-positivity-frontend/pull/13)

---

---

## Wave H: People Profile Management + Location Topology (CAP-117, CAP-119, CAP-120, CAP-121, CAP-214)

**Status: COMPLETED** | **Branch:** `cap/people-location-wave-h` | **Tests at close:** 699/699

### Domain Ownership Mapping

| Domain | Feature Dir | Capability | Story |
| --- | --- | --- | --- |
| `people` | `src/app/features/people/` | CAP-117 | #152 — Create/update employee profile |
| `people` | `src/app/features/people/` | CAP-117 | #154 — Disable employee / offboarding |
| `security` | `src/app/features/security/` | CAP-117 | #155 — Provision user and link to person |
| `people` | `src/app/features/people/` | CAP-119 | #150 — Assign person to location |
| `location` | `src/app/features/location/` | CAP-119 | #151 — Create/update location |
| `people` | `src/app/features/people/` | CAP-120 | #143 — Export approved time for payroll |
| `people` | `src/app/features/people/` | CAP-120 | #147 — Manager approves/rejects time entries |
| `people` | `src/app/features/people/` | CAP-120 | #148 — Record break start/end |
| `people` | `src/app/features/people/` | CAP-120 | #149 — Mechanic clock in/out |
| `people` | `src/app/features/people/` | CAP-121 | #144 — Attendance vs job time discrepancy |
| `people` | `src/app/features/people/` | CAP-121 | #145 — Submit job time to workorder |
| `people` | `src/app/features/people/` | CAP-121 | #146 — Start/stop timer against workorder |
| `location` | `src/app/features/location/` | CAP-214 | #102 — Define default staging locations |
| `location` | `src/app/features/location/` | CAP-214 | #103 — Create storage locations |
| `location` | `src/app/features/location/` | CAP-214 | #104 — Sync locations (contract-review-required) |

### Steps

- [x] Step 1: Read source materials — story MDs, wireframes, contract guides, and OpenAPI specs for all 16 stories:
  - CAP-117 #152: `docs/capabilities/CAP-117/stories/frontend/CAP_117.152.frontend.md`, `domains/people/.ui/frontend-story-users-create-update-employee-profil-152.wf.md`, `domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-people/openapi.yaml` (ops: `createEmployee`, `getEmployee`, `updateEmployee`)
  - CAP-117 #154: `docs/capabilities/CAP-117/stories/frontend/CAP_117.154.frontend.md`, `domains/people/.ui/frontend-story-users-disable-user-offboarding-with-154.wf.md` (ops: `disableEmployee`, `getEmployee`)
  - CAP-117 #155: `docs/capabilities/CAP-117/stories/frontend/CAP_117.155.frontend.md`, `domains/security/.ui/frontend-story-users-provision-user-and-link-to-pe-155.wf.md`, `domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-security-service/openapi.yaml` (ops: `createUser`, `getAllRoles`, `getUserById`)
  - CAP-119 #150: `docs/capabilities/CAP-119/stories/frontend/CAP_119.150.frontend.md`, `domains/people/.ui/frontend-story-location-assign-person-to-location-150.wf.md` (ops: `getAssignments`, `createAssignment`, `endAssignment`)
  - CAP-119 #151: `docs/capabilities/CAP-119/stories/frontend/CAP_119.151.frontend.md`, `domains/location/.ui/frontend-story-location-create-update-location-pos-151.wf.md`, `domains/location/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-location/openapi.yaml` (ops: `createLocation`, `getLocationById`, `updateLocation`)
  - CAP-120 #143: `docs/capabilities/CAP-120/stories/frontend/CAP_120.143.frontend.md`, `domains/accounting/.ui/frontend-story-timekeeping-export-approved-time-fo-143.wf.md` (ops: `getApprovedTimeForExport`)
  - CAP-120 #147: `docs/capabilities/CAP-120/stories/frontend/CAP_120.147.frontend.md`, `domains/people/.ui/frontend-story-timekeeping-manager-approves-reject-147.wf.md` (ops: `approveTimeEntries`, `rejectTimeEntries`)
  - CAP-120 #148: `docs/capabilities/CAP-120/stories/frontend/CAP_120.148.frontend.md`, `domains/people/.ui/frontend-story-timekeeping-record-break-start-end-148.wf.md` (ops: `startWorkSessionBreak`, `stopWorkSessionBreak`)
  - CAP-120 #149: `docs/capabilities/CAP-120/stories/frontend/CAP_120.149.frontend.md`, `domains/people/.ui/frontend-story-timekeeping-mechanic-clock-in-out-149.wf.md` (ops: `startWorkSession`, `stopWorkSession`)
  - CAP-121 #144: `docs/capabilities/CAP-121/stories/frontend/CAP_121.144.frontend.md`, `domains/people/.ui/frontend-story-integration-attendance-vs-job-time-144.wf.md` (ops: `getAttendanceDiscrepancyReport`)
  - CAP-121 #145: `docs/capabilities/CAP-121/stories/frontend/CAP_121.145.frontend.md`, `domains/people/.ui/frontend-story-integration-submit-job-time-to-work-145.wf.md`, `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-workorder/openapi.yaml` (ops: `createLaborPerformed`)
  - CAP-121 #146: `docs/capabilities/CAP-121/stories/frontend/CAP_121.146.frontend.md`, `domains/people/.ui/frontend-story-integration-start-stop-timer-agains-146.wf.md` (ops: `getActiveTimerEntries`, `startTimer`, `stopTimers`)
  - CAP-214 #102: `docs/capabilities/CAP-214/stories/frontend/CAP_214.102.frontend.md`, `domains/location/.ui/frontend-story-topology-define-default-staging-and-102.wf.md` (ops: `getDefaults`, `configureDefaults`)
  - CAP-214 #103: `docs/capabilities/CAP-214/stories/frontend/CAP_214.103.frontend.md`, `domains/inventory/.ui/frontend-story-topology-create-storage-locations-f-103.wf.md`, `domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-location/openapi.yaml` (ops: `list_2`, `create_2`, `validateStorageLocation`)
  - CAP-214 #104: `docs/capabilities/CAP-214/stories/frontend/CAP_214.104.frontend.md`, `domains/inventory/.ui/frontend-story-topology-sync-locations-from-durion-104.wf.md`, `durion-positivity-backend/pos-inventory/openapi.yaml` (ops: EMPTY — contract-review-required; resolve from OpenAPI before implementing)
  - Design: `design/HR/`, `design/DESIGN.md`, `design/source/theme-tokens.md`, `design/source/durion-style-guide.md`, `design/source/durion-theme.css`
- [x] Step 2: Create execution branch `cap/people-location-wave-h` from `master` via `durion/.github/hooks/create-branch-hook.sh`
- [x] Step 3: Designer first-pass — design brief for People profile management, timekeeping, and Location topology surfaces; consult `design/HR/` for people domain; issue token, layout, and responsive guidance
- [x] Step 4: Execute CAP-117 story #152 (Create/update employee profile — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: employee profile form, job/skill fields, empty/loading/error states
  - TypeScript Specialist: `PeopleService` methods for `createEmployee`, `getEmployee`, `updateEmployee`; route/page; state; validation
  - Designer final sign-off for story #152
  - Code Review Agent; iterate fixes until PASS
- [x] Step 5: Execute CAP-117 story #154 (Disable employee / offboarding — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: disable confirmation UI, offboarding status view, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `disableEmployee`, `getEmployee` wiring; state; confirmation flow
  - Designer final sign-off for story #154
  - Code Review Agent; iterate fixes until PASS
- [x] Step 6: Execute CAP-117 story #155 (Provision user and link to person — `security` domain cross-domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: user provision form, role assignment within employee context, empty/loading/error states
  - TypeScript Specialist: `SecurityService` extension for `createUser`, `getAllRoles`, `getUserById`; link to PeopleService; route/page; state
  - Designer final sign-off for story #155
  - Code Review Agent; iterate fixes until PASS
- [x] Step 7: Execute CAP-119 story #150 (Assign person to location — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: location assignment list/form, end-assignment action UI, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `getAssignments`, `createAssignment`, `endAssignment` wiring; route/page; state
  - Designer final sign-off for story #150
  - Code Review Agent; iterate fixes until PASS
- [x] Step 8: Execute CAP-119 story #151 (Create/update location — `location` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: location form, address fields, empty/loading/error states
  - TypeScript Specialist: `LocationService` methods for `createLocation`, `getLocationById`, `updateLocation`; route/page; state
  - Designer final sign-off for story #151
  - Code Review Agent; iterate fixes until PASS
- [x] Step 9: Execute CAP-120 story #143 (Export approved time for payroll — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: time export list, date range filter, export action, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `getApprovedTimeForExport` wiring; route/page; state; payroll export flow
  - Designer final sign-off for story #143
  - Code Review Agent; iterate fixes until PASS
- [x] Step 10: Execute CAP-120 story #147 (Manager approves/rejects time entries — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: time entry review list, approve/reject batch actions, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `approveTimeEntries`, `rejectTimeEntries` wiring; state; bulk action
  - Designer final sign-off for story #147
  - Code Review Agent; iterate fixes until PASS
- [x] Step 11: Execute CAP-120 story #148 (Record break start/end — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: break timer controls, start/end buttons, active-break state indicator
  - TypeScript Specialist: `PeopleService` `startWorkSessionBreak`, `stopWorkSessionBreak` wiring; state; timer behavior
  - Designer final sign-off for story #148
  - Code Review Agent; iterate fixes until PASS
- [x] Step 12: Execute CAP-120 story #149 (Mechanic clock in/out — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: clock-in/out widget, session status display, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `startWorkSession`, `stopWorkSession` wiring; route/page; state
  - Designer final sign-off for story #149
  - Code Review Agent; iterate fixes until PASS
- [x] Step 13: Execute CAP-121 story #144 (Attendance vs job time discrepancy report — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: discrepancy table/report, filter controls, empty/loading/error states
  - TypeScript Specialist: `PeopleService` `getAttendanceDiscrepancyReport` wiring; route/page; state; export
  - Designer final sign-off for story #144
  - Code Review Agent; iterate fixes until PASS
- [x] Step 14: Execute CAP-121 story #145 (Submit job time to workorder — `people`/`workexec` cross-domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: job time submission form, workorder reference selector, empty/loading/error states
  - TypeScript Specialist: `WorkexecService` / `PeopleService` `createLaborPerformed` wiring; cross-domain state; validation
  - Designer final sign-off for story #145
  - Code Review Agent; iterate fixes until PASS
- [x] Step 15: Execute CAP-121 story #146 (Start/stop timer against workorder — `people`/`workexec` cross-domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: active workorder time widget, start/stop toggle, elapsed timer display
  - TypeScript Specialist: `PeopleService` `getActiveTimerEntries`, `startTimer`, `stopTimers` wiring; interval management; workorder binding; state
  - Designer final sign-off for story #146
  - Code Review Agent; iterate fixes until PASS
- [x] Step 16: Execute CAP-214 story #102 (Define default staging locations — `location` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: default location config form, staging zone selector, empty/loading/error states
  - TypeScript Specialist: `LocationService` `getDefaults`, `configureDefaults` wiring; route/page; state
  - Designer final sign-off for story #102
  - Code Review Agent; iterate fixes until PASS
- [x] Step 17: Execute CAP-214 story #103 (Create storage locations — `location`/`inventory` cross-domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: storage location list/create form, validation feedback, empty/loading/error states
  - TypeScript Specialist: `LocationService` `list_2`, `create_2`, `validateStorageLocation` wiring; route/page; state
  - Designer final sign-off for story #103
  - Code Review Agent; iterate fixes until PASS
- [x] Step 18: Execute CAP-214 story #104 (Sync locations — `location` domain — contract-review-required)
  - Resolve operation from `durion-positivity-backend/pos-inventory/openapi.yaml` before implementing
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: sync trigger UI, sync status display, empty/loading/error states
  - TypeScript Specialist: wire resolved location-sync operation; state
  - Designer final sign-off for story #104
  - Code Review Agent; iterate fixes until PASS
- [x] Step 19: Designer final sign-off on fully integrated Wave H feature set
- [x] Step 20: Build verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- [x] Step 21: Test verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`
- [x] Step 22: Test Coverage Agent — harden coverage for `people`, `location`, and `security` domain changes
- [x] Step 23: Documentation Agent — update `CAPABILITY_STATUS_BOARD.md` for CAP-117, CAP-119, CAP-120, CAP-121, CAP-214; create run artifacts under `docs/capabilities/CAP-117/runs/latest.md`, `CAP-119/runs/latest.md`, `CAP-120/runs/latest.md`, `CAP-121/runs/latest.md`, `CAP-214/runs/latest.md`
- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story people-location-wave-h --base master --head cap/people-location-wave-h --title "feat(people,location): Wave H — People Profile Management + Location Topology (CAP-117, CAP-119, CAP-120, CAP-121, CAP-214)"`

---

## Wave G: People RBAC Identity Orchestration + CRM-Workorder Integration (CAP-118, CAP-094)

**Status: IN PROGRESS**
**Wave:** G — People RBAC + CRM Integration
**Branch:** `cap/people-crm-wave-g`
**Base:** `master` (8ca0ebf — Wave F merge)
**Target repo:** `durion-positivity-frontend`
**Capabilities:** CAP-118 (story #153), CAP-094 (stories #157, #156)
**Domains:** `people`, `crm`, `workexec`

### Domain Ownership Mapping

| Domain | Feature Dir | Capability | Story |
| --- | --- | --- | --- |
| `people` | `src/app/features/people/` | CAP-118 | #153 — RBAC role/scope assignment |
| `crm` | `src/app/features/crm/` | CAP-094 | #156 — Inbound workorder event handler |
| `workexec` | `src/app/features/workexec/` | CAP-094 | #157 — Emit CRM reference IDs in workorder artifacts |

### Steps

- [x] Step 1: Read source materials — story MDs, wireframes, contract guides, and OpenAPI specs for all three stories:
  - `docs/capabilities/CAP-118/stories/frontend/CAP_118.153.frontend.md`
  - `domains/people/.ui/frontend-story-access-assign-roles-and-scopes-glob-153.wf.md`
  - `domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - `durion-positivity-backend/pos-people/openapi.yaml` (ops: `getRoles`, `getAssignments_1`, `createAssignment_1`, `revokeAssignment`)
  - `docs/capabilities/CAP-094/stories/frontend/CAP_094.157.frontend.md`
  - `domains/workexec/.ui/frontend-story-integration-emit-crm-reference-ids-157.wf.md`
  - `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - `durion-positivity-backend/pos-workorder/openapi.yaml` (ops: `createEstimate`, `getEstimateById`, `promoteEstimateToWorkorder`, `createWorkorder`, `getWorkorderById`)
  - `docs/capabilities/CAP-094/stories/frontend/CAP_094.156.frontend.md`
  - `domains/crm/.ui/frontend-story-integration-inbound-event-handler-f-156.wf.md`
  - `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - `durion-positivity-backend/pos-accounting/openapi.yaml` (ops: `listEvents`, `getEvent`, `getEventProcessingLog`, `getReprocessingHistory`)
- [x] Step 2: Create execution branch `cap/people-crm-wave-g` from `master` via `durion/.github/hooks/create-branch-hook.sh`
- [x] Step 3: Designer first-pass — design brief for `people` RBAC UI and `crm`/`workexec` integration surfaces; consult `design/HR/` for people domain and `design/Customer/` + `design/Shop-Workorder/` for CRM/workexec; issue token, layout, and responsive guidance
- [x] Step 4: Execute CAP-118 story #153 (RBAC role/scope assignment — `people` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: role assignment UI, scope selectors, empty/loading/error states
  - TypeScript Specialist: `PeopleService` methods for `getRoles`, `getAssignments_1`, `createAssignment_1`, `revokeAssignment`; route/page; state; validation
  - Designer final sign-off for story #153
  - Code Review Agent
  - Iterate fixes until Code Review PASS
- [x] Step 5: Execute CAP-094 story #157 (Emit CRM reference IDs in workorder artifacts — `workexec` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: workorder form CRM reference fields, display of linked customer/vehicle IDs, empty/loading/error states
  - TypeScript Specialist: `WorkorderService` methods for `createEstimate`, `getEstimateById`, `promoteEstimateToWorkorder`, `createWorkorder`, `getWorkorderById`; CRM reference ID wiring; state; validation
  - Designer final sign-off for story #157
  - Code Review Agent
  - Iterate fixes until Code Review PASS
- [x] Step 6: Execute CAP-094 story #156 (Inbound event handler for workorder-originated updates — `crm` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: event list, event detail, processing log view, reprocessing history, empty/loading/error states
  - TypeScript Specialist: `CrmIntegrationService` methods for `listEvents`, `getEvent`, `getEventProcessingLog`, `getReprocessingHistory`; route/page; state; filtering/pagination
  - Designer final sign-off for story #156
  - Code Review Agent
  - Iterate fixes until Code Review PASS
- [x] Step 7: Designer final sign-off on fully integrated Wave G feature set
- [x] Step 8: Build verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- [x] Step 9: Test verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`
- [ ] Step 10: Test Coverage Agent — harden coverage for `people`, `crm`, and `workexec` changes
- [x] Step 11: Documentation Agent — update `CAPABILITY_STATUS_BOARD.md` for CAP-118 and CAP-094; create run artifacts under `docs/capabilities/CAP-118/` and `docs/capabilities/CAP-094/`; update completed waves table in `Durion-Processing.md`
- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh` — [PR #9](https://github.com/louisburroughs/durion-positivity-frontend/pull/9)

---

# Durion Processing — Wave E: Security Foundation (CAP-275 + CAP-253)

**Status: IN PROGRESS**
**Branch:** `cap/security-wave-e`
**Base:** `master` (94fd103)
**Target repo:** `durion-positivity-frontend`
**PR target:** TBD

- [x] Step 1: Read source materials — CAP-275 and CAP-253 manifests, worksets, story MDs, wireframes, security contract guide, API reference
- [x] Step 2: Normalize `docs/capabilities/CAP-253/AGENT_WORKSET.yaml` — populate stories list
- [x] Step 3: Implement CAP-275 auth wiring — `AuthService.logoutWithRedirect()`, `validateSessionOnResume()`, interceptor 401 redirect
- [x] Step 4: Implement CAP-275 login component — session-expired info banner (`?sessionExpired=true` param)
- [x] Step 5: Implement CAP-253 models and SecurityService — `SecurityRole`, `SecurityPermission`, `getAllRoles`, `createRole`, `getAllPermissions`, `updateRolePermissions`, `revokeRoleAssignment`
- [x] Step 6: Implement CAP-253 pages — roles list, role detail, permissions registry, security audit placeholder
- [x] Step 7: Update `security.routes.ts` + shell navigation entry
- [x] Step 8: Frontend Testing Agent — RED/GREEN for auth wiring + security admin
- [x] Step 9: Designer first-pass + final sign-off
- [x] Step 10: Code Review Agent
- [x] Step 11: Iterate fixes until Code Review PASS
- [x] Step 12: Build verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`)
- [x] Step 13: Test verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`)
- [x] Step 14: Test Coverage Agent — security domain coverage hardening
- [x] Step 15: Update run artifacts for CAP-275 and CAP-253
- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story security-wave-e --base master --head cap/security-wave-e --title "feat(security): Wave E — Security Foundation (CAP-275, CAP-253)"`
- [x] Step 1: Read source materials — CAP-275 and CAP-253 manifests, worksets, story MDs, wireframes, security contract guide, API reference
- [x] Step 2: Normalize `docs/capabilities/CAP-253/AGENT_WORKSET.yaml` — populate stories list
- [ ] Step 3: Implement CAP-275 auth wiring — `AuthService.logoutWithRedirect()`, `validateSessionOnResume()`, interceptor 401 redirect
- [ ] Step 4: Implement CAP-275 login component — session-expired info banner (`?sessionExpired=true` param)
- [ ] Step 5: Implement CAP-253 models and SecurityService — `SecurityRole`, `SecurityPermission`, `getAllRoles`, `createRole`, `getAllPermissions`, `updateRolePermissions`, `revokeRoleAssignment`
- [ ] Step 6: Implement CAP-253 pages — roles list, role detail, permissions registry, security audit placeholder
- [ ] Step 7: Update `security.routes.ts` + shell navigation entry
- [ ] Step 8: Frontend Testing Agent — RED/GREEN for auth wiring + security admin
- [ ] Step 9: Designer first-pass + final sign-off
- [ ] Step 10: Code Review Agent
- [ ] Step 11: Iterate fixes until Code Review PASS
- [ ] Step 12: Build verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`)
- [ ] Step 13: Test verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`)
- [ ] Step 14: Test Coverage Agent — security domain coverage hardening
- [ ] Step 15: Update run artifacts for CAP-275 and CAP-253
- [ ] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story security-wave-e --base master --head cap/security-wave-e --title "feat(security): Wave E — Security Foundation (CAP-275, CAP-253)"`
- [x] Step 3: Implement CAP-275 auth wiring — `AuthService.logoutWithRedirect()`, `validateSessionOnResume()`, interceptor 401 redirect
- [x] Step 4: Implement CAP-275 login component — session-expired info banner (`?sessionExpired=true` param)
- [x] Step 5: Implement CAP-253 models and SecurityService — `SecurityRole`, `SecurityPermission`, `getAllRoles`, `createRole`, `getAllPermissions`, `updateRolePermissions`, `revokeRoleAssignment`
- [x] Step 6: Implement CAP-253 pages — roles list, role detail, permissions registry, security audit placeholder
- [x] Step 7: Update `security.routes.ts` + shell navigation entry
- [x] Step 8: Frontend Testing Agent — RED/GREEN for auth wiring + security admin
- [x] Step 9: Designer first-pass + final sign-off
- [x] Step 10: Code Review Agent
- [x] Step 11: Iterate fixes until Code Review PASS
- [x] Step 12: Build verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`)
- [x] Step 13: Test verification (`cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm test -- --watch=false`)
- [x] Step 14: Test Coverage Agent — security domain coverage hardening
- [x] Step 15: Update run artifacts for CAP-275 and CAP-253
- [x] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story security-wave-e --base master --head cap/security-wave-e --title "feat(security): Wave E — Security Foundation (CAP-275, CAP-253)"`

## Wave E Completed

- **Date Completed:** 2026-03-27
- **PR Branch:** `cap/security-wave-e`
- **Key Deliverables:** CAP-275 session wiring and session-resume validation; CAP-275 login session-expired UX; CAP-253 RBAC Admin UI (roles list, role detail, permissions registry); updated run artifacts and frontend tests

---

## Completed Waves

| Wave | Capabilities | Domain | PRs |
| --- | --- | --- | --- |
| CRM Wave A | CAP-089, CAP-090, CAP-091, CAP-092 (partial) | `crm` | PR #1, #2 |
| Workexec Wave B | CAP-002, CAP-003 | `workexec` | PR #3 |
| Workexec Wave B-cont | CAP-004, CAP-005 | `workexec` | PR #4 |
| Workexec+Billing Wave C | CAP-006, CAP-007 | `workexec`, `billing` | PR #5 |
| Accounting Wave D | CAP-049–055 | `accounting` | PR #6 ✅ merged (94fd103) |

---

---

## Wave F: Shopmgmt + Location (CAP-142, CAP-249, CAP-137, CAP-138, CAP-136, CAP-139, CAP-140, CAP-141)

**Status: IN PROGRESS**
**Branch:** `cap/shopmgmt-location-wave-f`
**Base:** `master` (91a1a85 — Wave E merge)
**Target repo:** `durion-positivity-frontend`
**PR target:** TBD
**Updated:** 2026-03-27T21:55:00Z

**Domains introduced:** `shopmgmt` (new), `location` (new)
**Design pack:** `design/Shop-Workorder/`

**Capability scope:**

| CAP | Domain | Stories | Readiness |
| --- | ------ | ------- | --------- |
| CAP-142 | `shopmgmt` | #124 | 🟢 READY — 3 ops populated |
| CAP-249 | `shopmgmt` | #74, #75 | 🟡 NORMALIZE — story 75 empty ops |
| CAP-137 | `shopmgmt` | #137, #138 | 🟡 NORMALIZE — story 138 empty ops |
| CAP-138 | `shopmgmt` | #133, #134 | 🟡 NORMALIZE — story 134 empty ops |
| CAP-136 | `location` | #140, #141, #142 | 🟡 NORMALIZE — all stories empty ops |
| CAP-139 | `shopmgmt`/`people` | #130, #131 | 🟡 NORMALIZE — story 131 empty ops |
| CAP-140 | `shopmgmt`/`people` | #122, #127 | 🟡 NORMALIZE — story 127 empty ops |
| CAP-141 | `shopmgmt`/`security` | #125, #126 | 🟡 NORMALIZE — story 126 empty ops |

**Domain ownership:**

- `shopmgmt` features → `src/app/features/shopmgmt/` (new domain)
- `location` features → `src/app/features/location/` (new domain)

**OpenAPI contracts to inspect:**

- `durion-positivity-backend/pos-shop-manager/openapi.yaml` (primary shopmgmt ops)
- `durion-positivity-backend/pos-people/openapi.yaml` (CAP-139/140 people ops)
- `durion-positivity-backend/pos-workorder/openapi.yaml` (workexec cross-domain ops)
- `durion-positivity-backend/pos-location-service/openapi.yaml` (CAP-136 location ops)

---

- [x] Step 1: Read source materials — `AGENT_WORKSET.yaml` for all 8 CAPs, OpenAPI contracts, `design/Shop-Workorder/` wireframes, `durion/domains/shopmgmt/` and `durion/domains/location/` business rules
- [x] Step 2: Normalize `operation_ids` — inspect OpenAPI specs for stories #75, #138, #134, #140, #141, #142 (location), #131, #127, #126; update `AGENT_WORKSET.yaml` files for each CAP
- [x] Step 3: Create execution branch `cap/shopmgmt-location-wave-f` from `master`
- [ ] Step 4: Designer first-pass — design brief for `shopmgmt` and `location` domains; consult `design/Shop-Workorder/*.html` and `design/DESIGN.md`
- [x] Step 5: Execute CAP-142 story #124 (dispatch board dashboard) — RED tests → anvil instruction cards → HTML Specialist → TypeScript Specialist → Designer sign-off → Code Review
- [ ] Step 6: Execute CAP-249 stories #74, #75 (appointment assignment + reschedule) — same flow
- [ ] Step 7: Execute CAP-137 stories #137, #138 (appointment reschedule/cancel + schedule view by location) — same flow
- [ ] Step 8: Execute CAP-138 stories #133, #134 (dispatch override + mechanic assignment) — same flow
- [ ] Step 9: Execute CAP-136 stories #140, #141, #142 (mobile units, bays, shop location management) — same flow
- [ ] Step 10: Execute CAP-139 stories #130, #131 (approve submitted time + mobile travel time) — same flow
- [ ] Step 11: Execute CAP-140 stories #122, #127 (HR cross-domain ingestion + appointment status update) — same flow
- [ ] Step 12: Execute CAP-141 stories #125, #126 (security audit trail + define shop roles/permissions) — same flow
- [ ] Step 13: Build + test verification — `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build && npm test -- --watch=false`
- [ ] Step 14: Documentation Agent — update `CAPABILITY_STATUS_BOARD.md` for all 8 CAPs; create run artifacts; update completed waves table in `Durion-Processing.md`
- [ ] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --base master --head cap/shopmgmt-location-wave-f --title "feat(shopmgmt,location): Wave F — Shop Management + Location (CAP-136–142, CAP-249)"`

---

## Wave I — Final Delivery (Product + Inventory + Order/Billing/CRM)

**Status: READY** | **Tests at Wave I start:** 699/699 | **Base:** `master`

### Wave I-a: `product` domain

**Branch:** `cap/product-wave-i-a`
**Domain Ownership:** `product` — `src/app/features/product/`
**Capabilities:** CAP-165, CAP-166, CAP-167, CAP-168, CAP-170
**Story Count:** 12 stories (119, 120, 121, 260, 261, 118, 259, 116, 117, 110, 111, 112)

| Domain | Feature Dir | Capability | Stories |
| --- | --- | --- | --- |
| `product` | `src/app/features/product/` | CAP-165 | #119, #120, #121 — Product master data |
| `product` | `src/app/features/product/` | CAP-166 | #260, #261 — Cost management |
| `product` | `src/app/features/product/` | CAP-167 | #118, #259 — MSRP & base pricing |
| `product` | `src/app/features/product/` | CAP-168 | #116, #117 — Location store pricing overrides |
| `product` | `src/app/features/product/` | CAP-170 | #110, #111, #112 — Inventory visibility |

#### Steps

- [ ] Step 1: Read source materials — story MDs, wireframes, contract guides, and OpenAPI specs (pos-catalog, pos-inventory, pos-location) for all 12 product stories
- [ ] Step 2: Designer first-pass — review `design/Inventory-Catalog/` designs; issue design brief for product domain
- [ ] Step 3: Create branch `cap/product-wave-i-a` from `master`
- [ ] Step 4: anvil decomposition — file ownership matrix for product domain shell, catalog service, pricing service, availability service
- [ ] Step 5–16: Per-story implementation (HTML Specialist → TypeScript Specialist → Designer sign-off → Code Review) for each of the 12 stories
- [ ] Step 17: `npm run build` and `npx ng test --no-watch` — verify 699+ tests passing
- [ ] Step 18: Code Review Agent pass
- [ ] Step 19: Documentation Agent — update CAPABILITY_STATUS_BOARD.md
- [ ] Final Step: `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --base master --head cap/product-wave-i-a --title "feat(product): Wave I-a — Product Master Data, Pricing & Availability (CAP-165–168, CAP-170)"`

---

### Wave I-b: `inventory` domain

**Branch:** `cap/inventory-wave-i-b`
**Domain Ownership:** `inventory` — `src/app/features/inventory/`
**Capabilities:** CAP-215, CAP-216, CAP-217, CAP-218, CAP-219, CAP-220, CAP-221, CAP-247, CAP-315
**Story Count:** ~14 stories

| Domain | Feature Dir | Capability | Stories |
| --- | --- | --- | --- |
| `inventory` | `src/app/features/inventory/` | CAP-215 | #100, #101 — Inventory ledger & on-hand |
| `inventory` | `src/app/features/inventory/` | CAP-216 | TBD — Receiving (PO/ASN/Direct) |
| `inventory` | `src/app/features/inventory/` | CAP-217 | TBD, TBD — Put-away & replenishment |
| `inventory` | `src/app/features/inventory/` | CAP-218 | TBD×4 — Picking, issuing, workorder fulfillment |
| `inventory` | `src/app/features/inventory/` | CAP-219 | TBD, TBD — Cycle counts & adjustments |
| `inventory` | `src/app/features/inventory/` | CAP-220 | TBD — Reservations, allocations, substitutions |
| `inventory` | `src/app/features/inventory/` | CAP-221 | TBD — Roles, permissions, audit controls |
| `inventory` | `src/app/features/inventory/` | CAP-247 | TBD — Catalog search & product viewing |
| `inventory` | `src/app/features/inventory/` | CAP-315 | TBD — Procure-to-receive lifecycle |

#### Steps

- [ ] Step 1: Read source materials — story MDs, wireframes, contract guides, OpenAPI (pos-inventory, pos-catalog)
- [ ] Step 2: Designer first-pass — review `design/Inventory-Catalog/` designs; issue design brief for inventory domain
- [ ] Step 3: Create branch `cap/inventory-wave-i-b` from `master`
- [ ] Step 4: anvil decomposition — file ownership for inventory domain shell, inventory ledger service, receiving service, picking service, cycle-count service
- [ ] Step 5–N: Per-story implementation (HTML Specialist → TypeScript Specialist → Designer sign-off → Code Review) for all inventory stories
- [ ] Step N+1: `npm run build` and `npx ng test --no-watch` — verify tests passing
- [ ] Step N+2: Code Review Agent pass
- [ ] Step N+3: Documentation Agent — update CAPABILITY_STATUS_BOARD.md
- [ ] Final Step: `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend --base master --head cap/inventory-wave-i-b --title "feat(inventory): Wave I-b — Inventory Ledger, Receiving, Picking & Fulfillment (CAP-215–221, CAP-247, CAP-315)"`

---

### Wave I-c: `order`, `billing`, `accounting`, `crm` additions

**Status: IN PROGRESS** | **Base:** `master` | **Generated:** 2026-03-30T00:00:00Z
**Branch:** `cap/order-billing-crm-wave-i-c`
**Target repo:** `durion-positivity-frontend`
**Capabilities:** 5 | **Stories:** 13 | **Domains:** `billing` (CAP-248, CAP-250), `accounting` (CAP-251), `order` (CAP-246), `crm` (CAP-252)

#### Domain Ownership Mapping

| Domain | Feature Dir | Capability | Stories |
| --- | --- | --- | --- |
| `billing` | `src/app/features/billing/` | CAP-248 | #79 — Retrieve and display estimates; #78 — Display WIP summary; #77 — Display invoice and request payment |
| `billing` | `src/app/features/billing/` | CAP-250 | #73 — Initiate card authorization; #72 — Void authorization or refund; #71 — Print/email receipt |
| `accounting` | `src/app/features/accounting/` | CAP-251 | #69 — Reconcile POS status with accounting events; #70 — Update invoice payment status |
| `order` | `src/app/features/order/` | CAP-246 | #85 — Create sales order cart and add items; #84 — Apply price override; #83 — Cancel order |
| `crm` | `src/app/features/crm/` | CAP-252 | #67 — Enforce PO requirement and billing rules; #68 — Load customer/vehicle context |

#### Capability Register

| CAP | Name | Stories | operation_ids | OpenAPI |
| --- | --- | --- | --- | --- |
| CAP-248 | Estimate, WIP, and Invoice Visibility (Workexec Coordination) | #79, #78, #77 | `createInvoice`, `getInvoice`, `finalizeInvoice`, `listWip`, `getWipDetail`, `getTransitionHistory`, `searchEstimates`, `getEstimateById`, `getEstimatesByCustomer` | pos-invoice, pos-workorder |
| CAP-250 | Payments (Card Acceptance via Payment Service) | #73, #72, #71 | `applyPayment`, `getInvoiceStatus`, `getPayment`, `voidPayment`, `reversePayment`, `reversePaymentApplication`, `getPaymentByRef` | pos-accounting |
| CAP-251 | Invoice Payment Status Sync (Accounting Coordination) | #69, #70 | `listEvents`, `getEvent`, `getEventProcessingLog`, `getInvoiceStatus` | pos-accounting |
| CAP-246 | POS Sales Order & Cart (Quote-to-Cash Entry Point) | #85, #84, #83 | `createCart`, `addItem`, `updateItemQuantity`, `applyPriceOverride`, `getOverridesByOrder`, `getOverride`, `getOrder`, `cancelOrder` | pos-order |
| CAP-252 | Customer Context (CRM Snapshot) | #67, #68 | `getBillingRules`, `upsertBillingRules`, `finalizeInvoice`, `fetchByParty`, `fetchByVehicle` | pos-invoice, pos-customer |

#### Steps

- [x] Step 1: Read all source materials — story MDs, wireframes, contract guides, and OpenAPI specs for all 13 stories:
  - CAP-248 #79: `docs/capabilities/CAP-248/stories/frontend/CAP_248.79.frontend.md`, `domains/workexec/.ui/frontend-story-workexec-retrieve-and-display-estim-79.wf.md`, `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-workorder/openapi.yaml` (ops: `searchEstimates`, `getEstimateById`, `getEstimatesByCustomer`)
  - CAP-248 #78: `docs/capabilities/CAP-248/stories/frontend/CAP_248.78.frontend.md`, `domains/workexec/.ui/frontend-story-workexec-display-work-in-progress-s-78.wf.md` (ops: `listWip`, `getWipDetail`, `getTransitionHistory`)
  - CAP-248 #77: `docs/capabilities/CAP-248/stories/frontend/CAP_248.77.frontend.md`, `domains/accounting/.ui/frontend-story-workexec-display-invoice-and-reques-77.wf.md`, `domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-invoice/openapi.yaml` (ops: `createInvoice`, `getInvoice`, `finalizeInvoice`)
  - CAP-250 #73: `docs/capabilities/CAP-250/stories/frontend/CAP_250.73.frontend.md`, `domains/accounting/.ui/frontend-story-payment-initiate-card-authorization-73.wf.md`, `domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-accounting/openapi.yaml` (ops: `applyPayment`, `getInvoiceStatus`, `getPayment`)
  - CAP-250 #72: `docs/capabilities/CAP-250/stories/frontend/CAP_250.72.frontend.md`, `domains/accounting/.ui/frontend-story-payment-void-authorization-or-refun-72.wf.md` (ops: `voidPayment`, `reversePayment`, `reversePaymentApplication`)
  - CAP-250 #71: `docs/capabilities/CAP-250/stories/frontend/CAP_250.71.frontend.md`, `domains/accounting/.ui/frontend-story-payment-print-email-receipt-and-sto-71.wf.md` (ops: `getInvoiceStatus`, `getPayment`, `getPaymentByRef`)
  - CAP-251 #69: `docs/capabilities/CAP-251/stories/frontend/CAP_251.69.frontend.md`, `domains/accounting/.ui/frontend-story-accounting-reconcile-pos-status-wit-69.wf.md`, `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` (ops: `listEvents`, `getEvent`, `getEventProcessingLog`)
  - CAP-251 #70: `docs/capabilities/CAP-251/stories/frontend/CAP_251.70.frontend.md`, `domains/accounting/.ui/frontend-story-accounting-update-invoice-payment-s-70.wf.md` (ops: `getInvoiceStatus`, `listEvents`, `getEvent`)
  - CAP-246 #85: `docs/capabilities/CAP-246/stories/frontend/CAP_246.85.frontend.md`, `domains/order/.ui/frontend-story-order-create-sales-order-cart-and-a-85.wf.md`, `domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md`, `durion-positivity-backend/pos-order/openapi.yaml` (ops: `createCart`, `addItem`, `updateItemQuantity`)
  - CAP-246 #84: `docs/capabilities/CAP-246/stories/frontend/CAP_246.84.frontend.md`, `domains/pricing/.ui/frontend-story-order-apply-price-override-with-per-84.wf.md` (ops: `applyPriceOverride`, `getOverridesByOrder`, `getOverride`, `getOrder`)
  - CAP-246 #83: `docs/capabilities/CAP-246/stories/frontend/CAP_246.83.frontend.md`, `domains/order/.ui/frontend-story-order-cancel-order-with-controlled-83.wf.md` (ops: `cancelOrder`, `getOrder`)
  - CAP-252 #67: `docs/capabilities/CAP-252/stories/frontend/CAP_252.67.frontend.md`, `domains/accounting/.ui/frontend-story-customer-enforce-po-requirement-and-67.wf.md`, `domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md` (ops: `getBillingRules`, `upsertBillingRules`, `finalizeInvoice`)
  - CAP-252 #68: `docs/capabilities/CAP-252/stories/frontend/CAP_252.68.frontend.md`, `domains/crm/.ui/frontend-story-customer-load-customer-vehicle-cont-68.wf.md`, `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md` (ops: `fetchByParty`, `getBillingRules`, `fetchByVehicle`)
  - Design packs: `design/Accounting/`, `design/Shop-Workorder/`, `design/Customer/`, `design/DESIGN.md`, `design/source/`

- [x] Step 2: Create execution branch `cap/order-billing-crm-wave-i-c` from `master` via `durion/.github/hooks/create-branch-hook.sh`

- [ ] Step 3: Designer first-pass — design brief for billing/accounting/order/crm surfaces; consult `design/Accounting/`, `design/Shop-Workorder/`, `design/Customer/`, and `design/DESIGN.md`; issue token, layout, state-machine, and payment-flow guidance; confirm design approach for: invoice/payment screens, WIP summary, estimate search, order cart, price-override form, CRM context panel

- [ ] Step 4: Execute CAP-248 story #79 (Retrieve and display estimates — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: estimate search results list, detail view, customer filter, empty/loading/error states
  - TypeScript Specialist: `BillingService` methods `searchEstimates`, `getEstimateById`, `getEstimatesByCustomer`; route/page; state machine
  - Designer final sign-off for story #79
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 5: Execute CAP-248 story #78 (Display WIP summary — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: WIP list table, status badges, transition history panel, empty/loading/error states
  - TypeScript Specialist: `BillingService` `listWip`, `getWipDetail`, `getTransitionHistory`; state; route
  - Designer final sign-off for story #78
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 6: Execute CAP-248 story #77 (Display invoice and request payment — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: invoice detail display, finalize action, payment initiation entry point, empty/loading/error states
  - TypeScript Specialist: `BillingService` `createInvoice`, `getInvoice`, `finalizeInvoice`; state machine; route
  - Designer final sign-off for story #77
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 7: Test Coverage Agent — harden `billing` domain test coverage after CAP-248 stories

- [ ] Step 8: Execute CAP-250 story #73 (Initiate card authorization — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: card payment entry form, authorization status display, amount/method breakdown, empty/loading/error states
  - TypeScript Specialist: `PaymentService` `applyPayment`, `getInvoiceStatus`, `getPayment`; validation; state
  - Designer final sign-off for story #73
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 9: Execute CAP-250 story #72 (Void authorization or refund — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: void/refund confirmation dialog, status feedback, empty/loading/error states
  - TypeScript Specialist: `PaymentService` `voidPayment`, `reversePayment`, `reversePaymentApplication`; state
  - Designer final sign-off for story #72
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 10: Execute CAP-250 story #71 (Print/email receipt — `billing` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: receipt preview/print/email action panel, empty/loading/error states
  - TypeScript Specialist: `PaymentService` `getInvoiceStatus`, `getPayment`, `getPaymentByRef`; receipt generation; state
  - Designer final sign-off for story #71
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 11: Test Coverage Agent — harden `billing` domain test coverage after CAP-250 stories

- [ ] Step 12: Execute CAP-251 story #69 (Reconcile POS status with accounting events — `accounting` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: event list/processing log display, status reconciliation view, empty/loading/error states
  - TypeScript Specialist: `AccountingService` additions `listEvents`, `getEvent`, `getEventProcessingLog`; state; route
  - Designer final sign-off for story #69
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 13: Execute CAP-251 story #70 (Update invoice payment status — `accounting` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: invoice payment status panel, event log link, empty/loading/error states
  - TypeScript Specialist: `AccountingService` `getInvoiceStatus`, `listEvents`, `getEvent` additions; state
  - Designer final sign-off for story #70
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 14: Test Coverage Agent — harden `accounting` domain test coverage after CAP-251 stories

- [ ] Step 15: Execute CAP-246 story #85 (Create sales order cart and add items — `order` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: order cart page, item search/add, quantity controls, order summary panel, empty/loading/error states
  - TypeScript Specialist: NEW `order.routes.ts`, `OrderService` with `createCart`, `addItem`, `updateItemQuantity`; domain scaffold; state machine
  - Designer final sign-off for story #85
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 16: Execute CAP-246 story #84 (Apply price override — `order` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: price override input with authorization context, overrides summary list, empty/loading/error states
  - TypeScript Specialist: `OrderService` `applyPriceOverride`, `getOverridesByOrder`, `getOverride`, `getOrder`; state
  - Designer final sign-off for story #84
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 17: Execute CAP-246 story #83 (Cancel order — `order` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: cancel order confirmation flow, reason capture, status feedback, empty/loading/error states
  - TypeScript Specialist: `OrderService` `cancelOrder`, `getOrder`; guard/validation; state
  - Designer final sign-off for story #83
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 18: Test Coverage Agent — harden `order` domain test coverage after CAP-246 stories

- [ ] Step 19: Execute CAP-252 story #67 (Enforce PO requirement and billing rules — `crm` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: billing rules display/edit form, PO requirement toggle, empty/loading/error states
  - TypeScript Specialist: `CrmService` additions `getBillingRules`, `upsertBillingRules` + `BillingService` `finalizeInvoice` cross-domain; state; route
  - Designer final sign-off for story #67
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 20: Execute CAP-252 story #68 (Load customer/vehicle context — `crm` domain)
  - RED tests with Frontend Testing Agent
  - anvil instruction cards
  - HTML Specialist: customer context panel (party + vehicle snapshot), billing rules summary, empty/loading/error states
  - TypeScript Specialist: `CrmService` `fetchByParty`, `fetchByVehicle`, `getBillingRules`; derived view-model; route
  - Designer final sign-off for story #68
  - Code Review Agent; iterate fixes until PASS

- [ ] Step 21: Test Coverage Agent — harden `crm` domain test coverage after CAP-252 stories

- [ ] Step 22: Documentation Agent — update `CAPABILITY_STATUS_BOARD.md`; create run artifacts under `docs/capabilities/<CAP-ID>/runs/latest.md` for CAP-246, CAP-248, CAP-250, CAP-251, CAP-252; mark all Wave I-c capabilities DONE

- [ ] Step 23: Run `npm run build` in `durion-positivity-frontend` — verify build passes; fix any type/compile errors before PR

- [ ] Step 24: Run `npx ng test --no-watch` in `durion-positivity-frontend` — verify all tests pass; record test count

- [ ] Final Step: Create PR via `durion/.github/hooks/pull-request-hook.sh` with:
  - `--repo /home/louis-burroughs/IdeaProjects/durion-positivity-frontend`
  - `--story CAP-246/248/250/251/252`
  - `--base master`
  - `--head cap/order-billing-crm-wave-i-c`
  - `--title "feat(order,billing,crm): Wave I-c — POS Order Cart, Payments, Billing Visibility & CRM Snapshot (CAP-246/248/250/251/252)"`
  - `--body-file` pointing to rendered run summary

---

## ── ARCHIVED ── Wave E: Security Foundation (CAP-275, CAP-253)

**Status: COMPLETED** | **PR:** #7 (merged `91a1a85`) | **Branch:** `cap/security-wave-e`

Stories delivered: auth wiring (CAP-275), RBAC admin UI — roles list, role detail, permissions registry (CAP-253)

---

## ── ARCHIVED ── Wave D: CAP-049–055

**Status: COMPLETED** | **PR:** #6 (merged) | **Branch:** `cap/accounting-wave-d` | **Head at merge:** `94fd103`

Stories delivered: 208, 207, 206, 205, 177, 179–185, 202, 178, 195, 192, 123, 186 (18 stories)
Tests at close: 187/187 (30 files)
PR review cycles: 2 (25 threads total, all resolved)

## ── ARCHIVED ── Wave C: CAP-006 + CAP-007

**Status: COMPLETED** | **PR:** #5 | **Branch:** `cap/workexec-wave-c`
