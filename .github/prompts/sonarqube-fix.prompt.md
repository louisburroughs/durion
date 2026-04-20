---
name: "SonarQube Remediation With Branch + PR"
agent: "SonarQube Fix Agent"
description: "Run SonarQube remediation safely: create branch, apply fixes, commit, and prepare PR handoff for Pull Request Agent."
model: "GPT-5.3-Codex (copilot)"
---

# SonarQube Fix Prompt

## Goal

Remediate SonarQube issues in `durion-positivity-backend`, then commit and prepare pull-request handoff for `Pull Request Agent`.

You must:

1. Create a new branch before making code changes.
2. Switch to that branch.
3. Apply safe SonarQube fixes.
4. Commit the changes.
5. Push and prepare a pull request handoff payload with a unique short name.

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
const promptContent = await readFile(".github/prompts/sonarqube-fix.prompt.md");

// 2) Build runtime context (override only what you need)
const runtimeContext = `
## Runtime Context
- BACKEND_REPO_PATH: $WORKSPACE/durion-positivity-backend
- BASE_BRANCH: main
- PR_BASE_BRANCH: main
- SONAR_SCOPE: {module}
- AUTOMATED_MODE: true
`;

// 3) Delegate to SonarQube Fix Agent
await runSubagent({
  description: "Remediate SonarQube findings and prepare PR handoff",
  prompt: `${promptContent}\n\n${runtimeContext}\nPlease execute the prompt above with these runtime values.`,
});
```

## Reinforced Safety Rules (Non-Negotiable)

1. Do not fix or modify TODO issues/comments. Skip and report each TODO with a short summary.

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

- Load all issues from SonarQube/SonarLint tools (overall code), not only issues on new code.
- Do not restrict analysis to Sonar's New Code period.
- If `SONAR_SCOPE` is set, focus analysis there first.
- Build a remediation list: `fix`, `suppress-after-attempt`, `skip-todo-only`.

### 3) Apply fixes under strict constraints

- Implement minimal safe fixes.
- Do not modify TODO findings/comments.
- Do not stop at partial progress: continue until every non-TODO Sonar issue is addressed in this run.
- For non-TODO issues, do not skip: attempt a safe fix first; if a safe fix is not feasible, suppress with full rationale.

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

### 6) Prepare PR handoff with unique short name

Use:

- PR title: `fix(sonar): ${SHORT_ID}`
- PR branch: `${BRANCH_NAME}`
- PR base: `${PR_BASE_BRANCH:-${BASE_BRANCH:-main}}`

Do NOT create the pull request from this prompt. PR creation is reserved for `Pull Request Agent`. Produce a PR handoff payload containing:

- title
- base branch
- head branch
- body file path/content
- traceability links

## Required PR Handoff Body Template

Write this body to `.agent-tmp/sonar-pr-body-${SHORT_ID}.md` for `Pull Request Agent`:

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
- PR handoff payload (title/base/head/body)
- Fixed issues list
- Suppressed issues list with rationale
- Skipped issues list with reasons
- TODO short summaries
- Verification commands and outcomes

Completion requirement:

- Do not stop early. Finish only after all non-TODO Sonar issues are either fixed or explicitly suppressed with rationale, and only TODO issues remain skipped.
