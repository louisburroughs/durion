Title: [BACKEND] [STORY] Contacts: Store Communication Preferences and Consent Flags
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/107
Labels: type:story, domain:crm, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- none

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As a **Customer Service Representative (CSR)**, I need to **accurately record and manage a customer's communication preferences and consent status** so that **we can engage with them through their preferred channels and comply with their explicit opt-in/opt-out choices**.

## Actors & Stakeholders
- **CSR (Customer Service Representative):** The primary actor who creates and updates communication preferences on behalf of a customer.
- **Customer:** The individual whose preferences and consent are being recorded.
- **System (CRM Service):** The authoritative system responsible for persisting, managing, and exposing this data.
- **Downstream Systems (e.g., Workorder Execution, Marketing Platform):** Systems that consume this data to determine how and when to communicate with the customer.

## Preconditions
- The CSR is authenticated and has the necessary permissions to manage customer contact records.
- The target `Customer` or `Contact` record already exists in the system.

## Functional Behavior

### 4.1. Record and Update Communication Preferences
- **Trigger:** A CSR, interacting with the system via an authorized client (e.g., POS UI, Admin Portal), initiates a request to update a specific customer's communication preferences.

- **Process:**
    1. The system must provide a mechanism to set or update the customer's `preferredCommunicationChannel`.
    2. The system must allow for the explicit recording of consent status (opt-in/opt-out) for distinct communication types, such as `emailMarketingConsent` and `smsNotificationConsent`.
    3. Upon a successful update, the system MUST automatically record the following metadata:
        - `updatedTimestampUTC`: The UTC timestamp of when the change was persisted.
        - `updateSource`: An identifier for the system or context that initiated the change (e.g., `CSR_PORTAL`, `API:WORKORDER_SYSTEM`).

### 4.2. Retrieve Communication Preferences
- **Trigger:** An authorized system or user requests the communication preferences for a specific customer.
- **Process:** The system exposes an API endpoint that, given a customer identifier, returns their complete and current communication preference and consent record.

## Alternate / Error Flows
- **Flow 1: Customer Not Found**
    - **Given:** A request is made to update or retrieve preferences for a `customerId` that does not exist.
    - **When:** The system processes the request.
    - **Then:** The system MUST respond with a `404 Not Found` status and a descriptive error message.

- **Flow 2: Invalid Input Data**
    - **Given:** A request is made with invalid data (e.g., an unrecognized `preferredChannel` value, a non-boolean consent flag).
    - **When:** The system validates the request payload.
    - **Then:** The system MUST respond with a `400 Bad Request` status and a message detailing the validation failure.

- **Flow 3: Unauthorized Access**
    - **Given:** A user or system without the required permissions attempts to modify preferences.
    - **When:** The system processes the request.
    - **Then:** The system MUST respond with a `403 Forbidden` status.

## Business Rules
- Consent and preferences are managed at the individual `Customer` / `Contact` level.
- A `null` or missing value for any consent flag MUST be interpreted as "Consent Not Given" (i.e., opt-out by default).
- The list of available communication channels (e.g., `EMAIL`, `SMS`, `PHONE`, `NONE`) is a controlled vocabulary managed by the CRM domain.
- The `updateSource` field is mandatory for every create or update operation to ensure a clear audit trail.
- Once granted, consent remains active until it is explicitly revoked (i.e., changed to an opt-out status).

## Data Requirements

The implementation must support a logical entity, `CommunicationPreference`, with the following attributes:

| Field Name | Type | Constraints | Description |
|---|---|---|---|
| `customerId` | UUID | FK, Not Null, Indexed | The unique identifier for the customer record. |
| `preferredCommunicationChannel` | Enum | Not Null | The customer's preferred channel. (Values: `EMAIL`, `SMS`, `PHONE`, `NONE`) |
| `emailMarketingConsent` | Boolean | Nullable | `true` for opt-in, `false` for opt-out, `null` for not specified. |
| `smsNotificationConsent` | Boolean | Nullable | `true` for opt-in, `false` for opt-out, `null` for not specified. |
| `updatedTimestampUTC` | DateTime (UTC) | Not Null | Timestamp of the last modification. |
| `updateSource` | String(50) | Not Null | Identifier for the client or process that made the change. |

## Acceptance Criteria

**AC-1: Successfully Record a Customer's Initial Preferences**
- **Given** a customer with ID `CUST-123` exists and has no previously recorded preferences.
- **When** a CSR submits a request to set `preferredCommunicationChannel` to `EMAIL`, `emailMarketingConsent` to `true`, and `smsNotificationConsent` to `false` via the `CSR_PORTAL`.
- **Then** the system successfully persists this new record for `CUST-123`, and a subsequent query for that customer returns the exact preferences and consent flags.
- **And** the record's `updateSource` is `CSR_PORTAL` and the `updatedTimestampUTC` is current.

**AC-2: Successfully Update an Existing Customer's Preferences**
- **Given** a customer with ID `CUST-456` has an existing preference record with `preferredCommunicationChannel` set to `EMAIL`.
- **When** a CSR submits a request to change the `preferredCommunicationChannel` to `SMS`.
- **Then** the system updates the record for `CUST-456` to reflect `preferredCommunicationChannel` as `SMS`.
- **And** all other fields (like consent flags) remain unchanged.
- **And** the `updatedTimestampUTC` is updated to the current time.

**AC-3: Retrieve Preferences for a Customer**
- **Given** a customer with ID `CUST-789` has their preferences stored in the system.
- **When** an authorized downstream system requests the preferences for `CUST-789`.
- **Then** the API returns a `200 OK` response containing the complete and correct `CommunicationPreference` data for that customer.

**AC-4: Default Consent is "Opt-Out"**
- **Given** a customer with ID `CUST-101` has a preference record where `smsNotificationConsent` is `null`.
- **When** a downstream system retrieves this record.
- **Then** the downstream system MUST interpret the `null` value as "consent not granted" and MUST NOT send SMS notifications.

## Audit & Observability
- **Audit Trail:** Every creation or modification of a `CommunicationPreference` record MUST generate a discrete audit event.
- **Event Payload:** The audit event must contain the `customerId`, the principal (user/system) performing the action, the `updateSource`, the state of the record *before* the change, and the state *after* the change.
- **Metrics:** The service should expose metrics for the number of preference updates, broken down by source.
- **Logging:** Failed update attempts due to validation, authorization, or other errors must be logged with a `WARN` or `ERROR` level, including relevant context like the `customerId` (if available) and the source of the request.

## Open Questions
- None at this time.

## Original Story (Unmodified – For Traceability)
# Issue #107 — [BACKEND] [STORY] Contacts: Store Communication Preferences and Consent Flags

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Contacts: Store Communication Preferences and Consent Flags

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to record preferred channel and opt-in/opt-out flags** so that **communications follow customer preferences**.

## Details
- Per-person preferences: preferred channel; basic consent flags for SMS/email.
- Track last-updated and source.

## Acceptance Criteria
- Can set/get preferences.
- Consent flags are available via API.
- Audit is captured.

## Integration Points (Workorder Execution)
- Workorder Execution uses preferences to select notification channel (stubbed initially).

## Data / Entities
- CommunicationPreference
- ConsentRecord

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