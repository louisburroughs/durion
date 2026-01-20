Title: [FRONTEND] [STORY] Completion: Reopen Completed Workorder (Controlled)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/214
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Reopen Completed Workorder (Controlled)

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300018/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A completed workorder needs correction after completion.

## Main Flow
1. Authorized user selects 'Reopen' and provides a mandatory reason.
2. System records reopen audit event and transitions workorder to Reopened (or InProgress per policy).
3. System unlocks specific editable fields per policy.
4. System marks prior billable scope snapshot as superseded.
5. System requires re-completion and re-snapshot before invoicing.

## Alternate / Error Flows
- User lacks permission → block.
- Invoice already issued → block reopen or require credit/rebill workflow (out of scope).

## Business Rules
- Reopen is an exception workflow with strict permissions and audit.
- Reopening invalidates invoice-ready snapshot.

## Data Requirements
- Entities: Workorder, BillableScopeSnapshot, AuditEvent
- Fields: status, reopenReason, reopenedBy, reopenedAt, supersededSnapshotVersion

## Acceptance Criteria
- [ ] Only authorized users can reopen completed workorders.
- [ ] Reopen is auditable and requires a reason.
- [ ] Invoice-ready snapshot is invalidated and must be regenerated.
- [ ] Reopen emits a single WorkorderReopened event
- [ ] Any completion-related accounting state is reversible
- [ ] Invoice eligibility is revoked if not yet invoiced
- [ ] Reopen requires authorization and records reason
- [ ] Repeated reopen attempts do not emit duplicate events

## Integrations

### Accounting
- Emits Event: WorkorderReopened
- Event Type: Non-posting (reversal / invalidation signal)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Authorized reopen of a completed workorder
- Idempotency Key: workorderId + reopenVersion


## Notes for Agents
Don’t allow silent edits after completion; reopen is the controlled escape hatch.


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