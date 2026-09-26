---
type: Agent Guide
title: Location Agent Guide
description: The Location domain is the system of record for shop Locations and their Location-owned sub-resources (Bays, Mobile Units, Coverage Rules, Travel Buffer Policies). This update resolves previously o...
domain: location
tags: [domain, location, agent-guide]
---

# AGENT_GUIDE.md

## Summary

The Location domain is the system of record for shop Locations and their Location-owned sub-resources (Bays, Mobile Units, Coverage Rules, Travel Buffer Policies). This update resolves previously open questions with safe, implementable defaults and marks any remaining contract/permission unknowns as explicitly safe-to-defer decisions. It also normalizes decision traceability by mapping each Decision ID to a detailed rationale in `DOMAIN_NOTES.md`.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-LOCATION-001 | Location code immutability |
| DECISION-LOCATION-002 | Location name uniqueness (case-insensitive, trimmed) |
| DECISION-LOCATION-003 | Timezone validation + allowed list source |
| DECISION-LOCATION-004 | Operating hours representation + validation |
| DECISION-LOCATION-005 | Holiday closures representation + validation |
| DECISION-LOCATION-006 | API contract convention (Moqui REST pattern) |
| DECISION-LOCATION-007 | Permission gating model (view vs manage) |
| DECISION-LOCATION-008 | Default status filtering for lists |
| DECISION-LOCATION-009 | Synced location tags + region semantics |
| DECISION-LOCATION-010 | Navigation placement rules |
| DECISION-LOCATION-011 | Service Area scope (picker-only by default) |
| DECISION-LOCATION-012 | Capabilities/services/skills lookups |
| DECISION-LOCATION-013 | Mobile unit list contract defaults |
| DECISION-LOCATION-014 | Coverage rules replace semantics |
| DECISION-LOCATION-015 | Travel buffer policy schemas (FIXED_MINUTES, DISTANCE_TIER) |
| DECISION-LOCATION-016 | Distance tier unit handling (superseded by DECISION-LOCATION-028) |
| DECISION-LOCATION-017 | Coverage effective window timezone semantics |
| DECISION-LOCATION-018 | Address schema strategy |
| DECISION-LOCATION-019 | INACTIVE editability + reactivation policy |
| DECISION-LOCATION-020 | Audit UI endpoint/pattern |
| DECISION-LOCATION-021 | Parent hierarchy constraints |
| DECISION-LOCATION-022 | Status on create |
| DECISION-LOCATION-023 | Site default staging/quarantine nullability |
| DECISION-LOCATION-024 | Storage location eligibility for defaults |
| DECISION-LOCATION-025 | Bay specialty map is authoritative and published |
| DECISION-LOCATION-026 | Bay and mobile-unit lifecycle; delete retires |
| DECISION-LOCATION-027 | Coverage eligibility: active areas, base location, one ranking, UTC |
| DECISION-LOCATION-028 | Distance units configurable (MI or KM), stored in KM |
| DECISION-LOCATION-029 | Mobile unit and bay setup attributes |

## Domain Boundaries

### What Location owns (system of record)

- Locations (shop sites/shops): create, update, soft deactivate
- Location metadata: code, name, address, timezone, status, parent link, buffers
- Operating hours and holiday closures (Location-owned schedule metadata)
- Bays within a Location
- Mobile Units, their Coverage Rules, and Travel Buffer Policies
- Location sync logs and read-only roster views for externally-synced replicas

### What Location does not own

- Scheduling/work allocation rules that consume location metadata
- Staffing/HR policy (including roster correctness when roster is synced)
- Inventory topology entities (Storage Locations); Location references them for site defaults
- Authorization policy design (roles/permission taxonomy); Location only enforces checks

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| Location | Physical/logical business location with immutable `code` and mutable operational metadata. |
| Bay | Service bay under a Location. |
| MobileUnit | Mobile service resource linked to a base Location. |
| MobileUnitCoverageRule | Service-area link defining coverage for a Mobile Unit. |
| TravelBufferPolicy | Policy referenced by Mobile Units for travel buffers. |
| SyncLog | Record of a sync run (external → replica). |
| Site default locations | Site-level configuration referencing Inventory storage locations (staging/quarantine). |

## Invariants / Business Rules

- Location `code` is unique and immutable after creation. (Decision ID: DECISION-LOCATION-001)
- Location `name` uniqueness is enforced as case-insensitive + trimmed. (Decision ID: DECISION-LOCATION-002)
- Location `timezone` must be a valid IANA TZ identifier and should use a backend-provided allowed list. (Decision ID: DECISION-LOCATION-003)
- Operating hours: one entry per day, `open < close`, no overnight ranges, omission means closed, empty list allowed. (Decision ID: DECISION-LOCATION-004)
- Holiday closures: date-only entries, no duplicates; `reason` optional, max length 255; nullable allowed. (Decision ID: DECISION-LOCATION-005)
- Buffers: `checkInBufferMinutes` / `cleanupBufferMinutes` are null or non-negative integers.
- Optimistic locking is preferred (`version`) for updates and status changes; conflicts surface as `409` with `OPTIMISTIC_LOCK_FAILED`.

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-LOCATION-001 | Code cannot change after create | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-001---location-code-immutability) |
| DECISION-LOCATION-002 | Name unique with normalization | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-002---location-name-uniqueness-with-normalization) |
| DECISION-LOCATION-003 | Backend validates IANA tz + provides list | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-003---timezone-validation-and-allowed-list) |
| DECISION-LOCATION-004 | Operating hours rules + payload shape | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-004---operating-hours-representation-and-validation) |
| DECISION-LOCATION-005 | Holiday closures rules + null vs empty | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-005---holiday-closures-representation-and-validation) |
| DECISION-LOCATION-006 | REST/API convention for Location services | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-006---api-contract-convention-moqui-rest-pattern) |
| DECISION-LOCATION-007 | Permission split and action gating | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-007---permission-gating-model-view-vs-manage) |
| DECISION-LOCATION-008 | Default list filter behaviors | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-008---default-status-filtering-for-lists) |
| DECISION-LOCATION-009 | Tags and region are display-safe fields | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-009---synced-tags-and-region-semantics) |
| DECISION-LOCATION-010 | Navigation placement guidance | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-010---navigation-placement-rules) |
| DECISION-LOCATION-011 | Service Area is picker-only by default | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-011---service-area-scope-picker-only-default) |
| DECISION-LOCATION-012 | Lookups come from authoritative domains | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-012---capabilitiesserviceskills-lookup-contract) |
| DECISION-LOCATION-013 | Mobile unit list filters/envelope default | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-013---mobile-unit-list-contract-defaults) |
| DECISION-LOCATION-014 | Coverage rules use atomic replace | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-014---coverage-rules-atomic-replace) |
| DECISION-LOCATION-015 | Policy types and schema rules | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-015---travel-buffer-policy-schemas) |
| DECISION-LOCATION-016 | Store distances as KM only for v1 (superseded by DECISION-LOCATION-028) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-016---distance-tier-unit-handling) |
| DECISION-LOCATION-017 | Effective windows use UTC instants | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-017---coverage-effective-window-timezone-semantics) |
| DECISION-LOCATION-018 | Address remains backend-schema-driven | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-018---address-schema-strategy) |
| DECISION-LOCATION-019 | INACTIVE is editable except status-only fields; reactivation allowed | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-019---inactive-editability-and-reactivation) |
| DECISION-LOCATION-020 | Audit view is event-based when available | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-020---audit-ui-endpointpattern) |
| DECISION-LOCATION-021 | Parent constraints: no self-parent; cycle prevention server-side | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-021---parent-hierarchy-constraints) |
| DECISION-LOCATION-022 | Status defaults to ACTIVE on create | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-022---status-on-create) |
| DECISION-LOCATION-023 | Site defaults are nullable during rollout | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-023---site-defaults-nullability-during-rollout) |
| DECISION-LOCATION-024 | Default pickers show ACTIVE storage locations only | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-024---storage-location-eligibility-for-site-defaults) |
| DECISION-LOCATION-025 | Specialty map is the source of truth, published per tenant; retype resets codes | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-025--the-bay-specialty-map-is-authoritative-and-published) |
| DECISION-LOCATION-026 | ACTIVE / OUT_OF_SERVICE / RETIRED; delete retires; retired names reserved; reason list | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-026--bays-and-mobile-units-share-one-lifecycle-delete-retires) |
| DECISION-LOCATION-027 | Inactive areas give no coverage; scoped to base location; one ranking; UTC windows | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-027--coverage-eligibility-active-areas-base-location-one-ranking-utc) |
| DECISION-LOCATION-028 | Distances in MI or KM per location, stored in KM; evaluated once geocoded | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-028--distances-are-configurable-in-miles-or-kilometres-stored-in-kilometres) |
| DECISION-LOCATION-029 | Unit duty ceiling and optional identity; no equipment lists or crews | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-029--mobile-unit-and-bay-setup-attributes) |

## Open Questions (from source)

### Q: What are the exact Moqui service names or REST endpoints (and response envelopes/error formats) for the Location domain screens?

- Answer: Use the standard REST convention `/rest/api/v1/location/...` for Moqui-exposed services, and document the concrete endpoint list in the implementing story PR. This is safe-to-defer until the owning service module finalizes exact paths.
- Assumptions:
  - The platform uses `/rest/api/v1/...` as the canonical REST prefix.
  - Response envelopes include paging + stable error codes.
- Rationale:
  - Avoid inventing payloads while still giving agents a deterministic convention.
- Impact:
  - Requires adding/confirming endpoint docs at implementation time.
- Decision ID: DECISION-LOCATION-006

### Q: What permissions/roles gate each screen and action?

- Answer: Use a split model: `location:view` for read-only lists/details and `location:manage` for mutations; specialized manage permissions may exist for sub-resources but must be enforced server-side.
- Assumptions:
  - A user may be allowed to view sync roster/logs without manage rights.
- Rationale:
  - Minimizes accidental privilege escalation while enabling read-only operational UIs.
- Impact:
  - UI must hide/disable actions based on 403s and/or permission claims.
- Decision ID: DECISION-LOCATION-007

### Q: Locations list default status filter: should it default to ACTIVE or ALL?

- Answer: Default to `ACTIVE` for CRUD/admin lists; for sync roster/log views, default to `ALL` if the screen is used for ops/troubleshooting.
- Assumptions:
  - Admin CRUD is “operationally active set” oriented.
- Rationale:
  - Avoid overwhelming users while preserving access to inactive records when relevant.
- Impact:
  - Default filter must be explicitly documented per screen.
- Decision ID: DECISION-LOCATION-008

### Q: tags type for synced locations and region semantics?

- Answer: Treat `tags` as an array of strings if possible; if the backend returns a CSV, render it as a split list. Treat `region` as free-text display-only unless a controlled vocabulary is introduced.
- Assumptions:
  - Tags/region are not used for enforcement decisions.
- Rationale:
  - UI must be resilient to schema differences during sync evolution.
- Impact:
  - Rendering and filtering must not break if type changes.
- Decision ID: DECISION-LOCATION-009

### Q: Menu placement and route conventions?

- Answer: Place CRUD under an Admin/Location Management section; keep read-only sync roster/logs under the consumer-facing area (e.g., Pricing) only if the business owner requires it.
- Assumptions:
  - Navigation is centrally governed by frontend repo conventions.
- Rationale:
  - Avoid discoverability issues and prevent duplicate screens.
- Impact:
  - May require coordination with frontend navigation owners.
- Decision ID: DECISION-LOCATION-010

### Q: Service Area management scope?

- Answer: Default to picker-only (link existing service areas). CRUD requires a separate explicit story because it creates a new SoR surface.
- Assumptions:
  - Service areas may be owned by another geo domain/service.
- Rationale:
  - Prevent accidental domain ownership drift.
- Impact:
  - If CRUD is needed later, add a dedicated story and contracts.
- Decision ID: DECISION-LOCATION-011

### Q: Capabilities lookup endpoint and response shape?

- Answer: Capabilities and services are owned by Service Catalog and should be fetched via an authoritative read-only API (direct or via a backend proxy). The UI must not accept free-text IDs.
- Assumptions:
  - Catalog APIs support search/pagination.
- Rationale:
  - Prevent invalid IDs and avoid tight coupling.
- Impact:
  - Requires wiring the picker to an agreed lookup source.
- Decision ID: DECISION-LOCATION-012

### Q: Mobile unit list endpoint filters and envelope?

- Answer: Default supported filters are `status` and `baseLocationId` with server-side pagination. Response should include `items[]` and `totalCount`.
- Assumptions:
  - Lists must scale.
- Rationale:
  - Avoid unbounded loads.
- Impact:
  - API contract tests should assert paging.
- Decision ID: DECISION-LOCATION-013

### Q: Coverage rules atomic replacement endpoint/payload?

- Answer: Coverage rules updates must be atomic replace per mobile unit (single request sets the whole set). Use a `:replace` action suffix convention.
- Assumptions:
  - Partial updates cause drift.
- Rationale:
  - Deterministic configuration management.
- Impact:
  - Backend must implement replace semantics and idempotency for identical payloads.
- Decision ID: DECISION-LOCATION-014

### Q: Travel buffer policy FIXED_MINUTES schema?

- Answer: Use `policyConfiguration.minutes` (integer >= 0) as the v1 schema.
- Assumptions:
  - The backend stores `policyConfiguration` as JSON.
- Rationale:
  - Keeps schema minimal and explicit.
- Impact:
  - Contract must be documented and validated.
- Decision ID: DECISION-LOCATION-015

### Q: DISTANCE_TIER unit handling?

- Superseded by DECISION-LOCATION-028 (distances configurable in MI or KM, stored in KM).
- Original answer: In v1, store and transmit tiers in KM only (`unit = "KM"`), and do not accept MI in the UI until a conversion policy is explicitly approved.
- Assumptions:
  - Consistent storage units reduce audit/debug risk.
- Rationale:
  - Avoid silent conversions.
- Impact:
  - UI labels distances as KM and validates accordingly.
- Decision ID: DECISION-LOCATION-016

### Q: Timezone rule for coverage effective windows?

- Answer: Treat effective windows as UTC instants (ISO-8601 with offset). UI may display in user/session timezone but must submit offsets.
- Assumptions:
  - Backends store timestamps as instants.
- Rationale:
  - Avoid ambiguous DST conversions.
- Impact:
  - Date-time pickers must preserve offset.
- Decision ID: DECISION-LOCATION-017

### Q: Address schema for Location?

- Answer: Address fields are backend-schema-driven; UI should only render fields confirmed by the API. If not confirmed, use a minimal free-text address field and rely on backend validation.
- Assumptions:
  - Address schema may evolve.
- Rationale:
  - Prevent UI from inventing contract.
- Impact:
  - Requires a schema confirmation task in implementation.
- Decision ID: DECISION-LOCATION-018

### Q: Operating hours payload shape; is empty list allowed?

- Answer: Use `[{dayOfWeek, open, close}]` where `dayOfWeek` is `MONDAY`..`SUNDAY` and `open/close` are `HH:mm`. Empty list is allowed and means “no standard hours defined”.
- Assumptions:
  - Backend validates strings.
- Rationale:
  - Deterministic and easy to validate.
- Impact:
  - Contract tests must assert day enum values.
- Decision ID: DECISION-LOCATION-004

### Q: Holiday closures nullable vs empty array; reason constraints?

- Answer: Allow null and empty array; enforce no duplicate dates; `reason` optional, trimmed, max 255.
- Assumptions:
  - Null means “unknown/not provided” and empty means “none”.
- Rationale:
  - Preserves semantic difference when syncing.
- Impact:
  - UI must not auto-convert null to empty unless required.
- Decision ID: DECISION-LOCATION-005

### Q: Editing INACTIVE locations and reactivation?

- Answer: INACTIVE locations may be edited for non-identity fields (name/address/hours) but code remains immutable; reactivation (INACTIVE → ACTIVE) is allowed behind manage permission.
- Assumptions:
  - Deactivation is operational, not archival.
- Rationale:
  - Supports temporary closures.
- Impact:
  - UI must show explicit status transition action.
- Decision ID: DECISION-LOCATION-019

### Q: Audit UI endpoint for per-entity audit events?

- Answer: Use an event/audit endpoint when available (e.g., `/rest/api/v1/audit/events?entityType=Location&entityId=...`). If not available, the UI should rely on `createdAt/updatedAt/version` only.
- Assumptions:
  - Audit service may be cross-domain.
- Rationale:
  - Avoid inventing audit storage.
- Impact:
  - Add a follow-up task if audit endpoint is required.
- Decision ID: DECISION-LOCATION-020

### Q: Parent hierarchy constraints (cycles/max depth)?

- Answer: UI prevents self-parent selection; backend must prevent cycles. No max depth is enforced in v1 unless a consumer requires it.
- Assumptions:
  - Most deployments have shallow trees.
- Rationale:
  - Cycle prevention is mandatory; max depth is optional.
- Impact:
  - Backend validation + 400 error code for cycles.
- Decision ID: DECISION-LOCATION-021

### Q: Status on create?

- Answer: Status defaults to `ACTIVE` on create; the create form does not expose status selection.
- Assumptions:
  - Status transitions are deliberate actions.
- Rationale:
  - Reduces error-prone creates.
- Impact:
  - Create endpoint ignores/blocks status field if provided.
- Decision ID: DECISION-LOCATION-022

### Q: Site defaults null handling and storage location eligibility?

- Answer: During rollout, allow null defaults (read and show as “Not configured”). Pickers show ACTIVE storage locations only; backend validates belongs-to-site and distinctness.
- Assumptions:
  - Legacy sites may not have defaults yet.
- Rationale:
  - Supports incremental adoption.
- Impact:
  - UI must handle empty state and guide user.
- Decision ID: DECISION-LOCATION-023

### Q: Timezone source: backend-provided list or static frontend list?

- Answer: Use a backend-provided list endpoint; a static list is permitted only as a temporary fallback when the endpoint is unavailable.
- Assumptions:
  - Backend can access an up-to-date IANA list.
- Rationale:
  - Prevent stale clients.
- Impact:
  - UI adds lazy-loading + error state.
- Decision ID: DECISION-LOCATION-003

### Q: Is the bay-type specialty map authoritative, and what happens when a bay is retyped?

- Answer: the map is the single source of truth for which operation codes are specialty, and `pos-location` publishes it.
  - Published per tenant as `location.bay-specialty-map.updated` (`bayType`, `operationCodes[]`, `acceptsGeneralWork`, `aggregateVersion`) for `pos-shop-manager` and `pos-workorder` to replicate. This amends spec D14.3, which assumed consumers never need the map.
  - Every tenant gets the map on provisioning (a copy of the platform template), as ADR-0062 onboarding requires.
  - `acceptsGeneralWork` is a `BayType` attribute (false only for `WASH_DETAIL`), carried on the bay fact and the map fact.
  - A retype without codes resets the bay's codes to the new type's defaults (spec D14.3, kept). No provenance flag. The `BayPatchRequest` `@Schema` says so and the UI asks for confirmation.
  - A `WASH_DETAIL` bay may be created with no codes. Wash and detail services are ordinary catalog line items, not specialty operations, and are not added to the map.
- Assumptions:
  - Adding wash and detail services is `pos-catalog`'s job (spec D1).
- Rationale:
  - Specialty equipment is a physical fact about bay types; deriving it from whichever bays are active at the moment turned missing equipment into general work.
- Impact:
  - New fact and replicas; tenant provisioning of the map; `bays.csv` gains `maxDutyClass`.
- Decision ID: DECISION-LOCATION-025

### Q: How are bays and mobile units retired, and what is recorded when one leaves service?

- Answer: bays and mobile units share one lifecycle, `ACTIVE` | `OUT_OF_SERVICE` | `RETIRED`, and `DELETE` retires.
  - No hard delete. Consumers keep the replica row with `active = false`, so history keeps its names.
  - `RETIRED` is reversible and has no error code of its own: booking or placing work on it returns 422 `SERVICE_POSITION_INACTIVE`. Default lists hide it.
  - Retired names stay reserved: creating or renaming to a retired resource's name returns 409 `BAY_NAME_TAKEN` (or `MOBILE_UNIT_NAME_TAKEN`), so reactivation never clashes.
  - Going `OUT_OF_SERVICE` requires a reason from a fixed list, plus an optional note (required for `OTHER`); a missing reason returns 422 `OUT_OF_SERVICE_REASON_REQUIRED`. An optional `expectedReturnAt` is advisory and not used by scheduling. Both clear on return to `ACTIVE`.
  - Bays gain an optional `displayOrder`, the sort key for lists and the dispatch board.
  - Planned (future-dated) downtime is out of scope.
- Assumptions:
  - A mobile unit's `INACTIVE` becomes `OUT_OF_SERVICE` (pre-production, no shim). This amends the mobile-unit status set in DOMAIN_NOTES DECISION-LOCATION-007; its travel-buffer requirement for `ACTIVE` is unchanged.
- Rationale:
  - Asset registers archive resources with history; hard delete orphaned the appointments and workorders that named the resource.
- Impact:
  - Status CHECK on both tables; `DELETE` semantics change; new reason, note, expected-return and display-order fields.
- Decision ID: DECISION-LOCATION-026

### Q: Which coverage rules make a mobile unit eligible?

- Answer: only rules on an active service area, for units based at the booking's location, ranked by one priority across that location's units.
  - An inactive service area contributes no coverage. Its rules are kept but stop matching. Writing a rule that points at an inactive area returns 422 `SERVICE_AREA_INACTIVE`. The unit's own status is not changed.
  - The eligibility read is scoped by base location and may filter by operation codes: a unit must claim every requested code.
  - Priority is one ranking among the location's units (1 is sent first); ties break by unit id.
  - Validity windows are evaluated in UTC, as DECISION-LOCATION-017 already states.
- Assumptions:
  - A unit only takes work from its base location (DECISION-SHOPMGMT-023).
- Rationale:
  - Offering a unit the workorder service will then refuse (cross-site) or an area the business has retired makes the search promise what the write cannot keep.
- Impact:
  - Eligibility query filter and scope; new 422 on coverage writes.
- Decision ID: DECISION-LOCATION-027

### Q: In what unit are coverage and travel-buffer distances expressed?

- Answer: configurable per location, miles or kilometres; stored canonically in kilometres. This supersedes DECISION-LOCATION-016 (KM only).
  - Each location has a `distanceUnit` (`KM` | `MI`). Requests and responses carry distances with their unit, never a bare number; the backend converts (1 mi = 1.609344 km exactly) and stores kilometres.
  - Distance-based coverage and `DISTANCE_TIER` buffers are wanted but evaluated only once addresses can be geocoded. Until then they are stored and labelled "not yet evaluated".
  - The policy types stay DECISION-LOCATION-015's `FIXED_MINUTES` and `DISTANCE_TIER`. The code's `FLAT_MINUTES` is renamed to `FIXED_MINUTES`, and its `PERCENTAGE_OF_TRAVEL` and `DISTANCE_MULTIPLIER`, which no decision defines and which need routed travel time, are removed.
- Assumptions:
  - Seed distances were authored in miles and are converted when the column changes.
- Rationale:
  - Shops think in their local unit; one canonical storage unit keeps comparisons and audits unambiguous.
- Impact:
  - `max_distance` becomes `max_distance_km`; a location setting; geocoding is a separate capability.
- Decision ID: DECISION-LOCATION-028

### Q: Which setup attributes do mobile units and bays carry?

- Answer: a duty-class ceiling on units, optional identity fields, and no equipment lists or crews.
  - Mobile unit: `maxDutyClass` (1–8, the bay's axis per spec D13); optional, display-only `unitNumber`, `vin`, `licensePlate`, `plateRegion`, each unique per tenant when set.
  - No equipment list on either resource: lift rating is `maxDutyClass`; racks and machines are specialty operation codes (spec D14).
  - No usual crew on a unit: staffing belongs to People.
  - No hours on a unit: it follows its base location (DECISION-SHOPMGMT-023).
  - Bay floor position (x/y) is deferred; `displayOrder` covers ordering (DECISION-LOCATION-026).
- Assumptions:
  - Identity fields are not used by scheduling.
- Rationale:
  - One vocabulary for "what can this resource do" (spec D14.2); fields nothing reads stay labelled as display-only.
- Impact:
  - New nullable columns on `mobile_units`.
- Decision ID: DECISION-LOCATION-029

## Todos Reconciled

- Original todo: "whether UI should use a backend-provided allowed list vs frontend static list" → Resolution: Resolved (use backend list; fallback only). | Decision: DECISION-LOCATION-003
- Original todo: "enforce cycle prevention and max depth" → Resolution: Replace with task: `TASK-LOC-001` (backend cycle prevention; UI self-parent block). | Decision: DECISION-LOCATION-021
- Original todo: "if SyncLog is evented" → Resolution: Defer (needs platform eventing decision; owner: audit/observability). Replace with task: `TASK-LOC-002`.
- Original todo: "exact endpoint(s) and atomic replacement semantics" → Resolution: Resolved at policy level (atomic replace) and Defer exact path. | Decision: DECISION-LOCATION-014
- Original todo: "confirm services/skills ID types and lookup endpoints" → Resolution: Defer (authoritative domains must confirm) → `TASK-LOC-003`. | Decision: DECISION-LOCATION-012
- Escalation from durion-positivity-backend#2245: "CLARIFY bay and mobile-unit setup data" → Resolution: Resolved (published specialty map; shared lifecycle with retire; coverage scoping; configurable distance units; setup attributes) | Decision: DECISION-LOCATION-025 to DECISION-LOCATION-029

## End

End of document.
```
