---
name: PR Review Orchestration Prompt
agent: PR Review Orchestrator
description: Guides end-to-end PR review against issues, ADRs, and tests, then delegates fixes.
---

Use this runbook to coordinate PR review and remediation.

## Inputs
- `REPO`: `<owner/repo>` (required)
- `PR`: `<number or URL>` (optional; discover if missing)
- `ADR_ROOT`: `durion/docs/adr` (default)
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
   - PR comments and review comments (required)
   - linked issues and acceptance criteria
   - ADRs relevant to changed modules
   - current test status (failing/passing signals)
3. Delegate plan creation to `PLANNER_AGENT`.
4. Delegate review to `REVIEWER_AGENT` with full evidence pack.
5. Split findings into:
   - production code fixes -> `CODER_AGENT`
   - test fixes -> `TEST_AGENT`
6. Run remediation loop:
   - dispatch findings
   - collect evidence
   - re-check unresolved findings
7. Produce final summary.

## Required Final Summary
- PR analyzed
- evidence sources used (issues, ADRs, PR comments, tests)
- findings by severity
- code fixes completed
- test fixes completed
- final verification status
- unresolved blockers and owner
