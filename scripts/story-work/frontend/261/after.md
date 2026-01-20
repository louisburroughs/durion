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

## 1. Story Header

**Title:** Cost: Maintain Standard/Last/Average Cost with Audit (Frontend – Moqui)

**Primary Persona:** Inventory Manager (and Finance Manager for oversight)

**Business Value:**  
Provide accurate, permissioned visibility and maintenance of inventory item costs (standard/last/average) with full audit traceability so operations and finance can rely on authoritative valuation inputs and compliance-grade change history.

---

## 2. Story Intent

### As a / I want / So that
**As an** Inventory Manager (or Finance Manager),  
**I want** to view current costs (Standard, Last, Average) for an inventory item, manually update Standard Cost with a required reason code (when authorized), and view cost change audit history,  
**so that** costs are maintained correctly (system-managed vs user-managed) and all changes are traceable for analysis and audits.

### In-scope
- View current `standardCost`, `lastCost`, `averageCost` for an Inventory Item.
- Edit **Standard Cost only** (if user has `inventory.cost.standard.update`) with required `reasonCode`.
- Prevent editing of `lastCost` and `averageCost` (system-managed).
- Display cost audit history (`ItemCostAudit`) filtered to the item.
- Frontend handling of validation, authorization errors, and concurrency conflicts returned by services.

### Out-of-scope
- Implementing backend cost calculation/event processing (purchase order receipt updates).
- Creating/modifying inventory items or purchase order receipt workflows.
- Any FIFO/LIFO costing options.
- Any accounting postings/COGS calculations.

---

## 3. Actors & Stakeholders
- **Inventory Manager (Primary User):** Views costs, updates standard cost when permitted, reviews audit.
- **Finance Manager:** Same as above; oversight.
- **Accountant / Finance Team (Stakeholder):** Relies on average cost and audit trail.
- **Auditor (Stakeholder):** Requires immutable audit history and traceability.
- **System (Backend processes):** Updates last/average costs from receipt events; generates audit entries.

---

## 4. Preconditions & Dependencies
- Inventory item exists and is identifiable by `itemId` (or `inventoryItemId`) in routing.
- Backend exposes:
  - Read endpoint to load an item’s current costs and basic identity fields.
  - Mutation endpoint/service to update `standardCost` with `reasonCode` and permission enforcement.
  - Read endpoint to query `ItemCostAudit` by `itemId` with paging/sorting.
- AuthN/AuthZ available to frontend such that permission `inventory.cost.standard.update` is either:
  - discoverable via a “current user permissions” endpoint, **or**
  - enforced server-side with `403` and handled gracefully client-side.
- Moqui frontend project conventions for screen routing, forms, and transitions are available in repo README (not included in prompt).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Inventory Item detail screen (or item search results), user selects **Costs** tab/section.
- Direct navigation via URL to item cost screen: e.g. `/inventory/item/<itemId>/cost` (exact route TBD per repo conventions).

### Screens to create/modify
1. **Modify existing Inventory Item Detail screen** to add:
   - A **Costs** section showing current costs and “Edit Standard Cost” action (if permitted).
2. **New screen (or subscreen): Inventory Item Cost Maintenance**
   - Read-only display of Last Cost, Average Cost.
   - Editable Standard Cost (permission gated).
   - “Save Standard Cost” action with reason code input.
3. **New screen (or subscreen): Item Cost Audit History**
   - Table/grid of audit entries with filters (cost type, date range) and paging.

### Navigation context
- Breadcrumb: Inventory → Items → Item Detail → Costs
- Provide return navigation back to item detail.

### User workflows
**Happy path (authorized update):**
1. User opens item Costs.
2. User clicks “Edit Standard Cost”.
3. User enters new standard cost and reason code.
4. User saves.
5. UI confirms success and refreshes current costs + audit list.

**Alternate paths:**
- User without permission sees Standard Cost as read-only and no edit affordance (or edit attempt results in clear authorization error).
- Standard cost update fails validation (missing reason code, invalid decimal) → inline error + no data change.
- Backend rejects due to concurrency/version mismatch → prompt to refresh and retry.

---

## 6. Functional Behavior

### Triggers
- Screen load with `itemId`.
- User initiates edit/save of Standard Cost.
- User requests audit pagination/filter changes.

### UI actions
- Load current item costs on entry.
- Render cost values with 4-decimal formatting (display) while preserving exact backend values.
- Provide input controls:
  - `standardCost` numeric input (nullable allowed? see Open Questions).
  - `reasonCode` required text/select (backend expects string; UI may offer free-text unless reason codes list exists).
- Disable editing for `lastCost` and `averageCost` always.
- On save:
  - Call update service.
  - Show toast/notification on success.
  - Re-fetch item costs and audit list.

### State changes (frontend)
- Local form state: `view` → `editing` → `saving` → `view` (or `error`).
- Store `lastLoadedAt` timestamp for user awareness (optional).
- Track optimistic concurrency token if provided (e.g., `lastUpdatedStamp`).

### Service interactions
- `loadItemCosts(itemId)` on screen enter.
- `updateStandardCost(itemId, newStandardCost, reasonCode, concurrencyToken?)` on save.
- `loadCostAudit(itemId, filters, page)` on audit tab open and on filter/page change.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Standard cost input:
  - Must be a decimal with at least 4 decimal places supported (UI allows up to >=4; do not force rounding beyond backend rules).
  - **Zero is disallowed** (per inventory rules: “zero is disallowed to avoid ambiguity”). UI should block `0` and `0.0000`.
  - Negative values disallowed.
  - Null allowed only if explicitly supported for manual clearing (unclear; see Open Questions).
- Reason code:
  - Required for manual Standard Cost updates.
  - Non-empty trimmed string.
- Last/Average costs:
  - Never editable; if user attempts via devtools, backend must reject—UI should still treat as read-only.

### Enable/disable rules
- “Edit/Save Standard Cost” visible/enabled only when:
  - user has permission `inventory.cost.standard.update`, OR
  - user attempts and backend returns `403` → then UI hides/disables edit for remainder of session (optional enhancement; safe default not applied).
- Save button disabled until:
  - standardCost changed and valid
  - reasonCode present

### Visibility rules
- If a cost is `null`, display as “Not set” (not `0.00`).
- Audit section available to all users who can view item detail (permission model unclear; default to same visibility as item view).

### Error messaging expectations
- Validation errors: inline near field, plus summary banner if multiple.
- Authorization (`403`): “You don’t have permission to update Standard Cost.”
- Backend validation (`400`): show server message; map field errors to inputs if present.
- Not found (`404`): “Item not found” with link back to search.
- Server error (`5xx`): “Could not update cost. Try again.” with correlation/trace id if provided.

---

## 8. Data Requirements

### Entities involved
- `InventoryItem` (read current costs)
- `ItemCostAudit` (read audit history)

### Fields (type, required, defaults)
**InventoryItem (read-only except standard cost update action)**
- `itemId` (string/uuid, required)
- `standardCost` (decimal(?, scale>=4), nullable, default null)
- `lastCost` (decimal scale>=4, nullable, default null)
- `averageCost` (decimal scale>=4, nullable, default null)
- `lastUpdatedStamp` or equivalent (timestamp/version, optional but strongly recommended for concurrency)

**Standard Cost Update Payload**
- `itemId` (required)
- `standardCost` (decimal scale>=4, required if update sets value; nullable only if “clear” supported)
- `reasonCode` (string, required)

**ItemCostAudit (read-only table)**
- `auditId` (uuid/string)
- `timestamp` (datetime)
- `costTypeChanged` (enum: STANDARD/LAST/AVERAGE)
- `oldValue` (decimal nullable)
- `newValue` (decimal nullable)
- `changeSourceType` (MANUAL/PURCHASE_ORDER)
- `changeSourceId` (string)
- `actor` (string)
- `reasonCode` (string nullable except required when MANUAL+STANDARD)

### Read-only vs editable by state/role
- `standardCost`: editable only for users with `inventory.cost.standard.update`.
- `lastCost`, `averageCost`: always read-only.
- Audit records: always read-only.

### Derived/calculated fields
- None calculated client-side. Average cost calculation is backend-owned/event-driven.

---

## 9. Service Contracts (Frontend Perspective)

> NOTE: Exact endpoint names are unknown from prompt; Moqui implementations may use services invoked via screen transitions. The frontend must align with backend API contracts once confirmed.

### Load/view calls
1. **Get item costs**
   - Input: `itemId`
   - Output: `InventoryItem` fields above
   - Errors: 404 if not found; 403 if not authorized to view item (if applicable)

2. **Get cost audit history**
   - Input: `itemId`, optional filters:
     - `costTypeChanged` (multi or single)
     - `changeSourceType`
     - date range (`fromTs`, `toTs`)
     - paging (`pageIndex`, `pageSize`)
     - sort (default `timestamp desc`)
   - Output: paged list + total count if available

### Create/update calls
1. **Update standard cost**
   - Input: `itemId`, `standardCost`, `reasonCode`, optional concurrency token
   - Output: updated `standardCost` and updated stamp/token
   - Errors:
     - 400 validation (missing reasonCode, invalid cost <=0, invalid scale)
     - 403 missing permission `inventory.cost.standard.update`
     - 409 concurrency conflict (if supported)

### Submit/transition calls
- None beyond update action.

### Error handling expectations
- Backend returns structured error payload with:
  - message
  - fieldErrors (optional: `{field, message}`)
  - correlationId/traceId (optional)
- Frontend maps:
  - fieldErrors → input validation messages
  - otherwise → banner/toast

---

## 10. State Model & Transitions

### Allowed states
Frontend UI state machine (not domain lifecycle):
- `loading`
- `viewing`
- `editingStandardCost`
- `saving`
- `error`

### Role-based transitions
- To enter `editingStandardCost`: requires permission `inventory.cost.standard.update`.
- If permission absent:
  - stay in `viewing` with read-only display.

### UI behavior per state
- `loading`: show spinner/skeleton; disable actions.
- `viewing`: show costs, audit table; show “Edit Standard Cost” only if permitted.
- `editingStandardCost`: show inputs for standardCost and reasonCode; disable save until valid.
- `saving`: disable inputs; show progress indicator.
- `error`: show error panel with retry to reload.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- User enters `0` or negative → block save; message “Standard Cost must be greater than 0.”
- User leaves reason blank → block save; message “Reason code is required.”

### Validation failures (server-side)
- Server returns 400 with reasonCode missing/invalid → display and keep user in editing.
- Server rejects attempt to update last/average (shouldn’t happen from UI) → show generic validation error.

### Concurrency conflicts
- If update returns 409:
  - Show message: “This item was updated by someone else. Reload to continue.”
  - Provide “Reload” button that re-fetches item costs and resets form.

### Unauthorized access
- If load calls return 403: show access denied state.
- If update returns 403: show toast/banner and revert to viewing with edit disabled.

### Empty states
- Costs all null: display “Not set” for each.
- Audit list empty: show “No cost changes recorded yet.”

---

## 12. Acceptance Criteria

### Scenario 1: View costs with null handling
**Given** an inventory item exists with `standardCost = null`, `lastCost = null`, `averageCost = null`  
**When** the user opens the item Costs screen  
**Then** the UI displays each cost as “Not set” (not `0.00`)  
**And** the Last Cost and Average Cost fields are read-only.

### Scenario 2: Authorized user updates Standard Cost with reason code
**Given** the user has permission `inventory.cost.standard.update`  
**And** the item has `standardCost = 10.0000`  
**When** the user edits Standard Cost to `12.5000` and enters reason code `SUPPLIER_PRICE_INCREASE`  
**And** the user clicks Save  
**Then** the frontend calls the Standard Cost update service with `itemId`, `standardCost=12.5000`, and the reason code  
**And** on success the UI shows the updated Standard Cost value  
**And** the audit history list includes a new entry for costType `STANDARD` with old `10.0000` and new `12.5000` and reason code.

### Scenario 3: Standard Cost update blocked when reason code missing
**Given** the user has permission `inventory.cost.standard.update`  
**When** the user enters a valid Standard Cost value but leaves reason code blank  
**Then** the Save action is disabled (or save shows inline validation error)  
**And** no update service call is made.

### Scenario 4: Unauthorized user cannot edit Standard Cost
**Given** the user does not have permission `inventory.cost.standard.update`  
**When** the user opens the Costs screen  
**Then** Standard Cost is displayed as read-only  
**And** no “Edit/Save Standard Cost” action is available  
**Or** if the user attempts to invoke save (e.g., via direct request) and receives 403  
**Then** the UI displays an authorization error and no values change.

### Scenario 5: Prevent editing of system-managed costs
**Given** any user opens the Costs screen  
**Then** Last Cost and Average Cost are never presented as editable fields  
**And** the UI provides no action to update them.

### Scenario 6: Server rejects invalid Standard Cost (zero or negative)
**Given** the user has permission `inventory.cost.standard.update`  
**When** the user attempts to save Standard Cost as `0.0000`  
**Then** the UI blocks the save with a validation message  
**And** if the request is still sent and the server responds 400  
**Then** the UI shows the server validation error and keeps the user in editing state.

### Scenario 7: Audit history paging and filtering
**Given** an item has more than one page of `ItemCostAudit` records  
**When** the user changes page size or navigates to the next page  
**Then** the UI requests the next page from the audit service  
**And** shows results sorted by timestamp descending by default.

### Scenario 8: Concurrency conflict on update
**Given** the user starts editing Standard Cost  
**And** the item is updated by another actor before save  
**When** the user saves and the server responds with a 409 conflict  
**Then** the UI informs the user and offers a reload action  
**And** reloading refreshes the displayed costs and clears the edit form.

---

## 13. Audit & Observability

### User-visible audit data
- Audit table shows: timestamp, cost type, old value, new value, source type, source id, actor, reason code (when present).

### Status history
- Not applicable beyond audit entries; no additional UI lifecycle tracking required.

### Traceability expectations
- For update requests, capture and display server-provided `traceId`/`correlationId` in an expandable error details area (if provided).
- UI should include `itemId` in client logs for debugging (no PII).

---

## 14. Non-Functional UI Requirements
- **Performance:** Costs and audit should load within 2s on typical network for <=50 audit rows; audit uses paging to avoid large payloads.
- **Accessibility:** WCAG 2.1 AA; labels for inputs, error messages associated to fields, keyboard navigable table.
- **Responsiveness:** Works on tablet widths used in POS back office.
- **i18n/timezone/currency:**  
  - Costs displayed using system currency formatting rules (currency code source unclear; see Open Questions).  
  - Timestamps displayed in user locale/timezone; store/render UTC appropriately.

---

## 15. Applied Safe Defaults
- none

---

## 16. Open Questions
1. **API/Service contracts:** What are the exact backend endpoints or Moqui services for:
   - loading item costs,
   - updating standard cost,
   - querying cost audit history (including paging params and response shape)?
2. **Routing location:** What is the canonical route/screen hierarchy in `durion-moqui-frontend` for Inventory Item detail, so the Costs screen is placed consistently?
3. **Permissions discovery:** Does the frontend have a standard way to check permissions (e.g., current-user permissions payload), or should it rely purely on backend 403 handling?
4. **Reason codes source:** Is `reasonCode` free-text, or must it be selected from a controlled list? If controlled, what are the allowed values and how are they loaded?
5. **Currency context:** Are item costs always in a single system currency, or do we need to display a currency code per site/item?
6. **Standard cost clearing:** Is setting `standardCost` back to `null` allowed via UI (i.e., “Clear Standard Cost”), or are manual changes required to be >0 always?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Cost: Maintain Standard/Last/Average Cost with Audit  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/261  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #196 - Cost: Maintain Standard/Last/Average Cost with Audit  
**URL**: https://github.com/louisburroughs/durion/issues/196  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:24.884908378*