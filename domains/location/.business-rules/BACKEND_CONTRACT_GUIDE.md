---
title: Location Management Backend Contract Guide
domain: location
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/location/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-location/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-09-02
api_reference_generated: domains/location/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Location Management Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Location Management domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-location/openapi.yaml`
- Generated API reference: `domains/location/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/location/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Location Management behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-136 | `durion#136` | draft | [CAP] Manage Locations, Bays, and Mobile Units |
| CAP-214 | `durion#214` | draft | [CAP] Location & Storage Topology (Shops, Mobile, Floor/Shelf/Bin) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete a location | `deleteLocation` | DELETE | `/v1/locations/{locationId}` | Refer to generated API reference for payload details |
| Delete a bay | `deleteBay` | DELETE | `/v1/locations/{locationId}/bays/{bayId}` | Hard delete; publishes `location.bay.deleted` (backend PR #1674) |
| Delete a mobile unit | `deleteMobileUnit` | DELETE | `/v1/mobile-units/{id}` | Hard delete; also removes coverage rules, publishes `location.mobile-unit.deleted` (backend PR #1674) |
| Get all locations | `getAllLocations` | GET | `/v1/locations` | Refer to generated API reference for payload details |
| Get all location parents | `getAllParents` | GET | `/v1/locations/parents` | Refer to generated API reference for payload details |
| Get location roster | `getRoster` | GET | `/v1/locations/roster` | Refer to generated API reference for payload details |
| Get the top-level default location | `getTopLevelLocation` | GET | `/v1/locations/top-level` | Platform-wide default; see CAP-136 behavioral assertions (backend PR #1639) |
| Get location by ID | `getLocationById` | GET | `/v1/locations/{locationId}` | Refer to generated API reference for payload details |
| List bays | `listBays` | GET | `/v1/locations/{locationId}/bays` | Refer to generated API reference for payload details |
| Get bay | `getBay` | GET | `/v1/locations/{locationId}/bays/{bayId}` | Refer to generated API reference for payload details |
| Get all children for a location | `getAllChildren` | GET | `/v1/locations/{locationId}/children` | Refer to generated API reference for payload details |
| Operation | `getDefaults` | GET | `/v1/locations/{locationId}/defaults` | Refer to generated API reference for payload details |
| Get responsible person for a location | `getResponsiblePerson` | GET | `/v1/locations/{locationId}/responsible-person` | Refer to generated API reference for payload details |
| Validate location reference | `validateLocation` | GET | `/v1/locations/{locationId}/validation` | Refer to generated API reference for payload details |
| Operation | `list_2` | GET | `/v1/locations/{siteId}/storage-locations` | Refer to generated API reference for payload details |
| Operation | `get` | GET | `/v1/locations/{siteId}/storage-locations/{storageLocationId}` | Refer to generated API reference for payload details |
| List mobile units | `listMobileUnits` | GET | `/v1/mobile-units` | Refer to generated API reference for payload details |
| Get mobile unit | `getMobileUnitById` | GET | `/v1/mobile-units/{id}` | Refer to generated API reference for payload details |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-136: [CAP] Manage Locations, Bays, and Mobile Units

### Capability Metadata

- Capability ID: CAP-136
- Parent Issue: https://github.com/louisburroughs/durion/issues/136
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-location/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete a location | `deleteLocation` | DELETE | `/v1/locations/{locationId}` |
| Get all locations | `getAllLocations` | GET | `/v1/locations` |
| Get all location parents | `getAllParents` | GET | `/v1/locations/parents` |
| Get the top-level default location | `getTopLevelLocation` | GET | `/v1/locations/top-level` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

#### Backend PR louisburroughs/durion-positivity-backend#1639 — Top-Level Default Location

`GET /v1/locations/top-level` (`location:read`) is the location domain's public definition
of the platform-wide default location, added so callers (e.g. pos-people's
primary-location fallback, or the frontend directly) never invent their own default:

- Resolution is deterministic: the **active hierarchy root** — a location that is a parent
  of at least one `LocationParent` edge but a child of none — ordered by id (UUID v7 is
  time-ordered), first row wins.
- A flat deployment (no parent-child edges) falls back to the **oldest active location**
  (again by UUID v7 id order), so the default stays stable as locations are added.
- Returns `200` with a full `LocationResponseDTO`; `404` only when no active location
  exists at all. Read-only; no events emitted.
- Consumer note: pos-people mirrors these exact semantics over its event-fed
  `ext_location` / `ext_location_parent` replicas rather than calling this endpoint
  synchronously (ADR-0044); the endpoint remains the contract of record for the
  definition.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-location/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-214: [CAP] Location & Storage Topology (Shops, Mobile, Floor/Shelf/Bin)

### Capability Metadata

- Capability ID: CAP-214
- Parent Issue: https://github.com/louisburroughs/durion/issues/214
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-location/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get location roster | `getRoster` | GET | `/v1/locations/roster` |
| Get location by ID | `getLocationById` | GET | `/v1/locations/{locationId}` |
| List bays | `listBays` | GET | `/v1/locations/{locationId}/bays` |
| List location descendants by parent type | — | GET | `/v1/locations/{locationId}/descendants` |
| Storage-location topology for a site | — | GET | `/v1/locations/{siteId}/storage-locations/topology` |
| Create a storage location (accepts its putaway capability) | `createStorageLocation` | POST | `/v1/locations/{siteId}/storage-locations` |
| Patch a storage location (declare or change its putaway capability) | `patchStorageLocation` | PATCH | `/v1/locations/{siteId}/storage-locations/{storageLocationId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

#### Story #655 — Location Descendants and Storage-Location Topology Contract

- `GET /v1/locations/{locationId}/descendants?parentType=PHYSICAL` returns a flat array of descendant locations `{id, name, code, status, parentId, depth}` (depth 1 = direct child) by walking typed `LocationParent` edges downward.
- `parentType` is optional and defaults to `PHYSICAL`; unknown values return `400`; unknown `locationId` returns `404 LOCATION_NOT_FOUND`; no descendants returns `200 []`.
- Traversal is cycle-safe (visited set) and depth-capped at 20.
- `GET /v1/locations/{siteId}/storage-locations/topology` returns the complete, unpaginated storage-location set for a site as `{id, name, type, status, parentStorageLocationId}` with NO status filtering — inventory may still sit in INACTIVE/MAINTENANCE/QUARANTINED locations. (The paged list endpoint is unsuitable for full-topology consumers; use this endpoint.)
- Consumer contract pin (pos-inventory rollup): fields `id`, `name`, `type`, `status`, `parentStorageLocationId` are stable on both the list and topology responses.

#### Issue louisburroughs/durion-positivity-backend#1514 — Storage-Location Putaway Capability

A storage location now carries a **capability** alongside its topological `type`, and the two are independent: a tire rack and a bulk pallet area are both `FLOOR` topologically, but only one should receive tires.

- `type` (`FLOOR`, `SHELF`, `BIN`, `CAGE`, `TRUCK`) is **unchanged**. No value was added, removed or repurposed.
- `storageCategoryCode` says what the location is fit to *hold*: `TIRE_RACK`, `OIL_STORAGE`, `BATTERY_RACK`, `SMALL_PARTS_BIN`, `BULK_FLOOR`, `STAGING`, `QUARANTINE`, `GENERAL`.
- `hazardContainment` (boolean) declares that the location provides hazard containment. `BATTERY_RACK` and `OIL_STORAGE` are the containment-bearing classes, and a destination coded as one without declaring containment is refused by pos-inventory's putaway compatibility check.
- `allowNewProduct` (`MIXED`, `SAME_PRODUCT_ONLY`, `EMPTY_ONLY`) is accepted, stored and published, but is **not yet enforced** by any putaway check. Treat it as declarative until an enforcement point consumes it.

Contract behavior:

- All three fields are accepted on `createStorageLocation` and `patchStorageLocation`, and returned on the storage-location read paths. They are additive — an existing client that sends none of them is unaffected.
- `storage_category_code` is **nullable** (V8): "never declared" stays distinguishable from an
  explicit `GENERAL` in the owner's own table, so rows written before that change need no backfill.
  Every **read boundary** — response mapping and the published fact — resolves null to `GENERAL` via
  `StorageCategory.orDefault`, so a consumer never has to reimplement that rule and never sees null
  for a location whose fact was published after V8.
- The capability rides the existing `location.storage-location.updated` fact (`StorageLocationUpdatedV1`, schema version 1) additively per ADR-0044. No new synchronous endpoint was added for pos-inventory to read it.
- Consumer contract pin (pos-inventory putaway): `storageCategoryCode`, `hazardContainment` and `allowNewProduct` are stable on the fact payload and on the storage-location responses.
- `GENERAL` is permissive and accepts every catalog category. `STAGING` and `QUARANTINE` are putaway *sources*: pos-inventory refuses putaway into them outright.

Rollout note for consumers: the generic `location.outbox.replay-requested` command re-queues
already-serialized outbox rows, so it re-emits payloads that predate these fields and cannot hydrate
a consumer's replica. A fresh write through the storage-location API (a PATCH declaring the
capability) is what publishes a payload carrying them. See
`durion-positivity-backend/docs/OPERATIONS_RUNBOOK.md` → `Issue #1514: rehydrating the putaway
replica columns`.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-location/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-location/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/location/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/location/.business-rules/AGENT_GUIDE.md`
- `domains/location/.business-rules/DOMAIN_NOTES.md`
- `domains/location/.business-rules/BACKEND_API_REFERENCE.generated.md`
