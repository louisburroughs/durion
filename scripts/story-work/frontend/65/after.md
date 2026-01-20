STOP: Clarification required before finalization

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

**Title**: Security: Audit Trail for Price Overrides, Refunds, and Cancellations (Frontend, Moqui Screens)

**Primary Persona**: Auditor (secondary: Store Manager, Cashier; Compliance/Security Reviewer)

**Business Value**: Financial exceptions (price overrides, refunds, cancellations) are explainable, traceable, and reviewable with immutable audit evidence for investigations, internal controls, and dispute resolution.

---

## 2. Story Intent

### As a / I want / So that
- **As an Auditor**, I want a centralized, append-only audit trail for price overrides, refunds, and cancellations, so that I can explain financial exceptions with who/when/why and supporting references.

### In-scope
- Moqui **screens + navigation** to:
  - Search/filter audit entries
  - Drill down from an **order and/or invoice** to related audit entries
  - View audit entry details including actor, timestamp, reason, and payment/accounting references
  - Export audit entries report (format TBD)
- UI enforcement of **append-only** behavior (no edit/delete actions exposed)
- Basic error handling and empty states

### Out-of-scope
- Defining backend persistence, entity schema, or write-side generation of audit entries (no backend matches found)
- Authoring/refactoring the business logic that determines *when* an audit entry is created (assumed backend responsibility)
- Permission model details beyond what’s required to gate UI routes (requires clarification)

---

## 3. Actors & Stakeholders
- **Auditor**: Primary consumer; searches, filters, exports, reviews drilldowns.
- **Store Manager**: Investigates exceptions for a store/day/cashier; may export.
- **Cashier**: Typically not allowed to access global audit; may see limited “why required” prompts elsewhere (write-side not in scope).
- **Security/Compliance Admin**: Defines access policies/roles, retention, export restrictions (clarification required).
- **Accounting**: Needs references to invoices, journals, or accounting documents (integration references).

---

## 4. Preconditions & Dependencies
- Moqui frontend app is running with authenticated user context.
- Backend provides:
  - A read API/service to query audit entries (paging/sorting/filtering)
  - A read API/service to fetch audit entry detail
  - A linkable identifier for **order**, **invoice**, and **payment references**
  - An export endpoint or a query that can be exported server-side (clarification)
- Authorization/permissions available in session (e.g., user has roles/permissions)
- Entities mentioned (AuditLog, ExceptionReport, ReferenceIndex) exist or equivalents exist (clarification required due to “No backend matches found”).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Security → Audit Trail**
- Contextual drilldowns:
  - From **Order Detail** screen: “View Audit Trail”
  - From **Invoice Detail** screen: “View Audit Trail”
  - (Optional) From **Payment Detail** screen: “View Audit Trail” (clarification)

### Screens to create/modify
1. **New Screen**: `apps/pos/screen/security/AuditTrail.xml`
   - Search/list view with filters + results table + export action
2. **New Screen**: `apps/pos/screen/security/AuditTrailDetail.xml`
   - Detail view for a single audit entry (read-only)
3. **Modify Screen** (if exists): `OrderDetail` screen to add transition/link to audit trail filtered by orderId
4. **Modify Screen** (if exists): `InvoiceDetail` screen to add transition/link to audit trail filtered by invoiceId

> Exact screen paths depend on repo conventions (clarification if structure differs).

### Navigation context
- Audit Trail list supports deep links with query params:
  - `orderId`, `invoiceId`, `paymentId/ref`, `eventType`, `dateFrom/dateTo`, `userId`
- Detail screen accessible by `auditLogId` (or equivalent PK)

### User workflows
**Happy path (Auditor search → drilldown → export)**
1. Auditor opens Security → Audit Trail.
2. Applies filters (date range, event type, store/location, user).
3. Opens a result row to view detail.
4. From detail, navigates to referenced order/invoice/payment.
5. Exports results matching current filters.

**Alternate paths**
- Deep link from Order Detail → Audit Trail pre-filtered to that order
- Empty results: show “No audit entries match filters”
- Unauthorized user: show “Not authorized” and block access

---

## 6. Functional Behavior

### Triggers
- Screen load of Audit Trail list (initial query)
- Filter change + “Search” action (re-query)
- Row click “View details” (load detail)
- “Export” action (export current result set based on filters)

### UI actions
- Filters (exact set depends on backend fields; see Data Requirements)
- Results table:
  - Sort by timestamp desc by default
  - Pagination controls
  - Columns: event type, timestamp, actor, reason summary, order/invoice/payment refs
- Detail view:
  - Immutable fields displayed
  - Reference links to related records

### State changes (frontend)
- No mutation of audit entries
- Local UI state only: selected filters, paging, loading/error states

### Service interactions
- `findAuditEntries` (list query)
- `getAuditEntry` (detail)
- `exportAuditEntries` (export)
- `getOrder`/`getInvoice` (for reference linking, if needed)

(Exact service names/paths require clarification due to missing backend contract.)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Date range:
  - `dateFrom <= dateTo`
  - If only one boundary provided, treat the other as open-ended (allowed)
- At least one filter is **not required** (unless performance policy requires it; clarification)
- `eventType` must be within supported set (from backend or fixed enum)

### Enable/disable rules
- Export button disabled when:
  - No results loaded OR export is in progress
- “View details” disabled when row lacks a resolvable audit entry id (should not happen; treat as error)

### Visibility rules
- Screens visible only to authorized roles/permissions (policy TBD)
- Payment reference fields shown only if present (do not fabricate)

### Error messaging expectations
- Query failure: show non-technical message + correlation id if available
- Unauthorized: show “You do not have access to Audit Trail.”
- Export failure: show error and keep current filters intact

---

## 8. Data Requirements

### Entities involved (conceptual; backend mapping required)
- **AuditLog**: append-only audit events
- **ExceptionReport**: exportable reporting view (may be derived)
- **ReferenceIndex**: mapping between audit events and domain records (order/invoice/payment)

### Fields (type, required, defaults)
**Audit Entry (list + detail)**
- `auditLogId` (string/UUID, required) — primary key for drilldown
- `eventType` (enum/string, required) — one of:
  - `PRICE_OVERRIDE`, `REFUND`, `CANCELLATION` (names TBD)
- `eventTs` (datetime w/ timezone, required) — display in user timezone
- `actorUserId` (string, required)
- `actorUserDisplayName` (string, optional but preferred)
- `reasonCode` (string, optional; depends on policy)
- `reasonText` (string, required for exceptions? policy unclear)
- `orderId` (string, optional)
- `orderExternalRef` (string, optional)
- `invoiceId` (string, optional)
- `invoiceExternalRef` (string, optional)
- `paymentId` (string, optional)
- `paymentRef` (string, optional; e.g., processor reference)
- `amount` (decimal, optional; currency handling unclear)
- `currencyUomId` (string, optional)
- `locationId` / `storeId` (string, optional but likely important)
- `terminalId` (string, optional)
- `notes` (string, optional)
- `payloadJson` (string/json, optional) — only if allowed for auditors (security concern; clarification)

**Defaults**
- Default sort: `eventTs desc`
- Default date range: none (unless performance policy requires; clarification)

### Read-only vs editable
- All audit entry fields are **read-only** in UI.
- No create/update/delete actions exposed in these screens.

### Derived/calculated fields
- “Reason summary” derived from `reasonCode` + truncated `reasonText`
- “Reference display” derived from available order/invoice/payment refs

---

## 9. Service Contracts (Frontend Perspective)

> No backend matches found; these are **required contracts** that must be implemented or mapped.

### Load/view calls
1. **List query**
   - Service: `security.AuditTrail.find` (placeholder)
   - Inputs:
     - `eventType?`, `dateFrom?`, `dateTo?`, `actorUserId?`, `orderId?`, `invoiceId?`, `paymentRef?`, `locationId?`
     - `pageIndex` (int), `pageSize` (int)
     - `orderBy` (string; default `-eventTs`)
   - Output:
     - `items[]` (Audit Entry summary)
     - `totalCount` (int)

2. **Detail fetch**
   - Service: `security.AuditTrail.get` (placeholder)
   - Inputs: `auditLogId` (required)
   - Output: full Audit Entry

### Create/update calls
- None (append-only; UI provides no mutations)

### Submit/transition calls
- Export:
  - Service: `security.AuditTrail.export` (placeholder)
  - Inputs: same filters as list query (+ desired format)
  - Output: file download stream or URL to download

### Error handling expectations
- Map backend validation errors to field-level messages when possible (date range, invalid enum)
- For 401/403: redirect to login or show unauthorized screen per app convention
- For 5xx: show generic failure, allow retry

---

## 10. State Model & Transitions

### Allowed states
- Audit entries themselves have no UI-managed state (immutable records).

### Role-based transitions
- **Route access control**:
  - `AuditTrail` and `AuditTrailDetail` require audit permission/role (TBD)
- Drilldown transitions:
  - From audit entry → Order Detail / Invoice Detail / Payment Detail only if user has access to those screens

### UI behavior per state
- Loading: show progress indicator, disable export/search until initial load completes
- Loaded: show results + paging
- Error: show error panel with retry
- Empty: show empty state with “Clear filters” action

---

## 11. Alternate / Error Flows

1. **Validation failure (date range invalid)**
   - Prevent search; show inline error on date inputs.

2. **Concurrency conflicts**
   - Not applicable (read-only), except:
     - Audit entry referenced order/invoice missing/deleted: show “Referenced record not found” when navigating.

3. **Unauthorized access**
   - If user lacks permission:
     - Block route and show unauthorized message
     - Do not leak existence of audit entries (no partial results)

4. **Empty states**
   - No entries found: show empty message and retain filters.

5. **Export too large / restricted**
   - If backend returns “too many rows” or “export restricted”:
     - Show actionable error (“Narrow date range and try again” / “You do not have export permission”)

---

## 12. Acceptance Criteria

### Scenario 1: Auditor views audit trail list
**Given** I am authenticated as a user with audit trail access  
**When** I navigate to Security → Audit Trail  
**Then** I see a paginated list of audit entries sorted by most recent first  
**And** each entry shows event type, timestamp, actor, and any available order/invoice/payment references

### Scenario 2: Filter by order drilldown
**Given** I am viewing an Order Detail page for order `O-123`  
**When** I click “View Audit Trail”  
**Then** I am taken to the Audit Trail screen with results filtered to `orderId=O-123`  
**And** all matching audit entries are displayed (or an empty state if none)

### Scenario 3: View audit entry detail
**Given** I am on the Audit Trail list and there is an entry with id `A-1`  
**When** I open the audit entry detail for `A-1`  
**Then** I see read-only fields including who/when/why  
**And** I see any available payment reference(s)  
**And** I can navigate to referenced order/invoice/payment screens when identifiers exist

### Scenario 4: Append-only enforcement in UI
**Given** I am on the Audit Trail list or detail screen  
**Then** there are no UI actions to edit or delete an audit entry  
**And** direct URL attempts to any edit route for audit entries are not available (route does not exist or is denied)

### Scenario 5: Export report
**Given** I have loaded audit trail results with filters applied  
**When** I click Export  
**Then** an export is generated matching the current filters  
**And** I receive a downloadable file (or download link)  
**And** if export fails, I see an error message and can retry without losing filters

### Scenario 6: Unauthorized user blocked
**Given** I am authenticated without audit trail permission  
**When** I navigate to Security → Audit Trail  
**Then** I am shown an unauthorized message  
**And** no audit data is displayed

---

## 13. Audit & Observability

### User-visible audit data
- Display `actor`, `timestamp`, `reason`, and references on detail view
- Display “Created at” as the audit timestamp (no updates shown)

### Status history
- Not applicable unless backend provides multi-step exception workflows (not in scope)

### Traceability expectations
- Export should include the same identifiers shown in UI (auditLogId, orderId, invoiceId, paymentRef)
- UI should log client-side route/view events per workspace convention (screen viewed, export attempted), without including sensitive payloads

---

## 14. Non-Functional UI Requirements
- **Performance**: List queries must be paginated; avoid loading entire dataset in the browser.
- **Accessibility**: All controls keyboard accessible; table and filter controls have labels; export action reachable without mouse.
- **Responsiveness**: Works on tablet widths typical for POS back office; table columns may collapse into stacked rows.
- **i18n/timezone/currency**:
  - Timestamps displayed in user’s timezone (source timezone must be clear from backend).
  - Amounts formatted with currency if provided; do not infer currency.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message and “Clear filters” action; qualifies as UI ergonomics only; impacts UX Summary, Alternate/Error Flows.
- SD-UX-PAGINATION: Use paginated list with default page size per app convention; safe as it’s non-domain UX; impacts Functional Behavior, Service Contracts.
- SD-ERR-STANDARD-MAPPING: Map 401/403/5xx to standard unauthorized/generic error UI; safe as generic error handling; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend contract**: What are the exact Moqui services (names, parameters, outputs) for listing audit entries, fetching detail, and exporting?
2. **Entity schema**: Are `AuditLog`, `ExceptionReport`, and `ReferenceIndex` real Moqui entities in this project? If yes, what are their primary keys and key fields?
3. **Event types**: What is the canonical set of auditable exception event types (exact codes) and required fields per type (e.g., is “reason” mandatory)?
4. **Authorization**: Which roles/permissions can (a) view audit trail, (b) export, and (c) drill into linked order/invoice/payment detail?
5. **Export format & delivery**: CSV vs XLSX vs PDF? Should export be synchronous download, async job with notification, or “download link”?
6. **Reference linking**: What identifiers should be treated as authoritative for drilldowns (orderId vs orderExternalRef, invoiceId vs invoice number, paymentId vs paymentRef)?
7. **Sensitive payload visibility**: Should auditors see raw payload JSON/metadata, or only curated fields? Any redaction requirements?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/65  
Labels: frontend, story-implementation, payment  

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations

**Domain**: payment

### Story Description

/kiro  
# User Story

## Narrative  
As an **Auditor**, I want an audit trail so that financial exceptions are explainable.

## Details  
- Record who/when/why for overrides/refunds/cancels.  
- Exportable report.

## Acceptance Criteria  
- Audit entries append-only.  
- Drilldown by order/invoice.  
- Payment refs included.

## Integrations  
- Accounting and payment references included.

## Data / Entities  
- AuditLog, ExceptionReport, ReferenceIndex

## Classification (confirm labels)  
- Type: Story  
- Layer: Experience  
- domain :  Point of Sale

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript  
- Use Quasar framework for UI components  
- Integrate with Moqui Framework backend  
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API  
- TypeScript 5.x  
- Quasar v2.x  
- Moqui Framework integration

---  
*This issue was automatically created by the Durion Workspace Agent*

No backend matches found.