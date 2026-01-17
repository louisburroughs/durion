Title: [BACKEND] [STORY] Security: Define Inventory Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/23
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, agent:security

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:ready-for-dev

### Recommended
- agent:inventory
- agent:security
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** inventory-enforcement

---

## Story Intent
As a System Administrator,
I want Inventory-specific roles and a permission matrix (namespaced as `inventory:*`) that are enforced on inventory operations,
so that only authorized users can view/receive/count/adjust/approve inventory activities using least-privilege and auditable controls.

This story is **Inventory-owned** and depends on a **Security-owned RBAC framework** (see Dependencies).

## Actors & Stakeholders
- **System Administrator (Primary Actor):** Configures Inventory roles and assignments using Security RBAC admin tooling.
- **Inventory Manager / Controller:** Approves cycle counts and reviews privileged actions.
- **Receiver:** Receives inventory and posts receipts.
- **Stock Clerk:** Initiates and submits cycle counts.
- **Mechanic Picker:** Views parts availability and requests/consumes parts (read-only permissions in this story).
- **Security Service / API Gateway (System Actors):** Enforce permission checks and produce audit events.
- **Audit & Compliance:** Reviews audit trails for privileged actions.

## Preconditions
- A Security-owned RBAC framework exists and is usable by domain services (example: issue #42).
- Requests are authenticated via an external IdP (OIDC) and include a stable principal identifier (JWT `sub`).
- Inventory operations are exposed as discrete API actions that can be protected with permission checks.

## Dependencies
- Security RBAC framework story: https://github.com/louisburroughs/durion-positivity-backend/issues/42

## Functional Behavior

### 1) Canonical Inventory permission list
Inventory defines (and Security enforces) a canonical permission list using `domain:resource:action` style keys, namespaced to `inventory:*`.

The initial permission set to implement and enforce is:

**Catalog / Items**
- `inventory:item:view`
- `inventory:item:create`
- `inventory:item:update`
- `inventory:item:archive`

**Stock / Levels**
- `inventory:stock:view`
- `inventory:stock:adjust`
- `inventory:stock:transfer`

**Counts / Cycle Counts**
- `inventory:count:view`
- `inventory:count:initiate`
- `inventory:count:submit`
- `inventory:count:approve`

**Receiving / Putaway**
- `inventory:receiving:view`
- `inventory:receiving:receive`
- `inventory:receiving:reverse`

**Locations / Warehouses**
- `inventory:location:view`
- `inventory:location:create`
- `inventory:location:update`
- `inventory:location:archive`

**Reporting / Export**
- `inventory:report:view`
- `inventory:report:export`

### 2) Inventory roles and default mapping (seed)
The system provides a default, seedable role → permission mapping for Inventory:

- **Inventory Viewer**
	- All `inventory:*:view`, plus `inventory:report:view`
- **Inventory Clerk**
	- Viewer + `inventory:count:initiate`, `inventory:count:submit`, `inventory:receiving:receive`
- **Inventory Manager**
	- Clerk + `inventory:item:create`, `inventory:item:update`, `inventory:item:archive`, `inventory:stock:transfer`, `inventory:report:export`
- **Inventory Controller / Approver**
	- Viewer + `inventory:count:approve`, `inventory:stock:adjust`, `inventory:receiving:reverse`
- **Inventory Admin (optional, use sparingly)**
	- All `inventory:*`

### 3) Enforcement on Inventory operations
- Inventory APIs MUST be protected server-side (deny-by-default).
- Each protected endpoint/workflow MUST require one or more explicit permissions from the list above.
- The authorization decision MUST be enforced consistently (e.g., via Security Service decision API, shared middleware, gateway enforcement, or Spring Security method guards), but the outcome is the same: allow only when permission is granted.

### 4) Identity integration contract (non-blocking)
- The source of truth for identity is an external IdP (OIDC).
- The backend maps JWT `sub` (and optionally tenant) to an internal user profile record.
- RBAC roles/permissions are managed within this system (not IdP groups) unless added via a follow-up story.

## Alternate / Error Flows
- **Access denied:** If the principal lacks required permission, the API MUST return `403 Forbidden` and the operation MUST NOT execute.
- **Role/permission misconfiguration:** If a referenced permission is not registered/recognized, the system MUST fail safe (deny) and emit an actionable error/audit signal.

## Business Rules
- **Least privilege / deny-by-default.** Users have no Inventory permissions until roles are assigned.
- **Separation of duties (recommended, implement in defaults):** `inventory:count:approve` and `inventory:stock:adjust` are privileged permissions and should not be granted broadly.
- **Server-side enforcement required.** UI-only enforcement is not sufficient.

## Data Requirements
- Inventory maintains a permission manifest (code constants or config) sufficient to:
	- register permissions with the Security RBAC registry (idempotently), and
	- reference the permission keys when protecting endpoints.

Role/permission assignment data is owned by the Security RBAC system.

## Acceptance Criteria
```gherkin
Scenario: Allow authorized stock adjustment
	Given the permission "inventory:stock:adjust" exists in the Security RBAC registry
	And a role "Inventory Controller" is granted "inventory:stock:adjust"
	And user "Alice" is assigned role "Inventory Controller"
	When "Alice" calls the protected inventory endpoint to adjust stock
	Then the system MUST allow the action to proceed.

Scenario: Deny unauthorized cycle count approval
	Given the permission "inventory:count:approve" exists in the Security RBAC registry
	And user "Bob" is not assigned any role granting "inventory:count:approve"
	When "Bob" calls the protected inventory endpoint to approve a cycle count
	Then the system MUST deny the action
	And the system MUST return HTTP 403.

Scenario: Audit privileged inventory actions
	Given user "Alice" is authorized to adjust stock
	When "Alice" performs a stock adjustment
	Then the system MUST emit an audit event that includes actor identity, permission key, target identifiers, and outcome.
```

## Audit & Observability
- Emit audit events for privileged actions:
	- `inventory.stock.adjusted`
	- `inventory.count.approved`
	- `inventory.receiving.reversed`
- Emit access denial events for Inventory permissions:
	- `inventory.access.denied` (include permission key)
- Track metrics:
	- authorization allow/deny counts by permission
	- 95p authorization check latency

## Open Questions
None.

## Original Story (Unmodified – For Traceability)

# Issue #23 — [BACKEND] [STORY] Security: Define Inventory Roles and Permission Matrix

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Security: Define Inventory Roles and Permission Matrix

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want roles/permissions so that only authorized users can adjust stock or approve counts.

## Details
- Roles: InventoryManager, Receiver, StockClerk, MechanicPicker, Auditor.
- Least privilege defaults.

## Acceptance Criteria
- Permissions enforced.
- Role changes audited.

## Integrations
- Integrates with HR/security identity and role assignment.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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
