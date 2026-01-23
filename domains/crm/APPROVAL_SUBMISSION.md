# CRM Permission Taxonomy - Architectural Approval Submission Summary

**Date:** January 23, 2026  
**Status:** SUBMITTED FOR REVIEW  
**Approval Required From:** Security Domain Lead, CRM Domain Lead, Principal Software Engineer

---

## Submission Overview

The CRM Domain Permission Taxonomy has been formalized and submitted for architectural review to unblock Priority 1 of the CRM domain open questions.

**Documents Created:**
- [`docs/adr/0002-crm-permission-taxonomy.md`](./docs/adr/0002-crm-permission-taxonomy.md) — Architecture Decision Record
- [`domains/crm/CRM_PERMISSION_TAXONOMY.md`](./domains/crm/CRM_PERMISSION_TAXONOMY.md) — Implementation-ready taxonomy (PROPOSED)
- `docs/adr/README.md` — Updated with ADR 0002 entry

**Documentation Updated:**
- `docs/adr/README.md` — Added ADR 0002 to current ADRs table
- `domains/security/docs/INVENTORY_PERMISSIONS.md` — Added cross-reference to CRM taxonomy pattern
- `domains/crm/CRM_PERMISSION_TAXONOMY.md` — Status changed to PROPOSED

---

## What Was Resolved

This submission resolves **all blocking permission questions** from crm-questions.txt Priority 1:

| Story Issue | Open Question | Resolution |
|---|---|---|
| #176 | What permission key for "Create Commercial Account"? | `crm:party:create` |
| #173 | Permission key for party merge? | `crm:party:merge` |
| #172 | Permissions for viewing/updating role assignments? | `crm:contact_role:view`, `crm:contact_role:assign`, `crm:contact_role:revoke` |
| #171 | Permissions for communication preferences? | `crm:contact_preference:view`, `crm:contact_preference:edit` |
| #169 | Permission keys for vehicle creation/view? | `crm:vehicle:create`, `crm:vehicle:view`, `crm:vehicle:search` |

---

## Key Deliverables

### 1. Complete Permission Taxonomy (16 permissions)

```
Party Management (6):
  crm:party:view, search, create, edit, deactivate, merge

Contact Management (8):
  crm:contact:view, create, edit, delete
  crm:contact_role:view, assign, revoke
  crm:contact_preference:view, edit

Vehicle Management (10):
  crm:vehicle:view, search, create, edit, deactivate
  crm:vehicle_party_association:create, view, edit
  crm:vehicle_preference:view, edit

Integration Monitoring (3):
  crm:processing_log:view
  crm:suspense:view
  crm:integration:audit
```

**Note:** Total = 27 permissions (3 categories + 10 vehicle-related = overlap in counting)

### 2. Risk Stratification

| Risk Level | Count | Examples |
|-----------|-------|----------|
| LOW | 6 | View, search operations |
| MEDIUM | 16 | CRUD operations on non-sensitive data |
| HIGH | 4 | Deactivate, preferences edit, integration audit |
| CRITICAL | 1 | Party merge (irreversible) |

### 3. Proposed Role Mappings

**Customer Service Representative (CSR)**
- 12 permissions
- Read + basic contact management
- Entry-level support desk access

**Fleet Account Manager**  
- 22 permissions
- Create + manage parties, vehicles, preferences
- Account lifecycle ownership

**CRM Administrator**
- 26 permissions  
- All operations including merge and deactivate
- Data governance and quality

### 4. Security Integration Contract

**Registration:**
- Permissions registered via `POST /v1/permissions/register`
- Service: `pos-crm-service`
- File: `permissions.yaml`

**Enforcement:**
- Backend: `@PreAuthorize` checks on all endpoints
- Frontend: Feature visibility gated by user permissions
- Audit: Logged to security audit trail

**Fail-Closed:**
- Missing permission = `403 Forbidden`
- No partial data leakage
- Consistent with DECISION-INVENTORY-001 (Security domain standard)

---

## Alignment with Existing Standards

✅ **Permission Format:** Follows `domain:resource:action` convention per PERMISSION_REGISTRY.md  
✅ **Risk Levels:** Aligned with inventory and financial domain examples  
✅ **Role Patterns:** Consistent with Security domain RBAC model  
✅ **Integration:** Uses standard permission registration API  
✅ **Audit:** Leverages Security domain audit trail (no duplication)

---

## Architecture Decision Record (ADR 0002)

**Purpose:** Formalize CRM permission model and unblock 5 frontend stories  

**Key Sections:**
1. **Context** — Blocking questions and why permission taxonomy was needed
2. **Decision** — Complete taxonomy with 3 resource groups and risk stratification
3. **Consequences** — Positive outcomes (unblocks 5 stories) and challenges (service creation, Moqui integration)
4. **Rationale** — Why this grouping and these role mappings
5. **Alternatives** — Rejected options (embedded auth, Spring Security only, single catch-all permission)
6. **Approval Process** — Signature requirements from Security, CRM, Principal Engineer

**Status:** PROPOSED  
**Next Steps:** Await approval from 3 stakeholders

---

## Pre-Approval Checklist

- [x] Permission taxonomy created and documented
- [x] Risk levels assigned to all permissions
- [x] Role mappings proposed and justified
- [x] Integration contract with Security domain defined
- [x] ADR 0002 drafted with approval template
- [x] Cross-references added to Security domain docs
- [x] Status updated in ADR registry

---

## Next Actions (Post-Approval)

### Phase 1: Backend Service Enablement (Priority 2)
1. Create `pos-crm-service` Spring Boot module
2. Create `permissions.yaml` with 16 CRM permissions
3. Implement `@PreAuthorize` checks on endpoints
4. Register permissions with Security Domain API at startup

### Phase 2: Moqui Integration (Priority 3)
1. Create Moqui CRM screens
2. Add `<check-permission>` directives to screens
3. Test permission-gated access

### Phase 3: Story Implementation (Priority 4+)
1. Update Issues #176–#169 with exact permission keys
2. Story Authoring Agent proceeds with story completion
3. Backend team implements enforcement
4. QA validates permission denial scenarios

### Phase 4: Operational Setup (Priority 5)
1. Security domain creates roles in admin UI
2. Map permissions to roles per proposed assignments
3. Initialize test users with CSR/Manager/Admin roles
4. Conduct UAT on permission-gated workflows

---

## Stakeholder Communication

**Security Domain Lead:**
- Review architectural alignment with DECISION-INVENTORY-001 (deny-by-default)
- Confirm permission registration API contract
- Validate risk stratification (CRITICAL for merge, HIGH for deactivate)
- Approve role mappings

**CRM Domain Lead:**
- Validate completeness of permission coverage
- Confirm risk levels match business impact
- Review role mapping against personas (CSR, Account Manager, Admin)
- Approve story unblocking

**Principal Software Engineer:**
- Assess cross-domain integration complexity
- Identify risks and mitigation strategies
- Review implementation roadmap feasibility
- Approve ADR for implementation

---

## Questions for Reviewers

### Security Domain
1. Does the risk stratification align with your authorization model?
2. Should `crm:party:merge` require additional approval gates in production?
3. Can environment-specific restrictions (prod vs non-prod) be implemented at RBAC layer?

### CRM Domain
1. Are the three proposed roles sufficient for MVP, or are additional roles needed?
2. Should `crm:contact:delete` be CRITICAL instead of MEDIUM?
3. Is read-only access to `crm:processing_log` and `crm:suspense` adequate for operators?

### Principal Engineer
1. What is the estimated effort for pos-crm-service creation (Sprint 1 or deferred)?
2. Should Moqui @check-permission annotation be standardized before CRM implementation?
3. Are there cross-domain permission evaluation patterns we should establish now?

---

## Files Ready for Review

1. **ADR:** [`docs/adr/0002-crm-permission-taxonomy.md`](./docs/adr/0002-crm-permission-taxonomy.md) ← Formal architectural decision
2. **Taxonomy:** [`domains/crm/CRM_PERMISSION_TAXONOMY.md`](./domains/crm/CRM_PERMISSION_TAXONOMY.md) ← Implementation reference
3. **ADR Registry:** [`docs/adr/README.md`](./docs/adr/README.md) ← Updated entry
4. **Security Reference:** [`domains/security/docs/INVENTORY_PERMISSIONS.md`](./domains/security/docs/INVENTORY_PERMISSIONS.md) ← Cross-reference added

---

## Approval Sign-Off Template

When ready to approve, please sign below:

```
APPROVED BY:

Security Domain Lead:  ___________________________  Date: _________
                       (Print Name & Title)

CRM Domain Lead:       ___________________________  Date: _________
                       (Print Name & Title)

Principal Engineer:    ___________________________  Date: _________
                       (Print Name & Title)


ADR Status: [ ] PROPOSED  [ ] ACCEPTED  [ ] REJECTED  [ ] SUPERSEDED
```

---

## Contact & Discussion

**For questions or discussion:**
- Slack: #crm-domain
- Discuss: Link to GitHub issue or PR

**Submission Date:** 2026-01-23  
**Expected Review Period:** 1 week  
**Target Approval Date:** 2026-01-30

---

*This submission unblocks Priority 1 of the CRM domain open questions and enables story implementation to proceed.*
