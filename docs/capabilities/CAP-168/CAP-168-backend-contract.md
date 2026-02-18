---
title: CAP-168 Backend Contract
capability: CAP:168
domain: product
version: 0.1
last_updated: 2026-02-18
---

# CAP-168: Location Store Pricing (Overrides by Location) — Backend Contract

Authoritative source: `/home/louisb/Projects/durion-positivity-backend/pos-catalog/openapi.json`

This document consolidates the backend contract for CAP-168. It contains the endpoint contracts (gateway URLs), TypeScript-like schema interfaces derived from OpenAPI, JSON examples, and ContractBehaviorIT test scenario hints.

All gateway URLs use the API Gateway format: `http://localhost:8080/v1/products/pricing/...`.

## Endpoints

1) Create Location Price Override

- Gateway URL: `http://localhost:8080/v1/products/pricing/location-overrides`
- Method: `POST`
- OperationId: `createLocationPriceOverride`
- Request: `LocationPriceOverrideCreateRequestDto` (application/json)
- Success: `201 Created` + `LocationPriceOverrideResponseDto`
- Errors: `400 Bad Request` (guardrail validation failed), `403 Forbidden`

2) Approve Pending Override

- Gateway URL: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/approve`
- Method: `POST`
- OperationId: `approveLocationPriceOverride`
- Request: `LocationPriceOverrideDecisionRequestDto`
- Success: `200 OK` + `LocationPriceOverrideResponseDto`
- Errors: `404 Not Found`, `409 Conflict`, `400 Bad Request`

3) Reject Pending Override

- Gateway URL: `http://localhost:8080/v1/products/pricing/location-overrides/{overrideId}/reject`
- Method: `POST`
- OperationId: `rejectLocationPriceOverride`
- Request: `LocationPriceOverrideDecisionRequestDto`
- Success: `200 OK` + `LocationPriceOverrideResponseDto`
- Errors: `404 Not Found`, `400 Bad Request`, `409 Conflict`

4) Get Effective Location Price

- Gateway URL: `http://localhost:8080/v1/products/pricing/effective-price/{locationId}/{productId}`
- Method: `GET`
- OperationId: `getEffectiveLocationPrice`
- Success: `200 OK` + `EffectiveLocationPriceResponseDto`
- Errors: `404 Not Found` when no pricing context exists

5) Upsert Guardrail Policy

- Gateway URL: `http://localhost:8080/v1/products/pricing/guardrail-policies`
- Method: `POST`
- OperationId: `upsertLocationGuardrailPolicy`
- Request: `GuardrailPolicyUpsertRequestDto`
- Success: `200 OK`
- Errors: `400 Bad Request`

## TypeScript-like Interfaces (derived from OpenAPI)

// Request to create a location-specific override
interface LocationPriceOverrideCreateRequestDto {
  locationId?: string; // uuid
  productId?: string; // uuid
  basePrice?: number;
  cost?: number;
  overridePrice?: number;
  createdByUserId?: string; // uuid
}

// Standard response for override resources
interface LocationPriceOverrideResponseDto {
  overrideId?: string;
  version?: number;
  locationId?: string;
  productId?: string;
  basePrice?: number;
  cost?: number;
  overridePrice?: number;
  discountPercent?: number;
  marginPercent?: number;
  status?: 'ACTIVE' | 'PENDING_APPROVAL' | 'REJECTED' | 'INACTIVE';
  createdByUserId?: string;
  createdAt?: string; // date-time
  assignedApproverId?: string;
  assignmentStrategy?: string;
  approvedByUserId?: string;
  approvedAt?: string; // date-time
  rejectedBy?: string;
  rejectedAt?: string; // date-time
  rejectionReasonCode?: string;
  rejectionNotes?: string;
}

interface LocationPriceOverrideDecisionRequestDto {
  version?: number;
  actorUserId?: string; // uuid
  rejectionReasonCode?: string;
  rejectionNotes?: string;
}

interface GuardrailPolicyUpsertRequestDto {
  scopeId?: string; // uuid
  minMarginPercent?: number;
  maxDiscountPercent?: number;
  autoApprovalThresholdPercent?: number;
}

interface EffectiveLocationPriceResponseDto {
  locationId?: string;
  productId?: string;
  basePrice?: number;
  effectivePrice?: number;
  overrideStatus?: 'ACTIVE' | 'PENDING_APPROVAL' | 'REJECTED' | 'INACTIVE';
}

## JSON Examples

Create Override (request):

```json
{
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "basePrice": 100.00,
  "cost": 50.00,
  "overridePrice": 88.00,
  "createdByUserId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a537"
}
```

Create Override (pending approval response):

```json
{
  "overrideId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a538",
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "basePrice": 100.00,
  "overridePrice": 88.00,
  "discountPercent": 12.0000,
  "marginPercent": 43.1818,
  "status": "PENDING_APPROVAL",
  "assignedApproverId": "4b7e0f8e-26f1-3ac8-bde8-e2cb8f8ad7e8",
  "assignmentStrategy": "LOCATION_SCOPE_PRIMARY_THEN_POOL"
}
```

Effective Price response example:

```json
{
  "locationId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a535",
  "productId": "0196cf6f-c8dd-7ee0-93e7-f48a5698a536",
  "basePrice": 100.00,
  "effectivePrice": 88.00,
  "overrideStatus": "ACTIVE"
}
```

## ContractBehaviorIT Test Scenarios (hints)

- CP-101: Create override within auto-approval threshold (happy path)
  - Request: Create override with `overridePrice` within `autoApprovalThresholdPercent`.
  - Expected: `201 Created`, response `status` is `ACTIVE` (or `PENDING_APPROVAL` if auto-approval semantics differ); `getEffectiveLocationPrice` returns `effectivePrice` equal to override price.

- VE-101: Validation error (guardrail hard limit)
  - Request: Create override that violates `minMarginPercent`.
  - Expected: `400 Bad Request` with guardrail error indicator in response body.

- AUTH-101: Authorization failure
  - Request: Create override without required role/authority.
  - Expected: `403 Forbidden`.

- ID-101: Idempotency / retry behavior
  - Hint: If the service implements idempotency via `Idempotency-Key`, tests should assert a retry returns the same resource or idempotent response code. If not implemented, document the chosen behavior in the PR and assert accordingly.

- LC-101: Approval lifecycle
  - Steps: Create override that is `PENDING_APPROVAL`; call approve endpoint; assert `200 OK` and `getEffectiveLocationPrice` reflects activated override; verify optimistic locking (`version`) enforced on approve/reject.

## Implementation Notes

- Use guardrail policy to decide `ACTIVE` vs `PENDING_APPROVAL` transitions.
- Approve/Reject endpoints must accept a `version` to support optimistic locking and surface `409 Conflict` on mismatch.
- Standardize error envelope: `{ code, message, correlationId, fieldErrors[]? }`.

---

## Story fulfillment handoff (substitution block)

capability_label: CAP:168
capability_id: 168
domain: product
parent_capability_number: 168
parent_capability_url: https://github.com/louisburroughs/durion/issues/168
parent_capability_title: "[CAP] Location Store Pricing (Overrides by Location)"
parent_stories_list: "- [durion#168](https://github.com/louisburroughs/durion/issues/168) — Location Store Pricing"
backend_child_issues: |
  - [durion-positivity-backend#52](https://github.com/louisburroughs/durion-positivity-backend/issues/52)
  - [durion-positivity-backend#53](https://github.com/louisburroughs/durion-positivity-backend/issues/53)
