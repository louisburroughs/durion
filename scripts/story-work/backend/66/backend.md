Title: [BACKEND] [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/66
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
**As a** Shop Manager,
**I want to** review, approve, or reject submitted time entries for a given work order or day,
**so that** employee hours are finalized and locked for accurate payroll processing.

## Actors & Stakeholders
- **Shop Manager (Primary Actor):** The user responsible for reviewing and approving time submissions. They hold the authority to lock time entries.
- **Mechanic / Service Advisor (Indirect Actor):** The user who initially submits their time entries for approval.
- **Payroll / HR System (Downstream System):** A system that consumes the final, approved time data to process payroll.
- **System (Actor):** The POS system which enforces business rules and records state changes.

## Preconditions
1. The Shop Manager is authenticated and has the necessary permissions (`TimeEntry:Approve`, `TimeEntry:Reject`) to manage time entries.
2. There are one or more employee time entries in a `PENDING_APPROVAL` state associated with a specific Work Order or date range.
3. The system can access the relevant Work Order and Employee records.

## Functional Behavior

### 1. Approve Time Entry
- **Trigger:** The Shop Manager selects one or more time entries in a `PENDING_APPROVAL` state and triggers the "Approve" action.
- **System Action:**
    1. The system validates that the Shop Manager has the required permissions.
    2. The system validates that each selected time entry is in the `PENDING_APPROVAL` state.
    3. For each entry, the system transitions its state from `PENDING_APPROVAL` to `APPROVED`.
    4. The system records the ID of the approving manager and the UTC timestamp of the approval.
    5. The system locks the `APPROVED` time entry, making it immutable to further changes from this workflow.
    6. A `TimeEntryApproved` event is emitted for downstream consumers (e.g., HR/Payroll System).

### 2. Reject Time Entry
- **Trigger:** The Shop Manager selects one or more time entries in a `PENDING_APPROVAL` state and triggers the "Reject" action, providing a mandatory reason for rejection.
- **System Action:**
    1. The system validates that the Shop Manager has the required permissions.
    2. The system validates that each selected time entry is in the `PENDING_APPROVAL` state.
    3. For each entry, the system transitions its state from `PENDING_APPROVAL` to `REJECTED`.
    4. The system records the ID of the rejecting manager, the UTC timestamp of the rejection, and the provided rejection reason.
    5. The `REJECTED` time entry is unlocked for the original submitter to correct and resubmit.

## Alternate / Error Flows
- **Error - Insufficient Permissions:** If the user lacks `TimeEntry:Approve` or `TimeEntry:Reject` permissions, the system denies the action and returns an authorization error.
- **Error - Invalid State:** If any selected time entry is not in the `PENDING_APPROVAL` state (e.g., it is already `APPROVED` or `DRAFT`), the system rejects the batch operation for that entry and returns an error specifying the invalid state conflict.
- **Error - Rejection without Reason:** If the "Reject" action is triggered without a reason, the system prevents the state change and returns a validation error requiring a reason.

## Business Rules
- Only users with the 'Shop Manager' role (or equivalent permissions) can approve or reject time entries.
- Time entries in the `APPROVED` state are considered immutable and cannot be altered, deleted, or rejected. Any changes require a separate, audited adjustment process (see Resolved Questions).
- A reason is mandatory for all rejections to provide feedback to the employee.
- Approval and rejection actions must be recorded in an audit log, including the actor, timestamp, and target time entry.

## Data Requirements
**Time Entry Entity**
- `timeEntryId`: Unique identifier for the time entry.
- `workOrderId`: Foreign key to the associated work order.
- `employeeId`: Foreign key to the employee who performed the work.
- `startTimeUtc`: Start time of the work period.
- `endTimeUtc`: End time of the work period.
- `status`: State of the entry (e.g., `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`).
- `submittedAtUtc`: Timestamp of when the entry was submitted for approval.
- `decisionByUserId`: ID of the manager who approved/rejected the entry.
- `decisionAtUtc`: Timestamp of the approval/rejection.
- `rejectionReason`: Text field, populated only if `status` is `REJECTED`.

**Time Entry Approval Event** (`TimeEntryApproved`)
- `eventId`: Unique event ID.
- `timestamp`: Event creation timestamp.
- `timeEntryId`: The ID of the approved time entry.
- `workOrderId`: The associated work order ID.
- `employeeId`: The associated employee ID.
- `approvedHours`: The calculated total hours for the entry.
- `approvedByUserId`: The ID of the approving manager.

## Acceptance Criteria

### AC1: Successful Approval of a Time Entry
- **Given** a time entry exists with status `PENDING_APPROVAL`.
- **And** I am logged in as a Shop Manager with approval permissions.
- **When** I execute the "Approve" action on that time entry.
- **Then** the system must set the time entry's status to `APPROVED`.
- **And** the `decisionByUserId` and `decisionAtUtc` fields must be populated.
- **And** the time entry becomes read-only.
- **And** a `TimeEntryApproved` event is published.

### AC2: Successful Rejection of a Time Entry
- **Given** a time entry exists with status `PENDING_APPROVAL`.
- **And** I am logged in as a Shop Manager with rejection permissions.
- **When** I execute the "Reject" action on that time entry and provide a reason "Incorrect work order".
- **Then** the system must set the time entry's status to `REJECTED`.
- **And** the `rejectionReason` field must be populated with "Incorrect work order".
- **And** the `decisionByUserId` and `decisionAtUtc` fields must be populated.

### AC3: Attempting to Approve an Already Approved Entry
- **Given** a time entry exists with status `APPROVED`.
- **And** I am logged in as a Shop Manager with approval permissions.
- **When** I attempt to execute the "Approve" action on that time entry.
- **Then** the system must reject the request with an error indicating the entry is in an invalid state.
- **And** the time entry's state must remain `APPROVED`.

## Audit & Observability
- **Audit Log:** Every state transition (`PENDING_APPROVAL` -> `APPROVED` / `REJECTED`) for a `TimeEntry` must be logged. The log entry must include:
    - `timeEntryId`
    - `previousState`
    - `newState`
    - `timestamp`
    - `actorUserId` (The Shop Manager's ID)
    - `rejectionReason` (if applicable)
- **Metrics:** Monitor the count of approved vs. rejected time entries per day.
- **Alerts:** Consider an alert if the average time from submission-to-approval exceeds a defined threshold (e.g., 48 hours).

## Resolved Questions

### RQ1 (Adjustments)
**Question:** How does the adjustment process work for approved time entries? Can managers directly edit time entries before approving them?

**Resolution:** Use **Option B: Separate Adjustment Record** approach. Managers do not directly edit original time entries.

**Data Model - TimeEntry (Immutable once approved):**
```
TimeEntry {
  timeEntryId: UUID
  personId: UUID
  workOrderId: UUID
  startAt: Timestamp
  endAt: Timestamp
  status: Enum (DRAFT, SUBMITTED, APPROVED, REJECTED)
  submittedAt: Timestamp
  approvedBy: UUID (nullable)
  approvedAt: Timestamp (nullable)
}
```

**Data Model - TimeEntryAdjustment:**
```
TimeEntryAdjustment {
  adjustmentId: UUID
  timeEntryId: UUID (FK to TimeEntry)
  requestedBy: UUID (Manager/Technician who proposed adjustment)
  reasonCode: Enum (CLOCK_IN_MISSED, CLOCK_OUT_MISSED, INCORRECT_WORKORDER, etc.)
  notes: String (optional free-text explanation)
  
  // Delta fields - use EITHER proposed times OR minute delta
  proposedStartAt: Timestamp (nullable, preferred approach)
  proposedEndAt: Timestamp (nullable, preferred approach)
  minutesDelta: Integer (nullable, alternative approach)
  
  status: Enum (PROPOSED, APPROVED, REJECTED)
  approvedBy: UUID (nullable)
  approvedAt: Timestamp (nullable)
  createdAt: Timestamp
}
```

**Workflow:**
1. Manager identifies time entry needing correction during review
2. Manager creates `TimeEntryAdjustment` record with:
   - Link to original `TimeEntry`
   - Proposed corrected `startAt` and/or `endAt` times
   - Required `reasonCode` and optional `notes`
   - Status: `PROPOSED`
3. Adjustment requires explicit approval (either by same manager or higher authority, per policy)
4. Once adjustment is `APPROVED`:
   - Original TimeEntry remains unchanged (audit trail)
   - System computes **effective time** from TimeEntry + approved adjustments
5. When querying for payroll/reporting:
   - Join TimeEntry with approved TimeEntryAdjustments
   - Calculate: `effectiveStartAt`, `effectiveEndAt`, `effectiveMinutes`

**Benefits:**
- ✅ Immutable audit trail (original time preserved)
- ✅ Supports multiple adjustments over time
- ✅ Clear approval/rejection workflow
- ✅ Explicit reasoning for all changes

**Rationale:** This approach maintains full auditability while allowing corrections. It answers questions like "What time did the technician originally clock?" and "Why was it adjusted?" which are critical for dispute resolution and labor compliance.

---

### RQ2 (Exceptions)
**Question:** What are the business rules and criteria that define an "Exception"? How should exceptions be flagged and managed?

**Resolution:** Exceptions are **rule-based flags** attached to time entries or day summaries that must be explicitly resolved during approval.

**Exception Criteria (Minimum v1):**
1. **Overlapping Entries:** Same person has overlapping time entries for different work orders
2. **Missing Clock-Out:** Time entry has `startAt` but no `endAt` (open-ended entry)
3. **Exceeds Daily Hours Threshold:** Total daily hours > 12 hours (configurable)
4. **Overtime Threshold:** Weekly hours > 40 hours (configurable, jurisdiction-dependent)
5. **Time Against Closed Work Order:** Time logged to work order with status `CLOSED` or `CANCELLED`
6. **Time During PTO/Unavailable:** Time logged when person marked as PTO or unavailable
7. **Location Mismatch:** Time logged to work order at different location than person's assigned location

**Data Model - TimeException:**
```
TimeException {
  exceptionId: UUID
  timeEntryId: UUID (nullable if exception is for day summary)
  personId: UUID
  workDate: Date
  exceptionCode: Enum (OVERLAPPING_ENTRIES, MISSING_CLOCKOUT, DAILY_HOURS_EXCEEDED, etc.)
  severity: Enum (WARNING, BLOCKING)
  detectedAt: Timestamp
  detectedBy: String (system rule name)
  status: Enum (OPEN, ACKNOWLEDGED, RESOLVED, WAIVED)
  resolvedBy: UUID (nullable)
  resolvedAt: Timestamp (nullable)
  resolutionNotes: String (nullable)
  relatedEntityId: UUID (nullable, for related work order/adjustment)
}
```

**Exception Lifecycle:**
1. **Detection:** Automatic on time entry submission or daily rollup
2. **Status: OPEN** → Exception flagged, visible to manager during approval workflow
3. **Manager Actions:**
   - **ACKNOWLEDGED:** Manager sees it but doesn't block approval (for `WARNING` severity)
   - **RESOLVED:** Manager takes corrective action (e.g., creates adjustment, splits entry)
   - **WAIVED:** Manager explicitly waives with justification (requires `resolutionNotes`)
4. **Approval Rules:**
   - `WARNING` severity: Can approve with acknowledgment
   - `BLOCKING` severity: MUST be RESOLVED or WAIVED before approval allowed

**UI Integration:**
- Display exception badge/indicator on time entries in approval queue
- Require manager to click through and review each exception
- Force explicit action (resolve/waive with notes) for BLOCKING exceptions

**Rationale:** Structured exception handling prevents payroll errors, ensures labor law compliance, and provides clear audit trail of manager decisions. Severity levels balance strictness with operational flexibility.

---

### RQ3 (HR Integration Contract)
**Question:** What is the precise data contract for the integration with the HR system? Is it push/pull? What specific fields are required?

**Resolution:** **Push-based async events** via message queue. HR/Payroll system consumes events; Timekeeping does not require HR response for operational workflow.

**Integration Pattern:**
- **Type:** Asynchronous event push (fire-and-forget from Timekeeping perspective)
- **Transport:** Message queue topic: `timekeeping.daily_totals.approved.v1`
- **Reliability:** At-least-once delivery; HR must handle idempotency

**Event Schema - DailyTimeApproved:**
```json
{
  "eventId": "uuid",
  "eventType": "DailyTimeApproved",
  "schemaVersion": "1.0.0",
  "occurredAt": "2025-01-15T18:30:00Z",
  "payload": {
    "personId": "uuid",
    "locationId": "uuid",
    "workDate": "2025-01-15",
    "totals": {
      "totalMinutes": 480,
      "regularMinutes": 450,
      "overtimeMinutes": 30,
      "breakMinutes": 30
    },
    "byWorkOrder": [
      {
        "workOrderId": "WO-123",
        "minutes": 240
      },
      {
        "workOrderId": "WO-456",
        "minutes": 210
      }
    ],
    "approval": {
      "approvedBy": "uuid",
      "approvedAt": "2025-01-15T18:30:00Z"
    }
  }
}
```

**Critical Fields:**
- ✅ `personId`, `workDate`, `locationId` - Core identifiers
- ✅ `totalMinutes` - Total approved time
- ✅ `regularMinutes`, `overtimeMinutes` - Split for OT calculations
- ✅ `byWorkOrder[]` - Allocation breakdown for job costing
- ✅ `approvedBy`, `approvedAt` - Audit metadata

**Excluded from v1:**
- ❌ **Pay Rates:** HR/Payroll owns rate data and pay rules
- ❌ **Dollar Amounts:** Timekeeping provides hours/minutes only
- ❌ **Tax Withholdings:** Payroll system responsibility
- ❌ **Benefits/Deductions:** Payroll system responsibility

**Timekeeping System Responsibilities:**
- Capture time worked (hours/minutes)
- Categorize time (regular, OT, break)
- Allocate time to work orders
- Provide approval metadata

**HR/Payroll System Responsibilities:**
- Apply pay rates
- Calculate gross pay
- Apply tax rules
- Process benefits/deductions
- Generate paychecks

**Error Handling:**
- Timekeeping logs event publication success
- HR consumes asynchronously; failures are HR's responsibility to handle (retry, DLQ)
- No synchronous ACK required from HR to complete approval workflow

**Rationale:** Async integration prevents operational workflow (time approval) from being blocked by HR system downtime. Clean separation of concerns: Timekeeping tracks time, HR/Payroll handles compensation. This follows microservices best practices and maintains domain boundaries.

## Original Story (Unmodified – For Traceability)
# Issue #66 — [BACKEND] [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to approve time submissions so that time becomes locked for payroll.

## Details
- Approve/reject with reason.
- Adjustments via delta entry.

## Acceptance Criteria
- Approved locked.
- Adjustments tracked.
- Exceptions list supported.

## Integrations
- HR receives approval state and totals.

## Data / Entities
- TimeApproval, AdjustmentEntry, ExceptionFlag

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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