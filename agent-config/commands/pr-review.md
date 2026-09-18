---
name: PR Review Orchestration Prompt
description: Guides end-to-end PR review against issues, ADRs, and tests, then delegates fixes.
---

Use this runbook to coordinate PR review and remediation.

## Inputs
- `REPO`: `<owner/repo>` (required)
- `PR`: `<number or URL>` (optional; discover if missing - look in CAPABILITY_MANIFEST.yaml)
- `REVIEW_TRACK`: `auto|backend|frontend` (default `auto`; infer from changed files/repo when `auto`)
- `CATALOG_ROOT`: `durion/knowledge-catalog` (default; resolve modules, ADRs, and domains here first)
- `ADR_ROOT`: `durion/docs/adr` (default)
- `CONTRACT_GUIDE_PATH`: `domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md` (behavior source)
- `API_REFERENCE_PATH`: `domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md` (schema reference)
- `OPENAPI_PATH`: `durion-positivity-backend/pos-<module>/openapi.yaml` (authoritative schema source)
- `FRONTEND_POLICY_PATHS`: optional list for frontend PRs (examples: `durion-positivity-frontend/AGENTS.md`, `durion/.claude/instructions/typescript.md`, `durion/.claude/instructions/angular-i18n.md`, `durion/.claude/instructions/html-css.md`, `durion-positivity-frontend/docs/EXEMPLARS.md`, frontend ADRs 0029–0041, 0062–0065)
- `FRONTEND_REQUIREMENTS_PATHS`: optional list of product/design requirements for frontend behavior and UX acceptance
- `PROCESSING_FILE`: `PR-Review-Processing.md` (required run log file)
- `PLANNER_AGENT`: `PR Review Planner` (recommended)
- `REVIEWER_AGENT`: `PR Reviewer` (recommended)
- `CODER_AGENT`: `PR Fix Coder` (recommended)
- `TEST_AGENT`: `PR Test Fixer` (recommended)
- `CODE_REVIEW_AGENT`: `PR Code Reviewer` (recommended)

## Navigation — Knowledge Catalog (mandatory first step)

Resolve every module, ADR, and domain through `CATALOG_ROOT` before opening source or planning work:

- Module → `<CATALOG_ROOT>/backend/<pos-module>.md`
- ADR → `<CATALOG_ROOT>/adr/index.md`, then the matching entry
- Domain → `<CATALOG_ROOT>/domains/<domain>.md`

An entry's `path:` field is the workspace-relative location of its source — the ADR file itself for
an ADR, the module or domain directory for those. Read there rather than at a guessed path, and
follow the entry's links to neighbouring concepts (owning domain, implementing modules, superseding
and related ADRs) before fixing scope.

When delegating, put this in the agent prompt — a subagent does not inherit this runbook:

> Resolve modules, ADRs, and domains through `<CATALOG_ROOT>` (`backend/`, `adr/`, `domains/`) before
> reading source — expand `CATALOG_ROOT` to its value when writing the prompt. Read at the entry's
> `path:` field — the ADR file itself, or the module or domain directory — and cite the catalog
> entries you used in your report.

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
   - ADRs relevant to changed modules (if applicable)
   - backend PRs: contract behavior guidance from `BACKEND_CONTRACT_GUIDE.md`
   - backend PRs: API/schema detail from OpenAPI and `BACKEND_API_REFERENCE.generated.md`
   - frontend PRs: UI requirements/design acceptance criteria from linked issues/docs
   - frontend PRs: accessibility, responsive behavior, and user-flow impact expectations
   - frontend PRs: policy guidance from `FRONTEND_POLICY_PATHS` when provided
   - current test status (failing/passing signals)
   - Read the BODY of every review submitted by `copilot-pull-request-reviewer[bot]`: its "Suppressed
     comments" section lists findings that are never posted as threads and never trigger a webhook;
     treat each as a finding with a `comment_ref` of the review id.
3. Delegate plan creation to `PLANNER_AGENT`.
   - Require planner to write `## Plan` to `PROCESSING_FILE`.
4. Delegate review to `REVIEWER_AGENT` with full evidence pack.
   - Delegate write to `PLANNER_AGENT` in `mode: append_output` for `## Subagent Outputs`.
5. Split findings into:
   - production code fixes -> `CODER_AGENT` (include `comment_ref` targets)
   - test fixes -> `TEST_AGENT` (include `comment_ref` targets)
6. Run remediation loop using this exact order until reviewer `PASS` or an explicit blocked condition is reached:
   - `CODER_AGENT` -> `TEST_AGENT` -> `CODE_REVIEW_AGENT`
   - `CODE_REVIEW_AGENT` must return `Verdict: PASS | FAIL`, findings, and recommended split
   - after each subagent run, call `PLANNER_AGENT` with `mode: append_output` so planner writes UTC timestamp, delegated objective, output, and validation result to `PROCESSING_FILE`
   - verify coding standards checklist in coder handoff (track-specific backend/frontend checks)
   - verify direct replies were posted for each targeted `comment_ref`
   - if `CODE_REVIEW_AGENT` returns `PASS`, exit loop
   - if `CODE_REVIEW_AGENT` returns `FAIL`, split findings and start next cycle
   - if reviewer continues returning `FAIL` without safe progress, mark blocked and include unresolved findings in final summary
   - after two incremental fix rounds that each drew a new review round, stop patching thread by
     thread: run one whole-diff adversarial review of the current head (a second reviewer agent with
     no prior context), fix every confirmed finding as one change set, verify each fix is load-bearing
     (revert, red, restore), and push once
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
