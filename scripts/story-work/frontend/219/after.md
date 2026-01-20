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

**Title:** [FRONTEND] [STORY] Work Execution: Enforce Role-Based Visibility for Financial Fields in Work Order Execution UI (Moqui)

**Primary Persona:** Mechanic / Technician (restricted view) and Service Advisor / Back Office (full view)

**Business Value:** Prevents unauthorized visibility of sensitive pricing/cost/margin data during execution while preserving full financial truth for invoicing/reporting and ensuring consistent enforcement across execution screens.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Mechanic/Technician  
  **I want** the Work Order execution UI to hide pricing/cost/margin fields I’m not authorized to see  
  **So that** I can focus on completing work without being exposed to sensitive financial information.

- **As a** Service Advisor / Back Office user  
  **I want** the same screens to show full financial fields when I’m authorized  
  **So that** I can quote, explain, and prepare billing accurately.

### In Scope
- Frontend (Moqui screens + Vue/Quasar components integration points) enforcement of **role/permission-based visibility** for sensitive financial fields in Work Order execution views.
- Consistent behavior across:
  - Work order header/summary
  - Work order line items (parts/services)
  - Any execution-related subviews that display line item financials
- Secure fallback behavior when policy/permissions cannot be determined: **hide sensitive fields** and surface an admin-facing warning cue (non-sensitive).
- Optional audit logging **when sensitive fields are shown** (if supported by backend contract).

### Out of Scope
- Creating or editing visibility policies (owned by Security/Policy backend).
- Backend enforcement logic (assumed server-side filtering exists or will exist).
- Business calculations (pricing, margins, taxes).
- Defining new workflow states or changing work order state machine.

---

## 3. Actors & Stakeholders
- **Mechanic/Technician**: Uses execution UI; must not see restricted financials.
- **Service Advisor / Back Office**: Needs full visibility for pricing/cost.
- **System Administrator**: Needs misconfiguration signal (not policy authoring here).
- **Security/Policy Service (external dependency)**: System of record for permission scopes/visibility policies.
- **Workexec API**: Provides work order data (possibly already filtered by permissions).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend and has an identity context available to Moqui (session/userAccount).
- Work Order exists and is viewable by the user (`workexec:workorder:view` minimum).

### Dependencies (Blocking if absent/unknown)
- Backend endpoint(s) used by the execution UI to load work orders and items must either:
  1) **Return permission scopes** (or an equivalent “capabilities/visibility” object) to the frontend, OR  
  2) **Server-side omit sensitive fields** reliably, allowing frontend to treat “missing field” as “not visible”.
- A definitive list of “sensitive financial fields” to hide on the frontend (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order list → select Work Order → Execution view
- Direct route to work order execution screen by ID

### Screens to create/modify (Moqui)
- **Modify** existing Work Order execution screen(s) (exact screen path TBD):
  - A root execution screen (e.g., `apps/pos/workexec/WorkOrderExecution.xml`)
  - Subscreens/components for:
    - Work order summary
    - Parts list
    - Services/labor list
    - Line item detail panel/modal (if exists)

### Navigation context
- Execution UI must render correctly for different roles without separate routes.
- No role-name checks in UI; use permission scopes/capabilities.

### User workflows
**Happy path (Mechanic)**
1. Mechanic opens Work Order execution UI.
2. Screen loads work order + line items.
3. UI shows operational fields (descriptions, quantities, statuses) but hides pricing/cost/margin fields.

**Happy path (Service Advisor)**
1. Advisor opens same work order.
2. UI shows all operational fields plus pricing/cost/margin fields.

**Alternate path (Policy missing/role misconfigured)**
1. User opens work order.
2. UI cannot determine visibility capabilities.
3. UI defaults to hiding sensitive fields and shows a non-sensitive “Visibility policy unavailable; using safe defaults” indicator (and logs client event).

---

## 6. Functional Behavior

### Triggers
- Work order execution screen is loaded for a given `workOrderId`.
- User opens a line item detail view (if applicable).
- User switches tabs/subscreens within the execution UI.

### UI actions
- Load work order data.
- Determine visibility for sensitive fields using one of:
  - Permission scopes provided by backend/session context, or
  - Presence/absence of fields in API response, or
  - Dedicated “capabilities” endpoint (preferred, but not defined here).

### State changes (frontend)
- Local UI state: `canViewPricing`, `canViewCost`, `canViewMargin` (derived booleans)
- UI rendering toggles:
  - Hide columns
  - Hide summary rows/totals that would reveal pricing/cost/margin
  - Hide inline editors for those fields (even if editable elsewhere)

### Service interactions
- Load work order + items via workexec API (see Service Contracts).
- Optionally post an “audit access” event when sensitive fields are displayed (backend must support).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- If user lacks minimum permission to view work order (`workexec:workorder:view`):
  - UI must treat as unauthorized and show an access denied state (no data rendered).
- If sensitive-field visibility is unknown (capabilities missing, policy lookup fails):
  - UI must default to **hide sensitive fields**.

### Enable/disable rules
- Any UI control that reveals sensitive info (e.g., “Show cost breakdown”) must be disabled/hidden unless corresponding permission is present.
- No “toggle to reveal” for mechanics unless permission scope exists.

### Visibility rules
Sensitive fields (initial list from inputs; may be expanded by backend reality):
- `unitPrice`
- `extendedPrice`
- `cost`
- `margin`

Rule mapping:
- Show pricing fields only if user has `workexec:workorder:view-pricing` (or equivalent capability).
- Show cost fields only if user has `workexec:workorder:view-cost`.
- Show margin fields only if user has `workexec:workorder:view-margin` (if exists) OR if margin is grouped with cost (needs clarification).

### Error messaging expectations
- Unauthorized: “Insufficient permissions to view this work order.”
- Policy missing: “Visibility policy unavailable; showing limited information.” (must not reveal policy internals)

---

## 8. Data Requirements

### Entities involved (frontend-consumed)
- Work Order (WorkOrder)
- Work Order Item (WorkOrderService, WorkOrderPart or unified WorkorderItem DTO)
- User permissions/scopes (from auth context or backend payload)
- Visibility policy is **not** managed in frontend; only consumed indirectly.

### Fields (type, required, defaults)
**Work Order (read-only for this story)**
- `id` (string/number, required)
- `status` (enum string, required)
- `customerName` (string, optional)
- `vehicle` fields (optional)
- Non-sensitive operational fields as currently present.

**Line items**
- `description` (string, required)
- `quantity` (number, required for parts)
- `status` (enum string, required)
- Sensitive:
  - `unitPrice` (decimal, optional/hidden)
  - `extendedPrice` (decimal, optional/hidden)
  - `cost` (decimal, optional/hidden)
  - `margin` (decimal, optional/hidden)

**Capabilities / scopes**
- `permissionScopes: string[]` OR `capabilities: { canViewPricing: boolean, ... }`
- Default if missing: all `false`.

### Read-only vs editable by state/role
- This story is **visibility-only**: no edits required.
- Any existing editable pricing fields must be hidden when not permitted (even if backend would reject updates).

### Derived/calculated fields
- UI must not derive margin/cost totals if user cannot view them (avoid inference leaks).
- If totals are displayed, ensure totals are also hidden when they would reveal restricted info.

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not fully defined for Moqui frontend. The following is the minimum required for buildability; unknowns are listed in Open Questions.

### Load/view calls
- `GET /api/workorders/{id}` (or `/api/work-orders/{id}`)  
  Expected:
  - Returns work order details + line items
  - Either:
    - includes `permissionScopes`/capabilities, OR
    - sensitive fields are omitted when unauthorized

- Optional (preferred if available):
  - `GET /api/me/permissions?domain=workexec` or similar to fetch scopes once and cache in session.

### Create/update calls
- None in scope.

### Submit/transition calls
- None in scope.

### Error handling expectations
- `401` → route to login/session expired screen
- `403` → show access denied
- `404` → show “Work order not found”
- `409` → show “Work order updated; refresh” (only if backend returns on load conflicts)
- Policy lookup failure (if separate call) → hide sensitive, show limited-info banner

---

## 10. State Model & Transitions

Work order statuses exist (DRAFT, APPROVED, ASSIGNED, WORK_IN_PROGRESS, etc.) but this story does not add transitions.

### Allowed states
- Execution UI may be used in start-eligible and in-progress states (per workexec state machine).
- Visibility rules apply in **all** states.

### Role-based transitions
- None in scope.

### UI behavior per state
- Independent of state: sensitive fields are shown/hidden based on permissions, not status.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing workOrderId in route → show invalid route error and navigation option back to list.

### Concurrency conflicts
- If backend indicates data stale (409) on load: show refresh CTA; sensitive visibility rules still apply.

### Unauthorized access
- User lacks `workexec:workorder:view` → show access denied; do not render cached sensitive data.

### Empty states
- Work order has no line items → show empty items message; no pricing placeholders that imply cost/price.

### Role misconfiguration / policy not found
- If capabilities cannot be determined:
  - Hide sensitive fields
  - Log client-side warning (non-PII) with correlation/request ID if available
  - Display limited-info banner

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Mechanic cannot see pricing/cost/margin fields
**Given** a logged-in user without permission `workexec:workorder:view-pricing` and without `workexec:workorder:view-cost`  
**And** the user has permission `workexec:workorder:view`  
**When** the user opens the Work Order execution screen for an existing work order  
**Then** the UI shows operational work order and line item fields  
**And** the UI does not render `unitPrice`, `extendedPrice`, `cost`, or `margin` anywhere in the execution screen (including tables, detail panels, and totals).

### Scenario 2: Back office user sees full financial fields when permitted
**Given** a logged-in user with permission `workexec:workorder:view`  
**And** the user has permission `workexec:workorder:view-pricing` and `workexec:workorder:view-cost` (and `workexec:workorder:view-margin` if applicable)  
**When** the user opens the Work Order execution screen for an existing work order that contains those fields  
**Then** the UI renders `unitPrice` and `extendedPrice`  
**And** the UI renders `cost` and `margin` where available  
**And** values match the API response.

### Scenario 3: Missing policy/capabilities defaults to safe (hide)
**Given** a logged-in user with permission `workexec:workorder:view`  
**And** the backend response does not include permission scopes/capabilities  
**And** the backend response includes financial fields (or policy lookup fails client-side)  
**When** the user opens the Work Order execution screen  
**Then** the UI defaults to hiding sensitive financial fields  
**And** the UI displays a non-sensitive banner indicating limited visibility due to policy unavailability.

### Scenario 4: User lacks base permission
**Given** a logged-in user without permission `workexec:workorder:view`  
**When** the user navigates to a Work Order execution screen  
**Then** the UI shows an “Insufficient permissions” state  
**And** does not display any work order details or cached sensitive fields.

### Scenario 5: Sensitive fields are not inferred via totals
**Given** a logged-in user without pricing/cost permissions  
**When** the work order contains line items with prices and costs on the backend  
**Then** the UI does not show totals/subtotals or derived values that would reveal restricted information.

---

## 13. Audit & Observability

### User-visible audit data
- Not required to display audit history in UI for this story.

### Status history
- Not in scope.

### Traceability expectations
- Frontend should emit structured client logs for:
  - Policy/capabilities missing → “VISIBILITY_POLICY_FALLBACK_APPLIED”
  - Optional: when sensitive fields are displayed → “SENSITIVE_FIELDS_DISPLAYED” (only if backend requires/accepts)

> Backend audit event requirements exist in backend reference; frontend must only integrate if an endpoint exists (see Open Questions).

---

## 14. Non-Functional UI Requirements
- **Performance:** Determining visibility must not add more than one extra network call on initial load (prefer piggybacking scopes in existing payload).
- **Accessibility:** Hidden fields must be removed from DOM (not just visually hidden) to avoid screen reader leakage.
- **Responsiveness:** Column hiding must work on mobile/tablet layouts.
- **i18n/timezone/currency:** Currency formatting applies only when pricing is visible; otherwise no currency placeholders.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty state messaging for “no line items”; safe because it does not affect domain policy; impacts UX Summary, Error Flows.
- SD-ERR-HTTP-MAP: Standard handling for 401/403/404 with user-friendly messages; safe because it is generic transport behavior; impacts Service Contracts, Error Flows.
- SD-A11Y-DOM-REMOVE: Remove restricted fields from DOM rather than CSS hiding; safe because it strengthens confidentiality without changing policy; impacts Business Rules, Non-Functional.

---

## 16. Open Questions
1. **Backend contract for permissions:** How does the Moqui frontend determine `permissionScopes`/capabilities?  
   - Are scopes included in the work order payload, in session/user context, or via a separate endpoint?
2. **Canonical endpoint paths:** What are the exact endpoints used in this frontend for work orders (e.g., `/api/workorders/{id}` vs `/api/work-orders/{id}`), and what DTO shape is currently returned?
3. **Sensitive fields list:** Is the sensitive set limited to `unitPrice`, `extendedPrice`, `cost`, `margin`, or are there additional fields (discounts, taxes, labor rate, internal cost codes) that must be hidden too?
4. **Scope names:** Confirm exact permission scope strings to use (e.g., `workexec:workorder:view-pricing`, `workexec:workorder:view-cost`, `workexec:workorder:view-margin`). Is there a single “financials” scope instead?
5. **Audit logging integration:** Is there an existing backend endpoint/event the frontend must call/emit when sensitive fields are shown, or is audit fully server-side only?
6. **Which screens are in scope:** What are the exact Moqui screen names/routes/components that constitute “Execution UI” in this repo (to ensure consistent enforcement everywhere)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Apply Role-Based Visibility in Execution UI  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/219  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Apply Role-Based Visibility in Execution UI

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
A mechanic/technician views a workorder during execution.

## Main Flow
1. System identifies viewer role and applicable visibility policy.
2. System hides restricted pricing/cost fields from mechanic views.
3. System continues to store full financial data in the underlying records.
4. Back office views show full pricing/cost data.
5. System logs access to sensitive fields when shown (optional).

## Alternate / Error Flows
- Role misconfiguration → default to safer (hide sensitive) behavior and alert admins.

## Business Rules
- Visibility policies must be consistently enforced across screens and APIs.
- Hiding fields must not remove financial truth from the data model.

## Data Requirements
- Entities: VisibilityPolicy, Workorder, WorkorderItem, UserRole
- Fields: roleId, canViewPrices, unitPrice, extendedPrice, cost, margin

## Acceptance Criteria
- [ ] Mechanic views do not display restricted financial fields.
- [ ] Back office views retain full visibility.
- [ ] Financial data remains present for invoicing and reporting.

## Notes for Agents
This is a UI/API policy layer; keep it separate from business calculations.

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