---
name: 'Orchestration Policy for CAP-218 Backend Fulfillment Delivery'
agent: 'Orchestrator'
description: 'Policy for executing the CAP-218 backend fulfillment plan with the backend agent team.'
---

Run in strict compliance with `orchestrator.agent.md`.

## Objective
Produce exactly one PR in `durion-positivity-backend` that completes the CAP-218 backend fulfillment plan, ships the workorder-facing pick and consume facades, includes validation evidence, and updates run artifacts.

## Active PRDs and Required Inputs
- Primary execution PRD: `durion/docs/capabilities/CAP-218/PRD-cap218-backend-fulfillment-completion.md`
- Backend repo policy: `durion-positivity-backend/AGENTS.md`
- Capability manifest: `durion/docs/capabilities/CAP-218/CAPABILITY_MANIFEST.yaml`
- Agent workset: `durion/docs/capabilities/CAP-218/AGENT_WORKSET.yaml`
- Current run artifact: `durion/docs/capabilities/CAP-218/runs/latest.md`

## Repository Target Override (Mandatory)
- Backend implementation MUST occur in `durion-positivity-backend`.
- `durion` is a source-input repository for PRDs, capability metadata, ADRs, run artifacts, contract guides, wireframes, and business rules.
- Frontend repositories are source-input only; do not implement frontend code in this mode.
- Any legacy instruction that implies frontend or SDK implementation is superseded by this override.

## CAP-218 Hard Rules
- `pos-inventory` remains system of record for raw pick-list/task state and inventory movement state.
- `pos-workorder` owns browser-facing routes, response normalization, and server-to-server orchestration for CAP-218.
- Primary browser route key is `workorderId` unless a secondary route is intentionally documented.
- Controllers stay thin; cross-module access goes through service and client boundaries, never direct repository coupling.
- All code except `service/` and the `@SpringBootApplication` root class stays under `internal/**`.
- State-changing REST endpoints require `@EmitEvent` and matching event-type registration artifacts when new event ids are introduced.
- Non-null parameters and return types use `@NonNull` where applicable.
- Canonical permission model:
  - view/load pick routes: `inventory:pick_list:view`
  - resolve/confirm/complete pick routes: `inventory:pick_list:execute`
  - consume-picked-items permission: must be explicitly documented and enforced
- Error behavior must deterministically cover `400`, `401`, `403`, `404`, and `409` with machine-readable codes and useful messages.

## Backend Agent Team

### Directly Callable by Orchestrator
- `Planner`
- `Lead Coder`
- `Backend Testing Agent`
- `Client Coder`
- `API Surface Coder`
- `Domain Data Coder`
- `Code Review Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

### Removed from This Mode
- `Designer`
- `anvil`
- `HTML Specialist`
- `TypeScript Specialist`
- frontend-only testing or documentation behavior

Do not delegate to them for this prompt.

## Delegation Allowlist (Hard Rule)
Only delegate to:
- `Planner`
- `Lead Coder`
- `Backend Testing Agent`
- `Client Coder`
- `API Surface Coder`
- `Domain Data Coder`
- `Code Review Agent`
- `Documentation Agent`
- `Coder` (legacy fallback only)

Forbidden:
- delegating frontend implementation agents
- inventing agent aliases
- bypassing `Lead Coder` when specialist coder file ownership is unclear

## Team Responsibilities

### Planner
- builds the execution plan from the CAP-218 backend PRD, manifest, workset, and run artifact
- maintains `Durion-Processing.md`
- sequences CAP-218 phases, issue mapping, and validation gates

### Lead Coder
- decomposes each CAP-218 slice into specialist ownership cards
- maps files, dependencies, and acceptance checks for backend specialists
- validates specialist returns against the PRD, ADRs, and repository boundaries
- never writes code directly

### Backend Testing Agent
- writes or updates tests first where meaningful
- proves RED for behavior changes or reports a precise blocker
- validates GREEN and module verification on the same command family

### API Surface Coder
- owns controllers, DTOs, service interfaces, validation, OpenAPI annotations, permission annotations, and `@EmitEvent` usage
- default write scope: API-facing files in `src/main/java/**/controller`, `src/main/java/**/dto`, `src/main/java/**/service`, and related config artifacts

### Domain Data Coder
- owns service implementations, transactions, repositories, mappings, entities, and orchestration logic
- default write scope: domain and persistence files under `src/main/java/**/internal/**`

### Client Coder
- owns outbound inventory-integration clients, request/response adapters, correlation/auth propagation, and remote error translation
- default write scope: client/config/integration files assigned by `Lead Coder`

### Code Review Agent
- performs backend acceptance, ADR, and regression review
- reports findings only

### Documentation Agent
- updates CAP-218 run artifacts and backend-facing documentation when assigned

### Coder
- legacy single-agent fallback when specialist delegation is blocked
- must still satisfy the same backend PRD, testing, lint, and review gates

## Required Sequence
1. Planner creates validated plan.
2. Validate plan via plan-acceptance hook.
3. Create or switch execution branch via branch hook in `durion-positivity-backend`.
4. Read the active PRD, manifest, workset, current run artifact, ADR set, and module baselines.
5. Audit `pos-inventory` and `pos-workorder` against CAP-218 phases and identify the smallest required inventory-side delta.
6. Execute CAP-218 phases in order:
   - Phase 1: audit or close raw inventory contract gaps supporting `#28`
   - Phase 2: build workorder pick view facade
   - Phase 3: build mechanic picking execution facade for `#179`
   - Phase 4: build consume picked items facade for `#178`
   - Phase 5: validation, docs, and PR preparation
7. For each active slice:
   - load inputs in workflow order:
     - CAP-218 backend PRD
     - `CAPABILITY_MANIFEST.yaml`
     - `AGENT_WORKSET.yaml`
     - `runs/latest.md`
     - relevant contract guide or API reference
     - relevant module OpenAPI and existing code or tests
   - run RED with `Backend Testing Agent` when tests are warranted
   - obtain implementation cards from `Lead Coder`
   - delegate API contract work to `API Surface Coder`
   - delegate domain/persistence/orchestration work to `Domain Data Coder`
   - delegate outbound integration work to `Client Coder` when required
   - use `Coder` only when `Lead Coder` explicitly marks fallback
   - integrate and validate the combined result
   - rerun `Backend Testing Agent` for GREEN evidence
   - run `Code Review Agent`
   - iterate fixes until review PASS
   - update CAP-218 run artifacts via `Documentation Agent` when routes, permissions, or decisions changed
8. Run backend verification gates.
9. Create PR via pull-request hook.
10. Verify PR was created and mark the plan complete.

## Plan Acceptance Rules
Reject and return to Planner unless:
- the plan includes exact labels `Step 1:` and `Final Step:`
- Step 1 is source-material reading
- Final Step is PR creation in `durion-positivity-backend` via `durion/.github/hooks/pull-request-hook.sh`
- the plan explicitly includes:
  - CAP-218 phase mapping
  - backend issue mapping for `#28`, `#179`, and `#178`
  - module ownership mapping (`pos-inventory` vs `pos-workorder`)
  - verification commands from the CAP-218 PRD

## Delegation Templates

### Plan Acceptance
- Immediately after Planner returns, Orchestrator MUST invoke:
  - `durion/.github/hooks/plan-acceptance-hook.sh --plan-file $WORKSPACE/durion/Durion-Processing.md`
- Hook output MUST include PASS evidence before branch setup begins.

### Branch Setup
- Before any implementation work, invoke:
  - `durion/.github/hooks/create-branch-hook.sh`
- Required args:
  - `--repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend`
  - `--base <base branch>`
  - `--branch <execution branch>`

### Test and Verify Hooks
- Preferred targeted test execution:
  - `durion/.github/hooks/test-run-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend --module <module> --goal test [--also-make]`
- Full touched-module verification:
  - `durion/.github/hooks/module-verify-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend --modules <csv>`
- Touched-file lint:
  - `durion/.github/hooks/lint-run-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend --module <module>`

### A) Planner
- Scope: full CAP-218 backend plan
- Return:
  - phase-aware execution plan
  - issue mapping for `#28`, `#179`, `#178`
  - module ownership mapping
  - verification gates
  - PR objective in `durion-positivity-backend`

### B) RED (Backend Testing Agent)
- Scope: one backend slice at a time
- Allowed changes: tests first unless otherwise required
- Return:
  - changed test files
  - test command
  - failing test names
  - failure snippets proving RED
  - suggested GREEN implementation scope
  - touched module(s)

### C) Implementation Clarification (Lead Coder)
- Scope: one backend slice at a time
- Return:
  - file ownership matrix
  - `API Surface Coder` card
  - `Domain Data Coder` card
  - `Client Coder` card or `NO_SCOPE`
  - dependency order
  - acceptance checklist
- `Lead Coder` must not code directly.

### D) API Surface Coder
- Scope:
  - controllers
  - request/response DTOs
  - service interfaces
  - OpenAPI annotations
  - validation, permission, and event annotations
- Return:
  - changed files
  - endpoint and DTO summary
  - permission and event coverage notes
  - validation/build evidence

### E) Domain Data Coder
- Scope:
  - service implementations
  - repositories and entities
  - mappings and orchestration logic
  - optimistic concurrency and state validation
- Return:
  - changed files
  - behavior implemented
  - persistence and transaction notes
  - validation/build evidence
  - risks/follow-ups

### F) Client Coder
- Scope:
  - outbound inventory integration clients
  - correlation/auth/header propagation
  - remote error translation
- Return:
  - changed files
  - operations wired
  - config requirements
  - validation/build evidence
  - risks/follow-ups

### G) GREEN Validation (Backend Testing Agent)
- Scope: review the integrated backend slice
- Return:
  - changed test files
  - exact GREEN command(s)
  - passing test evidence
  - module verification evidence when asked

### H) Code Review
- Scope: backend acceptance, regression, ADR, and implementation review
- Return:
  - `Verdict: PASS|FAIL`
  - findings ordered by severity
  - fix queue

### I) Documentation Agent
- Scope: update CAP-218 backend run artifacts and adjacent execution docs
- Return:
  - changed files
  - route/permission/decision updates recorded
  - evidence sources used
  - blockers or follow-ups

### J) Final Verification and PR Creation
- Before PR creation, Orchestrator MUST have:
  - review PASS
  - passing backend verification evidence
  - updated run artifact when the contract or decisions changed
- Create the PR with:
  - `durion/.github/hooks/pull-request-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend --story CAP-218 --base <base> --head <branch> --title <title> --body-file <body-file>`

## Backend Verification Gates
Minimum required:
- `./mvnw -pl pos-workorder,pos-inventory -am test`
- `./mvnw -pl pos-workorder -am compile`
- `./mvnw -pl pos-inventory -am compile`
- `durion/.github/hooks/module-verify-hook.sh --repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend --modules pos-workorder,pos-inventory`

Also run when configured or relevant:
- targeted module tests through `test-run-hook.sh`
- integration or contract tests for the active slice
- touched-file lint through `lint-run-hook.sh`

If a command fails, do not proceed to PR creation until:
- the failure is fixed, or
- it is documented as a blocker with remediation and impact

## Runtime Context Rules
Resolve context in this order:
1. CAP-218 backend PRD
2. capability manifest and workset in `durion/docs/capabilities/CAP-218/`
3. current run artifact
4. backend repo policy in `durion-positivity-backend/AGENTS.md`
5. relevant ADRs, contract guide, OpenAPI, and module baselines

Fallbacks:
- contract guide: `durion/domains/<domain>/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- generated API reference: `durion/domains/<domain>/.business-rules/BACKEND_API_REFERENCE.generated.md`
- OpenAPI: module-local `openapi.yaml`, `openapi.json`, or generated spec used by the active module

## Open Decisions Handling
The CAP-218 PRD explicitly allows implementation-time closure of these decisions:
- whether pick tasks are embedded in the pick-list response or fetched separately
- whether scan resolution is task-scoped or workorder-scoped
- whether completion with remainder is allowed
- canonical permission for consume-picked-items
- exact optimistic concurrency or versioning shape

When a decision is closed during execution:
- keep code, tests, and OpenAPI consistent
- record the decision in `docs/capabilities/CAP-218/runs/latest.md`
- ensure the final PR body calls it out if it affects frontend integration

## PR Creation
- Create PR by invoking `durion/.github/hooks/pull-request-hook.sh`
- Required args:
  - `--repo /home/louis-burroughs/IdeaProjects/durion-positivity-backend`
  - `--story CAP-218`
  - `--base <base branch>`
  - `--head <head branch>`
  - `--title <pr title>`
  - one of:
    - `--body-file <abs path>`
    - `--body <rendered body>`
- After successful hook execution, verify the PR exists and ask Planner to mark the plan complete.
