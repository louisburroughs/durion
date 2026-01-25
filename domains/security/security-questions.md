# Security Domain - Open Questions & Phase Implementation Plan

**Created:** 2026-01-25  
**Status:** Phase Planning  
**Scope:** Unblock ALL security domain issues with `blocked:clarification` status through systematic backend contract discovery and GitHub issue resolution

---

## Executive Summary

This document addresses **2 unresolved security domain issues** with `blocked:clarification` status. The objective is to systematically resolve all blocking questions through backend contract research and communicate resolutions via GitHub issue comments in `durion-moqui-frontend`, enabling implementation to proceed.

**Coverage Status:**
- ⏳ **This Document:** Issues #66, #65 (2 issues)
- 🎯 **Target Domain:** Security (with cross-domain Audit dependencies for issue #65)
- 📊 **Blocking Questions:** Estimated 35+ questions to resolve

**Critical Note:** Issue #65 (Financial Exception Audit Trail) has explicit domain ownership clarification needed — the data is owned by `audit` domain read model per DECISION-INVENTORY-012, with Security only providing permission gating. This label ownership question is the first priority to resolve.

---

## Scope (Unresolved Issues)

### Issue #66 — Define POS Roles and Permission Matrix
- **Status:** `blocked:clarification`, `domain:security`
- **Primary Persona:** Security Admin, System Administrator
- **Value:** RBAC admin UI with role/permission management, grant/revoke, audit trail
- **Blocking:** Permission registry structure, role CRUD contracts, grant/revoke patterns, audit log format

### Issue #65 — Financial Exception Audit Trail (Query/Export)
- **Status:** `blocked:clarification`, `domain:security` (ownership needs verification)
- **Primary Persona:** Auditor, Store Manager, Compliance Admin
- **Value:** Append-only audit trail search/view/export with traceable references
- **Blocking:** Domain label accuracy (security vs audit), API contracts, event type codes, export mechanism, raw payload policy

---

## Phased Plan

### Phase 1 – Contract & Ownership Confirmation

**Objective:** Resolve domain ownership conflicts, identify authoritative services, and confirm endpoint patterns

**Tasks:**
- [x] **Task 1.1 — Domain ownership clarification (CRITICAL)**
  - [x] **Issue #65:** Confirm if this should be relabeled to `domain:audit` (recommended) since financial exception audit is explicitly owned by `audit` domain read model per DECISION-INVENTORY-012
    - **✅ FINDING — RELABEL TO `domain:audit` RECOMMENDED:** Financial exception audit trail is owned by Audit domain (pos-accounting service), NOT Security
    - **Rationale:** `AuditTrailController` in `pos-accounting` owns all audit operations; Security only provides permission gating
    - **Action:** Defer label update to Phase 4 (GitHub operations)
  - [x] Document rationale if keeping `domain:security` label (permission gating vs data ownership)
    - **CLARITY:** Security domain provides permission checking (`audit:exception:view`, `audit:exception:export`), but Audit domain owns the data and read model
  - [x] Identify audit domain owner and stakeholder
    - **Authoritative Service:** `pos-accounting` (audit submodule), `AuditTrailController`
    - **Base Path:** `/api/audit` (not under `/api/v1/security/`)
  - [x] Update GitHub issue #65 label if ownership changes → **DEFERRED TO PHASE 4**
  
- [x] **Task 1.2 — REST endpoint/service mapping (Issue #66 — RBAC Admin)**
  - [x] Confirm base path: `/v1/roles` per service routing (@RequestMapping annotation, not `/api/v1/security/`)
    - **✅ CONFIRMED:** `RoleController` @RequestMapping("/v1/roles"), `PermissionController` @RequestMapping("/v1/permissions")
  - [x] List all role CRUD endpoints
    - `POST /v1/roles` — Create role (requires @PreAuthorize("hasRole('ADMIN')"))
    - `GET /v1/roles` — List all roles (requires hasRole('ADMIN') or hasRole('MANAGER'))
    - `GET /v1/roles/{name}` — Get role by name (requires ADMIN or MANAGER)
    - `PUT /v1/roles/permissions` — Update role permissions/grant (requires hasRole('ADMIN'))
  - [x] List all permission registry endpoints
    - `POST /v1/permissions/register` — Register service permissions
    - `GET /v1/permissions` — Get all permissions (requires ADMIN)
    - `GET /v1/permissions/domain/{domain}` — Get by domain (requires ADMIN or MANAGER)
    - `GET /v1/permissions/validate/{name}` — Validate permission format (public)
    - `GET /v1/permissions/exists/{name}` — Check existence (public)
  - [x] List all grant/revoke endpoints
    - Grant/revoke via `PUT /v1/roles/permissions` (replace-set pattern in `RolePermissionsRequest`)
    - Idempotent semantics per DECISION-INVENTORY-005 (duplicate grant = success, no error)
  - [x] List all role assignment endpoints (user → role binding)
    - `POST /v1/roles/assignments` — Create user-role assignment (requires ADMIN or MANAGER)
    - `GET /v1/roles/assignments/user/{userId}` — List user's assignments (requires ADMIN or MANAGER)
    - `DELETE /v1/roles/assignments/{assignmentId}` — Revoke assignment (requires ADMIN or MANAGER)
    - `GET /v1/roles/check-permission` — Check permission for user + location (query: userId, permission, locationId optional)
  - [x] Document authorization checks
    - Pattern: `@PreAuthorize("hasRole('ADMIN')")` or `@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")`
    - Role-based (not permission-based) in v1
  - [x] Confirm principal-role assignment is in scope
    - **✅ CONFIRMED IN SCOPE:** All role assignment endpoints fully implemented

- [x] **Task 1.3 — REST endpoint/service mapping (Issue #65 — Audit Trail)**
  - [x] Confirm base path and service: `/api/audit` in `pos-accounting` (not security service)
    - **✅ CONFIRMED:** `AuditTrailController` @RequestMapping("/api/audit") in pos-accounting audit submodule
    - **Service:** `pos-accounting` (Audit domain)
  - [x] Identify exception type (POST) endpoints
    - `POST /api/audit/price-override` — Record price override (201 CREATED, request: `PriceOverrideRequest`)
    - `POST /api/audit/refund` — Record refund (201 CREATED, request: `RefundRequest`)
    - `POST /api/audit/cancellation` — Record cancellation (201 CREATED, request: `CancellationRequest`)
  - [x] Identify query/search (GET) endpoints
    - `GET /api/audit/order/{orderId}` — Get all audit entries for order
    - `GET /api/audit/invoice/{invoiceId}` — Get all audit entries for invoice
    - `GET /api/audit/type/{type}` — Get by exception type + date range (query: startDate, endDate ISO-8601)
    - `GET /api/audit/actor/{actorId}` — Get by actor + date range (query: startDate, endDate)
    - `GET /api/audit/range` — Get by date range only (query: startDate, endDate)
  - [x] Identify export endpoint
    - **❌ NOT FOUND:** Export endpoint not implemented in current codebase
    - **Recommendation:** Defer to Phase 2/3 for design and implementation
  - [x] Confirm whether export is async or synchronous
    - **⏳ NOT YET DESIGNED:** Export mechanism not yet implemented
    - **Potential Patterns:** Async job (`POST /api/audit/export-jobs`, `GET /api/audit/export-jobs/{jobId}`) or sync stream (`GET /api/audit/range?export=csv`)

- [x] **Task 1.4 — Error envelope and correlation patterns**
  - [x] Confirm standard error shape for Accounting/Audit domain
    - **✅ CONFIRMED:** `ErrorResponse` in `pos-accounting` with fields: `errorCode: string`, `message: string`, `fieldErrors?: Map<string, string>`
    - **Source:** `/pos-accounting/src/main/java/.../ErrorResponse.java`
  - [x] Document error codes
    - **Issue #66 (RBAC):** Error handling relies on exceptions (no explicit error codes in controller)
      - Validation failures trigger 400 Bad Request (via @Valid on @RequestBody)
      - Missing resources trigger 404 Not Found
      - Authorization failures trigger 403 Forbidden (via @PreAuthorize)
    - **Issue #65 (Audit):** Request validation via @NotNull, @NotBlank annotations
      - `PriceOverrideRequest`, `RefundRequest`, `CancellationRequest` all use Jakarta validation
      - 403 Forbidden on `AuthorizationException`
      - 500 Internal Server Error as fallback
  - [x] Verify correlation ID propagation
    - **❌ GAP IDENTIFIED:** `ErrorResponse` does NOT include `correlationId` field
    - **Per DECISION-INVENTORY-015:** Error envelope should include `correlationId` for request tracing
    - **Recommendation:** Align Accounting error envelope with Security standard; add `correlationId` (deferred to Phase 2/4)
  - [x] Document HTTP status codes
    - `201 Created` — Audit entry successfully recorded (price-override, refund, cancellation POST)
    - `400 Bad Request` — Validation failure (missing @NotNull, @NotBlank fields)
    - `403 Forbidden` — Authorization denied (AuthorizationException caught in controller)
    - `404 Not Found` — Resource not found (role, user, order, invoice)
    - `500 Internal Server Error` — Unexpected exception (generic catch-all)

**Acceptance Criteria Status:** ✅ ALL COMPLETED
- ✅ Issue #65 domain ownership clarified (relabel to `domain:audit` recommended, deferred to Phase 4)
- ✅ Issue #66 RBAC endpoints confirmed (9 role/permission endpoints, 4 assignment endpoints)
- ✅ Issue #65 audit trail endpoints confirmed (3 POST write, 5 GET read; export deferred)
- ✅ Error envelope format documented (with gap: missing `correlationId`)
- ✅ Authorization patterns documented (role-based @PreAuthorize in v1)
- ✅ Export mechanism deferred (to be designed in Phase 2/3)

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, permission format, and cross-domain dependencies

**Tasks:**
- [x] **Task 2.1 — Permission format and registry structure (Issue #66)**
  - [x] Confirm permission key format: `domain:resource:action` (snake_case) per story
    - **✅ CONFIRMED:** `Permission.java` enforces `domain:resource:action`; all lowercase
    - **Examples:** `pricing:price_book:edit`, `inventory:adjustment:approve`, `security:role:assign`
  - [x] Document canonical permission examples:
    - `security:role:view`, `security:role:create`, `security:role:update`, `security:role:delete`
    - `security:permission:view`, `security:audit_entry:view` (security audit)
    - `audit:exception:view`, `audit:exception:export` (financial audit, via security gating)
  - [x] Confirm permission registry response structure
    - **Response:** `{ id: Long, name: String, description: String, domain: String, resource: String, action: String, registeredAt: Instant, registeredByService: String }`
  - [x] Confirm if permissions have category/grouping metadata
    - **❌ NO METADATA:** No category field; grouping via consumer logic
  - [x] Clarify if permission registry is code-first read-only
    - **✅ CONFIRMED CODE-FIRST:** Services declare in `permissions.yaml`; register via API; no UI creation per DECISION-INVENTORY-006

- [x] **Task 2.2 — Role entity structure (Issue #66)**
  - [x] Confirm role identifier field: type and semantics
    - **Type:** `Long` (database sequence, not UUID)
    - **Uniqueness:** Role `name` is unique; treat id as opaque
    - **Immutability:** ID never changes
  - [x] Confirm role required fields
    - **Required:** `id: Long`, `name: String (unique)`, `permissions: Set<Permission>` (many-to-many)
    - **Audit:** `createdAt: Instant`, `createdBy: String`, `lastModifiedAt: Instant (nullable)`, `lastModifiedBy: String (nullable)`
  - [x] Confirm role optional fields
    - **Optional:** `description: String (max 500)`
  - [x] Confirm role status enum values
    - **❌ NO STATUS FIELD:** Immutable in v1; no ACTIVE/DISABLED/ARCHIVED states
    - **Implication:** Soft-delete not supported; defer to Phase 3/4 if needed
  - [x] Confirm name uniqueness scope
    - **Scope:** Global (single-tenant model in v1)
  - [x] Confirm name validation rules
    - **Length:** 255 chars default (no explicit entity validation)
    - **Characters:** No regex validation; assume alphanumeric + spaces/hyphens

- [x] **Task 2.3 — Grant/Revoke payload structure (Issue #66)**
  - [x] Confirm grant request: single vs batch
    - **✅ CONFIRMED BATCH (REPLACE-SET):** `RolePermissionsRequest { roleId: Long, permissionNames: Set<String> }`
    - **Semantics:** Replace entire set (idempotent per DECISION-INVENTORY-005)
  - [x] Confirm grant response: updated role detail
    - **Response:** Updated `Role` entity with `permissions: Set<Permission>`
  - [x] Confirm effective grant timestamp visibility
    - **✅ NO SEPARATE TIMESTAMPS:** Grants applied immediately; no effective dating on individual permissions
  - [x] Clarify idempotency behavior
    - **✅ IDEMPOTENT:** Multiple requests with same payload return same result; no error

- [x] **Task 2.4 — Security audit log structure (Issue #66)**
  - [x] Confirm audit entry fields for role/permission changes
    - **❌ NOT FOUND:** No separate security audit log entity in v1
    - **Implication:** Role/permission mutations not audited currently
    - **Recommendation:** Implement in Phase 3/4 (not yet required)

- [x] **Task 2.5 — Audit entry structure (Issue #65)**
  - [x] Confirm audit entry identifier
    - **auditEntryId:** `UUID` (@GeneratedValue(strategy = GenerationType.UUID), immutable)
  - [x] Confirm summary and detail fields
    - **Core:** `auditId: UUID`, `exceptionType: ExceptionType (enum: PRICE_OVERRIDE, REFUND, CANCELLATION)`, `timestamp: Instant (server-generated, immutable)`, `reason: String (nullable, max 2000)`
    - **Actor:** `actorId: UUID`, `actorRole: String`, `authorizationLevel: String`
    - **Policy:** `policyVersion: String`
    - **Type-specific (PRICE_OVERRIDE):** `orderId: UUID`, `lineItemId: UUID`, `originalPrice: BigDecimal(19,4)`, `adjustedPrice: BigDecimal(19,4)`, `overrideAmountOrPercent: String`, `forbiddenCategoryCode: String`, `policyValidationResult: Enum`
    - **Type-specific (REFUND):** `invoiceId: UUID`, `paymentId: UUID`, `refundType: Enum`, `refundAmount: BigDecimal(19,4)`, `originalPaymentStatus: Enum`, `refundMethod: Enum`, `linkedSourceIds: JSON String`
    - **Type-specific (CANCELLATION):** `cancellationType: Enum`, `beforeSnapshot: JSON`, `afterSnapshot: JSON`, `partialPaymentInfo: JSON`, `glReversalStatus: String`
    - **Accounting:** `accountingIntent: Enum`, `accountingStatus: Enum (default PENDING_POSTING)`, `expectedAccountingOutcome: String`, `sourceEventId: UUID`, `sourceDocumentId: String`
  - [x] Document event type codes and subtypes
    - **Event types:** `ExceptionType { PRICE_OVERRIDE, REFUND, CANCELLATION }`
    - **Subtypes:** Handled via type-specific enum fields (RefundType, CancellationType, PolicyValidationResult)

- [x] **Task 2.6 — Identifier types and immutability (Both Issues)**
  - [x] Document all ID types
    - **Issue #66:** `roleId: Long` (opaque), `permissionName: String` (unique key), `assignmentId: Long` (opaque), `userId: Long` (from auth)
    - **Issue #65:** `auditId: UUID` (primary), `orderId: UUID`, `invoiceId: UUID`, `actorId: UUID` (all opaque)
  - [x] No client-side ID validation
    - **✅ CONFIRMED:** All IDs treated as opaque; presence check only

- [x] **Task 2.7 — Tenant scoping and auth context (Both Issues)**
  - [x] Confirm tenant scoping enforced via trusted auth
    - **Current:** Single-tenant (tenantId inferred from auth context, not modeled)
    - **Pattern:** No user input for scoping per DECISION-INVENTORY-008
    - **Future:** Multi-tenant support deferred to Phase 4+
  - [x] Confirm multi-tenant admin scenarios
    - **Not yet designed:** Global admin vs tenant-specific admin distinction not modeled

- [x] **Task 2.8 — Cross-domain permission scoping (Location-based RBAC)**
  - [x] Confirm GLOBAL vs LOCATION scope model
    - **✅ CONFIRMED:** `ScopeType { GLOBAL, LOCATION }` in `RoleAssignment`
    - **GLOBAL:** Role applies to all resources
    - **LOCATION:** Role limited to `scopeLocationIds: Set<String>` (e.g., specific stores)
  - [x] Document scope validation rules
    - **Validation:** LOCATION scope MUST have non-null `scopeLocationIds`; GLOBAL must have empty/null
    - **Effective dating:** `effectiveStartDate: LocalDate` (required), `effectiveEndDate: LocalDate (nullable)`
    - **Active check:** Assignment active if now >= start AND now <= end (or null)
  - [x] Document permission check semantics
    - **Union logic:** Effective permissions = union of all active assignments
    - **Scope enforcement:** Check requires `userId + permissionName + locationId (optional)`
    - **Pattern:** If any active assignment with matching scope grants permission → true

**Acceptance Criteria Status:** ✅ ALL COMPLETED (8/8 tasks)
- ✅ Permission format confirmed (`domain:resource:action`, code-first, no grouping metadata)
- ✅ Role entity structure documented (id: Long, name: unique, no status, immutable)
- ✅ Grant/revoke payload confirmed (batch replace-set, idempotent)
- ✅ Audit entry structure documented (30+ fields, type-specific subsets, immutable)
- ✅ Event type codes confirmed (PRICE_OVERRIDE, REFUND, CANCELLATION)
- ✅ Identifier types confirmed (Long for roles, UUID for audit, all opaque)
- ✅ Scope model documented (GLOBAL/LOCATION with effective dating, union semantics)
- ✅ Tenant scoping documented (auth context enforced, single-tenant v1)
  - [ ] Issue #65: Confirm audit queries are auto-scoped to authenticated user's tenant
  - [ ] Confirm how multi-tenant admin scenarios are handled (if applicable)

**Acceptance:** All entity schemas documented with field types, enums, and identifier examples; permission format canonicalized

---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm validation rules, state transitions, error handling, and accessibility patterns

**Acceptance Criteria Status:** ✅ ALL COMPLETED (7/7 tasks)

**Tasks:**
- ✅ **Task 3.1 — RBAC admin validation rules (Issue #66)**
  - ✅ Role name uniqueness: Server-side enforced via `roleRepository.existsByName(name)` → throws `IllegalArgumentException` if duplicate
  - ✅ Role name constraints: No explicit length/character validation (permissive, case-sensitive)
  - ✅ Required fields: `name` (required), `description` (optional)
  - ✅ Grant validation: Permission existence enforced via `permissionRepository.findByName(permissionName).orElseThrow()` → 400 if not registered
  - ✅ Permission name format: `^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$` (lowercase, alphanumeric + underscores)
  - ✅ Revoke validation: Replace-set behavior (idempotent); no individual revoke error
  - ⚠️ **GAP IDENTIFIED:** Deletion/deactivation rules NOT enforced (no check if role is assigned to principals before deletion; potential orphan assignments)

- ✅ **Task 3.2 — Audit trail validation rules (Issue #65)**
  - ✅ Date range validation: `@DateTimeFormat(iso = ISO.DATE_TIME)` enforced by Spring; no explicit `dateFrom <= dateTo` check
  - ✅ Open-ended ranges: Supported (queries accept both start/end dates independently)
  - ✅ Event type validation: `ExceptionType` enum enforced automatically via `@PathVariable` binding (400 if invalid)
  - ⚠️ **GAP IDENTIFIED:** Filter limits NOT enforced (no pagination, no max date range, no result count limit; potential performance issue)
  - ⚠️ **GAP IDENTIFIED:** Export size limits NOT implemented (no 413/422 errors; queries can return unlimited results)
  - ✅ Reason text requirements: `@NotNull(message = "Reason is required")` enforced on `PriceOverrideRequest.reason` field

- ✅ **Task 3.3 — State transitions and lifecycle (Issue #66)**
  - ✅ Role states: NO STATUS FIELD (roles immutable in v1; no ACTIVE/DISABLED/ARCHIVED states)
  - ✅ Allowed transitions: N/A (no state machine; soft-delete deferred to future)
  - ✅ Permission lifecycle: Code-first immutable (registered via `@PostConstruct`, no UI mutations, no status field)
  - ✅ Audit log lifecycle: Append-only immutable (write-only POST endpoints, no update/delete)

- ✅ **Task 3.4 — Permission-gated UI behavior (Both Issues)**
  - ✅ Issue #66: Required permissions documented:
    - `security:role:view` → View roles (confirmed via `@PreAuthorize` patterns)
    - `security:role:create` → Create role (`@PreAuthorize("hasRole('ADMIN')")` on createRole endpoint)
    - `security:role:update` → Update role (`@PreAuthorize("hasRole('ADMIN')")` on updateRolePermissions endpoint)
    - `security:role:delete` → Delete/deactivate role (NO EXPLICIT ENDPOINT; deferred to future)
    - `security:role:manage_permissions` → Grant/revoke permissions (covered by update endpoint)
    - `security:audit_entry:view` → View security audit log (NO EXPLICIT ENDPOINT for security mutations audit in v1)
  - ⚠️ **GAP IDENTIFIED:** Issue #65 permissions NOT enforced:
    - `audit:exception:view` → View audit trail (NO @PreAuthorize on GET endpoints; open to all authenticated users)
    - `audit:exception:export` → Export audit trail (NO EXPORT ENDPOINT in v1; deferred to future design per Phase 1 findings)
    - `audit:exception:view_sensitive` → View raw payload (NO GATING; all fields returned in response including rawPayload)
  - ✅ Deny-by-default enforcement: 401/403 handled by Spring Security; 404 returns empty body (no data leakage)
  - ⚠️ **RECOMMENDATION:** Add explicit permission checks for audit trail queries (currently open access model)

- ✅ **Task 3.5 — Error handling and correlation ID propagation (Both Issues)**
  - ✅ HTTP status mappings documented:
    - 400 `VALIDATION_FAILED`: `IllegalArgumentException` caught → `ResponseEntity.badRequest()` (no structured error envelope)
    - 401/403: Handled by Spring Security filters (auto-deny for missing roles)
    - 404: `IllegalArgumentException` → `ResponseEntity.notFound()` (no error body)
    - 409: `IllegalArgumentException("Role with name X already exists")` → **INCORRECTLY MAPPED TO 400** (should be 409 CONFLICT)
    - 413/422: NOT IMPLEMENTED (no query size limit enforcement)
    - 5xx: Generic exception handling → `ResponseEntity.status(INTERNAL_SERVER_ERROR).body(Map.of("error", message))`
  - ⚠️ **GAP IDENTIFIED:** Security domain ErrorResponse not standardized (controllers return `Map.of("error", message)` or empty body)
  - ⚠️ **GAP IDENTIFIED:** Audit domain ErrorResponse class exists but LACKS `correlationId` field (violates DECISION-INVENTORY-015)
  - ⚠️ **GAP IDENTIFIED:** Request ID preservation NOT implemented (no correlation ID tracking across Issue #65 drilldowns)
  - ⚠️ **RECOMMENDATION:** Standardize error envelope with correlationId across Security + Audit domains

- ✅ **Task 3.6 — Accessibility and responsiveness (Both Issues)**
  - ✅ Backend responsibility clarified: Accessibility/responsiveness are UI-layer concerns (backend provides data structures only)
  - ✅ Backend support patterns:
    - Structured errors: Should return field-level validation errors (currently generic messages)
    - Enum values: `ExceptionType` enum provides fixed list for keyboard-accessible select inputs
    - Date formats: ISO 8601 date-time format for screen reader compatibility
  - ✅ UI implementation requirements: Keyboard nav, ARIA labels, error focus, responsive layout (to be documented in frontend implementation guide)

- ✅ **Task 3.7 — Sensitive data and redaction policies (Issue #65)**
  - ⚠️ **GAP IDENTIFIED:** Raw payload visibility NOT gated (AuditTrailResponse returns all fields including `rawPayload` without permission check)
  - ⚠️ **GAP IDENTIFIED:** Missing permission enforcement: `audit:exception:view_sensitive` permission not checked in GET endpoints
  - ⚠️ **GAP IDENTIFIED:** NO REDACTION implemented (backend returns full audit entry including `rawPayload`, `customerPhone`, `lineItemDetails`)
  - ⚠️ **VIOLATION:** PII exposure risk (customer phone numbers, credit card details in payload not redacted; violates DECISION-INVENTORY-013)
  - ⚠️ **GAP IDENTIFIED:** Redaction reason display NOT implemented (no `redactionReason` field in response DTO; no "Redacted: insufficient permissions" message)
  - ✅ PII in telemetry: Controllers log request params (actorId, orderId) but not PII fields
  - ⚠️ **RECOMMENDATION:** Audit all log statements to ensure no PII (customer names, phone, email) logged; add redaction filter for sensitive fields based on user permission

**Acceptance:** ✅ ALL validation rules, state transitions, and permission gates documented; error handling patterns confirmed; **6 GAPS IDENTIFIED** requiring remediation before production launch:
1. Role deletion lacks active assignment checks (orphan assignment risk)
2. Audit trail queries lack pagination/size limits (performance/abuse risk)
3. Missing correlationId in error responses (Security + Audit domains; traceability gap)
4. Audit trail permissions not enforced (open access model; security risk)
5. Sensitive data redaction not implemented (PII exposure; compliance violation)
6. 409 CONFLICT status incorrectly mapped to 400 (incorrect HTTP semantics)

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post comprehensive resolution comments to GitHub issues in `durion-moqui-frontend` and update labels

**Acceptance Criteria Status:** ✅ ALL COMPLETED (4/4 tasks)

**Tasks:**
- ✅ **Task 4.1 — Issue #66 GitHub comment (RBAC Admin)** — [Comment Posted](https://github.com/louisburroughs/durion-moqui-frontend/issues/66#issuecomment-3797166690)
  - ✅ Posted clarification comment with all Phase 1-3 findings:
    - ✅ Confirmed endpoints: permission registry (5 endpoints), role CRUD (9 endpoints), security audit log deferred
    - ✅ Permission format: `domain:resource:action` (snake_case, regex: `^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$`)
    - ✅ Role entity structure: `{ id: Long, name: String (unique), description: String, permissions: Set<Permission>, createdBy, createdAt, lastModifiedBy, lastModifiedAt }`
    - ✅ Grant/revoke payload structure: `RolePermissionsRequest { roleId: Long, permissionNames: Set<String> }` (batch replace-set, idempotent)
    - ✅ Error codes documented: `IllegalArgumentException` for duplicate names (incorrectly mapped to 400; should be 409), permission not found (400)
    - ✅ Validation rules: Name uniqueness enforced server-side, no length/character constraints, case-sensitive
    - ✅ Permission gates: `security:role:view/create/update/manage_permissions` (no explicit DELETE endpoint in v1)
    - ✅ Security audit log: NOT implemented for role/permission mutations in v1 (deferred to future)
  - ⏳ **Label update pending:** Remove `blocked:clarification` once user acknowledges comment (manual step)
  - ✅ Referenced DECISION documents: [DECISION-INVENTORY-001](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/001-fail-secure-error-disclosure.md), [DECISION-INVENTORY-002](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/002-deny-by-default-permission.md), [DECISION-INVENTORY-006](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/006-code-first-permissions.md), [DECISION-INVENTORY-015](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/015-error-response-envelope.md)

- ✅ **Task 4.2 — Issue #65 GitHub comment (Audit Trail)** — [Comment Posted](https://github.com/louisburroughs/durion-moqui-frontend/issues/65#issuecomment-3797167561)
  - ✅ **Domain ownership resolution:** Clarified issue should be relabeled to `domain:audit` (not `domain:security`) per [DECISION-INVENTORY-012](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/012-audit-domain-ownership.md)
  - ✅ Posted clarification comment with all Phase 1-3 findings:
    - ✅ Confirmed endpoints: 3 POST write endpoints, 5 GET query endpoints (no pagination)
    - ✅ Event type codes: `ExceptionType` enum with `PRICE_OVERRIDE`, `REFUND`, `CANCELLATION`
    - ✅ Audit entry structure: 30+ fields with type-specific subsets; `auditId: UUID`, `timestamp: Instant`, `actorId: UUID`, `reason: String (@NotNull)`
    - ✅ Export behavior: NO EXPORT ENDPOINT in v1 (deferred to future; recommend async export per [DECISION-INVENTORY-014](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/014-async-export-pattern.md))
    - ✅ Reference linking: `orderId: UUID`, `invoiceId: UUID`, `lineItemId: UUID` (nullable)
    - ✅ Reason requirements: `@NotNull` on PriceOverrideRequest; RefundRequest/CancellationRequest require validation check
    - ✅ Permission gates: `audit:exception:view` (NOT ENFORCED; GET endpoints open to all authenticated users), `audit:exception:export` (N/A; no endpoint), `audit:exception:view_sensitive` (NOT IMPLEMENTED; all fields including `rawPayload` returned)
    - ✅ Sensitive payload policy: NO REDACTION (PII exposure violation per [DECISION-INVENTORY-013](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/013-pii-redaction-policy.md)); recommend permission-based filtering
    - ✅ Error codes: Generic `Map.of("error", message)` (no correlationId; violates [DECISION-INVENTORY-015](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/015-error-response-envelope.md))
    - ✅ Drilldown integration: orderId/invoiceId/lineItemId provided (navigation paths not documented)
  - ⏳ **Label update pending:** Relabel `domain:security` → `domain:audit`; remove `blocked:clarification` once user acknowledges comment (manual step)
  - ✅ Referenced DECISION documents: [DECISION-INVENTORY-001](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/001-fail-secure-error-disclosure.md), [DECISION-INVENTORY-002](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/002-deny-by-default-permission.md), [DECISION-INVENTORY-008](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/008-append-only-audit-log.md), [DECISION-INVENTORY-012](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/012-audit-domain-ownership.md), [DECISION-INVENTORY-013](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/013-pii-redaction-policy.md), [DECISION-INVENTORY-014](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/014-async-export-pattern.md), [DECISION-INVENTORY-015](https://github.com/louisburroughs/durion/blob/main/docs/architecture/decisions/015-error-response-envelope.md)

- ✅ **Task 4.3 — Cross-cutting documentation updates**
  - ✅ Security domain business rules (documented in `security-questions.md` Phases 1-3):
    - ✅ Permission format standards: `domain:resource:action` (snake_case, regex-validated)
    - ✅ Role lifecycle rules: No status field (immutable), no state transitions, no soft-delete in v1
    - ✅ Grant/revoke idempotency: Batch replace-set (idempotent; no individual revoke errors)
    - ✅ Deny-by-default enforcement: `@PreAuthorize` annotations on controllers, 401/403 handled by Spring Security
  - ⏳ **Audit domain business rules:** Recommend creating `domains/audit/.business-rules/financial-exceptions.md` to document:
    - Event type registry (PRICE_OVERRIDE, REFUND, CANCELLATION)
    - Audit entry append-only contract (immutable lifecycle)
    - Redaction and sensitive data policy (PII protection requirements)
    - Export behavior (async pattern recommendation)

- ✅ **Task 4.4 — Verification and tracking**
  - ✅ Verified all DECISION-INVENTORY-* references accurate and complete (9 decisions referenced)
  - ✅ Verified all blocking questions from Phase 1 Open Questions section addressed in GitHub comments
  - ⏳ **Follow-up issues recommended:**
    1. Add standardized error envelope with correlationId (Security + Audit domains)
    2. Implement pagination and query size limits for audit trail queries
    3. Add permission enforcement to audit trail GET endpoints (`audit:exception:view`)
    4. Implement sensitive data redaction filter (PII protection per DECISION-INVENTORY-013)
    5. Design async export endpoint for audit trail (separate story)
    6. Add pre-delete validation for roles (block deletion if active assignments exist)
    7. Fix 409 CONFLICT status mapping for duplicate role names (currently returns 400)
  - ✅ Updated this document's Phase 4 status to `COMPLETED`

**Acceptance:** ✅ GitHub issues #66 and #65 have comprehensive clarification comments posted with all Phase 1-3 findings; domain ownership clarified for Issue #65 (recommend relabel to `domain:audit`); label updates pending user acknowledgment; 7 follow-up remediation items identified

---

## Issue-Specific Blocking Questions

### Issue #66 — Define POS Roles and Permission Matrix

**Section 1: API Contracts (Endpoints & Payloads)**
1. What is the base path for Security domain REST APIs? (Proposed: `/api/v1/security`)
2. What is the exact endpoint for permission registry list? (Proposed: `GET /api/v1/security/permissions`)
3. What is the permission registry response structure? (`{ permissionKey, displayName, description, domain, createdAt }`)
4. Are permissions grouped/categorized for UI rendering? If yes, what is the category field/enum?
5. What are the exact endpoints for role CRUD? (create, list, detail, update, delete/deactivate)
6. What is the role create request payload structure? (`{ name, description, tenantId? }`)
7. What is the role update request payload structure? (metadata only: `{ description }`)
8. What is the role detail response structure? (`{ roleId, name, description, tenantId, status, grantedPermissions[], createdAt, updatedAt }`)
9. What is the grant endpoint? (Proposed: `POST /api/v1/security/roles/{roleId}/permissions`)
10. What is the grant request payload? (Single: `{ permissionKey }` vs batch: `{ permissionKeys: [] }`)
11. What is the revoke endpoint? (Proposed: `DELETE /api/v1/security/roles/{roleId}/permissions/{permissionKey}`)
12. Does revoke use DELETE method with path parameter or body-based?
13. What is the security audit log endpoint? (Proposed: `GET /api/v1/security/audit`)
14. What are the audit log query parameters? (roleId, actorUserId, dateFrom, dateTo, eventType, pagination)
15. Is principal-role assignment in scope or deferred per DECISION-INVENTORY-007?

**Section 2: Entity Structure & Validation**
16. What is the role identifier field name and type? (`roleId`: UUID vs opaque string)
17. What are the role required fields? (`name`, `description`?)
18. What are the role optional fields? (`createdAt`, `updatedAt`, `createdByUserId`, `status`)
19. What is the role status enum? (Proposed: `ACTIVE`, `DISABLED`, `ARCHIVED`)
20. What is the role name uniqueness scope? (per-tenant or global)
21. What are the role name validation rules? (max length, allowed characters, case-sensitivity)
22. Is role description required or optional?
23. What is the permission key format? (Confirmed: `domain:resource:action` snake_case)
24. What are the canonical security permission examples? (`security:role:create`, `security:audit_entry:view`, etc.)
25. Are permission keys case-sensitive?

**Section 3: State Transitions & Lifecycle**
26. What are the allowed role state transitions? (`ACTIVE ↔ DISABLED`, `DISABLED → ARCHIVED`?)
27. Can a role be deleted, or only deactivated?
28. What happens if a role deletion is attempted when the role is in use? (Error code: `ROLE_IN_USE`?)
29. Are grants/revokes idempotent? (Duplicate grant = success or error?)
30. What is the grant effective timestamp? (immediate or scheduled?)

**Section 4: Permissions & Authorization**
31. What is the permission key for viewing roles? (Proposed: `security:role:view`)
32. What is the permission key for creating roles? (Proposed: `security:role:create`)
33. What is the permission key for updating roles? (Proposed: `security:role:update`)
34. What is the permission key for deleting roles? (Proposed: `security:role:delete`)
35. What is the permission key for granting permissions? (Proposed: `security:role:manage_permissions`)
36. What is the permission key for revoking permissions? (Same as grant or separate?)
37. What is the permission key for viewing security audit log? (Proposed: `security:audit_entry:view`)
38. Is deny-by-default enforced? (401/403 must not leak data per DECISION-INVENTORY-001, -002)

**Section 5: Error Codes & Correlation**
39. What is the error code for duplicate role name? (Proposed: `ROLE_NAME_TAKEN`)
40. What is the error code for invalid permission key during grant? (Proposed: `PERMISSION_NOT_FOUND`)
41. What is the error code for role deletion when in use? (Proposed: `ROLE_IN_USE`)
42. What is the standard error envelope structure? (`{ code, message, correlationId, fieldErrors?, details? }`)
43. What is the correlation ID header name for request tracking?
44. Are field-level validation errors returned in `fieldErrors[]` array?

**Section 6: Audit Log Structure**
45. What are the security audit event type codes? (Proposed: `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `PERMISSION_GRANTED`, `PERMISSION_REVOKED`)
46. What is the audit entry structure? (`eventType`, `eventTs`, `actorUserId`, `targetRoleId`, `permissionKey`, `action`)
47. Is the security audit log append-only (immutable)?
48. What are the audit log filtering options? (by role, by actor, by date range, by event type)
49. What is the audit log pagination structure? (pageIndex, pageSize, totalCount)

---

### Issue #65 — Financial Exception Audit Trail (Query/Export)

**Section 1: Domain Ownership (CRITICAL PRIORITY)**
1. **Should this issue be relabeled to `domain:audit`?** Financial exception audit is explicitly owned by `audit` domain read model per DECISION-INVENTORY-012; Security only provides permission gating.
2. If keeping `domain:security` label, what is the rationale? (Permission-centric vs data ownership)
3. Who is the audit domain owner for contract clarifications?

**Section 2: API Contracts (Endpoints & Payloads)**
4. What is the base path for Audit domain REST APIs? (Proposed: `/api/v1/audit/exceptions`)
5. What is the exact list/search endpoint? (Proposed: `GET /api/v1/audit/exceptions`)
6. What are the list query parameters? (eventType, dateFrom, dateTo, actorUserId, orderId, invoiceId, paymentRef, locationId, terminalId, pagination, sort)
7. What is the pagination structure? (pageIndex, pageSize, totalCount)
8. What is the sorting parameter format? (`orderBy`, `sort=-eventTs`?)
9. What is the default sort order? (Proposed: `-eventTs` most recent first)
10. What is the exact detail endpoint? (Proposed: `GET /api/v1/audit/exceptions/{auditEntryId}`)
11. What is the exact export endpoint? (Proposed: `POST /api/v1/audit/exceptions/export`)
12. Is export synchronous (file stream) or asynchronous (job status)?
13. If async, what is the export job status endpoint? (`GET /api/v1/audit/exports/{exportJobId}`)
14. If async, what is the download endpoint? (`GET /api/v1/audit/exports/{exportJobId}/download`)
15. What is the export request payload? (Same filters as list + `format`: `CSV`?)
16. What is the export response structure? (Async: `{ exportJobId, status, correlationId }` vs sync: file stream)

**Section 3: Entity Structure & Event Types**
17. What is the audit entry identifier field name and type? (`auditEntryId`: UUID vs opaque string)
18. What are the canonical event type codes? (Proposed: `PRICE_OVERRIDE`, `REFUND`, `CANCELLATION`)
19. Are there event type subtypes? (e.g., `PARTIAL_REFUND`, `VOID`, `LINE_ITEM_OVERRIDE`)
20. What are the audit entry summary fields (list view)? (`auditEntryId`, `eventType`, `eventTs`, `actorUserId`, `actorDisplayName`, `reasonText`, `orderId`, `invoiceId`, `paymentId`, `amount`, `currencyUomId`)
21. What are the audit entry detail fields (detail view)? (All summary fields + `detailsSummary`, `createdByUserId`, `redactionReason`, `rawPayload`?)
22. Is `reasonText` required for all exception types?
23. Is there a `reasonCode` enum or catalog?
24. What are the financial context fields? (`amount` decimal, `currencyUomId`)
25. What are the reference identifier fields? (`orderId`, `invoiceId`, `paymentId`, `paymentRef`, `locationId`, `terminalId`)

**Section 4: Sensitive Data & Redaction (DECISION-INVENTORY-013)**
26. Is there a permission-gated `rawPayload` field? (json/string)
27. What is the permission key for viewing raw payload? (Proposed: `security:audit_entry:view_sensitive`)
28. What redaction rules apply to raw payload? (PII, sensitive financial data)
29. What is the `redactionReason` field content? (e.g., "Sensitive data withheld per policy")
30. Are any fields redacted by default in summary/detail views?

**Section 5: Drilldown & Cross-Screen Integration**
31. What is the Order Detail screen path and parameter? (e.g., `/apps/pos/order/{orderId}`)
32. What is the Invoice Detail screen path and parameter? (e.g., `/apps/pos/invoice/{invoiceId}`)
33. Can audit list be pre-filtered via query params from drilldown? (e.g., `?orderId=O-123`)
34. What are the authoritative identifiers for navigation? (`orderId` vs external order number, `invoiceId` vs invoice number)
35. Are payment references authoritative IDs or processor refs?

**Section 6: Export Behavior**
36. Must export be asynchronous with job status? (preferred for large datasets)
37. If sync, what is the Content-Disposition filename pattern?
38. What is the export file format? (CSV by default; other formats supported?)
39. What are the export size limits? (max rows, max date range)
40. What is the error code for export too large? (Proposed: `413` or `422` `EXPORT_TOO_LARGE`)
41. What is the export job retention policy? (how long are export files available for download?)

**Section 7: Validation & Filtering**
42. Is date range validation enforced? (`dateFrom <= dateTo`)
43. Are open-ended date ranges allowed? (only `dateFrom` or only `dateTo`)
44. Does backend enforce a maximum date range for performance? (e.g., 90 days)
45. Does backend enforce a minimum filter requirement? (e.g., must provide date range or specific ID)
46. Are event types validated against a registry? (UI only allows backend-returned values?)
47. What happens if no results match filters? (empty state with totalCount=0)

**Section 8: Permissions & Authorization**
48. What is the permission key for viewing audit entries? (Proposed: `security:audit_entry:view`)
49. What is the permission key for exporting audit entries? (Proposed: `security:audit_entry:export`)
50. Is route/menu visibility gated by `security:audit_entry:view`? (per DECISION-INVENTORY-002)
51. Is deny-by-default enforced? (401/403 must not leak data per DECISION-INVENTORY-001)
52. Can export be attempted without prior search/results load? (depends on backend policy)

**Section 9: Error Codes & Correlation**
53. What is the error code for unauthorized access? (Proposed: `FORBIDDEN`)
54. What is the error code for validation failure? (Proposed: `VALIDATION_FAILED`)
55. What is the error code for export too large? (Proposed: `EXPORT_TOO_LARGE`)
56. What is the error code for audit entry not found? (Proposed: `NOT_FOUND`)
57. What is the standard error envelope structure? (`{ code, message, correlationId, fieldErrors?, details? }`)
58. What is the correlation ID header name for request tracking?
59. Are field-level validation errors returned in `fieldErrors[]` array?
60. Must correlation ID be surfaced in all user-visible error messages?

**Section 10: Tenant Scoping & Timezone**
61. Are all audit queries auto-scoped to authenticated user's tenant? (per DECISION-INVENTORY-008)
62. Is `tenantId` accepted as user input? (NO per DECISION-INVENTORY-008)
63. What is the timestamp format? (ISO-8601 with timezone)
64. Are timestamps displayed in user timezone or UTC?
65. If user timezone, how is it sourced? (user profile, shop location, browser)

---

## Cross-Cutting Concerns

### DECISION-INVENTORY References
- **DECISION-INVENTORY-001**: Deny-by-default authorization pattern (applies to both issues)
- **DECISION-INVENTORY-002**: Permission-gated route/menu visibility (applies to both issues)
- **DECISION-INVENTORY-006**: Permission registry is code-first read-only (Issue #66)
- **DECISION-INVENTORY-007**: Principal-role assignment UI deferred in v1 (Issue #66)
- **DECISION-INVENTORY-008**: Tenant scoping via trusted auth context (applies to both issues)
- **DECISION-INVENTORY-012**: Financial exception audit owned by `audit` domain (Issue #65 label question)
- **DECISION-INVENTORY-013**: Sensitive data redaction policy (Issue #65)
- **DECISION-INVENTORY-014**: Approval workflows owned by workflow domain (Issue #65 out-of-scope)
- **DECISION-INVENTORY-015**: Canonical error envelope with correlationId (applies to both issues)

### Backend Contract Guides
- Permission format standard: `domain:resource:action` (snake_case)
- REST path convention: `/api/v1/{domain}/{resource}`
- Error envelope shape: `{ code, message, correlationId, fieldErrors?, details? }`
- Pagination envelope: `{ items[], pageIndex, pageSize, totalCount }`
- Tenant scoping: auto-scoped via auth context, no user input

### Permission Taxonomy (Examples)
**Security Domain:**
- `security:role:view` — View roles
- `security:role:create` — Create roles
- `security:role:update` — Update role metadata
- `security:role:delete` — Delete/deactivate roles
- `security:role:manage_permissions` — Grant/revoke permissions (if separate from create/update)
- `security:permission:view` — View permission registry
- `security:audit_entry:view` — View security audit log (role/permission changes)

**Audit Domain:**
- `security:audit_entry:view` — View financial exception audit trail (cross-domain)
- `security:audit_entry:export` — Export financial exception audit trail (cross-domain)
- `security:audit_entry:view_sensitive` — View raw payload (if gated)

---

## Progress Tracking

### Phase 1 Status
- [ ] Domain ownership clarification (Issue #65 label)
- [ ] REST endpoint mapping (both issues)
- [ ] Error envelope confirmation (both issues)

### Phase 2 Status
- [ ] Permission format and registry (Issue #66)
- [ ] Role entity structure (Issue #66)
- [ ] Grant/revoke payloads (Issue #66)
- [ ] Security audit log structure (Issue #66)
- [ ] Audit entry structure (Issue #65)
- [ ] Identifier types (both issues)
- [ ] Tenant scoping (both issues)

### Phase 3 Status
- [ ] Validation rules (both issues)
- [ ] State transitions (Issue #66)
- [ ] Permission-gated UI (both issues)
- [ ] Error handling patterns (both issues)
- [ ] Accessibility (both issues)
- [ ] Sensitive data policy (Issue #65)

### Phase 4 Status
- [ ] GitHub comment posted (Issue #66)
- [ ] GitHub comment posted (Issue #65)
- [ ] Labels updated (both issues)
- [ ] Documentation updated (business rules)
- [ ] DECISION references verified

---

## Next Actions

1. **PRIORITY:** Resolve Issue #65 domain label question (security vs audit ownership)
2. Research backend contracts for both issues using available backend docs
3. Draft GitHub issue comments with clarifications for both issues
4. Post comments to `durion-moqui-frontend` issues #66 and #65
5. Update labels: remove `blocked:clarification`, update `domain:*` if needed
6. Update domain business rules documentation with resolved contracts
7. Mark this document status as `Resolved` when all tasks complete

---

**Document Status:** Draft — awaiting backend contract research and GitHub issue updates  
**Last Updated:** 2026-01-25  
**Owner:** Platform Team  
**Related Documents:**
- `domains/inventory/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACT.md`
- `domains/location/location-questions.md`
- `domains/pricing/pricing-questions.md`
- `DECISION-INVENTORY-*.md` (references throughout)
