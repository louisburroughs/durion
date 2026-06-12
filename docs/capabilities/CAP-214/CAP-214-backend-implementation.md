# CAP-214 Backend Implementation Log

Implementation records for backend stories delivered on branch `cap/CAP214`
in `durion-positivity-backend`.

---

## Story #655 — Topology: Location Descendants Endpoint and Storage-Location Topology Contract

- **Issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/655
- **Spec:** `durion` repo — `domains/inventory/SPEC-inventory-location-rollup.md` (FR-3, FR-4)
- **Branch:** `cap/CAP214`
- **Commit:** `4d622ca`
- **Status:** ✅ Implemented, tested, pushed (no PR — capability completion pending)

### Endpoints (pos-location)

1. **`GET /v1/locations/{locationId}/descendants?parentType=PHYSICAL`** (new, `LocationController`)
   - Flat array of `{id, name, code, status, parentId, depth}` (depth 1 = direct child).
   - `parentType` optional, default `PHYSICAL`, validated against `ParentType` → 400 on unknown.
   - Unknown `locationId` → 404 `LOCATION_NOT_FOUND`.
   - BFS with one batched query per level (`findByParent_IdInAndParentType`), visited-set cycle guard, depth cap 20.
   - `@PreAuthorize('location:read')`, full OpenAPI annotations.

2. **`GET /v1/locations/{siteId}/storage-locations/topology`** (new, `StorageLocationController`)
   - Flat unpaginated `{id, name, type, status, parentStorageLocationId}` array, all statuses.
   - Added because the existing list endpoint is paged (default size 20) with no fetch-all mode — the spec's sanctioned fallback (FR-3).
   - `LOCATION_STORAGE_LOCATION_TOPOLOGY` fastRead event registered.

### FR-3 findings

- Pagination WAS a gap → solved by the dedicated `/topology` endpoint.
- Status filtering was NOT a gap (`findBySiteId`, no filter); pinned by contract test (INACTIVE rows returned).
- Spec mentioned `RETIRED` status — does not exist; actual enum: ACTIVE, INACTIVE, MAINTENANCE, QUARANTINED (all covered in tests).

### Tests

17 new (7 descendants service unit incl. cycle guard + depth cap, 2 topology service, 5 + 3 contract ITs). Full module **174/174 pass**; ArchUnit 13/13; Spotless applied; `openapi.yaml` untouched.

### Notes for downstream

- pos-inventory `StorageLocationTopologyClient` (#658) should consume `/topology`, not the paged list.
- Cycle-guard tested at service level with mocked repository (DB constraints prevent persisting real cycles).
