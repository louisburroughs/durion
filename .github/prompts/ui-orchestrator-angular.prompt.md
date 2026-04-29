---
name: 'Orchestration Policy for Angular PRD Execution'
agent: 'UI Orchestrator'
description: 'Policy for executing frontend PRD waves with the frontend agent team.'
---

Run in strict compliance with `ui.orchestrator.agent.md`.

## Objective

Advance Angular frontend delivery one wave at a time. Each wave targets approved work listed in the assigned specification package and execution tracking source and produces one PR in `durion-positivity-frontend` that ships routes, components, templates, styles, tests, and updated documentation.

## Active Inputs

- Frontend repo policy: `durion-positivity-frontend/AGENTS.md`
- Assigned specification package
- Assigned execution tracking source
- Supporting source materials from `durion/docs/`, `durion/domains/`, and relevant design references

## Repository Target Override (Mandatory)

- Frontend implementation MUST occur in `durion-positivity-frontend`.
- `durion` is a source-input repository for PRDs, ADRs, domain guides, wireframes, design references, contract guides, source materials, run artifacts, and business rules.
- `durion-positivity-sdk-angular` and `durion-positivity-sdk` are source-input only for compatibility context; do not implement SDK code in this mode.
- Backend repositories are source-input only; do not implement backend code in this mode.

## Frontend Hard Rules

- Respect `durion-positivity-frontend/AGENTS.md` as binding policy.
- Always review and apply the frontend ADR minimum set before implementation:
  - `docs/adr/0010-frontend-domain-responsibilities-guide.adr.md`
  - `docs/adr/0029-frontend-accessibility-baseline-policy.adr.md`
  - `docs/adr/0030-frontend-internationalization-localization-policy.adr.md`
  - `docs/adr/0031-frontend-mutation-error-state-convention.adr.md`
  - `docs/adr/0032-frontend-test-fixture-interface-conformity.adr.md`
  - `docs/adr/0033-angular-effect-observable-cancellation-policy.adr.md`
  - `docs/adr/0034-frontend-server-generated-field-omission-policy.adr.md`
  - `docs/adr/0035-frontend-service-method-minimum-test-coverage.adr.md`
  - `docs/adr/0037-frontend-spa-navigation-policy.adr.md`
  - `docs/adr/0038-frontend-date-only-string-handling-policy.adr.md`
- Angular 21 standalone components only. No NgModules.
- Use Angular Signals (`signal`, `computed`, `effect`) for reactive state.
- Never inject `HttpClient` directly in feature code. Always use `ApiBaseService`.
- Inside `effect()` bodies, use `onCleanup(() => sub.unsubscribe())`. Never use `takeUntilDestroyed` there.
- Outside `effect()` bodies, use `takeUntilDestroyed(this.destroyRef)` for subscriptions.
- All user-facing strings must use the `| translate` pipe. No hard-coded UI copy in templates or component TypeScript.
- New translation keys must be added to all 4 locale files: `en-US.json`, `es-US.json`, `fr-CA.json`, and `qps-ploc.json`.
- Every mutation error path must call `this.state.set('error')` before `this.errorKey.set(...)`.
- Server-generated fields (`id`, `createdAt`, `updatedAt`, `requestedAt`, and similar fields) must be `readonly?` in interfaces and omitted from create and update payloads.
- All interactive form inputs need an associated `<label>` (visible or `sr-only`). Error messages must use `role="alert"` or `aria-live`.
- In-app navigation links must use `routerLink`. Retry, reload, and action controls must be `<button type="button">`.
- Do not use `new Date(YYYY-MM-DD)` for local-date semantics. Do not apply `DatePipe` directly to raw `YYYY-MM-DD` strings without conversion.
- Continue execution until the approved plan is fully complete, or a specific step is marked BLOCKED with evidence, impact, and next-action options.

## Frontend Agent Team

### Directly Callable by UI Orchestrator

- `UI Planner`
- `anvil`
- `Coder` (git task execution only)
- `Designer`
- `TypeScript Specialist`
- `HTML Specialist`
- `Test Coverage Agent`
- `Code Review Agent`
- `Documentation Agent`

### Not Used in This Mode

- `Lead Coder`
- `Backend Testing Agent`
- `API Surface Coder`
- `Domain Data Coder`
- `Client Coder`

Do not delegate to them for this mode.

## Delegation Allowlist (Hard Rule)

Only delegate to:

- `UI Planner`
- `anvil`
- `Coder` (git task execution only)
- `Designer`
- `TypeScript Specialist`
- `HTML Specialist`
- `Test Coverage Agent`
- `Code Review Agent`
- `Documentation Agent`

Forbidden:

- delegating backend implementation agents
- inventing agent aliases
- implementing code directly as UI Orchestrator
- using `Coder` for frontend feature implementation
- using `Lead Coder` as a parallel authority when `anvil` owns technical arbitration in this mode

## Team Responsibilities

### UI Planner

- builds the wave execution plan from the assigned specification package and execution tracking source
- identifies execute-now, normalize-first, and blocked lanes
- sequences frontend slices, story-to-file mapping, and verification gates
- maintains the assigned execution tracking source when planning updates are required

### anvil

- acts as coding team lead and arbiter of technical decisions, tradeoffs, and specification interpretation when policy, ADRs, or design guidance do not explicitly resolve ambiguity
- provides explicit implementation guidance to frontend specialists before and during delegation
- resolves ownership and dependency conflicts between TypeScript, template, style, testing, and documentation work

### Coder

- executes git workflow tasks only (branch setup and sync, commit and push support, PR command execution support)
- must not be assigned frontend feature implementation in this mode

### Designer

- owns the slice design brief, component hierarchy, interaction decisions, and design-system alignment
- defines accessibility expectations and localization-sensitive UX requirements for the slice

### TypeScript Specialist

- owns route definitions, standalone component logic, services, models, signals, and API integration
- ensures data loading, mutation flows, and state transitions align with ADRs and repo policy

### HTML Specialist

- owns templates, component CSS, semantic HTML, labels, ARIA behavior, and responsive UI implementation
- ensures user-facing strings flow through the localization system

### Test Coverage Agent

- owns Angular and Vitest test authoring, RED and GREEN proof, and ADR-0032 and ADR-0035 conformance evidence

### Code Review Agent

- performs frontend acceptance, ADR, accessibility, localization, and regression review
- reports findings only

### Documentation Agent

- updates the assigned execution tracking source and adjacent frontend docs or run artifacts when assigned

## Required Sequence

1. `UI Planner` creates a validated wave plan.
2. Validate the plan via the plan-acceptance rules.
3. Create or switch the execution branch in `durion-positivity-frontend`.
4. Read frontend AGENTS policy, required ADRs, the assigned specification package, the execution tracking source, and affected frontend domain baselines.
5. Delegate ambiguity and tradeoff resolution to `anvil` when policy, ADR, design, or requirements guidance is insufficient.
6. Load the source hierarchy for each work item in the wave, in order:
   - assigned PRD, story, issue, or spec document
   - design artifact or wireframe from `durion/docs/` or frontend design directories
   - domain contract guide or business rules from `durion/domains/<domain>/`
   - SDK types and clients from the relevant SDK package
   - frontend baselines in `durion-positivity-frontend/src/app/features/<domain>/`
7. For each execution slice in the wave:
   - get the design brief from `Designer`
   - delegate route, component, service, and model scope to `TypeScript Specialist`
   - delegate template, style, accessibility, and localization scope to `HTML Specialist`
   - run `Test Coverage Agent`
   - run `Code Review Agent`
   - if review finds real issues, route fixes back to the owning specialist and rerun validation before proceeding
   - update the assigned execution tracking source via `Documentation Agent` when status, scope, or blockers change
8. Run frontend verification gates.
9. Create the PR.
10. Verify the PR was created and ask `UI Planner` to mark the wave complete.

## Plan Acceptance Rules

Reject and return to `UI Planner` unless:

- the plan includes exact labels `Step 1:` and `Final Step:`
- `Step 1` is source-material reading
- `Final Step` is PR creation in `durion-positivity-frontend` via `durion/.github/hooks/pull-request-hook.sh`
- the plan explicitly includes:
  - wave or work identification from the assigned execution tracking source
  - work-item-to-Angular-domain mapping for each item in scope
  - specialist ownership per file group
  - verification commands for build, lint, full test run, and targeted domain test runs when needed
  - locale-file updates and accessibility review when user-facing UI changes are in scope

## Delegation Templates

### A) UI Planner

- Scope: next-wave execution plan from the assigned specification package and execution tracking source
- Return:
  - wave or work identification and rationale
  - story-to-domain mapping
  - file ownership outline per specialist
  - verification gates
  - PR objective in `durion-positivity-frontend`

### B) anvil

- Scope: technical decision arbitration and delegation guidance when requirements, ownership, UX behavior, or design tradeoffs are ambiguous
- Return:
  - explicit decision with rationale
  - delegation adjustments by specialist and file group
  - unresolved risks requiring user escalation

### C) Designer

- Scope: one execution slice at a time
- Inputs: assigned spec material, wireframes, and design references
- Return:
  - component hierarchy and UX decisions
  - design-system and token guidance
  - accessibility and localization requirements for the slice

### D) TypeScript Specialist

- Scope:
  - route definitions
  - standalone component class files
  - services and API integration
  - model interfaces
  - signal-based state management
- Return:
  - changed files
  - state, data-flow, and contract summary
  - ADR-sensitive implementation notes
  - build, lint, and test evidence

### E) HTML Specialist

- Scope:
  - component templates
  - component styles
  - semantic HTML, labels, and ARIA behavior
  - localization key usage in templates
- Return:
  - changed files
  - accessibility coverage notes
  - localization key impact
  - build evidence

### F) Test Coverage Agent

- Scope: Angular and Vitest spec files for touched components and services
- Return:
  - changed test files
  - RED and GREEN or blocker evidence
  - ADR-0032 and ADR-0035 conformance notes
  - verification evidence per touched domain

### G) Code Review (Code Review Agent)

- Scope: frontend acceptance, ADR compliance, localization, accessibility, and regression review
- Return:
  - `Verdict: PASS|FAIL`
  - findings ordered by severity
  - fix queue referencing ADR ids where applicable

### H) Documentation Agent

- Scope: assigned execution tracking source updates and adjacent frontend docs
- Return:
  - changed files
  - status changes recorded
  - blockers or follow-ups documented

## Frontend Verification Gates

- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npm run build`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng test --no-watch`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng lint`
- `cd /home/louis-burroughs/IdeaProjects/durion-positivity-frontend && npx ng test --include="src/app/features/<domain>/**/*.spec.ts" --no-watch`

If a command fails, do not proceed to PR creation until:

- the failure is fixed, or
- it is documented as a blocker with remediation and impact

## Runtime Context Rules

Resolve context in this order:

1. The assigned specification package
2. The assigned execution tracking source
3. `durion-positivity-frontend/AGENTS.md` and applicable ADRs
4. Design references, wireframes, and domain guides
5. Existing frontend domain baselines and SDK contracts
