---
mode: agent
description: One-time machine setup for minimum AI agent token usage
---

Before executing, ask the user:
1. OS? (macOS / Linux / Windows)
2. Is MCP configured in your editor? (yes / no / unsure)
3. Billing model? (token-based API / premium requests / flat plan)
Adapt the steps below to the answers.

Then set up this machine for minimum AI agent token usage:

1. CLI COMPRESSION -- Install RTK (github.com/rtk-ai/rtk).
   Run `rtk init -g`. Verify shell commands rewrite through rtk.
   Run `rtk gain` for a savings baseline.
   Windows: use WSL, or RTK's instruction-file fallback mode.

2. REPO SNAPSHOT TOOL -- Install Repomix globally: `npm i -g repomix`.

3. OUTPUT COMPRESSION MODE -- Install Caveman
   (github.com/JuliusBrussee/caveman) and configure it as default-on.
   For Copilot-selected setup, run:
   `npx -y github:JuliusBrussee/caveman -- --only copilot --with-init`.
   For Claude-selected setup, run:
   `npx -y github:JuliusBrussee/caveman -- --only claude`.
   Confirm default behavior is active unless a user prompt explicitly
   requests a different style.

4. AST LOOKUP (only if MCP is configured) -- Install the TokenSave
   MCP server (`cargo install tokensave` or per its docs). Accept
   its built-in git post-commit hook when offered; its index
   self-maintains with staleness checks and incremental sync.

5. REASONING CAP -- If the agent supports a thinking/reasoning
   token limit, set it to ~10k.

6. GLOBAL INSTRUCTIONS -- Add the contents of
   templates/global-instructions.md to the editor's global agent
   instructions (VS Code: Settings -> Copilot instructions;
   Claude Code: ~/.claude/CLAUDE.md). This must state Caveman is the
   global default unless overridden by the user.

7. Report: what was installed, config locations, and these habits:
   - Start a fresh chat between unrelated tasks.
   - Group related edits into one prompt.
   - Use the cheapest model that handles the task.
   - Run `rtk gain` periodically to measure savings.
