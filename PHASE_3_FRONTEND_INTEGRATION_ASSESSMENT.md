# Phase 3: Frontend Integration Layer — Completion Assessment

**Date:** 2026-01-24  
**Phase:** 3 (Moqui Frontend Integration)  
**Goal:** Create Moqui service wrappers and expose accounting services to frontend

---

## Executive Summary

**Status:** ⚠️ **PARTIALLY STARTED (20% Complete)**

The durion-accounting Moqui component exists with foundational structure, but is **missing critical Phase 3 deliverables**:
- ❌ REST service wrappers for backend endpoints (AccountingRestServices.xml)
- ❌ JWT token forwarding configuration
- ❌ Cross-domain integration contracts documentation
- ✅ Component structure in place (service/, screen/, entity/, data/ directories)
- ⚠️ Service definitions exist but are **AR/Payment focused**, not GL/Mapping/Posting Rules focused

---

## Phase 3 Deliverables Check

### 3.1 Moqui Component Structure

| Item | Status | Location | Notes |
|------|--------|----------|-------|
| **Component exists** | ✅ | `/durion-moqui-frontend/runtime/component/durion-accounting/` | Directory structure in place |
| **service/ directory** | ✅ | `durion-accounting/service/` | Exists with 1 XML file |
| **screen/ directory** | ✅ | `durion-accounting/screen/` | Exists with 2 screens (AR-focused) |
| **entity/ directory** | ✅ | `durion-accounting/entity/` | Exists with 2 entities (DurArTransaction, DurPayment) |
| **data/ directory** | ✅ | `durion-accounting/data/` | Exists with demo data |
| **component.xml** | ✅ | `durion-accounting/component.xml` | Minimal configuration |
| **README.md** | ✅ | `durion-accounting/README.md` | Basic documentation |

**Status:** ✅ **Component structure adequate**

### 3.2 Service Wrappers for Backend Endpoints

#### Required Services (per accounting-questions.txt Phase 2)

| Backend Entity | Required Moqui Services | Status | Files | Issues |
|---|---|---|---|---|
| **Chart of Accounts (GLAccount)** | `accounting.chart-of-accounts#list`, `#get`, `#create`, `#update`, `#deactivate`, `#activate` | ❌ MISSING | — | No wrappers for GL account REST endpoints |
| **GL Mapping** | `accounting.mapping#list`, `#get`, `#create`, `#validate`, `#resolveTest` | ❌ MISSING | — | No wrappers for GL mapping REST endpoints |
| **Posting Rules** | `accounting.posting-rules#list`, `#get`, `#create`, `#publish`, `#archive`, `#versions`, `#validate` | ❌ MISSING | — | No wrappers for posting rule REST endpoints |
| **Journal Entries** | `accounting.journal-entries#list`, `#get`, `#create`, `#post`, `#reverse` | ❌ MISSING | — | No wrappers for JE REST endpoints |
| **Event Ingestion** | `accounting.events#submit`, `#get`, `#retry`, `#list` | ❌ MISSING | — | No wrappers for event REST endpoints |
| **AP/Vendor Bills** | `accounting.vendor-bills#list`, `#get`, `#approve`, `#reject` | ⚠️ PARTIAL | DurAccountingServices.xml (lines 1-80) | Existing AR/Payment services; AP missing |

**Current Services in DurAccountingServices.xml:**
- ✅ `createArInvoice` (AR-specific, not GL-focused)
- ✅ `createCreditMemo` (AR-specific)
- ✅ `applyPayment` (Payment-specific)
- ✅ `findArTransactions` (AR-specific)
- ✅ `getArAging` (AR-specific)
- ✅ `recordPayment` (Payment-specific)
- ✅ `depositPayment` (Payment-specific)
- ✅ `clearPayment` (Payment-specific)
- ✅ `findPayments` (Payment-specific)
- ✅ `getPaymentSummary` (Payment-specific)

**Assessment:** Current services do **not** align with Phase 2 backend REST contracts for GL, Mapping, Posting Rules, JE, or Events.

### 3.3 JWT Token Forwarding Configuration

| Component | Status | File | Details |
|-----------|--------|------|---------|
| **JWT forwarding in rest calls** | ❌ MISSING | — | No configuration found in component or service definitions |
| **X-Authorities header injection** | ❌ MISSING | — | Not configured in AccountingRestServices |
| **Token enrichment for accounting permissions** | ⚠️ PARTIAL | pos-security-service/RoleAuthorityService.java | Backend RoleAuthorityService has accounting roles, but Moqui side doesn't forward them |
| **Service authentication** | ❌ MISSING | — | Services in DurAccountingServices.xml have no `auth` attributes |

**Required Configuration:**
```xml
<service verb="list" noun="GLAccounts" type="rest">
    <in-parameters>
        <parameter name="organizationId" required="true"/>
    </in-parameters>
    <out-parameters>
        <parameter name="glAccounts" type="List"/>
    </out-parameters>
    <remote url="http://pos-api-gateway:8080/v1/accounting/gl-accounts"
            method="GET"
            timeout="30"
            auth-jwt="${ec.user.getContext().get('JWT_TOKEN')}"/>
</service>
```

**Status:** ❌ **NOT CONFIGURED**

### 3.4 Cross-Domain Integration Contracts

| Document | Status | Location | Content |
|----------|--------|----------|---------|
| **CROSS_DOMAIN_INTEGRATION_CONTRACTS.md** | ❌ MISSING | Should be in `/durion/domains/accounting/.business-rules/` | No coordination with Organization, Billing, Inventory, Order domains |
| **External dependencies documented** | ❌ MISSING | — | No document defining required data sources (business units, locations, cost centers) |
| **Event type catalog** | ❌ MISSING | — | No canonical event types for order→accounting integration |
| **Vendor/supplier data contracts** | ❌ MISSING | — | No document defining what People/Party domain provides |
| **Integration patterns** | ⚠️ PARTIAL | AGENT_GUIDE.md | Some guidance exists but not formalized as contracts |

**Required Content (per accounting-questions.txt § 3.3):**
- Organization domain dependencies (business units, locations, departments, cost centers)
- Billing domain integration (invoice event contracts)
- Order domain integration (order→journal entry flow)
- Inventory domain integration (inventory movement→AP entry)
- Event type catalog (canonical event types)
- Dimension source mapping (where each dimension comes from)
- External authentication/authorization patterns

**Status:** ❌ **NOT CREATED**

---

## Detailed Gap Analysis

### Gap 1: Missing Backend Service Wrappers

**Current State:** DurAccountingServices.xml contains 10 services focused on AR/AP payment lifecycle.

**Required State:** Moqui service wrappers for **45+ backend REST endpoints** from Phase 2 backend implementation:
- 4 endpoints per entity × 6 entities = 24 core endpoints
- + 6 POST action endpoints (activate, deactivate, publish, archive, post, reverse)
- + 8 repository query endpoints (findBy*, list, etc.)
- + specialized endpoints (resolve, validate, etc.)

**Impact:** Frontend screens cannot call backend GL/Mapping/Posting Rules services without wrappers.

### Gap 2: No JWT/Auth Configuration

**Current State:** Services in DurAccountingServices.xml have no `<auth>` attributes, no remote endpoint configuration.

**Required State:** 
```xml
<service verb="list" noun="GLAccounts" type="rest" auth="always">
    <remote url="http://${pos_api_host:localhost:8080}/v1/accounting/gl-accounts"/>
</service>
```

Each backend endpoint needs corresponding Moqui service with JWT forwarding.

**Impact:** Services cannot authenticate to backend; calls will return 401 UNAUTHORIZED.

### Gap 3: No Cross-Domain Contracts

**Current State:** Phase 2 DOMAIN_MODEL.md documents internal accounting model; no external contracts.

**Required State:** New CROSS_DOMAIN_INTEGRATION_CONTRACTS.md documenting:
- Which domain owns which dimension source (locations, business units, cost centers)
- How order→journal entry flow works
- How billing invoice→AP vendor bill works
- Canonical event type catalog for event ingestion
- Integration patterns (sync REST vs async events)

**Impact:** Unclear which domains to depend on; difficult to implement cross-domain flows.

---

## Current Moqui Component Status

```
durion-accounting/
├── README.md .......................... ✅ Basic docs (needs expansion)
├── component.xml ..................... ⚠️ Minimal (missing service definitions)
├── MoquiConf.xml ..................... ✅ Exists
├── build.gradle ...................... ✅ Exists
├── entity/
│   ├── DurArTransaction.xml .......... ✅ AR transaction entity
│   └── DurPayment.xml ................ ✅ Payment entity
│   └── [MISSING] GLAccount, PostingRuleSet, JournalEntry, GLMapping, etc.
├── screen/
│   ├── ArTransactionFind.xml ......... ✅ AR listing screen
│   └── PaymentEntry.xml ............. ✅ Payment entry screen
│   └── [MISSING] GL account management, mapping config, rule set management screens
├── service/
│   └── DurAccountingServices.xml ..... ⚠️ 10 AR/Payment services, 0 GL services
│   └── [MISSING] REST wrappers for backend endpoints
└── script/
    └── DurAccountingServices.groovy .. ✅ AR/Payment script implementations
    └── [MISSING] GL service implementations
```

---

## Recommended Next Steps (Priority Order)

### Immediate (Critical Path Blockers)

1. **Create AccountingRestServices.xml** (~2 days)
   - Add REST service wrappers for all 6 backend entities
   - Configure JWT token forwarding
   - Add permission annotations from PERMISSION_TAXONOMY.md
   - Document error handling

2. **Create CROSS_DOMAIN_INTEGRATION_CONTRACTS.md** (~1-2 days)
   - Document dependencies with other domains
   - Define canonical event types
   - Define dimension source mappings
   - Get sign-off from Organization, Billing, Order, Inventory teams

3. **Add Entity Definitions** (~2 days)
   - Create Moqui entity definitions for GLAccount, PostingRuleSet, JournalEntry, etc.
   - Align with backend entity schemas
   - Define key/lookup fields for UI

### Secondary (Build Out Screens)

4. **Create GL Account Management Screens** (~3 days)
   - List screen with status filtering
   - Detail screen with activation/deactivation
   - Create/edit form with validation

5. **Create Posting Rule Configuration Screens** (~3 days)
   - Version management UI
   - Rule set editor (may be JSON form)
   - Publish/archive workflow

6. **Create Journal Entry Entry Screens** (~2 days)
   - Batch entry screen with dimension fields
   - Balance validation display
   - Post/reverse operations

7. **Create GL Mapping Configuration Screen** (~2 days)
   - Effective date management
   - Dimension matching configuration
   - Overlap detection UI

### Tertiary (Polish & Test)

8. **Add Integration Tests** (~3 days)
   - Service wrapper tests (mock backend)
   - Permission enforcement tests
   - End-to-end flow tests

9. **API Documentation** (~1 day)
   - OpenAPI/Swagger for service definitions
   - Parameter documentation
   - Error response examples

---

## Dependencies

- ✅ Phase 2 Backend Implementation complete (pos-accounting REST endpoints)
- ✅ DOMAIN_MODEL.md, PERMISSION_TAXONOMY.md, BACKEND_CONTRACT_GUIDE.md complete
- ❌ CROSS_DOMAIN_INTEGRATION_CONTRACTS.md (needed from organization/billing/order teams)
- ❌ Moqui service wrappers
- ❌ Frontend screens

---

## Summary Table

| Deliverable | Requirement | Status | % Complete | Blocker? |
|---|---|---|---|---|
| **3.1.1 Component structure** | Directory layout, component.xml | ✅ Done | 100% | ❌ No |
| **3.1.2 JWT configuration** | Token forwarding in services | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.1 GL Account wrappers** | 6 services (list/get/create/update/activate/deactivate) | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.2 Mapping wrappers** | 5 services (list/get/create/validate/resolve) | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.3 Posting Rule wrappers** | 7 services (list/get/create/publish/archive/versions/validate) | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.4 Journal Entry wrappers** | 5 services (list/get/create/post/reverse) | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.5 Event wrappers** | 4 services (submit/get/retry/list) | ❌ Missing | 0% | ⚠️ Yes |
| **3.2.6 AP/Vendor Bill wrappers** | 4 services (list/get/approve/reject) | ❌ Missing | 0% | ⚠️ Yes |
| **3.3 Cross-domain contracts** | CROSS_DOMAIN_INTEGRATION_CONTRACTS.md | ❌ Missing | 0% | ⚠️ Yes |
| **Screens** | GL, Mapping, Rule Set, JE entry/list screens | ❌ Missing | 0% | ⏳ Secondary |
| **Tests** | Integration tests for service wrappers | ❌ Missing | 0% | ⏳ Secondary |

**Overall:** Phase 3 is **20% complete** (component structure only).

---

## Effort Estimate

| Activity | Estimate | Owner |
|---|---|---|
| AccountingRestServices.xml (6 entities + JWT config) | 2 days | Backend/Frontend |
| CROSS_DOMAIN_INTEGRATION_CONTRACTS.md | 2 days | Domain Team + cross-domain alignment |
| Entity definitions (6 entities) | 1 day | Frontend |
| Screens (GL, Mapping, Rules, JE) | 8 days | Frontend |
| Integration tests | 3 days | QA/Frontend |
| **Total** | **~16-18 days** | |

**Timeline:** If started immediately, Phase 3 could be complete by **Week 1 of February 2026** (assuming 5-day work weeks, parallel tasks).

---

**Next Actions:**
1. Create AccountingRestServices.xml with 30+ service definitions
2. Initiate CROSS_DOMAIN_INTEGRATION_CONTRACTS.md with other domain teams
3. Begin GL account management screens

**Sign-Off Required:** Frontend Team Lead, Architecture Review
