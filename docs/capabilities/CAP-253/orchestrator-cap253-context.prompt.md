---
name: "CAP-253 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-253 Roles, Permissions, and Audit Controls."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-253 runs.

## Capability Scope
- Capability: `CAP:253` — Roles, Permissions, and Audit Controls
- Parent issue: `louisburroughs/durion#253`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#1`
  - `louisburroughs/durion-positivity-backend#2`

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-253/CAPABILITY_MANIFEST.yaml`
2. CAP contract context:
   - `durion/docs/capabilities/CAP-253/CAP-253-backend-contract.md`
3. Story issues:
   - `#1`, `#2` in `durion-positivity-backend`
4. Domain/contract context:
   - `durion/domains/security/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/security/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/security/.business-rules/DOMAIN_NOTES.md`
5. Authorization and permission registry sources:
   - `durion-positivity-backend/.github/permissions/permissions_v2.yml`
   - `durion-positivity-backend/scripts/permissions-aggregate.yaml`
   - `durion-positivity-backend/pos-security-service/src/main/resources/permissions.yaml`
   - `durion-positivity-backend/pos-accounting/src/main/resources/permissions.yaml`
6. Controller behavior baseline (required for role synthesis and gap checks):
   - `durion-positivity-backend/pos-security-service/src/main/java/com/positivity/securityservice/internal/controller`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/controller`
7. Security and API ADRs:
   - `durion/docs/adr/0011-api-gateway-security-architecture.adr.md`
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`

## Clarification Precedence Rules (Hard)
- If issue body conflicts with newer owner clarification comments in `#1`/`#2`, use the newer owner comment.
- If prompt text conflicts with CAP manifest metadata, use `CAPABILITY_MANIFEST.yaml`.
- For permission naming and role composition, `permissions_v2.yml` is canonical.

## Existing Implementation Baseline (Hard Constraint)
- Some CAP-253 functionality is already implemented.
- Agents must treat this as a **hardening and completion** capability, not greenfield.
- First step is mandatory:
  - inventory what is already implemented,
  - map implementation to CAP-253 acceptance criteria,
  - identify standards gaps and missing functions,
  - then execute targeted upgrades.

## Hardening and Gap-Closure Workflow (Required)
1. Produce `Current State vs Required State` matrix for stories `#1` and `#2`.
2. Classify each item as:
   - `compliant`,
   - `partially implemented`,
   - `missing`,
   - `implemented but below standard`.
3. Prioritize fixes in this order:
   - security correctness and authorization boundaries,
   - audit correctness and actor attribution,
   - contract/OpenAPI alignment,
   - test and coverage hardening.
4. Avoid rewriting stable code paths unless needed to satisfy standards or missing behavior.

## Module and Ownership Guidance
- `pos-security-service` owns role/permission registration, role assignment, and security audit APIs.
- `pos-accounting` (and other consuming services) own endpoint-level authority checks for their domain operations.
- Cross-module permission naming must remain canonical and registry-backed.
- Audit identity must be sourced from authenticated security context (no caller-supplied actor fields).

## Candidate Role Synthesis (Hard Requirement)
Agents must produce candidate role definitions using **actual controller behavior** and **canonical permissions**.

### Required Inputs
- `@PreAuthorize` (and equivalent) guards from CAP-253 impacted controllers.
- Permission entries from `.github/permissions/permissions_v2.yml`.

### Role-Creation Rules
- Do not invent permission IDs absent from `permissions_v2.yml`.
- Normalize controller guards into explicit permission requirements.
- Expand `hasAnyAuthority(...)` alternatives and document strategy.
- Treat `hasRole(...)` usage as compatibility signals; output canonical permission bundles.
- Flag every guard that references non-canonical or unregistered authorities as a discrepancy.

### Minimum Role Deliverable
Provide `Candidate Roles v0` with:
1. `role_name`
2. `persona`
3. `permission_bundle` (canonical IDs from `permissions_v2.yml`)
4. `endpoint_coverage` (controller methods/guards covered)
5. `discrepancies` (missing registration, naming mismatch, role-vs-authority mismatch)
6. `audit_scope` (what actions are auditable for the role)

At least 4 role candidates should be proposed (for example: security admin, role manager, auditor/read-only, domain operator), with names aligned to story language in `#1/#2`.

## Story-Specific Non-Negotiables
- All recommendations and fixes must be traceable to current controller guard behavior.
- Permission naming must conform to canonical naming conventions from `permissions_v2.yml`.
- Audit-critical operations (role assignment, permission changes, high-impact overrides) must have explicit role coverage and tests.
- Any missing function discovered during gap analysis must be implemented or explicitly documented as blocked.

## Error and Status Semantics
- Apply ADR-0017 semantics for auth failures:
  - `401` for unauthenticated requests
  - `403` for authenticated but insufficient authority
- Require explicit tests for 401/403 boundaries on CAP-253 touched endpoints.

## CAP-253 Execution Deliverables
- `Current State vs Required State` gap matrix for `#1/#2`.
- Candidate role matrix derived from controllers + `permissions_v2.yml`.
- Permission discrepancy report (controller guards vs registry/manifests).
- Remediation plan for discrepancies (controller update vs permission registration update).
- RED/GREEN evidence for each acceptance slice.
- Code review PASS evidence and coverage hardening evidence.

## Blocker Policy for CAP-253
- If blocked, report:
  - exact ambiguity or mismatch,
  - affected story (`#1` or `#2`),
  - impacted controller/permission/function,
  - recommended resolution path.
