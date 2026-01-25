# Accounting Domain Error Codes

**Version:** 1.0  
**Purpose:** Comprehensive error taxonomy for Accounting domain API responses  
**Domain:** accounting  
**Owner:** Accounting Domain  
**Status:** ACCEPTED  
**Date:** 2026-01-25

---

## Overview

This document defines the complete error code taxonomy for the Accounting domain. All backend services and frontend wrappers **MUST** use these error codes consistently for predictable error handling and user messaging.

Error codes follow the format: `{RESOURCE}_{ERROR_TYPE}` (e.g., `MAPPING_NOT_FOUND`, `JE_UNBALANCED`).

---

## Error Response Format

### Standard Error Response

```json
{
  "errorCode": "MAPPING_NOT_FOUND",
  "message": "No active GL mapping found for posting category PC-001 and mapping key MK-001",
  "path": "/v1/accounting/gl-mappings/resolve",
  "timestamp": "2026-01-25T14:30:00Z",
  "httpStatus": 404,
  "details": {
    "postingCategoryId": "PC-001",
    "mappingKeyId": "MK-001",
    "transactionDate": "2026-01-25"
  }
}
```

### HTTP Status Code Mapping

| HTTP Status | Use Case | Example |
|-------------|----------|---------|
| **400** | Bad Request (client error, malformed input) | INVALID_DATE_FORMAT, MISSING_REQUIRED_FIELD |
| **401** | Unauthorized (missing or invalid authentication) | AUTHENTICATION_REQUIRED |
| **403** | Forbidden (authenticated but insufficient permissions) | PERMISSION_DENIED |
| **404** | Not Found (resource doesn't exist) | ACCOUNT_NOT_FOUND, MAPPING_NOT_FOUND |
| **409** | Conflict (duplicate or constraint violation) | DUPLICATE_ACCOUNT_CODE, MAPPING_OVERLAP, JE_ALREADY_POSTED |
| **422** | Unprocessable Entity (semantic validation error) | JE_UNBALANCED, UNBALANCED_RULES |
| **500** | Internal Server Error (unexpected backend failure) | DATABASE_ERROR, EXTERNAL_SERVICE_ERROR |
| **503** | Service Unavailable (temporary outage) | SERVICE_UNAVAILABLE |

---

## Chart of Accounts (CoA) Errors

### ACCOUNT_NOT_FOUND (404)
**Description:** GL account does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch account by ID that doesn't exist
- Reference non-existent account in mapping or JE line

**Example:**
```json
{
  "errorCode": "ACCOUNT_NOT_FOUND",
  "message": "GL account 'GL-12345' does not exist",
  "details": {
    "glAccountId": "GL-12345"
  }
}
```

**Recovery:** Verify account ID; create account if needed

---

### DUPLICATE_ACCOUNT_CODE (409)
**Description:** Account code already exists (uniqueness violation)  
**HTTP Status:** 409  
**Use Cases:**
- Create account with code that already exists
- Update account code to match existing code

**Example:**
```json
{
  "errorCode": "DUPLICATE_ACCOUNT_CODE",
  "message": "Account code '1000-000' already exists",
  "details": {
    "accountCode": "1000-000",
    "existingAccountId": "GL-existing-67890"
  }
}
```

**Recovery:** Choose different account code

---

### ACCOUNT_IMMUTABLE (409)
**Description:** Account cannot be modified (immutability policy)  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to edit inactive account
- Attempt to change account type after posting

**Example:**
```json
{
  "errorCode": "ACCOUNT_IMMUTABLE",
  "message": "Account 'GL-12345' is inactive and cannot be modified",
  "details": {
    "glAccountId": "GL-12345",
    "status": "INACTIVE",
    "deactivatedAt": "2025-12-31"
  }
}
```

**Recovery:** Create new account or contact administrator

---

### ACCOUNT_HAS_BALANCE (409)
**Description:** Account has non-zero balance, cannot deactivate  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to deactivate account with outstanding balance
- Cannot delete account with transaction history

**Example:**
```json
{
  "errorCode": "ACCOUNT_HAS_BALANCE",
  "message": "Account 'GL-12345' has non-zero balance and cannot be deactivated",
  "details": {
    "glAccountId": "GL-12345",
    "currentBalance": "5000.00"
  }
}
```

**Recovery:** Zero out balance before deactivation

---

## GL Mapping Errors (Issue #203)

### MAPPING_NOT_FOUND (404)
**Description:** No active GL mapping found for criteria  
**HTTP Status:** 404  
**Use Cases:**
- Resolution test fails (no mapping for key + date)
- JE generation fails (no mapping for posting category)

**Example:**
```json
{
  "errorCode": "MAPPING_NOT_FOUND",
  "message": "No active GL mapping found for posting category 'PC-001' and mapping key 'MK-001' on date 2026-01-25",
  "details": {
    "postingCategoryId": "PC-001",
    "mappingKeyId": "MK-001",
    "transactionDate": "2026-01-25"
  }
}
```

**Recovery:** Create GL mapping with effective date range covering transaction date

---

### MAPPING_OVERLAP (409)
**Description:** GL mapping overlaps with existing mapping  
**HTTP Status:** 409  
**Use Cases:**
- Create mapping with effective dates that overlap existing mapping
- Effective-date conflict for same (postingCategoryId, mappingKeyId)

**Example:**
```json
{
  "errorCode": "MAPPING_OVERLAP",
  "message": "GL mapping overlaps with existing mapping for same category and key",
  "details": {
    "postingCategoryId": "PC-001",
    "mappingKeyId": "MK-001",
    "requestedEffectiveFrom": "2026-01-01",
    "requestedEffectiveTo": "2026-12-31",
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

**Recovery:** Adjust effective dates to avoid overlap or end-date existing mapping

---

### CATEGORY_NOT_FOUND (404)
**Description:** Posting category does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Create mapping with invalid posting category ID
- Reference non-existent category

**Example:**
```json
{
  "errorCode": "CATEGORY_NOT_FOUND",
  "message": "Posting category 'PC-999' does not exist",
  "details": {
    "postingCategoryId": "PC-999"
  }
}
```

**Recovery:** Verify category ID; create category if needed

---

### MAPPING_KEY_NOT_FOUND (404)
**Description:** Mapping key does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Create mapping with invalid mapping key ID
- Event references non-existent mapping key

**Example:**
```json
{
  "errorCode": "MAPPING_KEY_NOT_FOUND",
  "message": "Mapping key 'MK-999' does not exist",
  "details": {
    "mappingKeyId": "MK-999"
  }
}
```

**Recovery:** Verify mapping key ID; create key if needed

---

### DIMENSION_NOT_FOUND (400)
**Description:** Dimension value does not exist in Organization domain  
**HTTP Status:** 400  
**Use Cases:**
- Create mapping with invalid dimension (businessUnitId, locationId, etc.)
- Dimension validation enabled and reference doesn't exist

**Example:**
```json
{
  "errorCode": "DIMENSION_NOT_FOUND",
  "message": "Business unit 'BU-999' does not exist",
  "details": {
    "dimensionType": "businessUnitId",
    "dimensionValue": "BU-999"
  }
}
```

**Recovery:** Verify dimension value or create entity in Organization domain

---

### CATEGORY_HAS_ACTIVE_MAPPINGS (409)
**Description:** Cannot deactivate category with active GL mappings  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to deactivate posting category while mappings still reference it

**Example:**
```json
{
  "errorCode": "CATEGORY_HAS_ACTIVE_MAPPINGS",
  "message": "Posting category 'PC-001' has 5 active GL mappings and cannot be deactivated",
  "details": {
    "postingCategoryId": "PC-001",
    "activeMappingCount": 5
  }
}
```

**Recovery:** End-date all active mappings first, then deactivate category

---

## Journal Entry Errors (Issue #190, #201)

### JE_NOT_FOUND (404)
**Description:** Journal entry does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch JE by ID that doesn't exist

**Example:**
```json
{
  "errorCode": "JE_NOT_FOUND",
  "message": "Journal entry 'JE-12345' does not exist",
  "details": {
    "journalEntryId": "JE-12345"
  }
}
```

**Recovery:** Verify JE ID

---

### JE_UNBALANCED (422)
**Description:** Journal entry debits do not equal credits  
**HTTP Status:** 422  
**Use Cases:**
- Attempt to post JE with unbalanced lines
- Manual JE creation with arithmetic error

**Example:**
```json
{
  "errorCode": "JE_UNBALANCED",
  "message": "Journal entry is unbalanced: total debits (1000.00) do not equal total credits (950.00)",
  "details": {
    "totalDebits": "1000.00",
    "totalCredits": "950.00",
    "difference": "50.00",
    "tolerance": "0.0001"
  }
}
```

**Recovery:** Adjust line amounts to balance debits and credits

---

### JE_ALREADY_POSTED (409)
**Description:** Journal entry is already posted and cannot be modified  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to edit/delete posted JE
- Attempt to post JE that is already posted

**Example:**
```json
{
  "errorCode": "JE_ALREADY_POSTED",
  "message": "Journal entry 'JE-12345' is already posted and cannot be modified",
  "details": {
    "journalEntryId": "JE-12345",
    "status": "POSTED",
    "postedAt": "2026-01-24T16:00:00Z",
    "postedBy": "user123"
  }
}
```

**Recovery:** Use reversal functionality to correct posted JE

---

### JE_INVALID_STATUS (400)
**Description:** Journal entry status transition invalid  
**HTTP Status:** 400  
**Use Cases:**
- Attempt to transition from POSTED to DRAFT
- Invalid status state machine transition

**Example:**
```json
{
  "errorCode": "JE_INVALID_STATUS",
  "message": "Cannot transition journal entry from POSTED to DRAFT",
  "details": {
    "currentStatus": "POSTED",
    "requestedStatus": "DRAFT"
  }
}
```

**Recovery:** Follow valid status transitions (DRAFT → POSTED only)

---

### REASON_CODE_REQUIRED (400)
**Description:** Reason code required for manual JE  
**HTTP Status:** 400  
**Use Cases:**
- Create manual JE without reason code
- Required field validation

**Example:**
```json
{
  "errorCode": "REASON_CODE_REQUIRED",
  "message": "Reason code is required for manual journal entries",
  "details": {
    "allowedReasonCodes": ["ACCRUAL_ADJUSTMENT", "ERROR_CORRECTION", "RECLASSIFICATION", "OTHER"]
  }
}
```

**Recovery:** Select valid reason code from enum

---

### JUSTIFICATION_REQUIRED (400)
**Description:** Justification required for high-risk operation  
**HTTP Status:** 400  
**Use Cases:**
- Post manual JE without justification
- Reverse JE without justification

**Example:**
```json
{
  "errorCode": "JUSTIFICATION_REQUIRED",
  "message": "Justification is required for posting manual journal entries",
  "details": {
    "operation": "POST",
    "minJustificationLength": 20
  }
}
```

**Recovery:** Provide justification text (min 20 chars)

---

## Posting Rule Errors (Issue #202)

### RULES_NOT_FOUND (404)
**Description:** Posting rule set or version does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Reference non-existent rule set
- Attempt to use archived rule version

**Example:**
```json
{
  "errorCode": "RULES_NOT_FOUND",
  "message": "Posting rule set 'PRS-12345' version 2 does not exist",
  "details": {
    "postingRuleSetId": "PRS-12345",
    "versionNumber": 2
  }
}
```

**Recovery:** Verify rule set ID and version number

---

### UNBALANCED_RULES (422)
**Description:** Posting rules do not balance (debits ≠ credits)  
**HTTP Status:** 422  
**Use Cases:**
- Attempt to publish rule set with unbalanced conditions
- Rule validation failure

**Example:**
```json
{
  "errorCode": "UNBALANCED_RULES",
  "message": "Posting rule set validation failed: debits and credits do not balance",
  "details": {
    "postingRuleSetId": "PRS-12345",
    "versionNumber": 2,
    "conditions": [
      {
        "condition": "saleType == 'CASH'",
        "totalDebits": "1000.00",
        "totalCredits": "1000.00",
        "balanced": true
      },
      {
        "condition": "saleType == 'CREDIT'",
        "totalDebits": "500.00",
        "totalCredits": "450.00",
        "balanced": false,
        "difference": "50.00"
      }
    ]
  }
}
```

**Recovery:** Fix rule definition to balance debits and credits per condition

---

### RULES_ALREADY_PUBLISHED (409)
**Description:** Rule version is already published  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to publish already-published version
- Cannot edit published version

**Example:**
```json
{
  "errorCode": "RULES_ALREADY_PUBLISHED",
  "message": "Posting rule set version 2 is already published",
  "details": {
    "postingRuleSetId": "PRS-12345",
    "versionNumber": 2,
    "publishedAt": "2026-01-20T10:00:00Z"
  }
}
```

**Recovery:** Create new version if changes needed

---

## Vendor Bill Errors (Issue #194)

### VENDOR_BILL_NOT_FOUND (404)
**Description:** Vendor bill does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch bill by ID that doesn't exist

**Example:**
```json
{
  "errorCode": "VENDOR_BILL_NOT_FOUND",
  "message": "Vendor bill 'VB-12345' does not exist",
  "details": {
    "vendorBillId": "VB-12345"
  }
}
```

**Recovery:** Verify bill ID

---

### VENDOR_NOT_FOUND (404)
**Description:** Vendor does not exist in People domain  
**HTTP Status:** 404  
**Use Cases:**
- Create vendor bill with invalid vendor ID
- Vendor reference validation failure

**Example:**
```json
{
  "errorCode": "VENDOR_NOT_FOUND",
  "message": "Vendor 'V-vendor-999' does not exist",
  "details": {
    "vendorId": "V-vendor-999"
  }
}
```

**Recovery:** Verify vendor ID; create vendor in People domain if needed

---

### BILL_ALREADY_APPROVED (409)
**Description:** Vendor bill is already approved and cannot be edited  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to edit bill after approval
- Status = APPROVED or PAID

**Example:**
```json
{
  "errorCode": "BILL_ALREADY_APPROVED",
  "message": "Vendor bill 'VB-12345' is already approved and cannot be edited",
  "details": {
    "vendorBillId": "VB-12345",
    "status": "APPROVED",
    "approvedAt": "2026-01-24T15:00:00Z",
    "approvedBy": "user123"
  }
}
```

**Recovery:** Cannot edit; create new bill if correction needed

---

### BILL_INVALID_STATUS (400)
**Description:** Invalid bill status transition  
**HTTP Status:** 400  
**Use Cases:**
- Attempt invalid status transition (e.g., REJECTED → APPROVED)

**Example:**
```json
{
  "errorCode": "BILL_INVALID_STATUS",
  "message": "Cannot transition vendor bill from REJECTED to APPROVED",
  "details": {
    "currentStatus": "REJECTED",
    "requestedStatus": "APPROVED"
  }
}
```

**Recovery:** Follow valid status transitions

---

## AP Payment Errors (Issue #193)

### PAYMENT_NOT_FOUND (404)
**Description:** AP payment does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch payment by ID that doesn't exist

**Example:**
```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "AP payment 'AP-12345' does not exist",
  "details": {
    "paymentId": "AP-12345"
  }
}
```

**Recovery:** Verify payment ID

---

### PAYMENT_APPROVAL_REQUIRED (403)
**Description:** Payment requires higher approval authority  
**HTTP Status:** 403  
**Use Cases:**
- User attempts to approve payment over threshold
- Insufficient permission level

**Example:**
```json
{
  "errorCode": "PAYMENT_APPROVAL_REQUIRED",
  "message": "Payment amount $15,000 exceeds approval threshold; requires higher authority",
  "details": {
    "paymentId": "AP-12345",
    "amount": "15000.00",
    "userThreshold": "10000.00",
    "requiredPermission": "accounting:ap_payment:approve_over_threshold"
  }
}
```

**Recovery:** Escalate to user with higher approval authority

---

### PAYMENT_ALREADY_SCHEDULED (409)
**Description:** Payment is already scheduled and cannot be modified  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to reschedule already-scheduled payment

**Example:**
```json
{
  "errorCode": "PAYMENT_ALREADY_SCHEDULED",
  "message": "Payment 'AP-12345' is already scheduled for 2026-02-19 and cannot be rescheduled",
  "details": {
    "paymentId": "AP-12345",
    "scheduledDate": "2026-02-19",
    "status": "SCHEDULED"
  }
}
```

**Recovery:** Cancel payment first, then reschedule

---

### PAYMENT_METHOD_REQUIRED (400)
**Description:** Payment method required for scheduling  
**HTTP Status:** 400  
**Use Cases:**
- Schedule payment without specifying method (ACH, CHECK, WIRE)

**Example:**
```json
{
  "errorCode": "PAYMENT_METHOD_REQUIRED",
  "message": "Payment method is required for scheduling",
  "details": {
    "allowedMethods": ["ACH", "CHECK", "WIRE"]
  }
}
```

**Recovery:** Select payment method

---

## Reconciliation Errors (Issue #187)

### RECONCILIATION_NOT_FOUND (404)
**Description:** Reconciliation session does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch reconciliation by ID that doesn't exist

**Example:**
```json
{
  "errorCode": "RECONCILIATION_NOT_FOUND",
  "message": "Reconciliation session 'REC-12345' does not exist",
  "details": {
    "reconciliationId": "REC-12345"
  }
}
```

**Recovery:** Verify reconciliation ID

---

### RECONCILIATION_NOT_BALANCED (422)
**Description:** Reconciliation cannot be finalized (unmatched items remain)  
**HTTP Status:** 422  
**Use Cases:**
- Attempt to finalize with unmatched statement lines or GL transactions

**Example:**
```json
{
  "errorCode": "RECONCILIATION_NOT_BALANCED",
  "message": "Reconciliation cannot be finalized: 3 unmatched statement lines and 2 unmatched GL transactions",
  "details": {
    "reconciliationId": "REC-12345",
    "unmatchedStatementLines": 3,
    "unmatchedGLTransactions": 2,
    "difference": "150.00"
  }
}
```

**Recovery:** Match remaining items or create adjustments

---

### RECONCILIATION_ALREADY_FINALIZED (409)
**Description:** Reconciliation is already finalized and cannot be modified  
**HTTP Status:** 409  
**Use Cases:**
- Attempt to add matches/adjustments to finalized reconciliation

**Example:**
```json
{
  "errorCode": "RECONCILIATION_ALREADY_FINALIZED",
  "message": "Reconciliation 'REC-12345' is already finalized and cannot be modified",
  "details": {
    "reconciliationId": "REC-12345",
    "finalizedAt": "2026-02-01T16:00:00Z",
    "finalizedBy": "user123"
  }
}
```

**Recovery:** Reopen reconciliation (requires HIGH-risk permission)

---

### STATEMENT_IMPORT_FAILED (422)
**Description:** Bank statement import failed (format error)  
**HTTP Status:** 422  
**Use Cases:**
- Invalid CSV/OFX/QBO format
- Missing required fields

**Example:**
```json
{
  "errorCode": "STATEMENT_IMPORT_FAILED",
  "message": "Bank statement import failed: invalid CSV format",
  "details": {
    "fileName": "statement-2026-01.csv",
    "lineNumber": 15,
    "error": "Missing 'amount' column"
  }
}
```

**Recovery:** Fix file format and retry import

---

## Event Ingestion Errors (Issue #183, #207)

### EVENT_NOT_FOUND (404)
**Description:** Accounting event does not exist  
**HTTP Status:** 404  
**Use Cases:**
- Attempt to fetch event by ID that doesn't exist

**Example:**
```json
{
  "errorCode": "EVENT_NOT_FOUND",
  "message": "Accounting event 'AE-12345' does not exist",
  "details": {
    "eventId": "AE-12345"
  }
}
```

**Recovery:** Verify event ID

---

### DUPLICATE_EVENT (409)
**Description:** Event with same ID already submitted (idempotency check)  
**HTTP Status:** 409  
**Use Cases:**
- Duplicate event submission
- Idempotency key conflict

**Example:**
```json
{
  "errorCode": "DUPLICATE_EVENT",
  "message": "Event 'AE-12345' has already been submitted",
  "details": {
    "eventId": "AE-12345",
    "originalSubmissionAt": "2026-01-24T14:00:00Z",
    "idempotencyOutcome": "DUPLICATE_IGNORED",
    "existingJournalEntryId": "JE-67890"
  }
}
```

**Recovery:** Idempotent response; no action needed

---

### EVENT_PROCESSING_FAILED (500)
**Description:** Event processing failed (internal error)  
**HTTP Status:** 500  
**Use Cases:**
- JE generation exception
- Database constraint violation

**Example:**
```json
{
  "errorCode": "EVENT_PROCESSING_FAILED",
  "message": "Event processing failed: internal server error",
  "details": {
    "eventId": "AE-12345",
    "errorDetails": "NullPointerException in JournalEntryService.createEntry",
    "retryCount": 2,
    "lastRetryAt": "2026-01-24T14:05:00Z"
  }
}
```

**Recovery:** Retry event processing; escalate if retries exhausted

---

### INVALID_EVENT_SCHEMA (400)
**Description:** Event payload does not match schema  
**HTTP Status:** 400  
**Use Cases:**
- Missing required fields
- Invalid data types

**Example:**
```json
{
  "errorCode": "INVALID_EVENT_SCHEMA",
  "message": "Event payload validation failed: missing required field 'transactionDate'",
  "details": {
    "eventId": "AE-12345",
    "missingFields": ["transactionDate"],
    "invalidFields": []
  }
}
```

**Recovery:** Fix payload schema and resubmit

---

### INVALID_UUIDV7 (400)
**Description:** Event ID is not valid UUIDv7 (per AD-006)  
**HTTP Status:** 400  
**Use Cases:**
- Client submits non-UUIDv7 event ID
- Validation failure per AD-006

**Example:**
```json
{
  "errorCode": "INVALID_UUIDV7",
  "message": "Event ID 'AE-12345' is not a valid UUIDv7",
  "details": {
    "eventId": "AE-12345",
    "expectedFormat": "UUIDv7 (time-ordered UUID)"
  }
}
```

**Recovery:** Generate valid UUIDv7 event ID

---

## Permission Errors

### PERMISSION_DENIED (403)
**Description:** User lacks required permission for operation  
**HTTP Status:** 403  
**Use Cases:**
- Any operation requiring permission user doesn't have

**Example:**
```json
{
  "errorCode": "PERMISSION_DENIED",
  "message": "User 'user123' lacks permission 'accounting:je:post' for this operation",
  "details": {
    "userId": "user123",
    "requiredPermission": "accounting:je:post",
    "userPermissions": ["accounting:je:view", "accounting:je:create"]
  }
}
```

**Recovery:** Request permission from administrator

---

### AUTHENTICATION_REQUIRED (401)
**Description:** Request missing or invalid authentication token  
**HTTP Status:** 401  
**Use Cases:**
- Missing JWT token
- Expired token

**Example:**
```json
{
  "errorCode": "AUTHENTICATION_REQUIRED",
  "message": "Authentication required: missing or invalid JWT token",
  "details": {
    "authHeader": "missing"
  }
}
```

**Recovery:** Authenticate and include valid JWT token

---

## General Errors

### VALIDATION_ERROR (400)
**Description:** Generic validation failure  
**HTTP Status:** 400  
**Use Cases:**
- Field validation errors
- Business rule violations

**Example:**
```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed for multiple fields",
  "details": {
    "fieldErrors": {
      "accountCode": "Account code must be alphanumeric (max 20 chars)",
      "activationDate": "Activation date cannot be in the past"
    }
  }
}
```

**Recovery:** Fix validation errors and retry

---

### DATABASE_ERROR (500)
**Description:** Database operation failed  
**HTTP Status:** 500  
**Use Cases:**
- Database connection failure
- Constraint violation
- Transaction rollback

**Example:**
```json
{
  "errorCode": "DATABASE_ERROR",
  "message": "Database operation failed: connection timeout",
  "details": {
    "errorType": "ConnectionTimeout",
    "retryAfter": 5
  }
}
```

**Recovery:** Retry after delay; escalate if persistent

---

### SERVICE_UNAVAILABLE (503)
**Description:** Service temporarily unavailable  
**HTTP Status:** 503  
**Use Cases:**
- Service restart
- Maintenance window
- Circuit breaker open

**Example:**
```json
{
  "errorCode": "SERVICE_UNAVAILABLE",
  "message": "Accounting service is temporarily unavailable",
  "details": {
    "retryAfter": 60,
    "maintenanceWindow": "2026-01-25 02:00-04:00 UTC"
  }
}
```

**Recovery:** Retry after specified delay

---

## Error Handling Best Practices

### Frontend Error Display

```vue
<template>
  <q-banner v-if="error" class="bg-negative text-white">
    <template v-slot:avatar>
      <q-icon name="error" />
    </template>
    {{ errorMessage }}
    <template v-slot:action>
      <q-btn flat label="Retry" @click="retry" v-if="retryable" />
      <q-btn flat label="Dismiss" @click="dismissError" />
    </template>
  </q-banner>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const errorMessage = computed(() => {
  if (!error.value) return '';
  
  // User-friendly error messages
  const errorMap = {
    'MAPPING_NOT_FOUND': 'GL mapping not found. Please create a mapping for this transaction.',
    'JE_UNBALANCED': 'Journal entry is unbalanced. Please adjust line amounts.',
    'PERMISSION_DENIED': 'You do not have permission to perform this action.',
    'SERVICE_UNAVAILABLE': 'Service is temporarily unavailable. Please try again later.'
  };
  
  return errorMap[error.value.errorCode] || error.value.message;
});

const retryable = computed(() => {
  const retryableCodes = ['SERVICE_UNAVAILABLE', 'DATABASE_ERROR', 'EVENT_PROCESSING_FAILED'];
  return retryableCodes.includes(error.value?.errorCode);
});
</script>
```

### Backend Error Response

```java
@ExceptionHandler(MappingNotFoundException.class)
public ResponseEntity<ErrorResponse> handleMappingNotFound(MappingNotFoundException ex) {
    ErrorResponse error = ErrorResponse.builder()
        .errorCode("MAPPING_NOT_FOUND")
        .message(ex.getMessage())
        .httpStatus(HttpStatus.NOT_FOUND.value())
        .timestamp(Instant.now())
        .details(Map.of(
            "postingCategoryId", ex.getPostingCategoryId(),
            "mappingKeyId", ex.getMappingKeyId(),
            "transactionDate", ex.getTransactionDate()
        ))
        .build();
    
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}
```

---

## Change Log

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-01-25 | 1.0 | Backend Team | Initial error taxonomy for all accounting issues |

---

## References

- Accounting Backend Contract Guide: `BACKEND_CONTRACT_GUIDE.md`
- Accounting Permission Taxonomy: `PERMISSION_TAXONOMY.md`
- Accounting Questions: `accounting-questions.md`
