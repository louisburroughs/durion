Title: [FRONTEND] [STORY] Estimate: Present Estimate Summary for Review
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/234
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Present Estimate Summary for Review

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
A Draft estimate is ready to be reviewed with the customer before approval submission.

## Main Flow
1. User requests a customer-facing summary view/printout.
2. System generates a summary that includes scope, quantities, pricing, taxes, fees, and totals.
3. System excludes restricted internal fields (cost, margin) based on role/policy.
4. System includes configured terms, disclaimers, and expiration date.
5. User shares summary with customer and optionally proceeds to submit for approval.

## Alternate / Error Flows
- Terms/disclaimers not configured → use defaults or block submission depending on compliance settings.

## Business Rules
- Customer summary must be consistent with the estimate snapshot.
- Visibility rules must be enforced for internal-only fields.
- Expiration must be clearly shown if configured.

## Data Requirements
- Entities: Estimate, EstimateItem, DocumentTemplate, VisibilityPolicy
- Fields: displayPrice, taxTotal, grandTotal, termsText, expirationDate, hiddenCostFields

## Acceptance Criteria
- [ ] Customer-facing summary is generated and matches estimate totals.
- [ ] Restricted fields are not displayed to unauthorized roles.
- [ ] Summary includes terms and expiration where configured.

## Notes for Agents
This output becomes the basis for consent text during approval—keep it deterministic.


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