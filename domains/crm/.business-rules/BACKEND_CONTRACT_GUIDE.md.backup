# Customer Relationship Management (CRM) Backend Contract Guide

**Version:** 1.3  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-02-03  
**OpenAPI Source:** `pos-customer/openapi.json`  
**Additional OpenAPI Source (Vehicle Registry / CAP:091):** `durion-positivity-backend/pos-vehicle-inventory/openapi.json`  
**Status:** Accepted

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Customer Relationship Management (CRM) domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

No enums defined in OpenAPI spec. Standard enum conventions apply:

- Use UPPER_SNAKE_CASE
- Document all possible values
- Avoid numeric codes

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
GET /v1/crm/entities/123
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

This domain exposes **25** CRM REST API endpoints, plus **9** vehicle registry endpoints (CAP:091).

Vehicle-only lookups belong in pos-vehicle-inventory; this CRM domain contract retains customer-vehicle relationship endpoints.

| Method | Path | Summary |
|--------|------|---------|
| GET | `/v1/crm` | Get all customers |
| POST | `/v1/crm` | Create a new customer |
| POST | `/v1/crm/accounts/parties` | Create commercial account |
| POST | `/v1/crm/accounts/parties/search` | Search parties |
| GET | `/v1/crm/accounts/parties/{partyId}` | Get party details |
| GET | `/v1/crm/accounts/parties/{partyId}/communicationPreferences` | Get communication preferences |
| POST | `/v1/crm/accounts/parties/{partyId}/communicationPreferences` | Create or update communication preferences |
| GET | `/v1/crm/accounts/parties/{partyId}/contacts` | Get contacts with roles |
| PUT | `/v1/crm/accounts/parties/{partyId}/contacts/{contactId}/roles` | Update contact roles |
| POST | `/v1/crm/accounts/parties/{partyId}/merge` | Merge parties |
| POST | `/v1/crm/accounts/parties/{partyId}/vehicles` | Create vehicle for party |
| POST | `/v1/crm/accounts/tierResolve` | Resolve account tier |
| GET | `/v1/crm/accounts/{accountId}/tier` | Get account tier |
| GET | `/v1/crm/parties/{partyId}/communicationPreferences` | Get communication preferences |
| POST | `/v1/crm/parties/{partyId}/communicationPreferences` | Create or update communication preferences |
| GET | `/v1/crm/parties/{partyId}/contacts` | Get contacts with roles |
| PUT | `/v1/crm/parties/{partyId}/contacts/{contactId}/roles` | Update contact roles |
| POST | `/v1/crm/{customerId}/vehicles` | Create vehicle |
| PUT | `/v1/crm/{customerId}/vehicles` | Update vehicle |
| DELETE | `/v1/crm/{customerId}/vehicles/{vehicleId}` | Delete vehicle |
| GET | `/v1/crm/{customerId}/vehicles/{vehicleId}` | Get vehicle for customer |
| PUT | `/v1/crm/{customerId}/vehicles/{vehicleId}/transfer` | Transfer vehicle |
| DELETE | `/v1/crm/{id}` | Delete a customer |
| GET | `/v1/crm/{id}` | Get customer by ID |
| PUT | `/v1/crm/{id}` | Update an existing customer |

#### Vehicle Registry Endpoint Summary (CAP:091)

These endpoints are produced by the vehicle inventory service OpenAPI snapshot:

`durion-positivity-backend/pos-vehicle-inventory/openapi.json`

| Method | Path | Summary |
|--------|------|---------|
| GET | `/v1/vehicles` | Get all vehicles |
| POST | `/v1/vehicles` | Create a new vehicle |
| GET | `/v1/vehicles/{id}` | Get vehicle by ID |
| PUT | `/v1/vehicles/{id}` | Update vehicle by ID |
| DELETE | `/v1/vehicles/{id}` | Delete vehicle by ID |
| GET | `/v1/vehicles/vin/{vin}` | Get vehicle by VIN |
| PUT | `/v1/vehicles/vin/{vin}` | Update vehicle by VIN |
| DELETE | `/v1/vehicles/vin/{vin}` | Delete vehicle by VIN |
| POST | `/v1/vehicles/vin` | Create vehicle by VIN |

### Endpoint Details

#### GET /v1/crm

**Summary:** Get all customers

**Description:** Retrieve a list of all customers.

**Operation ID:** `getAllCustomers`

**Responses:**

- `200`: List of customers returned successfully.


---

#### POST /v1/crm

**Summary:** Create a new customer

**Description:** Add a new customer to the system.

**Operation ID:** `createCustomer`

**Responses:**

- `200`: Customer created successfully.


---

#### POST /v1/crm/accounts/parties

**Summary:** Create commercial account

**Description:** Create a new commercial party/account in the CRM system

**Operation ID:** `createCommercialAccount`

**Responses:**

- `201`: Account created successfully
- `400`: Invalid request
- `403`: Forbidden - insufficient permissions


---

#### POST /v1/crm/accounts/parties/search

**Summary:** Search parties

**Description:** Search for parties based on various criteria

**Operation ID:** `searchParties`

**Responses:**

- `200`: Search results returned
- `400`: Invalid search criteria
- `403`: Forbidden - insufficient permissions


---

#### GET /v1/crm/accounts/parties/{partyId}

**Summary:** Get party details

**Description:** Retrieve details for a specific party by ID

**Operation ID:** `getParty`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `200`: Party details retrieved successfully
- `403`: Forbidden - insufficient permissions
- `404`: Party not found


---

#### GET /v1/crm/accounts/parties/{partyId}/communicationPreferences

**Summary:** Get communication preferences

**Description:** Retrieve communication preferences and consent flags for a party

**Operation ID:** `getCommunicationPreferences_1`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `200`: Preferences retrieved successfully
- `403`: Forbidden - insufficient permissions
- `404`: Party not found

**Contract (Response 200):** `GetCommunicationPreferencesResponse`

**Response Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "partyId": { "type": "string" },
    "version": { "type": "string" },
    "emailPreference": { "type": "string" },
    "smsPreference": { "type": "string" },
    "phonePreference": { "type": "string" },
    "marketingPreference": { "type": "string" },
    "consentFlags": { "type": "object", "additionalProperties": { "type": "boolean" } },
    "preferencesNote": { "type": "string" },
    "updatedAt": { "type": "string" },
    "updateSource": { "type": "string" }
  }
}
```

**Provider Test Hints (ContractBehaviorIT):**
- Add tests for: `200`, `403`, `404` per OpenAPI
- TODO (CAP:090 / backend issue #107): add concrete example payload/expected body assertions once backend behavior is finalized


---

#### POST /v1/crm/accounts/parties/{partyId}/communicationPreferences

**Summary:** Create or update communication preferences

**Description:** Set or update communication preferences and consent flags for a party

**Operation ID:** `upsertCommunicationPreferences_1`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `200`: Preferences updated successfully
- `400`: Invalid preference data
- `403`: Forbidden - insufficient permissions
- `404`: Party not found

**Contract (Request Body):** `UpsertCommunicationPreferencesRequest`

**Request Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "emailPreference": { "type": "string" },
    "smsPreference": { "type": "string" },
    "phonePreference": { "type": "string" },
    "marketingPreference": { "type": "string" },
    "consentFlags": { "type": "object", "additionalProperties": { "type": "boolean" } },
    "preferencesNote": { "type": "string" },
    "updateSource": { "type": "string" }
  }
}
```

**Contract (Response 200):** `UpsertCommunicationPreferencesResponse`

**Response Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "partyId": { "type": "string" },
    "version": { "type": "string" },
    "operationType": { "type": "string" },
    "status": { "type": "string" },
    "updatedAt": { "type": "string" }
  }
}
```

**Provider Test Hints (ContractBehaviorIT):**
- Add tests for: `200`, `400`, `403`, `404` per OpenAPI
- TODO (CAP:090 / backend issue #107): define authoritative example(s) and validation rules for “Invalid preference data” → `400`


---

#### GET /v1/crm/accounts/parties/{partyId}/contacts

**Summary:** Get contacts with roles

**Description:** Retrieve all contacts for a party including their role assignments

**Operation ID:** `getContactsWithRoles_1`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `200`: Contacts retrieved successfully
- `403`: Forbidden - insufficient permissions
- `404`: Party not found

**Contract (Response 200):** `GetContactsWithRolesResponse`

**Response Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "partyId": { "type": "string" },
    "contacts": {
      "type": "array",
      "items": { "$ref": "#/components/schemas/ContactWithRoles" }
    }
  }
}
```

**Provider Test Hints (ContractBehaviorIT):**
- Add tests for: `200`, `403`, `404` per OpenAPI
- TODO (CAP:090 / backend issue #106): define example contact list shapes (incl. multiple contact points)


---

#### PUT /v1/crm/accounts/parties/{partyId}/contacts/{contactId}/roles

**Summary:** Update contact roles

**Description:** Assign or update role assignments for a specific contact within a party

**Operation ID:** `updateContactRoles_1`

**Parameters:**

- `partyId` (path, Required, string): Party ID
- `contactId` (path, Required, string): Contact ID

**Responses:**

- `200`: Roles updated successfully
- `400`: Invalid role assignment
- `403`: Forbidden - insufficient permissions
- `404`: Party or contact not found

**Contract (Request Body):** `UpdateContactRolesRequest`

**Request Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "version": { "type": "string" },
    "roles": {
      "type": "array",
      "items": { "$ref": "#/components/schemas/RoleAssignment" }
    }
  }
}
```

**Contract (Response 200):** `UpdateContactRolesResponse`

**Response Schema (JSON Schema, abridged):**
```json
{
  "type": "object",
  "properties": {
    "partyId": { "type": "string" },
    "contactId": { "type": "string" },
    "version": { "type": "string" },
    "status": { "type": "string" },
    "updatedAt": { "type": "string" }
  }
}
```

**Provider Test Hints (ContractBehaviorIT):**
- Add tests for: `200`, `400`, `403`, `404` per OpenAPI
- TODO (CAP:090 / backend issue #108): define valid roleCode set + rules for primary flags and “Invalid role assignment” → `400`


---

#### POST /v1/crm/accounts/parties/{partyId}/merge

**Summary:** Merge parties

**Description:** Merge multiple parties into a single party record

**Operation ID:** `mergeParties`

**Parameters:**

- `partyId` (path, Required, string): Target party ID

**Responses:**

- `200`: Parties merged successfully
- `400`: Invalid merge request
- `403`: Forbidden - insufficient permissions
- `404`: Party not found


---

#### POST /v1/crm/accounts/parties/{partyId}/vehicles

**Summary:** Create vehicle for party

**Description:** Associate a new vehicle with a party/customer

**Operation ID:** `createVehicleForParty`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `201`: Vehicle created successfully
- `400`: Invalid vehicle data
- `403`: Forbidden - insufficient permissions
- `404`: Party not found


---

#### POST /v1/crm/accounts/tierResolve

**Summary:** Resolve account tier

**Description:** Resolve or compute the account tier based on business rules (stub endpoint)

**Operation ID:** `resolveAccountTier`

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### GET /v1/crm/accounts/{accountId}/tier

**Summary:** Get account tier

**Description:** Retrieve the tier level for a specific account (stub endpoint)

**Operation ID:** `getAccountTier`

**Parameters:**

- `accountId` (path, Required, string): Account ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### GET /v1/crm/parties/{partyId}/communicationPreferences

**Summary:** Get communication preferences

**Description:** Retrieve communication preferences and consent flags for a party (stub endpoint)

**Operation ID:** `getCommunicationPreferences`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### POST /v1/crm/parties/{partyId}/communicationPreferences

**Summary:** Create or update communication preferences

**Description:** Set or update communication preferences and consent flags for a party (stub endpoint)

**Operation ID:** `upsertCommunicationPreferences`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### GET /v1/crm/parties/{partyId}/contacts

**Summary:** Get contacts with roles

**Description:** Retrieve all contact points for a party with their role assignments (stub endpoint)

**Operation ID:** `getContactsWithRoles`

**Parameters:**

- `partyId` (path, Required, string): Party ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### PUT /v1/crm/parties/{partyId}/contacts/{contactId}/roles

**Summary:** Update contact roles

**Description:** Assign or update role assignments for a specific contact within a party (stub endpoint)

**Operation ID:** `updateContactRoles`

**Parameters:**

- `partyId` (path, Required, string): Party ID
- `contactId` (path, Required, string): Contact ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---



#### POST /v1/crm/{customerId}/vehicles

**Summary:** Create vehicle

**Description:** Create a new vehicle for a customer (stub endpoint)

**Operation ID:** `createVehicles`

**Parameters:**

- `customerId` (path, Required, string): Customer ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### PUT /v1/crm/{customerId}/vehicles

**Summary:** Update vehicle

**Description:** Update vehicle information for a customer (stub endpoint)

**Operation ID:** `updateVehicles`

**Parameters:**

- `customerId` (path, Required, string): Customer ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### DELETE /v1/crm/{customerId}/vehicles/{vehicleId}

**Summary:** Delete vehicle

**Description:** Delete or deactivate a vehicle for a customer (stub endpoint)

**Operation ID:** `deleteVehicle`

**Parameters:**

- `customerId` (path, Required, string): Customer ID
- `vehicleId` (path, Required, string): Vehicle ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### GET /v1/crm/{customerId}/vehicles/{vehicleId}

**Summary:** Get vehicle for customer

**Description:** Retrieve a specific vehicle for a given customer (stub endpoint)

**Operation ID:** `getVehiclesForCustomer`

**Parameters:**

- `customerId` (path, Required, string): Customer ID
- `vehicleId` (path, Required, string): Vehicle ID

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### PUT /v1/crm/{customerId}/vehicles/{vehicleId}/transfer

**Summary:** Transfer vehicle

**Description:** Transfer vehicle ownership between customers (stub endpoint)

**Operation ID:** `transferVehicles`

**Parameters:**

- `customerId` (path, Required, string): Source customer ID
- `vehicleId` (path, Required, string): Vehicle ID to transfer

**Responses:**

- `403`: Forbidden - insufficient permissions
- `501`: Not implemented


---

#### DELETE /v1/crm/{id}

**Summary:** Delete a customer

**Description:** Delete a customer by their unique ID.

**Operation ID:** `deleteCustomer`

**Parameters:**

- `id` (path, Required, integer): ID of the customer to delete

**Responses:**

- `204`: Customer deleted successfully.
- `404`: Customer not found.


---

#### GET /v1/crm/{id}

**Summary:** Get customer by ID

**Description:** Retrieve a customer by their unique ID.

**Operation ID:** `getCustomerById`

**Parameters:**

- `id` (path, Required, integer): ID of the customer to retrieve

**Responses:**

- `200`: Customer found and returned.
- `404`: Customer not found.


---

#### PUT /v1/crm/{id}

**Summary:** Update an existing customer

**Description:** Update the details of an existing customer.

**Operation ID:** `updateCustomer`

**Parameters:**

- `id` (path, Required, integer): ID of the customer to update

**Responses:**

- `200`: Customer updated successfully.
- `404`: Customer not found.



---

### Vehicle Registry Endpoints (CAP:091)

#### GET /v1/vehicles

**Summary:** Get all vehicles

**Description:** Retrieve a list of all vehicles in the inventory.

**Operation ID:** `getAllVehicles`

**Responses:**

- `200`: List of vehicles returned successfully.

**Response Schema (JSON Schema, abridged):**

```json
{
  "type": "array",
  "items": {
    "$ref": "#/components/schemas/VehicleEntity"
  }
}
```

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`
- TODO (CAP:091): define auth requirements (if any) and error model for `4xx/5xx`

---

#### POST /v1/vehicles

**Summary:** Create a new vehicle

**Description:** Add a new vehicle to the inventory.

**Operation ID:** `createVehicle`

**Request Schema (JSON Schema, abridged):**

```json
{
  "$ref": "#/components/schemas/VehicleEntity"
}
```

**Responses:**

- `200`: Vehicle created successfully.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`
- TODO (CAP:091): define `400` conditions (validation) and error payload shape

---

#### GET /v1/vehicles/{id}

**Summary:** Get vehicle by ID

**Description:** Retrieve a vehicle by its unique ID.

**Operation ID:** `getVehicle`

**Parameters:**

- `id` (path, Required, integer): ID of the vehicle to retrieve

**Responses:**

- `200`: Vehicle found and returned.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`, `404`

---

#### PUT /v1/vehicles/{id}

**Summary:** Update vehicle by ID

**Description:** Update an existing vehicle's details by its ID.

**Operation ID:** `updateVehicle`

**Parameters:**

- `id` (path, Required, integer): ID of the vehicle to update

**Request Schema (JSON Schema, abridged):**

```json
{
  "$ref": "#/components/schemas/VehicleEntity"
}
```

**Responses:**

- `200`: Vehicle updated successfully.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`, `404`

---

#### DELETE /v1/vehicles/{id}

**Summary:** Delete vehicle by ID

**Description:** Delete a vehicle from the inventory by its ID.

**Operation ID:** `deleteVehicle`

**Parameters:**

- `id` (path, Required, integer): ID of the vehicle to delete

**Responses:**

- `204`: Vehicle deleted successfully.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `204`, `404`

---

#### GET /v1/vehicles/vin/{vin}

**Summary:** Get vehicle by VIN

**Description:** Retrieve a vehicle by its VIN.

**Operation ID:** `getVehicleByVIN`

**Parameters:**

- `vin` (path, Required, string): VIN of the vehicle to retrieve

**Responses:**

- `200`: Vehicle found and returned.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`, `404`

---

#### PUT /v1/vehicles/vin/{vin}

**Summary:** Update vehicle by VIN

**Description:** Update an existing vehicle's details by its VIN.

**Operation ID:** `updateVehicleByVIN`

**Parameters:**

- `vin` (path, Required, string): VIN of the vehicle to update

**Request Schema (JSON Schema, abridged):**

```json
{
  "$ref": "#/components/schemas/VehicleEntity"
}
```

**Responses:**

- `200`: Vehicle updated successfully.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `200`, `404`

---

#### DELETE /v1/vehicles/vin/{vin}

**Summary:** Delete vehicle by VIN

**Description:** Delete a vehicle from the inventory by its VIN.

**Operation ID:** `deleteVehicleByVIN`

**Parameters:**

- `vin` (path, Required, string): VIN of the vehicle to delete

**Responses:**

- `204`: Vehicle deleted successfully.
- `404`: Vehicle not found.

**Provider Test Hints (ContractBehaviorIT):**

- Add tests for: `204`, `404`

---

#### POST /v1/vehicles/vin

**Summary:** Create vehicle by VIN

**Description:** Add a new vehicle to the inventory using its VIN.

**Operation ID:** `createVehicleByVIN`

**Request Schema (JSON Schema, abridged):**

```json
{
  "$ref": "#/components/schemas/VehicleEntity"
}
```

**Responses:**

- `200`: Vehicle created successfully.

**Notes:**

- VIN is supplied in the request body as `vin`.


---

## Entity-Specific Contracts

### AbstractCustomer

Customer object to be created

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `customerNumber` | string | No | Unique customer number |
| `email` | string | No | Email address of the customer |
| `firstName` | string | Yes | First name of the customer |
| `id` | integer (int64) | No | Unique identifier of the customer |
| `lastName` | string | Yes | Last name of the customer |
| `phoneNumber` | string | No | Phone number of the customer |
| `primaryAddress` | string | No | Primary address label or identifier for the customer |
| `vehicleVins` | array | No | List of vehicle VINs associated with the customer |


### AssignedRole

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `isPrimary` | boolean | No |  |
| `roleCode` | string | No |  |
| `roleLabel` | string | No |  |


### ContactWithRoles

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contactId` | string | No |  |
| `contactName` | string | No |  |
| `email` | string | No |  |
| `hasPrimaryEmail` | boolean | No |  |
| `invoiceDeliveryMethod` | string | No |  |
| `phone` | string | No |  |
| `roles` | array | No |  |


### CreateCommercialAccountRequest

Commercial account creation request

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `billingTermsId` | string | No |  |
| `displayName` | string | No |  |
| `email` | string | No |  |
| `externalIdentifiers` | object | No |  |
| `legalName` | string | No |  |
| `partyType` | string | No |  |
| `phone` | string | No |  |
| `taxId` | string | No |  |


### CreateCommercialAccountResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdAt` | string (date-time) | No |  |
| `duplicateCandidates` | array | No |  |
| `legalName` | string | No |  |
| `partyId` | string | No |  |
| `status` | string | No |  |


### CreateVehicleForPartyRequest

Vehicle creation request

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `description` | string | No |  |
| `licensePlate` | string | No |  |
| `licensePlateRegion` | string | No |  |
| `unitNumber` | string | No |  |
| `vinNumber` | string | No |  |


### CreateVehicleForPartyResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `createdAt` | string | No |  |
| `description` | string | No |  |
| `licensePlate` | string | No |  |
| `partyId` | string | No |  |
| `status` | string | No |  |
| `unitNumber` | string | No |  |
| `vehicleId` | string | No |  |
| `vinNumber` | string | No |  |


### DuplicateCandidate

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `legalName` | string | No |  |
| `matchReason` | string | No |  |
| `partyId` | string | No |  |


### GetCommunicationPreferencesResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `consentFlags` | object | No |  |
| `emailPreference` | string | No |  |
| `marketingPreference` | string | No |  |
| `partyId` | string | No |  |
| `phonePreference` | string | No |  |
| `preferencesNote` | string | No |  |
| `smsPreference` | string | No |  |
| `updateSource` | string | No |  |
| `updatedAt` | string | No |  |
| `version` | string | No |  |


### UpsertCommunicationPreferencesRequest

Communication preference upsert request

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | string | No |  |
| `emailPreference` | string | No |  |
| `smsPreference` | string | No |  |
| `phonePreference` | string | No |  |
| `marketingPreference` | string | No |  |
| `consentFlags` | object | No | Map of consent flags (`string -> boolean`) |
| `preferencesNote` | string | No |  |
| `updateSource` | string | No |  |


### UpsertCommunicationPreferencesResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `partyId` | string | No |  |
| `version` | string | No |  |
| `operationType` | string | No |  |
| `status` | string | No |  |
| `updatedAt` | string | No |  |


### GetContactsWithRolesResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contacts` | array | No |  |
| `partyId` | string | No |  |

Note: In OpenAPI, `contacts[]` items are `ContactWithRoles`.


### ContactWithRoles

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `contactId` | string | No |  |
| `contactName` | string | No |  |
| `email` | string | No |  |
| `phone` | string | No |  |
| `hasPrimaryEmail` | boolean | No |  |
| `roles` | array | No | Items are `AssignedRole` |
| `invoiceDeliveryMethod` | string | No |  |


### AssignedRole

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `roleCode` | string | No |  |
| `roleLabel` | string | No |  |
| `isPrimary` | boolean | No |  |


### UpdateContactRolesRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | string | No |  |
| `roles` | array | No | Items are `RoleAssignment` |


### RoleAssignment

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `roleCode` | string | No |  |
| `isPrimary` | boolean | No |  |


### UpdateContactRolesResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `partyId` | string | No |  |
| `contactId` | string | No |  |
| `version` | string | No |  |
| `status` | string | No |  |
| `updatedAt` | string | No |  |


### VehicleEntity

Vehicle record in the vehicle registry service.

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | integer (int64) | No | Unique identifier for the vehicle |
| `make` | string | Yes | Vehicle manufacturer (e.g., Toyota, Honda) |
| `model` | string | Yes | Vehicle model name |
| `year` | integer | Yes | Manufacturing year of the vehicle |
| `vin` | string | Yes | Vehicle Identification Number |

**Schema (JSON Schema, abridged):**

```json
{
  "type": "object",
  "properties": {
    "id": {
      "type": "integer",
      "format": "int64",
      "description": "Unique identifier for the vehicle"
    },
    "make": {
      "type": "string",
      "description": "Vehicle manufacturer (e.g., Toyota, Honda)"
    },
    "model": {
      "type": "string",
      "description": "Vehicle model name"
    },
    "year": {
      "type": "integer",
      "description": "Manufacturing year of the vehicle"
    },
    "vin": {
      "type": "string",
      "description": "Vehicle Identification Number"
    }
  },
  "required": [
    "make",
    "model",
    "year",
    "vin"
  ]
}
```


*Additional schemas omitted for brevity. See OpenAPI spec for complete list.*

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/crm/{customerId}/vehicles
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
GET /v1/crm/{id}
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

This guide establishes standardized contracts for the Customer Relationship Management (CRM) domain:

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
| 1.3 | 2026-02-03 | Add CAP:091 implementation links and document vehicle registry endpoints (pos-vehicle-inventory OpenAPI) |
| 1.2 | 2026-02-02 | Add CAP:090 implementation links and enrich contact/communication preference contract details |
| 1.1 | 2026-02-02 | Add CAP:089 implementation links (capability/stories/backend issues) |
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |

---

## References

- OpenAPI Specification (CRM): `pos-customer/openapi.json`
- OpenAPI Specification (Vehicle Registry / CAP:091): `durion-positivity-backend/pos-vehicle-inventory/openapi.json`
- Domain Agent Guide: `domains/crm/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/crm/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/crm/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

### Implementation Links (CAP:089)

- Capability manifest: `docs/capabilities/CAP-089/CAPABILITY_MANIFEST.yaml`
- Parent capability: https://github.com/louisburroughs/durion/issues/89
- Parent stories:
  - https://github.com/louisburroughs/durion/issues/95
  - https://github.com/louisburroughs/durion/issues/96
  - https://github.com/louisburroughs/durion/issues/97
  - https://github.com/louisburroughs/durion/issues/98
- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/112
  - https://github.com/louisburroughs/durion-positivity-backend/issues/111
  - https://github.com/louisburroughs/durion-positivity-backend/issues/110
  - https://github.com/louisburroughs/durion-positivity-backend/issues/109
- OpenAPI snapshot used for this update: `durion-positivity-backend/pos-customer/openapi.json`

---

### Implementation Links (CAP:090)

- Capability manifest: `docs/capabilities/CAP-090/CAPABILITY_MANIFEST.yaml`
- Parent capability: https://github.com/louisburroughs/durion/issues/90
- Parent stories:
  - https://github.com/louisburroughs/durion/issues/99
  - https://github.com/louisburroughs/durion/issues/100
  - https://github.com/louisburroughs/durion/issues/101
- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/108
  - https://github.com/louisburroughs/durion-positivity-backend/issues/107
  - https://github.com/louisburroughs/durion-positivity-backend/issues/106
- OpenAPI snapshot used for this update: `durion-positivity-backend/pos-customer/openapi.json`

---

### Implementation Links (CAP:091)

- Capability manifest: `docs/capabilities/CAP-091/CAPABILITY_MANIFEST.yaml`
- Parent capability: https://github.com/louisburroughs/durion/issues/91
- Parent stories:
  - https://github.com/louisburroughs/durion/issues/102
  - https://github.com/louisburroughs/durion/issues/103
  - https://github.com/louisburroughs/durion/issues/104
  - https://github.com/louisburroughs/durion/issues/105
  - https://github.com/louisburroughs/durion/issues/106
- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/105
  - https://github.com/louisburroughs/durion-positivity-backend/issues/104
  - https://github.com/louisburroughs/durion-positivity-backend/issues/103
  - https://github.com/louisburroughs/durion-positivity-backend/issues/102
  - https://github.com/louisburroughs/durion-positivity-backend/issues/101
- OpenAPI snapshot used for this update: `durion-positivity-backend/pos-vehicle-inventory/openapi.json`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
