STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:audit
- status:draft

### Recommended
- agent:security
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

---

## 1. Story Header

### Title
Security: Immutable Audit Trail UI for Inventory Movements and Workorder Links (Drilldown + Export)

### Primary Persona
Auditor (read-only, compliance-oriented)

### Business Value
Enables dispute resolution and compliance by providing immutable, append-only audit trails that can be searched/drilled down by workorder/product/location/user and exported for external review.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Auditor  
- **I want** immutable (append-only) audit trails for inventory movements and their workorder links, with drilldowns by workorder/product/location/user and export  
- **So that** I can reconstruct what happened, by whom, and why, to resolve disputes and support compliance.

### In-scope
- Frontend screens to:
  - Search and view audit logs for movement details and references (workorder links).
  - Drill down (filter + navigate) by **workorder**, **product**, **location**, **user**.
  - Export audit results (at minimum CSV; format(s) TBD).
- UI enforcement of “append-only” semantics:
  - No create/edit/delete actions exposed from UI for audit records.
  - Read-only presentation and navigation only.
- Moqui screen/form/service wiring for list/detail + export invocation.

### Out-of-scope
- Defining or implementing backend persistence, immutability guarantees, or event generation (no backend matches found).
- Retroactive backfill/migration of historical audit data.
- Defining permission model/roles beyond “Auditor can view/export” (requires clarification).
- Any accounting/ledger calculation policies beyond displaying existing “ledger references”.

---

## 3. Actors & Stakeholders
- **Auditor**: primary user; needs read-only visibility, filters, export.
- **Operations Manager** (stakeholder): may use audit during disputes.
- **Inventory Clerk** (stakeholder): may be subject of audit (user drilldown).
- **Security/Compliance** (stakeholder): cares about immutability, access control, traceability.
- **Workexec system/process** (external stakeholder): “uses ledger references for traceability”.

---

## 4. Preconditions & Dependencies
- Moqui backend must expose read-only services or screen data sources for:
  - `AuditLog` retrieval (list + detail).
  - `MovementReferenceIndex` retrieval (movement ↔ references, and drilldown keys).
  - `WorkorderRef` retrieval (workorder link metadata).
- Backend must define:
  - Stable identifiers for movement, workorder, product, location, user.
  - What constitutes “movement” in UI terms (movementId? inventoryTransferId? etc.).
  - Export endpoint/behavior (sync download vs async job).
- Authentication must exist; authorization rules for audit access must be specified (blocking).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Security/Compliance → Audit Trail**
- Contextual links (if available in existing app):
  - From Movement detail → “View Audit Trail for this Movement”
  - From Workorder detail → “View Movement Audit Trail for this Workorder”
  - From Product/Location/User pages → “View Audit Trail” pre-filtered

### Screens to create/modify
Create (names are proposed; adjust to repo conventions):
- `component://durion/screen/audit/AuditTrail.xml`
  - Subscreens:
    - `AuditTrailList.xml` (search + results table)
    - `AuditTrailDetail.xml` (read-only record detail)
    - `AuditTrailExport.xml` (transition/service to generate/download export)

Modify (optional, if existing):
- Movement detail screen(s): add transition/link to `AuditTrailList` with movement filter.
- Workorder detail screen(s): add transition/link to `AuditTrailList` with workorder filter.

### Navigation context
- `AuditTrailList` supports deep-link query params:
  - `workorderId`, `productId`, `locationId`, `userId`, `movementId`
  - `fromTs`, `thruTs`
- Clicking a row navigates to `AuditTrailDetail` for that audit record.

### User workflows
**Happy path (search + drilldown):**
1. Auditor opens Audit Trail.
2. Sets filters (date range + any of workorder/product/location/user).
3. Reviews results list.
4. Clicks a record to view details; optionally jumps to related workorder/movement (read-only) if links exist.
5. Exports filtered results.

**Alternate path (start from workorder):**
1. Auditor opens Workorder detail.
2. Clicks “Audit Trail”.
3. Lands in Audit Trail list pre-filtered to that workorder.

**Alternate path (no results):**
- UI shows empty state + keeps filter controls visible; export disabled until results exist (or clarified).

---

## 6. Functional Behavior

### Triggers
- Screen load of `AuditTrailList` triggers a search (only if minimal criteria met; criteria TBD) or shows blank state awaiting filters (blocking).
- “Search” button triggers service call to load list.
- Clicking a record triggers load of record detail.
- “Export” triggers export service call for current filters.

### UI actions
On `AuditTrailList`:
- Filter inputs:
  - Workorder (by ID or lookup)
  - Product (by ID or lookup)
  - Location (by ID or lookup)
  - User (by ID or lookup)
  - Movement (by ID)
  - Date range (from/thru)
- Results table columns (proposed; backend must confirm available fields):
  - Timestamp
  - Actor (user)
  - Action/Event type
  - Entity type (Movement / WorkorderRef / ReferenceIndex)
  - Primary entity ID (movementId/workorderId/etc.)
  - Location (if applicable)
  - Product (if applicable)
  - Ledger reference (if applicable)
- Row action: View details
- Button: Export (CSV/JSON/PDF TBD)

On `AuditTrailDetail`:
- Read-only fields: all raw audit payload + normalized fields
- Related links (if IDs present): movement, workorder, product, location, user

### State changes
- None in domain data (audit is immutable). Only client-side state: filters, paging, selected record.

### Service interactions
- `findAuditLogs` (list)
- `getAuditLog` (detail)
- `exportAuditLogs` (export)
> Service names are placeholders; backend contract required.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Date range validation:
  - `fromTs` must be ≤ `thruTs`
  - timezone handling must be explicit (blocking: which timezone to interpret filters in)
- At least one filter requirement (to prevent unbounded queries) is **unknown**:
  - If backend requires a date range and/or at least one drilldown key, UI must enforce it (blocking).

### Enable/disable rules
- “Search” enabled when validation passes and required criteria satisfied (criteria TBD).
- “Export” enabled when:
  - A valid filter set exists, and
  - Search has returned at least one result (or backend supports exporting empty set; TBD).

### Visibility rules
- No “Add/Edit/Delete” controls anywhere for audit entities.
- If user lacks permission: show “Not authorized” screen and do not leak counts/fields.

### Error messaging expectations
- Show backend validation errors inline on filter form when mapped to fields.
- Show generic error banner for unexpected errors with a correlation/trace id if available.

---

## 8. Data Requirements

### Entities involved (as provided)
- `AuditLog`
- `MovementReferenceIndex`
- `WorkorderRef`

### Fields (type, required, defaults)
**AuditLog** (minimum needed; backend must confirm exact schema)
- `auditLogId` (string/ID, required, read-only)
- `eventTs` (datetime, required, read-only)
- `eventType` (string, required, read-only)
- `actorUserId` (string/ID, optional?, read-only)
- `entityType` (string, required, read-only)
- `entityId` (string/ID, required, read-only)
- `workorderId` (string/ID, optional, read-only) — if denormalized or via reference index
- `productId` (string/ID, optional, read-only)
- `locationId` (string/ID, optional, read-only)
- `movementId` (string/ID, optional, read-only)
- `ledgerRef` (string, optional, read-only)
- `payloadJson` (text/json, optional, read-only)
- `hash` / `prevHash` (string, optional, read-only) — if immutability chain is exposed (blocking)

**MovementReferenceIndex / WorkorderRef**
- Fields must support drilldown keys and linking:
  - `movementId`, `workorderId`, `referenceType`, `referenceId`, etc. (TBD)

### Read-only vs editable by state/role
- All fields are **read-only for all roles** in frontend.
- Only authorized roles can view/export (exact roles TBD).

### Derived/calculated fields
- Display-friendly actor name derived from userId (requires lookup service; TBD).
- Display-friendly product/location names derived from IDs (requires lookup service; TBD).

---

## 9. Service Contracts (Frontend Perspective)

> No backend matches found; contracts are blocking. Below are required contracts.

### Load/view calls
1. **List audit logs**
   - Input: filters `{fromTs, thruTs, workorderId, productId, locationId, userId, movementId, pageIndex, pageSize, sort}`
   - Output: `{items: AuditLogSummary[], totalCount}`
   - Must support server-side paging and sorting.

2. **Get audit log detail**
   - Input: `{auditLogId}`
   - Output: `{auditLog: AuditLogDetail}`

3. **Lookup helpers** (if UI includes search/select controls)
   - Users: `{query}` → `{userId, displayName}`
   - Products: `{query}` → `{productId, productName}`
   - Locations: `{query}` → `{locationId, locationName}`
   - Workorders: `{query}` → `{workorderId, workorderName/code}`

### Create/update calls
- None (UI must not call any create/update for audit).

### Submit/transition calls
4. **Export audit logs**
   - Input: same filters as list + `format`
   - Output options (backend must choose one):
     - (A) immediate file download response, or
     - (B) returns `exportJobId` and a follow-up download URL/status polling endpoint.

### Error handling expectations
- 401/403: route to unauthorized screen; no partial rendering.
- 400 validation: show field-level errors where possible.
- 409 concurrency: not expected for read-only, but handle stale export job state if async.
- 5xx: show error banner; allow retry.

---

## 10. State Model & Transitions

### Allowed states
- Not applicable to audit entities in UI (immutable, append-only).

### Role-based transitions
- Role/action matrix (blocking):
  - Auditor: View list, View detail, Export
  - Others: ? (unknown)

### UI behavior per state
- If not authorized: show unauthorized.
- If authorized: read-only access, no mutation controls.

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid date range → block search/export; show inline message: “From must be before Thru.”
- Missing required criteria (if mandated) → block search; message: “Select at least one filter” or “Date range required” (TBD).

### Concurrency conflicts
- Export async job: job expired/not found → show message and allow re-run export.

### Unauthorized access
- Direct URL access to detail without permission → unauthorized view; do not reveal record existence.

### Empty states
- No results found → show “No audit records match these filters.”
- No backend service available → show error banner + retry.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario: View audit trail list with filters
Given I am authenticated as a user with audit viewing permission  
And I navigate to the Audit Trail screen  
When I enter a valid date range and a workorder filter  
And I click Search  
Then I see a paginated list of audit records matching the filters  
And each row shows timestamp, actor, event type, entity type, and entity id

### Scenario: Drill down to audit detail
Given I have searched and see at least one audit record  
When I click an audit record row  
Then I am navigated to the Audit Trail Detail screen  
And I see the full read-only audit payload and all available reference fields  
And there are no edit or delete controls

### Scenario: Export filtered audit results
Given I am viewing audit results for a valid filter set  
When I click Export and choose CSV  
Then an export is generated for the same filter set  
And I can download the exported file (or I see an export job status until it is downloadable)

### Scenario: Prevent mutation from the UI
Given I am on any Audit Trail screen  
Then I cannot create, edit, or delete any audit record  
And no mutation actions are present in the UI

### Scenario: Unauthorized user is blocked
Given I am authenticated as a user without audit viewing permission  
When I navigate to the Audit Trail screen or a deep link to an audit record  
Then I see an unauthorized message  
And no audit data is displayed

### Scenario: Invalid date range is rejected
Given I am on the Audit Trail screen  
When I set From to a value after Thru  
Then Search is disabled (or returns a validation error)  
And I see an inline validation message explaining the issue

---

## 13. Audit & Observability

### User-visible audit data
- Display immutable audit fields including timestamp, actor, event type, entity identifiers, and ledger references where present.

### Status history
- Not applicable (audit records are history themselves). If audit chains (hash/prevHash) exist, display them read-only (blocking: whether present).

### Traceability expectations
- All drilldowns must preserve filters in URL query params for shareable links.
- Log frontend actions (search, detail view, export) with correlation id if Moqui provides one.

---

## 14. Non-Functional UI Requirements

- **Performance:** List queries must be paginated; avoid loading full dataset.
- **Accessibility:** All interactive controls keyboard-navigable; table has accessible labels; export action reachable without pointer.
- **Responsiveness:** Filters and results usable on tablet widths; table supports horizontal scroll if needed.
- **i18n/timezone:** Timestamps rendered in a clearly defined timezone (blocking which); date pickers respect locale.
- **Security:** No sensitive payload fields should be displayed if backend marks them restricted (blocking: field-level security rules).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard “No results” empty state with guidance to adjust filters; qualifies as safe because it does not change domain policy. (Impacted: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION: Use server-side pagination defaults (pageSize 25, configurable) to prevent unbounded loads; safe because it’s UI ergonomics and performance only. (Impacted: Functional Behavior, Service Contracts)
- SD-ERR-GENERIC-BANNER: Show a generic error banner for unexpected failures with retry; safe because it doesn’t alter domain behavior. (Impacted: Business Rules, Alternate / Error Flows)

---

## 16. Open Questions

1. **Domain label conflict:** Provided context says “Domain: user” and “Classification: Domain: Inventory Management”; should this story be owned by **domain:audit** (security/compliance) or **domain:inventory**? (Blocking)
2. **Backend contracts:** What are the exact Moqui service names/parameters for:
   - list audit logs, get audit detail, export audit logs, and lookups? (Blocking)
3. **Authorization:** What permission(s)/role(s) grant view and export access? Is field-level redaction required for `payloadJson`? (Blocking)
4. **Filter constraints:** Must the UI require a date range and/or at least one drilldown key to search/export? What maximum date range is allowed? (Blocking; policy)
5. **Immutability proof:** Does backend expose hash-chain fields (hash/prevHash/signature) that should be displayed/verified in UI? (Blocking)
6. **Export format & behavior:** Required export formats (CSV only vs CSV+JSON/PDF)? Synchronous download vs async job? Filename convention? (Blocking)
7. **Entity identifiers:** What are the canonical IDs for movement/workorder/product/location/user in this system, and are they always present on `AuditLog` or only via reference entities? (Blocking)
8. **Navigation targets:** Should drilldown links navigate to Movement/Workorder/Product/Location/User detail screens? If yes, what are the route/screen names? (Blocking)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Immutable Audit Trail for Movements and Workorder Links
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/86
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Immutable Audit Trail for Movements and Workorder Links

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Auditor**, I want immutable audit trails for movements and workorder links so that disputes can be resolved.

## Details
- Append-only logs for move details and references.
- Drilldown by workorder/product/location/user.

## Acceptance Criteria
- Audit append-only.
- Drilldown supported.
- Exportable.

## Integrations
- Workexec uses ledger references for traceability.

## Data / Entities
- AuditLog, MovementReferenceIndex, WorkorderRef

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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

====================================================================================================


No backend matches found.