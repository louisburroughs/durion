---
title: Work Order Execution Backend Contract Guide
domain: workexec
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-workorder/openapi.yaml
openapi_commit: 83164e57
last_verified_utc: 2026-09-07T02:20:00Z
last_updated: 2026-09-07
api_reference_generated: domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Work Order Execution Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Work Order Execution domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-workorder/openapi.yaml`
- Generated API reference: `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/workexec/.business-rules/AGENT_GUIDE.md`

## How To Use This Guide

Backend coder workflow:

1. Read `Domain Invariants` and the relevant capability section.
2. Validate behavior constraints before implementing endpoint changes.
3. Use `operationId` mappings here, then confirm payload details in generated API reference.
4. Ensure tests cover each changed behavioral assertion.

Frontend developer workflow:

1. Start with `Frontend API Lookup` and identify the `operationId` for the UI action.
2. Open generated API reference for exact payload and response details.
3. Implement error handling and headers described in this guide.

## Domain Invariants

- Work Order Execution behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-002 | `durion#2` | draft | [CAP] Create and Manage Estimates |
| CAP-003 | `durion#3` | draft | [CAP] Capture Customer Approval |
| CAP-004 | `durion#4` | draft | [CAP] Promote Estimate to Workorder |
| CAP-005 | `durion#5` | draft | [CAP] Execute Workorder (Parts & Labor) |
| CAP-006 | `durion#6` | draft | [CAP] Complete Workorder |
| CAP-007 | `durion#7` | draft | [CAP] Convert Workorder to Invoice |
| CAP-142 | `durion#142` | draft | [CAP] Daily Dispatch Board Dashboard |
| Tier 0 | `durion-positivity-backend#1569`, `#1575` | draft | Estimated labor on the quote — guide-time and labor-rate prefill, overlap-aware estimated hours, estimate-vs-actual variance, and the labor-intelligence rollup |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete an approval configuration | `deleteConfiguration` | DELETE | `/v1/workexec/approvalConfigurations/{approvalId}` | Refer to generated API reference for payload details |
| Review what finished work says about an operation | `listLaborIntelligence` | GET | `/v1/workorders/labor-intelligence/operations` | Advisory rollup; never auto-applied |
| Delete an estimate | `deleteEstimate` | DELETE | `/v1/workorders/estimates/{estimateId}` | Refer to generated API reference for payload details |
| Remove line item | `deleteEstimateItem` | DELETE | `/v1/workorders/estimates/{estimateId}/items/{itemId}` | Refer to generated API reference for payload details |
| Delete a work order | `deleteWorkorder` | DELETE | `/v1/workorders/{workorderId}` | Refer to generated API reference for payload details |
| Get all approval configurations | `getAllConfigurations` | GET | `/v1/workexec` | Refer to generated API reference for payload details |
| Get applicable configuration | `getApplicableConfiguration` | GET | `/v1/workexec/approvalConfigurations/applicable` | Refer to generated API reference for payload details |
| Get configuration by ID | `getConfigurationById` | GET | `/v1/workexec/approvalConfigurations/{approvalId}` | Refer to generated API reference for payload details |
| Operation | `getJobTimeTotals` | GET | `/v1/workexec/job-time-totals` | Refer to generated API reference for payload details |
| Operation | `getActiveTimerEntries` | GET | `/v1/workexec/time-entries/timer/active` | Refer to generated API reference for payload details |
| Get all work orders | `getAllWorkorders` | GET | `/v1/workorders` | Refer to generated API reference for payload details |
| Get change request by ID | `getChangeRequestById` | GET | `/v1/workorders/changeRequests/{changeId}` | Refer to generated API reference for payload details |
| Get all estimates | `getAllEstimates` | GET | `/v1/workorders/estimates` | Refer to generated API reference for payload details |
| Get estimates by customer | `getEstimatesByCustomer` | GET | `/v1/workorders/estimates/customer/{customerId}` | Refer to generated API reference for payload details |
| Get estimates by location | `getEstimatesByLocation` | GET | `/v1/workorders/estimates/location/{locationId}` | Refer to generated API reference for payload details |
| Get estimates by shop | `getEstimatesByShop` | GET | `/v1/workorders/estimates/shop/{locationId}` | Refer to generated API reference for payload details |
| Find estimate (typeahead) | `searchEstimates` | GET | `/v1/workexec/estimates/search?q=` | `q` matches estimate number, customer name, or estimate id; returns `EstimateSummaryResponse` enriched with `customerName`. Auth `workorder:estimate:view`. |
| Find workorder (typeahead / filtered search) | `searchWorkorders` | GET | `/v1/workorders/search?q=` | `q` matches customer name or workorder id; optional exact filters `customerId`, `vehicleId`, `technicianId`, `createdFrom`/`createdTo` and `status` (one or more values — see the search-filter note below the table). Auth `workorder:workorder:view`. (durion-positivity-backend#1676) |
| View daily dispatch board | `getDashboard` | GET | `/v1/workexec/dashboard/today` | Supports optional `?date=YYYY-MM-DD` query param; defaults to today. Refer to generated API reference for payload details |
| Record a note about the customer | `addWorkorderNote` | POST | `/v1/workorders/{workorderId}/notes` | Note about the CUSTOMER, not the work; author comes from the authenticated caller. Auth `workorder:note:add`. Publishes `workorder.note.added.v1`, which pos-customer projects onto the party's CRM timeline as a `WORKORDER_NOTE` interaction (durion-positivity-backend#1584). |
| View a workorder's customer notes | `listWorkorderNotes` | GET | `/v1/workorders/{workorderId}/notes` | One workorder's notes, newest first. Auth `workorder:note:view`. For notes across every workorder for a customer, read the CRM interaction timeline instead. |

Search-filter note for `searchWorkorders` (durion-positivity-backend#1676):

- `status` accepts several values, repeated (`status=A&status=B`) or comma-separated (`status=A,B`), so every open status is one call; an unrecognized value is a 400.
- `createdFrom`/`createdTo` are inclusive `YYYY-MM-DD` bounds on `createdAt`, evaluated in UTC; `technicianId` matches a technician who logged a labor entry on the workorder.
- Returns a page of `WorkorderSearchResult` `{workorderId, workorderNumber, estimateNumber, status, customerId, customerName, vehicleId, vehicleLabel, vin, createdAt}`; default page size 25, hard-capped at 100.

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-002: [CAP] Create and Manage Estimates

### Capability Metadata

- Capability ID: CAP-002
- Parent Issue: <https://github.com/louisburroughs/durion/issues/2>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete an approval configuration | `deleteConfiguration` | DELETE | `/v1/workexec/approvalConfigurations/{approvalId}` |
| Delete an estimate | `deleteEstimate` | DELETE | `/v1/workorders/estimates/{estimateId}` |
| Remove line item | `deleteEstimateItem` | DELETE | `/v1/workorders/estimates/{estimateId}/items/{itemId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-003: [CAP] Capture Customer Approval

### Capability Metadata

- Capability ID: CAP-003
- Parent Issue: <https://github.com/louisburroughs/durion/issues/3>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete a work order | `deleteWorkorder` | DELETE | `/v1/workorders/{workorderId}` |
| Get all approval configurations | `getAllConfigurations` | GET | `/v1/workexec` |
| Get applicable configuration | `getApplicableConfiguration` | GET | `/v1/workexec/approvalConfigurations/applicable` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-004: [CAP] Promote Estimate to Workorder

### Capability Metadata

- Capability ID: CAP-004
- Parent Issue: <https://github.com/louisburroughs/durion/issues/4>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get configuration by ID | `getConfigurationById` | GET | `/v1/workexec/approvalConfigurations/{approvalId}` |
| Operation | `getJobTimeTotals` | GET | `/v1/workexec/job-time-totals` |
| Operation | `getActiveTimerEntries` | GET | `/v1/workexec/time-entries/timer/active` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-005: [CAP] Execute Workorder (Parts & Labor)

### Capability Metadata

- Capability ID: CAP-005
- Parent Issue: <https://github.com/louisburroughs/durion/issues/5>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get all work orders | `getAllWorkorders` | GET | `/v1/workorders` |
| Get change request by ID | `getChangeRequestById` | GET | `/v1/workorders/changeRequests/{changeId}` |
| Get all estimates | `getAllEstimates` | GET | `/v1/workorders/estimates` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-006: [CAP] Complete Workorder

### Capability Metadata

- Capability ID: CAP-006
- Parent Issue: <https://github.com/louisburroughs/durion/issues/6>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get estimates by customer | `getEstimatesByCustomer` | GET | `/v1/workorders/estimates/customer/{customerId}` |
| Get estimates by location | `getEstimatesByLocation` | GET | `/v1/workorders/estimates/location/{locationId}` |
| Get estimates by shop | `getEstimatesByShop` | GET | `/v1/workorders/estimates/shop/{locationId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-007: [CAP] Convert Workorder to Invoice

### Capability Metadata

- Capability ID: CAP-007
- Parent Issue: <https://github.com/louisburroughs/durion/issues/7>
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get estimate by ID | `getEstimateById` | GET | `/v1/workorders/estimates/{estimateId}` |
| Get estimate summary (customer-facing) | `getEstimateSummary` | GET | `/v1/workorders/estimates/{estimateId}/summary` |
| Get work order by ID | `getWorkorderById` | GET | `/v1/workorders/{workorderId}` |

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-142: [CAP] Daily Dispatch Board Dashboard

### Capability Metadata

- Capability ID: CAP-142
- Parent Issue: <https://github.com/louisburroughs/durion/issues/142>
- Backend Story: <https://github.com/louisburroughs/durion-positivity-backend/issues/60>
- Capability Status: draft

### Behavioral Contract

#### getDashboardToday — `GET /v1/workexec/dashboard/today`

- Returns aggregated dispatch board data for the selected date (defaults to today via server-side date resolution if no `date` query param is provided).
- Accepts optional query parameter: `?date=YYYY-MM-DD`
- Data aggregated from: `pos-workorder` (workorders, line items, labor), `pos-people` (mechanic roster, availability, schedule), `pos-shop-manager` (bay occupancy)
- SLA targets: P50 < 1.0s, P95 < 2.0s, P99 < 3.5s. Implementation must use server-side caching; cache invalidation on workorder mutation or mechanic status change.
- Backend returns stale data indicator when upstream dependencies (pos-people, pos-shop-manager) are unavailable; must not fail the entire request.

#### Conflict Detection — 8 Enumerated Conditions

The endpoint MUST include a `conflicts` array in the response. Each conflict entry has `severity` (WARNING | BLOCKING) and `conditionCode`.

BLOCKING conflicts (prevent dispatch):

- `MECHANIC_DOUBLE_BOOKED` — mechanic assigned to overlapping workorders
- `BAY_DOUBLE_BOOKED` — bay assigned to overlapping workorders
- `MECHANIC_UNAVAILABLE` — mechanic on approved PTO or sick leave
- `MECHANIC_NOT_CLOCKED_IN` — mechanic not clocked at expected job start

WARNING conflicts (advisory, do not prevent dispatch):

- `BREAK_OVERLAP` — break occurs within 15-minute grace period of job window
- `WORKORDER_COUNT_APPROACHING_CAPACITY` — workorder count near bay capacity limit
- `AVAILABILITY_UNCONFIRMED` — mechanic availability not yet confirmed for the date
- `SHIFT_ENDING_DURING_JOB` — mechanic shift ends during assigned job window

#### HR Availability Integration

- Source of truth for mechanic availability: `pos-people` via `GET /v1/people/availability?date={date}`
- Orchestrator (pos-workorder dashboard service) calls this endpoint; it must not access pos-people database directly.
- If `pos-people` is unavailable, dashboard must return with `dataQualityWarning: true` and use cached/last-known availability data.

#### Data Freshness

- Frontend polling interval: 30 seconds.
- Backend response includes `lastRefreshed` timestamp (ISO-8601 UTC).
- Manual refresh is supported; the endpoint is stateless, and each call returns fresh-aggregated data.

### Provider Test Hints

- Test that `HEAD /v1/workexec/dashboard/today` returns HTTP 200 when data is available.
- Test that all 8 conflict conditions are detectable independently.
- Test that BLOCKING conflicts are returned even when WARNING conflicts are also present.
- Test that a date in the past returns data (not an error) when workorders exist for that date.
- Test that `GET /v1/workexec/dashboard/today?date=2026-03-09` returns the same shape as the default call.
- Test that if `pos-people` is unreachable, the response still returns HTTP 200 with `dataQualityWarning: true`.

### References

- OpenAPI: `durion-positivity-backend/pos-workorder/openapi.yaml` (operationId: `getDashboard`)
- Generated API reference: `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`

## Finder Search (estimate / workorder)

Typeahead finders on the workexec landing resolve a typed customer name or record id to a record.

- `GET /v1/workexec/estimates/search?q=` (`searchEstimates`) and `GET /v1/workorders/search?q=`
  (`searchWorkorders`) are **read-only**; no mutation.
- `q` resolution: estimate `q` matches `estimateNumber` (case-insensitive), customer name, or estimate
  id (UUID); workorder `q` matches customer name or workorder id (UUID). Workorders have no human-readable
  number — customer name is the primary key.
- Customer **name → ids** is resolved against pos-customer party browse (`/v1/crm/accounts/parties?name=`)
  at query time; `customerName` is enriched on the response (ADR-0015 §6 — name mastered in pos-customer,
  not denormalized here). **Fail-soft:** if pos-customer is unreachable, name matching yields empty while
  number/id matching still returns results.
- Permissions: `workorder:estimate:view` (estimates), `workorder:workorder:view` (workorders). Events:
  `WORKORDER_ESTIMATE_SEARCH`, `WORKORDER_SEARCH`.
- Contract tests: `EstimateSearchContractBehaviorIT`, `WorkorderSearchContractBehaviorIT` (+ service unit
  tests `EstimateSearchByQueryTest`, `WorkorderSearchServiceTest`, `CustomerReferenceServiceTest`).
- Realized by: durion-positivity-backend#734, durion-positivity-sdk-angular#16,
  durion-positivity-frontend#89 (CAP — workexec typeahead finders).

## Tier 0: Estimated Labor On The Quote

### Capability Metadata

- Capability ID: Tier 0 (issue-driven; no capability manifest)
- Parent Issue: `durion-positivity-backend#1569` (estimated service time), `durion-positivity-backend#1575` (sourcing tiers)
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-workorder/openapi.yaml`
- ADRs: ADR-0058 §5, ADR-0044 amendments 2026-09-02 (labor time) and 2026-09-07 (labor rate)

A LABOR line is two operands. pos-catalog answers **how long** an operation takes on a vehicle,
pos-price answers **what an hour of it costs** at a location, and this module multiplies them,
sums them honestly, and compares the result against what technicians actually clocked.

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Review finished work per operation | `listLaborIntelligence` | GET | `/v1/workorders/labor-intelligence/operations` |

The rest of this capability is **behavior on existing estimate and workorder operations**
(`addEstimateItem`, the estimate promotion path, and the workorder detail read) rather than new
endpoints. Those operationIds are unchanged; what changed is what they accept and return.

### Behavioral Assertions

**A LABOR line may omit both operands, and gets a prefill — never a lock.**

- `addEstimateItem` on a LABOR item naming a `serviceId` may omit `quantity`, `unitPrice`, or both.
  Omitted `quantity` is prefilled from the resolved guide time; omitted `unitPrice` from the shop's
  labor rate with its matrix applied. A writer's explicit value **always wins**.
- `rateAdjustmentCodes` on the request are the matrix steps the writer agreed apply (corrosion,
  after-hours, a fleet contract). Codes the shop has not priced are ignored, not rejected.
- If a prefill is unavailable and the writer sent nothing, the request is rejected with a message
  naming the missing field — a guide or rate miss is a request problem, never a line with a null
  quantity or no price.

**Both baselines are snapshotted beside the agreed numbers.** `quantity` stays the agreed hours and
`unitPrice` the charged rate; `guideHours` + its source, revision and match grade record what the
guide published, and `rateHourly` / `rateBaseHourly` / `rateScope` / `rateAdjustmentCodes` record
what pos-price published and which matrix steps produced it. An adjusted quote therefore keeps both
numbers on both operands. All of it rides onto `workorder_service` at promotion, on both the
approved and declined paths.

**The workorder total is not a naive sum.** `WorkorderSummary.estimatedLaborHours` is
overlap-aware:

- A line whose operation code appears in another line's guide-included list contributes **zero** —
  rotors include pads, and charging both bills the same work twice. Resolved largest-hours-first,
  so a zeroed line's include list never cascades and mutual includes keep the larger line.
- Lines sharing a guide `overlapGroup` (the wheels come off once) contribute the group's largest
  time in full and each additional line at `pos.workorder.labor.overlap-additional-factor`
  (default 0.5). That is the recorded v1 simplification; real per-pair deductions arrive with
  licensed guide data.
- The sum reads the promotion-time **snapshots**, not the live catalog, so it keeps computing the
  same answer after the guide publishes a new revision.

**Variance is a subtraction, and it compares like with like.** The detail response exposes
`estimatedLaborHours`, `actualLaborHours`, `laborVarianceHours` and `laborVariancePct`. Actual time
is **time on task** (`WorkorderLaborEntry.hoursWorked`), never attendance. Per the owner ruling on
#1573, estimated service time and time entries are unrelated systems: nothing here reads or writes
`work_session`, `time_entry` or `TimekeepingEntry`.

**`listLaborIntelligence` is advisory and deliberately conservative (#1575 Tier 0 / Tier 4).**

- Groups by operation and shop, with a technician breakdown. Shops are reported separately, never
  pooled.
- Only lines carrying a guide baseline count — an actual with nothing to compare against would move
  the variance without changing any real estimate.
- `suggestedStandardHours` is withheld entirely below the sample floor
  (`pos.workorder.labor-intelligence.min-samples`, default 5) rather than offered with a caveat: a
  median of three jobs is a rumour. The measurement is still reported. A caller may raise the floor,
  never lower it.
- A technician's median counts only lines that technician worked **alone**; a split line says
  nothing about either one's speed.
- **Not grouped by vehicle class.** pos-workorder holds VIN, plate and odometer but no make or
  model, so that dimension is unavailable rather than approximated.
- Nothing is promoted automatically. This module writes nothing to pos-catalog; promotion is a
  curator authoring a `DURION_STANDARD` labor standard there.

**Known limits of the resolve request.** The estimate path sends year/make/model and the quoting
`locationId`, and leaves submodel, engine code and `preferredTimeType` null: the CRM vehicle record
carries nothing finer, and no workorder flags warranty work. Consequently the `EXACT` match grade is
not reachable from this path today, and a warranty workorder cannot request `OEM_WARRANTY`. Both
need an upstream source before they can be wired.

### Frontend Usage Notes

- Treat a prefilled quantity or price as editable and attributed: show the guide's source, revision
  and match grade, and the rate's scope and applied matrix codes, next to the number.
- Show `estimatedLaborHours` as an overlap-adjusted figure. It will be smaller than the visible
  line sum, and the response names the operation codes contributing zero so the difference can be
  explained rather than looking like a bug.
- Variance sign convention: positive `laborVarianceHours` means the shop ran **longer** than
  estimated.
- `listLaborIntelligence` rows with a null `suggestedStandardHours` are thin samples, not errors —
  render the measured medians and omit the recommendation.

### ADR Constraints

- ADR-0044 amendment 2026-09-02: `CatalogLaborTimeClientImpl` is the only file that may call
  pos-catalog synchronously. Degraded path is the `ext_catalog_service` replica's vehicle-agnostic
  default hours, then a blank prefill.
- ADR-0044 amendment 2026-09-07: `PriceLaborRateClientImpl` is the only file that may call
  pos-price synchronously. **No replica fallback** — a stale rate is a wrong number on an invoice.
- A third client reaching for either module fails the platform `DomainWallsTest`.

### Events & Dependencies

- Consumes `catalog.service.updated` at `schemaVersion: 2` into `ext_catalog_service`, which
  supplies both the degraded-mode default hours and the operation category used to scope a rate.
- Emits `WORKORDER_LABOR_INTELLIGENCE_LIST`; the estimate and workorder events are unchanged.
- Permission: `workorder:labor_intelligence:view`, deliberately separate from
  `workorder:analytics:view` because the rollup exposes individual technician productivity.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-workorder/src/test/java/com/positivity/workorder/internal/`
  — `service/EstimateItemGuideDefaultingTest` (prefill and both snapshots),
  `service/LaborTimeDefaultingServiceTest`, `service/LaborRateDefaultingServiceTest`,
  `service/LaborIntelligenceServiceTest`, `client/CatalogLaborTimeClientImplTest`.
- Add or update tests covering each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-workorder/openapi.yaml`
- OpenAPI source revision: `83164e57` (branch `claude/tier-0-spec-implementation-o5j539`; adds the #1569 estimated-labor behavior and the #1575 Tier 0 labor-intelligence rollup)
- Last verified UTC: `2026-09-07T02:20:00Z`
- Generated API reference: `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/workexec/.business-rules/AGENT_GUIDE.md`
- `domains/workexec/.business-rules/DOMAIN_NOTES.md`
- `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`
