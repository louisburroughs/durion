# Order Management Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-order/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Order Management domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

#### ApplyPriceOverrideRequest.reasonCode

- `CUSTOMER_LOYALTY`
- `PRICE_MATCH`
- `PROMOTIONAL_PRICING`
- `PRICING_ERROR_CORRECTION`
- `VOLUME_DISCOUNT`
- `GOODWILL_ADJUSTMENT`
- `MANAGER_DISCRETION`
- `OTHER`

#### ApplyPriceOverrideResponse.reasonCode

- `CUSTOMER_LOYALTY`
- `PRICE_MATCH`
- `PROMOTIONAL_PRICING`
- `PRICING_ERROR_CORRECTION`
- `VOLUME_DISCOUNT`
- `GOODWILL_ADJUSTMENT`
- `MANAGER_DISCRETION`
- `OTHER`

#### ApplyPriceOverrideResponse.status

- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`
- `APPLIED`
- `CANCELLED`

#### PriceOverride.reasonCode

- `CUSTOMER_LOYALTY`
- `PRICE_MATCH`
- `PROMOTIONAL_PRICING`
- `PRICING_ERROR_CORRECTION`
- `VOLUME_DISCOUNT`
- `GOODWILL_ADJUSTMENT`
- `MANAGER_DISCRETION`
- `OTHER`

#### PriceOverride.status

- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`
- `APPLIED`
- `CANCELLED`

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
GET /v1/order/entities/123
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

This domain exposes **6** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/api/v1/orders/price-overrides` | Get price overrides |
| POST | `/api/v1/orders/price-overrides` | Apply price override |
| GET | `/api/v1/orders/price-overrides/pending` | Get pending approvals |
| GET | `/api/v1/orders/price-overrides/{overrideId}` | Get price override |
| POST | `/api/v1/orders/price-overrides/{overrideId}/approve` | Approve price override |
| POST | `/api/v1/orders/price-overrides/{overrideId}/reject` | Reject price override |

### Endpoint Details

#### GET /api/v1/orders/price-overrides

**Summary:** Get price overrides

**Description:** Retrieve price overrides by order ID, status, or date range. At least one filter parameter is required.

**Operation ID:** `getOverridesByOrder`

**Parameters:**

- `orderId` (query, Optional, string): Order ID filter
- `status` (query, Optional, string): Override status filter
- `startDate` (query, Optional, string): Start date for date range filter
- `endDate` (query, Optional, string): End date for date range filter

**Responses:**

- `200`: Overrides retrieved
- `400`: No filter parameter provided


---

#### POST /api/v1/orders/price-overrides

**Summary:** Apply price override

**Description:** Apply a price override to an order line. May require approval based on override amount.

**Operation ID:** `applyPriceOverride`

**Responses:**

- `201`: Override applied and auto-approved
- `202`: Override applied, pending approval
- `400`: Invalid request
- `403`: Insufficient permissions


---

#### GET /api/v1/orders/price-overrides/pending

**Summary:** Get pending approvals

**Description:** Retrieve all price overrides awaiting approval.

**Operation ID:** `getPendingApprovals`

**Responses:**

- `200`: Pending overrides retrieved


---

#### GET /api/v1/orders/price-overrides/{overrideId}

**Summary:** Get price override

**Description:** Retrieve a specific price override by ID.

**Operation ID:** `getOverride`

**Parameters:**

- `overrideId` (path, Required, integer): Price override ID

**Responses:**

- `200`: Override found
- `404`: Override not found


---

#### POST /api/v1/orders/price-overrides/{overrideId}/approve

**Summary:** Approve price override

**Description:** Approve a pending price override. Validates approver permission level.

**Operation ID:** `approvePriceOverride`

**Parameters:**

- `overrideId` (path, Required, integer): Price override ID

**Responses:**

- `200`: Override approved
- `400`: Invalid request or override not in pending state
- `403`: Insufficient approval permissions
- `404`: Override not found


---

#### POST /api/v1/orders/price-overrides/{overrideId}/reject

**Summary:** Reject price override

**Description:** Reject a pending price override with a reason.

**Operation ID:** `rejectPriceOverride`

**Parameters:**

- `overrideId` (path, Required, integer): Price override ID

**Responses:**

- `200`: Override rejected
- `400`: Invalid request or override not in pending state
- `403`: Insufficient rejection permissions
- `404`: Override not found



---

## Entity-Specific Contracts

### ApplyPriceOverrideRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `justification` | string | No |  |
| `orderId` | string | Yes |  |
| `orderLineId` | string | Yes |  |
| `originalPrice` | number | Yes |  |
| `overridePrice` | number | Yes |  |
| `productId` | string | Yes |  |
| `reasonCode` | string | Yes |  |


### ApplyPriceOverrideResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdAt` | string (date-time) | No |  |
| `discountAmount` | number | No |  |
| `discountPercentage` | number | No |  |
| `justification` | string | No |  |
| `message` | string | No |  |
| `orderId` | string | No |  |
| `orderLineId` | string | No |  |
| `originalPrice` | number | No |  |
| `overrideId` | integer (int64) | No |  |
| `overridePrice` | number | No |  |
| `productId` | string | No |  |
| `reasonCode` | string | No |  |
| `requestedByUserId` | string | No |  |
| `requiresApproval` | boolean | No |  |
| `status` | string | No |  |


### ApprovePriceOverrideRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `comments` | string | No |  |


### PriceOverride

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `appliedAt` | string (date-time) | No |  |
| `approvedAt` | string (date-time) | No |  |
| `approvedByUserId` | string | No |  |
| `createdAt` | string (date-time) | No |  |
| `discountAmount` | number | No |  |
| `discountPercentage` | number | No |  |
| `justification` | string | No |  |
| `orderId` | string | No |  |
| `orderLineId` | string | No |  |
| `originalPrice` | number | No |  |
| `overrideId` | integer (int64) | No |  |
| `overridePrice` | number | No |  |
| `productId` | string | No |  |
| `reasonCode` | string | No |  |
| `rejectedAt` | string (date-time) | No |  |
| `rejectedByUserId` | string | No |  |
| `rejectionReason` | string | No |  |
| `requestedByUserId` | string | No |  |
| `requiresApproval` | boolean | No |  |
| `status` | string | No |  |
| `updatedAt` | string (date-time) | No |  |


### RejectPriceOverrideRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `comments` | string | No |  |
| `reason` | string | Yes |  |



---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /api/v1/orders/price-overrides
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
GET /api/v1/orders/price-overrides
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

This guide establishes standardized contracts for the Order Management domain:

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

- OpenAPI Specification: `pos-order/target/openapi.json`
- Domain Agent Guide: `domains/order/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/order/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/order/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
