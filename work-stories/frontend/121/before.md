Title: [FRONTEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/121
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want to create a product with SKU/MPN, manufacturer, type, and attributes so that all modules reference a consistent product master.

## Details
- Identifiers: internal SKU, manufacturer part number, optional UPC.
- Attributes: description, category, tire size/spec, UOM, active/inactive.

## Acceptance Criteria
- Create/update/deactivate product.
- Search by SKU/MPN/keywords.
- Changes audited.

## Integrations
- Inventory and Workexec reference productId consistently.

## Data / Entities
- Product, ProductIdentifier, ProductAttribute, ManufacturerRef, AuditLog

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