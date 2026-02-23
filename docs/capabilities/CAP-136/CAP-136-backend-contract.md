---
title: CAP-136 Backend Contract — Manage Locations, Bays, and Mobile Units
capability_id: CAP:136
domain: location
status: draft
openapi_source: pos-location/openapi.yaml
owner_repo: louisburroughs/durion-positivity-backend
issues:
  - 76
  - 77
  - 78
---

# CAP-136: Manage Locations, Bays, and Mobile Units (Backend Contract)

Overview
--------

This document captures implementation-focused contract details for CAP-136. It complements the domain-level backend contract guide and is intended for backend engineers implementing the `pos-location` service and contract tests (provider tests).

Gateway base
------------

All gateway endpoints use the prefix: `http://localhost:8080/v1/location` (append the OpenAPI path). Example: OpenAPI `/v1/locations` → gateway `http://localhost:8080/v1/location/v1/locations`.

Gateway endpoint table (story grouped)
-------------------------------------

Locations (Issue #78)

- POST http://localhost:8080/v1/location/v1/locations — Create location (LocationRequestDTO)
- GET  http://localhost:8080/v1/location/v1/locations — List locations (default: ACTIVE)
- GET  http://localhost:8080/v1/location/v1/locations/{locationId} — Get by id
- PUT  http://localhost:8080/v1/location/v1/locations/{locationId} — Update location
- DELETE http://localhost:8080/v1/location/v1/locations/{locationId} — Delete location

Bays (Issue #77)

- POST http://localhost:8080/v1/location/v1/locations/{locationId}/bays — Create bay
- GET  http://localhost:8080/v1/location/v1/locations/{locationId}/bays/{bayId} — Get bay / list bays
- DELETE http://localhost:8080/v1/location/v1/locations/{locationId}/bays/{bayId} — Delete bay
- GET  http://localhost:8080/v1/location/v1/locations/bays — List/all bays
- PUT  http://localhost:8080/v1/location/v1/locations/bays — Bulk manage bays

Mobile Units (Issue #76)

- POST http://localhost:8080/v1/location/v1/locations/{locationId}/mobileUnit — Create mobile unit for a location
- GET  http://localhost:8080/v1/location/v1/locations/{locationId}/mobileUnit/{unitId} — Get mobile unit / list
- DELETE http://localhost:8080/v1/location/v1/locations/{locationId}/mobileUnit/{unitId} — Delete mobile unit
- GET /eligible (service-side query) — Eligible mobile units: see behavioral assertions below
- PUT  http://localhost:8080/v1/location/v1/locations/mobileUnit — Bulk manage mobile units

Key behavioral assertions (by issue)
-----------------------------------

Issue #78 — Locations

- Creating a location (`POST /v1/locations`) returns `201` on success.
- `name` is unique: uniqueness enforced on `normalizedName = lower(trim(name))` → duplicate (case-insensitive) returns `409 LOCATION_NAME_TAKEN`.
- `timezone` must be valid via `ZoneId.of()`; invalid timezone → `400 INVALID_TIMEZONE`.
- `operatingHours` entries must satisfy `open < close`, must not span overnight, and may not contain duplicate days → otherwise `400 INVALID_OPERATING_HOURS`.
- `PATCH/PUT /v1/locations/{id}` with `{status: INACTIVE}` performs deactivate flow; only `ACTIVE -> INACTIVE` allowed.
- Optimistic locking via JPA `@Version`: concurrent/stale update → `409 OPTIMISTIC_LOCK_FAILED`.
- `GET /v1/locations` returns only `ACTIVE` by default; `?status=ALL` returns all.

Issue #77 — Bays

- `POST /v1/locations/{locationId}/bays` returns `201` on success; unknown `locationId` → `404`.
- Bay `name` must be unique within a `locationId` (case-insensitive) → duplicate → `409`.
- `OUT_OF_SERVICE` status is excluded from `GET /v1/locations/{locationId}/bays?status=ACTIVE` results.
- `bayType` enumerated values: `GENERAL_SERVICE`, `ALIGNMENT`, `TIRE_SERVICE`, `HEAVY_DUTY`, `INSPECTION`, `WASH_DETAIL`.
- `supportedServiceIds` and `skillId` are validated; if any IDs are unknown return `400` with the list of invalid IDs.

Issue #76 — Mobile Units

- `POST /v1/mobile-units` (via gateway mapping) returns `201` on success.
- Creating a mobile unit with `status=ACTIVE` MUST include `travelBufferPolicyId`, non-empty `capabilityIds`, and at least one coverage rule; otherwise `400`.
- Duplicate mobile unit `name` within the same `baseLocationId` → `409`.
- `capabilityIds` must be validated against the service capability catalog; invalid IDs → `400` listing invalid IDs. If catalog is unavailable → `503`.
- `DISTANCE_TIER` coverage rules require strictly ascending `maxDistance` values and a catch-all tier (`maxDistance: null`) must be present; invalid configuration → `400`.
- `GET /v1/mobile-units:eligible?postalCode=&countryCode=&at=` returns ACTIVE units with effective coverage, ordered by `priority` ascending.
- `PUT /v1/mobile-units/{id}/coverage-rules` semantics: atomic replace (all-or-nothing).

Contract test hints (CP-136-001 → CP-136-015)
------------------------------------------------

Provide provider contract tests exercising the following scenarios (map to test IDs below):

- CP-136-001: Create Location success (201) and returns LocationResponseDTO with generated `id`.
- CP-136-002: Create Location with duplicate name (case-insensitive) returns `409 LOCATION_NAME_TAKEN`.
- CP-136-003: Create Location invalid timezone returns `400 INVALID_TIMEZONE`.
- CP-136-004: Create Location invalid operatingHours returns `400 INVALID_OPERATING_HOURS`.
- CP-136-005: Update Location to `INACTIVE` performs deactivate and emits `LOCATION_DEACTIVATED`.
- CP-136-006: Stale update returns `409 OPTIMISTIC_LOCK_FAILED`.
- CP-136-007: Create Bay success and duplicate bay name per-location returns `409`.
- CP-136-008: Create Bay with invalid `supportedServiceIds` or `skillId` returns `400` with invalid IDs list.
- CP-136-009: GET bays excludes `OUT_OF_SERVICE` when `?status=ACTIVE`.
- CP-136-010: Delete Bay success `204` and `404` when not found.
- CP-136-011: Create Mobile Unit `ACTIVE` requires travelBufferPolicyId + capabilityIds + coverageRules → otherwise `400`.
- CP-136-012: Create Mobile Unit duplicate name within baseLocationId → `409`.
- CP-136-013: Create Mobile Unit invalid capabilityIds → `400`; when catalog unreachable → `503`.
- CP-136-014: Eligible mobile units query returns ACTIVE units ordered by priority with effective coverage applied.
- CP-136-015: PUT coverage-rules is atomic replace-all.

Event types (suggested / @EmitEvent)
-----------------------------------

- LOCATION_CREATE
- LOCATION_UPDATE
- LOCATION_DEACTIVATED
- BAY_CREATE
- BAY_UPDATE
- MOBILE_UNIT_CREATE
- MOBILE_UNIT_UPDATE
- COVERAGE_RULES_REPLACE
- TRAVEL_BUFFER_POLICY_CREATE

Implementation links
--------------------

- https://github.com/louisburroughs/durion-positivity-backend/issues/76
- https://github.com/louisburroughs/durion-positivity-backend/issues/77
- https://github.com/louisburroughs/durion-positivity-backend/issues/78

Notes
-----

- The OpenAPI spec (`pos-location/openapi.yaml`) is the authoritative source. Do not add endpoints that are not present in that spec. Behavioral assertions in this file augment the OpenAPI contract and should be implemented and covered by provider tests.
- Ensure emitted event names follow the `pos-events` registration pattern and include standard metadata (`eventId`, `occurredAt`, `producer`, `schemaVersion`, `payload`).
