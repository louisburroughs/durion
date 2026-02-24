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

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete bay | `deleteBay` | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` | Refer to generated API reference for payload details |
| Delete mobile unit | `deleteMobileUnit` | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` | Refer to generated API reference for payload details |
| Load appointment | `getAppointment` | GET | `/v1/shop-manager/appointments/{appointmentId}` | Refer to generated API reference for payload details |
| Get bays | `getBays` | GET | `/v1/shop-manager/bays` | Refer to generated API reference for payload details |
| Get mobile units | `getMobileUnits` | GET | `/v1/shop-manager/mobileUnit` | Refer to generated API reference for payload details |
| Get bays | `getBays_1` | GET | `/v1/shop-manager/{locationId}/bays/{bayId}` | Refer to generated API reference for payload details |
| Get mobile units | `getMobileUnits_1` | GET | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` | Refer to generated API reference for payload details |
| View schedules | `viewSchedules` | GET | `/v1/shop-manager/{locationId}/schedules/view` | Refer to generated API reference for payload details |
| Get shop service details | `getShopServiceDetails` | GET | `/v1/shop-manager/{locationId}/services/{serviceId}/details` | Refer to generated API reference for payload details |
| Get technician's person details | `getTechnicianPerson` | GET | `/v1/shop-manager/{locationId}/technicians/{personId}/person` | Refer to generated API reference for payload details |
| View workorder operational context | `viewOpenWorkordersByShop` | GET | `/v1/shop-manager/{locationId}/workorders/{workorderId}/operationalContext` | Refer to generated API reference for payload details |
| Create appointment | `createAppointment` | POST | `/v1/shop-manager/appointments` | Refer to generated API reference for payload details |
| Create bay | `createBay` | POST | `/v1/shop-manager/{locationId}/bays` | Refer to generated API reference for payload details |
| Create mobile unit | `createMobileUnit` | POST | `/v1/shop-manager/{locationId}/mobileUnit` | Refer to generated API reference for payload details |
| Manage bays | `manageBays` | PUT | `/v1/shop-manager/bays` | Refer to generated API reference for payload details |

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
| Delete bay | `deleteBay` | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` |
| Delete mobile unit | `deleteMobileUnit` | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |
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

## CAP-138: [CAP] Dispatch and Assign Mechanics & Resources

### Capability Metadata

- Capability ID: CAP-138
- Parent Issue: https://github.com/louisburroughs/durion/issues/138
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-shop-manager/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get bays | `getBays` | GET | `/v1/shop-manager/bays` |
| Get mobile units | `getMobileUnits` | GET | `/v1/shop-manager/mobileUnit` |
| Get bays | `getBays_1` | GET | `/v1/shop-manager/{locationId}/bays/{bayId}` |

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
| Get mobile units | `getMobileUnits_1` | GET | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |
| View schedules | `viewSchedules` | GET | `/v1/shop-manager/{locationId}/schedules/view` |
| Get shop service details | `getShopServiceDetails` | GET | `/v1/shop-manager/{locationId}/services/{serviceId}/details` |

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
| View workorder operational context | `viewOpenWorkordersByShop` | GET | `/v1/shop-manager/{locationId}/workorders/{workorderId}/operationalContext` |
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
| Create bay | `createBay` | POST | `/v1/shop-manager/{locationId}/bays` |
| Create mobile unit | `createMobileUnit` | POST | `/v1/shop-manager/{locationId}/mobileUnit` |
| Manage bays | `manageBays` | PUT | `/v1/shop-manager/bays` |

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
| Manage mobile units | `manageMobileUnits` | PUT | `/v1/shop-manager/mobileUnit` |
| Delete bay | `deleteBay` | DELETE | `/v1/shop-manager/{locationId}/bays/{bayId}` |
| Delete mobile unit | `deleteMobileUnit` | DELETE | `/v1/shop-manager/{locationId}/mobileUnit/{bayId}` |

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
