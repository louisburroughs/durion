---
repo: louisburroughs/durion
title: "[STORY] NLTI Audit & Traceability (Request → Plan → Execution Ledger)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user and auditor, I want NLTI actions and outcomes to be fully traceable so that automated work can be reviewed, explained, and audited.

## Actors & Stakeholders
- Primary: Authenticated Positivity user
- Stakeholders: Security/Compliance, Support/SRE, Domain service owners

## Functional Behavior
- Record an audit trail for:
  - NLTI requests
  - Parsed intent
  - Generated plans
  - Confirmations
  - Tool calls and outcomes
- Allow searching by correlation ID and primary business identifiers

## Acceptance Criteria
- Given any NLTI request, when it completes, then there is an audit record chain linking request → plan → execution and step outcomes.
- Given a correlation ID, when searched, then all related NLTI records are retrievable.