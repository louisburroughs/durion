Title: [FRONTEND] [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/103
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Topology: Create Storage Locations (Floor/Shelf/Bin/Cage/Truck) and Hierarchy

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want to define storage locations and hierarchy so that stock can be tracked precisely.

## Details
- Types: Floor, Shelf, Bin, Cage, Yard, MobileTruck, Quarantine.
- Parent/child relationships within a site.
- Attributes: barcode, capacity (optional), temperature (optional).

## Acceptance Criteria
- Create/update/deactivate storage locations.
- Prevent cycles in hierarchy.
- Uniqueness of barcode within site.
- Audited.

## Integrations
- Workexec pick tasks reference storageLocationId.
- Shopmgr can display storage hints (optional).

## Data / Entities
- StorageLocation, StorageType, StorageHierarchy, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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