```markdown
# workexec AGENT_GUIDE

## Purpose
The `workexec` domain manages core workflows related to **work execution** in the POS system: work order lifecycle gating, execution-time edits (assignment context, substitutions, parts/labor usage), customer approvals, estimate revision/versioning, and execution-facing reporting views. It is responsible for **authoritative state transitions**, **policy enforcement**, and **immutable auditability**, while integrating with Shop Management (shopmgr), Inventory, Pricing, People/Availability, and Audit subsystems.

> Update note (2026-01): Frontend stories expand `workexec` responsibilities to include:
> - Substitute relationship admin CRUD (potential boundary conflict; see Open Questions)
> - Priced part substitution picker for WO/Estimate part lines
> - Operational/assignment context display + pre-start edit/override rules
> - Dispatch Board reporting view (read model aggregation)
> - Workexec event consumption to update Appointment status/timeline in Moqui (integration + idempotency)

---

## Domain Boundaries

### In Scope
- **Work Order execution UI + rules**
  - Work order status gating for “pre-start” vs “started” behaviors.
  - Assignment context display and pre-start edit (location/resource/mechanics).
  - Operational context display (Shopmgr SoR) and privileged override flows (manager).
  - Execution-time part substitution selection and application to a line item (WO and/or Estimate; scope TBD).
- **Customer approvals**
  - Capturing approvals for work orders and estimates (digital, in-person, partial).
  - Approval expiration and alerting.
  - Approval invalidation on revision/version changes (where applicable).
- **Estimate management (pre-approval)**
  - Adding parts and labor to draft estimates, including price overrides and non-catalog entries (policy-gated).
  - Revising estimates with immutable versions/snapshots.
  - Generating customer-facing estimate summaries.
- **Parts execution workflows**
  - Mechanic picking and confirmation of parts for work orders.
  - (Emerging) Part usage events (issue/consume/return) and authorization checks (frontend stories reference this; backend contract TBD).
- **Reporting/read models**
  - Dispatch Board dashboard view (read-only) for a single location/date with exceptions and availability signals.
- **Integration/event processing in Moqui (when this repo acts as consumer)**
  - Consuming Workexec events to update Appointment status + timeline with idempotency and failure handling.

### Out of Scope
- **Master data ownership**
  - Parts/catalog authoring (product/catalog domain) and inventory master data (inventory domain).
  - Location/bay/team configuration (location/shopmgr domain).
  - People directory and timekeeping SoR (people domain), except where workexec consumes availability signals.
- **Permission model definition**
  - RBAC policy definition and group membership management (security domain). Workexec enforces permissions but does not define them.
- **Notification delivery**
  - Sending SMS/email/push notifications (external notification systems). Workexec may emit events/alerts only.
- **Post-approval change order workflows**
  - Change request workflows after approval/conversion unless explicitly routed through workexec APIs (some stories mention change requests; contracts TBD).

> CLARIFY: Substitute relationship CRUD is “Product/Parts Management flavored” and may belong to `inventory` or a product domain. See “Open Questions from Frontend Stories”.

---

## Key Entities / Concepts

| Entity / Concept | Description |
|---|---|
| **Work Order** | Authoritative execution document with status FSM and line items (parts/labor/services). |
| **Estimate** | Draft/pending quote with parts/labor line items; supports revision/versioning and approval capture. |
| **Estimate Version / Snapshot** | Immutable representation of an estimate at a point in time; used for approvals and audit. |
| **Approval** | Immutable record of customer consent (method, payload hash, timestamps, actor). |
| **Assignment Context** | Workexec-owned editable (pre-start) context on a Work Order: `locationId`, `resourceId` (bay/mobile resource), `mechanicIds[]`. |
| **Operational Context (Shopmgr SoR)** | Shopmgr-provided context for a Work Order: location/bay/schedule/assigned mechanics/resources/constraints + version/audit metadata. Workexec UI displays it; privileged override may be routed via workexec. |
| **SubstituteLink** | Relationship between `partId` and `substitutePartId` with `substituteType`, `isAutoSuggest`, `priority`, effective dates, `isActive`, and `version` for optimistic locking. |
| **WorkOrderPartSubstitution (history)** | Server-side record created when a substitute is applied to a WO/Estimate line; used for audit/traceability. |
| **Picking List** | Parts allocated for a work order; scanned/confirmed by mechanics. |
| **Receiving Session / Inventory Movement** | Receiving workflow records for staging/quarantine movements (existing scope). |
| **DispatchBoardView** | Read model DTO for dispatch board: work scheduled for a date/location + exceptions + bay occupancy + (maybe) availability. |
| **ExceptionIndicator** | DTO describing a warning/blocking exception with target reference and code/category. |
| **Appointment** | Shop management entity updated based on Workexec events (status + reopen flag). |
| **WorkOrderAppointmentMapping** | Mapping table from `workOrderId` → `appointmentId` used during event processing. |
| **StatusTimelineEntry** | Append-only appointment timeline entry keyed by `sourceEventId` (idempotency). |
| **ApprovalExpirationJob / ApprovalAlertJob** | Scheduled jobs for approval expiry and expiring-soon alerts. |

### Relationships (high-signal)
- WorkOrder **has** Assignment Context (workexec-owned) and **references** Operational Context (shopmgr-owned).
- WorkOrder/Estimate **has** line items; line items may be substituted (creates substitution history).
- Estimate **has many** Versions/Snapshots; Approval references a specific snapshot/version.
- Appointment **maps to** WorkOrder via WorkOrderAppointmentMapping; Appointment **has many** StatusTimelineEntry.

---

## Invariants / Business Rules

### Work Order execution gating (pre-start vs started)
- **Pre-start editability**: Assignment Context is editable only when Work Order status is **start-eligible** (frontend stories cite `APPROVED`, `ASSIGNED` as canonical).
  - TODO/CLARIFY: Some story text elsewhere mentions `SCHEDULED`/`READY_FOR_WORK`; confirm canonical start-eligible statuses exposed to frontend.
- **Post-start lock**: After work starts, assignment/operational context becomes read-only for most users.
  - CLARIFY: Whether managers can still override after start, and whether overrides create a new version without mutating “locked-at-start” snapshot.

### Operational Context (Shopmgr SoR)
- Workexec UI must treat Shopmgr Operational Context as **read-only source of truth**.
- Overrides (if supported) must be:
  - **privileged** (manager-only or specific permission),
  - **audited** (actor, reason, timestamp),
  - **concurrency-safe** (version-based optimistic locking if required).

### Substitutes (admin + runtime application)
- **SubstituteLink uniqueness**: `(partId, substitutePartId)` must be unique (backend enforces; UI should handle 409 duplicate).
- **No self-substitute**: `partId != substitutePartId`.
- **Enum constraints**: `substituteType ∈ {EQUIVALENT, APPROVED_ALTERNATIVE, UPGRADE, DOWNGRADE}`.
- **Soft delete**: Deactivation should not remove history; `isActive=false` is preferred.
- **Optimistic locking**: Updates require `version`; stale updates return 409.
- **Runtime substitution**:
  - Candidate list must be policy-filtered server-side.
  - Applying a substitute must create immutable substitution history server-side.
  - UI must not allow “apply” when pricing is unavailable unless manual price permission is explicitly granted (see Security section).

### Approvals
- Approvals can only be captured when the target entity is in a valid state (e.g., “Pending Customer Approval”).
- Digital approvals require tamper-evident payload hashes and immutable storage.
- Partial approvals require line-level decisions and recalculated totals.
- Revisions invalidate prior approvals when snapshot/version changes.

### Receiving / Picking (existing)
- Receiving must use default staging/quarantine locations when configured; otherwise require manual selection.
- Picking disallows over-picking; scanned items must match list; confirmation requires required items picked.
- Inventory status updates must be transactional and auditable.

### Dispatch Board (reporting)
- Read-only view; no mutation actions in v1.
- Must support single location + date filter; auto-refresh via polling (30s).
- Must degrade gracefully if secondary dependency (People availability) fails.

### Event-driven appointment status updates (Moqui consumer)
- **Idempotency**: same event must not create duplicate timeline entries (unique by `sourceEventId` or composite key).
- **Precedence rules** (from story):
  - CANCELLED terminal except explicit reopen statuses.
  - INVOICED supersedes COMPLETED.
  - REOPENED supersedes terminal statuses until resolved.
- **Orphan handling**: if no mapping exists, record failure; do not create appointment.
- **Auditability**: store event identifiers and correlationId (if present) without storing secrets/PII.

---

## Events / Integrations

| Event Name | Description / Payload Highlights | Consumers / Integration Points |
|---|---|---|
| `InventoryReceived` | Emitted on successful receiving to staging. Includes movement details. | `domain:audit`, inventory ledger, reporting |
| `InventoryQuarantined` | Emitted on receiving to quarantine. | `domain:audit`, inventory ledger |
| `workexec.EntityApproved` | Approval captured; includes entity + approval IDs + payload hash. | Billing, audit, downstream workflows |
| `WorkOrderApprovalRecorded` | Partial approval recorded with line statuses + method. | Audit, scheduling, notifications |
| `APPROVAL_EXPIRING_SOON` | Alert job publishes expiring approvals. | External notification system |
| `PICKING_LIST_PARTIAL` / `PICKING_LIST_CONFIRMED` | Picking progress and completion. | Inventory, billing, audit |
| `WorkorderStatusChanged` | Workexec status change event used to update Appointment status/timeline. | Moqui consumer (this repo) / shopmgr |
| `InvoiceIssued` | Invoice issued signal; may be separate event or status. | Moqui consumer / shopmgr |

### Integration patterns (actionable)
- **Synchronous REST (UI-driven)**: Work order detail, assignment context update, operational context fetch/override, substitute CRUD, substitution candidate fetch/apply, approvals, estimate edits.
- **Read model aggregation**: Dispatch Board may require composing Workexec board payload + People availability payload in frontend. Prefer backend aggregation when available to meet SLA.
- **Asynchronous event ingestion** (Moqui as consumer):
  - Use an **inbox** pattern (persist first, then process) if delivery is at-least-once.
  - Enforce **idempotency** at DB level (unique constraint on `sourceEventId` or composite key).
  - Record failures to an ops-visible table/screen; do not drop silently.

> TODO/CLARIFY: Confirm the actual event ingestion mechanism in this repo (webhook vs broker vs polling job) and the security model for it.

---

## API Expectations (High-Level)

> This guide documents patterns and required behaviors. Exact paths vary across stories and are frequently marked TODO/CLARIFY until backend/Moqui integration contracts are confirmed.

### Common API patterns
- **Optimistic concurrency**: resources that support updates should expose `version` and require it on update; stale updates return `409 Conflict`.
- **Idempotency**: mutation endpoints should support `Idempotency-Key` header for create/submit actions where double-submit is likely (e.g., create substitute link, approvals, promote, etc.).
  - TODO/CLARIFY: Confirm whether Moqui gateway/backends honor `Idempotency-Key` and what response is returned on replay.
- **Error envelope**: backend should return a consistent JSON error schema for 400/409 with field errors and correlation id.
  - TODO/CLARIFY: Standard error payload schema is not confirmed; see Open Questions.

### SubstituteLink admin CRUD (story #109)
- Expected endpoints (from story reference):
  - `POST /api/v1/substitutes`
  - `GET /api/v1/substitutes/{id}`
  - `PUT /api/v1/substitutes/{id}` (requires `version`)
  - `DELETE /api/v1/substitutes/{id}` (soft delete)
  - `GET /api/v1/parts/{partId}/substitutes?...` (query candidates for a part)
- TODO/CLARIFY:
  - Whether a list/search endpoint exists: `GET /api/v1/substitutes` with filters/pagination.

### Priced substitution picker (story #113)
- Requires endpoints for:
  - Fetch substitution candidates for a **work order part line**
  - Fetch substitution candidates for an **estimate part line**
  - Apply selected substitute to the target line
- TODO/CLARIFY: Exact routes and payload schemas are not provided; do not guess.

### Operational/Assignment context (stories #123, #128)
- Operational context fetch (Shopmgr SoR):
  - `GET /shopmgr/v1/workorders/{workOrderId}/operational-context`
- Operational context override (Workexec):
  - `POST /workexec/v1/workorders/{workOrderId}/operational-context:override`
  - TODO/CLARIFY: response shape (200 vs 201; returns updated context vs ack).
- Assignment context update:
  - TODO/CLARIFY: exact endpoint path; story suggests `PUT /api/workorders/{id}/assignment-context` or equivalent.

### Dispatch Board (story #124)
- Board endpoint (from story reference):
  - `GET /dashboard/v1/today?locationId={locationId}&date={YYYY-MM-DD}`
- People availability:
  - `GET /people/v1/availability?locationId=...&date=...&includeSchedule=true`
- TODO/CLARIFY:
  - Whether board endpoint already includes availability and bay occupancy (preferred).

### Event ingestion (story #127)
- Moqui service/endpoint to process events:
  - `workexec.ProcessWorkexecEvent` (illustrative)
- TODO/CLARIFY:
  - Transport (HTTP webhook vs broker consumer vs polling job)
  - Whether to return 2xx for orphaned events (recommended: accept + record failure) vs 4xx.

---

## Security / Authorization Assumptions

### Authentication
- All user-initiated actions require authenticated session (Moqui auth).
- Service-to-service ingestion endpoints (events) must use non-user auth (mTLS, signed JWT, or shared secret) and be isolated from browser-accessible routes.

### Authorization (RBAC)
- Enforce authorization server-side; frontend may optionally hide/disable actions if it has a reliable capability signal.
- Known permission-sensitive actions from stories:
  - Substitute management (create/update/deactivate)
  - Apply part substitution to a line item
  - Manual price entry / override (`ENTER_MANUAL_PRICE`)
  - Operational context override (manager action)
  - Assignment context edit (advisor/manager/dispatcher; exact roles TBD)
  - Dispatch Board view (likely dispatcher/manager; permission TBD)
  - Ops screens for event processing failures (admin/ops only)

> TODO/CLARIFY: How the frontend obtains permission scopes/capabilities (session claims vs endpoint vs embedded in WorkOrder/Estimate payload).

### Secure-by-default UI guidance
- Do not log sensitive payloads (signature images, approval tokens/links, raw event payloads) to browser console.
- For event processing ops screens, display **sanitized** payload fields only (ids, status, timestamps, correlationId). Avoid customer PII.
- Prefer backend-derived actor identity from auth context; do not send `userId/actorId` unless explicitly required by contract.

---

## Observability (Logs / Metrics / Tracing)

### Logging (server + frontend)
- **Structured logs** with:
  - `correlationId` (propagated from request header or generated)
  - `workOrderId` / `estimateId` / `appointmentId` (non-PII identifiers)
  - `eventId` for event ingestion
  - `endpoint` and `httpStatus`
- Log levels:
  - INFO: successful state-changing operations (approval captured, substitution applied, assignment context updated, override submitted)
  - WARN: expected conflicts (409), missing configuration, upstream partial failures (People availability down)
  - ERROR: unexpected failures (5xx), processing exceptions, schema violations

### Metrics (actionable set)
- Counters:
  - `workexec_substitute_link_create_total{result}`
  - `workexec_substitute_link_update_total{result}`
  - `workexec_substitution_apply_total{result}`
  - `workexec_assignment_context_update_total{result}`
  - `workexec_operational_context_override_total{result}`
  - `workexec_dispatch_board_fetch_total{result,source=board|people}`
  - `workexec_event_ingest_total{result,failureReason,eventType}`
- Histograms:
  - `workexec_dispatch_board_fetch_latency_ms{source}`
  - `workexec_operational_context_fetch_latency_ms`
  - `workexec_substitution_candidates_fetch_latency_ms`
- Gauges:
  - `workexec_dispatch_board_staleness_seconds` (client-side derived; useful for UX/SLA)

### Tracing
- Propagate trace context across:
  - Workexec → Shopmgr operational context fetch
  - Dispatch board fetch + People availability fetch
  - Event ingestion processing pipeline (receive → persist inbox → apply update)
- Ensure async event processing logs include `eventId` and `correlationId`.

---

## Testing Guidance

### Unit tests
- Business rule helpers:
  - Start-eligible status gating (`APPROVED`, `ASSIGNED`) and post-start lock behavior.
  - SubstituteLink validation (no self-substitute, enum validation, effective date ordering).
  - Appointment status mapping + precedence rules (CANCELLED terminal, INVOICED supersedes, REOPENED precedence).
- Error mapping:
  - 400 field errors → UI field mapping (if schema exists)
  - 409 conflict → refresh/retry UX behavior

### Integration tests
- SubstituteLink CRUD:
  - Create → fetch → update with version → conflict on stale version → deactivate.
- Substitution picker:
  - Fetch candidates → apply → verify line updated and history created (server-side).
  - Pricing unavailable → ensure apply blocked unless permission present.
- Operational/assignment context:
  - Fetch operational context from Shopmgr; handle partial/missing fields.
  - Override flow with 403 and 409 handling.
  - Assignment context update allowed only pre-start.
- Dispatch Board:
  - Board loads with People availability success.
  - People availability fails → board still renders with warning.
  - Polling behavior does not create request storms (debounce/disable refresh while loading).
- Event ingestion:
  - Idempotent duplicate event does not duplicate timeline.
  - Orphaned event recorded as failure.
  - Out-of-order events respect precedence.

### Contract tests (recommended)
- Pin DTO schemas for:
  - SubstituteLink and substitute query response
  - DispatchBoardView and ExceptionIndicator
  - Operational context payload
  - Workexec event payloads (`WorkorderStatusChanged`, `InvoiceIssued`)
  - Standard error envelope
- Validate enum values exactly as serialized (case/casing mismatches are common).

### Security tests
- Authorization enforcement:
  - Substitute management endpoints reject unauthorized users (403).
  - Override endpoints reject unauthorized users.
  - Manual price permission enforced for substitution apply when price missing.
- Event ingestion endpoint:
  - Reject unauthenticated/invalid signature.
  - Ensure payload storage does not persist secrets/PII.

---

## Common Pitfalls

- **Domain boundary drift**
  - SubstituteLink admin CRUD may not belong in workexec long-term. Avoid coupling UI routes/services too tightly until ownership is confirmed. (See Open Questions.)
- **Status enum mismatches**
  - Stories reference multiple status taxonomies (`WORK_IN_PROGRESS` vs `IN_PROGRESS`, `AwaitingApproval` vs `AWAITING_APPROVAL`). Treat backend as authoritative; add mapping layer only when explicitly required.
- **Optimistic locking omissions**
  - Forgetting to send `version` on update leads to lost updates or unexpected 409s.
- **Idempotency gaps**
  - Double-submit on create/apply actions without `Idempotency-Key` can create duplicates (substitute links, approvals, etc.). Confirm support and adopt consistently.
- **Assuming actorId/userId fields**
  - Prefer backend-derived identity. Only send `actorId` if contract requires it; otherwise you risk spoofing vectors and inconsistent audit.
- **Dispatch Board SLA regressions**
  - Fetching board + people sequentially can exceed SLA. Fetch in parallel and render primary board even if People fails.
- **Event ingestion reliability**
  - Not persisting events before processing risks data loss on crashes.
  - Not enforcing DB-level idempotency risks duplicate timeline entries under retries.
- **Timezone handling**
  - Dispatch board and operational context timestamps must display in shop/user timezone; do not assume browser local timezone is correct for shop operations. (Timezone source is currently unclear.)
- **Over-logging**
  - Avoid logging names/PII from People availability responses; log only IDs and counts.

---

## Open Questions from Frontend Stories

### A) Domain boundaries / ownership
1. **Substitutes domain ownership:** Story #109 is labeled `domain:workexec` but is product/parts admin flavored. Should this be `domain:inventory` or a product domain label? **(Blocking)**
2. **Operational context story ownership:** Frontend issue labels “Shop Management/user” but backend reference is `domain:workexec` with Shopmgr as SoR. Confirm ownership/label. **(Blocking)**
3. **Timekeeping / people-adjacent features:** Several stories label domain conflicts (user/shop management vs workexec). Confirm what belongs in workexec UI vs people/shopmgr UI. **(Blocking where applicable)**

### B) Canonical routing / Moqui screen placement
4. What are the canonical Moqui screen paths/routes for:
   - Work Order detail
   - Estimate detail
   - Appointment detail
   - Reporting/Dispatch Board screens
   - TimeEntry list/detail (if in scope elsewhere)
   **(Blocking for implementation)**

### C) API contracts (paths + schemas)
5. **Part lookup UX:** What endpoint/screen should admin UI use to search/select parts by SKU/name? Provide route(s) + response shape. **(Blocking for substitutes UI)**
6. **SubstituteLink list/search:** Do we have `GET /api/v1/substitutes` with filters/pagination, or must list be “query by partId” only? **(Blocking for list screen)**
7. **Substitution picker endpoints:** Exact endpoints and payload schemas for:
   - fetching candidates for a work order part line
   - fetching candidates for an estimate part line
   - applying a selected substitute to the target line
   **(Blocking)**
8. **Dispatch Board endpoint contract:** Confirm exact endpoint path and request/response schema for `DispatchBoardView` and `ExceptionIndicator`. **(Blocking)**
9. **Aggregation responsibility:** Does dispatch board endpoint already include mechanic availability and bay occupancy, or must frontend call `/people/v1/availability` separately? **(Blocking for SLA/composition)**
10. **Appointments vs Work Orders:** Are “appointments” separate entities or represented as work orders with scheduled times? If separate, what endpoint supplies them? **(Blocking)**
11. **Assignment context endpoints:** Exact endpoints/services for:
   - loading work order detail (including assignment context)
   - updating assignment context
   - fetching audit/history for assignment-context changes
   **(Blocking)**
12. **Operational context override contract:** Does override require `version`? What field name? What response shape and success status (200 vs 201)? **(Blocking)**
13. **Event ingestion mechanism:** How are Workexec events delivered/handled in this Moqui repo (webhook, broker consumer, polling/inbox)? **(Blocking)**
14. **Invoice event semantics:** Is `InvoiceIssued` a separate event type or a status within `WorkorderStatusChanged`? What fields are present? **(Blocking)**

### D) Error handling / idempotency standards
15. **Standard error envelope:** For 400/409, what is the standard error response format (field errors, message, correlationId, existingResourceId on duplicates)? **(Blocking for consistent UX)**
16. **Idempotency-Key usage:** Should frontend generate/send `Idempotency-Key` for create calls by default? **(Blocking for safe UX)**
17. **Duplicate signaling:** For duplicates (e.g., SubstituteLink), does backend return existing resource id? If yes, where? **(Clarify)**

### E) Permissions / authorization signals
18. **Permission signal source:** How does frontend determine permission scopes/capabilities (session claims, user context endpoint, embedded in payload)? **(Blocking for proactive gating)**
19. **Manual price override permission:** How does backend indicate user has `ENTER_MANUAL_PRICE` and what is expected frontend behavior when pricing is unavailable? **(Blocking/security)**
20. **Dispatch Board RBAC:** Which roles/permissions may view Dispatch Board? Location membership only or explicit permission (e.g., `DISPATCH_VIEW`)? **(Blocking)**

### F) Business rule clarifications
21. **Editability of SubstituteLink key fields:** On update, are `partId` and `substitutePartId` immutable or editable? If editable, how handle uniqueness conflicts? **(Blocking)**
22. **Defaults alignment:** Does backend default `priority=100` and `isAutoSuggest=false` when omitted? **(Clarify)**
23. **Candidate inclusion rules:** Should candidate list include only available candidates or both available/unavailable with statuses? **(Blocking for UI behavior)**
24. **Eligibility source for substitution:** How does frontend determine “original is unavailable”? Is there a line-level field or should backend enforce? **(Blocking)**
25. **Override-after-start rule:** After work starts, are overrides disallowed, manager-only, or versioned without mutating locked snapshot? **(Blocking)**
26. **Which statuses count as “work started”:** Is `READY_FOR_PICKUP` considered started for lock rules? **(Clarify)**
27. **“Team” definition:** Is team represented by `assignedMechanics[]` only or separate team entity? **(Clarify)**

### G) Audit visibility
28. **Substitute audit trail API:** Is there an API to fetch `SubstituteAudit` entries for display? If not, should UI show only created/updated metadata? **(Clarify)**
29. **Assignment/override audit source:** Should UI display generic work order transition history, a specific assignment sync log, or both? **(Clarify)**
30. **Event failure handling:** Should orphaned/invalid events be stored in DLQ outside Moqui, in Moqui DB for review, or both? **(Blocking)**

### H) Timezone and identifiers
31. **Timezone source:** Should timestamps display in shop/location timezone or user preference timezone? How does frontend obtain location timezone? **(Blocking for correct display)**
32. **ID types:** Confirm identifier types (uuid vs numeric vs prefixed strings) for `locationId`, `resourceId`, `mechanicId`, `partId`, etc., and whether UI should use searchable pickers. **(Clarify)**

---

# End of workexec AGENT_GUIDE.md
```
