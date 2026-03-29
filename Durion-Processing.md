## Wave H: People Profile Management + Location Topology (CAP-117, CAP-119, CAP-120, CAP-121, CAP-214)

**Status: IN PROGRESS**
**Wave:** H — People Profile + Location Topology
**Branch:** `cap/people-location-wave-h`
**Base:** `master`
**Target repo:** `durion-positivity-frontend`
**Capabilities:** CAP-117 (stories #152, #154, #155), CAP-119 (stories #150, #151), CAP-120 (stories #143, #147, #148, #149), CAP-121 (stories #144, #145, #146), CAP-214 (stories #102, #103, #104)
**Domains:** `people`, `location`, `security` (cross-domain #155), `workexec` (cross-domain #145, #146)

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
