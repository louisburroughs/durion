# Location Management Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-location/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Location Management domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

#### LocationParent.parentType

- `HOME_OFFICE`
- `HEADQUARTERS`
- `REGION`
- `DISTRICT`
- `BILLING`

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
GET /v1/location/entities/123
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
| GET | `/v1/locations` | Get all locations |
| POST | `/v1/locations` | Create a new location |
| GET | `/v1/locations/bays` | Get bays |
| PUT | `/v1/locations/bays` | Manage bays |
| GET | `/v1/locations/mobileUnit` | Get mobile units |
| PUT | `/v1/locations/mobileUnit` | Manage mobile units |
| GET | `/v1/locations/parents` | Get all location parents |
| POST | `/v1/locations/{childId}/parents/{parentId}` | Add a parent to a location |
| DELETE | `/v1/locations/{locationId}` | Delete a location |
| GET | `/v1/locations/{locationId}` | Get location by ID |
| PUT | `/v1/locations/{locationId}` | Update an existing location |
| POST | `/v1/locations/{locationId}/bays` | Create bay |
| DELETE | `/v1/locations/{locationId}/bays/{bayId}` | Delete bay |
| GET | `/v1/locations/{locationId}/bays/{bayId}` | Get bays |
| POST | `/v1/locations/{locationId}/mobileUnit` | Create mobile unit |
| DELETE | `/v1/locations/{locationId}/mobileUnit/{bayId}` | Delete mobile unit |
| GET | `/v1/locations/{locationId}/mobileUnit/{bayId}` | Get mobile units |
| GET | `/v1/locations/{locationId}/responsible-person` | Get responsible person for a location |

### Endpoint Details

#### GET /v1/locations

**Summary:** Get all locations

**Description:** Retrieve a list of all locations.

**Operation ID:** `getAllLocations`

**Responses:**

- `200`: List of locations returned successfully.


---

#### POST /v1/locations

**Summary:** Create a new location

**Description:** Add a new location to the system.

**Operation ID:** `createLocation`

**Responses:**

- `200`: Location created successfully.


---

#### GET /v1/locations/bays

**Summary:** Get bays

**Description:** List all bays or get a specific bay detail by locationId and bayId.

**Operation ID:** `getBays`

**Responses:**

- `200`: Bays retrieved successfully.


---

#### PUT /v1/locations/bays

**Summary:** Manage bays

**Description:** Create or update bays in bulk.

**Operation ID:** `manageBays`

**Responses:**

- `200`: Bays managed successfully.


---

#### GET /v1/locations/mobileUnit

**Summary:** Get mobile units

**Description:** List all mobile units or get a specific mobile unit detail by locationId and bayId.

**Operation ID:** `getMobileUnits`

**Responses:**

- `200`: Mobile units retrieved successfully.


---

#### PUT /v1/locations/mobileUnit

**Summary:** Manage mobile units

**Description:** Create or update mobile units in bulk.

**Operation ID:** `manageMobileUnits`

**Responses:**

- `200`: Mobile units managed successfully.


---

#### GET /v1/locations/parents

**Summary:** Get all location parents

**Description:** Retrieve all parent relationships for locations.

**Operation ID:** `getAllParents`

**Responses:**

- `200`: List of location parents returned successfully.


---

#### POST /v1/locations/{childId}/parents/{parentId}

**Summary:** Add a parent to a location

**Description:** Add a parent relationship to a location.

**Operation ID:** `addParent`

**Parameters:**

- `childId` (path, Required, integer): ID of the child location
- `parentId` (path, Required, integer): ID of the parent location
- `parentType` (query, Required, string): Type of the parent relationship

**Responses:**

- `200`: Parent relationship added successfully.


---

#### DELETE /v1/locations/{locationId}

**Summary:** Delete a location

**Description:** Delete a location by its unique ID.

**Operation ID:** `deleteLocation`

**Parameters:**

- `locationId` (path, Required, integer): ID of the location to delete

**Responses:**

- `204`: Location deleted successfully.
- `404`: Location not found.


---

#### GET /v1/locations/{locationId}

**Summary:** Get location by ID

**Description:** Retrieve a location by its unique ID.

**Operation ID:** `getLocationById`

**Parameters:**

- `locationId` (path, Required, integer): ID of the location to retrieve

**Responses:**

- `200`: Location found and returned.
- `404`: Location not found.


---

#### PUT /v1/locations/{locationId}

**Summary:** Update an existing location

**Description:** Update the details of an existing location.

**Operation ID:** `updateLocation`

**Parameters:**

- `locationId` (path, Required, integer): ID of the location to update

**Responses:**

- `200`: Location updated successfully.
- `404`: Location not found.


---

#### POST /v1/locations/{locationId}/bays

**Summary:** Create bay

**Description:** Create a new bay for a specific location.

**Operation ID:** `createBay`

**Parameters:**

- `locationId` (path, Required, integer): Location ID

**Responses:**

- `200`: Bay created successfully.


---

#### DELETE /v1/locations/{locationId}/bays/{bayId}

**Summary:** Delete bay

**Description:** Delete a specific bay by locationId and bayId.

**Operation ID:** `deleteBay`

**Parameters:**

- `locationId` (path, Required, integer): Location ID
- `bayId` (path, Required, integer): Bay ID

**Responses:**

- `204`: Bay deleted successfully.
- `404`: Bay not found.


---

#### GET /v1/locations/{locationId}/bays/{bayId}

**Summary:** Get bays

**Description:** List all bays or get a specific bay detail by locationId and bayId.

**Operation ID:** `getBays_1`

**Parameters:**

- `locationId` (path, Required, integer): Location ID (optional for specific bay)
- `bayId` (path, Required, integer): Bay ID (optional for specific bay)

**Responses:**

- `200`: Bays retrieved successfully.


---

#### POST /v1/locations/{locationId}/mobileUnit

**Summary:** Create mobile unit

**Description:** Create a new mobile unit for a specific location. Validate baseLocationId and capabilities.

**Operation ID:** `createMobileUnit`

**Parameters:**

- `locationId` (path, Required, integer): Location ID

**Responses:**

- `200`: Mobile unit created successfully.


---

#### DELETE /v1/locations/{locationId}/mobileUnit/{bayId}

**Summary:** Delete mobile unit

**Description:** Delete a specific mobile unit by locationId and bayId.

**Operation ID:** `deleteMobileUnit`

**Parameters:**

- `locationId` (path, Required, integer): Location ID
- `bayId` (path, Required, integer): Bay ID

**Responses:**

- `204`: Mobile unit deleted successfully.
- `404`: Mobile unit not found.


---

#### GET /v1/locations/{locationId}/mobileUnit/{bayId}

**Summary:** Get mobile units

**Description:** List all mobile units or get a specific mobile unit detail by locationId and bayId.

**Operation ID:** `getMobileUnits_1`

**Parameters:**

- `locationId` (path, Required, integer): Location ID (optional for specific mobile unit)
- `bayId` (path, Required, integer): Bay ID (optional for specific mobile unit)

**Responses:**

- `200`: Mobile units retrieved successfully.


---

#### GET /v1/locations/{locationId}/responsible-person

**Summary:** Get responsible person for a location

**Description:** Retrieve the person responsible for a given location.

**Operation ID:** `getResponsiblePerson`

**Parameters:**

- `locationId` (path, Required, integer): ID of the location

**Responses:**

- `200`: Responsible person found and returned.
- `404`: Responsible person not found.



---

## Entity-Specific Contracts

### Location

Location object to be created

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `addressLine1` | string | No |  |
| `addressLine2` | string | No |  |
| `children` | array | No |  |
| `city` | string | No |  |
| `country` | string | No |  |
| `id` | integer (int64) | No |  |
| `mailingAddress` | string | No |  |
| `name` | string | No |  |
| `parents` | array | No |  |
| `postalCode` | string | No |  |
| `responsiblePersonId` | integer (int64) | No |  |
| `state` | string | No |  |


### LocationParent

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `child` | string | No |  |
| `id` | integer (int64) | No |  |
| `parent` | string | No |  |
| `parentType` | string | No |  |


### PersonDTO

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



---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/locations
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
GET /v1/locations/{locationId}
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

This guide establishes standardized contracts for the Location Management domain:

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

- OpenAPI Specification: `pos-location/target/openapi.json`
- Domain Agent Guide: `domains/location/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/location/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/location/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`

---

## CAP-119: Location Management — Generated Contract (summary)

This project includes a generated, capability-specific backend contract for CAP-119. The authoritative implementation document is at the repository path:

- `durion/docs/capabilities/CAP-119/CAP-119-location-backend-contract.md`

### Gateway endpoint mappings

All OpenAPI paths are exposed through the API gateway. Use the following gateway URLs (replace path params as appropriate). Each entry is presented as a single request line in the form `METHOD <gateway-url>`:

- `GET  http://localhost:8080/v1/locations`
- `POST http://localhost:8080/v1/locations`
- `GET  http://localhost:8080/v1/locations/{locationId}`
- `PUT  http://localhost:8080/v1/locations/{locationId}`
- `DELETE http://localhost:8080/v1/locations/{locationId}`
- `POST http://localhost:8080/v1/locations/{childId}/parents/{parentId}?parentType={parentType}`
- `GET  http://localhost:8080/v1/locations/parents`
- `GET  http://localhost:8080/v1/locations/{locationId}/responsible-person`
- `GET  http://localhost:8080/v1/locations/bays`
- `PUT  http://localhost:8080/v1/locations/bays`
- `POST http://localhost:8080/v1/locations/{locationId}/bays`
- `GET  http://localhost:8080/v1/locations/{locationId}/bays/{bayId}`
- `DELETE http://localhost:8080/v1/locations/{locationId}/bays/{bayId}`
- `GET  http://localhost:8080/v1/locations/mobileUnit`
- `PUT  http://localhost:8080/v1/locations/mobileUnit`
- `POST http://localhost:8080/v1/locations/{locationId}/mobileUnit`
- `GET  http://localhost:8080/v1/locations/{locationId}/mobileUnit/{bayId}`
- `DELETE http://localhost:8080/v1/locations/{locationId}/mobileUnit/{bayId}`

### ADR-0016 Compliance (required)
This service and its contract follow ADR-0016 decisions. Key points (must be respected by implementers and consumers):

- `Location` is canonical: `pos-location` is the authoritative source for all location data.
- `LocationType` is a managed entity (not a static enum). Types are runtime-configurable and stored in `pos-location`.
- `ParentType` enum values: `PHYSICAL`, `ORGANIZATIONAL`, `FINANCIAL`, `SHIPPING`. A single child may have at most one parent per `ParentType`.
- `ParentType` enum values: `PHYSICAL`, `ORGANIZATIONAL`, `FINANCIAL`, `SHIPPING`. A single child may have at most one parent per `ParentType`.

Note: The current OpenAPI spec (`pos-location/openapi.json`) defines a different set of parent-type values (`HOME_OFFICE`, `HEADQUARTERS`, `REGION`, `DISTRICT`, `BILLING`). ADR-0016 is authoritative; repository owners should migrate the OpenAPI enum and persistence model to match ADR-0016 or provide a documented mapping layer. See Issue #87 for discussion.

- `GeographicalLocation` is a separate entity holding address and coordinate data; `Location` references it by `geographicalLocationId`.
- Cross-module integration pattern: other services MUST store only `locationId` and query `pos-location` for details, hierarchy, or address when needed.
- Location classifications (examples): Geographical, Physical, Storage (bins/racks), Service (bays), Mobile (wreckers/vans).
- Hierarchy model: self-referencing adjacency list implemented as `Map<ParentType, UUID> parents` (one parent per ParentType key).


### Location entity (schema per ADR-0016)

Represented in JSON for API consumers (example):

```json
{
  "id": "018e1c9f-6b5a-7890-abcd-1234567890ab",
  "code": "MAIN-WH",
  "name": "Main Warehouse",
  "type": {
    "id": "2f4d3a2b-...",
    "name": "Warehouse",
    "description": "Storage facility"
  },
  "parents": {
    "PHYSICAL": "b3a1...",
    "ORGANIZATIONAL": "c4f2..."
  },
  "geographicalLocationId": "d5e6...",
  "status": "ACTIVE",
  "timezone": "America/New_York",
  "responsiblePersonId": 12345,
  "createdAt": "2026-02-17T12:00:00Z",
  "updatedAt": "2026-02-17T12:30:00Z"
}
```

Notes:

- `id` is UUIDv7 encoded as a string.
- `type` references the `LocationType` entity (managed in `pos-location`).
- `parents` is a JSON object mapping `ParentType` string -> parent `locationId` (UUID). Only one entry allowed per `ParentType` key.
- Address/coordinate details live in `GeographicalLocation` and are referenced by `geographicalLocationId`.

### Contract behavioral test hints (naming: `CP-119-NNN`)

- `CP-119-001` Create Location: `POST /v1/locations` returns `201` and Location with generated `id`.
- `CP-119-002` Update Location: `PUT /v1/locations/{locationId}` updates fields and returns `200`; `code` immutable.
- `CP-119-003` Unique Code: Creating a location with duplicate `code` returns `409 Conflict`.
- `CP-119-004` ParentType Uniqueness: Adding a parent with a `parentType` already present for a child should either replace or return `409` (implementation choice; document behavior).
- `CP-119-005` Cross-module reference: Consumers store only `locationId`; verify consumer fetches details by calling `GET /v1/locations/{locationId}` via the gateway.

### Event types (pos-events) — suggested IDs

All write operations MUST emit domain events using pos-events conventions. Suggested event type names:

- `LOCATION_CREATE` (pos.location.v1.LocationCreated)
- `LOCATION_UPDATE` (pos.location.v1.LocationUpdated)
- `LOCATION_DELETE` (pos.location.v1.LocationDeleted)
- `LOCATION_PARENT_ADDED` (pos.location.v1.LocationParentAdded)
- `BAY_MANAGED` (pos.location.v1.BaysManaged)
- `BAY_CREATED` (pos.location.v1.BayCreated)
- `BAY_DELETED` (pos.location.v1.BayDeleted)
- `MOBILE_UNIT_MANAGED` (pos.location.v1.MobileUnitsManaged)
- `MOBILE_UNIT_CREATED` (pos.location.v1.MobileUnitCreated)
- `MOBILE_UNIT_DELETED` (pos.location.v1.MobileUnitDeleted)

Each event should include `eventId` (UUIDv7), `occurredAt` (ISO 8601 UTC), `producer`, `schemaVersion`, and a `payload` containing the resource `id` and change details.

### Links

- Backend issue (implementation / discussion): [Issue #87](https://github.com/louisburroughs/durion-positivity-backend/issues/87)
