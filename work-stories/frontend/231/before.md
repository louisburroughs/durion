Title: [FRONTEND] [STORY] Promotion: Validate Promotion Preconditions
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/231
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Validate Promotion Preconditions

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
Service Advisor / Back Office

## Trigger
User attempts to promote an estimate to a workorder.

## Main Flow
1. User selects 'Promote to Workorder' on an estimate.
2. System verifies the estimate has a current valid approval (not expired, not superseded).
3. System verifies approved scope exists (full or partial) and references the correct snapshot version.
4. System checks that promotion has not already occurred for this estimate/snapshot (idempotency).
5. System either allows promotion or returns actionable errors.

## Alternate / Error Flows
- Approval missing/expired → block and instruct resubmission.
- Promotion already performed → return existing workorder reference.

## Business Rules
- Only current valid approvals can be promoted.
- Promotion must be idempotent.
- Approved scope governs what becomes executable work.

## Data Requirements
- Entities: Estimate, ApprovalRecord, ApprovedScope, Workorder, AuditEvent
- Fields: estimateId, snapshotVersion, approvalStatus, expiresAt, promotionRef, workorderId

## Acceptance Criteria
- [ ] System blocks promotion if approval is invalid or expired.
- [ ] System detects and handles re-tries without duplicates.
- [ ] System reports specific failed preconditions.

## Notes for Agents
Make precondition failures actionable—this saves operator time.


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