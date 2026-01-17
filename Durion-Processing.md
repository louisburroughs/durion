# Durion Processing Log

## Request
Update `scripts/story_update.py` to remove the nonexistent `story-authoring` CLI option and use only LLM providers (OpenAI/Gemini).

## Context
- Repo: /home/louisb/Projects/durion
- Current file in editor: scripts/story_update.py
- Observed behavior: `story-authoring` is not installed (`--help` returns exit code 127).
- Desired behavior: `story_update.py` should not advertise or attempt to use `story-authoring`.

## Action Plan

### 1) Remove local CLI path
- [x] Remove `story-authoring` checks and invocation.
- [x] Remove unused imports related to the CLI.
- [x] Update docstring to mention provider-only invocation.

### 2) Validation
- [x] `python3 -m py_compile scripts/story_update.py`
- [x] `python3 -m flake8 scripts/story_update.py`
- [x] Dry-run still runs: `python3 scripts/story_update.py --root ../work-stories --dry-run`

## Summary
- Updated `scripts/story_update.py` to remove the `story-authoring` CLI option.
- The script now supports provider-only invocation (OpenAI/Gemini) and remains lint/compile clean.
- Aligned OpenAI defaults with `scripts/story-output-consolidation/generate-domain-business-rules.sh`: default model `gpt-5.2`, base URL from `OPENAI_API_BASE` (default `https://api.openai.com/v1`), and optional `OPENAI_ORG` header.
- Fixed runtime failures:
  - Removed the external `requests` dependency by using Python stdlib `urllib` for HTTP.
  - Updated OpenAI request to use `max_completion_tokens` (model `gpt-5.2` rejects `max_tokens`).
  - Implemented provider preflight to fail fast on misconfiguration and made `--verbose` print per-item failures plus an aggregated failure summary.

## Follow-up Request
Fix the progressive scheduler so dry-run processes all pending items (it was only processing one due to a loop indentation/break issue).

## Follow-up Action Plan
- [x] Fix the in-flight completion loop to process exactly one completed future per iteration using `next(as_completed(...))`.
- [x] Re-run compile + lint.
- [x] Re-run dry-run and confirm `processed` reflects all pending items.

## Final Summary
- Fixed the scheduler loop in `scripts/story_update.py` so it processes all pending items (and still supports stop-on-consecutive-429).
- Verified locally:
  - `python3 -m py_compile scripts/story_update.py`
  - `python3 -m flake8 scripts/story_update.py`
  - Dry-run processes all items (example run returned `{"processed": 186, "updated": 0, "skipped_no_pattern": 0, "failed": 0}`).
