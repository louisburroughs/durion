# CRM Cross-Domain Integration Contracts

**Version:** 1.0  
**Status:** DRAFT  
**Last Updated:** 2026-01-23  
**Audience:** CRM domain, Billing domain, People/Contact domain, Vehicle Fitment domain

---

## Overview

This document defines the API contracts between the CRM domain and external domains (Billing, People/Contacts, Vehicle Fitment) that are required to implement CRM stories. Each contract specifies the service/endpoint, request/response schema, error handling, and usage context.

---

## Table of Contents

1. [Billing Domain – Billing Terms Lookup](#billing-domain--billing-terms-lookup)
2. [People/Contact Domain – Contact Points and Delivery Methods](#peopleconta domain--contact-points-and-delivery-methods)
3. [Vehicle Fitment Domain (pos-vehicle-fitment)  – VIN Validation and Decode (Future)](#vehicle-fitment-domain--vin-validation-and-decode-future)
4. [Integration Guidelines](#integration-guidelines)
5. [Status Tracking](#status-tracking)

---

## Billing Domain – Billing Terms Lookup

### Context & Use Case

CRM Issue #176 ("Create Commercial Account") requires:

- Display a dropdown/select of available billing terms when creating a new party/account
- Store the selected `billingTermsId` on the party record
- Validate that the selected billing terms ID exists in the Billing domain

### Required Contract

**Service Name (Proposed):**

```
billing.list#BillingTerms  (Groovy service in Moqui)
or
GET /v1/billing/terms      (REST endpoint)
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `status` | String (enum) | No | Filter by status: `ACTIVE`, `INACTIVE`, `ARCHIVED`. Default: `ACTIVE` only. |
| `pageNumber` | Integer | No | 1-indexed page number. Default: 1. |
| `pageSize` | Integer | No | Results per page. Default: 50. |

**Response DTO:**

```java
@Data
@Builder
public class ListBillingTermsResponse {
    private List<BillingTermsSummary> results;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalCount;
    private Boolean hasMore;

    @Data
    @Builder
    public static class BillingTermsSummary {
        private String billingTermsId;              // PK, string UUID or code
        private String label;                       // Display label (e.g., "Net 30", "Net 60", "COD")
        private String description;                 // Optional longer description
        private Integer netDays;                    // Net period in days (e.g., 30 for "Net 30")
        private Boolean isDefault;                  // true if default for new accounts
        private BillingTermsStatus status;          // ACTIVE | INACTIVE | ARCHIVED
        private Instant createdAt;                  // ISO 8601 UTC
    }
}

public enum BillingTermsStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
```

**Error Codes & HTTP Status:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `BILLING_SERVICE_UNAVAILABLE` | 503 Service Unavailable | Billing service not responding |
| `INVALID_STATUS_FILTER` | 400 Bad Request | Invalid status enum value |

**Example Request (REST):**

```http
GET /v1/billing/terms?status=ACTIVE&pageSize=100
Authorization: Bearer <jwt_token>
Accept: application/json
```

**Example Response:**

```json
{
  "results": [
    {
      "billingTermsId": "BT-NET30",
      "label": "Net 30",
      "description": "Payment due 30 days from invoice date",
      "netDays": 30,
      "isDefault": true,
      "status": "ACTIVE",
      "createdAt": "2025-06-15T10:00:00Z"
    },
    {
      "billingTermsId": "BT-NET60",
      "label": "Net 60",
      "description": "Payment due 60 days from invoice date",
      "netDays": 60,
      "isDefault": false,
      "status": "ACTIVE",
      "createdAt": "2025-06-15T10:00:00Z"
    },
    {
      "billingTermsId": "BT-COD",
      "label": "Cash on Delivery",
      "description": "Payment required at time of service",
      "netDays": 0,
      "isDefault": false,
      "status": "ACTIVE",
      "createdAt": "2025-06-15T10:00:00Z"
    }
  ],
  "pageNumber": 1,
  "pageSize": 100,
  "totalCount": 3,
  "hasMore": false
}
```

### Get Single Billing Terms (for Validation)

**Service Name (Proposed):**

```
billing.get#BillingTerms
or
GET /v1/billing/terms/{billingTermsId}
```

**Response DTO:** Same as `BillingTermsSummary` above.

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `BILLING_TERMS_NOT_FOUND` | 404 Not Found | Billing terms ID does not exist |
| `BILLING_TERMS_INACTIVE` | 400 Bad Request | Billing terms is inactive/archived |

**Example:**

```http
GET /v1/billing/terms/BT-NET30
Authorization: Bearer <jwt_token>
Accept: application/json

Response 404:
{
  "errorCode": "BILLING_TERMS_NOT_FOUND",
  "message": "Billing terms 'INVALID-ID' does not exist",
  "timestamp": 1706020200000
}
```

### Integration Pattern

**CRM Create Commercial Account Workflow:**

1. **Frontend:** Calls `crm.listCustomers()` or similar to display billing terms dropdown
   - Internally calls `billing.list#BillingTerms` (or REST GET /v1/billing/terms)
2. **Form Submission:** User selects `billingTermsId` and submits party creation
3. **CRM Backend Validation:**
   - Calls `billing.get#BillingTerms` to validate selected ID exists and is ACTIVE
   - If 404 or INACTIVE, reject with 400 Bad Request + appropriate error message
   - If valid, store `billingTermsId` on party record
4. **Error Handling:** Display user-friendly error ("Invalid or inactive billing terms") to frontend

### Ownership & Implementation

- **Owned By:** Billing domain
- **Status:** **TBD – Need Billing domain spec**
- **Dependencies:** None initially; may integrate with accounting reference data later

---

## People/Contact Domain – Contact Points and Delivery Methods

### Context & Use Case

CRM Issue #172 ("Maintain Contact Roles") requires:

- Load contacts for a party with `invoiceDeliveryMethod` (EMAIL, MAIL, etc.)
- Validate that each contact has appropriate contact points (e.g., email address for EMAIL delivery method)
- Enforce "billing contact must have primary email" rule
- Display `hasPrimaryEmail` flag without exposing full PII contact list

### Required Contract

**Service Name (Proposed):**

```
contact.list#ContactPointTypes
or
GET /v1/people/contactPointTypes
```

**Description:** List valid contact point types and their delivery method mappings.

**Response DTO:**

```java
@Data
@Builder
public class ContactPointTypesResponse {
    private List<ContactPointTypeInfo> contactPointTypes;

    @Data
    @Builder
    public static class ContactPointTypeInfo {
        private String contactPointType;           // e.g., "EMAIL", "PHONE", "PHYSICAL_ADDRESS"
        private String label;                      // Display label
        private List<String> applicableDeliveryMethods;  // e.g., ["EMAIL_DELIVERY"] for "EMAIL"
        private Boolean required;                  // true if billing contact must have this type
        private String validationPattern;          // Regex or format hint for validation
    }
}
```

**Example Response:**

```json
{
  "contactPointTypes": [
    {
      "contactPointType": "EMAIL",
      "label": "Email Address",
      "applicableDeliveryMethods": ["EMAIL_DELIVERY"],
      "required": true,
      "validationPattern": "^[^@]+@[^@]+\\.[^@]+$"
    },
    {
      "contactPointType": "PHONE",
      "label": "Phone Number",
      "applicableDeliveryMethods": ["SMS_DELIVERY", "VOICE_DELIVERY"],
      "required": false,
      "validationPattern": "^\\+?1?\\d{9,15}$"
    },
    {
      "contactPointType": "PHYSICAL_ADDRESS",
      "label": "Mailing Address",
      "applicableDeliveryMethods": ["MAIL_DELIVERY"],
      "required": false,
      "validationPattern": null
    }
  ]
}
```

---

### Get Contact with Delivery Method & hasPrimaryEmail Flag

**Service Name (Proposed):**

```
contact.get#ContactWithDeliveryMethod
or
GET /v1/people/contacts/{contactId}
```

**Description:** Load contact with delivery method preference and `hasPrimaryEmail` flag (without exposing full PII).

**Response DTO:**

```java
@Data
@Builder
public class ContactWithDeliveryMethodResponse {
    private String contactId;
    private String contactName;                   // First + Last (no email/phone in main response)
    private String invoiceDeliveryMethod;        // EMAIL, MAIL, EMAIL_AND_MAIL, etc.
    private Boolean hasPrimaryEmail;             // true if contact has primary email
    private Boolean hasPhoneNumber;              // true if contact has phone
    private List<AssignedRole> roles;            // From CRM

    @Data
    @Builder
    public static class AssignedRole {
        private String roleCode;                  // BILLING, APPROVER, DRIVER
        private String roleLabel;
        private Boolean isPrimary;
    }
}
```

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `CONTACT_NOT_FOUND` | 404 Not Found | Contact ID does not exist |
| `PEOPLE_SERVICE_UNAVAILABLE` | 503 Service Unavailable | People service not responding |

**Integration Pattern:**

**CRM Get Contacts Workflow:**

1. **CRM Backend:** When loading contacts for a party, calls `contact.get#ContactWithDeliveryMethod` for each contact
2. **Validation Rule:** If party has BILLING role contact, verify `hasPrimaryEmail == true`
   - If false, return 400 Bad Request with error message "Billing contact must have a primary email address"
3. **UI Display:** Show contacts with roles and delivery methods without exposing raw email/phone

### Ownership & Implementation

- **Owned By:** People/Contacts domain
- **Status:** **TBD – Need People domain spec**
- **Dependencies:** Contact point types (part of same service)

---

## Vehicle Fitment Domain – VIN Validation and Decode (Future)

### Context & Use Case

CRM Issue #169 ("Create Vehicle Record") may require:

- Validate VIN format (17-character alphanumeric)
- Decode VIN to extract:
  - Vehicle make and model
  - Year
  - Country of origin
- Match decoded vehicle data against fitment database (optional, for inventory/service planning)

### Planned Contract

**Service Name (Proposed):**

```
vehicle-fitment.decode#VIN
or
POST /v1/vehicle-fitment/vin/decode
```

**Request:**

```json
{
  "vin": "1HGCM82633A123456",
  "includeInventoryMatch": false
}
```

**Response DTO:**

```java
@Data
@Builder
public class VINDecodeResponse {
    private String vin;
    private String make;                        // e.g., "Honda"
    private String model;                       // e.g., "Civic"
    private String year;                        // e.g., "2019"
    private String bodyType;                    // e.g., "Sedan", "Coupe"
    private String countryOfOrigin;             // ISO 3166 code
    private Boolean isDecoded;                  // true if successfully decoded
    private List<InventoryMatch> inventoryMatches;  // If includeInventoryMatch=true
}
```

**Error Codes:**

| Error Code | HTTP Status | Meaning |
|------------|-------------|---------|
| `INVALID_VIN_FORMAT` | 400 Bad Request | VIN is not 17 characters or invalid format |
| `VIN_NOT_FOUND_IN_DATABASE` | 404 Not Found | VIN cannot be decoded (not in database) |
| `VEHICLE_FITMENT_SERVICE_UNAVAILABLE` | 503 Service Unavailable | Service not responding |

### Status

- **Status:** **NOT REQUIRED FOR MVP** (CRM Issue #169 marked "clarification" not "blocking")
- **Defer Until:** Vehicle Fitment domain API is defined
- **Note:** VIN validation (format check) can be done in CRM backend without this service

---

## Integration Guidelines

### Error Handling & Resilience

**Pattern 1: Required Service (Billing Terms)**

- If Billing service is unavailable, return 503 Service Unavailable to CRM frontend
- Log error for monitoring/alerting
- Do **not** allow party creation with invalid billing terms

**Pattern 2: Optional Service (Vehicle Fitment VIN Decode)**

- If Vehicle Fitment service is unavailable, allow VIN creation (format-only validation)
- Log warning for monitoring
- Display banner to user: "VIN validation unavailable, please verify manually"

### Timeout & Retry Strategy

| Service | Timeout | Retry | Max Duration |
|---------|---------|-------|--------------|
| Billing (list/get) | 5s | 2 retries with 1s backoff | 7s total |
| People/Contact (get) | 3s | 1 retry with 500ms backoff | 4s total |
| Vehicle Fitment (decode) | 10s | 0 retries | 10s total (optional, fire-and-forget) |

### Permission Model

- **Billing Terms Listing:** Visible to anyone with `crm:party:create` or `crm:party:view`
- **Contact Delivery Method:** Visible to anyone with `crm:contact:view`
- **VIN Decode:** Visible to anyone with `crm:vehicle:create`

### Service-to-Service Authentication

All cross-domain calls use:

- **JWT Bearer token** forwarded from user session (preferred)
- **Or:** Service-to-service OAuth2 credentials if user context is unavailable
- Document which pattern is used per service

### Caching Strategy

| Service | Cache TTL | Cache Key |
|---------|-----------|-----------|
| Billing Terms List | 24 hours | `billing:terms:{status}:{pageSize}` |
| Billing Terms Get | 24 hours | `billing:terms:{id}` |
| Contact Point Types | 7 days | `contact:pointTypes` |
| VIN Decode | 0 (no cache) | N/A – decode on-demand |

**Invalidation:** Clear cache when domain publishes update events (billing terms changed, contact types updated, etc.)

---

## Status Tracking

### Issue #176: Create Commercial Account

**Dependencies:**

- [x] CRM backend service implementation (Spring Boot PartyService)
- [x] CRM Groovy service wrapper
- [ ] **BLOCKING:** Billing domain provides `billing.list#BillingTerms` API contract
- [ ] Billing terms validation in CRM backend

**Action:** Billing domain to confirm contract by **2026-02-15**

---

### Issue #172: Maintain Contact Roles

**Dependencies:**

- [x] CRM backend service implementation (GetContactsWithRoles)
- [x] CRM Groovy service wrapper
- [ ] **BLOCKING:** People domain provides `contact.get#ContactWithDeliveryMethod` API contract
- [ ] Contact delivery method loading in CRM backend

**Action:** People domain to confirm contract by **2026-02-15**

---

### Issue #169: Create Vehicle Record

**Dependencies:**

- [x] CRM backend service implementation (CreateVehicleForParty)
- [x] CRM Groovy service wrapper
- [ ] **OPTIONAL:** Vehicle Fitment domain provides `vehicle-fitment.decode#VIN` for future use
- [ ] VIN format validation implemented (CRM-level)

**Action:** Vehicle Fitment domain to confirm contract by **2026-03-01** (non-blocking)

---

## Related Documents

- [CRM Backend Contract Guide](./BACKEND_CONTRACT_GUIDE.md)
- [CRM Domain Notes](./DOMAIN_NOTES.md)
- [Billing Domain Notes](../../billing/.business-rules/DOMAIN_NOTES.md)
- [People Domain Notes](../../people/.business-rules/DOMAIN_NOTES.md)

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-23 | Initial draft; Billing Terms and Contact delivery method contracts |
