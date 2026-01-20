Title: [FRONTEND] [STORY] Promotion: Create Workorder from Approved Estimate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/230
Labels: frontend, story-implementation, order

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Create Workorder from Approved Estimate

**Domain**: order

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
Promotion preconditions are satisfied for an approved estimate.

## Main Flow
1. System creates a Workorder header using customer and vehicle context from the estimate snapshot.
2. System links workorder to estimate version and approval record.
3. System sets workorder initial state (e.g., Ready or Scheduled) per configuration.
4. System applies role-based visibility rules (e.g., hide prices for mechanics).
5. System records the promotion event for audit.

## Alternate / Error Flows
- Workorder creation fails due to validation/config error → rollback and report error.
- Promotion retried after partial failure → idempotent recovery.

## Business Rules
- Workorder must reference estimate snapshot and approval record.
- Initial state is policy-driven.
- Promotion must be auditable.

## Data Requirements
- Entities: Workorder, Estimate, ApprovalRecord, AuditEvent
- Fields: workorderId, status, estimateId, estimateVersion, approvalId, shopId, createdBy, createdDate

## Acceptance Criteria
- [ ] A workorder is created and linked to the approved estimate snapshot.
- [ ] Initial workorder state matches configuration.
- [ ] Audit trail shows who promoted and when.

## Notes for Agents
Keep promotion atomic: either you have a valid workorder, or you have nothing.


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