# Security & Authentication Backend Contract Guide

**Version:** 2.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-02-01  
**OpenAPI Source:** `durion-positivity-backend/pos-security-service/openapi.json`  
**Architecture:** ADR-0011 Gateway-based Security Architecture  
**Related Issues:** [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417), [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280)

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Security & Authentication domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Required Request Headers](#required-request-headers)
3. [Gateway-Injected Headers](#gateway-injected-headers)
4. [Authentication & Authorization Flow](#authentication--authorization-flow)
5. [Public vs Protected Endpoints](#public-vs-protected-endpoints)
6. [JSON Field Naming Conventions](#json-field-naming-conventions)
7. [Data Types & Formats](#data-types--formats)
8. [Enum Value Conventions](#enum-value-conventions)
9. [Identifier Naming](#identifier-naming)
10. [Timestamp Conventions](#timestamp-conventions)
11. [Collection & Pagination](#collection--pagination)
12. [Error Response Format](#error-response-format)
13. [Correlation ID & Request Tracking](#correlation-id--request-tracking)
14. [API Endpoints](#api-endpoints)
15. [Entity-Specific Contracts](#entity-specific-contracts)
16. [Examples](#examples)

---

## Architecture Overview

This API follows the **ADR-0011 Gateway-based Security Architecture** where:

- **Moqui** is the system of record for user identities and role assignments
- **API Gateway** (`pos-api-gateway`) is the authentication enforcement boundary
- **Backend Services** trust gateway-injected headers and use `@PreAuthorize` for authorization
- **JWT Assertions** are HMAC-signed (HS256) with shared secret from AWS Secrets Manager

### Trust Model

```
Client → API Gateway → Backend Service
         [validates JWT]  [trusts X-Authorities]
         [injects headers] [applies @PreAuthorize]
```

**Key Principles:**

1. **Authentication at Gateway:** All JWT validation happens at the API Gateway
2. **Authorization at Service:** Backend services use Spring Security `@PreAuthorize` annotations
3. **No Direct JWT Validation:** Services do NOT validate JWTs; they trust gateway-injected headers
4. **Eliminates Circular Dependencies:** Services don't call security-service for token validation

**Implementation Status:**

- **Backend:** [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417) (In Progress)
- **Frontend:** [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280) (In Progress)
- **Architecture:** ADR-0011 (Accepted)

---

## Required Request Headers

All API requests **MUST** include these headers when calling through the API Gateway:

### X-API-Version (MANDATORY)

**Purpose:** API versioning via header instead of path

**Format:** Numeric string (e.g., `"1"`, `"2"`, `"10"`)

**Validation:** STRICT - Must be numeric or gateway returns `400 Bad Request`

**Behavior:** Gateway rewrites path from `/security/{resource}` to `/v{version}/.../{resource}`

**Example:**

```http
GET /security/users/123
X-API-Version: 1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Gateway rewrites to: `GET /v1/users/123`

**Error Response (Missing Header):**

```json
{
  "code": "BAD_REQUEST",
  "message": "X-API-Version header is required",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

**Error Response (Invalid Format):**

```json
{
  "code": "BAD_REQUEST",
  "message": "X-API-Version must be numeric (e.g., 1, 2, 10)",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

### Authorization (REQUIRED for Protected Endpoints)

**Purpose:** JWT authentication token for user identity and permissions

**Format:** `Bearer <JWT>`

**JWT Claims (issued by Moqui):**

- `iss`: `"moqui"` (issuer)
- `aud`: `"api-gateway:<env>"` (audience: local, dev, stage, prod)
- `sub`: User identifier (Moqui userId)
- `roles`: Array of role names (e.g., `["SHOP_MGR", "ACCOUNTING_CLERK"]`)
- `iat`: Issued at timestamp
- `exp`: Expiration timestamp
- `jti`: JWT ID (for replay protection)

**Validation Flow:**

1. Gateway extracts JWT from `Authorization: Bearer <token>`
2. Gateway calls `POST /v1/auth/validate?token=<token>`
3. If valid, gateway fetches authorities: `GET /v1/auth/authorities?token=<token>`
4. Gateway fetches subject: `GET /v1/auth/subject?token=<token>`
5. Gateway injects `X-Authorities` and `X-User` headers
6. Request forwarded to backend service

**Example:**

```http
POST /security/users
X-API-Version: 1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJtb3F1aSIsImF1ZCI6ImFwaS1nYXRld2F5OmxvY2FsIiwic3ViIjoidXNlcjEyMyIsInJvbGVzIjpbIlNIT1BfTUdSIl0sImlhdCI6MTczODQxNjAwMCwiZXhwIjoxNzM4NDE5NjAwLCJqdGkiOiJhYmMtMTIzIn0...
Content-Type: application/json
```

**Error Response (Missing Token):**

```json
{
  "code": "UNAUTHORIZED",
  "message": "Authorization header is required",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

**Error Response (Invalid/Expired Token):**

```json
{
  "code": "UNAUTHORIZED",
  "message": "Invalid or expired JWT token",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

### X-Correlation-Id (RECOMMENDED)

**Purpose:** Distributed tracing and request correlation

**Format:** UUID or correlation string (e.g., `"abc-123-def-456"`)

**Behavior:** Echoed in response headers and error bodies

**Example:**

```http
GET /security/users/123
X-API-Version: 1
X-Correlation-Id: abc-123-def-456
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## Gateway-Injected Headers

The API Gateway injects these headers after successful JWT validation. Backend services **MUST** read these headers to populate Spring SecurityContext.

### X-Authorities (Injected by Gateway)

**Purpose:** Comma-separated list of Spring Security authorities derived from JWT roles

**Format:** `ROLE_<ROLE_NAME>[,ROLE_<ROLE_NAME>]*`

**Populated By:** API Gateway after JWT validation

**Example Values:**

- `ROLE_SHOP_MGR`
- `ROLE_SHOP_MGR,ROLE_ACCOUNTING_CLERK`
- `ROLE_ADMIN,ROLE_MANAGER,ROLE_USER`

**Role Mapping:**

Gateway maps Moqui role names to Spring Security authorities:

| Moqui Role | Spring Authority |
|------------|------------------|
| `SHOP_MGR` | `ROLE_SHOP_MGR` |
| `ACCOUNTING_CLERK` | `ROLE_ACCOUNTING_CLERK` |
| `ADMIN` | `ROLE_ADMIN` |
| `MANAGER` | `ROLE_MANAGER` |
| `USER` | `ROLE_USER` |

**Usage in Backend Services:**

Backend services import `GatewaySecurityConfig` from `pos-security-common` which provides `GatewayAuthoritiesFilter` to read this header and populate `SecurityContextHolder`:

```java
@Configuration
@Import(GatewaySecurityConfig.class)
public class MyServiceSecurityConfig {
    // X-Authorities header automatically processed
}

@RestController
@RequestMapping("/v1/orders")
public class OrderController {
    
    @PostMapping
    @PreAuthorize("hasRole('SHOP_MGR')")
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        // X-Authorities: ROLE_SHOP_MGR parsed and applied
        // This method only executes if user has SHOP_MGR role
        return ResponseEntity.ok(orderService.create(request));
    }
}
```

**Error Behavior (Missing Header):**

If `X-Authorities` header is missing, backend service returns `403 Forbidden` because SecurityContext cannot be populated.

### X-User (Injected by Gateway)

**Purpose:** Authenticated user identifier (subject from JWT)

**Format:** String (Moqui userId)

**Populated By:** API Gateway after JWT validation

**Example Values:**

- `user123`
- `admin@example.com`
- `shop-manager-456`

**Usage in Backend Services:**

Read from SecurityContext via `Authentication.getName()`:

```java
@GetMapping("/me")
public ResponseEntity<UserProfile> getCurrentUser() {
    // X-User header populated into SecurityContext
    String userId = SecurityContextHolder.getContext()
        .getAuthentication().getName();
    
    UserProfile profile = userService.getProfile(userId);
    return ResponseEntity.ok(profile);
}
```

**Trust Model:**

Backend services **MUST** trust `X-Authorities` and `X-User` headers because:

1. Gateway is the only entry point (network-level isolation)
2. Gateway validates JWT cryptographically (HMAC-SHA256)
3. Gateway checks replay cache (JTI) to prevent token reuse
4. Services don't have direct external exposure

---

## Authentication & Authorization Flow

Complete request flow from client to backend service:

### 1. Client Request

Client sends request with JWT obtained from Moqui:

```http
POST /security/orders
X-API-Version: 1
X-Correlation-Id: abc-123-def-456
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJtb3F1aSIsImF1ZCI6ImFwaS1nYXRld2F5OmxvY2FsIiwic3ViIjoidXNlcjEyMyIsInJvbGVzIjpbIlNIT1BfTUdSIl0sImlhdCI6MTczODQxNjAwMCwiZXhwIjoxNzM4NDE5NjAwLCJqdGkiOiJhYmMtMTIzIn0.signature
Content-Type: application/json

{"customerId": "cust-456", "items": [...]}
```

### 2. Gateway: Path Rewriting

Gateway reads `X-API-Version: 1` and rewrites path:

- Original: `POST /security/orders`
- Rewritten: `POST /v1/orders`

### 3. Gateway: JWT Validation

Gateway validates JWT via Security Service:

```http
POST /v1/auth/validate?token=eyJhbGci...
Host: pos-security-service:8083
```

Response:

```json
{"valid": true}
```

### 4. Gateway: Fetch Authorities

Gateway fetches authorities from token:

```http
GET /v1/auth/authorities?token=eyJhbGci...
Host: pos-security-service:8083
```

Response:

```json
["ROLE_SHOP_MGR"]
```

### 5. Gateway: Fetch Subject

Gateway fetches subject (user ID) from token:

```http
GET /v1/auth/subject?token=eyJhbGci...
Host: pos-security-service:8083
```

Response:

```json
"user123"
```

### 6. Gateway: Header Injection

Gateway injects headers and forwards request to backend service:

```http
POST /v1/orders
Host: pos-order:8086
X-API-Version: 1
X-Correlation-Id: abc-123-def-456
X-Authorities: ROLE_SHOP_MGR
X-User: user123
Content-Type: application/json

{"customerId": "cust-456", "items": [...]}
```

### 7. Backend Service: Authorization

Backend service's `GatewayAuthoritiesFilter` reads headers and populates SecurityContext:

```java
// GatewayAuthoritiesFilter (from pos-security-common)
String authoritiesHeader = request.getHeader("X-Authorities");
String userHeader = request.getHeader("X-User");

if (authoritiesHeader != null && userHeader != null) {
    List<GrantedAuthority> authorities = Arrays.stream(authoritiesHeader.split(","))
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
    
    Authentication auth = new UsernamePasswordAuthenticationToken(
        userHeader, null, authorities);
    
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

Controller applies `@PreAuthorize`:

```java
@PostMapping
@PreAuthorize("hasRole('SHOP_MGR')")
public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
    // Executes only if user has ROLE_SHOP_MGR authority
    String userId = SecurityContextHolder.getContext()
        .getAuthentication().getName(); // "user123"
    
    Order order = orderService.create(request, userId);
    return ResponseEntity.ok(order);
}
```

### 8. Response Flow

Backend service returns response → Gateway forwards to client with correlation ID:

```http
HTTP/1.1 201 Created
X-Correlation-Id: abc-123-def-456
Content-Type: application/json

{"id": "ord-789", "status": "PENDING", ...}
```

### Error Scenarios

**Missing X-API-Version:**

```json
{
  "code": "BAD_REQUEST",
  "message": "X-API-Version header is required",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

**Invalid JWT:**

```json
{
  "code": "UNAUTHORIZED",
  "message": "Invalid or expired JWT token",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

**Insufficient Permissions:**

```json
{
  "code": "FORBIDDEN",
  "message": "Access denied: requires ROLE_SHOP_MGR",
  "timestamp": "2026-02-01T14:30:00Z",
  "correlationId": "abc-123-def-456"
}
```

---

## Public vs Protected Endpoints

### Public Endpoints (No Authentication Required)

These endpoints bypass JWT authentication at the gateway:

| Path Pattern | Purpose | X-API-Version Required? |
|--------------|---------|-------------------------|
| `/actuator/**` | Spring Boot Actuator (health, metrics) | No |
| `/swagger-ui/**` | Swagger UI for API documentation | No |
| `/v3/api-docs/**` | OpenAPI specification | No |
| `/webjars/**` | Swagger UI static assets | No |
| `/eureka/**` | Service discovery registration | No |

**Example (Health Check):**

```http
GET /actuator/health
Host: api-gateway:8080
```

Response:

```json
{
  "status": "UP"
}
```

No `Authorization` or `X-API-Version` headers required.

### Protected Endpoints (Authentication Required)

All other endpoints require:

1. `X-API-Version` header (MANDATORY)
2. `Authorization: Bearer <JWT>` header (MANDATORY)
3. Valid JWT with non-expired claims
4. Appropriate role assignments for `@PreAuthorize` checks

**Example (Protected Endpoint):**

```http
GET /security/users/123
X-API-Version: 1
Authorization: Bearer eyJhbGci...
X-Correlation-Id: abc-123-def-456
```

### Permission-Based Authorization

Some endpoints require specific roles:

| Endpoint | Required Role(s) | Example |
|----------|------------------|------|
| `POST /security/users` | `ROLE_ADMIN` | Create users |
| `GET /security/permissions` | `ROLE_ADMIN` | View all permissions |
| `POST /security/roles` | `ROLE_ADMIN` | Create roles |
| `GET /security/permissions/domain/{domain}` | `ROLE_ADMIN` or `ROLE_MANAGER` | Domain permissions |
| `POST /security/roles/assignments` | `ROLE_ADMIN` or `ROLE_MANAGER` | Assign roles |

**Example (`@PreAuthorize` Annotation):**

```java
@PostMapping("/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<User> createUser(@RequestBody CreateUserRequest request) {
    // Only executes if X-Authorities contains ROLE_ADMIN
    return ResponseEntity.ok(userService.createUser(request));
}
```

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

### Gateway Routing

All endpoints are accessed through the API Gateway with:

- **Base URL:** `http://api-gateway:8080/security` (production) or `http://localhost:8080/security` (local)
- **Required Headers:** `X-API-Version` (numeric) + `Authorization: Bearer <JWT>` (protected endpoints)
- **Path Rewriting:** Gateway rewrites `/security/{resource}` to `/v{X-API-Version}/{resource}`

**Example:**

```http
GET /security/users/123
X-API-Version: 1
Authorization: Bearer eyJhbGci...
```

Gateway routes to: `http://pos-security-service:8083/v1/users/123`

### Endpoint Summary

This domain exposes **29** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/security/auth/authorities` | Get authorities from token |
| DELETE | `/security/auth/delete` | Delete/revoke JWT token |
| POST | `/security/auth/login` | Authenticate user and issue JWT |
| POST | `/security/auth/refresh` | Refresh access token |
| GET | `/security/auth/roles` | Get roles from token |
| GET | `/security/auth/subject` | Get subject from token |
| POST | `/security/auth/token-pair` | Generate access and refresh token pair |
| GET | `/security/auth/validate` | Validate JWT token |
| GET | `/security/permissions` | Get all registered permissions |
| GET | `/security/permissions/domain/{domain}` | Get permissions by domain |
| GET | `/security/permissions/exists/{permissionName}` | Check if permission exists |
| POST | `/security/permissions/register` | Register permissions from a service |
| GET | `/security/permissions/validate/{permissionName}` | Validate permission name format |
| GET | `/security/roles` | Get all roles |
| POST | `/security/roles` | Create a new role |
| POST | `/security/roles/assignments` | Create role assignment |
| GET | `/security/roles/assignments/user/{userId}` | Get user role assignments |
| DELETE | `/security/roles/assignments/{assignmentId}` | Revoke role assignment |
| GET | `/security/roles/check-permission` | Check user permission |
| PUT | `/security/roles/permissions` | Update role permissions |
| GET | `/security/roles/permissions/user/{userId}` | Get user permissions |
| GET | `/security/roles/{name}` | Get role by name |
| GET | `/security/users` | Get all users |
| POST | `/security/users` | Create a new user |
| POST | `/security/users/login` | User login |
| DELETE | `/security/users/{id}` | Delete a user |
| GET | `/security/users/{id}` | Get user by ID |
| PUT | `/security/users/{id}` | Update an existing user |
| PUT | `/security/users/{username}/roles` | Assign roles to user |

### Endpoint Details

**Note:** All paths shown below use gateway routing. Include `X-API-Version: 1` header to route to `/v1` backend endpoints.

#### GET /security/auth/authorities

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
| 2.0 | 2026-02-01 | Added ADR-0011 gateway-based security architecture, required headers, authentication flow, public vs protected endpoints |
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

---

## References

- **Architecture:** ADR-0011 Gateway-based Security Architecture (`docs/adr/0011-api-gateway-security-architecture.adr.md`)
- **Backend Implementation:** [durion-positivity-backend#417](https://github.com/louisburroughs/durion-positivity-backend/issues/417)
- **Frontend Implementation:** [durion-moqui-frontend#280](https://github.com/louisburroughs/durion-moqui-frontend/issues/280)
- **OpenAPI Specification:** `durion-positivity-backend/pos-security-service/openapi.json`
- **Domain Agent Guide:** `domains/security/.business-rules/AGENT_GUIDE.md`
- **Cross-Domain Integration:** `domains/security/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- **Error Codes:** `domains/security/.business-rules/ERROR_CODES.md`
- **Correlation ID Standards:** `X-Correlation-Id-Implementation-Plan.md`

---

**Last Updated:** 2026-02-01 14:30:00 UTC  
**Maintained By:** Durion Platform Team
