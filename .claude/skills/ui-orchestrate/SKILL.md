---
name: ui-orchestrate
description: Policy for executing frontend delivery waves with the frontend agent team.
---

Run in strict compliance with `ui.orchestrator.agent.md`.

## Objective

Advance frontend delivery one wave at a time. Each wave targets approved work listed in the assigned execution tracking source and produces one PR in
`durion-positivity-frontend` that ships frontend component slices, tests, and updated documentation.

## Active Inputs

- Frontend repo policy: `durion-positivity-frontend/AGENTS.md`
- Assigned execution tracking source
- Supporting specifications and source materials: `durion/docs/capabilities/`
- Design reference: `durion-positivity-frontend/design/DESIGN.md`
- Style guide: `durion-positivity-frontend/design/source/durion-style-guide.md`
- Theme tokens: `durion-positivity-frontend/docs/theme-tokens.md`

## Repository Target Override (Mandatory)

- Frontend implementation MUST occur in `durion-positivity-frontend`.
- `durion` is a source-input repository for specifications, ADRs, contract guides, source materials, wireframes, and business rules.
- `durion-positivity-sdk` packages are consumed as npm dependencies; do not implement SDK changes in this mode.
- Backend repositories are source-input only; do not implement backend code in this mode.
- Any legacy instruction that implies backend implementation is superseded by this override.

## Frontend Hard Rules

- Angular 21 standalone components only — no NgModules.
- Use Angular Signals (`signal`, `computed`, `effect`) for reactive state.
- Never inject `HttpClient` directly in feature code; always use `ApiBaseService`.
- Inside `effect()` bodies: use `onCleanup(() => sub.unsubscribe())` — never `takeUntilDestroyed`.
- Outside `effect()` bodies (mutations etc.): use `takeUntilDestroyed(this.destroyRef)`.
- All user-facing strings must use `| translate` pipe — no hard-coded copies in templates.
- New translation keys must be added to all 4 locale files: `en-US.json`, `es-US.json`, `fr-CA.json`, `qps-ploc.json`.
- Server-generated fields (`id`, `createdAt`, `updatedAt`, `requestedAt`, etc.) are `readonly?` in model interfaces and must be omitted from create/update payloads.
- All inputs need associated `<label>` (visible or `sr-only`). Error messages via `role="alert"` or `aria-live`.
- Every component mutation error handler must call `this.state.set('error')` BEFORE `this.errorKey.set(...)`.
- Test fixtures must be explicitly typed as the exact domain interface.

## Frontend Agent Team

### Directly Callable by Orchestrator

- `UI Planner`
- `Lead Coder`
- `Designer`
- `TypeScript Specialist`
- `HTML Specialist`
- `Test Coverage Agent`
- `Code Review Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

### Not Used in This Mode

- `Backend Testing Agent`
- `API Surface Coder`
- `Domain Data Coder`
- `Client Coder`

Do not delegate to them for this mode.

## Delegation Allowlist (Hard Rule)

Only delegate to:

- `UI Planner`
- `Lead Coder`
- `Designer`
- `TypeScript Specialist`
- `HTML Specialist`
- `Test Coverage Agent`
- `Code Review Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

Forbidden:

- delegating backend implementation agents
- inventing agent aliases
- bypassing `Lead Coder` when specialist file ownership is unclear
- implementing code directly as Orchestrator

## Team Responsibilities

### UI Planner

- builds the wave execution plan by scanning the assigned execution tracking source
- identifies execute-now / normalize-first / blocked lanes
- sequences angular domain slices, story-to-file mapping, and validation gates
- maintains the assigned execution tracking source

### Lead Coder

- decomposes each execution slice into specialist ownership cards
- maps files, dependencies, and acceptance checks for frontend specialists
- validates specialist returns against the assigned specification package, ADRs, and Angular repo boundaries
- never writes code directly

### Designer

- produces the design brief for each execution slice
- references story wireframes and the Durion design system (`design/DESIGN.md`, theme tokens)
- must be consulted before HTML/CSS implementation begins

### TypeScript Specialist

- owns `*.ts` logic: routes, standalone components, services, models, state, API integration
- default write scope: `src/app/features/<domain>/`

### HTML Specialist

- owns `*.html` templates and `*.css` styles
- ensures semantic HTML, accessibility (`role`, `aria-*`, `<label>`), and responsive layout
- default write scope: `*.component.html`, `*.component.css`

### Test Coverage Agent

- writes and audits Angular/Vitest spec files
- validates coverage evidence
- default write scope: `*.spec.ts` co-located with component or service under test

### Code Review Agent

- performs frontend acceptance, ADR, and regression review
- reports findings only

### Documentation Agent

- updates the assigned execution tracking source and adjacent frontend documentation when assigned

### Coder

- legacy single-agent fallback when specialist delegation is blocked
- must still satisfy the same frontend PRD, test coverage, lint, and review gates

## Required Sequence

1. UI Planner creates validated wave plan.
2. Validate plan via plan-acceptance rules.
3. Create or switch execution branch in `durion-positivity-frontend`.
4. Read frontend AGENTS policy, required ADRs, the assigned execution tracking source, design references, and affected Angular domain baselines.
5. Load source hierarchy for each work item in the wave, in order:
   - story md in `durion/docs/capabilities/<CAP-*>/`
   - wireframe or design file from `design/`
   - contract guide or domain business rules from `durion/domains/<domain>/`
   - SDK package from relevant `durion-positivity-sdk` package (types, models, API client)
   - OpenAPI reference from SDK `openapi.yaml` as needed
6. For each execution slice in the wave: a. get design brief from `Designer` b. get assignment cards from `Lead Coder` c. delegate TypeScript implementation to
   `TypeScript Specialist` d. delegate template/style implementation to `HTML Specialist` e. integrate and validate combined results f. run `Test Coverage Agent` for coverage
   evidence g. run `Code Review Agent` h. iterate fixes until review PASS i. update the assigned execution tracking source via `Documentation Agent` when status changes
7. Run frontend verification gates.
8. Create the PR.
9. Verify PR was created and ask UI Planner to mark wave complete.

## Plan Acceptance Rules

Reject and return to UI Planner unless:

- the plan includes exact labels `Step 1:` and `Final Step:`
- Step 1 is source-material reading
- Final Step is PR creation in `durion-positivity-frontend` via `durion/.github/hooks/pull-request-hook.sh`
- the plan explicitly includes:
  - wave identification from the assigned execution tracking source
  - story-to-Angular-domain mapping for each work item in scope
  - specialist ownership per file group
  - verification commands: `npm run build`, `npx ng test --no-watch`, `npx ng lint`

## Delegation Templates

### A) UI Planner

- Scope: next-wave execution plan from the assigned execution tracking source
- Return:
  - wave identification and rationale
  - story-to-domain mapping
  - file ownership outline per specialist
  - verification gates
  - PR objective in `durion-positivity-frontend`

### B) Design Brief (Designer)

- Scope: one execution slice at a time
- Inputs: story md, wireframe(s), design system reference
- Return:
  - component hierarchy and UX decisions
  - token/style guidance aligned to Durion design system
  - accessibility and i18n requirements for the slice

### C) Implementation Decomposition (Lead Coder)

- Scope: one execution slice at a time
- Return:
  - file ownership matrix
  - `TypeScript Specialist` card (routes, component logic, services, models)
  - `HTML Specialist` card (templates, accessibility, CSS)
  - `Test Coverage Agent` card (spec file requirements)
  - dependency order
  - acceptance checklist referencing ADRs

### D) TypeScript Specialist

- Scope:
  - route definitions
  - standalone component class (`.ts`)
  - service and API integration
  - model interfaces
  - signal state management
- Return:
  - changed files
  - component and service summary
  - signal/subscription pattern notes
  - build/lint evidence

### E) HTML Specialist

- Scope:
  - component templates (`.html`)
  - component styles (`.css`)
  - semantic HTML, ARIA roles, labels
  - `| translate` pipe usage
- Return:
  - changed files
  - accessibility coverage notes
  - i18n key list
  - build evidence

### F) Test Coverage Agent

- Scope: Angular/Vitest spec files co-located with changed components and services
- Return:
  - changed spec files
  - coverage evidence (passing test names)
  - ADR-0032/0035 conformance notes
  - blockers or gaps

### G) Code Review (Code Review Agent)

- Scope: frontend acceptance, ADR compliance, accessibility, i18n, and regression review
- Return:
  - `Verdict: PASS|FAIL`
  - findings ordered by severity
  - fix queue referencing ADRs where applicable

### H) Documentation Agent

- Scope: assigned execution tracking source updates and adjacent frontend docs
- Return:
  - changed files
  - status changes recorded
  - blockers or follow-ups

### I) Final Verification and PR Creation

- Before PR creation, Orchestrator MUST have:
  - review PASS
  - passing frontend verification evidence
  - updated assigned execution tracking source when statuses changed
- Create the PR with:
  - `durion/.github/hooks/pull-request-hook.sh --repo` `/home/louis-burroughs/IdeaProjects/durion-positivity-frontend --story <wave-id>`
    `--base <base> --head <branch> --title <title> --body-file <body-file>`

## Frontend Verification Gates

Minimum required:

- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng test --no-watch`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng lint`

Run for targeted domain validation:

- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng test --include="src/app/features/<domain>/**/*.spec.ts" --no-watch`

If a command fails, do not proceed to PR creation until:

- the failure is fixed, or
- it is documented as a blocker with remediation and impact

## Runtime Context Rules

Resolve context in this order:

1. Frontend AGENTS policy in `durion-positivity-frontend/AGENTS.md`
2. Assigned execution tracking source and applicable `CAP-*` story files
3. Design references: `design/DESIGN.md`, wireframes, theme tokens
4. Applicable ADRs from `durion/docs/adr/` (frontend ADRs 0010, 0029–0038 are mandatory)
5. SDK package types and API clients from `durion-positivity-sdk`

Fallbacks:

- contract guide: `durion/domains/<domain>/.business-rules/`
- generated API reference: `durion/domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md`
- OpenAPI: relevant SDK package `openapi.yaml`

## Blocked Story Handling

If an execution slice is blocked by a missing backend contract, missing SDK dependency, or infrastructure prerequisite:

- record the blocker in the assigned execution tracking source with reason and date
- mark the work item as BLOCKED in the assigned execution tracking source
- skip to the next available story in the wave
- report blockers to the user at the end of wave execution
