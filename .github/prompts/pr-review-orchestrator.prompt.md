---
name: PR Review Orchestration Prompt
agent: PR Review Orchestrator
description: Guides end-to-end PR review against issues, ADRs, and tests, then delegates fixes.
---

Use this runbook to coordinate PR review and remediation.

## Inputs
- `REPO`: `<owner/repo>` (required)
- `PR`: `<number or URL>` (optional; discover if missing - look in CAPABILITY_MANIFEST.yaml)
- `ADR_ROOT`: `durion/docs/adr` (default)
- `CONTRACT_GUIDE_PATH`: `domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md` (behavior source)
- `API_REFERENCE_PATH`: `domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md` (schema reference)
- `OPENAPI_PATH`: `durion-positivity-backend/pos-<module>/openapi.yaml` (authoritative schema source)
- `PROCESSING_FILE`: `PR-Review-Processing.md` (required run log file)
- `PLANNER_AGENT`: `PR Review Planner` (recommended)
- `REVIEWER_AGENT`: `PR Reviewer` (recommended)
- `CODER_AGENT`: `PR Fix Coder` (recommended)
- `TEST_AGENT`: `PR Test Fixer` (recommended)

## Objective
Review one pull request end-to-end, validate it against issues and ADRs, evaluate test quality/status, and delegate fixes until verification is complete.

## Procedure
1. Resolve PR:
   - if `PR` is provided, use it;
   - else list open PRs and select a candidate using issue linkage and recency.
2. Gather evidence:
   - PR metadata, changed files, commits
   - PR comments and review comments (required), including unresolved thread IDs
   - linked issues and acceptance criteria
   - ADRs relevant to changed modules
   - contract behavior guidance from `BACKEND_CONTRACT_GUIDE.md`
   - API/schema detail from OpenAPI and `BACKEND_API_REFERENCE.generated.md`
   - current test status (failing/passing signals)
3. Delegate plan creation to `PLANNER_AGENT`.
   - Require planner to write `## Plan` to `PROCESSING_FILE`.
4. Delegate review to `REVIEWER_AGENT` with full evidence pack.
   - Delegate write to `PLANNER_AGENT` in `mode: append_output` for `## Subagent Outputs`.
5. Split findings into:
   - production code fixes -> `CODER_AGENT` (include `comment_ref` targets)
   - test fixes -> `TEST_AGENT` (include `comment_ref` targets)
6. Run remediation loop:
   - dispatch findings
   - collect evidence
   - after each subagent run, call `PLANNER_AGENT` with `mode: append_output` so planner writes UTC timestamp, delegated objective, output, and validation result to `PROCESSING_FILE`
   - verify coding standards checklist in coder handoff
   - verify direct replies were posted for each targeted `comment_ref`
   - re-check unresolved findings/comments
7. If tooling supports thread resolution, resolve addressed threads; otherwise post explicit follow-up status comments.
8. Produce final summary.
   - Delegate final outcome write to `PLANNER_AGENT` in `mode: write_final_summary` under `## Final Summary` in `PROCESSING_FILE`.

## Required Final Summary
- PR analyzed
- evidence sources used (issues, ADRs, PR comments, tests)
- findings by severity
- code fixes completed
- test fixes completed
- PR comment thread coverage (replied/resolved/pending with IDs)
- final verification status
- unresolved blockers and owner
- processing log file path
