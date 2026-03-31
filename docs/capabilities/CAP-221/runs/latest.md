# Capability Run Artifact — CAP-221 Wave I-b

Use this run record with:

- Manifest: docs/capabilities/CAP-221/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-221/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 1. Run Metadata

- Capability: CAP-221
- Run Timestamp (UTC): 2026-03-29T00:00:00Z
- Agent/Operator: Orchestrator (Wave I-b)
- Branch(es): `cap/inventory-wave-i-b`
- Status: partial — 1/2 stories done; 1 deferred (implementation pending; contract blockers cleared)

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-221/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-221/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Audit Log Search and Detail | #86 | #86 | done | Inventory event audit log table, event detail, search/filter, pagination |
| Inventory Security Admin (Permissions) | #87 | #87 | deferred | Inventory permission catalog is normalized, gating matrix is documented, and current-user permissions are available from JWT claims |

## 4. Implementation Changes

### Frontend Files Changed

- `src/app/features/security/services/security-audit.service.ts`
- `src/app/features/security/pages/audit-logs/audit-logs.component.ts`
- `src/app/features/security/pages/audit-logs/audit-logs.component.html`
- `src/app/features/security/pages/audit-logs/audit-logs.component.css`
- `src/app/features/security/pages/audit-logs/audit-logs.component.spec.ts`

### Behavior Implemented

- Inventory event audit log list with keyword search and entity/event-type filters (`searchEvents`)
- Event detail view with full event payload (`getEvent`)
- Inventory context filter for scoping to inventory events
- Pagination with empty/loading/error states per ADR-0031

### Deferred

- #87 Inventory permission admin — security permission endpoints exist, inventory permission registration is normalized, the gating matrix is documented, and current-user permissions can be read from JWT claims

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #86 Audit Log Search | `searchEvents` | pos-security-service / sdk-security | `security-audit.service.ts` | done |
| #86 Audit Log Detail | `getEvent` | pos-security-service / sdk-security | `security-audit.service.ts` | done |
| #87 Inventory Permissions | `getPermissionsByDomain` | pos-security-service / sdk-security | — | ready — `GET /v1/permissions/domain/inventory` can drive the read-only inventory permission catalog |
| #87 Effective User Permissions | JWT `authorities` claim | authenticated session token | `AuthService.currentUserClaims()` | ready — no separate current-user lookup endpoint required for route/action gating |

## 6. Validation

### Commands Run

```bash
cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend
npm run build
npx ng test --no-watch
```

### Results

- Build: pass
- Tests: pass (218/218 across 24 spec files)
- Lint: pass
- Typecheck: pass

## 7. Blockers and Decisions

- Blocker cleared: #87 is no longer blocked by missing RBAC primitives, inventory permission drift, or current-user permission lookup
  - Impact: Inventory Security Admin is now implementation-ready from a contract perspective
  - Needed: Execute the frontend page and gating work against the documented matrix

## 8. Follow-Up Actions

- [ ] Implement the Inventory Security permission catalog and gating UI against the published matrix and JWT claim-based permission source
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 1 explicitly blocked with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #87 not yet verified (implementation still pending).
