STOP: Clarification required before finalization

---
name: "Security: Audit Trail UI for Financial Exceptions (Price Overrides, Refunds, Cancellations)"
labels:
  - type:story
  - domain:security
  - status:draft
  - agent:security-domain
  - agent:story-authoring
  - blocked:clarification
  - risk:incomplete-requirements
---

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security-domain
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

---

## 1. Story Header

**Title**: Security: Gate & Surface Financial Exception Audit Trail (Price Overrides, Refunds, Cancellations) — Frontend (Moqui Screens)

**Primary Persona**: Auditor (secondary: Store Manager; Compliance/Security Reviewer)

**Business Value**: Financial exceptions are explainable and reviewable with consistent access control, correlation IDs, and export capability, enabling investigations and internal controls without exposing sensitive payloads.

---

## 2. Story Intent

### As a / I want / So that
- **As an Auditor**, I want to search, view, and export an append-only audit trail of financial exceptions (price overrides, refunds, cancellations), so that I can investigate who/when/why with traceable references.

### In-scope
- Moqui **screens + navigation** to:
  - Search/filter financial exception audit entries (read-only)
  - Drill down from **Order** and/or **Invoice** detail screens into audit entries filtered by that record
  - View audit entry details (read-only) including actor, timestamp, reason, and references
  - Export audit entries (CSV by default; asynchronous export is preferred but depends on backend)
- UI enforcement of **append-only** behavior (no edit/delete actions exposed)
- Permission-gated access (deny-by-default) using Security-owned permission keys for audit viewing/export
- Canonical error handling using the Security error envelope (`code`, `message`, `correlationId`, optional `fieldErrors[]`, optional `details`) per DECISION-INVENTORY-015

### Out-of-scope
- Write-side generation of financial exception audit entries (owned by the relevant business domains; exposed via `audit` domain read model) (**DECISION-INVENTORY-012**)
- Approval workflows/state transitions related to exceptions (owned by workflow domain; security only gates access) (**DECISION-INVENTORY-014**)
- Creating/editing permissions in UI (permission registry is code-first; UI read-only) (**DECISION-INVENTORY-006**)
- Principal-role assignment UI (explicitly deferred in v1) (**DECISION-INVENTORY-007**)

---

## 3. Actors & Stakeholders
- **Auditor**: Primary consumer; searches, filters, exports, reviews drilldowns.
- **Store Manager**: Investigates exceptions for a store/day/cashier; may export if permitted.
- **Security/Compliance Admin**: Assigns roles/permissions externally; ensures least-privilege and export restrictions.
- **Audit Domain Owner**: Owns financial exception audit read model and export behavior (backend).
- **Accounting/Billing Stakeholders**: Need references (order/invoice/payment identifiers) to reconcile and investigate.

---

## 4. Preconditions & Dependencies
- Authenticated user context is available in the Moqui frontend.
- Authorization is **deny-by-default**; UI must not show audit data without explicit permission grants (**DECISION-INVENTORY-001**, **DECISION-INVENTORY-002**).
- Backend dependencies:
  - **Audit domain** provides financial exception audit read APIs (list/detail/export) under `/api/v1/audit/exceptions/*` (exact endpoints must be confirmed; see Open Questions) (**DECISION-INVENTORY-012**).
  - **Security domain** provides permission gating and canonical error envelope semantics (**DECISION-INVENTORY-015**).
- Cross-screen drilldown dependencies:
  - Order Detail and Invoice Detail screens exist and can accept navigation parameters (e.g., `orderId`, `invoiceId`) to link out to audit trail.
- Tenant scoping:
  - All queries are tenant-scoped via trusted auth context; UI must not accept tenantId as user input (**DECISION-INVENTORY-008**).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Security → Audit Trail (Financial Exceptions)** (name may be “Audit Trail” but must be clearly scoped to financial exceptions to avoid confusion with security-owned RBAC audit).
- Contextual drilldowns:
  - From **Order Detail**: “View Financial Exception Audit”
  - From **Invoice Detail**: “View Financial Exception Audit”

### Screens to create/modify
1. **New Screen**: `apps/pos/screen/security/FinancialExceptionAudit.xml`
   - Search/list view with filters + results + export action (read-only)
2. **New Screen**: `apps/pos/screen/security/FinancialExceptionAuditDetail.xml`
   - Detail view for a single audit entry (read-only)
3. **Modify Screen**: Order detail screen (existing path TBD) to add transition/link to `FinancialExceptionAudit` filtered by `orderId`
4. **Modify Screen**: Invoice detail screen (existing path TBD) to add transition/link to `FinancialExceptionAudit` filtered by `invoiceId`

> Note: Although the data is owned by the `audit` domain, the UI entry point is placed under Security navigation because access is security-gated and auditor-facing. Backend ownership remains `audit` (**DECISION-INVENTORY-012**).

### Navigation context
- List screen supports deep links with query params (all optional):
  - `orderId`, `invoiceId`, `eventType`, `dateFrom`, `dateTo`, `actorUserId`, `locationId`, `terminalId`
- Detail screen accessible by `auditEntryId` (exact field name depends on backend; see Open Questions)

### User workflows (happy + alternate)
**Happy path (Auditor search → detail → export)**
1. Auditor opens Security → Audit Trail (Financial Exceptions).
2. Auditor sets filters (date range, event type, actor, location) and runs search.
3. Auditor opens a row to view detail.
4. Auditor follows reference links to order/invoice if permitted.
5. Auditor exports the filtered result set (if permitted).

**Alternate paths**
- Deep link from Order Detail → Audit Trail pre-filtered to that order
- Empty results: show “No audit entries match your filters”
- Unauthorized: show “Not authorized” without leaking whether entries exist

---

## 6. Functional Behavior

### Triggers
- Screen load of list: perform an initial query only if user has view permission; otherwise show unauthorized.
- User clicks “Search”: query with current filters.
- User clicks a row “View details”: load detail by id.
- User clicks “Export”: request export for current filters (permission required).

### UI actions
- Filters (must align to backend-supported fields; see Data Requirements/Open Questions):
  - Date range (`dateFrom`, `dateTo`)
  - Event type (enum)
  - Actor (user id or display name search if supported)
  - Order/Invoice identifiers
  - Location/Terminal (if supported)
- Results table:
  - Default sort: most recent first
  - Pagination controls
  - Columns (minimum): event type, timestamp, actor, reason summary, references (order/invoice/payment)
- Detail view:
  - Read-only fields
  - Reference links (order/invoice/payment) shown only when identifiers exist and user has access to the target screen

### State changes (frontend)
- No mutation of audit entries.
- Local UI state only: filters, paging, loading/error states, export-in-progress state.

### Service interactions (frontend → backend)
- `GET /api/v1/audit/exceptions` (list)
- `GET /api/v1/audit/exceptions/{auditEntryId}` (detail)
- `POST /api/v1/audit/exceptions/export` (export) or `GET .../export` depending on backend
- Optional: order/invoice screen navigation uses existing screens; no additional fetch required beyond what those screens already do.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Date range validation:
  - If both provided, enforce `dateFrom <= dateTo`; otherwise block search and show inline error.
  - If only one boundary provided, allow open-ended range.
- Event type validation:
  - UI must only allow selecting event types returned by backend (preferred) or a fixed list if backend does not provide a registry (requires clarification).
- No “required filter” rule is imposed by UI unless backend enforces it (performance policy is backend-owned; if backend returns a validation error, surface it).

### Enable/disable rules
- Export button:
  - Disabled if user lacks export permission.
  - Disabled while export request is in progress.
  - Disabled if there are no results loaded (to avoid exporting “unknown” set); if backend supports exporting without prior search, this can be relaxed (clarification).
- “View details”:
  - Enabled only when the row has a resolvable `auditEntryId`.

### Visibility rules
- Route/menu visibility:
  - Show Security → Audit Trail only if user has `security:audit_entry:view` (or equivalent) (**DECISION-INVENTORY-002**).
- Sensitive fields:
  - Do not display raw payload by default; show curated fields only (**DECISION-INVENTORY-013**).
  - If backend provides a gated “raw payload” field, only render it when user has explicit permission (permission key must be defined; see Open Questions).

### Error messaging expectations
- All API errors:
  - Display a user-friendly message plus `correlationId` when present (**DECISION-INVENTORY-015**).
- 401/403:
  - Show unauthorized screen/message; do not show partial data.
- Validation errors:
  - Map `fieldErrors[]` to inline field messages when provided.

---

## 8. Data Requirements

### Entities involved (ownership)
- **Audit domain read model**: Financial exception audit entries (system of record for query/export) (**DECISION-INVENTORY-012**).
- **Security domain**: Permissions and authorization gating; does not own financial exception audit content (**DECISION-INVENTORY-012**, **DECISION-INVENTORY-002**).

### Fields (type, required, defaults)
**Audit Entry Summary (list) — required minimum**
- `auditEntryId` (string/UUID, required) — primary key for drilldown
- `eventType` (string/enum, required) — e.g., `PRICE_OVERRIDE|REFUND|CANCELLATION` (exact codes must be confirmed)
- `eventTs` (datetime, required) — ISO-8601; display in user timezone
- `actorUserId` (string, required)
- `actorDisplayName` (string, optional)
- `reasonText` (string, optional unless backend requires; see Open Questions)
- References (all optional):
  - `orderId`
  - `invoiceId`
  - `paymentId` and/or `paymentRef`
  - `locationId`
  - `terminalId`
- Optional financial context (if provided by audit domain):
  - `amount` (decimal)
  - `currencyUomId` (string)

**Audit Entry Detail (detail) — required minimum**
- All summary fields plus:
  - `createdByUserId` (if distinct from actor)
  - `detailsSummary` (string, optional; curated)
  - `redactionReason` (string, optional; when details are withheld) (**DECISION-INVENTORY-013**)
  - `rawPayload` (json/string, optional; only if gated and redacted) (**DECISION-INVENTORY-013**)

**Defaults**
- Default sort: `eventTs desc`
- Default date range: none (do not assume a policy threshold)

### Read-only vs editable by state/role
- All fields are read-only.
- No edit/delete UI actions exist.

### Derived/calculated fields
- `reasonSummary`: derived client-side by truncating `reasonText` to a safe length for table display.
- `referenceSummary`: derived by preferring internal IDs for navigation and showing external refs if provided.

---

## 9. Service Contracts (Frontend Perspective)

> Financial exception audit is owned by `audit` domain; security provides permission gating and error envelope expectations. Exact audit endpoints must be confirmed (blocking).

### Load/view calls
1. **List query**
   - Method/Path (proposed): `GET /api/v1/audit/exceptions`
   - Query params:
     - `eventType?`, `dateFrom?`, `dateTo?`, `actorUserId?`, `orderId?`, `invoiceId?`, `paymentRef?`, `locationId?`, `terminalId?`
     - Pagination: `pageIndex` (int), `pageSize` (int)
     - Sorting: `orderBy` (string; default `-eventTs`) or `sort=-eventTs` (must match backend)
   - Response:
     ```json
     {
       "items": [ { "auditEntryId": "...", "eventType": "...", "eventTs": "...", "actorUserId": "...", "reasonText": "...", "orderId": "..."} ],
       "pageIndex": 0,
       "pageSize": 25,
       "totalCount": 123
     }
     ```

2. **Detail fetch**
   - Method/Path (proposed): `GET /api/v1/audit/exceptions/{auditEntryId}`
   - Response: audit entry detail (curated by default)

### Create/update calls
- None (append-only; UI provides no mutations)

### Submit/transition calls
1. **Export**
   - Method/Path (proposed): `POST /api/v1/audit/exceptions/export`
   - Body: same filter object as list query + `format` (default `CSV`)
   - Response options (backend-dependent):
     - **Async**: `{ "exportJobId": "...", "status": "QUEUED", "correlationId": "..." }`
     - **Sync**: file stream with `Content-Disposition` filename
   - UI must support both patterns if backend indicates via response headers/body (blocking clarification).

### Error handling expectations
- Canonical error envelope for non-2xx:
  ```json
  {
    "code": "FORBIDDEN|VALIDATION_FAILED|NOT_FOUND|...",
    "message": "…",
    "correlationId": "…",
    "fieldErrors": [{"field":"dateFrom","message":"must be <= dateTo"}],
    "details": {}
  }
  ```
- 403 must be treated as “no access” (deny-by-default) and must not leak data existence (**DECISION-INVENTORY-001**, **DECISION-INVENTORY-015**).

---

## 10. State Model & Transitions

### Allowed states
- Audit entries are immutable; no UI-managed lifecycle.

### Role-based transitions
- Route access control (minimum Security-owned permission keys):
  - View list/detail: `security:audit_entry:view`
  - Export: `security:audit_entry:export`
- Drilldown transitions:
  - From audit entry → Order/Invoice screens only if user has access to those screens (enforced by those screens’ own permission checks).

### UI behavior per state
- Loading: show loading indicator; disable actions until response.
- Loaded: show results and pagination.
- Empty: show empty state with “Clear filters”.
- Error: show error panel including `correlationId` and a “Retry” action.

---

## 11. Alternate / Error Flows

1. **Validation failure (client-side date range)**
   - User enters `dateFrom > dateTo`
   - UI blocks search and shows inline error on date fields.

2. **Validation failure (server-side)**
   - Backend returns `400 VALIDATION_FAILED` with `fieldErrors[]`
   - UI maps field errors to inputs; shows `correlationId`.

3. **Unauthorized access**
   - Backend returns `401/403`
   - UI shows unauthorized message and does not render results table.

4. **Not found (detail)**
   - Backend returns `404 NOT_FOUND`
   - UI shows “Audit entry not found” with correlationId and a link back to list.

5. **Referenced record missing**
   - User clicks a reference link (order/invoice) and target screen returns not found/forbidden
   - UI shows a non-blocking message: “Referenced record is not available.”

6. **Export failure**
   - Backend returns `403 FORBIDDEN`: show “You do not have export permission.”
   - Backend returns `413/422` (too large / constraints): show “Narrow your filters and try again.”
   - Backend returns `5xx`: show generic failure with correlationId; allow retry without losing filters.

---

## 12. Acceptance Criteria

### Scenario 1: Authorized user views financial exception audit list
**Given** I am authenticated and have permission `security:audit_entry:view`  
**When** I navigate to Security → Audit Trail (Financial Exceptions)  
**Then** the system requests the audit list from the audit service with pagination  
**And** I see a paginated list sorted by most recent first  
**And** each row shows event type, timestamp, actor, and available references  
**And** no edit/delete actions are present

### Scenario 2: Unauthorized user is blocked (deny-by-default)
**Given** I am authenticated and do not have permission `security:audit_entry:view`  
**When** I navigate to Security → Audit Trail (Financial Exceptions)  
**Then** I see an unauthorized message  
**And** the UI does not display any audit data  
**And** if an API call is attempted and returns 403, the UI displays the error with `correlationId`

### Scenario 3: Drilldown from Order Detail to filtered audit list
**Given** I am viewing Order Detail for order `O-123`  
**And** I have permission `security:audit_entry:view`  
**When** I click “View Financial Exception Audit”  
**Then** I am navigated to the audit list with `orderId=O-123` applied  
**And** the list query includes `orderId=O-123`  
**And** I see matching entries or an empty state

### Scenario 4: View audit entry detail (curated fields only by default)
**Given** I am on the audit list and an entry exists with id `A-1`  
**When** I open the audit entry detail for `A-1`  
**Then** the UI loads detail from the audit service  
**And** I see read-only fields including actor, timestamp, event type, reason, and references  
**And** I do not see raw payload content unless I have an explicit permission and the backend provides it redacted

### Scenario 5: Export is permission-gated and preserves filters on failure
**Given** I have permission `security:audit_entry:view`  
**And** I do not have permission `security:audit_entry:export`  
**When** I view the audit list  
**Then** the Export action is not shown or is disabled  
**And** if I attempt export via direct URL/action and receive 403, I see “not permitted” with correlationId

**Given** I have permissions `security:audit_entry:view` and `security:audit_entry:export`  
**And** I have applied filters and loaded results  
**When** I click Export  
**Then** the export request uses the current filters  
**And** I receive a downloadable CSV (or an export job id if async)  
**And** if export fails, my filters remain unchanged and I can retry

### Scenario 6: Canonical error envelope is surfaced
**Given** the backend returns an error envelope with `code`, `message`, and `correlationId`  
**When** any list/detail/export request fails  
**Then** the UI displays a non-technical message and includes the `correlationId` for support

---

## 13. Audit & Observability

### User-visible audit data
- Display curated audit fields (actor, timestamp, event type, reason, references).
- Display `correlationId` on error states.

### Status history
- Not applicable for immutable audit entries; any workflow status belongs to the owning domain (out of scope).

### Traceability expectations
- UI must preserve and surface `correlationId` from backend errors (**DECISION-INVENTORY-015**).
- UI must not log or display sensitive raw payload unless explicitly permitted and redacted (**DECISION-INVENTORY-013**).

---

## 14. Non-Functional UI Requirements
- **Performance**: Must use server-side pagination; no client-side full dataset loads.
- **Accessibility**: Keyboard navigable filters and table; labels for inputs; export action accessible.
- **Responsiveness**: Usable on typical back-office tablet widths; table may adapt to stacked layout.
- **i18n/timezone/currency**:
  - Display timestamps in user timezone; show ISO source if provided.
  - Format amounts only when `currencyUomId` is provided; do not infer currency.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Standard empty-state message and “Clear filters”; safe UI ergonomics; impacts UX Summary, Alternate/Error Flows.
- SD-UX-PAGINATION: Paginated list with default page size per app convention; safe non-domain UX; impacts Functional Behavior, Service Contracts.
- SD-ERR-STANDARD-MAPPING: Standard handling for 401/403/5xx with correlationId display; safe generic error mapping consistent with DECISION-INVENTORY-015; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Domain ownership / label conflict**: Financial exception audit is explicitly owned by the `audit` domain read model (**DECISION-INVENTORY-012**). Should this story be relabeled to `domain:audit` (recommended), with Security only providing permission gating? If not, what is the rationale for keeping `domain:security` as primary owner?
2. **Audit API contracts** (blocking): What are the exact endpoints, query params, and response schemas for:
   - list: `/api/v1/audit/exceptions`
   - detail: `/api/v1/audit/exceptions/{id}`
   - export: sync vs async, path, and response type?
3. **Event type codes** (blocking): What are the canonical `eventType` values for price overrides/refunds/cancellations, and are there subtypes (partial refund, void, line-item override)?
4. **Reason requirements** (blocking): Is `reasonText` required for all exception types? Are there `reasonCode` enumerations that must be used?
5. **Sensitive payload policy** (blocking): Is there a permission-gated “raw payload” view? If yes, what is the permission key and what redaction rules apply (**DECISION-INVENTORY-013**)?
6. **Export behavior** (blocking): Must export be asynchronous with job status and later download (preferred), or is synchronous CSV acceptable? If async, what is the job status endpoint and retention?
7. **Reference linking rules** (blocking): Which identifiers are authoritative for navigation (orderId vs external order number; invoiceId vs invoice number; paymentId vs processor ref), and which screens exist for each?