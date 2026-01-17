Title: [FRONTEND] [STORY] Accounting: Reverse Completion on Workorder Reopen
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/182
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Reverse Completion on Workorder Reopen

**Domain**: user

### Story Description

/kiro
Safely reverse completion-related accounting state.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `WorkorderReopened` event

## Main Flow
1. Validate reopen authorization
2. Reverse WIP/finished postings if present
3. Mark workorder as not invoice-ready
4. Record reversal audit trail

## Acceptance Criteria
- [ ] Accounting state matches reopened workorder
- [ ] Reversal is fully auditable


## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)



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