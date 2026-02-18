# ADR-0018: Audit Actor Fields from Security Context as Strings

**Status:** ACCEPTED  
**Date:** 2026-02-18  
**Deciders:** Architecture, Backend Lead, Security Lead  
**Affected Issues:** N/A

---

## Context

Backend services currently use mixed patterns for audit actor fields (`createdBy`, `updatedBy`, `changedBy`):

- Some request DTOs accept client-provided values.
- Some entity fields are modeled as `UUID`.
- Some service logic sets actor values from request payloads instead of authenticated context.

This creates security and consistency risk:

- Clients can spoof actor identity if the field is accepted from request payload.
- Different modules persist actor data with inconsistent types.
- Controller-level handling duplicates security concerns that should remain in service logic.

The platform already provides authenticated principal access through `SecurityContextHelper` in `pos-security-common`.

---

## Decision

### 1. Source of Truth for Audit Actor

**Decision:** ✅ **Resolved** - `createdBy`, `updatedBy`, and `changedBy` values must be derived from authenticated context using `SecurityContextHelper`.

- Actor identity must come from JWT-authenticated principal propagated by gateway/service security filters.
- Request payload values for these fields are informational at best and must not be used as persistence source of truth.

### 2. Data Type Standard

**Decision:** ✅ **Resolved** - Persist `createdBy`, `updatedBy`, and `changedBy` as `String` in entities and database columns.

- These fields represent actor identity, not domain entity primary keys.
- Actor values may be usernames, subject claims, service principals, or UUID-like strings.
- String storage preserves compatibility with heterogeneous identity providers.

### 3. Layering and Responsibility

**Decision:** ✅ **Resolved** - Actor resolution must happen in the service layer.

- Controllers remain thin and do not resolve or enforce actor identity.
- Service methods set actor values before persistence.
- Shared helper usage must be centralized through `SecurityContextHelper`.

### 4. Fallback Behavior

**Decision:** ✅ **Resolved** - When no authenticated principal is present, services must use a deterministic default actor (for example `"system"`).

- This supports internal automation and non-user flows while maintaining audit completeness.

---

## Alternatives Considered

1. **Keep actor fields as UUID**: Rejected because actor identifiers are not universally UUID-based.
2. **Accept actor identity from API request payload**: Rejected due to spoofing risk and inconsistent enforcement.
3. **Resolve actor in controllers**: Rejected because it violates thin-controller conventions and duplicates security logic.

---

## Consequences

### Positive ✅

- ✅ Stronger audit integrity by tying actor identity to authenticated principal.
- ✅ Uniform actor-field modeling across modules.
- ✅ Reduced contract ambiguity for API consumers.
- ✅ Cleaner separation of concerns (controller vs service responsibilities).

### Negative ⚠️

- ⚠️ Existing DTO/entity/database definitions using `UUID` must be migrated.
- ⚠️ Tests that provide `changedBy`/`updatedBy` in payload may need updates.

### Neutral

- Neutral impact on domain primary-key strategy (`UUID v7` remains unchanged for entity IDs).

---

## Implementation Notes

- Update entity fields and corresponding database columns for `createdBy`, `updatedBy`, and `changedBy` to string-based types.
- For API contracts, these fields should be optional or ignored as persistence inputs when present.
- Service-layer methods must call `SecurityContextHelper.getCurrentUsernameOrDefault("system")` (or equivalent helper methods) when setting actor fields.
- Add integration tests to verify actor persistence comes from authenticated security context, not request body.
- Apply migration scripts for impacted tables and backfill/transform existing data where needed.

---

## References

- [ADR-0011: API Gateway Security Architecture](0011-api-gateway-security-architecture.adr.md)
- [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)
- [ADR-0013: Platform UUID Identifier Strategy](0013-platform-uuid-identifier-strategy.adr.md)
- [`pos-security-common` SecurityContextHelper](../../durion-positivity-backend/pos-security-common/src/main/java/com/positivity/security/common/SecurityContextHelper.java)

---

## Sign-Off

| Role | Name | Date | Notes |
|------|------|------|-------|
| Architecture | LMB | 2026-02-18 | |
| Backend Lead | LMB | 2026-02-18 | |
| Security Lead | LMB | 2026-02-18 | |

---

## Timeline

- **Proposed**: 2026-02-18
- **Accepted**: 2026-02-18

---

## Changelog

- **2026-02-18**: Initial draft
- **2026-02-18**: Marked ACCEPTED
