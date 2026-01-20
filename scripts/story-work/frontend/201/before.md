Title: [FRONTEND] [STORY] GL: Build Balanced Journal Entry from Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/201
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Build Balanced Journal Entry from Event

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Build Balanced Journal Entry from Event

## Acceptance Criteria
- [ ] Mapped events create a draft journal entry with header refs (eventId/source refs/rule version)
- [ ] JE is balanced per currency (debits=credits)
- [ ] Each JE line includes category, account, and dimension references
- [ ] Mapping failures route to suspense or rejection per policy (no partial postings)


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