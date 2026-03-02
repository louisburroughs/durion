---
name: "CAP-248 Orchestrator Context Addendum"
agent: "Orchestrator"
description: "Capability-specific execution context for CAP-248 Estimate, WIP, and Invoice Visibility (Workexec Coordination)."
---

Use this addendum together with `.github/prompts/orchestrator.prompt.md` for CAP-248 runs.

## Capability Scope
- Capability: `CAP:248` — Estimate, WIP, and Invoice Visibility (Workexec Coordination)
- Parent issue: `louisburroughs/durion#248`
- Backend stories:
  - `louisburroughs/durion-positivity-backend#13` — Invoice finalization (controlled) + accounting posting
  - `louisburroughs/durion-positivity-backend#14` — WIP status visibility for active workorders
  - `louisburroughs/durion-positivity-backend#15` — Estimate retrieval/visibility for customer or vehicle

## Canonical Context Sources (Read First)
1. CAP manifest:
   - `durion/docs/capabilities/CAP-248/CAPABILITY_MANIFEST.yaml`
2. Story issues:
   - `#13`, `#14`, `#15` in `durion-positivity-backend`
3. Domain guides and contract references (from manifest):
   - `durion/domains/workexec/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`
   - `durion/domains/workexec/.business-rules/DOMAIN_NOTES.md`
   - `durion/domains/billing/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/billing/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/billing/.business-rules/BACKEND_API_REFERENCE.generated.md`
   - `durion/domains/billing/.business-rules/DOMAIN_NOTES.md`
   - `durion/domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md`
   - `durion/domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md`
   - `durion/domains/shopmgmt/.business-rules/DOMAIN_NOTES.md`

## Decision Precedence Rules (Hard)
- Manifest + resolved decisions in story bodies are authoritative unless a newer explicit maintainer clarification supersedes them.
- If a story body contains a “Resolved Decisions / Summary of Decisions” section, treat those decisions as binding implementation rules.
- Orchestrator and Planner must explicitly cite which story decision they are implementing in each plan slice.

## Module and Ownership Guidance
- Use existing backend anchors; do not create duplicate state models:
  - `pos-workorder`: estimate lifecycle and workorder/WIP canonical status.
  - `pos-shop-manager`: assignment/location context (mechanic/bay/mobile), scheduling-facing metadata.
  - `pos-invoice`: invoice display/finalization workflow and immutable invoice state transitions.
  - `pos-accounting`: async GL posting integration and posting outcome/error lifecycle.
- Shop management remains assignment authority. Work execution remains canonical status authority.
- Billing finalization is a controlled gate and must integrate asynchronously to accounting, not via synchronous hard dependency.

## OpenAPI and Contract Path Resolution
- Use contract guides for behavior and acceptance semantics.
- For schema/status details, prefer module OpenAPI:
  - `durion-positivity-backend/pos-workorder/openapi.json` (fallback `openapi.yaml`)
  - `durion-positivity-backend/pos-invoice/openapi.json` (fallback `openapi.yaml`)
  - `durion-positivity-backend/pos-shop-manager/openapi.json` (fallback `openapi.yaml`)
  - `durion-positivity-backend/pos-accounting/openapi.json` (fallback `openapi.yaml`)
- Do not manually edit generated OpenAPI artifacts.

## Story Planning Constraints
- `#14` and `#15` can run in parallel but must agree on canonical workexec statuses and freshness semantics.
- `#13` must not be marked complete unless async accounting posting behavior, retry policy, and immutable finalization/reversion rules are implemented and tested end-to-end.
- Any cross-module contract change must be reflected in domain contract docs before “done”.

## Story-Specific Non-Negotiables
### Story #13 (Invoice Finalization)
- Finalization gate is controlled by permission + amount threshold:
  - Service Advisor limit `<= $500`
  - Shop Manager unlimited
  - Manager approval required for overrides and fully audited
- Data-integrity failures are hard rejects and non-overridable.
- Finalization is mandatory before payment and transitions invoice to immutable `FINALIZED`.
- Accounting posting is asynchronous/event-driven from `InvoiceFinalized`, idempotent by `invoiceId`, with retries (`5 min`, up to `24h`) and alerting on repeated failure.
- Post-finalization behavior:
  - Success -> `POSTED` with GL linkage
  - Failure -> error state + retry path
- Reversion rules:
  - Allowed only pre-POSTED, within 24h, manager-approved, audited
  - POSTED invoices require accounting reversal flow

### Story #14 (WIP Visibility)
- Default transport is SSE with polling fallback every `30s`; show staleness indicator when data age `> 60s`.
- Workexec owns canonical status values; Shopmgmt provides location-specific display mappings.
- Shopmgmt remains source of truth for assignment context; WIP must support `UNASSIGNED` and queue position.
- `AWAITING_PARTS` must include blocking part detail context with inventory-backed resolution.
- Counter Associate escalation is notification/tasking only; no forced state transitions.
- No automatic customer notifications in this story; manual “send update” is permission-gated.
- Default visibility scope is single location; cross-location requires `WIP_VIEW_ALL_LOCATIONS`.

### Story #15 (Estimate Visibility)
- Workexec canonical estimate statuses are:
  - `DRAFT`, `OPEN`, `PENDING_CUSTOMER`, `APPROVED`, `DECLINED`, `EXPIRED`, `SCHEDULED`, `INVOICED`, `CANCELLED`, `ARCHIVED`
- Editable states are `DRAFT`, `OPEN`, `PENDING_CUSTOMER`; locked states include `APPROVED`, `SCHEDULED`, `INVOICED`, `CANCELLED`, `ARCHIVED`.
- Validity default is 30 days (location-configurable); expired estimates block conversion actions unless manager extension applies.
- Clone must deep-copy + reprice, produce a new `estimateId`, and start in `DRAFT`.
- Cardinality default is one active appointment per estimate (unique `linkedAppointmentId`).
- Customer approval is primary; staff proxy approval requires evidence + permission; manager approval required above configured thresholds.
- Estimate `estimatedDurationMinutes` is a scheduling hint; appointment-level overrides must not mutate historical estimate content.
- Sync semantics require event-driven update path with polling fallback and effective staleness target `<= 60s`.

## Audit and Observability Requirements
- Ensure key events are emitted and traceable across module boundaries:
  - Invoice: `InvoiceFinalizationRequested`, `InvoiceFinalized`, `InvoicePostedToGL`, `InvoicePostingFailed`, override/denial/approval events.
  - WIP: status change and staleness/connection-degraded signals.
  - Estimate: create/update/status-change events used for live views.
- Preserve correlation IDs across finalization -> accounting posting pipeline.

## CAP-248 Execution Deliverables (Per Story)
- Story-level RED evidence mapped to ACs.
- GREEN evidence for same AC scope.
- Contract behavior tests updated/added in provider modules.
- Explicit cross-module integration proof for async events and idempotency where required.
- Handoff section that cites which resolved decisions were implemented.

## Blocker Policy for CAP-248
- Do not mark story done when decisions above are only partially implemented.
- If blocked, return:
  - exact missing contract or dependency
  - impacted story IDs
  - fallback attempted
  - smallest completed unblocked slice
  - next concrete remediation step
