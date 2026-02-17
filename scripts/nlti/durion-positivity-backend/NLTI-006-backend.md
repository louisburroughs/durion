repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Preview + Confirmation Gate (risk-based execution control)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - agent:backend
  - agent:security
  - agent:story-authoring
---

## Story Intent (strengthened)
Offer a preview endpoint and a firm confirmation gate for `PlanV1` executions. Confirmation is required for plans flagged `HIGH` risk and must be recorded with an auditable confirmation token tied to the requesting user and session.

## Key Behavior
- `GET /nlt/v1/plans/{planId}` returns ordered steps, riskLevel, preconditions, estimated impact and `requiresConfirmation` flag.
- `POST /nlt/v1/plans/{planId}/confirm` records `userId`, timestamp, confirmation token and verification of session and subject.
- Execution orchestrator must validate confirmation before executing plans requiring confirmation.

## Acceptance Criteria
- HIGH risk plans block execution until an unexpired confirmation record exists for the same user and session.
- Confirmation attempted by another user is rejected with 403 and recorded in audit logs.

## Test Scenarios
- Confirm/Cancellation flows, expired token handling, cross-user rejection.

---

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Preview Mode + Confirmation for High-Risk Actions"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language

## Story Intent
As a Positivity user, I want to preview and confirm high-risk actions before execution so that I avoid accidental destructive changes.

## Functional Behavior
- Provide preview of planned steps
- Highlight high-risk operations
- Require explicit confirmation before execution

## Acceptance Criteria
- Given a HIGH risk plan, when user reviews it, then confirmation is required.
- Given confirmation is declined, when execution is requested, then execution does not proceed.

