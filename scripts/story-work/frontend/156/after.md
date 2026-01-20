## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] CRM Integration Admin UI: View & Triage Inbound Workorder-Originated Events (Processing Log + Suspense/DLQ)

### Primary Persona
- **Integration Support Engineer / Admin User** (human operator using the POS frontend)
- Secondary: **Data/Integration Engineer** (uses UI for debugging & validation during rollout)

### Business Value
Provide an operator-facing way to **monitor, audit, and triage inbound Workorder Execution events** processed by CRM so that failures (schema violations, missing entities, retries exhausted) can be detected, diagnosed, and re-driven without direct database access.

---

## 2. Story Intent

### As a / I want / So that
- **As an Integration Support Engineer**, I want to **view inbound event processing status and inspect failed events routed to suspense/DLQ**, so that **I can resolve integration issues quickly and keep CRM data current**.

### In-Scope
- Moqui/Quasar frontend screens to:
  - List and view **ProcessingLog** records.
  - List and view **Suspense/DLQ** items (failed/unprocessable events).
  - Display **traceability** (eventId, correlationId/workorder reference, type/version/source, timestamps, status, failure reason/notes).
- Filtering and search for operational triage (by status/type/date range/correlationId/eventId).
- Role-gated access to integration monitoring screens.
- Standard error handling and empty states.

### Out-of-Scope
- Implementing the actual event consumer/handler (backend processing).
- Defining or changing event schemas for `VehicleUpdated`, `ContactPreferenceUpdated`, `PartyNoteAdded`.
- Remediation actions that mutate backend state (e.g., “replay event”, “acknowledge”, “reprocess”, “delete from DLQ”) **unless clarified**.
- Customer data CRUD driven by events (vehicle/contact/note updates themselves are backend responsibility).

---

## 3. Actors & Stakeholders

### Actors
- **Integration Support Engineer (Admin UI user)**: monitors processing and triages failures.
- **Service Advisor (indirect stakeholder)**: benefits from up-to-date CRM data.
- **Moqui Frontend App**: calls Moqui services/screens to fetch logs and suspense items.
- **Moqui Backend Services**: expose view/query APIs for logs and suspense queue.

### Stakeholders
- **CRM Domain Owner / Integration Engineer**: defines what is visible, what actions are permitted, and data retention/redaction.
- **Security/Compliance**: ensures no sensitive payloads/PII are exposed improperly in UI.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend application.
- User has required permission (see Open Questions on exact permission name/role mapping).

### Dependencies (Blocking if unavailable)
- Backend provides read access to:
  - `ProcessingLog` (query + detail)
  - `SuspenseQueue` / DLQ items (query + detail)
- Backend defines what fields are safe to expose in UI (payload visibility/redaction rules).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Left-nav / Admin menu (or Integration menu) entry:
  - `Integration → Inbound Events` (name TBD)

### Screens to create/modify
Create new Moqui screens (names are implementation suggestions; adjust to repo conventions):
1. **`apps/pos/screen/integration/InboundEventList.xml`**
   - Tabs or subviews:
     - “Processing Logs”
     - “Suspense / DLQ”
2. **`apps/pos/screen/integration/ProcessingLogDetail.xml`**
3. **`apps/pos/screen/integration/SuspenseEventDetail.xml`**

### Navigation context
- From `InboundEventList`:
  - Click a row → navigate to detail screen with `eventId` (and/or suspense item id).
- Detail screen includes “Back to list” preserving filters (via query params).

### User workflows
#### Happy path: triage by status/type
1. User opens `Integration → Inbound Events`.
2. Defaults to Processing Logs tab.
3. User filters to `status=FAILURE` or `status=SKIPPED_DUPLICATE` and a date range.
4. User opens a specific event detail.
5. User copies `eventId`/`correlationId` for troubleshooting.

#### Alternate path: suspense queue investigation
1. User opens Suspense/DLQ tab.
2. User filters by failure reason (schema vs business rule vs retries exhausted).
3. Opens item detail to view envelope metadata and (if allowed) payload excerpt.

---

## 6. Functional Behavior

### Triggers
- Screen load triggers read/query calls to backend services/entities.
- User filter changes trigger re-query.

### UI actions
- **List views**
  - Sort by processed timestamp (ProcessingLog) or received timestamp (Suspense) descending by default.
  - Filter controls:
    - eventType (enum)
    - status (ProcessingLog enum)
    - correlationId (text exact/contains—see Open Questions)
    - eventId (exact)
    - date range (start/end)
- **Detail views**
  - Read-only display of fields.
  - Copy-to-clipboard for `eventId`, `correlationId`.
  - (Optional) show raw JSON payload in a collapsible block **if allowed**.

### State changes
- Frontend does not change processing state in this story (read-only) unless clarified.

### Service interactions
- Calls to backend to query and retrieve details (see Service Contracts).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Filter validation:
  - Date range: end date must be ≥ start date; show inline validation message.
  - UUID fields: if user enters `eventId`, must be UUID format; otherwise show “Invalid eventId format”.

### Enable/disable rules
- “View Detail” action enabled only when row has required identifier (e.g., `eventId`).
- If backend indicates user unauthorized (403), screen shows access-denied state and does not display data.

### Visibility rules
- Payload visibility:
  - Payload display area must be hidden or redacted unless backend explicitly returns a safe-to-display field (see Open Questions).
- Notes/error details:
  - Display `notes` and failure reason, but do not render stack traces unless backend returns a sanitized message.

### Error messaging expectations
- Query failures show non-technical error banner:
  - “Unable to load inbound event logs. Try again.”
- Detail not found (404) shows:
  - “This event record no longer exists or you don’t have access.”

---

## 8. Data Requirements

> Note: Entities are referenced from the story inputs; actual Moqui entity names/fields must match backend implementation. If entities differ, map them in service layer.

### Entities involved
- `ProcessingLog` (internal log of event processing)
- `EventEnvelope` (inbound event metadata; may be embedded in SuspenseQueue item)
- `SuspenseQueue` (DLQ/suspense items for failed events)

### Fields (type, required, defaults)

#### ProcessingLog (list + detail)
- `eventId` (UUID, required, primary key)
- `correlationId` (String, required) — “workorder reference”
- `status` (Enum String, required): `SUCCESS | FAILURE | SKIPPED_DUPLICATE`
- `processedTimestamp` (Datetime, required)
- `eventType` (String/Enum, **required?**) *(not explicitly in ProcessingLog spec; may come from envelope—Open Question)*
- `notes` (String, optional)

#### SuspenseQueue item (list + detail)
Minimum required for triage (exact fields TBD by backend):
- `suspenseId` (UUID or String, required) — unique ID for suspense record
- `eventId` (UUID, required)
- `eventType` (Enum String, required)
- `eventVersion` (String, required)
- `sourceSystem` (String, required; expected “WorkorderExecution”)
- `correlationId` (String, required)
- `timestamp` (Datetime, required) — when event generated
- `failureReason` (String, required) — e.g., “Schema Validation Failed”, “Entity Not Found”, “Processing Failed After Retries”
- `payload` (JSON, optional / restricted)
- `routedTimestamp` (Datetime, optional) — when moved to suspense/DLQ

### Read-only vs editable by state/role
- All fields are **read-only** in this story.

### Derived/calculated fields
- `age` (derived): “time since processed/routed” (optional UI convenience). If implemented, derived purely in UI from timestamps.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui can query entities directly via screens, but for security and payload redaction, prefer service endpoints that enforce RBAC and return safe DTOs.

### Load/view calls

#### Processing logs list
- **Service**: `crm.integration.ProcessingLogFind` (name TBD)
- **Inputs**:
  - `eventId` (optional exact)
  - `correlationId` (optional)
  - `status` (optional enum)
  - `eventType` (optional) *(if available)*
  - `fromTs`, `thruTs` (optional)
  - `pageIndex`, `pageSize` (optional)
  - `orderByField` (default `processedTimestamp DESC`)
- **Outputs**:
  - `results[]` with fields listed in Data Requirements
  - `totalCount`

#### Processing log detail
- **Service**: `crm.integration.ProcessingLogGet`
- **Inputs**: `eventId` (required)
- **Outputs**: ProcessingLog detail DTO

#### Suspense/DLQ list
- **Service**: `crm.integration.SuspenseEventFind`
- **Inputs**: similar filters (eventType, correlationId, eventId, reason, date range, paging)
- **Outputs**: list DTO + totalCount

#### Suspense/DLQ detail
- **Service**: `crm.integration.SuspenseEventGet`
- **Inputs**: `suspenseId` (required) OR `eventId` (if suspense keyed by eventId—Open Question)
- **Outputs**:
  - envelope metadata
  - sanitized error details
  - payload (optional/redacted)

### Create/update calls
- none (read-only story)

### Submit/transition calls
- none (unless “reprocess”/“ack” is later clarified)

### Error handling expectations
- `401`: redirect to login (project standard).
- `403`: show access denied.
- `404`: show not found on detail view.
- `400`: show validation message (filters).
- `5xx`/network: show retry affordance (“Retry” button).

---

## 10. State Model & Transitions

### Allowed states
#### ProcessingLog.status
- `SUCCESS`
- `FAILURE`
- `SKIPPED_DUPLICATE`

#### SuspenseQueue “state”
- Not explicitly defined. UI treats items as “in suspense” records with a failure reason and timestamps.

### Role-based transitions
- None in this story (read-only).

### UI behavior per state
- ProcessingLog list:
  - `SUCCESS`: neutral styling
  - `FAILURE`: highlighted; encourages click-through
  - `SKIPPED_DUPLICATE`: informational
- Suspense list:
  - Always treated as requiring attention; filter by `failureReason`.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Invalid UUID in `eventId` filter → prevent query; show inline error.
- Invalid date range → prevent query; show inline error.

### Concurrency conflicts
- If a record disappears between list and detail:
  - detail service returns 404 → show “Record not found” and link back.

### Unauthorized access
- If user lacks scope/role:
  - list screens render access denied; no partial data shown.

### Empty states
- No results for filters:
  - Show empty state with suggestion to broaden filters and a “Clear filters” action.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View processing logs with filtering
**Given** I am logged in as a user with permission to view inbound event monitoring  
**When** I navigate to `Integration → Inbound Events` and open the “Processing Logs” view  
**Then** I see a paginated list of processing log records including `eventId`, `correlationId`, `status`, and `processedTimestamp`  
**And** I can filter the list by `status` and date range  
**And** the results update to match my filters

### Scenario 2: View processing log detail
**Given** I am viewing the “Processing Logs” list  
**When** I select a log record  
**Then** I am taken to a detail view showing `eventId`, `correlationId`, `status`, `processedTimestamp`, and `notes`  
**And** I can copy `eventId` and `correlationId`

### Scenario 3: View suspense/DLQ list and detail
**Given** I am logged in as a user with permission to view inbound event monitoring  
**When** I open the “Suspense / DLQ” view  
**Then** I see a paginated list of suspense items including `eventId`, `eventType`, `correlationId`, and `failureReason`  
**When** I open a suspense item  
**Then** I see the event envelope metadata and the failure reason/details  
**And** I see payload content only if the backend provides an allowed/sanitized payload field

### Scenario 4: Client-side validation prevents invalid queries
**Given** I am on the inbound event list screen  
**When** I enter an invalid `eventId` value that is not a UUID and attempt to apply filters  
**Then** the UI shows an inline validation error  
**And** no backend query is executed

### Scenario 5: Unauthorized user cannot access monitoring
**Given** I am logged in as a user without permission to view inbound event monitoring  
**When** I navigate to `Integration → Inbound Events`  
**Then** I see an access denied message  
**And** no processing logs or suspense items are displayed

### Scenario 6: Detail record not found
**Given** I navigate directly to a processing log detail URL with an `eventId` that does not exist  
**When** the screen loads  
**Then** I see a “not found” message  
**And** a link to return to the list view

---

## 13. Audit & Observability

### User-visible audit data
- Display fields that support traceability:
  - `eventId`
  - `correlationId` (workorder reference)
  - `sourceSystem`
  - timestamps (`timestamp`, `processedTimestamp`, `routedTimestamp`)
  - processing status / failure reason

### Status history
- Not defined in inputs. If backend provides multiple attempts/retry history, UI may show a read-only “Attempts” table (Open Question).

### Traceability expectations
- The UI must prominently display `eventId` and `correlationId` to correlate with backend logs/metrics.

---

## 14. Non-Functional UI Requirements

- **Performance**: list queries should return within 2s for typical page sizes (e.g., 25–50) assuming indexed fields; UI must paginate (no unbounded loads).
- **Accessibility**: keyboard navigable filters and table rows; proper labels on inputs; sufficient contrast for status indicators.
- **Responsiveness**: usable on tablet widths; tables may switch to stacked rows on small screens.
- **i18n/timezone**: timestamps displayed in user’s locale/timezone (project standard); raw ISO may be shown in tooltip if needed.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide consistent empty-state messaging and “Clear filters” action; safe because it doesn’t change domain behavior and only affects UI ergonomics. (Sections: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION: Default page size 25 with server-side paging; safe because it is standard UI behavior and avoids unbounded queries. (Sections: UX Summary, Service Contracts, Non-Functional)
- SD-ERR-STANDARD: Map HTTP 401/403/404/5xx to standard UI states; safe because it is generic error-handling without altering business policy. (Sections: Business Rules, Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Backend contract for frontend**: What are the exact Moqui services (or entity views) to query `ProcessingLog` and `SuspenseQueue`? Please provide service names, required parameters, and returned fields.
2. **Suspense/DLQ identity**: Is the suspense record addressed by `eventId` or by a separate `suspenseId`/messageId?
3. **Payload visibility/redaction**: Can the frontend display raw `payload` JSON? If yes, what redaction rules apply (PII, secrets), and is there a safe “payloadSummary” field instead?
4. **Permissions/RBAC**: What role(s) or permission key(s) gate access to these screens (e.g., `crm.integration.monitor.read`)? Are there environment-based restrictions (prod vs non-prod)?
5. **Filter semantics**: Should `correlationId` filtering be exact match only, prefix match, or contains? (Impacts performance and indexing.)
6. **Retry/attempt details**: Should the UI expose retry count / last error / next retry timestamp (if available), or only final status?
7. **Operator actions** (explicitly out-of-scope unless confirmed): Should the UI support “reprocess/retry”, “acknowledge”, “export”, or “mark resolved”? If yes, define allowed actions and audit requirements.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Integration: Inbound Event Handler for Workorder-Originated Updates  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/156  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Inbound Event Handler for Workorder-Originated Updates

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **CRM System**, I want **to ingest workorder events that update CRM data** so that **CRM stays current based on operational reality**.

## Details  
- Handle: VehicleUpdated, ContactPreferenceUpdated, PartyNoteAdded.  
- Validate event envelope; idempotent processing; route failures to suspense queue.

## Acceptance Criteria  
- Events processed once.  
- Invalid events routed to suspense/dead-letter.  
- Audit includes source workorder reference.

## Integration Points (Workorder Execution)  
- Workorder Execution emits events via Positivity service layer; CRM consumes and applies changes.

## Data / Entities  
- EventEnvelope  
- ProcessingLog  
- SuspenseQueue

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: CRM

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