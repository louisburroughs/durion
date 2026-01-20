Title: [FRONTEND] [STORY] Execution: Record Labor Performed
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/223
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Record Labor Performed

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300002/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician

## Trigger
Technician performs labor associated with a workorder service item.

## Main Flow
1. Technician selects a labor/service line item.
2. Technician records time (start/stop or hours) or marks a flat-rate completion.
3. Technician adds notes/results (optional).
4. System validates permissions and records labor entry.
5. System updates workorder progress and completion indicators.

## Alternate / Error Flows
- Labor entry attempted without assignment → block or warn per policy.
- Negative or unrealistic hours → block and require correction.

## Business Rules
- Labor entries must be attributable to a technician and time.
- Support both flat-rate and time-based labor.
- Entries must be auditable and reversible only with permissions.

## Data Requirements
- Entities: Workorder, WorkorderItem, LaborEntry, AuditEvent
- Fields: workorderId, itemSeqId, technicianId, hours, flatRateFlag, notes, createdAt

## Acceptance Criteria
- [ ] Technicians can record labor entries on assigned workorders.
- [ ] Labor entries are auditable and tied to service items.
- [ ] Progress updates reflect labor completion.
- [ ] Labor entries emit LaborRecorded events when saved
- [ ] Labor events do not create AR or revenue
- [ ] Labor cost is available for job costing or WIP reporting
- [ ] Updates to labor entries supersede prior events
- [ ] Duplicate events do not create duplicate labor cost

## Integrations

### Accounting
- Emits Event: LaborRecorded
- Event Type: Non-posting (job cost / WIP tracking)
- Source Domain: workexec
- Source Entity: WorkorderLaborEntry
- Trigger: Labor entry recorded or completed
- Idempotency Key: workorderId + laborEntryId + version


## Notes for Agents
Even if prices are hidden, labor quantities must remain accurate for invoicing.


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