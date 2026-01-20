Title: [BACKEND] [STORY] Security: Audit Trail for Schedule and Assignment Changes
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/61
Labels: type:story, domain:audit, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:audit
- status:ready-for-dev

### Recommended
- agent:story-authoring
- agent:workexec
- agent:audit

---
**Rewrite Variant:** integration-conservative
---

## Story Intent
As a **Shop Manager**, I want a persistent and searchable history of all changes made to work schedules and mechanic assignments, so that I can investigate discrepancies, resolve disputes, and ensure operational accountability.

## Actors & Stakeholders
- **Shop Manager (Primary Actor):** Views the audit trail to understand the history of changes and resolve operational questions.
- **Service Advisor / Dispatcher (Implicit Actor):** Performs the schedule and assignment changes that are subject to audit.
- **System (System Actor):** Atomically and immutably records the audit entries whenever a relevant change occurs.
- **Mechanic (Stakeholder):** The subject of assignment changes; benefits from a clear record of when and why their schedule was altered.

## Preconditions
- A work execution system exists for managing appointments, work orders, and mechanic assignments.
- The system security context reliably identifies the user (the actor) performing any state-changing operation.
- Work Orders, Appointments, and Mechanics have unique, stable identifiers.

## Functional Behavior
1.  **Trigger:** Any create, update, or delete operation is committed for a work schedule entry or a mechanic-to-work-order assignment.
2.  **System Action:** Upon a successful commit of a change, the system captures the following information:
    - The identity of the actor who initiated the change.
    - A precise, transactional timestamp (UTC).
    - The entity type being changed (e.g., 'WorkOrderAssignment', 'AppointmentSchedule').
    - The unique identifier of the entity being changed.
    - A summary of the change, ideally capturing the state before and after the modification (a "diff").
    - Any "reason code" or comment provided by the actor for the change.
3.  **Outcome:** The captured information is durably persisted as an immutable audit log entry.
4.  **Retrieval:** The system provides an interface for authorized users (e.g., Shop Manager) to query and view the audit log, filterable by entity (Work Order, Appointment) or stakeholder (Mechanic).

## Alternate / Error Flows
- **Error - Audit Service Unavailability:** If the service responsible for persisting the audit log is unavailable, the system's behavior must be defined. (See Resolved Questions). The originating change might be rolled back, or the audit event could be queued for later processing.
- **Error - Unauthorized Access:** If a user without the required permissions attempts to view the audit history, the system must deny access with a clear "permission denied" error.
- **Error - Attempted Modification of Audit Log:** Any direct attempt to modify or delete an existing audit log entry through any interface MUST fail and be rejected. This may trigger a security alert.

## Business Rules
- All creations, updates, and deletions of schedule and assignment records MUST be audited. There are no exceptions.
- Audit log entries are immutable. Once written, they cannot be altered or deleted through normal application functions.
- The change history must be directly associated with the specific entity (e.g., Work Order `WO-123`) and the actor who performed the change.

## Data Requirements
An `AuditLog` entity must be defined, containing at a minimum:
- `AuditLogID` (string, UUID): Unique identifier for the audit entry.
- `Timestamp` (datetime, UTC): The precise time the change was committed.
- `ActorID` (string): The unique identifier of the user or system process that made the change.
- `EntityType` (string, enum): The type of entity that was changed (e.g., `WORK_ORDER_ASSIGNMENT`).
- `EntityID` (string): The unique identifier of the entity instance that was changed.
- `ChangeSummary` (JSON, text): A structured (e.g., JSON diff) or unstructured summary of the changes made.
- `ReasonCode` (string, optional): A code or free-text reason for the change, if provided.

## Acceptance Criteria
- **Given** a mechanic is not assigned to Work Order 'WO-123'
  **When** a Service Advisor assigns Mechanic 'M-456' to 'WO-123'
  **Then** a new audit log entry is created that records the actor (Service Advisor), the timestamp, the entity ('WO-123'), and a summary indicating the new assignment of 'M-456'.
- **Given** an appointment 'AP-789' is scheduled for 2:00 PM
  **When** a Dispatcher reschedules it to 3:00 PM and provides a reason 'Customer Request'
  **Then** a new audit log entry is created that records the actor (Dispatcher), timestamp, entity ('AP-789'), a summary showing the time changed from '2:00 PM' to '3:00 PM', and the reason 'Customer Request'.
- **Given** several changes have been audited for Work Order 'WO-123'
  **When** a Shop Manager searches the audit trail by the ID 'WO-123'
  **Then** all audit entries related to 'WO-123' are returned in reverse chronological order.
- **Given** a mechanic 'M-456' has been assigned to and unassigned from several work orders
  **When** a Shop Manager searches the audit trail by the mechanic ID 'M-456'
  **Then** all audit entries related to 'M-456' assignments are returned.
- **Given** an audit log entry exists
  **When** any user or system process attempts to modify or delete it via an API
  **Then** the operation must be rejected with an appropriate error status, and the entry remains unchanged.

## Audit & Observability
- Failures to write to the audit log MUST generate a high-priority system alert and be logged as an ERROR.
- Metrics should be tracked for the volume of audit entries being created and the latency of the write operations.
- Successful creation of an audit entry should be logged at an INFO or DEBUG level for traceability.

## Resolved Questions

### RQ1 (Domain Ownership)
**Question:** Which domain is the primary owner of this story? Is it `domain:workexec` (extending its model to include history) or `domain:audit` (a generic service that `workexec` calls)?

**Resolution:** **`domain:audit` is the primary owner** of this story.

**Architecture:**
- **Workexec** remains the system of record for operational state (schedules, assignments)
- **Audit** is a cross-cutting capability that Workexec integrates with
- This story delivers:
  1. **Audit Service:** Core audit API, storage, and query/search endpoints
  2. **Common Event Schema:** Standardized audit event envelope and payload structure
  3. **Integration Contract:** How Workexec emits audit events or calls audit API

**Implementation:**
- Workexec domain emits audit events/calls audit API when state changes occur
- Audit domain provides service endpoints for:
  - Recording audit entries
  - Querying audit history by entity, actor, date range
  - Enforcing immutability and retention policies

**Rationale:** Treating audit as a cross-cutting concern allows it to be reused across multiple domains (not just Workexec) and maintains proper separation of concerns. Workexec focuses on operational logic; Audit focuses on immutable historical record-keeping.

---

### RQ2 (Architectural Coupling)
**Question:** If the Audit service fails to record a change, should the originating `workexec` transaction be rolled back? Is auditing synchronous or asynchronous?

**Resolution:** **Asynchronous / Eventual Consistency** is the required architecture.

**Requirements:**
1. Workexec transactions **must NOT be rolled back** if Audit is unavailable
2. Use **outbox pattern** for reliability:
   - Workexec writes the operational change to its own database
   - Workexec writes an `AuditRecordRequested` event to a durable outbox table (same transaction)
   - Background worker polls outbox and publishes to event bus
   - Audit service consumes events and persists records
3. **Retry with backoff** until successful delivery
4. **Alert** if audit event backlog exceeds defined thresholds (e.g., > 1000 pending events or > 1 hour latency)

**Non-Requirements:**
- No synchronous audit API calls from Workexec in critical path
- No blocking on audit service availability

**Rationale:** Operational workflows (scheduling, assignments) must remain available even when audit is degraded. Asynchronous integration via outbox pattern ensures both reliability (no lost audit records) and availability (no operational downtime). This is a standard enterprise integration pattern for cross-domain communication.

---

### RQ3 (Data Retention Policy)
**Question:** How long must audit records be stored? Are there legal or regulatory requirements?

**Resolution:** **7 years minimum retention** is the baseline policy, with support for longer retention as needed.

**Policy Details:**
- **Default Retention:** 7 years for all audit records (aligns with common financial and labor regulations)
- **Tenant-Specific Configuration:** Support configurable retention periods by tenant/location
- **Break-Glass Actions:** For high-impact actions (e.g., data deletion, financial impact, security violations), allow longer retention or indefinite archival
- **Compliance:** Retention policy must be auditable itself (who set it, when, why)

**Implementation:**
- Store retention policy metadata with each audit record
- Support archive/purge workflows for records beyond retention period
- Prevent deletion of records within retention window (immutability)

**Rationale:** 7 years is a common legal requirement for employment and financial records in many jurisdictions (e.g., FLSA, IRS). Configurable retention allows for stricter requirements in specific locations or for specific action types while maintaining reasonable storage costs.

---

### RQ4 (Reason Codes)
**Question:** Is the "reason code" a free-text field, or should it be a selection from a predefined, managed list?

**Resolution:** Reason codes must be **enum/managed list**, NOT free-text.

**Required Structure:**
- `reasonCode`: Enum value from managed registry (required for structured queries)
- `reasonNotes`: Free-text field (optional, for additional context)

**Reason Code Management:**
- **`domain:audit`** owns the global reason code registry
- Use domain-specific namespaces for organization:
  - `workexec:SCHEDULE_CONFLICT_RESOLVED`
  - `workexec:MECHANIC_UNAVAILABLE`
  - `workexec:CUSTOMER_REQUEST`
  - `workexec:EMERGENCY_REASSIGNMENT`
- New reason codes added via configuration (not code changes)
- Each code includes: `code`, `displayName`, `description`, `domain`, `isActive`

**Rationale:** Structured reason codes enable:
- Consistent reporting and analytics
- Reliable filtering and search
- Localization of reason descriptions
- Prevention of typos and inconsistent terminology

Free-text notes supplement structured codes when additional context is needed.

---

### RQ5 (Search Granularity)
**Question:** Beyond appointment, work order, and mechanic, are there other required search filters?

**Resolution:** Yes. The following filters must be supported:

**Required Filters:**
- `workOrderId` (exact match)
- `appointmentId` (exact match)
- `mechanicId` (exact match)
- **`dateTimeRange` (MANDATORY for performance)** - Start and end timestamps
- `actorUserId` (who made the change)
- `eventType` (e.g., ASSIGNMENT_CREATED, SCHEDULE_MODIFIED)
- `locationId` (shop/location filter)

**Filter Combination:**
- All filters are optional **except dateTimeRange is recommended as mandatory** to prevent unbounded queries
- Filters use AND logic when combined
- Support pagination for large result sets

**Performance Considerations:**
- Index audit table by: `entityId`, `entityType`, `timestamp`, `actorUserId`, `locationId`
- Require at least one indexed filter in every query
- Default date range: last 90 days if not specified

**Rationale:** Rich filtering enables managers to answer specific operational questions: "Who changed mechanic assignments on Nov 15?" or "What changes did dispatcher Jane make this week?" Mandatory date range prevents expensive full table scans.

---

### RQ6 (Diff Summary Format)
**Question:** What is the required structure for the `ChangeSummary` field?

**Resolution:** Store **both structured diffs and optional human-readable summary**.

**Required Fields:**
- `changeSummaryText` (string): Short human-readable description for UI display
  - Example: "Reassigned mechanic from Mike (M-123) to Sarah (M-456)"
- `changePatch` (JSON): Structured diff for programmatic use
  - Use **JSON Patch (RFC 6902)** format or structured `{field, oldValue, newValue}` list
  - Example:
    ```json
    [
      {"op": "replace", "path": "/assignedMechanicId", "value": "M-456", "oldValue": "M-123"},
      {"op": "replace", "path": "/scheduleTime", "value": "2025-01-10T15:00:00Z", "oldValue": "2025-01-10T14:00:00Z"}
    ]
    ```

**Usage:**
- `changeSummaryText`: Display in UI audit trails for quick human review
- `changePatch`: Use for detailed inspection, replay, or automated compliance checks

**Recommended:**
- Generate both formats automatically when capturing audit events
- For new entity creation: include full entity snapshot
- For deletions: include final entity snapshot before deletion

**Rationale:** Structured diffs (JSON Patch) enable powerful programmatic analysis: detecting patterns, replaying changes, automated compliance validation. Human-readable summaries provide quick UX for managers reviewing audit logs. Providing both serves different stakeholders without requiring transformations at query time.

## Original Story (Unmodified – For Traceability)
# Issue #61 — [BACKEND] [STORY] Security: Audit Trail for Schedule and Assignment Changes

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Security: Audit Trail for Schedule and Assignment Changes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want an audit history of schedule and assignment changes so that we can explain conflicts and resolve disputes.

## Details
- Record diff summary, actor, timestamp, reason codes.
- Immutable audit log.

## Acceptance Criteria
- Every change audited.
- Search by appointment/workorder/mechanic.

## Integrations
- Include workexec refs for cross-domain traceability.

## Data / Entities
- AuditLog, AuditDiffSummary

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