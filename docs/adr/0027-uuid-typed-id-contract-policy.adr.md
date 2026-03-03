# ADR-0027: UUID-Typed Identifier Contract Policy

**Status:** ACCEPTED  
**Date:** 2026-02-28  
**Deciders:** Platform Architecture Team, Backend Lead  
**Affected Issues:** Service contract clarity, DTO/entity identifier consistency, lookup safety

---

## Context

Across backend modules, identifier fields are not consistently typed. Some IDs are modeled as `String` in service contracts or DTOs even when the underlying identifier is a platform UUID.

This creates ambiguity for callers and weakens compile-time guarantees:

- Service consumers cannot tell if an ID is a UUID or arbitrary text.
- String-based IDs allow invalid values to pass deeper into business logic.
- DTO/entity contracts become inconsistent and harder to validate.

ADR-0013 established UUID as the platform identifier strategy. This ADR defines the contract-level typing policy so that service callers and persistence models are explicit and consistent.

---

## Decision

### 1. Service Lookup Contracts Must Use UUID Types

**Decision:** ✅ **Resolved** - Any service/API method parameter used to identify or lookup a platform record must be typed as `UUID`, not `String`.

Examples:

- `getById(UUID id)` ✅
- `delete(UUID id)` ✅
- `getById(String id)` ❌ (unless external/non-platform ID by exception rule below)

### 2. DTO Identifier Fields Must Be UUID for Platform IDs

**Decision:** ✅ **Resolved** - All DTO fields that represent platform entity identifiers (primary or foreign keys) must use `UUID`.

Applies to:

- request DTOs
- response DTOs
- event DTOs
- nested DTO references

If a field links to a platform entity, it must be `UUID`.

### 3. Entity Identifier Fields Must Be UUID

**Decision:** ✅ **Resolved** - In JPA entities, all primary keys and foreign-key reference fields for platform entities must be `UUID`.

This applies whether mapped via:

- relationship annotations (`@ManyToOne`, `@JoinColumn`, etc.), or
- explicit FK ID fields.

### 4. Exception: External Business Identifiers

**Decision:** ✅ **Resolved** - IDs originating from external systems or domain business keys may remain non-UUID (commonly `String`).

Allowed examples:

- `sku`
- `partNumber`
- supplier/customer IDs owned by an external system
- OEM or catalog reference codes

Rule: if Durion is not the identity authority for that identifier, non-UUID types are allowed.

### 5. API Serialization

**Decision:** ✅ **Resolved** - UUID values continue to serialize as JSON strings; however, Java contracts must remain `UUID` typed.

This keeps wire compatibility while preserving compile-time type safety in backend code.

---

## Consequences

### Positive ✅

- Clear service contracts: callers immediately know UUID is required.
- Earlier error detection through type checking.
- Consistent DTO/entity modeling across modules.
- Better alignment with ADR-0013 and cross-module architecture standards.

### Negative ⚠️

- Refactors required where legacy contracts use `String` for platform IDs.
- Temporary migration overhead in tests/mappers/controllers.

### Neutral

- External/business identifiers remain unchanged where UUID is not appropriate.

---

## Implementation Notes

- Prefer `UUID` in method signatures for lookup-oriented operations.
- Replace `String` ID fields in DTOs/entities when they refer to platform-owned records.
- Keep explicit naming for external IDs (`sku`, `partNumber`, `external*Id`) to avoid confusion.
- Add/update validation and mapping tests where ID types are changed.
- Enforce through ArchUnit and code review checks where practical.

---

## References

- Related: `0013-platform-uuid-identifier-strategy.adr.md`
- Related: `0026-service-contract-boundary-policy.adr.md`
