Title: [FRONTEND] [STORY] Promotion: Handle Partial Approval Promotion
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/227
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Handle Partial Approval Promotion

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
An estimate is PartiallyApproved and user promotes approved scope.

## Main Flow
1. System creates workorder using only approved scope items.
2. System marks unapproved items as deferred and keeps them on the estimate.
3. System allows later approval of deferred items to create a change request or supplemental workorder items per policy.
4. System maintains traceability between deferred items and later approvals.
5. System shows clear indicators of partial promotion.

## Alternate / Error Flows
- Later approvals conflict with work already performed → require advisor resolution before adding.

## Business Rules
- Only approved scope is promotable.
- Deferred items remain visible but non-executable until approved.
- Later additions should flow through approval gates.

## Data Requirements
- Entities: ApprovedScope, Workorder, Estimate, ChangeRequest
- Fields: authorizedFlag, deferredFlag, scopeVersion, changeRequestId, status

## Acceptance Criteria
- [ ] Only approved items appear on the initial workorder.
- [ ] Deferred items remain on estimate and can be approved later.
- [ ] System maintains audit trace from deferred items to later changes.

## Notes for Agents
Partial approval adds “later” work—route that through change/approval, not silent edits.


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