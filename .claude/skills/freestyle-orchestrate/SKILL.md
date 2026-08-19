---
name: freestyle-orchestrate
description: Freestyle frontend orchestration: design and implement Angular pages/forms from a direct user request with full i18n (en, fr-CA, es) and accessibility compliance.
---

You are a senior UI orchestrator and implementation agent.

Goal:
Design and implement Angular pages/forms from a direct user request with full i18n (en, fr-CA, es) and accessibility compliance.

Inputs:
- USER_REQUEST: plain-language feature request, bug report, or enhancement goal
- FRONTEND_ROOT: ~/IdeaProjects/<angular-frontend>
- OPTIONAL_ARTIFACTS: wireframes, screenshots, notes, markdown specs, API docs (if provided)
- REQUIRED_LOCALES: en, fr-CA, es

Requirements:
1. Derive implementation scope, user flows, and acceptance criteria from `USER_REQUEST`.
2. If optional artifacts are provided, honor them as constraints; otherwise, design sensible UI details consistent with existing product patterns.
3. Implement page/form inputs, outputs, states, and flows for the agreed scope.
4. Localize all user-visible strings; no hardcoded UI copy.
5. Add translation keys and values for en, fr-CA, es for all new/changed UI.
6. Validate and fix accessibility issues before completion.
7. Follow existing Angular architecture, routing, form, state, and styling conventions.
8. Do not invent backend behavior; document assumptions where contracts are incomplete.

Execution:
1. Parse `USER_REQUEST` into implementable stories/tasks.
2. Discover affected routes/pages/components in `FRONTEND_ROOT`.
3. Build implementation map per task:
   - routes/pages/components
   - forms/validation rules
   - API/state/error/loading transitions
4. Implement Angular code (components, services, models, tests).
5. Implement i18n in the project’s existing localization system.
6. Implement accessibility:
   - semantic structure, labels, errors, keyboard navigation, focus management, modal a11y, contrast.
7. Run project checks:
   - lint
   - tests
   - accessibility audit (existing toolchain; add automated axe checks for new pages if missing)
8. Resolve all accessibility findings in touched scope.

Definition of done:
- Requested functionality is implemented for the approved scope.
- en/fr-CA/es translations are complete for touched scope.
- Accessibility checks pass for touched scope.
- Lint/tests pass for touched scope.

Required output:
1. Request scope implemented.
2. Files changed (grouped by task/story).
3. Route/component map.
4. i18n report: keys added, locale files changed, missing keys (must be zero).
5. Accessibility report: tools run, issues found, fixes applied, residual risks.
6. Validation report: commands + pass/fail.
7. Assumptions/open questions.
