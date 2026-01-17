Title: [FRONTEND] [STORY] Estimate: Create Draft Estimate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/239
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Create Draft Estimate

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
Service Advisor / Front Desk

## Trigger
A customer requests service or a quote for a vehicle (walk-in, phone, email, or fleet request).

## Main Flow
1. User selects or creates the Customer record.
2. User selects or creates the Vehicle record and captures context (VIN/plate, odometer, notes).
3. User clicks 'Create Estimate' and the system creates a Draft estimate with an identifier.
4. System sets default shop/location, currency, and tax region based on configuration.
5. User is taken to the Draft estimate workspace to add line items.

## Alternate / Error Flows
- Customer or Vehicle missing required fields → prompt user to complete required context.
- Estimate creation attempted without permissions → block and log access attempt.

## Business Rules
- Estimate starts in Draft state.
- Estimate identifier is unique per shop/location.
- Audit event is recorded on creation.

## Data Requirements
- Entities: Estimate, Customer, Vehicle, UserPermission, AuditEvent
- Fields: estimateId, status, customerId, vehicleId, shopId, currencyUomId, taxRegionId, createdBy, createdDate

## Acceptance Criteria
- [ ] A Draft estimate is created with required customer and vehicle context.
- [ ] Estimate status is Draft and visible.
- [ ] Creation is recorded in audit trail.

## Notes for Agents
Keep estimate creation decoupled from approval and workorder logic. Establish baseline validations and defaulting rules.


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