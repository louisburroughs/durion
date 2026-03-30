# ADR-0036: Frontend Security Audit Model Ownership Boundary

**Status:** ACCEPTED
**Date:** 2026-03-30
**Deciders:** Frontend Architecture Team
**Affected Issues:** PR #13 review thread r3006970093; CAP-221 (#86)

---

## Context

**Current State**: The inventory Wave I-b PR (#13) introduced four security-domain TypeScript interfaces at the bottom of `src/app/features/inventory/models/inventory.models.ts` under a `// Security Audit (CAP-221 #86)` comment:

- `AuditEventFilter`
- `AuditEventDetail`
- `AuditEventPageResponse`
- `AuditExportJob`

These interfaces are consumed exclusively by:

- `src/app/features/security/services/security-audit.service.ts`
- `src/app/features/security/pages/audit-logs/audit-logs.component.ts`

**The Problem**: Placing security-domain model interfaces inside `inventory.models.ts` creates an upward cross-domain dependency: the security feature module must import from the inventory feature module. This violates ADR-0010 (Frontend Domain Responsibilities Guide), which requires each domain to own its model layer and prohibits cross-domain feature imports without an explicit contract dependency.

**Drivers**:

- The audit log functionality (CAP-221) is a security operations concern, not an inventory concern; the audit events captured cover all domains (orders, workorders, pricing, inventory) and are not scoped to inventory alone.
- Cross-domain TypeScript imports create brittle coupling: a rename or reorganisation of `inventory.models.ts` breaks the security feature's compilation.
- The `src/app/features/security/` domain currently has no `models/` directory; this is the root cause that caused the interfaces to land in `inventory.models.ts` as a convenience shortcut.

**Scope**: `durion-positivity-frontend` — the Angular 21 SPA.

---

## Decision

### 1. Home for Security Audit Models

**Decision:** ✅ **Resolved** — The four security audit interfaces (`AuditEventFilter`, `AuditEventDetail`, `AuditEventPageResponse`, `AuditExportJob`) must be declared in `src/app/features/security/models/security-audit.models.ts`. The inventory models file must not contain any security-domain interfaces.

### 2. Cross-Domain Import Prohibition

**Decision:** ✅ **Resolved** — Feature modules must not import TypeScript interfaces from a sibling feature module's `models/` directory. If two feature domains share a structural type, that type belongs either in the owning domain's `models/` or (only when genuinely cross-cutting) in `src/app/core/models/`. Audit event types are owned by the security domain.

### 3. Migration Requirement

**Decision:** ✅ **Resolved** — The migration from `inventory.models.ts` to `security/models/security-audit.models.ts` is a required follow-on to PR #13 and must be completed before the next PR that touches either `inventory.models.ts` or any file under `src/app/features/security/`. The migration consists of:

1. Create `src/app/features/security/models/security-audit.models.ts` containing the four interfaces.
2. Update `security-audit.service.ts` and `audit-logs.component.ts` to import from `../models/security-audit.models` (relative to their location within the security feature).
3. Remove the four interfaces and the `// Security Audit (CAP-221 #86)` comment block from `inventory.models.ts`.
4. Verify no other file imports these interfaces from `inventory.models.ts`; update any remaining imports.
5. Run `npm run build` and `npx ng test --no-watch` to confirm no regressions.

---

## Alternatives Considered

1. **Keep interfaces in a shared `src/app/core/models/audit.models.ts`**: Rejected. `core/` is for framework-level abstractions (HTTP wrapper, auth guards, logger). Placing domain API response shapes in `core/` would conflate infrastructure with business-domain models and contradict the precedent set by every other domain in this codebase.

2. **Keep interfaces in `inventory.models.ts` permanently**: Rejected. Audit events cover all domains (orders, pricing, HR, inventory). Placing them permanently in inventory implies an incorrect ownership relationship. When the audit domain grows (additional event types, export formats, retention policies), developers would incorrectly look in the inventory module.

3. **Create a new shared `audit` feature domain**: Deferred. The audit log UI capability (CAP-221) is a security operations page surfaced behind a security role. The existing `security` feature domain is the correct home. A separate `audit` domain would be warranted only if audit functionality expanded into a standalone application area with its own navigation group.

---

## Consequences

### Positive ✅

- **Domain boundary integrity**: The security feature module becomes self-contained; its model layer is co-located with its services and pages.
- **Reduced coupling**: `inventory.models.ts` no longer contains types unrelated to inventory. A future rename or split of the inventory domain has no impact on the security feature.
- **Discoverability**: Developers working on audit features will find types where they expect them — in `src/app/features/security/models/`.
- **Consistent structure**: The security domain follows the same `models/` directory pattern established by `product`, `crm`, `accounting`, and `people` domains.

### Negative / Trade-offs ⚠️

- **One-time migration effort**: Two files (`security-audit.service.ts`, `audit-logs.component.ts`) require import path updates. Low risk — both are internal to the security feature.
- **Merge window**: The migration causes a non-empty diff to `inventory.models.ts`, which should be coordinated with any concurrent inventory-domain work to avoid merge conflicts.

---

## Compliance Checklist

When implementing the migration, verify:

- [ ] `src/app/features/security/models/security-audit.models.ts` created with all 4 interfaces
- [ ] `security-audit.service.ts` imports from `../models/security-audit.models`
- [ ] `audit-logs.component.ts` imports from `../../models/security-audit.models`
- [ ] `inventory.models.ts` no longer contains `AuditEventFilter`, `AuditEventDetail`, `AuditEventPageResponse`, `AuditExportJob`
- [ ] `grep -r "from '.*inventory.models'" src/app/features/security/` returns no results
- [ ] `npm run build` — clean
- [ ] `npx ng test --no-watch` — all tests pass
