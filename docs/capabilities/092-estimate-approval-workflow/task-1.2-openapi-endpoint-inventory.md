# Task 1.2: OpenAPI Endpoint Inventory - Estimate Approval Workflow

**Source**: `/home/louisb/Projects/durion-positivity-backend/pos-workorder/openapi.json`  
**Date**: February 13, 2026  
**Analyzed By**: Coder Agent

---

## Executive Summary

This document provides a complete inventory of estimate-related endpoints extracted from the pos-workorder OpenAPI specification. The analysis identifies **8 estimate-specific endpoints** and **1 workorder endpoint** that directly interacts with estimates.

### Key Findings

1. ✅ **Core CRUD operations implemented**: Create, Read (multiple query types), Delete
2. ✅ **Lifecycle operations implemented**: Approve, Decline, Reopen
3. ❌ **MISSING**: No "Submit for Approval" endpoint (referenced in manifest requirements)
4. ⚠️ **Naming Convention Violation**: Tag uses "Work Order API" (two words) instead of "Workorder API" (one word)
5. ✅ **Customer signature capture**: Implemented in approval flow
6. ✅ **Purchase Order support**: Implemented in approval flow (CAP:092 Story #98)
7. ✅ **Line item approval**: Supported via `LineItemApprovalDto` in approval request

---

## 1. Estimate-Related Endpoints

### 1.1 POST `/v1/workorders/estimates`

**Operation ID**: `createEstimate`  
**Tags**: Estimate API  
**Summary**: Create a new draft estimate  
**Description**: Create a new estimate in DRAFT status for a customer and vehicle. Requires ESTIMATE_CREATE permission. System will generate a unique estimate number and apply default values for location, currency, and tax region if not provided.

#### Request Headers

| Header | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| X-User-Id | UUID | No | `00000000-0000-0000-0000-000000000001` | ID of the user creating the estimate |

#### Request Body

**Content-Type**: `application/json`  
**Schema**: `CreateEstimateRequest`

```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "vehicleId": "550e8400-e29b-41d4-a716-446655440001",
  "locationId": "550e8400-e29b-41d4-a716-446655440002",
  "currencyUomId": "USD",
  "taxRegionId": "550e8400-e29b-41d4-a716-446655440003"
}
```

**Required Fields**: `customerId`, `vehicleId`

#### Response Codes

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Estimate created successfully | Empty object `{}` |
| 400 | Invalid request - missing required fields | Empty object `{}` |
| 403 | Forbidden - user does not have ESTIMATE_CREATE permission | Empty object `{}` |
| 500 | Internal server error - estimate creation failed | Empty object `{}` |

**⚠️ Issue**: Success response (200) returns empty object instead of `EstimateResponse`

---

### 1.2 GET `/v1/workorders/estimates`

**Operation ID**: `getAllEstimates`  
**Tags**: Estimate API  
**Summary**: Get all estimates  
**Description**: Retrieve a list of all estimates.

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | List of estimates returned successfully | Array of `EstimateResponse` |

---

### 1.3 GET `/v1/workorders/estimates/{estimateId}`

**Operation ID**: `getEstimateById`  
**Tags**: Estimate API  
**Summary**: Get estimate by ID  
**Description**: Retrieve an estimate by its unique ID.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| estimateId | UUID | Yes | ID of the estimate to retrieve | `550e8400-e29b-41d4-a716-446655440000` |

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Estimate found and returned | `EstimateResponse` |
| 404 | Estimate not found | `EstimateResponse` |

---

### 1.4 DELETE `/v1/workorders/estimates/{estimateId}`

**Operation ID**: `deleteEstimate`  
**Tags**: Estimate API  
**Summary**: Delete an estimate  
**Description**: Delete an estimate by its unique ID.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| estimateId | UUID | Yes | ID of the estimate to delete | `550e8400-e29b-41d4-a716-446655440000` |

#### Response

| Status | Description |
|--------|-------------|
| 204 | Estimate deleted successfully |
| 404 | Estimate not found |

---

### 1.5 POST `/v1/workorders/estimates/{estimateId}/approval`

**Operation ID**: `approveEstimate`  
**Tags**: Estimate API  
**Summary**: Approve an estimate with customer signature  
**Description**: Transition estimate to approved state with customer signature capture. Estimate can be approved from DRAFT status. Requires customer ID validation and signature data (base64-encoded image). For commercial accounts with PO enforcement enabled, a purchase order number must be provided (CAP:092 Story #98).

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| estimateId | UUID | Yes | ID of the estimate to approve | `550e8400-e29b-41d4-a716-446655440000` |

#### Request Body

**Content-Type**: `application/json`  
**Schema**: `ApproveEstimateRequest`

```json
{
  "customerId": "12345",
  "signatureData": "data:image/png;base64,iVBORw0KGgoAAAANS...",
  "signatureMimeType": "image/png",
  "signerName": "John Doe",
  "notes": "Approved with customer present",
  "lineItemApprovals": [
    {
      "lineItemId": 123,
      "approved": true,
      "rejectionReason": "Customer declined optional service",
      "notes": "Customer wants to get second opinion"
    }
  ],
  "purchaseOrderNumber": "PO-2024-12345"
}
```

**Required Fields**: `customerId`

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Estimate approved successfully with signature captured | `EstimateResponse` |
| 400 | Estimate cannot be approved in current state, customer ID mismatch, or PO required but not provided | `EstimateResponse` |
| 404 | Estimate not found | `EstimateResponse` |

---

### 1.6 POST `/v1/workorders/estimates/{estimateId}/decline`

**Operation ID**: `declineEstimate`  
**Tags**: Estimate API  
**Summary**: Decline an estimate  
**Description**: Transition estimate to declined state. Estimate can be declined from DRAFT or APPROVED status.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| estimateId | UUID | Yes | ID of the estimate to decline | `550e8400-e29b-41d4-a716-446655440000` |

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| reason | string | No | Reason for decline |

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Estimate declined successfully | `EstimateResponse` |
| 400 | Estimate cannot be declined in current state | `EstimateResponse` |
| 404 | Estimate not found | `EstimateResponse` |

---

### 1.7 POST `/v1/workorders/estimates/{estimateId}/reopen`

**Operation ID**: `reopenEstimate`  
**Tags**: Estimate API  
**Summary**: Reopen a declined estimate  
**Description**: Transition a declined estimate back to DRAFT state. Can only be done within the configured expiry period.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| estimateId | UUID | Yes | ID of the estimate to reopen | `550e8400-e29b-41d4-a716-446655440000` |

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Estimate reopened successfully | `EstimateResponse` |
| 400 | Estimate cannot be reopened (not declined or expired) | `EstimateResponse` |
| 404 | Estimate not found | `EstimateResponse` |

---

### 1.8 GET `/v1/workorders/estimates/shop/{locationId}`

**Operation ID**: `getEstimatesByShop`  
**Tags**: Estimate API  
**Summary**: Get estimates by shop  
**Description**: Retrieve all estimates for a specific shop.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| locationId | UUID | Yes | ID of the shop | `1` |

**⚠️ Issue**: Example shows integer `1` but schema expects UUID

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | List of estimates returned successfully | Array of `EstimateResponse` |

---

### 1.9 GET `/v1/workorders/estimates/location/{locationId}`

**Operation ID**: `getEstimatesByLocation`  
**Tags**: Estimate API  
**Summary**: Get estimates by location  
**Description**: Retrieve all estimates for a specific location.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| locationId | UUID | Yes | ID of the location | `1` |

**⚠️ Issue**: Example shows integer `1` but schema expects UUID

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | List of estimates returned successfully | Array of `EstimateResponse` |

---

### 1.10 GET `/v1/workorders/estimates/customer/{customerId}`

**Operation ID**: `getEstimatesByCustomer`  
**Tags**: Estimate API  
**Summary**: Get estimates by customer  
**Description**: Retrieve all estimates for a specific customer.

#### Path Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| customerId | UUID | Yes | ID of the customer | `1` |

**⚠️ Issue**: Example shows integer `1` but schema expects UUID

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | List of estimates returned successfully | Array of `EstimateResponse` |

---

## 2. Workorder Endpoints That Interact With Estimates

### 2.1 POST `/v1/workorders`

**Operation ID**: `createWorkorder`  
**Tags**: Work Order API  
**Summary**: Create a new work order  
**Description**: Add a new work order to the system.

**⚠️ Naming Violation**: Tag uses "Work Order API" (two words) instead of "Workorder API" (one word)

#### Request Body

**Content-Type**: `application/json`  
**Schema**: `CreateWorkorderRequest`

```json
{
  "estimateId": "550e8400-e29b-41d4-a716-446655440001",
  "customerId": "550e8400-e29b-41d4-a716-446655440002"
}
```

**Required Fields**: `estimateId`, `customerId`

**Note**: This endpoint creates a workorder from an approved estimate. The `estimateId` is a required field, establishing the relationship between estimates and workorders.

#### Response

| Status | Description | Schema |
|--------|-------------|--------|
| 200 | Work order created successfully | `WorkorderResponse` |

---

## 3. Data Models / Schema Definitions

### 3.1 CreateEstimateRequest

**Description**: Estimate creation request with customer and vehicle IDs

```json
{
  "type": "object",
  "properties": {
    "customerId": {
      "type": "string",
      "format": "uuid"
    },
    "vehicleId": {
      "type": "string",
      "format": "uuid"
    },
    "locationId": {
      "type": "string",
      "format": "uuid"
    },
    "currencyUomId": {
      "type": "string"
    },
    "taxRegionId": {
      "type": "string",
      "format": "uuid"
    }
  },
  "required": ["customerId", "vehicleId"]
}
```

---

### 3.2 EstimateResponse

**Description**: Response DTO for estimates

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the estimate",
      "example": "550e8400-e29b-41d4-a716-446655440000"
    },
    "estimateNumber": {
      "type": "string",
      "description": "Estimate number",
      "example": "EST-2024-1000"
    },
    "customerId": {
      "type": "string",
      "format": "uuid",
      "description": "Customer ID",
      "example": "550e8400-e29b-41d4-a716-446655440001"
    },
    "vehicleId": {
      "type": "string",
      "format": "uuid",
      "description": "Vehicle ID",
      "example": "550e8400-e29b-41d4-a716-446655440002"
    },
    "locationId": {
      "type": "string",
      "format": "uuid",
      "description": "Location ID",
      "example": "550e8400-e29b-41d4-a716-446655440003"
    },
    "currencyUomId": {
      "type": "string",
      "description": "Currency UOM ID",
      "example": "USD"
    },
    "taxRegionId": {
      "type": "string",
      "format": "uuid",
      "description": "Tax region ID",
      "example": "550e8400-e29b-41d4-a716-446655440004"
    },
    "status": {
      "type": "string",
      "description": "Estimate status",
      "example": "DRAFT"
    },
    "createdByUserId": {
      "type": "string",
      "format": "uuid",
      "description": "User ID who created the estimate",
      "example": "550e8400-e29b-41d4-a716-446655440005"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Date and time the estimate was created"
    }
  }
}
```

**⚠️ Missing Fields**: Based on typical estimate workflows, the following fields are likely missing:
- `updatedAt` (timestamp)
- `approvedAt` (timestamp)
- `approvedByUserId` (UUID)
- `declinedAt` (timestamp)
- `declineReason` (string)
- `expiresAt` (timestamp)
- `signatureUrl` (string)
- `purchaseOrderNumber` (string)
- `totalAmount` (decimal)
- `lineItems` (array)

---

### 3.3 ApproveEstimateRequest

**Description**: Approval request with customer ID and signature capture

```json
{
  "type": "object",
  "properties": {
    "customerId": {
      "type": "string",
      "format": "uuid",
      "description": "Customer ID who is approving the estimate",
      "example": 12345
    },
    "signatureData": {
      "type": "string",
      "description": "Base64-encoded signature image data (PNG format recommended)",
      "example": "data:image/png;base64,iVBORw0KGgoAAAANS..."
    },
    "signatureMimeType": {
      "type": "string",
      "description": "MIME type of signature (e.g., image/png, image/jpeg)",
      "example": "image/png"
    },
    "signerName": {
      "type": "string",
      "description": "Name of person providing signature",
      "example": "John Doe"
    },
    "notes": {
      "type": "string",
      "description": "Additional notes or comments",
      "example": "Approved with customer present"
    },
    "lineItemApprovals": {
      "type": "array",
      "description": "Individual line item approvals/rejections. If omitted, all items are considered approved.",
      "items": {
        "$ref": "#/components/schemas/LineItemApprovalDto"
      }
    },
    "purchaseOrderNumber": {
      "type": "string",
      "description": "Purchase order number (required when PO enforcement is enabled for the account)",
      "example": "PO-2024-12345"
    }
  },
  "required": ["customerId"]
}
```

**⚠️ Issue**: `customerId` example shows integer `12345` but type is UUID

---

### 3.4 LineItemApprovalDto

**Description**: Approval or rejection status for a specific line item

```json
{
  "type": "object",
  "properties": {
    "lineItemId": {
      "type": "integer",
      "format": "int64",
      "description": "ID of the line item (service/product) being approved or rejected",
      "example": 123
    },
    "approved": {
      "type": "boolean",
      "description": "Whether this line item is approved (true) or rejected (false)",
      "example": true
    },
    "rejectionReason": {
      "type": "string",
      "description": "Reason for rejection (required if approved=false)",
      "example": "Customer declined optional service"
    },
    "notes": {
      "type": "string",
      "description": "Additional notes about this line item decision",
      "example": "Customer wants to get second opinion"
    }
  },
  "required": ["lineItemId", "approved"]
}
```

---

### 3.5 CreateWorkorderRequest

**Description**: Work order creation request

```json
{
  "type": "object",
  "properties": {
    "estimateId": {
      "type": "string",
      "format": "uuid",
      "description": "Estimate ID",
      "example": "550e8400-e29b-41d4-a716-446655440001"
    },
    "customerId": {
      "type": "string",
      "format": "uuid",
      "description": "Customer ID",
      "example": "550e8400-e29b-41d4-a716-446655440002"
    }
  },
  "required": ["estimateId", "customerId"]
}
```

---

### 3.6 WorkorderResponse

**Description**: Response DTO for workorders

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique identifier for the workorder",
      "example": "550e8400-e29b-41d4-a716-446655440000"
    },
    "estimateId": {
      "type": "string",
      "format": "uuid",
      "description": "Estimate ID",
      "example": "550e8400-e29b-41d4-a716-446655440001"
    },
    "customerId": {
      "type": "string",
      "format": "uuid",
      "description": "Customer ID",
      "example": "550e8400-e29b-41d4-a716-446655440002"
    },
    "shopId": {
      "type": "string",
      "format": "uuid",
      "description": "Shop ID",
      "example": "550e8400-e29b-41d4-a716-446655440003"
    },
    "vehicleId": {
      "type": "string",
      "format": "uuid",
      "description": "Vehicle ID",
      "example": "550e8400-e29b-41d4-a716-446655440004"
    },
    "status": {
      "type": "string",
      "description": "Workorder status",
      "example": "DRAFT"
    },
    "approvedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Date and time the workorder was approved"
    },
    "completedAt": {
      "type": "string",
      "format": "date-time",
      "description": "Date and time the workorder was completed"
    }
  }
}
```

---

## 4. Status Codes & Error Handling

### 4.1 HTTP Status Codes Used

| Status Code | Usage Context | Meaning |
|-------------|--------------|---------|
| 200 | Success responses | Request processed successfully |
| 204 | Delete estimate | Estimate deleted successfully (no content) |
| 400 | Client errors | Invalid request, invalid state transition, missing required fields |
| 403 | Authorization | User lacks required permission (e.g., ESTIMATE_CREATE) |
| 404 | Not found | Estimate or resource not found |
| 500 | Server errors | Internal server error during processing |

### 4.2 Common Error Scenarios

#### Estimate Creation Errors

- **400**: Missing required fields (`customerId`, `vehicleId`)
- **403**: User lacks `ESTIMATE_CREATE` permission
- **500**: Estimate creation failed (system error)

#### State Transition Errors

- **400**: Invalid state transition (e.g., cannot approve from DECLINED)
- **400**: Customer ID mismatch during approval
- **400**: PO required but not provided (commercial accounts)
- **400**: Cannot reopen estimate (not declined or expired)
- **404**: Estimate not found

---

## 5. Estimate Status Values (Inferred from Descriptions)

While no explicit enum is defined in the OpenAPI spec, the following status values are referenced in endpoint descriptions:

| Status | Description | Valid Transitions To |
|--------|-------------|---------------------|
| `DRAFT` | Initial state after creation | APPROVED, DECLINED |
| `APPROVED` | Customer has approved with signature | DECLINED, (→ Workorder creation) |
| `DECLINED` | Customer declined the estimate | DRAFT (via reopen, within expiry period) |

**❌ MISSING**: No status for "PENDING_APPROVAL" or "SUBMITTED_FOR_APPROVAL" (referenced in manifest)

---

## 6. Identified Gaps & Issues

### 6.1 Critical Gaps

1. **❌ Missing "Submit for Approval" Endpoint**
   - Manifest references "submit estimate for approval"
   - No corresponding endpoint in OpenAPI
   - May need: `POST /v1/workorders/estimates/{estimateId}/submit`

2. **❌ Missing Status: PENDING_APPROVAL / SUBMITTED**
   - Workflow typically requires: DRAFT → SUBMITTED → APPROVED
   - Current spec shows: DRAFT → APPROVED (direct transition)

3. **❌ Missing Enum Definitions**
   - No `EstimateStatus` enum defined in components/schemas
   - Makes it difficult to enforce valid status values

### 6.2 Data Model Issues

1. **⚠️ CreateEstimate Response**
   - Returns empty object `{}` on success (200)
   - Should return `EstimateResponse` with created estimate details

2. **⚠️ EstimateResponse Missing Fields**
   - No `approvedAt`, `approvedByUserId`
   - No `declinedAt`, `declineReason`
   - No `updatedAt`, `expiresAt`
   - No `signatureUrl` or signature metadata
   - No `totalAmount`, `lineItems`

3. **⚠️ Type Inconsistencies**
   - `ApproveEstimateRequest.customerId` example shows integer but type is UUID
   - Query endpoints (by shop/location/customer) show integer examples but expect UUID

### 6.3 Naming Convention Violations

1. **⚠️ "Work Order API" Tag**
   - Uses "Work Order" (two words)
   - Should be "Workorder" (one word)
   - Affects: `POST /v1/workorders`, `GET /v1/workorders`, all workorder endpoints

2. **⚠️ OpenAPI Field Descriptions**
   - Throughout descriptions: "work order" appears as two words
   - Should be: "workorder" (one word)

### 6.4 Missing Permission Documentation

- `ESTIMATE_CREATE` permission mentioned for creation
- No documentation of other permissions:
  - `ESTIMATE_APPROVE` / `ESTIMATE_APPROVAL`?
  - `ESTIMATE_DECLINE`?
  - `ESTIMATE_REOPEN`?
  - `ESTIMATE_DELETE`?
  - `ESTIMATE_VIEW` / `ESTIMATE_READ`?

---

## 7. API Contract Validation Against Manifest Requirements

### 7.1 Manifest Requirement: Estimate Lifecycle

| Manifest Operation | OpenAPI Endpoint | Status |
|-------------------|------------------|--------|
| Create estimate (DRAFT) | `POST /v1/workorders/estimates` | ✅ Implemented |
| Submit for approval | ❌ NOT FOUND | ❌ Missing |
| Approve estimate | `POST /v1/workorders/estimates/{id}/approval` | ✅ Implemented |
| Decline estimate | `POST /v1/workorders/estimates/{id}/decline` | ✅ Implemented |
| Reopen declined estimate | `POST /v1/workorders/estimates/{id}/reopen` | ✅ Implemented |

### 7.2 Manifest Requirement: Customer Signature

| Feature | OpenAPI Implementation | Status |
|---------|----------------------|--------|
| Capture signature on approval | `ApproveEstimateRequest.signatureData` (base64) | ✅ Implemented |
| Store signature MIME type | `ApproveEstimateRequest.signatureMimeType` | ✅ Implemented |
| Capture signer name | `ApproveEstimateRequest.signerName` | ✅ Implemented |
| Signature validation | Not documented in OpenAPI | ⚠️ Unknown |

### 7.3 Manifest Requirement: Purchase Order Enforcement (CAP:092 Story #98)

| Feature | OpenAPI Implementation | Status |
|---------|----------------------|--------|
| PO number field | `ApproveEstimateRequest.purchaseOrderNumber` | ✅ Implemented |
| PO enforcement logic | Described: "required when PO enforcement is enabled for the account" | ✅ Documented |
| PO validation error | 400: "PO required but not provided" | ✅ Implemented |

### 7.4 Manifest Requirement: Line Item Approval

| Feature | OpenAPI Implementation | Status |
|---------|----------------------|--------|
| Individual line item approval/rejection | `ApproveEstimateRequest.lineItemApprovals[]` | ✅ Implemented |
| Line item rejection reason | `LineItemApprovalDto.rejectionReason` | ✅ Implemented |
| Default behavior (all approved if omitted) | Documented in description | ✅ Implemented |

---

## 8. Recommendations

### 8.1 High Priority

1. **Add Submit Endpoint**
   ```
   POST /v1/workorders/estimates/{estimateId}/submit
   ```
   - Transition estimate from DRAFT to PENDING_APPROVAL/SUBMITTED
   - Add corresponding status value to enum

2. **Fix CreateEstimate Response**
   - Change 200 response from `{}` to `EstimateResponse`
   - Ensures clients receive created estimate details

3. **Add Status Enum**
   ```json
   "EstimateStatus": {
     "type": "string",
     "enum": ["DRAFT", "SUBMITTED", "APPROVED", "DECLINED"]
   }
   ```

4. **Fix Naming Convention Violations**
   - Change "Work Order API" tag to "Workorder API"
   - Update all descriptions to use "workorder" (one word)

### 8.2 Medium Priority

5. **Enhance EstimateResponse**
   - Add missing timestamp fields: `approvedAt`, `declinedAt`, `updatedAt`, `expiresAt`
   - Add audit fields: `approvedByUserId`, `declineReason`
   - Add signature metadata: `signatureUrl`
   - Add financial fields: `totalAmount`, `subtotal`, `taxAmount`
   - Add line items: `lineItems[]`

6. **Fix Type Inconsistencies**
   - Correct examples in parameter descriptions (use UUID format consistently)

7. **Document Permissions**
   - Create comprehensive permission matrix for all estimate operations

### 8.3 Low Priority

8. **Add Pagination**
   - Add pagination parameters to list endpoints:
     - `GET /v1/workorders/estimates`
     - `GET /v1/workorders/estimates/shop/{locationId}`
     - `GET /v1/workorders/estimates/location/{locationId}`
     - `GET /v1/workorders/estimates/customer/{customerId}`

9. **Add Filtering/Search**
   - Add query parameters for filtering by status, date ranges, amounts

---

## 9. Conclusion

The OpenAPI specification provides a solid foundation for estimate management with **8 estimate-specific endpoints** covering:
- ✅ Creation, retrieval, deletion
- ✅ Approval workflow with signature capture
- ✅ Decline/reopen lifecycle
- ✅ PO enforcement for commercial accounts
- ✅ Line item-level approval/rejection

**Critical Missing Features:**
- ❌ Submit for approval endpoint
- ❌ PENDING_APPROVAL/SUBMITTED status
- ⚠️ Naming convention violations ("Work Order" vs "Workorder")
- ⚠️ Incomplete response schemas

**Next Steps:**
- Compare with existing contract guide documentation
- Identify additional gaps not visible in OpenAPI
- Plan implementation for missing "submit" operation
- Fix naming convention violations across API surface
