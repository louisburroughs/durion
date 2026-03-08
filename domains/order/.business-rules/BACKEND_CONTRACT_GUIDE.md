---
title: Order Management Backend Contract Guide
domain: order
doc_type: backend_contract
contract_status: draft
owner_repo: louisburroughs/durion
guide_path: domains/order/.business-rules/BACKEND_CONTRACT_GUIDE.md
openapi_source: durion-positivity-backend/pos-order/openapi.yaml
openapi_commit: ca7fadc3
last_verified_utc: 2026-03-07T00:00:00Z
last_updated: 2026-03-07
api_reference_generated: domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md
traceability:
  capability_manifest_root: docs/capabilities
---

# Order Management Backend Contract Guide

## Purpose & Scope

This is the curated contract guide for Order Management domain behavior.

- Use this guide for capability intent, domain invariants, dependency boundaries, and UI-to-API mapping.
- Use OpenAPI and generated API reference for request/response schemas and full endpoint detail.

Authoritative references:

- OpenAPI: `durion-positivity-backend/pos-order/openapi.yaml`
- Generated API reference: `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`
- Global standards: `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- Domain decisions: `domains/order/.business-rules/AGENT_GUIDE.md`

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

- Order Management behavioral rules are authoritative in backend services, not inferred from frontend state.
- Mutating operations require explicit permission enforcement and auditable outcomes.
- Error responses and correlation headers must be deterministic and traceable across requests.
- Cross-domain interactions must go through API/event contracts, not direct data coupling.

## Capability Index

| Capability | Parent Issue | Contract Status | Primary Scope |
| --- | --- | --- | --- |
| CAP-246 | [#246](https://github.com/louisburroughs/durion/issues/246) | draft | POS Sales Order & Cart (Quote-to-Cash Entry Point) |

## Frontend API Lookup

| UI Task | operationId | Method | Path | Notes |
| --- | --- | --- | --- | --- |
| Get price overrides | `getOverridesByOrder` | GET | `/v1/orders/price-overrides` | Refer to generated API reference for payload details |
| Get pending approvals | `getPendingApprovals` | GET | `/v1/orders/price-overrides/pending` | Refer to generated API reference for payload details |
| Get price override | `getOverride` | GET | `/v1/orders/price-overrides/{overrideId}` | Refer to generated API reference for payload details |
| Apply price override | `applyPriceOverride` | POST | `/v1/orders/price-overrides` | Refer to generated API reference for payload details |
| Approve price override | `approvePriceOverride` | POST | `/v1/orders/price-overrides/{overrideId}/approve` | Refer to generated API reference for payload details |
| Reject price override | `rejectPriceOverride` | POST | `/v1/orders/price-overrides/{overrideId}/reject` | Refer to generated API reference for payload details |
| Create sales order cart | TODO — pending [#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21) | POST | `/v1/orders/carts` | operationId assigned at implementation; check OpenAPI after issue #21 merges |
| Add item to cart | TODO — pending [#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21) | POST | `/v1/orders/carts/{cartId}/items` | operationId assigned at implementation |
| Update cart item | TODO — pending [#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21) | PUT | `/v1/orders/carts/{cartId}/items/{lineId}` | operationId assigned at implementation |
| Remove cart item | TODO — pending [#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21) | DELETE | `/v1/orders/carts/{cartId}/items/{lineId}` | operationId assigned at implementation |
| Cancel order | TODO — pending [#19](https://github.com/louisburroughs/durion-positivity-backend/issues/19) | POST | `/v1/orders/{orderId}/cancel` | operationId assigned at implementation; check OpenAPI after issue #19 merges |

Headers and auth notes:

- Always propagate `X-Correlation-Id`.
- Apply `Authorization` and endpoint-specific authorities for restricted operations.
- Use idempotency semantics where the endpoint contract requires mutation deduplication.

## Capability Sections

## CAP-246: POS Sales Order & Cart (Quote-to-Cash Entry Point)

### Capability Metadata

- Capability ID: CAP-246
- Parent Issue: [louisburroughs/durion#246](https://github.com/louisburroughs/durion/issues/246)
- Backend Issues: [#19](https://github.com/louisburroughs/durion-positivity-backend/issues/19) · [#20](https://github.com/louisburroughs/durion-positivity-backend/issues/20) · [#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21)
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-order/openapi.yaml`

---

### Story #20 — Apply Price Override with Permission and Reason

**Implementation link:** [louisburroughs/durion-positivity-backend#20](https://github.com/louisburroughs/durion-positivity-backend/issues/20)

#### API Operation References (OpenAPI Source of Truth)

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Get price overrides | `getOverridesByOrder` | GET | `/v1/orders/price-overrides` |
| Get pending approvals | `getPendingApprovals` | GET | `/v1/orders/price-overrides/pending` |
| Get price override | `getOverride` | GET | `/v1/orders/price-overrides/{overrideId}` |
| Apply price override | `applyPriceOverride` | POST | `/v1/orders/price-overrides` |
| Approve price override | `approvePriceOverride` | POST | `/v1/orders/price-overrides/{overrideId}/approve` |
| Reject price override | `rejectPriceOverride` | POST | `/v1/orders/price-overrides/{overrideId}/reject` |

Gateway base URL: `http://localhost:8080` · Service base URL: `http://localhost:8086`

#### Behavioral Assertions

- The caller must supply a `reasonCode` from the enumerated set (`CUSTOMER_LOYALTY`, `PRICE_MATCH`, `PROMOTIONAL_PRICING`, `PRICING_ERROR_CORRECTION`, `VOLUME_DISCOUNT`, `GOODWILL_ADJUSTMENT`, `MANAGER_DISCRETION`, `OTHER`); `justification` is supplementary.
- When the override discount exceeds the config-driven threshold for the requesting user's role, the service sets `status=PENDING_APPROVAL` and `requiresApproval=true` rather than immediately applying the override.
- Approval requires a distinct authority from the requester; self-approval is disallowed in the service layer.
- Rejection requires a non-empty `reason`; the override transitions to `REJECTED` and the original price remains unchanged.
- Override status lifecycle: `PENDING_APPROVAL` → `APPROVED` → `APPLIED` (forward path), or `PENDING_APPROVAL` → `REJECTED` (rejection path). Any non-terminal state may transition to `CANCELLED`.
- `requestedByUserId`, `approvedByUserId`, and `rejectedByUserId` are derived from authenticated JWT principal via `SecurityContextHelper`; these fields must not be accepted from request payloads (ADR-0018).
- Audit timestamps (`createdAt`, `approvedAt`, `rejectedAt`, `appliedAt`) are server-set and not client-writable.
- Commission impact flag: when a commission-eligible line item is overridden, the service records the commission impact on the override record for downstream reward calculations.

#### Authorization

- Authority `ORDER_PRICE_OVERRIDE_CREATE` is required to apply a price override (`POST /v1/orders/price-overrides`).
- Authority `ORDER_PRICE_OVERRIDE_APPROVE` is required to approve or reject a pending override.
- Insufficient authority returns `403 Forbidden` with a standard error envelope per ADR-0017.

#### Response Code Contract (ADR-0017)

| Scenario | Status |
| --- | --- |
| Override created (may be pending approval) | `201 Created` |
| Override approved or rejected successfully | `200 OK` |
| Override not found | `404 Not Found` |
| Caller lacks required authority | `403 Forbidden` |
| Malformed request or missing required field | `400 Bad Request` |
| Override not in a state that allows the operation | `409 Conflict` |

---

### Story #21 — Create Sales Order Cart and Add Items

**Implementation link:** [louisburroughs/durion-positivity-backend#21](https://github.com/louisburroughs/durion-positivity-backend/issues/21)

#### API Operation References

> Endpoints are not yet present in `openapi.yaml`. The table below documents expected paths and will be updated once issue #21 is implemented and OpenAPI is regenerated.

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Create order cart | TODO | POST | `/v1/orders/carts` |
| Add item to cart | TODO | POST | `/v1/orders/carts/{cartId}/items` |
| Update cart item | TODO | PUT | `/v1/orders/carts/{cartId}/items/{lineId}` |
| Remove cart item | TODO | DELETE | `/v1/orders/carts/{cartId}/items/{lineId}` |

#### Behavioral Assertions

- A newly created cart starts in `DRAFT` state owned by the requesting session/user; no downstream billing events are emitted until the cart is explicitly confirmed.
- Line items may be added, updated, or removed while the order is in `DRAFT` state. Mutations on non-`DRAFT` carts return `409 Conflict` (ADR-0017).
- Inventory availability conflicts follow the `WARN_AND_BACKORDER` policy: the line is accepted with a `LOW_INVENTORY` or `BACKORDERED` warning flag on the response rather than rejected outright.
- Guest-to-authenticated cart merge is supported: duplicate SKU lines are consolidated (quantities summed) rather than duplicated; the guest cart is retired after merge.
- Cart aggregate ID is UUIDv7 and assigned server-side; clients must not supply the cart ID on creation.
- TODO: Confirm operationIds, exact path structure, and response schemas against OpenAPI after issue #21 implementation.

#### Authorization

- Authority `ORDER_CART_CREATE` is required to create a cart (TODO: confirm authority name against implementation).
- Insufficient authority returns `403 Forbidden` per ADR-0017.

---

### Story #19 — Cancel Order with Controlled Void Logic

**Implementation link:** [louisburroughs/durion-positivity-backend#19](https://github.com/louisburroughs/durion-positivity-backend/issues/19)

#### API Operation References

> Endpoints are not yet present in `openapi.yaml`. The table below documents expected paths and will be updated once issue #19 is implemented and OpenAPI is regenerated.

| Use Case | operationId | Method | Path |
| --- | --- | --- | --- |
| Cancel order | TODO | POST | `/v1/orders/{orderId}/cancel` |
| Get cancellation saga state | TODO | GET | `/v1/orders/{orderId}/cancel/status` |

#### Behavioral Assertions

- Order cancellation is POS-orchestrated via a persisted saga: Workexec termination executes first; billing reversal executes only after Workexec confirms cancellation.
- Saga state is persisted at each transition: `CANCEL_REQUESTED` → `WORKEXEC_CANCELLED` → `BILLING_REVERSED` → `CANCELLED`.
- If Workexec cancellation fails, the saga halts in `CANCEL_REQUESTED` with a definitive error response; billing reversal is not attempted.
- If billing reversal fails after a successful Workexec cancellation, the saga transitions to `BILLING_REVERSAL_FAILED` and an alert event is emitted; the system must not leave the order in a silently inconsistent state.
- Cancellation is disallowed on orders already in terminal states (`CANCELLED`, `COMPLETED`); the service returns `409 Conflict` per ADR-0017.
- Actor identity for the cancellation audit record is derived from authenticated JWT principal via `SecurityContextHelper` (ADR-0018); the request payload must not supply actor identity.
- TODO: Confirm saga state field names, exact path structure, and cancellation reason requirements against OpenAPI after issue #19 implementation.

#### Authorization

- Authority `ORDER_CANCEL` is required to initiate cancellation (TODO: confirm authority name).
- Insufficient authority returns `403 Forbidden`  per ADR-0017.

#### Response Code Contract (ADR-0017)

| Scenario | Status |
| --- | --- |
| Cancellation saga initiated | `202 Accepted` |
| Order already cancelled or completed | `409 Conflict` |
| Order not found | `404 Not Found` |
| Caller lacks required authority | `403 Forbidden` |

---

### ADR Compliance — CAP-246

| ADR | Compliance Statement |
| --- | --- |
| ADR-0017 (HTTP Response Codes) | All status codes in this section follow the canonical matrix: `201` for creation, `200` for mutations, `400`/`403`/`404`/`409` for error conditions. `202 Accepted` used for saga-initiated cancellation per async-operation guidance. |
| ADR-0018 (Audit Actor from Security Context) | `requestedByUserId`, `approvedByUserId`, `rejectedByUserId`, and all cancellation actor fields are sourced from `SecurityContextHelper`; request payloads must not supply these values. |
| ADR-0026 (Service Contract Boundary Policy) | Controller methods depend on service interfaces in `com.positivity.order.service`; internal implementations remain in `com.positivity.order.internal.service`. No direct cross-module dependency on `internal.*` packages. |

### Events & Dependencies

- Story #19 emits a `BILLING_REVERSAL_FAILED` alert event; downstream consumers must handle this event for idempotent reversal retry.
- Story #21 must not emit billing events until cart confirmation; draft-state changes are internal order aggregate mutations only.
- All cross-service interactions (Workexec, billing) go through published API/event contracts, not direct data coupling.

### Contract Test Traceability

- Provider tests: `durion-positivity-backend/pos-order/src/test/...`
- Each behavioral assertion above must be covered by a provider contract test when the corresponding issue is implemented.

## Events & Cross-Domain Dependencies

- This domain exchanges data with other services only through REST APIs and message/event contracts.
- Integration failures must be observable through deterministic status and error reporting.
- Any contract-affecting change must update OpenAPI and regenerate API references.

## Verification Metadata

- OpenAPI source: `durion-positivity-backend/pos-order/openapi.yaml`
- OpenAPI source revision: `ca7fadc3` (price-override endpoints; cart and cancel endpoints pending issues #21 and #19)
- Last verified UTC: `2026-03-07T00:00:00Z`
- Last capability update: CAP-246 (stories #19, #20, #21)
- Generated API reference: `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`

## References

- `docs/architecture/api/BACKEND_CONTRACT_GLOBAL_STANDARDS.md`
- `domains/order/.business-rules/AGENT_GUIDE.md`
- `domains/order/.business-rules/DOMAIN_NOTES.md`
- `domains/order/.business-rules/BACKEND_API_REFERENCE.generated.md`
