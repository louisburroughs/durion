Title: [FRONTEND] [STORY] Estimate: Revise Estimate Prior to Approval
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/235
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Revise Estimate Prior to Approval

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
Service Advisor

## Trigger
A Draft or PendingApproval estimate requires changes (scope, price, quantities, fees, or notes).

## Main Flow
1. User opens the estimate and selects 'Revise'.
2. System creates a new estimate version (or revision record) linked to the prior version.
3. User edits line items and/or terms.
4. System recalculates totals and updates revision metadata.
5. System invalidates any prior approvals and sets state back to Draft (or Revision state) per policy.

## Alternate / Error Flows
- Estimate is already promoted to workorder → disallow revision or create a change request workflow (policy).
- User lacks permission to revise after approval submission → block and log.

## Business Rules
- Revision must preserve history and allow comparing versions.
- Any revision after approval invalidates approval.
- Revision increments version and records who/when/why.

## Data Requirements
- Entities: Estimate, EstimateRevision, ApprovalRecord, AuditEvent
- Fields: estimateId, version, status, revisionReason, revisedBy, revisedDate, priorVersionRef

## Acceptance Criteria
- [ ] System preserves revision history and allows retrieving prior versions.
- [ ] Any existing approval is invalidated on revision and recorded.
- [ ] Revised estimate totals are recalculated and stored.

## Notes for Agents
Revision history is a core audit artifact—do not overwrite approved snapshots.


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