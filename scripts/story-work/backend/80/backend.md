Title: [BACKEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/80
Labels: type:story, domain:people, status:ready-for-dev, agent:story-authoring, agent:people

**Rewrite Variant:** crm-pragmatic  
**Status:** Ready-for-dev (clarification #403 applied)

## Story Intent
As a **Manager**, I want a report that compares a technician's total attendance time (time clocked in) against their total productive time (time logged on jobs) for a given period, so that I can efficiently identify and investigate significant discrepancies, such as unaccounted-for time, potential payroll padding, or operational overhead.

## Actors & Stakeholders
- **Manager**: Requests and consumes the report.
- **Technician**: Subject of the report.
- **People domain**: Owns attendance time, threshold policy, and report generation.
- **Work Execution domain (`pos-workexec`)**: **System of record** for job-time totals (approved productive labor), exposed via an API contract consumed by People domain.

## Preconditions
- Manager is authenticated and authorized to view the requested scope.
- Caller provides `startDate`, `endDate` (inclusive), and `timezone` (IANA TZ) for day-boundary definitions.
- Attendance records exist in People domain for the date range (or an empty result is valid).
- Job time records exist in Work Execution domain for the date range (or an empty result is valid).

## Functional Behavior
1. Manager calls the People-domain report endpoint with `startDate`, `endDate`, `timezone`, and optional scoping parameters.
2. People domain computes per **technician + location + local day**:
   - **Total Attendance Time** (minutes), aggregated by local day in the requested `timezone`.
3. People domain queries Work Execution **once per report** (bulk), using the Work Execution contract for job-time totals.
4. People domain joins Work Execution results by grouping key:
   - `technicianId + locationId + localDate`
5. People domain computes discrepancy:
   - `discrepancyMinutes = attendanceMinutes - totalJobMinutes`
6. People domain resolves the effective threshold (global default with optional per-location override).
7. People domain flags exceptions:
   - `isFlagged = abs(discrepancyMinutes) > thresholdMinutes`
8. People domain returns per-row summary including the threshold applied.

## Alternate / Error Flows
- **No attendance data**:
  - Include a row only if `technicianIds` is explicitly requested OR there is job time for that day.
  - Otherwise omit the row to avoid noisy all-zero output.
- **No job time data**:
  - If attendance exists, treat job time as 0 and discrepancy equals attendance.
- **Work Execution dependency failure**:
  - On any Work Execution non-2xx (e.g., 400/403/503/500), People domain fails the report request (no partial data in v1).
- **Unauthorized**:
  - If manager requests out-of-scope location/technician, return `403 Forbidden` (do not silently filter).

## Business Rules
- **Report granularity**: per technician + location + local day.
- **Timezone/day boundaries**: use request param `timezone` (IANA TZ) to define the local day.
- **Attendance aggregation (People domain)**:
  - Sum clock-in/out pair durations in minutes.
  - Split cross-midnight entries at the day boundary (per requested `timezone`).
  - For open/unclosed attendance entries: cap at day end (or “now” if reporting includes today) and log a warning; do not fail the report.
- **Job time aggregation (Work Execution domain; authoritative)**:
  - People domain must not compute or infer job time.
  - People domain consumes Work Execution daily totals (minutes int) and does not split or recompute days.
  - Job time definition: **approved productive labor time only** (excludes draft/rejected/in-progress; excludes travel/breaks unless modeled as labor in Work Execution).
- **Threshold resolution** (People domain timekeeping policy):
  1. Location override where `scopeType=LOCATION` and `scopeId=locationId` and effective for the report date
  2. Global default where `scopeType=GLOBAL`
- **Default threshold**: `jobTimeDiscrepancyThresholdMinutes = 30`

## Data Requirements
### API Contract (People domain)

**Endpoint**
- `GET /api/people/reports/attendance-jobtime-discrepancy`

**Query params**
- `startDate` (required, `YYYY-MM-DD`)
- `endDate` (required, `YYYY-MM-DD`, inclusive)
- `timezone` (required, IANA TZ, e.g. `America/Chicago`)
- `locationId` (optional; if omitted, scope is “all locations manager can access”)
- `technicianIds` (optional; if omitted, include all technicians in scope)
- `flaggedOnly` (optional, default `false`)

**Response** (per technician *per day* *per location*)
- Store/compute internally in **minutes**.
- Return time fields as **decimal hours** rounded to **2 decimals**.
- Return `thresholdApplied` in **minutes** (int).

Fields:
- `technicianId`
- `technicianName`
- `locationId`
- `reportDate` (YYYY-MM-DD)
- `totalAttendanceHours`
- `totalJobHours`
- `discrepancyHours`
- `isFlagged`
- `thresholdApplied`

### API Contract (Work Execution domain) — resolved by clarification #403

**Authoritative system:** Work Execution (`pos-workexec`) is the sole system of record for job-time totals.

**Endpoint (required)**
- `GET /api/workexec/job-time-totals`

**Query params**
- `startDate` (required, `YYYY-MM-DD`, inclusive)
- `endDate` (required, `YYYY-MM-DD`, inclusive)
- `timezone` (required, IANA TZ)
- `locationId` (optional; if omitted, all authorized locations)
- `technicianIds` (optional list<UUID>; if omitted, all technicians in scope)

**Response row shape**
```json
{
  "technicianId": "uuid",
  "locationId": 123,
  "localDate": "YYYY-MM-DD",
  "totalJobMinutes": 360
}
```

**Semantics**
- Grouping keys: `technicianId + locationId + localDate`
- Units: minutes (integer)
- Included: approved/finalized labor only
- Excluded: draft/rejected/in-progress; non-labor (travel/breaks) unless modeled as labor

**Error taxonomy (stable codes)**
- `403` `WORKEXEC_FORBIDDEN`
- `400` `WORKEXEC_INVALID_REQUEST`
- `503` `WORKEXEC_UNAVAILABLE`
- `500` `WORKEXEC_INTERNAL_ERROR`

**Cross-domain contract rule**
- On any Work Execution non-2xx, People domain fails the report (no partial data).

### Policy Entity (People domain)

**TimekeepingPolicy**
- `scopeType`: GLOBAL | LOCATION
- `scopeId`: null for GLOBAL, `locationId` for LOCATION
- `jobTimeDiscrepancyThresholdMinutes`: int
- `effectiveStartAt`, `effectiveEndAt`: optional
- audit fields: `updatedBy`, `updatedAt`

## Acceptance Criteria

**Scenario 1: Discrepancy Above Threshold**
- Given attendance is 8.0 hours and job time is 6.0 hours for the same day
- And threshold is 60 minutes
- When the report is generated
- Then discrepancy is 2.0 hours and `isFlagged=true`

**Scenario 2: Discrepancy Below Threshold**
- Given attendance is 8.0 hours and job time is 7.5 hours
- And threshold is 60 minutes
- When the report is generated
- Then discrepancy is 0.5 hours and `isFlagged=false`

**Scenario 3: Job Time Exceeds Attendance (Data Anomaly)**
- Given attendance is 8.0 hours and job time is 9.5 hours
- And threshold is 60 minutes
- When the report is generated
- Then discrepancy is -1.5 hours and `isFlagged=true`

**Scenario 4: No Discrepancy**
- Given attendance is 8.0 hours and job time is 8.0 hours
- And threshold is 60 minutes
- When the report is generated
- Then discrepancy is 0.0 hours and `isFlagged=false`

**Scenario 5: Location Override Threshold**
- Given global threshold is 30 minutes and Location A override is 60 minutes
- When a 45-minute discrepancy is reported for Location A
- Then thresholdApplied is 60 and `isFlagged=false`

## Audit & Observability
- **Audit event (required)**
  - Name: `REPORT_ATTENDANCE_VS_JOBTIME_GENERATED`
  - Fields: `managerId`, `startDate`, `endDate`, `timezone`, optional `locationId`, optional `technicianIds`, optional `flaggedOnly`, `generatedAt`
- **Metrics**
  - `people.reports.attendance_jobtime_discrepancy.latency`
  - `people.workexec.errors` (tag by status/exception)
  - `people.reports.attendance_jobtime_discrepancy.generated`
- **Logging**
  - On Work Execution failure: error log with correlationId, request scope, upstream status/exception

## Resolved Questions
- Clarification #262: threshold configuration (global default + per-location override; default 30 minutes).
- Clarification #403: Work Execution is SoR for job-time totals; bulk endpoint contract defined and adopted above.

## Original Story (Unmodified – For Traceability)
# Issue #80 — [BACKEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Integration: Attendance vs Job Time Discrepancy Report

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Manager**, I want **a report comparing attendance time to job time** so that **I can identify gaps, overhead, and anomalies**.

## Details
- Summarize by technician/day/location.
- Flag differences above a configurable threshold.

## Acceptance Criteria
- Report shows clocked hours vs job timer total.
- Flags exceptions.

## Integration Points (workexec)
- Optional: correlate with labor lines for reconciliation.

## Data / Entities
- TimeEntry
- JobLink

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access