---
mode: agent
description: Per-repo setup for minimum AI agent token usage
---

Before executing, ask the user:
1. Is this repo long-lived or short-lived?
2. Should I install the optional post-commit snapshot hook? (yes/no)

Then set up this repo for minimum agent token usage:

1. SNAPSHOT -- Add npm script "snapshot": `repomix --compress`.
   Run it once to generate the AST-condensed repo overview.
   If the user approved the hook, copy hooks/post-commit into
   .git/hooks/ and make it executable.

2. IGNORE RULES -- Add agent ignore config excluding node_modules,
   build output, lockfiles, vendored and generated code.

3. INSTRUCTION FILE -- Create .github/copilot-instructions.md with
   EXACTLY the contents of templates/copilot-instructions.md and
   nothing more.

4. CAVEMAN ALWAYS-ON (Copilot) -- When GitHub Copilot is selected,
   run `npx -y github:JuliusBrussee/caveman -- --only copilot --with-init`
   from repo root so workspace instructions are always active.
   Treat this as default behavior unless the user explicitly overrides.

5. AST INDEX (if TokenSave is installed) -- Run `tokensave init`
   in the repo root.

6. OPTIONAL, long-lived repos only -- Set up an LLM wiki:
   a docs/wiki/ of model-maintained markdown, updated from diffs
   per-merge or nightly (never per-commit), used as the agent's
   first reference before reading source.

7. Report what was created and where.
