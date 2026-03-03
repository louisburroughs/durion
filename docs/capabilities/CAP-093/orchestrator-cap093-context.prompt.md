---
name: "CAP-093 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-093 Promotions & Commercial Activity (Lightweight)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-093 runs.

## Capability Scope
- Capability: `CAP:093` — Promotions & Commercial Activity (Lightweight)
- Parent issue: `louisburroughs/durion#93`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#94` — Promotions: Record Promotion Redemption from Invoicing (`domain:crm`)
  - `louisburroughs/durion-positivity-backend#95` — Promotions: Apply Offer During Estimate Pricing (`domain:pricing`)
  - `louisburroughs/durion-positivity-backend#96` — Promotions: Define Eligibility Rules (Account/Vehicle) (`domain:pricing`)
  - `louisburroughs/durion-positivity-backend#97` — Promotions: Create Promotion Offer (Basic) (`domain:pricing`)

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-093/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#94`, `#95`, `#96`, `#97` in `durion-positivity-backend`
3. Clarification issues and resolved comments:
   - `#275` (authoritative for story `#94`)
   - `#276` (authoritative for story `#96`)
   - `#308` (clarification context for story `#97`)
   - story `#95` resolved decisions in issue comments (no separate clarification issue)
4. Domain/ADR context:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`
   - `durion/domains/crm/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/pricing/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/pricing/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Clarification Precedence Rules (Hard)
- If story text conflicts with resolved clarification comments, use the clarification decisions.
- For story `#94`, use the clarification outcome from `#275`:
  - CRM records redemptions idempotently and updates counters atomically.
  - Usage-limit enforcement is upstream (workorder execution pre-finalization), not a hard reject in redemption recording.
- For story `#96`, use the clarification outcome from `#276`:
  - default rule combination `AND`, optional per-promotion `OR`.
  - explicit, machine-readable reason codes are required.
  - fleet size and vehicle tags must come from authoritative upstream services.
- For story `#95`, use the resolved issue comment decisions:
  - `domain:pricing` owns `PricingAdjustment` and promotion math.
  - exactly one promotion per estimate for this capability slice.
  - stable error codes are required; message copy is provisional.
- Ignore off-topic clarification comments that do not match CAP-093 promotions scope (some clarification threads include unrelated accounting/procurement examples).

## Module and Ownership Guidance
- Primary module ownership:
  - `pos-price` (`domain:pricing`): promotion application logic, eligibility evaluation, pricing adjustment authority.
  - `pos-customer` (`domain:crm`): promotion/redemption system-of-record concerns and redemption tracking.
- Integration boundary:
  - `pos-workorder` consumes pricing/eligibility decisions and emits/forwards promotion redemption events, but does not own promotion math or eligibility rule policy.
- Do not create a parallel promotions model in multiple modules without explicit contract-first justification.
- Respect internal package boundaries and service-layer API exposure rules for each module.

## OpenAPI and Contract Path Resolution
- CAP-093 manifest lists OpenAPI paths:
  - `pos-customer/openapi.yaml` (valid)
  - `pos-pricing/openapi.yaml` (stale module name)
- In this repository, use:
  - `durion-positivity-backend/pos-customer/openapi.yaml`
  - `durion-positivity-backend/pos-price/openapi.yaml`
- If generated artifacts are missing, fallback to `openapi.json` in the same module.
- Do not manually edit generated OpenAPI artifacts.

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-093:
1. `#97` — Create Promotion Offer (foundation: offer lifecycle and base model)
2. `#96` — Define Eligibility Rules (depends on promotion model from `#97`)
3. `#95` — Apply Offer During Estimate Pricing (depends on offer + eligibility contracts)
4. `#94` — Record Redemption from Invoicing (depends on apply/finalization integration event contract)

- `#95` must not be marked complete if it bypasses `domain:pricing` authority for pricing adjustments.
- `#94` must not be marked complete without idempotent dedupe + atomic counter update behavior.

## Story-Specific Non-Negotiables

### Story `#97` — Create Promotion Offer (Basic)
- Enforce unique promotion code.
- Validate date range (`startDate <= endDate`).
- Support lifecycle transitions at least `DRAFT`, `ACTIVE`, `INACTIVE` (and expiry handling as applicable).
- Persist offer type/value and optional usage limit with clear policy.
- Keep implementation lightweight for this capability slice; avoid introducing an oversized generic rule engine unless required by accepted clarification decisions.

### Story `#96` — Define Eligibility Rules (Account/Vehicle)
- Rule model supports:
  - condition type + operator + value
  - per-promotion combination mode `AND|OR` (default `AND`)
- Evaluation must return explicit `isEligible` + stable `reasonCode`.
- Must distinguish inclusion and exclusion outcomes when supported (e.g., `ACCOUNT_NOT_IN_LIST` vs `ACCOUNT_IN_EXCLUSION_LIST`).
- Fail-safe on missing/ambiguous upstream data: ineligible (or explicit error for missing required context).

### Story `#95` — Apply Offer During Estimate Pricing
- Single promotion per estimate for this scope.
- `PricingAdjustment` computation and ownership remain in `domain:pricing`.
- No duplicate promotion math in `workexec`.
- Return stable machine-readable error codes (`PROMO_NOT_FOUND`, `PROMO_NOT_APPLICABLE`, `PROMO_MULTIPLE_NOT_ALLOWED`, etc.).

### Story `#94` — Record Promotion Redemption from Invoicing
- Consume redemption events idempotently (dedupe by event key or equivalent uniqueness contract).
- Create redemption record and increment counters in a single transaction.
- Duplicate events are acknowledged/no-op with observability signal.
- Do not reject unique redemption solely because counters are at/over limit by default; optional over-limit flagging may be supported when configured.

## Error and Status Semantics
- Follow ADR-0017:
  - `400` malformed/invalid payload
  - `403` permission failures
  - `409` conflicts/idempotency collisions where applicable
  - `422` semantic/policy violations
- Use stable error envelope with correlation ID.

## Audit and Security Rules
- Follow ADR-0018:
  - actor fields come from authenticated security context, not request body.
- Use `@EmitEvent` on state-changing API operations and register event types in module event type registries.
- Ensure new permissions are added in module `permissions.yaml` for promotion CRUD/evaluation/redeeming paths.

## CAP-093 Execution Deliverables (Per Story)
- RED evidence tied to acceptance criteria.
- GREEN evidence for same scope.
- Code review PASS evidence against story + clarification decisions.
- Coverage evidence (`>= 80%` service/utility scope for touched modules).
- Explicit clarification compliance section in each orchestrator handoff.

## Blocker Policy for CAP-093
- Do not mark done if behavior depends on unresolved contract ambiguities.
- If blocked, return:
  - exact missing contract decision
  - impacted story IDs
  - attempted fallback path
  - smallest unblocked slice completed
  - next concrete remediation step
