```markdown
# AGENT_GUIDE.md — People Domain

---

## Purpose

The People domain manages user and employee lifecycle, identity, roles, assignments, and timekeeping-related personnel data within the modular POS system. It serves as the authoritative source for user status, employee profiles, role assignments, location assignments, and manages offboarding workflows that revoke access while preserving historical data for audit and payroll.

This guide is used by agents implementing People-domain backend + Moqui screens and integrating with other domains (Work Execution, Shop Management, Security).

---

## Domain Boundaries

### Authoritative Data Ownership (People is SoR)
- **Identity & lifecycle**
  - User and Person lifecycle states (`ACTIVE`, `DISABLED`, `TERMINATED`) and employment statuses (`ACTIVE`, `ON_LEAVE`, `SUSPENDED`, `TERMINATED`).
  - Employee profile data (legal name, employee number, contact info, hire/termination dates).
- **Access modeling**
  - Role definitions and role assignments with scope constraints (`GLOBAL`, `LOCATION`).
  - User disable workflow (soft offboarding) and its downstream revocation signaling.
- **Eligibility modeling**
  - Person-to-location assignments with effective dating and primary flags (used by downstream rosters/eligibility).
- **Timekeeping (People-owned time facts for payroll readiness)**
  - Break records (start/end, type, audit flags).
  - Time entry approval state and approval history (period-atomic approvals/rejections).
  - `TimekeepingEntry` records ingested from Shop Management work sessions for payroll review and approval visibility.
- **Reporting**
  - People-owned reports that aggregate People time + integrate with Work Execution totals (e.g., attendance vs job time discrepancy report).

### Exclusions (People is not SoR)
- **Authentication/session management**: delegated to Security service (People emits events and maintains status; Security enforces auth).
- **Work session/job time facts**: owned by Work Execution / Shop Management (People consumes via events or backend-to-backend calls).
- **Dispatch decisions and assignment workflows**: consumers of People roster/eligibility; People provides read models and constraints.
- **UI-only read models in other apps** (e.g., Dispatch roster screens): People may be upstream SoR, but the consuming domain owns its UI.

### Cross-domain read models (important nuance)
Frontend stories introduce read-only screens in other modules (e.g., Dispatch roster). Treat these as **consumers** of People data:
- People owns the canonical identity/status/assignment rules.
- Consumers may maintain their own read model (e.g., `Mechanic` + `MechanicSkill`) populated from People/HR events or APIs.
- People must provide stable identifiers and event semantics to support idempotent sync.

**CLARIFY:** Whether the `Mechanic` roster read model lives in People domain storage or in Shop/Dispatch domain storage. Frontend story #136 describes it as “HR-synced” and references backend story #72 (domain:people), but the UI entry point is under Dispatch.

---

## Key Entities / Concepts

| Entity                      | Description                                                                                  |
|-----------------------------|----------------------------------------------------------------------------------------------|
| **User**                    | Represents system login identity; status controls authentication eligibility.                |
| **Person**                  | Represents the employee or individual; linked to User; holds employment status and profile. |
| **EmployeeProfile**         | Detailed employee data including contact info, employment dates, and status.                 |
| **Role**                    | Defines a permission set with allowed scopes (`GLOBAL`, `LOCATION`).                         |
| **RoleAssignment**          | Links a User to a Role with scope, effective dates, and audit metadata.                      |
| **PersonLocationAssignment**| Assigns a Person to a Location with role, primary flag, and effective dating.                |
| **Break**                   | Records start/end of breaks during a workday, including type and auto-end flags.             |
| **TimeEntry**               | Represents work time records; used for active timers and payroll.                            |
| **TimePeriod**              | Pay period container with gating status (`OPEN`, `SUBMISSION_CLOSED`, `PAYROLL_CLOSED`).     |
| **TimePeriodApproval**      | Append-only records of manager approvals or rejections of time entries per pay period.       |
| **TimekeepingEntry**        | Payroll timekeeping facts ingested from Shop Management (`WorkSessionCompleted`).            |
| **Mechanic (read model)**   | HR-synced roster identity used by Dispatch/ShopMgr; includes status and home location.       |
| **MechanicSkill (read model)** | Skill snapshot associated with a mechanic for dispatch selection.                         |

### Relationships (actionable mental model)
- `User 1—1 Person` (typical; **CLARIFY** if multiple Persons per User is allowed).
- `Person 1—1 EmployeeProfile` (or EmployeeProfile is a projection of Person; depends on backend schema).
- `User 1—N RoleAssignment` (effective-dated, scope-aware).
- `Person 1—N PersonLocationAssignment` (effective-dated; primary uniqueness rule applies).
- `Person/User 1—N TimeEntry` (attendance time) and `Person/User 1—N Break` (subset/type of time entries).
- `Person 1—N TimekeepingEntry` (ingested sessions; payroll readiness).
- `TimePeriod 1—N TimeEntry` and `TimePeriod 1—N TimePeriodApproval` (history).

---

## Invariants / Business Rules

### User & Person Status Lifecycle
- `ACTIVE`: User can authenticate and be assigned work.
- `DISABLED`: User cannot authenticate; identity retained; reversible offboarding.
- `TERMINATED`: Employment ended; cannot authenticate; irreversible.
- Disabling a user is a logical soft delete; no physical data removal.
- Disabled users must be excluded from authentication, assignment pickers, and scheduling.

**Frontend impact:** UI must treat `DISABLED` as non-assignable and hide/disable actions that require an active identity.

### User Deactivation Workflow
- Disabling a user atomically updates User and Person statuses to `DISABLED`.
- Active assignments are ended immediately or scheduled per policy.
- Active timers (`TimeEntry`) are forcibly stopped or queued for saga retries.
- A `user.disabled` event is emitted for downstream consumers.
- Downstream failures do not rollback disable; authentication is blocked immediately.
- Saga retry queue with exponential backoff handles downstream command failures.
- Dead Letter Queue (DLQ) triggers manual intervention after 24h of retry failures.

**Frontend story alignment:** “Disable User” UX requires:
- explicit permission gating (exact permission string is **CLARIFY**),
- policy-driven options (leave assignments active vs end at date) surfaced as capabilities (**CLARIFY** how backend communicates).

### Role and RoleAssignment
- Roles have allowed scopes: `GLOBAL`, `LOCATION`, or both.
- RoleAssignments link Users to Roles with scope, location (if applicable), and effective dates.
- RoleAssignments are immutable in core fields; modifications require ending and recreating assignments.
- Multiple concurrent role assignments allowed; permissions computed as scope-aware union.
- Revocation is via setting `effectiveEndDate` (soft delete).
- No hard deletes except for test or privileged purge.

**Frontend story alignment:** Role assignment UI needs:
- list roles (including allowedScopes),
- list user’s assignments (active and optionally history),
- create assignment,
- end assignment (effectiveEndDate),
- date picker constraints for future/backdated ends (**CLARIFY** backend support).

### PersonLocationAssignment
- Persons can have multiple assignments with effective start/end timestamps.
- Exactly one primary assignment per person per role at any point in time.
- Overlapping assignments for the same `(personId, locationId, role)` are prohibited.
- Creating a new primary assignment automatically demotes existing primary assignments atomically.
- All changes are audited and emit versioned domain events.

**CLARIFY (from frontend story #150):**
- Is primary uniqueness enforced per `personId` overall, or per `(personId, role)`?
- Is `role` required on `PersonLocationAssignment` and if so, what are allowed values?
- Is `effectiveEndAt` inclusive or exclusive? Backend text is inconsistent; UI needs a single rule.

### Employee Profile
- `employeeNumber` and `primaryEmail` must be unique.
- Mandatory fields: `legalName`, `employeeNumber`, `status`, `hireDate`.
- Status lifecycle: `ACTIVE`, `ON_LEAVE`, `SUSPENDED`, `TERMINATED`.
- Contact info completeness enforced as a workflow gate before assignment or activation.
- Duplicate detection with hard-block (409) on high-confidence matches; soft warnings on ambiguous matches.

**Frontend story alignment (#152):**
- Create/update screens must handle:
  - 409 hard duplicate conflicts (blocking),
  - warnings payload (non-blocking),
  - field-level validation errors (400),
  - optimistic concurrency if required (e.g., `lastUpdatedStamp`/version) (**CLARIFY**).

### Breaks (Timekeeping)
- Only one active (`IN_PROGRESS`) break per mechanic at a time.
- Breaks must have a valid `breakType` (`MEAL`, `REST`, `OTHER`).
- Breaks can only be started/ended within an active clock-in session.
- Breaks auto-end at clock-out with audit flags and event generation.
- Break start/end times must not overlap.

**Frontend story alignment (#148):**
- UI must load “current clock-in/break context” and enforce:
  - cannot start if not clocked in,
  - cannot start if break already active,
  - cannot end if none active.
- `breakType=OTHER` notes requirement is **CLARIFY**.
- Timezone for display is **CLARIFY** (user vs location vs device).

### Time Entry Approval (Period-Atomic)
- Manager approves or rejects all `PENDING_APPROVAL` time entries for an employee per pay period atomically.
- Rejection requires reason code and notes.
- Approved entries are immutable; adjustments handled via separate workflow.
- Approval/rejection emits audit logs and domain events.
- Gating: decisions require `TimePeriod.status >= SUBMISSION_CLOSED`.

**Frontend story alignment (#147):**
- Approve/reject endpoints should be idempotent and return actionable 409 conflicts (e.g., blocking entry IDs) when mixed statuses occur.
- Approval history must be append-only and queryable.

### Payroll Timekeeping Ingestion (`TimekeepingEntry`)
- People domain ingests finalized work sessions (`WorkSessionCompleted`) from Shop Management.
- Idempotent ingestion keyed by `(tenantId, sourceSystem, sourceSessionId)` (or equivalent).
- Corrections handled via explicit correction events; no silent overwrites.
- Default approval status is `PENDING_APPROVAL`.

**Frontend story alignment (#122):**
- UI list/detail must expose stable source identifiers (`sourceSystem`, `sourceSessionId`) for audit traceability.
- UI must not show duplicates; backend must enforce idempotency.

### Attendance vs Job Time Discrepancy Report
- People domain aggregates attendance (clocked time) and integrates with Work Execution job time totals.
- Flagging threshold is applied server-side and returned per row (`thresholdApplied`).
- Backend must surface upstream dependency failures (WorkExec unavailable) with a stable error code if possible.

---

## Events / Integrations

| Event Name                         | Direction         | Description                                                                                  |
|-----------------------------------|-------------------|----------------------------------------------------------------------------------------------|
| `user.disabled`                   | Outbound          | Emitted on user disable; consumed by Security, Work Execution, Scheduling services.          |
| `PersonLocationAssignmentChanged` | Outbound          | Versioned event emitted on assignment create/update/end.                                     |
| `RoleAssignmentCreated/Ended`     | Outbound          | Audit and integration events on role assignment lifecycle changes.                           |
| `EmployeeProfileCreated/Updated`  | Outbound          | Audit events emitted on employee profile changes.                                            |
| `BreakStarted/Ended/AutoEnded`    | Outbound          | Audit events for break lifecycle actions.                                                    |
| `TimeEntriesApprovedEvent`        | Outbound          | Emitted after manager approves time entries.                                                 |
| `TimeEntriesRejectedEvent`        | Outbound          | Emitted after manager rejects time entries.                                                  |
| `WorkSessionCompleted`            | Inbound           | Event from Shop Management to ingest payroll timekeeping entries.                            |
| `MechanicUpserted` / `RosterSynced` (name TBD) | Outbound or Inbound | HR roster/skills sync events used by Dispatch read models. **CLARIFY** canonical event names. |

### Integration patterns (what to implement)
- **Event-driven ingestion (ShopMgr → People):**
  - Consume `WorkSessionCompleted` and upsert `TimekeepingEntry` idempotently.
  - Emit a People-domain event when a `TimekeepingEntry` is created/updated for downstream audit/reporting (**TODO** define event name/version if needed).
- **Backend-to-backend report dependency (People → WorkExec):**
  - People report endpoint calls WorkExec to fetch job time totals.
  - Map upstream failures to stable error codes (e.g., `WORKEXEC_UNAVAILABLE`) so UI can message correctly.
- **Roster sync (People/HR → Dispatch/ShopMgr):**
  - Provide either:
    - events (preferred for near-real-time), or
    - reconciliation API (for periodic sync).
  - Ensure stable external key (`personId`/`hrPersonId`) and versioning (`lastSyncedAt`/`version`) for idempotency.

---

## API Expectations (High-Level)

> Moqui can expose services via REST or screen transitions calling services. Frontend stories repeatedly flag missing contract details. Until clarified, treat these as required capabilities and document the expected shapes and error semantics.

### User Management
- Disable user endpoint with confirmation and assignment termination options (**CLARIFY** exact payload and capability discovery).
- Must return updated user/person status and effective timestamp.

### Employee Profile
- Create (`POST /employees`) and update (`PUT /employees/{id}`) endpoints with:
  - 400 field validation errors,
  - 409 duplicate conflicts,
  - optional `warnings[]` for soft duplicates,
  - optional optimistic concurrency (`version`/`lastUpdatedStamp`) (**CLARIFY**).

**CLARIFY:** authoritative read endpoint for edit prefill (`GET /employees/{id}` vs service name).

### Role Assignment
- List roles (include `allowedScopes`).
- List a user’s role assignments (active + optional history).
- Create role assignment (validate scope/location).
- End role assignment (set `effectiveEndDate`).
- Support 409 for conflicts (overlap, version mismatch, invalid end date).

### Person Location Assignment
- List assignments by person (active-only default + include history toggle).
- Create assignment (enforce overlap + primary uniqueness).
- Update assignment or enforce immutability (end + recreate) (**CLARIFY**).
- End assignment (set `effectiveEndAt`).
- List locations for selection.

### Break Management (Mechanic self-service)
- Load current clock-in/break context.
- Start break (`breakType`, optional `notes`).
- End break (prefer server-derived active break; avoid client passing IDs unless required).
- Return stable error codes for 409 conflicts (already in progress, overlap) and state mismatches.

### Time Entry Approval (Manager)
- List manager-authorized employees (direct reports / scoped).
- List time periods (with status and start/end dates if available).
- Fetch time entries for `{employeeId,timePeriodId}`.
- Fetch approval history (`TimePeriodApproval`) for `{employeeId,timePeriodId}`.
- Approve/reject period-atomic actions (idempotent).
- Rejection requires reason code + notes; reason codes should be enumerable via endpoint/service (**CLARIFY**).

### TimekeepingEntry (Ingested Sessions)
- List `TimekeepingEntry` with paging + filters (employeeId, date range, approvalStatus, locationId, workOrderId).
- Get `TimekeepingEntry` detail by `timekeepingEntryId`.
- Include source identifiers and approval status; include rejection metadata/history if available (**CLARIFY**).

### Reports
- Attendance vs Job Time Discrepancy:
  - `GET /api/people/reports/attendance-jobtime-discrepancy`
  - Required params: `startDate`, `endDate`, `timezone`
  - Optional: `locationId`, `technicianIds`, `flaggedOnly`
  - Return `thresholdApplied` per row and `isFlagged` computed server-side.

**CLARIFY:** encoding for `technicianIds` (repeat vs comma-separated) and any max date range constraints.

---

## Security / Authorization Assumptions

### Baseline
- All API calls require authentication.
- Authorization is enforced server-side; UI may hide menu entries/actions but must not rely on client-side gating.

### Role-based access control (expected)
- Admins: disable users, assign roles, manage assignments.
- HR Administrators: manage employee profiles.
- Managers: approve/reject time entries for authorized employees; run discrepancy report for authorized scope.
- Payroll Clerks: view ingested `TimekeepingEntry` records (read-only).

**CLARIFY:** exact permission strings and how the frontend repo checks them (named permissions vs role names).

### Permission hooks (concrete expectations)
Frontend stories require explicit gating for:
- Viewing `TimekeepingEntry` list/detail (Payroll Clerk).
- Starting/ending breaks (Mechanic self-service).
- Managing person location assignments (Manager/People admin).
- Managing role assignments (Admin).
- Disabling users (Admin or elevated permission).
- Running discrepancy report (Manager).

**TODO:** Document the canonical permission names once confirmed (e.g., `timekeeping:read`, `timekeeping:approve`, `people.locationAssignment.manage`, `people.roleAssignment.manage`, `user.disable`, etc.).

### Sensitive data handling
- Do not expose unnecessary identifiers in UI/logs.
- `tenantId` display policy is **CLARIFY** (frontend story asks if it is sensitive).
- Avoid leaking existence of users/persons on 403/404 (prefer generic “not authorized” for forbidden scopes).

---

## Observability (Logs / Metrics / Tracing)

### Audit Logs (must be immutable)
- User disable actions (include chosen assignment handling option, effective timestamp, actor).
- Employee profile create/update (include changed fields list; avoid storing raw PII in logs).
- Role assignment create/end.
- Person location assignment create/update/end (include primary demotion outcomes).
- Break start/end/auto-end (include endReason).
- Time period approve/reject (include reason code + notes for rejection; notes may contain sensitive info—store carefully and restrict access).
- TimekeepingEntry ingestion (include source identifiers and idempotency key).

### Metrics (additions based on frontend stories)
- `people_timekeeping_entry_list_requests_total{status}` and latency histogram.
- `people_timekeeping_entry_detail_requests_total{status}`.
- `people_break_start_total{status,breakType}` and `people_break_end_total{status,endReason}`.
- `people_time_period_approve_total{status}` / `people_time_period_reject_total{status}` plus `conflict_total`.
- `people_location_assignment_mutations_total{operation,status}` and `primary_demotion_total`.
- `people_employee_profile_mutations_total{operation,status}` plus `duplicate_conflict_total` and `duplicate_warning_total`.
- Report metrics:
  - `people_report_attendance_jobtime_runs_total{status}`
  - `people_report_attendance_jobtime_upstream_failures_total{code}` (e.g., WorkExec unavailable)
  - latency histogram for report generation.

### Logs (actionable guidance)
- Always include:
  - `tenantId` (if not sensitive; **CLARIFY**), otherwise include a hashed/opaque tenant key,
  - `correlationId` / `requestId`,
  - actor id (userId) and role context where available,
  - entity ids (userId/personId/timekeepingEntryId) but avoid names/emails/phones.
- For UI-triggered actions (approve/reject, disable user, assignment changes), log:
  - operation name,
  - target ids,
  - outcome (success/failure),
  - HTTP status and backend `errorCode` if present.

### Tracing
- Distributed tracing for:
  - user disable saga (including downstream calls and retries),
  - report generation (People → WorkExec call),
  - ingestion pipeline (event consume → upsert → emit).
- Propagate correlation IDs through:
  - Moqui screen transitions,
  - REST calls,
  - event headers/metadata.

**TODO:** Confirm the standard header names used in this stack (`X-Request-Id`, `traceparent`, etc.) and document them.

---

## Testing Guidance

### Unit Tests (domain rules)
- Status transitions: `ACTIVE → DISABLED`, `DISABLED → ACTIVE` (if supported), `TERMINATED` immutability.
- Role scope validation: cannot assign LOCATION-scoped role without location; cannot assign GLOBAL-only role with location.
- RoleAssignment immutability: ensure updates require end+create.
- PersonLocationAssignment:
  - overlap detection for `(personId, locationId, role)`,
  - primary uniqueness enforcement (per person vs per person+role) once clarified,
  - demotion behavior is atomic.
- Break rules:
  - single active break,
  - cannot start/end without active clock-in session,
  - overlap prevention.
- Time period approval:
  - period gating by `TimePeriod.status`,
  - atomic approve/reject,
  - rejection requires reason+notes.

### Integration Tests (API + persistence)
- `TimekeepingEntry` list/detail:
  - paging/filter correctness,
  - stable sorting (define default sort; **TODO**),
  - 404 on missing id,
  - 403 on unauthorized.
- Break start/end:
  - conflict behavior across multiple sessions (409),
  - state mismatch handling (end when none active).
- Person location assignment mutations:
  - 409 overlap,
  - primary demotion reflected after create.
- Employee profile create/update:
  - 409 duplicates,
  - warnings payload handling,
  - optimistic concurrency conflict (if enabled).
- Attendance vs job time report:
  - success path,
  - upstream WorkExec failure mapping to stable error code.

### Contract Tests (events + error payloads)
- Validate event schemas for:
  - `user.disabled`,
  - assignment change events,
  - approval events,
  - break events,
  - ingestion events (if added).
- Standardize error payload shape across endpoints:
  - `errorCode`, `message`, `fieldErrors{field:msg}`, `correlationId`.
  - Provide fixtures and ensure frontend can map them.

**CLARIFY:** Frontend story asks for standard error payload examples for 400/409.

### Performance Tests
- `TimekeepingEntry` list with typical payroll volumes (paging must remain fast).
- Report endpoint with large date ranges (ensure server-side limits; **CLARIFY** max range policy).

---

## Common Pitfalls

- **Partial disable rollback:** Avoid rolling back user disable on downstream failures; authentication must be blocked immediately.
- **Assignment overlap:** Ensure strict enforcement of no overlapping assignments for the same `(personId, locationId, role)`.
- **Primary uniqueness ambiguity:** UI and backend must agree whether “one primary” is per person or per person+role. Misalignment causes confusing UI and incorrect eligibility.
- **Effective end inclusivity:** Inconsistent inclusive/exclusive end semantics will cause off-by-one-day/hour display bugs and incorrect “active” labeling. Resolve and codify.
- **Role scope mismatch:** Validate role assignment scopes against role metadata to prevent invalid global/location combinations.
- **Duplicate detection false negatives:** Implement robust normalization (lowercase emails; consistent phone normalization policy) to prevent duplicate employee profiles.
- **Optimistic concurrency gaps:** If backend requires `version`/`lastUpdatedStamp`, missing it will cause lost updates or unexpected 409s. Confirm and enforce.
- **Break concurrency across devices:** Mechanics may have multiple sessions (tablet + terminal). Backend must enforce single active break; UI must handle 409 by refreshing context.
- **Report dependency failures:** Attendance vs job time report depends on WorkExec; ensure errors are mapped to stable codes so UI can message “job time system unavailable” rather than generic failure.
- **Leaking sensitive identifiers:** Avoid showing `tenantId` or internal IDs unless policy allows; avoid logging PII in client/server logs.
- **Event ordering/idempotency:** Ingestion and roster sync must be idempotent and resilient to replays; UI should rely on stable source identifiers rather than assuming uniqueness by timestamps.
- **Menu/routing drift:** Moqui screen paths and navigation entries must match repo conventions; multiple frontend stories flag uncertainty. Resolve early to avoid broken deep links.

---

## Open Questions from Frontend Stories

> Consolidated and organized from the provided frontend stories. These are blocking unless marked non-blocking.

### A) Moqui service names / REST endpoints (blocking)
1. TimekeepingEntry:
   - Exact service/endpoints for listing `TimekeepingEntry` with filters + paging.
   - Exact service/endpoints for fetching `TimekeepingEntry` by `timekeepingEntryId`.
2. Breaks:
   - Exact service/endpoints + payloads for:
     - loading current clock-in/break context,
     - starting a break,
     - ending a break.
   - Include status codes and stable error codes.
3. PersonLocationAssignment:
   - Exact service/endpoints for:
     - listing assignments by person,
     - creating an assignment,
     - updating an assignment,
     - ending an assignment,
     - listing locations for selection.
4. Employee profile:
   - Authoritative read endpoint/service to load an employee profile for edit (`GET /employees/{id}` vs Moqui service name) and exact response schema.
5. Role assignments:
   - Exact service/endpoints for:
     - listing Roles (with `allowedScopes`),
     - listing Locations,
     - listing a user’s RoleAssignments,
     - creating RoleAssignment,
     - ending RoleAssignment (set `effectiveEndDate`).
6. Disable user:
   - Exact service/endpoints for:
     - loading user detail (User + Person status fields),
     - performing disable action (payload + response).

### B) Authorization / permissions (blocking)
1. What permission/authorization hook is used in this frontend repo for People screens (named permissions vs roles)?
2. Which roles include **Payroll Clerk**, and what permission gates `TimekeepingEntry` read?
3. Permissions gating view vs manage for:
   - person location assignments,
   - role assignments,
   - disable user action,
   - break start/end (self-service).
4. For disable user: is it strictly `user.disable` or another permission string?

### C) Identifiers, routing, and module placement (blocking)
1. What is the correct Moqui screen path/module for `pos-people` (e.g., `/apps/pos-people/...`) and where should navigation entries live?
2. Mechanic roster:
   - Stable identifier for routing: `personId` (HR key) vs `mechanicId` (local UUID).
3. For breaks: is a break tied to a `Timecard`, `ClockInSession`, or generic `TimeEntry`? Which identifiers must UI pass, or is it derived from authenticated user?

### D) Data shape / fields / enums (blocking unless noted)
1. Is `tenantId` considered sensitive in UI (hide vs admin-only vs always show)?
2. Does `TimekeepingEntry` include rejection metadata (reason code, notes) and/or approval audit history fields for detail display?
3. Should `TimekeepingEntry` list display `employeeId` only, or resolve/display employee name (requires lookup)?
4. PersonLocationAssignment:
   - Is `role` required? If yes, allowed values and selection mechanism (enum vs free text).
   - Primary uniqueness scope: per person overall vs per person+role.
   - Effective end inclusivity: inclusive vs exclusive (backend text inconsistent).
   - Editability: editable after creation vs end+create only; which fields editable?
   - Is `changeReasonCode` required/available? If yes, valid codes and source endpoint.
5. Employee profile:
   - Default status on create (`ACTIVE` vs `ON_LEAVE` vs explicit selection).
   - Edit rules for `TERMINATED` employees (locked vs limited edits; permission).
   - Phone normalization: enforce E.164 in frontend or backend-only; standard library/pattern in repo.
   - Optimistic concurrency: version/etag required (e.g., `lastUpdatedStamp`)?
6. Role assignments:
   - Required permission(s) for viewing vs mutating role assignments.
   - Support for ending future assignments and/or backdating end dates.
   - Reason code requirement for ending/revoking assignments; allowed values.
   - Optimistic locking/version required for end/update.
   - Default list behavior: active only vs include history.
7. Breaks:
   - For `breakType=OTHER`, are `notes` required/optional/unsupported?
   - Timezone standard for displaying break times (user vs location vs device).
   - Last-used break type default: backend-provided vs frontend inference (non-blocking but important).

### E) Error handling and standards (blocking)
1. What is the standard error response format for 400/409 in this stack (field error mapping keys)? Provide an example payload.
2. For report endpoint: expected encoding for `technicianIds` (repeat vs comma-separated vs JSON string).
3. Should report screen enforce a maximum date range, or is it server-controlled?

### F) UX/security policy (blocking)
1. Disable user confirmation UX: required mechanism (simple confirm vs typed username vs checkbox + reason).
2. Disable user: how does backend communicate allowed assignment options (`LEAVE_ASSIGNMENTS_ACTIVE` vs `END_ASSIGNMENTS_AT_DATE`)? Is there a capabilities field?
3. Post-disable navigation: stay on detail (refreshed) vs navigate back to list.
4. Timezone source for displaying `statusEffectiveAt` on disable user screen.

---

# End of AGENT_GUIDE.md
```
