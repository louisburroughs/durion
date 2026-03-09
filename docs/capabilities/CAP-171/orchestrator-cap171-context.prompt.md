---
name: "CAP-171 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-171 Substitutions, Fitment Hints, and Commercial Rules."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-171 runs.

## Capability Scope
- Capability: `CAP:171` - Substitutions, Fitment Hints, and Commercial Rules
- Parent issue: `louisburroughs/durion#171`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#43` - Rules: Enforce Location Restrictions and Service Rules for Products
  - `louisburroughs/durion-positivity-backend#44` - Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)
  - `louisburroughs/durion-positivity-backend#45` - Rules: Maintain Substitute Relationships and Equivalency Types

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-171/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#43`, `#44`, `#45` in `durion-positivity-backend`
3. Domain guides and contract references (from CAP-171 manifest):
   - `durion/domains/product/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/product/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/pricing/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
4. Existing backend baseline (must be reused/aligned):
   - `durion-positivity-backend/pos-price/openapi.yaml`
   - `durion-positivity-backend/pos-price/src/main/java/com/positivity/price/internal/controller/PriceRestrictionsController.java`
   - `durion-positivity-backend/pos-vehicle-fitment/openapi.yaml`
   - `durion-positivity-backend/pos-vehicle-fitment/src/main/java/com/positivity/vehiclefitment/internal/service/VehicleApplicabilityHintServiceImpl.java`
   - `durion-positivity-backend/pos-vehicle-fitment/README-VEHICLE-HINTS.md`
   - `durion-positivity-backend/pos-workorder/openapi.yaml`
   - `durion-positivity-backend/pos-workorder/src/main/java/com/positivity/workorder/internal/controller/WorkorderPartAdjustmentController.java`
   - `durion-positivity-backend/pos-workorder/src/main/java/com/positivity/workorder/internal/service/WorkorderSubstitutionServiceImpl.java`
5. CAP wireframe context:
   - `durion/domains/pricing/.ui/frontend-story-rules-enforce-location-restrictions-107.wf.md`
   - `durion/domains/pricing/.ui/frontend-story-rules-enforce-location-restrictions-107.wf.meta.json`
6. Cross-cutting ADRs:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`

## Clarification Precedence Rules (Hard)
- If story body content conflicts with clarified owner decisions, use the clarified owner decisions.
- For CAP-171, the authoritative clarification record is:
  - `louisburroughs/durion-positivity-backend#238` owner decision comment on **January 12, 2026** (Domain SoR, enforcement API contract, fail-safe behavior, initial enum tags, and override UX flow) for story `#43`.
- The closure comment in `#238` confirming story `#43` update on **January 12, 2026** is normative unless a newer owner decision supersedes it.

## Existing Implementation Baseline (Hard Constraint)
- `pos-price` already exposes restrictions endpoints (`/v1/price/restrictions:evaluate` and `/v1/price/restrictions:override`) but they are currently `501 Not Implemented`.
- `pos-vehicle-fitment` already contains fitment hint entities/services/APIs and should be extended/aligned for CAP-171 behavior, not duplicated in parallel modules.
- `pos-workorder` already contains substitution flows for workorder part replacement and should be aligned with CAP-171 substitution relationship rules.
- CAP-171 work must extend existing modules/contracts and avoid parallel duplicate APIs/models for the same capability surface.

## Module and Ownership Guidance
- Commercial restriction rule ownership is `domain:pricing` (story `#43` clarification), implemented in `pos-price`.
- Fitment hints and vehicle applicability tags are product-fitment concerns and should align with `pos-vehicle-fitment` contracts/implementation.
- Runtime workorder substitution application remains `workexec` behavior and should align with existing `pos-workorder` part-substitution APIs/events.
- Cross-module communication must follow API/event contracts only (ADR-0026), not direct repository/entity coupling.

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-171:
1. `#43` - Restriction enforcement and override contract in pricing (authoritative gate for transactional safety)
2. `#44` - Fitment hint CRUD/filter behavior (can run in parallel with `#45` after #43 contracts are stable)
3. `#45` - Substitute relationships and equivalency behavior aligned with workexec runtime substitution

## Story-Specific Non-Negotiables

### Story `#43` - Restriction Rules and Overrides
- `domain:pricing` is the SoR for `RestrictionRule` CRUD/versioning/audit/publication.
- Authoritative enforcement uses synchronous evaluation API (`/pricing/v1/restrictions:evaluate` semantics from clarification `#238`) and optional cache acceleration.
- Fail-safe behavior is mandatory:
  - commit paths (`checkout`, `invoice finalize`, `commit sale`) fail closed (`503` or `409`)
  - non-commit browse/quote paths may degrade with `RESTRICTION_UNKNOWN` and must block finalization until evaluation succeeds
- Evaluation timeout is `800ms` with no synchronous retry loop.
- Initial location/service tags are fixed enum sets defined in clarification `#238`; do not silently replace with free-form strings.
- Override flow requires explicit permission (`pricing:restriction:override`), reason code + notes, auditable approval trail, and `overrideId` propagation downstream.

### Story `#44` - Fitment Hints and Vehicle Applicability
- Capability is hints/filtering, not a full deterministic fitment engine.
- CRUD operations for `VehicleApplicabilityHint` and `FitmentTag` must be supported and audited.
- Filtering by provided vehicle attributes must return matching products; no-match response is `200` with empty list.
- Validation/error behavior is required:
  - malformed tag payload -> `400`
  - non-existent product target -> `404`
- Tag/attribute model must remain extensible for future keys without forcing schema redesign.

### Story `#45` - Substitute Relationships and Equivalency
- Substitute relationship model must support explicit types (`EQUIVALENT`, `APPROVED_ALTERNATIVE`, `UPGRADE`, `DOWNGRADE`) and active/effective controls.
- Auto-suggest vs approval-required substitution behavior must be explicit, policy-driven, and auditable.
- Candidate ranking must account for availability and priority with deterministic ordering semantics.
- Concurrency and safety requirements apply:
  - optimistic locking on updates (conflicts -> `409`)
  - idempotent create semantics where defined
- Any substitution relationship mutation must emit immutable audit evidence with before/after state.

## Error and Status Semantics
- Apply ADR-0017 response mapping consistently for validation, conflict, authorization, and service-unavailable paths.
- Unknown or unavailable restriction evaluation on commit paths must not silently pass.
- Duplicate/competing writes (restriction/substitute mutations) must be deterministic and externally observable (`409` + clear error body).

## Audit and Security Rules
- Follow ADR-0018: actor identity comes from authenticated security context, not request body actor fields.
- Use `@EmitEvent` for state-changing APIs and ensure event types are registered.
- Enforce explicit permissions for restriction override and substitution approval paths.
- Preserve correlation IDs and policy/version references across evaluate, override, substitution, and downstream audit events.

## CAP-171 Execution Deliverables (Per Story)
- RED/GREEN evidence mapped to acceptance criteria.
- Code review PASS evidence.
- Coverage hardening evidence for changed services/utilities (target `>= 80%` unless documented blocker).
- Contract alignment updates in impacted domain guides/OpenAPI where behavior changed.

## Blocker Policy for CAP-171
- Clarification decisions in backend issue `#238` are authoritative for story `#43` unless superseded by newer owner comments.
- If blocked, return: exact missing decision, impacted story ID (`#43|#44|#45`), and smallest unblocked implementation slice with next remediation step.
