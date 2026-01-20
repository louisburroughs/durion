Title: [BACKEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/4
Labels: type:story, domain:crm, status:ready-for-dev

## 🏷️ Labels (Updated)

### Required
- type:story
- domain:crm
- status:ready

### Recommended
- agent:crm
- agent:story-authoring

---

## Story Intent

Enable **Service Advisors** to access a complete customer snapshot (account, contacts, vehicles, billing configuration) during order creation so that downstream processes can respect customer-specific billing rules, contact preferences, and vehicle context without repeated lookups or manual data entry.

This story establishes the **foundational data retrieval capability** that supports accurate quoting, invoicing, and communication throughout the order lifecycle.

---

## Actors & Stakeholders

### Primary Actors
- **Service Advisor**: Retrieves customer snapshot when creating or managing orders
- **CRM Service**: System of record for customer accounts, contacts, vehicles, and billing configuration
- **Order Creation Service (Workexec)**: Consumer of customer snapshot during order flow

### Secondary Stakeholders
- **Billing/Invoicing Service**: Consumes billing rules for enforcement during checkout and payment
- **Pricing Service**: May reference customer billing configuration (e.g., tax exemption) during quote calculation
- **Audit Service**: Logs snapshot retrieval and cache behavior for compliance

---

## Preconditions

1. Customer record exists in CRM with valid account ID
2. Service Advisor has permission to view customer details
3. CRM snapshot API is available and responding
4. At least one of the following exists for the customer:
   - Account-level billing configuration
   - At least one contact
   - At least one associated vehicle (optional but common)

---

## Functional Behavior

### 1. Retrieve Customer Snapshot by Account ID

**Trigger**: Service Advisor selects a customer during order creation or views customer details

**Flow**:
1. System receives request with customer account ID and requesting user context
2. System checks cache for unexpired customer snapshot
3. If cache miss or expired:
   - System calls CRM snapshot API with account ID
   - CRM returns CustomerSnapshot payload including:
     - Account metadata (account ID, name, type, status)
     - Contacts list (name, role, phone, email, preferred contact method)
     - Vehicles list (VIN, make, model, year, mileage, description)
     - Billing rules (PO requirement flag, tax exemption flag, payment terms, credit limit)
4. System caches snapshot with configured TTL
5. System returns snapshot to calling service/UI

**Outcome**: Customer snapshot is available for immediate use in order creation and downstream billing processes

---

### 2. Expose Billing Rule Enforcement Hooks

**Trigger**: Downstream service (e.g., order creation, invoicing) needs to validate or enforce billing rules

**Flow**:
1. Calling service requests billing rules from customer snapshot
2. System returns BillingRuleRef with flags and configuration:
   - `poRequired`: boolean
   - `taxExempt`: boolean
   - `paymentTerms`: string (e.g., "Net 30", "Due on Receipt")
   - `creditLimit`: decimal (optional)
   - `creditHold`: boolean
   - `invoiceDeliveryMethod`: enum (EMAIL, MAIL, PORTAL, EDI)
   - `billingAddressId`: UUID (optional)
   - `autoPayEnabled`: boolean
   - `discountPolicyRef`: string (optional)
   - `currency`: string (ISO-4217, optional)
3. Calling service applies rules according to its domain logic (e.g., checkout blocks if PO required but not provided)

**Outcome**: Billing rules are accessible to enforcement services without direct CRM coupling

---

### 3. Cache and Update Strategy

**Trigger**: Customer snapshot is requested or updated

**Flow**:
1. On first retrieval: Cache snapshot with TTL (15 minutes default)
2. On subsequent requests within TTL: Return cached snapshot
3. On cache expiration: Fetch fresh snapshot from CRM and update cache
4. On explicit invalidation (e.g., customer update event from CRM): Purge cache entry for that account

**Storage**: Redis (preferred) for shared cache across instances; in-memory cache for dev/test only  
**Cache key format**: `customer-snapshot:v1:<accountId>`  
**Invalidation triggers**: CustomerUpdated, ContactUpdated, VehicleUpdated, BillingRulesUpdated, AccountStatusChanged

**Outcome**: Balance between performance (caching) and freshness (TTL-based expiration)

---

## Alternate / Error Flows

### 1. Customer Not Found in CRM

**Trigger**: Account ID does not exist in CRM

**Flow**:
1. CRM API returns 404 Not Found
2. System logs error with account ID and requesting user
3. System returns error to calling service: "Customer not found"
4. UI displays user-friendly message: "Customer account not found. Please verify account ID."

---

### 2. CRM Service Unavailable

**Trigger**: CRM API is unreachable or returns 5xx error

**Flow**:
1. System detects CRM unavailability (timeout or error response)
2. System checks cache for stale snapshot (even if expired)
3. If stale snapshot exists and `staleSince <= 60 minutes`:
   - Return stale snapshot with warning flag: `stale: true, staleSince: timestamp`
   - Log degraded mode operation
4. If no snapshot exists or stale exceeds 60 minutes:
   - **Fail-open for order drafting**: Allow creating order draft with minimal customer identifiers
   - **Fail-closed for submission**: Block submission/checkout unless advisor provides required fields manually
   - Flag order as "CRM-DEGRADED"
   - Log critical alert for CRM outage
5. Retry with exponential backoff for cache refresh

**Outcome**: Graceful degradation using stale cache when CRM is unavailable

---

### 3. Incomplete Customer Data

**Trigger**: CRM returns snapshot with missing expected fields (e.g., no contacts, no billing rules)

**Flow**:
1. System validates snapshot payload
2. If billing rules are missing:
   - Apply default billing configuration (e.g., no PO required, not tax exempt, payment terms "Due on Receipt")
   - Log warning with account ID
3. If contacts are missing:
   - Include empty contacts array
   - Log warning for manual follow-up
4. Return snapshot with populated defaults and warning flags

---

### 4. Unauthorized Access

**Trigger**: Requesting user does not have permission to view customer account

**Flow**:
1. System checks user permissions against account access rules (evaluated at request time even if returning cached data)
2. If unauthorized:
   - Return 403 Forbidden
   - Log access attempt with user ID, account ID, timestamp
3. UI displays: "You do not have permission to view this customer."

---

### 5. Cache Invalidation Failure

**Trigger**: Customer update event is received but cache invalidation fails

**Flow**:
1. System receives customer update event from CRM (via event bus or webhook)
2. System attempts to purge cache entry
3. If purge fails:
   - Log error with account ID and retry up to 3 times
   - If still failing, rely on TTL-based expiration
4. Monitor for cache staleness metrics

---

## Business Rules

### 1. Customer Snapshot Composition

A customer snapshot MUST include:
- **Account**: ID, name, account type (individual, commercial, fleet), status (active, inactive)
- **Contacts**: Array of contact records (may be empty)
- **Vehicles**: Array of vehicle records (may be empty)
- **Billing Rules**: PO requirement, tax exemption, payment terms, credit limit (with defaults if not configured)

---

### 2. Billing Rule Defaults

If CRM does not provide explicit billing rules for a customer, apply the following defaults:
- `poRequired`: false
- `taxExempt`: false
- `paymentTerms`: "Due on Receipt"
- `creditLimit`: null (no limit)
- `creditHold`: false
- `autoPayEnabled`: false
- `invoiceDeliveryMethod`: EMAIL

These defaults MUST be logged and surfaced to users for manual review.

---

### 3. Cache TTL and Staleness Policy

- **Default TTL**: 15 minutes (configurable per environment)
- **Stale Tolerance**: Up to 60 minutes in degraded mode (CRM unavailable)
- **Invalidation Events**: CustomerUpdated, ContactUpdated, VehicleUpdated, BillingRulesUpdated, AccountStatusChanged
- Cache entries MUST include timestamp for staleness detection

---

### 4. Preferred Contact Method Priority

If a customer has multiple contacts, the **preferred contact method** is determined by:
1. Contact with `isPrimary=true`
2. Contact with explicit preference for the relevant `preferredFor` category (if present)
3. Contact with `preferredContactMethod` present (any)
4. First in list (stable ordering from CRM)

Calling services SHOULD respect preferred contact for notifications.

**Allowed values**: PHONE, EMAIL, SMS, PORTAL, MAIL  
**Scope**: Preference is per-contact; account-level default contact method may be added later.

---

### 5. Vehicle Context Association

Vehicles are associated with customer accounts at the **account level**, not individual contact level. A customer may have:
- Zero vehicles (walk-in service)
- One vehicle (typical individual customer)
- Many vehicles (fleet accounts)

**Snapshot includes lightweight vehicle data only**: identity + order-creation essentials. Full service history, tire specs, warranty status are fetched separately from work history or dedicated vehicle-service-history services.

---

### 6. Billing Rule Enforcement Ownership

This service **provides** billing rules but does NOT enforce them. Enforcement is the responsibility of:
- **Checkout/Order Creation**: Validates PO requirement before order submission, handles creditHold during submission/approval gates
- **Invoicing**: Applies payment terms and credit limit checks before invoice issuance
- **Pricing**: Applies tax exemption and discount policy evaluation

This service exposes `BillingRuleRef` as a **read-only configuration snapshot**.

---

### 7. Integration with Order Creation Flow

**Touchpoints**:
- **When fetched**: On **customer selection** in order creation UI/API flow
- **How held**: Stored as a **snapshot reference** in the order draft/session:
  - `orderDraft.customerSnapshotRef = { accountId, retrievedAt, cacheKey?, snapshotEtag? }`
- **Refresh behavior**:
  - Auto-refresh only on TTL expiry when UI explicitly re-opens customer panel or user hits **Refresh**
  - Do not silently refresh mid-checkout unless a rule requires it

**Security note**: Authorization must be evaluated at request time even if returning cached data.

---

## Data Requirements

### CustomerSnapshot (Response Model)

```json
{
  "accountId": "string (UUID)",
  "accountName": "string",
  "accountType": "enum (INDIVIDUAL, COMMERCIAL, FLEET)",
  "accountStatus": "enum (ACTIVE, INACTIVE, SUSPENDED)",
  "contacts": [
    {
      "contactId": "string (UUID)",
      "name": "string",
      "role": "string (e.g., Owner, Fleet Manager, Billing Contact)",
      "phone": "string",
      "email": "string",
      "preferredContactMethod": "enum (PHONE, EMAIL, SMS, PORTAL, MAIL)",
      "preferredFor": "enum list (QUOTES, INVOICES, APPOINTMENTS, MARKETING, GENERAL)",
      "doNotContact": "boolean (optional)",
      "isPrimary": "boolean"
    }
  ],
  "vehicles": [
    {
      "vehicleId": "string (UUID)",
      "vin": "string (17 characters)",
      "make": "string",
      "model": "string",
      "year": "integer",
      "mileage": "integer (optional)",
      "description": "string (optional)",
      "unitNumber": "string (optional)",
      "licensePlate": "string (optional)"
    }
  ],
  "billingRules": {
    "poRequired": "boolean",
    "taxExempt": "boolean",
    "paymentTerms": "string (e.g., Net 30, Due on Receipt)",
    "creditLimit": "decimal (optional)",
    "creditHold": "boolean",
    "invoiceDeliveryMethod": "enum (EMAIL, MAIL, PORTAL, EDI)",
    "billingAddressId": "string (UUID, optional)",
    "autoPayEnabled": "boolean",
    "discountPolicyRef": "string (optional)",
    "currency": "string (ISO-4217, optional)",
    "extensions": "map<string, any> (for future fields)"
  },
  "metadata": {
    "retrievedAt": "timestamp (ISO 8601)",
    "source": "enum (CACHE, CRM_API)",
    "stale": "boolean",
    "staleSince": "timestamp (ISO 8601, optional)"
  }
}
```

---

### VehicleRef (Nested Model)

**Scope**: Lightweight identity + order-creation essentials only

**Key Fields**:
- `vehicleId`: Unique identifier in CRM
- `vin`: Vehicle Identification Number (17-character standard)
- `make`, `model`, `year`: Core vehicle identity
- `mileage`: Optional odometer reading
- `description`: Optional free-text field for notes
- `unitNumber`: Optional fleet identifier
- `licensePlate`: Optional for improved lookup UX

**Explicitly out of scope**: Tire specs, full service history, warranty status, inspection history, attachments

---

### BillingRuleRef (Nested Model)

**Authoritative owner**: CRM domain owns storage and delivery; Billing domain owns semantic meaning and enforcement

**Key Fields (v1 schema)**:
- `poRequired`: If true, customer MUST provide a PO number before order submission
- `taxExempt`: If true, skip tax calculation (certificate presence tracked separately)
- `paymentTerms`: Human-readable payment terms string
- `creditLimit`: Optional decimal; cumulative unpaid invoices cannot exceed this limit
- `creditHold`: If true, caller should block credit-based flows
- `invoiceDeliveryMethod`: How invoices should be delivered to customer
- `billingAddressId`: References address entity in CRM
- `autoPayEnabled`: Informative flag (not an instruction to charge)
- `discountPolicyRef`: Reference key only; pricing owns evaluation
- `currency`: ISO-4217 currency code
- `extensions`: Map for future sector-specific rules

**Extension strategy**: Unknown keys in `extensions` must be ignored by consumers.

**Defaulting rule**: Defaults apply when entire `billingRules` object is missing. If partial fields are missing, default only those missing fields and log which keys were defaulted.

---

### Cache Entry Schema

Internal cache structure:

```json
{
  "cacheKey": "customer-snapshot:v1:<accountId>",
  "payload": "<CustomerSnapshot JSON>",
  "cachedAt": "timestamp (ISO 8601)",
  "expiresAt": "timestamp (ISO 8601)",
  "version": "integer (for cache versioning)"
}
```

---

## Acceptance Criteria

### AC1: Customer Snapshot Loads Successfully

**Given** a valid customer account ID exists in CRM  
**When** a Service Advisor requests the customer snapshot  
**Then** the system returns a CustomerSnapshot payload within 2 seconds  
**And** the payload includes account, contacts, vehicles, and billing rules sections  
**And** the snapshot is cached with the configured TTL

---

### AC2: Billing Rule Enforcement Hooks Are Exposed

**Given** a customer snapshot has been loaded  
**When** a downstream service requests billing rules  
**Then** the system returns a BillingRuleRef object with all flags and configuration  
**And** the calling service can check `poRequired`, `taxExempt`, `creditHold`, and other billing fields without additional CRM calls

---

### AC3: Cache Behavior Works as Expected

**Given** a customer snapshot has been cached  
**When** the same customer is requested again within the TTL window  
**Then** the system returns the cached snapshot without calling CRM  
**And** the response includes `source: CACHE` in metadata  
**When** the TTL expires  
**Then** the next request fetches a fresh snapshot from CRM  
**And** the cache is updated with the new snapshot

---

### AC4: Stale Cache Is Used When CRM Is Unavailable

**Given** CRM service is unavailable or unresponsive  
**And** a stale cached snapshot exists for the customer (stale <= 60 minutes)  
**When** a Service Advisor requests the customer snapshot  
**Then** the system returns the stale snapshot  
**And** the response includes `stale: true` and `staleSince` timestamp in metadata  
**And** a warning is logged for monitoring

---

### AC5: Missing Billing Rules Are Handled with Defaults

**Given** CRM returns a customer snapshot without billing rules  
**When** the system processes the response  
**Then** the system applies default billing rules (poRequired: false, taxExempt: false, paymentTerms: "Due on Receipt", etc.)  
**And** the response includes the defaults in the billingRules section  
**And** a warning is logged with the account ID for manual review

---

### AC6: Unauthorized Access Is Denied

**Given** a Service Advisor does not have permission to view customer account X  
**When** they request the customer snapshot for account X  
**Then** the system returns 403 Forbidden  
**And** the access attempt is logged with user ID, account ID, and timestamp

---

### AC7: Degraded Mode Behavior for Extended CRM Outage

**Given** CRM service is unavailable for more than 15 minutes  
**When** Service Advisor attempts to create a new order  
**Then** the system allows creating an order draft with minimal customer identifiers  
**And** the system blocks submission/checkout unless advisor provides required fields manually  
**And** the order is flagged as "CRM-DEGRADED"  
**And** a prominent alert/metric is emitted

---

## Audit & Observability

### Required Audit Events

1. **CustomerSnapshotRetrieved**
   - Timestamp, user ID, account ID, source (cache/CRM), response time, outcome (success/error)

2. **BillingRulesAccessed**
   - Timestamp, user ID, account ID, calling service, billing rule values

3. **CacheHit / CacheMiss**
   - Timestamp, account ID, cache key, TTL remaining (for hits), outcome

4. **CRMServiceUnavailable**
   - Timestamp, account ID, error message, retry count, degraded mode (yes/no)

5. **DefaultBillingRulesApplied**
   - Timestamp, account ID, reason (missing CRM data), default values applied

6. **UnauthorizedAccessAttempt**
   - Timestamp, user ID, account ID, requesting service, IP address

---

### Metrics to Track

- **Snapshot Retrieval Latency**: p50, p95, p99 response times (target: <2 seconds)
- **Cache Hit Rate**: Percentage of requests served from cache vs. CRM API (target: >80%)
- **CRM API Availability**: Uptime percentage and error rate
- **Stale Cache Usage**: Count of requests served with stale data (should be rare)
- **Default Billing Rules Applied**: Count per day (indicates missing CRM configuration)
- **Unauthorized Access Attempts**: Count per day (security monitoring)
- **Degraded Mode Operations**: Count of CRM-DEGRADED orders created

---

## Resolution Summary

All open questions (Q1-Q7) have been resolved with explicit decisions:

1. **Q1 - Complete billing rules schema**: Extended to include creditHold, invoiceDeliveryMethod, billingAddressId, autoPayEnabled, discountPolicyRef, currency, and extensions map
2. **Q2 - Caching strategy**: Redis with 15-min TTL, 60-min stale tolerance, event-driven invalidation + TTL safety net
3. **Q3 - Enforcement ownership**: This service provides read-only configuration; enforcement is caller's responsibility (Workexec, Billing, Pricing)
4. **Q4 - Preferred contact method**: Defined schema (PHONE, EMAIL, SMS, PORTAL, MAIL), priority logic, and per-contact scope
5. **Q5 - Vehicle detail level**: Lightweight identity + order-creation essentials only; full history fetched separately
6. **Q6 - Order creation integration**: Fetch on customer select, store snapshot ref in draft, explicit refresh points
7. **Q7 - Extended CRM outage**: Stale cache up to 60 minutes; fail-open for drafts, fail-closed for submission with manual override

**Status**: All clarifications resolved. Story is **READY FOR IMPLEMENTATION**.

---

## Original Story (Unmodified – For Traceability)

# Issue #4 — [BACKEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Customer: Load Customer + Vehicle Context and Billing Rules

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want a customer snapshot so that order creation respects billing rules and contacts.

## Details
- Show account, individuals, contacts, preferred contact method.
- Show vehicles and VIN/description.
- Show billing rules (PO required, tax exemption, terms).

## Acceptance Criteria
- Snapshot loads for selected customer.
- Billing rule enforcement hooks present.
- Cached/updated appropriately.

## Integrations
- CRM provides snapshot API; workexec can use same refs.

## Data / Entities
- CustomerSnapshot, VehicleRef, BillingRuleRef

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*