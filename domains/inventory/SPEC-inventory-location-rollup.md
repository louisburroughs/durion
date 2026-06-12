# Specification: Site Inventory Rollup by Storage Location Hierarchy

**Status:** APPROVED — all open questions resolved 2026-06-12; ready for story authoring
**Date:** 2026-06-12
**Domain:** Inventory Control (primary), Location Management (supporting)
**Repos:** `durion-positivity-backend` — modules `pos-inventory`, `pos-location`
**Capabilities:** CAP-214 (location topology — FR-3, FR-4), CAP-218 (inventory rollup — FR-1, FR-2, FR-5..FR-9)
**Related ADRs:** ADR-0009 (domain responsibilities), ADR-0016 (location entity semantics)

---

## 1. Problem Statement

Inventory quantities in `pos-inventory` are recorded against a single
`locationId` (a storage location UUID) on `InventoryLedgerEntry` and
`AllocationEntity`. There is no way to view inventory aggregated up the
storage-location hierarchy (bin → shelf → floor → site) or further up the
physical location hierarchy (site → building/place).

`pos-location` already models the full topology:

- `StorageLocationEntity.parentStorageLocation` — self-referencing chain
  (FLOOR/SHELF/BIN/CAGE/TRUCK, CAP-214)
- `StorageLocationEntity.site` — ManyToOne to `Location`
- `Location` ↔ `LocationParent` with `ParentType.PHYSICAL` — site → building/place chain

The missing piece is a query path that joins inventory quantities to that
topology. Per ADR-0016, services do not share databases and must not
replicate location data, so the rollup must be orchestrated in the
`pos-inventory` service layer via REST calls to `pos-location`.

## 2. Goals

1. View on-hand inventory for a site, grouped and subtotaled by storage
   location hierarchy (rollup tree).
2. Optionally roll the site totals up to a parent `Location` (building or
   place) via the `PHYSICAL` parent chain.
3. Optional filter by SKU.
4. Include outstanding allocation quantities so on-hand vs. available is
   visible at every node.

### Non-Goals

- No materialized rollup tables or caching layer (first iteration computes
  on demand).
- No changes to how ledger entries or allocations record `locationId`.
- No cross-site aggregation in a single call (one site, or one parent
  location resolving to its child sites, per request).
- No frontend work in this spec (separate frontend story).

## 3. Current State (verified in code)

| Artifact | Location | Relevant detail |
|---|---|---|
| `AllocationEntity` | `pos-inventory/.../entity/AllocationEntity.java` | bare `UUID locationId`, table `inventory_allocation` |
| `InventoryLedgerEntry` | `pos-inventory/.../entity/InventoryLedgerEntry.java` | bare `UUID locationId`; on-hand derived by event-type aggregation |
| `InventoryLedgerEntryRepository` | `pos-inventory/.../repository/` | single-location aggregates only (`calculateOnHandQuantityAtLocation`, `findPositiveOnHandByLocation` with `LocationOnHand` projection) |
| `LocationInventoryInquiryService` | `pos-inventory` | single storage location inquiry; defines `ON_HAND_EVENT_TYPES` and `ALLOCATION_EVENT_TYPES` |
| `StorageLocationValidationClient` | `pos-inventory/.../client/` | existing RestClient pattern for calling pos-location |
| `StorageLocationEntity` | `pos-location/.../entity/` | `site` + `parentStorageLocation`, type, status |
| `StorageLocationResponse` | `pos-location/.../dto/` | already exposes `id`, `name`, `type`, `status`, `siteId`, `parentStorageLocationId` |
| `StorageLocationController` | `pos-location` | `GET /v1/locations/{siteId}/storage-locations` returns flat list |
| `LocationParent` / `ParentType` | `pos-location/.../entity/` | typed parent links; ADR-0016: at most one parent per type |

## 4. Functional Requirements

### FR-1: Site inventory rollup endpoint (pos-inventory)

`GET /v1/inventory/sites/{siteId}/inventory-rollup`

Query parameters:

| Param | Type | Required | Meaning |
|---|---|---|---|
| `sku` | string | no | restrict to one stock item |
| `depth` | int | no | truncate tree below this depth (default: full) |
| `includeEmpty` | boolean | no (default `false`) | include storage locations with zero on-hand and zero allocations |

Response (shape, not final field names):

```json
{
  "siteId": "…",
  "siteName": "Main Workshop",
  "totals": { "onHand": 1240, "allocated": 75, "available": 1165 },
  "nodes": [
    {
      "storageLocationId": "…",
      "name": "Floor A",
      "type": "FLOOR",
      "status": "ACTIVE",
      "own": { "onHand": 0, "allocated": 0, "available": 0 },
      "rolledUp": { "onHand": 480, "allocated": 30, "available": 450 },
      "children": [
        {
          "storageLocationId": "…",
          "name": "Shelf A-1",
          "type": "SHELF",
          "own": { "onHand": 120, "allocated": 10, "available": 110 },
          "rolledUp": { "onHand": 480, "allocated": 30, "available": 450 },
          "children": [ "…bins…" ]
        }
      ]
    }
  ]
}
```

Rules:

- `own` = quantities recorded directly against that storage location.
- `rolledUp` = `own` + sum of all descendants' `own`.
- `available` = `onHand − allocated`, floor at node level (may be negative;
  do not clamp — negative available is a real signal).
- Orphaned rows (ledger/allocation `locationId` not in the site's
  storage-location list) are **excluded from v1 response**; the service logs
  a WARN with the count of distinct orphan location ids encountered, and the
  OpenAPI description documents the limitation. An `unassigned` bucket is
  deferred to v2 (requires site scoping of ledger rows; ledger has no
  `siteId` column).
- When `sku` given, all quantities are for that SKU only.
- Site with no storage locations: `nodes: []`, zero totals. 200, not 404.
  Unknown `siteId` (pos-location returns 404): 404 with ProblemDetail.

### FR-2: Parent-location rollup endpoint (pos-inventory)

`GET /v1/inventory/locations/{locationId}/inventory-rollup?parentType=PHYSICAL`

- `locationId` is a `Location` (building/place/region), not a storage
  location.
- pos-inventory asks pos-location for all descendant sites of `locationId`
  along the given `parentType` chain (FR-4), then computes FR-1 per site and
  returns site-level summaries (not full storage trees) plus a grand total:

```json
{
  "locationId": "…",
  "name": "Downtown Building",
  "parentType": "PHYSICAL",
  "totals": { "onHand": 3320, "allocated": 140, "available": 3180 },
  "sites": [
    { "siteId": "…", "siteName": "Main Workshop", "totals": { "…" : 0 } }
  ]
}
```

- Same `sku` filter as FR-1.
- `parentType` defaults to `PHYSICAL`; validated against the `ParentType`
  enum (400 on unknown value).
- `expand=tree` (optional, default absent): each `sites[]` entry additionally
  carries the full FR-1 `nodes` tree for that site (same shape, same rules,
  same `includeEmpty`/`depth` params applied per site). Without `expand`,
  site summaries only. Guard: if descendant site count exceeds a configured
  cap (default 25), reject `expand=tree` with 422 ProblemDetail advising
  per-site FR-1 calls — prevents unbounded fan-out of topology fetches and
  grouped queries.

### FR-3: Storage-location topology contract (pos-location)

Reuse `GET /v1/locations/{siteId}/storage-locations`. Verify and, if
needed, adjust so that:

- All storage locations of the site are returned in one call (no pagination
  gap, or pagination is honoured by the client). If currently paginated, add
  `size` support sufficient for full retrieval or a dedicated
  `GET /v1/locations/{siteId}/storage-locations/topology` returning the flat
  minimal projection: `id`, `name`, `type`, `status`, `parentStorageLocationId`.
- INACTIVE/RETIRED storage locations are included (inventory may still sit
  in them); client filters nothing by status.

### FR-4: Location descendants endpoint (pos-location) — new

`GET /v1/locations/{locationId}/descendants?parentType=PHYSICAL`

- Walks `LocationParent` edges of the given `parentType` downward from
  `locationId` and returns flat list of descendant `Location`s:
  `id`, `name`, `code`, `status`, `parentId`, `depth`.
- Cycle-safe traversal (visited set) even though ADR-0016 forbids cycles —
  defensive, capped depth 20.
- 404 if `locationId` unknown; empty list if no descendants.

### FR-5: Bulk quantity queries (pos-inventory repository)

New `InventoryLedgerEntryRepository` methods (JPQL, follow existing
`@Query` style):

```java
// on-hand per location for a set of locations
@Query("""
    select e.locationId as locationId, sum(e.quantityDelta) as quantity
    from InventoryLedgerEntry e
    where e.locationId in :locationIds and e.eventType in :eventTypes
    group by e.locationId
    """)
List<LocationQuantity> sumQuantityByLocation(
        Collection<UUID> locationIds, Collection<InventoryLedgerEventType> eventTypes);

// same, restricted to one SKU
List<LocationQuantity> sumQuantityByLocationForSku(
        String stockItemId, Collection<UUID> locationIds,
        Collection<InventoryLedgerEventType> eventTypes);
```

`LocationQuantity` is an interface projection `{ UUID getLocationId(); long getQuantity(); }`.
Exact field name for the quantity column must match the existing ledger
schema (verify `quantityDelta` vs actual name before implementation —
mirror whatever `calculateOnHandQuantityAtLocation` sums).

Allocation outstanding quantities: equivalent grouped query over the
ledger `ALLOCATION_EVENT_TYPES` (reuse the semantics in
`LocationInventoryInquiryServiceImpl.calculateOutstandingAllocations`,
bulked; do not re-derive allocation math differently). FR-9 makes these
ledger events actually get written — without it the `allocated` figures
are silently zero.

Chunk `locationIds` into batches of ≤1000 to respect parameter limits.

### FR-6: Topology client (pos-inventory)

New `StorageLocationTopologyClient` in `pos-inventory/.../client/`:

- Same RestClient/config pattern as `StorageLocationValidationClient`
  (same base-url property and error translation).
- Methods:
  - `List<StorageLocationNode> fetchSiteTopology(UUID siteId)` → FR-3
  - `List<LocationDescendant> fetchDescendants(UUID locationId, String parentType)` → FR-4
- pos-location 404 → domain `SiteNotFoundException` → 404 ProblemDetail.
- pos-location 5xx/timeout → 503 ProblemDetail with retry hint; rollup is a
  read, never partially fabricate topology.

### FR-7: Rollup service (pos-inventory)

New `SiteInventoryRollupService` (interface in `…inventory.service`, impl in
`…internal.service`, matching `LocationInventoryInquiryService` split):

Algorithm (per site):

1. Fetch flat topology from client (FR-6).
2. Build in-memory tree on `parentStorageLocationId`; nodes whose parent id
   is missing from the set attach to root (defensive).
3. Bulk-query on-hand and allocated per location (FR-5).
4. Compute `own` per node; depth-first post-order sum for `rolledUp`.
5. Orphan handling (v1): no `unassigned` bucket. Bulk queries are already
   restricted to the site's known location ids, so orphans never enter the
   tree. Log WARN with distinct orphan id count only if cheaply detectable;
   do not add a dedicated scan query for it.
6. Apply `includeEmpty`/`depth` pruning last.

Read-only; `@Transactional(readOnly = true)` over repository work; the
remote call happens outside the transaction.

### FR-8: Controllers and OpenAPI (pos-inventory)

- `SiteInventoryRollupController` for FR-1/FR-2 routes.
- Full OpenAPI annotations (`@Operation`, `@ApiResponse`, parameter
  descriptions) matching module conventions in
  `LocationInventoryInquiryController`.
- Errors as ProblemDetail via existing `InventoryGlobalExceptionHandler`.

### FR-9: Allocation lifecycle writes ledger events (pos-inventory) — new

**Background (verified in code):** `ALLOCATION_CREATED` / `ALLOCATION_RELEASED`
ledger event types exist and are read by `LocationInventoryInquiryServiceImpl`
and `InventoryAvailabilityServiceImpl`, but **no production code writes
them**. Additionally, `ReservationServiceImpl` creates soft allocations with
`locationId = null` and `promoteToHard` never assigns a location
(`PromoteAllocationRequest` carries only `hardenedReason`). Result today:
location-level outstanding allocations are always zero, and allocation rows
themselves are not location-attributable.

Decision (resolved 2026-06-12): keep the rollup and the existing inquiry
endpoint **ledger-based**, and fix the write side so the ledger is
trustworthy:

1. **Assign location on hard promotion.** Add `storageLocationId` (UUID,
   required) to `PromoteAllocationRequest`. `promoteToHard` validates it via
   the existing `StorageLocationValidationClient` and sets
   `AllocationEntity.locationId`. Rationale: SOFT allocations are
   site-agnostic demand; a HARD allocation pins physical stock, so it must
   say where.
2. **Write `ALLOCATION_CREATED`** ledger entry (quantity =
   `allocatedQuantity`, locationId = assigned storage location, stockItemId
   from the reservation) in the same transaction as the promotion. SOFT
   allocation creation writes no ledger event (no location to attribute).
3. **Write `ALLOCATION_RELEASED`** in the same transaction when:
   - `cancelReservation` releases allocations — only for allocations that
     are HARD with a non-null `locationId` (i.e. had a matching CREATED);
   - any future release/unpick path transitions a located allocation to
     `RELEASED`.
4. **Consumption closure:** when allocated stock is consumed
   (`WORKORDER_CONSUMPTION` posts on-hand decrement), the allocation must
   also be closed with `ALLOCATION_RELEASED` so outstanding does not
   double-count consumed stock. If no consumption path currently exists in
   pos-inventory, document this invariant on the event type Javadoc and the
   contract guide; enforce in whichever module posts consumption.
5. **Invariant:** for every allocation id, ledger CREATED quantities −
   RELEASED quantities ∈ {0, allocatedQuantity}. Re-promotion of an
   already-HARD allocation must not write a duplicate CREATED.
6. **No backfill:** existing allocation rows without locations are left
   as-is; rollup reflects ledger events from deployment forward. Note in
   release notes.

This FR is what makes Q3's "ledger-based" answer sound; it ships under
CAP-218 ahead of (or together with) FR-7.

## 5. Non-Functional Requirements

- **NFR-1 Performance:** one topology call + ≤4 grouped queries per site
  request. Target p95 < 1s for a site with 5k storage locations and 1M
  ledger rows. Add index on `inventory_ledger_entry (location_id, event_type)`
  if not present (verify; migration via the module's standard mechanism).
- **NFR-2 Consistency:** quantities are read-committed snapshots; document
  that rollup is not transactionally consistent with concurrent receipts.
- **NFR-3 Authorization:** same authz treatment as existing inventory
  inquiry endpoints (mirror whatever guards `LocationInventoryInquiryController`).
- **NFR-4 Module boundaries:** no new compile-time dependency from
  pos-inventory on pos-location classes; DTOs duplicated on the consumer
  side per existing client pattern (ArchUnit rules must stay green).

## 6. Testing Requirements

- **pos-location:** controller + service tests for FR-4 (descendants:
  happy path, unknown id, cycle guard, parentType filter). Contract test
  for FR-3 response fields used by the consumer (`id`, `name`, `type`,
  `status`, `parentStorageLocationId`).
- **pos-inventory:**
  - Repository tests for FR-5 grouped queries (multiple locations, SKU
    filter, empty id set, >1000 ids chunking).
  - Service tests for FR-7: 3-level tree rollup math, orphan parent id,
    empty site, negative available, `includeEmpty`/`depth` pruning,
    pos-location 404/5xx propagation (client mocked); FR-2 `expand=tree`
    (trees match per-site FR-1 output; site-count cap returns 422).
  - Controller tests (WebMvc slice): parameter validation, ProblemDetail
    shapes, OpenAPI-documented status codes.
  - FR-9 tests in `ReservationServiceImpl` coverage: promote writes
    `ALLOCATION_CREATED` with correct location/quantity in same transaction;
    re-promote writes no duplicate; cancel writes `ALLOCATION_RELEASED` only
    for located HARD allocations; promote rejects missing/invalid
    `storageLocationId`; existing inquiry endpoint now returns nonzero
    outstanding after promote (regression-proof the previously dead path).
  - Provider contract tests per module test policy.
- Coverage per module policy (JaCoCo gates unchanged).

## 7. Delivery Plan

| Step | CAP | Module | Item | Depends on |
|---|---|---|---|---|
| 1 | CAP-214 | pos-location | FR-3 verification (+topology projection if needed) | — |
| 2 | CAP-214 | pos-location | FR-4 descendants endpoint | — |
| 3 | CAP-218 | pos-inventory | FR-5 repository queries + index migration | — |
| 4 | CAP-218 | pos-inventory | FR-9 allocation ledger events + location on promote | — |
| 5 | CAP-218 | pos-inventory | FR-6 topology client | 1, 2 |
| 6 | CAP-218 | pos-inventory | FR-7 rollup service | 3, 5 |
| 7 | CAP-218 | pos-inventory | FR-8 controller + OpenAPI + error handling | 6 |
| 8 | both | both | Contract guide updates (`BACKEND_CONTRACT_GUIDE.md`), README updates | 7 |

Steps 1–4 are parallelizable. FR-9 is independently shippable and fixes a
live correctness gap (ATP currently equals on-hand at every location); it
should land first if sequencing is needed.

Stories filed 2026-06-12 in `durion-positivity-backend`:

| Issue | CAP | Covers |
|---|---|---|
| [#655](https://github.com/louisburroughs/durion-positivity-backend/issues/655) | CAP-214 | FR-3, FR-4 (topology contract, descendants endpoint) |
| [#656](https://github.com/louisburroughs/durion-positivity-backend/issues/656) | CAP-218 | FR-9 (allocation ledger events, location on promote) |
| [#657](https://github.com/louisburroughs/durion-positivity-backend/issues/657) | CAP-218 | FR-5 (bulk grouped queries, index) |
| [#658](https://github.com/louisburroughs/durion-positivity-backend/issues/658) | CAP-218 | FR-1, FR-6, FR-7, FR-8 (site rollup endpoint) |
| [#659](https://github.com/louisburroughs/durion-positivity-backend/issues/659) | CAP-218 | FR-2 (parent-location rollup, `expand=tree`) |

Dependency order: #655/#656/#657 parallel → #658 → #659. Stories filed under existing capability
lineages: pos-location work extends CAP-214 (storage location topology),
pos-inventory work extends CAP-218 (fulfillment/allocation inventory).
Branches: `cap/214-storage-location-descendants` and
`cap/218-inventory-location-rollup`. PR titles: `[CAP:214] Location
descendants and topology contract` and `[CAP:218] Site inventory rollup by
storage location hierarchy`. CAP-218 PR depends on CAP-214 PR merging first
(client consumes the new contract).

## 8. Open Questions

1. **Capability id:** RESOLVED 2026-06-12 — attach to existing lineages:
   CAP-214 for pos-location work (FR-3/FR-4), CAP-218 for pos-inventory
   work (FR-1/FR-2/FR-5..FR-8). See Delivery Plan.
2. **Unassigned bucket scoping:** RESOLVED 2026-06-12 — omitted in v1.
   Orphan rows excluded from response; WARN log when cheaply detectable;
   limitation documented in OpenAPI. `unassigned` bucket deferred to v2.
3. **Allocation source of truth:** RESOLVED 2026-06-12 — ledger stays the
   source of truth for outstanding allocations; allocation services are
   fixed to write `ALLOCATION_CREATED`/`ALLOCATION_RELEASED` events and to
   assign `locationId` on hard promotion. See FR-9.
4. **FR-2 response depth:** RESOLVED 2026-06-12 — optional `expand=tree`
   inlines full FR-1 trees per site; default remains summaries only; site
   count cap (default 25) guards fan-out. See FR-2.
