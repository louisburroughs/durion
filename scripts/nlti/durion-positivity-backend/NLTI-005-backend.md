repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Execution Orchestrator: Step Runner + Idempotency + Partial Failure Handling"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:sre
  - agent:architecture
---

## Story Intent (strengthened)
Implement an execution orchestrator that runs `PlanV1` steps sequentially with idempotency guarantees, configurable retry policy for transient errors, robust partial-failure handling, and structured `ExecutionResultV1` for audit and UI consumption.

## Core Behavior
- Execution receives `planId` and `correlationId`, creates `executionId` and executes steps in order.
- Each step is invoked via a tool adapter with `idempotencyKey`, `correlationId`, and `userContext`.
- Retries: configurable per-action retry policy (with exponential backoff & max attempts), only for transient errors.
- On permanent failure: mark execution as `PARTIAL_FAILURE` or `FAILED`, include failed step details and partial results.

## Acceptance Criteria
- Steps execute in order; completed steps are recorded.
- Idempotency: re-submitting same `executionId` or step `idempotencyKey` does not produce duplicate mutations.
- Retry behavior executes only for transient failures and respects max attempts.

## Observability & Audit
- Emit `nlt.execution.start`, `nlt.execution.step.completed`, `nlt.execution.step.failed` metrics with `executionId` and `correlationId` tags.
- Append execution events to audit ledger per NLTI audit story requirements.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Execution Orchestrator (Safe Step Runner + Idempotency)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user, I want NLTI to safely execute approved plans across multiple tools so that my goals are completed reliably and transparently.

## Functional Behavior
- Execute ordered plan steps
- Propagate correlationId
- Support idempotency and retries
- Handle partial failures and report results

## Acceptance Criteria
- Given an approved plan, when execution begins, then steps execute in order.
- Given a transient failure, when retryable, then the system retries per policy.
- Given partial failure, when execution completes, then a structured result summary is available.

