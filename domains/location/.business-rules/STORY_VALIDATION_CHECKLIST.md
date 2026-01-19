# Location Domain Story Validation Checklist

This checklist is intended for engineers and reviewers to validate story implementations within the `location` domain. It covers key aspects from scope to documentation to ensure secure, consistent, and high-quality delivery across the new frontend admin screens (Locations CRUD, Bays, Mobile Units/Coverage/Policies, Site Default Locations, and Location Sync roster/logs).

---

## Scope/Ownership
- [ ] Confirm each screen/story is correctly labeled `domain:location` vs another SoR (notably: **Site default staging/quarantine** depends on **Storage Locations** which may be inventory-owned).
- [ ] Verify the UI does not implement cross-domain enforcement (e.g., “inactive locations prevent staffing assignments”, “receiving uses staging by default”, scheduling eligibility computations).
- [ ] Confirm ownership boundaries for reference data pickers:
  - [ ] Capabilities come from Service Catalog (read-only lookup).
  - [ ] Services/Skills come from their authoritative domains (read-only lookup).
  - [ ] Service Areas ownership and CRUD scope is explicitly decided (picker-only vs CRUD).
- [ ] Verify read-only vs editable intent is consistent:
  - [ ] HR-synced location roster is **read-only** in POS UI.
  - [ ] Shop Locations CRUD is editable (subject to permissions).
- [ ] Confirm navigation placement and route conventions match repo standards (admin vs locations section) and are consistent across all new screens.

---

## Data Model & Validation
- [ ] Validate all IDs are treated as opaque identifiers (no parsing assumptions): `locationId`, `siteId`, `bayId`, `mobileUnitId`, `serviceAreaId`, `travelBufferPolicyId`, `syncId`.
- [ ] Verify **Location** create/edit validation:
  - [ ] `code` is required on create (if part of contract) and is **immutable** on update (UI must not send changes).
  - [ ] `name` is required; trim whitespace before submit; handle case-insensitive uniqueness conflicts from backend.
  - [ ] `timezone` is required and validated as an IANA timezone identifier; invalid values are rejected and surfaced inline.
  - [ ] `parentLocationId` (optional) must reference an existing location; UI prevents selecting self as parent.
  - [ ] Status transitions follow backend rules (ACTIVE/INACTIVE); UI does not assume reactivation unless confirmed.
- [ ] Validate **Address** handling matches backend schema:
  - [ ] UI collects only fields supported by the contract (no invented fields).
  - [ ] Required address fields are enforced client-side once schema is confirmed.
- [ ] Validate **Operating Hours** editor rules:
  - [ ] No duplicate day entries.
  - [ ] `open < close` and no overnight ranges unless backend explicitly supports them.
  - [ ] Empty list behavior matches backend rules (allowed vs required).
- [ ] Validate **Holiday Closures** rules:
  - [ ] No duplicate dates.
  - [ ] `reason` constraints (length/format) are enforced if specified; otherwise only basic string handling.
  - [ ] Null vs empty-array behavior matches backend contract.
- [ ] Validate **Buffer overrides** on Location:
  - [ ] `checkInBufferMinutes` and `cleanupBufferMinutes` accept null or non-negative integers only.
  - [ ] Negative values are blocked client-side and rejected server-side.
- [ ] Validate **Bay create** rules:
  - [ ] `name` required and trimmed.
  - [ ] `bayType` must be one of the allowed enum values.
  - [ ] `status` defaults to ACTIVE; allowed values are `ACTIVE|OUT_OF_SERVICE`.
  - [ ] `capacity.maxConcurrentVehicles` is required integer and `>= 1`.
  - [ ] `supportedServiceIds[]` and `requiredSkillRequirements[].skillId` are IDs from pickers (no free-text).
  - [ ] `requiredSkillRequirements[].minLevel` (if present) is integer `>= 1`.
  - [ ] Duplicate bay name within a location is handled via backend `409` and mapped to the name field.
- [ ] Validate **Mobile Unit** rules:
  - [ ] `name` required.
  - [ ] `baseLocationId` required and must exist.
  - [ ] `maxDailyJobs` required integer `>= 0`.
  # STORY_VALIDATION_CHECKLIST.md

  ## Summary

  This checklist validates Location-domain story implementations for correctness, consistency, and safety across Locations CRUD, Bays, Mobile Units/Coverage/Policies, Site Default Locations, and Sync roster/log screens. It has been updated to include testable acceptance criteria for each previously open question, using safe defaults from `AGENT_GUIDE.md` when contracts are not yet finalized.

  ## Completed items

  - [x] Updated acceptance criteria for each resolved open question

  ## Scope / Ownership

  - [ ] Confirm story labeled `domain:location`
  - [ ] Verify primary actor(s) and permissions
  - [ ] Confirm Storage Location entities remain Inventory-owned; Location only references them for site defaults
  - [ ] Verify read-only vs editable behavior is consistent (sync roster/logs are read-only)

  ## Data Model & Validation

  - [ ] Validate required inputs and types: `locationId`, `siteId`, `bayId`, `mobileUnitId`, `serviceAreaId`, `travelBufferPolicyId`, `syncId`
  - [ ] Validate `code` required on create and immutable on update
  - [ ] Validate timezone is IANA ID and loaded from backend allowed list
  - [ ] Validate operating hours rules: one per day, `open < close`, no overnight, empty list allowed
  - [ ] Validate holiday closures rules: unique dates, optional reason max 255, null allowed
  - [ ] Validate buffers are null or non-negative integers
  - [ ] Validate DISTANCE_TIER tiers: increasing `maxDistance`, exactly one catch-all `maxDistance=null`, `bufferMinutes >= 0`

  ## API Contract

  - [ ] Verify endpoints and response envelopes are documented per screen
  - [ ] Verify pagination: `pageIndex/pageSize` (or equivalent) and `totalCount`
  - [ ] Verify stable error codes and field-level error mapping
  - [ ] Verify optimistic locking conflicts are handled (`OPTIMISTIC_LOCK_FAILED`)

  ## Events & Idempotency

  - [ ] UI prevents double-submit and supports safe retries
  - [ ] Coverage rules replace is idempotent for identical payloads

  ## Security

  - [ ] Permission gating enforced server-side (401/403 handling)
  - [ ] UI does not leak sensitive data in logs/errors
  - [ ] Verify IDOR protections via tests (cannot mutate outside authorized scope)

  ## Observability

  - [ ] Correlation/request IDs surfaced in error UI when provided
  - [ ] Audit fields displayed (`createdAt/updatedAt/version`) and optional audit events when available

  ## Acceptance Criteria (per resolved question)

  ### Q: What are the exact Moqui service names or REST endpoints (and response envelopes/error formats) for the Location domain screens?

  - Acceptance: Each screen includes a short contract note (endpoint/service name + request/response fields) in its story/PR and uses versioned `/rest/api/v1/location/...` paths.
  - Test Fixtures:
    - List locations: `status=ACTIVE`, `pageIndex=0`, `pageSize=25`
  - Example API request/response:
  ```http
  GET /rest/api/v1/location/locations?status=ACTIVE&pageIndex=0&pageSize=25
  ```

  ### Q: What permissions/roles gate each screen and action?

  - Acceptance: Read-only pages are accessible with `location:view` and mutations require `location:manage` (or a documented stronger permission). Unauthorized mutation returns 403.
  - Test Fixtures:
    - User A: view-only
    - User B: manage
  - Example API request/response:
  ```http
  POST /rest/api/v1/location/locations
  ```

  ### Q: Locations list default status filter: should it default to ACTIVE or ALL?

  - Acceptance: CRUD list defaults to `status=ACTIVE`; sync/ops lists default to `status=ALL` and expose filter control.
  - Test Fixtures:
    - CRUD list load without explicit filter
  - Example API request/response:
  ```http
  GET /rest/api/v1/location/locations
  ```

  ### Q: tags type for synced locations: array vs CSV vs other; any constraints?

  - Acceptance: UI renders tags safely whether `tags` is `string[]` or CSV string. No runtime error for either shape.
  - Test Fixtures:
    - `tags: ["foo", "bar"]`
    - `tags: "foo,bar"`
  - Example API request/response:
  ```json
  { "locationId": "loc-1", "tags": ["foo", "bar"] }
  ```

  ### Q: region semantics: free-text vs controlled vocabulary; should it be filterable?

  - Acceptance: UI treats `region` as display-only free text and does not enforce vocabulary. Filtering is optional and only enabled if the API provides it.
  - Test Fixtures:
    - `region: "West"`
  - Example API request/response:
  ```json
  { "locationId": "loc-1", "region": "West" }
  ```

  ### Q: Menu placement: where should Locations, Mobile Units, Policies, Sync Logs live in navigation?

  - Acceptance: All screens are reachable from a single navigation area per the frontend convention; no duplicate entry points.
  - Test Fixtures:
    - Navigation config snapshot test
  - Example API request/response:
  ```text
  N/A (navigation)
  ```

  ### Q: Service Area management scope: CRUD vs picker-only?

  - Acceptance: No Service Area mutation UI exists unless a dedicated CRUD story exists; coverage rules only link existing service areas.
  - Test Fixtures:
    - Coverage rule editor uses picker
  - Example API request/response:
  ```http
  GET /rest/api/v1/location/service-areas?pageIndex=0&pageSize=25
  ```

  ### Q: Capabilities lookup endpoint provides valid capability IDs/names; response shape?

  - Acceptance: Capability IDs are selected from a read-only lookup API and sent as IDs only (no free-text). If lookup is unavailable, UI blocks save with retryable error.
  - Test Fixtures:
    - Catalog API 503
  - Example API request/response:
  ```http
  GET /rest/api/v1/catalog/capabilities?q=oil
  ```

  ### Q: Mobile unit list endpoint: confirm filters, pagination, response envelope.

  - Acceptance: `status` and `baseLocationId` filters work; response includes `items[]` and `totalCount`.
  - Test Fixtures:
    - `status=ACTIVE&baseLocationId=loc-1`
  - Example API request/response:
  ```http
  GET /rest/api/v1/location/mobile-units?status=ACTIVE&baseLocationId=loc-1&pageIndex=0&pageSize=25
  ```

  ### Q: Travel buffer policy FIXED_MINUTES schema keys and types.

  - Acceptance: FIXED_MINUTES uses `policyConfiguration.minutes` (integer >= 0). Invalid payload is rejected with 400/422.
  - Test Fixtures:
    - `minutes = -1` (invalid)
  - Example API request/response:
  ```json
  { "policyType": "FIXED_MINUTES", "policyConfiguration": { "minutes": 15 } }
  ```

  ### Q: DISTANCE_TIER unit handling: must stored config always be KM? MI conversion?

  - Acceptance: UI captures and submits distances in KM only and labels it. No MI entry or conversion exists in v1.
  - Test Fixtures:
    - Tier list with KM distances
  - Example API request/response:
  ```json
  { "policyType": "DISTANCE_TIER", "policyConfiguration": { "unit": "KM", "tiers": [] } }
  ```

  ### Q: Timezone rule for coverage effective windows: base location timezone vs user timezone vs UTC?

  - Acceptance: UI submits ISO-8601 timestamps with offsets; backend stores them as instants. Display may use user timezone but preserves instant.
  - Test Fixtures:
    - `effectiveStartAt=2026-01-01T08:00:00-05:00`
  - Example API request/response:
  ```json
  { "effectiveStartAt": "2026-01-01T08:00:00-05:00", "effectiveEndAt": null }
  ```

  ### Q: Address schema for Location: structured object fields vs free-text.

  - Acceptance: UI only sends address fields defined by the backend contract. If contract is unknown, UI uses a minimal address input and sends it in the backend-expected field.
  - Test Fixtures:
    - Contract fixture defines allowed keys
  - Example API request/response:
  ```json
  { "address": { "line1": "123 Main St", "city": "Austin" } }
  ```

  ### Q: Operating hours payload shape and empty list allowed?

  - Acceptance: UI uses `[{dayOfWeek, open, close}]` with `HH:mm`; empty list is allowed and round-trips without server error.
  - Test Fixtures:
    - `operatingHours=[]`
  - Example API request/response:
  ```json
  { "operatingHours": [{"dayOfWeek":"MONDAY","open":"08:00","close":"17:00"}] }
  ```

  ### Q: Holiday closures: nullable vs empty array; reason constraints.

  - Acceptance: UI supports null and empty; prevents duplicate dates; trims reason and enforces max length 255.
  - Test Fixtures:
    - Duplicate date attempt
  - Example API request/response:
  ```json
  { "holidayClosures": [{"date":"2026-12-25","reason":"Christmas"}] }
  ```

  ### Q: Editing INACTIVE locations: read-only or editable? Reactivation allowed?

  - Acceptance: INACTIVE locations are editable for non-identity fields and can be reactivated via explicit action; code remains immutable.
  - Test Fixtures:
    - INACTIVE location update of name
  - Example API request/response:
  ```http
  PATCH /rest/api/v1/location/locations/{locationId}
  ```

  ### Q: Audit UI endpoint: standard endpoint/pattern to fetch audit events per entity?

  - Acceptance: If audit endpoint exists, UI can fetch and display audit events; otherwise it displays only core audit fields.
  - Test Fixtures:
    - Audit endpoint missing returns 404
  - Example API request/response:
  ```http
  GET /rest/api/v1/audit/events?entityType=Location&entityId=loc-1
  ```

  ### Q: Parent hierarchy constraints: prevent cycles/max depth; UI constraints?

  - Acceptance: UI blocks selecting self as parent; backend rejects cycle creation.
  - Test Fixtures:
    - Attempt to set parent to self
  - Example API request/response:
  ```json
  { "parentLocationId": "loc-1" }
  ```

  ### Q: Status on create: can admin set status during creation?

  - Acceptance: Create defaults to ACTIVE; UI does not offer a status selector.
  - Test Fixtures:
    - Create request without status
  - Example API request/response:
  ```json
  { "code":"SHOP-1","name":"Main Shop" }
  ```

  ### Q: Site default staging/quarantine: null handling during rollout?

  - Acceptance: UI shows “Not configured” when defaults are null and allows setting them if authorized.
  - Test Fixtures:
    - `defaultStagingLocationId=null`
  - Example API request/response:
  ```json
  { "defaultStagingLocationId": null, "defaultQuarantineLocationId": null }
  ```

  ### Q: Storage location eligibility: can inactive storage locations be selected?

  - Acceptance: Picker shows ACTIVE storage locations only; backend rejects inactive selections.
  - Test Fixtures:
    - Attempt selecting INACTIVE storage location
  - Example API request/response:
  ```http
  GET /rest/api/v1/inventory/sites/{siteId}/storage-locations?status=ACTIVE
  ```

  ### Q: Timezone source: backend-provided list vs static frontend list?

  - Acceptance: UI uses backend list endpoint and falls back to static list only when the endpoint is unavailable.
  - Test Fixtures:
    - Timezone list endpoint returns 500
  - Example API request/response:
  ```http
  GET /rest/api/v1/location/timezones
  ```

## End

End of document.
- [ ] Editing INACTIVE locations: read-only or editable (aside from status)?
- [ ] Audit UI endpoint: is there a standard endpoint/pattern to fetch audit events per Location (and other entities)?
- [ ] Parent hierarchy constraints: prevent cycles/max depth; should UI enforce any constraints beyond “must exist”?
- [ ] Status on create: can Admin set status during creation or is it always defaulted to ACTIVE?
- [ ] Reactivation: is `INACTIVE → ACTIVE` allowed or is deactivation permanent?
- [ ] Timezone source: should UI use a backend-provided allowed timezone list or a static frontend list?
