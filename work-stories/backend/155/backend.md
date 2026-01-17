Title: [BACKEND] [STORY] Execution: Apply Role-Based Visibility in Execution UI
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/155
Labels: type:story, domain:workexec, status:needs-review

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** workexec-structured
---

## Story Intent
As a **System Administrator**, I want **to configure and apply role-based visibility policies to work order financial data**, so that **sensitive information like costs and pricing is hidden from unauthorized roles (e.g., Mechanics), protecting business margins and simplifying the user interface for technicians.**

## Actors & Stakeholders
- **Mechanic / Technician**: The primary user role whose view of a work order will be restricted.
- **Service Advisor / Back Office User**: User roles that require full visibility into financial data for quoting, invoicing, and reporting.
- **System Administrator**: The user role responsible for configuring the visibility policies that define what each role can see.
- **System**: The software actor responsible for identifying the user's role and enforcing the corresponding visibility policy at the API/data-access layer.
- **Stakeholders**:
    - **Finance Department**: Interested in protecting sensitive margin and cost data.
    - **Service Management**: Interested in operational efficiency and ensuring technicians have a streamlined, role-appropriate user experience.

## Preconditions
- A user is authenticated and has a clearly defined role (e.g., `ROLE_MECHANIC`, `ROLE_SERVICE_ADVISOR`).
- A Work Order exists in the system with associated line items containing financial data (e.g., `unitPrice`, `cost`).
- A source of truth for visibility policies exists, which maps user roles to permissions for viewing specific entities and fields.

## Functional Behavior
1.  **Trigger**: An authenticated user with an assigned role (e.g., `Mechanic`) requests to view a `Workorder` or its `WorkorderItem`s via a UI or API endpoint.
2.  The `Work Execution` service receives the request and identifies the user's role(s) from the security context.
3.  The service consults the authoritative `VisibilityPolicy` source to determine the specific viewing rules applicable to the user's role(s) for financial fields on the `Workorder` and `WorkorderItem` entities.
4.  The service constructs the response DTO (Data Transfer Object), omitting or nullifying any fields restricted by the policy (e.g., `unitPrice`, `extendedPrice`, `cost`, `margin`).
5.  The service returns the filtered work order data to the client (UI or API caller).
6.  The underlying `Workorder` and `WorkorderItem` records in the database remain unchanged, preserving all financial data for authorized processes like invoicing and reporting.

- Policy retrieval and caching from Security service
- Permission scope validation per request
- Dynamic DTO field filtering based on permissions
- Error handling for insufficient permissions

## Alternate / Error Flows
- **Role Misconfiguration / Policy Not Found**: If a visibility policy cannot be found for the user's role, the system **MUST** default to the most restrictive view (hiding all sensitive financial fields). An error **MUST** be logged for administrative review.
- **Unauthorized Direct API Access**: If a user attempts to directly query an API endpoint that is not intended for their role (if separate endpoints exist), the request **MUST** be rejected with a `403 Forbidden` status.

## Business Rules
- Financial data (cost, price, margin) **MUST** be considered sensitive and subject to role-based access control.
- The principle of "least privilege" **MUST** be applied: users should only see the data essential for performing their role-specific duties.
- The visibility policy **MUST** be enforced consistently across all relevant API endpoints and UI screens within the Work Execution domain.
- Hiding a field from view **MUST NOT** alter or remove the underlying data from the system of record. Financial truth must be preserved.
- A default "safe" policy (hide all sensitive fields) **MUST** be applied if a specific role's policy is not configured or fails to load.

### BR-POLICY-1: Security Service as Authoritative Source
The Security/Policy service is the single source of truth for all visibility policies and permission scopes. No other service may define or modify visibility policies. Domain services enforce policies but do not author them.

**Rationale:** Centralized policy management ensures consistency, auditability, and simplifies compliance.

### BR-POLICY-2: Domain Service Enforcement with Caching
Domain services (e.g., Workexec) SHALL:
- Retrieve visibility policies from the Security service via API
- Cache policies locally with a short TTL (recommended: 5-15 minutes)
- Subscribe to policy change events from Security service for immediate cache invalidation
- Perform server-side enforcement on every API request
- NEVER rely solely on client-side filtering for security

**Rationale:** Caching improves performance while short TTL and event-based invalidation ensure policy freshness.

### BR-RBAC-1: Explicit RBAC Scope Pattern
All permission scopes SHALL follow the pattern: `domain:resource:action`

Examples:
- `workexec:workorder:view` - Can view work order basic data
- `workexec:workorder:view-pricing` - Can view pricing fields
- `workexec:workorder:view-cost` - Can view cost fields
- `workexec:workorder:view-labor` - Can view labor details
- `workexec:workorder:edit` - Can edit work order

**Rationale:** Explicit, structured scopes provide fine-grained control and are self-documenting.

### BR-RBAC-2: Role-to-Permission Decoupling
Application code SHALL:
- Check permission scopes (e.g., `hasPermission('workexec:workorder:view-pricing')`)
- NEVER check role names directly (e.g., AVOID `if (role == 'MANAGER')`)

The Security service maintains the mapping: Role → List of Permission Scopes.

**Rationale:** Decoupling allows permission changes without code changes and supports flexible role definitions.

### BR-API-1: Single Endpoint with Dynamic Filtering
The Workexec API SHALL use a single endpoint pattern (e.g., `GET /api/workorders/{id}`) that:
- Accepts a standard request format
- Checks the caller's permission scopes
- Returns a DTO with fields filtered based on those scopes
- Omits fields the caller is not authorized to view

**Rationale:** Single endpoint simplifies client code, API versioning, and reduces maintenance burden.

### BR-API-2: Audit Trail Requirements
Every API request that involves visibility filtering SHALL generate an audit event containing:
- **requestId**: Unique identifier for the request
- **userId**: ID of the user making the request
- **timestamp**: UTC timestamp of the request
- **endpoint**: API endpoint accessed
- **resourceId**: ID of the resource accessed (e.g., work order ID)
- **permissionScopes**: List of permission scopes checked
- **fieldsVisible**: List of fields included in the response
- **fieldsFiltered**: List of fields excluded due to permissions
- **result**: SUCCESS or DENIED

**Rationale:** Comprehensive audit trails support compliance, troubleshooting, and security monitoring.

## Data Requirements
- **Entity**: `WorkorderItem`
    - **Fields to be controlled**: `unitPrice`, `extendedPrice`, `cost`, `margin`. These fields must exist on the entity and be populated.
- **Entity (Dependency)**: `User` / `SecurityPrincipal`
    - **Fields required**: `userId`, `roleId` (or an equivalent collection of assigned roles).
- **Entity (Dependency)**: `VisibilityPolicy`
    - **Purpose**: Defines which roles can view which fields on which entities.
    - **Required Attributes (Conceptual)**: `roleId`, `entityName` (e.g., 'WorkorderItem'), `fieldName` (e.g., 'cost'), `canView` (boolean).

- VisibilityPolicy entity (owned by Security service)
- VisibilityPolicyCache entity (in domain service for caching)
- Permission scope definitions (domain:resource:action)
- Audit event structure for visibility filtering

## Data Model Changes

### VisibilityPolicy (Owned by Security Service)
This entity is defined and managed by the Security service, NOT the Workexec domain.

```json
{
  "policyId": "uuid",
  "domain": "workexec",
  "resource": "workorder",
  "action": "view-pricing",
  "scope": "workexec:workorder:view-pricing",
  "description": "Permission to view pricing fields on work orders",
  "fieldMapping": {
    "unitPrice": true,
    "lineTotal": true,
    "tax": true,
    "discount": true
  },
  "effectiveDate": "2026-01-01T00:00:00Z",
  "expirationDate": null,
  "isActive": true,
  "createdAt": "2025-12-01T00:00:00Z",
  "updatedAt": "2025-12-15T00:00:00Z"
}
```

### VisibilityPolicyCache (In Workexec Domain for Caching)
```json
{
  "cacheId": "uuid",
  "policyId": "uuid (from Security service)",
  "scope": "workexec:workorder:view-pricing",
  "fieldMapping": {
    "unitPrice": true,
    "lineTotal": true,
    "tax": true,
    "discount": true
  },
  "cachedAt": "2026-01-11T10:00:00Z",
  "expiresAt": "2026-01-11T10:15:00Z",
  "version": "v1.2.3"
}
```

### VisibilityAuditEvent (In Workexec Domain for Audit Logging)
```json
{
  "eventId": "uuid",
  "requestId": "uuid",
  "userId": "user-123",
  "timestamp": "2026-01-11T10:30:00Z",
  "endpoint": "/api/workorders/wo-456",
  "resourceId": "wo-456",
  "resourceType": "workorder",
  "permissionScopes": [
    "workexec:workorder:view",
    "workexec:workorder:view-labor"
  ],
  "fieldsVisible": [
    "id", "status", "customerName", "vehicleVIN", 
    "laborItems", "laborHours", "mechanicName"
  ],
  "fieldsFiltered": [
    "unitPrice", "cost", "margin", "discount"
  ],
  "result": "SUCCESS"
}
```
## Acceptance Criteria
### AC-1: Mechanic view is restricted
- **Given** a user is logged in with the "Mechanic" role
- **And** a `WorkorderItem` exists with a `unitPrice` of 100, `cost` of 50, and `margin` of 50
- **When** the mechanic requests the details of that `WorkorderItem`
- **Then** the API response data **MUST NOT** contain the `unitPrice`, `cost`, or `margin` fields (or they must be null)
- **And** the underlying database record for the `WorkorderItem` **MUST** still contain the original financial data.

### AC-2: Back Office view is unrestricted
- **Given** a user is logged in with the "Service Advisor" role, which has full visibility
- **And** a `WorkorderItem` exists with a `unitPrice` of 100, `cost` of 50, and `margin` of 50
- **When** the service advisor requests the details of that `WorkorderItem`
- **Then** the API response data **MUST** contain the correct `unitPrice`, `cost`, and `margin` values.

### AC-3: System defaults to secure on misconfiguration
- **Given** a user is logged in with a "New Temporary Role" that has no defined visibility policy
- **And** a `WorkorderItem` exists with cost, price, and margin data
- **When** the user requests the details of that `WorkorderItem`
- **Then** the system **MUST** apply the default restrictive policy
- **And** the API response data **MUST NOT** contain any sensitive financial fields.

### AC-POLICY-1: Security Service Provides Visibility Policies
**Given** a domain service needs to enforce visibility rules  
**When** the service requests visibility policies from the Security service  
**Then** the Security service returns a list of applicable visibility policies for the domain and resource  
**And** each policy includes the permission scope, field mappings, and metadata

### AC-POLICY-2: Domain Service Caches Policies with Short TTL
**Given** the domain service has retrieved visibility policies from the Security service  
**When** the policies are cached locally  
**Then** the cache TTL is set to a configurable short duration (default: 10 minutes)  
**And** subsequent requests within the TTL use the cached policies  
**And** requests after TTL expiry trigger a refresh from the Security service

### AC-POLICY-3: Cache Invalidated on Policy Change Events
**Given** the domain service is subscribed to policy change events from the Security service  
**When** a policy change event is received  
**Then** the relevant cached policies are immediately invalidated  
**And** the next API request fetches fresh policies from the Security service  
**And** the cache invalidation event is logged for audit

### AC-RBAC-1: Permission Scopes Follow domain:resource:action Pattern
**Given** visibility policies are defined in the Security service  
**When** permission scopes are created or validated  
**Then** each scope follows the format `domain:resource:action`  
**And** invalid scope formats are rejected with a validation error  
**And** scope definitions are documented in the API contract

### AC-RBAC-2: Code Checks Permission Scopes, NOT Role Names
**Given** an API request requires authorization  
**When** the authorization check is performed  
**Then** the code verifies the user has the required permission scope(s)  
**And** the code does NOT check role names directly  
**And** the permission-to-role mapping is maintained in the Security service

### AC-API-1: Single Endpoint with Dynamic DTO Filtering
**Given** a user requests a work order via `GET /api/workorders/{id}`  
**When** the user has some but not all permission scopes  
**Then** the API returns HTTP 200 with a DTO containing only authorized fields  
**And** unauthorized fields are omitted from the response (not null, not present)  
**And** the response structure is consistent regardless of permissions

### AC-API-2: HTTP 403 When Minimum Permissions Not Met
**Given** a user requests a work order via `GET /api/workorders/{id}`  
**When** the user lacks the minimum required permission scope (`workexec:workorder:view`)  
**Then** the API returns HTTP 403 Forbidden  
**And** the response includes a message: "Insufficient permissions to view this resource"  
**And** the failed authorization attempt is logged in the audit trail

### AC-API-3: Partial DTO Returned with Accessible Fields Only
**Given** a user with permission `workexec:workorder:view` but NOT `workexec:workorder:view-pricing`  
**When** the user requests a work order  
**Then** the API returns a DTO with:
  - Basic work order fields (id, status, dates)
  - Customer and vehicle information
  - Labor and parts descriptions
  - BUT pricing fields (unitPrice, cost, margin) are OMITTED  
**And** the response is a valid, well-formed DTO

### AC-AUDIT-1: Audit Trail Captures Request, Permissions, Filtered Fields, UTC Timestamp
**Given** any API request that applies visibility filtering  
**When** the request is processed  
**Then** an audit event is created with:
  - Request ID and user ID
  - UTC timestamp
  - Endpoint and resource ID
  - Permission scopes checked
  - List of visible fields
  - List of filtered fields
  - Result (SUCCESS or DENIED)  
**And** the audit event is persisted to the audit log store  
**And** the audit event can be queried for compliance reporting

## Audit & Observability
- **Audit Log**: Any successful request from an authorized role (e.g., "Service Advisor") for a view containing sensitive financial data **SHOULD** generate an audit log entry. The entry should include `userId`, `workorderId`, `timestamp`, and the `endpoint` or `viewName` accessed.
- **Monitoring/Alerting**: An alert **SHOULD** be triggered and sent to an administrative channel when the system defaults to the secure view due to a missing or misconfigured visibility policy. This indicates a configuration error that requires immediate attention.

- Policy cache hits/misses
- Policy cache invalidation events
- Permission check results (allowed/denied)
- Field filtering decisions
- Failed authorization attempts

## Answered Questions
### Question 1: Policy Source of Truth
**Question:** What is the authoritative source for `VisibilityPolicy` data? Is it configured in a database table within the `Workexec` domain, a static configuration file, or managed by a separate `Security` / `People` domain service via an API? This defines the critical integration contract.

**Decision:** Security/Policy service is authoritative for permissions and visibility rules; domain services enforce server-side and cache with short TTL + invalidation events.

**Interpretation:**
- The **Security/Policy service** is the authoritative source for all visibility policies and permissions
- The **Workexec domain service** is responsible for enforcement but NOT policy authorship
- Domain services SHALL cache visibility policies with:
  - Short TTL (Time-To-Live) to ensure freshness
  - Cache invalidation on policy change events from Security service
- Server-side enforcement is REQUIRED; client-side filtering is supplementary only
- API contract SHALL be defined between Security service and domain services

### Question 2: Field Granularity
**Question:** The story mentions specific fields (`unitPrice`, `cost`, etc.). Is this list exhaustive? Is the policy configurable per-field, or is it a single "Can View Financials" flag per role? The Acceptance Criteria assume per-field control is desired.

**Decision:** Configurable; use explicit RBAC scopes (domain:resource:action) decoupled from role names.

**Interpretation:**
- Field-level visibility SHALL be configurable and NOT hardcoded
- RBAC scopes SHALL follow the pattern: `domain:resource:action`
  - Example: `workexec:workorder:view-pricing`
  - Example: `workexec:workorder:view-cost`
- Role names SHALL be decoupled from permissions (role → permissions mapping, NOT role-based checks in code)
- Policy SHALL support granular field-level control (not just coarse-grained "financial" flags)
- The Security service SHALL manage the mapping of roles to permission scopes
- Domain services SHALL check permission scopes, NOT role names

### Question 3: API Strategy
**Question:** Should this be implemented using a single endpoint that returns a differently shaped DTO based on role, or should we use separate, role-specific endpoints (e.g., `/api/workorders/{id}/mechanic-view` vs `/api/workorders/{id}/full-view`)? The functional behavior assumes a single endpoint with a filtered DTO, which is the preferred approach unless otherwise specified.

**Decision:** Use standard best practices with explicit contracts, idempotency, audit trails, UTC timestamps, scoped RBAC, configurable defaults.

**Interpretation:**
- Use a **single endpoint** pattern with dynamic DTO filtering based on permissions
- API contract SHALL be explicit and well-documented (OpenAPI/Swagger)
- Idempotency SHALL be implemented for state-changing operations
- Audit trails SHALL capture:
  - Who requested the data
  - What permissions were applied
  - Which fields were filtered/visible
  - Timestamp in UTC
- RBAC scopes SHALL be checked on every request (no session-level caching of permissions)
- Configurable defaults SHALL be provided for common visibility scenarios
- The endpoint SHALL return HTTP 403 Forbidden if the user lacks the minimum required permission scope
- The endpoint SHALL return a filtered DTO (omitting fields) if the user has partial permissions

## Original Story (Unmodified – For Traceability)
# Issue #155 — [BACKEND] [STORY] Execution: Apply Role-Based Visibility in Execution UI

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Execution: Apply Role-Based Visibility in Execution UI

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
A mechanic/technician views a workorder during execution.

## Main Flow
1. System identifies viewer role and applicable visibility policy.
2. System hides restricted pricing/cost fields from mechanic views.
3. System continues to store full financial data in the underlying records.
4. Back office views show full pricing/cost data.
5. System logs access to sensitive fields when shown (optional).

## Alternate / Error Flows
- Role misconfiguration → default to safer (hide sensitive) behavior and alert admins.

## Business Rules
- Visibility policies must be consistently enforced across screens and APIs.
- Hiding fields must not remove financial truth from the data model.

## Data Requirements
- Entities: VisibilityPolicy, Workorder, WorkorderItem, UserRole
- Fields: roleId, canViewPrices, unitPrice, extendedPrice, cost, margin

## Acceptance Criteria
- [ ] Mechanic views do not display restricted financial fields.
- [ ] Back office views retain full visibility.
- [ ] Financial data remains present for invoicing and reporting.

## Notes for Agents
This is a UI/API policy layer; keep it separate from business calculations.


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