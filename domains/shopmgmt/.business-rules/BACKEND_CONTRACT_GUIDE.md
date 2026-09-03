---
title: Shop Management Backend Contract Guide
domain: shopmgmt
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/shopmgmt/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-shop-manager/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Shop Management Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Shop Management domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-shop-manager/openapi.yaml`
- Generated API reference: `domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/shopmgmt/.business-rules/AGENT_GUIDE.md`

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

- Shop Management behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-137 | `durion#137` | draft | [CAP] Schedule Appointments (Calendar + Queue) |
| CAP-138 | `durion#138` | draft | [CAP] Dispatch and Assign Mechanics & Resources |
| CAP-139 | `durion#139` | draft | [CAP] Timekeeping Integration for Assigned Work |
| CAP-140 | `durion#140` | draft | [CAP] Workorder Execution Context Linking |
| CAP-141 | `durion#141` | draft | [CAP] Roles, Permissions, and Audit Controls |
| CAP-142 | `durion#142` | draft | [CAP] Operational Reporting & Dashboards (Lightweight) |

| CAP-249 | `durion#249` | draft | [CAP] Appointment Scheduling & Assignment (Shopmgr Coordination) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete bay | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` | Refer to generated API reference for payload details |
| Delete mobile unit | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` | Refer to generated API reference for payload details |
| Load appointment | `getAppointment` | GET | `/v1/shop-manager/appointments/{appointmentId}` | Refer to generated API reference for payload details |
| Get bays | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/bays` | Refer to generated API reference for payload details |
| Get mobile units | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/mobileUnit` | Refer to generated API reference for payload details |
| Get bays | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/bays/{bayId}` | Refer to generated API reference for payload details |
| Get mobile units | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` | Refer to generated API reference for payload details |
| View schedules | `viewSchedule` | GET | `/v1/schedules/view` | Refer to generated API reference for payload details |
| Get shop service details | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/services/{serviceId}/details` | Refer to generated API reference for payload details |
| Get technician's person details | `getTechnicianPerson` | GET | `/v1/shop-manager/{locationId}/technicians/{personId}/person` | Refer to generated API reference for payload details |
| View workorder operational context | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/workorders/{workorderId}/operationalContext` | Refer to generated API reference for payload details |
| Create appointment | `createAppointment` | POST | `/v1/shop-manager/appointments` | Refer to generated API reference for payload details |
| Reschedule appointment | `rescheduleAppointment` | PUT | `http://localhost:8080/v1/appointments/{appointmentId}/reschedule` | Refer to generated API reference for payload details |
| Cancel appointment | `cancelAppointment` | DELETE | `http://localhost:8080/v1/appointments/{appointmentId}/cancel` | Refer to generated API reference for payload details |
| Create assignment | `createAssignment` | POST | `http://localhost:8080/v1/appointments/{appointmentId}/assignments` | Refer to generated API reference for payload details |
| List assignments | `listAssignments` | GET | `http://localhost:8080/v1/appointments/{appointmentId}/assignments` | Refer to generated API reference for payload details |
| Conflict override (scheduling) | `executeOverride` | POST | `http://localhost:8080/v1/appointments/{appointmentId}/conflict-override` | Refer to generated API reference for payload details |
| Create bay | *(planned — not in pos-shop-manager OpenAPI)* | POST | `/v1/shop-manager/{locationId}/bays` | Refer to generated API reference for payload details |
| Create mobile unit | *(planned — not in pos-shop-manager OpenAPI)* | POST | `/v1/shop-manager/{locationId}/mobileUnit` | Refer to generated API reference for payload details |
| Manage bays | *(planned — not in pos-shop-manager OpenAPI)* | PUT | `/v1/shop-manager/bays` | Refer to generated API reference for payload details |
| Load the shop dashboard | `getShopDashboard` | GET | `/v1/shop-dashboard` | One call returns bays, mobile units, their workorders and all open work; see CAP-142 |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-137: [CAP] Schedule Appointments (Calendar + Queue)

### Capability Metadata

- Capability ID: CAP-137
- Parent Issue: https://github.com/louisburroughs/durion/issues/137
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Delete bay | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` |
| Delete mobile unit | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |
| Load appointment | `getAppointment` | GET | `/v1/shop-manager/appointments/{appointmentId}` |

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

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-249: [CAP] Appointment Scheduling & Assignment (Shopmgr Coordination)

### Capability Metadata

- Capability ID: CAP-249
- Parent Issue: https://github.com/louisburroughs/durion/issues/249
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create appointment | `createAppointment` | POST | `http://localhost:8080/v1/appointments` |
| Get appointment | `getAppointment` | GET | `http://localhost:8080/v1/appointments/{appointmentId}` |
| Reschedule appointment | `rescheduleAppointment` | PUT | `http://localhost:8080/v1/appointments/{appointmentId}/reschedule` |
| Cancel appointment | `cancelAppointment` | DELETE | `http://localhost:8080/v1/appointments/{appointmentId}/cancel` |
| Create assignment | `createAssignment` | POST | `http://localhost:8080/v1/appointments/{appointmentId}/assignments` |
| List assignments | `listAssignments` | GET | `http://localhost:8080/v1/appointments/{appointmentId}/assignments` |
| Conflict override (scheduling) | `executeOverride` | POST | `http://localhost:8080/v1/appointments/{appointmentId}/conflict-override` |

### Behavioral Assertions

- **Authoritative assignment source:** `shopmgmt` is the authoritative source of assignment data (assignments persisted in `pos-shop-manager` are the source of truth for downstream consumers).
- **Reschedule rules:** Rescheduling an appointment requires a `reason` field. The service enforces hard vs soft conflict semantics; hard conflicts are rejected unless the caller provides the conflict override permission. On successful reschedule the service emits an `AppointmentRescheduled` event to notify `WorkExec` and other subscribers.
- **Conflict override:** Conflict overrides are only permitted when the caller has `OVERRIDE_SCHEDULING_CONFLICT` authority and must be recorded via the `conflict-override` endpoint including `overrideReason` and audit metadata.
- **Appointment provenance & eligibility:** When an appointment is created from an `Estimate` or `WorkOrder` the service must validate eligibility (customer/vehicle linkage, required serviceRequestIds, shop capacity). On successful creation from an estimate the estimate status must transition to `SCHEDULED` and `WorkExec` must be notified via the appropriate event (`AppointmentCreatedFromEstimate` / `AppointmentCreatedFromWorkOrder`).
- **Permissions:** The service enforces permissions for the UX stories:
  - `VIEW_ASSIGNMENTS` — required to list or retrieve assignments (story #10).
  - `RESCHEDULE_APPOINTMENT` and `OVERRIDE_SCHEDULING_CONFLICT` — required for rescheduling and conflict override actions (story #11).
  - `CREATE_APPOINTMENT` — required to create new appointments (story #12).
- **Deterministic failures & correlation:** All mutation failures must return deterministic error codes (400/403/409) with `X-Correlation-Id` propagated for tracing.

### Events & Dependencies

- Outgoing events produced by this capability: `AppointmentCreatedFromEstimate`, `AppointmentCreatedFromWorkOrder`, `AppointmentRescheduled`, `ASSIGNMENT_UPDATED`.
- Downstream consumers: `WorkExec` (scheduling/execution), CRM (customer/vehicle snapshots), Billing (when appointments trigger invoice flows).

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/.../cap249/`
- Test suites should cover permission enforcement, conflict semantics (hard vs soft), event emission on create/reschedule, and estimate→appointment status transitions.

## CAP-138: [CAP] Dispatch and Assign Mechanics & Resources

### Capability Metadata

- Capability ID: CAP-138
- Parent Issue: https://github.com/louisburroughs/durion/issues/138
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get bays | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/bays` |
| Get mobile units | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/mobileUnit` |
| Get bays | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/bays/{bayId}` |

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

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-139: [CAP] Timekeeping Integration for Assigned Work

### Capability Metadata

- Capability ID: CAP-139
- Parent Issue: https://github.com/louisburroughs/durion/issues/139
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get mobile units | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |
| View schedules | `viewSchedule` | GET | `/v1/schedules/view` |
| Get shop service details | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/services/{serviceId}/details` |

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

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-140: [CAP] Workorder Execution Context Linking

### Capability Metadata

- Capability ID: CAP-140
- Parent Issue: https://github.com/louisburroughs/durion/issues/140
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get technician's person details | `getTechnicianPerson` | GET | `/v1/shop-manager/{locationId}/technicians/{personId}/person` |
| View workorder operational context | *(planned — not in pos-shop-manager OpenAPI)* | GET | `/v1/shop-manager/{locationId}/workorders/{workorderId}/operationalContext` |
| Create appointment | `createAppointment` | POST | `/v1/shop-manager/appointments` |

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

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-141: [CAP] Roles, Permissions, and Audit Controls

### Capability Metadata

- Capability ID: CAP-141
- Parent Issue: https://github.com/louisburroughs/durion/issues/141
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create bay | *(planned — not in pos-shop-manager OpenAPI)* | POST | `/v1/shop-manager/{locationId}/bays` |
| Create mobile unit | *(planned — not in pos-shop-manager OpenAPI)* | POST | `/v1/shop-manager/{locationId}/mobileUnit` |
| Manage bays | *(planned — not in pos-shop-manager OpenAPI)* | PUT | `/v1/shop-manager/bays` |

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

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-142: [CAP] Operational Reporting & Dashboards (Lightweight)

### Capability Metadata

- Capability ID: CAP-142
- Parent Issue: https://github.com/louisburroughs/durion/issues/142
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Manage mobile units | *(planned — not in pos-shop-manager OpenAPI)* | PUT | `/v1/shop-manager/mobileUnit` |
| Delete bay | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` |
| Delete mobile unit | *(planned — not in pos-shop-manager OpenAPI)* | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |
| Load the shop dashboard | `getShopDashboard` | GET | `/v1/shop-dashboard` |

### Shop Dashboard Aggregate Read (`GET /v1/shop-dashboard`, backend #1658)

**Module and path.** The endpoint lives in `pos-shop-manager` (package
`com.positivity.shopmanager.internal`) on a bare-noun path off `/v1`, matching the module's other
controllers. There is no `pos-shopmgmt` module and no `/v1/shopmgmt/...` prefix.

**Request.** `locationId` (UUID, required) and `date` (optional, date-only `yyyy-MM-dd` per
ADR-0038). `date` defaults to the location's local today, resolved through the shop's recorded
timezone and falling back to UTC when it is absent or unrecognised.

**Response shape.**

| Field | Meaning |
| --- | --- |
| `locationId`, `date` | echo of what the board was rendered for |
| `units[]` | every bay and mobile unit at the location, as a discriminated union tagged `unitType: BAY \| MOBILE_UNIT` |
| `units[].assignment` | the workorder on that unit, or an explicit `null` for an idle unit — never omitted |
| `openWorkorders[]` | every open workorder at the location, assigned or not |
| `openWorkordersTruncated` | `true` when the 200-row cap was reached |

Each workorder row carries `workorderId`, `workorderNumber`, `status`, `unitId` / `unitName` /
`unitType` (all `null` when unassigned), a structured `vehicle` (`vehicleId`, `vin`, `year`,
`make`, `model`), `mechanicName` and `mechanicNames[]`, and `promisedAt`.

**Two lists, two questions.** `units[]` is the physical roster and is the *only* thing `date`
scopes. `openWorkorders[]` is a **superset** of the assignments in `units[]` — it also contains work
that is on no unit at all, such as a DRAFT job before dispatch — and is never date-scoped.

**Ordering and cap.** `openWorkorders[]` is server-sorted: unassigned first, then by status band
(blocked → queued → active → ready), then `promisedAt` ascending with nulls last, then
`workorderNumber`. The sort is expressed in SQL and the 200-row cap is applied *after* it, so the
rows returned are the first page of the promised order rather than an arbitrary subset.

**Occupancy is a read-side consequence, not stored state.** A unit is occupied by the open
workorder assigned to it whose scheduled date is the requested day or absent. Because the candidate
set is the open set, a `COMPLETED` or `CANCELLED` workorder frees its unit with no write and no
schema change in Workorder Execution; a `READY_FOR_PICKUP` workorder still occupies its unit,
because the vehicle has not left.

**"Open" is derived, not copied.** `pos-workorder` defines `getOpenStatuses()` as the complement of
its terminal set. Shop Management cannot import that enum across the ADR-0044 wall, so it mirrors
the *derivation* — everything that is not `COMPLETED` or `CANCELLED` — rather than copying the seven
open names. A status added upstream therefore appears as open by default instead of silently
vanishing from the board.

**"Unit" is not an entity.** The bay ∪ mobile-unit union is synthesized per request and tagged.
Shop Management persists no unified Unit model; the two halves belong to the location domain.

**Errors** follow ADR-0017 in the standard `ApiError` envelope: `400` for a malformed `locationId`
or `date`, `403` without `shopmgmt:dashboard:view`, `404` for an unknown location.

**Permission.** `shopmgmt:dashboard:view` (`domain: shop`, `serviceName: pos-shop-manager`),
registered through the module's existing `PermissionRegistration`. It is a new read-only view and
supersedes none of the deprecated `shop:*` permissions, which cover mutations.

**Consistency.** Every fact behind the response is a local replica of another domain's events, with
the fail-open, retry-with-backoff semantics already used for the ShopMgmt↔WorkExec status sync. The
board is not expected to reflect an assignment change with zero latency, and the OpenAPI description
says so.

### Widened Workorder Status Event Payload (#1658)

The cross-domain workorder fact — `workorder.workorder.updated` on `workorder.events.v1`, payload
`WorkorderUpdatedV1` in `pos-domain-events` — was widened **additively within schema v1** with an
assignment block: `locationId`, `resourceId`, `resourceType` (`BAY` / `MOBILE_UNIT`, from the
discriminator added in #1656), `mechanicIds`, `promisedAt` and `scheduledDate`. `workorderNumber`,
`status` and `vehicleId` were already present.

- `mechanicIds` is a **list**: a workorder may carry more than one technician, and the first element
  is not privileged.
- `promisedAt` is **null in every fact published today** — the `Workorder` aggregate has no
  promise-time field. The slot exists so the contract does not change when it grows one; until then
  the `promisedAt` tier of the dashboard sort is inert and ordering falls through to
  `workorderNumber`.
- This is payload-only. No new endpoint, no change to `WorkorderStatus` semantics or transitions.

`pos-shop-manager` consumes the widened fact into an `ext_workorder` replica keyed by workorder id
(migration V6), extending its existing four-consumer replica pattern. The replica is the only thing
the dashboard queries — there is no live call into `pos-workorder`. Terminal rows are kept, not
deleted, which is what makes the closed-workorder-frees-its-unit rule pure read-side logic. The
consumer also raises the module's existing in-process `WorkorderStatusChangedEvent` (itself widened
to the same shape) — but only when the applied fact actually moves the status, so the appointment
status timeline does not gain a duplicate entry per unrelated workorder edit.

### Bay / Mobile-Unit Topology Sourcing Decision (#1658 AC11)

**Decision: event-sourced replica.** `pos-shop-manager` maintains `ext_bay` and `ext_mobile_unit`
(migration V6) fed by `location.bay.*` and `location.mobile-unit.*` on `location.events.v1`.

Rejected alternative: a synchronous `RestClient` into `pos-location`. It would work today and needs
no tables, and bay topology changes rarely, so the staleness argument for events is weak here. It
was rejected because:

1. It is a domain→domain synchronous call, forbidden by ADR-0044 R1. No standing grant covers it —
   the only synchronous exceptions on the books are the `SupplierStockService` grant (ADR-0026
   D1–D5) and pos-warranty's scoped v1 exception — so it would require minting a **new** recorded
   ADR-0044 exception on the pos-warranty precedent (backend#786).
2. `pos-workorder` answered the identical question the opposite way one story earlier (#1656). Two
   modules replicating the same aggregates in the same shape is one upstream publisher away from
   done; one replicating and one calling is a permanent inconsistency.
3. This module already runs four replica consumers over this exact contract, so the replica is the
   cheap option here and the live call the expensive one.

**Honest consequence, recorded deliberately.** `pos-location`'s `LocationFactPublisher` does not
publish bay or mobile-unit facts yet — it emits `location.location.*` and
`location.storage-location.updated` only. So `ext_bay` and `ext_mobile_unit` start empty and
**`units[]` is empty in production today**, while `openWorkorders[]` is fully populated once
workorder facts flow. A live read would have returned units today at the architectural price above.
Adding the bay and mobile-unit publishers to `pos-location` is the cross-repo follow-up that closes
the gap for this module and `pos-workorder` at once. The consumer-side fact contracts are declared
locally in `com.positivity.shopmanager.internal.dto.location` in the shape they should take in
`pos-domain-events`, so the move is a package change and nothing more.

### Behavioral Assertions

- Requests must satisfy domain validation rules before state change.
- Successful mutations must produce deterministic persisted outcomes.
- Failure responses must be explicit and actionable for callers.
- `GET /v1/shop-dashboard` must answer in a query count independent of the number of units or
  workorders at the location — one batched read per source, never one per unit or per workorder.

### Frontend Usage Notes

- Use operation IDs above as the stable API integration keys for UI actions.
- Read request/response payload shapes from generated API reference, not this guide.
- Surface validation and authorization failures directly to users with trace context.
- Render `units[]` and `openWorkorders[]` as two panels, not one filtered list: the second is a
  superset of the first and is not date-scoped.
- Treat `units[].assignment === null` as "free unit", which is a state worth showing, not a gap.

### ADR Constraints

- Follow domain decision constraints in `AGENT_GUIDE.md` and repository ADRs.
- ADR-0044 R1/R3/R6: no synchronous domain-to-domain reads; replicas are written only by their event
  consumers; one owner per fact.
- ADR-0017 error contract, ADR-0038 date-only strings, ADR-0042 operation annotation depth,
  ADR-0025 permission registration, ADR-0013/ADR-0027 UUID identifiers.

### Events & Dependencies

- Respect published API/event contracts for all upstream and downstream dependencies.
- Preserve traceability when integrating across services or asynchronous workflows.
- Consumes: `workorder.workorder.updated` (widened, see above); `location.bay.updated` /
  `location.bay.deleted` / `location.mobile-unit.updated` / `location.mobile-unit.deleted`
  (**not yet published by pos-location**); `vehicle.vehicle.updated`;
  `people-contact.person.updated`; `people` staffing assignment facts.
- Emits: audit event `SHOPMGR_SHOP_DASHBOARD_VIEW` (read-only; no domain events).

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-shop-manager/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.
- `#1658`: `ShopDashboardServiceTest` (ACs 1, 3–8, 12, 13 including the bounded-query-count guard),
  `ShopDashboardDateResolutionTest` (AC2), `ShopDashboardControllerTest` (AC12, AC14),
  `ShopDashboardOpenApiContractTest` (AC15), `WorkorderEventsListenerTest` and
  `ReplicaAndManifestListenerContractTest` (ACs 9, 10), and
  `pos-workorder`'s `WorkorderFactPublisherTest` (AC9, producer side).

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/shopmgmt/.business-rules/AGENT_GUIDE.md`
- `domains/shopmgmt/.business-rules/DOMAIN_NOTES.md`
- `domains/shopmgmt/.business-rules/BACKEND_API_REFERENCE.generated.md`
