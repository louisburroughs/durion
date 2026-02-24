---
title: Work Order Execution Backend Contract Guide
domain: workexec
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/workexec/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-workorder/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
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

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Delete an approval configuration | `deleteConfiguration` | DELETE | `/v1/workexec/approvalConfigurations/{approvalId}` | Refer to generated API reference for payload details |
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

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-002: [CAP] Create and Manage Estimates

### Capability Metadata

- Capability ID: CAP-002
- Parent Issue: https://github.com/louisburroughs/durion/issues/2
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/3
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/4
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/5
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/6
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
- Parent Issue: https://github.com/louisburroughs/durion/issues/7
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

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-workorder/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/workexec/.business-rules/AGENT_GUIDE.md`
- `domains/workexec/.business-rules/DOMAIN_NOTES.md`
- `domains/workexec/.business-rules/BACKEND_API_REFERENCE.generated.md`
