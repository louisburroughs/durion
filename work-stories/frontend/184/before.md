Title: [FRONTEND] [STORY] Accounting: Ingest InventoryAdjustment Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/184
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryAdjustment Event

**Domain**: user

### Story Description

/kiro
Handle inventory corrections with full auditability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryAdjusted` event from Workorder Execution

## Main Flow
1. Validate adjustment reason and quantities
2. Reverse or adjust prior inventory/COGS entries
3. Apply corrected inventory quantities
4. Record adjustment journal with reason code

## Business Rules
- Adjustments must reference original issue
- Negative inventory positions are prohibited unless explicitly allowed

## Acceptance Criteria
- [ ] Adjustments reconcile inventory correctly
- [ ] Prior postings are traceable and reversible

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

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