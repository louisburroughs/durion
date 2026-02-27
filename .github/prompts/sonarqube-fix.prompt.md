---
name: "SonarQube Remediation With Branch + PR"
agent: "SonarQube Fix Agent"
description: "Run SonarQube remediation safely: create branch, apply fixes, commit, and open PR with a unique short name."
model: "GPT-5.3-Codex (copilot)"
---

# SonarQube Fix Prompt

## Goal

Remediate SonarQube issues in `durion-positivity-backend`, then commit and open a pull request.

You must:
1. Create a new branch before making code changes.
2. Switch to that branch.
3. Apply safe SonarQube fixes.
4. Commit the changes.
5. Push and create a pull request with a unique short name.

## Runtime Context (provide at execution time)

- `BACKEND_REPO_PATH` (default: `$WORKSPACE/durion-positivity-backend`)
- `BASE_BRANCH` (default: `main`)
- `SONAR_SCOPE` (optional module/file scope)
- `PR_BASE_BRANCH` (default: same as `BASE_BRANCH`)
- `AUTOMATED_MODE` (default: `true`)

## Orchestrator Invocation Snippet

Use this pattern to invoke the prompt with runtime values automatically:

```typescript
// 1) Read prompt file
const promptContent = await readFile('.github/prompts/sonarqube-fix.prompt.md');

// 2) Build runtime context (override only what you need)
const runtimeContext = `
## Runtime Context
- BACKEND_REPO_PATH: $WORKSPACE/durion-positivity-backend
- BASE_BRANCH: main
- PR_BASE_BRANCH: main
- SONAR_SCOPE: pos-shop-manager
- AUTOMATED_MODE: true
`;

// 3) Delegate to SonarQube Fix Agent
await runSubagent({
  description: 'Remediate SonarQube findings and open PR',
  prompt: `${promptContent}\n\n${runtimeContext}\nPlease execute the prompt above with these runtime values.`
});
```

## Reinforced Safety Rules (Non-Negotiable)

1. Do not fix cognitive complexity issues. Skip and report.
2. Do not introduce new thrown exceptions and do not add new `throw` paths.
3. Do not fix or modify TODO issues/comments. Skip and report each TODO with a short summary.
4. If uncertain, skip the issue and add a note in the final report.
5. Try a real fix first; suppress only when a safe fix is not feasible.
6. Suppression for style issues in tests (including test method names) is allowed.
7. Every suppression must be explained in the final report.

## Required Execution Flow

### 1) Prepare git and create unique branch name

Run these commands in order:

```bash
cd ${BACKEND_REPO_PATH:-$WORKSPACE/durion-positivity-backend}
git fetch origin
git switch ${BASE_BRANCH:-main}
git pull --ff-only origin ${BASE_BRANCH:-main}
git status --short
SHORT_ID="$(date -u +%m%d%H%M)-$(LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c4)"
BRANCH_NAME="fix/sonar-${SHORT_ID}"
git switch -c "${BRANCH_NAME}"
git branch --show-current
```

Branch check must show `fix/sonar-<unique-short-id>` before any edits.

If `git switch -c "${BRANCH_NAME}"` fails because the branch already exists, run:

```bash
git switch "${BRANCH_NAME}"
git branch --show-current
```

### 2) Read SonarQube findings

- Load issues from SonarQube/SonarLint tools.
- If `SONAR_SCOPE` is set, focus analysis there first.
- Build a remediation list: `fix`, `suppress-after-attempt`, `skip`.

### 3) Apply fixes under strict constraints

- Implement minimal safe fixes.
- Do not touch cognitive complexity findings.
- Do not modify TODO findings/comments.
- Do not add new thrown exceptions or new `throw` paths.
- When in doubt, skip and note why.

### 4) Verify

Run targeted verification commands for changed modules/files (tests and/or analysis).

### 5) Commit

```bash
cd ${BACKEND_REPO_PATH:-$WORKSPACE/durion-positivity-backend}
git add -A
git commit -m "fix(sonar): ${SHORT_ID} remediate safe issues"
git push -u origin "${BRANCH_NAME}"
git rev-parse HEAD
```

If no changes were made, do not force a commit. Report why.

### 6) Create pull request with unique short name

Use:
- PR title: `fix(sonar): ${SHORT_ID}`
- PR branch: `${BRANCH_NAME}`
- PR base: `${PR_BASE_BRANCH:-${BASE_BRANCH:-main}}`

If `gh` is available:

```bash
cd ${BACKEND_REPO_PATH:-$WORKSPACE/durion-positivity-backend}
gh pr create \
  --base "${PR_BASE_BRANCH:-${BASE_BRANCH:-main}}" \
  --head "${BRANCH_NAME}" \
  --title "fix(sonar): ${SHORT_ID}" \
  --body-file .agent-tmp/sonar-pr-body-${SHORT_ID}.md
```

If GitHub tool APIs are available, create the PR with the same title/base/head and equivalent body.

If PR creation fails, include:
- exact error output
- `git status --short`
- `git log --oneline -5`

## Required PR Body Template

Write this body to `.agent-tmp/sonar-pr-body-${SHORT_ID}.md` before PR creation:

```bash
cd ${BACKEND_REPO_PATH:-$WORKSPACE/durion-positivity-backend}
mkdir -p .agent-tmp
```

```md
## Summary
- SonarQube remediation batch: `${SHORT_ID}`

## Fixed Issues
- <rule key> — <file:line> — <short fix summary>

## Suppressed Issues
- <rule key> — <file:line> — <why suppressed> — <attempted fix / unsafe reason>

## Skipped Issues
- <rule key> — <file:line> — <reason>

## TODO Items (Not Modified)
- <file:line> — <short TODO summary>

## Verification
- Commands run:
  - `<command>`
- Outcomes:
  - `<result>`
```

## Final Output (must be returned)

- Branch name
- Commit hash
- PR URL
- Fixed issues list
- Suppressed issues list with rationale
- Skipped issues list with reasons
- TODO short summaries
- Verification commands and outcomes
