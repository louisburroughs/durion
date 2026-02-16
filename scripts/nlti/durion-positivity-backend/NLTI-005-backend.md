---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Execution Orchestrator: Step Runner + Idempotency + Partial Failure Handling"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:sre
  - agent:architecture
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:sre
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI execution subsystem, I want to execute a confirmed PlanV1 safely with idempotency and structured result reporting so that cross-domain workflows are reliable and auditable.

## Actors & Stakeholders
- **Primary actor:** Execution Orchestrator.
- **Upstream:** Planning Engine.
- **Downstream:** Domain tool adapters.
- **Stakeholders:** Security, SRE, domain service owners.

## Preconditions
- PlanV1 exists.
- If `requiresConfirmation=true`, confirmation has been recorded.
- Authorized tool registry validated.

## Functional Behavior
1. **Execution Start**
   - Accept `planId`.
   - Generate `executionId`.
2. **Step Execution**
   - Execute steps in order.
   - Pass correlationId to each tool.
   - Include idempotencyKey per step.
3. **Retry Policy**
   - Retry transient failures (configurable count).
4. **Partial Failure Handling**
   - If step fails permanently:
     - Mark step FAILED.
     - Stop execution.
     - Return partial summary.
5. **Result Summary**
   - Return structured `ExecutionResultV1` including:
     - status (SUCCESS|PARTIAL_FAILURE|FAILED)
     - completedSteps[]
     - failedStep (if any)
     - userSummaryText

## Alternate / Error Flows
- Plan not found → structured error.
- Tool timeout → retry or mark failed per policy.
- Idempotency conflict → treat as already completed.

## Business Rules
- Steps MUST execute in order.
- No parallel execution in this story.
- Idempotency required for all mutating tool calls.

## Data Requirements
- `ExecutionResultV1`
- `ExecutionStepResult`
- idempotency store (implementation-specific)

## Acceptance Criteria
- **Given** confirmed plan, **when** execution starts, **then** steps execute sequentially.
- **Given** transient error, **when** retry policy applies, **then** retry occurs.
- **Given** permanent failure, **when** execution stops, **then** result status is PARTIAL_FAILURE or FAILED.
- **Given** repeated execution request with same idempotency key, **when** invoked, **then** duplicate mutation does not occur.

## Audit & Observability
- Log executionId, correlationId.
- Log step-level outcomes.
- Emit metrics: execution_success_rate, execution_latency_ms.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Execution Orchestrator (Safe Step Runner + Idempotency)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

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
- Given partial failure, when execution completes, then a structured result summary is returned.