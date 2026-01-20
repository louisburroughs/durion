STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Rules: Maintain Substitute Relationships and Equivalency Types (Admin UI)

### Primary Persona
Product Admin (with inventory/catalog administration responsibilities)

### Business Value
Enable the business to define and manage part substitute relationships (equivalent/alternative/upgrade/downgrade) so execution workflows can suggest viable alternatives when an item is unavailable, while enforcing “auto-suggest vs approval-required” policy and maintaining a complete audit trail.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Product Admin  
- **I want** to create, view, update, search, and deactivate substitute links between parts with an equivalency type and auto-suggest flag  
- **So that** work order execution can reliably query substitute candidates and respect policy (automatic suggestion vs requires approval), with traceable history for compliance.

### In-scope
- Moqui-based Admin UI screens to:
  - List/search substitute links
  - Create a substitute link
  - View substitute link details (including audit/metadata)
  - Update a substitute link with optimistic locking (`version`)
  - Deactivate (soft-delete) a substitute link
- “Query substitutes for a part” view (admin-facing) that uses the backend query endpoint and displays returned ranking/availability metadata.
- Frontend validation aligned with backend rules (required fields, enums, unique constraint handling, version conflicts).
- Display of audit-related metadata that is exposed by backend responses (created/updated fields, correlation id if provided).

### Out-of-scope
- Work order substitution application flow (`/api/v1/workorders/{workOrderId}/suggest-substitutes`) UI (technician/advisor runtime UX).
- Inventory availability ranking logic (server-side) beyond displaying returned metadata.
- Creating/editing parts/catalog items (owned by product/catalog domain).
- Defining approval policies beyond selecting the `isAutoSuggest` flag (policy configuration is not provided here).
- Building/defining backend endpoints or schemas (assumed to exist per backend reference).

---

## 3. Actors & Stakeholders
- **Primary user:** Product Admin
- **Secondary stakeholders:**
  - Service Advisor (consumer of accurate substitute data via workexec flows)
  - Technician/Mechanic (indirect consumer through suggested substitutes)
  - Inventory Manager (ensures substitutes make operational sense)
  - Compliance/Audit reviewer (needs traceability)

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend and authorized to manage substitutes.
- Backend endpoints exist and are reachable (see “Service Contracts”):
  - `POST /api/v1/substitutes`
  - `PUT /api/v1/substitutes/{id}`
  - `GET /api/v1/substitutes/{id}`
  - `DELETE /api/v1/substitutes/{id}` (soft delete)
  - `GET /api/v1/parts/{partId}/substitutes?...`
- A way to select/identify “Part” records in the UI exists:
  - either an existing part search/lookup endpoint/screen, or a minimal “enter partId” flow. (Currently unclear → Open Questions)
- Backend supports optimistic locking via `version` on update and returns 409 on conflict.
- Backend handles unique constraint `(part_id, substitute_part_id)` and returns 409 with enough detail to show a meaningful message (unclear → Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation: **Rules → Substitutes** (exact menu placement TBD by existing app nav conventions; implement as Moqui screen entry with menu item).

### Screens to create/modify
Create new Moqui screens under a consistent path, e.g.:
- `apps/pos/screen/rules/Substitutes.xml` (list/search)
- `apps/pos/screen/rules/SubstituteDetail.xml` (view/edit)
- `apps/pos/screen/rules/SubstituteCreate.xml` (create)

(Exact location/path may differ based on repo conventions → if the frontend uses a different screen root, follow existing structure.)

### Navigation context
- From list → click a row to open detail.
- From list → “Create Substitute Link” opens create screen.
- From detail → “Edit” toggles to editable form or navigates to edit mode.
- From detail/list → “Deactivate” triggers confirmation then calls DELETE.

### User workflows
**Happy path: Create**
1. Product Admin opens Substitutes list screen.
2. Clicks “Create”.
3. Selects Part and Substitute Part, selects Substitute Type, sets Auto Suggest, optional fields (priority/effective dates).
4. Saves → system creates link, shows success, navigates to detail.

**Happy path: Update**
1. Admin opens link detail.
2. Clicks Edit, modifies fields, saves with current `version`.
3. On success, detail refreshes and shows updated metadata.

**Happy path: Query substitutes for a part (admin check)**
1. Admin opens a “Substitutes for Part” panel in detail or separate tab.
2. Enters/selects `partId`, optional `locationId`, `availableOnly`, `limit`.
3. UI calls backend query endpoint and renders ranked results and availability metadata.

**Alternate path: Duplicate link**
- If admin attempts to create a duplicate `(partId, substitutePartId)`, UI shows conflict message and links to existing resource when possible.

---

## 6. Functional Behavior

### Triggers
- Navigating to list/detail/create screens.
- Clicking Save/Update/Deactivate.
- Executing “Query substitutes” action.

### UI actions
- **List/Search**
  - Filter by `partId`, `substitutePartId`, `substituteType`, `isActive` (default active), and optionally “effective now” (see Open Questions).
- **Create**
  - Collect required fields and post to backend.
- **Update**
  - Send updated fields + required optimistic `version`.
- **Deactivate**
  - Confirm modal then call DELETE; UI updates list/detail to show inactive.
- **Query substitutes**
  - Call query endpoint and render returned candidates in order.

### State changes (UI-level)
- Local UI state: loading, success toast/banner, inline field errors, conflict banner for 409, not found screen for 404.
- No frontend-owned domain state machines are introduced here; this story is CRUD/admin around substitute links.

### Service interactions
- Use Moqui `service-call` / REST calls per project convention for API integration.
- Ensure correlation/request id is propagated if the frontend already supports it (safe default only if it exists; otherwise leave).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
**Create/Update form validations (client-side, mirrored from backend):**
- `partId`: required
- `substitutePartId`: required and **must not equal** `partId`
- `substituteType`: required; must be one of:
  - `EQUIVALENT`, `APPROVED_ALTERNATIVE`, `UPGRADE`, `DOWNGRADE`
- `isAutoSuggest`: boolean; default false if not set
- `priority`: integer; default 100 if omitted (only if backend also defaults; otherwise leave blank → Open Question)
- `effectiveFrom`, `effectiveTo`: optional; if both provided, `effectiveFrom <= effectiveTo`
- `version`: required for update

### Enable/disable rules
- “Save” disabled until required fields are present and basic validations pass.
- “Deactivate” disabled if already inactive.
- “Update” disabled if no changes detected (optional ergonomics).

### Visibility rules
- Audit metadata fields (createdBy/createdAt/updatedBy/updatedAt/version/isActive) displayed read-only on detail screen if present in response.
- Availability metadata in “Query substitutes” only shown when returned by the query endpoint.

### Error messaging expectations
- 400: show field-level errors if provided; otherwise a banner “Validation failed”.
- 403: show “You do not have permission to manage substitutes.”
- 404: show “Substitute link not found” and link back to list.
- 409 (version conflict): show “This substitute link was updated by someone else. Refresh and re-apply changes.”
- 409 (duplicate): show “This substitute relationship already exists.” If response includes existing `id`, show “View existing” link.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `SubstituteLink`
- `Part` (reference/lookup only; actual entity may be `part` or `product` depending on backend)
- Optional: `SubstituteAudit` or audit metadata (only if API exposes it)

### Fields
**SubstituteLink (as used by UI forms):**
- `id` (UUID/string) — read-only (assigned by backend)
- `partId` (string/uuid/long; exact type TBD) — required, editable on create; typically read-only after create (Open Question)
- `substitutePartId` (same type as partId) — required, editable on create; typically read-only after create (Open Question)
- `substituteType` (enum) — required, editable
- `isAutoSuggest` (boolean) — editable
- `priority` (int) — optional/editable
- `effectiveFrom` (datetime) — optional/editable
- `effectiveTo` (datetime) — optional/editable
- `isActive` (boolean) — read-only in UI; changed via Deactivate action
- `version` (int) — required for update; read-only display but must be sent
- `createdBy`, `createdAt`, `updatedBy`, `updatedAt` — read-only display if provided

**Query substitute candidates response (read-only table):**
- `substitutePartId`
- `substituteType`
- `priority`
- `availableQuantityByLocation[]` (structure TBD)
- `leadTimeDays` (optional)
- `estimatedTransferTimeHours` (optional)
- `priceDeltaMinor` (optional)

### Read-only vs editable by state/role
- Role enforcement is backend-owned; frontend should hide/disable create/edit/deactivate controls if user lacks permission **only if** frontend has an existing permission/role capability. Otherwise rely on backend 403.
- `id`, audit fields, `version` are read-only.
- `partId` and `substitutePartId` editability on update is unclear (Open Question).

### Derived/calculated fields
- None calculated on frontend beyond displaying derived metadata returned by server.

---

## 9. Service Contracts (Frontend Perspective)

> Note: API paths are taken from backend reference; if actual Moqui integration uses different base path/versioning, map accordingly.

### Load/view calls
- **List screen (optional if backend supports listing):**
  - Not specified in backend reference. If no list endpoint exists, the list screen must be backed by search-by-part flow (Open Question).
- **Get detail:**
  - `GET /api/v1/substitutes/{id}`
  - Success: 200 with SubstituteLink JSON
  - Errors: 404/403/500

### Create/update calls
- **Create:**
  - `POST /api/v1/substitutes`
  - Request JSON:
    - `partId`, `substitutePartId`, `substituteType`, `isAutoSuggest`, `priority?`, `effectiveFrom?`, `effectiveTo?`
  - Headers:
    - `Idempotency-Key` optional but recommended when supported (Open Question on frontend behavior)
  - Success: 201 Created with created resource JSON (must include `id` and `version`)
  - Errors:
    - 400 validation
    - 409 duplicate
    - 403 unauthorized

- **Update:**
  - `PUT /api/v1/substitutes/{id}`
  - Request includes changed fields and **must include `version`**
  - Success: 200 OK with updated resource JSON (new `version`)
  - Errors:
    - 400 validation
    - 409 version conflict
    - 403/404

### Submit/transition calls
- **Deactivate (soft delete):**
  - `DELETE /api/v1/substitutes/{id}`
  - Success: 200 or 204 (backend-dependent; treat both as success)
  - Errors: 403/404/409 (if backend blocks due to constraints; not specified)

### Query substitutes (for a given part)
- `GET /api/v1/parts/{partId}/substitutes?availableOnly=true&locationId=...&limit=10`
- Success: 200 array ordered by server ranking
- Errors: 400 (bad params), 404 (part not found), 403, 500

### Error handling expectations
- Map HTTP statuses to user-friendly messages (see Business Rules).
- Preserve and display backend-provided `correlationId` if included in error payload (Open Question: error schema).

---

## 10. State Model & Transitions

### Allowed states (SubstituteLink)
- `isActive = true|false` (active vs deactivated)
- Time effectiveness (optional): effective now vs not yet vs expired based on `effectiveFrom/effectiveTo` (display-only unless explicitly filtered)

### Role-based transitions
- Product Admin can:
  - Create link
  - Update link
  - Deactivate link
- Others:
  - May view/query depending on permission; not defined here (Open Question)

### UI behavior per state
- If inactive:
  - Show “Inactive” status
  - Disable edit and deactivate
- If effective dates indicate not currently effective:
  - Show status badge “Not effective yet” or “Expired” (display-only; safe UX default)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields: inline errors; prevent submit.
- effective date range invalid: inline errors; prevent submit.
- partId == substitutePartId: inline error; prevent submit.

### Concurrency conflicts
- Update with stale `version` returns 409:
  - UI shows conflict banner
  - Provide “Reload” action that refetches detail
  - If user had edits, warn they will be lost unless copied manually (do not auto-merge).

### Unauthorized access
- 403 on any action:
  - Show a permission error banner
  - Hide mutation controls after first 403 for that session/screen (optional)

### Empty states
- List view: “No substitute links found” with CTA to create.
- Query substitutes: “No substitutes configured for this part” or “No available substitutes” depending on `availableOnly` toggle.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create a substitute link (happy path)
Given I am authenticated as a Product Admin  
And I am on the “Create Substitute Link” screen  
When I enter a valid partId and substitutePartId  
And I select substituteType = "EQUIVALENT"  
And I set isAutoSuggest = false  
And I click Save  
Then the system creates the substitute link via POST `/api/v1/substitutes`  
And I see a success message  
And I am navigated to the substitute link detail screen showing the new link id and version.

### Scenario 2: Prevent creating a self-substitute
Given I am on the “Create Substitute Link” screen  
When I set substitutePartId equal to partId  
Then the Save action is disabled or blocked  
And I see an inline error stating the substitute part must be different from the base part.

### Scenario 3: Handle duplicate substitute link conflict
Given a substitute link already exists for partId P and substitutePartId S  
And I am on the “Create Substitute Link” screen  
When I attempt to create the same relationship again  
Then the backend responds with HTTP 409  
And the UI displays a conflict message indicating the relationship already exists  
And if the response includes the existing link id, the UI provides a link to open it.

### Scenario 4: Update a substitute link with optimistic locking
Given I am on a substitute link detail screen  
And the link has version V  
When I change priority and click Save  
Then the UI sends PUT `/api/v1/substitutes/{id}` including version V  
And on success I see the updated values  
And the displayed version increments (or updates) based on the response.

### Scenario 5: Update conflict due to stale version
Given I am editing a substitute link with version V  
And another user has updated the link so the current version is now V+1  
When I click Save with version V  
Then the backend responds with HTTP 409  
And the UI shows a concurrency conflict message  
And provides an action to reload the latest version.

### Scenario 6: Deactivate (soft delete) a substitute link
Given I am on a substitute link detail screen for an active link  
When I click Deactivate and confirm  
Then the UI calls DELETE `/api/v1/substitutes/{id}`  
And on success the UI shows the link as inactive  
And the Deactivate action is no longer available.

### Scenario 7: Query substitutes for a part with availability-only filter
Given I am on the “Substitutes for Part” query panel  
When I enter partId P and set availableOnly=true and locationId L  
And I submit the query  
Then the UI calls GET `/api/v1/parts/{partId}/substitutes` with those parameters  
And renders the returned candidates in the order received  
And displays availability metadata per candidate when provided.

### Scenario 8: Unauthorized user cannot modify substitutes
Given I am authenticated without substitute-management permission  
When I attempt to create, update, or deactivate a substitute link  
Then the backend responds with HTTP 403  
And the UI displays a permission error and does not apply the change.

---

## 13. Audit & Observability

### User-visible audit data
- On detail screen, display (if available from API):
  - createdAt/createdBy, updatedAt/updatedBy, version, isActive
- If backend exposes audit trail entries:
  - Provide an “Audit” section listing operations (CREATE/UPDATE/DELETE), timestamp, actor, and (optional) correlation id.
  - If not exposed, do not invent (Open Question).

### Status history
- For SubstituteLink itself, only active/inactive is tracked in UI unless audit endpoint exists.

### Traceability expectations
- Ensure frontend includes correlation/request id header if project convention exists.
- Ensure errors surface correlation id to assist support.

---

## 14. Non-Functional UI Requirements

- **Performance:** list/detail views should load within 2s on typical network; query substitutes should show loading state and allow cancellation/navigation away.
- **Accessibility:** all form controls labeled; keyboard navigable; validation errors announced via ARIA where applicable (Quasar supports).
- **Responsiveness:** screens usable on tablet width; tables horizontally scroll if needed.
- **i18n/timezone:** display datetimes in user’s locale/timezone if the app has a standard; otherwise display ISO with timezone indicator (Open Question about project standard).
- **Security:** do not expose internal-only fields; never log sensitive data in browser console; rely on backend auth.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging and CTA on list/query results; safe because it does not change domain logic; impacts UX Summary, Alternate/Empty states.
- SD-UX-LOADING-ERROR: Standard loading spinners and error banners per screen; safe because it only affects presentation; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: If list endpoint exists, paginate with a conservative default page size (e.g., 25) using standard table pagination; safe because it’s UI ergonomics only; impacts UX Summary, Functional Behavior.

---

## 16. Open Questions

1. **Domain boundary/ownership confirmation:** This story is labeled `domain:workexec`, but it is “Product/Parts Management” flavored. Is workexec definitively the owning frontend area for the admin UI, or should this be `domain:inventory` or a product domain label if one exists in this repo? (Blocking label may change.)
2. **Part lookup UX contract:** What existing endpoint/screen should the admin UI use to search/select parts (by SKU/name) rather than manually entering IDs? Please provide the API route(s) and response shape.
3. **List/search endpoint for SubstituteLink:** Do we have `GET /api/v1/substitutes` (with filters/pagination), or must the list screen be built around “query by partId” only?
4. **Error payload schema:** When backend returns 400/409, what is the standard error response format (field errors, message, correlationId, existingResourceId on duplicates)?
5. **Idempotency-Key usage:** Should the frontend generate and send `Idempotency-Key` for create calls by default (e.g., UUID per submit) to protect against double-submits?
6. **Editability of key fields:** On update, are `partId` and `substitutePartId` immutable (recommended) or editable? If editable, how should uniqueness conflicts be handled?
7. **Defaults alignment:** Does backend default `priority=100` and `isAutoSuggest=false` when omitted, or must UI always send explicit values?
8. **Audit trail visibility:** Is there an API to fetch `SubstituteAudit` entries for display? If not, should UI only show basic created/updated metadata?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Rules: Maintain Substitute Relationships and Equivalency Types — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/109

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Rules: Maintain Substitute Relationships and Equivalency Types
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/109
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Rules: Maintain Substitute Relationships and Equivalency Types

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want substitute relationships so that workexec can suggest alternatives when items are unavailable.

## Details
- Types: Equivalent, ApprovedAlternative, Upgrade, Downgrade.
- Control whether suggestion is automatic or requires approval.

## Acceptance Criteria
- Create/update substitute link.
- Query substitutes.
- Policies enforced.
- Audited.

## Integrations
- Workexec uses substitute list; inventory availability ranks candidates.

## Data / Entities
- SubstituteLink, SubstituteType, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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