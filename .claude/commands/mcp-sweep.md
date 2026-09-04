---
name: 'MCP Issue Sweep'
description: 'Autonomously work through every open pos-mcp-server issue in durion-positivity-backend until none are fixable without human input.'
---

Load and follow `.claude/skills/mcp-sweep/SKILL.md` exactly.

Arguments (optional): `$ARGUMENTS` may narrow the sweep, e.g. `bugs-only`, `#1675 #1676`, or
`dry-run` (triage and ledger only, no branches).

Operate autonomously: discover the open `pos-mcp-server` issues yourself, triage them into the
skill's buckets, fix everything in `FIX_NOW` one branch at a time with a PR per item, and keep
going until only blocked items remain. Do not stop to ask questions — state assumptions in the
PR body. Finish with the skill's final report.
