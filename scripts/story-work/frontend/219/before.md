Title: [FRONTEND] [STORY] Execution: Apply Role-Based Visibility in Execution UI
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/219
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Apply Role-Based Visibility in Execution UI

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
A mechanic/technician views a workorder during execution.

## Main Flow
1. System identifies viewer role and applicable visibility policy.
2. System hides restricted pricing/cost fields from mechanic views.
3. System continues to store full financial data in the underlying records.
4. Back office views show full pricing/cost data.
5. System logs access to sensitive fields when shown (optional).

## Alternate / Error Flows
- Role misconfiguration → default to safer (hide sensitive) behavior and alert admins.

## Business Rules
- Visibility policies must be consistently enforced across screens and APIs.
- Hiding fields must not remove financial truth from the data model.

## Data Requirements
- Entities: VisibilityPolicy, Workorder, WorkorderItem, UserRole
- Fields: roleId, canViewPrices, unitPrice, extendedPrice, cost, margin

## Acceptance Criteria
- [ ] Mechanic views do not display restricted financial fields.
- [ ] Back office views retain full visibility.
- [ ] Financial data remains present for invoicing and reporting.

## Notes for Agents
This is a UI/API policy layer; keep it separate from business calculations.


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