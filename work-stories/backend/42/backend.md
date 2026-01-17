Title: [BACKEND] [STORY] Security: Define Roles and Permission Matrix for Product/Pricing
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/42
Labels: type:story, domain:security, status:ready-for-dev, agent:story-authoring, agent:security

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:ready-for-dev

### Recommended
- agent:security
- agent:story-authoring

---
**Rewrite Variant:** security-strict
---

## Story Intent
**As a** System Administrator,
**I want to** establish a Security-owned RBAC foundation (roles, permissions, assignments, and permission registration),
**so that** downstream domain services (Product, Pricing, etc.) can consistently enforce least-privilege access to financially sensitive operations.

## Actors & Stakeholders
- **System Administrator (Primary Actor):** Configures roles and permissions.
- **Security Service (System Actor):** Stores roles/permissions and provides authorization decisions.
- **Domain Services (Consumers):** Product service, Pricing service, etc. request authorization and register their permissions.
- **Authenticated User (End User):** Subject of authorization decisions.
- **Audit/Observability (Stakeholder):** Receives audit events for configuration changes and access decisions.

## Preconditions
1. An external Identity Provider (IdP) exists and is the source of truth for authentication.
2. Authenticated requests include a verifiable principal (e.g., JWT with subject + claims).

## Functional Behavior

### 1) Permission model + naming standard
- The system supports `Permission` as a stable identifier string.
- **Naming convention is standardized and validated:** `domain:resource:action`
  - `domain`: bounded context (`product`, `pricing`, `security`, ...)
  - `resource`: singular noun, snake_case (`price_book`, `sku`, `catalog_item`)
  - `action`: verb, snake_case (`read`, `create`, `edit`, `delete`, `approve`, ...)
- The Security Service rejects registration of permissions that do not conform.

### 2) Permission discovery/registration (code-first)
- Each domain service declares a local static permission manifest (e.g., YAML/JSON or code constants).
- On startup (or deployment), the domain service registers its permissions with the Security Service via an internal endpoint:
  - `POST /internal/permissions/register` (idempotent upsert)
  - payload includes `domain/service`, `permissions[]`, and optional metadata (description, deprecated).
- Security Service exposes query endpoints for ops/admin tooling:
  - `GET /permissions`
  - `GET /permissions?domain=pricing`

### 3) Role management
- System Administrator can create and update `Role`.
- System Administrator can grant/revoke permissions on a role.

### 4) Principal-to-role assignment
- Security Service can associate a principal (user or group) to one or more roles.
- Note: primary role assignment may be managed in the IdP and synchronized; Security supports assignment for system/exception scenarios.

### 5) Authorization decision API (RBAC)
- A domain service can request a decision: does principal P have permission X?
- Security Service evaluates principal’s roles → permissions and returns `allow` or `deny`.

## Alternate / Error Flows
- **Denied permission:** protecting service returns `403 Forbidden`; an `access.denied` audit event is emitted.
- **Invalid role/permission:** return `400/404` with a descriptive error.
- **Registration failure:** if a domain service cannot register permissions at startup, it fails fast (configurable) and emits an alert.

## Business Rules
- **Least privilege / deny by default.**
- **Permission immutability:** permission keys are stable identifiers.
- **Domain-owned permission lists:** domains own the list of permissionable actions; Security owns the RBAC framework and registry.

## Data Requirements
- `Permission`
  - `permission_key` (string, unique)
  - `domain` (string)
  - `description` (string, optional)
  - `deprecated` (boolean, optional)

- `Role`
  - `role_name` (string, unique)
  - `description` (string)

- `RolePermission` (many-to-many)
- `PrincipalRole` (many-to-many: user/group principal → roles)

- `AuditLog`
  - `timestamp`, `actor_id`, `event_type`, `details`, `outcome`

## Acceptance Criteria
- **AC1: Permission naming validation**
  - **Given** a permission registration request contains `pricing:price_book:edit`
  - **When** it is registered
  - **Then** it is accepted
  - **And** it is queryable via `GET /permissions?domain=pricing`.

- **AC2: Permission registration is idempotent**
  - **Given** a service registers the same permission manifest twice
  - **When** registration runs
  - **Then** no duplicates are created and the operation succeeds.

- **AC3: Role permission grant is enforced**
  - **Given** role `PricingAnalyst` is granted permission `pricing:msrp:edit`
  - **And** principal `userA` is assigned role `PricingAnalyst`
  - **When** Security is asked to authorize `pricing:msrp:edit` for `userA`
  - **Then** the decision is `allow`.

- **AC4: Deny by default**
  - **Given** principal `userB` has no role granting `pricing:msrp:edit`
  - **When** authorization is requested
  - **Then** the decision is `deny`
  - **And** the caller can translate that into `403 Forbidden`.

- **AC5: Auditing**
  - **Given** an admin grants a permission to a role
  - **When** the change is saved
  - **Then** an audit event `role.permission.grant` is recorded.

## Audit & Observability
- **Audit events**: `role.created`, `role.updated`, `permission.registered`, `role.permission.grant`, `role.permission.revoke`, `principal.role.assign`, `authorization.decision`, `access.denied`.
- **Metrics**: authorization latency, allow/deny rates, denied rate by permission/domain.

## Follow-up Stories (Out of Scope Here)
- Product: define Product permissions + endpoint enforcement.
- Pricing: define Pricing permissions + endpoint enforcement.
- Optional: ABAC location scoping (policy-based context checks) if/when required.

## Open Questions (if any)
- None.

---
## Original Story (Unmodified – For Traceability)
## Backend Implementation for Story

**Original Story**: [STORY] Security: Define Roles and Permission Matrix for Product/Pricing

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want roles/permissions for product/cost/pricing actions so that only authorized staff can change financially sensitive data.

## Details
- Roles: ProductAdmin, PricingAnalyst, StoreManager, ServiceAdvisor, IntegrationOperator.
- Permissions mapped to actions including location overrides.

## Acceptance Criteria
- Permissions enforced.
- Role changes audited.
- Least-privilege defaults.

## Integrations
- Integrates with durion-hr/security identity & role assignment.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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
