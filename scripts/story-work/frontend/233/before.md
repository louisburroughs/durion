Title: [FRONTEND] [STORY] Approval: Submit Estimate for Customer Approval
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/233
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Approval: Submit Estimate for Customer Approval

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
A Draft estimate is complete and ready for customer consent.

## Main Flow
1. User selects 'Submit for Approval'.
2. System validates estimate completeness (required fields, items, taxes, totals, terms).
3. System transitions estimate to PendingApproval and freezes an approval snapshot.
4. System generates an approval request payload (method, link/token, consent text).
5. System logs the submission event for audit.

## Alternate / Error Flows
- Validation fails (missing taxes, missing items) → block and show actionable errors.
- Estimate already pending approval → prevent duplicate submissions and show status.

## Business Rules
- Only submit-ready estimates may enter PendingApproval.
- Submission creates an immutable approval snapshot version.
- Submission must be auditable (who/when).

## Data Requirements
- Entities: Estimate, ApprovalRequest, ApprovalSnapshot, AuditEvent
- Fields: status, approvalRequestId, snapshotVersion, consentText, submittedBy, submittedDate, approvalMethod

## Acceptance Criteria
- [ ] System blocks submission when required completeness checks fail.
- [ ] PendingApproval state is set and visible.
- [ ] An approval snapshot is created and referenced by the request.

## Notes for Agents
Approval snapshot must remain immutable; later revisions require resubmission.


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