Title: [FRONTEND] [STORY] Timekeeping: Mechanic Clock In/Out
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/149
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Mechanic Clock In/Out

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to clock in and out** so that **my attendance time is recorded accurately**.

## Details
- Capture UTC timestamp and local timezone.
- Optional validation that mechanic is assigned to the selected location.

## Acceptance Criteria
- One-tap clock in/out.
- Prevent double clock-in without clock-out.
- Entries are auditable.

## Integration Points (workexec/shopmgr)
- shopmgr shift can be displayed for context (optional).

## Data / Entities
- TimeEntry (attendance)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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