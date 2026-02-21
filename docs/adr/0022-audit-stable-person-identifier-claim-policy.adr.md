# ADR-0022: Audit Stable Person Identifier Claim Policy

**Status:** ACCEPTED  
**Date:** 2026-02-21  
**Deciders:** Architecture Team, Backend Lead, Security Lead  
**Affected Issues:** [Audit identity durability], [JWT claim policy]

---

## Context

ADR-0018 established that audit actor fields must be sourced from authenticated security context and stored as strings. Current JWT usage commonly includes username, but username can change over time.  

If audit identity is linked only to username:

- historical attribution can become ambiguous after username changes,
- actor joins across systems become fragile,
- long-term compliance/audit trails are harder to preserve.

We need a stable, non-changing identity claim for audit actor linkage while preserving a human-readable actor name.

---

## Decision

✅ **Resolved** — JWTs must carry a stable person identifier claim for audit identity. Username remains a display attribute and must not be the canonical audit key.

### 1. Canonical Audit Actor Identity

**Decision:** ✅ **Resolved** — Use `personId` (string) as the canonical audit actor identifier in token-derived context.

- `personId` is immutable for the person identity lifecycle.
- Audit actor `id` must resolve from `personId` when present.
- Username must not be used as the canonical join key for audit history.

### 2. Display Identity

**Decision:** ✅ **Resolved** — Keep username in token claims for display and operator readability.

- Audit actor `username`/display field can use username.
- Username changes do not alter historical actor identity linkage.

### 3. Token Claim Policy

**Decision:** ✅ **Resolved** — Tokens should include:

- stable identity claim: `personId` (or equivalent standard claim mapped to same meaning),
- display claim: `username` (or equivalent display principal),
- existing subject semantics remain allowed, but audit mapping must prefer stable person identity.

### 4. Fallback and Compatibility

**Decision:** ✅ **Resolved** — During migration/compatibility windows:

- if `personId` is absent, use configured deterministic fallback (`system` for non-user flows, existing principal string for legacy authenticated flows),
- services must log absence of `personId` at appropriate level for migration visibility,
- once adoption is complete, `personId` becomes mandatory for user-authenticated tokens.

### 5. Audit vs Telemetry Boundary

**Decision:** ✅ **Resolved** — `@EmitEvent` is not the authoritative audit mechanism.

- `@EmitEvent` remains telemetry/observability instrumentation.
- Audit policy in this ADR applies to explicit audit entities/events and persisted audit records.
- Teams must not treat `@EmitEvent` payloads as the canonical compliance audit trail unless a module-specific ADR explicitly defines that contract.

---

## Alternatives Considered

1. **Keep username-only identity**
   - ❌ Rejected: mutable identifier weakens long-term audit traceability.

2. **Add `userId` and keep username, no `personId`**
   - ❌ Rejected: does not guarantee stable cross-domain person identity semantics if `userId` is account-scoped or mutable in some systems.

3. **Use only opaque subject without display claim**
   - ❌ Rejected: harms operational readability and support workflows.

---

## Consequences

### Positive ✅

- Durable audit attribution even when usernames change.
- Clear separation of stable identity (`personId`) vs display identity (`username`).
- Better cross-service consistency for actor lineage and analytics.

### Negative ⚠️

- Security service token issuance must be updated to include `personId`.
- Consumer services must update claim extraction/mapping logic.
- Temporary dual-mode handling is required during migration.

### Neutral

- ADR-0018 remains valid and is extended, not replaced.
- Actor fields remain string-typed in persistence and event contracts.

---

## Implementation Notes

- Token issuer (`pos-security-service`) must emit `personId` claim for authenticated users.
- Security helpers/common modules should expose helper methods that prioritize stable identity claim resolution.
- Do not use `@EmitEvent` alone to satisfy audit requirements; persist/use explicit audit models/events where audit is required.
- Audit event mapping should follow:
  - `actor.id` -> `personId` (canonical),
  - `actor.username` -> username/display principal.
- Integration and contract tests should verify:
  - username change does not break actor identity continuity,
  - fallback behavior for legacy tokens without `personId`,
  - non-user/system flows still resolve deterministic actor identity.

---

## References

- [ADR-0018: Audit Actor Fields from Security Context as Strings](0018-audit-actor-fields-from-security-context.adr.md)
- [ADR-0014: Gateway Internal Service Security](0014-gateway-internal-service-security.adr.md)
- [ADR-0022 Remediation Checklist](0022-audit-stable-person-id-remediation-checklist.md)
- `durion-positivity-backend/pos-security-common/src/main/java/com/positivity/security/common/SecurityContextHelper.java`
- `durion-positivity-backend/pos-inventory/src/main/java/com/positivity/inventory/internal/event/AuditActorRef.java`
