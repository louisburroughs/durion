# CAP-055 Backend Contract Guide Update

**Capability:** CAP:055 — Reconciliation, Audit, and Controls  
**Date:** 2026-02-10  
**Authoritative OpenAPI:** `durion-positivity-backend/pos-accounting/openapi.json`

## Section 1: Plan
1. Parse `docs/capabilities/CAP-055/CAPABILITY_MANIFEST.yaml` and extract capability id + backend child issue URL(s).
2. Parse `durion-positivity-backend/pos-accounting/openapi.json` and extract all operations (method + path) and their request/response metadata.
3. Scan `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` for documented endpoints and any non-gateway URLs.
4. Compute delta between OpenAPI operations and guide content (endpoint summary + endpoint detail sections).
5. Draft minimal guide edits: add missing endpoints (OpenAPI-derived), update endpoint count/table, and add implementation links to backend issues.
6. Validate: YAML/JSON parsing, issue URL formation, and ensure endpoints documented correspond to OpenAPI operations and stay on gateway host (`http://localhost:8080`).

## Section 2: Human Checklist
- Confirm the endpoint count is **67** and the endpoint summary table includes the 4 financial reporting endpoints under `/api/v1/reports/financial/*`.
- Review the new Endpoint Details blocks for posting categories, mapping keys, AP payments, payment application reversal, legacy invoice pay, and reporting.
- Sanity-check that all URLs use `http://localhost:8080` (gateway host) and no direct service ports appear.
- Spot-check a couple of new endpoints against the OpenAPI spec: operationId, required params, request body schema name, and status codes.
- If `/api/v1/reports/...` should be normalized to `/v1/accounting/...`, update the OpenAPI producer first, then regenerate this guide.

## Section 3: JSON Summary
```json
{
  "capability_id": "CAP:055",
  "manifest_path": "docs/capabilities/CAP-055/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/122"
  ],
  "updated_files": [
    "domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-055/CAP-055-backend-contract.md"
  ],
  "openapi_changes": {
    "added": [
      "GET /v1/accounting/posting-categories",
      "POST /v1/accounting/posting-categories",
      "GET /v1/accounting/posting-categories/{postingCategoryId}",
      "PUT /v1/accounting/posting-categories/{postingCategoryId}",
      "POST /v1/accounting/posting-categories/{postingCategoryId}/deactivate",
      "GET /v1/accounting/posting-categories/{postingCategoryId}/mapping-keys",
      "POST /v1/accounting/mapping-keys",
      "GET /v1/accounting/mapping-keys/{mappingKeyId}",
      "PUT /v1/accounting/mapping-keys/{mappingKeyId}",
      "POST /v1/accounting/mapping-keys/{mappingKeyId}/deactivate",
      "POST /v1/accounting/payment-applications/{applicationId}/reverse",
      "POST /v1/accounting/invoices/{invoiceId}/pay",
      "GET /v1/accounting/ap/bills",
      "POST /v1/accounting/ap/payments",
      "GET /v1/accounting/ap/payments/{paymentId}",
      "GET /v1/accounting/ap/payments/by-ref/{paymentRef}",
      "GET /api/v1/reports/financial/income-statement",
      "GET /api/v1/reports/financial/balance-sheet",
      "GET /api/v1/reports/financial/drilldown/journal-lines/{accountId}",
      "GET /api/v1/reports/financial/drilldown/accounts/{statementLineCode}"
    ],
    "changed": [],
    "removed": []
  },
  "applied": true,
  "confidence": 97
}
```

## Section 4: Patch
```apply_patch
*** Begin Patch
*** Update File: /home/louisb/Projects/durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md
@@
-**OpenAPI Source:** `pos-accounting/openapi.json`
+**OpenAPI Source:** `durion-positivity-backend/pos-accounting/openapi.json`
@@
 This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.
+
+---
+
+## Implementation Links
+
+- Capability: https://github.com/louisburroughs/durion/issues/55
+- Backend child issue: https://github.com/louisburroughs/durion-positivity-backend/issues/122
 
 ---
@@
-This domain exposes **63** REST API endpoints:
+This domain exposes **67** REST API endpoints:
@@
 | POST | `http://localhost:8080/v1/accounting/ap/payments` | Execute vendor payment |
 | GET | `http://localhost:8080/v1/accounting/ap/payments/{paymentId}` | Get payment details |
 | GET | `http://localhost:8080/v1/accounting/ap/payments/by-ref/{paymentRef}` | Get payment by reference |
 | GET | `http://localhost:8080/v1/accounting/ap/bills` | List eligible vendor bills |
+| GET | `http://localhost:8080/api/v1/reports/financial/income-statement` | Generate Income Statement |
+| GET | `http://localhost:8080/api/v1/reports/financial/drilldown/journal-lines/{accountId}` | Drilldown to Journal Lines |
+| GET | `http://localhost:8080/api/v1/reports/financial/drilldown/accounts/{statementLineCode}` | Drilldown to Accounts |
+| GET | `http://localhost:8080/api/v1/reports/financial/balance-sheet` | Generate Balance Sheet |
 | GET | `http://localhost:8080/v1/accounting/traceability/{journalEntryId}` | Get journal traceability |
 | POST | `http://localhost:8080/v1/invoice/invoices` | Regenerate invoice from workorder |
 | GET | `http://localhost:8080/v1/invoice/rules/{customerId}` | Get billing rules |
@@
 #### GET <http://localhost:8080/v1/accounting/posting-rules/{postingRuleSetId}/versions>
@@
 - `200`: Posting rule versions listed
 - `404`: Posting rule set not found
 
 ---
+
+#### GET <http://localhost:8080/v1/accounting/posting-categories>
+
+**Summary:** List posting categories
+
+**Description:** Retrieve paginated posting categories.
+
+**Operation ID:** `listPostingCategories`
+
+**Parameters:**
+
+- `page` (query, Optional, integer (int32))
+- `size` (query, Optional, integer (int32))
+- `sort` (query, Optional, string)
+- `isActive` (query, Optional, boolean)
+
+**Responses:**
+
+- `200`: Posting categories listed
+- `403`: Forbidden
+
+**Provider test hint (ContractBehaviorIT):**
+
- Exercise `GET /v1/accounting/posting-categories` (optionally vary query parameters); assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/posting-categories>
+
+**Summary:** Create posting category
+
+**Description:** Create a new posting category.
+
+**Operation ID:** `createPostingCategory`
+
+**Request Body:**
+
+- `application/json`: `PostingCategoryCreateRequest`
+
+**Responses:**
+
+- `201`: Posting category created
+- `400`: Invalid request or duplicate name
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/posting-categories` with a valid `PostingCategoryCreateRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}>
+
+**Summary:** Get posting category
+
+**Description:** Retrieve a posting category by identifier.
+
+**Operation ID:** `getPostingCategory`
+
+**Parameters:**
+
+- `postingCategoryId` (path, Required, string (uuid))
+
+**Responses:**
+
+- `200`: Posting category returned
+- `404`: Posting category not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/posting-categories/{postingCategoryId}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### PUT <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}>
+
+**Summary:** Update posting category
+
+**Description:** Update an existing posting category.
+
+**Operation ID:** `updatePostingCategory`
+
+**Parameters:**
+
+- `postingCategoryId` (path, Required, string (uuid))
+
+**Request Body:**
+
+- `application/json`: `PostingCategoryUpdateRequest`
+
+**Responses:**
+
+- `200`: Posting category updated
+- `400`: Invalid request or duplicate name
+- `404`: Posting category not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `PUT /v1/accounting/posting-categories/{postingCategoryId}` with a valid `PostingCategoryUpdateRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/deactivate>
+
+**Summary:** Deactivate posting category
+
+**Description:** Deactivate a posting category.
+
+**Operation ID:** `deactivatePostingCategory`
+
+**Parameters:**
+
+- `postingCategoryId` (path, Required, string (uuid))
+
+**Responses:**
+
+- `204`: Posting category deactivated
+- `404`: Posting category not found
+- `409`: Cannot deactivate - active mappings exist
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/posting-categories/{postingCategoryId}/deactivate` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/posting-categories/{postingCategoryId}/mapping-keys>
+
+**Summary:** List mapping keys by category
+
+**Description:** Retrieve paginated mapping keys for a posting category.
+
+**Operation ID:** `listMappingKeysByCategory`
+
+**Parameters:**
+
+- `postingCategoryId` (path, Required, string (uuid))
+- `page` (query, Optional, integer (int32))
+- `size` (query, Optional, integer (int32))
+- `sort` (query, Optional, string)
+- `isActive` (query, Optional, boolean)
+
+**Responses:**
+
+- `200`: Mapping keys listed
+- `403`: Forbidden
+- `404`: Posting category not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/posting-categories/{postingCategoryId}/mapping-keys` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/mapping-keys>
+
+**Summary:** Create mapping key
+
+**Description:** Create a new mapping key for a posting category.
+
+**Operation ID:** `createMappingKey`
+
+**Request Body:**
+
+- `application/json`: `MappingKeyCreateRequest`
+
+**Responses:**
+
+- `201`: Mapping key created
+- `400`: Invalid request or duplicate name
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/mapping-keys` with a valid `MappingKeyCreateRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}>
+
+**Summary:** Get mapping key
+
+**Description:** Retrieve a mapping key by identifier.
+
+**Operation ID:** `getMappingKey`
+
+**Parameters:**
+
+- `mappingKeyId` (path, Required, string (uuid))
+
+**Responses:**
+
+- `200`: Mapping key returned
+- `404`: Mapping key not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/mapping-keys/{mappingKeyId}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### PUT <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}>
+
+**Summary:** Update mapping key
+
+**Description:** Update an existing mapping key.
+
+**Operation ID:** `updateMappingKey`
+
+**Parameters:**
+
+- `mappingKeyId` (path, Required, string (uuid))
+
+**Request Body:**
+
+- `application/json`: `MappingKeyUpdateRequest`
+
+**Responses:**
+
+- `200`: Mapping key updated
+- `400`: Invalid request or duplicate name
+- `404`: Mapping key not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `PUT /v1/accounting/mapping-keys/{mappingKeyId}` with a valid `MappingKeyUpdateRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/mapping-keys/{mappingKeyId}/deactivate>
+
+**Summary:** Deactivate mapping key
+
+**Description:** Deactivate a mapping key.
+
+**Operation ID:** `deactivateMappingKey`
+
+**Parameters:**
+
+- `mappingKeyId` (path, Required, string (uuid))
+
+**Responses:**
+
+- `204`: Mapping key deactivated
+- `404`: Mapping key not found
+- `409`: Cannot deactivate - active mappings exist
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/mapping-keys/{mappingKeyId}/deactivate` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/payment-applications/{applicationId}/reverse>
+
+**Summary:** Reverse payment application
+
+**Description:** Reverse a payment application with compensating transaction (no deletion).
+
+**Operation ID:** `reversePaymentApplication`
+
+**Parameters:**
+
+- `applicationId` (path, Required, string (uuid))
+
+**Request Body:**
+
+- `application/json`: `PaymentApplicationReversalRequest`
+
+**Responses:**
+
+- `204`: Payment application reversed
+- `400`: Invalid request or already reversed
+- `404`: Payment application not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/payment-applications/{applicationId}/reverse` with a valid `PaymentApplicationReversalRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/invoices/{invoiceId}/pay>
+
+**Summary:** Apply payment (LEGACY)
+
+**Description:** Apply a payment to an invoice (invoice-centric workflow). Use /payments/{paymentId}/applications for new API.
+
+**Operation ID:** `applyPayment_1`
+
+**Parameters:**
+
+- `invoiceId` (path, Required, string (uuid))
+
+**Request Body:**
+
+- `application/json`: `PaymentAppliedRequest`
+
+**Responses:**
+
+- `200`: Payment applied
+- `400`: Invalid payment request
+- `500`: Processing error
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/invoices/{invoiceId}/pay` with a valid `PaymentAppliedRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/ap/bills>
+
+**Summary:** List eligible vendor bills
+
+**Description:** Get eligible vendor bills for payment (status = APPROVED). Bills are ordered by due date (oldest first, nulls last), then bill date, then bill ID.
+
+**Operation ID:** `listBills`
+
+**Parameters:**
+
+- `vendorId` (query, Required, string (uuid))
+
+**Responses:**
+
+- `200`: Bills retrieved successfully
+- `400`: Invalid vendor ID
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/ap/bills` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### POST <http://localhost:8080/v1/accounting/ap/payments>
+
+**Summary:** Execute vendor payment
+
+**Description:** Execute a vendor payment with optional explicit allocations to bills. Idempotent using paymentRef: same ref + same payload returns existing payment; same ref + different payload yields 409 conflict.
+
+**Operation ID:** `executePayment`
+
+**Request Body:**
+
+- `application/json`: `ExecuteAPPaymentRequest`
+
+**Responses:**
+
+- `200`: Idempotent replay: existing payment returned
+- `201`: Payment executed successfully (new payment created)
+- `400`: Validation error: negative amounts, invalid bills, etc.
+- `409`: Conflict: paymentRef exists with different payload
+- `502`: Payment gateway failure
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `POST /v1/accounting/ap/payments` with a valid `ExecuteAPPaymentRequest` payload; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/ap/payments/{paymentId}>
+
+**Summary:** Get payment details
+
+**Description:** Retrieve AP payment details including allocations and GL posting status.
+
+**Operation ID:** `getPayment`
+
+**Parameters:**
+
+- `paymentId` (path, Required, string (uuid))
+
+**Responses:**
+
+- `200`: Payment found
+- `404`: Payment not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/ap/payments/{paymentId}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/v1/accounting/ap/payments/by-ref/{paymentRef}>
+
+**Summary:** Get payment by reference
+
+**Description:** Retrieve AP payment details by paymentRef (idempotency key).
+
+**Operation ID:** `getPaymentByRef`
+
+**Parameters:**
+
+- `paymentRef` (path, Required, string)
+
+**Responses:**
+
+- `200`: Payment found
+- `404`: Payment not found
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /v1/accounting/ap/payments/by-ref/{paymentRef}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/api/v1/reports/financial/income-statement>
+
+**Summary:** Generate Income Statement
+
+**Description:** Generate Profit & Loss report for a date range with revenue, expenses, and net income
+
+**Operation ID:** `generateIncomeStatement`
+
+**Parameters:**
+
+- `startDate` (query, Required, string (date))
+- `endDate` (query, Required, string (date))
+
+**Responses:**
+
+- `200`: Income statement generated successfully
+- `400`: Invalid date range
+- `401`: Unauthorized
+- `403`: Forbidden - missing reporting:view:financial-statements
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /api/v1/reports/financial/income-statement` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/api/v1/reports/financial/drilldown/journal-lines/{accountId}>
+
+**Summary:** Drilldown to Journal Lines
+
+**Description:** Show source journal entries contributing to a GL account balance
+
+**Operation ID:** `drilldownToJournalLines`
+
+**Parameters:**
+
+- `accountId` (path, Required, string)
+- `startDate` (query, Required, string (date))
+- `endDate` (query, Required, string (date))
+
+**Responses:**
+
+- `200`: Journal line drilldown successful
+- `400`: Invalid account ID or date range
+- `401`: Unauthorized
+- `403`: Forbidden - missing reporting:view:financial-statements
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /api/v1/reports/financial/drilldown/journal-lines/{accountId}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/api/v1/reports/financial/drilldown/accounts/{statementLineCode}>
+
+**Summary:** Drilldown to Accounts
+
+**Description:** Show which GL accounts contribute to a specific statement line
+
+**Operation ID:** `drilldownToAccounts`
+
+**Parameters:**
+
+- `statementLineCode` (path, Required, string)
+- `startDate` (query, Required, string (date))
+- `endDate` (query, Required, string (date))
+
+**Responses:**
+
+- `200`: Account drilldown successful
+- `400`: Invalid statement line code or date range
+- `401`: Unauthorized
+- `403`: Forbidden - missing reporting:view:financial-statements
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /api/v1/reports/financial/drilldown/accounts/{statementLineCode}` with required parameters populated; assert status codes per OpenAPI.
+
+---
+
+#### GET <http://localhost:8080/api/v1/reports/financial/balance-sheet>
+
+**Summary:** Generate Balance Sheet
+
+**Description:** Generate Balance Sheet as of a specific date with assets, liabilities, and equity
+
+**Operation ID:** `generateBalanceSheet`
+
+**Parameters:**
+
+- `asOfDate` (query, Required, string (date))
+
+**Responses:**
+
+- `200`: Balance sheet generated successfully
+- `400`: Invalid date
+- `401`: Unauthorized
+- `403`: Forbidden - missing reporting:view:financial-statements
+
+**Provider test hint (ContractBehaviorIT):**
+
+- Exercise `GET /api/v1/reports/financial/balance-sheet` with required parameters populated; assert status codes per OpenAPI.
+
+---
 
 #### GET <http://localhost:8080/v1/accounting/traceability/{journalEntryId}>
*** End Patch
```

## Section 5: Self-Critique
- Financial reporting endpoints are under `/api/v1/...` in OpenAPI; the patch documents them as-is (gateway host retained) rather than rewriting paths.
- Many OpenAPI responses omit explicit response-body schemas; the patch avoids inventing response payload shapes and only documents schemas where OpenAPI references them.

Next steps: if you approve, apply the patch to `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md` and re-run any provider contract tests that validate endpoint surface coverage.
