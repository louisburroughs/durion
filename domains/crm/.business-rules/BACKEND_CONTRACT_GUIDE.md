# CRM Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-23

---

## Overview

This guide standardizes field naming conventions, data types, and payload structures for the CRM domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

---

## Table of Contents

1. [JSON Field Naming Conventions](#json-field-naming-conventions)
2. [Data Types & Formats](#data-types--formats)
3. [Enum Value Conventions](#enum-value-conventions)
4. [Identifier Naming](#identifier-naming)
5. [Timestamp Conventions](#timestamp-conventions)
6. [Boolean Field Naming](#boolean-field-naming)
7. [Collection & Pagination](#collection--pagination)
8. [Error Response Format](#error-response-format)
9. [Nested DTO Structures](#nested-dto-structures)
10. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "partyId": "P-12345",
  "legalName": "Acme Corporation",
  "billingTermsId": "BT-30",
  "createdAt": "2026-01-23T14:30:00Z"
}
```

### Rationale

- Aligns with JSON/JavaScript convention
- Matches Java property naming after Jackson deserialization
- Consistent with REST API best practices (RFC 7231)

---

## Data Types & Formats

### String Fields

Use `string` type for unstructured text:

- Names (legal, display, contact)
- Identifiers (partyId, vehicleId, etc.)
- Codes (roleCode, statusCode, errorCode)
- Free-form descriptions

```java
private String legalName;
private String description;
private String errorCode;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts, page numbers, total results
- IDs that are numeric (if not using UUID strings)

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs:

```java
private String partyId;        // UUID or encoded ID
private String vehicleId;      // UUID or encoded ID
private String contactId;      // UUID or encoded ID
private String billingTermsId; // Foreign key to Billing domain
```

### Instant/Timestamp Fields

Use `Instant` in Java; serialize to ISO 8601 in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;

private Instant modifiedAt;
```

JSON representation:

```json
{
  "createdAt": "2026-01-23T14:30:00Z",
  "modifiedAt": "2026-01-23T15:45:30Z"
}
```

### Boolean Fields

Use `Boolean` (object, nullable) or `boolean` (primitive, non-null):

```java
private Boolean isPrimary;           // nullable: true, false, null
private Boolean hasPrimaryEmail;    // nullable: indicates unknown state
private boolean isActive = true;    // default true, never null
```

JSON representation:

```json
{
  "isPrimary": true,
  "hasPrimaryEmail": false,
  "isActive": true
}
```

### Map/Dictionary Fields

Use `Map<String, Object>` or typed maps for flexible data:

```java
private Map<String, String> externalIdentifiers;  // system-specific IDs
private Map<String, Boolean> consentFlags;        // consent tracking
private Map<String, String> fieldErrors;          // validation errors

// Usage in JSON
{
  "externalIdentifiers": {
    "erp_customer_id": "CUST-001",
    "crm_legacy_id": "OLD-456"
  }
}
```

---

## Enum Value Conventions

### Standard Pattern: UPPER_CASE_WITH_UNDERSCORES

All enum values **MUST** use `UPPER_CASE_WITH_UNDERSCORES`:

```java
public enum PartyStatus {
    ACTIVE,
    PENDING,
    SUSPENDED,
    INACTIVE
}

public enum PreferenceType {
    OPT_IN,
    OPT_OUT,
    NOT_APPLICABLE
}

public enum RoleCode {
    BILLING,
    APPROVER,
    DRIVER
}
```

JSON representation:

```json
{
  "status": "ACTIVE",
  "emailPreference": "OPT_IN",
  "roleCode": "BILLING"
}
```

### Multi-word Enum Values

Separate words with underscores, no spaces:

```
✅ CORRECT:   WORK_IN_PROGRESS
❌ WRONG:     WORK IN PROGRESS
❌ WRONG:     WorkInProgress
```

---

## Identifier Naming

### Pattern: `{ResourceName}Id`

All identifier fields **MUST** follow the pattern `{resourceName}Id`:

| Resource | ID Field | Example |
|----------|----------|---------|
| Party | `partyId` | "P-uuid-12345" |
| Vehicle | `vehicleId` | "V-uuid-67890" |
| Contact | `contactId` | "C-uuid-11111" |
| Role Assignment | `roleAssignmentId` | "RA-uuid-22222" |
| Merge Audit | `mergeAuditId` | "MA-uuid-33333" |

### Foreign Key Identifiers

Foreign keys **MUST** use the full resource name:

```java
// ✅ CORRECT
private String billingTermsId;      // Foreign key to Billing domain
private String partyId;             // Parent party reference
private String contactId;           // Contact reference

// ❌ WRONG
private String billingTerms;        // Missing "Id" suffix
private String party;               // Ambiguous
private String contact;             // Ambiguous
```

---

## Timestamp Conventions

### Field Naming

Use descriptive past-tense names:

| Field | Meaning | Required |
|-------|---------|----------|
| `createdAt` | When record was created | Always |
| `modifiedAt` | When record was last modified | Always |
| `updatedAt` | Last update timestamp (alias for modifiedAt) | Optional |
| `mergedAt` | When merge was completed | If merged |
| `completedAt` | When operation completed | If applicable |

### Format: ISO 8601 (RFC 3339)

Always use UTC timezone (`Z` suffix):

```json
{
  "createdAt": "2026-01-23T14:30:00Z",
  "modifiedAt": "2026-01-23T15:45:30.123Z",
  "completedAt": "2026-01-23T16:00:00Z"
}
```

### Java Serialization

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, 
            pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", 
            timezone = "UTC")
private Instant createdAt;
```

### Precision

Include milliseconds when tracking high-precision timestamps (e.g., optimistic locking, audit trails):

```json
{
  "createdAt": "2026-01-23T14:30:00.000Z",
  "updatedAt": "2026-01-23T14:30:01.234Z"
}
```

---

## Boolean Field Naming

### Standard Prefixes

| Prefix | Meaning | Example | Nullable |
|--------|---------|---------|----------|
| `is*` | State check | `isActive`, `isPrimary`, `isApproved` | Boolean (nullable) |
| `has*` | Possession check | `hasPrimaryEmail`, `hasConsentRecord` | Boolean (nullable) |
| `can*` | Capability check | `canMerge`, `canDelete` | Boolean (nullable) |

### Examples

```json
{
  "isPrimary": true,
  "isActive": true,
  "hasPrimaryEmail": false,
  "hasConsentRecord": null,
  "canMerge": true,
  "canDelete": false
}
```

### Java Representation

```java
private Boolean isPrimary;              // nullable: true/false/null
private Boolean hasPrimaryEmail;       // nullable: indicates if checked
private Boolean isActive = true;       // default true
private boolean canMerge;              // primitive: never null, default false
```

### Avoid Negation

Never use negative boolean names:

```
✅ CORRECT:  isActive          (true = active)
❌ WRONG:    isInactive        (confusing double-negative)
```

---

## Collection & Pagination

### List/Array Fields

Use plural names for collections:

```json
{
  "results": [
    { "partyId": "P-1", "legalName": "Acme" },
    { "partyId": "P-2", "legalName": "Beta Corp" }
  ],
  "contacts": [
    { "contactId": "C-1", "name": "John" }
  ],
  "roles": [
    { "roleCode": "BILLING", "isPrimary": true }
  ]
}
```

### Pagination Fields

Standard pagination structure:

```java
private List<PartySummary> results;     // Result items
private Integer pageNumber;             // 1-indexed (default 1)
private Integer pageSize;               // Items per page (default 20)
private Integer totalCount;             // Total matching records
private Boolean hasMore;                // true if more pages available
```

JSON:

```json
{
  "results": [...],
  "pageNumber": 1,
  "pageSize": 20,
  "totalCount": 150,
  "hasMore": true
}
```

### Sorting Fields

```java
private String sortField;    // Field name (e.g., "legalName", "createdAt")
private String sortOrder;    // "ASC" or "DESC"
```

---

## Error Response Format

### Standard Error DTO

```java
@Data
@Builder
public class ErrorResponse {
    private String errorCode;              // Stable error code
    private String message;                // Human-readable message
    private Long timestamp;                // Unix timestamp
    private Map<String, String> fieldErrors; // Field-level errors
    private Map<String, Object> context;   // Additional context
}
```

### Error Code Naming Convention

Use `UPPER_CASE_WITH_UNDERSCORES`:

| Error Code | Meaning | HTTP Status |
| ------------ | --------- | ------------ |
| `DUPLICATE_PARTY` | Party with same attributes already exists | 409 Conflict |
| `INVALID_BILLING_TERMS` | Billing terms ID not found | 400 Bad Request |
| `MISSING_REQUIRED_FIELD` | Required field is null/empty | 400 Bad Request |
| `PERMISSION_DENIED` | User lacks required permission | 403 Forbidden |
| `OPTIMISTIC_LOCK_CONFLICT` | Version mismatch (concurrent update) | 409 Conflict |
| `DUPLICATE_VIN` | Vehicle with same VIN exists | 409 Conflict |
| `INVALID_ROLE_CODE` | Role code not recognized | 400 Bad Request |

### Example Error Response

```json
{
  "errorCode": "DUPLICATE_PARTY",
  "message": "A party with legal name 'Acme Corp' already exists",
  "timestamp": 1706020200000,
  "fieldErrors": {
    "legalName": "Duplicate legal name",
    "taxId": "Duplicate tax ID"
  },
  "context": {
    "duplicateCandidates": [
      {
        "partyId": "P-existing-123",
        "legalName": "Acme Corp",
        "matchReason": "Exact legal name match"
      }
    ]
  }
}
```

---

## Nested DTO Structures

### Organization

Nested DTOs should be defined as:

1. **Inner classes** if tightly coupled (used only by parent DTO)
2. **Separate files** if reusable across multiple endpoints

### Naming Pattern

Nested classes should describe their relationship or role:

```java
// ✅ CORRECT: Inner class (tightly coupled)
public class SearchPartiesResponse {
    @Data
    public static class PartySummary {
        private String partyId;
        private String legalName;
    }
    
    private List<PartySummary> results;
}

// ✅ CORRECT: Separate reusable class
public class ContactWithRoles {
    private String contactId;
    private String contactName;
    private List<AssignedRole> roles;
}

public class AssignedRole {
    private String roleCode;
    private String roleLabel;
    private Boolean isPrimary;
}
```

### Avoid Generic Wrapper Names

```
✅ CORRECT:   PartySummary, ContactWithRoles, DuplicateCandidate
❌ WRONG:     PartyDto, ContactDto, Result, Item
```

---

## Examples

### Complete Party Create Request

```json
{
  "legalName": "Acme Corporation",
  "displayName": "Acme",
  "taxId": "12-3456789",
  "partyType": "ORGANIZATION",
  "billingTermsId": "BT-NET30",
  "externalIdentifiers": {
    "erp_customer_id": "CUST-001",
    "crm_legacy_id": "OLD-12345"
  },
  "email": "contact@acme.com",
  "phone": "+1-555-0123"
}
```

### Complete Search Response

```json
{
  "results": [
    {
      "partyId": "P-uuid-001",
      "legalName": "Acme Corporation",
      "displayName": "Acme",
      "partyType": "ORGANIZATION",
      "status": "ACTIVE",
      "createdAt": "2026-01-15T10:00:00Z"
    },
    {
      "partyId": "P-uuid-002",
      "legalName": "Beta Inc.",
      "displayName": "Beta",
      "partyType": "ORGANIZATION",
      "status": "PENDING",
      "createdAt": "2026-01-20T14:30:00Z"
    }
  ],
  "pageNumber": 1,
  "pageSize": 20,
  "totalCount": 2,
  "hasMore": false
}
```

### Contact with Roles Response

```json
{
  "partyId": "P-uuid-001",
  "contacts": [
    {
      "contactId": "C-uuid-101",
      "contactName": "John Doe",
      "email": "john@acme.com",
      "phone": "+1-555-0100",
      "hasPrimaryEmail": true,
      "invoiceDeliveryMethod": "EMAIL",
      "roles": [
        {
          "roleCode": "BILLING",
          "roleLabel": "Billing Contact",
          "isPrimary": true
        },
        {
          "roleCode": "APPROVER",
          "roleLabel": "Approver",
          "isPrimary": false
        }
      ]
    }
  ]
}
```

### Error Response Example

```json
{
  "errorCode": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Concurrent update detected. Please reload and try again.",
  "timestamp": 1706020200000,
  "context": {
    "currentVersion": "v3",
    "submittedVersion": "v2"
  }
}
```

---

## Cross-Cutting Standards

### Null Handling

- **Nullable fields** (Optional in Java): Use `@JsonInclude(JsonInclude.Include.NON_NULL)` at DTO level to omit null values
- **Required fields**: Never null, always present in response

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatePartyRequest {
    private String legalName;              // Required, never null
    private String displayName;            // Optional, omit if null
    private Map<String, String> externalIdentifiers; // Optional
}
```

### Version Field (Optimistic Locking)

Field name is TBD per blocking questions; use consistently once decided:

```
Candidates: version, lastUpdatedStamp, eTag, or __v
Decision needed from backend architecture.

Suggested: Use "version" (simple, clear)
```

### Versioning the API

All endpoints should support API versioning:

```
✅ /v1/crm/parties              (v1)
✅ /v2/crm/parties              (v2, if incompatible change)
❌ /crm/parties                 (no version - breaks compatibility)
```

---

## Validation & Constraints

### Field Length Limits (TBD by backend)

Document in service/repository layer:

```java
// Example constraints to define
@Length(min = 1, max = 255)
private String legalName;

@Length(min = 50, max = 5000)
private String justification;

@Length(min = 17, max = 17)
private String vinNumber;
```

### Email & Phone Formatting

Use standard libraries (Apache Commons Email, E.164 for phone):

```java
@Email
private String email;

// Phone stored as E.164: +1-555-0123
private String phone;
```

---

## Related Documents

- [CRM Domain Notes](./DOMAIN_NOTES.md)
- [CRM Permissions Taxonomy](./PERMISSIONS_TAXONOMY.md)
- [CRM Domain Agent Guide](../../.github/agents/domains/crm-domain.agent.md)
- [API Catalog](../../.github/agents/api-catalog.yml)

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial version; field naming conventions, data types, error format |
