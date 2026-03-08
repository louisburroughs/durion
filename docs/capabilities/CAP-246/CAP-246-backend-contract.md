---
title: "CAP-246 Backend Contract Document"
capability_id: CAP-246
capability_name: "POS Sales Order & Cart (Quote-to-Cash Entry Point)"
domain: order
module: pos-order
doc_type: capability_backend_contract
contract_status: draft
parent_issue: https://github.com/louisburroughs/durion/issues/246
backend_issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/19
  - https://github.com/louisburroughs/durion-positivity-backend/issues/20
  - https://github.com/louisburroughs/durion-positivity-backend/issues/21
openapi_source: durion-positivity-backend/pos-order/openapi.yaml
openapi_commit: ca7fadc3
created_utc: 2026-03-07T00:00:00Z
---

# CAP-246 Backend Contract Document

## Overview

This document provides the implementation-level backend contract for **CAP-246: POS Sales Order & Cart (Quote-to-Cash Entry Point)**.
It is the companion to the curated guide at `domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md` and is scoped exclusively to the three backend stories in this capability.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-order/openapi.yaml` (commit `ca7fadc3`)
- Contract Guide: `domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Generated API Reference: `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`

---

## Implementation Links

| Story | Issue | Title | Status |
| --- | --- | --- | --- |
| #21 | [durion-positivity-backend#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21) | Order: Create Sales Order Cart and Add Items | backlog |
| #20 | [durion-positivity-backend#20](https://github.com/louisburroughs/durion-positivity-backend/issues/20) | Order: Apply Price Override with Permission and Reason | backlog |
| #19 | [durion-positivity-backend#19](https://github.com/louisburroughs/durion-positivity-backend/issues/19) | Order: Cancel Order with Controlled Void Logic | backlog |

---

## ADR Compliance

| ADR | Applies To | Compliance Requirement |
| --- | --- | --- |
| [ADR-0017](../../adr/0017-api-controller-http-response-codes.adr.md) | All three stories | Use canonical HTTP status matrix: `201` for creation, `200` for successful mutations, `202` for accepted sagas, `400`/`403`/`404`/`409` for errors. Never use `409` for domain-policy violations where `422` is more appropriate. |
| [ADR-0018](../../adr/0018-audit-actor-fields-from-security-context.adr.md) | #19, #20 | `requestedByUserId`, `approvedByUserId`, `rejectedByUserId`, and cancellation actor fields MUST be derived from `SecurityContextHelper`. Never accept actor identity from request payloads. Fallback to `"system"` when no principal is present. |
| [ADR-0026](../../adr/0026-service-contract-boundary-policy.adr.md) | All three stories | Service interfaces exposed in `com.positivity.order.service`; all implementations in `com.positivity.order.internal.service`. ArchUnit tests must enforce this boundary per module policy. |

---

## Story #20 — Apply Price Override with Permission and Reason

### Scope

Authorization guardrails, config-driven discount thresholds, commission impact tracking, and the approval workflow lifecycle.

### OpenAPI Coverage

All six endpoints for this story are present in `openapi.yaml` (commit `ca7fadc3`):

| operationId | Method | Gateway Path |
| --- | --- | --- |
| `applyPriceOverride` | POST | `http://localhost:8080/v1/orders/price-overrides` |
| `getOverridesByOrder` | GET | `http://localhost:8080/v1/orders/price-overrides` |
| `getPendingApprovals` | GET | `http://localhost:8080/v1/orders/price-overrides/pending` |
| `getOverride` | GET | `http://localhost:8080/v1/orders/price-overrides/{overrideId}` |
| `approvePriceOverride` | POST | `http://localhost:8080/v1/orders/price-overrides/{overrideId}/approve` |
| `rejectPriceOverride` | POST | `http://localhost:8080/v1/orders/price-overrides/{overrideId}/reject` |

Service direct URL: `http://localhost:8086/v1/orders/price-overrides[...]`

### Behavioral Contract

1. **ReasonCode enforcement**: `reasonCode` is required (`CUSTOMER_LOYALTY`, `PRICE_MATCH`, `PROMOTIONAL_PRICING`, `PRICING_ERROR_CORRECTION`, `VOLUME_DISCOUNT`, `GOODWILL_ADJUSTMENT`, `MANAGER_DISCRETION`, `OTHER`). Free-form text goes in the optional `justification` field.

2. **Threshold-based approval gate**: When the override discount exceeds the configured threshold for the requester's authority level, the service persists `status=PENDING_APPROVAL` with `requiresApproval=true` and returns `201 Created`. The price is not yet changed on the order line.

3. **Self-approval prohibition**: The approver's identity (from `SecurityContextHelper`) must differ from the requester's identity. The service enforces this in the service layer, not the controller.

4. **Rejection requirement**: `RejectPriceOverrideRequest.reason` is required (non-empty). Override transitions to `REJECTED`; original price is not modified.

5. **Status lifecycle**:
   ```
   PENDING_APPROVAL ──approve──► APPROVED ──applied──► APPLIED
   PENDING_APPROVAL ──reject──► REJECTED
   * ──cancel──► CANCELLED  (any non-terminal state)
   ```

6. **Audit actor fields** (ADR-0018): `requestedByUserId`, `approvedByUserId`, `rejectedByUserId` are set by service logic from `SecurityContextHelper.getCurrentUserId()`. These fields must not be accepted from request payloads.

7. **Commission impact**: When the overridden line item is commission-eligible, the service records a commission impact flag on the `PriceOverride` entity. This flag is surfaced in `PriceOverrideView` for downstream reward processing.

8. **Idempotency**: Submitting an identical override request for the same `orderId`/`orderLineId` while a `PENDING_APPROVAL` override exists should return `409 Conflict` rather than creating a duplicate pending override.

### Required Authorities

| Endpoint | Required Authority |
| --- | --- |
| `POST /v1/orders/price-overrides` | `ORDER_PRICE_OVERRIDE_CREATE` |
| `POST /v1/orders/price-overrides/{overrideId}/approve` | `ORDER_PRICE_OVERRIDE_APPROVE` |
| `POST /v1/orders/price-overrides/{overrideId}/reject` | `ORDER_PRICE_OVERRIDE_APPROVE` |
| `GET /v1/orders/price-overrides*` | `ORDER_PRICE_OVERRIDE_READ` (or authenticated user with relevant order association) |

### Response Code Contract (ADR-0017)

| Scenario | Code |
| --- | --- |
| Override created (may be pending approval) | `201 Created` |
| Override approved or rejected | `200 OK` |
| Caller missing required authority | `403 Forbidden` |
| Malformed or missing required field | `400 Bad Request` |
| Override not found | `404 Not Found` |
| Override in wrong state for operation | `409 Conflict` |

### Provider Contract Tests

- Test file: `pos-order/src/test/java/com/positivity/order/...`
- Must cover: threshold triggers `PENDING_APPROVAL`, approval/rejection lifecycle transitions, self-approval rejection, `reasonCode` validation, actor field derivation.

---

## Story #21 — Create Sales Order Cart and Add Items

### Scope

Foundational order/cart aggregate: `DRAFT` cart creation, line item add/update/remove, inventory `WARN_AND_BACKORDER` policy, and guest-to-authenticated cart merge semantics.

### OpenAPI Coverage

**Not yet present in `openapi.yaml`.** OpenAPI and generated API reference must be updated after issue #21 is implemented. The paths below are the expected contracts; confirm at implementation.

| Expected operationId | Method | Expected Gateway Path |
| --- | --- | --- |
| TODO | POST | `http://localhost:8080/v1/orders/carts` |
| TODO | GET | `http://localhost:8080/v1/orders/carts/{cartId}` |
| TODO | POST | `http://localhost:8080/v1/orders/carts/{cartId}/items` |
| TODO | PUT | `http://localhost:8080/v1/orders/carts/{cartId}/items/{lineId}` |
| TODO | DELETE | `http://localhost:8080/v1/orders/carts/{cartId}/items/{lineId}` |
| TODO | POST | `http://localhost:8080/v1/orders/carts/{cartId}/merge` |

### Behavioral Contract

1. **DRAFT state ownership**: A newly created cart starts in `DRAFT` and is owned by the requesting session/user. No downstream billing events are emitted in `DRAFT`.

2. **Line mutation guard**: Add, update, and remove operations are only permitted on carts in `DRAFT` state. Attempts on non-`DRAFT` carts return `409 Conflict` (ADR-0017).

3. **WARN_AND_BACKORDER inventory policy**: When a requested quantity exceeds available inventory, the line is accepted. The response includes a `inventoryStatus` field of `LOW_INVENTORY` or `BACKORDERED`. The request is never rejected solely because of inventory shortfall under this policy.

4. **Cart merge semantics**: When a guest cart is merged into an authenticated cart:
   - Lines for the same SKU are consolidated (quantities summed).
   - The guest cart is retired (status `MERGED`) after successful consolidation.
   - Merge is idempotent: re-merging a `MERGED` cart returns `409 Conflict`.

5. **Cart ID assignment**: Cart aggregate ID is UUIDv7 and is assigned server-side at creation. Clients must not supply a cart ID in the creation request body.

6. **Cart confirmation**: Transitioning out of `DRAFT` is an explicit action (separate endpoint or status update). Draft-state line mutations must not trigger billing, pricing recalculation, or workorder creation.

7. **Audit actor** (ADR-0018): Cart `createdBy` and `updatedBy` are derived from `SecurityContextHelper`; not accepted from request payloads.

### Required Authorities (TODO: confirm at implementation)

| Endpoint | Expected Authority |
| --- | --- |
| `POST /v1/orders/carts` | `ORDER_CART_CREATE` |
| Modify cart items | `ORDER_CART_WRITE` |
| Merge carts | `ORDER_CART_MERGE` |

### Response Code Contract (ADR-0017)

| Scenario | Code |
| --- | --- |
| Cart created | `201 Created` |
| Line added or updated | `200 OK` |
| Line removed | `204 No Content` |
| Cart not in DRAFT state | `409 Conflict` |
| Cart or line not found | `404 Not Found` |
| Caller lacks authority | `403 Forbidden` |
| Malformed request | `400 Bad Request` |

---

## Story #19 — Cancel Order with Controlled Void Logic

### Scope

POS-orchestrated cancellation saga: Workexec termination then billing reversal, persisted saga states, and alert event emission on partial failure.

### OpenAPI Coverage

**Not yet present in `openapi.yaml`.** OpenAPI and generated API reference must be updated after issue #19 is implemented.

| Expected operationId | Method | Expected Gateway Path |
| --- | --- | --- |
| TODO | POST | `http://localhost:8080/v1/orders/{orderId}/cancel` |
| TODO | GET | `http://localhost:8080/v1/orders/{orderId}/cancel/status` |

### Behavioral Contract

1. **Saga orchestration order**: The cancellation saga executes in strict order:
   1. Emit `CANCEL_REQUESTED` saga state.
   2. Call Workexec termination API. On success, advance to `WORKEXEC_CANCELLED`.
   3. Call billing reversal API. On success, advance to `BILLING_REVERSED`, then `CANCELLED`.

2. **Workexec failure semantics**: If the Workexec cancellation fails, the saga halts in `CANCEL_REQUESTED`. A definitive error response is returned to the caller. Billing reversal is **not** attempted.

3. **Billing failure semantics**: If billing reversal fails after Workexec succeeds, the saga transitions to `BILLING_REVERSAL_FAILED`. An alert event is emitted to the event bus. The system must not silently leave the order in an inconsistent state; the `BILLING_REVERSAL_FAILED` state is observable and queryable.

4. **Terminal state guard**: Cancellation is disallowed on orders already in `CANCELLED` or `COMPLETED` states. The service returns `409 Conflict` (ADR-0017).

5. **Saga state persistence**: All saga state transitions are persisted before the corresponding external call to support restart-on-failure scenarios.

6. **Audit actor** (ADR-0018): The cancellation actor (`cancelledByUserId`) is derived from `SecurityContextHelper`; not accepted from the request payload.

7. **Cancellation reason**: A reason field is recommended (TODO: confirm whether mandatory at implementation). The reason must be recorded on the order audit trail.

### Persisted Saga States

```
CANCEL_REQUESTED
  └── [Workexec OK] → WORKEXEC_CANCELLED
        └── [Billing OK] → BILLING_REVERSED → CANCELLED
        └── [Billing FAIL] → BILLING_REVERSAL_FAILED  (alert event emitted)
  └── [Workexec FAIL] → halts in CANCEL_REQUESTED  (error returned to caller)
```

### Required Authorities (TODO: confirm at implementation)

| Endpoint | Expected Authority |
| --- | --- |
| `POST /v1/orders/{orderId}/cancel` | `ORDER_CANCEL` |
| `GET /v1/orders/{orderId}/cancel/status` | `ORDER_CANCEL_READ` |

### Response Code Contract (ADR-0017)

| Scenario | Code |
| --- | --- |
| Cancellation saga initiated | `202 Accepted` |
| Order already in terminal state | `409 Conflict` |
| Order not found | `404 Not Found` |
| Caller lacks required authority | `403 Forbidden` |
| Malformed request | `400 Bad Request` |

### Events Emitted

| Event | Trigger | Consumer |
| --- | --- | --- |
| `ORDER_CANCEL_REQUESTED` | Saga start | Workexec service |
| `BILLING_REVERSAL_FAILED` | Saga partial failure | Ops / alert routing |

---

## Contract Update Checklist

When implementing any of the three stories, perform these update steps:

- [ ] Regenerate `openapi.yaml` for `pos-order` after implementation
- [ ] Re-run `scripts/generate-openapi.sh pos-order` and commit updated spec
- [ ] Update `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`
- [ ] Update `openapi_commit` in `BACKEND_CONTRACT_GUIDE.md` front matter
- [ ] Replace `TODO` operationId entries in `BACKEND_CONTRACT_GUIDE.md` Frontend API Lookup table
- [ ] Add provider contract tests for each behavioral assertion
- [ ] Update capability index status from `draft` to `stable-for-ui` once all stories are implemented and tested

---

## References

- `domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`
- `durion-positivity-backend/pos-order/openapi.yaml`
- `docs/adr/0017-api-controller-http-response-codes.adr.md`
- `docs/adr/0018-audit-actor-fields-from-security-context.adr.md`
- `docs/adr/0026-service-contract-boundary-policy.adr.md`
- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
