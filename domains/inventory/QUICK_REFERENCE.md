# Inventory Domain Phase 3 - Quick Reference Card

## 🎯 Mission
Unblock ALL 20 inventory domain issues with `blocked:clarification` status through systematic backend contract discovery and GitHub issue resolution.

## 📊 The Numbers
- **Issues:** 20 total
- **Blocking Questions:** 50+ to resolve  
- **Domain Conflicts:** 6 issues (need reassignment)
- **Core Workflows:** 6 high-priority issues
- **Timeline:** 4-6 days (4 phases)

## 📁 Key Documents

| File | Purpose | Size | Role |
|------|---------|------|------|
| [inventory-questions.md](inventory-questions.md) | Main Phase 3 plan | 27 KB | Everyone |
| [INVENTORY_PLANNING_README.md](INVENTORY_PLANNING_README.md) | Execution guide | 14 KB | Phase owners |
| [PHASE_3_SUMMARY.md](PHASE_3_SUMMARY.md) | This summary | 15 KB | Quick reference |
| [.business-rules/](https://github.com/louisburroughs/durion/tree/master/domains/inventory/.business-rules) | Domain decisions | — | Research |

## 🔄 Four Phases

### Phase 1: Assessment (🔄 IN PROGRESS)
**Duration:** 1-2 days | **Owner:** Platform Agent  
**Task:** Extract 50+ blocking questions from all 20 issues  
**Progress:** 2/20 issues analyzed (10%)

- [ ] Analyze remaining 18 issues
- [ ] Categorize blocking questions by type
- [ ] Identify domain conflicts
- [ ] Update inventory-questions.md

### Phase 2: Backend Discovery (🔴 BLOCKED)
**Duration:** 2-3 days | **Owner:** Backend Research  
**Task:** Research durion-positivity-backend and document contracts  
**Progress:** Awaiting Phase 1 completion

- [ ] Search pos-inventory, pos-order, pos-workexec modules
- [ ] Document entity schemas
- [ ] Extract REST endpoint definitions
- [ ] Identify permission models
- [ ] Answer all 50+ backend questions

### Phase 3: GitHub Communication (🔴 BLOCKED)
**Duration:** 1-2 days | **Owner:** Communication Agent  
**Task:** Add Phase 3 findings to 20 GitHub issues  
**Progress:** Awaiting Phase 2 completion

- [ ] Create comment template
- [ ] Add findings comments to 20 issues
- [ ] Remove `blocked:clarification` labels
- [ ] Add `status:resolved` labels

### Phase 4: Handoff (🔴 BLOCKED)
**Duration:** 1-2 days | **Owner:** Documentation  
**Task:** Finalize docs and prepare implementation  
**Progress:** Awaiting Phase 3 completion

- [ ] Complete inventory-questions.md
- [ ] Define acceptance criteria
- [ ] Document dependencies
- [ ] Create label update scripts
- [ ] Prepare implementation roadmap

## 🗂️ Issue Categories

### 🔴 Domain Conflicts (6 Issues)
Need reassignment to correct domain:
- #260 - Supplier/Vendor Cost Tiers → **pricing**
- #244 - Mechanic Executes Picking → **workexec**
- #243 - Issue/Consume Items → **workexec**
- #242 - Return Unused Items → **workexec**
- #121 - Create Product Record → **product**
- #120 - Manage UOM Conversions → **product**
- #119 - Product Lifecycle State → **product**
- #81 - Search Catalog → **product**

### 🟠 Critical Path (6 Issues - P1)
Foundation and core workflows:
- #87 - Define Inventory Roles & Permissions (**P0 - Foundation**)
- #99 - Receiving Session from PO/ASN (**P1**)
- #96 - Put-away Tasks from Staging (**P1**)
- #93 - Reserve/Allocate Stock to Workorder (**P1**)
- #92 - Create Pick List/Tasks (**P1**)
- #91 - Execute Cycle Count (**P1**)

### 🟡 Important (6 Issues - P2+)
Advanced features and optional items:
- #241 - Plan Cycle Counts (**P2**)
- #97 - Direct-to-Workorder Receiving (**P2**)
- #108 - Fitment Hints & Vehicle Tags (**P2**)
- #94 - Replenish Pick Faces (**P3 - Optional**)
- #90 - Approve Adjustments (**P2**)
- #88 - Reallocate Reserved Stock (**P3 - Optional**)

## 🔍 Question Categories

### 1️⃣ Domain Ownership
**How many:** 6 issues  
**Question:** Inventory or Product/Pricing/WorkExec?  
**Action:** Escalate for governance decision

### 2️⃣ Backend Contracts
**How many:** 18 issues | 50+ questions  
**Question:** What endpoints, entities, payloads?  
**Action:** Research durion-positivity-backend source code

### 3️⃣ Permissions
**How many:** 10+ issues  
**Question:** What permission tokens required?  
**Action:** Review PERMISSION_TAXONOMY.md patterns

### 4️⃣ Identifiers
**How many:** 8+ issues  
**Question:** Canonical key choices?  
**Action:** Align with backend entity definitions

### 5️⃣ State Machines
**How many:** 10+ issues  
**Question:** What states and transitions?  
**Action:** Document from backend code or decision docs

## 📋 Issue #260 (Supplier/Vendor Cost Tiers) - Example

**8 Blocking Questions:**
1. 🔴 Domain ownership (inventory vs pricing)
2. 🔴 Backend contract/endpoints
3. 🔴 Permissions model
4. 🔴 Identifier model (itemId vs productId)
5. 🔴 Currency rules
6. 🔴 Base cost support
7. 🔴 Numeric precision
8. 🔴 Optimistic locking

**Status:** Detailed analysis complete (inventory-questions.md#issue-260)

## 📋 Issue #244 (Mechanic Executes Picking) - Example

**9 Blocking Questions:**
1. 🔴 Domain ownership (inventory vs workexec)
2. 🔴 Backend contract/endpoints
3. 🔴 Route identifier (workorderId vs pickTaskId)
4. 🔴 Scan semantics (what can be scanned)
5. 🔴 Multi-match handling
6. 🔴 Quantity rules (partial picks allowed?)
7. 🔴 Serial/lot control
8. 🔴 Permissions model
9. 🔴 State machine definition

**Status:** Detailed analysis complete (inventory-questions.md#issue-244)

## 👥 Role Playbook

### Phase 1 Agent
**Time Needed:** ~1-2 hours per day  
**Tasks:**
1. Open [inventory-questions.md](inventory-questions.md)
2. Work through issues #243-#81 in order
3. Extract blocking questions (copy format from #260/#244)
4. Update status: 🔴 BLOCKING → 🟡 IN PROGRESS → 🟢 RESOLVED
5. Commit changes to inventory-questions.md

### Backend Research Team
**Time Needed:** ~2-3 hours per day  
**Tasks:**
1. Clone durion-positivity-backend repo
2. Search for entities by name:
   - SupplierCost → #260
   - PickTask → #244
   - etc.
3. Document in inventory-questions.md:
   - Entity schemas
   - REST endpoints
   - Validation rules
   - Error codes
4. Add source links

### GitHub Communication Team
**Time Needed:** ~1 hour total  
**Tasks:**
1. Create comment template
2. For each of 20 issues:
   - Extract key findings (5-10 points)
   - Add GitHub comment
   - Update labels
   - Link to documentation

### Documentation Team
**Time Needed:** ~2-3 hours  
**Tasks:**
1. Finalize inventory-questions.md
2. Create Durion-Processing.md
3. Generate label update scripts
4. Prepare Phase 4 roadmap

## 🚀 Quick Start for Phase 1 Agent

```bash
# 1. Navigate to inventory domain
cd $WORKSPACE/durion/domains/inventory

# 2. Open the main planning document
cat inventory-questions.md

# 3. Review completed analysis (as examples)
# - See Issue #260 section (8 questions detailed)
# - See Issue #244 section (9 questions detailed)

# 4. Start analyzing Issue #243
# - Copy structure from #260/#244
# - Extract blocking questions
# - Review domain-inventory.txt for raw story text

# 5. Commit progress
git add inventory-questions.md
git commit -m "Phase 1: Analyze Issue #243 - Extract blocking questions"
```

## ✅ Success Criteria

### Phase 1 Done When:
- ✅ All 20 issues have "Open Questions (Blocking)" sections
- ✅ 50+ questions extracted and status-marked
- ✅ Domain conflicts identified
- ✅ Detailed analysis complete for all 20 issues

### Phase 2 Done When:
- ✅ 50+ backend contract questions answered
- ✅ Source links provided (entity files, endpoints)
- ✅ All schemas documented
- ✅ Permission models identified

### Phase 3 Done When:
- ✅ 20 GitHub comments added
- ✅ Labels updated on all 20 issues
- ✅ Documentation linked in each issue

### Phase 4 Done When:
- ✅ Implementation roadmap ready
- ✅ Teams can start Phase 4 implementation
- ✅ No more `blocked:clarification` issues

## 🔗 Quick Links

| Link | Purpose |
|------|---------|
| [inventory-questions.md](inventory-questions.md) | Main plan (START HERE) |
| [INVENTORY_PLANNING_README.md](INVENTORY_PLANNING_README.md) | How to execute |
| [PHASE_3_SUMMARY.md](PHASE_3_SUMMARY.md) | This document |
| [Accounting Ref](../accounting/accounting-questions.md) | Example of completed Phase 3 |
| [GitHub Issues](https://github.com/louisburroughs/durion-moqui-frontend/issues?q=label:domain:inventory+label:blocked:clarification) | Issues to update |
| [Backend Repo](https://github.com/louisburroughs/durion-positivity-backend) | Source to research |

## ⏱️ Timeline

| Date | Milestone | Status |
|------|-----------|--------|
| 2026-01-25 | Documents created | ✅ Complete |
| 2026-01-26 | Phase 1: Analysis | 🔄 In Progress |
| 2026-01-27 | Phase 2: Backend Research | 🔄 Pending |
| 2026-01-28 | Phase 3: GitHub Comments | 🔄 Pending |
| 2026-01-29 | Phase 4: Handoff | 🔄 Pending |
| 2026-01-30 | Phase 4 Implementation Ready | 🔄 Pending |

## 📞 Need Help?

- **Questions about plan?** Review [INVENTORY_PLANNING_README.md](INVENTORY_PLANNING_README.md)
- **Need backend patterns?** Check [accounting-questions.md](../accounting/accounting-questions.md)
- **Domain decision?** Look in [.business-rules/](.business-rules/)
- **Code standards?** See [AGENTS.md](../../AGENTS.md)
- **Not sure where to start?** Go to [inventory-questions.md](inventory-questions.md#phase-1-issue-assessment--categorization-current)

---

**Status:** ✅ Ready for Phase 1 Execution  
**Created:** 2026-01-25  
**Owner:** Durion Platform Team
