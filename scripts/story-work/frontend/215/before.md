Title: [FRONTEND] [STORY] Completion: Complete Workorder and Record Audit
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/215
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Complete Workorder and Record Audit

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300007/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Shop Manager

## Trigger
Completion preconditions are satisfied.

## Main Flow
1. User selects 'Complete Workorder'.
2. System transitions workorder to Completed state.
3. System records completion timestamp and completing user.
4. System stores completion notes and optional inspection outcomes.
5. System locks execution edits except via controlled reopen workflow.

## Alternate / Error Flows
- Completion attempted with failing preconditions → block (covered by validation story).

## Business Rules
- Completion transition must be explicit and auditable.
- Completion locks billable scope unless reopened with permissions.

## Data Requirements
- Entities: Workorder, AuditEvent, InspectionRecord
- Fields: status, completedAt, completedBy, completionNotes, inspectionOutcome

## Acceptance Criteria
- [ ] Workorder transitions to Completed only when preconditions pass.
- [ ] Completion is auditable (who/when).
- [ ] Workorder is locked against uncontrolled edits.
- [ ] WorkCompleted event is emitted once per completion
- [ ] Event includes final billable scope and totals snapshot
- [ ] WIP accounting (if enabled) is finalized correctly
- [ ] Completion does not create AR or revenue
- [ ] Repeated completion attempts do not duplicate events

## Integrations

### Accounting
- Emits Event: WorkCompleted
- Event Type: Non-posting or Posting (WIP → Finished, if enabled)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Workorder transitioned to Completed state
- Idempotency Key: workorderId + completionVersion


## Notes for Agents
Treat completion as a state transition with strong validations and audit.


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