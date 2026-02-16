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