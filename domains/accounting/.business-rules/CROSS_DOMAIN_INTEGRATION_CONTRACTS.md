# Cross-Domain Integration Contracts – Durion Accounting Domain

## Overview
This document defines the integration contracts between the **Accounting domain** and other domains (Organization, Billing, Order, Inventory, People). These contracts establish canonical data flows, event types, dimension source mappings, and interaction patterns required for Phase 3 frontend integration.

**Status:** Phase 3 Frontend Integration Layer  
**Last Updated:** 2025  
**Owner:** Accounting Domain Lead / Durion Platform Engineering  

---

## 1. Domain Dependencies & Context

### 1.1 Required Dependencies
| Domain | Purpose | Contract Type | Status |
| -------- | --------- | --------------- | -------- |
| **Organization** | Organizational dimensions, business units, locations, cost centers | Temporal dimension resolution | ✅ Ready |
| **Billing** | Invoice-to-GL-Account posting rules, revenue recognition | Event-based posting; Posting rule sets | 🟨 Coordination needed |
| **Order** | Sales order line to AR-Account mapping; Revenue accrual | Mapping resolution; Event ingestion | 🟨 Coordination needed |
| **Inventory** | Inventory accrual posting; Cost of goods sold calculation | Event ingestion; Posting rules | 🟨 Coordination needed |
| **People** | Vendor master data; Employee/approver hierarchy | Reference data; Permission context | ✅ Ready |

### 1.2 Consuming Domains
Domains that consume accounting services:
- **Billing**: Query GL balances, submit accounting events for revenue posting
- **Order**: Submit accounting events for sales revenue, AR creation, COGS accrual
- **Inventory**: Submit events for inventory accrual, COGS posting
- **CRM**: AR aging reports, payment status queries

---

## 2. Dimension Source Mappings

### 2.1 Recognized Dimensions
The Accounting domain recognizes the following dimensions for GL mapping and posting rule matching:

| Dimension | Source Domain | Source Entity | Cardinality | Required | Example Values |
|-----------|---------------|---------------|-------------|----------|-----------------|
| **BUSINESS_UNIT** | Organization | BusinessUnit | 1..* per Organization | N | 'BU001', 'BU002' |
| **LOCATION** | Organization | Location | 1..* per BusinessUnit | N | 'LOC001', 'NYC', 'LA' |
| **COST_CENTER** | Organization | CostCenter | 1..* per Organization | Y | 'CC-SALES', 'CC-OPS' |
| **DEPARTMENT** | Organization | Department | 1..* per Organization | N | 'SALES', 'ENGINEERING' |
| **PROJECT** | Order | Project | 0..* per Organization | N | 'PRJ-001', 'ACME-2025' |
| **CUSTOMER** | CRM | Customer | 0..* per Organization | N | 'CUST-001', external ID |
| **VENDOR** | People | Vendor | 0..* per Organization | N | 'VEND-001', external ID |
| **PRODUCT** | Inventory | Product | 0..* per Organization | N | 'SKU-001', 'PROD-ABC' |
| **CURRENCY** | Organization | Currency | 1 per Organization | Y | 'USD', 'EUR' |

### 2.2 Dimension Lookup Flows
**Dimension Value to Organization Master Data:**

```
Source Event (e.g., Billing.InvoicePosted)
  ↓
Extract: {businessUnitId, locationId, costCenter}
  ↓
GL Mapping Resolution Service (accounting-backend)
  ├─ Query Organization.BusinessUnit (cached, effective-dated)
  ├─ Query Organization.Location (cached, effective-dated)
  ├─ Query Organization.CostCenter (reference data)
  └─ Verify dimensions exist; fail fast if not found
  ↓
Return resolved GL Account ID + validated dimension context
  ↓
Journal Entry creation with {dimensionValues: {...}} JSON field
```

**Recommended Caching Strategy:**
- Cache Organization dimensions in Accounting service startup (5-minute refresh)
- Dimension validation fails with `DIMENSION_NOT_FOUND` if mismatch
- Accounting events store raw dimension IDs; validation deferred to GL mapping phase

---

## 3. Canonical Event Catalog

### 3.1 Event Type Registry
All accounting-relevant business events are captured in this registry. External domains **MUST** emit events with these type codes; Accounting domain consumes and translates to journal entries.

#### Billing Domain Events
| Event Type | Source | Trigger | Payload | Posting Pattern | Status |
|------------|--------|---------|---------|-----------------|--------|
| **billing.invoicePosted** | Billing Service | Invoice marked POSTED | {invoiceId, customerId, totalAmount, lineItems: [{productId, quantity, price, glAccountId, dimensions}]} | A/R Receivable + Revenue (multi-line) | ✅ Ready |
| **billing.creditMemoCreated** | Billing Service | Credit memo created | {invoiceId, creditMemoId, customerId, amount, reason} | A/R Adjustment (debit to A/R, credit to Revenue) | ✅ Ready |
| **billing.paymentReceived** | Billing Service | Payment recorded | {customerId, paymentAmount, invoiceIds, paymentDate, method} | A/R reduction (debit to Cash, credit to A/R) | 🟨 In progress |

#### Order Domain Events
| Event Type | Source | Trigger | Payload | Posting Pattern | Status |
|------------|--------|---------|---------|-----------------|--------|
| **order.shipmentCreated** | Order Service | Shipment created from order | {orderId, shipmentId, lineItems: [{productId, quantity, unitPrice, dimensions}]} | COGS + Revenue Accrual (debit COGS, credit Inventory) | 🟨 Coordination needed |
| **order.orderFulfilled** | Order Service | Order fully shipped/completed | {orderId, totalAmount, fulfillmentDate} | Final revenue recognition (if deferred) | 🟨 Coordination needed |

#### Inventory Domain Events
| Event Type | Source | Trigger | Payload | Posting Pattern | Status |
|------------|--------|---------|---------|-----------------|--------|
| **inventory.stockReceived** | Inventory Service | Purchase order receipt posted | {purchaseOrderId, vendorId, lineItems: [{productId, quantity, unitCost, dimensions}]} | Inventory accrual (debit Inventory, credit AP) | 🟨 Coordination needed |
| **inventory.inventoryAdjustment** | Inventory Service | Inventory count adjustment | {adjustmentId, productId, quantityDelta, adjustmentReason} | Inventory variance (debit/credit Inventory, credit/debit COGS or misc) | 🟨 Coordination needed |

#### Organization Domain Events
| Event Type | Source | Trigger | Payload | Posting Pattern | Status |
|------------|--------|---------|---------|-----------------|--------|
| **organization.costCenterCreated** | Organization Service | New cost center created | {costCenterId, name, parentCostCenterId, effectiveDate} | Reference data update; triggers dimension cache refresh | ✅ Ready |
| **organization.locationDeactivated** | Organization Service | Location marked inactive | {locationId, effectiveDate} | Reference data update; future GL mappings must exclude | ✅ Ready |

### 3.2 Event Emission Standards
**All domains MUST adhere to:**

```json
{
  "eventId": "evt-20250101-abc123",
  "eventType": "billing.invoicePosted",
  "sourceSystem": "billing-service",
  "organizationId": "org-001",
  "transactionDate": "2025-01-01T10:30:00Z",
  "payload": {
    "invoiceId": "inv-001",
    "customerId": "cust-001",
    "totalAmount": 1000.00,
    "lineItems": [...]
  },
  "dimensions": {
    "businessUnitId": "bu-001",
    "locationId": "loc-001",
    "costCenterId": "cc-sales"
  },
  "metadata": {
    "sourceEventId": "billing:evt:202501:invoice-123",
    "idempotencyKey": "inv-001:attempt-1",
    "correlationId": "order-001"
  }
}
```

**Validation Rules:**
- `eventId` MUST be globally unique (UUID or {sourceSystem}:{timestamp}:{nonce})
- `organizationId` MUST match authenticated user's organization context
- `transactionDate` MUST be date-only or ISO 8601 timestamp with timezone
- `dimensions` MUST include all **Required** dimensions listed in §2.1
- `metadata.idempotencyKey` MUST allow safe event replay (Accounting service deduplicates)

---

## 4. Integration Patterns

### 4.1 Event-Based Posting (Asynchronous)
**Pattern:** Domain emits event → Accounting consumes → Journal entry created → GL updated

```
Timeline:
T0: Billing.invoicePosted event emitted
    └─ Event stored in Accounting.AccountingEvent queue (RECEIVED status)
    └─ JWT token forwarded in Authorization header from Moqui frontend

T1: EventIngestionService polls/consumes event
    ├─ Validate event schema + dimensions
    ├─ Query Organization master data (cached)
    ├─ Query GL Mapping (effective-date resolution)
    ├─ Query active PostingRuleSet
    └─ Mark event as PROCESSING

T2: JournalEntryService creates entry (atomic)
    ├─ Validate debit ≈ credit (±0.0001 tolerance)
    ├─ Persist JournalEntry + JournalEntryLines
    ├─ Update GL balance cache
    └─ Mark event as POSTED

T3: EventIngestionService completes
    ├─ Update AccountingEvent.status = COMPLETED
    ├─ Link AccountingEvent.journalEntryId
    └─ Emit accounting.journalEntryPosted event (for auditing)

Failure Path (T1-T2):
    ├─ Exception caught, event marked FAILED
    ├─ Error reason stored in event log
    └─ Retry queue populated (exponential backoff)
```

**Required JWT Token Contents (for cross-domain context):**
```json
{
  "sub": "user-001",
  "organizationId": "org-001",
  "permissions": ["accounting:events:submit", "accounting:je:create"],
  "sourceSystem": "billing-service",
  "traceId": "trace-abc123"
}
```

### 4.2 Mapping Resolution (Synchronous)
**Pattern:** External code → Dimension-based GL account lookup → Resolved GL ID

```
Timeline (from GL Mapping REST service):
T0: POST /v1/accounting/mappings/resolve
    {
      "sourceSystem": "ERP_LEGACY",
      "externalCode": "1000-COGS",
      "transactionDate": "2025-01-01",
      "dimensions": {
        "businessUnitId": "bu-001",
        "locationId": "loc-001",
        "costCenterId": "cc-sales"
      }
    }

T1: GLMappingResolver.resolve()
    ├─ Query DurGLMapping WHERE
    │  sourceSystem = 'ERP_LEGACY'
    │  AND externalCode = '1000-COGS'
    │  AND effectiveStartDate ≤ T0
    │  AND (effectiveEndDate IS NULL OR effectiveEndDate ≥ T0)
    │  AND dimensionMatch IN (bu-001, loc-001, cc-sales)
    ├─ Sort by priority DESC, effectiveStartDate DESC
    ├─ Return highest-priority match
    └─ If no match: throw MAPPING_NOT_FOUND

T2: Response
    {
      "glAccountId": "gl-001-cogs",
      "postingCategory": "OPERATING",
      "dimensions": {
        "businessUnitId": "bu-001",
        "locationId": "loc-001",
        "costCenterId": "cc-sales"
      }
    }
```

**Error Handling:**
| Error Code | HTTP Status | Meaning | Recovery |
|-----------|-------------|---------|----------|
| `MAPPING_NOT_FOUND` | 404 | No active mapping for external code | Fallback mapping or manual entry |
| `DIMENSION_NOT_FOUND` | 400 | Dimension value doesn't exist in Organization | Validate dimension before event submission |
| `MAPPING_AMBIGUOUS` | 400 | Multiple equal-priority mappings match | Admin must adjust priorities |
| `MAPPING_EXPIRED` | 400 | All mappings are past their effective-end date | Admin must create new mapping |

---

## 5. Data Synchronization & Temporal Consistency

### 5.1 Effective-Date Semantics
**All cross-domain references use effective-dating:**

- **GL Account**: `effectiveDate` to `inactiveDate` (dimension valid during range)
- **GL Mapping**: `effectiveStartDate` to `effectiveEndDate` (mapping valid during range)
- **Posting Rule Set**: `publishedDate` onwards (version-based, no end date)
- **Organization Dimensions**: Managed by Organization domain; Accounting caches with 5-min TTL

**Example:** If Location 'LOC-001' is deactivated on 2025-06-30:
- Accounting queries Location master data on 2025-07-01: returns inactive status
- GL Mappings using LOC-001 dimensionMatch reject transactions on/after 2025-07-01
- Existing JournalEntries created before 2025-07-01 remain valid (immutable)

### 5.2 Synchronization Flows
**Organization Dimensions Cache Refresh:**
```
Timer (every 5 minutes):
  ├─ Query Organization API: GET /v1/organization/business-units?active=true
  ├─ Query Organization API: GET /v1/organization/locations?active=true
  ├─ Query Organization API: GET /v1/organization/cost-centers?active=true
  ├─ Merge into LocalCache (ConcurrentHashMap)
  ├─ Log dimension count: "Refreshed 15 business units, 42 locations, 103 cost centers"
  └─ On error: log warning, use stale cache, retry next cycle

Event-Driven Cache Invalidation (preferred):
  Subscribe to: organization.costCenterCreated, organization.locationDeactivated, ...
  On receipt:
    ├─ Update LocalCache immediately
    └─ Log: "Dimension cache invalidated: {dimension: 'LOCATION', id: 'loc-001', action: 'deactivate'}"
```

---

## 6. Permission Model & Authorization Boundaries

### 6.1 Cross-Domain Permission Context
Accounting domain enforces permissions at API boundary; relies on JWT token for user context.

**JWT Token Enrichment (performed by pos-security-service):**
```json
{
  "sub": "user-001",
  "organizationId": "org-001",
  "roles": ["GL_ANALYST", "AP_CLERK", "ACCOUNTANT"],
  "permissions": [
    "accounting:coa:view",
    "accounting:coa:create",
    "accounting:je:create",
    "accounting:je:post",
    "accounting:je:reverse",
    "accounting:mappings:view",
    "accounting:rules:publish",
    "accounting:ap:approve",
    "accounting:ap:reject",
    "accounting:ap:pay"
  ],
  "sourceSystem": "billing-service",
  "traceId": "trace-abc123"
}
```

**Permission Sources:**
- **Role definitions:** pos-security-service RoleAuthorityService (accounting roles)
- **Cross-domain role grants:** Organizations can assign users to CRM roles, which inherit accounting permissions
- **Resource-level grants:** Accounting domain checks per-record permissions (e.g., AP_CLERK can approve bills in their cost center only)

### 6.2 Service-to-Service Authentication
**Internal Service-to-Service (Service Account JWT):**

When Accounting backend calls Organization or Inventory services:
```
Header: Authorization: Bearer <service-account-jwt>
  {
    "sub": "service:accounting:pos-accounting",
    "scope": "organization:read inventory:read",
    "aud": "organization-service,inventory-service"
  }
```

**Moqui Frontend → Spring Boot Backend:**
```
Header: Authorization: Bearer <user-jwt>
  (same JWT returned by login; forwarded as-is)
```

---

## 7. Error Handling & Failure Modes

### 7.1 Event Processing Failures
| Scenario | Root Cause | Accounting Response | Retry Strategy |
|----------|-----------|-------------------|-----------------|
| **Missing Organization Dimension** | External domain emits unknown `businessUnitId` | Event marked FAILED; error logged | Admin must verify Organization master data; manual retry after fix |
| **GL Mapping Not Found** | No mapping rule for external code | Event marked FAILED; retryable | Admin creates mapping; automatic retry (exponential backoff) |
| **Duplicate Event** | Same event emitted twice (same `idempotencyKey`) | Second submission rejected; returns existing result | Idempotency key deduplication ensures idempotent API |
| **Unbalanced Entry** | Debit ≠ Credit (beyond tolerance) | JournalEntryService throws exception; event rolled back | Originating domain must fix payload; manual retry after correction |
| **Network Timeout** | Organization dimension query fails | Event marked FAILED; retryable | Automatic retry (5x with exponential backoff, then quarantine) |

### 7.2 Moqui Frontend Error Handling
**Accounting REST Service Wrapper Error Response Format:**
```json
{
  "error": {
    "code": "MAPPING_NOT_FOUND",
    "message": "No active GL mapping found for external code 1000-COGS",
    "httpStatus": 404,
    "context": {
      "sourceSystem": "ERP_LEGACY",
      "externalCode": "1000-COGS",
      "transactionDate": "2025-01-01"
    }
  }
}
```

**Moqui Service Wrapper Retry Logic:**
```groovy
def remoteResponse = ec.service.rest('accounting.resolveGLMapping', [...])
if (remoteResponse.error?.code == 'MAPPING_NOT_FOUND') {
  // Log: suggest admin create mapping
  // Return user-friendly error: "GL mapping not configured for this transaction"
} else if (remoteResponse.error?.code in ['DIMENSION_NOT_FOUND', 'NETWORK_TIMEOUT']) {
  // Auto-retry up to 3x
  for (int attempt = 1; attempt <= 3; attempt++) {
    remoteResponse = ec.service.rest('accounting.resolveGLMapping', [...])
    if (!remoteResponse.error) break
    Thread.sleep(1000 * attempt) // exponential backoff
  }
}
```

---

## 8. Testing & Contract Validation

### 8.1 Contract Test Coverage
Each domain provides contract tests to validate integration:

**Accounting Domain Tests:**
```
com.positivity.accounting.integration.contracts
├── BillingEventContractTest (validate billing.invoicePosted payload)
├── OrderEventContractTest (validate order.shipmentCreated payload)
├── InventoryEventContractTest (validate inventory.stockReceived payload)
├── OrganizationDimensionContractTest (validate dimension lookup & caching)
└── GLMappingResolutionContractTest (validate mapping resolution with dimensions)
```

**Moqui Frontend Tests:**
```
runtime/component/durion-accounting/test
├── AccountingRestServicesContractTest.groovy
├── JournalEntryRestServiceTest.groovy
├── GLMappingRestServiceTest.groovy
└── EventIngestionRestServiceTest.groovy
```

### 8.2 Contract Testing Approach
**Event Payload Validation:**
```groovy
@Test
def "billing.invoicePosted event creates GL revenue entry"() {
  // GIVEN: valid event payload
  def event = [
    eventType: 'billing.invoicePosted',
    payload: [invoiceId: 'inv-001', totalAmount: 1000.00, ...],
    dimensions: [businessUnitId: 'bu-001', ...]
  ]
  
  // WHEN: event submitted to Accounting
  def result = ec.service.sync('durion.submitAccountingEvent', [event: event])
  
  // THEN: journal entry created with correct accounts
  assert result.journalEntry.status == 'POSTED'
  assert result.journalEntry.journalEntryLines.any { it.debitAmount == 1000.00 }
}
```

---

## 9. Migration & Rollout Plan

### 9.1 Phased Rollout
| Phase | Focus | Domains | Timeline | Blockers |
|-------|-------|---------|----------|----------|
| **Phase 3.0** | Accounting core (GL, Mapping, Rules) | Accounting (standalone) | Week 1-2 | None |
| **Phase 3.1** | Billing integration | Billing → Accounting | Week 3-4 | Billing team readiness |
| **Phase 3.2** | Order/Inventory integration | Order, Inventory → Accounting | Week 5-6 | Order/Inventory team readiness |
| **Phase 3.3** | Full system testing | All domains | Week 7 | Phase 3.0-3.2 completion |

### 9.2 Backward Compatibility
- **Accounting API versions**: Moqui frontend pins to `/v1/accounting/*` endpoint version
- **Event schema evolution**: New event types added with `eventTypeVersion` field; old events still supported
- **Dimension schema**: New dimensions added via `MAPPING_KEY` reference table; old dimensions remain valid

---

## 10. Governance & Change Management

### 10.1 Event Type Registration
**All new event types MUST be:**
1. Defined in this contract document (§3.1)
2. Registered in `EventTypeRegistry.java` (Spring Boot backend)
3. Added to `EventTypeEnum` in Moqui `DurAccountingServices.xml`
4. Tested via contract test suite
5. Announced in domain slack channel 48 hours before production use

### 10.2 Dimension Changes
**Adding a new dimension:**
1. Domain lead submits RFC in Durion governance channel
2. Accounting domain reviews; confirm GL mapping rule support
3. Update this document §2.1 (Recognized Dimensions)
4. Create `DurMappingKey` reference data record
5. Deploy to test environment; smoke test GL mapping resolution
6. Deploy to production

### 10.3 GL Mapping Rule Publishing
**Posting Rule Set lifecycle:**
1. GL Analyst creates rule set in DRAFT status
2. Accountant reviews + validates rules
3. Controller publishes (DRAFT → PUBLISHED)
4. System auto-archives previous version (PUBLISHED → ARCHIVED)
5. Future events use newly published rules

---

## 11. Appendix: API Response Formats

### 11.1 GL Mapping Resolution Success Response
```json
{
  "glAccountId": "gl-001-ar-receivable",
  "accountNumber": "1200",
  "accountType": "ASSET",
  "postingCategory": "OPERATING",
  "status": "ACTIVE",
  "dimensions": {
    "businessUnitId": "bu-001",
    "locationId": "loc-001",
    "costCenterId": "cc-sales"
  },
  "effectiveDate": "2024-01-01",
  "resolvedAt": "2025-01-01T10:30:00Z"
}
```

### 11.2 Journal Entry Creation Success Response
```json
{
  "journalEntryId": "je-001",
  "organizationId": "org-001",
  "status": "POSTED",
  "transactionDate": "2025-01-01T10:30:00Z",
  "totalDebit": 1000.00,
  "totalCredit": 1000.00,
  "lines": [
    {
      "journalEntryLineId": "jel-001",
      "glAccountId": "gl-001-ar",
      "debitAmount": 1000.00,
      "creditAmount": 0.00
    },
    {
      "journalEntryLineId": "jel-002",
      "glAccountId": "gl-002-revenue",
      "debitAmount": 0.00,
      "creditAmount": 1000.00
    }
  ],
  "sourceEventId": "evt-billing-inv-001",
  "postedAt": "2025-01-01T10:30:05Z"
}
```

---

**End of Contract Document**  
For questions or updates, contact: Accounting Domain Lead  
Repository: [durion-positivity-backend](https://github.com/louisburroughs/durion-positivity-backend)
