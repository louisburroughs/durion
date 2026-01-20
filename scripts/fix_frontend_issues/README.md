fix_issue script

Usage:

```
python3 fix_issue.py <domain> <path/to/after.md> [--print-copilot] [--agent-ref AGENT_REF]
```

Environment:

- If calling GitHub APIs, set `GITHUB_TOKEN` in environment (not required for file-only mode).

Outputs:

- `fixed.md` written next to `after.md` with YAML frontmatter labels and updated Markdown body.
- When `--print-copilot` is supplied the script prints a JSON payload to stdout suitable for sending to Copilot.
