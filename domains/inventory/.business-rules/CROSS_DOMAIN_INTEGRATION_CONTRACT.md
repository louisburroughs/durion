# Inventory Cross-Domain Integration Contracts

**Version:** 1.0  
**Status:** DRAFT  
**Last Updated:** 2026-01-25  
**Audience:** Inventory domain, Workexec domain, Accounting domain, Catalog/Pricing domain, Security/Organization domains

---

## Overview

This document defines the integration contracts between the Inventory domain and the
external domains required to deliver Phase 3 inventory capabilities. Each contract
specifies the service/endpoint, request/response schema, error handling, and usage
context. Contracts mirror structure and rigor used in CRM and Accounting domain
integration guides.

---

## Table of Contents

1. [Workexec Domain – Workorder Picking, Consumption, Returns](#workexec-domain--workorder-picking-consumption-returns)
2. [Catalog/Pricing Domain – Product, UOM, Cost Tiers](#catalogpricing-domain--product-uom-cost-tiers)
3. [Accounting Domain – Inventory Event Posting](#accounting-domain--inventory-event-posting)
4. [Organization Domain – Locations and Defaults](#organization-domain--locations-and-defaults)
5. [Order/Receiving Domain – PO/ASN Intake](#orderreceiving-domain--poasn-intake)
6. [Security Domain – Permission Registration](#security-domain--permission-registration)
7. [Integration Guidelines](#integration-guidelines)
8. [Status Tracking](#status-tracking)

---

## Workexec Domain – Workorder Picking, Consumption, Returns

### Context & Use Cases
- Issue #244/#243/#242: Picking, consuming, and returning parts against a
  workorder.
- Inventory must reserve, decrement, and return stock while Workexec owns the
  workorder lifecycle and labor context.

### Required Contracts

**Service Names (Proposed):**

```
workexec.list#PickingList           or GET /v1/workexec/workorders/{workorderId}/picking-list
workexec.execute#PickLines          or POST /v1/workexec/workorders/{workorderId}/pick
workexec.execute#ConsumeParts       or POST /v1/workexec/workorders/{workorderId}/consume
workexec.execute#ReturnParts        or POST /v1/workexec/workorders/{workorderId}/return
```

**Pick/Consume/Return Request (REST):**

```json
{
  "workorderId": "WO-12345",
  "lines": [
    {
      "lineNumber": 1,
      "productId": "SKU-ABC",
      "quantity": "2",
      "uom": "EA",
      "locationId": "LOC-001",
      "sourceReservationId": "RSV-456"    
    }
  ],
  "performedBy": "user-001",
  "performedAt": "2026-01-25T10:30:00Z",
  "idempotencyKey": "WO-12345:picking:1"
}
```

**Response (REST):**

```json
{
  "workorderId": "WO-12345",
  "status": "PICKED",
  "lines": [
    {
      "lineNumber": 1,
      "productId": "SKU-ABC",
      "quantity": "2",
      "uom": "EA",
      "locationId": "LOC-001",
      "reservationId": "RSV-456",
      "inventoryTransactionId": "INV-TXN-789"
    }
  ],
  "correlationId": "corr-uuid",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `WORKORDER_NOT_FOUND` | 404 | workorderId not found or inaccessible |
| `WORKORDER_STATE_INVALID` | 400 | workorder not in a state that allows pick/consume/return |
| `INVENTORY_INSUFFICIENT` | 409 | Available quantity < requested quantity |
| `RESERVATION_NOT_FOUND` | 404 | Source reservation missing or expired |
| `LOCATION_INVALID` | 400 | locationId not valid/active for organization |
| `IDEMPOTENCY_CONFLICT` | 409 | idempotencyKey already processed with different payload |

**Ownership & Implementation**
- **Owned By:** Workexec domain for workflow + state; Inventory domain for stock
  movements, reservations, and availability.
- **Dependencies:** Inventory availability/reservation services; Organization
  location master data; Security permissions (inventory:picking:*).

---

## Catalog/Pricing Domain – Product, UOM, Cost Tiers

### Context & Use Cases
- Issue #121/#120/#119/#260: Product creation/validation, UOM conversions,
  lifecycle status, and cost tier retrieval for valuation.

### Required Contracts

**Service Names (Proposed):**

```
catalog.get#Product                 or GET /v1/catalog/products/{productId}
catalog.list#Products               or GET /v1/catalog/products
catalog.get#UomConversions          or GET /v1/catalog/uom-conversions/{productId}
catalog.get#ProductLifecycle        or GET /v1/catalog/products/{productId}/lifecycle
pricing.get#ProductCostTiers        or GET /v1/pricing/products/{productId}/cost-tiers
```

**Product Response (REST excerpt):**

```json
{
  "productId": "SKU-ABC",
  "productType": "STOCK",
  "uom": "EA",
  "lifecycleStatus": "ACTIVE",
  "description": "Example part",
  "defaultLocationId": "LOC-001",
  "uomConversions": [
    { "from": "EA", "to": "BOX", "factor": "12" }
  ]
}
```

**Cost Tier Response (REST excerpt):**

```json
{
  "productId": "SKU-ABC",
  "costTiers": [
    { "tier": "STANDARD", "unitCost": "12.50", "currency": "USD" },
    { "tier": "LAST_RECEIVED", "unitCost": "12.10", "currency": "USD" }
  ],
  "effectiveDate": "2026-01-25"
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `PRODUCT_NOT_FOUND` | 404 | productId does not exist |
| `PRODUCT_INACTIVE` | 400 | lifecycleStatus not ACTIVE |
| `UOM_CONVERSION_NOT_FOUND` | 404 | conversion path missing |
| `COST_TIER_NOT_FOUND` | 404 | no cost tier for product/organization |

**Ownership & Implementation**
- **Owned By:** Catalog/Pricing domains.
- **Dependencies:** Organization (business unit/location scoping), Inventory (uses
  product IDs and conversions).

---

## Accounting Domain – Inventory Event Posting

### Context & Use Cases
- Inventory must emit events for receipts, adjustments, and consumption/return to
  allow accrual and COGS postings.

### Required Contracts

**Event Types (align with Accounting catalog):**
- `inventory.stockReceived`
- `inventory.inventoryAdjustment`
- `inventory.partsConsumed`
- `inventory.partsReturned`

**Event Schema (REST/Message):**

```json
{
  "eventId": "evt-uuid",
  "eventType": "inventory.partsConsumed",
  "sourceSystem": "inventory-service",
  "organizationId": "org-001",
  "transactionDate": "2026-01-25",
  "payload": {
    "workorderId": "WO-12345",
    "lineItems": [
      {
        "productId": "SKU-ABC",
        "quantity": "2",
        "uom": "EA",
        "unitCost": "12.50",
        "locationId": "LOC-001",
        "reservationId": "RSV-456"
      }
    ]
  },
  "dimensions": {
    "businessUnitId": "BU-001",
    "locationId": "LOC-001",
    "costCenterId": "CC-OPS"
  },
  "metadata": {
    "idempotencyKey": "WO-12345:consume:1",
    "correlationId": "corr-uuid",
    "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
  }
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `MAPPING_NOT_FOUND` | 404 | GL mapping missing for dimensions/product |
| `DIMENSION_NOT_FOUND` | 400 | Organization dimension invalid/inactive |
| `EVENT_ID_CONFLICT` | 409 | Duplicate eventId or idempotencyKey |

**Ownership & Implementation**
- **Owned By:** Accounting domain for ingestion and posting.
- **Dependencies:** Inventory emits events with required dimensions and mapping
  keys per Accounting event catalog.

---

## Organization Domain – Locations and Defaults

### Context & Use Cases
- Inventory operations require valid locations, hierarchy, and site defaults for
  putaway/picking.

### Required Contracts

**Service Names (Proposed):**

```
organization.get#Location             or GET /v1/organization/locations/{locationId}
organization.list#Locations           or GET /v1/organization/locations
organization.get#SiteDefaults         or GET /v1/organization/sites/{siteId}/inventory-defaults
```

**Location Response (REST excerpt):**

```json
{
  "locationId": "LOC-001",
  "status": "ACTIVE",
  "parentLocationId": "LOC-ROOT",
  "locationType": "WAREHOUSE",
  "allowsInventory": true,
  "businessUnitId": "BU-001"
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `LOCATION_NOT_FOUND` | 404 | locationId not found |
| `LOCATION_INACTIVE` | 400 | location not ACTIVE |

**Ownership & Implementation**
- **Owned By:** Organization domain.
- **Dependencies:** Security for access scoping; Inventory consumes for validation
  and defaulting.

---

## Order/Receiving Domain – PO/ASN Intake

### Context & Use Cases
- Inventory receipts rely on purchase order and ASN data for matching and
  valuation.

### Required Contracts

**Service Names (Proposed):**

```
order.get#PurchaseOrder              or GET /v1/order/purchase-orders/{purchaseOrderId}
order.get#AdvanceShipNotice          or GET /v1/order/asn/{asnId}
```

**PO/ASN Response (REST excerpt):**

```json
{
  "purchaseOrderId": "PO-123",
  "supplierId": "VEND-001",
  "lines": [
    {
      "lineNumber": 1,
      "productId": "SKU-ABC",
      "orderedQuantity": "10",
      "uom": "EA",
      "unitCost": "12.00",
      "expectedReceiptDate": "2026-01-26"
    }
  ]
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `PURCHASE_ORDER_NOT_FOUND` | 404 | purchaseOrderId not found |
| `ASN_NOT_FOUND` | 404 | asnId not found |

**Ownership & Implementation**
- **Owned By:** Order/Receiving domain.
- **Dependencies:** Inventory consumes for receipt validation and cost capture;
  Accounting consumes via `inventory.stockReceived` events.

---

## Security Domain – Permission Registration

### Required Permissions (Inventory)
- `inventory:availability:query`
- `inventory:cyclecount:plan`
- `inventory:cyclecount:submit`
- `inventory:cyclecount:approve`
- `inventory:adjustment:create`
- `inventory:adjustment:approve`
- `inventory:adjustment:override`
- `inventory:picking:list`
- `inventory:picking:execute`
- `inventory:consume:execute`
- `inventory:return:execute`
- `inventory:receiving:create`
- `inventory:receiving:execute`
- `inventory:location:view`
- `inventory:location:manage`
- `inventory:roles:define`

### Contract Expectations
- Permissions registered in pos-security-service with location/org scoping.
- JWT tokens must carry organizationId, permissions, and trace context.
- Inventory endpoints enforce deny-by-default; Workexec calls must present
  inventory permissions or trusted client role.

---

## Integration Guidelines

- **Authentication:** OAuth2/JWT with organizationId, permissions, and trace
  context. Service-to-service calls must use signed service credentials.
- **Idempotency:** All mutating endpoints accept `Idempotency-Key` header or
  `idempotencyKey` field; duplicates with mismatched payload return 409.
- **Correlation:** Include `correlationId` and W3C `traceparent` on all requests
  and emitted events.
- **Pagination:** Default pageSize 50, max 200 for list endpoints.
- **Error Schema:** Deterministic codes with message and timestamp; prefer
  `errorCode`, `message`, `timestamp`, `fields`.
- **Retries:** Clients retry on 503/timeout with exponential backoff; avoid
  retrying 4xx.
- **Caching:** Allow caching of reference data (product, location) for up to
  5 minutes; respect `etag`/`last-modified` when provided.
- **Validation Order:** Validate organization/location → product/lifecycle →
  permissions → stock/reservation → accounting event emission.

---

## Status Tracking

| Contract Area | Status | Owner | Notes |
|---------------|--------|-------|-------|
| Workexec picking/consume/return | Draft | Workexec + Inventory | Align with workorder states |
| Catalog/Product + Pricing | Draft | Catalog/Pricing | UOM + cost tiers required |
| Accounting events | Draft | Accounting | Event catalog alignment needed |
| Organization locations/defaults | Draft | Organization | Validate location scoping |
| Order/Receiving PO/ASN | Draft | Order | Required for receipts |
| Security permissions | Draft | Security | Register inventory:* strings |
