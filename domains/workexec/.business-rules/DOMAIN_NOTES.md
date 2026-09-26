---
type: Domain Notes
title: Work Execution Domain Notes
description: This document is the non-normative rationale and decision log for the workexec domain. It expands the decisions in AGENT_GUIDE.md with alternatives, tradeoffs, and validation guidance for architect...
domain: workexec
tags: [domain, workexec, domain-notes]
---

# WORKEXEC_DOMAIN_NOTES.md

## Summary

This document is the non-normative rationale and decision log for the `workexec` domain. It expands the decisions in `AGENT_GUIDE.md` with alternatives, tradeoffs, and validation guidance for architects and auditors. It is intended to make changes reviewable and to keep system-of-record boundaries explicit.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-INVENTORY-001 — SubstituteLink ownership boundary

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: SubstituteLink authoring/admin is not owned by workexec; workexec consumes SubstituteLink for runtime substitution and records substitution history.
- Alternatives considered:
- Option A (chosen): inventory/product owns SubstituteLink
- Pros: clear SoR; reusable rules
- Cons: cross-domain dependency
- Option B: workexec owns SubstituteLink
- Pros: fewer moving parts short-term
- Cons: domain drift; harder reuse
- Reasoning and evidence:
- SubstituteLink is master-data adjacency; runtime substitution is execution behavior.
- Architectural implications:
- Components affected: inventory/product APIs, workexec picker consumption
- Diagram (optional): SubstituteLink (master) -> Picker (workexec) -> Apply -> SubstitutionHistory
- Auditor-facing explanation:
- Inspect SubstituteLink changes in the owning domain; inspect substitution history for runtime usage.
- Migration & backward-compatibility notes:
- If early workexec tables exist for SubstituteLink, migrate into inventory/product and keep read-only views temporarily.
- Governance & owner recommendations:
- Owner: inventory/product domain; workexec as consumer reviewer.

### DECISION-INVENTORY-002 — Canonical Moqui screens/routes

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Align stories to existing screens: `WorkOrderBoard.xml`, `WorkOrderEdit.xml`, `EstimateEdit.xml` in `durion-workexec`; appointment screens in `durion-shopmgr`.
- Alternatives considered:
- Option A (chosen): extend existing screens
- Pros: lowest risk; matches current services
- Cons: may require incremental refactors
- Option B: new UI routes first
- Pros: better UX
- Cons: requires stable REST contracts first
- Reasoning and evidence:
- Existing screens already encode navigation and entity/service usage.
- Architectural implications:
- Keep new workexec UIs adjacent to existing screens for discoverability.
- Auditor-facing explanation:
- Screen artifacts provide traceability to invoked services.
- Migration & backward-compatibility notes:
- Preserve service contracts and auditing semantics when refactoring screens.
- Governance & owner recommendations:
- Owners: workexec component maintainers; shopmgr for appointment screens.

### DECISION-INVENTORY-003 — Identifier handling (opaque IDs)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Treat all IDs as opaque strings; do not enforce UUID/numeric client-side constraints.
- Alternatives considered:
- Option A (chosen): opaque IDs
- Option B: enforce UUID everywhere
- Reasoning and evidence:
- Moqui entities use `type="id"` and IDs can be prefixed strings.
- Architectural implications:
- Prefer pickers/search; avoid free-form ID entry.
- Auditor-facing explanation:
- ID format is not a contract; treat as stable identifiers only.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owners: all UI teams; enforce in UI linting/reviews.

### DECISION-INVENTORY-004 — Work order status taxonomy and “work started”

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: In Moqui `durion-workexec`, started means `WO_IN_PROGRESS` or later; pre-start means `WO_CREATED`/`WO_SCHEDULED`. Other taxonomies must expose `isStarted` to clients.
- Alternatives considered:
- Option A (chosen): status-based gating
- Option B: time-based gating (actualStartTime)
- Reasoning and evidence:
- Status is the authoritative lifecycle signal in current entity model.
- Architectural implications:
- Avoid client-side mapping tables by exposing `isStarted`.
- Auditor-facing explanation:
- Validate that assignment edits are rejected after started.
- Migration & backward-compatibility notes:
- If other services use different statuses, add a translation layer returning `isStarted`.
- Governance & owner recommendations:
- Owner: workexec; coordinate with any upstream SoR for status.

### DECISION-INVENTORY-005 — Assignment vs operational context (SoR + audit)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Operational context is shopmgr SoR and read-only in workexec; overrides are manager-only, audited, and concurrency-safe.
- Alternatives considered:
- Option A (chosen): strict SoR separation
- Option B: copy operational context into workexec and allow edits
- Reasoning and evidence:
- Scheduling integrity requires a single authoritative owner.
- Architectural implications:
- Add an append-only override audit trail; avoid accepting client-supplied actor IDs.
- Auditor-facing explanation:
- Inspect override audit records and confirm no “start snapshot” mutation.
- Migration & backward-compatibility notes:
- safe_to_defer: true for version token until shopmgr provides one.
- Governance & owner recommendations:
- Owners: shopmgr for operational context; workexec for override UX and audit.

### DECISION-INVENTORY-006 — SubstituteLink update semantics and defaults

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: SubstituteLink key fields are immutable; defaults are `priority=100` and `isAutoSuggest=false`; uniqueness is enforced on `(partId, substitutePartId)`; deactivate instead of delete.
- Alternatives considered:
- Option A (chosen): immutable keys + soft deactivate
- Option B: editable keys
- Reasoning and evidence:
- Simplifies idempotency, audit, and uniqueness conflicts.
- Architectural implications:
- UI edit form disables key fields; 409 duplicate returns `existingResourceId`.
- Auditor-facing explanation:
- Verify deactivation does not delete historical substitution usage.
- Migration & backward-compatibility notes:
- If deletes exist, replace with `isActive=false` semantics.
- Governance & owner recommendations:
- Owner: inventory/product; workexec consumer review.

### DECISION-INVENTORY-007 — Substitution picker scope + eligibility source

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Picker supports both WorkOrder and Estimate lines; backend enforces eligibility and returns pricing + `canEnterManualPrice` flags.
- Alternatives considered:
- Option A (chosen): shared picker DTO with `targetType`
- Option B: separate endpoints/DTOs per target
- Reasoning and evidence:
- Ensures consistent UX and avoids duplicate client logic.
- Architectural implications:
- Contract tests for both targets; DB audit record on apply.
- Auditor-facing explanation:
- Confirm apply creates substitution history and eligibility is enforced server-side.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owners: workexec (runtime apply); inventory/pricing (eligibility inputs).

### DECISION-INVENTORY-008 — Part lookup UX contract

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Part selection uses search/picker with pagination; avoid raw ID entry.
- Alternatives considered:
- Option A (chosen): search/picker
- Option B: free-text IDs
- Reasoning and evidence:
- Prevents invalid IDs and reduces errors.
- Architectural implications:
- Provide a standard DTO for part search results.
- Auditor-facing explanation:
- Confirm part selection logs only IDs, not PII.
- Migration & backward-compatibility notes:
- Replace any ID-only UI fields with a picker.
- Governance & owner recommendations:
- Owner: inventory/product UI.

### DECISION-INVENTORY-009 — Dispatch board contract + aggregation behavior

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Board is read-only in v1; exceptions computed server-side; prefer backend aggregation for SLA; UI merges secondary sources in parallel and degrades gracefully.
- Alternatives considered:
- Option A (chosen): degrade gracefully
- Option B: block until all sources succeed
- Reasoning and evidence:
- Dispatch workflow must remain usable during partial outages.
- Architectural implications:
- Stable exception code enum; include `asOf` timestamp in responses.
- Auditor-facing explanation:
- Inspect logs/metrics for partial failures and stale indicators.
- Migration & backward-compatibility notes:
- If moving from Moqui screen to REST, preserve the same exception code semantics.
- Governance & owner recommendations:
- Owner: workexec; consult people/shopmgr as data providers.

### DECISION-INVENTORY-010 — Appointments vs work orders (SoR + link)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Appointments are shopmgr SoR; work orders may reference via `appointmentId`.
- Alternatives considered:
- Option A (chosen): separate appointment entity
- Option B: embed scheduling into work order
- Reasoning and evidence:
- Enables planning without coupling to execution lifecycle.
- Architectural implications:
- Joining requires a shopmgr API or view.
- Auditor-facing explanation:
- Validate that appointment updates are not authored by workexec except via approved integration.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: shopmgr.

### DECISION-INVENTORY-011 — Standard error envelope + duplicate signaling

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Standardize errors to JSON envelope with `code`, `message`, `correlationId`, optional `fieldErrors`, optional `existingResourceId` for duplicates.
- Alternatives considered:
- Option A (chosen): standard envelope
- Option B: framework defaults
- Reasoning and evidence:
- Enables consistent UI parsing and support workflows.
- Architectural implications:
- Bridge layer normalizes Moqui errors where needed.
- Auditor-facing explanation:
- Correlate incidents via `correlationId` across logs.
- Migration & backward-compatibility notes:
- Provide backward-compatible parsing where legacy errors exist.
- Governance & owner recommendations:
- Owner: platform/API governance.

### DECISION-INVENTORY-012 — Idempotency-Key usage for UI mutations

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: UI sends `Idempotency-Key` for create/submit actions and reuses it on retry for the same attempt.
- Alternatives considered:
- Option A (chosen): header-based idempotency
- Option B: no idempotency
- Reasoning and evidence:
- Prevents duplicates under retries/double-click.
- Architectural implications:
- Persist idempotency outcomes server-side.
- Auditor-facing explanation:
- Verify idempotency logs/outcomes for create operations.
- Migration & backward-compatibility notes:
- Add idempotency support to gateway/bridge if missing.
- Governance & owner recommendations:
- Owner: platform/API governance.

### DECISION-INVENTORY-013 — Capability/permission signaling + manual price gating

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Provide a capability signal (user context endpoint or session claims) to drive UI gating; backend remains authoritative. Manual price gating is explicit in picker DTO (`canEnterManualPrice`) and enforced server-side.
- Alternatives considered:
- Option A (chosen): capability flags
- Option B: UI hardcodes roles
- Reasoning and evidence:
- Reduces coupling and prevents accidental unsafe actions.
- Architectural implications:
- Define stable capability schema; apply artifact auth to screens.
- Auditor-facing explanation:
- Confirm 403 responses exist even when UI hides actions.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: security domain; consumers coordinate.

### DECISION-INVENTORY-014 — Audit visibility strategy (substitutes + overrides)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Default UI shows created/updated metadata; optional append-only audit endpoints may be added for substitutes and overrides when required.
- Alternatives considered:
- Option A (chosen): metadata first, audit optional
- Option B: always fetch full audit logs
- Reasoning and evidence:
- Balances operator needs with performance.
- Architectural implications:
- Audit endpoints (if added) must be append-only and PII-safe.
- Auditor-facing explanation:
- Validate audit log immutability.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: each domain for its records; audit domain consult.

### DECISION-INVENTORY-015 — Event ingestion mechanism + failure handling

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Use inbox persistence + async processing with DB idempotency; store failures in Moqui DB for ops review and emit to external DLQ. Protect ingestion with service auth.
- Alternatives considered:
- Option A (chosen): inbox + ops view + DLQ
- Option B: synchronous handling only
- Reasoning and evidence:
- Durable processing and operational visibility are required.
- Architectural implications:
- Add inbox table, failure table, reprocess capability, and alerts.
- Auditor-facing explanation:
- Verify failures are recorded and processing is idempotent.
- Migration & backward-compatibility notes:
- safe_to_defer: true for transport selection until platform standard chosen.
- Governance & owner recommendations:
- Owner: platform/integration; shopmgr consulted.

### DECISION-INVENTORY-016 — Timezone semantics for shop UX

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Display timestamps in user timezone; interpret date-bucket filters in shop timezone when available and label timezone explicitly. If shop timezone is unavailable, use user timezone with explicit labeling.
- Alternatives considered:
- Option A (chosen): user display + shop bucketing
- Option B: always user timezone
- Reasoning and evidence:
- Reduces confusion while preserving operational day boundaries.
- Architectural implications:
- Provide shop timezone source in location/facility config.
- Auditor-facing explanation:
- Validate that board day boundaries are consistent across users.
- Migration & backward-compatibility notes:
- Add timezone field to location/facility entities when available.
- Governance & owner recommendations:
- Owner: shopmgr/location domain.

### DECISION-INVENTORY-017 — Position assignment never defaults the technician

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #1990
- Decision: Bays are pooled — no technician owns a bay, and no technician-to-bay relation exists or will be created. Assigning a workorder to a bay or mobile unit stays entirely silent about its technician, as it does today; the same holds for mobile units. Position (`service_position_assignment`) and technician (`technician_assignment`) are independent: assigning, moving or releasing one never reads or writes the other. `GET /v1/workorders/{workorderId}/position` returning them together is presentation, not a rule.
- Alternatives considered:
- Option A (chosen): position and technician assignment stay fully independent; no defaulting either direction
- Pros: no silent reassignment on repeated saves; matches the pooled-bay reality; no phantom technician-to-bay master data to maintain
- Cons: dispatchers must set the technician explicitly even after placing a workorder at a position
- Option B: default the technician from whoever is staffed at the position
- Pros: one fewer dispatcher click in the common case
- Cons: bays are pooled so there is no "whoever is staffed at the position" person to default from; re-sending a workorder's current position is deliberately a no-op that writes no history, so defaulting would silently reassign the technician on a repeated save
- Reasoning and evidence:
- Technician-to-bay master data in pos-people is a settled "will not model"; this decision does not re-open it.
- Position and technician are two independent per-workorder facts owned by pos-workorder; combining them at read time (the `GET .../position` response) is a presentation convenience, not a coupling of the underlying writes.
- Architectural implications:
- Components affected: `ServicePositionServiceImpl`, `WorkorderTechnicianServiceImpl` (or equivalents) in pos-workorder stay decoupled; no new technician-to-bay or technician-to-mobile-unit table is introduced.
- Auditor-facing explanation:
- Confirm that assigning/moving/releasing a position never writes `technician_assignment`, and that assigning/reassigning a technician never writes `service_position_assignment`. Confirm no technician-to-bay master-data table exists in pos-people.
- Migration & backward-compatibility notes:
- None; this is existing behaviour being recorded, not a change.
- Governance & owner recommendations:
- Owner: pos-workorder (position and technician assignment); pos-people is not to introduce technician-to-bay master data.

### DECISION-INVENTORY-018 — A technician must be staffed at the workorder's site; no override

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #1990; ADR-0044
- Decision: Staffing is pos-people's fact. A technician who is going to work at a different site is given a staffing assignment at that site first; workexec does not record an assignment that contradicts the staffing record. Assign (POST) and reassign (PUT) `/v1/workorders/{workorderId}/technician` refuse with 422 `TECHNICIAN_NOT_STAFFED_AT_SITE` when the technician has one or more ACTIVE staffing rows effective today and none of them is at the workorder's site. "Staffed at a site" means at least one `ext_people_staffing_assignment` row with `status = 'ACTIVE'`, `locationId` equal to the site, and effective today; `is_primary` is not considered, and role is not filtered on. A technician with no active staffing rows at all is allowed — the check refuses only on a positive contradiction, never on absence. "The workorder's site" is resource-specific, not always `Workorder.locationId` directly: for a `MOBILE_UNIT` position, the site to check is the unit's own `ExtMobileUnitReplica.baseLocationId`; for `BAY`, `HOLD`, and a workorder with no position yet, it is the workorder's own `locationId` (`TechnicianAssignmentServiceImpl.resolveSiteId`). `ServicePositionServiceImpl.requireSameSite` forces a mobile unit's base site and the workorder's `locationId` to already agree, so today the branch is a no-op — both paths resolve to the same value. It is written as a real branch anyway, not collapsed to `workorder.getLocationId()`, so the check keeps working rather than silently drifting the day a mobile workorder carries the customer's site instead of the unit's.
- Alternatives considered:
- Option A (chosen): refuse on positive contradiction only, no override lever
- Pros: keeps `technician_assignment` history consistent with `ext_people_staffing_assignment`; tolerant of replica lag, bootstrap and DLQ backlog (a technician with no replicated staffing rows is not blocked); consistent with ADR-0044 R3 (refuse only on a known contradiction, never on absence of data)
- Cons: a shop cannot assign a technician who is staffed elsewhere without first correcting the staffing record in pos-people
- Option B: an override permission or reason code to bypass the check
- Pros: lets a dispatcher force an assignment in an emergency without waiting on pos-people
- Cons: writes into `technician_assignment` history a statement contradicting `ext_people_staffing_assignment` — a second, divergent account of where a person works, held by a module (pos-workorder) that does not own that fact; violates ADR-0044 R6 (no module keeps its own contradicting copy of another module's owned fact); the correct lever for a lagging replica is operational (ADR-0044 §4 replay/reconciliation), not a permission
- Reasoning and evidence:
- `is_primary` is not considered because at most one primary per person is enforced in pos-people; requiring primary would make "staffed at two sites this week" (a real, supported case) impossible to assign in either site.
- Role is not filtered on because it is free text in `ext_people_staffing_assignment`; filtering on it would refuse real technicians over a typo in a role string.
- Absence of any active staffing row is explicitly allowed through (not refused) because replica lag, bootstrap and DLQ backlog must not take a shop offline (ADR-0044 R3): the check is a contradiction check, not a presence check.
- The site is resolved per resource type rather than always read off `Workorder.locationId` because a mobile unit's true site is the unit's, not necessarily the workorder row's; `resolveSiteId` is written as the real `MOBILE_UNIT`-vs-other branch even though `requireSameSite` currently makes the two values equal, so the distinction is not lost if that equality is ever relaxed.
- Architectural implications:
- Components affected: assign/reassign technician endpoints (`POST`/`PUT /v1/workorders/{workorderId}/technician`) in pos-workorder gain the `TECHNICIAN_NOT_STAFFED_AT_SITE` 422 check against the `ext_people_staffing_assignment` replica; `TechnicianAssignmentServiceImpl.resolveSiteId` resolves the site from `ExtMobileUnitReplica.baseLocationId` for `MOBILE_UNIT`, else `Workorder.locationId`; no synchronous call to pos-people is introduced.
- Auditor-facing explanation:
- Confirm the check queries only the local `ext_people_staffing_assignment` replica (never a live RestClient call to pos-people), evaluates ACTIVE + site match + effective-today, ignores `is_primary` and role, and passes (does not refuse) when zero active rows exist for the technician. Confirm the site comparison is resource-specific: `MOBILE_UNIT` compares against `ExtMobileUnitReplica.baseLocationId`, and `BAY`/`HOLD`/no-position compare against `Workorder.locationId` — not a flat read of the workorder's `locationId` in every case.
- Migration & backward-compatibility notes:
- None; implemented by issue #1990.
- Governance & owner recommendations:
- Owner: pos-workorder for the check; pos-people remains sole owner of the staffing fact via `ext_people_staffing_assignment`. No override permission is to be added as a follow-up.

### DECISION-INVENTORY-019 — pos-people owns technician-to-SITE staffing, not technician-to-bay or technician-to-mobile-unit

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #1990; ADR-0044
- Decision: pos-people owns technician staffing, and the relation it owns is technician-to-SITE — not technician-to-bay and not technician-to-mobile-unit. pos-location keeps bay and mobile-unit identity and capability (asset configuration, not staffing). pos-workorder owns only per-workorder facts: `technician_assignment` and `service_position_assignment`. Read across the boundary is the `ext_people_staffing_assignment` replica only, per ADR-0044 R1/R3/R6 — no synchronous RestClient to pos-people.
- Alternatives considered:
- Option A (chosen): three-way split — pos-people owns technician-to-site staffing; pos-location owns bay/mobile-unit identity and capability; pos-workorder owns per-workorder technician and position facts, reading staffing only via the `ext_people_staffing_assignment` replica
- Pros: each module owns exactly one fact; no module holds a synchronous dependency on another for a fact it doesn't own; matches ADR-0044's event-only domain walls
- Cons: workexec's site-staffing check is only as fresh as replica replay, not real-time
- Option B: model technician-to-bay or technician-to-mobile-unit relations directly (in pos-people or pos-location)
- Pros: would let a defaulting or override feature (rejected in DECISION-INVENTORY-017/018) read a concrete assignee
- Cons: bays are pooled — there is no technician-to-bay fact to model (settled "will not model"); conflates asset configuration (pos-location's fact) with staffing (pos-people's fact)
- Reasoning and evidence:
- ADR-0044 R1 (each fact has exactly one owning module), R3 (consumers tolerate absence, never treat missing replicated data as a contradiction) and R6 (no module keeps a second, divergent copy of a fact it does not own) together define this split.
- Architectural implications:
- Components affected: `ext_people_staffing_assignment` (pos-workorder's replica of pos-people's `staffing.events.v1` or equivalent), pos-location's bay/mobile-unit entities, pos-workorder's `technician_assignment` and `service_position_assignment` tables.
- Auditor-facing explanation:
- Confirm no direct RestClient call from pos-workorder to pos-people for staffing lookups; confirm pos-location has no technician-staffing columns/tables; confirm pos-people has no bay- or mobile-unit-scoped tables.
- Migration & backward-compatibility notes:
- None; this is the settled ownership boundary, not a change.
- Governance & owner recommendations:
- Owner: pos-people (technician-to-site staffing, system of record); pos-location (bay/mobile-unit identity and capability); pos-workorder (per-workorder technician and position facts, replica-only read of staffing).

### DECISION-INVENTORY-020 — No cap on concurrent workorders per technician

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #1990
- Decision: A technician may hold any number of open or in-progress workorders; there is no per-technician WIP cap. The scarce resource is the clock, not the assignment, and it is already correctly constrained: `WorkexecTimeTrackingServiceImpl.startTimer` refuses a second concurrent timer for the same technician with 409 `TIMER_ALREADY_ACTIVE`, keyed on the technician rather than the workorder — "one job at a time" is already true and expressed at the right level.
- Alternatives considered:
- Option A (chosen): no assignment-count cap; concurrency is constrained only at the timer (one active timer per technician)
- Pros: dispatchers can hold a technician against multiple vehicles awaiting parts without releasing them; the single-technician-of-record invariant (DECISION-INVENTORY-021) is preserved per workorder while the technician's real-time attention is still bounded by the timer check
- Cons: a technician's open-workorder count is not itself a dispatch signal; overload must be read from the timer/queue state, not an assignment count
- Option B: a per-technician WIP cap on open/in-progress workorder assignments
- Pros: caps how many jobs a technician is nominally responsible for at once
- Cons: forces dispatchers to release technicians from `AWAITING_PARTS` vehicles to take the next job, destroying the accountability chain the single-technician invariant was built for
- Reasoning and evidence:
- `WorkexecTimeTrackingServiceImpl.startTimer` already enforces "one job being actively worked at a time" by refusing a second concurrent timer per technician (409 `TIMER_ALREADY_ACTIVE`); this is the correct level for the constraint because it targets the technician's actual attention, not their backlog of assigned-but-parked jobs.
- Architectural implications:
- Components affected: none required; no new cap is added to `technician_assignment` creation.
- Auditor-facing explanation:
- Confirm no code path limits the count of open `technician_assignment` rows per technician; confirm `startTimer` remains keyed on technician id, not workorder id, for its concurrency check.
- Migration & backward-compatibility notes:
- None; this is existing behaviour being recorded, not a change.
- Governance & owner recommendations:
- Owner: pos-workorder. A per-technician WIP cap is a settled "will not add"; do not re-open it as a follow-up.

### DECISION-INVENTORY-021 — One technician per workorder, one technician per mobile unit; no crew

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #1990; related #1983, #1984, #1985
- Decision: One technician per workorder, and one technician per mobile unit. There is no crew: no lead-plus-helpers, no LEAD/ASSIST roles in workexec, no crew table. `technician_assignment_one_current_uniq` means "the technician **of record**", full stop. One technician per mobile unit needs no new schema: `workorder_open_position_uniq` gives a mobile unit at most one open workorder, and `technician_assignment_one_current_uniq` gives that workorder at most one current technician. The leak paths are closed: `ServicePositionServiceImpl.releaseOnClose` clears the position on close and the index's predicate excludes closed rows; `HOLD` is excluded from the index and its `resource_id` is forced to the workorder's own site so it can never collide with a mobile unit; a null `resource_id` holds no unit. Mobile-unit exclusivity is deliberate and stays — a van works one open workorder at a time, and the accepted consequence is that a route is worked one job at a time, opened sequentially.
- This is narrower than "workexec never carries more than one technician id anywhere," and that narrower claim would be false: `Workorder.mechanic_ids` (#1658), exposed as `assignedMechanics` on `OperationalContextResponse`/`OperationalContextOverrideRequest`, is a legacy multi-valued field, still written today by the shopmgmt-sourced assignment-context event and by `overrideOperationalContext`. Per #2015, `WorkorderFactPublisher` reconciles the two sources rather than treating them as competing: the published `mechanicIds` fact is the current `technician_assignment` first, then any `mechanic_ids` entry not already named. `mechanic_ids` is not a crew model, confers no second technician of record, and this decision does not permit building anything new on it — this decision, and `technician_assignment_one_current_uniq`, govern who the technician of record is; `mechanic_ids`/`assignedMechanics` is bookkeeping this decision constrains, not an exception to it.
- Alternatives considered:
- Option A (chosen): single technician of record per workorder and per open mobile-unit slot, enforced by `technician_assignment_one_current_uniq` and `workorder_open_position_uniq`; no crew concept in workexec
- Pros: unambiguous accountability chain; no new schema needed for mobile-unit exclusivity, it falls out of the existing uniqueness constraints once the close/HOLD/null leak paths are closed
- Cons: a mobile unit works one open workorder at a time — a multi-stop route is opened sequentially, not in parallel
- Option B: relax `ResourceType.isExclusive()` / `workorder_open_position_uniq` to let a mobile unit hold multiple open workorders, or introduce a crew table with LEAD/ASSIST roles
- Pros: could model a route worked with parallel open jobs, or a helper working alongside the technician of record
- Cons: breaks the single-technician-of-record invariant that the accountability chain depends on; a workorder crew table is a settled "will not model" and is not to be re-opened as a follow-up
- Reasoning and evidence:
- Single assignment is a rule about the technician OF RECORD, not about whose time is logged: `WorkorderLaborEntry.technicianId` is per-person, and the `workorder:labor:add_on_behalf` path means more than one person's labor can legitimately land on a single-assignment workorder; `TravelSegment.technicianId` is per-segment and not tied to the workorder's current technician. Both are existing, deliberate behaviour and do not contradict "one technician of record."
- `mechanic_ids`/`assignedMechanics` is not an exception either, for the same reason: it is a legacy multi-valued list (#1658) that a workorder can carry alongside its single `technician_assignment`, but nothing reads it as naming a second technician of record. `BACKEND_CONTRACT_GUIDE.md:116` ("the location and mechanics are applied") describes exactly this legacy field being written by the inbound shopmgmt assignment fact, not a second technician-of-record mechanism; `BACKEND_API_REFERENCE.generated.md:2773,2799-2802` documents the same field on `getOperationalContext`/`overrideOperationalContext`, including `overrideOperationalContext`'s informal "re-slot ... to a different bay, crew, or location" phrasing — that endpoint description's use of "crew" is colloquial for this legacy list, not a crew entity, and does not conflict with "no crew table in workexec."
- Architectural implications:
- Components affected: `technician_assignment_one_current_uniq`, `workorder_open_position_uniq`, `ServicePositionServiceImpl.releaseOnClose`, `ResourceType.isExclusive()`. None of these are to be relaxed. `Workorder.mechanic_ids`/`OperationalContext{Response,OverrideRequest}.assignedMechanics` and `WorkorderFactPublisher`'s `mechanicIds` reconciliation (#2015) continue as-is; no schema change is implied by this decision.
- Auditor-facing explanation:
- Confirm `releaseOnClose` runs on every close path and the partial index predicate excludes closed rows; confirm `HOLD` rows always carry the workorder's own site as `resource_id`; confirm a null `resource_id` is never treated as holding a mobile unit; confirm no crew/LEAD/ASSIST table exists in pos-workorder. Confirm no code path treats an entry in `mechanic_ids`/`assignedMechanics` other than the current `technician_assignment` as authoritative for "who is the technician" (e.g. permission checks, `startWorkorder`, timer ownership) — the list is display/legacy bookkeeping, not a second source of assignment truth.
- Migration & backward-compatibility notes:
- None; this is existing behaviour being recorded, not a change. Do not relax `ResourceType.isExclusive()` or `workorder_open_position_uniq`. Do not build new features on `mechanic_ids`/`assignedMechanics`, and do not treat it, or shopmgmt's `AssignmentMechanic(LEAD|ASSIST)` (DECISION-INVENTORY-022), as authority to give a workorder a second technician of record.
- Governance & owner recommendations:
- Owner: pos-workorder. A workorder crew table is a settled "will not model"; do not re-open it as a follow-up. `mechanic_ids`/`assignedMechanics` is legacy (#1658); a future story that wants a real multi-mechanic model must open a new decision record, not extend this field's meaning.

### DECISION-INVENTORY-022 — Assignment-ownership boundary: pos-shop-manager plans, pos-workorder records

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue #2000; related #70, #134, ADR-0006, ADR-0044
- Decision: pos-shop-manager owns the PLANNED assignment — who is expected to work an appointment, including any multi-mechanic shape it keeps for scheduling purposes (`Assignment` + `AssignmentMechanic(role LEAD|ASSIST)`). pos-workorder owns the ACTUAL CURRENT technician of record on the workorder, and it is exactly one person (DECISION-INVENTORY-021). shopmgmt's `AssignmentUpdatedEvent` is an INPUT, never a second system of record: workexec may use it to inform an assignment, but that does not make workexec's technician a projection of shopmgmt's, and a multi-mechanic planned assignment does not become multiple technicians on the workorder. `AssignmentMechanic(LEAD|ASSIST)` STOPS AT THE BOUNDARY and is not to be mirrored into pos-workorder — decision 021 gives workexec no crew concept, and without this written down a future dispatch or scheduling story could re-import crew through the appointment and quietly break the single-technician invariant.
- Alternatives considered:
- Option A (chosen): planned (shopmgmt) vs actual-current (workexec) are two distinct facts; `AssignmentUpdatedEvent` is consumed as an input signal only, never mirrored 1:1, and `AssignmentMechanic` roles stop at the boundary
- Pros: preserves each module's single-technician / multi-mechanic model as appropriate to what it owns; keeps ADR-0044's event-only domain walls (an event is an input, not a second SoR); protects the single-technician invariant from a future story that naively projects the event
- Cons: workexec's technician of record can, briefly, differ from shopmgmt's planned assignment until whatever process (manual dispatch action, or a future rule) reconciles them — this is accepted, not a defect
- Option B: treat workexec's `technician_assignment` as a live projection of shopmgmt's `Assignment`/`AssignmentMechanic`, mirroring LEAD/ASSIST into workexec
- Pros: keeps the two systems visibly "in sync" without a separate dispatch action
- Cons: makes workexec's technician a second, derived copy of a fact shopmgmt owns (violates ADR-0044 R1/R6); a multi-mechanic planned assignment would have nowhere correct to go on a single-technician workorder, forcing either data loss or a crew concept workexec has deliberately declined to build (DECISION-INVENTORY-021)
- Reasoning and evidence:
- Per [ADR-0006](../../../docs/adr/0006-workexec-domain-ownership-boundaries.adr.md), assignment/scheduling context is shopmgmt's source of record; workexec's execution-time facts (including the current technician) are workexec-owned. `AssignmentUpdatedEvent` crossing that boundary is governed by [ADR-0044](../../../docs/adr/0044-platform-event-only-domain-walls.adr.md): an event is an input a consumer may act on, not a hand-off of ownership.
- Nothing is broken today: workexec does not read shopmgmt's mechanic rows, and `WorkorderAssignmentEventListener` already consumes `AssignmentUpdatedEvent` as an input only. This decision record changes no behaviour or schema; it forecloses a specific future mistake.
- Whether pos-shop-manager keeps a multi-mechanic planned assignment (`AssignmentMechanic` with LEAD/ASSIST) is that domain's call, and this ruling holds either way — it does not change the `AssignmentMechanic` model, only what may cross into pos-workorder.
- Architectural implications:
- Components affected: `WorkorderAssignmentEventListener` in pos-workorder (stays input-only; must not begin writing `AssignmentMechanic` roles into `technician_assignment`); no schema change in either module.
- Auditor-facing explanation:
- Confirm `WorkorderAssignmentEventListener` never persists a LEAD/ASSIST role or a second technician row per workorder from `AssignmentUpdatedEvent`; confirm pos-workorder has no column or table shaped like `AssignmentMechanic`.
- Migration & backward-compatibility notes:
- None; this is a decision record with no behaviour or schema change (issue #2000).
- Governance & owner recommendations:
- Owner: pos-shop-manager (planned assignment, including any multi-mechanic shape); pos-workorder (actual current technician of record, exactly one person). Any future dispatch/scheduling story that wants to reflect LEAD/ASSIST in workexec must open a new decision record, not silently mirror the event.

### DECISION-INVENTORY-023 — Workorder transfer between locations: same workorder, one operation, site owned by workexec

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q1, Q8); related #2245, DECISION-SHOPMGMT-023, DECISION-SHOPMGMT-024, DECISION-SHOPMGMT-025
- Decision: A workorder may be transferred from one location to another. Transfer is needed, and one operation covers every case; the cases differ only by reason code (DECISION-INVENTORY-028): the customer asks for the work to be done at another shop (`CUSTOMER_REQUEST`); the source site lacks capacity (`CAPACITY`); the source site lacks the equipment the job needs, e.g. no bay that is active, equipped or rated for the vehicle's duty class under DECISION-SHOPMGMT-021/022 (`EQUIPMENT`); and a mobile job is served by another depot's mobile unit, which DECISION-SHOPMGMT-023 routes as a transfer rather than a cross-location dispatch (`MOBILE_DEPOT`). Transfer changes the site **on the same workorder** (issue option Q8(a)); there is no close-and-reissue. Six rules follow:
  1. **Same identity.** The workorder keeps its id, `workorderNumber`, estimate link, customer approval (`approvalId`, `approvedAt`, signature), fleet authorization, notes, line items and all histories. Only the site changes, and the transfer's own consequences (DECISION-INVENTORY-024 to -027) are applied in the same transaction.
  2. **Both site fields move together.** `Workorder.shopId` and `Workorder.locationId` are the same concept (`WorkorderServiceImpl.doCreateWorkorder` seeds both from one value) and a transfer writes both to the target. After a transfer they are equal; a divergence is a defect.
  3. **The site is workexec's execution fact, changed only by transfer.** Where the work is performed is a fact of the workorder, owned by pos-workorder. It changes only through the transfer operation. This narrows DECISION-INVENTORY-005 for the site: shopmgmt still owns the operational context it plans (appointment, planned resource, planned mechanics — DECISION-INVENTORY-022), but its inputs no longer move a workorder between sites.
  4. **Close the two paths that change the site today without the transfer rules.** `WorkorderServiceImpl.overrideOperationalContext` refuses a body `locationId` that differs from the workorder's current site with 422 `WORKORDER_TRANSFER_REQUIRED` (omitted or equal is accepted, and it re-slots within the site as before). `WorkorderServiceImpl.handleAssignmentUpdated` (the `AssignmentUpdatedEvent` input) never writes the site: an inbound assignment whose `locationId` differs from the workorder's is dropped whole — position and mechanics included, because they describe a plan at another site — with a warning log and a counter, in the same log-and-swallow style it already uses for an occupied or inactive position.
  5. **Direction.** A workorder transfer causes the appointment change: pos-shop-manager cancels the linked appointment at the source, and the target books a new one (DECISION-SHOPMGMT-025). An appointment never causes a workorder transfer. An appointment's location is fixed (DECISION-SHOPMGMT-024), and booking a workorder's appointment at another location is refused on the shopmgmt side, so nothing on that side moves a workorder. Only someone with `workorder:workorder:transfer` does.
  6. **Out of scope.** Transfer after work has started (DECISION-INVENTORY-024); a linked reissue at the new site; bulk transfer; giving a workorder with no site its first site (not a transfer — the source-leg scope check fails closed on a null site, as `overrideOperationalContext` already does); cross-tenant moves (impossible under ADR-0062 row-level security — a location in another tenant is unknown to the `ext_location` replica).
- Alternatives considered:
- Option A (chosen): change the site on the same workorder, through one dedicated operation, and close the other site-writing paths
- Pros: one workorder number for the customer from quote to invoice; the approval, signature and fleet authorization already captured stay attached without being copied; each downstream rule (position, technician, parts, tax) is handled once, in the operation that owns the change; the audit shows one move rather than a cancellation and an unrelated creation
- Cons: every rule that keys on the site must tolerate the site changing during the workorder's life, which is why transfer is restricted to before work starts (DECISION-INVENTORY-024)
- Option B: close the workorder at the old site and reissue a new one at the new site, linked
- Pros: every existing site-keyed rule stays true for each workorder, because each workorder keeps one site for life
- Cons: the old workorder must end somehow — `CANCELLED` means "not done" to billing, shopmgmt (it frees the appointment slot) and reporting, and no other terminal status fits; the customer approval would have to be copied or re-captured, which is inventing approval semantics; the customer sees two workorder numbers for one job
- Option C: no transfer; staff re-enter the work at the other site by hand
- Pros: no new code
- Cons: the manual re-entry duplicates the vehicle, customer and lines with no link, loses the approval, and leaves reservations and placements at the old site — the risk the issue names
- Reasoning and evidence:
- The issue states that a workorder's site is fixed at creation. The code disagrees. Two paths already rewrite `Workorder.locationId` without any transfer rule: `overrideOperationalContext` (before work starts, gated at both ends by `workorder:operationalContext:override`) and `handleAssignmentUpdated` (for any workorder that is not locked, including one in `WORK_IN_PROGRESS`). Both write `locationId` and leave `shopId` unchanged. The workorder then answers two different sites. Position, technician staffing, pick scope, the dispatch board and the invoice read `locationId` (`ServicePositionServiceImpl.resolvePosition`, `TechnicianAssignmentServiceImpl.resolveSiteId`, `WorkorderPickFacadeServiceImpl`, `DashboardServiceImpl`, `WorkorderInvoiceServiceImpl.generateInvoice`). Location scope, parts reservation and pick-list demand, WIP, job-time totals and the published `shopId` read `shopId` (`ServicePositionServiceImpl.requireLocationScope`, `WorkorderPartUsageServiceImpl`, `PromotedWorkorderDemandPublisher`, `WipServiceImpl`, `WorkexecTimeTrackingServiceImpl`, `WorkorderFactPublisher`). Rules 2 to 4 close that gap. Transfer does not create it.
- The workorder number is allocated per tenant and year (`DocumentNumberAllocator`, #2150), not per location, so it stays valid when the site changes. The estimate number is unique per `(locationId, estimateNumber)`, which is one reason the estimate itself does not move (DECISION-INVENTORY-025).
- Architectural implications:
- Components affected: pos-workorder gains `POST /v1/workorders/{workorderId}/transfer` (body `toLocationId`, `reasonCode`, `note`; `Idempotency-Key` honoured per DECISION-INVENTORY-012), a transfer service that applies DECISION-INVENTORY-024 to -028 in one transaction, and an append-only `workorder_location_transfer` table (DECISION-INVENTORY-028). `overrideOperationalContext` gains the `WORKORDER_TRANSFER_REQUIRED` refusal. `handleAssignmentUpdated` stops writing `locationId`. Contract chain: controller OpenAPI annotations, regenerated `openapi.yaml`, Angular SDK, and the `API Artifacts Sync` workflow. The pos-workorder README error-code table gains every new code in the same pull request.
- Auditor-facing explanation:
- Confirm that no code path other than the transfer service writes `Workorder.locationId` or `Workorder.shopId` after creation. Confirm that every transfer leaves `shopId = locationId`. Workorders that diverged before this rule are found with `SELECT id, shop_id, location_id, status FROM workorder WHERE shop_id IS DISTINCT FROM location_id;` — after the change, only rows written by the retired paths should appear.
- Migration & backward-compatibility notes:
- Pre-production: no shim. Rows the retired paths left with `shop_id <> location_id` are a one-off data correction (follow-up). `overrideOperationalContext` callers that used it to change the site move to the transfer endpoint.
- Governance & owner recommendations:
- Owner: pos-workorder (the transfer operation and the site fact). pos-shop-manager owns what happens to the appointment (DECISION-SHOPMGMT-024, DECISION-SHOPMGMT-025). A linked reissue, or transfer after work has started, needs a new clarification. Do not extend this decision to cover them.

### DECISION-INVENTORY-024 — Transfer is allowed only before work starts; the position and technician are released

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q2; workexec side of Q3)
- Decision: A workorder may be transferred only before work starts (issue option Q2(a)), and never once any time has been recorded against it. The checks run after the 404 and the location-scope checks (DECISION-INVENTORY-028), in this order:
  1. `CANCELLED`, or `COMPLETED` and not reopened (`Workorder.isLocked()`): 409 `WORKORDER_CLOSED` (existing code).
  2. Any other status outside `DRAFT`, `APPROVED` or `ASSIGNED`, or `workStartedAt` set: 409 `WORKORDER_TRANSFER_NOT_ALLOWED`. This covers `WORK_IN_PROGRESS`, `AWAITING_PARTS`, `AWAITING_APPROVAL` (reachable from `APPROVED`, so it is refused even before work starts: resolve the pending approval first), `READY_FOR_PICKUP`, and a reopened `COMPLETED` workorder. These are refusals on the target's lifecycle status, so ADR-0017 §2 gives them 409.
  3. Target location unknown to `ext_location`, or equal to the current site: 422 `WORKORDER_TRANSFER_LOCATION_INVALID`. Target known but `active = false`: 422 `WORKORDER_TRANSFER_LOCATION_INACTIVE`. The target must be known because its tax address, position replicas and staffing rows all key on it. This follows the `SERVICE_POSITION_INVALID` / `SERVICE_POSITION_INACTIVE` split (#2001).
  4. A change request in `AWAITING_ADVISOR_REVIEW` (the same gate `startWork` applies): 422 `WORKORDER_TRANSFER_CHANGE_REQUEST_PENDING`.
  5. Any recorded time — a `WorkorderLaborEntry`, a `WorkSession` or a `TravelSegment` that references the workorder, in any status, active timers included: 422 `WORKORDER_TRANSFER_TIME_RECORDED` (DECISION-INVENTORY-027).
  6. Parts physically in hand at the source: 422 `WORKORDER_TRANSFER_PARTS_IN_HAND` (DECISION-INVENTORY-026).
- Effects of a transfer that passes the checks, in the same transaction:
  - **Position released.** The current `BAY`, `MOBILE_UNIT` or `HOLD` position is released through `ServicePositionService` with reason `Workorder transferred to <toLocationId>`, which leaves the normal history row. A source-site `HOLD` cannot stay, because a `HOLD`'s `resource_id` must equal the workorder's own site. Nothing is carried over to the target: a source bay or unit fails `requireSameSite` there (`SERVICE_POSITION_INVALID`) by construction. The workorder arrives unplaced.
  - **Technician released.** The current `technician_assignment` is ended with the same reason. Staffing is site-specific (DECISION-INVENTORY-018), so the technician of record at the source is not assumed to be valid at the target. The target dispatcher assigns anew through the normal endpoints, subject to the `TECHNICIAN_NOT_STAFFED_AT_SITE` check. The same person may be reassigned if they pass it. The legacy `mechanic_ids` list is cleared with it (DECISION-INVENTORY-021).
  - **Status.** No new status is added and the transfer is not a status. `DRAFT` and `APPROVED` stay as they are. `ASSIGNED` returns to `APPROVED` through the existing `ASSIGNED → APPROVED` transition (`WorkorderStateMachine.reconcileAssigned`, #2011), recorded in `WorkorderStateTransition` with the transfer as its reason.
  - **Schedule.** `scheduledDate` is kept. Whether the date still holds at the target is settled when the target books its new appointment (DECISION-SHOPMGMT-025 rule 4), and a placement at the target re-dates only an undated workorder (#2002).
  - **Appointment (shopmgmt's, cross-referenced).** pos-workorder does not author or change appointments (DECISION-INVENTORY-010). It publishes the transfer fact (DECISION-INVENTORY-028). On that fact, pos-shop-manager ends the linked appointment at the source (`CANCELLED`, reason `WORKORDER_TRANSFERRED`), and the target advisor books a new appointment under every target rule (DECISION-SHOPMGMT-024, DECISION-SHOPMGMT-025). The transfer never waits on, or is refused because of, an appointment: pos-workorder cannot see appointments (ADR-0044). The workexec side assumes only that shopmgmt never sends an `AssignmentUpdatedEvent` naming the source site after a transfer. If one arrives, it is dropped (DECISION-INVENTORY-023 rule 4).
- Alternatives considered:
- Option A (chosen): before work starts only, and no recorded time; release the position and technician
- Pros: nothing site-bound has been spent yet — no labour, no parts in hand — so the transfer only moves plans, and plans can be released and remade; the rule matches the existing "work started locks the operational context" gate in `overrideOperationalContext` (`workStartedAt != null`), and DECISION-INVENTORY-004's rule that assignment edits are refused once work has started
- Cons: a vehicle that must move after teardown cannot be transferred; the shop finishes or cancels the workorder at the source under the existing lifecycle rules and quotes the rest at the target
- Option B: any time before completion
- Pros: covers the towed-after-teardown case
- Cons: clocked labour, consumed parts and completed lines at the source would ride along to the target. That forces decisions on payroll attribution, partial completion and split invoicing. Each is a mandatory clarification trigger for this domain, and the owner has not answered any of them. It would also pull every in-progress sub-status into the transfer rules.
- Option C: not supported
- Pros: nothing to build
- Cons: DECISION-SHOPMGMT-023 routes cross-depot mobile work to a transfer; without one that work has no path
- Reasoning and evidence:
- `workStartedAt` alone does not prove that no work has happened. `WorkexecTimeTrackingServiceImpl.TIMER_ELIGIBLE_STATUSES` accepts a timer on an `APPROVED` or `ASSIGNED` workorder, `WorkorderLaborServiceImpl.LABOR_ALLOWED_STATUSES` accepts labour on `ASSIGNED`, and `workorder.statemachine.autoStart.onFirstLaborEntry=false` means neither sets `workStartedAt`. Rule 5 therefore checks the time records themselves.
- Keeping the technician when they are also staffed at the target would save a click, but it would make the transfer branch on a replica read. Releasing both halves is deterministic. The target's own dispatcher reassigns under the check that already exists.
- Architectural implications:
- Components affected: the transfer service in pos-workorder; `ServicePositionService` (release with reason); `TechnicianAssignmentService` (release with reason); `WorkorderStateMachine.reconcileAssigned`; read-only checks on `WorkorderLaborEntryRepository`, `WorkSessionRepository`, `TravelSegmentRepository`, `ChangeRequestRepository`, `ExtLocationReplicaRepository`. New error codes: 409 `WORKORDER_TRANSFER_NOT_ALLOWED`; 422 `WORKORDER_TRANSFER_LOCATION_INVALID`, `WORKORDER_TRANSFER_LOCATION_INACTIVE`, `WORKORDER_TRANSFER_CHANGE_REQUEST_PENDING`, `WORKORDER_TRANSFER_TIME_RECORDED`, `WORKORDER_TRANSFER_PARTS_IN_HAND`, `WORKORDER_TRANSFER_REQUIRED`.
- Auditor-facing explanation:
- For every `workorder_location_transfer` row, confirm that the workorder had no labour entry, work session or travel segment created before `transferred_at`; that its status at that instant (from `work_order_state_transitions`) was `DRAFT`, `APPROVED` or `ASSIGNED`; and that a position release and a technician release (where one was current) carry the same timestamp and the transfer reason.
- Migration & backward-compatibility notes:
- None; new operation.
- Governance & owner recommendations:
- Owner: pos-workorder. Relaxing the "before work starts" rule requires a new clarification that answers the partial-completion, payroll and split-billing questions first.

### DECISION-INVENTORY-025 — Transfer keeps quoted prices; tax follows the new location at invoicing; no re-approval

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q4)
- Decision: Quoted prices are kept and only tax follows the new location (issue option Q4(b)). Five rules follow:
  1. **Prices are kept.** Every existing service and part line keeps its snapshotted `unitPrice`, `lineTotal` and `taxCode`, and every labour line keeps its rate snapshot (`rateHourly`, `rateBaseHourly`, `rateScope`, `rateId`, `rateAdjustmentCodes`). Transfer never re-prices a line.
  2. **Tax follows the site that performs the work, at invoicing, as today.** pos-invoice computes the authoritative tax when it finalizes the invoice. It uses the invoice's `locationId` (`InvoiceTaxCalculator.calculate` → `LocationReferenceService.resolveTaxAddress`), and that value is the workorder's `locationId` when the invoice is requested (`WorkorderInvoiceServiceImpl.generateInvoice`). After a transfer that is the new location, with no new code. pos-workorder computes no tax on the workorder.
  3. **The estimate does not move.** The approved estimate keeps its `locationId`, totals and tax figure as the record of what was quoted and approved, where. It is not recalculated: `calculateEstimateTaxesAndTotals` is DRAFT-only, and an approved estimate is not edited.
  4. **No re-approval.** The customer approved scope and price, and neither changes. A difference in tax is statutory, not a price the shop sets, and the estimate's tax was already an estimate (it can be `taxPending`). The customer is told at the transfer that tax will be calculated for the new location — a UI obligation, not a new approval step. If the shop wants to charge the target location's prices, that is not a transfer: re-quote the work at the target under the normal estimate flow.
  5. **What comes after the transfer is priced and approved at the new location.** Lines added later — through a change request or otherwise — are priced with the target location's rates, and later approvals use the `ApprovalConfiguration` that applies to the target location. Approvals captured before the transfer stay valid. Customer deposits are party-scoped in pos-invoice (`DepositCredit.partyId`, no location), so they follow the customer and apply to the invoice as today.
- Alternatives considered:
- Option A (chosen): keep quoted prices; tax at the new location; no re-approval
- Pros: the customer pays the price they approved; no approval loop is invented; tax lands in the jurisdiction where the service is performed, through the code path that already does this
- Cons: the invoice total can differ from the approved estimate total by the tax difference; the target location honours a price set by the source
- Option B: re-price at the new location, with re-approval when the total changes
- Pros: every line carries the performing location's own price
- Cons: needs a "re-approval of an approved workorder" flow that does not exist (approval today is estimate approval plus change requests for added scope); invents approval semantics this domain may not decide alone; penalises the customer for a move that capacity, equipment or a mobile depot may have caused
- Reasoning and evidence:
- Lines are snapshotted at promotion (`WorkorderServiceLine.unitPrice`, "Snapshotted unit price"; `WorkorderPart.unitPrice`, "Snapshotted unit price from estimate"). The snapshot exists to hold the price the customer approved.
- Architectural implications:
- Components affected: none in pricing or tax. The transfer service writes nothing to line prices, the estimate or approval records. Labour-rate defaulting for lines added after the transfer resolves on the workorder's current location.
- Auditor-facing explanation:
- For a transferred workorder, confirm that line prices equal their values before the transfer (compare the `TRANSFER` snapshot, DECISION-INVENTORY-028), that the estimate row is unchanged, and that the invoice's `locationId` is the transfer's `to_location_id`.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: pos-workorder (prices kept, estimate unchanged); pos-invoice with pos-tax (tax at finalization). Where the accounting location of a deposit taken at the source is recorded is an accounting-domain question, noted as a follow-up and not decided here.

### DECISION-INVENTORY-026 — Parts: release at the source and re-request at the target; workexec never moves stock

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q5); ADR-0044
- Decision: Reservations are released at the source and requested again at the target. Stock is not transferred as part of a workorder transfer. Four rules follow:
  1. **Nothing physical is at the source.** A transfer is refused with 422 `WORKORDER_TRANSFER_PARTS_IN_HAND` while any part line has picked, issued or consumed quantity not returned to stock: `WorkorderPart.quantityIssued − quantityReturned > 0`, `quantityConsumed > 0`, or an `ext_pick_task` row for the workorder with `quantityPicked` above what has been returned. Goods on a shelf or bench at the source go back through the existing return path first. The system does not pretend they moved.
  2. **Demand is released at the source.** After the transfer commits, pos-workorder asks pos-inventory to release the workorder's demand at the source: every open reservation and every unpicked pick list or task for `(workorderId, fromLocationId)`. The mechanism is a new command, `inventory.workorder-demand.release-requested`, on `inventory.commands.v1` (the topic `InventoryCommandPublisher` already uses). pos-inventory owns reservations and decides how to apply it.
  3. **Demand is re-registered at the target.** After the release, the part lines' demand is registered at the target with the same commands as promotion (`inventory.reservation.request-requested` per line and `inventory.pick-list.generate-requested`, via `PromotedWorkorderDemandPublisher`), using the new `shopId`. Both command ids must include the location. Today the reservation id is derived from (part, item, quantity) and the pick-list id from the workorder alone, so pos-inventory's `processed_events` dedupe would swallow the target's commands as repeats of the source's.
  4. **Workexec never moves stock between sites.** If the target has no stock, its reservation takes the normal path (backorder or sourcing) like any new demand. Whether the target sources the part from the source site's stock is pos-inventory's decision, made through its own transfer process. Target availability is not a transfer gate (ADR-0044 R3).
- Alternatives considered:
- Option A (chosen): release and re-request; refuse while parts are in hand
- Pros: each module acts only on what it owns (ADR-0044 R1); no stock ledger entry is invented by workexec; demand ends up at the site that will do the work
- Cons: the reservation at the target can fail or backorder when the source had the part; for a short while, until pos-inventory applies both commands, the source reservation and the target request coexist
- Option B: transfer the reserved stock to the target as part of the workorder transfer
- Pros: the target is guaranteed the parts the source had set aside
- Cons: a stock transfer is an inventory movement with its own ledger, transit and receiving rules, owned by pos-inventory; driving it from a workorder operation makes workexec author inventory movements
- Reasoning and evidence:
- `PromotedWorkorderDemandPublisher.publish` reserves and generates pick lists at `workorder.getShopId()`, and `WorkorderPartUsageServiceImpl` reserves at `workorder.getShopId()`, so all demand is keyed to the source site. pos-inventory's `WorkorderEventsListener` keeps no location on `ext_workorder` and releases nothing when a workorder changes. Without rule 2, the source's reservations would be stranded.
- Architectural implications:
- Components affected: pos-workorder `InventoryCommandPublisher` (a new release command; location-qualified command ids for reservation and pick-list generation); `PromotedWorkorderDemandPublisher` (re-registration after a transfer). pos-inventory must implement `inventory.workorder-demand.release-requested` (cross-domain follow-up, listed in `CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`).
- Auditor-facing explanation:
- After a transfer, pos-inventory holds no open reservation or unpicked pick task for the workorder at `from_location_id`. A standing count of such rows older than the transfer means the release command was lost or not applied.
- Migration & backward-compatibility notes:
- Changing the command-id derivation changes ids only for commands issued from then on. pos-inventory's dedupe is keyed per command id, so nothing already processed is replayed.
- Governance & owner recommendations:
- Owner: pos-inventory (reservations, pick lists, stock); pos-workorder (the gate in rule 1 and the requests in rules 2–3). Whether the same release command is also sent when a workorder is cancelled is a separate question and is not decided here.

### DECISION-INVENTORY-027 — Labour and history: no recorded time crosses a transfer; history stays where it was written

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q6)
- Decision: Recorded time never moves. Four rules follow:
  1. **No clocked time at transfer.** A workorder with any labour entry, work session or travel segment is not transferable (422 `WORKORDER_TRANSFER_TIME_RECORDED`, DECISION-INVENTORY-024). So there is never clocked time to move, and this decision creates no rule about how labour affects billing or payroll across sites.
  2. **Technicians.** The technician of record is released at transfer (DECISION-INVENTORY-024). No technician is carried to the target by default.
  3. **History stays and is not rewritten.** The `technician_assignment`, `service_position_assignment`, `WorkorderStateTransition`, `AuditEvent` and `WorkorderSnapshot` rows written at the source remain, unchanged. They still name the source's bays, units and technician, and each release row carries the transfer reason. No history row is re-keyed to the target.
  4. **Every time record written after a transfer is at the target**, because the workorder's site is the target from then on.
- Alternatives considered:
- Option A (chosen): refuse transfer when time is recorded; keep all history as written
- Pros: no labour hour is ever attributed to a site where it was not worked; payroll and job-time reports stay correct without per-entry location stamping
- Cons: a job with even a test timer at the source must have that entry handled under the normal labour rules before it can move
- Option B: allow transfer with recorded time, and stamp each labour entry with the site where it was worked
- Pros: supports a mid-job move
- Cons: `WorkexecTimeTrackingServiceImpl` attributes each finalized entry to `workorder.getShopId()` when the report is read, so every job-time report and payroll feed would need migrating to the stamped value; it also decides labour-affects-payroll semantics across sites, a mandatory clarification trigger
- Reasoning and evidence:
- `WorkexecTimeTrackingServiceImpl` (`rowLocationId = workorder.getShopId()`) reads the site from the workorder at query time. A transfer with time on it would silently move the source's hours into the target's report.
- Architectural implications:
- Components affected: none beyond the DECISION-INVENTORY-024 check; no schema change to labour entities.
- Auditor-facing explanation:
- Confirm that no `WorkorderLaborEntry`, `WorkSession` or `TravelSegment` for a transferred workorder has a creation time before the transfer's `transferred_at`.
- Migration & backward-compatibility notes:
- None.
- Governance & owner recommendations:
- Owner: pos-workorder; pos-people remains owner of timekeeping and staffing (DECISION-INVENTORY-019). Transfer with recorded time needs a new clarification.

### DECISION-INVENTORY-028 — Transfer authority, reason code, audit trail and published fact

- Normative source: `AGENT_GUIDE.md` (Decision ID); issue durion-positivity-backend#2258 (Q7); ADR-0017, ADR-0044, ADR-0061
- Decision: Six rules follow:
  1. **Permission.** A new permission, `workorder:workorder:transfer`, guards the transfer, registered in `WorkorderPermissions` and the module's permission registry. It is a Shop Manager-level grant, not a Service Advisor one. `workorder:operationalContext:override` does not imply it.
  2. **Location scope at both ends (ADR-0061).** The caller must hold `workorder:workorder:transfer` in scope for the workorder's current `shopId` (a null site fails closed) and for `toLocationId`. Both checks run after the 404 and before any write, as `overrideOperationalContext` does. A denial is 403 `LOCATION_SCOPE_DENIED`, and a denied transfer writes nothing.
  3. **Reason.** `reasonCode` is required. Its values are `CUSTOMER_REQUEST`, `CAPACITY`, `EQUIPMENT`, `MOBILE_DEPOT` and `OTHER` (enum `WorkorderTransferReason`). `note` is free text of at most 500 characters and is required when the reason is `OTHER`. A missing or invalid reason, or `OTHER` without a note, is 400 `VALIDATION_FAILED`. The actor comes from the security context; a client-supplied actor id is not accepted (DECISION-INVENTORY-005).
  4. **Audit, in the transfer transaction.** Four records are written together:
     - an append-only `workorder_location_transfer` row: `id` (UUID v7), `tenant_id`, `workorder_id`, `from_location_id`, `to_location_id`, `reason_code`, `note`, `transferred_by`, `transferred_at` (UTC), plus the released `resource_type`/`resource_id` and `technician_id`
     - an `AuditEvent` with `eventType = "WorkorderTransferred"`
     - a `WorkorderSnapshot` of type `TRANSFER` taken before the change, when `workorder.statemachine.snapshot.enabled` is set
     - the `WorkorderStateTransition` row, when `ASSIGNED` returns to `APPROVED`
  5. **Telemetry.** The endpoint carries `@EmitEvent(id = "WORKORDER_TRANSFER", apiVersion = "1")`, registered in `EventTypes` as `write`.
  6. **Published fact.** A new fact, `workorder.workorder.transferred`, is published on `workorder.events.v1` (payload `WorkorderTransferredV1` in `pos-domain-events`, schema v1) through the outbox, in the transfer transaction. Its fields are `workorderId`, `workorderNumber`, `fromLocationId`, `toLocationId`, `reasonCode`, `transferredBy`, `transferredAt`, `estimateId`, `appointmentId` (the estimate's; nullable), `releasedResourceType`, `releasedResourceId`, `releasedTechnicianId` and `status` (after the transfer). `note` is not published, to keep free text out of other domains' replicas. The usual `workorder.workorder.updated` fact follows through `WorkorderFactPublisher.markChanged`, carrying the new `shopId` and `locationId`, no position and the new status. pos-shop-manager consumes the transfer fact (DECISION-SHOPMGMT-025). It uses the explicit fact, never a change of `locationId` between two snapshots. pos-inventory is driven by the command in DECISION-INVENTORY-026, not by this fact.
- Alternatives considered:
- Option A (chosen): a dedicated permission gated at both ends; a required reason code; an append-only transfer table plus an explicit fact
- Pros: a transfer moves work, and the obligations that follow it, into another site's queue, so both sites' authority is checked; the reason distinguishes the four cases for reporting; consumers learn about a move from a named business fact rather than by diffing `locationId` on `workorder.workorder.updated`
- Cons: one more permission to grant; the target-leg check means a manager scoped to a single shop cannot push work to a shop outside that scope
- Option B: reuse `workorder:operationalContext:override` and publish only the updated fact
- Pros: no new permission or event type
- Cons: override is a within-site re-slot after this decision (DECISION-INVENTORY-023 rule 4); overloading it grants cross-site moves to everyone who can re-slot a bay, and consumers would have to infer a transfer from a field change with no reason attached
- Reasoning and evidence:
- `overrideOperationalContext` already gates both ends of a location change with `LocationScope.require` (#1872), which is the precedent rule 2 follows. `service_position_assignment` and `technician_assignment` already keep append-only histories, and the transfer table follows the same pattern for the site.
- Architectural implications:
- Components affected: pos-workorder `WorkorderPermissions`, the permission registry and `permissions.yaml`, `EventTypes`, the outbox fact writer, the `location-scope.yaml` record, a Flyway migration for `workorder_location_transfer` (tenant-scoped under ADR-0062, with an RLS policy); `pos-domain-events` gains `WorkorderTransferredV1`; pos-shop-manager adds a consumer. Contract chain and the `API Artifacts Sync` workflow follow, because a controller and a permission change.
- Auditor-facing explanation:
- Every change of `workorder.location_id` after creation has exactly one `workorder_location_transfer` row, one `WorkorderTransferred` audit event and one `workorder.workorder.transferred` outbox row, all with the same timestamp. `SELECT workorder_id, count(*) FROM workorder_location_transfer GROUP BY workorder_id HAVING count(*) > 1;` lists workorders moved more than once — allowed, but worth reviewing.
- Migration & backward-compatibility notes:
- New fact type on an existing topic. Consumers that ignore unknown types record the id and move on, as pos-inventory's `WorkorderEventsListener` already does.
- Governance & owner recommendations:
- Owner: pos-workorder (permission enforcement, audit, fact); pos-security-service (permission registry of record). Do not add a transfer override that skips the DECISION-INVENTORY-024 gates.

## End

End of document.
