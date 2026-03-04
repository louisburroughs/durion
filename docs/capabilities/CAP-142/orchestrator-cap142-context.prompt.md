---
name: "CAP-142 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-142 Daily Dispatch Board Dashboard."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-142 runs.

## Capability Scope
- Capability: `CAP:142` — Daily Dispatch Board Dashboard
- Parent issue: `louisburroughs/durion#142` (Assumed, as this is CAP-142)
- Backend story:
  - `louisburroughs/durion-positivity-backend#60` — Reporting: Daily Dispatch Board Dashboard (`domain:workexec`)

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-142/CAPABILITY_MANIFEST.yaml`
2. Story issue:
   - `#60` in `durion-positivity-backend`
3. Domain/ADR context:
   - `durion/docs/adr/0017-api-controller-http-response-codes.adr.md`
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/people/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Clarification Precedence Rules (Hard)
- The story issue #60 contains a "Resolved Questions" section that should be treated as authoritative.
  - **SLA Definition**: P95<2.0s, P50<1.0s, P99<3.5s for initial load.
  - **Data Refresh Policy**: Polling every 30 seconds for V1.
  - **HR Integration Contract**: `GET /v1/people/availability` with a defined schema.
  - **Conflict Rules**: 8 enumerated conditions across 4 categories with defined severity.

## Module and Ownership Guidance
- Primary module ownership:
  - `pos-workorder` (`domain:workexec`): Owns the dashboard's primary endpoint and aggregates data.
- Data sources:
  - `pos-workorder`: Work orders, line items, labor summary.
  - `pos-people`: Mechanic roster, real-time status, schedule, PTO.
  - `pos-shop-manager`: Service bays, occupancy, availability.
  - `pos-price` (Optional): Estimated labor cost per job.

## Story-Specific Non-Negotiables

### Story `#60` — Reporting: Daily Dispatch Board Dashboard
- The dashboard must display data for a selected date, defaulting to today.
- The backend must provide a single endpoint for the dashboard data (e.g., `GET /v1/workexec/dashboard/today`).
- The system must detect and display the 8 enumerated conflict conditions with the correct severity (WARNING vs. BLOCKING).
- The `people` service is the source of truth for mechanic availability.
- The system must not auto-resolve conflicts.

## Error and Status Semantics
- Follow ADR-0017 for standard HTTP response codes.
- The dashboard should handle stale data and offline scenarios gracefully.

## Audit and Security Rules
- All dispatch assignments and conflict detections must be logged for audit purposes.
- The actor performing dispatch actions must be captured from the security context.

## CAP-142 Execution Deliverables
- RED/GREEN evidence for all acceptance criteria in issue #60.
- Code review PASS evidence.
- Test coverage `> 80%` for the new dashboard service and related components.
- Performance tests to validate the SLA.

## Blocker Policy for CAP-142
- All questions in the story are marked as resolved.
- If any new ambiguities arise, they must be clarified before development continues.
