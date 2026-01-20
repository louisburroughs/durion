Title: [FRONTEND] [STORY] Completion: Resolve Approval-Gated Change Requests
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/217
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Resolve Approval-Gated Change Requests

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
Workorder contains one or more approval-gated change requests.

## Main Flow
1. User views outstanding change requests tied to the workorder.
2. User submits requests for customer approval (if not already submitted).
3. System records approval/rejection outcomes.
4. Approved requests add authorized items to the workorder; rejected requests remain excluded.
5. System clears the completion block when all requests are resolved.

## Alternate / Error Flows
- Customer unreachable → leave request pending and keep workorder incomplete.
- Emergency exception policy invoked → record exception and proceed per policy.

## Business Rules
- All approval-gated requests must be resolved before completion (unless exception).
- Approvals must be linked to added items.

## Data Requirements
- Entities: ChangeRequest, ApprovalRecord, WorkorderItem
- Fields: changeRequestId, status, approvalId, resolutionAt, resolutionBy, exceptionReason

## Acceptance Criteria
- [ ] Pending change requests block completion.
- [ ] Resolved approvals correctly add or exclude items.
- [ ] Resolution is auditable.

## Notes for Agents
Make resolution visible; operators should never guess what’s blocking completion.


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