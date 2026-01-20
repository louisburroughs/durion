Title: [FRONTEND] [STORY] Promotion: Enforce Idempotent Promotion
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/228
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Enforce Idempotent Promotion

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
Promotion is executed multiple times due to retries or double actions.

## Main Flow
1. System checks for an existing promotion record for (estimateId, snapshotVersion).
2. If a workorder exists, system returns the existing workorder reference instead of creating a duplicate.
3. If promotion was partially completed, system completes missing pieces safely.
4. System records retry event for diagnostics/audit (optional).
5. User sees a single canonical workorder link.

## Alternate / Error Flows
- Promotion record exists but workorder deleted/corrupted → require admin intervention and block.

## Business Rules
- Promotion must be idempotent under retries.
- Promotion record is the authoritative link between estimate snapshot and workorder.

## Data Requirements
- Entities: PromotionRecord, Workorder, Estimate, AuditEvent
- Fields: promotionKey, estimateId, snapshotVersion, workorderId, status, retryCount

## Acceptance Criteria
- [ ] Repeated promotion attempts do not create duplicate workorders.
- [ ] System returns the same workorder URL/number for the same snapshot.
- [ ] Partial promotion can be safely completed.

## Notes for Agents
Idempotency prevents data integrity nightmares—treat it as non-negotiable.


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