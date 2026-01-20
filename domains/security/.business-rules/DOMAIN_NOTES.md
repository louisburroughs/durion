# SECURITY_DOMAIN_NOTES.md

## Summary

This document is non-normative and exists to explain the rationale, tradeoffs, and audit/operations implications behind the Security domain’s normative rules. It mirrors the decisions listed in `AGENT_GUIDE.md` and provides alternatives and migration notes so architects and auditors can understand “why”, not just “what”.

## Completed items

- [x] Linked each Decision ID to a detailed rationale

## Decision details

### DECISION-INVENTORY-001 — Deny-by-default authorization

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Authorization returns `DENY` unless an explicit grant exists.
- Alternatives considered:
  - Allow-by-default for authenticated users: simpler UX, unacceptable risk.
  - Mixed allow/deny defaults: flexible, hard to audit and easy to misconfigure.
- Reasoning and evidence:
  - Least privilege and auditability both require explicit grants.
- Architectural implications:
  - Components affected: authorization evaluator, RBAC storage/indexing.
  - Observability: track allow/deny counts by permission key and endpoint.
- Auditor-facing explanation:
  - Inspect: denied requests must have no matching role grants.
  - Artifact: authorization decision logs or security audit entries with `correlationId`.
- Migration & backward-compatibility notes:
  - If any legacy implicit access exists, migrate by creating explicit roles/grants before enabling.
- Governance & owner recommendations:
  - Owner: Security domain owners; review deny spikes as misconfiguration indicator.

### DECISION-INVENTORY-002 — Permission key format and immutability

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Permission keys are immutable strings in the form `domain:resource:action` using snake_case.
- Alternatives considered:
  - Free-form strings: too error-prone.
  - Numeric IDs: not human-auditable without lookup.
- Reasoning and evidence:
  - Immutable keys preserve historical meaning for audits and logs.
- Architectural implications:
  - Components affected: permission registry validation; any “register permission” workflow.
  - Compatibility: renames are prohibited; replacements require a new key and explicit migration.
- Auditor-facing explanation:
  - Inspect: permission registry list; verify keys match the pattern.
- Migration & backward-compatibility notes:
  - For non-conforming legacy keys, introduce a mapping plan: add new keys, migrate grants, then deprecate old keys.
- Governance & owner recommendations:
  - Require code review for new permission keys and a published catalog.

### DECISION-INVENTORY-003 — Role name normalization and uniqueness

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Role name uniqueness is enforced on `roleNameNormalized = lower(trim(collapse_whitespace(roleName)))`.
- Alternatives considered:
  - Case-sensitive uniqueness: confusing duplicates.
  - Forced canonical casing: degrades UX.
- Reasoning and evidence:
  - Prevents “looks duplicate” roles while preserving display casing.
- Architectural implications:
  - Components affected: database uniqueness constraints; role create endpoint.
  - Error contract: `409 ROLE_NAME_TAKEN` (or equivalent) with stable `code`.
- Auditor-facing explanation:
  - Inspect: no two roles share the same normalized name within a tenant.
- Migration & backward-compatibility notes:
  - Backfill normalized column and resolve collisions prior to enabling constraint.
- Governance & owner recommendations:
  - Role naming guideline: short, job-function oriented titles.

### DECISION-INVENTORY-004 — Role name immutability (rename-by-recreate)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: `roleName` is immutable after create. Renames are performed by creating a new role and migrating assignments/grants.
- Alternatives considered:
  - Mutable roleName: breaks audit trails and deep links.
- Reasoning and evidence:
  - Stable identifiers and names reduce operational confusion.
- Architectural implications:
  - Components affected: role update endpoint must reject roleName changes.
  - Audit: rename-by-recreate produces an explicit audit record for the new role.
- Auditor-facing explanation:
  - Inspect: role history should show discrete creates and assignments, not silent renames.
- Migration & backward-compatibility notes:
  - If legacy roleName updates exist, freeze them and introduce the recreate flow.
- Governance & owner recommendations:
  - Require admin justification metadata when migrating assignments.

### DECISION-INVENTORY-005 — RBAC mutation semantics (grant/revoke + replace-set)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: RBAC mutation APIs must be idempotent and support incremental grant/revoke; replace-set is optional.
- Alternatives considered:
  - Replace-set only: hard for UIs to manage concurrency.
  - Incremental only: harder to reconcile desired-state workflows.
- Reasoning and evidence:
  - Supporting both covers admin UI needs and automation use cases.
- Architectural implications:
  - Components affected: role-permission endpoints; uniqueness constraints on `(roleId, permissionKey)`.
  - Status codes: duplicates should return success (no-op) or a deterministic `409` (preferred no-op).
- Auditor-facing explanation:
  - Inspect: each mutation produces a corresponding security audit entry.
- Migration & backward-compatibility notes:
  - Ensure API behavior under retry is documented and tested.
- Governance & owner recommendations:
  - Require “least privilege” review for bulk permission changes.

### DECISION-INVENTORY-006 — Permission registry is code-first (UI read-only)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Permissions are registered by services (code-first). The admin UI can list/search but cannot create/edit permissions.
- Alternatives considered:
  - UI-created permissions: leads to drift and orphaned permissions.
- Reasoning and evidence:
  - Permission creation must be tied to deployed capability and enforcement.
- Architectural implications:
  - Components affected: internal registration endpoint and startup workflows.
  - Operational: missing registration is a deploy-time error.
- Auditor-facing explanation:
  - Inspect: permission registry entries should be attributable to a service/release.
- Migration & backward-compatibility notes:
  - For existing UI-defined permissions, move to code-first manifests.
- Governance & owner recommendations:
  - Require a “permission manifest” per service with ownership and description.

### DECISION-INVENTORY-007 — Principal-role assignments (effective dating; UI deferred)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: The assignment model supports effective dating, but the v1 RBAC Admin UI does not include assignment management unless explicitly scoped.
- Alternatives considered:
  - Full assignment UI in v1: increases risk and complexity.
- Reasoning and evidence:
  - Keeps the first release focused on roles and grants.
- Architectural implications:
  - Components affected: assignment tables and authorization queries (if effective dating is enabled).
- Auditor-facing explanation:
  - Inspect: assignment creation/revocation events (if enabled) are auditable.
- Migration & backward-compatibility notes:
  - safe_to_defer: true (UI scope can be revisited without breaking existing RBAC semantics).
- Governance & owner recommendations:
  - Add a dedicated admin workflow when enabled (four-eyes optional).

### DECISION-INVENTORY-008 — Tenant scoping (no location-scoped RBAC in v1)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: RBAC state is tenant-scoped; location-scoped grants/ABAC are deferred.
- Alternatives considered:
  - Location-scoped RBAC in v1: requires explicit policy language and consistent enforcement.
- Reasoning and evidence:
  - Tenant isolation is non-negotiable; location scoping needs dedicated design.
- Architectural implications:
  - Components affected: all RBAC queries must include tenant context.
  - Security: tenant context must come from trusted claims.
- Auditor-facing explanation:
  - Inspect: no cross-tenant role/grant/assignment reads.
- Migration & backward-compatibility notes:
  - safe_to_defer: true (location scoping can be added later if policy is clear).
- Governance & owner recommendations:
  - Any future ABAC work requires an ADR and cross-domain signoff.

### DECISION-INVENTORY-009 — Provisioning identity key (IdP subject) and email match

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: `idpSubject` is the provisioning identity key; provisioning requires submitted email to match the selected Person email (case-insensitive).
- Alternatives considered:
  - Email as identity key: changes over time; risky.
  - Allow email mismatch: increases chance of linking wrong person.
- Reasoning and evidence:
  - IdP subject is stable; email match prevents common admin mistakes.
- Architectural implications:
  - Components affected: provisioning endpoint validation; user uniqueness constraints.
  - Error code: `EMAIL_MISMATCH` as `409` conflict.
- Auditor-facing explanation:
  - Inspect: provisioning events include `personId`, `userId`, and `correlationId`.
- Migration & backward-compatibility notes:
  - For existing users created without strict checks, allow “grandfathered” records but enforce on new provisioning.
- Governance & owner recommendations:
  - Provisioning requires elevated privilege; consider break-glass logging.

### DECISION-INVENTORY-010 — Provisioning initial roles (optional; roleId identifiers)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Provisioning may include zero or more initial roles; role references use `roleId`.
- Alternatives considered:
  - Require at least one role: forces a default policy decision.
  - Use roleName in API: brittle and ambiguous.
- Reasoning and evidence:
  - Supports onboarding without assuming default access.
- Architectural implications:
  - Components affected: provisioning endpoint must validate roleIds exist.
- Auditor-facing explanation:
  - Inspect: provisioning audit entry records which roles (if any) were assigned.
- Migration & backward-compatibility notes:
  - None; safe additive behavior.
- Governance & owner recommendations:
  - Encourage minimal starting roles.

### DECISION-INVENTORY-011 — Provisioning linking via outbox event; link-status visibility

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Security publishes a provisioning event via transactional outbox; admins can query a `linkStatus` field to see whether `people` linking completed.
- Alternatives considered:
  - Synchronous linking: couples services; brittle.
  - No status visibility: creates admin uncertainty.
- Reasoning and evidence:
  - Event-driven linking is resilient; explicit status supports ops.
- Architectural implications:
  - Components affected: outbox; people consumer; security user read model includes link status.
- Auditor-facing explanation:
  - Inspect: outbox record + downstream link creation audit artifacts.
- Migration & backward-compatibility notes:
  - If link status cannot be computed initially, start with `PENDING|LINKED` only.
- Governance & owner recommendations:
  - Maintain a runbook for stuck `PENDING` users.

### DECISION-INVENTORY-012 — Audit ownership split (RBAC vs financial exceptions)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: `security` owns RBAC/provisioning audit entries; financial exception audit is owned by an `audit` domain read model.
- Alternatives considered:
  - Put all audit in security: centralizes but violates domain boundaries.
  - Each domain implements its own UI exports: duplicates effort.
- Reasoning and evidence:
  - A dedicated audit read model is the clean cross-domain integration point.
- Architectural implications:
  - Components affected: audit service storage and query; security permission gating.
- Auditor-facing explanation:
  - Inspect: a single audit query endpoint can filter by event types and references.
- Migration & backward-compatibility notes:
  - safe_to_defer: true (if audit service not yet present, implement security-owned RBAC audit first).
- Governance & owner recommendations:
  - Audit domain should have retention and export policies reviewed by compliance.

### DECISION-INVENTORY-013 — Audit payload redaction and permission gating

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Audit APIs return curated fields by default; any raw payload is gated behind an explicit permission and must be redacted.
- Alternatives considered:
  - Return raw payload to all auditors: high leakage risk.
- Reasoning and evidence:
  - PII and secrets can appear in payloads; least exposure is required.
- Architectural implications:
  - Components affected: audit storage schema, serialization, and response shaping.
  - Provide `detailsSummary` and `redactionReason` fields.
- Auditor-facing explanation:
  - Inspect: redaction policy is documented and enforced consistently.
- Migration & backward-compatibility notes:
  - Start with “no raw payload” and add gated payload later.
- Governance & owner recommendations:
  - Require periodic redaction policy review.

### DECISION-INVENTORY-014 — Approvals are not security-owned (security only gates)

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: Approval workflows belong to a workflow domain (default `workexec`). Security provides permission keys and enforcement but does not own approval state transitions.
- Alternatives considered:
  - Put approvals in security: turns security into workflow engine.
- Reasoning and evidence:
  - Keeps security focused on access control and identity.
- Architectural implications:
  - Components affected: approval APIs live outside security; security integrates via permissions.
- Auditor-facing explanation:
  - Inspect: approval actions are audited by the owning domain (and/or audit domain).
- Migration & backward-compatibility notes:
  - Stories must be relabeled to the owning domain.
- Governance & owner recommendations:
  - Require an ADR if ownership changes.

### DECISION-INVENTORY-015 — Canonical REST error envelope and status codes

- Normative source: `AGENT_GUIDE.md` (Decision ID)
- Decision: All Security APIs return a canonical error envelope with stable `code` values and include `correlationId`.
- Alternatives considered:
  - Free-form messages: hard for UI to handle consistently.
- Reasoning and evidence:
  - Improves UI behavior and reduces support time.
- Architectural implications:
  - Components affected: global exception handler, validation layer.
  - Required fields: `code`, `message`, `correlationId`, optional `fieldErrors`.
- Auditor-facing explanation:
  - Inspect: error responses and logs share correlationId.
- Migration & backward-compatibility notes:
  - Add envelope in a backwards-compatible manner (keep HTTP status semantics stable).
- Governance & owner recommendations:
  - Maintain a registry of error `code` values.

## End

End of document.
