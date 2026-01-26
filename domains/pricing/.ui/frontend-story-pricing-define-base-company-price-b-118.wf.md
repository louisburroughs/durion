# [FRONTEND] [STORY] Pricing: Define Base Company Price Book Rules
## Purpose
Enable Moqui Admin users to manage PriceBooks and their PriceBookRules, including listing/searching, viewing details, creating/updating, and deactivating via end-dating. Provide rule authoring with effective dating, priority, and targeting while enforcing pricing domain constraints (scope model, single condition dimension per rule, store-local timezone semantics). Surface read-only audit history for both PriceBooks and Rules and wire all actions to backend Pricing endpoints.

## Components
- Global page header: “Admin → Pricing → Price Books”
- PriceBook list/search
  - Search input (name/id)
  - Filters (Scope type, Active/Inactive as-of now)
  - Results table (Name, Scope, Status, Start, End, Actions)
  - Primary button: “Create Price Book”
- PriceBook create/edit form
  - Fields: name (required), companyId (required), locationId (optional), tierId (optional)
  - Optional fields (only if backend supports): isDefault, startAt, endAt
  - Derived read-only: scope label, status as-of now (store timezone)
  - Buttons: Save, Cancel, Deactivate (if supported)
- PriceBook detail view with tabs/sections
  - Tabs: Rules, Details, Audit
- Rules tab
  - Rules table (Type, Target, Condition, Priority, Effective Start, Effective End, Updated By/At, Actions)
  - Button: “Add Rule”
- Rule create/edit form (within a book)
  - Hidden: priceBookId (when launched from book)
  - Fields: ruleType (GLOBAL/SKU/CATEGORY), targetId (required unless GLOBAL)
  - Condition: conditionType (NONE/SKU/CATEGORY) + conditionId (required if not NONE)
  - Priority (integer), startAt (required), endAt (optional)
  - Price JSON editor (required; includes backend-owned priceType; opaque JSON)
  - Target pickers:
    - Product picker (SKU search via Inventory API; shows sku + name)
    - Category picker (taxonomy lookup; TBD endpoint)
  - Buttons: Save, Cancel, Deactivate/End-date (if supported)
- Audit tab (read-only)
  - Audit list/table with timestamp, actor, action, changed fields summary
  - Filters (entity: PriceBook/Rule, date range)
- System feedback
  - Inline field errors (400 mapping)
  - Unauthorized screen/banner (401/403)
  - Conflict banner (409)
  - Retry banner/toast for 5xx

## Layout
- Top: Breadcrumb + page title + primary action (Create Price Book)
- Main (List): Search/filters row above results table; table fills page width
- Main (Detail): Header with PriceBook name + status + scope; below: tabs (Rules | Details | Audit)
- Rules tab: “Add Rule” button top-right; rules table below
- Details tab: read-only summary + edit button; edit form in-place or separate screen
- Audit tab: filters row + audit table

## Interaction Flow
1. Navigate Admin → Pricing → Price Books to view searchable list of PriceBooks (including base/company-default and scoped books).
2. Search/filter list; select a PriceBook row to open PriceBook detail (default to Rules tab).
3. Create PriceBook:
   1) Click “Create Price Book” → enter name, companyId, optionally locationId and tierId.
   2) Save → on success, show detail page; derived scope displays “Location + Customer Tier” when both are set; audit shows create entry.
4. Edit/Deactivate PriceBook:
   1) From Details tab, click Edit → update allowed fields → Save.
   2) Deactivate via end-date: use dedicated deactivate action if supported, otherwise update with endAt set; status updates based on store-local “now”.
5. Add Rule (Scenario: GLOBAL with no condition):
   1) In Rules tab click “Add Rule” → choose ruleType=GLOBAL; targetId not required.
   2) Set conditionType=NONE; enter priority, startAt, optional endAt; enter valid price JSON including priceType.
   3) Save → rule appears in list using backend-returned values; audit shows rule create entry.
6. Add Rule (Scenario: SKU rule with product search):
   1) Choose ruleType=SKU → open product picker → search triggers Inventory product search API; show results with sku and name.
   2) Select product → targetId set; complete required fields and save → rules list displays selected product as “sku — name”.
7. Edit/Deactivate Rule:
   1) From a rule row click View/Edit → update fields (including JSON) → Save.
   2) Deactivate/end-date using supported backend contract; rule becomes inactive when now ∉ [start,end).
8. View Audit:
   1) Open Audit tab → view read-only history for PriceBook and PriceBookRule changes; filter by date/entity as needed.
9. Error handling (standard mapping):
   1) 400 → show field-level validation errors on the form.
   2) 401/403 → show unauthorized UX and block actions.
   3) 409 → show conflict banner with guidance to refresh/retry.
   4) 5xx → show retry banner/toast; preserve user input where possible.

## Notes
- Timezone: all effective timestamps interpreted/displayed in store-local timezone; date-time pickers must reflect store timezone (DECISION-PRICING-003).
- Scope model: companyId required; locationId and tierId optional; derived scope label computed from presence of locationId/tierId (DECISION-PRICING-004).
- Rule condition model: at most one condition dimension per rule (NONE or a single SKU or CATEGORY); combined scenarios must be modeled via scoped books, not multi-condition rules (DECISION-PRICING-005).
- Price JSON: UI treats JSON as opaque; must include backend-owned priceType; do not compute prices client-side (DECISION-PRICING-006).
- Deactivation: prefer dedicated deactivate endpoint with end-date; otherwise update with endAt set—UI must follow confirmed backend contract (DECISION-PRICING-016).
- Integration: all list/CRUD/audit actions wired to backend Pricing endpoints under the agreed base path (DECISION-PRICING-002); audit retrieval per DECISION-PRICING-015.
- Reference data dependencies/TODOs: category taxonomy lookup endpoint TBD; location lookup endpoint TBD; customer tier lookup endpoint TBD.
- Optional fields: only render isDefault/startAt/endAt for PriceBook if backend supports effective dating/default flags on books.
- Validation: enforce required fields (name, companyId; ruleType; targetId unless GLOBAL; conditionId when conditionType != NONE; priority; startAt; valid JSON).
