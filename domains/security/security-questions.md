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
- [ ] **Task 1.1 — Domain ownership clarification (CRITICAL)**
  - [ ] **Issue #65:** Confirm if this should be relabeled to `domain:audit` (recommended) since financial exception audit is explicitly owned by `audit` domain read model per DECISION-INVENTORY-012
  - [ ] Document rationale if keeping `domain:security` label (permission gating vs data ownership)
  - [ ] Identify audit domain owner and stakeholder for contract clarifications
  - [ ] Update GitHub issue #65 label if ownership changes
  
- [ ] **Task 1.2 — REST endpoint/service mapping (Issue #66 — RBAC Admin)**
  - [ ] Confirm base path: `/api/v1/security/*` per story or alternate
  - [ ] Identify permission registry endpoint: `GET /api/v1/security/permissions` (code-first read model)
  - [ ] Identify role CRUD endpoints:
    - [ ] `POST /api/v1/security/roles` (create)
    - [ ] `GET /api/v1/security/roles` (list)
    - [ ] `GET /api/v1/security/roles/{roleId}` (detail)
    - [ ] `PUT /api/v1/security/roles/{roleId}` (update metadata only)
    - [ ] `DELETE /api/v1/security/roles/{roleId}` or deactivate pattern
  - [ ] Identify grant/revoke endpoints:
    - [ ] `POST /api/v1/security/roles/{roleId}/permissions` (grant)
    - [ ] `DELETE /api/v1/security/roles/{roleId}/permissions/{permissionId}` (revoke)
  - [ ] Identify security audit log endpoint: `GET /api/v1/security/audit` (role/permission change events)
  - [ ] Confirm whether principal-role assignment is in scope or deferred (DECISION-INVENTORY-007)

- [ ] **Task 1.3 — REST endpoint/service mapping (Issue #65 — Audit Trail)**
  - [ ] Confirm base path: `/api/v1/audit/exceptions` per proposed path or alternate
  - [ ] Identify list/search endpoint: `GET /api/v1/audit/exceptions`
  - [ ] Identify detail endpoint: `GET /api/v1/audit/exceptions/{auditEntryId}`
  - [ ] Identify export endpoint: `POST /api/v1/audit/exceptions/export` or sync `GET .../export`
  - [ ] Confirm drilldown integration points:
    - [ ] Order Detail screen path and `orderId` parameter
    - [ ] Invoice Detail screen path and `invoiceId` parameter
  - [ ] Confirm whether export is async (with job status endpoint) or synchronous stream

- [ ] **Task 1.4 — Error envelope and correlation patterns**
  - [ ] Confirm standard error shape for Security domain: `{ code, message, correlationId, fieldErrors?, details? }` per DECISION-INVENTORY-015
  - [ ] Issue #66: Document conflict codes:
    - [ ] `ROLE_NAME_TAKEN` (duplicate role name)
    - [ ] `PERMISSION_NOT_FOUND` (invalid permission during grant)
    - [ ] `ROLE_IN_USE` (deletion/deactivation blocked)
  - [ ] Issue #65: Document audit-specific error codes:
    - [ ] `FORBIDDEN` (401/403 deny-by-default)
    - [ ] `VALIDATION_FAILED` (date range, filter limits)
    - [ ] `EXPORT_TOO_LARGE` (413/422)
  - [ ] Verify correlation ID propagation (header name, request/response pattern)

**Acceptance:** Both issues have documented authoritative endpoints/services with error codes; Issue #65 domain label is resolved

---

### Phase 2 – Data & Dependency Contracts

**Objective:** Resolve entity schemas, ID types, permission format, and cross-domain dependencies

**Tasks:**
- [ ] **Task 2.1 — Permission format and registry structure (Issue #66)**
  - [ ] Confirm permission key format: `domain:resource:action` (snake_case) per story
  - [ ] Document canonical permission examples:
    - [ ] `security:role:create`
    - [ ] `security:role:update`
    - [ ] `security:role:delete`
    - [ ] `security:permission:view`
    - [ ] `security:audit_entry:view` (audit viewing)
    - [ ] `security:audit_entry:export` (audit export)
  - [ ] Confirm permission registry response structure: `{ permissionKey, displayName, description, domain, createdAt }`
  - [ ] Confirm if permissions have category/grouping metadata for UI rendering
  - [ ] Clarify if permission registry is code-first read-only (no UI for permission creation) per DECISION-INVENTORY-006

- [ ] **Task 2.2 — Role entity structure (Issue #66)**
  - [ ] Confirm role identifier field: `roleId` (type: UUID vs opaque string)
  - [ ] Confirm role required fields: `name` (unique), `description`, `tenantId` (scoping)
  - [ ] Confirm role optional fields: `createdAt`, `updatedAt`, `createdByUserId`, `status`
  - [ ] Confirm role status enum values: `ACTIVE`, `DISABLED`, `ARCHIVED` (or alternate)
  - [ ] Confirm name uniqueness scope: per-tenant or global
  - [ ] Confirm name validation rules: length limits, character restrictions, case-sensitivity

- [ ] **Task 2.3 — Grant/Revoke payload structure (Issue #66)**
  - [ ] Confirm grant request: `{ permissionKey }` (single) vs `{ permissionKeys: [] }` (batch)
  - [ ] Confirm grant response: updated role detail with `grantedPermissions[]` array
  - [ ] Confirm revoke: `DELETE .../permissions/{permissionKey}` or body-based
  - [ ] Confirm effective grant timestamp visibility and audit linkage
  - [ ] Clarify idempotency behavior for duplicate grants

- [ ] **Task 2.4 — Security audit log structure (Issue #66)**
  - [ ] Confirm audit entry fields: `eventType`, `eventTs`, `actorUserId`, `targetRoleId`, `permissionKey`, `action`
  - [ ] Confirm event type codes: `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `PERMISSION_GRANTED`, `PERMISSION_REVOKED`
  - [ ] Confirm pagination and filtering: by role, by actor, by date range
  - [ ] Confirm whether audit log is append-only read model or supports deletion/purging

- [ ] **Task 2.5 — Audit entry structure (Issue #65)**
  - [ ] Confirm audit entry identifier: `auditEntryId` (type: UUID vs opaque string)
  - [ ] Confirm summary fields (list view):
    - [ ] `eventType` (enum: `PRICE_OVERRIDE`, `REFUND`, `CANCELLATION`, others?)
    - [ ] `eventTs` (ISO-8601 datetime)
    - [ ] `actorUserId`, `actorDisplayName`
    - [ ] `reasonText`, `reasonCode` (optional or required?)
    - [ ] References: `orderId`, `invoiceId`, `paymentId`, `paymentRef`, `locationId`, `terminalId`
    - [ ] Financial context: `amount` (decimal), `currencyUomId`
  - [ ] Confirm detail fields (detail view):
    - [ ] All summary fields plus `detailsSummary`, `createdByUserId`, `redactionReason`
    - [ ] Optional: `rawPayload` (json/string) only if permission-gated and redacted per DECISION-INVENTORY-013
  - [ ] Document event type codes and subtypes (partial refund, void, line-item override)

- [ ] **Task 2.6 — Identifier types and immutability (Both Issues)**
  - [ ] Issue #66: Confirm `roleId`, `permissionKey`, `auditEventId` types and examples
  - [ ] Issue #65: Confirm `auditEntryId`, `orderId`, `invoiceId`, `paymentId` types and examples
  - [ ] Treat all IDs as opaque; no client-side validation beyond presence

- [ ] **Task 2.7 — Tenant scoping and auth context (Both Issues)**
  - [ ] Confirm tenant scoping is enforced via trusted auth context (no user input for `tenantId`) per DECISION-INVENTORY-008
  - [ ] Issue #66: Confirm role list/detail queries are auto-scoped to authenticated user's tenant
  - [ ] Issue #65: Confirm audit queries are auto-scoped to authenticated user's tenant
  - [ ] Confirm how multi-tenant admin scenarios are handled (if applicable)

**Acceptance:** All entity schemas documented with field types, enums, and identifier examples; permission format canonicalized

---

### Phase 3 – UX/Validation Alignment

**Objective:** Confirm validation rules, state transitions, error handling, and accessibility patterns

**Tasks:**
- [ ] **Task 3.1 — RBAC admin validation rules (Issue #66)**
  - [ ] Role name uniqueness: client-side format check, server-side conflict detection
  - [ ] Role name constraints: length (max), allowed characters, case normalization
  - [ ] Required fields: name (always), description (optional vs required?)
  - [ ] Grant validation: permission key must exist in registry (server-enforced)
  - [ ] Revoke validation: permission must be currently granted (idempotent or error?)
  - [ ] Deletion/deactivation rules: blocked if role is assigned to principals? (depends on scope)

- [ ] **Task 3.2 — Audit trail validation rules (Issue #65)**
  - [ ] Date range validation: `dateFrom <= dateTo` (client-side), open-ended ranges allowed
  - [ ] Event type validation: UI only allows values from backend registry or fixed list?
  - [ ] Filter limits: backend enforces maximum date range or result count? (performance policy)
  - [ ] Export size limits: backend returns `413`/`422` if query too large?
  - [ ] Reason text requirements: is `reasonText` required for all exception types?

- [ ] **Task 3.3 — State transitions and lifecycle (Issue #66)**
  - [ ] Role states: `ACTIVE`, `DISABLED`, `ARCHIVED` (if supported)
  - [ ] Allowed transitions: `ACTIVE ↔ DISABLED`, `DISABLED → ARCHIVED`?
  - [ ] Permission lifecycle: code-first immutable (no state transitions in UI)
  - [ ] Audit log lifecycle: append-only immutable (no UI mutations)

- [ ] **Task 3.4 — Permission-gated UI behavior (Both Issues)**
  - [ ] Issue #66: Required permissions:
    - [ ] View roles: `security:role:view`
    - [ ] Create role: `security:role:create`
    - [ ] Update role: `security:role:update`
    - [ ] Delete/deactivate role: `security:role:delete`
    - [ ] Grant/revoke permissions: `security:role:manage_permissions`?
    - [ ] View security audit log: `security:audit_entry:view`
  - [ ] Issue #65: Required permissions:
    - [ ] View audit trail: `security:audit_entry:view`
    - [ ] Export audit trail: `security:audit_entry:export`
    - [ ] View raw payload (if gated): `security:audit_entry:view_sensitive`?
  - [ ] Confirm deny-by-default enforcement: 401/403 must not leak data existence per DECISION-INVENTORY-001, DECISION-INVENTORY-002

- [ ] **Task 3.5 — Error handling and correlation ID propagation (Both Issues)**
  - [ ] Map HTTP codes to UX:
    - [ ] 400 `VALIDATION_FAILED` → field errors with correlationId
    - [ ] 401/403 → unauthorized message without data leakage
    - [ ] 404 → not found with correlationId
    - [ ] 409 → conflict (duplicate name) with correlationId
    - [ ] 413/422 → constraints (too large, too many results)
    - [ ] 5xx → generic failure with correlationId and retry option
  - [ ] Confirm correlationId is surfaced in all error banners (user-visible)
  - [ ] Confirm request ID preservation across drilldowns (Issue #65 reference navigation)

- [ ] **Task 3.6 — Accessibility and responsiveness (Both Issues)**
  - [ ] Keyboard navigation: forms, tables, filters, buttons
  - [ ] ARIA labels: inputs, buttons, error messages, loading states
  - [ ] Error focus: move focus to first error field on validation failure
  - [ ] Responsive layout: usable on typical back-office tablet widths
  - [ ] Table adaptation: consider stacked layout for narrow screens

- [ ] **Task 3.7 — Sensitive data and redaction policies (Issue #65)**
  - [ ] Confirm raw payload visibility: gated by permission `security:audit_entry:view_sensitive`?
  - [ ] Confirm redaction rules: what PII/sensitive fields are redacted by default? per DECISION-INVENTORY-013
  - [ ] Confirm redaction reason display: show `redactionReason` when payload is withheld
  - [ ] Confirm PII must not be logged in telemetry or console errors

**Acceptance:** All validation rules, state transitions, and permission gates documented; error handling patterns confirmed

---

### Phase 4 – Issue Updates and Closure

**Objective:** Post comprehensive resolution comments to GitHub issues in `durion-moqui-frontend` and update labels

**Tasks:**
- [ ] **Task 4.1 — Issue #66 GitHub comment (RBAC Admin)**
  - [ ] Post clarification comment with:
    - [ ] Confirmed endpoints: permission registry, role CRUD, grant/revoke, security audit log
    - [ ] Permission format: `domain:resource:action` (snake_case)
    - [ ] Role entity structure: required/optional fields, identifier type
    - [ ] Grant/revoke payload structure and idempotency
    - [ ] Error codes: `ROLE_NAME_TAKEN`, `PERMISSION_NOT_FOUND`, `ROLE_IN_USE`
    - [ ] Validation rules: name uniqueness, length limits, character restrictions
    - [ ] Permission gates: `security:role:*` permissions
    - [ ] Security audit log format and filtering
    - [ ] Any remaining open questions with requested owner/domain
  - [ ] Update label: remove `blocked:clarification` when clarifications complete
  - [ ] Reference DECISION documents: DECISION-INVENTORY-001, -002, -006, -007, -015

- [ ] **Task 4.2 — Issue #65 GitHub comment (Audit Trail)**
  - [ ] **Priority:** Address domain label question first (security vs audit ownership)
  - [ ] Post clarification comment with:
    - [ ] **Domain ownership resolution:** Clarify if issue should be relabeled to `domain:audit` per DECISION-INVENTORY-012
    - [ ] Confirmed endpoints: list, detail, export (sync vs async)
    - [ ] Event type codes: `PRICE_OVERRIDE`, `REFUND`, `CANCELLATION` (and subtypes?)
    - [ ] Audit entry structure: summary fields, detail fields, optional raw payload
    - [ ] Export behavior: async with job status endpoint or synchronous stream
    - [ ] Reference linking: authoritative identifiers for navigation (orderId, invoiceId, etc.)
    - [ ] Reason requirements: `reasonText` required/optional, `reasonCode` usage
    - [ ] Permission gates: `security:audit_entry:view`, `security:audit_entry:export`, optional `view_sensitive`
    - [ ] Sensitive payload policy: redaction rules per DECISION-INVENTORY-013
    - [ ] Error codes: `FORBIDDEN`, `VALIDATION_FAILED`, `EXPORT_TOO_LARGE`
    - [ ] Drilldown integration: Order/Invoice Detail screen paths and parameters
    - [ ] Any remaining open questions with requested owner/domain
  - [ ] Update label: update `domain:*` label if ownership changes; remove `blocked:clarification` when clarifications complete
  - [ ] Reference DECISION documents: DECISION-INVENTORY-001, -002, -008, -012, -013, -014, -015

- [ ] **Task 4.3 — Cross-cutting documentation updates**
  - [ ] Update `domains/security/.business-rules/` with:
    - [ ] Permission format standards: `domain:resource:action` (snake_case)
    - [ ] Role lifecycle rules and state transitions
    - [ ] Grant/revoke idempotency and audit linkage
    - [ ] Deny-by-default permission enforcement patterns
  - [ ] Update `domains/audit/.business-rules/` (if issue #65 ownership changes) with:
    - [ ] Financial exception event type registry
    - [ ] Audit entry append-only contract
    - [ ] Redaction and sensitive data policy
    - [ ] Export behavior (sync vs async)

- [ ] **Task 4.4 — Verification and tracking**
  - [ ] Verify all DECISION-INVENTORY-* references are accurate and complete
  - [ ] Verify all blocking questions from story section 16 (Open Questions) are addressed
  - [ ] Create follow-up issues if any clarifications spawn new work items
  - [ ] Update this document's status to `Resolved` when all GitHub comments posted

**Acceptance:** GitHub issues #66 and #65 have comprehensive clarification comments; labels updated; remaining blockers documented

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
