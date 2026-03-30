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
- Status: partial — 1/2 stories done; 1 deferred (ownership and gating details pending)

## 2. Inputs Used

- Manifest: docs/capabilities/CAP-221/CAPABILITY_MANIFEST.yaml
- Workset: docs/capabilities/CAP-221/AGENT_WORKSET.yaml
- PRD: docs/capabilities/PRD-agent-capability-frontend-execution.md

## 3. Story Execution Summary

| Story | Parent Issue | Frontend Issue | Result | Notes |
| --- | --- | --- | --- | --- |
| Audit Log Search and Detail | #86 | #86 | done | Inventory event audit log table, event detail, search/filter, pagination |
| Inventory Security Admin (Roles & Permissions) | #87 | #87 | deferred | Core RBAC endpoints exist, but inventory ownership, scope, and gating matrix remain unresolved |

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

- #87 Inventory RBAC admin — security endpoints exist, but inventory-scoped ownership and gating rules are not yet finalised

## 5. API Wiring Evidence

For each story, list operations implemented and where they are wired.

| Story | operation_id | SDK/OpenAPI Source | Client/Service File | Status |
| --- | --- | --- | --- | --- |
| #86 Audit Log Search | `searchEvents` | pos-security-service / sdk-security | `security-audit.service.ts` | done |
| #86 Audit Log Detail | `getEvent` | pos-security-service / sdk-security | `security-audit.service.ts` | done |
| #87 Inventory Roles | `getAllRoles` | pos-security-service / sdk-security | — | deferred — endpoint exists; inventory scope/role model pending |
| #87 Inventory Permissions | `getAllPermissions` | pos-security-service / sdk-security | — | deferred — endpoint exists; inventory gating model pending |
| #87 User Role Assignments | `getUserRoleAssignments` | pos-security-service / sdk-security | — | deferred — endpoint exists; inventory admin workflow pending |

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

- Blocker: #87 is no longer blocked by missing RBAC primitives; it is blocked by unresolved feature ownership and scope
  - Impact: Inventory-scoped admin UI cannot be implemented without deciding whether this is a Security feature or an Inventory-admin shell
  - Needed: Confirm owning domain, canonical role catalog, inventory action-to-permission gating matrix, and any role-assignment audit/history requirements

## 8. Follow-Up Actions

- [ ] Confirm ownership, role catalog, and inventory gating matrix for #87
- [ ] Merge `cap/inventory-wave-i-b` to `master` via PR

## 9. Completion Gate

- [x] All workset stories processed (1 done, 1 explicitly blocked with reason).
- [x] All required operations wired or explicitly blocked with reason.
- [ ] Acceptance criteria for #87 not yet verified (blocked).
