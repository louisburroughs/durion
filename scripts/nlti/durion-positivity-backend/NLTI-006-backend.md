---
repo: louisburroughs/durion-positivity-backend
title: "[STORY] NLTI Preview + Confirmation Gate (risk-based execution control)"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
  - agent:backend
  - agent:security
  - agent:story-authoring
---

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:backend
- agent:security
- agent:story-authoring
- capability:natural-language

### Blocking / Risk
- none

**Rewrite Variant:** integration-conservative

## Story Intent
As the NLTI control layer, I want to enforce preview and confirmation gates for risky plans so that destructive or high-impact actions cannot execute without explicit user approval.

## Actors & Stakeholders
- **Primary actor:** Authenticated user reviewing plan.
- **System:** NLTI service.
- **Stakeholders:** Security, compliance, domain owners.

## Preconditions
- PlanV1 exists.
- Plan contains `riskLevel` and `requiresConfirmation` flag.

## Functional Behavior
1. **Preview Endpoint**
   - Provide `GET /nlt/v1/plans/{planId}` returning:
     - ordered steps
     - riskLevel
     - preconditions
     - planSummaryText
2. **Confirmation Recording**
   - Provide `POST /nlt/v1/plans/{planId}/confirm`
     - Record:
       - userId
       - timestamp
       - confirmationToken
3. **Execution Gate**
   - Execution Orchestrator MUST validate:
     - if requiresConfirmation=true
     - confirmation exists and matches user/session
4. **Decline Handling**
   - If user declines, mark plan as CANCELLED.

## Alternate / Error Flows
- Confirmation attempted by different user → reject.
- Expired plan → reject confirmation.
- Attempt execution without confirmation → reject.

## Business Rules
- HIGH risk plans require confirmation.
- Confirmation is plan-specific.
- Confirmation does not transfer across sessions.

## Data Requirements
- `PlanConfirmationRecord`
  - planId
  - userId
  - timestamp
  - confirmationToken
  - status (CONFIRMED|CANCELLED)

## Acceptance Criteria
- **Given** HIGH risk plan, **when** previewed, **then** confirmation is required.
- **Given** confirmation recorded, **when** execution requested, **then** execution proceeds.
- **Given** confirmation not recorded, **when** execution requested, **then** execution is rejected.
- **Given** plan cancelled, **when** execution requested, **then** execution does not proceed.

## Audit & Observability
- Log confirmation events.
- Log rejected execution attempts.
- Emit metric: confirmation_required_count.

## Original Story (Unmodified – For Traceability)
---
repo: louisburroughs/durion
title: "[STORY] NLTI Preview Mode + Confirmation for High-Risk Actions"
labels:
  - type:story
  - domain:positivity
  - status:draft
  - capability:natural-language
---

## Story Intent
As a Positivity user, I want to preview and confirm high-risk actions before execution so that I avoid accidental destructive changes.

## Functional Behavior
- Provide preview of planned steps
- Highlight high-risk operations
- Require explicit confirmation before execution

## Acceptance Criteria
- Given a HIGH risk plan, when user reviews it, then confirmation is required.
- Given confirmation is declined, when execution is requested, then execution does not proceed.