# ADR-0023: Remove `tenantId` and Adopt Single-Organization Context

**Status:** ACCEPTED  
**Date:** 2026-02-21  
**Deciders:** Backend Architecture, Domain Leads  
**Affected Issues:** Platform bootstrap alignment (no backward-compatibility requirement)

---

## Context

- The platform currently includes optional `tenantId` fields in some contracts and docs.
- The system is not implemented as multi-tenant in storage, authorization boundaries, routing, or operational model.
- Existing `tenantId` usage is inconsistent and can become misleading (for example, empty-string values in emitted events).
- The team is at an early stage and explicitly does not require compatibility-preserving transitions for this cleanup.

In short: current `tenantId` presence implies an architecture the platform does not actually implement.

---

## Decision

### 1. Platform tenancy model

**Decision:** ✅ **Resolved** - The platform is treated as single-organization scoped. `tenantId` is removed from active contracts, code paths, and canonical docs.

### 2. Identity and scoping semantics

**Decision:** ✅ **Resolved** - Do not overload `tenantId` with `organizationId`.

- If organization scope is needed, use explicit `organizationId` naming.
- `organizationId` and `tenantId` are distinct concepts and must not be conflated.

### 3. Contract strategy

**Decision:** ✅ **Resolved** - Breaking changes are allowed for this phase.

- Remove `tenantId` fields directly (no deprecation bridge).
- Update request/response schemas, event payloads/envelopes, and tests to match.

### 4. Documentation alignment

**Decision:** ✅ **Resolved** - Remove references that imply active multi-tenant behavior unless clearly marked as future-state design notes.

---

## Alternatives Considered

1. **Keep `tenantId` optional everywhere**  
   Rejected: preserves ambiguity and encourages accidental pseudo-tenancy behavior.

2. **Map `organizationId` into `tenantId`**  
   Rejected: semantic mismatch, future migration debt, and audit/reporting confusion.

3. **Implement full multi-tenant architecture now**  
   Rejected: out of scope for current stage and not required by current platform constraints.

---

## Consequences

### Positive ✅

- ✅ Contracts and code reflect actual architecture.
- ✅ Reduced cognitive load and fewer ambiguous identifiers.
- ✅ Cleaner path to either explicit organization scoping or future real multi-tenancy.
- ✅ Removes empty or placeholder tenant values from events and APIs.

### Negative ⚠️

- ⚠️ Immediate breaking changes for any consumers expecting `tenantId`.
- ⚠️ Requires coordinated updates across backend contracts, tests, and docs.

### Neutral

- ℹ️ Future multi-tenancy can still be introduced later via a dedicated ADR and explicit model changes.

---

## Implementation Notes

- Execute remediation checklist: `0023-tenantid-removal-checklist.md`.
- Current known code hotspot:
  - `durion-positivity-backend/pos-inventory/src/main/java/com/positivity/inventory/internal/event/InventoryAuditEvent.java`
  - `durion-positivity-backend/pos-inventory/src/test/java/com/positivity/inventory/contract/InventoryAuditEventContractBehaviorIT.java`
- Current known docs hotspots:
  - `docs/architecture/API_SECURITY_ARCHITECTURE.md`
  - `docs/adr/0011-api-gateway-security-architecture.adr.md` (optional claim list)
  - domain rule docs containing `tenantId` guidance

---

## References

- Related ADRs:
  - `0011-api-gateway-security-architecture.adr.md`
  - `0018-audit-actor-fields-from-security-context.adr.md`
  - `0022-audit-stable-person-identifier-claim-policy.adr.md`
- Related glossary:
  - `.ai/GLOSSARY.md`
