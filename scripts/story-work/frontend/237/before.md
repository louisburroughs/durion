Title: [FRONTEND] [STORY] Estimate: Add Labor to Estimate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/237
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Add Labor to Estimate

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
A Draft estimate exists and labor/services need to be quoted.

## Main Flow
1. User searches service catalog by service code or description.
2. User selects a service and specifies hours/units or selects a flat-rate option.
3. System defaults labor rate based on shop, role/class, and pricing rules.
4. System adds the labor line item and recalculates totals.
5. User adds notes/instructions if required for execution.

## Alternate / Error Flows
- Service not found → allow controlled custom service entry (if enabled) with required description and labor units.
- Labor units invalid (<=0) → block and prompt correction.

## Business Rules
- Each labor line item references a service code (or controlled custom code).
- Labor rate defaulting must be deterministic (policy-driven).
- Totals must be recalculated on labor line changes.

## Data Requirements
- Entities: Estimate, EstimateItem, ServiceCatalog, LaborRateRule, AuditEvent
- Fields: itemSeqId, serviceCode, laborUnits, laborRate, flatRateFlag, notes, taxCode

## Acceptance Criteria
- [ ] User can add labor/service line items to a Draft estimate.
- [ ] Labor pricing defaults correctly per configured rules.
- [ ] Totals update immediately after adding/editing labor items.

## Notes for Agents
Keep labor structure compatible with time-based and flat-rate models.


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