---
name: pull-request
description: Create a pull request using .github/pull_request_template.md and runtime context.
---

# Pull Request Creation

## Hard Rule
Only `Pull Request Agent` may create pull requests.

## Required Template
Use `.github/pull_request_template.md` as the PR body template.

## Runtime Context (provided by caller)
- `REPO_SLUG`
- `BASE_BRANCH`
- `HEAD_BRANCH`
- `CAPABILITY_ID`
- `PARENT_STORY_REF`
- `CHILD_ISSUE_REF`
- `DOMAIN`
- `CONTRACT_GUIDE_LINK` (if applicable)
- `CONTRACT_PR_LINK` (if applicable)
- `WHAT_CHANGED`
- `WHY`
- `TEST_COMMANDS`
- `RISK_LEVEL`
- `ROLLBACK_PLAN`

## Steps
1. Read `.github/pull_request_template.md`.
2. Fill the template with runtime values.
3. Create PR with title format: `[CAP:<cap-id>] <short summary>`.
4. Ensure parent/child issue links are present.
5. Return PR URL, PR number, title, and final body used.

## Stop Conditions
- If branch is missing, auth fails, or required runtime fields are missing, stop and return explicit blocker details.
