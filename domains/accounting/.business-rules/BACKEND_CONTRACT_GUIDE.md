# Accounting Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-01-27  
**OpenAPI Source:** `pos-accounting/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Accounting domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

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

#### AuditTrailResponse.accountingIntent

- `REVENUE_ADJUSTMENT`
- `PAYMENT_REVERSAL`
- `CUSTOMER_CREDIT`
- `WRITE_OFF`
- `REVENUE_REVERSAL`
- `PAYMENT_RECOVERY`

#### AuditTrailResponse.accountingStatus

- `PENDING_POSTING`
- `POSTED`
- `FAILED`

#### AuditTrailResponse.cancellationType

- `ORDER_CANCELLED`
- `INVOICE_CANCELLED`

#### AuditTrailResponse.exceptionType

- `PRICE_OVERRIDE`
- `REFUND`
- `CANCELLATION`

#### AuditTrailResponse.originalPaymentStatus

- `PENDING`
- `SETTLED`

#### AuditTrailResponse.policyValidationResult

- `APPROVED`
- `REJECTED_FORBIDDEN`
- `REJECTED_THRESHOLD_EXCEEDED`

#### AuditTrailResponse.refundMethod

- `VOID`
- `CHARGEBACK`
- `CASH_REFUND`
- `CREDIT_MEMO`

#### AuditTrailResponse.refundType

- `REVERSAL`
- `CREDIT_MEMO`
- `ADJUSTMENT`

#### CancellationRequest.cancellationType

- `ORDER_CANCELLED`
- `INVOICE_CANCELLED`

#### InvoiceStatusResponse.status

- `PAID`
- `PARTIALLY_PAID`
- `UNPAID`
- `FAILED`

#### RefundRequest.originalPaymentStatus

- `PENDING`
- `SETTLED`

#### RefundRequest.refundType

- `REVERSAL`
- `CREDIT_MEMO`
- `ADJUSTMENT`

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
GET /v1/accounting/entities/123
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

This domain exposes **44** REST API endpoints:

| Method | Path | Summary |
|--------|------|---------|
| GET | `/api/audit/actor/{actorId}` |  |
| POST | `/api/audit/cancellation` |  |
| GET | `/api/audit/invoice/{invoiceId}` |  |
| GET | `/api/audit/order/{orderId}` |  |
| POST | `/api/audit/price-override` |  |
| GET | `/api/audit/range` |  |
| POST | `/api/audit/refund` |  |
| GET | `/api/audit/type/{type}` |  |
| GET | `/v1/accounting/events` | List events |
| POST | `/v1/accounting/events` | Submit event |
| GET | `/v1/accounting/events/{eventId}` | Get event |
| GET | `/v1/accounting/events/{eventId}/processing-log` | Get event processing log |
| POST | `/v1/accounting/events/{eventId}/retry` | Retry event processing |
| GET | `/v1/accounting/gl-accounts` | List GL accounts |
| POST | `/v1/accounting/gl-accounts` | Create GL account |
| GET | `/v1/accounting/gl-accounts/{glAccountId}` | Get GL account |
| PUT | `/v1/accounting/gl-accounts/{glAccountId}` | Update GL account |
| POST | `/v1/accounting/gl-accounts/{glAccountId}/activate` | Activate GL account |
| POST | `/v1/accounting/gl-accounts/{glAccountId}/archive` | Archive GL account |
| GET | `/v1/accounting/gl-accounts/{glAccountId}/balance` | Get GL account balance |
| POST | `/v1/accounting/gl-accounts/{glAccountId}/deactivate` | Deactivate GL account |
| POST | `/v1/accounting/glAccounts` | Create GL account (legacy path) |
| GET | `/v1/accounting/glAccounts/{accountId}` | Get GL account (legacy path) |
| PUT | `/v1/accounting/glAccounts/{accountId}` | Update GL account (legacy path) |
| GET | `/v1/accounting/invoice/{invoiceId}/status` | Get invoice status |
| GET | `/v1/accounting/journal-entries` | List journal entries |
| POST | `/v1/accounting/journal-entries` | Create journal entry |
| GET | `/v1/accounting/journal-entries/{journalEntryId}` | Get journal entry |
| PUT | `/v1/accounting/journal-entries/{journalEntryId}` | Update journal entry |
| POST | `/v1/accounting/journal-entries/{journalEntryId}/post` | Post journal entry |
| POST | `/v1/accounting/journal-entries/{journalEntryId}/reverse` | Reverse journal entry |
| POST | `/v1/accounting/payments/{paymentId}/applications` | Apply payment |
| POST | `/v1/accounting/payments/{paymentId}/reverse` | Reverse payment |
| GET | `/v1/accounting/payments/{paymentId}/status` | Get payment status |
| POST | `/v1/accounting/payments/{paymentId}/void` | Void payment |
| GET | `/v1/accounting/posting-rules` | List posting rule sets |
| POST | `/v1/accounting/posting-rules` | Create posting rule set |
| GET | `/v1/accounting/posting-rules/{postingRuleSetId}` | Get posting rule set |
| POST | `/v1/accounting/posting-rules/{postingRuleSetId}/archive` | Archive posting rule set |
| POST | `/v1/accounting/posting-rules/{postingRuleSetId}/publish` | Publish posting rule set |
| GET | `/v1/accounting/posting-rules/{postingRuleSetId}/versions` | List posting rule versions |
| GET | `/v1/accounting/traceability/{journalEntryId}` | Get journal traceability |
| POST | `/v1/invoice/invoices` | Regenerate invoice from workorder |
| GET | `/v1/invoice/rules/{customerId}` | Get billing rules |

### Endpoint Details

#### GET /api/audit/actor/{actorId}

**Operation ID:** `getByActor`

**Parameters:**

- `actorId` (path, Required, string):
- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: OK

---

#### POST /api/audit/cancellation

**Operation ID:** `recordCancellation`

**Responses:**

- `200`: OK

---

#### GET /api/audit/invoice/{invoiceId}

**Operation ID:** `getByInvoiceId`

**Parameters:**

- `invoiceId` (path, Required, string):

**Responses:**

- `200`: OK

---

#### GET /api/audit/order/{orderId}

**Operation ID:** `getByOrderId`

**Parameters:**

- `orderId` (path, Required, string):

**Responses:**

- `200`: OK

---

#### POST /api/audit/price-override

**Operation ID:** `recordPriceOverride`

**Responses:**

- `200`: OK

---

#### GET /api/audit/range

**Operation ID:** `getByDateRange`

**Parameters:**

- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: OK

---

#### POST /api/audit/refund

**Operation ID:** `recordRefund`

**Responses:**

- `200`: OK

---

#### GET /api/audit/type/{type}

**Operation ID:** `getByType`

**Parameters:**

- `type` (path, Required, string):
- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: OK

---

#### GET /v1/accounting/events

**Summary:** List events

**Description:** Retrieve paginated accounting events with optional filters.

**Operation ID:** `listEvents`

**Parameters:**

- `page` (query, Optional, integer): Page index (0-based)
- `size` (query, Optional, integer): Page size
- `eventType` (query, Optional, string): Filter by event type
- `status` (query, Optional, string): Filter by processing status

**Responses:**

- `200`: Events listed
- `403`: Forbidden

---

#### POST /v1/accounting/events

**Summary:** Submit event

**Description:** Submit a new accounting event for processing.

**Operation ID:** `submitEvent`

**Responses:**

- `202`: Event accepted for processing
- `400`: Invalid request

---

#### GET /v1/accounting/events/{eventId}

**Summary:** Get event

**Description:** Retrieve details for an accounting event.

**Operation ID:** `getEvent`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `200`: Event returned
- `404`: Event not found

---

#### GET /v1/accounting/events/{eventId}/processing-log

**Summary:** Get event processing log

**Description:** Retrieve the processing log for an accounting event.

**Operation ID:** `getEventProcessingLog`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `200`: Processing log returned
- `404`: Event not found

---

#### POST /v1/accounting/events/{eventId}/retry

**Summary:** Retry event processing

**Description:** Retry processing for a failed accounting event.

**Operation ID:** `retryEventProcessing`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `202`: Retry scheduled
- `404`: Event not found

---

#### GET /v1/accounting/gl-accounts

**Summary:** List GL accounts

**Description:** Retrieve paginated GL accounts filtered by status and sorted by a field.

**Operation ID:** `listGLAccounts`

**Parameters:**

- `page` (query, Optional, integer): Page index (0-based)
- `size` (query, Optional, integer): Page size
- `sort` (query, Optional, string): Sort field
- `status` (query, Optional, string): Filter by account status

**Responses:**

- `200`: GL accounts listed
- `403`: Forbidden

---

#### POST /v1/accounting/gl-accounts

**Summary:** Create GL account

**Description:** Create a new GL account.

**Operation ID:** `createGLAccount`

**Responses:**

- `201`: GL account created
- `400`: Invalid request

---

#### GET /v1/accounting/gl-accounts/{glAccountId}

**Summary:** Get GL account

**Description:** Retrieve a GL account by identifier.

**Operation ID:** `getGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account returned
- `404`: GL account not found

---

#### PUT /v1/accounting/gl-accounts/{glAccountId}

**Summary:** Update GL account

**Description:** Update details for an existing GL account.

**Operation ID:** `updateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account updated
- `404`: GL account not found

---

#### POST /v1/accounting/gl-accounts/{glAccountId}/activate

**Summary:** Activate GL account

**Description:** Mark a GL account as active.

**Operation ID:** `activateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account activated
- `404`: GL account not found

---

#### POST /v1/accounting/gl-accounts/{glAccountId}/archive

**Summary:** Archive GL account

**Description:** Archive a GL account and remove it from active use.

**Operation ID:** `archiveGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account archived
- `404`: GL account not found

---

#### GET /v1/accounting/gl-accounts/{glAccountId}/balance

**Summary:** Get GL account balance

**Description:** Retrieve the current balance for a GL account.

**Operation ID:** `getAccountBalance`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: Balance returned
- `404`: GL account not found

---

#### POST /v1/accounting/gl-accounts/{glAccountId}/deactivate

**Summary:** Deactivate GL account

**Description:** Mark a GL account as inactive.

**Operation ID:** `deactivateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account deactivated
- `404`: GL account not found

---

#### POST /v1/accounting/glAccounts

**Summary:** Create GL account (legacy path)

**Description:** Create a GL account via the legacy endpoint.

**Operation ID:** `createGlAccount`

**Responses:**

- `201`: GL account created
- `400`: Invalid request

---

#### GET /v1/accounting/glAccounts/{accountId}

**Summary:** Get GL account (legacy path)

**Description:** Retrieve a GL account by identifier.

**Operation ID:** `getGlAccount`

**Parameters:**

- `accountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account returned
- `404`: GL account not found

---

#### PUT /v1/accounting/glAccounts/{accountId}

**Summary:** Update GL account (legacy path)

**Description:** Update a GL account via the legacy endpoint.

**Operation ID:** `manageGlAccount`

**Parameters:**

- `accountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account updated
- `404`: GL account not found

---

#### GET /v1/accounting/invoice/{invoiceId}/status

**Summary:** Get invoice status

**Description:** Retrieve current payment status for an invoice.

**Operation ID:** `getInvoiceStatus`

**Parameters:**

- `invoiceId` (path, Required, string): Invoice identifier

**Responses:**

- `200`: Invoice status returned
- `404`: Invoice not found
- `500`: Error retrieving invoice status

---

#### GET /v1/accounting/journal-entries

**Summary:** List journal entries

**Description:** Retrieve paginated journal entries.

**Operation ID:** `listJournalEntries`

**Parameters:**

- `page` (query, Optional, integer): Page index (0-based)
- `size` (query, Optional, integer): Page size
- `sort` (query, Optional, string): Sort field

**Responses:**

- `200`: Journal entries listed
- `403`: Forbidden

---

#### POST /v1/accounting/journal-entries

**Summary:** Create journal entry

**Description:** Create a new journal entry.

**Operation ID:** `createJournalEntry`

**Responses:**

- `201`: Journal entry created
- `400`: Invalid request

---

#### GET /v1/accounting/journal-entries/{journalEntryId}

**Summary:** Get journal entry

**Description:** Retrieve a journal entry by identifier.

**Operation ID:** `getJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry returned
- `404`: Journal entry not found

---

#### PUT /v1/accounting/journal-entries/{journalEntryId}

**Summary:** Update journal entry

**Description:** Update an existing journal entry.

**Operation ID:** `updateJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry updated
- `404`: Journal entry not found

---

#### POST /v1/accounting/journal-entries/{journalEntryId}/post

**Summary:** Post journal entry

**Description:** Post a draft journal entry to the ledger.

**Operation ID:** `postJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry posted
- `404`: Journal entry not found

---

#### POST /v1/accounting/journal-entries/{journalEntryId}/reverse

**Summary:** Reverse journal entry

**Description:** Reverse a posted journal entry.

**Operation ID:** `reverseJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry reversed
- `404`: Journal entry not found

---

#### POST /v1/accounting/payments/{paymentId}/applications

**Summary:** Apply payment

**Description:** Apply a payment to an invoice and update its status.

**Operation ID:** `applyPayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment applied
- `400`: Invalid payment request
- `500`: Processing error

---

#### POST /v1/accounting/payments/{paymentId}/reverse

**Summary:** Reverse payment

**Description:** Reverse a previously applied payment.

**Operation ID:** `reversePayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment reversed
- `404`: Payment not found

---

#### GET /v1/accounting/payments/{paymentId}/status

**Summary:** Get payment status

**Description:** Retrieve status for an accounts payable payment.

**Operation ID:** `getPaymentStatus`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment status returned
- `404`: Payment not found

---

#### POST /v1/accounting/payments/{paymentId}/void

**Summary:** Void payment

**Description:** Void a payment before settlement.

**Operation ID:** `voidPayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment voided
- `404`: Payment not found

---

#### GET /v1/accounting/posting-rules

**Summary:** List posting rule sets

**Description:** Retrieve paginated posting rule sets.

**Operation ID:** `listPostingRuleSets`

**Parameters:**

- `page` (query, Optional, integer): Page index (0-based)
- `size` (query, Optional, integer): Page size
- `sort` (query, Optional, string): Sort field

**Responses:**

- `200`: Posting rule sets listed
- `403`: Forbidden

---

#### POST /v1/accounting/posting-rules

**Summary:** Create posting rule set

**Description:** Create a new posting rule set.

**Operation ID:** `createPostingRuleSet`

**Responses:**

- `201`: Posting rule set created
- `400`: Invalid request

---

#### GET /v1/accounting/posting-rules/{postingRuleSetId}

**Summary:** Get posting rule set

**Description:** Retrieve a posting rule set by identifier.

**Operation ID:** `getPostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set returned
- `404`: Posting rule set not found

---

#### POST /v1/accounting/posting-rules/{postingRuleSetId}/archive

**Summary:** Archive posting rule set

**Description:** Archive a posting rule set.

**Operation ID:** `archivePostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set archived
- `404`: Posting rule set not found

---

#### POST /v1/accounting/posting-rules/{postingRuleSetId}/publish

**Summary:** Publish posting rule set

**Description:** Publish a posting rule set version.

**Operation ID:** `publishPostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set published
- `404`: Posting rule set not found

---

#### GET /v1/accounting/posting-rules/{postingRuleSetId}/versions

**Summary:** List posting rule versions

**Description:** List versions for a posting rule set.

**Operation ID:** `listPostingRuleVersions`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier
- `page` (query, Optional, integer): Page index (0-based)
- `size` (query, Optional, integer): Page size

**Responses:**

- `200`: Posting rule versions listed
- `404`: Posting rule set not found

---

#### GET /v1/accounting/traceability/{journalEntryId}

**Summary:** Get journal traceability

**Description:** Trace a journal entry across related records.

**Operation ID:** `getJournalTraceability`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Traceability returned
- `404`: Journal entry not found

---

#### POST /v1/invoice/invoices

**Summary:** Regenerate invoice from workorder

**Description:** Regenerate an invoice from a workorder.

**Operation ID:** `regenerateInvoiceFromWorkorder`

**Responses:**

- `202`: Invoice regeneration accepted
- `400`: Invalid request

---

#### GET /v1/invoice/rules/{customerId}

**Summary:** Get billing rules

**Description:** Retrieve billing rules for a customer.

**Operation ID:** `getBillingRules`

**Parameters:**

- `customerId` (path, Required, string): Customer identifier

**Responses:**

- `200`: Billing rules returned
- `404`: Customer not found

---

## Entity-Specific Contracts

### AuditTrailResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `accountingIntent` | string | No |  |
| `accountingStatus` | string | No |  |
| `actorId` | string (uuid) | No |  |
| `actorRole` | string | No |  |
| `adjustedPrice` | number | No |  |
| `afterSnapshot` | string | No |  |
| `auditId` | string (uuid) | No |  |
| `authorizationLevel` | string | No |  |
| `beforeSnapshot` | string | No |  |
| `cancellationType` | string | No |  |
| `exceptionType` | string | No |  |
| `expectedAccountingOutcome` | string | No |  |
| `forbiddenCategoryCode` | string | No |  |
| `glReversalStatus` | string | No |  |
| `invoiceId` | string (uuid) | No |  |
| `lineItemId` | string (uuid) | No |  |
| `linkedSourceIds` | string | No |  |
| `orderId` | string (uuid) | No |  |
| `originalPaymentStatus` | string | No |  |
| `originalPrice` | number | No |  |
| `overrideAmountOrPercent` | string | No |  |
| `partialPaymentInfo` | string | No |  |
| `paymentId` | string (uuid) | No |  |
| `policyValidationResult` | string | No |  |
| `policyVersion` | string | No |  |
| `reason` | string | No |  |
| `refundAmount` | number | No |  |
| `refundMethod` | string | No |  |
| `refundType` | string | No |  |
| `sourceDocumentId` | string | No |  |
| `sourceEventId` | string (uuid) | No |  |
| `timestamp` | string (date-time) | No |  |

### CancellationRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actorId` | string (uuid) | Yes |  |
| `actorRole` | string | Yes |  |
| `afterSnapshot` | string | Yes |  |
| `beforeSnapshot` | string | Yes |  |
| `cancellationType` | string | Yes |  |
| `invoiceId` | string (uuid) | No |  |
| `orderId` | string (uuid) | No |  |
| `partialPaymentInfo` | string | No |  |
| `reason` | string | Yes |  |

### InvoiceStatusResponse

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `invoiceId` | string | No |  |
| `invoiceTotal` | number | No |  |
| `lastUpdated` | string (date-time) | No |  |
| `latestTransactionReference` | string | No |  |
| `remainingBalance` | number | No |  |
| `status` | string | No |  |
| `totalPaid` | number | No |  |

### PaymentAppliedRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `idempotencyKey` | string | Yes |  |
| `invoiceId` | string | Yes |  |
| `invoiceTotal` | number | Yes |  |
| `paymentAmount` | number | Yes |  |
| `paymentFailed` | boolean | No |  |
| `transactionReference` | string | Yes |  |

### PriceOverrideRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actorId` | string (uuid) | Yes |  |
| `actorRole` | string | Yes |  |
| `adjustedPrice` | number | Yes |  |
| `lineItemId` | string (uuid) | Yes |  |
| `orderId` | string (uuid) | Yes |  |
| `originalPrice` | number | Yes |  |
| `reason` | string | Yes |  |

### RefundRequest

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `actorId` | string (uuid) | Yes |  |
| `actorRole` | string | Yes |  |
| `invoiceId` | string (uuid) | Yes |  |
| `originalPaymentStatus` | string | Yes |  |
| `paymentId` | string (uuid) | Yes |  |
| `reason` | string | Yes |  |
| `refundAmount` | number | Yes |  |
| `refundType` | string | Yes |  |

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST /v1/invoice/invoices
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
GET /v1/accounting/journal-entries/{journalEntryId}
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

This guide establishes standardized contracts for the Accounting domain:

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

- OpenAPI Specification: `pos-accounting/target/openapi.json`
- Domain Agent Guide: `domains/accounting/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/accounting/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-01-27 14:27:53 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`
