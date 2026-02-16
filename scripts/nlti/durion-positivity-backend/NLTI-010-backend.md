repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Guidance Mode: “How do I…” answers + Convert-to-Plan"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:frontend
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:frontend
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As a Positivity NLTI user, I want “how do I…” questions answered with procedural guidance and (when applicable) the ability to convert guidance into a PlanV1 so that I can learn and execute tasks in one flow.

## Actors & Stakeholders
- **Primary actor:** Authenticated user seeking guidance.
- **System:** NLTI service (guidance responder + planner).
- **Stakeholders:** Product enablement, support, domain owners.

## Preconditions
- Intent classification exists (Capability 02).
- Planning engine exists (Capability 04).
- Tool registry exists (Capability 03).

## Functional Behavior
1. **Guidance Detection**
   - When intent is classified as a “guidance query” (e.g., starts with “how do I”, “what are the steps to”), NLTI routes to Guidance Mode.
2. **Guidance Response**
   - Provide structured response:
     - `guidanceTitle`
     - `steps[]` (numbered)
     - `notes[]` (optional)
     - `supportedForExecution` (boolean)
3. **Convert-to-Plan**
   - If `supportedForExecution=true`, NLTI offers “Convert to Plan” which:
     - Produces an IntentV1 + PlanV1 consistent with the described steps
     - Returns plan for preview and confirmation (as required)
4. **Bounded Knowledge Sources**
   - Guidance is grounded in approved internal documentation/config (no external web dependency required for correctness in this story).

## Alternate / Error Flows
- Unknown workflow → respond with best-effort guidance and mark `supportedForExecution=false`.
- Ambiguous workflow → ask clarification questions before producing guidance.

## Business Rules
- Guidance must not recommend actions the user is not authorized to perform.
- Convert-to-Plan must still go through authorization-aware planning.

## Data Requirements
- `GuidanceResponseV1` schema.
- Mapping table from known workflows → plan templates (initially minimal).

## Acceptance Criteria
- **Given** a how-to query, **when** NLTI responds, **then** it returns numbered steps and an overall guidance title.
- **Given** a known supported workflow, **when** user selects convert-to-plan, **then** NLTI returns a PlanV1 suitable for preview.
- **Given** an unauthorized user for a workflow, **when** guidance is generated, **then** restricted steps are omitted or replaced with “request access”.

## Audit & Observability
- Log guidance requests with correlationId and workflow classification.
- Metric: guidance_request_count, convert_to_plan_rate.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Guidance Mode (How do I… → Steps + Optional Plan)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to answer “how do I…” questions with clear steps and optionally convert the guidance into an executable plan so I can learn and act from one place.

## Functional Behavior
- Provide step-by-step guidance for supported workflows
- When a workflow is supported, allow converting guidance into a plan

## Acceptance Criteria
- Given a “how do I…” request, when NLTI responds, then it provides actionable steps.
- Given the workflow is supported, when the user chooses “execute”, then NLTI generates a plan for review.