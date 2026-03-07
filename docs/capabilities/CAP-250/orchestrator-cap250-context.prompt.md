---
name: "CAP-250 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-250 Payments (Card Acceptance via Payment Service)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-250 runs.

## Capability Scope
- Capability: `CAP:250` — Payments (Card Acceptance via Payment Service)
- Parent issue: `louisburroughs/durion#250`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#9` — Payment: Initiate Card Authorization and Capture
  - `louisburroughs/durion-positivity-backend#7` — Payment: Print/Email Receipt and Store Reference
  - `louisburroughs/durion-positivity-backend#8` — Payment: Void Authorization or Refund Captured Payment

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-250/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#7`, `#8`, `#9` in `durion-positivity-backend`
3. Billing domain/contract context:
   - `durion/domains/billing/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`
4. Existing Stripe implementation context (current backend baseline):
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/payment/PaymentGatewayProvider.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/payment/StripePaymentGateway.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/service/APPaymentServiceImpl.java`
   - `durion-positivity-backend/pos-accounting/src/main/resources/application.yml`
   - `durion-positivity-backend/pos-accounting/PAYMENT_GATEWAY_SETUP.md`
5. Cross-cutting ADRs:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`

## Clarification Precedence Rules (Hard)
- If story body conflicts with resolved clarification comments, use the clarification comment decisions.
- For CAP-250, the authoritative clarification comments are:
  - Story `#7`: owner clarification comment on **January 14, 2026** (receipt content, template ownership/versioning, email SLA/retries, reprint authorization, retention, multi-tender, PCI/PII).
  - Story `#8`: owner clarification comment on **January 14, 2026** (reason enums, windows, approval tiers, async refund lifecycle, partial refund limits, split-tender allocation).
  - Story `#9`: owner clarification comment on **January 14, 2026** (single-gateway MVP, auth hold windows, flow selection, partial capture policy, retry/inquiry semantics, permissions).

## Existing Stripe Baseline (Hard Constraint)
- Stripe is already implemented in backend via `PaymentGatewayProvider` + `StripePaymentGateway` in `pos-accounting`.
- Current provider defaults to `stripe` (`payment.gateway.provider`) and uses `stripe.api-key` configuration.
- Current Stripe integration uses Charges API patterns and existing idempotency/status inquiry hooks.
- CAP-250 work must build from this baseline and avoid parallel/duplicate gateway abstractions unless explicitly approved as a migration.

## Module and Ownership Guidance
- `pos-invoice` is the billing domain service and should own invoice/payment receipt-facing contracts for CAP-250.
- `pos-accounting` already contains payment-gateway adapter plumbing and Stripe implementation details that should be reused/aligned, not re-invented.
- Integration boundary:
  - Payment gateway execution concerns stay behind backend service abstractions.
  - Cross-module coupling must follow API/event contracts (ADR-0026).

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-250:
1. `#9` — Initiate Card Authorization and Capture (foundational payment lifecycle and idempotency behavior)
2. `#7` — Print/Email Receipt and Store Reference (depends on successful capture artifacts and transaction references)
3. `#8` — Void/Refund (depends on established payment states/lifecycle and amount allocation semantics)

## Story-Specific Non-Negotiables

### Story `#9` — Initiate Card Authorization and Capture
- MVP supports one configured gateway via adapter abstraction.
- Default flow is `SALE_CAPTURE`; `AUTH_ONLY` only when policy/flags require delayed capture.
- Use strict idempotent retry policy and gateway status inquiry for unknown outcomes.
- Partial capture in v1 is single partial capture per auth, with remainder voided.
- Explicit permission model required: `PROCESS_PAYMENT`, `MANUAL_CAPTURE`, `VOID_PAYMENT`, `OVERRIDE_PAYMENT_LIMIT`, and story-defined flow selection permission.

### Story `#7` — Print/Email Receipt and Store Reference
- Receipt Content v1 mandatory fields apply.
- Receipt must persist immutable `templateId` + `templateVersion`; reprints must use original template version.
- Email delivery is async, idempotent, with bounded retries and explicit failure outcomes.
- Reprint authorization and limits are enforced; duplicate/reprint watermarking is mandatory.
- Privacy/PCI requirements are strict: no PAN storage, least-privilege lookup, encrypted email at rest.

### Story `#8` — Void Authorization or Refund Captured Payment
- Void/refund reason sets are controlled enums with `OTHER` requiring notes.
- Method-specific windows and amount-tier approvals are configurable and enforceable.
- Refund lifecycle is asynchronous: `REQUESTED -> PENDING -> COMPLETED/FAILED`.
- Partial refund limits and minimums are enforced; split-tender refunds use deterministic allocation (default LIFO).
- All overrides and out-of-window actions require auditable authorization trail.

## Error and Status Semantics
- Follow ADR-0017 for status code behavior and deterministic error handling.
- Unknown gateway outcomes must resolve via status inquiry before allowing reattempts.
- Pending/failed async refund states must be explicit and externally observable.

## Audit and Security Rules
- Follow ADR-0018: actor fields derive from authenticated security context.
- Use `@EmitEvent` on state-changing APIs and register event types.
- Ensure permissions are declared and enforced for payment processing, capture, void, refund, and reprint-related actions.

## CAP-250 Execution Deliverables (Per Story)
- RED/GREEN evidence for acceptance criteria.
- Code review PASS evidence.
- Test coverage `> 80%` for new/modified services.
- Contract updates synchronized with Billing domain guide/openapi as needed.

## Blocker Policy for CAP-250
- Clarifications in the issue comments above are considered resolved unless superseded by newer owner decisions.
- If blocked, return: exact missing decision, impacted story ID, and proposed next step.
