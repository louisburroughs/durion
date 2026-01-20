## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** pricing-strict

---

# 1. Story Header

## Title
[FRONTEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails

## Primary Persona
Store Manager

## Business Value
Enable Store Managers to respond to local market pricing while enforcing centrally-defined pricing guardrails and approval workflow, ensuring pricing is compliant, auditable, and safe.

---

# 2. Story Intent

## As a / I want / So that
**As a** Store Manager  
**I want** to create or update a location-specific store price override for a product  
**So that** I can compete locally while the system enforces guardrails and routes approvals when required.

## In-scope
- Moqui/Vue/Quasar frontend screens to:
  - Search/select a product and location context
  - View base price and any existing override
  - Propose an override price
  - Submit override for activation (auto-approved) or for approval (pending)
  - View current override status and approval details
- Frontend handling of guardrail/approval outcomes returned by backend services:
  - ACTIVE vs PENDING_APPROVAL vs validation failure
- Display user-visible audit/status history for overrides (as exposed by backend)
- Authorization-aware UI behavior (hide/disable actions when user lacks permission)

## Out-of-scope
- Defining guardrail policy values and formulas (owned by backend/pricing domain logic)
- Approver-side approval/rejection UI (unless already part of frontend scope elsewhere)
- Workexec consumption/reporting implementation (downstream systems)
- Bulk override upload/import

---

# 3. Actors & Stakeholders
- **Store Manager (Primary)**: Creates/updates override for their assigned location(s).
- **Pricing System (Backend, System Actor)**: Computes base price, enforces guardrails, determines approval requirement, persists override.
- **Approver (Secondary)**: Approves/rejects pending overrides (handled outside this story unless an existing screen exists).
- **Regional Pricing Manager / Pricing Desk (Stakeholders)**: Operational owners of approvals and guardrail enforcement.
- **Reporting/Analytics (Stakeholder)**: Consumes override state and audit trail (out of scope for UI beyond visibility).

---

# 4. Preconditions & Dependencies
1. User is authenticated in the Moqui app.
2. User has permission to manage overrides:
   - Required permission: `pricing:override:manage`
3. User is scoped to one or more locations; override actions must be limited to authorized locations.
4. Backend endpoints/services exist (or will exist) consistent with backend story #52:
   - Retrieve base price + cost (if available) + current override for (locationId, productId)
   - Submit override and receive resulting status (ACTIVE or PENDING_APPROVAL) or validation errors
   - Retrieve override details including approval request (if pending) and audit/history
5. GuardrailPolicy exists for the location/region (backend enforces; frontend displays error if missing).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Pricing / Store Price Overrides navigation item (new menu entry) **OR**
- Product detail (if existing) → “Set Store Price Override” action (must include location context)

(If project conventions require a single entry point, prefer Pricing module menu entry; otherwise support both where feasible.)

## Screens to create/modify
1. **New Screen:** `apps/pos/screen/pricing/StorePriceOverrideList.xml` (name indicative)
   - Location selector (limited to authorized locations)
   - Product search/select
   - List existing overrides for selected location (optional but recommended for usability)
2. **New Screen:** `apps/pos/screen/pricing/StorePriceOverrideDetail.xml`
   - View base price, current effective price, existing override state
   - Form to submit a new override price or update existing (where allowed)
   - Read-only approval/audit panel (status history)
3. **Optional Dialog Component (Vue/Quasar):**
   - “Propose Override Price” modal with confirmation summary (base price, proposed price, computed discount/margin if backend returns)

## Navigation context
- `Pricing > Store Price Overrides` (list)
- Selecting a row navigates to detail:
  - `.../pricing/store-price-override/detail?locationId=...&productId=...`
- From detail, back navigates to list preserving filters (location/product search)

## User workflows
### Happy path: auto-approved
1. Store Manager selects location + product.
2. System loads base price + current override info.
3. Store Manager enters `overridePrice` and submits.
4. UI shows success and status `ACTIVE`; effective price updates to override.

### Alternate path: requires approval
1. Submit override that is within hard guardrails but exceeds auto-approval threshold.
2. UI shows status `PENDING_APPROVAL` and displays assigned approver info (if provided).

### Alternate path: guardrail violation
1. Submit override that violates hard guardrails.
2. UI shows inline validation error message(s); no record created/updated.

---

# 6. Functional Behavior

## Triggers
- Enter list screen: initialize location from user’s default location (if available) and load overrides (optional).
- Select location/product: load pricing context (base price, cost if available, existing override).
- Submit override: call backend to validate and persist; update UI based on response.

## UI actions
- **Select Location**
  - Dropdown/search constrained to authorized locationIds
- **Select Product**
  - Product lookup (by SKU/name); selection yields `productId`
- **View Pricing Context**
  - Display:
    - base price (read-only)
    - cost (read-only, if returned and permitted)
    - current effective price (read-only)
    - any active/pending override (read-only summary)
- **Enter Override Price**
  - Monetary input, currency display (currency comes from backend context if provided)
- **Submit**
  - Disabled until required inputs present and valid format
  - On submit:
    - show confirmation including base vs override and any backend-provided computed values
    - call submit service
- **Post-submit display**
  - If ACTIVE: show “Override active”
  - If PENDING_APPROVAL: show “Submitted for approval” plus approval request summary
  - If validation error: show message(s) mapped to field/global

## State changes (frontend-visible)
- `LocationPriceOverride.status` transitions as returned by backend:
  - Created/updated to `ACTIVE` or `PENDING_APPROVAL`
- If an override is `REJECTED` or `INACTIVE`, detail screen shows read-only record with status and history; edit/submit behavior depends on backend allowance (see State Model section).

## Service interactions
- Load context service on selection/navigation
- Create/update override submit service
- Load audit/history service (or included in context payload)

---

# 7. Business Rules (Translated to UI Behavior)

> Pricing formulas/guardrail evaluation are owned by backend. Frontend must not re-implement calculation logic; it may display computed values returned by backend.

## Validation
- `overridePrice`:
  - Required for submit
  - Must be a valid monetary amount (> 0)
  - Precision/rounding: accept up to 4 decimal places (DECIMAL(19,4)) in input; display formatting per currency
- Location/product must be selected and valid UUIDs.

## Enable/disable rules
- Submit button disabled when:
  - missing locationId/productId/overridePrice
  - overridePrice is not parseable or <= 0
- Edit fields disabled when:
  - user lacks `pricing:override:manage`
  - override status is `REJECTED` (immutable) (frontend enforces read-only; backend must also enforce)
- If backend responds `403`, show unauthorized message and disable further edits.

## Visibility rules
- Show approval panel only if response includes `ApprovalRequest` or status `PENDING_APPROVAL/REJECTED`.
- Show cost only if backend includes it (do not assume it is always available).

## Error messaging expectations
- Guardrail violation errors must be displayed as actionable messages, e.g.:
  - “Discount exceeds maximum allowed”
  - “Margin below minimum allowed”
- Missing policy / missing base data errors displayed as blocking banners:
  - “Unable to evaluate override: guardrail policy missing for this location”
  - “Unable to compute base price for this product at this location”

---

# 8. Data Requirements

## Entities involved (frontend-facing)
- `LocationPriceOverride`
- `GuardrailPolicy` (read-only, optional to display summary if backend provides)
- `ApprovalRequest` (read-only summary)
- `AuditLog` / status history (read-only)

## Fields (type, required, defaults)

### LocationPriceOverride (as displayed/edited)
- `overrideId` (UUID, read-only)
- `locationId` (UUID, required, read-only after selection)
- `productId` (UUID, required, read-only after selection)
- `overridePrice` (Decimal(19,4), required for submit, editable when allowed)
- `status` (Enum: `ACTIVE`, `PENDING_APPROVAL`, `REJECTED`, `INACTIVE`; read-only)
- `createdByUserId` (UUID, read-only)
- `createdAt` (datetime, read-only)
- `approvedByUserId` (UUID nullable, read-only)
- `resolvedAt` (datetime nullable, read-only)
- Rejection fields (read-only):
  - `rejectedBy` (UUID)
  - `rejectedAt` (datetime)
  - `rejectionReasonCode` (string)
  - `rejectionNotes` (string)

### GuardrailPolicy (if returned for display)
- `policyId` (UUID, read-only)
- `scope` (string/enum, read-only)
- `scopeId` (UUID/string, read-only)
- `min_margin_percent` (decimal, read-only)
- `max_discount_percent` (decimal, read-only)
- `auto_approval_threshold_percent` (decimal, read-only)

### ApprovalRequest (if pending)
- `requestId` (UUID, read-only)
- `overrideId` (UUID, read-only)
- `status` (`PENDING`, `APPROVED`, `REJECTED`; read-only)
- `assignedApproverId` (UUID, read-only)
- `assignmentStrategy` (string, read-only)

## Read-only vs editable by state/role
- Store Manager with `pricing:override:manage`:
  - Can submit new override for (locationId, productId)
  - Can update overridePrice only if backend allows updates for current status (assume only when `ACTIVE` or no override exists; if `PENDING_APPROVAL`, treat as read-only unless backend explicitly supports “replace pending”)
- Any user without permission: all fields read-only.

## Derived/calculated fields (display only if backend returns)
- `basePrice` (money)
- `effectivePrice` (money)
- `discountPercent` (decimal)
- `marginPercent` (decimal; only when cost available)

---

# 9. Service Contracts (Frontend Perspective)

> Endpoint names are placeholders; implement using Moqui services/transitions consistent with project conventions. Frontend must integrate via Moqui screens/services, not direct DB.

## Load/view calls
1. **Get pricing context**
   - Input: `locationId`, `productId`
   - Output:
     - `basePrice`, `effectivePrice`, optional `cost`
     - existing `LocationPriceOverride` (if any)
     - optional `GuardrailPolicy` summary
     - optional `ApprovalRequest` (if pending)
     - optional `currencyUomId`
   - Error handling:
     - 404 product not found → show “Product not found”
     - 403 unauthorized location → show unauthorized banner
     - 409/422 missing base data/policy → show blocking banner (no submit)

2. **Get override audit/history**
   - Input: `overrideId`
   - Output: list of status changes/audit entries
   - If backend includes audit within context, this call is optional.

## Create/update calls
1. **Submit override**
   - Input:
     - `locationId` (UUID)
     - `productId` (UUID)
     - `overridePrice` (Decimal)
   - Output:
     - `overrideId`
     - `status` (`ACTIVE` or `PENDING_APPROVAL`)
     - if pending: `ApprovalRequest` summary
     - updated `effectivePrice`
   - Error handling:
     - Guardrail violations: field/global errors with codes/messages; do not persist
     - 403 unauthorized
     - 404 invalid product
     - 409 conflict if concurrent update detected (see Error Flows)

## Submit/transition calls
- If Moqui uses transitions:
  - `transition` from detail form submit to `submitStorePriceOverride` service
  - On success, redirect to same detail with updated data; on error, remain on form with messages.

## Error handling expectations
- Map backend validation errors to:
  - field-level error on `overridePrice` when applicable
  - global banner for policy/base-data/system errors
- Preserve user input on validation failure.

---

# 10. State Model & Transitions

## Allowed states (as displayed)
- `ACTIVE`
- `PENDING_APPROVAL`
- `REJECTED`
- `INACTIVE`

## Role-based transitions (frontend)
- Store Manager (`pricing:override:manage`):
  - Can create/submit override → results in `ACTIVE` or `PENDING_APPROVAL` (backend decision)
  - Cannot directly set `REJECTED`/`INACTIVE` via UI in this story (unless backend exposes deactivation; not specified)
- Approver (`pricing:override:approve`):
  - Approval/rejection transitions are out of scope for this frontend story unless existing screens already handle them.

## UI behavior per state
- **ACTIVE**
  - Show active override and effective price = overridePrice
  - Allow proposing a new overridePrice (submit creates new record or updates; depends on backend contract—see Open Questions if not supported)
- **PENDING_APPROVAL**
  - Show pending status and assignedApproverId
  - Override editing disabled by default; provide informational text “Pending approval”
- **REJECTED**
  - Show rejection reason code/notes and who/when
  - Entire record read-only
- **INACTIVE**
  - Show inactive status; submit new override allowed (treated like create)

---

# 11. Alternate / Error Flows

## Validation failures
- Override price violates min margin/max discount:
  - Display backend-provided message(s)
  - Keep user on screen with entered price preserved
  - No status change shown

## Concurrency conflicts
- If backend returns conflict (e.g., override changed since load):
  - Show banner “This override was updated by another user. Reloading latest values.”
  - Re-load context and show latest status/values
  - Do not auto-resubmit

## Unauthorized access
- If user tries to access detail for a location they are not authorized for:
  - Show 403 page or banner
  - Do not show sensitive data (cost, guardrails)
  - Provide navigation back to list

## Empty states
- No override exists for selected product/location:
  - Show base price and “No active override”
  - Allow submit

## Backend dependency failure
- If pricing context service unavailable:
  - Show error state with retry
  - Disable submit (fail closed)

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Load pricing context for product/location
**Given** I am a Store Manager with `pricing:override:manage` for location `L1`  
**When** I navigate to the Store Price Override detail for product `P1` at location `L1`  
**Then** the UI loads and displays the base price and effective price for `P1` at `L1`  
**And** if an override exists, its status and override price are displayed  

## Scenario 2: Submit override within auto-approval thresholds (ACTIVE)
**Given** base price and guardrails allow auto-approval for my proposed override  
**When** I enter a valid overridePrice and submit  
**Then** the UI shows a success message  
**And** the override status is `ACTIVE`  
**And** the effective price displayed equals the submitted overridePrice  

## Scenario 3: Submit override requiring approval (PENDING_APPROVAL)
**Given** my proposed override is within hard guardrails but requires manual approval  
**When** I submit the overridePrice  
**Then** the UI shows status `PENDING_APPROVAL`  
**And** the UI displays assigned approver information when provided by the backend  
**And** the effective price displayed remains the base price (or the backend-provided effective price that excludes the pending override)  

## Scenario 4: Submit override that violates hard guardrails (validation error)
**Given** my proposed override violates a hard guardrail  
**When** I submit the overridePrice  
**Then** the UI displays a validation error message explaining the violated rule  
**And** the override is not created/updated (no new overrideId returned)  
**And** my entered overridePrice remains in the input field  

## Scenario 5: Unauthorized location access blocked
**Given** I do not have access to location `L2`  
**When** I attempt to view or submit an override for location `L2`  
**Then** the UI shows an unauthorized error state  
**And** no override submission is performed  

## Scenario 6: Concurrency conflict handled
**Given** I loaded an override detail page  
**And** another user updates the override before I submit  
**When** I submit my overridePrice  
**Then** the UI shows a conflict message  
**And** reloads the latest override data  
**And** does not apply my submission without my re-confirmation  

---

# 13. Audit & Observability

## User-visible audit data
- Detail screen shows:
  - createdBy/createdAt
  - current status
  - approvedBy/resolvedAt when applicable
  - rejection details when applicable
  - status history/audit entries if backend provides them

## Status history
- Display as a chronological list with:
  - timestamp
  - actor (userId or display name if available)
  - transition (e.g., SUBMITTED → ACTIVE)
  - notes (e.g., rejection reason)

## Traceability expectations
- Frontend must include correlation/request ID in logs if provided by Moqui response headers.
- On submit/load failures, log:
  - locationId, productId (no prices in logs if considered sensitive; do not log cost)
  - overrideId when available
  - error code/status

---

# 14. Non-Functional UI Requirements

## Performance
- Pricing context load should render initial skeleton/loading state within 200ms and complete when backend responds.
- Avoid repeated calls: debounce product search; cache last loaded context per (locationId, productId) in-session if consistent with app patterns.

## Accessibility
- All form inputs labeled.
- Error messages announced to screen readers (aria-live region for banners).
- Keyboard navigable (tab order, enter to submit where appropriate).

## Responsiveness
- Works on tablet resolutions used in-store.
- Forms must be usable without horizontal scrolling.

## i18n/timezone/currency
- Display currency symbol/format based on backend-provided currency (if available) or app default.
- Display timestamps in user’s locale/timezone per app standard.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging when no override exists; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-UX-DEBOUNCE-SEARCH: Debounce product search input to reduce backend load; qualifies as safe UI ergonomics/performance. Impacted sections: Non-Functional UI Requirements, Functional Behavior.
- SD-ERR-STD-MAPPING: Map HTTP 403/404/409/422 into standard banner/field errors without changing domain logic; qualifies as safe error-handling mapping. Impacted sections: Service Contracts, Alternate / Error Flows.

---

# 16. Open Questions
- none

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/116  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] StorePrice: Set Location Store Price Override within Guardrails

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Store Manager**, I want to set store price overrides so that I can compete locally within policy.

## Details
- Override layered over base price.
- Guardrails: min margin %, max discount %, approval thresholds.

## Acceptance Criteria
- Override created/updated.
- Guardrails enforced with approvals.
- Audited.

## Integrations
- Workexec receives store price for that location; reporting tracks override usage.

## Data / Entities
- LocationPriceOverride, GuardrailPolicy, ApprovalRecord, AuditLog

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