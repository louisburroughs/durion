# CAP-050 Backend Implementation Plan

## Capability Summary
**CAP-050: Maintain Chart of Accounts and Posting Categories**

Define CoA, posting categories, and mapping keys used to translate business events into accounting structure.

## Parent Story Issue
[louisburroughs/durion#50](https://github.com/louisburroughs/durion/issues/50)

## Backend Implementation Issue
[louisburroughs/durion-positivity-backend#138](https://github.com/louisburroughs/durion-positivity-backend/issues/138)

## Implementation Status
- [x] Feature branch created: `cap/CAP050`
- [x] DTOs created for Posting Categories and Mapping Keys
- [x] Services implemented for Posting Categories and Mapping Keys
- [x] Controllers implemented for Posting Categories and Mapping Keys
- [x] Contract behavior tests added (GLAccountContractBehaviorIT, PostingCategoryContractBehaviorIT, MappingKeyContractBehaviorIT)
- [x] Event types registered (AccountingEventTypes and AccountingEventTypeInitializer exist)
- [x] OpenAPI annotations complete (already present on controllers)
- [x] Pull request created: https://github.com/louisburroughs/durion-positivity-backend/pull/427

**Implementation Date:** February 10, 2026

---

## Implementation Checklist

### 1. Chart of Accounts (GL Account) - CRUD Operations

**Existing Artifacts:**
- ✅ Entity: `GLAccount.java`
- ✅ Repository: `GLAccountRepository.java`
- ✅ Controller (stub): `GLAccountController.java`
- ✅ Service (stub): `GLAccountService.java`

**Tasks:**
- [x] Create DTOs:
  - `GLAccountCreateRequest`
  - `GLAccountUpdateRequest`
  - `GLAccountResponse`
  - `GLAccountListResponse`
  - `GLAccountBalanceResponse`
- [x] Implement `GLAccountService` methods:
  - `createGLAccount` - validate account code uniqueness, set activation date
  - `getGLAccount` - retrieve with derived status
  - `updateGLAccount` - validate immutability rules (account code/type locked after first posting)
  - `listGLAccounts` - pagination, filtering by status/type
  - `activateGLAccount` - set activation date
  - `deactivateGLAccount` - verify zero balance, set deactivation date
  - `archiveGLAccount` - verify inactive status, set archived date
  - `getAccountBalance` - sum debit/credit lines from journal entries
  - `validateAccountForPosting` - validate account is active and exists
- [x] Wire service into controller
- [x] Add validation error handling
- [x] Add @EmitEvent annotations (already present)

### 2. Posting Categories - CRUD Operations

**Existing Artifacts:**
- ✅ Entity: `PostingCategory.java`
- ✅ Repository: `PostingCategoryRepository.java`

**Tasks:**
- [x] Create DTOs:
  - `PostingCategoryCreateRequest` (with @Builder)
  - `PostingCategoryUpdateRequest` (with @Builder)
  - `PostingCategoryResponse`
  - `PostingCategoryListResponse`
- [x] Create `PostingCategoryService`:
  - `createPostingCategory` - validate name uniqueness
  - `getPostingCategory` - retrieve by ID
  - `updatePostingCategory` - update name/description
  - `listPostingCategories` - pagination, filtering by active status
  - `deactivatePostingCategory` - check no active mappings reference it (returns PostingCategoryResponse)
- [x] Create `PostingCategoryController`:
  - `POST /v1/accounting/posting-categories`
  - `GET /v1/accounting/posting-categories/{id}`
  - `PUT /v1/accounting/posting-categories/{id}`
  - `GET /v1/accounting/posting-categories`
  - `POST /v1/accounting/posting-categories/{id}/deactivate`
- [x] Add OpenAPI annotations
- [x] Add @EmitEvent annotations
- [x] Add security annotations (@PreAuthorize)

### 3. Mapping Keys - CRUD Operations

**Existing Artifacts:**
- ✅ Entity: `MappingKey.java`
- ✅ Repository: `MappingKeyRepository.java`

**Tasks:**
- [x] Create DTOs:
  - `MappingKeyCreateRequest` (with @Builder)
  - `MappingKeyUpdateRequest` (with @Builder)
  - `MappingKeyResponse`
  - `MappingKeyListResponse`
- [x] Create `MappingKeyService`:
  - `createMappingKey` - validate posting category exists, key name unique within category
  - `getMappingKey` - retrieve by ID
  - `updateMappingKey` - update name/description
  - `listMappingKeysByCategory` - list all keys for a posting category (with 5 params including isActive filter)
  - `linkToCategory` - associate key with category (validate 1:1 deterministic mapping)
  - `deactivateMappingKey` - check no active mappings reference it (returns MappingKeyResponse)
- [x] Create `MappingKeyController`:
  - `POST /v1/accounting/mapping-keys`
  - `GET /v1/accounting/mapping-keys/{id}`
  - `PUT /v1/accounting/mapping-keys/{id}`
  - `GET /v1/accounting/posting-categories/{categoryId}/mapping-keys`
  - `POST /v1/accounting/mapping-keys/{id}/deactivate`
- [x] Add OpenAPI annotations
- [x] Add @EmitEvent annotations
- [x] Add security annotations

### 4. GL Mappings - Configuration (Existing)

**Note:** GL Mapping functionality already exists in the codebase:
- ✅ Entity: `GLMapping.java`
- ✅ Repository: `GLMappingRepository.java`
- ✅ Service: `GLMappingResolver.java`

**Tasks:**
- [ ] Review existing GL Mapping implementation
- [ ] Ensure validation that posting categories and mapping keys exist
- [ ] Add effective dating validation (overlapping date ranges)
- [ ] Document mapping resolution algorithm in contract guide

### 5. Event Type Registration

**Tasks:**
- [x] Create `AccountingEventTypes` registry class with:
  - `ACCOUNTING_GL_ACCOUNT_CREATE`
  - `ACCOUNTING_GL_ACCOUNT_UPDATE`
  - `ACCOUNTING_GL_ACCOUNT_ACTIVATE`
  - `ACCOUNTING_GL_ACCOUNT_DEACTIVATE`
  - `ACCOUNTING_GL_ACCOUNT_ARCHIVE`
  - `ACCOUNTING_GL_ACCOUNT_LIST`
  - `ACCOUNTING_POSTING_CATEGORY_CREATE`
  - `ACCOUNTING_POSTING_CATEGORY_UPDATE`
  - `ACCOUNTING_POSTING_CATEGORY_DEACTIVATE`
  - `ACCOUNTING_POSTING_CATEGORY_LIST`
  - `ACCOUNTING_MAPPING_KEY_CREATE`
  - `ACCOUNTING_MAPPING_KEY_UPDATE`
  - `ACCOUNTING_MAPPING_KEY_DEACTIVATE`
  - `ACCOUNTING_MAPPING_KEY_LIST`
- [x] Create `AccountingEventTypeInitializer` (ApplicationRunner pattern)
- [x] Register event types with performance thresholds:
  - Read operations: `fastRead` preset (p50=50ms, p95=200ms, p99=500ms)
  - Write operations: `write` preset (p50=200ms, p95=1s, p99=3s)

### 6. Contract Behavior Tests

**Tasks:**
- [x] Create `GLAccountContractBehaviorIT` (20 test cases):
  - Happy path: create, retrieve, update, activate, deactivate, archive
  - Validation: duplicate account code rejected
  - Immutability: account code/type locked after creation
  - Deactivation: requires zero balance
  - Archival: requires inactive status
  - Effective dating: derived status calculation
  - Balance queries and posting validation
- [x] Create `PostingCategoryContractBehaviorIT` (10 test cases):
  - Happy path: create, retrieve, update, deactivate
  - Validation: duplicate name rejected
  - Deactivation blocked if active mappings exist
  - List with pagination and filtering
- [x] Create `MappingKeyContractBehaviorIT` (14 test cases):
  - Happy path: create, retrieve, update, link to category
  - Validation: duplicate key name within category rejected
  - Validation: posting category exists
  - Deactivation blocked if active mappings exist
  - List by category with filters
- [ ] Create `GLMappingContractBehaviorIT`:
  - Effective dating: overlapping ranges rejected
  - Resolution: deterministic mapping selection
  - Validation: posting category and mapping key exist
  - Validation: GL account exists

### 7. Error Handling

**Tasks:**
- [x] Add custom exceptions:
  - `GLAccountNotFoundException` - HTTP 404
  - `PostingCategoryNotFoundException`
  - `MappingKeyNotFoundException`
  - `DuplicateAccountCodeException` - HTTP 409
  - `DuplicatePostingCategoryException`
  - `DuplicateMappingKeyException`
  - `AccountNotZeroBalanceException` - HTTP 400
  - `AccountNotInactiveException` - HTTP 400
  - `ActiveMappingsExistException`
- [x] Add global exception handler mapping to standard error response format
- [x] Follow error code conventions from `domains/accounting/.business-rules/ERROR_CODES.md`

---

## File Structure

### DTOs
```
pos-accounting/src/main/java/com/positivity/accounting/internal/dto/
├── GLAccountCreateRequest.java
├── GLAccountUpdateRequest.java
├── GLAccountResponse.java
├── GLAccountListResponse.java
├── GLAccountBalanceResponse.java
├── PostingCategoryCreateRequest.java
├── PostingCategoryUpdateRequest.java
├── PostingCategoryResponse.java
├── PostingCategoryListResponse.java
├── MappingKeyCreateRequest.java
├── MappingKeyUpdateRequest.java
├── MappingKeyResponse.java
└── MappingKeyListResponse.java
```

### Services
```
pos-accounting/src/main/java/com/positivity/accounting/service/
├── GLAccountService.java (update existing)
├── PostingCategoryService.java (new)
└── MappingKeyService.java (new)
```

### Controllers
```
pos-accounting/src/main/java/com/positivity/accounting/internal/controller/
├── GLAccountController.java (update existing)
├── PostingCategoryController.java (new)
└── MappingKeyController.java (new)
```

### Event Configuration
```
pos-accounting/src/main/java/com/positivity/accounting/internal/config/
├── AccountingEventTypes.java (new)
└── AccountingEventTypeInitializer.java (new)
```

### Tests
```
pos-accounting/src/test/java/com/positivity/accounting/contract/
├── GLAccountContractBehaviorIT.java (new)
├── PostingCategoryContractBehaviorIT.java (new)
├── MappingKeyContractBehaviorIT.java (new)
└── GLMappingContractBehaviorIT.java (new)
```

---

## Business Rules & Validation

### GL Account
1. **Account code** must be unique across all accounts
2. **Account type** is immutable after creation
3. **Account code** is immutable after first posting
4. **Deactivation** requires zero balance
5. **Archival** requires inactive status
6. **Derived status** based on activation/deactivation dates:
   - ACTIVE: activationDate <= today < deactivationDate (or null)
   - INACTIVE: deactivationDate <= today
   - NOT_YET_ACTIVE: activationDate > today

### Posting Category
1. **Category name** must be unique
2. **Deactivation** blocked if active GL mappings reference it
3. Audit trail for all lifecycle changes

### Mapping Key
1. **Key name** must be unique within a posting category
2. **Posting category** must exist
3. **1:1 deterministic mapping** - each key links to exactly one category
4. **Deactivation** blocked if active GL mappings reference it

### GL Mapping
1. **Posting category** and **mapping key** must exist
2. **GL account** must exist and be active
3. **Effective dating** - no overlapping date ranges for same category+key
4. **Resolution algorithm** follows hierarchy:
   - Exact match with dimensions
   - Dimensional fallback
   - Category default (no dimensions)

---

## Security Permissions

### GL Account Operations
- `accounting:coa:view` - List and retrieve accounts
- `accounting:coa:create` - Create new accounts
- `accounting:coa:edit` - Update account details
- `accounting:coa:deactivate` - Deactivate or archive accounts

### Posting Category Operations
- `accounting:posting-category:view` - List and retrieve categories
- `accounting:posting-category:create` - Create new categories
- `accounting:posting-category:edit` - Update category details
- `accounting:posting-category:deactivate` - Deactivate categories

### Mapping Key Operations
- `accounting:mapping-key:view` - List and retrieve keys
- `accounting:mapping-key:create` - Create new keys
- `accounting:mapping-key:edit` - Update key details
- `accounting:mapping-key:deactivate` - Deactivate keys

---

## OpenAPI Annotations

All endpoints must include:
- `@Operation` with summary and description
- `@ApiResponses` for all possible status codes
- `@Parameter` for path/query parameters
- `@Tag` for controller grouping

---

## Next Steps

1. ✅ Create feature branch `cap/CAP050`
2. ✅ Create DTOs for all entities
3. ✅ Implement service layer methods
4. ✅ Wire services into controllers
5. ✅ Add event type registration
6. ✅ Create contract behavior tests
7. ⏳ Run tests and fix failures (1 test failure in GLAccountContractBehaviorIT.testActivateGLAccount_Success)
8. ⏳ Complete GLMappingContractBehaviorIT tests
9. Commit and push changes
10. Update pull request

---

## Implementation Summary

### Completed Work (February 10, 2026)

**Services Implemented:**
- `GLAccountService` - Full implementation with 10 methods (400+ lines)
  - CRUD operations with validation
  - Lifecycle management (activate, deactivate, archive)
  - Balance calculations from journal entry lines
  - Derived status calculation based on effective dates
  
- `PostingCategoryService` - Already existed, updated to return response DTOs from lifecycle operations
- `MappingKeyService` - Already existed, updated to return response DTOs from lifecycle operations

**DTOs Created/Updated:**
- All GL Account DTOs (Create, Update, Response, List, Balance)
- Posting Category request DTOs with `@Builder` annotation
- Mapping Key request DTOs with `@Builder` annotation

**Exception Classes Created:**
- `GLAccountNotFoundException` (HTTP 404)
- `DuplicateAccountCodeException` (HTTP 409)
- `AccountNotZeroBalanceException` (HTTP 400)
- `AccountNotInactiveException` (HTTP 400)

**Tests Created:**
- `GLAccountContractBehaviorIT` - 20 test cases
- `PostingCategoryContractBehaviorIT` - 10 test cases
- `MappingKeyContractBehaviorIT` - 14 test cases
- **Total: 44 contract behavior tests**

**Event Types:**
- `AccountingEventTypes` registry - Already exists with all event type definitions
- `AccountingEventTypeInitializer` - Already exists with ApplicationRunner pattern

### Known Issues

1. **Test Failure:** `GLAccountContractBehaviorIT.testActivateGLAccount_Success`
   - **Issue:** Test expects `ACTIVE` status but receives `NOT_YET_ACTIVE`
   - **Root Cause:** Derived status calculation may have timing issue with activation date
   - **Status:** Needs investigation and fix

### Pending Work

1. Fix failing test in `GLAccountContractBehaviorIT`
2. Create `GLMappingContractBehaviorIT` test suite
3. Run full test suite to verify all 44+ tests pass
4. Update and finalize pull request

---

## References

- Contract Guide: `/home/louisb/Projects/durion/domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- Error Codes: `/home/louisb/Projects/durion/domains/accounting/.business-rules/ERROR_CODES.md`
- Backend AGENTS.md: `/home/louisb/Projects/durion-positivity-backend/AGENTS.md`
- Parent Capability Issue: https://github.com/louisburroughs/durion/issues/50
- Backend Story Issue: https://github.com/louisburroughs/durion-positivity-backend/issues/138
