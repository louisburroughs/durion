---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Planning Engine: Intent → PlanV1 (ordered steps + preconditions)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
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
- agent:architecture
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI planning subsystem, I want to convert a READY structured intent into a deterministic, versioned execution plan so that users can review intended actions before execution occurs.

## Actors & Stakeholders
- **Primary actor:** NLTI Planning Engine.
- **Upstream:** Intent Parser (Capability 02).
- **Downstream (future):** Execution Orchestrator.
- **Stakeholders:** Security, domain tool owners.

## Preconditions
- Intent status is `READY`.
- Authorized tool registry is available.
- No execution occurs in this story.

## Functional Behavior
1. **Plan Generation**
   - Input: `IntentV1`
   - Output: `PlanV1` including:
     - `planId`
     - `correlationId`
     - `steps[]` (ordered)
     - `preconditions[]`
     - `riskLevel`
     - `requiresConfirmation` (boolean)
2. **Step Structure**
   Each step MUST include:
   - `stepId`
   - `actionId` (from tool registry)
   - `description`
   - `inputs` (resolved slots)
   - `expectedOutcome`
3. **Precondition Identification**
   - Identify required data/state:
     - Required entity existence
     - Required permissions
     - Required slot completion
   - Represent missing preconditions explicitly.
4. **Confirmation Flagging**
   - If `riskLevel = HIGH`, set `requiresConfirmation = true`.
5. **Human Explanation**
   - Produce `planSummaryText` describing steps in plain language.

## Alternate / Error Flows
- **Intent not READY:** return error indicating clarification required.
- **Tool not authorized:** planning fails with NOT_AUTHORIZED.
- **No matching tool:** return structured planning error.

## Business Rules
- Planning MUST be deterministic for identical intent inputs.
- Planning MUST NOT perform side effects.
- Plan schema MUST be versioned.

## Data Requirements
- `PlanV1` schema (versioned).
- `StepV1` schema.
- Precondition objects with `type`, `status`, `message`.

## Acceptance Criteria
- **Given** a READY intent, **when** planning is invoked, **then** a `PlanV1` with ordered steps is returned.
- **Given** missing required slots, **when** planning is invoked, **then** a structured planning error is returned.
- **Given** a HIGH risk intent, **when** plan is generated, **then** `requiresConfirmation=true`.
- **Given** identical input intent, **when** planning occurs twice, **then** resulting steps are equivalent.

## Audit & Observability
- Log planning invocation with correlationId.
- Log planId and step count.
- Emit metric: planning_latency_ms.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Planning Engine (Goal → Ordered Steps + Preconditions)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to translate my intent into an ordered execution plan with preconditions so that I can understand what will happen before actions are executed.

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
- Given a risky intent, when generating a plan, then the plan indicates that confirmation will be required before execution.