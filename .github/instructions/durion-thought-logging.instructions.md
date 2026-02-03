---
name: 'Thought Logging Instructions'
applyTo: '**'
description: 'See process the agent is following where you can edit this to reshape the interaction or save when follow up may be needed'
---

# Durion Process tracking Instructions

## File Location (MANDATORY)

- `Durion-Processing.md` MUST ONLY exist in the Durion repo root: `~/Projects/durion/Durion-Processing.md`.
- Do NOT create, modify, or reference `Durion-Processing.md` in any other repo (for example `durion-positivity-backend/` or `durion-moqui-frontend/`).
- Any instruction below that says "workspace root" MUST be interpreted as the Durion repo root (`~/Projects/durion/`) only.

**ABSOLUTE MANDATORY RULES:**
- Exit these instructions when given the command "EXIT DURION PROCESSING INSTRUCTIONS"
- You must review these instructions in full before executing any steps to understand the full instructions guidelines.
- You must follow these instructions exactly as specified without deviation.
- Do not keep repeating status updates while processing or explanations unless explicitly required. This is bad and will flood Copilot session context.
- NO phase announcements (no "# Phase X" headers in output)
- Phases must be executed one at a time and in the exact order specified.
- NO combining of phases in one response
- NO skipping of phases
- NO verbose explanations or commentary
- Only output the exact text specified in phase instructions

# Phase 1: Initialization

- Delete existing file `~/Projects/durion/Durion-Processing.md` (Durion repo root) if it is marked as complete. If not marked complete, check if the question or demand is relevant to the contents of the file. If the contents are stale, delete without asking. If there is a doubt if this is a continuation, ask if you should append to it or start a new one.
- Create file `~/Projects/durion/Durion-Processing.md` (Durion repo root only)
- Populate `~/Projects/durion/Durion-Processing.md` with user request details
- Work silently without announcements until complete.
- When this phase is complete keep mental note of this that <Phase 1> is done and does not need to be repeated.

# Phase 2: Planning

- Generate an action plan into the `Durion-Processing.md` file.
- Generate detailed and granular task specific action items to be used for tracking each action plan item with todo/complete status in the file `Durion-Processing.md`.
- This should include:
  - Specific tasks for each action item in the action plan as a phase.
  - Clear descriptions of what needs to be done
  - Any dependencies or prerequisites for each task
  - Ensure tasks are granular enough to be executed one at a time
- Work silently without announcements until complete.
- When this phase is complete keep mental note of this that <Phase 2> is done and does not need to be repeated.

# Phase 3: Execution

- Execute action items from the action plan in logical groupings/phases
- Work silently without announcements until complete.
- Update file `Durion-Processing.md` and mark the action item(s) as complete in the tracking.
- When a phase is complete keep mental note of this that the specific phase from `Durion-Processing.md` is done and does not need to be repeated.
- Repeat this pattern until all action items are complete

# Phase 4: Summary

- Add summary to `Durion-Processing.md`
- Work silently without announcements until complete.
- Execute only when ALL actions complete
- Inform user: "Added final summary to `Durion-Processing.md`."
- Remind user to review the summary and confirm completion of the process then to remove the file when done so it is not added to the repository.
- **Mark the file as complete!!.**

**ENFORCEMENT RULES:**
- NEVER write "# Phase X" headers in responses
- NEVER repeat the word "Phase" in output unless explicitly required
- NEVER provide explanations beyond the exact text specified
- NEVER combine multiple phases in one response
- NEVER continue past current phase without user input
- If you catch yourself being verbose, STOP and provide only required output
- If you catch yourself about to skip a phase, STOP and go back to the correct phase
- If you catch yourself combining phases, STOP and perform only the current phase
- NEVER exit without completing OR giving clear STOP instructions unles s explicitly instructed to do so with "EXIT DURION PROCESSING INSTRUCTIONS"