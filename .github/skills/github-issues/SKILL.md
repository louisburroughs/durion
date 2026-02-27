---
name: github-issues
description: 'Create, update, and manage GitHub issues using MCP tools. Use this skill when users want to create bug reports, feature requests, or task issues, update existing issues, add labels/assignees/milestones, or manage issue workflows. Triggers on requests like "create an issue", "file a bug", "request a feature", "update issue X", or any GitHub issue management task.'
---

# GitHub Issues

Manage GitHub issues using the GitHub MCP server (`io.github.github`).

## Available MCP Tools

The same tool may appear with different naming depending on runtime:

- MCP registry command path: `io.github.github/<tool>`
- Runtime alias (example): `mcp__github__<tool>`

| Registry Command | Runtime Alias (example) | Purpose |
|------------------|-------------------------|---------|
| `io.github.github/create_issue` | `mcp__github__create_issue` | Create new issues |
| `io.github.github/update_issue` | `mcp__github__update_issue` | Update existing issues |
| `io.github.github/get_issue` | `mcp__github__get_issue` | Fetch issue details |
| `io.github.github/search_issues` | `mcp__github__search_issues` | Search issues |
| `io.github.github/add_issue_comment` | `mcp__github__add_issue_comment` | Add comments |
| `io.github.github/list_issues` | `mcp__github__list_issues` | List repository issues |

## Workflow

1. **Determine action**: Create, update, or query?
2. **Gather context**: Get repo info, existing labels (or confirm label names), milestones if needed
3. **Structure content**: Use appropriate template from [references/templates.md](references/templates.md)
4. **Execute**: Call the appropriate MCP tool (`io.github.github/...` or runtime alias)
5. **Confirm**: Report the issue URL to user

## Creating Issues

### Required Parameters

```
owner: repository owner (org or user)
repo: repository name  
title: clear, actionable title
body: structured markdown content
```

### Optional Parameters

```
labels: ["bug", "enhancement", "documentation", ...]
assignees: ["username1", "username2"]
milestone: milestone number (integer)
```

## Label Safety (Required)

- Do not assume custom labels exist in the target repository.
- If label availability is unknown, create/update the issue without labels first.
- Add labels only after confirming exact label names with the user or repository context.
- If user requests labels that may not exist (for example `high-priority`), ask for fallback labels.

### Title Guidelines

- Start with type prefix when useful: `[Bug]`, `[Feature]`, `[Docs]`
- Be specific and actionable
- Keep under 72 characters
- Examples:
  - `[Bug] Login fails with SSO enabled`
  - `[Feature] Add dark mode support`
  - `Add unit tests for auth module`

### Body Structure

Always use the templates in [references/templates.md](references/templates.md). Choose based on issue type:

| User Request | Template |
|--------------|----------|
| Bug, error, broken, not working | Bug Report |
| Feature, enhancement, add, new | Feature Request |
| Task, chore, refactor, update | Task |

## Updating Issues

Use `io.github.github/update_issue` (or runtime alias) with:

```
owner, repo, issue_number (required)
title, body, state, labels, assignees, milestone (optional - only changed fields)
```

State values: `open`, `closed`

## Examples

### Example 1: Bug Report

**User**: "Create a bug issue - the login page crashes when using SSO"

**Action**: Call `io.github.github/create_issue` (or runtime alias) with:
```json
{
  "owner": "github",
  "repo": "awesome-copilot",
  "title": "[Bug] Login page crashes when using SSO",
  "body": "## Description\nThe login page crashes when users attempt to authenticate using SSO.\n\n## Steps to Reproduce\n1. Navigate to login page\n2. Click 'Sign in with SSO'\n3. Page crashes\n\n## Expected Behavior\nSSO authentication should complete and redirect to dashboard.\n\n## Actual Behavior\nPage becomes unresponsive and displays error.\n\n## Environment\n- Browser: [To be filled]\n- OS: [To be filled]\n\n## Additional Context\nReported by user.",
  "labels": ["bug"]
}
```

### Example 2: Feature Request

**User**: "Create a feature request for dark mode with high priority"

**Action**: Call `io.github.github/create_issue` (or runtime alias) with:
```json
{
  "owner": "github",
  "repo": "awesome-copilot",
  "title": "[Feature] Add dark mode support",
  "body": "## Summary\nAdd dark mode theme option for improved user experience and accessibility.\n\n## Motivation\n- Reduces eye strain in low-light environments\n- Increasingly expected by users\n- Improves accessibility\n\n## Proposed Solution\nImplement theme toggle with system preference detection.\n\n## Acceptance Criteria\n- [ ] Toggle switch in settings\n- [ ] Persists user preference\n- [ ] Respects system preference by default\n- [ ] All UI components support both themes\n\n## Alternatives Considered\nNone specified.\n\n## Additional Context\nHigh priority request.",
  "labels": ["enhancement"]
}
```

If `high-priority` exists in the repo, add it in a follow-up update.

## Common Labels (Use Only If Present in Repo)

Use these standard labels when applicable:

| Label | Use For |
|-------|---------|
| `bug` | Something isn't working |
| `enhancement` | New feature or improvement |
| `documentation` | Documentation updates |
| `good first issue` | Good for newcomers |
| `help wanted` | Extra attention needed |
| `question` | Further information requested |
| `wontfix` | Will not be addressed |
| `duplicate` | Already exists |
| `high-priority` | Urgent issues |

## Tips

- Always confirm the repository context before creating issues
- Ask for missing critical information rather than guessing
- Treat labels as repo-specific; do not assume availability
- Link related issues when known: `Related to #123`
- For updates, fetch current issue first to preserve unchanged fields
