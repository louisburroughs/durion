## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

# 1. Story Header

**Title:** [FRONTEND] [STORY] Billing: Define Account Billing Rules (Commercial Accounts)

**Primary Persona:** Billing Clerk

**Business Value:** Ensures invoicing behavior (PO enforcement, terms, delivery, grouping) is consistent per commercial account, auditable, and permission-controlled so downstream Work Execution and Billing automation behave predictably.

---

# 2. Story Intent

### As a / I want / So that
- **As a** Billing Clerk  
- **I want** to create and update billing rules for a commercial account  
- **So that** invoicing defaults and enforcement rules are correct, consistent, and traceable across the POS workflow.

### In-scope
- Frontend screens/flows to **view** and **edit/upsert** Billing Rules for a given `accountId`.
- Fetching current Billing Rules for display.
- Fetching enumerated options for selectable fields (e.g., payment terms, delivery method, grouping strategy) if exposed by backend.
- Permission-gated UI actions and user-facing error handling for 403/404/409/422-like failures.
- Display of audit metadata (at minimum: last updated timestamp + updated by), and a link/section placeholder for change history if backend supports it.

### Out-of-scope
- Implementing backend persistence, validation, event emission, or provisioning (handled by Billing backend).
- Work Execution estimate approval enforcement UI (owned by Work Execution domain).
- Invoice creation/issuance/payment processing flows.
- Defining the system-wide default rules template (admin/backoffice configuration).

---

# 3. Actors & Stakeholders
- **Billing Clerk (Primary):** Configures rules per commercial account.
- **Billing Manager / Auditor:** Needs confidence changes are permissioned and auditable.
- **Work Execution users (Indirect):** Rely on rules snapshot/enforcement downstream; not editing here.
- **System (Moqui UI + Billing APIs):** UI calls Billing services/APIs and renders results.

---

# 4. Preconditions & Dependencies
- User is authenticated in the Moqui-based frontend.
- A commercial account exists and the UI can obtain a stable `accountId` (from CRM/account lookup or route param).
- Backend provides Billing Rules APIs (reference backend story #100):
  - `GET /billing-rules/{accountId}`
  - `PUT /billing-rules/{accountId}` (idempotent upsert)
- Backend provides (or embeds) option sets for:
  - `paymentTermsId` valid values
  - `invoiceDeliveryMethod` enum values
  - `invoiceGroupingStrategy` enum values  
  If not available as endpoints, UI must rely on values returned from backend or a shared configuration (needs clarification).

---

# 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Commercial Account** context (e.g., Account Details screen): action “Billing Rules”.
- Direct deep link route: `/accounts/<accountId>/billing-rules`.

### Screens to create/modify
- **New/Modified Screen:** `apps/pos/account/BillingRules.xml` (or equivalent per repo conventions)
  - View current rules (read-only summary)
  - Edit form (upsert)
  - Audit metadata display (last updated)
- **Modify existing Account screen** to add navigation link to Billing Rules (where account screens exist).

### Navigation context
- Breadcrumb/toolbar should reflect: Account → Billing Rules.
- The screen must retain `accountId` in parameters across transitions.

### User workflows
**Happy path**
1. Billing Clerk opens Billing Rules for an account.
2. UI loads current rules (or shows “not configured” state if 404).
3. Clerk edits fields (PO required toggle, payment terms, delivery method, grouping strategy).
4. Clerk saves; UI performs upsert.
5. UI reloads and shows updated values and updated metadata.

**Alternate paths**
- Rules not found (404): show empty state + allow “Create rules” (same form with defaults from backend if returned).
- Permission denied (403): show read-only view if allowed; otherwise show access denied.
- Concurrent modification (409): prompt user to reload and re-apply changes.
- Validation errors (400/422): highlight fields and show actionable messages.

---

# 6. Functional Behavior

### Triggers
- Screen load with `accountId`.
- User clicks Save/Update.

### UI actions
- **On load**
  - Call service to load account summary (optional, if already in context) and Billing Rules.
  - Populate form model with returned values or initialize with backend-provided defaults if available.
- **On change**
  - Track dirty state; enable Save only when changes exist and form is valid.
- **On Save**
  - Submit upsert request with full Billing Rules payload (not patch) to ensure deterministic state.
  - Disable Save while request is in flight; prevent double-submit.
- **On success**
  - Show success confirmation.
  - Refresh displayed data from GET to reflect canonical persisted state (including version/updatedAt).
- **On failure**
  - Map backend error to user-facing message and field errors where applicable.

### State changes (frontend)
- `loading` → `loaded`
- `editMode` toggles (optional; may be always editable if permitted)
- `saving` → `saved` or `error`
- Maintain `serverVersion`/`etag` (if provided) for optimistic concurrency.

### Service interactions (Moqui)
- Use Moqui screen actions to invoke REST calls (or Moqui services that proxy to backend).
- Use transitions for Save action with redirect back to view state on success.

---

# 7. Business Rules (Translated to UI Behavior)

### Validation
UI must enforce basic client-side validation (non-authoritative) aligned to backend invariants:
- `paymentTermsId` is **required**.
- `invoiceDeliveryMethod` is **required**.
- `invoiceGroupingStrategy` is **required**.
- `isPoRequired` is **required** boolean (default false when creating new).

If backend defines PO format/uniqueness policies, they are enforced server-side; UI must display server error text.

### Enable/disable rules
- Save button enabled only when:
  - user has modify permission, AND
  - form is dirty, AND
  - required fields are present.
- If user lacks modify permission:
  - fields rendered read-only/disabled
  - Save hidden/disabled

### Visibility rules
- Show “Not configured” empty state when GET returns 404.
- Show audit metadata section when `updatedAt`/`updatedBy` present.
- Show “Change history” section only if backend provides an audit history endpoint (otherwise show placeholder text “Not available”).

### Error messaging expectations
- 403: “You do not have permission to manage billing rules for this account.”
- 404 on load: “No billing rules exist for this account yet.”
- 409: “These billing rules were updated by another user. Reload to continue.”
- 400/422: “Fix the highlighted fields and try again.”
- 503/network: “Billing service unavailable. Try again later.”

---

# 8. Data Requirements

### Entities involved (frontend perspective)
- **BillingRules** (Billing-owned; UI reads/writes via APIs)
- **PaymentTerm** (enumeration/list; read-only options)
- **Commercial Account** (CRM-owned; UI uses `accountId` and display name)

### Fields
BillingRules form model (types are UI-level):
- `accountId` (string/uuid, required, read-only; from route/context)
- `isPoRequired` (boolean, required, default false)
- `paymentTermsId` (string, required)
- `invoiceDeliveryMethod` (enum string, required; e.g., `EMAIL|PORTAL|MAIL`)
- `invoiceGroupingStrategy` (enum string, required; e.g., `PER_WORK_ORDER|PER_VEHICLE|SINGLE_INVOICE`)
- `version` (number/int, optional, read-only; used for optimistic concurrency if present)
- `updatedAt` (datetime, read-only)
- `updatedBy` (string/userRef, read-only)

### Read-only vs editable by state/role
- Editable only for users with permission `billingRules:manage` (or backend equivalent).
- Always read-only: `accountId`, `version`, `updatedAt`, `updatedBy`.

### Derived/calculated fields
- Display label text derived from IDs/enums (e.g., PaymentTerm name from list).
- “Last updated” string derived from `updatedAt`.

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/endpoints must match the integration layer in this repo. Below is the required contract behavior; implement via REST calls or Moqui façade services.

### Load/view calls
1. **Get Billing Rules**
   - Method: `GET /billing-rules/{accountId}`
   - Success: `200` returns BillingRules JSON
   - Not found: `404` (render empty state)
   - Forbidden: `403` (render access denied or read-only)
2. **Get option lists** (one of the following must exist; otherwise Open Question)
   - `GET /payment-terms` → list `{id, name, ...}`
   - enums may be returned as part of BillingRules payload (e.g., `allowedDeliveryMethods`, `allowedGroupingStrategies`)

### Create/update calls
1. **Upsert Billing Rules**
   - Method: `PUT /billing-rules/{accountId}`
   - Request body includes:
     - `isPoRequired`
     - `paymentTermsId`
     - `invoiceDeliveryMethod`
     - `invoiceGroupingStrategy`
     - include `version` or `If-Match` header if backend supports optimistic concurrency
   - Responses:
     - `200 OK` (updated) or `201 Created` (created)
     - `400/422` validation errors with field-level details if provided
     - `403` forbidden
     - `409` conflict if stale version/etag

### Error handling expectations
- Map HTTP status to UX messages (see section 7).
- If backend returns structured errors:
  - Prefer field mapping for inline validation (e.g., `{fieldErrors:{paymentTermsId:"..."}}`)
- Never log PII; sanitize any account contact info if present.

---

# 10. State Model & Transitions

### Allowed states (UI-level)
- `UNCONFIGURED` (GET 404)
- `CONFIGURED` (GET 200)

### Role-based transitions
- `UNCONFIGURED` → `CONFIGURED` via Save (PUT) if authorized.
- `CONFIGURED` → `CONFIGURED` via Save (PUT) if authorized.

### UI behavior per state
- **UNCONFIGURED**
  - Show empty state
  - If authorized: show editable form with required fields; defaults prefilled if available
  - If not authorized: show message and no form submission
- **CONFIGURED**
  - Show current values and audit metadata
  - If authorized: allow edits + Save
  - If not authorized: read-only display

---

# 11. Alternate / Error Flows

### Validation failures (server)
- Backend returns 400/422 with message(s)
- UI:
  - keeps user inputs
  - highlights fields if field mapping available
  - displays summary banner

### Concurrency conflicts
- Backend returns 409
- UI:
  - shows conflict message
  - provides action “Reload” that re-fetches GET and resets form
  - does not auto-merge

### Unauthorized access
- Backend returns 403 on GET:
  - UI shows access denied page/state
- Backend returns 403 on PUT:
  - UI shows “not permitted” banner and keeps form read-only thereafter

### Empty states
- GET 404:
  - show “No rules configured”
  - provide Create flow if authorized

### Downstream unavailable
- 503 / network timeout:
  - show retry affordance
  - do not lose unsaved edits on PUT failure

---

# 12. Acceptance Criteria

### Scenario 1: View existing billing rules
**Given** I am an authenticated Billing Clerk with permission to view billing rules  
**And** billing rules exist for `accountId`  
**When** I open the Billing Rules screen for that account  
**Then** the system loads and displays `isPoRequired`, `paymentTermsId`, `invoiceDeliveryMethod`, and `invoiceGroupingStrategy`  
**And** the screen displays `updatedAt` and `updatedBy` when provided by the API.

### Scenario 2: Create billing rules when none exist
**Given** I am authorized to manage billing rules  
**And** no billing rules exist for `accountId` (API returns 404)  
**When** I enter valid values for required fields and click Save  
**Then** the UI sends `PUT /billing-rules/{accountId}` with the entered values  
**And** I see a success confirmation  
**And** a subsequent reload shows the saved rules as the canonical values.

### Scenario 3: Update billing rules
**Given** I am authorized to manage billing rules  
**And** billing rules exist for `accountId`  
**When** I change `paymentTermsId` and click Save  
**Then** the UI sends `PUT /billing-rules/{accountId}` with the updated payload  
**And** the UI refreshes from `GET /billing-rules/{accountId}`  
**And** the displayed value reflects the update.

### Scenario 4: Prevent save when required fields missing (client-side)
**Given** I am authorized to manage billing rules  
**When** `paymentTermsId` is empty  
**Then** the Save action is disabled (or blocked with inline validation)  
**And** no PUT request is sent.

### Scenario 5: Handle server-side validation error
**Given** I am authorized to manage billing rules  
**When** I click Save and the backend returns a validation error (400/422)  
**Then** the UI shows an error message  
**And** keeps my entered values  
**And** highlights the fields indicated by the server response (if provided).

### Scenario 6: Handle concurrent modification
**Given** I am authorized to manage billing rules  
**And** I have loaded billing rules for `accountId`  
**When** I click Save and the backend responds with `409 Conflict`  
**Then** I see a conflict message prompting reload  
**And** I can reload to fetch the latest rules before re-applying changes.

### Scenario 7: Permission enforcement
**Given** I do not have permission to manage billing rules  
**When** I open the Billing Rules screen  
**Then** I cannot edit or save billing rules  
**And** if I attempt a save (e.g., via direct action), the UI handles `403 Forbidden` by showing an authorization error.

---

# 13. Audit & Observability

### User-visible audit data
- Display:
  - `updatedAt`
  - `updatedBy`
- If an audit-history endpoint exists, display a read-only list of changes with:
  - timestamp, actor, fields changed (before/after where provided)

### Status history
- Not a state machine beyond configured/unconfigured for UI; show last updated metadata.

### Traceability expectations
- Include `accountId` in:
  - UI logs (sanitized)
  - correlation/trace headers if the frontend integration layer supports it

---

# 14. Non-Functional UI Requirements
- **Performance:** Initial load should complete with a single round-trip for rules + (optional) one round-trip for option lists; avoid chatty calls.
- **Accessibility:** Form controls must have labels, keyboard navigation, and error text associated to fields.
- **Responsiveness:** Must work on tablet width used at POS; avoid layouts that require horizontal scrolling.
- **i18n/timezone/currency:** Display `updatedAt` in the user’s locale/timezone per app defaults (no currency handling required here).

---

# 15. Applied Safe Defaults
- **SD-UI-EMPTY-STATE-01**: Provide a standard empty state when `GET /billing-rules/{accountId}` returns 404; qualifies as safe because it affects only UX presentation, not domain policy. (Impacted sections: UX Summary, Alternate / Error Flows)
- **SD-ERR-MAP-HTTP-01**: Map common HTTP statuses (403/404/409/422/503) to consistent banners and inline messages; qualifies as safe because it does not change backend behavior and only standardizes error display. (Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows, Acceptance Criteria)

---

# 16. Open Questions
1. **Option list sources:** What are the exact endpoints/contracts to load **Payment Terms** and allowed enum values for **Delivery Method** and **Grouping Strategy** in the frontend (or are these embedded in `GET /billing-rules/{accountId}` responses)?
2. **Permissions contract:** What permissions/roles should the frontend check (exact permission strings) to determine view vs manage access for billing rules?
3. **Optimistic concurrency:** Does the backend require `version` in payload or `If-Match`/ETag for updates, and what is the expected UI behavior when version is omitted?
4. **Audit history UI:** Is there an endpoint to retrieve Billing Rules change history (e.g., `GET /billing-rules/{accountId}/audit`)? If yes, what fields and pagination does it support?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Billing: Define Account Billing Rules — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/164


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Billing: Define Account Billing Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/164
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Billing: Define Account Billing Rules

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Billing Clerk**, I want **to set billing rules for a commercial account** so that **invoicing is correct and consistent**.

## Details
- Rules: PO required, payment terms, invoice delivery (email/portal), invoice grouping (per vehicle/per workorder).
- Rule changes audited and permissioned.

## Acceptance Criteria
- Create/update rules.
- Rules returned on account lookup.
- Rule changes audited and access-controlled.

## Integration Points (Workorder Execution)
- Estimate approval enforces PO rules.
- Invoicing uses delivery + grouping defaults.

## Data / Entities
- BillingRule
- PaymentTerm
- DeliveryPreference

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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