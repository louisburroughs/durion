Title: [FRONTEND] [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/186
Labels: frontend, story-implementation, admin

## Frontend Implementation for Story

**Original Story**: [STORY] Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

**Domain**: admin

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Controls: Route Unmapped or Failed Events to Suspense Queue and Reprocess

## Acceptance Criteria
- [ ] Unmapped/failed events go to Suspense with actionable missing-key details
- [ ] Admin can correct mapping/rules and reprocess
- [ ] Reprocess is idempotent (no duplicate postings)
- [ ] Attempt history and final posting references are retained


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