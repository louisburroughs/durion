====================================================================================================
MAIN PROMPT INSTRUCTIONS
====================================================================================================
---
name: 'Frontend Story Creation Prompt for Moqui (Greenfield Authoring)'
agent: 'Story Authoring Agent'
description: 'Create a new frontend GitHub user story that is implementation-ready for the Moqui framework, using domain-enriched structure, label intelligence, and strict completeness rules so the story can be built without follow-up clarification.'
model: GPT-5 mini (copilot)
---

# STORY CREATION PROMPT
## POS Frontend Story Authoring – Moqui-Buildable Mode (With Label Intelligence & Conflict Detection)

You are authoring a **NEW frontend GitHub user story** for the POS system.

You are NOT rewriting an existing story.
You ARE transforming provided inputs (story description, instructions, business rules) into a **complete, buildable frontend story** suitable for implementation using the **Moqui framework (screens, forms, services, transitions)**.

You are expected to enforce **clarity, precision, completeness, and correct routing metadata**.

---

## ⚠️ CRITICAL REQUIREMENT: Buildability Over Brevity

Your output must be complete enough that:
- A Moqui developer can implement screens, services, validations, and transitions
- A tester can write test cases directly from Acceptance Criteria
- No core behavior is left implied or “assumed”

If something cannot be determined:
- Surface it explicitly as an **Open Question**
- Apply appropriate **blocking labels**
- Do NOT silently guess

---

## 1. Authoritative References (Treat as Truth)

The README at: https://github.com/louisburroughs/durion-moqui-frontend will describe the project and its conventions.

You MUST follow and be consistent with (I you don't have the project in context, look for the information in the prompt before failing):

- `/agents/story-authoring-agent.md`
- `/agents/domains/*.md`
- `/agents/assumptions/safe-defaults.md`
- Moqui framework conventions (screens, forms, transitions, services, entities)

Domain agent contracts are **binding constraints**:
- Where enrichment is allowed → enrich confidently
- Where clarification is required → surface Open Questions and STOP if needed

---
## SAFE DEFAULTS GATE (STRICT)

SAFE_DEFAULTS_MODE: ON_STRICT

You MAY apply safe defaults ONLY for:
- UI ergonomics (empty states, pagination, standard filtering)
- Standard error-handling mapping when the backend contract implies it
- Observability boilerplate consistent with workspace defaults

You MUST NOT apply safe defaults for:
- Domain ownership, SoR, lifecycle/state machines
- Any business formula/threshold/policy
- Permission scope, role inheritance, security boundaries
- Event contracts or identifiers
If any denylist item is unclear, create Open Questions and add blocked:clarification.
---

## 2. Label Awareness (Critical)

GitHub labels are **executable constraints**, not decoration.

You MUST reason about labels as part of authoring.

### Canonical label families:
- `type:*`
- `domain:*` (EXACTLY ONE on stories)
- `status:*`
- `blocked:*`
- `clarification:*`
- `risk:*`
- `agent:*`

---

## 3. Label Responsibilities

### 3.1 Detect Required Labels

You MUST determine:

- `type:story` (always required)
- **Exactly one** `domain:*` label based on primary ownership
- Appropriate `status:*` for a newly authored story
- Whether `blocked:*` or `risk:*` labels are required due to uncertainty

---

### 3.2 Safe Label Inference Rules

You ARE ALLOWED to infer labels when:
- The primary domain is clear from core user value
- No financial, legal, tax, or security policy is being guessed

You MUST NOT silently resolve:
- Multi-domain ownership
- Regulatory, accounting, or security ambiguity

Those require **Open Questions** and blocking labels.

---

## 4. 🏷️ Labels (Proposed) — Output Contract (Non-Negotiable)

At the VERY TOP of your output, include:

```markdown
## 🏷️ Labels (Proposed)
````

With the following subsections.

### 4.1 Required Labels

```markdown
### Required
- type:story
- domain:<inferred-domain>
- status:draft
```

---

### 4.2 Recommended Labels

```markdown
### Recommended
- agent:<primary-domain-agent>
- agent:story-authoring
```

---

### 4.3 Blocking / Risk Labels

If applicable:

```markdown
### Blocking / Risk
- blocked:clarification
- blocked:domain-conflict
- risk:incomplete-requirements
```

If none apply:

```markdown
### Blocking / Risk
- none
```

---

## 5. Rewrite Variant Selection (MANDATORY)

⚠️ **YOU MUST SPECIFY A REWRITE VARIANT – THIS IS NON-NEGOTIABLE** ⚠️

Select the variant based on the inferred primary domain:

| Domain            | Variant                  |
| ----------------- | ------------------------ |
| domain:accounting | accounting-strict        |
| domain:pricing    | pricing-strict           |
| domain:security   | security-strict          |
| domain:inventory  | inventory-flexible       |
| domain:workexec   | workexec-structured      |
| domain:crm        | crm-pragmatic            |
| domain:positivity | integration-conservative |
| domain:billing    | accounting-strict        |
| domain:audit      | security-strict          |
| domain:people     | crm-pragmatic            |
| domain:location   | inventory-flexible       |

### 5.1 Placement Requirement

Immediately after **Labels (Proposed)**, include:

```markdown
**Rewrite Variant:** <variant-name>
```

### 5.2 If Domain Is Unclear

* Select `integration-conservative`
* Add `blocked:domain-conflict`
* Still MUST specify a variant

---

## 6. Multi-Domain Conflict Detection (Non-Negotiable)

A story MUST have **exactly one** `domain:*` label.

If conflict signals apply:

* STOP and follow the domain-conflict procedure
* Add a **Domain Conflict Summary**
* Apply `blocked:domain-conflict` and `status:needs-review`

Conflict signals include:

* Two systems of record
* Two competing state machines
* Ambiguous ownership of calculations or policies
* Acceptance criteria requiring multiple domains simultaneously

---

## 7. Mandatory Story Structure (Exact Order)

⚠️ **CRITICAL**: Sections must appear in EXACT order.

1. **Story Header**

   * Title
   * Primary Persona
   * Business Value

2. **Story Intent**

   * As a / I want / So that
   * In-scope and out-of-scope

3. **Actors & Stakeholders**

4. **Preconditions & Dependencies**

5. **UX Summary (Moqui-Oriented)**

   * Entry points
   * Screens to create/modify
   * Navigation context
   * User workflows (happy + alternate paths)

6. **Functional Behavior**

   * Triggers
   * UI actions
   * State changes
   * Service interactions

7. **Business Rules (Translated to UI Behavior)**

   * Validation
   * Enable/disable rules
   * Visibility rules
   * Error messaging expectations

8. **Data Requirements**

   * Entities involved
   * Fields (type, required, defaults)
   * Read-only vs editable by state/role
   * Derived/calculated fields

9. **Service Contracts (Frontend Perspective)**

   * Load/view calls
   * Create/update calls
   * Submit/transition calls
   * Error handling expectations

10. **State Model & Transitions**

    * Allowed states
    * Role-based transitions
    * UI behavior per state

11. **Alternate / Error Flows**

    * Validation failures
    * Concurrency conflicts
    * Unauthorized access
    * Empty states

12. **Acceptance Criteria**

    * Gherkin (Given / When / Then)
    * At least one scenario per major flow
    * Success and failure cases

13. **Audit & Observability**

    * User-visible audit data
    * Status history
    * Traceability expectations

14. **Non-Functional UI Requirements**

    * Performance
    * Accessibility
    * Responsiveness
    * i18n/timezone/currency (if applicable)

15. **Applied Safe Defaults**
   * REQUIRED even if empty
   * List each default applied with:
     - Default ID (from safe-defaults allowlist)
     - What was assumed
     - Why it qualifies as safe (1 sentence)
     - Impacted sections (UX, Service Contracts, Error Flows, etc.)
   * If none: write `- none`

16. **Open Questions**

    * Only if needed
    * Explicit, blocking questions

---

## 8. Domain-Enriched Writing Rules

* Describe **behavior**, not visual layout
* Be explicit about data ownership and validation
* Assume standard POS and Moqui patterns unless contradicted
* Prefer determinism over flexibility

---

## 9. Open Questions & Blocking Rules

Create **Open Questions** when:

* Multiple valid behaviors exist
* Domain policy is unclear
* Backend contract is undefined

If Open Questions exist:

* Add `blocked:clarification`
* Add at the VERY TOP:

```
STOP: Clarification required before finalization
```

Still produce the best possible structured story.

---

## 10. Provided Inputs

Below this prompt, the user will provide:

```markdown
# Provided Inputs
- Story description
- Story writing instructions
- Business rules
- Any constraints or references
```

You MUST base the story ONLY on these inputs plus authoritative references.

---

## 11. Tone & Perspective

Write as:

* A senior product, domain, and Moqui architect
* Preparing work for professional developers and testers
* Optimizing for implementation without guesswork

Be explicit.
Be structured.
Be unambiguous.

---

## 12.  Project References

Project references for context can be found in the README.md of the following public repositories:

- Durion Project - https://github.com/louisburroughs/durion.git
- Durion Frontend - https://github.com/louisburroughs/durion-moqui-frontend.git
- Durion Backend - https://github.com/louisburroughs/durion-positivity-backend.git

## 🔴 FINAL CHECKLIST – Before Submitting

* [ ] Labels (Proposed) included at top
* [ ] **Rewrite Variant specified immediately after labels**
* [ ] Exactly one `domain:*` label
* [ ] All mandatory sections present in exact order
* [ ] UI behavior mapped to services and data
* [ ] Acceptance criteria in Given/When/Then
* [ ] Open Questions listed if anything is unclear

**If forced to choose:**

> **Buildability and correct routing beats elegance.**

====================================================================================================
BUSINESS RULES FOR DOMAIN: security
====================================================================================================

--- AGENT_GUIDE.md ---
# AGENT_GUIDE.md

## Summary

This guide defines the normative, implementation-ready business rules for the `security` domain: RBAC (roles, permissions, assignments), authorization decisions, and user provisioning orchestration. It resolves the prior “CLARIFY/TODO” items into explicit decisions so frontend and backend teams can implement admin screens and APIs without guessing. A non-normative rationale companion exists in `DOMAIN_NOTES.md`.

## Completed items

- [x] Generated Decision Index
- [x] Mapped Decision IDs to `DOMAIN_NOTES.md`
- [x] Reconciled todos from original AGENT_GUIDE

## Decision Index

| Decision ID | Title |
| --- | --- |
| DECISION-INVENTORY-001 | Deny-by-default authorization |
| DECISION-INVENTORY-002 | Permission key format and immutability |
| DECISION-INVENTORY-003 | Role name normalization and uniqueness |
| DECISION-INVENTORY-004 | Role name immutability (rename-by-recreate) |
| DECISION-INVENTORY-005 | RBAC mutation semantics (grant/revoke + replace-set) |
| DECISION-INVENTORY-006 | Permission registry is code-first (UI read-only) |
| DECISION-INVENTORY-007 | Principal-role assignments (model supports effective dating; UI deferred) |
| DECISION-INVENTORY-008 | Tenant scoping (no location-scoped RBAC in v1) |
| DECISION-INVENTORY-009 | Provisioning identity key (IdP subject) and email match policy |
| DECISION-INVENTORY-010 | Provisioning initial roles (optional; roleId identifiers) |
| DECISION-INVENTORY-011 | Provisioning linking via outbox event; link-status visibility |
| DECISION-INVENTORY-012 | Audit ownership split (RBAC audit vs financial exception audit) |
| DECISION-INVENTORY-013 | Audit payload redaction and permissions |
| DECISION-INVENTORY-014 | Approvals are not security-owned (security only gates access) |
| DECISION-INVENTORY-015 | Canonical REST error envelope and status codes |

## Domain Boundaries

### What `security` owns (system of record)

- RBAC administration primitives:
  - Roles (create/update; delete is optional and off by default)
  - Role ↔ Permission grants
  - Permission registry (query + validation; permissions are registered by services)
- Authorization decisioning semantics (deny-by-default; deterministic evaluation)
- User provisioning orchestration:
  - Create/resolve a Security `User`
  - Publish a provisioning event (transactional outbox)
- RBAC/provisioning audit events and query API (append-only)

### What `security` does *not* own

- `Person` and `UserPersonLink` (owned by `people`)
- Authentication source of truth (external IdP)
- Approval workflows and state machines (owned by `workexec` or another workflow domain) (**Decision DECISION-INVENTORY-014**)
- Financial exception business rules (price overrides/refunds/cancellations) and their authoritative audit events (owned by the relevant business domains and exposed via the `audit` domain read model) (**Decision DECISION-INVENTORY-012**)

## Key Entities / Concepts

| Entity | Description |
| --- | --- |
| User | Security-owned user identity record, keyed by IdP subject; links to Person asynchronously. |
| Role | Named bundle of permissions (grant set). |
| Permission | Code-first registry entry identified by immutable `permissionKey`. |
| RolePermission | Grant mapping `(roleId, permissionKey)` (unique). |
| PrincipalRole | Assignment mapping `(principalId, roleId)` with optional effective dating (model). |
| AuditEntry | Append-only record for security-owned mutations; financial exception audit entries are owned by `audit`. |

## Invariants / Business Rules

### Authorization / RBAC

- Deny-by-default: absence of a grant results in `DENY`. (**Decision DECISION-INVENTORY-001**)
- Permission keys are immutable and validated as `domain:resource:action` using snake_case. (**Decision DECISION-INVENTORY-002**)
- Roles are referenced by immutable `roleId` in APIs; `roleName` is display-only. (**Decision DECISION-INVENTORY-004**)
- Role names are normalized (trim + collapse whitespace + lowercase) for uniqueness checks. (**Decision DECISION-INVENTORY-003**)
- Role permission updates support:
  - Idempotent incremental grant/revoke, and
  - Optional replace-set semantics for “set exactly these permissions”. (**Decision DECISION-INVENTORY-005**)

### Provisioning

- Provisioning creates/resolves a Security `User` idempotently using IdP subject as the identity key. (**Decision DECISION-INVENTORY-009**)
- Provisioning is asynchronous with respect to `people` linking:
  - Provisioning returns success after writing the user + outbox record.
  - `people` consumes the event and creates `UserPersonLink` idempotently.
  - Provisioning must not block on linking. (**Decision DECISION-INVENTORY-011**)
- Initial roles are optional (zero roles allowed) and referenced by `roleId`. (**Decision DECISION-INVENTORY-010**)

### Audit

- Audit entries are append-only (no edit/delete).
- RBAC/provisioning audit is served by `security`; financial exception audit is served by `audit`, with access gated by security permissions. (**Decision DECISION-INVENTORY-012**)
- Raw payload visibility is restricted; default responses return redacted/curated fields only. (**Decision DECISION-INVENTORY-013**)

## Mapping: Decisions → Notes

| Decision ID | One-line summary | Link to notes |
| --- | --- | --- |
| DECISION-INVENTORY-001 | Missing grant implies deny | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-001--deny-by-default-authorization) |
| DECISION-INVENTORY-002 | `domain:resource:action` snake_case keys are immutable | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-002--permission-key-format-and-immutability) |
| DECISION-INVENTORY-003 | Role names are normalized and unique (case-insensitive) | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-003--role-name-normalization-and-uniqueness) |
| DECISION-INVENTORY-004 | Role rename requires recreate/migrate | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-004--role-name-immutability-rename-by-recreate) |
| DECISION-INVENTORY-005 | Idempotent grant/revoke + optional replace-set | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-005--rbac-mutation-semantics-grantrevoke--replace-set) |
| DECISION-INVENTORY-006 | Permission registry is code-first; UI read-only | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-006--permission-registry-is-code-first-ui-read-only) |
| DECISION-INVENTORY-007 | Assignment model supports effective dating; UI deferred | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-007--principal-role-assignments-effective-dating-ui-deferred) |
| DECISION-INVENTORY-008 | Tenant-scoped RBAC; no location-scoped RBAC in v1 | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-008--tenant-scoping-no-location-scoped-rbac-in-v1) |
| DECISION-INVENTORY-009 | Provisioning identity key is IdP subject; email must match Person | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-009--provisioning-identity-key-idp-subject-and-email-match) |
| DECISION-INVENTORY-010 | Initial roles optional; use roleId | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-010--provisioning-initial-roles-optional-roleid-identifiers) |
| DECISION-INVENTORY-011 | Outbox event + link-status visibility | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-011--provisioning-linking-via-outbox-event-link-status-visibility) |
| DECISION-INVENTORY-012 | RBAC audit in security; financial exceptions in audit domain | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-012--audit-ownership-split-rbac-vs-financial-exceptions) |
| DECISION-INVENTORY-013 | Raw payload redaction; gated full payload | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-013--audit-payload-redaction-and-permission-gating) |
| DECISION-INVENTORY-014 | Approvals are workflow-owned, not security-owned | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-014--approvals-are-not-security-owned-security-only-gates) |
| DECISION-INVENTORY-015 | Canonical error envelope + status semantics | [DOMAIN_NOTES.md](DOMAIN_NOTES.md#decision-inventory-015--canonical-rest-error-envelope-and-status-codes) |

## Open Questions (from source)

### Q: Backend endpoints & schemas — what are the concrete REST/Moqui contracts and error shapes?

- Answer: Security APIs are hosted by `pos-security-service` and use a versioned REST base path: `/api/v1/security/*`. Pagination uses `pageIndex/pageSize/totalCount`, and all errors use the canonical envelope defined in **Decision DECISION-INVENTORY-015**.
- Assumptions:
  - REST endpoints are accessible via Moqui as needed (via gateway or direct service bridge).
  - IDs are UUIDs unless explicitly documented otherwise.
- Rationale:
  - Stable contracts prevent frontend “guessing” and reduce cross-domain coupling.
- Impact:
  - Backend must document and implement endpoint schemas; frontend uses only documented fields.
- Decision ID: DECISION-INVENTORY-015

### Q: Identifiers — are roles referenced by `roleId` or `roleName` in APIs?

- Answer: Roles are referenced by immutable `roleId` (UUID). `roleName` is display-only and immutable (rename requires recreate). Permissions are referenced by immutable `permissionKey`.
- Assumptions:
  - Existing systems can store `roleId` references for assignments.
- Rationale:
  - Stable identifiers avoid broken deep links and audit ambiguity.
- Impact:
  - UI uses `roleId` for mutations; backend rejects role-name-based mutation contracts.
- Decision ID: DECISION-INVENTORY-004

### Q: Error shape — what is the canonical error response format?

- Answer: All error responses return a stable JSON envelope with: `code`, `message`, `correlationId`, optional `fieldErrors[]`, and optional `details`.
- Assumptions:
  - `correlationId` is generated if absent and returned in all responses.
- Rationale:
  - Frontend can reliably map errors to UI states and support can correlate logs.
- Impact:
  - Backend aligns controllers/exception mappers; tests assert presence of `code` and `correlationId`.
- Decision ID: DECISION-INVENTORY-015

### Q: Authorization scope — which permission keys gate RBAC admin, provisioning, and audit?

- Answer: Security defines and owns the permission keys used to gate these screens and endpoints. Minimum keys:
  - RBAC view: `security:role:view`, `security:permission:view`
  - Role CRUD: `security:role:create`, `security:role:update` (delete optional: `security:role:delete`)
  - Grants: `security:role_permission:grant`, `security:role_permission:revoke` (or `security:role_permission:replace`)
  - Provisioning: `security:user:provision`
  - Security audit view/export: `security:audit_entry:view`, `security:audit_entry:export`
- Assumptions:
  - Permission keys are registered on service startup and are immutable.
- Rationale:
  - Removes a blocking dependency (“don’t invent permission names”) by making Security the authoritative source.
- Impact:
  - Permission registry seeded; UI uses these keys for route gating and backend enforcement.
- Decision ID: DECISION-INVENTORY-002

### Q: Role name mutability — is `roleName` editable?

- Answer: `roleName` is immutable; only `description` (and other non-identifier metadata) may be edited. Renaming a role is done by creating a new role and migrating assignments/grants.
- Assumptions:
  - Existing role references are stored by `roleId`.
- Rationale:
  - Avoids audit/history ambiguity and broken references.
- Impact:
  - UI disables editing roleName; backend rejects roleName updates.
- Decision ID: DECISION-INVENTORY-004

### Q: Role uniqueness & casing — what normalization rules apply?

- Answer: Uniqueness is enforced on a normalized name computed as: trim leading/trailing whitespace, collapse internal whitespace runs to a single space, then lowercase.
- Assumptions:
  - UI may still show original casing as entered.
- Rationale:
  - Prevents confusing duplicates while preserving human-friendly display.
- Impact:
  - Database unique index on `roleNameNormalized` (or equivalent); create role returns `409 ROLE_NAME_TAKEN`.
- Decision ID: DECISION-INVENTORY-003

### Q: Multi-tenant/location scoping — are roles/assignments scoped by tenant/location?

- Answer: RBAC is tenant-scoped (roles, grants, and assignments belong to a tenant). Location-scoped grants/overrides are out-of-scope for v1.
- Assumptions:
  - Tenant context is derived from trusted auth claims or request context, not user input.
- Rationale:
  - Tenant isolation is mandatory; location-scoped ABAC requires more explicit policy design.
- Impact:
  - API queries/mutations require tenant context; add indexes by tenant.
- Decision ID: DECISION-INVENTORY-008

### Q: Location overrides / ABAC — is there any requirement for location-scoped permissions?

- Answer: No, not in v1. Any mention of “location overrides” is deferred until a separate story defines ABAC requirements, policy language, and enforcement model.
- Assumptions:
  - All role permissions apply globally within a tenant.
- Rationale:
  - Prevents accidental policy sprawl and inconsistent enforcement.
- Impact:
  - UI does not expose per-location overrides; backend does not accept location scope on grants.
- Decision ID: DECISION-INVENTORY-008

### Q: Role assignment UI — is PrincipalRole/UserRole assignment in scope?

- Answer: The data model supports assignments (including effective dating), but the RBAC Admin UI does not include principal-role assignment in v1 unless a story explicitly requires it.
- Assumptions:
  - Assignments can be managed via internal/admin tooling or a later UI iteration.
- Rationale:
  - Keeps initial admin UI focused and reduces permission management complexity.
- Impact:
  - If later enabled, add endpoints under `/api/v1/security/principals/*/roles` gated by `security:principal_role:*` permissions.
- Decision ID: DECISION-INVENTORY-007

### Q: Provision User — which permission gates provisioning screen and submit?

- Answer: Provisioning is gated by `security:user:provision` for submit. The screen may also be gated by `security:user:provision` (single permission covers view+submit for v1).
- Assumptions:
  - If finer-grained control is needed later, split into `security:user:provision:view` and `security:user:provision:execute`.
- Rationale:
  - Simple least-privilege for a high-risk operation.
- Impact:
  - Backend checks permission; UI hides route and blocks submit on 401/403.
- Decision ID: DECISION-INVENTORY-002

### Q: Provision User — what are the endpoint contracts for person search, role list, and provision request/response?

- Answer: Person search is served by `people` (read-only), role list by `security`, and provisioning by `security`:
  - `GET /api/v1/people/persons?search=...` (returns `personId`, name, email)
  - `GET /api/v1/security/roles` (returns `roleId`, `roleName`, `description`)
  - `POST /api/v1/security/users/provision`
- Assumptions:
  - Gateway routes exist to reach each service.
- Rationale:
  - Preserves domain ownership while enabling orchestration.
- Impact:
  - Frontend uses these endpoints and does not infer hidden fields.
- Decision ID: DECISION-INVENTORY-011

### Q: Identity rules — is `username` required or derived, and must email match Person?

- Answer: `idpSubject` is the identity key. `username` is derived from Person email (local-part) unless explicitly provided. Provisioning requires the submitted email to match the selected Person email (case-insensitive) to prevent mismatched identity linking.
- Assumptions:
  - Person records have a canonical primary email.
- Rationale:
  - Minimizes the risk of provisioning a user tied to the wrong Person.
- Impact:
  - Backend validates email match; conflicts return `409 EMAIL_MISMATCH`.
- Decision ID: DECISION-INVENTORY-009

### Q: User status enumeration — what statuses exist and default?

- Answer: Security User status values are `ACTIVE` and `DISABLED`. Default on provisioning is `ACTIVE`.
- Assumptions:
  - Downstream systems use `DISABLED` to block access if needed.
- Rationale:
  - Keeps status model simple and predictable.
- Impact:
  - UI renders explicit statuses; backend rejects unknown status values.
- Decision ID: DECISION-INVENTORY-009

### Q: Initial role assignment — are zero roles allowed and how are roles identified?

- Answer: Zero roles are allowed at provision time. If roles are provided, they are identified by `roleId`.
- Assumptions:
  - Default access is minimal until roles are assigned.
- Rationale:
  - Supports phased onboarding and avoids forcing a “default role” policy.
- Impact:
  - Backend validates roleIds exist; UI allows empty selection.
- Decision ID: DECISION-INVENTORY-010

### Q: Link status visibility — is there an API to query link status?

- Answer: Yes. Security exposes `GET /api/v1/security/users/{userId}` returning `linkStatus` = `PENDING|LINKED|FAILED` plus timestamps. UI should provide a manual “Refresh status” action; no automatic polling in v1.
- Assumptions:
  - `people` emits a “link created” event or `security` can query `people` by userId.
- Rationale:
  - Avoids polling load while still giving admins confidence in eventual consistency.
- Impact:
  - Add linkStatus fields to response; add runbook for `FAILED`.
- Decision ID: DECISION-INVENTORY-011

### Q: Approvals — domain ownership and contract?

- Answer: Approvals are workflow-owned (default owner: `workexec`). Security does not own approval state or endpoints, but does define/host permission keys used to gate approval actions.
- Assumptions:
  - Existing approval endpoints live in a workflow service.
- Rationale:
  - Prevents Security from becoming a workflow engine.
- Impact:
  - Stories labeled `domain:security` for approvals must be relabeled to the owning domain.
- Decision ID: DECISION-INVENTORY-014

### Q: Audit Trail (financial exceptions) — ownership, export, references, and sensitivity?

- Answer: Financial exception audit data is owned by `audit` as a read model fed by domain events. Security enforces access via permissions. Export is asynchronous and produces CSV by default.
- Assumptions:
  - The platform has (or will have) a dedicated `audit` service/read model.
- Rationale:
  - Consolidates cross-domain audit queries without violating domain write ownership.
- Impact:
  - Implement `/api/v1/audit/exceptions/*` endpoints in audit domain; security implements view/export permissions.
- Decision ID: DECISION-INVENTORY-012

## Todos Reconciled

- Original todo: "publish canonical error schema for Moqui/REST consumers" → Resolution: Resolved in this guide via **Decision DECISION-INVENTORY-015**.
- Original todo: "CLARIFY permission keys gate access" → Resolution: Resolved by defining Security-owned permission keys (**Decision DECISION-INVENTORY-002**).
- Original todo: "CLARIFY replace-set vs grant/revoke" → Resolution: Support both semantics; grant/revoke required, replace-set optional (**Decision DECISION-INVENTORY-005**).

## End

End of document.


--- DOMAIN_NOTES.md ---
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


--- STORY_VALIDATION_CHECKLIST.md ---
# STORY_VALIDATION_CHECKLIST.md

## Summary

This checklist validates implementations of Security domain stories (RBAC admin, provisioning orchestration, and security-owned audit) against the normative decisions in `AGENT_GUIDE.md`. It also includes testable acceptance criteria for each previously-open question so reviewers can verify contracts, permissions, idempotency, and data handling.

## Completed items

- [x] Updated acceptance criteria for each resolved open question

## Scope / Ownership

- [ ] Confirm story labeled `domain:security` (and not mislabeled `domain:workexec` / `domain:audit` when appropriate).
- [ ] Verify primary actor(s) and permissions are explicit for each UI screen and API.
- [ ] Confirm entity ownership:
  - [ ] Security: `Role`, `Permission` (registry), `User` (security identity record), RBAC/provisioning audit.
  - [ ] People: `Person`, `UserPersonLink`.
  - [ ] Audit: financial exception audit read model.
  - [ ] Workexec: approvals (workflow).

## Data Model & Validation

- [ ] Enforce uniqueness constraints:
  - [ ] `permissionKey` is unique and immutable.
  - [ ] `roleNameNormalized` is unique per tenant.
  - [ ] `(roleId, permissionKey)` unique.
- [ ] Enforce role name normalization: trim + collapse whitespace + case-insensitive uniqueness.
- [ ] Reject roleName updates after creation; allow description updates.
- [ ] Provisioning input validation:
  - [ ] `personId` exists.
  - [ ] Email matches selected Person email (case-insensitive).
  - [ ] `idpSubject` is present and stable.
  - [ ] `status` is `ACTIVE|DISABLED` (default `ACTIVE`).
  - [ ] If roles provided, `roleIds[]` exist.

## API Contract

- [ ] Security endpoints are versioned under `/api/v1/security/*` and document request/response schemas.
- [ ] List endpoints support pagination: `pageIndex`, `pageSize`, `totalCount`.
- [ ] Error responses use the canonical envelope: `code`, `message`, `correlationId`, optional `fieldErrors[]`.

## Events & Idempotency

- [ ] Provisioning is idempotent on `idpSubject`.
- [ ] Provisioning publishes an outbox-backed event; API returns without waiting for `people`.
- [ ] RBAC grant/revoke endpoints are idempotent and safe under retry.

## Security

- [ ] Authorization is deny-by-default.
- [ ] Admin actions are gated by Security-owned permission keys (documented in `AGENT_GUIDE.md`).
- [ ] Audit APIs return curated fields by default; any raw payload is gated and redacted.

## Observability

- [ ] Every mutation and provisioning request logs/returns `correlationId`.
- [ ] Security emits audit entries for RBAC/provisioning changes.
- [ ] Outbox/consumer failures route to DLQ with alerting.

## Acceptance Criteria (per resolved question)

### Q: Backend endpoints & schemas — what are the concrete REST/Moqui contracts and error shapes?

- Acceptance: Contracts exist for RBAC, provisioning, and security audit under `/api/v1/security/*`, with documented request/response fields and pagination.
- Test Fixtures:
  - Tenant `t1`, admin principal with `security:role:view`.
- Example API request/response:

```http
GET /api/v1/security/roles?pageIndex=0&pageSize=25
```

```json
{
  "items": [{"roleId": "...", "roleName": "Price Manager", "description": "..."}],
  "pageIndex": 0,
  "pageSize": 25,
  "totalCount": 1
}
```

### Q: Identifiers — are roles referenced by `roleId` or `roleName` in APIs?

- Acceptance: All role mutation endpoints accept `roleId` and reject mutation-by-roleName.
- Test Fixtures:
  - Existing role: `roleId=r1`, `roleName=Manager`.
- Example API request/response:

```http
PUT /api/v1/security/roles/r1
Content-Type: application/json

{"roleName": "New Name", "description": "x"}
```

```json
{"code": "ROLE_NAME_IMMUTABLE", "message": "roleName cannot be changed", "correlationId": "..."}
```

### Q: Error shape — what is the canonical error response format?

- Acceptance: Every non-2xx response returns `code`, `message`, and `correlationId`.
- Test Fixtures:
  - Submit invalid payload to any endpoint.
- Example API request/response:

```http
POST /api/v1/security/roles
Content-Type: application/json

{"roleName": ""}
```

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "correlationId": "...",
  "fieldErrors": [{"field": "roleName", "message": "required"}]
}
```

### Q: Authorization scope — which permission keys gate RBAC admin, provisioning, and audit?

- Acceptance: Backend checks and enforces the documented permissions, returning `403` for insufficient grants.
- Test Fixtures:
  - User A: no grants.
  - User B: has `security:role:create`.
- Example API request/response:

```http
POST /api/v1/security/roles
```

```json
{"code": "FORBIDDEN", "message": "Access denied", "correlationId": "..."}
```

### Q: Role name mutability — is `roleName` editable?

- Acceptance: `roleName` cannot be changed after create; only `description` can be updated.
- Test Fixtures:
  - Existing role `roleId=r1`.
- Example API request/response:

```http
PUT /api/v1/security/roles/r1
Content-Type: application/json

{"description": "Updated"}
```

```json
{"roleId": "r1", "roleName": "Manager", "description": "Updated"}
```

### Q: Role uniqueness & casing — what normalization rules apply?

- Acceptance: Creating `" Manager "` when `"manager"` exists returns `409 ROLE_NAME_TAKEN`.
- Test Fixtures:
  - Existing roleName `manager`.
- Example API request/response:

```http
POST /api/v1/security/roles
Content-Type: application/json

{"roleName": "  MANAGER  "}
```

```json
{"code": "ROLE_NAME_TAKEN", "message": "Role name already exists", "correlationId": "..."}
```

### Q: Multi-tenant/location scoping — are roles/assignments scoped by tenant/location?

- Acceptance: Requests are scoped to the caller’s tenant context and cannot read or mutate another tenant’s roles.
- Test Fixtures:
  - Tenant `t1` role `r1`, tenant `t2` role `r2`.
- Example API request/response:

```http
GET /api/v1/security/roles/r2
```

```json
{"code": "NOT_FOUND", "message": "Role not found", "correlationId": "..."}
```

### Q: Location overrides / ABAC — is there any requirement for location-scoped permissions?

- Acceptance: RBAC grant endpoints do not accept location scope fields and document that grants are tenant-global.
- Test Fixtures:
  - Attempt to include `locationId` in grant request.
- Example API request/response:

```json
{"code": "VALIDATION_FAILED", "message": "locationId not supported", "correlationId": "..."}
```

### Q: Role assignment UI — is PrincipalRole/UserRole assignment in scope?

- Acceptance: If the story is v1 RBAC Admin UI, it includes role CRUD and grants only; no principal-role assignment UI routes exist.
- Test Fixtures:
  - Check UI menus/routes; verify no assignment screens.
- Example API request/response:

```json
{"note": "No principal-role assignment UI in v1"}
```

### Q: Provision User — which permission gates provisioning screen and submit?

- Acceptance: Provisioning submit requires `security:user:provision`; unauthorized callers receive `403`.
- Test Fixtures:
  - User without the permission.
- Example API request/response:

```http
POST /api/v1/security/users/provision
```

```json
{"code": "FORBIDDEN", "message": "Access denied", "correlationId": "..."}
```

### Q: Provision User — what are the endpoint contracts for person search, role list, and provision request/response?

- Acceptance: Person search is read-only in `people`; roles list is read-only in `security`; provisioning lives in `security`.
- Test Fixtures:
  - Person search by email fragment.
- Example API request/response:

```http
GET /api/v1/people/persons?search=doe
```

```json
{"items": [{"personId": "p1", "displayName": "John Doe", "email": "john.doe@example.com"}]}
```

### Q: Identity rules — is `username` required or derived, and must email match Person?

- Acceptance: Provisioning rejects mismatched emails with `409 EMAIL_MISMATCH`. Username is derived when not provided.
- Test Fixtures:
  - Person email `john.doe@example.com`, request email `other@example.com`.
- Example API request/response:

```json
{"code": "EMAIL_MISMATCH", "message": "Email must match Person email", "correlationId": "..."}
```

### Q: User status enumeration — what statuses exist and default?

- Acceptance: New provisioned users default to `ACTIVE`; invalid status values return `400 VALIDATION_FAILED`.
- Test Fixtures:
  - Request with `status=UNKNOWN`.
- Example API request/response:

```json
{"code": "VALIDATION_FAILED", "message": "Invalid status", "correlationId": "..."}
```

### Q: Initial role assignment — are zero roles allowed and how are roles identified?

- Acceptance: Provisioning accepts an empty `roleIds` array (or omitted field) and succeeds.
- Test Fixtures:
  - Provision request with no roles.
- Example API request/response:

```json
{"userId": "u1", "resolvedExisting": false, "eventEnqueued": true, "correlationId": "..."}
```

### Q: Link status visibility — is there an API to query link status?

- Acceptance: `GET /api/v1/security/users/{userId}` returns `linkStatus=PENDING|LINKED|FAILED` and UI offers manual refresh.
- Test Fixtures:
  - Immediately after provisioning.
- Example API request/response:

```json
{"userId": "u1", "personId": "p1", "linkStatus": "PENDING"}
```

### Q: Approvals — domain ownership and contract?

- Acceptance: Approval endpoints are not implemented in `pos-security-service`; security stories do not introduce approval state transitions.
- Test Fixtures:
  - Search for routes; verify approvals are routed to `workexec` service.
- Example API request/response:

```json
{"note": "Approvals are owned by workexec; security only gates access."}
```

### Q: Audit Trail (financial exceptions) — ownership, export, references, and sensitivity?

- Acceptance: Financial exception audit listing/detail/export is implemented in the `audit` domain; security gates access and audit APIs default to curated fields.
- Test Fixtures:
  - Audit view permission present, export permission absent.
- Example API request/response:

```json
{"code": "FORBIDDEN", "message": "Export not permitted", "correlationId": "..."}
```

## End

End of document.


====================================================================================================
ISSUE TO BE UPDATED
====================================================================================================
Title: after

STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

---

## 1. Story Header

### Title
[FRONTEND] Security: Roles & Permission Matrix Admin UI (Roles, Grants, Assignments, Audit)

### Primary Persona
Admin (authorized security administrator)

### Business Value
Provide an admin-facing UI to manage RBAC (roles, permissions, role grants, user role assignments) with audit visibility so sensitive POS actions can be controlled with least privilege and changes are traceable.

---

## 2. Story Intent

### As a / I want / So that
- **As an Admin**, I want to create/edit roles, grant permissions to roles, and assign roles to users  
- **So that** sensitive operations (refunds, overrides, voids, adjustments, cancellations) are protected by explicit permission checks and all changes are auditable.

### In-scope
- Moqui frontend screens to:
  - List/view/create roles
  - View permissions registry and grant/revoke permissions on roles
  - Assign/revoke roles for users (principal)
  - View audit log entries related to RBAC changes (read-only)
- Frontend enforcement behaviors:
  - Show/hide/disable protected admin actions based on authorization responses (deny-by-default)
  - Display clear error states for 401/403/validation/conflicts
- Basic filtering/search/pagination for lists (safe UI defaults)

### Out-of-scope
- Defining the canonical permission list itself (owned by security backend/manifest)
- Authentication mechanism (IdP/JWT) implementation details
- Location/tenant scoping policy (unless explicitly provided)
- Role inheritance / hierarchy rules (not specified)
- “Protected operations” enforcement across the entire POS UI (this story focuses on admin RBAC management UI; wiring other features belongs to their stories)

---

## 3. Actors & Stakeholders
- **Admin**: performs RBAC configuration
- **Security Officer / Auditor**: reviews audit trail (read-only)
- **POS Operator**: indirectly impacted by permissions (not a UI actor here)
- **Security backend service (`pos-security-service`)**: system of record for RBAC entities and audit emission
- **People domain / Identity**: provides user/principal lookup and person linkage (integration detail; not owned here)

---

## 4. Preconditions & Dependencies
- Moqui app can call backend services for:
  - Role CRUD
  - Permission registry read
  - Grant/revoke permissions to roles
  - Assign/revoke roles to principals/users
  - Audit log query
- Backend enforces deny-by-default authorization and returns standard HTTP status codes (401/403/400/404/409).
- The permission registry exists (reference mentions `permissions_v1.yml` in backend repo), and an API exists to list permissions (exact endpoint TBD).
- A way to search/select a user/principal exists (endpoint + shape TBD).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Administration → Security → Roles & Permissions**
- Deep links:
  - `/admin/security/roles`
  - `/admin/security/roles/{roleId}`
  - `/admin/security/users` (or `/admin/security/principals`) (TBD)

### Screens to create/modify (Moqui)
Create new screens under a security admin root:
- `component://pos-security/screen/admin/security/SecurityAdmin.xml` (root/menu)
- `.../RoleList.xml`
- `.../RoleDetail.xml`
- `.../UserRoleAssignments.xml`
- `.../AuditLog.xml` (filtered to RBAC-related events)

Each screen uses Moqui screen forms + transitions that call services (or REST via Moqui service facade, depending on project conventions).

### Navigation context
- From Role List → Role Detail
- From Role Detail tabs/sections:
  - Role info
  - Permissions granted to role
  - Users assigned to role (optional; depends on API)
  - Audit events for this role

### User workflows
**Happy path: create role + grant permissions**
1. Admin opens Role List → “Create Role”
2. Enters role name + description → Save
3. On Role Detail, opens “Permissions” section → selects permissions → Grant
4. System confirms; audit events visible in Audit Log screen

**Alternate path: assign role to user**
1. Admin opens User Role Assignments screen
2. Searches user/principal
3. Assigns one or more roles
4. Role takes effect immediately (frontend reflects success; no caching assumptions)

**Alternate path: revoke**
- Revoke permission from role / revoke role from user with confirmation and resulting UI refresh

---

## 6. Functional Behavior

### Triggers
- Admin navigates to security admin screens
- Admin submits create/update/grant/revoke/assign actions
- Admin filters/searches lists

### UI actions
- Role list: search by name, open detail
- Create role: submit form
- Edit role: update description/name (if allowed; TBD)
- Grant permission(s): multi-select grant action
- Revoke permission: per-row revoke action
- Assign role(s) to user: add assignment
- Revoke role from user: remove assignment
- Audit log: filter by event type/date/actor/role (as supported)

### State changes (frontend)
- Local UI state: loading/error/success banners
- No domain state machine is defined; treat entities as active records unless backend indicates statuses.

### Service interactions
- All mutations call backend; on success, refresh the affected lists/details from backend.
- On 401 → route to login/session-expired flow (project convention TBD).
- On 403 → show “Not authorized” and disable mutation controls.
- On 409 → show conflict message (e.g., duplicate role name).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Role name required; enforce basic client-side validation (non-empty, trim). Server remains source of truth.
- Permission grant/revoke requires selected permission(s).
- User selection required before assigning roles.

### Enable/disable rules
- If user lacks authorization to manage RBAC:
  - Entire Security Admin menu hidden OR screen shows 403 state (implementation depends on how authz is exposed to UI; TBD).
- Mutation buttons disabled while request in-flight to avoid double-submit.
- Deny-by-default: if permission check endpoint is unavailable, UI must default to hiding/disabling protected actions and show an error.

### Visibility rules
- Permissions list display includes permission key and description (if provided).
- Audit screen shows only RBAC-relevant event types by default (filter), with ability to broaden if API supports.

### Error messaging expectations
- Validation errors: show field-level messages where possible.
- Authorization errors: “Insufficient permissions to perform this action.”
- Conflicts: “Role name already exists” (if 409 indicates unique constraint).
- Not found: “Role not found or you no longer have access.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
Security domain (SoR backend):
- Role
- Permission
- RolePermission (grant mapping)
- PrincipalRole / UserRole (assignment mapping)
- AuditLog (RBAC events)

### Fields (type, required, defaults)
**Role**
- `roleId` (string/uuid, read-only)
- `roleName` (string, required, unique; casing rules TBD)
- `description` (string, optional)
- `createdAt` (datetime, read-only)
- `createdBy` (string, read-only)

**Permission**
- `permissionKey` (string, read-only; format `domain:resource:action`)
- `description` (string, read-only, optional)
- `domain` (string, read-only, optional if derived)
- `resource` / `action` (string, read-only, optional if derived)

**RolePermission**
- `roleId` (read-only)
- `permissionKey` (read-only)
- `assignedAt` (datetime, read-only)
- `assignedBy` (string, read-only)

**PrincipalRole/UserRole**
- `principalId` or `userId` (string, read-only once selected)
- `roleId` (string)
- `assignedAt`, `assignedBy` (read-only)
- `revokedAt`, `revokedBy` (read-only; if soft revoke exists; TBD)

**AuditLog**
- `auditId` (string)
- `eventType` (string)
- `actorId` (string)
- `subjectType` + `subjectId` (strings, if provided)
- `occurredAt` (datetime)
- `correlationId` (string, optional)
- `details` (json/string, read-only)

### Read-only vs editable by state/role
- Editable: roleName/description (if backend allows), grant/revoke mappings, assignments
- Read-only: permission registry, audit logs, generated IDs/timestamps

### Derived/calculated fields
- Display-only counts:
  - number of permissions in role
  - number of users assigned to role (only if API provides)

---

## 9. Service Contracts (Frontend Perspective)

> Endpoints are **TBD** unless confirmed by repo conventions; frontend must be implemented behind a service abstraction so URLs can be swapped without UI rewrites.

### Load/view calls
- `GET roles` → list roles (pagination)
- `GET roles/{roleId}` → role detail
- `GET permissions` → permission registry list (filter by domain optional)
- `GET roles/{roleId}/permissions` → current grants (or included in role detail)
- `GET principals/search?q=` → search users/principals (TBD)
- `GET principals/{principalId}/roles` → assignments (TBD)
- `GET audit?eventType in (...)&subjectId=...` → audit query (TBD)

### Create/update calls
- `POST roles` → create role
- `PUT/PATCH roles/{roleId}` → update role (TBD)

### Submit/transition calls (mutations)
- `POST roles/{roleId}/permissions:grant` with `{ permissionKeys: [] }` (TBD)
- `POST roles/{roleId}/permissions:revoke` with `{ permissionKeys: [] }` (TBD)
- `POST principals/{principalId}/roles:assign` with `{ roleIds: [] }` (TBD)
- `POST principals/{principalId}/roles:revoke` with `{ roleIds: [] }` (TBD)

### Error handling expectations
- `400` validation → surface messages; map to field errors when keys provided
- `401` unauthenticated → route to login/session recovery
- `403` unauthorized → show not-authorized state; disable controls
- `404` missing resource → show not-found
- `409` conflict (duplicate name / concurrent changes) → show conflict banner with refresh option
- Network/timeouts → retry affordance; do not assume mutation succeeded

---

## 10. State Model & Transitions

### Allowed states
- No explicit state machine defined for Role/Permission. Treat as active records.
- Assignments may be active/revoked if backend models revocation vs delete (TBD).

### Role-based transitions
- Admin-only transitions:
  - Create role
  - Update role
  - Grant/revoke permissions
  - Assign/revoke roles
- Auditor-only (or read-only admin) transitions:
  - View roles/permissions/audit

### UI behavior per state
- If assignment supports “revoked” history:
  - Show active roles by default; allow “include revoked” toggle.

---

## 11. Alternate / Error Flows

### Validation failures
- Empty role name → inline error; do not submit
- Invalid role name rejected by backend → show backend message; keep user input

### Concurrency conflicts
- If role was modified elsewhere and backend returns 409:
  - UI shows conflict message and provides “Reload role” action.
  - Do not auto-merge.

### Unauthorized access
- Direct navigation to admin screens without permission:
  - Screen returns 403 state with link back to home.
  - No mutation controls rendered.

### Empty states
- No roles exist → show empty state with “Create Role” if authorized
- No permissions granted to role → show empty state with “Grant Permissions”
- No audit events found → “No matching events”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Admin can create a role with least privilege defaults
**Given** the user is authenticated as an Admin authorized to manage roles  
**When** the user creates a role with a unique role name and optional description  
**Then** the role is created successfully  
**And** the role shows zero permissions granted by default  
**And** the UI refreshes the role detail from the backend

### Scenario 2: Duplicate role name is rejected
**Given** a role named "Cashier" already exists  
**When** the Admin attempts to create another role named "Cashier"  
**Then** the backend returns a conflict error (e.g., 409)  
**And** the UI shows an error indicating the role name already exists  
**And** no duplicate role appears in the list

### Scenario 3: Admin grants permissions to a role
**Given** an Admin is viewing an existing role detail  
**And** the permission registry is available  
**When** the Admin grants one or more permissions to the role  
**Then** the role’s granted permissions list updates to include the new permissions  
**And** the UI shows a success confirmation  
**And** the UI can navigate to audit log view and see an RBAC change event for the grant (if audit query API supports it)

### Scenario 4: Admin revokes a permission from a role
**Given** a role currently has a permission granted  
**When** the Admin revokes that permission  
**Then** the permission no longer appears in the role’s granted permissions list after refresh  
**And** any backend error is surfaced to the user with no silent failure

### Scenario 5: Admin assigns a role to a user/principal
**Given** an Admin can search and select a user/principal  
**When** the Admin assigns a role to that principal  
**Then** the assignment appears in the principal’s role list after refresh  
**And** a success confirmation is shown  
**And** the UI displays any audit reference/correlation id returned (if provided)

### Scenario 6: Unauthorized user cannot access RBAC admin mutations
**Given** the user is authenticated but lacks authorization to manage RBAC  
**When** the user navigates to the Roles & Permissions screens  
**Then** the UI shows a not-authorized state (403) or hides the module entrypoint per convention  
**And** no create/edit/grant/assign actions are available

---

## 13. Audit & Observability

### User-visible audit data
- Provide an Audit Log screen (read-only) showing RBAC-related events:
  - role created/updated
  - permission granted/revoked
  - role assigned/revoked for principal
  - (optionally) access denied events if exposed

### Status history
- Role detail includes “Recent changes” section sourced from audit query filtered by subject roleId (if supported).

### Traceability expectations
- All mutation requests include a `correlationId` header if the frontend has one (or uses backend-generated id returned in response; TBD).
- UI surfaces correlationId on success/error banners when available for support.

---

## 14. Non-Functional UI Requirements

- **Performance:** lists must support pagination; initial load < 2s on typical datasets (exact SLA TBD).
- **Accessibility:** keyboard navigable forms; proper labels; error messages associated with inputs.
- **Responsiveness:** usable on tablet widths; admin screens may be desktop-first but must not break on smaller screens.
- **i18n/timezone:** display timestamps in user’s locale/timezone per project convention; do not hardcode formats.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide explicit empty states with authorized primary actions; safe because it’s purely presentational and reversible. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-PAGINATION: Paginate role/permission/audit lists with standard page size; safe because it doesn’t change domain meaning. (Impacted: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Standard mapping of 400/401/403/404/409/network to banners/field errors; safe because it follows implied backend semantics and is UI-only. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Permission/role management API contract (blocking):** What are the exact backend endpoints, request/response schemas, and identifiers for:
   - Role CRUD
   - Permission registry list (does it come from `permissions_v1.yml` via an endpoint?)
   - Grant/revoke permissions to role
   - Assign/revoke roles to principal/user
   - Audit log query and filter fields

2. **Authorization model for admin UI (blocking):** Which permissions (keys) govern:
   - Viewing RBAC screens
   - Creating/updating roles
   - Granting/revoking permissions
   - Assigning/revoking user roles  
   (Per security agent contract: do not invent permission names.)

3. **Principal identity & scoping (blocking):**
   - Is assignment performed against `userId`, `principalId`, or something else?
   - Is RBAC scoped by `tenantId` and/or `locationId`? If yes, how is scope selected in UI and enforced?

4. **Role uniqueness & casing rules (blocking):**
   - Is role name uniqueness case-insensitive?
   - Are updates to role name allowed after creation?

5. **Audit visibility (blocking):**
   - Is audit log queryable by the frontend? If yes, what fields are safe to display and what retention applies?
   - Should access denied events be visible in UI or only in logs?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Define POS Roles and Permission Matrix — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/66

Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Define POS Roles and Permission Matrix

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want roles and permissions so that sensitive actions (refunds, overrides) are controlled.

## Details
- Roles mapped to permissions.
- Least privilege defaults.

## Acceptance Criteria
- Protected operations enforce permissions.
- Role changes audited.

## Integrations
- Integrates with HR/security identity/roles.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*
====================================================================================================

----------------------------------------------------------------------------------------------------
TASK:
----------------------------------------------------------------------------------------------------
Review and update the included issue and only this issue for the 'security' domain. Resolve any open questions in this issue using the business rules provided. Produce an implementation-ready rewrite following all guidance. Ensure output contains required section headers and labels as specified in the prompt instructions.

----------------------------------------------------------------------------------------------------
OUTPUT REQUIREMENTS (CRITICAL):
----------------------------------------------------------------------------------------------------
DO NOT create Durion-Processing.md or follow the thought logging workflow.
DO NOT create any planning or tracking files.

Use create_file tool to write the output.

YOU MUST:
1. Use read_file tool to read: after.md
2. Process and update the issue according to the instructions above
3. Use create_file tool to write ONLY the final updated story content to: fixed.md

The output file fixed.md should contain:
- The complete, updated issue body with all sections
- Resolved open questions (using business rules)
- Proper labels and structure as specified in the prompt

WRITE THE FILE NOW. Do not defer to another workflow.

