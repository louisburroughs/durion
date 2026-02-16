repo: louisburroughs/durion
title: "[STORY] NLTI Foundation & Shell (Chat panel + session + command router)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want a Natural Language Task Interface entry point where I can type requests and see structured results so I can start accomplishing work through a single conversational interface.

## Actors & Stakeholders
- Primary: Authenticated Positivity user (Service Manager, Accounting Clerk, etc.)
- Stakeholders: Platform engineering, Security, Domain teams (workexec/accounting/inventory/crm)

## Functional Behavior
- Provide an NLTI panel with:
  - Text input and submit
  - Session-scoped history
  - Rendered responses (answer, plan, errors)
- Provide a backend NLTI endpoint that:
  - Accepts a prompt + session context
  - Returns a structured response envelope with a correlation ID

## Acceptance Criteria
- Given an authenticated user, when they submit a prompt, then a response is returned and displayed with a correlation ID.
- Given an error, when the backend returns a failure, then the UI shows a friendly error and the correlation ID.
- Given multiple prompts in a session, when the user submits sequential requests, then the UI shows session history in order.