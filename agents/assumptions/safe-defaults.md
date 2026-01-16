**Safe Defaults for Agents and Helper Scripts**

This document records conservative, secure, and recoverable defaults that agent code and helper scripts in this workspace should follow. These defaults are intentionally defensive to reduce risk during automation and to make runs resumable, auditable, and recoverable.

- **Secrets & Credentials**: Never hardcode secrets. Read tokens and keys exclusively from environment variables or a secrets store. Fail fast with a clear error if required credentials are missing.

- **Dry-run First**: Scripts that change external state (GitHub, repos, issue edits, labels, deployments) should support a `--dry-run` mode that shows intended actions without making changes.

- **Explicit Opt-in for Destructive Actions**: Require an explicit flag (e.g., `--force`, `--apply`) to perform destructive or irreversible actions. By default, run in a non-destructive mode.

- **Atomic Writes & Plan Files**: When writing progress or plan files, write to a temp file and atomically rename into place. Keep a single authoritative plan file per long-running operation so interrupted runs can resume deterministically.

- **Locking & Concurrency**: Use a simple lock file (`.lock`) with process PID and timestamp to prevent concurrent runs against the same output directory. Respect existing recent locks unless `--force` is provided.

- **Idempotency & Resume**: Design operations so repeated runs skip already-completed work (check plan/completed entries and existing outputs). Record per-item success/failure with timestamps in the plan file.

- **Rate Limiting & Backoff**: Handle remote API rate limits gracefully (inspect rate headers where available). Implement exponential backoff with jitter on transient network or 429/403-with-zero-remaining responses.

- **Minimal Privilege**: Use the least-privilege token/scopes needed (e.g., repo:issues vs full repo) and document required scopes in README or script header.

- **Safe Logging**: Avoid logging secrets or large PII. Log enough detail to debug (endpoints, issue numbers, error messages) but sanitize tokens and personal data.

- **Validation & Fail-Fast**: Validate inputs and remote resources early (e.g., repository existence, token access) and fail with actionable messages rather than proceeding with partial state.

- **Retry Policy**: For per-item transient failures, retry a small configurable number of times before marking the item `failed` in the plan and continuing to the next item.

- **Clear Exit Codes & Summaries**: Exit 0 for normal completion (even with per-item failures recorded), non-zero for fatal unrecoverable errors. Print a concise JSON summary at the end of runs.

- **Preserve Originals**: When transforming or rewriting artifacts, preserve the original (e.g., keep `before.md` unchanged and write rewritten output to a distinct file) and include traceability metadata (origin URL, timestamps).

- **Documentation**: Document required env vars, expected repo scopes, and plan file schema adjacent to the script (script header or README) so operators can run or resume safely.

Use these defaults as the baseline for new extraction, rewrite, and publish scripts in this workspace.
