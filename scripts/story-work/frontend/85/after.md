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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Order: Create Sales Order Cart and Add Items (Moqui Screens)

### Primary Persona
POS Clerk

### Business Value
Enable a clerk to start a sales transaction by creating a persistent cart (sales order) and adding products/services with deterministic totals, optional source linking (estimate/workorder), and auditable changes—so checkout/quoting can proceed efficiently and traceably.

---

## 2. Story Intent

### As a / I want / So that
- **As a** POS Clerk  
- **I want** to create a new sales order cart and add/update/remove items (by SKU or service code), optionally merge items from an estimate/workorder  
- **So that** I can quote and sell at the counter with reliable totals and a clear audit trail.

### In-scope
- Create a new Sales Order (“cart”) in `DRAFT` state.
- Display and maintain cart header (orderId, customer/vehicle context if present, terminal/clerk context).
- Add line items by identifier (SKU/service code) with quantity.
- Resolve unit price via Pricing Service; handle pricing-service failure via cache TTL and manual-entry permission gate.
- Optional inventory availability check and insufficient-stock policy (WARN_AND_BACKORDER vs BLOCK) as described.
- Update quantity and remove line items.
- Optional linking to an existing estimate or workorder, using merge + idempotency rules described.
- Display deterministic subtotal (sum of qty * unitPrice).
- Frontend-visible audit cues (show that change is recorded; optionally show transition/audit feed if API exists).

### Out-of-scope
- Promotions, tax finalization, invoicing, order submission/checkout flows.
- Configuration management (inventory policy, price override policies, default locations, approval configs).
- Implementing backend services/entities/events (assumed available).
- Notification delivery (alerts).

---

## 3. Actors & Stakeholders
- **POS Clerk (Primary user)**
- **Service Advisor (Secondary user)**: may link estimates/workorders depending on permissions.
- **Pricing Service (External dependency)**: authoritative unit price.
- **Inventory Service (Optional external dependency)**: availability/backorder status.
- **CRM/Customer context provider (Dependency)**: customerId/vehicleId lookup and selection.
- **Audit/Reporting consumers (Stakeholders)**: require consistent event/audit generation.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS UI.
- User session has access to a **clerk identity** and **terminal identity** (required to create an order per backend rules).
- User has permission to create orders and modify lines (exact permission names TBD; see Open Questions).

### Dependencies (must exist or be stubbed)
- Backend endpoints/services for:
  - Create SalesOrder (cart)
  - Add/update/remove SalesOrderLine
  - Link source (estimate/workorder) and merge idempotently
  - Price quote lookup and/or line add that performs pricing server-side
  - Optional inventory availability check
  - Permission check for manual price entry (`ENTER_MANUAL_PRICE`) and possibly reason codes
- Entities exposed (or mapped) to UI:
  - `SalesOrder`, `SalesOrderLine` (names may differ in Moqui entities; mapping required)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- POS main navigation: **Order / New Order** action.
- Optional: from Customer/Vehicle context screen: **Start Order**.
- Optional: from Estimate/Workorder view: **Create Order from Source** (links source during creation or immediately after).

### Screens to create/modify (Moqui)
1. **`apps/pos/order/OrderCreate.xml`** (new)
   - Action to create a new cart (SalesOrder) and redirect to Order Detail.
2. **`apps/pos/order/OrderDetail.xml`** (new)
   - Shows cart header + line items table + subtotal.
   - Contains add-item form and line edit/remove actions.
   - Contains “Link Source” action (estimate/workorder) and results summary.
3. **`apps/pos/order/OrderLinkSourceDialog.xml`** (new or embedded screen/dialog)
   - Collect `sourceType` (ESTIMATE/WORKORDER) and `sourceId`.
4. (Optional) **`apps/pos/order/OrderAudit.xml`** (new) if audit feed is available.

> Note: exact file paths must follow repo conventions (README/agents). If different, adjust during implementation.

### Navigation context
- After creation: redirect to `OrderDetail?orderId=...`
- From OrderDetail, allow returning to POS home while preserving orderId in session/history (implementation detail).

### User workflows
**Happy path**
1. Clerk clicks “New Order”.
2. System creates SalesOrder (`DRAFT`) and loads Order Detail.
3. Clerk adds an item by SKU/service code + quantity.
4. UI shows added line, unit price source, and updated subtotal.
5. Clerk adjusts quantities/removes lines as needed.

**Alternate paths**
- Link an estimate/workorder: user provides ID; UI merges lines and shows merge summary.
- Pricing service down: UI uses cached price (within TTL) or prompts for manual price (if permitted) + reason code.
- Inventory insufficient: UI shows warning and line flagged BACKORDER; still included in subtotal.

---

## 6. Functional Behavior

### Triggers
- User clicks **Create New Order**
- User submits **Add Item**
- User edits **Quantity**
- User clicks **Remove Line**
- User initiates **Link Source** (estimate/workorder)

### UI actions (explicit)
- **Create New Order**
  - Calls create service
  - On success: navigate to OrderDetail with `orderId`
  - On failure: show blocking error banner with reason
- **Add Item**
  - Validate inputs client-side (non-empty identifier, quantity > 0 integer)
  - Call backend add-line operation (preferred: backend performs pricing/inventory checks and returns created line)
  - Render line with flags: `fulfillmentStatus`, `priceSource`, `STALE` indicator if `CACHE`
- **Update Quantity**
  - Inline edit quantity (integer > 0). If set to 0, treat as remove (confirm) OR block (see Open Questions).
  - Call update-line service, refresh order totals
- **Remove Line**
  - Confirm remove
  - Call remove-line service, refresh order totals
- **Link Source**
  - Collect `sourceType` and `sourceId`
  - Call link/merge service
  - Show merge results: items merged vs added as separate lines
  - Ensure idempotency: re-linking same source shows “Already linked; no changes” (based on backend response)

### State changes (frontend-visible)
- SalesOrder created in `DRAFT`
- SalesOrder subtotal changes on line add/update/remove/link merge
- Line-level fields set/returned by backend:
  - `fulfillmentStatus` changes based on inventory policy
  - `priceSource` and staleness indicator

### Service interactions
- Create order
- Get order detail (header + lines)
- Add line (with pricing resolution)
- Update line quantity
- Remove line
- Link source (estimate/workorder) -> merge

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Create order** requires terminalId + clerkId context; if missing in UI session:
  - Block action and show: “Terminal or clerk context missing. Please re-login or select a terminal.”
- **Add item**:
  - Identifier required (SKU/service code)
  - Quantity required, integer, > 0
- **Manual price fallback** (only when pricing unavailable AND no valid cached price):
  - Only show manual price input if user has `ENTER_MANUAL_PRICE`
  - Require:
    - `unitPrice` decimal > 0
    - `reasonCode` non-empty
  - Mark line as `priceSource=MANUAL`
- **Inventory insufficient**:
  - If policy = `WARN_AND_BACKORDER`: allow add, set `fulfillmentStatus=BACKORDER`, show warning
  - If policy = `BLOCK`: prevent add and show error (exact message below)

### Enable/disable rules
- Disable “Add Item” submit while request in-flight.
- Disable quantity edit/remove while update in-flight for that line.
- Hide/disable manual price controls unless pricing failure pathway is active and permission is present.
- Restrict customer-specific promotion UI (if present on screen) when `customerId` is null:
  - Must show message requiring customer assignment (even if promotions themselves are out-of-scope, do not accidentally enable them).

### Visibility rules
- Show “Anonymous cart” indicator when `customerId` is null.
- Show “Price from cache (STALE)” banner/label when `priceSource=CACHE`.
- Show “Backordered” badge when `fulfillmentStatus=BACKORDER`.

### Error messaging expectations (user-facing)
- Invalid SKU/service code: **“Product not found”**
- Pricing service unavailable, cache used: **“Pricing service unavailable — using cached price (may be stale).”**
- Pricing unavailable, no cache, no permission: **“Pricing unavailable and manual price entry is not permitted. Try again later or contact a manager.”**
- Inventory insufficient + BLOCK policy: **“Insufficient stock — item cannot be added.”**
- Unauthorized (403): **“You don’t have permission to perform this action.”**
- Conflict (409) e.g., concurrent edits: **“This order was updated elsewhere. Reload to continue.”**

---

## 8. Data Requirements

### Entities involved (frontend view)
- `SalesOrder` (SoR: backend/workexec domain)
- `SalesOrderLine` (SoR: backend/workexec domain)
- Optional reference entities:
  - Estimate, Workorder (as source references only)
- Optional: AuditLog/Event feed (read-only)

### Fields

**SalesOrder**
- `orderId` (string, required, read-only)
- `status` enum: `DRAFT`, `QUOTED`, `COMPLETED`, `VOIDED` (read-only in this story; always `DRAFT` after create)
- `customerId` (string, nullable; editable only if UI supports setting context—NOT implemented here unless already exists)
- `vehicleId` (string, nullable; same as above)
- `clerkId` (string, required, derived from session, read-only)
- `terminalId` (string, required, derived from session, read-only)
- `subtotal` (decimal, read-only; returned by backend)
- `createdAt`, `updatedAt` (timestamps, read-only)

**SalesOrderLine**
- `orderLineId` (string, required, read-only)
- `orderId` (string, required, read-only)
- `itemSku` (string, required)
- `itemDescription` (string, returned, read-only)
- `quantity` (int, required, editable)
- `unitPrice` (decimal, required; editable ONLY in manual-price flow at add-time; otherwise read-only)
- `fulfillmentStatus` enum `AVAILABLE|BACKORDER` (read-only)
- `priceSource` enum `PRICING_SERVICE|CACHE|MANUAL` (read-only)
- `reasonCode` (string; required when `priceSource=MANUAL`; read-only after add unless backend supports edits—assume read-only)
- `sourceType` enum `ESTIMATE|WORKORDER|null` (read-only)
- `sourceId` (string|null, read-only)
- `sourceLineId` (string|null, read-only)

### Read-only vs editable by state/role
- While `SalesOrder.status != DRAFT`: all line modifications disabled (UI should enforce even if not expected in this story).
- Manual price entry: only if permission `ENTER_MANUAL_PRICE` and only during add flow when pricing unavailable.

### Derived/calculated fields (UI-only)
- `lineExtended = quantity * unitPrice` (display only; backend remains authoritative for subtotal)
- `isStalePrice = (priceSource == CACHE)` (display only)

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may call REST endpoints directly or via Moqui services; contracts below describe required behaviors, not transport specifics.

### Load/view calls
1. **Get order detail**
   - `GET /api/orders/{orderId}` (TBD exact path)
   - Returns SalesOrder + lines + subtotal

### Create/update calls
2. **Create new order**
   - `POST /api/orders`
   - Request includes (or derives): `customerId?`, `vehicleId?`, `clerkId`, `terminalId`
   - Response: created SalesOrder with `orderId`, `status=DRAFT`, `subtotal=0.00`

3. **Add line**
   - Preferred: `POST /api/orders/{orderId}/lines`
   - Request: `itemSkuOrServiceCode`, `quantity`
   - Optional request fields only in manual flow: `unitPrice`, `reasonCode`
   - Response: created line + updated order subtotal (or require refetch)

4. **Update line quantity**
   - `PUT /api/orders/{orderId}/lines/{orderLineId}`
   - Request: `quantity`
   - Response: updated line + updated subtotal (or require refetch)

5. **Remove line**
   - `DELETE /api/orders/{orderId}/lines/{orderLineId}`
   - Response: success + updated subtotal (or require refetch)

### Submit/transition calls
6. **Link source (merge)**
   - `POST /api/orders/{orderId}/link-source`
   - Request: `sourceType`, `sourceId`
   - Response:
     - Updated lines and subtotal
     - Merge summary: counts of merged vs added; idempotency indicator

### Error handling expectations
- `400` validation errors: show field-level error where possible, otherwise banner.
- `401` redirect to login.
- `403` show permission error.
- `404` show not found (order/line/source not found).
- `409` concurrency: prompt reload and provide reload action.
- `503/504` service unavailable: trigger pricing fallback logic when adding items (cache/manual pathways).

---

## 10. State Model & Transitions

### SalesOrder states (relevant to this story)
- `DRAFT` (modifiable)
- `QUOTED`, `COMPLETED`, `VOIDED` (non-modifiable here; UI must render read-only if encountered)

### Allowed transitions (UI impact)
- This story does **not** implement transitions between SalesOrder states.
- UI must:
  - Allow add/update/remove/link only when `status=DRAFT`
  - Disable modification controls when `status!=DRAFT`

### Role-based transitions
- N/A (no state transitions implemented), but role-based permission affects:
  - Manual price entry (`ENTER_MANUAL_PRICE`)

---

## 11. Alternate / Error Flows

1. **Invalid SKU/service code**
   - Backend returns 404/400 with code indicating unknown item
   - UI shows “Product not found”, keeps form inputs for correction

2. **Pricing service unavailable**
   - If backend returns line with `priceSource=CACHE`: show stale warning
   - If backend indicates no price and requires manual:
     - If user has permission: show manual price + reasonCode inputs and allow retry submit
     - Else: show blocking error and do not add line

3. **Inventory insufficient**
   - If WARN_AND_BACKORDER: line added with BACKORDER + warning badge/banner
   - If BLOCK: do not add line; show error

4. **Link source idempotency**
   - Re-link same source:
     - Backend returns no changes or explicit idempotent response
     - UI shows “Source already linked; no changes applied.”

5. **Concurrency conflict**
   - When updating/removing line:
     - Backend returns 409
     - UI shows conflict message and offers reload; do not attempt auto-merge client-side

6. **Unauthorized**
   - Any action returns 403:
     - UI shows permission error
     - If action is manual price entry, ensure manual controls are hidden/disabled afterwards

7. **Empty states**
   - New order has no lines: show “No items yet” and focus add-item input.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create a new, empty sales cart
Given I am a logged-in POS Clerk with terminal context available  
When I initiate “Create New Order”  
Then a SalesOrder is created with a unique orderId  
And the SalesOrder status is DRAFT  
And the subtotal is 0.00  
And the Order Detail screen is shown for that orderId

### Scenario 2: Add a valid and available item to the cart
Given I have a DRAFT sales order  
When I add SKU “ABC-123” with quantity 2  
Then the system adds a SalesOrderLine for “ABC-123” with quantity 2 and a resolved unit price  
And the order subtotal equals the deterministic sum of (quantity * unitPrice) across all lines  
And the new line is displayed in the cart

### Scenario 3: Attempt to add an item with an invalid SKU
Given I have a DRAFT sales order  
When I attempt to add SKU “INVALID-SKU” with quantity 1  
Then the item is not added to the cart  
And I see the error message “Product not found”  
And the order subtotal remains unchanged

### Scenario 4: Update the quantity of an existing item
Given my cart contains a line for SKU “ABC-123” with quantity 2 and unit price 10.50  
When I update that line quantity to 3  
Then the line quantity becomes 3  
And the order subtotal is recalculated and displayed as 31.50

### Scenario 5: Remove an item from the cart
Given my cart contains a line for SKU “ABC-123”  
When I remove that line  
Then the line no longer appears in the cart  
And the order subtotal is recalculated and displayed

### Scenario 6: Add item with insufficient inventory (backorder policy WARN_AND_BACKORDER)
Given I have a DRAFT sales order  
And the inventory insufficient policy is WARN_AND_BACKORDER  
When I add an item that the Inventory Service reports as insufficient stock  
Then the line is added to the cart  
And the line is marked with fulfillmentStatus BACKORDER  
And I see a warning “Insufficient stock — item will be backordered”  
And the line is included in the subtotal calculation

### Scenario 7: Pricing unavailable - use cached price within TTL
Given I have a DRAFT sales order  
And the Pricing Service is unavailable  
And a valid cached price exists within the TTL  
When I add SKU “ABC-123”  
Then the line is added with priceSource CACHE  
And I see “Pricing service unavailable — using cached price (may be stale).”

### Scenario 8: Pricing unavailable - manual entry allowed with permission
Given I have a DRAFT sales order  
And the Pricing Service is unavailable  
And no valid cached price exists  
And I have permission ENTER_MANUAL_PRICE  
When I enter a manual unit price and a reason code and submit add-item  
Then the line is added with priceSource MANUAL  
And the reason code is stored for audit  
And the order subtotal updates accordingly

### Scenario 9: Pricing unavailable - manual entry not permitted
Given I have a DRAFT sales order  
And the Pricing Service is unavailable  
And no valid cached price exists  
And I do not have permission ENTER_MANUAL_PRICE  
When I attempt to add the item  
Then the item is not added  
And I see “Pricing unavailable and manual price entry is not permitted. Try again later or contact a manager.”

### Scenario 10: Link estimate merges lines deterministically and is idempotent
Given I have a DRAFT sales order with SKU “TIRE-001” quantity 2 at price 100  
And an estimate exists with SKU “TIRE-001” quantity 3 at price 100 and SKU “OIL-001” quantity 1 at price 25  
When I link the estimate to the cart  
Then the cart line for “TIRE-001” quantity becomes 5  
And “OIL-001” is added as a new line  
And the lines include sourceType ESTIMATE and sourceId and sourceLineId values  
When I link the same estimate again  
Then no duplicate lines are created  
And the UI indicates no changes were applied

---

## 13. Audit & Observability

### User-visible audit data
- After each successful action (create/add/update/remove/link), show a non-intrusive confirmation and maintain a “last updated” timestamp (from `updatedAt` or response).
- If an audit feed endpoint exists, show a read-only panel listing:
  - action type, user, timestamp, key identifiers (orderId, orderLineId)

### Status history
- Not applicable for SalesOrder state transitions in this story, but changes to lines must be traceable.

### Traceability expectations
- UI must pass correlation/request ID headers if project conventions exist (TBD).
- For manual price entry, ensure reasonCode is collected and submitted; do not log sensitive details client-side.

---

## 14. Non-Functional UI Requirements

- **Performance:** Order detail load and line mutations should feel immediate; show loading states. Target < 500ms UI feedback after response; if slower, show spinner.
- **Accessibility:** Keyboard operable add-item flow; proper labels for inputs; warnings/errors announced (ARIA live region).
- **Responsiveness:** Usable on tablet and desktop POS layouts.
- **i18n/timezone/currency:** Display currency formatting consistently with locale; timestamps displayed in local timezone but stored/handled in UTC (display-only here).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide an explicit empty-state message when a cart has no lines; safe because it’s purely presentational and does not alter business logic. (Sections: UX Summary, Alternate / Error Flows)
- SD-UX-INFLIGHT-DISABLE: Disable action buttons during in-flight requests to prevent duplicate submissions; safe because it reduces accidental double posts without changing domain rules. (Sections: Functional Behavior, Error Flows)
- SD-ERR-HTTP-MAP: Map common HTTP codes (400/401/403/404/409/5xx) to standard user messages; safe because it does not change backend policy, only presentation. (Sections: Service Contracts, Business Rules, Error Flows)

---

## 16. Open Questions

1. **Moqui routing & screen conventions:** What are the exact POS app screen paths and naming conventions in `durion-moqui-frontend` (e.g., `apps/pos/order/*` vs another structure)?  
2. **Backend endpoint paths for frontend:** What are the exact REST endpoints for create order, add/update/remove line, get order, and link source in the Moqui frontend integration layer? (Backend story is Spring-based; Moqui may proxy differently.)  
3. **Permissions source & names:** Besides `ENTER_MANUAL_PRICE`, what permissions control create order, modify lines, and link estimate/workorder? How does the frontend check them (session claims vs endpoint-driven)?  
4. **Quantity-to-zero behavior:** Should setting quantity to `0` be treated as “remove line” (with confirmation) or rejected as invalid?  
5. **Service vs SKU identifier:** How does the UI distinguish “product SKU” vs “service code”? Is there a unified identifier field, or should there be a selector?  
6. **Inventory check toggle:** Is inventory availability check always on, or configurable per site/terminal? If configurable, where does the frontend read that configuration?  
7. **Pricing cache behavior location:** Is the 60-second TTL cache implemented server-side (preferred) or must the frontend implement caching? If frontend, what is the cache key data available in UI (customerAccountId, priceListId, currency)?  
8. **Link source UX:** How does the clerk locate/select an estimate/workorder to link (search dialog vs entering an ID)? If search is required, which endpoints support lookup and what filters are allowed?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Order: Create Sales Order Cart and Add Items  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/85  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Create Sales Order Cart and Add Items

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative
As a **POS Clerk**, I want to create a cart and add products/services so that I can quote and sell efficiently at the counter.

## Details
- Create cart for a customer/vehicle context.
- Add items by SKU/service code; set quantities.
- Support linking to an existing estimate/workorder as the source of items (optional).

## Acceptance Criteria
- Cart created with unique orderId.
- Items can be added/updated/removed.
- Totals recalc deterministically.
- Audit changes.

## Integrations
- Pull product pricing from product/pricing service; optionally check availability from inventory.

## Data / Entities
- PosOrder, PosOrderLine, PriceQuote, TaxQuote (hook), AuditLog

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