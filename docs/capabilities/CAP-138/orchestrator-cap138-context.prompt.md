---
name: "CAP-138 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-138 Dispatch and Assign Mechanics & Resources."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-138 runs.

## Capability Scope
- Capability: `CAP:138` — Dispatch and Assign Mechanics & Resources
- Parent issue: `louisburroughs/durion#138`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#69`
  - `louisburroughs/durion-positivity-backend#70`
  - `louisburroughs/durion-positivity-backend#71`
  - `louisburroughs/durion-positivity-backend#72`

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-138/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#69`, `#70`, `#71`, `#72` in `durion-positivity-backend`
3. Clarification issues (binding when story body is stale/ambiguous):
   - `#253` (for story `#70`)
   - `#257` (for story `#72`)
   - `#271` (for story `#71`)
4. Domain/ADR context:
   - `durion/docs/adr/0006-workexec-domain-ownership-boundaries.adr.md`
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`
   - `durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/people/.business-rules/AGENT_GUIDE.md`

## Clarification Precedence Rules (Hard)
- If story body conflicts with resolved clarification issue comments, use the clarification decisions.
- For `#71`, treat clarification issue `#271` decisions as authoritative.
- Ignore off-topic clarification comments that do not match CAP-138 domain context; use the latest relevant CAP-138 clarification decision.
- Orchestrator and Planner must explicitly cite which clarification decision they used per story.

## Module and Ownership Guidance
- Default backend implementation anchor for CAP-138 is `pos-shop-manager`.
- Existing anchors in `pos-shop-manager` must be extended rather than bypassed:
  - appointment APIs/entities/services
  - `ConflictDetectionService*`
  - `HrAvailabilityClient`
  - `Technician`/shop scheduling model
- If story `#72` requires people-domain integration, prefer adding integration boundaries first (clients/events/contracts) and only touch `pos-people` when required by accepted contract decisions.
- Do not create parallel assignment/availability models in multiple modules without explicit plan justification.

## OpenAPI and Contract Path Resolution
- Manifest points to generated spec:
  - `durion-positivity-backend/pos-shop-manager/target/openapi.json`
- If missing, fallback to:
  - `durion-positivity-backend/pos-shop-manager/openapi.yaml`
- Do not manually edit generated OpenAPI artifacts.
- Use contract guides for behavior and OpenAPI/API reference for schema/status code detail:
  - `domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - `domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
  - `domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Story Dependency Graph (Hard Planning Constraint)
- Required story order for CAP-138:
  1. `#72` (HR mechanic roster + skills sync foundation)
  2. `#71` (mechanic availability query and reasoning)
  3. `#69` (conflict override permission + reason + audit trail + context flag)
  4. `#70` (assignment create/history and override-aware assignment flow)
- `#70` must not be treated complete unless `#69`, `#71`, and `#72` contracts are implemented or formally blocked with explicit remediation.

## Story-Specific Non-Negotiables
### Story #72
- Primary integration: event-driven from HR.
- REST pull: reconciliation/backfill only.
- Enforce monotonic ordering (`version` or `effectiveAt`) and `eventId` dedupe.
- Deactivation must exclude mechanic from new assignment eligibility.

### Story #71
- Must expose deterministic availability response with machine-readable reason codes.
- Must account for HR availability + internal assignment/travel constraints.
- Use fail-fast behavior and explicit freshness/confidence signaling when HR dependency is degraded.
- Time-window validation and UTC-safe handling are required.

### Story #69
- Override requires permission and mandatory non-empty reason.
- Must persist immutable override audit record with conflict context.
- Must flag resulting scheduled entity/context for downstream consumers.

### Story #70
- Exactly one active assignment per appointment.
- Role rules: single mechanic defaults to `LEAD`; multi-mechanic requires exactly one `LEAD`.
- Skill validation supports team coverage with lead-required constraints.
- Override categories and hard non-overrideable blocks must follow clarification decisions.

## Error and Status Semantics
- Follow ADR-0017:
  - `409` for state/conflict collisions
  - `422` for semantic policy violations
  - `403` for permission failures
  - `400` for malformed/invalid request payloads
- Error responses must include correlation ID and stable error envelope fields.

## Audit and Security Rules
- Follow ADR-0018 for actor fields:
  - actor values from authenticated security context, not request payload
  - persisted actor fields as strings
- Ensure permissions required by CAP-138 are registered in module permissions configuration.

## CAP-138 Execution Deliverables (Per Story)
- Story-level RED evidence with failing assertions tied to story ACs.
- GREEN evidence with passing tests for same scope.
- Code review PASS evidence against story + clarification decisions.
- Coverage evidence (`>= 80%` service/utility scope for touched module).
- Explicit ADR + clarification compliance section in each handoff.

## Blocker Policy for CAP-138
- Do not mark "done" if a story relies on unresolved external assumptions.
- If blocked, return:
  - exact missing contract input
  - impacted story IDs
  - attempted fallback path
  - smallest unblocked slice completed
  - next concrete remediation step
