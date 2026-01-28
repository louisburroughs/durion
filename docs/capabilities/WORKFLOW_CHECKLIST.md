---
title: Capability Execution Workflow — Quick Reference Checklist
description: One-page checklist for executing a capability with 3 stops
---

# Quick Reference: Capability Execution Workflow

**Capability ID:** `CAP:________________` | **Parent STORY:** `durion#_______` | **Domain:** `_________________`

---

## STEP 1: Backend Contract PR (Durion)

- [ ] **YOU:** Create branch in durion: `git checkout -b cap/<CAP_ID>`
- [ ] **AGENT (Copilot):** Paste prompt into Chat (Ctrl+Shift+I) in `domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - Copilot generates contract entry (endpoint/event, examples, assertions, error cases)
- [ ] **YOU:** Review contract entry for accuracy against STORY acceptance criteria
- [ ] **YOU:** Commit & push: `git commit -m 'contract(...)'` → `git push origin cap/<CAP_ID>`
- [ ] **YOU:** Open PR on `durion` repo with **required fields:**
  - Capability: `CAP:<CAP_ID>`
  - Parent STORY: `durion#<PARENT_STORY>`
  - Contract guide entry: `[link to anchor]`
  - Status: Draft
- [ ] **YOU:** Save PR URL: `________________________________`
- [ ] **GATE:** PR must be merged before backend code PR can merge

---

## STEP 2: Backend Code PR (durion-positivity-backend)

- [ ] **YOU:** Create branch in durion-positivity-backend: `git checkout -b cap/<CAP_ID>`
- [ ] **AGENT (Copilot):** Paste prompt into Chat (Ctrl+Shift+I) in the pos-* module
  - Copilot generates: controller, service, tests (ContractBehaviorIT)
  - Includes examples from contract guide, error handling, OpenAPI annotations
- [ ] **YOU:** Run tests locally: `./mvnw -pl pos-<module> -am test` ✓ Pass
- [ ] **YOU:** Commit & push: `git commit -m 'feat(...)'` → `git push origin cap/<CAP_ID>`
- [ ] **YOU:** Open PR on `durion-positivity-backend` repo with **required fields:**
  - Capability: `cap:<CAP_ID>`
  - Parent STORY: `durion#<PARENT_STORY>`
  - Child issue: `durion-positivity-backend#<CHILD>`
  - Contract guide entry: `[link]`
  - Durion contract PR: `[link to Step 1]`
  - Status: Ready (waiting for contract PR to merge)
- [ ] **YOU:** Save PR URL: `________________________________`
- [ ] **GATE:** Do NOT merge until Step 1 (durion contract PR) is merged

---

## STEP 3: Contract Freeze

- [ ] **GATE PREREQUISITE:** Verify durion contract PR (Step 1) is **merged** ✓
- [ ] **YOU:** Pull durion master: `git checkout master && git pull origin master`
- [ ] **YOU:** Open `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- [ ] **YOU:** Find your story entry and change marker:
  ```
  contract-status: draft  →  contract-status: stable-for-ui
  ```
- [ ] **YOU:** Commit & push: `git commit -m 'contract(cap/<CAP_ID>): freeze...'` → `git push origin master`
- [ ] **OPTIONAL:** If backend PR review changed the contract, update guide & revert to draft, re-request review

---

## 🛑 STOP #1: Backend Merge Gate

**BEFORE proceeding, manually verify & merge the backend PR:**

- [ ] Go to backend PR: `________________________________`
- [ ] **Verify:**
  - ✓ All CI checks pass (tests, lint, contract-sync if configured)
  - ✓ Code review approved
  - ✓ No merge conflicts (GitHub shows "Mergeable")
  - ✓ Contract PR is merged (check PR body link)
- [ ] **Merge** the PR in GitHub UI (squash recommended)
- [ ] **Confirm** merge in terminal:
  ```bash
  cd durion-positivity-backend
  git checkout main && git pull origin main
  git log --oneline | head -3  # Verify your commit is there
  ```

**✓ Ready to proceed with frontend?** → Continue to Step 4

---

## STEP 4: Frontend PR (durion-moqui-frontend)

- [ ] **YOU:** Create branch: `git checkout -b cap/<CAP_ID>`
- [ ] **AGENT (Copilot):** Paste prompt into Chat (Ctrl+Shift+I) in component or runtime
  - Copilot generates: Vue components, service/composable, Jest tests
  - Includes API integration, form validation, error handling
- [ ] **YOU:** Run tests locally: `npm test` ✓ Pass
- [ ] **YOU:** Run linting: `npm run lint` ✓ Clean
- [ ] **YOU:** Run type-check: `npm run type-check` ✓ Clean
- [ ] **YOU:** Commit & push: `git commit -m 'feat(...)'` → `git push origin cap/<CAP_ID>`
- [ ] **YOU:** Open PR on `durion-moqui-frontend` repo with **required fields:**
  - Capability: `cap:<CAP_ID>`
  - Parent STORY: `durion#<PARENT_STORY>`
  - Child issue: `durion-moqui-frontend#<CHILD>`
  - Contract guide entry: `[link]`
  - Backend PR: `[link to Step 2, should be merged]`
  - Wireframes: `[link]`
- [ ] **YOU:** Save PR URL: `________________________________`
- [ ] **GATE:** Do NOT merge until Step 3 (contract freeze) is complete

---

## 🛑 STOP #2: Frontend Merge Gate

**BEFORE proceeding, manually verify & merge the frontend PR:**

- [ ] Go to frontend PR: `________________________________`
- [ ] **Verify:**
  - ✓ All CI checks pass (Jest, lint, type-check)
  - ✓ Code review approved
  - ✓ No merge conflicts (GitHub shows "Mergeable")
  - ✓ Backend PR is merged (check PR body link)
- [ ] **Merge** the PR in GitHub UI (squash recommended)
- [ ] **Confirm** merge in terminal:
  ```bash
  cd durion-moqui-frontend
  git checkout main && git pull origin main
  git log --oneline | head -3  # Verify your commit is there
  ```

**✓ Ready for integration testing?** → Continue to Step 5

---

## STEP 5: Integration Completion

### 5a: Spin Up Local Stack

Choose one:

**Option A: Docker Compose (Recommended)**
```bash
cd durion-moqui-frontend
docker-compose -f docker/moqui-postgres-compose.yml up -d
# Wait 2-3 minutes
curl http://localhost:8080/webroot/  # Verify Moqui is up
```

**Option B: Manual Startup**
```bash
# Terminal 1
cd durion-positivity-backend
./mvnw -pl pos-api-gateway -am spring-boot:run

# Terminal 2
cd durion-moqui-frontend
./gradlew build -x test
java -jar runtime/build/libs/moqui.war

# Open browser
open http://localhost:8080/webroot/
```

### 5b: Test User Journey

- [ ] Navigate to the screen/feature you implemented
- [ ] Follow acceptance criteria from durion#<PARENT_STORY>
- [ ] Test **happy path:**
  - [ ] Form submission succeeds
  - [ ] Response matches contract examples
  - [ ] Data displays correctly in UI
- [ ] Test **error cases:**
  - [ ] Invalid input → validation error displayed correctly
  - [ ] Auth error → error handling works
  - [ ] Other contract error codes → handled gracefully
- [ ] Check **browser console:** No JS errors
- [ ] Check **backend logs:** Expected traces/debug output

### 5c: Update & Close Story

- [ ] Go to durion#<PARENT_STORY>
- [ ] Add/update checklist:
  ```
  - [x] Contract PR merged: [link]
  - [x] Backend PR merged: [link]
  - [x] Frontend PR merged: [link]
  - [x] Integration tested & verified
  ```
- [ ] Close issue or move to "Done" in project board

### 5d: Cleanup (When Capability Complete)

If this was the **last story** in `cap:<CAP_ID>`:
```bash
# Remove local branches
cd durion && git branch -d cap/<CAP_ID>
cd durion-positivity-backend && git branch -d cap/<CAP_ID>
cd durion-moqui-frontend && git branch -d cap/<CAP_ID>

# Remove remote branches
git push origin --delete cap/<CAP_ID> # Run in each repo
```

---

## 🛑 STOP #3: Integration Testing Complete

**AFTER testing is verified:**

- [ ] All acceptance criteria from STORY met ✓
- [ ] No browser console errors ✓
- [ ] All contract examples verified in actual API responses ✓
- [ ] Error handling tested ✓

**Capability `cap:<CAP_ID>` is COMPLETE and DELIVERED.**

---

## Summary

| Step | Deliverable | Merger | Blocker |
|------|-------------|--------|---------|
| 1 | Durion contract PR | You | — |
| 2 | Backend code PR | You | Step 1 merged |
| 🛑 | **STOP #1:** Merge backend PR | You | Tests + review ✓ |
| 3 | Contract freeze (stable-for-ui) | You | Step 2 code review done |
| 4 | Frontend PR | You | Step 3 complete |
| 🛑 | **STOP #2:** Merge frontend PR | You | Tests + review ✓ |
| 5 | Integration testing | You | Step 4 merged |
| 🛑 | **STOP #3:** Close story | You | Testing done |

---

## Key Contacts

- **Domain expert:** `_______________________`
- **Frontend lead:** `_______________________`
- **Backend lead:** `_______________________`
- **Project coordinator:** `_______________________`

---

**For detailed guidance, see:** `docs/capabilities/WORKFLOW_GUIDE.md`
