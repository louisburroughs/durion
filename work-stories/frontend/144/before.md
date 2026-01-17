Title: [FRONTEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/144
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Attendance vs Job Time Discrepancy Report

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Manager**, I want **a report comparing attendance time to job time** so that **I can identify gaps, overhead, and anomalies**.

## Details
- Summarize by technician/day/location.
- Flag differences above a configurable threshold.

## Acceptance Criteria
- Report shows clocked hours vs job timer total.
- Flags exceptions.

## Integration Points (workexec)
- Optional: correlate with labor lines for reconciliation.

## Data / Entities
- TimeEntry
- JobLink

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