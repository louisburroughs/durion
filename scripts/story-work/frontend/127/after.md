## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Workexec: Consume Workexec Events to Update Appointment Status + Timeline (Moqui)

### Primary Persona
System (Shop Management / POS frontend runtime acting as event consumer) benefiting Dispatchers and Service Advisors.

### Business Value
Dispatch and advisors see near-real-time appointment status aligned with work execution progress, improving scheduling accuracy, customer communication, and operational visibility, with an auditable timeline and reopen signaling.

---

## 2. Story Intent

### As a / I want / So that
- **As a** system integrated with Workexec,
- **I want** to process Workexec work order events and reflect them onto the linked Appointment’s status, timeline, and reopen indicator,
- **So that** the appointment board reflects execution reality, even with duplicate or out-of-order event delivery.

### In-scope
- Moqui UI/admin screen(s) to view appointment status, reopen flag, and status timeline entries (read-only).
- Moqui service(s) to process incoming Workexec events (`WorkorderStatusChanged`, `InvoiceIssued`) with:
  - mapping Workexec status → Appointment status
  - precedence rules
  - idempotency (no duplicate timeline entries)
  - orphan handling (no appointment mapping)
  - invalid mapping handling
- Storage of status timeline entries with source event identifier.

### Out-of-scope
- Defining/maintaining location configuration, work order lifecycle, or emitting Workexec events (owned by backend/workexec).
- Delivering notifications/alerts to external systems (only record/log for ops visibility).
- Creating the WorkOrder ↔ Appointment mapping (assumed pre-existing; this story consumes it).

---

## 3. Actors & Stakeholders
- **System Actors**
  - Workexec event producer (external to this frontend repo)
  - Moqui runtime (event ingestion endpoint/job) acting as consumer
- **Human Stakeholders**
  - Dispatcher (needs real-time status for scheduling/dispatch board)
  - Service Advisor (needs accurate customer-facing updates)
  - Ops/Support (needs orphan/invalid mapping visibility)

---

## 4. Preconditions & Dependencies
- Appointment exists in Shop Management/POS domain (entity owned outside workexec, but updated here).
- A persistent mapping exists from `workOrderId` → `appointmentId` (see backend reference “WorkOrderAppointmentMapping”).
- Workexec events delivered to Moqui via **one** of:
  - HTTP webhook endpoint in Moqui, or
  - message broker consumer integrated into Moqui, or
  - polling job reading an inbox table.
- Backend event payload includes (minimum): `eventId`, `workOrderId`, `newStatus`, `eventTimestamp`, `correlationId`.
- Appointment statuses include the 10-status enum listed in backend reference (SCHEDULED, CHECKED_IN, WORK_IN_PROGRESS, WAITING_FOR_PARTS, QUALITY_CHECK, READY_FOR_PICKUP, COMPLETED, CANCELLED, INVOICED, REOPENED).

**Dependency/clarification required:** exact event ingestion mechanism and existing entity names/fields in this Moqui frontend project (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Workexec → Appointment Status Timeline** (or **Shop Management → Appointments → <Appointment> → Status**), depending on existing menu taxonomy.
- Direct route: `/workexec/appointments/{appointmentId}/status` (proposed).

### Screens to create/modify
1. **Screen: Appointment Detail (extend)**
   - Add read-only fields: `status`, `reopenFlag`
   - Add a timeline list/table of status changes (append-only)
2. **Screen: Orphaned Events / Processing Errors (optional but recommended)**
   - List recent failed event ingestions (appointment not found, invalid status mapping)
   - Filters by date, workOrderId, eventId, failure reason

### Navigation context
- From appointment list/dispatch board → appointment detail → status timeline.
- From ops/admin menu → event processing errors → drill into payload snapshot.

### User workflows
- **Happy path (viewer):**
  1. Dispatcher opens appointment detail.
  2. Sees current status updated based on latest processed Workexec event.
  3. Reviews timeline entries with timestamps and source event ids.
- **Alternate path (reopen):**
  1. Appointment shows `status=REOPENED` and `reopenFlag=true`.
  2. Timeline includes entry tied to reopen-causing event.
- **Ops path (failure):**
  1. Ops opens error list.
  2. Sees orphaned event with workOrderId and eventId.
  3. Uses it to remediate mapping outside this story.

---

## 6. Functional Behavior

### Triggers
- Receipt of a Workexec event:
  - `WorkorderStatusChanged`
  - `InvoiceIssued`

### UI actions
- No manual status changes in UI (read-only; state is event-driven).
- UI provides visibility into:
  - current appointment status
  - reopen flag
  - timeline entries
  - event processing failures (if optional screen implemented)

### State changes (Appointment)
On successful processing:
- Update `Appointment.status` to mapped appointment status (subject to precedence rules).
- If mapped status corresponds to “reopened” semantics:
  - set `Appointment.reopenFlag = true` (permanent once set).
- Append a `StatusTimelineEntry` (if not already present for that `sourceEventId` or equivalent idempotency key).

### Service interactions
- Ingestion service receives event payload.
- Lookup appointment via mapping table using `workOrderId` (primary key).
- Apply mapping and precedence logic.
- Persist appointment update + timeline entry atomically.
- Record processing outcome (success/failure) for observability.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Reject/route to failure handling if:
  - `eventId` missing/blank
  - `workOrderId` missing/blank
  - `newStatus` missing/blank for `WorkorderStatusChanged`
  - event type unrecognized
- If `workOrderId` does not map to an appointment:
  - mark event as failed/orphaned; do **not** create an appointment
- If `newStatus` has no mapping:
  - mark event as failed; do **not** update appointment

### Enable/disable rules
- Appointment status field is **not editable** in UI.
- Timeline entries are **not editable** in UI.

### Visibility rules
- Show `reopenFlag` only when true or always with boolean indicator (TBD; safe default: always show).
- Timeline list shows newest first (safe default).

### Error messaging expectations
- Viewer UI: if timeline cannot load, show non-destructive error “Unable to load status history. Try again.”
- Admin/error screen: show failure reason + identifiers, but do not expose secrets/PII; allow viewing sanitized payload.

---

## 8. Data Requirements

### Entities involved (conceptual; exact Moqui entity names TBD)
- `Appointment`
  - `appointmentId` (PK)
  - `status` (enum/string; required)
  - `reopenFlag` (boolean; required; default false; once true never false)
- `StatusTimelineEntry` (append-only)
  - `statusTimelineEntryId` (PK)
  - `appointmentId` (FK; required)
  - `status` (enum/string; required)
  - `changeTimestamp` (datetime UTC; required)
  - `sourceEventId` (UUID/string; required; unique per appointment to ensure idempotency)
  - `sourceEventType` (string; required)
  - `workOrderId` (string; required for traceability)
  - `eventTimestamp` (datetime UTC; required)
  - `correlationId` (string/UUID; optional but recommended)
- `WorkOrderAppointmentMapping`
  - `workOrderId` (PK; required)
  - `appointmentId` (FK; required)
  - `status` (optional)
  - `createdAt` (datetime UTC)

Optional (for ops visibility):
- `WorkexecEventProcessingLog`
  - `eventId` (PK or unique; required)
  - `eventType` (required)
  - `workOrderId` (required)
  - `appointmentId` (nullable)
  - `processingStatus` (SUCCESS/FAILED)
  - `failureReason` (nullable string enum)
  - `receivedAt`, `processedAt` (UTC)
  - `payloadHash` or `payloadJson` (see Open Questions re: storage policy)

### Read-only vs editable
- All fields above are system-managed; UI is read-only for these aspects.

### Derived/calculated fields
- “Current status” is stored on Appointment; timeline is historical.
- “Currently reopened” can be derived as `Appointment.status == REOPENED` (do not add new field in this story).

---

## 9. Service Contracts (Frontend Perspective)

> Moqui-specific implementation detail: in Moqui, services can be invoked by REST endpoints and screens can call services via transitions/actions.

### Load/view calls
- `workexec.AppointmentStatusDetail` (name illustrative)
  - Input: `appointmentId`
  - Output: appointment status, reopenFlag, timeline entries (paged)
- `workexec.ListEventProcessingFailures` (optional)
  - Input: filters (date range, workOrderId, eventType, failureReason)
  - Output: list of failures with identifiers

### Create/update calls (event-driven)
- `workexec.ProcessWorkexecEvent`
  - Input (payload):
    - `eventId` (required)
    - `eventType` (required: `WorkorderStatusChanged` | `InvoiceIssued`)
    - `workOrderId` (required)
    - `newStatus` (required for status-changed)
    - `eventTimestamp` (required)
    - `correlationId` (optional)
  - Output:
    - `processingStatus` (SUCCESS/FAILED)
    - `appointmentId` (if resolved)
    - `appointmentStatusApplied` (if success)
    - `idempotentNoop` (true if duplicate event)
    - `failureReason` (if failed)

### Error handling expectations
- 201/200 on success depending on ingestion style (TBD).
- 400 for invalid payload.
- 404 not used for orphaned event (should be processed into failure log + acknowledged; transport-level response depends on ingestion mechanism).
- 409 for concurrency conflict (e.g., optimistic lock) with retry guidance.
- Idempotency: same `eventId` processed twice returns SUCCESS with `idempotentNoop=true` and no additional timeline entry.

---

## 10. State Model & Transitions

### Workexec statuses (authoritative in backend reference)
Workexec → Appointment mapping table (must be implemented exactly as provided in backend reference):

| Workexec Status | Appointment Status |
|---|---|
| CREATED | SCHEDULED |
| PENDING | SCHEDULED |
| ASSIGNED | SCHEDULED |
| CUSTOMER_ARRIVED | CHECKED_IN |
| CHECKED_IN | CHECKED_IN |
| IN_PROGRESS | WORK_IN_PROGRESS |
| STARTED | WORK_IN_PROGRESS |
| PARTS_PENDING | WAITING_FOR_PARTS |
| ON_HOLD | WAITING_FOR_PARTS |
| PARTS_ORDERED | WAITING_FOR_PARTS |
| INSPECTING | QUALITY_CHECK |
| QC_IN_PROGRESS | QUALITY_CHECK |
| WORK_COMPLETE | READY_FOR_PICKUP |
| AWAITING_CUSTOMER | READY_FOR_PICKUP |
| CLOSED | COMPLETED |
| DELIVERED | COMPLETED |
| CUSTOMER_PICKUP | COMPLETED |
| CANCELLED | CANCELLED |
| ABANDONED | CANCELLED |
| INVOICE_GENERATED | INVOICED |
| INVOICED | INVOICED |
| REOPENED | REOPENED |
| REWORK_REQUIRED | REOPENED |

### Precedence rules (apply during event processing)
1. `CANCELLED` is terminal — once appointment is CANCELLED, ignore subsequent updates **except** explicit reopen statuses (`REOPENED`, `REWORK_REQUIRED`).
2. `INVOICED` supersedes `COMPLETED` — if events arrive out of order, INVOICED wins.
3. `REOPENED` supersedes terminal statuses (`COMPLETED`, `CANCELLED`) until resolved (resolution semantics not defined here; appointment remains REOPENED until a non-reopen status arrives and is allowed by precedence).
4. Multiple events mapping to same appointment status are idempotent (no duplicate timeline entries).

### Role-based transitions
- No human-initiated transitions in UI for this story.

### UI behavior per state
- Always display current status.
- If `status == REOPENED` or `reopenFlag == true`, surface “Reopened” indicator.
- Timeline shows sequence of status changes with timestamps and source event ids.

---

## 11. Alternate / Error Flows

### Validation failures (bad payload)
- Missing required identifiers/status:
  - Record failure with reason `INVALID_PAYLOAD`
  - Do not update appointment
  - Return 400 to sender if HTTP ingestion (TBD)

### Concurrency conflicts
- If appointment update fails due to concurrent write:
  - Service retries once (safe default) or returns 409 with `retryable=true` (TBD)
  - Ensure idempotency key prevents duplicate timeline insertion on retry

### Unauthorized access
- Ingestion endpoint/service requires authentication appropriate to integration (TBD).
- UI screens require authenticated user; error screen restricted to admin/ops roles (TBD).

### Empty states
- Appointment has no timeline entries: show “No status changes recorded yet.”
- Failure list empty: show “No processing failures in selected period.”

### Orphaned event (no mapping)
- Record failure reason `APPOINTMENT_NOT_FOUND`
- Persist event identifiers for later remediation
- Do not create/modify any appointment
- Mark as DLQ candidate (if broker integration exists) or store in failure log

### Invalid status mapping
- Record failure reason `INVALID_STATUS_MAPPING`
- Persist offending `newStatus` value
- Do not update appointment

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Successful status update from WorkorderStatusChanged
Given an Appointment exists with a mapping for workOrderId "WO-123"  
And the Appointment status is "SCHEDULED"  
When the system receives a WorkorderStatusChanged event with eventId "event-abc", workOrderId "WO-123", newStatus "IN_PROGRESS", and an eventTimestamp in UTC  
Then the Appointment status is set to "WORK_IN_PROGRESS"  
And a status timeline entry is appended with status "WORK_IN_PROGRESS" and sourceEventId "event-abc"  
And the entry includes the workOrderId "WO-123" and the eventTimestamp

### Scenario 2: Idempotent duplicate delivery does not duplicate timeline
Given the system has already processed eventId "event-abc" for workOrderId "WO-123"  
And the Appointment status is "WORK_IN_PROGRESS"  
When the system receives the same event again with eventId "event-abc"  
Then the Appointment status remains "WORK_IN_PROGRESS"  
And no additional timeline entry is created for sourceEventId "event-abc"  
And the processing result indicates idempotent no-op

### Scenario 3: Reopen sets permanent reopenFlag and status REOPENED
Given an Appointment exists mapped to workOrderId "WO-456"  
And the Appointment status is "COMPLETED"  
And reopenFlag is false  
When the system receives a WorkorderStatusChanged event with newStatus "REOPENED"  
Then the Appointment status is set to "REOPENED"  
And reopenFlag is set to true  
And a timeline entry is appended for the REOPENED status

### Scenario 4: ReopenFlag is never cleared
Given an Appointment has reopenFlag true  
When the system processes subsequent events that map to non-reopened statuses  
Then reopenFlag remains true

### Scenario 5: CANCELLED terminal rule blocks non-reopen updates
Given an Appointment status is "CANCELLED"  
When the system receives a WorkorderStatusChanged event that maps to "WORK_IN_PROGRESS"  
Then the Appointment status remains "CANCELLED"  
And no timeline entry is appended for that event  
And the processing result indicates the event was ignored due to precedence

### Scenario 6: CANCELLED can be superseded by reopen
Given an Appointment status is "CANCELLED"  
When the system receives a WorkorderStatusChanged event with newStatus "REWORK_REQUIRED"  
Then the Appointment status becomes "REOPENED"  
And reopenFlag is true  
And a timeline entry is appended for REOPENED

### Scenario 7: INVOICED supersedes COMPLETED with out-of-order delivery
Given an Appointment status is "COMPLETED"  
When the system receives an InvoiceIssued (or status-mapped INVOICED) event for the same workOrderId  
Then the Appointment status becomes "INVOICED"  
And a timeline entry is appended for INVOICED

### Scenario 8: Orphaned event (no appointment mapping) is recorded as failure
Given no appointment mapping exists for workOrderId "WO-999"  
When the system receives a WorkorderStatusChanged event for workOrderId "WO-999"  
Then the system records a processing failure with reason "APPOINTMENT_NOT_FOUND"  
And the event is not applied to any appointment

### Scenario 9: Invalid status mapping is recorded as failure
Given an Appointment mapping exists for workOrderId "WO-123"  
When the system receives a WorkorderStatusChanged event with newStatus "SOME_NEW_STATUS" that is not in the mapping table  
Then the system records a processing failure with reason "INVALID_STATUS_MAPPING"  
And the Appointment status is not changed  
And no timeline entry is appended

---

## 13. Audit & Observability

### User-visible audit data
- Appointment status timeline displays:
  - status
  - change timestamp (when processed)
  - event timestamp (when occurred)
  - source event id
  - correlation id (if present)

### Status history
- Timeline is append-only; UI does not allow edits/deletes.

### Traceability expectations
- Logs and stored failure records include: `eventId`, `workOrderId`, `appointmentId` (if resolved), `correlationId`.
- If payload storage is allowed, store only sanitized/minimal payload or hash (policy TBD).

---

## 14. Non-Functional UI Requirements

- **Performance:** Appointment detail loads timeline within 1s for last 50 entries (excluding network latency); support pagination for longer histories.
- **Accessibility:** Timeline table supports keyboard navigation and screen-reader labels for status and timestamps.
- **Responsiveness:** Appointment detail and timeline usable on tablet width (typical shop floor device).
- **i18n/timezone:** Display timestamps in user’s locale/timezone, while storing timestamps in UTC.
- **Security:** Do not expose raw event payloads containing sensitive information in general user screens; restrict ops views.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for no timeline / no failures; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION-DEFAULT: Paginate timeline (default page size 50); safe to avoid large renders; impacts UX Summary, Data Requirements.
- SD-ERR-RETRY-ONCE: Retry once on transient concurrency failure; safe operational default; impacts Error Flows, Service Contracts.
- SD-OBS-CORRELATION-ID: Surface correlationId when provided; safe observability boilerplate; impacts Audit & Observability, Data Requirements.

---

## 16. Open Questions
1. **Event ingestion mechanism (blocking):** In this Moqui frontend repo, how are Workexec events delivered/handled?
   - HTTP webhook endpoint in Moqui?
   - Message broker consumer?
   - Polling job/inbox table?
2. **System of record / domain ownership (blocking):** Are `Appointment`, `WorkOrderAppointmentMapping`, and timeline entities already defined in this repo (Moqui entities), or are they provided by another service?
   - Provide exact Moqui entity names and field names to update.
3. **Routing / screen taxonomy (blocking):** What is the canonical route/menu location for Appointment detail in this frontend project so we extend the correct screen?
4. **Security (blocking):** What auth mechanism should protect the ingestion endpoint/consumer (shared secret, mTLS, signed JWT), and which roles can access the ops failure screen?
5. **Failure handling (blocking):** Should orphaned/invalid events be:
   - stored in a DLQ managed outside Moqui,
   - stored in Moqui DB for review,
   - or both?
6. **Idempotency key (blocking):** Is `eventId` guaranteed globally unique and stable across retries? If not, what composite key should be used (e.g., workOrderId + newStatus + eventTimestamp)?
7. **Invoice event semantics (blocking):** Does `InvoiceIssued` arrive with its own event type only, or as a status within `WorkorderStatusChanged`? Confirm which payload fields are present for `InvoiceIssued`.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Update Appointment Status from Workexec Events  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/127  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

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


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*