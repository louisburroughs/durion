Title: [BACKEND] [STORY] Access: Assign Roles and Scopes (Global vs Location)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/89
Labels: type:story, domain:people, status:ready-for-dev

## 🏷️ Labels (Final)
### Required
- type:story
- domain:people
- status:ready-for-dev

### Recommended
- agent:people
- agent:story-authoring

---
**Rewrite Variant:** crm-pragmatic

---

## Story Intent

**Goal:** To establish a robust and flexible access control system by enabling administrators to assign specific roles to users, with the ability to constrain those roles to a particular location or apply them globally across the entire organization.

**Problem:** The current system lacks granular access control. Users either have too much or too little permission, and there is no mechanism to restrict a user's role (e.g., a `MANAGER`) to a single physical shop location. This creates operational risks and inefficiencies.

**Value:** This feature will enhance security by enforcing the principle of least privilege, improve operational clarity by ensuring users only see and interact with data relevant to their job function and location, and provide a clear audit trail for all access grants.

## Actors & Stakeholders

- **Primary Actor:**
  - **Admin:** A user with privileges to manage other users' roles and access scopes.

- **System Actors:**
  - **People Service:** The microservice responsible for managing user, role, and role assignment data. This is the System of Record for role assignments.
  - **Auth Service:** The central authentication and authorization service that consumes role assignment data to make real-time access decisions.

- **Stakeholders (Downstream Consumers):**
  - **Work Execution Domain:** Consumes `MECHANIC` role assignments with `LOCATION` scope to determine technician eligibility for jobs at a specific shop.
  - **Location Management Domain:** Consumes `MANAGER` and `DISPATCHER` role assignments to control scheduling and operational permissions within the `shopmgr` application.
  - **Audit System:** Subscribes to all role assignment change events for compliance and security monitoring.

## Preconditions

1. A `User` entity exists and can be uniquely identified.
2. A canonical list of `Role` entities (e.g., `OWNER`, `ADMIN`, `MANAGER`) is defined in the system with scope constraint metadata.
3. A `Location` entity exists for all physical shops and can be uniquely identified.
4. The `Admin` actor is authenticated and authorized to perform user management actions.

## Functional Behavior

An `Admin` actor must be able to create, view, and modify `RoleAssignments` for any user. A `RoleAssignment` links a `User` to a `Role` for a specified duration and scope.

**1. Create Role Assignment:**
- **Trigger:** Admin uses an API endpoint to grant a role to a user.
- **Process:**
  - The Admin provides the `UserID`, `RoleID`, `ScopeType` (`GLOBAL` or `LOCATION`), `EffectiveStartDate`, and optionally `EffectiveEndDate`.
  - If `ScopeType` is `LOCATION`, a `LocationID` must also be provided.
  - **Validation (NEW):** The system checks the target role's `allowedScopes` metadata:
    - If role is inherently `GLOBAL` and scope is `LOCATION`, reject with error.
    - If role is inherently `LOCATION` and scope is `GLOBAL`, reject with error.
    - If role allows both scopes, accept the request.
  - The system validates all inputs (e.g., existence of user, role, location).
  - A new `RoleAssignment` record is created and persisted.
  - An audit event is published indicating the creation of the role grant.

**2. View Role Assignments:**
- **Trigger:** Admin or service queries active role assignments.
- **Process:**
  - Query returns all `RoleAssignment` records where `effectiveStartDate <= now` AND (`effectiveEndDate` is null OR `effectiveEndDate > now`).
  - Permissions are computed as the **union** of all active assignments' permissions.
  - If multiple assignments exist with different scopes, permissions are scope-aware (GLOBAL applies everywhere; LOCATION applies to that location only).

**3. Modify Role Assignment:**
- **Trigger:** Admin needs to change the effective dates or scope of an existing assignment.
- **Process:**
  - The system supports modifying the `EffectiveEndDate` to extend or shorten an assignment's duration.
  - Modifying the core `Role`, `User`, or `Scope` should be handled by ending the current assignment (setting `EffectiveEndDate`) and creating a new one to maintain a clear audit history.
  - An audit event is published for the modification.

**4. End (Revoke) Role Assignment:**
- **Trigger:** Admin needs to revoke a user's role.
- **Process:**
  - The Admin modifies the `RoleAssignment` by setting its `EffectiveEndDate` to the current date/time (or a past date/time).
  - This effectively deactivates the role grant without performing a hard delete, preserving the historical record.
  - An audit event is published with actor, subject, effective dates, and optional reason code.

## Alternate / Error Flows

1. **Invalid User/Role/Location:**
   - **Given:** An Admin attempts to create a role assignment.
   - **When:** The provided `UserID`, `RoleID`, or `LocationID` does not correspond to an existing entity.
   - **Then:** The system must reject the request with a `404 Not Found` or `400 Bad Request` error and a clear message.

2. **Mismatched Scope and Location:**
   - **Given:** An Admin attempts to create a role assignment.
   - **When:** The `ScopeType` is `LOCATION` but no `LocationID` is provided.
   - **Then:** The system must reject the request with a `400 Bad Request` error.
   - **When:** The `ScopeType` is `GLOBAL` but a `LocationID` is provided.
   - **Then:** The system must reject the request with a `400 Bad Request` error, as `LocationID` is not applicable for `GLOBAL` scope.

3. **Invalid Scope for Role (NEW):**
   - **Given:** An Admin attempts to assign a role with a scope not in its `allowedScopes` metadata.
   - **When:** E.g., assigning `MECHANIC` (location-only) with `GLOBAL` scope.
   - **Then:** The system rejects with `400 Bad Request` and a message: "Role {roleId} does not allow {scopeType} scope. Allowed scopes: {allowedScopes}".

4. **Invalid Date Range:**
   - **Given:** An Admin attempts to create or modify a role assignment.
   - **When:** The `EffectiveEndDate` is earlier than the `EffectiveStartDate`.
   - **Then:** The system must reject the request with a `400 Bad Request` error.

5. **Permission Denied:**
   - **Given:** A non-Admin user attempts to access the role assignment endpoints.
   - **When:** The request is received.
   - **Then:** The system must reject the request with a `403 Forbidden` error.

## Business Rules

1. **Role Definitions:** The system shall support the following roles: `OWNER`, `ADMIN`, `MANAGER`, `HR`, `ACCOUNTING`, `DISPATCHER`, `SERVICE_WRITER`, `MECHANIC`, `AUDITOR`, `READ_ONLY`. This list must be configurable.

2. **Scope Types:** A role assignment must have a `ScopeType` of either `GLOBAL` or `LOCATION`.

3. **Location Mandate:** If `ScopeType` is `LOCATION`, the `LocationID` field is mandatory.

4. **Location Prohibition:** If `ScopeType` is `GLOBAL`, the `LocationID` field must be null.

5. **Effective Dates:**
   - `EffectiveStartDate` is mandatory for all assignments.
   - `EffectiveEndDate` is optional. A null value signifies the assignment is active indefinitely.

6. **Immutability:** To ensure a clean audit trail, a persisted `RoleAssignment`'s `UserID`, `RoleID`, and `ScopeType` should be treated as immutable. Changes require ending the existing assignment and creating a new one.

7. **Scope Constraints (Authoritative - NEW):**
   - **Inherently GLOBAL roles (cannot be LOCATION-scoped):**
     - `GLOBAL_ADMIN` (or `ADMIN_GLOBAL`)
     - `OWNER`
     - `AUDITOR`
     - `ACCOUNTING` (if spans multiple locations)
     - `SECURITY_ADMIN` (if present)
   - **Inherently LOCATION-scoped roles (cannot be GLOBAL):**
     - `MECHANIC`
     - `SERVICE_WRITER` / `ADVISOR`
     - `DISPATCHER`
     - `SHOP_MANAGER` (if management is per-location)
   - **Hybrid roles (allowed in either scope):**
     - `MANAGER`
     - `INVENTORY`
     - `REPORTING`
   - **Enforcement:** Stored in `Role` metadata table with `allowedScopes` array. Validated on assignment creation/update.

8. **Multiple Concurrent Assignments (NEW):**
   - A user may hold multiple role assignments concurrently (e.g., `MANAGER@LocationA` + `GLOBAL_ADMIN@GLOBAL`).
   - Permissions are computed as the **scope-aware union** of all active assignments.
   - **Allow-only RBAC at launch:** No explicit denies; permissions are additive.

9. **No Hard Deletes (NEW):**
   - Role assignments are never physically deleted.
   - Revocation always uses `effectiveEndDate`.
   - Hard delete prohibited except test cleanup or privileged administrative purge.

## Data Requirements

**Entity: `Role` (Metadata)**

| Field                 | Type           | Nullable | Description                                                              |
| --------------------- | -------------- | -------- | ------------------------------------------------------------------------ |
| `roleId`              | UUID/String    | false    | Primary Key.                                                             |
| `roleName`            | String         | false    | Human-readable role name (e.g., `MECHANIC`).                            |
| `allowedScopes`       | Array[Enum]    | false    | Set of allowed scopes for this role: `[GLOBAL]`, `[LOCATION]`, or both. |
| `createdAt`           | Timestamp      | false    | Timestamp of role creation.                                              |
| `updatedAt`           | Timestamp      | false    | Timestamp of last role update.                                           |

**Entity: `RoleAssignment`**

| Field                 | Type           | Nullable | Description                                                              |
| --------------------- | -------------- | -------- | ------------------------------------------------------------------------ |
| `assignmentId`        | UUID           | false    | Primary Key.                                                             |
| `userId`              | UUID           | false    | Foreign key to the `User` entity.                                        |
| `roleId`              | UUID/String    | false    | Foreign key to the `Role` entity.                                        |
| `scopeType`           | Enum           | false    | The scope of the assignment. (Values: `GLOBAL`, `LOCATION`).             |
| `locationId`          | UUID           | true     | Foreign key to the `Location` entity. Required if `scopeType=LOCATION`.  |
| `effectiveStartDate`  | Timestamp      | false    | The date and time when the role assignment becomes active.               |
| `effectiveEndDate`    | Timestamp      | true     | The date and time when the role assignment expires. Null means forever.  |
| `createdAt`           | Timestamp      | false    | Timestamp of record creation.                                            |
| `updatedAt`           | Timestamp      | false    | Timestamp of last record update.                                         |
| `changedBy`           | UUID           | false    | Actor ID who created/modified the assignment.                            |
| `reasonCode`          | String         | true     | Optional reason code for the change.                                     |
| `version`             | Integer        | false    | Used for optimistic locking.                                             |

## Acceptance Criteria

**AC-1: Successfully assign a GLOBAL role**
- **Given:** An `Admin` user is authenticated.
- **And:** A `User` with ID `user-123` and a `Role` named `ACCOUNTING` (with `allowedScopes: [GLOBAL]`) exist.
- **When:** The `Admin` submits a request to assign the `ACCOUNTING` role to `user-123` with a `GLOBAL` scope and a valid `effectiveStartDate`.
- **Then:** The system returns a `201 Created` status.
- **And:** A new `RoleAssignment` record is created in the database with the correct `userId`, `roleId`, `scopeType` set to `GLOBAL`, and a null `locationId`.

**AC-2: Successfully assign a LOCATION-scoped role**
- **Given:** An `Admin` user is authenticated.
- **And:** A `User` with ID `user-456`, a `Role` named `MANAGER` (with `allowedScopes: [GLOBAL, LOCATION]`), and a `Location` with ID `loc-789` exist.
- **When:** The `Admin` submits a request to assign the `MANAGER` role to `user-456` with a `LOCATION` scope, `locationId` of `loc-789`, and a valid `effectiveStartDate`.
- **Then:** The system returns a `201 Created` status.
- **And:** A new `RoleAssignment` record is created in the database with the correct `userId`, `roleId`, `scopeType` set to `LOCATION`, and `locationId` set to `loc-789`.

**AC-3: Reject LOCATION scope without a Location ID**
- **Given:** An `Admin` user is authenticated.
- **When:** The `Admin` submits a request to assign a `MANAGER` role with a `LOCATION` scope but provides a null `locationId`.
- **Then:** The system rejects the request with a `400 Bad Request` status and an informative error message.

**AC-4: Reject scope not allowed by role (NEW)**
- **Given:** An `Admin` attempts to assign a `MECHANIC` role (with `allowedScopes: [LOCATION]`) with `GLOBAL` scope.
- **When:** The request is submitted.
- **Then:** The system rejects with `400 Bad Request` and error: "Role MECHANIC does not allow GLOBAL scope. Allowed scopes: [LOCATION]".

**AC-5: Permission check respects effective dates**
- **Given:** A `User` has a `RoleAssignment` for the `MECHANIC` role with an `effectiveStartDate` of yesterday and an `effectiveEndDate` of tomorrow.
- **When:** An external service (like the `Auth Service`) checks the user's permissions for the `MECHANIC` role today.
- **Then:** The check confirms the user has the `MECHANIC` role.

**AC-6: Permission check rejects expired assignment**
- **Given:** A `User` has a `RoleAssignment` for the `MECHANIC` role that had an `effectiveEndDate` of yesterday.
- **When:** An external service checks the user's permissions for the `MECHANIC` role today.
- **Then:** The check confirms the user does NOT have the `MECHANIC` role.

**AC-7: Multiple concurrent assignments (scope-aware union)**
- **Given:** A `User` holds two role assignments:
  - `MANAGER@LocationA`
  - `GLOBAL_ADMIN@GLOBAL`
- **When:** The `Auth Service` evaluates permissions for this user at `LocationA`.
- **Then:** The user has permissions from both roles (union): location-specific manager permissions at `LocationA` plus global admin permissions.

**AC-8: Audit event is generated on role assignment creation**
- **Given:** An `Admin` is about to create a new role assignment.
- **When:** The role assignment is successfully created via the API.
- **Then:** An audit event of type `RoleAssignmentCreated` is published to the event bus, containing the `assignmentId`, the actor (`Admin`), full assignment state, and timestamp.

**AC-9: Revocation via effectiveEndDate (soft delete)**
- **Given:** A role assignment exists for `user-123` with `MECHANIC` role.
- **When:** An `Admin` revokes this assignment by setting `effectiveEndDate = now`.
- **Then:** The `RoleAssignment` record remains in the database (not deleted).
- **And:** The user no longer has active `MECHANIC` permissions as of that effective date.
- **And:** An audit event of type `RoleAssignmentEnded` is published with actor, subject, effective dates, and optional reason code.

## Audit & Observability

1. **Audit Trail:** All CUD (Create, Update, Delete/Deactivate) operations on the `RoleAssignment` entity MUST generate a structured audit event. The event must capture:
   - `EventID`, `Timestamp`, `EventType` (`RoleAssignmentCreated`, `RoleAssignmentModified`, `RoleAssignmentEnded`).
   - `ActorID` and `ActorType` (who performed the action).
   - `SubjectID` (the user whose role was changed).
   - `BeforeState` and `AfterState` of the `RoleAssignment` entity (for modifications).
   - `ReasonCode` (optional, for revocations).

2. **Downstream Integration Events:** A separate, public integration event (e.g., `people.RoleAssignmentChanged`) should be published to a dedicated topic for consumption by other domains like `workexec` and `location`. This decouples internal auditing from external system notifications.

3. **Metrics:** The service should expose metrics for:
   - Number of active role assignments per role.
   - Rate of role assignment API calls (success vs. failure).
   - Distribution of role assignments by scope type (GLOBAL vs. LOCATION).

## Original Story (Unmodified – For Traceability)
# Issue #89 — [BACKEND] [STORY] Access: Assign Roles and Scopes (Global vs Location)

[Original story body preserved as provided in previous issue snapshot]
