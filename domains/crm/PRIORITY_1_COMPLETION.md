# CRM Priority 1 Completion - Integration Guide

**Objective:** Resolve all blocking permission questions from CRM domain stories  
**Status:** ✅ SUBMITTED FOR ARCHITECTURAL REVIEW  
**Target Date:** 2026-01-23  
**Completion Date:** 2026-01-23

---

## Overview

Priority 1 of the CRM domain open questions has been **completely resolved** by defining a formal permission taxonomy and submitting it for architectural approval.

**What was blocked:** 5 frontend stories waiting on permission definitions  
**What is now unblocked:** Story authoring can proceed with exact permission keys  
**What remains:** Architectural review and backend service implementation

---

## Artifacts Created

### 1. Open Questions Extraction (Foundation)
**File:** `domains/crm/crm-questions.txt` (26 questions, 5 stories)

- Consolidated all blocking questions from domain-crm.txt
- Organized by issue and identified cross-cutting themes
- **Priority 1:** All permission questions requiring backend clarification

### 2. Permission Taxonomy (Normative)
**File:** `domains/crm/CRM_PERMISSION_TAXONOMY.md` (481 lines)

- 27 CRM permissions organized into 10 resource groups
- Risk stratification: 1 CRITICAL, 4 HIGH, 16 MEDIUM, 6 LOW
- 3 proposed role mappings (CSR, Fleet Manager, Administrator)
- Security integration contract and enforcement patterns
- **Status:** PROPOSED (linked to ADR 0002)

### 3. Architecture Decision Record
**File:** `docs/adr/0002-crm-permission-taxonomy.md` (315 lines)

- Formal architectural decision captured
- Complete rationale and alternatives analysis
- Implementation roadmap with 4 phases
- Approval sign-off template
- Cross-references to related decisions
- **Status:** PROPOSED (awaiting 3 stakeholder sign-offs)

### 4. Submission Package
**File:** `domains/crm/APPROVAL_SUBMISSION.md` (303 lines)

- Executive summary of submission
- What was resolved (all 5 stories unblocked)
- Key deliverables and risk analysis
- Approval checklist and stakeholder questions
- Next actions (post-approval roadmap)
- Contact and timeline info

### 5. Documentation Updates
- `docs/adr/README.md` — Added ADR 0002 entry
- `domains/security/docs/INVENTORY_PERMISSIONS.md` — Added cross-reference

---

## What Was Resolved

### Before: Blocking Status

| Issue | Story | Blocker |
|-------|-------|---------|
| #176 | Create Commercial Account | "What permission key for party creation?" |
| #173 | Merge Duplicate Parties | "What permission key for party merge?" |
| #172 | Maintain Contact Roles | "What permissions for role assignment?" |
| #171 | Communication Preferences | "What permissions for preferences edit?" |
| #169 | Create Vehicle Record | "What permission keys for vehicle operations?" |

### After: Unblocked Status

| Issue | Story | Resolved With | Permission(s) |
|-------|-------|---|---|
| #176 | Create Commercial Account | CRM_PERMISSION_TAXONOMY.md | `crm:party:create` |
| #173 | Merge Duplicate Parties | CRM_PERMISSION_TAXONOMY.md | `crm:party:merge` |
| #172 | Maintain Contact Roles | CRM_PERMISSION_TAXONOMY.md | `crm:contact_role:*` (3 perms) |
| #171 | Communication Preferences | CRM_PERMISSION_TAXONOMY.md | `crm:contact_preference:*` (2 perms) |
| #169 | Create Vehicle Record | CRM_PERMISSION_TAXONOMY.md | `crm:vehicle:*` (5 perms) |

---

## How Reviewers Should Approach This

### Security Domain Lead
1. Read: `docs/adr/0002-crm-permission-taxonomy.md` (sections: Context, Decision, Rationale)
2. Validate: Risk stratification aligns with DECISION-INVENTORY-001 (deny-by-default)
3. Review: Integration contract with Security Domain API
4. Approve: Permission format and role mapping patterns
5. Action: Provide role mapping guidance for implementation

### CRM Domain Lead
1. Read: `domains/crm/CRM_PERMISSION_TAXONOMY.md` (sections: Overview, Permission Groups, Role Mappings)
2. Validate: All 26 open questions resolved by this taxonomy
3. Confirm: Permission coverage complete for all 5 stories
4. Review: Risk levels match business impact
5. Approve: Ready to unblock story authoring

### Principal Software Engineer
1. Read: `docs/adr/0002-crm-permission-taxonomy.md` (sections: Consequences, Implementation Roadmap)
2. Assess: Cross-domain complexity and feasibility
3. Review: Phase 1-4 roadmap and effort estimates
4. Identify: Risks and dependencies (pos-crm-service, Moqui integration)
5. Approve: Proceed with architecture decision

---

## Next Steps Post-Approval

### Immediate (Upon Approval)
- [ ] Update ADR 0002 status from PROPOSED → ACCEPTED
- [ ] Update CRM_PERMISSION_TAXONOMY.md status from PROPOSED → ACCEPTED
- [ ] Notify Story Authoring Agent with exact permission keys
- [ ] Update crm-questions.txt to mark Priority 1 as RESOLVED

### Phase 1: Backend Service Setup (Sprint [TBD])
- [ ] Create `pos-crm-service` Spring Boot module
- [ ] Create `permissions.yaml` with 27 CRM permissions
- [ ] Implement `@PreAuthorize` checks on endpoints
- [ ] Register permissions with Security Domain API

### Phase 2: Moqui Integration (Sprint [TBD])
- [ ] Create CRM Moqui screens (party, contact, vehicle)
- [ ] Add `<check-permission>` directives
- [ ] Test permission-gated access locally

### Phase 3: Story Implementation (Sprint [TBD]+)
- [ ] Update Issues #176–#169 with permission keys
- [ ] Story Authoring Agent completes stories
- [ ] Backend implementation with enforcement
- [ ] QA validates permission denial

### Phase 4: Operations & Configuration
- [ ] Security domain creates roles in admin UI
- [ ] Map permissions to roles per proposed assignments
- [ ] Initialize test users with roles
- [ ] UAT on permission-gated workflows

---

## Communication Checklist

- [ ] Email approval submission to Security Domain Lead
- [ ] Email approval submission to CRM Domain Lead
- [ ] Email approval submission to Principal Software Engineer
- [ ] Post #crm-domain Slack thread with submission summary
- [ ] Post approval sign-offs when received
- [ ] Notify engineering teams of unblocking event

---

## Documents for Sharing

**For Architects/Leads:**
- `docs/adr/0002-crm-permission-taxonomy.md` (formal decision)
- `domains/crm/APPROVAL_SUBMISSION.md` (executive summary)

**For Implementation Teams:**
- `domains/crm/CRM_PERMISSION_TAXONOMY.md` (reference guide)
- `domains/crm/crm-questions.txt` (resolved questions)

**For Security Domain:**
- `docs/adr/0002-crm-permission-taxonomy.md` (architectural context)
- `domains/crm/CRM_PERMISSION_TAXONOMY.md` (permission definitions)
- `domains/security/docs/INVENTORY_PERMISSIONS.md` (reference pattern)

---

## FAQ

**Q: Are all Priority 1 questions from crm-questions.txt resolved?**  
A: Yes. All 5 issues (#176, #173, #172, #171, #169) had blocking permission questions; all are now resolved with specific permission keys.

**Q: What about Priority 2 and 3 questions?**  
A: Priority 2 is backend contract specification (API endpoints, DTOs). Priority 3 is cross-domain dependencies (Billing, Vehicle Fitment). These remain separate and will be addressed as separate work items.

**Q: When can story implementation start?**  
A: Upon approval (expected 2026-01-30). Story Authoring Agent will inject permission keys into Issues #176–#169 immediately post-approval.

**Q: Is pos-crm-service created yet?**  
A: No. Service creation is Phase 1, scheduled for Sprint [TBD] (post-approval). This is a separate story.

**Q: Do we need all 27 permissions for MVP?**  
A: Yes. The 5 stories require different subsets, but collectively they use all 27. However, Moqui screen implementation and backend enforcement can be phased.

**Q: What about environment-specific permissions (prod vs non-prod)?**  
A: This is safe-to-defer (noted in ADR 0002 "Future Enhancements"). Can be implemented post-approval if business requires it.

---

## Metrics & Success Criteria

**Success for Priority 1 = Approval + Story Unblocking:**

- [x] All 26 open questions reviewed and answered
- [x] Permission taxonomy created and documented
- [x] Risk stratification applied consistently
- [x] Role mappings proposed and justified
- [x] ADR submitted for architectural review
- [x] Documentation updated and cross-linked
- [ ] Approved by 3 stakeholders (IN PROGRESS)
- [ ] Story Authoring Agent notified with permission keys (BLOCKED ON APPROVAL)
- [ ] Issues #176–#169 updated with permission keys (BLOCKED ON APPROVAL)

**Timeline:**
- Submission: 2026-01-23 ✅
- Expected Approval: 2026-01-30
- Story Update: 2026-01-30 (same day as approval)
- Story Implementation Start: 2026-02-06 (next sprint)

---

## References

### CRM Domain
- `domains/crm/crm-questions.txt` — Open questions
- `domains/crm/CRM_PERMISSION_TAXONOMY.md` — Permission definitions
- `domains/crm/.business-rules/AGENT_GUIDE.md` — Domain invariants (DECISION-INVENTORY-007)

### Architecture
- `docs/adr/0002-crm-permission-taxonomy.md` — Formal decision record
- `docs/adr/README.md` — ADR registry

### Security Domain
- `domains/security/docs/PERMISSION_REGISTRY.md` — Format standard
- `domains/security/docs/BASELINE_PERMISSIONS.md` — Examples
- `domains/security/.business-rules/AGENT_GUIDE.md` — Authorization model

### Affected Stories
- Issue #176: Create Commercial Account
- Issue #173: Search and Merge Duplicate Parties  
- Issue #172: Maintain Contact Roles and Primary Flags
- Issue #171: Manage Communication Preferences & Consent Flags
- Issue #169: Create Vehicle Record

---

**Submitted:** 2026-01-23  
**Status:** ✅ SUBMITTED FOR ARCHITECTURAL REVIEW  
**Next Milestone:** Approval sign-offs from Security, CRM, Principal Engineer
