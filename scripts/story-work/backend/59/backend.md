Title: [BACKEND] [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/59
Labels: type:story, domain:workexec, status:ready-for-dev, agent:story-authoring, agent:workexec

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:ready-for-dev

### Recommended
- agent:workexec
- agent:story-authoring

---
**Rewrite Variant:** workexec-strict
---

## Story Intent
**As a** Mechanic or Shop Manager,
**I want** the Workorder Execution (Workexec) system to display the current operational context of a work order,
**so that** I understand who is assigned, which bay is in use, and the current schedule before starting work or making changes.

## Actors & Stakeholders
- **Mechanic (Primary Actor)**: The individual who will perform the work. Needs to see assignments, bay location, and schedule constraints.
- **Shop Manager (Primary Actor)**: The individual responsible for overseeing shop operations. Needs to see operational context to make dispatch decisions, resolve conflicts, and handle exceptions.
- **Workexec System (System Actor)**: The system responsible for executing work, tracking labor time, and capturing completion data.
- **Shopmgr System (Integration / System of Record)**: The authoritative source for operational context (location, bay, dispatch schedule, assigned mechanics, resources, constraints).
- **Dispatch System (Stakeholder)**: The system that creates and updates work order assignments. Workexec needs to faithfully reflect the current dispatch state.

## Preconditions
1.  The work order exists in the system and is in a state where work can be performed (e.g., `ASSIGNED`, `IN_PROGRESS`).
2.  The user (Mechanic or Shop Manager) is authenticated and has permission to view work orders.
3.  Shopmgr is the system of record and has published or made available the latest operational context for the work order.

## Functional Behavior
1.  The user opens or refreshes a view of a work order in the Workexec system.
2.  Workexec requests the latest operational context from Shopmgr (via REST API or consumes a recently-published event).
3.  Workexec displays the following operational context elements to the user (where available):
    -   **Location**: The name/identifier of the location where the work is to be performed.
    -   **Bay Assignment**: The bay identifier where the vehicle is or should be placed.
    -   **Dispatch Schedule**: The scheduled start and end times for the work (if known).
    -   **Assigned Mechanics**: A list of mechanics assigned to the work order, including primary and supporting roles.
    -   **Assigned Resources**: Any resources (e.g., equipment, specialized tools) allocated to the work.
    -   **Constraints**: Any operational constraints (e.g., "Bay must be vacated by 3:00 PM", "Requires lift").
4.  The system presents this information in a read-only view, indicating that Shopmgr is the authoritative source.
5.  If the Mechanic or Shop Manager has the appropriate permissions, the system may provide a mechanism to request a change or override to the operational context (which would result in a request to Shopmgr or a direct privileged update).

## Alternate / Error Flows
- **Error Flow 1: Operational Context Not Available**
    -   If the operational context cannot be retrieved from Shopmgr (e.g., due to a network error or an upstream system outage), Workexec displays a message indicating the context is temporarily unavailable.
    -   The user can still view the work order details, but the operational context panel is marked as stale or unavailable.
- **Error Flow 2: Unauthorized Access**
    -   If the user attempts to view a work order they do not have permission to access, the system returns a `403 Forbidden` error.
- **Alternate Flow 1: Manager Override**
    -   If a Shop Manager needs to override the operational context (e.g., reassign a bay, adjust timing due to an emergency), they invoke a privileged "Override Operational Context" action.
    -   This action triggers a call to a special Shopmgr endpoint (or emits a domain event) requesting a change in context (see Resolved Questions RQ1).
    -   The new operational context is reflected in Workexec once Shopmgr confirms the change.

## Business Rules
- **BR1: System of Record:** Shopmgr is the authoritative system for operational context. Workexec MUST NOT store or maintain a writable, independent copy of this data. It may cache the context for display purposes, but must treat the cache as read-only and subject to invalidation.
- **BR2: Context Immutability During Execution:** Once work has started on a work order (i.e., the work order transitions to `IN_PROGRESS`), the operational context version associated with that work start event is locked for the purposes of audit and tracking. This does not prevent Shopmgr from updating the context (e.g., for a future work session), but the locked version represents the conditions under which the work was initiated.
- **BR3: Required Context Elements:** At a minimum, Workexec must display the location, assigned mechanic(s), and the current work order status. Bay assignment, dispatch schedule, and resources are highly desirable but may be optional if not yet populated by Shopmgr.

## Data Requirements
- **OperationalContext (from Shopmgr)**
    -   `workOrderId` (FK)
    -   `version` (Optimistic Lock / Version Number)
    -   `locationId`
    -   `locationName`
    -   `bayId` (Nullable)
    -   `bayName` (Nullable)
    -   `scheduledStartAt` (Timestamp, Nullable)
    -   `scheduledEndAt` (Timestamp, Nullable)
    -   `assignedMechanics` (List of Mechanic assignments with role)
    -   `assignedResources` (List of Resource assignments)
    -   `constraints` (List of Operational Constraint objects)
    -   `lastUpdatedAt` (Timestamp)
    -   `lockedAtWorkStart` (Boolean; true if this version was in effect when work started)
- **WorkorderExecution (in Workexec)**
    -   `workOrderId` (PK/FK)
    -   `status` (Enum: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, etc.)
    -   `workStartedAt` (Timestamp)
    -   `operationalContextVersion` (Integer; the version of the context in effect when work started)

## Acceptance Criteria
- **AC1: Display Complete Operational Context**
    -   **Given** a work order with a fully populated operational context in Shopmgr,
    -   **When** a Mechanic views the work order in Workexec,
    -   **Then** Workexec must display the location name, bay assignment, scheduled start/end times, all assigned mechanics, assigned resources, and any operational constraints,
    -   **And** the display must indicate the source of this data is Shopmgr (e.g., "Operational context provided by Shop Management System").
- **AC2: Display Partial Context (Graceful Degradation)**
    -   **Given** a work order with only a location and one assigned mechanic defined (no bay or schedule yet),
    -   **When** a Mechanic views the work order in Workexec,
    -   **Then** Workexec must display the available location and mechanic information,
    -   **And** indicate which fields (bay, schedule) are not yet available,
    -   **And** the work order must still be actionable (e.g., mechanic can start work if permissions allow).
- **AC3: Lock Context Version on Work Start**
    -   **Given** a work order with operational context version 5,
    -   **When** a Mechanic starts work on that work order (transitions to `IN_PROGRESS`),
    -   **Then** the Workexec system must record `operationalContextVersion=5` and `workStartedAt` timestamp,
    -   **And** this locked version must be immutable for the duration of that work session,
    -   **And** any subsequent updates to the operational context in Shopmgr must not retroactively change the recorded context for this work session.
- **AC4: Context Unavailable Handling**
    -   **Given** Workexec is unable to retrieve operational context from Shopmgr due to a network error,
    -   **When** a Mechanic views the work order,
    -   **Then** Workexec must display a clear message that the operational context is currently unavailable,
    -   **And** the user must still be able to view other work order details (e.g., work order ID, vehicle information, requested services).
- **AC5: Manager Override Triggers Context Update**
    -   **Given** a Shop Manager has permission to override operational context,
    -   **When** they submit a request to change the bay assignment for a work order,
    -   **Then** Workexec must invoke Shopmgr's override endpoint with the proposed change,
    -   **And** if the override is accepted, the new operational context must be reflected in Workexec within a reasonable timeframe (e.g., <2 seconds),
    -   **And** the system must emit an audit event capturing the before/after context and the reason for the override.

## Audit & Observability
- **Audit Trail:** Any display of operational context must be logged (at DEBUG level or higher) to facilitate troubleshooting. This includes the work order ID, the version of the context retrieved, and the timestamp.
- **Events:** Workexec should log or emit events when:
    -   Operational context is successfully retrieved from Shopmgr.
    -   Operational context retrieval fails (for alerting and diagnostics).
    -   A Manager override request is submitted to Shopmgr.
- **Metrics:**
    -   Latency of operational context retrieval calls to Shopmgr.
    -   Frequency of context retrieval failures.
    -   Frequency of Manager override requests.

## Resolved Questions

From **Clarification Issue #256**, the following answers were incorporated:

### RQ1: Manager Override Process

**Decision**: Manager override is a **privileged, explicit action** requiring a separate endpoint and permissions.

**Not**: In-line field edits that would overwrite Shopmgr's current view.

**Mechanism**:
- Separate privileged endpoint: `POST /workexec/v1/workorders/{workOrderId}/operational-context:override`
- Permission: `workexec:operational_context:override`
- Request body:
  ```json
  {
    "overrides": {
      "bayId": "uuid",
      "scheduledStartAt": "2025-01-24T10:00:00Z"
    },
    "reason": "Emergency vehicle arrival; bay 3 required",
    "actorId": "uuid"
  }
  ```

**System behavior**:
1. Workexec validates permission and business rules
2. Creates new operational context version with updates
3. Uses optimistic concurrency control (`version` field)
4. Emits `OperationalContextOverridden` event with before/after and reason
5. Updates display in Workexec with new context

**Audit**: All overrides captured in immutable audit log.

### RQ2: Integration Contract

**Decision**: **Shopmgr is system of record**; Workexec consumes via REST API with optional event push for cache invalidation.

**REST endpoint (definitive)**:
```
GET /shopmgr/v1/workorders/{workOrderId}/operational-context
```

**Response schema**:
```json
{
  "workOrderId": "uuid",
  "version": 7,
  "location": {
    "locationId": "uuid",
    "locationName": "Main Shop - Bay Area 1"
  },
  "bay": {
    "bayId": "uuid",
    "bayName": "Bay 3",
    "bayType": "LIFT"
  },
  "dispatchSchedule": {
    "scheduledStartAt": "2025-01-24T10:00:00Z",
    "scheduledEndAt": "2025-01-24T12:00:00Z"
  },
  "assignedMechanics": [
    {
      "mechanicId": "uuid",
      "name": "John Doe",
      "role": "LEAD",
      "assignedAt": "2025-01-24T09:00:00Z"
    }
  ],
  "assignedResources": [
    {
      "resourceId": "uuid",
      "resourceType": "TIRE_MACHINE",
      "resourceName": "TireMaster Pro"
    }
  ],
  "constraints": [
    {
      "constraintType": "BAY_VACATE_BY",
      "value": "15:00:00"
    }
  ],
  "metadata": {
    "lastUpdatedAt": "2025-01-24T09:30:00Z",
    "lastUpdatedBy": "dispatch-service"
  }
}
```

**Event push (optional)**:
- Shopmgr publishes `OperationalContextChanged` event when context updates
- Workexec subscribes to invalidate local cache
- Workexec re-fetches via REST on next view

**Contract stability**: Schema versioned; backward-compatible changes preferred.

### RQ3: Work Start Event

**Decision**: **Explicit work start command** triggers context lock.

**Mechanism**:
- Endpoint: `POST /workexec/v1/workorders/{workOrderId}:start`
- Transitions work order to `IN_PROGRESS`
- Records `workStartedAt` timestamp
- Locks `operationalContextVersion` (current version at time of start)

**Lifecycle**:
- `NOT_STARTED` → `IN_PROGRESS` (first start)
- `PAUSED` → `IN_PROGRESS` (resume)

**Event emitted**: `WorkorderStatusChanged` with `lockedContextVersion`

**Business rule**: Locked version represents conditions under which work began; immutable for that session (future context updates do not retroactively alter locked version).

### RQ4: Status Egress

**Decision**: **Event stream** is the primary egress mechanism for work order status changes.

**Event topic**: `workexec.workorder.status.changed.v1`

**Payload schema**:
```json
{
  "eventId": "uuid",
  "eventType": "WorkorderStatusChanged",
  "occurredAt": "2025-01-24T10:00:00Z",
  "workOrderId": "uuid",
  "previousStatus": "NOT_STARTED",
  "newStatus": "IN_PROGRESS",
  "lockedOperationalContextVersion": 7,
  "locationId": "uuid",
  "actor": {
    "actorId": "uuid",
    "actorType": "MECHANIC"
  },
  "reason": "Work started by mechanic",
  "metadata": {
    "source": "workexec-service",
    "version": "1.2.3"
  }
}
```

**Consumers**:
- Shopmgr (for real-time shop floor visibility)
- Reporting (for labor utilization, cycle time tracking)
- Dispatch (for constraint enforcement and replanning)

**Delivery guarantees**:
- At-least-once delivery
- Idempotent consumption required (use `eventId` as deduplication key)
- Non-blocking (does not halt work execution)

**Fallback**: REST polling endpoint available but not recommended for real-time use.

## Original Story (Unmodified – For Traceability)
# Issue #59 — [BACKEND] [STORY] CrossDomain: Workexec Displays Operational Context

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] CrossDomain: Workexec Displays Operational Context

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want to see the current operational context (who assigned, which bay, etc.) in Workexec, so I know what's in effect before I start work.

## Details
- Fetch location, bay, dispatch schedule, assigned resources from Shopmgr.
- Shopmgr is system-of-record for operational context; Workexec displays read-only view.

## Acceptance Criteria
- Operational context displayed in Workexec UI.
- Data sourced from Shopmgr REST or events.

## Integrations
- Shopmgr provides context; Workexec consumes and displays it; any override triggers update back to Shopmgr.

## Data / Entities
- OperationalContext (Shopmgr), WorkorderExecution (Workexec)

## Classification (confirm labels)
- Type: Story
- Layer: Integration
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