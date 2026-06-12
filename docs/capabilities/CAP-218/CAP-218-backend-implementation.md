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
