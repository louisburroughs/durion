# AGENT_GUIDE.md

## Summary

This document is the normative guide for the `workexec` (Work Execution) domain. It defines ownership boundaries, canonical concepts, and the required invariants for work order execution, estimate flows, substitutions, dispatch board views, and event-driven appointment updates. It resolves all previously captured open questions and reconciles todos into explicit decisions or tracked tasks.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | SubstituteLink ownership boundary |
| DECISION-INVENTORY-002 | Canonical Moqui screens/routes |
| DECISION-INVENTORY-003 | Identifier handling (opaque IDs) |
| DECISION-INVENTORY-004 | Work order status taxonomy and “work started” |
| DECISION-INVENTORY-005 | Assignment vs operational context (SoR + audit) |
| DECISION-INVENTORY-006 | SubstituteLink update semantics and defaults |
| DECISION-INVENTORY-007 | Substitution picker scope + eligibility source |
| DECISION-INVENTORY-008 | Part lookup UX contract |
| DECISION-INVENTORY-009 | Dispatch board contract + aggregation behavior |
| DECISION-INVENTORY-010 | Appointments vs work orders (SoR + link) |
| DECISION-INVENTORY-011 | Standard error envelope + duplicate signaling |
| DECISION-INVENTORY-012 | Idempotency-Key usage for UI mutations |
| DECISION-INVENTORY-013 | Capability/permission signaling + manual price gating |
| DECISION-INVENTORY-014 | Audit visibility strategy (substitutes + overrides) |
| DECISION-INVENTORY-015 | Event ingestion mechanism + failure handling |
| DECISION-INVENTORY-016 | Timezone semantics for shop UX |

## Domain Boundaries

### What Work Execution owns (system of record)

- Work order execution lifecycle state (`durion.workexec.DurWorkOrder.statusId` in Moqui `durion-workexec`)
- Execution-time edits that are owned by work execution (assignment-like fields on the work order, execution notes, completion/invoicing transitions)
- Estimate editing/approval flows implemented in `durion-workexec` screens/services
- Runtime substitution apply behavior for estimate/work order line items (including immutable substitution history)
- Execution-facing read models owned by work execution when derived from workexec-owned data
- **Approval capture UX and approval recording for workexec-owned subjects (Estimates and Work Orders)**:
  - Workexec owns the *subject lifecycle* transitions that depend on approvals (e.g., Estimate becomes `APPROVED`, Work Order line items become `APPROVED/DECLINED`).
  - Workexec owns the *auditability requirements* for approvals attached to workexec subjects (immutability, non-repudiation, traceability).
  - **CLARIFY:** Whether there is a separate cross-domain “Approvals” service that is SoR for approval artifacts (approval records, expiration) while workexec remains SoR for subject state transitions. Until confirmed, treat approvals for Estimates/Work Orders as workexec-owned workflow behavior and enforce via workexec endpoints.

- **Estimate snapshot artifacts used for customer review and billing finalization**:
  - Workexec owns snapshot creation triggers and the linkage from Estimate/WorkOrder to snapshot IDs.
  - Snapshot records must be immutable/append-only once created.

### What Work Execution does *not* own

- Part/product master data and substitute rule authoring (inventory/product domains)
- Inventory availability/on-hand/reservations (inventory domain)
- Appointment authoring/scheduling and operational schedule truth (shop management domain)
- People directory and availability (people domain)
- Permission policy definitions (security domain; workexec enforces only)
- **Sales order/cart lifecycle** (POS order domain / billing domain / pricing domain): stories describing cart creation and sales order lines are **domain-conflict** and must be relabeled (see pitfalls + open questions).
- **Timekeeping / work sessions / travel segments**: People domain is SoR; workexec may provide entry points or read-only signals only (see DECISION-INVENTORY-009 and related story questions).

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| `durion.workexec.DurWorkOrder` | Work order execution entity; includes `statusId`, `appointmentId`, `mechanicId`, `bayId` (as implemented today). |
| Estimate | Quote/estimate concept; edited and approved via `durion-workexec` screens/services. |
| SubstituteLink | Relationship between `partId` and `substitutePartId` with type/priority/active flags (master-data adjacency). |
| Substitution history | Append-only record created when a substitute is applied to a work order/estimate line. |
| Appointment (`durion.shopmgr.DurShopAppointment`) | Shop scheduling entity; work orders may reference via `appointmentId`. |
| Dispatch board view | Read-only location/date view of work orders and scheduling signals; partial rendering permitted. |
| **Approval (workexec subject approval)** | Immutable record of an approval decision/proof for an Estimate or Work Order (header-level approval and/or per-line partial approval). **CLARIFY:** entity name and owning service. |
| **Estimate Version / Revision** | Versioned estimate model where prior versions are immutable and a new active version is created on “Revise”. **CLARIFY:** entity names and linkage fields (`activeVersionId`, `priorVersionId`, etc.). |
| **Estimate Summary Snapshot** | Immutable snapshot used for customer-facing review/print/share. Must be generated from a Draft estimate and rendered without client-side recalculation. **CLARIFY:** entity name and storage location. |
| **Billable Scope Snapshot** | Immutable snapshot created at work completion/finalization for billing; used to detect variance and gate invoicing. **CLARIFY:** entity name and status/flag representation. |
| **Work Order Part Usage Event** | Append-only event representing ISSUE/CONSUME/RETURN (or combined model). **CLARIFY:** authoritative event model and invariants. |

### Relationships (actionable mental model)

- `DurWorkOrder` → optional `appointmentId` → `DurShopAppointment` (shopmgr SoR)
- Estimate ↔ optional `appointmentId` (link direction **CLARIFY**; see open questions)
- Estimate → many EstimateItems (PART/LABOR/SERVICE/etc.; exact type schema **CLARIFY**)
- Work Order → many WorkOrderLineItems (services/parts; identifiers and collections **CLARIFY**)
- Estimate → many EstimateVersions (if versioned) with one active version
- Estimate → many EstimateSummarySnapshots (append-only)
- Work Order → many BillableScopeSnapshots (append-only) with optional active snapshot pointer/flag
- Estimate/WorkOrder → many Approvals (append-only); approvals may expire; subject state transitions depend on approval outcomes

## Invariants / Business Rules

- Work order state transitions are authoritative and audited.
- “Work started” gating must be based on authoritative status signals and must prevent mid-work assignment drift.
- Substitution apply must be eligibility-checked server-side and must produce immutable history.
- Mutation operations must be safe for double-submit (idempotency) and stale edits (conflict).
- Dispatch board is read-only in v1; partial failures (People availability) must not block core view.
- **Approvals are immutable and non-repudiable once recorded**:
  - Approval artifacts (signature image, proof references) must not be editable after success.
  - Approval submission must be idempotent (Idempotency-Key required) and concurrency-safe (409 on stale/invalid state).
  - UI must not mark approved locally without a successful server response + refresh.
- **Estimate editability is status-gated**:
  - Draft-only edits for adding/modifying parts and labor/service lines.
  - Revision creates a new editable version; prior versions are read-only.
- **Snapshot artifacts are append-only**:
  - Customer summary snapshots and billable scope snapshots must be immutable once created.
  - UI must render snapshot totals exactly as returned; do not recompute totals client-side.
- **Visibility and pricing controls are capability-driven**:
  - Manual price/rate entry and sensitive financial fields must be gated by explicit capability flags and enforced server-side with 403.

## Integration Patterns & Events (Normative)

> This section is additive guidance derived from consolidated stories and existing decisions. It does not introduce new Decision IDs; it operationalizes DECISION-INVENTORY-015 and related invariants.

### Event ingestion (inbound)

- Use inbox pattern (DECISION-INVENTORY-015):
  - Persist inbound event first (inbox table) with a unique `sourceEventId` and `sourceSystem`.
  - Process asynchronously with retries and DB-level idempotency on `sourceEventId`.
  - On failure: persist failure record for ops review and emit to external DLQ.
- **Security**:
  - Ingestion endpoints must be protected by service-to-service auth (mTLS or signed JWT). **CLARIFY** which is standard in this repo.
  - Ops screens for inbox/failures must be permission-gated (see Security section).

### Event types (workexec relevant)

- Appointment updates: workexec may ingest events that update appointment execution-facing status/timeline (shopmgr SoR for appointment entity; workexec must not overwrite scheduling status fields).
  - **CLARIFY/TODO:** confirm which appointment field is safe to update for execution status (`executionStatusId` vs `statusId`).
- Billing/invoice signals:
  - Treat invoice issuance as a work order status transition to `WO_INVOICED` unless a dedicated billing event exists (DECISION-INVENTORY-015).

### Outbound events (recommended patterns)

- When workexec records:
  - approval recorded (estimate/work order),
  - estimate revised,
  - snapshot generated,
  - parts usage recorded,
  - billable scope finalized,
  
  it should emit domain events for downstream consumers (billing, audit, reporting). **CLARIFY:** event bus/transport and schema governance.

## API Contracts & Patterns (Normative)

### Base path and routing

- **CLARIFY/TODO (blocking across multiple stories):** canonical REST base path(s) used by Moqui frontend integration layer:
  - Stories reference `/api/...`, `/api/v1/...`, and `/rest/api/v1/...`.
  - Until confirmed, do not hardcode paths in new UI; align to existing Moqui screen/service calls or the established REST prefix in this repo.

### Standard error envelope

- Use DECISION-INVENTORY-011 envelope for all non-2xx responses:
  - `code`, `message`, `correlationId`, optional `fieldErrors[]`, optional `existingResourceId`.
- Frontend must:
  - display a user-safe message,
  - surface `correlationId` in a support-only details affordance,
  - map `fieldErrors[]` to inline validation where applicable.

### Idempotency

- UI must send `Idempotency-Key` for create/submit operations (DECISION-INVENTORY-012):
  - Generate UUID per user attempt.
  - Reuse the same key on retry for the same attempt (network timeout, retry button).
  - Do not construct semantic keys from business identifiers.

### Concurrency / optimistic locking

- Prefer explicit version tokens (ETag/`version`/`lastUpdatedStamp`) on mutable resources:
  - Require token on updates that can lose data (estimate item quantity changes, price overrides, revise/save, partial approvals).
  - Return 409 on conflict.
- **CLARIFY/TODO:** confirm which token exists in Moqui payloads and whether it is required.

### DTO patterns (recommended)

- Prefer “server-authoritative” DTOs that include:
  - `isEditable` / `isActionable` booleans to avoid client-side status mapping tables.
  - capability flags relevant to the view (or a stable user-context endpoint).
  - totals and computed pricing fields returned by the server (avoid client recomputation).

## Security / Authorization Requirements (Normative)

- Backend is authoritative; UI gating is defense-in-depth (DECISION-INVENTORY-013).
- Require explicit permissions for sensitive views/actions:
  - Dispatch board view requires explicit permission (e.g., `WORKEXEC_DISPATCH_VIEW`) + location membership (DECISION-INVENTORY-013).
  - Approval recording requires explicit permission/capability (names **CLARIFY**).
  - Manual price/rate entry requires explicit permission/capability (e.g., `ENTER_MANUAL_PRICE`) (DECISION-INVENTORY-013).
  - Viewing internal cost/margin fields requires explicit permission/capability (names **CLARIFY**; see role-based visibility story).
  - Ops inbox/failure screens for event ingestion require explicit ops permission (names **CLARIFY**).

### Sensitive data handling

- Treat signature images and approval proof payloads as sensitive:
  - Do not log raw Base64 signature images in client or server logs.
  - Prefer storing proof as a reference/token where possible; if storing payload is required, encrypt at rest and restrict access. **CLARIFY:** storage policy for payload JSON vs hash-only (see ingestion open questions).
- Do not accept client-supplied actor IDs for audit; derive actor from auth context (DECISION-INVENTORY-005 principle).

## Observability Guidance (Actionable)

### Correlation and tracing

- Propagate `correlationId` from error envelope into:
  - frontend error UI details,
  - frontend logs (support-level),
  - server logs for the request.
- **CLARIFY/TODO:** whether `correlationId` is also provided via headers; if so, standardize extraction order (header first, then body).

### Metrics (minimum set)

Track these per endpoint/service (tagged by `locationId` where applicable, but avoid PII):

- `http_server_requests_total{route,method,status}`
- `http_server_request_duration_seconds_bucket{route,method}`
- `idempotency_replay_total{route}` (count of requests served from idempotency store)
- `conflict_total{route,code}` (409s, broken down by `code`)
- Approval flows:
  - `approval_submit_total{subjectType,result}` (success/validation/forbidden/conflict)
  - `approval_expired_total{subjectType}` (if expiration is modeled)
- Estimate editing:
  - `estimate_item_mutation_total{type=part|labor,action=add|update|override,result}`
  - `estimate_revision_total{result}`
  - `estimate_snapshot_generate_total{result,policyMode}`
- Event ingestion:
  - `inbox_received_total{eventType}`
  - `inbox_processed_total{eventType,result}`
  - `inbox_dlq_total{eventType}`
  - `inbox_processing_lag_seconds` (now - receivedAt)

### Logging (rules)

- Log at INFO:
  - action name, subject IDs (opaque), idempotency key (if policy allows; otherwise hash it), correlationId, result code.
- Log at WARN/ERROR:
  - correlationId, error `code`, and safe context (no signature payloads, no customer PII).
- Redaction:
  - signature images, approval tokens/links, and any customer PII must be redacted.

## Testing Strategies (Actionable)

### Contract tests (backend + frontend integration)

- Standard error envelope parsing:
  - 400 with `fieldErrors[]`
  - 403 forbidden
  - 404 not found
  - 409 conflict with `code` and optional `existingResourceId`
- Idempotency:
  - same `Idempotency-Key` replay returns same outcome; no duplicates created.
- Concurrency:
  - stale version token returns 409; UI refresh path works.

### Domain-level tests (backend)

- Work order status gating:
  - assignment edits rejected after started (DECISION-INVENTORY-004/005).
- Approval invariants:
  - approval cannot be recorded when subject not eligible (409).
  - approval record is immutable after creation.
  - partial approval is atomic: either all line decisions persist or none.
- Estimate invariants:
  - only DRAFT editable; non-DRAFT mutations rejected.
  - revise creates new version; prior version immutable; approvals invalidated as required.
- Snapshot invariants:
  - snapshot is append-only; totals match server calculation at snapshot time.

### UI tests (frontend)

- Approval capture:
  - submit disabled until signature present
  - 409 conflict triggers refresh and exits if no longer eligible
  - no logging of signature payload
- Estimate add parts/labor:
  - quantity validation (>0, decimal allowed)
  - capability-gated override UI
  - totals refresh from backend response
- Dispatch board:
  - partial rendering when People availability fails
  - timezone labeling behavior (DECISION-INVENTORY-016)

## Common Pitfalls & Gotchas

- **Path confusion (`/api` vs `/rest/api/v1`)**: multiple stories show inconsistent base paths. Do not implement new endpoints in UI until canonical routing is confirmed. Prefer existing Moqui screen/service calls where possible. (TODO: resolve canonical base path.)
- **Client-side status mapping drift**: avoid hardcoding status enums for eligibility; prefer backend-provided booleans like `isStarted`, `isApprovalEligible`, `isRevisable`, `isEditableForApproval`.
- **Double-submit without idempotency**: all submit-like actions must send `Idempotency-Key` and disable UI while in-flight.
- **Logging sensitive payloads**: signature images, approval tokens/links, and any proof payload must never be logged.
- **Ambiguous field omission**: if backend omits sensitive fields, omission may mean “not authorized” or “not applicable”. Prefer explicit capability flags (DECISION-INVENTORY-013) and/or explicit `visibility` metadata.
- **Cross-domain ownership drift**:
  - Appointment reschedule/cancel belongs to shopmgr SoR (DECISION-INVENTORY-010).
  - Timekeeping/work sessions belong to People SoR (DECISION-INVENTORY-009).
  - Sales order/cart belongs to a POS order/billing domain (domain-conflict).
- **Totals recomputation in UI**: for snapshots and server-priced items, do not recompute totals client-side; display server totals only.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | SubstituteLink authoring is not workexec SoR | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-002 | Use existing `durion-workexec` and `durion-shopmgr` screens | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-003 | IDs are opaque strings | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-004 | Started means `WO_IN_PROGRESS` or later | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-005 | Operational context is shopmgr SoR; overrides audited | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-006 | SubstituteLink keys immutable; soft deactivate | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-007 | Picker supports WO + Estimate; backend enforces eligibility | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-008 | Part selection via search/picker | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-009 | Board is read-only; aggregate where feasible | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-010 | Appointments are separate shopmgr entities | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-011 | Standard error envelope with correlationId | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-012 | UI sends `Idempotency-Key` for create/submit | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-013 | Capability signal drives UI gating; backend authoritative | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-014 | Audit metadata by default; optional audit endpoints | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-015 | Inbox + async processing; DLQ + ops view | See DOMAIN_NOTES.md |
| DECISION-INVENTORY-016 | Display user TZ; bucket by shop TZ | See DOMAIN_NOTES.md |

## Open Questions (from source)

### Q: Substitutes domain ownership: Story #109 is labeled `domain:workexec` but is product/parts admin flavored. Should this be `domain:inventory` or a product domain label?

- Answer: SubstituteLink authoring/admin is not owned by workexec; it should be owned by inventory/product master-data domains. Workexec consumes SubstituteLink for runtime substitution and records substitution history.
- Assumptions:
- SubstituteLink is reused beyond work order execution.
- Rationale:
- Keeps SoR boundaries clean.
- Impact:
- Relabel story; treat SubstituteLink as a workexec dependency.
- Decision ID: DECISION-INVENTORY-001

### Q: Operational context story ownership: Frontend issue labels “Shop Management/user” but backend reference is `domain:workexec` with Shopmgr as SoR. Confirm ownership/label

- Answer: Operational context is shopmgr SoR; workexec owns the execution UI that displays it and gates edits by status/permission.
- Assumptions:
- Scheduling/bay constraints are authored in shopmgr.
- Rationale:
- One authoritative scheduling owner.
- Impact:
- Coordinate contracts across workexec and shopmgr.
- Decision ID: DECISION-INVENTORY-005

### Q: Timekeeping / people-adjacent features: Several stories label domain conflicts (user/shop management vs workexec). Confirm what belongs in workexec UI vs people/shopmgr UI

- Answer: Time entry remains in people domain; workexec may consume People availability only as a read-only signal for dispatch views.
- Assumptions:
- People is the SoR for availability/timekeeping.
- Rationale:
- Prevents competing SoR.
- Impact:
- Move any time-entry CRUD story to `domain:people`.
- Decision ID: DECISION-INVENTORY-009

### Q: What are the canonical Moqui screen paths/routes for work order detail, estimate detail, appointment detail, reporting/dispatch screens?

- Answer: Use existing Moqui screens under `durion-moqui-frontend` (workexec: `WorkOrderBoard.xml`, `WorkOrderEdit.xml`, `EstimateEdit.xml`; shopmgr: `AppointmentEdit.xml`).
- Assumptions:
- Moqui routing exposes these screens via component menus.
- Rationale:
- Aligns stories with implemented artifacts.
- Impact:
- Reference these screens in story AC and navigation.
- Decision ID: DECISION-INVENTORY-002

### Q: Part lookup UX: What endpoint/screen should admin UI use to search/select parts by SKU/name? Provide route(s) + response shape

- Answer: Use a searchable part/product picker (query + pagination) instead of manual ID entry.
- Assumptions:
- Product master records are queryable by SKU/name.
- Rationale:
- Prevents invalid IDs.
- Impact:
- Add/reuse a “Find Part/Product” UI and supporting service.
- Decision ID: DECISION-INVENTORY-008

### Q: SubstituteLink list/search: Do we have `GET /api/v1/substitutes` with filters/pagination, or must list be “query by partId” only?

- Answer: Default is “query by partId”; a global list is allowed only if paginated and filter-required.
- Assumptions:
- Dataset can be large.
- Rationale:
- Keeps performance predictable.
- Impact:
- Admin UI starts with selecting a part, then lists substitutes.
- Decision ID: DECISION-INVENTORY-006

### Q: Substitution picker endpoints: Exact endpoints and payload schemas for fetching candidates (WO/Estimate) and applying a selected substitute

- Answer: Provide a stable picker contract for both WorkOrder and Estimate lines returning eligibility + pricing + permission flags; apply returns enough data to refresh the affected line.
- Assumptions:
- Backend exposes a dedicated picker DTO.
- Rationale:
- Runtime substitution requires context beyond SubstituteLink.
- Impact:
- Define and version a picker DTO schema.
- Decision ID: DECISION-INVENTORY-007

### Q: Dispatch Board endpoint contract: Confirm exact endpoint path and request/response schema for `DispatchBoardView` and `ExceptionIndicator`

- Answer: In Moqui today, dispatch view is implemented by `WorkOrderBoard.xml` over `durion.workexec.DurWorkOrder`. If a REST read model is introduced, it must be read-only, filter-required, and return stable exception codes.
- Assumptions:
- Board v1 is read-only.
- Rationale:
- Enables progressive enhancement.
- Impact:
- Contract must define `asOf` and exception enums.
- Decision ID: DECISION-INVENTORY-009

### Q: Aggregation responsibility: Does dispatch board already include mechanic availability and bay occupancy, or must frontend call People availability separately?

- Answer: Prefer server-side aggregation for SLA; if not available, UI fetches People availability in parallel and renders board even on People failure.
- Assumptions:
- People availability is secondary.
- Rationale:
- Partial outages must not block dispatch.
- Impact:
- UI implements partial rendering + warning state.
- Decision ID: DECISION-INVENTORY-009

### Q: Appointments vs Work Orders: Are “appointments” separate entities or represented as work orders with scheduled times? If separate, what endpoint supplies them?

- Answer: Appointments are separate shopmgr entities; work orders reference via `appointmentId` and appointment details are supplied by shopmgr.
- Assumptions:
- Scheduling remains shopmgr-owned.
- Rationale:
- Single SoR for scheduling.
- Impact:
- Any board join is via `appointmentId`.
- Decision ID: DECISION-INVENTORY-010

### Q: Assignment context endpoints: Exact endpoints/services for loading work order detail, updating assignment context, and fetching audit/history

- Answer: In Moqui today, assignment-like fields live on `DurWorkOrder` and are updated by existing workexec services; add an append-only audit entity/service if stories require visible history.
- Assumptions:
- Existing update service remains the primary mutation path.
- Rationale:
- Avoid over-design.
- Impact:
- Implement audit trail only when required.
- Decision ID: DECISION-INVENTORY-014

### Q: Operational context override contract: Does override require `version`? What field name? What response shape and success status (200 vs 201)?

- Answer: Overrides must be manager-only, audited, and concurrency-safe. If a version token exists, require it and return 409 on conflicts; prefer 200 with updated context.
- Assumptions:
- shopmgr can expose a version token.
- Rationale:
- Prevent lost updates.
- Impact:
- safe_to_defer: true until shopmgr provides a version token.
- Decision ID: DECISION-INVENTORY-005

### Q: Event ingestion mechanism: How are Workexec events delivered/handled in this Moqui repo (webhook, broker consumer, polling/inbox)?

- Answer: Use an inbox pattern (persist first, process async, DB idempotency). Transport may be webhook or broker.
- Assumptions:
- Delivery is at-least-once.
- Rationale:
- Durable processing with retries.
- Impact:
- Requires inbox entity + ops failure view + alerts.
- Decision ID: DECISION-INVENTORY-015

### Q: Invoice event semantics: Is `InvoiceIssued` a separate event type or a status within `WorkorderStatusChanged`? What fields are present?

- Answer: In Moqui today, invoicing transitions the work order to `WO_INVOICED`; treat invoice issuance as a status transition unless a dedicated billing event contract exists.
- Assumptions:
- Billing may later emit `InvoiceIssued`.
- Rationale:
- Use the simplest consistent signal now.
- Impact:
- Avoid double-applying if both signals exist.
- Decision ID: DECISION-INVENTORY-015

### Q: Standard error envelope: For 400/409, what is the standard error response format (field errors, message, correlationId, existingResourceId on duplicates)?

- Answer: Use a stable JSON envelope with `code`, `message`, `correlationId`, optional `fieldErrors[]`, and optional `existingResourceId` on duplicates.
- Assumptions:
- UI requires stable parsing.
- Rationale:
- Consistent UX.
- Impact:
- Normalize Moqui and backend errors to this envelope.
- Decision ID: DECISION-INVENTORY-011

### Q: Idempotency-Key usage: Should frontend generate/send `Idempotency-Key` for create calls by default?

- Answer: Yes—UI sends `Idempotency-Key` for create/submit operations and reuses it on retry for the same attempt.
- Assumptions:
- Double-submit and retries occur.
- Rationale:
- Prevent duplicates.
- Impact:
- Backend stores idempotency outcomes.
- Decision ID: DECISION-INVENTORY-012

### Q: Duplicate signaling: For duplicates (e.g., SubstituteLink), does backend return existing resource id? If yes, where?

- Answer: Yes—return 409 and include `existingResourceId` in the standard error envelope.
- Assumptions:
- Duplicate create is common.
- Rationale:
- Enables UI to navigate to existing.
- Impact:
- Standardize across create endpoints.
- Decision ID: DECISION-INVENTORY-011

### Q: Permission signal source: How does frontend determine permission scopes/capabilities (session claims, user context endpoint, embedded in payload)?

- Answer: Prefer a user context/capabilities endpoint (or session claims) returning stable capability flags; backend remains authoritative with 403.
- Assumptions:
- UI should avoid hardcoding roles.
- Rationale:
- Reduce coupling.
- Impact:
- Define capability schema and map to UI actions.
- Decision ID: DECISION-INVENTORY-013

### Q: Manual price override permission: How does backend indicate user has `ENTER_MANUAL_PRICE` and what is expected frontend behavior when pricing is unavailable?

- Answer: Picker response includes `canEnterManualPrice`; if false and a candidate lacks price, UI blocks apply and shows guidance. Backend also enforces.
- Assumptions:
- Some candidates may lack computed price.
- Rationale:
- Prevent accidental free work.
- Impact:
- Picker DTO includes pricing + flag.
- Decision ID: DECISION-INVENTORY-013

### Q: Dispatch Board RBAC: Which roles/permissions may view Dispatch Board? Location membership only or explicit permission (e.g., `DISPATCH_VIEW`)?

- Answer: Require an explicit permission (e.g., `WORKEXEC_DISPATCH_VIEW`) and enforce location membership constraints.
- Assumptions:
- Board data is sensitive.
- Rationale:
- Least privilege.
- Impact:
- Apply permission to screens/endpoints.
- Decision ID: DECISION-INVENTORY-013

### Q: Editability of SubstituteLink key fields: On update, are `partId` and `substitutePartId` immutable or editable? If editable, how handle uniqueness conflicts?

- Answer: Immutable. Changing either requires create-new + deactivate-old.
- Assumptions:
- Key edits complicate uniqueness.
- Rationale:
- Simplify audit and conflict handling.
- Impact:
- UI disables key fields on edit.
- Decision ID: DECISION-INVENTORY-006

### Q: Defaults alignment: Does backend default `priority=100` and `isAutoSuggest=false` when omitted?

- Answer: Yes—those are the required defaults; UI should still send explicit values.
- Assumptions:
- Defaults represent “normal” behavior.
- Rationale:
- Reduce ambiguity.
- Impact:
- Responses include defaulted values.
- Decision ID: DECISION-INVENTORY-006

### Q: Candidate inclusion rules: Should candidate list include only available candidates or both available/unavailable with statuses?

- Answer: Default is only eligible/available candidates; optionally include unavailable with `availabilityStatus` + reason when explicitly requested.
- Assumptions:
- Large lists harm UX.
- Rationale:
- Keep picker focused.
- Impact:
- Support `includeUnavailable=true`.
- Decision ID: DECISION-INVENTORY-007

### Q: Eligibility source for substitution: How does frontend determine “original is unavailable”? Is there a line-level field or should backend enforce?

- Answer: Backend is authoritative; UI may display indicators but must not enforce eligibility solely client-side.
- Assumptions:
- Availability can change rapidly.
- Rationale:
- Avoid stale gating.
- Impact:
- Picker response includes eligibility fields.
- Decision ID: DECISION-INVENTORY-007

### Q: Override-after-start rule: After work starts, are overrides disallowed, manager-only, or versioned without mutating locked snapshot?

- Answer: Manager-only overrides are allowed after start with explicit audit records and without mutating a “start snapshot”.
- Assumptions:
- Emergencies require controlled changes.
- Rationale:
- Preserve audit integrity.
- Impact:
- Requires audit trail and snapshot discipline.
- Decision ID: DECISION-INVENTORY-005

### Q: Which statuses count as “work started”: Is `READY_FOR_PICKUP` considered started for lock rules?

- Answer: Yes—any in-progress-or-later status is considered started; `READY_FOR_PICKUP` is post-start.
- Assumptions:
- Other services may use different taxonomies.
- Rationale:
- Prevent late-stage reassignment drift.
- Impact:
- Prefer backend-provided `isStarted` boolean.
- Decision ID: DECISION-INVENTORY-004

### Q: “Team” definition: Is team represented by `assignedMechanics[]` only or separate team entity?

- Answer: Team is represented as assigned mechanic(s) on the work order; no separate team entity is required for v1.
- Assumptions:
- Most work orders have one primary mechanic.
- Rationale:
- Keep model lightweight.
- Impact:
- If multi-mechanic assignment is added, represent as a list.
- Decision ID: DECISION-INVENTORY-005

### Q: Substitute audit trail API: Is there an API to fetch `SubstituteAudit` entries for display? If not, should UI show only created/updated metadata?

- Answer: Minimum requirement is created/updated metadata; an audit endpoint is optional and must be append-only if implemented.
- Assumptions:
- Operators mainly need “who/when”.
- Rationale:
- Avoid expensive reads unless required.
- Impact:
- Add `/substitutes/{id}/audit` only if demanded.
- Decision ID: DECISION-INVENTORY-014

### Q: Assignment/override audit source: Should UI display generic work order transition history, a specific assignment sync log, or both?

- Answer: Prefer both when available; otherwise show transitions + metadata until a dedicated audit log exists.
- Assumptions:
- Lifecycle transitions and assignment edits are distinct.
- Rationale:
- Improves audit clarity.
- Impact:
- Add dedicated audit log if required by stories.
- Decision ID: DECISION-INVENTORY-014

### Q: Event failure handling: Should orphaned/invalid events be stored in DLQ outside Moqui, in Moqui DB for review, or both?

- Answer: Both—persist a failure record in Moqui DB for ops review and emit to an external DLQ for alerting/remediation.
- Assumptions:
- Some failures require manual intervention.
- Rationale:
- Supports ops workflows.
- Impact:
- Implement durable failure entity + ops screen.
- Decision ID: DECISION-INVENTORY-015

### Q: Timezone source: Should timestamps display in shop/location timezone or user preference timezone? How does frontend obtain location timezone?

- Answer: Display timestamps in user timezone; interpret date-bucket filters (dispatch day) in shop timezone when available and label timezone in UI. If shop timezone is not available in location entities, treat as safe_to_defer and use user timezone with explicit labeling.
- Assumptions:
- Shop timezone may be configured outside current entity models.
- Rationale:
- Avoid silent timezone errors.
- Impact:
- Add timezone source to location/facility model or shop configuration.
- Decision ID: DECISION-INVENTORY-016

### Q: ID types: Confirm identifier types (uuid vs numeric vs prefixed strings) for `locationId`, `resourceId`, `mechanicId`, `partId`, etc., and whether UI should use searchable pickers

- Answer: Treat all IDs as opaque strings (`type="id"`) and use pickers/search rather than UUID/numeric assumptions.
- Assumptions:
- IDs may be generated by different systems.
- Rationale:
- Avoid invalid client-side validation.
- Impact:
- UI controls must not assume UUID.
- Decision ID: DECISION-INVENTORY-003

## Open Questions from Frontend Stories

> This section consolidates *new* open questions from the consolidated frontend stories. Items are phrased to be answerable by backend/domain owners and to unblock implementation. Use **TODO**/**CLARIFY** markers where the owning team must respond.

### Approvals (Estimate + Work Order)

1. **Canonical API paths (blocking):** What are the definitive endpoints used by this Moqui frontend deployment for:
   - Estimate detail GET
   - Work order detail GET
   - Estimate approval POST
   - Work order approval POST  
   Stories conflict across `/api/v1/...`, `/api/...`, `/rest/api/v1/...`. **TODO:** publish canonical base path + route list.

2. **Eligibility status mapping (blocking):** What exact `statusId` values indicate:
   - Estimate “awaiting customer approval” (eligible) vs approved/ineligible
   - Work Order “awaiting customer approval” (eligible) vs approved/ineligible  
   **CLARIFY:** CUSTOMER_APPROVAL_WORKFLOW.md lists `DRAFT/APPROVED/DECLINED/EXPIRED` but stories require “awaiting approval” semantics; Work Order FSM includes `AWAITING_APPROVAL`.

3. **Approval payload schema (blocking):** What is the required JSON schema for approval submission:
   - required fields for `approvalPayload`
   - whether a document digest/version is required to prevent approving stale totals
   - whether approval is header-level only or can be per-line for work orders

4. **Signature strokes requirement (blocking):** Are `signatureStrokes` required? If yes, specify:
   - coordinate system (pixels vs normalized)
   - timestamp semantics (`t` units and origin)
   - grouping (single array vs array-of-strokes)

5. **Signer identity (blocking):** Is signer identity required in payload?
   - customer identifier required vs derived server-side
   - if required: field name and source in POS (customer selection vs implicit)

6. **Approval response shape (needed for UX):** On success, does approval POST return:
   - `approvalId`, `approvedAt`, method, proof reference
   - updated subject state
   - or must UI refresh subject to obtain approval metadata?

7. **Partial approval contract (blocking):** For Work Order partial approval:
   - exact endpoint path(s)
   - request schema for `lineDecisions[]`, method/proof, and optional `version`
   - response schema (updated line items + totals + status)

8. **Line item identifiers (blocking):** For Work Order approval decisions:
   - are services and parts separate collections?
   - what IDs must be echoed back (`workOrderServiceId` vs `workOrderPartId` vs unified lineId)?

9. **Approval methods/proof schema (blocking):** What approval methods are supported now and what proof is required for each?
   - signature payload vs signature token/file reference vs note-only
   - whether “unsupported method” can occur and how it is represented

10. **All-declined terminal mapping (blocking):** When all Work Order items are declined, what Work Order `statusId` is set (e.g., `CANCELLED` or another)? UI must not guess.

11. **Approval window semantics (blocking if required):** Is there an approval window?

- does backend provide `approvalWindowEnd`?
- is expiration enforced only server-side?
- what error `code` indicates “window expired”?

### Approval Expiration (Approvals Inbox/Detail)

1. **Domain ownership confirmation (blocking):** Is there a workflow/approval service separate from workexec, or is workexec the owning service for approvals? Provide correct `domain:*` label and owning component.

2. **Approvals inbox/detail endpoints (blocking):** Provide endpoints/services and schemas for:

- list approvals (pagination + filters)
- load approval detail
- approve
- deny  
   Include error envelope confirmation (must match DECISION-INVENTORY-011 or provide mapping rules).

1. **Approval state model (blocking):** Provide authoritative approval status enum and which are actionable vs terminal.

2. **Expiration representation (blocking):** Does backend expose:

- `status=EXPIRED`, and/or `isExpired`, and/or `expiresAt`?
   Confirm UI must rely on backend status/flag (preferred) vs computing from timestamps.

1. **Timezone/display standard (blocking):** Which timezone governs display of `expiresAt` and what formatting is standard in this app?

2. **Deny reason requirements (blocking):** Is deny reason required? If yes, provide validation rules and whether requirement is dynamic (`denyReasonRequired`).

3. **Post-expiration path (non-blocking):** Should UI offer “Request new approval”? If yes, what route/service and owning domain?

### Estimate Editing (Parts + Labor/Service)

1. **Moqui/REST contract mapping (blocking):** What are the exact Moqui services and/or REST endpoints for:

- loading estimate header + items + totals
- searching catalog parts/products (query + pagination)
- searching service catalog (query + pagination)
- creating catalog part items
- creating non-catalog part items
- adding catalog labor/service lines
- adding custom labor lines (if supported)
- updating quantity
- applying/removing price overrides

1. **Capability/policy source (blocking):** Where does UI obtain capability/policy flags:

- `canOverridePrice`
- `allowNonCatalogParts`
- `requireOverrideReason`
- `allowCustomLabor` (or equivalent)  
   **CLARIFY:** user-context endpoint vs embedded in estimate payload vs location config.

1. **Override reason codes (blocking if required):** What endpoint provides valid override reason codes and are they location-scoped?

2. **Concurrency token (blocking):** Do estimate/items expose `version`/`lastUpdatedStamp`/ETag that must be echoed on mutation? Provide field/header name and 409 behavior.

3. **Flat-rate editability (blocking):** For flat-rate services, is `laborUnits` editable or locked? Provide validation rules.

4. **Manual rate/price entry (blocking):** If backend cannot determine labor rate:

- is manual rate entry allowed?
- what capability flag enables it?
- what fields are required (rate, reason/note)?
- how is it audited and displayed?

1. **Tax code handling (blocking):** Is tax code always backend-derived or user-selectable? If selectable, provide allowed values and defaults.

### Estimate Revision / Versioning

1. **Versioning endpoints (blocking):** Provide endpoints/services and schemas for:

- create revision/version
- list versions
- load specific version
- save edits to active version

1. **Revision eligibility (blocking):** Confirm revise is allowed for `APPROVED` and `DECLINED` and whether revise is distinct from `reopenEstimate()`.

2. **Line item type schema (blocking):** Provide authoritative estimate line item types and required fields per type (PART/LABOR/FEE/etc.).

3. **Currency source (blocking if not in payload):** If totals do not include currency code, what is authoritative shop/location currency source?

### Estimate Customer Summary Snapshot

1. **Snapshot generation contract (blocking):** Provide endpoint/service and schema for:

- generate snapshot (idempotent)
- fetch snapshot by `snapshotId`
- whether response includes renderable HTML/PDF URL vs structured JSON

1. **Legal terms policy (blocking):** Provide:

- how terms are sourced (location/customer/default)
- policy modes (`FAIL` vs `USE_DEFAULTS`)
- error codes for missing terms

1. **Visibility enforcement (blocking):** Confirm whether backend returns a customer-facing DTO with restricted fields omitted (preferred) and/or explicit `visibility` flags.

2. **Estimate “pending approval” status (blocking):** If there is a submit-for-approval step, what is the status enum and transition? CUSTOMER_APPROVAL_WORKFLOW.md does not list `PENDING_APPROVAL`.

3. **Approval token/link sensitivity (blocking if returned):** If backend returns an approval token/link, is SA allowed to view/copy it in POS?

### Work Order Execution: Parts Issue/Consume & Finalization

1. **Parts usage endpoints (blocking):** Provide Moqui services/REST endpoints for:

- loading work order parts with totals
- listing usage events
- creating a usage event

1. **Usage event model (blocking):** Confirm whether usage events are separate types (`ISSUE`, `CONSUME`, `RETURN`) or combined.

2. **Authorized quantity enforcement (blocking):** Confirm invariant:

- `consumed <= issued <= authorized` vs other rule
   and what “remaining authorized” means.

1. **Insufficient inventory behavior (blocking):** On ISSUE with insufficient inventory:

- reject only
- transition to `AWAITING_PARTS`
- require explicit transition endpoint

1. **Capabilities for parts usage (blocking):** Provide capability keys for:

- view usage
- record ISSUE/CONSUME/RETURN
- transition to `AWAITING_PARTS` (if explicit)

1. **Inventory availability indicators (blocking):** How should UI detect whether to show on-hand/availability:

- embedded in parts list response
- separate inventory endpoint
- never shown in workexec UI

1. **Finalize for billing contract (blocking):** Provide endpoints/services and schemas for:

- finalize for billing (snapshot creation)
- list snapshots by work order
- load snapshot detail
- variance approval (and whether inline vs separate)

1. **Finalization indicator (blocking):** Is finalization represented by:

- new `DurWorkOrder.statusId`
- `activeBillableSnapshotId` presence
- or both?

1. **Variance schema and reason codes (blocking):** Provide structured variance payload and reason code source/list.

### Scheduling / Dispatch / Appointments

1. **Daily schedule SoR (blocking):** Confirm whether “Daily Schedule” is:

- workexec dispatch board (work orders), or
- shopmgr scheduling calendar (appointments/blocks)

1. **Board exception codes (blocking):** Provide stable exception/conflict code enums for dispatch board.

2. **Appointment reschedule/cancel ownership (blocking):** Confirm story labeling and implementation location:

- should be shopmgr (`AppointmentEdit.xml`) with workexec read-only display only.

1. **Time payload semantics (blocking):** For reschedule requests, confirm timestamp format (UTC `Z` vs local with offset) and parsing expectations.

### Role-based visibility in execution UI

1. **Capability signal source (blocking):** Where does Moqui frontend obtain capability flags (session claims vs `/me/capabilities` vs embedded payload)?

2. **Sensitive field inventory (blocking):** Enumerate sensitive financial fields present in work order/line item payloads and which capabilities gate them.

3. **Omission semantics (non-blocking):** If backend omits fields, is omission guaranteed to mean “not authorized” vs “not applicable”? Prefer explicit capability flags.

### Event ingestion (appointment updates)

1. **Transport selection (blocking for implementation):** In this repo, what inbound event transport is chosen (webhook vs broker consumer vs polling)? (DECISION-INVENTORY-015 allows safe_to_defer, but implementation needs a choice.)

2. **Appointment execution status field (blocking):** Which field on `DurShopAppointment` is safe to update for execution status without conflicting with scheduling status?

3. **Timeline storage and append policy (blocking):**

- where timeline entries live (shopmgr vs workexec)
- whether same-status events append timeline rows for audit
- payload storage policy (payload JSON vs hash-only)

1. **Ingestion security model (blocking):** What auth mechanism protects ingestion endpoint and what permissions gate ops inbox/failure screens?

### Domain conflicts (explicit)

1. **Sales order/cart SoR (blocking):** Which domain owns `SalesOrder`/cart lifecycle in this repo? Workexec should not own it. Relabel story accordingly.

## Todos Reconciled

- Original todo: "Confirm canonical start-eligible statuses exposed to frontend." → Resolution: Resolved (use Moqui `WO_CREATED`/`WO_SCHEDULED` as pre-start; started is `WO_IN_PROGRESS` or later).
- Original todo: "Whether managers can still override after start and snapshot semantics." → Resolution: Resolved (manager-only override with audit; do not mutate a start snapshot).
- Original todo: "Confirm event ingestion mechanism and security model." → Resolution: Replace with task: `TASK-WE-001` (choose transport + auth and implement inbox processing).
- Original todo: "Confirm whether gateways honor `Idempotency-Key`." → Resolution: Resolved (require `Idempotency-Key` support for create/submit; if not supported, add bridge-layer support).
- Original todo: "Standard error payload schema is not confirmed." → Resolution: Resolved (define standard envelope; normalize all errors to it).

## End

End of document.
