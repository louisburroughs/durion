```markdown
# AGENT_GUIDE.md — Location Domain

---

## Purpose

The Location domain manages the authoritative representation and lifecycle of business locations within the POS ecosystem. It provides CRUD operations for locations, including hierarchical relationships, operational status, and timezone data. This domain serves as the system of record for location metadata consumed by scheduling, staffing, inventory, and work execution services.

This guide is used by agents implementing Moqui/Vue admin screens and backend integrations for:
- Shop Locations CRUD (address/timezone/hours/closures/buffers)
- Bays under a Location (capacity + constraints)
- Mobile Units + Coverage Rules + Travel Buffer Policies
- Location sync roster + sync logs (durion-hr replica for pricing scope)
- Site default staging/quarantine storage location configuration (cross-domain dependency on Inventory storage locations)

---

## Domain Boundaries

### In-Scope
- Creation, retrieval, update, and soft-deactivation of **Location** entities.
- Management of location attributes:
  - `code`, `name`, `address`, `timezone`, `status`, `parentLocationId`
  - `operatingHours`, `holidayClosures`
  - buffer overrides (`checkInBufferMinutes`, `cleanupBufferMinutes`)
- Management of **sub-resources** owned by Location domain:
  - **Bays** under a Location (capacity, status, constraints)
  - **Mobile Units** (base location, capabilities, status, max daily jobs)
  - **Mobile Unit Coverage Rules** (link to Service Areas, priority, effective windows)
  - **Travel Buffer Policies** (FIXED_MINUTES, DISTANCE_TIER)
- Read-only access to **Location replica** records synced from external authoritative sources (e.g., `durion-hr`) when Location is not the SoR for those records.
- Recording and exposing **SyncLog** for location sync runs.
- Emission of domain events reflecting lifecycle changes (see Events / Integrations).

### Out-of-Scope
- Scheduling, staffing, pricing, and work execution logic that *consumes* location data (e.g., “inactive locations prevent staffing assignments”).
- Distance calculations / travel time computations (caller systems own).
- Authorization policy design (roles/permissions definitions) — owned by Security domain; Location domain enforces checks but does not define them.
- CRUD for **Storage Locations** (inventory topology) unless explicitly moved into Location domain.  
  - Location domain may *reference* storage locations for site defaults, but the SoR is **CLARIFY** (Inventory vs Location).

### Boundary Clarifications (from frontend stories)
- **Shop Location CRUD** is in Location domain (SoR).
- **Pricing roster** screens are read-only and represent a **replica** of locations synced from `durion-hr`. The UI must not allow edits.
- **Site default staging/quarantine** configuration is owned by Location domain *as configuration*, but depends on **Inventory StorageLocation** entities for picker options. Treat this as a cross-domain integration.

---

## Key Entities / Concepts

| Entity | Description |
|---|---|
| **Location** | Physical or logical business site with unique `code`, `name`, `address`, `timezone`, `status`, optional `parentLocationId`, and operational metadata (hours/closures/buffers). |
| **Bay** | Service bay within a Location. Has `bayType`, `status`, `capacity`, and constraint references to services and skills. |
| **Mobile Unit** | Mobile service resource linked to exactly one `baseLocationId`. Has `status`, `maxDailyJobs`, `capabilityIds[]`, and optional/required `travelBufferPolicyId` depending on status. |
| **MobileUnitCoverageRule** | Links a Mobile Unit to a **Service Area** with `priority` and effective window (`effectiveStartAt`, `effectiveEndAt`). |
| **Service Area** | Geographic postal-code-based area used to define mobile unit coverage. CRUD ownership is **CLARIFY** (Location domain vs separate geo domain). |
| **Travel Buffer Policy** | Defines travel time buffers for mobile units. Types: `FIXED_MINUTES` and `DISTANCE_TIER` with JSON `policyConfiguration`. |
| **SyncLog** | Records synchronization runs when importing location data from external authoritative sources (e.g., durion-hr) for replica/roster use cases. |
| **Site Default Locations** | Configuration on a Site (Location acting as a site) pointing to default **staging** and **quarantine** storage locations for receiving topology. Storage locations are likely owned by Inventory domain (**CLARIFY**). |

### Relationships (actionable)
- `Location (1) -> (N) Bay` via `locationId`
- `MobileUnit (1) -> (1) Location` via `baseLocationId`
- `MobileUnit (1) -> (N) MobileUnitCoverageRule`
- `MobileUnitCoverageRule (N) -> (1) ServiceArea`
- `MobileUnit (N) -> (1) TravelBufferPolicy` via `travelBufferPolicyId` (required when `status=ACTIVE`)
- `Site (Location) (1) -> (1) DefaultStagingStorageLocationId` (**external ref**)
- `Site (Location) (1) -> (1) DefaultQuarantineStorageLocationId` (**external ref**)

---

## Invariants / Business Rules

### Location
- `code` is unique across all locations and **immutable after creation**.
- `name` is unique (case-insensitive, trimmed) across all locations.
- `timezone` must be a valid IANA time zone identifier.
  - **CLARIFY/TODO:** whether UI should use a backend-provided allowed list vs frontend static list.
- `parentLocationId`, if provided, must reference an existing Location.
  - **TODO:** enforce cycle prevention and max depth (not currently specified; UI should at least prevent self-parent).
- Status transitions:
  - Soft-deactivation supported (`ACTIVE` → `INACTIVE`); no hard deletes.
  - **CLARIFY:** whether reactivation (`INACTIVE` → `ACTIVE`) is allowed.
  - **CLARIFY:** whether INACTIVE locations are editable (beyond status).
- Operating hours:
  - Valid local times; no duplicates per day.
  - No overnight ranges; must satisfy `open < close`.
  - Closed days represented by omission.
  - **CLARIFY:** exact payload shape and whether empty list is allowed/required.
- Holiday closures:
  - Date-only entries; no duplicate dates.
  - **CLARIFY:** nullable vs empty array preference; `reason` constraints.
- Buffers:
  - `checkInBufferMinutes`, `cleanupBufferMinutes` are non-negative integers or null (null means “use global defaults”).
- Concurrency:
  - Prefer optimistic locking (`version` field) for updates and status changes.
  - On conflict, return `409`/`412` with a stable error code (e.g., `OPTIMISTIC_LOCK_FAILED`).

### Bays
- Bay names must be unique within their parent Location.
- `capacity.maxConcurrentVehicles` must be integer `>= 1`.
- `status` values: `ACTIVE` or `OUT_OF_SERVICE`.
  - Out-of-service bays are excluded from availability queries (consumer behavior; Location domain stores status).
- Constraint references:
  - `supportedServiceIds[]` must reference valid Service Catalog service IDs.
  - `requiredSkillRequirements[].skillId` must reference valid Skills IDs.
  - **CLARIFY:** ID types (UUID vs string codes) and lookup endpoints.

### Mobile Units
- Must have exactly one `baseLocationId` and it must reference an existing Location.
- `status` values: `ACTIVE | INACTIVE | OUT_OF_SERVICE`.
- `maxDailyJobs` integer `>= 0`.
- `travelBufferPolicyId`:
  - Required when `status=ACTIVE` (UI should enforce; backend must enforce).
  - Optional when not ACTIVE.
- Capabilities:
  - `capabilityIds[]` must be validated against Service Catalog (or a proxy).
  - Backend may return `503` if Service Catalog dependency is unavailable; UI should treat as retryable.

### Coverage Rules
- Each rule must reference a valid `serviceAreaId`.
- `priority` integer; lower number = higher priority (UI should communicate).
- Effective window:
  - If both provided, must satisfy `effectiveEndAt > effectiveStartAt`.
  - Null start/end allowed for immediate/indefinite.
- Updates should be **atomic replacement** of the full set for a mobile unit (avoid partial config).
  - **CLARIFY:** exact endpoint and semantics.

### Travel Buffer Policies
- `policyType` is required: `FIXED_MINUTES` or `DISTANCE_TIER`.
- FIXED_MINUTES:
  - Must include non-negative minutes in `policyConfiguration`.
  - **CLARIFY:** exact JSON schema keys/types.
- DISTANCE_TIER:
  - Tiers must be strictly increasing by `maxDistance` for non-null values.
  - Must include a catch-all tier with `maxDistance = null`.
  - `unit` handling:
    - Backend reference suggests stored config uses `"KM"`.
    - **CLARIFY:** whether UI can accept MI and convert, and what backend expects in stored config.

### Site Default Staging/Quarantine Storage Locations
- Both defaults are required (non-null) **unless migration allows nulls** (**CLARIFY**).
- Must be distinct: `defaultStagingLocationId != defaultQuarantineLocationId`.
  - Backend should return stable error code: `DEFAULT_LOCATION_ROLE_CONFLICT`.
- Selected storage locations must belong to the same site.
- Eligibility filtering:
  - **CLARIFY:** whether inactive storage locations can be selected.

### Cross-Domain Enforcement
- Rules such as “Inactive locations prevent new staffing assignments” are enforced by consuming domains (People/Scheduling/Workexec), not within Location domain.

---

## Events / Integrations

### Domain Events Emitted (Outbound)
- `pos.location.v1.LocationCreated`
- `pos.location.v1.LocationUpdated` (includes status changes)
- `pos.location.v1.BayCreated`, `pos.location.v1.BayUpdated`
- `pos.location.v1.MobileUnitCreated`, `pos.location.v1.MobileUnitUpdated`
- `pos.location.v1.TravelBufferPolicyCreated`, `pos.location.v1.TravelBufferPolicyUpdated`
- `pos.location.v1.SiteDefaultsUpdated` (staging/quarantine default storage location changes)
- `pos.location.v1.LocationSyncRunRecorded` (**TODO/CLARIFY:** if SyncLog is evented)

**Event payload guidance (actionable)**
- Include stable identifiers (`locationId`, `bayId`, `mobileUnitId`, `travelBufferPolicyId`, `syncId`).
- Include `version` and `updatedAt` where applicable.
- For updates, include either:
  - full “after” snapshot, or
  - `{changedFields, before, after}` diff (preferable for audit consumers).
- Do not emit PII; location address is not PII but may be sensitive—emit only if required by consumers (**CLARIFY**).

### Inbound Integrations (Inbound)
- Synchronization from authoritative external systems (e.g., `durion-hr`) to replicate and update location roster data.
- Validation of referenced entities:
  - Service Catalog (capabilities, services)
  - Skills domain (skills)
  - Service Area source (postal-code sets) (**CLARIFY** ownership)

### Integration Patterns
- **SyncLog** is the primary troubleshooting artifact for sync runs:
  - UI requires list + detail views.
  - Sync is idempotent; missing-from-feed locations become INACTIVE (soft).
- **Atomic replacement** for coverage rules:
  - Prefer a single replace endpoint to avoid partial updates and race conditions.
- **Picker endpoints**:
  - Base location picker uses location list endpoint with status filter.
  - Service/skill/capability/service-area pickers should support search + pagination to avoid loading entire catalogs.

---

## API Expectations (High-Level)

> The existing guide lists high-level endpoints; frontend stories require concrete contracts. Where unknown, mark as TODO/CLARIFY and do not invent payloads.

### Locations (SoR CRUD)
- `POST /v1/locations` — Create a new location (status defaults to `ACTIVE`).
  - **CLARIFY:** can status be set on create?
- `GET /v1/locations/{locationId}` — Retrieve location details.
- `GET /v1/locations?status=ACTIVE|INACTIVE|ALL` — List locations filtered by status.
  - Frontend stories assume default filter is `ACTIVE` for admin CRUD screens.
  - Pricing roster story asks whether default should be `ACTIVE` or `ALL` (**CLARIFY**).
- `PUT /v1/locations/{locationId}` — Full update (immutable `code`).
- `PATCH /v1/locations/{locationId}` — Partial update (preferred for status changes).

**Contract expectations**
- Support optimistic locking via `version` (request includes version; response returns updated version).
- Standard error payload should include:
  - `code` (stable machine-readable)
  - `message` (human-readable)
  - optional `fieldErrors[]` or `{field: message}` map
  - optional `correlationId`

### Bays
- `POST /v1/locations/{locationId}/bays` — Create bay under location.
- `GET /v1/locations/{locationId}/bays` — List bays with optional status filter.
- `GET /v1/locations/{locationId}/bays/{bayId}` — Get bay details.
- `PATCH /v1/locations/{locationId}/bays/{bayId}` — Update bay attributes (not required by current frontend story but expected).

**Create payload (as used by frontend story)**
```json
{
  "name": "Alignment Rack 1",
  "bayType": "ALIGNMENT",
  "status": "ACTIVE",
  "capacity": { "maxConcurrentVehicles": 1 },
  "supportedServiceIds": ["svc-..."],
  "requiredSkillRequirements": [{ "skillId": "skill-...", "minLevel": 2 }]
}
```

### Mobile Units
- `POST /v1/mobile-units` — Create mobile unit.
- `GET /v1/mobile-units` — List mobile units.
  - **CLARIFY:** confirm filters: `status`, `baseLocationId`, pagination params, response envelope.
- `GET /v1/mobile-units/{mobileUnitId}` — Detail.
- `PATCH /v1/mobile-units/{mobileUnitId}` — Update (preferred).

Eligibility query (consumer-facing)
- `GET /v1/mobile-units:eligible?postalCode=...&countryCode=...&at=...` — Query eligible mobile units by coverage.

### Coverage Rules
- **TODO/CLARIFY:** exact endpoint(s) and atomic replacement semantics.
  - Preferred pattern:
    - `PUT /v1/mobile-units/{mobileUnitId}/coverage-rules:replace`
    - Request: `{ "rules": [ ... ] }`
    - Response: updated set

### Service Areas
- **TODO/CLARIFY:** whether Location domain exposes:
  - `GET /v1/service-areas` (picker)
  - CRUD endpoints for service areas (if UI must manage them)

### Travel Buffer Policies
- `POST /v1/travel-buffer-policies` — Create policy.
- `GET /v1/travel-buffer-policies/{id}` — Retrieve policy.
- **TODO/CLARIFY:** list endpoint for picker:
  - `GET /v1/travel-buffer-policies`

### Site Default Locations (Receiving Topology)
- `PUT /api/v1/sites/{siteId}/default-locations` — Configure default staging and quarantine storage locations.
- `GET /api/v1/sites/{siteId}/default-locations` — Preferred explicit read endpoint.
  - **CLARIFY:** whether defaults are also embedded in `GET /api/v1/sites/{siteId}`.

Storage locations picker (Inventory domain likely)
- **TODO/CLARIFY:** `GET /api/v1/sites/{siteId}/storage-locations?status=ACTIVE` (example from story)

### Sync Logs (durion-hr replica)
- **TODO/CLARIFY:** exact endpoints/service names for:
  - listing locations (replica) and retrieving by `locationId`
  - listing sync logs and retrieving by `syncId`

---

## Security / Authorization Assumptions

### Authentication
- All endpoints require authenticated callers unless explicitly documented otherwise.

### Authorization (must be explicit; do not rely on UI-only gating)
- Modifying operations must require explicit permissions.
- Read operations may be allowed to broader roles (e.g., pricing admins) depending on policy.

**Permissions referenced by frontend stories (not yet confirmed)**
- Locations CRUD: `location:manage` (assumed) and/or `location:view` for read-only.
- Bays: `location.bay.manage` (**CLARIFY** exact string)
- Mobile Units / Coverage / Policies: `location.mobile-unit.manage` (referenced by story)
- Sync roster/logs: `location:view` or `location.sync:view` (**CLARIFY**)
- Site defaults (staging/quarantine): `inventory:topology.manage` vs `location:manage` (**CLARIFY**)

**Actionable guidance**
- Backend should return `403` for unauthorized access; frontend should:
  - render read-only views when allowed
  - hide/disable create/edit actions when permission context is available
- **CLARIFY:** how frontend checks permissions (session payload? endpoint? Moqui artifact authz?).

### Data exposure
- Avoid exposing internal-only identifiers beyond what is required by UI and integrations.
- For logs/audit, do not include secrets; include correlation IDs and stable error codes.

---

## Observability (Logs / Metrics / Tracing)

### Logging (backend)
- Structured logs at INFO for:
  - Location create/update/status change
  - Bay create/update/status change
  - Mobile unit create/update/status change
  - Coverage rules replace operation
  - Travel buffer policy create/update
  - Site defaults update
  - Sync run start/finish + outcome
- Include:
  - actor identifier (non-PII user id)
  - resource ids (`locationId`, `bayId`, `mobileUnitId`, `syncId`, `siteId`)
  - `correlationId` / request id
  - error `code` on failures
- Avoid logging full addresses unless required for debugging; prefer logging `locationId` and validation codes.

### Metrics (backend)
Minimum actionable metrics aligned to stories:
- `location_locations_created_total`
- `location_locations_updated_total`
- `location_locations_deactivated_total`
- `location_bays_created_total`
- `location_mobile_units_created_total`
- `location_coverage_rules_replace_total` + `..._failed_total`
- `location_travel_buffer_policies_created_total`
- `location_site_defaults_updated_total` + `..._conflict_total` (for `DEFAULT_LOCATION_ROLE_CONFLICT`)
- Sync:
  - `location_sync_runs_total{status=...}`
  - `location_sync_run_duration_seconds`
  - `location_sync_records_processed_total`, created/updated/skipped

### Tracing
- Propagate correlation IDs across:
  - Moqui frontend → backend API → downstream dependencies (Service Catalog, Skills, Inventory storage locations)
- For coverage rules replace and sync runs, create spans for:
  - validation
  - persistence
  - event emission
  - downstream calls

### Frontend observability (from stories)
- Log client-side actions at INFO/DEBUG without PII:
  - create/update/deactivate attempts and outcomes
  - save coverage replace attempt/outcome
  - site defaults save attempt/outcome
  - include `siteId/locationId/mobileUnitId` and correlation id if exposed via headers

---

## Testing Guidance

### Unit Tests (backend)
- Location:
  - `code` immutability
  - name uniqueness normalization (trim + case-insensitive)
  - timezone validation
  - operating hours validation (duplicates, open<close, no overnight)
  - holiday closure duplicate dates
  - buffer non-negative
  - parent reference exists; prevent self-parent; **TODO** cycle prevention if implemented
  - status transitions: deactivate; **CLARIFY** reactivation
- Bays:
  - unique name per location
  - capacity bounds
  - constraint ID validation behavior (invalid IDs -> 400 with details)
- Mobile units:
  - baseLocationId exists
  - travelBufferPolicy required when ACTIVE
  - maxDailyJobs bounds
- Coverage rules:
  - effective window validation
  - atomic replace semantics (set equality after replace)
- Travel buffer policies:
  - FIXED_MINUTES schema validation
  - DISTANCE_TIER tier ordering + catch-all
  - unit handling rules
- Site defaults:
  - staging != quarantine (`DEFAULT_LOCATION_ROLE_CONFLICT`)
  - storage location belongs to site
  - null handling policy (**CLARIFY**)

### Integration Tests
- End-to-end API tests for:
  - Locations list/detail/create/update/deactivate with optimistic locking
  - Bay create + fetch
  - Mobile unit create/update + list filters
  - Coverage rules replace endpoint (once confirmed)
  - Travel buffer policy create + fetch
  - Site defaults get/put + error codes
  - SyncLog list/detail + ordering by startedAt desc
- Contract tests for downstream dependencies:
  - Service Catalog lookup/validation (capabilities/services)
  - Skills lookup/validation
  - Storage locations list for site defaults picker

### Security Tests
- Verify `401/403` behavior for each endpoint:
  - read-only screens (pricing roster/logs) vs manage screens
- Ensure no privilege escalation via PATCH/PUT (e.g., changing immutable `code`).

### Frontend Tests (pragmatic)
- Component tests for:
  - operating hours editor validation
  - holiday closure duplicate prevention
  - site defaults distinctness validation + mapping `DEFAULT_LOCATION_ROLE_CONFLICT`
  - coverage rule effective window validation
  - distance tier editor (strictly increasing + catch-all)
- E2E tests (Cypress/Playwright) for:
  - create location + deactivate flow
  - create bay flow with 409 duplicate name handling
  - create mobile unit + coverage replace flow (once endpoints confirmed)
  - pricing roster list/detail + sync logs list/detail (read-only)

---

## Common Pitfalls

- **Domain confusion (SoR vs replica):**
  - Pricing roster locations are synced from `durion-hr` and must be read-only in UI. Do not add edit actions.
- **Undefined API contracts:**
  - Multiple frontend stories are blocked on exact Moqui service names/endpoints and payload shapes. Do not “guess” schemas; mark TODO and align once confirmed.
- **Timezone handling:**
  - Do not invent timezone conversion rules for coverage effective windows or timestamps. Explicitly decide whether to use base location timezone, user timezone, or UTC (**CLARIFY**).
- **Address schema mismatch:**
  - Do not implement a structured address UI unless backend contract is confirmed. If backend expects free-text, structured UI will break updates.
- **INACTIVE edit behavior:**
  - Frontend stories disagree/are uncertain whether INACTIVE locations are editable. Default to read-only until clarified to avoid policy violations.
- **Coverage rules partial updates:**
  - Avoid per-row PATCH semantics unless backend guarantees consistency. Prefer atomic replace to prevent drift.
- **Tier policy edge cases:**
  - Ensure exactly one catch-all tier (`maxDistance=null`) and prevent multiple catch-alls in UI.
- **Picker scalability:**
  - Service/skill/capability pickers must support search/pagination; loading full catalogs will be slow and brittle.
- **Optimistic locking:**
  - Always include `version` on update/deactivate if supported; handle `409 OPTIMISTIC_LOCK_FAILED` with reload UX.
- **Error mapping:**
  - Standardize on stable error codes (`INVALID_TIMEZONE`, `INVALID_OPERATING_HOURS`, `LOCATION_NAME_TAKEN`, `DEFAULT_LOCATION_ROLE_CONFLICT`) and map to field-level errors.

---

## Open Questions from Frontend Stories

> Consolidated and organized. These are blocking/clarifying items that must be answered to finalize contracts and UI behavior.

### A) Backend Contracts / Endpoints (Blocking)
1. **Location CRUD + list/detail:** What are the exact Moqui service names or REST endpoints, request/response payloads, and error formats for:
   - list locations
   - get location by `locationId`
   - create/update/deactivate location?
2. **Sync roster + SyncLog:** What are the exact Moqui service names or REST endpoints for:
   - listing locations (replica) and retrieving by `locationId`
   - listing sync logs and retrieving by `syncId`?
3. **Coverage rules replace:** What are the exact endpoints and payloads for creating/updating coverage rules, and what is the atomic replacement mechanism (single replace endpoint vs batch semantics)?
4. **Service Area endpoints:** Does the frontend need CRUD screens for Service Areas or only a picker? If CRUD is required, confirm endpoints and required fields.
5. **Capabilities lookup:** What endpoint should the frontend call to list valid capability IDs/names (service-catalog directly vs location-service proxy)? Provide response shape.
6. **Mobile unit list contract:** Confirm `GET /v1/mobile-units` exists and supports filters (`status`, `baseLocationId`), pagination params, and response envelope shape.
7. **Service/Skills lookup:** What are the canonical endpoints and response schemas for Service Catalog services and Skills lookup (search/pagination, id/displayName fields)? Are IDs UUIDs or string codes?
8. **Audit endpoint:** Is there an API/screen pattern for fetching audit events per entity (Location)? If yes, what endpoint and shape?

### B) Permissions / Roles (Blocking)
1. What permission(s)/roles gate:
   - viewing locations vs managing locations (`location:view` vs `location:manage`)?
   - viewing sync roster/logs (pricing admins read-only?)?
   - managing bays?
   - managing mobile units/coverage/policies (`location.mobile-unit.manage`)?
   - updating site default staging/quarantine (location vs inventory permission)?
2. How does the frontend check permissions (session claims, endpoint, Moqui artifact authz)? **CLARIFY**.

### C) Data Shape / Schema (Blocking)
1. **Address schema:** What exact `address` object fields does the backend expect/return? Required fields?
2. **Operating hours representation:** Confirm payload shape (day enum values, property names) and whether empty list is allowed vs required.
3. **Holiday closures constraints:** Nullable vs empty array; `reason` length/format constraints.
4. **Tags type:** Are `tags` an array, CSV string, or other structure? Any max length/count constraints?
5. **Region semantics:** Is `region` free-text or controlled vocabulary? Should it be filterable?

### D) Business Rules / UX Defaults (Needs Confirmation)
1. **Default status filter:** On Locations list (especially pricing roster), should default be `ACTIVE` or `ALL`?
2. **Status on create:** Can Admin set `status` during creation, or is it always defaulted to `ACTIVE` and only changeable after creation?
3. **Reactivation:** Is `INACTIVE → ACTIVE` allowed, or is deactivation permanent?
4. **Editing INACTIVE locations:** Should INACTIVE locations be editable (aside from status), or read-only?
5. **Parent hierarchy constraints:** Beyond “must reference existing”, are cycles prevented, is there max depth, and should UI enforce any constraints?
6. **Site defaults null handling:** Can existing sites have null staging/quarantine defaults during rollout/migration? If yes, how should UI behave?
7. **Storage location eligibility:** Should inactive storage locations be selectable for defaults?

### E) Timezone Rules (Blocking/High Risk)
1. **Coverage effective windows timezone:** Should effectiveStartAt/effectiveEndAt be entered/displayed in base location timezone, user/session timezone, or UTC with offsets?
2. **Timezone source:** Should UI use a backend-provided list of allowed IANA timezones or ship a static list?

### F) Navigation / Routing (Frontend Repo Conventions)
1. Canonical route/screen paths for admin maintenance screens in this repo (e.g., `/admin/locations` vs `/locations`).
2. Menu placement for:
   - Locations CRUD
   - Mobile Units / Travel Buffer Policies / Service Areas
   - Sync roster/logs (Admin vs Location Management vs Pricing)
   - Site default locations (Inventory Topology vs Location)

---

*End of AGENT_GUIDE.md*
```
