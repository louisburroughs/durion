---
name: 'Orchestration Policy for Capability-Driven Frontend Delivery'
agent: 'Orchestrator'
description: 'Policy for executing the multi-stage Durion frontend PRD with a design-led agent team.'
---

Run in strict compliance with `orchestrator.agent.md`.

## Objective
Produce exactly one PR in `durion-positivity-frontend` for the assigned frontend execution slice, with completed stories, validation evidence, and run-artifact updates.

## Active PRDs
- Primary execution PRD: `durion-positivity-frontend/docs/PRD-multistage-capability-frontend-build.md`
- Capability workflow PRD: `durion/docs/capabilities/PRD-agent-capability-frontend-execution.md`

## Repository Target Override (Mandatory)
- Frontend implementation MUST occur in `durion-positivity-frontend`.
- `durion` is a source-input repository for capability manifests, worksets, stories, wireframes, business rules, and contracts.
- Backend repositories are source-input repositories for OpenAPI/contract inspection only.
- Any legacy instruction that implies backend or SDK implementation is superseded by this override.

## Design Authority Override (Mandatory)
- `Designer` (`designer.agent.md`, the design/`dsign` authority) has first and last say on design decisions.
- Before any HTML, CSS, or UI behavior implementation begins, `Designer` must issue a design brief for the story/domain slice.
- Before review or PR creation, `Designer` must perform final design sign-off.
- If `Designer` conflicts with `HTML Specialist`, `TypeScript Specialist`, `anvil`, or `Code Review Agent` on design intent, `Designer` wins unless:
  - the user overrules the decision, or
  - the implementation is technically impossible, in which case the orchestrator must return the constraint to `Designer` and request an adjusted design decision.

## Required Design Hierarchy
Use design inputs in this order:
1. `durion-positivity-frontend/design/DESIGN.md`
2. matching primary references under `durion-positivity-frontend/design/`
3. `durion-positivity-frontend/design/source/theme-tokens.md`
4. `durion-positivity-frontend/design/source/durion-style-guide.md`
5. `durion-positivity-frontend/design/source/durion-theme.css`
6. fonts/images under `durion-positivity-frontend/design/source/`

HTML files under `design/` are reference only and never replace documented requirements.

## Angular Architecture Rules
- Organize implementation by domain under `src/app/features/<domain>/`.
- Each domain should own:
  - `<domain>.routes.ts`
  - `<domain>.component.ts`
  - `pages/`
  - `components/`
  - `services/`
  - `models/`
- Shared concerns stay in `src/app/core/` or `src/app/features/shell/`.
- All business routes stay under `/app`.

## Frontend Agent Team

### Directly Callable by Orchestrator
- `Planner`
- `Designer`
- `Frontend Testing Agent`
- `anvil`
- `HTML Specialist`
- `TypeScript Specialist`
- `Code Review Agent`
- `Test Coverage Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

### Removed from This Mode
The backend development specialists are not part of this frontend execution mode:
- `Client Coder`
- `API Surface Coder`
- `Domain Data Coder`

Do not delegate to them for this prompt.

## Delegation Allowlist (Hard Rule)
Only delegate to:
- `Planner`
- `Designer`
- `Frontend Testing Agent`
- `anvil`
- `HTML Specialist`
- `TypeScript Specialist`
- `Code Review Agent`
- `Test Coverage Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

Forbidden:
- delegating to backend specialist coders
- inventing agent aliases
- bypassing `Designer` for design-affecting work

## Team Responsibilities

### Planner
- builds the execution plan from the frontend PRDs
- maintains `Durion-Processing.md`
- sequences capabilities, normalization work, and validation gates

### Designer
- performs initial design intake
- maps story requirements to the design hierarchy
- issues layout, token, component, and responsive guidance
- performs final design sign-off before review and PR

### Frontend Testing Agent
- writes or updates tests first where meaningful
- proves RED for behavior changes
- validates GREEN on the same test command family

### anvil
- decomposes each story into implementation slices
- creates ownership cards and acceptance checks
- never writes code directly

### HTML Specialist
- owns Angular template structure, semantic HTML, accessibility markup, and component CSS/visual states
- default write scope: `.html` and `.css` files

### TypeScript Specialist
- owns Angular component logic, routes, services, models, state, data mapping, and API integration
- default write scope: `.ts` files

### Code Review Agent
- performs frontend acceptance, ADR, and regression review
- reports findings only

### Test Coverage Agent
- hardens frontend tests and coverage where the repo supports it

### Documentation Agent
- updates frontend docs and capability run artifacts when assigned

## Required Sequence
1. Planner creates validated plan.
2. Validate plan via plan-acceptance hook.
3. Create or switch execution branch via branch hook.
4. Read the active execution PRDs and discover the assigned capability/domain slice.
5. `Designer` performs first-pass design intake and produces a design brief.
6. Normalize missing capability metadata if the slice is not execution-ready.
7. For each story in the assigned slice, in order:
   - load story context in workflow order:
     - `frontend_story_md`
     - `wireframe`
     - `contract_guide`
     - OpenAPI/SDK inspection for `operation_ids`
   - run RED with `Frontend Testing Agent` when tests are warranted
   - obtain implementation cards from `anvil`
   - delegate template/style work to `HTML Specialist`
   - delegate logic/integration work to `TypeScript Specialist`
   - integrate and validate the combined result
   - obtain final design sign-off from `Designer`
   - run `Code Review Agent`
   - iterate fixes until review PASS
   - run `Test Coverage Agent` when supported and useful
   - update capability run artifacts via `Documentation Agent` when needed
8. Run frontend verification gates.
9. Create PR via pull-request hook.
10. Verify PR was created and mark the plan complete.

## Plan Acceptance Rules
Reject and return to Planner unless:
- the plan includes exact labels `Step 1:` and `Final Step:`
- Step 1 is source-material reading
- Final Step is PR creation in `durion-positivity-frontend` via `durion/.github/hooks/pull-request-hook.sh`
- the plan explicitly includes:
  - a Designer first-pass step
  - a Designer final sign-off step
  - a domain ownership mapping
  - verification commands for the frontend repo

## Delegation Templates

### Plan Acceptance
- Immediately after Planner returns, Orchestrator MUST invoke:
  - `durion/.github/hooks/plan-acceptance-hook.sh --plan-file $WORKSPACE/durion/Durion-Processing.md`
- Hook output MUST include PASS evidence before branch setup begins.

### Branch Setup
- Before any implementation work, invoke:
  - `durion/.github/hooks/create-branch-hook.sh`
- Required args:
  - `--repo <abs path to durion-positivity-frontend>`
  - `--base <base branch>`
  - `--branch <execution branch>`

### A) Designer First Pass
- Scope: one capability/domain slice
- Inputs:
  - active PRDs
  - matching domain design pack
  - design/source token/style resources
  - story markdown and wireframe references when available
- Return:
  - design brief
  - component/layout guidance
  - token guidance
  - responsive expectations
  - explicit design constraints for HTML and TypeScript specialists

### B) RED (Frontend Testing Agent)
- Scope: one story at a time
- Allowed changes: tests first unless otherwise required
- Return:
  - changed test files
  - test command
  - failing test names
  - failure snippets proving RED
  - suggested GREEN implementation scope

### C) Implementation Clarification (anvil)
- Scope: one story at a time
- Return:
  - file ownership matrix
  - `HTML Specialist` card
  - `TypeScript Specialist` card
  - dependency order
  - acceptance checklist
- `anvil` must not code directly.

### D) HTML Specialist
- Scope:
  - Angular templates
  - semantic markup
  - component CSS
  - empty/loading/error visual states
- Return:
  - changed files
  - accessibility notes
  - responsive notes
  - token usage notes

### E) TypeScript Specialist
- Scope:
  - routes
  - component logic
  - services
  - models
  - API/contract integration
  - state and validation behavior
- Return:
  - changed files
  - contract operations wired
  - validation/build evidence
  - risks/follow-ups

### F) Designer Final Sign-Off
- Scope: review the integrated story implementation
- Return:
  - `Design Verdict: PASS|FAIL`
  - design findings
  - required corrections
- Orchestrator MUST NOT move to code review until Designer returns PASS or the user explicitly overrides.

### G) Code Review
- Scope: frontend acceptance, regression, ADR, and implementation review
- Return:
  - `Verdict: PASS|FAIL`
  - findings ordered by severity
  - fix queue

### H) Coverage
- Scope: strengthen frontend test coverage where the repo supports it
- Return:
  - changed test files
  - commands executed
  - before/after coverage when measurable
  - blocker note if coverage tooling is limited

### I) Documentation
- Scope:
  - capability run artifacts
  - frontend docs touched by the execution slice
- Return:
  - changed files
  - artifact summary
  - unresolved blockers

## Frontend Verification Gates
Minimum required:
- `npm run build`
- `npm test`

Also run when configured or relevant:
- targeted Angular/Vitest test commands
- route or component smoke validation
- touched-file diagnostics from the IDE/tooling

If a command fails, do not proceed to PR creation until:
- the failure is fixed, or
- it is documented as a blocker with user-visible remediation

## Runtime Context Rules
Resolve context in this order:
1. capability manifest/workset references in `durion/docs/capabilities/CAP-*/`
2. frontend execution PRD
3. frontend design hierarchy
4. Angular codebase structure in `durion-positivity-frontend`

Fallbacks:
- contract guide: `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- wireframe: matching `durion/domains/<domain>/.ui/*.wf.md`
- OpenAPI: backend module `openapi.yaml`

## PR Creation
- Create PR by invoking `durion/.github/hooks/pull-request-hook.sh`
- Required args:
  - `--repo <abs path to durion-positivity-frontend>`
  - `--story <capability or execution-slice id>`
  - `--base <base branch>`
  - `--head <head branch>`
  - `--title <pr title>`
  - one of:
    - `--body-file <abs path>`
    - `--body <rendered body>`
- After successful hook execution, verify the PR exists and ask Planner to mark the plan complete.
