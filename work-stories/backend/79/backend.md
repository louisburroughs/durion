Title: [BACKEND] [STORY] Timekeeping: Export Approved Time for Accounting/Payroll
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/79
Labels: type:story, domain:accounting, status:ready-for-dev, agent:story-authoring, agent:accounting, agent:people

## Story Intent
As an Accounting Clerk, I need to export approved time entries for a date range and location(s), so payroll and cost accounting can be processed accurately using the payroll-facing identifiers and a stable definition of “APPROVED”.

## Actors & Stakeholders
- **Primary actor:** Accounting Clerk
- **Primary owner of export contract + payroll rules (Decision):** Accounting (`domain:accounting`)
- **System of record for timekeeping data + approval state (Decision):** People/Timekeeping (`domain:people`)
- **Auditor:** requires immutable audit trail of sensitive exports

## Preconditions
- Accounting Clerk is authorized to export time.
- Time entries exist in `domain:people` with a well-defined `APPROVED` state.
- Payroll-facing identifier mappings exist in `domain:accounting` (or export will exclude unmapped entries).

## Functional Behavior
### 1) Request export (Accounting-owned)
- Accounting exposes a secure export function (API and/or scheduled job) that supports:
  - `startDate` (inclusive)
  - `endDate` (inclusive)
  - `locationId` (one or more)
  - `format` (`CSV` or `JSON`)

### 2) Retrieve source data (People-owned)
- Accounting retrieves time entries from People via a stable read contract (API or events) for entries that are:
  - in state `APPROVED`
  - and within the requested date range and location(s)

### 3) Map identifiers (Accounting-owned) — critical
- Export uses **payroll/accounting identifiers** as authoritative.
- Accounting maps:
  - `timekeepingEmployeeId → payrollEmployeeId`
  - `timekeepingLocationId → payrollLocationId / costCenterCode`

### 4) Handle missing mappings
- If a required mapping is missing for an entry, the entry MUST NOT be exported.
- The system records a remediation artifact (e.g., export error record/log/dead-letter) identifying the missing mapping.

### 5) Assemble file + deliver
- Assemble mapped approved entries into `CSV` or `JSON`.
- Provide download (pull) and/or store for retrieval depending on deployment approach.

### 6) Audit
- Record an immutable audit event for each export request (parameters + outcome).

## Alternate / Error Flows
- No matching approved entries → return `200` with empty dataset (CSV header only or empty JSON array).
- Invalid parameters (e.g., `endDate < startDate`, unknown locations) → `400`.
- Unauthorized → `403`.
- People/timekeeping service unavailable → `503`.

## Business Rules
- Export includes **only** entries in exact state `APPROVED`.
- Date range is inclusive.
- Export is idempotent: same request yields same output if source data and mappings are unchanged.

## Decisions Applied (from 2026-01-14 Decision Record)
### Domain ownership
- `domain:accounting` owns export contract, schema/versioning, transformation rules, and reconciliation behavior.
- `domain:people` owns timekeeping data and the approval state machine.

### Authoritative identifiers
- Payroll/accounting IDs are authoritative; do not assume timekeeping IDs match.
- Accounting owns mapping tables/entities (e.g., `PayrollIdentityMap`) from timekeeping IDs to payroll IDs.

### Formal definition of `APPROVED`
A time entry is `APPROVED` when:
1. Required payroll-export validations have passed.
2. An authorized approval action is recorded producing:
   - `approvedAt`
   - `approvedBy`
3. The entry is ready for payroll and should be stable.

Immutability expectation:
- Once `APPROVED`, exported “work facts” must not change silently.
- Post-approval changes require revoke/unapprove, adjustment event, or versioned revision with re-approval.

## Data Requirements
Each exported row/record MUST include payroll-facing identifiers:
- `timeEntryId` (source traceability)
- `payrollEmployeeId` (authoritative)
- `employeeName` (display)
- `payrollLocationId` or `costCenterCode` (authoritative)
- `locationName` (display)
- `entryDate`
- `hoursWorked`
- `approvedAt` (approval timestamp)
- `approvedBy` (approver reference)

## Acceptance Criteria
- **AC1: CSV export includes only approved entries**
  - Given a mix of `APPROVED` and non-approved entries
  - When exporting for a date range/location
  - Then only `APPROVED` entries appear in the output.

- **AC2: Unmapped IDs are excluded and reported**
  - Given an approved time entry with missing employee or location mapping
  - When exporting
  - Then the entry is excluded
  - And a remediation artifact is recorded (missing mapping details).

- **AC3: Empty export returns success**
  - Given no approved entries match the criteria
  - When exporting
  - Then the response is `200` with an empty dataset.

- **AC4: Audit event recorded for export**
  - When an export request completes (success or failure)
  - Then an immutable audit event is recorded with requester, parameters, status, count, and correlationId.

## Audit & Observability
- Audit captures: `requestingPrincipalId`, `sourceIpAddress`, `timestampUtc`, parameters, outcome, `recordsExportedCount`, `correlationId`.
- Metrics:
  - `time_export_requests_total{status,format}`
  - `time_export_duration_seconds`
  - `time_export_records_exported_total`
  - `time_export_records_skipped_total{reason=missing_mapping}`
- Logs must not contain sensitive export contents.

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Record generated by `clarification-resolver.sh` on 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #79 — [BACKEND] [STORY] Timekeeping: Export Approved Time for Accounting/Payroll

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Export Approved Time for Accounting/Payroll

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Accounting user**, I want **to export approved time** so that **it can be used for payroll or cost accounting**.

## Details
- Export by date range and location.
- Provide CSV/JSON output.

## Acceptance Criteria
- Only approved time is exported.
- Export includes person identifiers and location.
- Export activity is audited.

## Integration Points (workexec/shopmgr)
- None required initially.

## Data / Entities
- TimeEntry

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*