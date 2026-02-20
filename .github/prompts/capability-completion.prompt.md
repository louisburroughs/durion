---
name: Capability Completion & PR Creation
description: "Creates pull request after all child stories under a capability are complete. Verifies all stories implemented, runs final tests, and opens PR with comprehensive description."
agent: "agent"
model: Claude Sonnet 4.5 (copilot)
---

# Capability Completion & Pull Request Creation

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
- Any reason other than a genuine technical blocker (auth failure, GitHub API down, etc.)

**IF YOU FIND YOURSELF WRITING "The user should create a PR" OR "Next step is to create a PR", YOU HAVE FAILED.**

**YOU MUST ACTUALLY CREATE THE PR using tools available to you:**
- `gh pr create` command via terminal
- `mcp_github_create_pull_request` tool
- GitHub API calls

**Only stop without creating PR if:**
- Prerequisites are not met (tests failing, stories incomplete)
- GitHub authentication fails and cannot be resolved
- GitHub API returns persistent errors (after retries)

---

## Purpose

This prompt is invoked **AFTER** all backend child stories for a capability have been implemented and committed to the capability branch (`cap/CAP{{capability_id}}`).

**Use this prompt to:**
1. Verify all child stories are complete
2. Run final verification tests
3. **CREATE the pull request** (not suggest, not recommend - ACTUALLY CREATE IT)
4. Link PR to all related issues

## Prerequisites (VERIFY BEFORE PROCEEDING)

**STOP and verify these conditions are met:**

- [ ] All backend child stories listed in CAPABILITY_MANIFEST.yaml are marked complete
- [ ] All commits are on `cap/CAP{{capability_id}}` branch in durion-positivity-backend
- [ ] Branch has been pushed to remote: `git ls-remote --heads origin cap/CAP{{capability_id}}`
- [ ] All tests passing on the capability branch
- [ ] No uncommitted changes: `git status` shows clean working tree

**If ANY prerequisite is not met, STOP and complete the missing stories first.**

---

## Capability Context

**Capability ID:** {{capability_label}} (e.g., CAP:089)
**Capability Issue:** [durion#{{parent_capability_number}}]({{parent_capability_url}}) — {{parent_capability_title}}
**Domain:** {{domain}}
**Backend Repository:** louisburroughs/durion-positivity-backend
**Target Branch:** `cap/CAP{{capability_id}}`

**Backend Child Stories (must all be complete):**
{{backend_child_issues}}

**Contract Guide:** `durion/domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md`

---

## Completion Checklist

### 1. Verify Branch Status

```bash
cd $WORKSPACE/durion-positivity-backend

# Confirm you're on the capability branch
git checkout cap/CAP{{capability_id}}
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "cap/CAP{{capability_id}}" ]; then
  echo "ERROR: Not on capability branch. Current: $CURRENT_BRANCH"
  exit 1
fi

# Ensure branch is up to date with remote
git fetch origin
git pull origin cap/CAP{{capability_id}}

# Verify branch has commits ahead of main
COMMITS_AHEAD=$(git rev-list --count main..cap/CAP{{capability_id}})
if [ "$COMMITS_AHEAD" -eq 0 ]; then
  echo "ERROR: No commits on capability branch"
  exit 1
fi
echo "✓ Capability branch has $COMMITS_AHEAD commits ahead of main"
```

### 2. Run Final Verification Tests

**Determine affected modules** (e.g., for workorder domain, test `pos-workorder`):
```bash
cd $WORKSPACE/durion-positivity-backend

# Run tests for affected module(s)
./mvnw -pl pos-{{module}} clean test

# Run architecture tests if they exist
./mvnw -pl pos-archunit test 2>/dev/null || echo "No archunit tests"

# Capture test results
TEST_RESULTS=$(./mvnw -pl pos-{{module}} test 2>&1 | grep -E "(Tests run|BUILD SUCCESS|BUILD FAILURE)")
echo "$TEST_RESULTS"
```

**IF TESTS FAIL:**
- Fix failures on the capability branch
- Commit fixes: `git commit -am "fix: resolve test failures for CAP{{capability_id}}"`
- Push: `git push origin cap/CAP{{capability_id}}`
- Re-run tests until passing

### 3. Generate PR Description

**Gather information for comprehensive PR description:**

```bash
cd $WORKSPACE/durion-positivity-backend

# Get commit summary
git log --oneline main..cap/CAP{{capability_id}}

# Get files changed
git diff --name-only main...cap/CAP{{capability_id}} | head -20

# Get line change stats
git diff --stat main...cap/CAP{{capability_id}}
```

**PR Title Format:**
```
feat({{domain}}): implement {{capability_label}} - {{parent_capability_title}}
```

**PR Description Template:**
```markdown
## Capability: {{capability_label}}

**Capability Issue:** Closes louisburroughs/durion#{{parent_capability_number}}

### Summary

This PR implements the complete backend for {{capability_label}} - {{parent_capability_title}}.

### Backend Stories Implemented

{{backend_child_issues}}

### Changes Summary

- **Domain:** {{domain}}
- **Module(s):** pos-{{module}} (adjust as needed)
- **Commits:** [N commits] ahead of main
- **Files Changed:** [N files]
- **Lines Added/Removed:** [+X/-Y lines]

### Key Features Implemented

<!-- Auto-generate from commit messages or list manually -->
- Feature 1: [Endpoint/service description]
- Feature 2: [Endpoint/service description]
- Feature 3: [Endpoint/service description]

### Testing

- [x] Unit tests passing
- [x] Contract behavior tests passing
- [x] Architecture tests passing (ArchUnit)
- [ ] Integration tests (if applicable)

**Test Results:**
```
[Paste test summary: X tests run, Y passed, Z failed]
```

### Contract Compliance

- [x] Implementation matches contract guide: `domains/{{domain}}/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- [x] OpenAPI annotations match contract
- [x] Provider contract tests verify behavior

### Documentation

- [x] OpenAPI annotations added/updated
- [x] Contract guide sections marked `stable-for-ui` (or `draft` if not ready)
- [x] AGENTS.md updated (if needed)
- [ ] README updates (if applicable)

### Migration/Breaking Changes

<!-- If there are database migrations, breaking changes, or deployment notes -->
- [ ] Database migrations included: `V{n}__*.sql`
- [ ] Breaking changes: [Describe or "None"]
- [ ] Deployment notes: [Special steps or "Standard deployment"]

### Related Issues

<!-- Link all related issues -->
- Closes louisburroughs/durion#{{parent_capability_number}}
<!-- Add links to each backend child issue -->

### Review Checklist

- [ ] Code follows workspace conventions (see `.github/instructions/`)
- [ ] No secrets or credentials hardcoded
- [ ] Null safety annotations used (`@NonNull`)
- [ ] Events emitted for state changes (`@EmitEvent`)
- [ ] Error handling matches contract error codes
- [ ] Idempotency implemented where required

---

**Branch:** `cap/CAP{{capability_id}}`
**Target:** `main`
```

### 4. Create Pull Request **[MANDATORY - DO NOT SKIP]**

**⚠️ THIS IS THE PRIMARY DELIVERABLE OF THIS PROMPT. YOU MUST ACTUALLY CREATE THE PR.**

**STEP 4A: Attempt PR Creation via GitHub CLI**

```bash
cd $WORKSPACE/durion-positivity-backend

# Verify gh CLI is available
if ! command -v gh &> /dev/null; then
  echo "WARNING: gh CLI not found, will use mcp_github tool"
else
  # Authenticate and check status
  gh auth status
  
  # Create PR with filled-in description
  gh pr create \
    --base main \
    --head cap/CAP{{capability_id}} \
    --title "feat({{domain}}): implement {{capability_label}} - {{parent_capability_title}}" \
    --body "[Generated PR description from template above]" \
    --repo louisburroughs/durion-positivity-backend
  
  # Capture PR number
  PR_NUMBER=$(gh pr list --head cap/CAP{{capability_id}} --json number --jq '.[0].number')
  echo "✅ PR CREATED: #$PR_NUMBER"
fi
```

**STEP 4B: If gh CLI Fails, Use MCP GitHub Tool**

If gh CLI is not available or fails, you MUST use the `mcp_github_create_pull_request` tool:

```
Tool: mcp_github_create_pull_request
Parameters:
  owner: louisburroughs
  repo: durion-positivity-backend
  title: "feat({{domain}}): implement {{capability_label}} - {{parent_capability_title}}"
  body: "[Full PR description from template, filled in with actual values]"
  head: cap/CAP{{capability_id}}
  base: main
```

**STEP 4C: Verify PR Was Created**

After using EITHER method above, verify the PR exists:

```bash
# Check PR was created
gh pr view cap/CAP{{capability_id}} --repo louisburroughs/durion-positivity-backend

# Get PR number and URL
gh pr list --head cap/CAP{{capability_id}} --json number,url --jq '.[]'
```

**IF PR CREATION FAILS:**

1. **First attempt - Retry:** Try the command/tool again (may be transient)
2. **Second attempt - Check auth:** Verify GitHub authentication and permissions
3. **Third attempt - Alternative method:** Try the other creation method (CLI vs MCP tool)
4. **If all fail:** Report the specific error and blockers, then STOP

**DO NOT PROCEED TO STEP 5 UNTIL PR IS CREATED AND YOU HAVE A PR NUMBER.**

### 5. Link PR to Issues

**After PR is created, link it to all related issues:**

For each backend child issue in {{backend_child_issues}}:
```bash
# Add comment linking to PR
gh issue comment [ISSUE_NUMBER] --repo louisburroughs/durion-positivity-backend --body "Implemented in PR #[PR_NUMBER]"
```

Or use `mcp_github_add_issue_comment` tool for each child issue.

### 6. Request Review (Optional)

If review is required:
```bash
# Request review from team members
gh pr review [PR_NUMBER] --request-reviewer [USERNAME]
```

---

## Completion Report **[MANDATORY - Only After PR Created]**

**⚠️ DO NOT GENERATE THIS REPORT UNTIL YOU HAVE CREATED THE PR AND HAVE A PR NUMBER.**

After PR is successfully created, provide this summary:

```markdown
✅ **CAPABILITY COMPLETION SUCCESSFUL**

**Capability:** {{capability_label}} - {{parent_capability_title}}
**Pull Request:** louisburroughs/durion-positivity-backend#[ACTUAL_PR_NUMBER]
**PR URL:** [ACTUAL_PR_URL]
**Branch:** cap/CAP{{capability_id}}
**Status:** Open and ready for review

**Implemented Stories:**
{{backend_child_issues}}

**Test Results:**
- Unit tests: [X/X passing]
- Contract tests: [Y/Y passing]
- Architecture tests: [Z/Z passing]

**Files Changed:** [N files]
**Commits:** [M commits]
**Lines Changed:** [+X/-Y]

**PR Details:**
- Title: feat({{domain}}): implement {{capability_label}} - {{parent_capability_title}}
- Base: main
- Head: cap/CAP{{capability_id}}
- Created: [Timestamp]
- Linked Issues: [List actual issue numbers that were linked]

**Next Steps:**
1. ✅ PR created - DONE
2. ⏳ Awaiting code review
3. ⏳ Address review feedback (if any)
4. ⏳ Merge after approval
5. ⏳ Deploy to target environment
6. ⏳ Verify functionality in deployed environment

**Completion Criteria Met:**
- ✅ All child stories implemented
- ✅ Tests passing
- ✅ Branch pushed
- ✅ PR created
- ✅ Issues linked
```

**VERIFICATION:** Your completion report MUST include:
- An actual PR number (e.g., #42, not "[PR_NUMBER]")
- An actual PR URL (e.g., https://github.com/louisburroughs/durion-positivity-backend/pull/42)
- Confirmation that you used a tool or command to create it

**If your completion report has placeholders like "[ACTUAL_PR_NUMBER]", you have NOT completed the task.**

---

## Error Handling & Blockers

**⚠️ EXHAUST ALL OPTIONS BEFORE STOPPING WITHOUT A PR**

### If PR Creation Fails

**Attempt all of these in order:**

1. **Retry the same method** (may be transient GitHub API issue)
2. **Try alternative method:**
   - If gh CLI failed → try `mcp_github_create_pull_request` tool
   - If MCP tool failed → try gh CLI
3. **Check authentication:**
   ```bash
   gh auth status
   gh auth refresh
   ```
4. **Verify branch is pushed:**
   ```bash
   git ls-remote --heads origin cap/CAP{{capability_id}}
   ```
5. **Check for merge conflicts:**
   ```bash
   git fetch origin main
   git merge-base --is-ancestor main cap/CAP{{capability_id}} || echo "Branch may have conflicts"
   ```
6. **Try with minimal description:**
   - If long description fails, try with short description
   - Can edit PR description after creation

**Only STOP without creating PR if:**
- ❌ All creation methods exhausted (CLI + MCP tool both fail repeatedly)
- ❌ GitHub API returns persistent authentication errors (after refresh attempts)
- ❌ Branch doesn't exist remotely and push fails
- ❌ Severe merge conflicts that require manual resolution

**In these cases, report:**
```markdown
🚫 **PR CREATION BLOCKED**

**Blocker:** [Specific technical issue]

**Attempted:**
1. [Method 1] - Result: [Error]
2. [Method 2] - Result: [Error]
3. [Method 3] - Result: [Error]

**Error Details:** [Full error message]

**Diagnosis:** [What you think is wrong]

**Required Action:** [What needs to happen to unblock]

**Partial Completion:**
- ✅ All stories implemented
- ✅ Tests passing
- ✅ Branch pushed: cap/CAP{{capability_id}}
- ❌ PR creation failed

**Manual PR Creation (if needed):**
- Base: main
- Head: cap/CAP{{capability_id}}
- Title: feat({{domain}}): implement {{capability_label}} - {{parent_capability_title}}
- Description: [See PR template in this prompt]
```

**If tests fail during verification:**
1. Fix issues on the capability branch
2. Commit and push fixes
3. Re-run this prompt to retry PR creation after tests pass

**If child stories are incomplete:**
1. Identify which stories need implementation
2. Run backend-story-fulfillment prompt for incomplete stories
3. Return to this prompt after all stories are complete

---

## Notes

- This prompt assumes all backend child stories have been implemented using the backend-story-fulfillment prompt
- The capability branch should have ALL commits from all child stories
- PR description should be comprehensive to help reviewers understand the full scope
- Tests MUST pass before PR creation
- Contract guide status (draft/stable-for-ui) should be accurate
