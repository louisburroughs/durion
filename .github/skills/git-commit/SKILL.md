---
name: git-commit
description: "Execute git commit with conventional commit analysis using safe, auto-approve-friendly commands. Supports optional MCP-based issue lookup/comment flows via io.github.github. Use when user asks to commit changes, create a commit, or mentions '/commit'."
license: MIT
---

# Git Commit with Conventional Commits

## Overview

Create standardized, semantic git commits using the Conventional Commits specification. Analyze the actual diff to determine appropriate type, scope, and message.

Primary goal: complete commit workflows using safe commands that are likely to pass terminal auto-approve rules without intervention.

## Conventional Commit Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Commit Types

| Type       | Purpose                        |
| ---------- | ------------------------------ |
| `feat`     | New feature                    |
| `fix`      | Bug fix                        |
| `docs`     | Documentation only             |
| `style`    | Formatting/style (no logic)    |
| `refactor` | Code refactor (no feature/fix) |
| `perf`     | Performance improvement        |
| `test`     | Add/update tests               |
| `build`    | Build system/dependencies      |
| `ci`       | CI/config changes              |
| `chore`    | Maintenance/misc               |
| `revert`   | Revert commit                  |

## Breaking Changes

```
# Exclamation mark after type/scope
feat!: remove deprecated endpoint

# BREAKING CHANGE footer
feat: allow config to extend other configs

BREAKING CHANGE: `extends` key behavior changed
```

## Workflow

### 1. Analyze Diff

```bash
git diff --staged
git diff
git status --porcelain
```

### 2. Stage Files (if needed)

If nothing is staged or you want to group changes differently:

```bash
git add path/to/file1 path/to/file2
git add src/module/FileA.java src/module/FileB.java
```

**Never commit secrets** (.env, credentials.json, private keys).

### 3. Generate Commit Message

Analyze the diff to determine:

- **Type**: What kind of change is this?
- **Scope**: What area/module is affected?
- **Description**: One-line summary of what changed (present tense, imperative mood, <72 chars)

### 4. Execute Commit

```bash
git commit -m "<type>[scope]: <description>"
```

Use one `-m` line by default for reliability with auto-approve.

## Best Practices

- One logical change per commit
- Present tense: "add" not "added"
- Imperative mood: "fix bug" not "fixes bug"
- Reference issues: `Closes #123`, `Refs #456`
- Keep description under 72 characters

## MCP Integration (Optional)

Use MCP only when the user asks to sync commit context with GitHub issues.

### MCP Discovery

```bash
mcp-cli
mcp-cli io.github.github -d
mcp-cli io.github.github/search_issues
mcp-cli io.github.github/add_issue_comment
```

### MCP-Assisted Commit Context

```bash
# Find open issues related to your change
mcp-cli io.github.github/search_issues '{"query":"repo:OWNER/REPO is:issue is:open auth"}'

# Commit locally
git commit -m "fix(auth): handle missing SSO callback token"

# Copy commit id for issue comment
git rev-parse --short HEAD

# Add follow-up comment to a linked issue
mcp-cli io.github.github/add_issue_comment '{"owner":"OWNER","repo":"REPO","issue_number":123,"body":"Fixed in <commit-sha>. Please validate on next build."}'
```

### MCP Rules

- Keep git operations local-first; MCP is supplemental.
- Never post MCP comments unless the user requested issue updates.
- Keep MCP calls one command per line to match auto-approve-friendly style.
- Discover exact tool signatures with `mcp-cli io.github.github/<tool>` before execution.

## Auto-Approve Compatibility Rules

Use these rules to minimize manual approval prompts.

### Required Command Style

- Run one command per line.
- Do not chain commands with `&&`, `||`, or `;`.
- Do not wrap git actions in `if`, `for`, or subshell scripting.
- Prefer direct commands over interactive flows.
- Use `git switch` for branch changes (not `git checkout`).

### Safe Command Set (preferred)

```bash
git status --porcelain
git diff
git diff --staged
git add path/to/file
git add path/to/fileA path/to/fileB
git restore --staged path/to/file
git commit -m "type(scope): message"
git log --oneline -n 10
git show --name-only --oneline HEAD
git fetch origin
git pull origin <branch>
git switch <branch>
git switch -c <new-branch>
```

### Blocked by Default

- `git reset --hard`
- `git clean -fd`
- `git checkout -- <file>`
- `git push --force` or `--force-with-lease`
- `git rebase` (unless explicitly requested)
- `git commit --amend` (unless explicitly requested)
- `git commit --no-verify` (unless explicitly requested)
- Any command that edits global/local git config

## Failure Handling

- If `git commit` fails due to hooks or formatting checks, fix issues and retry normal `git commit`.
- Do not bypass checks automatically.
- If branch setup is needed, run each step as separate commands.
- If a command is denied by policy, propose the minimal safe equivalent command.
