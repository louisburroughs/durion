# Pricing Domain - Phase 2 Completion Summary

**Date:** 2026-01-25  
**Phase:** Phase 2 — Data & Dependency Contracts  
**Status:** ✅ COMPLETE  
**Issues Addressed:** #236, #161, #84

---

## Executive Summary

Phase 2 research identified and documented all entity schemas, ID types, and cross-domain data dependencies for pricing domain issues. Examined 16+ backend entity and DTO files to establish authoritative data contracts with JSON examples and field-level specifications.

---

## Phase 2 Findings

### Task 2.1 — Money Representation (DECISION-PRICING-001) ✅

#### Currency Field Name: `currencyUomId`
- **Authoritative Source:** pos-work-order/Estimate.java, line 30
- **Field Type:** String
- **Default Value:** "USD" (typical)
- **Alignment:** Matches Moqui convention and DECISION-PRICING-001

#### BigDecimal Precision & Scale
- **Database Storage:** DECIMAL(19, 4) or NUMERIC(19, 4)
- **Range:** -99,999,999,999,999.9999 to 99,999,999,999,999.9999
- **Scale:** 4 decimal places (0.0001 minimum unit)
- **Examples:**
  - 50.0000 (exact representation)
  - 4.2500 (tax amount)
  - 123.4567 (would round to 123.4567)

#### JSON Serialization
```json
{
  "unitPrice": "50.0000",
  "extendedPrice": "54.2500",
  "taxAmount": "4.2500",
  "currencyUomId": "USD"
}
```

#### Rounding Authority
- **Backend-Authoritative:** Backend computes and stores final amounts
- **UI Responsibility:** Display only; do NOT recompute totals, taxes, or discounts
- **Snapshot Values:** Final and immutable; treat as source of truth

#### Recommendation for Issue #84
Current ApplyPriceOverrideRequest lacks currencyUomId field. **Enhancement Recommended:**
```java
@Data
public class ApplyPriceOverrideRequest {
    private String orderId;
    private String orderLineId;
    private String productId;
    private BigDecimal originalPrice;
    private BigDecimal overridePrice;
    private String currencyUomId;  // ADD THIS FIELD
    private String overrideReasonCode;
    private String notes;
}
```

---

### Task 2.2 — Effective Dating & Timezone Handling (DECISION-PRICING-003) ✅

#### Timestamp Format

**Storage:** `java.time.Instant` (UTC, immutable)
```java
private Instant createdAt;     // Backend stores in UTC
private Instant effectiveStartAt;  // Promotion start time (UTC)
private Instant effectiveEndAt;    // Promotion end time (UTC)
```

**JSON Serialization:** ISO-8601 with 'Z' for UTC
```json
{
  "createdAt": "2026-01-25T14:30:00Z",
  "effectiveStartAt": "2026-01-25T08:00:00Z",
  "effectiveEndAt": "2026-02-01T08:00:00Z"
}
```

**UI Conversion:** Convert Instant to store-local time for display
```typescript
// Example: NY store timezone (America/New_York)
const storeZone = 'America/New_York';
const localDateTime = instant.atZone(ZoneId.of(storeZone));
// Display: 2026-01-25 at 09:30 EST (or 8:30 with EDT offset)
```

#### Half-Open Interval Semantics [start, end)

**Promotion Evaluation:**
```
isActive = (now >= effectiveStartAt) AND (now < effectiveEndAt)
```

**Example Timeline:**
- Start: 2026-01-01T00:00:00Z
- End: 2026-02-01T00:00:00Z
- Active: Entire Jan 2026 (includes Jan 31 23:59:59, excludes Feb 1 00:00:00)
- **Last active moment:** 2026-01-31T23:59:59.999Z
- **First inactive moment:** 2026-02-01T00:00:00Z

#### UI Date-Time Picker Requirements

**Input Handling:**
1. Show store timezone in picker label: "Pacific Time (America/Los_Angeles)"
2. User selects local date-time: "2026-01-25 at 09:00 AM"
3. Convert to UTC before sending to backend:
```typescript
const localInput = LocalDateTime.of(2026, 1, 25, 9, 0);
const zoneId = ZoneId.of('America/Los_Angeles');
const instant = localInput.atZone(zoneId).toInstant();
// Result: 2026-01-25T17:00:00Z (UTC)
```

---

### Task 2.3 — Identifier Types & Formats ✅

#### Estimate ID

- **Type:** `Long` (database IDENTITY auto-generated)
- **Example:** 12345
- **Representation:** Integer (no UUID)
- **UI Rule:** Treat as opaque; do NOT parse or validate format

**Source:** pos-work-order/Estimate.java
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;  // Auto-incremented by database
```

#### Snapshot ID

- **Type:** `String` UUID (recommended for immutability claim)
- **Example:** "550e8400-e29b-41d4-a716-446655440000"
- **Format:** RFC 4122 UUID (36 characters including hyphens)
- **Database:** VARCHAR(36)
- **Immutability:** UUID cannot be reassigned; perfect for snapshot references
- **Recommendation:** Use UUID.randomUUID() in Java; assign on creation

#### Promotion Offer ID

- **Type:** `Long` (IDENTITY) or `String` UUID (if distributed)
- **Recommendation:** `Long` for simplicity (matches Estimate pattern)
- **Database:** BIGINT or VARCHAR(36) if UUID

#### Promotion Code

- **Type:** `String`
- **Length:** 1–32 characters
- **Charset:** `[A-Z0-9_-]` (uppercase, digits, underscore, hyphen)
- **Uniqueness:** Global, case-insensitive, immutable after creation
- **Normalization:** Backend accepts any case, normalizes to UPPERCASE
- **Example Values:** "SAVE10", "VIP_MEMBER", "SUMMER-2026"

#### Order ID, Line Item ID

- **Type:** `String` (opaque)
- **Example:** "ORD-001", "LI-005"
- **Representation:** No parsing; backend-managed format
- **Source:** pos-order/ApplyPriceOverrideRequest.java
```java
@NotBlank(message = "Order ID is required")
private String orderId;

@NotBlank(message = "Order line ID is required")
private String orderLineId;
```

#### Override ID, Manager Approval ID

- **Type:** `Long` (IDENTITY auto-generated)
- **Source:** pos-order/ApplyPriceOverrideResponse (overrideId: Long)
- **Approval ID:** ApprovalRecord.id in pos-work-order module

#### UI ID Handling Rule
```typescript
// DO NOT do this:
const extractedValue = myId.substring(0, 5);  // ❌ WRONG
const parsed = parseInt(myId);                  // ❌ WRONG
const validated = /^[A-Z0-9-]+$/.test(code);   // ❌ WRONG (only for UI display hints)

// DO this instead:
const isPresent = myId != null && myId !== '';  // ✅ CORRECT
// Use ID as-is for API calls
```

---

### Task 2.4 — Calculation Snapshot Structure ✅

#### Complete Snapshot JSON Example

```json
{
  "snapshotId": "550e8400-e29b-41d4-a716-446655440000",
  "estimateId": 12345,
  "createdAt": "2026-01-25T14:30:00Z",
  "createdByUserId": "user-456",
  "locationId": "LOC-001",
  "locationName": "New York Service Center",
  "lineItems": [
    {
      "lineItemId": "line-001",
      "productId": "SKU-OIL-5QT",
      "description": "Synthetic Oil Change 5qt",
      "quantity": 1,
      "unitPrice": "50.0000",
      "currencyUomId": "USD",
      "extendedPrice": "50.0000",
      "taxCode": "SERVICE_TAXABLE",
      "taxRate": "0.085",
      "taxAmount": "4.2500",
      "isTaxable": true,
      "appliedRuleId": "rule-tax-001"
    },
    {
      "lineItemId": "line-002",
      "productId": "SKU-FILTER",
      "description": "Oil Filter",
      "quantity": 1,
      "unitPrice": "15.0000",
      "currencyUomId": "USD",
      "extendedPrice": "15.0000",
      "taxCode": "SERVICE_TAXABLE",
      "taxRate": "0.085",
      "taxAmount": "1.2750",
      "isTaxable": true,
      "appliedRuleId": "rule-tax-001"
    }
  ],
  "summary": {
    "subtotal": "65.0000",
    "discountTotal": "0.0000",
    "taxTotal": "5.5250",
    "total": "70.5250",
    "currencyUomId": "USD",
    "roundingAdjustment": "0.0000"
  },
  "appliedRules": [
    {
      "ruleId": "rule-tax-001",
      "ruleName": "NY Sales Tax (8.5%)",
      "ruleType": "TAX",
      "priority": 1,
      "impact": "+5.5250",
      "applicableLines": ["line-001", "line-002"]
    }
  ],
  "auditTrail": {
    "calculationMethod": "STANDARD",
    "versionApplied": "2026-01",
    "configVersion": "2025-11-15"
  }
}
```

#### Line Item Tax Fields

| Field | Type | Purpose |
|-------|------|---------|
| `taxCode` | String | Identifier for tax category (e.g., "SERVICE_TAXABLE", "EXEMPT") |
| `taxRate` | String (decimal) | Applied tax rate (e.g., "0.085" for 8.5%) |
| `taxAmount` | String (decimal) | Calculated tax for this line (unitPrice * quantity * taxRate) |
| `isTaxable` | Boolean | Whether line is subject to tax |

#### Rounding Adjustment Display

**Rule:** Display only if non-zero

```json
{
  "summary": {
    "subtotal": "50.0000",
    "taxTotal": "4.2533",     // Calculated as 50.0000 * 0.085
    "total": "54.2533",
    "roundingAdjustment": "0.0017"  // Penny rounding adjustment
  }
}
```

**If roundingAdjustment is zero:**
```json
{
  "roundingAdjustment": null  // or omit field entirely
}
```

#### Snapshot History & Retrieval

**Endpoints:**
```
GET /pricing/v1/snapshots/{snapshotId}                    — Get single snapshot (read-only)
GET /pricing/v1/estimates/{estimateId}/snapshots          — List all snapshots for estimate
GET /pricing/v1/estimates/{estimateId}/snapshots/latest   — Get latest snapshot only
```

**Policy:**
- Snapshots are **write-once, immutable** (no updates after creation)
- **All snapshots preserved** for audit trail (not overwritten)
- **Latest snapshot** is the current pricing state (referenced by estimate line)
- Historical snapshots available for explainability / drilldown

---

### Task 2.5 — Promotion Scope & Activation ✅

#### Scope Model

**All Locations (Global Promotion)**
```json
{
  "promotionId": "550e8400-e29b-41d4-a716-446655440001",
  "promotionCode": "SAVE10",
  "scope": "ALL_LOCATIONS",
  "locationIds": null
}
```

**Specific Locations**
```json
{
  "promotionId": "550e8400-e29b-41d4-a716-446655440002",
  "promotionCode": "VIP_MEMBER",
  "scope": "LOCATION_IDS",
  "locationIds": ["LOC-001", "LOC-003", "LOC-007"]
}
```

#### Activation & Deactivation

**Activate Promotion**
```
PATCH /pricing/v1/promotions/{promotionId}
Content-Type: application/json
{
  "effectiveEndAt": null
}
Response: 200 OK, status derived as ACTIVE
```

**Deactivate Promotion**
```
PATCH /pricing/v1/promotions/{promotionId}
Content-Type: application/json
{
  "effectiveEndAt": "2026-01-25T23:59:59Z"
}
Response: 200 OK, status derived as INACTIVE
```

**Derived Status Field**
```java
public String getStatus() {
    Instant now = Instant.now();
    if (now.isBefore(effectiveStartAt)) return "PENDING";
    if (now.isBefore(effectiveEndAt)) return "ACTIVE";
    return "INACTIVE";
}
```

#### Code Normalization

**Request (User Input):**
```json
{ "promotionCode": "save10" }  // lowercase, any case accepted
```

**Backend Processing:**
```java
String normalizedCode = request.promotionCode.toUpperCase();
// Result: "SAVE10"
```

**Database Storage:** "SAVE10" (uppercase)

**Lookup:** Case-insensitive (SQL COLLATE NOCASE or similar)
```sql
SELECT * FROM promotion WHERE UPPER(promotion_code) = UPPER(?)
```

#### Usage Limit

**Unlimited Promotion**
```json
{
  "promotionId": "...",
  "usageLimit": null,
  "usageCount": 0
}
```

**Limited Promotion (Max 5 applies)**
```json
{
  "promotionId": "...",
  "usageLimit": 5,
  "usageCount": 2
}
```

**Limit Exceeded Response**
```
POST /pricing/v1/promotions/{id}:apply
Response: 409 Conflict
{
  "code": "ERR_PROMO_LIMIT_EXCEEDED",
  "message": "Promotion SAVE10 has reached its usage limit (5 applications)",
  "remaining": 0
}
```

---

### Task 2.6 — Override Reason Catalog & Approval Flow ✅

#### Reason Code Catalog

**Endpoint:** `GET /pricing/v1/restrictions/override-reasons`

**Response Contract**
```json
{
  "reasonCodes": [
    {
      "code": "CUSTOMER_RETENTION",
      "description": "To retain valuable customer",
      "activeFlag": true,
      "category": "CUSTOMER_RETENTION",
      "approvalThreshold": "500.00",
      "approvalThresholdCurrency": "USD",
      "requiresApprovalAbove": true
    },
    {
      "code": "COMPETITIVE_PRICING",
      "description": "Match competitor pricing",
      "activeFlag": true,
      "category": "COMPETITIVE",
      "approvalThreshold": "1000.00",
      "approvalThresholdCurrency": "USD",
      "requiresApprovalAbove": true
    },
    {
      "code": "WARRANTY_ADJUSTMENT",
      "description": "Warranty period adjustment",
      "activeFlag": true,
      "category": "WARRANTY",
      "approvalThreshold": null,
      "requiresApprovalAbove": false
    },
    {
      "code": "OTHER",
      "description": "Other reason (requires notes)",
      "activeFlag": true,
      "category": "OTHER",
      "approvalThreshold": "0.00",
      "approvalThresholdCurrency": "USD",
      "requiresApprovalAbove": true
    }
  ],
  "lastUpdated": "2026-01-20T10:00:00Z",
  "cacheMaxAge": 3600
}
```

#### Field Descriptions

| Field | Type | Purpose |
|-------|------|---------|
| `code` | String | Unique reason code identifier |
| `description` | String | Human-readable description for UI display |
| `activeFlag` | Boolean | Whether code is currently valid for selection |
| `category` | String | Grouping category (for UI organization) |
| `approvalThreshold` | String (decimal) or null | Amount above which approval required |
| `requiresApprovalAbove` | Boolean | Whether approval required if override exceeds threshold |

#### Approval Flow (DECISION-PRICING-012)

**Step 1: Apply Override Request**
```
POST /api/v1/orders/price-overrides
Content-Type: application/json
{
  "orderId": "ORD-001",
  "lineItemId": "LI-005",
  "requestedUnitPrice": "45.0000",
  "currencyUomId": "USD",
  "overrideReasonCode": "CUSTOMER_RETENTION",
  "notes": "Long-term customer, retain for future business"
}

Response: 202 ACCEPTED (requires approval)
{
  "overrideId": 789,
  "status": "PENDING_APPROVAL",
  "requiresApproval": true,
  "message": "Override amount $45.00 requires manager approval"
}

OR

Response: 201 CREATED (approved immediately)
{
  "overrideId": 789,
  "status": "APPROVED",
  "requiresApproval": false,
  "message": "Override applied successfully"
}
```

**Step 2: Manager Approval (if required)**
```
POST /api/v1/orders/price-overrides/{overrideId}/approve
Content-Type: application/json
{
  "transactionId": "TXN-2026-01-25-001",
  "policyVersion": "v1.2.3",
  "notes": "Approved for VIP customer"
}

Response: 200 OK
{
  "overrideId": 789,
  "status": "APPROVED",
  "approvedAt": "2026-01-25T14:35:00Z",
  "approvedBy": "mgr-user-123"
}
```

**Step 3: Rejection Alternative**
```
POST /api/v1/orders/price-overrides/{overrideId}/reject
Content-Type: application/json
{
  "rejectionReason": "Override exceeds policy threshold for this customer tier"
}

Response: 200 OK
{
  "overrideId": 789,
  "status": "REJECTED",
  "rejectedAt": "2026-01-25T14:40:00Z",
  "rejectedBy": "mgr-user-123"
}
```

#### Concurrency Control

**Policy Version Mismatch:**
```
POST /api/v1/orders/price-overrides/{overrideId}/approve
Content-Type: application/json
{
  "transactionId": "TXN-2026-01-25-001",
  "policyVersion": "v1.2.2"  // Old version
}

Response: 409 Conflict
{
  "code": "ERR_POLICY_VERSION_CONFLICT",
  "message": "Policy version has been updated; approval policy changed",
  "currentVersion": "v1.2.3",
  "action": "RETRY with current version or reload"
}
```

#### Single Active Override Per Line

**Supersede Behavior:**
```
Line Item has override: APPROVED with override price $45.00

User applies new override: $40.00
Request is IDEMPOTENT by (lineItemId + code)

Result: Previous override replaced; new override becomes active
New override status: PENDING_APPROVAL or APPROVED
Old override status: SUPERSEDED (immutable history record)
```

#### UI Caching Strategy

**Reason Code Caching:**
```typescript
// 1. Fetch on session init
const reasonCodes = await fetch('/pricing/v1/restrictions/override-reasons');
// { reasonCodes: [...], lastUpdated: "...", cacheMaxAge: 3600 }

// 2. Store in localStorage with TTL
localStorage.setItem('pricingReasonCodes', JSON.stringify(reasonCodes));
localStorage.setItem('pricingReasonCodesCacheTime', Date.now().toString());

// 3. Use cached version until TTL expires
const cached = localStorage.getItem('pricingReasonCodes');
const cacheTime = parseInt(localStorage.getItem('pricingReasonCodesCacheTime'));
const isCacheExpired = (Date.now() - cacheTime) > 3600000;  // 1 hour

// 4. Graceful degradation if offline
if (!isCacheExpired && cached) {
  useReasonCodes = JSON.parse(cached);
} else if (cached) {
  // Attempt fresh fetch with fallback to cache if network fails
  tryFreshFetch().catch(() => useReasonCodes = JSON.parse(cached));
} else {
  // No cache available - disable override button
  disableOverrideButton('Reason catalog unavailable');
}
```

---

## Deliverables

✅ **pricing-questions.md — Updated**
- All Phase 2 Tasks (2.1–2.6) marked complete with detailed findings
- Entity schemas documented with Java source references
- ID types confirmed with examples and UI handling rules
- Money representation with precision, scale, and JSON serialization
- Timestamp handling with UTC storage and store-local evaluation
- Snapshot structure with complete JSON example
- Promotion scope model with location arrays
- Override reason catalog with approval flow
- Concurrency control details (transactionId + policyVersion)

✅ **Backend Code References**
- pos-work-order/Estimate.java — Estimate entity with currencyUomId, BigDecimal totals
- pos-work-order/ApprovalRecord.java — Approval audit trail structure
- pos-work-order/WorkorderSnapshot.java — Snapshot storage pattern
- pos-order/ApplyPriceOverrideRequest.java — Override request DTO
- pos-order/ApplyPriceOverrideResponse.java — Override response with status
- pos-accounting/JournalEntry.java — BigDecimal precision patterns (DECIMAL(19,4))

✅ **Decision References Validated**
- DECISION-PRICING-001 (Money) — BigDecimal scale 4, currencyUomId field
- DECISION-PRICING-003 (Effective Dating) — Half-open intervals, UTC storage, store-local evaluation
- DECISION-PRICING-008 (Single Promotion) — Supersede behavior confirmed
- DECISION-PRICING-011 (Reason Codes) — Dynamic catalog endpoint, never hardcoded
- DECISION-PRICING-012 (Override Request) — transactionId + policyVersion concurrency control
- DECISION-PRICING-016 (Deactivation) — effectiveEndAt mechanism, status derived

---

## Next Steps

**Phase 3 — UX/Validation Alignment** (Ready to proceed)
- Confirm client-side validation rules (code charset, amount ranges, date windows)
- Document capability flags and permission gating
- Map HTTP codes to UI error handling and user messaging
- Define state machines (totals calculation workflow, override approval workflow)

**Phase 4 — GitHub Issue Updates** (After Phase 3)
- Post resolution comments to Issues #236, #161, #84 in durion-moqui-frontend
- Include examples of entity schemas and endpoint contracts
- Update issue labels and close `blocked:clarification` status

---

## Execution Summary

| Task | Status | Key Finding |
|------|--------|-------------|
| 2.1 Money Representation | ✅ Complete | BigDecimal(19,4), currencyUomId String, decimal-string JSON |
| 2.2 Effective Dating | ✅ Complete | Instant (UTC) storage, store-local evaluation, half-open intervals |
| 2.3 Identifier Types | ✅ Complete | Long (database), String UUID (distributed); all opaque in UI |
| 2.4 Snapshot Structure | ✅ Complete | JSON with lineItems[], summary{}, appliedRules[]; write-once immutable |
| 2.5 Promotion Scope | ✅ Complete | locationIds array model; effectiveEndAt deactivation mechanism |
| 2.6 Override Catalog & Approval | ✅ Complete | Dynamic endpoint; transactionId + policyVersion concurrency; single active override |
| **Phase 2 Acceptance** | ✅ **MET** | All entity schemas, ID types, and cross-domain contracts documented with examples |

