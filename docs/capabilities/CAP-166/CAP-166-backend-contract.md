# CAP-166 — Cost Management (Acquisition & Cost Models)

**Capability manifest:** /docs/capabilities/CAP-166/CAPABILITY_MANIFEST.yaml
**Status:** draft
**Authoritative backend repo:** louisburroughs/durion-positivity-backend (issues #195, #196)

**Guide version:** v0.5
**Last Updated:** 2026-02-18

---

## Purpose

This document specifies the planned backend contract for CAP-166. It documents the REST endpoints, request/response payload shapes, behavioral assertions, and test hints for implementers. Two backend issues form the scope:

- Issue #195: SupplierItemCost and CostTier (volume-based supplier-item cost tiers)
- Issue #196: Item cost management — `standardCost`, `lastCost`, `averageCost` with audit

OpenAPI (`pos-catalog/openapi.json`) is authoritative for implemented endpoints; the paths below are planned additions and must be added to OpenAPI when implemented.

## Base URL (Gateway)

All paths below use the API Gateway format (MANDATORY):

`http://localhost:8080/v1/products` (append the paths listed per operation)

---

## Issue #195 — SupplierItemCost (Cost Tiers)

Paths (gateway):

- `POST /v1/products/supplier-costs`
- `GET /v1/products/supplier-costs/{supplierItemCostId}`
- `PUT /v1/products/supplier-costs/{supplierItemCostId}`
- `DELETE /v1/products/supplier-costs/{supplierItemCostId}`

Overview: Store a single supplier-item cost structure containing ordered `CostTier` entries to model quantity price breaks (volume-based pricing) for a supplier+item.

Business rules (summary):
- Only one active `SupplierItemCost` per `supplierId` + `itemId`.
- `tiers` must be contiguous, starting at `minQuantity = 1`, with no gaps or overlaps.
- The final tier must have `maxQuantity = null` to indicate "and above".
- `unitCost` values must be positive (> 0).
- Creating a duplicate supplier+item returns `409 Conflict`.
- Overlapping or non-contiguous tiers return `400` with code `INVALID_TIER_STRUCTURE`.

Request/response shapes (TypeScript-like):

Create request:
```ts
interface CreateSupplierItemCostRequest {
  supplierId: string; // uuid
  itemId: string; // uuid
  currencyCode: string; // ISO 4217
  baseCost?: string | null; // decimal string, precision scale 4
  tiers: Array<{
    minQuantity: number; // >=1
    maxQuantity?: number | null; // nullable for final tier
    unitCost: string; // decimal string > 0
  }>;
}
```

Create response (201):
```ts
interface SupplierItemCostResponse {
  id: string;
  supplierId: string;
  itemId: string;
  currencyCode: string;
  baseCost?: string | null;
  tiers: Array<{ minQuantity: number; maxQuantity?: number | null; unitCost: string }>;
  createdAt: string;
  updatedAt: string;
}
```

Errors and status codes:
- `201 Created` — created resource
- `400 Bad Request` — invalid payload or `INVALID_TIER_STRUCTURE`
- `403 Forbidden` — insufficient permission
- `409 Conflict` — duplicate supplier+item

Behavioral assertions:
- Tiers in responses MUST be ordered ascending by `minQuantity`.
- Server must validate contiguity and positivity on create/update.
- The API may enforce idempotency via an `Idempotency-Key` header (recommended) for create.

ContractBehaviorIT suggestions:
- CP-166-195-001: create valid contiguous tiers -> assert `201` and persisted values
- CP-166-195-002: create overlapping tiers -> assert `400` + `INVALID_TIER_STRUCTURE`
- CP-166-195-003: create duplicate supplier+item -> assert `409`

---

## Issue #196 — Item Costs: Standard / Last / Average (with audit)

Paths (gateway):

- `PUT /v1/products/items/{itemId}/standard-cost`
- `GET /v1/products/items/{itemId}/costs`
- `GET /v1/products/items/{itemId}/costs/audit`

Overview: Provide controlled manual updates to `standardCost` and read-only views plus audit records for `lastCost` and `averageCost`. `lastCost` and `averageCost` are system-managed and updated when purchase receipts are processed.

Business rules (summary):
- `standardCost`: manual-only; requires `inventory.cost.standard.update` permission and a `reasonCode` for audit.
- `lastCost`: system-managed only — updated from purchase order receipts; manual edits must be rejected with `400`.
- `averageCost`: system-managed only — calculated using Weighted Average Cost (WAC) formula below; manual edits rejected with `400`.
- Initial values for `standardCost`, `lastCost`, and `averageCost` MUST be `null`.
- WAC formula:
  ```
  NewAverageCost = ((OldQtyOnHand * OldAverageCost) + (ReceivedQty * ReceivedUnitCost)) / (OldQtyOnHand + ReceivedQty)
  ```
- Every cost change MUST insert an `ItemCostAudit` entry in the same transaction as the cost update.

PUT `/v1/products/items/{itemId}/standard-cost`
- Purpose: Set/update `standardCost` for `itemId`.
- Required permission: `inventory.cost.standard.update` (return `403` if missing).
- Request:
```ts
interface UpdateStandardCostRequest {
  standardCost: string | null; // decimal string, nullable to unset
  currencyCode?: string; // optional
  reasonCode: string; // required
  modifiedByUserId?: string;
}
```
- Responses:
  - `200 OK` — updated value
  - `400 Bad Request` — missing `reasonCode` or invalid payload
  - `403 Forbidden` — missing permission
  - `404 Not Found` — item not found
- ContractBehaviorIT hints:
  - Authorized update with `reasonCode` -> assert `200` and audit record created
  - Missing `reasonCode` -> assert `400` and no DB change
  - Unauthorized -> `403`

GET `/v1/products/items/{itemId}/costs`
- Purpose: Return `{ standardCost, lastCost, averageCost, currencyCode }` for the item.
- Response:
```ts
interface ItemCostsResponse {
  itemId: string;
  standardCost?: string | null;
  lastCost?: string | null;
  averageCost?: string | null;
  currencyCode?: string | null;
}
```
- Responses: `200 OK`, `404 Not Found`, `403 Forbidden`
- ContractBehaviorIT hints:
  - New item -> all three `null`.
  - After a purchase receipt event -> `lastCost` and `averageCost` updated per WAC.

GET `/v1/products/items/{itemId}/costs/audit`
- Purpose: Query `ItemCostAudit` history for an item; supports pagination and simple filters.
- Query params: `fromTimestamp?`, `toTimestamp?`, `costType?` (STANDARD|LAST|AVERAGE), `page`, `size`
- Response entry shape:
```ts
interface ItemCostAuditEntry {
  auditId: string;
  itemId: string;
  timestamp: string;
  costTypeChanged: 'STANDARD'|'LAST'|'AVERAGE';
  oldValue?: string | null;
  newValue?: string | null;
  changeSourceType: 'MANUAL' | 'PURCHASE_ORDER';
  changeSourceId?: string | null;
  actor?: string | null;
  reasonCode?: string | null; // required for MANUAL standard cost changes
}
```
- Responses: `200 OK` + paged entries, `404 Not Found` when item missing, `403 Forbidden`
- ContractBehaviorIT hints:
  - After manual standard update -> audit contains MANUAL entry with `reasonCode`.
  - After receipt -> audit contains `PURCHASE_ORDER` entries for `LAST` and `AVERAGE`.

Errors (recommended codes):
- `INVALID_TIER_STRUCTURE` -> 400
- `NOT_FOUND` -> 404
- `CONFLICT` -> 409
- `FORBIDDEN` -> 403

---

## Implementation notes & OpenAPI

- The above endpoints are planned additions and must be added to `pos-catalog/openapi.json` under `paths` with matching request/response schemas under `components/schemas`.
- Implementers should:
  - Add OpenAPI `SupplierItemCost` and `CostTier` schemas mirroring the TypeScript-like shapes above.
  - Add `ItemCostsResponse` and `ItemCostAuditEntry` schemas.
  - Annotate controller mutation methods with `@EmitEvent` for audit/observability where appropriate.
  - Enforce transactional writes for cost updates + audit entries. Audit write failure SHOULD rollback cost update.

## Testing & ContractBehaviorIT guidance

- Add ContractBehaviorIT tests covering:
  - Happy path create/update/delete for supplier-item tiers.
  - Validation scenarios (overlap, gaps, negative costs).
  - Authorization scenarios for manual standard cost updates.
  - Audit transactional integrity: simulate audit write failure and assert rollback.

---

## Migration / Backward compatibility

- As these endpoints are greenfield, no breaking changes to existing CAP-165/CAP-167/CAP-168 endpoints are expected.
- When OpenAPI is updated, increment Product guide version and coordinate with frontend teams.

---

End of CAP-166 backend contract spec.
