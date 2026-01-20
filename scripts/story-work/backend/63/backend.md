Title: [BACKEND] [STORY] Workexec: Update Appointment Status from Workexec Events
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/63
Labels: type:story, domain:workexec, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:story-authoring
- agent:workexec

---
**Rewrite Variant:** workexec-structured
---

## Story Intent

As the Work Execution System, I want to publish work order status changes via events, so that the Shop Management System can consume these events to update the corresponding Appointment status in real-time, providing accurate and timely visibility to Dispatchers and Service Advisors.

## Actors & Stakeholders

- **System Actors**:
    - `Work Execution System`: The source system of record for work order status; publishes events.
    - `Shop Management System`: The consuming system that owns the Appointment entity; subscribes to and processes events.
    - `Message Broker / Event Bus`: The infrastructure responsible for event transport.
- **Human Actors**:
    - `Dispatcher`: Primary beneficiary who needs real-time visibility into work progress to manage shop capacity and scheduling.
    - `Service Advisor`: Secondary beneficiary who communicates appointment status to customers.

## Preconditions

- An `Appointment` entity exists in the Shop Management System.
- A corresponding `Work Order` exists in the Work Execution System, which is logically linked to the `Appointment`.
- The Shop Management System has an active and correctly configured event subscription to the `WorkorderStatusChanged` and `InvoiceIssued` topics/queues from the Work Execution System.

## Functional Behavior

1.  The `Work Execution System` performs an action that changes the state of a work order (e.g., work started, work completed, invoice issued).
2.  It publishes a corresponding event (`WorkorderStatusChanged` or `InvoiceIssued`) to the message broker. The event payload contains, at a minimum, the `workOrderId`, the `newStatus`, and a `timestamp`.
3.  The `Shop Management System`'s event consumer receives the event.
4.  The system identifies the target `Appointment` associated with the `workOrderId` from the event payload.
5.  The system translates the incoming `Workexec` status to the corresponding `Appointment` status using a predefined mapping table.
6.  The system updates the `Appointment.status` field with the newly translated status.
7.  A new entry containing the new status, a timestamp, and the source event identifier is appended to the `Appointment.statusTimeline` collection.
8.  The event processing must be idempotent. If the same event is processed more than once, the `Appointment`'s state must be identical to its state after the first successful processing, with no duplicate timeline entries.

## Alternate / Error Flows

- **Reopened Work Order**: If an event is received indicating the work order has been "reopened," the system must update the `Appointment.status` and set the `Appointment.reopenFlag` to `true`.
- **Appointment Not Found**: If an event is received for a `workOrderId` that does not correspond to an existing `Appointment`, the event is acknowledged, an error is logged to a dead-letter queue (DLQ) or equivalent, and a high-priority alert is generated for operational investigation.
- **Invalid Status Mapping**: If the status from the `Workexec` event does not have a defined mapping to an `Appointment` status, the event processing fails. The event is moved to a DLQ and a high-priority alert is generated.

## Business Rules

- **BR1: Status Mapping Authority**: A definitive, non-ambiguous mapping between all possible `Workexec` statuses and `Appointment` statuses must exist and be maintained as a configurable business rule. (See Resolved Questions)
- **BR2: Idempotency Required**: All event handlers for status updates must be idempotent. The system should gracefully handle duplicate event delivery without causing data corruption or invalid state transitions.
- **BR3: Reopen Flag Logic**: The `reopenFlag` on an `Appointment` indicates that its associated work order was reopened at least once. The specific logic for when this flag can be cleared (if ever) must be defined. (See Resolved Questions)
- **BR4: Immutable Timeline**: The `statusTimeline` is an append-only log of status changes for an `Appointment`. Entries must not be modified or deleted after being recorded.

## Data Requirements

- **Event Payload Contract (`WorkorderStatusChanged`, `InvoiceIssued`)**:
    - `eventId`: `UUID` (Unique identifier for the event instance)
    - `workOrderId`: `String | UUID` (Identifier for the work order)
    - `newStatus`: `String | Enum` (The new status from the Work Execution System)
    - `eventTimestamp`: `ISO 8601 UTC` (Timestamp of when the event occurred)
    - `correlationId`: `UUID` (For tracing across systems)
- **Target Entity (`Appointment`) Fields to be Modified**:
    - `status`: `String | Enum` (The current status of the appointment)
    - `reopenFlag`: `Boolean` (Defaults to `false`)
    - `statusTimeline`: `List` (A collection of status change records)
- **Data Structure (`StatusTimelineEntry`)**:
    - `status`: `String | Enum` (The status that was set)
    - `changeTimestamp`: `ISO 8601 UTC` (Timestamp of when the status was changed in this system)
    - `sourceEventId`: `UUID` (The ID of the event that triggered this change)

## Acceptance Criteria

**AC1: Successful Status Update**
- **Given** an Appointment exists with a `workOrderId` of "WO-123" and its status is "Scheduled".
- **When** a `WorkorderStatusChanged` event is received for "WO-123" with a `newStatus` of "InProgress".
- **Then** the `Appointment`'s status is updated to the corresponding "Work In Progress" status.
- **And** a new entry is added to the `statusTimeline` for the "Work In Progress" status, including a timestamp and the event ID.

**AC2: Idempotent Event Processing**
- **Given** the Appointment for "WO-123" has its status set to "Work In Progress" from an event with ID "event-abc".
- **When** the system re-processes the exact same `WorkorderStatusChanged` event with ID "event-abc".
- **Then** the `Appointment`'s status remains "Work In Progress".
- **And** no duplicate entry is added to the `statusTimeline`.

**AC3: Reopen Work Order**
- **Given** an Appointment for "WO-456" has a status of "Work Complete".
- **When** a `WorkorderStatusChanged` event is received for "WO-456" with a `newStatus` indicating it has been "Reopened".
- **Then** the `Appointment`'s status is updated to the corresponding "Work In Progress" status.
- **And** the `reopenFlag` on the `Appointment` is set to `true`.
- **And** a new "Work In Progress" entry is added to the `statusTimeline`.

**AC4: Event for Non-Existent Appointment**
- **Given** the system is subscribed to work order events.
- **When** a `WorkorderStatusChanged` event is received for a `workOrderId` of "WO-999" which has no corresponding `Appointment`.
- **Then** an error is logged.
- **And** an alert is triggered for "Orphaned Work Order Event".
- **And** the event is moved to a dead-letter queue.

## Audit & Observability

- **Audit Trail**: The `Appointment.statusTimeline` field, with its `sourceEventId`, serves as a complete audit trail for all status changes triggered by this integration.
- **Logging**:
    - `INFO`: Successful consumption and processing of each event, including `workOrderId` and status change.
    - `ERROR`: Failures in processing, such as "Appointment Not Found" or "Invalid Status Mapping," including the full event payload for debugging.
- **Metrics**:
    - `events.processed.count`: Counter for successfully processed events, tagged by event type.
    - `events.processing.latency`: Histogram measuring the time from event receipt to completion of processing.
    - `events.failed.count`: Counter for failed events, tagged by failure reason (e.g., `appointment_not_found`, `invalid_status`).
- **Alerting**:
    - `CRITICAL`: Alert when the rate of failed events exceeds a defined threshold.
    - `CRITICAL`: Alert for any "Invalid Status Mapping" error, as it indicates a contract mismatch between systems.

## Resolved Questions

### RQ1 (Status Mapping Definition)
**Question:** What is the definitive, exhaustive mapping of all possible `Workexec` statuses to their corresponding `Appointment` statuses?

**Resolution:** The comprehensive status mapping table is as follows:

#### Appointment Status Enum (10 statuses):
1. `SCHEDULED` - Appointment booked but work not started
2. `CHECKED_IN` - Customer arrived, checked in at service desk
3. `WORK_IN_PROGRESS` - Active work being performed
4. `WAITING_FOR_PARTS` - Work paused pending parts arrival
5. `QUALITY_CHECK` - Work complete, undergoing inspection
6. `READY_FOR_PICKUP` - All work complete, awaiting customer pickup
7. `COMPLETED` - Customer picked up vehicle, appointment closed
8. `CANCELLED` - Appointment cancelled before or during work
9. `INVOICED` - Invoice generated and sent to customer
10. `REOPENED` - Previously completed work reopened for additional service

#### Workexec → Appointment Status Mapping:

| Workexec Status | Appointment Status | Notes |
|----------------|-------------------|-------|
| `CREATED` | `SCHEDULED` | Initial work order creation |
| `PENDING` | `SCHEDULED` | Awaiting resource allocation |
| `ASSIGNED` | `SCHEDULED` | Mechanic assigned but not started |
| `CUSTOMER_ARRIVED` | `CHECKED_IN` | Customer check-in event |
| `CHECKED_IN` | `CHECKED_IN` | Explicit check-in status |
| `IN_PROGRESS` | `WORK_IN_PROGRESS` | Work actively being performed |
| `STARTED` | `WORK_IN_PROGRESS` | Work begun |
| `PARTS_PENDING` | `WAITING_FOR_PARTS` | Waiting for parts arrival |
| `ON_HOLD` | `WAITING_FOR_PARTS` | Default hold reason |
| `PARTS_ORDERED` | `WAITING_FOR_PARTS` | Parts ordered from supplier |
| `INSPECTING` | `QUALITY_CHECK` | Quality inspection in progress |
| `QC_IN_PROGRESS` | `QUALITY_CHECK` | Formal QC process |
| `WORK_COMPLETE` | `READY_FOR_PICKUP` | All work done, awaiting pickup |
| `AWAITING_CUSTOMER` | `READY_FOR_PICKUP` | Ready for customer |
| `CLOSED` | `COMPLETED` | Fully closed and customer departed |
| `DELIVERED` | `COMPLETED` | Vehicle delivered to customer |
| `CUSTOMER_PICKUP` | `COMPLETED` | Customer picked up vehicle |
| `CANCELLED` | `CANCELLED` | Cancellation at any stage |
| `ABANDONED` | `CANCELLED` | Customer did not return |
| `INVOICE_GENERATED` | `INVOICED` | Invoice created |
| `INVOICED` | `INVOICED` | Invoice sent to customer |
| `REOPENED` | `REOPENED` | Previously closed work reopened |
| `REWORK_REQUIRED` | `REOPENED` | Quality issue requires rework |

#### Precedence Rules:
1. `CANCELLED` is terminal - once cancelled, no further status updates except explicit reopen
2. `INVOICED` supersedes `COMPLETED` - if both events received, `INVOICED` wins
3. `REOPENED` supersedes any terminal status (`COMPLETED`, `CANCELLED`) until resolved
4. Multiple events with same Appointment status = idempotent (no duplicate timeline entries)

**Rationale:** This mapping covers the full lifecycle of shop operations including edge cases (holds, cancellations, reopens). Precedence rules ensure consistent state even with out-of-order event delivery.

---

### RQ2 (Reopen Flag Permanence)
**Question:** Once the `reopenFlag` is set to `true`, can it ever be reset to `false`?

**Resolution:** The `reopenFlag` is **permanent once set to `true`**. It must never be reset to `false`.

**Semantics:**
- `reopenFlag = true` means: "This appointment was reopened at least once during its lifecycle"
- This is a permanent indicator of appointment history, not current state
- The flag answers the question: "Was this appointment ever reopened?" (not "Is it currently reopened?")

**Alternative Field (if needed):**
For tracking whether an appointment is **currently** in a reopened state:
- Add separate field: `isCurrentlyReopened` (boolean)
- OR track `reopenCount` (integer) 
- OR rely solely on `status == REOPENED` for current state

**Rationale:** Permanent flags provide valuable historical insight for:
- Quality analysis (which appointments required rework?)
- Customer satisfaction tracking
- Process improvement (why are appointments being reopened?)
- Billing disputes (did we charge for rework?)

This follows audit log principles where historical facts are immutable.

---

### RQ3 (Appointment Identification)
**Question:** How is the `Appointment` identified from the incoming event? Is there a direct `appointmentId` in the event payload, or must the system perform a lookup?

**Resolution:** **Primary linkage uses `workOrderId` lookup**. Events must include `workOrderId`, and the Appointment service maintains a mapping table.

**Required Architecture:**
1. **Workexec Events** must always include `workOrderId` in payload
2. **Appointment Service** maintains mapping table:
   - Table: `WorkOrderAppointmentMapping`
   - Columns: `workOrderId (PK/FK)`, `appointmentId (FK)`, `createdAt`, `status`
3. **Event Handler** resolves `appointmentId` by lookup:
   ```sql
   SELECT appointmentId 
   FROM WorkOrderAppointmentMapping 
   WHERE workOrderId = :eventWorkOrderId
   ```
4. **Mapping Lifecycle:**
   - Created when Appointment creates/links to Work Order
   - Never deleted (for audit trail)
   - Indexed on `workOrderId` for fast lookup

**Optional Optimization (future):**
- If Workexec events start including `appointmentId` directly in payload:
  - Use `appointmentId` if present (skip lookup)
  - Fall back to `workOrderId` lookup if `appointmentId` absent
  - This supports gradual migration and backward compatibility

**Error Handling:**
- If `workOrderId` lookup fails → move event to DLQ, alert "Orphaned Work Order"
- If multiple appointments map to same `workOrderId` → log error, use most recent

**Rationale:** Using `workOrderId` as the integration key respects domain boundaries:
- Workexec owns Work Order concept
- Appointment owns Appointment concept  
- Mapping table maintains the relationship
This prevents tight coupling and allows Appointment to exist independently of Workexec's internal IDs.

## Original Story (Unmodified – For Traceability)
# Issue #63 — [BACKEND] [STORY] Workexec: Update Appointment Status from Workexec Events

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Workexec: Update Appointment Status from Workexec Events

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want appointments to reflect workexec status so that dispatch has real-time visibility.

## Details
- Map workexec states to appointment states.
- Handle reopen as exception.

## Acceptance Criteria
- Status updates idempotent.
- Reopen flagged.
- Timeline stored.

## Integrations
- Workexec→Shopmgr WorkorderStatusChanged/InvoiceIssued events.

## Data / Entities
- AppointmentStatus, StatusTimeline, ExceptionFlag

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