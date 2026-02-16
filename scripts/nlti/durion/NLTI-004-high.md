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