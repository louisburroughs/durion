# PR Creation Enforcement - What Changed

## Problem Statement

The coder agent was NOT creating pull requests when running `capability-completion.prompt.md`. It would:
- ❌ Stop with "The user should create a PR"
- ❌ Provide instructions instead of executing them
- ❌ Report completion with placeholders like "[PR_NUMBER]"

## Solution: Mandatory PR Creation

The capability-completion prompt now **REQUIRES** the agent to create the PR. It cannot stop without a PR unless genuinely blocked.

---

## Changes Made

### 1. capability-completion.prompt.md - Added "DO NOT STOP" Warning

**Added at top of file:**

```markdown
## ⚠️ CRITICAL: YOU MUST CREATE THE PULL REQUEST

**THIS PROMPT'S PRIMARY RESPONSIBILITY IS TO CREATE A PULL REQUEST. DO NOT STOP UNTIL THE PR IS CREATED.**

**YOU MUST CONTINUE WORKING UNTIL:**
1. ✅ All verification steps are complete
2. ✅ Tests are passing
3. ✅ Pull request is created and you have a PR number
4. ✅ PR is linked to all child issues

**DO NOT STOP FOR:**
- "Providing instructions" to create PR manually
- "Recommending" the user create a PR
- Reporting that PR "should" be created
- Any reason other than a genuine technical blocker

**IF YOU FIND YOURSELF WRITING "The user should create a PR" OR "Next step is to create a PR", YOU HAVE FAILED.**

**YOU MUST ACTUALLY CREATE THE PR using tools available to you:**
- `gh pr create` command via terminal
- `mcp_github_create_pull_request` tool
```

### 2. capability-completion.prompt.md - Made Step 4 Mandatory

**Changed from optional suggestion to mandatory execution:**

```markdown
### 4. Create Pull Request **[MANDATORY - DO NOT SKIP]**

**⚠️ THIS IS THE PRIMARY DELIVERABLE OF THIS PROMPT. YOU MUST ACTUALLY CREATE THE PR.**

**STEP 4A: Attempt PR Creation via GitHub CLI**
[Actual commands to execute]

**STEP 4B: If gh CLI Fails, Use MCP GitHub Tool**
[Fallback method with tool parameters]

**STEP 4C: Verify PR Was Created**
[Verification commands]

**IF PR CREATION FAILS:**
1. First attempt - Retry (may be transient)
2. Second attempt - Check auth
3. Third attempt - Alternative method (CLI vs MCP tool)
4. If all fail - Report specific error and STOP

**DO NOT PROCEED TO STEP 5 UNTIL PR IS CREATED AND YOU HAVE A PR NUMBER.**
```

### 3. capability-completion.prompt.md - Completion Report Verification

**Added verification requirements:**

```markdown
## Completion Report **[MANDATORY - Only After PR Created]**

**⚠️ DO NOT GENERATE THIS REPORT UNTIL YOU HAVE CREATED THE PR AND HAVE A PR NUMBER.**

**VERIFICATION:** Your completion report MUST include:
- An actual PR number (e.g., #42, not "[PR_NUMBER]")
- An actual PR URL (e.g., https://github.com/louisburroughs/durion-positivity-backend/pull/42)
- Confirmation that you used a tool or command to create it

**If your completion report has placeholders like "[ACTUAL_PR_NUMBER]", you have NOT completed the task.**
```

### 4. capability-completion.prompt.md - Exhaustive Error Handling

**Added step-by-step retry strategy:**

```markdown
## Error Handling & Blockers

**⚠️ EXHAUST ALL OPTIONS BEFORE STOPPING WITHOUT A PR**

**Attempt all of these in order:**
1. Retry the same method (transient issue)
2. Try alternative method (CLI → MCP tool, or vice versa)
3. Check authentication (gh auth refresh)
4. Verify branch is pushed
5. Check for merge conflicts
6. Try with minimal description

**Only STOP without creating PR if:**
- ❌ All creation methods exhausted (CLI + MCP both fail repeatedly)
- ❌ GitHub API returns persistent authentication errors
- ❌ Branch doesn't exist remotely and push fails
- ❌ Severe merge conflicts requiring manual resolution
```

### 5. coder.agent.md - Emphasized PR Creation is NOT Optional

**Updated branching strategy section:**

```markdown
**Capability Completion (All Stories Done) - YOU MUST CREATE THE PR:**
- **CRITICAL:** When running `capability-completion.prompt.md`, you MUST actually CREATE the pull request
- **DO NOT** just "recommend" or "suggest" creating a PR - you must DO IT
- **DO NOT** stop with placeholders like "[PR_NUMBER]" - you must get an actual PR number
- **DO** exhaust all creation methods (CLI + MCP tool) before giving up
- **DO** report actual PR number (e.g., #42) in completion report

**PR Creation is NOT Optional:**
The only acceptable reasons to stop without a PR are:
- GitHub authentication persistently fails (after retry + refresh)
- All creation methods fail (gh CLI AND mcp_github tool)
- Tests are failing (must fix first)
- Child stories incomplete (must complete first)
```

### 6. BRANCH_AND_PR_WORKFLOW.md - Added PR Creation Enforcement

**Updated Phase 2 description:**

```markdown
**⚠️ CRITICAL:** This prompt MUST create the PR. The agent cannot stop without creating it unless genuinely blocked.

**PR Creation Methods (agent tries both if needed):**
- Method 1: `gh pr create` command
- Method 2: `mcp_github_create_pull_request` tool
- If both fail: reports blocker and requires manual intervention

**The agent MUST report:** "PR created: durion-positivity-backend#42" (actual number, not placeholder)
```

---

## What This Achieves

### Before (Problem)

1. Agent runs capability-completion prompt
2. Agent verifies tests passing ✅
3. Agent generates PR description ✅
4. Agent writes: "The user should now create a PR with the description above" ❌
5. **No PR created** ❌

### After (Solution)

1. Agent runs capability-completion prompt
2. Agent verifies tests passing ✅
3. Agent generates PR description ✅
4. Agent executes: `gh pr create ...` ✅
5. Agent verifies PR exists ✅
6. Agent reports: "PR created: durion-positivity-backend#42" ✅
7. **PR exists and is linked** ✅

---

## Enforcement Mechanisms

### 1. Clear Primary Objective
- Top of prompt states: "PRIMARY RESPONSIBILITY IS TO CREATE A PULL REQUEST"
- No ambiguity about the main deliverable

### 2. Forbidden Phrases Detection
- Agent knows these phrases mean failure:
  - "The user should create a PR"
  - "Next step is to create a PR"  
  - "[PR_NUMBER]" or "[ACTUAL_PR_NUMBER]" in report

### 3. Multi-Method Fallback
- Method 1: GitHub CLI (`gh pr create`)
- Method 2: MCP GitHub tool (`mcp_github_create_pull_request`)
- Must try both before giving up

### 4. Mandatory Verification
- After creation, must verify PR exists
- Must capture actual PR number
- Must include PR number in completion report

### 5. Completion Report Gating
- Cannot generate completion report without PR number
- Report template requires actual values, rejects placeholders

### 6. Error Exhaustion Requirement
- Must retry transient failures
- Must try alternative authentication methods
- Must attempt minimal description if full description fails
- Only stop if ALL methods exhausted

---

## Example Execution Flow

### Happy Path

```bash
# Step 1: Verify prerequisites
✅ All child stories complete
✅ Tests passing
✅ Branch pushed

# Step 2: Run tests
✅ 54/54 tests passing

# Step 3: Generate description
✅ PR description created

# Step 4A: Try gh CLI
$ gh pr create --base main --head cap/CAP-005 --title "..." --body "..."
✅ https://github.com/louisburroughs/durion-positivity-backend/pull/42

# Step 4C: Verify
$ gh pr view cap/CAP-005
✅ PR #42 exists

# Step 5: Link issues
✅ Linked #155, #156, #157, #158, #159, #160, #161

# Completion Report
✅ PR created: durion-positivity-backend#42
✅ URL: https://github.com/louisburroughs/durion-positivity-backend/pull/42
```

### Failure Path (gh CLI not available)

```bash
# Step 4A: Try gh CLI
$ gh pr create ...
❌ gh: command not found

# Step 4B: Fallback to MCP tool
Tool: mcp_github_create_pull_request
  owner: louisburroughs
  repo: durion-positivity-backend
  head: cap/CAP-005
  base: main
  ...
✅ PR created: #42

# Continue with verification...
```

### Genuine Blocker Path

```bash
# Step 4A: Try gh CLI
$ gh pr create ...
❌ HTTP 401: Bad credentials

# Retry with auth refresh
$ gh auth refresh
❌ HTTP 401: Bad credentials

# Step 4B: Try MCP tool
Tool: mcp_github_create_pull_request
❌ Authentication failed: Invalid token

# All methods exhausted
🚫 PR CREATION BLOCKED

Blocker: GitHub authentication failed
Attempted:
1. gh CLI - HTTP 401
2. gh auth refresh - HTTP 401
3. MCP tool - Invalid token

Required Action: User must reconfigure GitHub authentication
```

---

## Testing the Fix

### To verify this works, invoke capability-completion prompt and check:

1. **Agent does NOT write:**
   - "The user should create a PR"
   - "Recommend creating a PR"
   - Placeholder "[PR_NUMBER]"

2. **Agent DOES execute:**
   - `gh pr create` command OR
   - `mcp_github_create_pull_request` tool call

3. **Agent DOES report:**
   - Actual PR number (e.g., "PR #42")
   - Actual PR URL
   - Confirmation of tool/command used

4. **Agent DOES retry if first attempt fails:**
   - Tries CLI then MCP tool
   - Refreshes auth if needed
   - Reports blocker only if all attempts fail

---

## Summary

**Core Change:** Shifted from "suggest PR creation" to "execute PR creation"

**Key Files Modified:**
1. `.github/prompts/capability-completion.prompt.md` - Added mandatory PR creation
2. `.github/agents/coder.agent.md` - Emphasized PR creation is not optional
3. `docs/BRANCH_AND_PR_WORKFLOW.md` - Clarified PR creation requirements

**Result:** The coder agent will now create PRs when completing capabilities, not just recommend creating them.

**Acceptable Failure Cases:** Only stops without PR if genuinely blocked (auth failure, API down) after exhausting all options.
