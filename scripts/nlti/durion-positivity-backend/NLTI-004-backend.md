repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Planning Engine: Intent → PlanV1 (ordered steps + preconditions)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:architecture
  - agent:story-authoring
---

## Story Intent (strengthened)
Produce a deterministic, versioned `PlanV1` from `IntentV1` that contains ordered steps, explicit preconditions, risk annotations, and user-friendly plan summary text. Planning must be side-effect free and deterministic for identical inputs.

## Core Output (PlanV1)
- `planId`, `correlationId`, `intentId`, `steps[]` (ordered), `preconditions[]`, `riskLevel`, `requiresConfirmation`, `planSummaryText`.
- Each `step` includes: `stepId`, `actionId`, `description`, `inputs` (resolved values), `expectedOutcome`, `idempotencyKey`.

## Preconditions
- Include entity existence checks, permission checks (requiredPermissions), and slot completion requirements. Represent missing preconditions explicitly with messages and remediation steps.

## Acceptance Criteria
- Given READY intent, planning returns PlanV1 with ordered steps and preconditions.
- Determinism: repeated planning with same IntentV1 returns semantically-equivalent PlanV1 (ids may be stable-deterministic or canonicalized).
- If required tool/action not available or unauthorized, return structured planning error (NOT_AUTHORIZED or TOOL_UNAVAILABLE) with correlationId.

## Test Scenarios
- Unit: verify step generation for sample intents.
- Integration: plan generation respects tool registry availability and authorization.

## Observability
- Emit `nlt.planning.latency_ms`, `nlt.planning.error_count`, and log `planId`, `stepCount`, `riskLevel`.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Planning Engine (Goal → Ordered Steps + Preconditions)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user, I want NLTI to translate my intent into an ordered execution
plan with preconditions so that I can understand what will happen before actions are executed.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Platform engineering, Domain service owners, Security

## Functional Behavior
- Convert structured intent into:
  - Ordered steps
  - Referenced tool actions
  - Preconditions
  - Expected outcomes
- Produce a human-readable explanation of the plan
- Do not execute the plan in this story

## Acceptance Criteria
- Given a valid structured intent, when NLTI processes it, then it produces an ordered plan with steps and referenced tool actions.
- Given missing preconditions, when generating a plan, then the plan indicates what is required before execution.

