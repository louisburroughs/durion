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
  - [ ] `status` is one of `ACTIVE|INACTIVE|OUT_OF_SERVICE`.
  - [ ] `travelBufferPolicyId` is required when `status=ACTIVE` and optional otherwise.
  - [ ] `capabilityIds[]` values come from capabilities lookup; unknown IDs returned by backend are surfaced clearly.
- [ ] Validate **Coverage Rule** rules:
  - [ ] `serviceAreaId` required.
  - [ ] `priority` required integer (documented ordering: lower number = higher priority).
  - [ ] If both effective timestamps provided, enforce `effectiveEndAt > effectiveStartAt`.
  - [ ] Null start/end semantics match backend (immediate/indefinite).
- [ ] Validate **Travel Buffer Policy** rules:
  - [ ] `name` required; `policyType` required.
  - [ ] FIXED_MINUTES config enforces non-negative minutes (exact key names/types per contract).
  - [ ] DISTANCE_TIER config:
    - [ ] At least one tier.
    - [ ] `bufferMinutes >= 0` for each tier.
    - [ ] `maxDistance` strictly increasing for non-null tiers.
    - [ ] Exactly one catch-all tier with `maxDistance = null`.
    - [ ] Unit handling (KM/MI) matches backend contract; UI does not guess conversions.
- [ ] Validate **Site Default Locations** rules:
  - [ ] Both `defaultStagingLocationId` and `defaultQuarantineLocationId` are required (unless rollout allows nulls—see Open Questions).
  - [ ] Staging and quarantine defaults must be distinct; enforce client-side and handle backend error code `DEFAULT_LOCATION_ROLE_CONFLICT`.
  - [ ] Picker options are filtered to storage locations belonging to the site; backend still validates belongs-to-site.
  - [ ] Eligibility rules for inactive storage locations are enforced per contract (ACTIVE-only vs allow INACTIVE).
- [ ] Validate **Location Sync roster/logs** display rules:
  - [ ] Locations are read-only; no edit actions are present.
  - [ ] Unknown status values are displayed safely (raw value, neutral styling).
  - [ ] `tags` rendering supports the actual type (array vs CSV) without breaking.

---

## API Contract
- [ ] Confirm the exact Moqui service names or REST endpoints exist for all screens:
  - [ ] Locations list/get/create/update/status change.
  - [ ] Bays create/list/get under a location.
  - [ ] Mobile units list/get/create/update.
  - [ ] Coverage rules replace/update semantics for a mobile unit.
  - [ ] Travel buffer policies list/get/create.
  - [ ] Site default locations get/update and storage locations list for a site.
  - [ ] Sync logs list/get and location roster list/get.
- [ ] Verify list endpoints support required query parameters and response envelopes:
  - [ ] Pagination: `pageIndex/pageSize` (or equivalent) and `totalCount`.
  - [ ] Sorting: supported fields and order.
  - [ ] Filtering: status filters (ACTIVE/INACTIVE/ALL), baseLocationId, run status, etc.
- [ ] Verify update semantics are consistent and safe:
  - [ ] PUT vs PATCH usage matches backend expectations.
  - [ ] Optimistic locking/version is included where required (e.g., Location updates/deactivate).
  - [ ] Coverage rules update is **atomic replacement** (single request results in exact saved set).
- [ ] Verify error response format is consistent and mappable:
  - [ ] Field-level errors include field identifiers usable by UI.
  - [ ] Domain error codes are stable (e.g., `INVALID_TIMEZONE`, `INVALID_OPERATING_HOURS`, `LOCATION_NAME_TAKEN`, `OPTIMISTIC_LOCK_FAILED`, `DEFAULT_LOCATION_ROLE_CONFLICT`).
  - [ ] 404 behavior is defined for missing `locationId/siteId/bayId/mobileUnitId/syncId`.
- [ ] Verify default filter behavior is implemented as specified (e.g., Locations list default `status=ACTIVE` vs `ALL`).
- [ ] Verify timestamp fields returned by APIs include timezone/offset information or are explicitly documented as UTC.

---

## Events & Idempotency
- [ ] Verify UI actions that can be retried are safe:
  - [ ] Create actions prevent double-submit (disable Save while in-flight).
  - [ ] Update/deactivate actions handle retries without creating duplicates.
- [ ] Verify coverage rules “replace” operation is idempotent for identical payloads.
- [ ] Verify sync roster/log screens do not imply a “rerun sync” action unless a backend idempotent trigger exists.
- [ ] If backend emits domain events for changes (LocationUpdated, MobileUnitUpdated, etc.), confirm UI does not depend on events for correctness (UI should rely on API responses/refetch).

---

## Security
- [ ] Verify all screens enforce AuthN/AuthZ via backend (401/403) and UI handles them safely:
  - [ ] 401 triggers login/session-expired flow.
  - [ ] 403 shows access denied and does not render partial sensitive data.
- [ ] Confirm permission model is applied consistently:
  - [ ] Location CRUD gated by `location:manage` (or confirmed equivalent).
  - [ ] Read-only location roster/logs gated by `location:view` (or confirmed equivalent).
  - [ ] Mobile unit/policy/coverage management gated by `location.mobile-unit.manage` (or confirmed equivalent).
  - [ ] Bay management gated by appropriate permission (confirmed string).
  - [ ] Site default locations update gated by appropriate permission (confirmed string).
- [ ] Verify UI does not leak sensitive data in logs/errors:
  - [ ] No tokens, headers, or full payload dumps in console logs.
  - [ ] Error dialogs show correlation/reference IDs only (when available), not stack traces by default.
- [ ] Verify IDOR protections are enforced server-side and validated in testing (e.g., cannot update defaults for a site you cannot access).

---

## Observability
- [ ] Verify correlation/request IDs are propagated and surfaced:
  - [ ] Frontend includes correlation ID header if standard in app.
  - [ ] Frontend surfaces backend-provided correlation ID in error UI (non-sensitive “reference id”).
- [ ] Verify audit metadata is displayed when available:
  - [ ] Location detail shows `createdAt/updatedAt/version` and optionally `updatedBy`.
  - [ ] Site defaults screen shows “last updated at/by” if provided.
  - [ ] Bay/MobileUnit/Policy detail shows created/updated timestamps if provided.
- [ ] Verify structured client logging exists for key actions (create/update/deactivate/save coverage/save defaults) including entity IDs and outcome, without PII.
- [ ] Verify sync log screens clearly display run status and counts and handle “running” logs (missing `syncFinishedAt`) gracefully.

---

## Performance & Failure Modes
- [ ] Verify list screens are paginated and do not fetch unbounded datasets:
  - [ ] Locations roster list.
  - [ ] Sync logs list.
  - [ ] Mobile units list.
  - [ ] Travel buffer policies list (if present).
  - [ ] Service/skills/capabilities pickers use search/autocomplete or pagination where needed.
- [ ] Verify initial page loads avoid N+1 calls; target minimal calls per screen (e.g., defaults + options).
- [ ] Verify graceful handling of dependency outages:
  - [ ] Capability lookup/service catalog unavailable → show retryable error and prevent invalid saves.
  - [ ] Skills/services lookup unavailable → allow bay creation without constraints if constraints are optional; otherwise block with clear message.
- [ ] Verify empty states are explicit and actionable:
  - [ ] No storage locations available for site defaults.
  - [ ] No service areas available for coverage rules.
  - [ ] No travel buffer policies available when creating ACTIVE mobile unit.
  - [ ] No synced locations/logs yet.
- [ ] Verify concurrency failure handling:
  - [ ] Location optimistic lock conflict shows reload prompt and prevents silent overwrite.
  - [ ] Duplicate name conflicts (Location/Bay/MobileUnit) map to the correct field and preserve user input.

---

## Testing
- [ ] Unit tests cover client-side validation rules:
  - [ ] Location timezone required + invalid timezone mapping.
  - [ ] Operating hours duplicate day / close<=open / overnight rejection.
  - [ ] Holiday closure duplicate date prevention.
  - [ ] Buffers non-negative integer validation.
  - [ ] Bay capacity `>=1`, enum validation, trimming name.
  - [ ] Mobile unit travel buffer policy required when ACTIVE; maxDailyJobs `>=0`.
  - [ ] Coverage effective window validation.
  - [ ] Distance tier validation (increasing distances, single catch-all).
  - [ ] Site defaults distinctness validation.
- [ ] Integration/API contract tests (or contract fixtures) validate:
  - [ ] Correct endpoints, query params, and payload shapes are used.
  - [ ] Error code mapping to UI fields/banners works for 400/401/403/404/409/422/503.
  - [ ] Optimistic locking/version is sent and handled.
  - [ ] Coverage rules replace semantics result in exact saved set.
- [ ] Authorization tests validate:
  - [ ] Read-only users cannot see create/edit actions.
  - [ ] Forbidden responses do not leak data and disable editing.
- [ ] E2E tests cover critical flows:
  - [ ] Create/edit/deactivate location.
  - [ ] Create bay under location and handle duplicate name.
  - [ ] Create mobile unit, set OUT_OF_SERVICE, configure coverage, create policy.
  - [ ] Configure site default staging/quarantine and handle conflict.
  - [ ] View synced locations roster and sync logs with filters/pagination.

---

## Documentation
- [ ] Document route paths, menu placement, and screen ownership (admin vs locations section).
- [ ] Document API/service contracts used by each screen (endpoints, params, payloads, response envelopes).
- [ ] Document validation rules implemented client-side and which are server-authoritative.
- [ ] Document error code mappings used by UI (code → field/message).
- [ ] Document timezone handling rules for:
  - [ ] Location timezone field.
  - [ ] Coverage effective windows display/input timezone.
  - [ ] Display of audit timestamps (user locale vs location timezone vs UTC).
- [ ] Document read-only vs editable behavior for:
  - [ ] HR-synced location roster.
  - [ ] INACTIVE locations (editability).
- [ ] Record decisions resolving the Open Questions below and link to the decision record/ticket.

---

## Open Questions to Resolve
- [ ] What are the exact Moqui service names or REST endpoints (and response envelopes/error formats) for:
  - [ ] listing locations and retrieving by `locationId`
  - [ ] creating/updating/deactivating locations
  - [ ] listing sync logs and retrieving by `syncId`
  - [ ] site default locations get/update and storage locations list for a site
  - [ ] mobile units list/get/create/update
  - [ ] coverage rules atomic replacement endpoint/payload
  - [ ] travel buffer policies list/get/create
  - [ ] bays create/list/get under a location
- [ ] What permissions/roles gate each screen and action?
  - [ ] Is there a `location:view` vs `location:manage` split?
  - [ ] Can pricing admins access roster/logs read-only without manage permissions?
  - [ ] What permission gates site default locations update?
  - [ ] What permission gates bay management?
- [ ] Locations list default status filter: should it default to `ACTIVE` or `ALL`?
- [ ] `tags` type for synced locations: array vs CSV vs other; any max length/count constraints?
- [ ] `region` semantics: free-text vs controlled vocabulary; should it be filterable?
- [ ] Menu placement: where should Locations, Mobile Units, Policies, Sync Logs live in POS navigation?
- [ ] Service Area management scope: does frontend need CRUD for Service Areas or only a picker to link existing ones?
- [ ] Capabilities lookup: which endpoint provides valid capability IDs/names (service-catalog direct vs proxy), and what is the response shape?
- [ ] Mobile unit list endpoint: confirm filters (`status`, `baseLocationId`), pagination params, and response envelope.
- [ ] Travel buffer policy FIXED_MINUTES schema: exact `policyConfiguration` JSON keys and types.
- [ ] DISTANCE_TIER unit handling: must stored config always be `"KM"`? Can UI accept MI and convert, or must it send KM only?
- [ ] Timezone rule for coverage effective windows: entered/displayed in base location timezone, user/session timezone, or UTC with offsets?
- [ ] Address schema for Location: structured object fields and requiredness vs free-text string.
- [ ] Operating hours payload shape: day enum values/property names; is empty list allowed?
- [ ] Holiday closures: nullable vs empty array; `reason` constraints.
- [ ] Editing INACTIVE locations: read-only or editable (aside from status)?
- [ ] Audit UI endpoint: is there a standard endpoint/pattern to fetch audit events per Location (and other entities)?
- [ ] Parent hierarchy constraints: prevent cycles/max depth; should UI enforce any constraints beyond “must exist”?
- [ ] Status on create: can Admin set status during creation or is it always defaulted to ACTIVE?
- [ ] Reactivation: is `INACTIVE → ACTIVE` allowed or is deactivation permanent?
- [ ] Timezone source: should UI use a backend-provided allowed timezone list or a static frontend list?
