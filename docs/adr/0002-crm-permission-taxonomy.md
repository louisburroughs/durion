# ADR 0002: CRM Domain Permission Taxonomy and Authorization Model

**Status:** ACCEPTED  
**Date:** 2026-01-23  
**Author:** CRM Domain + Security Domain Collaboration  
**Affected Components:** pos-crm-service, durion-moqui-frontend, pos-security-service  

---

## Context

The CRM domain has five frontend stories requiring explicit permission gates (Issues #176, #173, #172, #171, #169). Without a defined permission taxonomy:

1. Story authoring was blocked on DECISION-INVENTORY-007 (Permissions + fail-closed enforcement)
2. Backend implementation cannot enforce authorization consistently
3. Frontend cannot implement feature visibility based on user permissions
4. Security domain cannot establish role mappings without artifact specifications

Prior CRM domain documents (AGENT_GUIDE.md, DOMAIN_NOTES.md) identified permission requirements as blocking issues but deferred the actual taxonomy definition.

This ADR formalizes the CRM permission model, establishing:

- Complete permission set for all CRM operations
- Naming conventions aligned with security domain standards
- Risk stratification (LOW/MEDIUM/HIGH/CRITICAL)
- Proposed role-to-permission mappings
- Integration points with security domain RBAC

---

## Decision

### 1. Permission Taxonomy Structure

Define 16 CRM permissions organized into 5 resource groups, following the security domain standard format: `domain:resource:action`

**Permission Groups:**

| Resource | Purpose | Primary Issues |
|----------|---------|-----------------|
| `party` | Commercial account (person/organization) CRUD | #176, #173 |
| `contact` | Contact point (email/phone) management | #172 |
| `contact_role` | Contact-to-role assignments (BILLING/APPROVER/DRIVER) | #172 |
| `contact_preference` | Communication preferences and consent flags | #171 |
| `vehicle` | Vehicle record CRUD and search | #169 |
| `vehicle_party_association` | Effective-dated vehicle-to-party relationships | #169 |
| `vehicle_preference` | Vehicle care preferences with versioning | #169 |
| `processing_log` | Read-only integration monitoring | Operational (CRM-owned) |
| `suspense` | Read-only event quarantine viewing | Operational (CRM-owned) |
| `integration` | Audit/attempt history for retries | Operational (CRM-owned) |

**Complete Permission List (16 total):**

```text
Core Party Management (3):
  crm:party:view
  crm:party:search
  crm:party:create
  crm:party:edit
  crm:party:deactivate
  crm:party:merge

Contact Management (6):
  crm:contact:view
  crm:contact:create
  crm:contact:edit
  crm:contact:delete
  crm:contact_role:view
  crm:contact_role:assign
  crm:contact_role:revoke
  crm:contact_preference:view
  crm:contact_preference:edit

Vehicle Management (5):
  crm:vehicle:view
  crm:vehicle:search
  crm:vehicle:create
  crm:vehicle:edit
  crm:vehicle:deactivate
  crm:vehicle_party_association:create
  crm:vehicle_party_association:view
  crm:vehicle_party_association:edit
  crm:vehicle_preference:view
  crm:vehicle_preference:edit

Integration Monitoring (3):
  crm:processing_log:view
  crm:suspense:view
  crm:integration:audit
```

### 2. Risk Stratification

Apply consistent risk levels tied to business impact:

| Risk Level | Characteristics | Story Examples |
|-----------|---|---|
| LOW | Read-only, no sensitive data exposure | `crm:party:view`, `crm:contact:view` |
| MEDIUM | Business data mutation, non-financial | `crm:party:create`, `crm:vehicle:create` |
| HIGH | Financial or regulatory impact, sensitive operations | `crm:party:deactivate`, `crm:contact_preference:edit` |
| CRITICAL | Merger/audit data, irreversible operations | `crm:party:merge` |

### 3. Proposed Role Mappings

Define three foundational roles with clear responsibility boundaries:

**Role: Customer Service Representative (CSR)**

- Scope: Support desk, customer-facing operations
- Permissions: 12 (read + basic contact management)
- Typical Users: Call center, chat support

**Role: Fleet Account Manager**

- Scope: Account and vehicle lifecycle
- Permissions: 22 (create + manage parties, vehicles, preferences)
- Typical Users: Account managers, fleet operations

**Role: CRM Administrator (Data Steward)**

- Scope: Data quality, master data governance
- Permissions: 26 (all CRM permissions including merge and deactivate)
- Typical Users: Data stewards, compliance officers

### 4. Integration with Security Domain

**Registration Flow:**

1. Create `permissions.yaml` in CRM service resources
2. Register during deployment via Security Domain API: `POST /v1/permissions/register`
3. Security domain maps permissions to roles in RBAC admin UI

**Enforcement:**

- Backend: `@PreAuthorize` annotation on all CRM endpoints validates permissions via Security Domain
- Frontend: Load user permissions at login, gate feature visibility accordingly
- Audit: Every permission check logs to security audit trail

### 5. Decision Artifacts

- **Normative:** [CRM_PERMISSION_TAXONOMY.md](../CRM_PERMISSION_TAXONOMY.md) — Implementation-ready permission definitions
- **Registration:** `pos-crm-service/src/main/resources/permissions.yaml` — Code-first permission manifest
- **Scope:** Durion Positivity Platform (POS backend + Moqui frontend)

---

## Consequences

### Positive

✅ **Unblocks 5 CRM stories** — All permission blocking questions (Priority 1 in crm-questions.txt) now resolved  
✅ **Consistent authorization** — All CRM endpoints enforce same permission model, fail-closed semantics  
✅ **Operational transparency** — Explicit risk levels guide role assignment and audit reviews  
✅ **Security domain alignment** — Follows existing PERMISSION_REGISTRY.md naming convention  
✅ **Frontend enablement** — Story authoring agent can now specify exact permission keys in stories  
✅ **Environment-aware** — Supports prod/non-prod authorization differences (e.g., executive approval for merge in prod only)  

### Negative / Challenges

⚠️ **Backend service dependency** — CRM service doesn't yet exist; requires creation of `pos-crm-service` module with Spring Boot + Moqui integration  
⚠️ **Security domain integration** — Requires coordination with security team for role→permission mappings in RBAC admin UI  
⚠️ **Moqui screen permission mapping** — Moqui screens (e.g., account creation UI) must be annotated with permission checks via `<check-permission>`  
⚠️ **Retroactive permission application** — Existing CRM functionality in Moqui must be retrofitted with permission checks  

### Mitigations

1. **Service creation:** Schedule pos-crm-service setup as Priority 2 task after taxonomy approval
2. **Security coordination:** Define SLA for Security domain to complete role mapping (recommend 1 sprint)
3. **Moqui annotation:** Include permission checking as part of CRM story implementation checklist
4. **Retroactive retrofit:** Mark existing Moqui CRM screens with tech debt tags; plan permission retrofit in backlog

---

## Rationale

### Why This Taxonomy?

1. **Grouped by CRM system-of-record boundaries:** Party, Contact, Vehicle are distinct entities with different authorization scopes
2. **Story-driven:** Each permission maps to at least one blocking open question from crm-questions.txt
3. **CRITICAL permissions reserved for rare operations:** Only `crm:party:merge` is CRITICAL, reflecting business risk
4. **Operational screens separated:** Integration monitoring (processing_log, suspense) are read-only, lower risk

### Why Proposed Role Mappings?

1. **CSR role:** Supports customer-facing operations without account creation authority (prevents spam account creation)
2. **Fleet Account Manager role:** Full access to party and vehicle lifecycle; representative of typical customer success use case
3. **CRM Administrator role:** Includes merge and deactivate; restricted to small team for data governance
4. **Alignment with Durion personas:** Roles map to documented business personas in CRM domain stories

### Why Security Domain Integration?

1. **Single source of truth for permissions:** Security domain owns permission registry; CRM service registers via standard API
2. **Consistent audit trails:** Authorization checks logged to security audit domain, not duplicated in CRM
3. **Deny-by-default semantics:** Leverages DECISION-INVENTORY-001 (Security domain standard); no need to reinvent
4. **Flexible role evolution:** Role-to-permission mappings can change without CRM code deployment

---

## Alternatives Considered

### A1: Embed Permissions in CRM Service

**Option:** CRM service owns permission definitions; no registration with security domain.  
**Rejected:** Creates authorization fragmentation; security audit trail incomplete; frontend must poll CRM for permissions.

### A2: Use Spring Security Authorities Instead of Custom Permissions

**Option:** Map CRM operations to Spring role/authority model directly.  
**Rejected:** Limits flexibility for cross-domain permission evaluation; doesn't align with security domain's centralized permission registry.

### A3: Single `crm:all` Permission for MVP

**Option:** Define one catch-all permission instead of granular set.  
**Rejected:** Violates least-privilege principle; can't implement separation of duties (e.g., CSR can't create parties but can manage contacts); blocks compliance use cases.

---

## Related Decisions

| Decision | Link | Relationship |
|----------|------|--------------|
| DECISION-INVENTORY-001 | Security AGENT_GUIDE.md | Deny-by-default authorization model |
| DECISION-INVENTORY-002 | Security PERMISSION_REGISTRY.md | Permission naming format (`domain:resource:action`) |
| DECISION-INVENTORY-007 | CRM AGENT_GUIDE.md | Explicit permissions + fail-closed enforcement (this ADR realizes DECISION-INVENTORY-007) |
| DECISION-INVENTORY-001 | CRM AGENT_GUIDE.md | partyId as canonical identifier (influences party permission scoping) |

---

## References

- **CRM Permission Taxonomy (Normative):** [`domains/crm/CRM_PERMISSION_TAXONOMY.md`](../CRM_PERMISSION_TAXONOMY.md)
- **CRM Open Questions (Priority 1):** [`domains/crm/crm-questions.txt`](../crm-questions.txt)
- **CRM AGENT_GUIDE:** [`domains/crm/.business-rules/AGENT_GUIDE.md`](../.business-rules/AGENT_GUIDE.md)
- **Security PERMISSION_REGISTRY:** [`domains/security/docs/PERMISSION_REGISTRY.md`](../../security/docs/PERMISSION_REGISTRY.md)
- **Security BASELINE_PERMISSIONS:** [`domains/security/docs/BASELINE_PERMISSIONS.md`](../../security/docs/BASELINE_PERMISSIONS.md)
- **Affected Stories:**
  - Issue #176: Create Commercial Account
  - Issue #173: Search and Merge Duplicate Parties
  - Issue #172: Maintain Contact Roles and Primary Flags
  - Issue #171: Manage Communication Preferences & Consent Flags
  - Issue #169: Create Vehicle Record

---

## Approval Process

This ADR is **PROPOSED** and awaiting approval from:

1. **Security Domain Lead** — Architectural alignment with PERMISSION_REGISTRY.md and RBAC model
2. **CRM Domain Lead** — Completeness of permission coverage
3. **Principal Software Engineer** — Cross-domain impact and integration feasibility

**Sign-off template:**

```
Security Domain Lead: [Name] _____ [Date]
CRM Domain Lead:     [Name] _____ [Date]
Principal Engineer:  [Name] _____ [Date]
```

---

## Implementation Roadmap

### Phase 1: Approval & Documentation (Current)

- [ ] Security domain review
- [ ] CRM domain review
- [ ] Update INVENTORY_PERMISSIONS.md to reference CRM taxonomy
- [ ] Update CRM AGENT_GUIDE.md to link to this ADR

### Phase 2: Backend Service Setup (Priority 2)

- [ ] Create `pos-crm-service` Spring Boot module
- [ ] Create `permissions.yaml` with 16 CRM permissions
- [ ] Implement `@PreAuthorize` checks on all endpoints
- [ ] Register permissions with Security Domain API at startup

### Phase 3: Moqui Integration (Priority 3)

- [ ] Create Moqui screens for CRM operations (party, contact, vehicle)
- [ ] Add `<check-permission>` directives to Moqui screens
- [ ] Test permission-gated screens locally

### Phase 4: Story Implementation (Priority 4+)

- [ ] Update Issue #176–#169 with exact permission keys from this ADR
- [ ] Story authoring agent proceeds with implementation
- [ ] Backend team implements permission enforcement
- [ ] QA validates permission denial and access control

---

## Future Enhancements (Safe-to-Defer)

- **Threshold-based approval:** `crm:party:merge` requiring executive sign-off above $X transaction threshold
- **Effective-dated role assignments:** DECISION-INVENTORY-007 (Security) supports temporal RBAC; CRM frontend deferred
- **Environment-specific restrictions:** Prod-only enforcement of `crm:party:merge` approval gates
- **Audit report generation:** Compliance reports on permission usage and denied access attempts

---

## Questions & Open Issues

| Issue | Status | Owner | ETA |
|-------|--------|-------|-----|
| Role mappings finalized by Security domain? | PENDING | Security Lead | Sprint [TBD] |
| pos-crm-service created? | NOT STARTED | Backlog | Sprint [TBD] |
| Moqui @check-permission pattern established? | NOT STARTED | Framework Lead | Sprint [TBD] |

---

**ADR Template Version:** 1.0  
**Document Status:** PROPOSED (awaiting architectural review)
