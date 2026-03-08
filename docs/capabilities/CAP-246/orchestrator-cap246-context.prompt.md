---
name: "CAP-246 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-246 POS Sales Order & Cart (Quote-to-Cash Entry Point)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-246 runs.

## Capability Scope
- Capability: `CAP:246` — POS Sales Order & Cart (Quote-to-Cash Entry Point)
- Parent issue: `louisburroughs/durion#246`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#21` — Order: Create Sales Order Cart and Add Items
  - `louisburroughs/durion-positivity-backend#20` — Order: Apply Price Override with Permission and Reason
  - `louisburroughs/durion-positivity-backend#19` — Order: Cancel Order with Controlled Void Logic

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-246/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#19`, `#20`, `#21` in `durion-positivity-backend`
3. Order domain/contract context:
   - `durion/domains/order/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`
4. Existing backend baseline (must be reused/aligned):
   - `durion-positivity-backend/pos-order/openapi.yaml`
   - `durion-positivity-backend/pos-order/src/main/java/com/positivity/order/internal/controller/PriceOverrideController.java`
   - `durion-positivity-backend/pos-order/src/main/java/com/positivity/order/internal/service/PriceOverrideServiceImpl.java`
   - `durion-positivity-backend/pos-order/src/main/java/com/positivity/order/internal/entity/PriceOverride.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/controller/PaymentApplicationController.java`
5. Cross-cutting ADRs:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`

## Clarification Precedence Rules (Hard)
- If story body conflicts with resolved clarification comments/decision records, use the clarification decision.
- For CAP-246, authoritative decisions are:
  - Story `#19`: owner Decision Record comment on **January 14, 2026** (POS-orchestrated cancellation saga, Workexec authority, ordering: cancel work order first then payment reversal, persisted saga states).
  - Story `#20`: use the `Resolved Decisions (from clarification #409)` section embedded in issue body as authoritative (thresholds, rounding, commission/rebate impact).
  - Story `#21`: owner refinement comment on **January 13, 2026** + `Resolved Business Decisions` section in issue body (inventory/backorder policy, merge rules for linking sources, anonymous cart restrictions, pricing fallback policy).

## Existing Implementation Baseline (Hard Constraint)
- `pos-order` already contains a price-override API surface and persistence model.
- `pos-accounting` already contains payment reversal endpoints (`/v1/accounting/payments/{paymentId}/reverse`).
- CAP-246 work must extend/align existing modules and contracts; do not create parallel duplicate flows in new modules without explicit approval.

## Module and Ownership Guidance
- `pos-order` should own order/cart aggregate behavior and order-level orchestration for CAP-246.
- `pos-workorder` (`domain:workexec`) is authoritative for work-order cancellability and cancellation command acceptance/rejection.
- `pos-accounting` (`domain:billing`) is authoritative for reversal execution and reversal-state outcomes.
- Cross-module coordination must occur via API/event contracts, not direct repository/entity coupling (ADR-0026).

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-246:
1. `#21` — Create Sales Order Cart and Add Items (foundational order/cart aggregate and line semantics)
2. `#20` — Apply Price Override with Permission and Reason (depends on cart/line baseline and pricing semantics)
3. `#19` — Cancel Order with Controlled Void Logic (depends on established order links and cross-domain orchestration contracts)

## Story-Specific Non-Negotiables

### Story `#21` — Create Sales Order Cart and Add Items
- Must support persistent `DRAFT` order/cart creation with deterministic subtotal recalculation.
- Add/update/remove line behavior must be deterministic and auditable.
- Link estimate/workorder with merge semantics:
  - Same SKU/service + same unit price => merge quantity.
  - Same SKU/service + different price/attributes => separate line.
  - Preserve `sourceType`, `sourceId`, `sourceLineId`.
  - Re-linking same source is idempotent.
- Inventory-insufficient default policy is `WARN_AND_BACKORDER` with line-level backorder flag.
- Pricing fallback is bounded:
  - Primary pricing service.
  - Cache fallback TTL 60s with stale marking.
  - Manual price entry only with `ENTER_MANUAL_PRICE`, mandatory reason, and audit.
  - Silent fallback to zero/unknown is forbidden.
- Anonymous carts are allowed, but restricted for customer-dependent actions until customer is assigned.

### Story `#20` — Apply Price Override with Permission and Reason
- Authorization guardrails required (`price.override` and approval workflow controls).
- Threshold policy is config-driven and enforced with OR semantics:
  - `absoluteThresholdMinor = 5000`
  - `percentageThreshold = 10`
  - `perBusinessUnit = true`
  - Compare against `baselinePriceMinor` (not prior overridden price).
- Rounding/normalization is canonicalized with pricing service (`HALF_EVEN`, ISO4217 minor-unit policy).
- Overrides must be idempotent, immutable/auditable, and recorded with before/after values.
- Applied overrides must mark commission impact and emit downstream flag event for recalculation workflow.

### Story `#19` — Cancel Order with Controlled Void Logic
- POS owns cancellation orchestration and persisted saga state machine; no distributed transaction.
- Workexec command is authoritative for cancellability; POS pre-check is advisory UX only.
- Required ordering: Workexec cancel first, then Billing reversal.
- Billing reversal must be idempotent and saga-aware with persisted terminal failure/manual-review states when needed.
- Duplicate cancellation requests must return current state without replaying side effects.
- Failure handling must remain deterministic and audit-visible (`workexec_denial`, `billing_error`, `timeout`, `manual_review` classes).

## Error and Status Semantics
- Use ADR-0017 response mapping and deterministic error bodies for all new/modified endpoints.
- Cancellation and override workflows must expose explicit intermediate and terminal states suitable for retries and operator tooling.
- Idempotent replay must never produce duplicate side effects.

## Audit and Security Rules
- Follow ADR-0018: actor metadata derives from authenticated security context, not caller-supplied identity fields.
- Apply permission checks explicitly on mutation operations (`price.override`, manager approvals, cancellation authorization, manual price entry).
- Use `@EmitEvent` on state-changing endpoints and register/initialize event types for CAP-246 actions.
- Ensure correlation/idempotency identifiers propagate through POS -> Workexec/Billing interactions and logs.

## CAP-246 Execution Deliverables (Per Story)
- RED/GREEN evidence for each acceptance-criteria slice.
- Code Review PASS evidence per story loop.
- Coverage hardening evidence to target service+utility threshold (`>= 80%` unless blocked and documented).
- Contract guide + OpenAPI alignment updates for impacted operations.

## Blocker Policy for CAP-246
- Clarification decisions above are authoritative unless superseded by newer owner comments.
- If blocked, return: exact missing decision, impacted story ID (`#19|#20|#21`), and proposed remediation step.
