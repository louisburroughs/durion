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
last_updated: 2026-02-24
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
| Get all locations | `getAllLocations` | GET | `/v1/locations` | Refer to generated API reference for payload details |
| Get all location parents | `getAllParents` | GET | `/v1/locations/parents` | Refer to generated API reference for payload details |
| Get location roster | `getRoster` | GET | `/v1/locations/roster` | Refer to generated API reference for payload details |
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

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

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

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

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
