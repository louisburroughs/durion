---
name: "CAP-094 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-094 CRM & Workorder Integration."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-094 runs.

## Capability Scope
- Capability: `CAP:094` — CRM & Workorder Integration
- Parent issue: `louisburroughs/durion#94` (Assumed, as this is CAP-094)
- Backend stories:
  - `louisburroughs/durion-positivity-backend#92` — Integration: Inbound Event Handler for Workorder-Originated Updates (`domain:crm`)
  - `louisburroughs/durion-positivity-backend#93` — Integration: Emit CRM Reference IDs in Workorder Artifacts (`domain:workexec`)

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-094/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#92`, `#93` in `durion-positivity-backend`
3. Domain/ADR context:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
   - `durion/docs/adr/0026-service-contract-boundary-policy.adr.md`
   - `durion/domains/crm/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Clarification Precedence Rules (Hard)
- If story text conflicts with resolved clarification comments, use the clarification decisions.
- For story `#93`, use the final resolution from the comment on Jan 11, 2026, which finalizes clarification issue #274. The key resolutions are:
  - **Data Types**: `partyId`, `vehicleId`, and `contactId` are all UUIDv7 strings.
  - **Empty Contacts**: An empty array `[]` for `crmContactIds` is valid.
  - **Alias Resolution**: Alias resolution is the responsibility of the consumer, not the workorder service. A suggested endpoint is `POST /crm/aliases:resolve`.
- Issue #92 has no comments with resolutions.

## Module and Ownership Guidance
- Primary module ownership:
  - `pos-customer` (`domain:crm`): Inbound event handling, data validation, and updates to CRM entities.
  - `pos-workorder` (`domain:workexec`): Storage of CRM reference IDs in `Workorder` and `Estimate` artifacts.
- Integration boundary:
  - `pos-workorder` is the source of truth for operational data and emits events.
  - `pos-customer` consumes these events to keep CRM data synchronized.

## Story Dependency Graph (Hard Planning Constraint)
Required implementation order for CAP-094:
1. `#93` — Emit CRM Reference IDs in Workorder Artifacts (Establishes the data foundation for traceability)
2. `#92` — Inbound Event Handler for Workorder-Originated Updates (Consumes the events containing the references from `#93`)

- `#93` must be completed before `#92` can be fully implemented and tested.

## Story-Specific Non-Negotiables

### Story `#93` — Emit CRM Reference IDs in Workorder Artifacts
- All `Workorder` and `Estimate` entities MUST store non-null `crmPartyId` and `crmVehicleId` as UUIDv7 strings.
- `crmContactIds` can be an empty list but not null.
- The stored CRM IDs are immutable point-in-time references.
- The workorder service does not perform alias resolution.

### Story `#92` — Inbound Event Handler for Workorder-Originated Updates
- Event processing MUST be idempotent.
- The database update and `ProcessingLog` entry creation must be atomic.
- A single malformed event must not block the processing of subsequent valid events.
- The service must handle `VehicleUpdated`, `ContactPreferenceUpdated`, and `PartyNoteAdded` events.

## Error and Status Semantics
- Follow ADR-0017:
  - `400` for malformed payloads, invalid UUID formats, or missing required identifiers.
  - `500` for database persistence failures.
- Use a stable error envelope with a correlation ID.

## Audit and Security Rules
- Follow ADR-0018:
  - Actor fields must come from the authenticated security context.
- Use `@EmitEvent` for state-changing API operations.
- Ensure new permissions are defined for any new API endpoints.

## CAP-094 Execution Deliverables (Per Story)
- RED/GREEN evidence for acceptance criteria.
- Code review PASS evidence.
- Test coverage `> 80%` for new/modified services.

## Blocker Policy for CAP-094
- All clarifications for story #93 are now considered resolved.
- If blocked, return the specific missing contract decision, impacted story ID, and proposed next step.
