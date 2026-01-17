Title: [BACKEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/99
Labels: type:story, domain:crm, status:ready-for-dev, agent:story-authoring, agent:crm, agent:workexec, agent:billing

## Story Intent
As a consuming system (initially Workorder Execution, and also Billing), I need a stable, efficient, CRM-owned read endpoint to retrieve a point-in-time snapshot of core customer relationship data (Account/Party, Contacts, Vehicles, and contactability + billing-relevant preferences), so downstream workflows can operate quickly and consistently without re-deriving CRM association rules.

## Actors & Stakeholders
- **Provider / SoR:** CRM service (`domain:crm`) owns the API and contract.
- **Consumers:** Workorder Execution (`domain:workexec`), Billing (`domain:billing`).
- **Indirect user:** Service Advisor (benefits from faster, consistent estimate creation).

## Preconditions
- Caller is service-to-service authenticated.
- Caller is authorized by **both**:
  - allowlisted service identity, and
  - required scope: `crm.snapshot.read`
- Request includes at least one identifier: `partyId` or `vehicleId`.

## Functional Behavior
### Endpoint
- `GET /v1/crm-snapshot`
- Query params:
  - `partyId` (optional)
  - `vehicleId` (optional)
- Rule: request MUST include at least one of `partyId` or `vehicleId`.

### Snapshot Retrieval (partyId)
- If `partyId` is provided, CRM returns a snapshot for that party/account, including:
  - account summary
  - active contacts (with one primary contact indicated)
  - associated vehicles (active)
  - preferences (v1 scope defined below)

### Snapshot Retrieval (vehicleId-only)
When only `vehicleId` is provided, CRM MUST resolve a single “primary party” deterministically:
1. Find all **active vehicle-party relationships** for the vehicle.
2. Select primary party by precedence:
   1. `PRIMARY_OWNER` (ACTIVE)
   2. else `OWNER` (ACTIVE; most recent)
   3. else `LESSEE/DRIVER` (ACTIVE; most recent)
   4. else any ACTIVE relationship (most recent)
3. Tie-breaker for “most recent”: latest `effectiveFrom`, then latest `updatedAt`.
4. If **no active relationships**, return `404` with error code `VEHICLE_HAS_NO_ACTIVE_PARTY`.
5. Return contacts:
   - all active contacts for the resolved primary party/account
   - mark one as `isPrimary=true` using CRM’s primary-contact flag; if none exists, choose most recently updated contact.

## Alternate / Error Flows
- Missing identifiers (`partyId` and `vehicleId` both absent) → `400 Bad Request`
- Party not found (invalid `partyId`) → `404 Not Found`
- Vehicle not found (invalid `vehicleId`) → `404 Not Found`
- Vehicle has no active party relationship → `404 Not Found` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`
- Unauthorized (valid auth, but not allowlisted and/or missing scope) → `403 Forbidden`
- Unexpected aggregation failure → `500 Internal Server Error` with correlation ID logged

## Business Rules
- **Domain ownership (Decision):** `domain:crm` owns this read API as a CRM read model. Billing/Workexec are consumers.
- **Contract stability:** response schema is versioned (`/v1/...`) and changes must be backward compatible within a version.
- **Preferences scope (Decision, v1):** “rules and preferences” means **contactability + billing-relevant preferences only**; explicitly exclude Workexec/Billing workflow/policy rules.
- **Authorization (Decision):** NOT open to “any internal caller”; restricted to allowlisted service identities + required scope.

## Data Requirements
### Response (v1) — shape (illustrative)
```json
{
  "snapshotMetadata": {
    "snapshotId": "uuid",
    "createdAt": "ISO-8601-Timestamp",
    "version": "string | ETag"
  },
  "account": {
    "partyId": "uuid",
    "accountNumber": "string",
    "accountName": "string",
    "accountType": "INDIVIDUAL | BUSINESS"
  },
  "contacts": [
    {
      "contactId": "uuid",
      "isPrimary": true,
      "name": "string",
      "roles": ["string"],
      "phoneNumbers": [{ "type": "MOBILE", "number": "string" }],
      "emailAddresses": [{ "type": "PRIMARY", "address": "string" }],
      "preferences": {
        "emailOptIn": true,
        "smsOptIn": false,
        "phoneOptIn": true,
        "doNotContact": false,
        "preferredContactMethod": "EMAIL | SMS | PHONE | NONE",
        "preferredLanguage": "string"
      }
    }
  ],
  "vehicles": [
    {
      "vehicleId": "uuid",
      "vin": "string",
      "licensePlate": "string",
      "make": "string",
      "model": "string",
      "year": 2024
    }
  ],
  "preferences": {
    "marketingOptOut": false,
    "doNotContact": false,
    "invoiceDeliveryMethod": "EMAIL | MAIL | NONE"
  }
}
```

## Acceptance Criteria
- **AC-1: Successful snapshot retrieval by partyId**
  - Given a valid `partyId` exists with contacts and vehicles
  - When calling `GET /v1/crm-snapshot?partyId={partyId}`
  - Then the service returns `200 OK` with the correct snapshot DTO and `snapshotMetadata.version`.

- **AC-2: Successful snapshot retrieval by vehicleId using deterministic party resolution**
  - Given a valid `vehicleId` exists with active relationships
  - When calling `GET /v1/crm-snapshot?vehicleId={vehicleId}`
  - Then the service returns `200 OK`
  - And selects the primary party using precedence `PRIMARY_OWNER > OWNER > LESSEE/DRIVER > any active` with “most recent” tie-breaker.

- **AC-3: Vehicle has no active party relationship**
  - Given a valid `vehicleId` exists but has no active party relationship
  - When calling `GET /v1/crm-snapshot?vehicleId={vehicleId}`
  - Then the service returns `404 Not Found` with `errorCode=VEHICLE_HAS_NO_ACTIVE_PARTY`.

- **AC-4: Missing identifier rejected**
  - Given any state
  - When calling `GET /v1/crm-snapshot` with no query params
  - Then the service returns `400 Bad Request`.

- **AC-5: Authorization is allowlist + scope enforced**
  - Given a valid service token
  - When the caller is not allowlisted and/or missing `crm.snapshot.read`
  - Then the service returns `403 Forbidden`.

- **AC-6: Preferences scope (v1) is limited**
  - Given a valid snapshot request
  - Then the response contains only contactability + billing-relevant preferences
  - And does not contain Workexec/Billing workflow/policy rules.

## Audit & Observability
- **Audit:** log caller identity + queried identifiers + timestamp.
- **Rate limiting:** per-caller rate limits to protect CRM.
- **Metrics:** request count, latency, and status codes, tagged by caller identity.
- **Logging:** structured logs with correlation IDs for all errors (4xx/5xx).

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Record generated by `clarification-resolver.sh` on 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #99 — [BACKEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Workorder System**, I want **a single endpoint to fetch account/person, contacts, vehicles, rules, and preferences** so that **estimate creation can be fast and consistent**.

## Details
- Endpoint accepts partyId and/or vehicleId.
- Returns normalized snapshot with version/timestamp.
- Cache-friendly response model.

## Acceptance Criteria
- Returns expected fields.
- Includes timestamps/version.
- Not-found handled cleanly.

## Integration Points (Workorder Execution)
- Workorder Execution calls snapshot at estimate draft and on-demand refresh.

## Data / Entities
- CRM Snapshot DTO / API

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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