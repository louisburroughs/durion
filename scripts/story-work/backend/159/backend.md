Title: [BACKEND] [STORY] Execution: Record Labor Performed
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/159
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** workexec-structured
---
## Story Intent
- **As a:** Technician
- **I want to:** Accurately record the labor I perform on a specific service item of a work order, whether it's measured in hours or as a completed flat-rate task.
- **So that:** My work is correctly tracked, the work order status is updated, and the business has accurate data for job costing and future invoicing.

## Actors & Stakeholders
- **Technician (Primary Actor):** The user performing and recording the labor against an assigned work order item.
- **Service Advisor (Stakeholder):** Manages the work order and may need to review, edit, or approve labor entries.
- **Accounting System (Stakeholder):** A downstream system that consumes `LaborRecorded` events for Work-in-Progress (WIP) and job costing purposes.
- **System (Actor):** The POS/Workshop system responsible for validating and persisting labor entries and emitting events.

## Preconditions
- The Technician is authenticated and has the necessary permissions (`WORKORDER_LABOR_RECORD`) to record labor.
- A Work Order exists with a status that allows labor to be recorded (e.g., 'IN_PROGRESS').
- The Work Order contains one or more service line items that require labor.
- The Technician is assigned to the Work Order or the specific service item, as per system policy.

## Functional Behavior
### Trigger
The Technician initiates the action to record labor against a specific service line item on an active work order.

### Path 1: Record Time-Based Labor
1. The Technician selects a time-based service item from the work order.
2. The System presents an interface to record time (e.g., direct entry of hours).
3. The Technician enters the labor hours (e.g., `1.5`) and optionally adds notes about the work performed.
4. The Technician submits the labor entry.
5. The System validates and persists the entry, creating a `WorkorderLaborEntry` record.
6. The System updates the status of the work order item (e.g., `labor_in_progress`) and/or the parent work order.
7. The System emits a `LaborRecorded` event.

### Path 2: Record Flat-Rate Labor
1. The Technician selects a flat-rate service item from the work order.
2. The System presents an interface to mark the item as complete.
3. The Technician confirms completion and optionally adds notes.
4. The Technician submits the labor entry.
5. The System validates and persists the entry, creating a `WorkorderLaborEntry` record.
6. The System updates the status of the work order item to `labor_complete`.
7. The System emits a `LaborRecorded` event.

## Alternate / Error Flows
- **Error - Unassigned Technician:**
  - **Scenario:** A Technician attempts to record labor on a work order or item they are not assigned to.
  - **System Response:** The system rejects the entry and displays an error message: "You are not assigned to this work order/item. Please see the Service Advisor."

- **Error - Invalid Labor Hours:**
  - **Scenario:** A Technician enters a non-positive or excessively large value for hours on a time-based entry (e.g., `0`, `-5`, or `1000`).
  - **System Response:** The system rejects the entry with a validation error: "Please enter a valid, positive number for labor hours."

- **Error - Work Order in Invalid State:**
  - **Scenario:** A Technician attempts to record labor on a work order that is not in an active state (e.g., 'DRAFT', 'COMPLETED', 'CANCELED').
  - **System Response:** The system rejects the entry with an error message: "Labor cannot be recorded on a work order with status '[WorkOrderStatus]'."

## Business Rules
- `BR1`: All labor entries must be immutably associated with a specific `Workorder` and `WorkorderItem`.
- `BR2`: Each labor entry must be attributed to a single, identified `Technician`.
- `BR3`: The system must support two types of labor recording:
    - `BR3.1`: **Time-Based:** Recorded as a quantity of hours.
    - `BR3.2`: **Flat-Rate:** Recorded as a single completion event.
- `BR4`: Labor entries are immutable. Corrections must be made by creating a new, superseding entry (e.g., a reversal and a new correct entry), which requires specific permissions (`WORKORDER_LABOR_CORRECT`).
- `BR5`: A `LaborRecorded` event must be emitted upon the successful creation of a `WorkorderLaborEntry`. This event is for internal tracking (WIP/costing) and is explicitly non-posting from an Accounts Receivable (AR) perspective.
- `BR6`: The cost of labor is tracked for job costing purposes but is not recognized as revenue until the work order is invoiced.

## Data Requirements
- **Entity:** `WorkorderLaborEntry`
- **Attributes:**
    - `laborEntryId` (PK): Unique identifier for the labor record.
    - `workorderId` (FK): Reference to the parent `Workorder`.
    - `workorderItemSeqId` (FK): Reference to the specific `WorkorderItem`.
    - `technicianId` (FK): Reference to the `Technician` who performed the labor.
    - `laborType` (Enum: `TIME_BASED`, `FLAT_RATE`): The type of labor being recorded.
    - `laborHours` (Decimal): Hours recorded for `TIME_BASED` labor. Null for `FLAT_RATE`.
    - `isComplete` (Boolean): Flag indicating completion for `FLAT_RATE` labor.
    - `notes` (Text, Optional): Technician's notes.
    - `entryTimestamp` (Timestamp): When the record was created in the system.
    - `version` (Integer): For optimistic locking and idempotency key generation.

## Acceptance Criteria
- **AC1: Record Time-Based Labor Successfully**
  - **Given:** A Technician is assigned to a work order with a time-based service item.
  - **When:** The Technician records 2.5 hours of labor against that service item.
  - **Then:** The system creates a `WorkorderLaborEntry` with `laborType` = `TIME_BASED` and `laborHours` = 2.5.
  - **And:** The system emits a `LaborRecorded` event with the correct details.

- **AC2: Record Flat-Rate Labor Successfully**
  - **Given:** A Technician is assigned to a work order with a flat-rate service item.
  - **When:** The Technician marks the flat-rate item as complete.
  - **Then:** The system creates a `WorkorderLaborEntry` with `laborType` = `FLAT_RATE` and `isComplete` = true.
  - **And:** The system emits a `LaborRecorded` event with the corresponding details.

- **AC3: Verify Event is Non-Posting for Accounts Receivable**
  - **Given:** A `LaborRecorded` event is emitted.
  - **When:** The Accounting domain consumes and processes the event.
  - **Then:** No General Ledger entries for Accounts Receivable or Revenue are created.
  - **And:** The labor cost is correctly recorded in the Work-in-Progress (WIP) sub-ledger.

- **AC4: Reject Invalid (Negative) Labor Hours**
  - **Given:** A Technician is recording labor for a time-based service item.
  - **When:** The Technician attempts to submit an entry with -1.0 hours.
  - **Then:** The system rejects the submission and displays a user-facing validation error.
  - **And:** No `WorkorderLaborEntry` is created and no `LaborRecorded` event is emitted.

- **AC5: Ensure Idempotent Event Processing**
  - **Given:** A `WorkorderLaborEntry` has been successfully recorded and a `LaborRecorded` event was emitted.
  - **When:** The same `LaborRecorded` event (with the same idempotency key) is processed a second time by a downstream system.
  - **Then:** The system recognizes the event as a duplicate and does not create a duplicate labor cost entry in the WIP ledger.

- **AC6: Verify Labor Entry Auditability**
  - **Given:** A `WorkorderLaborEntry` is successfully created.
  - **When:** An auditor queries the system's audit logs.
  - **Then:** An immutable audit record exists that clearly identifies the `technicianId`, `workorderId`, `laborEntryId`, the data recorded, and the creation timestamp.

## Audit & Observability
- **Audit Log:** All CUD (Create, Update, Delete) operations on `WorkorderLaborEntry` must be logged in an immutable audit trail. The log must include the principal (Technician ID), timestamp, and a snapshot of the changed data.
- **Event Sourcing:** The `LaborRecorded` event must be published to a durable, ordered message topic (e.g., Kafka, Kinesis) for reliable consumption by other domains like Accounting.
- **Event Schema & Contract:** The event payload must be versioned and strictly conform to the `Durion_Accounting_Event_Contract_v1.pdf`.
- **Idempotency Key:** The emitted event must contain a unique and deterministic idempotency key, composed of `workorderId` + `laborEntryId` + `version`, to guarantee safe reprocessing by consumers.
- **Metrics:** The service must expose metrics for monitoring, including:
  - `labor_entries_created_total` (counter, with dimensions for `laborType`)
  - `labor_entry_errors_total` (counter, with dimensions for `errorType`)
  - `labor_entry_duration_seconds` (histogram)

## Original Story (Unmodified – For Traceability)
# Issue #159 — [BACKEND] [STORY] Execution: Record Labor Performed

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Record Labor Performed

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300002/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician

## Trigger
Technician performs labor associated with a workorder service item.

## Main Flow
1. Technician selects a labor/service line item.
2. Technician records time (start/stop or hours) or marks a flat-rate completion.
3. Technician adds notes/results (optional).
4. System validates permissions and records labor entry.
5. System updates workorder progress and completion indicators.

## Alternate / Error Flows
- Labor entry attempted without assignment → block or warn per policy.
- Negative or unrealistic hours → block and require correction.

## Business Rules
- Labor entries must be attributable to a technician and time.
- Support both flat-rate and time-based labor.
- Entries must be auditable and reversible only with permissions.

## Data Requirements
- Entities: Workorder, WorkorderItem, LaborEntry, AuditEvent
- Fields: workorderId, itemSeqId, technicianId, hours, flatRateFlag, notes, createdAt

## Acceptance Criteria
- [ ] Technicians can record labor entries on assigned workorders.
- [ ] Labor entries are auditable and tied to service items.
- [ ] Progress updates reflect labor completion.
- [ ] Labor entries emit LaborRecorded events when saved
- [ ] Labor events do not create AR or revenue
- [ ] Labor cost is available for job costing or WIP reporting
- [ ] Updates to labor entries supersede prior events
- [ ] Duplicate events do not create duplicate labor cost

## Integrations

### Accounting
- Emits Event: LaborRecorded
- Event Type: Non-posting (job cost / WIP tracking)
- Source Domain: workexec
- Source Entity: WorkorderLaborEntry
- Trigger: Labor entry recorded or completed
- Idempotency Key: workorderId + laborEntryId + version


## Notes for Agents
Even if prices are hidden, labor quantities must remain accurate for invoicing.


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
