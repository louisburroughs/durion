Title: [BACKEND] [STORY] Party: Create Commercial Account
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/112
Labels: payment, type:story, status:ready-for-dev

STOP: Conflicting domain guidance detected

## 🏷️ Labels (Proposed)

### Required
- type:story
- status:needs-review
- blocked:domain-conflict

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:domain-conflict

**Rewrite Variant:** integration-conservative

## ⚠️ Domain Conflict Summary
- **Candidate Primary Domains:** `domain:crm`, `domain:payment`, `domain:billing`
- **Why conflict was detected:** The issue has an existing `payment` label, but the core functionality—creating and managing a customer entity—is a canonical responsibility of the `domain:crm`. The mention of "billing profile" and downstream "invoices" also introduces `domain:billing` as a stakeholder, creating ambiguity about data ownership.
- **What must be decided:** The authoritative System of Record for the Commercial Account entity must be explicitly declared. The data ownership and inter-domain contract for billing-related information stored on the account must be defined.
- **Recommended split:** No, the creation of a single account entity should remain atomic. The ownership, not the functionality, needs to be clarified.

---
STOP: Clarification required before finalization

## Story Intent
**As a** Fleet Account Manager,
**I need to** create and manage a definitive record for a commercial customer, including their legal identity, billing preferences, and unique business identifiers.
**So that** all subsequent transactions, such as work orders and invoices, are accurately associated with the correct business entity, preventing billing errors and streamlining account management.

## Actors & Stakeholders
- **Fleet Account Manager**: The primary user who creates and manages commercial customer accounts.
- **System (CRM Domain)**: The system responsible for owning, creating, and storing the authoritative customer account record.
- **System (Workorder Execution Domain)**: A downstream consumer that needs to search for and associate work orders with an account.
- **System (Billing/Invoicing Domain)**: A downstream consumer that uses the account's billing profile and identifiers to generate invoices.

## Preconditions
- The Fleet Account Manager is authenticated and has the necessary permissions (`account:create`) to create commercial accounts.
- The core CRM service is available and operational.
- Dependent services for retrieving data like "Billing Terms" are available.

## Functional Behavior
1.  **Trigger**: The Fleet Account Manager initiates the "Create Commercial Account" action from the POS or back-office interface.
2.  **Data Capture**: The system presents a form to capture the commercial account's details. Required fields (e.g., Legal Name, Default Billing Terms) are clearly marked.
3.  **Validation**: Upon submission, the system performs server-side validation on all submitted data for presence, format, and validity (e.g., Tax ID format).
4.  **Duplicate Detection**: Before creating the record, the system executes a duplicate check against existing accounts based on a predefined set of rules (e.g., normalized Legal Name and primary contact phone/email).
5.  **Conflict Resolution**:
    - If potential duplicates are found, the system presents a non-blocking warning to the user, listing the matching accounts.
    - The user is given the choice to review the potential duplicates, cancel the creation, or explicitly override the warning and proceed with creating a new account.
6.  **Persistence**: Upon successful validation and user confirmation (including any override), the system:
    - Generates a new, unique, and immutable system-wide identifier (`PartyID`) for the account.
    - Persists the Commercial Account record to the database.
    - Sets the initial `AccountStatus` to 'Active'.
7.  **Confirmation**: The system confirms the successful creation and returns the new `PartyID` and account details to the user interface.
8.  **Event Publication**: An event, such as `CommercialAccountCreated`, is published to the event stream containing the new account's ID and core data for consumption by downstream domains.

## Alternate / Error Flows
- **Validation Failure**: If any required field is missing or data is improperly formatted, the system rejects the submission and returns a clear error message indicating which fields need correction. The user's entered data is preserved in the form.
- **Duplicate Check Failure**: If the duplicate check service is unavailable or times out, the system should fail gracefully, either by preventing creation with a "System temporarily unavailable" message or by proceeding with a logged warning (pending policy decision).
- **Creation Canceled**: If the user cancels the operation at any point before final confirmation, no account is created, and no data is persisted.
- **Persistence Failure**: If the database write fails, the system returns a generic server error message, and a critical error is logged. The transaction is rolled back to ensure data integrity.

## Business Rules
- `LegalName` is a mandatory, non-empty string.
- `DefaultBillingTerms` is a mandatory selection from a centrally managed list of billing terms.
- `AccountStatus` must default to `Active` upon creation.
- The duplicate check logic is defined as: a case-insensitive match on `LegalName` AND an exact match on a primary contact phone number OR email address.
- The system MUST NOT prevent a user from creating an account if they choose to override a duplicate warning, but this override action MUST be explicitly audited.

## Data Requirements
The `CommercialAccount` entity will be created with the following attributes:

| Field Name              | Data Type                         | Constraints                        | Description                                                              |
| ----------------------- | --------------------------------- | ---------------------------------- | ------------------------------------------------------------------------ |
| `PartyID`               | UUID                              | Primary Key, Not Null, Immutable   | The unique, system-generated identifier for the account.                 |
| `LegalName`             | String(255)                       | Not Null                           | The official legal name of the business entity.                          |
| `DoingBusinessAs`       | String(255)                       | Nullable                           | The "DBA" or trade name of the business, if different from the legal name. |
| `TaxIdentifier`         | String(50)                        | Nullable, Unique (if provided)     | The business's tax ID (e.g., EIN, VAT number). Format may be validated.  |
| `AccountStatus`         | Enum                              | Not Null, Default: `Active`        | The current status of the account (`Active`, `Inactive`, `OnHold`).      |
| `DefaultBillingTermsID` | UUID                              | Not Null, Foreign Key              | A reference to the identifier of the selected billing terms entity.      |
| `ExternalIdentifiers`   | JSONB / Key-Value Map             | Nullable                           | A flexible store for identifiers from external systems (e.g., `{"erpId": "CUST12345"}`). |
| `CreatedAt`             | Timestamp                         | Not Null, System-managed           | Timestamp of when the record was created.                                |
| `CreatedBy`             | UUID                              | Not Null, System-managed           | The user ID of the person who created the account.                       |
| `UpdatedAt`             | Timestamp                         | Not Null, System-managed           | Timestamp of the last update to the record.                              |
| `UpdatedBy`             | UUID                              | Not Null, System-managed           | The user ID of the person who last updated the account.                  |

## Acceptance Criteria

### Scenario 1: Successful Creation of a Commercial Account
**Given** an authenticated Fleet Account Manager with account creation permissions
**When** they submit the form to create a new commercial account with all required fields (Legal Name, Default Billing Terms)
**Then** the system successfully creates a new Commercial Account record
**And** the account is assigned a unique, immutable `PartyID`
**And** the account's status is set to `Active`
**And** a `CommercialAccountCreated` event is published.

### Scenario 2: Creation Attempt with Missing Required Fields
**Given** an authenticated Fleet Account Manager
**When** they attempt to submit the new commercial account form without providing a `LegalName`
**Then** the system rejects the request
**And** returns a validation error message specifying that `LegalName` is required
**And** no new account record is created.

### Scenario 3: Duplicate Warning is Triggered and Overridden
**Given** an existing commercial account named "Global Transport Inc."
**And** an authenticated Fleet Account Manager
**When** they submit a new account form with the `LegalName` "Global Transport Inc." and a matching phone number
**Then** the system presents a warning indicating a potential duplicate exists, showing details of the existing account
**And** the user chooses to override the warning and proceed
**Then** a new, distinct Commercial Account record is created for "Global Transport Inc."
**And** an audit event is logged for the duplicate warning override.

### Scenario 4: Created Account is Searchable
**Given** a new commercial account for "Rapid Logistics" with `PartyID` 'abc-123' has just been successfully created
**When** a downstream system, like Workorder Execution, immediately queries the CRM for an account by `PartyID` 'abc-123' or name "Rapid Logistics"
**Then** the query successfully returns the newly created account's data.

## Audit & Observability
- **Audit Event**: A `CommercialAccount.Created` event must be logged in the audit trail. The event payload must include the new `PartyID`, all initial field values, the `CreatedBy` user ID, and the timestamp.
- **Audit Event (Override)**: If a user overrides a duplicate warning, a separate `CommercialAccount.DuplicateWarningOverridden` audit event must be logged, referencing the new `PartyID` and the `PartyID`(s) of the potential duplicates that were shown.
- **Logging**: All steps in the creation process (request received, validation, duplicate check, persistence) must be logged with structured context, including a unique correlation ID.
- **Metrics**: The service must expose metrics for:
    - `commercial_account_creation_success_total` (counter)
    - `commercial_account_creation_failure_total` (counter, with reason e.g., 'validation', 'persistence')
    - `commercial_account_creation_duration_seconds` (histogram)

## Open Questions
1.  **Domain Ownership**: The story is labeled `payment` but describes a core `CRM` function. Which domain is the primary owner and System of Record for the Commercial Account (Party) entity? All subsequent design depends on this decision.
2.  **Billing Profile Contract**: What specific fields constitute the "default billing terms"? Is this just a reference ID to an entity owned by the `Billing` domain, or does some billing data (e.g., payment method type) get cached within the CRM record? Please define the precise data contract and ownership.
3.  **Duplicate Detection Logic**: The story mentions a "basic duplicate warning." The proposed rule is "(normalized name) AND (phone OR email)". Is this the correct and final logic? What normalization should be applied to the name (e.g., remove "Inc.", "LLC")?
4.  **External ID Management**: How should `ExternalIdentifiers` be managed? Is it a free-form key-value pair, or should the "keys" (e.g., `erpId`) be constrained to a predefined list of external systems?

## Original Story (Unmodified – For Traceability)
# Issue #112 — [BACKEND] [STORY] Party: Create Commercial Account

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Party: Create Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to create a commercial customer account with legal name, billing profile, and identifiers** so that **workorders and invoices can be consistently tied to the correct business entity**.

## Details
- Capture: legal name, DBA, tax ID (optional), account status, default billing terms.
- Support external identifiers (ERP/customer number) as optional fields.
- Basic duplicate warning on create (name + phone/email match).

## Acceptance Criteria
- Can create account with required fields.
- Account has stable CRM ID.
- Duplicate warning presented when close matches exist.

## Integration Points (Workorder Execution)
- Workorder Execution can search/select the account by name/ID.
- Selected account CRM ID is stored on Estimate/Workorder.

## Data / Entities
- Party/Account
- ExternalId (optional)
- Audit fields

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