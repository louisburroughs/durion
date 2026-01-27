# Inventory Management Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-inventory/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Inventory Management domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.

---

## Table of Contents

1. [JSON Field Naming Conventions](#json-field-naming-conventions)
2. [Data Types & Formats](#data-types--formats)
3. [Enum Value Conventions](#enum-value-conventions)
4. [Identifier Naming](#identifier-naming)
5. [Timestamp Conventions](#timestamp-conventions)
6. [Collection & Pagination](#collection--pagination)
7. [Error Response Format](#error-response-format)
8. [Correlation ID & Request Tracking](#correlation-id--request-tracking)
9. [API Endpoints](#api-endpoints)
10. [Entity-Specific Contracts](#entity-specific-contracts)
11. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "id": "abc-123",
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z",
  "status": "ACTIVE"
}
```

### Rationale

- Aligns with JSON/JavaScript convention
- Matches Java property naming after Jackson deserialization
- Consistent with REST API best practices (RFC 7231)
- Consistent across all Durion platform domains

---

## Data Types & Formats

### String Fields

Use `string` type for:

- Names and descriptions
- Codes and identifiers
- Free-form text
- Enum values (serialized as strings)

```java
private String id;
private String name;
private String description;
private String status;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts (page numbers, total results)
- Version numbers
- Sequence numbers

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
```

### Boolean Fields

Use `boolean` for true/false flags:

```java
private boolean isActive;
private boolean isPrimary;
private boolean hasPermission;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs:

```java
private String id;
private String parentId;
private String referenceId;
```

### Instant/Timestamp Fields

Use `Instant` in Java; serialize to ISO 8601 UTC in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
private Instant updatedAt;
```

JSON representation:

```json
{
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z"
}
```

### LocalDate Fields

Use `LocalDate` for date-only fields (no time component):

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
private LocalDate effectiveFrom;
private LocalDate effectiveTo;
```

JSON representation:

```json
{
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31"
}
```

---

## Enum Value Conventions

### Standard Pattern: UPPER_SNAKE_CASE

All enum values **MUST** use `UPPER_SNAKE_CASE`:

```java
public enum Status {
    ACTIVE,
    INACTIVE,
    PENDING_APPROVAL,
    ARCHIVED
}
```

### Enums in this Domain

#### AdjustmentResponse.requiredApprovalTier

- `TIER_1_MANAGER`
- `TIER_2_DIRECTOR`

#### AdjustmentResponse.status

- `PENDING_APPROVAL`
- `AUTO_APPROVED`
- `APPROVED`
- `POSTED`
- `REJECTED`
- `FAILED`

#### CountResponse.taskStatus

- `ASSIGNED`
- `COUNTED_PENDING_REVIEW`
- `REQUIRES_INVESTIGATION`
- `APPROVED`
- `REJECTED`

#### CycleCountTask.status

- `ASSIGNED`
- `COUNTED_PENDING_REVIEW`
- `REQUIRES_INVESTIGATION`
- `APPROVED`
- `REJECTED`

---

## Identifier Naming

### Standard Pattern

- Primary keys: `id` or `{entity}Id` (e.g., `customerId`, `orderId`)
- Foreign keys: `{entity}Id` (e.g., `parentId`, `accountId`)
- Composite identifiers: use structured object, not concatenated string

### Examples

```json
{
  "id": "abc-123",
  "customerId": "cust-456",
  "orderId": "ord-789"
}
```

---

## Timestamp Conventions

### Standard Pattern: ISO 8601 UTC

All timestamps **MUST** be:

- Serialized in ISO 8601 format with UTC timezone (`Z` suffix)
- Stored as `Instant` in Java
- Include millisecond precision when available

```json
{
  "createdAt": "2026-01-27T14:30:00.123Z",
  "updatedAt": "2026-01-27T15:45:30.456Z"
}
```

### Common Timestamp Fields

- `createdAt`: When the entity was created
- `updatedAt`: When the entity was last updated
- `deletedAt`: When the entity was soft-deleted (if applicable)
- `effectiveFrom`: Start date for effective dating
- `effectiveTo`: End date for effective dating

---

## Collection & Pagination

### Standard Pagination Request

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "sortField": "createdAt",
  "sortOrder": "DESC"
}
```

### Standard Pagination Response

```json
{
  "results": [...],
  "totalCount": 150,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 8
}
```

### Guidelines

- Use zero-based page numbering
- Default page size: 20 items
- Maximum page size: 100 items
- Include total count for client-side pagination controls

---

## Error Response Format

### Standard Error Response

All error responses **MUST** follow this format:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "correlationId": "abc-123-def-456",
  "timestamp": "2026-01-27T14:30:00Z",
  "fieldErrors": [
    {
      "field": "email",
      "message": "Invalid email format",
      "rejectedValue": "invalid-email"
    }
  ]
}
```

### Standard HTTP Status Codes

- `200 OK`: Successful GET, PUT, PATCH
- `201 Created`: Successful POST
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Validation error
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation
- `422 Unprocessable Entity`: Semantic validation error
- `500 Internal Server Error`: Unexpected server error
- `501 Not Implemented`: Endpoint not yet implemented

---

## Correlation ID & Request Tracking

### X-Correlation-Id Header

All API requests **SHOULD** include an `X-Correlation-Id` header for distributed tracing:

```http
GET /v1/inventory/entities/123
X-Correlation-Id: abc-123-def-456
```

### Response Headers

All API responses **MUST** echo the correlation ID:

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456
```

### Error Responses

All error responses **MUST** include the correlation ID in the body:

```json
{
  "code": "NOT_FOUND",
  "message": "Entity not found",
  "correlationId": "abc-123-def-456"
}
```

**Reference:** See `DECISION-INVENTORY-012` in domain AGENT_GUIDE.md for correlation ID standards.

---

## API Endpoints

### Endpoint Summary

This domain exposes **18** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/api/inventory/cycleCount/auditor/{auditorId}/tasks` | Get tasks assigned to an auditor |
| POST | `/api/inventory/cycleCount/recount` | Submit a recount for a cycle count task |
| POST | `/api/inventory/cycleCount/submit` | Submit a count for a cycle count task |
| GET | `/api/inventory/cycleCount/task/{taskId}` | Get cycle count task details |
| GET | `/api/inventory/cycleCount/task/{taskId}/history` | Get count history for a task |
| POST | `/api/inventory/locations/{locationId}/deactivate` |  |
| GET | `/api/v1/inventory/cycleCountAdjustments` | List adjustments by status |
| POST | `/api/v1/inventory/cycleCountAdjustments` | Create cycle count adjustment |
| GET | `/api/v1/inventory/cycleCountAdjustments/pending` | List pending approvals |
| GET | `/api/v1/inventory/cycleCountAdjustments/pending/count` | Count pending approvals |
| GET | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}` | Get adjustment details |
| POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve` | Approve adjustment |
| POST | `/api/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject` | Reject adjustment |
| GET | `/v1/inventory/availability/{productId}` | Query inventory availability |
| POST | `/v1/inventory/availability/{productId}` | Update inventory availability |
| POST | `/v1/inventory/pickingLists/{id}/confirm` | Confirm picking list |
| GET | `/v1/inventory/sites/{siteId}/defaultLocations` | Get site default locations |
| PUT | `/v1/inventory/sites/{siteId}/defaultLocations` | Replace site default locations |

### Endpoint Details

#### GET /api/inventory/cycleCount/auditor/{auditorId}/tasks

**Summary:** Get tasks assigned to an auditor

**Description:** Retrieves all cycle count tasks assigned to a specific auditor.

**Operation ID:** `getAuditorTasks`

**Parameters:**

- `auditorId` (path, Required, string): Auditor ID

**Responses:**

- `200`: Tasks retrieved successfully


---

#### POST /api/inventory/cycleCount/recount

**Summary:** Submit a recount for a cycle count task

**Description:** Records a recount with permission validation and limit enforcement. Maximum 2 recounts allowed (3 total counts).

**Operation ID:** `submitRecount`

**Responses:**

- `200`: Recount submitted successfully
- `400`: Invalid request or recount limit exceeded
- `403`: Insufficient permission
- `404`: Task not found


---

#### POST /api/inventory/cycleCount/submit

**Summary:** Submit a count for a cycle count task

**Description:** Records the actual quantity counted by an auditor. Calculates variance and updates task status.

**Operation ID:** `submitCount`

**Responses:**

- `200`: Count submitted successfully
- `400`: Invalid request or quantity
- `404`: Task not found


---

#### GET /api/inventory/cycleCount/task/{taskId}

**Summary:** Get cycle count task details

**Description:** Retrieves details of a specific cycle count task.

**Operation ID:** `getTask`

**Parameters:**

- `taskId` (path, Required, string): Task ID

**Responses:**

- `200`: Task retrieved successfully
- `404`: Task not found


---

#### GET /api/inventory/cycleCount/task/{taskId}/history

**Summary:** Get count history for a task

**Description:** Retrieves all count entries (original + recounts) for a task, ordered by sequence.

**Operation ID:** `getCountHistory`

**Parameters:**

- `taskId` (path, Required, string): Task ID

**Responses:**

- `200`: History retrieved successfully


---

#### POST /api/inventory/locations/{locationId}/deactivate

**Operation ID:** `deactivate`

**Parameters:**

- `locationId` (path, Required, string): 

**Responses:**

- `200`: OK


---

#### GET /api/v1/inventory/cycleCountAdjustments

**Summary:** List adjustments by status

**Description:** Lists all adjustments matching the specified status

**Operation ID:** `listAdjustments`

**Parameters:**

- `status` (query, Optional, string): Filter by adjustment status

**Responses:**

- `200`: Adjustments retrieved


---

#### POST /api/v1/inventory/cycleCountAdjustments

**Summary:** Create cycle count adjustment

**Description:** Creates a new adjustment from a cycle count. Automatically evaluates against approval thresholds.

**Operation ID:** `createAdjustment`

**Responses:**

- `201`: Adjustment created successfully
- `400`: Invalid request or no variance detected


---

#### GET /api/v1/inventory/cycleCountAdjustments/pending

**Summary:** List pending approvals

**Description:** Lists all adjustments awaiting approval

**Operation ID:** `listPendingApprovals`

**Responses:**

- `200`: Pending adjustments retrieved


---

#### GET /api/v1/inventory/cycleCountAdjustments/pending/count

**Summary:** Count pending approvals

**Description:** Returns the count of adjustments awaiting approval

**Operation ID:** `countPendingApprovals`

**Responses:**

- `200`: Count retrieved


---

#### GET /api/v1/inventory/cycleCountAdjustments/{adjustmentId}

**Summary:** Get adjustment details

**Description:** Retrieves details of a specific cycle count adjustment

**Operation ID:** `getAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment found
- `404`: Adjustment not found


---

#### POST /api/v1/inventory/cycleCountAdjustments/{adjustmentId}/approve

**Summary:** Approve adjustment

**Description:** Approves a pending adjustment and posts it to the inventory ledger

**Operation ID:** `approveAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment approved and posted
- `400`: Adjustment not found or not in approvable state
- `403`: User lacks required approval permission


---

#### POST /api/v1/inventory/cycleCountAdjustments/{adjustmentId}/reject

**Summary:** Reject adjustment

**Description:** Rejects a pending adjustment with a reason. No inventory changes are made.

**Operation ID:** `rejectAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, integer): Adjustment ID

**Responses:**

- `200`: Adjustment rejected
- `400`: Adjustment not found or not in rejectable state
- `403`: User lacks required approval permission


---

#### GET /v1/inventory/availability/{productId}

**Summary:** Query inventory availability

**Description:** Returns availability for a product. Stub implementation.

**Operation ID:** `queryInventoryAvailability`

**Parameters:**

- `productId` (path, Required, string): Product identifier

**Responses:**

- `200`: Availability returned
- `501`: Not implemented


---

#### POST /v1/inventory/availability/{productId}

**Summary:** Update inventory availability

**Description:** Updates availability for a product. Stub implementation.

**Operation ID:** `updateInventoryAvailability`

**Parameters:**

- `productId` (path, Required, string): Product identifier

**Responses:**

- `200`: Availability updated
- `501`: Not implemented


---

#### POST /v1/inventory/pickingLists/{id}/confirm

**Summary:** Confirm picking list

**Description:** Confirms a picking list and commits consumption. Stub implementation.

**Operation ID:** `confirmPickingList`

**Parameters:**

- `id` (path, Required, string): Picking list identifier

**Responses:**

- `200`: Picking list confirmed
- `501`: Not implemented


---

#### GET /v1/inventory/sites/{siteId}/defaultLocations

**Summary:** Get site default locations

**Description:** Returns configured default locations for a site. Stub implementation.

**Operation ID:** `getSiteDefaultLocations`

**Parameters:**

- `siteId` (path, Required, string): Site identifier

**Responses:**

- `200`: Default locations returned
- `501`: Not implemented


---

#### PUT /v1/inventory/sites/{siteId}/defaultLocations

**Summary:** Replace site default locations

**Description:** Replaces the configured default locations for a site. Stub implementation.

**Operation ID:** `putSiteDefaultLocations`

**Parameters:**

- `siteId` (path, Required, string): Site identifier

**Responses:**

- `200`: Default locations replaced
- `501`: Not implemented



---

## Entity-Specific Contracts

### AdjustmentResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `adjustmentId` | integer (int64) | No |  |
| `approvedAt` | string (date-time) | No |  |
| `approvedByUserId` | string | No |  |
| `costAtTimeOfAdjustment` | number | No |  |
| `countedQuantity` | integer (int32) | No |  |
| `createdAt` | string (date-time) | No |  |
| `createdByUserId` | string | No |  |
| `errorMessage` | string | No |  |
| `ledgerEntryId` | integer (int64) | No |  |
| `postedAt` | string (date-time) | No |  |
| `quantityChange` | integer (int32) | No |  |
| `quantityOnHandBefore` | integer (int32) | No |  |
| `reasonCode` | string | No |  |
| `rejectedAt` | string (date-time) | No |  |
| `rejectedByUserId` | string | No |  |
| `rejectionReason` | string | No |  |
| `requiredApprovalTier` | string | No |  |
| `status` | string | No |  |
| `stockItemId` | string | No |  |
| `updatedAt` | string (date-time) | No |  |
| `variancePercentage` | number | No |  |
| `varianceValue` | number | No |  |


### ApproveAdjustmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `approverUserId` | string | Yes |  |
| `notes` | string | No |  |


### CountEntry

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actualQuantity` | integer (int32) | No |  |
| `auditorId` | string | No |  |
| `countEntryId` | string (uuid) | No |  |
| `countedAt` | string (date-time) | No |  |
| `cycleCountTaskId` | string (uuid) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `recount` | boolean | No |  |
| `recountOfCountEntryId` | string (uuid) | No |  |
| `recountSequenceNumber` | integer (int32) | No |  |
| `variance` | integer (int32) | No |  |


### CountResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actualQuantity` | integer (int32) | No |  |
| `countEntryId` | string (uuid) | No |  |
| `countedAt` | string (date-time) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `limitExceeded` | boolean | No |  |
| `message` | string | No |  |
| `recountSequenceNumber` | integer (int32) | No |  |
| `taskId` | string (uuid) | No |  |
| `taskStatus` | string | No |  |
| `variance` | integer (int32) | No |  |


### CreateAdjustmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `costAtTimeOfAdjustment` | number | Yes |  |
| `countedQuantity` | integer (int32) | Yes |  |
| `createdByUserId` | string | Yes |  |
| `quantityOnHandBefore` | integer (int32) | Yes |  |
| `reasonCode` | string | Yes |  |
| `stockItemId` | string | Yes |  |


### CycleCountTask

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `auditorId` | string | No |  |
| `binLocation` | string | No |  |
| `countEntriesCount` | integer (int32) | No |  |
| `createdAt` | string (date-time) | No |  |
| `expectedQuantity` | integer (int32) | No |  |
| `itemDescription` | string | No |  |
| `itemSku` | string | No |  |
| `latestCountEntryId` | string (uuid) | No |  |
| `status` | string | No |  |
| `taskId` | string (uuid) | No |  |
| `updatedAt` | string (date-time) | No |  |


### DeactivateLocationRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `destinationLocationId` | string (uuid) | No |  |


### DeactivateLocationResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `destinationLocationId` | string (uuid) | No |  |
| `sourceLocationId` | string (uuid) | No |  |
| `status` | string | No |  |
| `transfer` | string | No |  |


### InventoryAvailabilityResponse

Inventory availability response including on-hand, allocations, and ATP

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `allocatedQty` | number | Yes | Total quantity allocated (hard commitments) |
| `asOfTimestamp` | string (date-time) | Yes | Timestamp when this calculation was performed |
| `atpQty` | number | Yes | Available-to-promise quantity (On-Hand - Allocations) |
| `expectedReceiptsQty` | number | No | Expected receipts quantity (optional, not included in ATP for v1) |
| `locationId` | string (uuid) | Yes | Location identifier |
| `onHandQty` | number | Yes | Physical on-hand quantity (net sum of ledger events) |
| `productId` | string (uuid) | Yes | Product identifier |
| `uom` | string | Yes | Base unit of measure for all quantities |


### MovedItem

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `itemId` | string | No |  |
| `quantity` | number (double) | No |  |


*Additional schemas omitted for brevity. See OpenAPI spec for complete list.*

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/inventory/pickingLists/{id}/confirm
Content-Type: application/json
X-Correlation-Id: abc-123-def-456

{
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE"
}
```

**Response:**

```http
HTTP/1.1 201 Created
X-Correlation-Id: abc-123-def-456

{
  "id": "new-id-123",
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:30:00Z"
}
```

#### Example: Retrieve Request

```http
GET /v1/inventory/sites/{siteId}/defaultLocations
X-Correlation-Id: abc-123-def-456
```

**Response:**

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456

{
  "id": "existing-id-456",
  "name": "Example",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:00:00Z",
  "updatedAt": "2026-01-27T14:30:00Z"
}
```

---

## Summary

This guide establishes standardized contracts for the Inventory Management domain:

- **Field Naming**: camelCase for all JSON fields
- **Enum Values**: UPPER_SNAKE_CASE for all enums
- **Timestamps**: ISO 8601 UTC format
- **Identifiers**: String-based UUIDs
- **Pagination**: Zero-based with standard response format
- **Error Handling**: Consistent error response structure with correlation IDs

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

---

## References

- OpenAPI Specification: `pos-inventory/target/openapi.json`
- Domain Agent Guide: `domains/inventory/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/inventory/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
