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

### What Work Execution does *not* own

- Part/product master data and substitute rule authoring (inventory/product domains)
- Inventory availability/on-hand/reservations (inventory domain)
- Appointment authoring/scheduling and operational schedule truth (shop management domain)
- People directory and availability (people domain)
- Permission policy definitions (security domain; workexec enforces only)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| `durion.workexec.DurWorkOrder` | Work order execution entity; includes `statusId`, `appointmentId`, `mechanicId`, `bayId` (as implemented today). |
| Estimate | Quote/estimate concept; edited and approved via `durion-workexec` screens/services. |
| SubstituteLink | Relationship between `partId` and `substitutePartId` with type/priority/active flags (master-data adjacency). |
| Substitution history | Append-only record created when a substitute is applied to a work order/estimate line. |
| Appointment (`durion.shopmgr.DurShopAppointment`) | Shop scheduling entity; work orders may reference via `appointmentId`. |
| Dispatch board view | Read-only location/date view of work orders and scheduling signals; partial rendering permitted. |

## Invariants / Business Rules

- Work order state transitions are authoritative and audited.
- “Work started” gating must be based on authoritative status signals and must prevent mid-work assignment drift.
- Substitution apply must be eligibility-checked server-side and must produce immutable history.
- Mutation operations must be safe for double-submit (idempotency) and stale edits (conflict).
- Dispatch board is read-only in v1; partial failures (People availability) must not block core view.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | SubstituteLink authoring is not workexec SoR | [DOMAIN_NOTES.md](#decision-inventory-001---substitutelink-ownership-boundary) |
| DECISION-INVENTORY-002 | Use existing `durion-workexec` and `durion-shopmgr` screens | [DOMAIN_NOTES.md](#decision-inventory-002---canonical-moqui-screensroutes) |
| DECISION-INVENTORY-003 | IDs are opaque strings | [DOMAIN_NOTES.md](#decision-inventory-003---identifier-handling-opaque-ids) |
| DECISION-INVENTORY-004 | Started means `WO_IN_PROGRESS` or later | [DOMAIN_NOTES.md](#decision-inventory-004---workorder-status-taxonomy-and-work-started) |
| DECISION-INVENTORY-005 | Operational context is shopmgr SoR; overrides audited | [DOMAIN_NOTES.md](#decision-inventory-005---assignment-vs-operational-context-sor--audit) |
| DECISION-INVENTORY-006 | SubstituteLink keys immutable; soft deactivate | [DOMAIN_NOTES.md](#decision-inventory-006---substitutelink-update-semantics-and-defaults) |
| DECISION-INVENTORY-007 | Picker supports WO + Estimate; backend enforces eligibility | [DOMAIN_NOTES.md](#decision-inventory-007---substitution-picker-scope--eligibility-source) |
| DECISION-INVENTORY-008 | Part selection via search/picker | [DOMAIN_NOTES.md](#decision-inventory-008---part-lookup-ux-contract) |
| DECISION-INVENTORY-009 | Board is read-only; aggregate where feasible | [DOMAIN_NOTES.md](#decision-inventory-009---dispatch-board-contract--aggregation-behavior) |
| DECISION-INVENTORY-010 | Appointments are separate shopmgr entities | [DOMAIN_NOTES.md](#decision-inventory-010---appointments-vs-workorders-sor--link) |
| DECISION-INVENTORY-011 | Standard error envelope with correlationId | [DOMAIN_NOTES.md](#decision-inventory-011---standard-error-envelope--duplicate-signaling) |
| DECISION-INVENTORY-012 | UI sends `Idempotency-Key` for create/submit | [DOMAIN_NOTES.md](#decision-inventory-012---idempotency-key-usage-for-ui-mutations) |
| DECISION-INVENTORY-013 | Capability signal drives UI gating; backend authoritative | [DOMAIN_NOTES.md](#decision-inventory-013---capabilitypermission-signaling--manual-price-gating) |
| DECISION-INVENTORY-014 | Audit metadata by default; optional audit endpoints | [DOMAIN_NOTES.md](#decision-inventory-014---audit-visibility-strategy-substitutes--overrides) |
| DECISION-INVENTORY-015 | Inbox + async processing; DLQ + ops view | [DOMAIN_NOTES.md](#decision-inventory-015---event-ingestion-mechanism--failure-handling) |
| DECISION-INVENTORY-016 | Display user TZ; bucket by shop TZ | [DOMAIN_NOTES.md](#decision-inventory-016---timezone-semantics-for-shop-ux) |

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

### Q: Operational context story ownership: Frontend issue labels “Shop Management/user” but backend reference is `domain:workexec` with Shopmgr as SoR. Confirm ownership/label.

- Answer: Operational context is shopmgr SoR; workexec owns the execution UI that displays it and gates edits by status/permission.
- Assumptions:
- Scheduling/bay constraints are authored in shopmgr.
- Rationale:
- One authoritative scheduling owner.
- Impact:
- Coordinate contracts across workexec and shopmgr.
- Decision ID: DECISION-INVENTORY-005

### Q: Timekeeping / people-adjacent features: Several stories label domain conflicts (user/shop management vs workexec). Confirm what belongs in workexec UI vs people/shopmgr UI.

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

### Q: Part lookup UX: What endpoint/screen should admin UI use to search/select parts by SKU/name? Provide route(s) + response shape.

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

### Q: Substitution picker endpoints: Exact endpoints and payload schemas for fetching candidates (WO/Estimate) and applying a selected substitute.

- Answer: Provide a stable picker contract for both WorkOrder and Estimate lines returning eligibility + pricing + permission flags; apply returns enough data to refresh the affected line.
- Assumptions:
- Backend exposes a dedicated picker DTO.
- Rationale:
- Runtime substitution requires context beyond SubstituteLink.
- Impact:
- Define and version a picker DTO schema.
- Decision ID: DECISION-INVENTORY-007

### Q: Dispatch Board endpoint contract: Confirm exact endpoint path and request/response schema for `DispatchBoardView` and `ExceptionIndicator`.

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

### Q: Assignment context endpoints: Exact endpoints/services for loading work order detail, updating assignment context, and fetching audit/history.

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

### Q: ID types: Confirm identifier types (uuid vs numeric vs prefixed strings) for `locationId`, `resourceId`, `mechanicId`, `partId`, etc., and whether UI should use searchable pickers.

- Answer: Treat all IDs as opaque strings (`type="id"`) and use pickers/search rather than UUID/numeric assumptions.
- Assumptions:
- IDs may be generated by different systems.
- Rationale:
- Avoid invalid client-side validation.
- Impact:
- UI controls must not assume UUID.
- Decision ID: DECISION-INVENTORY-003

## Todos Reconciled

- Original todo: "Confirm canonical start-eligible statuses exposed to frontend." → Resolution: Resolved (use Moqui `WO_CREATED`/`WO_SCHEDULED` as pre-start; started is `WO_IN_PROGRESS` or later).
- Original todo: "Whether managers can still override after start and snapshot semantics." → Resolution: Resolved (manager-only override with audit; do not mutate a start snapshot).
- Original todo: "Confirm event ingestion mechanism and security model." → Resolution: Replace with task: `TASK-WE-001` (choose transport + auth and implement inbox processing).
- Original todo: "Confirm whether gateways honor `Idempotency-Key`." → Resolution: Resolved (require `Idempotency-Key` support for create/submit; if not supported, add bridge-layer support).
- Original todo: "Standard error payload schema is not confirmed." → Resolution: Resolved (define standard envelope; normalize all errors to it).

## End

End of document.
