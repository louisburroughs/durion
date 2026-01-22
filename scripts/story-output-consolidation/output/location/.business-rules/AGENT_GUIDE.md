# AGENT_GUIDE.md

## Summary

The Location domain is the system of record for shop Locations and their Location-owned sub-resources (Bays, Mobile Units, Coverage Rules, Travel Buffer Policies). This update resolves previously open questions with safe, implementable defaults and marks any remaining contract/permission unknowns as explicitly safe-to-defer decisions. It also normalizes decision traceability by mapping each Decision ID to a detailed rationale in `DOMAIN_NOTES.md`.

**Enhancement note (new consolidated stories):** This guide now explicitly documents Bay creation fields (type/status/capacity/constraints), Bay-specific invariants, and concrete integration/testing/observability guidance needed by the new frontend stories. It also clarifies cross-domain boundaries with Scheduling/Appointments (shopmgr) and CRM.

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
| DECISION-LOCATION-016 | Distance tier unit handling |
| DECISION-LOCATION-017 | Coverage effective window timezone semantics |
| DECISION-LOCATION-018 | Address schema strategy |
| DECISION-LOCATION-019 | INACTIVE editability + reactivation policy |
| DECISION-LOCATION-020 | Audit UI endpoint/pattern |
| DECISION-LOCATION-021 | Parent hierarchy constraints |
| DECISION-LOCATION-022 | Status on create |
| DECISION-LOCATION-023 | Site default staging/quarantine nullability |
| DECISION-LOCATION-024 | Storage location eligibility for defaults |

## Domain Boundaries

### What Location owns (system of record)

- Locations (shop sites/shops): create, update, soft deactivate
- Location metadata: code, name, address, timezone, status, parent link, buffers
- Operating hours and holiday closures (Location-owned schedule metadata)
- Bays within a Location
- Mobile Units, their Coverage Rules, and Travel Buffer Policies
- Location sync logs and read-only roster views for externally-synced replicas

**Bay-specific ownership clarifications (from consolidated Bay story):**
- Location owns Bay configuration used by downstream scheduling/dispatch/work execution:
  - Bay identity and status (`ACTIVE`, `OUT_OF_SERVICE`)
  - Bay capacity (`capacity.maxConcurrentVehicles`)
  - Bay constraint references:
    - `supportedServiceIds[]` (references Service Catalog SoR)
    - `requiredSkillRequirements[]` (references People/Skills SoR)

### What Location does not own

- Scheduling/work allocation rules that consume location metadata
- Staffing/HR policy (including roster correctness when roster is synced)
- Inventory topology entities (Storage Locations); Location references them for site defaults
- Authorization policy design (roles/permission taxonomy); Location only enforces checks

**Cross-domain non-ownership clarifications (from consolidated Appointment story):**
- Appointments are **not** owned by Location. Appointment authoring/scheduling is owned by **shop management (shopmgr)** (CLARIFY: confirm repo label/ownership; see Open Questions).
- CRM customer/vehicle identity and association are owned by **CRM**; Location must not persist or “correct” CRM data.

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

**Bay sub-structures (used by UI/API contracts):**
- `Bay.capacity`: object containing `maxConcurrentVehicles` (integer, `>= 1`)
- `Bay.supportedServiceIds[]`: array of opaque IDs (Service Catalog references)
- `Bay.requiredSkillRequirements[]`: array of `{ skillId, minLevel? }` (People/Skills references)

## Invariants / Business Rules

- Location `code` is unique and immutable after creation. (Decision ID: DECISION-LOCATION-001)
- Location `name` uniqueness is enforced as case-insensitive + trimmed. (Decision ID: DECISION-LOCATION-002)
- Location `timezone` must be a valid IANA TZ identifier and should use a backend-provided allowed list. (Decision ID: DECISION-LOCATION-003)
- Operating hours: one entry per day, `open < close`, no overnight ranges, omission means closed, empty list allowed. (Decision ID: DECISION-LOCATION-004)
- Holiday closures: date-only entries, no duplicates; `reason` optional, max length 255; nullable allowed. (Decision ID: DECISION-LOCATION-005)
- Buffers: `checkInBufferMinutes` / `cleanupBufferMinutes` are null or non-negative integers.
- Optimistic locking is preferred (`version`) for updates and status changes; conflicts surface as `409` with `OPTIMISTIC_LOCK_FAILED`.

**Bay invariants (from consolidated Bay story + existing notes):**
- Bay `name` must be unique **within the parent Location**, with whitespace trimmed before uniqueness check. Conflict returns `409` with code `BAY_NAME_TAKEN_IN_LOCATION` when available. (CLARIFY: this is documented in `LOCATION_DOMAIN_NOTES.md` as “Bay Name Uniqueness Within Location”; ensure Decision ID mapping is consistent—see TODO below.)
- Bay `capacity.maxConcurrentVehicles` is required on create and must be an integer `>= 1`. (TODO: confirm whether backend allows null/omitted capacity for legacy bays; frontend story assumes required.)
- Bay constraint references are **IDs only** (no free text IDs):
  - `supportedServiceIds[]` must reference existing Service Catalog services.
  - `requiredSkillRequirements[].skillId` must reference existing Skills.
  - Backend is authoritative for ID validity; invalid IDs should return `400` with field-level errors when possible.
- Bay `status` allowed values for v1 UI: `ACTIVE`, `OUT_OF_SERVICE`. (CLARIFY: confirm if `INACTIVE` exists for bays; do not invent additional states in UI.)

**TODO / CLARIFY (Decision index consistency):**
- `LOCATION_DOMAIN_NOTES.md` includes “DECISION-LOCATION-006 — Bay Name Uniqueness Within Location”, but this AGENT_GUIDE uses DECISION-LOCATION-006 for API contract convention. This is a numbering collision across documents. **TODO:** reconcile Decision IDs between `AGENT_GUIDE.md` and `LOCATION_DOMAIN_NOTES.md` so Bay uniqueness has a stable Decision ID and cross-reference.

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
| DECISION-LOCATION-016 | Store distances as KM only for v1 | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-016---distance-tier-unit-handling) |
| DECISION-LOCATION-017 | Effective windows use UTC instants | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-017---coverage-effective-window-timezone-semantics) |
| DECISION-LOCATION-018 | Address remains backend-schema-driven | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-018---address-schema-strategy) |
| DECISION-LOCATION-019 | INACTIVE is editable except status-only fields; reactivation allowed | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-019---inactive-editability-and-reactivation) |
| DECISION-LOCATION-020 | Audit view is event-based when available | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-020---audit-ui-endpointpattern) |
| DECISION-LOCATION-021 | Parent constraints: no self-parent; cycle prevention server-side | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-021---parent-hierarchy-constraints) |
| DECISION-LOCATION-022 | Status defaults to ACTIVE on create | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-022---status-on-create) |
| DECISION-LOCATION-023 | Site defaults are nullable during rollout | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-023---site-defaults-nullability-during-rollout) |
| DECISION-LOCATION-024 | Default pickers show ACTIVE storage locations only | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-location-024---storage-location-eligibility-for-site-defaults) |

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

- Answer: In v1, store and transmit tiers in KM only (`unit = "KM"`), and do not accept MI in the UI until a conversion policy is explicitly approved.
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

## Open Questions from Frontend Stories

> This section consolidates open questions introduced by the new consolidated frontend stories. Items are phrased to be answerable by backend/domain owners and to unblock implementation.

### Bays: Create Bays with Constraints and Capacity (Issue #141)

1. **Service Catalog lookup contract (blocking):** What are the actual lookup endpoints (paths), query params (search/pagination), and response envelope for Services used to populate `supportedServiceIds[]`? Provide minimum DTO fields `{id, displayName}` and paging fields (`pageIndex`, `pageSize`, `totalCount` or equivalent).  
   - TODO: confirm whether Location backend proxies these lookups or frontend calls Service Catalog directly.

2. **Skills lookup contract (blocking):** What are the actual lookup endpoints (paths), query params (search/pagination), and response envelope for Skills used to populate `requiredSkillRequirements[].skillId`? Provide minimum DTO fields `{id, displayName}`.

3. **ID formats (blocking):** Confirm ID type/shape for:
   - `supportedServiceIds[]`
   - `requiredSkillRequirements[].skillId`  
   Frontend will treat as opaque strings, but needs example values and whether IDs are UUIDs vs codes for display/testing.

4. **Bay create/view response shape (blocking):** Confirm Bay API response fields:
   - Identifier field name (`id` vs `bayId`)
   - Whether response includes `capacity`, `supportedServiceIds`, `requiredSkillRequirements`
   - Whether response includes audit fields (`createdAt`, `updatedAt`, `createdBy`, `version`)

5. **Error envelope (blocking):** Confirm standard error payload shape for:
   - `400` validation errors (field errors)
   - `409` conflicts (duplicate name) including code `BAY_NAME_TAKEN_IN_LOCATION`
   - Correlation/request ID propagation (header name or response field)

6. **Bay type enum source (blocking):** Confirm allowed `bayType` values and how frontend should source them:
   - Backend-provided enum/config endpoint (preferred), or
   - Static list (temporary) that must match backend exactly.  
   CLARIFY: if backend supports additional bay types, UI must not hardcode without contract.

7. **Frontend route conventions (non-blocking but important):** Confirm canonical URL/screen patterns for location subresources in this repo (e.g., `/locations/:locationId/bays/new` and `/locations/:locationId/bays/:bayId`) and Moqui screen paths.

### Scheduling: Create Appointment with CRM Customer and Vehicle (Issue #139)

1. **Domain label/ownership (blocking):** This story creates Appointments, which are SoR in **shopmgr** per the story text. Confirm the correct single `domain:*` label for scheduling/appointments in this repo.  
   - CLARIFY: current issue labels include `domain:location`; likely incorrect.

2. **Canonical Moqui screen route (blocking):** Confirm exact Moqui screen path and parameter name for appointment create/detail (e.g., `durion-shopmgr/AppointmentEdit` and `appointmentId`) and whether create is “new record mode” of the same screen.

3. **CRM API contracts (blocking):** Provide exact endpoints (or Moqui services invoked by transitions) and DTOs for:
   - CRM customer search
   - CRM vehicles-by-customer list  
   Include error codes for `CUSTOMER_NOT_FOUND`, `VEHICLE_NOT_FOUND`.

4. **Appointment create contract (blocking):** Provide exact endpoint/service, request/response DTO, and error codes for:
   - `VEHICLE_CUSTOMER_MISMATCH` (409)
   - `403` authorization failure
   - Any duplicate/idempotency behavior (see next item)

5. **Timezone semantics for start/end (blocking):** For `startAt`/`endAt`, must UI send UTC (`Z`) or local offset? If local offset is allowed, which timezone is authoritative (user vs shop)? How does frontend obtain shop timezone (if required)?

6. **Requested services structure (blocking):** Are requested services free-text only, or must they reference catalog service IDs/codes? If IDs required, provide lookup endpoint/picker contract.

7. **Scheduling conflicts (non-blocking unless backend enforces):** Does appointment create validate overlaps and return a specific conflict code? If yes, provide code and required UX (simple error vs suggested alternatives).

## Integration Patterns and Events (Guidance)

**Location-owned resources consumed by other domains:**
- Scheduling/dispatch/work execution should treat Location/Bay/MobileUnit data as read-only configuration inputs.
- Consumers should query by `status=ACTIVE` where applicable (bays/mobile units) and handle `OUT_OF_SERVICE` as non-assignable.

**Events (CLARIFY / TODO):**
- TODO: confirm whether Location emits domain events (e.g., `LocationUpdated`, `BayCreated`, `BayUpdated`, `MobileUnitCoverageRulesReplaced`) and via which mechanism (Moqui entity events, message bus, audit stream).
- Safe default until confirmed: consumers poll/query Location APIs and use `updatedAt/version` for change detection.

**Idempotency:**
- For Location domain creates (e.g., Bay create), idempotency header usage is not specified in Location decisions.  
  - TODO: confirm whether Location endpoints support `Idempotency-Key` similar to shopmgr appointment create. If not supported, frontend must prevent double-submit and rely on server-side uniqueness constraints (e.g., bay name uniqueness) to avoid duplicates.

## API Contracts and Patterns (Guidance)

- Use `/rest/api/v1/location/...` prefix for Location domain endpoints. (Decision ID: DECISION-LOCATION-006)
- Prefer resource-oriented paths for subresources:
  - `/locations/{locationId}/bays`
  - `/locations/{locationId}/bays/{bayId}`
- Lists must be paginated and return stable envelopes (`items[]` + `totalCount`) where possible. (Decision ID: DECISION-LOCATION-013 provides the pattern for mobile units; apply same pattern to bays unless backend differs—CLARIFY.)
- Error handling:
  - Use stable error `code` values for programmatic mapping (e.g., `BAY_NAME_TAKEN_IN_LOCATION`, `INVALID_TIMEZONE`, `OPTIMISTIC_LOCK_FAILED`).
  - Include correlation/request ID in error responses or headers for support workflows (CLARIFY exact mechanism).

## Security / Authorization Requirements (Guidance)

- Enforce least privilege:
  - `location:view` for read-only access.
  - `location:manage` for mutations (create/update/status changes) including Bay creation. (Decision ID: DECISION-LOCATION-007)
- Frontend must not assume permission based on UI state alone:
  - Always handle `401` (unauthenticated) and `403` (forbidden) from backend.
  - Hide/disable mutation actions when permission claims are available; otherwise rely on backend responses.
- Do not log sensitive data:
  - For Bay create attempts, log only `locationId`, `bayId` (on success), `bayName`, and selected ID counts (e.g., number of services/skills), not full payloads.

## Observability Guidance (Metrics, Logs, Traces)

**Frontend (Moqui UI) logging (actionable):**
- Emit structured logs for Bay create:
  - `event`: `bay_create_attempt|bay_create_success|bay_create_failure`
  - `locationId`, `bayId` (success), `bayName`, `bayType`, `status`
  - `supportedServiceCount`, `requiredSkillCount`
  - `httpStatus`, `errorCode` (if present), `correlationId/requestId` (if present)
- Do not log full arrays of IDs unless required for debugging; if logged, cap length and redact beyond N entries.

**Backend (Location services) metrics (TODO if not already standard):**
- Counters:
  - `location.bay.create.count` tagged by `status` and `result` (`success|validation_error|conflict|forbidden|error`)
- Latency histograms:
  - `location.bay.create.latency_ms`
  - `location.bay.list.latency_ms`
- Error rate alerts:
  - sustained `5xx` on bay endpoints
  - elevated `409` conflicts may indicate UX issues (duplicate naming patterns)

**Tracing:**
- Propagate correlation IDs end-to-end:
  - CLARIFY header name (e.g., `X-Correlation-Id` or platform standard).
  - Frontend should surface correlation ID in error banners for support.

## Testing Strategies (Concrete)

**Contract tests (backend):**
- Bay create:
  - `201` returns identifier and echoes persisted fields (`capacity`, constraints).
  - `409 BAY_NAME_TAKEN_IN_LOCATION` when same trimmed name exists under same location.
  - Ensure uniqueness is scoped to location (same name allowed in different locations).
  - `400` when `capacity.maxConcurrentVehicles < 1` or non-integer.
  - `400` when invalid service/skill IDs are provided (if backend validates references).
- Bay list:
  - Pagination behavior and stable envelope.
  - Status filtering (`ACTIVE`, `OUT_OF_SERVICE`, `ALL` if supported—CLARIFY).

**Frontend tests:**
- Form validation:
  - trims name before submit
  - blocks submit on missing required fields
  - blocks submit when skill row missing `skillId`
- Error mapping:
  - maps `409 BAY_NAME_TAKEN_IN_LOCATION` to name field error
  - preserves form state on `400/409/5xx`
  - shows correlation ID when present
- Authorization:
  - hides/disables create action without `location:manage`
  - handles `403` by showing access denied state

**Integration/E2E tests:**
- Create bay happy path then navigate to Bay Detail and verify displayed fields.
- Create bay with constraints using stubbed lookup endpoints (Service Catalog/Skills) to ensure picker integration works with pagination/search.

## Common Pitfalls and Gotchas

- **Decision ID collisions across docs:** `LOCATION_DOMAIN_NOTES.md` currently contains Decision IDs that conflict with this guide’s index (e.g., DECISION-LOCATION-006). Do not add new decisions until IDs are reconciled. TODO: normalize IDs and update cross-references.
- **Do not implement client-side uniqueness rules beyond trimming:** Bay name uniqueness is server-defined (case sensitivity is backend-defined per notes). Frontend should trim but not case-fold.
- **Do not accept free-text IDs for constraints:** Always use pickers backed by authoritative lookup APIs; treat IDs as opaque.
- **Null vs empty semantics:** Preserve null vs empty arrays where the contract requires it (notably holiday closures). Do not “helpfully” coerce null to empty without explicit requirement.
- **Timezone ambiguity:** For appointment start/end (shopmgr), timezone semantics are blocking and must be confirmed; do not guess (UTC vs local offset) because it affects scheduling correctness.
- **Double-submit duplicates:** If Location endpoints do not support idempotency keys, prevent double-submit in UI and rely on server-side uniqueness constraints where available.

## Todos Reconciled

- Original todo: "whether UI should use a backend-provided allowed list vs frontend static list" → Resolution: Resolved (use backend list; fallback only). | Decision: DECISION-LOCATION-003
- Original todo: "enforce cycle prevention and max depth" → Resolution: Replace with task: `TASK-LOC-001` (backend cycle prevention; UI self-parent block). | Decision: DECISION-LOCATION-021
- Original todo: "if SyncLog is evented" → Resolution: Defer (needs platform eventing decision; owner: audit/observability). Replace with task: `TASK-LOC-002`.
- Original todo: "exact endpoint(s) and atomic replacement semantics" → Resolution: Resolved at policy level (atomic replace) and Defer exact path. | Decision: DECISION-LOCATION-014
- Original todo: "confirm services/skills ID types and lookup endpoints" → Resolution: Defer (authoritative domains must confirm) → `TASK-LOC-003`. | Decision: DECISION-LOCATION-012

**New TODOs from consolidated stories:**
- TODO: Reconcile Decision ID collision between `AGENT_GUIDE.md` and `LOCATION_DOMAIN_NOTES.md` for Bay name uniqueness and other Bay-related decisions.
- TODO: Confirm Bay `bayType` enum source and values; document in implementing PR and/or add a decision once IDs are reconciled.
- TODO: Confirm Location Bay endpoints’ response envelopes, error payload shape, and correlation ID propagation.
- TODO: Confirm whether Location endpoints support idempotency keys for create operations.
- TODO: Confirm correct domain label/ownership for Appointment creation story (shopmgr vs location) and document canonical screen route/params.

## End

End of document.
