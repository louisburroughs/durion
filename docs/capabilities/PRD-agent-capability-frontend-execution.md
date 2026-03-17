---
title: "PRD: Capability-Driven Frontend Execution Workflow"
owner: "louisburroughs/durion"
status: "draft"
last_updated: "2026-03-17"
---

# Product Requirements Document — Capability-Driven Frontend Execution Workflow

## 1. Objective

Define a deterministic agent workflow to implement frontend capability stories by using:
- `CAPABILITY_MANIFEST.yaml` as the capability contract map
- `AGENT_WORKSET.yaml` as the execution index

The workflow must process all capability folders under `docs/capabilities/CAP-*` and produce implementation code plus run artifacts.

## 2. Scope

### In Scope
- Read each capability manifest and workset.
- For each story in a workset, execute context loading in this order:
  1. Extracted frontend story markdown
  2. Wireframe
  3. Contract guide
  4. SDK/OpenAPI inspection for listed `operation_ids`
- Implement frontend code in the correct domain/angular module.
- Update run artifacts for traceability.

### Out of Scope
- Backend feature implementation (already built).
- Re-authoring capability manifests unless they are invalid.
- Rewriting business rules outside documented contracts.

## 3. Inputs

### Required Per Capability
- `docs/capabilities/<CAP-ID>/CAPABILITY_MANIFEST.yaml`
- `docs/capabilities/<CAP-ID>/AGENT_WORKSET.yaml`

### Required Per Story (from workset)
- `frontend_story_md`
- `wireframe`
- `contract_guide`
- `sdk_package`
- `openapi_spec`
- `operation_ids[]`

## 4. Processing Sequence

For each capability in `docs/capabilities/CAP-*`:

1. Load manifest.
2. Load workset.
3. Validate workset story entries:
   - required fields present
   - referenced files exist
   - `operation_ids` list is non-empty (or mark for review)
4. For each story entry:
   - Read story markdown (`frontend_story_md`)
   - Read wireframe (`wireframe`)
   - Read contract guide (`contract_guide`)
   - Inspect SDK package and OpenAPI operations for `operation_ids`
5. Implement frontend behavior in the target domain/angular module:
   - routes/screens/components
   - service client calls
   - request/response mapping
   - error handling and validation
   - state transitions and UX behavior per wireframe + story acceptance criteria
6. Run module-level validation (build/tests/lint where configured).
7. Update run artifacts.

## 5. Implementation Rules

### Contract Fidelity
- OpenAPI is source-of-truth for operation signatures.
- `operation_ids` in workset define primary backend operations to wire.
- Do not invent undocumented request/response fields.

### Story Fidelity
- Acceptance criteria in extracted story markdown are mandatory.
- Wireframe behavior governs UI flow and visual state handling.
- Contract guide governs domain constraints and cross-domain boundaries.

### Module Placement
- Place code only in the appropriate domain/angular module implied by capability domain and workset metadata.
- Avoid cross-domain leakage without explicit contract requirement.

## 6. Run Artifacts

For each capability run, update/create artifacts under:
- `docs/capabilities/<CAP-ID>/runs/`

Template source:
- `docs/capabilities/RUN_ARTIFACT_TEMPLATE.md`

Minimum artifact set:
- `latest.md` containing:
  - capability id
  - stories processed
  - files changed
  - operation_ids implemented
  - validation commands executed
  - pass/fail summary
  - blockers and follow-ups

Optional history retention:
- `docs/capabilities/<CAP-ID>/runs/history/<timestamp>.md`

## 7. Acceptance Criteria

A capability is complete when:
1. All stories in `AGENT_WORKSET.yaml` are processed.
2. Frontend code is implemented in the correct domain/angular module.
3. Each story’s `operation_ids` are wired to SDK/OpenAPI-backed client calls.
4. Acceptance criteria from story markdown are met.
5. Wireframe-prescribed flows are implemented.
6. Run artifact `runs/latest.md` is updated with evidence.
7. Build/test/lint checks for affected frontend modules pass (or failures are documented with actionable remediation).

## 8. Failure and Escalation Policy

If a story cannot be completed, mark it as `blocked` in run artifacts with:
- missing file or contract details
- unresolved API mismatch
- dependency blocker
- required human decision

Continue processing remaining stories unless blocker is global to the capability.

## 9. Execution Checklist

For each `CAP-*` folder:
1. Read manifest.
2. Read workset.
3. For each story:
   - read `frontend_story_md`
   - read `wireframe`
   - read `contract_guide`
   - inspect `sdk_package` + `openapi_spec` for `operation_ids`
   - implement frontend code in domain/angular module
4. Run validation.
5. Update `runs/latest.md`.
