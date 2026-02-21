# ADR 0022 Remediation Checklist: Stable Person ID in Audit Records

Status: ACTIVE
Last Updated: 2026-02-21
Owner: Platform + Domain Teams
Related ADR: `0022-audit-stable-person-identifier-claim-policy.adr.md`

## Scope

This checklist tracks remediation for modules that persist audit actor identity and must move to ADR-0022 policy:

- authoritative actor identity comes from security context
- stable `personId` is used for immutable audit lineage
- `@EmitEvent` is telemetry/observability and not the system-of-record audit trail

## Platform Foundation

- [x] `pos-security-service`: JWT supports `personId` claim, extraction endpoint added
- [x] `pos-api-gateway`: forwards `X-User-Id` sourced from security service
- [x] `pos-security-common`: `SecurityContextHelper` resolves stable user/person id from authenticated context

## Module Checklist

### `pos-inventory`

- [x] Audit actor resolution switched to security-context sourced stable id
- [x] `AuditActorRef` semantics clarified to stable actor identifier
- [x] Approval/audit writes use context actor id + username pair
- [ ] Add/expand tests that assert audit actor id comes from security context, not request DTO
- [ ] Verify all inventory audit write paths (not just cycle count approval) use shared helper methods

### `pos-accounting`

- [x] Audit actor id type migrated to `String` in audit entity/DTO/event payloads
- [x] Audit write service uses `SecurityContextHelper.getCurrentUserIdOrDefault(...)`
- [x] Actor query path/repository updated for string actor id
- [x] `AuditTrailServiceTest` + `AuditTrailContractBehaviorIT` aligned and passing in reactor
- [x] Update `openapi.yaml` examples/descriptions to explicitly call actor id a stable person identifier
- [x] De-emphasize client-supplied `actorId` in write requests (now deprecated/optional; authoritative actor comes from security context)

### `pos-people`

- [x] Replace username-based actor sourcing with stable person id helper in:
  - `TimeEntryServiceImpl`
  - `TimeEntryExceptionServiceImpl`
  - `TimeEntryAdjustmentServiceImpl`
  - `EmployeeServiceImpl` / offboarding retry writes
  - `UserPersonLinkServiceImpl`
  - `WorkSessionServiceImpl`
- [x] Ensure `TimeEntryAudit.actorId` stores stable person id semantics
- [x] Update unit/integration tests to assert actor id comes from security context
- [ ] Validate any API docs/examples referencing actor id semantics

### `pos-workorder`

- [x] Inventory persisted audit entities touched in this pass (`AuditEvent`, `ApprovalRecord`)
- [x] Ensure `AuditEvent.userId` and `ApprovalRecord.resolvedBy` are sourced from security context stable id
- [x] Extend same treatment to part adjustment/usage and snapshot/transition actor fields
- [x] Remove dependence on caller-provided actor ids where present in start/complete/reopen controller flow (request `userId` now deprecated compatibility only; authenticated context is authoritative)
- [ ] Add/adjust tests for approval/reopen/adjustment audit entries to verify stable actor id usage (part adjustment/usage contract tests now cover idempotent and persisted actor behavior)
- [ ] Confirm workorder audit is not treated as equivalent to `@EmitEvent` telemetry

## Verification Gates (per module)

- [ ] Contract and unit tests pass for audit write/read paths
- [ ] ArchUnit/layering rules remain green
- [ ] No request DTO field is treated as authoritative actor identity
- [ ] Audit records remain append-only and immutable

## Rollout Notes

- No data migration required (environment currently has no production audit history to preserve).
- Apply changes module-by-module with isolated PRs and focused test runs.
