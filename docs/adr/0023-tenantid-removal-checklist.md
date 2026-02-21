# ADR 0023 Remediation Checklist: Remove `tenantId`

Status: ACTIVE  
Last Updated: 2026-02-21  
Owner: Platform + Domain Teams  
Related ADR: `0023-remove-tenantid-single-organization-context.adr.md`

## Goal

Remove `tenantId` from active backend contracts, events, and platform docs.  
No backward compatibility bridge is required.

## Phase 1: Contract and Event Model Cleanup

### `pos-inventory`

- [x] Remove `tenantId` from `InventoryAuditEvent` record:
  - `durion-positivity-backend/pos-inventory/src/main/java/com/positivity/inventory/internal/event/InventoryAuditEvent.java`
- [x] Update event construction call sites:
  - `durion-positivity-backend/pos-inventory/src/main/java/com/positivity/inventory/internal/service/CycleCountAdjustmentServiceImpl.java`
- [x] Update contract tests that assert `tenantId`:
  - `durion-positivity-backend/pos-inventory/src/test/java/com/positivity/inventory/contract/InventoryAuditEventContractBehaviorIT.java`
- [ ] Regenerate/update `openapi.yaml` for affected modules after schema changes.

## Phase 2: Security and Context Documentation Alignment

- [x] Remove `tenantId` from active claim lists and examples:
  - `durion/docs/architecture/API_SECURITY_ARCHITECTURE.md`
  - `durion/docs/adr/0011-api-gateway-security-architecture.adr.md`
- [x] Replace with explicit `organizationId` only where organization scoping is truly required.
- [x] Ensure no gateway/header propagation rules mention `tenantId` as current behavior.

## Phase 3: Domain Rule and Capability Doc Cleanup

- [x] Remove or reword `tenantId` references in domain business-rule docs that imply active multi-tenancy.
- [x] Keep future-state notes only if explicitly labeled "future / not implemented".
- [x] Align `.ai` glossary and domain guides to ADR-0023 language.

## Phase 4: Verification Gates

- [x] Repository scan returns no active backend `tenantId` fields in runtime contracts/events:
  - `rg -n --glob '!**/target/**' 'tenantId|tenant_id' /home/louisb/Projects/durion-positivity-backend`
- [ ] Module test suites pass for changed modules.
- [ ] OpenAPI generation passes for changed modules.
- [x] Contract behavior tests assert the updated schema (without `tenantId`).

## Completion Criteria

- [x] No runtime backend contract includes `tenantId`.
- [x] No event envelope requires or emits `tenantId`.
- [ ] Platform architecture docs describe single-organization reality.
- [ ] Any organization scoping uses explicit `organizationId` naming only.
