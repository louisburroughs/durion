---
title: Inventory Management Backend Contract Guide
domain: inventory
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/inventory/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-inventory/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-02-24T14:23:11Z
last_updated: 2026-02-24
api_reference_generated: domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Inventory Management Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Inventory Management domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-inventory/openapi.yaml`
- Generated API reference: `domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/inventory/.business-rules/AGENT_GUIDE.md`

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

- Inventory Management behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-215 | `durion#215` | draft | [CAP] Inventory Ledger & On-hand/ATP |
| CAP-216 | `durion#216` | draft | [CAP] Receiving (PO/ASN/Direct) |
| CAP-217 | `durion#217` | draft | [CAP] Put-away & Replenishment |
| CAP-218 | `durion#218` | draft | [CAP] Picking, Issuing, and Workorder Fulfillment |
| CAP-219 | `durion#219` | draft | [CAP] Cycle Counts & Adjustments |
| CAP-220 | `durion#220` | draft | [CAP] Reservations, Allocations, and Substitutions |
| CAP-221 | `durion#221` | draft | [CAP] Roles, Permissions, and Audit Controls |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Get tasks assigned to an auditor | `getAuditorTasks` | GET | `/api/inventory/cycleCount/auditor/{auditorId}/tasks` | Refer to generated API reference for payload details |
| Get cycle count task details | `getTask` | GET | `/api/inventory/cycleCount/task/{taskId}` | Refer to generated API reference for payload details |
| Get count history for a task | `getCountHistory` | GET | `/api/inventory/cycleCount/task/{taskId}/history` | Refer to generated API reference for payload details |
| List adjustments by status | `listAdjustments` | GET | `/api/v1/inventory/cycleCountAdjustments` | Refer to generated API reference for payload details |
| List pending approvals | `listPendingApprovals` | GET | `/api/v1/inventory/cycleCountAdjustments/pending` | Refer to generated API reference for payload details |
| Count pending approvals | `countPendingApprovals` | GET | `/api/v1/inventory/cycleCountAdjustments/pending/count` | Refer to generated API reference for payload details |
| Get adjustment details | `getAdjustment` | GET | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}` | Refer to generated API reference for payload details |
| Operation | `getPlan` | GET | `/api/v1/inventory/cycleCountPlans/{planId}` | Refer to generated API reference for payload details |
| Query inventory availability | `queryInventoryAvailability` | GET | `/v1/inventory/availability/{productId}` | Refer to generated API reference for payload details |
| Get site default locations | `getSiteDefaultLocations` | GET | `/v1/inventory/sites/{siteId}/defaultLocations` | Refer to generated API reference for payload details |
| Submit a recount for a cycle count task | `submitRecount` | POST | `/api/inventory/cycleCount/recount` | Refer to generated API reference for payload details |
| Submit a count for a cycle count task | `submitCount` | POST | `/api/inventory/cycleCount/submit` | Refer to generated API reference for payload details |
| Deactivate a storage location | `deactivate` | POST | `/api/inventory/locations/{locationId}/deactivate` | Refer to generated API reference for payload details |
| Create cycle count adjustment | `createAdjustment` | POST | `/api/v1/inventory/cycleCountAdjustments` | Refer to generated API reference for payload details |
| Approve adjustment | `approveAdjustment` | POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve` | Refer to generated API reference for payload details |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-215: Inventory Ledger & On-hand/ATP

### Story #36 — Compute On-Hand and Available-to-Promise by Location/Storage

**New endpoint:** `GET /v1/inventory/availability/query`
**Query params:**

- `productSku` (String, required)
- `locationId` (UUID, required)
- `storageLocationId` (UUID, optional)

**Response (200):** `AvailabilityView`

```json
{
  "productSku": "SKU-001",
  "locationId": "uuid-of-location",
  "storageLocationId": null,
  "onHandQuantity": 100,
  "allocatedQuantity": 20,
  "availableToPromiseQuantity": 80,
  "unitOfMeasure": "EACH"
}
```

**Behavioral assertions:**

- ATP = onHandQuantity - allocatedQuantity (NOT onHand - reservations, per ADR-0001)
- When no storageLocationId is given, aggregate all child storage locations under the parent locationId
- When storageLocationId is given, scope computation to that storage location only
- Return 404 if productSku is not found in the ledger
- Return 404 if locationId is not found in the ledger
- Return 200 with all-zero quantities if product/location combination has no ledger entries
- On-hand = net sum of INBOUND minus OUTBOUND ledger entries (entries with affectsOnHand() = true)
- Allocated = net sum of ALLOCATION_CREATED minus ALLOCATION_RELEASED entries

**Test hints:**

- Seed ledger entries via `InventoryLedgerEntryRepository` directly in tests
- Use `GOODS_RECEIPT` event to create on-hand stock
- Use `ALLOCATION_CREATED` event to simulate allocations

### Story #37 — Record Stock Movements in Inventory Ledger

**Endpoints:**

- `POST /v1/inventory/stock-movements` — record RECEIVE, PUT_AWAY, PICK, ISSUE, RETURN, TRANSFER movement
- `POST /v1/inventory/adjustments` — create draft adjustment request (requires INVENTORY_ADJUST_CREATE)
- `POST /v1/inventory/adjustments/{adjustmentId}/approve` — approve and post adjustment ledger entry (requires INVENTORY_ADJUST_APPROVE)

**Movement request body:**

```json
{
  "movementType": "RECEIVE",
  "productSku": "SKU-001",
  "locationId": "uuid-of-location",
  "quantity": 50,
  "unitOfMeasure": "EACH",
  "sourceTransactionId": "optional-reference-id"
}
```

**Adjustment request body:**

```json
{
  "productSku": "SKU-001",
  "locationId": "uuid-of-location",
  "quantity": -5,
  "reasonCode": "DAMAGE",
  "unitOfMeasure": "EACH"
}
```

**Behavioral assertions:**

- RECEIVE movement creates a GOODS_RECEIPT ledger entry (INBOUND)
- TRANSFER movement creates both TRANSFER_OUT (source location) and TRANSFER_IN (destination location)
- Adjustment without reasonCode returns 400
- Approved adjustments post a single ADJUSTMENT_IN or ADJUSTMENT_OUT entry depending on quantity sign
- Negative on-hand resulting from PICK/ISSUE returns 422 INSUFFICIENT_STOCK
- PRODUCT_NOT_FOUND returns 404; LOCATION_NOT_FOUND returns 404
- All entries are immutable once posted (append-only)
- Actor recorded from SecurityContext, not from request body (ADR-0018)

**Permissions:**

- Regular movements: any authenticated user
- Adjustment creation (draft): INVENTORY_ADJUST_CREATE
- Adjustment approval: INVENTORY_ADJUST_APPROVE

**Test hints:**

- Use @WithMockUser(roles={"INVENTORY_ADJUST_CREATE"}) for adjustment creation tests
- Use @WithMockUser(roles={"INVENTORY_ADJUST_APPROVE"}) for approval tests
- Verify ledger entry count increases by exactly 1 (or 2 for TRANSFER) after each movement

## CAP-216: [CAP] Receiving (PO/ASN/Direct)

### Capability Metadata

- Capability ID: CAP-216
- Parent Issue: https://github.com/louisburroughs/durion/issues/216
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| List adjustments by status | `listAdjustments` | GET | `/api/v1/inventory/cycleCountAdjustments` |
| List pending approvals | `listPendingApprovals` | GET | `/api/v1/inventory/cycleCountAdjustments/pending` |
| Count pending approvals | `countPendingApprovals` | GET | `/api/v1/inventory/cycleCountAdjustments/pending/count` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-217: [CAP] Put-away & Replenishment

### Capability Metadata

- Capability ID: CAP-217
- Parent Issue: https://github.com/louisburroughs/durion/issues/217
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get adjustment details | `getAdjustment` | GET | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}` |
| Operation | `getPlan` | GET | `/api/v1/inventory/cycleCountPlans/{planId}` |
| Query inventory availability | `queryInventoryAvailability` | GET | `/v1/inventory/availability/{productId}` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-218: [CAP] Picking, Issuing, and Workorder Fulfillment

### Capability Metadata

- Capability ID: CAP-218
- Parent Issue: https://github.com/louisburroughs/durion/issues/218
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get site default locations | `getSiteDefaultLocations` | GET | `/v1/inventory/sites/{siteId}/defaultLocations` |
| Submit a recount for a cycle count task | `submitRecount` | POST | `/api/inventory/cycleCount/recount` |
| Submit a count for a cycle count task | `submitCount` | POST | `/api/inventory/cycleCount/submit` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-219: [CAP] Cycle Counts & Adjustments

### Capability Metadata

- Capability ID: CAP-219
- Parent Issue: https://github.com/louisburroughs/durion/issues/219
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Deactivate a storage location | `deactivate` | POST | `/api/inventory/locations/{locationId}/deactivate` |
| Create cycle count adjustment | `createAdjustment` | POST | `/api/v1/inventory/cycleCountAdjustments` |
| Approve adjustment | `approveAdjustment` | POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-220: [CAP] Reservations, Allocations, and Substitutions

### Capability Metadata

- Capability ID: CAP-220
- Parent Issue: https://github.com/louisburroughs/durion/issues/220
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Reject adjustment | `rejectAdjustment` | POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject` |
| Operation | `createPlan` | POST | `/api/v1/inventory/cycleCountPlans` |
| Update inventory availability | `updateInventoryAvailability` | POST | `/v1/inventory/availability/{productId}` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## CAP-221: [CAP] Roles, Permissions, and Audit Controls

### Capability Metadata

- Capability ID: CAP-221
- Parent Issue: https://github.com/louisburroughs/durion/issues/221
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Confirm picking list | `confirmPickingList` | POST | `/v1/inventory/pickingLists/{id}/confirm` |
| Replace site default locations | `putSiteDefaultLocations` | PUT | `/v1/inventory/sites/{siteId}/defaultLocations` |
| Get tasks assigned to an auditor | `getAuditorTasks` | GET | `/api/inventory/cycleCount/auditor/{auditorId}/tasks` |

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

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Add or update tests that cover each behavioral assertion above when behavior changes.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-inventory/openapi.yaml`
- OpenAPI source revision: `ca7fadc3`
- Last verified UTC: `2026-02-24T14:23:11Z`
- Generated API reference: `domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/inventory/.business-rules/AGENT_GUIDE.md`
- `domains/inventory/.business-rules/DOMAIN_NOTES.md`
- `domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md`
