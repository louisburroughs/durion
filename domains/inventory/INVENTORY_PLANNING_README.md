# Inventory Domain - Phase 3 Planning Guide

## Quick Start

This directory contains the comprehensive Phase 3 implementation plan for unblocking all **20 inventory domain issues** with `blocked:clarification` status.

### Key Files

- **[inventory-questions.md](inventory-questions.md)** - Phase 3 plan with all blocking questions, issue details, and progress checklist
- **[INVENTORY_PLANNING_README.md](INVENTORY_PLANNING_README.md)** - This file; overview and execution guide

### Current Status

🔄 **Phase 1: Assessment & Categorization** (IN PROGRESS)

- 20 inventory issues identified
- 50+ blocking questions to resolve
- Domain conflicts identified (6 issues likely in other domains)
- Implementation phase plan created

---

## Issues to Unblock

### Total: 20 Issues

| # | Title | Status | Blocking Qs | Domain | Priority |
|---|-------|--------|------------|--------|----------|
| #260 | Store Supplier/Vendor Cost Tiers | draft | 8 | inventory/pricing | P2 |
| #244 | Mechanic Executes Picking | draft | 9 | inventory/workexec | P1 |
| #243 | Issue/Consume Picked Items | draft | TBD | inventory/workexec | P1 |
| #242 | Return Unused Items to Stock | draft | TBD | inventory/workexec | P2 |
| #241 | Plan Cycle Counts | draft | TBD | inventory | P2 |
| #121 | Create Product Record | draft | TBD | inventory/product | P0 |
| #120 | Manage UOM Conversions | draft | TBD | inventory/product | P0 |
| #119 | Product Lifecycle State | draft | TBD | inventory/product | P0 |
| #108 | Fitment Hints & Vehicle Tags | draft | TBD | inventory | P2 |
| #99 | Receiving Session from PO/ASN | draft | TBD | inventory | P1 |
| #97 | Direct-to-Workorder Receiving | draft | TBD | inventory | P2 |
| #96 | Put-away Tasks from Staging | draft | TBD | inventory | P1 |
| #94 | Replenish Pick Faces | draft | TBD | inventory | P3 |
| #93 | Reserve/Allocate Stock | draft | TBD | inventory | P1 |
| #92 | Create Pick List/Tasks | draft | TBD | inventory | P1 |
| #91 | Execute Cycle Count | draft | TBD | inventory | P2 |
| #90 | Approve Adjustments | draft | TBD | inventory | P2 |
| #88 | Reallocate Reserved Stock | draft | TBD | inventory | P3 |
| #87 | Define Roles & Permissions | draft | TBD | inventory | P0 |
| #81 | Search Catalog | draft | TBD | inventory/product | P3 |

---

## Phase Breakdown

### Phase 1: Assessment & Categorization (CURRENT)

**Duration:** 1-2 days  
**Owner:** Platform Team / Agent  
**Deliverables:**
- Complete blocking questions extraction for all 20 issues
- Domain conflict identification and resolution
- Categorization by question type
- Updated inventory-questions.md with Phase 1 analysis

**Progress:**
- [x] Issue list created (20 issues)
- [x] Phase plan skeleton established
- [x] Two detailed issues analyzed (#260, #244)
- [ ] Complete all remaining 18 issues
- [ ] Identify domain ownership resolutions
- [ ] Create dependency matrix

### Phase 2: Backend Contract Discovery

**Duration:** 2-3 days (parallel with Phase 1)  
**Owner:** Backend Research / Agent  
**Deliverables:**
- Backend endpoint contracts documented
- Entity/field schemas identified
- Permission models defined
- State machine definitions captured
- Error code taxonomies documented

**Tasks:**
- Research durion-positivity-backend source code
- Review pos-inventory, pos-order, pos-workexec modules
- Document REST API patterns
- Extract validation rules and error codes
- Cross-reference existing domain decisions

### Phase 3: GitHub Issue Resolution

**Duration:** 1-2 days  
**Owner:** Communication / Agent  
**Deliverables:**
- Phase 3 resolution comments on all 20 GitHub issues
- Label updates (remove `blocked:clarification`, add `status:resolved`)
- Documentation links in each issue
- Implementation readiness indicators

**Process:**
1. Create comment template with key findings structure
2. For each issue (sorted by ID):
   - Extract 4-10 key findings
   - Link to inventory-questions.md
   - Provide entity schemas / endpoints / permissions
   - Set implementation readiness status
   - Add comment to durion-moqui-frontend issue

### Phase 4: Documentation & Handoff

**Duration:** 1-2 days  
**Owner:** Documentation / Agent  
**Deliverables:**
- Complete inventory-questions.md with all resolutions
- Implementation acceptance criteria for each issue
- Dependency order and timeline
- Durion-Processing.md with Phase 3 summary
- Label update scripts (Bash + Python)

---

## Blocking Question Categories

### Category 1: Domain Ownership (6 issues)

**Issue Count:** 6  
**Impact:** May require reassignment to other domains (Product, Pricing, WorkExec)  
**Action:** Domain governance review needed

```
Issue #260 - Supplier/Vendor Cost Tiers  → inventory vs pricing
Issue #244 - Mechanic Executes Picking  → inventory vs workexec
Issue #243 - Issue/Consume Items         → inventory vs workexec
Issue #242 - Return Unused Items        → inventory vs workexec
Issue #121 - Create Product Record      → inventory vs product
Issue #120 - Manage UOM Conversions     → inventory vs product
Issue #119 - Product Lifecycle State    → inventory vs product
Issue #81  - Search Catalog             → inventory vs product
```

### Category 2: Backend Contracts (18 issues)

**Issue Count:** 18  
**Questions per Issue:** 6-9  
**Total Questions:** ~120+  
**Action:** Research durion-positivity-backend source code

**Typical Questions:**
- What endpoints exist?
- What request/response payloads?
- What identifiers/keys?
- What error codes?
- What state transitions?
- What validation rules?

### Category 3: Permissions & Security (10+ issues)

**Issue Count:** 10+  
**Questions:** Permission token definitions, role-based access, authorization model  
**Action:** Review existing permission patterns across domains

**Pattern Across Domains:**
```
accounting:    resource:action (e.g., accounting:mapping:view)
product:       resource:action
workexec:      resource:action
inventory:     resource:action (TBD)
```

### Category 4: Identifier Models (8+ issues)

**Issue Count:** 8+  
**Questions:** Canonical identifier choices (ID, SKU, UUID, composite key)  
**Action:** Align with backend entity primary/foreign keys

---

## How to Use inventory-questions.md

### For Agents Executing Phase 1

1. **Open** [inventory-questions.md](inventory-questions.md)
2. **Navigate to** Issue section (e.g., "Issue #260")
3. **Review** Open Questions (marked with status)
4. **Update** question status as you research:
   - 🔴 BLOCKING → 🟡 IN PROGRESS → 🟢 RESOLVED
5. **Add findings** in Answer sections with:
   - Backend entity/endpoint name
   - Request/response field names
   - Validation rules
   - Source link (e.g., pos-inventory module)
6. **Commit changes** to inventory-questions.md

### For Backend Research Team

1. **Start with** Category 2: Backend Contracts section
2. **Clone** durion-positivity-backend repository
3. **Search for** entities/services matching issue names:
   - Issue #260 → Search for "SupplierCost" or "PriceTier"
   - Issue #244 → Search for "PickTask" or "PickLine"
   - Issue #243 → Search for "Consumption" or "Issue"
4. **Document** findings in inventory-questions.md
5. **Link** to specific source files (e.g., `/pos-inventory/PickTask.java`)

### For GitHub Communication Team

1. **Start with** Phase 3: GitHub Issue Resolution section
2. **Generate** comment for each issue with template:
   ```markdown
   ## Phase 3 Resolution Complete ✅
   
   Status: [X/Y] blocking questions resolved
   
   ### Key Findings
   - Entity: [name] with fields [...]
   - Endpoints: [GET/POST/PUT paths]
   - Permissions: [token list]
   - [other critical info]
   
   ### Documentation
   Complete details: [inventory-questions.md link]
   
   ### Ready for Implementation ✅
   ```
3. **Add** comment to durion-moqui-frontend issue (#260, #244, etc.)
4. **Update** GitHub labels via script

---

## Progress Tracking

### Phase 1 Completion Checklist

- [ ] Issue #260 - 8 questions extracted and analyzed
- [ ] Issue #244 - 9 questions extracted and analyzed
- [ ] Issue #243 - Blocking questions extracted
- [ ] Issue #242 - Blocking questions extracted
- [ ] Issue #241 - Blocking questions extracted
- [ ] Issue #121 - Blocking questions extracted
- [ ] Issue #120 - Blocking questions extracted
- [ ] Issue #119 - Blocking questions extracted
- [ ] Issue #108 - Blocking questions extracted
- [ ] Issue #99 - Blocking questions extracted
- [ ] Issue #97 - Blocking questions extracted
- [ ] Issue #96 - Blocking questions extracted
- [ ] Issue #94 - Blocking questions extracted
- [ ] Issue #93 - Blocking questions extracted
- [ ] Issue #92 - Blocking questions extracted
- [ ] Issue #91 - Blocking questions extracted
- [ ] Issue #90 - Blocking questions extracted
- [ ] Issue #88 - Blocking questions extracted
- [ ] Issue #87 - Blocking questions extracted
- [ ] Issue #81 - Blocking questions extracted
- [ ] Domain conflicts identified and noted
- [ ] Dependency matrix created

### Phase 2 Completion Checklist

- [ ] pos-inventory module analyzed
- [ ] pos-order module analyzed
- [ ] pos-workexec module analyzed
- [ ] REST endpoint patterns documented
- [ ] Entity schemas captured
- [ ] Permission models identified
- [ ] State machines documented
- [ ] Error codes extracted
- [ ] All 50+ backend contract questions answered

### Phase 3 Completion Checklist

- [ ] Comment template created
- [ ] Issue #260 comment added to GitHub
- [ ] Issue #244 comment added to GitHub
- [ ] Issue #243 comment added to GitHub
- [ ] Issue #242 comment added to GitHub
- [ ] Issue #241 comment added to GitHub
- [ ] Issue #121 comment added to GitHub
- [ ] Issue #120 comment added to GitHub
- [ ] Issue #119 comment added to GitHub
- [ ] Issue #108 comment added to GitHub
- [ ] Issue #99 comment added to GitHub
- [ ] Issue #97 comment added to GitHub
- [ ] Issue #96 comment added to GitHub
- [ ] Issue #94 comment added to GitHub
- [ ] Issue #93 comment added to GitHub
- [ ] Issue #92 comment added to GitHub
- [ ] Issue #91 comment added to GitHub
- [ ] Issue #90 comment added to GitHub
- [ ] Issue #88 comment added to GitHub
- [ ] Issue #87 comment added to GitHub
- [ ] Issue #81 comment added to GitHub
- [ ] `blocked:clarification` labels removed from all issues
- [ ] `status:resolved` labels added to all issues
- [ ] Documentation links added to all comments

### Phase 4 Completion Checklist

- [ ] All 20 issues have final detailed sections
- [ ] All 50+ blocking questions answered
- [ ] Acceptance criteria defined for all issues
- [ ] Implementation order documented
- [ ] Dependencies captured in matrix
- [ ] Risk assessments completed
- [ ] Timeline established
- [ ] Label update scripts created
- [ ] Durion-Processing.md summary created
- [ ] Lessons learned documented

---

## Related Documentation

**Accounting Domain (Completed Phase 3 Example):**
- [accounting-questions.md](../accounting/accounting-questions.md) - Full example with 6 issues resolved
- Follow this format and depth for inventory-questions.md

**Inventory Domain Rules:**
- [DECISION-INVENTORY-*.md](.business-rules/) - Existing domain decisions
- [Inventory Domain Rules](.business-rules/) - Business policies

**Durion Platform Standards:**
- [AGENTS.md](../../AGENTS.md) - Development guidelines
- [Copilot Instructions](../../.github/copilot-instructions.md) - Code standards
- [Code Review Instructions](../../.github/instructions/code-review-generic.instructions.md)

---

## Success Criteria

### Phase 3 Complete When:

✅ **Documentation**
- [x] inventory-questions.md created with Phase plan
- [ ] All 20 issues analyzed with blocking questions
- [ ] 50+ backend contract questions answered
- [ ] All acceptance criteria defined

✅ **GitHub Communication**
- [ ] 20 resolution comments added to durion-moqui-frontend issues
- [ ] Labels updated on all 20 issues
- [ ] Documentation links provided

✅ **Implementation Ready**
- [ ] Backend contracts finalized
- [ ] Dependency order established
- [ ] Acceptance criteria clear
- [ ] Frontend teams can begin implementation

---

## Escalation & Support

**For Domain Conflict Questions:**
- Review domains/*/DECISION-*.md files
- Escalate to domain owners (listed in AGENTS.md)
- Document resolution decision in inventory-questions.md

**For Backend Contract Questions:**
- Search durion-positivity-backend source
- Review existing controller/service patterns
- Check OpenAPI/Swagger docs if available
- Ask backend team for clarification

**For Permission Model Questions:**
- Review PERMISSION_TAXONOMY.md patterns
- Check existing @PreAuthorize annotations
- Review security/RBAC documentation

---

## Next Actions

### Immediate (Today)

1. ✅ Create inventory-questions.md - DONE
2. ✅ Create INVENTORY_PLANNING_README.md (this file) - DONE  
3. 🔄 Start Phase 1: Analyze remaining 18 issues
4. 🔄 Extract blocking questions for each
5. 🔄 Create detailed sections for 18 issues

### Short Term (This Week)

6. Execute Phase 2: Research backend contracts
7. Document findings in inventory-questions.md
8. Identify all permission models needed
9. Create state machine diagrams
10. Document identifier model decisions

### Medium Term (Next Week)

11. Add GitHub resolution comments
12. Update issue labels
13. Create label update scripts
14. Prepare Phase 4 handoff
15. Conduct implementation readiness review

---

**Plan Created:** 2026-01-25  
**Status:** 🔄 Phase 1 in Progress  
**Owner:** Durion Platform Team  
**Repository:** durion-moqui-frontend
