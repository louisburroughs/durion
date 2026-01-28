---
title: Capability Execution Workflow — Script & Instructions
description: Step-by-step guide for executing a capability build with minimal stops and clear role demarcation
---

## Overview

This document accompanies `scripts/capability-execution-workflow.sh`. It explains:

- **Role demarcation** (you vs. agents)
- **How to invoke agents** (IDE, command-line, GitHub)
- **Mandatory stops** (3 total: backend merge, frontend merge, integration verification)
- **Specific prompts** for Copilot to minimize context-switching and rework

The goal is a **fast, deterministic capability build** with minimal back-and-forth.

---

## Capability Documentation Structure

Before starting, create a capability directory and manifest to organize your work:

```
durion/docs/capabilities/
└── cap:my-feature/
    ├── CAPABILITY_MANIFEST.yaml          ← Single source of truth for all repos/stories/contracts
    ├── STORIES.md                        ← Story list & status tracking
    ├── DECISION_LOG.md                   ← Decisions, blockers, lessons learned
    └── README.md                         ← Overview & quick links
```

### Capability Manifest (`CAPABILITY_MANIFEST.yaml`)

### Story Tracking (`STORIES.md`)

Track each parent STORY's progress:
```markdown
# cap:my-feature Stories

## Story 1: durion#123 — Create order
- [ ] Contract PR open
- [ ] Contract PR merged
- [ ] Backend PR open
- [ ] Backend PR merged
- [ ] Frontend PR open
- [ ] Frontend PR merged
- [ ] Integration tested

## Story 2: durion#124 — Cancel order
...
```

### Decision Log (`DECISION_LOG.md`)

Document decisions as you go:
```markdown
## Merge Decisions

### Backend PR #456 — 2026-01-27
- Decision: Merged with squash
- Reason: Contract stable, all tests pass
- Impact: Frontend can proceed

### Contract Entry Change — 2026-01-27
- Decision: Reverted contract to draft
- Reason: Backend implementation revealed new error case
- Impact: Delayed frontend start by 1 day
- Resolution: Re-froze contract to stable-for-ui after fix
```

---

## Quick Start

```bash
cd durion

# 1. Create capability directory and manifest
mkdir -p docs/capabilities/cap:my-feature
cp docs/capabilities/_TEMPLATE_/CAPABILITY_MANIFEST.yaml \
   docs/capabilities/cap:my-feature/CAPABILITY_MANIFEST.yaml

# 2. Run the workflow script
bash scripts/capability-execution-workflow.sh cap:my-feature 123
```

The script will guide you through all 5 steps interactively.

**Pro tip:** Reference your manifest in Copilot prompts:
```
See durion/docs/capabilities/CAP-XXX/CAPABILITY_MANIFEST.yaml
for the full story list, impacted repos, and contract locations.
```

---

## Workflow Overview (High Level)

| Step | Actor | Deliverable | Blocking | Notes |
|------|-------|-------------|----------|-------|
| 1 | Agent + You | Durion contract PR | ❌ No | Declares intent; can proceed in parallel with step 2 |
| 2 | Agent + You | Backend code PR | ✅ Wait for Step 1 merge | Cannot merge until step 1 merges |
| 3 | You | Contract freeze + guide update | ✅ Wait for Step 2 merge | Update contract-status to stable-for-ui |
| 4 | Agent + You | Frontend PR | ✅ Wait for Step 3 | Frontend only starts when contract is stable |
| 5 | You | Integration testing | ✅ Wait for Step 4 merge | Manual verification + story checklist |

---

## Step-by-Step Details

### Step 1: Backend Contract PR (Durion) — **FIRST**

**What:** Declare the contract in `durion/domains/{domain}/.business-rules/BACKEND_CONTRACT_GUIDE.md`

**Why:** This is the single source of truth for the API/event shape, behavior, and error handling before any code is written.

#### User Actions

1. **Open durion repo** in VS Code
2. **Create branch:**
   ```bash
   git checkout -b cap/<cap-id>
   ```
3. **Locate or create the contract guide anchor** in `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`  (## Entity-Specific Contracts)
4. **Fill in your CAPABILITY_MANIFEST.yaml** with contract guide path & anchor location (if not done already)

#### Agent Action (Copilot)

**Trigger:** Copilot Chat in VS Code (Ctrl+Shift+I) while positioned in the contract guide file

**Prompt to paste:**
```
You are working on capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).

I need you to add/update a contract guide entry for this story in this backend contract guide file.

Include:
1. Story linkage (durion#PARENT_STORY_NUMBER, backend child issue link)
2. Endpoint or event name
3. HTTP method + path (if API) or event topic (if event-driven)
4. Request/response shape examples
5. Behavioral assertions (always-true invariants, e.g., "order.total must equal sum(items)")
6. Error cases (validation, auth, idempotency)
7. Marker: contract-status: draft

Use the existing guide format as a template. Keep examples realistic and concise.
```

**Expected Output:**
- A well-formatted entry with endpoint/event, examples, assertions, and draft marker
- Examples use realistic sample data
- Assertions map to acceptance criteria from the parent STORY

#### User Actions (After Agent)

1. **Review** the generated entry for accuracy
2. **Commit locally:**
   ```bash
   git add . && git commit -m 'contract(cap/<cap-id>): declare contract for story durion#<PARENT_STORY>'
   ```
3. **Push & open PR:**
   ```bash
   git push origin cap/<cap-id>
   ```
   - **Title:** `contract(cap/<cap-id>): declare contract for story durion#<PARENT_STORY>`
   - **Body (required fields):**
     ```
     Capability: cap:<cap-id>
     Parent STORY: durion#<PARENT_STORY>
     Contract guide entry: [link to anchor in BACKEND_CONTRACT_GUIDE.md]
     
     This PR declares the API/event contract before backend implementation begins.
     
     - Endpoint/event: [name]
     - Method/topic: [POST, GET, etc. or topic]
     - Status codes: [list]
     - Error model: [describes error response shape]
     - Behavioral invariants: [from assertions section]
     
     Backend PR will reference this contract. Frontend will wait for contract to be frozen (stable-for-ui).
     ```

---

### Step 2: Backend Code PR(s)

**What:** Implement the API endpoint or event handler in the pos-* module(s)

**Why:** Backend logic is implemented to match the contract declared in step 1.

**Timing:** Can be opened in parallel with step 1, but **cannot merge** until step 1 is merged.

#### User Actions

1. **Open durion-positivity-backend repo** in VS Code
2. **Create branch:**
   ```bash
   git checkout -b cap/<cap-id>
   ```
3. **Navigate to the relevant pos-* module** (e.g., `pos-order/`, `pos-inventory/`, etc.)

#### Agent Action (Copilot)

**Trigger:** Copilot Chat in VS Code (Ctrl+Shift+I) from within the pos-* module

**Prompt to paste:**
```
You are implementing capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).
Backend child issue: <repo>#CHILD_ISSUE_NUMBER (replace with actual number).

Contract guide entry (stable reference):
  durion repo, domains/DOMAIN_NAME/.business-rules/BACKEND_CONTRACT_GUIDE.md
  Anchor: [describe or link the specific section]

I need you to:
1. Implement the endpoint/service to match the contract
2. Add provider behavioral contract tests (ContractBehaviorIT)
3. Include examples from the contract guide in the tests
4. Add validation & error handling per the assertions
5. Include concurrency-safe patterns if needed (idempotency, optimistic locking)

Follow existing pos-* module patterns for:
  - Controller layer (keep thin, delegate to service)
  - Service layer (orchestration, business logic)
  - Repository layer (Spring Data JPA)
  - Entity/DTO shapes

Module structure:
  pos-*/src/main/java/com/durion/pos*/controller/
  pos-*/src/main/java/com/durion/pos*/service/
  pos-*/src/main/java/com/durion/pos*/repository/
  pos-*/src/test/java/com/durion/pos*/contract/

Tests: Follow the ContractBehaviorIT pattern:
  - Happy path (from examples in guide)
  - Validation errors
  - Auth/permission errors (if applicable)
  - Idempotency (if applicable)
  - Concurrency-safe invariants

Add or update OpenAPI annotations (@Operation, @ApiResponse, etc.) if the module exposes REST.
```

**Expected Output:**
- Full endpoint implementation (controller + service + repository)
- ContractBehaviorIT test class with scenarios from the contract guide
- OpenAPI annotations (if REST)
- Validation & error handling

#### User Actions (After Agent)

1. **Run tests locally:**
   ```bash
   cd durion-positivity-backend
   ./mvnw -pl pos-<module> -am test
   ```
   Verify all tests pass.

2. **Check build:**
   ```bash
   ./mvnw -pl pos-<module> -am clean compile
   ```

3. **Commit & push:**
   ```bash
   git add . && git commit -m 'feat(cap/<cap-id>): implement story durion#<PARENT_STORY>'
   git push origin cap/<cap-id>
   ```

4. **Open PR on durion-positivity-backend:**
   - **Title:** `feat(cap/<cap-id>): implement story durion#<PARENT_STORY>`
   - **Body (required):**
     ```
     Capability: cap:<cap-id>
     Parent STORY: durion#<PARENT_STORY>
     Child issue: durion-positivity-backend#<CHILD_BACKEND_ISSUE>
     
     Contract guide entry: [link to durion BACKEND_CONTRACT_GUIDE.md#anchor]
     Durion contract PR: [link to step 1 PR]
     
     This PR implements the backend logic declared in the contract guide.
     
     - Endpoint: [method + path]
     - Tests: ContractBehaviorIT covers happy path + error cases from contract
     - Status: Ready for review (waiting for contract PR to merge before merging this)
     
     Do not merge until durion contract PR is merged.
     ```

---

### Step 3: Contract Freeze

**What:** Verify contract is stable; update contract-status marker to `stable-for-ui`

**Why:** Freezing the contract signals to frontend that the API is ready to consume.

**Timing:** Only after durion contract PR is merged and backend PR is code-reviewed.

#### User Actions

1. **Go to durion repo, pull latest master:**
   ```bash
   cd durion
   git checkout master && git pull origin master
   ```

2. **Locate your contract entry** in `domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`

3. **Change the status marker** from `contract-status: draft` to `contract-status: stable-for-ui`
   ```markdown
   ### Story: durion#<PARENT_STORY>
   
   contract-status: stable-for-ui  ← CHANGE THIS
   ```

4. **Commit & push:**
   ```bash
   git commit -m 'contract(cap/<cap-id>): freeze contract to stable-for-ui'
   git push origin master
   ```

#### Optional: Rebase Backend PR if Contract Changed

If the backend code review revealed changes to the contract (examples, assertions, error codes), you must:

1. **Update the contract guide entry** with the final examples/assertions
2. **Revert contract-status to `draft`** if significant changes
3. **Update backend PR** with the revised examples and re-request review
4. **Re-freeze** (set to `stable-for-ui`) only after backend PR is approved again

---

### Step 4: Frontend PR(s)

**What:** Implement UI screens and wire to the backend API

**Why:** Frontend consumes the now-stable contract.

**Timing:** Starts only after contract is `stable-for-ui`.

#### User Actions

1. **Open durion-moqui-frontend repo** (or component-specific repo) in VS Code
2. **Create branch:**
   ```bash
   git checkout -b cap/<cap-id>
   ```

#### Agent Action (Copilot)

**Trigger:** Copilot Chat in VS Code (Ctrl+Shift+I) within the component or runtime directory

**Prompt to paste:**
```
You are implementing capability CAP_ID (replace with actual ID).
Parent STORY: durion#PARENT_STORY_NUMBER (replace with actual number).
Frontend child issue: <repo>#CHILD_ISSUE_NUMBER (replace with actual number).

Contract guide entry (stable-for-ui):
  durion repo, domains/DOMAIN_NAME/.business-rules/BACKEND_CONTRACT_GUIDE.md
  Anchor: [describe or link the specific section]

Backend API endpoint/event is now implemented and available:
  Backend PR: [link to backend PR or merged commit]

I need you to:
1. Consume the backend API/event in the frontend
2. Implement UI screens/pages matching the wireframes
3. Wire form submissions, validation, and error handling
4. Add UI tests (Jest) for key user flows
5. Ensure error handling matches backend error model (shape, codes, messages)

Frontend stack:
  - Vue 3 (Composition API)
  - Quasar for components
  - TypeScript 5
  - Test framework: Jest (or configured in package.json)

Patterns to follow:
  - Services layer (composables or service classes) for API calls
  - Components for UI (keep presentation logic in components, business in services)
  - Use existing API bridge (runtime/component/durion-positivity/) for backend calls
  - Type-safe: ensure TypeScript types match contract DTO/API shapes

Wireframes/acceptance criteria:
  [Reference the wireframe path or acceptance criteria from the parent STORY]

Examples from contract (for testing):
  [Paste the examples from the backend contract guide, or reference the guide link]
```

**Expected Output:**
- Vue 3 component(s) for the UI
- Service/composable for API calls
- Form validation and error handling
- Jest tests for user flows

#### User Actions (After Agent)

1. **Run tests:**
   ```bash
   cd durion-moqui-frontend
   npm test
   ```

2. **Lint & type-check:**
   ```bash
   npm run lint
   npm run type-check
   ```

3. **Build (optional local test):**
   ```bash
   npm run build
   ```

4. **Commit & push:**
   ```bash
   git add . && git commit -m 'feat(cap/<cap-id>): implement UI for story durion#<PARENT_STORY>'
   git push origin cap/<cap-id>
   ```

5. **Open PR on durion-moqui-frontend:**
   - **Title:** `feat(cap/<cap-id>): implement UI for story durion#<PARENT_STORY>`
   - **Body (required):**
     ```
     Capability: cap:<cap-id>
     Parent STORY: durion#<PARENT_STORY>
     Child issue: durion-moqui-frontend#<CHILD_FRONTEND_ISSUE>
     
     Contract guide entry: [link to durion BACKEND_CONTRACT_GUIDE.md#anchor]
     Backend PR: [link to backend PR, should be merged]
     Wireframes: [link to wireframe file or design doc]
     
     This PR implements the UI according to the contract and wireframes.
     
     - Screens: [list screens/pages]
     - API integration: Uses [endpoint/topic from contract]
     - Error handling: Handles all error codes from contract
     - Tests: Jest tests for happy path + error cases
     
     Backend PR is merged and API is stable.
     ```

---

### Step 5: Integration Completion

**What:** Verify all PRs are merged, run integration tests, close the parent STORY

**Why:** Ensures the full feature works end-to-end

**Timing:** After both backend and frontend PRs are approved

#### User Actions: STOP #1 (Backend Merge Gate)

Before merging the backend PR, verify:

1. **All CI checks pass:**
   - Unit tests (ContractBehaviorIT)
   - Lint
   - `contract-sync` check (if configured) — ensures contract PR link is present

2. **Code review:** At least one approval from domain expert or codeowner

3. **No conflicts:** GitHub shows "Mergeable"

4. **Contract PR is merged:** Check the PR description link; contract PR must be merged first

5. **Merge the backend PR:**
   ```
   (In GitHub UI)
   1. Click "Merge pull request"
   2. Choose merge strategy (squash recommended for clean history)
   3. Confirm
   ```

6. **Confirm merge:**
   ```bash
   (In terminal)
   cd durion-positivity-backend
   git checkout main && git pull origin main
   git log --oneline | head -5  # Verify your commit is there
   ```

#### User Actions: STOP #2 (Frontend Merge Gate)

Before merging the frontend PR, verify:

1. **All CI checks pass:**
   - Jest tests
   - Lint (`npm run lint`)
   - Type-check (`npm run type-check`)

2. **Code review:** At least one approval

3. **No conflicts:** GitHub shows "Mergeable"

4. **Backend PR is merged:** Check the PR description link; backend PR must be merged first

5. **Merge the frontend PR:**
   ```
   (In GitHub UI)
   1. Click "Merge pull request"
   2. Choose merge strategy (squash recommended)
   3. Confirm
   ```

6. **Confirm merge:**
   ```bash
   (In terminal)
   cd durion-moqui-frontend
   git checkout main && git pull origin main
   git log --oneline | head -5  # Verify your commit is there
   ```

#### User Actions: STOP #3 (Integration Testing)

Verify the feature works end-to-end:

1. **Spin up local stack** (if not already running):

   **Option A: Docker Compose (recommended)**
   ```bash
   cd durion-moqui-frontend
   docker-compose -f docker/moqui-postgres-compose.yml up -d
   # Wait for containers to start (2-3 minutes)
   curl http://localhost:8080/webroot/  # Verify Moqui is up
   ```

   **Option B: Manual (for quick testing)**
   ```bash
   # Terminal 1: Backend
   cd durion-positivity-backend
   ./mvnw -pl pos-api-gateway -am spring-boot:run
   
   # Terminal 2: Frontend
   cd durion-moqui-frontend
   ./gradlew build -x test
   java -jar runtime/build/libs/moqui.war
   
   # Wait for startup
   open http://localhost:8080/webroot/
   ```

2. **Test the user journey:**
   - Navigate to the screen/feature you implemented
   - Follow the acceptance criteria from the parent STORY
   - Test happy path + error cases (invalid input, auth errors, etc.)
   - Verify responses match contract examples
   - Check UI displays errors correctly

3. **Update parent STORY:**
   - Go to durion#PARENT_STORY
   - Add checklist items (if not already present):
     ```
     - [x] Contract PR merged: <link>
     - [x] Backend PR merged: <link>
     - [x] Frontend PR merged: <link>
     - [x] Integration tested
     ```
   - Close the issue (or move to "Done" in the project board)

4. **Archive capability** (when all stories are complete):
   - If this was the last story in cap:<cap-id>, remove the branches:
     ```bash
     cd durion-positivity-backend
     git branch -d cap/<cap-id> && git push origin --delete cap/<cap-id>
     
     cd durion-moqui-frontend
     git branch -d cap/<cap-id> && git push origin --delete cap/<cap-id>
     
     cd durion
     git branch -d cap/<cap-id> && git push origin --delete cap/<cap-id>
     ```

---

## Summary: Role & Trigger Matrix

| Task | Actor | Trigger | How | Outputs |
|------|-------|---------|-----|---------|
| Write contract entry | Copilot | Chat in VS Code | Paste the prompt | BACKEND_CONTRACT_GUIDE.md entry (draft) |
| Implement backend | Copilot | Chat in VS Code | Paste the prompt | Controllers, services, tests |
| Review & test | You | Local terminal | `./mvnw -pl pos-<module> -am test` | Passing tests, clean build |
| Commit & PR | You | Terminal + GitHub | `git push` + GitHub UI | PR on durion-positivity-backend |
| Freeze contract | You | Editor + terminal | Edit marker, git push | Contract marked stable-for-ui |
| Implement frontend | Copilot | Chat in VS Code | Paste the prompt | Vue components, services, tests |
| Review & test | You | Local terminal | `npm test`, `npm run lint` | Passing tests, no lint errors |
| Commit & PR | You | Terminal + GitHub | `git push` + GitHub UI | PR on durion-moqui-frontend |
| Merge gates | You | GitHub UI | Verify checks, click merge | PRs merged to main |
| Integration test | You | Local stack + browser | Spin up stack, manual test | Verified end-to-end |

---

## Capability Documentation — How to Use

### During Execution

**CAPABILITY_MANIFEST.yaml** — Update as you progress:
```yaml
pr_links:
  durion_contract_pr: "https://github.com/louisburroughs/durion/pull/456"
  backend_pr: "https://github.com/louisburroughs/durion-positivity-backend/pull/789"
  frontend_prs:
    - "https://github.com/louisburroughs/durion-moqui-frontend/pull/1011"
```

**STORIES.md** — Checkbox what's done:
```markdown
## Story 1: durion#123
- [x] Contract PR merged
- [x] Backend PR merged
- [x] Backend PR code reviewed & approved
- [ ] Frontend PR open
- [ ] Frontend PR merged
- [ ] Integration tested
```

**DECISION_LOG.md** — Log merge gates & changes:
```markdown
## Merge Decisions

### STOP #1: Backend PR #789 — 2026-01-27 10:15 AM
✓ Merged with squash
- All CI checks passed
- Contract PR merged
- No conflicts

### Contract Status Change — 2026-01-27 10:20 AM
✓ Froze contract to stable-for-ui
- No changes from initial draft
- Ready for frontend

### STOP #2: Frontend PR #1011 — 2026-01-27 03:45 PM
✓ Merged with squash
- Jest tests pass
- Linting clean
- Backend PR was merged
```

### After Capability Complete

Archive your capability directory with final status:
```markdown
# cap:my-feature — COMPLETE

**Delivered:** 2026-01-27
**Stories completed:** 3 of 3
**PRs merged:** 6 (1 contract + 2 backend + 3 frontend)
**Integration verified:** ✓

## Key Dates
- Contract PR merged: 2026-01-27 08:00 AM
- Backend PR merged: 2026-01-27 10:30 AM
- Frontend PR merged: 2026-01-27 04:00 PM
- Integration testing complete: 2026-01-27 05:30 PM

## Lessons Learned
- [Document any discoveries, blockers, or process improvements]

## Artifacts
- Manifest: `CAPABILITY_MANIFEST.yaml`
- Stories: `STORIES.md`
- Decisions: `DECISION_LOG.md`
- Contract PRs: [links]
- Backend PRs: [links]
- Frontend PRs: [links]
```

### Sharing with Agents

When using Copilot, reference the manifest to reduce context-switching:

**In the prompt:**
```
Capability ID: cap:my-feature
For full context, see: durion/docs/capabilities/cap:my-feature/CAPABILITY_MANIFEST.yaml

This file contains:
- All parent STORY links (durion#)
- All child issue links (backend & frontend)
- Contract guide path & anchor
- Impacted repos & modules
- PR tracking
```

Then paste only the **relevant excerpt** from the manifest (not the whole file) to keep the prompt focused.

---

## Tips for Minimal Stops

1. **Parallelize steps 1 & 2:**
   - Open durion contract PR at the same time as backend PR
   - Backend PR just can't merge until contract PR merges
   - This saves 1 round-trip

2. **Freeze contract ASAP:**
   - As soon as contract PR is approved, update the marker
   - Frontend can start immediately after (no waiting for approval)

3. **Use specific prompts:**
   - The prompts in this guide are specific enough that Copilot needs minimal clarification
   - This reduces chat back-and-forth

4. **Run tests before creating PR:**
   - Run `./mvnw test` and `npm test` locally before pushing
   - This catches issues early and speeds up CI

5. **Use Docker Compose for integration testing:**
   - Docker Compose is faster than manual startup
   - Both backend and frontend are in one command

---

## Troubleshooting

### Copilot asks clarifying questions

**Why:** Prompt was too vague (missing domain, issue numbers, or contracting details).

**Fix:** Paste the full prompt from this guide, filling in all bracket placeholders first.

### Test failures during integration testing

**Why:** Backend and frontend may have different interpretations of the contract.

**Fix:** 
1. Compare actual API response with contract examples
2. Update contract guide with corrections
3. Rebase backend or frontend PR with the fix
4. Re-test

### Backend PR merge blocked by contract-sync check

**Why:** Backend PR doesn't link to a durion contract PR in the PR body (if check is configured).

**Fix:** Edit the PR body to include:
```
Durion contract PR: [link to durion contract PR]
```

### Frontend tests fail due to API shape mismatch

**Why:** DTO/response shape doesn't match TypeScript types.

**Fix:**
1. Check the contract guide examples against actual API response
2. Update TypeScript types if API shape changed
3. Re-run tests

---

## Next Steps

1. **Create capability directory & manifest:**
   ```bash
   mkdir -p durion/docs/capabilities/cap:my-feature
   cp durion/docs/capabilities/_TEMPLATE_/CAPABILITY_MANIFEST.yaml \
      durion/docs/capabilities/cap:my-feature/CAPABILITY_MANIFEST.yaml
   ```

2. **Run the workflow script:**
   ```bash
   cd durion
   bash scripts/capability-execution-workflow.sh cap:my-feature 123
   ```

3. **Follow the prompts** — the script will walk you through each step with specific actions

4. **Paste prompts into Copilot Chat** — copy the prompts verbatim and fill in placeholders

5. **Update capability docs as you go:**
   - Manifest: Add PR links when opened
   - Stories: Checkbox progress
   - Decision log: Record merge gates & changes

6. **Stop at merge gates** — verify all checks pass before merging

---

## Questions?

- **Capability docs:** See `durion/docs/capabilities/cap:<cap-id>/`
- **Manifest template:** Copy from `durion/docs/capabilities/_TEMPLATE_/CAPABILITY_MANIFEST.yaml`
- **Contract guide location:** See CAPABILITY_MANIFEST.yaml `contract_guide` section
- **Backend module structure:** `durion-positivity-backend/pos-{module}/`
- **Frontend component structure:** `durion-moqui-frontend/runtime/component/{component}/`
- **Copilot instructions:** See `.github/copilot-instructions.md` in each repo
