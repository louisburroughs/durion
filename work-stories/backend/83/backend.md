Title: [BACKEND] [STORY] Timekeeping: Manager Approves/Rejects Time Entries
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/83
Labels: type:story, domain:people, status:ready-for-dev

## Story Intent
As a **Manager**, I want **to review and approve or reject submitted time entries for my direct reports for a given pay period**, so that **employee time is finalized and auditable, enabling accurate payroll processing and labor cost allocation**.

## Actors & Stakeholders
- **Manager (Primary Actor)**: A user authorized to review/approve time for assigned employees.
- **Employee (Indirect Actor)**: The user whose time is being reviewed.
- **System (Actor)**: Enforces rules and manages state transitions.
- **Payroll System (Stakeholder)**: Consumes approved time for payroll processing.
- **Work Execution System (Stakeholder)**: May consume approved job time for labor cost posting/analysis.

## Preconditions
1. Manager is authenticated and authorized.
2. Time entries exist for `{employeeId, timePeriodId}`.
3. The time period meets the gating rules for approval (see Business Rules).

## Functional Behavior
### View Pending Entries
1. Manager selects an employee and a pay period.
2. System returns all `TimeEntry` records for `{employeeId, timePeriodId}` filtered by status (primarily `PENDING_APPROVAL`).

### Approve Period (Atomic)
1. Manager triggers **Approve** for `{employeeId, timePeriodId}`.
2. System validates:
   - Manager has `timekeeping:approve` AND is authorized for the employee (direct report / configured reporting scope).
   - TimePeriod is at least `SUBMISSION_CLOSED`.
   - All entries for `{employeeId, timePeriodId}` are in `PENDING_APPROVAL`.
3. System transitions all associated `TimeEntry.status` from `PENDING_APPROVAL` → `APPROVED` in a single transaction.
4. System creates one append-only `TimePeriodApproval` record with `finalStatus=APPROVED`.
5. System writes an immutable audit log entry.
6. System publishes `TimeEntriesApprovedEvent` post-commit.

### Reject Period (Atomic)
1. Manager triggers **Reject** for `{employeeId, timePeriodId}` and supplies required rejection metadata.
2. System validates:
   - Manager has `timekeeping:approve` AND is authorized for the employee.
   - TimePeriod is at least `SUBMISSION_CLOSED`.
   - All entries for `{employeeId, timePeriodId}` are in `PENDING_APPROVAL`.
   - `rejectionReasonCode` is present AND `rejectionNotes` is non-empty (trimmed).
3. System records the rejection as period-level history:
   - Creates one append-only `TimePeriodApproval` record with `finalStatus=REJECTED`, reasonCode, and notes.
4. System returns entries to the employee for correction:
   - Canonical persisted end state is editable, so entries are set to `DRAFT` for correction/resubmission.
   - Rejection history remains preserved via `TimePeriodApproval` + audit trail.
5. System writes an immutable audit log entry.
6. System publishes `TimeEntriesRejectedEvent` post-commit (includes reasonCode + notes).

## Alternate / Error Flows
- **Reject without reasonCode/notes**: return `400 Bad Request` with field-level errors.
- **Non-pending entries in period**: if any entries for `{employeeId, timePeriodId}` are not `PENDING_APPROVAL`, return `409 Conflict` and include which entry IDs blocked approval/rejection.
- **Authorization failure**: return `403 Forbidden` (do not leak reporting relationship details).
- **Concurrent modification**: if entries change during processing, return `409 Conflict`.

## Business Rules
- **Approval/Reject is period-atomic**: the action applies to all time entries for `{employeeId, timePeriodId}` currently in `PENDING_APPROVAL`. No partial approval/rejection.
- **Canonical status model**:
  - `TimeEntry.status` is the line-item state.
  - `TimePeriodApproval.finalStatus` is the period-level decision and is append-only history.
- **Canonical `TimeEntry` transitions**:
  - Employee: `DRAFT → SUBMITTED → PENDING_APPROVAL`
  - Manager: `PENDING_APPROVAL → APPROVED`
  - Rejection outcome: rejection is recorded, but entries are returned for correction as `DRAFT` (rejection history preserved).
- **Time period gating** (source of truth):
  - `TimePeriod.status` includes at least: `OPEN`, `SUBMISSION_CLOSED`, `PAYROLL_CLOSED`.
  - Manager approval/rejection requires `TimePeriod.status >= SUBMISSION_CLOSED`.
  - Controlled adjustments require `TimePeriod.status < PAYROLL_CLOSED`.
- **Immutability + controlled adjustments**:
  - Approved `TimeEntry` rows are immutable to standard endpoints.
  - Post-approval changes are performed via `TimeEntryAdjustment` linked to the original entry, with full audit trail (see Data Requirements).
  - For this story, the minimum required is enforcing immutability and providing the adjustment scaffolding hooks; adjustment UX/flow may be covered by a separate story.
- **Idempotency**:
  - Retrying the same approve/reject request should be safe; if no state changes are needed return `200 OK`.

## Data Requirements
### `TimeEntry`
- **Fields (minimum)**: `id`, `employeeId`, `timePeriodId`, `status`, `clockIn`, `clockOut`, optional `jobCode`.
- **Status enum**: `DRAFT`, `SUBMITTED`, `PENDING_APPROVAL`, `APPROVED` (and optionally `REJECTED` if used transiently).

### `TimePeriod`
- **Fields (minimum)**: `id`, `status` (`OPEN|SUBMISSION_CLOSED|PAYROLL_CLOSED`), plus configuration fields from Clarification #265 (timezone, payPeriodType, etc.).

### `TimePeriodApproval` (append-only history)
- **Fields**: `id`, `employeeId`, `timePeriodId`, `finalStatus (APPROVED|REJECTED)`, `approvingManagerId`, `rejectionReasonCode`, `rejectionNotes`, `processedAt`, `policyVersion`, optional `requestId`.
- **Behavior**: create one record per approve/reject action; maintain history.

### `TimeEntryAdjustment` (controlled adjustment)
- **Fields**: `id`, `originalTimeEntryId`, `adjustmentDeltaMinutes`, `reasonCode`, `notes`, `status (DRAFT|APPROVED|REJECTED)`, `approvedAt`.
- **Constraints**: allowed only after approval and before payroll close; requires reasonCode and notes; full audit trail.

## Acceptance Criteria
### AC1: Approve pending entries for a period
- Given all entries for `{employeeId, timePeriodId}` are `PENDING_APPROVAL` and `TimePeriod.status >= SUBMISSION_CLOSED`
- When manager approves
- Then all entries become `APPROVED`
- And a `TimePeriodApproval(finalStatus=APPROVED)` record is created
- And an audit entry is written
- And `TimeEntriesApprovedEvent` is published post-commit

### AC2: Reject pending entries for a period (requires reason)
- Given all entries for `{employeeId, timePeriodId}` are `PENDING_APPROVAL` and `TimePeriod.status >= SUBMISSION_CLOSED`
- When manager rejects with `rejectionReasonCode` and non-empty `rejectionNotes`
- Then a `TimePeriodApproval(finalStatus=REJECTED)` record is created with the reason metadata
- And entries are returned to the employee as `DRAFT`
- And an audit entry is written
- And `TimeEntriesRejectedEvent` is published post-commit

### AC3: Reject without required reason metadata
- Given manager attempts to reject without reason code and/or notes
- When reject is submitted
- Then API returns `400 Bad Request`
- And no entry statuses change

### AC4: Mixed statuses prevent period decision
- Given any entry for `{employeeId, timePeriodId}` is not `PENDING_APPROVAL`
- When manager attempts approve/reject
- Then API returns `409 Conflict` and identifies blocking `timeEntryIds`

### AC5: Approved entries are immutable
- Given a `TimeEntry` is `APPROVED`
- When a standard update endpoint is called
- Then the update is rejected
- And the system requires use of the adjustment workflow for post-approval changes

## Audit & Observability
- **Audit log (immutable)** per approve/reject action must include: `timestamp`, `actingManagerId`, `affectedEmployeeId`, `timePeriodId`, `action`, `timeEntryIds`, `rejectionReasonCode/rejectionNotes` (if rejected), `policyVersion`, optional `requestId`, `ip`, `userAgent`.
- **Events (post-commit)**:
  - `TimeEntriesApprovedEvent`
  - `TimeEntriesRejectedEvent`
  - Payload includes: `employeeId`, `timePeriodId`, `actedByManagerId`, `processedAt`, `finalStatus`, and `timeEntryIds` (recommended).
- **Notifications**: in-app/email notifications are handled asynchronously by consumers; the approval/reject API must not block on email delivery.
- **Metrics**: count approvals/rejections over time, optionally per manager.

## Open Questions
None.

## Original Story (Unmodified – For Traceability)
# Issue #83 — [BACKEND] [STORY] Timekeeping: Manager Approves/Rejects Time Entries

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Manager Approves/Rejects Time Entries

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Manager**, I want **to approve or reject time entries** so that **time is locked and ready for export and reconciliation**.

## Details
- Reject requires reason.
- Approved becomes read-only except controlled adjustment.

## Acceptance Criteria
- Approve/reject per person per period.
- Reason required on rejection.
- Audit trail includes actor and changes.

## Integration Points (workexec/shopmgr)
- Optional: workexec uses approved job time for labor posting.

## Data / Entities
- TimeEntryApproval

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