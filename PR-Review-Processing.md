# PR Review Processing Log

## Context
- **Repo**: louisburroughs/durion-positivity-frontend
- **PR**: 15
- **URL**: https://github.com/louisburroughs/durion-positivity-frontend/pull/15
- **Branch**: `cap/inventory-wave-i-b-deferred` → `master`
- **Title**: `cap/218: Wave I-b Deferred inventory stories (CAP-216/218/219/220/221/315)`
- **Review Track**: frontend
- **Linked Issues**: #87 (inventory security admin), #89 (shortage resolution), #92 (pick-list), #97 (cross-dock receiving), #241 (cycle count plans), #242 (return-to-stock), #243 (consume-picked-items), #244 (pick-execute), #571 (receive-into-staging ASN extension)
- **ADRs Checked**:
    - **ADR-0029 (Accessibility)**: All inputs have labels, errors announced via `role="alert"`.
    - **ADR-0030 (i18n)**: All user-facing strings use `| translate`; 4 locale files updated.
    - **ADR-0031 (Mutation Error State)**: `state.set('error')` before `errorKey.set(...)`.
    - **ADR-0032 (Test Fixture Conformity)**: All fixtures typed as exact domain interfaces.
    - **ADR-0033 (Effect Cancellation)**: `onCleanup(() => sub.unsubscribe())` in all `effect()` bodies.
    - **ADR-0034 (Server-Generated Fields)**: `readonly?` on server-generated fields; excluded from POST payloads.
    - **ADR-0035 (Service Method Coverage)**: All new public service methods have ≥1 test.
- **Copilot Round 1 Threads (8 — all resolved)**:
    1. `r3025706806` — `receive-into-staging.ts:56` — `asnMode` fallback on normal nav ✅ Fixed
    2. `r3025706819` — `receive-into-staging.html:24` — Shipment ref passed to ASN ID endpoint ✅ Fixed
    3. `r3025706821` — `inventory-security-admin-page.ts:148` — `split('.')[1]` wrong for multi-segment keys ✅ Fixed
    4. `r3025706825` — `inventory-security-admin-page.html` — `description` piped through `| translate` (OUTDATED) ✅ Resolved/outdated
    5. `r3025706831` — `workexec.service.spec.ts:489` — Untyped `consumeReq` fixture (ADR-0032) ✅ Fixed
    6. `r3025706835` — `cross-dock-receive-page.ts:83` — Validation errors hide form ✅ Fixed
    7. `r3025706839` — `fr-CA.json:2078` — "Transferer" → "Transférer" ✅ Fixed
    8. `r3025706846` — `fr-CA.json:2081` — "Gerer" → "Gérer" ✅ Fixed
- **Copilot Round 2 Threads (7 — ALL UNRESOLVED)**:
    1. `r3027589778` — `cycle-count-plan-form-page.ts:141` — `new Date(YYYY-MM-DD)` parsed as UTC; off-by-one timezone bug in date validation ❌
    2. `r3027589814` — `cycle-count-plan-form-page.html:71` — Hard-coded `href="/app/inventory/counts/plans"` bypasses Angular router ❌
    3. `r3027589844` — `return-to-stock-page.html:110` — Hard-coded `href="/app/inventory/fulfillment"` bypasses Angular router ❌
    4. `r3027589859` — `shortage-resolution-page.html:65` — Hard-coded `href="/app/inventory/fulfillment"` bypasses Angular router ❌
    5. `r3027589883` — `pick-list-page.html:21` — "Retry" `<a href>` navigates away instead of retrying the load ❌
    6. `r3027589900` — `cycle-count-plan-list-page.html:63` — Angular `date` pipe on YYYY-MM-DD string causes UTC timezone off-by-one ❌
    7. `r3027589915` — `receive-into-staging.ts:50` — `asnId` signal set from query param but never read/used (dead state) ❌
- **ADR Gaps Identified** (new ADRs required per user instruction):
    - No ADR governs SPA-internal navigation (routerLink vs href) — 4 violations in this PR
    - No ADR governs date-only string (YYYY-MM-DD) parsing/display — 2 violations in this PR

## Plan

**Summary**: PR #15 (frontend, Wave I-b inventory stories) has 7 unresolved Copilot review threads from a second-pass review, all raised after the original 8 threads were resolved. The findings span three categories: (1) SPA navigation regressions (4 hard-coded `href` links), (2) date-only timezone off-by-one (2 instances), and (3) dead signal state (1 instance). No ADR currently governs either SPA navigation or date-only string handling — two new ADRs must be created to prevent recurrence.

**Objective**: Remediate all 7 unresolved Round-2 review threads, create two new ADRs, update the frontend AGENTS.md, and obtain a final `Verdict: PASS` from the code reviewer.

### Implementation Steps

- [ ] **Step 1 — Code Remediation** (`PR Fix Coder`): Fix all 7 unresolved production-code defects.
    - `r3027589778` **(HIGH)**: Fix `isScheduledDateValid()` in `cycle-count-plan-form-page.component.ts:141` — replace `new Date(dateInput)` UTC parsing with local-date constructor `new Date(y, m-1, d)` (split YYYY-MM-DD).
    - `r3027589814` **(HIGH)**: Replace `href="/app/inventory/counts/plans"` with `routerLink="/app/inventory/counts/plans"` in `cycle-count-plan-form-page.component.html:71`; add `RouterLink` to component `imports`.
    - `r3027589844` **(HIGH)**: Replace `href="/app/inventory/fulfillment"` with `routerLink="/app/inventory/fulfillment"` in `return-to-stock-page.component.html:110`; add `RouterLink` to component `imports`.
    - `r3027589859` **(HIGH)**: Replace `href="/app/inventory/fulfillment"` with `routerLink="/app/inventory/fulfillment"` in `shortage-resolution-page.component.html:65`; add `RouterLink` to component `imports`.
    - `r3027589883` **(HIGH)**: Replace the "Retry" `<a href>` in `pick-list-page.component.html:21` with a `<button (click)="loadPickList()">` for retry, and add a separate `<a routerLink>` "Back" link; add `RouterLink` to component `imports`.
    - `r3027589900` **(MEDIUM)**: Fix `cycle-count-plan-list-page.component.html:63` — render `plan.scheduledDate` directly (raw YYYY-MM-DD) by removing the `| date: 'mediumDate'` pipe, avoiding UTC parse. If display formatting is desired use a local-date pipe or string split.
    - `r3027589915` **(MEDIUM)**: Fix `receive-into-staging.component.ts:50` — bind `asnId()` signal to `[value]` of the ASN ID input element in the template so deep-link prefill works; or remove the signal if the `switchToFallback` path makes it unnecessary.

- [ ] **Step 2 — Test Remediation** (`PR Test Fixer`): Update or add tests for the date validation fix.
    - In `cycle-count-plan-form-page.component.spec.ts`: update the "past date" test and add a "today" boundary test verifying `canSubmit()` returns `true` for today's date (using YYYY-MM-DD local format).

- [ ] **Step 3 — ADR Creation** (Orchestrator-direct): Create two new ACCEPTED ADRs.
    - `0037-frontend-spa-navigation-policy.adr.md` — prohibit bare `href` for in-app routes; require `routerLink` or `router.navigate()`.
    - `0038-frontend-date-only-string-handling-policy.adr.md` — prohibit `new Date(YYYY-MM-DD)` and Angular `date` pipe on raw date-only strings; require local-date constructor or string-split display.

- [ ] **Step 4 — Verification** (`PR Code Reviewer`): Verify all 7 thread defects addressed; 0 TS errors; domain tests pass; ADRs authored. Return `Verdict: PASS | FAIL`.

- [ ] **Step 5 — Thread Replies + Final Summary**: Post a reply to each of the 7 unresolved threads; write `## Final Summary`.

### Risks
- `routerLink` requires `RouterLink` in `imports`; missing import will cause NG compilation error.
- Date fix must handle all edge cases: empty string, invalid string, today boundary.

### Success Checks
- All 7 Copilot review threads marked resolved or have a posted reply.
- `npm run build` → 0 TS errors.
- Domain inventory spec suite → all specs pass.
- ADR-0037 and ADR-0038 files exist and are ACCEPTED.

## Subagent Outputs

### 2026-04-02T UTC — PR-Review-Processing.md plan written by Orchestrator

- **Objective**: Write execution plan for PR #15 frontend review
- **Output**: `## Plan` section written with 5 steps and 7 thread findings catalogued
- **Validation decision**: accepted

### 2026-04-02T UTC — PR Reviewer — Evidence-based review of 7 threads

- **Objective**: Confirm all 7 Round-2 Copilot threads against source code
- **Output**: All 7 findings CONFIRMED. ADR gaps identified (no ADR for SPA nav, no ADR for date-only strings). Recommended 7→coder, 2→test (overlap). Severity: 2 HIGH, 4 MEDIUM, 1 LOW.
- **Validation decision**: accepted

### 2026-04-02T UTC — PR Fix Coder — 7 production code defects fixed

- **Objective**: Implement all 7 production fixes for threads r3027589778, r3027589814, r3027589844, r3027589859, r3027589883, r3027589900, r3027589915
- **Output**:
  - `cycle-count-plan-form-page.component.ts` — local-date parse + `RouterLink` import
  - `cycle-count-plan-form-page.component.html` — `routerLink` Cancel
  - `return-to-stock-page.component.ts/html` — `RouterLink` import + `routerLink` Cancel
  - `shortage-resolution-page.component.ts/html` — `RouterLink` import + `routerLink` Cancel
  - `pick-list-page.component.ts/html` — `reload()` method + retry button → `<button>` + Back routerLink
  - `cycle-count-plan-list-page.component.html` — `+ 'T00:00:00'` date pipe fix
  - `receive-into-staging.component.html` — `[value]="asnId() ?? ''"` binding
  - Targeted spec runs: pick-list 3/3, cycle-count-form 5/5 passing
- **Validation decision**: accepted

### 2026-04-02T UTC — PR Test Fixer — 2 test updates applied

- **Objective**: Add "today" boundary test (cycle-count form) + `reload()` test (pick-list)
- **Output**:
  - `cycle-count-plan-form-page.component.spec.ts` — added `canSubmit is true when scheduledDate is today` using local-time getters (6/6 pass)
  - `pick-list-page.component.spec.ts` — added `reload() re-fetches pick list and sets state to ready` (4/4 pass)
- **Validation decision**: accepted

### 2026-04-02T UTC — PR Code Reviewer — Final verification

- **Objective**: Verify all 7 fixes applied; 0 TS errors; return `Verdict: PASS | FAIL`
- **Output**: All 7 findings confirmed fixed in source. 0 TypeScript/template compilation errors. No severity findings. **Verdict: PASS**
- **Validation decision**: accepted

### 2026-04-02T UTC — Orchestrator direct — ADR-0037 + ADR-0038 created

- **Objective**: Create two new ACCEPTED ADRs to prevent recurrence
- **Output**:
  - `durion/docs/adr/0037-frontend-spa-navigation-policy.adr.md` — prohibits bare `href`; requires `routerLink`/`router.navigate()`
  - `durion/docs/adr/0038-frontend-date-only-string-handling-policy.adr.md` — prohibits `new Date(YYYY-MM-DD)` and bare `DatePipe` on date-only strings
  - Both ADRs added to `durion-positivity-frontend/AGENTS.md` checklist and `durion/AGENTS.md` minimum ADR list
- **Validation decision**: accepted

### 2026-04-02T UTC — Thread reply posted

- **Objective**: Post summarizing comment covering all 7 threads
- **Comment**: https://github.com/louisburroughs/durion-positivity-frontend/pull/15#issuecomment-4177850746
- **Validation decision**: accepted

---

## Final Summary

- **PR analyzed**: [#15](https://github.com/louisburroughs/durion-positivity-frontend/pull/15) — `cap/218: Wave I-b Deferred inventory stories (CAP-216/218/219/220/221/315)`
- **Review track**: frontend

### Evidence sources used

| Source | Details |
|--------|---------|
| PR metadata | 55 files changed, +6425/-18 |
| Linked issues | #87, #89, #92, #97, #241, #242, #243, #244, #571 |
| PR comments | 1 issue comment (owner remediation summary), multiple Copilot reviews |
| Review threads | 15 total; 8 Round-1 (all resolved); 7 Round-2 (all remediated in this run) |
| ADRs | 0029–0035, + 2 new (0037, 0038) |
| Test evidence | Targeted spec runs via `ng test --include` |

### Round-1 findings status (8 threads — all pre-resolved)

All 8 threads from the first Copilot review (r3025706806–r3025706846) were resolved before this orchestration run.

### Round-2 findings by severity (7 threads — all remediated in this run)

| Severity | Count | Threads |
|----------|-------|---------|
| HIGH | 2 | r3027589778 (date UTC), r3027589883 (retry nav) |
| MEDIUM | 4 | r3027589814, r3027589844, r3027589859 (href nav), r3027589900 (date pipe) |
| LOW | 1 | r3027589915 (dead signal) |

### Code fixes completed (10 files)

1. `cycle-count-plan-form-page.component.ts` — local-date parse in `isScheduledDateValid()`, `RouterLink` import
2. `cycle-count-plan-form-page.component.html` — Cancel `href` → `routerLink`
3. `return-to-stock-page.component.ts` — `RouterLink` import
4. `return-to-stock-page.component.html` — Cancel `href` → `routerLink`
5. `shortage-resolution-page.component.ts` — `RouterLink` import
6. `shortage-resolution-page.component.html` — Cancel `href` → `routerLink`
7. `pick-list-page.component.ts` — `reload()` method, loading logic extracted
8. `pick-list-page.component.html` — Retry `<a href>` → `<button (click)="reload()">`, Back `<a routerLink>`
9. `cycle-count-plan-list-page.component.html` — `+ 'T00:00:00'` date-pipe fix
10. `receive-into-staging.component.html` — `[value]="asnId() ?? ''"` deep-link binding

### Test fixes completed (2 files)

1. `cycle-count-plan-form-page.component.spec.ts` — added "today" boundary test (local-time getters)
2. `pick-list-page.component.spec.ts` — added `reload()` test (error → ready transition)

### ADRs created (2)

| ADR | Title |
|-----|-------|
| ADR-0037 | Frontend SPA Navigation Policy — prohibit bare `href` for in-app routes |
| ADR-0038 | Frontend Date-Only String Handling Policy — prohibit `new Date(YYYY-MM-DD)` for local semantics |

Both ADRs added to `durion-positivity-frontend/AGENTS.md` and `durion/AGENTS.md`.

### PR comment thread coverage

| Thread | Status |
|--------|--------|
| r3027589778 | ✅ Replied in consolidated comment |
| r3027589814 | ✅ Replied in consolidated comment |
| r3027589844 | ✅ Replied in consolidated comment |
| r3027589859 | ✅ Replied in consolidated comment |
| r3027589883 | ✅ Replied in consolidated comment |
| r3027589900 | ✅ Replied in consolidated comment |
| r3027589915 | ✅ Replied in consolidated comment |

### Final verification status

**Verdict: PASS** — All 7 unresolved threads remediated; 0 TypeScript errors; affected spec suites passing; ADR-0037 and ADR-0038 created and registered.

### Unresolved blockers

None.

### Processing log file

`durion/PR-Review-Processing.md`


### Test fixes completed (1 file)
- `WorkorderPickFacadeControllerTest.java` — 2 version assertions corrected (lines 208, 239)

### PR comment thread coverage
- All 8 threads addressed in single consolidated PR comment: `#issuecomment-4164043083`
- Thread-level replies not individually posted (MCP supports issue comments only, not inline review thread replies)

### Final verification status
- **Java**: 0 compiler errors across all modified files
- **Build/test**: awaiting CI run after push

### Unresolved blockers
- None. All 8 review findings are remediated.

### Processing log
`durion/PR-Review-Processing.md`

