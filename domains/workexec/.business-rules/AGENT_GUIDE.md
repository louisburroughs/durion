---
type: Agent Guide
title: Work Execution Agent Guide
description: This document is the normative guide for the workexec (Work Execution) domain. It defines ownership boundaries, canonical concepts, and the required invariants for workorder execution, estimate flo...
domain: workexec
tags: [domain, workexec, agent-guide]
---

# AGENT_GUIDE.md

## Summary

This document is the normative guide for the `workexec` (Work Execution) domain. It defines ownership boundaries, canonical concepts, and the required invariants for workorder execution, estimate flows, substitutions, dispatch board views, and event-driven appointment updates. It resolves all previously captured open questions and reconciles todos into explicit decisions or tracked tasks.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | SubstituteLink ownership boundary |
| DECISION-INVENTORY-002 | Canonical frontend screens/routes |
| DECISION-INVENTORY-003 | Identifier handling (opaque IDs) |
| DECISION-INVENTORY-004 | Workorder status taxonomy and “work started” |
| DECISION-INVENTORY-005 | Assignment vs operational context (SoR + audit) |
| DECISION-INVENTORY-006 | SubstituteLink update semantics and defaults |
| DECISION-INVENTORY-007 | Substitution picker scope + eligibility source |
| DECISION-INVENTORY-008 | Part lookup UX contract |
| DECISION-INVENTORY-009 | Dispatch board contract + aggregation behavior |
| DECISION-INVENTORY-010 | Appointments vs workorders (SoR + link) |
| DECISION-INVENTORY-011 | Standard error envelope + duplicate signaling |
| DECISION-INVENTORY-012 | Idempotency-Key usage for UI mutations |
| DECISION-INVENTORY-013 | Capability/permission signaling + manual price gating |
| DECISION-INVENTORY-014 | Audit visibility strategy (substitutes + overrides) |
| DECISION-INVENTORY-015 | Event ingestion mechanism + failure handling |
| DECISION-INVENTORY-016 | Timezone semantics for shop UX |
| DECISION-INVENTORY-017 | Position assignment never defaults the technician |
| DECISION-INVENTORY-018 | Technician must be staffed at the workorder's site; no override |
| DECISION-INVENTORY-019 | pos-people owns technician-to-SITE staffing, not technician-to-bay/mobile-unit |
| DECISION-INVENTORY-020 | No cap on concurrent workorders per technician |
| DECISION-INVENTORY-021 | One technician per workorder, one technician per mobile unit; no crew |
| DECISION-INVENTORY-022 | Assignment-ownership boundary: pos-shop-manager plans, pos-workorder records |
| DECISION-INVENTORY-023 | Workorder transfer between locations: same workorder, one operation, site owned by workexec |
| DECISION-INVENTORY-024 | Transfer only before work starts; position and technician released |
| DECISION-INVENTORY-025 | Transfer keeps quoted prices; tax follows the new location at invoicing; no re-approval |
| DECISION-INVENTORY-026 | Transfer parts: release at source, re-request at target; workexec never moves stock |
| DECISION-INVENTORY-027 | Transfer labour and history: no recorded time crosses a transfer |
| DECISION-INVENTORY-028 | Transfer authority, reason code, audit trail and published fact |

## Domain Boundaries

### What Work Execution owns (system of record)

- Workorder execution lifecycle state (`durion.workexec.DurWorkorder.statusId` in frontend `durion-workexec`)
- Execution-time edits that are explicitly WorkExec-owned (execution notes, completion/invoicing transitions) while assignment/scheduling context remains a ShopMgmt source of record per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md)
- Estimate editing/approval flows implemented in `durion-workexec` screens/services
- Runtime substitution apply behavior for estimate/workorder line items (including immutable substitution history)
- Execution-facing read models owned by work execution when derived from workexec-owned data
- Per-workorder technician assignment (`technician_assignment`) — the single current technician of record — and per-workorder position assignment (`service_position_assignment`, bay or mobile unit); each is workexec-owned and the two are independent of each other (DECISION-INVENTORY-017, -019, -021)

### What Work Execution does *not* own

- Part/product master data and substitute rule authoring (inventory/product domains)
- Inventory availability/on-hand/reservations (inventory domain)
- Appointment authoring/scheduling and operational schedule truth (shop management domain)
- Dispatch board data, mechanic assignments, and reschedule workflows (shop management domain per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md)); WorkExec consumes read-only projections only
- People directory and availability (people domain); technician-to-SITE staffing is pos-people's owned fact, consumed by workexec only via the `ext_people_staffing_assignment` replica — never a synchronous call (DECISION-INVENTORY-018, -019)
- Technician-to-bay and technician-to-mobile-unit relations — these are not modeled anywhere and will not be (bays are pooled; DECISION-INVENTORY-017, -019)
- The PLANNED assignment (who is expected to work an appointment, including any multi-mechanic LEAD/ASSIST shape) — that stays shop management's fact; workexec owns only the ACTUAL CURRENT technician (DECISION-INVENTORY-022)
- Permission policy definitions (security domain; workexec enforces only)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| `durion.workexec.DurWorkorder` | Workorder execution entity; includes `statusId`, `appointmentId`, `mechanicId`, `bayId` (as implemented today). |
| Estimate | Quote/estimate concept; edited and approved via `durion-workexec` screens/services. |
| SubstituteLink | Relationship between `partId` and `substitutePartId` with type/priority/active flags (master-data adjacency). |
| Substitution history | Append-only record created when a substitute is applied to a workorder/estimate line. |
| Appointment (`durion.shopmgr.DurShopAppointment`) | Shop scheduling entity; workorders may reference via `appointmentId`. |
| Dispatch board view | ShopMgmt-owned read-only projection of scheduling data that WorkExec consumes for execution context (per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md)). |
| `technician_assignment` | pos-workorder-owned; the single current technician of record for a workorder. Independent of position; never defaulted from position staffing (DECISION-INVENTORY-017, -021). |
| `service_position_assignment` | pos-workorder-owned; a workorder's bay or mobile-unit position. Independent of `technician_assignment`; a repeated save of the same current position is a no-op that writes no history (DECISION-INVENTORY-017). |
| `ext_people_staffing_assignment` | pos-workorder's replica of pos-people's technician-to-SITE staffing fact (ADR-0044 R1/R3/R6). Read-only; queried by the assign/reassign technician endpoints to refuse on a positive site contradiction (DECISION-INVENTORY-018). |
| `Assignment` / `AssignmentMechanic(LEAD\|ASSIST)` | shopmgmt-owned PLANNED assignment, including any multi-mechanic shape kept for scheduling. Consumed by workexec only as an event input (`AssignmentUpdatedEvent`); stops at the boundary and is never mirrored into `technician_assignment` (DECISION-INVENTORY-022). |
| `mechanic_ids` / `assignedMechanics` | pos-workorder-owned but **legacy** (#1658); the `Workorder.mechanic_ids` JSON column, exposed as `assignedMechanics` on `OperationalContextResponse`/`OperationalContextOverrideRequest`. Still written by the assignment-context event and `overrideOperationalContext`; reconciled with the current `technician_assignment` (current technician first, then unnamed legacy ids) by `WorkorderFactPublisher` for the published `mechanicIds` fact (#2015). Not a crew model and not a second technician of record — do not build new features on it (DECISION-INVENTORY-021). |

## Invariants / Business Rules

- Workorder state transitions are authoritative and audited.
- “Work started” gating must be based on authoritative status signals and must prevent mid-work assignment drift.
- Substitution apply must be eligibility-checked server-side and must produce immutable history.
- Mutation operations must be safe for double-submit (idempotency) and stale edits (conflict).
- Dispatch board feed is provided by ShopMgmt and is read-only for WorkExec consumers; partial failures (People availability) must not block core view.
- Identifier generation for new WorkExec-owned entities follows the platform UUID v7 standard per [ADR-0013](../../../docs/adr/0013-platform-uuid-identifier-strategy.adr.md); treat IDs as opaque strings across contracts.

### Technician & position assignment rules (issue #1990, #2000)

- **Position never defaults the technician.** Bays are pooled — no technician owns a bay, no technician-to-bay relation exists or will be created, and the same holds for mobile units. Assigning/moving/releasing a position never reads or writes `technician_assignment`, and vice versa; `GET /v1/workorders/{workorderId}/position` returning both together is presentation only (DECISION-INVENTORY-017).
- **A technician must be staffed at the workorder's site — no override exists.** `POST`/`PUT /v1/workorders/{workorderId}/technician` refuse with 422 `TECHNICIAN_NOT_STAFFED_AT_SITE` when the technician has one or more ACTIVE `ext_people_staffing_assignment` rows effective today and none is at the workorder's site. `is_primary` and role are not filtered on. A technician with **no** active staffing rows is allowed through — the check refuses only a positive contradiction, never absence of data (ADR-0044 R3). There is no override permission or reason code; do not add one. **"The workorder's site" is resource-specific** (`TechnicianAssignmentServiceImpl.resolveSiteId`): for `MOBILE_UNIT`, compare staffing against `ExtMobileUnitReplica.baseLocationId`; for `BAY`, `HOLD`, and no position, use the workorder's own `locationId`. `ServicePositionServiceImpl.requireSameSite` forces those two values equal today, so the branch is currently a no-op — it is written explicitly so the check does not silently break if a mobile workorder ever carries the customer's site instead of the unit's (DECISION-INVENTORY-018).
- **Ownership split:** pos-people owns technician-to-SITE staffing (system of record, replicated to workexec as `ext_people_staffing_assignment`, never called synchronously); pos-location owns bay/mobile-unit identity and capability (asset configuration, not staffing); pos-workorder owns only `technician_assignment` and `service_position_assignment` (DECISION-INVENTORY-019).
- **No cap on concurrent workorders per technician.** A technician may hold any number of open/in-progress workorders. Concurrency is constrained at the clock, not the assignment: `WorkexecTimeTrackingServiceImpl.startTimer` refuses a second concurrent timer for the same technician (409 `TIMER_ALREADY_ACTIVE`), keyed on technician, not workorder. Do not add a per-technician WIP cap (DECISION-INVENTORY-020).
- **One technician per workorder; one technician per mobile unit; no crew.** `technician_assignment_one_current_uniq` means exactly one technician **of record** — no LEAD/ASSIST, no crew table in workexec. Mobile-unit exclusivity (at most one *open* workorder per mobile unit) falls out of `workorder_open_position_uniq` + `technician_assignment_one_current_uniq` once `ServicePositionServiceImpl.releaseOnClose`, the `HOLD` resource-id rule, and the null-`resource_id` handling close the leak paths; do not relax `ResourceType.isExclusive()` or `workorder_open_position_uniq`. Per-person labor time (`WorkorderLaborEntry.technicianId`, incl. `workorder:labor:add_on_behalf`) and per-segment travel (`TravelSegment.technicianId`) are not exceptions to this — they log whose time was spent, not who the technician of record is. Neither is `mechanic_ids`/`assignedMechanics` (see Key Entities): it is a legacy multi-valued list (#1658), still written by the assignment-context event and `overrideOperationalContext`, and it confers no second technician of record; nothing new is to be built on it, and it is not authority for a crew (DECISION-INVENTORY-021).
- **Planning vs. recording boundary.** pos-shop-manager owns the PLANNED assignment (`Assignment` + `AssignmentMechanic(LEAD|ASSIST)`, which may be multi-mechanic for scheduling purposes). pos-workorder owns the ACTUAL CURRENT technician — exactly one person. shopmgmt's `AssignmentUpdatedEvent` is an input `WorkorderAssignmentEventListener` may act on, never a second system of record; `AssignmentMechanic` roles stop at the shopmgmt/workorder boundary and must never be mirrored into `technician_assignment` (DECISION-INVENTORY-022).

### Workorder transfer between locations (issue #2258)

- **Same workorder; only transfer moves the site.** `POST /v1/workorders/{workorderId}/transfer` changes the site on the same workorder, keeping its id, number, approval and histories. It writes `shopId` and `locationId` together. No other path may change the site: `overrideOperationalContext` refuses a different `locationId` with 422 `WORKORDER_TRANSFER_REQUIRED`, and an inbound `AssignmentUpdatedEvent` naming another site is dropped whole. An appointment move never transfers a workorder (DECISION-INVENTORY-023).
- **Only before work starts.** A transfer is allowed from `DRAFT`, `APPROVED` or `ASSIGNED` with `workStartedAt` unset, no pending change request, no recorded time (labour entry, work session, travel segment) and no parts in hand. Otherwise it is refused with 409 `WORKORDER_CLOSED` / `WORKORDER_TRANSFER_NOT_ALLOWED`, or 422 `WORKORDER_TRANSFER_CHANGE_REQUEST_PENDING` / `_TIME_RECORDED` / `_PARTS_IN_HAND`. The target must be a known, active location other than the current one (422 `WORKORDER_TRANSFER_LOCATION_INVALID` / `_INACTIVE`). The position and the technician are released, `ASSIGNED` returns to `APPROVED`, and no new status exists. The appointment at the source is cancelled by pos-shop-manager and rebooked at the target (DECISION-SHOPMGMT-024, -025) (DECISION-INVENTORY-024).
- **Prices kept, tax at the new site, no re-approval.** Lines keep their snapshotted prices and rates. The estimate is not moved or recalculated. pos-invoice taxes at the workorder's `locationId` at finalization. Lines and approvals after the transfer use the target's rates and approval configuration (DECISION-INVENTORY-025).
- **Parts.** Demand at the source is released (`inventory.workorder-demand.release-requested`) and re-requested at the target, with location-qualified command ids. Workexec never moves stock (DECISION-INVENTORY-026).
- **Labour and history.** No clocked time ever crosses a transfer. All history rows stay as written (DECISION-INVENTORY-027).
- **Authority and audit.** `workorder:workorder:transfer` is required, location-scoped at both the source and the target. `reasonCode` (`CUSTOMER_REQUEST`, `CAPACITY`, `EQUIPMENT`, `MOBILE_DEPOT`, `OTHER`, with a note required for `OTHER`) is required. The transfer writes an append-only `workorder_location_transfer` row and publishes `workorder.workorder.transferred` on `workorder.events.v1` (DECISION-INVENTORY-028).

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | SubstituteLink authoring is not workexec SoR | [DOMAIN_NOTES.md](#decision-inventory-001---substitutelink-ownership-boundary) |
| DECISION-INVENTORY-002 | Use existing `durion-workexec` and `durion-shopmgr` screens | [DOMAIN_NOTES.md](#decision-inventory-002---canonical-frontend-screensroutes) |
| DECISION-INVENTORY-003 | IDs are opaque strings | [DOMAIN_NOTES.md](#decision-inventory-003---identifier-handling-opaque-ids) |
| DECISION-INVENTORY-004 | Started means `WO_IN_PROGRESS` or later | [DOMAIN_NOTES.md](#decision-inventory-004---workorder-status-taxonomy-and-work-started) |
| DECISION-INVENTORY-005 | Operational context is shopmgr SoR; overrides audited | [DOMAIN_NOTES.md](#decision-inventory-005---assignment-vs-operational-context-sor--audit) |
| DECISION-INVENTORY-006 | SubstituteLink keys immutable; soft deactivate | [DOMAIN_NOTES.md](#decision-inventory-006---substitutelink-update-semantics-and-defaults) |
| DECISION-INVENTORY-007 | Picker supports WO + Estimate; backend enforces eligibility | [DOMAIN_NOTES.md](#decision-inventory-007---substitution-picker-scope--eligibility-source) |
| DECISION-INVENTORY-008 | Part selection via search/picker | [DOMAIN_NOTES.md](#decision-inventory-008---part-lookup-ux-contract) |
| DECISION-INVENTORY-009 | ShopMgmt-owned dispatch feed; WorkExec consumes read-only projections | [DOMAIN_NOTES.md](#decision-inventory-009---dispatch-board-contract--aggregation-behavior) |
| DECISION-INVENTORY-010 | Appointments are separate shopmgr entities | [DOMAIN_NOTES.md](#decision-inventory-010---appointments-vs-workorders-sor--link) |
| DECISION-INVENTORY-011 | Standard error envelope with correlationId | [DOMAIN_NOTES.md](#decision-inventory-011---standard-error-envelope--duplicate-signaling) |
| DECISION-INVENTORY-012 | UI sends `Idempotency-Key` for create/submit | [DOMAIN_NOTES.md](#decision-inventory-012---idempotency-key-usage-for-ui-mutations) |
| DECISION-INVENTORY-013 | Capability signal drives UI gating; backend authoritative | [DOMAIN_NOTES.md](#decision-inventory-013---capabilitypermission-signaling--manual-price-gating) |
| DECISION-INVENTORY-014 | Audit metadata by default; optional audit endpoints | [DOMAIN_NOTES.md](#decision-inventory-014---audit-visibility-strategy-substitutes--overrides) |
| DECISION-INVENTORY-015 | Inbox + async processing; DLQ + ops view | [DOMAIN_NOTES.md](#decision-inventory-015---event-ingestion-mechanism--failure-handling) |
| DECISION-INVENTORY-016 | Display user TZ; bucket by shop TZ | [DOMAIN_NOTES.md](#decision-inventory-016---timezone-semantics-for-shop-ux) |
| DECISION-INVENTORY-017 | Position assignment never defaults the technician | [DOMAIN_NOTES.md](#decision-inventory-017---position-assignment-never-defaults-the-technician) |
| DECISION-INVENTORY-018 | Technician must be staffed at the workorder's site; no override | [DOMAIN_NOTES.md](#decision-inventory-018---a-technician-must-be-staffed-at-the-workorders-site-no-override) |
| DECISION-INVENTORY-019 | pos-people owns technician-to-SITE staffing only | [DOMAIN_NOTES.md](#decision-inventory-019---pos-people-owns-technician-to-site-staffing-not-technician-to-bay-or-technician-to-mobile-unit) |
| DECISION-INVENTORY-020 | No cap on concurrent workorders per technician | [DOMAIN_NOTES.md](#decision-inventory-020---no-cap-on-concurrent-workorders-per-technician) |
| DECISION-INVENTORY-021 | One technician per workorder/mobile unit; no crew | [DOMAIN_NOTES.md](#decision-inventory-021---one-technician-per-workorder-one-technician-per-mobile-unit-no-crew) |
| DECISION-INVENTORY-022 | pos-shop-manager plans, pos-workorder records | [DOMAIN_NOTES.md](#decision-inventory-022---assignment-ownership-boundary-pos-shop-manager-plans-pos-workorder-records) |
| DECISION-INVENTORY-023 | Transfer changes the site on the same workorder; only transfer writes the site | [DOMAIN_NOTES.md](#decision-inventory-023---workorder-transfer-between-locations-same-workorder-one-operation-site-owned-by-workexec) |
| DECISION-INVENTORY-024 | Transfer only from DRAFT/APPROVED/ASSIGNED with no recorded time; releases position and technician | [DOMAIN_NOTES.md](#decision-inventory-024---transfer-is-allowed-only-before-work-starts-the-position-and-technician-are-released) |
| DECISION-INVENTORY-025 | Prices kept; invoice tax at the new location; no re-approval | [DOMAIN_NOTES.md](#decision-inventory-025---transfer-keeps-quoted-prices-tax-follows-the-new-location-at-invoicing-no-re-approval) |
| DECISION-INVENTORY-026 | Release demand at source, re-request at target; refuse while parts are in hand | [DOMAIN_NOTES.md](#decision-inventory-026---parts-release-at-the-source-and-re-request-at-the-target-workexec-never-moves-stock) |
| DECISION-INVENTORY-027 | No clocked time crosses a transfer; history is not rewritten | [DOMAIN_NOTES.md](#decision-inventory-027---labour-and-history-no-recorded-time-crosses-a-transfer-history-stays-where-it-was-written) |
| DECISION-INVENTORY-028 | `workorder:workorder:transfer` at both ends; reason code; `workorder.workorder.transferred` fact | [DOMAIN_NOTES.md](#decision-inventory-028---transfer-authority-reason-code-audit-trail-and-published-fact) |

## Open Questions (from source)

### Q: Substitutes domain ownership: Story #109 is labeled `domain:workexec` but is product/parts admin flavored. Should this be `domain:inventory` or a product domain label? {#decision-inventory-001---substitutelink-ownership-boundary}

- Answer: SubstituteLink authoring/admin is not owned by workexec; it should be owned by inventory/product master-data domains. Workexec consumes SubstituteLink for runtime substitution and records substitution history.
- Assumptions:
- SubstituteLink is reused beyond workorder execution.
- Rationale:
- Keeps SoR boundaries clean.
- Impact:
- Relabel story; treat SubstituteLink as a workexec dependency.
- Decision ID: DECISION-INVENTORY-001

### Q: Operational context story ownership: Frontend issue labels “Shop Management/user” but backend reference is `domain:workexec` with Shopmgr as SoR. Confirm ownership/label {#decision-inventory-005---assignment-vs-operational-context-sor--audit}

- Answer: Operational context is shopmgr SoR; workexec owns the execution UI that displays it and gates edits by status/permission.
- Assumptions:
- Scheduling/bay constraints are authored in shopmgr.
- Rationale:
- One authoritative scheduling owner.
- Impact:
- Coordinate contracts across workexec and shopmgr.
- Decision ID: DECISION-INVENTORY-005

### Q: Timekeeping / people-adjacent features: Several stories label domain conflicts (user/shop management vs workexec). Confirm what belongs in workexec UI vs people/shopmgr UI {#decision-inventory-009---timekeeping-readonly}

- Answer: Per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md), time entry remains in the People domain; WorkExec may consume People availability only as a read-only signal for dispatch views.
- Assumptions:
- People is the SoR for availability/timekeeping.
- Rationale:
- Prevents competing SoR.
- Impact:
- Move any time-entry CRUD story to `domain:people`.
- Decision ID: DECISION-INVENTORY-009

### Q: What are the canonical frontend screen paths/routes for workorder detail, estimate detail, appointment detail, reporting/dispatch screens? {#decision-inventory-002---canonical-frontend-screensroutes}

- Answer: Use existing frontend screens under `durion-frontend` (workexec: `WorkOrderBoard.xml`, `WorkOrderEdit.xml`, `EstimateEdit.xml`; shopmgr: `AppointmentEdit.xml`).
- Assumptions:
- Frontend routing exposes these screens via component menus.
- Rationale:
- Aligns stories with implemented artifacts.
- Impact:
- Reference these screens in story AC and navigation.
- Decision ID: DECISION-INVENTORY-002

### Q: Part lookup UX: What endpoint/screen should admin UI use to search/select parts by SKU/name? Provide route(s) + response shape {#decision-inventory-008---part-lookup-ux-contract}

- Answer: Use a searchable part/product picker (query + pagination) instead of manual ID entry.
- Assumptions:
- Product master records are queryable by SKU/name.
- Rationale:
- Prevents invalid IDs.
- Impact:
- Add/reuse a “Find Part/Product” UI and supporting service.
- Decision ID: DECISION-INVENTORY-008

### Q: SubstituteLink list/search: Do we have `GET /api/v1/substitutes` with filters/pagination, or must list be “query by partId” only? {#decision-inventory-006---substitutelink-update-semantics-and-defaults}

- Answer: Default is “query by partId”; a global list is allowed only if paginated and filter-required.
- Assumptions:
- Dataset can be large.
- Rationale:
- Keeps performance predictable.
- Impact:
- Admin UI starts with selecting a part, then lists substitutes.
- Decision ID: DECISION-INVENTORY-006

### Q: Substitution picker endpoints: Exact endpoints and payload schemas for fetching candidates (WO/Estimate) and applying a selected substitute {#decision-inventory-007---substitution-picker-scope--eligibility-source}

- Answer: Provide a stable picker contract for both WorkOrder and Estimate lines returning eligibility + pricing + permission flags; apply returns enough data to refresh the affected line.
- Assumptions:
- Backend exposes a dedicated picker DTO.
- Rationale:
- Runtime substitution requires context beyond SubstituteLink.
- Impact:
- Define and version a picker DTO schema.
- Decision ID: DECISION-INVENTORY-007

### Q: Dispatch Board endpoint contract: Confirm exact endpoint path and request/response schema for `DispatchBoardView` and `ExceptionIndicator` {#decision-inventory-009---dispatch-board-contract--aggregation-behavior}

- Answer: Per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md), ShopMgmt owns the dispatch board projection. WorkExec consumes the existing `WorkOrderBoard.xml` view (or future ShopMgmt REST read model) strictly as a read-only, filter-required feed that surfaces stable exception codes.
- Assumptions:
- Board v1 is read-only and sourced from ShopMgmt.
- Rationale:
- Enables progressive enhancement without duplicating ShopMgmt scheduling logic.
- Impact:
- Contract must define `asOf` and exception enums while clearly identifying ShopMgmt as the system of record.
- Decision ID: DECISION-INVENTORY-009

### Q: Aggregation responsibility: Does dispatch board already include mechanic availability and bay occupancy, or must frontend call People availability separately?

- Answer: ShopMgmt is responsible for aggregating scheduling signals (mechanic availability, bay occupancy) in the dispatch projection. If supplemental People data is required, WorkExec may fetch it in parallel but must continue rendering the ShopMgmt feed even on auxiliary failures.
- Assumptions:
- People availability is secondary and sourced outside WorkExec.
- Rationale:
- Partial outages must not block dispatch while respecting ShopMgmt ownership.
- Impact:
- UI implements partial rendering + warning state without duplicating ShopMgmt aggregation logic.
- Decision ID: DECISION-INVENTORY-009

### Q: Appointments vs Work Orders: Are “appointments” separate entities or represented as workorders with scheduled times? If separate, what endpoint supplies them? {#decision-inventory-010---appointments-vs-workorders-sor--link}

- Answer: Appointments are separate shopmgr entities; workorders reference via `appointmentId` and appointment details are supplied by shopmgr.
- Assumptions:
- Scheduling remains shopmgr-owned.
- Rationale:
- Single SoR for scheduling.
- Impact:
- Any board join is via `appointmentId`.
- Decision ID: DECISION-INVENTORY-010

### Q: Assignment context endpoints: Exact endpoints/services for loading workorder detail, updating assignment context, and fetching audit/history {#decision-inventory-014---audit-visibility-strategy-substitutes--overrides}

- Answer: In frontend today, assignment-like fields live on `DurWorkorder` and are updated by existing workexec services; add an append-only audit entity/service if stories require visible history.
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

### Q: Event ingestion mechanism: How are Workexec events delivered/handled in this frontend repo (webhook, broker consumer, polling/inbox)? {#decision-inventory-015---event-ingestion-mechanism--failure-handling}

- Answer: Use an inbox pattern (persist first, process async, DB idempotency). Transport may be webhook or broker.
- Assumptions:
- Delivery is at-least-once.
- Rationale:
- Durable processing with retries.
- Impact:
- Requires inbox entity + ops failure view + alerts.
- Decision ID: DECISION-INVENTORY-015

### Q: Invoice event semantics: Is `InvoiceIssued` a separate event type or a status within `WorkorderStatusChanged`? What fields are present?

- Answer: In frontend today, invoicing transitions the workorder to `WO_INVOICED`; treat invoice issuance as a status transition unless a dedicated billing event contract exists.
- Assumptions:
- Billing may later emit `InvoiceIssued`.
- Rationale:
- Use the simplest consistent signal now.
- Impact:
- Avoid double-applying if both signals exist.
- Decision ID: DECISION-INVENTORY-015

### Q: Standard error envelope: For 400/409, what is the standard error response format (field errors, message, correlationId, existingResourceId on duplicates)? {#decision-inventory-011---standard-error-envelope--duplicate-signaling}

- Answer: Use a stable JSON envelope with `code`, `message`, `correlationId`, optional `fieldErrors[]`, and optional `existingResourceId` on duplicates.
- Assumptions:
- UI requires stable parsing.
- Rationale:
- Consistent UX.
- Impact:
- Normalize frontend and backend errors to this envelope.
- Decision ID: DECISION-INVENTORY-011

### Q: Idempotency-Key usage: Should frontend generate/send `Idempotency-Key` for create calls by default? {#decision-inventory-012---idempotency-key-usage-for-ui-mutations}

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

### Q: Permission signal source: How does frontend determine permission scopes/capabilities (session claims, user context endpoint, embedded in payload)? {#decision-inventory-013---capabilitypermission-signaling--manual-price-gating}

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

- Answer: Because ShopMgmt owns the board per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md), enforce explicit ShopMgmt-scoped permissions (e.g., `shopmgmt:dispatch:view`) and layer WorkExec location membership checks when embedding the read-only view.
- Assumptions:
- Board data is sensitive and cross-domain.
- Rationale:
- Least privilege without mis-stating ownership.
- Impact:
- Apply the ShopMgmt permission to screens/endpoints and honor WorkExec constraints when rendering embeds.
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

### Q: Which statuses count as “work started”: Is `READY_FOR_PICKUP` considered started for lock rules? {#decision-inventory-004---workorder-status-taxonomy-and-work-started}

- Answer: Yes—any in-progress-or-later status is considered started; `READY_FOR_PICKUP` is post-start.
- Assumptions:
- Other services may use different taxonomies.
- Rationale:
- Prevent late-stage reassignment drift.
- Impact:
- Prefer backend-provided `isStarted` boolean.
- Decision ID: DECISION-INVENTORY-004

### Q: “Team” definition: Is team represented by `assignedMechanics[]` only or separate team entity?

- Answer: Superseded by DECISION-INVENTORY-021. There is no team/crew concept in workexec: `technician_assignment_one_current_uniq` means exactly one technician of record per workorder, full stop — no LEAD/ASSIST roles, no crew table. `assignedMechanics[]` already exists (it is `Workorder.mechanic_ids`, #1658) but is a legacy multi-valued field, not a team model: it confers no second technician of record, and nothing new is to be built on it.
- Assumptions:
- Every workorder has exactly one technician of record; `assignedMechanics[]`/`mechanic_ids` is legacy bookkeeping the assignment-context event and `overrideOperationalContext` still write, reconciled against the current technician by `WorkorderFactPublisher` (#2015), not a second assignment mechanism.
- Rationale:
- Single assignment preserves the accountability chain (DECISION-INVENTORY-020, -021); planned multi-mechanic shapes stay in shopmgmt's `AssignmentMechanic` and stop at the boundary (DECISION-INVENTORY-022); the legacy `mechanic_ids` list is constrained by the same rule, not exempted from it.
- Impact:
- Do not introduce a crew entity; do not extend `mechanic_ids`/`assignedMechanics` into a real multi-mechanic assignment model, and do not treat it or shopmgmt's `AssignmentMechanic(LEAD|ASSIST)` as authority to give a workorder two technicians of record. A future multi-mechanic story needs a new decision record.
- Decision ID: DECISION-INVENTORY-021 (supersedes DECISION-INVENTORY-005 on this question)

### Q: Substitute audit trail API: Is there an API to fetch `SubstituteAudit` entries for display? If not, should UI show only created/updated metadata?

- Answer: Minimum requirement is created/updated metadata; an audit endpoint is optional and must be append-only if implemented.
- Assumptions:
- Operators mainly need “who/when”.
- Rationale:
- Avoid expensive reads unless required.
- Impact:
- Add `/substitutes/{id}/audit` only if demanded.
- Decision ID: DECISION-INVENTORY-014

### Q: Assignment/override audit source: Should UI display generic workorder transition history, a specific assignment sync log, or both?

- Answer: Prefer both when available; otherwise show transitions + metadata until a dedicated audit log exists.
- Assumptions:
- Lifecycle transitions and assignment edits are distinct.
- Rationale:
- Improves audit clarity.
- Impact:
- Add dedicated audit log if required by stories.
- Decision ID: DECISION-INVENTORY-014

### Q: Event failure handling: Should orphaned/invalid events be stored in DLQ outside frontend, in frontend DB for review, or both?

- Answer: Both—persist a failure record in frontend DB for ops review and emit to an external DLQ for alerting/remediation.
- Assumptions:
- Some failures require manual intervention.
- Rationale:
- Supports ops workflows.
- Impact:
- Implement durable failure entity + ops screen.
- Decision ID: DECISION-INVENTORY-015

### Q: Timezone source: Should timestamps display in shop/location timezone or user preference timezone? How does frontend obtain location timezone? {#decision-inventory-016---timezone-semantics-for-shop-ux}

- Answer: Display timestamps in user timezone; interpret date-bucket filters (dispatch day) in shop timezone when available and label timezone in UI. If shop timezone is not available in location entities, treat as safe_to_defer and use user timezone with explicit labeling.
- Assumptions:
- Shop timezone may be configured outside current entity models.
- Rationale:
- Avoid silent timezone errors.
- Impact:
- Add timezone source to location/facility model or shop configuration.
- Decision ID: DECISION-INVENTORY-016

### Q: ID types: Confirm identifier types (uuid vs numeric vs prefixed strings) for `locationId`, `resourceId`, `mechanicId`, `partId`, etc., and whether UI should use searchable pickers? {#decision-inventory-003---identifier-handling-opaque-ids}

- Answer: Per [ADR-0013](../../../docs/adr/0013-platform-uuid-identifier-strategy.adr.md), all newly created identifiers are UUID v7 values. Treat them as opaque strings (`type="id"`) in UI contracts, rely on backend generation, and use pickers/search rather than imposing client-side UUID validation heuristics.
- Assumptions:
- UUID v7 generation happens server-side; legacy entities may still surface historical formats until migrated.
- Rationale:
- Avoid invalid client-side validation while reinforcing the platform UUID v7 strategy.
- Impact:
- UI controls must not assume UUID beyond accepting 36-char hyphenated strings; backend remains the source of truth.
- Decision ID: DECISION-INVENTORY-003

## Todos Reconciled

- Original todo: "Confirm canonical start-eligible statuses exposed to frontend." → Resolution: Resolved (use frontend `WO_CREATED`/`WO_SCHEDULED` as pre-start; started is `WO_IN_PROGRESS` or later).
- Original todo: "Whether managers can still override after start and snapshot semantics." → Resolution: Resolved (manager-only override with audit; do not mutate a start snapshot).
- Original todo: "Confirm event ingestion mechanism and security model." → Resolution: Replace with task: `TASK-WE-001` (choose transport + auth and implement inbox processing).
- Original todo: "Confirm whether gateways honor `Idempotency-Key`." → Resolution: Resolved (require `Idempotency-Key` support for create/submit; if not supported, add bridge-layer support).
- Original todo: "Standard error payload schema is not confirmed." → Resolution: Resolved (define standard envelope; normalize all errors to it).

## End

End of document.
