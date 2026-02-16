---
repo: louisburroughs/durion
title: "[STORY] NLTI Authorized Tool Registry + Discovery"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to only use tools and actions I am authorized to access so that requests are executed safely and I do not see or invoke restricted operations.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Security/AuthZ team, Platform engineering, Domain service owners

## Functional Behavior
- Maintain a registry of tool/action descriptors (name, purpose, inputs/outputs, risk flags)
- Filter tools/actions returned to NLTI by the user’s permissions
- Provide a consistent “not authorized” message and optional “request access” guidance

## Acceptance Criteria
- Given a user with limited permissions, when NLTI requests available tools, then only authorized tools/actions are returned.
- Given a user attempts a restricted action, when evaluated, then NLTI blocks the action with an authorization error explanation.

---