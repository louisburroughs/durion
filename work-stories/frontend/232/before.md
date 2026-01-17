Title: [FRONTEND] [STORY] Approval: Invalidate Approval on Estimate Revision
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/232
Labels: frontend, story-implementation, customer

## Frontend Implementation for Story

**Original Story**: [STORY] Approval: Invalidate Approval on Estimate Revision

**Domain**: customer

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
An approved (or pending approval) estimate is modified after submission/approval.

## Main Flow
1. System detects a change that affects scope, pricing, quantities, taxes, or terms.
2. System invalidates existing approval record(s) and marks them as superseded.
3. System transitions estimate back to Draft (or Revision) and requires resubmission.
4. System records invalidation reason and linkage between versions.
5. System prevents promotion until a new valid approval is captured.

## Alternate / Error Flows
- Minor change that does not affect customer-visible outcome → policy may allow non-invalidation (rare; configurable).

## Business Rules
- Any customer-visible change invalidates approval.
- Invalidation must preserve original approval artifact but mark it not-current.
- Promotion validation checks for latest valid approval.

## Data Requirements
- Entities: Estimate, ApprovalRecord, ApprovalSnapshot, AuditEvent
- Fields: invalidationReason, supersededByApprovalId, status, changedFields, changedBy, changedDate

## Acceptance Criteria
- [ ] Approval is invalidated when customer-visible changes occur.
- [ ] System requires resubmission and blocks promotion until re-approved.
- [ ] Audit shows why and when approval was invalidated.

## Notes for Agents
This is the guardrail against scope/price drift—keep it strict.


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*