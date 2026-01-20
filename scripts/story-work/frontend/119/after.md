## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Master: Manage Product Lifecycle State (Active/Inactive/Discontinued) with Effective Dates + Replacement Suggestions

## Primary Persona
Product Admin (or Inventory Admin)

## Business Value
Ensure discontinued/unsellable products are clearly flagged and enforced in downstream selling workflows (quotes/work orders) while maintaining an auditable history of lifecycle changes and providing replacement suggestions to prevent quoting unsourceable items.

---

# 2. Story Intent

## As a / I want / So that
**As a** Product Admin,  
**I want** to set a product’s lifecycle state (Active/Inactive/Discontinued) with an effective date (date-only or timestamp) and optionally manage replacement products,  
**So that** selling/quoting workflows can avoid unsourceable items, and lifecycle changes are auditable and enforceable.

## In-Scope
- A “Lifecycle” section on a Product Master / Product Detail screen:
  - View current lifecycle state and its effective timestamp.
  - Change lifecycle state with an effective date/time input.
  - If setting to `DISCONTINUED`, require override permission and require/collect an override reason when applicable (per backend rules).
  - Display lifecycle change metadata (last changed by/at, effective at).
- Manage replacement suggestions for discontinued products:
  - Add one or more replacement products with priority order, optional notes, and effectiveAt.
  - List existing replacements sorted by priority.
- Frontend enforcement behaviors (UI-level gating) consistent with backend:
  - Disable/guard invalid transitions (e.g., reactivating discontinued).
  - Surface backend validation/permission errors clearly.
- Audit visibility:
  - Show lifecycle-related audit history entries (at minimum: state changes; ideally also replacement add/edit/remove if supported by backend).

## Out-of-Scope
- Defining/assigning security roles/permissions (security domain).
- Implementing downstream quote/work order enforcement screens (separate stories), except for displaying lifecycle state where already shown by shared product search components.
- Product master creation/edit outside lifecycle fields.
- Any inventory valuation/costing behavior.

---

# 3. Actors & Stakeholders
- **Primary Actor:** Product Admin / Inventory Admin (edits lifecycle state, sets replacements)
- **Secondary Actor:** Service Advisor (consumes lifecycle info during quoting/WO; may be blocked elsewhere)
- **System Stakeholders:**
  - Authorization/Permission subsystem (enforces `product:lifecycle:update` and `product:lifecycle:override_discontinued`)
  - Work Execution / Pricing & Availability consumers (must receive lifecycle status via existing responses)
- **Compliance Stakeholder:** Audit/Reporting consumer (needs traceability)

---

# 4. Preconditions & Dependencies
- Product exists and is viewable in Product Master UI.
- Backend story #55 is implemented and deployed (or equivalent services exist) providing:
  - Read product lifecycle fields (`lifecycleState`, `lifecycleStateEffectiveAt`, `lastStateChangedBy`, `lastStateChangedAt`, `lifecycleOverrideReason` where applicable).
  - Update lifecycle state with permission enforcement and validation (irreversibility, effective date validation).
  - CRUD for `ProductReplacement` records (at least create/list; update/delete if supported).
  - Audit/event publication for lifecycle change (frontend displays audit log if an endpoint exists).
- Authentication is in place; frontend can detect permission-based errors (403) and validation errors (400).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Product Master list → Product Detail → **Lifecycle** tab/section.
- Direct route to Product Detail (deep link) including Lifecycle section anchor.

## Screens to create/modify (Moqui)
- **Modify**: `apps/durion/screen/product/ProductDetail.xml` (name illustrative; must align to repo conventions)
  - Add a `subscreens` entry or a `section` for `Lifecycle`.
- **Create** (if not existing): `apps/durion/screen/product/ProductLifecycle.xml` as a subscreen:
  - Display current state panel (read-only fields + effective timestamp).
  - State change form.
  - Replacement list + add form (and optional edit/delete if supported).
  - Audit log view section (if supported).

## Navigation context
- Breadcrumb: Product Master → Product {productId or displayName} → Lifecycle
- Preserve current product context (`productId` parameter in URL).

## User workflows

### Happy path A — Set product to INACTIVE with effective date
1. User opens Lifecycle section.
2. User selects `INACTIVE`.
3. User enters effective date/time (date-only allowed).
4. User submits.
5. UI shows success and updated lifecycle metadata.

### Happy path B — Set product to DISCONTINUED (requires override permission)
1. User selects `DISCONTINUED`.
2. UI prompts for override reason if required by backend rule.
3. User submits.
4. UI refreshes state; replacements section becomes available/encouraged.

### Happy path C — Add replacement product(s)
1. For a discontinued product, user clicks “Add Replacement”.
2. User searches/selects replacement product (product picker).
3. User sets priorityOrder (integer) and optional notes/effectiveAt.
4. UI saves and refreshes list.

### Alternate path — Attempt to reactivate DISCONTINUED
- UI should prevent selection/submit for invalid transition, and if attempted via stale UI, backend error is shown verbatim with mapped friendly message.

---

# 6. Functional Behavior

## Triggers
- Screen load with `productId`
- User submits lifecycle change form
- User submits add replacement form
- (Optional) User edits/deletes replacement (only if backend supports)

## UI actions
- **On load**: fetch product lifecycle details + replacement list (+ audit entries if available).
- **On state selection**:
  - If state == `DISCONTINUED`, show override permission hint and show “Override Reason” input (visibility may depend on current state and permission).
  - If current state is `DISCONTINUED`, disable selecting `ACTIVE` or `INACTIVE` (hard disable) and show explanatory text.
- **On submit**:
  - Validate required inputs locally (presence, type) before calling backend.
  - Call backend update service; on success, refresh the product lifecycle details and audit section.

## State changes (domain state)
- `ACTIVE` ⇄ `INACTIVE` allowed (per backend).
- Transition to `DISCONTINUED` allowed only with override permission.
- `DISCONTINUED` → `ACTIVE/INACTIVE` is not allowed (irreversible).

## Service interactions (Moqui)
- Use Moqui transitions for form submits.
- Use Moqui services (or REST via `service-call` if project pattern uses REST) to:
  - Load product lifecycle data
  - Update lifecycle state
  - List/create replacement records
  - Load audit log entries (if endpoint exists)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Effective date/time:
  - Must be present (unless backend defaults “immediately”; see Open Questions).
  - Must not be in the past (backend rule); UI should pre-check using client time **but treat backend as source of truth**.
- When setting to `DISCONTINUED`:
  - User must have permission `product:lifecycle:override_discontinued` or the submit will fail with 403.
  - Override reason:
    - If backend requires reason for discontinued and/or for overrides, UI must require non-empty text and show inline validation.
- Replacement records:
  - `replacementProductId` required.
  - `priorityOrder` required; must be integer >= 1 (backend constraints unknown; enforce minimally as integer).
  - `effectiveAt` optional unless backend requires.

## Enable/disable rules
- If current state is `DISCONTINUED`:
  - Disable state selection for `ACTIVE` and `INACTIVE`.
  - Lifecycle change submit disabled unless backend allows adding only replacements without changing state.
- DISCONTINUED selection enabled only if user has override permission **or** allow selection but expect 403; prefer disabling with a “permission required” message if permission info is available.

## Visibility rules
- Replacement management section visible when product is `DISCONTINUED` (or always visible but disabled with guidance; prefer conditional visibility).
- Override reason input visible when selecting `DISCONTINUED` (and/or when an override is being performed per backend response).

## Error messaging expectations
- If backend returns the known message:
  - `"Discontinued products cannot be reactivated. Specify a replacement product instead."`
  UI must surface it prominently near the lifecycle form.
- For 403: show “You do not have permission to perform this action: <permissionName if provided>”.
- For 400: show field-level errors when possible; otherwise show top-of-form error summary.

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- `Product` (or product master view)
- `ProductReplacement` (originalProductId → replacementProductId)
- `AuditLog` / lifecycle event stream (if exposed)

## Fields

### Product lifecycle fields (read-only except via form submit)
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| productId | UUID/string | yes | n/a | route param |
| lifecycleState | enum(`ACTIVE`,`INACTIVE`,`DISCONTINUED`) | yes | n/a | displayed + edited via form |
| lifecycleStateEffectiveAt | Timestamp (UTC) | yes* | backend | *may be null for legacy products; UI must handle |
| lastStateChangedBy | UserRef | no | null | display if present |
| lastStateChangedAt | Timestamp (UTC) | no | null | display if present |
| lifecycleOverrideReason | string | no/conditional | null | display last override reason if present |

### Replacement fields (create/list)
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| replacementId | UUID/string | no (create) | n/a | assigned by backend |
| originalProductId | UUID/string | yes | from context | discontinued product |
| replacementProductId | UUID/string | yes | n/a | selected via picker |
| priorityOrder | integer | yes | 1 | UI may suggest next available |
| notes | string | no | null | textarea |
| effectiveAt | Timestamp (UTC) or date | no | null | UI supports date-only if backend supports |

## Read-only vs editable by state/role
- Users without `product:lifecycle:update`: all lifecycle and replacement forms read-only (view-only).
- Users with `product:lifecycle:update` but without `product:lifecycle:override_discontinued`:
  - Can set ACTIVE/INACTIVE (if allowed) but cannot set DISCONTINUED.
  - Can add replacements? (unclear—Open Question; backend story suggests `product:lifecycle:update` sufficient for replacements.)

## Derived/calculated fields
- Display “Sellable: Yes/No” derived from lifecycleState:
  - `ACTIVE` => Yes
  - `INACTIVE`/`DISCONTINUED` => No

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact service names/paths are not provided in inputs; this story defines required contracts and flags as blocking where unknown.

## Load/view calls
- **Get Product Detail (including lifecycle fields)**
  - Input: `productId`
  - Output: product fields including lifecycle state + effectiveAt + last changed metadata
- **List replacements**
  - Input: `originalProductId`
  - Output: array of `ProductReplacement` sorted by `priorityOrder` ascending

## Create/update calls
- **Update lifecycle state**
  - Input:
    - `productId`
    - `lifecycleState`
    - `effectiveAt` (timestamp or date-only)
    - `overrideReason` (conditional)
  - Output: updated product lifecycle fields
- **Create ProductReplacement**
  - Input:
    - `originalProductId`
    - `replacementProductId`
    - `priorityOrder`
    - `notes?`
    - `effectiveAt?`
  - Output: created replacement

## Submit/transition calls (Moqui screens)
- Lifecycle form submit transition:
  - On success: redirect back to same screen with success message and refreshed data.
  - On error: stay on page, show errors.

## Error handling expectations
- 400: validation errors; map to inline fields when possible
- 403: permission denied; show action blocked message; do not retry automatically
- 409 (if used): concurrency conflict; prompt to reload (see Error Flows)

---

# 10. State Model & Transitions

## Allowed states
- `ACTIVE`
- `INACTIVE`
- `DISCONTINUED`

## Role-/permission-based transitions
- `ACTIVE` → `INACTIVE`: requires `product:lifecycle:update`
- `INACTIVE` → `ACTIVE`: requires `product:lifecycle:update`
- `ACTIVE|INACTIVE` → `DISCONTINUED`: requires `product:lifecycle:override_discontinued` (and possibly `product:lifecycle:update` as baseline; backend to confirm)

## UI behavior per state
- **ACTIVE**: lifecycle form allows selecting INACTIVE or DISCONTINUED (if permitted)
- **INACTIVE**: allow selecting ACTIVE or DISCONTINUED (if permitted)
- **DISCONTINUED**:
  - lifecycle state selector locked (read-only)
  - replacement management enabled (if permitted)
  - show banner: “Discontinued products cannot be reactivated.”

---

# 11. Alternate / Error Flows

## Validation failures
- Missing effective date/time (if required) → inline error
- Effective date in past → inline error (and backend error shown if backend rejects)
- Missing override reason when required → inline error

## Concurrency conflicts
- If backend indicates product lifecycle changed since load (e.g., optimistic lock):
  - Show message: “This product’s lifecycle was updated by another user. Reload to continue.”
  - Provide reload action.

## Unauthorized access
- User lacking permission attempts submit:
  - Show 403 error and keep form values (do not clear).
  - If permission names provided by backend, display them.

## Empty states
- No replacements exist:
  - Show “No replacement products configured.”
  - If discontinued, prompt to add one.

---

# 12. Acceptance Criteria

## Scenario 1: View lifecycle state on Product Detail
**Given** I am an authenticated user with access to view products  
**And** a product exists with lifecycleState `ACTIVE`  
**When** I open the Product Lifecycle section for that product  
**Then** I see the current lifecycle state and the effective timestamp (or “Not set” if null)  
**And** I see last changed by/at if provided by the backend  

## Scenario 2: Set a product to INACTIVE with a future effective date
**Given** a product is currently `ACTIVE`  
**And** I have permission `product:lifecycle:update`  
**When** I select lifecycle state `INACTIVE`  
**And** I enter an effective date of `2023-11-01` (date-only)  
**And** I submit the lifecycle change  
**Then** the frontend sends an update request including the entered effective date  
**And** the screen refreshes showing lifecycleState `INACTIVE` and a stored UTC effective timestamp returned by backend  
**And** a success confirmation is shown  

## Scenario 3: Discontinue a product requires override permission
**Given** a product is currently `ACTIVE`  
**And** I do not have permission `product:lifecycle:override_discontinued`  
**When** I attempt to set lifecycle state to `DISCONTINUED` and submit  
**Then** the frontend prevents submission OR the backend responds `403`  
**And** the UI shows an error indicating the action requires `product:lifecycle:override_discontinued`  

## Scenario 4: Discontinue a product with override reason
**Given** a product is currently `ACTIVE`  
**And** I have permission `product:lifecycle:override_discontinued`  
**When** I select lifecycle state `DISCONTINUED`  
**And** I enter override reason `End of Life`  
**And** I submit  
**Then** the product shows lifecycleState `DISCONTINUED` after refresh  
**And** the UI shows a discontinued banner indicating it cannot be reactivated  
**And** the override reason is displayed if returned by backend  

## Scenario 5: Prevent reactivation of discontinued product
**Given** a product is `DISCONTINUED`  
**When** I attempt to change it to `ACTIVE` or `INACTIVE`  
**Then** the UI prevents the action  
**And** if a backend error is returned, the UI displays: “Discontinued products cannot be reactivated. Specify a replacement product instead.”  

## Scenario 6: Add a replacement product suggestion
**Given** a product is `DISCONTINUED`  
**And** I have permission to manage lifecycle (`product:lifecycle:update`)  
**When** I add replacement product `PROD-E` with priority `1` and optional notes  
**Then** the frontend creates a replacement record via backend  
**And** the replacement appears in the replacement list sorted by priority  

---

# 13. Audit & Observability

## User-visible audit data
- Display an “Lifecycle History” list showing at minimum:
  - timestamp
  - actor (user)
  - oldState → newState
  - effectiveAt
  - reason (if present)
- If audit endpoint not available, show “Audit history unavailable” and log a console warning only in dev mode.

## Status history
- Show current lifecycle + effectiveAt and last changed metadata (from product fields).
- Prefer to derive history from audit log rather than guessing.

## Traceability expectations
- All lifecycle update submissions include:
  - productId
  - proposed new state
  - effectiveAt input value (as entered)
- Frontend should include correlation/request id if project has standard header injection (per repo conventions).

---

# 14. Non-Functional UI Requirements
- **Performance:** Product lifecycle section should load within 1s on typical broadband once product detail is loaded; replacement list paginates if large (client-side minimal).
- **Accessibility:** All form controls labeled; errors announced via ARIA live region; keyboard navigable.
- **Responsiveness:** Works on tablet widths used on shop floor.
- **i18n/timezone/currency:**
  - Date/time inputs must clarify timezone handling; effectiveAt stored in UTC.
  - Display effectiveAt in user’s local timezone with UTC tooltip (or dual display) if conventions support.

---

# 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**
  - Assumed: Standard empty-state messaging for no replacement products.
  - Why safe: Pure UI ergonomics; does not change domain logic.
  - Impacted sections: UX Summary, Alternate/Empty states.
- **SD-UX-ERROR-MAP-HTTP**
  - Assumed: Map 400/403/409 to inline+banner errors using common patterns.
  - Why safe: Error mapping is presentation-only and reversible.
  - Impacted sections: Service Contracts, Alternate/Error Flows.

---

# 16. Open Questions

1. **Domain label conflict (Product vs Inventory):** Frontend story synopsis says “Product / Parts Management” but backend labels `domain:inventory`. Confirm that Product Lifecycle State is owned by **Inventory domain** in this workspace and that this issue should be labeled `domain:inventory` (current proposal), not `domain:product` (which is not a provided canonical domain label set here).
2. **Backend service contract details:** What are the exact Moqui service names or REST endpoints for:
   - load product lifecycle fields
   - update lifecycle state
   - list/create/update/delete `ProductReplacement`
   - load audit history
3. **Effective date defaulting:** If user does not enter an effective date/time, does backend default to “immediately”? If yes, what timestamp is applied (request time in UTC)?
4. **Past effective date rule:** Backend states “effective date cannot be in the past.” Is “past” evaluated relative to server time at submit? Are near-past tolerances allowed?
5. **Replacement permissions:** Is `product:lifecycle:update` sufficient to add replacements for a discontinued product, or is a separate permission required?
6. **Replacement editing/deleting:** Are update/delete operations supported for `ProductReplacement`? If yes, what validations apply (e.g., uniqueness of priorityOrder, effectiveAt constraints)?
7. **Timezone governing rule:** Backend mentions “product’s governing timezone if specified.” Where does the frontend retrieve this governing timezone (product field? organization/site setting?), and what is the fallback?
8. **UI integration point for “blocked from new quotes”:** Which existing frontend flows must enforce “cannot add discontinued item unless override permission”? (This story currently limits to Product Master UI; enforcement in quoting may require additional stories.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/119

Labels: frontend, story-implementation, user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want lifecycle states with effective dates so that quoting avoids unsourceable items.

## Details
- States: Active, Inactive, Discontinued, Replaced.
- Optional replacement product link.

## Acceptance Criteria
- Discontinued items blocked from new quotes unless override permission.
- Replacement suggested.
- Audited.

## Integrations
- Workexec receives lifecycle status in pricing/availability responses.

## Data / Entities
- ProductLifecycle, ReplacementLink, AuditLog

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