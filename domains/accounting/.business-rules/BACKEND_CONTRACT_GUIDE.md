---
title: Accounting Backend Contract Guide
domain: accounting
doc_type: backend_contract
contract:
  status: draft
  owner_repo: louisburroughs/durion
  guide_path: domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
  openapi_source: durion-positivity-backend/pos-accounting/openapi.json
traceability:
  capability_manifest: docs/capabilities
last_updated: 2026-02-19
---

# Accounting Backend Contract Guide

**Version:** 1.4  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** 2026-02-10  
**OpenAPI Source:** `durion-positivity-backend/pos-accounting/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the Accounting domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.

---

## Implementation Links

- Capability: https://github.com/louisburroughs/durion/issues/55
- Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/122
- Capability (CAP-278): https://github.com/louisburroughs/durion/issues/278
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/472
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/473
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/474
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/475
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/476
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/477
- Backend child issue (CAP-278): https://github.com/louisburroughs/durion-positivity-backend/issues/478

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

#### CreditMemoResponse.status

- `DRAFT` - Credit Memo created but not yet posted to GL
- `POSTED` - Credit Memo posted to GL; AR reduced
- `APPLIED` - Credit Memo applied to customer account or future invoice
- `VOIDED` - Credit Memo voided/reversed

#### CreateCreditMemoRequest.reasonCode

**Standard Reason Codes:**

- `RETURNED_GOODS` - Customer returned merchandise
- `PRICING_ERROR` - Incorrect pricing on original invoice
- `SERVICE_CREDIT` - Credit for service level issues
- `BILLING_ERROR` - General billing error correction
- `DAMAGED_GOODS` - Goods received damaged
- `GOODWILL_GESTURE` - Customer satisfaction credit

**Note:** Organizations may define additional reason codes. All reason codes must be 1-50 characters, UPPER_SNAKE_CASE format, and documented in accounting policies.

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
GET http://localhost:8080/v1/accounting/events?page=0&size=10
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

This domain exposes **67** REST API endpoints:

| Method | Path | Summary |
| ------ | ---- | ------- |
| GET | `http://localhost:8080/v1/accounting/audit/actor/{actorId}` | Get audit trail by actor |
| GET | `http://localhost:8080/v1/accounting/audit/invoice/{invoiceId}` | Get audit trail for invoice |
| GET | `http://localhost:8080/v1/accounting/audit/order/{orderId}` | Get audit trail for order |
| GET | `http://localhost:8080/v1/accounting/audit/range` | Get audit trail by date range |
| GET | `http://localhost:8080/v1/accounting/audit/type/{type}` | Get audit trail by exception type |
| POST | `http://localhost:8080/v1/accounting/audit/cancellation` | Record a cancellation |
| POST | `http://localhost:8080/v1/accounting/audit/price-override` | Record a price override |
| POST | `http://localhost:8080/v1/accounting/audit/refund` | Record a refund |
| GET | `http://localhost:8080/v1/accounting/credit-memos` | List credit memos |
| POST | `http://localhost:8080/v1/accounting/credit-memos` | Create credit memo |
| GET | `http://localhost:8080/v1/accounting/credit-memos/{creditMemoId}` | Get credit memo |
| GET | `http://localhost:8080/v1/accounting/events` | List events |
| POST | `http://localhost:8080/v1/accounting/events` | Submit event |
| GET | `http://localhost:8080/v1/accounting/events/{eventId}` | Get event |
| GET | `http://localhost:8080/v1/accounting/events/{eventId}/processing-log` | Get event processing log |
| POST | `http://localhost:8080/v1/accounting/events/{eventId}/retry` | Retry event processing |
| GET | `http://localhost:8080/v1/accounting/gl-accounts` | List GL accounts |
| POST | `http://localhost:8080/v1/accounting/gl-accounts` | Create GL account |
| GET | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}` | Get GL account |
| PUT | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}` | Update GL account |
| POST | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/activate` | Activate GL account |
| POST | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/archive` | Archive GL account |
| POST | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/deactivate` | Deactivate GL account |
| GET | `http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/balance` | Get GL account balance |
| POST | `http://localhost:8080/v1/accounting/glAccounts` | Create GL account (legacy path) |
| GET | `http://localhost:8080/v1/accounting/glAccounts/{accountId}` | Get GL account (legacy path) |
| PUT | `http://localhost:8080/v1/accounting/glAccounts/{accountId}` | Update GL account (legacy path) |
| GET | `http://localhost:8080/v1/accounting/invoice/{invoiceId}/status` | Get invoice status |
| POST | `http://localhost:8080/v1/accounting/invoices/{invoiceId}/pay` | Apply payment (LEGACY) |
| GET | `http://localhost:8080/v1/accounting/journal-entries` | List journal entries |
| POST | `http://localhost:8080/v1/accounting/journal-entries` | Create journal entry |
| GET | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}` | Get journal entry |
| PUT | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}` | Update journal entry |
| POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/post` | Post journal entry |
| POST | `http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/reverse` | Reverse journal entry |
| GET | `http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}` | Get mapping key |
| POST | `http://localhost:8080/v1/accounting/mapping-keys` | Create mapping key |
| PUT | `http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}` | Update mapping key |
| POST | `http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}/deactivate` | Deactivate mapping key |
| POST | `http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse` | Reverse payment application |
| POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/applications` | Apply payment |
| POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/reverse` | Reverse payment |
| GET | `http://localhost:8080/v1/accounting/payments/{paymentId}/status` | Get payment status |
| POST | `http://localhost:8080/v1/accounting/payments/{paymentId}/void` | Void payment |
| GET | `http://localhost:8080/v1/accounting/posting-categories` | List posting categories |
| POST | `http://localhost:8080/v1/accounting/posting-categories` | Create posting category |
| GET | `http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}` | Get posting category |
| PUT | `http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}` | Update posting category |
| POST | `http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/deactivate` | Deactivate posting category |
| GET | `http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/mapping-keys` | List mapping keys by category |
| GET | `http://localhost:8080/v1/accounting/posting-rules` | List posting rule sets |
| POST | `http://localhost:8080/v1/accounting/posting-rules` | Create posting rule set |
| GET | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}` | Get posting rule set |
| POST | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/archive` | Archive posting rule set |
| POST | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/publish` | Publish posting rule set |
| GET | `http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/versions` | List posting rule versions |
| POST | `http://localhost:8080/v1/accounting/ap/payments` | Execute vendor payment |
| GET | `http://localhost:8080/v1/accounting/ap/payments/{paymentId}` | Get payment details |
| GET | `http://localhost:8080/v1/accounting/ap/payments/by-ref/{paymentRef}` | Get payment by reference |
| GET | `http://localhost:8080/v1/accounting/ap/bills` | List eligible vendor bills |
| GET | `http://localhost:8080/v1/accounting/reports/financial/income-statement` | Generate Income Statement |
| GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/journal-lines/{accountId}` | Drilldown to Journal Lines |
| GET | `http://localhost:8080/v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}` | Drilldown to Accounts |
| GET | `http://localhost:8080/v1/accounting/reports/financial/balance-sheet` | Generate Balance Sheet |
| GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` | Get journal traceability |
| POST | `http://localhost:8080/v1/accounting/invoice/invoices` | Regenerate invoice from workorder |
| GET | `http://localhost:8080/v1/accounting/invoice/rules/{customerId}` | Get billing rules |

### Endpoint Details

#### GET <http://localhost:8080/v1/accounting/audit/actor/{actorId}>

**Operation ID:** `getByActor`

**Parameters:**

- `actorId` (path, Required, string):
- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: Audit entries retrieved successfully
- `400`: Invalid date range or actor ID
- `404`: Actor not found
- `500`: Internal server error

---

#### POST <http://localhost:8080/v1/accounting/audit/cancellation>

**Operation ID:** `recordCancellation`

**Responses:**

- `200`: Cancellation recorded successfully
- `400`: Invalid cancellation request
- `500`: Internal server error

---

#### GET <http://localhost:8080/v1/accounting/audit/invoice/{invoiceId}>

**Operation ID:** `getByInvoiceId`

**Parameters:**

- `invoiceId` (path, Required, string):

**Responses:**

- `200`: Audit entries retrieved successfully
- `400`: Invalid invoice ID
- `404`: Invoice not found
- `500`: Internal server error

---

#### GET <http://localhost:8080/v1/accounting/audit/order/{orderId}>

**Operation ID:** `getByOrderId`

**Parameters:**

- `orderId` (path, Required, string):

**Responses:**

- `200`: Audit entries retrieved successfully
- `400`: Invalid order ID
- `404`: Order not found
- `500`: Internal server error

---

#### POST <http://localhost:8080/v1/accounting/audit/price-override>

**Operation ID:** `recordPriceOverride`

**Responses:**

- `200`: Price override recorded successfully
- `400`: Invalid price override request
- `500`: Internal server error

---

#### GET <http://localhost:8080/v1/accounting/audit/range>

**Operation ID:** `getByDateRange`

**Parameters:**

- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: Audit entries retrieved successfully
- `400`: Invalid date range
- `500`: Internal server error

---

#### POST <http://localhost:8080/v1/accounting/audit/refund>

**Operation ID:** `recordRefund`

**Responses:**

- `200`: Refund recorded successfully
- `400`: Invalid refund request
- `500`: Internal server error

---

#### GET <http://localhost:8080/v1/accounting/audit/type/{type}>

**Operation ID:** `getByType`

**Parameters:**

- `type` (path, Required, string):
- `startDate` (query, Required, string):
- `endDate` (query, Required, string):

**Responses:**

- `200`: Audit entries retrieved successfully
- `400`: Invalid exception type or date range
- `500`: Internal server error

---

#### GET <http://localhost:8080/v1/accounting/credit-memos>

**Summary:** List credit memos

**Description:** Retrieve paginated credit memos with optional filters.

**Operation ID:** `listCreditMemos`

**Parameters:**

- `page` (query, Optional, integer): Page index (0-based, default: 0)
- `size` (query, Optional, integer): Page size (default: 20, max: 100)
- `customerId` (query, Optional, string (uuid)): Filter by customer
- `originalInvoiceId` (query, Optional, string (uuid)): Filter by original invoice
- `status` (query, Optional, string): Filter by status (DRAFT, POSTED, APPLIED, VOIDED)

**Responses:**

- `200`: Credit memos listed successfully
- `400`: Invalid query parameters
- `403`: Forbidden

---

#### POST <http://localhost:8080/v1/accounting/credit-memos>

**Summary:** Create credit memo

**Description:** Create a new Credit Memo to reverse invoice charges for returned goods, pricing errors, or service credits. The Credit Memo posts offsetting GL entries that debit Revenue/Tax and credit Accounts Receivable.

**Business Rules (CAP-052):**

- Original invoice must be in FINALIZED status
- Credit amount cannot exceed invoice outstanding balance
- Reason code is mandatory for audit trail
- GL entries must be balanced
- Prior period adjustments posted to current period with flag

**Operation ID:** `createCreditMemo`

**Request Body (application/json):**

```json
{
  "originalInvoiceId": "660e8400-e29b-41d4-a716-446655440000",
  "creditAmount": 100.00,
  "reasonCode": "RETURNED_GOODS",
  "justificationNote": "Customer returned item #12345 - full refund"
}
```

**Responses:**

- `201`: Credit memo created successfully
  - Response includes `creditMemoId`, `totalAmount`, `taxAmountReversed`, `invoiceBalanceAfter`
- `400`: Invalid request (missing reason code, negative amount)
- `404`: Original invoice not found
- `409`: Business rule violation (amount exceeds balance, invoice not finalized)
- `500`: Internal server error (GL posting failed)

---

#### GET <http://localhost:8080/v1/accounting/credit-memos/{creditMemoId}>

**Summary:** Get credit memo

**Description:** Retrieve details for a specific Credit Memo including GL posting status and traceability to original invoice.

**Operation ID:** `getCreditMemo`

**Parameters:**

- `creditMemoId` (path, Required, string (uuid)): Credit memo identifier

**Responses:**

- `200`: Credit memo returned
- `404`: Credit memo not found

---

#### GET <http://localhost:8080/v1/accounting/events>

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

#### POST <http://localhost:8080/v1/accounting/events>

**Summary:** Submit event

**Description:** Submit a new accounting event for processing.

**Operation ID:** `submitEvent`

**Responses:**

- `202`: Event accepted for processing
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/events/{eventId}>

**Summary:** Get event

**Description:** Retrieve details for an accounting event.

**Operation ID:** `getEvent`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `200`: Event returned
- `404`: Event not found

---

#### GET <http://localhost:8080/v1/accounting/events/{eventId}/processing-log>

**Summary:** Get event processing log

**Description:** Retrieve the processing log for an accounting event.

**Operation ID:** `getEventProcessingLog`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `200`: Processing log returned
- `404`: Event not found

---

#### POST <http://localhost:8080/v1/accounting/events/{eventId}/retry>

**Summary:** Retry event processing

**Description:** Retry processing for a failed accounting event.

**Operation ID:** `retryEventProcessing`

**Parameters:**

- `eventId` (path, Required, string): Event identifier

**Responses:**

- `202`: Retry scheduled
- `404`: Event not found

---

#### GET <http://localhost:8080/v1/accounting/gl-accounts>

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

#### POST <http://localhost:8080/v1/accounting/gl-accounts>

**Summary:** Create GL account

**Description:** Create a new GL account.

**Operation ID:** `createGLAccount`

**Responses:**

- `201`: GL account created
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}>

**Summary:** Get GL account

**Description:** Retrieve a GL account by identifier.

**Operation ID:** `getGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account returned
- `404`: GL account not found

---

#### PUT <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}>

**Summary:** Update GL account

**Description:** Update details for an existing GL account.

**Operation ID:** `updateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account updated
- `404`: GL account not found

---

#### POST <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/activate>

**Summary:** Activate GL account

**Description:** Mark a GL account as active.

**Operation ID:** `activateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account activated
- `404`: GL account not found

---

#### POST <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/archive>

**Summary:** Archive GL account

**Description:** Archive a GL account and remove it from active use.

**Operation ID:** `archiveGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account archived
- `404`: GL account not found

---

#### GET <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/balance>

**Summary:** Get GL account balance

**Description:** Retrieve the current balance for a GL account.

**Operation ID:** `getAccountBalance`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: Balance returned
- `404`: GL account not found

---

#### POST <http://localhost:8080/v1/accounting/gl-accounts/{glAccountId}/deactivate>

**Summary:** Deactivate GL account

**Description:** Mark a GL account as inactive.

**Operation ID:** `deactivateGLAccount`

**Parameters:**

- `glAccountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account deactivated
- `404`: GL account not found

---

#### POST <http://localhost:8080/v1/accounting/glAccounts>

**Summary:** Create GL account (legacy path)

**Description:** Create a GL account via the legacy endpoint.

**Operation ID:** `createGlAccount`

**Responses:**

- `201`: GL account created
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/glAccounts/{accountId}>

**Summary:** Get GL account (legacy path)

**Description:** Retrieve a GL account by identifier.

**Operation ID:** `getGlAccount`

**Parameters:**

- `accountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account returned
- `404`: GL account not found

---

#### PUT <http://localhost:8080/v1/accounting/glAccounts/{accountId}>

**Summary:** Update GL account (legacy path)

**Description:** Update a GL account via the legacy endpoint.

**Operation ID:** `manageGlAccount`

**Parameters:**

- `accountId` (path, Required, string): GL account identifier

**Responses:**

- `200`: GL account updated
- `404`: GL account not found

---

#### GET <http://localhost:8080/v1/accounting/invoice/{invoiceId}/status>

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

#### GET <http://localhost:8080/v1/accounting/journal-entries>

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

#### POST <http://localhost:8080/v1/accounting/journal-entries>

**Summary:** Create journal entry

**Description:** Create a new journal entry.

**Operation ID:** `createJournalEntry`

**Responses:**

- `201`: Journal entry created
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}>

**Summary:** Get journal entry

**Description:** Retrieve a journal entry by identifier.

**Operation ID:** `getJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry returned
- `404`: Journal entry not found

---

#### PUT <http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}>

**Summary:** Update journal entry

**Description:** Update an existing journal entry.

**Operation ID:** `updateJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry updated
- `404`: Journal entry not found

---

#### POST <http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/post>

**Summary:** Post journal entry

**Description:** Post a draft journal entry to the ledger.

**Operation ID:** `postJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry posted
- `404`: Journal entry not found

---

#### POST <http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}/reverse>

**Summary:** Reverse journal entry

**Description:** Reverse a posted journal entry.

**Operation ID:** `reverseJournalEntry`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Journal entry reversed
- `404`: Journal entry not found

---

#### POST <http://localhost:8080/v1/accounting/payments/{paymentId}/applications>

**Summary:** Apply payment

**Description:** Apply a payment to an invoice and update its status.

**Operation ID:** `applyPayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Request Body:**

- `application/json`: `PaymentApplicationRequest`

**Responses:**

- `201`: Payment applied successfully (`PaymentApplicationResponse`)
- `400`: Invalid request or insufficient funds (`PaymentApplicationResponse`)
- `404`: Payment not found (`PaymentApplicationResponse`)
- `409`: Currency mismatch or invoice not applicable (`PaymentApplicationResponse`)

---

#### POST <http://localhost:8080/v1/accounting/payments/{paymentId}/reverse>

**Summary:** Reverse payment

**Description:** Reverse a previously applied payment.

**Operation ID:** `reversePayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment reversed
- `404`: Payment not found

---

#### GET <http://localhost:8080/v1/accounting/payments/{paymentId}/status>

**Summary:** Get payment status

**Description:** Retrieve status for an accounts payable payment.

**Operation ID:** `getPaymentStatus`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment status returned
- `404`: Payment not found

---

#### POST <http://localhost:8080/v1/accounting/payments/{paymentId}/void>

**Summary:** Void payment

**Description:** Void a payment before settlement.

**Operation ID:** `voidPayment`

**Parameters:**

- `paymentId` (path, Required, string): Payment identifier

**Responses:**

- `200`: Payment voided
- `404`: Payment not found

---

#### GET <http://localhost:8080/v1/accounting/posting-rules>

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

#### POST <http://localhost:8080/v1/accounting/posting-rules>

**Summary:** Create posting rule set

**Description:** Create a new posting rule set.

**Operation ID:** `createPostingRuleSet`

**Responses:**

- `201`: Posting rule set created
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}>

**Summary:** Get posting rule set

**Description:** Retrieve a posting rule set by identifier.

**Operation ID:** `getPostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set returned
- `404`: Posting rule set not found

---

#### POST <http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/archive>

**Summary:** Archive posting rule set

**Description:** Archive a posting rule set.

**Operation ID:** `archivePostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set archived
- `404`: Posting rule set not found

---

#### POST <http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/publish>

**Summary:** Publish posting rule set

**Description:** Publish a posting rule set version.

**Operation ID:** `publishPostingRuleSet`

**Parameters:**

- `postingRuleSetId` (path, Required, string): Posting rule set identifier

**Responses:**

- `200`: Posting rule set published
- `404`: Posting rule set not found

---

#### GET <http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/versions>

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

#### GET <http://localhost:8080/v1/accounting/posting-categories>

**Summary:** List posting categories

**Description:** Retrieve paginated posting categories.

**Operation ID:** `listPostingCategories`

**Parameters:**

- `page` (query, Optional, integer (int32))
- `size` (query, Optional, integer (int32))
- `sort` (query, Optional, string)
- `isActive` (query, Optional, boolean)

**Responses:**

- `200`: Posting categories listed
- `403`: Forbidden

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/posting-categories` (optionally vary query parameters); assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/posting-categories>

**Summary:** Create posting category

**Description:** Create a new posting category.

**Operation ID:** `createPostingCategory`

**Request Body:**

- `application/json`: `PostingCategoryCreateRequest`

**Responses:**

- `201`: Posting category created
- `400`: Invalid request or duplicate name

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/posting-categories` with a valid `PostingCategoryCreateRequest` payload; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}>

**Summary:** Get posting category

**Description:** Retrieve a posting category by identifier.

**Operation ID:** `getPostingCategory`

**Parameters:**

- `postingCategoryId` (path, Required, string (uuid))

**Responses:**

- `200`: Posting category returned
- `404`: Posting category not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/posting-categories/{postingCategoryId}` with required parameters populated; assert status codes per OpenAPI.

---

#### PUT <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}>

**Summary:** Update posting category

**Description:** Update an existing posting category.

**Operation ID:** `updatePostingCategory`

**Parameters:**

- `postingCategoryId` (path, Required, string (uuid))

**Request Body:**

- `application/json`: `PostingCategoryUpdateRequest`

**Responses:**

- `200`: Posting category updated
- `400`: Invalid request or duplicate name
- `404`: Posting category not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `PUT /v1/accounting/posting-categories/{postingCategoryId}` with a valid `PostingCategoryUpdateRequest` payload; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/deactivate>

**Summary:** Deactivate posting category

**Description:** Deactivate a posting category.

**Operation ID:** `deactivatePostingCategory`

**Parameters:**

- `postingCategoryId` (path, Required, string (uuid))

**Responses:**

- `204`: Posting category deactivated
- `404`: Posting category not found
- `409`: Cannot deactivate - active mappings exist

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/posting-categories/{postingCategoryId}/deactivate` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/mapping-keys>

**Summary:** List mapping keys by category

**Description:** Retrieve paginated mapping keys for a posting category.

**Operation ID:** `listMappingKeysByCategory`

**Parameters:**

- `postingCategoryId` (path, Required, string (uuid))
- `page` (query, Optional, integer (int32))
- `size` (query, Optional, integer (int32))
- `sort` (query, Optional, string)
- `isActive` (query, Optional, boolean)

**Responses:**

- `200`: Mapping keys listed
- `403`: Forbidden
- `404`: Posting category not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/posting-categories/{postingCategoryId}/mapping-keys` with required parameters populated; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/mapping-keys>

**Summary:** Create mapping key

**Description:** Create a new mapping key for a posting category.

**Operation ID:** `createMappingKey`

**Request Body:**

- `application/json`: `MappingKeyCreateRequest`

**Responses:**

- `201`: Mapping key created
- `400`: Invalid request or duplicate name

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/mapping-keys` with a valid `MappingKeyCreateRequest` payload; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}>

**Summary:** Get mapping key

**Description:** Retrieve a mapping key by identifier.

**Operation ID:** `getMappingKey`

**Parameters:**

- `mappingKeyId` (path, Required, string (uuid))

**Responses:**

- `200`: Mapping key returned
- `404`: Mapping key not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/mapping-keys/{mappingKeyId}` with required parameters populated; assert status codes per OpenAPI.

---

#### PUT <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}>

**Summary:** Update mapping key

**Description:** Update an existing mapping key.

**Operation ID:** `updateMappingKey`

**Parameters:**

- `mappingKeyId` (path, Required, string (uuid))

**Request Body:**

- `application/json`: `MappingKeyUpdateRequest`

**Responses:**

- `200`: Mapping key updated
- `400`: Invalid request or duplicate name
- `404`: Mapping key not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `PUT /v1/accounting/mapping-keys/{mappingKeyId}` with a valid `MappingKeyUpdateRequest` payload; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}/deactivate>

**Summary:** Deactivate mapping key

**Description:** Deactivate a mapping key.

**Operation ID:** `deactivateMappingKey`

**Parameters:**

- `mappingKeyId` (path, Required, string (uuid))

**Responses:**

- `204`: Mapping key deactivated
- `404`: Mapping key not found
- `409`: Cannot deactivate - active mappings exist

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/mapping-keys/{mappingKeyId}/deactivate` with required parameters populated; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse>

**Summary:** Reverse payment application

**Description:** Reverse a payment application with compensating transaction (no deletion).

**Operation ID:** `reversePaymentApplication`

**Parameters:**

- `applicationId` (path, Required, string (uuid))

**Request Body:**

- `application/json`: `PaymentApplicationReversalRequest`

**Responses:**

- `204`: Payment application reversed
- `400`: Invalid request or already reversed
- `404`: Payment application not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/payment-applications/{applicationId}/reverse` with a valid `PaymentApplicationReversalRequest` payload; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/invoices/{invoiceId}/pay>

**Summary:** Apply payment (LEGACY)

**Description:** Apply a payment to an invoice (invoice-centric workflow). Use /payments/{paymentId}/applications for new API.

**Operation ID:** `applyPayment_1`

**Parameters:**

- `invoiceId` (path, Required, string (uuid))

**Request Body:**

- `application/json`: `PaymentAppliedRequest`

**Responses:**

- `200`: Payment applied
- `400`: Invalid payment request
- `500`: Processing error

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/invoices/{invoiceId}/pay` with a valid `PaymentAppliedRequest` payload; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/ap/bills>

**Summary:** List eligible vendor bills

**Description:** Get eligible vendor bills for payment (status = APPROVED). Bills are ordered by due date (oldest first, nulls last), then bill date, then bill ID.

**Operation ID:** `listBills`

**Parameters:**

- `vendorId` (query, Required, string (uuid))

**Responses:**

- `200`: Bills retrieved successfully
- `400`: Invalid vendor ID

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/ap/bills` with required parameters populated; assert status codes per OpenAPI.

---

#### POST <http://localhost:8080/v1/accounting/ap/payments>

**Summary:** Execute vendor payment

**Description:** Execute a vendor payment with optional explicit allocations to bills. Idempotent using paymentRef: same ref + same payload returns existing payment; same ref + different payload yields 409 conflict.

**Operation ID:** `executePayment`

**Request Body:**

- `application/json`: `ExecuteAPPaymentRequest`

**Responses:**

- `200`: Idempotent replay: existing payment returned
- `201`: Payment executed successfully (new payment created)
- `400`: Validation error: negative amounts, invalid bills, etc.
- `409`: Conflict: paymentRef exists with different payload
- `502`: Payment gateway failure

**Provider test hint (ContractBehaviorIT):**

- Exercise `POST /v1/accounting/ap/payments` with a valid `ExecuteAPPaymentRequest` payload; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/ap/payments/{paymentId}>

**Summary:** Get payment details

**Description:** Retrieve AP payment details including allocations and GL posting status.

**Operation ID:** `getPayment`

**Parameters:**

- `paymentId` (path, Required, string (uuid))

**Responses:**

- `200`: Payment found
- `404`: Payment not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/ap/payments/{paymentId}` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/ap/payments/by-ref/{paymentRef}>

**Summary:** Get payment by reference

**Description:** Retrieve AP payment details by paymentRef (idempotency key).

**Operation ID:** `getPaymentByRef`

**Parameters:**

- `paymentRef` (path, Required, string)

**Responses:**

- `200`: Payment found
- `404`: Payment not found

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/ap/payments/by-ref/{paymentRef}` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/reports/financial/income-statement>

**Summary:** Generate Income Statement

**Description:** Generate Profit & Loss report for a date range with revenue, expenses, and net income

**Operation ID:** `generateIncomeStatement`

**Parameters:**

- `startDate` (query, Required, string (date))
- `endDate` (query, Required, string (date))

**Responses:**

- `200`: Income statement generated successfully
- `400`: Invalid date range
- `401`: Unauthorized
- `403`: Forbidden - missing reporting:view:financial-statements

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/reports/financial/income-statement` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/reports/financial/drilldown/journal-lines/{accountId}>

**Summary:** Drilldown to Journal Lines

**Description:** Show source journal entries contributing to a GL account balance

**Operation ID:** `drilldownToJournalLines`

**Parameters:**

- `accountId` (path, Required, string)
- `startDate` (query, Required, string (date))
- `endDate` (query, Required, string (date))

**Responses:**

- `200`: Journal line drilldown successful
- `400`: Invalid account ID or date range
- `401`: Unauthorized
- `403`: Forbidden - missing reporting:view:financial-statements

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/reports/financial/drilldown/journal-lines/{accountId}` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}>

**Summary:** Drilldown to Accounts

**Description:** Show which GL accounts contribute to a specific statement line

**Operation ID:** `drilldownToAccounts`

**Parameters:**

- `statementLineCode` (path, Required, string)
- `startDate` (query, Required, string (date))
- `endDate` (query, Required, string (date))

**Responses:**

- `200`: Account drilldown successful
- `400`: Invalid statement line code or date range
- `401`: Unauthorized
- `403`: Forbidden - missing reporting:view:financial-statements

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/reports/financial/drilldown/accounts/{statementLineCode}` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/reports/financial/balance-sheet>

**Summary:** Generate Balance Sheet

**Description:** Generate Balance Sheet as of a specific date with assets, liabilities, and equity

**Operation ID:** `generateBalanceSheet`

**Parameters:**

- `asOfDate` (query, Required, string (date))

**Responses:**

- `200`: Balance sheet generated successfully
- `400`: Invalid date
- `401`: Unauthorized
- `403`: Forbidden - missing reporting:view:financial-statements

**Provider test hint (ContractBehaviorIT):**

- Exercise `GET /v1/accounting/reports/financial/balance-sheet` with required parameters populated; assert status codes per OpenAPI.

---

#### GET <http://localhost:8080/v1/accounting/traceability/{journalEntryId}>

**Summary:** Get journal traceability

**Description:** Trace a journal entry across related records.

**Operation ID:** `getJournalTraceability`

**Parameters:**

- `journalEntryId` (path, Required, string): Journal entry identifier

**Responses:**

- `200`: Traceability returned
- `404`: Journal entry not found

---

#### POST <http://localhost:8080/v1/accounting/invoice/invoices>

**Summary:** Regenerate invoice from workorder

**Description:** Regenerate an invoice from a workorder.

**Operation ID:** `regenerateInvoiceFromWorkorder`

**Responses:**

- `202`: Invoice regeneration accepted
- `400`: Invalid request

---

#### GET <http://localhost:8080/v1/accounting/invoice/rules/{customerId}>

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
| ------- | ------ | ---------- | ------------- |
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
| ------- | ------ | ---------- | ------------- |
| `actorId` | string (uuid) | Yes |  |
| `actorRole` | string | Yes |  |
| `afterSnapshot` | string | Yes |  |
| `beforeSnapshot` | string | Yes |  |
| `cancellationType` | string | Yes |  |
| `invoiceId` | string (uuid) | No |  |
| `orderId` | string (uuid) | No |  |
| `partialPaymentInfo` | string | No |  |
| `reason` | string | Yes |  |

### CreateCreditMemoRequest

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
| `originalInvoiceId` | string (uuid) | Yes | Invoice to credit (must be finalized) |
| `creditAmount` | number | Yes | Amount to credit (positive, max 15 digits, 4 decimals) |
| `reasonCode` | string | Yes | Reason code (1-50 chars, e.g., RETURNED_GOODS, PRICING_ERROR, SERVICE_CREDIT) |
| `justificationNote` | string | No | Optional justification (max 1000 chars) |

### CreditMemoResponse

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
| `creditMemoId` | string (uuid) | Yes | Credit memo identifier |
| `originalInvoiceId` | string (uuid) | Yes | Referenced invoice |
| `customerId` | string (uuid) | Yes | Customer identifier |
| `creditAmount` | number | Yes | Revenue amount credited |
| `taxAmountReversed` | number | Yes | Tax amount reversed |
| `totalAmount` | number | Yes | Total credit (creditAmount + taxAmountReversed) |
| `reasonCode` | string | Yes | Reason code |
| `justificationNote` | string | No | Optional justification |
| `status` | string | Yes | Status (DRAFT, POSTED, APPLIED, VOIDED) |
| `creationTimestamp` | string (ISO 8601) | Yes | Creation timestamp |
| `postedTimestamp` | string (ISO 8601) | No | Posted timestamp (if status=POSTED) |
| `createdByUserId` | string | Yes | User who created the credit memo |
| `priorPeriodAdjustment` | boolean | Yes | True if CM for closed-period invoice |
| `originalPeriodId` | string | No | Original invoice's accounting period |
| `currency` | string | Yes | Currency code (ISO 4217, e.g., USD) |
| `invoiceBalanceAfter` | number | No | Invoice balance after credit applied |

### InvoiceStatusResponse

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
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
| ------- | ------ | ---------- | ------------- |
| `idempotencyKey` | string | Yes |  |
| `invoiceId` | string | Yes |  |
| `invoiceTotal` | number | Yes |  |
| `paymentAmount` | number | Yes |  |
| `paymentFailed` | boolean | No |  |
| `transactionReference` | string | Yes |  |

### PriceOverrideRequest

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
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
| ------- | ------ | ---------- | ------------- |
| `actorId` | string (uuid) | Yes |  |
| `actorRole` | string | Yes |  |
| `invoiceId` | string (uuid) | Yes |  |
| `originalPaymentStatus` | string | Yes |  |
| `paymentId` | string (uuid) | Yes |  |
| `reason` | string | Yes |  |
| `refundAmount` | number | Yes |  |
| `refundType` | string | Yes |  |

### PaymentApplicationRequest

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
| `applicationRequestId` | string (uuid) | Yes | Idempotency key for payment application |
| `applications` | array | Yes | List of invoice applications |
| `applications[].invoiceId` | string (uuid) | Yes | Invoice to apply payment to |
| `applications[].amountToApply` | number | Yes | Amount to apply to this invoice |

### PaymentApplicationResponse

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
| `paymentId` | string (uuid) | Yes | Payment identifier |
| `customerId` | string (uuid) | Yes | Customer identifier |
| `remainingAmount` | number | Yes | Remaining unapplied payment amount |
| `totalApplied` | number | Yes | Total amount applied to invoices |
| `appliedInvoices` | array | Yes | List of applied invoice details |
| `appliedInvoices[].paymentApplicationId` | string (uuid) | Yes | Application record ID |
| `appliedInvoices[].invoiceId` | string (uuid) | Yes | Invoice ID |
| `appliedInvoices[].appliedAmount` | number | Yes | Amount applied |
| `appliedInvoices[].appliedAt` | string (ISO 8601) | Yes | Application timestamp |
| `credit` | object | No | Customer credit if overpayment |
| `credit.creditId` | string (uuid) | Yes | Credit record ID |
| `credit.amount` | number | Yes | Credit amount |
| `credit.createdAt` | string (ISO 8601) | Yes | Credit creation timestamp |

### PaymentApplicationReversalRequest

**Fields:**

| Field | Type | Required | Description |
| ------- | ------ | ---------- | ------------- |
| `reason` | string | Yes | Reason for reversal (min 10 chars, max 500 chars) |

---

## Examples

### Example Request/Response Pairs

#### Example: Create Request

```http
POST http://localhost:8080/v1/accounting/invoice/invoices
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
GET http://localhost:8080/v1/accounting/journal-entries/{journalEntryId}
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

#### Example: Apply Payment to Invoices

**Request:**

```http
POST http://localhost:8080/v1/accounting/payments/123e4567-e89b-12d3-a456-426614174000/applications
Content-Type: application/json
X-Correlation-Id: payment-app-001

{
  "applicationRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "applications": [
    {
      "invoiceId": "660e8400-e29b-41d4-a716-446655440000",
      "amountToApply": 100.00
    },
    {
      "invoiceId": "770e8400-e29b-41d4-a716-446655440000",
      "amountToApply": 50.00
    }
  ]
}
```

**Response (201 Created):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: payment-app-001

{
  "paymentId": "123e4567-e89b-12d3-a456-426614174000",
  "customerId": "234e5678-e89b-12d3-a456-426614174000",
  "remainingAmount": 0.00,
  "totalApplied": 150.00,
  "appliedInvoices": [
    {
      "paymentApplicationId": "789e0123-e89b-12d3-a456-426614174000",
      "invoiceId": "660e8400-e29b-41d4-a716-446655440000",
      "appliedAmount": 100.00,
      "appliedAt": "2025-01-13T10:30:00Z"
    },
    {
      "paymentApplicationId": "890e1234-e89b-12d3-a456-426614174000",
      "invoiceId": "770e8400-e29b-41d4-a716-446655440000",
      "appliedAmount": 50.00,
      "appliedAt": "2025-01-13T10:30:00Z"
    }
  ],
  "credit": null
}
```

**Response (201 Created with overpayment):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: payment-app-002

{
  "paymentId": "123e4567-e89b-12d3-a456-426614174000",
  "customerId": "234e5678-e89b-12d3-a456-426614174000",
  "remainingAmount": 25.00,
  "totalApplied": 125.00,
  "appliedInvoices": [
    {
      "paymentApplicationId": "789e0123-e89b-12d3-a456-426614174000",
      "invoiceId": "660e8400-e29b-41d4-a716-446655440000",
      "appliedAmount": 125.00,
      "appliedAt": "2025-01-13T10:30:00Z"
    }
  ],
  "credit": {
    "creditId": "345e6789-e89b-12d3-a456-426614174000",
    "amount": 25.00,
    "createdAt": "2025-01-13T10:30:00Z"
  }
}
```

#### Example: Reverse Payment Application

**Request:**

```http
POST http://localhost:8080/v1/accounting/payment-applications/789e0123-e89b-12d3-a456-426614174000/reverse
Content-Type: application/json
X-Correlation-Id: reversal-001

{
  "reason": "Customer disputed charge, refund issued"
}
```

**Response (204 No Content):**

```http
HTTP/1.1 204 No Content
X-Correlation-Id: reversal-001
```

---

#### Example: Create Credit Memo (Full Credit)

**Request:**

```http
POST http://localhost:8080/v1/accounting/credit-memos
Content-Type: application/json
X-Correlation-Id: credit-memo-001

{
  "originalInvoiceId": "inv-123e4567-e89b-12d3-a456-426614174000",
  "creditAmount": 100.00,
  "reasonCode": "RETURNED_GOODS",
  "justificationNote": "Customer returned Item #12345, full refund authorized by manager"
}
```

**Response (201 Created):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: credit-memo-001

{
  "creditMemoId": "cm-789e0123-e89b-12d3-a456-426614174000",
  "originalInvoiceId": "inv-123e4567-e89b-12d3-a456-426614174000",
  "customerId": "cust-234e5678-e89b-12d3-a456-426614174000",
  "creditAmount": 100.00,
  "taxAmountReversed": 10.00,
  "totalAmount": 110.00,
  "reasonCode": "RETURNED_GOODS",
  "justificationNote": "Customer returned Item #12345, full refund authorized by manager",
  "status": "POSTED",
  "creationTimestamp": "2026-02-08T14:30:00.123Z",
  "postedTimestamp": "2026-02-08T14:30:00.123Z",
  "createdByUserId": "user-456",
  "priorPeriodAdjustment": false,
  "originalPeriodId": null,
  "currency": "USD",
  "invoiceBalanceAfter": 0.00
}
```

**Response (409 Conflict - Amount Exceeds Balance):**

```http
HTTP/1.1 409 Conflict
X-Correlation-Id: credit-memo-002

{
  "code": "CREDIT_AMOUNT_EXCEEDS_BALANCE",
  "message": "Credit amount $150.00 exceeds invoice outstanding balance $100.00",
  "correlationId": "credit-memo-002",
  "timestamp": "2026-02-08T14:30:00Z"
}
```

**Response (400 Bad Request - Missing Reason Code):**

```http
HTTP/1.1 400 Bad Request
X-Correlation-Id: credit-memo-003

{
  "code": "VALIDATION_ERROR",
  "message": "Reason code is required",
  "correlationId": "credit-memo-003",
  "timestamp": "2026-02-08T14:30:00Z",
  "fieldErrors": [
    {
      "field": "reasonCode",
      "message": "Reason code is required",
      "rejectedValue": null
    }
  ]
}
```

---

#### Example: Create Credit Memo (Partial Credit, Prior Period)

**Request:**

```http
POST http://localhost:8080/v1/accounting/credit-memos
Content-Type: application/json
X-Correlation-Id: credit-memo-004

{
  "originalInvoiceId": "inv-old-2025-q4-invoice",
  "creditAmount": 50.00,
  "reasonCode": "PRICING_ERROR",
  "justificationNote": "Overcharged on service hours, partial credit approved"
}
```

**Response (201 Created with Prior Period Adjustment):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: credit-memo-004

{
  "creditMemoId": "cm-890e1234-e89b-12d3-a456-426614174000",
  "originalInvoiceId": "inv-old-2025-q4-invoice",
  "customerId": "cust-234e5678-e89b-12d3-a456-426614174000",
  "creditAmount": 50.00,
  "taxAmountReversed": 5.00,
  "totalAmount": 55.00,
  "reasonCode": "PRICING_ERROR",
  "justificationNote": "Overcharged on service hours, partial credit approved",
  "status": "POSTED",
  "creationTimestamp": "2026-02-08T14:30:00.123Z",
  "postedTimestamp": "2026-02-08T14:30:00.123Z",
  "createdByUserId": "user-456",
  "priorPeriodAdjustment": true,
  "originalPeriodId": "Q4-2025",
  "currency": "USD",
  "invoiceBalanceAfter": 55.00
}
```

**Note:** The `priorPeriodAdjustment: true` flag indicates the original invoice was from a closed accounting period (Q4-2025), so the GL entries are posted to the current open period (Q1-2026) but flagged as prior period adjustments for reporting purposes.

---

---

#### Example: Execute AP Payment with Automatic Allocation

**Request:**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-001

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": 600.00,
  "feeAmount": 5.00,
  "netAmount": 595.00,
  "currency": "USD",
  "paymentRef": "pay-789e0123-e89b-12d3-a456-426614174000",
  "paymentMethod": "ACH",
  "memo": "Monthly vendor payment - automatic allocation",
  "allocations": []
}
```

**Response (201 Created):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: ap-payment-001

{
  "paymentId": "pay-890e1234-e89b-12d3-a456-426614174000",
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "paymentRef": "pay-789e0123-e89b-12d3-a456-426614174000",
  "grossAmount": 600.00,
  "feeAmount": 5.00,
  "netAmount": 595.00,
  "unappliedAmount": 0.00,
  "currency": "USD",
  "paymentMethod": "ACH",
  "status": "GATEWAY_SUCCEEDED",
  "gatewayTransactionId": "gw-txn-12345",
  "gatewayTimestamp": "2026-02-08T14:30:00.123Z",
  "glJournalEntryId": null,
  "glPostedAt": null,
  "glPostError": null,
  "memo": "Monthly vendor payment - automatic allocation",
  "createdAt": "2026-02-08T14:30:00.123Z",
  "updatedAt": "2026-02-08T14:30:00.123Z",
  "allocations": [
    {
      "allocationId": "alloc-234e5678-e89b-12d3-a456-426614174000",
      "vendorBillId": "bill-345e6789-e89b-12d3-a456-426614174000",
      "billNumber": "BILL-001",
      "appliedAmount": 500.00,
      "allocationSequence": 0
    },
    {
      "allocationId": "alloc-456e7890-e89b-12d3-a456-426614174000",
      "vendorBillId": "bill-567e8901-e89b-12d3-a456-426614174000",
      "billNumber": "BILL-002",
      "appliedAmount": 100.00,
      "allocationSequence": 1
    }
  ]
}
```

**Note:** Empty `allocations` array triggers automatic allocation logic (oldest due first per BR-4). Payment allocates $500 to BILL-001 (oldest due) and $100 to BILL-002, leaving no unapplied amount.

---

#### Example: Execute AP Payment with Explicit Allocations

**Request:**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-002

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": 400.00,
  "feeAmount": 5.00,
  "netAmount": 395.00,
  "currency": "USD",
  "paymentRef": "pay-abc12345-e89b-12d3-a456-426614174000",
  "paymentMethod": "WIRE",
  "memo": "Payment for BILL-002 only",
  "allocations": [
    {
      "vendorBillId": "bill-567e8901-e89b-12d3-a456-426614174000",
      "appliedAmount": 300.00
    }
  ]
}
```

**Response (201 Created with Unapplied Amount):**

```http
HTTP/1.1 201 Created
X-Correlation-Id: ap-payment-002

{
  "paymentId": "pay-def67890-e89b-12d3-a456-426614174000",
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "paymentRef": "pay-abc12345-e89b-12d3-a456-426614174000",
  "grossAmount": 400.00,
  "feeAmount": 5.00,
  "netAmount": 395.00,
  "unappliedAmount": 100.00,
  "currency": "USD",
  "paymentMethod": "WIRE",
  "status": "GATEWAY_SUCCEEDED",
  "gatewayTransactionId": "gw-txn-67890",
  "gatewayTimestamp": "2026-02-08T14:35:00.123Z",
  "glJournalEntryId": null,
  "glPostedAt": null,
  "glPostError": null,
  "memo": "Payment for BILL-002 only",
  "createdAt": "2026-02-08T14:35:00.123Z",
  "updatedAt": "2026-02-08T14:35:00.123Z",
  "allocations": [
    {
      "allocationId": "alloc-678e9012-e89b-12d3-a456-426614174000",
      "vendorBillId": "bill-567e8901-e89b-12d3-a456-426614174000",
      "billNumber": "BILL-002",
      "appliedAmount": 300.00,
      "allocationSequence": 0
    }
  ]
}
```

**Note:** Explicit allocation to BILL-002 only ($300) leaves $100 unapplied, which becomes vendor credit.

---

#### Example: Idempotent Payment Execution (Same paymentRef Returns Existing)

**Request (retrying same paymentRef with same payload):**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-003

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": 200.00,
  "feeAmount": 0.00,
  "netAmount": 200.00,
  "currency": "USD",
  "paymentRef": "pay-idempotent-001",
  "paymentMethod": "CHECK",
  "memo": "Idempotency test",
  "allocations": []
}
```

**Response (200 OK - existing payment returned):**

```http
HTTP/1.1 200 OK
X-Correlation-Id: ap-payment-003

{
  "paymentId": "pay-existing-12345",
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "paymentRef": "pay-idempotent-001",
  "grossAmount": 200.00,
  "feeAmount": 0.00,
  "netAmount": 200.00,
  "unappliedAmount": 0.00,
  "currency": "USD",
  "paymentMethod": "CHECK",
  "status": "GATEWAY_SUCCEEDED",
  "gatewayTransactionId": "gw-txn-existing",
  "gatewayTimestamp": "2026-02-08T13:00:00.123Z",
  "glJournalEntryId": null,
  "glPostedAt": null,
  "glPostError": null,
  "memo": "Idempotency test",
  "createdAt": "2026-02-08T13:00:00.123Z",
  "updatedAt": "2026-02-08T13:00:00.123Z",
  "allocations": []
}
```

**Note:** Same `paymentRef` with same payload returns existing payment (200 OK). No duplicate payment created.

---

#### Example: Idempotency Conflict (Same paymentRef, Different Payload)

**Request:**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-004

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": 300.00,
  "feeAmount": 0.00,
  "netAmount": 300.00,
  "currency": "USD",
  "paymentRef": "pay-idempotent-001",
  "paymentMethod": "ACH",
  "memo": "Different payload",
  "allocations": []
}
```

**Response (409 Conflict):**

```http
HTTP/1.1 409 Conflict
X-Correlation-Id: ap-payment-004

{
  "code": "PAYMENT_IDEMPOTENCY_CONFLICT",
  "message": "Payment with paymentRef 'pay-idempotent-001' already exists with different payload",
  "correlationId": "ap-payment-004",
  "timestamp": "2026-02-08T14:40:00Z"
}
```

**Note:** Same `paymentRef` with different payload (e.g., different grossAmount) returns 409 Conflict.

---

#### Example: Validation Error (Negative Amount)

**Request:**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-005

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": -100.00,
  "feeAmount": 0.00,
  "netAmount": -100.00,
  "currency": "USD",
  "paymentRef": "pay-invalid-001",
  "paymentMethod": "ACH",
  "allocations": []
}
```

**Response (400 Bad Request):**

```http
HTTP/1.1 400 Bad Request
X-Correlation-Id: ap-payment-005

{
  "code": "VALIDATION_ERROR",
  "message": "Gross amount must be positive",
  "correlationId": "ap-payment-005",
  "timestamp": "2026-02-08T14:45:00Z",
  "fieldErrors": [
    {
      "field": "grossAmount",
      "message": "must be greater than or equal to 0.01",
      "rejectedValue": -100.00
    }
  ]
}
```

---

#### Example: Validation Error (Allocations Exceed Gross Amount)

**Request:**

```http
POST http://localhost:8080/v1/accounting/ap/payments
Content-Type: application/json
X-Correlation-Id: ap-payment-006

{
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "grossAmount": 100.00,
  "feeAmount": 0.00,
  "netAmount": 100.00,
  "currency": "USD",
  "paymentRef": "pay-invalid-002",
  "paymentMethod": "WIRE",
  "allocations": [
    {
      "vendorBillId": "bill-123e4567-e89b-12d3-a456-426614174000",
      "appliedAmount": 600.00
    }
  ]
}
```

**Response (400 Bad Request):**

```http
HTTP/1.1 400 Bad Request
X-Correlation-Id: ap-payment-006

{
  "code": "ALLOCATION_EXCEEDS_GROSS",
  "message": "Total allocations ($600.00) exceed gross amount ($100.00)",
  "correlationId": "ap-payment-006",
  "timestamp": "2026-02-08T14:50:00Z"
}
```

---

#### Example: List Eligible Vendor Bills

**Request:**

```http
GET http://localhost:8080/v1/accounting/ap/bills?vendorId=vendor-123e4567-e89b-12d3-a456-426614174000
X-Correlation-Id: ap-bills-001
```

**Response (200 OK):**

```http
HTTP/1.1 200 OK
X-Correlation-Id: ap-bills-001

[
  {
    "vendorBillId": "bill-345e6789-e89b-12d3-a456-426614174000",
    "billNumber": "BILL-001",
    "billDate": "2026-01-10",
    "dueDate": "2026-01-30",
    "totalAmount": 500.00,
    "openAmount": 500.00,
    "status": "APPROVED",
    "currency": "USD"
  },
  {
    "vendorBillId": "bill-567e8901-e89b-12d3-a456-426614174000",
    "billNumber": "BILL-002",
    "billDate": "2026-02-01",
    "dueDate": "2026-02-23",
    "totalAmount": 300.00,
    "openAmount": 300.00,
    "status": "APPROVED",
    "currency": "USD"
  }
]
```

**Note:** Bills are ordered by due date (oldest first, nulls last), then bill date, then bill ID per BR-4.

---

#### Example: Get Payment by ID

**Request:**

```http
GET http://localhost:8080/v1/accounting/ap/payments/pay-890e1234-e89b-12d3-a456-426614174000
X-Correlation-Id: ap-get-001
```

**Response (200 OK):**

```http
HTTP/1.1 200 OK
X-Correlation-Id: ap-get-001

{
  "paymentId": "pay-890e1234-e89b-12d3-a456-426614174000",
  "vendorId": "vendor-123e4567-e89b-12d3-a456-426614174000",
  "paymentRef": "pay-789e0123-e89b-12d3-a456-426614174000",
  "grossAmount": 600.00,
  "feeAmount": 5.00,
  "netAmount": 595.00,
  "unappliedAmount": 0.00,
  "currency": "USD",
  "paymentMethod": "ACH",
  "status": "GL_POSTED",
  "gatewayTransactionId": "gw-txn-12345",
  "gatewayTimestamp": "2026-02-08T14:30:00.123Z",
  "glJournalEntryId": "je-001",
  "glPostedAt": "2026-02-08T14:31:00.456Z",
  "glPostError": null,
  "memo": "Monthly vendor payment - automatic allocation",
  "createdAt": "2026-02-08T14:30:00.123Z",
  "updatedAt": "2026-02-08T14:31:00.456Z",
  "allocations": [
    {
      "allocationId": "alloc-234e5678-e89b-12d3-a456-426614174000",
      "vendorBillId": "bill-345e6789-e89b-12d3-a456-426614174000",
      "billNumber": "BILL-001",
      "appliedAmount": 500.00,
      "allocationSequence": 0
    },
    {
      "allocationId": "alloc-456e7890-e89b-12d3-a456-426614174000",
      "vendorBillId": "bill-567e8901-e89b-12d3-a456-426614174000",
      "billNumber": "BILL-002",
      "appliedAmount": 100.00,
      "allocationSequence": 1
    }
  ]
}
```

**Note:** Payment is fully posted to GL (`status: GL_POSTED`, `glPostedAt` populated).

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
| --------- | ------ | --------- |
| 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |
| 1.1 | 2026-02-08 | Added Credit Memo endpoints and contracts (CAP-052) |
| 1.2 | 2026-02-08 | Added AP Payment endpoints and contracts (CAP-053) |
| 1.3 | 2026-02-09 | Synced Endpoint Summary with OpenAPI (AP endpoints) + added CAP-054 coordination links |
| 1.4 | 2026-02-10 | Normalized gateway URL paths (reports + invoice routes) + added CAP-278 coordination links |

---

## Implementation Links

### CAP-052: Accounts Receivable (Invoice → Cash Application)

**Parent Issue:** [durion#52](https://github.com/louisburroughs/durion/issues/52)  
**Backend Implementation:** [durion-positivity-backend#131](https://github.com/louisburroughs/durion-positivity-backend/issues/131)  
**Frontend Implementation:** [durion-moqui-frontend#195](https://github.com/louisburroughs/durion-moqui-frontend/issues/195)

**Endpoints Added:**

- `POST /v1/accounting/credit-memos` - Create Credit Memo
- `GET /v1/accounting/credit-memos` - List Credit Memos
- `GET /v1/accounting/credit-memos/{creditMemoId}` - Get Credit Memo

**Scope:** Credit Memo creation for AR corrections (returned goods, pricing errors, service credits). Posts reversing GL entries (debit revenue/tax, credit AR). Handles prior period adjustments. Does NOT include cash refund execution (separate Payment capability).

---

### CAP-053: Accounts Payable (Bill → Payment)

**Parent Issue:** [durion#53](https://github.com/louisburroughs/durion/issues/53)  
**Backend Implementation:** [durion-positivity-backend#128](https://github.com/louisburroughs/durion-positivity-backend/issues/128)

**Endpoints Added:**

- `POST /v1/accounting/ap/payments` - Execute AP Payment
- `GET /v1/accounting/ap/payments/{paymentId}` - Get Payment by ID
- `GET /v1/accounting/ap/payments/by-ref/{paymentRef}` - Get Payment by Reference
- `GET /v1/accounting/ap/bills?vendorId={vendorId}` - List Eligible Vendor Bills

**Scope:** AP payment execution with gateway integration and asynchronous GL posting. Supports idempotency via `paymentRef` (idempotency key), automatic allocation (oldest due first per BR-4) or explicit allocations, two-phase completion (gateway success = payment complete, GL posted = accounting complete), unapplied remainder tracking as vendor credit.

**Key Patterns:**

- **Idempotency:** Same `paymentRef` + same payload = return existing payment (200 OK). Same `paymentRef` + different payload = 409 Conflict.
- **Two-Phase Completion:** `status: GATEWAY_SUCCEEDED` (gateway complete, payment confirmed) → async event → `status: GL_POSTED` (accounting complete, GL entries recorded).
- **Allocation Logic (BR-4):** Empty `allocations[]` triggers automatic allocation (oldest due first, nulls last ordering). Explicit `allocations` validated: non-negative, sum ≤ gross, bills must be APPROVED status.
- **Unapplied Amount:** `unappliedAmount = grossAmount - sum(allocations)` becomes vendor credit for future bill payments.

---

### CAP-054: Period Close, Adjustments, and Reporting

**Parent Issue:** [durion#54](https://github.com/louisburroughs/durion/issues/54)  
**Backend Implementation:** [durion-positivity-backend#125](https://github.com/louisburroughs/durion-positivity-backend/issues/125)  
**Frontend Implementation:** [durion-moqui-frontend#123](https://github.com/louisburroughs/durion-moqui-frontend/issues/123)

**Contract Note:**

- No CAP-054-specific Accounting OpenAPI paths detected in `pos-accounting/openapi.json` as of 2026-02-09; this section tracks implementation coordination.

---

### CAP-278: Posting Rule Engine

**Parent Issue:** [durion#278](https://github.com/louisburroughs/durion/issues/278)  
**Backend Implementation:**

- [durion-positivity-backend#472](https://github.com/louisburroughs/durion-positivity-backend/issues/472)
- [durion-positivity-backend#473](https://github.com/louisburroughs/durion-positivity-backend/issues/473)
- [durion-positivity-backend#474](https://github.com/louisburroughs/durion-positivity-backend/issues/474)
- [durion-positivity-backend#475](https://github.com/louisburroughs/durion-positivity-backend/issues/475)
- [durion-positivity-backend#476](https://github.com/louisburroughs/durion-positivity-backend/issues/476)
- [durion-positivity-backend#477](https://github.com/louisburroughs/durion-positivity-backend/issues/477)
- [durion-positivity-backend#478](https://github.com/louisburroughs/durion-positivity-backend/issues/478)

**Contract Note:**

- Coordination links only. Any API changes introduced by CAP-278 must be reflected in `pos-accounting/openapi.json` and then synced into this guide.

## References

- OpenAPI Specification: `pos-accounting/openapi.json`
- Domain Agent Guide: `domains/accounting/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/accounting/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/accounting/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** 2026-02-10 00:00:00 UTC  
**Tool:** `scripts/generate_backend_contract_guides.py`

---

## Capability Contract Template

Use the shared template for capability sections:

- `domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md`
