# Accounting Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-24

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Accounting domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide follows the precedent established by the CRM domain and extends it with accounting-specific requirements: monetary precision, effective dating, versioning, balance validation, and audit trails.

---

## Table of Contents

1. [JSON Field Naming Conventions](#json-field-naming-conventions)
2. [Data Types & Formats](#data-types--formats)
3. [Monetary Amounts & Precision](#monetary-amounts--precision)
4. [Enum Value Conventions](#enum-value-conventions)
5. [Identifier Naming](#identifier-naming)
6. [Timestamp Conventions](#timestamp-conventions)
7. [Effective Dating & Versioning](#effective-dating--versioning)
8. [Collection & Pagination](#collection--pagination)
9. [Error Response Format](#error-response-format)
10. [Optimistic Locking](#optimistic-locking)
11. [Audit Trail Requirements](#audit-trail-requirements)
12. [Entity-Specific Contracts](#entity-specific-contracts)
13. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{
  "glAccountId": "GL-12345",
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  "accountType": "ASSET",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "createdAt": "2026-01-24T14:30:00Z"
}
```

### Rationale

- Aligns with JSON/JavaScript convention
- Matches Java property naming after Jackson deserialization
- Consistent with REST API best practices (RFC 7231)
- Consistent with CRM domain standards

---

## Data Types & Formats

### String Fields

Use `string` type for:

- Names and descriptions (accountName, categoryDescription)
- Codes (accountCode, eventType, errorCode)
- Identifiers (glAccountId, journalEntryId)
- Free-form text (justification, notes, rulesDefinition)

```java
private String accountCode;
private String accountName;
private String eventType;
private String justification;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts (page numbers, total results, line item counts)
- Version numbers (integer sequences)
- Sequence numbers

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
private Integer versionNumber;
private Integer lineNumber;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs:

```java
private String glAccountId;          // UUID or encoded ID
private String journalEntryId;       // UUID or encoded ID
private String postingRuleSetId;     // UUID or encoded ID
private String vendorBillId;         // UUID or encoded ID
private String mappingKeyId;         // UUID or encoded ID
```

### Instant/Timestamp Fields

Use `Instant` in Java; serialize to ISO 8601 UTC in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
private Instant modifiedAt;
private Instant postedAt;
private Instant approvedAt;
```

JSON representation:

```json
{
  "createdAt": "2026-01-24T14:30:00Z",
  "modifiedAt": "2026-01-24T15:45:30Z",
  "postedAt": "2026-01-24T16:00:00Z"
}
```

### LocalDate Fields (Effective Dating)

Use `LocalDate` for date-only fields (no time component):

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
private LocalDate effectiveFrom;
private LocalDate effectiveTo;
private LocalDate transactionDate;
private LocalDate activationDate;
```

JSON representation:

```json
{
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31",
  "transactionDate": "2026-01-24"
}
```

### Boolean Fields

Use `Boolean` (object, nullable) or `boolean` (primitive, non-null):

```java
private Boolean isActive;            // nullable: true, false, null
private Boolean isBalanced;          // nullable: indicates unknown state
private boolean isPosted = false;    // default false, never null
private boolean isImmutable = false;
```

JSON representation:

```json
{
  "isActive": true,
  "isBalanced": true,
  "isPosted": false,
  "isImmutable": false
}
```

### Map/Dictionary Fields

Use `Map<String, Object>` or typed maps for flexible data:

```java
private Map<String, String> dimensions;           // businessUnitId, locationId, etc.
private Map<String, String> fieldErrors;          // validation errors
private Map<String, BigDecimal> accountBalances;  // per-account totals
private Map<String, Object> metadata;             // extensibility

// Usage in JSON
{
  "dimensions": {
    "businessUnitId": "BU-001",
    "locationId": "LOC-123",
    "departmentId": "DEPT-456",
    "costCenterId": "CC-789"
  }
}
```

---

## Monetary Amounts & Precision

### BigDecimal for All Amounts

**CRITICAL:** Always use `BigDecimal` for monetary amounts. Never use `float`, `double`, or `Float`.

```java
private BigDecimal debitAmount;   // Exact precision
private BigDecimal creditAmount;  // Exact precision
private BigDecimal balanceAmount; // Exact precision
private BigDecimal totalAmount;   // Exact precision
```

### Precision Rules

- **Internal Precision:** Use `DECIMAL(19, 4)` in database (19 total digits, 4 decimal places)
- **Display Precision:** Round to 2 decimal places for currency display
- **Calculation Precision:** Maintain 4 decimal places during calculations
- **Rounding Mode:** Use `RoundingMode.HALF_UP` for all rounding operations

### JSON Representation

Always serialize `BigDecimal` as string (not number) to preserve precision:

```java
@JsonSerialize(using = ToStringSerializer.class)
private BigDecimal debitAmount;
```

JSON representation:

```json
{
  "debitAmount": "1234.5678",
  "creditAmount": "1234.5678",
  "balanceAmount": "0.0000"
}
```

**Rationale:** JSON numbers lose precision; string representation preserves exact values.

### Balance Validation

Journal entries **MUST** balance to 4 decimal places:

```java
public boolean isBalanced() {
    BigDecimal totalDebits = lines.stream()
        .map(JournalEntryLine::getDebitAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    BigDecimal totalCredits = lines.stream()
        .map(JournalEntryLine::getCreditAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    BigDecimal difference = totalDebits.subtract(totalCredits).abs();
    return difference.compareTo(new BigDecimal("0.0001")) < 0;
}
```

**Tolerance:** Accept balance within ±0.0001 (1/10,000th of a currency unit).

---

## Enum Value Conventions

### Standard Pattern: UPPER_CASE_WITH_UNDERSCORES

All enum values **MUST** use `UPPER_CASE_WITH_UNDERSCORES`:

```java
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}

public enum JournalEntryStatus {
    DRAFT,
    PENDING,
    POSTED,
    REVERSED
}

public enum PostingRuleSetState {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}

public enum VendorBillStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    PAID,
    CANCELLED
}

public enum AccountingEventStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED,
    SUSPENDED
}
```

JSON representation:

```json
{
  "accountType": "ASSET",
  "status": "POSTED",
  "state": "PUBLISHED",
  "billStatus": "PENDING_REVIEW",
  "eventStatus": "PROCESSED"
}
```

### Accounting-Specific Enums

#### GLAccount Status (Derived)

Status is **derived** from `activationDate` and `deactivationDate`, not stored:

```
ACTIVE:          activationDate <= today < deactivationDate (or null)
INACTIVE:        deactivationDate <= today
NOT_YET_ACTIVE:  activationDate > today
```

**Note:** Frontend calculates status client-side; backend does not expose a `status` field on GLAccount.

#### Posting Rule Set State Machine

```
DRAFT → PUBLISHED → ARCHIVED
  ↓         ↓
  ├─────────┘
  └─────────→ (delete if never published)
```

- **DRAFT:** Editable, not used for JE generation
- **PUBLISHED:** Immutable, active for JE generation
- **ARCHIVED:** Immutable, inactive (historical only)

---

## Identifier Naming

### Pattern: `{ResourceName}Id`

All identifier fields **MUST** follow the pattern `{resourceName}Id`:

| Resource | ID Field | Example |
|----------|----------|---------|
| GLAccount | `glAccountId` | "GL-uuid-12345" |
| PostingCategory | `postingCategoryId` | "PC-uuid-67890" |
| MappingKey | `mappingKeyId` | "MK-uuid-11111" |
| GLMapping | `glMappingId` | "GM-uuid-22222" |
| PostingRuleSet | `postingRuleSetId` | "PRS-uuid-33333" |
| PostingRuleVersion | `versionId` | "PRV-uuid-44444" |
| JournalEntry | `journalEntryId` | "JE-uuid-55555" |
| JournalEntryLine | `lineId` | "JEL-uuid-66666" |
| VendorBill | `vendorBillId` | "VB-uuid-77777" |
| AccountingEvent | `eventId` | "AE-uuid-88888" |

### Foreign Key Identifiers

Foreign keys **MUST** use the full resource name:

```java
// ✅ CORRECT
private String glAccountId;          // FK to GLAccount
private String postingCategoryId;    // FK to PostingCategory
private String mappingKeyId;         // FK to MappingKey
private String postingRuleSetId;     // FK to PostingRuleSet
private String journalEntryId;       // FK to JournalEntry
private String sourceEventId;        // FK to AccountingEvent
private String vendorId;             // FK to Vendor (People domain)

// ❌ WRONG
private String glAccount;            // Missing "Id" suffix
private String postingCategory;      // Ambiguous
private String sourceEvent;          // Ambiguous
```

### Composite Keys (Versioning)

For versioned entities, use composite natural keys:

```java
// PostingRuleVersion
private String postingRuleSetId;     // Parent rule set
private Integer versionNumber;       // Version within set (1, 2, 3, ...)
private String versionId;            // Synthetic UUID for FK references
```

**Note:** `versionId` is the primary key for FK references; `(postingRuleSetId, versionNumber)` is a unique constraint.

---

## Timestamp Conventions

### Field Naming

Use descriptive past-tense names:

| Field | Meaning | Required |
|-------|---------|----------|
| `createdAt` | When record was created | Always |
| `modifiedAt` | When record was last modified | Always |
| `postedAt` | When JE was posted to GL | If posted |
| `approvedAt` | When vendor bill was approved | If approved |
| `publishedAt` | When posting rule version was published | If published |
| `archivedAt` | When posting rule version was archived | If archived |
| `receivedAt` | When accounting event was received | If event |
| `processedAt` | When accounting event was processed | If processed |
| `reversedAt` | When JE was reversed | If reversed |

### Format: ISO 8601 (RFC 3339)

Always use UTC timezone (`Z` suffix):

```json
{
  "createdAt": "2026-01-24T14:30:00Z",
  "modifiedAt": "2026-01-24T15:45:30Z",
  "postedAt": "2026-01-24T16:00:00Z",
  "approvedAt": null
}
```

### Effective Dating Fields

Use `LocalDate` (date-only, no time):

| Field | Meaning | Nullable |
|-------|---------|----------|
| `effectiveFrom` | Start date of validity | No |
| `effectiveTo` | End date of validity (exclusive) | Yes (null = no end date) |
| `transactionDate` | Business date of transaction | No |
| `activationDate` | When account becomes active | Yes |
| `deactivationDate` | When account becomes inactive | Yes |

```json
{
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "transactionDate": "2026-01-24"
}
```

**Semantic:** `effectiveFrom <= transactionDate < effectiveTo` (or effectiveTo is null).

---

## Effective Dating & Versioning

### Effective-Dated Entities (GLMapping)

GL mappings are **effective-dated** (not versioned):

- **Create new row** for each change with new effective date range
- **Immutable:** Once created, mappings are never edited
- **Overlap detection:** Backend rejects mappings with overlapping date ranges for same (postingCategoryId, mappingKeyId)

```json
{
  "glMappingId": "GM-uuid-12345",
  "postingCategoryId": "PC-001",
  "mappingKeyId": "MK-001",
  "glAccountId": "GL-1000",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-06-30",
  "dimensions": {
    "businessUnitId": "BU-001"
  }
}
```

**Query Pattern:** Resolve by `(postingCategoryId, mappingKeyId, transactionDate)` where `effectiveFrom <= transactionDate < effectiveTo`.

### Versioned Entities (PostingRuleSet)

Posting rule sets use **explicit versioning**:

- **Parent entity:** `PostingRuleSet` (metadata: name, eventType)
- **Child entity:** `PostingRuleVersion` (version number, state, rulesDefinition)
- **Version numbering:** Sequential integers (1, 2, 3, ...)
- **Immutability:** PUBLISHED and ARCHIVED versions are immutable

```json
{
  "postingRuleSetId": "PRS-uuid-12345",
  "name": "Vehicle Sale Rules",
  "eventType": "VehicleSaleEvent",
  "versions": [
    {
      "versionId": "PRV-uuid-11111",
      "versionNumber": 1,
      "state": "PUBLISHED",
      "rulesDefinition": "{...}",
      "publishedAt": "2026-01-01T00:00:00Z"
    },
    {
      "versionId": "PRV-uuid-22222",
      "versionNumber": 2,
      "state": "DRAFT",
      "rulesDefinition": "{...}",
      "publishedAt": null
    }
  ]
}
```

**Create New Version:**

```json
POST /v1/accounting/posting-rule-sets/{postingRuleSetId}/versions
{
  "baseVersionNumber": 1,
  "rulesDefinition": "{...}"
}
```

Response includes new `versionNumber` (auto-incremented).

---

## Collection & Pagination

### Paginated List Response

All list endpoints **MUST** return paginated responses:

```json
{
  "items": [
    { "glAccountId": "GL-001", "accountCode": "1000-000" },
    { "glAccountId": "GL-002", "accountCode": "1000-100" }
  ],
  "pagination": {
    "pageNumber": 1,
    "pageSize": 20,
    "totalCount": 150,
    "totalPages": 8
  }
}
```

### Request Parameters

Standardized query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `pageNumber` | Integer | 1 | Page number (1-indexed) |
| `pageSize` | Integer | 20 | Items per page (max 100) |
| `sortBy` | String | varies | Sort field (e.g., "accountCode") |
| `sortOrder` | String | "ASC" | Sort direction: ASC or DESC |

**Example:**

```
GET /v1/accounting/gl-accounts?pageNumber=1&pageSize=20&sortBy=accountCode&sortOrder=ASC
```

### Filter Parameters

Entity-specific filters:

**GLAccount:**
- `accountType`: Filter by account type (ASSET, LIABILITY, etc.)
- `search`: Search by account code or name (contains)

**JournalEntry:**
- `status`: Filter by status (DRAFT, POSTED, REVERSED)
- `transactionDateFrom`: Filter by transaction date (>= date)
- `transactionDateTo`: Filter by transaction date (<= date)
- `sourceEventId`: Filter by source event

**VendorBill:**
- `status`: Filter by status (PENDING_REVIEW, APPROVED, etc.)
- `vendorId`: Filter by vendor

---

## Error Response Format

### Standard Error Structure

All errors **MUST** follow this structure:

```json
{
  "errorCode": "UNBALANCED_RULES",
  "message": "Posting rule set validation failed: debits and credits do not balance",
  "path": "/v1/accounting/posting-rule-sets/PRS-123/versions/2/publish",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": {
    "totalDebits": "1000.00",
    "totalCredits": "950.00",
    "difference": "50.00",
    "conditions": [
      {
        "condition": "saleType == 'CASH'",
        "debits": "500.00",
        "credits": "500.00",
        "balanced": true
      },
      {
        "condition": "saleType == 'CREDIT'",
        "debits": "500.00",
        "credits": "450.00",
        "balanced": false
      }
    ]
  },
  "fieldErrors": null
}
```

### Standard Error Codes

#### Chart of Accounts (CoA)

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `DUPLICATE_ACCOUNT_CODE` | 409 | Account code already exists |
| `ACCOUNT_HAS_BALANCE` | 409 | Cannot deactivate account with non-zero balance |
| `ACCOUNT_REFERENCED_BY_MAPPINGS` | 409 | Cannot deactivate account referenced by active GL mappings |
| `ACCOUNT_USED_IN_POSTED_JE` | 409 | Cannot deactivate account used in posted journal entries |
| `INVALID_ACTIVATION_DATES` | 422 | Activation date must be before deactivation date |
| `ACCOUNT_NOT_FOUND` | 404 | GL account not found |
| `ACCOUNT_VERSION_CONFLICT` | 409 | Optimistic lock conflict; account modified by another user |

#### GL Mapping

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `MAPPING_OVERLAP` | 409 | Overlapping effective date range for same category + key |
| `INVALID_DIMENSION_VALUE` | 422 | Dimension value does not exist or is invalid |
| `REQUIRED_DIMENSION_MISSING` | 422 | Required dimension not provided for this posting category |
| `EFFECTIVE_DATE_INVALID` | 422 | effectiveFrom must be before effectiveTo |
| `MAPPING_NOT_FOUND` | 404 | GL mapping not found |
| `MAPPING_KEY_NOT_FOUND` | 404 | Mapping key not found |
| `POSTING_CATEGORY_NOT_FOUND` | 404 | Posting category not found |

#### Posting Rules

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `UNBALANCED_RULES` | 422 | Posting rules do not balance (debits != credits) |
| `INVALID_RULES_JSON` | 422 | rulesDefinition JSON is malformed or does not match schema |
| `POSTING_RULE_SET_NOT_FOUND` | 404 | Posting rule set not found |
| `VERSION_NOT_FOUND` | 404 | Posting rule version not found |
| `VERSION_ALREADY_PUBLISHED` | 409 | Version already in PUBLISHED state |
| `VERSION_ALREADY_ARCHIVED` | 409 | Version already in ARCHIVED state |
| `CANNOT_EDIT_PUBLISHED_VERSION` | 409 | Cannot edit version in PUBLISHED or ARCHIVED state |
| `BASE_VERSION_NOT_FOUND` | 404 | Base version for creating new version not found |
| `VERSION_NUMBER_CONFLICT` | 409 | Version number conflict (concurrent version creation) |

#### Journal Entries

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `JE_NOT_BALANCED` | 422 | Journal entry debits and credits do not balance |
| `JE_ALREADY_POSTED` | 409 | Cannot edit or delete posted journal entry |
| `JE_NOT_FOUND` | 404 | Journal entry not found |
| `CANNOT_REVERSE_DRAFT_JE` | 409 | Cannot reverse journal entry in DRAFT status |
| `CANNOT_REVERSE_ALREADY_REVERSED` | 409 | Cannot reverse journal entry that is already reversed |
| `MAPPING_RESOLUTION_FAILED` | 422 | Cannot resolve GL mapping for event (no mapping found) |
| `SOURCE_EVENT_NOT_FOUND` | 404 | Source accounting event not found |

#### Accounts Payable (AP)

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VENDOR_BILL_NOT_FOUND` | 404 | Vendor bill not found |
| `VENDOR_BILL_ALREADY_APPROVED` | 409 | Cannot approve already-approved bill |
| `VENDOR_BILL_ALREADY_REJECTED` | 409 | Cannot reject already-rejected bill |
| `VENDOR_BILL_ALREADY_PAID` | 409 | Cannot modify bill that is already paid |
| `APPROVAL_JUSTIFICATION_REQUIRED` | 422 | Justification required for approval |
| `REJECTION_REASON_REQUIRED` | 422 | Reason required for rejection |
| `VENDOR_NOT_FOUND` | 404 | Vendor not found (People domain integration) |

#### Event Ingestion

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `DUPLICATE_EVENT_ID` | 409 | Event ID already exists |
| `INVALID_EVENT_TYPE` | 422 | Event type not recognized |
| `INVALID_PAYLOAD` | 422 | Payload is not valid JSON or does not match schema |
| `PAYLOAD_MUST_BE_OBJECT` | 422 | Payload must be a JSON object (if policy enforces) |
| `EVENT_NOT_FOUND` | 404 | Accounting event not found |

### Field Errors

For validation errors, include `fieldErrors` map:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/v1/accounting/gl-accounts",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": null,
  "fieldErrors": {
    "accountCode": "Account code must be 10 characters or less",
    "accountType": "Account type must be one of: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE",
    "activationDate": "Activation date must be before deactivation date"
  }
}
```

---

## Optimistic Locking

### ETag-Based Locking (Recommended)

Use ETag headers for optimistic locking on critical entities:

- **GLAccount** (prevents concurrent edits)
- **PostingRuleVersion** (DRAFT only; immutable once published)
- **VendorBill** (prevents concurrent approval/rejection)

**Read (GET):**

```
GET /v1/accounting/gl-accounts/GL-12345

Response:
ETag: "33a64df551425fcc55e4d42a148795d9f25f89d4"

{
  "glAccountId": "GL-12345",
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  ...
}
```

**Update (PUT):**

```
PUT /v1/accounting/gl-accounts/GL-12345
If-Match: "33a64df551425fcc55e4d42a148795d9f25f89d4"

{
  "accountName": "Cash - Operating Account",
  ...
}
```

**Success (200):**

```
ETag: "new-etag-value"

{
  "glAccountId": "GL-12345",
  "accountName": "Cash - Operating Account",
  ...
}
```

**Conflict (412):**

```json
{
  "errorCode": "ACCOUNT_VERSION_CONFLICT",
  "message": "Account was modified by another user",
  "path": "/v1/accounting/gl-accounts/GL-12345",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": {
    "currentVersion": {
      "glAccountId": "GL-12345",
      "accountName": "Cash - Main Operating",
      "modifiedAt": "2026-01-24T14:28:00Z",
      "modifiedBy": "user456"
    }
  }
}
```

**Resolution:** Frontend must fetch current version, merge changes, and retry.

### Version Field Alternative

If ETag not feasible, use `version` field (integer):

```json
{
  "glAccountId": "GL-12345",
  "version": 5,
  ...
}
```

Update requires matching version:

```
PUT /v1/accounting/gl-accounts/GL-12345

{
  "version": 5,
  "accountName": "Cash - Operating Account"
}
```

Returns 409 if version does not match.

---

## Audit Trail Requirements

### Audit Fields (All Entities)

Every entity **MUST** include these audit fields:

```java
private Instant createdAt;       // When record was created
private String createdBy;        // User ID who created record
private Instant modifiedAt;      // When record was last modified
private String modifiedBy;       // User ID who last modified record
```

JSON representation:

```json
{
  "glAccountId": "GL-12345",
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  "createdAt": "2026-01-01T00:00:00Z",
  "createdBy": "user123",
  "modifiedAt": "2026-01-24T14:30:00Z",
  "modifiedBy": "user456"
}
```

### High-Risk Operations (Justification Required)

High-risk operations **MUST** include `justification` field:

**Operations requiring justification:**
- Post journal entry (`accounting:je:post`)
- Reverse journal entry (`accounting:je:reverse`)
- Approve vendor bill (`accounting:ap:approve`)
- Reject vendor bill (`accounting:ap:reject`)
- Publish posting rule set (`accounting:posting_rules:publish`)
- Deactivate GL account (`accounting:coa:deactivate`)

**Request:**

```json
POST /v1/accounting/journal-entries/{journalEntryId}/post

{
  "justification": "Month-end close for January 2026"
}
```

**Validation:** Backend rejects if `justification` is null or empty string:

```json
{
  "errorCode": "JUSTIFICATION_REQUIRED",
  "message": "Justification is required for posting journal entries",
  "path": "/v1/accounting/journal-entries/JE-12345/post",
  "timestamp": "2026-01-24T14:30:00Z"
}
```

### Audit Log Entry

Backend creates audit log entry for all high-risk operations:

```java
@Entity
public class AccountingAuditLog {
    private String auditLogId;
    private String entityType;        // "JournalEntry", "VendorBill", etc.
    private String entityId;          // Entity primary key
    private String operation;         // "POST", "REVERSE", "APPROVE", etc.
    private String userId;            // Who performed action
    private Instant timestamp;        // When action occurred
    private String justification;     // User-provided reason
    private String ipAddress;         // Client IP
    private String traceId;           // W3C trace context
    private String oldValue;          // Previous state (JSON)
    private String newValue;          // New state (JSON)
}
```

**Retention:** 7 years (financial regulatory compliance).

---

## Entity-Specific Contracts

### GLAccount

**Endpoints:**
- `GET /v1/accounting/gl-accounts` — List with pagination
- `GET /v1/accounting/gl-accounts/{glAccountId}` — Get details
- `POST /v1/accounting/gl-accounts` — Create account
- `PUT /v1/accounting/gl-accounts/{glAccountId}` — Update account
- `DELETE /v1/accounting/gl-accounts/{glAccountId}` — Deactivate account

**DTO Structure:**

```json
{
  "glAccountId": "GL-uuid-12345",
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  "accountType": "ASSET",
  "description": "Main operating cash account",
  "parentAccountId": null,
  "activationDate": "2026-01-01",
  "deactivationDate": null,
  "createdAt": "2026-01-01T00:00:00Z",
  "createdBy": "user123",
  "modifiedAt": "2026-01-24T14:30:00Z",
  "modifiedBy": "user456"
}
```

**Required Fields (Create):**
- `accountCode` (unique, max 20 chars)
- `accountName` (max 100 chars)
- `accountType` (enum)

**Optional Fields:**
- `description`
- `parentAccountId` (FK to parent account)
- `activationDate` (defaults to today)
- `deactivationDate` (defaults to null)

**Derived Status:**
- `ACTIVE`: `activationDate <= today < deactivationDate` (or null)
- `INACTIVE`: `deactivationDate <= today`
- `NOT_YET_ACTIVE`: `activationDate > today`

---

### PostingCategory, MappingKey, GLMapping

**Endpoints:**
- `GET /v1/accounting/posting-categories` — List categories
- `POST /v1/accounting/posting-categories` — Create category
- `GET /v1/accounting/mapping-keys?postingCategoryId={id}` — List keys for category
- `POST /v1/accounting/mapping-keys` — Create mapping key
- `GET /v1/accounting/gl-mappings?postingCategoryId={id}&mappingKeyId={id}` — List mappings
- `POST /v1/accounting/gl-mappings` — Create GL mapping
- `POST /v1/accounting/gl-mappings/resolve` — Test resolution (optional)

**GLMapping DTO:**

```json
{
  "glMappingId": "GM-uuid-12345",
  "postingCategoryId": "PC-001",
  "mappingKeyId": "MK-001",
  "glAccountId": "GL-1000",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "dimensions": {
    "businessUnitId": "BU-001",
    "locationId": "LOC-123",
    "departmentId": null,
    "costCenterId": null
  },
  "createdAt": "2026-01-01T00:00:00Z",
  "createdBy": "user123"
}
```

**Immutability:** Once created, GL mappings are never edited (create new effective-dated row instead).

**Overlap Detection:** Backend returns 409 if effective dates overlap for same (postingCategoryId, mappingKeyId):

```json
{
  "errorCode": "MAPPING_OVERLAP",
  "message": "GL mapping overlaps with existing mapping",
  "details": {
    "conflictingMappings": [
      {
        "glMappingId": "GM-existing",
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31",
        "glAccountId": "GL-1000"
      }
    ]
  }
}
```

---

### PostingRuleSet & PostingRuleVersion

**Endpoints:**
- `GET /v1/accounting/posting-rule-sets` — List rule sets with latest version summary
- `GET /v1/accounting/posting-rule-sets/{postingRuleSetId}` — Get rule set with all versions
- `POST /v1/accounting/posting-rule-sets` — Create rule set (creates v1 in DRAFT)
- `GET /v1/accounting/posting-rule-sets/{id}/versions/{versionNumber}` — Get specific version
- `POST /v1/accounting/posting-rule-sets/{id}/versions` — Create new version
- `POST /v1/accounting/posting-rule-sets/{id}/versions/{versionNumber}/publish` — Publish version
- `POST /v1/accounting/posting-rule-sets/{id}/versions/{versionNumber}/archive` — Archive version

**PostingRuleVersion DTO:**

```json
{
  "versionId": "PRV-uuid-12345",
  "postingRuleSetId": "PRS-uuid-67890",
  "versionNumber": 2,
  "state": "PUBLISHED",
  "rulesDefinition": "{\"conditions\":[...]}",
  "createdAt": "2026-01-20T00:00:00Z",
  "createdBy": "user123",
  "publishedAt": "2026-01-24T00:00:00Z",
  "publishedBy": "user456",
  "archivedAt": null,
  "archivedBy": null
}
```

**Rules Definition JSON Schema:**

```json
{
  "conditions": [
    {
      "condition": "saleType == 'CASH'",
      "lines": [
        {
          "accountCode": "1000-000",
          "debitExpression": "totalAmount",
          "creditExpression": "0"
        },
        {
          "accountCode": "4000-000",
          "debitExpression": "0",
          "creditExpression": "totalAmount"
        }
      ]
    }
  ]
}
```

**Balance Validation on Publish:**

Backend validates that sum(debits) == sum(credits) per condition. Returns 422 with details if unbalanced.

---

### JournalEntry & JournalEntryLine

**Endpoints:**
- `GET /v1/accounting/journal-entries` — List with filters
- `GET /v1/accounting/journal-entries/{journalEntryId}` — Get details with lines
- `POST /v1/accounting/journal-entries/build` — Build JE from source event
- `POST /v1/accounting/journal-entries/{journalEntryId}/post` — Post to GL
- `POST /v1/accounting/journal-entries/{journalEntryId}/reverse` — Create reversing JE

**JournalEntry DTO:**

```json
{
  "journalEntryId": "JE-uuid-12345",
  "status": "POSTED",
  "transactionDate": "2026-01-24",
  "description": "Vehicle sale - Invoice INV-001",
  "sourceEventId": "AE-event-67890",
  "sourceEventType": "VehicleSaleEvent",
  "postingRuleSetId": "PRS-rules-11111",
  "postingRuleVersionId": "PRV-version-22222",
  "reversalJournalEntryId": null,
  "reversedByJournalEntryId": null,
  "lines": [
    {
      "lineId": "JEL-line-11111",
      "lineNumber": 1,
      "glAccountId": "GL-1000",
      "accountCode": "1000-000",
      "accountName": "Cash - Operating",
      "debitAmount": "5000.00",
      "creditAmount": "0.00",
      "description": "Cash received",
      "dimensions": {
        "businessUnitId": "BU-001",
        "locationId": "LOC-123"
      }
    },
    {
      "lineId": "JEL-line-22222",
      "lineNumber": 2,
      "glAccountId": "GL-4000",
      "accountCode": "4000-000",
      "accountName": "Revenue - Vehicle Sales",
      "debitAmount": "0.00",
      "creditAmount": "5000.00",
      "description": "Vehicle sale revenue",
      "dimensions": {
        "businessUnitId": "BU-001",
        "locationId": "LOC-123"
      }
    }
  ],
  "totalDebits": "5000.00",
  "totalCredits": "5000.00",
  "isBalanced": true,
  "createdAt": "2026-01-24T14:00:00Z",
  "createdBy": "system",
  "postedAt": "2026-01-24T16:00:00Z",
  "postedBy": "user789"
}
```

**Balance Check:** `totalDebits - totalCredits` must be within ±0.0001.

**Immutability:** Once `status = POSTED`, JE and lines are immutable (cannot edit or delete).

---

### VendorBill

**Endpoints:**
- `GET /v1/accounting/vendor-bills` — List with status filter
- `GET /v1/accounting/vendor-bills/{vendorBillId}` — Get details with traceability
- `POST /v1/accounting/vendor-bills/{vendorBillId}/approve` — Approve bill
- `POST /v1/accounting/vendor-bills/{vendorBillId}/reject` — Reject bill

**VendorBill DTO:**

```json
{
  "vendorBillId": "VB-uuid-12345",
  "vendorId": "V-vendor-67890",
  "vendorName": "ACME Parts Supplier",
  "billNumber": "BILL-2026-001",
  "billDate": "2026-01-20",
  "dueDate": "2026-02-19",
  "totalAmount": "1500.00",
  "status": "APPROVED",
  "originEventId": "AE-event-11111",
  "originEventType": "GoodsReceivedEvent",
  "journalEntryId": "JE-je-22222",
  "paymentTransactionId": null,
  "createdAt": "2026-01-20T10:00:00Z",
  "createdBy": "system",
  "approvedAt": "2026-01-24T15:00:00Z",
  "approvedBy": "user123",
  "approvalJustification": "Verified goods received and pricing correct"
}
```

**Traceability:**
- `originEventId` → Source accounting event
- `journalEntryId` → JE created from event
- `paymentTransactionId` → Payment (if paid)

---

### AccountingEvent

**Endpoints:**
- `POST /v1/accounting/events/submitSync` — Submit event (sync ingestion)
- `GET /v1/accounting/events/{eventId}` — Get event details
- `GET /v1/accounting/events` — List events with filters
- `POST /v1/accounting/events/{eventId}/retry` — Retry failed processing

**AccountingEvent DTO:**

```json
{
  "eventId": "AE-uuid-12345",
  "eventType": "VehicleSaleEvent",
  "transactionDate": "2026-01-24",
  "payload": {
    "invoiceId": "INV-001",
    "saleType": "CASH",
    "totalAmount": 5000.00,
    "vehicleId": "V-12345"
  },
  "status": "PROCESSED",
  "receivedAt": "2026-01-24T14:00:00Z",
  "processedAt": "2026-01-24T14:00:15Z",
  "journalEntryId": "JE-je-67890",
  "errorMessage": null
}
```

**Submit Response (Acknowledgement):**

```json
{
  "eventId": "AE-uuid-12345",
  "status": "RECEIVED",
  "receivedAt": "2026-01-24T14:00:00Z",
  "sequenceNumber": 12345
}
```

---

## Examples

### Example 1: Create GL Account

**Request:**

```
POST /v1/accounting/gl-accounts
Authorization: Bearer {jwt}
Content-Type: application/json

{
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  "accountType": "ASSET",
  "description": "Main operating cash account",
  "activationDate": "2026-01-01"
}
```

**Success Response (201):**

```
Location: /v1/accounting/gl-accounts/GL-uuid-12345
ETag: "etag-value-12345"

{
  "glAccountId": "GL-uuid-12345",
  "accountCode": "1000-000",
  "accountName": "Cash - Operating",
  "accountType": "ASSET",
  "description": "Main operating cash account",
  "parentAccountId": null,
  "activationDate": "2026-01-01",
  "deactivationDate": null,
  "createdAt": "2026-01-24T14:30:00Z",
  "createdBy": "user123",
  "modifiedAt": "2026-01-24T14:30:00Z",
  "modifiedBy": "user123"
}
```

**Error Response (409 Duplicate):**

```json
{
  "errorCode": "DUPLICATE_ACCOUNT_CODE",
  "message": "Account code '1000-000' already exists",
  "path": "/v1/accounting/gl-accounts",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": {
    "existingAccountId": "GL-uuid-existing"
  }
}
```

---

### Example 2: Create GL Mapping with Overlap Detection

**Request:**

```
POST /v1/accounting/gl-mappings
Authorization: Bearer {jwt}

{
  "postingCategoryId": "PC-001",
  "mappingKeyId": "MK-001",
  "glAccountId": "GL-1000",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31",
  "dimensions": {
    "businessUnitId": "BU-001"
  }
}
```

**Error Response (409 Overlap):**

```json
{
  "errorCode": "MAPPING_OVERLAP",
  "message": "GL mapping overlaps with existing mapping for same category and key",
  "path": "/v1/accounting/gl-mappings",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": {
    "postingCategoryId": "PC-001",
    "mappingKeyId": "MK-001",
    "conflictingMappings": [
      {
        "glMappingId": "GM-existing-12345",
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-06-30",
        "glAccountId": "GL-1000"
      }
    ]
  }
}
```

---

### Example 3: Publish Posting Rule Set Version with Balance Validation

**Request:**

```
POST /v1/accounting/posting-rule-sets/PRS-12345/versions/2/publish
Authorization: Bearer {jwt}

{
  "justification": "Reviewed and approved by Controller - effective for January 2026 transactions"
}
```

**Error Response (422 Unbalanced):**

```json
{
  "errorCode": "UNBALANCED_RULES",
  "message": "Posting rule set validation failed: debits and credits do not balance",
  "path": "/v1/accounting/posting-rule-sets/PRS-12345/versions/2/publish",
  "timestamp": "2026-01-24T14:30:00Z",
  "details": {
    "totalDebits": "1000.00",
    "totalCredits": "950.00",
    "difference": "50.00",
    "conditions": [
      {
        "condition": "saleType == 'CASH'",
        "debits": "500.00",
        "credits": "500.00",
        "balanced": true
      },
      {
        "condition": "saleType == 'CREDIT'",
        "debits": "500.00",
        "credits": "450.00",
        "balanced": false,
        "message": "Condition does not balance: debits 500.00, credits 450.00"
      }
    ]
  }
}
```

---

### Example 4: Post Journal Entry

**Request:**

```
POST /v1/accounting/journal-entries/JE-12345/post
Authorization: Bearer {jwt}

{
  "justification": "Month-end close for January 2026"
}
```

**Success Response (200):**

```json
{
  "journalEntryId": "JE-12345",
  "status": "POSTED",
  "postedAt": "2026-01-24T16:00:00Z",
  "postedBy": "user123",
  "justification": "Month-end close for January 2026"
}
```

**Error Response (409 Already Posted):**

```json
{
  "errorCode": "JE_ALREADY_POSTED",
  "message": "Journal entry JE-12345 is already posted and cannot be modified",
  "path": "/v1/accounting/journal-entries/JE-12345/post",
  "timestamp": "2026-01-24T16:00:00Z",
  "details": {
    "postedAt": "2026-01-24T15:00:00Z",
    "postedBy": "user456"
  }
}
```

---

## Summary

This guide establishes comprehensive backend contract standards for the Accounting domain:

- ✅ **Consistent naming:** camelCase fields, UPPER_SNAKE enums, `{resource}Id` pattern
- ✅ **Monetary precision:** BigDecimal with 4 decimal places, string serialization
- ✅ **Effective dating:** LocalDate for date-only fields, effective range queries
- ✅ **Versioning:** Explicit version numbering for PostingRuleSet, immutability rules
- ✅ **Error codes:** Standardized error structure with 26 accounting-specific codes
- ✅ **Optimistic locking:** ETag-based concurrency control
- ✅ **Audit trails:** createdAt/By, modifiedAt/By, justification for high-risk operations
- ✅ **Pagination:** Standard request/response patterns
- ✅ **Balance validation:** Tolerance of ±0.0001 for journal entries
- ✅ **Immutability:** Posted JEs, published rule versions, GL mappings

All backend developers and frontend developers **MUST** follow these standards when implementing Accounting domain APIs.

---

## Change Log

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-01-24 | 1.0 | Backend Team | Initial accounting backend contract guide |

---

## References

- CRM Backend Contract Guide: `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Accounting Permission Taxonomy: `domains/accounting/.business-rules/PERMISSION_TAXONOMY.md`
- Accounting Questions: `domains/accounting/accounting-questions.txt`
- API Catalog: `.github/agents/api-catalog.yml`
