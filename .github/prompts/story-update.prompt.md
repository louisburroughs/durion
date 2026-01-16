---
name: 'Story Update Script for GitHub Issues'
agent: 'agent'
description: 'Create a script that crawls a local extraction tree of frontend stories, finds stories missing an after.md, extracts a pattern from the before.md, summarizes related backend stories, and invokes the Story Authoring Agent to produce an updated after.md for each matched frontend story.'
model: GPT-5 mini (copilot)
---

This prompt asks you to produce a safe, resumable script (Python 3.10+) that processes frontend story folders created by the extraction script (see story-extract.prompt.md). The script will:

- Accept a required `--root` path argument pointing at the extraction root (the directory containing `frontend/` and `backend/`).
- Crawl `--root/frontend/*/` directories and find any folder that contains `before.md` but does not contain `after.md`.
- For each candidate frontend story:
	- Read `before.md` and search for a text value using the placeholder `r"Original Story\s+Story:\s+#\d{1,3}\s+-\s+.+?:\s+.+"` (this placeholder will be a grep-/Python `re`-compatible regex provided at runtime via `--pattern`).
	- Generate a short summary of the frontend issue (Title, first paragraph, labels if present). Use the matched pattern value extracted from `before.md` to grep the backend extraction tree under `--root/backend/` for related stories — this effectively locates related backend items by searching for the value found in the frontend issue. Include context lines and the backend file path for each match in the summary.
	- Build a Story Authoring Agent prompt that includes (exact ordering is important):
		- The full Story Authoring Agent text loaded verbatim from an `--agent-file` (if supplied) and included near the top of the constructed prompt.
		- Domain-specific agent inclusion: inspect the backend match metadata (preferably via the extraction plan entry for that backend artifact; alternatively a `labels:` block or header in the backend markdown) for a label that begins with `domain:`. For each unique `domain:<name>` found among the backend matches, attempt to load `durion/.github/agents/domains/<name>.md` (or `.txt`) and append the full verbatim contents of each found domain agent immediately after the Story Authoring Agent block. Include each unique domain agent only once, ordered alphabetically by domain name.

		- Business rules inclusion: when loading a domain-specific agent file, search its text for the literal marker `**Business Rules:** durion/domains/{domain}/.business-rules/` (substituting `{domain}` with the domain name). If the marker is present, look for files under `durion/domains/{domain}/.business-rules/` and append the contents of each file (plain text) to the prompt after the corresponding domain agent block. Include a short header before each appended business-rules file indicating its relative path. If no such directory or files exist, continue without error but record a warning in the run logs and the plan entry.
		- A one-line summary of the frontend `before.md` (title and short excerpt).
		- The backend search results (file path + one-line excerpt for each match). If backend matches include extraction-plan IDs, include those IDs so the agent can reference backend issue metadata.
		- The matched pattern value and the location in `before.md` where it was found.
		- Clear instruction: "Review and update the issue: produce an implementation-ready rewrite (`after.md`) following the Story Authoring Agent rules and any appended domain-specific agent guidance. Preserve the original `before.md` content in the `Original Story (Unmodified – For Traceability)` section at the end."

The script must:

- Be resumable: create and maintain an atomic progress plan file at `<root>/.story_update_plan.json` with schema similar to the extraction plan (`pending`, `processing`, `completed`, `failed`, config, timestamps). The plan must record the `agent_file` path used for the run (if any).
- Support CLI flags: `--root PATH` (required), `--pattern '<regex>'` (required), `--agent-file PATH` (optional; path to a file containing the Story Authoring Agent text and instructions), `--concurrency N` (default 1), `--dry-run`, `--force-refresh-plan`, `--verbose`.
- Respect safe defaults (`agents/assumptions/safe-defaults.md`): `GITHUB_TOKEN` only for any GitHub actions (not required for local file updates), atomic writes, `.lock` file, dry-run mode.
- Create `after.md` files in the same folder as `before.md` only when the agent output validates (non-empty, includes `## Original Story (Unmodified – For Traceability)` at end). In `--dry-run` mode, print the intended `after.md` path and a short preview.
- Use an LLM/agent invocation method of your choice but keep it pluggable (environment-driven): prefer calling a local `story-authoring` CLI if present, else call an LLM via environment variables (e.g., `OPENAI_API_KEY` or `GEMINI_API_KEY`). Fail with a helpful error message if no provider is configured.
- Validate outputs: ensure the returned `after.md` contains required headings (`## 🏷️ Labels (Proposed)`, `**Rewrite Variant:**`, required story sections, and `## Original Story (Unmodified – For Traceability)`).
- Write the progress plan atomically (temp file + rename) after each story is processed and record `completed[issue_dir] = {file: relative_path, processed_at: iso}` or `failed[...]` with error.
- Continue processing after per-item failures; do not abort entire run unless the plan file is corrupted or lock prevents progress.

Implementation guidance and examples:

- The script should produce helpful console output and a final JSON summary: `{processed: n, updated: m, skipped_no_pattern: k, failed: f}`.
- Use `grep` or Python `re` to search backend `*.md` files for the matched pattern value; include up to 3 matches per frontend story in the summary given to the agent.
- If multiple backend matches are found, include them all in the prompt so the Story Authoring Agent can reason across them.
- Use the `rewrite_story.py` workspace model as a reference for safe workspace creation, atomic writes, and publish patterns, but the update script must write `after.md` files locally (do not auto-publish to GitHub unless a `--publish` flag and token are provided).

CLI usage examples (include these exact commands in script docstring):

python story_update.py --root ./stories --pattern "r\"Original Story\\s+Story:\\s+#\\d{1,3}\\s+-\\s+.+?:\\s+.+\"" --dry-run

python story_update.py --root ./stories --pattern "r\"Original Story\\s+Story:\\s+#\\d{1,3}\\s+-\\s+.+?:\\s+.+\"" --concurrency 4

Design deliverables expected from this prompt:

- A single executable script `story_update.py` in Python implementing the behavior above.
- A short README block at top explaining required env vars, the plan file format, and safety notes.
- `--dry-run` implementation that shows which frontend directories would be updated and the agent prompt that would be sent for the first 3 items.
- Unit-testable core functions for: scanning for missing after.md, extracting pattern from `before.md`, searching backend files, building the agent prompt, validating agent output, atomic plan updates.

Constraints & edge cases:

- Frontend issues must be processed regardless of labels; `skip_label` only applied for backend extraction in the upstream extraction step.
- If `before.md` has no match for `<pattern placeholder>`, record it in the plan as `skipped_no_pattern` with timestamp and continue.
- If `after.md` already exists, skip unless `--force-refresh-plan` is set (which should add the folder back into `pending`).
- If the Story Authoring Agent output does not include the `Original Story (Unmodified – For Traceability)` section, mark the item `failed` and include the agent output in the failure record.

Ask for clarifying details only if the `<pattern placeholder>` form (regex flavor) is unclear or if the user wants a different LLM/agent invocation method. Otherwise produce the script and tests as specified.
