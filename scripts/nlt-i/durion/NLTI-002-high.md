repo: louisburroughs/durion
title: "[STORY] NLTI Intent Parsing + Clarification Dialogue"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want NLTI to interpret my request into an actionable intent and ask clarifying questions when needed so that my goals are executed correctly without unsafe assumptions.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Platform engineering, Security, Domain teams, Support/Operations

## Functional Behavior
- Classify input as “question” vs “action”
- Extract key entities (dates, identifiers, customer/work order/invoice references)
- When intent is ambiguous or missing required details, ask targeted clarification questions and present selectable options

## Acceptance Criteria
- Given an ambiguous request, when NLTI cannot safely infer parameters, then it asks a clarification question.
- Given a clear request, when NLTI parses it, then it produces a structured intent representation.
- Given a request that appears risky, when parsed, then NLTI marks it as requiring confirmation before execution.