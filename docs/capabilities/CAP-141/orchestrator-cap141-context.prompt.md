---
name: "CAP-141 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-141 Roles, Permissions, and Audit Controls."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-141 runs.

## Capability Scope
- Capability: `CAP:141` — Roles, Permissions, and Audit Controls
- Parent issue: `louisburroughs/durion#141`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#61`
  - `louisburroughs/durion-positivity-backend#62`

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-141/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#61`, `#62` in `durion-positivity-backend`
3. Domain/contract context:
   - `durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md`
   - `durion/domains/shopmgmt/.business-rules/DOMAIN_NOTES.md`
4. Authorization and permission registry sources:
   - `durion-positivity-backend/.github/permissions/permissions_v2.yml`
   - `durion-positivity-backend/scripts/permissions-aggregate.yaml`
   - `durion-positivity-backend/pos-shop-manager/src/main/resources/permissions.yaml`
   - `durion-positivity-backend/pos-security-service/src/main/resources/permissions.yaml`
5. Controller behavior baseline (required for role synthesis):
   - `durion-positivity-backend/pos-shop-manager/src/main/java/com/positivity/shopmanager/internal/controller`
   - `durion-positivity-backend/pos-security-service/src/main/java/com/positivity/securityservice/internal/controller`
6. Security ADRs and policies:
   - `durion/docs/adr/0011-api-gateway-security-architecture.adr.md`
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`

## Clarification Precedence Rules (Hard)
- If issue body conflicts with newer owner clarification comments in `#61`/`#62`, use the newer owner comment.
- If prompt text conflicts with CAP manifest metadata, use `CAPABILITY_MANIFEST.yaml`.
- For permission naming and role composition, `permissions_v2.yml` is canonical.

## Module and Ownership Guidance
- `pos-shop-manager` owns shop scheduling/assignment authorization checks in its domain.
- `pos-security-service` owns role/permission registration and assignment APIs.
- Cross-module permission naming must remain canonical and registry-backed.
- Audit identity must be sourced from authenticated security context (no caller-supplied actor fields).

## Candidate Role Synthesis (Hard Requirement)
Agents must produce candidate role definitions based on **actual controller behavior** and **canonical permissions**.

### Required Inputs
- All relevant `@PreAuthorize` expressions from CAP-141 impacted controllers.
- Permission entries from `.github/permissions/permissions_v2.yml`.

### Role-Creation Rules
- Do not invent permission IDs absent from `permissions_v2.yml`.
- Normalize controller guards into explicit permission requirements.
- Expand `hasAnyAuthority(...)` into alternatives and document selected role strategy.
- Treat `hasRole(...)` usage in security-service as legacy/compatibility signals and map to canonical permission bundles for output.
- Flag every guard that references a non-canonical or unregistered authority as a discrepancy requiring follow-up.

### Minimum Role Deliverable
Provide `Candidate Roles v0` with:
1. `role_name`
2. `persona`
3. `permission_bundle` (canonical IDs from `permissions_v2.yml`)
4. `endpoint_coverage` (controller methods/guards covered)
5. `discrepancies` (missing registration, naming mismatch, role-vs-authority mismatch)
6. `audit_scope` (what actions are auditable for the role)

At least 4 role candidates should be proposed (for example: read-only scheduler, dispatcher, shop manager, security admin), with names refined to match domain language in `#61/#62`.

## Story-Specific Non-Negotiables
- Role recommendations must be traceable to current controller guard behavior.
- Permission naming must conform to canonical naming conventions from `permissions_v2.yml`.
- Audit-critical operations (role assignment, override actions, high-impact schedule changes) must have explicit role coverage and test expectations.

## Error and Status Semantics
- Apply ADR-0017 semantics for unauthorized/forbidden outcomes:
  - `401` for unauthenticated requests
  - `403` for authenticated but insufficient authority
- Require explicit tests for 401/403 boundaries on CAP-141 touched endpoints.

## CAP-141 Execution Deliverables
- Candidate role matrix derived from controllers + `permissions_v2.yml`.
- Permission discrepancy report (controller guards vs registry/manifests).
- Proposed remediation plan for discrepancies (controller update vs permission registration update).
- RED/GREEN evidence for story slices in `#61/#62`.
- Code review PASS evidence and coverage hardening evidence.

## Blocker Policy for CAP-141
- If blocked, report:
  - exact ambiguity or mismatch,
  - affected story (`#61` or `#62`),
  - impacted controller/permission,
  - recommended resolution path.
