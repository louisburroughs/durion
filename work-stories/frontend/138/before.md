Title: [FRONTEND] [STORY] Scheduling: View Schedule by Location and Resource
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/138
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: View Schedule by Location and Resource

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want a daily schedule view by location/bay/mobile so that I can manage capacity and avoid conflicts.

## Details
- Views: location calendar, bay lanes, mobile list.
- Conflict highlighting and filters.

## Acceptance Criteria
- Filter by date/location/resource.
- Conflicts flagged.
- Loads within SLA.

## Integrations
- Optional HR availability overlay.

## Data / Entities
- ScheduleView(read model), ConflictFlag

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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