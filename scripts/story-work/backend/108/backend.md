Title: [BACKEND] [STORY] Contacts: Maintain Contact Roles and Primary Flags
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/108
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
- **Candidate Primary Domains:** `domain:crm`, `domain:payment`
- **Why conflict was detected:** The story's core responsibility—managing contact roles like 'driver' and 'approver'—is characteristic of a Customer Relationship Management (CRM) or People domain. However, the original story was explicitly labeled with `domain:payment` and mentions billing contacts, creating ambiguity about which domain is the authoritative system of record for this data.
- **What must be decided:**
    1.  Which domain is the primary owner and system of record for a Customer's Contact Roles?
    2.  What is the inter-domain contract for how other domains (like Payment/Billing or Work Execution) consume this information?
- **Recommended split:** Yes. The core management of contacts and their general roles should be a `domain:crm` story. A separate, dependent story in `domain:payment` or `domain:billing` could then consume these roles and enforce billing-specific rules.

---
STOP: Clarification required before finalization

## Story Intent
As a system, I must provide the capability to assign specific, persistent roles (e.g., Billing, Approver, Driver) to a customer's contacts and designate one contact as the primary for a given role. This ensures that downstream processes, such as invoice delivery and work order approvals, can reliably identify and communicate with the correct individual for their specific function.

## Actors & Stakeholders
- **CSR (Customer Service Representative)**: The primary user who manages contact information and role assignments on behalf of a customer.
- **System**: The POS platform responsible for persisting and validating contact role data.
- **Downstream Consumers**:
    - **Billing/Invoicing System**: Consumes `Billing` contact information for invoice delivery.
    - **Work Order Execution System**: Consumes `Approver` contact information for estimate and work approvals.

## Preconditions
- A Customer Account exists in the system.
- One or more Contact records are associated with the Customer Account.
- The CSR is authenticated and has the necessary permissions to modify customer account data.
- A centrally defined list of valid `ContactRole`s is available to the system.

## Functional Behavior
### Scenario: Assigning and Managing Contact Roles
1.  **Trigger**: A CSR initiates a request via an API endpoint to update the roles for a specific contact associated with a customer account.
2.  **Process**:
    - The system validates that the `ContactID`, `CustomerAccountID`, and all specified `RoleName`s are valid.
    - The system assigns one or more roles to the contact (e.g., 'Billing', 'Driver').
    - If the request designates a contact as 'Primary' for a role (e.g., Primary Billing Contact), the system enforces the 'single primary per role' rule. If another contact was the previous primary for that role, its primary status for that role is revoked.
    - The system persists the `ContactRoleAssignment` records, linking the contact, account, and role, including the `isPrimary` flag.
3.  **Outcome**: The contact's roles are updated in the system of record. An event, such as `CustomerContactRolesUpdated`, is published to notify downstream systems of the change.

### Scenario: Enforcing Invoicing Configuration Rules
1.  **Trigger**: A CSR attempts to modify contact roles for a customer account that is configured for email-based invoice delivery.
2.  **Process**: The system checks if the proposed change would result in the customer account having zero contacts with the 'Billing' role.
3.  **Outcome (Success)**: If at least one 'Billing' contact remains, the change is saved successfully.
4.  **Outcome (Failure)**: If the change would remove the last 'Billing' contact, the system rejects the transaction and returns a validation error explaining that at least one billing contact is required for email invoicing.

## Alternate / Error Flows
- **Invalid Role**: If the request contains a role name that does not exist in the system's list of valid roles, the API call is rejected with a `400 Bad Request` error.
- **Invalid Contact/Account**: If the specified `ContactID` or `CustomerAccountID` does not exist, the API call is rejected with a `404 Not Found` error.
- **Primary Flag without Role**: If a request attempts to set the `isPrimary` flag for a role that is not also being assigned to the contact in the same transaction, the request is rejected with a `400 Bad Request` error.
- **Authorization Failure**: If the authenticated user (CSR) does not have permission to modify the specified customer account, the request is rejected with a `403 Forbidden` error.

## Business Rules
- A single contact can be assigned multiple roles simultaneously.
- For any given Customer Account and `RoleName` (e.g., 'Billing'), only one contact can be designated as the `Primary`.
- The list of available roles (`BILLING`, `APPROVER`, `DRIVER`) is centrally managed and enumerable.
- **Configurable Rule**: When a Customer Account's invoice delivery method is set to 'EMAIL', the system must enforce that at least one associated contact has the `BILLING` role.

## Data Requirements
- **`ContactRoleAssignment`**:
    - `contactId`: Foreign key to the Contact entity.
    - `customerAccountId`: Foreign key to the Customer Account entity.
    - `roleName`: (Enum/String) The role being assigned (e.g., `BILLING`, `APPROVER`).
    - `isPrimary`: (Boolean) Indicates if this contact is the primary for this role within the account.
    - Primary Key: (`contactId`, `customerAccountId`, `roleName`).
    - Unique Constraint: (`customerAccountId`, `roleName`) where `isPrimary` is true.

## Acceptance Criteria
**AC-1: Assign Multiple Roles to a Contact**
- **Given** a customer account with a contact named "Jane Doe" who has no assigned roles.
- **When** a CSR sends an API request to assign the roles 'Billing' and 'Approver' to "Jane Doe".
- **Then** the system successfully saves the assignments, and a subsequent query for "Jane Doe's" roles returns both 'Billing' and 'Approver'.

**AC-2: Designate a Primary Billing Contact**
- **Given** a customer account with contacts "Jane Doe" and "John Smith", both having the 'Billing' role, and neither being primary.
- **When** a CSR sends an API request to designate "Jane Doe" as the primary billing contact.
- **Then** the system updates "Jane Doe" to be the primary billing contact, and "John Smith" remains a non-primary billing contact.

**AC-3: Change the Primary Billing Contact**
- **Given** "Jane Doe" is the primary billing contact for an account.
- **When** a CSR sends an API request to designate "John Smith" as the new primary billing contact.
- **Then** the system updates "John Smith" to be the primary, and automatically removes the primary flag from "Jane Doe".

**AC-4: Enforce Required Billing Contact Rule**
- **Given** a customer account is configured for email invoicing and "Jane Doe" is the only contact with the 'Billing' role.
- **When** a CSR attempts to remove the 'Billing' role from "Jane Doe".
- **Then** the system rejects the request with a validation error message indicating a billing contact is required.

**AC-5: Prevent Assigning an Invalid Role**
- **Given** a customer account with a contact.
- **When** a CSR attempts to assign an invalid role named "Shareholder" to the contact.
- **Then** the system rejects the request with a `400 Bad Request` error.

## Audit & Observability
- **Audit Log**: Every creation, modification, or deletion of a `ContactRoleAssignment` must be logged, including `customerAccountId`, `contactId`, `roleName`, the change made, the `timestamp`, and the `userId` of the CSR who performed the action.
- **Domain Event**: Upon any successful change to a contact's roles for an account, the system must publish a `CustomerContactRolesUpdated` event. The event payload should contain the `customerAccountId` and a list of all current role assignments for all contacts on that account to allow for stateless consumption.

## Open Questions
1.  **[BLOCKER] Domain Ownership**: Which domain, `CRM` or `Payment`, is the authoritative system of record for managing contact roles? This decision dictates the service boundary and API ownership.
2.  **Definitive Role List**: What is the complete, definitive list of contact roles required at launch? Is this list expected to be static or will it require management capabilities in the future?
3.  **Primary Designation Logic**: The story assumes that designating a new primary contact for a role automatically demotes the old one. Please confirm this is the desired behavior. The alternative is to reject the request if a primary already exists, forcing a two-step (demote, then promote) process.
4.  **Rule Configuration Scope**: Is the rule "enforce at least one billing contact for email invoicing" a global system setting, or can it be configured on a per-customer-account basis?

## Original Story (Unmodified – For Traceability)
# Issue #108 — [BACKEND] [STORY] Contacts: Maintain Contact Roles and Primary Flags

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Contacts: Maintain Contact Roles and Primary Flags

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to set contact roles (billing, approver, driver) and primary flags** so that **the correct person is used in approvals and invoicing**.

## Details
- Allow multiple roles per person/account.
- Optionally enforce at least one billing contact when invoice delivery is email.

## Acceptance Criteria
- Can assign roles.
- Primary billing contact can be designated.
- Validation enforced when configured.

## Integration Points (Workorder Execution)
- Estimate approval uses approver role.
- Invoice delivery uses billing contact.

## Data / Entities
- ContactRole
- PartyRelationship

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