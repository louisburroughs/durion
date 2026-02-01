# Security & Authentication Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-security-service/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Security & Authentication domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

#### RoleAssignment.scopeType

- `GLOBAL`
- `LOCATION`

#### RoleAssignmentRequest.scopeType

- `GLOBAL`
- `LOCATION`

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
GET /v1/security/entities/123
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

This domain exposes **29** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/v1/auth/authorities` |  |
| DELETE | `/v1/auth/delete` |  |
| POST | `/v1/auth/login` | Authenticate user and issue JWT |
| POST | `/v1/auth/refresh` |  |
| GET | `/v1/auth/roles` |  |
| GET | `/v1/auth/subject` |  |
| POST | `/v1/auth/token-pair` |  |
| GET | `/v1/auth/validate` | Validate JWT token |
| GET | `/v1/permissions` | Get all registered permissions |
| GET | `/v1/permissions/domain/{domain}` | Get permissions by domain |
| GET | `/v1/permissions/exists/{permissionName}` | Check if permission exists |
| POST | `/v1/permissions/register` | Register permissions from a service |
| GET | `/v1/permissions/validate/{permissionName}` | Validate permission name format |
| GET | `/v1/roles` | Get all roles |
| POST | `/v1/roles` | Create a new role |
| POST | `/v1/roles/assignments` | Create role assignment |
| GET | `/v1/roles/assignments/user/{userId}` | Get user role assignments |
| DELETE | `/v1/roles/assignments/{assignmentId}` | Revoke role assignment |
| GET | `/v1/roles/check-permission` | Check user permission |
| PUT | `/v1/roles/permissions` | Update role permissions |
| GET | `/v1/roles/permissions/user/{userId}` | Get user permissions |
| GET | `/v1/roles/{name}` | Get role by name |
| GET | `/v1/users` | Get all users |
| POST | `/v1/users` | Create a new user |
| POST | `/v1/users/login` | User login |
| DELETE | `/v1/users/{id}` | Delete a user |
| GET | `/v1/users/{id}` | Get user by ID |
| PUT | `/v1/users/{id}` | Update an existing user |
| PUT | `/v1/users/{username}/roles` |  |

### Endpoint Details

#### GET /v1/auth/authorities

**Operation ID:** `getAuthorities`

**Parameters:**

- `token` (query, Required, string):

**Responses:**

- `200`: OK

---

#### DELETE /v1/auth/delete

**Operation ID:** `deleteToken`

**Parameters:**

- `token` (query, Required, string):

**Responses:**

- `200`: OK

---

#### POST /v1/auth/login

**Summary:** Authenticate user and issue JWT

**Description:** Authenticate with username and password to receive a JWT token.

**Operation ID:** `generateToken`

**Parameters:**

- `subject` (query, Required, string):
- `roles` (query, Optional, array):

**Responses:**

- `200`: JWT token issued successfully.
- `401`: Invalid credentials.

---

#### POST /v1/auth/refresh

**Operation ID:** `refreshAccessToken`

**Parameters:**

- `refreshToken` (query, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/auth/roles

**Operation ID:** `getRoles`

**Parameters:**

- `token` (query, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/auth/subject

**Operation ID:** `getSubject`

**Parameters:**

- `token` (query, Required, string):

**Responses:**

- `200`: OK

---

#### POST /v1/auth/token-pair

**Operation ID:** `generateTokenPair`

**Parameters:**

- `subject` (query, Required, string):
- `roles` (query, Optional, array):

**Responses:**

- `200`: OK

---

#### GET /v1/auth/validate

**Summary:** Validate JWT token

**Description:** Validate a JWT token and return its claims if valid.

**Operation ID:** `validateToken`

**Parameters:**

- `token` (query, Required, string):

**Responses:**

- `200`: Token is valid.
- `401`: Token is invalid or expired.

---

#### GET /v1/permissions

**Summary:** Get all registered permissions

**Description:** Returns all permissions in the registry

**Operation ID:** `getAllPermissions`

**Responses:**

- `200`: OK

---

#### GET /v1/permissions/domain/{domain}

**Summary:** Get permissions by domain

**Description:** Returns all permissions for a specific domain/service

**Operation ID:** `getPermissionsByDomain`

**Parameters:**

- `domain` (path, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/permissions/exists/{permissionName}

**Summary:** Check if permission exists

**Description:** Returns true if the permission is registered

**Operation ID:** `permissionExists`

**Parameters:**

- `permissionName` (path, Required, string):

**Responses:**

- `200`: OK

---

#### POST /v1/permissions/register

**Summary:** Register permissions from a service

**Description:** Services call this endpoint to register their available permissions

**Operation ID:** `registerPermissions`

**Responses:**

- `200`: OK

---

#### GET /v1/permissions/validate/{permissionName}

**Summary:** Validate permission name format

**Description:** Checks if a permission name follows the domain:resource:action format

**Operation ID:** `validatePermissionName`

**Parameters:**

- `permissionName` (path, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/roles

**Summary:** Get all roles

**Description:** Returns all roles in the system

**Operation ID:** `getAllRoles`

**Responses:**

- `200`: OK

---

#### POST /v1/roles

**Summary:** Create a new role

**Description:** Creates a new role with the specified name and description

**Operation ID:** `createRole`

**Responses:**

- `200`: OK

---

#### POST /v1/roles/assignments

**Summary:** Create role assignment

**Description:** Assigns a role to a user with optional scope and effective dates

**Operation ID:** `createRoleAssignment`

**Responses:**

- `200`: OK

---

#### GET /v1/roles/assignments/user/{userId}

**Summary:** Get user role assignments

**Description:** Returns all effective role assignments for a user

**Operation ID:** `getUserRoleAssignments`

**Parameters:**

- `userId` (path, Required, integer):

**Responses:**

- `200`: OK

---

#### DELETE /v1/roles/assignments/{assignmentId}

**Summary:** Revoke role assignment

**Description:** Revokes a role assignment by setting its end date

**Operation ID:** `revokeRoleAssignment`

**Parameters:**

- `assignmentId` (path, Required, integer):

**Responses:**

- `200`: OK

---

#### GET /v1/roles/check-permission

**Summary:** Check user permission

**Description:** Checks if a user has a specific permission for a location

**Operation ID:** `checkUserPermission`

**Parameters:**

- `userId` (query, Required, integer):
- `permission` (query, Required, string):
- `locationId` (query, Optional, string):

**Responses:**

- `200`: OK

---

#### PUT /v1/roles/permissions

**Summary:** Update role permissions

**Description:** Assigns a set of permissions to a role

**Operation ID:** `updateRolePermissions`

**Responses:**

- `200`: OK

---

#### GET /v1/roles/permissions/user/{userId}

**Summary:** Get user permissions

**Description:** Returns all permissions for a user from their role assignments

**Operation ID:** `getUserPermissions`

**Parameters:**

- `userId` (path, Required, integer):

**Responses:**

- `200`: OK

---

#### GET /v1/roles/{name}

**Summary:** Get role by name

**Description:** Returns a specific role by its name

**Operation ID:** `getRoleByName`

**Parameters:**

- `name` (path, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/users

**Summary:** Get all users

**Description:** Retrieve a list of all users.

**Operation ID:** `getAllUsers`

**Responses:**

- `200`: List of users returned successfully.

---

#### POST /v1/users

**Summary:** Create a new user

**Description:** Creates a new user with username, password, and roles.

**Operation ID:** `createUser`

**Responses:**

- `200`: User created successfully.

---

#### POST /v1/users/login

**Summary:** User login

**Description:** Authenticates a user and returns a JWT token.

**Operation ID:** `login`

**Responses:**

- `200`: OK

---

#### DELETE /v1/users/{id}

**Summary:** Delete a user

**Description:** Delete a user by their unique ID.

**Operation ID:** `deleteUser`

**Parameters:**

- `id` (path, Required, integer): ID of the user to delete

**Responses:**

- `204`: User deleted successfully.
- `404`: User not found.

---

#### GET /v1/users/{id}

**Summary:** Get user by ID

**Description:** Retrieve a user by their unique ID.

**Operation ID:** `getUserById`

**Parameters:**

- `id` (path, Required, integer): ID of the user to retrieve

**Responses:**

- `200`: User found and returned.
- `404`: User not found.

---

#### PUT /v1/users/{id}

**Summary:** Update an existing user

**Description:** Update the details of an existing user.

**Operation ID:** `updateUser`

**Parameters:**

- `id` (path, Required, integer): ID of the user to update

**Responses:**

- `200`: User updated successfully.
- `404`: User not found.

---

#### PUT /v1/users/{username}/roles

**Operation ID:** `assignRoles`

**Parameters:**

- `username` (path, Required, string):

**Responses:**

- `200`: OK

---

## Entity-Specific Contracts

### Permission

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | No |  |
| `description` | string | No |  |
| `domain` | string | No |  |
| `id` | integer (int64) | No |  |
| `name` | string | No |  |
| `registeredAt` | string (date-time) | No |  |
| `registeredByService` | string | No |  |
| `resource` | string | No |  |
| `version` | string | No |  |

### PermissionDefinition

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `description` | string | No |  |
| `name` | string | No |  |

### PermissionRegistrationRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `domain` | string | No |  |
| `permissions` | array | No |  |
| `serviceName` | string | No |  |
| `version` | string | No |  |

### PermissionRegistrationResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `errors` | array | No |  |
| `message` | string | No |  |
| `registeredPermissions` | integer (int32) | No |  |
| `skippedPermissions` | integer (int32) | No |  |
| `success` | boolean | No |  |
| `totalPermissions` | integer (int32) | No |  |
| `updatedPermissions` | integer (int32) | No |  |

### Role

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdAt` | string (date-time) | No |  |
| `createdBy` | string | No |  |
| `description` | string | No |  |
| `id` | integer (int64) | No |  |
| `lastModifiedAt` | string (date-time) | No |  |
| `lastModifiedBy` | string | No |  |
| `name` | string | No |  |
| `permissions` | array | No |  |

### RoleAssignment

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdAt` | string (date-time) | No |  |
| `createdBy` | string | No |  |
| `effective` | boolean | No |  |
| `effectiveEndDate` | string (date) | No |  |
| `effectiveStartDate` | string (date) | No |  |
| `id` | integer (int64) | No |  |
| `lastModifiedAt` | string (date-time) | No |  |
| `lastModifiedBy` | string | No |  |
| `role` | string | No |  |
| `scopeLocationIds` | array | No |  |
| `scopeType` | string | No |  |
| `user` | string | No |  |

### RoleAssignmentRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `effectiveEndDate` | string (date) | No |  |
| `effectiveStartDate` | string (date) | No |  |
| `roleId` | integer (int64) | No |  |
| `scopeLocationIds` | array | No |  |
| `scopeType` | string | No |  |
| `userId` | integer (int64) | No |  |

### RolePermissionsRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `permissionNames` | array | No |  |
| `roleId` | integer (int64) | No |  |

### TokenPair

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `accessToken` | string | No |  |
| `refreshToken` | string | No |  |

### User

Updated user object

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | integer (int64) | No |  |
| `password` | string | No |  |
| `roles` | array | No |  |
| `username` | string | No |  |

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/users
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
GET /v1/users/{id}
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

This guide establishes standardized contracts for the Security & Authentication domain:

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

- OpenAPI Specification: `pos-security-service/target/openapi.json`
- Domain Agent Guide: `domains/security/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/security/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/security/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
