Title: [BACKEND] [STORY] Party: Associate Individuals to Commercial Account
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/110
Labels: backend, story-implementation, type:story, domain:crm, status:ready-for-dev, agent:story-authoring, agent:crm

## Story Intent
As a Fleet Account Manager, I want to link individuals to a commercial account with relationship roles and effective dates, so that work order approval and billing contacts are unambiguous for downstream processes.

## Actors & Stakeholders
- **Primary Actor:** Fleet Account Manager
- **Service Owner:** CRM domain/service (party + relationship master data)
- **Downstream Consumers:** Workorder Execution, Billing/Invoice workflows (read-only consumers)
- **Stakeholders:** Service Advisors, Billing Department, Commercial Account customers

## Preconditions
- Commercial account (Organization party) exists.
- Individual (Person party) exists.
- Actor is authenticated/authorized to manage the commercial account.
- Relationship roles are system-defined and validated (e.g., `APPROVER`, `BILLING`).

## Functional Behavior

### 1) Create party relationship
- User creates an association between an Organization and a Person with one or more roles and an `effectiveStartDate`.
- System persists an immutable/append-only history (no hard deletes).

### 2) Designate primary billing contact (Decision)
- **Rule:** Exactly one (1) primary billing contact per commercial account at a time.
- Assigning a new primary billing contact **atomically demotes** any existing primary billing contact.
- Primary billing contact must be an **active** relationship.
- If the primary relationship is deactivated, the account can have **no primary billing contact** until one is assigned.

### 3) Deactivate relationship
- Deactivation sets `effectiveEndDate` (logical end of validity).
- Deactivated relationships are excluded from “active contacts” reads.

## Alternate / Error Flows
- Party not found → `404`.
- Role invalid → `400`.
- Duplicate overlapping active relationship for same party pair + role → `409`.
- Unauthorized → `403`.

## Business Rules
- **Domain authority (Decision):** `domain:crm` is the system of record for party relationships; Payment/Billing/Workexec are consumers.
- Relationship uniqueness is based on (commercialAccount, individual, role, effective date range) with no overlapping active ranges.
- Effective dates determine active/inactive status; null end date means active.

## Data Requirements
- `PartyRelationship`:
  - `partyRelationshipId` (UUID)
  - `fromPartyId` (commercial account / org)
  - `toPartyId` (individual / person)
  - `roleType` (e.g., `APPROVER`, `BILLING`)
  - `isPrimaryBillingContact` (boolean; only meaningful for `BILLING` role)
  - `effectiveStartDate`, `effectiveEndDate`
  - audit fields (createdAt/By, updatedAt/By)

## Consumer API Contract (Decision)
CRM must expose a stable read API for consumers (default: real-time reads).

### Read endpoint (recommended)
`GET /crm/commercial-accounts/{commercialAccountId}/contacts`

- Query params:
  - `roles` (optional, comma-separated): `APPROVER,BILLING`
  - `status` (optional, default `ACTIVE`): `ACTIVE|INACTIVE`
  - `includeIndividuals` (optional, default `true`)

- Semantics:
  - **Active approvers** = contacts where `status=ACTIVE` and roles include `APPROVER`
  - **Billing contacts** = contacts where `status=ACTIVE` and roles include `BILLING`
  - **Primary billing contact** = billing contact with `isPrimaryBillingContact=true` (0 or 1 by rule)

## Acceptance Criteria
- Can create a relationship with role and effective start date.
- Can deactivate a relationship (sets end date; does not delete).
- Enforces single primary billing contact per account; assigning a new primary demotes the previous primary atomically.
- Exposes `GET /crm/commercial-accounts/{id}/contacts` returning active approvers/billing contacts and at most one primary billing contact.

## Audit & Observability
- Audit log all create/update/deactivate operations with actor and timestamps.
- Emit a distinct audit event when `isPrimaryBillingContact` changes.
- Metrics for relationships created/deactivated and primary billing changes.

## Open Questions
- None. Decisions were supplied in the issue comments (Decision Record dated 2026-01-14).

---
## Original Story (Unmodified – For Traceability)
# Issue #110 — [BACKEND] [STORY] Party: Associate Individuals to Commercial Account

## Current Labels
- backend
- story-implementation
- payment

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Party: Associate Individuals to Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link individuals to a commercial account with relationship types/roles** so that **workorder approval and billing contacts are unambiguous**.

## Details
- Relationship includes role(s) and effective dates.
- Allow one or more primary billing contacts per account.

## Acceptance Criteria
- Can create relationship with role.
- Primary billing contact can be designated.
- Relationship can be deactivated.

## Integration Points (Workorder Execution)
- Workorder Execution retrieves approvers/billing contacts for an account.

## Data / Entities
- PartyRelationship (roles, flags, effective dates)

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