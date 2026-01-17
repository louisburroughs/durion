Title: [FRONTEND] [STORY] Promotion: Record Promotion Audit Trail
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/226
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Record Promotion Audit Trail

**Domain**: user

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
A promotion event completes successfully.

## Main Flow
1. System records an audit event including who initiated promotion and when.
2. System records the estimate snapshot version and approval reference used.
3. System stores a summary of items promoted (counts, totals) for quick review.
4. System links audit record to the workorder and estimate.
5. System exposes audit record in UI for authorized roles.

## Alternate / Error Flows
- Audit write fails → fail promotion or retry per strictness policy (recommended: fail).

## Business Rules
- Promotion must be auditable and traceable.
- Audit must reference the exact snapshot promoted.

## Data Requirements
- Entities: AuditEvent, Workorder, Estimate, ApprovalRecord
- Fields: eventType, actorUserId, timestamp, estimateId, snapshotVersion, approvalId, workorderId, summaryTotals

## Acceptance Criteria
- [ ] Promotion event is stored and retrievable.
- [ ] Audit record references estimate snapshot and approval.
- [ ] Audit record shows summary totals and item counts.

## Notes for Agents
Audit isn’t optional—this protects you in customer disputes.


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