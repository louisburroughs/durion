Title: [FRONTEND] [STORY] Contacts: Store Communication Preferences and Consent Flags
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/171
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Contacts: Store Communication Preferences and Consent Flags

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to record preferred channel and opt-in/opt-out flags** so that **communications follow customer preferences**.

## Details
- Per-person preferences: preferred channel; basic consent flags for SMS/email.
- Track last-updated and source.

## Acceptance Criteria
- Can set/get preferences.
- Consent flags are available via API.
- Audit is captured.

## Integration Points (Workorder Execution)
- Workorder Execution uses preferences to select notification channel (stubbed initially).

## Data / Entities
- CommunicationPreference
- ConsentRecord

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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