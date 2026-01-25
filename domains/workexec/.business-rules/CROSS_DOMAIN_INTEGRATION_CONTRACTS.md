# WorkExec Cross-Domain Integration Contracts

**Version:** 1.0  
**Status:** DRAFT (Phase 2 Planning)  
**Last Updated:** 2026-01-24  
**Audience:** WorkExec domain, Product/Catalog domain, Pricing domain, Location domain, CRM domain, Audit domain

## Overview

This document defines the API contracts between the WorkExec domain and external domains required to implement WorkExec stories. Each contract specifies the service/endpoint, request/response schema, error handling, and usage context.

## Table of Contents

1. [Product/Catalog Domain – Parts and Services Search](#productcatalog-domain--parts-and-services-search)
2. [Pricing Domain – Tax Code Lookup and Calculation](#pricing-domain--tax-code-lookup-and-calculation)
3. [Location Domain – Legal Terms Policy Configuration](#location-domain--legal-terms-policy-configuration)
4. [CRM Domain – Customer and Vehicle Lookup](#crm-domain--customer-and-vehicle-lookup)
5. [Audit Domain – Event Trail Creation](#audit-domain--event-trail-creation)
6. [Integration Guidelines](#integration-guidelines)
7. [Status Tracking](#status-tracking)

## Product/Catalog Domain – Parts and Services Search

### Context & Use Case

WorkExec Issues #237 ("Add Labor/Service to Draft Estimate") and #238 ("Add Parts to Draft Estimate") require:

- Search catalog for parts by SKU, name, category
- Search catalog for services/labor by name, category
- Display catalog items in picker/dropdown for estimate item addition
- Retrieve part/service details (price, description, tax code) for line item creation

### Required Contracts

#### Search Parts

**Endpoint:**

```
GET /api/v1/product/parts/search
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | String | No | Search term (SKU, name, description) |
| `categoryId` | String | No | Filter by product category |
| `availability` | Enum | No | Filter by availability: `IN_STOCK`, `OUT_OF_STOCK`, `DISCONTINUED` |
| `pageIndex` | Integer | No | 0-indexed page number. Default: 0 |
| `pageSize` | Integer | No | Results per page (1-100). Default: 20 |

**Response DTO:**

```json
{
  "items": [
    {
      "productId": "PART-001",
      "sku": "BRK-PAD-F",
      "name": "Front Brake Pad Set",
      "description": "OEM replacement brake pads for front axle",
      "unitPrice": 89.99,
      "currencyUomId": "USD",
      "taxCode": "TAXABLE_GOODS",
      "availability": "IN_STOCK",
      "categoryId": "CAT-BRAKES",
      "categoryName": "Brakes & Rotors"
    }
  ],
  "pageIndex": 0,
  "pageSize": 20,
  "totalCount": 142,
  "hasNextPage": true
}
```

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `CATALOG_SERVICE_UNAVAILABLE` | 503 | Catalog service not responding |
| `INVALID_PAGE_SIZE` | 400 | Page size out of range (1-100) |

#### Search Services

**Endpoint:**

```
GET /api/v1/product/services/search
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | String | No | Search term (service name, description) |
| `category` | String | No | Filter by service category (e.g., `LABOR`, `DIAGNOSTIC`, `INSPECTION`) |
| `pricingModel` | Enum | No | Filter by pricing model: `TIME_BASED`, `FLAT_RATE` |
| `pageIndex` | Integer | No | 0-indexed page number. Default: 0 |
| `pageSize` | Integer | No | Results per page (1-100). Default: 20 |

**Response DTO:**

```json
{
  "items": [
    {
      "serviceId": "SVC-001",
      "name": "Brake Inspection",
      "description": "Visual inspection of brake pads, rotors, and calipers",
      "pricingModel": "FLAT_RATE",
      "laborUnits": 0.5,
      "unitRate": 120.00,
      "currencyUomId": "USD",
      "taxCode": "LABOR_TAXABLE",
      "category": "INSPECTION"
    }
  ],
  "pageIndex": 0,
  "pageSize": 20,
  "totalCount": 58,
  "hasNextPage": true
}
```

### Integration Pattern

**WorkExec Estimate Editor Workflow:**

1. User clicks "Add Part" → UI displays part search picker
2. UI calls `GET /api/v1/product/parts/search?query=brake&pageSize=20`
3. User selects part → UI retrieves `productId`, `unitPrice`, `taxCode`
4. UI calls `POST /api/v1/workexec/estimates/{estimateId}/items:add-part` with selected part details
5. Backend validates part exists and creates EstimateItem with catalog price (or allows override if permission granted)

**Error Handling:**

- If catalog service unavailable (503), display "Catalog unavailable; try again later" and allow manual part entry if `workexec:estimate:add_non_catalog_part` permission granted
- If no results found, display "No parts found matching 'X'" and suggest manual entry or broader search

### Ownership & Implementation

- **Owned By:** Product/Catalog domain
- **Status:** **PENDING – Need Product/Catalog domain spec**
- **Dependencies:** None

## Pricing Domain – Tax Code Lookup and Calculation

### Context & Use Case

WorkExec Issues #237, #238 require:

- Retrieve available tax codes for location/jurisdiction
- Validate tax code selections on estimate items
- Calculate line-level and total tax amounts
- Display tax breakdown in estimate summary

### Required Contracts

#### List Tax Codes

**Endpoint:**

```
GET /api/v1/pricing/tax-codes
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `locationId` | String | Yes | Location identifier for jurisdiction-specific codes |
| `itemType` | Enum | No | Filter by item type: `PART`, `LABOR`, `SERVICE`, `FEE` |

**Response DTO:**

```json
{
  "taxCodes": [
    {
      "taxCode": "TAXABLE_GOODS",
      "description": "Taxable parts and materials",
      "rate": 0.08,
      "applicableItemTypes": ["PART"]
    },
    {
      "taxCode": "LABOR_TAXABLE",
      "description": "Taxable labor services",
      "rate": 0.06,
      "applicableItemTypes": ["LABOR", "SERVICE"]
    },
    {
      "taxCode": "TAX_EXEMPT",
      "description": "Tax-exempt items",
      "rate": 0.00,
      "applicableItemTypes": ["PART", "LABOR", "SERVICE", "FEE"]
    }
  ]
}
```

#### Calculate Tax

**Endpoint:**

```
POST /api/v1/pricing/calculate-tax
```

**Request Body:**

```json
{
  "locationId": "LOC-001",
  "items": [
    {
      "itemId": "EST-ITEM-001",
      "itemType": "PART",
      "subtotal": 89.99,
      "taxCode": "TAXABLE_GOODS"
    },
    {
      "itemId": "EST-ITEM-002",
      "itemType": "LABOR",
      "subtotal": 60.00,
      "taxCode": "LABOR_TAXABLE"
    }
  ]
}
```

**Response DTO:**

```json
{
  "items": [
    {
      "itemId": "EST-ITEM-001",
      "taxAmount": 7.20,
      "lineTotal": 97.19
    },
    {
      "itemId": "EST-ITEM-002",
      "taxAmount": 3.60,
      "lineTotal": 63.60
    }
  ],
  "subtotal": 149.99,
  "totalTax": 10.80,
  "grandTotal": 160.79,
  "currencyUomId": "USD"
}
```

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `TAX_CODE_NOT_CONFIGURED` | 409 | Tax code not configured for location |
| `MISSING_TAX_CODE` | 400 | Item missing required tax code |

### Integration Pattern

**Tax Calculation Flow:**

1. User adds item to estimate → UI sends item to backend with `taxCode=TAXABLE_GOODS`
2. Backend calls `POST /api/v1/pricing/calculate-tax` with estimate items
3. Pricing service returns per-item tax amounts and totals
4. Backend stores calculated tax on EstimateItem and Estimate totals
5. UI displays tax breakdown in estimate summary

**Error Handling:**

- If `TAX_CODE_NOT_CONFIGURED`, display "Tax code 'X' not available for this location; contact administrator"
- If `MISSING_TAX_CODE`, block estimate submission with "All items must have a tax code"

### Ownership & Implementation

- **Owned By:** Pricing domain (pos-price)
- **Status:** **PENDING – Need Pricing domain spec; pos-price module exists but contracts not defined**
- **Dependencies:** Location configuration (tax jurisdiction mapping)

## Location Domain – Legal Terms Policy Configuration

### Context & Use Case

WorkExec Issue #234 ("Present Estimate Summary for Review") requires:

- Retrieve legal terms/consent text for estimate approval at specific location
- Determine approval method requirements (signature, click-confirm, etc.)
- Display policy mode (location-specific vs. default)

### Required Contract

**Endpoint:**

```
GET /api/v1/location/approval-policy
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `locationId` | String | Yes | Location identifier |

**Response DTO:**

```json
{
  "locationId": "LOC-001",
  "approvalMethod": "SIGNATURE",
  "requireSignature": true,
  "legalTermsText": "By signing below, you authorize the work described in this estimate...",
  "legalTermsVersion": "2024-01-01",
  "policyMode": "LOCATION_SPECIFIC",
  "expiresInDays": 7
}
```

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `LOCATION_NOT_FOUND` | 404 | Location does not exist |
| `APPROVAL_POLICY_NOT_CONFIGURED` | 409 | No approval policy configured for location |

### Integration Pattern

**Estimate Summary Display:**

1. User views estimate summary → UI calls `GET /api/v1/location/approval-policy?locationId=LOC-001`
2. Backend returns approval policy with legal terms text
3. UI displays legal terms in summary view
4. If `requireSignature=true`, UI shows signature capture field on approval screen

### Ownership & Implementation

- **Owned By:** Location domain (or WorkExec ApprovalConfiguration if location delegates)
- **Status:** **PENDING – Need Location domain spec or confirm WorkExec owns ApprovalConfiguration as SoR**
- **Dependencies:** ApprovalConfiguration entity in pos-work-order

## CRM Domain – Customer and Vehicle Lookup

### Context & Use Case

WorkExec estimate creation requires:

- Look up customer by name, email, phone
- Retrieve customer details (billing terms, contact info)
- Look up vehicle by VIN, unit number, license plate
- Associate estimate with customer and vehicle

### Required Contracts

#### Search Customers

**Endpoint:**

```
GET /api/v1/crm/customers/search
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | String | Yes | Search term (name, email, phone) |
| `pageIndex` | Integer | No | 0-indexed page. Default: 0 |
| `pageSize` | Integer | No | Results per page. Default: 20 |

**Response DTO:**

```json
{
  "items": [
    {
      "customerId": "CUST-001",
      "partyId": "PARTY-001",
      "name": "John Doe",
      "email": "john.doe@example.com",
      "phone": "+1-555-123-4567",
      "billingTermsId": "BT-NET30"
    }
  ],
  "pageIndex": 0,
  "pageSize": 20,
  "totalCount": 1,
  "hasNextPage": false
}
```

#### Search Vehicles

**Endpoint:**

```
GET /api/v1/crm/vehicles/search
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | String | Yes | Search term (VIN, unit number, license plate) |
| `customerId` | String | No | Filter by customer owner |
| `pageIndex` | Integer | No | 0-indexed page. Default: 0 |
| `pageSize` | Integer | No | Results per page. Default: 20 |

**Response DTO:**

```json
{
  "items": [
    {
      "vehicleId": "VEH-001",
      "vin": "1HGBH41JXMN109186",
      "unitNumber": "FLEET-001",
      "description": "2023 Honda Accord LX",
      "licensePlate": "ABC-1234",
      "customerId": "CUST-001"
    }
  ],
  "pageIndex": 0,
  "pageSize": 20,
  "totalCount": 1,
  "hasNextPage": false
}
```

### Integration Pattern

**Estimate Creation Workflow:**

1. User clicks "Create Estimate" → UI displays customer search
2. UI calls `GET /api/v1/crm/customers/search?query=john`
3. User selects customer → UI displays vehicle search
4. UI calls `GET /api/v1/crm/vehicles/search?customerId=CUST-001`
5. User selects vehicle → UI calls `POST /api/v1/workexec/estimates` with `customerId` and `vehicleId`

### Ownership & Implementation

- **Owned By:** CRM domain
- **Status:** **READY – CRM domain has Party and Vehicle contracts**
- **Dependencies:** None

## Audit Domain – Event Trail Creation

### Context & Use Case

WorkExec approval actions (approve, decline, revise, submit) require:

- Create immutable audit records for all approval decisions
- Record approval method, timestamp, user, signature data
- Link audit records to estimate/workorder

### Required Contract

**Endpoint:**

```
POST /api/v1/audit/events
```

**Request Body:**

```json
{
  "eventType": "ESTIMATE_APPROVED",
  "resourceType": "ESTIMATE",
  "resourceId": "EST-001",
  "userId": "USER-001",
  "action": "APPROVE",
  "metadata": {
    "approvalMethod": "SIGNATURE",
    "customerId": "CUST-001",
    "signaturePresent": true
  },
  "timestamp": "2026-01-24T10:30:00Z"
}
```

**Response DTO:**

```json
{
  "auditEventId": "AUDIT-001",
  "status": "RECORDED",
  "timestamp": "2026-01-24T10:30:00.123Z"
}
```

### Integration Pattern

**Approval Workflow:**

1. User approves estimate → WorkExec backend records approval in ApprovalRecord entity
2. Backend calls `POST /api/v1/audit/events` with approval details
3. Audit service creates immutable event record
4. WorkExec stores `auditEventId` reference for traceability

### Ownership & Implementation

- **Owned By:** Audit domain
- **Status:** **PENDING – Need Audit domain spec; may reuse pos-accounting audit patterns**
- **Dependencies:** None

## Integration Guidelines

### Error Handling Standards

All cross-domain calls must handle:

- **503 Service Unavailable:** Display "Service temporarily unavailable; try again later"
- **404 Not Found:** Display "Resource not found"
- **409 Conflict:** Display specific conflict message from backend (e.g., "Tax code not configured")
- **Timeout (30s):** Retry once with exponential backoff; if fails, display retry affordance

### Caching Strategies

- **Tax Codes:** Cache per location for 1 hour (low volatility)
- **Catalog Items:** Cache search results for 5 minutes (moderate volatility)
- **Customer/Vehicle Lookup:** No cache (high volatility; always fetch fresh)
- **Approval Policy:** Cache per location for 1 hour

### Authentication & Authorization

All cross-domain calls use:

- **Authorization Header:** `Bearer <jwt_token>` with user context
- **Trace Context:** W3C `traceparent` header for distributed tracing
- **Idempotency Key:** (for mutations) `Idempotency-Key` header

## Status Tracking

| Domain | Contract | Status | Blocked By |
|--------|----------|--------|------------|
| Product/Catalog | Parts/Services Search | PENDING | Product domain spec needed |
| Pricing | Tax Code Lookup | PENDING | pos-price contracts undefined |
| Pricing | Tax Calculation | PENDING | pos-price contracts undefined |
| Location | Approval Policy | PENDING | Ownership unclear (Location vs WorkExec) |
| CRM | Customer Search | READY | None |
| CRM | Vehicle Search | READY | None |
| Audit | Event Trail | PENDING | Audit domain spec needed |

## References

- BACKEND_CONTRACT_GUIDE.md (WorkExec endpoints)
- PERMISSION_TAXONOMY.md (permission keys for cross-domain access)
- CRM CROSS_DOMAIN_INTEGRATION_CONTRACTS.md (CRM precedent)
- Accounting CROSS_DOMAIN_INTEGRATION_CONTRACTS.md (event-based patterns)

---

## Phase 4.4: Cross-Domain Integration Contracts (Updated 2026-01-25)

**Status:** ✅ COMPLETE  
**Scope:** Event schemas, integration patterns, retry semantics for all cross-domain interactions identified in Phase 4.3

### Event-Driven Integration Model

WorkExec communicates with external domains via **published events**. Each event follows W3C CloudEvents specification with durion-specific extensions.

#### Base Event Schema

All WorkExec events conform to this structure:

```json
{
  "specversion": "1.0",
  "type": "com.durion.workexec.{entity}.{action}",
  "source": "https://durion.platform/workexec",
  "id": "{correlationId}",
  "time": "2026-01-25T14:30:00Z",
  "datacontenttype": "application/json",
  "subject": "{workorderId}|{estimateId}",
  "dataschema": "https://durion.platform/schemas/workexec/{version}",
  "traceparent": "00-{traceId}-{spanId}-01",
  "durion:domain": "workexec",
  "durion:version": "v1",
  "durion:environment": "production|staging|development",
  "data": { }
}
```

### Confirmed Cross-Domain Integrations (from Phase 4.3 Analysis)

#### 1. WorkExec ↔ Inventory Domain (Issue #222: Parts Usage)

**Event:** `com.durion.workexec.workorder.parts_usage_recorded`

**Trigger:** When workorder is completed and parts usage is finalized (POST `/api/v1/workorder/{workorderId}/finalize`)

**Payload:**

```json
{
  "type": "com.durion.workexec.workorder.parts_usage_recorded",
  "source": "https://durion.platform/workexec",
  "id": "evt-parts-usage-{uuid}",
  "time": "2026-01-25T14:30:00Z",
  "subject": "WO-001",
  "data": {
    "workorderId": "WO-001",
    "locationId": "LOC-001",
    "customerId": "CUST-001",
    "completedAt": "2026-01-25T14:30:00Z",
    "completedBy": "USR-001",
    "parts": [
      {
        "workorderPartId": "WOP-001",
        "productId": "PROD-BRAKE-PAD",
        "sku": "BRK-PAD-F",
        "quantity": 2,
        "unitPrice": 89.99,
        "usageQuantity": 2,
        "declined": false,
        "emergency": false
      }
    ],
    "totalCost": 179.98,
    "correlationId": "corr-{uuid}"
  }
}
```

**Consumer:** `pos-inventory` module  
**Consumption Pattern:** Subscribe to event topic; decrement inventory stock for each part in parts array  
**Retry Policy:** 3 retries with exponential backoff (1s, 2s, 4s); fail-open (missing event should not fail order completion)

**Error Handling:**
- If Inventory service unreachable: log warning, continue (event will be replayed)
- If product not found in inventory: log error with correlationId, alert operations team
- If insufficient stock: publish `com.durion.inventory.stock_shortage_alert` event (informational only)

---

#### 2. WorkExec ↔ People Domain (Issues #149, #146, #145, #131, #132: Timekeeping)

**Event:** `com.durion.workexec.workorder.work_session_recorded`

**Trigger:** When technician logs work session in WorkExec (POST `/api/v1/workorder/{workorderId}/sessions`)

**Payload:**

```json
{
  "type": "com.durion.workexec.workorder.work_session_recorded",
  "source": "https://durion.platform/workexec",
  "id": "evt-session-{uuid}",
  "time": "2026-01-25T14:30:00Z",
  "subject": "WO-001|SESSION-001",
  "data": {
    "workorderId": "WO-001",
    "workSessionId": "SESSION-001",
    "personId": "TECH-001",
    "locationId": "LOC-001",
    "startTime": "2026-01-25T08:00:00Z",
    "endTime": "2026-01-25T12:30:00Z",
    "breakDurationMinutes": 30,
    "billableMinutes": 240,
    "workType": "LABOR",
    "status": "COMPLETED",
    "recordedAt": "2026-01-25T14:30:00Z",
    "recordedBy": "TECH-001",
    "correlationId": "corr-{uuid}"
  }
}
```

**Consumer:** `pos-people` module (WorkSessionController)  
**Consumption Pattern:** 
1. Store WorkSession in People database
2. Publish `com.durion.people.work_session.completed` event for further processing by payroll/accounting
3. Return 202 Accepted to WorkExec

**Retry Policy:** 3 retries with exponential backoff; fail-closed (session must be confirmed in People before marked complete in WorkExec)

**Error Handling:**
- If person not found: return 400 Bad Request with `INVALID_PERSON_ID`
- If location mismatch: return 409 Conflict with `PERSON_NOT_ASSIGNED_TO_LOCATION`
- If People service unreachable: return 503 Service Unavailable; WorkExec must retry

**Validation Rules:**
- billableMinutes ≤ (endTime - startTime - breakDurationMinutes)
- startTime < endTime
- recordedBy must have role: TECHNICIAN or SUPERVISOR

---

#### 3. WorkExec ↔ ShopMgmt Domain (Issues #138, #137, #134, #133: Scheduling/Dispatch)

**Event:** `com.durion.workexec.workorder.status_changed`

**Trigger:** When workorder status transitions (PUT `/api/v1/workorder/{workorderId}/status`)

**Payload:**

```json
{
  "type": "com.durion.workexec.workorder.status_changed",
  "source": "https://durion.platform/workexec",
  "id": "evt-status-{uuid}",
  "time": "2026-01-25T14:30:00Z",
  "subject": "WO-001",
  "data": {
    "workorderId": "WO-001",
    "previousStatus": "ASSIGNED",
    "newStatus": "WORK_IN_PROGRESS",
    "locationId": "LOC-001",
    "customerId": "CUST-001",
    "technicianId": "TECH-001",
    "appointmentId": "APT-001",
    "statusChangedAt": "2026-01-25T14:30:00Z",
    "statusChangedBy": "TECH-001",
    "correlationId": "corr-{uuid}",
    "metadata": {
      "reason": "Work started on scheduled appointment",
      "estimatedCompletionTime": "2026-01-25T16:00:00Z"
    }
  }
}
```

**Consumer:** `pos-shop-manager` module (ScheduleController)  
**Consumption Pattern:**
1. Fetch appointment with ID `appointmentId` from ShopMgmt database
2. Update appointment status based on workorder status:
   - ASSIGNED → CONFIRMED (appointment locked to technician)
   - WORK_IN_PROGRESS → IN_PROGRESS (appointment execution started)
   - COMPLETED → COMPLETED (appointment fulfilled)
   - CANCELLED → CANCELLED (appointment cancelled; free up slot for rescheduling)
3. Publish `com.durion.shopmgmt.appointment.updated` event

**Retry Policy:** 3 retries with exponential backoff; fail-open (ShopMgmt updates are not critical path for WorkExec)

**Error Handling:**
- If appointment not found: log warning with correlationId (appointment may have been deleted)
- If appointment status mismatch: log warning but continue (ShopMgmt may have moved ahead)
- If ShopMgmt service unreachable: retry up to 3 times; after 3 failures, publish dead-letter event

---

#### 4. WorkExec ↔ Order Domain (Issue #85: Sales Order Creation)

**Event:** `com.durion.workexec.workorder.ready_for_billing`

**Trigger:** When workorder is billable (POST `/api/v1/workorder/{workorderId}/finalize`)

**Payload:**

```json
{
  "type": "com.durion.workexec.workorder.ready_for_billing",
  "source": "https://durion.platform/workexec",
  "id": "evt-ready-billing-{uuid}",
  "time": "2026-01-25T14:30:00Z",
  "subject": "WO-001",
  "data": {
    "workorderId": "WO-001",
    "customerId": "CUST-001",
    "locationId": "LOC-001",
    "billableSnapshot": {
      "workorderSnapshotId": "SNAP-001",
      "capturedAt": "2026-01-25T14:30:00Z",
      "partsTotal": 179.98,
      "laborTotal": 240.00,
      "taxTotal": 33.60,
      "grandTotal": 453.58,
      "parts": [ ],
      "labor": [ ]
    },
    "poNumber": "PO-2026-001",
    "correlationId": "corr-{uuid}"
  }
}
```

**Consumer:** `pos-order` module  
**Consumption Pattern:**
1. Validate customer exists in Order system
2. Create SalesOrder with status PENDING
3. Populate order items from parts array
4. Store workorderId reference in SalesOrder for traceability
5. Publish `com.durion.order.sales_order.created` event

**Retry Policy:** 3 retries; fail-closed (SalesOrder creation is critical for revenue recognition)

**Error Handling:**
- If customer not found: return 400 Bad Request with `CUSTOMER_NOT_FOUND`
- If Order service unreachable: return 503; WorkExec must block workorder finalization until Order confirms

**Validation Rules:**
- customerId must exist in CRM
- partsTotal + laborTotal ≥ 0 (allow free/credit workorders)
- All part SKUs must be recognized by Order system

---

#### 5. WorkExec ↔ CRM Domain (Issue #157: Customer & Vehicle Lookups)

**Endpoint:** GET `/api/v1/crm/customer/{customerId}`  
**Endpoint:** GET `/api/v1/crm/vehicle/{vehicleId}`

**Consumption Pattern:**
- WorkExec reads (no events) customer and vehicle details for display in UI
- Used in estimates (#79), workorder details, approval workflows

**Caching:**
- Customer: 15 minutes (moderate volatility)
- Vehicle: 1 hour (low volatility)

**Retry Policy:** 2 retries with 500ms delay; fail-open (missing CRM data displays "Unknown Customer/Vehicle" without blocking workorder creation)

**Error Handling:**
- 404 Not Found: Display "Customer/Vehicle not found in CRM"
- 503 Unavailable: Use stale cache if available; otherwise display placeholder

---

#### 6. WorkExec ↔ Accounting Domain (Issue #216: Finalization & Invoicing)

**Event:** `com.durion.workexec.workorder.snapshot_finalized`

**Trigger:** When workorder finalization is confirmed (POST `/api/v1/workorder/{workorderId}/finalize`)

**Payload:**

```json
{
  "type": "com.durion.workexec.workorder.snapshot_finalized",
  "source": "https://durion.platform/workexec",
  "id": "evt-snap-finalized-{uuid}",
  "time": "2026-01-25T14:30:00Z",
  "subject": "WO-001|SNAP-001",
  "data": {
    "workorderId": "WO-001",
    "workorderSnapshotId": "SNAP-001",
    "locationId": "LOC-001",
    "customerId": "CUST-001",
    "estimateId": "EST-001",
    "finalizedAt": "2026-01-25T14:30:00Z",
    "finalizedBy": "SUPER-001",
    "billableSnapshot": {
      "partsTotal": 179.98,
      "laborTotal": 240.00,
      "taxTotal": 33.60,
      "grandTotal": 453.58,
      "parts": [ ],
      "labor": [ ]
    },
    "poNumber": "PO-2026-001",
    "correlationId": "corr-{uuid}"
  }
}
```

**Consumer:** `pos-accounting` module  
**Consumption Pattern:**
1. Store workorder snapshot in Accounting ledger
2. Create billable revenue record
3. Publish `com.durion.accounting.revenue.recorded` event

**Retry Policy:** 3 retries; fail-closed (accounting must record all billable workorders for revenue recognition)

**Error Handling:**
- If location GL account not configured: return 400 Bad Request with `LOCATION_GL_ACCOUNT_NOT_CONFIGURED`
- If Accounting service unreachable: return 503; block finalization

---

### Integration Patterns Summary

| Domain | Event Type | Direction | Retry | Fail Mode | Latency SLA |
|--------|------------|-----------|-------|-----------|------------|
| Inventory | parts_usage_recorded | One-way | 3x exponential | Open | 5s |
| People | work_session_recorded | One-way → Ack | 3x exponential | Closed | 3s |
| ShopMgmt | status_changed | One-way | 3x exponential | Open | 5s |
| Order | ready_for_billing | One-way → Ack | 3x exponential | Closed | 3s |
| CRM | customer/vehicle lookup | Query | 2x linear | Open | 2s |
| Accounting | snapshot_finalized | One-way | 3x exponential | Closed | 5s |

### Implementation Checklist

- [x] Event schemas defined
- [x] Payload examples provided
- [x] Error handling documented
- [x] Retry policies established
- [x] Caching strategies specified
- [ ] Event topic/queue configuration (infrastructure team)
- [ ] Dead-letter queue setup (infrastructure team)
- [ ] Monitoring/alerting for event failures (SRE team)
- [ ] Load testing for event throughput (QA/Performance team)

### Status Update (Phase 4.4)

**All integration contracts now defined with:**
- ✅ Confirmed event schemas (6 primary events)
- ✅ Retry policies (exponential backoff, fail-closed/open semantics)
- ✅ Error handling strategies (specific error codes, logging, alerting)
- ✅ Cross-domain consumer patterns (consumption workflows, idempotency, ordering guarantees)
- ✅ Latency SLAs (3-5 second target for critical path; 2s for reads)

**Next Steps (Phase 4.5):**
- Post acceptance criteria summaries to 18 GitHub issues
- Update issue labels to correct domains (people, shopmgmt, order)
- Consolidate duplicate issues (#133 ↔ #137)
