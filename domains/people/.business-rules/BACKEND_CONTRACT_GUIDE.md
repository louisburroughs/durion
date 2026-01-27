# People & Human Resources Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-people/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the People & Human Resources domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

#### TimeEntryAdjustment.status

- `PROPOSED`
- `PENDING`
- `APPROVED`
- `REJECTED`

#### TimeEntryException.severity

- `WARNING`
- `BLOCKING`

#### TimeEntryException.status

- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`
- `WAIVED`

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
GET /v1/people/entities/123
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

This domain exposes **21** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/v1/people` | Get all people |
| POST | `/v1/people` | Create a new person |
| GET | `/v1/people/availability` | Get people availability |
| GET | `/v1/people/exceptions` | List exceptions, optional filter by employeeId |
| POST | `/v1/people/exceptions` | Create a time entry exception |
| POST | `/v1/people/exceptions/{exceptionId}/acknowledge` | Acknowledge an exception |
| POST | `/v1/people/exceptions/{exceptionId}/resolve` | Resolve an exception |
| POST | `/v1/people/exceptions/{exceptionId}/waive` | Waive an exception |
| GET | `/v1/people/reports/attendanceJobtimeDiscrepancy` | Get attendance and job time discrepancy report |
| POST | `/v1/people/timeEntries/adjustments` | Create a time entry adjustment |
| POST | `/v1/people/timeEntries/adjustments/{adjustmentId}/approve` |  |
| POST | `/v1/people/timeEntries/approve` | Batch approve time entries |
| POST | `/v1/people/timeEntries/reject` | Batch reject time entries |
| GET | `/v1/people/timeEntries/{timeEntryId}/adjustments` | List adjustments for a time entry |
| POST | `/v1/people/workSessions/start` | Start work session |
| POST | `/v1/people/workSessions/stop` | Stop work session |
| POST | `/v1/people/workSessions/{id}/breaks/start` | Start work session break |
| POST | `/v1/people/workSessions/{id}/breaks/stop` | Stop work session break |
| DELETE | `/v1/people/{personId}` | Delete a person |
| GET | `/v1/people/{personId}` | Get person by ID |
| PUT | `/v1/people/{personId}` | Update an existing person |

### Endpoint Details

#### GET /v1/people

**Summary:** Get all people

**Description:** Retrieve a list of all people.

**Operation ID:** `getAllPeople`

**Responses:**

- `200`: List of people returned successfully.


---

#### POST /v1/people

**Summary:** Create a new person

**Description:** Add a new person to the system.

**Operation ID:** `createPerson`

**Responses:**

- `200`: Person created successfully.


---

#### GET /v1/people/availability

**Summary:** Get people availability

**Description:** Return availability with optional locationId and date filters.

**Operation ID:** `getPeopleAvailability`

**Parameters:**

- `locationId` (query, Optional, integer): Filter by location ID
- `date` (query, Optional, string): Filter by date (ISO format: yyyy-MM-dd)

**Responses:**

- `200`: Availability data returned successfully.


---

#### GET /v1/people/exceptions

**Summary:** List exceptions, optional filter by employeeId

**Operation ID:** `listByEmployee`

**Parameters:**

- `employeeId` (query, Optional, string): 

**Responses:**

- `200`: List returned


---

#### POST /v1/people/exceptions

**Summary:** Create a time entry exception

**Operation ID:** `createException`

**Responses:**

- `200`: Exception created
- `400`: Invalid request


---

#### POST /v1/people/exceptions/{exceptionId}/acknowledge

**Summary:** Acknowledge an exception

**Operation ID:** `acknowledgeException`

**Parameters:**

- `exceptionId` (path, Required, string): 
- `X-User-Id` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### POST /v1/people/exceptions/{exceptionId}/resolve

**Summary:** Resolve an exception

**Operation ID:** `resolveException`

**Parameters:**

- `exceptionId` (path, Required, string): 
- `X-User-Id` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### POST /v1/people/exceptions/{exceptionId}/waive

**Summary:** Waive an exception

**Operation ID:** `waiveException`

**Parameters:**

- `exceptionId` (path, Required, string): 
- `X-User-Id` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### GET /v1/people/reports/attendanceJobtimeDiscrepancy

**Summary:** Get attendance and job time discrepancy report

**Description:** Reporting endpoint for attendance vs. job time discrepancies.

**Operation ID:** `getAttendanceDiscrepancyReport`

**Responses:**

- `200`: Report generated successfully.


---

#### POST /v1/people/timeEntries/adjustments

**Summary:** Create a time entry adjustment

**Operation ID:** `createAdjustment`

**Responses:**

- `200`: Adjustment created
- `400`: Invalid request


---

#### POST /v1/people/timeEntries/adjustments/{adjustmentId}/approve

**Operation ID:** `approveAdjustment`

**Parameters:**

- `adjustmentId` (path, Required, string): 
- `X-Permissions` (header, Optional, string): 
- `X-User-Id` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### POST /v1/people/timeEntries/approve

**Summary:** Batch approve time entries

**Description:** Approve multiple time entries. pos-people is authoritative for approval execution.

**Operation ID:** `approveTimeEntries`

**Parameters:**

- `X-User-Id` (header, Optional, string): 
- `X-Permissions` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### POST /v1/people/timeEntries/reject

**Summary:** Batch reject time entries

**Description:** Reject multiple time entries. rejectionReason is required for each decision.

**Operation ID:** `rejectTimeEntries`

**Parameters:**

- `X-User-Id` (header, Optional, string): 
- `X-Permissions` (header, Optional, string): 
- `X-Correlation-Id` (header, Optional, string): 

**Responses:**

- `200`: OK


---

#### GET /v1/people/timeEntries/{timeEntryId}/adjustments

**Summary:** List adjustments for a time entry

**Operation ID:** `listForTimeEntry`

**Parameters:**

- `timeEntryId` (path, Required, string): 

**Responses:**

- `200`: List returned


---

#### POST /v1/people/workSessions/start

**Summary:** Start work session

**Description:** Create/start a work session for a person.

**Operation ID:** `startWorkSession`

**Responses:**

- `200`: Work session started successfully.


---

#### POST /v1/people/workSessions/stop

**Summary:** Stop work session

**Description:** Stop an active work session.

**Operation ID:** `stopWorkSession`

**Responses:**

- `200`: Work session stopped successfully.


---

#### POST /v1/people/workSessions/{id}/breaks/start

**Summary:** Start work session break

**Description:** Start a break within an active work session.

**Operation ID:** `startWorkSessionBreak`

**Parameters:**

- `id` (path, Required, integer): Work session ID

**Responses:**

- `200`: Break started successfully.
- `404`: Work session not found.


---

#### POST /v1/people/workSessions/{id}/breaks/stop

**Summary:** Stop work session break

**Description:** End a break within a work session.

**Operation ID:** `stopWorkSessionBreak`

**Parameters:**

- `id` (path, Required, integer): Work session ID

**Responses:**

- `200`: Break stopped successfully.
- `404`: Work session or break not found.


---

#### DELETE /v1/people/{personId}

**Summary:** Delete a person

**Description:** Delete a person by their unique ID.

**Operation ID:** `deletePerson`

**Parameters:**

- `personId` (path, Required, integer): ID of the person to delete

**Responses:**

- `204`: Person deleted successfully.
- `404`: Person not found.


---

#### GET /v1/people/{personId}

**Summary:** Get person by ID

**Description:** Retrieve a person by their unique ID.

**Operation ID:** `getPersonById`

**Parameters:**

- `personId` (path, Required, integer): ID of the person to retrieve

**Responses:**

- `200`: Person found and returned.
- `404`: Person not found.


---

#### PUT /v1/people/{personId}

**Summary:** Update an existing person

**Description:** Update the details of an existing person.

**Operation ID:** `updatePerson`

**Parameters:**

- `personId` (path, Required, integer): ID of the person to update

**Responses:**

- `200`: Person updated successfully.
- `404`: Person not found.



---

## Entity-Specific Contracts

### Decision

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `rejectionReason` | string | No |  |
| `timeEntryId` | string | No |  |


### Person

Person object to be created

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `firstName` | string | No |  |
| `id` | integer (int64) | No |  |
| `lastName` | string | No |  |
| `phoneNumbers` | array | No |  |
| `primaryEmail` | string | No |  |
| `secondaryEmail` | string | No |  |
| `username` | string | No |  |


### TimeEntryAdjustment

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `adjustmentId` | string (uuid) | No |  |
| `createdAt` | string (date-time) | No |  |
| `createdBy` | string | No |  |
| `decidedAt` | string (date-time) | No |  |
| `decidedBy` | string | No |  |
| `minutesDelta` | integer (int32) | No |  |
| `notes` | string | No |  |
| `proposedEndAt` | string (date-time) | No |  |
| `proposedStartAt` | string (date-time) | No |  |
| `reasonCode` | string | No |  |
| `status` | string | No |  |
| `timeEntryId` | string | No |  |


### TimeEntryAdjustmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdBy` | string | No |  |
| `minutesDelta` | integer (int32) | No |  |
| `notes` | string | No |  |
| `proposedEndAt` | string (date-time) | No |  |
| `proposedStartAt` | string (date-time) | No |  |
| `reasonCode` | string | No |  |
| `timeEntryId` | string | No |  |


### TimeEntryAdjustmentResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `adjustmentId` | string (uuid) | No |  |
| `message` | string | No |  |
| `success` | boolean | No |  |


### TimeEntryDecisionBatchRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `decisions` | array | No |  |


### TimeEntryException

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `detectedAt` | string (date-time) | No |  |
| `employeeId` | string | No |  |
| `exceptionCode` | string | No |  |
| `exceptionId` | string (uuid) | No |  |
| `resolutionNotes` | string | No |  |
| `resolvedAt` | string (date-time) | No |  |
| `resolvedBy` | string | No |  |
| `severity` | string | No |  |
| `status` | string | No |  |
| `timeEntryId` | string | No |  |
| `workDate` | string (date) | No |  |


### TimeEntryExceptionRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `detectedAt` | string (date-time) | No |  |
| `employeeId` | string | No |  |
| `exceptionCode` | string | No |  |
| `resolutionNotes` | string | No |  |
| `severity` | string | No |  |
| `timeEntryId` | string | No |  |


### TimeEntryExceptionResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `exceptionId` | string (uuid) | No |  |
| `message` | string | No |  |
| `success` | boolean | No |  |



---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/people
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
GET /v1/people/{personId}
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

This guide establishes standardized contracts for the People & Human Resources domain:

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

- OpenAPI Specification: `pos-people/target/openapi.json`
- Domain Agent Guide: `domains/people/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/people/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/people/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
