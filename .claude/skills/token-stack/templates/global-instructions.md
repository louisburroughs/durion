---
name: token-stack
description: "Set up a machine or repository for minimum AI agent token usage. Trigger when the user asks to install the token stack, reduce token usage, set up token optimization, or configure RTK / Repomix / TokenSave. Handles both one-time machine setup and per-repo setup."
---

# Token Stack Skill

Two workflows. Ask the user which they need (or infer: no RTK on
PATH means machine setup hasn't happened).

## Machine setup (once)

Follow `prompts/machine-setup.prompt.md` in this skill folder.
Ask the preamble questions first; adapt to OS, MCP availability,
and billing model. Write `templates/global-instructions.md` into
~/.claude/CLAUDE.md (append under a "# Token rules" header,
deduplicating if present).

## Project setup (per repo)

Follow `prompts/project-setup.prompt.md`. Write
`templates/copilot-instructions.md` content into the repo's
CLAUDE.md instead of .github/copilot-instructions.md when the
agent is Claude Code. Offer the post-commit hook; never install
it silently.

## Rules

- Idempotent: safe to re-run; check before duplicating config.
- Never install hooks or services without explicit approval.
- Finish with a report: installs, config paths, measurement
  command (`rtk gain`).
