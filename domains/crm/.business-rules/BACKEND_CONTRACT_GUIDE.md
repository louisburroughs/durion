# Customer Relationship Management (CRM) Backend Contract Guide

**Version:** 2.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-02-05  
**OpenAPI Sources:**
- `durion-positivity-backend/pos-customer/openapi.json`  
- `durion-positivity-backend/pos-vehicle-inventory/openapi.json` (Vehicle Registry - CAP:091)  
**Status:** Accepted

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Customer Relationship Management (CRM) domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specifications of the pos-customer and pos-vehicle-inventory modules and follows the standards established across all Durion platform domains.

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
9. [API Endpoints Summary](#api-endpoints-summary)
10. [Customer API Endpoints](#customer-api-endpoints)
11. [CRM Accounts Endpoints](#crm-accounts-endpoints)
12. [Vehicle Registry Endpoints (CAP:091)](#vehicle-registry-endpoints-cap091)
13. [Vehicle Search Endpoints](#vehicle-search-endpoints)
14. [Vehicle Preferences Endpoints](#vehicle-preferences-endpoints)
15. [Entity-Specific Contracts](#entity-specific-contracts)
16. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "id": "abc-123",
  "accountId": "acct-456",
  "firstName": "John",
  "isActive": true,
  "createdAt": "2026-01-27T14:30:00Z"
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
- Years

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
private Integer year;
```

### Boolean Fields

Use `boolean` for true/false flags:

```java
private boolean isActive;
private boolean isPrimary;
private boolean manualOverride;
```

### UUID/ID Fields

Use `String` with `format: uuid` for all primary and foreign key IDs:

```java
private String id;
private String vehicleId;
private String accountId;
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

---

## Enum Value Conventions

### Standard Pattern: UPPER_SNAKE_CASE

All enum values **MUST** use `UPPER_SNAKE_CASE`:

```java
public enum AccountTier {
    STANDARD,
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    ENTERPRISE
}

public enum Status {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
```

### Enums in this Domain

- `AccountTier`: Customer segmentation tier (see [Account Tier Thresholds](#account-tier-thresholds))
- Status enums follow UPPER_SNAKE_CASE convention

---

## Identifier Naming

### Standard Pattern

- Primary keys: `id` or `{entity}Id` (e.g., `customerId`, `vehicleId`, `accountId`)
- Foreign keys: `{entity}Id` (e.g., `parentId`, `accountId`, `vehicleId`)
- Composite identifiers: use structured object, not concatenated string

### Examples

```json
{
  "id": "abc-123",
  "vehicleId": "vehicle-456",
  "accountId": "acct-789"
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
  "updatedAt": "2026-01-27T15:45:30.456Z",
  "tierAssignedAt": "2026-02-01T09:00:00.000Z"
}
```

### Common Timestamp Fields

- `createdAt`: When the entity was created
- `updatedAt`: When the entity was last updated
- `deletedAt`: When the entity was soft-deleted (if applicable)
- `tierAssignedAt`: When account tier was last assigned/recalculated
- `effectiveFrom`: Start date for effective dating
- `effectiveTo`: End date for effective dating

---

## Collection & Pagination

### Standard Pagination Request

```json
{
  "pageNumber": 0,
  "pageSize": 20,
  "sortBy": "createdAt",
  "sortOrder": "DESC"
}
```

### Standard Pagination Response

```json
{
  "results": [...],
  "pageNumber": 0,
  "pageSize": 20,
  "totalCount": 150,
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
  "message": "Request validation failed",
  "status": 400,
  "timestamp": "2026-01-27T14:30:00Z",
  "correlationId": "abc-123-def-456",
  "details": [
    {
      "field": "vin",
      "message": "VIN format is invalid"
    }
  ]
}
```

### Standard HTTP Status Codes

- `200 OK`: Successful GET, PUT, PATCH
- `201 Created`: Successful POST (creation)
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Validation error
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation (e.g., duplicate VIN)
- `422 Unprocessable Entity`: Semantic validation error
- `500 Internal Server Error`: Unexpected server error

---

## Correlation ID & Request Tracking

### X-Correlation-Id Header

All API requests **SHOULD** include an `X-Correlation-Id` header for distributed tracing:

```http
GET /v1/crm/accounts/parties/abc-123
X-Correlation-Id: abc-123-def-456
```

### Response Headers

All API responses **MUST** echo the correlation ID:

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456
```

### Error Responses

All error responses **MUST** include the correlation ID in the body and headers.

---

## API Endpoints Summary

### Module Distribution

**pos-customer module:** 8 Customer API endpoints + 8 CRM Accounts endpoints = 16 endpoints

**pos-vehicle-inventory module:** 20 endpoints distributed across:
- Vehicle Registry: 5 endpoints
- Vehicle Search: 2 endpoints
- Vehicle Preferences: 4 endpoints
- Vehicle API (Legacy): 9 endpoints

**Total: 36 REST API endpoints**

---

## CAP:092 Addendum (Draft) — Billing Rules + CRM Snapshot

This addendum documents CAP:092 endpoints that are not yet present in OpenAPI (pending `pos-invoice` and related gateway routing updates).

### GET http://localhost:8080/v1/crm/snapshot

**Summary:** Return a lightweight CRM snapshot for use by WorkExec and other UIs.

**Source:** CAP:092 backend story defines `GET /v1/crm-snapshot`; API Gateway SHOULD expose it as `GET http://localhost:8080/v1/crm/snapshot`.

**Query params:**
- `partyId` (string, optional)
- `vehicleId` (string, optional)

**Authorization:**
- Scope/permission: `crm.snapshot.read`

**Behavior (deterministic resolution):**
- If `vehicleId` resolves to an active party, use that party.
- If `vehicleId` has no active party, return `404` with code `VEHICLE_HAS_NO_ACTIVE_PARTY`.
- If both `partyId` and `vehicleId` provided, `vehicleId` resolution wins.

### GET http://localhost:8080/v1/crm/accounts/parties/{partyId}/billingRules

**Summary:** CRM facade for billing rules.

**Notes:**
- Implemented in `pos-customer` as a facade; rules are owned by `pos-invoice`.
- Consumers SHOULD treat the response as authoritative.

---

## Customer API Endpoints

### GET http://localhost:8080/v1/crm

**Summary:** Get all customers

**Description:** Retrieve a paginated list of all customers

**Operation ID:** `getCustomers`

**Parameters:** None (pagination/filtering may be added)

**Responses:**

- `200`: Customers retrieved successfully
- `401`: Unauthorized
- `403`: Forbidden - insufficient permissions

**Contract (Response 200):** Array of `AbstractCustomer`

---

### POST http://localhost:8080/v1/crm

**Summary:** Create a new customer

**Description:** Add a new customer to the system

**Operation ID:** `createCustomer`

**Request Body:** `AbstractCustomer`

**Responses:**

- `201`: Customer created successfully
- `400`: Invalid request
- `401`: Unauthorized
- `403`: Forbidden - insufficient permissions

**Contract (Response 201):** `AbstractCustomer`

---

### GET http://localhost:8080/v1/crm/{id}

**Summary:** Get customer by ID

**Description:** Retrieve a customer by their unique ID

**Operation ID:** `getCustomerById`

**Parameters:**

- `id` (path, required, string): Customer ID

**Responses:**

- `200`: Customer found
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Customer not found

**Contract (Response 200):** `AbstractCustomer`

---

### PUT http://localhost:8080/v1/crm/{id}

**Summary:** Update an existing customer

**Description:** Update the details of an existing customer

**Operation ID:** `updateCustomer`

**Parameters:**

- `id` (path, required, string): Customer ID

**Request Body:** `AbstractCustomer`

**Responses:**

- `200`: Customer updated successfully
- `400`: Invalid request
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Customer not found

**Contract (Response 200):** `AbstractCustomer`

---

### DELETE http://localhost:8080/v1/crm/{id}

**Summary:** Delete a customer

**Description:** Delete a customer by their unique ID (may be soft-delete)

**Operation ID:** `deleteCustomer`

**Parameters:**

- `id` (path, required, string): Customer ID

**Responses:**

- `204`: Customer deleted successfully
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Customer not found

---

## CRM Accounts Endpoints

### POST http://localhost:8080/v1/crm/accounts/parties

**Summary:** Create commercial account

**Description:** Create a new commercial account (party/customer) with detailed information

**Operation ID:** `createCommercialAccount`

**Request Body:** `CreateCommercialAccountRequest`

**Responses:**

- `201`: Account created successfully
- `400`: Invalid request
- `401`: Unauthorized
- `403`: Forbidden - insufficient permissions

**Contract (Response 201):** `CreateCommercialAccountResponse`

---

### POST http://localhost:8080/v1/crm/accounts/parties/search

**Summary:** Search parties

**Description:** Search for parties/accounts using various criteria

**Operation ID:** `searchParties`

**Request Body:** `SearchPartiesRequest`

**Responses:**

- `200`: Search completed
- `400`: Invalid search query
- `401`: Unauthorized
- `403`: Forbidden

**Contract (Response 200):** `SearchPartiesResponse`

---

### GET http://localhost:8080/v1/crm/accounts/parties/{partyId}

**Summary:** Get party details

**Description:** Retrieve full details for a specific party

**Operation ID:** `getParty`

**Parameters:**

- `partyId` (path, required, string): Party ID

**Responses:**

- `200`: Party found
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 200):** `GetPartyResponse`

---

### GET http://localhost:8080/v1/crm/accounts/parties/{partyId}/contacts

**Summary:** Get contacts with roles

**Description:** Retrieve all contacts for a party with their assigned roles

**Operation ID:** `getContactsWithRoles`

**Parameters:**

- `partyId` (path, required, string): Party ID

**Responses:**

- `200`: Contacts retrieved successfully
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 200):** `GetContactsWithRolesResponse`

---

### PUT http://localhost:8080/v1/crm/accounts/parties/{partyId}/contacts/{contactId}/roles

**Summary:** Update contact roles

**Description:** Assign or update role assignments for a specific contact

**Operation ID:** `updateContactRoles`

**Parameters:**

- `partyId` (path, required, string): Party ID
- `contactId` (path, required, string): Contact ID

**Request Body:** `UpdateContactRolesRequest`

**Responses:**

- `200`: Roles updated successfully
- `400`: Invalid role assignment
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party or contact not found

**Contract (Response 200):** `UpdateContactRolesResponse`

---

### GET http://localhost:8080/v1/crm/accounts/parties/{partyId}/communicationPreferences

**Summary:** Get communication preferences

**Description:** Retrieve communication preferences and consent flags for a party

**Operation ID:** `getCommunicationPreferences`

**Parameters:**

- `partyId` (path, required, string): Party ID

**Responses:**

- `200`: Preferences retrieved
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 200):** `GetCommunicationPreferencesResponse`

---

### POST http://localhost:8080/v1/crm/accounts/parties/{partyId}/communicationPreferences

**Summary:** Create or update communication preferences

**Description:** Set or update communication preferences and consent flags for a party

**Operation ID:** `upsertCommunicationPreferences`

**Parameters:**

- `partyId` (path, required, string): Party ID

**Request Body:** `UpsertCommunicationPreferencesRequest`

**Responses:**

- `200`: Preferences updated
- `201`: Preferences created
- `400`: Invalid request
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 200/201):** `UpsertCommunicationPreferencesResponse`

---

### POST http://localhost:8080/v1/crm/accounts/parties/{partyId}/vehicles

**Summary:** Create vehicle for party

**Description:** Associate a new vehicle with a party/customer

**Operation ID:** `createVehicleForParty`

**Parameters:**

- `partyId` (path, required, string): Party ID

**Request Body:** `CreateVehicleForPartyRequest`

**Responses:**

- `201`: Vehicle created successfully
- `400`: Invalid vehicle data
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 201):** `CreateVehicleForPartyResponse`

---

### POST http://localhost:8080/v1/crm/accounts/parties/{partyId}/merge

**Summary:** Merge parties

**Description:** Merge multiple parties into a single party record

**Operation ID:** `mergeParties`

**Parameters:**

- `partyId` (path, required, string): Target party ID

**Request Body:** `MergePartiesRequest`

**Responses:**

- `200`: Parties merged successfully
- `400`: Invalid merge request
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Party not found

**Contract (Response 200):** `MergePartiesResponse`

---

### GET http://localhost:8080/v1/crm/accounts/{accountId}/tier

**Summary:** Get account tier

**Description:** Retrieve the current tier level for a specific account (Story #102)

**Operation ID:** `getAccountTier`

**Parameters:**

- `accountId` (path, required, string): Account ID

**Responses:**

- `200`: Tier retrieved
- `401`: Unauthorized
- `403`: Forbidden
- `404`: Account not found

**Contract (Response 200):** `GetAccountTierResponse`

**Behavior Notes:**
- Returns current tier, assignment date, assigned by user, and manual override flag
- See [Account Tier Thresholds](#account-tier-thresholds) for business rules

---

### POST http://localhost:8080/v1/crm/accounts/tierResolve

**Summary:** Resolve account tier

**Description:** Calculate or recalculate the account tier based on business rules (Story #102)

**Operation ID:** `resolveAccountTier`

**Request Body:** `ResolveAccountTierRequest`

**Responses:**

- `200`: Tier calculation completed
- `400`: Invalid request
- `401`: Unauthorized
- `403`: Forbidden

**Contract (Response 200):** `ResolveAccountTierResponse`

**Behavior Notes:**
- Accepts revenue, contract count, account age, and override flags
- Returns current and recommended tiers with scoring details
- See [Account Tier Thresholds](#account-tier-thresholds) for calculation rules

---

## Vehicle Registry Endpoints (CAP:091)

All vehicle registry endpoints are in the `pos-vehicle-inventory` module under the **Vehicle Registry** tag.

### POST http://localhost:8080/v1/vehicles

**Summary:** Create a new vehicle

**Description:** Creates a new vehicle record with VIN validation and normalization. VIN must be globally unique across all active vehicles. (Story #105)

**Operation ID:** `createVehicle`

**Request Body:** `CreateVehicleRequest`

**Responses:**

- `201`: Vehicle created successfully
- `400`: Invalid request - VIN format invalid, missing required fields, or duplicate VIN
- `409`: Conflict - VIN already exists for another active vehicle

**Contract (Response 201):** `VehicleResponse`

**Required Fields in Request:**
- `accountId` (UUID)
- `vin` (string, 17 characters)
- `unitNumber` (string)
- `description` (string)

**Optional Fields:**
- `licensePlate`
- `licensePlateJurisdiction`
- `year`
- `make`
- `model`
- `trim`

---

### GET http://localhost:8080/v1/vehicles/{vehicleId}

**Summary:** Get vehicle by ID

**Description:** Retrieves a vehicle by its unique UUID. (Story #105)

**Operation ID:** `getVehicle`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle UUID

**Responses:**

- `200`: Vehicle found
- `404`: Vehicle not found

**Contract (Response 200):** `VehicleResponse`

---

### PUT http://localhost:8080/v1/vehicles/{vehicleId}

**Summary:** Update vehicle

**Description:** Updates an existing vehicle's details. VIN cannot be changed. (Story #105)

**Operation ID:** `updateVehicle`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle UUID

**Request Body:** `CreateVehicleRequest`

**Responses:**

- `200`: Vehicle updated successfully
- `400`: Invalid request
- `404`: Vehicle not found

**Contract (Response 200):** `VehicleResponse`

---

### DELETE http://localhost:8080/v1/vehicles/{vehicleId}

**Summary:** Delete vehicle

**Description:** Soft-deletes (deactivates) a vehicle. Sets `isActive` to false. (Story #105)

**Operation ID:** `deleteVehicle`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle UUID

**Responses:**

- `204`: Vehicle deleted successfully
- `404`: Vehicle not found

---

### GET http://localhost:8080/v1/vehicles/vin/{vin}

**Summary:** Get vehicle by VIN

**Description:** Retrieves a vehicle by its VIN (normalized lookup). (Story #105)

**Operation ID:** `getVehicleByVin`

**Parameters:**

- `vin` (path, required, string): Vehicle VIN (17 characters)

**Responses:**

- `200`: Vehicle found
- `404`: Vehicle not found

**Contract (Response 200):** `VehicleResponse`

---

## Vehicle Search Endpoints

### POST http://localhost:8080/v1/vehicles/search

**Summary:** Search vehicles

**Description:** Search for vehicles by VIN, license plate, unit number, or description. Results are ranked by relevance: exact match > prefix match > contains match. (Story #103)

**Operation ID:** `search`

**Request Body:** `SearchVehiclesRequest`

**Responses:**

- `200`: Search results returned
- `400`: Invalid search query

**Contract (Response 200):** `SearchVehiclesResponse`

**Search Behavior:**
- Exact matches ranked highest
- Prefix matches ranked second
- Contains matches ranked lowest (if `enableContainsMatching` is true)
- Searches against: VIN, license plate, unit number, description
- Default limit: 25 results, max 50

---

### GET http://localhost:8080/v1/vehicles/search

**Summary:** Search vehicles (query parameter)

**Description:** Alternative search endpoint using query parameters. Useful for browser-based queries.

**Operation ID:** `searchByQuery`

**Parameters:**

- `q` (query, required, string): Search query
- `limit` (query, optional, integer): Result limit (default 25, max 50)
- `enableContains` (query, optional, boolean): Enable contains matching (default false)

**Responses:**

- `200`: Search results returned
- `400`: Invalid search query

**Contract (Response 200):** `SearchVehiclesResponse`

---

## Vehicle Preferences Endpoints

### GET http://localhost:8080/v1/vehicles/{vehicleId}/preferences

**Summary:** Get vehicle care preferences

**Description:** Retrieves the care preferences for a vehicle. Returns 404 if no preferences exist.

**Operation ID:** `getPreferences`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle ID

**Responses:**

- `200`: Preferences found and returned
- `404`: No preferences found for this vehicle

**Contract (Response 200):** `VehicleCarePreference`

---

### PUT http://localhost:8080/v1/vehicles/{vehicleId}/preferences

**Summary:** Create or update vehicle care preferences

**Description:** Upserts preferences for a vehicle. If preferences exist, replaces them entirely. Use PATCH for partial updates.

**Operation ID:** `upsertPreferences`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle ID

**Request Body:** `PreferencesUpsertDto`

**Responses:**

- `200`: Preferences updated successfully
- `201`: Preferences created successfully
- `400`: Invalid preference data
- `404`: Vehicle not found

**Contract (Response 200/201):** `VehicleCarePreference`

**Request Fields:**
- `preferences` (object, required): Complete preference map
- `serviceNotes` (string, optional)
- `createdByUserId` (string, UUID, optional)
- `updatedByUserId` (string, UUID, optional)

---

### PATCH http://localhost:8080/v1/vehicles/{vehicleId}/preferences

**Summary:** Partially update vehicle care preferences

**Description:** Merges provided preference fields into existing preferences without replacing the entire map.

**Operation ID:** `mergePreferences`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle ID

**Request Body:** `PreferencesMergeDto`

**Responses:**

- `200`: Preferences merged successfully
- `404`: No existing preferences to merge into

**Contract (Response 200):** `VehicleCarePreference`

**Request Fields:**
- `partialPreferences` (object, required): Partial preference map to merge
- `updatedByUserId` (string, UUID, optional)

---

### DELETE http://localhost:8080/v1/vehicles/{vehicleId}/preferences

**Summary:** Delete vehicle care preferences

**Description:** Removes all preferences for a vehicle.

**Operation ID:** `deletePreferences`

**Parameters:**

- `vehicleId` (path, required, string, format: uuid): Vehicle ID

**Responses:**

- `204`: Preferences deleted successfully
- `404`: No preferences found to delete

---

## Entity-Specific Contracts

### CreateVehicleRequest

**Module:** pos-vehicle-inventory

**Description:** Request DTO for creating a vehicle (CAP:091 Story #105)

**Required Fields:**
- `accountId` (string, UUID): Account/party ID that owns this vehicle
- `vin` (string): Vehicle Identification Number (17 characters)
- `unitNumber` (string): Internal unit number for the vehicle
- `description` (string): Description of the vehicle

**Optional Fields:**
- `licensePlate` (string)
- `licensePlateJurisdiction` (string)
- `year` (integer)
- `make` (string)
- `model` (string)
- `trim` (string)

**Example:**
```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "vin": "1HGCM82633A004352",
  "unitNumber": "UNIT-001",
  "description": "2019 Honda Accord Sedan",
  "licensePlate": "ABC-1234",
  "licensePlateJurisdiction": "CA",
  "year": 2019,
  "make": "Honda",
  "model": "Accord",
  "trim": "EX-L"
}
```

---

### VehicleResponse

**Module:** pos-vehicle-inventory

**Description:** Response DTO for vehicle operations (CAP:091)

**Fields:**
- `vehicleId` (string, UUID): Unique vehicle identifier
- `accountId` (string, UUID): Account/party ID that owns this vehicle
- `vin` (string): Vehicle Identification Number (original format)
- `vinNormalized` (string): Normalized VIN (uppercase, no spaces/dashes)
- `unitNumber` (string): Internal unit number
- `description` (string): Vehicle description
- `licensePlate` (string): License plate number
- `licensePlateJurisdiction` (string): License plate jurisdiction
- `year` (integer): Manufacturing year
- `make` (string): Vehicle manufacturer
- `model` (string): Vehicle model
- `trim` (string): Vehicle trim level
- `isActive` (boolean): Whether vehicle is active (not soft-deleted)
- `createdAt` (string, date-time): Creation timestamp (ISO 8601 UTC)
- `updatedAt` (string, date-time): Last update timestamp (ISO 8601 UTC)
- `version` (integer): Optimistic locking version

**Example:**
```json
{
  "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "vin": "1HGCM82633A004352",
  "vinNormalized": "1HGCM82633A004352",
  "unitNumber": "UNIT-001",
  "description": "2019 Honda Accord Sedan",
  "licensePlate": "ABC-1234",
  "licensePlateJurisdiction": "CA",
  "year": 2019,
  "make": "Honda",
  "model": "Accord",
  "trim": "EX-L",
  "isActive": true,
  "createdAt": "2026-02-01T10:00:00Z",
  "updatedAt": "2026-02-04T14:30:00Z",
  "version": 1
}
```

---

### SearchVehiclesRequest

**Module:** pos-vehicle-inventory

**Description:** Request DTO for searching vehicles (CAP:091 Story #103)

**Required Fields:**
- `query` (string): Search query (VIN, license plate, unit number, or description)

**Optional Fields:**
- `limit` (integer, default 25): Result limit (max 50)
- `cursor` (string, nullable): Pagination cursor (reserved for future use)
- `enableContainsMatching` (boolean, default false): Enable contains matching (strict matching by default)

**Example:**
```json
{
  "query": "1HGCM82633A004352",
  "limit": 25,
  "enableContainsMatching": false
}
```

---

### SearchVehiclesResponse

**Module:** pos-vehicle-inventory

**Description:** Search results response (CAP:091 Story #103)

**Fields:**
- `results` (array of `VehicleSummary`): Array of matching vehicle summaries
- `totalCount` (integer): Total number of results found
- `hasMore` (boolean): Whether there are more results available
- `query` (string): Original query string

**Example:**
```json
{
  "results": [
    {
      "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
      "vin": "1HGCM82633A004352",
      "unitNumber": "UNIT-001",
      "licensePlate": "ABC-1234",
      "description": "2019 Honda Accord Sedan",
      "year": 2019,
      "make": "Honda",
      "model": "Accord"
    }
  ],
  "totalCount": 1,
  "hasMore": false,
  "query": "1HGCM82633A004352"
}
```

---

### VehicleCarePreference

**Module:** pos-vehicle-inventory

**Description:** Vehicle care preference entity with flexible JSONB preferences

**Fields:**
- `id` (string, UUID): Preference record ID
- `vehicleId` (string, UUID): Associated vehicle ID
- `preferences` (object): Flexible key-value map of preferences (stored as JSONB)
- `serviceNotes` (string): General service notes for the vehicle
- `createdByUserId` (string, UUID): User who created this record
- `updatedByUserId` (string, UUID): User who last updated this record
- `createdAt` (string, date-time): Creation timestamp (ISO 8601 UTC)
- `updatedAt` (string, date-time): Last update timestamp (ISO 8601 UTC)

**Example:**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
  "preferences": {
    "maintenanceSchedule": "every_6_months",
    "preferredServiceCenter": "downtown_honda",
    "requiresSpecialHandling": true
  },
  "serviceNotes": "Vehicle requires synthetic oil only",
  "createdByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "updatedByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "createdAt": "2026-02-01T11:00:00Z",
  "updatedAt": "2026-02-04T14:30:00Z"
}
```

---

### GetAccountTierResponse

**Module:** pos-customer

**Description:** Response DTO for account tier retrieval (Story #102)

**Fields:**
- `accountId` (string): Account ID
- `tier` (string, enum): Current tier (STANDARD, BRONZE, SILVER, GOLD, PLATINUM, ENTERPRISE)
- `tierDisplayName` (string): Human-readable tier name
- `tierAssignedAt` (string, date-time): When tier was last assigned
- `tierAssignedBy` (string): User or system that assigned the tier
- `notes` (string): Optional notes about tier assignment
- `manualOverride` (boolean): Whether tier was manually overridden

**Example:**
```json
{
  "accountId": "acct-789",
  "tier": "GOLD",
  "tierDisplayName": "Gold Tier",
  "tierAssignedAt": "2026-02-01T09:00:00Z",
  "tierAssignedBy": "system",
  "notes": "Automatic tier assignment based on revenue threshold",
  "manualOverride": false
}
```

---

### ResolveAccountTierRequest

**Module:** pos-customer

**Description:** Request DTO for account tier calculation (Story #102)

**Required Fields:**
- `accountId` (string): Account ID to resolve tier for

**Optional Fields:**
- `annualRevenue` (decimal): Annual revenue in dollars
- `activeContractCount` (integer): Number of active contracts
- `accountAgeMonths` (integer): Age of account in months
- `applyTier` (boolean, default true): Whether to apply the resolved tier
- `forceRecalculation` (boolean, default false): Force recalculation even if manual override exists

**Example:**
```json
{
  "accountId": "acct-789",
  "annualRevenue": 500000.00,
  "activeContractCount": 5,
  "accountAgeMonths": 24,
  "applyTier": true,
  "forceRecalculation": false
}
```

---

### ResolveAccountTierResponse

**Module:** pos-customer

**Description:** Response DTO for account tier calculation result (Story #102)

**Fields:**
- `currentTier` (string, enum): Current tier before calculation
- `recommendedTier` (string, enum): Recommended tier based on business rules
- `tierApplied` (boolean): Whether the recommended tier was applied
- `manualOverrideActive` (boolean): Whether a manual override is blocking automatic tier assignment
- `resolutionReason` (string): Explanation of tier calculation
- `tierScore` (number): Calculated score used for tier determination

**Example:**
```json
{
  "currentTier": "SILVER",
  "recommendedTier": "GOLD",
  "tierApplied": true,
  "manualOverrideActive": false,
  "resolutionReason": "Annual revenue exceeded $250,000 threshold",
  "tierScore": 275000.00
}
```

---

## Account Tier Thresholds

The following thresholds determine account tier (Story #102):

| Tier | Minimum Annual Revenue | Min. Contracts | Min. Account Age |
|------|------------------------|----------------|------------------|
| STANDARD | $0 | 0 | 0 months |
| BRONZE | $50,000 | N/A | N/A |
| SILVER | $100,000 | N/A | N/A |
| GOLD | $250,000 | N/A | N/A |
| PLATINUM | $500,000 | N/A | N/A |
| ENTERPRISE | $1,000,000 | N/A | N/A |

**Calculation Rules:**
- Tier is determined primarily by annual revenue
- Contract count and account age may influence qualification
- Manual override flag can lock tier at a specific level
- Recalculation respects manual overrides unless `forceRecalculation=true`

---

## Examples

### Example 1: Create a Vehicle

**Request:**
```http
POST /v1/vehicles HTTP/1.1
Host: api-gateway.local/api/vehicle-inventory
Content-Type: application/json
X-Correlation-Id: req-001

{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "vin": "1HGCM82633A004352",
  "unitNumber": "UNIT-001",
  "description": "2019 Honda Accord Sedan",
  "licensePlate": "ABC-1234",
  "licensePlateJurisdiction": "CA",
  "year": 2019,
  "make": "Honda",
  "model": "Accord",
  "trim": "EX-L"
}
```

**Success Response (201):**
```json
{
  "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "vin": "1HGCM82633A004352",
  "vinNormalized": "1HGCM82633A004352",
  "unitNumber": "UNIT-001",
  "description": "2019 Honda Accord Sedan",
  "licensePlate": "ABC-1234",
  "licensePlateJurisdiction": "CA",
  "year": 2019,
  "make": "Honda",
  "model": "Accord",
  "trim": "EX-L",
  "isActive": true,
  "createdAt": "2026-02-04T14:30:00Z",
  "updatedAt": "2026-02-04T14:30:00Z",
  "version": 1
}
```

**Error Response (409 - Duplicate VIN):**
```json
{
  "code": "CONFLICT",
  "message": "VIN already exists for another active vehicle",
  "status": 409,
  "timestamp": "2026-02-04T14:30:00Z",
  "correlationId": "req-001"
}
```

---

### Example 2: Search Vehicles

**Request:**
```http
POST /v1/vehicles/search HTTP/1.1
Host: api-gateway.local/api/vehicle-inventory
Content-Type: application/json
X-Correlation-Id: req-002

{
  "query": "Honda",
  "limit": 10,
  "enableContainsMatching": true
}
```

**Response (200):**
```json
{
  "results": [
    {
      "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
      "vin": "1HGCM82633A004352",
      "unitNumber": "UNIT-001",
      "licensePlate": "ABC-1234",
      "description": "2019 Honda Accord Sedan",
      "year": 2019,
      "make": "Honda",
      "model": "Accord"
    }
  ],
  "totalCount": 1,
  "hasMore": false,
  "query": "Honda"
}
```

---

### Example 3: Resolve Account Tier

**Request:**
```http
POST /v1/crm/accounts/tierResolve HTTP/1.1
Host: api-gateway.local/api/customer
Content-Type: application/json
X-Correlation-Id: req-003

{
  "accountId": "acct-789",
  "annualRevenue": 300000.00,
  "activeContractCount": 3,
  "accountAgeMonths": 18,
  "applyTier": true,
  "forceRecalculation": false
}
```

**Response (200):**
```json
{
  "currentTier": "SILVER",
  "recommendedTier": "GOLD",
  "tierApplied": true,
  "manualOverrideActive": false,
  "resolutionReason": "Annual revenue exceeded $250,000 threshold with 3 active contracts",
  "tierScore": 300000.00
}
```

---

### Example 4: Vehicle Care Preferences

**Request (PUT - Upsert):**
```http
PUT /v1/vehicles/660e8400-e29b-41d4-a716-446655440001/preferences HTTP/1.1
Host: api-gateway.local/api/vehicle-inventory
Content-Type: application/json
X-Correlation-Id: req-004

{
  "preferences": {
    "maintenanceSchedule": "every_6_months",
    "preferredServiceCenter": "downtown_honda",
    "requiresSpecialHandling": true,
    "insuranceProvider": "state_farm"
  },
  "serviceNotes": "Vehicle requires synthetic oil only",
  "updatedByUserId": "880e8400-e29b-41d4-a716-446655440003"
}
```

**Response (201):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
  "preferences": {
    "maintenanceSchedule": "every_6_months",
    "preferredServiceCenter": "downtown_honda",
    "requiresSpecialHandling": true,
    "insuranceProvider": "state_farm"
  },
  "serviceNotes": "Vehicle requires synthetic oil only",
  "createdByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "updatedByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "createdAt": "2026-02-04T14:35:00Z",
  "updatedAt": "2026-02-04T14:35:00Z"
}
```

**Request (PATCH - Merge):**
```http
PATCH /v1/vehicles/660e8400-e29b-41d4-a716-446655440001/preferences HTTP/1.1
Host: api-gateway.local/api/vehicle-inventory
Content-Type: application/json
X-Correlation-Id: req-005

{
  "partialPreferences": {
    "maintenanceSchedule": "every_3_months"
  },
  "updatedByUserId": "880e8400-e29b-41d4-a716-446655440003"
}
```

**Response (200):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "vehicleId": "660e8400-e29b-41d4-a716-446655440001",
  "preferences": {
    "maintenanceSchedule": "every_3_months",
    "preferredServiceCenter": "downtown_honda",
    "requiresSpecialHandling": true,
    "insuranceProvider": "state_farm"
  },
  "serviceNotes": "Vehicle requires synthetic oil only",
  "createdByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "updatedByUserId": "880e8400-e29b-41d4-a716-446655440003",
  "createdAt": "2026-02-04T14:35:00Z",
  "updatedAt": "2026-02-04T14:40:00Z"
}
```

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 2.0 | 2026-02-04 | Updated based on current OpenAPI specs; removed stub endpoints; added complete Vehicle Registry, Search, and Preferences endpoints from CAP:091; added Account Tier implementation details |
| 1.3 | 2026-02-03 | Previous version with stub endpoints |
