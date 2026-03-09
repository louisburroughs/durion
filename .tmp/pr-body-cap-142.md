## Capability & Traceability
- Capability: `cap:142`
- Parent STORY (durion): louisburroughs/durion#142
- Child issue: louisburroughs/durion-positivity-backend#60
- Domain: `domain:workexec`

## Contract References (REQUIRED for backend PRs touching API/event behavior)
- Contract guide entries (durion): `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md` → CAP-142 section
- Durion contract PR: N/A (contract guide updated locally in durion repo)

## Scope
- What changed:
  - `DashboardResponse.java`: Added `Boolean dataQualityWarning` field to the dispatch board aggregate response DTO
  - `DashboardServiceImpl.java`: Added degradation tracking for both upstream clients (`pos-people` and `pos-shop-manager`); when either service is unavailable, dashboard returns HTTP 200 with `dataQualityWarning=true` and gracefully degrades (empty mechanics/bays list)
- Why:
  - **Missing acceptance criterion from issue #60**: When `pos-people` availability service or `pos-shop-manager` bay service is unavailable (throws exception or returns null), the backend was previously failing with RuntimeException instead of returning a degraded HTTP 200 response with `dataQualityWarning: true`. This change implements the required graceful degradation behavior.

## Tests
- [x] Unit tests added/updated: 4 new service tests for degraded-mode scenarios (`DashboardServiceTest`)
  - `whenPeopleServiceUnavailable_dashboardReturnsWithDataQualityWarningTrue`
  - `whenPeopleServiceReturnsNull_dashboardReturnsWithDataQualityWarningTrue`
  - `whenBothServicesAvailable_dataQualityWarningIsFalse`
  - `whenShopmgrUnavailableViaCatch_dataQualityWarningIsTrue`
- [ ] Integration tests added/updated: N/A (existing integration tests cover the endpoint)
- [x] Provider behavioral contract tests added/updated: Covered by service unit tests
- [ ] Consumer/UI tests added/updated: N/A (backend only)
- How to run:
  ```bash
  # Targeted tests (new + existing)
  bash .github/hooks/test-run-hook.sh --repo . --module pos-workorder --goal test --test DashboardServiceTest
  # Full module verify
  bash .github/hooks/module-verify-hook.sh --repo . --modules pos-workorder
  ```

## Risk & Rollback
- Risk level: Low
- Rollback plan: Revert the 3 changed files (`DashboardResponse.java`, `DashboardServiceImpl.java`, `DashboardServiceTest.java`). The new `dataQualityWarning` field is additive and null-safe; no existing clients are broken.

## Checklist
- [x] Branch name matches `cap/<cap-id>` (cap/142)
- [x] PR title starts with `[CAP:142]`
- [x] Links to parent + child issues are present
- [x] Contract guide updated (CAP-142 section added to `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`)
- [x] Required CI checks passing (261 tests, 0 failures; DashboardServiceImpl 96.5% instruction coverage)
