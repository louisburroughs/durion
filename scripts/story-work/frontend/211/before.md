Title: [FRONTEND] [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/211
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Preserve Traceability Links (Estimate/Approval/Workorder)

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
System

## Trigger
Invoice draft is generated.

## Main Flow
1. System stores references from invoice to workorder.
2. System stores references from invoice to originating estimate version.
3. System stores references from invoice to approval artifacts/records.
4. System exposes traceability in UI for authorized roles.
5. System includes reference identifiers in customer-facing invoice where configured.

## Alternate / Error Flows
- Origin artifacts missing due to data corruption → block issuance and alert admin.

## Business Rules
- Invoices must be traceable to the approved scope and executed work.

## Data Requirements
- Entities: Invoice, Workorder, Estimate, ApprovalRecord, DocumentArtifact
- Fields: workorderId, estimateId, estimateVersion, approvalId, artifactRef, traceabilitySummary

## Acceptance Criteria
- [ ] Invoice contains links to workorder and estimate/approval trail.
- [ ] Authorized users can retrieve approval artifacts from invoice context.
- [ ] Issuance is blocked if traceability is incomplete (policy).

## Notes for Agents
Traceability is your defense in disputes; enforce it.


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