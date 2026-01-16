---
name: 'Story Extraction Script for GitHub Issues'
agent: 'agent'
description: 'Create a Python script that extracts GitHub issues from two repositories and writes them into a filesystem layout suitable for downstream processing, with resumable capabilities and a planning/progress file.'
model: GPT-5 mini (copilot)
---

Create a Python script that extracts GitHub issues from two repositories (durion-moqui-frontend and durion-positivity-backend) and writes each issue into a filesystem layout suitable for downstream processing. The script must be resumable and must create and maintain a planning/progress file so an interrupted run can pick up where it left off.

Details:
- Language & environment:
	- Write the script in Python 3.10+.
	- Use only commonly available libraries (`requests`, `json`, `os`, `pathlib`, `time`, `logging`). If you prefer a library like `PyGithub`, make it optional and include fallback implementation with `requests`.
	- The script must read GitHub credentials only from `GITHUB_TOKEN` environment variable (do not hardcode tokens).

- Inputs / CLI:
	- `--owner` (default: project owner or org)
	- `--frontend-repo` (default: durion-moqui-frontend)
	- `--backend-repo` (default: durion-positivity-backend)
	- `--out-dir` (default: stories)
	- `--readiness-labels` (default: status:ready-for-dev,status:needs-review) — comma-separated labels indicating backend issues are ready to be considered "already updated"
	- `--skip-label` (default: status:draft) — label name; issues with this label will be skipped for **backend** extraction only; frontend issues are extracted regardless of labels
	- `--per-page` (pagination, default 100)
	- `--dry-run` flag
	- `--verbose` flag

- Filesystem layout (exact):
	- Root output directory: `<out-dir>` (default `stories`)
	- Frontend issues: `<out-dir>/frontend/<issue_number>/before.md` (filename exactly `before.md`)
	- Backend issues that are considered "already been updated": `<out-dir>/backend/<issue_number>/backend.md` (filename exactly `backend.md`)
	- Progress/plan file: `<out-dir>/extraction_plan.json` — REQUIRED. This file must be created and updated atomically and must contain enough state to resume. Do not overwrite it blindly.

- Content written to each issue file:
	- Exact file content must contain: Title, URL, Labels, and then the issue body, in this plain-text format (use this exact layout):
		Title: <issue title>
		URL: <issue html_url>
		Labels: <label1>, <label2>, ...
    
		<issue body possibly multi-line>
	- If the issue body is empty, leave the body section empty but still write the header lines.
	- Labels must be the literal label names joined by comma+space.

- Progress/plan file schema and behavior:
	- The `extraction_plan.json` must be JSON and contain at least these top-level keys:
		- `version` (string)
		- `created_at` (ISO timestamp)
		- `updated_at` (ISO timestamp)
		- `owner` (string)
		- `frontend_repo` (string)
		- `backend_repo` (string)
		- `readiness_labels` (list of strings)
		- `skip_label` (string)
		- `state` — an object with:
			- `pending_frontend`: list of issue numbers not yet processed
			- `pending_backend`: list of issue numbers not yet processed (subject to updated criteria)
			- `processing`: list of issue numbers currently being processed (optional)
			- `completed`: object mapping issue_number -> { repo: "frontend"|"backend", file: "<relative path>", processed_at: ISO }
			- `failed`: object mapping issue_number -> { repo, error, last_attempt: ISO }
	- On start:
		- If `extraction_plan.json` does not exist, the script must create it and populate `pending_frontend` and `pending_backend` by fetching issue numbers from the GitHub API (apply repo filter: state=open; include only open issues). Do NOT include issues that have the configured `skip_label` (e.g., `status:draft`) in `pending_backend`; include frontend issues regardless of labels.
		- If the plan file exists, the script must load it and continue processing only `pending_*` items. Do not re-fetch or reset pending lists unless `--force-refresh-plan` (optional) is given.
	- During processing of each issue:
		- Move the issue from `pending_*` -> `processing`, write the output file, then move `processing` -> `completed` with processed_at timestamp and the path to the created file. Write the plan file to disk after each issue is finished (success or failure).
		- On error for a given issue, log the error, record it in `failed` with last_attempt timestamp and do not exit the entire run (continue to next issue). Optionally retry a configurable number of times per issue before marking permanently failed.
	- The plan file must be written atomically (e.g., write to a temp file then rename).

- Rules for deciding "backend stories that have already been updated":
	- Use the presence of any label listed in `readiness_labels` (default: `status:ready-for-dev`, `status:needs-review`): if a backend issue has one or more of those labels, treat it as "already been updated" and write its file into `backend/<number>/backend.md`.
	- Skip issues that have the `skip_label` (default: `status:draft`) for backend processing only — such issues should not be added to `pending_backend` and should not be processed as backend stories. Frontend issues are extracted regardless of the skip label.
	- Note: closed backend issues will not be extracted because this script only collects open issues. Use GitHub or another process to collect closed items if needed.
	- Backend issues that do not meet the "ready" criteria should not create files in `backend/` and should remain in `pending_backend` (so future runs can act on them after labels or status change).

- Behavior for frontend issues:
	- For every frontend issue fetched, create directory `<out-dir>/frontend/<issue_number>/` and write file `before.md` with the content format specified above, then mark it completed in the plan.

- Additional useful outputs:
	- After the run finishes (or after processing each page), print a short JSON summary: {frontend_processed: n, backend_processed: m, backend_skipped_not_updated: k, failed: f}
	- Verbose logging when `--verbose` is set.

- Robustness & rate limits:
	- Handle GitHub API pagination and rate limiting (back off and retry on 403 with `X-RateLimit-Remaining` 0 or on 429).
	- Respect network errors: exponential backoff with jitter.
	- Avoid duplicate processing when resuming: skip issues already present in `completed` map.

- Testing & usage examples (include these exact commands in the README or script docstring):
	- Example command (default owner if provided):
		python extract_stories.py --owner louisburroughs --out-dir stories
	- With custom readiness labels and a custom skip label:
		python extract_stories.py --owner louisburroughs --readiness-labels "status:ready-for-dev,status:needs-review" --skip-label status:draft
	- Dry run:
		python extract_stories.py --owner louisburroughs --dry-run

- Example output file contents (exact example; use placeholders showing where values go):
	Title: Example issue title
	URL: https://github.com/<owner>/<repo>/issues/123
	Labels: enhancement, ui
  
	This is the full issue body text. It can be multiple paragraphs and include markdown.

- Edge cases and constraints:
	- Ensure directories are created with safe names (issue numbers are numeric so safe).
	- If an issue has the same number in both repos (rare), the directories are independent because they're under `frontend/` and `backend/`.
	- If `extraction_plan.json` is corrupted or unreadable, the script should fail with a clear error unless `--force-refresh-plan` is used.
	- File writes must handle concurrent runs: recommend acquiring a simple lock file `<out-dir>/.lock` at start; if lock exists and is recent, warn and exit (or allow `--force` to override).

- Deliverables from the model you are prompting:
	- A single Python script `extract_stories.py` implementing the above behavior.
	- A short README block at the top of the script or a separate `README.md` describing usage, dependencies, and the plan file format.
	- A minimal unit/integration test harness or simple `--dry-run` mode that does not write files but shows what it would do for the first 5 issues.
	- Clear, compact inline comments only where necessary (keep code readable).

# Steps (recommended implementation plan)
1. Parse CLI arguments and validate `GITHUB_TOKEN` env var.
2. Load or create `extraction_plan.json` (fetch issue lists if creating).
3. Iterate pending frontend issues: for each, write `before` file, update plan.
4. Iterate pending backend issues: for each, check updated criteria; if updated, write `backend` file and update plan; otherwise skip and keep in pending.
5. Save plan after each issue and print progress summary.
6. Exit with non-zero exit code only if more than a threshold of fatal errors occurred (configurable).

# Output Format (what the script must produce)
- Files in the filesystem exactly as described (no extensions on `before` or `backend` filenames).
- `extraction_plan.json` with the schema above.
- Console JSON summary at the end of the run (one-line JSON).
- Exit status 0 on normal completion (even if some issues failed but plan saved); non-zero only for fatal unrecoverable errors.

# Examples (start -> expected result)
- Input: run `python extract_stories.py --owner louisburroughs --out-dir stories`
- Expected FS changes:
	- stories/
		extraction_plan.json
		frontend/
			101/
				before.md          (contains Title/URL/Labels + body)
			102/
				before.md
		backend/
			45/
				backend.md
			99/
				backend.md
- Expected final console (example):
	{"frontend_processed": 12, "backend_processed": 4, "backend_skipped_not_updated": 38, "failed": 1}

# Notes
- Ask clarifying questions only if the token sourcing, owner, or updated-label criteria are unknown — otherwise implement sensible defaults (`GITHUB_TOKEN` env var, owner parameter required or defaults to `louisburroughs`).
- The next prompt (the user said) will create a processing prompt; ensure the files are organized exactly as specified so that step can consume them deterministically.

Note: Use `/home/louisb/Projects/durion-positivity-backend/.story-work/tools/rewrite_story.py` as an implementation example — it creates a per-issue workspace (via `make_rewrite_workspace.sh`) with `before.md` and `after.md`, calls an LLM, then publishes with `publish_rewrite.sh`. The extraction script you are prompting should follow the same safe patterns (workspace-safe writes, atomic plan updates, and compatible markdown `before.md` / `backend.md` files) but output into the two-repo `stories/frontend` and `stories/backend` layout described above.
