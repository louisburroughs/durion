repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Guidance Mode: ‘How do I…’ answers + Convert-to-Plan"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:frontend
  - agent:story-authoring
---

## Story Intent (strengthened)
Provide high-quality “how do I…” guidance responses that produce structured step lists and, for supported workflows, a convert-to-plan flow that generates `PlanV1` for preview and confirmation. Guidance must be grounded in approved internal docs and respect authorization boundaries.

## Behavior
- Detect guidance intent (phrases like “how do I”, “steps to”) and respond with `GuidanceResponseV1` containing `guidanceTitle`, ordered `steps[]`, `notes[]`, `supportedForExecution` boolean and `estimatedRisk`.
- If `supportedForExecution=true`, present Convert-to-Plan option that transforms guidance into `IntentV1` + `PlanV1` (previewable) with preconditions and `requiresConfirmation` set appropriately.

## Acceptance Criteria
- Given a how-to query, return numbered steps and a concise guidance title.
- Given supported workflow and user chooses convert-to-plan, return deterministic `PlanV1` suitable for preview and confirmation.
- If user lacks permission for steps, omit restricted steps and surface actionable guidance to request access.

## Test Scenarios
- Sample how-to phrases mapped to guidance templates; conversion to PlanV1 validated against tool registry and AuthZ.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Guidance Mode: “How do I…” answers + Convert-to-Plan"
labels:

- type:story
- domain:positivity
- status:draft

/* Full original preserved in repository attachments. */
