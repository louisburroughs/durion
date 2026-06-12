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
| CAP-315 | `durion#315` | draft | [CAP] Procure-to-Receive Lifecycle (PO + ASN + Accrual) |

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
| Create Purchase Order | `createPurchaseOrder` | POST | `/v1/inventory/purchase-orders` | PO DRAFT created |
| Get Purchase Order | `getPurchaseOrder` | GET | `/v1/inventory/purchase-orders/{poId}` | |
| List Purchase Orders | `listPurchaseOrders` | GET | `/v1/inventory/purchase-orders` | Filter by vendorId, status |
| Approve Purchase Order | `approvePurchaseOrder` | POST | `/v1/inventory/purchase-orders/{poId}/approve` | Requires PURCHASE_ORDER_APPROVE |
| Revise Purchase Order | `revisePurchaseOrder` | POST | `/v1/inventory/purchase-orders/{poId}/revisions` | Increments versionNumber |
| Cancel Purchase Order | `cancelPurchaseOrder` | POST | `/v1/inventory/purchase-orders/{poId}/cancel` | |
| Create ASN | `createAsn` | POST | `/v1/inventory/asns` | ASN state LOADED |
| Get ASN | `getAsn` | GET | `/v1/inventory/asns/{asnId}` | |
| Create Goods Receipt | `createGoodsReceipt` | POST | `/v1/inventory/goods-receipts` | Creates GRNI accrual event |
| Get Goods Receipt | `getGoodsReceipt` | GET | `/v1/inventory/goods-receipts/{receiptId}` | |

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

## CAP-216: [CAP] Receiving (PO/ASN/Direct-to-Workorder)

### Capability Metadata

- Capability ID: CAP-216
- Parent Issue: https://github.com/louisburroughs/durion/issues/216
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`

### Stories

- #35 — Create Receiving Session
- #34 — Receive Items into Staging
- #33 — Cross-dock to Workorder

### Endpoints (all paths relative to gateway http://localhost:8080)

| Method | Path | Auth | Story | Status Code |
|--------|------|------|-------|-------------|
| POST | /v1/inventory/receiving/sessions | inventory:receiving:create | #35 | 201 |
| GET | /v1/inventory/receiving/sessions/{sessionId} | inventory:receiving:view | #35 | 200 |
| POST | /v1/inventory/receiving/sessions/{sessionId}/receive | inventory:receiving:execute | #34 | 200 |
| POST | /v1/inventory/receiving/sessions/{sessionId}/lines/{lineId}/cross-dock | inventory:receiving:execute AND inventory:issue:parts | #33 | 200 |

### Behavioral Contracts

Story #35 — Create Receiving Session:
- POST `/v1/inventory/receiving/sessions` with a valid `sourceDocumentId` returns `201` with a JSON body containing `sessionId` and `status=OPEN`.
- GET `/v1/inventory/receiving/sessions/{sessionId}` returns `200` with full session details including lines and their current statuses.
- If the referenced source document cannot be located, return `404` with error code `SOURCE_NOT_FOUND`.
- If the source document is already fully received, return `400` with error code `ALREADY_RECEIVED`.

Story #34 — Receive Items into Staging:
- POST `/v1/inventory/receiving/sessions/{sessionId}/receive` with line-level received quantities creates `GOODS_RECEIPT` ledger entries and records variance rows where applicable; response `200` on success.
- When received quantity is less than expected, record a `SHORTAGE` variance for the line and set line status to `RECEIVED_SHORT`.
- When received quantity is greater than expected, record an `OVERAGE` variance for the line and set line status to `RECEIVED_OVER`.
- When each session line is completed (all expected quantities received or variance recorded), session status transitions to `COMPLETED`.

Story #33 — Cross-dock to Workorder:
- POST `/v1/inventory/receiving/sessions/{sessionId}/lines/{lineId}/cross-dock` performs an atomic operation that creates both `GOODS_RECEIPT` and `GOODS_ISSUE` ledger entries so items flow from inbound receipt directly to the target workorder.
- If the target workorder is in a closed state, return `400` with error code `WORKORDER_CLOSED` and no ledger rows are written.
- If the caller lacks `inventory:issue:parts` authority, return `403` `FORBIDDEN`.
- If the session or session line is not found, return `404`.

### Module

- pos-inventory

### Flyway migrations added

- V4: create `receiving_session` and `receiving_line` tables
- V5: create `inventory_variance` table

### Test & Contract Notes

- Contract tests should target the gateway-format paths (`/v1/...`) and assert both success and explicit error codes listed above.
- Mock upstream source document lookups in provider tests to exercise `SOURCE_NOT_FOUND` and `ALREADY_RECEIVED` paths deterministically.
- Assert ledger rows created with correct `transactionType` (`GOODS_RECEIPT`, `GOODS_ISSUE`) and that cross-dock operations are atomic.

### Traceability

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Ensure generated API reference (`domains/inventory/.business-rules/BACKEND_API_REFERENCE.generated.md`) is updated if OpenAPI changes.


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
| Reserve stock for a workorder line | `createReservation` | POST | `/v1/inventory/reservations` |
| Promote SOFT allocation to HARD | `promoteAllocation` | POST | `/v1/inventory/allocations/{allocationId}/promote` |
| Generate pick list from confirmed reservations | `generatePickList` | POST | `/v1/inventory/pick-lists/generate` |
| Fetch picking list for workorder execution | `fetchPickingList` | GET | `/v1/inventory/picking-lists/{pickingListId}` |
| Validate pick scan input | `validatePickScan` | POST | `/v1/inventory/picking-lists/{pickingListId}/scan` |
| Confirm picking completion | `confirmPicking` | POST | `/v1/inventory/picking-lists/{pickingListId}/confirm` |
| Consume picked items to workorder | `consumePickedItems` | POST | `/v1/inventory/consume` |
| Return unused items to stock | `returnItemsToStock` | POST | `/v1/inventory/returns` |

### Behavioral Assertions

#### Story #29 — Reserve/Allocate Stock to Workorder Lines

- SOFT allocation request is idempotent by `workorderLineId`; repeated calls upsert the same reservation/allocation rather than creating duplicates.
- SOFT allocation does not reduce ATP; HARD allocation reduces ATP using `ATP = onHand - hardAllocated`.
- Promoting SOFT to HARD requires permission `inventory.reserve.hard`; unauthorized promotion is rejected.
- Cancelling SOFT allocation leaves ATP unchanged; cancelling HARD allocation restores ATP by released quantity.
- HARD allocation with insufficient ATP returns `422 INSUFFICIENT_ATP` and records reservation status `BACKORDERED`.

#### Story #656 — Write Allocation Ledger Events and Assign Location on Hard Promotion

- `promoteAllocation` requires `storageLocationId` in the request body; a missing value returns `400`.
- The storage location is validated against pos-location before any state change: nonexistent location returns `404`, inactive location returns `400`.
- Successful promotion pins the allocation to `storageLocationId` and writes an `ALLOCATION_CREATED` inventory ledger event (quantity = `allocatedQuantity`, `locationId` = storage location, `sourceTransactionId` = allocation id) in the same transaction.
- Repeat promotion of an already-HARD allocation with a recorded location writes no duplicate `ALLOCATION_CREATED` and keeps the original location.
- Cancelling a reservation writes `ALLOCATION_RELEASED` only for HARD allocations with a non-null `locationId` (those with a matching `CREATED` event); SOFT and unlocated allocations release silently.
- Invariant: per allocation, ledger `ALLOCATION_CREATED` quantities − `ALLOCATION_RELEASED` quantities ∈ {0, allocatedQuantity}.
- Consumption-closure invariant: whichever flow posts `WORKORDER_CONSUMPTION` for allocated stock must also close the allocation with `ALLOCATION_RELEASED`, otherwise outstanding allocations double-count consumed stock.
- No backfill: allocation rows hardened before this change keep `locationId = null`; ledger reflects events from deployment forward.

#### Story #28 — Create Pick List / Pick Tasks for Workorder

- Processing `WorkOrderPartsReservationConfirmed` creates one `PickList` and one `PickTask` per reserved line item.
- Location selection prefers `isPickZone=true` locations and applies deterministic sort `zoneOrder ASC -> aisleOrder ASC -> rackOrder ASC -> binOrder ASC`.
- Effective priority is computed as `min(basePriority + modifiers, MAX_PRIORITY)` and due time as `scheduledStartAt - 30min`.
- Missing eligible location sets `PickTask` status to `NeedsReview` and keeps `PickList` status `Draft`.
- Successful generation emits `PickListCreated` event with correlation to the originating workorder reservation flow.

#### Story #179 — Mechanic Executes Picking (Scan + Confirm)

- `PickingList` is keyed to `workorderId` in inventory state, while workexec owns user-facing orchestration boundaries (ADR-0006).
- Scan validation rejects requests with `400` when item is not in list or scan quantity exceeds `requiredQuantity`.
- Confirm action returns `422` when any item remains not fully picked; all items `PICKED` transitions list to `COMPLETED`.
- Confirm success transitions related allocation records from `ALLOCATED` to `DISBURSED_TO_WORK_ORDER`.
- Confirm emits `workexec.PickingListConfirmed` when complete, otherwise `workexec.PickingListPartial`.

#### Story #178 — Issue/Consume Picked Items to Workorder

- `POST /v1/inventory/consume` persists immutable `InventoryLedgerEntry` rows with `transactionType=WORKORDER_CONSUMPTION` and negative `quantityChange`.
- Consume operation is atomic across all requested items; partial commit is not allowed when any line fails validation.
- Unit cost uses valuation method rules (Weighted Average as v1 default) and persists computed cost on each ledger entry.
- Workorder not in `In Progress` state returns `409`; item not picked for the workorder returns `400`.
- Requested consume quantity greater than picked quantity returns `400`, and no ledger rows are written.

#### Story #177 — Return Unused Items to Stock with Reason

- Return requires workorder in `Completed` or `Closed` state and caller permission `inventory:return:create`.
- Return request must include valid reason code (`NOT_NEEDED`, `WRONG_PART`, `CUSTOMER_REFUSED`); missing reason returns `400`.
- Return quantity greater than consumed quantity returns `422` and does not change inventory.
- Accepted return creates `InventoryReturn` record and immutable `RETURN_TO_STOCK` ledger entries that increment on-hand.
- Return write and related outbox/event publish are atomic, and emits `Inventory.ItemReturnedToStock` on success.

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

## CAP-220 — Reservations, Allocations, and Substitutions

> Status: **draft** | Module: `pos-inventory` | Issues: [#24](https://github.com/louisburroughs/durion-positivity-backend/issues/24) [#25](https://github.com/louisburroughs/durion-positivity-backend/issues/25)

### Story #24 — Reallocate Reserved Stock When Schedule Changes

**Endpoint:** `POST http://localhost:8080/v1/inventory/allocations/reallocate`

**Required Authority:** `inventory:allocations:reallocate`

#### Request Body
```json
{
  "stockItemId": "<uuid>",
  "triggerType": "PRIORITY_CHANGE | SCHEDULE_CHANGE | MANUAL_OVERRIDE | ...",
  "triggerReferenceId": "<string | null>"
}
```

#### Response (200 OK)
```json
{
  "stockItemId": "<uuid>",
  "totalReallocated": 2,
  "auditRecordsCreated": 3,
  "atpAfterReallocation": 5
}
```

#### Behavioral Contract
- **Deterministic sort:** workorders sorted by (effectivePriority ASC → dueDateTime ASC → waitingSince ASC → scheduleStartTime ASC → createdAt ASC); same inputs always yield same allocation outcome.
- **Priority aging:** `effectivePriority = max(CRITICAL=1, basePriority - floor((now - waitingSince - 24h) / 24h))`. Aging begins after 24h grace period. Aging resets when a workorder receives a full allocation.
- **Full-allocation-only:** a workorder receives either its full `requiredQuantity` or 0. No partial allocations.
- **ATP invariant:** `ATP = onHand - totalAllocated` is unchanged by reallocation; the total allocated across all workorders equals the original total.
- **Audit:** an `inventory_allocation_audit` record is created for every workorder processed (both allocated and displaced), with `reasonCode` set to `PRIORITY_CHANGE`, `PRIORITY_AGED`, or `STOCK_SHORTAGE` as appropriate.

#### Error Responses
- `400 Bad Request` — `stockItemId` missing or null
- `422 Unprocessable Entity` — domain constraint violation (future extension)

---

### Story #25 — Handle Shortages with Backorder or Substitution Suggestion

**Endpoint:** `POST http://localhost:8080/v1/inventory/allocations/shortages/resolve`

**Required Authority:** `inventory:shortages:resolve`

#### Request Body
```json
{
  "allocationId": "<uuid>",
  "sku": "<string>",
  "shortQuantity": 3
}
```

#### Response (200 OK)
```json
{
  "allocationId": "<uuid>",
  "sku": "PART-12345",
  "options": [
    {
      "type": "SUBSTITUTE",
      "substitutePartNumber": "ALT-PART-99",
      "unitCost": 45.00,
      "estimatedLeadTimeDays": 3,
      "source": "PRODUCT",
      "confidence": "HIGH",
      "qualityTier": "A"
    },
    {
      "type": "EXTERNAL_PURCHASE",
      "substitutePartNumber": null,
      "unitCost": 52.00,
      "estimatedLeadTimeDays": 5,
      "source": "EXTERNAL",
      "confidence": "MEDIUM",
      "qualityTier": "B"
    },
    {
      "type": "BACKORDER",
      "substitutePartNumber": null,
      "unitCost": null,
      "estimatedLeadTimeDays": null,
      "source": null,
      "confidence": null,
      "qualityTier": null
    }
  ],
  "partialResultsBanner": false
}
```

#### Behavioral Contract
- **Option ordering:** `SUBSTITUTE` options appear first, then `EXTERNAL_PURCHASE`, then `BACKORDER`. The `BACKORDER` option is always present.
- **Within-category ranking:** sorted by `estimatedLeadTimeDays ASC`, then `unitCost ASC`, then `qualityTier DESC`.
- **Parallel resolution:** Product Domain (`/product/v1/substitutes:resolve`, timeout 800ms) and Positivity Domain (`/positivity/v1/availability/external`, timeout 1200ms) are called in parallel.
- **Timeout / failure:** if a client times out or errors, that category is omitted and `partialResultsBanner: true` is set. The remaining categories are still returned.
- **BACKORDER fields:** `estimatedLeadTimeDays`, `source`, and `confidence` are `null` when no lead time source is available (tiered fallback: PURCHASING → INVENTORY → CATALOG). The BACKORDER option is never omitted.
- **Event:** `@EmitEvent(id = "INVENTORY_SHORTAGE_RESOLVE", apiVersion = "1")` on the controller.

#### Error Responses
- `400 Bad Request` — `allocationId` or `sku` missing
- `422 Unprocessable Entity` — domain constraint violation (future extension)

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

## CAP-315: Procure-to-Receive Lifecycle (PO + ASN + Accrual)

### Capability Metadata

- Capability ID: CAP-315
- Parent Issue: https://github.com/louisburroughs/durion/issues/315
- Capability Status: draft
- OpenAPI Source: `durion-positivity-backend/pos-inventory/openapi.yaml`
- Backend Issues: #571 (ASN + Receiving), #572 (Purchase Order)

### Stories

- #572 — Create Purchase Order (Procure-to-Pay Initiation)
- #571 — Load ASN for Receiving

### Endpoints (all paths relative to gateway http://localhost:8080)

| Method | Path | Auth | Story | Status Code |
|--------|------|------|-------|-------------|
| POST | /v1/inventory/purchase-orders | PURCHASE_ORDER_CREATE | #572 | 201 |
| GET | /v1/inventory/purchase-orders/{poId} | PURCHASE_ORDER_VIEW | #572 | 200 |
| GET | /v1/inventory/purchase-orders | PURCHASE_ORDER_VIEW | #572 | 200 |
| POST | /v1/inventory/purchase-orders/{poId}/approve | PURCHASE_ORDER_APPROVE | #572 | 200 |
| POST | /v1/inventory/purchase-orders/{poId}/revisions | PURCHASE_ORDER_CREATE | #572 | 201 |
| POST | /v1/inventory/purchase-orders/{poId}/cancel | PURCHASE_ORDER_APPROVE | #572 | 200 |
| POST | /v1/inventory/asns | ASN_CREATE | #571 | 201 |
| GET | /v1/inventory/asns/{asnId} | ASN_VIEW | #571 | 200 |
| POST | /v1/inventory/goods-receipts | GOODS_RECEIPT_CREATE | #571 | 201 |
| GET | /v1/inventory/goods-receipts/{receiptId} | GOODS_RECEIPT_VIEW | #571 | 200 |

### Behavioral Contracts

#### Story #572 — Create Purchase Order (Procure-to-Pay Initiation)

**PO Lifecycle state machine:**
- States: `DRAFT` → `APPROVED` → `PARTIALLY_RECEIVED` → `FULLY_RECEIVED` → `CLOSED`
- `CANCELLED` is a terminal state reachable from `DRAFT` or `APPROVED` (not from received states).
- Only `APPROVED` POs may generate receipts or be matched to invoices.

**PO Creation:**
- `POST /v1/inventory/purchase-orders` creates a PO in `DRAFT` state and returns `201`.
- Required fields: `vendorId`, `poDate`, `currency` (ISO 4217), `shipToLocationId`, `lines[]` with `skuId`/`description`, `quantity`, `unitCostMinor`, `taxCodeId`.
- Computed on creation: `lineTotalMinor = quantity × unitCostMinor` (per-line), `subtotalMinor`, `taxMinor`, `grandTotalMinor = subtotalMinor + taxMinor`.
- All monetary values stored in minor currency units plus currency code.
- Missing or inactive vendor returns `400` with code `VENDOR_NOT_FOUND` or `VENDOR_INACTIVE`.
- SKU not found returns `400` with code `SKU_NOT_FOUND`.

**Approval:**
- `POST /v1/inventory/purchase-orders/{poId}/approve` transitions PO from `DRAFT` to `APPROVED`.
- On approval: price and quantity fields locked (read-only for receiving/matching); changes only via revision workflow.
- On approval: emit `PurchaseOrderApproved` event payload: `{ poId, vendorId, totalAmountMinor, currency, lineItems[], approvalStatus, approverId, approvalTimestamp }`.
- Encumbrance (config flag `encumbranceEnabled`, default `false`): if `true`, emit encumbrance posting event. If `false`, no GL posting at approval time.
- Unauthorized approval (wrong role) returns `403`.

**Receipts against PO:**
- Receipt attempt against a non-`APPROVED` PO returns `409` with error code `PO_NOT_APPROVED`.
- Each receipt decrements `openQuantity` and `openValueMinor` per line and at PO level.
- When all line `openQuantities` reach zero, PO transitions to `FULLY_RECEIVED`.
- Partial receipts supported; PO transitions to `PARTIALLY_RECEIVED`.

**Revision workflow:**
- `POST /v1/inventory/purchase-orders/{poId}/revisions` creates a new revision.
- `versionNumber` increments on each revision; previous versions are immutable in audit trail.
- Emit `PurchaseOrderRevised` event with `priorVersion` and `delta`.
- Revision reducing quantities below already-received quantities is rejected with `409`.

**AP visibility:**
- `GET /v1/inventory/purchase-orders` supports filtering by `vendorId`, `status`, date ranges, `locationId`, `currency`.
- Response includes `openBalanceMinor`, per-line `openQuantity` for 2-way/3-way matching.

**Events emitted:**
- `PurchaseOrderCreated`: `{ poId, vendorId, totalAmountMinor, currency, lineItems[], status, versionNumber, timestamp, actorId }`
- `PurchaseOrderApproved`: `{ poId, vendorId, totalAmountMinor, currency, lineItems[], approvalStatus, versionNumber, timestamp }`
- `PurchaseOrderRevised`: `{ poId, priorVersion, versionNumber, delta, timestamp, actorId }`
- `PurchaseOrderCancelled`: `{ poId, vendorId, timestamp, actorId, reason }`

**Actor source (ADR-0018):** Actor extracted from SecurityContext, never from request body or X-User-Id header.

#### Story #571 — Load ASN for Receiving

**ASN Lifecycle state machine:**
- States: `LOADED` → `READY_FOR_RECEIPT` → `PARTIALLY_RECEIVED` → `FULLY_RECEIVED` → `CANCELLED`
- ASN transitions to `PARTIALLY_RECEIVED` when at least one line is partially received.
- ASN transitions to `FULLY_RECEIVED` when all line received quantities equal shipped quantities.
- `CANCELLED` allowed if no receipts exist; cannot cancel ASN with posted receipts.

**ASN Creation:**
- `POST /v1/inventory/asns` creates ASN in `LOADED` state and returns `201`.
- Required fields: `vendorId`, `asnReferenceNumber` (vendor external identifier), `relatedPoNumbers[]` (at least one), `shipDate`, `expectedArrivalDate`, `lineItems[]` with `sku`, `quantityShipped`.
- Validation: all referenced POs must exist and be in `APPROVED` state; returns `400` with `INVALID_PO_REFERENCE` listing invalid PO IDs.
- Each `sku` must exist on the referenced PO line; returns `400` with `SKU_NOT_ON_PO`.
- `quantityShipped` must be positive; over-shipment relative to PO remaining quantity requires elevated permission or returns `400` with `OVER_SHIPMENT`.
- Duplicate ASN (same `vendorId` + `asnReferenceNumber`) returns `409` with `DUPLICATE_ASN`.
- ASN creation produces **no GL journal entries** (accrual occurs only at physical receipt).

**Goods Receipt:**
- `POST /v1/inventory/goods-receipts` creates a goods receipt and returns `201`.
- Required fields: `asnId` (nullable), `poId`, `locationId`, `lines[]` with `sku`, `quantityReceived`, `unitCostMinor`.
- On receipt completion: emit `ReceiptCompleted` event and create GRNI accrual entries (handled by domain:accounting subscriber): Dr Inventory Asset / Cr Accrued Purchases (GRNI).
- Receipt must link to an `APPROVED` PO; receipt against a non-approved PO returns `409`.
- On-hand quantity for SKU at location increases by `quantityReceived` (GOODS_RECEIPT ledger entry, type INBOUND).
- Lot/serial tracking: `lotNumber` and `serialNumbers[]` must be captured when SKU requires lot/serial tracking.

**Over/Under receipt rules:**
- Over-receipt (`actualReceivedQty > ASN shippedQty` or PO remaining qty) without override permission returns `403` with `OVER_RECEIPT_NOT_PERMITTED`.
- Over-receipt with override permission: proceeds and creates a variance record + emits over-receipt event.
- Under-receipt: ASN transitions to `PARTIALLY_RECEIVED`; remaining quantity stays open.

**GRNI / AP matching:**
- GRNI balance queryable per PO via `GET /v1/inventory/purchase-orders/{poId}` response field `grniBalanceMinor`.
- Receipt lines are linked to PO lines for 3-way match (PO ↔ Receipt ↔ Invoice).

**Events emitted:**
- `ASNLoaded`: `{ asnId, vendorId, relatedPoNumbers, lineItems, loadedBy, timestamp }`
- `ReceiptCreated`: `{ receiptId, asnId, poId, lines[], createdBy, timestamp }`
- `ReceiptCompleted`: `{ receiptId, asnId, poId, totalAccruedAmountMinor, lineItems[], locationId, timestamp }`

**Actor source (ADR-0018):** Actor extracted from SecurityContext.

### Module

- pos-inventory

### Flyway migrations required

- PO migration: `purchase_order`, `purchase_order_line` tables
- ASN migration: `advance_shipping_notice`, `asn_line` tables
- Goods Receipt migration: `goods_receipt`, `goods_receipt_line` tables

### ADR Constraints

- ADR-0017: Receipt against non-APPROVED PO → `409`; over-receipt without permission → `403`; SKU validation failure → `400`
- ADR-0018: Actor always from SecurityContext
- ADR-0025: Permissions in permissions.yaml; use registry constants in @PreAuthorize
- ADR-0001: GOODS_RECEIPT ledger entry type for on-hand updates

### Test & Contract Notes

- Contract tests assert DRAFT PO receipt rejection (409), GRNI accrual event emission, PO state transitions (PARTIALLY_RECEIVED → FULLY_RECEIVED).
- Tests for ASN: invalid PO reference (400), duplicate ASN (409), no GL on ASN creation, GRNI accrual at receipt.
- Use `@WithMockUser` for permission-restricted endpoints.
- Provider test class: `PurchaseOrderContractBehaviorIT`, `AsnContractBehaviorIT`.

### Traceability

- Provider tests: `durion-positivity-backend/pos-inventory/src/test/...`
- Issues: #571 (ASN), #572 (PO)
- Capability: CAP-315

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
