---
name: "CAP-251 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-251 Invoice Payment Status Sync (Accounting Coordination)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-251 runs.

## Capability Scope
- Capability: `CAP:251` — Invoice Payment Status Sync (Accounting Coordination)
- Parent issue: `louisburroughs/durion#251`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#6` — Accounting: Update Invoice Payment Status from Payment Outcomes
  - `louisburroughs/durion-positivity-backend#5` — Accounting: Reconcile POS Status with Accounting Authoritative Status

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-251/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#5`, `#6` in `durion-positivity-backend`
3. Accounting domain/contract context:
   - `durion/domains/accounting/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/accounting/.business-rules/BACKEND_API_REFERENCE.generated.md`
4. Existing backend baseline (must be reused/aligned):
   - `durion-positivity-backend/pos-accounting/openapi.yaml`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/service/InvoicePaymentStatusServiceImpl.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/controller/InvoicePaymentController.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/entity/InvoiceStatusView.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/payment/PaymentGatewayProvider.java`
   - `durion-positivity-backend/pos-accounting/src/main/java/com/positivity/accounting/internal/payment/StripePaymentGateway.java`
5. CAP wireframe context:
   - `durion/domains/accounting/.ui/frontend-story-accounting-reconcile-pos-status-wit-69.wf.md`
6. Cross-cutting ADRs:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`

## Clarification Precedence Rules (Hard)
- If story body conflicts with resolved clarification comments/decision records, use the clarification decision.
- For CAP-251, authoritative clarifications are:
  - Story `#5`: owner clarification comment on **January 14, 2026** (status enum, authority model, event idempotency/retry/DLQ, stale polling, drilldown permissions, archive policy, latency SLA).
  - Story `#6`: owner decision record comment in issue `#408` on **January 17, 2026** (async outbox posting, SLA/retry/escalation, GL mapping defaults, chargeback automation, multi-currency/partial/overpayment policy).
  - Story `#6`: `Resolved Decisions (from clarification #408)` section in issue body is normative unless superseded by a newer owner comment.

## Existing Implementation Baseline (Hard Constraint)
- `pos-accounting` already has invoice payment status APIs, status-view persistence, payment application flows, and event infrastructure.
- CAP-251 must extend and align existing accounting status flows and contracts; do not create parallel status models or duplicate orchestration paths.
- Existing Stripe gateway adapter infrastructure is present in `pos-accounting`; payment-outcome integration must remain adapter-driven and avoid direct gateway coupling in invoice-status orchestration.

## Module and Ownership Guidance
- `pos-accounting` is authoritative for accounting-status computation, posting intent generation, reconciliation/audit artifacts, and accounting-status read APIs.
- `pos-invoice` owns invoice aggregate lifecycle but should consume synchronized accounting status via contracts/events rather than direct persistence coupling.
- POS UI-facing status visibility, drilldown entitlements, and stale-state indicators must use accounting-published state as financial source-of-truth.
- Cross-module communication must follow API/event contracts only (ADR-0026).

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-251:
1. `#6` — Update Invoice Payment Status from Payment Outcomes (foundational status transitions, idempotency, outbox posting intent, and failure semantics)
2. `#5` — Reconcile POS Status with Accounting Authoritative Status (consumes authoritative accounting statuses and synchronization controls for display/drilldown/discrepancy handling)

## Story-Specific Non-Negotiables

### Story `#6` — Update Invoice Payment Status from Payment Outcomes
- Payment outcomes must map to canonical invoice payment statuses deterministically (`Paid`, `PartiallyPaid`, `Unpaid`, `Failed`, `Chargeback`) with minor-unit arithmetic.
- Idempotency must key on `transactionId` first, with `idempotencyKey` fallback; duplicates are strict no-ops.
- Ledger posting is asynchronous via outbox; invoice/payment state updates remain transactional and authoritative.
- SLA/retry/escalation are mandatory: target posting completion <= 5 minutes, exponential retry (`maxRetries = 10`), terminal `postingError` + `InvoicePostingFailed` + reconciliation record on exhaustion.
- Chargebacks require automatic reversal postings on explicit chargeback events; no heuristic trigger logic in v1.
- Overpayment must create customer credit balance while invoice transitions to paid state.

### Story `#5` — Reconcile POS Status with Accounting Authoritative Status
- POS-facing accounting status enum is fixed for v1: `PENDING_POSTING`, `POSTED`, `RECONCILED`, `REJECTED`, `REVERSED`, `VOIDED`, `ON_HOLD`, `DISPUTED`.
- Accounting status is financially authoritative headline state; POS workflow state remains separate and visible.
- Regression/backward status transitions (for example `POSTED -> PENDING_POSTING`) are abnormal and must be ignored/alerted with ordering protection.
- Event processing model is at-least-once with idempotent consumer, bounded retry, DLQ on exhaustion, and max event-age handling.
- Three-tier permissions are required: `VIEW_ACCOUNTING_STATUS`, `REFRESH_ACCOUNTING_STATUS`, `VIEW_ACCOUNTING_DETAIL`.
- Archived invoices must continue to receive status synchronization and audit logging (archive-not-delete policy).
- SLA tiers are mandatory:
  - critical statuses (`POSTED`, `REJECTED`, `REVERSED`, `VOIDED`) => p95 < 5s, p99 < 30s
  - non-critical statuses (`PENDING_POSTING`, `ON_HOLD`, `DISPUTED`, `RECONCILED`) => p95 < 30s, p99 < 2m

## Error and Status Semantics
- Apply ADR-0017 response mapping and deterministic error bodies for status sync, status inquiry, and reconciliation actions.
- Unknown outcomes and processing uncertainty must result in explicit observable states (`PENDING`, `FAILED`, `STALE`, `DISCREPANCY`) rather than silent retries only.
- Out-of-order and duplicate events must not regress or duplicate state transitions.

## Audit and Security Rules
- Follow ADR-0018: actor identity is derived from authenticated security context.
- Use `@EmitEvent` for state-changing endpoints and register event types for CAP-251 status/payment/reconciliation flows.
- Ensure permission checks are explicit for status viewing, refresh, drilldown, and exception workflows.
- Correlation IDs, transaction IDs, and event IDs must propagate through payment outcome ingestion, outbox posting, and reconciliation audit trails.

## CAP-251 Execution Deliverables (Per Story)
- RED/GREEN evidence for each acceptance criteria slice.
- Code Review PASS evidence for each story loop.
- Coverage hardening evidence to target service+utility threshold (`>= 80%` unless blocked and documented).
- Contract guide + OpenAPI + event schema alignment updates for impacted operations.

## Blocker Policy for CAP-251
- Clarification decisions above are authoritative unless superseded by newer owner decisions in `#5`, `#6`, or `#408`.
- If blocked, return: exact missing decision, impacted story ID (`#5|#6`), and proposed remediation step.
