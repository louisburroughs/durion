# ADR-0024: Entity createdAt/updatedAt Population Policy

**Status:** ACCEPTED  
**Date:** 2026-02-23  
**Deciders:** Architecture, Backend Leads  
**Affected Issues:** CAP-214 #39

---

## Context

- **Current State**: Entities in `durion-positivity-backend` use inconsistent timestamp population patterns. Some use `Instant.now()` in `@PrePersist`, some in `@PreUpdate`, and some only track one field.
- **The Problem**: `updatedAt` semantics are inconsistent on create operations, and direct `Instant.now()` calls are hard to test deterministically.
- **Drivers**:
  - consistent audit semantics across services,
  - deterministic and time-condensed tests,
  - reduced duplicated timestamp logic in entities.
- **Scope**: All mutable JPA entities that persist lifecycle timestamps in `durion-positivity-backend`.

---

## Decision

### 1. createdAt/updatedAt semantics

**Decision:** ✅ **Resolved** - Mutable entities that track timestamps MUST have both `createdAt` and `updatedAt`. On insert, both are set to the same instant. On update, only `updatedAt` changes.

### 2. Timestamp population mechanism

**Decision:** ✅ **Resolved** - Standardize on Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`) with `@EntityListeners(AuditingEntityListener.class)` rather than ad-hoc `@PrePersist/@PreUpdate` timestamp logic.

### 3. Time source

**Decision:** ✅ **Resolved** - Timestamp generation MUST use an injected `Clock` via `DateTimeProvider` configuration. Direct `Instant.now()` in entities/services for audit fields is disallowed.

### 4. Transitional allowance

**Decision:** ✅ **Resolved** - Existing entities may keep legacy callbacks temporarily, but any touched entity must migrate to the standard auditing approach in the same change when feasible.

---

## Alternatives Considered

1. **Keep `@PrePersist/@PreUpdate` with `Instant.now()` everywhere**  
   Rejected: still non-deterministic in tests and easy to implement inconsistently.

2. **Set `updatedAt` only on update and leave null at create**  
   Rejected: introduces null-handling complexity and inconsistent analytics/query behavior.

3. **Database-only defaults/triggers for all timestamps**  
   Rejected: less transparent in application code and harder to enforce uniformly across local/test environments.

---

## Consequences

### Positive ✅

- Uniform audit semantics across entities.
- Deterministic tests via fixed `Clock`.
- Reduced duplicate callback logic and fewer semantic bugs.

### Negative ⚠️

- Requires migration effort for legacy entities.
- Adds central auditing configuration that must be enabled in all relevant services.

### Neutral

- Existing schema may not require changes if `createdAt/updatedAt` columns already exist.

---

## Implementation Notes

- Add shared auditing configuration:
  - `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`
  - bean: `Clock` (system default in runtime, fixed in tests)
  - bean: `DateTimeProvider` from `Instant.now(clock)`
- For entity migration:
  - add `createdAt` and `updatedAt` columns where missing,
  - annotate fields with `@CreatedDate` and `@LastModifiedDate`,
  - remove timestamp-setting callback code.
- Test strategy:
  - integration tests with fixed `Clock`,
  - verify insert sets both fields equal,
  - verify update changes only `updatedAt`.
- Rollout:
  1. Introduce shared auditing + clock infra.
  2. Migrate entities incrementally by module.
  3. Remove legacy timestamp callbacks after migration completion.

---

## References

- Related ADRs:
  - `0013-platform-uuid-identifier-strategy.adr.md`
  - `0018-audit-actor-fields-from-security-context.adr.md`
- Related code:
  - `durion-positivity-backend/pos-location/src/main/java/com/positivity/location/internal/entity/Location.java`

