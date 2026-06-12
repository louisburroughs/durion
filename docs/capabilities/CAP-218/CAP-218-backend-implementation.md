# CAP-218 Backend Implementation Log

Implementation records for backend stories delivered on branch `cap/CAP218`
in `durion-positivity-backend`.

---

## Story #656 — Allocation: Write Allocation Ledger Events and Assign Location on Hard Promotion

- **Issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/656
- **Spec:** `durion` repo — `domains/inventory/SPEC-inventory-location-rollup.md` (FR-9)
- **Branch:** `cap/CAP218`
- **Commit:** `1c4b529`
- **Status:** ✅ Implemented, tested, pushed (no PR — capability completion pending)

### Problem

`ALLOCATION_CREATED` / `ALLOCATION_RELEASED` ledger event types existed and were
read by `LocationInventoryInquiryServiceImpl` and
`InventoryAvailabilityServiceImpl`, but nothing wrote them — location-level
outstanding allocations were always zero and ATP silently equalled on-hand.
Additionally, soft allocations were created with `locationId = null` and
`promoteToHard` never assigned a location, so allocation rows were not
location-attributable.

### Changes (all in `pos-inventory`)

| File | Change |
| --- | --- |
| `internal/dto/reservation/PromoteAllocationRequest.java` | Added required `UUID storageLocationId` (`@NotNull`, OpenAPI `@Schema`); kept `hardenedReason` |
| `internal/service/ReservationServiceImpl.java` | Location validation via `StorageLocationValidationClient` before any state change; pins `locationId` on promotion; writes `ALLOCATION_CREATED` in-transaction; duplicate-promote guard; `cancelReservation` writes `ALLOCATION_RELEASED` for located HARD allocations only |
| `internal/enums/InventoryLedgerEventType.java` | Javadoc invariants on `ALLOCATION_CREATED` / `ALLOCATION_RELEASED`, including the `WORKORDER_CONSUMPTION` closure rule |
| `internal/controller/ReservationController.java` | Promote `@Operation` description updated; 404 response covers storage location |
| `test/.../service/ReservationServiceImplTest.java` | Constructor + request updates; 5 new FR-9 tests |
| `test/.../contract/ReservationContractBehaviorIT.java` | Request updates; new 400 contract test for missing `storageLocationId` |

### Behavior

1. **Promote (`POST /v1/inventory/reservations/{allocationId}/promote`):**
   - `storageLocationId` required → `400` if absent (bean validation).
   - Validated against pos-location: `exists=false` → `404`
     (`LocationNotFoundException`); `active=false` → `400`
     (`IllegalArgumentException`). Validation happens before the ATP check and
     before any reservation/allocation mutation.
   - On success: allocation pinned to the storage location, `ALLOCATION_CREATED`
     ledger entry written in the same transaction
     (`changeInQuantity = allocatedQuantity`, `quantityAfter` = current net
     on-hand (event is NEUTRAL), `sourceTransactionId` = allocation id).
   - Re-promote of an already-HARD allocation with a recorded location: no
     duplicate `CREATED`, original location kept. Legacy already-HARD rows with
     `locationId = null` are repaired on re-promote (location set + `CREATED`
     written).

2. **Cancel (`DELETE /v1/inventory/reservations/{workorderLineId}`):**
   - `ALLOCATION_RELEASED` written only for allocations that were HARD, located,
     and not already RELEASED — i.e. exactly those with a matching `CREATED`.
   - SOFT/unlocated allocations release without ledger events (no `CREATED` to
     balance).

3. **Invariants:**
   - Per allocation: `CREATED − RELEASED ∈ {0, allocatedQuantity}`.
   - Consumption closure documented on the event-type Javadoc and in the
     contract guide: the flow posting `WORKORDER_CONSUMPTION` for allocated
     stock must close the allocation with `ALLOCATION_RELEASED`. (The current
     `ConsumptionServiceImpl` consumes via pick tasks with no allocation
     linkage; enforcement lands when that linkage exists.)
   - No backfill of pre-existing unlocated allocation rows.

### Tests

- `ReservationServiceImplTest`: 21 tests (16 pre-existing updated, 5 new):
  promote writes `CREATED` with correct location/quantity/source id; re-promote
  writes no duplicate and keeps location; missing storage location →
  `LocationNotFoundException` with no state change; inactive location →
  `IllegalArgumentException`; cancel writes `RELEASED` only for located HARD.
- `ReservationContractBehaviorIT`: 6 tests (5 pre-existing updated, 1 new):
  promote without `storageLocationId` → `400`.
- Full module: `./mvnw -pl pos-inventory test` → **280/280 pass**.
- Spotless applied.

### Contract guide

`domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md` — new
"Story #656" behavioral assertions block under CAP-218.

### Notes for downstream stories

- #658 (site rollup) consumes these events for its `allocated` figures; without
  this story those figures are zero.
- The previously RED-documented gap "ATP equals on-hand everywhere" is fixed
  from deployment forward only; historical allocations are not represented in
  the ledger.

---

## Story #657 — Inventory: Bulk On-Hand and Allocation Quantity Queries by Location Set

- **Issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/657
- **Spec:** `durion` repo — `domains/inventory/SPEC-inventory-location-rollup.md` (FR-5)
- **Branch:** `cap/CAP218`
- **Commit:** `68c87ff`
- **Status:** ✅ Implemented, tested, pushed (no PR — capability completion pending)

### Changes (pos-inventory, repository layer only)

| File | Change |
| --- | --- |
| `internal/repository/InventoryLedgerEntryRepository.java` | Grouped JPQL queries + chunked default methods + outstanding-allocations helper + nested `LocationQuantity` projection |
| `internal/entity/InventoryLedgerEntry.java` | `@Index(name = "idx_inventory_ledger_entry_location_event", columnList = "location_id, event_type")` |
| `db/migration/V3__add_inventory_ledger_entry_location_event_index.sql` | Flyway btree index (authoritative prod artifact; `ddl-auto: validate` ignores indexes) |
| `test/.../internal/repository/InventoryLedgerEntryRepositoryTest.java` | 5 new repository tests |

### API (service-layer consumers, e.g. #658)

```java
List<LocationQuantity> sumQuantityByLocation(Collection<UUID> locationIds, Collection<InventoryLedgerEventType> eventTypes);
List<LocationQuantity> sumQuantityByLocationForSku(String stockItemId, Collection<UUID> locationIds, Collection<InventoryLedgerEventType> eventTypes);
default Map<UUID, Long> sumQuantityByLocationChunked(...);          // empty/null guard, dedupe, ≤1000 chunks, merged
default Map<UUID, Long> sumQuantityByLocationForSkuChunked(...);
default Map<UUID, Long> calculateOutstandingAllocationsByLocation(Collection<UUID> locationIds); // sum(CREATED) − sum(RELEASED)
```

Semantics mirror `calculateOnHandQuantityAtLocation` exactly
(`COALESCE(SUM(changeInQuantity), 0)`, eventType IN filter), grouped by
location. Result maps are sparse (absent = 0) and immutable.

### Tests

5 new: grouped sums (cross-checked vs single-location aggregate), SKU filter,
empty-set guard (no SQL), 1001-id chunking, outstanding allocations
cross-checked against `calculateOutstandingAllocations` semantics.
Full module **285/285 pass**; Spotless applied; adversarial review PASS.

### Notes for downstream

- #658 should call the chunked default methods, never the raw queries, and
  wrap the two-query outstanding-allocations helper in
  `@Transactional(readOnly = true)` at the service layer (the two grouped
  sums are not atomic on their own).

---

## Story #658 — Inventory: Site Inventory Rollup by Storage Location Hierarchy

- **Issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/658
- **Spec:** `durion` repo — `domains/inventory/SPEC-inventory-location-rollup.md` (FR-1, FR-6, FR-7, FR-8)
- **Branch:** `cap/CAP218`
- **Commit:** `63676bd`
- **Status:** ✅ Implemented, tested, pushed (no PR — capability completion pending)

### Changes (pos-inventory, 13 files, +926)

| Artifact | Purpose |
| --- | --- |
| `internal/client/StorageLocationTopologyClient` (FR-6) | Consumes pos-location `/v1/locations/{siteId}/storage-locations/topology` (#655 contract); 404 → `LocationNotFoundException`, 5xx/timeout → `LocationServiceUnavailableException`; consumer-side `StorageLocationNode` record |
| `internal/service/SiteInventoryQuantityLoader` | Loads on-hand + outstanding-allocation maps in ONE `@Transactional(readOnly = true)` scope using #657 chunked queries (honors #657 handoff note); SKU-scoped variant composes CREATED − RELEASED |
| `internal/service/SiteInventoryRollupServiceImpl` (FR-7) | Topology fetch outside transaction → tree build on `parentStorageLocationId` (orphan parents attach to root) → depth-first post-order `rolledUp` sums → `includeEmpty`/`depth` pruning last; children sorted by name |
| `service/SiteInventoryRollupService` | Public interface (module split convention) |
| `internal/dto/rollup/*` (3 records) | `RollupQuantities` (available unclamped), `StorageLocationRollupNode` (own/rolledUp/children), `SiteInventoryRollupResponse` |
| `internal/controller/SiteInventoryRollupController` (FR-8) | `GET /v1/inventory/sites/{siteId}/inventory-rollup?sku&depth&includeEmpty`; `@PreAuthorize('inventory:on_hand:view')`; full OpenAPI incl. 503 |
| `InventoryGlobalExceptionHandler` | New 503 mapping `LOCATION_SERVICE_UNAVAILABLE` |

### Tests (18 new)

- Service (9): 3-level own/rolledUp math, SKU-scoped queries incl. allocation
  CREATED−RELEASED, orphan parent → root, negative available unclamped,
  includeEmpty pruning (parents of non-empty children survive), depth=1
  truncation with full-tree totals, empty site → empty 200, 404/503
  propagation.
- Contract IT (6): 200 tree shape, query param pass-through, depth=0 → 400,
  404 NOT_FOUND, 503 LOCATION_SERVICE_UNAVAILABLE, 403 without authority.
- Client (3): contract field mapping (MockRestServiceServer), 404 → not
  found, 5xx → unavailable.
- Full module: **297/297 pass** incl. ArchUnit 12/12. Spotless applied.

### Deviations

- Spec's example response had `siteName`; the topology contract carries no
  site name, so the response omits it (consumer can resolve the site via
  pos-location). Documented in OpenAPI.
- Orphan WARN log omitted: bulk queries are restricted to known ids, so
  orphans are invisible without a dedicated scan the spec forbade.

### Next

- #659 (parent-location rollup, `expand=tree`) reuses
  `SiteInventoryRollupService` per site; needs `fetchDescendants` added to
  the topology client against the #655 descendants endpoint.

---

## Story #659 — Inventory: Parent-Location Inventory Rollup with Optional Per-Site Trees

- **Issue:** https://github.com/louisburroughs/durion-positivity-backend/issues/659
- **Spec:** `durion` repo — `domains/inventory/SPEC-inventory-location-rollup.md` (FR-2)
- **Branch:** `cap/CAP218`
- **Commit:** `7ee1d9a`
- **Status:** ✅ Implemented, tested, pushed (no PR — capability completion pending)

### Changes (pos-inventory, 11 files, +716)

| Artifact | Purpose |
| --- | --- |
| `StorageLocationTopologyClient.fetchDescendants` + `LocationDescendant` record | Consumes the #655 descendants contract (`id, name, code, status, parentId, depth`); 404 → not-found, 5xx → unavailable |
| `service/LocationInventoryRollupService` + `internal/service/LocationInventoryRollupServiceImpl` | Resolves descendants, computes Story #658 rollup per site (zero totals for non-site descendants), aggregates grand total; consumer-side `ParentType` pin validation (400), case-insensitive, default PHYSICAL |
| `internal/exception/RollupExpansionTooLargeException` + handler entry | `expand=tree` over `pos.inventory.rollup.expand-site-cap` (default 25) → `422 ROLLUP_EXPANSION_TOO_LARGE`, thrown before any per-site work |
| `internal/dto/rollup/LocationInventoryRollupResponse`, `SiteRollupSummary` | `sites[]` carry `siteName` (from descendants contract); `nodes` serialized only with expand=tree (`@JsonInclude(NON_NULL)`) |
| `internal/controller/LocationInventoryRollupController` | `GET /v1/inventory/locations/{locationId}/inventory-rollup?parentType&sku&expand&depth&includeEmpty`; `expand` accepts only `tree` (else 400); full OpenAPI incl. 422/503 |

### Tests (16 new)

- Service (7): grand total = sum of site totals, expand=tree inlines per-site
  trees, over-cap with expand → 422 before any site computation, over-cap
  without expand → summaries fine, parentType validation (unknown → 400,
  case-insensitive), no descendants → empty 200, 404 propagation.
- Contract IT (7): 200 summaries (`nodes` absent from JSON), 200 expand=tree,
  `expand=bogus` → 400, unknown parentType → 400 VALIDATION_ERROR, over-cap →
  422 ROLLUP_EXPANSION_TOO_LARGE, unknown location → 404, 403 without
  authority.
- Client (2): descendants contract field mapping, 404.
- Full module: **306/306 pass**. Spotless applied.

### Notes

- All descendants are treated as candidate sites; non-sites yield empty
  topology → zero totals. A future pos-location `LocationType` filter on the
  descendants endpoint could trim the fan-out.
- Sequential per-site computation in v1; parallelize only if NFR pressure
  appears (cap bounds the worst case).

---

## CAP-218 story set status

| Story | Commit | Status |
| --- | --- | --- |
| #656 allocation ledger events | `1c4b529` | ✅ |
| #657 bulk grouped queries | `68c87ff` | ✅ |
| #658 site rollup endpoint | `63676bd` | ✅ |
| #659 parent-location rollup | `7ee1d9a` | ✅ |
| #662 consumption closes allocations (from PR #661 review finding 3) | `4efb8df` | ✅ |

All four CAP-218 stories of the inventory-location-rollup spec are complete
on `cap/CAP218` (together with CAP-214 #655 on `cap/CAP214`).

**Pull requests (2026-06-12):**
[#661](https://github.com/louisburroughs/durion-positivity-backend/pull/661)
(CAP-218, this branch) depends on
[#660](https://github.com/louisburroughs/durion-positivity-backend/pull/660)
(CAP-214) — merge #660 first.

