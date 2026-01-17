Title: [BACKEND] [STORY] Contacts: Capture Multiple Contact Points
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/106
Labels: type:story, domain:crm, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:crm
- status:draft
- blocked:clarification

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** crm-pragmatic
---

## Story Intent
As a Customer Service Representative (CSR), I want to capture and manage multiple, labeled contact points (i.e., email addresses and phone numbers) for a customer, so that I and other system users can reliably contact the customer using the most appropriate method for a given context.

## Actors & Stakeholders
- **CSR (Customer Service Representative) (Primary Actor)**: Creates, reads, updates, and deletes customer contact information.
- **Customer (Subject)**: The person or account whose contact information is being stored.
- **Workorder Execution System (Secondary Actor/Consumer)**: Reads customer contact information to facilitate operational communication (e.g., sending approvals, notifications, and invoices).

## Preconditions
- The CSR is authenticated and authorized to manage customer records.
- A customer record (e.g., Person or Account) exists in the system to which contact points can be associated.

## Functional Behavior
### 4.1. Creating Contact Points
A CSR can add a new `ContactPoint` to a customer record. When adding a `ContactPoint`, the CSR must specify:
- **Kind**: The type of contact point (`PHONE` or `EMAIL`).
- **Value**: The actual phone number or email address.
- **Label**: A usage descriptor for the contact point (e.g., `WORK`, `HOME`, `MOBILE`).
- **Is Primary**: A boolean flag indicating if this is the primary contact point for its kind.

### 4.2. Managing the Primary Contact Point
The system will enforce a "single primary per kind" rule for each customer.
- If a new `ContactPoint` is added and marked as primary, any existing primary `ContactPoint` of the same kind (e.g., `PHONE`) for that customer will automatically be demoted (its `isPrimary` flag will be set to `false`).
- The same demotion logic applies when an existing, non-primary `ContactPoint` is updated to become the primary.

### 4.3. Updating and Deleting Contact Points
- A CSR can modify the `value`, `label`, or `isPrimary` status of any existing `ContactPoint`.
- A CSR can permanently remove a `ContactPoint` from a customer's record.

## Alternate / Error Flows
- **Invalid Format**: If a user attempts to save a `ContactPoint` with a `value` that does not conform to a valid format for its `kind` (e.g., an invalid email address format), the system will reject the change and provide a user-friendly error message.
- **Duplicate Entry**: If a user attempts to add a `ContactPoint` that is an exact duplicate (same `customerId`, `kind`, and `value`) of an existing one, the system will prevent the creation and notify the user.
- **Deleting the Primary Contact**: If the primary `ContactPoint` for a kind is deleted, no other `ContactPoint` of that kind is automatically promoted to primary. The customer will simply have no primary contact for that kind until one is explicitly set.

## Business Rules
- A customer can have zero or many `ContactPoint` records.
- Each `ContactPoint` must belong to exactly one customer.
- Each `ContactPoint` must have a `kind` from the controlled vocabulary: `PHONE`, `EMAIL`.
- Each `ContactPoint` may have a `label` from the controlled vocabulary: `WORK`, `HOME`, `MOBILE`, `OTHER`.
- For any given customer, there can be at most one `ContactPoint` with `isPrimary=true` for the `PHONE` kind.
- For any given customer, there can be at most one `ContactPoint` with `isPrimary=true` for the `EMAIL` kind.

## Data Requirements
The implementation will require a `ContactPoint` entity with the following attributes:

| Field         | Type      | Description                                                 | Constraints               |
|---------------|-----------|-------------------------------------------------------------|---------------------------|
| `id`          | UUID      | Unique identifier for the contact point record.             | Primary Key, Not Null     |
| `customerId`  | UUID      | Foreign key to the associated customer record.              | Foreign Key, Not Null     |
| `kind`        | Enum      | The type of contact point. (`PHONE`, `EMAIL`)               | Not Null                  |
| `label`       | Enum      | The usage context. (`WORK`, `HOME`, `MOBILE`, `OTHER`)      | Nullable                  |
| `value`       | String    | The contact detail (e.g., "555-123-4567", "a@b.com").       | Not Null, Validated       |
| `isPrimary`   | Boolean   | True if this is the default contact of its kind.            | Not Null, Default: `false`|

## Acceptance Criteria

### AC-1: Add a New Contact Point
- **Given** a customer record exists
- **When** a CSR adds a new `PHONE` contact point with the label `MOBILE` and `isPrimary` as `false`
- **Then** the customer record is updated to include the new contact point with the correct details.

### AC-2: Set the First Primary Contact Point
- **Given** a customer has no primary `EMAIL` contact point
- **When** a CSR adds a new `EMAIL` contact point and marks it as primary
- **Then** that `EMAIL` is stored as the customer's primary email, with `isPrimary` set to `true`.

### AC-3: Change the Primary Contact Point
- **Given** a customer has an existing primary `PHONE` contact point (Phone A)
- **When** a CSR updates a different phone contact point (Phone B) to be the new primary
- **Then** Phone B's `isPrimary` flag is set to `true`
- **And** Phone A's `isPrimary` flag is automatically set to `false`.

### AC-4: Remove a Contact Point
- **Given** a customer has at least two `EMAIL` contact points
- **When** a CSR removes one of the `EMAIL` contact points
- **Then** the customer record no longer contains the removed contact point.

### AC-5: Reject Invalid Format
- **Given** a CSR is adding a new `EMAIL` contact point
- **When** they enter an invalid value (e.g., "invalid-email-address") and attempt to save
- **Then** the system rejects the operation and displays a "Invalid email format" error message.

## Audit & Observability
- **Audit Logging**: Any CUD (Create, Update, Delete) operation on a `ContactPoint` record must trigger an audit event. The event must log the `customerId`, the `ContactPoint` ID, the change details (including before/after state for updates), the ID of the CSR performing the action, and a timestamp.
- **Integration Logging**: All API requests from consumer systems (e.g., Workorder Execution) to retrieve contact data must be logged, including the consumer identity and the `customerId` requested.

## Open Questions
- **OQ1: Model Confirmation**: The proposed model separates `kind` (`PHONE`, `EMAIL`) from `label` (`WORK`, `HOME`, `MOBILE`). Please confirm this separation is correct and that the proposed list of labels is sufficient. Should this list of labels be configurable?
- **OQ2: Primary Demotion Rule**: The proposed behavior is that setting a new primary `ContactPoint` of a certain `kind` (e.g., `PHONE`) automatically demotes the previous primary of the same `kind` for that customer. Please confirm this "single primary per kind" rule is the desired business logic.

---
## Original Story (Unmodified – For Traceability)
# Issue #106 — [BACKEND] [STORY] Contacts: Capture Multiple Contact Points

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Contacts: Capture Multiple Contact Points

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to store multiple phone numbers and emails per person/account** so that **I can reach them via the right contact point**.

## Details
- Support type tags: work, mobile, home.
- Basic formatting validation.

## Acceptance Criteria
- Add/update/remove contact points.
- Identify primary contact point per type.

## Integration Points (Workorder Execution)
- Workorder Execution displays contact points during approval/invoice delivery.

## Data / Entities
- ContactPoint (type, value, primaryFlag)

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