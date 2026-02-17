repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Tool Adapters v1: Implement initial domain action set + contracts"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:architecture
  - agent:story-authoring
---

## Story Intent (strengthened)
Define v1 tool adapter contracts and implement an initial set of adapters with deterministic input/output schemas, authorization checks, and contract tests so NLTI can execute a first set of domain actions safely.

## Requirements
- Adapter interface includes: `validate(inputs)`, `transform(inputs)`, `call()`, `normalizeOutput()` and must return `ActionResultV1` with `status`, `summaryText`, `details`.
- Each action descriptor must include `actionId`, `inputSchema`, `outputSchema`, `requiredPermissions`, `riskLevel`.

## Register Actions (minimum)
- WorkExec: listCompletedWorkOrders, closeWorkOrder, dailySummary.
- Accounting: listUnpaidInvoices, reprocessPayment (requires high privilege + confirmation).

## Contract Tests
- Each adapter must include unit/contract tests validating validation, error responses, and idempotency behaviour.

## Acceptance Criteria
- Given a supported action with valid inputs, adapter returns normalized OK with `summaryText`.
- Given missing input, adapter returns structured ERROR without calling downstream.
- Given unauthorized user, adapter returns NOT_AUTHORIZED and does not call downstream.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Domain Tool Adapters v1 (WorkExec, Accounting, Inventory, CRM)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user, I want NLTI to perform real work across core domains using approved tool adapters so I can complete common tasks without switching between systems.

## Functional Behavior
Implement an initial set of NLTI tools/actions for:
- WorkExec: list/close completed work orders, daily summary
- Accounting: list unpaid invoices, explain suspense status, reprocess failed payments (if authorized)
- Inventory: low stock report, adjust counts (with confirmation)
- CRM: customer lookup, contact details, notes

## Acceptance Criteria
- Given a supported task, when invoked via NLTI, then the correct domain adapter is called and a structured result is returned.
- Given an unauthorized action, when invoked, then NLTI blocks it and explains why.

