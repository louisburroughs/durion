## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:audit
- status:draft

### Recommended
- agent:audit-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Audit: View/Search Audit Trail for Schedule & Assignment Changes

### Primary Persona
Shop Manager

### Business Value
Provide a persistent, immutable, and searchable audit history of scheduling and mechanic assignment changes so Shop Managers can investigate discrepancies, resolve disputes, and support compliance/accountability.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Shop Manager  
- **I want** to view and search audit history for schedule and mechanic assignment changes (including who/when/what changed and why)  
- **So that** I can explain conflicts, resolve disputes, and demonstrate operational accountability.

### In-scope
- New/updated Moqui **screens** in the frontend to:
  - Search audit logs by required filters (appointment/work order/mechanic plus date range and additional filters as available).
  - View audit log list results (reverse chronological).
  - View audit log detail (including normalized fields + raw payload when available).
- UI handling for immutability (read-only) and authorization failures.
- UI integration points (“deep links”) back to workexec entities **if provided by backend**.

### Out-of-scope
- Producing audit events (workexec outbox/eventing) and audit persistence/retention enforcement (backend responsibility).
- Creating/updating/deleting audit records (must not exist in UI).
- Managing reason code registry (unless an explicit query endpoint exists and is required by UI; currently unspecified).

---

## 3. Actors & Stakeholders
- **Shop Manager (primary):** Searches and reviews audit history.
- **Compliance Auditor / Support (secondary):** May use the same UI for investigations (role-gated).
- **Service Advisor / Dispatcher (implicit):** Appears as “actor” on audit entries.
- **System:** Provides audit search and detail endpoints; enforces tenant/location isolation.

---

## 4. Preconditions & Dependencies
- User is authenticated and has an active **tenant** context; location context is available (explicit selection or derived).
- Backend audit services exist (or will exist) to:
  - Search/list audit logs with filters and pagination.
  - Retrieve audit log detail by `eventId`.
- Backend returns fields required for rendering:
  - `eventId`, `occurredAt`/timestamp (UTC), actor identity, entity/aggregate references, eventType, reasonCode/reasonNotes, change summary text and/or patch, and optionally raw payload and deep-link metadata.
- Authorization model exists for audit viewing/export (exact roles/permissions currently unclear; see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From POS navigation: **Security / Audit** (or equivalent admin/manager area).
- Contextual entry points (optional if provided): “View Audit History” link from:
  - Work Order detail
  - Appointment detail
  - Mechanic profile  
  (These contextual links pre-fill filters; whether these screens exist in this frontend is unknown—see Open Questions.)

### Screens to create/modify
- **Create** `apps/pos/screen/audit/AuditLogSearch.xml` (name illustrative; final path must follow repo conventions)
  - Search form + results grid.
- **Create** `apps/pos/screen/audit/AuditLogDetail.xml`
  - Read-only audit entry detail view.
- **Modify** navigation/menu screen to add “Audit Logs” entry (location depends on existing nav structure).

### Navigation context
- `AuditLogSearch` → selecting a result row navigates to `AuditLogDetail` with `eventId`.
- `AuditLogDetail` includes “Back to results” preserving prior search parameters (Moqui parameter pass-through).

### User workflows
**Happy path (search + view):**
1. Shop Manager opens Audit Logs.
2. Sets date range + optional filters (workOrderId / appointmentId / mechanicId / actor / eventType / location).
3. Runs search; sees results sorted newest-first.
4. Clicks an entry; sees detail including diff summary, structured patch (if present), reason code/notes, and cross-domain references.

**Alternate paths:**
- No results → show empty state with suggestion to broaden filters.
- Unauthorized → show access denied screen/message.
- Backend error → show non-PII error banner and allow retry.

---

## 6. Functional Behavior

### Triggers
- User navigates to Audit Logs screen.
- User submits search form.
- User opens an audit record detail.

### UI actions
- Search form actions:
  - Set `dateFrom` and `dateTo` (required unless backend provides defaulting).
  - Enter exact-match identifiers: `workOrderId`, `appointmentId`, `mechanicId`.
  - Select `eventType` (if backend provides enumerations or free text entry if not).
  - Enter `actorUserId` (exact) or actor search (if backend supports).
  - Select `locationId` (required if not implicit in session).
- Results list actions:
  - Pagination (next/prev, page size up to max).
  - Sort is fixed to reverse chronological unless backend supports alternate sort (not required).
  - Open detail view by `eventId`.

### State changes
- No domain state changes; UI is read-only.
- UI state: query params, results list, selected record.

### Service interactions
- On screen load: optionally load defaults (current tenant, location options, eventType options) if endpoints exist.
- On search: call audit query service (see Service Contracts).
- On detail: call audit detail service by `eventId`.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Date range**:
  - UI must require `start` and `end` date/time **unless** backend guarantees a safe default (e.g., last 90 days). In strict mode, treat as required until confirmed.
  - Validate `start <= end`.
  - Validate max range if backend enforces one (unknown; see Open Questions).
- **Exact match identifiers**:
  - `workOrderId`, `appointmentId`, `mechanicId`, `actorUserId` are treated as exact strings; no formatting assumptions.
- **Reason code**:
  - Display `reasonCode` as provided (namespaced code) and `reasonNotes` if present.
  - Do not allow editing.

### Enable/disable rules
- “Search” button disabled until required fields are valid (at minimum date range).
- “View detail” enabled only when `eventId` is present.

### Visibility rules
- If structured patch exists → show “Change Details” section.
- If only human-readable summary exists → show summary.
- If raw payload exists → show expandable/collapsible raw JSON view (read-only).
- If deep-link metadata exists → show “Open Source Record” link(s); otherwise hide.

### Error messaging expectations
- Validation errors: inline field messages (“Start date is required”, “End date must be after start date”).
- Authorization: show “You do not have permission to view audit logs.”
- Backend errors: generic banner (“Unable to load audit logs. Please retry.”) plus correlation/reference id if returned.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `AuditLog` (read-only, from backend)
- `ReasonCode` registry (read-only, only if backend provides endpoint for display names; otherwise display code as-is)

### Fields (type, required, defaults)
**AuditLog list item (minimum render set):**
- `eventId` (string, required) — primary key for detail navigation
- `occurredAt` or `timestamp` (datetime string, required) — display in user’s locale, sourced as UTC
- `eventType` (string, required)
- `aggregateType` / `entityType` (string, required)
- `aggregateId` / `entityId` (string, required)
- `actor.id` / `actorId` (string, required)
- `actor.displayName` (string, optional)
- `locationId` (string, required for filtering/display if multi-location)
- `changeSummaryText` (string, optional)
- `reasonCode` (string, optional)
- `reasonNotes` (string, optional)

**AuditLog detail (additional):**
- `schemaVersion` (string/int, optional but expected)
- `emittedAt` (datetime, optional)
- `correlationId` (string, optional but preferred)
- `changePatch` (JSON array/object, optional)
- `rawPayload` (JSON/text, optional)
- `sourceSystem` (string, optional)
- `deepLinks` / link metadata (optional)

### Read-only vs editable by state/role
- All audit fields are read-only for all roles in this story.

### Derived/calculated fields
- “When” display: convert UTC timestamp to user locale/timezone for display; retain UTC in raw view.
- Display actor: prefer `actor.displayName`, fallback to `actorId`.

---

## 9. Service Contracts (Frontend Perspective)

> Backend API/service names are not specified in provided inputs. Moqui screens must call services; until exact service names/paths are confirmed, implement using placeholders and mark as blocking.

### Load/view calls
- **Load search screen context (optional):**
  - `audit.getSearchContext` (placeholder)
    - returns available `locationId` options (if needed) and `eventType` options (if provided).

### Create/update calls
- None (immutability).

### Submit/transition calls
- **Search/list audit logs:**
  - `audit.findAuditLogs` (placeholder)
  - Request params (AND semantics):
    - `dateFrom` (UTC datetime, required)
    - `dateTo` (UTC datetime, required)
    - `workOrderId` (optional, exact)
    - `appointmentId` (optional, exact)
    - `mechanicId` (optional, exact)
    - `actorUserId` (optional, exact)
    - `eventType` (optional)
    - `locationId` (optional/required depending on tenant model)
    - pagination: `pageIndex`/`offset`, `pageSize`/`limit`
  - Response:
    - `results[]` list of normalized audit entries (fields above)
    - pagination metadata (total count or next cursor)

- **Get audit log detail by eventId:**
  - `audit.getAuditLog` (placeholder)
  - Request: `eventId` (required)
  - Response: full record including raw payload, patch, correlationId, schemaVersion.

### Error handling expectations
- 400: field-level validation errors displayed inline (e.g., invalid date range).
- 401/403: redirect to login or show forbidden.
- 404 on detail: show “Audit record not found” with eventId.
- 429: show rate limit message and retry option.
- 5xx: show generic error banner; log correlation id if present.

---

## 10. State Model & Transitions

### Allowed states
- Audit records: immutable (no state transitions exposed to UI).

### Role-based transitions
- Only “view” actions; no transitions.

### UI behavior per state
- N/A; read-only views only.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing date range → prevent search; show inline errors.
- Start > end → prevent search; show inline errors.
- Invalid identifier formats → no strict validation unless backend defines formats; treat as opaque strings.

### Concurrency conflicts
- If detail fetch returns not found (record aged out or incorrect id) → show not found state and link back to search.

### Unauthorized access
- If user lacks permission to access audit search/detail:
  - Search screen: show forbidden state, do not attempt queries repeatedly.
  - Deep links: hide if unauthorized/unknown.

### Empty states
- No results: show “No audit entries found for selected filters” and provide “Clear filters” action.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Search audit logs by work order within date range
**Given** I am logged in as a Shop Manager with permission to view audit logs  
**And** I am on the Audit Logs search screen  
**When** I enter a date range from "2026-01-01T00:00:00Z" to "2026-01-31T23:59:59Z"  
**And** I enter workOrderId "WO-123"  
**And** I run the search  
**Then** I see a list of audit entries filtered to workOrderId "WO-123"  
**And** the list is sorted in reverse chronological order by occurredAt/timestamp  
**And** each row shows occurredAt, actor, eventType, and a change summary (text or indicator).

### Scenario 2: Search audit logs by mechanic within date range
**Given** I am logged in as a Shop Manager with permission to view audit logs  
**When** I search with a valid date range and mechanicId "M-456"  
**Then** I see audit entries related to mechanic assignment changes for "M-456" (as represented by aggregate/entity references in results).

### Scenario 3: View audit log detail
**Given** I have search results on the Audit Logs screen  
**When** I select an audit entry with eventId "EVT-001"  
**Then** I navigate to the Audit Log detail screen for "EVT-001"  
**And** I can see eventId, occurredAt (displayed in my locale), actor, eventType, entity/aggregate type and id  
**And** I can see reasonCode and reasonNotes if present  
**And** I can see changePatch and/or changeSummaryText if present  
**And** the audit record is read-only (no edit or delete actions are available).

### Scenario 4: Enforce required date range validation in UI
**Given** I am on the Audit Logs search screen  
**When** I attempt to search without setting a start and end date  
**Then** the search is not submitted  
**And** I see inline validation errors indicating the date range is required.

### Scenario 5: Unauthorized user cannot view audit logs
**Given** I am logged in as a user without permission to view audit logs  
**When** I navigate to the Audit Logs screen  
**Then** I see an access denied message  
**And** no audit data is displayed.

### Scenario 6: Audit log detail not found
**Given** I navigate directly to the Audit Log detail screen with eventId "EVT-NOT-FOUND"  
**When** the backend returns a not-found response  
**Then** I see a “not found” state  
**And** I can navigate back to the Audit Logs search screen.

---

## 13. Audit & Observability

### User-visible audit data
- Display key normalized fields: occurredAt, actor, eventType, aggregate/entity references, reason code/notes, summary/patch.
- Display correlationId if provided to aid support investigations.

### Status history
- Not applicable beyond the audit record itself; no UI-driven status changes.

### Traceability expectations
- UI should preserve and pass through `correlationId` when present in responses into frontend logs (console/app log) for troubleshooting (avoid exposing sensitive raw payload by default).
- If backend provides deep-link metadata (entityType/entityId), show navigation back to workexec screens when available.

---

## 14. Non-Functional UI Requirements

- **Performance:** Search results must paginate; do not attempt to render unbounded lists. Page size must respect backend maximums.
- **Accessibility:** All form controls labeled; keyboard navigable; error messages associated with fields.
- **Responsiveness:** Works on tablet/desktop; results table supports horizontal scroll if needed.
- **i18n/timezone:** Display timestamps localized for the user, but preserve UTC in raw views. Currency not applicable.

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions

1. **Backend service contract:** What are the exact Moqui service names (and parameter/response field names) for:
   - audit search/list (filters + pagination)
   - audit detail by `eventId`
   - optional search context (eventType list, location list)?
2. **Authorization model:** What permission/role(s) in this frontend control access to audit search and detail (e.g., Shop Manager, Compliance, Support)? Is there a specific permission string to check in Moqui screens?
3. **Navigation placement:** Where should “Audit Logs” live in the POS navigation (menu structure), and should there be contextual links from Work Order / Appointment / Mechanic screens in this frontend?
4. **Filter requirements:** Is `locationId` required in the UI (multi-location tenants), or is location implicit from the logged-in session context?
5. **Date range limits:** Does backend enforce a maximum date range window per query (e.g., max 90 days), and should UI enforce the same?
6. **EventType vocabulary:** Should eventType be a controlled dropdown (requires endpoint/enum) or free-text filter?
7. **Reason code display:** Will backend return `reasonCode` only, or also `displayName/description` from the audit-owned registry for UI display?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Audit Trail for Schedule and Assignment Changes — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/125

Labels: frontend, story-implementation, user

## Frontend Implementation for Story

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