Title: [FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/115
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want correct pricing on estimate lines so that customers receive accurate quotes.

## Details
- Request: productId, qty, locationId, customer tier, effective time.
- Response: unit/ext price, MSRP, breakdown, policy flags.

## Acceptance Criteria
- Deterministic evaluation order (base→store override→rounding).
- Returns breakdown + warnings.
- SLA suitable for UI.

## Integrations
- Workexec → Product PriceQuote API; optional availability in same response.

## Data / Entities
- PriceQuoteRequest, PriceQuoteResponse, PricingRuleTrace

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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