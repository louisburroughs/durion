# CAP-120 Backend Implementation (Issue #79)

## Scope

This implementation was completed in `durion-positivity-backend` on branch `cap/120` and aligns with the authoritative People API contract in `pos-people/openapi.json` (read-only).

## Implementation Checklist

- Implemented `GET /v1/people/reports/attendanceJobtimeDiscrepancy` service-backed behavior (removed TODO stub).
- Added report DTO and service-layer orchestration for attendance discrepancy aggregates.
- Extended provider contract coverage (`ContractBehaviorIT`) for report, batch approve, and batch reject flows.
- Added explicit assignment request validation fallback in `PersonAccessController` for `roleCode` when Bean Validation provider is unavailable.
- Updated `TimeEntryBatchIntegrationTest` stubs to match current repository/service behavior.
- Validated with targeted and module-level test runs; documented unrelated pre-existing test failures.
- Ran Sonar analysis and security hotspot checks for changed production files.

## Files Changed

### Backend (`durion-positivity-backend`)

- `pos-people/src/main/java/com/positivity/people/internal/controller/PeopleReportsController.java`
- `pos-people/src/main/java/com/positivity/people/internal/controller/PersonAccessController.java`
- `pos-people/src/main/java/com/positivity/people/internal/dto/AttendanceDiscrepancyReportResponse.java`
- `pos-people/src/main/java/com/positivity/people/service/PeopleReportsService.java`
- `pos-people/src/main/java/com/positivity/people/internal/repository/TimeEntryRepository.java`
- `pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java`
- `pos-people/src/test/java/com/positivity/people/controller/TimeEntryBatchIntegrationTest.java`

## Critical Code Snippets

### Controller Signature

```java
@GetMapping("/attendanceJobtimeDiscrepancy")
public ResponseEntity<AttendanceDiscrepancyReportResponse> getAttendanceDiscrepancyReport() {
    log.info("Fetching attendance job time discrepancy report");
    return ResponseEntity.ok(peopleReportsService.getAttendanceDiscrepancyReport());
}
```

### Service Method

```java
public AttendanceDiscrepancyReportResponse getAttendanceDiscrepancyReport() {
    long approvedCount = timeEntryRepository.countByStatus(TimeEntryStatus.APPROVED);
    long pendingApprovalCount = timeEntryRepository.countByStatus(TimeEntryStatus.PENDING_APPROVAL);
    long rejectedCount = timeEntryRepository.countByStatus(TimeEntryStatus.REJECTED);

    return new AttendanceDiscrepancyReportResponse(
            Instant.now(),
            approvedCount,
            pendingApprovalCount,
            rejectedCount);
}
```

### Repository Query Method

```java
long countByStatus(TimeEntryStatus status);
```

### Sample ContractBehaviorIT Test

```java
@Test
@DisplayName("happy path: approve time entries returns decision results")
void approveTimeEntries_happyPath() throws Exception {
    when(timeEntryService.approveEntries(anyList(), any(), any(), any()))
            .thenReturn(List.of(new TimeEntryDecisionResult(
                    "11111111-1111-1111-1111-111111111111",
                    true,
                    null,
                    null)));

    String payload = """
            {
              "decisions": [
                {
                  "timeEntryId": "11111111-1111-1111-1111-111111111111"
                }
              ]
            }
            """;

    mockMvc.perform(withAuth(post("/v1/people/timeEntries/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
                    .header("X-User-Id", "approver-user")
                    .header("X-Permissions", "people:timeEntry:approve")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].success").value(true));
}
```

## Configuration Changes

No new configuration properties were added.
No OpenAPI file changes were made. The authoritative file `pos-people/openapi.json` was treated as read-only.

## Validation and Quality

- Sonar analysis run for all changed production/test files.
- Security hotspots/vulnerabilities: none reported for changed production files.
- Targeted tests (`ContractBehaviorIT`, `TimeEntryBatchIntegrationTest`): `passed=17, failed=0`.
- Module test run (`pos-people` test files): `passed=61, failed=10`, with failures isolated to pre-existing `UserPersonLinkControllerIT` expectations (400/404 mismatches) not introduced by this change.

## Completion Details

- Branch: `cap/120`
- Commit: `c5942fad5e061f5670cf0e68d06a64647f544e88`
- Push: completed (`origin/cap/120`)
- Files changed from `main...cap/120`:
  - `pos-people/src/main/java/com/positivity/people/internal/controller/PeopleReportsController.java`
  - `pos-people/src/main/java/com/positivity/people/internal/controller/PersonAccessController.java`
  - `pos-people/src/main/java/com/positivity/people/internal/dto/AttendanceDiscrepancyReportResponse.java`
  - `pos-people/src/main/java/com/positivity/people/internal/repository/TimeEntryRepository.java`
  - `pos-people/src/main/java/com/positivity/people/service/PeopleReportsService.java`
  - `pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java`
  - `pos-people/src/test/java/com/positivity/people/controller/TimeEntryBatchIntegrationTest.java`
- Test summary:
  - Targeted: `passed=17, failed=0`
  - Full module file-set run: `passed=61, failed=10` (existing unrelated failures)
