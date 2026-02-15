# Backend Capability Implementation Workflow

## Overview

This document explains the updated workflow for implementing backend capabilities across multiple child stories, then creating a single pull request at completion.

## Problem Solved

**Previous Issues:**
1. Branch creation failed on 2nd+ child story (
`git checkout -b` crashed if branch exists)
2. No clear instructions for PR creation timing
3. Agents didn't know when/how to create PRs

**New Solution:**
- Idempotent branch creation (works for all child stories)
- Separate prompts for story implementation vs. capability completion
- Clear PR creation workflow after all stories done

---

## Two-Phase Workflow

### Phase 1: Implement Child Stories (Repeat for Each Story)

**Prompt:** `.github/prompts/backend-story-fulfillment.prompt.md`

**Purpose:** Implement ONE backend child story under a capability

**What It Does:**
1. ✅ Creates OR checks out existing capability branch `cap/CAP-###` (idempotent)
2. ✅ Implements the story (endpoints, services, tests)
3. ✅ Commits changes to the capability branch
4. ✅ Pushes the capability branch to remote
5. ❌ Does NOT create a pull request

**Command Pattern:**
```bash
# Story 1 - Creates branch
git checkout cap/CAP-005 2>/dev/null || git checkout -b cap/CAP-005
# ... implement story 1 ...
git commit -m "feat(workorder): implement story #160"
git push -u origin cap/CAP-005

# Story 2 - Checks out existing branch
git checkout cap/CAP-005 2>/dev/null || git checkout -b cap/CAP-005  # Already exists, just checks out
git pull origin cap/CAP-005  # Gets story 1's commits
# ... implement story 2 ...
git commit -m "feat(workorder): implement story #161"
git push origin cap/CAP-005

# Story 3, 4, 5... same pattern
```

**Completion Criteria:**
- ✅ Code committed to capability branch
- ✅ Branch pushed to remote
- ✅ Tests passing
- ⏸️ **STOP - DO NOT CREATE PR**

**Repeat this phase for EACH backend child story** under the capability.

---

### Phase 2: Complete Capability & Create PR (Once at End)

**Prompt:** `.github/prompts/capability-completion.prompt.md`

**Purpose:** Create pull request after ALL child stories complete

**⚠️ CRITICAL:** This prompt MUST create the PR. The agent cannot stop without creating it unless genuinely blocked.

**Prerequisites (Verify First):**
- [ ] ALL backend child stories implemented and committed
- [ ] Capability branch (`cap/CAP-###`) exists and is pushed
- [ ] All tests passing
- [ ] Contract guide updated

**What It Does:**
1. ✅ Verifies all child stories complete
2. ✅ Runs final verification tests
3. ✅ Generates comprehensive PR description
4. ✅ **CREATES pull request: `cap/CAP-###` → `main`** (using gh CLI or mcp_github tool)
5. ✅ Links PR to all child story issues
6. ✅ Requests reviews (optional)

**PR Creation Methods (agent tries both if needed):**
- Method 1: `gh pr create` command
- Method 2: `mcp_github_create_pull_request` tool
- If both fail: reports blocker and requires manual intervention

**PR Description Includes:**
- Capability summary
- List of all implemented child stories
- Test results
- Files changed
- Contract compliance confirmation
- Migration notes (if any)

**Completion Criteria:**
- ✅ Pull request created and open
- ✅ Agent reports actual PR number (e.g., #42, not placeholder)
- ✅ All child issues linked to PR
- ✅ Ready for code review

**The agent MUST report:** "PR created: durion-positivity-backend#42" (actual number, not placeholder)

---

## Example: CAP-005 with 7 Child Stories

**Capability:** CAP-005 - Execute Workorder Parts & Labor
**Child Stories:** #155, #156, #157, #158, #159, #160, #161

### Phase 1: Implement Each Story (7 times)

```bash
# Story #160
./invoke backend-story-fulfillment.prompt.md CAP-005 story-160
# Creates cap/CAP-005 branch, implements story #160, commits, pushes

# Story #156
./invoke backend-story-fulfillment.prompt.md CAP-005 story-156
# Checks out cap/CAP-005, pulls, implements story #156, commits, pushes

# Story #161
./invoke backend-story-fulfillment.prompt.md CAP-005 story-161
# Checks out cap/CAP-005, pulls, implements story #161, commits, pushes

# ... repeat for #159, #158, #157, #155
```

**After Phase 1:**
- Branch `cap/CAP-005` exists with 7+ commits (one per story)
- No PR created yet
- All stories implemented and tested

### Phase 2: Create Capability PR (Once)

```bash
# After ALL 7 stories are complete
./invoke capability-completion.prompt.md CAP-005

# Verifies all stories complete
# Runs final tests
# Creates PR: cap/CAP-005 → main
# Links PR to issues #155, #156, #157, #158, #159, #160, #161
```

**After Phase 2:**
- PR created: `feat(workorder): implement CAP-005 - Execute Workorder Parts & Labor`
- PR description lists all 7 child stories
- PR links to all 7 child issues
- Ready for code review

---

## Branch Creation Commands (Idempotent)

**Key Innovation:** These commands work whether branch exists or not

```bash
# Location
cd /home/louisb/Projects/durion-positivity-backend

# Sync with remote
git fetch origin
git checkout main
git pull origin main

# Create OR checkout capability branch (idempotent)
git checkout cap/CAP-005 2>/dev/null || git checkout -b cap/CAP-005

# Track remote if exists
git branch --set-upstream-to=origin/cap/CAP-005 cap/CAP-005 2>/dev/null || true

# Pull latest if remote exists
git pull origin cap/CAP-005 2>/dev/null || true

# Verify branch
git branch --show-current  # MUST output: cap/CAP-005
```

**Why This Works:**
1. `git checkout cap/CAP-005 2>/dev/null` - Try to checkout existing branch, suppress errors
2. `|| git checkout -b cap/CAP-005` - If checkout fails (branch doesn't exist), create it
3. Subsequent stories just checkout the existing branch

---

## File Structure

```
durion/.github/prompts/
├── backend-story-fulfillment.prompt.md      # Phase 1: Per-story implementation
└── capability-completion.prompt.md          # Phase 2: PR creation

durion/.github/agents/
└── coder.agent.md                           # Updated with branching/PR guidance

durion/docs/
└── BRANCH_AND_PR_WORKFLOW.md               # This file
```

---

## Coder Agent Responsibilities

**Updated in `.github/agents/coder.agent.md`:**

### During Story Implementation
- ✅ Create or checkout capability branch (idempotent)
- ✅ Implement story features
- ✅ Commit to capability branch
- ✅ Push capability branch
- ❌ **DO NOT** create pull request

### During Capability Completion
- ✅ Verify all stories complete
- ✅ Run final tests
- ✅ Create comprehensive PR
- ✅ Link all child issues
- ✅ Request reviews if needed

### Never Do
- ❌ Create separate branches per child story
- ❌ Create PRs during story implementation (unless user explicitly requests)
- ❌ Push directly to main

---

## Verification Commands

**Before Phase 2, verify all stories complete:**

```bash
# Check capability branch exists and has commits
cd durion-positivity-backend
git fetch origin
git log --oneline main..origin/cap/CAP-005

# Verify all child stories implemented
# Check commit messages reference all story numbers
git log --oneline --grep="#155" --grep="#156" --grep="#157" --grep="#158" --grep="#159" --grep="#160" --grep="#161" main..origin/cap/CAP-005

# Run tests
./mvnw -pl pos-workorder clean test

# Check files changed
git diff --name-only main...cap/CAP-005
```

**If any story is missing:**
1. Go back to Phase 1
2. Run backend-story-fulfillment prompt for missing story
3. Return to Phase 2 after all stories complete

---

## Benefits of This Approach

1. **Idempotent Branch Creation**
   - Works for first story or 10th story
   - No more "branch already exists" errors
   - Agents can retry without fear

2. **Single PR Per Capability**
   - Easier code review (all related changes together)
   - Clear capability scope
   - Atomic merge (all or nothing)

3. **Clear Workflow Separation**
   - Story implementation = backend-story-fulfillment prompt
   - PR creation = capability-completion prompt
   - No confusion about when to create PR

4. **Comprehensive PR Description**
   - Links all child issues
   - Includes test results
   - Documents scope and changes
   - Simplifies reviewer's job

5. **Failure Recovery**
   - If story implementation fails, branch persists
   - Can resume on existing branch
   - No orphaned branches

---

## Troubleshooting

### Problem: "Branch already exists" error

**Cause:** Using `git checkout -b` on existing branch

**Solution:** Use idempotent pattern:
```bash
git checkout cap/CAP-005 2>/dev/null || git checkout -b cap/CAP-005
```

### Problem: No PR created after all stories done

**Cause:** backend-story-fulfillment prompt doesn't create PRs

**Solution:** Run capability-completion prompt:
```bash
./invoke capability-completion.prompt.md CAP-005
```

### Problem: PR description is incomplete

**Cause:** capability-completion prompt needs better info

**Solution:** Verify:
- All commits have good messages
- Contract guide is updated
- Tests are passing
- CAPABILITY_MANIFEST.yaml is accurate

### Problem: Tests fail at Phase 2

**Cause:** Story implementations have issues

**Solution:**
1. Fix issues on the capability branch
2. Commit fixes: `git commit -am "fix: resolve test failures"`
3. Push: `git push origin cap/CAP-005`
4. Re-run capability-completion prompt

---

## Updates Made

### .github/prompts/backend-story-fulfillment.prompt.md
- ✅ Step 3: Idempotent branch creation command
- ✅ Step 3: Remote tracking setup
- ✅ Step 3: Pull latest changes if branch exists remotely
- ✅ Step 6: Push verification command
- ✅ Step 7: New completion checklist (no PR creation)
- ✅ Clearer "STOP HERE" guidance

### .github/prompts/capability-completion.prompt.md (NEW)
- ✅ Prerequisites verification section
- ✅ Branch status verification
- ✅ Final test execution
- ✅ Comprehensive PR description template
- ✅ PR creation via gh CLI or mcp_github tools
- ✅ Issue linking instructions
- ✅ Completion report template

### .github/agents/coder.agent.md
- ✅ Replaced "Pull Requests (Capability-Level Only)" section
- ✅ Added "Branching & Pull Request Strategy" section
- ✅ Documented idempotent branch pattern
- ✅ Clear prompt usage guidance
- ✅ "Never" guidelines for common mistakes

---

## Summary

**Before:**
- ❌ Branch creation failed on 2nd story
- ❌ No clear PR creation process
- ❌ Agents confused about timing

**After:**
- ✅ Idempotent branch creation works every time
- ✅ Two-phase workflow (story → capability)
- ✅ Clear separation: implement vs. complete
- ✅ Comprehensive PR creation process
- ✅ Agents know exactly when/how to create PRs

**Workflow:**
1. Run `backend-story-fulfillment.prompt.md` for each child story (no PR)
2. Run `capability-completion.prompt.md` once at end (creates PR)

Done! 🎉
