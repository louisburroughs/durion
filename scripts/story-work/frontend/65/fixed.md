---
labels:
  - type:story
  - domain:security
  - domain:audit
  - status:ready-for-implementation
  - agent:security-domain
  - agent:audit-domain
  - agent:story-authoring
---

# Security: Audit Trail for Price Overrides, Refunds, and Cancellations (Frontend, Moqui Screens)

## 1. Story Header

**Title**: Security: Audit Trail for Price Overrides, Refunds, and Cancellations (Frontend, Moqui Screens)

**Primary Persona**: Compliance Auditor (secondary: Shop Manager; Security/Compliance Admin)

**Business Value**: Financial exceptions (price overrides, refunds, cancellations) are explainable, traceable, and reviewable with immutable audit evidence for investigations, internal controls, compliance, and dispute resolution.

---

## 2. Story Intent

### As a / I want / So that
- **As a Compliance Auditor**, I want a centralized, append-only audit trail for price overrides, refunds, and cancellations, so that I can explain financial exceptions with who/when/why and supporting references for investigations and compliance.

### In-scope
- Moqui **screens + navigation** to:
  - Search/filter audit entries (with mandatory date range and indexed filters per AUD-SEC-005)
  - Drill down from an **order and/or invoice** to related audit entries
  - View audit entry details including actor, timestamp, reason, and payment/accounting references
  - Export audit entries report (async job per AUD-SEC-006)
  - View pricing snapshot and rule trace evidence (per AUD-SEC-008)
- UI enforcement of **append-only** behavior (no edit/delete actions exposed)
- Authorization-gated access per AUD-SEC-003
- Location scoping per AUD-SEC-002
- Tenant isolation per AUD-SEC-001

### Out-of-scope
- Backend persistence, entity schema, or write-side generation of audit entries
- Business logic that determines *when* an audit entry is created
- Backend audit ingestion, immutability guarantees, or retention policies

---

## 3. Actors & Stakeholders
- **Compliance Auditor**: Primary consumer; searches, filters, exports, reviews drilldowns, views pricing evidence. Requires `audit:log:view`, `audit:log:view-detail`, `audit:export:execute`, `audit:export:download`, `audit:pricing-snapshot:view`, `audit:pricing-trace:view`.
- **Shop Manager**: Investigates exceptions; view/search + detail (no export by default, no raw payload by default).
- **Security/Compliance Admin**: Defines access policies/roles, retention, export restrictions; manages reason code registry (`audit:reason-code:manage`).
- **Support/Operations**: View/search + detail; raw payload only if explicitly granted (`audit:payload:view`).

---

## 4. Preconditions & Dependencies
- Moqui frontend app running with authenticated user context
- Backend provides (per audit domain "API Expectations (Normative)"):
  - `GET /audit/logs/search` (paging/sorting/filtering with mandatory date range and indexed filters)
  - `GET /audit/logs/detail?eventId=...`
  - Export: `POST /audit/export/request`, `GET /audit/export/status`, `GET /audit/export/download`
  - Meta: `GET /audit/meta/eventTypes`, `GET /audit/meta/reasonCodes`, `GET /audit/meta/locations`
  - Pricing evidence: `GET /audit/pricing/snapshot?snapshotId=...`, `GET /audit/pricing/trace?ruleTraceId=...`
- Authorization/permissions available in session per AUD-SEC-003
- Entities per audit domain: `AuditLog`, `PricingSnapshot`, `PricingRuleTrace`
- Tenant isolation enforced server-side per AUD-SEC-001
- Location scoping enforced server-side per AUD-SEC-002

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Administration/Compliance → Audit Trail** (role-based visibility per AUD-SEC-003 with `audit:log:view`)
- Contextual drilldowns (per AUD-SEC-011):
  - From **Order Detail**, **Invoice Detail**, **Work Order Detail**, **Appointment Detail**, **Mechanic Detail**, **Inventory Movement Detail**, **Estimate Line Detail** screens: "View Audit Trail" (pre-filter to relevant ID)

### Screens to create/modify
1. **New**: `apps/pos/screen/audit/AuditTrail.xml` - Search/list view with filters + results table + export action
2. **New**: `apps/pos/screen/audit/AuditTrailDetail.xml` - Detail view for single audit entry (read-only)
3. **New**: `apps/pos/screen/audit/PricingSnapshot.xml` - Read-only pricing snapshot evidence (per AUD-SEC-008)
4. **New**: `apps/pos/screen/audit/PricingTrace.xml` - Read-only pricing rule trace evidence (per AUD-SEC-008)
5. **New**: `apps/pos/screen/audit/ExportStatus.xml` - Async export job status and download link (per AUD-SEC-006)
6. **Modify**: `OrderDetail`, `InvoiceDetail`, `WorkOrderDetail`, `AppointmentDetail`, `MechanicDetail`, `MovementDetail`, `EstimateLineDetail` screens to add audit trail drilldown links

### Navigation context
- Audit Trail list supports deep links with query params:
  - **Required**: `fromUtc`, `toUtc` (mandatory per AUD-SEC-005; max 90 days)
  - **At least one indexed filter required** per AUD-SEC-005: `eventType`, `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `aggregateId`, `correlationId`, `reasonCode`
  - **Optional** (if user has `audit:scope:cross-location`): `locationIds[]`
- Detail screen accessible by `eventId` (UUIDv7 per audit domain)
- Pricing snapshot accessible by `snapshotId` (UUIDv7)
- Pricing trace accessible by `ruleTraceId` (UUIDv7)

### User workflows
**Happy path (Compliance Auditor search → drilldown → export → pricing evidence)**
1. Auditor opens Administration/Compliance → Audit Trail
2. Applies mandatory date range (max 90 days per AUD-SEC-005) and at least one indexed filter
3. Opens a result row to view detail
4. From detail, views pricing snapshot and rule trace if present (per AUD-SEC-008)
5. From detail, navigates to referenced order/invoice/payment (authorization-gated per AUD-SEC-011)
6. Returns to list and exports results matching current filters (async job per AUD-SEC-006)
7. Downloads export artifact when job completes

**Alternate paths**
- Deep link from Order Detail → Audit Trail pre-filtered to that order
- Empty results: show "No audit entries match filters" with "Clear filters" action
- Unauthorized user: show "Not authorized" and block access (per AUD-SEC-003)
- Date range exceeds 90 days: show inline error (per AUD-SEC-005)
- No indexed filter: show inline error (per AUD-SEC-005)

---

## 6. Functional Behavior

### Triggers
- Screen load of Audit Trail list (initial query with mandatory date range and indexed filter)
- Filter change + "Search" action (re-query)
- Row click "View details" (load detail by `eventId`)
- "Export" action (POST export request → poll status → download per AUD-SEC-006)
- "View Snapshot" link (load pricing snapshot by `snapshotId` per AUD-SEC-008)
- "View Trace" link (load pricing trace by `ruleTraceId` per AUD-SEC-008)

### UI actions
- **Filters** (per AUD-SEC-005 and audit domain):
  - **Mandatory**: `fromUtc`, `toUtc` (date range; max 90 days)
  - **At least one indexed filter required**: `eventType` (dropdown from `GET /audit/meta/eventTypes`), `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `aggregateId`, `correlationId`, `reasonCode`
  - **Optional** (if user has `audit:scope:cross-location`): `locationIds[]` (multiselect from `GET /audit/meta/locations`)
- **Results table**: Sort by `occurredAt` desc by default; pagination controls; columns: event type, timestamp (user timezone), actor, reason summary, order/invoice/payment refs, location
- **Detail view**: Immutable fields displayed; reference links to related records (per AUD-SEC-011); pricing snapshot/trace links if present (per AUD-SEC-008); immutability proof fields if present and user has `audit:proof:view` (per AUD-SEC-009); raw payload only if user has `audit:payload:view` and rendered as escaped text (per AUD-SEC-004)
- **Export**: CSV format (per AUD-SEC-006); async job with status polling and download link

### Service interactions (per audit domain API Expectations)
- `GET /audit/logs/search` (list query)
- `GET /audit/logs/detail?eventId=...` (detail)
- `POST /audit/export/request` (export request)
- `GET /audit/export/status?exportId=...` (export status)
- `GET /audit/export/download?exportId=...` (export download)
- `GET /audit/meta/eventTypes` (event type dropdown)
- `GET /audit/meta/reasonCodes` (reason code display metadata)
- `GET /audit/meta/locations` (location dropdown for cross-location search)
- `GET /audit/pricing/snapshot?snapshotId=...` (pricing snapshot)
- `GET /audit/pricing/trace?ruleTraceId=...` (pricing rule trace)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (per AUD-SEC-005)
- Date range:
  - `fromUtc` and `toUtc` are **required**
  - `fromUtc <= toUtc`
  - `toUtc - fromUtc <= 90 days`
  - UI displays in user timezone but sends UTC to backend
- At least one indexed filter is **required**: `eventType`, `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `aggregateId`, `correlationId`, `reasonCode`
- `eventType` must be from controlled vocabulary (`GET /audit/meta/eventTypes` per AUD-SEC-010)
- Cross-location search requires explicit `locationIds[]` and `audit:scope:cross-location` permission (per AUD-SEC-002)

### Enable/disable rules (per AUD-SEC-003, AUD-SEC-005)
- **Export button**: Enabled only if user has `audit:export:execute` AND results loaded AND export not already in progress
- **Pricing snapshot/trace links**: Visible only if `snapshotId`/`ruleTraceId` present AND user has `audit:pricing-snapshot:view`/`audit:pricing-trace:view`
- **Raw payload section**: Visible only if user has `audit:payload:view` (per AUD-SEC-004)
- **Immutability proof fields**: Visible only if fields present AND user has `audit:proof:view` (per AUD-SEC-009); label as "Provided" not "Verified" unless backend provides `proofVerified=true`
- **Cross-location filter**: Visible only if user has `audit:scope:cross-location` (per AUD-SEC-002)

### Visibility rules (per AUD-SEC-001, AUD-SEC-002, AUD-SEC-003)
- Screens visible only to authorized roles/permissions per AUD-SEC-003
- All data is tenant-scoped (per AUD-SEC-001); no cross-tenant search/export
- Default location scope is user's current location (per AUD-SEC-002)
- Payment reference fields shown only if present
- Reason display: show `reasonCode` + `reasonNotes` if present; map `reasonCode` to display name via `GET /audit/meta/reasonCodes`

### Error messaging expectations (per AUD-SEC-004, AUD-SEC-005)
- Query failure: show non-technical message + correlation id if available
- Unauthorized: show "You do not have access to Audit Trail" (per AUD-SEC-003)
- Date range validation failure: show "Date range is required and maximum 90 days" (per AUD-SEC-005)
- Missing indexed filter: show "At least one filter required (e.g., event type, work order, actor)" (per AUD-SEC-005)
- Cross-location denied: show "Cross-location search requires additional permission" (per AUD-SEC-002)
- Export failure: show error and keep current filters intact
- For 401: redirect to login
- For 403: show unauthorized screen per AUD-SEC-003
- For 5xx: show generic failure with correlation id, allow retry

---

## 8. Data Requirements

### Entities involved (per audit domain)
- **AuditLog**: append-only audit events (normative entity)
- **PricingSnapshot**: immutable pricing evidence (normative entity per AUD-SEC-008)
- **PricingRuleTrace**: immutable pricing rule trace evidence (normative entity per AUD-SEC-008)

### Key Fields (per audit domain)

**AuditLog**
- `auditLogId` (UUIDv7, required) — internal PK
- `eventId` (UUIDv7, required) — dedupe key; used for drilldown
- `schemaVersion` (string, required)
- `eventType` (string, required) — from controlled vocabulary
- `occurredAt` (datetime w/ timezone, required) — display in user timezone
- `emittedAt` (datetime w/ timezone, optional)
- `tenantId` (UUIDv7, required) — enforced server-side (per AUD-SEC-001)
- `locationId` (UUIDv7, required) — display as location name (per AUD-SEC-002)
- `actor` (object, required): `actorType`, `actorId`, `displayName` (optional)
- `aggregateType` (string, required)
- `aggregateId` (string, required)
- `changeSummaryText` (string, optional)
- `changePatch` (structured diff, optional)
- `reasonCode` (string, optional)
- `reasonNotes` (string, optional)
- `correlationId` (string, optional) — W3C trace correlation (per AUD-SEC-012)
- `sourceSystem` (string, optional)
- `rawPayload` (string/json, optional) — only if user has `audit:payload:view`; render as escaped text (per AUD-SEC-004)
- Optional immutability proof fields (per AUD-SEC-009): `hash`, `prevHash`, `signature` (display-only if user has `audit:proof:view`)

**PricingSnapshot** (per AUD-SEC-008)
- `snapshotId` (UUIDv7, required)
- `timestamp` (datetime w/ timezone, required)
- `quoteContext` (object, restricted/redacted per AUD-SEC-004)
- `finalPrice` (money, required)
- `ruleTraceId` (UUIDv7, optional)

**PricingRuleTrace** (per AUD-SEC-008)
- `ruleTraceId` (UUIDv7, required)
- `evaluationSteps[]` (array, required): `ruleId`, `status` (APPLIED|REJECTED|SKIPPED), `inputs` (restricted/redacted), `outputs` (restricted/redacted)
- If paginated: support `pageToken`, `nextPageToken`, `isTruncated`

**Defaults**
- Default sort: `occurredAt desc`
- Default date range: **none** (mandatory per AUD-SEC-005)
- Default location scope: user's current location (implicit per AUD-SEC-002)

---

## 9. Service Contracts (Frontend Perspective)

> Concrete Moqui service names must map to these capability contracts per audit domain "API Expectations (Normative)".

### Load/view calls
1. **List query**: `GET /audit/logs/search` (or Moqui service `search#AuditLogs`)
   - **Required inputs**: `fromUtc`, `toUtc` (ISO 8601 UTC, max 90-day window per AUD-SEC-005)
   - **At least one indexed filter required** per AUD-SEC-005: `eventType`, `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `productId`, `sku`, `partNumber`, `actorId`, `aggregateId`, `correlationId`, `reasonCode`
   - **Optional** (for cross-location search per AUD-SEC-002): `locationIds[]` (requires `audit:scope:cross-location`)
   - **Pagination**: `pageIndex` (int, default 1), `pageSize` (int, default per app convention)
   - **Sort**: `orderBy` (string, default `-occurredAt`)
   - **Output**: `items[]` (AuditLog summary), `totalCount` (int), `pageInfo`
   - **Security**: enforces tenant isolation (AUD-SEC-001), location scoping (AUD-SEC-002), date range/filter guardrails (AUD-SEC-005)

2. **Detail fetch**: `GET /audit/logs/detail?eventId=...` (or Moqui service `get#AuditLogDetail`)
   - **Inputs**: `eventId` (UUIDv7, required)
   - **Output**: full AuditLog record
   - **Security**: enforces tenant isolation (AUD-SEC-001), permission check (`audit:log:view-detail`)

3. **Meta: Event types**: `GET /audit/meta/eventTypes` (or Moqui service `list#EventTypes`)
   - **Inputs**: none (tenant-scoped per AUD-SEC-001)
   - **Output**: `eventTypes[]` (code, displayName)

4. **Meta: Reason codes**: `GET /audit/meta/reasonCodes` (or Moqui service `list#ReasonCodes`)
   - **Inputs**: none (tenant-scoped per AUD-SEC-001)
   - **Output**: `reasonCodes[]` (code, displayName, description)

5. **Meta: Locations**: `GET /audit/meta/locations` (or Moqui service `list#Locations`)
   - **Inputs**: none (tenant-scoped per AUD-SEC-001)
   - **Output**: `locations[]` (locationId, displayName)

6. **Pricing snapshot**: `GET /audit/pricing/snapshot?snapshotId=...` (or Moqui service `get#PricingSnapshot`)
   - **Inputs**: `snapshotId` (UUIDv7, required)
   - **Output**: full PricingSnapshot record per AUD-SEC-008
   - **Security**: permission check (`audit:pricing-snapshot:view`), redaction per AUD-SEC-004

7. **Pricing rule trace**: `GET /audit/pricing/trace?ruleTraceId=...` (or Moqui service `get#PricingRuleTrace`)
   - **Inputs**: `ruleTraceId` (UUIDv7, required), `pageToken` (string, optional for pagination)
   - **Output**: PricingRuleTrace record with `evaluationSteps[]` per AUD-SEC-008; if paginated: `nextPageToken`, `isTruncated`
   - **Security**: permission check (`audit:pricing-trace:view`), redaction per AUD-SEC-004

### Create/update calls
- None (append-only; UI provides no mutations per audit domain)

### Submit/transition calls
1. **Export request**: `POST /audit/export/request` (or Moqui service `request#AuditExport`)
   - **Inputs**: same filters as list query (per AUD-SEC-006): `fromUtc`, `toUtc` (required), at least one indexed filter (required), `format` (string, default `CSV`)
   - **Output**: `exportId` (UUIDv7), `statusUrl` (URL to poll status)
   - **Security**: permission check (`audit:export:execute`), audits export request per AUD-SEC-006

2. **Export status**: `GET /audit/export/status?exportId=...` (or Moqui service `get#ExportStatus`)
   - **Inputs**: `exportId` (UUIDv7, required)
   - **Output**: `status` (PENDING|PROCESSING|COMPLETED|FAILED), `progress` (0-100, optional), `downloadUrl` (string, only if status=COMPLETED), `error` (string, only if status=FAILED)
   - **Security**: permission check (`audit:export:download`), non-enumerable per AUD-SEC-006

3. **Export download**: `GET /audit/export/download?exportId=...` (or Moqui service `download#AuditExport`)
   - **Inputs**: `exportId` (UUIDv7, required)
   - **Output**: file download stream (CSV per AUD-SEC-006) with SHA-256 digest manifest
   - **Security**: permission check (`audit:export:download`)

---

## 10. State Model & Transitions

### Allowed states
- Audit entries: immutable (no UI-managed state per audit domain)
- Export jobs: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` (per AUD-SEC-006)

### Role-based transitions (per AUD-SEC-003)
- **Route access control**:
  - `AuditTrail` requires `audit:log:view`
  - `AuditTrailDetail` requires `audit:log:view-detail`
  - `PricingSnapshot` requires `audit:pricing-snapshot:view`
  - `PricingTrace` requires `audit:pricing-trace:view`
  - Export requires `audit:export:execute` and `audit:export:download`
  - Raw payload section requires `audit:payload:view`
  - Immutability proof fields require `audit:proof:view`
  - Cross-location search requires `audit:scope:cross-location`
- Drilldown transitions (per AUD-SEC-011): From audit entry → Order/Invoice/Payment/WorkOrder/Appointment/Mechanic/Movement/EstimateLine Detail only if user has access (destination screens enforce their own access controls)

### UI behavior per state
- Loading: show progress indicator, disable export/search until initial load completes
- Loaded: show results + paging
- Error: show error panel with retry
- Empty: show empty state with "Clear filters" action
- Export pending/processing: show progress indicator, disable export button
- Export completed: show download link
- Export failed: show error message and retry button

---

## 11. Alternate / Error Flows

1. **Validation failure (date range invalid or missing indexed filter)**: Prevent search; show inline error on date inputs or filter section. Backend also rejects with 400 + field errors (per AUD-SEC-005).
2. **Date range exceeds 90 days**: Show inline error "Maximum date range is 90 days" (per AUD-SEC-005). Backend also rejects with 400.
3. **Cross-location search without permission**: Hide cross-location filter if user lacks `audit:scope:cross-location` (per AUD-SEC-002). If user tries to bypass (e.g., URL hack), backend rejects with 403.
4. **Concurrency conflicts**: Not applicable (read-only), except: Audit entry referenced order/invoice/workOrder missing/deleted: show "Referenced record not found" when navigating (per AUD-SEC-011).
5. **Unauthorized access**: If user lacks permission: Block route and show unauthorized message per AUD-SEC-003; do not leak existence of audit entries.
6. **Empty states**: No entries found: show empty message and retain filters; provide "Clear filters" action.
7. **Export too large / restricted**: If backend returns "too many rows" or "export restricted" (per AUD-SEC-006): Show actionable error ("Narrow date range and try again" / "You do not have export permission").
8. **Pricing trace pagination**: If trace is large and backend returns `isTruncated=true` (per AUD-SEC-008): Show "Page 1 of N" with next/prev buttons using `pageToken`/`nextPageToken`.
9. **Raw payload access denied**: If user lacks `audit:payload:view` (per AUD-SEC-004): Hide raw payload section entirely; if user tries to bypass, backend rejects with 403.
10. **Immutability proof fields not verified**: If backend does not provide `proofVerified=true` (per AUD-SEC-009): Label fields as "Provided" not "Verified".

---

## 12. Acceptance Criteria

### Scenario 1: Compliance Auditor views audit trail list with mandatory date range and indexed filter
**Given** I am authenticated as a Compliance Auditor with `audit:log:view`  
**When** I navigate to Administration/Compliance → Audit Trail  
**Then** I see a search form requiring a date range (max 90 days) and at least one indexed filter  
**And** when I provide valid filters and search  
**Then** I see a paginated list of audit entries sorted by most recent first  
**And** each entry shows event type, timestamp (user timezone), actor, reason summary, and any available refs  
**And** I see location scope indicator showing my current location

### Scenario 2: Filter by order drilldown
**Given** I am viewing an Order Detail page for order `O-123` and have `audit:log:view`  
**When** I click "View Audit Trail"  
**Then** I am taken to the Audit Trail screen with `orderId=O-123` pre-filled and a valid date range (e.g., last 90 days)  
**And** all matching audit entries are displayed (or an empty state if none)

### Scenario 3: View audit entry detail with pricing evidence
**Given** I am on the Audit Trail list and there is an entry with `eventId=A-1` for a price override  
**And** I have `audit:log:view-detail` and `audit:pricing-snapshot:view`  
**When** I open the audit entry detail for `A-1`  
**Then** I see read-only fields including actor, timestamp, reason, correlation id  
**And** I see pricing snapshot link (if `snapshotId` present)  
**When** I click "View Snapshot"  
**Then** I see the pricing snapshot detail screen with timestamp, final price, and redacted quote context  
**And** I see pricing rule trace link (if `ruleTraceId` present)  
**When** I click "View Trace"  
**Then** I see the pricing rule trace detail screen with evaluation steps, rule IDs, statuses, and redacted inputs/outputs

### Scenario 4: Append-only enforcement in UI
**Given** I am on the Audit Trail list or detail screen  
**Then** there are no UI actions to edit or delete an audit entry  
**And** direct URL attempts to any edit route for audit entries are not available (route does not exist or is denied)

### Scenario 5: Export report (async job)
**Given** I have loaded audit trail results with filters applied and have `audit:export:execute` and `audit:export:download`  
**When** I click Export  
**Then** an export job is requested and I see a status indicator  
**And** I can poll export status until it completes  
**And** when status is COMPLETED, I receive a download link  
**When** I click download  
**Then** I receive a CSV file matching the current filters with SHA-256 digest manifest  
**And** if export fails, I see an error message and can retry without losing filters

### Scenario 6: Unauthorized user blocked
**Given** I am authenticated without `audit:log:view`  
**When** I navigate to Administration/Compliance → Audit Trail  
**Then** I am shown an unauthorized message per AUD-SEC-003  
**And** no audit data is displayed

### Scenario 7: Cross-location search with permission
**Given** I am authenticated with `audit:scope:cross-location`  
**When** I navigate to Audit Trail and select multiple locations from the location filter  
**And** I provide a valid date range and at least one indexed filter and search  
**Then** I see audit entries from all selected locations  
**And** each entry shows its location

### Scenario 8: Cross-location search without permission
**Given** I am authenticated without `audit:scope:cross-location`  
**Then** I do not see a cross-location filter in the UI  
**And** my search is implicitly scoped to my current location per AUD-SEC-002

### Scenario 9: Date range validation
**Given** I am on the Audit Trail screen  
**When** I provide a date range exceeding 90 days  
**Then** I see an inline error "Maximum date range is 90 days" per AUD-SEC-005  
**And** the search is prevented

### Scenario 10: Missing indexed filter validation
**Given** I am on the Audit Trail screen  
**When** I provide a date range but no indexed filter  
**Then** I see an inline error "At least one filter required (e.g., event type, work order, actor)" per AUD-SEC-005  
**And** the search is prevented

### Scenario 11: Immutability proof fields display
**Given** I am viewing an audit entry detail for `eventId=A-1` and the backend provides `hash`, `prevHash`, `signature` fields and I have `audit:proof:view`  
**Then** I see immutability proof fields labeled as "Provided" (not "Verified") per AUD-SEC-009  
**And** if backend provides `proofVerified=true`, label changes to "Verified"

### Scenario 12: Raw payload restricted access
**Given** I am viewing an audit entry detail for `eventId=A-1` and I do not have `audit:payload:view`  
**Then** I do not see a raw payload section per AUD-SEC-004  
**And** if I have `audit:payload:view`  
**Then** I see raw payload rendered as escaped text (never interpreted as HTML)

---

## 13. Audit & Observability

### User-visible audit data (per audit domain)
- Display `actor`, `occurredAt`, `reasonCode`, `reasonNotes`, and references on detail view
- Display "Created at" as the audit timestamp (immutable)
- Display correlation id (`correlationId`) if present per AUD-SEC-012
- Display location name (derived from `locationId`) per AUD-SEC-002

### Status history
- Not applicable for audit entries (immutable)
- Export jobs have status history: PENDING → PROCESSING → COMPLETED/FAILED (per AUD-SEC-006)

### Traceability expectations (per AUD-SEC-006, AUD-SEC-012)
- Export includes same identifiers shown in UI (`eventId`, `orderId`, `invoiceId`, `paymentRef`, `workOrderId`, `appointmentId`, `mechanicId`, `movementId`, `correlationId`)
- UI logs client-side route/view events per workspace convention (screen viewed, export attempted, filters applied), without including sensitive payloads
- Correlation id (`correlationId`) displayed for tracing across systems per AUD-SEC-012

---

## 14. Non-Functional UI Requirements
- **Performance**: List queries must be paginated per audit domain; avoid loading entire dataset. Mandatory date range and indexed filter reduce result set size per AUD-SEC-005.
- **Accessibility**: All controls keyboard accessible; table and filter controls have labels; export action reachable without mouse.
- **Responsiveness**: Works on tablet widths typical for POS back office; table columns may collapse into stacked rows.
- **i18n/timezone/currency** (per AUD-SEC-005, audit domain): Timestamps displayed in user's timezone (source timezone is UTC); amounts formatted with currency symbol if `currencyUomId` provided; localized date/time pickers for date range inputs.
- **Security** (per AUD-SEC-001, AUD-SEC-002, AUD-SEC-003, AUD-SEC-004): Tenant isolation enforced server-side; location scoping enforced server-side; authorization checks per permission strings; raw payload and redacted fields rendered as escaped text (never interpreted as HTML).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state message and "Clear filters" action; qualifies as UI ergonomics only.
- SD-UX-PAGINATION: Use paginated list with default page size per app convention; safe as non-domain UX.
- SD-ERR-STANDARD-MAPPING: Map 401/403/5xx to standard unauthorized/generic error UI; safe as generic error handling.

---

## 16. Open Questions — RESOLVED via Audit Domain Business Rules

All open questions from the original draft have been resolved by consulting the audit domain business rules (`durion/domains/audit/.business-rules/AGENT_GUIDE.md`):

1. **Backend contract**: ✅ Resolved. See "Service Contracts" section, which maps to audit domain "API Expectations (Normative)".
2. **Entity schema**: ✅ Resolved. `AuditLog`, `PricingSnapshot`, `PricingRuleTrace` are normative entities per audit domain "Key Entities / Concepts".
3. **Event types**: ✅ Resolved. Controlled vocabulary from `GET /audit/meta/eventTypes` per AUD-SEC-010.
4. **Authorization**: ✅ Resolved. Permission strings and role guidance per AUD-SEC-003; cross-location permission per AUD-SEC-002.
5. **Export format & delivery**: ✅ Resolved. CSV required, async jobs per AUD-SEC-006.
6. **Reference linking**: ✅ Resolved. Use UUIDv7 identifiers denormalized onto `AuditLog` per AUD-SEC-007; deep-link metadata per AUD-SEC-011.
7. **Sensitive payload visibility**: ✅ Resolved. Raw payload restricted to `audit:payload:view`, redacted fields per AUD-SEC-004.
8. **Date range/filter guardrails**: ✅ Resolved. Mandatory date range (max 90 days) + at least one indexed filter per AUD-SEC-005.
9. **Location scoping**: ✅ Resolved. Default to user's current location; cross-location search requires explicit permission and filters per AUD-SEC-002.
10. **Tenant isolation**: ✅ Resolved. Enforced server-side for all reads/writes per AUD-SEC-001.
11. **Immutability proof fields**: ✅ Resolved. Display-only with `audit:proof:view`; label as "Provided" not "Verified" unless backend confirms per AUD-SEC-009.
12. **Pricing evidence access**: ✅ Resolved. Separate permissions (`audit:pricing-snapshot:view`, `audit:pricing-trace:view`), size limits, pagination per AUD-SEC-008.
13. **Correlation/trace context**: ✅ Resolved. W3C Trace Context standard per AUD-SEC-012.

**No STOP phrase**: All clarifications resolved via domain business rules. Story is ready for implementation.

---

## 17. Original Story (For Traceability)

Title: [FRONTEND] [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/65  
Labels: frontend, story-implementation, payment  

### Story Description (Original)
As an **Auditor**, I want an audit trail so that financial exceptions are explainable.
- Record who/when/why for overrides/refunds/cancels
- Exportable report
- Audit entries append-only
- Drilldown by order/invoice
- Payment refs included

**Note**: No backend matches found in original story. Backend contracts have been defined in this rewrite per audit domain business rules.

---

## 18. Implementation Notes for Developers

### Moqui Screen Structure Recommendations
- Use Moqui `form-list` for audit trail list with server-side pagination
- Use Moqui `form-single` for detail views (read-only)
- Use Moqui `transition` for all service calls
- Use Moqui `conditional-field` for permission-gated fields (raw payload, proof fields, cross-location filter)
- Use Moqui `parameter` for deep-link query params
- Use Moqui `actions` to enforce permission checks before rendering

### Permission Checks in Moqui
```xml
<screen>
  <actions>
    <if condition="!ec.user.hasPermission('audit:log:view')">
      <return error="true" message="You do not have access to Audit Trail"/>
    </if>
  </actions>
  <!-- screen content -->
</screen>
```

### Export Job Polling Pattern
1. POST `/audit/export/request` → get `exportId`
2. Poll `GET /audit/export/status?exportId=...` every 2-5 seconds
3. When `status=COMPLETED`, show download link
4. GET `/audit/export/download?exportId=...` for file

---

## 19. Testing Guidance

### Unit Tests
- Service contract validation: verify mandatory date range, indexed filter, max 90-day window
- Permission checks: verify screens/transitions enforce correct permissions
- Data transformations: verify timezone display, currency formatting, reason code mapping

### Integration Tests
- Search with various filter combinations
- Deep-link from Order/Invoice/Work Order to Audit Trail
- Export job: request → poll → download
- Pricing evidence drilldown: snapshot → trace
- Authorization: unauthorized user blocked, authorized user succeeds

### End-to-End Tests
- Compliance Auditor workflow: navigate → search → detail → pricing evidence → export
- Shop Manager workflow: navigate → search → detail (no export)
- Cross-location search with permission
- Date range validation error handling
- Empty state display

---

## 20. Domain Agent Attribution

This story was authored by the **Story Authoring Agent** in consultation with:
- **Security Domain Agent** (for authorization model per AUD-SEC-003)
- **Audit Domain Agent** (for all audit-specific business rules per `durion/domains/audit/.business-rules/AGENT_GUIDE.md`)

All open questions were resolved by referencing normative business rules in the audit domain. No clarification issues were required.

---

## 21. Handoff Summary

**Story Status**: ✅ Ready for Implementation

**Required Permissions** (per AUD-SEC-003):
- `audit:log:view`, `audit:log:view-detail`
- `audit:export:execute`, `audit:export:download`
- `audit:pricing-snapshot:view`, `audit:pricing-trace:view`
- `audit:payload:view` (restricted)
- `audit:proof:view` (display-only)
- `audit:scope:cross-location` (for cross-location search)

**Backend Contracts Required** (per audit domain):
- `GET /audit/logs/search`, `GET /audit/logs/detail`
- `POST /audit/export/request`, `GET /audit/export/status`, `GET /audit/export/download`
- `GET /audit/meta/eventTypes`, `GET /audit/meta/reasonCodes`, `GET /audit/meta/locations`
- `GET /audit/pricing/snapshot`, `GET /audit/pricing/trace`

**Screens to Create**:
- `apps/pos/screen/audit/AuditTrail.xml`
- `apps/pos/screen/audit/AuditTrailDetail.xml`
- `apps/pos/screen/audit/PricingSnapshot.xml`
- `apps/pos/screen/audit/PricingTrace.xml`
- `apps/pos/screen/audit/ExportStatus.xml`

**Screens to Modify** (add audit trail drilldown link):
- `OrderDetail.xml`, `InvoiceDetail.xml`, `WorkOrderDetail.xml`, `AppointmentDetail.xml`, `MechanicDetail.xml`, `MovementDetail.xml`, `EstimateLineDetail.xml`

**Files Created for Story #65**:
- `/home/n541342/IdeaProjects/durion/.story-work/65/FINAL_STORY.md` (this file)

---

**End of Story**
