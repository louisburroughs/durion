## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Inventory Ledger: View Stock Movements (Append-Only) and Support Adjustment Request/Posting UI Hooks

**Primary Persona:** Inventory Manager

**Business Value:** Provide an auditable, explainable history of on-hand changes by product/location, enabling operational troubleshooting, compliance/audit review, and downstream consumers (e.g., Work Execution) to trace movements tied to source transactions.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Inventory Manager  
- **I want** to view all stock movements in an append-only inventory ledger (filterable by product, location, movement type, date range, and source transaction)  
- **So that** on-hand can be audited/explained and I can trace what changed, when, by whom, and why (especially for adjustments).

### In-scope
- A Moqui screen flow to **list and view** `InventoryLedgerEntry` records with:
  - Filtering/search (product, location, movementType, date range, sourceTransactionId/workorder line reference)
  - Row-level details (from/to, qty change, UOM, actor, timestamp, reasonCode)
  - Clear empty/loading/error states
- UI-level enforcement of **immutability** (no edit/delete actions)
- UI hooks for **Adjustment** flows consistent with backend reference:
  - Create adjustment request (draft/pending) UI entry point
  - Approve/post adjustment UI entry point
  - Permission-gated actions and reason code requirement surfaced in UI
- Navigation entry points from Inventory area, and deep linkability to a ledger entry detail screen

### Out-of-scope
- Implementing backend ledger creation/atomicity/replay logic (backend responsibility)
- Defining inventory valuation, reservation/allocation logic, or costing behavior
- Creating movement events (Receive/PutAway/Pick/Consume/Return/Transfer) UI flows unless already existing; this story only **displays ledger** and provides **adjustment request/post UI** entry points
- Report exports (CSV/PDF) unless explicitly requested later

---

## 3. Actors & Stakeholders
- **Inventory Manager (primary user):** audits movements; creates/approves adjustments.
- **Parts Counter / Staff (secondary):** views movement history to answer “what happened”.
- **Auditor (stakeholder):** needs immutable, traceable history.
- **Work Execution users/system (consumer):** links from work order line to movement history (view-only).
- **System actor:** some ledger entries are system-generated; UI must display actorId accordingly.

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend and has an active session.
- Product master records exist (referenced by `productId`).
- Storage locations exist (referenced by `fromLocationId` / `toLocationId`).
- Backend exposes read APIs/services for:
  - Listing ledger entries with filters and pagination
  - Fetching a single ledger entry by ID
  - Reason codes list for adjustments (or an enum endpoint)
  - Adjustment request creation and approval/posting (if UI is expected to initiate these)
- Permissions are available to frontend (either via a “current user permissions” endpoint, token claims, or Moqui security context).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Inventory module navigation: **Inventory → Ledger**
- Contextual entry points:
  - From Product detail: “View Ledger”
  - From Location detail: “View Ledger”
  - From Work Order Line (Work Execution area): “View Movement History” (view-only link with pre-applied filters)

### Screens to create/modify (Moqui)
1. **Screen:** `apps/pos/screen/inventory/Ledger.xml` (or equivalent per repo conventions)
   - Main list screen with filter form + results grid
2. **Screen:** `apps/pos/screen/inventory/LedgerEntryDetail.xml`
   - Read-only detail view of a ledger entry
3. **(Optional, if adjustments are frontend-initiated here)**:
   - `apps/pos/screen/inventory/Adjustments.xml` (list of adjustment requests)
   - `apps/pos/screen/inventory/AdjustmentDetail.xml` (create/request + approve/post)
   - If adjustments are managed elsewhere, then only provide action buttons that route to existing screens.

### Navigation context
- Breadcrumbs:
  - Inventory → Ledger
  - Inventory → Ledger → Entry `<ledgerEntryId>`
- Deep links:
  - `/inventory/ledger` with query params
  - `/inventory/ledger/entry/<ledgerEntryId>`

### User workflows
**Happy path (view ledger):**
1. User opens Ledger screen
2. Filters by product/location/date range
3. Reviews results and opens a row to see details

**Alternate path (work order movement history):**
1. User comes from work order line link
2. Ledger loads pre-filtered by `sourceTransactionId` (and/or workorderLineId if supported)
3. User inspects movements and navigates back

**Adjustment path (permission-gated):**
1. User with `INVENTORY_ADJUST_CREATE` clicks “New Adjustment”
2. Completes required fields (product, location, qty change, UOM, reasonCode)
3. Saves as pending/draft request
4. Approver with `INVENTORY_ADJUST_APPROVE` opens request and posts it
5. UI shows resulting posted status and ledger entry link (if returned)

---

## 6. Functional Behavior

### Triggers
- Screen load triggers ledger query with default filters (see Safe Defaults).
- Filter form submission triggers refreshed query.
- Row click triggers load of ledger entry details.
- Adjustment actions trigger create/approve/post service calls (if included).

### UI actions
- **Filter controls** (all optional unless specified):
  - `productId` (lookup/search)
  - `locationId` (single selection; applies to either from/to matching)
  - `movementType` (multi-select or single-select)
  - `dateFrom`, `dateTo` (UTC aware display; query in ISO)
  - `sourceTransactionId` (text)
- **Results grid**
  - Sort by `timestamp` descending by default
  - Columns: timestamp, movementType, productId (and name if available), qtyChange, UOM, from, to, actor, reasonCode, sourceTransactionId
- **Detail view**
  - Read-only display of all fields; highlight adjustment reason and actor

### State changes (frontend)
- No client-side mutation of ledger records
- Adjustment request UI: local form state changes; after submit, state updates from server response

### Service interactions
- `load` ledger entries list on screen enter and on filter changes
- `load` ledger entry by ID on detail screen
- `create` adjustment request (requires permission)
- `approve/post` adjustment request (requires permission)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Ledger list filters:
  - `dateFrom` must be <= `dateTo` when both set; otherwise show inline validation error and do not call backend.
- Adjustment request create:
  - `reasonCode` is **required**
  - `quantityChange` is **required** and must be non-zero (backend disallows zero-like ambiguity; UI should prevent submit if 0)
  - `productId` required
  - `locationId` required (maps to from/to per adjustment semantics; see Open Questions if unclear)
  - `unitOfMeasure` required
- Approval/post:
  - Must be in a pending state (UI disables action if not pending; still rely on backend enforcement)

### Enable/disable rules
- “New Adjustment” button visible/enabled only when user has `INVENTORY_ADJUST_CREATE`
- “Approve/Post” button visible/enabled only when user has `INVENTORY_ADJUST_APPROVE`
- Edit/delete controls for ledger entries: **never shown**
- If backend returns `PERMISSION_DENIED`/403, show an access error and keep user on page

### Visibility rules
- `reasonCode` field displayed prominently for `movementType = ADJUST`
- For non-adjust movements, `reasonCode` display as “—” when null
- `fromLocationId` and `toLocationId` may be null; UI must render “—” and not crash

### Error messaging expectations
- Map backend error codes/messages to user-readable text:
  - `PRODUCT_NOT_FOUND` → “Product not found.”
  - `LOCATION_NOT_FOUND` → “Location not found.”
  - `INSUFFICIENT_STOCK` → “Insufficient stock for this movement.”
  - `REASON_CODE_REQUIRED` → “Reason is required for adjustments.”
  - `PERMISSION_DENIED`/403 → “You don’t have permission to perform this action.”
- Unknown errors: show generic failure with correlation/trace id if provided

---

## 8. Data Requirements

### Entities involved (frontend read/write)
- **InventoryLedgerEntry** (read-only)
- **ReasonCode** (read-only list for adjustments)
- **AdjustmentRequest** (if adjustment request workflow is in frontend scope; otherwise only link out)

### Fields (type, required, defaults)
**InventoryLedgerEntry (read-only)**
- `ledgerEntryId` (UUID/string, required)
- `productId` (UUID/string, required)
- `timestamp` (UTC timestamp, required)
- `movementType` (enum string, required): `RECEIVE | PUT_AWAY | PICK | ISSUE | RETURN | TRANSFER | ADJUST`
- `quantityChange` (decimal, required)
- `unitOfMeasure` (string, required)
- `fromLocationId` (string, nullable)
- `toLocationId` (string, nullable)
- `actorId` (string, required)
- `reasonCode` (enum string, nullable except for ADJUST)
- `sourceTransactionId` (string, nullable)

**AdjustmentRequest (write) – minimal frontend needs**
- `productId` (required)
- `locationId` (required) *(exact mapping to from/to depends on backend contract; see Open Questions)*
- `quantityChange` (decimal, required, non-zero)
- `unitOfMeasure` (required)
- `reasonCode` (required)
- `notes` (optional, if supported)
- `requestedBy` (server-derived)
- `requestedAt` (server-derived)
- `status` (`PENDING`/`APPROVED`/`POSTED` etc.; server authoritative)

### Read-only vs editable by state/role
- Ledger entry fields: always read-only
- Adjustment request:
  - Editable only in draft/pending by creator (if backend supports); otherwise read-only after submit
  - Approval action only for users with approve permission

### Derived/calculated fields
- “Direction” derived in UI from `quantityChange` sign (Increase/Decrease)
- “Location” display:
  - For list filtering by location: include entries where either `fromLocationId == locationId` OR `toLocationId == locationId`

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/endpoints are not provided in inputs; the frontend must integrate with the backend contract. If Moqui is acting as backend façade, implement screens calling Moqui services that proxy to backend APIs.

### Load/view calls
1. **List ledger entries**
   - **Request params:** `productId?`, `locationId?`, `movementType?` (single or list), `dateFrom?`, `dateTo?`, `sourceTransactionId?`, `pageIndex`, `pageSize`, `sort=-timestamp`
   - **Response:** paginated list of `InventoryLedgerEntry` + total count
2. **Get ledger entry detail**
   - **Request:** `ledgerEntryId`
   - **Response:** `InventoryLedgerEntry`

3. **List reason codes**
   - **Response:** `[ { code, label, description? } ]` or enum list

### Create/update calls
4. **Create adjustment request**
   - **Request body:** productId, locationId, quantityChange, unitOfMeasure, reasonCode, notes?
   - **Response:** created adjustment request object, including its ID and status

### Submit/transition calls
5. **Approve/post adjustment**
   - **Request:** adjustmentRequestId (and optional concurrency token/version if supported)
   - **Response:** updated request status and (ideally) resulting `ledgerEntryId` link

### Error handling expectations
- Support standard HTTP:
  - 400 validation errors with field-level messages when possible
  - 403 for permission issues
  - 404 for missing resources
  - 409 for concurrency conflicts (if adjustment approvals are versioned)
- Frontend must show actionable message and preserve user input on validation failures

---

## 10. State Model & Transitions

### Ledger entries
- **State model:** immutable append-only records
- **Transitions:** none (no edit/delete)

### Adjustment requests (if in scope)
- **States (minimum):** `PENDING` → `POSTED` (or `APPROVED` then `POSTED` depending on backend)
- **Allowed transitions:**
  - Create: (none) → `PENDING` (requires `INVENTORY_ADJUST_CREATE`)
  - Post/Approve: `PENDING` → `POSTED` (requires `INVENTORY_ADJUST_APPROVE`)
- **Role/permission-based transitions:**
  - UI must check permission before rendering actions; backend enforces truth

### UI behavior per state
- `PENDING`: show “Approve/Post” if permitted; show read-only summary
- `POSTED`: show link to created ledger entry; actions disabled

---

## 11. Alternate / Error Flows

### Validation failures
- Invalid date range: block query; show inline message
- Adjustment missing reason: disable submit and/or show field error; backend error mapped if returned

### Concurrency conflicts
- If approve/post returns 409 (already posted/changed):
  - Reload request and show message: “This adjustment was updated by another user. Current status: <status>.”

### Unauthorized access
- If user opens adjustment screens without permission:
  - Show access denied state; do not attempt restricted service calls

### Empty states
- No ledger entries match filters:
  - Show “No movements found for the selected criteria.”
  - Provide “Clear filters” action

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View ledger list with default sorting
**Given** I am an authenticated Inventory Manager  
**When** I navigate to Inventory → Ledger  
**Then** I see a list of ledger entries sorted by timestamp descending  
**And** I can open an entry to view its read-only details  
**And** I do not see any Edit or Delete actions for ledger entries

### Scenario 2: Filter ledger by product and location
**Given** ledger entries exist for product `SKU-123` in locations `RCV-01` and `BIN-C4`  
**When** I filter by product `SKU-123` and location `BIN-C4`  
**Then** the results include entries where `fromLocationId = BIN-C4` or `toLocationId = BIN-C4`  
**And** entries for other products are not shown

### Scenario 3: Ledger detail renders nullable fields safely
**Given** a ledger entry exists with `fromLocationId = null` and `toLocationId = RCV-01`  
**When** I open the ledger entry detail screen  
**Then** the UI displays `fromLocationId` as “—”  
**And** the UI displays `toLocationId` as `RCV-01`  
**And** no client error occurs

### Scenario 4: Adjustment create requires permission and reason code
**Given** I am logged in **without** permission `INVENTORY_ADJUST_CREATE`  
**When** I view the Ledger screen  
**Then** I do not see an enabled “New Adjustment” action  
**And** if I attempt to access the adjustment create route directly  
**Then** I see an access denied message

**Given** I am logged in **with** permission `INVENTORY_ADJUST_CREATE`  
**When** I start a new adjustment request  
**And** I leave `reasonCode` blank  
**Then** the UI prevents submission and shows “Reason is required for adjustments.”

### Scenario 5: Approve/post adjustment requires permission
**Given** an adjustment request exists in `PENDING` status  
**And** I am logged in without `INVENTORY_ADJUST_APPROVE`  
**When** I view that adjustment request  
**Then** I cannot approve/post it  
**And** if I attempt to post via UI action, the backend response is handled and an access denied message is shown

### Scenario 6: Backend validation error is surfaced clearly
**Given** I am creating an adjustment request  
**When** the backend responds with error code `INSUFFICIENT_STOCK`  
**Then** the UI shows “Insufficient stock for this movement.”  
**And** my entered form values remain available for correction

---

## 13. Audit & Observability

### User-visible audit data
- Ledger list and detail must display:
  - `actorId`
  - `timestamp` (display in user locale, sourced from UTC)
  - `sourceTransactionId` (when present)
  - `reasonCode` (when present, especially for ADJUST)

### Status history
- For adjustment requests (if implemented): display status and timestamps returned by backend (`requestedAt`, `approvedAt/postedAt`), and `requestedBy/approvedBy` if provided.

### Traceability expectations
- UI should display `ledgerEntryId` on detail screen for referencing during investigations.
- If backend provides correlation ID in error responses/headers, surface it in an expandable “Technical details” area.

---

## 14. Non-Functional UI Requirements
- **Performance:** Ledger list must support pagination; do not load unbounded results.
- **Accessibility:** All form controls must have labels; table must be navigable via keyboard; error messages must be programmatically associated with fields.
- **Responsiveness:** Filters and results usable on tablet widths; table may switch to a stacked row layout if needed.
- **i18n/timezone:** Timestamps stored in UTC; display in user’s timezone; movementType and reasonCode labels should be translatable if label resources exist. Currency not applicable.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-results state with “Clear filters” action; safe because it does not change domain behavior. (Impacted: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION: Default page size of 25 with server-side pagination; safe because it only affects presentation/performance. (Impacted: UX Summary, Service Contracts, Non-Functional)
- SD-ERR-MAP-GENERIC: Map unknown backend errors to a generic failure message while preserving trace/correlation id when available; safe because it does not alter business rules. (Impacted: Business Rules, Error Flows, Observability)

---

## 16. Open Questions
1. **Backend contract for ledger queries:** What are the exact Moqui service names or REST endpoints, including query parameter names and pagination response shape (items/total, page tokens, etc.)?
2. **Adjustment UI scope:** Should the frontend implement full Adjustment Request screens in this repo, or only provide links to an existing adjustments area? If full, what is the adjustment request entity name/fields and state machine (`PENDING/APPROVED/POSTED`)?
3. **Location filtering semantics:** For the ledger list filter by location, should it match `fromLocationId OR toLocationId` (recommended for usability), or only a specific side depending on movement type?
4. **Workexec integration key:** For “Workexec can query movement history for a workorder line”, what identifier is available in ledger entries—`sourceTransactionId` only, or a dedicated `workorderId/workorderLineId` field?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/101


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/101
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Ledger: Record Stock Movements in Inventory Ledger

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want all stock movements recorded in a ledger so that on-hand is auditable and explainable.

## Details
- Movement types: Receive, PutAway, Pick, Issue/Consume, Return, Transfer, Adjust.
- Capture productId, qty, UOM, from/to storage, actor, timestamp, reason.

## Acceptance Criteria
- Every movement creates a ledger entry.
- Ledger is append-only.
- Can reconstruct on-hand by replay.
- Adjustments require reason and permission.

## Integrations
- Workexec can query movement history for a workorder line.

## Data / Entities
- InventoryLedgerEntry, MovementType, ReasonCode, AuditLog

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