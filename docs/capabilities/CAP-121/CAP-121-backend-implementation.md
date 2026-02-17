# CAP-121 Backend Implementation (Issue #80)

## Overview
Implemented backend fulfillment for CAP-121 in `pos-people` for attendance vs job-time discrepancy reporting, aligned to backend child issue #80 and its clarifications.

## What Was Implemented

### 1) Endpoint + Service Contract
- Implemented `GET /v1/people/reports/attendanceJobtimeDiscrepancy` in `PeopleReportsController`.
- Added required query parameters:
  - `startDate`
  - `endDate`
  - `timezone`
  - optional `locationId`
  - optional `technicianIds`
  - optional `flaggedOnly`
- Added audit event emission:
  - `@EmitEvent(id = "REPORT_ATTENDANCE_VS_JOBTIME_GENERATED", apiVersion = "1")`

### 2) Report Logic
- Replaced placeholder status-count implementation with full discrepancy logic in `PeopleReportsService`:
  - Attendance aggregation by technician + location + local day.
  - Cross-midnight split by requested timezone day boundaries.
  - Open attendance entries capped using `now` and report window end.
  - Workexec bulk fetch and join on `technicianId + locationId + localDate`.
  - Discrepancy calculation:
    - `discrepancyMinutes = attendanceMinutes - totalJobMinutes`
  - Flagging rule:
    - `isFlagged = abs(discrepancyMinutes) > thresholdMinutes`
  - Optional `flaggedOnly` filtering.
  - Stable result ordering by `reportDate`, `technicianName`, `technicianId`.

### 3) Threshold Policy
- Added policy model and repository:
  - `TimekeepingPolicy`
  - `TimekeepingPolicyRepository`
  - `TimekeepingPolicyScopeType` (`GLOBAL`, `LOCATION`)
- Implemented threshold resolution:
  1. Effective location-scoped policy
  2. Effective global policy
  3. Default `30` minutes

### 4) Workexec Integration
- Added Workexec client integration:
  - `WorkexecJobTimeClient`
  - `WorkexecJobTimeTotal` DTO
  - `WorkexecClientException`
- Uses bulk endpoint contract:
  - `/v1/workexec/job-time-totals`
- Added error-code mapping for non-2xx responses:
  - `WORKEXEC_FORBIDDEN`
  - `WORKEXEC_INVALID_REQUEST`
  - `WORKEXEC_UNAVAILABLE`
  - `WORKEXEC_INTERNAL_ERROR`

### 5) Validation & Error Handling
- Added timezone and date-range validation.
- Extended `PeopleExceptionHandler` to map `WorkexecClientException` with status and `errorCode` in `ProblemDetail`.

### 6) Data/Repository Support
- Extended `TimeEntry` with attendance/location fields used by reporting:
  - `locationId`
  - `attendanceStartAt`
  - `attendanceEndAt`
- Added attendance window query to `TimeEntryRepository`.
- Added `AttendanceReportKey` for grouping logic.

### 7) Events Registration
- Registered report event type in `PeopleEventTypes`:
  - `REPORT_ATTENDANCE_VS_JOBTIME_GENERATED`

### 8) Provider Behavioral Contract Tests
- Updated `pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java` with report-focused provider behaviors:
  - Happy path discrepancy above threshold (flagged)
  - Strict threshold invariant (`>` not `>=`)
  - Validation error for invalid timezone
  - Auth failure for unauthenticated request
  - Dependency failure path on Workexec non-2xx

## Sonar / Static Analysis
- Sonar file analysis was triggered for changed report files.
- Connected Mode security hotspot retrieval was unavailable in this environment (workspace not bound to remote Sonar project).
- Static issues surfaced in editor checks for `PeopleReportsService` were resolved (loop flow, null-safety accumulation, documented fallback catch).

## Verification

### Focused test
```bash
runTests: ContractBehaviorIT -> passed (17/17)
```

### Module validation
```bash
./mvnw -pl pos-people -am clean test
```
Result: pass (verified via terminal marker `POS_PEOPLE_TESTS_OK`).

## Completion Details

### Branch
`cap/CAP121`

### Commit hash
`729ad8c6b260d8166101095eb862dcb0e7358139`

### Files changed
```text
pos-people/src/main/java/com/positivity/people/internal/client/WorkexecClientException.java
pos-people/src/main/java/com/positivity/people/internal/client/WorkexecJobTimeClient.java
pos-people/src/main/java/com/positivity/people/internal/client/dto/WorkexecJobTimeTotal.java
pos-people/src/main/java/com/positivity/people/internal/config/PeopleEventTypes.java
pos-people/src/main/java/com/positivity/people/internal/config/RestClientConfig.java
pos-people/src/main/java/com/positivity/people/internal/controller/PeopleExceptionHandler.java
pos-people/src/main/java/com/positivity/people/internal/controller/PeopleReportsController.java
pos-people/src/main/java/com/positivity/people/internal/dto/AttendanceDiscrepancyReportResponse.java
pos-people/src/main/java/com/positivity/people/internal/dto/AttendanceReportKey.java
pos-people/src/main/java/com/positivity/people/internal/entity/TimeEntry.java
pos-people/src/main/java/com/positivity/people/internal/entity/TimekeepingPolicy.java
pos-people/src/main/java/com/positivity/people/internal/enums/TimekeepingPolicyScopeType.java
pos-people/src/main/java/com/positivity/people/internal/repository/TimeEntryRepository.java
pos-people/src/main/java/com/positivity/people/internal/repository/TimekeepingPolicyRepository.java
pos-people/src/main/java/com/positivity/people/service/PeopleReportsService.java
pos-people/src/test/java/com/positivity/people/ContractBehaviorIT.java
```

### Push verification

```text
729ad8c6b260d8166101095eb862dcb0e7358139        refs/heads/cap/CAP121
```
