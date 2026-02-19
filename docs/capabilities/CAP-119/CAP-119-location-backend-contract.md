---
title: CAP-119 Location Backend Contract
capability: CAP-119
module: pos-location
domain: location
generated: 2026-02-18
---

## CAP-119 — Location Backend Contract (pos-location)

This document is the generated backend contract for capability CAP-119 (Location Management & Staffing Assignments). It is derived from the `pos-location` OpenAPI spec and ADR-0016 decisions. Implementers should treat this as the canonical contract used by frontend and downstream services.

## Gateway Endpoints
All endpoints are exposed through the API gateway at `http://localhost:8080/v1/locations`.

- `GET  http://localhost:8080/v1/locations` — list locations
- `POST http://localhost:8080/v1/locations` — create location
- `GET  http://localhost:8080/v1/locations/{locationId}` — get by id
- `PUT  http://localhost:8080/v1/locations/{locationId}` — update by id
- `DELETE http://localhost:8080/v1/locations/{locationId}` — delete by id
- `POST http://localhost:8080/v1/locations/{childId}/parents/{parentId}?parentType={parentType}` — add a parent relation
- `GET  http://localhost:8080/v1/locations/parents` — list parent relations
- `GET  http://localhost:8080/v1/locations/{locationId}/responsible-person` — responsible person
- `GET  http://localhost:8080/v1/locations/bays` — list bays
- `PUT  http://localhost:8080/v1/locations/bays` — manage bays (bulk)
- `POST http://localhost:8080/v1/locations/{locationId}/bays` — create bay
- `GET  http://localhost:8080/v1/locations/{locationId}/bays/{bayId}` — get bay
- `DELETE http://localhost:8080/v1/locations/{locationId}/bays/{bayId}` — delete bay
- `GET  http://localhost:8080/v1/locations/mobileUnit` — list mobile units
- `PUT  http://localhost:8080/v1/locations/mobileUnit` — manage mobile units (bulk)
- `POST http://localhost:8080/v1/locations/{locationId}/mobileUnit` — create mobile unit
- `GET  http://localhost:8080/v1/locations/{locationId}/mobileUnit/{bayId}` — get mobile unit
- `DELETE http://localhost:8080/v1/locations/{locationId}/mobileUnit/{bayId}` — delete mobile unit

## ADR-0016 Compliance
The following contract constraints implement ADR-0016 (see `docs/adr/0016-location-entity-semantics.adr.md`):

- `Location` is canonical and authoritative in `pos-location`.
- `LocationType` is a managed entity (CRUD) in `pos-location`.
- `ParentType` enum values required by ADR-0016: `PHYSICAL`, `ORGANIZATIONAL`, `FINANCIAL`, `SHIPPING`.
- A location can have at most one parent per `ParentType` (modeled as `Map<ParentType, UUID>`).
- `GeographicalLocation` is a separate entity referenced by `geographicalLocationId`.
- Consumers (other services) must store only `locationId` and query `pos-location` for details and hierarchy.
- Classifications: Geographical, Physical, Storage, Service, Mobile (examples; not exhaustive).

## Location JSON Schema (contract-model)
The OpenAPI `Location` schema is extended to reflect ADR-0016. Example representation:

```json
{
  "id": "018e1c9f-6b5a-7890-abcd-1234567890ab",
  "code": "MAIN-WH",
  "name": "Main Warehouse",
  "type": { "id": "2f4d3a2b-...", "name": "Warehouse" },
  "parents": { "PHYSICAL": "b3a1-...", "ORGANIZATIONAL": "c4f2-..." },
  "geographicalLocationId": "d5e6-...",
  "status": "ACTIVE",
  "timezone": "America/New_York",
  "responsiblePersonId": 12345,
  "createdAt": "2026-02-17T12:00:00Z",
  "updatedAt": "2026-02-17T12:30:00Z"
}
```

Field notes:

- `id`: UUIDv7 as string
- `code`: user-defined unique code (immutable)
- `type`: reference to `LocationType` (entity)
- `parents`: map keyed by `ParentType` -> `locationId` (one per ParentType)
- `geographicalLocationId`: FK to `GeographicalLocation` entity (addresses + coordinates)

## Events (write operations)
Write operations MUST emit events. Suggested stable event types (names shown for reference):

- `pos.location.v1.LocationCreated`
- `pos.location.v1.LocationUpdated`
- `pos.location.v1.LocationDeleted`
- `pos.location.v1.LocationParentAdded`
- `pos.location.v1.BaysManaged`
- `pos.location.v1.BayCreated`
- `pos.location.v1.BayDeleted`
- `pos.location.v1.MobileUnitCreated`
- `pos.location.v1.MobileUnitDeleted`

Event envelope (required):

```json
{
  "eventId": "UUIDv7",
  "eventType": "pos.location.v1.LocationCreated",
  "occurredAt": "2026-02-18T12:00:00Z",
  "producer": "pos-location",
  "schemaVersion": 1,
  "payload": { /* resource snapshot or change set */ }
}
```

## Behavioral contract test hints (use `CP-119-NNN` names)

- `CP-119-001` Create Location: POST `/v1/locations` -> `201` with full Location
- `CP-119-002` Update Location: PUT `/v1/locations/{locationId}` -> `200`; `code` immutable
- `CP-119-003` Duplicate code -> `409 Conflict`
- `CP-119-004` ParentType uniqueness enforced (one parent per `ParentType`)
- `CP-119-005` Consumers store `locationId` only; validate consumer fetch flow

## Capability links & issues

- Capability manifest: `docs/capabilities/CAP-119/CAPABILITY_MANIFEST.yaml`

- Backend issues referenced by capability manifest:
  - Issue #86 (closed) — assigns person to location story (people assignment behavior)
  - Issue #87 (open) — location create/update including timezone: [Issue #87](https://github.com/louisburroughs/durion-positivity-backend/issues/87)

---
Generated from `pos-location/openapi.json` and ADR-0016 on 2026-02-18.
