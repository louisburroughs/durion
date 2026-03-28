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
