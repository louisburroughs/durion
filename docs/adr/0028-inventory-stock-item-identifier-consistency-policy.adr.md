# ADR-0028: Inventory Stock Item Identifier Consistency Policy

**Status:** ACCEPTED  
**Date:** 2026-02-28  
**Deciders:** Platform Architecture Team, Inventory Domain Lead  
**Affected Issues:** Inventory reservation/on-hand key mismatch, reallocation correctness, service contract clarity

---

## Context

Inventory flows currently mix identifier meaning and naming across contracts and persistence:

- Reallocation requests use `sku` in API/service contracts.
- Reservation records store `sku` as a `String`.
- Inventory ledger on-hand math uses `stockItemId` as a lookup key.
- Some writers persist UUID values converted to string, while others persist external SKU strings.

This can cause on-hand calculations to use a different key than the reservations being reallocated, producing incorrect ATP/reallocation outcomes.

ADR-0013 and ADR-0027 establish UUID typing for platform-owned identifiers. This ADR applies that rule specifically to inventory lookup keys and forbids mixed-key semantics.

---

## Decision

### 1. Canonical Inventory Lookup Key

**Decision:** ✅ **Resolved** - The canonical key for inventory availability, reservation, and allocation lookup is `stockItemId` typed as `UUID`.

`stockItemId` is a platform-owned identifier and must not be modeled as free-form text.

### 2. Reallocation Contract

**Decision:** ✅ **Resolved** - Reallocation APIs/services must accept `stockItemId: UUID` (not `sku: String`) when computing on-hand and selecting reservations to rebalance.

### 3. Reservation Model Alignment

**Decision:** ✅ **Resolved** - Reservation entities/DTOs/repositories must store and query the lookup key as `stockItemId: UUID`.

Reservation lookup for reallocation must use the same key domain as ledger on-hand queries.

### 4. Ledger Model Alignment

**Decision:** ✅ **Resolved** - Inventory ledger entry lookup key must be `stockItemId: UUID`, and on-hand repository query methods must accept `UUID`.

String-based overloads for platform stock item lookups are not allowed.

### 5. External Business Identifiers

**Decision:** ✅ **Resolved** - External business IDs (for example `sku`, `partNumber`, supplier item codes) remain `String` and are not used as the canonical inventory lookup key unless explicitly designated as external-only references.

If a flow receives an external identifier, it must resolve that identifier to `stockItemId: UUID` before creating reservations, writing ledger entries, or computing on-hand.

### 6. Prohibited Patterns

**Decision:** ✅ **Resolved** - The following are prohibited:

- Using `.toString()` conversions of UUIDs as inventory lookup keys.
- Passing `sku` text directly into on-hand or reservation lookup methods.
- Modeling platform lookup IDs as `String` in new inventory contracts.

---

## Consequences

### Positive ✅

- Correctness: reservation reallocation and ATP use the same identifier domain.
- Clarity: service callers can see required ID type at compile time.
- Consistency: DTO/entity/repository contracts align with ADR-0013 and ADR-0027.
- Safety: reduces silent key mismatches caused by text-based IDs.

### Negative ⚠️

- Refactoring required in inventory DTOs/entities/repositories and dependent tests.
- Database migrations required where lookup columns are currently string-based.
- Upstream integrations that provide only external SKU values require explicit resolution logic.

### Neutral

- External identifiers remain supported as business reference fields.

---

## Implementation Notes

- Use `stockItemId` naming for platform lookup identifiers in inventory code paths.
- Use `UUID` for:
  - Reallocation request/response identifiers where lookup occurs
  - Reservation lookup fields
  - Ledger lookup query signatures
- Keep external fields explicitly named (`sku`, `partNumber`, `external*Id`) and separate from lookup IDs.
- Add tests that verify reallocation uses identical key values for:
  - reservation selection
  - on-hand calculation
- Add architecture/static checks that reject `String` platform lookup IDs in inventory contracts.

---

## References

- Related: `0001-inventory-ledger-atp-computation.adr.md`
- Related: `0013-platform-uuid-identifier-strategy.adr.md`
- Related: `0027-uuid-typed-id-contract-policy.adr.md`

