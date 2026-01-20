Title: [FRONTEND] [STORY] Categories: Define Posting Categories and Mapping Keys
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/203
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Categories: Define Posting Categories and Mapping Keys

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
Categories: Define Posting Categories and Mapping Keys

## Acceptance Criteria
- [ ] Posting categories exist for business meaning (Labor Revenue, Sales Tax Payable, COGS Tires, etc.)
- [ ] Mapping keys used by producers resolve deterministically to categories
- [ ] Category→Account/Dimensions mappings are effective-dated and audit-logged
- [ ] Invalid/overlapping mappings are rejected per policy


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