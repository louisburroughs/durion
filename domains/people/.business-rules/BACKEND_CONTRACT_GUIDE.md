# pos-people Backend Contract Guide

## Overview
The People domain provides person management, time tracking, and access control integration.

## CAP-118 Traceability

- Capability: `cap:118`
- Parent story: `durion#118`
- Backend implementation PR: `louisburroughs/durion-positivity-backend#526`
- Integration focus: Person-based RBAC facade over pos-security-service role assignment APIs


## Base URL
- Local (service - OpenAPI authoritative): `http://localhost:8085`
- Via API Gateway (recommended): `http://localhost:8080/v1/people`

## CAP-117: Identity Orchestration (User ↔ Person Linking)

This section defines the backend contract for CAP-117 (Identity Orchestration) covering employee profile CRUD, offboarding/disable flows, and user↔person linking.

All gateway paths use: `http://localhost:8080/v1/people` as the base.

### From Issue #88 — Employee Profile CRUD

- POST `http://localhost:8080/v1/people/employees`
  - Purpose: Create employee profile.
  - Request (TypeScript-like):
    ```ts
    interface CreateEmployeeRequest {
      legalName?: string;
      preferredName?: string;
      employeeNumber?: string; // unique
      status?: 'ACTIVE' | 'ON_LEAVE' | 'SUSPENDED' | 'TERMINATED';
      hireDate?: string; // yyyy-MM-dd
      terminationDate?: string; // yyyy-MM-dd
      contactInfo?: {
        emails?: string[];
        phones?: string[];
      };
      duplicatePolicy?: 'STRICT' | 'BALANCED';
    }
    ```
  - Responses:
    - `201 Created`: Employee created. Body: `EmployeeResponse`.
    - `409 Conflict`: High-confidence duplicate detected (exact match on email/phone/employeeNumber).
    - `201 Created` with `warnings` array: Ambiguous duplicate detected (BALANCED policy returned with warnings).
  - Behavior / rules:
    - Duplicate detection: `STRICT` detects exact email/phone/employeeNumber matches; `BALANCED` applies fuzzy/heuristic checks and may return soft-warnings.
    - `employeeNumber` when provided must be unique.
    - Persistence: contactInfo fields optional at DB level but business gating requires ≥1 contact before assignment.

- PUT `http://localhost:8080/v1/people/employees/{employeeId}`
  - Purpose: Update employee profile.
  - Request: same shape as `CreateEmployeeRequest`.
  - Responses:
    - `200 OK`: Updated `EmployeeResponse`.
    - `404 Not Found`: `employeeId` not found.
    - `409 Conflict`: Duplicate detection as above.
  - Behavior:
    - Same duplicate detection logic as create.

- GET `http://localhost:8080/v1/people/employees/{employeeId}`
  - Purpose: Retrieve employee profile.
  - Responses:
    - `200 OK`: `EmployeeResponse`.
    - `404 Not Found` if missing.
  - Employee `status` enum: `ACTIVE | ON_LEAVE | SUSPENDED | TERMINATED`.
  - Field constraints:
    - `terminationDate` must be >= `hireDate` when both present.

EmployeeResponse (TypeScript-like):
```ts
interface EmployeeResponse {
  employeeId: string; // uuid
  legalName?: string;
  preferredName?: string;
  employeeNumber?: string;
  status: 'ACTIVE' | 'ON_LEAVE' | 'SUSPENDED' | 'TERMINATED';
  hireDate?: string; // yyyy-MM-dd
  terminationDate?: string | null;
  contactInfo?: { emails?: string[]; phones?: string[] };
  warnings?: string[];
  createdAt?: string; // date-time
  updatedAt?: string; // date-time
}
```

ContractBehaviorIT hints (naming):
- Happy path: `CP-117-001` (create), `CP-117-002` (update), `CP-117-003` (get)
- Validation errors/VE: `VE-117-001` (terminationDate < hireDate), `VE-117-002` (missing contact when required)
- Lifecycle/LC: `LC-117-001` (status transitions)

### From Issue #90 — Disable User (Offboarding)

- POST `http://localhost:8080/v1/people/employees/{employeeId}/disable`
  - Purpose: Disable/offboard an employee's user account and mark associated person/user as DISABLED. Implements a saga-style pattern for downstream coordination.
  - Request body:
    ```json
    {
      "disableReason": "string (optional)",
      "assignmentPolicy": "END_ASSIGNMENTS_NOW|END_ASSIGNMENTS_AT_DATE|LEAVE_ASSIGNMENTS_ACTIVE",
      "assignmentEndDate": "2026-12-31" // optional when END_ASSIGNMENTS_AT_DATE
    }
    ```
  - Responses:
    - `200 OK`: Action accepted and executed (person+user status set to `DISABLED`), body: operation result.
    - `400 Bad Request`: If employee already `DISABLED` or `TERMINATED` or invalid payload.
    - `404 Not Found`: `employeeId` not found.
  - Behavior / business rules:
    - Allowed transitions: `ACTIVE -> DISABLED` (reversible via admin restore if supported); `ACTIVE -> TERMINATED` (irreversible).
    - If already `DISABLED` or `TERMINATED` return `400` with problem details.
    - Event: immediately emits `user.disabled` (pos-events) with payload indicating `employeeId`, `userId` (if linked), `disableReason`, and `assignmentPolicy`.
    - Saga: downstream services (security, workexec, payroll) consume `user.disabled` asynchronously and perform their part; retries are handled by consumer logic.

ContractBehaviorIT hints:
- CP-117-010: disable happy path (verify event emitted)
- VE-117-010: disabling already DISABLED returns 400
- LC-117-010: verify status transition rules and irreversibility of TERMINATED

### From Issue #91 — Provision User + Link to Person

- POST `http://localhost:8080/v1/people/user-links`
  - Purpose: Create a `UserPersonLink` binding between an authentication `userId` and a `personId`. Idempotent.
  - Request (TypeScript-like):
    ```ts
    interface CreateUserLinkRequest {
      userId: string; // uuid
      personId: string; // uuid
    }
    ```
  - Responses:
    - `201 Created`: Link created (body: `UserPersonLinkResponse`).
    - `200 OK`: Link already exists (idempotent behaviour).
    - `409 Conflict`: `userId` already linked to a different `personId` (1:1 constraint).
    - `404 Not Found`: `personId` not found.
  - Behavior:
    - This endpoint is administratively callable; the same operation is performed by an event-driven consumer when `UserCreated` / `UserProvisioned` events arrive from the security/provisioning service.
    - Unique constraint: `userId` -> single `personId`. Attempting to link the same `userId` to another `personId` returns `409`.

- GET `http://localhost:8080/v1/people/user-links/{personId}`
  - Purpose: Retrieve current `UserPersonLink` for a given `personId`.
  - Responses:
    - `200 OK`: `UserPersonLinkResponse` (or empty if not linked).
    - `404 Not Found`: `personId` not found.

UserPersonLinkResponse (TypeScript-like):
```ts
interface UserPersonLinkResponse {
  linkId: string; // uuid
  userId: string;
  personId: string;
  createdAt?: string; // date-time
  createdBy?: string;
}
```

ContractBehaviorIT hints:
- CP-117-020: create link happy path (201)
- CP-117-021: idempotent re-create returns 200
- VE-117-020: 409 on conflicting userId link

### Events
- `user.disabled` — emitted by disable endpoint (immediate, write preset)
- `USER_PERSON_LINK_CREATE` — emitted on successful link creation (existing OpenAPI emits `USER_PERSON_LINK_CREATE` for `/users/{userId}/link` endpoints)

### Test & Naming Guidance
- Use `CP-117-NNN` for capability happy-path tests, `VE-117-NNN` for validation/edge cases, `LC-117-NNN` for lifecycle transitions.
- Ensure tests cover idempotency, duplicate detection policies (`STRICT|BALANCED`), assignmentPolicy effects, and event emission (verify event payload shape).

---


## Authentication & Headers
- Standard headers: X-User-Id, X-Correlation-Id, X-Permissions
- Authentication via JWT tokens (coordinated through API Gateway)

## Endpoints

### Person Access Control
#### GET /v1/people/{personUuid}/access/roles
**Purpose:** List available access roles for people (LOCATION and GLOBAL scope roles only)

**Request:**
- Path Parameters:
  - `personUuid` (UUID, required): Person identifier

**Response:** 200 OK
```json
[
  {
    "code": "SHOP_MANAGER",
    "name": "Shop Manager",
    "description": "Manage shop operations",
    "scopeType": "LOCATION",
    "active": true
  }
```

**Event:** `PEOPLE_ACCESS_ROLES_LIST`

---
#### GET /v1/people/{personUuid}/access/assignments

**Request:**
  - `endDate` (date-time, optional): Filter assignments active at this date (ISO 8601)
- Path Parameters:
  - `personUuid` (UUID, required): Person identifier
- Query Parameters:
  - `includeHistory` (Boolean, optional): Include historical/inactive assignments
  - `endDate` (LocalDateTime, optional): Filter assignments active at this date

**Response:** 200 OK
```json
[
  {
    "userId": "user123",
    "roleCode": "SHOP_MANAGER",
    "locationId": "uuid-of-location",
    "startDate": "2026-01-01T00:00:00",
    "endDate": null,
    "active": true
  }
```

**Event:** `PEOPLE_ACCESS_ASSIGNMENTS_LIST`

---
#### POST /v1/people/{personUuid}/access/assignments

**Request:**
- Path Parameters:
  - `personUuid` (UUID, required): Person identifier
- Body:
```json
{
  "roleCode": "SHOP_MANAGER",
  "locationId": "uuid-of-location",
  "startDate": "2026-02-16T00:00:00",
  "endDate": null
}
```

**Response:** 201 Created
```json
{
  "userId": "user123",
  "roleCode": "SHOP_MANAGER",
  "locationId": "uuid-of-location",
  "startDate": "2026-02-16T00:00:00",
  "endDate": null,
  "active": true
```

**Event:** `PEOPLE_ACCESS_ASSIGNMENT_CREATE`

**Validation:**
- `locationId` required for LOCATION scope roles, must be null for GLOBAL scope
- Person must have a linked User account (from UserPersonLink)
---


**Note (OpenAPI authoritative):** The OpenAPI spec uses `{roleCode}` (role identifier) as the path parameter for revoke operations, not a numeric `assignmentId`. If your client still references an `assignmentId`, map it to the role code or use the backend issue below to coordinate a migration.

**Request:**
- Path Parameters:
- Query Parameters:
  - `endDate` (date-time, optional): Effective end date (defaults to now)

**Responses:**
- `204 No Content`: Role assignment revoked successfully
- `404 Not Found`: Person or role assignment not found
- `400 Bad Request`: Invalid request for revoking role assignment

**Event:** `PEOPLE_ACCESS_ASSIGNMENT_REVOKE`

**Coordination / Backlog:** https://github.com/louisburroughs/durion-positivity-backend/issues/86

---

## Integration with pos-security-service

The Person Access Control endpoints provide a facade over pos-security-service:

1. **UserPersonTranslationService** translates Person UUIDs to User IDs using the UserPersonLink entity
2. **SecurityServiceClient** makes REST calls to pos-security-service endpoints
3. **PeopleAccessControlService** orchestrates the translation and security service calls
4. **PersonAccessController** exposes person-centric REST endpoints

**Service Dependencies:**
- pos-security-service must be running and accessible at `${pos.security-service.base-url}` (default: http://localhost:8084)

**Error Handling:**
- 404 Not Found: Person has no linked User account
- 400 Bad Request: Invalid role code, scope mismatch, or validation failure
- Errors from pos-security-service are propagated with appropriate context

## Events
All endpoints emit events to pos-events service for audit and observability:
- `PEOPLE_ACCESS_ROLES_LIST` (fastRead preset)
- `PEOPLE_ACCESS_ASSIGNMENTS_LIST` (fastRead preset)
- `PEOPLE_ACCESS_ASSIGNMENT_CREATE` (write preset)
- `PEOPLE_ACCESS_ASSIGNMENT_REVOKE` (write preset)

## Example Workflows

### Assign Shop Manager Role to Person
1. GET /v1/people/{personUuid}/access/roles - List available roles
2. POST /v1/people/{personUuid}/access/assignments - Assign SHOP_MANAGER role
3. GET /v1/people/{personUuid}/access/assignments - Verify assignment

### View Historical Role Assignments
1. GET /v1/people/{personUuid}/access/assignments?includeHistory=true - Get all assignments including inactive

### Revoke Role Effective Future Date
1. DELETE /v1/people/{personUuid}/access/assignments/{roleCode}?endDate=2026-12-31T23:59:59 - Schedule future revocation

---

## Legacy Appendix (Auto-Generated Domain Contract)

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

This domain exposes **29** REST API endpoints:

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
| POST | `/v1/people/users/{userId}/link` | Link user to person |
| DELETE | `/v1/people/users/{userId}/link` | Unlink user from person |
| GET | `/v1/people/users/{userId}/person` | Get person by user ID |
| GET | `/v1/people/{personId}/access/roles` | List available roles |
| GET | `/v1/people/{personId}/access/assignments` | Get role assignments for person |
| POST | `/v1/people/{personId}/access/assignments` | Create role assignment for person |
| DELETE | `/v1/people/{personId}/access/assignments/{assignmentId}` | Revoke role assignment |
| GET | `/v1/people/{personId}/users` | Get users linked to person |

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

- `201`: Person created successfully.


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

- `201`: Exception created
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

- `201`: Adjustment created
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

- `201`: Work session started successfully.


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

- `201`: Break started successfully.
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

- `personId` (path, Required, string (uuid)): ID of the person to delete

**Responses:**

- `204`: Person deleted successfully.
- `404`: Person not found.


---

#### GET /v1/people/{personId}

**Summary:** Get person by ID

**Description:** Retrieve a person by their unique ID.

**Operation ID:** `getPersonById`

**Parameters:**

- `personId` (path, Required, string (uuid)): ID of the person to retrieve

**Responses:**

- `200`: Person found and returned.
- `404`: Person not found.


---

#### PUT /v1/people/{personId}

**Summary:** Update an existing person

**Description:** Update the details of an existing person.

**Operation ID:** `updatePerson`

**Parameters:**

- `personId` (path, Required, string (uuid)): ID of the person to update

**Responses:**

- `200`: Person updated successfully.
- `404`: Person not found.



---

#### POST /v1/people/users/{userId}/link

**Summary:** Link user to person

**Description:** Create a link between an authentication user and a person record.

**Operation ID:** `linkUserToPerson`

**Parameters:**

- `userId` (path, Required, string): User ID from authentication system

**Request Body:**

- `LinkUserToPersonRequest` (application/json)

**Responses:**

- `201 Created`: Link created successfully. Returns `UserPersonLinkResponse`.
- `400 Bad Request`: Invalid request (validation error). Returns error body.
- `404 Not Found`: Person not found. Returns error body.
- `409 Conflict`: User already linked. Returns `UserPersonLinkResponse` with existing link info.

**Event:** `USER_PERSON_LINK_CREATE` (controller annotated with `@EmitEvent(id = "USER_PERSON_LINK_CREATE", apiVersion = "1")`)

**Example Request:**

```http
POST http://localhost:8080/v1/people/users/abc-123/link
Content-Type: application/json
X-Correlation-Id: abc-123-def-456

{
  "userId": "abc-123",
  "personId": "123e4567-e89b-12d3-a456-426614174000",
  "linkType": "PRIMARY",
  "notes": "Linked during onboarding"
}
```

**Example 201 Response:**

```http
HTTP/1.1 201 Created
X-Correlation-Id: abc-123-def-456

{
  "linkId": "9f8b7a6c-5d4e-4b2a-8f1e-0a1b2c3d4e5f",
  "userId": "abc-123",
  "personId": "123e4567-e89b-12d3-a456-426614174000",
  "linkType": "PRIMARY",
  "createdAt": "2026-02-16T12:00:00Z",
  "createdBy": "system",
  "notes": "Linked during onboarding"
}
```

---

#### DELETE /v1/people/users/{userId}/link

**Summary:** Unlink user from person

**Description:** Remove the link between a user and person.

**Operation ID:** `unlinkUserFromPerson`

**Parameters:**

- `userId` (path, Required, string): User ID

**Responses:**

- `204 No Content`: Link deleted successfully.
- `404 Not Found`: Link not found.

**Event:** `USER_PERSON_LINK_DELETE` (controller annotated with `@EmitEvent(id = "USER_PERSON_LINK_DELETE", apiVersion = "1")`)

**Example Request:**

```http
DELETE http://localhost:8080/v1/people/users/abc-123/link
X-Correlation-Id: abc-123-def-456
```

**Example 204 Response:**

```http
HTTP/1.1 204 No Content
X-Correlation-Id: abc-123-def-456
```

---

#### GET /v1/people/users/{userId}/person

**Summary:** Get person by user ID

**Description:** Retrieve the person record linked to a user.

**Operation ID:** `getPersonByUserId`

**Parameters:**

- `userId` (path, Required, string): User ID

**Responses:**

- `200 OK`: Person found. Returns `PersonResponse`.
- `404 Not Found`: Link or person not found.

**Example Request:**

```http
GET http://localhost:8080/v1/people/users/abc-123/person
X-Correlation-Id: abc-123-def-456
```

**Example 200 Response:**

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456

{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "firstName": "Jane",
  "lastName": "Doe",
  "primaryEmail": "jane.doe@example.com",
  "phoneNumbers": ["+15551234567"],
  "username": "jdoe"
}
```

---

#### GET /v1/people/{personId}/users

**Summary:** Get users linked to person

**Description:** Retrieve all user IDs linked to a person record.

**Operation ID:** `getUserIdsByPersonId`

**Parameters:**

- `personId` (path, Required, string): Person ID

**Responses:**

- `200 OK`: User IDs returned (array of strings).
- `404 Not Found`: Person not found.

**Example Request:**

```http
GET http://localhost:8080/v1/people/123e4567-e89b-12d3-a456-426614174000/users
X-Correlation-Id: abc-123-def-456
```

**Example 200 Response:**

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456

["abc-123", "def-456"]
```

---

#### GET /v1/people/{personId}/access/roles

**Summary:** List available roles

**Description:** Returns all available roles that can be assigned through person-centric access APIs.

**Operation ID:** `getRoles`

**Parameters:**

- `personId` (path, Required, string): Person ID

**Responses:**

- `200 OK`: Available roles returned.

**Event:** `PEOPLE_ACCESS_ROLES_LIST`

---

#### GET /v1/people/{personId}/access/assignments

**Summary:** Get role assignments for person

**Description:** Returns role assignments for the linked user account behind the person. Use `includeHistory=true` to include expired/revoked assignments.

**Operation ID:** `getAssignments`

**Parameters:**

- `personId` (path, Required, string): Person ID
- `includeHistory` (query, Optional, boolean): Include historical assignments (default `false`)

**Responses:**

- `200 OK`: Role assignments returned.
- `404 Not Found`: Person-to-user link not found.

**Event:** `PEOPLE_ACCESS_ASSIGNMENTS_LIST`

---

#### POST /v1/people/{personId}/access/assignments

**Summary:** Create role assignment for person

**Description:** Creates a new role assignment for the linked user account behind the person.

**Operation ID:** `createAssignment`

**Parameters:**

- `personId` (path, Required, string): Person ID

**Request Body:** `RoleAssignmentRequest`

**Responses:**

- `201 Created`: Assignment created.
- `400 Bad Request`: Invalid request payload.
- `404 Not Found`: Person-to-user link not found.

**Event:** `PEOPLE_ACCESS_ASSIGNMENT_CREATE`

---

#### DELETE /v1/people/{personId}/access/assignments/{assignmentId}

**Summary:** Revoke role assignment

**Description:** Revokes a role assignment by setting its end date. If `endDate` is omitted, current date is used.

**Operation ID:** `revokeAssignment`

**Parameters:**

- `personId` (path, Required, string): Person ID
- `assignmentId` (path, Required, string): Role assignment ID
- `endDate` (query, Optional, string date): Effective revocation date (`yyyy-MM-dd`)

**Responses:**

- `204 No Content`: Assignment revoked.
- `404 Not Found`: Assignment not found.

**Event:** `PEOPLE_ACCESS_ASSIGNMENT_REVOKE`

---

## Entity-Specific Contracts

### User-Person Linking

Schemas for the User-Person linking endpoints.

#### LinkUserToPersonRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | string | Yes | Authentication user ID (also present in path) |
| `personId` | string (uuid) | Yes | Target person ID to link |
| `linkType` | string | No | Type of link (for example `PRIMARY`, `SECONDARY`) |
| `notes` | string | No | Free-form notes about the link |

#### UserPersonLinkResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `linkId` | string (uuid) | No | Unique ID for the created link |
| `userId` | string | No | Authentication user ID |
| `personId` | string (uuid) | No | Linked person ID |
| `linkType` | string | No | Link type |
| `createdAt` | string (date-time) | No | Timestamp when link was created |
| `createdBy` | string | No | Actor who created the link |
| `notes` | string | No | Notes provided during linking |

#### PersonResponse

See `Person` fields above; `PersonResponse` mirrors the returned Person representation for linking endpoints.

### Decision

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `rejectionReason` | string | No |  |
| `timeEntryId` | string | No |  |


### Person

Person object to be created

**Fields:**

| Field            | Type          | Required | Description |
|------------------|---------------|----------|-------------|
| `firstName`      | string        | No       |             |
| `id`             | string (uuid) | No       |             |
| `lastName`       | string        | No       |             |
| `phoneNumbers`   | array         | No       |             |
| `primaryEmail`   | string        | No       |             |
| `secondaryEmail` | string        | No       |             |
| `username`       | string        | No       |             |


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
POST http://localhost:8080/v1/people
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
GET http://localhost:8080/v1/people/{personId}
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
| 1.2 | 2026-02-18 | Added CAP-117: Identity Orchestration (Employee profile CRUD, disable user/offboarding, user-person linking admin API). |
| 1.1 | 2026-02-16 | Added person-centric RBAC facade endpoints under `/v1/people/{personId}/access` (roles list, assignments list, create assignment, revoke assignment) for CAP-118 |
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

---

## References

- OpenAPI Specification: `pos-people/target/openapi.json`
- Domain Agent Guide: `domains/people/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/people/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/people/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-02-16 19:25:00 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
